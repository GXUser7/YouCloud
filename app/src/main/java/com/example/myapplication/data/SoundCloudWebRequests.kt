package com.example.myapplication.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends SoundCloud API requests from inside a soundcloud.com page rather than the app's HTTP
 * client — for the ones its bot protection guards, such as likes.
 *
 * DataDome refuses likes from VPN addresses until a captcha is solved, and the cookie solving it
 * earns is honoured only from the browser that earned it: replayed from OkHttp, with a different
 * TLS handshake and client hints, it got banned on the spot. A WebView is that browser. The page
 * runs DataDome's own script, the captcha is solved in the same WebView profile (same engine,
 * user agent and cookie jar — so neither overrides the user agent), and the request goes out as
 * the website's like button sends it.
 *
 * The WebView is never attached to a window (see [SoundCloudSessionRefresher] for why) and is
 * kept for a minute after use, so liking several tracks in a row loads the site once.
 */
object SoundCloudWebRequests {

    data class Result(val status: Int, val body: String) {
        val isSuccessful get() = status in 200..299

        /** DataDome's refusal: a 403 whose body is `{"url": "<captcha link>"}`. */
        val captchaUrl: String?
            get() = if (status == 403 && "captcha-delivery.com" in body) {
                runCatching { JSONObject(body).optString("url") }.getOrNull()
                    ?.takeIf { it.startsWith("https://") }
            } else {
                null
            }
    }

    private const val TAG = "SoundCloudWeb"
    private const val PAGE_URL = "https://soundcloud.com/"
    private const val LOAD_TIMEOUT_MS = 30_000L
    private const val REQUEST_TIMEOUT_MS = 20_000L

    // Lets the page's own scripts, DataDome's among them, run before the first request.
    private const val SETTLE_MS = 1_500L
    private const val KEEP_ALIVE_MS = 60_000L

    private const val BRIDGE = "YouCloudBridge"

    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val results = ConcurrentHashMap<String, CompletableDeferred<Result>>()

    // Main thread only.
    private var page: WebView? = null
    private var pageReady: CompletableDeferred<Boolean>? = null
    private val destroyPage = Runnable {
        page?.destroy()
        page = null
        pageReady = null
    }

    /**
     * @param url full API url, client_id included.
     * @return SoundCloud's answer, or null when the page didn't load or the request never
     *   finished.
     */
    suspend fun send(context: Context, method: String, url: String, oauthToken: String): Result? =
        mutex.withLock {
            val ready = withContext(Dispatchers.Main) {
                mainHandler.removeCallbacks(destroyPage)
                ensurePage(context.applicationContext)
            }
            try {
                if (withTimeoutOrNull(LOAD_TIMEOUT_MS) { ready.await() } != true) {
                    Log.w(TAG, "soundcloud.com didn't load")
                    withContext(NonCancellable + Dispatchers.Main) { destroyPage.run() }
                    return@withLock null
                }
                val id = UUID.randomUUID().toString()
                val result = CompletableDeferred<Result>()
                results[id] = result
                try {
                    withContext(Dispatchers.Main) {
                        page?.evaluateJavascript(script(id, method, url, oauthToken), null)
                    }
                    val answer = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { result.await() }
                    if (answer == null) Log.w(TAG, "$method $url didn't finish in time")
                    answer
                } finally {
                    results.remove(id)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    mainHandler.removeCallbacks(destroyPage)
                    mainHandler.postDelayed(destroyPage, KEEP_ALIVE_MS)
                }
            }
        }

    /**
     * Stores the cookie a solved captcha hands over, where both this page and the captcha
     * WebView read it. [cookie] is a `Set-Cookie` line, or a bare value from older captcha pages.
     */
    fun acceptCaptchaCookie(cookie: String) {
        val line = cookie.trim().let {
            if (it.startsWith("datadome=")) it
            else "datadome=$it; Max-Age=31536000; Domain=.soundcloud.com; Path=/; Secure; SameSite=Lax"
        }
        CookieManager.getInstance().apply {
            setCookie("https://soundcloud.com", line)
            flush()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun ensurePage(context: Context): CompletableDeferred<Boolean> {
        pageReady?.let { if (page != null) return it }
        val ready = CompletableDeferred<Boolean>()
        pageReady = ready
        page = WebView(context).apply {
            // Never attached to a window, so give it a phone-sized viewport by hand.
            layout(0, 0, 1080, 1920)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            val cookies = CookieManager.getInstance()
            cookies.setAcceptCookie(true)
            cookies.setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(Bridge, BRIDGE)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    mainHandler.postDelayed({ ready.complete(true) }, SETTLE_MS)
                }
            }
            loadUrl(PAGE_URL)
        }
        return ready
    }

    private object Bridge {
        @JavascriptInterface
        fun onResult(id: String, status: Int, body: String) {
            results[id]?.complete(Result(status, body))
        }
    }

    // The same request the website's like button makes, datadome header included.
    private fun script(id: String, method: String, url: String, oauthToken: String): String = """
        (function() {
          var id = ${JSONObject.quote(id)};
          var headers = {
            'Authorization': ${JSONObject.quote("OAuth $oauthToken")},
            'Accept': 'application/json, text/javascript, */*; q=0.1'
          };
          var dd = document.cookie.match(/(?:^|;\s*)datadome=([^;]+)/);
          if (dd) headers['X-Datadome-ClientId'] = decodeURIComponent(dd[1]);
          fetch(${JSONObject.quote(url)}, { method: ${JSONObject.quote(method)}, headers: headers, credentials: 'include' })
            .then(function(r) {
              return r.text().then(function(t) { $BRIDGE.onResult(id, r.status, t); });
            })
            .catch(function(e) { $BRIDGE.onResult(id, 0, String(e)); });
        })();
    """.trimIndent()
}
