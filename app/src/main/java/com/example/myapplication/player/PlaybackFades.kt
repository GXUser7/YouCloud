package com.example.myapplication.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
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

/** How a track sounds by its own effects: its speed and pitch, and its reverb (0–100). */
internal class TrackSound(val parameters: PlaybackParameters, val reverb: Int)

/**
 * The player's volume over time: the crossfade between tracks, and the sleep timer's fade.
 *
 * A crossfade lays the next track over the end of this one, and touches nothing else. The main
 * player plays its track to the very end and moves on to the next by itself, as without a
 * crossfade — the queue, the notification, the history, the radio and listening together see a
 * track that ended, not one skipped seconds early. A few seconds before the end a second player
 * ([helper]) starts the next track, quietly at first, while the main one fades out. When the main
 * player gets to that track, it goes to where the helper is in it, unheard — one jump, then a
 * little faster or slower for a moment until the two play level, as a jump lands only roughly —
 * and takes over in a moment's blend: nothing heard breaks off, nothing is heard twice. Meanwhile
 * the app goes on showing it playing ([SessionBridge.crossfadeSettling]); and it shows the next
 * track from the moment it comes in, as heard ([SessionBridge.crossfadeIncoming]).
 *
 * The helper is heard and nothing more: no audio focus of its own. Its audio session is its own
 * too: the equalizer sets the volume of everything in its session as one, so with the main
 * player's the two had one volume between them — the next track faded out with the old one, and
 * came back over it at the hand-back as a burst. [onHelperSession] puts the equalizer on it as
 * well. It reads the next track from the stream cache, where the prefetcher has put it.
 *
 * [crossfadeMs]: the crossfade's length, 0 for none. [soundOf]: a track's own effects, for the
 * helper to play the next track with them.
 */
