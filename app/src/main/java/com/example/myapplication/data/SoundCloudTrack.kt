package com.example.myapplication.data

import com.google.gson.annotations.SerializedName

data class SoundCloudTrack(
    val id: Long,
    val urn: String? = null,
    val kind: String? = null,
    val title: String? = null,
    @SerializedName("artwork_url") val artworkUrl: String? = null,
    @SerializedName("permalink_url") val permalinkUrl: String? = null,
    @SerializedName("user") val user: SoundCloudUser? = null,
    // Every credited artist, not just the uploader. SoundCloud only tells us who uploaded a
    // track, so this stays empty there and `user` remains the answer; Yandex genuinely returns
    // a list, and collapsing it to `.first()` was losing every feature and collaborator.
    // Nullable on purpose: Gson bypasses Kotlin constructors, so a field missing from cached
    // JSON stays null regardless of the default here. Declaring it non-null crashed on every
    // track cached before this field existed.
    val artists: List<SoundCloudUser>? = null,
    @SerializedName("user_id") val userId: Long? = null,
    @SerializedName("duration") val duration: Long = 0L,
    val streamable: Boolean? = null,
    val policy: String? = null,
    @SerializedName("track_authorization") val trackAuthorization: String? = null,
    val media: SoundCloudMedia? = null
)

data class SoundCloudUser(
    val id: Long? = null,
    val username: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val description: String? = null,
    @SerializedName("followers_count") val followersCount: Int? = null,
    @SerializedName("track_count") val trackCount: Int? = null,
    @SerializedName("permalink_url") val permalinkUrl: String? = null
)

data class SoundCloudTracksResponse(
    val collection: List<SoundCloudTrack> = emptyList(),
    @SerializedName("next_href") val nextHref: String? = null
)

data class SoundCloudMedia(
    val transcodings: List<SoundCloudTranscoding> = emptyList()
)

data class SoundCloudTranscoding(
    val url: String,
    val preset: String?,
    val snipped: Boolean = false,
    val format: SoundCloudFormat?
)

data class SoundCloudFormat(
    val protocol: String?,
    @SerializedName("mime_type") val mimeType: String?
)

data class SoundCloudStreamResponse(
    val url: String?
)

data class SoundCloudMeResponse(
    val id: Long
)

data class SoundCloudLikesResponse(
    val collection: List<SoundCloudLikeItem> = emptyList(),
    @SerializedName("next_href") val nextHref: String? = null
)

data class SoundCloudLikeItem(
    @SerializedName("created_at") val createdAt: String?,
    val track: SoundCloudTrack?
)

data class SoundCloudPlaylist(
    val id: Long,
    val title: String? = null,
    val tracks: List<SoundCloudTrack> = emptyList(),
    @SerializedName("track_count") val trackCount: Int = 0,
    @SerializedName("artwork_url") val artworkUrl: String? = null,
    @SerializedName("permalink_url") val permalinkUrl: String? = null,
    val user: SoundCloudUser? = null
)

data class SoundCloudStreamUserResponse(
    val collection: List<SoundCloudStreamItem> = emptyList(),
    @SerializedName("next_href") val nextHref: String? = null
)

data class SoundCloudStreamItem(
    val type: String,
    val track: SoundCloudTrack? = null,
    val playlist: SoundCloudPlaylist? = null
)


