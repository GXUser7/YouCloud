package com.example.myapplication.data

import com.example.myapplication.i18n.tr
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A channel as youtube.com shows it, for one YouTube Music has nothing of: its videos, newest
 * first ([continuation] for the rest), its playlists, and who it is.
 */
data class YtChannelVideos(
    val videos: List<SoundCloudTrack>,
    val playlists: List<SoundCloudPlaylist>,
    val continuation: String?,
    val name: String?,
    val avatarUrl: String?,
    val description: String?
)

/**
 * youtube.com's own web API ("InnerTube", the WEB client), for what YouTube Music's doesn't say:
 * a live stream's chat ([YouTubeLiveChat]), the channel a video is from, and a plain YouTube search.
 * Signed in as the YouTube Music session when there is one — the same Google cookies.
 */
object YouTubeWeb {
    private const val ORIGIN = "https://www.youtube.com"
    private const val API = "$ORIGIN/youtubei/v1/"
    private const val CLIENT_VERSION = "2.20260925.01.00"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    private val JSON = "application/json".toMediaType()
    // A channel's tabs: "Трансляции", "Видео", "Плейлисты".
    private const val STREAMS_TAB = "EgdzdHJlYW1z8gYECgJ6AA=="
    private const val VIDEOS_TAB = "EgZ2aWRlb3PyBgQKAjoA"
    private const val PLAYLISTS_TAB = "EglwbGF5bGlzdHPyBgQKAkIA"
    // A video's length on its picture: "13:13", "1:02:45".
    private val LENGTH = Regex("""^\d+(:\d{2})+$""")

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Blocking: one request to [endpoint], as youtube.com's page makes it. */
    fun post(endpoint: String, body: JsonObject, session: YtAuth?): JsonElement {
        body.add("context", JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "WEB")
                addProperty("clientVersion", CLIENT_VERSION)
                addProperty("hl", "ru")
                addProperty("gl", "RU")
                session?.visitorData?.let { addProperty("visitorData", it) }
            })
        })
        val request = Request.Builder()
            .url("$API$endpoint?prettyPrint=false")
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .apply {
                val sapisid = session?.sapisid
                if (session != null && sapisid != null) {
                    header("Cookie", session.cookie)
                    header("Authorization", sapisidAuthorization(sapisid, ORIGIN))
                    header("X-Goog-AuthUser", session.authUser)
                }
            }
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube HTTP ${response.code}")
            return JsonParser.parseString(response.body?.string().orEmpty())
        }
    }

    /**
     * The channel [videoId] is from, as its watch page names it. YouTube Music doesn't always
     * say: a broadcast it lists as a podcast's episode ("📻 Lofi Girl - Radios") links no channel.
     */
    suspend fun videoOwner(videoId: String, session: YtAuth?): SoundCloudUser? = withContext(Dispatchers.IO) {
        val owner = post("next", JsonObject().apply { addProperty("videoId", videoId) }, session)
            .findAll("videoOwnerRenderer").firstOrNull() ?: return@withContext null
        val channelId = owner.str("navigationEndpoint", "browseEndpoint", "browseId")?.takeIf { it.startsWith("UC") }
            ?: return@withContext null
        SoundCloudUser(
            username = owner.arr("title", "runs").firstOrNull().str("text"),
            avatarUrl = owner.arr("thumbnail", "thumbnails").lastOrNull().str("url"),
            permalinkUrl = YT_ARTIST_REF + channelId
        )
    }

    /**
     * A plain YouTube search: its videos, live streams among them, each with the channel it is
     * from, and its channels. Whatever YouTube Music leaves out, or files under a podcast.
     */
    suspend fun search(query: String, session: YtAuth?): YtSearchPage = withContext(Dispatchers.IO) {
        val page = post("search", JsonObject().apply { addProperty("query", query) }, session)
        val tracks = page.findAll("videoRenderer").mapNotNull(::parseVideo).distinctBy { it.id }
        val channels = page.findAll("channelRenderer").mapNotNull { channel ->
            val id = channel.str("channelId")?.takeIf { it.startsWith("UC") } ?: return@mapNotNull null
            SoundCloudUser(
                username = channel.str("title", "simpleText"),
                avatarUrl = channel.arr("thumbnail", "thumbnails").lastOrNull().str("url")?.let { if (it.startsWith("//")) "https:$it" else it },
                permalinkUrl = YT_ARTIST_REF + id
            )
        }.distinctBy { it.permalinkUrl }
        YtSearchPage(tracks = tracks, artists = channels, albums = emptyList(), playlists = emptyList(), continuation = null)
    }

    /**
     * What the channel [channelId] is broadcasting right now, from its "Трансляции" tab: the
     * streams marked live, not those that ended or are yet to start. [owner] is who they're by.
     */
    suspend fun channelLive(channelId: String, owner: SoundCloudUser, session: YtAuth?): List<SoundCloudTrack> =
        withContext(Dispatchers.IO) {
            val page = post("browse", JsonObject().apply {
                addProperty("browseId", channelId)
                addProperty("params", STREAMS_TAB)
            }, session)
            val lockups = page.findAll("lockupViewModel").mapNotNull { parseVideoLockup(it, owner) }.filter { it.kind == "live" }
            // The older layout, should the page come in it.
            val videos = page.findAll("videoRenderer").mapNotNull(::parseVideo)
                .filter { it.kind == "live" }
                .map { if (it.artists.isNullOrEmpty()) it.copy(user = owner, artists = listOf(owner)) else it }
            (lockups + videos).distinctBy { it.id }
        }

    /**
     * Subscribes the signed-in account to the channel [channelId], or unsubscribes it: one
     * subscription for YouTube and YouTube Music both. [params] say where from, as the page's own
     * button does: sent without, YouTube files the subscription as made watching a video for
     * children, and won't let it write in a subscribers' chat.
     */
    suspend fun subscribe(channelId: String, subscribe: Boolean, session: YtAuth?, params: String?) = withContext(Dispatchers.IO) {
        if (session?.sapisid == null) throw IOException(tr("Не выполнен вход в YouTube"))
        post(if (subscribe) "subscription/subscribe" else "subscription/unsubscribe", JsonObject().apply {
            add("channelIds", com.google.gson.JsonArray().apply { add(channelId) })
            params?.let { addProperty("params", it.replace("%3D", "=")) }
        }, session)
        Unit
    }

    /**
     * What the watch page of [videoId] sends with its subscribe button ([subscribe]) or its
     * unsubscribe: the source, a watch page, and the video.
     */
    fun watchSubscribeParams(videoId: String, subscribe: Boolean): String {
        val id = videoId.toByteArray()
        val bytes = if (subscribe) {
            byteArrayOf(0x12, 0x02, 0x08, 0x03, 0x18, 0x00, 0x22, id.size.toByte()) + id
        } else {
            byteArrayOf(0x0a, 0x02, 0x08, 0x03, 0x12, id.size.toByte()) + id + byteArrayOf(0x18, 0x00)
        }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    /**
     * A channel's "Видео" tab, for one YouTube Music has no page for ("нет общедоступного
     * контента"): its videos as tracks, its playlists, its name and picture. [owner] is who
     * the videos are by.
     */
    suspend fun channelVideos(channelId: String, owner: SoundCloudUser, session: YtAuth?): YtChannelVideos =
        withContext(Dispatchers.IO) {
            val page = post("browse", JsonObject().apply {
                addProperty("browseId", channelId)
                addProperty("params", VIDEOS_TAB)
            }, session)
            val about = page.at("metadata", "channelMetadataRenderer")
            val name = about.str("title") ?: owner.username
            val by = owner.copy(username = name)
            // Its playlists, a request of their own; the videos stand without them.
            val playlists = runCatching {
                post("browse", JsonObject().apply {
                    addProperty("browseId", channelId)
                    addProperty("params", PLAYLISTS_TAB)
                }, session).findAll("lockupViewModel").mapNotNull { parsePlaylistLockup(it, by) }.distinctBy { it.id }
            }.getOrDefault(emptyList())
            YtChannelVideos(
                videos = page.findAll("lockupViewModel").mapNotNull { parseVideoLockup(it, by) }.distinctBy { it.id },
                playlists = playlists,
                continuation = page.lastContinuation(),
                name = name,
                avatarUrl = about.arr("avatar", "thumbnails").lastOrNull().str("url"),
                description = about.str("description")?.takeIf { it.isNotBlank() }
            )
        }

    /** The next videos of a channel's tab, and where the ones after them are. */
    suspend fun moreChannelVideos(continuation: String, owner: SoundCloudUser, session: YtAuth?): Pair<List<SoundCloudTrack>, String?> =
        withContext(Dispatchers.IO) {
            val page = post("browse", JsonObject().apply { addProperty("continuation", continuation) }, session)
            page.findAll("lockupViewModel").mapNotNull { parseVideoLockup(it, owner) } to page.lastContinuation()
        }

    private fun JsonElement.lastContinuation(): String? =
        findAll("continuationCommand").lastOrNull().str("token")

    /**
     * A video of a channel's tab, in its newer layout: its length on the picture's badge, or a
     * "В ЭФИРЕ" badge for a broadcast going on now.
     */
    private fun parseVideoLockup(lockup: JsonElement, owner: SoundCloudUser): SoundCloudTrack? {
        if (lockup.str("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val videoId = lockup.str("contentId") ?: return null
        val title = lockup.str("metadata", "lockupMetadataViewModel", "title", "content") ?: return null
        val badges = lockup.findAll("thumbnailBadgeViewModel")
        val live = badges.any { it.str("badgeStyle") == "THUMBNAIL_OVERLAY_BADGE_STYLE_LIVE" }
        val length = badges.firstNotNullOfOrNull { badge -> badge.str("text")?.takeIf { LENGTH.matches(it) } }
        // Not yet out (a premiere, a broadcast to come): nothing to play.
        if (!live && length == null) return null
        val duration = length?.split(':')?.fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: 0L) }?.times(1000) ?: 0L
        val artwork = lockup.arr("contentImage", "thumbnailViewModel", "image", "sources").lastOrNull().str("url")?.substringBefore('?')
        return SoundCloudTrack(
            id = youTubeTrackId(if (live) "live:$videoId" else videoId),
            urn = (if (live) YT_LIVE_URN else YT_TRACK_URN) + videoId,
            kind = if (live) "live" else "track",
            title = title,
            artworkUrl = artwork,
            permalinkUrl = "https://www.youtube.com/watch?v=$videoId",
            user = owner,
            artists = listOf(owner),
            duration = if (live) 0L else duration,
            streamable = true,
            policy = "ALLOW"
        )
    }

    /** A playlist of a channel's tab, opened the way YouTube Music opens one. */
    private fun parsePlaylistLockup(lockup: JsonElement, owner: SoundCloudUser): SoundCloudPlaylist? {
        if (lockup.str("contentType") != "LOCKUP_CONTENT_TYPE_PLAYLIST") return null
        val playlistId = lockup.str("contentId") ?: return null
        val title = lockup.str("metadata", "lockupMetadataViewModel", "title", "content") ?: return null
        val count = lockup.findAll("thumbnailBadgeViewModel").firstNotNullOfOrNull { it.str("text") }
            ?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        return SoundCloudPlaylist(
            id = youTubeTrackId("set:VL$playlistId"),
            title = title,
            artworkUrl = lockup.findAll("sources").firstOrNull()?.takeIf { it.isJsonArray }?.asJsonArray
                ?.lastOrNull().str("url")?.substringBefore('?'),
            permalinkUrl = "${YT_SET_REF}VL$playlistId:$playlistId",
            user = owner,
            trackCount = count,
            isAlbum = false
        )
    }

    private fun parseVideo(video: JsonElement): SoundCloudTrack? {
        val videoId = video.str("videoId") ?: return null
        val title = video.arr("title", "runs").joinToString("") { it.str("text").orEmpty() }.ifBlank { return null }
        val ownerRun = video.arr("ownerText", "runs").firstOrNull()
        val owner = ownerRun?.let { run ->
            SoundCloudUser(
                username = run.str("text"),
                permalinkUrl = run.str("navigationEndpoint", "browseEndpoint", "browseId")?.takeIf { it.startsWith("UC") }?.let { YT_ARTIST_REF + it }
            )
        }
        val length = video.str("lengthText", "simpleText")
        val live = length == null && (
            video.findAll("style").any { it.isJsonPrimitive && it.asString == "BADGE_STYLE_TYPE_LIVE_NOW" } ||
                video.findAll("iconType").any { it.isJsonPrimitive && it.asString.contains("LIVE") }
            )
        val duration = length?.split(':')?.fold(0L) { total, part -> total * 60 + (part.toLongOrNull() ?: 0L) }?.times(1000) ?: 0L
        val artwork = video.arr("thumbnail", "thumbnails").lastOrNull().str("url")?.substringBefore('?')
        return SoundCloudTrack(
            id = youTubeTrackId(if (live) "live:$videoId" else videoId),
            urn = (if (live) YT_LIVE_URN else YT_TRACK_URN) + videoId,
            kind = if (live) "live" else "track",
            title = title,
            artworkUrl = artwork,
            permalinkUrl = "https://www.youtube.com/watch?v=$videoId",
            user = owner ?: SoundCloudUser(username = "YouTube"),
            artists = listOfNotNull(owner),
            duration = if (live) 0L else duration,
            streamable = true,
            policy = "ALLOW"
        )
    }
}

private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

private fun JsonElement?.at(vararg path: String): JsonElement? {
    var node = this
    for (key in path) node = node?.asObjectOrNull()?.get(key) ?: return null
    return node
}

private fun JsonElement?.str(vararg path: String): String? =
    at(*path)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonElement?.arr(vararg path: String): List<JsonElement> =
    at(*path)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

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
