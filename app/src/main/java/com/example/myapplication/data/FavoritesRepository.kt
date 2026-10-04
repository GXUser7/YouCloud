package com.example.myapplication.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FavoritesRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("favorite_tracks", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<FavoriteTrack>>() {}.type

    private val _favorites = MutableStateFlow(load())
    val favorites = _favorites.asStateFlow()

    private val _downloadedFolderArtworkUri = MutableStateFlow(
        preferences.getString(KEY_DOWNLOADED_FOLDER_ARTWORK_URI, null)
    )
    val downloadedFolderArtworkUri = _downloadedFolderArtworkUri.asStateFlow()

    fun isFavorite(trackId: Long): Boolean = _favorites.value.any { it.id == trackId }

    fun get(trackId: Long): FavoriteTrack? = _favorites.value.firstOrNull { it.id == trackId }

    fun add(track: SoundCloudTrack, streamUrl: String?) {
        val credits = track.artists?.takeIf { it.isNotEmpty() }
        if (isFavorite(track.id)) {
            // Already saved, but possibly from before multi-artist support or from a listing that
            // only carried the uploader. Backfill the credits rather than leaving the stored entry
            // permanently single-artist.
            if (credits != null) {
                _favorites.update { current ->
                    current.map { if (it.id == track.id && it.artists.isNullOrEmpty()) it.copy(artists = credits) else it }
                }
                persist()
            }
            return
        }
        val newTrack = FavoriteTrack(
            id = track.id,
            urn = track.urn ?: "",
            title = track.title ?: "Unknown Track",
            artworkUrl = track.artworkUrl,
            permalinkUrl = track.permalinkUrl,
            artist = track.user?.username ?: "Unknown Artist",
            duration = track.duration,
            streamUrl = streamUrl,
            downloadState = DownloadState.NONE,
            artistPermalinkUrl = track.user?.permalinkUrl,
            artistId = track.user?.id,
            artists = credits
        )
        // Newest first: "Скачанное" lists them, and plays them, in this order.
        _favorites.update { current -> listOf(newTrack) + current }
        persist()
    }

    fun addFavoriteTrack(favoriteTrack: FavoriteTrack) {
        if (isFavorite(favoriteTrack.id)) return
        _favorites.update { current -> listOf(favoriteTrack) + current }
        persist()
    }

    /**
     * Puts [ids] on top, in that order: tracks saved together (a likes sync), as the service
     * lists them, newest first. Saved one by one, each went on top, the last saved — the oldest
     * like — highest.
     */
    fun moveToTop(ids: List<Long>) {
        if (ids.isEmpty()) return
        val order = ids.withIndex().associate { it.value to it.index }
        _favorites.update { current ->
            val (moved, rest) = current.partition { it.id in order }
            moved.sortedBy { order.getValue(it.id) } + rest
        }
        persist()
    }

    /**
     * Fills in the credited-artist list for saved tracks that predate [FavoriteTrack.artists],
     * using whatever fresh copies of those tracks the caller happens to have. Downloads saved
     * before multi-artist support hold only the first name and there is nothing on disk to
     * recover the rest from, so they get repaired opportunistically as listings load them again.
     */
    fun syncCredits(tracks: List<SoundCloudTrack>) {
        val creditsById = tracks.mapNotNull { track ->
            track.artists?.takeIf { it.size > 1 }?.let { track.id to it }
        }.toMap()
        if (creditsById.isEmpty()) return
        var changed = false
        _favorites.update { current ->
            current.map { saved ->
                val credits = creditsById[saved.id]
                if (credits != null && saved.artists.isNullOrEmpty()) {
                    changed = true
                    saved.copy(artists = credits)
                } else {
                    saved
                }
            }
        }
        if (changed) persist()
    }

    fun remove(trackId: Long) {
        _favorites.update { current -> current.filterNot { it.id == trackId } }
        persist()
    }

    fun updateStreamUrl(trackId: Long, streamUrl: String) {
        _favorites.update { current ->
            current.map { if (it.id == trackId) it.copy(streamUrl = streamUrl) else it }
        }
        persist()
    }

    fun updateDownloadState(trackId: Long, state: DownloadState) {
        _favorites.update { current ->
            current.map { if (it.id == trackId) it.copy(downloadState = state) else it }
        }
        persist()
    }

    fun updateLocalArtwork(trackId: Long, path: String?) {
        _favorites.update { current ->
            current.map { if (it.id == trackId) it.copy(localArtworkPath = path) else it }
        }
        persist()
    }

    /** Downloaded tracks that still have no cached cover — see [OfflineMusicStore.downloadArtwork]. */
    fun downloadedWithoutArtwork(): List<FavoriteTrack> = _favorites.value.filter {
        it.downloadState == DownloadState.DOWNLOADED &&
            it.localArtworkPath == null &&
            !it.artworkUrl.isNullOrBlank()
    }

    fun updateDownloadedFolderArtworkUri(uri: String?) {
        _downloadedFolderArtworkUri.value = uri
        preferences.edit().putString(KEY_DOWNLOADED_FOLDER_ARTWORK_URI, uri).apply()
    }

    private fun resolveLocalPath(path: String?): String? {
        if (path == null) return null
        if (path.contains("/files/offline_music/")) {
            val filename = path.substringAfterLast("/")
            return java.io.File(java.io.File(context.filesDir, "offline_music"), filename).absolutePath
        }
        return path
    }

    /**
     * Same rebasing as [resolveLocalPath], for covers. Also drops the path when the file is gone,
     * so a cover deleted behind our back falls back to the remote URL instead of showing nothing.
     */
    private fun resolveArtworkPath(path: String?): String? {
        if (path == null) return null
        val filename = path.substringAfterLast("/")
        val rebased = java.io.File(java.io.File(context.filesDir, "offline_art"), filename)
        return if (rebased.exists() && rebased.length() > 0L) rebased.absolutePath else null
    }

    private fun load(): List<FavoriteTrack> {
        val json = preferences.getString(KEY_TRACKS, null)
        val saved = json?.let { runCatching { gson.fromJson<List<FavoriteTrack>>(it, listType) }.getOrNull() }.orEmpty()
        // Saved oldest first until the newest went on top: turned over once. Marked even with
        // nothing saved, or the first tracks saved newest first would be turned over next time.
        val list = if (preferences.getBoolean(KEY_NEWEST_FIRST, false)) {
            saved
        } else {
            saved.asReversed().also { turned ->
                preferences.edit()
                    .apply { if (json != null) putString(KEY_TRACKS, gson.toJson(turned)) }
                    .putBoolean(KEY_NEWEST_FIRST, true)
                    .apply()
            }
        }
        return list.map { track ->
            val resolvedUrl = resolveLocalPath(track.streamUrl)
            val resolvedArt = resolveArtworkPath(track.localArtworkPath)
            if (resolvedUrl != track.streamUrl || resolvedArt != track.localArtworkPath) {
                track.copy(streamUrl = resolvedUrl, localArtworkPath = resolvedArt)
            } else {
                track
            }
        }
    }

    private fun persist() {
        preferences.edit().putString(KEY_TRACKS, gson.toJson(_favorites.value)).apply()
    }

    private companion object {
        const val KEY_TRACKS = "tracks"
        const val KEY_NEWEST_FIRST = "newest_first"
        const val KEY_DOWNLOADED_FOLDER_ARTWORK_URI = "downloaded_folder_artwork_uri"
    }
}
