package com.example.myapplication.data.social

import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudUser
import com.example.myapplication.data.YT_ARTIST_REF
import com.example.myapplication.data.YT_LIVE_URN
import com.example.myapplication.data.YT_TRACK_URN
import com.example.myapplication.data.YandexAlbum
import com.example.myapplication.data.YandexArtist
import com.example.myapplication.data.YandexTrack
import com.example.myapplication.data.youTubeTrackId
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteRowsTest {
    private val gson = Gson()

    /** As FavoritesRepository.add saves a track. */
    private fun saved(track: SoundCloudTrack) = FavoriteTrack(
        id = track.id,
        urn = track.urn ?: "",
        title = track.title ?: "Unknown Track",
        artworkUrl = track.artworkUrl,
        permalinkUrl = track.permalinkUrl,
        artist = track.user?.username ?: "Unknown Artist",
        duration = track.duration,
        artistPermalinkUrl = track.user?.permalinkUrl,
        artistId = track.user?.id,
        artists = track.artists?.takeIf { it.isNotEmpty() }
    )

    /** Up to the server and back, as JSON. */
    private fun roundTrip(track: FavoriteTrack): FavoriteTrack {
        val key = FavoriteRows.keyOf(track)
        assertNotNull("no key for ${track.urn}", key)
        val row = FavoriteRows.rowOf(key!!, track, liked = true, at = 1_760_000_000_000L)
        val back = gson.fromJson(gson.toJson(row), FavoriteRow::class.java)
        return FavoriteRows.trackOf(back)!!
    }

    @Test
    fun yandexTrackComesBackAsTheSameTrack() {
        val track = YandexTrack(
            id = "1710811",
            title = "Creep",
            artists = listOf(YandexArtist("36800", "Radiohead"), YandexArtist("1", "Guest")),
            durationMs = 238_000L,
            coverUri = "avatars.yandex.net/get-music-content/x/%%",
            albums = listOf(YandexAlbum(id = 3542, title = "Pablo Honey", coverUri = null))
        ).toSoundCloudTrack()
        val back = roundTrip(saved(track))
        assertEquals(track.id, back.id)
        assertEquals("yandex:track:1710811:3542", back.urn)
        assertEquals(track.urn, back.urn)
        assertEquals(listOf("Radiohead", "Guest"), back.artists?.map { it.username })
        assertEquals("yandex:artist:36800", back.artistPermalinkUrl)
        assertEquals(238_000L, back.duration)
    }

    @Test
    fun youTubeTrackAndBroadcastKeepTheirIds() {
        val song = FavoriteTrack(
            id = youTubeTrackId("dQw4w9WgXcQ"),
            urn = YT_TRACK_URN + "dQw4w9WgXcQ",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley",
            artistPermalinkUrl = YT_ARTIST_REF + "UCuAXFkgsw1L7xaCfnd5JJOw",
            artists = listOf(SoundCloudUser(username = "Rick Astley", permalinkUrl = YT_ARTIST_REF + "UCuAXFkgsw1L7xaCfnd5JJOw"))
        )
        val back = roundTrip(song)
        assertEquals(song.id, back.id)
        assertEquals(song.urn, back.urn)
        assertEquals(song.artistPermalinkUrl, back.artistPermalinkUrl)

        val live = FavoriteTrack(id = youTubeTrackId("live:jfKfPfyJRdk"), urn = YT_LIVE_URN + "jfKfPfyJRdk", title = "lofi")
        val liveBack = roundTrip(live)
        assertEquals(live.id, liveBack.id)
        assertEquals(live.urn, liveBack.urn)
        assertEquals(0L, liveBack.duration)
    }

    @Test
    fun soundCloudTrackKeepsItsIdAndArtist() {
        val track = FavoriteTrack(
            id = 123_456_789L,
            urn = "soundcloud:tracks:123456789",
            title = "Track",
            permalinkUrl = "https://soundcloud.com/someone/track",
            artist = "someone",
            artistId = 42L,
            artistPermalinkUrl = "https://soundcloud.com/someone",
            duration = 200_000L
        )
        val back = roundTrip(track)
        assertEquals(track.id, back.id)
        assertEquals(track.urn, back.urn)
        assertEquals(42L, back.artistId)
        assertEquals("https://soundcloud.com/someone", back.artistPermalinkUrl)
        assertEquals(track.permalinkUrl, back.permalinkUrl)
    }

    @Test
    fun filesFromThePhoneStayOnIt() {
        assertNull(FavoriteRows.keyOf(FavoriteTrack(id = 1_700_000_000_123L, urn = "local:track:1700000000123")))
        // Without an urn, an imported file's timestamp isn't taken for a SoundCloud id either.
        assertNull(FavoriteRows.keyOf(FavoriteTrack(id = 1_700_000_000_123L, urn = "")))
    }

    @Test
    fun rowsTheSiteWritesBecomeTheAppsTracks() {
        // As web/src/lib/favorites.svelte.ts writes them.
        val yandex = gson.fromJson(
            """{"service":"yandex","track_id":"1710811","liked":true,"changed_at":"2026-10-11T10:00:00.000Z",
               "title":"Creep","artists":[{"id":"36800","name":"Radiohead"}],"album_id":"3542","duration_ms":238000}""",
            FavoriteRow::class.java
        )
        val expected = YandexTrack(id = "1710811", title = "Creep", coverUri = null, albums = listOf(YandexAlbum(3542, null, coverUri = null)))
            .toSoundCloudTrack()
        val track = FavoriteRows.trackOf(yandex)!!
        assertEquals(expected.id, track.id)
        assertEquals(expected.urn, track.urn)

        val soundCloud = gson.fromJson(
            """{"service":"soundcloud","track_id":"987","liked":true,"title":"T","artists":[{"id":"9","name":"A"}],"link":"https://soundcloud.com/a/t"}""",
            FavoriteRow::class.java
        )
        val sc = FavoriteRows.trackOf(soundCloud)!!
        assertEquals(987L, sc.id)
        assertEquals(9L, sc.artistId)
        assertEquals("https://soundcloud.com/a/t", sc.permalinkUrl)

        assertNull(FavoriteRows.trackOf(FavoriteRow(service = "soundcloud", trackId = "not-a-number")))
        assertNull(FavoriteRows.trackOf(FavoriteRow(service = "spotify", trackId = "1")))
    }
}
