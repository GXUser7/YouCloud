package com.example.myapplication.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Fetch a complete recording in bounded ranges, failing over across googlevideo mirrors per chunk
 * without throwing away already-downloaded data, and starting all workers at t=0 whenever the file
 * length is known (from stream metadata or the URL's `clen` parameter).
 */
object RangedDownload {
    enum class Outcome { OK, REJECTED_403, FAILED }

    private const val TAG = "RangedDownload"
    private const val DEFAULT_CHUNK_BYTES = 1024L * 1024
    private const val MIN_CHUNK_BYTES = 512L * 1024
    private const val MAX_CHUNK_BYTES = 2L * 1024 * 1024
    private const val ATTEMPTS = 2
    private const val AT_ONCE = 4
    private const val BAD_HOST_TTL_MS = 3 * 60_000L

    private val contentRange = Regex("""bytes (\d+)-(\d+)/(\d+)""")
    private val clenParam = Regex("""(?:^|[?&])clen=(\d+)(?:&|$)""")
    private val failedHosts = ConcurrentHashMap<String, Long>()

    private val pool = ConnectionPool(16, 2, TimeUnit.MINUTES)
    private val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 16
    }
    private val http = OkHttpClient.Builder()
        .connectionPool(pool)
        .dispatcher(dispatcher)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // Shorter timeouts when other mirrors are available so a dead VPN edge host fails over quickly.
    private val fastHttp = http.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun clearFailedHosts() {
        failedHosts.clear()
    }

    internal fun extractClen(url: String): Long? =
        clenParam.find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }

    internal fun adaptiveChunkBytes(total: Long): Long {
        if (total <= 0L) return DEFAULT_CHUNK_BYTES
        val split = (total + AT_ONCE - 1L) / AT_ONCE
        return split.coerceIn(MIN_CHUNK_BYTES, MAX_CHUNK_BYTES)
    }

    internal fun hostKey(url: String): String? = runCatching {
        val uri = URI(url)
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return@runCatching null
        if (uri.port > 0) "$host:${uri.port}" else host
    }.getOrNull()

    /**
     * [url], then the same stream at each of its googlevideo mirrors (`mn`): pure-JVM helper so
     * both production code and unit tests share exact mirror discovery.
     */
    internal fun mirrorsOf(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull() ?: return listOf(url)
        val host = uri.host?.takeIf { it.endsWith(".googlevideo.com") } ?: return listOf(url)
        val prefix = host.substringBefore("---", "").ifEmpty { return listOf(url) }
        val current = host.substringAfter("---").removeSuffix(".googlevideo.com")
        val rawMn = uri.rawQuery?.split('&')
            ?.firstOrNull { it.startsWith("mn=") }
            ?.substringAfter("mn=")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            .orEmpty()
        val mirrors = rawMn.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != current }
        return listOf(url) + mirrors.map { url.replaceFirst(host, "$prefix---$it.googlevideo.com") }
    }

    internal fun orderMirrors(addresses: List<String>, nowMs: Long = System.currentTimeMillis()): List<String> {
        if (addresses.size <= 1) return addresses
        val healthy = ArrayList<String>(addresses.size)
        val unhealthy = ArrayList<Pair<String, Long>>()
        for (addr in addresses) {
            val key = hostKey(addr)
            val failedAt = key?.let { failedHosts[it] }
            if (failedAt == null || nowMs - failedAt >= BAD_HOST_TTL_MS) {
                if (failedAt != null) failedHosts.remove(key, failedAt)
                healthy += addr
            } else {
                unhealthy += addr to failedAt
            }
        }
        return healthy + unhealthy.sortedBy { it.second }.map { it.first }
    }

    suspend fun toFile(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (Float) -> Unit = {},
        expectedLength: Long = -1L
    ): Boolean = toFileOutcome(url, headers, target, onProgress, expectedLength) == Outcome.OK

    internal suspend fun toFileOutcome(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (Float) -> Unit = {},
        expectedLength: Long = -1L,
        mirrorsOverride: List<String>? = null
    ): Outcome = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val mirrors = orderMirrors(mirrorsOverride?.takeIf { it.isNotEmpty() } ?: mirrorsOf(url))
        val activeMirror = AtomicInteger(0)
        val knownTotal = expectedLength.takeIf { it > 0L } ?: extractClen(url)
        try {
            if (knownTotal != null) {
                try {
                    fetchWithKnownTotal(mirrors, activeMirror, headers, target, knownTotal, onProgress)
                    logSuccess(target, mirrors, activeMirror, started)
                    return@withContext Outcome.OK
                } catch (_: FallbackToProbeException) {
                    target.delete()
                }
            }
            fetchProbeFirst(mirrors, activeMirror, headers, target, onProgress)
            logSuccess(target, mirrors, activeMirror, started)
            Outcome.OK
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: HttpStatusException) {
            logWarn("Recording download rejected (HTTP ${e.code})")
            target.delete()
            if (e.code == 403) Outcome.REJECTED_403 else Outcome.FAILED
        } catch (e: IOException) {
            logWarn("Recording download failed: ${e.javaClass.simpleName}: ${e.message}")
            target.delete()
            Outcome.FAILED
        }
    }

    private suspend fun fetchWithKnownTotal(
        mirrors: List<String>,
        activeMirror: AtomicInteger,
        headers: Map<String, String>,
        target: File,
        total: Long,
        progress: (Float) -> Unit
    ) {
        val chunkSize = adaptiveChunkBytes(total)
        val workerCount = minOf(AT_ONCE.toLong(), (total + chunkSize - 1L) / chunkSize).toInt().coerceAtLeast(1)
        val nextOffset = AtomicLong(0L)
        val written = AtomicLong(0L)
        RandomAccessFile(target, "rw").use { raf ->
            raf.setLength(total)
            val channel = raf.channel
            coroutineScope {
                List(workerCount) {
                    async {
                        while (true) {
                            ensureActive()
                            val offset = nextOffset.getAndAdd(chunkSize)
                            if (offset >= total) break
                            val end = minOf(offset + chunkSize - 1L, total - 1L)
                            val part = fetchChunk(
                                mirrors = mirrors,
                                activeMirror = activeMirror,
                                headers = headers,
                                offset = offset,
                                endInclusive = end,
                                expectedTotal = total,
                                allowFullFallback = false,
                                fullFile = null
                            ) ?: throw FallbackToProbeException()
                            writeFully(channel, part.bytes, offset)
                            progress((written.addAndGet(part.bytes.size.toLong()).toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }.awaitAll()
            }
        }
        if (written.get() != total || target.length() != total) throw IOException("Incomplete recording")
        progress(1f)
    }

    private suspend fun fetchProbeFirst(
        mirrors: List<String>,
        activeMirror: AtomicInteger,
        headers: Map<String, String>,
        target: File,
        progress: (Float) -> Unit
    ) {
        val first = fetchChunk(
            mirrors = mirrors,
            activeMirror = activeMirror,
            headers = headers,
            offset = 0L,
            endInclusive = DEFAULT_CHUNK_BYTES - 1L,
            expectedTotal = null,
            allowFullFallback = true,
            fullFile = target
        )
        if (first == null) {
            progress(1f)
            return
        }
        val total = first.total
        val written = AtomicLong(first.bytes.size.toLong())
        RandomAccessFile(target, "rw").use { raf ->
            raf.setLength(total)
            val channel = raf.channel
            writeFully(channel, first.bytes, 0L)
            progress((written.get().toFloat() / total).coerceIn(0f, 1f))
            val remaining = total - first.bytes.size
            if (remaining > 0L) {
                val chunkSize = adaptiveChunkBytes(remaining)
                val workerCount = minOf(AT_ONCE.toLong(), (remaining + chunkSize - 1L) / chunkSize).toInt().coerceAtLeast(1)
                val nextOffset = AtomicLong(first.bytes.size.toLong())
                coroutineScope {
                    List(workerCount) {
                        async {
                            while (true) {
                                ensureActive()
                                val offset = nextOffset.getAndAdd(chunkSize)
                                if (offset >= total) break
                                val end = minOf(offset + chunkSize - 1L, total - 1L)
                                val part = fetchChunk(
                                    mirrors = mirrors,
                                    activeMirror = activeMirror,
                                    headers = headers,
                                    offset = offset,
                                    endInclusive = end,
                                    expectedTotal = total,
                                    allowFullFallback = false,
                                    fullFile = null
                                ) ?: throw IOException("Expected a byte range")
                                writeFully(channel, part.bytes, offset)
                                progress((written.addAndGet(part.bytes.size.toLong()).toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }.awaitAll()
                }
            }
        }
        if (written.get() != total || target.length() != total) throw IOException("Incomplete recording")
        progress(1f)
    }

    private data class Part(val bytes: ByteArray, val total: Long)
    private class FallbackToProbeException : Exception()
    private class HttpStatusException(val code: Int) : IOException("Unexpected HTTP $code for a byte range")

    private fun fetchChunk(
        mirrors: List<String>,
        activeMirror: AtomicInteger,
        headers: Map<String, String>,
        offset: Long,
        endInclusive: Long,
        expectedTotal: Long?,
        allowFullFallback: Boolean,
        fullFile: File?
    ): Part? {
        if (mirrors.size == 1) {
            var lastError: IOException? = null
            repeat(ATTEMPTS) {
                try {
                    return rangeOnce(http, mirrors[0], headers, offset, endInclusive, expectedTotal, allowFullFallback, fullFile)
                } catch (e: FallbackToProbeException) {
                    throw e
                } catch (e: HttpStatusException) {
                    if (e.code == 403 || e.code == 404 || e.code == 410) throw e
                    lastError = e
                } catch (e: IOException) {
                    lastError = e
                }
            }
            throw lastError ?: IOException("Could not download recording")
        }

        // Multiple mirrors: try each mirror once with fast timeouts before falling back to relaxed timeouts.
        var lastError: IOException? = null
        var saw403 = false
        val startIdx = activeMirror.get().coerceIn(0, mirrors.lastIndex)
        for (pass in 0 until 2) {
            val client = if (pass == 0) fastHttp else http
            val limit = if (pass == 0) mirrors.size else minOf(2, mirrors.size)
            for (step in 0 until limit) {
                val idx = (startIdx + step) % mirrors.size
                val address = mirrors[idx]
                val host = hostKey(address)
                val attemptStarted = System.currentTimeMillis()
                try {
                    val result = rangeOnce(client, address, headers, offset, endInclusive, expectedTotal, allowFullFallback, fullFile)
                    if (host != null && idx == activeMirror.get()) {
                        // Only clear an older failure; never erase a failure recorded by a sibling
                        // worker on this same host while our request was already in flight.
                        failedHosts.compute(host) { _, failedAt ->
                            if (failedAt != null && failedAt >= attemptStarted) failedAt else null
                        }
                    }
                    return result
                } catch (e: FallbackToProbeException) {
                    throw e
                } catch (e: HttpStatusException) {
                    if (e.code == 403) saw403 = true
                    lastError = e
                    val next = (idx + 1) % mirrors.size
                    activeMirror.compareAndSet(idx, next)
                } catch (e: IOException) {
                    lastError = e
                    if (host != null) failedHosts[host] = System.currentTimeMillis()
                    val next = (idx + 1) % mirrors.size
                    activeMirror.compareAndSet(idx, next)
                }
            }
            if (saw403 && lastError is HttpStatusException && (lastError as HttpStatusException).code == 403) {
                throw lastError
            }
        }
        throw lastError ?: IOException("Could not download recording")
    }

    /** A whole response is streamed to disk when allowed. Partial responses must match their declared range. */
    private fun rangeOnce(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String>,
        offset: Long,
        endInclusive: Long,
        expectedTotal: Long?,
        allowFullFallback: Boolean,
        fullFile: File?
    ): Part? {
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .header("Range", "bytes=$offset-$endInclusive")
            .header("Accept-Encoding", "identity")
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Missing recording body")
            if (response.code == 200) {
                if (allowFullFallback && fullFile != null) {
                    val size = fullFile.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
                    val declared = body.contentLength()
                    if (size <= 0L || (declared >= 0 && size != declared)) throw IOException("Incomplete recording")
                    return@use null
                }
                throw FallbackToProbeException()
            }
            if (response.code == 416 && expectedTotal != null) throw FallbackToProbeException()
            if (response.code != 206) throw HttpStatusException(response.code)
            val parts = response.header("Content-Range")?.let(contentRange::matchEntire)?.groupValues
                ?: throw IOException("Missing or malformed Content-Range")
            val start = parts[1].toLongOrNull() ?: throw IOException("Invalid range start")
            val end = parts[2].toLongOrNull()
            val length = parts[3].toLongOrNull()
            if (expectedTotal != null && length != null && length != expectedTotal) {
                throw FallbackToProbeException()
            }
            if (start != offset || end == null || length == null || length <= offset ||
                end != minOf(endInclusive, length - 1)
            ) throw IOException("Unexpected Content-Range")
            val source = body.source()
            val bytes = source.readByteArray(end - start + 1)
            if (!source.exhausted()) throw IOException("Body exceeds Content-Range")
            Part(bytes, length)
        }
    }

    private fun writeFully(channel: FileChannel, bytes: ByteArray, offset: Long) {
        val buf = ByteBuffer.wrap(bytes)
        var pos = offset
        while (buf.hasRemaining()) {
            val n = channel.write(buf, pos)
            if (n <= 0) throw IOException("Short file write")
            pos += n
        }
    }

    private fun logSuccess(target: File, mirrors: List<String>, activeMirror: AtomicInteger, startedMs: Long) {
        val elapsed = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L)
        val size = target.length()
        val kb = size / 1024L
        val kbps = size * 1000L / 1024L / elapsed
        val host = hostKey(mirrors[activeMirror.get().coerceIn(0, mirrors.lastIndex)]) ?: "?"
        logInfo("Downloaded $kb KB from $host in $elapsed ms ($kbps KB/s)")
    }

    private fun logInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            // Pure JVM unit tests without Android Log stubs.
        }
    }

    private fun logWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {
            // Pure JVM unit tests without Android Log stubs.
        }
    }
}
