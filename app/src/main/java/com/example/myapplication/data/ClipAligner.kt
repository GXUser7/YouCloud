package com.example.myapplication.data

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Lines a music video up with its track by their sound. A video is rarely the track as released:
 * it opens with a scene, stops for a skit, fades out early. Both are decoded, reduced to where
 * the sound gets louder ten times a second, and each ten seconds of the track is looked for in
 * the video. What comes out is the map [TrackVideo] plays the video by; a video whose sound
 * doesn't convincingly match the track's gives none, and isn't shown.
 */
object ClipAligner {
    private const val TAG = "ClipAligner"

    /** Where a stream is, and what to fetch it with. */
    data class AudioSource(val url: String, val headers: Map<String, String> = emptyMap())

    private const val FRAME_MS = 10
    private const val WINDOW_FRAMES = 1_000 // ten seconds of the track per anchor
    private const val REFINE_FRAMES = 4
    private const val MIN_CORRELATION = 0.45f
    private const val MIN_MATCHED_SHARE = 0.4f
    private const val SAME_OFFSET_FRAMES = 3
    private const val MAX_DECODE_US = 10 * 60 * 1_000_000L

    // googlevideo sends a whole file at about the pace it plays, and a few megabytes asked for
    // by range at full speed: yt-dlp downloads YouTube in pieces for the same reason.
    private const val CHUNK_BYTES = 2L * 1024 * 1024
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * The map from the track's timeline to the video's, or null. Both sounds are fetched to
     * [workDir] first, side by side: decoding straight off the network read them at the pace
     * googlevideo trickles a whole file out, over a minute for one track.
     */
    suspend fun align(track: AudioSource, video: AudioSource, workDir: File): List<VideoSegment>? = coroutineScope {
        val started = System.currentTimeMillis()
        workDir.mkdirs()
        val trackFile = async(Dispatchers.IO) { fetch(track, workDir) }
        val videoFile = async(Dispatchers.IO) { fetch(video, workDir) }
        val files = listOfNotNull(trackFile.await(), videoFile.await())
        try {
            if (files.size < 2) return@coroutineScope null
            val fetched = System.currentTimeMillis()
            val onsetsA = async(Dispatchers.Default) { onsets(files[0].path) }
            val onsetsB = async(Dispatchers.Default) { onsets(files[1].path) }
            val a = onsetsA.await() ?: return@coroutineScope null
            val b = onsetsB.await() ?: return@coroutineScope null
            val decoded = System.currentTimeMillis()
            withContext(Dispatchers.Default) { match(a, b) }.also {
                Log.d(
                    TAG,
                    "fetched in ${fetched - started} ms, decoded in ${decoded - fetched} ms, " +
                        "matched in ${System.currentTimeMillis() - decoded} ms"
                )
            }
        } finally {
            // Only what was downloaded here: a downloaded track's own file is read in place.
            files.filter { it.parentFile == workDir }.forEach { it.delete() }
        }
    }

