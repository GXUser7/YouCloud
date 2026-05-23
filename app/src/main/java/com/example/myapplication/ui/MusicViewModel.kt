package com.example.myapplication.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DownloadState
import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.FavoritesRepository
import com.example.myapplication.data.OfflineMusicStore
import com.example.myapplication.data.MixSection
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.SoundCloudApi
import com.example.myapplication.data.SoundCloudMix
import com.example.myapplication.data.SoundCloudMixesRepository
import com.example.myapplication.data.SoundCloudPlaybackResolver
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudMeResponse
import com.example.myapplication.data.SoundCloudUser
import com.example.myapplication.data.Playlist
import com.example.myapplication.data.PlaylistsRepository
import com.example.myapplication.player.MusicPlayer
import com.example.myapplication.player.MusicPlayer.QueueTrack
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException

enum class AppScreen {
    HOME,
    SEARCH,
    DOWNLOADS,
    SETTINGS,
    MIX_DETAIL,
    PLAYLIST_DETAIL
}

class MusicViewModel(
    private val context: Context,
    private val musicPlayer: MusicPlayer,
    private val favoritesRepository: FavoritesRepository,
    private val playlistsRepository: PlaylistsRepository,
    @get:androidx.media3.common.util.UnstableApi
    private val offlineMusicStore: OfflineMusicStore,
    val settingsRepository: SettingsRepository
) : ViewModel() {

    val playlists = playlistsRepository.playlists

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylist = combine(playlistsRepository.playlists, _selectedPlaylistId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isClientIdExpired = MutableStateFlow(false)
    val isClientIdExpired = _isClientIdExpired.asStateFlow()

    private val service = SoundCloudApi.createService(settingsRepository::oauthTokenValue)
    private val playbackResolver = SoundCloudPlaybackResolver(service)
    private val mixesRepository = SoundCloudMixesRepository(service, context)

    private val _tracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val isPlaying = musicPlayer.isPlaying
    val currentTrackId = musicPlayer.currentTrackId
    val currentTrackTitle = musicPlayer.currentTrack
    val playbackPositionMs = musicPlayer.positionMs
    val playbackDurationMs = musicPlayer.durationMs
    val repeatMode = musicPlayer.repeatMode
    val shuffleEnabled = musicPlayer.shuffleEnabled
    val favorites = favoritesRepository.favorites
    val downloadedFolderArtworkUri = favoritesRepository.downloadedFolderArtworkUri
    val clientId = settingsRepository.clientId
    val defaultClientId = settingsRepository.defaultClientId
    val oauthToken = settingsRepository.oauthToken
    val defaultOauthToken = settingsRepository.defaultOauthToken
    val userId = settingsRepository.userId
    val defaultUserId = settingsRepository.defaultUserId
    val homeSelectedTab = settingsRepository.homeSelectedTab

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn = _isLoggingIn.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError = _loginError.asStateFlow()

    val isLoggedOut = combine(
        settingsRepository.clientId,
        settingsRepository.oauthToken,
        settingsRepository.userId
    ) { cId, token, uId ->
        cId.isBlank() || token.isBlank() || uId.isBlank()
    }

    private val _mixSection = MutableStateFlow<MixSection?>(null)
    val mixSection = _mixSection.asStateFlow()

    private val _stationSection = MutableStateFlow<MixSection?>(null)
    val stationSection = _stationSection.asStateFlow()

    private val _mixesLoading = MutableStateFlow(false)
    val mixesLoading = _mixesLoading.asStateFlow()

    private val _loadingMixId = MutableStateFlow<String?>(null)
    val loadingMixId = _loadingMixId.asStateFlow()

    private val _screen = MutableStateFlow(AppScreen.HOME)
    val screen = _screen.asStateFlow()

    private val _selectedTrack = MutableStateFlow<SoundCloudTrack?>(null)
    val selectedTrack = _selectedTrack.asStateFlow()

    private val _selectedMix = MutableStateFlow<SoundCloudMix?>(null)
    val selectedMix = _selectedMix.asStateFlow()

    private val _mixTracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val mixTracks = _mixTracks.asStateFlow()

    private val _currentPlayingTrack = MutableStateFlow<SoundCloudTrack?>(null)
    val currentPlayingTrack = _currentPlayingTrack.asStateFlow()

    private val _likesSyncStatus = MutableStateFlow(LikesSyncStatus())
    val likesSyncStatus = _likesSyncStatus.asStateFlow()
    private var likesSyncJob: Job? = null

    private var searchJob: Job? = null
    private var openMixJob: Job? = null
    private var playMixJob: Job? = null
    private var activeQueue: List<SoundCloudTrack> = emptyList()
    private val resolvedUrls = mutableMapOf<Long, String>()

    init {
        viewModelScope.launch {
            combine(
                musicPlayer.currentTrackId,
                musicPlayer.shuffleEnabled
            ) { trackId, _ -> trackId }
                .collectLatest { trackId ->
                    if (trackId == null) return@collectLatest
                    
                    val trackIndex = activeQueue.indexOfFirst { it.id == trackId }
                    if (trackIndex == -1) return@collectLatest
                    
                    val track = activeQueue[trackIndex]
                    _currentPlayingTrack.value = track
                    if (_selectedTrack.value != null) {
                        _selectedTrack.value = track
                    }

                    // Lazy resolve current track
                    resolveTrackIfNeeded(trackIndex)
                    
                    // Lazy resolve the next track in the queue (respects shuffle!)
                    val nextIndex = musicPlayer.getNextMediaItemIndex()
                    if (nextIndex != -1 && nextIndex < activeQueue.size) {
                        resolveTrackIfNeeded(nextIndex)
                    }
                }
        }

        viewModelScope.launch {
            combine(
                settingsRepository.clientId,
                settingsRepository.oauthToken,
                settingsRepository.userId
            ) { clientId, oauthToken, userId -> Triple(clientId, oauthToken, userId) }
                .collectLatest { (clientId, oauthToken, userId) ->
                    if (clientId.isBlank() || oauthToken.isBlank() || userId.isBlank()) {
                        _mixSection.value = null
                        _stationSection.value = null
                    } else {
                        loadMixes()
                    }
                }
        }
    }

    private var loadMixesJob: Job? = null

    fun loadMixes() {
        if (settingsRepository.oauthTokenValue().isBlank()) {
            _mixSection.value = null
            _stationSection.value = null
            return
        }
        if (settingsRepository.clientId.value.isBlank()) {
            _errorMessage.value = "Укажите SoundCloud client_id в настройках"
            return
        }

        if (_mixesLoading.value) return
        _mixesLoading.value = true
        _errorMessage.value = null

        loadMixesJob?.cancel()
        loadMixesJob = viewModelScope.launch {
            try {
                mixesRepository.fetchHomeSectionsFlow(settingsRepository.clientId.value)
                    .collect { (moods, stations) ->
                        _mixSection.value = moods
                        _stationSection.value = stations
                        _mixesLoading.value = false // Done with initial load
                    }
            } catch (e: Exception) {
                if (_mixSection.value == null && _stationSection.value == null) {
                    _errorMessage.value = readableMessage(e)
                }
                _mixesLoading.value = false
            }
        }
    }

    fun refreshMixesAndStations() {
        mixesRepository.clearCache()
        _mixSection.value = null
        _stationSection.value = null
        loadMixes()
    }

    fun openMix(mix: SoundCloudMix) {
        _selectedMix.value = mix
        _screen.value = AppScreen.MIX_DETAIL
        _mixTracks.value = emptyList()
        if (clientId.value.isBlank()) {
            _errorMessage.value = "Укажите SoundCloud client_id в настройках"
            return
        }
        _isLoading.value = true
        openMixJob?.cancel()
        openMixJob = viewModelScope.launch {
            try {
                val tracks = mixesRepository.loadMixTracks(mix, clientId.value)
                if (_selectedMix.value?.id == mix.id) {
                    _mixTracks.value = tracks.filter { isPlayableMixTrack(it) }
                }
            } catch (e: Exception) {
                if (_selectedMix.value?.id == mix.id) {
                    _errorMessage.value = readableMessage(e)
                }
            } finally {
                if (_selectedMix.value?.id == mix.id) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun closeMix() {
        _selectedMix.value = null
        _mixTracks.value = emptyList()
        _screen.value = AppScreen.HOME
    }

    fun playMix(mix: SoundCloudMix) {
        playMixJob?.cancel()
        playMixJob = viewModelScope.launch {
            Log.d("MusicViewModel", "playMix: mixId=${mix.id}")
            _loadingMixId.value = mix.id
            _errorMessage.value = null

            try {
                val clientId = settingsRepository.clientId.value
                if (clientId.isBlank()) {
                    _errorMessage.value = "Укажите SoundCloud client_id в настройках"
                    return@launch
                }
                val tracks = mixesRepository.loadMixTracks(mix, clientId)
                    .filter(::isPlayableMixTrack)

                if (_loadingMixId.value != mix.id) return@launch

                if (tracks.isEmpty()) {
                    _errorMessage.value = "В этом миксе нет доступных треков."
                    return@launch
                }

                activeQueue = tracks
                resolvedUrls.clear()

                // Pre-resolve the first track in the mix before playing to prevent instant failure / skip loop
                val firstTrack = tracks.firstOrNull()
                if (firstTrack != null) {
                    val resolvedUrl = favoritesRepository.get(firstTrack.id)?.streamUrl
                        ?: playbackResolver.resolve(firstTrack, clientId)
                        ?: ""
                    if (resolvedUrl.isNotEmpty()) {
                        resolvedUrls[firstTrack.id] = resolvedUrl
                    }
                }
                
                // Play immediately with first track resolved and others as stubs
                val stubs = tracks.map { t ->
                    val localUrl = favoritesRepository.get(t.id)?.streamUrl
                    t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: "soundcloud://track/${t.id}")
                }
                musicPlayer.playQueue(stubs, 0)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "playMix error", e)
                _errorMessage.value = readableMessage(e)
            } finally {
                _loadingMixId.value = null
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        _screen.value = AppScreen.SEARCH
        searchJob?.cancel()

        if (query.length < 3) {
            _tracks.value = emptyList()
            _errorMessage.value = null
            _isLoading.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            if (settingsRepository.clientId.value.isBlank()) {
                _errorMessage.value = "Укажите SoundCloud client_id в настройках"
                _tracks.value = emptyList()
                return@launch
            }
            searchTracks(query)
        }
    }

    private suspend fun searchTracks(query: String) {
        _isLoading.value = true
        _errorMessage.value = null

        try {
            val results = service.searchTracks(
                query = query,
                clientId = settingsRepository.clientId.value
            )
            _tracks.value = results.collection.filter { isPlayableMixTrack(it) }
        } catch (e: Exception) {
            _tracks.value = emptyList()
            _errorMessage.value = readableMessage(e)
        } finally {
            _isLoading.value = false
        }
    }

    fun playMixTrack(track: SoundCloudTrack) {
        viewModelScope.launch {
            Log.d("MusicViewModel", "playMixTrack: trackId=${track.id}")
            _errorMessage.value = null

            val tracks = _mixTracks.value
            val playableTracks = tracks.filter { isPlayableMixTrack(it) }

            if (playableTracks.isEmpty()) {
                playTrack(track)
                return@launch
            }

            _isLoading.value = true
            try {
                val startIndex = playableTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

                activeQueue = playableTracks
                resolvedUrls.clear()

                // Pre-resolve the clicked track before starting playback to avoid instant failure / skip loop
                val startTrack = playableTracks.getOrNull(startIndex)
                if (startTrack != null) {
                    val clientIdValue = settingsRepository.clientId.value
                    val resolvedUrl = favoritesRepository.get(startTrack.id)?.streamUrl
                        ?: playbackResolver.resolve(startTrack, clientIdValue)
                        ?: ""
                    if (resolvedUrl.isNotEmpty()) {
                        resolvedUrls[startTrack.id] = resolvedUrl
                    }
                }

                // Play immediately with starting track resolved and others as stubs
                val stubs = playableTracks.map { t ->
                    val localUrl = favoritesRepository.get(t.id)?.streamUrl
                    t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: "soundcloud://track/${t.id}")
                }
                musicPlayer.playQueue(stubs, startIndex)
            } catch (e: Exception) {
                Log.e("MusicViewModel", "playMixTrack error", e)
                _errorMessage.value = readableMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun resolveTrackIfNeeded(index: Int) {
        val track = activeQueue.getOrNull(index) ?: return
        if (resolvedUrls.containsKey(track.id)) return

        Log.d("MusicViewModel", "Lazy resolving track: ${track.title}")
        try {
            val streamUrl = favoritesRepository.get(track.id)?.streamUrl
                ?: playbackResolver.resolve(track, settingsRepository.clientId.value)
                ?: ""
            
            if (streamUrl.isNotEmpty()) {
                resolvedUrls[track.id] = streamUrl
                // Update the player queue with the new URL
                val updatedQueue = activeQueue.map { t ->
                    val localUrl = favoritesRepository.get(t.id)?.streamUrl
                    t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: "soundcloud://track/${t.id}")
                }
                musicPlayer.updateQueue(updatedQueue)
            }
        } catch (e: Exception) {
            handleSoundCloudApiError(e)
            Log.e("MusicViewModel", "Lazy resolution failed for trackId=${track.id}", e)
        }
    }


    fun playTrack(track: SoundCloudTrack) {
        viewModelScope.launch {
            _errorMessage.value = null

            try {
                val clientId = settingsRepository.clientId.value
                if (clientId.isBlank()) {
                    _errorMessage.value = "Укажите SoundCloud client_id в настройках"
                    return@launch
                }
                val streamUrl = favoritesRepository.get(track.id)?.streamUrl
                    ?: playbackResolver.resolve(track, clientId)

                if (streamUrl == null) {
                    _errorMessage.value = "Для этого трека не нашёлся доступный поток."
                    return@launch
                }

                activeQueue = listOf(track)
                musicPlayer.playQueue(
                    tracks = listOf(
                        track.toQueueTrack(streamUrl)
                    ),
                    startIndex = 0
                )
                _currentPlayingTrack.value = track
                _selectedTrack.value = track
            } catch (e: Exception) {
                _errorMessage.value = readableMessage(e)
            }
        }
    }

    fun openTrack(track: SoundCloudTrack) {
        _selectedTrack.value = track
    }

    fun closeTrack() {
        _selectedTrack.value = null
    }

    fun openHome() {
        _screen.value = AppScreen.HOME
    }

    fun openSearch() {
        _screen.value = AppScreen.SEARCH
    }

    fun closeSearch() {
        _screen.value = AppScreen.HOME
    }

    fun openDownloads() {
        _screen.value = AppScreen.DOWNLOADS
    }

    fun closeDownloads() {
        _screen.value = AppScreen.HOME
    }

    fun openSettings() {
        _screen.value = AppScreen.SETTINGS
    }

    fun closeSettings() {
        _screen.value = AppScreen.HOME
    }

    fun saveClientId(value: String) {
        settingsRepository.saveClientId(value)
    }

    fun resetClientId() {
        settingsRepository.resetClientId()
    }

    fun saveOauthToken(value: String) {
        settingsRepository.saveOauthToken(value)
    }

    fun resetOauthToken() {
        settingsRepository.resetOauthToken()
    }

    fun saveUserId(value: String) {
        settingsRepository.saveUserId(value)
    }

    fun resetUserId() {
        settingsRepository.resetUserId()
    }

    fun setHomeSelectedTab(tab: Int) {
        settingsRepository.setHomeSelectedTab(tab)
    }

    fun updateDownloadedFolderArtworkUri(uri: String?) {
        favoritesRepository.updateDownloadedFolderArtworkUri(uri)
    }

    fun toggleFavorite(track: SoundCloudTrack) {
        viewModelScope.launch {
            if (favoritesRepository.isFavorite(track.id)) {
                favoritesRepository.get(track.id)?.let { favorite ->
                    if (favorite.downloadState == DownloadState.DOWNLOADED && favorite.streamUrl != null) {
                        withContext(Dispatchers.IO) {
                            offlineMusicStore.removeHls(favorite.streamUrl)
                        }
                    }
                }
                favoritesRepository.remove(track.id)
                val userIdValue = settingsRepository.userIdValue()
                if (userIdValue.isNotBlank() && settingsRepository.oauthTokenValue().isNotBlank()) {
                    try {
                        service.unlikeTrack(userIdValue, track.id, settingsRepository.clientId.value)
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Failed to unlike track on SoundCloud", e)
                    }
                }
                return@launch
            }

            favoritesRepository.add(track, streamUrl = null)
            favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADING)

            val userIdValue = settingsRepository.userIdValue()
            if (userIdValue.isNotBlank() && settingsRepository.oauthTokenValue().isNotBlank()) {
                try {
                    service.likeTrack(userIdValue, track.id, settingsRepository.clientId.value)
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to like track on SoundCloud", e)
                }
            }

            try {
                val clientId = settingsRepository.clientId.value
                val streamUrl = if (clientId.isNotBlank()) {
                    playbackResolver.resolve(track, clientId)
                } else {
                    null
                }
                if (streamUrl == null) {
                    favoritesRepository.updateDownloadState(track.id, DownloadState.FAILED)
                    if (clientId.isBlank()) {
                        _errorMessage.value = "Укажите SoundCloud client_id в настройках для загрузки трека"
                    } else {
                        _errorMessage.value = "Трек добавлен в любимое, но поток для загрузки не нашёлся."
                    }
                    return@launch
                }

                favoritesRepository.updateStreamUrl(track.id, streamUrl)
                withContext(Dispatchers.IO) {
                    offlineMusicStore.downloadHls(streamUrl)
                }
                favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADED)
            } catch (e: Exception) {
                favoritesRepository.updateDownloadState(track.id, DownloadState.FAILED)
                _errorMessage.value = readableMessage(e)
            }
        }
    }

    fun playFavorite(track: FavoriteTrack) {
        val playable = track.toSoundCloudTrack()
        val streamUrl = track.streamUrl
        if (streamUrl == null) {
            _errorMessage.value = "У этого любимого трека пока нет сохранённого потока."
            return
        }

        val downloadedQueue = favoritesRepository.favorites.value
            .filter { it.downloadState == DownloadState.DOWNLOADED && it.streamUrl != null }
        if (downloadedQueue.isEmpty()) return

        activeQueue = downloadedQueue.map { it.toSoundCloudTrack() }
        downloadedQueue.forEach { fav ->
            fav.streamUrl?.let { url ->
                resolvedUrls[fav.id] = url
            }
        }
        musicPlayer.playQueue(
            tracks = downloadedQueue.mapNotNull { favorite ->
                favorite.streamUrl?.let { favorite.toQueueTrack(it) }
            },
            startIndex = downloadedQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        )
        _currentPlayingTrack.value = playable
        _selectedTrack.value = playable
    }

    fun togglePlayPause() {
        musicPlayer.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        musicPlayer.seekTo(positionMs)
    }

    fun skipNext() {
        musicPlayer.skipNext()
    }

    fun skipPrevious() {
        musicPlayer.skipPrevious()
    }

    fun cycleRepeatMode() {
        musicPlayer.cycleRepeatMode()
    }

    fun toggleShuffle() {
        musicPlayer.toggleShuffle()
    }

    fun deleteDownloadedTrack(track: FavoriteTrack) {
        val streamUrl = track.streamUrl ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    offlineMusicStore.removeHls(streamUrl)
                }
                favoritesRepository.updateDownloadState(track.id, DownloadState.NONE)
            } catch (e: Exception) {
                _errorMessage.value = "Не удалось удалить локальную копию трека."
            }
        }
    }

    fun onCredentialsCaptured(capturedClientId: String, capturedOauthToken: String) {
        if (_isLoggingIn.value) return
        _isLoggingIn.value = true
        _loginError.value = null

        viewModelScope.launch {
            try {
                // Fetch user ID using the captured credentials
                val tempService = SoundCloudApi.createService { capturedOauthToken }
                val meResponse = tempService.getMe(capturedClientId)
                val userIdString = meResponse.id.toString()

                // Save credentials to settings
                settingsRepository.saveClientId(capturedClientId)
                settingsRepository.saveOauthToken(capturedOauthToken)
                settingsRepository.saveUserId(userIdString)

                _isLoggingIn.value = false
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to login with captured credentials", e)
                _loginError.value = "Ошибка при получении профиля SoundCloud. Попробуйте еще раз."
                _isLoggingIn.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            settingsRepository.resetClientId()
            settingsRepository.resetOauthToken()
            settingsRepository.resetUserId()
            _tracks.value = emptyList()
            _mixSection.value = null
            _stationSection.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
    }

    private fun isPlayableMixTrack(track: SoundCloudTrack): Boolean =
        track.kind == "track" &&
            track.streamable == true &&
            track.policy != "BLOCK" &&
            track.policy != "SNIP" &&
            track.policy != "SNIPPET" &&
            track.policy != "PREVIEW" &&
            track.media?.transcodings?.isNotEmpty() == true

    private fun readableMessage(error: Exception): String {
        handleSoundCloudApiError(error)
        return when (error) {
            is HttpException -> when (error.code()) {
                401 -> "SoundCloud отклонил запрос. Возможно, client_id устарел."
                403 -> "SoundCloud запретил доступ к этому ресурсу."
                404 -> "SoundCloud не нашёл нужный поток."
                429 -> "Слишком много запросов к SoundCloud. Попробуй чуть позже."
                else -> "Ошибка SoundCloud: HTTP ${error.code()}."
            }

            is IOException -> "Нет соединения с сетью."
            else -> "Не удалось выполнить запрос к SoundCloud."
        }
    }

    private fun SoundCloudTrack.toQueueTrack(streamUrl: String): QueueTrack =
        QueueTrack(
            id = id,
            url = streamUrl,
            title = title ?: "Unknown Track",
            artist = user?.username ?: "Unknown Artist",
            artworkUrl = artworkUrl
        )

    private fun FavoriteTrack.toQueueTrack(streamUrl: String): QueueTrack =
        QueueTrack(
            id = id,
            url = streamUrl,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl
        )

    private fun SoundCloudTrack.toFavoriteTrack(streamUrl: String? = null, downloadState: DownloadState = DownloadState.NONE) = FavoriteTrack(
        id = id,
        urn = urn ?: "",
        title = title ?: "Unknown Track",
        artworkUrl = artworkUrl,
        permalinkUrl = permalinkUrl,
        artist = user?.username ?: "Unknown Artist",
        duration = duration,
        streamUrl = streamUrl,
        downloadState = downloadState
    )

    private fun FavoriteTrack.toSoundCloudTrack() = SoundCloudTrack(
        id = id,
        urn = urn,
        title = title,
        artworkUrl = artworkUrl,
        permalinkUrl = permalinkUrl,
        user = SoundCloudUser(username = artist),
        duration = duration,
        kind = "track",
        streamable = true,
        policy = null,
        trackAuthorization = null,
        media = null
    )

    // Playlist and Local Import Support
    fun createPlaylist(name: String) {
        playlistsRepository.createPlaylist(name)
    }

    fun deletePlaylist(playlistId: String) {
        playlistsRepository.deletePlaylist(playlistId)
        if (_selectedPlaylistId.value == playlistId) {
            closePlaylist()
        }
    }

    fun addTrackToPlaylist(playlistId: String, track: SoundCloudTrack) {
        playlistsRepository.addTrackToPlaylist(playlistId, track.toFavoriteTrack())
    }

    fun addFavoriteTrackToPlaylist(playlistId: String, track: FavoriteTrack) {
        playlistsRepository.addTrackToPlaylist(playlistId, track)
    }

    fun removeTrackFromPlaylist(playlistId: String, trackId: Long) {
        playlistsRepository.removeTrackFromPlaylist(playlistId, trackId)
    }

    fun openPlaylist(playlist: Playlist) {
        _selectedPlaylistId.value = playlist.id
        _screen.value = AppScreen.PLAYLIST_DETAIL
    }

    fun closePlaylist() {
        _selectedPlaylistId.value = null
        _screen.value = AppScreen.HOME
    }

    fun updatePlaylistArtwork(playlistId: String, uriString: String?) {
        viewModelScope.launch {
            if (uriString == null) {
                playlistsRepository.updatePlaylistArtwork(playlistId, null)
                return@launch
            }
            val uri = android.net.Uri.parse(uriString)
            val localPath = copyUriToInternalStorage(context, uri, "playlist_artworks")
            if (localPath != null) {
                playlistsRepository.updatePlaylistArtwork(playlistId, localPath)
            }
        }
    }

    private fun copyUriToInternalStorage(context: Context, uri: android.net.Uri, folderName: String): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "img_${System.currentTimeMillis()}.jpg"
            val folder = java.io.File(context.filesDir, folderName)
            if (!folder.exists()) folder.mkdirs()
            val destFile = java.io.File(folder, fileName)
            destFile.outputStream().use { outputStream ->
                inputStream.use { it.copyTo(outputStream) }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error copying image to internal storage", e)
            null
        }
    }

    private var refreshingJob: Job? = null

    fun tryAutoRefreshClientId() {
        if (refreshingJob?.isActive == true) return
        refreshingJob = viewModelScope.launch {
            _isLoading.value = true
            val newClientId = SoundCloudApi.fetchSoundCloudClientId()
            if (newClientId != null) {
                settingsRepository.saveClientId(newClientId)
                _isClientIdExpired.value = false
                _errorMessage.value = null
                refreshMixesAndStations()
            } else {
                _isClientIdExpired.value = true
                _errorMessage.value = "SoundCloud client_id устарел. Не удалось обновить его автоматически. Пожалуйста, укажите рабочий ID в настройках."
            }
            _isLoading.value = false
        }
    }

    private fun handleSoundCloudApiError(e: Throwable) {
        if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
            _isClientIdExpired.value = true
            tryAutoRefreshClientId()
        }
    }

    fun playPlaylistTrack(playlist: Playlist, track: FavoriteTrack) {
        viewModelScope.launch {
            _errorMessage.value = null
            val playable = track.toSoundCloudTrack()
            val tracks = playlist.tracks
            if (tracks.isEmpty()) return@launch

            activeQueue = tracks.map { it.toSoundCloudTrack() }
            
            tracks.forEach { fav ->
                fav.streamUrl?.let { url ->
                    resolvedUrls[fav.id] = url
                }
            }
            
            val startIndex = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val startTrack = tracks[startIndex]
            if (startTrack.streamUrl == null) {
                val clientIdValue = settingsRepository.clientId.value
                val resolvedUrl = playbackResolver.resolve(startTrack.toSoundCloudTrack(), clientIdValue) ?: ""
                if (resolvedUrl.isNotEmpty()) {
                    resolvedUrls[startTrack.id] = resolvedUrl
                }
            }

            val stubs = tracks.map { fav ->
                val localUrl = favoritesRepository.get(fav.id)?.streamUrl
                val url = localUrl ?: fav.streamUrl ?: resolvedUrls[fav.id] ?: "soundcloud://track/${fav.id}"
                fav.toQueueTrack(url)
            }

            musicPlayer.playQueue(stubs, startIndex)
            _currentPlayingTrack.value = playable
            _selectedTrack.value = playable
        }
    }

    fun importLocalTracks(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val imported = importLocalAudio(context, uris)
                for (track in imported) {
                    favoritesRepository.addFavoriteTrack(track)
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error importing local tracks", e)
                _errorMessage.value = "Не удалось импортировать треки"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startLikesSync() {
        likesSyncJob?.cancel()
        likesSyncJob = viewModelScope.launch {
            _likesSyncStatus.value = LikesSyncStatus(state = SyncState.FETCHING_LIKES)
            val clientId = settingsRepository.clientId.value
            val oauthToken = settingsRepository.oauthToken.value
            val userId = settingsRepository.userId.value

            if (clientId.isBlank() || oauthToken.isBlank() || userId.isBlank()) {
                _likesSyncStatus.value = LikesSyncStatus(
                    state = SyncState.FAILED,
                    errorMessage = "Не все данные авторизации указаны в настройках"
                )
                return@launch
            }

            val allTracks = mutableListOf<SoundCloudTrack>()
            var nextOffset: String? = null
            var hasMore = true

            try {
                while (hasMore) {
                    val response = service.getLikedTracks(
                        userId = userId,
                        clientId = clientId,
                        limit = 50,
                        offset = nextOffset
                    )
                    val items = response.collection.mapNotNull { it.track }
                    allTracks.addAll(items)
                    
                    if (response.nextHref != null) {
                        nextOffset = android.net.Uri.parse(response.nextHref).getQueryParameter("offset")
                        if (nextOffset == null) {
                            hasMore = false
                        }
                    } else {
                        hasMore = false
                    }
                    
                    delay(500)
                }

                if (allTracks.isEmpty()) {
                    _likesSyncStatus.value = LikesSyncStatus(state = SyncState.COMPLETED)
                    return@launch
                }

                _likesSyncStatus.value = LikesSyncStatus(
                    state = SyncState.DOWNLOADING,
                    totalTracks = allTracks.size,
                    currentTrackIndex = 0
                )

                var downloadedCount = 0
                var failedCount = 0

                allTracks.forEachIndexed { index, track ->
                    ensureActive()

                    _likesSyncStatus.value = _likesSyncStatus.value.copy(
                        currentTrackIndex = index + 1,
                        currentTrackTitle = track.title ?: "Unknown Track"
                    )

                    val existing = favoritesRepository.get(track.id)
                    val isAlreadyDownloaded = existing != null && existing.downloadState == DownloadState.DOWNLOADED && !existing.streamUrl.isNullOrBlank()

                    if (isAlreadyDownloaded) {
                        downloadedCount++
                        _likesSyncStatus.value = _likesSyncStatus.value.copy(
                            downloadedCount = downloadedCount
                        )
                    } else {
                        try {
                            if (existing == null) {
                                favoritesRepository.add(track, streamUrl = null)
                            }
                            favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADING)

                            var resolvedStreamUrl: String? = null
                            try {
                                resolvedStreamUrl = playbackResolver.resolve(track, clientId)
                            } catch (e: Exception) {
                                if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
                                    val newClientId = SoundCloudApi.fetchSoundCloudClientId()
                                    if (newClientId != null) {
                                        settingsRepository.saveClientId(newClientId)
                                        resolvedStreamUrl = playbackResolver.resolve(track, newClientId)
                                    }
                                }
                                if (resolvedStreamUrl == null) throw e
                            }

                            if (resolvedStreamUrl == null) {
                                throw Exception("Could not resolve stream URL")
                            }

                            favoritesRepository.updateStreamUrl(track.id, resolvedStreamUrl)
                            withContext(Dispatchers.IO) {
                                offlineMusicStore.downloadHls(resolvedStreamUrl)
                            }
                            favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADED)
                            downloadedCount++
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Failed to sync/download track ${track.id}", e)
                            favoritesRepository.updateDownloadState(track.id, DownloadState.FAILED)
                            failedCount++
                        }

                        _likesSyncStatus.value = _likesSyncStatus.value.copy(
                            downloadedCount = downloadedCount,
                            failedCount = failedCount
                        )
                        
                        delay(1000)
                    }
                }

                _likesSyncStatus.value = _likesSyncStatus.value.copy(
                    state = SyncState.COMPLETED
                )

            } catch (e: CancellationException) {
                _likesSyncStatus.value = LikesSyncStatus(state = SyncState.IDLE)
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Likes sync failed", e)
                _likesSyncStatus.value = LikesSyncStatus(
                    state = SyncState.FAILED,
                    errorMessage = readableMessage(e)
                )
            }
        }
    }

    fun stopLikesSync() {
        likesSyncJob?.cancel()
        likesSyncJob = null
        _likesSyncStatus.value = LikesSyncStatus(state = SyncState.IDLE)
    }

    fun resetLikesSyncStatus() {
        likesSyncJob?.cancel()
        likesSyncJob = null
        _likesSyncStatus.value = LikesSyncStatus(state = SyncState.IDLE)
    }

    fun moveDownloadedTracksToDownloads(playlist: Playlist) {
        viewModelScope.launch {
            val clientId = settingsRepository.clientId.value
            val userIdValue = settingsRepository.userIdValue()
            val token = settingsRepository.oauthTokenValue()

            playlist.tracks.forEach { track ->
                if (track.downloadState == DownloadState.DOWNLOADED && !track.streamUrl.isNullOrBlank()) {
                    val existing = favoritesRepository.get(track.id)
                    if (existing == null) {
                        favoritesRepository.add(track.toSoundCloudTrack(), streamUrl = track.streamUrl)
                        favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADED)
                    } else if (existing.downloadState != DownloadState.DOWNLOADED) {
                        favoritesRepository.updateStreamUrl(track.id, track.streamUrl)
                        favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADED)
                    }

                    if (userIdValue.isNotBlank() && token.isNotBlank() && clientId.isNotBlank()) {
                        try {
                            service.likeTrack(userIdValue, track.id, clientId)
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Failed to like track ${track.id} on SoundCloud", e)
                        }
                    }
                }
            }
        }
    }
}