@UnstableApi
internal class PlaybackFades(
    private val context: Context,
    private val main: ExoPlayer,
    private val mediaSourceFactory: MediaSource.Factory,
    private val crossfadeMs: () -> Long,
    private val soundOf: (MediaItem) -> TrackSound,
    private val onHelperSession: (audioSessionId: Int) -> Unit
) : Player.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val helperReverb = ReverbAudioProcessor()
    private var helper: ExoPlayer? = null

    private enum class Phase {
        Idle,

        /** The helper holds the next track, loaded and paused, for the end of this one. */
        Armed,

        /** Both heard: the main player's track fading out, the helper's coming in. */
        Fading,

        /** The main player on the new track, finding the helper's place in it, unheard. */
        HandingBack,

        /** Level with the helper: the main player heard instead of it, over a moment. */
        Swapping
    }

    private var phase = Phase.Idle

    // What the helper is armed for: this track and the next, by their places in the queue and ids.
    private var armedKey: String? = null

    // The next track, as the main player will name it on getting there.
    private var incomingId: String? = null

    // Taking over from the helper: how far ahead of the helper's place to seek, learned from each
    // crossfade (what a seek takes to sound); how many seeks there have been, since when it is
    // under way, and the main player's own speed, which a nudge departs from; since when the main
    // player has played as it is (0 before the first jump), what the two were seen apart meanwhile,
    // and whether it is being nudged.
    private var handbackLeadMs = INITIAL_HANDBACK_LEAD_MS
    private var handbackSeeks = 0
    private var handbackSince = 0L
    private var learned = false
    private var mainSound: PlaybackParameters = PlaybackParameters.DEFAULT
    private var steadySince = 0L
    private val apart = ArrayDeque<Long>()
    private var nudging = false
    private var swapSince = 0L

    private val endNudge = Runnable {
        if (!nudging) return@Runnable
        nudging = false
        main.playbackParameters = mainSound
        unsteady()
    }

    // The main player's seeks that are this class's own, not the listener's.
    private var ownSeeks = 0

    private var ticking = false
    private var timerExpiry: Runnable? = null

    private val tick = object : Runnable {
        override fun run() {
            step()
            val busy = phase == Phase.Fading || phase == Phase.HandingBack || phase == Phase.Swapping
            // Nothing to watch for with neither a crossfade nor the timer set.
            val watching = main.isPlaying && (crossfadeMs() > 0 || SleepTimer.state.value != SleepTimer.State.Off)
            if (busy || watching) {
                handler.postDelayed(this, if (busy) FADE_TICK_MS else TICK_MS)
            } else {
                ticking = false
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
        handler.post(tick)
    }

    private fun step() {
        val timerGain = timerGain() ?: return
        when (phase) {
            Phase.Idle, Phase.Armed -> {
                maybeCrossfade()
            }
            Phase.Fading -> {
                // The new track goes on whatever the old one does but pause: held while the old
                // one's stream caught up, it fell silent with it — and at the very end, where the
                // main player loads the next track, both were silent at once.
                helper?.playWhenReady = main.playWhenReady
            }
            Phase.HandingBack -> {
                // Heard alone until the main player has its place: it plays on while that loads.
                helper?.playWhenReady = main.playWhenReady
                handBack()
            }
            Phase.Swapping -> if (SystemClock.elapsedRealtime() - swapSince >= SWAP_MS || !main.isPlaying) finish()
        }

        when (phase) {
            Phase.Fading -> {
                // Equal power: the two together stay as loud as one.
                val p = fadeProgress()
                setVolume(main, cos(p * PI / 2).toFloat() * timerGain)
                helper?.let { setVolume(it, sin(p * PI / 2).toFloat() * timerGain) }
            }
            Phase.HandingBack -> {
                setVolume(main, 0f)
                helper?.let { setVolume(it, timerGain) }
            }
            Phase.Swapping -> {
                // The same music on both, level: straight lines add up to one.
                val p = ((SystemClock.elapsedRealtime() - swapSince).toFloat() / SWAP_MS).coerceIn(0f, 1f)
                setVolume(main, p * timerGain)
                helper?.let { setVolume(it, (1f - p) * timerGain) }
            }
            Phase.Idle, Phase.Armed -> setVolume(main, timerGain)
        }
    }

    /** The sleep timer's share of the volume, 1 when it is off; null once it has stopped the music. */
    private fun timerGain(): Float? = when (val timer = SleepTimer.state.value) {
        is SleepTimer.State.At -> {
            val left = timer.endsAt - SystemClock.elapsedRealtime()
            if (left <= 0) {
                stopForTimer()
                null
            } else {
                fadeCurve(left.toFloat() / SleepTimer.FADE_MS)
            }
        }
        SleepTimer.State.EndOfTrack -> {
            val duration = main.duration
            if (duration == C.TIME_UNSET) 1f else fadeCurve((duration - main.currentPosition).toFloat() / SleepTimer.TRACK_END_FADE_MS)
        }
        SleepTimer.State.Off -> 1f
    }

    /** How far the crossfade is, 0 to 1: by what is left of the old track, which ends it. */
    private fun fadeProgress(): Float {
        val duration = main.duration
        if (duration == C.TIME_UNSET || fadeMs <= 0) return 1f
        val speed = main.playbackParameters.speed.coerceAtLeast(0.1f)
        val left = (duration - main.currentPosition) / speed
        return (1f - left / fadeMs).coerceIn(0f, 1f)
    }

    // The crossfade under way: its length.
    private var fadeMs = 0L

    /** Arms the helper near the end of the track, and starts the crossfade at its moment. */
    private fun maybeCrossfade() {
        val fade = crossfadeMs()
        val item = main.currentMediaItem
        val nextIndex = main.nextMediaItemIndex
        val possible = fade > 0 &&
            item != null &&
            // The sleep timer stopping at the end of the track wants that end, not the next track.
            SleepTimer.state.value != SleepTimer.State.EndOfTrack &&
            main.playWhenReady &&
            main.repeatMode != Player.REPEAT_MODE_ONE &&
            nextIndex != C.INDEX_UNSET &&
            nextIndex != main.currentMediaItemIndex
        if (!possible) {
            disarm()
            return
        }
        val next = main.getMediaItemAt(nextIndex)
        val duration = main.duration
        // A broadcast has no end to fade, and a short track would be half crossfade.
        if (isLive(item!!) || isLive(next) || duration == C.TIME_UNSET || duration < fade * 2 + MIN_TRACK_EXTRA_MS) {
            disarm()
            return
        }
        val speed = main.playbackParameters.speed.coerceAtLeast(0.1f)
        val left = ((duration - main.currentPosition) / speed).toLong()
        val key = "${main.currentMediaItemIndex}:${item.mediaId}>$nextIndex:${next.mediaId}"
        if (left > fade + ARM_AHEAD_MS) {
            // Gone back from the end, or not there yet.
            disarm()
            return
        }
        if (armedKey != key) arm(next, key)
        if (left > fade || main.playbackState != Player.STATE_READY) return
        val player = helper ?: return
        // Not loaded yet: it comes in over what is left once it is, or not at all at the very end.
        if (player.playbackState != Player.STATE_READY) return
        if (left < MIN_FADE_MS) return
        val incomingDuration = player.duration
        if (incomingDuration != C.TIME_UNSET && incomingDuration < left * 2 + MIN_TRACK_EXTRA_MS) {
            disarm()
            return
        }
        start(left)
    }

    /** Loads the next track into the helper, from its start, with its own effects, to start at once. */
    private fun arm(next: MediaItem, key: String) {
        val player = helper ?: build().also { helper = it }
        val sound = soundOf(next)
        player.playbackParameters = sound.parameters
        helperReverb.amount = sound.reverb
        player.setMediaItem(next, 0L)
        player.volume = 0f
        player.playWhenReady = false
        player.prepare()
        armedKey = key
        incomingId = next.mediaId
        phase = Phase.Armed
    }

    private fun start(fade: Long) {
        val player = helper ?: return
        fadeMs = fade
        phase = Phase.Fading
        player.volume = 0f
        player.play()
        // The app moves on to it now, with the music, rather than when the track before ends.
        val id = incomingId
        if (id != null) {
            SessionBridge.crossfadeIncoming.value = SessionBridge.CrossfadeIncoming(id, player.duration.coerceAtLeast(0L)) {
                helper?.currentPosition?.coerceAtLeast(0L) ?: 0L
            }
        }
        Log.d(TAG, "Crossfading over $fade ms into ${player.currentMediaItem?.mediaMetadata?.title}")
    }

    /** The main player has reached the track the helper plays: it finds the helper's place in it. */
    private fun beginHandBack() {
        phase = Phase.HandingBack
        SessionBridge.crossfadeSettling.value = true
        main.volume = 0f
        handbackSeeks = 0
        steadySince = 0L
        apart.clear()
        learned = false
        handbackSince = SystemClock.elapsedRealtime()
        // Not from here, in the middle of the player's own callback: its seek would be told late.
        handler.post {
            if (phase != Phase.HandingBack) return@post
            // The new track's own speed, as the service has just set it: what catching up departs from.
            mainSound = main.playbackParameters
            seekToHelper()
        }
    }

    private fun seekToHelper() {
        val player = helper ?: return
        ownSeeks++
        main.seekTo(player.currentPosition + handbackLeadMs)
        handbackSeeks++
        unsteady()
    }

    /** The main player changed how it plays: what the two are apart is to be seen anew. */
    private fun unsteady() {
        steadySince = SystemClock.elapsedRealtime()
        apart.clear()
    }

    /**
     * Plays the main player a little faster or slower for as long as makes up [diff] (it is
     * [diff] ahead of the helper, behind if less than 0), then as before.
     */
    private fun nudge(diff: Long) {
        val speed = mainSound.speed.coerceAtLeast(0.1f)
        val factor = if (diff < 0) 1f + NUDGE else 1f - NUDGE
        main.playbackParameters = PlaybackParameters(speed * factor, mainSound.pitch)
        nudging = true
        unsteady()
        Log.d(TAG, "Crossfade hand-back nudged over $diff ms")
        handler.postDelayed(endNudge, (abs(diff) / (speed * NUDGE)).toLong().coerceAtLeast(1L))
    }

    private fun stopNudge() {
        handler.removeCallbacks(endNudge)
        nudging = false
    }

    /**
     * One look at the main player taking over. A jump lands within some tens of milliseconds of
     * where it was aimed; the rest is made up by playing a little faster or slower for a moment —
     * unheard, and with no stop to load, as another jump would have. Level, it is heard instead.
     *
     * What the two are apart is only taken once it stays put: after a jump the position eases
     * over to the audio output's clock at a tenth of the speed, a few tens of milliseconds over a
     * moment, and a nudge sounds only once what was played before it is out. Taken while it
     * moved, catching up chased it, and the two were blended apart.
     */
    private fun handBack() {
        val player = helper
        if (player == null) {
            finish()
            return
        }
        // Paused meanwhile: the main player simply stays where the helper got to.
        if (!main.playWhenReady) {
            ownSeeks++
            main.seekTo(player.currentPosition)
            finish()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (steadySince == 0L) return
        if (!main.isPlaying || main.playbackState != Player.STATE_READY) {
            unsteady()
            // The new track won't load: the helper has it, but can't hold it for ever.
            if (now - handbackSince > HANDBACK_GIVE_UP_MS) {
                Log.w(TAG, "Main player didn't catch up with the crossfade; taking over anyway")
                stopNudge()
                main.playbackParameters = mainSound
                ownSeeks++
                main.seekTo(player.currentPosition)
                finish()
            }
            return
        }
        if (nudging || now - steadySince < SETTLE_MS) return
        apart.addLast(main.currentPosition - player.currentPosition)
        if (apart.size > STEADY_LOOKS) apart.removeFirst()
        val tooLong = now - handbackSince > HANDBACK_MAX_MS
        val steady = apart.size == STEADY_LOOKS && apart.max() - apart.min() <= STEADY_SPREAD_MS
        if (!steady) {
            if (tooLong) takeOverUnlevel(apart.last())
            return
        }
        val diff = Math.round(apart.average())
        if (!learned) {
            // What this jump missed by: the next crossfade's first jump aims that much better.
            learned = true
            handbackLeadMs = (handbackLeadMs - diff).coerceIn(0L, MAX_HANDBACK_LEAD_MS)
            Log.d(TAG, "Crossfade hand-back landed $diff ms off")
        }
        when {
            abs(diff) <= ALIGNED_MS -> {
                phase = Phase.Swapping
                swapSince = now
                Log.d(TAG, "Crossfade handed back $diff ms apart in ${now - handbackSince} ms")
            }
            tooLong -> takeOverUnlevel(diff)
            // Far off (a jump gone wrong): one more jump.
            abs(diff) > RESEEK_MS && handbackSeeks < MAX_HANDBACK_SEEKS -> {
                learned = false
                seekToHelper()
            }
            else -> nudge(diff)
        }
    }

    // Never level: one straight after the other, a jump of what they are apart, rather than the
    // two blended — two copies of the music apart, heard as a burst.
    private fun takeOverUnlevel(diff: Long) {
        Log.d(TAG, "Crossfade handed back unlevel, $diff ms apart, at once")
        finish()
    }

    /** The main player heard alone again, at full volume. */
    private fun finish() {
        val wasHandingBack = phase == Phase.HandingBack || phase == Phase.Swapping
        stopNudge()
        phase = Phase.Idle
        armedKey = null
        incomingId = null
        stopHelper()
        if (wasHandingBack) main.playbackParameters = mainSound
        SessionBridge.crossfadeSettling.value = false
        SessionBridge.crossfadeIncoming.value = null
        timerGain()?.let { setVolume(main, it) }
    }

    /** No crossfade in prospect: the helper let go of the next track. */
    private fun disarm() {
        if (phase != Phase.Armed) return
        phase = Phase.Idle
        armedKey = null
        incomingId = null
        stopHelper()
    }

    /** The crossfade broken off: the listener skipped, sought, or the queue changed under it. */
    private fun abort() {
        if (phase == Phase.Idle) return
        Log.d(TAG, "Crossfade broken off in $phase")
        stopNudge()
        if (phase == Phase.HandingBack || phase == Phase.Swapping) main.playbackParameters = mainSound
        phase = Phase.Idle
        armedKey = null
        incomingId = null
        stopHelper()
        SessionBridge.crossfadeSettling.value = false
        SessionBridge.crossfadeIncoming.value = null
        main.volume = timerGain() ?: 1f
    }

    private fun stopHelper() {
        helper?.let {
            it.stop()
            it.clearMediaItems()
        }
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
        // A crossfade about to begin gives way to a timer stopping at the end of the track.
        if (state == SleepTimer.State.EndOfTrack && phase == Phase.Fading) abort()
        if (state == SleepTimer.State.Off && !main.isPlaying && phase == Phase.Idle) main.volume = 1f
        ensureTicking()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val byItself = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        when (phase) {
            Phase.Fading -> if (byItself && mediaItem?.mediaId == incomingId) beginHandBack() else abort()
            Phase.Armed -> disarm()
            Phase.HandingBack, Phase.Swapping -> abort()
            Phase.Idle -> Unit
        }
        ensureTicking()
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        // The queue changed: what comes next may be another track now.
        if (phase == Phase.Armed) disarm()
    }

    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
        if (reason != Player.DISCONTINUITY_REASON_SEEK) return
        if (ownSeeks > 0) {
            ownSeeks--
            return
        }
        // The listener sought: in the old track, there is no end to fade now; in the new one, it
        // is theirs from there, at full volume.
        when (phase) {
            Phase.Armed -> disarm()
            Phase.Fading -> if (newPosition.mediaItemIndex == oldPosition.mediaItemIndex) abort()
            Phase.HandingBack, Phase.Swapping -> abort()
            Phase.Idle -> Unit
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        ensureTicking()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // The two tracks of a crossfade pause and go on together; paused while the two are level,
        // the main player has it.
        if (phase == Phase.Fading) helper?.playWhenReady = playWhenReady
        if (phase == Phase.Swapping && !playWhenReady) finish()
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
            // Its own session, with the equalizer put on it.
            if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) onHelperSession(audioSessionId)
            addListener(object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) onHelperSession(audioSessionId)
                }
            })
        }

    fun release() {
        SessionBridge.crossfadeSettling.value = false
        SessionBridge.crossfadeIncoming.value = null
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

        // The helper is loaded this long before it comes in: the next track's address may take
        // yt-dlp a few seconds to find.
        const val ARM_AHEAD_MS = 12_000L
        const val MIN_TRACK_EXTRA_MS = 10_000L

        // Shorter than this, a crossfade is only a click: the tracks just follow each other.
        const val MIN_FADE_MS = 800L

        // Taking over from the helper: close enough to be heard as one; how long after a jump or
        // a nudge to start looking (the audio output's clock comes in, what was played before is
        // out); how many looks in a row, a tick apart, are to differ by no more than this for what
        // the two are apart to be taken; off by more than this, another jump, and how many at
        // most; how much faster or slower a nudge plays; how long it may take before it takes over
        // as it is, and how long to wait for the track to load at all; the blend from one player
        // to the other.
        const val ALIGNED_MS = 5L
        const val SETTLE_MS = 600L
        const val STEADY_LOOKS = 5
        const val STEADY_SPREAD_MS = 6L
        const val RESEEK_MS = 300L
        const val MAX_HANDBACK_SEEKS = 3
        const val NUDGE = 0.2f
        const val HANDBACK_MAX_MS = 8_000L
        // The helper is heard meanwhile: the next track's address may take yt-dlp a while.
        const val HANDBACK_GIVE_UP_MS = 25_000L
        const val INITIAL_HANDBACK_LEAD_MS = 120L
        const val MAX_HANDBACK_LEAD_MS = 600L
        const val SWAP_MS = 60L

        fun isLive(item: MediaItem) = item.localConfiguration?.uri?.scheme == "ytlive"

        // Loudness heard as falling evenly: squared, not straight.
        fun fadeCurve(x: Float): Float = x.coerceIn(0f, 1f).let { it * it }

        fun setVolume(player: ExoPlayer, volume: Float) {
            if (abs(player.volume - volume) > 0.004f || (volume == 1f && player.volume != 1f)) player.volume = volume
        }
    }
}
