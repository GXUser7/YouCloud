package com.example.myapplication.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.runComposition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Draws the widgets: each once, with what plays as it is then, whenever that changes ([render])
 * and whenever the system asks — a widget added, resized, the launcher started again.
 *
 * Glance composes them, but no session of Glance's stays to follow what plays: one did, and after
 * each change — a tap on play, a track ending — sent the widgets to the launcher hundreds of times
 * over several seconds, which redrew them all the while and let their buttons answer late, if at
 * all. Here a change is one composition and one update a widget, by construction: the first
 * result of [runComposition] is sent, and the composition closed.
 */
internal object WidgetRenderer {
    private const val TAG = "WidgetRenderer"

    // Changes that come in a burst (a track ending: paused, buffering, the next one, playing) are
    // drawn once, as they settle.
    private const val SETTLE_MS = 250L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pending: Job? = null

    /**
     * Draws every widget again after [delayMs], a later call taking the place of one still
     * waiting. [done] runs either way, drawn or taken over: a receiver's goAsync is let go there.
     */
    @OptIn(ExperimentalGlanceApi::class)
    fun render(context: Context, delayMs: Long = SETTLE_MS, done: () -> Unit = {}) {
        val app = context.applicationContext
        pending?.cancel()
        pending = scope.launch {
            try {
                delay(delayMs)
                val manager = AppWidgetManager.getInstance(app)
                val ids = manager.getAppWidgetIds(ComponentName(app, NowPlayingWidgetReceiver::class.java))
                val glance = GlanceAppWidgetManager(app)
                val widget = NowPlayingWidget()
                for (id in ids) {
                    try {
                        val views = widget.runComposition(app, glance.getGlanceIdBy(id), manager.getAppWidgetOptions(id)).first()
                        manager.updateAppWidget(id, views)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Widget $id not drawn", e)
                    }
                }
            } finally {
                done()
            }
        }
    }
}
