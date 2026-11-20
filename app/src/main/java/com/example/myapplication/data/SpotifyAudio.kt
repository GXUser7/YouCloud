package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Spotify supplies the catalog. The player resolves a matching complete recording using its other sources. */
internal object SpotifyAudio {
    data class Stream(val url: String, val extension: String, val userAgent: String? = null, val chunked: Boolean = false)

    private val cache = android.util.LruCache<String, Pair<Long, Stream>>(64)
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

    suspend fun resolve(context: Context, settings: SettingsRepository, track: SoundCloudTrack, urgent: Boolean): Stream {
        val id = track.spotifyId ?: throw unavailable()
        val key = "$id:${settings.ytMusicAccount.value.orEmpty()}:${settings.clientId.value.isNotEmpty()}:${settings.yandexTokenValue().isNotEmpty()}"
        val now = android.os.SystemClock.elapsedRealtime()
        cache.get(key)?.takeIf { (expires, _) -> expires > now }?.let { return it.second }
        val query = spotifyAudioQuery(track).trim()
        if (query.isEmpty() || track.duration <= 0) throw unavailable()

        var failure: Exception? = null
        suspend fun <T> trySource(call: suspend () -> T): T? = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
            null
        }

        val ytAuth = settings.ytMusicAuth()
        val yt = if (ytAuth == null) ytSearch else YouTubeMusicClient { ytAuth }
        val youtube = trySource { yt.searchAudioTracks(query) }.orEmpty()
            .filter { it.youTubeVideoId != null }.rankedFor(track)
        for (candidate in youtube.take(3)) {
            val stream = trySource {
                withContext(Dispatchers.IO) { YouTubeStreams.resolve(context, candidate.youTubeVideoId!!, ytAuth, urgent = { urgent }) }
            } ?: continue
            val extension = if (stream.mimeType?.startsWith("audio/webm") == true) "webm" else "m4a"
            return remember(key, Stream(stream.url, extension, stream.userAgent, chunked = stream.contentLength > 0))
        }

        val yandexToken = settings.yandexTokenValue()
        if (yandexToken.isNotBlank()) {
            val yandex = YandexMusicApi.createService(settings::yandexTokenValue)
            val candidates = trySource { yandex.searchTracks(query).result?.tracks?.results.orEmpty() }.orEmpty()
                .map { it.toSoundCloudTrack() }.rankedFor(track)
            for (candidate in candidates.take(3)) {
                val raw = candidate.urn?.removePrefix("yandex:track:")?.substringBefore(':') ?: continue
                val url = trySource { YandexMusicApi.resolveTrackStream(raw, yandexToken) } ?: continue
                return remember(key, Stream(url, "mp3"))
            }
        }

        val clientId = settings.clientId.value
        if (clientId.isNotBlank()) {
            val service = SoundCloudApi.createService(
                oauthTokenProvider = settings::oauthTokenValue, clientIdProvider = { settings.clientId.value }
            )
            val resolver = SoundCloudPlaybackResolver(service)
            val candidates = trySource { service.searchTracks(query, clientId, limit = 10).collection }.orEmpty()
                .filter(::soundCloudPlaysWhole).rankedFor(track)
            for (candidate in candidates.take(3)) {
                val url = trySource { resolver.resolve(candidate, clientId, progressiveOnly = true) } ?: continue
                return remember(key, Stream(url, "mp3"))
            }
        }
        val error = failure
        if (error is java.io.IOException) throw java.io.IOException(tr("Не удалось найти источник звука для трека Spotify."), error)
        throw unavailable()
    }

    private fun remember(key: String, stream: Stream): Stream {
        cache.put(key, (android.os.SystemClock.elapsedRealtime() + 5 * 60_000) to stream)
        return stream
    }

    private fun List<SoundCloudTrack>.rankedFor(target: SoundCloudTrack): List<SoundCloudTrack> =
        mapNotNull { track -> spotifyMatchScore(target, track)?.let { track to it } }
            .sortedByDescending { it.second }.map { it.first }

    private fun unavailable() = TrackUnavailableException(tr("Не удалось найти источник звука для трека Spotify."))
}
