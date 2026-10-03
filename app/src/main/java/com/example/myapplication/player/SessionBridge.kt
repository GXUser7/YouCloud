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

    /**
     * The crossfade, in seconds, in place of the one in the settings; null to keep those. A guest
     * listening together crossfades as its host does: they hear the same.
     */
    val crossfadeOverride = MutableStateFlow<Int?>(null)

    /**
     * The end of a crossfade: the main player finding its place in the new track, unheard, while
     * the crossfade's other player plays it. It may stop to load meanwhile; the app goes on showing
     * it playing, which, to the ear, it is.
     */
    val crossfadeSettling = MutableStateFlow(false)

    /**
     * A crossfade's track coming in, from the moment it starts to be heard until the main player
     * has taken it over: the app shows it as the one playing from then, not seconds later when
     * the track before has ended. [position]: where it is in it, read on the main thread.
     */
    class CrossfadeIncoming(val mediaId: String, val durationMs: Long, val position: () -> Long)

    val crossfadeIncoming = MutableStateFlow<CrossfadeIncoming?>(null)

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
