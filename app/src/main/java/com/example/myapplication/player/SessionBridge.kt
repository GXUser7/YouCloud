package com.example.myapplication.player

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * What the notification, the lock screen and the car show besides play and skip: "Нравится" on the
 * track playing, and "Не нравится" while "Моя волна" plays. The app says what they show — whether
 * the track is liked, whether the wave plays — and does what they ask: a like downloads the track
 * and likes it on its service, and a dislike tells the wave, which only the app knows how to do.
 */
object SessionBridge {
    sealed interface Action {
        val trackId: Long

        data class Like(override val trackId: Long) : Action
        data class Dislike(override val trackId: Long) : Action
    }

    /** The track playing, by its id, and whether it is liked. */
    val liked = MutableStateFlow<Pair<Long, Boolean>?>(null)

    /** Whether "Не нравится" means anything now: the wave is playing. */
    val canDislike = MutableStateFlow(false)

    // Kept until the app takes them: pressed while its screen was closed, a like still counts.
    private val pending = Channel<Action>(capacity = 16)
    val actions: Flow<Action> = pending.receiveAsFlow()

    fun ask(action: Action) {
        pending.trySend(action)
        // Shown at once, before the app has done it.
        if (action is Action.Like) {
            val now = liked.value?.takeIf { it.first == action.trackId }?.second ?: false
            liked.value = action.trackId to !now
        }
    }
}
