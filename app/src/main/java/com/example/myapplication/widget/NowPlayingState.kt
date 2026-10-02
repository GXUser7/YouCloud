package com.example.myapplication.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What the widget shows, as the playback service last told it: kept on disk, as the widget is
 * drawn again whenever the system asks — added to the home screen, after a restart — the service
 * long gone perhaps.
 */
object NowPlayingState {
    private const val PREFS = "now_playing_widget"

    data class Snapshot(
        val title: String? = null,
        val artist: String? = null,
        val artwork: String? = null,
        val playing: Boolean = false,
        // Something is queued: the player's buttons mean something.
        val active: Boolean = false
    )

    private var last: Snapshot? = null

    // What the widgets on screen follow while their session runs: told again, a session only
    // recomposes, and what it read once at its start would stay.
    @Volatile
    private var current: kotlinx.coroutines.flow.MutableStateFlow<Snapshot>? = null

    fun flow(context: Context): kotlinx.coroutines.flow.StateFlow<Snapshot> = (current ?: synchronized(this) {
        current ?: kotlinx.coroutines.flow.MutableStateFlow(read(context)).also { current = it }
    })
    private var pending: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun read(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Told by another run of the app: its player went with it, and nothing plays.
        if (prefs.getInt("pid", 0) != android.os.Process.myPid()) return Snapshot()
        return Snapshot(
            title = prefs.getString("title", null),
            artist = prefs.getString("artist", null),
            artwork = prefs.getString("artwork", null),
            playing = prefs.getBoolean("playing", false),
            active = prefs.getBoolean("active", false)
        )
    }

    /**
     * Shows [snapshot] on the widgets. Changes that come in a burst (a track ending: paused,
     * buffering, the next one, playing) are shown once, as they settle.
     */
    fun publish(context: Context, snapshot: Snapshot) {
        if (snapshot == last) return
        last = snapshot
        current?.value = snapshot
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", snapshot.title)
            .putString("artist", snapshot.artist)
            .putString("artwork", snapshot.artwork)
            .putBoolean("playing", snapshot.playing)
            .putBoolean("active", snapshot.active)
            .putInt("pid", android.os.Process.myPid())
            .apply()
        pending?.cancel()
        pending = scope.launch {
            delay(SETTLE_MS)
            runCatching { NowPlayingWidget().updateAll(app) }
        }
    }

    private const val SETTLE_MS = 300L
}
