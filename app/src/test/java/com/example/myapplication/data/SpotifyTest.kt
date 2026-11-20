package com.example.myapplication.data

import com.example.myapplication.data.social.FavoriteRow
import com.example.myapplication.data.social.FavoriteRowArtist
import com.example.myapplication.data.social.FavoriteRows
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

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
    fun acceptsTheWebPlayerSessionOnlyForSpotifyRequests() {
        val now = 100_000L
        val claims = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1200}""".toByteArray())
        val token = "header.$claims.signature"
        val url = "https://api-partner.spotify.com/pathfinder/v2/query"
        val headers = mapOf("authorization" to "Bearer $token", "CLIENT-TOKEN" to "browser-client-token", "Origin" to SPOTIFY_ORIGIN)
        val session = spotifyRequestSession(url, headers, now)!!
        assertEquals(token, session.token)
        assertEquals("browser-client-token", session.clientToken)
        assertEquals(1_200_000L, session.expiresAt)

        assertNull(spotifyRequestSession("https://api-partner.spotify.com.untrusted.test/query", headers, now))
        assertNull(spotifyRequestSession("https://api-partner.spotify.com@untrusted.test/query", headers, now))
        assertNull(spotifyRequestSession("http://api-partner.spotify.com/query", headers, now))
        assertNull(spotifyRequestSession(url, headers + ("Origin" to "https://untrusted.test"), now))
        assertNull(spotifyRequestSession(url, headers - "CLIENT-TOKEN", now))
        assertNull(spotifyRequestSession(url, headers, now = 1_200_000L))
    }

    @Test
    fun readsWebPlayerHomeWithWrappedTracksAndPlaylists() {
        val playlistId = "37i9dQZF1DXcBWIGoYBM5M"
        val response = JsonParser.parseString("""
            {"home":{"sectionContainer":{"sections":{"items":[
                {
                    "data":{"title":{"transformedLabel":"Your picks"}},
                    "sectionItems":{"items":[
                        {"content":{"data":{"__typename":"Playlist","uri":"spotify:playlist:$playlistId","name":"Mix","content":{"totalCount":21}}}},
                        {"content":{"data":{"__typename":"Track","uri":"spotify:track:$id","name":"A Song",
                            "trackDuration":{"totalMilliseconds":210000},
                            "artists":{"items":[{"uri":"spotify:artist:$artistId","profile":{"name":"An Artist"}}]},
                            "albumOfTrack":{"coverArt":{"sources":[{"url":"https://i.scdn.co/cover"}]}}}}},
                        {"content":{"data":{"__typename":"NotFound"}}}
                    ]}
                }
            ]}}}}
        """.trimIndent()).asJsonObject
        val row = spotifyHomeRows(response).single()
        assertEquals("Your picks", row.title)
        assertEquals(SPOTIFY_PLAYLIST + playlistId, row.sets.single().permalinkUrl)
        assertEquals(21, row.sets.single().trackCount)
        val track = row.tracks.single()
        assertEquals(SPOTIFY_TRACK + id, track.urn)
        assertEquals(SPOTIFY_ARTIST + artistId, track.user?.permalinkUrl)
        assertEquals(210_000L, track.duration)
        assertEquals("https://i.scdn.co/cover", track.artworkUrl)
    }

    @Test
    fun refreshesOnlyKnownCatalogOperations() {
        val hash = "a".repeat(64)
        assertEquals(
            mapOf("profileAttributes" to hash),
            SpotifyQueries.extractOperations("""x('profileAttributes','query','$hash');x('unrelated','query','$hash')""")
        )
    }

    @Test
    fun hidesAuthorizationAndAllCookiesFromDiagnostics() {
        assertEquals("Authorization: …", DiagnosticsReport.redact("Authorization: Bearer fake-token"))
        assertEquals("Cookie: …", DiagnosticsReport.redact("Cookie: sp_dc=first; other=second"))
        assertEquals("https://open.spotify.com/callback?…", DiagnosticsReport.redact("https://open.spotify.com/callback?code=hidden&state=test"))
    }

    @Test
    fun allowsSpotifyAndIdentityHostsForSignInOnly() {
        assertTrue(spotifySignInHostAllowed("accounts.spotify.com"))
        assertTrue(spotifySignInHostAllowed("challenge.spotify.com"))
        assertTrue(spotifySignInHostAllowed("open.spotify.com"))
        assertTrue(spotifySignInHostAllowed("accounts.google.com"))
        assertTrue(spotifySignInHostAllowed("appleid.apple.com"))
        assertTrue(spotifySignInHostAllowed("m.facebook.com"))
        assertFalse(spotifySignInHostAllowed("spotify.com.untrusted.test"))
        assertFalse(spotifySignInHostAllowed("example.org"))
        assertFalse(spotifySignInHostAllowed(null))
    }
}
