package com.example.myapplication.together

import android.os.SystemClock
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudUser
import com.example.myapplication.data.liveVideoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The player as listening together needs it: the app's own, seen and driven from here. */
interface TogetherPlayer {
    val currentTrack: StateFlow<SoundCloudTrack?>
    val isPlaying: StateFlow<Boolean>
    val currentTrackId: StateFlow<Long?>
    val isBuffering: StateFlow<Boolean>
    fun livePositionMs(): Long

    // The host's own player, as its listener's buttons drive it: a guest's presses come here.
    fun togglePlayPause()
    fun skip(next: Boolean)
    fun seekTo(positionMs: Long)
    fun playQueue(track: SoundCloudTrack, queue: List<SoundCloudTrack>)

    // A guest's player, following the host.
    fun follow(track: SoundCloudTrack, url: String?, startMs: Long, playing: Boolean)
    fun setPlaying(playing: Boolean)
    fun setSpeed(speed: Float)

    /** An address a guest without the service can play [track] by, if one is needed; on the host. */
    suspend fun directUrlFor(track: SoundCloudTrack): String?
}

/**
 * Listening together, on top of [TogetherSession]: the host tells its guests what plays and where
 * — on every change and every few seconds — and does what their buttons ask; a guest plays the same
 * track and keeps to the host's position, by the host's clock (see [ClockSync], [DriftCorrection]).
 */
