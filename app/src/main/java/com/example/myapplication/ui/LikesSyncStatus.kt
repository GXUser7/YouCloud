package com.example.myapplication.ui

enum class SyncState {
    IDLE,
    FETCHING_LIKES,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

enum class LikesSyncSource {
    SOUNDCLOUD,
    YANDEX
}

data class LikesSyncStatus(
    val state: SyncState = SyncState.IDLE,
    val currentTrackIndex: Int = 0,
    val totalTracks: Int = 0,
    val downloadedCount: Int = 0,
    val failedCount: Int = 0,
    val currentTrackTitle: String = "",
    val errorMessage: String? = null
)

enum class LikesPushState {
    IDLE,
    CHECKING,
    SENDING,
    // Stopped part way — refused by SoundCloud or no network. What's left stays queued and goes
    // out on its own later, or on the next press.
    PAUSED,
    COMPLETED,
    FAILED
}

/** Progress of liking on SoundCloud the downloaded tracks whose likes never got there. */
data class LikesPushStatus(
    val state: LikesPushState = LikesPushState.IDLE,
    val total: Int = 0,
    val sent: Int = 0,
    val message: String? = null
)
