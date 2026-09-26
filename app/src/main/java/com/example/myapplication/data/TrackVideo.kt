package com.example.myapplication.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * One stretch of a music video that lines up with its song: from [trackStartMs], for
 * [durationMs], the video is at [videoStartMs] onwards.
 */
data class VideoSegment(val trackStartMs: Long, val videoStartMs: Long, val durationMs: Long)

/**
 * Moving picture for the player. A music video plays in the cover's place, in step with the
 * track; a looping one ([loop] — Yandex's "videoshots") is a backdrop that keeps its own time.
 */
data class TrackVideo(
    val trackId: Long,
    val url: String,
    val loop: Boolean,
    // Whether the picture is taller than wide, when known before it plays.
    val vertical: Boolean? = null,
    val userAgent: String? = null,
    // Empty: the video runs alongside the track one to one.
    val segments: List<VideoSegment> = emptyList(),
    // False while it is still being lined up: the player buffers it, in step as far as is known,
    // but doesn't show it yet.
    val ready: Boolean = true,
    // A YouTube video's codec, as YouTube names it; see [OfflineVideoStore].
    val codec: String? = null
) {
    /** Where the video should be while the track is at [trackMs]. */
    fun videoPositionFor(trackMs: Long): Long {
        if (segments.isEmpty()) return trackMs
        val segment = segments.lastOrNull { trackMs >= it.trackStartMs } ?: segments.first()
        return (segment.videoStartMs + (trackMs - segment.trackStartMs)).coerceAtLeast(0L)
    }
}

/**
 * Where videos are kept while they play, apart from the music cache: that one holds downloads and
 * is never trimmed, while a video is only worth keeping until it has looped or been replayed.
 */
@UnstableApi
object VideoCache {
    private const val MAX_BYTES = 200L * 1024 * 1024
    private const val CHUNK_BYTES = 2L * 1024 * 1024

    @Volatile
    private var cache: SimpleCache? = null

    fun dataSourceFactory(context: Context, userAgent: String?): CacheDataSource.Factory {
        val upstream = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .apply { userAgent?.let(::setUserAgent) }
        return CacheDataSource.Factory()
            .setCache(cache(context))
            .setUpstreamDataSourceFactory(ChunkedDataSource.Factory(upstream, CHUNK_BYTES))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "video"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            OfflineMusicStore.getInstance(context).databaseProvider
        ).also { cache = it }
    }
}
