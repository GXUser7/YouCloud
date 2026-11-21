package com.example.myapplication.player.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import android.util.LruCache
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * What a Cast device plays, served by the phone itself over the home network: the TV fetches
 * `http://<phone>:<port>/t/<token>` (or `/h/<token>.m3u8` for HLS streams, `/i/<token>` for covers)
 * and the phone reads the track as it would play it — from the download, the stream cache or the
 * service, through the same resolver ([resolve]) and data sources ([dataSources]). So a downloaded
 * track plays on the TV too, and so do audio/covers that require the phone's network or VPN.
 */
@UnstableApi
internal class CastStreamServer(
    private val context: Context,
    private val resolve: (DataSpec) -> DataSpec,
    private val dataSources: DataSource.Factory
) {
    private data class ImageTarget(val primary: Uri, val fallback: Uri?)
    private data class CachedImage(val bytes: ByteArray, val contentType: String)
    private data class SegmentTarget(val uri: Uri, val headers: Map<String, String>)
    private class OpenFailedException(cause: IOException) : IOException(cause.message, cause)

    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "CastStream").apply { isDaemon = true } }
    private val tokens = ConcurrentHashMap<String, Uri>()
    private val byUri = ConcurrentHashMap<String, String>()
    private val hlsTokens = ConcurrentHashMap<String, Uri>()
    private val hlsByUri = ConcurrentHashMap<String, String>()
    private val segments = ConcurrentHashMap<String, SegmentTarget>()
    private val segmentByKey = ConcurrentHashMap<String, String>()
    private val images = ConcurrentHashMap<String, ImageTarget>()
    private val imageByUri = ConcurrentHashMap<String, String>()
    private val imageCache = object : LruCache<String, CachedImage>(IMAGE_CACHE_BYTES) {
        override fun sizeOf(key: String, value: CachedImage): Int = value.bytes.size.coerceAtLeast(1)
    }
    private val imageHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    private val random = SecureRandom()

    @Synchronized
    fun start() {
        if (socket != null) return
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress(0))
        socket = server
        pool.execute {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute { serve(client) }
            }
        }
        Log.d(TAG, "Serving Cast on port ${server.localPort}")
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        tokens.clear()
        byUri.clear()
        hlsTokens.clear()
        hlsByUri.clear()
        segments.clear()
        segmentByKey.clear()
        images.clear()
        imageByUri.clear()
        synchronized(imageCache) { imageCache.evictAll() }
    }

    /** The address a Cast device fetches [uri] at; null without an address on Wi-Fi or Ethernet. */
    fun urlFor(uri: Uri): String? {
        val base = serverBaseUrl() ?: return null
        val token = byUri.getOrPut(uri.toString()) {
            newToken().also { tokens[it] = uri }
        }
        return "$base/t/$token"
    }

    /** The address a Cast device fetches an HLS playlist [uri] at, rewritten through this proxy. */
    fun hlsUrlFor(uri: Uri): String? {
        val base = serverBaseUrl() ?: return null
        val token = hlsByUri.getOrPut(uri.toString()) {
            newToken().also { hlsTokens[it] = uri }
        }
        return "$base/h/$token.m3u8"
    }

    /** The address a Cast device fetches a cover image [uri] (file, content, or HTTPS via phone VPN) at. */
    fun imageUrlFor(uri: Uri, fallback: Uri? = null): String? {
        val base = serverBaseUrl() ?: return null
        val key = "$uri|${fallback ?: ""}"
        val token = imageByUri.getOrPut(key) {
            newToken().also { images[it] = ImageTarget(uri, fallback) }
        }
        return "$base/i/$token"
    }

    private fun registerSegmentUrl(uri: Uri, headers: Map<String, String>): String? {
        val base = serverBaseUrl() ?: return null
        val headerKey = headers.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
        val token = segmentByKey.getOrPut("$uri|$headerKey") {
            newToken().also { segments[it] = SegmentTarget(uri, headers) }
        }
        return "$base/s/$token"
    }

    private fun serverBaseUrl(): String? {
        val port = socket?.localPort ?: return null
        val host = localAddress() ?: return null
        return "http://$host:$port"
    }

    private fun newToken(): String {
        val bytes = ByteArray(16).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun localAddress(): String? {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return null
        for (network in connectivity.allNetworks) {
            val caps = connectivity.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) continue
            val address = connectivity.getLinkProperties(network)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            if (address != null) return address.hostAddress
        }
        return null
    }

    private fun serve(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = 30_000
                val input = BufferedInputStream(socket.getInputStream())
                val requestLine = readLine(input) ?: return
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
                }
                val parts = requestLine.split(' ')
                val method = parts.getOrNull(0).orEmpty()
                val path = parts.getOrNull(1).orEmpty()
                val out = socket.getOutputStream()
                if (method == "OPTIONS") {
                    respond(
                        out,
                        "204 No Content",
                        mapOf(
                            "Access-Control-Allow-Methods" to "GET, HEAD, OPTIONS",
                            "Access-Control-Allow-Headers" to "Range, Content-Type, Accept",
                            "Access-Control-Max-Age" to "3600"
                        )
                    )
                    return
                }
                if (method != "GET" && method != "HEAD") {
                    respond(out, "404 Not Found", emptyMap())
                    return
                }
                val headOnly = method == "HEAD"
                when {
                    path.startsWith("/i/") -> {
                        val image = path.removePrefix("/i/").substringBefore('?').let(images::get)
                        if (image == null) respond(out, "404 Not Found", emptyMap()) else sendImage(image, headOnly, out)
                    }
                    path.startsWith("/h/") -> {
                        val token = path.removePrefix("/h/").substringBefore('?').removeSuffix(".m3u8")
                        val uri = hlsTokens[token]
                        if (uri == null) respond(out, "404 Not Found", emptyMap()) else sendHlsPlaylist(uri, headers["range"], headOnly, out)
                    }
                    path.startsWith("/s/") -> {
                        val segment = path.removePrefix("/s/").substringBefore('?').let(segments::get)
                        if (segment == null) respond(out, "404 Not Found", emptyMap()) else sendSegment(segment, headers["range"], headOnly, out)
                    }
                    path.startsWith("/t/") -> {
                        val uri = path.removePrefix("/t/").substringBefore('?').let(tokens::get)
                        if (uri == null) respond(out, "404 Not Found", emptyMap()) else send(uri, headers["range"], headOnly, out)
                    }
                    else -> respond(out, "404 Not Found", emptyMap())
                }
            } catch (e: IOException) {
                // The TV let go: it seeks by asking again.
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't serve a Cast request", e)
            }
        }
    }

    private fun send(uri: Uri, range: String?, headOnly: Boolean, out: OutputStream) {
        val spotifyId = uri.takeIf { it.scheme == "spotify" }?.lastPathSegment?.takeIf { it.isNotBlank() }
        val maxAttempts = if (spotifyId != null) 2 else 1
        for (attempt in 0 until maxAttempts) {
            val spec = try {
                resolve(DataSpec(uri))
            } catch (e: IOException) {
                Log.w(TAG, "Couldn't resolve Cast track $uri: ${e.message}")
                respond(out, "404 Not Found", emptyMap())
                return
            }
            if (isHls(spec)) {
                sendHls(spec, headOnly, out)
                return
            }
            try {
                streamSpec(spec, range, headOnly, out)
                return
            } catch (e: OpenFailedException) {
                if (spotifyId != null && attempt + 1 < maxAttempts) {
                    Log.w(TAG, "Cast upstream failed for spotify:$spotifyId, trying fallback: ${e.message}")
                    com.example.myapplication.data.SpotifyAudio.markStreamFailed(
                        spotifyId,
                        context,
                        reason = "Cast upstream: ${e.message}"
                    )
                    continue
                }
                Log.w(TAG, "Couldn't open Cast stream for $uri: ${e.message}")
                respond(out, "404 Not Found", emptyMap())
                return
            }
        }
    }

    private fun sendHlsPlaylist(uri: Uri, range: String?, headOnly: Boolean, out: OutputStream) {
        val spec = try {
            resolve(DataSpec(uri))
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't resolve Cast HLS track $uri: ${e.message}")
            respond(out, "404 Not Found", emptyMap())
            return
        }
        if (!isHls(spec)) {
            try {
                streamSpec(spec, range, headOnly, out)
            } catch (e: OpenFailedException) {
                Log.w(TAG, "Couldn't open resolved non-HLS stream for $uri: ${e.message}")
                respond(out, "404 Not Found", emptyMap())
            }
            return
        }
        try {
            var playlistUri = spec.uri
            var text = readText(spec)
            if (text.contains("#EXT-X-STREAM-INF")) {
                val variant = text.lines().map(String::trim).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if (variant == null) {
                    respond(out, "404 Not Found", emptyMap())
                    return
                }
                playlistUri = resolveAgainst(playlistUri, variant)
                text = readText(spec.buildUpon().setUri(playlistUri).setKey(null).build())
            }
            val rewritten = rewriteHlsPlaylist(playlistUri.toString(), text) { segUriStr ->
                registerSegmentUrl(Uri.parse(segUriStr), spec.httpRequestHeaders) ?: segUriStr
            }
            val bytes = rewritten.toByteArray(Charsets.UTF_8)
            respond(
                out,
                "200 OK",
                mapOf(
                    "Content-Type" to "application/vnd.apple.mpegurl; charset=utf-8",
                    "Content-Length" to bytes.size.toString(),
                    "Cache-Control" to "no-cache"
                )
            )
            if (!headOnly) {
                out.write(bytes)
                out.flush()
            }
            Log.d(TAG, "Served Cast HLS playlist for $uri (${bytes.size} B)")
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't serve Cast HLS playlist for $uri: ${e.message}")
            respond(out, "404 Not Found", emptyMap())
        }
    }

    private fun sendSegment(target: SegmentTarget, range: String?, headOnly: Boolean, out: OutputStream) {
        val spec = DataSpec.Builder()
            .setUri(target.uri)
            .setHttpRequestHeaders(target.headers)
            .build()
        try {
            streamSpec(spec, range, headOnly, out)
        } catch (e: OpenFailedException) {
            Log.w(TAG, "Couldn't serve Cast HLS segment ${target.uri}: ${e.message}")
            respond(out, "404 Not Found", emptyMap())
        }
    }

    private fun streamSpec(spec: DataSpec, range: String?, headOnly: Boolean, out: OutputStream) {
        val from = range?.let { RANGE.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val source = dataSources.createDataSource()
        var headerSent = false
        try {
            val length = source.open(spec.subrange(from))
            val head = ByteArray(16)
            val headRead = readFully(source, head)
            val type = source.responseHeaders.entries
                .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }
                ?.value?.firstOrNull()?.takeIf { it.startsWith("audio/") || it.startsWith("video/") }
                ?: sniff(head, headRead)
            val headers = mutableMapOf("Content-Type" to type, "Accept-Ranges" to "bytes")
            val status = if (length != C.LENGTH_UNSET.toLong()) {
                headers["Content-Length"] = length.toString()
                if (range != null) {
                    headers["Content-Range"] = "bytes $from-${from + length - 1}/${from + length}"
                    "206 Partial Content"
                } else {
                    "200 OK"
                }
            } else {
                "200 OK"
            }
            respond(out, status, headers)
            headerSent = true
            if (headOnly) return
            out.write(head, 0, headRead)
            copy(source, out)
        } catch (e: IOException) {
            if (headerSent) throw e else throw OpenFailedException(e)
        } finally {
            runCatching { source.close() }
        }
    }

    private fun sendImage(target: ImageTarget, headOnly: Boolean, out: OutputStream) {
        val cached = loadImage(target.primary) ?: target.fallback?.let(::loadImage)
        if (cached == null) {
            Log.w(TAG, "Cast cover fetch failed: ${target.primary}")
            respond(out, "404 Not Found", emptyMap())
            return
        }
        respond(
            out,
            "200 OK",
            mapOf(
                "Content-Type" to cached.contentType,
                "Content-Length" to cached.bytes.size.toString(),
                "Cache-Control" to "max-age=3600"
            )
        )
        if (!headOnly) {
            out.write(cached.bytes)
            out.flush()
        }
    }

    private fun loadImage(uri: Uri): CachedImage? {
        val key = uri.toString()
        synchronized(imageCache) { imageCache.get(key) }?.let { return it }

        val loaded: CachedImage? = when (uri.scheme) {
            "http", "https" -> {
                val localBytes = com.example.myapplication.data.ArtworkUrls.local(key)?.let { path ->
                    runCatching { File(path).takeIf { it.isFile && it.length() <= MAX_IMAGE_BYTES }?.readBytes() }.getOrNull()
                }
                if (localBytes != null && localBytes.isNotEmpty()) {
                    CachedImage(localBytes, sniffImage(localBytes))
                } else {
                    runCatching {
                        val req = Request.Builder()
                            .url(key)
                            .header("User-Agent", IMAGE_UA)
                            .build()
                        imageHttp.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) return@runCatching null
                            val bytes = resp.body?.bytes() ?: return@runCatching null
                            if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return@runCatching null
                            CachedImage(bytes, sniffImage(bytes))
                        }
                    }.getOrNull()
                }
            }
            "file" -> runCatching {
                uri.path?.let { File(it).takeIf { f -> f.isFile && f.length() <= MAX_IMAGE_BYTES }?.readBytes() }
            }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { CachedImage(it, sniffImage(it)) }
            else -> runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()?.takeIf { it.isNotEmpty() && it.size <= MAX_IMAGE_BYTES }?.let { CachedImage(it, sniffImage(it)) }
        }

        if (loaded != null) {
            synchronized(imageCache) { imageCache.put(key, loaded) }
        }
        return loaded
    }

    /** Fallback for an unresolved placeholder URI that turned out to be HLS on demand. */
    private fun sendHls(spec: DataSpec, headOnly: Boolean, out: OutputStream) {
        var playlistUri = spec.uri
        var text = readText(spec)
        if (text.contains("#EXT-X-STREAM-INF")) {
            val variant = text.lines().map(String::trim).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: return
            playlistUri = resolveAgainst(playlistUri, variant)
            text = readText(spec.buildUpon().setUri(playlistUri).setKey(null).build())
        }
        val segments = mutableListOf<Uri>()
        for (line in text.lines().map(String::trim)) {
            if (line.startsWith("#EXT-X-MAP:")) {
                MAP_URI.find(line)?.groupValues?.get(1)?.let { segments += resolveAgainst(playlistUri, it) }
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                segments += resolveAgainst(playlistUri, line)
            }
        }
        var headerSent = false
        for (segment in segments) {
            val source = dataSources.createDataSource()
            try {
                source.open(spec.buildUpon().setUri(segment).setKey(null).setPosition(0).setLength(C.LENGTH_UNSET.toLong()).build())
                if (!headerSent) {
                    val head = ByteArray(16)
                    val n = readFully(source, head)
                    respond(out, "200 OK", mapOf("Content-Type" to sniff(head, n)))
                    headerSent = true
                    if (headOnly) return
                    out.write(head, 0, n)
                }
                copy(source, out)
            } finally {
                runCatching { source.close() }
            }
        }
        if (!headerSent) respond(out, "404 Not Found", emptyMap())
    }

    private fun readText(spec: DataSpec): String {
        val source = dataSources.createDataSource()
        try {
            source.open(spec.subrange(0))
            val bytes = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = source.read(buffer, 0, buffer.size)
                if (n == C.RESULT_END_OF_INPUT) break
                bytes.write(buffer, 0, n)
                if (bytes.size() > 4_000_000) break
            }
            return bytes.toString(Charsets.UTF_8.name())
        } finally {
            runCatching { source.close() }
        }
    }

    private fun copy(source: DataSource, out: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = source.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buffer, 0, n)
        }
        out.flush()
    }

    private fun readFully(source: DataSource, into: ByteArray): Int {
        var total = 0
        while (total < into.size) {
            val n = source.read(into, total, into.size - total)
            if (n == C.RESULT_END_OF_INPUT) break
            total += n
        }
        return total
    }

    private fun respond(out: OutputStream, status: String, headers: Map<String, String>) {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            headers.forEach { (k, v) -> append(k).append(": ").append(v).append("\r\n") }
            // The Cast receiver is an HTTPS web page: allow cross-origin & local private network fetches.
            append("Access-Control-Allow-Origin: *\r\n")
            append("Access-Control-Allow-Private-Network: true\r\n")
            append("Cross-Origin-Resource-Policy: cross-origin\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(text.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val line = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (line.isEmpty()) null else line.toString()
            if (b == '\n'.code) return line.toString().trimEnd('\r')
            line.append(b.toChar())
            if (line.length > 8192) throw IOException("header too long")
        }
    }

    private fun isHls(spec: DataSpec): Boolean = isHlsCastUri(spec.uri)

    private fun resolveAgainst(base: Uri, ref: String): Uri =
        Uri.parse(java.net.URI(base.toString()).resolve(ref).toString())

    private fun DataSpec.subrange(from: Long): DataSpec =
        buildUpon().setPosition(from).setLength(C.LENGTH_UNSET.toLong()).build()

    companion object {
        private const val TAG = "CastStreamServer"
        private const val MAX_IMAGE_BYTES = 8_000_000
        private const val IMAGE_CACHE_BYTES = 8 * 1024 * 1024
        private const val IMAGE_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private val RANGE = Regex("bytes=(\\d+)-")
        private val MAP_URI = Regex("URI=\"([^\"]+)\"")

        /** What an image is by its first bytes. */
        internal fun sniffImage(bytes: ByteArray): String {
            fun at(i: Int) = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1
            return when {
                at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 -> "image/png"
                at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code -> "image/gif"
                at(0) == 'R'.code && at(1) == 'I'.code && at(2) == 'F'.code && at(3) == 'F'.code &&
                    at(8) == 'W'.code && at(9) == 'E'.code && at(10) == 'B'.code && at(11) == 'P'.code -> "image/webp"
                else -> "image/jpeg"
            }
        }

        /** What a file is by its first bytes, for the TV, which plays it by what it's told. */
        fun sniff(head: ByteArray, n: Int): String {
            fun at(i: Int) = if (i < n) head[i].toInt() and 0xFF else -1
            fun ascii(i: Int, s: String) = s.indices.all { at(i + it) == s[it].code }
            return when {
                ascii(4, "ftyp") || ascii(4, "styp") || ascii(4, "moof") -> "audio/mp4"
                ascii(0, "OggS") -> "audio/ogg"
                ascii(0, "fLaC") -> "audio/flac"
                ascii(0, "RIFF") -> "audio/wav"
                at(0) == 0x1A && at(1) == 0x45 && at(2) == 0xDF && at(3) == 0xA3 -> "audio/webm"
                at(0) == 0xFF && (at(1) and 0xF6) == 0xF0 -> "audio/aac"
                ascii(0, "ID3") || (at(0) == 0xFF && (at(1) and 0xE0) == 0xE0) -> "audio/mpeg"
                at(0) == 0x47 -> "video/mp2t"
                else -> "application/octet-stream"
            }
        }
    }
}
