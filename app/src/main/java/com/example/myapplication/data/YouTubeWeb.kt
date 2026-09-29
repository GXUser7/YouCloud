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
import java.io.IOException
import java.util.concurrent.TimeUnit

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
