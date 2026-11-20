package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import com.example.myapplication.i18n.tr
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Spotify's Web API. OAuth tokens are sent only to Spotify and kept out of Android backups. */
internal class SpotifyClient private constructor(context: Context) {
    private class Session(val clientId: String, val access: String, val refresh: String, val expiresAt: Long, val name: String)

    private val prefs = context.getSharedPreferences("spotify_session", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var session: Session? = restore()
    private val _account = MutableStateFlow(session?.name)
    val account = _account.asStateFlow()
    val clientId: String get() = prefs.getString("client_id", null).orEmpty()

    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun signIn(clientId: String, openBrowser: suspend (Uri) -> Unit) {
        require(clientId.matches(Regex("[a-fA-F0-9]{32}")))
        val code = spotifyAuthorize(clientId, openBrowser)
        val token = exchange(
            clientId, "authorization_code", "code" to code.code,
            "redirect_uri" to code.redirect, "code_verifier" to code.verifier
        )
        val provisional = token.session(clientId, "Spotify")
        val profile = request(api("me"), provisional.access).spotifyObject()
        val name = profile?.string("display_name") ?: profile?.string("id") ?: "Spotify"
        mutex.withLock { store(token.session(clientId, name)) }
    }

    suspend fun signOut() = mutex.withLock { clear() }

    private fun restore(): Session? {
        val client = clientId.takeIf { it.isNotBlank() } ?: return null
        val refresh = prefs.getString("refresh", null)?.takeIf { it.isNotBlank() } ?: return null
        return Session(client, prefs.getString("access", null).orEmpty(), refresh, prefs.getLong("expiry", 0), prefs.getString("name", "Spotify") ?: "Spotify")
    }

    private fun store(next: Session) {
        prefs.edit().putString("client_id", next.clientId).putString("access", next.access)
            .putString("refresh", next.refresh).putLong("expiry", next.expiresAt).putString("name", next.name).apply()
        session = next
        _account.value = next.name
    }

    private fun clear() {
        prefs.edit().remove("access").remove("refresh").remove("expiry").remove("name").apply()
        session = null
        _account.value = null
    }

    private fun JsonObject.session(clientId: String, name: String, previousRefresh: String? = null): Session {
        val access = string("access_token") ?: throw SpotifyFailure(tr("Spotify не подтвердил вход."))
        val refresh = string("refresh_token") ?: previousRefresh ?: throw SpotifyFailure(tr("Spotify не подтвердил вход."))
        val seconds = number("expires_in")?.coerceIn(60, 86_400) ?: 3600
        return Session(clientId, access, refresh, System.currentTimeMillis() + seconds * 1000, name)
    }

    private suspend fun exchange(clientId: String, grant: String, vararg params: Pair<String, String>): JsonObject {
        val form = FormBody.Builder().add("client_id", clientId).add("grant_type", grant)
        for ((name, value) in params) form.add(name, value)
        val request = Request.Builder().url("$SPOTIFY_ACCOUNTS/api/token").post(form.build()).build()
        return execute(request).spotifyObject() ?: throw SpotifyFailure(tr("Spotify не подтвердил вход."))
    }

    private suspend fun token(rejected: String? = null): Session = mutex.withLock {
        val current = session ?: throw SpotifyFailure(tr("Войдите в Spotify, чтобы открыть этот раздел."), 401)
        if (current.access.isNotEmpty() && current.access != rejected && current.expiresAt > System.currentTimeMillis() + 60_000) {
            return@withLock current
        }
        try {
            exchange(current.clientId, "refresh_token", "refresh_token" to current.refresh)
                .session(current.clientId, current.name, current.refresh).also(::store)
        } catch (e: SpotifyFailure) {
            if (e.status == 400 || e.status == 401) clear()
            throw e
        }
    }

    private fun api(path: String, vararg params: Pair<String, String>): HttpUrl =
        API.newBuilder().addPathSegments(path)
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }.build()

    private suspend fun request(url: HttpUrl, token: String, method: String = "GET"): JsonElement? {
        check(url.scheme == "https" && url.host == API.host && url.port == API.port &&
            url.username.isEmpty() && url.password.isEmpty() && url.encodedPath.startsWith("/v1/"))
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .method(method, if (method == "PUT") ByteArray(0).toRequestBody() else null).build()
        return execute(req)
    }

    private suspend fun request(url: HttpUrl, method: String = "GET"): JsonElement? {
        val active = token()
        return try {
            request(url, active.access, method)
        } catch (e: SpotifyFailure) {
            if (e.status != 401) throw e
            val refreshed = token(rejected = active.access)
            try {
                request(url, refreshed.access, method)
            } catch (failure: SpotifyFailure) {
                if (failure.status == 401) mutex.withLock { if (session === refreshed) clear() }
                throw failure
            }
        }
    }

