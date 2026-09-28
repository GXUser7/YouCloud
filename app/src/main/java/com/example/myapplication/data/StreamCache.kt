package com.example.myapplication.data

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Where tracks played from the internet are kept: the one playing as it plays, the next ones in
 * the queue fetched ahead (see `StreamPrefetcher`), and the last few heard, so that going back to
 * one, or hearing it again, doesn't fetch it again. The least recently heard give way once it is
 * full. Apart from the music cache, which holds downloads and is never trimmed.
 *
 * A track is kept under what it is, not where it came from: Yandex's and YouTube's links are made
 * anew each time, signed, and SoundCloud's carry a signature that changes, so keyed by the link it
 * was never found again — and never trimmed either, piling up in the downloads' cache.
 */
@UnstableApi
object StreamCache {
    private const val MAX_BYTES = 400L * 1024 * 1024

    /** Prefix of the keys the playback service gives tracks resolved to a link of their own. */
    const val KEY_PREFIX = "stream:"

    @Volatile
    private var cache: SimpleCache? = null

    fun cache(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.cacheDir, "stream"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            OfflineMusicStore.getInstance(context).databaseProvider
        ).also { cache = it }
    }

    /**
     * A key the service set (`stream:yandex:…`, `ytmusic:…`) as it is; a SoundCloud piece by its
     * address without the signature; a playlist by its whole address, since the one kept would
     * list pieces under signatures long run out.
     */
    val keyFactory = CacheKeyFactory { spec ->
        val key = spec.key
        val uri = spec.uri
        val path = uri.path.orEmpty()
        val host = uri.host.orEmpty()
        when {
            key != null && (key.startsWith(KEY_PREFIX) || key.startsWith("ytmusic:")) -> key
            (host.endsWith("sndcdn.com") || host.contains("soundcloud")) && !path.endsWith(".m3u8") ->
                KEY_PREFIX + host + path
            else -> key ?: uri.toString()
        }
    }

    /** Reads through the cache, fetching and keeping what it hasn't got. */
    fun dataSourceFactory(context: Context): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache(context))
        .setCacheKeyFactory(keyFactory)
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
        // Without FLAG_BLOCK_ON_CACHE: a piece being fetched ahead is read from the network
        // meanwhile rather than waited for.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** Whether all of [key] is kept, so it plays without the network, and without a link. */
    fun isFullyCached(context: Context, key: String): Boolean {
        val cache = cache(context)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length > 0 && cache.isCached(key, 0, length)
    }
}

/**
 * Reads what is on the phone already (a file, a `content:` URI) straight from it, and anything
 * else through [cached]: kept in a cache, a downloaded file was copied into it on every play.
 */
@UnstableApi
class LocalOrCachedDataSource(
    private val cached: DataSource,
    private val local: DataSource
) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        local.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = when (dataSpec.uri.scheme) {
            "file", "content", "asset", "android.resource", "rawresource", "data" -> local
            else -> cached
        }
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        current?.read(buffer, offset, length) ?: throw IllegalStateException("not open")

    override fun getUri() = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    class Factory(private val cached: DataSource.Factory, private val local: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource() = LocalOrCachedDataSource(cached.createDataSource(), local.createDataSource())
    }
}
