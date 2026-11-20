package com.example.myapplication.data

import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Cached snapshot of an artist page across SoundCloud, Yandex Music, YouTube Music or Spotify. */
data class CachedArtistPage(
    val artist: SoundCloudUser,
    val tracks: List<SoundCloudTrack> = emptyList(),
    val playlists: List<SoundCloudPlaylist> = emptyList(),
    val lives: List<SoundCloudTrack> = emptyList(),
    val shelves: List<YtShelf> = emptyList(),
    val allSongsPlaylist: SoundCloudPlaylist? = null,
    val tracksAreVideos: Boolean = false,
    val allTracksLoaded: Boolean = false,
    val subscribed: Boolean? = null,
    val subscribeParams: String? = null,
    val unsubscribeParams: String? = null
)

/** Result of a cache lookup: [value] can be rendered immediately; [needsRefresh] asks for a quiet background reload. */
data class CachedPage<T>(
    val value: T,
    val needsRefresh: Boolean
)

/**
 * Two-tier (RAM + disk JSON under `cacheDir/pages`) cache for opened albums, playlists, mixes, and
 * artist pages. Static albums stay fresh for days so reopening them makes zero network requests;
 * dynamic playlists and artist pages render immediately from cache and refresh quietly in the
 * background once past their freshness window (stale-while-revalidate).
 */
