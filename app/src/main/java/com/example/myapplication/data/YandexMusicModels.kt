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
    val coverUri: String?,
    val albums: List<YandexAlbum>? = emptyList()
) {
    fun getCoverUrl(size: String = "200x200"): String? {
        if (coverUri == null) return null
        return "https://" + coverUri.replace("%%", size)
    }

    fun toSoundCloudTrack(customAlbumId: String? = null): SoundCloudTrack {
        val artistList = artists.orEmpty()
        val artistName = artistList.firstOrNull()?.name ?: "Unknown Yandex Artist"
        val rawId = id.substringBefore(":")
        // Use toLong for numeric IDs with deterministic negative offset to avoid collision with SoundCloud IDs
        // Falls back to hashCode for non-numeric IDs, with safe abs handling for Int.MIN_VALUE
        val numericId = rawId.toLongOrNull()
        val generatedId = if (numericId != null) {
            -(numericId + 1_000_000_000L)
        } else {
            val hash = rawId.hashCode().toLong()
            -(if (hash == Int.MIN_VALUE.toLong()) Int.MAX_VALUE.toLong() else kotlin.math.abs(hash)) - 1_000_000_000L
        }
        val albumId = customAlbumId ?: albums?.firstOrNull()?.id?.toString()
        val finalUrn = if (albumId != null) "yandex:track:$rawId:$albumId" else "yandex:track:$rawId"
        return SoundCloudTrack(
            id = generatedId,
            urn = finalUrn,
            kind = "track",
            title = title ?: "Unknown Track",
            artworkUrl = getCoverUrl("200x200"),
            permalinkUrl = "https://music.yandex.ru/track/$rawId",
            user = SoundCloudUser(
                id = artistList.firstOrNull()?.id?.toLongOrNull() ?: 0L,
                username = artistName,
                permalinkUrl = artistList.firstOrNull()?.id?.let { "yandex:artist:$it" }
            ),
            artists = artistList.mapNotNull { artist ->
                val name = artist.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SoundCloudUser(
                    id = artist.id?.toLongOrNull() ?: 0L,
                    username = name,
                    permalinkUrl = artist.id?.let { "yandex:artist:$it" }
                )
            },
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
    val stats: YandexStats?,
    // brief-info puts the follower count on the artist itself as `likesCount`, and the real
    // track/album totals in `counts`. The code used to read `stats.likes`, which this endpoint
    // never returns — so subscribers and track counts were always zero.
    @SerializedName("likesCount") val likesCount: Int? = null,
    val counts: YandexArtistCounts? = null
)

data class YandexArtistCounts(
    val tracks: Int = 0,
    @SerializedName("directAlbums") val directAlbums: Int = 0,
    @SerializedName("alsoAlbums") val alsoAlbums: Int = 0,
    @SerializedName("alsoTracks") val alsoTracks: Int = 0
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
        // Offset album IDs by 10_000_000 to avoid collision with playlist kind IDs
        val namespacedId = id + 10_000_000L
        return SoundCloudPlaylist(
            id = namespacedId,
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
