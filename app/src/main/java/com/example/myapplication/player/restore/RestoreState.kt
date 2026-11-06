package com.example.myapplication.player.restore

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether what is heard now is being restored — for "+AI" in the player. Each player that restores
 * (the main one, and the crossfade's other while it plays the next track) says so for itself.
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
}
