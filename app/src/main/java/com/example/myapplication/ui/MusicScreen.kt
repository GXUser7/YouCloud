package com.example.myapplication.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.myapplication.data.SettingsRepository
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import androidx.media3.common.Player
import com.example.myapplication.data.DownloadState
import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.MixSection
import com.example.myapplication.data.SoundCloudMix
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.Playlist
import com.example.myapplication.data.SoundCloudPlaylist
import com.example.myapplication.data.SoundCloudUser
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import kotlin.math.max
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.os.Message
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import android.content.Context
import android.widget.FrameLayout
import android.widget.Toast
import android.view.ViewGroup
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.absoluteValue
import kotlin.math.pow

@Composable
fun MusicScreen(viewModel: MusicViewModel) {
    val haptic = LocalHapticFeedback.current
    val isLoggedOut by viewModel.isLoggedOut.collectAsState(initial = false)
    val tracks by viewModel.tracks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleEnabled by viewModel.shuffleEnabled.collectAsState()
    val currentTrackTitle by viewModel.currentTrackTitle.collectAsState()
    val selectedTrack by viewModel.selectedTrack.collectAsState()
    val currentPlayingTrack by viewModel.currentPlayingTrack.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val downloadedFolderArtworkUri by viewModel.downloadedFolderArtworkUri.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val oauthToken by viewModel.oauthToken.collectAsState()
    val mixSection by viewModel.mixSection.collectAsState()
    val stationSection by viewModel.stationSection.collectAsState()
    val mixesLoading by viewModel.mixesLoading.collectAsState()
    val loadingMixId by viewModel.loadingMixId.collectAsState()
    val selectedMix by viewModel.selectedMix.collectAsState()
    val playingMixId by viewModel.playingMixId.collectAsState()
    val mixTracks by viewModel.mixTracks.collectAsState()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()
    val playbackDurationMs by viewModel.playbackDurationMs.collectAsState()
    val screen by viewModel.screen.collectAsState()
    val activeQueue by viewModel.activeQueue.collectAsState()
    val userId by viewModel.userId.collectAsState()
    val playlists by viewModel.playlists.collectAsState(initial = emptyList())
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val isClientIdExpired by viewModel.isClientIdExpired.collectAsState()
    val homeSelectedTab by viewModel.homeSelectedTab.collectAsState()
    var showTrackActionsDialog by remember { mutableStateOf(false) }
    val showDebugPercentage by viewModel.showDebugPercentage.collectAsState()
    val downloadedPercentages by viewModel.downloadedPercentages.collectAsState()
    val isAllArtistTracksLoaded by viewModel.isAllArtistTracksLoaded.collectAsState()

    val yandexPlaylists by viewModel.yandexPlaylists.collectAsState()
    val yandexToken by viewModel.yandexToken.collectAsState()
    val hasYandexToken = yandexToken.isNotEmpty()
    val yandexLoginUrl by viewModel.yandexLoginUrl.collectAsState()

    val downloadedTracks = remember(favorites) { favorites.filter { it.downloadState == DownloadState.DOWNLOADED } }

    BackHandler(enabled = selectedTrack != null && !isLoggedOut) {
        viewModel.closeTrack()
    }

    BackHandler(enabled = selectedMix != null && selectedTrack == null && !isLoggedOut) {
        viewModel.closeMix()
    }

    BackHandler(enabled = selectedTrack == null && selectedMix == null && screen != AppScreen.HOME && !isLoggedOut) {
        when (screen) {
            AppScreen.SEARCH -> viewModel.closeSearch()
            AppScreen.DOWNLOADS -> viewModel.closeDownloads()
            AppScreen.SETTINGS -> viewModel.closeSettings()
            AppScreen.MIX_DETAIL -> viewModel.closeMix()
            AppScreen.PLAYLIST_DETAIL -> viewModel.closePlaylist()
            AppScreen.YANDEX_PLAYLIST_DETAIL -> viewModel.deselectYandexPlaylist()
            AppScreen.ARTIST_DETAIL -> viewModel.closeArtist()
            AppScreen.HOME -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ExpressiveBackground()

        yandexLoginUrl?.let { url ->
            YandexLoginDialog(
                loginUrl = url,
                onTokenCaptured = { token ->
                    viewModel.onYandexTokenCaptured(token)
                },
                onDismiss = {
                    viewModel.cancelYandexLogin()
                }
            )
        }

        if (isLoggedOut) {
            SoundCloudLoginScreen(viewModel = viewModel)
        } else {
            when (screen) {
                AppScreen.HOME -> HomeScreen(
                    downloadedTracks = downloadedTracks,
                    downloadedFolderArtworkUri = downloadedFolderArtworkUri,
                    mixSection = mixSection,
                    stationSection = stationSection,
                    mixesLoading = mixesLoading,
                    loadingMixId = loadingMixId,
                    hasOauthToken = oauthToken.isNotBlank(),
                    mixesError = if (screen == AppScreen.HOME) errorMessage else null,
                    clientId = clientId,
                    playingMixId = playingMixId,
                    isPlaying = isPlaying,
                    playlists = playlists,
                    isClientIdExpired = isClientIdExpired,
                    onAutoRefreshClientId = viewModel::tryAutoRefreshClientId,
                    selectedTab = homeSelectedTab,
                    onTabSelected = viewModel::setHomeSelectedTab,
                    onOpenSearch = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.openSearch()
                    },
                    onOpenDownloads = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.openDownloads()
                    },
                    onOpenSettings = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.openSettings()
                    },
                    onPlayMix = { mix ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.playMix(mix)
                    },
                    onOpenMix = { mix ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.openMix(mix)
                    },
                    onReloadMixes = viewModel::loadMixes,
                    onCreatePlaylist = viewModel::createPlaylist,
                    onDeletePlaylist = viewModel::deletePlaylist,
                    onOpenPlaylist = viewModel::openPlaylist,
                    yandexPlaylists = yandexPlaylists,
                    hasYandexToken = hasYandexToken,
                    onOpenYandexPlaylist = { playlist ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.selectYandexPlaylist(playlist)
                    }
                )

                AppScreen.SEARCH -> {
                    val searchInYandex by viewModel.searchInYandex.collectAsState()
                    val yandexSearchQuery by viewModel.yandexSearchQuery.collectAsState()
                    val yandexTracks by viewModel.yandexTracks.collectAsState()
                    val yandexLoading by viewModel.yandexLoading.collectAsState()
                    val yandexError by viewModel.yandexError.collectAsState()
                    SearchScreen(
                        query = searchQuery,
                        tracks = tracks,
                        favorites = favorites,
                        currentTrackId = currentTrackId,
                        downloadProgress = downloadProgress,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onBack = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.closeSearch()
                        },
                        onQueryChange = viewModel::onSearchQueryChange,
                        onPlayTrack = { track ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playQueuedTrack(track)
                        },
                        onFavoriteClick = { track ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(track)
                        },
                        searchInYandex = searchInYandex,
                        onSearchSourceChanged = viewModel::setSearchSource,
                        yandexQuery = yandexSearchQuery,
                        yandexTracks = yandexTracks,
                        yandexLoading = yandexLoading,
                        yandexError = yandexError,
                        onYandexQueryChange = viewModel::onYandexSearchQueryChange,
                        hasYandexToken = hasYandexToken,
                        onOpenSettings = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openSettings()
                        }
                    )
                }

                AppScreen.DOWNLOADS -> DownloadsScreen(
                    tracks = downloadedTracks,
                    folderArtworkUri = downloadedFolderArtworkUri,
                    currentTrackId = currentTrackId,
                    downloadProgress = downloadProgress,
                    isPlaying = isPlaying,
                    onBack = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.closeDownloads()
                    },
                    onChangeArtwork = viewModel::updateDownloadedFolderArtworkUri,
                    onPlayTrack = { track ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.playFavorite(track)
                    },
                    onDeleteDownload = { track ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deleteDownloadedTrack(track)
                    },
                    onImportTracks = viewModel::importLocalTracks,
                    showDebugPercentage = showDebugPercentage,
                    downloadedPercentages = downloadedPercentages
                )

                AppScreen.SETTINGS -> {
                    val soundcloudLikesSyncStatus by viewModel.soundcloudLikesSyncStatus.collectAsState()
                    val yandexLikesSyncStatus by viewModel.yandexLikesSyncStatus.collectAsState()
                    SettingsScreen(
                        settingsRepository = viewModel.settingsRepo,
                        soundcloudLikesSyncStatus = soundcloudLikesSyncStatus,
                        yandexLikesSyncStatus = yandexLikesSyncStatus,
                        startSoundCloudLikesSync = viewModel::startSoundCloudLikesSync,
                        startYandexLikesSync = viewModel::startYandexLikesSync,
                        stopLikesSync = viewModel::stopLikesSync,
                        resetSoundCloudLikesSyncStatus = viewModel::resetSoundCloudLikesSyncStatus,
                        resetYandexLikesSyncStatus = viewModel::resetYandexLikesSyncStatus,
                        onBack = viewModel::closeSettings,
                        onRelogin = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.logout()
                        },
                        onClearCache = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.refreshMixesAndStations()
                        },
                        onYandexLoginClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.startYandexLogin()
                        },
                        onYandexLogoutClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.logoutYandex()
                        }
                    )
                }

                AppScreen.PLAYLIST_DETAIL -> {
                    selectedPlaylist?.let { playlist ->
                        PlaylistDetailScreen(
                            playlist = playlist,
                            currentTrackId = currentTrackId,
                            downloadProgress = downloadProgress,
                            isPlaying = isPlaying,
                            onBack = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.closePlaylist()
                            },
                            onPlayTrack = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playPlaylistTrack(playlist, track)
                            },
                            onRemoveTrack = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.removeTrackFromPlaylist(playlist.id, track.id)
                            },
                            onChangeArtwork = { uri ->
                                viewModel.updatePlaylistArtwork(playlist.id, uri)
                            },
                            onMoveDownloadedToDownloads = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.moveDownloadedTracksToDownloads(playlist)
                            }
                        )
                    }
                }

                AppScreen.YANDEX_PLAYLIST_DETAIL -> {
                    val selectedYandexPlaylist by viewModel.selectedYandexPlaylist.collectAsState()
                    val yandexPlaylistLoading by viewModel.yandexPlaylistLoading.collectAsState()
                    selectedYandexPlaylist?.let { playlist ->
                        YandexPlaylistDetailScreen(
                            playlist = playlist,
                            isLoading = yandexPlaylistLoading,
                            currentTrackId = currentTrackId,
                            downloadProgress = downloadProgress,
                            isPlaying = isPlaying,
                            favorites = favorites,
                            onBack = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deselectYandexPlaylist()
                            },
                            onPlayTrack = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playQueuedTrack(track, playlist.tracks)
                            },
                            onFavoriteClick = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleFavorite(track)
                            },
                            onChangeArtwork = { uri ->
                                viewModel.updateYandexPlaylistArtwork(playlist.id, uri)
                            }
                        )
                    }
                }

                AppScreen.ARTIST_DETAIL -> {
                    val currentArtist by viewModel.currentArtist.collectAsState()
                    val currentArtistTracks by viewModel.currentArtistTracks.collectAsState()
                    val currentArtistPlaylists by viewModel.currentArtistPlaylists.collectAsState()
                    val artistLoading by viewModel.artistLoading.collectAsState()
                    val artistError by viewModel.artistError.collectAsState()
                    val selectedArtistPlaylist by viewModel.selectedArtistPlaylist.collectAsState()
                    currentArtist?.let { artist ->
                        ArtistDetailScreen(
                            artist = artist,
                            tracks = currentArtistTracks,
                            playlists = currentArtistPlaylists,
                            isLoading = artistLoading,
                            error = artistError,
                            currentTrackId = currentTrackId,
                            downloadProgress = downloadProgress,
                            isPlaying = isPlaying,
                            favorites = favorites,
                            onBack = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.closeArtist()
                            },
                            onPlayTrack = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val queue = selectedArtistPlaylist?.tracks ?: currentArtistTracks
                                viewModel.playQueuedTrack(track, queue)
                            },
                            onFavoriteClick = { track ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleFavorite(track)
                            },
                            onPlaylistClick = { playlist ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.selectArtistPlaylist(playlist)
                            },
                            selectedPlaylist = selectedArtistPlaylist,
                            onDeselectPlaylist = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.deselectArtistPlaylist()
                            },
                            isAllTracksLoaded = isAllArtistTracksLoaded,
                            onLoadAllTracks = {
                                val isYandex = artist.permalinkUrl?.startsWith("yandex") == true || artist.id.toString().startsWith("yandex:")
                                viewModel.loadAllArtistTracks(artist.id.toString(), isYandex)
                            }
                        )
                    }
                }

                AppScreen.MIX_DETAIL -> Unit // Handled by selectedMix visibility
            }

            AnimatedVisibility(
                visible = selectedMix != null && selectedTrack == null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                selectedMix?.let { mix ->
                    MixDetailScreen(
                        mix = mix,
                        tracks = mixTracks,
                        currentTrackId = currentTrackId,
                        favorites = favorites,
                        downloadProgress = downloadProgress,
                        isPlaying = isPlaying,
                        onBack = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.closeMix()
                        },
                        onPlayTrack = { track ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playMixTrack(track)
                        },
                        onFavoriteClick = { track ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(track)
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = currentTrackTitle != null && selectedTrack == null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .navigationBarsPadding()
            ) {
                PlayerBar(
                    title = currentTrackTitle.orEmpty(),
                    isPlaying = isPlaying,
                    onTogglePlay = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.togglePlayPause()
                    },
                    onOpen = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentPlayingTrack?.let(viewModel::openTrack)
                    }
                )
            }

            AnimatedVisibility(
                visible = selectedTrack != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                selectedTrack?.let { track ->
                    val favorite = favorites.firstOrNull { it.id == track.id }
                    TrackDetailScreen(
                        track = track,
                        activeQueue = activeQueue,
                        onReorderQueue = viewModel::reorderActiveQueue,
                        onPlayTrackFromQueue = { qTrack ->
                            viewModel.playQueuedTrack(qTrack, activeQueue)
                        },
                        isFavorite = favorite != null,
                        favoriteTrack = favorite,
                        downloadState = favorite?.downloadState,
                        isPlaying = isPlaying,
                        repeatMode = repeatMode,
                        shuffleEnabled = shuffleEnabled,
                        positionMs = playbackPositionMs,
                        durationMs = max(playbackDurationMs, track.duration),
                        onBack = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.closeTrack()
                        },
                        onTogglePlay = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.togglePlayPause()
                        },
                        onSeek = viewModel::seekTo,
                        onFavoriteClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(track)
                        },
                        onPrevious = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.skipPrevious()
                        },
                        onNext = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.skipNext()
                        },
                        onRepeat = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.cycleRepeatMode()
                        },
                        onShuffle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleShuffle()
                        },
                        onDeleteDownload = { fav ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.deleteDownloadedTrack(fav)
                        },
                        onLongPressCover = {
                            showTrackActionsDialog = true
                        },
                        onArtistClick = {
                            track.user?.let { user ->
                                viewModel.openArtistDetails(
                                    userId = user.id ?: 0L,
                                    permalinkUrl = user.permalinkUrl,
                                    username = user.username,
                                    trackUrn = track.urn
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    val context = LocalContext.current
    if (showTrackActionsDialog && selectedTrack != null) {
        // Capture to local val to prevent NPE if state changes (#3)
        val capturedTrack = selectedTrack ?: return
        TrackActionsDialog(
            track = capturedTrack,
            playlists = playlists,
            onDismiss = { showTrackActionsDialog = false },
            onAddToPlaylist = { playlist ->
                viewModel.addTrackToPlaylist(playlist.id, capturedTrack)
            },
            onCreatePlaylist = { name ->
                viewModel.createPlaylist(name)
            },
            onShare = {
                capturedTrack.permalinkUrl?.let { url ->
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, url)
                        type = "text/plain"
                    }
                    val shareIntent = Intent.createChooser(sendIntent, null)
                    context.startActivity(shareIntent)
                }
            },
            onRedownload = {
                viewModel.redownloadTrack(capturedTrack)
            }
        )
    }
}

