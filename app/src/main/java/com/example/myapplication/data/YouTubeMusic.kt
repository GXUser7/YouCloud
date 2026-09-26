package com.example.myapplication.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/** What a search found: songs a page at a time ([continuation] for the next), and the rest at once. */
data class YtSearchPage(
    val tracks: List<SoundCloudTrack>,
    val artists: List<SoundCloudUser>,
    val albums: List<SoundCloudPlaylist>,
    val playlists: List<SoundCloudPlaylist>,
    val continuation: String?
)

/**
 * An artist's page: their top songs, the set that holds all of them ([allSongs], for "Все"), and
 * their albums, then singles.
 */
data class YtArtistPage(
    val artist: SoundCloudUser,
    val topSongs: List<SoundCloudTrack>,
    val allSongs: SoundCloudPlaylist?,
    val releases: List<SoundCloudPlaylist>
)

/**
 * A song's music video, and how the two timelines line up ([segments]; empty: one to one).
 * [paired]: YouTube Music's own pairing, rather than a video found by search. [itself]: the
 * "song" is a video to begin with — an official one, or somebody's upload — so its picture is
 * its video, and in step with its sound by definition.
 */
data class YtMusicVideo(
    val videoId: String,
    val segments: List<VideoSegment>,
    val paired: Boolean,
    val itself: Boolean = false
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

    /**
     * What music.youtube.com shows for [query]: its own results page first — the top result and a
     * list mixing songs, videos, artists and albums — and then, for depth, the site's filtered
     * searches. The songs filter alone misses every track that YouTube has only as a video, which
     * is a good half of some genres.
     */
    suspend fun search(query: String): YtSearchPage = coroutineScope {
        val main = async { post("search", json { addProperty("query", query) }) }
        // Extras: if one of them fails, the main results still show.
        val songs = async { optional { searchShelf(query, FILTER_SONGS) } }
        val artists = async { optional { searchShelf(query, FILTER_ARTISTS) } }
        val albums = async { optional { searchShelf(query, FILTER_ALBUMS) } }
        val playlists = async { optional { searchShelf(query, FILTER_PLAYLISTS) } }

        val mixed = parseMixedResults(main.await())
        val songShelf = songs.await()
        val filteredSongs = listRows(songShelf).mapNotNull { parseSongRow(it) }
        // The filtered rows carry the length and album the main page leaves out.
        val detailed = filteredSongs.associateBy { it.id }
        YtSearchPage(
            tracks = (mixed.tracks.map { detailed[it.id] ?: it } + filteredSongs).distinctBy { it.id },
            artists = (mixed.artists + listRows(artists.await()).mapNotNull(::parseListArtist))
                .distinctBy { it.permalinkUrl },
            albums = (mixed.sets.filter { it.isAlbum == true } + listRows(albums.await()).mapNotNull(::parseListSet))
                .distinctBy { it.id },
            playlists = (mixed.sets.filter { it.isAlbum != true } + listRows(playlists.await()).mapNotNull(::parseListSet))
                .distinctBy { it.id },
            continuation = songShelf.continuation()
        )
    }

    private class MixedResults(
        val tracks: List<SoundCloudTrack>,
        val artists: List<SoundCloudUser>,
        val sets: List<SoundCloudPlaylist>
    )

    /** The main results page: the top result's card, then every row, in the site's order. */
    private fun parseMixedResults(page: JsonElement): MixedResults {
        val tracks = mutableListOf<SoundCloudTrack>()
        val artists = mutableListOf<SoundCloudUser>()
        val sets = mutableListOf<SoundCloudPlaylist>()
        page.findAll("musicCardShelfRenderer").firstOrNull()?.let { card ->
            parseCardTrack(card)?.let(tracks::add)
            parseCardArtist(card)?.let(artists::add)
        }
        for (row in page.findAll("musicResponsiveListItemRenderer")) {
            val browseId = row.str("navigationEndpoint", "browseEndpoint", "browseId")
            when {
                browseId == null -> parseSongRow(row)?.let(tracks::add)
                browseId.startsWith("UC") -> {
                    // Listeners' own channels ("Профили") come up too; artists only.
                    val pageType = row.str(
                        "navigationEndpoint", "browseEndpoint", "browseEndpointContextSupportedConfigs",
                        "browseEndpointContextMusicConfig", "pageType"
                    )
                    if (pageType == null || pageType == "MUSIC_PAGE_TYPE_ARTIST") parseListArtist(row)?.let(artists::add)
                }
                else -> parseListSet(row)?.let(sets::add)
            }
        }
        return MixedResults(tracks, artists, sets)
    }

    /** The top result, when it is a song or a video. */
    private fun parseCardTrack(card: JsonElement): SoundCloudTrack? {
        val videoId = card.str("title", "runs", 0, "navigationEndpoint", "watchEndpoint", "videoId")
            ?: card.str("onTap", "watchEndpoint", "videoId")
            ?: return null
        val title = card.runs("title") ?: return null
        val subtitle = card.at("subtitle")
        return track(
            videoId = videoId,
            title = title,
            artists = artistsOf(subtitle.arr("runs")).ifEmpty { plainArtist(subtitle.runsText()) },
            durationMs = subtitle.durationRun()?.let(::parseDuration) ?: 0L,
            artwork = bestThumbnail(card.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"))
        )
    }

    /** The top result, when it is an artist. */
    private fun parseCardArtist(card: JsonElement): SoundCloudUser? {
        val channelId = card.str("title", "runs", 0, "navigationEndpoint", "browseEndpoint", "browseId")
            ?.takeIf { it.startsWith("UC") } ?: return null
        return SoundCloudUser(
            username = card.runs("title"),
            avatarUrl = bestThumbnail(card.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
            followersCount = card.runs("subtitle")?.substringAfter(" • ", "")?.takeIf { it.isNotBlank() }?.let(::parseCount),
            permalinkUrl = YT_ARTIST_REF + channelId
        )
    }

    /** The next page of a search's songs. */
    suspend fun moreSongs(continuation: String): Pair<List<SoundCloudTrack>, String?> {
        val shelf = post("search", JsonObject(), "&ctoken=$continuation&continuation=$continuation&type=next")
            .at("continuationContents", "musicShelfContinuation")
        return listRows(shelf).mapNotNull { parseSongRow(it) }.distinctBy { it.id } to shelf.continuation()
    }

    /** An artist's page (`UC…`). */
    suspend fun artist(channelId: String): YtArtistPage {
        val page = post("browse", json { addProperty("browseId", channelId) })
        val header = page.at("header", "musicImmersiveHeaderRenderer")
            ?: page.at("header", "musicVisualHeaderRenderer")
        val name = header.runs("title") ?: ""
        val sections = page.at(
            "contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content",
            "sectionListRenderer"
        ).arr("contents")

        val topShelf = sections.firstNotNullOfOrNull { it.at("musicShelfRenderer") }
        val carousels = sections.mapNotNull { it.at("musicCarouselShelfRenderer") }
        // A channel that uploads rather than releases (a remixer, say) has no songs, only videos.
        val videos = carousels.flatMap { shelf ->
            shelf.arr("contents").mapNotNull { item -> item.at("musicTwoRowItemRenderer")?.let(::parseTileTrack) }
        }
        val topSongs = listRows(topShelf).mapNotNull { parseSongRow(it, fallbackArtist = name) }
            .ifEmpty { videos }
            .distinctBy { it.id }
        // The shelf's title links to the playlist of all the artist's songs.
        val allSongs = (topShelf.str("title", "runs", 0, "navigationEndpoint", "browseEndpoint", "browseId")
            ?: topShelf.str("bottomEndpoint", "browseEndpoint", "browseId"))
            ?.takeIf { it.startsWith("VL") }
            ?.let { browseId ->
                SoundCloudPlaylist(
                    id = youTubeTrackId("set:$browseId"),
                    title = topShelf.runs("title") ?: name,
                    permalinkUrl = "$YT_SET_REF$browseId:${browseId.removePrefix("VL")}",
                    user = SoundCloudUser(username = name)
                )
            }
        val releases = carousels
            .flatMap { shelf -> shelf.arr("contents").mapNotNull { item -> item.at("musicTwoRowItemRenderer")?.let(::parseTileSet) } }
            // Albums before singles and the artist's playlists, as the page orders them.
            .sortedBy { if (it.isAlbum == true) 0 else 1 }
            .distinctBy { it.id }
        val description = header.runs("description")
            ?: sections.firstNotNullOfOrNull { it.at("musicDescriptionShelfRenderer") }.runs("description")
        val listeners = header.runs("monthlyListenerCount")
            ?: header.runs("subscriptionButton", "subscribeButtonRenderer", "subscriberCountText")
        val artist = SoundCloudUser(
            username = name,
            // A channel's page shows its avatar apart from a wide banner; an artist's, one picture.
            avatarUrl = bestThumbnail(header.arr("foregroundThumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"), PORTRAIT_SIZE)
                ?: bestThumbnail(header.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails"), PORTRAIT_SIZE),
            description = description,
            followersCount = listeners?.let(::parseCount),
            permalinkUrl = YT_ARTIST_REF + channelId
        )
        return YtArtistPage(artist, topSongs, allSongs, releases)
    }

    /**
     * The music video of the song [videoId] (null: a song from elsewhere, found by [title] and
     * [artist]). YouTube Music pairs songs with their videos itself, for some listeners with a
     * map of where the song's moments fall in the video. Otherwise an official video found by
     * search stands in, to be lined up by its sound ([ClipAligner]).
     */
    suspend fun musicVideo(videoId: String?, title: String, artist: String?, durationMs: Long): YtMusicVideo? =
        videoId?.let { counterpart(it) } ?: officialVideo(title, artist, durationMs)

    private suspend fun counterpart(videoId: String): YtMusicVideo? {
        val next = post("next", json {
            addProperty("videoId", videoId)
            addProperty("enablePersistentPlaylistPanel", true)
        })
        val type = next.findAll("playlistPanelVideoRenderer")
            .firstOrNull { it.str("videoId") == videoId }
            .str(
                "navigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs",
                "watchEndpointMusicConfig", "musicVideoType"
            )
        if (type == "MUSIC_VIDEO_TYPE_OMV" || type == "MUSIC_VIDEO_TYPE_UGC") {
            return YtMusicVideo(videoId, emptyList(), paired = true, itself = true)
        }
        val panel = next.findAll("playlistPanelVideoWrapperRenderer").firstOrNull() ?: return null
        if (panel.str("primaryRenderer", "playlistPanelVideoRenderer", "videoId") != videoId) return null
        val counterpart = panel.arr("counterpart").firstOrNull() ?: return null
        val video = counterpart.str("counterpartRenderer", "playlistPanelVideoRenderer", "videoId") ?: return null
        val segments = counterpart.arr("segmentMap", "segment").mapNotNull { segment ->
            VideoSegment(
                trackStartMs = segment.str("primaryVideoStartTimeMilliseconds")?.toLongOrNull() ?: return@mapNotNull null,
                videoStartMs = segment.str("counterpartVideoStartTimeMilliseconds")?.toLongOrNull() ?: return@mapNotNull null,
                durationMs = segment.str("durationMilliseconds")?.toLongOrNull() ?: return@mapNotNull null
            )
        }
        return YtMusicVideo(video, segments, paired = true)
    }

    private suspend fun officialVideo(title: String, artist: String?, durationMs: Long): YtMusicVideo? {
        val wanted = normalized(title)
        if (wanted.isBlank()) return null
        val shelf = searchShelf(listOfNotNull(artist, title).joinToString(" "), FILTER_VIDEOS)
        return listRows(shelf).firstNotNullOfOrNull { row ->
            val videoId = row.str("playlistItemData", "videoId") ?: return@firstNotNullOfOrNull null
            val type = row.str(
                "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
                "playNavigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs",
                "watchEndpointMusicConfig", "musicVideoType"
            )
            val columns = flexColumns(row)
            val videoTitle = normalized(columns.getOrNull(0).runsText())
            val length = columns.getOrNull(1).durationRun()?.let(::parseDuration) ?: 0L
            videoId.takeIf {
                type == "MUSIC_VIDEO_TYPE_OMV" &&
                    wanted in videoTitle &&
                    // "Official Audio", "Lyric Video", "Visualizer": a still picture, or words.
                    NOT_A_CLIP.none { it in videoTitle } &&
                    // Room for an opening scene or a skit, not for a compilation.
                    (durationMs <= 0 || length in (durationMs - SHORTER_BY_MS)..(durationMs + LONGER_BY_MS))
            }
        }?.let { YtMusicVideo(it, emptyList(), paired = false) }
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
        val columns = flexColumns(row)
        val title = columns.getOrNull(0).runsText() ?: return null
        val byline = columns.getOrNull(1)
        val artists = artistsOf(byline.arr("runs"))
            .ifEmpty { plainArtist(byline.runsText()) }
            .ifEmpty { plainArtist(fallbackArtist) }
        // A column of its own in playlists; the byline's last part in search results.
        val duration = (row.arr("fixedColumns")
            .firstNotNullOfOrNull { it.at("musicResponsiveListItemFixedColumnRenderer", "text").runsText() }
            ?: byline.durationRun())
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

    /** An album or playlist row of a search. */
    private fun parseListSet(row: JsonElement): SoundCloudPlaylist? {
        val browse = row.at("navigationEndpoint", "browseEndpoint") ?: return null
        val browseId = browse.str("browseId") ?: return null
        val pageType = browse.str(
            "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig", "pageType"
        )
        val isAlbum = pageType == "MUSIC_PAGE_TYPE_ALBUM" || browseId.startsWith("MPRE")
        val isPlaylist = pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || browseId.startsWith("VL")
        if (!isAlbum && !isPlaylist) return null
        val columns = flexColumns(row)
        val title = columns.getOrNull(0).runsText() ?: return null
        val byline = columns.getOrNull(1)
        // "Альбом • Кишлак • 2026", "Сингл • …", "Автор • 2,9 тыс. просмотров".
        val parts = byline.runsText()?.split(" • ").orEmpty()
        val owner = artistsOf(byline.arr("runs")).joinToString(", ") { it.username.orEmpty() }
            .ifBlank { parts.getOrNull(if (isAlbum) 1 else 0).orEmpty() }
        val play = row.at(
            "overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer",
            "playNavigationEndpoint"
        )
        val playlistId = play.str("watchPlaylistEndpoint", "playlistId")
            ?: play.str("watchEndpoint", "playlistId")
            ?: browseId.takeIf { it.startsWith("VL") }?.removePrefix("VL")
            ?: ""
        return SoundCloudPlaylist(
            id = youTubeTrackId("set:$browseId"),
            title = title,
            artworkUrl = bestThumbnail(row.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
            permalinkUrl = "$YT_SET_REF$browseId:$playlistId",
            user = SoundCloudUser(username = owner),
            isAlbum = isAlbum,
            setType = if (!isAlbum) null else when (parts.firstOrNull()?.lowercase()) {
                "сингл", "single" -> "single"
                "ep", "мини-альбом" -> "ep"
                else -> "album"
            },
            releaseDate = parts.lastOrNull()?.trim()?.takeIf { isAlbum && it.length == 4 && it.all(Char::isDigit) }
        )
    }

    /** An artist row of a search. */
    private fun parseListArtist(row: JsonElement): SoundCloudUser? {
        val channelId = row.str("navigationEndpoint", "browseEndpoint", "browseId")
            ?.takeIf { it.startsWith("UC") } ?: return null
        val columns = flexColumns(row)
        val name = columns.getOrNull(0).runsText() ?: return null
        // "Исполнитель • 411 тыс. слушателей в месяц"
        val audience = columns.getOrNull(1).runsText()?.substringAfter(" • ", "")
        return SoundCloudUser(
            username = name,
            avatarUrl = bestThumbnail(row.arr("thumbnail", "musicThumbnailRenderer", "thumbnail", "thumbnails")),
            followersCount = audience?.takeIf { it.isNotBlank() }?.let(::parseCount),
            permalinkUrl = YT_ARTIST_REF + channelId
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

    /** "Artist • 29 млн прослушиваний", "Видео • Artist • …" → the artist. */
    private fun plainArtist(byline: String?): List<SoundCloudUser> {
        val parts = byline?.split(" • ")?.map { it.trim() }.orEmpty()
        val name = parts.firstOrNull { it.isNotEmpty() && it.lowercase() !in KIND_WORDS }
        return listOfNotNull(name?.let { SoundCloudUser(username = it) })
    }

    private fun bestThumbnail(thumbnails: List<JsonElement>, size: Int = 544): String? {
        val url = thumbnails.lastOrNull()?.str("url") ?: return null
        val absolute = if (url.startsWith("//")) "https:$url" else url
        // Song and album art is served at any size; ask for one fit for the player.
        return absolute.replace(SIZE_SUFFIX, "=w$size-h$size")
    }

    /** "411 тыс. слушателей в месяц", "1,2 млн" → a count. */
    private fun parseCount(text: String): Int? {
        val match = COUNT.find(text.replace('\u00a0', ' ')) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val scale = when (match.groupValues[2].lowercase()) {
            "тыс", "k" -> 1_000.0
            "млн", "m" -> 1_000_000.0
            "млрд", "b" -> 1_000_000_000.0
            else -> 1.0
        }
        return (number * scale).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Letters and digits only, lower case: titles compared past their punctuation. */
    private fun normalized(text: String?): String =
        text.orEmpty().lowercase().replace(NON_WORD, " ").trim()

    private suspend fun searchShelf(query: String, params: String): JsonElement? =
        post("search", json {
            addProperty("query", query)
            addProperty("params", params)
        }).findAll("musicShelfRenderer").firstOrNull()

    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
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
        // The search page's filter chips, as it sends them.
        private const val FILTER_SONGS = "EgWKAQIIAWoQEAUQCRADEAQQChAREBAQFQ=="
        private const val FILTER_VIDEOS = "EgWKAQIQAWoQEAUQCRADEAQQChAREBAQFQ=="
        private const val FILTER_ALBUMS = "EgWKAQIYAWoQEAUQCRADEAQQChAREBAQFQ=="
        private const val FILTER_ARTISTS = "EgWKAQIgAWoQEAUQCRADEAQQChAREBAQFQ=="
        private const val FILTER_PLAYLISTS = "EgeKAQQoAEABahAQBRAJEAMQBBAKEBEQEBAV"

        private const val PORTRAIT_SIZE = 900
        private const val SHORTER_BY_MS = 10_000L
        private const val LONGER_BY_MS = 150_000L
        private val NOT_A_CLIP = listOf("audio", "lyric", "visualizer", "visualiser", "текст")
        // What a search row's byline starts with before naming anyone.
        private val KIND_WORDS = setOf("композиция", "видео", "трек", "song", "video", "эпизод", "episode")
        private val COUNT = Regex("(\\d+(?:[.,]\\d+)?)\\s*(тыс|млн|млрд|k|m|b)?", RegexOption.IGNORE_CASE)
        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

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

private fun JsonElement?.continuation(): String? = str("continuations", 0, "nextContinuationData", "continuation")

/** The rows of a list shelf. */
private fun listRows(shelf: JsonElement?): List<JsonElement> =
    shelf.arr("contents").mapNotNull { it.at("musicResponsiveListItemRenderer") }

private fun flexColumns(row: JsonElement): List<JsonElement?> =
    row.arr("flexColumns").map { it.at("musicResponsiveListItemFlexColumnRenderer", "text") }

/** The "3:20" among a byline's parts. */
private fun JsonElement?.durationRun(): String? =
    arr("runs").map { it.str("text").orEmpty().trim() }.lastOrNull { DURATION_TEXT.matches(it) }

private val DURATION_TEXT = Regex("\\d{1,2}(:\\d{2}){1,2}")

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
