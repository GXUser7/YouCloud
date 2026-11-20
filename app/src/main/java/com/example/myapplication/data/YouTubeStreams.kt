package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Audio of YouTube Music tracks. YouTube Music's API only describes a track; the stream comes
 * from YouTube's player, whose URLs are scrambled by the player script and, on addresses YouTube
 * distrusts — VPN exits among them — withheld from anyone not signed in.
 *
 * When the current network isn't bot-checked by YouTube, a single direct InnerTube `ANDROID_VR`
 * request resolves the stream in ~200-400 ms without Python or QuickJS. When that is refused
 * (e.g. VPN bot-check, age-restricted or members-only video), [YtDlp] takes over with the signed-in
 * YouTube Music session, and NewPipeExtractor remains the final signed-out fallback.
 *
 * Stream URLs are signed for a few hours ("expire" in the URL); within that they're reused.
 */
object YouTubeStreams {
    /**
     * [userAgent]: the one to fetch [url] with — the client it was issued to. [audioUrls]: with a
     * video's picture, its sound, to line the video up with a track by — smallest first.
     */
    data class Stream(
        val url: String,
        val mimeType: String?,
        val contentLength: Long,
        val userAgent: String = USER_AGENT,
        val audioUrls: List<String> = emptyList(),
        // A video's codec as YouTube names it ("vp09.00.31.08", "avc1.4d401f").
        val codec: String? = null,
        // YouTube metadata for filtering non-music uploads (e.g. "Gaming" osu! replays).
        val category: String? = null,
        val tags: List<String> = emptyList(),
        val viewCount: Long? = null,
        // True when resolved via the lightweight direct ANDROID_VR InnerTube fast-path.
        val fastPath: Boolean = false
    )

