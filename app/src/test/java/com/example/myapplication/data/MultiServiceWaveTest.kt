package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MultiServiceWaveTest {
    private fun track(id: Long, urn: String, title: String, artist: String) = SoundCloudTrack(
        id = id,
        urn = urn,
        kind = "track",
        title = title,
        duration = 180_000L,
        user = SoundCloudUser(username = artist),
        streamable = true
    )

    @Test
    fun deduplicatesSameSongAcrossDifferentServices() {
        val mixer = MultiServiceWaveMixer(Random(42))
        val ya = track(-1001L, "yandex:track:1", "Зависим", "Heronwater")
        val yt = track(-2001L, "${YT_TRACK_URN}abc12345678", "Heronwater - Зависим (Official Audio)", "Heronwater - Topic")
        val sp = track(-3001L, "spotify:track:7nA9LnuXcL6bt6GUsTml4F", "Зависим [Explicit]", "Heronwater")
        val sc = track(4001L, "soundcloud:tracks:4001", "Другой трек", "Другой артист")

        assertEquals(waveTrackFingerprint(ya), waveTrackFingerprint(yt))
        assertEquals(waveTrackFingerprint(ya), waveTrackFingerprint(sp))

        val mixed = mixer.mix(
            mapOf(
                WaveSource.YANDEX to listOf(ya),
                WaveSource.YOUTUBE to listOf(yt),
                WaveSource.SPOTIFY to listOf(sp),
                WaveSource.SOUNDCLOUD to listOf(sc)
            ),
            targetCount = 10
        )
        assertEquals(2, mixed.size)
        assertEquals(1, mixed.count { waveLeadArtistKey(it) == "heronwater" })
        assertTrue(mixed.any { it.id == sc.id })
    }

    @Test
    fun interleavesConnectedServicesRoundRobinWithoutLongStreaks() {
        val mixer = MultiServiceWaveMixer(Random(7))
        val byService = mapOf(
            WaveSource.YANDEX to (1..4).map { track(-100L - it, "yandex:track:$it", "Ya $it", "YaArtist $it") },
            WaveSource.SOUNDCLOUD to (1..4).map { track(200L + it, "soundcloud:tracks:$it", "Sc $it", "ScArtist $it") },
            WaveSource.YOUTUBE to (1..4).map { track(-300L - it, "${YT_TRACK_URN}vid$it", "Yt $it", "YtArtist $it") },
            WaveSource.SPOTIFY to (1..4).map { track(-400L - it, "spotify:track:4uLU6hMCjMI75M1A2tKUQ$it", "Sp $it", "SpArtist $it") }
        )

        val mixed = mixer.mix(byService, targetCount = 8)
        assertEquals(8, mixed.size)
        // First 4 tracks must come from 4 distinct services (round-robin first pass).
        assertEquals(4, mixed.take(4).map(::waveSourceOf).toSet().size)
        assertEquals(4, mixed.drop(4).map(::waveSourceOf).toSet().size)
    }

    @Test
    fun respectsDislikedArtistsAndDiscoverExclusionsAcrossServices() {
        val mixer = MultiServiceWaveMixer(Random(1))
        val hated = track(-10L, "yandex:track:10", "Bad Song", "Toxic Artist")
        mixer.recordDislike(hated)

        val hatedOnYt = track(-20L, "${YT_TRACK_URN}v1", "Another Song", "Toxic Artist - Topic")
        val alreadyLiked = track(30L, "soundcloud:tracks:30", "Liked Song", "Good Artist")
        val freshSong = track(-40L, "spotify:track:4uLU6hMCjMI75M1A2tKUQA", "Fresh Song", "New Artist")

        val mixed = mixer.mix(
            mapOf(
                WaveSource.YOUTUBE to listOf(hatedOnYt),
                WaveSource.SOUNDCLOUD to listOf(alreadyLiked),
                WaveSource.SPOTIFY to listOf(freshSong)
            ),
            targetCount = 1,
            excludeIds = setOf(alreadyLiked.id)
        )
        assertEquals(listOf(freshSong.id), mixed.map { it.id })
        assertFalse(mixed.any { it.id == hatedOnYt.id })
    }

    @Test
    fun prioritizesPositiveTracksAsNextSeeds() {
        val mixer = MultiServiceWaveMixer(Random(3))
        val enjoyed = track(500L, "soundcloud:tracks:500", "Loved SC", "SC Hero")
        val fallback = track(501L, "soundcloud:tracks:501", "Other SC", "SC Other")
        mixer.recordPositive(enjoyed)

        assertEquals(enjoyed.id, mixer.pickSeed(WaveSource.SOUNDCLOUD, listOf(fallback))?.id)
        assertEquals(fallback.id, mixer.pickSeed(WaveSource.SOUNDCLOUD, listOf(fallback))?.id)
    }

    @Test
    fun identifiesSoundCloudTracksExcludingLocalAndOtherServices() {
        assertTrue(isSoundCloudTrack(track(12345L, "soundcloud:tracks:12345", "SC", "Artist")))
        assertTrue(isSoundCloudTrack(SoundCloudTrack(id = 12346L, urn = null, title = "Legacy SC")))
        assertFalse(isSoundCloudTrack(track(100_000_000_001L, "local:track:1", "Local", "Phone")))
        assertFalse(isSoundCloudTrack(track(-100L, "yandex:track:1", "Ya", "Artist")))
        assertFalse(isSoundCloudTrack(track(-200L, "${YT_TRACK_URN}abcdefghijk", "YT", "Artist")))
        assertFalse(isSoundCloudTrack(track(-300L, "spotify:track:4uLU6hMCjMI75M1A2tKUQC", "Sp", "Artist")))
    }
}
