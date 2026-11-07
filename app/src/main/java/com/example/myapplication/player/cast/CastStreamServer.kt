package com.example.myapplication.player.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * What a Cast device plays, served by the phone itself over the home network: the TV fetches
 * `http://<phone>:<port>/t/<token>` and the phone reads the track as it would play it — from the
 * download, the stream cache or the service, through the same resolver ([resolve]) and data
 * sources ([dataSources]). So a downloaded track plays on the TV too, and so does YouTube's audio,
 * whose links work only from the address that asked for them: the phone's.
 *
 * Only what is in the Cast queue is served, each track under a token of its own, random and long:
 * nothing else on the phone can be asked for. A track the service streams as HLS is sent on as one
 * file, its segments one after another — the TV plays it through, though it can't seek in it.
 */
@UnstableApi
internal class CastStreamServer(
    private val context: Context,
    private val resolve: (DataSpec) -> DataSpec,
    private val dataSources: DataSource.Factory
) {
    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "CastStream").apply { isDaemon = true } }
    private val tokens = ConcurrentHashMap<String, Uri>()
    private val byUri = ConcurrentHashMap<String, String>()
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
    }

    /** The address a Cast device fetches [uri] at; null without an address on Wi-Fi or Ethernet. */
    fun urlFor(uri: Uri): String? {
        val port = socket?.localPort ?: return null
        val host = localAddress() ?: return null
        val key = uri.toString()
        val token = byUri.getOrPut(key) {
            val bytes = ByteArray(16).also(random::nextBytes)
            bytes.joinToString("") { "%02x".format(it) }.also { tokens[it] = uri }
        }
        return "http://$host:$port/t/$token"
    }

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
                val uri = path.removePrefix("/t/").substringBefore('?').let(tokens::get)
                if ((method != "GET" && method != "HEAD") || !path.startsWith("/t/") || uri == null) {
                    respond(out, "404 Not Found", emptyMap())
                    return
                }
                send(uri, headers["range"], method == "HEAD", out)
            } catch (e: IOException) {
                // The TV let go: it seeks by asking again.
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't serve a Cast request", e)
            }
        }
    }

    private fun send(uri: Uri, range: String?, headOnly: Boolean, out: OutputStream) {
        val from = range?.let { RANGE.find(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val spec = resolve(DataSpec(uri))
        if (isHls(spec)) {
            sendHls(spec, headOnly, out)
            return
        }
        val source = dataSources.createDataSource()
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
            if (headOnly) return
            out.write(head, 0, headRead)
            copy(source, out)
        } finally {
            runCatching { source.close() }
        }
    }

    /** An HLS stream as one file: its init segment, if any, then each segment in turn. */
    private fun sendHls(spec: DataSpec, headOnly: Boolean, out: OutputStream) {
        var playlistUri = spec.uri
        var text = readText(spec)
        // A master playlist: its first variant.
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
            // The Cast receiver is a web page: it may read the stream only if the phone allows it.
            append("Access-Control-Allow-Origin: *\r\n")
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

    private fun isHls(spec: DataSpec): Boolean {
        val path = spec.uri.path.orEmpty()
        return path.endsWith(".m3u8") || spec.uri.toString().contains("m3u8")
    }

    private fun resolveAgainst(base: Uri, ref: String): Uri =
        Uri.parse(java.net.URI(base.toString()).resolve(ref).toString())

    private fun DataSpec.subrange(from: Long): DataSpec = buildUpon().setPosition(from).setLength(C.LENGTH_UNSET.toLong()).build()

    companion object {
        private const val TAG = "CastStreamServer"
        private val RANGE = Regex("bytes=(\\d+)-")
        private val MAP_URI = Regex("URI=\"([^\"]+)\"")

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
