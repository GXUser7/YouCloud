package com.example.myapplication.data.social

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlin.math.abs

/**
 * Tells friends what the playback service plays: when the track changes, pauses, resumes or is
 * moved through, a moment after it settles (skipping through a queue says only where it stops),
 * and every few minutes while it plays, so that a row left behind by a phone that went quiet
 * reads as old (see [Person.listeningNow]).
 *
 * Lives in the service, not the screen: music goes on with the app closed, and so does this.
 */
internal class NowPlayingPublisher(context: Context, private val player: Player) : Player.Listener {

    /** What is told: a track, where it is from and where to find it, and whether it plays. */
    data class Snapshot(
        val title: String,
        val artist: String?,
        val coverUrl: String?,
        val service: String?,
        val trackUrl: String?,
        val playing: Boolean,
        val startedAtMs: Long?,
        val durationMs: Long?
    )

    private val social = Social.get(context)
    private val handler = Handler(Looper.getMainLooper())
    private val tell = Runnable { send() }
    private var told: Snapshot? = null
    private var toldAt = 0L

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_MEDIA_METADATA_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY
            )
        ) {
            schedule(SETTLE_MS)
        }
    }

    /** The service is going: what was playing isn't any more. */
    fun release() {
        handler.removeCallbacks(tell)
        told?.takeIf { it.playing }?.let { social.publishNowPlaying(it.copy(playing = false)) }
        told = null
    }

    private fun schedule(delayMs: Long) {
        handler.removeCallbacks(tell)
        handler.postDelayed(tell, delayMs)
    }

    private fun send() {
        val now = System.currentTimeMillis()
        val snapshot = snapshot(now)
        if (snapshot == null) {
            // The queue emptied: what was told as playing has stopped.
            told?.takeIf { it.playing }?.let { social.publishNowPlaying(it.copy(playing = false)) }
            told = null
            return
        }
        // While it plays, again in a while, whether anything changes or not.
        if (snapshot.playing) schedule(HEARTBEAT_MS)
        if (sameAs(told, snapshot) && now - toldAt < HEARTBEAT_MS - SETTLE_MS) return
        told = snapshot
        toldAt = now
        social.publishNowPlaying(snapshot)
    }

    private fun snapshot(now: Long): Snapshot? {
        val item = player.currentMediaItem ?: return null
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val uri = item.localConfiguration?.uri
        val scheme = uri?.scheme
        val service = when (scheme) {
            "soundcloud" -> "soundcloud"
            "yandex" -> "yandex"
            "ytmusic", "ytlive" -> "youtube"
            "file", "content" -> "device"
            // A SoundCloud stream the app resolved already.
            else -> if (uri?.host?.contains("sndcdn") == true || uri?.host?.contains("soundcloud") == true) "soundcloud" else null
        }
        // A cover on the phone (a file's) is no use to anyone else.
        val cover = metadata.artworkUri?.toString()?.takeIf { it.startsWith("http") }
        val playing = player.playWhenReady &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
        return Snapshot(
            title = title,
            artist = metadata.artist?.toString(),
            coverUrl = cover,
            service = service,
            // The item's own metadata: what the app gave it (see MusicPlayer), whatever the stream adds.
            trackUrl = (item.mediaMetadata.extras ?: metadata.extras)?.getString(EXTRA_LINK),
            playing = playing,
            startedAtMs = if (playing) now - player.currentPosition.coerceAtLeast(0L) else null,
            durationMs = duration
        )
    }

    /** The same as told, but for the start drifting by the moments it took to tell. */
    private fun sameAs(a: Snapshot?, b: Snapshot): Boolean {
        if (a == null) return false
        if (a.copy(startedAtMs = null) != b.copy(startedAtMs = null)) return false
        val start = a.startedAtMs
        val again = b.startedAtMs
        return (start == null && again == null) || (start != null && again != null && abs(start - again) < 3_000L)
    }

    companion object {
        /** In a media item's metadata extras: the track's page, for a friend to play it too. */
        const val EXTRA_LINK = "track_link"

        private const val SETTLE_MS = 2_500L
        private const val HEARTBEAT_MS = 5 * 60_000L

        /** A track told about longer ago than this, still "playing", is taken as gone quiet. */
        const val STALE_AFTER_MS = 12 * 60_000L
    }
}
