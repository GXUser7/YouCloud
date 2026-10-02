package com.example.myapplication.data

import com.google.gson.annotations.SerializedName

data class YandexSearchResponse(
    val result: YandexSearchResult?
)

data class YandexSearchResult(
    val tracks: YandexSearchTracks?,
    val albums: YandexSearchAlbums? = null,
    val artists: YandexSearchArtists? = null,
    val playlists: YandexSearchPlaylists? = null
)

data class YandexSearchAlbums(val results: List<YandexAlbum>? = emptyList())

data class YandexSearchArtists(val results: List<YandexArtistDetail>? = emptyList())

data class YandexSearchPlaylists(val results: List<YandexPlaylist>? = emptyList())

data class YandexLyricsResponse(val result: YandexLyricsResult?)

data class YandexLyricsResult(val downloadUrl: String?)

data class YandexSearchTracks(
    val results: List<YandexTrack>? = emptyList(),
    val total: Int? = null,
    val perPage: Int? = null
)

data class YandexTrack(
    val id: String,
    val title: String?,
    val artists: List<YandexArtist>? = emptyList(),
    val durationMs: Long = 0L,
    val coverUri: String?,
    val albums: List<YandexAlbum>? = emptyList(),
    // A "videoshot": a short vertical loop Yandex plays behind the player, as an MP4.
    val backgroundVideoUri: String? = null,
    // False for a track that can't be played (withdrawn, not licensed here); absent otherwise.
    val available: Boolean? = null
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

/**
 * Every clip in an `artists/{id}/blocks/artist-clips` answer: the objects under a `clip` key, how
 * deep the blocks nest them (`items[].data.clip` today) notwithstanding.
 */
fun yandexClipsIn(json: com.google.gson.JsonElement?): List<YandexClip> {
    val found = mutableListOf<YandexClip>()
    fun walk(element: com.google.gson.JsonElement?) {
        when {
            element == null || element.isJsonNull -> Unit
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                if (key == "clip" && value.isJsonObject) {
                    runCatching { com.google.gson.Gson().fromJson(value, YandexClip::class.java) }.getOrNull()?.let(found::add)
                } else {
                    walk(value)
                }
            }
            element.isJsonArray -> element.asJsonArray.forEach(::walk)
        }
    }
    walk(json)
    return found.distinctBy { it.id }
}

/**
 * A music video, as an artist's page lists it: [cover]'s `videoUrl` is ten seconds of it, silent,
 * which Yandex's own player loops in the cover's place. The block doesn't say which track it is a
 * video of; the title does.
 */
data class YandexClip(
    val id: Long? = null,
    val title: String? = null,
    val duration: Int? = null,
    val cover: YandexClipCover? = null
)

data class YandexClipCover(
    val uri: String? = null,
    val videoUrl: String? = null
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

/** As the artist screen expects it: [SoundCloudUser.permalinkUrl] names a Yandex artist. */
fun YandexArtistDetail.toArtistUser(): SoundCloudUser? {
    val artistId = id?.takeIf { it.isNotBlank() } ?: return null
    return SoundCloudUser(
        id = artistId.toLongOrNull() ?: 0L,
        username = name,
        avatarUrl = cover?.getCoverUrl("400x400"),
        trackCount = counts?.tracks,
        permalinkUrl = "yandex:artist:$artistId"
    )
}

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
    val owner: YandexPlaylistOwner?,
    val ogImage: String? = null
) {
    /**
     * Someone else's playlist, found by search: opened by owner and kind, so both go in the
     * permalink. The id only has to be unique among results, and is kept negative so it can't
     * meet an album's.
     */
    fun toSearchPlaylist(): SoundCloudPlaylist? {
        val ownerUid = owner?.uid ?: return null
        val artwork = cover?.getCoverUrl("400x400")
            ?: ogImage?.let { "https://" + it.replace("%%", "400x400") }
        return SoundCloudPlaylist(
            id = -(ownerUid * 100_000L + kind),
            title = title ?: "Без названия",
            trackCount = trackCount,
            artworkUrl = artwork,
            permalinkUrl = "yandex:playlist:$ownerUid:$kind",
            user = SoundCloudUser(username = owner.name)
        )
    }

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
    val name: String?,
    // Absent from the listener's own playlists; a playlist from a link names it.
    val login: String? = null
)

data class YandexPlaylistDetailResponse(
    val result: YandexPlaylistDetail?
)

data class YandexPlaylistDetail(
    val kind: Long,
    val title: String?,
    val trackCount: Int = 0,
    val tracks: List<YandexPlaylistTrackContainer>? = emptyList(),
    // Who made it and its cover: for a playlist opened from a link, not from the listener's own list.
    val owner: YandexPlaylistOwner? = null,
    val cover: YandexPlaylistCover? = null,
    val ogImage: String? = null
)

/** A playlist's cover: its own picture ([uri]) or a mosaic of its tracks' ([itemsUri]). */
data class YandexPlaylistCover(
    val uri: String? = null,
    val itemsUri: List<String>? = null
) {
    fun url(size: String = "400x400"): String? =
        (uri ?: itemsUri?.firstOrNull())?.let { "https://" + it.replace("%%", size) }
}

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
    val artists: List<YandexArtist>? = emptyList(),
    val year: Int? = null,
    // "single", "compilation", … — null for a regular album.
    val type: String? = null
) {
    /** For search: carries what the album row and its caption show. */
    fun toSearchAlbum(): SoundCloudPlaylist = toSoundCloudPlaylist().copy(
        artworkUrl = coverUri?.let { "https://" + it.replace("%%", "400x400") },
        user = artists?.firstOrNull()?.name?.let { SoundCloudUser(username = it) },
        isAlbum = true,
        setType = type ?: "album",
        releaseDate = year?.toString()
    )

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

// Rotor: Yandex Music's radio. A session is started from seeds ("track:123") and hands out
// batches of tracks; what is heard, skipped or finished is fed back to it and steers what comes.

data class YandexRotorSessionRequest(
    val seeds: List<String>,
    // Tracks already heard ("id" or "id:albumId"), so the radio doesn't offer them again.
    val queue: List<String> = emptyList(),
    val includeTracksInResponse: Boolean = true
)

data class YandexRotorQueueRequest(val queue: List<String>)

data class YandexRotorSessionResponse(val result: YandexRotorSession?)

data class YandexRotorSession(
    val radioSessionId: String? = null,
    val batchId: String? = null,
    val sequence: List<YandexRotorItem>? = emptyList(),
    val terminated: Boolean? = null,
    val unknownSession: Boolean? = null
)

data class YandexRotorItem(
    val type: String? = null,
    val track: YandexTrack? = null
)

data class YandexRotorFeedback(
    val event: YandexRotorEvent,
    val batchId: String? = null
)

data class YandexRotorEvent(
    // radioStarted, trackStarted, trackFinished, skip.
    val type: String,
    // ISO 8601 in UTC, with milliseconds.
    val timestamp: String,
    val trackId: String? = null,
    val totalPlayedSeconds: Double? = null
)
