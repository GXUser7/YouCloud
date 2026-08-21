package com.example.myapplication.data

import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object SoundCloudApi {
    const val BASE_URL = "https://api-v2.soundcloud.com/"
    const val APP_VERSION = "1778677443"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // SoundCloud rotates the anonymous client_id regularly, and every in-flight request fails at
    // once when it happens. One scrape serves all of them.
    private val refreshMutex = Mutex()

    @Volatile
    private var lastRefreshAt = 0L
    private const val MIN_REFRESH_INTERVAL_MS = 15_000L

    /**
     * @param clientIdProvider read at request time rather than baked into each call, so a refresh
     *   takes effect immediately instead of on the next screen load.
     * @param onClientIdRefreshed called with a freshly scraped id so the caller can persist it.
     */
    fun createService(
        oauthTokenProvider: () -> String,
        clientIdProvider: () -> String = { "" },
        onClientIdRefreshed: (String) -> Unit = {}
    ): SoundCloudService {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val usedClientId = clientIdProvider().trim()
                var response = chain.proceed(decorate(original, oauthTokenProvider(), usedClientId))

                // A rotated client_id looks exactly like this. Swap in a fresh one and retry once,
                // so the failure never reaches the UI as "wait for the id to update".
                //
                // Only when a provider was supplied. Callers that omit it are verifying one
                // specific credential pair — silently healing their request would report a dead
                // pair as working and break the recovery ladder that depends on the answer.
                if ((response.code == 401 || response.code == 403) && usedClientId.isNotEmpty()) {
                    val fresh = runBlocking {
                        refreshClientId(usedClientId, clientIdProvider, onClientIdRefreshed)
                    }
                    if (fresh != null && fresh != usedClientId) {
                        response.close()
                        Log.d("SoundCloudApi", "Retrying ${original.url.encodedPath} with refreshed client_id")
                        response = chain.proceed(decorate(original, oauthTokenProvider(), fresh))
                    }
                }
                response
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SoundCloudService::class.java)
    }

    /**
     * Applies the browser-ish headers SoundCloud's private API expects, and forces [clientId] onto
     * the URL. Overriding rather than appending matters: Retrofit bakes the id in when the call is
     * built, so without this a retry would resend the stale one.
     */
    private fun decorate(original: Request, oauthToken: String, clientId: String): Request {
        val builder = original.newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")

        val token = oauthToken.trim()
        if (token.isNotEmpty()) {
            builder.header("Authorization", "OAuth $token")
        }

        // Only SoundCloud's own hosts take a client_id; media CDNs reject unexpected query params.
        if (clientId.isNotEmpty() && original.url.host.endsWith("soundcloud.com")) {
            builder.url(
                original.url.newBuilder()
                    .removeAllQueryParameters("client_id")
                    .addQueryParameter("client_id", clientId)
                    .build()
            )
        }
        return builder.build()
    }

    private suspend fun refreshClientId(
        used: String,
        clientIdProvider: () -> String,
        onClientIdRefreshed: (String) -> Unit
    ): String? = refreshMutex.withLock {
        // Another request may already have replaced it while this one waited for the lock.
        val current = clientIdProvider().trim()
        if (current.isNotEmpty() && current != used) return@withLock current

        val now = System.currentTimeMillis()
        if (now - lastRefreshAt < MIN_REFRESH_INTERVAL_MS) return@withLock null
        lastRefreshAt = now

        val fresh = fetchSoundCloudClientId()
        if (fresh != null) {
            Log.d("SoundCloudApi", "Scraped a fresh client_id")
            onClientIdRefreshed(fresh)
        } else {
            Log.w("SoundCloudApi", "Could not scrape a client_id")
        }
        fresh
    }

    /**
     * Pulls a working anonymous client_id out of soundcloud.com's own scripts.
     *
     * Candidates are verified with a real request before being returned. The bundles carry more
     * than one 32-character literal, and accepting the wrong one used to leave the app with an id
     * that failed every call — indistinguishable, from the outside, from the rotation it was
     * supposed to fix.
     */
    suspend fun fetchSoundCloudClientId(): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        // Different entry pages ship different bundles; if one stops exposing the id, another
        // usually still does.
        val pages = listOf("https://soundcloud.com/discover", "https://soundcloud.com")
        val clientIdRegex = """client_id\s*[:=]\s*["']?([a-zA-Z0-9]{32})""".toRegex()
        val tried = LinkedHashSet<String>()

        for (page in pages) {
            try {
                val html = client.newCall(
                    Request.Builder().url(page).header("User-Agent", USER_AGENT).build()
                ).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                } ?: continue

                val scriptUrls = """<script[^>]+src=["']([^"']+)["']""".toRegex()
                    .findAll(html)
                    .map { it.groupValues[1] }
                    .map { src ->
                        when {
                            src.startsWith("http://") || src.startsWith("https://") -> src
                            src.startsWith("//") -> "https:$src"
                            src.startsWith("/") -> "https://soundcloud.com$src"
                            else -> "https://soundcloud.com/$src"
                        }
                    }
                    .toList()
                    .reversed() // the id lives in the last bundles far more often than the first

                for (url in scriptUrls) {
                    try {
                        val js = client.newCall(
                            Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
                        ).execute().use { scriptResponse ->
                            if (!scriptResponse.isSuccessful) return@use null
                            scriptResponse.body?.string()
                        } ?: continue

                        for (match in clientIdRegex.findAll(js)) {
                            val candidate = match.groupValues[1]
                            if (candidate.isBlank() || !tried.add(candidate)) continue
                            if (isUsable(candidate, client)) return@withContext candidate
                        }
                    } catch (e: Exception) {
                        // try the next script
                    }
                }
            } catch (e: Exception) {
                Log.w("SoundCloudApi", "client_id scrape failed for $page", e)
            }
        }
        null
    }

    /** Cheap public call that only succeeds with a live client_id. */
    private fun isUsable(clientId: String, client: OkHttpClient): Boolean = try {
        val request = Request.Builder()
            .url("${BASE_URL}search/tracks?q=music&limit=1&client_id=$clientId")
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://soundcloud.com")
            .build()
        client.newCall(request).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }
}
