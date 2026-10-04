package com.example.myapplication.ui

import com.example.myapplication.i18n.tr
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
import com.example.myapplication.data.YT_TRACK_URN
import com.example.myapplication.data.YandexRotorEvent
import com.example.myapplication.data.YandexRotorFeedback
import com.example.myapplication.data.YandexRotorItem
import com.example.myapplication.data.YandexRotorQueueRequest
import com.example.myapplication.data.YandexRotorSessionRequest
import com.example.myapplication.data.YtAuth
import com.example.myapplication.data.YtShelf
import com.example.myapplication.data.isProgressiveSource
import com.example.myapplication.data.liveVideoId
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
import com.example.myapplication.data.SharedLink
import com.example.myapplication.data.SharedLinks
import com.example.myapplication.data.social.ShowcaseItem
import com.example.myapplication.data.social.ShowcaseSlot
import com.example.myapplication.data.social.Social
import com.example.myapplication.data.social.socialMessage
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.StateFlow
import com.example.myapplication.together.TogetherNotice
import com.example.myapplication.together.TogetherServices
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
    YTM_SET_DETAIL,
    HISTORY,
    // The account: one's own profile (or signing in), the friends, and someone's profile.
    PROFILE,
    FRIENDS,
    PERSON
}

/** Where search looks. YouTube Music and Yandex only once they are connected. */
enum class SearchSource { SOUNDCLOUD, YANDEX, YOUTUBE }

