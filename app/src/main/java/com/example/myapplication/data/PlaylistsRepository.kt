package com.example.myapplication.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<FavoriteTrack> = emptyList(),
    val artworkUrl: String? = null,
    // Set when this playlist is a liked album or set: the source's key (see [sourceKey]), so the
    // heart on the album finds this copy again instead of saving a second one. Null for playlists
    // made by hand — and for every entry saved before albums could be liked, since Gson leaves
    // missing fields null whatever the default says.
    val sourceKey: String? = null,
    val artist: String? = null
) {
    val isLikedAlbum: Boolean get() = sourceKey != null

    /** Tracks saved on the device through this playlist (not through "Скачанное"). */
    val downloadedCount: Int get() = tracks.count { it.downloadState == DownloadState.DOWNLOADED }
}

/**
 * Stable key of an album or set, used to recognise it once liked. Yandex albums carry
 * "yandex:album:<id>" as their permalink; SoundCloud sets their web URL.
 */
fun SoundCloudPlaylist.sourceKey(): String =
    permalinkUrl?.takeIf { it.isNotBlank() } ?: "soundcloud:set:$id"

class PlaylistsRepository(context: Context) {
    private val filesDir = context.filesDir
    private val preferences = context.getSharedPreferences("playlists_store", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<Playlist>>() {}.type

    private val _playlists = MutableStateFlow(load())
    val playlists = _playlists.asStateFlow()

    fun createPlaylist(name: String): Playlist {
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name
        )
        update(_playlists.value + newPlaylist)
        return newPlaylist
    }

    fun get(playlistId: String): Playlist? = _playlists.value.firstOrNull { it.id == playlistId }

    fun findBySource(sourceKey: String): Playlist? =
        _playlists.value.firstOrNull { it.sourceKey == sourceKey }

    /**
     * Saves a liked album. It goes to the front of the list, so the library shows it right after
     * "Скачанное", newest first.
     */
    fun createFromSource(
        sourceKey: String,
        name: String,
        artist: String?,
        artworkUrl: String?,
        tracks: List<FavoriteTrack>
    ): Playlist {
        findBySource(sourceKey)?.let { return it }
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name,
            tracks = tracks.distinctBy { it.id },
            artworkUrl = artworkUrl ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
            sourceKey = sourceKey,
            artist = artist
        )
        update(listOf(playlist) + _playlists.value)
        return playlist
    }

    /**
     * Replaces a liked album's track list with a fresher one (the full list arrives after the
     * first few tracks) while keeping what was already downloaded for each track.
     */
    fun mergeTracks(playlistId: String, fresh: List<FavoriteTrack>) {
        update(_playlists.value.map { playlist ->
            if (playlist.id != playlistId) return@map playlist
            val saved = playlist.tracks.associateBy { it.id }
            val merged = fresh.distinctBy { it.id }.map { track ->
                val old = saved[track.id] ?: return@map track
                track.copy(
                    downloadState = old.downloadState,
                    streamUrl = old.streamUrl,
                    localArtworkPath = old.localArtworkPath
                )
            }
            if (merged == playlist.tracks) playlist else playlist.copy(tracks = merged)
        })
    }

    /** Download bookkeeping for one track of one playlist; "Скачанное" is not touched. */
    fun updateTrackDownload(
        playlistId: String,
        trackId: Long,
        state: DownloadState,
        streamUrl: String? = null,
        localArtworkPath: String? = null
    ) {
        update(_playlists.value.map { playlist ->
            if (playlist.id != playlistId) return@map playlist
            playlist.copy(tracks = playlist.tracks.map { track ->
                if (track.id != trackId) {
                    track
                } else {
                    track.copy(
                        downloadState = state,
                        streamUrl = if (state == DownloadState.DOWNLOADED) streamUrl else null,
                        localArtworkPath = localArtworkPath ?: track.localArtworkPath
                    )
                }
            })
        })
    }

    /** A copy of [trackId] saved through any playlist, for playing it offline from anywhere. */
    fun downloadedStreamUrl(trackId: Long): String? =
        _playlists.value.firstNotNullOfOrNull { playlist ->
            playlist.tracks.firstOrNull {
                it.id == trackId && it.downloadState == DownloadState.DOWNLOADED && !it.streamUrl.isNullOrBlank()
            }?.streamUrl
        }

    /** Whether any playlist other than [exceptPlaylistId] still plays from [streamUrl]. */
    fun usesStream(streamUrl: String, exceptPlaylistId: String? = null): Boolean =
        _playlists.value.any { playlist ->
            playlist.id != exceptPlaylistId && playlist.tracks.any { it.streamUrl == streamUrl }
        }

    fun deletePlaylist(playlistId: String) {
        update(_playlists.value.filterNot { it.id == playlistId })
    }

    fun addTrackToPlaylist(playlistId: String, track: FavoriteTrack) {
        update(_playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                if (playlist.tracks.any { it.id == track.id }) {
                    playlist
                } else {
                    playlist.copy(
                        tracks = playlist.tracks + track,
                        artworkUrl = playlist.artworkUrl ?: track.artworkUrl
                    )
                }
            } else {
                playlist
            }
        })
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) {
        update(_playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                val updatedTracks = playlist.tracks.filterNot { it.id == trackId }
                playlist.copy(
                    tracks = updatedTracks,
                    artworkUrl = if (playlist.artworkUrl == playlist.tracks.firstOrNull()?.artworkUrl) {
                        updatedTracks.firstOrNull()?.artworkUrl
                    } else {
                        playlist.artworkUrl
                    }
                )
            } else {
                playlist
            }
        })
    }

    fun updatePlaylistArtwork(playlistId: String, artworkUrl: String?) {
        update(_playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(artworkUrl = artworkUrl)
            } else {
                playlist
            }
        })
    }

    fun updatePlaylistTracks(playlistId: String, tracks: List<FavoriteTrack>) {
        update(_playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(
                    tracks = tracks,
                    artworkUrl = playlist.artworkUrl ?: tracks.firstOrNull()?.artworkUrl
                )
            } else {
                playlist
            }
        })
    }

    private fun load(): List<Playlist> {
        val json = preferences.getString("playlists", null) ?: return emptyList()
        val list = runCatching { gson.fromJson<List<Playlist>>(json, listType) }
            .getOrDefault(emptyList())
        // Same path rebasing as FavoritesRepository: Android may move filesDir between launches,
        // and an album saved on the device must not lose its files over it.
        return list.map { playlist ->
            playlist.copy(tracks = playlist.tracks.orEmpty().map { track ->
                val url = rebase(track.streamUrl, "offline_music", mustExist = false)
                val art = rebase(track.localArtworkPath, "offline_art", mustExist = true)
                // The download queue lives in memory: a track still "downloading" at launch was
                // cut off with the process and would otherwise spin forever.
                val state = if (track.downloadState == DownloadState.DOWNLOADING) DownloadState.NONE else track.downloadState
                if (url != track.streamUrl || art != track.localArtworkPath || state != track.downloadState) {
                    track.copy(streamUrl = url, localArtworkPath = art, downloadState = state)
                } else {
                    track
                }
            })
        }
    }

    private fun rebase(path: String?, folder: String, mustExist: Boolean): String? {
        if (path == null || !path.contains("/files/$folder/")) return path
        val file = java.io.File(java.io.File(filesDir, folder), path.substringAfterLast("/"))
        return if (!mustExist || (file.exists() && file.length() > 0L)) file.absolutePath else null
    }

    private fun update(value: List<Playlist>) {
        _playlists.value = value
        preferences.edit().putString("playlists", gson.toJson(value)).apply()
    }
}
