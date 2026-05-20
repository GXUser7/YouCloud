package com.example.myapplication.data

import com.google.gson.annotations.SerializedName

data class MixedSelectionsResponse(
    val collection: List<MixedSelection> = emptyList()
)

data class MixedSelection(
    val title: String?,
    @SerializedName("tracking_feature_name") val trackingFeatureName: String?,
    val items: MixedSelectionItems?
)

data class MixedSelectionItems(
    val collection: List<SoundCloudMixPlaylist> = emptyList()
)

data class SoundCloudMixPlaylist(
    val id: String?,
    val urn: String?,
    val permalink: String?,
    val title: String?,
    val description: String?,
    @SerializedName("short_description") val shortDescription: String?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("calculated_artwork_url") val calculatedArtworkUrl: String?,
    val tracks: List<SoundCloudMixTrackStub> = emptyList()
) {
    fun toMix(): SoundCloudMix? {
        val resolvedId = id ?: urn ?: permalink ?: return null
        return SoundCloudMix(
            id = resolvedId,
            permalink = permalink ?: resolvedId,
            title = title?.takeIf { it.isNotBlank() } ?: "Mix",
            description = shortDescription?.takeIf { it.isNotBlank() } ?: description,
            artworkUrl = calculatedArtworkUrl ?: artworkUrl,
            trackIds = tracks.map { it.id }
        )
    }
}

data class SoundCloudMixTrackStub(
    val id: Long,
    val kind: String?,
    val policy: String?
)

data class SoundCloudMix(
    val id: String,
    val permalink: String,
    val title: String,
    val description: String?,
    val artworkUrl: String?,
    val trackIds: List<Long>
)

data class MixSection(
    val title: String,
    val mixes: List<SoundCloudMix>
)

data class SoundCloudSystemPlaylist(
    val title: String?,
    val tracks: List<SoundCloudTrack>? = null
)

data class TracksByIdsResponse(
    val collection: List<SoundCloudTrack> = emptyList()
)
