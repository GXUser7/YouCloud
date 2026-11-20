package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

/** Spotify supplies the catalog. The player resolves a matching complete recording using its other sources. */
internal object SpotifyAudio {
    data class Stream(
        val url: String,
        val extension: String,
        val userAgent: String? = null,
        val chunked: Boolean = false,
        val cacheKey: String? = null,
        val candidateKey: String = ""
    )

    private const val CACHE_TTL_MS = 5 * 60_000L
    private const val FAILED_TTL_MS = 10 * 60_000L

    private val cache = android.util.LruCache<String, Pair<Long, Stream>>(64)
    private val failedCandidates = android.util.LruCache<String, MutableMap<String, Long>>(64)
    private val ytSearch = YouTubeMusicClient { null }

    fun uri(track: SoundCloudTrack): String? {
        val id = track.spotifyId ?: return null
        return Uri.Builder().scheme("spotify").authority("track").appendPath(id)
            .appendQueryParameter("title", track.title.orEmpty())
            .appendQueryParameter("duration", track.duration.toString())
            .apply { (track.artists.orEmpty().ifEmpty { listOfNotNull(track.user) }).mapNotNull { it.username }.take(8)
                .forEach { appendQueryParameter("artist", it) } }
            .build().toString()
    }

    fun fromUri(uri: Uri): SoundCloudTrack? {
        if (uri.scheme != "spotify" || uri.host != "track" || uri.pathSegments.size != 1) return null
        val id = uri.lastPathSegment?.takeIf(String::isSpotifyId) ?: return null
        val ref = SPOTIFY_TRACK + id
        val title = uri.getQueryParameter("title")?.takeIf { it.isNotBlank() && it.length <= 512 } ?: return null
        val duration = uri.getQueryParameter("duration")?.toLongOrNull()?.takeIf { it in 1..86_400_000 } ?: return null
        val artists = uri.getQueryParameters("artist").take(8).filter { it.isNotBlank() && it.length <= 256 }
            .map { SoundCloudUser(username = it) }
        return SoundCloudTrack(spotifyKey(ref), urn = ref, kind = "track", title = title, duration = duration, user = artists.firstOrNull(), artists = artists)
    }

    @UnstableApi
    fun markStreamFailed(spotifyId: String, context: Context? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        val matched = mutableListOf<Stream>()
        val snapshot = synchronized(cache) { cache.snapshot() }
        for ((key, entry) in snapshot) {
            if (key.startsWith("$spotifyId:")) {
                matched += entry.second
                cache.remove(key)
            }
        }
        val failedMap = synchronized(failedCandidates) {
            failedCandidates.get(spotifyId) ?: LinkedHashMap<String, Long>().also { failedCandidates.put(spotifyId, it) }
        }
        for (stream in matched) {
            if (stream.candidateKey.isNotEmpty()) {
                synchronized(failedMap) { failedMap[stream.candidateKey] = now + FAILED_TTL_MS }
            }
            val cacheKey = stream.cacheKey
            if (context != null && cacheKey != null) {
                val appContext = context.applicationContext
                kotlin.concurrent.thread(name = "spotify-cache-drop", isDaemon = true) {
                    runCatching { StreamCache.cache(appContext).removeResource(cacheKey) }
                }
            }
        }
    }

    private fun activeFailedKeys(spotifyId: String, now: Long): Set<String> {
        val map = synchronized(failedCandidates) { failedCandidates.get(spotifyId) } ?: return emptySet()
        return synchronized(map) {
            val it = map.entries.iterator()
            while (it.hasNext()) {
                if (it.next().value <= now) it.remove()
            }
            map.keys.toSet()
        }
    }

    private enum class SourceKind { YOUTUBE, YANDEX, SOUNDCLOUD }

    private data class ScoredCandidate(
        val key: String,
        val kind: SourceKind,
        val track: SoundCloudTrack,
        val score: Long
    )

