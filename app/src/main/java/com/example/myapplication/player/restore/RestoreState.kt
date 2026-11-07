package com.example.myapplication.player.restore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether what is heard now is being restored. Each player that restores (the main one, and the
 * crossfade's other while it plays the next track) says so for itself ([enhancing]).
 *
 * "+AI" in the player reads [badge] instead: decided once a track, and held until the next — the
 * player is the screen where every needless change costs, and a mark that came and went with the
 * sound (a quiet passage, a fade) was one. It comes in with the track when the track's verdict is
 * kept from before; heard for the first time, it is decided once the track has been listened to,
 * and simply there then, without sliding in.
 */
object RestoreState {
    private val owners = MutableStateFlow(emptySet<String>())
    private val _enhancing = MutableStateFlow(false)
    val enhancing: StateFlow<Boolean> = _enhancing.asStateFlow()

    fun report(owner: String, on: Boolean) {
        while (true) {
            val now = owners.value
            val next = if (on) now + owner else now - owner
            if (owners.compareAndSet(now, next)) {
                _enhancing.value = next.isNotEmpty()
                return
            }
        }
    }

    /**
     * "+AI" for the track with [mediaId]: whether it is [restored]; [fromStart] when that was known
     * as it began (so the mark comes in with it); [decided] false while it is still being listened to.
     */
    data class Badge(val mediaId: String, val restored: Boolean, val fromStart: Boolean, val decided: Boolean)

    private val _badge = MutableStateFlow<Badge?>(null)
    val badge: StateFlow<Badge?> = _badge.asStateFlow()

    /** A track has begun: [restored] as kept from before, or null when it isn't known yet. */
    fun trackStarted(mediaId: String, restored: Boolean?) {
        _badge.value = Badge(mediaId, restored == true, fromStart = true, decided = restored != null)
    }

    /** What listening to [mediaId] found — taken only while nothing has been decided for it yet. */
    fun decided(mediaId: String, restored: Boolean) {
        val now = _badge.value ?: return
        if (now.mediaId == mediaId && !now.decided) _badge.value = Badge(mediaId, restored, fromStart = false, decided = true)
    }

    /** Nothing is restored for now (switched off, or the sound is on a TV): no mark until the next track. */
    fun off(mediaId: String?) {
        _badge.value = mediaId?.let { Badge(it, restored = false, fromStart = false, decided = true) }
    }
}