@Composable
private fun HomeScreen(
    downloadedTracks: List<FavoriteTrack>,
    downloadedFolderArtworkUri: String?,
    mixSection: MixSection?,
    stationSection: MixSection?,
    mixesLoading: Boolean,
    loadingMixId: String?,
    hasOauthToken: Boolean,
    mixesError: String?,
    clientId: String,
    playingMixId: String?,
    isPlaying: Boolean,
    playlists: List<Playlist>,
    isClientIdExpired: Boolean,
    onAutoRefreshClientId: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayMix: (SoundCloudMix) -> Unit,
    onOpenMix: (SoundCloudMix) -> Unit,
    onReloadMixes: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    yandexPlaylists: List<SoundCloudPlaylist> = emptyList(),
    hasYandexToken: Boolean = false,
    onOpenYandexPlaylist: (SoundCloudPlaylist) -> Unit = {}
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val pagerState = rememberPagerState(initialPage = selectedTab) { 2 }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != selectedTab) {
            onTabSelected(pagerState.currentPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Моя музыка",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                }
            }

            if (clientId.isBlank()) {
                ClientIdWarningCard(onOpenSettings = onOpenSettings)
            } else if (isClientIdExpired) {
                ClientIdExpiredWarningCard(onOpenSettings = onOpenSettings, onAutoRefresh = onAutoRefreshClientId)
            }

            SearchLaunchCard(onClick = onOpenSearch)

            SegmentedControl(
                items = listOf("Миксы", "Плейлисты"),
                selectedIndex = pagerState.currentPage,
                onSelectedIndexChanged = { index ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTabSelected(index)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (page == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        MixesSection(
                            mixSection = mixSection,
                            isLoading = mixesLoading,
                            loadingMixId = loadingMixId,
                            hasOauthToken = hasOauthToken,
                            errorMessage = mixesError,
                            playingMixId = playingMixId,
                            isPlaying = isPlaying,
                            onPlayMix = onPlayMix,
                            onOpenMix = onOpenMix,
                            onReload = onReloadMixes,
                            onOpenSettings = onOpenSettings
                        )
                    }
 
                    if (stationSection != null) {
                        item {
                            MixesSection(
                                mixSection = stationSection,
                                isLoading = mixesLoading,
                                loadingMixId = loadingMixId,
                                hasOauthToken = hasOauthToken,
                                errorMessage = null,
                                playingMixId = playingMixId,
                                isPlaying = isPlaying,
                                onPlayMix = onPlayMix,
                                onOpenMix = onOpenMix,
                                onReload = {},
                                onOpenSettings = onOpenSettings
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        DownloadedFolderCard(
                            count = downloadedTracks.size,
                            artworkUri = downloadedFolderArtworkUri,
                            onClick = onOpenDownloads
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Плейлисты",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCreatePlaylistDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Создать плейлист",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (playlists.isEmpty() && (!hasYandexToken || yandexPlaylists.isEmpty())) {
                        item {
                            EmptyState("Создайте свой первый плейлист, нажав кнопку выше.")
                        }
                    } else {
                        if (playlists.isNotEmpty()) {
                            items(playlists, key = { "playlist-${it.id}" }) { playlist ->
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist) },
                                    onDelete = { onDeletePlaylist(playlist.id) }
                                )
                            }
                        }

                        if (hasYandexToken && yandexPlaylists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Плейлисты Яндекс Музыки",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }

                            items(yandexPlaylists, key = { "yandex-playlist-${it.id}" }) { playlist ->
                                YandexPlaylistCard(
                                    playlist = playlist,
                                    onClick = { onOpenYandexPlaylist(playlist) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Создать плейлист") },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text("Название плейлиста") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistNameInput.isNotBlank()) {
                            onCreatePlaylist(playlistNameInput)
                            showCreatePlaylistDialog = false
                            playlistNameInput = ""
                        }
                    }
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun SettingsScreen(
    settingsRepository: SettingsRepository,
    soundcloudLikesSyncStatus: LikesSyncStatus,
    yandexLikesSyncStatus: LikesSyncStatus,
    startSoundCloudLikesSync: () -> Unit,
    startYandexLikesSync: () -> Unit,
    stopLikesSync: () -> Unit,
    resetSoundCloudLikesSyncStatus: () -> Unit,
    resetYandexLikesSyncStatus: () -> Unit,
    onBack: () -> Unit,
    onRelogin: () -> Unit,
    onClearCache: () -> Unit,
    onYandexLoginClick: () -> Unit,
    onYandexLogoutClick: () -> Unit
) {
    val yandexToken by settingsRepository.yandexToken.collectAsState()
    val hasYandexToken = yandexToken.isNotEmpty()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopBar(title = "Настройки", onBack = onBack)
        }

        // 1. SoundCloud Sync Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Синхронизация SoundCloud",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Text(
                        text = "Автоматически добавляйте все треки, которые вы лайкнули на SoundCloud, в любимые и скачивайте их для прослушивания офлайн.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    if (soundcloudLikesSyncStatus.state != SyncState.IDLE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val statusText = when (soundcloudLikesSyncStatus.state) {
                            SyncState.FETCHING_LIKES -> "Получение лайкнутых треков..."
                            SyncState.DOWNLOADING -> "Скачивание треков: ${soundcloudLikesSyncStatus.currentTrackIndex} из ${soundcloudLikesSyncStatus.totalTracks}"
                            SyncState.COMPLETED -> "Синхронизация завершена!"
                            SyncState.FAILED -> "Ошибка: ${soundcloudLikesSyncStatus.errorMessage}"
                            else -> ""
                        }
                        
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (soundcloudLikesSyncStatus.state == SyncState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        
                        if (soundcloudLikesSyncStatus.state == SyncState.DOWNLOADING) {
                            Text(
                                text = "Скачивается: ${soundcloudLikesSyncStatus.currentTrackTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            val progress = if (soundcloudLikesSyncStatus.totalTracks > 0) {
                                (soundcloudLikesSyncStatus.downloadedCount + soundcloudLikesSyncStatus.failedCount).toFloat() / soundcloudLikesSyncStatus.totalTracks
                            } else 0f
                            
                            CustomWavyProgressIndicator(
                                progress = progress,
                                color = Color(0xFFFF5500),
                                trackColor = Color(0xFFFF5500).copy(alpha = 0.2f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )
                            
                            Text(
                                text = "Успешно: ${soundcloudLikesSyncStatus.downloadedCount} | Ошибки: ${soundcloudLikesSyncStatus.failedCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (soundcloudLikesSyncStatus.state == SyncState.FETCHING_LIKES || soundcloudLikesSyncStatus.state == SyncState.DOWNLOADING) {
                            Button(
                                onClick = stopLikesSync,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Остановить", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = startSoundCloudLikesSync,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF5500)
                                )
                            ) {
                                Text(
                                    text = if (soundcloudLikesSyncStatus.state == SyncState.COMPLETED || soundcloudLikesSyncStatus.state == SyncState.FAILED) "Синхронизировать заново" else "Синхронизировать лайки",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            if (soundcloudLikesSyncStatus.state == SyncState.COMPLETED || soundcloudLikesSyncStatus.state == SyncState.FAILED) {
                                Button(
                                    onClick = resetSoundCloudLikesSyncStatus,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Text("Сбросить", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Yandex Music Sync Card (only if logged in)
        if (hasYandexToken) {
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Синхронизация Яндекс.Музыки",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Text(
                            text = "Автоматически добавляйте все треки из вашего плейлиста 'Мне нравится' на Яндекс.Музыке в любимые и скачивайте их для прослушивания офлайн.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        
                        if (yandexLikesSyncStatus.state != SyncState.IDLE) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val statusText = when (yandexLikesSyncStatus.state) {
                                SyncState.FETCHING_LIKES -> "Получение лайкнутых треков..."
                                SyncState.DOWNLOADING -> "Скачивание треков: ${yandexLikesSyncStatus.currentTrackIndex} из ${yandexLikesSyncStatus.totalTracks}"
                                SyncState.COMPLETED -> "Синхронизация завершена!"
                                SyncState.FAILED -> "Ошибка: ${yandexLikesSyncStatus.errorMessage}"
                                else -> ""
                            }
                            
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (yandexLikesSyncStatus.state == SyncState.FAILED) MaterialTheme.colorScheme.error else Color(0xFFF2C200)
                            )
                            
                            if (yandexLikesSyncStatus.state == SyncState.DOWNLOADING) {
                                Text(
                                    text = "Скачивается: ${yandexLikesSyncStatus.currentTrackTitle}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                
                                val progress = if (yandexLikesSyncStatus.totalTracks > 0) {
                                    (yandexLikesSyncStatus.downloadedCount + yandexLikesSyncStatus.failedCount).toFloat() / yandexLikesSyncStatus.totalTracks
                                } else 0f
                                
                                CustomWavyProgressIndicator(
                                    progress = progress,
                                    color = Color(0xFFFFD600),
                                    trackColor = Color(0xFFFFD600).copy(alpha = 0.2f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                )
                                
                                Text(
                                    text = "Успешно: ${yandexLikesSyncStatus.downloadedCount} | Ошибки: ${yandexLikesSyncStatus.failedCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (yandexLikesSyncStatus.state == SyncState.FETCHING_LIKES || yandexLikesSyncStatus.state == SyncState.DOWNLOADING) {
                                Button(
                                    onClick = stopLikesSync,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Остановить", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = startYandexLikesSync,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFFD600),
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text(
                                        text = if (yandexLikesSyncStatus.state == SyncState.COMPLETED || yandexLikesSyncStatus.state == SyncState.FAILED) "Синхронизировать заново" else "Синхронизировать лайки",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                if (yandexLikesSyncStatus.state == SyncState.COMPLETED || yandexLikesSyncStatus.state == SyncState.FAILED) {
                                    Button(
                                        onClick = resetYandexLikesSyncStatus,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    ) {
                                        Text("Сбросить", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Accounts Cards (SoundCloud and Yandex)
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Аккаунт SoundCloud",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Вы вошли в систему SoundCloud. Если у вас возникли проблемы или вы хотите сменить аккаунт, вы можете перезайти.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRelogin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF5500)
                        )
                    ) {
                        Text("Войти заново", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Яндекс.Музыка",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (hasYandexToken) {
                        Text(
                            text = "Вы вошли в систему Яндекс.Музыка. Если вы хотите сменить аккаунт или выйти из него, используйте кнопку ниже.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onYandexLogoutClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Text("Выйти из аккаунта", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            text = "Подключите свой аккаунт Яндекс.Музыки, чтобы искать любимые треки, синхронизировать лайки и воспроизводить свои плейлисты.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onYandexLoginClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD600),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Войти через Яндекс", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }


        item {
            val context = LocalContext.current
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Данные и кэш",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Сброс локального кэша миксов и станций. Это принудительно обновит списки треков из SoundCloud.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onClearCache()
                            Toast.makeText(context, "Кэш миксов и станций успешно очищен", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("Сбросить кэш и обновить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            val eqEnabled by settingsRepository.equalizerEnabled.collectAsState()
            val eqPreset by settingsRepository.equalizerPreset.collectAsState()
            EqualizerCard(
                settingsRepository = settingsRepository,
                eqEnabled = eqEnabled,
                eqPreset = eqPreset
            )
        }

        item {
            val showDebugPercentageVal by settingsRepository.showDebugPercentage.collectAsState()
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Дебаг информация",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Показывать процент скачивания треков в память на экране загрузок.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showDebugPercentageVal,
                        onCheckedChange = { settingsRepository.setShowDebugPercentage(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    tracks: List<SoundCloudTrack>,
    favorites: List<FavoriteTrack>,
    currentTrackId: Long?,
    downloadProgress: Map<Long, Float> = emptyMap(),
    isPlaying: Boolean = false,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    searchInYandex: Boolean,
    onSearchSourceChanged: (Boolean) -> Unit,
    yandexQuery: String,
    yandexTracks: List<SoundCloudTrack>,
    yandexLoading: Boolean,
    yandexError: String?,
    onYandexQueryChange: (String) -> Unit,
    hasYandexToken: Boolean,
    onOpenSettings: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }

    val activeQuery = if (searchInYandex) yandexQuery else query
    val activeTracks = if (searchInYandex) yandexTracks else tracks
    val activeLoading = if (searchInYandex) yandexLoading else isLoading
    val activeError = if (searchInYandex) yandexError else errorMessage

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopBar(title = "Поиск", onBack = onBack)
        }

        item {
            SearchField(
                query = activeQuery,
                onQueryChange = { newQuery ->
                    if (searchInYandex) {
                        onYandexQueryChange(newQuery)
                    } else {
                        onQueryChange(newQuery)
                    }
                },
                focusRequester = focusRequester
            )
        }

        item {
            SegmentedControl(
                items = listOf("SoundCloud", "Яндекс Музыка"),
                selectedIndex = if (searchInYandex) 1 else 0,
                onSelectedIndexChanged = { index ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSearchSourceChanged(index == 1)
                }
            )
        }

        if (activeLoading) {
            item {
                CustomWavyProgressIndicator(
                    progress = null,
                    color = if (searchInYandex) Color(0xFFF2C200) else MaterialTheme.colorScheme.primary,
                    trackColor = (if (searchInYandex) Color(0xFFF2C200) else MaterialTheme.colorScheme.primary).copy(alpha = 0.2f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }
        }

        if (activeError != null) {
            item { MessageCard(activeError) }
        }

        if (searchInYandex && !hasYandexToken) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFFF2C200),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Войдите в Яндекс Музыку в настройках, чтобы искать треки.",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(16.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF2C200),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("Перейти в настройки", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            if (activeTracks.isEmpty() && !activeLoading) {
                item {
                    EmptyState(
                        if (activeQuery.isBlank()) "Напиши, что хочешь услышать."
                        else "Ничего не нашлось."
                    )
                }
            } else {
                items(activeTracks, key = { "${if (searchInYandex) "yandex" else "sc"}-search-${it.id}" }) { track ->
                    val favorite = favorites.firstOrNull { it.id == track.id }
                    val progress = downloadProgress[track.id]
                    TrackCard(
                        track = track,
                        isFavorite = favorite != null,
                        isSelected = track.id == currentTrackId,
                        downloadState = favorite?.downloadState,
                        progress = progress,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onFavoriteClick = { onFavoriteClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsScreen(
    tracks: List<FavoriteTrack>,
    folderArtworkUri: String?,
    currentTrackId: Long?,
    downloadProgress: Map<Long, Float> = emptyMap(),
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onChangeArtwork: (String?) -> Unit,
    onPlayTrack: (FavoriteTrack) -> Unit,
    onDeleteDownload: (FavoriteTrack) -> Unit,
    onImportTracks: (List<android.net.Uri>) -> Unit,
    showDebugPercentage: Boolean = false,
    downloadedPercentages: Map<Long, Int> = emptyMap()
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onChangeArtwork(uri.toString())
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            onImportTracks(uris)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TopBar(title = "Скачанные", onBack = onBack)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                FolderHero(
                    count = tracks.size,
                    artworkUri = folderArtworkUri,
                    onChangeArtwork = { imagePicker.launch(arrayOf("image/*")) },
                    onImportLocalTrack = { audioPicker.launch(arrayOf("audio/*")) }
                )
            }

            if (tracks.isEmpty()) {
                item { EmptyState("Здесь появятся треки, которые ты сохранишь на устройство.") }
            } else {
                items(tracks, key = { "downloaded-${it.id}" }) { track ->
                    val progress = downloadProgress[track.id]
                    val debugPct = downloadedPercentages[track.id]
                    DownloadedTrackCard(
                        track = track,
                        isSelected = track.id == currentTrackId,
                        progress = progress,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onDeleteDownload = { onDeleteDownload(track) },
                        showDebugPercentage = showDebugPercentage,
                        debugPercentage = debugPct
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpressiveBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "expressive_bg")
    
    val rotationPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation1"
    )
    val rotationPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 240000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation2"
    )
    val rotationPhase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 280000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation3"
    )
    val rotationPhase4 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 320000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation4"
    )
    val rotationPhase5 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation5"
    )

    val colorPrimary = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val colorTertiary = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.04f)
    val colorSecondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val steps = 72  // Reduced from 120 for performance (#15)

            // 1. Center: 8-petaled flower shape (primary)
            drawContext.canvas.save()
            drawContext.canvas.translate(width * 0.5f, height * 0.5f)
            drawContext.canvas.rotate(rotationPhase1)
            val flowerPath = Path()
            val r1 = minOf(width, height) * 0.28f
            for (i in 0..steps) {
                val theta = (i * 2f * Math.PI / steps).toFloat()
                val rFactor = 0.85f + 0.15f * kotlin.math.cos(8f * theta)
                val r = r1 * rFactor
                val x = r * kotlin.math.cos(theta)
                val y = r * kotlin.math.sin(theta)
                if (i == 0) {
                    flowerPath.moveTo(x, y)
                } else {
                    flowerPath.lineTo(x, y)
                }
            }
            flowerPath.close()
            drawPath(path = flowerPath, color = colorPrimary)
            drawContext.canvas.restore()

            // 2. Top-Left: Squircle shape (tertiary) - slightly larger than medium
            drawContext.canvas.save()
            drawContext.canvas.translate(width * 0.12f, height * 0.14f)
            drawContext.canvas.rotate(rotationPhase2)
            val squirclePath = Path()
            val r2 = minOf(width, height) * 0.28f
            for (i in 0..steps) {
                val theta = (i * 2f * Math.PI / steps).toFloat()
                val cosT = kotlin.math.cos(theta)
                val sinT = kotlin.math.sin(theta)
                val cosT4 = cosT.absoluteValue.pow(4.5f)
                val sinT4 = sinT.absoluteValue.pow(4.5f)
                val r = r2 * (1f / (cosT4 + sinT4).pow(1f / 4.5f)) * 0.85f
                val x = r * cosT
                val y = r * sinT
                if (i == 0) {
                    squirclePath.moveTo(x, y)
                } else {
                    squirclePath.lineTo(x, y)
                }
            }
            squirclePath.close()
            drawPath(path = squirclePath, color = colorTertiary)
            drawContext.canvas.restore()

            // 3. Top-Right: 5-petaled star shape (secondary) - medium
            drawContext.canvas.save()
            drawContext.canvas.translate(width * 0.88f, height * 0.14f)
            drawContext.canvas.rotate(rotationPhase3)
            val starPath = Path()
            val r3 = minOf(width, height) * 0.23f
            for (i in 0..steps) {
                val theta = (i * 2f * Math.PI / steps).toFloat()
                val rFactor = 0.8f + 0.2f * kotlin.math.cos(5f * theta)
                val r = r3 * rFactor
                val x = r * kotlin.math.cos(theta)
                val y = r * kotlin.math.sin(theta)
                if (i == 0) {
                    starPath.moveTo(x, y)
                } else {
                    starPath.lineTo(x, y)
                }
            }
            starPath.close()
            drawPath(path = starPath, color = colorSecondary)
            drawContext.canvas.restore()

            // 4. Bottom-Left: 6-pointed star/flower shape (tertiary) - smaller than medium, not small
            drawContext.canvas.save()
            drawContext.canvas.translate(width * 0.15f, height * 0.86f)
            drawContext.canvas.rotate(rotationPhase4)
            val path4 = Path()
            val r4 = minOf(width, height) * 0.19f
            for (i in 0..steps) {
                val theta = (i * 2f * Math.PI / steps).toFloat()
                val rFactor = 0.8f + 0.2f * kotlin.math.cos(6f * theta)
                val x = r4 * rFactor * kotlin.math.cos(theta)
                val y = r4 * rFactor * kotlin.math.sin(theta)
                if (i == 0) {
                    path4.moveTo(x, y)
                } else {
                    path4.lineTo(x, y)
                }
            }
            path4.close()
            drawPath(path = path4, color = colorTertiary)
            drawContext.canvas.restore()

            // 5. Bottom-Right: Squircle shape (secondary) - huge
            drawContext.canvas.save()
            drawContext.canvas.translate(width * 0.85f, height * 0.86f)
            drawContext.canvas.rotate(rotationPhase5)
            val path5 = Path()
            val r5 = minOf(width, height) * 0.45f
            for (i in 0..steps) {
                val theta = (i * 2f * Math.PI / steps).toFloat()
                val cosT = kotlin.math.cos(theta)
                val sinT = kotlin.math.sin(theta)
                val cosT4 = cosT.absoluteValue.pow(3f)
                val sinT4 = sinT.absoluteValue.pow(3f)
                val r = r5 * (1f / (cosT4 + sinT4).pow(1f / 3f)) * 0.85f
                val x = r * cosT
                val y = r * sinT
                if (i == 0) {
                    path5.moveTo(x, y)
                } else {
                    path5.lineTo(x, y)
                }
            }
            path5.close()
            drawPath(path = path5, color = colorSecondary)
            drawContext.canvas.restore()
        }
    }
}

@Composable
private fun SearchLaunchCard(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(26.dp))
            Text(
                text = "Найти трек",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, focusRequester: FocusRequester) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(30.dp),
        placeholder = { Text("Найти трек") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun DownloadedFolderCard(count: Int, artworkUri: String?, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FolderArtwork(artworkUri, 82.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Скачанные",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "$count треков на устройстве",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun FolderHero(
    count: Int,
    artworkUri: String?,
    onChangeArtwork: () -> Unit,
    onImportLocalTrack: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(42.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FolderArtwork(artworkUri, 220.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Скачанные",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = "$count треков доступны оффлайн",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onChangeArtwork()
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Default.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Обложка", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                if (onImportLocalTrack != null) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onImportLocalTrack()
                        },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Импорт", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalIconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onBack()
        }) {
            Icon(Icons.Default.ArrowBack, contentDescription = null)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MixesSection(
    mixSection: MixSection?,
    isLoading: Boolean,
    loadingMixId: String?,
    hasOauthToken: Boolean,
    errorMessage: String?,
    playingMixId: String?,
    isPlaying: Boolean,
    onPlayMix: (SoundCloudMix) -> Unit,
    onOpenMix: (SoundCloudMix) -> Unit,
    onReload: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = mixSection?.title ?: "Your Mixes",
            subtitle = when {
                !hasOauthToken -> "Добавь OAuth-токен в настройках, чтобы увидеть персональные миксы."
                isLoading -> "Загружаем подборку your-moods…"
                mixSection == null -> "Подборка пока не загрузилась."
                else -> "${mixSection.mixes.size} миксов от SoundCloud"
            }
        )

        if (!hasOauthToken) {
            ElevatedCard(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Text(
                        text = "Открыть настройки и вставить OAuth",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            return
        }

        // Removed LinearProgressIndicator to prevent layout jumping

        if (errorMessage != null && mixSection == null) {
            MessageCard(errorMessage)
            Button(onClick = onReload) {
                Text("Повторить")
            }
        }

        mixSection?.mixes?.let { mixes ->
            if (mixes.isEmpty()) {
                EmptyState("Подборка your-moods пуста.")
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    items(mixes, key = { it.id }) { mix ->
                        val isMixPlaying = isPlaying && playingMixId == mix.id
                        MixCard(
                            mix = mix,
                            isLoading = loadingMixId == mix.id,
                            isMixPlaying = isMixPlaying,
                            onPlay = { onPlayMix(mix) },
                            onClick = { onOpenMix(mix) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MixCard(
    mix: SoundCloudMix,
    isLoading: Boolean,
    isMixPlaying: Boolean,
    onPlay: () -> Unit,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .width(190.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMixPlaying) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            },
            contentColor = if (isMixPlaying) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                if (mix.artworkUrl != null) {
                    TrackArtwork(
                        artworkUrl = mix.artworkUrl,
                        size = 166.dp,
                        isPlaying = isMixPlaying,
                        useMorphing = true,
                        fallbackShape = RoundedCornerShape(24.dp)
                    )
                } else {
                    Surface(
                        color = if (isMixPlaying) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.size(166.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isMixPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(
                    text = mix.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = mix.description?.takeIf { it.isNotBlank() } ?: "${mix.trackIds.size} треков",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMixPlaying) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MixDetailScreen(
    mix: SoundCloudMix,
    tracks: List<SoundCloudTrack>,
    currentTrackId: Long?,
    favorites: List<FavoriteTrack>,
    downloadProgress: Map<Long, Float> = emptyMap(),
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit
) {
    // Map for O(1) favorite lookup (#37)
    val favoritesMap = remember(favorites) { favorites.associateBy { it.id } }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            FilledTonalIconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        TrackArtwork(mix.artworkUrl, 240.dp, useMorphing = false)
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = mix.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.Center
                        )
                        
                        if (!mix.description.isNullOrBlank()) {
                            Text(
                                text = mix.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
                            )
                        }
                    }
                }

                // Removed CircularProgressIndicator to prevent layout jumping

                items(tracks, key = { "mix-track-${it.id}" }) { track ->
                    val favorite = favoritesMap[track.id]
                    val progress = downloadProgress[track.id]
                    TrackCard(
                        track = track,
                        isFavorite = favorite != null,
                        isSelected = track.id == currentTrackId,
                        downloadState = favorite?.downloadState,
                        progress = progress,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onFavoriteClick = { onFavoriteClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackCard(
    track: SoundCloudTrack,
    isFavorite: Boolean,
    isSelected: Boolean = false,
    downloadState: DownloadState? = null,
    progress: Float? = null,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(track.artworkUrl, size = 68.dp, isPlaying = isSelected && isPlaying)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: "Unknown Track",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.user?.username ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (downloadState == DownloadState.DOWNLOADING) {
                    Spacer(modifier = Modifier.height(4.dp))
                    CustomWavyProgressIndicator(
                        progress = progress,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        trackColor = (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary).copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                    )
                }
            }
            
            val favoriteScale by animateFloatAsState(
                targetValue = if (isFavorite) 1.2f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy)
            )

            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .graphicsLayer {
                        scaleX = favoriteScale
                        scaleY = favoriteScale
                    }
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else if (isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadedTrackCard(
    track: FavoriteTrack,
    isSelected: Boolean = false,
    progress: Float? = null,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onDeleteDownload: () -> Unit,
    showDebugPercentage: Boolean = false,
    debugPercentage: Int? = null
) {
    val haptic = LocalHapticFeedback.current
    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackArtwork(track.artworkUrl, 64.dp, isPlaying = isSelected && isPlaying)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showDebugPercentage && debugPercentage != null) {
                        Text(
                            text = "• $debugPercentage%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (debugPercentage < 90) {
                                Color.Red
                            } else if (isSelected) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            if (track.downloadState == DownloadState.DOWNLOADING) {
                CustomCircularWavyProgressIndicator(
                    progress = progress,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                IconButton(onClick = onDeleteDownload) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
@Composable
private fun TrackDetailScreen(
    track: SoundCloudTrack,
    activeQueue: List<SoundCloudTrack>,
    onReorderQueue: (Int, Int) -> Unit,
    onPlayTrackFromQueue: (SoundCloudTrack) -> Unit,
    isFavorite: Boolean,
    favoriteTrack: FavoriteTrack?,
    downloadState: DownloadState?,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavoriteClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onDeleteDownload: (FavoriteTrack) -> Unit,
    onLongPressCover: () -> Unit,
    onArtistClick: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        }
    }
    val haptic = LocalHapticFeedback.current
    var lastVibratedRatio by remember(track.id) { mutableStateOf(0f) }
    var sliderProgress by remember { mutableStateOf<Float?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val blurRadius by animateDpAsState(
        targetValue = if (showQueue) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "blurRadius"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(showQueue) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            if (dragAmount.y < -40f && !showQueue) {
                                showQueue = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(blurRadius)
            ) {
                ExpressiveBackground()
                // Rich dynamic gradient overlaying the expressive background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .blur(blurRadius),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DownloadBadge(downloadState ?: DownloadState.NONE)
                        if (favoriteTrack?.downloadState == DownloadState.DOWNLOADED) {
                            FilledTonalIconButton(
                                onClick = { onDeleteDownload(favoriteTrack) },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete download")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
                
                val artworkScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1f else 0.85f,
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "artworkScale"
                )
                val artworkPressedScale = remember { Animatable(1f) }
                
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = artworkScale * artworkPressedScale.value
                            scaleY = artworkScale * artworkPressedScale.value
                        }
                        .pointerInput(track.permalinkUrl) {
                            detectTapGestures(
                                onLongPress = {
                                    // 1. Heavy vibration click
                                    try {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                            vibrator?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_HEAVY_CLICK))
                                        } else {
                                            @Suppress("DEPRECATION")
                                            vibrator?.vibrate(80)
                                        }
                                    } catch (e: Exception) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    // 2. Visual spring pulse animation
                                    coroutineScope.launch {
                                        artworkPressedScale.animateTo(1.12f, animationSpec = tween(150))
                                        artworkPressedScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))

                                        // 3. Trigger bottom sheet dialog callback
                                        onLongPressCover()
                                    }
                                }
                            )
                        }
                ) {
                    TrackArtwork(track.artworkUrl, size = 320.dp, useMorphing = false)
                }
                
                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = track.title ?: "Unknown Track",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-1).sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = track.user?.username ?: "Unknown Artist",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onArtistClick()
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                 Slider(
                    value = sliderProgress ?: if (durationMs > 0) {
                        positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()
                    } else {
                        0f
                    },
                    onValueChange = { ratio ->
                        sliderProgress = ratio
                        if (kotlin.math.abs(ratio - lastVibratedRatio) >= 0.02f) {
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    vibrator?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator?.vibrate(10)
                                }
                            } catch (e: Exception) {
                                // fallback
                            }
                            lastVibratedRatio = ratio
                        }
                    },
                    onValueChangeFinished = {
                        sliderProgress?.let { ratio ->
                            onSeek((ratio * durationMs).toLong())
                        }
                        sliderProgress = null
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(positionMs),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        formatDuration(durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(32.dp))
                    }
                    ExpressivePlayButton(
                        isPlaying = isPlaying,
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .height(84.dp)
                            .width(128.dp),
                        iconSize = 42.dp
                    )
                    FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next track", modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveControlIconButton(
                        selected = isFavorite,
                        onClick = onFavoriteClick,
                        icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(68.dp)
                    )

                    ExpressiveControlIconButton(
                        selected = repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = onRepeat,
                        icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(68.dp)
                    )

                    ExpressiveControlIconButton(
                        selected = shuffleEnabled,
                        onClick = onShuffle,
                        icon = Icons.Default.Shuffle,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.size(68.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }

            AnimatedVisibility(
                visible = showQueue,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                QueueManagerPanel(
                    activeQueue = activeQueue,
                    currentTrack = track,
                    isPlaying = isPlaying,
                    onDismiss = { showQueue = false },
                    onReorder = onReorderQueue,
                    onPlayTrack = onPlayTrackFromQueue
                )
            }
        }
    }
}

@Composable
private fun QueueManagerPanel(
    activeQueue: List<SoundCloudTrack>,
    currentTrack: SoundCloudTrack,
    isPlaying: Boolean,
    onDismiss: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    BackHandler(onBack = onDismiss)

    Card(
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
        ),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {}
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        if (dragAmount.y > 10f) {
                                            onDismiss()
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Drag handle with adequate touch target (#40)
                            Box(
                                modifier = Modifier
                                    .size(40.dp, 5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Очередь воспроизведения",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                )
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = "Close")
                                }
                            }
                        }
                    }

                    val lazyListState = rememberLazyListState()

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeQueue.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Очередь пуста",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                itemsIndexed(activeQueue, key = { _, track -> "queue-${track.id}" }) { index, trackItem ->
                    val currentIndex by rememberUpdatedState(index)
                    val isCurrent = trackItem.id == currentTrack.id
                    val isThisDragged = currentIndex == draggedIndex
                    val scale by animateFloatAsState(
                        targetValue = if (isThisDragged) 1.05f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "dragScale"
                    )
                    val elevation = if (isThisDragged) 8.dp else 0.dp

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationY = if (isThisDragged) dragOffsetY else 0f
                                scaleX = scale
                                scaleY = scale
                                alpha = if (draggedIndex != null && !isThisDragged) 0.65f else 1f
                            }
                            .animateItem()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .pointerInput(trackItem.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                draggedIndex = currentIndex
                                                dragOffsetY = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                
                                                // Use actual item height from layout info (#28)
                                                val layoutInfo = lazyListState.layoutInfo
                                                val itemHeightPx = layoutInfo.visibleItemsInfo
                                                    .firstOrNull()?.size?.toFloat()
                                                    ?: with(density) { 82.dp.toPx() }
                                                
                                                val currentDragIdx = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                                if (dragOffsetY > itemHeightPx * 0.7f) {
                                                    val nextIdx = (currentDragIdx + 1).coerceAtMost(activeQueue.lastIndex)
                                                    if (nextIdx != currentDragIdx) {
                                                        onReorder(currentDragIdx, nextIdx)
                                                        draggedIndex = nextIdx
                                                        dragOffsetY -= itemHeightPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                } else if (dragOffsetY < -itemHeightPx * 0.7f) {
                                                    val prevIdx = (currentDragIdx - 1).coerceAtLeast(0)
                                                    if (prevIdx != currentDragIdx) {
                                                        onReorder(currentDragIdx, prevIdx)
                                                        draggedIndex = prevIdx
                                                        dragOffsetY += itemHeightPx
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                                // Auto-scroll near edges (#27)
                                                val viewportHeight = layoutInfo.viewportSize.height.toFloat()
                                                val touchY = layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.index == currentDragIdx }
                                                    ?.let { it.offset + it.size / 2f + dragOffsetY }
                                                    ?: (viewportHeight / 2f)
                                                val scrollThreshold = viewportHeight * 0.15f
                                                if (touchY > viewportHeight - scrollThreshold) {
                                                    lazyListState.dispatchRawDelta(itemHeightPx * 0.3f)
                                                } else if (touchY < scrollThreshold) {
                                                    lazyListState.dispatchRawDelta(-itemHeightPx * 0.3f)
                                                }
                                            },
                                            onDragEnd = {
                                                draggedIndex = null
                                                dragOffsetY = 0f
                                            },
                                            onDragCancel = {
                                                draggedIndex = null
                                                dragOffsetY = 0f
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Reorder,
                                    contentDescription = "Drag to reorder",
                                    tint = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TrackArtwork(
                                artworkUrl = trackItem.artworkUrl,
                                size = 50.dp,
                                isPlaying = isCurrent && isPlaying
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        if (!isCurrent) {
                                            onPlayTrack(trackItem)
                                        }
                                    }
                            ) {
                                Text(
                                    text = trackItem.title ?: "Unknown Track",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = trackItem.user?.username ?: "Unknown Artist",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            }
                        }
                    }
                }
            }
        }
    }
@Composable
private fun FolderArtwork(artworkUri: String?, size: Dp) {
    if (artworkUri.isNullOrBlank()) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(size * 0.55f),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        SubcomposeAsyncImage(
            model = artworkUri,
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(if (size > 100.dp) 36.dp else 18.dp)),
            contentScale = ContentScale.Crop,
            loading = {
                Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.55f),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            },
            error = {
                Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(size * 0.55f),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}

class MorphingArtworkShape(
    private val progress: Float,
    private val rotationPhase: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = minOf(width, height) / 2f

        val steps = 72  // Reduced from 120 for performance (#15)
        for (i in 0..steps) {
            val theta = (i * 2f * Math.PI / steps).toFloat()

            // 1. Calculate squircle radius at theta (representing rounded square).
            val cosT = kotlin.math.cos(theta)
            val sinT = kotlin.math.sin(theta)
            val cosT4 = cosT.absoluteValue.pow(4.5f)
            val sinT4 = sinT.absoluteValue.pow(4.5f)
            val rSquare = 1f / (cosT4 + sinT4).pow(1f / 4.5f)

            // 2. Calculate flower radius at theta (representing an 8-petaled flower).
            val rFlower = 1f + 0.12f * kotlin.math.cos(8f * (theta - rotationPhase))

            // 3. Interpolate between squircle and flower based on progress
            val rRaw = (1f - progress) * rSquare + progress * rFlower

            // Normalize so it always fits comfortably
            val rNormalized = rRaw * 0.82f

            val x = centerX + rNormalized * maxRadius * cosT
            val y = centerY + rNormalized * maxRadius * sinT

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
private fun TrackArtwork(
    artworkUrl: String?,
    size: Dp,
    isPlaying: Boolean = false,
    useMorphing: Boolean = true,
    fallbackShape: Shape? = null
) {
    val clipShape = if (useMorphing) {
        val morphProgress by animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            label = "morphProgress"
        )
        val infiniteTransition = rememberInfiniteTransition(label = "rotation")
        val rotationPhase by if (isPlaying) {
            infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = (2f * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 16000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "rotationPhase"
            )
        } else {
            remember { mutableStateOf(0f) }
        }

        remember(morphProgress, rotationPhase) {
            MorphingArtworkShape(morphProgress, rotationPhase)
        }
    } else {
        fallbackShape ?: RoundedCornerShape(if (size > 100.dp) 36.dp else 18.dp)
    }

    AsyncImage(
        model = artworkUrl?.replace("large", "t500x500"),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(clipShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentScale = ContentScale.Crop
    )
}


@Composable
private fun DownloadBadge(state: DownloadState) {
    Surface(
        color = when (state) {
            DownloadState.DOWNLOADED -> MaterialTheme.colorScheme.primaryContainer
            DownloadState.DOWNLOADING -> MaterialTheme.colorScheme.secondaryContainer
            DownloadState.FAILED -> MaterialTheme.colorScheme.errorContainer
            DownloadState.NONE -> MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(100.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state == DownloadState.DOWNLOADING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            }
            Text(
                text = when (state) {
                    DownloadState.DOWNLOADED -> "Загружено"
                    DownloadState.DOWNLOADING -> "Загрузка"
                    DownloadState.FAILED -> "Ошибка"
                    DownloadState.NONE -> "Онлайн"
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MessageCard(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun PlayerBar(
    title: String,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.95f),
        shape = RoundedCornerShape(36.dp),
        tonalElevation = 12.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onOpen)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpressivePlayButton(
                    isPlaying = isPlaying,
                    onClick = onTogglePlay,
                    modifier = Modifier.size(52.dp),
                    iconSize = 24.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isPlaying) "Сейчас играет" else "На паузе",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
fun ExpressiveControlIconButton(
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedColor: Color,
    unselectedColor: Color,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val cornerSize by animateDpAsState(
        targetValue = if (selected) 16.dp else 50.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cornerSize"
    )

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        animationSpec = tween(400),
        label = "containerColor"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) contentColorFor(selectedColor) else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(400),
        label = "contentColor"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        color = containerColor,
        shape = RoundedCornerShape(cornerSize),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
@Composable
fun ExpressivePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp
) {
    val haptic = LocalHapticFeedback.current
    val cornerSize by animateDpAsState(
        targetValue = if (isPlaying) 20.dp else 50.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cornerSize"
    )

    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
        animationSpec = tween(450),
        label = "containerColor"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isPlaying) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(450),
        label = "iconColor"
    )

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        color = containerColor,
        shape = RoundedCornerShape(cornerSize),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun ClientIdWarningCard(onOpenSettings: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    ElevatedCard(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onOpenSettings()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Не указан SoundCloud client_id",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Нажмите, чтобы открыть настройки и ввести рабочий ключ, иначе поиск и воспроизведение работать не будут.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun ClientIdExpiredWarningCard(onOpenSettings: () -> Unit, onAutoRefresh: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SoundCloud client_id устарел",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Текущий ключ SoundCloud больше недействителен. Попробуйте обновить его автоматически или укажите рабочий вручную.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenSettings()
                    }
                ) {
                    Text("Настройки", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAutoRefresh()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Обновить автоматически")
                }
            }
        }
    }
}

@Composable
private fun EqualizerPresetChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EqualizerCard(
    settingsRepository: SettingsRepository,
    eqEnabled: Boolean,
    eqPreset: String
) {
    val eqInfo = remember { settingsRepository.getEqualizerInfo() }
    var bandLevels by remember(eqPreset) {
        mutableStateOf(
            (0 until eqInfo.numBands).map { band ->
                settingsRepository.getBandLevel(band)
            }
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Эквалайзер",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Настройка звуковых частот",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = { settingsRepository.setEqualizerEnabled(it) }
                )
            }

            if (eqEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Пресеты",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val presetList = listOf("Flat", "Bass Boost", "Rock", "Pop", "Classical", "Jazz", "Vocal")
                    items(presetList) { preset ->
                        val isSelected = eqPreset == preset
                        EqualizerPresetChip(
                            text = preset,
                            selected = isSelected,
                            onClick = {
                                settingsRepository.setEqualizerPreset(preset)
                                val presetBands = when (preset) {
                                    "Bass Boost" -> listOf(600, 400, 0, 0, 0)
                                    "Rock" -> listOf(500, 300, -300, 200, 500)
                                    "Pop" -> listOf(-200, -100, 300, 200, -200)
                                    "Classical" -> listOf(500, 300, -200, 400, 400)
                                    "Jazz" -> listOf(400, 200, -200, 200, 500)
                                    "Vocal" -> listOf(-200, 0, 500, 400, 0)
                                    else -> listOf(0, 0, 0, 0, 0) // Flat
                                }
                                for (i in 0 until eqInfo.numBands) {
                                    val level = presetBands.getOrNull(i) ?: 0
                                    settingsRepository.setBandLevel(i, level)
                                }
                                bandLevels = (0 until eqInfo.numBands).map { band ->
                                    settingsRepository.getBandLevel(band)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Полосы частот",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                for (i in 0 until eqInfo.numBands) {
                    val frequency = eqInfo.frequencies.getOrNull(i) ?: 0
                    val currentLevel = bandLevels.getOrNull(i) ?: 0
                    
                    val minVal = eqInfo.minLevel.toFloat()
                    val maxVal = eqInfo.maxLevel.toFloat()

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (frequency >= 1000) "${frequency / 1000} kHz" else "$frequency Hz",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${currentLevel / 100} dB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = currentLevel.toFloat(),
                            onValueChange = { newValue ->
                                val levelInt = newValue.toInt()
                                settingsRepository.setBandLevel(i, levelInt)
                                if (eqPreset != "Custom") {
                                    settingsRepository.setEqualizerPreset("Custom")
                                }
                                bandLevels = bandLevels.toMutableList().apply {
                                    this[i] = levelInt
                                }
                            },
                            valueRange = minVal..maxVal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SoundCloudLoginScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val isLoggingIn by viewModel.isLoggingIn.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    var isWebViewLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SoundCloud",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF5500)
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Войдите в свой аккаунт, чтобы настроить приложение",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                if (loginError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = loginError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // WebView Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(Color.White)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val container = FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        val mainWebView = WebView(ctx)
                        mainWebView.apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                setSupportMultipleWindows(true)
                                javaScriptCanOpenWindowsAutomatically = true
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"
                            }
                            
                            // Enable cookies and third-party cookies to persist Google/SoundCloud logins
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            var popupWebView: WebView? = null

                            webChromeClient = object : WebChromeClient() {
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: Message?
                                ): Boolean {
                                    popupWebView?.let { container.removeView(it) }

                                    val newWebView = WebView(ctx).apply {
                                        layoutParams = FrameLayout.LayoutParams(
                                            FrameLayout.LayoutParams.MATCH_PARENT,
                                            FrameLayout.LayoutParams.MATCH_PARENT
                                        )
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            setSupportMultipleWindows(true)
                                            javaScriptCanOpenWindowsAutomatically = true
                                            userAgentString = mainWebView.settings.userAgentString
                                        }
                                        
                                        // Enable cookies and third-party cookies for Google login popups
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.setAcceptCookie(true)
                                        cookieManager.setAcceptThirdPartyCookies(this, true)
                                        
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onCloseWindow(window: WebView?) {
                                                container.removeView(window)
                                                popupWebView = null
                                            }
                                        }
                                        
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                isWebViewLoading = true
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                isWebViewLoading = false
                                                CookieManager.getInstance().flush()
                                            }

                                            override fun shouldInterceptRequest(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): WebResourceResponse? {
                                                if (request != null) {
                                                    val url = request.url
                                                    val clientId = url.getQueryParameter("client_id")
                                                    val headers = request.requestHeaders
                                                    val authHeader = headers["Authorization"] ?: headers["authorization"]

                                                    if (!clientId.isNullOrBlank() && !authHeader.isNullOrBlank() && authHeader.startsWith("OAuth ", ignoreCase = true)) {
                                                        val token = authHeader.removePrefix("OAuth ").trim()
                                                        if (token.isNotEmpty()) {
                                                            post {
                                                                viewModel.onCredentialsCaptured(clientId, token)
                                                            }
                                                        }
                                                    }
                                                }
                                                return super.shouldInterceptRequest(view, request)
                                            }
                                        }
                                    }

                                    popupWebView = newWebView
                                    container.addView(newWebView)

                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    transport?.webView = newWebView
                                    resultMsg?.sendToTarget()
                                    return true
                                }

                                override fun onCloseWindow(window: WebView?) {
                                    container.removeView(window)
                                    if (window == popupWebView) {
                                        popupWebView = null
                                    }
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isWebViewLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isWebViewLoading = false
                                    CookieManager.getInstance().flush()
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    if (request != null) {
                                        val url = request.url
                                        val clientId = url.getQueryParameter("client_id")
                                        val headers = request.requestHeaders
                                        val authHeader = headers["Authorization"] ?: headers["authorization"]

                                        if (!clientId.isNullOrBlank() && !authHeader.isNullOrBlank() && authHeader.startsWith("OAuth ", ignoreCase = true)) {
                                            val token = authHeader.removePrefix("OAuth ").trim()
                                            if (token.isNotEmpty()) {
                                                post {
                                                    viewModel.onCredentialsCaptured(clientId, token)
                                                }
                                            }
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }
                            loadUrl("https://soundcloud.com/signin")
                        }

                        container.addView(mainWebView)
                        container
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isWebViewLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF5500))
                    }
                }
            }
        }

        // Authentication Overlay
        // Authentication Overlay
        if (isLoggingIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFF5500))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Авторизация в SoundCloud...",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelectedIndexChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(27.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val width = maxWidth / items.size
            
            val offset by animateDpAsState(
                targetValue = width * selectedIndex,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "segmentedOffset"
            )

            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(width)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(23.dp))
            )

            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, text ->
                    val isSelected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(150),
                        label = "segmentedTextColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectedIndexChanged(index)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, onDelete: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "playlistScale"
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (playlist.artworkUrl != null) {
                FolderArtwork(playlist.artworkUrl, 82.dp)
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.size(82.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${playlist.tracks.size} треков",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDelete()
                }
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: Playlist,
    currentTrackId: Long?,
    downloadProgress: Map<Long, Float> = emptyMap(),
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onPlayTrack: (FavoriteTrack) -> Unit,
    onRemoveTrack: (FavoriteTrack) -> Unit,
    onChangeArtwork: (String?) -> Unit,
    onMoveDownloadedToDownloads: () -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onChangeArtwork(uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TopBar(title = playlist.name, onBack = onBack)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .clickable {
                                imagePicker.launch(arrayOf("image/*"))
                            }
                    ) {
                        if (playlist.artworkUrl != null) {
                            FolderArtwork(playlist.artworkUrl, 220.dp)
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }

                        // Premium edit overlay
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(12.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "${playlist.tracks.size} треков",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val hasDownloaded = playlist.tracks.any { it.downloadState == DownloadState.DOWNLOADED }
                        if (hasDownloaded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onMoveDownloadedToDownloads,
                                shape = RoundedCornerShape(16.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text("Переместить скачанные в Скачанное", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (playlist.tracks.isEmpty()) {
                item {
                    EmptyState("Здесь пока нет треков. Зажмите обложку трека в плеере, чтобы добавить его.")
                }
            } else {
                items(playlist.tracks, key = { "playlist-${playlist.id}-${it.id}" }) { track ->
                    val progress = downloadProgress[track.id]
                    DownloadedTrackCard(
                        track = track,
                        isSelected = track.id == currentTrackId,
                        progress = progress,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onDeleteDownload = { onRemoveTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun YandexPlaylistCard(
    playlist: SoundCloudPlaylist,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "playlistScale"
    )

    Card(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(36.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isLikedPlaylist = playlist.id == -100L
            if (playlist.artworkUrl != null) {
                FolderArtwork(playlist.artworkUrl, 82.dp)
            } else {
                val containerColor = if (isLikedPlaylist) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                val tintColor = if (isLikedPlaylist) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                val icon = if (isLikedPlaylist) Icons.Default.Favorite else Icons.Default.QueueMusic
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.size(82.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = tintColor,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title ?: "Без названия",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "${playlist.trackCount} треков",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun YandexPlaylistDetailScreen(
    playlist: SoundCloudPlaylist,
    isLoading: Boolean,
    currentTrackId: Long?,
    downloadProgress: Map<Long, Float> = emptyMap(),
    isPlaying: Boolean = false,
    favorites: List<FavoriteTrack>,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    onChangeArtwork: (String?) -> Unit
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onChangeArtwork(uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            TopBar(title = playlist.title ?: "Плейлист", onBack = onBack)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .clickable {
                                imagePicker.launch(arrayOf("image/*"))
                            }
                    ) {
                        val isLikedPlaylist = playlist.id == -100L
                        if (playlist.artworkUrl != null) {
                            FolderArtwork(playlist.artworkUrl, 220.dp)
                        } else {
                            val containerColor = if (isLikedPlaylist) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            val tintColor = if (isLikedPlaylist) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                            val icon = if (isLikedPlaylist) Icons.Default.Favorite else Icons.Default.QueueMusic
                            Surface(
                                color = containerColor,
                                shape = RoundedCornerShape(32.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = tintColor,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(12.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val isLikedPlaylist = playlist.id == -100L
                        Text(
                            text = playlist.title ?: "Без названия",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )
                        Text(
                            text = "${playlist.trackCount} треков",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CustomCircularWavyProgressIndicator(
                            progress = null,
                            color = if (playlist.id == -100L) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            } else if (playlist.tracks.isEmpty()) {
                item {
                    EmptyState("Здесь пока нет треков.")
                }
            } else {
                items(playlist.tracks, key = { "yandex-playlist-detail-${playlist.id}-${it.id}" }) { track ->
                    val favorite = favorites.firstOrNull { it.id == track.id }
                    val progress = downloadProgress[track.id]
                    TrackCard(
                        track = track,
                        isFavorite = favorite != null,
                        isSelected = track.id == currentTrackId,
                        downloadState = favorite?.downloadState,
                        progress = progress,
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onFavoriteClick = { onFavoriteClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistDetailScreen(
    artist: SoundCloudUser,
    tracks: List<SoundCloudTrack>,
    playlists: List<SoundCloudPlaylist>,
    isLoading: Boolean,
    error: String?,
    currentTrackId: Long?,
    downloadProgress: Map<Long, Float> = emptyMap(),
    isPlaying: Boolean = false,
    favorites: List<FavoriteTrack>,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    onPlaylistClick: (SoundCloudPlaylist) -> Unit,
    selectedPlaylist: SoundCloudPlaylist? = null,
    onDeselectPlaylist: () -> Unit = {},
    isAllTracksLoaded: Boolean = false,
    onLoadAllTracks: () -> Unit = {}
) {
    if (selectedPlaylist != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TopBar(title = selectedPlaylist.title ?: "Альбом", onBack = onDeselectPlaylist)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .clip(RoundedCornerShape(32.dp))
                        ) {
                            if (selectedPlaylist.artworkUrl != null) {
                                FolderArtwork(selectedPlaylist.artworkUrl, 200.dp)
                            } else {
                                Surface(
                                    color = if (selectedPlaylist.permalinkUrl?.contains("yandex") == true) Color(0xFFF2C200).copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(32.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Album,
                                            contentDescription = null,
                                            tint = if (selectedPlaylist.permalinkUrl?.contains("yandex") == true) Color(0xFFF2C200) else MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(64.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = selectedPlaylist.title ?: "Без названия",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "${selectedPlaylist.tracks.size} треков",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (selectedPlaylist.tracks.isEmpty()) {
                    item {
                        EmptyState("Здесь пока нет треков.")
                    }
                } else {
                    items(selectedPlaylist.tracks, key = { "album-${selectedPlaylist.id}-${it.id}" }) { track ->
                        val favorite = favorites.firstOrNull { it.id == track.id }
                        val progress = downloadProgress[track.id]
                        TrackCard(
                            track = track,
                            isFavorite = favorite != null,
                            isSelected = track.id == currentTrackId,
                            downloadState = favorite?.downloadState,
                            progress = progress,
                            isPlaying = isPlaying,
                            onClick = { onPlayTrack(track) },
                            onFavoriteClick = { onFavoriteClick(track) }
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TopBar(title = "", onBack = onBack)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            if (artist.avatarUrl != null) {
                                AsyncImage(
                                    model = artist.avatarUrl.orEmpty().replace("large", "t500x500"),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = artist.username.orEmpty(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-1).sp
                        )

                        if ((artist.followersCount ?: 0) > 0) {
                            Text(
                                text = "${artist.followersCount} подписчиков",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (!artist.description.isNullOrBlank()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Text(
                                    text = artist.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(14.dp),
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CustomCircularWavyProgressIndicator(
                                progress = null,
                                color = if (artist.permalinkUrl?.startsWith("yandex") == true) Color(0xFFF2C200) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                } else if (error != null) {
                    item {
                        MessageCard(error)
                    }
                } else {
                    if (tracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Популярные треки",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        val tracksToDisplay = if (isAllTracksLoaded) tracks else tracks.take(15)

                        items(tracksToDisplay, key = { "artist-track-${it.id}" }) { track ->
                            val favorite = favorites.firstOrNull { it.id == track.id }
                            val progress = downloadProgress[track.id]
                            TrackCard(
                                track = track,
                                isFavorite = favorite != null,
                                isSelected = track.id == currentTrackId,
                                downloadState = favorite?.downloadState,
                                progress = progress,
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track) },
                                onFavoriteClick = { onFavoriteClick(track) }
                            )
                        }

                        if (!isAllTracksLoaded) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Button(
                                        onClick = onLoadAllTracks,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        shape = RoundedCornerShape(30.dp),
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        modifier = Modifier.fillMaxWidth(0.9f)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.KeyboardArrowDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Text(
                                                text = "Показать все популярные треки",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = (-0.3).sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Альбомы и плейлисты",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }

                        item {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(playlists, key = { "artist-playlist-${it.id}" }) { playlist ->
                                    val isYandex = playlist.permalinkUrl?.startsWith("yandex:album:") == true || artist.permalinkUrl?.startsWith("yandex") == true
                                    Card(
                                        onClick = { onPlaylistClick(playlist) },
                                        modifier = Modifier.width(140.dp),
                                        shape = RoundedCornerShape(24.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(124.dp)
                                                    .clip(RoundedCornerShape(18.dp))
                                            ) {
                                                if (playlist.artworkUrl != null) {
                                                    FolderArtwork(playlist.artworkUrl, 124.dp)
                                                } else {
                                                    Surface(
                                                        color = if (isYandex) Color(0xFFF2C200).copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(18.dp),
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                Icons.Default.Album,
                                                                contentDescription = null,
                                                                tint = if (isYandex) Color(0xFFF2C200) else MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.size(36.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = playlist.title ?: "Альбом",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${playlist.trackCount} треков",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackActionsDialog(
    track: SoundCloudTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onShare: () -> Unit,
    onRedownload: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            var showPlaylistSelection by remember { mutableStateOf(false) }
            var showCreatePlaylistDialog by remember { mutableStateOf(false) }
            var playlistNameInput by remember { mutableStateOf("") }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = false, onClick = {}) // prevent click-through
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(5.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                CircleShape
                            )
                    )

                    // Cover Art & Title Info
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TrackArtwork(artworkUrl = track.artworkUrl, size = 64.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title ?: "Unknown Track",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.user?.username ?: "SoundCloud Artist",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (!showPlaylistSelection) {
                        // Options
                        Surface(
                            onClick = { showPlaylistSelection = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Добавить в плейлист",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Сохраните этот трек в свои подборки",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = {
                                onShare()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Отправить ссылку на трек",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Поделитесь треком с друзьями",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = {
                                onRedownload()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Перескачать трек",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Скачать файл заново на устройство",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Отмена", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        // Playlist Selection Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { showPlaylistSelection = false },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "Выберите плейлист",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { showCreatePlaylistDialog = true },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        if (playlists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "У вас пока нет плейлистов",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(playlists) { playlist ->
                                    Surface(
                                        onClick = {
                                            onAddToPlaylist(playlist)
                                            onDismiss()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            if (!playlist.artworkUrl.isNullOrBlank()) {
                                                FolderArtwork(playlist.artworkUrl, size = 44.dp)
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.QueueMusic,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${playlist.tracks.size} треков",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text("Отмена", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (showCreatePlaylistDialog) {
                AlertDialog(
                    onDismissRequest = { showCreatePlaylistDialog = false },
                    title = { Text("Создать плейлист") },
                    text = {
                        OutlinedTextField(
                            value = playlistNameInput,
                            onValueChange = { playlistNameInput = it },
                            placeholder = { Text("Название плейлиста") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (playlistNameInput.isNotBlank()) {
                                    onCreatePlaylist(playlistNameInput)
                                    showCreatePlaylistDialog = false
                                    playlistNameInput = ""
                                }
                            }
                        ) {
                            Text("Создать")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCreatePlaylistDialog = false }) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun CustomWavyProgressIndicator(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.2f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wavy")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseShift"
    )

    Canvas(modifier = modifier.height(10.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val ampPx = 3.dp.toPx()
        val waveLenPx = 20.dp.toPx()

        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        val prog = progress ?: 0.3f
        val progressWidth = width * prog.coerceIn(0f, 1f)

        if (progressWidth > 0f) {
            val path = Path()
            val startX = 0f
            val startY = centerY + ampPx * kotlin.math.sin(-phaseShift)
            path.moveTo(startX, startY)

            val stepPx = 2.dp.toPx()
            var x = stepPx
            while (x <= progressWidth) {
                val angle = (x / waveLenPx) * (2f * Math.PI.toFloat()) - phaseShift
                val y = centerY + ampPx * kotlin.math.sin(angle)
                path.lineTo(x, y)
                x += stepPx
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun CustomCircularWavyProgressIndicator(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.2f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "circularWavy")
    val phaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phaseShift"
    )

    Canvas(modifier = modifier.size(40.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseRadius = (minOf(size.width, size.height) / 2f) - 4.dp.toPx()
        val ampPx = 2.dp.toPx()
        val waveCount = 8

        drawCircle(
            color = trackColor,
            radius = baseRadius,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        val prog = progress ?: 1f
        val sweepAngle = 360f * prog.coerceIn(0f, 1f)

        if (sweepAngle > 0f) {
            val path = Path()
            val steps = (sweepAngle * 2).toInt().coerceAtLeast(10)
            
            for (i in 0..steps) {
                val angleDeg = i / 2f
                val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
                
                val currentAmp = ampPx * kotlin.math.sin(angleRad * waveCount - phaseShift)
                val r = baseRadius + currentAmp
                
                val x = center.x + r * kotlin.math.cos(angleRad)
                val y = center.y + r * kotlin.math.sin(angleRad)
                
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun YandexLoginDialog(
    loginUrl: String,
    onTokenCaptured: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var isWebViewLoading by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Вход в Яндекс Музыку",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // WebView Container
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isWebViewLoading = true
                                        url?.let { checkUrl(it) }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebViewLoading = false
                                        url?.let { checkUrl(it) }
                                    }

                                    private fun checkUrl(url: String) {
                                        if (url.contains("access_token=")) {
                                            val token = url.substringAfter("access_token=").substringBefore("&")
                                            if (token.isNotEmpty()) {
                                                onTokenCaptured(token)
                                            }
                                        }
                                    }
                                }
                                loadUrl(loginUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isWebViewLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}



