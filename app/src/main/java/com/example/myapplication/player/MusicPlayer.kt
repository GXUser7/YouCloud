package com.example.myapplication.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(
            appContext,
            SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        ).buildAsync()

    private var controller: MediaController? = null
    private var pendingQueueRequest: QueueRequest? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentTrack = MutableStateFlow<String?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _currentTrackId = MutableStateFlow<Long?>(null)

    /** The track the player is on. Listening together, the radio and the history go by it. */
    val currentTrackId = _currentTrackId.asStateFlow()

    /**
     * The track shown as playing: [currentTrackId], or, while a crossfade brings the next one in,
     * that one — heard from then, and shown from then. [positionMs], [durationMs] and
     * [livePositionMs] are the shown track's.
     */
    val shownTrackId: StateFlow<Long?> =
        combine(_currentTrackId, SessionBridge.crossfadeIncoming) { id, incoming -> incoming?.mediaId?.toLongOrNull() ?: id }
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val _positionMs = MutableStateFlow(0L)
    val positionMs = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled = _shuffleEnabled.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering = _isBuffering.asStateFlow()

    // Played to the end of the queue and stopped there: a radio that ran short finds more then.
    private val _ended = MutableStateFlow(false)
    val ended = _ended.asStateFlow()

    /** Set once the controller is connected, so [queueSnapshot] tells the truth. */
    private val _connected = MutableStateFlow(false)
    val connected = _connected.asStateFlow()

    /** The track that failed to play, by its id, until it plays or another comes. */
    private val _failedTrackId = MutableStateFlow<Long?>(null)
    val failedTrackId = _failedTrackId.asStateFlow()

    // Whether the track playing came by itself — the one before ended — rather than being picked.
    private var lastChangeByItself = false

    init {
        controllerFuture.addListener(
            {
                controller = runCatching { controllerFuture.get() }.getOrNull()
                controller?.addListener(playerListener)
                syncState()
                _connected.value = controller != null
                pendingQueueRequest?.let(::playQueue)
                pendingQueueRequest = null
            },
            ContextCompat.getMainExecutor(appContext)
        )

        scope.launch {
            while (true) {
                if (controller?.isPlaying == true) {
                    syncState()
                }
                delay(500)
            }
        }
        // The end of a crossfade: shown playing throughout, whatever the main player does unheard.
        scope.launch { SessionBridge.crossfadeSettling.collect { syncState() } }
        // A crossfade's next track coming in, or taken over: its position and length shown.
        scope.launch { SessionBridge.crossfadeIncoming.collect { syncState() } }
    }

    /** A crossfade's next track coming in, while the player is still on the track before it. */
    private fun incoming(): SessionBridge.CrossfadeIncoming? {
        val incoming = SessionBridge.crossfadeIncoming.value ?: return null
        return incoming.takeIf { controller?.currentMediaItem?.mediaId != it.mediaId }
    }

    /** Where the shown track is in the queue: the incoming one's place while a crossfade brings it in. */
    private fun shownIndex(player: Player): Int =
        if (incoming() != null && player.nextMediaItemIndex != androidx.media3.common.C.INDEX_UNSET) player.nextMediaItemIndex else player.currentMediaItemIndex

    /** Playing, as the listener hears it: a crossfade settling plays on through the other player. */
    private fun heardPlaying(player: Player) =
        player.isPlaying || (SessionBridge.crossfadeSettling.value && player.playWhenReady)

    private fun heardLoading(player: Player) =
        player.playbackState == Player.STATE_BUFFERING && !SessionBridge.crossfadeSettling.value

    /** [tracks] from [startIndex], from [startPositionMs] into it; paused there unless [playWhenReady]. */
    fun playQueue(tracks: List<QueueTrack>, startIndex: Int, startPositionMs: Long = 0L, playWhenReady: Boolean = true) {
        if (tracks.isEmpty()) return

        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val player = controller
        if (player == null) {
            pendingQueueRequest = QueueRequest(tracks, safeIndex, startPositionMs, playWhenReady)
            return
        }

        val items = tracks.map(::toMediaItem)
        player.setMediaItems(items, safeIndex, startPositionMs.coerceAtLeast(0L))
        player.prepare()
        if (playWhenReady) player.play() else player.pause()
        syncState()
    }

    /** Playing or paused, as said, rather than the other way round from where it is. */
    fun setPlaying(playing: Boolean) {
        controller?.let { player -> if (playing) player.play() else player.pause() }
    }

    /**
     * The speed, its pitch kept: listening together nudges it a few percent to catch up with the
     * host or let it catch up, too slightly to be heard.
     */
    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
    }

    /**
     * Rearranges the queue around whatever is playing right now, leaving the item under the
     * playhead untouched. [playQueue] cannot do this: `setMediaItems` replaces the whole timeline,
     * so the current track restarts from zero and the listener hears a gap even if the caller
     * seeks back afterwards. Here only the items before and after the current one are swapped out,
     * which ExoPlayer handles without interrupting decoding.
     *
     * Returns false when the reorder cannot be done safely — no controller yet, an empty timeline,
     * or a new queue whose [newIndex] is not the track currently playing. The caller is expected
     * to fall back to [playQueue] in that case.
     */
    fun reorderQueueKeepingCurrent(tracks: List<QueueTrack>, newIndex: Int): Boolean {
        if (tracks.isEmpty()) return false
        val player = controller ?: return false
        val count = player.mediaItemCount
        if (count == 0) return false
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex !in 0 until count) return false

        val safeIndex = newIndex.coerceIn(0, tracks.lastIndex)
        // Bail out unless the caller really is keeping the playing track in place; otherwise the
        // item we preserve would not be the one it thinks sits at safeIndex.
        val playingId = player.currentMediaItem?.mediaId ?: return false
        if (tracks[safeIndex].id.toString() != playingId) return false

        // Strip the timeline down to just the playing item, then rebuild around it. Order matters:
        // trailing items go first so the leading removal does not shift the indices we still need.
        if (currentIndex + 1 < count) player.removeMediaItems(currentIndex + 1, count)
        if (currentIndex > 0) player.removeMediaItems(0, currentIndex)

        val before = tracks.subList(0, safeIndex).map(::toMediaItem)
        val after = tracks.subList(safeIndex + 1, tracks.size).map(::toMediaItem)
        if (before.isNotEmpty()) player.addMediaItems(0, before)
        if (after.isNotEmpty()) player.addMediaItems(player.mediaItemCount, after)

        syncState()
        return true
    }

    fun updateQueue(tracks: List<QueueTrack>) {
        val player = controller ?: return
        
        tracks.forEachIndexed { index, track ->
            if (index < player.mediaItemCount) {
                val oldItem = player.getMediaItemAt(index)
                val newUri = track.url
                if (newUri.isNotEmpty()) {
                    val oldUriStr = oldItem.localConfiguration?.uri?.toString() ?: ""
                    val normOld = if (oldUriStr.startsWith("file://")) oldUriStr.substring(7) else oldUriStr
                    val normNew = if (newUri.startsWith("file://")) newUri.substring(7) else newUri
                    if (normOld != normNew) {
                        val newItem = toMediaItem(track)
                        player.replaceMediaItem(index, newItem)
                    }
                }
            } else {
                player.addMediaItem(toMediaItem(track))
            }
        }
        // Remove excess tail items if new list is shorter than old queue
        while (player.mediaItemCount > tracks.size) {
            player.removeMediaItem(player.mediaItemCount - 1)
        }
    }

    /**
     * Keeps the playing track and puts [next] after it, in place of whatever came after it (and
     * before it): a guest listening together has the host's next tracks queued, so they load
     * ahead and follow on by themselves. What is the same already stays, loaded as it is.
     */
    fun replaceUpcoming(next: List<QueueTrack>): Boolean {
        val player = controller ?: return false
        if (player.mediaItemCount == 0) return false
        val current = player.currentMediaItemIndex
        if (current > 0) player.removeMediaItems(0, current)
        val queued = (1 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val wanted = next.map { it.id.toString() }
        val same = queued.zip(wanted).takeWhile { (a, b) -> a == b }.size
        if (same == queued.size && same == wanted.size) return true
        if (1 + same < player.mediaItemCount) player.removeMediaItems(1 + same, player.mediaItemCount)
        val added = next.drop(same).map(::toMediaItem)
        if (added.isNotEmpty()) player.addMediaItems(added)
        return true
    }

    /** The ids of the next [count] tracks, as the player will come to them. */
    fun upcomingIds(count: Int): List<Long> {
        val player = controller ?: return emptyList()
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        // Repeating one track, "next" is still the next one.
        val repeat = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
        val ids = ArrayList<Long>(count)
        var index = player.currentMediaItemIndex
        repeat(count) {
            index = timeline.getNextWindowIndex(index, repeat, player.shuffleModeEnabled)
            if (index == androidx.media3.common.C.INDEX_UNSET || index == player.currentMediaItemIndex) return ids
            player.getMediaItemAt(index).mediaId.toLongOrNull()?.let(ids::add)
        }
        return ids
    }

    /** Whether [trackId] is the track loaded, ready to sound the moment it is played. */
    fun isReadyFor(trackId: Long): Boolean {
        val player = controller ?: return false
        return player.currentMediaItem?.mediaId == trackId.toString() && player.playbackState == Player.STATE_READY
    }

    /** Whether it is to play, loaded or not yet: what play and pause set. */
    fun wantsToPlay(): Boolean = controller?.playWhenReady == true

    /** Whether the track playing came on by itself, the one before having ended; not picked. */
    fun changedByItself(): Boolean = lastChangeByItself

    fun togglePlayPause() {
        controller?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    /** To [positionMs] in the shown track: the one coming in, while a crossfade brings it in. */
    fun seekTo(positionMs: Long) {
        val player = controller ?: return
        if (incoming() != null) player.seekTo(shownIndex(player), positionMs) else player.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    /** To [positionMs] in the track the player is on, whatever is shown: listening together keeps to it. */
    fun seekPlayerTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    /**
     * Where the shown track is right now, to the millisecond: [positionMs] is only sampled twice a
     * second, too coarse to keep a music video's lips on the words. Main thread only.
     */
    fun livePositionMs(): Long =
        SessionBridge.crossfadeIncoming.value?.position?.invoke()
            ?: controller?.currentPosition?.coerceAtLeast(0L)
            ?: _positionMs.value

    /** Where the player is in its own track, whatever is shown. Main thread only. */
    fun playerPositionMs(): Long = controller?.currentPosition?.coerceAtLeast(0L) ?: _positionMs.value

    fun skipNext() {
        controller?.let { player ->
            if (!seekPastIncoming(player, next = true)) player.seekToNextMediaItem()
            player.play()
        }
    }

    /**
     * While a crossfade brings the next track in, "next" and "previous" go from it, as shown: on to
     * the track after it, or back to its start, or (in its first seconds) to the track before. False
     * when no crossfade is under way.
     */
    private fun seekPastIncoming(player: Player, next: Boolean): Boolean {
        if (incoming() == null) return false
        val shown = shownIndex(player)
        if (next) {
            val repeat = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
            val after = player.currentTimeline.getNextWindowIndex(shown, repeat, player.shuffleModeEnabled)
            if (after != androidx.media3.common.C.INDEX_UNSET) player.seekTo(after, 0L) else player.seekTo(shown, 0L)
        } else {
            if (livePositionMs() > 5_000) player.seekTo(shown, 0L) else player.seekTo(player.currentMediaItemIndex, 0L)
        }
        return true
    }

    /**
     * Back to the start of the track once it has played a few seconds, else to the track before,
     * as every player does. How many seconds is the service's player's to say (5, there), so the
     * notification's and the car's buttons behave the same.
     */
    fun skipPrevious() {
        controller?.let { player ->
            if (!seekPastIncoming(player, next = false)) player.seekToPrevious()
            player.play()
        }
    }

    /** Whether there is a track to move to that way: the queue's next, or the one before. */
    fun hasNeighbourTrack(next: Boolean): Boolean {
        val player = controller ?: return false
        return if (next) player.hasNextMediaItem() else player.hasPreviousMediaItem()
    }

    /**
     * To the next track or the one before — the track before however far into this one: swiping
     * the cover back means the track, not its start.
     */
    fun skipToNeighbourTrack(next: Boolean) {
        controller?.let { player ->
            if (!seekPastIncoming(player, next)) {
                if (next) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
            }
            player.play()
        }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun toggleShuffle() {
        val enabled = !_shuffleEnabled.value
        _shuffleEnabled.value = enabled
    }

    /**
     * Plays the queued track [trackId] from its start, where it stands in the queue: the queue
     * itself is left as it is, as a skip leaves it. False when the track isn't queued.
     */
    fun playQueuedItem(trackId: Long): Boolean {
        val player = controller ?: return false
        val id = trackId.toString()
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id } ?: return false
        player.seekTo(index, 0L)
        player.play()
        return true
    }

    fun moveMediaItem(fromIndex: Int, toIndex: Int) {
        controller?.moveMediaItem(fromIndex, toIndex)
    }

    fun getNextMediaItemIndex(): Int {
        return controller?.nextMediaItemIndex ?: -1
    }

    /**
     * The playback service's queue as it stands. It outlives the screen: reopened while music
     * plays, the app finds its queue here rather than starting from nothing.
     */
    fun queueSnapshot(): List<QueueItem> {
        val player = controller ?: return emptyList()
        return (0 until player.mediaItemCount).mapNotNull { index ->
            val item = player.getMediaItemAt(index)
            val id = item.mediaId.toLongOrNull() ?: return@mapNotNull null
            QueueItem(
                id = id,
                uri = item.localConfiguration?.uri?.toString(),
                title = item.mediaMetadata.title?.toString(),
                artist = item.mediaMetadata.artist?.toString(),
                artworkUrl = item.mediaMetadata.artworkUri?.toString()
            )
        }
    }

    fun release() {
        scope.cancel()
        controller?.removeListener(playerListener)
        controller = null
        MediaController.releaseFuture(controllerFuture)
    }

    private fun playQueue(request: QueueRequest) {
        playQueue(request.tracks, request.startIndex, request.startPositionMs, request.playWhenReady)
    }

    private fun toMediaItem(track: QueueTrack): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(track.url)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .apply {
                        track.artworkUrl?.let { setArtworkUri(Uri.parse(it)) }
                        if (track.leadArtist != null || track.source != null) {
                            setExtras(android.os.Bundle().apply {
                                track.leadArtist?.let { putString(PlayLog.EXTRA_LEAD_ARTIST, it) }
                                track.source?.let { putString(PlayLog.EXTRA_SOURCE, it) }
                            })
                        }
                    }
                    .build()
            )
        
        if (track.url.startsWith("soundcloud://") || track.url.startsWith("ytlive://") || track.url.contains("m3u8")) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    private fun syncState() {
        val player = controller
        if (player == null || player.mediaItemCount == 0) {
            _currentTrack.value = null
            _currentTrackId.value = null
            _ended.value = false
            return
        }
        _isPlaying.value = heardPlaying(player)
        val incoming = SessionBridge.crossfadeIncoming.value
        _positionMs.value = incoming?.position?.invoke() ?: player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = incoming?.durationMs ?: player.duration.coerceAtLeast(0L)
        _currentTrack.value =
            player.currentMediaItem?.mediaMetadata?.title?.toString()
        _currentTrackId.value = player.currentMediaItem?.mediaId?.toLongOrNull()
        _repeatMode.value = player.repeatMode
        _isBuffering.value = heardLoading(player)
        _ended.value = player.playbackState == Player.STATE_ENDED
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = controller?.let(::heardPlaying) ?: isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) _failedTrackId.value = null
            syncState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            lastChangeByItself = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            _failedTrackId.value = null
            syncState()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _failedTrackId.value = controller?.currentMediaItem?.mediaId?.toLongOrNull()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = repeatMode
        }
    }

    data class QueueTrack(
        val id: Long,
        val url: String,
        val title: String,
        val artist: String,
        val artworkUrl: String?,
        // For "Итоги" (see PlayLog): the first artist alone, and the track's service.
        val leadArtist: String? = null,
        val source: String? = null
    )

    data class QueueItem(
        val id: Long,
        val uri: String?,
        val title: String?,
        val artist: String?,
        val artworkUrl: String?
    )

    private data class QueueRequest(
        val tracks: List<QueueTrack>,
        val startIndex: Int,
        val startPositionMs: Long = 0L,
        val playWhenReady: Boolean = true
    )
}
