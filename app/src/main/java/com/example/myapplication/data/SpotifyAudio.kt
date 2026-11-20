package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import android.util.Log
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
    private const val TAG = "SpotifyAudio"

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
            .apply {
                (track.artists.orEmpty().ifEmpty { listOfNotNull(track.user) }).mapNotNull { it.username }.take(8)
                    .forEach { appendQueryParameter("artist", it) }
            }
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
    fun markStreamFailed(spotifyId: String, context: Context? = null, reason: String? = null) {
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
            Log.w(TAG, "[$spotifyId] Marked failed ${stream.candidateKey} (${reason ?: "playback error"})")
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

    internal enum class SourceKind { YOUTUBE, YANDEX, SOUNDCLOUD }

    internal const val BONUS_YT_SONG = 26_000L
    internal const val BONUS_YT_VIDEO = 25_000L
    internal const val BONUS_YT_WEB = 24_000L
    internal const val BONUS_YANDEX = 10_000L
    internal const val BONUS_SOUNDCLOUD = 0L

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
        cache.get(key)?.takeIf { (expires, _) -> expires > now }?.let {
            Log.d(TAG, "[$id] Cache hit -> ${it.second.candidateKey}")
            return it.second
        }
        val primaryQuery = spotifyAudioQuery(track).trim()
        if (primaryQuery.isEmpty() || track.duration <= 0) {
            Log.w(TAG, "[$id] Invalid metadata for resolve: query='$primaryQuery' duration=${track.duration}")
            throw unavailable()
        }

        val startedAt = now
        val artistsText = track.artists.orEmpty().mapNotNull { it.username }
            .ifEmpty { listOfNotNull(track.user?.username) }.joinToString(", ")
        Log.i(
            TAG,
            "[$id] Resolving '${track.title}' by [$artistsText] (${track.duration / 1000}s) urgent=$urgent " +
                "auth={yt=${ytAuth != null}, ya=${yandexToken.isNotBlank()}, scId=${clientId.isNotBlank()}, scAuth=${settings.oauthTokenValue().isNotBlank()}} query='$primaryQuery'"
        )

        val failed = activeFailedKeys(id, now)
        if (failed.isNotEmpty()) Log.d(TAG, "[$id] Skipping failed candidates: $failed")
        var networkError: IOException? = null
        val yt = if (ytAuth == null) ytSearch else YouTubeMusicClient { ytAuth }
        val yandexService = if (yandexToken.isNotBlank()) YandexMusicApi.createService(settings::yandexTokenValue) else null
        val soundCloudService = if (clientId.isNotBlank()) {
            SoundCloudApi.createService(
                oauthTokenProvider = settings::oauthTokenValue,
                clientIdProvider = { settings.clientId.value },
                onClientIdRefreshed = settings::saveClientId
            )
        } else null
        val scResolver = soundCloudService?.let(::SoundCloudPlaybackResolver)

        suspend fun searchCandidates(query: String, label: String): List<ScoredCandidate> = coroutineScope {
            suspend fun <T> safeSearch(source: String, call: suspend () -> T): T? = try {
                call()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "[$id] $source search IO error for '$query': $e")
                networkError = e
                null
            } catch (e: Exception) {
                Log.w(TAG, "[$id] $source search failed for '$query': ${e.javaClass.simpleName}: ${e.message}")
                null
            }

            val ytJob = async { safeSearch("YTM") { yt.searchAudioCandidates(query) } }
            val yandexJob = async {
                if (yandexService == null) emptyList()
                else safeSearch("Yandex") { yandexService.searchTracks(query).result?.tracks?.results.orEmpty() }.orEmpty()
            }
            val scJob = async {
                if (soundCloudService == null) emptyList()
                else safeSearch("SoundCloud") { soundCloudService.searchTracks(query, settings.clientId.value, limit = 15).collection }.orEmpty()
            }

            val out = mutableListOf<ScoredCandidate>()
            val ytRes = ytJob.await()
            val ytSongsRaw = ytRes?.songs.orEmpty()
            val ytVideosRaw = ytRes?.videos.orEmpty()
            var ytMatched = 0
            for (song in ytSongsRaw) {
                val vid = song.youTubeVideoId ?: continue
                val cKey = "yt:$vid"
                if (cKey in failed) continue
                val score = spotifyMatchScore(track, song) ?: continue
                ytMatched++
                out += ScoredCandidate(cKey, SourceKind.YOUTUBE, song, score + BONUS_YT_SONG)
            }
            for (video in ytVideosRaw) {
                val vid = video.youTubeVideoId ?: continue
                val cKey = "yt:$vid"
                if (cKey in failed) continue
                val score = spotifyMatchScore(track, video) ?: continue
                ytMatched++
                out += ScoredCandidate(cKey, SourceKind.YOUTUBE, video, score + BONUS_YT_VIDEO)
            }

            // If YouTube Music had nothing matching (e.g. region-restricted catalog), also check YouTube Web.
            var ytWebCount = 0
            if (ytMatched == 0) {
                val ytWebTracks = safeSearch("YTWeb") { YouTubeWeb.search(query, ytAuth).tracks }.orEmpty()
                    .filter { it.youTubeVideoId != null && it.liveVideoId == null && it.duration > 0 }
                ytWebCount = ytWebTracks.size
                for (webTrack in ytWebTracks) {
                    val vid = webTrack.youTubeVideoId ?: continue
                    val cKey = "yt:$vid"
                    if (cKey in failed) continue
                    val score = spotifyMatchScore(track, webTrack) ?: continue
                    ytMatched++
                    out += ScoredCandidate(cKey, SourceKind.YOUTUBE, webTrack, score + BONUS_YT_WEB)
                }
            }

            val yaRaw = yandexJob.await()
            var yaMatched = 0
            for (yaTrack in yaRaw) {
                val mapped = yaTrack.toSoundCloudTrack()
                val matchTrack = mapped.copy(title = yaTrack.fullTitle())
                val raw = mapped.urn?.removePrefix("yandex:track:")?.substringBefore(':') ?: continue
                val cKey = "ya:$raw"
                if (cKey in failed) continue
                val score = spotifyMatchScore(track, matchTrack) ?: continue
                yaMatched++
                out += ScoredCandidate(cKey, SourceKind.YANDEX, mapped, score + BONUS_YANDEX)
            }

            val scRaw = scJob.await()
            val scPlayable = scRaw.filter(::soundCloudPlaysWhole)
            val scProgressive = scPlayable.filter { sc ->
                sc.media?.transcodings.orEmpty().any { isWholeStream(it) && it.format?.protocol == "progressive" }
            }
            var scMatched = 0
            var scHlsOnlyMatched = 0
            for (scTrack in scPlayable) {
                val hasProgressive = scTrack in scProgressive
                val cKey = "sc:${scTrack.id}"
                if (cKey in failed) continue
                val score = spotifyMatchScore(track, scTrack) ?: continue
                if (!hasProgressive) {
                    scHlsOnlyMatched++
                    continue
                }
                scMatched++
                out += ScoredCandidate(cKey, SourceKind.SOUNDCLOUD, scTrack, score + BONUS_SOUNDCLOUD)
            }

            Log.i(
                TAG,
                "[$id] Search($label) raw={ytmS=${ytSongsRaw.size}, ytmV=${ytVideosRaw.size}, ytw=$ytWebCount, ya=${yaRaw.size}, sc=${scRaw.size}/${scProgressive.size}prog} " +
                    "matched={yt=$ytMatched, ya=$yaMatched, sc=$scMatched, scHlsOnly=$scHlsOnlyMatched}"
            )
            if (out.isEmpty()) {
                val sampleRejects = buildList {
                    ytSongsRaw.firstOrNull()?.let { add("ytmS:${spotifyMatchRejectReason(track, it)}") }
                    ytVideosRaw.firstOrNull()?.let { add("ytmV:${spotifyMatchRejectReason(track, it)}") }
                    yaRaw.firstOrNull()?.let { ya ->
                        add("ya:${spotifyMatchRejectReason(track, ya.toSoundCloudTrack().copy(title = ya.fullTitle()))}")
                    }
                    scPlayable.firstOrNull()?.let { add("sc:${spotifyMatchRejectReason(track, it)}") }
                }
                if (sampleRejects.isNotEmpty()) {
                    Log.i(TAG, "[$id] Sample rejects($label): ${sampleRejects.joinToString(" | ")}")
                }
            }
            out.distinctBy { it.key }.sortedByDescending { it.score }
        }

        var ranked = searchCandidates(primaryQuery, "primary")
        if (ranked.isEmpty()) {
            spotifyAudioFallbackQuery(track)?.let { fallbackQuery ->
                ranked = searchCandidates(fallbackQuery, "fallback")
            }
        }

        suspend fun <T> tryResolve(source: String, call: suspend () -> T): T? = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "[$id] $source stream IO error: $e")
            networkError = e
            null
        } catch (e: Exception) {
            Log.w(TAG, "[$id] $source stream error: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

        // Cap extraction attempts per source so YouTube leads when available, while Yandex/SoundCloud remain reachable.
        var ytAttempts = 0
        var yaAttempts = 0
        var scAttempts = 0
        for (cand in ranked) {
            when (cand.kind) {
                SourceKind.YOUTUBE -> {
                    if (ytAttempts >= 3) continue
                    ytAttempts++
                    val vid = cand.track.youTubeVideoId ?: continue
                    val stream = tryResolve(cand.key) {
                        withContext(Dispatchers.IO) { YouTubeStreams.resolve(context, vid, ytAuth, urgent = { urgent }) }
                    }
                    if (stream == null) {
                        Log.w(TAG, "[$id] ${cand.key} returned null stream (score=${cand.score})")
                        continue
                    }
                    val extension = if (stream.mimeType?.startsWith("audio/webm") == true) "webm" else "m4a"
                    val itag = Uri.parse(stream.url).getQueryParameter("itag") ?: "0"
                    val cacheKey = "${StreamCache.KEY_PREFIX}spotify:$id:yt_${vid}_$itag"
                    Log.i(
                        TAG,
                        "[$id] Resolved via ${cand.key} (score=${cand.score}, ext=$extension, clen=${stream.contentLength}) " +
                            "in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
                    )
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
                    if (yaAttempts >= 3) continue
                    yaAttempts++
                    val raw = cand.track.urn?.removePrefix("yandex:track:")?.substringBefore(':') ?: continue
                    val url = tryResolve(cand.key) { YandexMusicApi.resolveTrackStream(raw, yandexToken) }
                    if (url == null) {
                        Log.w(TAG, "[$id] ${cand.key} returned null stream (score=${cand.score})")
                        continue
                    }
                    val cacheKey = "${StreamCache.KEY_PREFIX}spotify:$id:ya_$raw"
                    Log.i(
                        TAG,
                        "[$id] Resolved via ${cand.key} (score=${cand.score}) in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
                    )
                    return remember(key, Stream(url = url, extension = "mp3", cacheKey = cacheKey, candidateKey = cand.key))
                }
                SourceKind.SOUNDCLOUD -> {
                    if (scAttempts >= 3) continue
                    scAttempts++
                    val resolver = scResolver ?: continue
                    // Playback uses DefaultMediaSourceFactory with a non-HLS spotify:// MediaItem: progressive only avoids M3U8 parser failures.
                    val url = tryResolve(cand.key) {
                        resolver.resolve(cand.track, settings.clientId.value, progressiveFirst = true, progressiveOnly = true)
                    }
                    if (url == null) {
                        Log.w(TAG, "[$id] ${cand.key} returned null progressive stream (score=${cand.score})")
                        continue
                    }
                    val cacheKey = "${StreamCache.KEY_PREFIX}spotify:$id:sc_${cand.track.id}"
                    Log.i(
                        TAG,
                        "[$id] Resolved via ${cand.key} (score=${cand.score}) in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
                    )
                    return remember(key, Stream(url = url, extension = "mp3", cacheKey = cacheKey, candidateKey = cand.key))
                }
            }
        }

        val error = networkError
        Log.w(
            TAG,
            "[$id] FAILED to resolve '${track.title}' (candidates=${ranked.size}, netErr=${error != null}) " +
                "in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
        )
        if (error != null) throw IOException(tr("Не удалось найти источник звука для трека Spotify."), error)
        throw unavailable()
    }

    private fun remember(key: String, stream: Stream): Stream {
        cache.put(key, (android.os.SystemClock.elapsedRealtime() + CACHE_TTL_MS) to stream)
        return stream
    }

    private fun unavailable() = TrackUnavailableException(tr("Не удалось найти источник звука для трека Spotify."))
}
