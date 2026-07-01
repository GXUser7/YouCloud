package com.example.myapplication.data

data class FavoriteTrack(
    val id: Long = 0L,
    val urn: String = "",
    val title: String = "Unknown Track",
    val artworkUrl: String? = null,
    val permalinkUrl: String? = null,
    val artist: String = "Unknown Artist",
    val duration: Long = 0L,
    val streamUrl: String? = null,
    val downloadState: DownloadState = DownloadState.NONE,
    val artistPermalinkUrl: String? = null,
    val artistId: Long? = null
) {
    fun toSoundCloudTrack(): SoundCloudTrack = SoundCloudTrack(
        id = id,
        urn = urn,
        kind = "track",
        title = title,
        artworkUrl = artworkUrl,
        permalinkUrl = permalinkUrl,
        user = SoundCloudUser(
            id = artistId,
            username = artist,
            permalinkUrl = artistPermalinkUrl
        ),
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
