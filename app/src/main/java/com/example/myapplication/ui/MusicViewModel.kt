package com.example.myapplication.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.ArtworkUrls
import com.example.myapplication.data.PlayHistoryRequest
import com.example.myapplication.data.DownloadState
import com.example.myapplication.data.TrackLyrics
import com.example.myapplication.data.YandexLyricsRepository
import com.example.myapplication.data.YT_ARTIST_REF
import com.example.myapplication.data.YT_ID_BASE
import com.example.myapplication.data.YouTubeMusicClient
import com.example.myapplication.data.YouTubeStreams
import com.example.myapplication.data.YtDlp
import com.example.myapplication.data.TrackVideo
import com.example.myapplication.data.ClipAligner
import com.example.myapplication.data.YT_SET_REF
import com.example.myapplication.data.YtAuth
import com.example.myapplication.data.YtShelf
import com.example.myapplication.data.isProgressiveSource
import com.example.myapplication.data.youTubeVideoId
import com.example.myapplication.data.toArtistUser
import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.FavoritesRepository
import com.example.myapplication.data.OfflineMusicStore
import com.example.myapplication.data.MixSection
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.data.SoundCloudApi
import com.example.myapplication.data.SoundCloudMix
import com.example.myapplication.data.SoundCloudMixesRepository
import com.example.myapplication.data.SoundCloudPlaybackResolver
import com.example.myapplication.data.SoundCloudSessionRefresher
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudWebRequests
import com.example.myapplication.data.SoundCloudPlaylist
import com.example.myapplication.data.SoundCloudMeResponse
import com.example.myapplication.data.SoundCloudUser
import com.example.myapplication.data.Playlist
import com.example.myapplication.data.PlaylistsRepository
import com.example.myapplication.data.sourceKey
import com.example.myapplication.data.UpdateRepository
import com.example.myapplication.data.UpdateService
import com.example.myapplication.player.MusicPlayer
import com.example.myapplication.player.MusicPlayer.QueueTrack
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
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
    PLAYLISTS,
    DOWNLOADS,
    SETTINGS,
    MIX_DETAIL,
    PLAYLIST_DETAIL,
    ARTIST_DETAIL,
    YANDEX_PLAYLIST_DETAIL,
    YTM_SET_DETAIL
}

/** Where search looks. YouTube Music and Yandex only once they are connected. */
enum class SearchSource { SOUNDCLOUD, YANDEX, YOUTUBE }

