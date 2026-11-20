package com.example.myapplication.data

import com.example.myapplication.data.social.FavoriteRow
import com.example.myapplication.data.social.FavoriteRowArtist
import com.example.myapplication.data.social.FavoriteRows
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTest {
    private val id = "4uLU6hMCjMI75M1A2tKUQC"
    private val artistId = "0TnOYISbd1XYRBk9myaseg"

    @Test
    fun preservesCatalogIdArtistAndDownloadSourceAcrossFavoritesSync() {
        val raw = JsonParser.parseString(
            """{"id":"$id","name":"A Song","duration_ms":210000,"artists":[{"id":"$artistId","name":"An Artist"}],
                "album":{"images":[{"url":"https://i.scdn.co/cover"}]}}"""
        ).asJsonObject
        val track = spotifyTrack(raw)!!
        val fromFavorite = FavoriteRows.trackOf(
            FavoriteRow("spotify", id, title = "A Song", durationMs = 210_000, artists = listOf(FavoriteRowArtist(artistId, "An Artist")))
        )!!
        assertEquals(track.id, fromFavorite.id)
        assertEquals(track.urn, fromFavorite.urn)
        assertEquals(id, FavoriteRows.keyOf(fromFavorite)?.trackId)
        assertEquals("https://open.spotify.com/artist/$artistId", SharedLinks.linkOf(track.user!!))
        assertEquals(track.user, fromFavorite.toSoundCloudTrack().artists?.single())
        assertTrue(isProgressiveSource(track.urn))
        assertEquals("https://i.scdn.co/cover", track.artworkUrl)
    }

    @Test
    fun parsesSpotifyLinksAndRejectsOtherHostsAndInvalidIds() {
        assertEquals(SharedLink.Spotify("track", id), SharedLinks.parse("https://open.spotify.com/intl-fr/track/$id?si=tracking"))
        assertEquals(SharedLink.Spotify("artist", artistId), SharedLinks.parse("https://open.spotify.com/artist/$artistId"))
        assertNull(SharedLinks.parse("https://open.spotify.com.example.org/track/$id"))
        assertNull(SharedLinks.parse("https://open.spotify.com/track/$id/extra"))
        assertNull(SharedLinks.parse("https://open.spotify.com/track/short"))
    }

    @Test
    fun onlyMatchesTheSameRecording() {
        val base = SoundCloudTrack(1L, title = "Été (feat. Guest)", duration = 245_000L, user = SoundCloudUser(username = "Artist"))
        assertNotNull(spotifyMatchScore(base, base.copy(title = "Ete - Remastered 2011", duration = 247_000L)))
        assertNull(spotifyMatchScore(base, base.copy(title = "Ete (Live)")))
        assertNull(spotifyMatchScore(base, base.copy(user = SoundCloudUser(username = "Other Artist Group"), title = "Different")))
        assertNull(spotifyMatchScore(base, base.copy(user = SoundCloudUser(username = "Someone Else"))))
        assertNull(spotifyMatchScore(base, base.copy(duration = 30_000L)))
    }

    @Test
    fun hidesAuthorizationAndAllCookiesFromDiagnostics() {
        assertEquals("Authorization: …", DiagnosticsReport.redact("Authorization: Bearer fake-token"))
        assertEquals("Cookie: …", DiagnosticsReport.redact("Cookie: sp_dc=first; other=second"))
        assertEquals("https://open.spotify.com/callback?…", DiagnosticsReport.redact("https://open.spotify.com/callback?code=hidden&state=test"))
    }
}
