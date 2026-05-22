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
    val artworkUrl: String? = null
)

class PlaylistsRepository(context: Context) {
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

    private fun load(): List<Playlist> {
        val json = preferences.getString("playlists", null) ?: return emptyList()
        return runCatching { gson.fromJson<List<Playlist>>(json, listType) }
            .getOrDefault(emptyList())
    }

    private fun update(value: List<Playlist>) {
        _playlists.value = value
        preferences.edit().putString("playlists", gson.toJson(value)).apply()
    }
}
