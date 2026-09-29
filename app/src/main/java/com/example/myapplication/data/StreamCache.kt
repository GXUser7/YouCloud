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
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context, slowNetworkHttp()))
        // Without FLAG_BLOCK_ON_CACHE: a piece being fetched ahead is read from the network
        // meanwhile rather than waited for.
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * HTTP with room for a slow connection: over a VPN a handshake with YouTube's video servers
     * took longer than the default eight seconds, and the track failed before it began.
     */
    fun slowNetworkHttp(userAgent: String? = null): androidx.media3.datasource.DefaultHttpDataSource.Factory =
        androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(HTTP_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(HTTP_READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
            .apply { userAgent?.let(::setUserAgent) }

    private const val HTTP_CONNECT_TIMEOUT_MS = 20_000
    private const val HTTP_READ_TIMEOUT_MS = 25_000

    /** Whether all of [key] is kept, so it plays without the network, and without a link. */
    fun isFullyCached(context: Context, key: String): Boolean {
        val cache = cache(context)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
        return length > 0 && cache.isCached(key, 0, length)
    }

    /**
     * The key a Yandex track's sound is kept under: the track *and which of its files* — Yandex
     * offers the same track as several (MP3 at 320, 192, 128 kbps, AAC), and kept under the track
     * alone, a start fetched ahead from one file and the rest read later from another were stitched
     * together: the bytes don't line up between them, so the track jumped seconds ahead where they
     * met and its length and seeking were off from there on.
     */
    fun yandexKey(trackId: String, variant: String): String = "${KEY_PREFIX}yandex:$trackId:$variant"

    /** The same for a YouTube track: the video and its format (`itag`), for the same reason. */
    fun youTubeKey(videoId: String, itag: String): String = "ytmusic:$videoId:$itag"

    /** Which of a Yandex track's files is kept whole, if one is. */
    fun cachedYandexKey(context: Context, trackId: String): String? =
        YANDEX_VARIANTS.map { yandexKey(trackId, it) }.firstOrNull { isFullyCached(context, it) }

    /** Which of a YouTube track's formats is kept whole, if one is. */
    fun cachedYouTubeKey(context: Context, videoId: String): String? =
        YOUTUBE_AUDIO_ITAGS.map { youTubeKey(videoId, it) }.firstOrNull { isFullyCached(context, it) }

    /**
     * Takes out what was kept under the track alone, before the file or format was part of the
     * key: whatever of it is stitched from two files would play broken, and nothing reads it now.
     */
    fun dropUnversionedKeys(context: Context): Int {
        val cache = cache(context)
        val stale = cache.keys.filter { key ->
            (key.startsWith("${KEY_PREFIX}yandex:") && key.removePrefix("${KEY_PREFIX}yandex:").count { it == ':' } == 0) ||
                (key.startsWith("ytmusic:") && key.removePrefix("ytmusic:").count { it == ':' } == 0)
        }
        stale.forEach(cache::removeResource)
        return stale.size
    }

    // The files Yandex offers a track as, best first; and YouTube's audio formats, Opus and AAC.
    private val YANDEX_VARIANTS = listOf("mp3-320", "mp3-192", "mp3-128", "aac-256", "aac-192", "aac-128", "aac-64")
    private val YOUTUBE_AUDIO_ITAGS = listOf("251", "140", "250", "249", "141", "139", "774", "171", "172")
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
        val source = when {
            dataSpec.uri.scheme in LOCAL_SCHEMES -> local
            // A live broadcast's pieces are heard once: kept, they only pushed tracks out.
            isLiveBroadcast(dataSpec.uri) -> local
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

    private companion object {
        val LOCAL_SCHEMES = setOf("file", "content", "asset", "android.resource", "rawresource", "data")

        fun isLiveBroadcast(uri: android.net.Uri): Boolean {
            val path = uri.path.orEmpty()
            return uri.host == "manifest.googlevideo.com" || "/yt_live_broadcast/" in path || "/live/1/" in path
        }
    }
}