    suspend fun resolve(context: Context, settings: SettingsRepository, track: SoundCloudTrack, urgent: Boolean): Stream {
        val id = track.spotifyId ?: throw unavailable()
        val ytAuth = settings.ytMusicAuth()
        val yandexToken = settings.yandexTokenValue()
        val clientId = settings.clientId.value
        val key = "$id:${ytAuth?.cookie.orEmpty().hashCode()}:${clientId.isNotEmpty()}:${yandexToken.isNotEmpty()}:${!urgent}"
        val now = android.os.SystemClock.elapsedRealtime()
        cache.get(key)?.takeIf { (expires, _) -> expires > now }?.let { return it.second }
        val primaryQuery = spotifyAudioQuery(track).trim()
        if (primaryQuery.isEmpty() || track.duration <= 0) throw unavailable()

        val failed = activeFailedKeys(id, now)
        var networkError: IOException? = null
        val yt = if (ytAuth == null) ytSearch else YouTubeMusicClient { ytAuth }
        val yandexService = if (yandexToken.isNotBlank()) YandexMusicApi.createService(settings::yandexTokenValue) else null
        val soundCloudService = if (clientId.isNotBlank()) {
            SoundCloudApi.createService(oauthTokenProvider = settings::oauthTokenValue, clientIdProvider = { settings.clientId.value })
        } else null
        val scResolver = soundCloudService?.let(::SoundCloudPlaybackResolver)

        suspend fun searchCandidates(query: String): List<ScoredCandidate> = coroutineScope {
            suspend fun <T> safeSearch(call: suspend () -> T): T? = try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                networkError = e
                null
            } catch (_: Exception) {
                null
            }

            val ytJob = async { safeSearch { yt.searchAudioCandidates(query) } }
            val yandexJob = async {
                if (yandexService == null) emptyList()
                else safeSearch { yandexService.searchTracks(query).result?.tracks?.results.orEmpty() }.orEmpty()
            }
            val scJob = async {
                if (soundCloudService == null) emptyList()
                else safeSearch { soundCloudService.searchTracks(query, clientId, limit = 15).collection }.orEmpty()
            }

            val out = mutableListOf<ScoredCandidate>()
            ytJob.await()?.let { res ->
                for (song in res.songs) {
                    val vid = song.youTubeVideoId ?: continue
                    val cKey = "yt:$vid"
                    if (cKey in failed) continue
                    val score = spotifyMatchScore(track, song) ?: continue
                    out += ScoredCandidate(cKey, SourceKind.YOUTUBE, song, score + 900L)
                }
                for (video in res.videos) {
                    val vid = video.youTubeVideoId ?: continue
                    val cKey = "yt:$vid"
                    if (cKey in failed) continue
                    val score = spotifyMatchScore(track, video) ?: continue
                    out += ScoredCandidate(cKey, SourceKind.YOUTUBE, video, score + 300L)
                }
            }
            for (yaTrack in yandexJob.await()) {
                val mapped = yaTrack.toSoundCloudTrack()
                val matchTrack = mapped.copy(title = yaTrack.fullTitle())
                val raw = mapped.urn?.removePrefix("yandex:track:")?.substringBefore(':') ?: continue
                val cKey = "ya:$raw"
                if (cKey in failed) continue
                val score = spotifyMatchScore(track, matchTrack) ?: continue
                out += ScoredCandidate(cKey, SourceKind.YANDEX, mapped, score + 800L)
            }
            for (scTrack in scJob.await().filter(::soundCloudPlaysWhole)) {
                val cKey = "sc:${scTrack.id}"
                if (cKey in failed) continue
                val score = spotifyMatchScore(track, scTrack) ?: continue
                out += ScoredCandidate(cKey, SourceKind.SOUNDCLOUD, scTrack, score)
            }
            out.distinctBy { it.key }.sortedByDescending { it.score }
        }

        var ranked = searchCandidates(primaryQuery)
        if (ranked.isEmpty()) {
            spotifyAudioFallbackQuery(track)?.let { fallbackQuery ->
                ranked = searchCandidates(fallbackQuery)
            }
        }

        suspend fun <T> tryResolve(call: suspend () -> T): T? = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            networkError = e
            null
        } catch (_: Exception) {
            null
        }

        // Cap slow YouTube stream extractions so a VPN bot-check quickly yields to Yandex/SoundCloud.
        var ytAttempts = 0
        for (cand in ranked.take(10)) {
            when (cand.kind) {
                SourceKind.YOUTUBE -> {
                    if (ytAttempts >= 3) continue
                    ytAttempts++
                    val vid = cand.track.youTubeVideoId ?: continue
                    val stream = tryResolve {
                        withContext(Dispatchers.IO) { YouTubeStreams.resolve(context, vid, ytAuth, urgent = { urgent }) }
                    } ?: continue
                    val extension = if (stream.mimeType?.startsWith("audio/webm") == true) "webm" else "m4a"
                    val itag = Uri.parse(stream.url).getQueryParameter("itag") ?: "0"
                    val cacheKey = "${StreamCache.KEY_PREFIX}spotify:$id:yt_${vid}_$itag"
                    return remember(
                        key,
                        Stream(
                            url = stream.url,
                            extension = extension,
                            userAgent = stream.userAgent,
                            chunked = stream.contentLength > 0,
                            cacheKey = cacheKey,
                            candidateKey = cand.key
                        )
                    )
                }
                SourceKind.YANDEX -> {
                    val raw = cand.track.urn?.removePrefix("yandex:track:")?.substringBefore(':') ?: continue
                    val url = tryResolve { YandexMusicApi.resolveTrackStream(raw, yandexToken) } ?: continue
                    val cacheKey = "${StreamCache.KEY_PREFIX}spotify:$id:ya_$raw"
                    return remember(key, Stream(url = url, extension = "mp3", cacheKey = cacheKey, candidateKey = cand.key))
                }
                SourceKind.SOUNDCLOUD -> {
                    val resolver = scResolver ?: continue
                    val url = tryResolve {
                        resolver.resolve(cand.track, clientId, progressiveOnly = !urgent)
                    } ?: continue
                    val isHls = url.contains(".m3u8", ignoreCase = true) || url.contains("/hls", ignoreCase = true)
                    val cacheKey = if (isHls) null else "${StreamCache.KEY_PREFIX}spotify:$id:sc_${cand.track.id}"
                    return remember(
                        key,
                        Stream(
                            url = url,
                            extension = if (isHls) "m3u8" else "mp3",
                            cacheKey = cacheKey,
                            candidateKey = cand.key
                        )
                    )
                }
            }
        }

        val error = networkError
        if (error != null) throw IOException(tr("Не удалось найти источник звука для трека Spotify."), error)
        throw unavailable()
    }

    private fun remember(key: String, stream: Stream): Stream {
        cache.put(key, (android.os.SystemClock.elapsedRealtime() + CACHE_TTL_MS) to stream)
        return stream
    }

    private fun unavailable() = TrackUnavailableException(tr("Не удалось найти источник звука для трека Spotify."))
}
