package com.example.myapplication.player.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.myapplication.player.AudioOutputs
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import org.json.JSONObject
import java.io.File
import java.util.IdentityHashMap

/**
 * Google Cast: a TV, or a speaker with Chromecast built in, picked from the track's menu plays the
 * queue in the phone's place. The session's player becomes a [CastPlayer] (wrapped by
 * [CastMirrorPlayer] so the app, notification and lock screen see full track metadata, real
 * durations and a consistent mirrored queue), and the queue moves over from where it was; the
 * session ending moves it back to the phone, where the TV left off.
 *
 * A session only suspended — the Wi-Fi gone for a moment — leaves the music with the TV, which
 * plays on: it comes back, or ends and the phone takes over, or after [SUSPENDED_MS] the phone
 * takes the queue back, paused.
 *
 * Without Google Play services (or Cast) on the phone, none of it is set up and nothing changes.
 */
@UnstableApi
internal class CastSupport(
    private val context: Context,
    private val local: ExoPlayer,
    private val session: () -> MediaSession?,
    private val server: CastStreamServer
) {
    private var cast: CastPlayer? = null
    private var mirror: CastMirrorPlayer? = null
    private var castContext: CastContext? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val tracker = CastQueueTracker()
    private var suspended = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val snapshot = object : Runnable {
        override fun run() {
            syncCastState()
            handler.postDelayed(this, 1_000)
        }
    }

    // Suspended this long, without coming back or ending: the phone takes the queue back, paused.
    private val giveUp = Runnable {
        if (suspended) {
            suspended = false
            moveTo(local, pause = true)
        }
    }

    fun start() {
        try {
            CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context))
                .addOnSuccessListener(::attach)
                .addOnFailureListener { Log.w(TAG, "No Cast on this phone", it) }
        } catch (e: Exception) {
            Log.w(TAG, "No Cast on this phone", e)
        }
    }

    private fun attach(castContext: CastContext) {
        this.castContext = castContext
        val player = CastPlayer(castContext, Converter())
        val wrapped = CastMirrorPlayer(player)
        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_POSITION_DISCONTINUITY,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED
                    )
                ) {
                    syncCastState()
                }
            }
        })
        player.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                suspended = false
                handler.removeCallbacks(giveUp)
                moveTo(wrapped)
            }

            // Told when a session ends and also when it is only suspended: which it was is known
            // once every listener has heard (see below), so it is decided a moment later.
            override fun onCastSessionUnavailable() {
                handler.post {
                    if (suspended) {
                        handler.removeCallbacks(giveUp)
                        handler.postDelayed(giveUp, SUSPENDED_MS)
                    } else {
                        moveTo(local)
                    }
                }
            }
        })
        castContext.sessionManager.addSessionManagerListener(object : SessionManagerListener<CastSession> {
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                suspended = true
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                suspended = false
                handler.removeCallbacks(giveUp)
            }

            override fun onSessionEnding(session: CastSession) {
                syncCastState()
            }

            // A suspended session that ends: the Cast player says nothing more, so it is done here.
            override fun onSessionEnded(session: CastSession, error: Int) = endedWhileSuspended()
            override fun onSessionResumeFailed(session: CastSession, error: Int) = endedWhileSuspended()

            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStarted(session: CastSession, sessionId: String) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
        }, CastSession::class.java)
        cast = player
        mirror = wrapped
        if (player.isCastSessionAvailable) moveTo(wrapped)
    }

    private fun endedWhileSuspended() {
        if (!suspended) return
        suspended = false
        handler.removeCallbacks(giveUp)
        handler.post { moveTo(local) }
    }

    private fun syncCastState() {
        val player = cast ?: return
        val rawItem = runCatching { player.currentMediaItem }.getOrNull()
        val index = player.currentMediaItemIndex
        val count = player.mediaItemCount
        val fallbackItem = if (rawItem != null && rawItem.mediaId.isBlank() && count == tracker.size()) {
            tracker.itemAt(index)
        } else {
            null
        }
        val mediaId = rawItem?.mediaId?.takeIf { it.isNotBlank() } ?: fallbackItem?.mediaId
        tracker.syncFromCast(
            castMediaId = mediaId,
            castIndex = index,
            castCount = count,
            castPositionMs = player.currentPosition,
            castPlaying = player.playWhenReady,
            castPlaybackState = player.playbackState
        )
    }

    /**
     * The queue, where it was and whether it played, carried over to [target], which then is the
     * session's — paused there when [pause].
     */
    private fun moveTo(target: Player, pause: Boolean = false) {
        val mediaSession = session() ?: return
        val from = mediaSession.player
        val toCast = target !== local
        if (toCast) {
            server.start()
            hold(true)
        }
        if (from !== target) {
            val fromCast = from !== local
            handler.removeCallbacks(snapshot)
            val items: List<MediaItem>
            val index: Int
            val position: Long
            val playing: Boolean
            if (fromCast) {
                syncCastState()
                val snap = tracker.snapshot()
                items = snap.items
                index = snap.index
                position = snap.positionMs
                playing = snap.playing
                val restoring = items.getOrNull(index)
                Log.i(
                    TAG,
                    "Cast disconnected -> localPlayer: size=${items.size}, index=$index, " +
                        "posMs=$position, playing=${playing && !pause}, " +
                        "track='${restoring?.mediaMetadata?.title}' (id=${restoring?.mediaId})"
                )
            } else {
                val localItems = (0 until from.mediaItemCount).map(from::getMediaItemAt).filter { it.localConfiguration != null }
                val localIndex = from.currentMediaItem?.let(localItems::indexOf)?.takeIf { it >= 0 } ?: 0
                position = from.currentPosition.coerceAtLeast(0L)
                playing = from.playWhenReady
                tracker.seedFromLocal(localItems, localIndex, position, playing)
                items = localItems
                index = if (localItems.isEmpty()) 0 else localIndex.coerceIn(0, localItems.lastIndex)
                Log.i(TAG, "Local -> Cast: size=${items.size}, index=$index, posMs=$position, playing=$playing")
            }
            from.stop()
            val transferTarget = if (toCast) (cast ?: target) else local
            if (items.isNotEmpty()) {
                try {
                    transferTarget.setMediaItems(items, index.coerceIn(0, items.size - 1), position)
                    transferTarget.playWhenReady = playing && !pause
                    transferTarget.prepare()
                } catch (e: Exception) {
                    // Whatever is wrong with the queue, the music stops rather than the app.
                    Log.w(TAG, "Couldn't hand the queue over", e)
                }
            }
            mediaSession.player = target
            if (toCast) handler.post(snapshot)
        }
        // What the phone was restoring isn't heard now.
        if (toCast) {
            com.example.myapplication.player.restore.RestoreState.report("main", false)
            com.example.myapplication.player.restore.RestoreState.report("helper", false)
            com.example.myapplication.player.restore.RestoreState.off(target.currentMediaItem?.mediaId)
        }
        AudioOutputs.castingTo.value = if (toCast) {
            castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName ?: "Chromecast"
        } else {
            null
        }
        if (!toCast) {
            hold(false)
            server.stop()
        }
    }

    /** Wi-Fi and the processor kept awake while the TV reads from the phone. */
    private fun hold(on: Boolean) {
        if (on) {
            if (wifiLock == null) {
                wifiLock = context.getSystemService(WifiManager::class.java)
                    ?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "YouCloud:cast")
                    ?.apply { setReferenceCounted(false); acquire() }
            }
            if (wakeLock == null) {
                wakeLock = context.getSystemService(PowerManager::class.java)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouCloud:cast")
                    ?.apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
            }
        } else {
            runCatching { wifiLock?.release() }
            runCatching { wakeLock?.release() }
            wifiLock = null
            wakeLock = null
        }
    }

    fun release() {
        handler.removeCallbacks(snapshot)
        handler.removeCallbacks(giveUp)
        cast?.setSessionAvailabilityListener(null)
        cast?.release()
        cast = null
        mirror = null
        hold(false)
        server.stop()
        AudioOutputs.castingTo.value = null
    }

    /**
     * Wraps [CastPlayer] for [MediaSession] so every queue mutation updates [tracker] reliably,
     * partial empty items are enriched from [tracker], and 10s HLS segment durations are replaced
     * by the known track duration both locally and over Binder [Timeline] updates.
     */
    private inner class CastMirrorPlayer(
        private val delegate: CastPlayer
    ) : ForwardingPlayer(delegate) {
        private val patchedListeners = IdentityHashMap<Player.Listener, Player.Listener>()

        override fun addListener(listener: Player.Listener) {
            val patched = synchronized(patchedListeners) {
                patchedListeners.getOrPut(listener) { PatchedListener(listener) }
            }
            super.addListener(patched)
        }

        override fun removeListener(listener: Player.Listener) {
            val patched = synchronized(patchedListeners) { patchedListeners.remove(listener) } ?: listener
            super.removeListener(patched)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
            tracker.onSetMediaItems(mediaItems, 0, C.TIME_UNSET)
            super.setMediaItems(mediaItems)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            val snap = tracker.snapshot()
            tracker.onSetMediaItems(
                mediaItems,
                if (resetPosition) 0 else snap.index,
                if (resetPosition) 0L else snap.positionMs
            )
            super.setMediaItems(mediaItems, resetPosition)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
            tracker.onSetMediaItems(mediaItems, startIndex, startPositionMs)
            super.setMediaItems(mediaItems, startIndex, startPositionMs)
        }

        override fun setMediaItem(mediaItem: MediaItem) {
            tracker.onSetMediaItems(listOf(mediaItem), 0, C.TIME_UNSET)
            super.setMediaItem(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
            tracker.onSetMediaItems(listOf(mediaItem), 0, startPositionMs)
            super.setMediaItem(mediaItem, startPositionMs)
        }

        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
            val snap = tracker.snapshot()
            tracker.onSetMediaItems(
                listOf(mediaItem),
                0,
                if (resetPosition) 0L else snap.positionMs
            )
            super.setMediaItem(mediaItem, resetPosition)
        }

        override fun addMediaItem(mediaItem: MediaItem) {
            tracker.onAddMediaItems(delegate.mediaItemCount, listOf(mediaItem), delegate.mediaItemCount)
            super.addMediaItem(mediaItem)
        }

        override fun addMediaItem(index: Int, mediaItem: MediaItem) {
            tracker.onAddMediaItems(index, listOf(mediaItem), delegate.mediaItemCount)
            super.addMediaItem(index, mediaItem)
        }

        override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
            tracker.onAddMediaItems(delegate.mediaItemCount, mediaItems, delegate.mediaItemCount)
            super.addMediaItems(mediaItems)
        }

        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
            tracker.onAddMediaItems(index, mediaItems, delegate.mediaItemCount)
            super.addMediaItems(index, mediaItems)
        }

        override fun removeMediaItem(index: Int) {
            tracker.onRemoveMediaItems(index, index + 1)
            super.removeMediaItem(index)
        }

        override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
            tracker.onRemoveMediaItems(fromIndex, toIndex)
            super.removeMediaItems(fromIndex, toIndex)
        }

        override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
            tracker.onMoveMediaItems(currentIndex, currentIndex + 1, newIndex)
            super.moveMediaItem(currentIndex, newIndex)
        }

        override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
            tracker.onMoveMediaItems(fromIndex, toIndex, newIndex)
            super.moveMediaItems(fromIndex, toIndex, newIndex)
        }

        override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
            tracker.onReplaceMediaItems(index, index + 1, listOf(mediaItem))
            super.replaceMediaItem(index, mediaItem)
        }

        override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {
            tracker.onReplaceMediaItems(fromIndex, toIndex, mediaItems)
            super.replaceMediaItems(fromIndex, toIndex, mediaItems)
        }

        override fun clearMediaItems() {
            tracker.onClearMediaItems()
            super.clearMediaItems()
        }

        override fun play() {
            tracker.onPlayWhenReadyChanged(true)
            super.play()
        }

        override fun pause() {
            tracker.onPlayWhenReadyChanged(false)
            super.pause()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            tracker.onPlayWhenReadyChanged(playWhenReady)
            super.setPlayWhenReady(playWhenReady)
        }

        override fun seekTo(positionMs: Long) {
            tracker.onSeek(null, positionMs)
            super.seekTo(positionMs)
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            tracker.onSeek(mediaItemIndex, positionMs)
            super.seekTo(mediaItemIndex, positionMs)
        }

        override fun seekToDefaultPosition() {
            tracker.onSeek(null, 0L)
            super.seekToDefaultPosition()
        }

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            tracker.onSeek(mediaItemIndex, 0L)
            super.seekToDefaultPosition(mediaItemIndex)
        }

        override fun getDuration(): Long = effectiveDurationMs()

        override fun getContentDuration(): Long = effectiveDurationMs()

        override fun getMediaItemAt(index: Int): MediaItem {
            val raw = super.getMediaItemAt(index)
            return patchMediaItem(raw, index) ?: raw
        }

        override fun getCurrentMediaItem(): MediaItem? {
            val raw = super.getCurrentMediaItem()
            return patchMediaItem(raw, super.getCurrentMediaItemIndex())
        }

        override fun getCurrentTimeline(): Timeline = patchTimeline(super.getCurrentTimeline())

        private fun effectiveDurationMs(): Long {
            val raw = delegate.duration
            val pos = delegate.currentPosition
            val metaMs = (patchMediaItem(delegate.currentMediaItem, delegate.currentMediaItemIndex)
                ?: tracker.currentTrackedItem())?.mediaMetadata?.durationMs
            return resolveEffectiveCastDurationMs(raw, pos, metaMs)
        }

        private fun patchMediaItem(raw: MediaItem?, index: Int): MediaItem? {
            if (raw == null) return null
            if (raw.mediaId.isNotBlank() && raw.localConfiguration != null) return raw
            return tracker.known(raw.mediaId)
                ?: if (delegate.mediaItemCount == tracker.size()) tracker.itemAt(index) else raw
        }

        private fun patchTimeline(raw: Timeline): Timeline {
            if (raw.isEmpty) return raw
            return CastPatchedTimeline(
                raw = raw,
                currentIndex = delegate.currentMediaItemIndex,
                currentPositionMs = delegate.currentPosition,
                tracker = tracker
            )
        }

        private inner class PatchedListener(
            private val downstream: Player.Listener
        ) : Player.Listener by downstream {
            override fun onEvents(player: Player, events: Player.Events) {
                downstream.onEvents(this@CastMirrorPlayer, events)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                downstream.onTimelineChanged(patchTimeline(timeline), reason)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                downstream.onMediaItemTransition(
                    patchMediaItem(mediaItem, delegate.currentMediaItemIndex),
                    reason
                )
            }
        }
    }

    private class CastPatchedTimeline(
        private val raw: Timeline,
        private val currentIndex: Int,
        private val currentPositionMs: Long,
        private val tracker: CastQueueTracker
    ) : Timeline() {
        override fun getWindowCount(): Int = raw.windowCount

        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            raw.getWindow(windowIndex, window, defaultPositionProjectionUs)
            val tracked = when {
                window.mediaItem.mediaId.isNotBlank() -> tracker.known(window.mediaItem.mediaId)
                raw.windowCount == tracker.size() -> tracker.itemAt(windowIndex)
                else -> null
            }
            val item = if (tracked != null && (window.mediaItem.mediaId.isBlank() || window.mediaItem.localConfiguration == null)) {
                tracked
            } else {
                window.mediaItem
            }
            window.mediaItem = item
            val metaDurationMs = item.mediaMetadata.durationMs?.takeIf { it > 0L }
                ?: tracked?.mediaMetadata?.durationMs?.takeIf { it > 0L }
            val rawDurationMs = if (window.durationUs == C.TIME_UNSET) C.TIME_UNSET else androidx.media3.common.util.Util.usToMs(window.durationUs)
            val posMs = if (windowIndex == currentIndex) currentPositionMs else 0L
            val effectiveMs = resolveEffectiveCastDurationMs(rawDurationMs, posMs, metaDurationMs)
            window.durationUs = if (effectiveMs == C.TIME_UNSET) C.TIME_UNSET else androidx.media3.common.util.Util.msToUs(effectiveMs)
            return window
        }

        override fun getPeriodCount(): Int = raw.periodCount

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            raw.getPeriod(periodIndex, period, setIds)
            val wIdx = period.windowIndex
            if (wIdx in 0 until windowCount) {
                val w = getWindow(wIdx, Window(), 0L)
                if (w.durationUs != C.TIME_UNSET) {
                    period.durationUs = w.durationUs
                }
            }
            return period
        }

        override fun getIndexOfPeriod(uid: Any): Int = raw.getIndexOfPeriod(uid)
        override fun getUidOfPeriod(periodIndex: Int): Any = raw.getUidOfPeriod(periodIndex)
        override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
            raw.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
        override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
            raw.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)
        override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = raw.getLastWindowIndex(shuffleModeEnabled)
        override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = raw.getFirstWindowIndex(shuffleModeEnabled)
    }

    /**
     * A queue item as the TV is handed it: the phone's address for its sound, its title, artist
     * and cover; and, kept with it, the track as the app knows it, to come back with.
     */
    private inner class Converter : MediaItemConverter {
        override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
            tracker.remember(mediaItem)
            val original = mediaItem.localConfiguration?.uri ?: mediaItem.requestMetadata.mediaUri ?: Uri.EMPTY
            val hls = isHlsCastUri(original)
            val url = if (hls) {
                server.hlsUrlFor(original) ?: original.toString()
            } else {
                server.urlFor(original) ?: original.toString()
            }
            val contentType = if (hls) "application/x-mpegurl" else contentTypeOf(original)
            val streamType = if (original.scheme == "ytlive") MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED
            val meta = mediaItem.mediaMetadata
            val durationMs = meta.durationMs?.takeIf { it > 0L }
            val cover = tvArtwork(meta.artworkUri)
            val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                meta.title?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, it.toString()) }
                meta.artist?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, it.toString()) }
                meta.albumTitle?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
                cover?.let { addImage(WebImage(Uri.parse(it))) }
            }
            val custom = JSONObject()
                .put("mediaId", mediaItem.mediaId)
                .put("uri", original.toString())
                .put("title", meta.title?.toString())
                .put("artist", meta.artist?.toString())
                .put("album", meta.albumTitle?.toString())
                .put("artwork", meta.artworkUri?.toString())
                .put("durationMs", durationMs ?: 0L)
                // What YouCloud's own screen on the TV shows (see [RECEIVER_APP_ID]).
                .put("cover", cover)
            val info = MediaInfo.Builder(url)
                .setContentUrl(url)
                .setStreamType(streamType)
                .setContentType(contentType)
                .apply {
                    if (durationMs != null && streamType == MediaInfo.STREAM_TYPE_BUFFERED) {
                        setStreamDuration(durationMs)
                    }
                }
                .setMetadata(castMeta)
                .setCustomData(custom)
                .build()
            return MediaQueueItem.Builder(info).setCustomData(custom).build()
        }

        override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
            val custom = mediaQueueItem.customData ?: mediaQueueItem.media?.customData ?: JSONObject()
            val id = custom.optString("mediaId")
            // A track the phone handed over: as it plays it.
            tracker.known(id)?.let { return it }
            val metadata = MediaMetadata.Builder()
                .setTitle(custom.optString("title").takeIf { it.isNotEmpty() && it != "null" })
                .setArtist(custom.optString("artist").takeIf { it.isNotEmpty() && it != "null" })
                .setAlbumTitle(custom.optString("album").takeIf { it.isNotEmpty() && it != "null" })
                .setArtworkUri(custom.optString("artwork").takeIf { it.isNotEmpty() && it != "null" }?.let(Uri::parse))
                .apply {
                    custom.optLong("durationMs", 0L).takeIf { it > 0L }?.let(::setDurationMs)
                }
                .build()
            return MediaItem.Builder()
                .setMediaId(id)
                .setUri(custom.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse))
                .setMediaMetadata(metadata)
                .build()
        }
    }

    /**
     * The cover as a TV fetches it: proxied through [CastStreamServer] (from disk or via the phone's
     * own network/VPN), in a high-res size for a big screen with [raw] as fallback if a size misses.
     */
    private fun tvArtwork(artwork: Uri?): String? {
        val raw = artwork?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val sized = com.example.myapplication.ui.artworkUrlForSize(raw, androidx.compose.ui.unit.Dp(TV_COVER_DP)) ?: raw
        val primary = if (sized.startsWith("/")) Uri.fromFile(File(sized)) else Uri.parse(sized)
        val fallback = raw.takeIf { it != sized && (it.startsWith("https://") || it.startsWith("http://")) }?.let(Uri::parse)
        return server.imageUrlFor(primary, fallback)
            ?: sized.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }

    private fun contentTypeOf(uri: Uri): String = when (uri.scheme) {
        "ytmusic" -> "audio/mp4"
        "spotify" -> {
            val id = uri.lastPathSegment
            id?.let(com.example.myapplication.data.SpotifyAudio::cachedMimeType)
                ?: id?.let { com.example.myapplication.data.StreamCache.cachedSpotifyKey(context, it) }
                    ?.let(com.example.myapplication.data.StreamCache::mimeTypeForSpotifyKey)
                ?: "audio/mp4"
        }
        "file" -> when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "m4a", "mp4", "aac" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "webm" -> "audio/webm"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }
        else -> "audio/mpeg"
    }

    private companion object {
        const val TAG = "CastSupport"
        const val SUSPENDED_MS = 45_000L
        // A cover asked for as for a 320 dp tile: 500–1000 px, what the services have.
        const val TV_COVER_DP = 320f
    }
}

/**
 * YouCloud's own screen on the TV — the cover large, the player's colours taken from it, the title
 * and the time as the app sets them (web/cast.html, served by the site at /cast.html) — once it is
 * registered in the Google Cast SDK Developer Console under that address: its application id goes
 * here. Empty, the TV shows Google's own player: the cover and the title, but not the app's look.
 */
internal const val RECEIVER_APP_ID = ""

/** What the Cast framework asks the app for: the receiver, YouCloud's screen or Google's own player on the TV. */
class CastOptionsProvider : com.google.android.gms.cast.framework.OptionsProvider {
    override fun getCastOptions(context: Context): com.google.android.gms.cast.framework.CastOptions =
        com.google.android.gms.cast.framework.CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID.ifEmpty { com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID })
            .setStopReceiverApplicationWhenEndingSession(true)
            // The app's own media session and notification go on: the framework's would be a second.
            .setCastMediaOptions(
                com.google.android.gms.cast.framework.media.CastMediaOptions.Builder()
                    .setNotificationOptions(null)
                    .setMediaSessionEnabled(false)
                    .build()
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<com.google.android.gms.cast.framework.SessionProvider>? = null
}
