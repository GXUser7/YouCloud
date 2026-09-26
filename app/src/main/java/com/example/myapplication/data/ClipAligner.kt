package com.example.myapplication.data

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
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
    private const val FRAME_US = FRAME_MS * 1_000L
    private const val DECODE_PARTS = 4
    private const val PREROLL_US = 500_000L
    private const val WINDOW_FRAMES = 1_000 // ten seconds of the track per anchor
    private const val REFINE_FRAMES = 4
    private const val MIN_CORRELATION = 0.45f
    private const val MIN_MATCHED_SHARE = 0.25f
    private const val MIN_AGREEING_SHARE = 0.6f
    private const val AGREEING_FRAMES = 10
    private const val NEAR_FRAMES = 50
    private const val CONTINUITY_SLACK = 0.15f
    private const val SAME_OFFSET_FRAMES = 3
    private const val MAX_DECODE_US = 10 * 60 * 1_000_000L

    // googlevideo sends a whole file at about the pace it plays, and a few megabytes asked for
    // by range at full speed: yt-dlp downloads YouTube in pieces for the same reason.
    private const val CHUNK_BYTES = 1L * 1024 * 1024
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    private const val RANGE_ATTEMPTS = 2

    /**
     * How [source]'s sound rises, ten milliseconds at a time. The stream is fetched to [workDir]
     * first, in ranges: decoding straight off the network read it at the pace googlevideo trickles
     * a whole file out, over a minute for one track. [onFetched] is told when the network is free.
     */
    suspend fun onsetsOf(source: AudioSource, workDir: File, onFetched: () -> Unit = {}): FloatArray? = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val file = fetch(source, workDir) ?: return@withContext null
        onFetched()
        try {
            onsets(file.path)
        } finally {
            // Only what was downloaded here: a downloaded track's own file is read in place.
            if (file.parentFile == workDir) file.delete()
        }
    }

    /**
     * The map from the track's timeline ([track]'s onsets) to the video's, or null when the two
     * don't convincingly line up.
     */
    fun align(track: FloatArray, video: FloatArray): List<VideoSegment>? = match(track, video)

    /** [source] as a local file: itself when it is one, else downloaded in ranges, several at once. */
    private suspend fun fetch(source: AudioSource, workDir: File): File? {
        if (!source.url.startsWith("http")) return File(source.url).takeIf { it.exists() }
        val file = File.createTempFile("align", ".media", workDir)
        val started = System.currentTimeMillis()
        return try {
            val (first, total) = range(source, 0)
            RandomAccessFile(file, "rw").use { it.write(first) }
            if (total != null && total > first.size) {
                coroutineScope {
                    (first.size.toLong() until total step CHUNK_BYTES).map { offset ->
                        async(Dispatchers.IO) {
                            val (bytes, _) = range(source, offset)
                            RandomAccessFile(file, "rw").use { out ->
                                out.seek(offset)
                                out.write(bytes)
                            }
                        }
                    }.awaitAll()
                }
            }
            Log.d(TAG, "${file.length() / 1024} KB from ${source.url.substringAfter("//").substringBefore('/')} in ${System.currentTimeMillis() - started} ms")
            file
        } catch (e: CancellationException) {
            file.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't fetch ${source.url.take(80)}: $e")
            file.delete()
            null
        }
    }

    /**
     * [CHUNK_BYTES] of [source] from [offset], and the whole file's size when the server gave a
     * range (null: it sent everything at once, which is then what came back).
     */
    private fun range(source: AudioSource, offset: Long): Pair<ByteArray, Long?> {
        // googlevideo now and then leaves a request hanging; asked again, it answers.
        var failure: java.io.IOException? = null
        repeat(RANGE_ATTEMPTS) {
            try {
                return rangeOnce(source, offset)
            } catch (e: java.io.IOException) {
                failure = e
            }
        }
        throw failure!!
    }

    private fun rangeOnce(source: AudioSource, offset: Long): Pair<ByteArray, Long?> {
        val request = Request.Builder()
            .url(source.url)
            .header("Range", "bytes=$offset-${offset + CHUNK_BYTES - 1}")
            .apply { source.headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return http.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: error("no body")
            when (response.code) {
                200 -> bytes to null
                206 -> bytes to response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                else -> error("HTTP ${response.code}")
            }
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
        var previous: Anchor? = null
        while (start + WINDOW_FRAMES <= a.size) {
            val (coarseLag, coarseScore) = bestLag(coarseA, start / 2, coarseWindow, coarseB, statsB, 0, coarseB.size - coarseWindow)
            val (globalLag, globalScore) = if (coarseScore < MIN_CORRELATION) {
                coarseLag * 2 to coarseScore
            } else {
                bestLag(
                    a, start, WINDOW_FRAMES, b, fineStatsB,
                    (coarseLag * 2 - REFINE_FRAMES).coerceAtLeast(0),
                    (coarseLag * 2 + REFINE_FRAMES).coerceAtMost(b.size - WINDOW_FRAMES)
                )
            }
            // A chorus comes round more than once, and the next one can match as well as the
            // right one. Where the video carries on from the last stretch nearly as well, it's
            // taken to carry on.
            val chosen = previous?.takeIf { it.score >= MIN_CORRELATION }?.let { last ->
                val from = (start + last.offset - NEAR_FRAMES).coerceIn(0, b.size - WINDOW_FRAMES)
                val to = (start + last.offset + NEAR_FRAMES).coerceIn(0, b.size - WINDOW_FRAMES)
                val (nearLag, nearScore) = bestLag(a, start, WINDOW_FRAMES, b, fineStatsB, from, to)
                if (nearScore >= globalScore - CONTINUITY_SLACK) nearLag to nearScore else null
            } ?: (globalLag to globalScore)
            val anchor = Anchor(start, chosen.first - start, chosen.second)
            anchors += anchor
            if (anchor.score >= MIN_CORRELATION) previous = anchor
            start += WINDOW_FRAMES
        }

        val matched = anchors.filter { it.score >= MIN_CORRELATION }
        Log.d(
            TAG,
            "${matched.size}/${anchors.size} stretches matched " +
                "(${anchors.joinToString(" ") { "%.2f@%d".format(it.score, it.offset * FRAME_MS / 1000) }}) " +
                "in ${System.currentTimeMillis() - started} ms"
        )
        // A video with sound of its own over the music (effects, lines) matches weakly in
        // places, yet its stretches still point to the same spots; another song's point anywhere.
        val agreeing = anchors.count { anchor ->
            matched.any { kotlin.math.abs(it.offset - anchor.offset) <= AGREEING_FRAMES }
        }
        if (matched.size < max(2, (anchors.size * MIN_MATCHED_SHARE).toInt()) ||
            agreeing < anchors.size * MIN_AGREEING_SHARE
        ) {
            return null
        }

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
     *
     * Decoded in [DECODE_PARTS] stretches at once, each by a codec of its own: the system's
     * decoders run in another process and hand back a few milliseconds of sound per round trip,
     * which took the better part of ten seconds for one song.
     */
    private suspend fun onsets(path: String): FloatArray? = coroutineScope {
        val probe = MediaExtractor()
        val (trackIndex, format) = try {
            probe.setDataSource(path)
            val index = (0 until probe.trackCount).firstOrNull {
                probe.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return@coroutineScope null
            index to probe.getTrackFormat(index)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read $path: $e")
            return@coroutineScope null
        } finally {
            probe.release()
        }
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else -1L
        val endUs = if (durationUs > 0) minOf(durationUs, MAX_DECODE_US) else MAX_DECODE_US
        val frames = (endUs / FRAME_US).toInt() + 1
        val parts = if (durationUs > 0) DECODE_PARTS else 1
        val started = System.currentTimeMillis()
        val decoded = (0 until parts).map { part ->
            async(Dispatchers.Default) {
                decodePart(path, trackIndex, format, endUs * part / parts, endUs * (part + 1) / parts, frames)
            }
        }.awaitAll()
        if (decoded.any { it == null }) return@coroutineScope null

        val sums = DoubleArray(frames)
        val counts = IntArray(frames)
        for (part in decoded.filterNotNull()) {
            for (f in 0 until frames) {
                sums[f] += part.sums[f]
                counts[f] += part.counts[f]
            }
        }
        val used = counts.indexOfLast { it > 0 } + 1
        if (used == 0) return@coroutineScope null
        val energies = FloatArray(used)
        var last = ln(1e-9).toFloat()
        for (f in 0 until used) {
            if (counts[f] > 0) last = ln(1e-9 + sums[f] / counts[f]).toFloat()
            energies[f] = last
        }
        Log.d(TAG, "${used * FRAME_MS / 1000} s of ${format.getString(MediaFormat.KEY_MIME)} decoded in ${System.currentTimeMillis() - started} ms")
        FloatArray(used) { i -> if (i == 0) 0f else max(0f, energies[i] - energies[i - 1]) }
    }

    /** Energy summed per frame, and samples per frame, of one stretch of a file. */
    private class PartialEnergy(val sums: DoubleArray, val counts: IntArray)

    private fun decodePart(
        path: String,
        trackIndex: Int,
        format: MediaFormat,
        startUs: Long,
        endUs: Long,
        frames: Int
    ): PartialEnergy? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            extractor.selectTrack(trackIndex)
            // Started a little early: a decoder's first moments after a seek are silence while it
            // warms up, which read as a burst of loudness right where the stretch begins.
            extractor.seekTo((startUs - PREROLL_US).coerceAtLeast(0L), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false
            val sums = DoubleArray(frames)
            val counts = IntArray(frames)
            var shortScratch = ShortArray(0)
            var floatScratch = FloatArray(0)

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            // Fed and drained as far as the codec lets, and only then waited on.
            while (!outputDone) {
                var progressed = false
                while (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(0)
                    if (inIndex < 0) break
                    val buffer = decoder.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0 || extractor.sampleTime >= endUs) {
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                    progressed = true
                }
                while (!outputDone) {
                    val outIndex = decoder.dequeueOutputBuffer(info, if (progressed) 0L else 2_000L)
                    if (outIndex >= 0) {
                        val out = decoder.getOutputBuffer(outIndex)!!.order(ByteOrder.nativeOrder())
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        // Copied out in bulk: a get() per sample is slow.
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
                        // Each sample goes to the frame its time falls in. What the codec decodes
                        // from before the stretch (a seek lands on a sync point) is another's.
                        val usPerSample = 1_000_000.0 / sampleRate
                        var i = 0
                        var n = 0
                        while (i + channels <= count) {
                            val t = info.presentationTimeUs + (n * usPerSample).toLong()
                            if (t >= endUs) break
                            if (t >= startUs) {
                                var energy = 0f
                                for (c in 0 until channels) {
                                    val v = if (floatPcm) floatScratch[i + c] else shortScratch[i + c] * (1f / 32768f)
                                    energy += v * v
                                }
                                val f = (t / FRAME_US).toInt()
                                if (f < frames) {
                                    sums[f] += energy
                                    counts[f]++
                                }
                            }
                            i += channels
                            n++
                        }
                        decoder.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        progressed = true
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val output = decoder.outputFormat
                        sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        floatPcm = output.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            output.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                    } else {
                        break
                    }
                }
            }
            PartialEnergy(sums, counts)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't decode $path from ${startUs / 1_000_000} s: $e")
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }
}
