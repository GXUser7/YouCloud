package com.example.myapplication.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Downloads a file a megabyte at a time, a few pieces at once. googlevideo sends a whole file at
 * about the pace it plays and a bounded range at full speed (yt-dlp downloads YouTube in pieces for
 * the same reason); it also leaves a request hanging now and then, and some of its hosts can't be
 * reached through a VPN at all while their mirrors can. So: ranges, each tried again when it
 * hangs, and the stream's mirrors when its own host doesn't answer.
 */
object RangedDownload {
    private const val TAG = "RangedDownload"
    private const val CHUNK_BYTES = 1L * 1024 * 1024
    private const val ATTEMPTS = 2
    private const val AT_ONCE = 4

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** [url] into [target], from its own host or a mirror. False if none of them would send it. */
    suspend fun toFile(url: String, headers: Map<String, String>, target: File): Boolean = withContext(Dispatchers.IO) {
        for (address in YouTubeStreams.withMirrors(url)) {
            if (fetchFrom(address, headers, target)) return@withContext true
        }
        false
    }

    private suspend fun fetchFrom(url: String, headers: Map<String, String>, target: File): Boolean {
        val started = System.currentTimeMillis()
        return try {
            target.delete()
            val (first, total) = range(url, headers, 0)
            RandomAccessFile(target, "rw").use { it.write(first) }
            if (total != null && total > first.size) {
                val gate = Semaphore(AT_ONCE)
                coroutineScope {
                    (first.size.toLong() until total step CHUNK_BYTES).map { offset ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                val (bytes, _) = range(url, headers, offset)
                                RandomAccessFile(target, "rw").use { out ->
                                    out.seek(offset)
                                    out.write(bytes)
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
            Log.d(TAG, "${target.length() / 1024} KB from ${url.substringAfter("//").substringBefore('/')} in ${System.currentTimeMillis() - started} ms")
            true
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't fetch ${url.take(80)}: $e")
            target.delete()
            false
        }
    }

    /**
     * [CHUNK_BYTES] of [url] from [offset], and the whole file's size when the server gave a range
     * (null: it sent everything at once, which is then what came back).
     */
    private fun range(url: String, headers: Map<String, String>, offset: Long): Pair<ByteArray, Long?> {
        var failure: IOException? = null
        repeat(ATTEMPTS) {
            try {
                return rangeOnce(url, headers, offset)
            } catch (e: IOException) {
                failure = e
            }
        }
        throw failure!!
    }

    private fun rangeOnce(url: String, headers: Map<String, String>, offset: Long): Pair<ByteArray, Long?> {
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=$offset-${offset + CHUNK_BYTES - 1}")
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return http.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes() ?: throw IOException("no body")
            when (response.code) {
                200 -> bytes to null
                206 -> bytes to response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                else -> throw IOException("HTTP ${response.code}")
            }
        }
    }
}