/** A YouTube Music search: what was asked, and what came back so far. */
data class YtSearchState(
    val query: String = "",
    val page: com.example.myapplication.data.YtSearchPage? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null
)

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

    /** New versions from GitHub releases; see [UpdateRepository]. */
    val updates = UpdateRepository(
        context = context.applicationContext,
        service = UpdateService(userAgent = "YouCloud/${com.example.myapplication.BuildConfig.VERSION_NAME}"),
        settings = settingsRepository,
        currentVersion = com.example.myapplication.BuildConfig.VERSION_NAME,
        scope = viewModelScope
    )
    val showDebugPercentage = settingsRepository.showDebugPercentage
    val yandexToken = settingsRepository.yandexToken

    val playlists = playlistsRepository.playlists

    private val _selectedPlaylistId = MutableStateFlow<String?>(null)
    val selectedPlaylist = combine(playlistsRepository.playlists, _selectedPlaylistId) { list, id ->
        list.firstOrNull { it.id == id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isClientIdExpired = MutableStateFlow(false)
    val isClientIdExpired = _isClientIdExpired.asStateFlow()

    // The client_id is read per request and re-scraped in place on 401/403, and an expired
    // token is renewed and the request resent, so both kinds of expiry heal inside the failing
    // call instead of surfacing as an error to wait out.
    private val service = SoundCloudApi.createService(
        oauthTokenProvider = settingsRepository::oauthTokenValue,
        clientIdProvider = { settingsRepository.clientId.value },
        onClientIdRefreshed = settingsRepository::saveClientId,
        onSessionExpired = ::renewSoundCloudSession
    )
    private val yandexService = YandexMusicApi.createService(settingsRepository::yandexTokenValue)
    private val lyricsRepository = YandexLyricsRepository(context, yandexService)

    // YouTube Music: signed in through its own site; see onYtMusicLoginCaptured.
    private val ytMusic = YouTubeMusicClient { settingsRepository.ytMusicAuth() }
    val ytMusicAccount = settingsRepository.ytMusicAccount

    private val _ytHome = MutableStateFlow<List<YtShelf>>(emptyList())
    val ytHome = _ytHome.asStateFlow()

    private val _ytHomeLoading = MutableStateFlow(false)
    val ytHomeLoading = _ytHomeLoading.asStateFlow()

    private val _ytHomeError = MutableStateFlow<String?>(null)
    val ytHomeError = _ytHomeError.asStateFlow()

    private val _ytLoginOpen = MutableStateFlow(false)
    val ytLoginOpen = _ytLoginOpen.asStateFlow()

    /** A playlist, mix or album from YouTube Music's home, open on its own screen. */
    private val _ytOpenedSet = MutableStateFlow<SoundCloudPlaylist?>(null)
    val ytOpenedSet = _ytOpenedSet.asStateFlow()

    private val _ytSetLoading = MutableStateFlow(false)
    val ytSetLoading = _ytSetLoading.asStateFlow()

    private val _ytSetError = MutableStateFlow<String?>(null)
    val ytSetError = _ytSetError.asStateFlow()
    private var ytSetJob: Job? = null

    /** Synced lyrics of the playing track, when Yandex has them; the player offers them then. */
    private val _lyrics = MutableStateFlow<TrackLyrics?>(null)
    val lyrics = _lyrics.asStateFlow()

    // The queue as last set, so reopening the app while music plays can put it back.
    private val queueFile = java.io.File(context.filesDir, "player_queue.json")
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

    private val _yandexHasMore = MutableStateFlow(false)
    val yandexHasMore = _yandexHasMore.asStateFlow()

    private val _yandexLoadingMore = MutableStateFlow(false)
    val yandexLoadingMore = _yandexLoadingMore.asStateFlow()

    private var yandexSearchPage = 0

    private val _downloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    fun updateDownloadProgress(trackId: Long, progress: Float) {
        viewModelScope.launch {
            _downloadProgress.value = _downloadProgress.value + (trackId to progress)
        }
    }

    private val _searchSource = MutableStateFlow(SearchSource.SOUNDCLOUD)
    val searchSource = _searchSource.asStateFlow()

    /** Switches search to [source], asking it what the one before was asked. */
    fun setSearchSource(source: SearchSource) {
        val query = when (_searchSource.value) {
            SearchSource.SOUNDCLOUD -> _searchQuery.value
            SearchSource.YANDEX -> _yandexSearchQuery.value
            SearchSource.YOUTUBE -> _ytSearch.value.query
        }
        _searchSource.value = source
        when (source) {
            SearchSource.SOUNDCLOUD -> if (query != _searchQuery.value) onSearchQueryChange(query)
            SearchSource.YANDEX -> if (query != _yandexSearchQuery.value) onYandexSearchQueryChange(query)
            SearchSource.YOUTUBE -> if (query != _ytSearch.value.query) onYtSearchQueryChange(query)
        }
    }

    private val _ytSearch = MutableStateFlow(YtSearchState())
    val ytSearch = _ytSearch.asStateFlow()
    private var ytSearchJob: Job? = null

    fun onYtSearchQueryChange(query: String) {
        ytSearchJob?.cancel()
        if (query.isBlank()) {
            _ytSearch.value = YtSearchState(query = query)
            return
        }
        _ytSearch.value = _ytSearch.value.copy(query = query, loadingMore = false, error = null)
        ytSearchJob = viewModelScope.launch {
            delay(400)
            _ytSearch.value = _ytSearch.value.copy(loading = true)
            try {
                val page = ytMusic.search(query.trim())
                _ytSearch.value = _ytSearch.value.copy(page = page, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "YouTube Music search failed", e)
                _ytSearch.value = _ytSearch.value.copy(
                    page = null,
                    loading = false,
                    error = "Ошибка поиска: ${readableMessage(e)}"
                )
            }
        }
    }

    fun loadMoreYtSearchTracks() {
        val state = _ytSearch.value
        val token = state.page?.continuation ?: return
        if (state.loading || state.loadingMore) return
        // Shares the search job so that typing a new query cancels a page still in flight.
        ytSearchJob = viewModelScope.launch {
            _ytSearch.value = state.copy(loadingMore = true)
            try {
                val (more, next) = ytMusic.moreSongs(token)
                val current = _ytSearch.value
                val page = current.page ?: return@launch
                _ytSearch.value = current.copy(
                    page = page.copy(tracks = (page.tracks + more).distinctBy { it.id }, continuation = next),
                    loadingMore = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "More YouTube Music results failed", e)
                _ytSearch.value = _ytSearch.value.copy(loadingMore = false)
            }
        }
    }

    /** Set when automatic recovery is exhausted and only a real sign-in can help. */
    private val _needsRelogin = MutableStateFlow(false)
    val needsRelogin = _needsRelogin.asStateFlow()

    /**
     * Captcha SoundCloud's bot protection answered a like with. Shown so the listener can solve
     * it, as the website would; solving it lets likes through from this network again.
     */
    private val _antiBotCaptchaUrl = MutableStateFlow<String?>(null)
    val antiBotCaptchaUrl = _antiBotCaptchaUrl.asStateFlow()

    private var lastLikeBlockAt = 0L
    private var likeFlushJob: Job? = null

    private var authRecoveryJob: Job? = null
    private var authRecoveryAttempts = 0
    private var lastAuthRecoveryAt = 0L
    private var lastSessionCheckAt = 0L

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

    private val _searchAlbums = MutableStateFlow<List<SoundCloudPlaylist>>(emptyList())
    val searchAlbums = _searchAlbums.asStateFlow()

    private val _searchPlaylists = MutableStateFlow<List<SoundCloudPlaylist>>(emptyList())
    val searchPlaylists = _searchPlaylists.asStateFlow()

    private val _searchArtists = MutableStateFlow<List<SoundCloudUser>>(emptyList())
    val searchArtists = _searchArtists.asStateFlow()

    // Yandex's search answers with all four kinds at once, like SoundCloud's four requests.
    private val _yandexSearchAlbums = MutableStateFlow<List<SoundCloudPlaylist>>(emptyList())
    val yandexSearchAlbums = _yandexSearchAlbums.asStateFlow()

    private val _yandexSearchPlaylists = MutableStateFlow<List<SoundCloudPlaylist>>(emptyList())
    val yandexSearchPlaylists = _yandexSearchPlaylists.asStateFlow()

    private val _yandexSearchArtists = MutableStateFlow<List<SoundCloudUser>>(emptyList())
    val yandexSearchArtists = _yandexSearchArtists.asStateFlow()

    private val _searchHasMore = MutableStateFlow(false)
    val searchHasMore = _searchHasMore.asStateFlow()

    private val _searchLoadingMore = MutableStateFlow(false)
    val searchLoadingMore = _searchLoadingMore.asStateFlow()

    // Raw offset into SoundCloud's results. Unplayable tracks are dropped after the fact, so the
    // visible list length can't be used to ask for the next page.
    private var searchTracksOffset = 0

    /** An album or playlist opened from the search results, shown in place of the list. */
    private val _searchOpenedPlaylist = MutableStateFlow<SoundCloudPlaylist?>(null)
    val searchOpenedPlaylist = _searchOpenedPlaylist.asStateFlow()

    private val _searchPlaylistLoading = MutableStateFlow(false)
    val searchPlaylistLoading = _searchPlaylistLoading.asStateFlow()

    private val _searchPlaylistError = MutableStateFlow<String?>(null)
    val searchPlaylistError = _searchPlaylistError.asStateFlow()

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

    private val _trendingSection = MutableStateFlow<MixSection?>(null)
    val trendingSection = _trendingSection.asStateFlow()

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

    /** The playing track's music video or videoshot, once found; see [findTrackVideo]. */
    private val _trackVideo = MutableStateFlow<TrackVideo?>(null)
    val trackVideo = _trackVideo.asStateFlow()

    /**
     * The playing track's music video while it is still being lined up with the track: the
     * player buffers it meanwhile, unseen, so that it can show at once when it is ready.
     */
    private val _pendingTrackVideo = MutableStateFlow<TrackVideo?>(null)
    val pendingTrackVideo = _pendingTrackVideo.asStateFlow()

    // Tracks looked up lately, with or without a video, so reopening the player doesn't ask again.
    private val videoLookups = java.util.concurrent.ConcurrentHashMap<Long, Pair<TrackVideo?, Long>>()

    // Lookups under way. Not tied to the track on screen: skipping to a track whose video is
    // still being found picks that work up instead of starting it over.
    private val videoLookupsInFlight = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<Pair<TrackVideo?, Long>>>()

    private fun freshVideoLookup(trackId: Long): Pair<TrackVideo?, Long>? =
        videoLookups[trackId]?.takeIf { System.currentTimeMillis() - it.second < VIDEO_LOOKUP_TTL_MS }

    private fun videoLookup(track: SoundCloudTrack): kotlinx.coroutines.Deferred<Pair<TrackVideo?, Long>> =
        videoLookupsInFlight[track.id] ?: viewModelScope.async(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val video = try {
                findTrackVideo(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "No video for ${track.urn}: $e")
                null
            }
            (video to System.currentTimeMillis()).also {
                videoLookups[track.id] = it
                videoLookupsInFlight.remove(track.id)
            }
        }.also {
            // Registered before it starts, so that it can't finish, and unregister, first.
            videoLookupsInFlight[track.id] = it
            it.start()
        }

    private val _soundcloudLikesSyncStatus = MutableStateFlow(LikesSyncStatus())
    val soundcloudLikesSyncStatus = _soundcloudLikesSyncStatus.asStateFlow()

    private val _yandexLikesSyncStatus = MutableStateFlow(LikesSyncStatus())
    val yandexLikesSyncStatus = _yandexLikesSyncStatus.asStateFlow()
    private var likesSyncJob: Job? = null

    private val _likesPushStatus = MutableStateFlow(LikesPushStatus())
    val likesPushStatus = _likesPushStatus.asStateFlow()
    private var likesPushJob: Job? = null

    private var searchJob: Job? = null
    private var searchMoreJob: Job? = null
    private var searchPlaylistJob: Job? = null
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
        // Set for a liked album's "download all": the file is saved for that playlist only and
        // the track never enters "Скачанное".
        val playlistId: String? = null,
        val onComplete: (Boolean) -> Unit = {}
    )

    private val downloadQueue = kotlinx.coroutines.channels.Channel<DownloadRequest>(kotlinx.coroutines.channels.Channel.UNLIMITED)

    init {
        updates.startSchedule()
        backfillArtwork()
        reportPlaysToSoundCloud()

        viewModelScope.launch { restoreQueueFromPlayer() }
        loadYtHome()
        viewModelScope.launch {
            // The first value is the empty start, which must not overwrite the saved queue
            // before it has been restored.
            _activeQueue.drop(1).collectLatest { queue ->
                delay(1_000)
                withContext(Dispatchers.IO) {
                    runCatching { queueFile.writeText(com.google.gson.Gson().toJson(queue)) }
                        .onFailure { Log.w("MusicViewModel", "Couldn't save the queue", it) }
                }
            }
        }

        viewModelScope.launch {
            // Looked up only while the full player is open: what plays in the background has
            // no picture to show, and every lookup costs traffic (and yt-dlp, on YouTube).
            combine(
                _currentPlayingTrack,
                _selectedTrack.map { it != null },
                settingsRepository.playerVideos
            ) { track, playerOpen, enabled -> track.takeIf { playerOpen && enabled } }
                .distinctUntilChangedBy { it?.id }
                .collectLatest { track ->
                    if (_trackVideo.value?.trackId != track?.id) _trackVideo.value = null
                    if (_pendingTrackVideo.value?.trackId != track?.id) _pendingTrackVideo.value = null
                    if (track == null) return@collectLatest
                    val video = freshVideoLookup(track.id) ?: run {
                        // Flicking through the queue shouldn't start a lookup for every track passed.
                        delay(700)
                        videoLookup(track).await()
                    }
                    // In this order, so that a pending video turning into the found one never
                    // leaves a moment with neither, which would drop the player and its buffer.
                    _trackVideo.value = video?.first
                    _pendingTrackVideo.value = null
                    // The next track's video, found while this one plays, shows as soon as it starts.
                    val nextIndex = musicPlayer.getNextMediaItemIndex()
                    _activeQueue.value.getOrNull(nextIndex)
                        ?.takeIf { next -> freshVideoLookup(next.id) == null }
                        ?.let(::videoLookup)
                }
        }

        viewModelScope.launch {
            _currentPlayingTrack.distinctUntilChangedBy { it?.id }.collectLatest { track ->
                _lyrics.value = null
                val urn = track?.urn?.takeIf { it.startsWith("yandex:track:") } ?: return@collectLatest
                val lines = lyricsRepository.syncedLyrics(urn) ?: return@collectLatest
                _lyrics.value = TrackLyrics(track.id, lines)
            }
        }

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
                        _trendingSection.value = null
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
                            if (isProgressiveSource(fav.urn)) {
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
                val success = request.playlistId?.let { performPlaylistDownload(request.track, it) }
                    ?: performDownload(request.track, request.isRedownload)
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
                    val hidden = settingsRepository.hiddenYandexPlaylists.value
                    _yandexPlaylists.value = playlistList.filterNot { hidden.contains(it.id.toString()) }
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load Yandex playlists", e)
            } finally {
                _yandexPlaylistsLoading.value = false
            }
        }
    }

    /** Yandex playlists belong to the account, so they're hidden locally rather than deleted. */
    fun hideYandexPlaylist(id: Long) {
        settingsRepository.setYandexPlaylistHidden(id.toString(), true)
        _yandexPlaylists.value = _yandexPlaylists.value.filterNot { it.id == id }
        _selectedYandexPlaylist.value = null
        _screen.value = AppScreen.HOME
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
            _trendingSection.value = null
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
                    .collect { sections ->
                        _mixSection.value = sections.moods
                        _stationSection.value = sections.stations
                        _trendingSection.value = sections.trending
                        _mixesLoading.value = false // Done with initial load
                    }
            } catch (e: Exception) {
                if (_mixSection.value == null && _stationSection.value == null && _trendingSection.value == null) {
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
        _trendingSection.value = null
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
                    val resolvedUrl = localStreamUrl(resolveTarget.id)
                        ?: playbackResolver.resolve(resolveTarget, clientId)
                        ?: ""
                    if (resolvedUrl.isNotEmpty()) {
                        resolvedUrls[resolveTarget.id] = resolvedUrl
                    }
                }
                
                // Play immediately with first track resolved and others as stubs
                val stubs = queueToPlay.map { t ->
                    val localUrl = localStreamUrl(t.id)
                    t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: placeholderStreamUrl(t))
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
        searchMoreJob?.cancel()
        closeSearchPlaylist()

        if (query.length < 3) {
            clearSoundCloudSearchResults()
            _errorMessage.value = null
            _isLoading.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            if (settingsRepository.clientId.value.isBlank()) {
                _errorMessage.value = "Укажите SoundCloud client_id в настройках"
                clearSoundCloudSearchResults()
                return@launch
            }
            searchTracks(query)
        }
    }

    private fun clearSoundCloudSearchResults() {
        _tracks.value = emptyList()
        _searchAlbums.value = emptyList()
        _searchPlaylists.value = emptyList()
        _searchArtists.value = emptyList()
        _searchHasMore.value = false
        _searchLoadingMore.value = false
        searchTracksOffset = 0
    }

    private suspend fun searchTracks(query: String) {
        _isLoading.value = true
        _errorMessage.value = null
        val clientId = settingsRepository.clientId.value

        try {
            coroutineScope {
                // Albums and playlists are extras: if either fails, the tracks still show.
                val albums = async {
                    runCatching { service.searchAlbums(query, clientId).collection }
                        .onFailure { if (it !is CancellationException) Log.w("MusicViewModel", "Album search failed", it) }
                        .getOrDefault(emptyList())
                }
                val playlists = async {
                    runCatching { service.searchPlaylists(query, clientId).collection }
                        .onFailure { if (it !is CancellationException) Log.w("MusicViewModel", "Playlist search failed", it) }
                        .getOrDefault(emptyList())
                }
                val artists = async {
                    runCatching { service.searchUsers(query, clientId).collection }
                        .onFailure { if (it !is CancellationException) Log.w("MusicViewModel", "Artist search failed", it) }
                        .getOrDefault(emptyList())
                }
                val results = service.searchTracks(
                    query = query,
                    clientId = clientId,
                    limit = SEARCH_PAGE_SIZE
                )
                _tracks.value = results.collection.filter { isPlayableTrack(it) }.distinctBy { it.id }
                searchTracksOffset = results.collection.size
                _searchHasMore.value = results.nextHref != null && results.collection.isNotEmpty()
                _searchAlbums.value = albums.await().filter { it.trackCount > 0 }.distinctBy { it.id }
                _searchPlaylists.value = playlists.await().filter { it.trackCount > 0 }.distinctBy { it.id }
                // Accounts that only listen come up too; an artist is someone with tracks.
                _searchArtists.value = artists.await().filter { (it.trackCount ?: 0) > 0 }.distinctBy { it.id }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            clearSoundCloudSearchResults()
            handleSoundCloudApiError(e)
            _errorMessage.value = readableMessage(e)
        } finally {
            _isLoading.value = false
        }
    }

    fun loadMoreSearchTracks() {
        val query = _searchQuery.value
        if (query.length < 3 || !_searchHasMore.value || _isLoading.value || _searchLoadingMore.value) return

        searchMoreJob = viewModelScope.launch {
            _searchLoadingMore.value = true
            try {
                val results = service.searchTracks(
                    query = query,
                    clientId = settingsRepository.clientId.value,
                    limit = SEARCH_PAGE_SIZE,
                    offset = searchTracksOffset
                )
                if (_searchQuery.value != query) return@launch
                // Later pages repeat tracks from earlier ones now and then, and a duplicate key
                // crashes the list.
                _tracks.value = (_tracks.value + results.collection.filter { isPlayableTrack(it) })
                    .distinctBy { it.id }
                searchTracksOffset += results.collection.size
                _searchHasMore.value = results.nextHref != null && results.collection.isNotEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleSoundCloudApiError(e)
                _errorMessage.value = readableMessage(e)
            } finally {
                _searchLoadingMore.value = false
            }
        }
    }

    fun openSearchPlaylist(playlist: SoundCloudPlaylist) {
        _searchOpenedPlaylist.value = playlist.copy(tracks = playlist.knownTracks.filter { isPlayableTrack(it) })
        searchPlaylistJob?.cancel()
        _searchPlaylistError.value = null
        searchPlaylistJob = viewModelScope.launch {
            _searchPlaylistLoading.value = true
            val isYandex = playlist.permalinkUrl?.startsWith("yandex:") == true
            val isYouTube = playlist.permalinkUrl?.startsWith(YT_SET_REF) == true
            try {
                val tracks = when {
                    isYandex -> loadYandexSetTracks(playlist)
                    isYouTube -> ytMusic.setTracks(playlist)
                    else -> loadSoundCloudPlaylistTracks(playlist)
                }
                if (_searchOpenedPlaylist.value?.id == playlist.id) {
                    _searchOpenedPlaylist.value = playlist.copy(tracks = tracks)
                }
                syncLikedAlbum(playlist.copy(tracks = tracks))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load playlist ${playlist.permalinkUrl ?: playlist.id}", e)
                if (!isYandex && !isYouTube) handleSoundCloudApiError(e)
                _searchPlaylistError.value = readableMessage(e, isYandex = isYandex)
            } finally {
                _searchPlaylistLoading.value = false
            }
        }
    }

    fun closeSearchPlaylist() {
        searchPlaylistJob?.cancel()
        _searchOpenedPlaylist.value = null
        _searchPlaylistLoading.value = false
        _searchPlaylistError.value = null
    }

    /** Tracks of a Yandex album (`yandex:album:<id>`) or someone's playlist (`yandex:playlist:<owner>:<kind>`). */
    private suspend fun loadYandexSetTracks(set: SoundCloudPlaylist): List<SoundCloudTrack> {
        val ref = set.permalinkUrl.orEmpty()
        val tracks = when {
            ref.startsWith("yandex:album:") -> {
                val albumId = ref.removePrefix("yandex:album:").toLong()
                yandexService.getAlbumWithTracks(albumId).result?.volumes.orEmpty().flatten()
                    .map { it.toSoundCloudTrack(customAlbumId = albumId.toString()) }
            }
            ref.startsWith("yandex:playlist:") -> {
                val (owner, kind) = ref.removePrefix("yandex:playlist:").split(":").map { it.toLong() }
                yandexService.getPlaylistDetail(owner, kind).result?.tracks.orEmpty()
                    .mapNotNull { it.track?.toSoundCloudTrack() }
            }
            else -> emptyList()
        }
        return tracks.filter { isPlayableTrack(it) }.distinctBy { it.id }
    }

    /**
     * Full, playable track list of a SoundCloud set. Listings only carry metadata for the first
     * few tracks, so the id-only stubs are fetched by id; order follows the set, not the batch.
     */
    private suspend fun loadSoundCloudPlaylistTracks(playlist: SoundCloudPlaylist): List<SoundCloudTrack> {
        val clientId = settingsRepository.clientId.value
        val entries = if (playlist.knownTracks.size < playlist.trackCount) {
            service.getPlaylist(playlist.id, clientId).knownTracks
        } else {
            playlist.knownTracks
        }
        val stubIds = entries.filter { it.title.isNullOrBlank() }.map { it.id }
        val fetched = stubIds.chunked(50)
            .flatMap { chunk -> service.getTracksByIds(chunk.joinToString(","), clientId) }
            .associateBy { it.id }
        return entries
            .mapNotNull { entry -> if (entry.title.isNullOrBlank()) fetched[entry.id] else entry }
            .filter { isPlayableTrack(it) }
            .distinctBy { it.id }
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
                    val serviceUrl = serviceStreamUrl(startTrack)
                    if (serviceUrl != null) {
                        resolvedUrls[startTrack.id] = serviceUrl
                    } else {
                        val clientIdValue = settingsRepository.clientId.value
                        val resolvedUrl = localStreamUrl(startTrack.id)
                            ?: playbackResolver.resolve(startTrack, clientIdValue)
                            ?: ""
                        if (resolvedUrl.isNotEmpty()) {
                            resolvedUrls[startTrack.id] = resolvedUrl
                        }
                    }
                }

                // Play immediately with starting track resolved and others as stubs
                val stubs = queueToPlay.map { t ->
                    val localUrl = localStreamUrl(t.id)
                    val fallbackUrl = placeholderStreamUrl(t)
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

    /**
     * For tracks whose audio the playback service finds itself — Yandex and YouTube — the URL
     * it resolves; null for SoundCloud, whose streams the app resolves up front.
     */
    private fun serviceStreamUrl(track: SoundCloudTrack): String? {
        track.youTubeVideoId?.let { return "ytmusic://track/$it" }
        if (track.urn?.startsWith("yandex:track:") == true) {
            return "yandex://track/${track.urn.removePrefix("yandex:track:")}"
        }
        return null
    }

    /** What a not-yet-resolved queue entry points at; the service resolves it when it's reached. */
    private fun placeholderStreamUrl(track: SoundCloudTrack): String =
        serviceStreamUrl(track) ?: "soundcloud://track/${track.id}"

    private suspend fun resolveTrackIfNeeded(index: Int) {
        val track = _activeQueue.value.getOrNull(index) ?: return
        if (resolvedUrls.containsKey(track.id)) return

        Log.d("MusicViewModel", "Lazy resolving track: ${track.title}")
        try {
            // Yandex and YouTube are resolved by the playback service (#5)
            val serviceUrl = serviceStreamUrl(track)
            val streamUrl = if (serviceUrl != null) {
                localStreamUrl(track.id) ?: serviceUrl
            } else {
                localStreamUrl(track.id)
                    ?: playbackResolver.resolve(track, settingsRepository.clientId.value)
                    ?: ""
            }
            
            if (streamUrl.isNotEmpty()) {
                resolvedUrls[track.id] = streamUrl
                // Update the player queue with the new URL
                val updatedQueue = _activeQueue.value.map { t ->
                    val localUrl = localStreamUrl(t.id)
                    val fallbackUrl = placeholderStreamUrl(t)
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
                val serviceUrl = serviceStreamUrl(track)
                val clientId = settingsRepository.clientId.value
                if (clientId.isBlank() && serviceUrl == null) {
                    _errorMessage.value = "Укажите SoundCloud client_id в настройках"
                    return@launch
                }
                val streamUrl = if (serviceUrl != null) {
                    serviceUrl
                } else {
                    localStreamUrl(track.id)
                        ?: playbackResolver.resolve(track, clientId)
                }

                if (streamUrl == null) {
                    _errorMessage.value = "Для этого трека не нашёлся доступный поток."
                    return@launch
                }

                _activeQueue.value = listOf(track)
                // Shuffle draws from originalQueue, so a single-track play has to claim it too.
                // Leaving it stale meant shuffling here reshuffled whichever list was loaded
                // earlier — the artist's whole discography rather than what is actually playing.
                originalQueue = listOf(track)
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

    /**
     * Puts back the queue of music that kept playing while the app was closed. The screen state
     * starts empty on reopening while the playback service carries on, so the mini player had a
     * title (from the service) but no track behind it — no cover, and nothing to open.
     *
     * The order is the service's; the tracks are the saved ones where they match, or rebuilt
     * from what the service knows. The service's own URLs are taken as already resolved: resolving
     * the playing track again would swap its media item and restart it.
     */
    private suspend fun restoreQueueFromPlayer() {
        musicPlayer.connected.first { it }
        if (_activeQueue.value.isNotEmpty()) return
        val items = musicPlayer.queueSnapshot()
        if (items.isEmpty()) return

        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val type = object : com.google.gson.reflect.TypeToken<List<SoundCloudTrack>>() {}.type
                com.google.gson.Gson().fromJson<List<SoundCloudTrack>>(queueFile.readText(), type)
            }.getOrNull().orEmpty()
        }.associateBy { it.id }
        // Something may have started playing while the file was read.
        if (_activeQueue.value.isNotEmpty()) return

        items.forEach { item -> resolvedUrls[item.id] = item.uri.orEmpty() }
        val restored = items.map { item -> saved[item.id] ?: item.toStubTrack() }
        originalQueue = restored
        _activeQueue.value = restored
        musicPlayer.currentTrackId.value
            ?.let { id -> restored.firstOrNull { it.id == id } }
            ?.let { _currentPlayingTrack.value = it }
        Log.d("MusicViewModel", "Restored a queue of ${restored.size} from the playback service")
    }

    private fun MusicPlayer.QueueItem.toStubTrack() = SoundCloudTrack(
        id = id,
        // Ids say where a track is from: Yandex ones are negative, imported files are timestamps.
        urn = when {
            // A YouTube id is a hash; the saved queue is what knows the video behind it.
            id <= -YT_ID_BASE -> null
            id < 0 -> "yandex:track:${-id - 1_000_000_000L}"
            id > 100_000_000_000L -> "local:track:$id"
            else -> "soundcloud:tracks:$id"
        },
        kind = "track",
        title = title,
        artworkUrl = artworkUrl,
        user = SoundCloudUser(username = artist),
        streamable = true
    )

    fun openYtMusicLogin() {
        _ytLoginOpen.value = true
    }

    fun cancelYtMusicLogin() {
        _ytLoginOpen.value = false
    }

    /** The login page reached music.youtube.com signed in; [auth] is its session. */
    fun onYtMusicLoginCaptured(auth: YtAuth) {
        Log.d("MusicViewModel", "YouTube Music session captured: sapisid=${auth.sapisid != null}, " +
            "visitor=${auth.visitorData != null}, authUser=${auth.authUser}")
        _ytLoginOpen.value = false
        viewModelScope.launch {
            // Saved first, so the account request below is already a signed-in one.
            settingsRepository.saveYtMusicAuth(auth, accountName = null)
            val name = runCatching { ytMusic.accountName() }
                .onFailure { Log.w("MusicViewModel", "YouTube Music account lookup failed", it) }
                .getOrNull()
            settingsRepository.saveYtMusicAuth(auth, name)
            loadYtHome()
        }
    }

    fun logoutYtMusic() {
        settingsRepository.resetYtMusicAuth()
        _ytHome.value = emptyList()
        _ytHomeError.value = null
    }

    fun loadYtHome() {
        if (settingsRepository.ytMusicAuth() == null || _ytHomeLoading.value) return
        // yt-dlp plays these tracks; unpacking it and fetching the current release takes a while
        // the first time, better spent now than on the first track.
        YtDlp.warmUp(context, settingsRepository.ytMusicAuth())
        viewModelScope.launch {
            _ytHomeLoading.value = true
            _ytHomeError.value = null
            try {
                _ytHome.value = ytMusic.home()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "YouTube Music home failed", e)
                _ytHomeError.value = if (e is com.example.myapplication.data.YtMusicException && e.code == 401) {
                    "YouTube Music не принял вход. Войдите заново в настройках."
                } else {
                    readableMessage(e)
                }
            } finally {
                _ytHomeLoading.value = false
            }
        }
    }

    /** Opens a YouTube Music set; a row of songs from home arrives with its tracks already. */
    fun openYtSet(set: SoundCloudPlaylist) {
        _ytOpenedSet.value = set
        _ytSetError.value = null
        _screen.value = AppScreen.YTM_SET_DETAIL
        if (set.knownTracks.isNotEmpty()) return
        ytSetJob?.cancel()
        ytSetJob = viewModelScope.launch {
            _ytSetLoading.value = true
            try {
                val tracks = ytMusic.setTracks(set)
                if (_ytOpenedSet.value?.id == set.id) {
                    _ytOpenedSet.value = set.copy(tracks = tracks, trackCount = tracks.size)
                }
                if (tracks.isEmpty()) _ytSetError.value = "В этой подборке не нашлось треков."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "YouTube Music set ${set.permalinkUrl} failed", e)
                _ytSetError.value = readableMessage(e)
            } finally {
                _ytSetLoading.value = false
            }
        }
    }

    fun closeYtSet() {
        ytSetJob?.cancel()
        _ytOpenedSet.value = null
        _ytSetLoading.value = false
        _screen.value = AppScreen.HOME
    }

    /** Plays YouTube Music's radio from [track]: the track, then what it would play after it. */
    fun playYtRadio(track: SoundCloudTrack) {
        val videoId = track.youTubeVideoId ?: return
        viewModelScope.launch {
            try {
                val radio = ytMusic.radio(videoId).filterNot { it.id == track.id }
                playQueuedTrack(track, listOf(track) + radio)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "YouTube Music radio for $videoId failed", e)
                android.widget.Toast.makeText(context, "Не удалось загрузить радио: ${readableMessage(e)}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** A like mirrored to YouTube Music, when it's connected. Failing is not worth bothering anyone. */
    private fun rateOnYouTube(videoId: String, like: Boolean) {
        if (settingsRepository.ytMusicAuth() == null) return
        viewModelScope.launch {
            runCatching { ytMusic.rate(videoId, like) }
                .onFailure { Log.w("MusicViewModel", "YouTube Music rating of $videoId failed", it) }
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

    fun openPlaylists() {
        _screen.value = AppScreen.PLAYLISTS
    }

    fun closePlaylists() {
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

    // An artist opened from search results goes back to them, not home.
    private var returnToSearchFromArtist = false

    /**
     * From search: the artist is the one tapped, not the playing track's. [openArtistDetails]
     * would otherwise read a playing Yandex track as "this is a Yandex artist" — so the artist's
     * own link stands in for the track urn it looks at.
     */
    fun openArtistFromSearch(artist: SoundCloudUser) {
        openArtistDetails(
            userId = artist.id ?: 0L,
            permalinkUrl = artist.permalinkUrl,
            username = artist.username,
            trackUrn = artist.permalinkUrl.orEmpty(),
            avatarUrl = artist.avatarUrl
        )
        returnToSearchFromArtist = true
    }

    fun openArtistDetails(
        userId: Long,
        permalinkUrl: String?,
        username: String?,
        trackUrn: String? = null,
        avatarUrl: String? = null
    ) {
        returnToSearchFromArtist = false
        if (permalinkUrl?.startsWith(YT_ARTIST_REF) == true) {
            openYouTubeArtist(permalinkUrl.removePrefix(YT_ARTIST_REF), username, avatarUrl)
            return
        }
        val activeUrn = trackUrn ?: _selectedTrack.value?.urn ?: _currentPlayingTrack.value?.urn
        viewModelScope.launch {
            _selectedTrack.value = null // Close the player overlay
            _selectedMix.value = null   // Close the mix screen if open (prevents mix list showing behind)
            // What is already known about the artist, so the page has its name (and portrait,
            // from search) while the rest loads. Left empty, the page had nothing to draw, and
            // the screen transition grew it out of the top-left corner once it arrived.
            _currentArtist.value = SoundCloudUser(
                id = userId.takeIf { it != 0L },
                username = username,
                avatarUrl = avatarUrl,
                permalinkUrl = permalinkUrl
            )
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
                            followersCount = artist.likesCount ?: artist.stats?.likes ?: 0,
                            // `popularTracks` is a short highlight reel, not the discography —
                            // using its size showed "10 треков" for artists with hundreds.
                            trackCount = artist.counts?.tracks
                                ?: response.result?.tracks.orEmpty().size,
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

    // The playlist of all a YouTube Music artist's songs, which "Все" opens up.
    private var ytArtistSongs: SoundCloudPlaylist? = null
    private var ytArtistJob: Job? = null

    private fun openYouTubeArtist(channelId: String, name: String?, avatarUrl: String?) {
        _selectedTrack.value = null
        _selectedMix.value = null
        _currentArtist.value = SoundCloudUser(username = name, avatarUrl = avatarUrl, permalinkUrl = YT_ARTIST_REF + channelId)
        _screen.value = AppScreen.ARTIST_DETAIL
        _artistLoading.value = true
        _artistError.value = null
        _currentArtistTracks.value = emptyList()
        _currentArtistPlaylists.value = emptyList()
        _selectedArtistPlaylist.value = null
        _isAllArtistTracksLoaded.value = false
        ytArtistSongs = null
        ytArtistJob?.cancel()
        ytArtistJob = viewModelScope.launch {
            try {
                val page = ytMusic.artist(channelId)
                _currentArtist.value = page.artist.copy(
                    username = page.artist.username?.takeIf { it.isNotBlank() } ?: name,
                    avatarUrl = page.artist.avatarUrl ?: avatarUrl
                )
                _currentArtistTracks.value = page.topSongs
                _currentArtistPlaylists.value = page.releases
                ytArtistSongs = page.allSongs
                _isAllArtistTracksLoaded.value = page.allSongs == null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "YouTube Music artist $channelId failed", e)
                _artistError.value = "Не удалось загрузить артиста: ${readableMessage(e)}"
            } finally {
                _artistLoading.value = false
            }
        }
    }

    fun closeArtist() {
        _currentArtist.value = null
        _currentArtistTracks.value = emptyList()
        _currentArtistPlaylists.value = emptyList()
        _selectedArtistPlaylist.value = null
        _screen.value = if (returnToSearchFromArtist) AppScreen.SEARCH else AppScreen.HOME
        returnToSearchFromArtist = false
    }

    fun selectArtistPlaylist(playlist: SoundCloudPlaylist) {
        _selectedArtistPlaylist.value = playlist
        val isYandexAlbum = playlist.permalinkUrl?.startsWith("yandex:album:") == true
        if (playlist.permalinkUrl?.startsWith(YT_SET_REF) == true) {
            viewModelScope.launch {
                _artistLoading.value = true
                try {
                    val tracks = ytMusic.setTracks(playlist)
                    if (_selectedArtistPlaylist.value?.id == playlist.id) {
                        _selectedArtistPlaylist.value = playlist.copy(tracks = tracks, trackCount = tracks.size)
                    }
                    syncLikedAlbum(playlist.copy(tracks = tracks))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "YouTube Music set ${playlist.permalinkUrl} failed", e)
                } finally {
                    _artistLoading.value = false
                }
            }
        } else if (isYandexAlbum) {
            viewModelScope.launch {
                _artistLoading.value = true
                try {
                    val albumId = playlist.permalinkUrl!!.substringAfter("yandex:album:").toLongOrNull()
                    if (albumId != null) {
                        val response = yandexService.getAlbumWithTracks(albumId)
                        val tracks = response.result?.volumes?.flatten()?.map { it.toSoundCloudTrack(albumId.toString()) } ?: emptyList()
                        _selectedArtistPlaylist.value = playlist.copy(tracks = tracks)
                        syncLikedAlbum(playlist.copy(tracks = tracks))
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to fetch Yandex album tracks", e)
                } finally {
                    _artistLoading.value = false
                }
            }
        } else if (playlist.permalinkUrl?.startsWith("yandex:") != true) {
            // Stream sets arrive with id-only stubs past the first few tracks, which showed up as
            // "Unknown Track" rows that could not play.
            viewModelScope.launch {
                _artistLoading.value = true
                try {
                    val tracks = loadSoundCloudPlaylistTracks(playlist)
                    if (_selectedArtistPlaylist.value?.id == playlist.id) {
                        _selectedArtistPlaylist.value = playlist.copy(tracks = tracks)
                    }
                    syncLikedAlbum(playlist.copy(tracks = tracks))
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to fetch SoundCloud playlist tracks", e)
                    handleSoundCloudApiError(e)
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

    /**
     * Renews an expired SoundCloud session from the soundcloud.com web session and stores the
     * result. Called by the HTTP layer the moment a request is rejected, which then resends that
     * request, and by [recoverFromAuthFailure].
     */
    private suspend fun renewSoundCloudSession(staleToken: String): Boolean {
        val renewed = SoundCloudSessionRefresher.refresh(
            context = context,
            staleToken = staleToken,
            clientId = settingsRepository.clientId.value
        ) ?: return false
        settingsRepository.saveClientId(renewed.clientId)
        settingsRepository.saveOauthToken(renewed.oauthToken)
        settingsRepository.saveUserId(renewed.userId.toString())
        authRecoveryAttempts = 0
        _isClientIdExpired.value = false
        _needsRelogin.value = false
        return true
    }

    /**
     * Checks the session when the app comes to the foreground, at most every
     * [SESSION_CHECK_INTERVAL_MS]. A token that lapsed while the app was away is renewed here,
     * by the HTTP layer, before anything the listener taps depends on it.
     */
    fun onAppForeground() {
        if (System.currentTimeMillis() - lastLikeBlockAt >= LIKE_RETRY_AFTER_BLOCK_MS) {
            flushPendingSoundCloudLikes()
        }
        val now = System.currentTimeMillis()
        if (now - lastSessionCheckAt < SESSION_CHECK_INTERVAL_MS) return
        val clientIdValue = settingsRepository.clientId.value
        if (clientIdValue.isBlank() || settingsRepository.oauthTokenValue().isBlank()) return
        lastSessionCheckAt = now
        viewModelScope.launch {
            runCatching { service.getMe(clientIdValue) }
                .onFailure { Log.w("MusicViewModel", "Foreground session check failed", it) }
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
        val youTubeId = track.youTubeVideoId
        if (youTubeId != null) {
            rateOnYouTube(youTubeId, like = false)
        } else if (isYandex) {
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
            settingsRepository.setSoundCloudLikePending(track.id, false)
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
                    if (favorite.downloadState == DownloadState.DOWNLOADED && favorite.streamUrl != null &&
                        !playlistsRepository.usesStream(favorite.streamUrl)
                    ) {
                        withContext(Dispatchers.IO) {
                            if (isProgressiveSource(favorite.urn)) {
                                offlineMusicStore.removeProgressive(favorite.streamUrl)
                            } else {
                                offlineMusicStore.removeHls(favorite.streamUrl)
                            }
                        }
                    }
                }
                favoritesRepository.remove(track.id)
                settingsRepository.setSoundCloudLikePending(track.id, false)
                val userIdValue = settingsRepository.userIdValue()
                val youTubeId = track.youTubeVideoId
                if (youTubeId != null) {
                    rateOnYouTube(youTubeId, like = false)
                } else if (track.urn?.startsWith("yandex:track:") == true) {
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
                        val response = service.unlikeTrack(
                            userIdValue,
                            track.id,
                            settingsRepository.clientId.value
                        )
                        if (!response.isSuccessful) {
                            Log.e("MusicViewModel", "Unlike rejected by SoundCloud: ${response.code()}")
                            if (response.code() == 401 || response.code() == 403) {
                                recoverFromAuthFailure()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MusicViewModel", "Failed to unlike track on SoundCloud", e)
                    }
                }
                return@launch
            }

            favoritesRepository.add(track, streamUrl = null)
            favoritesRepository.updateDownloadState(track.id, DownloadState.DOWNLOADING)

            val isYandex = track.urn?.startsWith("yandex:track:") == true
            val youTubeId = track.youTubeVideoId
            if (youTubeId != null) {
                rateOnYouTube(youTubeId, like = true)
            } else if (isYandex) {
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
                // Goes through a soundcloud.com page that may take seconds to load; the download
                // doesn't wait for it.
                viewModelScope.launch {
                    if (likeOnSoundCloud(track.id, askForCaptcha = true)) {
                        // The network lets likes through again; send whatever was held back.
                        flushPendingSoundCloudLikes()
                    }
                }
            }

            downloadQueue.trySend(DownloadRequest(track, isRedownload = false))
        }
    }

    /**
     * Likes [trackId] on SoundCloud. A like that doesn't go through is kept and sent again later
     * (see [flushPendingSoundCloudLikes]), so a track liked from a blocked network still ends up
     * liked on the account.
     *
     * @param askForCaptcha show the captcha when the bot protection asks for one. Only for a like
     *   the listener just made; a background retry shouldn't pop one up out of nowhere.
     * @return whether SoundCloud accepted it.
     */
    private suspend fun likeOnSoundCloud(trackId: Long, askForCaptcha: Boolean): Boolean {
        val userIdValue = settingsRepository.userIdValue()
        if (userIdValue.isBlank() || settingsRepository.oauthTokenValue().isBlank()) return false

        // Sent from a soundcloud.com page, as the website's like button sends it: from a VPN
        // address SoundCloud's bot protection refuses likes from anything but a browser.
        suspend fun send(): SoundCloudWebRequests.Result? = SoundCloudWebRequests.send(
            context = context,
            method = "PUT",
            url = "${SoundCloudApi.BASE_URL}users/$userIdValue/track_likes/$trackId" +
                "?client_id=${settingsRepository.clientId.value}" +
                "&app_version=${SoundCloudApi.APP_VERSION}&app_locale=en",
            oauthToken = settingsRepository.oauthTokenValue()
        )

        var result = send()
        // The page bypasses the HTTP layer that renews an expired session, so renew it here.
        if (result?.status == 401 && renewSoundCloudSession(settingsRepository.oauthTokenValue())) {
            result = send()
        }

        if (result?.isSuccessful == true) {
            settingsRepository.setSoundCloudLikePending(trackId, false)
            return true
        }
        settingsRepository.setSoundCloudLikePending(trackId, true)

        val captchaUrl = result?.captchaUrl
        when {
            result == null -> Log.w("MusicViewModel", "Like of $trackId not sent: soundcloud.com unreachable")

            captchaUrl != null -> {
                // Not an auth problem: renewing the session can't help, and would end in asking
                // the listener to sign in again for nothing.
                lastLikeBlockAt = System.currentTimeMillis()
                Log.w("MusicViewModel", "Like of $trackId blocked by SoundCloud's bot protection: $captchaUrl")
                // `t=bv` is an outright ban, which no captcha lifts.
                if (askForCaptcha && !captchaUrl.contains("t=bv")) {
                    _antiBotCaptchaUrl.value = captchaUrl
                } else if (askForCaptcha) {
                    Toast.makeText(
                        context,
                        "SoundCloud блокирует лайки с этого адреса. Смените сервер VPN — лайк отправится позже.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            else -> Log.e("MusicViewModel", "Like of $trackId rejected by SoundCloud: ${result.status} ${result.body.take(200)}")
        }
        return false
    }

    /**
     * Sends the likes that didn't go through earlier, stopping at the first one that fails.
     * While [pushLikesToSoundCloud] is under way it reports into [likesPushStatus].
     *
     * @param askForCaptcha bring up the captcha if the bot protection refuses — only for a round
     *   the listener started, never for one that runs on its own when the app comes back.
     */
    private fun flushPendingSoundCloudLikes(askForCaptcha: Boolean = false) {
        if (likeFlushJob?.isActive == true) return
        if (settingsRepository.pendingSoundCloudLikes().isEmpty()) {
            finishLikesPush()
            return
        }
        likeFlushJob = viewModelScope.launch {
            val pushing = _likesPushStatus.value.state == LikesPushState.SENDING ||
                _likesPushStatus.value.state == LikesPushState.PAUSED
            if (pushing) {
                _likesPushStatus.value = _likesPushStatus.value.copy(state = LikesPushState.SENDING, message = null)
            }
            var first = true
            for (trackId in settingsRepository.pendingSoundCloudLikes()) {
                if (favoritesRepository.get(trackId)?.isSoundCloudTrack() != true) {
                    settingsRepository.setSoundCloudLikePending(trackId, false)
                    continue
                }
                // A burst of likes is what SoundCloud's rate limit and bot protection look for.
                if (!first) delay(LIKE_SEND_INTERVAL_MS)
                first = false
                // Each refused request makes the bot protection trust this network less, so
                // one refusal ends the round.
                if (!likeOnSoundCloud(trackId, askForCaptcha = askForCaptcha)) {
                    if (pushing) {
                        _likesPushStatus.value = _likesPushStatus.value.copy(
                            state = LikesPushState.PAUSED,
                            message = if (_antiBotCaptchaUrl.value != null) {
                                "SoundCloud просит пройти проверку — после неё отправка продолжится"
                            } else {
                                "SoundCloud не принял лайк. Остальные отправятся позже или по кнопке"
                            }
                        )
                    }
                    return@launch
                }
                Log.d("MusicViewModel", "Sent held-back like of $trackId to SoundCloud")
                if (pushing) {
                    _likesPushStatus.value = _likesPushStatus.value.copy(sent = _likesPushStatus.value.sent + 1)
                }
            }
            finishLikesPush()
        }
    }

    private fun finishLikesPush() {
        val status = _likesPushStatus.value
        if (status.state == LikesPushState.SENDING || status.state == LikesPushState.PAUSED) {
            _likesPushStatus.value = status.copy(state = LikesPushState.COMPLETED, message = null)
        }
    }

    /**
     * Likes on SoundCloud every downloaded SoundCloud track the account doesn't have among its
     * likes — the ones whose like was lost before likes learned to get past the bot protection.
     */
    fun pushLikesToSoundCloud() {
        if (likesPushJob?.isActive == true) return
        likesPushJob = viewModelScope.launch {
            val clientId = settingsRepository.clientId.value
            val userId = settingsRepository.userIdValue()
            if (clientId.isBlank() || userId.isBlank() || settingsRepository.oauthTokenValue().isBlank()) {
                _likesPushStatus.value = LikesPushStatus(
                    state = LikesPushState.FAILED,
                    message = "Войдите в SoundCloud"
                )
                return@launch
            }

            _likesPushStatus.value = LikesPushStatus(state = LikesPushState.CHECKING)
            val likedIds = try {
                fetchSoundCloudLikes(userId, clientId).map { it.id }.toSet()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("MusicViewModel", "Failed to fetch SoundCloud likes to compare", e)
                _likesPushStatus.value = LikesPushStatus(
                    state = LikesPushState.FAILED,
                    message = readableMessage(e)
                )
                return@launch
            }

            favoritesRepository.favorites.value
                .filter { it.isSoundCloudTrack() && it.id !in likedIds }
                .forEach { settingsRepository.setSoundCloudLikePending(it.id, true) }
            // Queued earlier but meanwhile liked some other way.
            settingsRepository.pendingSoundCloudLikes()
                .filter { it in likedIds }
                .forEach { settingsRepository.setSoundCloudLikePending(it, false) }

            val total = settingsRepository.pendingSoundCloudLikes().size
            if (total == 0) {
                _likesPushStatus.value = LikesPushStatus(
                    state = LikesPushState.COMPLETED,
                    message = "Все скачанные треки уже лайкнуты на SoundCloud"
                )
                return@launch
            }

            // A background round would stop on its own snapshot of the queue; start afresh.
            likeFlushJob?.cancelAndJoin()
            lastLikeBlockAt = 0L
            _likesPushStatus.value = LikesPushStatus(state = LikesPushState.SENDING, total = total)
            flushPendingSoundCloudLikes(askForCaptcha = true)
        }
    }

    fun resetLikesPushStatus() {
        if (likesPushJob?.isActive == true || likeFlushJob?.isActive == true) return
        _likesPushStatus.value = LikesPushStatus()
    }

    /** Yandex and imported files have ids of their own, which could name some other SoundCloud track. */
    private fun FavoriteTrack.isSoundCloudTrack(): Boolean =
        id > 0 && !urn.startsWith("yandex:") && !urn.startsWith("local:") && !urn.startsWith("ytmusic:")

    /** @param cookie what the captcha page hands over once solved, a `datadome` cookie. */
    fun onAntiBotCaptchaSolved(cookie: String) {
        Log.d("MusicViewModel", "SoundCloud captcha solved")
        SoundCloudWebRequests.acceptCaptchaCookie(cookie)
        _antiBotCaptchaUrl.value = null
        lastLikeBlockAt = 0L
        flushPendingSoundCloudLikes(askForCaptcha = true)
    }

    fun dismissAntiBotCaptcha() {
        _antiBotCaptchaUrl.value = null
        if (_likesPushStatus.value.state == LikesPushState.PAUSED) {
            _likesPushStatus.value = _likesPushStatus.value.copy(
                message = "Проверка не пройдена. Остальные лайки отправятся позже или по кнопке"
            )
        }
        Toast.makeText(context, "Лайк отправится на SoundCloud позже", Toast.LENGTH_SHORT).show()
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
            val localUrl = localStreamUrl(t.id) ?: resolvedUrls[t.id]
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

    /** See [MusicPlayer.livePositionMs]. */
    fun livePositionMs(): Long = musicPlayer.livePositionMs()

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
                        val localUrl = localStreamUrl(t.id)
                        val fallbackUrl = placeholderStreamUrl(t)
                        t.toQueueTrack(localUrl ?: resolvedUrls[t.id] ?: fallbackUrl)
                    }
                    // Reorder around the playing item so toggling shuffle is silent. Only if the
                    // player refuses (no controller, empty timeline) do we fall back to the old
                    // rebuild, which restarts the track and has to seek back into place.
                    if (!musicPlayer.reorderQueueKeepingCurrent(stubs, newIndex)) {
                        musicPlayer.playQueue(stubs, newIndex)
                        musicPlayer.seekTo(currentPos)
                    }
                }
            }
        }
    }

    /**
     * Keeps a copy of the cover next to the audio. Best effort: a track that downloaded fine is
     * still usable without its artwork, so a failure here only costs a log line.
     */
    private suspend fun cacheArtwork(track: SoundCloudTrack) {
        val source = ArtworkUrls.highRes(track.artworkUrl) ?: return
        val path = withContext(Dispatchers.IO) {
            offlineMusicStore.downloadArtwork(source, track.id)
        }
        if (path != null) favoritesRepository.updateLocalArtwork(track.id, path)
    }

    /**
     * Tells SoundCloud what actually got listened to.
     *
     * The app was read-only against the recommendation engine: it fetched mixes but never fed
     * anything back, so the same selections kept coming round. Listening history is the signal
     * that moves them, alongside likes.
     *
     * A play is only reported once the track has been playing for [PLAY_REPORT_AFTER_MS].
     * `collectLatest` cancels that wait when the track changes, so skipping through a queue
     * reports nothing — otherwise every skipped track would be fed back as something the user
     * chose to hear.
     */
    private fun reportPlaysToSoundCloud() {
        viewModelScope.launch {
            musicPlayer.currentTrackId.collectLatest { trackId ->
                if (trackId == null) return@collectLatest
                delay(PLAY_REPORT_AFTER_MS)
                if (musicPlayer.currentTrackId.value != trackId) return@collectLatest

                val queued = _activeQueue.value.firstOrNull { it.id == trackId }
                // Yandex and YouTube tracks are nothing to do with SoundCloud's history.
                if (queued?.urn?.startsWith("yandex:") == true || queued?.urn?.startsWith("ytmusic:") == true) return@collectLatest
                if (trackId < 0) return@collectLatest

                val urn = queued?.urn?.takeIf { it.startsWith("soundcloud:tracks:") }
                    ?: "soundcloud:tracks:$trackId"
                val clientId = settingsRepository.clientId.value
                if (clientId.isBlank() || settingsRepository.oauthTokenValue().isBlank()) return@collectLatest

                try {
                    val response = service.addToPlayHistory(PlayHistoryRequest(trackUrn = urn), clientId)
                    if (response.isSuccessful) {
                        Log.d("MusicViewModel", "Reported play of $urn to SoundCloud")
                    } else {
                        Log.w("MusicViewModel", "Play history rejected: ${response.code()} for $urn")
                        if (response.code() == 401 || response.code() == 403) recoverFromAuthFailure()
                    }
                } catch (e: Exception) {
                    Log.w("MusicViewModel", "Could not report play of $urn", e)
                }
            }
        }
    }

    /**
     * Fetches covers for tracks downloaded before artwork was cached, once, in the background.
     * Without this an existing library would stay blank in the car until every track was
     * re-downloaded.
     */
    private fun backfillArtwork() {
        viewModelScope.launch {
            val pending = favoritesRepository.downloadedWithoutArtwork()
            if (pending.isEmpty()) return@launch
            for (fav in pending) {
                val source = ArtworkUrls.highRes(fav.artworkUrl) ?: continue
                val path = withContext(Dispatchers.IO) {
                    offlineMusicStore.downloadArtwork(source, fav.id)
                }
                if (path != null) favoritesRepository.updateLocalArtwork(fav.id, path)
            }
        }
    }

    fun deleteDownloadedTrack(track: FavoriteTrack) {
        val streamUrl = track.streamUrl ?: return
        viewModelScope.launch {
            try {
                // A liked album may share this copy (see performPlaylistDownload); it keeps it.
                if (!playlistsRepository.usesStream(streamUrl)) {
                    withContext(Dispatchers.IO) {
                        // Use correct removal method based on track source (#4)
                        if (isProgressiveSource(track.urn)) {
                            offlineMusicStore.removeProgressive(streamUrl)
                        } else {
                            offlineMusicStore.removeHls(streamUrl)
                        }
                        offlineMusicStore.removeArtwork(track.id)
                    }
                }
                favoritesRepository.updateDownloadState(track.id, DownloadState.NONE)
                favoritesRepository.updateStreamUrl(track.id, "")
                favoritesRepository.updateLocalArtwork(track.id, null)
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
                val tempService = SoundCloudApi.createService(oauthTokenProvider = { capturedOauthToken })
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
            _trendingSection.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicPlayer.release()
    }

    // Unified playability check (#41: merged isPlayableMixTrack)
    private fun isPlayableTrack(track: SoundCloudTrack): Boolean {
        if (track.urn?.startsWith("yandex:track:") == true || track.youTubeVideoId != null) {
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
            // Notification and lockscreen credit line: every artist, not just the uploader.
            artist = artists?.mapNotNull { it.username?.takeIf(String::isNotBlank) }
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
                ?: user?.username
                ?: "Unknown Artist",
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
            artist = displayArtist,
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
        artistId = user?.id,
        artists = artists?.takeIf { it.isNotEmpty() }
    )

    // Playlist and Local Import Support
    fun createPlaylist(name: String) {
        playlistsRepository.createPlaylist(name)
    }

    fun deletePlaylist(playlistId: String) {
        playlistsRepository.get(playlistId)?.let { playlist ->
            releaseDownloads(playlistId, playlist.tracks)
        }
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
        playlistsRepository.get(playlistId)?.tracks?.filter { it.id == trackId }?.let { removed ->
            releaseDownloads(playlistId, removed)
        }
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

    /**
     * Where a track can be played from on the device: its copy in "Скачанное" first, otherwise one
     * saved through a liked album. Albums download on their own, so every play path has to look
     * there too, or a saved album would still stream when played from its artist page.
     */
    private fun localStreamUrl(trackId: Long): String? =
        favoritesRepository.get(trackId)?.streamUrl ?: playlistsRepository.downloadedStreamUrl(trackId)

    /**
     * The heart on an album or set: saves it as a playlist in the library, next to "Скачанное",
     * or removes that copy (and whatever was downloaded for it) again.
     */
    fun toggleAlbumLike(album: SoundCloudPlaylist, artistName: String?) {
        val existing = playlistsRepository.findBySource(album.sourceKey())
        if (existing != null) {
            deletePlaylist(existing.id)
            return
        }
        playlistsRepository.createFromSource(
            sourceKey = album.sourceKey(),
            name = album.title ?: "Альбом",
            artist = artistName?.takeIf { it.isNotBlank() } ?: album.user?.username,
            artworkUrl = ArtworkUrls.highRes(album.displayArtworkUrl),
            tracks = album.knownTracks.filterNot { it.title.isNullOrBlank() }.map { it.toFavoriteTrack() }
        )
    }

    /** Brings a liked album's saved track list up to date once its full list has loaded. */
    private fun syncLikedAlbum(album: SoundCloudPlaylist) {
        val saved = playlistsRepository.findBySource(album.sourceKey()) ?: return
        val fresh = album.knownTracks.filterNot { it.title.isNullOrBlank() }
        if (fresh.isEmpty()) return
        playlistsRepository.mergeTracks(saved.id, fresh.map { it.toFavoriteTrack() })
    }

    /**
     * Saves every track of a playlist on the device without adding it to "Скачанное". The files
     * belong to the playlist: they wait in the same one-at-a-time queue as other downloads, and
     * deleting the playlist deletes them.
     */
    fun downloadPlaylist(playlistId: String) {
        val playlist = playlistsRepository.get(playlistId) ?: return
        playlist.tracks
            .filter { it.downloadState != DownloadState.DOWNLOADED && it.downloadState != DownloadState.DOWNLOADING }
            // Imported files are already on the device.
            .filterNot { it.urn.startsWith("local:") }
            .forEach { track ->
                playlistsRepository.updateTrackDownload(playlistId, track.id, DownloadState.DOWNLOADING)
                downloadQueue.trySend(
                    DownloadRequest(track.toSoundCloudTrack(), isRedownload = false, playlistId = playlistId)
                )
            }
    }

    /** Deletes the files saved for [tracks] of a playlist, unless something else still plays them. */
    private fun releaseDownloads(playlistId: String, tracks: List<FavoriteTrack>) {
        val owned = tracks.filter {
            it.downloadState == DownloadState.DOWNLOADED && !it.streamUrl.isNullOrBlank() && !it.urn.startsWith("local:")
        }
        if (owned.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            owned.forEach { track ->
                val url = track.streamUrl ?: return@forEach
                val sharedWithDownloads = favoritesRepository.favorites.value.any { it.streamUrl == url }
                if (sharedWithDownloads || playlistsRepository.usesStream(url, exceptPlaylistId = playlistId)) {
                    return@forEach
                }
                if (isProgressiveSource(track.urn)) {
                    offlineMusicStore.removeProgressive(url)
                } else {
                    offlineMusicStore.removeHls(url)
                }
                if (favoritesRepository.get(track.id) == null) offlineMusicStore.removeArtwork(track.id)
            }
        }
    }

    /**
     * One track of a playlist's "download all". Mirrors [performDownload] but records the result
     * on the playlist, so "Скачанное" and the SoundCloud / Yandex likes stay as they are.
     */
    private suspend fun performPlaylistDownload(track: SoundCloudTrack, playlistId: String): Boolean {
        fun stillWanted() = playlistsRepository.get(playlistId)?.tracks?.any { it.id == track.id } == true
        if (!stillWanted()) return false

        // Already in "Скачанное": share that copy rather than fetching the same audio twice.
        favoritesRepository.get(track.id)
            ?.takeIf { it.downloadState == DownloadState.DOWNLOADED && !it.streamUrl.isNullOrBlank() }
            ?.let { saved ->
                playlistsRepository.updateTrackDownload(
                    playlistId, track.id, DownloadState.DOWNLOADED, saved.streamUrl, saved.localArtworkPath
                )
                return true
            }

        val isYandex = track.urn?.startsWith("yandex:track:") == true
        val youTubeId = track.youTubeVideoId
        try {
            val stored: String? = if (youTubeId != null) {
                withContext(Dispatchers.IO) { YouTubeStreams.resolve(context, youTubeId, settingsRepository.ytMusicAuth()) }?.let { audio ->
                    val path = withContext(Dispatchers.IO) {
                        offlineMusicStore.downloadProgressive(audio.url, "pl_yt_$youTubeId", extension = "m4a", chunked = true, userAgent = audio.userAgent) { progress ->
                            updateDownloadProgress(track.id, progress)
                        }
                    }
                    if (path != null && !isCompleteDownload(path, track.duration)) {
                        withContext(Dispatchers.IO) { java.io.File(path).delete() }
                        null
                    } else {
                        path
                    }
                }
            } else if (isYandex) {
                val yandexId = track.urn?.substringAfter("yandex:track:")?.substringBefore(":").orEmpty()
                val token = settingsRepository.yandexTokenValue()
                val streamUrl = if (token.isNotBlank() && yandexId.isNotBlank()) {
                    YandexMusicApi.resolveTrackStream(yandexId, token)
                } else {
                    null
                }
                streamUrl?.let { url ->
                    // A file name of its own, so it never collides with the same track saved
                    // through "Скачанное".
                    val path = withContext(Dispatchers.IO) {
                        offlineMusicStore.downloadProgressive(url, "pl_$yandexId") { progress ->
                            updateDownloadProgress(track.id, progress)
                        }
                    }
                    if (path != null && !isCompleteDownload(path, track.duration)) {
                        withContext(Dispatchers.IO) { java.io.File(path).delete() }
                        null
                    } else {
                        path
                    }
                }
            } else {
                val clientId = settingsRepository.clientId.value
                // Saved entries carry no stream metadata; the full track has it.
                val full = if (track.media == null || track.trackAuthorization == null) {
                    service.getTrack(track.id, clientId)
                } else {
                    track
                }
                val streamUrl = if (clientId.isNotBlank()) playbackResolver.resolve(full, clientId) else null
                streamUrl?.also { url ->
                    withContext(Dispatchers.IO) {
                        offlineMusicStore.downloadHls(url) { progress ->
                            updateDownloadProgress(track.id, progress / 100f)
                        }
                    }
                }
            }

            if (stored == null) {
                playlistsRepository.updateTrackDownload(playlistId, track.id, DownloadState.FAILED)
                return false
            }
            if (!stillWanted()) {
                // Removed from the playlist (or the playlist deleted) while it downloaded.
                withContext(Dispatchers.IO) {
                    if (isProgressiveSource(track.urn)) offlineMusicStore.removeProgressive(stored) else offlineMusicStore.removeHls(stored)
                }
                return false
            }
            val artwork = ArtworkUrls.highRes(track.artworkUrl)
                ?.takeUnless { it.startsWith("file://") }
                ?.let { url -> withContext(Dispatchers.IO) { offlineMusicStore.downloadArtwork(url, track.id) } }
            playlistsRepository.updateTrackDownload(playlistId, track.id, DownloadState.DOWNLOADED, stored, artwork)
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Error downloading playlist track ${track.id}", e)
            handleSoundCloudApiError(e)
            playlistsRepository.updateTrackDownload(playlistId, track.id, DownloadState.FAILED)
            return false
        } finally {
            _downloadProgress.value = _downloadProgress.value - track.id
        }
    }

    /** The same completeness check [performDownload] applies to Yandex files. */
    private fun isCompleteDownload(path: String, expectedMs: Long): Boolean {
        val actual = getMp3Duration(path)
        return if (expectedMs > 0) {
            kotlin.math.abs(actual - expectedMs) <= 8000L || actual.toFloat() / expectedMs.toFloat() >= 0.95f
        } else {
            actual > 10_000L
        }
    }

    /**
     * "Перемешать": switches shuffle on (it stays on, as if pressed in the player) and starts
     * [queue] from a random track; the play paths already shuffle the rest once the flag is set.
     */
    fun playShuffled(queue: List<SoundCloudTrack>, fromMix: Boolean = false) {
        val start = queue.randomOrNull() ?: return
        if (!musicPlayer.shuffleEnabled.value) musicPlayer.toggleShuffle()
        if (fromMix) playMixTrack(start) else playQueuedTrack(start, queue)
    }

    /** Same, for a saved playlist, whose tracks may already be on the device. */
    fun playPlaylistShuffled(playlist: Playlist) {
        val start = playlist.tracks.randomOrNull() ?: return
        if (!musicPlayer.shuffleEnabled.value) musicPlayer.toggleShuffle()
        playPlaylistTrack(playlist, start)
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

    private companion object {
        const val AUTH_RECOVERY_COOLDOWN_MS = 30_000L
        const val MAX_AUTH_RECOVERY_ATTEMPTS = 2
        const val SESSION_CHECK_INTERVAL_MS = 15 * 60_000L

        // How long held-back likes wait, after the bot protection refused one, before coming
        // back to the app retries them.
        const val LIKE_RETRY_AFTER_BLOCK_MS = 30 * 60_000L

        // Between the likes of one round of held-back ones.
        const val LIKE_SEND_INTERVAL_MS = 2_000L

        // Long enough that skipping through tracks reports nothing, short enough that a track
        // left playing counts even if the listener moves on part way through.
        const val PLAY_REPORT_AFTER_MS = 25_000L

        const val SEARCH_PAGE_SIZE = 30

        // A found video URL (YouTube's) holds for hours; an absent video stays absent a while.
        const val VIDEO_LOOKUP_TTL_MS = 60 * 60 * 1000L
    }

    /** Confirms a credential pair actually works before we treat the session as healthy. */
    private suspend fun credentialsWork(clientId: String, oauthToken: String): Boolean =
        runCatching {
            SoundCloudApi.createService(oauthTokenProvider = { oauthToken }).getMe(clientId)
            true
        }.getOrDefault(false)

    /** Manual "обновить автоматически" — clears the backoff and retries immediately. */
    fun tryAutoRefreshClientId() {
        authRecoveryAttempts = 0
        lastAuthRecoveryAt = 0L
        _needsRelogin.value = false
        recoverFromAuthFailure(force = true)
    }

    /**
     * Single escalating recovery path for 401/403.
     *
     * The previous version re-fetched an anonymous client_id and then immediately called
     * `refreshMixesAndStations()`. Because `loadMixes()` launches its own coroutine, the
     * refresh job had already completed by the time the retried request failed again, so
     * the in-flight guard never held and every failure started another round — an endless
     * loop. Worse, a scraped anonymous client_id cannot fix an expired OAuth token, which
     * is what a 401 on an authorised endpoint almost always means, so the loop could never
     * converge.
     *
     * Now each step is verified before being accepted, attempts are capped, a cooldown
     * separates rounds, and exhausting the ladder puts the app into an explicit
     * "sign in again" state instead of spinning.
     */
    private fun recoverFromAuthFailure(force: Boolean = false) {
        if (authRecoveryJob?.isActive == true) return

        val now = System.currentTimeMillis()
        if (!force) {
            if (now - lastAuthRecoveryAt < AUTH_RECOVERY_COOLDOWN_MS) return
            if (authRecoveryAttempts >= MAX_AUTH_RECOVERY_ATTEMPTS) {
                _needsRelogin.value = true
                return
            }
        }
        lastAuthRecoveryAt = now
        authRecoveryAttempts++

        authRecoveryJob = viewModelScope.launch {
            _isClientIdExpired.value = true
            _isLoading.value = true
            try {
                val token = settingsRepository.oauthTokenValue()

                // 1. Only helps when the client_id itself is what went stale.
                val freshClientId = SoundCloudApi.fetchSoundCloudClientId()
                if (freshClientId != null && credentialsWork(freshClientId, token)) {
                    settingsRepository.saveClientId(freshClientId)
                    onAuthRecovered()
                    return@launch
                }

                // 2. Otherwise the OAuth token is dead. Reuse the still-valid
                //    soundcloud.com web session to pick up a fresh one.
                if (renewSoundCloudSession(token)) {
                    onAuthRecovered()
                    return@launch
                }

                // 3. Nothing automatic is left — ask, don't spin.
                _needsRelogin.value = true
                _errorMessage.value =
                    "Сессия SoundCloud истекла. Войдите в аккаунт заново."
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun onAuthRecovered() {
        authRecoveryAttempts = 0
        _isClientIdExpired.value = false
        _needsRelogin.value = false
        _errorMessage.value = null
        Log.d("MusicViewModel", "SoundCloud auth recovered")
        refreshMixesAndStations()
    }

    private fun handleSoundCloudApiError(e: Throwable) {
        if (e is HttpException && (e.code() == 401 || e.code() == 403)) {
            recoverFromAuthFailure()
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
                val hasLocal = localStreamUrl(startTrack.id) != null
                if (!hasLocal && resolvedUrls[startTrack.id] == null && serviceStreamUrl(startTrack) == null) {
                    val clientIdValue = settingsRepository.clientId.value
                    val resolvedUrl = playbackResolver.resolve(startTrack, clientIdValue) ?: ""
                    if (resolvedUrl.isNotEmpty()) {
                        resolvedUrls[startTrack.id] = resolvedUrl
                    }
                }
            }

            val stubs = queueToPlay.map { t ->
                val localUrl = localStreamUrl(t.id)
                val fallbackUrl = placeholderStreamUrl(t)
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

    /** Every track the SoundCloud account has liked, newest first. */
    private suspend fun fetchSoundCloudLikes(userId: String, clientId: String): List<SoundCloudTrack> {
        val tracks = mutableListOf<SoundCloudTrack>()
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
            tracks.addAll(response.collection.mapNotNull { it.track })

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
        return tracks
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
                    allTracks.addAll(fetchSoundCloudLikes(userId, clientId))
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

                    if (userIdValue.isNotBlank() && token.isNotBlank() && clientId.isNotBlank() &&
                        track.isSoundCloudTrack()
                    ) {
                        settingsRepository.setSoundCloudLikePending(track.id, true)
                    }
                }
            }
            // Sent as one round that stops at the first refusal, rather than a request per track
            // into a network the bot protection is blocking.
            flushPendingSoundCloudLikes()
        }
    }

    private var yandexSearchJob: Job? = null

    fun onYandexSearchQueryChange(query: String) {
        _yandexSearchQuery.value = query
        yandexSearchJob?.cancel()
        _yandexHasMore.value = false
        _yandexLoadingMore.value = false
        yandexSearchPage = 0

        if (query.trim().isEmpty()) {
            _yandexTracks.value = emptyList()
            _yandexSearchAlbums.value = emptyList()
            _yandexSearchPlaylists.value = emptyList()
            _yandexSearchArtists.value = emptyList()
            _yandexError.value = null
            _yandexLoading.value = false
            return
        }

        yandexSearchJob = viewModelScope.launch {
            delay(500)
            _yandexLoading.value = true
            _yandexError.value = null
            try {
                // One request for everything; further pages of tracks come from `type=track`,
                // which pages the same twenty at a time.
                val result = yandexService.searchAll(query).result
                val page = result?.tracks
                _yandexTracks.value = page?.results.orEmpty().map { it.toSoundCloudTrack() }.distinctBy { it.id }
                _yandexHasMore.value = page.hasMoreAfter(0)
                _yandexSearchAlbums.value = result?.albums?.results.orEmpty()
                    .map { it.toSearchAlbum() }
                    .filter { it.trackCount > 0 }
                    .distinctBy { it.id }
                _yandexSearchPlaylists.value = result?.playlists?.results.orEmpty()
                    .mapNotNull { it.toSearchPlaylist() }
                    .filter { it.trackCount > 0 }
                    .distinctBy { it.id }
                _yandexSearchArtists.value = result?.artists?.results.orEmpty()
                    .mapNotNull { it.toArtistUser() }
                    .distinctBy { it.permalinkUrl }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to search Yandex tracks", e)
                _yandexError.value = "Ошибка поиска: ${readableMessage(e)}"
            } finally {
                _yandexLoading.value = false
            }
        }
    }

    fun loadMoreYandexSearchTracks() {
        val query = _yandexSearchQuery.value
        if (query.isBlank() || !_yandexHasMore.value || _yandexLoading.value || _yandexLoadingMore.value) return

        // Shares the search job so that typing a new query cancels a page still in flight.
        yandexSearchJob = viewModelScope.launch {
            _yandexLoadingMore.value = true
            try {
                val nextPage = yandexSearchPage + 1
                val page = yandexService.searchTracks(query, page = nextPage).result?.tracks
                _yandexTracks.value = (_yandexTracks.value + page?.results.orEmpty().map { it.toSoundCloudTrack() })
                    .distinctBy { it.id }
                yandexSearchPage = nextPage
                _yandexHasMore.value = page.hasMoreAfter(nextPage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to load more Yandex tracks", e)
                _yandexError.value = "Ошибка поиска: ${readableMessage(e)}"
            } finally {
                _yandexLoadingMore.value = false
            }
        }
    }

    private fun com.example.myapplication.data.YandexSearchTracks?.hasMoreAfter(page: Int): Boolean {
        if (this == null || results.isNullOrEmpty()) return false
        val totalCount = total ?: return true
        val pageSize = perPage?.takeIf { it > 0 } ?: results.size
        return (page + 1) * pageSize < totalCount
    }



    fun playQueuedTrack(
        track: SoundCloudTrack,
        customQueue: List<SoundCloudTrack>? = null,
        fromQueueManager: Boolean = false
    ) {
        viewModelScope.launch {
            _errorMessage.value = null
            val isYandexTrack = track.urn?.startsWith("yandex:track:") == true
            // Without a list of its own, a track plays among the search results of its service.
            val defaultQueue = when {
                isYandexTrack -> _yandexTracks.value
                track.youTubeVideoId != null -> _ytSearch.value.page?.tracks.orEmpty()
                else -> _tracks.value
            }
            val qTracks = customQueue ?: defaultQueue
            val listed = if (customQueue != null) qTracks else qTracks.filter { isPlayableTrack(it) }
            // A track missing from that list plays on its own. Falling back to the list's first
            // entry played a different track, from another service even.
            if (listed.isEmpty()) {
                playTrack(track)
                return@launch
            }
            val playableTracks = if (listed.any { it.id == track.id }) listed else listOf(track)
            // This queue came straight from a listing, so it carries full credits — a good moment
            // to repair saved tracks that were downloaded before every artist was persisted.
            favoritesRepository.syncCredits(playableTracks)
            _isLoading.value = true
            try {
                val startIndex = playableTracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                val isFromQueueManager = fromQueueManager
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
                    val serviceUrl = serviceStreamUrl(startTrack)
                    if (serviceUrl != null) {
                        resolvedUrls[startTrack.id] = serviceUrl
                    } else {
                        val clientIdValue = settingsRepository.clientId.value
                        val resolvedUrl = localStreamUrl(startTrack.id)
                            ?: playbackResolver.resolve(startTrack, clientIdValue)
                            ?: ""
                        if (resolvedUrl.isNotEmpty()) {
                            resolvedUrls[startTrack.id] = resolvedUrl
                        }
                    }
                }
                
                val stubs = queueToPlay.map { t ->
                    val localUrl = localStreamUrl(t.id)
                    val fallbackUrl = placeholderStreamUrl(t)
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
                        if (isProgressiveSource(favorite.urn)) {
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
        val youTubeId = track.youTubeVideoId
        val clientId = settingsRepository.clientId.value

        try {
            val youTubeAudio = youTubeId?.let { id ->
                withContext(Dispatchers.IO) { YouTubeStreams.resolve(context, id, settingsRepository.ytMusicAuth()) }
            }
            val streamUrl = if (youTubeId != null) {
                youTubeAudio?.url
            } else if (isYandex) {
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
                if (youTubeId != null) {
                    _errorMessage.value = "YouTube не отдал звук этого трека."
                } else if (isYandex) {
                    _errorMessage.value = "Укажите рабочий токен Яндекс Музыки в настройках."
                } else if (clientId.isBlank()) {
                    _errorMessage.value = "Укажите SoundCloud client_id в настройках для загрузки трека"
                } else {
                    _errorMessage.value = "Поток для загрузки не нашёлся."
                }
                return false
            }

            if (isYandex || youTubeId != null) {
                val yandexTrackId = track.urn?.substringAfter("yandex:track:")?.substringBefore(":") ?: ""
                val localPath = withContext(Dispatchers.IO) {
                    if (youTubeId != null) {
                        // YouTube's audio is AAC in MP4, and comes down in pieces (see downloadProgressive).
                        offlineMusicStore.downloadProgressive(streamUrl, "yt_$youTubeId", extension = "m4a", chunked = true, userAgent = youTubeAudio?.userAgent) { progress ->
                            updateDownloadProgress(track.id, progress)
                        }
                    } else {
                        offlineMusicStore.downloadProgressive(streamUrl, yandexTrackId) { progress ->
                            updateDownloadProgress(track.id, progress)
                        }
                    }
                }
                if (localPath != null) {
                    val actualDuration = getMp3Duration(localPath)
                    var expectedDuration = track.duration
                    if (expectedDuration <= 0L && isYandex) {
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
                            cacheArtwork(track)
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
                    cacheArtwork(track)
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

    /**
     * A moving picture for [track]. Yandex: the track's music video, else its videoshot (a
     * vertical loop Yandex made to play behind the player). YouTube Music: the song's music
     * video. A Yandex track without either borrows the music video from YouTube, when signed in
     * there — its picture comes through yt-dlp, which needs the session on a VPN.
     */
    private suspend fun findTrackVideo(track: SoundCloudTrack): TrackVideo? {
        val urn = track.urn.orEmpty()
        val youTubeId = track.youTubeVideoId
        return when {
            urn.startsWith("yandex:track:") -> yandexVideo(track) ?: youTubeVideo(track, null)
            youTubeId != null -> youTubeVideo(track, youTubeId)
            else -> null
        }
    }

    /**
     * Yandex's own: the track's videoshot, a vertical loop made to play behind the player, else
     * the ten-second loop of its music video that Yandex's player shows in the cover's place.
     */
    private suspend fun yandexVideo(track: SoundCloudTrack): TrackVideo? {
        val trackId = track.urn.orEmpty().removePrefix("yandex:track:").substringBefore(':')
        val details = yandexService.getTracksDetails(trackId).result.orEmpty().firstOrNull() ?: return null
        val clips = details.artists.orEmpty().firstOrNull()?.id?.let { artistId ->
            try {
                com.example.myapplication.data.yandexClipsIn(yandexService.getArtistClips(artistId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Yandex clips of artist $artistId: $e")
                emptyList()
            }
        }.orEmpty()
        // The clips block doesn't name their tracks; a clip is this track's when the titles agree.
        val wanted = comparableTitle(track.title)
        val clip = clips.firstOrNull { clip ->
            val title = comparableTitle(clip.title)
            wanted.isNotEmpty() && title.isNotEmpty() && (title == wanted || title.startsWith("$wanted "))
        }?.takeIf { !it.cover?.videoUrl.isNullOrBlank() }
        Log.d(
            "MusicViewModel",
            "Yandex $trackId \"${track.title}\": clips of the artist ${clips.map { it.title }}, this track's: ${clip?.id}, " +
                "videoshot: ${details.backgroundVideoUri != null}"
        )
        // The videoshot first: made for the player, it fills it; the clip's loop only the cover.
        details.backgroundVideoUri?.takeIf { it.isNotBlank() }
            ?.let { return TrackVideo(track.id, it, loop = true, vertical = true) }
        return clip?.let { TrackVideo(track.id, it.cover!!.videoUrl!!, loop = true, vertical = false) }
    }

    /** Lower case, letters and digits only. */
    private fun comparableTitle(title: String?): String =
        title.orEmpty().lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /**
     * The track's music video on YouTube, played in step with the track. A video found by search
     * is lined up by its sound ([ClipAligner]); one that can't be isn't shown at all.
     */
    private suspend fun youTubeVideo(track: SoundCloudTrack, videoId: String?): TrackVideo? {
        val auth = settingsRepository.ytMusicAuth() ?: return null
        val video = ytMusic.musicVideo(
            videoId = videoId,
            title = track.title.orEmpty(),
            artist = track.artists?.firstOrNull()?.username ?: track.user?.username,
            durationMs = track.duration
        )
        Log.d(
            "MusicViewModel",
            "YouTube video for ${track.urn}: ${video?.videoId}, " +
                (if (video?.paired == true) "paired, ${video.segments.size} segments" else "by search")
        )
        if (video == null) return null
        val workDir = java.io.File(context.cacheDir, "clip-align")
        return coroutineScope {
            // The track's own sound doesn't depend on the video: it is fetched and decoded while
            // yt-dlp looks for the video's.
            val trackOnsets = if (video.paired && video.segments.isNotEmpty()) {
                null
            } else {
                async { trackOnsets(track, videoId, auth, workDir) }
            }
            val stream = YouTubeStreams.resolveVideo(context, video.videoId, auth) ?: return@coroutineScope null
            if (_currentPlayingTrack.value?.id == track.id && trackOnsets != null) {
                _pendingTrackVideo.value = TrackVideo(
                    track.id, stream.url, loop = false, vertical = false, userAgent = stream.userAgent, ready = false
                )
            }
            val segments = if (trackOnsets == null) video.segments else {
                val videoOnsets = stream.audioUrl?.let { sound ->
                    ClipAligner.onsetsOf(ClipAligner.AudioSource(sound, mapOf("User-Agent" to stream.userAgent)), workDir)
                }
                val ownOnsets = trackOnsets.await()
                val aligned = if (videoOnsets != null && ownOnsets != null) {
                    withContext(Dispatchers.Default) { ClipAligner.align(ownOnsets, videoOnsets) }
                } else {
                    null
                }
                aligned ?: run {
                    Log.d("MusicViewModel", "The video ${video.videoId} doesn't line up with ${track.urn}")
                    return@coroutineScope null
                }
            }
            TrackVideo(track.id, stream.url, loop = false, vertical = false, userAgent = stream.userAgent, segments = segments)
        }
    }

    // Tracks' sound, decoded for lining videos up, kept for a replay or a reopened player.
    private val onsetCache = android.util.LruCache<Long, FloatArray>(8)

    private suspend fun trackOnsets(
        track: SoundCloudTrack,
        videoId: String?,
        auth: com.example.myapplication.data.YtAuth,
        workDir: java.io.File
    ): FloatArray? {
        onsetCache.get(track.id)?.let { return it }
        val source = trackSound(track, videoId, auth) ?: return null
        return ClipAligner.onsetsOf(source, workDir)?.also { onsetCache.put(track.id, it) }
    }

    /** Where to read the track's own sound from: its file, when it is downloaded. */
    private suspend fun trackSound(track: SoundCloudTrack, videoId: String?, auth: com.example.myapplication.data.YtAuth): ClipAligner.AudioSource? {
        favoritesRepository.get(track.id)
            ?.takeIf { it.downloadState == DownloadState.DOWNLOADED }
            ?.streamUrl?.takeIf { it.startsWith("/") && java.io.File(it).exists() }
            ?.let { return ClipAligner.AudioSource(it) }
        if (videoId != null) {
            val stream = YouTubeStreams.resolve(context, videoId, auth) ?: return null
            return ClipAligner.AudioSource(stream.url, mapOf("User-Agent" to stream.userAgent))
        }
        val yandexId = track.urn.orEmpty().removePrefix("yandex:track:").substringBefore(':')
        val token = settingsRepository.yandexTokenValue().takeIf { it.isNotBlank() } ?: return null
        return YandexMusicApi.resolveTrackStream(yandexId, token, lightest = true)?.let { ClipAligner.AudioSource(it) }
    }

    fun loadAllArtistTracks(artistId: String, isYandex: Boolean) {
        if (_currentArtist.value?.permalinkUrl?.startsWith(YT_ARTIST_REF) == true) {
            val songs = ytArtistSongs ?: return
            viewModelScope.launch {
                _artistLoading.value = true
                try {
                    val tracks = ytMusic.setTracks(songs)
                    if (tracks.isNotEmpty()) _currentArtistTracks.value = tracks
                    _isAllArtistTracksLoaded.value = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "All songs of a YouTube Music artist failed", e)
                    _artistError.value = "Ошибка: ${readableMessage(e)}"
                } finally {
                    _artistLoading.value = false
                }
            }
            return
        }
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
