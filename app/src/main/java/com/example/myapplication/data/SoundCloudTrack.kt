package com.example.myapplication.data

import com.google.gson.annotations.SerializedName

data class SoundCloudTrack(
    val id: Long,
    val urn: String,
    val kind: String,
    val title: String,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("permalink_url") val permalinkUrl: String?,
    @SerializedName("user") val user: SoundCloudUser?,
    @SerializedName("duration") val duration: Long,
    val streamable: Boolean?,
    val policy: String?,
    @SerializedName("track_authorization") val trackAuthorization: String?,
    val media: SoundCloudMedia?
)

data class SoundCloudUser(
    val username: String
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

