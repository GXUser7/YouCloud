package com.example.myapplication.ui

import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.myapplication.data.TrackVideo
import com.example.myapplication.data.VideoCache
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "PlayerVideo"

// A music video follows the track: drift is made up by running a little fast or slow, in
// proportion to it; only a large one is seeked away, which stalls the picture for a moment.
// Lips out of step with words show from about 50 ms.
private const val IN_STEP_MS = 30L
private const val SPEED_PER_MS = 1f / 800f
private const val MAX_SPEED_CHANGE = 0.25f
private const val SEEK_DRIFT_MS = 1_000L
private const val SEEK_COOLDOWN_MS = 2_000L
private const val SEEK_LEAD_MS = 200L
private const val SYNC_INTERVAL_MS = 100L

/** A muted player for [video], and what the screen needs to know about its picture. */
@Stable
class PlayerVideoState internal constructor(video: TrackVideo, internal val player: ExoPlayer) {
    /**
     * The video as last known: the same stream, first while it is being lined up with the track,
     * then with its map. Swapped in place, so the player keeps what it has buffered.
     */
    var video by mutableStateOf(video)
        internal set

    /** The picture's size, once the decoder knows it. */
    var size by mutableStateOf(IntSize.Zero)
        internal set

    // A frame is up, and the video hasn't run out or failed.
    internal var rendering by mutableStateOf(false)

    /** On screen: rendering, and lined up with the track. */
    val showing: Boolean
        get() = rendering && video.ready

    internal var firstFrame = false

    // The picture as drawn, for the glass to draw again; and where on screen it is drawn.
    internal var frameLayer: GraphicsLayer? = null
    internal var frameOrigin: Offset? by mutableStateOf(null)

    /**
     * Taller than wide: Yandex's videoshots. Played behind the whole player rather than in the
     * cover's place. Before the size is known, the video's own hint decides.
     */
    val isPortrait: Boolean
        get() = if (size == IntSize.Zero) video.vertical == true else size.height > size.width
}

/**
 * Plays [video] without sound, alongside the track: a music video keeps to the track's position
 * (through its segment map, when YouTube gave one), a loop just runs while the track plays. Stops
 * while the app is in the background.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerVideoState(video: TrackVideo?, isPlaying: Boolean, trackPosition: () -> Long): PlayerVideoState? {
    if (video == null) return null
    val context = LocalContext.current
    val state = remember(video.url) {
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(VideoCache.dataSourceFactory(context, video.userAgent)))
            // Off again after a second rather than the default two and a half: it is a picture,
            // and the sync loop makes up for a stall.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 30_000, 1_000, 2_000)
                    .build()
            )
            .build()
        player.volume = 0f
        // A clip may carry its own sound; the track is what's heard.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .build()
        player.repeatMode = if (video.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.setMediaItem(MediaItem.fromUri(video.url))
        if (!video.loop) player.seekTo(video.videoPositionFor(trackPosition()))
        player.prepare()
        PlayerVideoState(video, player)
    }
    state.video = video

    state.frameLayer = rememberGraphicsLayer()

    DisposableEffect(state) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    state.size = IntSize((videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt(), videoSize.height)
                }
            }

            override fun onRenderedFirstFrame() {
                state.firstFrame = true
                state.rendering = true
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state.rendering = playbackState != Player.STATE_ENDED && state.firstFrame
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "${state.video.url}: ${error.errorCodeName}", error)
                state.rendering = false
            }
        }
        state.player.addListener(listener)
        onDispose {
            state.player.removeListener(listener)
            state.player.release()
        }
    }

    var foreground by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> foreground = true
                Lifecycle.Event.ON_STOP -> foreground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val playing by rememberUpdatedState(isPlaying && foreground)
    val position by rememberUpdatedState(trackPosition)

    LaunchedEffect(state) {
        val player = state.player
        var lastSeekAt = 0L
        var lastLogAt = 0L
        var speed = 1f
        while (isActive) {
            player.playWhenReady = playing
            if (!state.video.loop) {
                val now = SystemClock.elapsedRealtime()
                val target = state.video.videoPositionFor(position())
                val drift = target - player.currentPosition
                val wanted = when {
                    abs(drift) > SEEK_DRIFT_MS && now - lastSeekAt > SEEK_COOLDOWN_MS -> {
                        player.seekTo(target + if (playing) SEEK_LEAD_MS else 0L)
                        lastSeekAt = now
                        1f
                    }
                    playing && player.playbackState == Player.STATE_READY && abs(drift) > IN_STEP_MS ->
                        1f + (drift * SPEED_PER_MS).coerceIn(-MAX_SPEED_CHANGE, MAX_SPEED_CHANGE)
                    else -> 1f
                }
                if (wanted != speed) {
                    speed = wanted
                    player.setPlaybackSpeed(wanted)
                }
                if (now - lastLogAt > 10_000L && playing) {
                    lastLogAt = now
                    Log.d(TAG, "drift ${drift} ms, speed $speed")
                }
            }
            delay(SYNC_INTERVAL_MS)
        }
    }
    return state
}

/**
 * The video's picture, cropped to fill [modifier]'s bounds as a cover is. It is drawn through
 * the state's layer, which [FrostedVideoGlass] draws a second time, blurred.
 */
@Composable
fun VideoSurface(state: PlayerVideoState, modifier: Modifier = Modifier) {
    val layer = state.frameLayer
    BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { state.frameOrigin = it.positionInRoot() }
            .drawWithContent {
                if (layer == null) {
                    drawContent()
                } else {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val size = state.size
        val density = LocalDensity.current
        val viewModifier = if (size == IntSize.Zero || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            Modifier.fillMaxSize()
        } else {
            val scale = max(constraints.maxWidth.toFloat() / size.width, constraints.maxHeight.toFloat() / size.height)
            with(density) { Modifier.requiredSize((size.width * scale).toDp(), (size.height * scale).toDp()) }
        }
        AndroidView(
            factory = { context -> TextureView(context).also(state.player::setVideoTextureView) },
            onRelease = state.player::clearVideoTextureView,
            modifier = viewModifier
        )
    }
}

/**
 * Frosted glass: the part of the video behind this box, blurred, under a wash of [tint]. What a
 * panel stands on over a vertical video, in place of its solid colour.
 *
 * The video is drawn here a second time from the same layer, not copied: the glass moves with
 * every frame, and nothing is read back from the GPU.
 */
@Composable
fun BoxScope.FrostedVideoGlass(state: PlayerVideoState, tint: Color) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .graphicsLayer {
                val radius = 32.dp.toPx()
                renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                clip = true
            }
            .drawBehind {
                val layer = state.frameLayer ?: return@drawBehind
                val at = state.frameOrigin ?: return@drawBehind
                translate(at.x - origin.x, at.y - origin.y) { drawLayer(layer) }
            }
    )
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind { drawRect(tint) }
    )
}
