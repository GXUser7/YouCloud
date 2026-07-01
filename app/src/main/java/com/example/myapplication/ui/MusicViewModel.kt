package com.example.myapplication.ui

import android.content.Context
import android.media.MediaMetadataRetriever
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
import com.example.myapplication.data.SoundCloudPlaylist
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.myapplication.data.YandexMusicApi
import com.example.myapplication.data.YandexMusicService
import java.util.concurrent.ConcurrentHashMap

enum class AppScreen {
    HOME,
    SEARCH,
    DOWNLOADS,
    SETTINGS,
    MIX_DETAIL,
    PLAYLIST_DETAIL,
    ARTIST_DETAIL,
    YANDEX_PLAYLIST_DETAIL
}

class MusicViewModel(
    context: Context,
    private val musicPlayer: MusicPlayer,
    private val favoritesRepository: FavoritesRepository,
    private val playlistsRepository: PlaylistsRepository,
    @get:androidx.media3.common.util.UnstableApi
    private val offlineMusicStore: OfflineMusicStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    // Use applicationContext to avoid Activity memory leak (#16)
    private val context: Context = context.applicationContext

    // Expose settingsRepository read-only for SettingsScreen (#43)
    val settingsRepo: SettingsRepository get() = settingsRepository
    val showDebugPercentage = settingsRepository.showDebugPercentage
    val yandexToken = settingsRepository.yandexToken

    val playlists = playlistsRepository.playlists

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylist = combine(playlistsRepository.playlists, _selectedPlaylistId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isClientIdExpired = MutableStateFlow(false)
    val isClientIdExpired = _isClientIdExpired.asStateFlow()

    private val service = SoundCloudApi.createService(settingsRepository::oauthTokenValue)
    private val yandexService = YandexMusicApi.createService(settingsRepository::yandexTokenValue)
    private val playbackResolver = SoundCloudPlaybackResolver(service)
    private val mixesRepository = SoundCloudMixesRepository(service, context)

    private val _tracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private val _yandexSearchQuery = MutableStateFlow("")
    val yandexSearchQuery = _yandexSearchQuery.asStateFlow()

    private val _yandexTracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val yandexTracks = _yandexTracks.asStateFlow()

    private val _yandexLoading = MutableStateFlow(false)
    val yandexLoading = _yandexLoading.asStateFlow()

    private val _yandexError = MutableStateFlow<String?>(null)
    val yandexError = _yandexError.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    fun updateDownloadProgress(trackId: Long, progress: Float) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (trackId to progress)
        }
    }

    // true = search in Yandex, false = search in SoundCloud
    private val _searchInYandex = MutableStateFlow(false)
    val searchInYandex = _searchInYandex.asStateFlow()

    fun setSearchSource(useYandex: Boolean) {
        _searchInYandex.value = useYandex
        // Re-run search in the new source if there's already a query
        val q = _searchQuery.value
        if (q.length >= 3) {
            if (useYandex) {
                _tracks.value = emptyList()
                onYandexSearchQueryChange(q)
            } else {
                _yandexTracks.value = emptyList()
                onSearchQueryChange(q)
            }
        }
    }

    private val _silentLoginUrl = MutableStateFlow<String?>(null)
    val silentLoginUrl = _silentLoginUrl.asStateFlow()
    private var lastSilentLoginTime = 0L

    private val _yandexLoginUrl = MutableStateFlow<String?>(null)
    val yandexLoginUrl = _yandexLoginUrl.asStateFlow()

    private val _currentArtistPlaylists = MutableStateFlow<List<SoundCloudPlaylist>>(emptyList())
    val currentArtistPlaylists = _currentArtistPlaylists.asStateFlow()

    private val _selectedArtistPlaylist = MutableStateFlow<SoundCloudPlaylist?>(null)
    val selectedArtistPlaylist = _selectedArtistPlaylist.asStateFlow()

    private val _currentArtist = MutableStateFlow<SoundCloudUser?>(null)
    val currentArtist = _currentArtist.asStateFlow()

    private val _currentArtistTracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val currentArtistTracks = _currentArtistTracks.asStateFlow()

    private val _isAllArtistTracksLoaded = MutableStateFlow(false)
    val isAllArtistTracksLoaded = _isAllArtistTracksLoaded.asStateFlow()

    private val _downloadedPercentages = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val downloadedPercentages = _downloadedPercentages.asStateFlow()

    private val _artistLoading = MutableStateFlow(false)
    val artistLoading = _artistLoading.asStateFlow()

    private val _artistError = MutableStateFlow<String?>(null)
    val artistError = _artistError.asStateFlow()

    private val _yandexPlaylists = MutableStateFlow<List<SoundCloudPlaylist>>(emptyList())
    val yandexPlaylists = _yandexPlaylists.asStateFlow()

    private val _yandexPlaylistsLoading = MutableStateFlow(false)
    val yandexPlaylistsLoading = _yandexPlaylistsLoading.asStateFlow()

    private val _selectedYandexPlaylist = MutableStateFlow<SoundCloudPlaylist?>(null)
    val selectedYandexPlaylist = _selectedYandexPlaylist.asStateFlow()

    private val _yandexPlaylistLoading = MutableStateFlow(false)
    val yandexPlaylistLoading = _yandexPlaylistLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // TODO #19: Split into _searchLoading, _mixLoading etc. to avoid one operation's
    // loading state interfering with another. Requires updating MusicScreen consumers.
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    val isPlaying = musicPlayer.isPlaying
    val isPlaybackBuffering = musicPlayer.isBuffering
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

    private val _playingMixId = MutableStateFlow<String?>(null)
    val playingMixId = _playingMixId.asStateFlow()

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

    private val _soundcloudLikesSyncStatus = MutableStateFlow(LikesSyncStatus())
    val soundcloudLikesSyncStatus = _soundcloudLikesSyncStatus.asStateFlow()

    private val _yandexLikesSyncStatus = MutableStateFlow(LikesSyncStatus())
    val yandexLikesSyncStatus = _yandexLikesSyncStatus.asStateFlow()
    private var likesSyncJob: Job? = null

    private var searchJob: Job? = null
    private var openMixJob: Job? = null
    private var playMixJob: Job? = null
    private val _activeQueue = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val activeQueue = _activeQueue.asStateFlow()

    fun reorderActiveQueue(fromIndex: Int, toIndex: Int) {
        val list = _activeQueue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val element = list.removeAt(fromIndex)
            list.add(toIndex, element)
            _activeQueue.value = list
            musicPlayer.moveMediaItem(fromIndex, toIndex)
            if (!musicPlayer.shuffleEnabled.value) {
                originalQueue = list
            }
        }
    }
    private val resolvedUrls = ConcurrentHashMap<Long, String>()
    private val originalQueueFlow = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    private var originalQueue: List<SoundCloudTrack>
        get() = originalQueueFlow.value
        set(value) { originalQueueFlow.value = value }
    private val queueMutex = Mutex()

    private class DownloadRequest(
        val track: SoundCloudTrack,
        val isRedownload: Boolean,
        val onComplete: (Boolean) -> Unit = {}
    )

    private val downloadQueue = kotlinx.coroutines.channels.Channel<DownloadRequest>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        viewModelScope.launch {
            combine(
                musicPlayer.currentTrackId,
                musicPlayer.shuffleEnabled
            ) { trackId, _ -> trackId }
                .collectLatest { trackId ->
                    if (trackId == null) return@collectLatest
                    
                    val trackIndex = _activeQueue.value.indexOfFirst { it.id == trackId }
                    if (trackIndex == -1) return@collectLatest
                    
                    val track = _activeQueue.value[trackIndex]
                    _currentPlayingTrack.value = track
                    if (_selectedTrack.value != null) {
                        _selectedTrack.value = track
                    }

                    // Lazy resolve current track
                    resolveTrackIfNeeded(trackIndex)
                    
                    // Lazy resolve the next track in the queue (respects shuffle!)
                    val nextIndex = musicPlayer.getNextMediaItemIndex()
                    if (nextIndex != -1 && nextIndex < _activeQueue.value.size) {
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
        viewModelScope.launch {
            settingsRepository.yandexToken.collectLatest { token ->
                if (token.isBlank()) {
                    _yandexPlaylists.value = emptyList()
                    _selectedYandexPlaylist.value = null
                } else {
                    loadYandexPlaylists()
                }
            }
        }
        viewModelScope.launch {
            favorites.collectLatest { list ->
                val newMap = mutableMapOf<Long, Int>()
                withContext(Dispatchers.IO) {
                    list.forEach { fav ->
                        if (fav.downloadState == DownloadState.DOWNLOADED) {
                            if (fav.urn.startsWith("yandex:track:")) {
                                val path = fav.streamUrl
                                if (path != null && java.io.File(path).exists()) {
                                    val actual = getMp3Duration(path)
                                    val expected = fav.duration
                                    if (expected > 0) {
                                        val pct = ((actual.toFloat() / expected.toFloat()) * 100).toInt().coerceIn(0, 100)
                                        newMap[fav.id] = pct
                                    } else {
                                        newMap[fav.id] = 100
                                    }
                                } else {
                                    newMap[fav.id] = 0
                                }
                            } else if (fav.urn.startsWith("local:track:")) {
                                val path = fav.streamUrl
                                if (path != null && java.io.File(path).exists()) {
                                    val actual = getMp3Duration(path)
                                    val expected = fav.duration
                                    if (expected > 0) {
                                        newMap[fav.id] = ((actual.toFloat() / expected.toFloat()) * 100).toInt().coerceIn(0, 100)
                                    } else {
                                        newMap[fav.id] = 100
                                    }
                                } else {
                                    newMap[fav.id] = 0
                                }
                            } else {
                                newMap[fav.id] = 100
                            }
                        }
                    }
                }
                _downloadedPercentages.value = newMap
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            for (request in downloadQueue) {
                val success = performDownload(request.track, request.isRedownload)
                request.onComplete(success)
            }
        }
    }

    private suspend fun getYandexUid(): Long? {
        val storedUid = settingsRepository.yandexUid.value
        if (storedUid != 0L) return storedUid
        
        return try {
            val response = yandexService.getAccountStatus()
            val uid = response.result?.account?.uid
            if (uid != null) {
                settingsRepository.saveYandexUid(uid)
            }
            uid
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Failed to fetch Yandex UID", e)
            null
        }
    }

    fun loadYandexPlaylists() {
        val token = settingsRepository.yandexTokenValue()
        if (token.isBlank()) {
            _yandexPlaylists.value = emptyList()
            return
        }
        viewModelScope.launch {
            _yandexPlaylistsLoading.value = true
            try {
                val uid = getYandexUid()
                if (uid != null) {
                    val response = yandexService.getUserPlaylists(uid)
                    val playlistList = response.result.orEmpty().map { 
                        applyCustomYandexPlaylistArtwork(it.toSoundCloudPlaylist())
                    }.toMutableList()

                    var likedCount = 0
                    try {
                        val likedTracksResponse = yandexService.getLikedTracks(uid)
                        likedCount = likedTracksResponse.result?.library?.tracks?.size ?: 0
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Failed to fetch liked tracks size", e)
                    }

                    val likedPlaylist = SoundCloudPlaylist(
                        id = -100L,
                        title = "Мне нравится",
                        trackCount = likedCount,
                        artworkUrl = null,
                        permalinkUrl = "yandex:playlist:liked"
                    )

                    playlistList.add(0, applyCustomYandexPlaylistArtwork(likedPlaylist))
                    _yandexPlaylists.value = playlistList
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load Yandex playlists", e)
            } finally {
                _yandexPlaylistsLoading.value = false
            }
        }
    }

    fun selectYandexPlaylist(playlist: SoundCloudPlaylist) {
        val playlistWithArt = applyCustomYandexPlaylistArtwork(playlist)
        viewModelScope.launch {
            _yandexPlaylistLoading.value = true
            _selectedYandexPlaylist.value = playlistWithArt
            _screen.value = AppScreen.YANDEX_PLAYLIST_DETAIL
            try {
                val token = settingsRepository.yandexTokenValue()
                val uid = getYandexUid()
                if (uid != null && token.isNotBlank()) {
                    if (playlistWithArt.id == -100L) {
                        // Liked Tracks special playlist
                        val response = yandexService.getLikedTracks(uid)
                        val trackRefs = response.result?.library?.tracks.orEmpty()
                        val allTracks = mutableListOf<SoundCloudTrack>()
                        
                        // Chunk by 50 to avoid big payloads and query limits
                        trackRefs.chunked(50).forEach { chunk ->
                            val trackIdsStr = chunk.joinToString(",") { if (it.albumId.isNullOrBlank()) it.id else "${it.id}:${it.albumId}" }
                            try {
                                val tracksDetailsResponse = yandexService.getTracksDetails(trackIdsStr)
                                allTracks.addAll(tracksDetailsResponse.result.orEmpty().map { it.toSoundCloudTrack() })
                            } catch (e: Exception) {
                                Log.e("MusicViewModel", "Failed to get details for chunk of liked tracks", e)
                            }
                        }
                        
                        // Preserve original order of liked tracks from Yandex
                        val orderMap = trackRefs.withIndex().associate { it.value.id to it.index }
                        val sortedTracks = allTracks.sortedBy { track ->
                            val yandexId = track.urn?.substringAfter("yandex:track:") ?: ""
                            orderMap[yandexId] ?: Int.MAX_VALUE
                        }
                        
                        _selectedYandexPlaylist.value = playlistWithArt.copy(
                            tracks = sortedTracks,
                            trackCount = sortedTracks.size
                        )
                    } else {
                        // Regular playlist
                        val yandexPlaylistId = playlist.id
                        val response = yandexService.getPlaylistDetail(uid, yandexPlaylistId)
                        val tracks = response.result?.tracks.orEmpty().mapNotNull { it.track?.toSoundCloudTrack() }
                        _selectedYandexPlaylist.value = playlistWithArt.copy(tracks = tracks)
                    }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to fetch Yandex playlist tracks", e)
            } finally {
                _yandexPlaylistLoading.value = false
            }
        }
    }

    fun deselectYandexPlaylist() {
        _selectedYandexPlaylist.value = null
        _screen.value = AppScreen.HOME
    }

    fun getCustomYandexPlaylistArtwork(playlistId: Long): String? {
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        return prefs.getString("yandex_playlist_art_$playlistId", null)
    }

    private fun applyCustomYandexPlaylistArtwork(playlist: SoundCloudPlaylist): SoundCloudPlaylist {
        val path = getCustomYandexPlaylistArtwork(playlist.id)
        return if (path != null) playlist.copy(artworkUrl = path) else playlist
    }

    fun updateYandexPlaylistArtwork(playlistId: Long, uriString: String?) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            if (uriString == null) {
                prefs.edit().remove("yandex_playlist_art_$playlistId").apply()
                refreshYandexPlaylistsArtwork(playlistId, null)
                return@launch
            }
            val uri = android.net.Uri.parse(uriString)
            val localPath = copyUriToInternalStorage(context, uri, "yandex_playlist_artworks")
            if (localPath != null) {
                prefs.edit().putString("yandex_playlist_art_$playlistId", localPath).apply()
                refreshYandexPlaylistsArtwork(playlistId, localPath)
            }
        }
    }

    private fun refreshYandexPlaylistsArtwork(playlistId: Long, path: String?) {
        _selectedYandexPlaylist.value?.let { current ->
            if (current.id == playlistId) {
                _selectedYandexPlaylist.value = current.copy(artworkUrl = path)
            }
        }
        _yandexPlaylists.value = _yandexPlaylists.value.map { playlist ->
            if (playlist.id == playlistId) {
                playlist.copy(artworkUrl = path)
            } else {
                playlist
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
                    handleSoundCloudApiError(e)
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
                    _mixTracks.value = tracks.filter { isPlayableTrack(it) }
                }
            } catch (e: Exception) {
                if (_selectedMix.value?.id == mix.id) {
                    handleSoundCloudApiError(e)
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
                    .filter(::isPlayableTrack)

                if (_loadingMixId.value != mix.id) return@launch

                if (tracks.isEmpty()) {
                    _errorMessage.value = "В этом миксе нет доступных треков."
                    return@launch
                }

                val firstTrack = tracks.firstOrNull()
                originalQueue = tracks
                val queueToPlay = if (shuffleEnabled.value && firstTrack != null) {
                    val list = tracks.toMutableList()
                    list.removeAt(0)
                    listOf(firstTrack) + list.shuffled(java.util.Random())
                } else {
                    tracks
                }
                _activeQueue.value = queueToPlay
                resolvedUrls.clear()

                // Pre-resolve the first track in the mix before playing to prevent instant failure / skip loop
                val resolveTarget = queueToPlay.firstOrNull()
                if (resolveTarget != null) {
                    val resolvedUrl = favoritesRepository.get(resolveTarget.id)?.streamUrl
                        ?: playbackResolver.resolve(resolveTarget, clientId)
                        ?: ""
                    if (resolvedUrl.isNotEmpty()) {
                        resolvedUrls[resolveTarget.id] = resolvedUrl
                    }
                }
                
                // Play immediately with first track resolved and others as stubs
                val stubs = queueToPlay.map { t ->
                    val localUrl = favoritesRepository.get(t.id)?.streamUrl
                    t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: "soundcloud://track/${t.id}")
                }
                musicPlayer.playQueue(stubs, 0)
                _playingMixId.value = mix.id
            } catch (e: Exception) {
                Log.e("MusicViewModel", "playMix error", e)
                handleSoundCloudApiError(e)
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
            _tracks.value = results.collection.filter { isPlayableTrack(it) }
        } catch (e: Exception) {
            _tracks.value = emptyList()
            handleSoundCloudApiError(e)
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
            val playableTracks = tracks.filter { isPlayableTrack(it) }

            if (playableTracks.isEmpty()) {
                playTrack(track)
                return@launch
            }

            _isLoading.value = true
            try {
                val startIndex = playableTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                originalQueue = playableTracks
                val clickedTrack = playableTracks.getOrNull(startIndex)
                val queueToPlay = if (shuffleEnabled.value && clickedTrack != null) {
                    val list = playableTracks.toMutableList()
                    list.removeAt(startIndex)
                    listOf(clickedTrack) + list.shuffled(java.util.Random())
                } else {
                    playableTracks
                }
                val newStartIndex = if (shuffleEnabled.value) 0 else startIndex
                _activeQueue.value = queueToPlay
                resolvedUrls.clear()

                // Pre-resolve the clicked track before starting playback to avoid instant failure / skip loop
                val startTrack = queueToPlay.getOrNull(newStartIndex)
                if (startTrack != null) {
                    val isYandex = startTrack.urn?.startsWith("yandex:track:") == true
                    if (isYandex) {
                        val yandexId = startTrack.urn?.removePrefix("yandex:track:")
                        resolvedUrls[startTrack.id] = "yandex://track/$yandexId"
                    } else {
                        val clientIdValue = settingsRepository.clientId.value
                        val resolvedUrl = favoritesRepository.get(startTrack.id)?.streamUrl
                            ?: playbackResolver.resolve(startTrack, clientIdValue)
                            ?: ""
                        if (resolvedUrl.isNotEmpty()) {
                            resolvedUrls[startTrack.id] = resolvedUrl
                        }
                    }
                }

                // Play immediately with starting track resolved and others as stubs
                val stubs = queueToPlay.map { t ->
                    val localUrl = favoritesRepository.get(t.id)?.streamUrl
                    val isYandex = t.urn?.startsWith("yandex:track:") == true
                    val fallbackUrl = if (isYandex) {
                        val yandexId = t.urn?.removePrefix("yandex:track:")
                        "yandex://track/$yandexId"
                    } else {
                        "soundcloud://track/${t.id}"
                    }
                    t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: fallbackUrl)
                }
                musicPlayer.playQueue(stubs, newStartIndex)
                _playingMixId.value = _selectedMix.value?.id
            } catch (e: Exception) {
                Log.e("MusicViewModel", "playMixTrack error", e)
                handleSoundCloudApiError(e)
                _errorMessage.value = readableMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun resolveTrackIfNeeded(index: Int) {
        val track = _activeQueue.value.getOrNull(index) ?: return
        if (resolvedUrls.containsKey(track.id)) return

        Log.d("MusicViewModel", "Lazy resolving track: ${track.title}")
        try {
            // Handle Yandex tracks separately (#5)
            val isYandex = track.urn?.startsWith("yandex:track:") == true
            val streamUrl = if (isYandex) {
                val yandexId = track.urn?.removePrefix("yandex:track:") ?: ""
                favoritesRepository.get(track.id)?.streamUrl
                    ?: "yandex://track/$yandexId"
            } else {
                favoritesRepository.get(track.id)?.streamUrl
                    ?: playbackResolver.resolve(track, settingsRepository.clientId.value)
                    ?: ""
            }
            
            if (streamUrl.isNotEmpty()) {
                resolvedUrls[track.id] = streamUrl
                // Update the player queue with the new URL
                val updatedQueue = _activeQueue.value.map { t ->
                    val localUrl = favoritesRepository.get(t.id)?.streamUrl
                    val isYandexT = t.urn?.startsWith("yandex:track:") == true
                    val fallbackUrl = if (isYandexT) {
                        val yId = t.urn?.removePrefix("yandex:track:") ?: ""
                        "yandex://track/$yId"
                    } else {
                        "soundcloud://track/${t.id}"
                    }
                    t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: fallbackUrl)
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
                val isYandex = track.urn?.startsWith("yandex:track:") == true
                val clientId = settingsRepository.clientId.value
                if (clientId.isBlank() && !isYandex) {
                    _errorMessage.value = "Укажите SoundCloud client_id в настройках"
                    return@launch
                }
                val streamUrl = if (isYandex) {
                    val yandexId = track.urn?.removePrefix("yandex:track:")
                    "yandex://track/$yandexId"
                } else {
                    favoritesRepository.get(track.id)?.streamUrl
                        ?: playbackResolver.resolve(track, clientId)
                }

                if (streamUrl == null) {
                    _errorMessage.value = "Для этого трека не нашёлся доступный поток."
                    return@launch
                }

                _activeQueue.value = listOf(track)
                musicPlayer.playQueue(
                    tracks = listOf(
                        track.toQueueTrack(streamUrl)
                    ),
                    startIndex = 0
                )
                _playingMixId.value = null
                _currentPlayingTrack.value = track
                _selectedTrack.value = track
            } catch (e: Exception) {
                handleSoundCloudApiError(e)
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

    fun openArtistDetails(userId: Long, permalinkUrl: String?, username: String?, trackUrn: String? = null) {
        val activeUrn = trackUrn ?: _selectedTrack.value?.urn ?: _currentPlayingTrack.value?.urn
        viewModelScope.launch {
            _selectedTrack.value = null // Close the player overlay
            _selectedMix.value = null   // Close the mix screen if open (prevents mix list showing behind)
            _screen.value = AppScreen.ARTIST_DETAIL
            _artistLoading.value = true
            _artistError.value = null
            _currentArtistTracks.value = emptyList()
            _currentArtistPlaylists.value = emptyList()
            _selectedArtistPlaylist.value = null
            _isAllArtistTracksLoaded.value = false
            
            var isYandex = permalinkUrl?.startsWith("yandex:artist:") == true || 
                           permalinkUrl?.contains("yandex:artist:") == true ||
                           activeUrn?.startsWith("yandex:track:") == true
            var yandexArtistId = if (permalinkUrl?.startsWith("yandex:artist:") == true) {
                permalinkUrl.substringAfter("yandex:artist:")
            } else null

            if (isYandex && yandexArtistId == null) {
                val trackId = activeUrn?.substringAfter("yandex:track:")
                if (trackId != null) {
                    try {
                        val detailsResponse = yandexService.getTracksDetails(trackId)
                        val artist = detailsResponse.result.orEmpty().firstOrNull()?.artists?.firstOrNull()
                        if (artist != null) {
                            yandexArtistId = artist.id
                        }
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Failed to resolve Yandex track artist info", e)
                    }
                }
            }

            // Robust fallback: if artist URN resolving failed but we have a valid non-zero userId (Yandex artist ID)
            if (isYandex && (yandexArtistId == null || yandexArtistId.isBlank())) {
                if (userId != 0L) {
                    yandexArtistId = userId.toString()
                }
            }

            if (isYandex) {
                val artistId = yandexArtistId ?: ""
                try {
                    val response = yandexService.getArtistBriefInfo(artistId)
                    val artist = response.result?.artist
                    if (artist != null) {
                        _currentArtist.value = SoundCloudUser(
                            id = artist.id?.toLongOrNull() ?: 0L,
                            username = artist.name ?: "Unknown Artist",
                            avatarUrl = artist.cover?.getCoverUrl("200x200"),
                            description = artist.description?.text,
                            followersCount = artist.stats?.likes ?: 0,
                            trackCount = response.result?.tracks.orEmpty().size,
                            permalinkUrl = artist.id?.let { "yandex:artist:$it" }
                        )
                        _currentArtistTracks.value = response.result?.tracks.orEmpty().map { it.toSoundCloudTrack() }
                        _currentArtistPlaylists.value = response.result?.albums.orEmpty().map { it.toSoundCloudPlaylist() }
                    } else {
                        _currentArtist.value = SoundCloudUser(username = username ?: "Яндекс Артист")
                        _artistError.value = "Информация об артисте недоступна"
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to fetch Yandex artist info", e)
                    _currentArtist.value = SoundCloudUser(username = username ?: "Яндекс Артист")
                    _artistError.value = "Ошибка: ${readableMessage(e, isYandex = true)}"
                } finally {
                    _artistLoading.value = false
                }
            } else {
                // SoundCloud artist
                val clientIdVal = settingsRepository.clientId.value
                if (clientIdVal.isBlank()) {
                    _currentArtist.value = SoundCloudUser(id = userId, username = username)
                    _artistError.value = "Укажите SoundCloud client_id в настройках"
                    _artistLoading.value = false
                    return@launch
                }
                
                var resolvedUserId = userId
                var resolvedUser = SoundCloudUser(id = userId, username = username, permalinkUrl = permalinkUrl)
                
                try {
                    // 1. Resolve user: use permalink URL if available, else construct from numeric userId
                    val urlToResolve = when {
                        !permalinkUrl.isNullOrBlank() -> permalinkUrl
                        resolvedUserId != 0L -> "https://soundcloud.com/users/$resolvedUserId"
                        else -> null
                    }
                    if (resolvedUserId == 0L && urlToResolve != null) {
                        Log.d("MusicViewModel", "Resolving SoundCloud artist: $urlToResolve")
                        try {
                            val resolved = service.resolveUrl(urlToResolve, clientIdVal)
                            resolvedUserId = resolved.id ?: 0L
                            resolvedUser = resolved
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Failed to resolve artist url", e)
                        }
                    }

                    // 2. Load full user details
                    if (resolvedUserId != 0L) {
                        try {
                            resolvedUser = service.getUser(resolvedUserId, clientIdVal)
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Failed to get user, fallback to initial resolvedUser", e)
                        }
                    }
                    _currentArtist.value = resolvedUser

                    // 3. Load user's stream feed (includes tracks and albums/playlists)
                    if (resolvedUserId != 0L) {
                        val streamResponse = service.getStreamUserTracks(resolvedUserId, clientIdVal)
                        val tracksList = mutableListOf<SoundCloudTrack>()
                        val playlistsList = mutableListOf<SoundCloudPlaylist>()
                        
                        streamResponse.collection.forEach { item ->
                            if (item.type == "track" || item.type == "track-repost") {
                                item.track?.let { tracksList.add(it) }
                            } else if (item.type == "playlist" || item.type == "playlist-repost") {
                                item.playlist?.let { playlistsList.add(it) }
                            }
                        }
                        
                        _currentArtistTracks.value = tracksList
                        _currentArtistPlaylists.value = playlistsList
                    } else {
                        _artistError.value = "Не удалось определить ID артиста"
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to fetch SoundCloud artist stream", e)
                    handleSoundCloudApiError(e)
                    _artistError.value = "Не удалось загрузить данные: ${readableMessage(e)}"
                } finally {
                    _artistLoading.value = false
                }
            }
        }
    }

    fun closeArtist() {
        _currentArtist.value = null
        _currentArtistTracks.value = emptyList()
        _currentArtistPlaylists.value = emptyList()
        _selectedArtistPlaylist.value = null
        _screen.value = AppScreen.HOME
    }

    fun selectArtistPlaylist(playlist: SoundCloudPlaylist) {
        _selectedArtistPlaylist.value = playlist
        val isYandexAlbum = playlist.permalinkUrl?.startsWith("yandex:album:") == true
        if (isYandexAlbum) {
            viewModelScope.launch {
                _artistLoading.value = true
                try {
                    val albumId = playlist.permalinkUrl!!.substringAfter("yandex:album:").toLongOrNull()
                    if (albumId != null) {
                        val response = yandexService.getAlbumWithTracks(albumId)
                        val tracks = response.result?.volumes?.flatten()?.map { it.toSoundCloudTrack(albumId.toString()) } ?: emptyList()
                        _selectedArtistPlaylist.value = playlist.copy(tracks = tracks)
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to fetch Yandex album tracks", e)
                } finally {
                    _artistLoading.value = false
                }
            }
        }
    }

    fun deselectArtistPlaylist() {
        _selectedArtistPlaylist.value = null
    }

    fun startYandexLogin() {
        _yandexLoginUrl.value = "https://oauth.yandex.ru/authorize?response_type=token&client_id=23cabbbdc6cd418abb4b39c32c41195d"
    }

    fun onYandexTokenCaptured(token: String) {
        Log.d("MusicViewModel", "Captured Yandex token: ${token.take(4)}***")
        settingsRepository.saveYandexToken(token)
        _yandexLoginUrl.value = null
        onYandexSearchQueryChange(_yandexSearchQuery.value)
    }

    fun cancelYandexLogin() {
        _yandexLoginUrl.value = null
    }

    fun logoutYandex() {
        settingsRepository.resetYandexToken()
        _yandexTracks.value = emptyList()
        _yandexPlaylists.value = emptyList()
        _selectedYandexPlaylist.value = null
    }

    fun triggerSilentRelogin() {
        val now = System.currentTimeMillis()
        if (now - lastSilentLoginTime < 60000L) return
        lastSilentLoginTime = now
        Log.d("MusicViewModel", "Triggering silent relogin in background WebView...")
        _silentLoginUrl.value = "https://soundcloud.com/discover"
    }

    fun onSilentCredentialsCaptured(clientId: String, oauthToken: String) {
        Log.d("MusicViewModel", "Captured silent credentials: clientId=${clientId.take(4)}***, oauthToken=${oauthToken.take(4)}***")
        viewModelScope.launch {
            try {
                val tempService = SoundCloudApi.createService { oauthToken }
                val meResponse = tempService.getMe(clientId)
                val userIdString = meResponse.id.toString()

                settingsRepository.saveClientId(clientId)
                settingsRepository.saveOauthToken(oauthToken)
                settingsRepository.saveUserId(userIdString)

                _silentLoginUrl.value = null
                Log.d("MusicViewModel", "Silent relogin successful!")
                refreshMixesAndStations()
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to login silently with captured credentials", e)
            }
        }
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

    private suspend fun unlikeOnApiAndLocal(track: SoundCloudTrack) {
        favoritesRepository.remove(track.id)
        
        val isYandex = track.urn?.startsWith("yandex:track:") == true
        if (isYandex) {
            val token = settingsRepository.yandexTokenValue()
            val uid = getYandexUid()
            if (token.isNotBlank() && uid != null) {
                val yandexTrackId = track.urn!!.substringAfter("yandex:track:")
                try {
                    yandexService.unlikeTrack(uid, yandexTrackId)
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to auto-unlike track on Yandex on download failure", e)
                }
            }
        } else {
            val userIdValue = settingsRepository.userIdValue()
            if (userIdValue.isNotBlank() && settingsRepository.oauthTokenValue().isNotBlank()) {
                try {
                    service.unlikeTrack(userIdValue, track.id, settingsRepository.clientId.value)
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to auto-unlike track on SoundCloud on download failure", e)
                }
            }
        }
    }

    fun toggleFavorite(track: SoundCloudTrack) {
        viewModelScope.launch {
            if (favoritesRepository.isFavorite(track.id)) {
                favoritesRepository.get(track.id)?.let { favorite ->
                    if (favorite.downloadState == DownloadState.DOWNLOADED && favorite.streamUrl != null) {
                        withContext(Dispatchers.IO) {
                            if (favorite.urn.startsWith("yandex:track:")) {
                                offlineMusicStore.removeProgressive(favorite.streamUrl)
                            } else {
                                offlineMusicStore.removeHls(favorite.streamUrl)
                            }
                        }
                    }
                }
                favoritesRepository.remove(track.id)
                val userIdValue = settingsRepository.userIdValue()
                if (track.urn?.startsWith("yandex:track:") == true) {
                    val token = settingsRepository.yandexTokenValue()
                    val uid = getYandexUid()
                    if (token.isNotBlank() && uid != null) {
                        val yandexTrackId = track.urn.substringAfter("yandex:track:")
                        try {
                            yandexService.unlikeTrack(uid, yandexTrackId)
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Failed to unlike track on Yandex", e)
                        }
                    }
                } else if (userIdValue.isNotBlank() && settingsRepository.oauthTokenValue().isNotBlank()) {
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

            val isYandex = track.urn?.startsWith("yandex:track:") == true
            if (isYandex) {
                val token = settingsRepository.yandexTokenValue()
                val uid = getYandexUid()
                if (token.isNotBlank() && uid != null) {
                    val yandexTrackId = track.urn!!.substringAfter("yandex:track:")
                    try {
                        yandexService.likeTrack(uid, yandexTrackId)
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Failed to like track on Yandex", e)
                    }
                }
            } else {
                val soundCloudUserId = settingsRepository.userIdValue()
                if (soundCloudUserId.isNotBlank() && settingsRepository.oauthTokenValue().isNotBlank()) {
                    try {
                        service.likeTrack(soundCloudUserId, track.id, settingsRepository.clientId.value)
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Failed to like track on SoundCloud", e)
                    }
                }
            }

            downloadQueue.trySend(DownloadRequest(track, isRedownload = false))
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

        val mappedTracks = downloadedQueue.map { it.toSoundCloudTrack() }
        originalQueue = mappedTracks
        val startIndex = downloadedQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val clickedTrack = mappedTracks.getOrNull(startIndex)
        
        val queueToPlay = if (shuffleEnabled.value && clickedTrack != null) {
            val list = mappedTracks.toMutableList()
            list.removeAt(startIndex)
            listOf(clickedTrack) + list.shuffled(java.util.Random())
        } else {
            mappedTracks
        }
        val newStartIndex = if (shuffleEnabled.value) 0 else startIndex
        
        _activeQueue.value = queueToPlay
        downloadedQueue.forEach { fav ->
            fav.streamUrl?.let { url ->
                resolvedUrls[fav.id] = url
            }
        }
        val stubs = queueToPlay.mapNotNull { t ->
            val localUrl = favoritesRepository.get(t.id)?.streamUrl ?: resolvedUrls[t.id]
            localUrl?.let { t.toQueueTrack(it) }
        }
        // Recalculate index in the filtered list (#2: avoid IndexOutOfBoundsException)
        val safeStartIndex = stubs.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        musicPlayer.playQueue(
            tracks = stubs,
            startIndex = safeStartIndex
        )
        _playingMixId.value = null
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
        viewModelScope.launch {
            queueMutex.withLock {
                musicPlayer.toggleShuffle()
                val enabled = musicPlayer.shuffleEnabled.value
                val currentTrack = _currentPlayingTrack.value
                
                if (currentTrack != null && originalQueue.isNotEmpty()) {
                    val currentPos = playbackPositionMs.value
                    val list = originalQueue.toMutableList()
                    val currentIdx = list.indexOfFirst { it.id == currentTrack.id }
                    
                    val newQueue = if (enabled) {
                        if (currentIdx != -1) {
                            list.removeAt(currentIdx)
                        }
                        val shuffled = list.shuffled(java.util.Random())
                        if (currentIdx != -1) {
                            listOf(currentTrack) + shuffled
                        } else {
                            shuffled
                        }
                    } else {
                        originalQueue
                    }
                    
                    _activeQueue.value = newQueue
                    val newIndex = newQueue.indexOfFirst { it.id == currentTrack.id }.coerceAtLeast(0)
                    
                    val stubs = newQueue.map { t ->
                        val localUrl = favoritesRepository.get(t.id)?.streamUrl
                        val isYandex = t.urn?.startsWith("yandex:track:") == true
                        val fallbackUrl = if (isYandex) {
                            val yandexId = t.urn?.removePrefix("yandex:track:") ?: ""
                            "yandex://track/$yandexId"
                        } else {
                            "soundcloud://track/${t.id}"
                        }
                        t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: fallbackUrl)
                    }
                    musicPlayer.playQueue(stubs, newIndex)
                    musicPlayer.seekTo(currentPos)
                }
            }
        }
    }

    fun deleteDownloadedTrack(track: FavoriteTrack) {
        val streamUrl = track.streamUrl ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Use correct removal method based on track source (#4)
                    if (track.urn.startsWith("yandex:")) {
                        offlineMusicStore.removeProgressive(streamUrl)
                    } else {
                        offlineMusicStore.removeHls(streamUrl)
                    }
                }
                favoritesRepository.updateDownloadState(track.id, DownloadState.NONE)
                favoritesRepository.updateStreamUrl(track.id, "")
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

    // Unified playability check (#41: merged isPlayableMixTrack)
    private fun isPlayableTrack(track: SoundCloudTrack): Boolean {
        if (track.urn?.startsWith("yandex:track:") == true) {
            return track.streamable == true
        }
        return track.kind == "track" &&
            track.streamable == true &&
            track.policy != "BLOCK" &&
            track.policy != "SNIP" &&
            track.policy != "SNIPPET" &&
            track.policy != "PREVIEW" &&
            track.media?.transcodings?.isNotEmpty() == true
    }
    // Pure formatter with no side effects (#26)
    private fun readableMessage(error: Exception, isYandex: Boolean = false): String {
        return when (error) {
            is HttpException -> when (error.code()) {
                401 -> if (isYandex) "Яндекс отклонил запрос. Возможно, токен устарел." else "SoundCloud отклонил запрос. Возможно, client_id устарел."
                403 -> if (isYandex) "Яндекс запретил доступ к этому ресурсу." else "SoundCloud запретил доступ к этому ресурсу."
                404 -> if (isYandex) "Яндекс не нашёл нужного ресурса." else "SoundCloud не нашёл нужный поток."
                429 -> if (isYandex) "Слишком много запросов к Яндексу. Попробуй чуть позже." else "Слишком много запросов к SoundCloud. Попробуй чуть позже."
                else -> if (isYandex) "Ошибка Яндекс Музыки: HTTP ${error.code()}." else "Ошибка SoundCloud: HTTP ${error.code()}."
            }

            is IOException -> "Нет соединения с сетью."
            else -> if (isYandex) "Не удалось выполнить запрос к Яндекс Музыке." else "Не удалось выполнить запрос к SoundCloud."
        }
    }

    private fun SoundCloudTrack.toQueueTrack(streamUrl: String): QueueTrack {
        val finalUrl = if (streamUrl.startsWith("/") && !streamUrl.startsWith("file://")) {
            "file://$streamUrl"
        } else {
            streamUrl
        }
        return QueueTrack(
            id = id,
            url = finalUrl,
            title = title ?: "Unknown Track",
            artist = user?.username ?: "Unknown Artist",
            artworkUrl = artworkUrl
        )
    }

    private fun FavoriteTrack.toQueueTrack(streamUrl: String): QueueTrack {
        val finalUrl = if (streamUrl.startsWith("/") && !streamUrl.startsWith("file://")) {
            "file://$streamUrl"
        } else {
            streamUrl
        }
        return QueueTrack(
            id = id,
            url = finalUrl,
            title = title,
            artist = artist,
            artworkUrl = artworkUrl
        )
    }

    private fun SoundCloudTrack.toFavoriteTrack(streamUrl: String? = null, downloadState: DownloadState = DownloadState.NONE) = FavoriteTrack(
        id = id,
        urn = urn ?: "",
        title = title ?: "Unknown Track",
        artworkUrl = artworkUrl,
        permalinkUrl = permalinkUrl,
        artist = user?.username ?: "Unknown Artist",
        duration = duration,
        streamUrl = streamUrl,
        downloadState = downloadState,
        artistPermalinkUrl = user?.permalinkUrl,
        artistId = user?.id
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
            triggerSilentRelogin()
        }
    }

    fun playPlaylistTrack(playlist: Playlist, track: FavoriteTrack) {
        viewModelScope.launch {
            _errorMessage.value = null
            val playable = track.toSoundCloudTrack()
            val tracks = playlist.tracks
            if (tracks.isEmpty()) return@launch

            val mappedTracks = tracks.map { it.toSoundCloudTrack() }
            originalQueue = mappedTracks
            val startIndex = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            val clickedTrack = mappedTracks.getOrNull(startIndex)
            
            val queueToPlay = if (shuffleEnabled.value && clickedTrack != null) {
                val list = mappedTracks.toMutableList()
                list.removeAt(startIndex)
                listOf(clickedTrack) + list.shuffled(java.util.Random())
            } else {
                mappedTracks
            }
            val newStartIndex = if (shuffleEnabled.value) 0 else startIndex
            
            _activeQueue.value = queueToPlay
            
            tracks.forEach { fav ->
                fav.streamUrl?.let { url ->
                    resolvedUrls[fav.id] = url
                }
            }
            
            val startTrack = queueToPlay.getOrNull(newStartIndex)
            if (startTrack != null) {
                val hasLocal = favoritesRepository.get(startTrack.id)?.streamUrl != null
                if (!hasLocal && resolvedUrls[startTrack.id] == null) {
                    val clientIdValue = settingsRepository.clientId.value
                    val resolvedUrl = playbackResolver.resolve(startTrack, clientIdValue) ?: ""
                    if (resolvedUrl.isNotEmpty()) {
                        resolvedUrls[startTrack.id] = resolvedUrl
                    }
                }
            }

            val stubs = queueToPlay.map { t ->
                val localUrl = favoritesRepository.get(t.id)?.streamUrl
                val isYandex = t.urn?.startsWith("yandex:track:") == true
                val fallbackUrl = if (isYandex) {
                    val yandexId = t.urn?.removePrefix("yandex:track:") ?: ""
                    "yandex://track/$yandexId"
                } else {
                    "soundcloud://track/${t.id}"
                }
                t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: fallbackUrl)
            }

            musicPlayer.playQueue(stubs, newStartIndex)
            _playingMixId.value = null
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

    private fun startLikesSync(source: LikesSyncSource) {
        likesSyncJob?.cancel()
        likesSyncJob = viewModelScope.launch {
            val statusFlow = if (source == LikesSyncSource.SOUNDCLOUD) _soundcloudLikesSyncStatus else _yandexLikesSyncStatus
            statusFlow.value = LikesSyncStatus(state = SyncState.FETCHING_LIKES)
            
            val clientId = settingsRepository.clientId.value
            val oauthToken = settingsRepository.oauthToken.value
            val userId = settingsRepository.userId.value
            val yandexToken = settingsRepository.yandexTokenValue()

            val hasSoundCloud = clientId.isNotBlank() && oauthToken.isNotBlank() && userId.isNotBlank()
            val hasYandex = yandexToken.isNotBlank()

            if (source == LikesSyncSource.SOUNDCLOUD && !hasSoundCloud) {
                statusFlow.value = LikesSyncStatus(
                    state = SyncState.FAILED,
                    errorMessage = "Не все данные авторизации SoundCloud указаны в настройках"
                )
                return@launch
            }
            if (source == LikesSyncSource.YANDEX && !hasYandex) {
                statusFlow.value = LikesSyncStatus(
                    state = SyncState.FAILED,
                    errorMessage = "Укажите рабочий токен Яндекс Музыки в настройках"
                )
                return@launch
            }

            val allTracks = mutableListOf<SoundCloudTrack>()
            var yandexTrackRefs: List<com.example.myapplication.data.YandexLikedTrackRef> = emptyList()

            try {
                if (source == LikesSyncSource.SOUNDCLOUD) {
                    var nextUrl: String? = null
                    var hasMore = true
                    while (hasMore) {
                        val response = if (nextUrl == null) {
                            service.getLikedTracks(
                                userId = userId,
                                clientId = clientId,
                                limit = 50
                            )
                        } else {
                            service.getLikedTracksByUrl(nextUrl)
                        }
                        val items = response.collection.mapNotNull { it.track }
                        allTracks.addAll(items)
                        
                        val rawNextHref = response.nextHref
                        if (!rawNextHref.isNullOrBlank()) {
                            val uri = android.net.Uri.parse(rawNextHref)
                            nextUrl = if (uri.getQueryParameter("client_id") == null) {
                                uri.buildUpon().appendQueryParameter("client_id", clientId).build().toString()
                            } else {
                                rawNextHref
                            }
                        } else {
                            hasMore = false
                        }
                        
                        delay(500)
                    }
                } else {
                    val yandexUid = getYandexUid()
                    if (yandexUid != null) {
                        try {
                            val likedResponse = yandexService.getLikedTracks(yandexUid)
                            yandexTrackRefs = likedResponse.result?.library?.tracks.orEmpty()
                            
                            yandexTrackRefs.chunked(50).forEach { chunk ->
                                val trackIdsStr = chunk.joinToString(",") { if (it.albumId.isNullOrBlank()) it.id else "${it.id}:${it.albumId}" }
                                try {
                                    val tracksDetailsResponse = yandexService.getTracksDetails(trackIdsStr)
                                    allTracks.addAll(tracksDetailsResponse.result.orEmpty().map { it.toSoundCloudTrack() })
                                } catch (e: Exception) {
                                    Log.e("MusicViewModel", "Failed to fetch Yandex tracks chunk details for sync", e)
                                }
                                delay(300)
                            }

                            // Restore original chronological order of liked tracks from Yandex
                            val orderMap = yandexTrackRefs.withIndex().associate { it.value.id to it.index }
                            val sortedAllTracks = allTracks.sortedBy { track ->
                                val yandexId = track.urn?.substringAfter("yandex:track:") ?: ""
                                orderMap[yandexId] ?: Int.MAX_VALUE
                            }
                            allTracks.clear()
                            allTracks.addAll(sortedAllTracks)
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Failed to get Yandex liked tracks", e)
                        }
                    }
                }

                if (allTracks.isEmpty()) {
                    statusFlow.value = LikesSyncStatus(state = SyncState.COMPLETED)
                    return@launch
                }

                statusFlow.value = LikesSyncStatus(
                    state = SyncState.DOWNLOADING,
                    totalTracks = allTracks.size,
                    currentTrackIndex = 0
                )

                var downloadedCount = 0
                var failedCount = 0

                allTracks.forEachIndexed { index, track ->
                    ensureActive()

                    statusFlow.value = statusFlow.value.copy(
                        currentTrackIndex = index + 1,
                        currentTrackTitle = track.title ?: "Unknown Track"
                    )

                    val existing = favoritesRepository.get(track.id)
                    val isAlreadyDownloaded = existing != null && existing.downloadState == DownloadState.DOWNLOADED && !existing.streamUrl.isNullOrBlank()

                    if (isAlreadyDownloaded) {
                        downloadedCount++
                        statusFlow.value = statusFlow.value.copy(
                            downloadedCount = downloadedCount
                        )
                    } else {
                        try {
                            if (existing == null) {
                                favoritesRepository.add(track, streamUrl = null)
                            }
                            favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADING)

                            val success = enqueueDownloadAndWait(track)
                            if (success) {
                                downloadedCount++
                            } else {
                                throw Exception("Track download failed or incomplete")
                            }
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Failed to sync/download track ${track.id}", e)
                            failedCount++
                        }

                        statusFlow.value = statusFlow.value.copy(
                            downloadedCount = downloadedCount,
                            failedCount = failedCount
                        )
                        
                        delay(1000)
                    }
                }

                if (source == LikesSyncSource.YANDEX && yandexTrackRefs.isNotEmpty()) {
                    val currentFavs = favoritesRepository.favorites.value
                    val nonYandex = currentFavs.filter { !it.urn.startsWith("yandex:track:") }
                    val yandex = currentFavs.filter { it.urn.startsWith("yandex:track:") }
                    
                    val orderMap = yandexTrackRefs.withIndex().associate { it.value.id to it.index }
                    val sortedYandex = yandex.sortedBy { track ->
                        val yandexId = track.urn.substringAfter("yandex:track:")
                        orderMap[yandexId] ?: Int.MAX_VALUE
                    }
                    favoritesRepository.reorderTracks(nonYandex + sortedYandex)
                }

                statusFlow.value = statusFlow.value.copy(
                    state = SyncState.COMPLETED
                )

            } catch (e: CancellationException) {
                statusFlow.value = LikesSyncStatus(state = SyncState.IDLE)
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Likes sync failed", e)
                statusFlow.value = LikesSyncStatus(
                    state = SyncState.FAILED,
                    errorMessage = readableMessage(e, isYandex = (source == LikesSyncSource.YANDEX))
                )
            }
        }
    }

    fun startSoundCloudLikesSync() {
        startLikesSync(LikesSyncSource.SOUNDCLOUD)
    }

    fun startYandexLikesSync() {
        startLikesSync(LikesSyncSource.YANDEX)
    }

    fun stopLikesSync() {
        likesSyncJob?.cancel()
        likesSyncJob = null
        // Only reset non-completed statuses (#42)
        if (_soundcloudLikesSyncStatus.value.state != SyncState.COMPLETED) {
            _soundcloudLikesSyncStatus.value = LikesSyncStatus(state = SyncState.IDLE)
        }
        if (_yandexLikesSyncStatus.value.state != SyncState.COMPLETED) {
            _yandexLikesSyncStatus.value = LikesSyncStatus(state = SyncState.IDLE)
        }
    }

    fun resetSoundCloudLikesSyncStatus() {
        _soundcloudLikesSyncStatus.value = LikesSyncStatus(state = SyncState.IDLE)
    }

    fun resetYandexLikesSyncStatus() {
        _yandexLikesSyncStatus.value = LikesSyncStatus(state = SyncState.IDLE)
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

    private var yandexSearchJob: Job? = null

    fun onYandexSearchQueryChange(query: String) {
        _yandexSearchQuery.value = query
        yandexSearchJob?.cancel()

        if (query.trim().isEmpty()) {
            _yandexTracks.value = emptyList()
            _yandexError.value = null
            _yandexLoading.value = false
            return
        }

        yandexSearchJob = viewModelScope.launch {
            delay(500)
            _yandexLoading.value = true
            _yandexError.value = null
            try {
                val response = yandexService.searchTracks(query)
                val yList = response.result?.tracks?.results.orEmpty()
                _yandexTracks.value = yList.map { it.toSoundCloudTrack() }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to search Yandex tracks", e)
                _yandexError.value = "Ошибка поиска: ${readableMessage(e)}"
            } finally {
                _yandexLoading.value = false
            }
        }
    }



    fun playQueuedTrack(track: SoundCloudTrack, customQueue: List<SoundCloudTrack>? = null) {
        viewModelScope.launch {
            _errorMessage.value = null
            val isYandexTrack = track.urn?.startsWith("yandex:track:") == true
            val defaultQueue = if (isYandexTrack) _yandexTracks.value else _tracks.value
            val qTracks = customQueue ?: defaultQueue
            val playableTracks = if (customQueue != null) qTracks else qTracks.filter { isPlayableTrack(it) }
            
            if (playableTracks.isEmpty()) {
                playTrack(track)
                return@launch
            }
            _isLoading.value = true
            try {
                val startIndex = playableTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                val isFromQueueManager = customQueue != null
                if (!isFromQueueManager) {
                    originalQueue = playableTracks
                }
                val clickedTrack = playableTracks.getOrNull(startIndex)
                val queueToPlay = if (shuffleEnabled.value && clickedTrack != null && !isFromQueueManager) {
                    val list = playableTracks.toMutableList()
                    list.removeAt(startIndex)
                    listOf(clickedTrack) + list.shuffled(java.util.Random())
                } else {
                    playableTracks
                }
                val newStartIndex = if (shuffleEnabled.value && !isFromQueueManager) 0 else startIndex
                
                _activeQueue.value = queueToPlay
                resolvedUrls.clear()
                
                val startTrack = queueToPlay.getOrNull(newStartIndex)
                if (startTrack != null) {
                    val isYandex = startTrack.urn?.startsWith("yandex:track:") == true
                    if (isYandex) {
                        val yandexId = startTrack.urn?.removePrefix("yandex:track:") ?: ""
                        resolvedUrls[startTrack.id] = "yandex://track/$yandexId"
                    } else {
                        val clientIdValue = settingsRepository.clientId.value
                        val resolvedUrl = favoritesRepository.get(startTrack.id)?.streamUrl
                            ?: playbackResolver.resolve(startTrack, clientIdValue)
                            ?: ""
                        if (resolvedUrl.isNotEmpty()) {
                            resolvedUrls[startTrack.id] = resolvedUrl
                        }
                    }
                }
                
                val stubs = queueToPlay.map { t ->
                    val localUrl = favoritesRepository.get(t.id)?.streamUrl
                    val isYandex = t.urn?.startsWith("yandex:track:") == true
                    val fallbackUrl = if (isYandex) {
                        val yandexId = t.urn?.removePrefix("yandex:track:") ?: ""
                        "yandex://track/$yandexId"
                    } else {
                        "soundcloud://track/${t.id}"
                    }
                    t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: fallbackUrl)
                }
                musicPlayer.playQueue(stubs, newStartIndex)
                _playingMixId.value = null
                _currentPlayingTrack.value = track
                _selectedTrack.value = track
            } catch (e: Exception) {
                Log.e("MusicViewModel", "playQueuedTrack error", e)
                _errorMessage.value = readableMessage(e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getMp3Duration(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            time?.toLongOrNull() ?: 0L
        } catch (e: java.lang.Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (e: java.lang.Exception) {}
        }
    }

    private suspend fun enqueueDownloadAndWait(track: SoundCloudTrack): Boolean {
        val deferred = kotlinx.coroutines.CompletableDeferred<Boolean>()
        downloadQueue.send(DownloadRequest(track, isRedownload = false) { success ->
            deferred.complete(success)
        })
        return deferred.await()
    }

    private suspend fun performDownload(track: SoundCloudTrack, isRedownload: Boolean): Boolean {
        if (!favoritesRepository.isFavorite(track.id) && !isRedownload) {
            return false
        }

        if (isRedownload) {
            favoritesRepository.get(track.id)?.let { favorite ->
                if (favorite.streamUrl != null) {
                    withContext(Dispatchers.IO) {
                        if (favorite.urn.startsWith("yandex:track:")) {
                            offlineMusicStore.removeProgressive(favorite.streamUrl)
                        } else {
                            offlineMusicStore.removeHls(favorite.streamUrl)
                        }
                    }
                }
            }
            if (!favoritesRepository.isFavorite(track.id)) {
                favoritesRepository.add(track, streamUrl = null)
            } else {
                favoritesRepository.updateStreamUrl(track.id, "")
            }
            favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADING)
        }

        val isYandex = track.urn?.startsWith("yandex:track:") == true
        val clientId = settingsRepository.clientId.value

        try {
            val streamUrl = if (isYandex) {
                val yandexTrackId = track.urn?.substringAfter("yandex:track:")?.substringBefore(":") ?: ""
                val token = settingsRepository.yandexTokenValue()
                if (token.isNotBlank()) {
                    YandexMusicApi.resolveTrackStream(yandexTrackId, token)
                } else {
                    null
                }
            } else {
                if (clientId.isNotBlank()) {
                    var resolved: String? = null
                    try {
                        resolved = playbackResolver.resolve(track, clientId)
                    } catch (e: Exception) {
                        if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                            val newClientId = SoundCloudApi.fetchSoundCloudClientId()
                            if (newClientId != null) {
                                settingsRepository.saveClientId(newClientId)
                                resolved = playbackResolver.resolve(track, newClientId)
                            }
                        }
                        if (resolved == null) throw e
                    }
                    resolved
                } else {
                    null
                }
            }

            if (streamUrl == null) {
                favoritesRepository.updateDownloadState(track.id, DownloadState.FAILED)
                if (isYandex) {
                    _errorMessage.value = "Укажите рабочий токен Яндекс Музыки в настройках."
                } else if (clientId.isBlank()) {
                    _errorMessage.value = "Укажите SoundCloud client_id в настройках для загрузки трека"
                } else {
                    _errorMessage.value = "Поток для загрузки не нашёлся."
                }
                return false
            }

            if (isYandex) {
                val yandexTrackId = track.urn?.substringAfter("yandex:track:")?.substringBefore(":") ?: ""
                val localPath = withContext(Dispatchers.IO) {
                    offlineMusicStore.downloadProgressive(streamUrl, yandexTrackId) { progress ->
                        updateDownloadProgress(track.id, progress)
                    }
                }
                if (localPath != null) {
                    val actualDuration = getMp3Duration(localPath)
                    var expectedDuration = track.duration
                    if (expectedDuration <= 0L) {
                        try {
                            val expectedYandexTrackId = track.urn?.substringAfter("yandex:track:")?.substringBefore(":") ?: ""
                            val response = yandexService.getTracksDetails(expectedYandexTrackId)
                            expectedDuration = response.result?.firstOrNull()?.durationMs ?: 0L
                        } catch (e: Exception) {
                            Log.e("MusicViewModel", "Failed to fetch expected duration during download", e)
                        }
                    }
                    val isValid = if (expectedDuration > 0) {
                        val diff = kotlin.math.abs(actualDuration - expectedDuration)
                        diff <= 8000L || (actualDuration.toFloat() / expectedDuration.toFloat()) >= 0.95f
                    } else {
                        actualDuration > 10000L
                    }
                    
                    if (isValid) {
                        if (favoritesRepository.isFavorite(track.id)) {
                            favoritesRepository.updateStreamUrl(track.id, localPath)
                            favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADED)
                            return true
                        } else {
                            withContext(Dispatchers.IO) {
                                java.io.File(localPath).delete()
                            }
                            return false
                        }
                    } else {
                        withContext(Dispatchers.IO) {
                            java.io.File(localPath).delete()
                        }
                        favoritesRepository.updateDownloadState(track.id, DownloadState.FAILED)
                        _errorMessage.value = "Ошибка: Трек скачался не полностью."
                        return false
                    }
                } else {
                    favoritesRepository.updateDownloadState(track.id, DownloadState.FAILED)
                    return false
                }
            } else {
                // Don't store URL until download completes (#23)
                withContext(Dispatchers.IO) {
                    offlineMusicStore.downloadHls(streamUrl) { progress ->
                        updateDownloadProgress(track.id, progress / 100f)
                    }
                }
                if (favoritesRepository.isFavorite(track.id)) {
                    // Store URL only after successful download (#23)
                    favoritesRepository.updateStreamUrl(track.id, streamUrl)
                    favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADED)
                    return true
                } else {
                    withContext(Dispatchers.IO) {
                        offlineMusicStore.removeHls(streamUrl)
                    }
                    return false
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error downloading track ${track.id}", e)
            favoritesRepository.updateDownloadState(track.id, DownloadState.FAILED)
            _errorMessage.value = readableMessage(e, isYandex = isYandex)
            return false
        } finally {
            _downloadProgress.value = _downloadProgress.value - track.id
        }
    }

    fun loadAllArtistTracks(artistId: String, isYandex: Boolean) {
        viewModelScope.launch {
            _artistLoading.value = true
            _artistError.value = null
            try {
                if (isYandex) {
                    val response = yandexService.getArtistTracks(artistId, page = 0, pageSize = 100)
                    val tracksList = response.result?.tracks.orEmpty().map { it.toSoundCloudTrack() }
                    _currentArtistTracks.value = tracksList
                    _isAllArtistTracksLoaded.value = true
                } else {
                    val clientIdVal = settingsRepository.clientId.value
                    if (clientIdVal.isNotBlank()) {
                        val numericUserId = artistId.toLongOrNull() ?: 0L
                        if (numericUserId != 0L) {
                            val response = service.getUserTracks(numericUserId, clientIdVal, limit = 100)
                            _currentArtistTracks.value = response.collection
                            _isAllArtistTracksLoaded.value = true
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                Log.e("MusicViewModel", "Failed to load all artist tracks", e)
                _artistError.value = "Ошибка: ${readableMessage(e, isYandex = isYandex)}"
            } finally {
                _artistLoading.value = false
            }
        }
    }

    fun redownloadTrack(track: SoundCloudTrack) {
        downloadQueue.trySend(DownloadRequest(track, isRedownload = true))
    }

    fun playYandexTrack(track: SoundCloudTrack, customQueue: List<SoundCloudTrack>? = null) {
        playQueuedTrack(track, customQueue)
    }
}
