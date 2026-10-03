package com.example.myapplication.together

import android.os.SystemClock
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudUser
import com.example.myapplication.data.liveVideoId
import com.example.myapplication.data.youTubeVideoId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    /** Whether [trackId] is loaded and would sound the moment it is played. */
    fun isReadyFor(trackId: Long): Boolean

    /** Whether it is to play — playing, or loading to — rather than paused. */
    fun wantsToPlay(): Boolean

    // The host's own player, as its listener's buttons drive it: a guest's presses come here.
    fun togglePlayPause()
    fun skip(next: Boolean)
    fun seekTo(positionMs: Long)
    fun playQueue(track: SoundCloudTrack, queue: List<SoundCloudTrack>)

    /** On the host: whether the track playing came on by itself, the one before having ended. */
    fun changedByItself(): Boolean

    /** On the host: the [count] tracks after the one playing, as they will come. */
    fun upcoming(count: Int): List<SoundCloudTrack>

    /** On the host: its crossfade, seconds. */
    fun crossfadeSeconds(): Int

    // A guest's player, following the host. [next]: the host's next tracks, each with the address
    // the host found for it, if any.
    fun follow(track: SoundCloudTrack, url: String?, startMs: Long, playing: Boolean, next: List<Pair<SoundCloudTrack, String?>>)

    /** On a guest: the host's next tracks after the one playing, which stays as it is. */
    fun followUpcoming(next: List<Pair<SoundCloudTrack, String?>>)
    fun setPlaying(playing: Boolean)
    fun setSpeed(speed: Float)

    /** On a guest: the crossfade to use, the host's; null for its own again. */
    fun useCrossfade(seconds: Int?)

    /**
     * On a guest: why [trackId] can't be played here — a [TogetherServices] key for the service
     * not signed in to, [TogetherServices.LOCAL] for a file only on the host, [TogetherServices.ERROR]
     * otherwise — or null while nothing says it can't.
     */
    fun failureOf(trackId: Long): String?

    /** An address a guest without the service can play [track] by, if one is needed; on the host. */
    suspend fun directUrlFor(track: SoundCloudTrack): String?

    /**
     * [track] as another phone can show it: its cover from the web, where this phone has it as a
     * file of its own (a downloaded track's), which isn't there.
     */
    fun shareable(track: SoundCloudTrack): SoundCloudTrack
}

/** The services a phone listening together may be signed in to, as the phones name them to each other. */
object TogetherServices {
    const val SOUNDCLOUD = "soundcloud"
    const val YANDEX = "yandex"
    const val YOUTUBE = "youtube"
    val ALL = listOf(SOUNDCLOUD, YANDEX, YOUTUBE)

    // Not services: why a track won't play — a file only on the other phone, or something else.
    const val LOCAL = "local"
    const val ERROR = "error"

    /** Which service [track] comes from, as a key of [ALL]; null for a file on a phone. */
    fun of(track: SoundCloudTrack): String? = when {
        track.urn?.startsWith("local:") == true -> null
        track.urn?.startsWith("yandex:") == true -> YANDEX
        track.youTubeVideoId != null || track.liveVideoId != null -> YOUTUBE
        else -> SOUNDCLOUD
    }
}

/** Something one phone listening together should hear about the others. */
sealed interface TogetherNotice {
    /** On the host: a guest can't play [track], for [reason] (see [TogetherPlayer.failureOf]). */
    data class GuestCantPlay(val guest: String, val track: SoundCloudTrack?, val reason: String) : TogetherNotice

    /** On the host: the start waited as long as it may for [guests] to load the track, and went on. */
    data class GuestsTooSlow(val guests: List<String>) : TogetherNotice
}

/**
 * Listening together, on top of [TogetherSession]: the host tells its guests what plays and where
 * — on every change and every few seconds — and does what their buttons ask; a guest plays the same
 * track and keeps to the host's position, by the host's clock (see [ClockSync], [DriftCorrection]).
 *
 * A new track starts on every phone at once: the host holds its own back until each guest has it
 * loaded too (or says it can't play it), then names a moment just ahead for all of them to start
 * at. Guests have the host's next tracks queued, so they load ahead and follow on by themselves;
 * a track that comes on by itself is held only for a guest that isn't there with it.
 */
