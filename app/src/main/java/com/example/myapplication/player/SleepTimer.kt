package com.example.myapplication.player

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The sleep timer: the music fades out and stops after a while, or at the end of the track
 * playing. Set from the player on screen, kept by the playback service ([PlaybackFades]): the
 * screen may be long gone by the time it runs out.
 */
object SleepTimer {
    sealed interface State {
        data object Off : State

        /** Stops at [endsAt] (`SystemClock.elapsedRealtime`), [minutes] after it was set. */
        data class At(val endsAt: Long, val minutes: Int) : State

        /** Stops once the track playing has ended. */
        data object EndOfTrack : State
    }

    // How long the music takes to fade out before it stops: long enough not to be noticed going.
    const val FADE_MS = 30_000L

    // At the end of a track it fades over its last seconds instead.
    const val TRACK_END_FADE_MS = 8_000L

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state.asStateFlow()

    fun set(minutes: Int) {
        _state.value = State.At(SystemClock.elapsedRealtime() + minutes * 60_000L, minutes)
    }

    fun endOfTrack() {
        _state.value = State.EndOfTrack
    }

    fun cancel() {
        _state.value = State.Off
    }

    /** Minutes left, rounded up; null unless it is set to a time. */
    fun minutesLeft(): Int? {
        val at = _state.value as? State.At ?: return null
        val left = at.endsAt - SystemClock.elapsedRealtime()
        return ((left + 59_999) / 60_000).toInt().coerceAtLeast(0)
    }
}
