package com.example.myapplication.data.social

import com.example.myapplication.data.SoundCloudPlaylist
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModelsTest {

    @Test
    fun buildsTrackPayloadForSupportedServicesAndSkipsLocalAndLive() {
        val spTrack = SoundCloudTrack(
            id = 1L,
            urn = "spotify:track:4uLU6hMCjMI75M1A2tKUQC",
            title = "Never Gonna Give You Up",
            duration = 213_000L,
            artworkUrl = "https://i.scdn.co/cover",
            artists = listOf(SoundCloudUser(username = "Rick Astley"))
        )
        val payload = ChatSharePayload.fromTrack(spTrack)!!
        assertEquals("track", payload.kind)
        assertEquals("spotify", payload.service)
        assertEquals("https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC", payload.itemUrl)
        assertEquals("Never Gonna Give You Up", payload.title)
        assertEquals("Rick Astley", payload.artist)
        assertEquals(213_000L, payload.durationMs)
        assertNull(payload.trackCount)

        val local = spTrack.copy(urn = "local:123", permalinkUrl = null)
        assertNull(ChatSharePayload.fromTrack(local))

        val live = spTrack.copy(urn = "youtube:live:abc123", permalinkUrl = "https://youtube.com/watch?v=abc123")
        assertNull(ChatSharePayload.fromTrack(live))
    }

    @Test
    fun buildsAlbumPayloadWithTrackCountAndService() {
        val track = SoundCloudTrack(
            id = 10L,
            title = "6 кадров",
            duration = 93_000L,
            artworkUrl = "https://i.scdn.co/album",
            user = SoundCloudUser(username = "unki")
        )
        val album = SoundCloudPlaylist(
            id = 20L,
            title = "JERK SHIT HAPPENS",
            tracks = listOf(track),
            trackCount = 5,
            artworkUrl = "https://i.scdn.co/album",
            permalinkUrl = "spotify:album:1A2B3C4D5E6F7G8H9I0J12",
            user = SoundCloudUser(username = "unki"),
            isAlbum = true
        )
        val payload = ChatSharePayload.fromSet(album)!!
        assertEquals("album", payload.kind)
        assertEquals("spotify", payload.service)
        assertEquals("https://open.spotify.com/album/1A2B3C4D5E6F7G8H9I0J12", payload.itemUrl)
        assertEquals("JERK SHIT HAPPENS", payload.title)
        assertEquals("unki", payload.artist)
        assertEquals(5, payload.trackCount)
        assertNull(payload.durationMs)
    }

    @Test
    fun groupsReactionsAndPicksMirroredDisplayRating() {
        val reactions = listOf(
            ChatReaction(userId = "me", emoji = "🔥", rating = 4),
            ChatReaction(userId = "friend", emoji = "🔥", rating = 5),
            ChatReaction(userId = "other", emoji = "👍", rating = null),
            ChatReaction(userId = "empty", emoji = "", rating = 9)
        )
        val grouped = groupedEmojiReactions(reactions, myUserId = "me")
        assertEquals(2, grouped.size)
        assertEquals("🔥", grouped[0].emoji)
        assertEquals(2, grouped[0].count)
        assertTrue(grouped[0].mine)
        assertEquals("👍", grouped[1].emoji)
        assertEquals(1, grouped[1].count)
        assertFalse(grouped[1].mine)

        // Peer's rating takes precedence over own rating when present
        assertEquals(5, displayRatingOf(reactions, myUserId = "me"))
        assertEquals(4, displayRatingOf(reactions.filter { it.userId == "me" }, myUserId = "me"))
        assertNull(displayRatingOf(listOf(ChatReaction(userId = "x", emoji = "👍", rating = 7)), myUserId = "me"))
    }

    @Test
    fun skipsPersonalAccountSets() {
        val ytLikedVllm = SoundCloudPlaylist(
            id = 100L,
            title = "Понравившиеся",
            permalinkUrl = "ytmusic:set:VLLM"
        )
        assertNull(ChatSharePayload.fromSet(ytLikedVllm))

        val ytLikedLm = SoundCloudPlaylist(
            id = 101L,
            title = "Понравившиеся",
            permalinkUrl = "ytmusic:set:VLLM:LM"
        )
        assertNull(ChatSharePayload.fromSet(ytLikedLm))
        assertNull(ChatSharePayload.fromSet(ytLikedLm, link = "https://music.youtube.com/browse/VLLM"))
        assertNull(ChatSharePayload.fromSet(ytLikedLm, link = "https://music.youtube.com/playlist?list=LM"))

        val spLiked = SoundCloudPlaylist(
            id = 102L,
            title = "Понравившиеся треки",
            permalinkUrl = "spotify:liked"
        )
        assertNull(ChatSharePayload.fromSet(spLiked))
    }
}
