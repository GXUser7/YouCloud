package com.example.myapplication.player

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import com.example.myapplication.data.StreamCache
import java.util.concurrent.Executors

/**
 * Fetches the next [AHEAD] tracks of the queue whole into the [StreamCache] while one plays, in the
 * order they will come (shuffled, if the queue is): skipped to, or reached, the next one starts at
 * once, from the phone, instead of starting to load then — on a slow connection a mix's next track
 * often hadn't loaded by the time it came. The tracks are found as playback finds them, through
 * [resolver], so they are kept under the same keys it reads.
 *
 * One at a time, at low priority; when what comes next changes, the fetch under way stops (what it
 * got is kept) and the new ones are fetched instead.
 */
@UnstableApi
internal class StreamPrefetcher(
    private val context: Context,
    private val player: Player,
    private val resolver: ResolvingDataSource.Resolver
) : Player.Listener {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "StreamPrefetch").apply { priority = Thread.MIN_PRIORITY }
    }
    private val cacheFactory = StreamCache.dataSourceFactory(context)

    // What is being fetched, by media id; each new plan bumps [generation], which the fetches
    // under way check.
    private var planned: List<String> = emptyList()
    @Volatile
    private var generation = 0
    @Volatile
    private var cancelRunning: (() -> Unit)? = null

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = plan()

    override fun onTimelineChanged(timeline: Timeline, reason: Int) = plan()

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = plan()

    override fun onRepeatModeChanged(repeatMode: Int) = plan()

    fun release() {
        generation++
        cancelRunning?.invoke()
        executor.shutdownNow()
    }

    private fun plan() {
        val upcoming = upcoming()
        val ids = upcoming.map { it.mediaId }
        if (ids == planned) return
        planned = ids
        val plan = ++generation
        cancelRunning?.invoke()
        if (upcoming.isEmpty()) return
        executor.execute {
            for (item in upcoming) {
                if (plan != generation) return@execute
                fetch(item, plan)
            }
        }
    }

    /** The next [AHEAD] items, as the player will come to them. */
    private fun upcoming(): List<MediaItem> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        // Repeating one track, "next" is still the next one when it is skipped to.
        val repeat = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
        val items = ArrayList<MediaItem>(AHEAD)
        var index = player.currentMediaItemIndex
        repeat(AHEAD) {
            index = timeline.getNextWindowIndex(index, repeat, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET || index == player.currentMediaItemIndex) return items
            items += player.getMediaItemAt(index)
        }
        return items
    }

    private fun fetch(item: MediaItem, plan: Int) {
        val configuration = item.localConfiguration ?: return
        if (configuration.uri.scheme in LOCAL_SCHEMES) return
        val started = System.currentTimeMillis()
        try {
            val spec = resolver.resolveDataSpec(DataSpec(configuration.uri))
            // On the phone already, or kept whole (the resolver then leaves its own address).
            if (spec.uri.scheme != "http" && spec.uri.scheme != "https") return
            if (plan != generation) return
            val hls = configuration.mimeType == MimeTypes.APPLICATION_M3U8 ||
                spec.uri.path.orEmpty().endsWith(".m3u8")
            if (hls) {
                val downloader = HlsDownloader(MediaItem.fromUri(spec.uri), cacheFactory)
                cancelRunning = downloader::cancel
                if (plan != generation) return
                downloader.download(null)
            } else {
                val writer = CacheWriter(cacheFactory.createDataSource(), spec, null, null)
                cancelRunning = writer::cancel
                if (plan != generation) return
                writer.cache()
            }
            Log.d(TAG, "Fetched ahead ${item.mediaMetadata.title} in ${System.currentTimeMillis() - started} ms")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            // Cancelled for a new plan, or the network: playback fetches it itself then.
            if (plan == generation) Log.w(TAG, "Couldn't fetch ahead ${item.mediaMetadata.title}: $e")
        } finally {
            cancelRunning = null
        }
    }

    private companion object {
        const val TAG = "StreamPrefetcher"
        const val AHEAD = 2
        // On the phone already, or a broadcast: nothing to fetch ahead.
        val LOCAL_SCHEMES = setOf("file", "content", "asset", "android.resource", "rawresource", "data", "ytlive")
    }
}
