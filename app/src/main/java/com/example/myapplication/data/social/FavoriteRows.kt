package com.example.myapplication.data.social

import com.example.myapplication.data.DownloadState
import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.SoundCloudUser
import com.example.myapplication.data.YT_ARTIST_REF
import com.example.myapplication.data.YT_LIVE_URN
import com.example.myapplication.data.YT_TRACK_URN
import com.example.myapplication.data.yandexTrackId
import com.example.myapplication.data.youTubeTrackId
import com.google.gson.annotations.SerializedName
import java.time.Instant

/**
 * A track of «Любимое» as the database keeps it (public.favorites): the service, the track's own id
 * there, liked or taken out and when, and enough of the track for a list to show it.
 */
internal data class FavoriteRow(
    val service: String,
    @SerializedName("track_id") val trackId: String,
    val liked: Boolean = true,
    @SerializedName("changed_at") val changedAt: String? = null,
    val title: String? = null,
    val artists: List<FavoriteRowArtist>? = null,
    @SerializedName("album_id") val albumId: String? = null,
    val cover: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    val link: String? = null,
    val live: Boolean? = null
) {
    val key: String get() = "$service:$trackId"
}

/** An artist of a row: [id] is the service's own (SoundCloud's number, Yandex's, YouTube's channel). */
internal data class FavoriteRowArtist(val id: String? = null, val name: String? = null, val link: String? = null)

/**
 * How the app's saved tracks and the database's rows name each other. A row made into a track
 * gets the id the app gives that track wherever it meets it, so a track liked on the site is the
 * same one here as when it turns up in a search.
 */
internal object FavoriteRows {
    /** Where a track is from, as the database names it. */
    data class Key(val service: String, val trackId: String, val albumId: String? = null, val live: Boolean = false) {
        val key: String get() = "$service:$trackId"
    }

    // What the database takes for a track's id.
    private val TRACK_ID = Regex("^[A-Za-z0-9_-]{1,64}$")

    /** Null for a file from the phone: nowhere else to be found. */
    fun keyOf(track: FavoriteTrack): Key? {
        val urn = track.urn
        val key = when {
            urn.startsWith("local:") -> null
            urn.startsWith("yandex:track:") -> urn.removePrefix("yandex:track:").let { rest ->
                Key("yandex", rest.substringBefore(":"), rest.substringAfter(":", "").ifBlank { null })
            }
            urn.startsWith(YT_TRACK_URN) -> Key("youtube", urn.removePrefix(YT_TRACK_URN))
            urn.startsWith(YT_LIVE_URN) -> Key("youtube", urn.removePrefix(YT_LIVE_URN), live = true)
            // SoundCloud's: its own ids, below the timestamps files from the phone get.
            (urn.isBlank() || urn.startsWith("soundcloud:")) && track.id in 1 until 100_000_000_000L ->
                Key("soundcloud", track.id.toString())
            else -> null
        }
        return key?.takeIf { TRACK_ID.matches(it.trackId) }
    }

    fun rowOf(key: Key, track: FavoriteTrack, liked: Boolean, at: Long): FavoriteRow {
        val credits = track.artists?.takeIf { it.isNotEmpty() }
            ?: listOf(SoundCloudUser(id = track.artistId, username = track.artist, permalinkUrl = track.artistPermalinkUrl))
        return FavoriteRow(
            service = key.service,
            trackId = key.trackId,
            liked = liked,
            changedAt = Instant.ofEpochMilli(at).toString(),
            title = track.title,
            artists = credits.mapNotNull { artist ->
                val name = artist.username?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                FavoriteRowArtist(id = artistIdOf(key.service, artist), name = name, link = artist.permalinkUrl?.takeIf { it.startsWith("http") })
            },
            albumId = key.albumId,
            // The cover's address, never the copy kept on the phone.
            cover = track.artworkUrl?.takeIf { it.startsWith("http") },
            durationMs = track.duration.takeIf { it > 0 },
            link = track.permalinkUrl?.takeIf { it.startsWith("http") },
            live = key.live
        )
    }

    private fun artistIdOf(service: String, artist: SoundCloudUser): String? = when (service) {
        "soundcloud" -> artist.id?.takeIf { it > 0 }?.toString()
        "yandex" -> artist.permalinkUrl?.takeIf { it.startsWith("yandex:artist:") }?.removePrefix("yandex:artist:")
            ?: artist.id?.takeIf { it > 0 }?.toString()
        else -> artist.permalinkUrl?.takeIf { it.startsWith(YT_ARTIST_REF) }?.removePrefix(YT_ARTIST_REF)
    }

    /** The track a row names, as the app would have made it meeting it on its service. */
    fun trackOf(row: FavoriteRow): FavoriteTrack? {
        val id = row.trackId
        if (!TRACK_ID.matches(id)) return null
        val artists = row.artists.orEmpty().mapNotNull { artist ->
            val name = artist.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            when (row.service) {
                "soundcloud" -> SoundCloudUser(id = artist.id?.toLongOrNull(), username = name, permalinkUrl = artist.link)
                "yandex" -> SoundCloudUser(id = artist.id?.toLongOrNull() ?: 0L, username = name, permalinkUrl = artist.id?.let { "yandex:artist:$it" })
                else -> SoundCloudUser(username = name, permalinkUrl = artist.id?.let { YT_ARTIST_REF + it })
            }
        }
        val live = row.live == true
        val (trackId, urn, page) = when (row.service) {
            "soundcloud" -> {
                val number = id.toLongOrNull()?.takeIf { it > 0 } ?: return null
                Triple(number, "soundcloud:tracks:$number", row.link)
            }
            "yandex" -> Triple(
                yandexTrackId(id),
                if (row.albumId != null) "yandex:track:$id:${row.albumId}" else "yandex:track:$id",
                "https://music.yandex.ru/track/$id"
            )
            "youtube" -> Triple(
                youTubeTrackId(if (live) "live:$id" else id),
                (if (live) YT_LIVE_URN else YT_TRACK_URN) + id,
                "https://music.youtube.com/watch?v=$id"
            )
            else -> return null
        }
        val first = artists.firstOrNull()
        return FavoriteTrack(
            id = trackId,
            urn = urn,
            title = row.title?.takeIf { it.isNotBlank() } ?: "Unknown Track",
            artworkUrl = row.cover,
            permalinkUrl = page,
            artist = first?.username ?: "Unknown Artist",
            duration = if (live) 0L else row.durationMs ?: 0L,
            downloadState = DownloadState.NONE,
            artistPermalinkUrl = first?.permalinkUrl,
            artistId = first?.id,
            artists = artists.takeIf { it.isNotEmpty() }
        )
    }
}
