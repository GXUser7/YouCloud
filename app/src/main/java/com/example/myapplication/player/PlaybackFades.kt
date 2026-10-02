package com.example.myapplication.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The player's volume over time: the crossfade between tracks, and the sleep timer's fade.
 *
 * A crossfade hands over early. A few seconds before the track ends, a second player ([helper])
 * takes up its last seconds where the main one is, fading out, while the main player moves on to
 * the next track and fades it in. The main player, and with it the queue, the notification and
 * the app, go on as they always do, only a few seconds sooner; the helper is heard and nothing
 * more — no audio focus of its own, and the main player's audio session, so the equalizer is on it
 * too. The old track reads from the cache the main player filled.
 *
 * [crossfadeMs]: the crossfade's length, 0 for none. [reverbOfCurrent]: the track playing's reverb,
 * which its last seconds keep.
 */
@UnstableApi
internal class PlaybackFades(
    private val context: Context,
    private val main: ExoPlayer,
    private val mediaSourceFactory: MediaSource.Factory,
    private val crossfadeMs: () -> Long,
    private val reverbOfCurrent: () -> Int
) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val helperReverb = ReverbAudioProcessor()
    private var helper: ExoPlayer? = null

    // The track the helper is loaded with, by its place in the queue and its id.
    private var armedFor: String? = null

    // A crossfade under way: its length, how far the old track has faded out and the new one come
    // in (-1: not), and the track the main player moved on to.
    private var fadeMs = 0L
    private var outElapsed = -1L
    private var inElapsed = -1L
    private var movedTo: String? = null

    private var ticking = false
    private var lastTick = 0L
    private var timerExpiry: Runnable? = null

    private val tick = object : Runnable {
        override fun run() {
            step()
            val fading = outElapsed >= 0 || inElapsed >= 0
            // Nothing to watch for with neither a crossfade nor the timer set.
            val watching = main.isPlaying && (crossfadeMs() > 0 || SleepTimer.state.value != SleepTimer.State.Off)
            if (fading || watching) {
                handler.postDelayed(this, if (fading) FADE_TICK_MS else TICK_MS)
            } else {
                ticking = false
                lastTick = 0L
            }
        }
    }

    init {
        main.addListener(this)
        scope.launch { SleepTimer.state.collect(::onTimer) }
    }

    /** Looks at the player again: playing, a setting or the timer changed. */
    fun ensureTicking() {
        if (ticking) return
        ticking = true
        lastTick = 0L
        handler.post(tick)
    }

    private fun step() {
        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTick == 0L) 0L else (now - lastTick).coerceIn(0L, 500L)
        lastTick = now
        // The fade moves on only while the music does: paused, it waits where it is.
        if (outElapsed >= 0 && main.playWhenReady) outElapsed += dt
        if (inElapsed >= 0 && main.isPlaying) inElapsed += dt
        maybeCrossfade()

        val timerGain = when (val timer = SleepTimer.state.value) {
            is SleepTimer.State.At -> {
                val left = timer.endsAt - now
                if (left <= 0) {
                    stopForTimer()
                    return
                }
                fadeCurve(left.toFloat() / SleepTimer.FADE_MS)
            }
            SleepTimer.State.EndOfTrack -> {
                val duration = main.duration
                if (duration == C.TIME_UNSET) 1f else fadeCurve((duration - main.currentPosition).toFloat() / SleepTimer.TRACK_END_FADE_MS)
            }
            SleepTimer.State.Off -> 1f
        }
        // Equal power: the two together stay as loud as one.
        val out = if (outElapsed >= 0) (outElapsed.toFloat() / fadeMs).coerceIn(0f, 1f) else 1f
        val into = if (inElapsed >= 0) (inElapsed.toFloat() / fadeMs).coerceIn(0f, 1f) else 1f
        setVolume(main, sin(into * PI / 2).toFloat() * timerGain)
        helper?.let { setVolume(it, cos(out * PI / 2).toFloat() * timerGain) }

        if (outElapsed >= fadeMs || (outElapsed >= 0 && helper?.playbackState == Player.STATE_ENDED)) stopHelper()
        if (inElapsed >= fadeMs) inElapsed = -1L
    }

    /** Starts a crossfade when the track playing is that close to its end, and the next can take over. */
    private fun maybeCrossfade() {
        val fade = crossfadeMs()
        if (fade <= 0 || outElapsed >= 0) return
        // The sleep timer stopping at the end of the track wants that end, not the next track.
        if (SleepTimer.state.value == SleepTimer.State.EndOfTrack) return
        if (!main.isPlaying || main.playbackState != Player.STATE_READY) return
        if (!main.hasNextMediaItem() || main.repeatMode == Player.REPEAT_MODE_ONE) return
        val item = main.currentMediaItem ?: return
        if (isLive(item) || isLive(main.getMediaItemAt(main.nextMediaItemIndex))) return
        val duration = main.duration
        // A short track would be half crossfade.
        if (duration == C.TIME_UNSET || duration < fade * 2 + MIN_TRACK_EXTRA_MS) return
        val speed = main.playbackParameters.speed.coerceAtLeast(0.1f)
        val left = ((duration - main.currentPosition) / speed).toLong()
        val key = "${main.currentMediaItemIndex}:${item.mediaId}"
        if (left <= fade + ARM_AHEAD_MS && armedFor != key) arm(item, key, duration - (fade * speed).toLong())
        if (left <= fade && armedFor == key) handOff(fade)
    }

    /** Loads the track playing into the helper, around where it will take over, to start at once. */
    private fun arm(item: MediaItem, key: String, fromMs: Long) {
        val player = helper ?: build().also { helper = it }
        player.setMediaItem(item, fromMs.coerceAtLeast(0L))
        player.volume = 0f
        player.playWhenReady = false
        player.prepare()
        armedFor = key
    }

    private fun handOff(fade: Long) {
        val player = helper ?: return
        armedFor = null
        player.playbackParameters = main.playbackParameters
        helperReverb.amount = reverbOfCurrent()
        // Just where the main player is: buffered already around there, it starts at once.
        player.seekTo(main.currentPosition)
        player.volume = main.volume
        player.play()
        fadeMs = fade
        outElapsed = 0L
        inElapsed = 0L
        val next = main.nextMediaItemIndex
        movedTo = "$next:${main.getMediaItemAt(next).mediaId}"
        Log.d(TAG, "Crossfading over $fade ms into ${main.getMediaItemAt(next).mediaMetadata.title}")
        main.seekToNextMediaItem()
        main.volume = 0f
    }

    /** The crossfade broken off: the listener skipped on, or sought, in the middle of it. */
    private fun abort() {
        stopHelper()
        inElapsed = -1L
        movedTo = null
    }

    private fun stopHelper() {
        helper?.let {
            it.stop()
            it.clearMediaItems()
        }
        outElapsed = -1L
    }

    private fun stopForTimer() {
        Log.d(TAG, "Sleep timer ran out")
        abort()
        main.pause()
        main.volume = 1f
        SleepTimer.cancel()
    }

    private fun onTimer(state: SleepTimer.State) {
        main.pauseAtEndOfMediaItems = state == SleepTimer.State.EndOfTrack
        timerExpiry?.let(handler::removeCallbacks)
        timerExpiry = null
        if (state is SleepTimer.State.At) {
            // Run out while paused, it only goes off: there is nothing playing to stop.
            val expiry = Runnable { if (SleepTimer.state.value === state && !main.playWhenReady) SleepTimer.cancel() }
            timerExpiry = expiry
            handler.postDelayed(expiry, (state.endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        }
        if (state == SleepTimer.State.Off && !main.isPlaying) main.volume = 1f
        ensureTicking()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        armedFor = null
        val key = "${main.currentMediaItemIndex}:${mediaItem?.mediaId}"
        if (outElapsed >= 0 || inElapsed >= 0) {
            if (key == movedTo) movedTo = null else abort()
        }
        ensureTicking()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        // A seek within the new track while it comes in: it is the listener's now, at full volume.
        if (reason == Player.DISCONTINUITY_REASON_SEEK && inElapsed >= 0 && newPosition.mediaItemIndex == oldPosition.mediaItemIndex) abort()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        ensureTicking()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // The old track's end pauses and goes on with the main player.
        helper?.takeIf { outElapsed >= 0 }?.playWhenReady = playWhenReady
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && SleepTimer.state.value == SleepTimer.State.EndOfTrack) {
            Log.d(TAG, "Sleep timer: the track has ended")
            SleepTimer.cancel()
            main.volume = 1f
        }
        // Played again after the timer ran out while paused: it is over, not to stop the music now.
        if (playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            val timer = SleepTimer.state.value
            if (timer is SleepTimer.State.At && timer.endsAt <= SystemClock.elapsedRealtime()) SleepTimer.cancel()
        }
        ensureTicking()
    }

    private fun build(): ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessorChain(DefaultAudioSink.DefaultAudioProcessorChain(helperReverb))
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        })
        .setMediaSourceFactory(mediaSourceFactory)
        // Heard alongside the main player, never instead of it: no audio focus of its own.
        .setAudioAttributes(
            AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build(),
            false
        )
        .build()
        .apply {
            trackSelectionParameters = trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
            if (main.audioSessionId != C.AUDIO_SESSION_ID_UNSET) setAudioSessionId(main.audioSessionId)
        }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        main.removeListener(this)
        helper?.release()
        helper = null
    }

    private companion object {
        const val TAG = "PlaybackFades"
        const val TICK_MS = 250L
        const val FADE_TICK_MS = 40L

        // The helper is loaded this long before it takes over.
        const val ARM_AHEAD_MS = 6_000L
        const val MIN_TRACK_EXTRA_MS = 10_000L

        fun isLive(item: MediaItem) = item.localConfiguration?.uri?.scheme == "ytlive"

        // Loudness heard as falling evenly: squared, not straight.
        fun fadeCurve(x: Float): Float = x.coerceIn(0f, 1f).let { it * it }

        fun setVolume(player: ExoPlayer, volume: Float) {
            if (abs(player.volume - volume) > 0.004f || (volume == 1f && player.volume != 1f)) player.volume = volume
        }
    }
}
