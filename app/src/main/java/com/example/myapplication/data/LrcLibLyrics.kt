package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Synced lyrics from LRCLIB (lrclib.net), the open library of LRC lyrics: for the tracks Yandex has
 * none for — YouTube Music's and SoundCloud's above all. Found by title and artist, and taken only
 * when the length agrees: lyrics timed to another cut of the song run off from it.
 *
 * Kept on disk, as Yandex's are, so a downloaded track shows them offline; a track found to have
 * none isn't asked about again this session.
 */
class LrcLibLyrics(context: Context) {
    private val dir = File(context.filesDir, "lyrics-lrclib").apply { mkdirs() }
    private val missing = ConcurrentHashMap.newKeySet<Long>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Null when LRCLIB has no synced lyrics that fit [track]. */
    suspend fun syncedLyrics(track: SoundCloudTrack): List<LyricLine>? = withContext(Dispatchers.IO) {
        if (track.id in missing) return@withContext null
        val cached = File(dir, "${track.id}.lrc")
        if (cached.exists()) return@withContext YandexLyricsRepository.parseLrc(cached.readText()).takeIf { it.isNotEmpty() }

        val lrc = try {
            guesses(track).firstNotNullOfOrNull { (artist, title) -> find(artist, title, track.duration) }
        } catch (e: Exception) {
            // Offline, or LRCLIB down: asked again next time, not marked as having none.
            Log.w(TAG, "LRCLIB didn't answer for ${track.urn}: $e")
            return@withContext null
        }
        val lines = lrc?.let(YandexLyricsRepository::parseLrc).orEmpty()
        if (lines.isEmpty()) {
            missing += track.id
            Log.d(TAG, "No synced lyrics for \"${track.title}\"")
            return@withContext null
        }
        runCatching { cached.writeText(lrc!!) }
        lines
    }

    /**
     * Who and what to look for, likeliest first: the credited artist and the title without what
     * uploads add to it ("(Official Video)", "[Free DL]", "feat. …"); and, for an upload titled
     * "Artist - Title" as SoundCloud's often are, the two halves of that.
     */
    private fun guesses(track: SoundCloudTrack): List<Pair<String, String>> {
        val artist = track.artists?.firstOrNull()?.username ?: track.user?.username
        val title = clean(track.title.orEmpty())
        val credited = if (!artist.isNullOrBlank() && title.isNotEmpty()) clean(artist) to title else null
        val dash = Regex("""\s+[-–—]\s+""").find(title)
        val halves = dash?.let {
            val left = title.substring(0, it.range.first).trim()
            val right = title.substring(it.range.last + 1).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) left to right else null
        }
        // On SoundCloud the uploader is often not the artist, and the title says who is.
        val soundCloud = track.urn?.startsWith("soundcloud:") == true
        return (if (soundCloud) listOfNotNull(halves, credited) else listOfNotNull(credited, halves)).distinct()
    }

    /** The synced lyrics of the best of LRCLIB's matches: the closest in length, within a few seconds. */
    private fun find(artist: String, title: String, durationMs: Long): String? {
        val url = Uri.parse("$BASE/search").buildUpon()
            .appendQueryParameter("track_name", title)
            .appendQueryParameter("artist_name", artist)
            .build()
            .toString()
        val request = Request.Builder()
            .url(url)
            // LRCLIB asks to be told who is asking.
            .header("User-Agent", USER_AGENT)
            .build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("LRCLIB HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        val results = runCatching { JsonParser.parseString(body) as? JsonArray }.getOrNull() ?: return null
        val seconds = durationMs / 1000.0
        return results.mapNotNull { it as? JsonObject }
            .filter { result -> result.text("syncedLyrics") != null }
            .map { result -> result to (result.get("duration")?.takeUnless { it.isJsonNull }?.asDouble ?: -1.0) }
            .filter { (_, length) -> durationMs <= 0 || length < 0 || abs(length - seconds) <= MAX_LENGTH_GAP_S }
            .minByOrNull { (_, length) -> if (durationMs <= 0 || length < 0) Double.MAX_VALUE else abs(length - seconds) }
            ?.first?.text("syncedLyrics")
    }

    private fun JsonObject.text(name: String): String? = get(name)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "LrcLib"
        const val BASE = "https://lrclib.net/api"
        const val USER_AGENT = "YouCloud (https://github.com/GXUser7/YouCloud)"

        // Further apart than this, it is another cut of the song (a radio edit, a live take).
        const val MAX_LENGTH_GAP_S = 4.0

        // What uploads add in brackets that isn't the song's name.
        private val NOISE = Regex(
            """\s*[(\[](?:[^)\]]*\b(?:official|video|audio|lyrics?|clip|visuali[sz]er|hd|hq|4k|mv|free\s*(?:dl|download)|prod\.?|remaster(?:ed)?|explicit|клип|премьера)\b[^)\]]*)[)\]]""",
            RegexOption.IGNORE_CASE
        )
        private val FEAT = Regex("""\s*[(\[]?\s*\b(?:feat\.?|ft\.?|featuring)\s+[^)\]]*[)\]]?""", RegexOption.IGNORE_CASE)

        // "Song - Remastered 2011", "Song - Live": a version, not the name.
        private val VERSION = Regex("""\s+[-–—]\s+(?:\d{4}\s+)?(?:remaster(?:ed)?|live|radio edit|single version|mono|stereo).*$""", RegexOption.IGNORE_CASE)

        fun clean(text: String): String = text
            .replace(VERSION, "")
            .replace(NOISE, "")
            .replace(FEAT, "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
    }
}
