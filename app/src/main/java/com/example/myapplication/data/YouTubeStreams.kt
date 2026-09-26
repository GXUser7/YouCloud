package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import android.util.Log
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
 * [YtDlp] does the work, with the YouTube Music session. NewPipeExtractor, signed out, is the
 * fallback for when yt-dlp can't run at all.
 *
 * Stream URLs are signed for a few hours ("expire" in the URL); within that they're reused.
 */
object YouTubeStreams {
    /**
     * [userAgent]: the one to fetch [url] with — the client it was issued to. [audioUrl]: with a
     * video's picture, its sound, to line the video up with a track by.
     */
    data class Stream(
        val url: String,
        val mimeType: String?,
        val contentLength: Long,
        val userAgent: String = USER_AGENT,
        val audioUrl: String? = null
    )

    private const val TAG = "YouTubeStreams"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; rv:140.0) Gecko/20100101 Firefox/140.0"

    private val cache = ConcurrentHashMap<String, Pair<Stream, Long>>()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var initialised = false

    /**
     * Blocking — seconds for a track not heard lately; call it off the main thread.
     *
     * @param auth the YouTube Music session, when there is one.
     */
    fun resolve(context: Context, videoId: String, auth: YtAuth? = null): Stream? =
        cached("audio:$videoId") {
            YtDlp.resolve(context, videoId, auth, YtDlp.Kind.AUDIO) ?: anonymousStream(videoId)
        }

    /** The picture of a music video, without its sound: the player shows it in the cover's place. */
    fun resolveVideo(context: Context, videoId: String, auth: YtAuth? = null): Stream? =
        cached("video:$videoId") { YtDlp.resolve(context, videoId, auth, YtDlp.Kind.VIDEO) }

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
