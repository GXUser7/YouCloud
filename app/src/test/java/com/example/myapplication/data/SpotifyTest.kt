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

    @Test
    fun matchesCrossServiceMetadataNoiseAndCollabsWhileRejectingWrongEditions() {
        val target = SoundCloudTrack(
            1L,
            title = "Kill Bill (with Doja Cat) - Single Version",
            duration = 154_000L,
            user = SoundCloudUser(username = "SZA"),
            artists = listOf(SoundCloudUser(username = "SZA"), SoundCloudUser(username = "Doja Cat"))
        )
        // YouTube Music video/audio upload with noise tags and combined artist byline.
        val ytCandidate = SoundCloudTrack(
            2L,
            title = "SZA - Kill Bill (Official Audio) [Explicit]",
            duration = 161_000L,
            user = SoundCloudUser(username = "SZA & Doja Cat - Topic")
        )
        assertNotNull(spotifyMatchScore(target, ytCandidate))

        // Yandex track with explicit separate version string matches safely without picking remixes.
        val yandexRemaster = YandexTrack(
            id = "999",
            title = "Kill Bill",
            artists = listOf(YandexArtist(id = "1", name = "Doja Cat"), YandexArtist(id = "2", name = "SZA")),
            durationMs = 155_000L,
            coverUri = null,
            version = "2023 Remaster"
        )
        assertNotNull(spotifyMatchScore(target, yandexRemaster.toSoundCloudTrack().copy(title = yandexRemaster.fullTitle())))

        val yandexRemix = yandexRemaster.copy(version = "Slowed Remix")
        assertNull(spotifyMatchScore(target, yandexRemix.toSoundCloudTrack().copy(title = yandexRemix.fullTitle())))

        // Single-word titles must not falsely match multi-word songs by the same artist.
        val love = SoundCloudTrack(3L, title = "Love", duration = 200_000L, user = SoundCloudUser(username = "Artist"))
        assertNull(spotifyMatchScore(love, love.copy(title = "Love Story")))
        assertNotNull(spotifyMatchScore(love, love.copy(title = "Artist - Love (Official Video)")))

        // Punctuation/apostrophe differences (FE!N vs FEIN, Don't vs Dont) and clean/video edits match.
        val fein = SoundCloudTrack(4L, title = "FE!N (feat. Playboi Carti)", duration = 191_000L, user = SoundCloudUser(username = "Travis Scott"))
        val feinCand = fein.copy(title = "Travis Scott - FEIN (Official Video) [Clean Edit]")
        assertNotNull(spotifyMatchRejectReason(fein, feinCand), spotifyMatchScore(fein, feinCand))
        val dont = SoundCloudTrack(5L, title = "Don't Stop", duration = 180_000L, user = SoundCloudUser(username = "Artist"))
        val dontCand = dont.copy(title = "Dont Stop")
        assertNotNull(spotifyMatchRejectReason(dont, dontCand), spotifyMatchScore(dont, dontCand))

        // Reuploads with "Title by Artist" or "Title - Artist" on a third-party channel match when duration agrees.
        val byTarget = SoundCloudTrack(6L, title = "ФРУКТОВЫЙ 2.0", duration = 126_000L, user = SoundCloudUser(username = "soit pas jaloux"))
        val byCand = SoundCloudTrack(7L, title = "ФРУКТОВЫЙ 2.0 by soit pas jaloux", duration = 126_000L, user = SoundCloudUser(username = "Зиач"))
        assertNotNull(spotifyMatchRejectReason(byTarget, byCand), spotifyMatchScore(byTarget, byCand))
        val dashRevCand = SoundCloudTrack(8L, title = "ФРУКТОВЫЙ 2.0 - soit pas jaloux", duration = 126_000L, user = SoundCloudUser(username = "Зиач"))
        assertNotNull(spotifyMatchRejectReason(byTarget, dashRevCand), spotifyMatchScore(byTarget, dashRevCand))

        // Shorter reupload (1:20 vs 2:06) is rejected strictly, accepted ONLY via loose-duration exact fallback, never for 30s previews.
        val shortCand = byCand.copy(duration = 80_000L)
        assertNull(spotifyMatchScore(byTarget, shortCand))
        assertNotNull(spotifyLooseDurationMatchScore(byTarget, shortCand))
        assertNull(spotifyLooseDurationMatchScore(byTarget, byCand.copy(duration = 30_000L)))

        // Backing tracks, instrumentals, minus channels, and tutorials must be rejected strictly AND loosely.
        val bloody = SoundCloudTrack(9L, title = "BLOODY STREAM", duration = 261_000L, user = SoundCloudUser(username = "Coda"))
        val guitarMinus = SoundCloudTrack(
            10L,
            title = "Coda - Bloody Stream (guitar backing track)",
            duration = 261_000L,
            user = SoundCloudUser(username = "Guitar Minus Tracks")
        )
        assertNull(spotifyMatchScore(bloody, guitarMinus))
        assertNull(spotifyLooseDurationMatchScore(bloody, guitarMinus))
        // Even if the title omits "(backing track)", a Minus / Karaoke uploader channel is still rejected.
        val hiddenMinus = SoundCloudTrack(
            11L,
            title = "Coda - Bloody Stream",
            duration = 261_000L,
            user = SoundCloudUser(username = "Guitar Minus Tracks")
        )
        assertNull(spotifyMatchScore(bloody, hiddenMinus))
        assertNull(spotifyLooseDurationMatchScore(bloody, hiddenMinus))
        // Original songs whose own title contains an instrument word ("Piano Man") still match.
        val pianoMan = SoundCloudTrack(12L, title = "Piano Man", duration = 339_000L, user = SoundCloudUser(username = "Billy Joel"))
        assertNotNull(spotifyMatchScore(pianoMan, pianoMan.copy(title = "Billy Joel - Piano Man (Official Video)")))

        // Rhythm-game playthroughs (osu!, HDDT/HDHR, pp, accuracy %, handcam/typing) must be rejected, while (HD)/(4K) music videos stay valid.
        val osuTitle = SoundCloudTrack(13L, title = "Coda - BLOODY STREAM [osu! +HDDT 99.42% FC 512pp]", duration = 261_000L, user = SoundCloudUser(username = "Player123"))
        assertNull(spotifyMatchScore(bloody, osuTitle))
        assertNull(spotifyLooseDurationMatchScore(bloody, osuTitle))
        val osuHandcam = SoundCloudTrack(14L, title = "Coda - BLOODY STREAM (handcam typing)", duration = 261_000L, user = SoundCloudUser(username = "osu! liveplays"))
        assertNull(spotifyMatchScore(bloody, osuHandcam))
        val osuChannelOnly = SoundCloudTrack(15L, title = "Coda - BLOODY STREAM", duration = 261_000L, user = SoundCloudUser(username = "Best osu! replays"))
        assertNull(spotifyMatchScore(bloody, osuChannelOnly))
        val officialHd = SoundCloudTrack(16L, title = "Coda - BLOODY STREAM (Official Video) [HD] (4K)", duration = 261_000L, user = SoundCloudUser(username = "Coda - Topic"))
        assertNotNull(spotifyMatchRejectReason(bloody, officialHd), spotifyMatchScore(bloody, officialHd))

        // Live-verified anime cases:
        // 1) Official bilingual channel "Kenshi Yonezu  米津玄師" matches "KICK BACK" even with its real 3:48 MV length
        //    (+35s intro/outro), decisively outranks a 47-view random upload titled "Kenshi Yonezu - KICK BACK",
        //    and rejects Dance Practice / Live / short teasers / dated replays / arrangements / AI / piano covers.
        val kickBack = SoundCloudTrack(17L, title = "KICK BACK", duration = 193_000L, user = SoundCloudUser(username = "Kenshi Yonezu"))
        val officialMv = SoundCloudTrack(
            18L,
            title = "米津玄師 Kenshi Yonezu - KICKBACK",
            duration = 228_000L,
            user = SoundCloudUser(username = "Kenshi Yonezu  米津玄師"),
            playbackCount = 246_557_783L
        )
        val officialMvScore = spotifyMatchScore(kickBack, officialMv)
        assertNotNull(spotifyMatchRejectReason(kickBack, officialMv), officialMvScore)
        val lowViewOsuCandidate = SoundCloudTrack(
            27L,
            title = "Kenshi Yonezu - KICK BACK",
            duration = 202_000L,
            user = SoundCloudUser(username = "얼붕쓰"),
            playbackCount = 47L
        )
        val lowViewScore = spotifyMatchScore(kickBack, lowViewOsuCandidate)!!
        assertTrue(
            "Expected official 246M-view MV (${officialMvScore!! + SpotifyAudio.BONUS_YT_WEB}) to outrank 47-view random upload (${lowViewScore + SpotifyAudio.BONUS_YT_VIDEO})",
            officialMvScore + SpotifyAudio.BONUS_YT_WEB > lowViewScore + SpotifyAudio.BONUS_YT_VIDEO
        )
        // Exact-length official Topic Art Track still outranks a longer Official MV.
        val officialTopic = SoundCloudTrack(
            28L,
            title = "KICK BACK",
            duration = 193_000L,
            user = SoundCloudUser(username = "Kenshi Yonezu - Topic"),
            playbackCount = 10_000_000L
        )
        assertTrue(
            spotifyMatchScore(kickBack, officialTopic)!! + SpotifyAudio.BONUS_YT_SONG >
                officialMvScore + SpotifyAudio.BONUS_YT_WEB
        )
        // Non-song official channel videos (Dance Practice, Live, 33s teaser) stay rejected.
        assertNull(spotifyMatchScore(kickBack, SoundCloudTrack(29L, title = "Kenshi Yonezu – KICK BACK (Dance Practice)", duration = 208_000L, user = SoundCloudUser(username = "Kenshi Yonezu  米津玄師"))))
        assertNull(spotifyMatchScore(kickBack, SoundCloudTrack(30L, title = "Kenshi Yonezu - KICK BACK（Live with Daiki Tsuneta）", duration = 203_000L, user = SoundCloudUser(username = "Kenshi Yonezu  米津玄師"))))
        assertNull(spotifyMatchScore(kickBack, SoundCloudTrack(31L, title = "米津玄師 Kenshi Yonezu - KICKBACK", duration = 33_000L, user = SoundCloudUser(username = "Kenshi Yonezu  米津玄師"))))
        assertNull(spotifyMatchScore(kickBack, SoundCloudTrack(19L, title = "260912 Kenshi Yonezu - KICK BACK", duration = 193_000L, user = SoundCloudUser(username = "극초음속 코끼리"))))
        assertNull(spotifyMatchScore(kickBack, SoundCloudTrack(20L, title = "Kenshi Yonezu - KICK BACK (arrangement)", duration = 193_000L, user = SoundCloudUser(username = "Jay1212"))))
        assertNull(spotifyMatchScore(kickBack, SoundCloudTrack(21L, title = "jammable ai: Kenshi Yonezu - KICK BACK", duration = 193_000L, user = SoundCloudUser(username = "cat"))))
        assertNull(spotifyMatchScore(kickBack, SoundCloudTrack(22L, title = "KICK BACK (Kenshi Yonezu)", duration = 193_000L, user = SoundCloudUser(username = "Ru's Piano - Topic"))))
        assertNull(spotifyMatchScore(kickBack, SoundCloudTrack(23L, title = "Kenshi Yonezu - Kick Back but in the Sonic soundfont", duration = 193_000L, user = SoundCloudUser(username = "Scepty"))))

        // Post-extraction metadata filter rejects third-party Gaming/osu! uploads while sparing official channels and normal Music/People & Blogs uploads.
        assertTrue(spotifyYouTubeMetadataRejected(kickBack, isOfficialChannel = false, category = "Gaming", tags = emptyList(), viewCount = 47L))
        assertTrue(spotifyYouTubeMetadataRejected(kickBack, isOfficialChannel = false, category = "Entertainment", tags = listOf("osu!", "beatmap"), viewCount = 120_000L))
        assertFalse(spotifyYouTubeMetadataRejected(kickBack, isOfficialChannel = true, category = "Music", tags = listOf("Kenshi Yonezu"), viewCount = 246_557_783L))
        assertFalse(spotifyYouTubeMetadataRejected(bloody, isOfficialChannel = false, category = "People & Blogs", tags = listOf("jojo", "bloody stream"), viewCount = 3_809L))

        // Last-resort popular YouTube fallback ranks the highest-viewed matching video first while still rejecting osu!/karaoke/minus tracks.
        val popular10M = SoundCloudTrack(32L, title = "BLOODY STREAM - Special Anime Upload", duration = 260_000L, user = SoundCloudUser(username = "AnimeChannel"), playbackCount = 10_000_000L)
        val popular500 = SoundCloudTrack(33L, title = "BLOODY STREAM - Upload", duration = 260_000L, user = SoundCloudUser(username = "SmallChannel"), playbackCount = 500L)
        assertTrue(spotifyPopularYouTubeFallbackScore(bloody, popular10M)!! > spotifyPopularYouTubeFallbackScore(bloody, popular500)!!)
        assertNull(spotifyPopularYouTubeFallbackScore(bloody, osuTitle))
        assertNull(spotifyPopularYouTubeFallbackScore(bloody, guitarMinus))
        // Cross-script YouTube uploads (e.g. Spotify "Tetoris" by "Hiiragi Magnetite" vs YouTube "テトリス / 重音テトSV" by "柊マグネタイト")
        // still get a positive fallback score so the track never skips when YouTube returned playable results.
        val tetorisSp = SoundCloudTrack(34L, title = "Tetoris", duration = 142_000L, user = SoundCloudUser(username = "Hiiragi Magnetite"))
        val tetorisYtJp = SoundCloudTrack(35L, title = "テトリス / 重音テトSV", duration = 142_000L, user = SoundCloudUser(username = "柊マグネタイト"), playbackCount = 55_000_000L)
        val tetorisYtOverlap = SoundCloudTrack(36L, title = "Tetoris (テトリス)", duration = 142_000L, user = SoundCloudUser(username = "柊マグネタイト"), playbackCount = 5_000_000L)
        val jpScore = spotifyPopularYouTubeFallbackScore(tetorisSp, tetorisYtJp)
        val overlapScore = spotifyPopularYouTubeFallbackScore(tetorisSp, tetorisYtOverlap)
        assertNotNull(jpScore)
        assertNotNull(overlapScore)
        assertTrue(overlapScore!! > jpScore!!)

        // 2) Full Bloody Stream lyric video (4:19) matches despite anime prefix + "(Color Coded Lyrics ...)", while Piano Trio / +4Key karaoke are rejected.
        val bloodyFullLyric = SoundCloudTrack(
            24L,
            title = "JoJo's Bizarre Adventure OP2: Coda - BLOODY STREAM Lyrics (Color Coded Lyrics Kan/Rom/Eng)",
            duration = 259_000L,
            user = SoundCloudUser(username = "Anime Lyrics")
        )
        assertNotNull(spotifyMatchRejectReason(bloody, bloodyFullLyric), spotifyMatchScore(bloody, bloodyFullLyric))
        assertNull(spotifyMatchScore(bloody, SoundCloudTrack(25L, title = "BLOODY STREAM", duration = 261_000L, user = SoundCloudUser(username = "Coda Piano Trio - Topic"))))
        assertNull(spotifyMatchScore(bloody, SoundCloudTrack(26L, title = "BLOODY STREAM +4Key (原曲歌手:Coda)", duration = 261_000L, user = SoundCloudUser(username = "歌っちゃ王 - Topic"))))
    }

    @Test
    fun mergesRepeatedSpotifyHomeSectionTitlesIntoSingleShelves() {
        val p1 = "37i9dQZF1DXcBWIGoYBM5M"
        val p2 = "37i9dQZF1DX0XUsuxWHRQd"
        val response = JsonParser.parseString("""
            {"home":{"sectionContainer":{"sections":{"items":[
                {
                    "data":{"title":{"transformedLabel":"Сделано для тебя"}},
                    "sectionItems":{"items":[
                        {"content":{"data":{"__typename":"Playlist","uri":"spotify:playlist:$p1","name":"Mix 1"}}}
                    ]}
                },
                {
                    "data":{"title":{"text":"  Сделано для тебя "}},
                    "sectionItems":{"items":[
                        {"content":{"data":{"__typename":"Playlist","uri":"spotify:playlist:$p1","name":"Mix 1"}}},
                        {"content":{"data":{"__typename":"Playlist","uri":"spotify:playlist:$p2","name":"Mix 2"}}}
                    ]}
                }
            ]}}}}
        """.trimIndent()).asJsonObject

        val rows = spotifyHomeRows(response)
        assertEquals(1, rows.size)
        assertEquals("Сделано для тебя", rows.single().title)
        assertEquals(2, rows.single().sets.size)
    }

    @Test
    fun buildsCleanQueriesAndExtractsBothFirstAndOtherArtists() {
        val raw = JsonParser.parseString("""
            {"uri":"spotify:track:$id","name":"Track Name (feat. Guest) [Explicit]","trackDuration":{"totalMilliseconds":180000},
             "firstArtist":{"items":[{"uri":"spotify:artist:$artistId","profile":{"name":"Lead Artist"}}]},
             "otherArtists":{"items":[{"uri":"spotify:artist:1TnOYISbd1XYRBk9myaseg","profile":{"name":"Second Artist"}}]},
             "albumOfTrack":{"coverArt":{"sources":[{"url":"https://i.scdn.co/cover"}]}}}
        """.trimIndent()).asJsonObject
        val track = spotifyTrack(raw)!!
        assertEquals(listOf("Lead Artist", "Second Artist"), track.artists?.map { it.username })
        assertEquals("Lead Artist Track Name", spotifyAudioQuery(track))
    }

    @Test
    fun prioritizesYouTubeOverYandexAndSoundCloudEvenForPartialMatches() {
        val target = SoundCloudTrack(1L, title = "Track Title", duration = 180_000L, user = SoundCloudUser(username = "Artist"))
        val imperfectYtVideo = spotifyMatchScore(target, target.copy(duration = 195_000L))!! + SpotifyAudio.BONUS_YT_VIDEO
        val perfectYandex = spotifyMatchScore(target, target)!! + SpotifyAudio.BONUS_YANDEX
        val perfectSoundCloud = spotifyMatchScore(target, target)!! + SpotifyAudio.BONUS_SOUNDCLOUD
        assertTrue(imperfectYtVideo > perfectYandex)
        assertTrue(perfectYandex > perfectSoundCloud)
    }

    @Test
    fun rejectsGamingMontagesLikeValorantMontage() {
        val target = SoundCloudTrack(
            100L,
            title = "Jeff the Killer",
            duration = 103_000L,
            user = SoundCloudUser(username = "unki"),
            artists = listOf(SoundCloudUser(username = "unki"), SoundCloudUser(username = "sly sheen"))
        )
        val montage = SoundCloudTrack(
            101L,
            title = "unki, sly sheen - Jeff the Killer | VALORANT montage",
            duration = 104_000L,
            user = SoundCloudUser(username = "desolated")
        )
        assertNull(spotifyMatchScore(target, montage))
        assertNull(spotifyMatchScore(target, montage, allowArtistMismatch = true))
        assertNull(spotifyLooseDurationMatchScore(target, montage))
        assertNull(spotifyLooseDurationMatchScore(target, montage, allowArtistMismatch = true))
        assertNull(spotifyPopularYouTubeFallbackScore(target, montage))
    }

    @Test
    fun allowsSoundCloudArtistMismatchWhilePreservingEditionAndRankingGuards() {
        val target = SoundCloudTrack(
            100L,
            title = "Jeff the Killer",
            duration = 103_000L,
            user = SoundCloudUser(username = "unki"),
            artists = listOf(SoundCloudUser(username = "unki"), SoundCloudUser(username = "sly sheen"))
        )
        val scReupload = SoundCloudTrack(
            102L,
            title = "Jeff the Killer",
            duration = 103_000L,
            user = SoundCloudUser(username = "archive_reuploads")
        )
        assertNull(spotifyMatchScore(target, scReupload, allowArtistMismatch = false))
        val reuploadScore = spotifyMatchScore(target, scReupload, allowArtistMismatch = true)
        assertNotNull(reuploadScore)

        val prefixedSc = scReupload.copy(id = 103L, title = "archive - Jeff the Killer", user = SoundCloudUser(username = "random_user"))
        assertNotNull(spotifyMatchScore(target, prefixedSc, allowArtistMismatch = true))

        val titleArtistSc = scReupload.copy(id = 104L, title = "unki - Jeff the Killer")
        val titleArtistScore = spotifyMatchScore(target, titleArtistSc, allowArtistMismatch = true)!!
        val officialSc = scReupload.copy(id = 105L, title = "Jeff the Killer", user = SoundCloudUser(username = "unki"))
        val officialScore = spotifyMatchScore(target, officialSc, allowArtistMismatch = true)!!
        assertTrue(officialScore > titleArtistScore)
        assertTrue(titleArtistScore > reuploadScore!!)

        // Wrong editions and short previews must still be rejected even with allowArtistMismatch = true.
        assertNull(spotifyMatchScore(target, scReupload.copy(title = "Jeff the Killer (slowed + reverb)"), allowArtistMismatch = true))
        assertNull(spotifyMatchScore(target, scReupload.copy(title = "Jeff the Killer | VALORANT montage"), allowArtistMismatch = true))
        assertNull(spotifyMatchScore(target, scReupload.copy(title = "Jeff the Killer (karaoke)"), allowArtistMismatch = true))
        assertNull(spotifyLooseDurationMatchScore(target, scReupload.copy(duration = 30_000L), allowArtistMismatch = true))
    }

    @Test
    fun prioritizesSoundCloudOverYouTubeWhenSoundCloudPrioritySelected() {
        val target = SoundCloudTrack(1L, title = "Track Title", duration = 180_000L, user = SoundCloudUser(username = "Artist"))
        val scReupload = SoundCloudTrack(2L, title = "Track Title", duration = 180_000L, user = SoundCloudUser(username = "random_uploader"))
        val ytStrict = spotifyMatchScore(target, target)!! + SpotifyAudio.BONUS_YT_FALLBACK_SONG
        val scPriority = spotifyMatchScore(target, scReupload, allowArtistMismatch = true)!! + SpotifyAudio.BONUS_SC_PRIORITY
        assertTrue(scPriority > ytStrict)
    }

    @Test
    fun infersCastMimeTypeFromCachedSpotifyStreamKeys() {
        assertEquals("audio/webm", StreamCache.mimeTypeForSpotifyKey("stream:spotify:$id:yt_abc12345678_251"))
        assertEquals("audio/mp4", StreamCache.mimeTypeForSpotifyKey("stream:spotify:$id:yt_abc12345678_140"))
        assertEquals("audio/mpeg", StreamCache.mimeTypeForSpotifyKey("stream:spotify:$id:ya_98765"))
        assertEquals("audio/mpeg", StreamCache.mimeTypeForSpotifyKey("stream:spotify:$id:sc_12345"))
    }

    @Test
    fun parsesYouTubeLibraryPlaylistsSkippingAlbumsAndPodcasts() {
        val json = JsonParser.parseString("""
            {"items":[
                {"musicTwoRowItemRenderer":{
                    "title":{"runs":[{"text":"Liked Music"}]},
                    "subtitle":{"runs":[{"text":"Auto playlist"}]},
                    "navigationEndpoint":{"browseEndpoint":{"browseId":"VLLM","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}
                }},
                {"musicTwoRowItemRenderer":{
                    "title":{"runs":[{"text":"Episodes for Later"}]},
                    "navigationEndpoint":{"browseEndpoint":{"browseId":"VLSE","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}
                }},
                {"musicTwoRowItemRenderer":{
                    "title":{"runs":[{"text":"My Mix"}]},
                    "subtitle":{"runs":[{"text":"You"}]},
                    "navigationEndpoint":{"browseEndpoint":{"browseId":"VLPL123","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_PLAYLIST"}}}}
                }},
                {"musicTwoRowItemRenderer":{
                    "title":{"runs":[{"text":"Some Album"}]},
                    "navigationEndpoint":{"browseEndpoint":{"browseId":"MPREb_123","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ALBUM"}}}}
                }}
            ]}
        """.trimIndent())
        val parsed = YouTubeMusicClient { null }.parseLibraryPlaylists(json)
        assertEquals(listOf("Liked Music", "My Mix"), parsed.map { it.title })
        assertTrue(parsed.first().isYtLikedMusic)
    }
}