/** Whether the account follows the artist on screen; [busy] while the change is on its way. */
data class ArtistFollow(val following: Boolean, val busy: Boolean = false)

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
    /** What was listened to; see [ListeningHistory]. */
    private val listeningHistory = com.example.myapplication.data.ListeningHistory(context.applicationContext, viewModelScope)
    val history = listeningHistory.tracks

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
    private val lrcLib = com.example.myapplication.data.LrcLibLyrics(context)

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

    // Called from the download threads, once for every 8 KB read: each call used to launch a
    // coroutine and rebuild the screens, hundreds of times a second on a fast connection. Now the
    // map changes only once a track's progress moves on by a thousandth — under a pixel of any
    // progress bar — and in place, with no coroutine.
    fun updateDownloadProgress(trackId: Long, progress: Float) {
        _downloadProgress.update { current ->
            val shown = current[trackId]
            if (shown != null && (shown * 1000f).toInt() == (progress * 1000f).toInt()) current
            else current + (trackId to progress)
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
                val page = if (settingsRepository.ytWebSearch.value) {
                    com.example.myapplication.data.YouTubeWeb.search(query.trim(), settingsRepository.ytMusicAuth())
                } else {
                    ytMusic.search(query.trim())
                }
                _ytSearch.value = _ytSearch.value.copy(page = page, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "YouTube Music search failed", e)
                _ytSearch.value = _ytSearch.value.copy(
                    page = null,
                    loading = false,
                    error = tr("Ошибка поиска: %s", readableMessage(e))
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
    // An album opened on the artist's page, its tracks on the way: apart from the page's own
    // loading, so the page under the album keeps showing what it has.
    private val _artistAlbumLoading = MutableStateFlow(false)
    val artistAlbumLoading = _artistAlbumLoading.asStateFlow()

    private val _artistError = MutableStateFlow<String?>(null)
    val artistError = _artistError.asStateFlow()

    // Following the artist on screen, in its own service; null: no account there to follow with.
    private val _artistFollow = MutableStateFlow<ArtistFollow?>(null)
    val artistFollow = _artistFollow.asStateFlow()
    private var artistFollowJob: Job? = null

    // A YouTube artist's broadcasts going on now, and every row of their page.
    private val _artistLives = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val artistLives = _artistLives.asStateFlow()
    private val _artistShelves = MutableStateFlow<List<YtShelf>>(emptyList())
    val artistShelves = _artistShelves.asStateFlow()

    // The artist's tracks are a YouTube channel's videos: YouTube Music had nothing of it.
    private val _artistTracksAreVideos = MutableStateFlow(false)
    val artistTracksAreVideos = _artistTracksAreVideos.asStateFlow()
    // Where the rest of such a channel's videos are, for "Все".
    private var ytChannelContinuation: String? = null

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
    // Loading, or held for the others listening together to load it too: the same wait.
    val isPlaybackBuffering: StateFlow<Boolean> by lazy {
        combine(musicPlayer.isBuffering, together.waiting) { loading, waiting -> loading || waiting }
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    }
    // The track shown playing: the next one already while a crossfade brings it in.
    val currentTrackId = musicPlayer.shownTrackId
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
    // Where the queue playing was started from, as the carousels' cards name it, to light the one
    // it came from: "downloads", "history", "playlist-<id>" (one of the app's), "set-<id>" (an
    // album or playlist of a service), "mix-<id>". Null for a lone track, a search's results, a
    // radio.
    private val _playingFrom = MutableStateFlow<String?>(null)
    val playingFrom = _playingFrom.asStateFlow()

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

    // Videos of downloaded tracks, for playing offline; see [downloadExtras].
    private val offlineVideos = com.example.myapplication.data.OfflineVideoStore(context)
    private val extrasQueue = kotlinx.coroutines.channels.Channel<SoundCloudTrack>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val extrasQueued = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    // Tracks looked up lately, with or without a video, so reopening the player doesn't ask again.
    private val videoLookups = java.util.concurrent.ConcurrentHashMap<Long, Pair<TrackVideo?, Long>>()

    // Lookups under way. Not tied to the track on screen: skipping to a track whose video is
    // still being found picks that work up instead of starting it over.
    private val videoLookupsInFlight = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Deferred<Pair<TrackVideo?, Long>>>()

    private fun freshVideoLookup(trackId: Long): Pair<TrackVideo?, Long>? =
        videoLookups[trackId]?.takeIf { System.currentTimeMillis() - it.second < VIDEO_LOOKUP_TTL_MS }

    // Tracks whose video YouTube didn't hand out this time — yt-dlp out of time on a slow phone,
    // the network gone — rather than found to have none: looked up again after a little while.
    private val videoLookupMissed = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    private fun videoLookup(track: SoundCloudTrack): kotlinx.coroutines.Deferred<Pair<TrackVideo?, Long>> =
        videoLookupsInFlight[track.id] ?: viewModelScope.async(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            var missed = false
            val video = try {
                findTrackVideo(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "No video for ${track.urn}: $e")
                missed = true
                null
            }
            missed = videoLookupMissed.remove(track.id) || missed
            // A miss is kept as if looked up long ago, so that it goes stale in a couple of minutes.
            val at = System.currentTimeMillis() - if (missed && video == null) VIDEO_LOOKUP_TTL_MS - VIDEO_MISS_RETRY_MS else 0L
            (video to at).also {
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
        // A Yandex radio's queue is the radio's: what comes next is what it gave, in its order
        // (the queue sheet offers no dragging there either).
        if (yandexRadio != null) return
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

        // Downloaded tracks get their video and lyrics too, one at a time in the background —
        // those downloaded before this existed as well as new ones.
        viewModelScope.launch(Dispatchers.IO) {
            for (track in extrasQueue) {
                try {
                    downloadExtras(track)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("MusicViewModel", "Extras of ${track.urn} failed: $e")
                } finally {
                    extrasQueued.remove(track.id)
                }
            }
        }
        viewModelScope.launch {
            combine(favorites, playlistsRepository.playlists) { saved, lists ->
                (saved + lists.flatMap { it.tracks })
                    .filter { it.downloadState == DownloadState.DOWNLOADED }
                    .distinctBy { it.id }
            }.collectLatest { downloaded ->
                // Let a burst of changes (a playlist downloading) settle first.
                delay(5_000)
                val onDevice = downloaded.map { it.id }.toSet()
                withContext(Dispatchers.IO) {
                    // A track's video goes with the track.
                    (offlineVideos.trackIds() - onDevice).forEach(offlineVideos::remove)
                    downloaded
                        .filter { it.urn.startsWith("yandex:track:") || it.urn.startsWith(YT_TRACK_URN) }
                        .filterNot { offlineVideos.isSettled(it.id) }
                        .forEach { track -> if (extrasQueued.add(track.id)) extrasQueue.trySend(track.toSoundCloudTrack()) }
                }
            }
        }
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
            // Videos looked up under other settings aren't the ones to show now.
            var lookedUpFor: Pair<Pair<Boolean, Boolean>, Boolean>? = null
            combine(
                _currentPlayingTrack,
                _selectedTrack.map { it != null },
                settingsRepository.playerVideos,
                // YouTube's videos, and whether they come in VP9: one this phone failed to decode
                // after all is looked up again, in H.264.
                combine(settingsRepository.videoYouTube, com.example.myapplication.data.VideoDecoders.vp9Allowed(context)) { on, vp9 -> on to vp9 },
                settingsRepository.videoYandex
            ) { track, playerOpen, enabled, youTube, yandex ->
                Triple(track.takeIf { playerOpen && enabled }, youTube, yandex)
            }
                .distinctUntilChangedBy { (track, youTube, yandex) -> Triple(track?.id, youTube, yandex) }
                .collectLatest { (track, youTube, yandex) ->
                    val sources = youTube to yandex
                    if (lookedUpFor != null && lookedUpFor != sources) {
                        videoLookups.clear()
                        videoLookupsInFlight.clear()
                        _trackVideo.value = null
                        _pendingTrackVideo.value = null
                        withContext(Dispatchers.IO) { offlineVideos.forgetNone() }
                    }
                    lookedUpFor = sources
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

        // A track goes into the history once it has been heard for a moment: not one skipped past,
        // nor one put back in the queue at launch and never played.
        viewModelScope.launch {
            combine(_currentPlayingTrack, musicPlayer.isPlaying) { track, playing -> track?.takeIf { playing } }
                .distinctUntilChangedBy { it?.id }
                .collectLatest { track ->
                    if (track == null) return@collectLatest
                    delay(HISTORY_HEARD_MS)
                    // The same track as it is now: resolving it may have filled it in meanwhile.
                    val heard = _currentPlayingTrack.value?.takeIf { it.id == track.id } ?: track
                    listeningHistory.record(heard)
                }
        }

        viewModelScope.launch {
            _currentPlayingTrack.distinctUntilChangedBy { it?.id }.collectLatest { track ->
                _lyrics.value = null
                // A broadcast has its chat where lyrics would be.
                if (track == null || track.liveVideoId != null) return@collectLatest
                // Yandex's own first; LRCLIB's for everything else, and for Yandex's without.
                val lines = track.urn?.takeIf { it.startsWith("yandex:track:") }?.let { lyricsRepository.syncedLyrics(it) }
                    ?: lrcLib.syncedLyrics(track)
                    ?: return@collectLatest
                _lyrics.value = TrackLyrics(track.id, lines)
            }
        }

        viewModelScope.launch {
            // The track shown: a crossfade's next one from the moment it is heard.
            combine(
                musicPlayer.shownTrackId,
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

    // Yandex Music's home, as rows; see [loadYandexShelves].
    private val _yandexShelves = MutableStateFlow<List<com.example.myapplication.data.YtShelf>>(emptyList())
    val yandexShelves = _yandexShelves.asStateFlow()

    /** Yandex Music's own home rows: the playlists made for the listener, new releases, the chart. */
    private fun loadYandexShelves() {
        viewModelScope.launch {
            try {
                val shelves = com.example.myapplication.data.YandexLanding.rows(yandexService)
                Log.d("MusicViewModel", "Yandex home rows: " + shelves.joinToString { "${it.title} (${it.tracks.size} tracks, ${it.sets.size} sets)" })
                _yandexShelves.value = shelves
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Yandex home rows failed", e)
            }
        }
    }

    fun loadYandexPlaylists() {
        val token = settingsRepository.yandexTokenValue()
        if (token.isBlank()) {
            _yandexPlaylists.value = emptyList()
            _yandexShelves.value = emptyList()
            return
        }
        loadYandexShelves()
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
                        title = tr("Мне нравится"),
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
            _errorMessage.value = tr("Укажите SoundCloud client_id в настройках")
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
            _errorMessage.value = tr("Укажите SoundCloud client_id в настройках")
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
                    _errorMessage.value = tr("Укажите SoundCloud client_id в настройках")
                    return@launch
                }
                val tracks = mixesRepository.loadMixTracks(mix, clientId)
                    .filter(::isPlayableTrack)

                if (_loadingMixId.value != mix.id) return@launch

                if (tracks.isEmpty()) {
                    _errorMessage.value = tr("В этом миксе нет доступных треков.")
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
                _playingFrom.value = "mix-${mix.id}"
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
                _errorMessage.value = tr("Укажите SoundCloud client_id в настройках")
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

    /**
     * A track started from a list: the player opens over it, unless the settings say the mini
     * player is enough ([SettingsRepository.openPlayerOnTap]).
     */
    private fun showPlayerFor(track: SoundCloudTrack) {
        if (settingsRepository.openPlayerOnTap.value) _selectedTrack.value = track
    }

    fun playMixTrack(track: SoundCloudTrack) {
        if (together.relayPlay(track, _mixTracks.value)) return
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
                _playingFrom.value = _selectedMix.value?.id?.let { "mix-$it" }
                // As anywhere else: a mix's track opened the player nowhere but here did it not.
                startTrack?.let(::showPlayerFor)
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
        track.liveVideoId?.let { return "ytlive://track/$it" }
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
                    _errorMessage.value = tr("Укажите SoundCloud client_id в настройках")
                    return@launch
                }
                val streamUrl = if (serviceUrl != null) {
                    serviceUrl
                } else {
                    localStreamUrl(track.id)
                        ?: playbackResolver.resolve(track, clientId)
                }

                if (streamUrl == null) {
                    _errorMessage.value = tr("Для этого трека не нашёлся доступный поток.")
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
                _playingFrom.value = null
                _currentPlayingTrack.value = track
                showPlayerFor(track)
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
                    tr("YouTube Music не принял вход. Войдите заново в настройках.")
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
                val tracks = when {
                    set.permalinkUrl?.startsWith("yandex:") == true -> loadYandexSetTracks(set)
                    set.permalinkUrl?.startsWith(YT_SET_REF) == true -> ytMusic.setTracks(set)
                    // A SoundCloud set, opened from a link.
                    else -> loadSoundCloudPlaylistTracks(set)
                }
                if (_ytOpenedSet.value?.id == set.id) {
                    _ytOpenedSet.value = set.copy(tracks = tracks, trackCount = tracks.size)
                }
                if (tracks.isEmpty()) _ytSetError.value = tr("В этой подборке не нашлось треков.")
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

    // region Listening together

    // The phones nearby, found and talked to directly.
    private val togetherSession = com.example.myapplication.together.TogetherSession(context.applicationContext)

    /** Listening together with phones nearby; see [ListenTogether]. */
    val together: com.example.myapplication.together.ListenTogether = com.example.myapplication.together.ListenTogether(
        session = togetherSession,
        player = object : com.example.myapplication.together.TogetherPlayer {
            // The track the player is on, not the one shown: a crossfade shows the next one
            // seconds before the player gets there, and the phones keep to the player.
            override val currentTrack = combine(_currentPlayingTrack, musicPlayer.currentTrackId, _activeQueue) { shown, id, queue ->
                if (shown?.id == id) shown else queue.firstOrNull { it.id == id } ?: shown
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
            override val isPlaying = musicPlayer.isPlaying
            override val currentTrackId = musicPlayer.currentTrackId
            // A crossfade settling moves the main player about unheard: nothing to keep level then.
            override val isBuffering = combine(musicPlayer.isBuffering, com.example.myapplication.player.SessionBridge.crossfadeSettling) { loading, settling -> loading || settling }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)
            override fun livePositionMs() = musicPlayer.playerPositionMs()
            override fun isReadyFor(trackId: Long) = musicPlayer.isReadyFor(trackId)
            override fun wantsToPlay() = musicPlayer.wantsToPlay()
            override fun togglePlayPause() = musicPlayer.togglePlayPause()
            override fun skip(next: Boolean) = if (next) musicPlayer.skipNext() else musicPlayer.skipPrevious()
            // A guest's button, on the host, means the track shown there; a guest keeping level
            // with the host means its player's own.
            override fun seekTo(positionMs: Long) =
                if (together.isGuest) musicPlayer.seekPlayerTo(positionMs) else musicPlayer.seekTo(positionMs)
            override fun playQueue(track: SoundCloudTrack, queue: List<SoundCloudTrack>) =
                playQueuedTrack(track, queue, fromGuest = true)
            override fun changedByItself() = musicPlayer.changedByItself()
            override fun upcoming(count: Int): List<SoundCloudTrack> {
                val queue = _activeQueue.value
                return musicPlayer.upcomingIds(count).mapNotNull { id -> queue.firstOrNull { it.id == id } }
            }
            override fun crossfadeSeconds() = settingsRepository.crossfadeSeconds.value
            override fun follow(track: SoundCloudTrack, url: String?, startMs: Long, playing: Boolean, next: List<Pair<SoundCloudTrack, String?>>) =
                followHost(track, url, startMs, playing, next)
            override fun followUpcoming(next: List<Pair<SoundCloudTrack, String?>>) = followHostUpcoming(next)
            override fun setPlaying(playing: Boolean) = musicPlayer.setPlaying(playing)
            override fun setSpeed(speed: Float) = musicPlayer.setSpeed(speed)
            override fun useCrossfade(seconds: Int?) {
                com.example.myapplication.player.SessionBridge.crossfadeOverride.value = seconds
            }
            override fun failureOf(trackId: Long): String? = followFailures[trackId]
                ?: _activeQueue.value.firstOrNull { it.id == trackId }
                    ?.takeIf { musicPlayer.failedTrackId.value == trackId }
                    ?.let(::whyNotPlaying)
            override suspend fun directUrlFor(track: SoundCloudTrack): String? {
                val id = track.urn?.takeIf { it.startsWith("yandex:track:") }?.removePrefix("yandex:track:") ?: return null
                val token = settingsRepository.yandexTokenValue().takeIf { it.isNotBlank() } ?: return null
                return YandexMusicApi.resolveTrackStream(id, token)
            }
            override fun shareable(track: SoundCloudTrack): SoundCloudTrack {
                fun onPhone(url: String?) = url != null && (url.startsWith("file:") || url.startsWith("/") || url.startsWith("content:"))
                if (!onPhone(track.artworkUrl)) return track
                val web = favoritesRepository.get(track.id)?.artworkUrl
                    ?: playlistsRepository.playlists.value.firstNotNullOfOrNull { list -> list.tracks.firstOrNull { it.id == track.id }?.artworkUrl }
                return track.copy(artworkUrl = web?.takeUnless(::onPhone))
            }
        },
        scope = viewModelScope,
        signedIn = ::signedInServices
    )

    /** The services this phone is signed in to, as listening together names them. */
    fun signedInServices(): Set<String> = buildSet {
        if (settingsRepository.oauthTokenValue().isNotBlank()) add(TogetherServices.SOUNDCLOUD)
        if (settingsRepository.yandexTokenValue().isNotBlank()) add(TogetherServices.YANDEX)
        if (settingsRepository.ytMusicAccount.value != null) add(TogetherServices.YOUTUBE)
    }

    // On a guest: the host's tracks this phone couldn't start, and why (see TogetherPlayer.failureOf);
    // and those it has said so of, once each.
    private val followFailures = HashMap<Long, String>()
    private val warnedFailures = HashSet<Long>()

    init {
        viewModelScope.launch {
            together.notices.collect { notice ->
                val text = when (notice) {
                    is TogetherNotice.GuestCantPlay -> {
                        val title = notice.track?.title?.let { "«$it»" } ?: tr("трек")
                        when (notice.reason) {
                            TogetherServices.LOCAL -> tr("У «%s» нет %s: он есть только на вашем телефоне", notice.guest, title)
                            TogetherServices.ERROR -> tr("У «%s» не заиграл %s", notice.guest, title)
                            else -> tr("У «%s» не заиграет %s: нет входа в %s", notice.guest, title, serviceInAccusative(notice.reason))
                        }
                    }
                    is TogetherNotice.GuestsTooSlow ->
                        notice.guests.joinToString { "«$it»" } + tr(" не успевает загрузить трек — играем без ожидания")
                }
                Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            }
        }
        viewModelScope.launch {
            // On a guest: the host's track won't play here.
            musicPlayer.failedTrackId.collect { id ->
                if (id == null || !together.isGuest) return@collect
                val track = _activeQueue.value.firstOrNull { it.id == id } ?: return@collect
                warnCantFollow(track, whyNotPlaying(track))
            }
        }
        viewModelScope.launch {
            together.state.collect { state ->
                if (state !is com.example.myapplication.together.TogetherState.Joined) {
                    followFailures.clear()
                    warnedFailures.clear()
                }
            }
        }
    }

    /** Why [track] won't play here: its service not signed in to, or something else. */
    private fun whyNotPlaying(track: SoundCloudTrack): String {
        if (track.urn?.startsWith("local:") == true) return TogetherServices.LOCAL
        val service = TogetherServices.of(track)
        return if (service != null && service !in signedInServices()) service else TogetherServices.ERROR
    }

    /** "в Яндекс Музыку": a service as signing in to it is said. */
    private fun serviceInAccusative(service: String): String = when (service) {
        TogetherServices.YANDEX -> tr("Яндекс Музыку")
        TogetherServices.YOUTUBE -> "YouTube Music"
        else -> "SoundCloud"
    }

    /** On a guest: says, once a track, that the host's [track] won't play here, and what would help. */
    private fun warnCantFollow(track: SoundCloudTrack, why: String) {
        if (!warnedFailures.add(track.id)) return
        val title = track.title?.let { "«$it»" } ?: tr("Этот трек")
        val text = when (why) {
            TogetherServices.LOCAL -> tr("%s есть только на телефоне ведущего", title)
            TogetherServices.ERROR -> tr("%s не заиграл на этом телефоне", title)
            else -> tr("%s не заиграет: войдите в %s (Настройки → Аккаунты)", title, serviceInAccusative(why))
        }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    }

    /** What this phone is called to the phones nearby: its own name, as set in Android. */
    private fun deviceName(): String =
        android.provider.Settings.Global.getString(context.contentResolver, android.provider.Settings.Global.DEVICE_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: android.os.Build.MODEL

    fun hostTogether() = togetherSession.host(deviceName())

    fun searchTogether() = togetherSession.search(deviceName())

    fun joinTogether(host: com.example.myapplication.together.TogetherPeer) = togetherSession.join(host)

    fun answerTogether(request: com.example.myapplication.together.TogetherRequest, accept: Boolean) =
        togetherSession.answer(request, accept)

    fun leaveTogether() = together.leave()

    /**
     * A track from the library picked on a guest: sent to the host to play, unless it is a file of
     * this phone's own, which the host has no way to play.
     */
    private fun relayFromLibrary(track: SoundCloudTrack, library: List<FavoriteTrack>): Boolean {
        if (!together.isGuest) return false
        if (track.urn?.startsWith("local:") == true) {
            Toast.makeText(context, tr("Этот трек есть только на вашем телефоне"), Toast.LENGTH_SHORT).show()
            return true
        }
        val queue = library.map { it.toSoundCloudTrack() }.filterNot { it.urn?.startsWith("local:") == true }
        return together.relayPlay(track, queue)
    }

    /**
     * On a guest: the host's track, from where the host is in it, with the host's [next] tracks
     * queued after it to load ahead. Played by this phone's own means — its copy, its own service —
     * or, a Yandex track without Yandex Music here, by the address the host sent.
     */
    private fun followHost(track: SoundCloudTrack, url: String?, startMs: Long, playing: Boolean, next: List<Pair<SoundCloudTrack, String?>>) {
        viewModelScope.launch {
            val streamUrl = guestStreamUrl(track, url, resolve = true)
            if (streamUrl == null) {
                val why = if (track.urn?.startsWith("local:") == true) TogetherServices.LOCAL else TogetherServices.of(track) ?: TogetherServices.ERROR
                followFailures[track.id] = why
                warnCantFollow(track, why)
                return@launch
            }
            followFailures.remove(track.id)
            warnedFailures.remove(track.id)
            val upcoming = guestUpcoming(track, next)
            resolvedUrls[track.id] = streamUrl
            upcoming.forEach { (t, u) -> resolvedUrls[t.id] = u }
            val queue = listOf(track) + upcoming.map { it.first }
            originalQueue = queue
            _activeQueue.value = queue
            musicPlayer.playQueue(
                listOf(track.toQueueTrack(streamUrl)) + upcoming.map { (t, u) -> t.toQueueTrack(u) },
                0,
                startMs,
                playing
            )
            _playingMixId.value = null
            _playingFrom.value = null
            _currentPlayingTrack.value = track
            if (_selectedTrack.value != null) _selectedTrack.value = track
        }
    }

    /** On a guest: the host's next tracks after the one playing here, which plays on untouched. */
    private fun followHostUpcoming(next: List<Pair<SoundCloudTrack, String?>>) {
        viewModelScope.launch {
            val currentId = musicPlayer.currentTrackId.value ?: return@launch
            val current = _activeQueue.value.firstOrNull { it.id == currentId } ?: return@launch
            val upcoming = guestUpcoming(current, next)
            val queue = listOf(current) + upcoming.map { it.first }
            if (queue.map { it.id } == _activeQueue.value.map { it.id }) return@launch
            upcoming.forEach { (t, u) -> resolvedUrls[t.id] = u }
            if (musicPlayer.replaceUpcoming(upcoming.map { (t, u) -> t.toQueueTrack(u) })) {
                originalQueue = queue
                _activeQueue.value = queue
            }
        }
    }

    /** The host's [next] tracks this phone can play, after [current], each with how. */
    private suspend fun guestUpcoming(current: SoundCloudTrack, next: List<Pair<SoundCloudTrack, String?>>): List<Pair<SoundCloudTrack, String>> =
        next.filter { it.first.id != current.id }
            .mapNotNull { (t, hostUrl) -> guestStreamUrl(t, hostUrl, resolve = false)?.let { t to it } }

    /**
     * How this phone plays a track the host plays: its own copy, its own service, or [hostUrl] (a
     * Yandex track without Yandex Music here); null when it has no way. [resolve]: a SoundCloud
     * track's stream found now — for the one to start at once — rather than as the player gets to it.
     */
    private suspend fun guestStreamUrl(track: SoundCloudTrack, hostUrl: String?, resolve: Boolean): String? {
        localStreamUrl(track.id)?.takeIf { it.isNotBlank() }?.let { return it }
        if (track.urn?.startsWith("local:") == true) return null
        if (track.urn?.startsWith("yandex:") == true && settingsRepository.yandexTokenValue().isBlank()) return hostUrl
        serviceStreamUrl(track)?.let { return it }
        if (!resolve) return placeholderStreamUrl(track)
        return runCatching { playbackResolver.resolve(track, settingsRepository.clientId.value) }.getOrNull()
            ?: placeholderStreamUrl(track)
    }

    // endregion

    // region Links

    // A link being opened: a second one shared meanwhile takes its place.
    private var linkJob: Job? = null

    /**
     * Opens what a link shared to the app, or opened with it, points at — [text] may be the link
     * alone or words around it: a track plays with the player up (followed, for Yandex Music and
     * YouTube Music, by its radio, as their own apps do); an album or playlist opens as a set; an
     * artist or channel opens its page.
     */
    fun openSharedText(text: String) {
        val url = SharedLinks.findUrl(text)
        if (url == null) {
            Toast.makeText(context, tr("Здесь нет ссылки"), Toast.LENGTH_SHORT).show()
            return
        }
        linkJob?.cancel()
        linkJob = viewModelScope.launch {
            var link = SharedLinks.parse(url)
            if (link is SharedLink.Short) link = SharedLinks.follow(link.url)
            if (link == null) {
                Toast.makeText(context, tr("Такие ссылки YouCloud пока не открывает"), Toast.LENGTH_SHORT).show()
                return@launch
            }
            Log.d("MusicViewModel", "Opening link $link")
            try {
                openLink(link)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Link $url failed", e)
                Toast.makeText(context, tr("Не удалось открыть ссылку: %s", readableMessage(e)), Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun openLink(link: SharedLink) {
        when (link) {
            is SharedLink.YandexTrack -> {
                if (settingsRepository.yandexTokenValue().isBlank()) {
                    Toast.makeText(context, tr("Чтобы слушать треки Яндекс Музыки, войдите в неё в настройках"), Toast.LENGTH_LONG).show()
                    return
                }
                val ids = if (link.albumId != null) "${link.trackId}:${link.albumId}" else link.trackId
                val track = yandexService.getTracksDetails(ids).result.orEmpty().firstOrNull()
                    ?.toSoundCloudTrack(customAlbumId = link.albumId)
                    ?: throw IllegalStateException(tr("трек не найден"))
                playFromLink(track)
                // Then on with its radio: the queue grows behind the track as it plays.
                playYandexRadio(track)
            }
            is SharedLink.YandexAlbum -> openSetFromLink(
                yandexAlbum(link.albumId) ?: throw IllegalStateException(tr("альбом не найден"))
            )
            is SharedLink.YandexArtist -> openArtistFromLink {
                openArtistDetails(link.artistId.toLongOrNull() ?: 0L, "yandex:artist:${link.artistId}", null)
            }
            is SharedLink.YandexPlaylist -> openSetFromLink(
                yandexPlaylist(yandexService.getPlaylistByOwner(link.owner, link.kind).result)
            )
            is SharedLink.YandexPlaylistUuid -> openSetFromLink(
                yandexPlaylist(yandexService.getPlaylistByUuid(link.uuid).result)
            )
            is SharedLink.YouTubeVideo -> {
                val queue = ytMusic.radio(link.videoId)
                val id = com.example.myapplication.data.youTubeTrackId(link.videoId)
                val track = queue.firstOrNull { it.id == id || it.youTubeVideoId == link.videoId || it.liveVideoId == link.videoId }
                    ?: queue.firstOrNull()
                    ?: throw IllegalStateException(tr("видео не найдено"))
                playFromLink(track, queue)
            }
            is SharedLink.YouTubeSet -> {
                val set = ytMusic.setFromLink(link.browseId, link.playlistId)
                if (set.knownTracks.isEmpty()) throw IllegalStateException(tr("в этой подборке нет треков"))
                openSetFromLink(if (set.title.isNullOrBlank()) set.copy(title = tr("Микс YouTube Music")) else set)
            }
            is SharedLink.YouTubeChannel -> openArtistFromLink {
                openArtistDetails(0L, YT_ARTIST_REF + link.channelId, null)
            }
            is SharedLink.SoundCloud -> openSoundCloudLink(link.url)
            is SharedLink.Short -> Unit // followed already in openSharedText
        }
    }

    private suspend fun openSoundCloudLink(url: String) {
        val clientId = settingsRepository.clientId.value
        if (clientId.isBlank()) {
            Toast.makeText(context, tr("Укажите SoundCloud client_id в настройках"), Toast.LENGTH_LONG).show()
            return
        }
        val found = try {
            service.resolve(url, clientId)
        } catch (e: Exception) {
            handleSoundCloudApiError(e)
            throw e
        }
        val gson = com.google.gson.Gson()
        when (found.get("kind")?.takeIf { it.isJsonPrimitive }?.asString) {
            "track" -> playFromLink(gson.fromJson(found, SoundCloudTrack::class.java))
            "playlist", "system-playlist" -> {
                val set = gson.fromJson(found, SoundCloudPlaylist::class.java)
                openSetFromLink(set.copy(tracks = loadSoundCloudPlaylistTracks(set)))
            }
            "user" -> {
                val user = gson.fromJson(found, SoundCloudUser::class.java)
                openArtistFromLink { openArtistDetails(user.id ?: 0L, user.permalinkUrl ?: url, user.username, avatarUrl = user.avatarUrl) }
            }
            else -> throw IllegalStateException(tr("на этой странице SoundCloud нет трека"))
        }
    }

    /** A track from a link: playing at once, with the player up over whatever screen is open. */
    private fun playFromLink(track: SoundCloudTrack, queue: List<SoundCloudTrack> = listOf(track)) {
        _selectedMix.value = null
        playQueuedTrack(track, queue, openPlayer = true)
    }

    /** An album or playlist from a link, its tracks known already, on the set screen. */
    private fun openSetFromLink(set: SoundCloudPlaylist) {
        _selectedTrack.value = null
        _selectedMix.value = null
        closeSearchPlaylist()
        openYtSet(set.copy(trackCount = maxOf(set.trackCount, set.knownTracks.size)))
    }

    private fun openArtistFromLink(open: () -> Unit) {
        _selectedTrack.value = null
        _selectedMix.value = null
        closeSearchPlaylist()
        open()
    }

    /** A Yandex album as a set, with its tracks. */
    private suspend fun yandexAlbum(albumId: Long, fallbackArtwork: String? = null): SoundCloudPlaylist? {
        val detail = yandexService.getAlbumWithTracks(albumId).result ?: return null
        val tracks = detail.volumes.orEmpty().flatten()
            .map { it.toSoundCloudTrack(customAlbumId = albumId.toString()) }
            .filter { isPlayableTrack(it) }
            .distinctBy { it.id }
        return SoundCloudPlaylist(
            id = albumId + 10_000_000L,
            title = detail.title,
            tracks = tracks,
            trackCount = tracks.size,
            artworkUrl = detail.coverUri?.let { "https://" + it.replace("%%", "400x400") } ?: fallbackArtwork,
            permalinkUrl = "yandex:album:$albumId",
            user = tracks.firstOrNull()?.user,
            isAlbum = true,
            setType = "album"
        )
    }

    /** Someone's Yandex playlist as a set, with its tracks. */
    private fun yandexPlaylist(detail: com.example.myapplication.data.YandexPlaylistDetail?): SoundCloudPlaylist {
        detail ?: throw IllegalStateException(tr("плейлист не найден"))
        val tracks = detail.tracks.orEmpty().mapNotNull { it.track?.toSoundCloudTrack() }
            .filter { isPlayableTrack(it) }
            .distinctBy { it.id }
        val owner = detail.owner
        return SoundCloudPlaylist(
            // Apart from the listener's own playlists (their kind as the id) and albums.
            id = 20_000_000_000L + (owner?.uid ?: 0L) * 1_000L + detail.kind,
            title = detail.title,
            tracks = tracks,
            trackCount = tracks.size,
            artworkUrl = detail.cover?.url() ?: detail.ogImage?.let { "https://" + it.replace("%%", "400x400") }
                ?: tracks.firstNotNullOfOrNull { it.artworkUrl },
            permalinkUrl = owner?.uid?.let { "yandex:playlist:$it:${detail.kind}" },
            user = SoundCloudUser(username = owner?.name ?: owner?.login),
            isAlbum = false
        )
    }

    // endregion

    /**
     * Opens the album [track] is from, as the player's title does: Yandex Music's (the track
     * carries its album's id, a saved one too), YouTube Music's (asked of its watch queue).
     */
    fun openTrackAlbum(track: SoundCloudTrack) {
        viewModelScope.launch {
            val album = try {
                findTrackAlbum(track)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Album of ${track.urn} failed", e)
                null
            }
            if (album == null) {
                Toast.makeText(context, tr("Не нашлось альбома этого трека"), Toast.LENGTH_SHORT).show()
                return@launch
            }
            _selectedTrack.value = null
            _selectedMix.value = null
            openYtSet(album)
        }
    }

    private suspend fun findTrackAlbum(track: SoundCloudTrack): SoundCloudPlaylist? {
        val urn = track.urn.orEmpty()
        if (urn.startsWith("yandex:track:")) {
            val parts = urn.removePrefix("yandex:track:").split(':')
            val albumId = parts.getOrNull(1)?.toLongOrNull()
                ?: yandexService.getTracksDetails(parts.first()).result.orEmpty().firstOrNull()?.albums?.firstOrNull()?.id
                ?: return null
            return yandexAlbum(albumId, fallbackArtwork = track.artworkUrl)
        }
        val videoId = track.youTubeVideoId ?: return null
        return ytMusic.albumOf(videoId)?.let { it.copy(artworkUrl = track.artworkUrl ?: it.artworkUrl) }
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
                playQueueFrom(track, listOf(track) + radio)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "YouTube Music radio for $videoId failed", e)
                android.widget.Toast.makeText(context, tr("Не удалось загрузить радио: %s", readableMessage(e)), android.widget.Toast.LENGTH_SHORT).show()
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
        // Left while picking for the profile's showcase: back to the profile, nothing picked.
        if (_showcasePick.value != null) {
            _showcasePick.value = null
            _screen.value = AppScreen.PROFILE
            return
        }
        _screen.value = AppScreen.HOME
    }

    // The place of the profile's showcase being picked in search: a tap there on what it wants —
    // a track, an artist, an album — puts it there, rather than playing or opening it.
    private val _showcasePick = MutableStateFlow<ShowcaseSlot?>(null)
    val showcasePick = _showcasePick.asStateFlow()

    fun pickShowcase(slot: ShowcaseSlot) {
        _showcasePick.value = slot
        _screen.value = AppScreen.SEARCH
    }

    fun chooseShowcaseTrack(track: SoundCloudTrack) = chooseShowcase(
        ShowcaseItem(
            title = track.title ?: tr("Без названия"),
            subtitle = track.artists?.mapNotNull { it.username?.takeIf(String::isNotBlank) }
                ?.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: track.user?.username,
            cover = track.artworkUrl,
            link = track.pageLink(),
            service = sourceOf(track.urn).takeIf { it.isNotEmpty() }
        )
    )

    fun chooseShowcaseArtist(artist: SoundCloudUser) {
        val link = SharedLinks.linkOf(artist)
        chooseShowcase(ShowcaseItem(artist.username ?: tr("Без названия"), cover = artist.avatarUrl, link = link, service = SharedLinks.serviceOf(link)))
    }

    fun chooseShowcaseSet(set: SoundCloudPlaylist) {
        val link = SharedLinks.linkOf(set)
        chooseShowcase(
            ShowcaseItem(set.title ?: tr("Без названия"), set.user?.username, set.displayArtworkUrl, link, SharedLinks.serviceOf(link))
        )
    }

    private fun chooseShowcase(item: ShowcaseItem) {
        val slot = _showcasePick.value ?: return
        _showcasePick.value = null
        _screen.value = AppScreen.PROFILE
        viewModelScope.launch {
            try {
                Social.get(context).setShowcase(slot, item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, e.socialMessage(), Toast.LENGTH_LONG).show()
            }
        }
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

    fun openHistory() {
        _screen.value = AppScreen.HISTORY
    }

    fun closeHistory() {
        _screen.value = AppScreen.HOME
    }

    /** From the history: on from [track] through what was heard before it. */
    fun playHistoryTrack(track: SoundCloudTrack) {
        playQueuedTrack(track, listeningHistory.tracks.value, source = "history")
    }

    fun playHistoryShuffled() {
        playShuffled(listeningHistory.tracks.value, source = "history")
    }

    fun clearHistory() {
        listeningHistory.clear()
    }

    // Settings are opened from the profile now, and close back to it; from a warning on home
    // they close to home.
    private var settingsFromProfile = false

    fun openSettings() {
        settingsFromProfile = _screen.value == AppScreen.PROFILE
        _screen.value = AppScreen.SETTINGS
    }

    fun closeSettings() {
        _screen.value = if (settingsFromProfile) AppScreen.PROFILE else AppScreen.HOME
        settingsFromProfile = false
    }

    fun openProfile() {
        _screen.value = AppScreen.PROFILE
    }

    fun closeProfile() {
        _screen.value = AppScreen.HOME
    }

    // Where the friends were opened from: the profile, or home's avatar menu.
    private var friendsFrom = AppScreen.PROFILE

    fun openFriends() {
        if (_screen.value != AppScreen.FRIENDS) {
            friendsFrom = if (_screen.value == AppScreen.PROFILE) AppScreen.PROFILE else AppScreen.HOME
        }
        _screen.value = AppScreen.FRIENDS
    }

    fun closeFriends() {
        _screen.value = friendsFrom
    }

    private val _personId = MutableStateFlow<String?>(null)
    /** Whose profile [AppScreen.PERSON] shows. */
    val personId = _personId.asStateFlow()
    private var personFrom = AppScreen.FRIENDS

    fun openPerson(id: String) {
        if (_screen.value != AppScreen.PERSON) personFrom = _screen.value
        _personId.value = id
        _screen.value = AppScreen.PERSON
    }

    fun closePerson() {
        _screen.value = personFrom
    }

    /**
     * The page [screen] goes back to, when that is a page rather than home: what pulling it down
     * uncovers (the friends were opened from the profile, someone's page from the friends).
     */
    fun pageUnder(screen: AppScreen?): AppScreen? = when (screen) {
        AppScreen.FRIENDS -> friendsFrom.takeIf { it == AppScreen.PROFILE }
        AppScreen.PERSON -> personFrom.takeIf { it == AppScreen.FRIENDS || it == AppScreen.PROFILE }
        else -> null
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
        lastArtistOpen = { openArtistDetails(userId, permalinkUrl, username, trackUrn, avatarUrl) }
        if (permalinkUrl?.startsWith(YT_ARTIST_REF) == true) {
            openYouTubeArtist(permalinkUrl.removePrefix(YT_ARTIST_REF), username, avatarUrl)
            return
        }
        // A YouTube broadcast YouTube Music files under a podcast names no channel: the watch
        // page does.
        val liveId = (_selectedTrack.value ?: _currentPlayingTrack.value)?.takeIf { permalinkUrl == null }?.liveVideoId
        if (liveId != null) {
            viewModelScope.launch {
                val owner = runCatching { com.example.myapplication.data.YouTubeWeb.videoOwner(liveId, settingsRepository.ytMusicAuth()) }.getOrNull()
                val channel = owner?.permalinkUrl
                if (channel != null) {
                    openYouTubeArtist(channel.removePrefix(YT_ARTIST_REF), owner.username, owner.avatarUrl)
                } else {
                    android.widget.Toast.makeText(context, tr("Не удалось найти канал трансляции"), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
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
            resetArtistExtras()
            
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
                        _currentArtist.value?.let(::checkArtistFollow)
                    } else {
                        _currentArtist.value = SoundCloudUser(username = username ?: tr("Яндекс Артист"))
                        _artistError.value = tr("Информация об артисте недоступна")
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to fetch Yandex artist info", e)
                    _currentArtist.value = SoundCloudUser(username = username ?: tr("Яндекс Артист"))
                    _artistError.value = tr("Ошибка: %s", readableMessage(e, isYandex = true))
                } finally {
                    _artistLoading.value = false
                }
            } else {
                // SoundCloud artist
                val clientIdVal = settingsRepository.clientId.value
                if (clientIdVal.isBlank()) {
                    _currentArtist.value = SoundCloudUser(id = userId, username = username)
                    _artistError.value = tr("Укажите SoundCloud client_id в настройках")
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
                    checkArtistFollow(resolvedUser)

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
                        _artistError.value = tr("Не удалось определить ID артиста")
                    }
                } catch (e: Exception) {
                    Log.e("MusicViewModel", "Failed to fetch SoundCloud artist stream", e)
                    handleSoundCloudApiError(e)
                    _artistError.value = tr("Не удалось загрузить данные: %s", readableMessage(e))
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
        lastArtistOpen = { openYouTubeArtist(channelId, name, avatarUrl) }
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
        resetArtistExtras()
        ytArtistSongs = null
        ytArtistJob?.cancel()
        ytArtistJob = viewModelScope.launch {
            // What the channel is broadcasting, from youtube.com, while YouTube Music gives the rest.
            val lives = async {
                try {
                    com.example.myapplication.data.YouTubeWeb.channelLive(
                        channelId, SoundCloudUser(username = name, permalinkUrl = YT_ARTIST_REF + channelId),
                        settingsRepository.ytMusicAuth()
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("MusicViewModel", "Broadcasts of $channelId failed", e)
                    emptyList()
                }
            }
            try {
                // Over a VPN a request now and then breaks off: once more before giving up.
                val page = try {
                    ytMusic.artist(channelId)
                } catch (e: IOException) {
                    Log.w("MusicViewModel", "YouTube Music artist $channelId, trying again", e)
                    delay(ARTIST_RETRY_MS)
                    ytMusic.artist(channelId)
                }
                _currentArtist.value = page.artist.copy(
                    username = page.artist.username?.takeIf { it.isNotBlank() } ?: name,
                    avatarUrl = page.artist.avatarUrl ?: avatarUrl
                )
                _currentArtistTracks.value = page.topSongs
                _currentArtistPlaylists.value = page.releases
                ytArtistSongs = page.allSongs
                _isAllArtistTracksLoaded.value = page.allSongs == null
                _artistFollow.value = page.subscribed?.let { ArtistFollow(it) }
                ytFollowParams = page.subscribeParams to page.unsubscribeParams
                val owner = SoundCloudUser(username = _currentArtist.value?.username, permalinkUrl = YT_ARTIST_REF + channelId)
                val live = lives.await().map { it.copy(user = owner, artists = listOf(owner)) }
                _artistLives.value = live
                // Rows the page's top already shows (its videos standing in for songs), and the
                // broadcasts it files as a podcast's episodes, aren't shown twice.
                val shown = (page.topSongs + live).mapTo(HashSet()) { it.id }
                _artistShelves.value = page.shelves
                    .map { shelf -> shelf.copy(tracks = shelf.tracks.filterNot { it.id in shown }) }
                    .filter { it.tracks.isNotEmpty() || it.sets.isNotEmpty() || it.artists.isNotEmpty() }
                // A channel YouTube Music has nothing of (a video maker's, say): its videos, as
                // youtube.com lists them.
                if (page.topSongs.isEmpty() && page.releases.isEmpty() && page.shelves.isEmpty()) {
                    val channel = com.example.myapplication.data.YouTubeWeb.channelVideos(channelId, owner, settingsRepository.ytMusicAuth())
                    val current = _currentArtist.value
                    _currentArtist.value = current?.copy(
                        username = current.username?.takeIf { it.isNotBlank() } ?: channel.name,
                        avatarUrl = current.avatarUrl ?: channel.avatarUrl,
                        description = current.description ?: channel.description
                    )
                    val liveIds = live.mapTo(HashSet()) { it.id }
                    _currentArtistTracks.value = channel.videos.filterNot { it.id in liveIds }
                    _currentArtistPlaylists.value = channel.playlists
                    _artistTracksAreVideos.value = true
                    ytChannelContinuation = channel.continuation
                    _isAllArtistTracksLoaded.value = channel.continuation == null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "YouTube Music artist $channelId failed", e)
                _artistError.value = tr("Не удалось загрузить артиста: %s", readableMessage(e))
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
        resetArtistExtras()
        _screen.value = if (returnToSearchFromArtist) AppScreen.SEARCH else AppScreen.HOME
        returnToSearchFromArtist = false
    }

    // What a YouTube artist page's subscribe and unsubscribe buttons send.
    private var ytFollowParams: Pair<String?, String?> = null to null

    // How the artist on screen was opened, for "Повторить" when it didn't load.
    private var lastArtistOpen: (() -> Unit)? = null

    fun retryArtist() {
        val backToSearch = returnToSearchFromArtist
        lastArtistOpen?.invoke()
        returnToSearchFromArtist = backToSearch
    }

    private fun resetArtistExtras() {
        _artistTracksAreVideos.value = false
        ytChannelContinuation = null
        artistFollowJob?.cancel()
        _artistFollow.value = null
        _artistLives.value = emptyList()
        _artistShelves.value = emptyList()
    }

    /** Asks whether the account follows [artist] (SoundCloud, Yandex), for the button on its page. */
    private fun checkArtistFollow(artist: SoundCloudUser) {
        artistFollowJob?.cancel()
        artistFollowJob = viewModelScope.launch {
            val following = try {
                isFollowing(artist)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Following ${artist.permalinkUrl} unknown", e)
                false
            }
            if (sameArtist(_currentArtist.value, artist)) {
                _artistFollow.value = following?.let { ArtistFollow(it) }
            }
        }
    }

    // Null: no account in the artist's service, or the artist is the account itself.
    private suspend fun isFollowing(artist: SoundCloudUser): Boolean? {
        val ref = artist.permalinkUrl.orEmpty()
        if (ref.startsWith("yandex:artist:")) {
            if (settingsRepository.yandexTokenValue().isBlank()) return null
            val uid = getYandexUid() ?: return null
            val id = ref.removePrefix("yandex:artist:")
            val liked = yandexService.likedArtists(uid).get("result")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
            return liked.any { entry ->
                val item = entry.takeIf { it.isJsonObject }?.asJsonObject ?: return@any false
                val artistId = item.get("id")
                    ?: item.get("artist")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")
                artistId?.takeIf { it.isJsonPrimitive }?.asString == id
            }
        }
        val userId = artist.id?.takeIf { it != 0L } ?: return null
        if (settingsRepository.oauthTokenValue().isBlank() || settingsRepository.userIdValue() == userId.toString()) return null
        val ids = service.getFollowingIds(settingsRepository.clientId.value)
            .get("collection")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        return ids.any { it.isJsonPrimitive && it.asLong == userId }
    }

    private fun sameArtist(a: SoundCloudUser?, b: SoundCloudUser): Boolean =
        a != null && a.permalinkUrl == b.permalinkUrl && (a.id ?: 0L) == (b.id ?: 0L)

    /** Follows the artist on screen in its service, or unfollows: YouTube's subscription, Yandex's like. */
    fun toggleArtistFollow() {
        val artist = _currentArtist.value ?: return
        val state = _artistFollow.value ?: return
        if (state.busy) return
        val follow = !state.following
        // Shown at once; put back if the service refuses.
        _artistFollow.value = ArtistFollow(follow, busy = true)
        viewModelScope.launch {
            val sent = try {
                sendFollow(artist, follow)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Follow of ${artist.permalinkUrl} failed", e)
                false
            }
            if (!sameArtist(_currentArtist.value, artist)) return@launch
            _artistFollow.value = ArtistFollow(if (sent) follow else !follow)
            val name = artist.username?.takeIf { it.isNotBlank() } ?: tr("исполнителя")
            Toast.makeText(
                context,
                when {
                    sent && follow -> tr("Вы подписались на %s", name)
                    sent -> tr("Вы отписались от %s", name)
                    follow -> tr("Не удалось подписаться")
                    else -> tr("Не удалось отписаться")
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private suspend fun sendFollow(artist: SoundCloudUser, follow: Boolean): Boolean {
        val ref = artist.permalinkUrl.orEmpty()
        when {
            ref.startsWith(YT_ARTIST_REF) -> {
                // As the page's own button: YouTube Music's, with where the subscription is from.
                ytMusic.subscribe(ref.removePrefix(YT_ARTIST_REF), follow, if (follow) ytFollowParams.first else ytFollowParams.second)
                return true
            }
            ref.startsWith("yandex:artist:") -> {
                val uid = getYandexUid() ?: return false
                val id = ref.removePrefix("yandex:artist:")
                if (follow) yandexService.likeArtist(uid, id) else yandexService.unlikeArtist(uid, id)
                return true
            }
        }
        val userId = artist.id?.takeIf { it != 0L } ?: return false
        // From a soundcloud.com page, as the website's own button: likes taught that SoundCloud's
        // bot protection lets nothing else through from a VPN address.
        suspend fun send() = SoundCloudWebRequests.send(
            context = context,
            method = if (follow) "POST" else "DELETE",
            url = "${SoundCloudApi.BASE_URL}me/followings/$userId" +
                "?client_id=${settingsRepository.clientId.value}" +
                "&app_version=${SoundCloudApi.APP_VERSION}&app_locale=en",
            oauthToken = settingsRepository.oauthTokenValue()
        )
        var result = send()
        if (result?.status == 401 && renewSoundCloudSession(settingsRepository.oauthTokenValue())) result = send()
        result?.captchaUrl?.takeIf { !it.contains("t=bv") }?.let { _antiBotCaptchaUrl.value = it }
        return result?.isSuccessful == true
    }

    fun selectArtistPlaylist(playlist: SoundCloudPlaylist) {
        _selectedArtistPlaylist.value = playlist
        val isYandexAlbum = playlist.permalinkUrl?.startsWith("yandex:album:") == true
        if (playlist.permalinkUrl?.startsWith(YT_SET_REF) == true) {
            viewModelScope.launch {
                _artistAlbumLoading.value = true
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
                    _artistAlbumLoading.value = false
                }
            }
        } else if (isYandexAlbum) {
            viewModelScope.launch {
                _artistAlbumLoading.value = true
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
                    _artistAlbumLoading.value = false
                }
            }
        } else if (playlist.permalinkUrl?.startsWith("yandex:") != true) {
            // Stream sets arrive with id-only stubs past the first few tracks, which showed up as
            // "Unknown Track" rows that could not play.
            viewModelScope.launch {
                _artistAlbumLoading.value = true
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
                    _artistAlbumLoading.value = false
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
            tellYandexRadio(track, if (favoritesRepository.isFavorite(track.id)) "unlike" else "like")
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
                val youTubeId = track.youTubeVideoId ?: track.liveVideoId
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
            // A broadcast is kept in favourites to come back to, and liked on YouTube; there is
            // nothing to download.
            track.liveVideoId?.let { liveId ->
                rateOnYouTube(liveId, like = true)
                return@launch
            }
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
                        tr("SoundCloud блокирует лайки с этого адреса. Смените сервер VPN — лайк отправится позже."),
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
                                tr("SoundCloud просит пройти проверку — после неё отправка продолжится")
                            } else {
                                tr("SoundCloud не принял лайк. Остальные отправятся позже или по кнопке")
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
                    message = tr("Войдите в SoundCloud")
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
                    message = tr("Все скачанные треки уже лайкнуты на SoundCloud")
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
                message = tr("Проверка не пройдена. Остальные лайки отправятся позже или по кнопке")
            )
        }
        Toast.makeText(context, tr("Лайк отправится на SoundCloud позже"), Toast.LENGTH_SHORT).show()
    }

    fun playFavorite(track: FavoriteTrack) {
        val playable = track.toSoundCloudTrack()
        if (relayFromLibrary(playable, favoritesRepository.favorites.value.filter { it.downloadState == DownloadState.DOWNLOADED })) return
        val streamUrl = track.streamUrl
        if (streamUrl == null) {
            _errorMessage.value = tr("У этого любимого трека пока нет сохранённого потока.")
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
        _playingFrom.value = "downloads"
        _currentPlayingTrack.value = playable
        showPlayerFor(playable)
    }

    fun togglePlayPause() {
        if (together.relay("toggle")) return
        musicPlayer.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        if (together.relay("seek", positionMs)) return
        musicPlayer.seekTo(positionMs)
    }

    /** See [MusicPlayer.livePositionMs]. */
    fun livePositionMs(): Long = musicPlayer.livePositionMs()

    fun skipNext() {
        if (together.relay("next")) return
        musicPlayer.skipNext()
    }

    fun skipPrevious() {
        if (together.relay("prev")) return
        musicPlayer.skipPrevious()
    }

    // A guest's queue is the one track the host plays: a swipe goes to the host, who has the rest.
    fun hasNeighbourTrack(next: Boolean): Boolean = together.isGuest || musicPlayer.hasNeighbourTrack(next)

    fun skipToNeighbourTrack(next: Boolean) {
        if (together.relay(if (next) "next" else "prev")) return
        musicPlayer.skipToNeighbourTrack(next)
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
                // Only a cover on the web can be fetched: a track imported from the phone has its
                // own file for one, and asking for that path failed on every start.
                val source = ArtworkUrls.highRes(fav.artworkUrl)?.takeIf { it.startsWith("http") } ?: continue
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
                _errorMessage.value = tr("Не удалось удалить локальную копию трека.")
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
                // Signed in afresh: whatever the old session's failures had set — the "sign in
                // again" warning, the recovery ladder — is over, and home loads with the new one.
                onAuthRecovered()
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Failed to login with captured credentials", e)
                _loginError.value = tr("Ошибка при получении профиля SoundCloud. Попробуйте еще раз.")
                _isLoggingIn.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRecoveryJob?.cancel()
            authRecoveryAttempts = 0
            lastAuthRecoveryAt = 0L
            _needsRelogin.value = false
            _isClientIdExpired.value = false
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
        together.leave()
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
                401 -> if (isYandex) tr("Яндекс отклонил запрос. Возможно, токен устарел.") else tr("SoundCloud отклонил запрос. Возможно, client_id устарел.")
                403 -> if (isYandex) tr("Яндекс запретил доступ к этому ресурсу.") else tr("SoundCloud запретил доступ к этому ресурсу.")
                404 -> if (isYandex) tr("Яндекс не нашёл нужного ресурса.") else tr("SoundCloud не нашёл нужный поток.")
                429 -> if (isYandex) tr("Слишком много запросов к Яндексу. Попробуй чуть позже.") else tr("Слишком много запросов к SoundCloud. Попробуй чуть позже.")
                else -> if (isYandex) tr("Ошибка Яндекс Музыки: HTTP %s.", error.code()) else tr("Ошибка SoundCloud: HTTP %s.", error.code())
            }

            is IOException -> tr("Нет соединения с сетью.")
            else -> if (isYandex) tr("Не удалось выполнить запрос к Яндекс Музыке.") else tr("Не удалось выполнить запрос к SoundCloud.")
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
            artworkUrl = artworkUrl,
            leadArtist = artists?.firstNotNullOfOrNull { it.username?.takeIf(String::isNotBlank) } ?: user?.username,
            source = sourceOf(urn),
            link = pageLink()
        )
    }

    /** The track's page on its service, as a link shared to it would be: for a friend to play it too. */
    private fun SoundCloudTrack.pageLink(): String? {
        youTubeVideoId?.let { return "https://music.youtube.com/watch?v=$it" }
        liveVideoId?.let { return "https://www.youtube.com/watch?v=$it" }
        if (urn?.startsWith("yandex:track:") == true) {
            val ids = urn.removePrefix("yandex:track:").split(':')
            val album = ids.getOrNull(1)
            return if (album != null) "https://music.yandex.ru/album/$album/track/${ids[0]}" else "https://music.yandex.ru/track/${ids[0]}"
        }
        if (urn?.startsWith("local:") == true) return null
        return permalinkUrl?.takeIf { it.startsWith("https://soundcloud.com/") }
    }

    /** The service a track is from, as "Итоги" counts them; see PlayLog. */
    private fun sourceOf(urn: String?): String = when {
        urn == null -> ""
        urn.startsWith("yandex:") -> "yandex"
        urn.startsWith(YT_TRACK_URN) || urn.startsWith("ytmusic:") -> "youtube"
        urn.startsWith("soundcloud:") -> "soundcloud"
        urn.startsWith("local:") -> "phone"
        else -> ""
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
            artworkUrl = artworkUrl,
            leadArtist = artists?.firstNotNullOfOrNull { it.username?.takeIf(String::isNotBlank) } ?: artist,
            source = sourceOf(urn)
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
            name = album.title ?: tr("Альбом"),
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
                withContext(Dispatchers.IO) { YouTubeStreams.resolve(context, youTubeId, settingsRepository.ytMusicAuth(), urgent = { false }) }?.let { audio ->
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
            _downloadProgress.update { it - track.id }
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
    fun playShuffled(queue: List<SoundCloudTrack>, fromMix: Boolean = false, source: String? = null) {
        val start = queue.randomOrNull() ?: return
        if (!musicPlayer.shuffleEnabled.value) musicPlayer.toggleShuffle()
        if (fromMix) playMixTrack(start) else playQueuedTrack(start, queue, source = source)
    }

    /** Everything downloaded, shuffled. */
    fun playDownloadsShuffled() {
        val start = favoritesRepository.favorites.value
            .filter { it.downloadState == DownloadState.DOWNLOADED && it.streamUrl != null }
            .randomOrNull() ?: return
        if (!musicPlayer.shuffleEnabled.value) musicPlayer.toggleShuffle()
        playFavorite(start)
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
        // How long a track plays before it counts as listened to, for the history.
        const val HISTORY_HEARD_MS = 5_000L

        // The Yandex radio asks for more once fewer tracks than this are left after the one playing.
        const val RADIO_AHEAD = 3
        // "Моя волна": the listener's own taste, as Rotor's seed.
        const val YANDEX_WAVE_SEED = "user:onyourwave"
        // How many of the wave's tracks are remembered as heard, how many of the latest Rotor is
        // told of, and how many times to ask again when all it offers has been heard.
        const val WAVE_HEARD_KEPT = 500
        const val RADIO_QUEUE_SENT = 150
        const val WAVE_EMPTY_TRIES = 3
        // The pause before asking for an artist's page again after the request broke off.
        const val ARTIST_RETRY_MS = 1_500L
        // How many of a YouTube channel's videos "Все" goes as far as.
        const val CHANNEL_VIDEOS_MAX = 300

        // A radio track that stopped this close to its end was heard to the end, not skipped.
        const val RADIO_FINISHED_SLACK_MS = 5_000L
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
        const val VIDEO_MISS_RETRY_MS = 2 * 60 * 1000L

        // How many tracks the queue goes on by at a time; the last of them goes on again.
        const val AUTO_CONTINUE_TRACKS = 25
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
                    tr("Сессия SoundCloud истекла. Войдите в аккаунт заново.")
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
        if (relayFromLibrary(track.toSoundCloudTrack(), playlist.tracks)) return
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
            _playingFrom.value = "playlist-${playlist.id}"
            _currentPlayingTrack.value = playable
            showPlayerFor(playable)
        }
    }

    fun importLocalTracks(uris: List<android.net.Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val imported = importLocalAudio(context, uris, offlineVideos)
                for (track in imported) {
                    favoritesRepository.addFavoriteTrack(track)
                }
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Error importing local tracks", e)
                _errorMessage.value = tr("Не удалось импортировать треки")
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
                    errorMessage = tr("Не все данные авторизации SoundCloud указаны в настройках")
                )
                return@launch
            }
            if (source == LikesSyncSource.YANDEX && !hasYandex) {
                statusFlow.value = LikesSyncStatus(
                    state = SyncState.FAILED,
                    errorMessage = tr("Укажите рабочий токен Яндекс Музыки в настройках")
                )
                return@launch
            }

            val allTracks = mutableListOf<SoundCloudTrack>()
            var yandexTrackRefs: List<com.example.myapplication.data.YandexLikedTrackRef> = emptyList()
            // The tracks this sync saves; see the end.
            val savedNow = mutableSetOf<Long>()

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
                                savedNow += track.id
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
            } finally {
                // Saved one by one, each went on top; together they go there as the service lists
                // its likes, newest first — stopped halfway too.
                favoritesRepository.moveToTop(allTracks.map { it.id }.filter { it in savedNow })
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
                _yandexError.value = tr("Ошибка поиска: %s", readableMessage(e))
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
                _yandexError.value = tr("Ошибка поиска: %s", readableMessage(e))
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



    /**
     * Plays [queue] from [track]: when [track] is the one playing already, it plays on where it is
     * and only what comes around it changes — a radio started from the track playing used to start
     * that track over. Otherwise as [playQueuedTrack].
     */
    private fun playQueueFrom(track: SoundCloudTrack, queue: List<SoundCloudTrack>) {
        if (musicPlayer.currentTrackId.value != track.id) {
            playQueuedTrack(track, queue)
            return
        }
        viewModelScope.launch {
            val kept = queueMutex.withLock {
                val stubs = queue.map { t -> t.toQueueTrack(localStreamUrl(t.id) ?: resolvedUrls[t.id] ?: placeholderStreamUrl(t)) }
                val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                if (musicPlayer.reorderQueueKeepingCurrent(stubs, index)) {
                    originalQueue = queue
                    _activeQueue.value = queue
                    true
                } else {
                    false
                }
            }
            if (!kept) playQueuedTrack(track, queue)
        }
    }

    fun playQueuedTrack(
        track: SoundCloudTrack,
        customQueue: List<SoundCloudTrack>? = null,
        fromQueueManager: Boolean = false,
        // What the queue is, for the card it came from (see [playingFrom]).
        source: String? = null,
        // The player opened whatever the settings say: a track opened from a link.
        openPlayer: Boolean = false,
        // Picked by a guest listening together: the host's player stays as it is, open or not.
        fromGuest: Boolean = false
    ) {
        if (!fromQueueManager && together.relayPlay(track, customQueue ?: listOf(track))) return
        // Picked in a Yandex radio's queue: played where it is, as a skip would. Rebuilt from the
        // list on screen, the queue lost what the radio had just added, the radio its last track
        // with it, and it took itself for over — nothing more came.
        if (fromQueueManager && yandexRadio != null && musicPlayer.playQueuedItem(track.id)) return
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
                // Moved about in the queue, it is still the queue it was.
                if (!fromQueueManager) _playingFrom.value = source
                _currentPlayingTrack.value = track
                when {
                    openPlayer -> _selectedTrack.value = track
                    fromGuest -> if (_selectedTrack.value != null) _selectedTrack.value = track
                    else -> showPlayerFor(track)
                }
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
                // A download waits for what plays now.
                withContext(Dispatchers.IO) { YouTubeStreams.resolve(context, id, settingsRepository.ytMusicAuth(), urgent = { false }) }
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
                    _errorMessage.value = tr("YouTube не отдал звук этого трека.")
                } else if (isYandex) {
                    _errorMessage.value = tr("Укажите рабочий токен Яндекс Музыки в настройках.")
                } else if (clientId.isBlank()) {
                    _errorMessage.value = tr("Укажите SoundCloud client_id в настройках для загрузки трека")
                } else {
                    _errorMessage.value = tr("Поток для загрузки не нашёлся.")
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
                        _errorMessage.value = tr("Ошибка: Трек скачался не полностью.")
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
            _downloadProgress.update { it - track.id }
        }
    }

    /**
     * A moving picture for [track]. Yandex: the track's music video, else its videoshot (a
     * vertical loop Yandex made to play behind the player). YouTube Music: the song's music
     * video. A Yandex track without either borrows the music video from YouTube, when signed in
     * there — its picture comes through yt-dlp, which needs the session on a VPN.
     */
    private suspend fun findTrackVideo(track: SoundCloudTrack): TrackVideo? =
        withContext(Dispatchers.IO) { offlineVideos.get(track.id) }
            // Yandex's loop, or a video from YouTube: shown only while their kind is. One imported
            // with the track is its own.
            ?.takeIf { it.local || if (it.loop) settingsRepository.videoYandex.value else settingsRepository.videoYouTube.value }
            ?: findOnlineTrackVideo(track)

    /**
     * What a downloaded track needs to be itself offline, besides its sound: its synced lyrics
     * (Yandex keeps them on disk once fetched) and its video, the one the player would find for
     * it. Videos only off metered networks, and only when the player shows them at all; a track
     * that has none is marked, so it isn't looked up on every start.
     */
    private suspend fun downloadExtras(track: SoundCloudTrack) {
        val urn = track.urn.orEmpty()
        if (track.liveVideoId == null) {
            (if (urn.startsWith("yandex:track:")) lyricsRepository.syncedLyrics(urn) else null) ?: lrcLib.syncedLyrics(track)
        }
        if (!settingsRepository.playerVideos.value || !settingsRepository.videoDownload.value || isNetworkMetered()) return
        // Neither kind of video wanted: nothing to look for, and nothing to mark as not found.
        if (!settingsRepository.videoYouTube.value && !settingsRepository.videoYandex.value) return
        val video = findOnlineTrackVideo(track)
        if (video == null) {
            offlineVideos.markNone(track.id)
            return
        }
        val saved = offlineVideos.save(video)
        Log.d("MusicViewModel", "Video of ${track.urn} ${if (saved) "saved" else "not saved"} for offline")
    }

    private fun isNetworkMetered(): Boolean {
        val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
        return connectivity?.isActiveNetworkMetered ?: true
    }

    private suspend fun findOnlineTrackVideo(track: SoundCloudTrack): TrackVideo? {
        val urn = track.urn.orEmpty()
        val youTubeId = track.youTubeVideoId
        return when {
            urn.startsWith("yandex:track:") -> {
                val own = if (settingsRepository.videoYandex.value) yandexVideo(track) else null
                when {
                    own == null -> youTubeVideo(track, null)
                    // The videoshot: made for the player, and it fills it.
                    own.vertical == true -> own
                    // The clip's ten-second loop plays at once; a whole video from YouTube, lined
                    // up with the track, takes over if one is found.
                    else -> {
                        if (_currentPlayingTrack.value?.id == track.id) _trackVideo.value = own
                        youTubeVideo(track, null) ?: own
                    }
                }
            }
            youTubeId != null -> youTubeVideo(track, youTubeId)
            // A broadcast shows itself: its own picture, running along as it airs.
            track.liveVideoId != null -> liveVideo(track, track.liveVideoId!!)
            else -> null
        }
    }

    private fun liveVideo(track: SoundCloudTrack, videoId: String): TrackVideo? {
        if (!settingsRepository.videoYouTube.value) return null
        val stream = YouTubeStreams.resolveLiveVideo(context, videoId, settingsRepository.ytMusicAuth()) ?: return null
        return TrackVideo(trackId = track.id, url = stream.url, loop = true, userAgent = stream.userAgent)
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
        if (!settingsRepository.videoYouTube.value) return null
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
                when {
                    video?.itself == true -> "the track itself"
                    video?.paired == true -> "paired, ${video.segments.size} segments"
                    else -> "by search"
                }
        )
        if (video == null) return null
        val workDir = java.io.File(context.cacheDir, "clip-align")
        // Wanted now while its track plays; the next track's, looked up ahead, waits its turn.
        val urgent = { _currentPlayingTrack.value?.id == track.id }
        return coroutineScope {
            // The track's own sound doesn't depend on the video: it is fetched and decoded while
            // yt-dlp looks for the video's.
            // Nothing to line up when the track is the video, or YouTube mapped the two itself.
            val trackOnsets = if (video.itself || (video.paired && video.segments.isNotEmpty())) {
                null
            } else {
                async { trackOnsets(track, videoId, auth, workDir, urgent) }
            }
            val stream = YouTubeStreams.resolveVideo(context, video.videoId, auth, urgent) ?: run {
                videoLookupMissed += track.id
                return@coroutineScope null
            }
            // Buffered unseen while the sound is compared, but only once the sound is in: over a
            // VPN the two downloads side by side each took as long as both.
            val startBuffering = {
                if (_currentPlayingTrack.value?.id == track.id && trackOnsets != null) {
                    _pendingTrackVideo.value = TrackVideo(
                        track.id, stream.url, loop = false, vertical = false, userAgent = stream.userAgent, ready = false,
                        codec = stream.codec
                    )
                }
            }
            val segments = if (trackOnsets == null) video.segments else {
                // The smallest of the video's sounds first; one a server won't hand out gives way
                // to the next.
                var videoOnsets: FloatArray? = null
                for (sound in stream.audioUrls.take(3)) {
                    videoOnsets = ClipAligner.onsetsOf(
                        ClipAligner.AudioSource(sound, mapOf("User-Agent" to stream.userAgent)),
                        workDir,
                        onFetched = startBuffering
                    )
                    if (videoOnsets != null) break
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
            TrackVideo(
                track.id, stream.url, loop = false, vertical = false, userAgent = stream.userAgent,
                segments = segments, codec = stream.codec
            )
        }
    }

    // Tracks' sound, decoded for lining videos up, kept for a replay or a reopened player.
    private val onsetCache = android.util.LruCache<Long, FloatArray>(8)

    private suspend fun trackOnsets(
        track: SoundCloudTrack,
        videoId: String?,
        auth: com.example.myapplication.data.YtAuth,
        workDir: java.io.File,
        urgent: () -> Boolean
    ): FloatArray? {
        onsetCache.get(track.id)?.let { return it }
        val source = trackSound(track, videoId, auth, urgent) ?: return null
        return ClipAligner.onsetsOf(source, workDir)?.also { onsetCache.put(track.id, it) }
    }

    /** Where to read the track's own sound from: its file, when it is downloaded. */
    private suspend fun trackSound(
        track: SoundCloudTrack,
        videoId: String?,
        auth: com.example.myapplication.data.YtAuth,
        urgent: () -> Boolean
    ): ClipAligner.AudioSource? {
        favoritesRepository.get(track.id)
            ?.takeIf { it.downloadState == DownloadState.DOWNLOADED }
            ?.streamUrl?.takeIf { it.startsWith("/") && java.io.File(it).exists() }
            ?.let { return ClipAligner.AudioSource(it) }
        if (videoId != null) {
            val stream = YouTubeStreams.resolve(context, videoId, auth, urgent) ?: return null
            return ClipAligner.AudioSource(stream.url, mapOf("User-Agent" to stream.userAgent))
        }
        val yandexId = track.urn.orEmpty().removePrefix("yandex:track:").substringBefore(':')
        val token = settingsRepository.yandexTokenValue().takeIf { it.isNotBlank() } ?: return null
        return YandexMusicApi.resolveTrackStream(yandexId, token, lightest = true)?.let { ClipAligner.AudioSource(it) }
    }

    /** The rest of a YouTube channel's videos, a page after another, up to a few hundred. */
    private fun loadMoreChannelVideos() {
        val artist = _currentArtist.value ?: return
        viewModelScope.launch {
            val owner = SoundCloudUser(username = artist.username, permalinkUrl = artist.permalinkUrl)
            try {
                while (sameArtist(_currentArtist.value, artist) && _currentArtistTracks.value.size < CHANNEL_VIDEOS_MAX) {
                    val token = ytChannelContinuation ?: break
                    val (more, next) = com.example.myapplication.data.YouTubeWeb.moreChannelVideos(token, owner, settingsRepository.ytMusicAuth())
                    if (!sameArtist(_currentArtist.value, artist)) return@launch
                    _currentArtistTracks.value = (_currentArtistTracks.value + more).distinctBy { it.id }
                    ytChannelContinuation = next
                    if (more.isEmpty()) break
                }
                _isAllArtistTracksLoaded.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "More videos of ${artist.permalinkUrl} failed", e)
                Toast.makeText(context, tr("Не удалось загрузить остальные видео"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun loadAllArtistTracks(artistId: String, isYandex: Boolean) {
        if (_currentArtist.value?.permalinkUrl?.startsWith(YT_ARTIST_REF) == true && ytChannelContinuation != null) {
            loadMoreChannelVideos()
            return
        }
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
                    _artistError.value = tr("Ошибка: %s", readableMessage(e))
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
                _artistError.value = tr("Ошибка: %s", readableMessage(e, isYandex = isYandex))
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

    // region Yandex radio

    /**
     * Yandex Music's radio (Rotor) playing from a track: the session, which batch each queued
     * track came in (the feedback names it), and every track it has given, so none comes twice.
     */
    private class YandexRadio(
        var sessionId: String,
        val seeds: List<String>,
        val wave: Boolean = false,
        // What was heard before this radio (the wave's earlier sessions): never given again, and
        // told to Rotor as heard.
        private val heardBefore: List<String> = emptyList()
    ) {
        val batchOf = HashMap<Long, String>()
        val given = LinkedHashSet<String>()
        private val givenRaw = HashSet<String>().apply { heardBefore.forEach { add(it.substringBefore(':')) } }

        /** What Rotor is told was heard: the latest of what came before, and all this radio gave. */
        fun heard(): List<String> = (heardBefore + given).takeLast(RADIO_QUEUE_SENT)
        // The last track the radio put in the queue: while it is still there, the queue is the
        // radio's; once another queue has replaced it, the radio stops.
        var tailId: Long? = null
        var refilling = false

        /** Takes [id] ("id" or "id:albumId") as given; false if it was already. */
        fun give(id: String): Boolean {
            if (!givenRaw.add(id.substringBefore(':'))) return false
            given += id
            return true
        }
    }

    private var yandexRadio: YandexRadio? = null
        set(value) {
            field = value
            _yandexWaveOn.value = value?.wave == true
            _yandexRadioOn.value = value != null
        }

    // Whether the queue is a Yandex radio's, the wave's or a track's: its order is the radio's.
    private val _yandexRadioOn = MutableStateFlow(false)
    val yandexRadioOn = _yandexRadioOn.asStateFlow()

    // Whether the radio playing is "Моя волна".
    private val _yandexWaveOn = MutableStateFlow(false)
    val yandexWaveOn = _yandexWaveOn.asStateFlow()

    private val _yandexWaveStarting = MutableStateFlow(false)
    val yandexWaveStarting = _yandexWaveStarting.asStateFlow()

    // What the wave is tuned to: its mood and mode, as seeds for Rotor.
    private val _yandexWavePicks = MutableStateFlow(settingsRepository.yandexWavePicks())
    val yandexWavePicks = _yandexWavePicks.asStateFlow()

    // The track just disliked: leaving it is no skip to tell the radio of, the dislike said it all.
    private var radioDislikedId: Long? = null

    // The track playing's own effects (reverb, speed); see [setTrackFx].
    private val _trackFx = MutableStateFlow(com.example.myapplication.data.TrackFx())
    val trackFx = _trackFx.asStateFlow()

    /** Sets the track playing's effects: kept for it, and applied by the playback service at once. */
    fun setTrackFx(fx: com.example.myapplication.data.TrackFx) {
        val trackId = musicPlayer.currentTrackId.value ?: return
        settingsRepository.setTrackFx(trackId, fx)
        _trackFx.value = fx
    }

    // The track playing as the radio saw it, and how far into it playback got: when it gives way
    // the radio hears whether it was finished or skipped.
    private var radioPlayingId: Long? = null
    private var radioPlayedMs = 0L

    /**
     * Plays Yandex Music's radio from [track], as the Yandex app's "track radio" does: the track,
     * then what the radio puts after it, and more whenever the queue runs low, for as long as it
     * plays. What is heard, skipped or finished goes back to the radio and steers what comes next.
     */
    fun playYandexRadio(track: SoundCloudTrack) {
        val urn = track.urn?.takeIf { it.startsWith("yandex:track:") } ?: return
        val trackId = urn.removePrefix("yandex:track:")
        val seed = "track:" + trackId.substringBefore(':')
        viewModelScope.launch {
            try {
                // The seed goes in as heard: it plays first, and the radio shouldn't offer it again.
                val session = yandexService.rotorSessionNew(
                    YandexRotorSessionRequest(seeds = listOf(seed), queue = listOf(trackId))
                ).result
                val sessionId = session?.radioSessionId ?: throw IllegalStateException(tr("радио не запустилось"))
                val radio = YandexRadio(sessionId, listOf(seed)).apply { give(trackId) }
                val batch = acceptRadioBatch(radio, session.batchId, session.sequence).filterNot { it.id == track.id }
                if (batch.isEmpty()) throw IllegalStateException(tr("радио ничего не предложило"))
                radio.tailId = batch.last().id
                yandexRadio = radio
                Log.d("MusicViewModel", "Yandex radio from $seed: session $sessionId, ${batch.size} tracks")
                playQueueFrom(track, listOf(track) + batch)
                sendRadioFeedback(radio, YandexRotorEvent(type = "radioStarted", timestamp = rotorNow()), session.batchId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Yandex radio from $seed failed", e)
                android.widget.Toast.makeText(context, tr("Не удалось запустить радио: %s", readableMessage(e)), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Tunes the wave to [seed] for [key] ("mood", "mode"), or, picked again, lets it go. A wave
     * playing starts over, tuned so; a tuned wave is a session of its own.
     */
    fun pickYandexWave(key: String, seed: String) {
        val picks = _yandexWavePicks.value.toMutableMap()
        if (picks[key] == seed) picks.remove(key) else picks[key] = seed
        retuneYandexWave(picks)
    }

    /** The wave as it is by itself, untuned. */
    fun resetYandexWave() {
        if (_yandexWavePicks.value.isNotEmpty()) retuneYandexWave(emptyMap())
    }

    private fun retuneYandexWave(picks: Map<String, String>) {
        _yandexWavePicks.value = picks
        settingsRepository.setYandexWavePicks(picks)
        settingsRepository.setYandexWaveSession(null)
        if (_yandexWaveOn.value) playYandexWave()
    }

    /**
     * Tells the radio playing that [track] was liked or unliked ([type] "like" or "unlike"), when it
     * is one the radio gave: the wave leans towards what is liked.
     */
    private fun tellYandexRadio(track: SoundCloudTrack, type: String) {
        val radio = yandexRadio ?: return
        val batch = radio.batchOf[track.id] ?: return
        val id = track.urn?.removePrefix("yandex:track:") ?: return
        sendRadioFeedback(radio, YandexRotorEvent(type = type, timestamp = rotorNow(), trackId = id), batch)
    }

    /**
     * "Не нравится" on the Yandex track playing: marked "Не рекомендовать" in Yandex Music, told to
     * the radio as a dislike, and skipped.
     */
    fun dislikeYandexTrack() {
        val trackId = musicPlayer.currentTrackId.value ?: return
        val track = _activeQueue.value.firstOrNull { it.id == trackId } ?: return
        val id = track.urn?.takeIf { it.startsWith("yandex:track:") }?.removePrefix("yandex:track:") ?: return
        val radio = yandexRadio
        val batch = radio?.batchOf?.get(trackId)
        if (radio != null && batch != null) {
            radioDislikedId = trackId
            sendRadioFeedback(
                radio,
                YandexRotorEvent(
                    type = "dislike",
                    timestamp = rotorNow(),
                    trackId = id,
                    totalPlayedSeconds = radioPlayedMs / 1000.0
                ),
                batch
            )
        }
        viewModelScope.launch {
            try {
                val uid = getYandexUid() ?: return@launch
                yandexService.dislikeTrack(uid, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Yandex dislike failed", e)
            }
        }
        android.widget.Toast.makeText(context, tr("Больше не будет в рекомендациях"), android.widget.Toast.LENGTH_SHORT).show()
        musicPlayer.skipNext()
    }

    /**
     * "Моя волна" asked for from outside the app — its icon's shortcut, the quick settings tile,
     * the widget: played, or played on if it is the one paused; left as it is if it plays already.
     */
    fun startWaveFromOutside() {
        if (settingsRepository.yandexTokenValue().isBlank()) {
            android.widget.Toast.makeText(context, tr("Чтобы слушать волну, войдите в Яндекс Музыку в настройках"), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (_yandexWaveOn.value && yandexRadio != null) {
            if (!musicPlayer.isPlaying.value) musicPlayer.togglePlayPause()
        } else {
            playYandexWave()
        }
    }

    /** "Моя волна": played, or paused and played on where it is when it is the one playing. */
    fun toggleYandexWave() {
        if (_yandexWaveOn.value && yandexRadio != null) {
            musicPlayer.togglePlayPause()
        } else {
            playYandexWave()
        }
    }

    /**
     * Plays "Моя волна": a session from the listener's own seed, its first batch as the queue, more
     * whenever it runs low — the same radio as [playYandexRadio], without a track to start from.
     */
    fun playYandexWave() {
        val seeds = listOf(YANDEX_WAVE_SEED) + _yandexWavePicks.value.values
        _yandexWaveStarting.value = true
        viewModelScope.launch {
            try {
                // Everything the wave has given before is heard: told to Rotor, and kept out here
                // too. Started afresh each time with nothing to go on, it opened on the same tracks.
                val heard = settingsRepository.yandexWaveHeard()
                val sent = heard.takeLast(RADIO_QUEUE_SENT)
                // The last session goes on, as the Yandex app's wave does; a new one if Rotor has
                // let it go.
                var sessionId = settingsRepository.yandexWaveSession()
                var answer = sessionId?.let { last ->
                    runCatching { yandexService.rotorSessionTracks(last, YandexRotorQueueRequest(sent)).result }
                        .getOrNull()
                        ?.takeUnless { it.unknownSession == true || it.terminated == true || it.sequence.isNullOrEmpty() }
                }
                val resumed = answer != null
                if (answer == null) {
                    answer = try {
                        yandexService.rotorSessionNew(YandexRotorSessionRequest(seeds = seeds, queue = sent)).result
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A mood or mode Rotor doesn't take: the wave as it is, rather than none.
                        if (seeds.size == 1) throw e
                        Log.w("MusicViewModel", "Yandex wave $seeds refused, playing it untuned", e)
                        yandexService.rotorSessionNew(YandexRotorSessionRequest(seeds = listOf(YANDEX_WAVE_SEED), queue = sent)).result
                    }
                    sessionId = answer?.radioSessionId
                }
                val session = answer ?: throw IllegalStateException(tr("волна не запустилась"))
                val id = sessionId ?: throw IllegalStateException(tr("волна не запустилась"))
                settingsRepository.setYandexWaveSession(id)
                val radio = YandexRadio(id, seeds, wave = true, heardBefore = heard)
                var batchId = session.batchId
                var batch = acceptRadioBatch(radio, batchId, session.sequence)
                // All of it heard already: more, a few times, before giving up.
                var tries = 0
                while (batch.isEmpty() && tries < WAVE_EMPTY_TRIES) {
                    tries++
                    val more = yandexService.rotorSessionTracks(id, YandexRotorQueueRequest(radio.heard())).result ?: break
                    batchId = more.batchId
                    batch = acceptRadioBatch(radio, batchId, more.sequence)
                }
                if (batch.isEmpty()) throw IllegalStateException(tr("волна ничего нового не предложила"))
                radio.tailId = batch.last().id
                yandexRadio = radio
                Log.d(
                    "MusicViewModel",
                    "Yandex wave: session $id (${if (resumed) "resumed" else "new"}), ${batch.size} tracks, ${heard.size} heard before"
                )
                playQueuedTrack(batch.first(), batch)
                sendRadioFeedback(radio, YandexRotorEvent(type = "radioStarted", timestamp = rotorNow()), batchId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MusicViewModel", "Yandex wave $seeds failed", e)
                android.widget.Toast.makeText(context, tr("Не удалось запустить волну: %s", readableMessage(e)), android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                _yandexWaveStarting.value = false
            }
        }
    }

    /**
     * A batch's tracks the radio hasn't given before, as the app's tracks, each tagged with the
     * batch. The wave's are kept as heard, for the next time it starts.
     */
    private fun acceptRadioBatch(radio: YandexRadio, batchId: String?, sequence: List<YandexRotorItem>?): List<SoundCloudTrack> {
        val accepted = sequence.orEmpty()
            .mapNotNull { it.track }
            .filter { it.available != false }
            .mapNotNull { yandexTrack ->
                val track = yandexTrack.toSoundCloudTrack()
                val id = track.urn?.removePrefix("yandex:track:") ?: return@mapNotNull null
                if (!radio.give(id)) return@mapNotNull null
                if (batchId != null) radio.batchOf[track.id] = batchId
                track
            }
        if (radio.wave && accepted.isNotEmpty()) {
            val ids = accepted.mapNotNull { it.urn?.removePrefix("yandex:track:") }
            val fresh = ids.map { it.substringBefore(':') }.toSet()
            val kept = settingsRepository.yandexWaveHeard().filterNot { it.substringBefore(':') in fresh }
            settingsRepository.setYandexWaveHeard((kept + ids).takeLast(WAVE_HEARD_KEPT))
        }
        return accepted
    }

    /**
     * Asks the radio for more once fewer than [RADIO_AHEAD] tracks are left after [index], and adds
     * them to the end of the queue. A session the radio no longer knows is started again from the
     * same seed, with everything given so far as heard. A queue that has played out meanwhile
     * (more couldn't be had in time) plays on into what came.
     */
    private fun refillYandexRadio(index: Int) {
        val radio = yandexRadio ?: return
        val queue = _activeQueue.value
        if (queue.none { it.id == radio.tailId }) {
            // Another queue has taken over: the radio is over.
            yandexRadio = null
            return
        }
        if (radio.refilling || queue.size - 1 - index >= RADIO_AHEAD) return
        radio.refilling = true
        viewModelScope.launch {
            try {
                var answer = yandexService.rotorSessionTracks(radio.sessionId, YandexRotorQueueRequest(radio.heard())).result
                if (answer == null || answer.unknownSession == true || answer.terminated == true) {
                    answer = yandexService.rotorSessionNew(
                        YandexRotorSessionRequest(seeds = radio.seeds, queue = radio.heard())
                    ).result
                    answer?.radioSessionId?.let {
                        radio.sessionId = it
                        if (radio.wave) settingsRepository.setYandexWaveSession(it)
                    }
                }
                val more = acceptRadioBatch(radio, answer?.batchId, answer?.sequence)
                if (more.isEmpty() || yandexRadio !== radio) return@launch
                queueMutex.withLock {
                    val current = _activeQueue.value
                    if (current.none { it.id == radio.tailId }) return@launch
                    val fresh = more.filter { track -> current.none { it.id == track.id } }
                    if (fresh.isEmpty()) return@launch
                    val extended = current + fresh
                    _activeQueue.value = extended
                    originalQueue = originalQueue + fresh
                    // The items already queued are left as they are; the new ones are added after them.
                    musicPlayer.updateQueue(extended.map { t ->
                        t.toQueueTrack(localStreamUrl(t.id) ?: resolvedUrls[t.id] ?: placeholderStreamUrl(t))
                    })
                    radio.tailId = fresh.last().id
                    Log.d("MusicViewModel", "Yandex radio: ${fresh.size} more tracks, ${extended.size} queued")
                    if (musicPlayer.ended.value) musicPlayer.skipNext()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Yandex radio couldn't get more tracks", e)
            } finally {
                radio.refilling = false
            }
        }
    }

    /**
     * Follows playback for the radio: how far the playing track gets, and each change of track —
     * the one left is reported finished or skipped, the new one started, and the queue topped up.
     */
    private fun followYandexRadio() {
        viewModelScope.launch {
            // The furthest it got: moving to the next track, the player sets the position back to
            // nought a moment before it names the new track, and a track heard to the end would
            // otherwise be reported skipped at its first second.
            musicPlayer.positionMs.collect { position ->
                if (musicPlayer.currentTrackId.value == radioPlayingId) radioPlayedMs = maxOf(radioPlayedMs, position)
            }
        }
        viewModelScope.launch {
            musicPlayer.currentTrackId.collect { trackId ->
                val radio = yandexRadio
                val left = radioPlayingId
                if (radio != null && left != null && left != trackId && left == radioDislikedId) {
                    radioDislikedId = null
                } else if (radio != null && left != null && left != trackId) {
                    val batch = radio.batchOf[left]
                    val track = _activeQueue.value.firstOrNull { it.id == left }
                    val id = track?.urn?.removePrefix("yandex:track:")
                    if (batch != null && id != null) {
                        val duration = track.duration
                        val finished = duration > 0 && radioPlayedMs >= duration - RADIO_FINISHED_SLACK_MS
                        sendRadioFeedback(
                            radio,
                            YandexRotorEvent(
                                type = if (finished) "trackFinished" else "skip",
                                timestamp = rotorNow(),
                                trackId = id,
                                totalPlayedSeconds = radioPlayedMs / 1000.0
                            ),
                            batch
                        )
                    }
                }
                radioPlayingId = trackId
                radioPlayedMs = 0L
                if (radio == null || trackId == null) return@collect
                val index = _activeQueue.value.indexOfFirst { it.id == trackId }
                if (index < 0) return@collect
                val batch = radio.batchOf[trackId]
                val id = _activeQueue.value[index].urn?.removePrefix("yandex:track:")
                if (batch != null && id != null) {
                    sendRadioFeedback(radio, YandexRotorEvent(type = "trackStarted", timestamp = rotorNow(), trackId = id), batch)
                }
                refillYandexRadio(index)
            }
        }
        viewModelScope.launch {
            // The queue played out: a refill that failed (no network as the last track began)
            // would otherwise leave the radio silent for good.
            musicPlayer.ended.collect { ended ->
                if (!ended || yandexRadio == null) return@collect
                val index = _activeQueue.value.indexOfFirst { it.id == musicPlayer.currentTrackId.value }
                if (index >= 0) refillYandexRadio(index)
            }
        }
    }

    private fun sendRadioFeedback(radio: YandexRadio, event: YandexRotorEvent, batchId: String?) {
        viewModelScope.launch {
            try {
                yandexService.rotorSessionFeedback(radio.sessionId, YandexRotorFeedback(event, batchId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Yandex radio feedback ${event.type} failed", e)
            }
        }
    }

    /** Now, as Rotor's events want it: ISO 8601 in UTC with milliseconds. */
    private fun rotorNow(): String =
        java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString()

    /**
     * The notification's and the car's "Нравится" and "Не нравится" ([com.example.myapplication.player.SessionBridge]):
     * what they show follows the track playing, and what they ask is done as the player's own
     * buttons do it. Asked while the app's screen was closed, it is done once the screen is back.
     */
    private fun followSessionButtons() {
        val bridge = com.example.myapplication.player.SessionBridge
        viewModelScope.launch {
            combine(_currentPlayingTrack, favoritesRepository.favorites) { track, saved ->
                track?.let { playing -> playing.id to saved.any { it.id == playing.id } }
            }.collect { bridge.liked.value = it }
        }
        viewModelScope.launch { _yandexWaveOn.collect { bridge.canDislike.value = it } }
        viewModelScope.launch {
            bridge.actions.collect { action ->
                val track = _activeQueue.value.firstOrNull { it.id == action.trackId }
                    ?: _currentPlayingTrack.value?.takeIf { it.id == action.trackId }
                    ?: return@collect
                when (action) {
                    is com.example.myapplication.player.SessionBridge.Action.Like -> toggleFavorite(track)
                    // Only for the track still playing: one pressed long ago is past.
                    is com.example.myapplication.player.SessionBridge.Action.Dislike -> if (musicPlayer.currentTrackId.value == track.id) {
                        if (_yandexWaveOn.value) dislikeYandexTrack() else musicPlayer.skipNext()
                    }
                }
            }
        }
    }

    // region Autocontinue

    private var continuing: Job? = null

    /**
     * The queue down to its last track: the music goes on after it with radio from it, as the
     * services' own apps go on — Yandex Music's radio after its track (which then keeps itself
     * going), YouTube Music's after its, tracks like it after SoundCloud's. Not after a radio, which
     * goes on by itself, nor with the queue on repeat, nor for a guest listening together.
     */
    private fun followQueueEnd() {
        viewModelScope.launch {
            musicPlayer.currentTrackId.collect { trackId ->
                if (trackId == null || !settingsRepository.autoContinue.value || yandexRadio != null || together.isGuest) return@collect
                if (musicPlayer.repeatMode.value != androidx.media3.common.Player.REPEAT_MODE_OFF) return@collect
                val queue = _activeQueue.value
                val index = queue.indexOfFirst { it.id == trackId }
                if (index < 0 || index != queue.lastIndex) return@collect
                continueAfter(queue[index])
            }
        }
    }

    private fun continueAfter(track: SoundCloudTrack) {
        if (continuing?.isActive == true) return
        continuing = viewModelScope.launch {
            try {
                val urn = track.urn.orEmpty()
                val youTubeId = track.youTubeVideoId
                when {
                    urn.startsWith("yandex:track:") -> continueWithYandexRadio(track, urn.removePrefix("yandex:track:"))
                    youTubeId != null -> appendAfter(track, ytMusic.radio(youTubeId))
                    urn.startsWith("soundcloud:tracks:") -> {
                        val clientId = settingsRepository.clientId.value.takeIf { it.isNotBlank() } ?: return@launch
                        appendAfter(track, service.getRelatedTracks(track.id, clientId).collection.filter(::isPlayableTrack))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Couldn't go on after ${track.urn}", e)
            }
        }
    }

    /** Yandex's radio from [track], its tracks after it: from then on the queue is the radio's. */
    private suspend fun continueWithYandexRadio(track: SoundCloudTrack, trackId: String) {
        val seed = "track:" + trackId.substringBefore(':')
        val session = yandexService.rotorSessionNew(
            YandexRotorSessionRequest(seeds = listOf(seed), queue = listOf(trackId))
        ).result ?: return
        val sessionId = session.radioSessionId ?: return
        val radio = YandexRadio(sessionId, listOf(seed)).apply { give(trackId) }
        val added = appendAfter(track, acceptRadioBatch(radio, session.batchId, session.sequence))
        if (added.isEmpty()) return
        radio.tailId = added.last().id
        yandexRadio = radio
        sendRadioFeedback(radio, YandexRotorEvent(type = "radioStarted", timestamp = rotorNow()), session.batchId)
    }

    /**
     * Adds [more] after [last], when it is still the queue's last track (the listener may have put
     * on something else meanwhile); what is queued already isn't added twice. What was added.
     */
    private suspend fun appendAfter(last: SoundCloudTrack, more: List<SoundCloudTrack>): List<SoundCloudTrack> = queueMutex.withLock {
        val current = _activeQueue.value
        if (current.lastOrNull()?.id != last.id) return@withLock emptyList()
        val fresh = more.filter { track -> track.id != last.id && current.none { it.id == track.id } }
            .distinctBy { it.id }
            .take(AUTO_CONTINUE_TRACKS)
        if (fresh.isEmpty()) return@withLock emptyList()
        val extended = current + fresh
        _activeQueue.value = extended
        originalQueue = originalQueue + fresh
        musicPlayer.updateQueue(extended.map { t -> t.toQueueTrack(localStreamUrl(t.id) ?: resolvedUrls[t.id] ?: placeholderStreamUrl(t)) })
        Log.d("MusicViewModel", "Going on after ${last.urn}: ${fresh.size} tracks")
        fresh
    }

    // endregion

    init {
        // Last in the class, so everything it touches is there by the time it runs.
        followYandexRadio()
        followSessionButtons()
        followQueueEnd()
        viewModelScope.launch {
            musicPlayer.currentTrackId.collect { id ->
                _trackFx.value = id?.let(settingsRepository::trackFx) ?: com.example.myapplication.data.TrackFx()
            }
        }
    }

    // endregion
}