class ListenTogether(
    private val session: TogetherLink,
    private val player: TogetherPlayer,
    private val scope: CoroutineScope,
    // Both phones' clock: elapsedRealtime, which neither phone's user can set.
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    // The services this phone is signed in to (see [TogetherServices]), told to the host.
    private val signedIn: () -> Set<String> = { TogetherServices.ALL.toSet() }
) {
    val state get() = session.state

    // On a guest: the host's clock, and the last of what it said.
    private val clock = ClockSync()
    private var hostState: TogetherMessage? = null
    private var lastSeq = -1L
    private var followJob: Job? = null
    private var hostJob: Job? = null
    private var startJob: Job? = null
    private var nudged = false
    // What this guest last told the host: the tracks it could start at once, the one it can't play.
    private var toldReady: List<Long>? = null
    private var toldFailed: Long? = null
    // The host's next tracks as last said: a heartbeat without them leaves them as they were.
    private var hostUpcoming: List<Pair<SoundCloudTrack, String?>> = emptyList()

    // On the host: addresses a guest may need for the tracks, worked out once each and shared by
    // whoever asks meanwhile; when each was found, as they are signed for a while only.
    private val directs = HashMap<Long, Pair<Long, String?>>()
    private val resolving = HashMap<Long, Deferred<String?>>()
    private var seq = 0L
    // The guests: the tracks each can start at once, the track each can't play, its services.
    private val readyOf = HashMap<String, List<Long>>()
    private val failedOf = HashMap<String, Long>()
    private val _guestServices = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** On the host: the services each guest is signed in to, by its endpoint; unknown until it says. */
    val guestServices = _guestServices.asStateFlow()
    private var gateJob: Job? = null
    // The track whose start the host holds for its guests, null while none is.
    private var holding: Long? = null
    // The queue last sent, by its tracks' ids, and the heartbeats since: sent again now and then.
    private var sentUpcoming: List<Long>? = null
    private var beatsSinceUpcoming = 0

    /** How much later this phone's sound reaches the ear than the host's (Bluetooth), ms; on a guest. */
    private val _latencyMs = MutableStateFlow(0L)
    val latencyMs = _latencyMs.asStateFlow()
    fun setLatency(ms: Long) {
        _latencyMs.value = ms
    }

    /** How far the guest is from the host, ms, as last measured; null while not following. */
    private val _driftMs = MutableStateFlow<Long?>(null)
    val driftMs = _driftMs.asStateFlow()

    /**
     * A track's start held until every phone has it loaded: on the host while it holds it, on a
     * guest while the host says so. Shown as loading.
     */
    private val _waiting = MutableStateFlow(false)
    val waiting = _waiting.asStateFlow()

    private val _notices = MutableSharedFlow<TogetherNotice>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val notices: SharedFlow<TogetherNotice> = _notices.asSharedFlow()

    init {
        scope.launch {
            session.messages.collect { (from, message) -> onMessage(from, message) }
        }
        scope.launch {
            session.state.collect { state ->
                when (state) {
                    is TogetherState.Hosting -> {
                        startHosting()
                        forgetGone(state.guests.map { it.id }.toSet())
                    }
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
        var after = queue.drop(from).take(MAX_RELAYED_QUEUE).map { player.shareable(it).slim() }
        var message = TogetherMessage(t = "cmd", c = "play", track = player.shareable(track).slim(), queue = after)
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
            launch {
                // …a new track held back until the guests have it…
                player.currentTrackId.collect { id -> if (id != null) onHostTrack(id) }
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
        // Said to start a moment from now: it is where it was said to be till then.
        val expected = if (saidPlaying && now() > saidAt) saidPos + (now() - saidAt) else saidPos
        return abs(player.livePositionMs() - expected) > JUMP_MS
    }

    private fun guests(): List<TogetherPeer> = (session.state.value as? TogetherState.Hosting)?.guests.orEmpty()

    /** Whether every guest can start [trackId] at once, or has said it can't play it. */
    private fun guestsReady(trackId: Long) = guests().all { readyOf[it.id]?.contains(trackId) == true || failedOf[it.id] == trackId }

    /**
     * The host's track changed: unless every guest has it already, its start is held — at once
     * for one picked, a moment later for one that came on by itself (the guests' own players most
     * likely moved on with the host's) — until they have it loaded, then started on all together.
     */
    private fun onHostTrack(trackId: Long) {
        gateJob?.cancel()
        holding = null
        _waiting.value = false
        if (guests().isEmpty() || guestsReady(trackId)) return
        val byItself = player.changedByItself()
        gateJob = scope.launch {
            if (byItself) {
                val until = now() + BY_ITSELF_GRACE_MS
                while (now() < until) {
                    if (guestsReady(trackId)) return@launch
                    delay(GATE_TICK_MS)
                }
            } else {
                // After whatever started the track has done with the player: it plays it, then it
                // is held back here, not the other way round.
                delay(1)
            }
            if (player.currentTrackId.value != trackId || guestsReady(trackId)) return@launch
            // Paused, it has nothing to hold.
            if (!player.wantsToPlay()) return@launch
            holding = trackId
            _waiting.value = true
            player.setPlaying(false)
            broadcastState(withUpcoming = true)
            val until = now() + MAX_HOLD_MS
            while (holding == trackId) {
                delay(GATE_TICK_MS)
                if (player.currentTrackId.value != trackId) return@launch
                // Played by hand meanwhile (here, or a guest's button): it goes on without waiting.
                if (player.wantsToPlay()) {
                    holding = null
                    _waiting.value = false
                    return@launch
                }
                val ready = guestsReady(trackId) && player.isReadyFor(trackId)
                if (ready || now() >= until) {
                    if (!ready) {
                        val slow = guests().filterNot { readyOf[it.id]?.contains(trackId) == true || failedOf[it.id] == trackId }
                        if (slow.isNotEmpty()) _notices.tryEmit(TogetherNotice.GuestsTooSlow(slow.map { it.name }))
                    }
                    startTogether(trackId)
                }
            }
        }
    }

    /** Starts the held track on every phone at once: at a moment just ahead, said to all first. */
    private suspend fun startTogether(trackId: Long) {
        holding = null
        val startAt = now() + START_LEAD_MS
        broadcastState(playing = true, at = startAt)
        delay((startAt - now()).coerceAtLeast(0L))
        _waiting.value = false
        if (player.currentTrackId.value == trackId) player.setPlaying(true)
    }

    /**
     * Tells the guests where the host's music is. [playing] and [at] in place of the player's own:
     * a start said ahead, at a moment to come.
     */
    private suspend fun broadcastState(playing: Boolean? = null, at: Long? = null, withUpcoming: Boolean = false) {
        if (guests().isEmpty()) return
        val track = player.currentTrack.value ?: return
        // Some guest without the service (or not said yet): addresses for the tracks, from here.
        val needDirect = guests().any { TogetherServices.YANDEX !in (_guestServices.value[it.id] ?: emptySet()) }
        val direct = if (needDirect) directUrl(track) else null
        val next = player.upcoming(UPCOMING)
        val nextIds = next.map { it.id }
        beatsSinceUpcoming++
        val sendUpcoming = withUpcoming || nextIds != sentUpcoming || beatsSinceUpcoming >= UPCOMING_EVERY_BEATS
        val nextUrls = if (sendUpcoming && needDirect) next.map { directUrl(it) } else null
        saidPos = player.livePositionMs()
        saidAt = at ?: now()
        saidPlaying = playing ?: player.isPlaying.value
        var message = TogetherMessage(
            t = "state",
            seq = ++seq,
            track = player.shareable(track).slim(),
            url = direct,
            playing = saidPlaying,
            pos = saidPos,
            at = saidAt,
            wait = holding != null,
            xf = player.crossfadeSeconds(),
            queue = if (sendUpcoming) next.map { player.shareable(it).slim() } else null,
            urls = nextUrls
        )
        // A long queue cut down to fit one message, to nothing if it must.
        while (message.queue != null && !session.fits(message)) {
            val shorter = message.queue!!.dropLast(1)
            message = message.copy(queue = shorter, urls = message.urls?.take(shorter.size))
        }
        if (sendUpcoming) {
            sentUpcoming = message.queue?.map { it.id }
            beatsSinceUpcoming = 0
        }
        session.broadcast(message)
    }

    /**
     * The address a guest without the service can play [track] by (see [TogetherPlayer.directUrlFor]),
     * looked up once a while for each track, whoever asks meanwhile waiting for the same answer.
     * Asked from a broadcast that is called off, the lookup goes on for the next one.
     */
    private suspend fun directUrl(track: SoundCloudTrack): String? {
        directs[track.id]?.let { (found, url) -> if (url != null && now() - found < DIRECT_URL_TTL_MS) return url }
        val lookup = resolving.getOrPut(track.id) {
            scope.async { runCatching { player.directUrlFor(track) }.getOrNull() }
        }
        val url = lookup.await()
        resolving.remove(track.id)
        if (url != null) directs[track.id] = now() to url
        return url
    }

    private fun onHostMessage(from: String, message: TogetherMessage) {
        when (message.t) {
            "ping" -> session.send(from, TogetherMessage(t = "pong", a = message.a, h = now()))
            "hello" -> {
                message.svc?.let { _guestServices.value = _guestServices.value + (from to it.toSet()) }
                scope.launch { broadcastState(withUpcoming = true) }
            }
            "ready" -> {
                val ids = message.ids.orEmpty()
                readyOf[from] = ids
                // Playing it after all (a broken stream that came back).
                if (failedOf[from] in ids) failedOf.remove(from)
            }
            "fail" -> {
                val id = message.id ?: return
                if (failedOf[from] == id) return
                failedOf[from] = id
                val name = guests().firstOrNull { it.id == from }?.name ?: return
                val track = player.currentTrack.value?.takeIf { it.id == id }
                _notices.tryEmit(TogetherNotice.GuestCantPlay(name, track, message.why ?: TogetherServices.ERROR))
            }
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

    /** Lets go of what was known of guests that have left. */
    private fun forgetGone(present: Set<String>) {
        readyOf.keys.retainAll(present)
        failedOf.keys.retainAll(present)
        if (!_guestServices.value.keys.all { it in present }) _guestServices.value = _guestServices.value.filterKeys { it in present }
    }

    // ------------------------------------------------------------------------------------ guest

    private fun startFollowing(hostId: String) {
        if (followJob?.isActive == true) return
        hostJob?.cancel()
        clock.clear()
        hostState = null
        lastSeq = -1
        toldReady = null
        toldFailed = null
        hostUpcoming = emptyList()
        // Which services this phone has, for the host to know whom to find addresses for.
        session.send(hostId, TogetherMessage(t = "hello", svc = signedIn().toList()))
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
            // What this phone can start at once, told as soon as it changes: the host waits on it.
            launch {
                while (true) {
                    tellReadiness(hostId)
                    delay(READY_TICK_MS)
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

    /** Tells the host which tracks this phone could start at once, and which of its it can't play. */
    private fun tellReadiness(hostId: String) {
        val hostTrack = hostState?.track?.id
        val ready = listOfNotNull(hostTrack, player.currentTrackId.value).distinct().filter(player::isReadyFor)
        if (ready != toldReady) {
            toldReady = ready
            session.send(hostId, TogetherMessage(t = "ready", ids = ready))
        }
        val failure = hostTrack?.let(player::failureOf)
        if (hostTrack != null && failure != null && toldFailed != hostTrack) {
            toldFailed = hostTrack
            session.send(hostId, TogetherMessage(t = "fail", id = hostTrack, why = failure))
        }
    }

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
                _waiting.value = message.wait == true
                message.xf?.let(player::useCrossfade)
                message.queue?.let { queue -> hostUpcoming = queue.mapIndexed { i, next -> next to message.urls?.getOrNull(i) } }
                startJob?.cancel()
                val hostNow = clock.hostNow(now())
                val playing = message.playing == true
                // A start said ahead: from where it was said, at the moment said.
                val startsLater = playing && hostNow != null && (message.at ?: 0L) > hostNow
                // A new track; or the one this phone has played on to by itself, and kept.
                if (player.currentTrackId.value != track.id || previous == null || retryWith(previous, message)) {
                    val start = if (hostNow == null || startsLater) (message.pos ?: 0L) + _latencyMs.value else expected(message, hostNow)
                    resetSpeed()
                    toldFailed = null
                    player.follow(track, message.url, start.coerceAtLeast(0L), playing && !startsLater, hostUpcoming)
                } else {
                    if (message.queue != null) player.followUpcoming(hostUpcoming)
                    if (!playing && player.wantsToPlay()) player.setPlaying(false)
                    if (playing && !startsLater && !player.wantsToPlay()) player.setPlaying(true)
                }
                if (startsLater) startAt(message)
            }
        }
    }

    /**
     * Whether the host's track, which this phone has but couldn't play, is worth following again:
     * the host has an address for it now, which it hadn't before.
     */
    private fun retryWith(previous: TogetherMessage, message: TogetherMessage): Boolean {
        val id = message.track?.id ?: return false
        return player.failureOf(id) != null && previous.url == null && message.url != null
    }

    /** Plays at the moment the host said, from where it said: paused there till then. */
    private fun startAt(message: TogetherMessage) {
        val id = message.track?.id ?: return
        startJob = scope.launch {
            // Still being found and loaded here, when it is new: what plays now isn't it.
            while (player.currentTrackId.value != id) delay(READY_TICK_MS)
            val from = (message.pos ?: 0L) + _latencyMs.value
            // Paused, a jump costs nothing heard: it is made now, not at the start.
            if (abs(player.livePositionMs() - from) > DriftCorrection.HOLD_MS) player.seekTo(from)
            val hostNow = clock.hostNow(now()) ?: return@launch
            delay(((message.at ?: hostNow) - hostNow).coerceAtLeast(0L))
            player.setPlaying(true)
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
        // Not started yet: it starts on its own, at the moment the host said.
        if (state.playing != true || startJob?.isActive == true || (state.at ?: 0L) > hostNow) {
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
        val wasFollowing = followJob != null
        followJob?.cancel()
        followJob = null
        startJob?.cancel()
        startJob = null
        resetSpeed()
        hostState = null
        hostUpcoming = emptyList()
        _driftMs.value = null
        _waiting.value = false
        if (wasFollowing) player.useCrossfade(null)
    }

    private fun stopAll() {
        stopFollowing()
        hostJob?.cancel()
        hostJob = null
        gateJob?.cancel()
        gateJob = null
        // Left while holding a track back: it plays on, as it would have.
        if (holding != null && player.currentTrackId.value == holding) player.setPlaying(true)
        holding = null
        _waiting.value = false
        directs.clear()
        resolving.clear()
        readyOf.clear()
        failedOf.clear()
        _guestServices.value = emptyMap()
        sentUpcoming = null
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
        private const val READY_TICK_MS = 100L
        private const val PING_EVERY_MS = 10_000L
        private const val PINGS_AT_START = 8
        private const val MAX_RELAYED_QUEUE = 30

        // The host's next tracks a guest has queued, and every how many heartbeats they are said
        // again when they haven't changed (for a guest that missed them).
        private const val UPCOMING = 3
        private const val UPCOMING_EVERY_BEATS = 5

        // A start held for the guests: looked at this often, for this long at most; a track that
        // came on by itself first given this long for the guests' own players to get there.
        private const val GATE_TICK_MS = 50L
        private const val MAX_HOLD_MS = 15_000L
        private const val BY_ITSELF_GRACE_MS = 1_500L

        // How far ahead a start is named: time for the message to reach every guest.
        private const val START_LEAD_MS = 350L

        // How long the address found for a guest is used before it is looked up again.
        private const val DIRECT_URL_TTL_MS = 20 * 60_000L
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
