package com.example.myapplication.ui

enum class SyncState {
    IDLE,
    FETCHING_LIKES,
    DOWNLOADING,
    COMPLETED,
    FAILED
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
