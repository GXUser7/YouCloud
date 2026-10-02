package com.example.myapplication.together

import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudUser
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * A host and a guest listening together, in one process: their messages cross a network that
 * takes its time (and not always the same), the host's clock is seconds apart from the guest's,
 * and the guest's player takes a moment to start a track — what two real phones do.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherTest {
    private val trackA = SoundCloudTrack(id = 1, urn = "soundcloud:tracks:1", title = "A", duration = 300_000)
    private val trackB = SoundCloudTrack(id = 2, urn = "soundcloud:tracks:2", title = "B", duration = 300_000)

    /** A player whose position moves with virtual time, at its speed, once it has loaded. */
    private class FakePlayer(
        private val scope: CoroutineScope,
        private val now: () -> Long,
        private val loadMs: Long = 0,
        // What a jump takes to sound again: a guest's player reaching the new place.
        private val seekMs: Long = 0
    ) : TogetherPlayer {
        override val currentTrack = MutableStateFlow<SoundCloudTrack?>(null)
        override val isPlaying = MutableStateFlow(false)
        override val currentTrackId = MutableStateFlow<Long?>(null)
        override val isBuffering = MutableStateFlow(false)
        private var pos = 0L
        private var at = 0L
        private var startsAt = 0L
        var speed = 1f
            private set
        val played = mutableListOf<Pair<SoundCloudTrack, List<SoundCloudTrack>>>()
        var followedUrl: String? = null

        override fun livePositionMs(): Long {
            val t = now()
            if (!isPlaying.value || t <= startsAt) return pos
            return pos + ((t - max(at, startsAt)) * speed).toLong()
        }

        private fun rebase() {
            pos = livePositionMs()
            at = now()
        }

        private fun start(track: SoundCloudTrack, fromMs: Long, playing: Boolean) {
            currentTrack.value = track
            currentTrackId.value = track.id
            pos = fromMs
            at = now()
            startsAt = now() + loadMs
            isPlaying.value = playing
            if (loadMs > 0) {
                isBuffering.value = true
                scope.launch {
                    delay(loadMs)
                    isBuffering.value = false
                }
            }
        }

        override fun togglePlayPause() = setPlaying(!isPlaying.value)
        override fun skip(next: Boolean) = start(if (next) trackB else trackA, 0, true)
        override fun seekTo(positionMs: Long) {
            pos = positionMs
            at = now()
            startsAt = now() + seekMs
        }

        override fun playQueue(track: SoundCloudTrack, queue: List<SoundCloudTrack>) {
            played += track to queue
            start(track, 0, true)
        }

        override fun follow(track: SoundCloudTrack, url: String?, startMs: Long, playing: Boolean) {
            followedUrl = url
            start(track, startMs, playing)
        }

        override fun setPlaying(playing: Boolean) {
            rebase()
            isPlaying.value = playing
        }

        override fun setSpeed(speed: Float) {
            rebase()
            this.speed = speed
        }

        override suspend fun directUrlFor(track: SoundCloudTrack): String? = "https://direct/${track.id}"

        private val trackB get() = SoundCloudTrack(id = 2, urn = "soundcloud:tracks:2", title = "B")
        private val trackA get() = SoundCloudTrack(id = 1, urn = "soundcloud:tracks:1", title = "A")
    }

    /** One end of a network between "H" (host) and "G" (guest) whose messages take a while. */
    private class FakeLink(
        private val scope: CoroutineScope,
        private val me: String,
        initial: TogetherState
    ) : TogetherLink {
        lateinit var other: FakeLink
        override val state = MutableStateFlow(initial)
        override val messages = MutableSharedFlow<Pair<String, TogetherMessage>>(extraBufferCapacity = 64)
        private var sent = 0
        val received = mutableListOf<TogetherMessage>()

        private fun deliver(message: TogetherMessage) {
            // 20 ms one way, now and then 120: Bluetooth on a busy band.
            val latency = if (sent++ % 4 == 3) 120L else 20L
            scope.launch {
                delay(latency)
                // Through JSON, as over the air.
                val copy = Gson().fromJson(Gson().toJson(message), TogetherMessage::class.java)
                other.received += copy
                other.messages.emit(me to copy)
            }
        }

        override fun send(to: String, message: TogetherMessage) = deliver(message)
        override fun broadcast(message: TogetherMessage) = deliver(message)
        override fun fits(message: TogetherMessage) = Gson().toJson(message).length <= 30_000
        override fun leave() {
            state.value = TogetherState.Idle
        }
    }

    private class Pair2(val host: ListenTogether, val guest: ListenTogether, val hostPlayer: FakePlayer, val guestPlayer: FakePlayer, val guestLink: FakeLink)

    private fun TestScope.setUp(guestLoadMs: Long = 400): Pair2 {
        val scope = backgroundScope
        // The host's clock 7 s ahead of the guest's: the phones were switched on at other times.
        val hostNow = { testScheduler.currentTime + 7_000 }
        val guestNow = { testScheduler.currentTime }
        val hostLink = FakeLink(scope, "H", TogetherState.Hosting(listOf(TogetherPeer("G", "Гость"))))
        val guestLink = FakeLink(scope, "G", TogetherState.Idle)
        hostLink.other = guestLink
        guestLink.other = hostLink
        val hostPlayer = FakePlayer(scope, hostNow)
        val guestPlayer = FakePlayer(scope, guestNow, loadMs = guestLoadMs, seekMs = 150)
        val host = ListenTogether(hostLink, hostPlayer, scope, hostNow)
        val guest = ListenTogether(guestLink, guestPlayer, scope, guestNow)
        hostPlayer.playQueue(trackA, listOf(trackA))
        hostPlayer.seekTo(30_000)
        guestLink.state.value = TogetherState.Joined(TogetherPeer("H", "Ведущий"))
        return Pair2(host, guest, hostPlayer, guestPlayer, guestLink)
    }

    private fun gap(p: Pair2) = abs(p.hostPlayer.livePositionMs() - p.guestPlayer.livePositionMs())

    @Test
    fun guestPlaysTheHostsTrackLevelWithIt() = runTest {
        val p = setUp()
        advanceTimeBy(1_500)
        assertEquals(trackA.id, p.guestPlayer.currentTrackId.value)
        assertTrue(p.guestPlayer.isPlaying.value)
        assertEquals("https://direct/1", p.guestPlayer.followedUrl)
        // A track takes 400 ms to start on the guest: caught up within half a minute, and kept.
        advanceTimeBy(30_000)
        assertTrue("gap ${gap(p)} ms", gap(p) <= 60)
        advanceTimeBy(60_000)
        assertTrue("gap ${gap(p)} ms", gap(p) <= 60)
        assertEquals(1f, p.guestPlayer.speed)
    }

    @Test
    fun aJumpOnTheHostIsFollowed() = runTest {
        val p = setUp(guestLoadMs = 0)
        advanceTimeBy(10_000)
        p.hostPlayer.seekTo(120_000)
        // Told at once, not at the next heartbeat; the guest's jump lands where the host is.
        advanceTimeBy(2_000)
        assertTrue("gap ${gap(p)} ms", gap(p) <= 60)
    }

    @Test
    fun aGuestsButtonsDriveTheHost() = runTest {
        val p = setUp(guestLoadMs = 0)
        advanceTimeBy(3_000)
        assertTrue(p.guest.relay("toggle"))
        advanceTimeBy(1_000)
        assertFalse(p.hostPlayer.isPlaying.value)
        assertFalse(p.guestPlayer.isPlaying.value)

        assertTrue(p.guest.relayPlay(trackB, listOf(trackA, trackB)))
        advanceTimeBy(1_500)
        assertEquals(trackB.id, p.hostPlayer.played.last().first.id)
        assertEquals(listOf(trackB.id), p.hostPlayer.played.last().second.map { it.id })
        assertEquals(trackB.id, p.guestPlayer.currentTrackId.value)
        assertTrue(p.guestPlayer.isPlaying.value)
    }

    @Test
    fun notAGuestRelaysNothing() = runTest {
        val p = setUp()
        p.guestLink.state.value = TogetherState.Idle
        assertFalse(p.guest.relay("toggle"))
        assertFalse(p.guest.relayPlay(trackB, listOf(trackB)))
    }

    @Test
    fun aMessageCrossesAsJson() {
        val track = trackA.copy(
            user = SoundCloudUser(id = 5, username = "Artist", description = "long"),
            artists = listOf(SoundCloudUser(username = "One"), SoundCloudUser(username = "Two"))
        )
        val message = TogetherMessage(t = "state", seq = 3, track = track.slim(), playing = true, pos = 1234, at = 99)
        val back = Gson().fromJson(Gson().toJson(message), TogetherMessage::class.java)
        assertEquals(message, back)
        assertEquals(null, back.track?.user?.description)
        assertEquals(listOf("One", "Two"), back.track?.artists?.map { it.username })
    }
}
