package com.example.myapplication.player.restore

import android.content.Context

/**
 * What restoration found in each track the last time it played — its cutoff, or 0 for a file that
 * reaches the top — so that the next time it is restored from its first second, not after the
 * second or two it takes to listen. The sound itself is never kept: it is restored as it plays,
 * for less than a hundredth of the phone's processor, and a restored copy would be compressed again.
 */
class RestoreVerdicts(context: Context) {
    private val preferences = context.getSharedPreferences("restore_verdicts", Context.MODE_PRIVATE)

    fun get(mediaId: String): Float? =
        if (preferences.contains(mediaId)) preferences.getFloat(mediaId, 0f) else null

    fun put(mediaId: String, cutoffHz: Float) {
        if (get(mediaId) == cutoffHz) return
        // Kept small: past five thousand tracks it starts over, and they are listened to afresh.
        if (preferences.all.size > MAX) preferences.edit().clear().apply()
        preferences.edit().putFloat(mediaId, cutoffHz).apply()
    }

    private companion object {
        const val MAX = 5_000
    }
}
