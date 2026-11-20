package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PageCacheTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun track(id: Long, title: String) = SoundCloudTrack(
        id = id,
        urn = "spotify:track:4uLU6hMCjMI75M1A2tKUQ$id",
        kind = "track",
        title = title,
        duration = 180_000L,
        user = SoundCloudUser(username = "Artist"),
        streamable = true
    )

    @Test
    fun persistsTracksAcrossInstancesAndRespectsAlbumVsPlaylistFreshness() {
        var now = 1_000_000L
        val dir = tempFolder.newFolder("pages")
        val cache1 = PageCache(dir, nowMs = { now })

        val album = SoundCloudPlaylist(id = 1L, title = "Album", permalinkUrl = "${SPOTIFY_ALBUM}1234567890123456789012")
        val playlist = SoundCloudPlaylist(id = 2L, title = "Liked", permalinkUrl = SPOTIFY_LIKED)
        val tracks = listOf(track(1L, "One"), track(2L, "Two"))

        cache1.putTracks(PageCache.setKey(album), tracks)
        cache1.putTracks(PageCache.setKey(playlist), tracks)

        // Fresh instance simulates app restart: RAM is empty, disk serves immediately.
        val cache2 = PageCache(dir, nowMs = { now })
        assertNull(cache2.getTracksMemory(PageCache.setKey(album), PageCache.freshDurationFor(album)))

        now += 10L * 60 * 1000 // +10 minutes
        val albumHit = cache2.getTracks(PageCache.setKey(album), PageCache.freshDurationFor(album), PageCache.maxAgeFor(album))
        assertNotNull(albumHit)
        assertEquals(2, albumHit!!.value.size)
        assertFalse(albumHit.needsRefresh) // Static album stays fresh for 7 days.

        val playlistHit = cache2.getTracks(PageCache.setKey(playlist), PageCache.freshDurationFor(playlist), PageCache.maxAgeFor(playlist))
        assertNotNull(playlistHit)
        assertEquals(2, playlistHit!!.value.size)
        assertTrue(playlistHit.needsRefresh) // Dynamic playlist renders instantly but refreshes quietly after 3 min.

        // Subsequent RAM lookup works instantaneously after disk warm-up.
        assertNotNull(cache2.getTracksMemory(PageCache.setKey(album), PageCache.freshDurationFor(album), PageCache.maxAgeFor(album)))
    }

    @Test
    fun markTracksStaleKeepsInstantDataWhileTriggeringBackgroundRefresh() {
        var now = 5_000_000L
        val dir = tempFolder.newFolder("pages-stale")
        val cache = PageCache(dir, nowMs = { now })
        cache.putTracks("yandex:liked", listOf(track(1L, "Fav")))

        val fresh = cache.getTracks("yandex:liked", PageCache.PLAYLIST_FRESH_MS)!!
        assertFalse(fresh.needsRefresh)

        cache.markTracksStale("yandex:liked")
        val stale = cache.getTracks("yandex:liked", PageCache.PLAYLIST_FRESH_MS)!!
        assertEquals(1, stale.value.size)
        assertTrue(stale.needsRefresh)
    }

    @Test
    fun persistsArtistPageAndClearsBothRamAndDisk() {
        var now = 10_000_000L
        val dir = tempFolder.newFolder("pages-artist")
        val cache = PageCache(dir, nowMs = { now })
        val page = CachedArtistPage(
            artist = SoundCloudUser(id = 42L, username = "Coda", permalinkUrl = "yandex:artist:42"),
            tracks = listOf(track(1L, "BLOODY STREAM")),
            playlists = listOf(SoundCloudPlaylist(id = 99L, title = "Single", isAlbum = true))
        )
        cache.putArtist("artist:yandex:42", page)

        val reloaded = PageCache(dir, nowMs = { now }).getArtist("artist:yandex:42")
        assertNotNull(reloaded)
        assertEquals("Coda", reloaded!!.value.artist.username)
        assertEquals(1, reloaded.value.tracks.size)
        assertEquals(1, reloaded.value.playlists.size)
        assertFalse(reloaded.needsRefresh)

        cache.clear()
        assertNull(cache.getArtist("artist:yandex:42"))
    }

    @Test
    fun prunesOldestDiskEntriesPastLimit() {
        var now = 20_000_000L
        val dir = tempFolder.newFolder("pages-prune")
        val cache = PageCache(dir, nowMs = { now }, maxDiskEntries = 3)
        for (i in 1..5) {
            now += 1_000L
            cache.putTracks("album:$i", listOf(track(i.toLong(), "Track $i")))
            // Ensure distinct file mtimes for deterministic LRU eviction on fast filesystems.
            dir.listFiles()?.maxByOrNull { it.lastModified() }?.setLastModified(now)
        }
        val jsonCount = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.size ?: 0
        assertEquals(3, jsonCount)
    }
}
