package com.example.myapplication.together

import com.example.myapplication.data.SoundCloudTrack
import kotlin.math.abs

/**
 * What the phones listening together say to each other, as JSON: the host tells where its music
 * is, the guests answer pings and send what they press. One shape for all, its fields filled as
 * [t] needs them:
 *
 * - `hello` guest → host: [name].
 * - `ping` guest → host, [a] its clock; `pong` host → guest, [a] echoed and [h] the host's clock.
 * - `state` host → guests: [track] playing ([playing]) at [pos] ms as of [at] on the host's clock,
 *   [seq] counting up; [url] an address the guest may play it by when it can't find the track
 *   itself (a Yandex track for a guest without Yandex Music).
 * - `cmd` guest → host: [c] "toggle", "next", "prev", "seek" (to [pos]) or "play" ([track] and
 *   [queue] after it).
 */
data class TogetherMessage(
    val t: String,
    val name: String? = null,
    val a: Long? = null,
    val h: Long? = null,
    val seq: Long? = null,
    val track: SoundCloudTrack? = null,
    val queue: List<SoundCloudTrack>? = null,
    val url: String? = null,
    val playing: Boolean? = null,
    val pos: Long? = null,
    val at: Long? = null,
    val c: String? = null
)

/**
 * The host's clock as seen from a guest: from ping round trips, the offset that took the least
 * time — the one the network delayed least, and so the truest. Clocks are `elapsedRealtime`,
 * which neither phone's user can change.
 */
class ClockSync(private val keep: Int = 12) {
    private class Sample(val rtt: Long, val offset: Long)

    private val samples = ArrayDeque<Sample>()

    /** A ping sent at [sentAt] (guest's clock), answered with [hostAt], back at [receivedAt]. */
    fun add(sentAt: Long, hostAt: Long, receivedAt: Long) {
        val rtt = receivedAt - sentAt
        if (rtt < 0) return
        samples.addLast(Sample(rtt, hostAt - (sentAt + receivedAt) / 2))
        while (samples.size > keep) samples.removeFirst()
    }

    private fun best() = samples.minByOrNull { it.rtt }

    /** Host clock minus guest clock, ms; null before the first answer. */
    val offsetMs: Long? get() = best()?.offset

    /** The quickest round trip, ms: how far [offsetMs] may be off, twice over at most. */
    val rttMs: Long? get() = best()?.rtt

    fun hostNow(guestNow: Long): Long? = offsetMs?.let { guestNow + it }

    fun clear() = samples.clear()
}

/** What a guest's player does to be where the host's is. */
sealed interface DriftAction {
    /** Close enough: at normal speed. */
    data object Hold : DriftAction

    /** A little behind or ahead: catch up or let it catch up, too slightly to be heard. */
    data class Nudge(val speed: Float) : DriftAction

    /** Too far to catch up: jump, a little ahead for the time the jump takes. */
    data class Seek(val toMs: Long) : DriftAction
}

object DriftCorrection {
    // Within this the ear can't tell two phones apart.
    const val HOLD_MS = 35L
    // Past this a seek is quicker than catching up, and a seek's hiccup is better than minutes out.
    const val SEEK_MS = 700L
    // What a seek takes to sound again, on a track already being played.
    const val SEEK_LEAD_MS = 150L
    // Sped up or slowed down by 5% at most: the pitch is kept, and the difference unheard.
    const val MAX_NUDGE = 0.05f

    /**
     * Where the host's track is now: [pos] at [at] on its clock, moved on since if it plays.
     * [latencyMs] is how much later this phone's sound reaches the ear than the host's (Bluetooth
     * headphones): played that much ahead.
     */
    fun expectedPosition(pos: Long, at: Long, playing: Boolean, hostNow: Long, latencyMs: Long): Long =
        (if (playing) pos + (hostNow - at) else pos) + latencyMs

    fun decide(localMs: Long, expectedMs: Long): DriftAction {
        val behind = expectedMs - localMs
        return when {
            abs(behind) >= SEEK_MS -> DriftAction.Seek((expectedMs + SEEK_LEAD_MS).coerceAtLeast(0L))
            abs(behind) <= HOLD_MS -> DriftAction.Hold
            // A second of speed at 1 + x gains x of a second: the gap shrinks by a quarter or so a
            // second, from half a second out to level in about ten.
            else -> DriftAction.Nudge(1f + (behind / 4_000f).coerceIn(-MAX_NUDGE, MAX_NUDGE))
        }
    }
}
