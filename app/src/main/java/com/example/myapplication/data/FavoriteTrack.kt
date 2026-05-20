package com.example.myapplication.data

data class FavoriteTrack(
    val id: Long,
    val urn: String,
    val title: String,
    val artworkUrl: String?,
    val permalinkUrl: String?,
    val artist: String,
    val duration: Long,
    val streamUrl: String?,
    val downloadState: DownloadState
) {
    fun toSoundCloudTrack(): SoundCloudTrack = SoundCloudTrack(
        id = id,
        urn = urn,
        kind = "track",
        title = title,
        artworkUrl = artworkUrl,
        permalinkUrl = permalinkUrl,
        user = SoundCloudUser(artist),
        duration = duration,
        streamable = true,
        policy = "ALLOW",
        trackAuthorization = null,
        media = null
    )
}

enum class DownloadState {
    NONE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}
