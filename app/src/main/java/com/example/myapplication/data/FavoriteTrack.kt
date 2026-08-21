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
    val artistId: Long? = null,
    // Every credited artist, so a saved collaboration survives a round trip through disk. The
    // three fields above only ever held the first one, which meant downloading a track quietly
    // threw away its features. Nullable because Gson bypasses Kotlin constructors: entries
    // persisted before this field existed decode as null no matter what default is written here.
    val artists: List<SoundCloudUser>? = null,
    // Cover cached alongside the audio, so a downloaded track still shows its artwork with no
    // connection. Null for tracks that are not downloaded, or downloaded before this existed.
    val localArtworkPath: String? = null
) {
    /** All credited names, falling back to the single [artist] for entries saved before [artists]. */
    val displayArtist: String
        get() = artists?.mapNotNull { it.username?.takeIf(String::isNotBlank) }
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: artist

    /** Cover to show: the cached file when there is one, otherwise the remote URL. */
    val displayArtworkUrl: String?
        get() = localArtworkPath?.let { "file://$it" } ?: artworkUrl

    fun toSoundCloudTrack(): SoundCloudTrack = SoundCloudTrack(
        id = id,
        urn = urn,
        kind = "track",
        title = title,
        artworkUrl = displayArtworkUrl,
        permalinkUrl = permalinkUrl,
        user = SoundCloudUser(
            id = artistId,
            username = artist,
            permalinkUrl = artistPermalinkUrl
        ),
        artists = artists?.takeIf { it.isNotEmpty() },
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