class PageCache(
    private val directory: File,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxDiskEntries: Int = MAX_DISK_ENTRIES
) {
    private val gson = Gson()
    private val lock = Any()

    private val tracksMemory = object : LinkedHashMap<String, TracksEnvelope>(MAX_MEMORY_SETS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TracksEnvelope>?): Boolean =
            size > MAX_MEMORY_SETS
    }
    private val artistMemory = object : LinkedHashMap<String, ArtistEnvelope>(MAX_MEMORY_ARTISTS, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ArtistEnvelope>?): Boolean =
            size > MAX_MEMORY_ARTISTS
    }

    private data class TracksEnvelope(
        val savedAtMs: Long,
        val tracks: List<SoundCloudTrack>
    )

    private data class ArtistEnvelope(
        val savedAtMs: Long,
        val page: CachedArtistPage
    )

    /** Fast synchronous RAM lookup (safe on the UI thread) so cached pages open on the very first frame. */
    fun getTracksMemory(key: String, freshMs: Long, maxAgeMs: Long = MAX_AGE_MS): CachedPage<List<SoundCloudTrack>>? {
        val env = synchronized(lock) { tracksMemory[key] } ?: return null
        return evaluate(env.savedAtMs, env.tracks, freshMs, maxAgeMs)
    }

    /** Full RAM + disk lookup (call off the main thread when [getTracksMemory] missed). */
    fun getTracks(key: String, freshMs: Long, maxAgeMs: Long = MAX_AGE_MS): CachedPage<List<SoundCloudTrack>>? {
        getTracksMemory(key, freshMs, maxAgeMs)?.let { return it }
        val file = fileFor("tracks", key)
        val env = readJson(file, TracksEnvelope::class.java) ?: return null
        val result = evaluate(env.savedAtMs, env.tracks, freshMs, maxAgeMs)
        if (result == null) {
            runCatching { file.delete() }
            return null
        }
        synchronized(lock) { tracksMemory[key] = env }
        return result
    }

    fun putTracks(key: String, tracks: List<SoundCloudTrack>) {
        if (tracks.isEmpty()) return
        val env = TracksEnvelope(savedAtMs = nowMs(), tracks = tracks)
        synchronized(lock) { tracksMemory[key] = env }
        writeJson(fileFor("tracks", key), env)
    }

    /** Fast synchronous RAM lookup for an artist page. */
    fun getArtistMemory(key: String, freshMs: Long = ARTIST_FRESH_MS, maxAgeMs: Long = MAX_AGE_MS): CachedPage<CachedArtistPage>? {
        val env = synchronized(lock) { artistMemory[key] } ?: return null
        return evaluate(env.savedAtMs, env.page, freshMs, maxAgeMs)
    }

    /** Full RAM + disk lookup for an artist page (call off the main thread on RAM miss). */
    fun getArtist(key: String, freshMs: Long = ARTIST_FRESH_MS, maxAgeMs: Long = MAX_AGE_MS): CachedPage<CachedArtistPage>? {
        getArtistMemory(key, freshMs, maxAgeMs)?.let { return it }
        val file = fileFor("artist", key)
        val env = readJson(file, ArtistEnvelope::class.java) ?: return null
        val result = evaluate(env.savedAtMs, env.page, freshMs, maxAgeMs)
        if (result == null) {
            runCatching { file.delete() }
            return null
        }
        synchronized(lock) { artistMemory[key] = env }
        return result
    }

    fun putArtist(key: String, page: CachedArtistPage) {
        val env = ArtistEnvelope(savedAtMs = nowMs(), page = page)
        synchronized(lock) { artistMemory[key] = env }
        writeJson(fileFor("artist", key), env)
    }

    /**
     * Marks a cached track list as stale without erasing it, so reopening shows it immediately
     * without a spinner while refreshing quietly in the background.
     */
    fun markTracksStale(key: String) {
        val updated = synchronized(lock) {
            tracksMemory[key]?.let { existing ->
                existing.copy(savedAtMs = STALE_TIMESTAMP).also { tracksMemory[key] = it }
            }
        }
        val file = fileFor("tracks", key)
        if (updated != null) {
            writeJson(file, updated)
        } else if (file.exists()) {
            readJson(file, TracksEnvelope::class.java)?.let { env ->
                writeJson(file, env.copy(savedAtMs = STALE_TIMESTAMP))
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            tracksMemory.clear()
            artistMemory.clear()
        }
        runCatching {
            directory.listFiles()?.forEach { it.delete() }
        }
    }

    private fun <T> evaluate(savedAtMs: Long, value: T, freshMs: Long, maxAgeMs: Long): CachedPage<T>? {
        if (savedAtMs == STALE_TIMESTAMP) return CachedPage(value, needsRefresh = true)
        val age = (nowMs() - savedAtMs).coerceAtLeast(0L)
        if (age > maxAgeMs) return null
        return CachedPage(value, needsRefresh = age > freshMs)
    }

    private fun fileFor(kind: String, key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$kind:$key".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)
        return File(directory, "${kind}_$digest.json")
    }

    private fun <T> readJson(file: File, clazz: Class<T>): T? {
        if (!file.isFile) return null
        return runCatching {
            file.readText(Charsets.UTF_8).let { gson.fromJson(it, clazz) }
        }.getOrNull()
    }

    private fun writeJson(file: File, payload: Any) {
        runCatching {
            if (!directory.exists()) directory.mkdirs()
            val tmp = File(directory, "${file.name}.tmp")
            tmp.writeText(gson.toJson(payload), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            pruneDiskIfNeeded()
        }
    }

    private fun pruneDiskIfNeeded() {
        val files = directory.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return
        if (files.size <= maxDiskEntries) return
        files.sortBy { it.lastModified() }
        val excess = files.size - maxDiskEntries
        for (i in 0 until excess) {
            runCatching { files[i].delete() }
        }
    }

    companion object {
        const val ALBUM_FRESH_MS = 7L * 24 * 60 * 60 * 1000
        const val PLAYLIST_FRESH_MS = 3L * 60 * 1000
        const val MIX_FRESH_MS = 15L * 60 * 1000
        const val ARTIST_FRESH_MS = 15L * 60 * 1000
        const val ALBUM_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        private const val STALE_TIMESTAMP = 1L
        private const val MAX_MEMORY_SETS = 64
        private const val MAX_MEMORY_ARTISTS = 32
        private const val MAX_DISK_ENTRIES = 120

        fun isStaticAlbumSet(playlist: SoundCloudPlaylist): Boolean {
            if (playlist.isAlbum == true) return true
            val type = playlist.setType?.lowercase(Locale.ROOT)
            if (type == "album" || type == "ep" || type == "single" || type == "compilation") return true
            val ref = playlist.permalinkUrl.orEmpty()
            return ref.startsWith(SPOTIFY_ALBUM) ||
                ref.startsWith("yandex:album:") ||
                ref.startsWith("${YT_SET_REF}MPREb") ||
                ref.startsWith("${YT_SET_REF}OLAK5uy_")
        }

        fun setKey(playlist: SoundCloudPlaylist): String =
            playlist.permalinkUrl?.takeIf { it.isNotBlank() } ?: "playlist:${playlist.id}"

        fun freshDurationFor(playlist: SoundCloudPlaylist): Long =
            if (isStaticAlbumSet(playlist)) ALBUM_FRESH_MS else PLAYLIST_FRESH_MS

        fun maxAgeFor(playlist: SoundCloudPlaylist): Long =
            if (isStaticAlbumSet(playlist)) ALBUM_MAX_AGE_MS else MAX_AGE_MS
    }
}
