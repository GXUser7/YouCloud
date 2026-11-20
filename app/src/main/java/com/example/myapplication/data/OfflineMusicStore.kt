package com.example.myapplication.data

import kotlinx.coroutines.ensureActive

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
    // Shared with the video cache: one database, one helper holding it open.
    val databaseProvider = StandaloneDatabaseProvider(context)
    private val downloadDirectory = File(context.filesDir, "offline_music").apply { mkdirs() }
    private val artworkDirectory = File(context.filesDir, "offline_art").apply { mkdirs() }
    private val hlsCacheDirectory = File(downloadDirectory, "hls_cache").apply { mkdirs() }
    private val cache = SimpleCache(hlsCacheDirectory, NoOpCacheEvictor(), databaseProvider)

    // One client for every download: a client of its own per file brought its own connection pool
    // and threads each time, kept alive for minutes after — a hundred of them for a playlist.
    private val http by lazy { OkHttpClient() }

    // What downloads are written through.
    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * What playback reads through: a file on the phone as it is; a download kept here, without
     * writing anything to it; anything else through the [StreamCache]. Played through the
     * downloads' cache, every track heard from the internet was written into it and never left.
     * Lazy: the stream cache shares this store's database, so it can't be made while this is.
     */
    val playbackDataSourceFactory: androidx.media3.datasource.DataSource.Factory by lazy {
        LocalOrCachedDataSource.Factory(
            cached = CacheDataSource.Factory()
                .setCache(cache)
                .setCacheWriteDataSinkFactory(null)
                .setUpstreamDataSourceFactory(StreamCache.dataSourceFactory(appContext))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR),
            local = DefaultDataSource.Factory(appContext)
        )
    }
    private val appContext = context.applicationContext

    /**
     * Takes out of the downloads' cache what playback left in it that is surely no download: YouTube
     * tracks (kept as files when downloaded), Yandex's links (so are they), files on the phone
     * copied in. What SoundCloud streamed stays: it can't be told from SoundCloud downloads.
     */
    fun dropStreamedLeftovers() {
        val leftovers = cache.keys.filter { key ->
            key.startsWith("ytmusic:") || key.contains("/get-mp3/") ||
                key.startsWith("file:") || key.startsWith("content:")
        }
        var freed = 0L
        for (key in leftovers) {
            freed += cache.getCachedBytes(key, 0, Long.MAX_VALUE)
            cache.removeResource(key)
        }
        Log.d("OfflineMusicStore", "Dropped ${leftovers.size} streamed leftovers, ${freed / 1024 / 1024} MB")
    }

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

    /** If [youTubeId] is already cached whole in [StreamCache], copies it straight to offline storage. */
    suspend fun copyCachedYouTube(youTubeId: String, trackId: String): String? {
        val cachedKey = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            StreamCache.cachedYouTubeKey(appContext, youTubeId)
        } ?: return null
        return copyFromStreamCache(cachedKey, trackId, StreamCache.extensionForYouTubeKey(cachedKey))
    }

    /** If [spotifyId] is already cached whole in [StreamCache], copies it straight to offline storage. */
    suspend fun copyCachedSpotify(spotifyId: String, trackId: String): String? {
        val cachedKey = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            StreamCache.cachedSpotifyKey(appContext, spotifyId)
        } ?: return null
        return copyFromStreamCache(cachedKey, trackId, StreamCache.extensionForSpotifyKey(cachedKey))
    }

    /**
     * Copies an audio track that is already completely present in [StreamCache] into the offline
     * music folder, finishing in milliseconds without hitting yt-dlp or the network.
     */
    suspend fun copyFromStreamCache(
        cacheKey: String,
        trackId: String,
        extension: String
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val finalFile = File(downloadDirectory, "track_${trackId}.$extension")
        val tempFile = File.createTempFile("track_${trackId}_", ".tmp", downloadDirectory)
        try {
            if (!StreamCache.copyCachedToFile(appContext, cacheKey, tempFile)) return@withContext null
            coroutineContext.ensureActive()
            finalFile.delete()
            if (!tempFile.renameTo(finalFile)) throw java.io.IOException("Could not save cached recording")
            Log.i("OfflineMusicStore", "Copied $cacheKey from StreamCache (${finalFile.length() / 1024} KB)")
            finalFile.absolutePath
        } catch (e: java.io.IOException) {
            Log.w("OfflineMusicStore", "Failed to copy cached track $cacheKey: ${e.message}")
            null
        } finally {
            tempFile.delete()
        }
    }

    /**
     * @param chunked fetch in bounded ranges. YouTube serves a whole file at barely more than
     *   playback speed and a ranged request at full speed, so its audio comes down in pieces.
     * @param expectedLength known byte size if available, letting [RangedDownload] launch all
     *   parallel chunk workers at t=0 without waiting for a single-threaded first chunk.
     */
    suspend fun downloadProgressive(
        url: String,
        trackId: String,
        extension: String = "mp3",
        chunked: Boolean = false,
        userAgent: String? = null,
        expectedLength: Long = -1L,
        onRejected403: (() -> Unit)? = null,
        onProgress: (Float) -> Unit = {}
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val finalFile = File(downloadDirectory, "track_${trackId}.$extension")
        val tempFile = File.createTempFile("track_${trackId}_", ".tmp", downloadDirectory)
        try {
            if (chunked) {
                val headers = if (userAgent == null) emptyMap() else mapOf("User-Agent" to userAgent)
                when (RangedDownload.toFileOutcome(url, headers, tempFile, onProgress, expectedLength)) {
                    RangedDownload.Outcome.OK -> Unit
                    RangedDownload.Outcome.REJECTED_403 -> {
                        onRejected403?.invoke()
                        return@withContext null
                    }
                    RangedDownload.Outcome.FAILED -> return@withContext null
                }
            } else {
                val request = okhttp3.Request.Builder().url(url)
                    .apply { userAgent?.let { header("User-Agent", it) } }.build()
                http.newCall(request).execute().use { response ->
                    if (response.code == 403) {
                        onRejected403?.invoke()
                        return@withContext null
                    }
                    if (response.code != 200) return@withContext null
                    val body = response.body ?: return@withContext null
                    val expected = body.contentLength()
                    var size = 0L
                    tempFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                size += count
                                if (expected > 0) onProgress((size.toFloat() / expected).coerceIn(0f, 1f))
                            }
                        }
                    }
                    if (size <= 0 || (expected >= 0 && size != expected)) {
                        throw java.io.IOException("Incomplete recording")
                    }
                }
            }
            coroutineContext.ensureActive()
            finalFile.delete()
            if (!tempFile.renameTo(finalFile)) throw java.io.IOException("Could not save recording")
            finalFile.absolutePath
        } catch (e: java.io.IOException) {
            Log.w("OfflineMusicStore", "Failed to download progressive track: ${e.javaClass.simpleName}")
            null
        } finally {
            tempFile.delete()
        }
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
            val client = http
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
            val temp = File.createTempFile("art_${trackId}_", ".tmp", artworkDirectory)
            try {
                temp.outputStream().use { out ->
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
                }
                if (scaled !== decoded) scaled.recycle()
                decoded.recycle()
                if (temp.length() <= 0L) {
                    return null
                }
                target.delete()
                if (temp.renameTo(target)) target.absolutePath else null
            } finally {
                temp.delete()
            }
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
        private const val MAX_ARTWORK_PX = 640

        @Volatile
        private var instance: OfflineMusicStore? = null

        fun getInstance(context: Context): OfflineMusicStore =
            instance ?: synchronized(this) {
                instance ?: OfflineMusicStore(context.applicationContext).also { instance = it }
            }
    }
}
