package com.example.myapplication.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import java.io.File

@UnstableApi
class OfflineMusicStore private constructor(context: Context) {
    private val databaseProvider = StandaloneDatabaseProvider(context)
    private val downloadDirectory = File(context.filesDir, "offline_music").apply { mkdirs() }
    private val cache = SimpleCache(downloadDirectory, NoOpCacheEvictor(), databaseProvider)

    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
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

    fun release() {
        cache.release()
    }

    companion object {
        @Volatile
        private var instance: OfflineMusicStore? = null

        fun getInstance(context: Context): OfflineMusicStore =
            instance ?: synchronized(this) {
                instance ?: OfflineMusicStore(context.applicationContext).also { instance = it }
            }
    }
}
