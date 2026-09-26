package com.example.myapplication.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** A signed-in music.youtube.com session, as the login page leaves it. */
data class YtAuth(
    val cookie: String,
    val visitorData: String?,
    // Which of the Google accounts signed in on the page it is: "0" for the first.
    val authUser: String = "0"
) {
    val sapisid: String?
        get() = cookieValue("SAPISID") ?: cookieValue("__Secure-3PAPISID")

    private fun cookieValue(name: String): String? = cookie.split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }
}

/**
 * The Authorization header Google's web clients sign requests with: a SHA-1 over the time, the
 * SAPISID cookie and the page's origin.
 */
fun sapisidAuthorization(sapisid: String, origin: String): String {
    val timestamp = System.currentTimeMillis() / 1000
    val digest = MessageDigest.getInstance("SHA-1").digest("$timestamp $sapisid $origin".toByteArray())
    return "SAPISIDHASH ${timestamp}_" + digest.joinToString("") { "%02x".format(it) }
}

/** A row of YouTube Music's home: songs to play in place, and sets to open. */
data class YtShelf(
    val title: String,
    val tracks: List<SoundCloudTrack>,
    val sets: List<SoundCloudPlaylist>
)

class YtMusicException(val code: Int, detail: String) : Exception("YouTube Music HTTP $code: $detail")

const val YT_TRACK_URN = "ytmusic:track:"
const val YT_SET_REF = "ytmusic:set:"
const val YT_ARTIST_REF = "ytmusic:artist:"

// YouTube ids live below this; see [youTubeTrackId].
const val YT_ID_BASE = 2_000_000_000_000L

/**
 * An app-wide id for a YouTube video. The app keys tracks by Long and YouTube's ids are
 * 11-character strings, so they are hashed (FNV-1a, 40 bits) into a negative range of their own:
 * far below Yandex's, which sit around minus a billion, and apart from imported files' timestamps.
 */
