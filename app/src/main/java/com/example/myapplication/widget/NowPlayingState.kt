package com.example.myapplication.widget

import android.content.Context

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

    // What the widgets are drawn with (see WidgetRenderer): the last told, in memory; read from
    // disk at first, for a widget drawn before the service has told anything this run.
    @Volatile
    private var current: kotlinx.coroutines.flow.MutableStateFlow<Snapshot>? = null

    fun flow(context: Context): kotlinx.coroutines.flow.StateFlow<Snapshot> = (current ?: synchronized(this) {
        current ?: kotlinx.coroutines.flow.MutableStateFlow(read(context)).also { current = it }
    })

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
     * buffering, the next one, playing) are shown once, as they settle (see WidgetRenderer).
     */
    fun publish(context: Context, snapshot: Snapshot) {
        if (snapshot == last) return
        last = snapshot
        current?.value = snapshot
        val app = context.applicationContext
        // The cover made while the widgets are drawn; they are drawn again once it is ready.
        WidgetCover.follow(app, snapshot.artwork?.takeIf { snapshot.active })
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", snapshot.title)
            .putString("artist", snapshot.artist)
            .putString("artwork", snapshot.artwork)
            .putBoolean("playing", snapshot.playing)
            .putBoolean("active", snapshot.active)
            .putInt("pid", android.os.Process.myPid())
            .apply()
        WidgetRenderer.render(app)
    }
}