class ListenTogether(
    private val session: TogetherLink,
    private val player: TogetherPlayer,
    private val scope: CoroutineScope,
    // Both phones' clock: elapsedRealtime, which neither phone's user can set.
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) {
    val state get() = session.state

    // On a guest: the host's clock, and the last of what it said.
    private val clock = ClockSync()
    private var hostState: TogetherMessage? = null
    private var lastSeq = -1L
    private var followJob: Job? = null
    private var hostJob: Job? = null
    private var nudged = false

    // On the host: the address a guest may need for the track playing, worked out once.
    private var directFor: Long? = null
    private var direct: String? = null
    private var seq = 0L

    /** How much later this phone's sound reaches the ear than the host's (Bluetooth), ms; on a guest. */
    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs = _latencyMs.asStateFlow()
    fun setLatency(ms: Long) {
        _latencyMs.value = ms
    }

    /** How far the guest is from the host, ms, as last measured; null while not following. */
    private val _driftMs = MutableStateFlow<Long?>(null)
    val driftMs = _driftMs.asStateFlow()

    init {
        scope.launch {
            session.messages.collect { (from, message) -> onMessage(from, message) }
        }
        scope.launch {
            session.state.collect { state ->
                when (state) {
                    is TogetherState.Hosting -> startHosting()
                    is TogetherState.Joined -> startFollowing(state.host.id)
                    else -> stopAll()
                }
            }
        }
    }

    val isGuest get() = session.state.value is TogetherState.Joined

    /** A guest's button, sent to the host; false when this phone isn't a guest. */
    fun relay(command: String, positionMs: Long? = null): Boolean {
        val host = (session.state.value as? TogetherState.Joined)?.host ?: return false
        session.send(host.id, TogetherMessage(t = "cmd", c = command, pos = positionMs))
        return true
    }

    /** A track a guest picked, for the host to play with [queue] after it; false when not a guest. */
    fun relayPlay(track: SoundCloudTrack, queue: List<SoundCloudTrack>): Boolean {
        val host = (session.state.value as? TogetherState.Joined)?.host ?: return false
        // As much of the queue as fits in one message, from the track on.
        val from = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        var after = queue.drop(from).take(MAX_RELAYED_QUEUE).map { it.slim() }
        var message = TogetherMessage(t = "cmd", c = "play", track = track.slim(), queue = after)
        while (!session.fits(message) && after.size > 1) {
            after = after.take(after.size / 2)
            message = message.copy(queue = after)
        }
        session.send(host.id, message)
        return true
    }

    // ------------------------------------------------------------------------------------- host

    private fun startHosting() {
        if (hostJob?.isActive == true) return
        stopFollowing()
        hostJob = scope.launch {
            launch {
                // Every change of track or of playing at once…
                combine(player.currentTrack, player.isPlaying) { track, playing -> track?.id to playing }
                    .distinctUntilChanged()
                    .collectLatest { broadcastState() }
            }
            // …at once when the host's track jumps (a seek), and every few seconds anyway: a guest
            // come in, a message lost.
            var sinceLast = 0L
            while (true) {
                delay(HOST_TICK_MS)
                sinceLast += HOST_TICK_MS
                if (sinceLast >= HEARTBEAT_MS || jumped()) {
                    sinceLast = 0
                    broadcastState()
                }
            }
        }
    }

    // Where the host's track was said to be, and when: what [jumped] compares with.
    private var saidPos = 0L
    private var saidAt = 0L
    private var saidPlaying = false

    /** Whether the host's track is somewhere else than it would be from what was last said. */
    private fun jumped(): Boolean {
        val expected = if (saidPlaying) saidPos + (now() - saidAt) else saidPos
        return abs(player.livePositionMs() - expected) > JUMP_MS
    }

    private suspend fun broadcastState() {
        val hosting = session.state.value as? TogetherState.Hosting ?: return
        if (hosting.guests.isEmpty()) return
        val track = player.currentTrack.value ?: return
        if (directFor != track.id) {
            directFor = track.id
            direct = runCatching { player.directUrlFor(track) }.getOrNull()
        }
        saidPos = player.livePositionMs()
        saidAt = now()
        saidPlaying = player.isPlaying.value
        session.broadcast(
            TogetherMessage(
                t = "state",
                seq = ++seq,
                track = track.slim(),
                url = direct,
                playing = saidPlaying,
                pos = saidPos,
                at = saidAt
            )
        )
    }

    private fun onHostMessage(from: String, message: TogetherMessage) {
        when (message.t) {
            "ping" -> session.send(from, TogetherMessage(t = "pong", a = message.a, h = now()))
            "hello" -> scope.launch { broadcastState() }
            "cmd" -> {
                when (message.c) {
                    "toggle" -> player.togglePlayPause()
                    "next" -> player.skip(next = true)
                    "prev" -> player.skip(next = false)
                    "seek" -> message.pos?.let(player::seekTo)
                    "play" -> message.track?.let { player.playQueue(it, message.queue.orEmpty().ifEmpty { listOf(it) }) }
                }
                // Told at once, not at the next heartbeat; a moment later, once the player has it.
                scope.launch {
                    delay(250)
                    broadcastState()
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------ guest

    private fun startFollowing(hostId: String) {
        if (followJob?.isActive == true) return
        hostJob?.cancel()
        clock.clear()
        hostState = null
        lastSeq = -1
        followJob = scope.launch {
            // The host's clock: a burst of pings to start, then one now and then as it drifts.
            launch {
                repeat(PINGS_AT_START) {
                    ping(hostId)
                    delay(120)
                }
                while (true) {
                    delay(PING_EVERY_MS)
                    ping(hostId)
                }
            }
            while (true) {
                delay(FOLLOW_TICK_MS)
                keepUp()
            }
        }
    }

    private fun ping(hostId: String) =
        session.send(hostId, TogetherMessage(t = "ping", a = now()))

    private fun onGuestMessage(message: TogetherMessage) {
        when (message.t) {
            "pong" -> {
                val sent = message.a ?: return
                val host = message.h ?: return
                clock.add(sent, host, now())
            }
            "state" -> {
                val seq = message.seq ?: return
                // Out of order over the air now and then: an older one would move the player back.
                if (seq <= lastSeq) return
                lastSeq = seq
                val track = message.track ?: return
                val previous = hostState
                hostState = message
                if (player.currentTrackId.value != track.id || previous?.track?.id != track.id) {
                    val hostNow = clock.hostNow(now())
                    val start = if (hostNow != null) expected(message, hostNow) else message.pos ?: 0L
                    resetSpeed()
                    player.follow(track, message.url, start.coerceAtLeast(0L), message.playing == true)
                } else if (message.playing != player.isPlaying.value) {
                    player.setPlaying(message.playing == true)
                }
            }
        }
    }

    private fun expected(state: TogetherMessage, hostNow: Long) = DriftCorrection.expectedPosition(
        pos = state.pos ?: 0L,
        at = state.at ?: hostNow,
        playing = state.playing == true,
        hostNow = hostNow,
        latencyMs = _latencyMs.value
    )

    /** One step of keeping to the host: nothing, a little faster or slower, or a jump. */
    private fun keepUp() {
        val state = hostState ?: return
        val track = state.track ?: return
        val hostNow = clock.hostNow(now()) ?: return
        // A broadcast is at its live edge on both: there is no position to keep to.
        if (track.liveVideoId != null) return
        if (player.currentTrackId.value != track.id || player.isBuffering.value) {
            _driftMs.value = null
            return
        }
        if (state.playing != true) {
            resetSpeed()
            _driftMs.value = null
            return
        }
        val local = player.livePositionMs()
        val target = expected(state, hostNow)
        _driftMs.value = target - local
        when (val action = DriftCorrection.decide(local, target)) {
            DriftAction.Hold -> resetSpeed()
            is DriftAction.Nudge -> {
                nudged = true
                player.setSpeed(action.speed)
            }
            is DriftAction.Seek -> {
                resetSpeed()
                player.seekTo(action.toMs)
            }
        }
    }

    private fun resetSpeed() {
        if (nudged) {
            nudged = false
            player.setSpeed(1f)
        }
    }

    // --------------------------------------------------------------------------------- shared

    private fun onMessage(from: String, message: TogetherMessage) {
        when (session.state.value) {
            is TogetherState.Hosting -> onHostMessage(from, message)
            is TogetherState.Joined -> onGuestMessage(message)
            else -> Unit
        }
    }

    private fun stopFollowing() {
        followJob?.cancel()
        followJob = null
        resetSpeed()
        hostState = null
        _driftMs.value = null
    }

    private fun stopAll() {
        stopFollowing()
        hostJob?.cancel()
        hostJob = null
        directFor = null
        direct = null
    }

    fun leave() {
        session.leave()
    }

    companion object {
        private const val HEARTBEAT_MS = 3_000L
        private const val HOST_TICK_MS = 500L
        // A jump of the host's track worth telling at once: far more than a tick's drift.
        private const val JUMP_MS = 1_000L
        private const val FOLLOW_TICK_MS = 500L
        private const val PING_EVERY_MS = 10_000L
        private const val PINGS_AT_START = 8
        private const val MAX_RELAYED_QUEUE = 30
    }
}

/**
 * A track as sent to another phone: what it needs to find and play it, without what can run long
 * and is no use there (an artist's description). SoundCloud's stream details stay: the other phone
 * resolves the track by them.
 */
internal fun SoundCloudTrack.slim(): SoundCloudTrack = copy(
    user = user?.slim(),
    artists = artists?.map { it.slim() }
)

private fun SoundCloudUser.slim(): SoundCloudUser = copy(description = null)