fun youTubeTrackId(videoId: String): Long {
    var hash = -0x340d631b7bdddcdbL
    for (char in videoId) {
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return -(YT_ID_BASE + (hash and 0xFF_FFFF_FFFFL))
}

val SoundCloudTrack.youTubeVideoId: String?
    get() = urn?.takeIf { it.startsWith(YT_TRACK_URN) }?.removePrefix(YT_TRACK_URN)

/** Kept on the device as a plain file (Yandex, YouTube), not in SoundCloud's HLS cache. */
fun isProgressiveSource(urn: String?): Boolean =
    urn != null && (urn.startsWith("yandex:") || urn.startsWith("ytmusic:"))

/**
 * YouTube Music's web API ("InnerTube") as music.youtube.com itself calls it — the same requests
 * sigma67/ytmusicapi makes — for what the app needs: the personal home, radio from a track, the
 * tracks of a playlist, mix or album, the account's name and likes.
 *
 * Signed-in requests carry the site's cookies and a SAPISIDHASH built from them. Without a
 * session YouTube still answers, with what it shows everyone.
 */
class YouTubeMusicClient(private val authProvider: () -> YtAuth?) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var anonymousVisitor: String? = null

    /** Home's rows, the personal ones included; they arrive a few at a time, so a few pages. */
    suspend fun home(): List<YtShelf> {
        val first = post("browse", json { addProperty("browseId", "FEmusic_home") })
        val sections = first.at(
            "contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content",
            "sectionListRenderer"
        )
        val shelves = sections.arr("contents").mapNotNull(::parseShelf).toMutableList()
        var continuation = sections.str("continuations", 0, "nextContinuationData", "continuation")
        for (page in 1..HOME_EXTRA_PAGES) {
            val token = continuation ?: break
            val more = post("browse", JsonObject(), "&ctoken=$token&continuation=$token&type=next")
                .at("continuationContents", "sectionListContinuation")
            shelves += more.arr("contents").mapNotNull(::parseShelf)
            continuation = more.str("continuations", 0, "nextContinuationData", "continuation")
        }
        return shelves.filter { it.tracks.isNotEmpty() || it.sets.isNotEmpty() }
    }

    /** YouTube Music's radio from [videoId]: what it plays when that track is started. */
    suspend fun radio(videoId: String): List<SoundCloudTrack> = watchQueue(json {
        addProperty("videoId", videoId)
        addProperty("playlistId", "RDAMVM$videoId")
        addProperty("params", "wAEB")
    })

    /** Tracks of a set from [home]: a playlist or mix (`VL…`) or an album (`MPRE…`). */
    suspend fun setTracks(set: SoundCloudPlaylist): List<SoundCloudTrack> {
        val (browseId, playlistId) = parseSetRef(set.permalinkUrl) ?: return emptyList()
        val fromPage = if (browseId.isNotBlank()) {
            val page = post("browse", json { addProperty("browseId", browseId) })
            // The list itself, not the suggestions a playlist page may add below it.
            val shelf = page.findAll("musicPlaylistShelfRenderer").firstOrNull()
                ?: page.findAll("musicShelfRenderer").firstOrNull()
            // Album rows leave out the artist; the album's header names it.
            val albumArtist = page.findAll("musicResponsiveHeaderRenderer").firstOrNull()
                ?.runs("straplineTextOne")
            shelf.arr("contents").mapNotNull { item ->
                item.at("musicResponsiveListItemRenderer")?.let { row ->
                    parseSongRow(row, fallbackArtwork = set.artworkUrl, fallbackArtist = albumArtist)
                }
            }
        } else {
            emptyList()
        }
        // Radio-style mixes have no page of their own; their tracks are in the watch queue.
        if (fromPage.isNotEmpty() || playlistId.isBlank()) return fromPage.distinctBy { it.id }
        return watchQueue(json { addProperty("playlistId", playlistId) })
    }

    suspend fun accountName(): String? {
        val header = post("account/account_menu", JsonObject()).at(
            "actions", 0, "openPopupAction", "popup", "multiPageMenuRenderer", "header",
            "activeAccountHeaderRenderer"
        )
        return header.runs("accountName") ?: header.runs("channelHandle")
    }

    /** Thumbs up, or back to neither. */
    suspend fun rate(videoId: String, like: Boolean) {
        post(if (like) "like/like" else "like/removelike", json {
            add("target", json { addProperty("videoId", videoId) })
        })
    }

    private suspend fun watchQueue(body: JsonObject): List<SoundCloudTrack> {
        body.addProperty("enablePersistentPlaylistPanel", true)
        body.addProperty("isAudioOnly", true)
        val panel = post("next", body).at(
            "contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer",
            "watchNextTabbedResultsRenderer", "tabs", 0, "tabRenderer", "content", "musicQueueRenderer",
            "content", "playlistPanelRenderer"
        )
        return panel.arr("contents").mapNotNull { item ->
            // Wrapped when YouTube offers the music video alongside the song.
            val video = item.at("playlistPanelVideoRenderer")
                ?: item.at("playlistPanelVideoWrapperRenderer", "primaryRenderer", "playlistPanelVideoRenderer")
            video?.let(::parseQueueVideo)
        }.distinctBy { it.id }
    }

    // ------------------------------------------------------------------------------ parsing

    private fun parseShelf(section: JsonElement): YtShelf? {
        val shelf = section.at("musicCarouselShelfRenderer") ?: return null
        val title = shelf.runs("header", "musicCarouselShelfBasicHeaderRenderer", "title") ?: return null
        val tracks = mutableListOf<SoundCloudTrack>()
        val sets = mutableListOf<SoundCloudPlaylist>()
        for (item in shelf.arr("contents")) {
            item.at("musicResponsiveListItemRenderer")?.let { row -> parseSongRow(row)?.let(tracks::add) }
            item.at("musicTwoRowItemRenderer")?.let { tile ->
                parseTileTrack(tile)?.let(tracks::add) ?: parseTileSet(tile)?.let(sets::add)
            }
        }
        return YtShelf(title, tracks.distinctBy { it.id }, sets.distinctBy { it.id })
    }

    private fun parseSongRow(
        row: JsonElement,
        fallbackArtwork: String? = null,
        fallbackArtist: String? = null
    ): SoundCloudTrack? {
        val videoId = row.str("playlistItemData", "videoId")
            ?: row.str(
                "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
                "playNavigationEndpoint", "watchEndpoint", "videoId"
            )
            ?: return null
        val columns = row.arr("flexColumns").map { it.at("musicResponsiveListItemFlexColumnRenderer", "text") }
        val title = columns.getOrNull(0).runsText() ?: return null
        val byline = columns.getOrNull(1)
        val artists = artistsOf(byline.arr("runs"))
            .ifEmpty { plainArtist(byline.runsText()) }
            .ifEmpty { plainArtist(fallbackArtist) }
        val duration = row.arr("fixedColumns")
            .firstNotNullOfOrNull { it.at("musicResponsiveListItemFixedColumnRenderer", "text").runsText() }
            ?.let(::parseDuration) ?: 0L
        val artwork = bestThumbnail(row.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
            ?: fallbackArtwork
        return track(videoId, title, artists, duration, artwork)
    }

    private fun parseQueueVideo(video: JsonElement): SoundCloudTrack? {
        val videoId = video.str("videoId") ?: return null
        val title = video.runs("title") ?: return null
        val artists = artistsOf(video.arr("longBylineText", "runs"))
            .ifEmpty { plainArtist(video.runs("shortBylineText")) }
        val duration = video.runs("lengthText")?.let(::parseDuration) ?: 0L
        return track(videoId, title, artists, duration, bestThumbnail(video.arr("thumbnail", "thumbnails")))
    }

    /** A tile that plays a song ("Listen again" mixes songs in with sets). */
    private fun parseTileTrack(tile: JsonElement): SoundCloudTrack? {
        val videoId = tile.str("navigationEndpoint", "watchEndpoint", "videoId") ?: return null
        val title = tile.runs("title") ?: return null
        val artists = artistsOf(tile.arr("subtitle", "runs")).ifEmpty { plainArtist(tile.runs("subtitle")) }
        val artwork = bestThumbnail(tile.arr("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        return track(videoId, title, artists, 0L, artwork)
    }

    private fun parseTileSet(tile: JsonElement): SoundCloudPlaylist? {
        val browse = tile.at("navigationEndpoint", "browseEndpoint") ?: return null
        val browseId = browse.str("browseId") ?: return null
        val pageType = browse.str(
            "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType"
        )
        val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE")
        val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL")
        // Artists, podcasts, channels: pages the app has nowhere to show.
        if (!isAlbum && !isPlaylist) return null
        val title = tile.runs("title") ?: return null
        val play = tile.at(
            "thumbnailOverlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
            "playNavigationEndpoint"
        )
        val playlistId = play.str("watchPlaylistEndpoint", "playlistId")
            ?: play.str("watchEndpoint", "playlistId")
            ?: browseId.takeIf { it.startsWith("VL") }?.removePrefix("VL")
            ?: ""
        return SoundCloudPlaylist(
            id = youTubeTrackId("set:$browseId"),
            title = title,
            artworkUrl = bestThumbnail(tile.arr("thumbnailRenderer", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
            permalinkUrl = "$YT_SET_REF$browseId:$playlistId",
            user = SoundCloudUser(username = tile.runs("subtitle")),
            isAlbum = isAlbum,
            setType = if (isAlbum) "album" else null
        )
    }

    private fun track(
        videoId: String,
        title: String,
        artists: List<SoundCloudUser>,
        durationMs: Long,
        artwork: String?
    ) = SoundCloudTrack(
        id = youTubeTrackId(videoId),
        urn = YT_TRACK_URN + videoId,
        kind = "track",
        title = title,
        artworkUrl = artwork,
        permalinkUrl = "https://music.youtube.com/watch?v=$videoId",
        user = artists.firstOrNull() ?: SoundCloudUser(username = "YouTube Music"),
        artists = artists,
        duration = durationMs,
        streamable = true,
        policy = "ALLOW"
    )

    /** The linked artists of a byline; its other runs are separators, albums, years, plays. */
    private fun artistsOf(runs: List<JsonElement>): List<SoundCloudUser> = runs.mapNotNull { run ->
        val id = run.str("navigationEndpoint", "browseEndpoint", "browseId")
            ?.takeIf { it.startsWith("UC") } ?: return@mapNotNull null
        SoundCloudUser(username = run.str("text"), permalinkUrl = YT_ARTIST_REF + id)
    }

    /** "Artist • 29 млн прослушиваний" → the artist. */
    private fun plainArtist(byline: String?): List<SoundCloudUser> =
        listOfNotNull(byline?.substringBefore(" • ")?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { SoundCloudUser(username = it) })

    private fun bestThumbnail(thumbnails: List<JsonElement>): String? {
        val url = thumbnails.lastOrNull()?.str("url") ?: return null
        val absolute = if (url.startsWith("//")) "https:$url" else url
        // Song and album art is served at any size; ask for one fit for the player.
        return absolute.replace(SIZE_SUFFIX, "=w544-h544")
    }

    private fun parseDuration(text: String): Long = text.split(':')
        .mapNotNull { it.trim().toLongOrNull() }
        .fold(0L) { total, part -> total * 60 + part } * 1000

    // ------------------------------------------------------------------------------ transport

    private suspend fun post(endpoint: String, body: JsonObject, extraParams: String = ""): JsonElement =
        withContext(Dispatchers.IO) {
            val auth = authProvider()?.takeIf { it.sapisid != null }
            body.add("context", requestContext())
            val builder = Request.Builder()
                .url("$API$endpoint?prettyPrint=false$extraParams")
                .post(body.toString().toRequestBody(JSON))
                .header("User-Agent", USER_AGENT)
                .header("Origin", ORIGIN)
                .header("X-Origin", ORIGIN)
                .header("Referer", "$ORIGIN/")
            val visitor = auth?.visitorData ?: anonymousVisitorData()
            if (!visitor.isNullOrBlank()) builder.header("X-Goog-Visitor-Id", visitor)
            if (auth != null) {
                builder.header("Cookie", auth.cookie)
                builder.header("Authorization", sapisidAuthorization(auth.sapisid!!, ORIGIN))
                builder.header("X-Goog-AuthUser", auth.authUser)
            }
            http.newCall(builder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw YtMusicException(response.code, text.take(300))
                JsonParser.parseString(text)
            }
        }

    private fun requestContext() = json {
        add("client", json {
            addProperty("clientName", "WEB_REMIX")
            addProperty("clientVersion", "1." + dateFormat().format(Date()) + ".01.00")
            addProperty("hl", "ru")
        })
        add("user", JsonObject())
    }

    /** Signed-out requests still need a visitor id, which the site hands out in its page. */
    private fun anonymousVisitorData(): String? {
        anonymousVisitor?.let { return it }
        return runCatching {
            http.newCall(Request.Builder().url(ORIGIN).header("User-Agent", USER_AGENT).build()).execute()
                .use { response -> VISITOR_DATA.find(response.body?.string().orEmpty())?.groupValues?.get(1) }
        }.getOrNull()?.also { anonymousVisitor = it }
    }

    companion object {
        private const val ORIGIN = "https://music.youtube.com"
        private const val API = "$ORIGIN/youtubei/v1/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        private const val HOME_EXTRA_PAGES = 3
        private val JSON = "application/json".toMediaType()
        private val SIZE_SUFFIX = Regex("=w\\d+-h\\d+")
        private val VISITOR_DATA = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")

        private fun dateFormat() = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        /** `ytmusic:set:<browseId>:<playlistId>` → the two ids. */
        fun parseSetRef(ref: String?): Pair<String, String>? {
            if (ref == null || !ref.startsWith(YT_SET_REF)) return null
            val rest = ref.removePrefix(YT_SET_REF)
            return rest.substringBefore(':') to rest.substringAfter(':', "")
        }
    }
}

// ---------------------------------------------------------------------------------- JSON paths

private inline fun json(build: JsonObject.() -> Unit): JsonObject = JsonObject().apply(build)

/** Walks [path] — object keys and array indices — returning null wherever it breaks off. */
private fun JsonElement?.at(vararg path: Any): JsonElement? {
    var current = this
    for (step in path) {
        current = when {
            current == null || current.isJsonNull -> return null
            step is String && current.isJsonObject -> current.asJsonObject.get(step)
            step is Int && current.isJsonArray -> current.asJsonArray.let { if (step in 0 until it.size()) it[step] else null }
            else -> return null
        }
    }
    return current?.takeUnless { it.isJsonNull }
}

private fun JsonElement?.str(vararg path: Any): String? =
    at(*path)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonElement?.arr(vararg path: Any): List<JsonElement> =
    at(*path)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

/** The text of a `{ runs: [...] }` object at [path]. */
private fun JsonElement?.runs(vararg path: Any): String? = at(*path).runsText()

private fun JsonElement?.runsText(): String? =
    arr("runs").joinToString("") { it.str("text").orEmpty() }.takeIf { it.isNotBlank() }

/** Every value under [key], anywhere below. */
private fun JsonElement.findAll(key: String, into: MutableList<JsonElement> = mutableListOf()): List<JsonElement> {
    when {
        isJsonObject -> asJsonObject.entrySet().forEach { (name, value) ->
            if (name == key) into += value
            value.findAll(key, into)
        }
        isJsonArray -> asJsonArray.forEach { it.findAll(key, into) }
    }
    return into
}
