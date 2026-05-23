package com.example.myapplication.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FavoritesRepository(context: Context) {
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
        if (isFavorite(track.id)) return
 
        update(
            _favorites.value + FavoriteTrack(
                id = track.id,
                urn = track.urn ?: "",
                title = track.title ?: "Unknown Track",
                artworkUrl = track.artworkUrl,
                permalinkUrl = track.permalinkUrl,
                artist = track.user?.username ?: "Unknown Artist",
                duration = track.duration,
                streamUrl = streamUrl,
                downloadState = DownloadState.NONE
            )
        )
    }

    fun addFavoriteTrack(favoriteTrack: FavoriteTrack) {
        if (isFavorite(favoriteTrack.id)) return
        update(_favorites.value + favoriteTrack)
    }

    fun remove(trackId: Long) {
        update(_favorites.value.filterNot { it.id == trackId })
    }

    fun updateStreamUrl(trackId: Long, streamUrl: String) {
        update(_favorites.value.map {
            if (it.id == trackId) it.copy(streamUrl = streamUrl) else it
        })
    }

    fun updateDownloadState(trackId: Long, state: DownloadState) {
        update(_favorites.value.map {
            if (it.id == trackId) it.copy(downloadState = state) else it
        })
    }

    fun updateDownloadedFolderArtworkUri(uri: String?) {
        _downloadedFolderArtworkUri.value = uri
        preferences.edit().putString(KEY_DOWNLOADED_FOLDER_ARTWORK_URI, uri).apply()
    }

    private fun load(): List<FavoriteTrack> {
        val json = preferences.getString(KEY_TRACKS, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<FavoriteTrack>>(json, listType) }
            .getOrDefault(emptyList())
    }

    private fun update(value: List<FavoriteTrack>) {
        _favorites.value = value
        preferences.edit().putString(KEY_TRACKS, gson.toJson(value)).apply()
    }

    private companion object {
        const val KEY_TRACKS = "tracks"
        const val KEY_DOWNLOADED_FOLDER_ARTWORK_URI = "downloaded_folder_artwork_uri"
    }
}
