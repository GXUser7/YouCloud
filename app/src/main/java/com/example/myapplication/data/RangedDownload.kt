package com.example.myapplication.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Fetch a complete recording in bounded ranges, trying the stream's mirrors when needed. */
object RangedDownload {
    private const val TAG = "RangedDownload"
    private const val CHUNK_BYTES = 1024L * 1024
    private const val ATTEMPTS = 2
    private const val AT_ONCE = 4
    private val contentRange = Regex("""bytes (\d+)-(\d+)/(\d+)""")
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun toFile(
        url: String,
        headers: Map<String, String>,
        target: File,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        for (address in YouTubeStreams.withMirrors(url)) {
            if (fetchFrom(address, headers, target, onProgress)) return@withContext true
        }
        false
    }

    private suspend fun fetchFrom(url: String, headers: Map<String, String>, target: File, progress: (Float) -> Unit): Boolean {
        return try {
            val first = range(url, headers, offset = 0, total = null, fullFile = target)
            if (first != null) {
                val total = first.total
                target.writeBytes(first.bytes)
                val written = AtomicLong(first.bytes.size.toLong())
                progress(written.get().toFloat() / total)
                coroutineScope {
                    List(AT_ONCE) { worker ->
                        async {
                            var offset = first.bytes.size + worker * CHUNK_BYTES
                            while (offset < total) {
                                ensureActive()
                                val part = range(url, headers, offset, total, fullFile = null)
                                    ?: throw IOException("Expected a byte range")
                                RandomAccessFile(target, "rw").use { out ->
                                    out.seek(offset)
                                    out.write(part.bytes)
                                }
                                progress(written.addAndGet(part.bytes.size.toLong()).toFloat() / total)
                                offset += AT_ONCE * CHUNK_BYTES
                            }
                        }
                    }.awaitAll()
                }
                if (written.get() != total || target.length() != total) throw IOException("Incomplete recording")
            }
            progress(1f)
            true
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Recording download failed: ${e.javaClass.simpleName}")
            target.delete()
            false
        }
    }

    private data class Part(val bytes: ByteArray, val total: Long)

    private fun range(url: String, headers: Map<String, String>, offset: Long, total: Long?, fullFile: File?): Part? {
        var failure: IOException? = null
        repeat(ATTEMPTS) {
            try {
                return rangeOnce(url, headers, offset, total, fullFile)
            } catch (e: IOException) {
                failure = e
            }
        }
        throw failure ?: IOException("Could not download recording")
    }

    /** A whole response is streamed to disk. Partial responses must match their declared range. */
    private fun rangeOnce(url: String, headers: Map<String, String>, offset: Long, total: Long?, fullFile: File?): Part? {
        val request = Request.Builder().url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .header("Range", "bytes=$offset-${offset + CHUNK_BYTES - 1}")
            .header("Accept-Encoding", "identity")
            .build()
        return http.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Missing recording body")
            if (response.code == 200 && fullFile != null) {
                val size = fullFile.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
                val declared = body.contentLength()
                if (size <= 0L || (declared >= 0 && size != declared)) throw IOException("Incomplete recording")
                return@use null
            }
            if (response.code != 206) throw IOException("Unexpected HTTP ${response.code} for a byte range")
            val parts = response.header("Content-Range")?.let(contentRange::matchEntire)?.groupValues
                ?: throw IOException("Missing or malformed Content-Range")
            val start = parts[1].toLongOrNull() ?: throw IOException("Invalid range start")
            val end = parts[2].toLongOrNull()
            val length = parts[3].toLongOrNull()
            if (start != offset || end == null || length == null || length <= offset ||
                end != minOf(offset + CHUNK_BYTES - 1, length - 1) || (total != null && total != length)
            ) throw IOException("Unexpected Content-Range")
            val source = body.source()
            val bytes = source.readByteArray(end - start + 1)
            if (!source.exhausted()) throw IOException("Body exceeds Content-Range")
            Part(bytes, length)
        }
    }
}
