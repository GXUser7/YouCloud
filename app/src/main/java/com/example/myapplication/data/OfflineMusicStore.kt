package com.example.myapplication.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import android.util.Log
import okhttp3.OkHttpClient
import java.io.File
@UnstableApi
class OfflineMusicStore private constructor(context: Context) {
    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val downloadDirectory = File(context.filesDir, "offline_music").apply { mkdirs() }
    private val artworkDirectory = File(context.filesDir, "offline_art").apply { mkdirs() }
    private val hlsCacheDirectory = File(downloadDirectory, "hls_cache").apply { mkdirs() }
    private val cache = SimpleCache(hlsCacheDirectory, NoOpCacheEvictor(), databaseProvider)

    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun downloadHls(streamUrl: String, onProgress: (Float) -> Unit = {}) {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .build()

        HlsDownloader(mediaItem, cacheDataSourceFactory)
            .download { _, bytesDownloaded, percentDownloaded ->
                if (percentDownloaded >= 0f) {
                    onProgress(percentDownloaded)
                } else if (bytesDownloaded > 0L) {
                    onProgress(0f)
                }
            }
    }

    fun removeHls(streamUrl: String) {
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .build()

        HlsDownloader(mediaItem, cacheDataSourceFactory).remove()
    }

    /**
     * @param chunked fetch in [CHUNK_BYTES] ranges. YouTube serves a whole file at barely more than
     *   playback speed and a ranged request at full speed, so its audio comes down in pieces.
     */
    fun downloadProgressive(
        url: String,
        trackId: String,
        extension: String = "mp3",
        chunked: Boolean = false,
        userAgent: String? = null,
        onProgress: (Float) -> Unit = {}
    ): String? {
        val tempFile = File(downloadDirectory, "track_${trackId}.$extension.tmp")
        val finalFile = File(downloadDirectory, "track_${trackId}.$extension")
        if (chunked) return downloadInChunks(url, tempFile, finalFile, userAgent, onProgress)
        return try {
            val client = OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                val contentLength = body.contentLength()
                
                tempFile.outputStream().use { outputStream ->
                    body.byteStream().use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead = 0L
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            bytesRead += read
                            if (contentLength > 0) {
                                val progress = bytesRead.toFloat() / contentLength
                                onProgress(progress.coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                
                if (contentLength > 0 && tempFile.length() != contentLength) {
                    throw java.io.IOException("File download incomplete. Expected $contentLength bytes, but got ${tempFile.length()} bytes")
                }

                if (tempFile.length() <= 0L) {
                    throw java.io.IOException("File download empty. Got 0 bytes.")
                }
                
                if (tempFile.renameTo(finalFile)) {
                    finalFile.absolutePath
                } else {
                    throw java.io.IOException("Failed to rename temp file to final file")
                }
            }
        } catch (e: Exception) {
            Log.e("OfflineMusicStore", "Failed to download progressive track", e)
            if (tempFile.exists()) {
                tempFile.delete()
            }
            null
        }
    }

    private fun downloadInChunks(
        url: String,
        tempFile: File,
        finalFile: File,
        userAgent: String?,
        onProgress: (Float) -> Unit
    ): String? = try {
        val client = OkHttpClient()
        var position = 0L
        var total = -1L
        tempFile.outputStream().use { output ->
            while (total < 0 || position < total) {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$position-${position + CHUNK_BYTES - 1}")
                    .apply { if (userAgent != null) header("User-Agent", userAgent) }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) {
                        throw java.io.IOException("HTTP ${response.code} at byte $position")
                    }
                    val bytes = response.body?.bytes() ?: ByteArray(0)
                    if (total < 0) {
                        total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull()
                            ?: bytes.size.toLong()
                    }
                    // A server that ignores Range sends everything at once.
                    if (response.code == 200) total = bytes.size.toLong()
                    if (bytes.isEmpty()) throw java.io.IOException("Empty chunk at byte $position")
                    output.write(bytes)
                    position += bytes.size
                }
                if (total > 0) onProgress((position.toFloat() / total).coerceIn(0f, 1f))
            }
        }
        if (tempFile.length() <= 0L || tempFile.length() != total) {
            throw java.io.IOException("Got ${tempFile.length()} of $total bytes")
        }
        if (!tempFile.renameTo(finalFile)) throw java.io.IOException("Failed to rename temp file")
        finalFile.absolutePath
    } catch (e: Exception) {
        Log.e("OfflineMusicStore", "Failed to download track in chunks", e)
        tempFile.delete()
        null
    }

    /** Where the cached cover for [trackId] lives, whether or not it has been fetched yet. */
    fun artworkFile(trackId: Long): File = File(artworkDirectory, "art_$trackId.jpg")

    /**
     * Saves a track's cover next to its audio so it survives going offline. Android Auto renders
     * browse lists in its own process and fetches artwork itself, so a remote URL leaves the car
     * with blank tiles the moment there is no connection — which is exactly where an offline
     * library gets used.
     *
     * Downscaled to at most [MAX_ARTWORK_PX] on the long edge: the car and the track rows never
     * show it larger, and full-size covers would waste a lot of space across a big library.
     */
    fun downloadArtwork(url: String, trackId: Long): String? {
        val target = artworkFile(trackId)
        if (target.exists() && target.length() > 0L) return target.absolutePath
        return try {
            val client = OkHttpClient()
            val request = okhttp3.Request.Builder().url(url).build()
            val bytes = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes() ?: return null
            }
            val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return null
            val longest = maxOf(decoded.width, decoded.height)
            val scaled = if (longest > MAX_ARTWORK_PX) {
                val ratio = MAX_ARTWORK_PX.toFloat() / longest
                android.graphics.Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width * ratio).toInt().coerceAtLeast(1),
                    (decoded.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                decoded
            }
            val temp = File(artworkDirectory, "art_$trackId.jpg.tmp")
            temp.outputStream().use { out ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            if (temp.length() <= 0L) {
                temp.delete()
                return null
            }
            target.delete()
            if (temp.renameTo(target)) target.absolutePath else null
        } catch (e: Exception) {
            Log.e("OfflineMusicStore", "Failed to cache artwork for $trackId", e)
            null
        }
    }

    fun removeArtwork(trackId: Long) {
        try {
            val file = artworkFile(trackId)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.e("OfflineMusicStore", "Failed to remove artwork for $trackId", e)
        }
    }

    fun removeProgressive(localPath: String) {
        try {
            val file = File(localPath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e("OfflineMusicStore", "Failed to remove progressive track", e)
        }
    }

    fun release() {
        cache.release()
    }

    companion object {
        private const val CHUNK_BYTES = 1L shl 20

        private const val MAX_ARTWORK_PX = 640

        @Volatile
        private var instance: OfflineMusicStore? = null

        fun getInstance(context: Context): OfflineMusicStore =
            instance ?: synchronized(this) {
                instance ?: OfflineMusicStore(context.applicationContext).also { instance = it }
            }
    }
}
