package com.example.myapplication.data

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
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Audio of YouTube Music tracks. YouTube Music's API only describes a track; the stream comes
 * from YouTube's player, and its URL has to be deciphered with the player script — which
 * NewPipeExtractor keeps up with.
 *
 * YouTube asks anonymous players to "sign in to confirm you're not a bot" on addresses it
 * distrusts, VPN exits among them, so the signed-in session asks first, through the clients
 * yt-dlp uses with cookies, one after another until one hands out audio that actually plays.
 * NewPipe, anonymous, is the fallback.
 *
 * Stream URLs are signed for a few hours ("expire" in the URL); within that they're reused.
 */
object YouTubeStreams {
    /** [userAgent]: the one to fetch [url] with — the client it was issued to. */
    data class Audio(
        val url: String,
        val mimeType: String?,
        val contentLength: Long,
        val userAgent: String = USER_AGENT
    )

    private const val TAG = "YouTubeStreams"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:140.0) Gecko/20100101 Firefox/140.0"

    /**
     * A YouTube player client, as yt-dlp describes it (extractor/youtube/_base.py). All of these
     * take the site's cookies, which is what gets a signed-in session past the bot check.
     */
    private class Client(
        val label: String,
        val name: String,
        val version: String,
        val nameId: Int,
        val userAgent: String,
        val origin: String,
        val embedded: Boolean = false
    )

    private val signedInClients = listOf(
        // YouTube's TV app: no PO token for its streams.
        Client(
            label = "tv",
            name = "TVHTML5",
            version = "7.20260707.07.00",
            nameId = 7,
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold " +
                "(unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
            origin = "https://www.youtube.com"
        ),
        // The embedded player, as on other sites.
        Client(
            label = "web_embedded",
            name = "WEB_EMBEDDED_PLAYER",
            version = "2.20260708.00.00",
            nameId = 56,
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)",
            origin = "https://www.youtube.com",
            embedded = true
        ),
        // music.youtube.com itself.
        Client(
            label = "web_music",
            name = "WEB_REMIX",
            version = "1.20260707.12.00",
            nameId = 67,
            userAgent = USER_AGENT,
            origin = "https://music.youtube.com"
        )
    )

    private val cache = ConcurrentHashMap<String, Pair<Audio, Long>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var initialised = false

    /**
     * Blocking — a few network round trips; call it off the main thread.
     *
     * @param auth the YouTube Music session, when there is one.
     */
    fun resolve(videoId: String, auth: YtAuth? = null): Audio? {
        cache[videoId]?.let { (audio, validUntil) ->
            if (System.currentTimeMillis() < validUntil) return audio
        }
        ensureInitialised()
        val audio = auth?.let { session ->
            signedInClients.firstNotNullOfOrNull { client -> signedInStream(videoId, session, client) }
        } ?: anonymousStream(videoId) ?: return null
        val expiresAt = Uri.parse(audio.url).getQueryParameter("expire")?.toLongOrNull()?.times(1000)
            ?: (System.currentTimeMillis() + TimeUnit.HOURS.toMillis(5))
        cache[videoId] = audio to (expiresAt - TimeUnit.MINUTES.toMillis(10))
        return audio
    }

    /**
     * Asks [client]'s player for [videoId], signed in with the site's cookies, and returns its
     * audio only once a first request for it has been answered — a URL that YouTube issues but
     * then refuses (no PO token, streaming by SABR only) moves on to the next client.
     */
    private fun signedInStream(videoId: String, auth: YtAuth, client: Client): Audio? {
        val sapisid = auth.sapisid ?: return null
        val tag = "${client.label} $videoId"
        return try {
            val body = JsonObject().apply {
                add("context", JsonObject().apply {
                    add("client", JsonObject().apply {
                        addProperty("clientName", client.name)
                        addProperty("clientVersion", client.version)
                        addProperty("userAgent", client.userAgent)
                        addProperty("hl", "en")
                    })
                    add("user", JsonObject())
                    if (client.embedded) {
                        add("thirdParty", JsonObject().apply { addProperty("embedUrl", "https://www.youtube.com/") })
                    }
                })
                addProperty("videoId", videoId)
                add("playbackContext", JsonObject().apply {
                    add("contentPlaybackContext", JsonObject().apply {
                        // Which version of the cipher to scramble the URLs with: the script's.
                        addProperty("signatureTimestamp", YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId))
                        addProperty("html5Preference", "HTML5_PREF_WANTS")
                    })
                })
                addProperty("contentCheckOk", true)
                addProperty("racyCheckOk", true)
            }
            val request = okhttp3.Request.Builder()
                .url("${client.origin}/youtubei/v1/player?prettyPrint=false")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("User-Agent", client.userAgent)
                .header("Origin", client.origin)
                .header("X-Origin", client.origin)
                .header("Referer", "${client.origin}/")
                .header("X-Youtube-Client-Name", client.nameId.toString())
                .header("X-Youtube-Client-Version", client.version)
                .header("Cookie", auth.cookie)
                .header("Authorization", sapisidAuthorization(sapisid, client.origin))
                .header("X-Goog-AuthUser", auth.authUser)
                .apply { auth.visitorData?.let { header("X-Goog-Visitor-Id", it) } }
                .build()
            val response = http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    Log.w(TAG, "$tag: player HTTP ${r.code}")
                    return null
                }
                JsonParser.parseString(text).asJsonObject
            }
            val playability = response.getAsJsonObject("playabilityStatus")
            val status = playability?.get("status")?.asString
            if (status != "OK") {
                Log.w(TAG, "$tag: refused, $status ${playability?.get("reason")}")
                return null
            }
            val streaming = response.getAsJsonObject("streamingData")
            val audioFormats = streaming?.getAsJsonArray("adaptiveFormats")
                ?.map { it.asJsonObject }
                ?.filter { it.get("mimeType")?.asString?.startsWith("audio/") == true }
                .orEmpty()
            val playable = audioFormats.filter { it.has("url") || it.has("signatureCipher") }
            if (playable.isEmpty()) {
                Log.w(TAG, "$tag: no usable audio (${audioFormats.size} audio formats, " +
                    "SABR only: ${streaming?.has("serverAbrStreamingUrl")}, keys ${streaming?.keySet()})")
                return null
            }
            // AAC in MP4 first (itag 140): every device decodes it, and downloads are kept as it.
            val format = playable.firstOrNull { it.get("itag")?.asInt == 140 }
                ?: playable.filter { it.get("mimeType").asString.startsWith("audio/mp4") }
                    .maxByOrNull { it.get("bitrate")?.asInt ?: 0 }
                ?: playable.maxByOrNull { it.get("bitrate")?.asInt ?: 0 }!!
            val raw = format.get("url")?.asString ?: decipher(videoId, format.get("signatureCipher").asString)
            val url = YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, raw)
            val probe = http.newCall(
                okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", client.userAgent)
                    .header("Range", "bytes=0-1023")
                    .build()
            ).execute().use { it.code }
            if (probe != 206 && probe != 200) {
                Log.w(TAG, "$tag: itag ${format.get("itag")} refused by googlevideo, HTTP $probe")
                return null
            }
            Log.d(TAG, "$tag: streaming itag ${format.get("itag")}")
            Audio(
                url = url,
                mimeType = format.get("mimeType")?.asString?.substringBefore(';'),
                contentLength = format.get("contentLength")?.asString?.toLongOrNull() ?: -1L,
                userAgent = client.userAgent
            )
        } catch (e: Exception) {
            Log.w(TAG, "$tag: failed", e)
            null
        }
    }

    /** `signatureCipher`: the URL, and its signature scrambled by the player script. */
    private fun decipher(videoId: String, cipher: String): String {
        val parts = cipher.split('&').associate { part ->
            part.substringBefore('=') to Uri.decode(part.substringAfter('=', ""))
        }
        val signature = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, parts.getValue("s"))
        return parts.getValue("url") + "&" + (parts["sp"] ?: "signature") + "=" + Uri.encode(signature)
    }

    /** NewPipe's own extraction, signed out. */
    private fun anonymousStream(videoId: String): Audio? {
        return try {
            val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
            val streams = info.audioStreams.filter {
                it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.content.isNotBlank()
            }
            // AAC in MP4 first: every device decodes it, and downloads are kept as it.
            val best = streams.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
                ?: streams.maxByOrNull { it.averageBitrate }
                ?: return null
            Audio(
                url = best.content,
                mimeType = best.format?.mimeType,
                contentLength = Uri.parse(best.content).getQueryParameter("clen")?.toLongOrNull() ?: -1L
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
