package com.example.myapplication.data

import android.content.Context
import android.util.Log
import com.example.myapplication.i18n.tr
import com.google.gson.GsonBuilder
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Catalog and library through the same Spotify Web Player transport as the YouCloud website. */
internal class SpotifyClient private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("spotify_session", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var session: SpotifyWebSession? = null
    private val _account = MutableStateFlow(prefs.getString("web_account", null))
    val account = _account.asStateFlow()
    private val gson = GsonBuilder().serializeNulls().create()
    private val http = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()
    private val queries = SpotifyQueries(http)

    init {
        // Older builds stored their own OAuth credentials. This login gets its session from Spotify's page.
        if (prefs.contains("client_id")) {
            prefs.edit().remove("client_id").remove("access").remove("refresh").remove("expiry").remove("name").apply()
        }
    }

    private data class Profile(val id: String, val name: String)

    private suspend fun profile(candidate: SpotifyWebSession): Profile {
        val user = query("profileAttributes", session = candidate).obj("me")?.obj("profile")
            ?: throw SpotifyFailure(tr("Spotify не подтвердил вход."))
        val id = user.string("username") ?: user.string("uri")?.takeIf { it.startsWith("spotify:user:") }
            ?.removePrefix("spotify:user:")?.takeIf { it.isNotBlank() }
            ?: throw SpotifyFailure(tr("Spotify не подтвердил вход."))
        return Profile(id, user.string("name") ?: id)
    }

    suspend fun signIn(candidate: SpotifyWebSession) {
        val user = profile(candidate)
        withContext(Dispatchers.IO) { android.webkit.CookieManager.getInstance().flush() }
        mutex.withLock {
            session = candidate
            prefs.edit().putString("web_account_id", user.id).putString("web_account", user.name).apply()
            _account.value = user.name
        }
    }

    suspend fun signOut() = mutex.withLock {
        clear()
        SpotifyLogin.disconnect()
    }

    private fun clear() {
        prefs.edit().remove("web_account").remove("web_account_id").apply()
        session = null
        _account.value = null
    }

    private fun signInRequired() = SpotifyFailure(tr("Войдите в Spotify, чтобы открыть этот раздел."), 401)

    private suspend fun active(rejected: SpotifyWebSession? = null): SpotifyWebSession = mutex.withLock {
        if (_account.value == null) throw signInRequired()
        session?.takeIf { it.token != rejected?.token && it.expiresAt > System.currentTimeMillis() + 60_000 }
            ?.let { return@withLock it }
        val renewed = SpotifyLogin.refresh(context, rejected?.token)
        if (renewed == null) {
            if (!withContext(Dispatchers.Main) { SpotifyLogin.hasCookie() }) {
                clear()
                throw signInRequired()
            }
            throw SpotifyFailure(tr("Не удалось обновить вход в Spotify. Попробуйте ещё раз."))
        }
        if (profile(renewed).id != prefs.getString("web_account_id", null)) {
            clear()
            throw signInRequired()
        }
        session = renewed
        renewed
    }

    private suspend fun <T> authorized(explicit: SpotifyWebSession? = null, request: suspend (SpotifyWebSession) -> T): T {
        val active = explicit ?: active()
        try {
            return request(active)
        } catch (e: SpotifyFailure) {
            if (explicit != null || e.status != 401) throw e
            return request(active(rejected = active))
        }
    }

    private fun authenticatedRequest(url: HttpUrl, auth: SpotifyWebSession): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", "Bearer ${auth.token}")
            .header("Client-Token", auth.clientToken)
            .header("App-Platform", "WebPlayer")
            .header("Origin", SPOTIFY_ORIGIN)
            .header("Referer", SPOTIFY_SITE)
            .header("User-Agent", SPOTIFY_AGENT)
            .header("Accept", "application/json")

    private suspend fun query(
        operation: String,
        variables: Map<String, Any?> = emptyMap(),
        session: SpotifyWebSession? = null
    ): JsonObject = authorized(session) { auth ->
        val hash = queries.hash(operation)
        try {
            runQuery(operation, variables, hash, auth)
        } catch (e: SpotifyQueryChanged) {
            queries.refresh(operation, hash)
            val updated = queries.hash(operation)
            if (updated == hash) throw SpotifyFailure(tr("Spotify обновил каталог. Попробуйте ещё раз позже."))
            try {
                runQuery(operation, variables, updated, auth)
            } catch (_: SpotifyQueryChanged) {
                throw SpotifyFailure(tr("Spotify обновил каталог. Попробуйте ещё раз позже."))
            }
        }
    }

    private class SpotifyQueryChanged : IOException()

    private suspend fun runQuery(
        operation: String,
        variables: Map<String, Any?>,
        hash: String,
        auth: SpotifyWebSession
    ): JsonObject {
        val payload = gson.toJson(
            mapOf("operationName" to operation, "variables" to variables,
                "extensions" to mapOf("persistedQuery" to mapOf("version" to 1, "sha256Hash" to hash)))
        )
        val request = authenticatedRequest(pathfinder, auth).post(payload.toRequestBody(json)).build()
        val response = execute(request)
        val data = response.obj("data")
        val errors = response.items("errors")
        if (errors.isNotEmpty() && (data == null || operation in libraryWrites)) {
            val errorsText = errors.mapNotNull { it.string("message") }.joinToString(" ")
            Log.w("SpotifyClient", "GraphQL $operation error: $errorsText")
            if ("PersistedQueryNotFound" in errorsText || "persisted query not found" in errorsText.lowercase()) {
                throw SpotifyQueryChanged()
            }
            if ("unauthorized" in errorsText.lowercase() || "token expired" in errorsText.lowercase()) {
                throw SpotifyFailure(tr("Проверьте вход в Spotify."), 401)
            }
            throw SpotifyFailure(tr("Spotify не предоставил доступ к этому разделу."))
        }
        return data ?: throw SpotifyFailure(tr("Spotify вернул непонятный ответ."))
    }

    private suspend fun execute(request: Request): JsonObject = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w("SpotifyClient", "HTTP ${response.code} on ${request.url.encodedPath}")
                val message = when (response.code) {
                    401 -> tr("Проверьте вход в Spotify.")
                    403 -> tr("Spotify не предоставил доступ к этому разделу.")
                    404 -> tr("Spotify не нашёл этот раздел.")
                    429 -> tr("Слишком много запросов к Spotify. Попробуйте позже.")
                    else -> tr("Ошибка Spotify: HTTP %s.", response.code)
                }
                throw SpotifyFailure(message, response.code)
            }
            val text = response.body?.string().orEmpty()
            val result = runCatching { JsonParser.parseString(text).spotifyObject() }.getOrNull()
            result ?: throw SpotifyFailure(tr("Spotify вернул непонятный ответ."))
        }
    }

    suspend fun track(id: String): SoundCloudTrack = spotifyTrack(trackData(id))
        ?: throw SpotifyFailure(tr("Spotify не нашёл этот трек."))

    private suspend fun trackData(id: String): JsonObject {
        require(id.isSpotifyId())
        return query("getTrack", mapOf("uri" to SPOTIFY_TRACK + id)).obj("trackUnion")
            ?: throw SpotifyFailure(tr("Spotify не нашёл этот трек."))
    }

    suspend fun search(query: String, offset: Int = 0): YtSearchPage {
        val operation = if (offset == 0) "searchDesktop" else "searchTracks"
        val fields = mapOf("searchTerm" to query, "offset" to offset, "limit" to if (offset == 0) 15 else 20,
            "numberOfTopResults" to 5, "includeAudiobooks" to false)
        val args = if (offset != 0) fields else fields + mapOf(
            "includeArtistHasConcertsField" to false, "includePreReleases" to false,
            "includeLocalConcertsField" to false, "includeAuthors" to false
        )
        val root = query(operation, args).obj("searchV2")
        val tracks = root?.obj("tracksV2")
        val items = tracks?.items("items").orEmpty()
        val next = tracks?.obj("pagingInfo")?.number("nextOffset") ?: (offset + items.size).toLong()
        return YtSearchPage(
            tracks = items.mapNotNull { it.obj("item")?.let(::spotifyTrack) }.distinctBy { it.id },
            artists = root?.obj("artists")?.items("items").orEmpty().mapNotNull(::spotifyArtist).filter { it.id != null }.distinctBy { it.id },
            albums = root?.obj("albumsV2")?.items("items").orEmpty().mapNotNull { spotifySet(it, true) }.distinctBy { it.id },
            playlists = root?.obj("playlists")?.items("items").orEmpty().mapNotNull { spotifySet(it, false) }.distinctBy { it.id },
            continuation = next.toString().takeIf { items.isNotEmpty() && next > offset && next < (tracks?.number("totalCount") ?: 0L) }
        )
    }

    suspend fun home(): List<YtShelf> = coroutineScope {
        val home = async { spotifyHomeRows(query("home", mapOf(
            "timeZone" to TimeZone.getDefault().id, "sp_t" to "", "facet" to "", "sectionItemsLimit" to 12,
            "homeEndUserIntegration" to "INTEGRATION_WEB_PLAYER", "includeHomeChapterVideoCards" to false,
            "includeEpisodeContentRatingsV2" to false
        ))) }
        val library = async { optional {
            query("libraryV3", mapOf(
                "filters" to emptyList<String>(), "order" to null, "textFilter" to "",
                "features" to listOf("LIKED_SONGS", "YOUR_EPISODES_V2"), "limit" to 80, "offset" to 0,
                "flatten" to true, "folderUri" to null, "includeFoldersWhenFlattening" to true
            )).obj("me")?.obj("libraryV3")?.items("items").orEmpty().mapNotNull { it.obj("item") }
        }.orEmpty() }
        val liked = async {
            optional {
                val page = likedPage(0, 16)
                val count = page.number("totalCount") ?: 0L
                val tracks = page.items("items").mapNotNull { it.obj("track")?.let(::spotifyTrack) }
                count to tracks
            } ?: (0L to emptyList())
        }
        val items = library.await()
        val (likedCount, likedTracks) = liked.await()
        val sets = items.mapNotNull { spotifySet(it, false) ?: spotifySet(it, true) }.distinctBy { it.id }
        val artists = items.filter { it.obj("data")?.string("__typename") == "Artist" }.mapNotNull(::spotifyArtist).distinctBy { it.id }
        buildList {
            add(YtShelf(tr("Медиатека"), emptyList(), listOf(likedSet(likedCount, likedTracks)) + sets))
            addAll(home.await())
            if (artists.isNotEmpty()) add(YtShelf(tr("Подписки"), emptyList(), emptyList(), artists))
        }
    }

    private fun likedSet(count: Long = 0, tracks: List<SoundCloudTrack> = emptyList()) = SoundCloudPlaylist(
        id = spotifyKey(SPOTIFY_LIKED),
        title = tr("Понравившиеся треки"),
        tracks = tracks,
        trackCount = count.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        permalinkUrl = SPOTIFY_LIKED
    )

    private suspend fun likedPage(offset: Int, limit: Int): JsonObject =
        query("fetchLibraryTracks", mapOf("offset" to offset, "limit" to limit))
            .obj("me")?.obj("library")?.obj("tracks") ?: throw SpotifyFailure(tr("Spotify не предоставил доступ к этому разделу."))

    private suspend fun album(ref: String, offset: Int, limit: Int): JsonObject =
        query("getAlbum", mapOf("uri" to ref, "locale" to "", "offset" to offset, "limit" to limit))
            .obj("albumUnion") ?: throw SpotifyFailure(tr("Spotify не нашёл этот раздел."))

    private suspend fun playlist(ref: String, offset: Int, limit: Int): JsonObject =
        query("fetchPlaylist", mapOf("uri" to ref, "offset" to offset, "limit" to limit, "enableWatchFeedEntrypoint" to false))
            .obj("playlistV2") ?: throw SpotifyFailure(tr("Spotify не нашёл этот раздел."))

    suspend fun set(ref: String): SoundCloudPlaylist {
        if (ref == SPOTIFY_LIKED) return likedSet()
        val set = when {
            ref.spotifyIdFor(SPOTIFY_ALBUM) != null -> spotifySet(album(ref, 0, 1), true)
            ref.spotifyIdFor(SPOTIFY_PLAYLIST) != null -> spotifySet(playlist(ref, 0, 1), false)
            else -> null
        }
        return set ?: throw SpotifyFailure(tr("Spotify не нашёл этот раздел."))
    }

    suspend fun setTracks(ref: String): List<SoundCloudTrack> = when {
        ref == SPOTIFY_LIKED -> readSet { offset ->
            val page = likedPage(offset, pageSize)
            trackPage(page) { it.obj("track")?.let(::spotifyTrack) }
        }
        ref.spotifyIdFor(SPOTIFY_ALBUM) != null -> readSet { offset ->
            val data = album(ref, offset, pageSize)
            trackPage(data.obj("tracksV2")) { spotifyTrack(it.obj("track") ?: it, data) }
        }
        ref.spotifyIdFor(SPOTIFY_PLAYLIST) != null -> readSet { offset ->
            trackPage(playlist(ref, offset, pageSize).obj("content")) { it.obj("itemV2")?.let(::spotifyTrack) }
        }
        else -> emptyList()
    }

    private data class TrackPage(val tracks: List<SoundCloudTrack>, val size: Int, val total: Long)

    private fun trackPage(data: JsonObject?, parse: (JsonObject) -> SoundCloudTrack?): TrackPage {
        val entries = data?.items("items").orEmpty()
        return TrackPage(entries.mapNotNull(parse), entries.size, data?.number("totalCount") ?: entries.size.toLong())
    }

    private suspend fun readSet(fetch: suspend (Int) -> TrackPage): List<SoundCloudTrack> {
        val tracks = mutableListOf<SoundCloudTrack>()
        var offset = 0
        do {
            val page = fetch(offset)
            tracks += page.tracks
            if (page.size == 0) break
            offset += page.size
        } while (offset < minOf(page.total, 1000))
        return tracks.distinctBy { it.id }
    }

    suspend fun artist(id: String): YtArtistPage {
        require(id.isSpotifyId())
        val data = query("queryArtistOverview", mapOf("uri" to SPOTIFY_ARTIST + id, "locale" to "", "includePrerelease" to false))
            .obj("artistUnion") ?: throw SpotifyFailure(tr("Spotify не нашёл исполнителя."))
        val artist = spotifyArtist(data)?.takeIf { it.permalinkUrl != null }
            ?: throw SpotifyFailure(tr("Spotify не нашёл исполнителя."))
        val discography = data.obj("discography")
        val tracks = discography?.obj("topTracks")?.items("items").orEmpty().mapNotNull { it.obj("track")?.let(::spotifyTrack) }
        val releases = listOf("albums", "singles", "compilations").flatMap { kind ->
            discography?.obj(kind)?.items("items").orEmpty().flatMap { entry ->
                entry.obj("releases")?.items("items").orEmpty().mapNotNull { spotifySet(it, true) }
            }
        }
        return YtArtistPage(artist, tracks.distinctBy { it.id }, null, releases.distinctBy { it.id })
    }

    suspend fun albumOf(track: SoundCloudTrack): SoundCloudPlaylist? {
        val id = track.spotifyId ?: return null
        val album = trackData(id).obj("albumOfTrack") ?: return null
        val ref = album.string("uri")?.takeIf { it.spotifyIdFor(SPOTIFY_ALBUM) != null } ?: return null
        return spotifySet(album, true) ?: set(ref)
    }

    suspend fun related(seed: SoundCloudTrack): List<SoundCloudTrack> {
        if (seed.spotifyId == null) return emptyList()
        val radio = optional {
            authorized { auth ->
                val url = spclient.newBuilder().addPathSegments("inspiredby-mix/v2/seed_to_playlist")
                    .addPathSegment(seed.urn.orEmpty()).addQueryParameter("response-format", "json").build()
                execute(authenticatedRequest(url, auth).get().build())
            }.items("mediaItems").firstNotNullOfOrNull { it.string("uri")?.takeIf { uri -> uri.spotifyIdFor(SPOTIFY_PLAYLIST) != null } }
                ?.let { setTracks(it) }
        }.orEmpty().filterNot { it.id == seed.id }
        if (radio.isNotEmpty()) return radio
        val artistId = (seed.artists?.firstOrNull() ?: seed.user)?.permalinkUrl.spotifyIdFor(SPOTIFY_ARTIST)
            ?: return emptyList()
        return artist(artistId).topSongs.filterNot { it.id == seed.id }
    }

    suspend fun inLibrary(ref: String): Boolean =
        query("areEntitiesInLibrary", mapOf("uris" to listOf(ref))).items("lookup").firstOrNull()
            ?.obj("data")?.get("saved")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean == true

    suspend fun save(ref: String, saved: Boolean) {
        require(ref.spotifyIdFor(SPOTIFY_TRACK) != null || ref.spotifyIdFor(SPOTIFY_ARTIST) != null)
        query(if (saved) "addToLibrary" else "removeFromLibrary", mapOf("libraryItemUris" to listOf(ref)))
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
        private val pathfinder = "https://api-partner.spotify.com/pathfinder/v2/query".toHttpUrl()
        private val spclient = "https://spclient.wg.spotify.com/".toHttpUrl()
        private val json = "application/json".toMediaType()
        private val libraryWrites = setOf("addToLibrary", "removeFromLibrary")
        private const val pageSize = 100
        @Volatile private var instance: SpotifyClient? = null

        fun get(context: Context): SpotifyClient = instance ?: synchronized(this) {
            instance ?: SpotifyClient(context.applicationContext).also { instance = it }
        }
    }
}
