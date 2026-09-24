package com.example.myapplication.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Picks up a fresh OAuth token from the soundcloud.com web session the user signed in with.
 *
 * The tokens the app intercepts at sign-in are the website's own, and they expire. The site
 * renews them silently for as long as its session cookies live, so when ours dies we look where
 * the site keeps the current one: first its `oauth_token` cookie, which costs one request to
 * verify, then a headless WebView that loads the site, lets it renew, and hands back the
 * Authorization header its own API calls carry.
 *
 * That WebView is never attached to a window. It used to be composed into the screen, pushed
 * off to one side, and attaching a WebView to the app's window is what turned the whole app
 * white for a moment on some devices.
 *
 * Callers share one refresh: whoever arrives while it runs gets its result.
 */
object SoundCloudSessionRefresher {

    data class Credentials(val clientId: String, val oauthToken: String, val userId: Long)

    private const val TAG = "SoundCloudSession"
    private const val SITE_URL = "https://soundcloud.com/discover"
    private const val WEBVIEW_TIMEOUT_MS = 25_000L

    // A dead web session can't be fixed by trying again right away, and every in-flight request
    // would otherwise start its own 25-second attempt.
    private const val FAILURE_COOLDOWN_MS = 60_000L
    private const val REUSE_SUCCESS_MS = 60_000L

    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val mutex = Mutex()

    @Volatile
    private var lastFailureAt = 0L

    @Volatile
    private var lastSuccess: Credentials? = null

    @Volatile
    private var lastSuccessAt = 0L

    /**
     * @param staleToken the token SoundCloud just rejected; finding it again is not a refresh.
     * @param clientId used to verify a token found in the cookie jar.
     * @return verified credentials, or null when the web session is gone too and only a real
     *   sign-in can help.
     */
    suspend fun refresh(context: Context, staleToken: String, clientId: String): Credentials? =
        mutex.withLock {
            val now = System.currentTimeMillis()
            lastSuccess
                ?.takeIf { it.oauthToken != staleToken && now - lastSuccessAt < REUSE_SUCCESS_MS }
                ?.let { return@withLock it }
            if (now - lastFailureAt < FAILURE_COOLDOWN_MS) return@withLock null

            val result = fromCookie(clientId, staleToken)
                ?: fromWebView(context.applicationContext, staleToken)
            if (result != null) {
                Log.d(TAG, "Session renewed")
                lastSuccess = result
                lastSuccessAt = System.currentTimeMillis()
            } else {
                Log.w(TAG, "Could not renew the session")
                lastFailureAt = System.currentTimeMillis()
            }
            result
        }

    private suspend fun fromCookie(clientId: String, staleToken: String): Credentials? {
        if (clientId.isBlank()) return null
        val cookies = withContext(Dispatchers.Main) {
            CookieManager.getInstance().getCookie("https://soundcloud.com")
        } ?: return null
        val token = cookies.split(';')
            .map { it.trim() }
            .firstOrNull { it.startsWith("oauth_token=") }
            ?.substringAfter('=')
            ?.let(Uri::decode)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != staleToken }
            ?: return null
        return verify(clientId, token)
    }

    private suspend fun fromWebView(context: Context, staleToken: String): Credentials? {
        val captured = Channel<Pair<String, String>>(Channel.UNLIMITED)
        var webView: WebView? = null
        try {
            webView = withContext(Dispatchers.Main) {
                createHeadlessWebView(context) { clientId, token ->
                    if (token != staleToken) captured.trySend(clientId to token)
                }
            }
            return withTimeoutOrNull(WEBVIEW_TIMEOUT_MS) {
                val tried = HashSet<String>()
                for ((clientId, token) in captured) {
                    if (!tried.add("$clientId:$token")) continue
                    verify(clientId, token)?.let { return@withTimeoutOrNull it }
                }
                null
            }
        } finally {
            captured.close()
            val toDestroy = webView
            if (toDestroy != null) {
                withContext(NonCancellable + Dispatchers.Main) {
                    toDestroy.stopLoading()
                    toDestroy.destroy()
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createHeadlessWebView(
        context: Context,
        onCaptured: (clientId: String, token: String) -> Unit
    ): WebView = WebView(context).apply {
        // Never attached to a window, so give it a phone-sized viewport by hand; the site lays
        // itself out and boots as it would on a real screen.
        layout(0, 0, 1080, 1920)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = USER_AGENT
        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val outgoing = request ?: return null
                val clientId = outgoing.url.getQueryParameter("client_id")
                val authorization = outgoing.requestHeaders["Authorization"]
                    ?: outgoing.requestHeaders["authorization"]
                if (!clientId.isNullOrBlank() &&
                    authorization != null &&
                    authorization.startsWith("OAuth ", ignoreCase = true)
                ) {
                    val token = authorization.substring("OAuth ".length).trim()
                    if (token.isNotEmpty()) onCaptured(clientId, token)
                }
                return null
            }
        }
        loadUrl(SITE_URL)
    }

    private suspend fun verify(clientId: String, token: String): Credentials? =
        withContext(Dispatchers.IO) {
            runCatching {
                val me = SoundCloudApi.createService(oauthTokenProvider = { token }).getMe(clientId)
                Credentials(clientId = clientId, oauthToken = token, userId = me.id)
            }.getOrNull()
        }
}
