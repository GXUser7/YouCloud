package com.example.myapplication.player.cast

import androidx.media3.common.C
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CastSupportTest {

    private data class FakeItem(val id: String, val hasLocation: Boolean = true)

    private fun newState() = CastQueueState<FakeItem>(
        idOf = { it.id },
        hasLocation = { it.hasLocation }
    )

    @Test
    fun disconnectingAfterStartingNewQueueOnCastRestoresNewQueueAtCurrentTrack() {
        val state = newState()
        state.seedFromLocal(
            localItems = listOf(FakeItem("old-1"), FakeItem("old-2")),
            index = 1,
            positionMs = 45_000L,
            playing = true
        )

        val wave = listOf(FakeItem("wave-1"), FakeItem("wave-2"), FakeItem("wave-3"))
        state.onSetMediaItems(wave, startIndex = 0, startPositionMs = 0L)
        state.syncFromCast(
            castMediaId = "wave-2",
            castIndex = 1,
            castCount = 3,
            castPositionMs = 74_000L,
            castPlaying = true,
            castPlaybackState = Player.STATE_READY
        )

        // Cast session disconnects: CastPlayer drops to IDLE with 0 items.
        state.syncFromCast(
            castMediaId = null,
            castIndex = -1,
            castCount = 0,
            castPositionMs = 0L,
            castPlaying = false,
            castPlaybackState = Player.STATE_IDLE
        )

        val snap = state.snapshot()
        assertEquals(listOf("wave-1", "wave-2", "wave-3"), snap.items.map { it.id })
        assertEquals(1, snap.index)
        assertEquals(74_000L, snap.positionMs)
        assertTrue(snap.playing)
    }

    @Test
    fun queueRefillsRemovalsReplacesAndReorderKeepingCurrentStayExact() {
        val state = newState()
        state.seedFromLocal(
            localItems = listOf(FakeItem("a"), FakeItem("b"), FakeItem("c"), FakeItem("d")),
            index = 2,
            positionMs = 12_000L,
            playing = true
        )

        // Exact reorderQueueKeepingCurrent pattern around item "c" at index 2 -> new index 1:
        // remove after ("d"), add before ("x"), remove old before ("a","b"), add after ("y","z").
        state.onRemoveMediaItems(3, 4) // [a, b, c] (index=2)
        state.onAddMediaItems(2, listOf(FakeItem("x"))) // [a, b, x, c] (index=3)
        state.onRemoveMediaItems(0, 2) // [x, c] (index=1)
        state.onAddMediaItems(2, listOf(FakeItem("y"), FakeItem("z"))) // [x, c, y, z] (index=1)

        var snap = state.snapshot()
        assertEquals(listOf("x", "c", "y", "z"), snap.items.map { it.id })
        assertEquals(1, snap.index)

        // Move "z" (index 3) ahead of "c" (to index 0) -> [z, x, c, y], "c" moves from 1 to 2.
        state.onMoveMediaItems(3, 4, 0)
        snap = state.snapshot()
        assertEquals(listOf("z", "x", "c", "y"), snap.items.map { it.id })
        assertEquals(2, snap.index)

        // Replace item "x" with "x2".
        state.onReplaceMediaItems(1, 2, listOf(FakeItem("x2")))
        snap = state.snapshot()
        assertEquals(listOf("z", "x2", "c", "y"), snap.items.map { it.id })
        assertEquals(2, snap.index)

        // Partial item lookup uses known items remembered earlier.
        state.onReplaceMediaItems(0, 1, listOf(FakeItem("a", hasLocation = false)))
        assertEquals(FakeItem("a", hasLocation = true), state.itemAt(0))
    }

    @Test
    fun tailMendingDoesNotDuplicateAlreadyTrackedItems() {
        val state = newState()
        state.onSetMediaItems(
            listOf(FakeItem("t1"), FakeItem("t2"), FakeItem("t3"), FakeItem("t4")),
            startIndex = 0,
            startPositionMs = 0L
        )

        // Simulate MusicPlayer.mendQueueSoon appending t3, t4 when Cast only reported count=2.
        state.onAddMediaItems(
            index = 2,
            added = listOf(FakeItem("t3"), FakeItem("t4")),
            castCountBeforeAdd = 2
        )

        assertEquals(listOf("t1", "t2", "t3", "t4"), state.snapshot().items.map { it.id })
    }

    @Test
    fun syncFromCastIgnoresStaleMediaIdAndPreservesStateOnDisconnect() {
        val state = newState()
        state.onSetMediaItems(listOf(FakeItem("new-1"), FakeItem("new-2")), startIndex = 0, startPositionMs = 0L)
        state.onPlayWhenReadyChanged(true)
        state.onSeek(1, 25_000L)

        // Stale Cast status from an older queue that isn't in items anymore must be ignored.
        state.syncFromCast(
            castMediaId = "stale-from-previous-queue",
            castIndex = 0,
            castCount = 2,
            castPositionMs = 99_000L,
            castPlaying = false,
            castPlaybackState = Player.STATE_READY
        )

        val snap = state.snapshot()
        assertEquals(1, snap.index)
        assertEquals(25_000L, snap.positionMs)
        assertTrue(snap.playing)
    }

    @Test
    fun effectiveDurationGuardsAgainstTenSecondHlsSegmentArtifact() {
        // 10s reported for a 3-minute track -> use track metadata 180s.
        assertEquals(180_000L, resolveEffectiveCastDurationMs(10_000L, 1_000L, 180_000L))
        // Unset or zero duration -> fall back to track metadata.
        assertEquals(180_000L, resolveEffectiveCastDurationMs(C.TIME_UNSET, 0L, 180_000L))
        assertEquals(180_000L, resolveEffectiveCastDurationMs(0L, 0L, 180_000L))
        // Playback position passed a broken short reported duration -> fall back to metadata.
        assertEquals(180_000L, resolveEffectiveCastDurationMs(12_000L, 45_000L, 180_000L))
        // Legitimate shorter resolved audio (1:20 vs 2:06 Spotify metadata) -> keep real 1:20.
        assertEquals(80_000L, resolveEffectiveCastDurationMs(80_000L, 15_000L, 126_000L))
        // Short track (<30s) genuinely lasting 10s -> keep 10s.
        assertEquals(10_000L, resolveEffectiveCastDurationMs(10_000L, 2_000L, 11_000L))
    }

    @Test
    fun rewriteHlsPlaylistPreservesVodTagsAndRewritesSegmentsKeysAndMaps() {
        val playlist = """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:10
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXT-X-KEY:METHOD=AES-128,URI="../keys/k.bin"
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:10.0,
            seg-001.m4s
            #EXTINF:10.0,
            https://cdn.example.com/audio/seg-002.m4s?token=abc
            #EXT-X-ENDLIST
        """.trimIndent()

        val seenUrls = mutableListOf<String>()
        var nextId = 0
        val rewritten = rewriteHlsPlaylist("https://cdn.example.com/hls/v1/list.m3u8", playlist) { url ->
            seenUrls += url
            "http://192.168.1.5:8080/s/${++nextId}"
        }

        assertEquals(
            listOf(
                "https://cdn.example.com/hls/keys/k.bin",
                "https://cdn.example.com/hls/v1/init.mp4",
                "https://cdn.example.com/hls/v1/seg-001.m4s",
                "https://cdn.example.com/audio/seg-002.m4s?token=abc"
            ),
            seenUrls
        )
        assertTrue(rewritten.contains("#EXT-X-KEY:METHOD=AES-128,URI=\"http://192.168.1.5:8080/s/1\""))
        assertTrue(rewritten.contains("#EXT-X-MAP:URI=\"http://192.168.1.5:8080/s/2\""))
        assertTrue(rewritten.contains("http://192.168.1.5:8080/s/3"))
        assertTrue(rewritten.contains("http://192.168.1.5:8080/s/4"))
        assertTrue(rewritten.endsWith("#EXT-X-ENDLIST"))
        assertFalse(rewritten.contains("seg-001.m4s"))
    }

    @Test
    fun sniffDetectsContainerTypesForAudioAndMpegTs() {
        val ftyp = byteArrayOf(0, 0, 0, 20, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        val id3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0)
        val ogg = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte(), 0)
        val webm = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        val ts = byteArrayOf(0x47, 0x40, 0x00, 0x10)

        assertEquals("audio/mp4", CastStreamServer.sniff(ftyp, ftyp.size))
        assertEquals("audio/mpeg", CastStreamServer.sniff(id3, id3.size))
        assertEquals("audio/ogg", CastStreamServer.sniff(ogg, ogg.size))
        assertEquals("audio/webm", CastStreamServer.sniff(webm, webm.size))
        assertEquals("video/mp2t", CastStreamServer.sniff(ts, ts.size))
    }
}
