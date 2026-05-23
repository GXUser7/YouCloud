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
    @SerializedName("duration") val duration: Long = 0L,
    val streamable: Boolean? = null,
    val policy: String? = null,
    @SerializedName("track_authorization") val trackAuthorization: String? = null,
    val media: SoundCloudMedia? = null
)

data class SoundCloudUser(
    val username: String? = null
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


