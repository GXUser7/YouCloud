package com.example.myapplication.data

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Base64

/** The Web Player's catalog operations, also used by YouCloud Web. */
internal class SpotifyQueries(private val http: OkHttpClient) {
    @Volatile private var hashes = fallback
    private val mutex = Mutex()
    private var lastChecked = 0L

    fun hash(operation: String): String = hashes.getValue(operation)

    suspend fun refresh(operation: String, stale: String) = mutex.withLock {
        if (hash(operation) != stale || System.currentTimeMillis() - lastChecked < 60_000) return@withLock
        lastChecked = System.currentTimeMillis()
        val html = download(SPOTIFY_SITE)
        val encoded = urls.find(html)?.groupValues?.get(1)?.trim() ?: return@withLock
        val files = runCatching {
            JsonParser.parseString(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)).spotifyObject()
        }.getOrNull() ?: return@withLock
        val scripts = listOf("web-player", "xpui-routes-search", "xpui-routes-artist", "xpui-routes-album", "xpui-routes-playlist")
        val published = coroutineScope {
            scripts.mapNotNull { files.string("build/web-player/$it.js") }
                .filter { spotifyHttpsHost(it) == "open.spotifycdn.com" && it.startsWith("https://open.spotifycdn.com/cdn/build/web-player/") }
                .map { address -> async { download(address) } }.map { it.await() }
        }
        hashes = hashes + published.flatMap { extractOperations(it).entries }.associate { it.key to it.value }
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", SPOTIFY_AGENT).build()
        try {
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string().orEmpty() else ""
            }
        } catch (_: IOException) {
            ""
        }
    }

    companion object {
        private val urls = Regex("""<script[^>]+id=["']__CDN_FILE_URLS__["'][^>]*>([^<]+)</script>""")
        private val operations = Regex("""["']([a-zA-Z0-9_]+)["']\s*,\s*["'](?:query|mutation)["']\s*,\s*["']([a-f0-9]{64})["']""")

        internal fun extractOperations(script: String): Map<String, String> =
            operations.findAll(script).filter { it.groupValues[1] in fallback }
                .associate { it.groupValues[1] to it.groupValues[2] }

        private val fallback = mapOf(
            "home" to "5fb7da7a03ee2776c0670856609cb85a189a3cecc36cc160b91cde79063b9cf1",
            "getAlbum" to "0b6ce3511e1b02e4a40fa95440f992acbe85080eb308af9194322d708fc1914f",
            "fetchPlaylist" to "21ea11a6ac75a5f3e01fd365d29c18e0a4a8b7dc09f89bbb5d73910190c6acaf",
            "queryArtistOverview" to "eafb1620d783ece118e72fbfede2b0064483e029d3c1aafafa1c2de0e25a89e7",
            "getTrack" to "e7b1bf4e95cb3d733d48b442f93006e9712fd504e8b88d7a82e6e3bac9306a6b",
            "fetchLibraryTracks" to "087278b20b743578a6262c2b0b4bcd20d879c503cc359a2285baf083ef944240",
            "libraryV3" to "390c78e5b951029bad359785e69b07b536a509c581cbcd0aded5e5067f187455",
            "areEntitiesInLibrary" to "134337999233cc6fdd6b1e6dbf94841409f04a946c5c7b744b09ba0dfe5a85ed",
            "addToLibrary" to "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
            "removeFromLibrary" to "896ebcb47815681340860d121cb5d494e157e2a78d3950385cd54e0393c67148",
            "profileAttributes" to "08ffb4730af3746e04a8301396f20875dbbce10c75243803091a9274eacc8ac0",
            "searchDesktop" to "eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03",
            "searchTracks" to "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751",
        )
    }
}
