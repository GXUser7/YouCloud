package com.example.myapplication.player

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.Executors

/**
 * Every track heard, for "Итоги": what, by whom, from which service, when and for how long. The
 * playback service writes it, as it hears everything — in the car, from the notification, with the
 * app's screen long closed: a line of JSON a play, in a file only ever added to.
 */
object PlayLog {
    /**
     * [source]: "yandex", "youtube", "soundcloud", "phone", or "" when unknown; [ms]: how long it
     * played, in real time; [at]: when it began, in epoch ms.
     */
    data class Play(
        val id: Long,
        val title: String,
        val artist: String,
        val artwork: String?,
        val source: String,
        val at: Long,
        val ms: Long
    )

    // What a media item carries for the log: its first artist alone, and its service.
    const val EXTRA_LEAD_ARTIST = "lead_artist"
    const val EXTRA_SOURCE = "source"

    private val gson = Gson()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "play log").apply { isDaemon = true } }

    private fun file(context: Context) = File(context.filesDir, "plays.jsonl")

    fun add(context: Context, play: Play) {
        val target = file(context)
        writer.execute { runCatching { target.appendText(gson.toJson(play) + "\n") } }
    }

    /** Every play logged, oldest first. Blocking: a file read; off the main thread. */
    fun read(context: Context): List<Play> {
        val source = file(context).takeIf { it.exists() } ?: return emptyList()
        return source.useLines { lines ->
            lines.mapNotNull { line -> runCatching { gson.fromJson(line, Play::class.java) }.getOrNull() }
                .filter { it.title.isNotBlank() }
                .toList()
        }
    }

    /**
     * Follows [player]: how long each track plays, counted while it does, and the track logged
     * once it gives way — when it was heard for half a minute, or half of it if it is shorter.
     */
    class Tracker(private val context: Context, private val player: Player) : Player.Listener {
        private var item: MediaItem? = null
        private var startedAt = 0L
        private var heardMs = 0L
        private var since = -1L
        // The track's length, once the player knows it: by the time it gives way, the player
        // tells the next one's.
        private var itemDuration = 0L

        init {
            begin(player.currentMediaItem)
            if (player.isPlaying) since = SystemClock.elapsedRealtime()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                if (since < 0) since = SystemClock.elapsedRealtime()
            } else {
                count()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (player.currentMediaItem == item && player.duration > 0) itemDuration = player.duration
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            count()
            finish()
            begin(mediaItem)
            if (player.isPlaying) since = SystemClock.elapsedRealtime()
        }

        /** The playback service going away: what plays now is logged as far as it got. */
        fun release() {
            count()
            finish()
            player.removeListener(this)
        }

        private fun count() {
            if (since >= 0) heardMs += SystemClock.elapsedRealtime() - since
            since = -1L
        }

        private fun begin(next: MediaItem?) {
            item = next
            startedAt = System.currentTimeMillis()
            heardMs = 0L
            itemDuration = 0L
        }

        private fun finish() {
            val heard = item ?: return
            item = null
            val id = heard.mediaId.toLongOrNull() ?: return
            val metadata = heard.mediaMetadata
            val scheme = heard.localConfiguration?.uri?.scheme
            // Broadcasts run for hours: not a track heard.
            if (scheme == "ytlive") return
            val enough = if (itemDuration > 0) minOf(MIN_HEARD_MS, itemDuration / 2) else MIN_HEARD_MS
            if (heardMs < enough) return
            val extras = metadata.extras
            val artist = extras?.getString(EXTRA_LEAD_ARTIST)
                ?: metadata.artist?.toString()?.substringBefore(", ")
                ?: return
            add(
                context,
                Play(
                    id = id,
                    title = metadata.title?.toString() ?: return,
                    artist = artist,
                    artwork = metadata.artworkUri?.toString(),
                    source = extras?.getString(EXTRA_SOURCE) ?: sourceOf(scheme),
                    at = startedAt,
                    ms = heardMs
                )
            )
        }

        private fun sourceOf(scheme: String?): String = when (scheme) {
            "yandex" -> "yandex"
            "ytmusic" -> "youtube"
            "soundcloud" -> "soundcloud"
            else -> ""
        }
    }

    private const val MIN_HEARD_MS = 30_000L
}
