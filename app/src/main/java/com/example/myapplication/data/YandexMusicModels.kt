package com.example.myapplication.data

import com.google.gson.annotations.SerializedName

data class YandexSearchResponse(
    val result: YandexSearchResult?
)

data class YandexSearchResult(
    val tracks: YandexSearchTracks?
)

data class YandexSearchTracks(
    val results: List<YandexTrack>? = emptyList()
)

data class YandexTrack(
    val id: String,
    val title: String?,
    val artists: List<YandexArtist>? = emptyList(),
    val durationMs: Long = 0L,
    val coverUri: String?
) {
    fun getCoverUrl(size: String = "200x200"): String? {
        if (coverUri == null) return null
        return "https://" + coverUri.replace("%%", size)
    }

    fun toSoundCloudTrack(): SoundCloudTrack {
        val artistList = artists.orEmpty()
        val artistName = artistList.firstOrNull()?.name ?: "Unknown Yandex Artist"
        // Generate a deterministic negative ID from string id hashcode
        val generatedId = -kotlin.math.abs(id.hashCode().toLong())
        return SoundCloudTrack(
            id = generatedId,
            urn = "yandex:track:$id",
            kind = "track",
            title = title ?: "Unknown Track",
            artworkUrl = getCoverUrl("200x200"),
            permalinkUrl = "https://music.yandex.ru/track/$id",
            user = SoundCloudUser(
                id = artistList.firstOrNull()?.id?.toLongOrNull() ?: 0L,
                username = artistName,
                permalinkUrl = artistList.firstOrNull()?.id?.let { "yandex:artist:$it" }
            ),
            duration = durationMs,
            streamable = true,
            policy = "ALLOW",
            trackAuthorization = null,
            media = null
        )
    }
}

data class YandexArtist(
    val id: String?,
    val name: String?
)

data class YandexDownloadInfoResponse(
    val result: List<YandexDownloadInfoItem>? = emptyList()
)

data class YandexDownloadInfoItem(
    val codec: String,
    val bitrateInKbps: Int,
    val downloadInfoUrl: String
)

data class YandexArtistBriefResponse(
    val result: YandexArtistBriefResult?
)

data class YandexArtistBriefResult(
    val artist: YandexArtistDetail?,
    @SerializedName("popularTracks") val tracks: List<YandexTrack>? = emptyList(),
    val albums: List<YandexAlbum>? = emptyList()
)

data class YandexArtistDetail(
    val id: String?,
    val name: String?,
    val cover: YandexCover?,
    val description: YandexDescription?,
    val stats: YandexStats?
)

data class YandexCover(
    val uri: String?
) {
    fun getCoverUrl(size: String = "200x200"): String? {
        if (uri == null) return null
        return "https://" + uri.replace("%%", size)
    }
}

data class YandexDescription(
    val text: String?
)

data class YandexStats(
    val likes: Int = 0
)

// Yandex Playlists Models
data class YandexPlaylistsResponse(
    val result: List<YandexPlaylist>? = emptyList()
)

data class YandexPlaylist(
    val kind: Long,
    val title: String?,
    val trackCount: Int = 0,
    val cover: YandexCover?,
    val owner: YandexPlaylistOwner?
) {
    fun toSoundCloudPlaylist(): SoundCloudPlaylist {
        return SoundCloudPlaylist(
            id = kind,
            title = title ?: "Без названия",
            trackCount = trackCount,
            artworkUrl = cover?.getCoverUrl("200x200"),
            tracks = emptyList(),
            permalinkUrl = "yandex:playlist:$kind"
        )
    }
}

data class YandexPlaylistOwner(
    val uid: Long,
    val name: String?
)

data class YandexPlaylistDetailResponse(
    val result: YandexPlaylistDetail?
)

data class YandexPlaylistDetail(
    val kind: Long,
    val title: String?,
    val trackCount: Int = 0,
    val tracks: List<YandexPlaylistTrackContainer>? = emptyList()
)

data class YandexPlaylistTrackContainer(
    val id: Long,
    val track: YandexTrack?
)

// Yandex Albums Models
data class YandexAlbum(
    val id: Long,
    val title: String?,
    val trackCount: Int = 0,
    val coverUri: String?,
    val artists: List<YandexArtist>? = emptyList()
) {
    fun toSoundCloudPlaylist(): SoundCloudPlaylist {
        val artwork = coverUri?.let { "https://" + it.replace("%%", "200x200") }
        return SoundCloudPlaylist(
            id = id,
            title = title ?: "Без названия",
            trackCount = trackCount,
            artworkUrl = artwork,
            tracks = emptyList(),
            permalinkUrl = "yandex:album:$id"
        )
    }
}

data class YandexAlbumDetailResponse(
    val result: YandexAlbumDetail?
)

data class YandexAlbumDetail(
    val id: Long,
    val title: String?,
    val trackCount: Int = 0,
    val coverUri: String?,
    val volumes: List<List<YandexTrack>>? = emptyList()
)

// Yandex Liked Tracks Models
data class YandexLikedTracksResponse(
    val result: YandexLikedTracksResult?
)

data class YandexLikedTracksResult(
    val library: YandexLibrary?
)

data class YandexLibrary(
    val uid: Long,
    val tracks: List<YandexLikedTrackRef>? = emptyList()
)

data class YandexLikedTrackRef(
    val id: String,
    val albumId: String?
)

// Yandex Account Status Models
data class YandexAccountStatusResponse(
    val result: YandexAccountStatusResult?
)

data class YandexAccountStatusResult(
    val account: YandexAccount?
)

data class YandexAccount(
    val uid: Long,
    val login: String?,
    val displayName: String?,
    val fullName: String?
)

data class YandexLikeResponse(
    val result: String?
)

data class YandexTracksResponse(
    val result: List<YandexTrack>? = emptyList()
)

data class YandexArtistTracksResponse(
    val result: YandexArtistTracksResult?
)

data class YandexArtistTracksResult(
    val tracks: List<YandexTrack>? = emptyList()
)