    /** [source] as a local file: itself when it is one, else downloaded in ranges. */
    private fun fetch(source: AudioSource, workDir: File): File? {
        if (!source.url.startsWith("http")) return File(source.url).takeIf { it.exists() }
        val file = File.createTempFile("align", ".media", workDir)
        val started = System.currentTimeMillis()
        return try {
            file.outputStream().use { out ->
                var offset = 0L
                var total = -1L
                while (total < 0 || offset < total) {
                    val request = Request.Builder()
                        .url(source.url)
                        .header("Range", "bytes=$offset-${offset + CHUNK_BYTES - 1}")
                        .apply { source.headers.forEach { (name, value) -> header(name, value) } }
                        .build()
                    val done = http.newCall(request).execute().use { response ->
                        val body = response.body ?: error("no body")
                        when (response.code) {
                            // The whole file, ranges or not.
                            200 -> {
                                body.byteStream().copyTo(out)
                                true
                            }
                            206 -> {
                                total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                                val bytes = body.bytes()
                                out.write(bytes)
                                offset += bytes.size
                                bytes.isEmpty() || (total < 0 && bytes.size < CHUNK_BYTES)
                            }
                            else -> error("HTTP ${response.code}")
                        }
                    }
                    if (done) break
                }
            }
            Log.d(TAG, "${file.length() / 1024} KB from ${source.url.substringAfter("//").substringBefore('/')} in ${System.currentTimeMillis() - started} ms")
            file
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't fetch ${source.url.take(80)}: $e")
            file.delete()
            null
        }
    }

    private fun match(a: FloatArray, b: FloatArray): List<VideoSegment>? {
        val started = System.currentTimeMillis()
        if (a.size < WINDOW_FRAMES || b.size < WINDOW_FRAMES) return null

        // Searched at half the resolution first, then refined around the best match.
        val coarseA = halve(a)
        val coarseB = halve(b)
        val coarseWindow = WINDOW_FRAMES / 2
        val statsB = SlidingStats(coarseB, coarseWindow)
        val fineStatsB = SlidingStats(b, WINDOW_FRAMES)

        val anchors = mutableListOf<Anchor>()
        var start = 0
        while (start + WINDOW_FRAMES <= a.size) {
            val (coarseLag, coarseScore) = bestLag(coarseA, start / 2, coarseWindow, coarseB, statsB, 0, coarseB.size - coarseWindow)
            val (lag, score) = if (coarseScore < MIN_CORRELATION) {
                coarseLag * 2 to coarseScore
            } else {
                bestLag(
                    a, start, WINDOW_FRAMES, b, fineStatsB,
                    (coarseLag * 2 - REFINE_FRAMES).coerceAtLeast(0),
                    (coarseLag * 2 + REFINE_FRAMES).coerceAtMost(b.size - WINDOW_FRAMES)
                )
            }
            anchors += Anchor(start, lag - start, score)
            start += WINDOW_FRAMES
        }

        val matched = anchors.filter { it.score >= MIN_CORRELATION }
        Log.d(
            TAG,
            "${matched.size}/${anchors.size} stretches matched " +
                "(${anchors.joinToString(" ") { "%.2f@%d".format(it.score, it.offset * FRAME_MS / 1000) }}) " +
                "in ${System.currentTimeMillis() - started} ms"
        )
        if (matched.isEmpty() || matched.size < anchors.size * MIN_MATCHED_SHARE) return null

        // A stretch that matched nothing keeps the offset of the one before it.
        val segments = mutableListOf<VideoSegment>()
        var offset = matched.first().offset
        for (anchor in anchors) {
            if (anchor.score >= MIN_CORRELATION) offset = anchor.offset
            val last = segments.lastOrNull()
            val lastOffset = last?.let { (it.videoStartMs - it.trackStartMs) / FRAME_MS }
            if (last == null || kotlin.math.abs(offset - lastOffset!!) > SAME_OFFSET_FRAMES) {
                val trackStart = if (last == null) 0L else anchor.start.toLong() * FRAME_MS
                segments += VideoSegment(
                    trackStartMs = trackStart,
                    videoStartMs = trackStart + offset.toLong() * FRAME_MS,
                    durationMs = Long.MAX_VALUE / 4
                )
            }
        }
        return segments.zipWithNext { current, next ->
            current.copy(durationMs = next.trackStartMs - current.trackStartMs)
        } + segments.last()
    }

    private class Anchor(val start: Int, val offset: Int, val score: Float)

    /**
     * Normalised cross-correlation of `a[start, start + window)` with every window of [b] from
     * [from] to [to]: the lag that fits best, and how well.
     */
    private fun bestLag(
        a: FloatArray, start: Int, window: Int,
        b: FloatArray, stats: SlidingStats,
        from: Int, to: Int
    ): Pair<Int, Float> {
        var meanA = 0.0
        for (i in 0 until window) meanA += a[start + i]
        meanA /= window
        var varA = 0.0
        for (i in 0 until window) {
            val d = a[start + i] - meanA
            varA += d * d
        }
        if (varA <= 1e-9) return from to 0f
        val normA = sqrt(varA)
        var bestLag = from
        var best = -1.0
        for (lag in from..to) {
            val sdB = stats.sd(lag)
            if (sdB <= 1e-9) continue
            var dot = 0.0
            for (i in 0 until window) dot += (a[start + i] - meanA) * b[lag + i]
            // Σ(a−ā)(b−b̄) = Σ(a−ā)b, since Σ(a−ā) = 0.
            val score = dot / (normA * sdB)
            if (score > best) {
                best = score
                bestLag = lag
            }
        }
        return bestLag to best.toFloat()
    }

    /** Sums over every window of [values], for the correlation's denominator. */
    private class SlidingStats(values: FloatArray, private val window: Int) {
        private val sum = DoubleArray(values.size + 1)
        private val squares = DoubleArray(values.size + 1)

        init {
            for (i in values.indices) {
                sum[i + 1] = sum[i] + values[i]
                squares[i + 1] = squares[i] + values[i].toDouble() * values[i]
            }
        }

        /** √Σ(b−b̄)² over the window at [start]. */
        fun sd(start: Int): Double {
            val s = sum[start + window] - sum[start]
            val q = squares[start + window] - squares[start]
            return sqrt(max(0.0, q - s * s / window))
        }
    }

    private fun halve(values: FloatArray): FloatArray =
        FloatArray(values.size / 2) { max(values[2 * it], values[2 * it + 1]) }

    /**
     * How much louder the sound gets, frame by frame (10 ms): the rise of its log energy. Beats,
     * syllables and chords all show as rises, and they fall at the same places in any copy of
     * the same recording.
     */
    private fun onsets(path: String): FloatArray? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val energies = ArrayList<Float>(40_000)
            var shortScratch = ShortArray(0)
            var floatScratch = FloatArray(0)
            var frameSum = 0.0
            var frameCount = 0
            var frameSize = sampleRate * FRAME_MS / 1000

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(1_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > MAX_DECODE_US) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 1_000)
                when {
                    outIndex >= 0 -> {
                        val out = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        // Copied out in bulk: a get() per sample made decoding the slow part.
                        val count: Int
                        if (floatPcm) {
                            val samples = out.asFloatBuffer()
                            count = samples.remaining()
                            if (floatScratch.size < count) floatScratch = FloatArray(count)
                            samples.get(floatScratch, 0, count)
                        } else {
                            val samples = out.asShortBuffer()
                            count = samples.remaining()
                            if (shortScratch.size < count) shortScratch = ShortArray(count)
                            samples.get(shortScratch, 0, count)
                        }
                        var i = 0
                        while (i + channels <= count) {
                            var energy = 0f
                            for (c in 0 until channels) {
                                val v = if (floatPcm) floatScratch[i + c] else shortScratch[i + c] * (1f / 32768f)
                                energy += v * v
                            }
                            i += channels
                            frameSum += energy
                            if (++frameCount == frameSize) {
                                energies += ln(1e-9 + frameSum / frameSize).toFloat()
                                frameSum = 0.0
                                frameCount = 0
                            }
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = decoder.outputFormat
                        sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = output.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            output.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        frameSize = sampleRate * FRAME_MS / 1000
                    }
                }
            }
            FloatArray(energies.size) { i -> if (i == 0) 0f else max(0f, energies[i] - energies[i - 1]) }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't decode $path: $e")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
