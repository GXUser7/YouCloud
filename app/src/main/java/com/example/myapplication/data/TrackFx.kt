package com.example.myapplication.data

/**
 * How a track is played back, kept for that track: its [speed] (1 as recorded), whether its pitch
 * stays put at another speed ([keepPitch]; otherwise it goes down slowed and up sped up, as a
 * record's does), and how much [reverb] it has, 0 (none) to 100.
 */
data class TrackFx(
    val speed: Float = 1f,
    val keepPitch: Boolean = false,
    val reverb: Int = 0
) {
    val isDefault: Boolean
        get() = speed == 1f && reverb == 0

    fun encode(): String = "$speed;${if (keepPitch) 1 else 0};$reverb"

    companion object {
        /** Where a track's effects are kept in the app's settings, by its id. */
        const val KEY_PREFIX = "track_fx_"

        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 1.5f

        fun decode(value: String?): TrackFx {
            val parts = value?.split(';') ?: return TrackFx()
            return TrackFx(
                speed = parts.getOrNull(0)?.toFloatOrNull()?.coerceIn(MIN_SPEED, MAX_SPEED) ?: 1f,
                keepPitch = parts.getOrNull(1) == "1",
                reverb = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 100) ?: 0
            )
        }
    }
}
