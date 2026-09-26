package com.example.myapplication.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** One line of synced lyrics; [text] is empty for an instrumental gap. */
data class LyricLine(val timeMs: Long, val text: String)

/** Synced lyrics of the track with this app id. */
data class TrackLyrics(val trackId: Long, val lines: List<LyricLine>)

/**
 * Synced (LRC) lyrics of Yandex Music tracks, through the unofficial API the rest of the app
 * uses. Only lyrics with line timings count — plain text has nothing to follow the music with.
 *
 * Fetched lyrics are kept on disk, so a downloaded track shows them offline too; a track known
 * to have none isn't asked about again this session.
 */
class YandexLyricsRepository(
    context: Context,
    private val service: YandexMusicService
) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /** @param urn `yandex:track:<id>[:<album>]`. Null when there are no synced lyrics. */
    suspend fun syncedLyrics(urn: String): List<LyricLine>? = withContext(Dispatchers.IO) {
        val trackId = urn.removePrefix("yandex:track:").substringBefore(':')
        if (trackId.isBlank() || trackId in missing) return@withContext null

        val cached = File(dir, "$trackId.lrc")
        if (cached.exists()) return@withContext parseLrc(cached.readText()).takeIf { it.isNotEmpty() }

        val lrc = try {
            val timestamp = System.currentTimeMillis() / 1000
            val downloadUrl = service.getLyrics(
                trackId = trackId,
                format = "LRC",
                timeStamp = timestamp,
                sign = YandexMusicApi.lyricsSign(trackId, timestamp)
            ).result?.downloadUrl
            if (downloadUrl == null) {
                missing.add(trackId)
                return@withContext null
            }
            http.newCall(Request.Builder().url(downloadUrl).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: HttpException) {
            // 404: the track has no lyrics of this kind.
            if (e.code() == 404) missing.add(trackId)
            Log.d("YandexLyrics", "No synced lyrics for $trackId: ${e.code()}")
            null
        } catch (e: Exception) {
            Log.w("YandexLyrics", "Couldn't load lyrics for $trackId", e)
            null
        } ?: return@withContext null

        val lines = parseLrc(lrc)
        if (lines.isEmpty()) {
            missing.add(trackId)
            return@withContext null
        }
        runCatching { cached.writeText(lrc) }
        lines
    }

    companion object {
        private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

        /**
         * `[mm:ss.xx] text` lines, in time order. A line may carry several time tags; tags that
         * aren't times (`[ar:...]`) are ignored.
         */
        fun parseLrc(lrc: String): List<LyricLine> = lrc.lineSequence()
            .flatMap { raw ->
                val tags = TIME_TAG.findAll(raw).toList()
                if (tags.isEmpty()) return@flatMap emptySequence()
                val text = raw.substring(tags.last().range.last + 1).trim()
                tags.asSequence().map { tag ->
                    val (min, sec, frac) = tag.destructured
                    val fracMs = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    LyricLine(min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs, text)
                }
            }
            .sortedBy { it.timeMs }
            .toList()
    }
}
