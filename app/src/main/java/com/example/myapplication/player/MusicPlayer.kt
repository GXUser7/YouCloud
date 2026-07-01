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
import kotlinx.coroutines.flow.asStateFlow
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
    val currentTrackId = _currentTrackId.asStateFlow()

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

    init {
        controllerFuture.addListener(
            {
                controller = runCatching { controllerFuture.get() }.getOrNull()
                controller?.addListener(playerListener)
                syncState()
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
    }

    fun playQueue(tracks: List<QueueTrack>, startIndex: Int) {
        if (tracks.isEmpty()) return

        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val player = controller
        if (player == null) {
            pendingQueueRequest = QueueRequest(tracks, safeIndex)
            return
        }

        val items = tracks.map(::toMediaItem)
        player.setMediaItems(items, safeIndex, 0L)
        player.prepare()
        player.play()
        syncState()
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

    fun togglePlayPause() {
        controller?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _positionMs.value = positionMs
    }

    fun skipNext() {
        controller?.let { player ->
            player.seekToNextMediaItem()
            player.play()
        }
    }

    fun skipPrevious() {
        controller?.let { player ->
            player.seekToPreviousMediaItem()
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

    fun moveMediaItem(fromIndex: Int, toIndex: Int) {
        controller?.moveMediaItem(fromIndex, toIndex)
    }

    fun getNextMediaItemIndex(): Int {
        return controller?.nextMediaItemIndex ?: -1
    }

    fun release() {
        scope.cancel()
        controller?.removeListener(playerListener)
        controller = null
        MediaController.releaseFuture(controllerFuture)
    }

    private fun playQueue(request: QueueRequest) {
        playQueue(request.tracks, request.startIndex)
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
                    }
                    .build()
            )
        
        if (track.url.startsWith("soundcloud://") || track.url.contains(".m3u8") || track.url.contains("m3u8")) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }
        return builder.build()
    }

    private fun syncState() {
        val player = controller
        if (player == null || player.mediaItemCount == 0) {
            _currentTrack.value = null
            _currentTrackId.value = null
            return
        }
        _isPlaying.value = player.isPlaying
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        _durationMs.value = player.duration.coerceAtLeast(0L)
        _currentTrack.value =
            player.currentMediaItem?.mediaMetadata?.title?.toString()
        _currentTrackId.value = player.currentMediaItem?.mediaId?.toLongOrNull()
        _repeatMode.value = player.repeatMode
        _isBuffering.value = player.playbackState == Player.STATE_BUFFERING
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncState()
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
        val artworkUrl: String?
    )

    private data class QueueRequest(
        val tracks: List<QueueTrack>,
        val startIndex: Int
    )
}