    private const val TAG = "YouTubeStreams"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:140.0) Gecko/20100101 Firefox/140.0"
    private const val VR_UA =
        "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
    private const val VR_PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val VR_BOT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(15)
    private val VR_ERROR_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(2)

    private val cache = ConcurrentHashMap<String, Pair<Stream, Long>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val fastHttp = http.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var initialised = false

    @Volatile
    private var vrBlockedUntil = 0L

    fun clearCache() {
        cache.clear()
        vrBlockedUntil = 0L
    }

    fun invalidate(videoId: String) {
        cache.remove("audio:$videoId")
    }

    fun markFastPathRejected() {
        vrBlockedUntil = System.currentTimeMillis() + VR_BOT_COOLDOWN_MS
    }

    fun resetFastPathCooldown() {
        vrBlockedUntil = 0L
    }

    /**
     * Blocking — call off the main thread.
     *
     * @param auth the YouTube Music session, when there is one.
     * @param urgent whether it is wanted now, not fetched ahead; see [YtDlp.resolve].
     */
    fun resolve(context: Context, videoId: String, auth: YtAuth? = null, urgent: () -> Boolean = { true }): Stream? =
        resolve(context, videoId, auth, priority = { if (urgent()) YtDlp.Priority.URGENT else YtDlp.Priority.PREFETCH })

    @JvmName("resolveWithPriority")
    fun resolve(
        context: Context,
        videoId: String,
        auth: YtAuth? = null,
        priority: () -> YtDlp.Priority,
        allowFastPath: Boolean = true
    ): Stream? =
        cached("audio:$videoId") {
            (if (allowFastPath) tryAndroidVrStream(videoId) else null)
                ?: YtDlp.resolve(context, videoId, auth, YtDlp.Kind.AUDIO, priority)
                ?: anonymousStream(videoId)
        }

    /**
     * [url], then the same stream at each of its mirrors (googlevideo's `mn`): some hosts can't be
     * reached through a VPN while their mirrors can, and YouTube's own player moves to a mirror
     * when a host doesn't answer. Anything that isn't a googlevideo URL is just itself.
     */
    fun withMirrors(url: String): List<String> = RangedDownload.mirrorsOf(url)

    /** A live stream's sound, as the address of its HLS playlist. */
    fun resolveLive(context: Context, videoId: String, auth: YtAuth? = null): Stream? =
        cached("live:$videoId") { YtDlp.resolve(context, videoId, auth, YtDlp.Kind.LIVE_AUDIO) }

    /** A live stream's picture, as the address of its HLS playlist. */
    fun resolveLiveVideo(context: Context, videoId: String, auth: YtAuth? = null): Stream? =
        cached("livevideo:$videoId") { YtDlp.resolve(context, videoId, auth, YtDlp.Kind.LIVE_VIDEO) }

    /**
     * The picture of a music video, without its sound: the player shows it in the cover's place.
     * Kept apart by codec: once VP9 has failed here, one resolved in it isn't handed out again.
     */
    fun resolveVideo(context: Context, videoId: String, auth: YtAuth? = null, urgent: () -> Boolean = { true }): Stream? =
        cached("video:${VideoDecoders.vp9(context)}:$videoId") { YtDlp.resolve(context, videoId, auth, YtDlp.Kind.VIDEO, urgent) }

    private inline fun cached(key: String, resolve: () -> Stream?): Stream? {
        cache[key]?.let { (stream, validUntil) ->
            if (System.currentTimeMillis() < validUntil) return stream
        }
        val stream = resolve() ?: return null
        val expiresAt = Uri.parse(stream.url).getQueryParameter("expire")?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(5))
        cache[key] = stream to (expiresAt - TimeUnit.MINUTES.toMillis(10))
        return stream
    }

    /**
     * Lightweight single-request InnerTube resolution via `ANDROID_VR`, which hands out plain
     * unscrambled HTTPS audio URLs whenever the IP isn't flagged by YouTube's bot check.
     */
    private fun tryAndroidVrStream(videoId: String): Stream? {
        val now = System.currentTimeMillis()
        if (now < vrBlockedUntil) return null
        val started = now
        return try {
            val payload = JsonObject().apply {
                addProperty("videoId", videoId)
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", "ANDROID_VR")
                        addProperty("clientVersion", "1.62.27")
                        addProperty("deviceMake", "Oculus")
                        addProperty("deviceModel", "Quest 3")
                        addProperty("androidSdkVersion", 32)
                        addProperty("osName", "Android")
                        addProperty("osVersion", "12L")
                        addProperty("hl", "ru")
                    })
                })
            }
            val request = okhttp3.Request.Builder()
                .url(VR_PLAYER_URL)
                .header("User-Agent", VR_UA)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val bodyText = fastHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 403 || response.code == 429) {
                        vrBlockedUntil = System.currentTimeMillis() + VR_BOT_COOLDOWN_MS
                    }
                    return null
                }
                response.body?.string() ?: return null
            }
            val root = JsonParser.parseString(bodyText).asJsonObject
            val playability = root.getAsJsonObject("playabilityStatus")
            val status = playability?.get("status")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
            if (status != "OK") {
                val reason = playability?.get("reason")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                val perVideoOnly = reason.contains("age", ignoreCase = true) ||
                    reason.contains("private", ignoreCase = true) ||
                    reason.contains("members", ignoreCase = true) ||
                    reason.contains("unavailable", ignoreCase = true)
                if (status == "LOGIN_REQUIRED" && !perVideoOnly) {
                    vrBlockedUntil = System.currentTimeMillis() + VR_BOT_COOLDOWN_MS
                    Log.i(TAG, "ANDROID_VR bot-checked ($reason); cooling down for 15 min")
                }
                return null
            }
            val details = root.getAsJsonObject("videoDetails")
            if (details?.get("isLive")?.takeUnless { it.isJsonNull }?.asBoolean == true) return null
            val formats = root.getAsJsonObject("streamingData")
                ?.getAsJsonArray("adaptiveFormats")
                ?.mapNotNull { it.takeIf { el -> el.isJsonObject }?.asJsonObject }
                ?.filter { f ->
                    val mime = f.get("mimeType")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                    val url = f.get("url")?.takeUnless { it.isJsonNull }?.asString.orEmpty()
                    val isLivePiece = f.has("targetDurationSec")
                    mime.startsWith("audio/") && url.isNotBlank() && !isLivePiece
                }
                .orEmpty()
            if (formats.isEmpty()) return null
            val best = pickBestVrAudio(formats) ?: return null
            val url = best.get("url").asString
            val mimeType = best.get("mimeType")?.takeUnless { it.isJsonNull }?.asString?.substringBefore(';')
            val contentLength = best.get("contentLength")?.takeUnless { it.isJsonNull }?.asString?.toLongOrNull()
                ?: RangedDownload.extractClen(url)
                ?: -1L
            val category = root.getAsJsonObject("microformat")
                ?.getAsJsonObject("playerMicroformatRenderer")
                ?.get("category")?.takeUnless { it.isJsonNull }?.asString?.takeIf(String::isNotBlank)
            val tags = details?.getAsJsonArray("keywords")
                ?.mapNotNull { el -> el.takeIf { it.isJsonPrimitive }?.asString?.takeIf(String::isNotBlank) }
                ?.take(30)
                .orEmpty()
            val viewCount = details?.get("viewCount")?.takeUnless { it.isJsonNull }?.asString?.toLongOrNull()?.takeIf { it >= 0L }
            Log.i(TAG, "$videoId: audio $mimeType via ANDROID_VR in ${System.currentTimeMillis() - started} ms")
            Stream(
                url = url,
                mimeType = mimeType,
                contentLength = contentLength,
                userAgent = VR_UA,
                category = category,
                tags = tags,
                viewCount = viewCount,
                fastPath = true
            )
        } catch (e: Exception) {
            vrBlockedUntil = System.currentTimeMillis() + VR_ERROR_COOLDOWN_MS
            Log.d(TAG, "ANDROID_VR fast-path skipped for $videoId: ${e.javaClass.simpleName}")
            null
        }
    }

    internal fun pickBestVrAudio(formats: List<JsonObject>): JsonObject? {
        fun isDefaultTrack(f: JsonObject): Boolean =
            f.getAsJsonObject("audioTrack")?.get("audioIsDefault")?.takeUnless { it.isJsonNull }?.asBoolean != false
        fun isDrc(f: JsonObject): Boolean =
            f.get("isDrc")?.takeUnless { it.isJsonNull }?.asBoolean == true ||
                f.get("itag")?.takeUnless { it.isJsonNull }?.asInt in setOf(1400, 2510)
        fun bitrate(f: JsonObject): Int = f.get("bitrate")?.takeUnless { it.isJsonNull }?.asInt ?: 0
        fun itag(f: JsonObject): Int = f.get("itag")?.takeUnless { it.isJsonNull }?.asInt ?: -1
        fun isMp4(f: JsonObject): Boolean =
            f.get("mimeType")?.takeUnless { it.isJsonNull }?.asString.orEmpty().startsWith("audio/mp4")

        val preferred = formats.filter(::isDefaultTrack).ifEmpty { formats }
        val nonDrc = preferred.filterNot(::isDrc).ifEmpty { preferred }
        return nonDrc.firstOrNull { itag(it) == 140 }
            ?: nonDrc.filter(::isMp4).maxByOrNull(::bitrate)
            ?: nonDrc.maxByOrNull(::bitrate)
    }

    /** NewPipe's own extraction, signed out. */
    private fun anonymousStream(videoId: String): Stream? {
        ensureInitialised()
        return try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val streams = info.audioStreams.filter {
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank()
            }
            // AAC in MP4 first: every device decodes it, and downloads are kept as it.
            val best = streams.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
                ?: streams.maxByOrNull { it.averageBitrate }
                ?: return null
            Stream(
                url = best.content,
                mimeType = best.format?.mimeType,
                contentLength = RangedDownload.extractClen(best.content) ?: -1L,
                category = info.category?.takeIf(String::isNotBlank),
                tags = info.tags.orEmpty().filter { it.isNotBlank() }.take(30),
                viewCount = info.viewCount.takeIf { it >= 0L }
            )
        } catch (e: Exception) {
            // One line: YouTube's bot check on a VPN address is the usual case, not news.
            Log.w(TAG, "No anonymous audio for $videoId: $e")
            null
        }
    }

    private fun ensureInitialised() {
        if (initialised) return
        synchronized(this) {
            if (!initialised) {
                NewPipe.init(OkHttpDownloader(http))
                initialised = true
            }
        }
    }

    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), request.dataToSend()?.toRequestBody())
                .url(request.url())
                .header("User-Agent", USER_AGENT)
            request.headers().forEach { (name, values) ->
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) throw ReCaptchaException("reCaptcha challenge", request.url())
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string(),
                    response.request.url.toString()
                )
            }
        }
    }
}
