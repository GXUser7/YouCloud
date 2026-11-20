package com.example.myapplication.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.myapplication.i18n.tr
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.URI
import java.util.Base64
import kotlin.coroutines.resume

internal const val SPOTIFY_SITE = "https://open.spotify.com/"
internal const val SPOTIFY_LOGIN = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
internal const val SPOTIFY_ORIGIN = "https://open.spotify.com"
internal const val SPOTIFY_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"

internal class SpotifyFailure(message: String, val status: Int? = null) : IOException(message)

internal class SpotifyWebSession(val token: String, val clientToken: String, val expiresAt: Long)

internal fun spotifyHttpsHost(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    return uri.host?.lowercase(java.util.Locale.ROOT)?.takeIf {
        uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443)
    }
}

private val spotifyApiHosts = setOf("api-partner.spotify.com", "spclient.wg.spotify.com", "api.spotify.com")

internal fun spotifyRequestSession(
    url: String,
    headers: Map<String, String>,
    now: Long = System.currentTimeMillis()
): SpotifyWebSession? {
    if (spotifyHttpsHost(url) !in spotifyApiHosts) return null
    fun header(key: String) = headers.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    val origin = header("origin")
    if (origin != null && origin != SPOTIFY_ORIGIN) return null
    val token = header("authorization")?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
        ?.substring(7)?.trim()?.takeIf { it.isNotBlank() && it.length <= 8192 && it.none(Char::isWhitespace) } ?: return null
    val client = header("client-token")?.trim()
        ?.takeIf { it.isNotBlank() && it.length <= 8192 && it.none(Char::isWhitespace) } ?: return null
    val claims = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return@runCatching null
        JsonParser.parseString(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)).spotifyObject()
    }.getOrNull()
    val expiry = claims?.number("exp")?.takeIf { it in 1..Long.MAX_VALUE / 1000 }?.times(1000)
    val expiresAt = minOf(expiry ?: Long.MAX_VALUE, now + 45 * 60_000)
    return SpotifyWebSession(token, client, expiresAt).takeIf { expiresAt > now + 15_000 }
}

internal object SpotifyLogin {
    private const val playerHost = "open.spotify.com"
    private val main = Handler(Looper.getMainLooper())
    private val signInHosts = setOf(
        "accounts.spotify.com", playerHost, "spotify.com", "www.spotify.com",
        "accounts.google.com", "appleid.apple.com", "www.facebook.com", "m.facebook.com"
    )

    fun hasCookie(): Boolean =
        CookieManager.getInstance().getCookie(SPOTIFY_SITE).orEmpty().split(';').any {
            it.substringBefore('=').trim() == "sp_dc" && it.substringAfter('=', "").isNotBlank()
        }

    /** Spotify does the whole sign-in. Its cookies stay in WebView's cookie store. */
    @SuppressLint("SetJavaScriptEnabled")
    fun webView(
        context: Context,
        onSession: (SpotifyWebSession) -> Unit,
        onLoading: (Boolean) -> Unit = {},
        onError: () -> Unit = {}
    ): WebView = WebView(context).apply {
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, false)
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
        }
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            @Volatile private var player = false
            private var lastToken: String? = null
            private var rendererGone = false

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                if (spotifyHttpsHost(request.url.toString()) in signInHosts) return false
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                val host = spotifyHttpsHost(url.orEmpty())
                player = host == playerHost
                if (host !in signInHosts) {
                    view.stopLoading()
                    onError()
                    return
                }
                // The mobile landing page can hand off to the Spotify app. Load the Web Player for
                // the catalog as soon as we return from the mobile sign-in form.
                if (player && view.settings.userAgentString != SPOTIFY_AGENT) {
                    player = false
                    view.stopLoading()
                    view.settings.userAgentString = SPOTIFY_AGENT
                    view.loadUrl(url.orEmpty())
                    return
                }
                onLoading(true)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                onLoading(false)
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (!player || request.isForMainFrame) return null
                val session = spotifyRequestSession(request.url.toString(), request.requestHeaders) ?: return null
                main.post {
                    if (rendererGone || !player || lastToken == session.token || !hasCookie()) return@post
                    lastToken = session.token
                    onSession(session)
                }
                return null
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) onError()
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) onError()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone = true
                player = false
                onError()
                return true
            }
        }
    }

    suspend fun refresh(context: Context, rejected: String?): SpotifyWebSession? = withContext(Dispatchers.Main.immediate) {
        if (!hasCookie()) return@withContext null
        val captured = Channel<SpotifyWebSession>(capacity = 1)
        val view = webView(
            context,
            onSession = { if (it.token != rejected) captured.trySend(it) },
            onError = { captured.close() }
        )
        try {
            view.settings.userAgentString = SPOTIFY_AGENT
            view.layout(0, 0, 1080, 1920)
            view.loadUrl(SPOTIFY_SITE)
            withTimeoutOrNull(25_000) { captured.receiveCatching().getOrNull() }
        } finally {
            captured.close()
            view.stopLoading()
            view.destroy()
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.Main.immediate) {
        val cookies = CookieManager.getInstance()
        for (url in listOf(SPOTIFY_SITE, "https://accounts.spotify.com/")) {
            for (name in listOf("sp_dc", "sp_key")) {
                for (domain in listOf("", "; Domain=.spotify.com")) {
                    suspendCancellableCoroutine { done ->
                        cookies.setCookie(url, "$name=; Max-Age=0; Path=/; Secure$domain") { done.resume(Unit) }
                    }
                }
            }
        }
        withContext(Dispatchers.IO) { cookies.flush() }
    }
}