    private suspend fun execute(request: Request): JsonElement? = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val message = when (response.code) {
                    400, 401 -> tr("Проверьте вход в Spotify.")
                    403 -> tr("Spotify не предоставил доступ к этому разделу.")
                    404 -> tr("Spotify не нашёл этот раздел.")
                    429 -> tr("Слишком много запросов к Spotify. Попробуйте позже.")
                    else -> tr("Ошибка Spotify: HTTP %s.", response.code)
                }
                throw SpotifyFailure(message, response.code)
            }
            if (response.code == 204 || response.body?.contentLength() == 0L) return@use null
            val text = response.body?.string()?.takeIf { it.isNotBlank() } ?: return@use null
            try {
                JsonParser.parseString(text)
            } catch (_: com.google.gson.JsonParseException) {
                throw SpotifyFailure(tr("Spotify вернул непонятный ответ."))
            }
        }
    }

    private suspend fun get(url: HttpUrl): JsonObject =
        request(url).spotifyObject() ?: throw SpotifyFailure(tr("Spotify вернул непонятный ответ."))

    internal suspend fun track(id: String): SoundCloudTrack {
        require(id.isSpotifyId())
        return spotifyTrack(get(api("tracks/$id"))) ?: throw SpotifyFailure(tr("Spotify не нашёл этот трек."))
    }

    internal suspend fun search(query: String, offset: Int = 0): YtSearchPage {
        val types = if (offset == 0) "track,album,playlist,artist" else "track"
        val root = get(api("search", "q" to query, "type" to types, "limit" to "10", "offset" to offset.toString()))
        val tracks = root.obj("tracks")
        return YtSearchPage(
            tracks = tracks?.items("items").orEmpty().mapNotNull(::spotifyTrack).distinctBy { it.id },
            artists = root.obj("artists")?.items("items").orEmpty().mapNotNull(::spotifyArtist).distinctBy { it.id },
            albums = root.obj("albums")?.items("items").orEmpty().mapNotNull { spotifySet(it, true) }.distinctBy { it.id },
            playlists = root.obj("playlists")?.items("items").orEmpty().mapNotNull { spotifySet(it, false) }.distinctBy { it.id },
            continuation = (offset + 10).toString().takeIf { offset < 990 && tracks?.string("next") != null }
        )
    }

    internal suspend fun home(): List<YtShelf> = coroutineScope {
        val liked = async { get(api("me/tracks", "limit" to "1")).number("total") ?: 0 }
        val playlists = async { get(api("me/playlists", "limit" to "20")).items("items").mapNotNull { spotifySet(it, false) } }
        val albums = async { optional { get(api("me/albums", "limit" to "20")).items("items").mapNotNull { it.obj("album")?.let { a -> spotifySet(a, true) } } }.orEmpty() }
        val recent = async { optional {
            get(api("me/player/recently-played", "limit" to "20")).items("items")
                .mapNotNull { it.obj("track")?.let(::spotifyTrack) }.distinctBy { it.id }
        }.orEmpty() }
        val top = async { optional {
            get(api("me/top/tracks", "limit" to "20", "time_range" to "short_term")).items("items")
                .mapNotNull(::spotifyTrack)
        }.orEmpty() }
        val artists = async { optional {
            get(api("me/following", "type" to "artist", "limit" to "20")).obj("artists")?.items("items")
                .orEmpty().mapNotNull(::spotifyArtist)
        }.orEmpty() }
        val library = YtShelf(tr("Медиатека"), emptyList(), listOf(likedSet(liked.await())) + playlists.await())
        listOf(
            library, YtShelf(tr("Недавно прослушано"), recent.await(), emptyList()),
            YtShelf(tr("Ваши любимые треки"), top.await(), emptyList()),
            YtShelf(tr("Альбомы"), emptyList(), albums.await()),
            YtShelf(tr("Подписки"), emptyList(), emptyList(), artists.await())
        ).map { it.copy(tracks = it.tracks.distinctBy { track -> track.id }, sets = it.sets.distinctBy { set -> set.id }, artists = it.artists.distinctBy { artist -> artist.id }) }
    }

    private fun likedSet(count: Long = 0) = SoundCloudPlaylist(
        id = spotifyKey(SPOTIFY_LIKED), title = tr("Понравившиеся треки"), trackCount = count.toInt(),
        permalinkUrl = SPOTIFY_LIKED
    )

    suspend fun set(ref: String): SoundCloudPlaylist {
        if (ref == SPOTIFY_LIKED) return likedSet()
        val album = ref.spotifyIdFor(SPOTIFY_ALBUM)
        val playlist = ref.spotifyIdFor(SPOTIFY_PLAYLIST)
        val raw = when {
            album != null -> get(api("albums/$album"))
            playlist != null -> get(api("playlists/$playlist"))
            else -> throw SpotifyFailure(tr("Не удалось открыть ссылку Spotify."))
        }
        return spotifySet(raw, album != null) ?: throw SpotifyFailure(tr("Spotify не нашёл этот раздел."))
    }

    suspend fun setTracks(ref: String): List<SoundCloudTrack> {
        val album = ref.spotifyIdFor(SPOTIFY_ALBUM)
        val playlist = ref.spotifyIdFor(SPOTIFY_PLAYLIST)
        val tracks = when {
            ref == SPOTIFY_LIKED -> pages(api("me/tracks", "limit" to "50")).mapNotNull { it.obj("track")?.let(::spotifyTrack) }
            album != null -> {
                val metadata = get(api("albums/$album"))
                val page = metadata.obj("tracks") ?: return emptyList()
                pages(api("albums/$album/tracks", "limit" to "50"), page).mapNotNull { spotifyTrack(it, metadata) }
            }
            playlist != null -> playlistItems(playlist).mapNotNull { (it.obj("item") ?: it.obj("track"))?.let(::spotifyTrack) }
            else -> emptyList()
        }
        return tracks.distinctBy { it.id }
    }

    private suspend fun playlistItems(id: String): List<JsonObject> = try {
        pages(api("playlists/$id/items", "limit" to "50"))
    } catch (e: SpotifyFailure) {
        if (e.status != 403 && e.status != 404) throw e
        // Apps still using the older extended-access contract can read `tracks`.
        pages(api("playlists/$id/tracks", "limit" to "50"))
    }

    private suspend fun pages(start: HttpUrl, initial: JsonObject? = null): List<JsonObject> {
        var page = initial ?: get(start)
        val entries = mutableListOf<JsonObject>()
        val seen = mutableSetOf(start)
        repeat(20) {
            entries += page.items("items")
            val next = page.string("next")?.let { runCatching { it.toHttpUrl() }.getOrNull() }
                ?.takeIf { it.host == API.host && it.scheme == "https" && it.encodedPath == start.encodedPath }
                ?: return entries
            if (!seen.add(next)) return entries
            if (it < 19) page = get(next)
        }
        return entries
    }

    /** Development-mode Spotify apps may receive albums but not the old top-track endpoint. */
    suspend fun artist(id: String): YtArtistPage = coroutineScope {
        require(id.isSpotifyId())
        val profile = async { spotifyArtist(get(api("artists/$id"))) ?: throw SpotifyFailure(tr("Spotify не нашёл исполнителя.")) }
        val albums = async {
            get(api("artists/$id/albums", "include_groups" to "album,single", "limit" to "20"))
                .items("items").mapNotNull { spotifySet(it, true) }.distinctBy { it.id }
        }
        val hits = async { optional { get(api("artists/$id/top-tracks")).items("tracks").mapNotNull(::spotifyTrack) }.orEmpty() }
        val data = profile.await()
        val releases = albums.await()
        val tracks = hits.await().ifEmpty {
            val quoted = data.username.orEmpty().replace("\"", "")
            search("artist:\"$quoted\"").tracks.filter { song -> song.artists.orEmpty().any { it.permalinkUrl == data.permalinkUrl } }
        }
        YtArtistPage(data, tracks.distinctBy { it.id }, null, releases)
    }

    suspend fun albumOf(track: SoundCloudTrack): SoundCloudPlaylist? {
        val id = track.spotifyId ?: return null
        return get(api("tracks/$id")).obj("album")?.let { spotifySet(it, true) }
    }

    suspend fun related(seed: SoundCloudTrack): List<SoundCloudTrack> {
        val id = seed.spotifyId ?: return emptyList()
        val recommended = optional {
            get(api("recommendations", "seed_tracks" to id, "limit" to "20")).items("tracks").mapNotNull(::spotifyTrack)
        }.orEmpty()
        if (recommended.isNotEmpty()) return recommended.distinctBy { it.id }.filterNot { it.id == seed.id }
        val artistId = (seed.artists?.firstOrNull() ?: seed.user)?.permalinkUrl.spotifyIdFor(SPOTIFY_ARTIST) ?: return emptyList()
        val page = artist(artistId)
        val tracks = page.topSongs.filterNot { it.id == seed.id }
        if (tracks.isNotEmpty()) return tracks
        return page.releases.take(2).flatMap { optional { setTracks(it.permalinkUrl.orEmpty()) }.orEmpty() }
            .distinctBy { it.id }.filterNot { it.id == seed.id }
    }

    suspend fun inLibrary(ref: String): Boolean {
        val data = request(api("me/library/contains", "uris" to ref))
        val value = data?.takeIf { it.isJsonArray }?.asJsonArray?.firstOrNull()
        return value?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true
    }

    suspend fun save(ref: String, saved: Boolean) {
        require(ref.spotifyIdFor(SPOTIFY_TRACK) != null || ref.spotifyIdFor(SPOTIFY_ARTIST) != null)
        request(api("me/library", "uris" to ref), method = if (saved) "PUT" else "DELETE")
    }

    private suspend fun <T> optional(fetch: suspend () -> T): T? = try {
        fetch()
    } catch (e: SpotifyFailure) {
        if (e.status == 401 || e.status == 429) throw e
        null
    } catch (_: IOException) {
        null
    }

    companion object {
        private val API = "https://api.spotify.com/v1/".toHttpUrl()
        @Volatile private var instance: SpotifyClient? = null

        fun get(context: Context): SpotifyClient = instance ?: synchronized(this) {
            instance ?: SpotifyClient(context.applicationContext).also { instance = it }
        }
    }
}
