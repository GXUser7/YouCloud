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
    // Its page on soundcloud.com: what sharing the mix shares.
    @SerializedName("permalink_url") val permalinkUrl: String? = null,
    val title: String?,
    val description: String?,
    @SerializedName("short_description") val shortDescription: String?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("calculated_artwork_url") val calculatedArtworkUrl: String?,
    // Null when the JSON has none, as a playlist of somebody's often hasn't: Gson goes round the
    // default.
    val tracks: List<SoundCloudMixTrackStub>? = null,
    // "system-playlist" for a mix, a station or a chart; "playlist" for one somebody put together.
    val kind: String? = null,
    val user: SoundCloudUser? = null,
    @SerializedName("track_count") val trackCount: Int? = null
) {
    /** A playlist somebody put together, as a set to open; null for SoundCloud's own. */
    fun toPlaylist(): SoundCloudPlaylist? {
        if (kind != "playlist") return null
        val numeric = id?.toLongOrNull() ?: return null
        return SoundCloudPlaylist(
            id = numeric,
            title = title,
            trackCount = trackCount ?: tracks.orEmpty().size,
            artworkUrl = artworkUrl ?: calculatedArtworkUrl,
            permalinkUrl = permalinkUrl,
            user = user
        )
    }

    fun toMix(): SoundCloudMix? {
        val resolvedId = id ?: urn ?: permalink ?: return null
        return SoundCloudMix(
            id = resolvedId,
            permalink = permalink ?: resolvedId,
            title = title?.takeIf { it.isNotBlank() } ?: "Mix",
            description = shortDescription?.takeIf { it.isNotBlank() } ?: description,
            artworkUrl = calculatedArtworkUrl ?: artworkUrl,
            trackIds = tracks.orEmpty().map { it.id },
            url = permalinkUrl?.takeIf { it.startsWith("https://") }
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
    val trackIds: List<Long>,
    val url: String? = null
)

/**
 * A mix's key in the library as it is on [day]: SoundCloud makes its mixes anew, so a mix kept
 * is that day's tracks, and the next day's is another one to keep.
 */
fun SoundCloudMix.sourceKey(day: java.time.LocalDate = java.time.LocalDate.now()): String = "soundcloud:mix:$id:$day"

data class MixSection(
    val title: String,
    val mixes: List<SoundCloudMix>
)

/**
 * Another of SoundCloud's home rows, past the three home has a place of its own for: curated
 * playlists, artists to watch, the charts, what SoundCloud adds next. Its own mixes open as mixes,
 * the playlists people put together as sets. [key] is SoundCloud's name for the row.
 */
data class MoreSection(
    val key: String,
    val title: String,
    val mixes: List<SoundCloudMix> = emptyList(),
    val playlists: List<SoundCloudPlaylist> = emptyList()
)

/** What home shows from SoundCloud's `mixed-selections`. */
data class HomeSections(
    val moods: MixSection? = null,
    val stations: MixSection? = null,
    // "Trending by genre": a chart per genre, opened like a mix.
    val trending: MixSection? = null,
    // Every other row, in SoundCloud's order.
    val more: List<MoreSection> = emptyList()
) {
    val isEmpty get() = moods == null && stations == null && trending == null && more.isEmpty()
    val mixIds get() = (listOfNotNull(moods, stations, trending).flatMap { it.mixes } + more.flatMap { it.mixes }).map { it.id }.toSet()
}

data class SoundCloudSystemPlaylist(
    val title: String?,
    val tracks: List<SoundCloudTrack>? = null
)
