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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.util.lerp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.zIndex
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import com.example.myapplication.ui.theme.AppShapes
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.SoundCloudBrandSource
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.min
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * Offscreen WebView that refreshes SoundCloud credentials without interrupting the user.
 *
 * The browser session cookie usually outlives the OAuth token, so loading a normal page
 * makes the site's own scripts issue authorised API calls; we read the fresh `client_id`
 * and `Authorization: OAuth` straight off those requests — the same interception the
 * visible login screen uses.
 *
 * Parked far off-screen rather than sized to zero: it still lays out and runs scripts at a
 * realistic viewport, but can never be seen or touched.
 */
@Composable
private fun SilentLoginWebView(
    url: String,
    onCredentialsCaptured: (String, String) -> Unit
) {
    val latestCallback by rememberUpdatedState(onCredentialsCaptured)

    Box(
        modifier = Modifier
            .offset(x = 4000.dp)
            .size(width = 360.dp, height = 640.dp)
    ) {
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
                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    }
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val outgoing = request ?: return null
                            val capturedClientId = outgoing.url.getQueryParameter("client_id")
                            val authHeader = outgoing.requestHeaders["Authorization"]
                                ?: outgoing.requestHeaders["authorization"]
                            if (!capturedClientId.isNullOrBlank() &&
                                !authHeader.isNullOrBlank() &&
                                authHeader.startsWith("OAuth ", ignoreCase = true)
                            ) {
                                val token = authHeader.removePrefix("OAuth ").trim()
                                if (token.isNotEmpty()) {
                                    post { latestCallback(capturedClientId, token) }
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    loadUrl(url)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

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
    val isPlaybackBuffering by viewModel.isPlaybackBuffering.collectAsState()
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
    val needsRelogin by viewModel.needsRelogin.collectAsState()
    val silentLoginUrl by viewModel.silentLoginUrl.collectAsState()
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
            AppScreen.PLAYLISTS -> viewModel.closePlaylists()
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

        silentLoginUrl?.let { url ->
            SilentLoginWebView(
                url = url,
                onCredentialsCaptured = viewModel::onSilentCredentialsCaptured
            )
        }

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
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    // Screens share the top-bar geometry now, so a soft fade+scale makes the bar
                    // look like it stays put while only the content beneath it swaps.
                    (fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 1.02f, animationSpec = tween(160)))
                },
                label = "screenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        mixSection = mixSection,
                        stationSection = stationSection,
                        mixesLoading = mixesLoading,
                        loadingMixId = loadingMixId,
                        hasOauthToken = oauthToken.isNotBlank(),
                        mixesError = if (screen == AppScreen.HOME) errorMessage else null,
                        clientId = clientId,
                        playingMixId = playingMixId,
                        isPlaying = isPlaying,
                        isClientIdExpired = isClientIdExpired,
                        needsRelogin = needsRelogin,
                        playerVisible = currentTrackTitle != null,
                        downloadedCount = downloadedTracks.size,
                        downloadedFolderArtworkUri = downloadedFolderArtworkUri,
                        playlists = playlists,
                        yandexPlaylists = yandexPlaylists,
                        onOpenPlaylist = viewModel::openPlaylist,
                        onOpenYandexPlaylist = { playlist ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.selectYandexPlaylist(playlist)
                        },
                        onCreatePlaylist = viewModel::createPlaylist,
                        onRelogin = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.logout()
                        },
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
                        onOpenMix = { mix ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openMix(mix)
                        },
                        onReloadMixes = viewModel::loadMixes
                    )

                    AppScreen.PLAYLISTS -> PlaylistsScreen(
                        playlists = playlists,
                        yandexPlaylists = yandexPlaylists,
                        hasYandexToken = hasYandexToken,
                        onBack = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.closePlaylists()
                        },
                        onCreatePlaylist = viewModel::createPlaylist,
                        onDeletePlaylist = viewModel::deletePlaylist,
                        onOpenPlaylist = viewModel::openPlaylist,
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
                                },
                                onDeletePlaylist = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deletePlaylist(playlist.id)
                                    viewModel.closePlaylist()
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
                                },
                                onHidePlaylist = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.hideYandexPlaylist(playlist.id)
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
                    artist = currentPlayingTrack?.user?.username.orEmpty(),
                    artworkUrl = currentPlayingTrack?.artworkUrl,
                    isPlaying = isPlaying,
                    progress = if (playbackDurationMs > 0L) {
                        playbackPositionMs.coerceIn(0L, playbackDurationMs).toFloat() /
                            playbackDurationMs.toFloat()
                    } else {
                        0f
                    },
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
                            viewModel.playQueuedTrack(qTrack, activeQueue, fromQueueManager = true)
                        },
                        isFavorite = favorite != null,
                        favoriteTrack = favorite,
                        downloadState = favorite?.downloadState,
                        isPlaying = isPlaying,
                        isBuffering = isPlaybackBuffering,
                        isLoading = isLoading,
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
                        onArtistClick = { artist ->
                            viewModel.openArtistDetails(
                                userId = artist.id ?: 0L,
                                permalinkUrl = artist.permalinkUrl,
                                username = artist.username,
                                trackUrn = track.urn
                            )
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
    mixSection: MixSection?,
    stationSection: MixSection?,
    mixesLoading: Boolean,
    loadingMixId: String?,
    hasOauthToken: Boolean,
    mixesError: String?,
    clientId: String,
    playingMixId: String?,
    isPlaying: Boolean,
    isClientIdExpired: Boolean,
    needsRelogin: Boolean,
    playerVisible: Boolean,
    downloadedCount: Int,
    downloadedFolderArtworkUri: String?,
    playlists: List<Playlist>,
    yandexPlaylists: List<SoundCloudPlaylist>,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenYandexPlaylist: (SoundCloudPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRelogin: () -> Unit,
    onAutoRefreshClientId: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMix: (SoundCloudMix) -> Unit,
    onReloadMixes: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Page 0 = mixes, page 1 = stations. A vertical swipe switches category, which is exactly
    // why playlists and downloads had to leave the home screen: the vertical axis is spoken for.
    val categoryPager = rememberPagerState(initialPage = selectedTab.coerceIn(0, 3)) { 4 }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    LaunchedEffect(selectedTab) {
        if (selectedTab in 0..3 && categoryPager.currentPage != selectedTab) {
            categoryPager.animateScrollToPage(selectedTab)
        }
    }
    LaunchedEffect(categoryPager.currentPage) {
        if (categoryPager.currentPage != selectedTab) {
            onTabSelected(categoryPager.currentPage)
        }
    }

    val sections = listOf(mixSection, stationSection)
    val hasWarning = clientId.isBlank() || needsRelogin || isClientIdExpired

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AppTopBar(
            modifier = Modifier.padding(horizontal = 16.dp),
            leadingIcon = Icons.Default.Search,
            leadingDescription = "Поиск",
            onLeadingClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenSearch()
            },
            title = {
                AnimatedContent(
                    targetState = when (categoryPager.currentPage) {
                        0 -> "Миксы"
                        1 -> "Станции"
                        2 -> "Медиатека"
                        else -> "Моя музыка"
                    },
                    transitionSpec = {
                        (slideInVertically { height -> height / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { height -> -height / 2 } + fadeOut())
                    },
                    label = "categoryTitle"
                ) { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1
                    )
                }
            }
        ) {

            HomeIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Настройки",
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenSettings()
                }
            )
        }

        if (hasWarning) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                when {
                    clientId.isBlank() -> ClientIdWarningCard(onOpenSettings = onOpenSettings)
                    needsRelogin -> ReloginRequiredCard(onRelogin = onRelogin)
                    else -> ClientIdExpiredWarningCard(
                        onOpenSettings = onOpenSettings,
                        onAutoRefresh = onAutoRefreshClientId
                    )
                }
            }
        }

        VerticalPager(
            state = categoryPager,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (page == 2) {
                // Yandex lives on its own page; downloads and hand-made playlists get another
                // one below it, so the two libraries never mix in a single strip.
                LibraryCarousel(
                    tiles = yandexPlaylists.map { playlist ->
                        LibraryTile(
                            key = "yandex-${playlist.id}",
                            title = playlist.title ?: "Без названия",
                            subtitle = plural(playlist.trackCount, "трек", "трека", "треков"),
                            artworkUrl = playlist.artworkUrl,
                            icon = if (playlist.id == -100L) {
                                Icons.Rounded.Favorite
                            } else {
                                Icons.Default.Album
                            },
                            onClick = { onOpenYandexPlaylist(playlist) }
                        )
                    },
                    emptyText = "Подключи Яндекс Музыку в настройках, чтобы увидеть свои плейлисты."
                )
            } else if (page == 3) {
                LibraryCarousel(
                    tiles = buildList {
                        add(
                            LibraryTile(
                                key = "downloads",
                                title = "Скачанное",
                                subtitle = plural(downloadedCount, "трек", "трека", "треков"),
                                // The user's own cover for the downloads folder — passing null
                                // here is why it never showed.
                                artworkUrl = downloadedFolderArtworkUri,
                                icon = Icons.Default.Download,
                                onClick = onOpenDownloads
                            )
                        )
                        playlists.forEach { playlist ->
                            add(
                                LibraryTile(
                                    key = "local-${playlist.id}",
                                    title = playlist.name,
                                    subtitle = plural(playlist.tracks.size, "трек", "трека", "треков"),
                                    artworkUrl = playlist.artworkUrl,
                                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                                    onClick = { onOpenPlaylist(playlist) }
                                )
                            )
                        }
                        add(
                            LibraryTile(
                                key = "create",
                                title = "Создать плейлист",
                                subtitle = "Своя подборка",
                                artworkUrl = null,
                                icon = Icons.Default.Add,
                                accent = true,
                                onClick = { showCreatePlaylistDialog = true }
                            )
                        )
                    },
                    emptyText = ""
                )
            } else {
                MixCarousel(
                    mixes = sections.getOrNull(page)?.mixes.orEmpty(),
                    isStations = page == 1,
                    isLoading = mixesLoading,
                    hasOauthToken = hasOauthToken,
                    errorMessage = if (page == 0) mixesError else null,
                    loadingMixId = loadingMixId,
                    playingMixId = playingMixId,
                    isPlaying = isPlaying,
                    onOpenMix = onOpenMix,
                    onReload = onReloadMixes,
                    onOpenSettings = onOpenSettings
                )
            }
        }

        Spacer(
            modifier = Modifier.height(if (playerVisible) 108.dp else 12.dp)
        )
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

/**
 * The one bar geometry every screen uses. Search morphs out of home, so the leading control
 * and the title have to land on identical coordinates in both — sharing the primitive is what
 * guarantees that, rather than two call sites that merely look similar today.
 */
@Composable
private fun AppTopBar(
    leadingIcon: ImageVector,
    leadingDescription: String,
    onLeadingClick: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeIconButton(
            icon = leadingIcon,
            contentDescription = leadingDescription,
            onClick = onLeadingClick
        )
        Spacer(modifier = Modifier.width(14.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            title()
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(14.dp))
            trailing()
        }
    }
}

/**
 * Rounded square with an accent glyph. Separation from the backdrop comes from the container
 * tone alone — the washes behind it are held low enough for that to hold (see ExpressiveBackground).
 */
@Composable
private fun HomeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * Tracks dealt into pages you flick sideways, the same interaction the mix screen uses, so a
 * long tracklist never turns into an endless vertical scroll. Height is fixed from [perPage] so
 * this can also sit inside a LazyColumn item.
 */
@Composable
private fun PagedTrackList(
    tracks: List<SoundCloudTrack>,
    favorites: List<FavoriteTrack>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    downloadProgress: Map<Long, Float>,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    perPage: Int = 4,
    modifier: Modifier = Modifier
) {
    if (tracks.isEmpty()) return
    val rowHeight = 96.dp
    val rowGap = 8.dp
    val pageCount = (tracks.size + perPage - 1) / perPage
    val pagerState = rememberPagerState { pageCount }
    val favoritesMap = remember(favorites) { favorites.associateBy { it.id } }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * perPage + rowGap * (perPage - 1)),
            pageSpacing = 8.dp
        ) { page ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                val from = page * perPage
                val to = minOf(from + perPage, tracks.size)
                for (index in from until to) {
                    val track = tracks[index]
                    val favorite = favoritesMap[track.id]
                    TrackCard(
                        track = track,
                        isFavorite = favorite != null,
                        isSelected = track.id == currentTrackId,
                        downloadState = favorite?.downloadState,
                        progress = downloadProgress[track.id],
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onFavoriteClick = { onFavoriteClick(track) }
                    )
                }
            }
        }

        if (pageCount > 1) {
            Spacer(modifier = Modifier.height(10.dp))
            CarouselPageIndicator(
                count = pageCount,
                currentPage = pagerState.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/** 45678 -> "45,7 тыс.". Raw six-digit follower counts are unreadable at a glance. */
private fun compactCount(value: Int): String {
    // Formatted against a fixed locale and then switched to a comma: the app's UI is Russian
    // regardless of the device locale, and "1.5 тыс." mixes conventions.
    fun short(amount: Float, unit: String): String =
        String.format(java.util.Locale.US, "%.1f", amount)
            .removeSuffix(".0")
            .replace('.', ',') + " " + unit
    return when {
        value >= 1_000_000 -> short(value / 1_000_000f, "млн")
        value >= 10_000 -> "${value / 1000} тыс."
        value >= 1_000 -> short(value / 1000f, "тыс.")
        else -> value.toString()
    }
}

/**
 * The artist's portrait as the subject, with the numbers that matter sitting on the same card
 * underneath. Replaces a 120dp circular avatar floating in the middle of an otherwise empty
 * header.
 */
@Composable
private fun ArtistHeroCard(
    artist: SoundCloudUser,
    albumCount: Int
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.extraExtraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!artist.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = artworkUrlForSize(artist.avatarUrl, 400.dp),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }

                // Fades the photo into the card so the join never reads as a hard seam.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            )
                        )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val followers = artist.followersCount ?: 0
                val trackTotal = artist.trackCount ?: 0
                if (followers > 0) {
                    ArtistStat(value = compactCount(followers), label = "подписчиков")
                }
                if (trackTotal > 0) {
                    ArtistStat(value = compactCount(trackTotal), label = "треков")
                }
                if (albumCount > 0) {
                    ArtistStat(
                        value = albumCount.toString(),
                        label = plural(albumCount, "альбом", "альбома", "альбомов")
                            .substringAfter(' ')
                    )
                }
            }
        }
    }
}

/**
 * Cover, title, artist and count as one block, on the same card language the rest of the app
 * uses. The old header centred a bare 200dp square over the page with the title floating
 * underneath it, which looked unfinished next to every other screen.
 */
@Composable
private fun AlbumHeroCard(
    artworkUrl: String?,
    title: String,
    subtitle: String,
    trackCount: Int,
    isYandex: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.extraExtraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(1f)
                    .clip(AppShapes.extraLargeIncreased)
            ) {
                if (artworkUrl != null) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        color = if (isYandex) {
                            AppTheme.brand.yandex.container
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Album,
                                contentDescription = null,
                                tint = if (isYandex) {
                                    AppTheme.brand.yandex.onContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = plural(trackCount, "трек", "трека", "треков"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ArtistStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Artist bios run long; four clipped lines with no way to read the rest is a dead end. */
@Composable
private fun ExpandableDescription(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (expanded) "Свернуть" else "Читать полностью",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Russian needs three forms, and "1 треков" in the corner of the home screen looks broken. */
private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val noun = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$count $noun"
}

@Composable
private fun LibraryLaunchRow(
    downloadedCount: Int,
    playlistCount: Int,
    onOpenDownloads: () -> Unit,
    onOpenPlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LibraryLaunchCard(
            icon = Icons.Default.Download,
            label = "Скачанное",
            caption = plural(downloadedCount, "трек", "трека", "треков"),
            onClick = onOpenDownloads,
            modifier = Modifier.weight(1f)
        )
        LibraryLaunchCard(
            icon = Icons.Default.LibraryMusic,
            label = "Плейлисты",
            caption = plural(playlistCount, "плейлист", "плейлиста", "плейлистов"),
            onClick = onOpenPlaylists,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Built in the language of the settings rows — tinted icon puck, title, quiet caption — since
 * that screen is the one the app already gets right. The two cards differ only by accent
 * colour, so they read as a pair rather than as two unrelated buttons.
 */
@Composable
private fun LibraryLaunchCard(
    icon: ImageVector,
    label: String,
    caption: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "libraryCardScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = AppShapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(21.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * One full-bleed cover at a time, with the neighbouring covers peeking in at the edges.
 * Everything off-centre is deliberately pushed back (smaller, dimmer, text hidden) so the
 * focused mix is the only thing that reads as content.
 */
@Composable
private fun MixCarousel(
    mixes: List<SoundCloudMix>,
    isStations: Boolean,
    isLoading: Boolean,
    hasOauthToken: Boolean,
    errorMessage: String?,
    loadingMixId: String?,
    playingMixId: String?,
    isPlaying: Boolean,
    onOpenMix: (SoundCloudMix) -> Unit,
    onReload: () -> Unit,
    onOpenSettings: () -> Unit
) {
    if (!hasOauthToken) {
        CarouselMessage(
            text = "Добавь OAuth-токен в настройках, чтобы увидеть персональные миксы.",
            actionLabel = "Открыть настройки",
            onAction = onOpenSettings
        )
        return
    }

    if (mixes.isEmpty()) {
        when {
            errorMessage != null -> CarouselMessage(
                text = errorMessage,
                actionLabel = "Повторить",
                onAction = onReload
            )
            isLoading -> CarouselSkeleton()
            else -> CarouselMessage(
                text = if (isStations) "Станции пока не загрузились." else "Подборка пока не загрузилась.",
                actionLabel = "Обновить",
                onAction = onReload
            )
        }
        return
    }

    val pagerState = rememberPagerState { mixes.size }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        HorizontalPager(
            state = pagerState,
            // Peek width is (contentPadding - pageSpacing), then the neighbour's own scale
            // pulls it further inward — so the gap has to stay small or the side covers vanish
            // entirely, which is what happened when this was 30/12.
            contentPadding = PaddingValues(horizontal = 34.dp),
            pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val mix = mixes[page]
            val distance = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)

            MixCarouselCard(
                mix = mix,
                isStation = isStations,
                distance = distance,
                isLoading = loadingMixId == mix.id,
                isMixPlaying = playingMixId == mix.id && isPlaying,
                onClick = { onOpenMix(mix) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        CarouselPageIndicator(
            count = mixes.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

private data class LibraryTile(
    val key: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val icon: ImageVector?,
    val accent: Boolean = false,
    val onClick: () -> Unit
)

/**
 * The library as a third page of the same carousel rather than two buttons wedged above the
 * player bar: liked tracks and Yandex playlists first, then your own, then downloads, and a
 * create tile at the end.
 */
@Composable
private fun LibraryCarousel(
    tiles: List<LibraryTile>,
    emptyText: String
) {
    if (tiles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val pagerState = rememberPagerState { tiles.size }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 34.dp),
            pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val tile = tiles[page]
            val distance = (
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                ).absoluteValue.coerceIn(0f, 1f)
            LibraryCarouselCard(tile = tile, distance = distance)
        }

        Spacer(modifier = Modifier.height(20.dp))

        CarouselPageIndicator(
            count = tiles.size,
            currentPage = pagerState.currentPage,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun LibraryCarouselCard(tile: LibraryTile, distance: Float) {
    val focus = 1f - distance
    val coverScale = lerp(0.92f, 1f, focus)
    val coverAlpha = lerp(0.45f, 1f, focus)
    val captionAlpha = (focus * 2.4f - 1.4f).coerceIn(0f, 1f)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "libraryPress"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = coverScale * pressScale
                    scaleY = coverScale * pressScale
                    alpha = coverAlpha
                }
                .clip(AppShapes.extraExtraLarge)
                .background(
                    if (tile.accent) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = tile.onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!tile.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = tile.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (tile.icon != null) {
                Icon(
                    tile.icon,
                    contentDescription = null,
                    tint = if (tile.accent) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(84.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .padding(horizontal = 8.dp)
                .graphicsLayer { alpha = captionAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tile.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MixCarouselCard(
    mix: SoundCloudMix,
    isStation: Boolean,
    distance: Float,
    isLoading: Boolean,
    isMixPlaying: Boolean,
    onClick: () -> Unit
) {
    val focus = 1f - distance
    // Side cards fall back in scale and fade out; their captions disappear well before they
    // reach the edge so the strip stays quiet instead of competing with the focused cover.
    val coverScale = lerp(0.92f, 1f, focus)
    val coverAlpha = lerp(0.45f, 1f, focus)
    val captionAlpha = (focus * 2.4f - 1.4f).coerceIn(0f, 1f)

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "coverPress"
    )

    // Mixes ship their artist list in `description`. Stations put "Artist station" there,
    // which is not a caption — the station's artist is its title, shown above.
    val artistLine = if (isStation) null else mix.description?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = coverScale * pressScale
                    scaleY = coverScale * pressScale
                    alpha = coverAlpha
                }
                .clip(AppShapes.extraExtraLarge)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints {
                TrackArtwork(
                    artworkUrl = mix.artworkUrl,
                    size = maxWidth,
                    isPlaying = isMixPlaying,
                    useMorphing = isMixPlaying,
                    fallbackShape = AppShapes.extraExtraLarge
                )
            }

            if (isMixPlaying) {
                NowPlayingBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                )
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Fixed height on purpose. Let this box size to its content and the page indicator
        // below it sits at a different y for a one-line artist list than for a two-line one,
        // so it hops every time you swipe between mixes.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .padding(horizontal = 8.dp)
                .graphicsLayer { alpha = captionAlpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isStation) mix.title else localizedMixTitle(mix.title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isStation) {
                Text(
                    text = "Станция",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            if (!artistLine.isNullOrBlank()) {
                Text(
                    text = artistLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Animated equaliser bars — the playing cue that replaced morphing the cover itself. */
@Composable
private fun NowPlayingBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val transition = rememberInfiniteTransition(label = "nowPlaying")
    val barCount = 4
    val heights = List(barCount) { index ->
        transition.animateFloat(
            initialValue = 0.30f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 420 + index * 130,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$index"
        )
    }

    Surface(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            heights.forEach { height ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp * height.value)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary)
                )
            }
        }
    }
}

@Composable
private fun CarouselPageIndicator(
    count: Int,
    currentPage: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count.coerceAtMost(12)) { index ->
            val active = index == currentPage
            val width by animateDpAsState(
                targetValue = if (active) 22.dp else 6.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dotWidth"
            )
            val color by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                },
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun CarouselSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .clip(AppShapes.extraExtraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
    }
}

@Composable
private fun CarouselMessage(
    text: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun PlaylistsScreen(
    playlists: List<Playlist>,
    yandexPlaylists: List<SoundCloudPlaylist>,
    hasYandexToken: Boolean,
    onBack: () -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenYandexPlaylist: (SoundCloudPlaylist) -> Unit
) {
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopBar(title = "Плейлисты", onBack = onBack)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Мои плейлисты",
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCreatePlaylistDialog = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Создать плейлист")
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
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            TopBar(title = "Настройки", onBack = onBack)
        }

        // Section 1: Sync
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "СИНХРОНИЗАЦИЯ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // SoundCloud Sync
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(AppTheme.brand.soundCloud.container, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = null,
                                    tint = AppTheme.brand.soundCloud.onContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SoundCloud",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Автоскачивание лайкнутых треков SoundCloud",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (soundcloudLikesSyncStatus.state != SyncState.IDLE) {
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
                                fontWeight = FontWeight.Medium,
                                color = if (soundcloudLikesSyncStatus.state == SyncState.FAILED) MaterialTheme.colorScheme.error else AppTheme.brand.soundCloud.color
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
                                    color = AppTheme.brand.soundCloud.color,
                                    trackColor = AppTheme.brand.soundCloud.container,
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (soundcloudLikesSyncStatus.state == SyncState.FETCHING_LIKES || soundcloudLikesSyncStatus.state == SyncState.DOWNLOADING) {
                                FilledTonalButton(
                                    onClick = stopLikesSync,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large,
                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Text("Остановить")
                                }
                            } else {
                                Button(
                                    onClick = startSoundCloudLikesSync,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = AppTheme.brand.soundCloud.color,
                                        contentColor = AppTheme.brand.soundCloud.onColor
                                    )
                                ) {
                                    Text(
                                        text = if (soundcloudLikesSyncStatus.state == SyncState.COMPLETED || soundcloudLikesSyncStatus.state == SyncState.FAILED) "Синхронизировать заново" else "Синхронизировать лайки"
                                    )
                                }

                                if (soundcloudLikesSyncStatus.state == SyncState.COMPLETED || soundcloudLikesSyncStatus.state == SyncState.FAILED) {
                                    FilledTonalButton(
                                        onClick = resetSoundCloudLikesSyncStatus,
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text("Сбросить")
                                    }
                                }
                            }
                        }

                        if (hasYandexToken) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            // Yandex.Music Sync
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(AppTheme.brand.yandex.container, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = AppTheme.brand.yandex.onContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Яндекс.Музыка",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Автоскачивание треков из плейлиста 'Мне нравится'",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (yandexLikesSyncStatus.state != SyncState.IDLE) {
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
                                    fontWeight = FontWeight.Medium,
                                    color = if (yandexLikesSyncStatus.state == SyncState.FAILED) MaterialTheme.colorScheme.error else AppTheme.brand.yandex.color
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
                                        color = AppTheme.brand.yandex.color,
                                        trackColor = AppTheme.brand.yandex.container,
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (yandexLikesSyncStatus.state == SyncState.FETCHING_LIKES || yandexLikesSyncStatus.state == SyncState.DOWNLOADING) {
                                    FilledTonalButton(
                                        onClick = stopLikesSync,
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.large,
                                        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Text("Остановить")
                                    }
                                } else {
                                    Button(
                                        onClick = startYandexLikesSync,
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.large,
                                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                            containerColor = AppTheme.brand.yandex.color,
                                            contentColor = AppTheme.brand.yandex.onColor
                                        )
                                    ) {
                                        Text(
                                            text = if (yandexLikesSyncStatus.state == SyncState.COMPLETED || yandexLikesSyncStatus.state == SyncState.FAILED) "Синхронизировать заново" else "Синхронизировать лайки"
                                        )
                                    }

                                    if (yandexLikesSyncStatus.state == SyncState.COMPLETED || yandexLikesSyncStatus.state == SyncState.FAILED) {
                                        FilledTonalButton(
                                            onClick = resetYandexLikesSyncStatus,
                                            modifier = Modifier.weight(1f),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text("Сбросить")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Accounts
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "АККАУНТЫ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // SoundCloud Account
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(AppTheme.brand.soundCloud.container, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = AppTheme.brand.soundCloud.onContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SoundCloud",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Вы вошли в аккаунт",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = onRelogin,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text("Перезайти")
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        // Yandex.Music Account
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(AppTheme.brand.yandex.container, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = AppTheme.brand.yandex.onContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Яндекс.Музыка",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (hasYandexToken) "Подключен" else "Не подключен",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (hasYandexToken) {
                                FilledTonalButton(
                                    onClick = onYandexLogoutClick,
                                    shape = MaterialTheme.shapes.medium,
                                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Выйти")
                                }
                            } else {
                                Button(
                                    onClick = onYandexLoginClick,
                                    shape = MaterialTheme.shapes.medium,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = AppTheme.brand.yandex.color,
                                        contentColor = AppTheme.brand.yandex.onColor
                                    )
                                ) {
                                    Text("Войти")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Data & Cache
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ХРАНИЛИЩЕ И КЭШ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(18.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Очистка кэша",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Сброс кэша миксов и станций",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                onClearCache()
                                Toast.makeText(context, "Кэш очищен", Toast.LENGTH_SHORT).show()
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Очистить")
                        }
                    }
                }
            }
        }

        // Section 4: Sound / Equalizer
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ЗВУК",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                val eqEnabled by settingsRepository.equalizerEnabled.collectAsState()
                val eqPreset by settingsRepository.equalizerPreset.collectAsState()
                EqualizerCard(
                    settingsRepository = settingsRepository,
                    eqEnabled = eqEnabled,
                    eqPreset = eqPreset
                )
            }
        }

        // Section 5: Debug & App
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ОТЛАДКА",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                val showDebugPercentageVal by settingsRepository.showDebugPercentage.collectAsState()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Дебаг информация",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Процент скачивания на экране загрузок",
                                style = MaterialTheme.typography.bodySmall,
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
            .statusBarsPadding()
            // The keyboard opens itself here, so without this the last result sits under it
            // with no way to scroll to it.
            .imePadding(),
        // top = 0: the bar has to start at the same y as the home bar, or entering search
        // shifts the title and back button downward and the transition reads as a jump.
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 120.dp),
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
                    color = if (searchInYandex) AppTheme.brand.yandex.color else MaterialTheme.colorScheme.primary,
                    trackColor = if (searchInYandex) AppTheme.brand.yandex.container else MaterialTheme.colorScheme.primaryContainer,
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
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                            tint = AppTheme.brand.yandex.color,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Войдите в Яндекс Музыку в настройках, чтобы искать треки.",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onOpenSettings,
                            shape = MaterialTheme.shapes.large,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = AppTheme.brand.yandex.color,
                                contentColor = AppTheme.brand.yandex.onColor
                            )
                        ) {
                            Text("Перейти в настройки")
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
private fun ExpressiveBackground(animated: Boolean = true) {
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

    // These must be *tints* drawn over the backdrop. Painting them with a surface-container
    // role made them darker than `background` in dark theme, which is what turned the
    // shapes into black silhouettes. Accent roles are always lighter/more chromatic than
    // the backdrop in both themes, so the motif reads as a glow rather than a cutout.
    // Content surfaces are opaque now, so these can sit stronger than they used to.
    // Colour here comes from wide radial washes rather than flat fills. A flat tint sitting
    // near the card plane reads as a competing panel — which is what went wrong before —
    // whereas a wash that falls off to nothing can carry real saturation and still never
    // present an edge that rivals a card. The rotating polygons stay on top as faint
    // texture, so the motion survives without the tonal fight.
    val primaryTone = MaterialTheme.colorScheme.primary
    val secondaryTone = MaterialTheme.colorScheme.secondary
    val tertiaryTone = MaterialTheme.colorScheme.tertiary

    val colorPrimary = primaryTone.copy(alpha = 0.030f)
    val colorTertiary = tertiaryTone.copy(alpha = 0.026f)
    val colorSecondary = secondaryTone.copy(alpha = 0.022f)

    // Reading these only when animating is deliberate: skipping the state read stops the
    // Canvas being invalidated every frame. That matters under the queue sheet, where this
    // whole layer is blurred — an animated backdrop meant re-blurring a full screen at 120Hz.
    val phase1 = if (animated) rotationPhase1 else 0f
    val phase2 = if (animated) rotationPhase2 else 0f
    val phase3 = if (animated) rotationPhase3 else 0f
    val phase4 = if (animated) rotationPhase4 else 0f
    val phase5 = if (animated) rotationPhase5 else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val steps = 72  // Reduced from 120 for performance (#15)

            // Accent washes first, so the polygons layer over them.
            // Centres sit well outside the viewport on purpose. With a centre parked in a
            // corner the screen catches the wash at nearly full strength, which pushed the
            // backdrop *above* the card plane and made controls read as holes; from out here
            // only the falloff crosses the glass, so the tint stays under the content.
            val washes = listOf(
                Triple(primaryTone, Offset(width * -0.28f, height * -0.10f), 0.075f),
                Triple(tertiaryTone, Offset(width * 1.28f, height * 0.20f), 0.070f),
                Triple(secondaryTone, Offset(width * -0.24f, height * 0.74f), 0.065f),
                Triple(primaryTone, Offset(width * 1.24f, height * 1.08f), 0.070f)
            )
            washes.forEach { wash ->
                val tone = wash.first
                val center = wash.second
                val strength = wash.third
                val radius = width * 1.08f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(tone.copy(alpha = strength), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }

            // 1. Center: 8-petaled flower shape (primary)
            drawContext.canvas.save()
            drawContext.canvas.translate(width * 0.5f, height * 0.5f)
            drawContext.canvas.rotate(phase1)
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
            drawContext.canvas.rotate(phase2)
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
            drawContext.canvas.rotate(phase3)
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
            drawContext.canvas.rotate(phase4)
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
            drawContext.canvas.rotate(phase5)
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
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = AppShapes.extraLargeIncreased,
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
                style = MaterialTheme.typography.titleMedium,
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
        shape = MaterialTheme.shapes.extraLarge,
        placeholder = { Text("Найти трек") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
        shape = AppShapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
        shape = AppShapes.extraExtraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    shape = AppShapes.largeIncreased,
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
                        shape = AppShapes.largeIncreased,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    // Deliberately the same primitive the home screen uses: the back button has to sit exactly
    // where the search button was, or entering search reads as the whole bar jumping.
    AppTopBar(
        leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
        leadingDescription = "Назад",
        onLeadingClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onBack()
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailing = trailing
    )
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    onOpenMix: (SoundCloudMix) -> Unit,
    onReload: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = localizedSectionTitle(mixSection?.title),
            // Only worth a second line when there's something to act on — the routine
            // "N миксов от SoundCloud" was noise on every section.
            subtitle = when {
                !hasOauthToken -> "Добавь OAuth-токен в настройках, чтобы увидеть персональные миксы."
                mixSection == null && !isLoading -> "Подборка пока не загрузилась."
                else -> null
            }
        )

        if (!hasOauthToken) {
            ElevatedCard(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
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

        if (mixSection == null && isLoading) {
            // Placeholder tiles keep the row's height stable while loading, instead of the
            // section popping into existence and shoving the page around.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false
            ) {
                items(3) {
                    Column(
                        modifier = Modifier.width(168.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(168.dp)
                                .clip(AppShapes.largeIncreased)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .height(14.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.45f)
                                .height(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                    }
                }
            }
        }

        mixSection?.mixes?.let { mixes ->
            if (mixes.isEmpty()) {
                EmptyState("Подборка your-moods пуста.")
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(end = 4.dp)
                ) {
                    items(mixes, key = { it.id }) { mix ->
                        val isMixPlaying = isPlaying && playingMixId == mix.id
                        MixCard(
                            mix = mix,
                            isLoading = loadingMixId == mix.id,
                            isMixPlaying = isMixPlaying,
                            onClick = { onOpenMix(mix) }
                        )
                    }
                }
            }
        }
    }
}

/** SoundCloud hands these section names back in English; surface them in Russian. */
private fun localizedSectionTitle(raw: String?): String = when {
    raw.isNullOrBlank() -> "Миксы"
    raw.contains("Station", ignoreCase = true) -> "Станции"
    raw.startsWith("Mixed for", ignoreCase = true) -> "Твои миксы"
    raw.contains("Your Mixes", ignoreCase = true) -> "Твои миксы"
    else -> raw
}

private val YourMixPattern = Regex("""^Your Mix\s*(\d+)$""", RegexOption.IGNORE_CASE)

private fun localizedMixTitle(raw: String): String =
    YourMixPattern.find(raw.trim())?.let { "Твой микс ${it.groupValues[1]}" } ?: raw

/**
 * An editorial mix tile: the artwork *is* the card.
 *
 * No play button — mixes are opened, and tracks are started from inside them. Playing
 * state is carried by the artwork itself: the morphing squircle blooms into a flower
 * while the mix plays, which is the app's own expressive idiom and needs no badge,
 * ring or recolour on top of it.
 *
 * Only the title sits underneath. The old subtitle was a long comma-separated artist
 * list that added a second line of noise to every tile.
 */
@Composable
private fun MixCard(
    mix: SoundCloudMix,
    isLoading: Boolean,
    isMixPlaying: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val tileSize = 172.dp

    Column(
        modifier = Modifier
            .width(tileSize)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(tileSize),
            contentAlignment = Alignment.Center
        ) {
            if (mix.artworkUrl != null) {
                TrackArtwork(
                    artworkUrl = mix.artworkUrl,
                    size = tileSize,
                    isPlaying = isMixPlaying,
                    useMorphing = true
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = AppShapes.largeIncreased,
                    modifier = Modifier.size(tileSize * 0.86f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // No label: these covers are self-labelling, so a caption under the tile just
        // said the same thing twice. The Russian section header above carries the
        // context, and the morphing artwork carries the playing state.
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
    val playerVisible = currentTrackId != null

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            AppTopBar(
                modifier = Modifier.padding(horizontal = 16.dp),
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                leadingDescription = "Назад",
                onLeadingClick = onBack,
                title = {
                    Text(
                        text = localizedMixTitle(mix.title),
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            // Centred, and noticeably larger than a row's 68dp thumbnail. Laid out as
            // cover-left + two lines of text it was structurally a TrackCard, so it read as
            // just another item in the list instead of as the screen's header.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TrackArtwork(
                    artworkUrl = mix.artworkUrl,
                    size = 132.dp,
                    useMorphing = false,
                    fallbackShape = AppShapes.extraLargeIncreased
                )
                if (!mix.description.isNullOrBlank()) {
                    Text(
                        text = mix.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // An explicit rule between header and list, so the two can never blur together.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = plural(tracks.size, "трек", "трека", "треков"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 4.dp
                    )
                }
                return@Column
            }

            // The whole point of this screen's rework: 30 tracks used to be one endless scroll.
            // Now they're dealt into pages that each fit on screen, and you flick sideways —
            // same gesture as the mix carousel you arrived from.
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // A TrackCard is 68dp of artwork plus 14dp padding top and bottom. Guessing
                // low here is what let an extra row onto the page, and a Column with no room
                // left squeezes its children rather than dropping them.
                val rowHeight = 96.dp
                val rowGap = 8.dp
                val perPage = ((maxHeight + rowGap) / (rowHeight + rowGap)).toInt().coerceIn(1, 8)
                val pageCount = (tracks.size + perPage - 1) / perPage
                val pagerState = rememberPagerState { pageCount }

                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        pageSpacing = 8.dp
                    ) { page ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(rowGap)
                        ) {
                            val from = page * perPage
                            val to = minOf(from + perPage, tracks.size)
                            for (index in from until to) {
                                val track = tracks[index]
                                val favorite = favoritesMap[track.id]
                                TrackCard(
                                    track = track,
                                    isFavorite = favorite != null,
                                    isSelected = track.id == currentTrackId,
                                    downloadState = favorite?.downloadState,
                                    progress = downloadProgress[track.id],
                                    isPlaying = isPlaying,
                                    onClick = { onPlayTrack(track) },
                                    onFavoriteClick = { onFavoriteClick(track) }
                                )
                            }
                        }
                    }

                    if (pageCount > 1) {
                        CarouselPageIndicator(
                            count = pageCount,
                            currentPage = pagerState.currentPage,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (playerVisible) 96.dp else 12.dp))
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
        shape = AppShapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // Was just the uploader, so a collaboration listed one name here while the
                    // player showed several.
                    text = track.artists?.takeIf { it.isNotEmpty() }
                        ?.mapNotNull { it.username }
                        ?.joinToString(", ")
                        ?: track.user?.username
                        ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
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
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                        trackColor = (if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary).copy(alpha = 0.2f),
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
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        // One icon family across the app: unset just means a quieter accent,
                        // not a different (grey) colour.
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isFavorite) 1f else 0.55f
                        )
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
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimaryContainer
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
            // Cached cover when the track is downloaded, so the row still shows artwork offline.
            TrackArtwork(track.displayArtworkUrl, 64.dp, isPlaying = isSelected && isPlaying)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                            color = if (debugPercentage < 90) {
                                MaterialTheme.colorScheme.error
                            } else if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
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
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                IconButton(onClick = onDeleteDownload) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
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
    isBuffering: Boolean,
    isLoading: Boolean,
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
    onArtistClick: (SoundCloudUser) -> Unit
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
    val showLoadingShape = (downloadState != DownloadState.DOWNLOADED) && (isBuffering || isLoading || (positionMs == 0L && !isPlaying))
    val blurRadius by animateDpAsState(
        targetValue = if (showQueue) 10.dp else 0.dp,
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
                // Background Box
                Box(modifier = Modifier.fillMaxSize()) {
                    ExpressiveBackground(animated = !showQueue)
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
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HomeIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        onClick = onBack
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // The indicator is always the equaliser now, whatever the download
                        // state — tapping it reveals the state and the destructive action,
                        // instead of a separate delete button appearing out of nowhere.
                        val state = downloadState ?: DownloadState.NONE
                        var showTrackMenu by remember { mutableStateOf(false) }
                        Box {
                            NowPlayingBadge(onClick = { showTrackMenu = true })
                            DropdownMenu(
                                expanded = showTrackMenu,
                                onDismissRequest = { showTrackMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = when (state) {
                                                DownloadState.DOWNLOADED -> "Скачано на устройство"
                                                DownloadState.DOWNLOADING -> "Скачивается"
                                                DownloadState.FAILED -> "Ошибка загрузки"
                                                DownloadState.NONE -> "Играет из сети"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    enabled = false,
                                    onClick = {}
                                )
                                if (favoriteTrack?.downloadState == DownloadState.DOWNLOADED) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Удалить с устройства") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {
                                            showTrackMenu = false
                                            onDeleteDownload(favoriteTrack)
                                        }
                                    )
                                }
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
                    TrackArtwork(
                        artworkUrl = track.artworkUrl,
                        size = 320.dp,
                        isPlaying = isPlaying,
                        useMorphing = false,
                        showLoadingShape = showLoadingShape
                    )
                }
                
                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = track.title ?: "Unknown Track",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Every credited artist gets its own tappable chip. Previously this was one
                // line built from `track.user`, so a collaboration showed a single name and
                // there was no way to reach anyone else on the track.
                val credited = remember(track) {
                    track.artists?.takeIf { it.isNotEmpty() }
                        ?: listOfNotNull(track.user)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (credited.isEmpty()) {
                        Text(
                            text = "Unknown Artist",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    credited.forEach { artist ->
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onArtistClick(artist)
                            },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ) {
                            Text(
                                text = artist.username ?: "Unknown Artist",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

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
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        formatDuration(durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                val playButtonWidth by animateDpAsState(
                    targetValue = if (isPlaying) 128.dp else 84.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "playButtonWidth"
                )

                val buttonsSpacing by animateDpAsState(
                    targetValue = if (isPlaying) 36.dp else 12.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "buttonsSpacing"
                )

                val controlButtonsSpacing by animateDpAsState(
                    targetValue = if (isPlaying) 28.dp else 14.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioHighBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "controlButtonsSpacing"
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(buttonsSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(32.dp))
                    }
                    ExpressivePlayButton(
                        isPlaying = isPlaying,
                        onClick = onTogglePlay,
                        modifier = Modifier
                            .height(84.dp)
                            .width(playButtonWidth),
                        iconSize = 42.dp
                    )
                    FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next track", modifier = Modifier.size(32.dp))
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(controlButtonsSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveControlIconButton(
                        selected = isFavorite,
                        onClick = onFavoriteClick,
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(68.dp)
                    )

                    ExpressiveControlIconButton(
                        selected = repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = onRepeat,
                        icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(68.dp)
                    )

                    ExpressiveControlIconButton(
                        selected = shuffleEnabled,
                        onClick = onShuffle,
                        icon = Icons.Rounded.Shuffle,
                        selectedColor = MaterialTheme.colorScheme.primary,
                        unselectedColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(68.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
            }
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

private val AUTO_SCROLL_EDGE = 170.dp
private val AUTO_SCROLL_SPEED = 6.dp
private const val SWAP_INTERVAL_MS = 90L

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
    val lazyListState = rememberLazyListState()
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    // The finger's absolute position inside the list, and where within the row it grabbed.
    // Tracking the pointer rather than an accumulated delta is the whole point of this rewrite:
    // the finger can hold still at an edge while rows scroll underneath it, and the drop target
    // updates on its own. The old version accumulated a delta, so a stationary finger meant no
    // reordering at all — the list scrolled away and the row was recycled, killing the gesture.
    var pointerY by remember { mutableStateOf(0f) }
    var grabOffset by remember { mutableStateOf(0f) }
    var autoScroll by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    var lastSwapAt by remember { mutableStateOf(0L) }
    val settleDrag: () -> Unit = {
        val from = draggedIndex
        val now = System.currentTimeMillis()
        // The list needs a frame or two to lay out after a swap. Settling again before then
        // compares the pointer against stale geometry and swaps the same pair repeatedly —
        // that was one haptic and one ExoPlayer moveMediaItem *per frame*.
        if (from != null && now - lastSwapAt >= SWAP_INTERVAL_MS) {
            val info = lazyListState.layoutInfo
            val y = pointerY
            val target = info.visibleItemsInfo.firstOrNull { candidate ->
                candidate.index != from && y >= candidate.offset && y <= candidate.offset + candidate.size
            }
            if (target != null) {
                onReorder(from, target.index)
                draggedIndex = target.index
                lastSwapAt = now
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LaunchedEffect(autoScroll) {
        if (autoScroll == 0f) return@LaunchedEffect
        while (kotlinx.coroutines.currentCoroutineContext().isActive) {
            withFrameNanos { }
            val consumed = lazyListState.scrollBy(autoScroll)
            settleDrag()
            // Hit the end of the list — nothing left to scroll, so stop burning frames.
            if (consumed == 0f) break
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        if (dragAmount.y > 10f) {
                            onDismiss()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        Card(
            shape = AppShapes.bottomSheet,
            colors = CardDefaults.cardColors(
                // Translucent on purpose: the player behind is blurred by 20dp while this is
                // open, and an opaque sheet simply hid that entirely.
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .align(Alignment.BottomCenter)
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
                    // Drag handle with adequate touch target (#40)
                    Box(
                        modifier = Modifier
                            .size(40.dp, 5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

                    var listDragAccumulator by remember { mutableStateOf(0f) }
                    val nestedScrollConnection = remember {
                        object : NestedScrollConnection {
                            override fun onPreScroll(
                                available: Offset,
                                source: NestedScrollSource
                            ): Offset {
                                val isAtTop = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
                                if (isAtTop && available.y > 0f) {
                                    listDragAccumulator += available.y
                                    if (listDragAccumulator > 150f) {
                                        onDismiss()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        listDragAccumulator = 0f
                                    }
                                    return Offset(0f, available.y)
                                } else {
                                    listDragAccumulator = 0f
                                }
                                return Offset.Zero
                            }

                            override fun onPostScroll(
                                consumed: Offset,
                                available: Offset,
                                source: NestedScrollSource
                            ): Offset {
                                if (available.y > 0f) {
                                    listDragAccumulator += available.y
                                    if (listDragAccumulator > 150f) {
                                        onDismiss()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        listDragAccumulator = 0f
                                    }
                                    return Offset(0f, available.y)
                                }
                                return Offset.Zero
                            }
                        }
                    }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
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
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                            }
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
                        shape = AppShapes.largeIncreased,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(if (isThisDragged) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isThisDragged) {
                                    // Sit exactly under the finger, wherever the list has
                                    // scrolled to since the drag began.
                                    val info = lazyListState.layoutInfo
                                    val me = info.visibleItemsInfo
                                        .firstOrNull { it.index == currentIndex }
                                    if (me != null) {
                                        pointerY - grabOffset - me.offset
                                    } else {
                                        0f
                                    }
                                } else {
                                    0f
                                }
                                scaleX = scale
                                scaleY = scale
                                alpha = if (draggedIndex != null && !isThisDragged) 0.65f else 1f
                            }
                            // The dragged card is positioned by hand; letting the item
                            // animation also drive it is what made it jump around.
                            .then(if (isThisDragged) Modifier else Modifier.animateItem())
                            .pointerInput(trackItem.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { start ->
                                        val info = lazyListState.layoutInfo
                                        val me = info.visibleItemsInfo
                                            .firstOrNull { it.index == currentIndex }
                                        if (me != null) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedIndex = currentIndex
                                            // Convert the touch, which arrives relative to this
                                            // row, into the viewport space item offsets use.
                                            pointerY = me.offset + start.y
                                            grabOffset = start.y
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        if (draggedIndex == null) return@detectDragGesturesAfterLongPress
                                        change.consume()
                                        val bounds = lazyListState.layoutInfo
                                        pointerY = (pointerY + dragAmount.y).coerceIn(
                                            bounds.viewportStartOffset.toFloat(),
                                            bounds.viewportEndOffset.toFloat()
                                        )
                                        settleDrag()

                                        val info = lazyListState.layoutInfo
                                        val top = info.viewportStartOffset.toFloat()
                                        val bottom = info.viewportEndOffset.toFloat()
                                        // Roughly the first/last two rows. One steady speed, not
                                        // a ramp: a proportional speed makes the same gesture
                                        // behave differently depending on exactly where the
                                        // finger stopped.
                                        val edge = with(density) { AUTO_SCROLL_EDGE.toPx() }
                                        val step = with(density) { AUTO_SCROLL_SPEED.toPx() }
                                        autoScroll = when {
                                            pointerY > bottom - edge -> step
                                            pointerY < top + edge -> -step
                                            else -> 0f
                                        }
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        autoScroll = 0f
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        autoScroll = 0f
                                    }
                                )
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
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
}
@Composable
private fun FolderArtwork(artworkUri: String?, size: Dp) {
    if (artworkUri.isNullOrBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = if (size > 100.dp) AppShapes.extraLargeIncreased else MaterialTheme.shapes.large,
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.46f),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
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

class LoadingExpressiveShape(
    private val phase: Float,
    private val rotation: Float
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

        val steps = 120
        val stage = (phase.toInt()) % 6
        val fraction = phase - phase.toInt()

        for (i in 0..steps) {
            val theta = (i * 2f * Math.PI / steps).toFloat()
            val rStage = getRadiusForShape(stage, theta, maxRadius)
            val rNext = getRadiusForShape((stage + 1) % 6, theta, maxRadius)
            // Scaled down by 0.78f to prevent clipping at bounds
            val r = (rStage * (1f - fraction) + rNext * fraction) * 0.78f

            val rotAngle = theta + rotation
            val x = centerX + r * cos(rotAngle)
            val y = centerY + r * sin(rotAngle)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }

    private fun getRadiusForShape(shapeIndex: Int, angle: Float, baseRadius: Float): Float {
        return when (shapeIndex) {
            0 -> { // Pill (Ellipse / squashed oval)
                val cosA = cos(angle)
                val sinA = sin(angle)
                val scaleFactor = baseRadius / sqrt(1.8f * cosA * cosA + 0.5f * sinA * sinA)
                scaleFactor * 0.85f
            }
            1 -> { // Triangle
                baseRadius * (1f + 0.3f * cos(3f * angle)) * 0.72f
            }
            2 -> { // Square
                baseRadius * (1f + 0.15f * cos(4f * angle)) * 0.8f
            }
            3 -> { // 4-sided Cookie
                baseRadius * (1f + 0.18f * cos(4f * angle) + 0.08f * cos(8f * angle)) * 0.78f
            }
            4 -> { // Pentagon
                baseRadius * (1f + 0.12f * cos(5f * angle)) * 0.8f
            }
            else -> { // 5 -> Diamond
                val cosA = cos(angle - (Math.PI / 4).toFloat())
                val sinA = sin(angle - (Math.PI / 4).toFloat())
                val scaleFactor = baseRadius / sqrt(0.5f * cosA * cosA + 1.8f * sinA * sinA)
                scaleFactor * 0.82f
            }
        }
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
            val cosT4 = cosT.absoluteValue.pow(8f)
            val sinT4 = sinT.absoluteValue.pow(8f)
            val rSquare = 1f / (cosT4 + sinT4).pow(1f / 8f)

            // 2. Calculate flower radius at theta (representing an 8-petaled flower).
            val rFlower = 1f + 0.08f * kotlin.math.cos(8f * (theta - rotationPhase))

            // 3. Interpolate between squircle and flower based on progress
            val rRaw = (1f - progress) * rSquare + progress * rFlower

            // A superellipse already peaks at exactly 1.0 on the axes and stays inside the box
            // everywhere else, so the square state needs no shrinking — the old flat 0.90 factor
            // was cutting ~10% off every edge and slicing the lettering burnt into mix covers.
            // Only the flower state overshoots (by its 0.08 petal amplitude), so compensate for
            // just that, in proportion to how far the morph has progressed.
            val rNormalized = rRaw * (1f - 0.075f * progress)

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

/**
 * SoundCloud serves several artwork sizes. Asking for t500x500 everywhere meant a 50dp queue
 * thumbnail downloaded and decoded a 500x500 JPEG, which is pure cost on every row that scrolls
 * into view. Thresholds are generous — these are dp against a ~3x screen.
 */
private val SizedPathPattern = Regex("\\d{2,4}x\\d{2,4}")

private fun artworkUrlForSize(url: String?, size: Dp): String? {
    if (url == null) return null
    if (url.contains("avatars.yandex.net") || url.contains("music-content")) {
        // Yandex encodes the dimensions in the path itself, so the SoundCloud "large" swap
        // never matched and every Yandex cover stayed at the 200x200 the mapper asked for.
        val yandexSize = when {
            size <= 64.dp -> "200x200"
            size <= 120.dp -> "400x400"
            size <= 260.dp -> "800x800"
            else -> "1000x1000"
        }
        return SizedPathPattern.replace(url, yandexSize)
    }
    val variant = when {
        size <= 64.dp -> "t200x200"
        size <= 120.dp -> "t300x300"
        else -> "t500x500"
    }
    return url.replace("large", variant)
}

@Composable
private fun TrackArtwork(
    artworkUrl: String?,
    size: Dp,
    isPlaying: Boolean = false,
    useMorphing: Boolean = true,
    fallbackShape: Shape? = null,
    showLoadingShape: Boolean = false
) {
    val clipShape = if (showLoadingShape) {
        val infiniteTransition = rememberInfiniteTransition(label = "loadingTransition")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "loadingPhase"
        )
        val rotation = remember(phase) {
            val twoPi = (2f * Math.PI).toFloat()
            twoPi * (phase - kotlin.math.sin(twoPi * phase) / twoPi)
        }
        remember(phase, rotation) {
            LoadingExpressiveShape(phase, rotation)
        }
    } else if (useMorphing) {
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

        if (morphProgress <= 0.001f) {
            // Not morphing — so don't pay for a morphing shape. MorphingArtworkShape rebuilds a
            // 72-segment path every time its outline is invalidated, and with useMorphing
            // defaulting to true that ran for every row of every list, playing or not. It was
            // the dominant cost while scrolling.
            fallbackShape ?: RoundedCornerShape(if (size > 100.dp) 36.dp else 18.dp)
        } else {
            remember(morphProgress, rotationPhase) {
                MorphingArtworkShape(morphProgress, rotationPhase)
            }
        }
    } else {
        fallbackShape ?: RoundedCornerShape(if (size > 100.dp) 36.dp else 18.dp)
    }

    AsyncImage(
        model = artworkUrlForSize(artworkUrl, size),
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
        shape = CircleShape
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
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraLarge,
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
        shape = MaterialTheme.shapes.extraLarge,
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
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit
) {
    // `tonalElevation` only tints when the container is the `surface` role, so it was a
    // no-op against secondaryContainer. Depth now comes from a real shadow instead.
    Surface(
        onClick = onOpen,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = AppShapes.extraLargeIncreased,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The artwork does the identifying work here — a title alone made the bar
                // read as a generic control strip rather than "this track is playing".
                // Morphing shapes only fill ~82% of their box, which at 52dp left the
                // thumbnail small and floating. A plain rounded square fills the slot.
                TrackArtwork(
                    artworkUrl = artworkUrl,
                    size = 52.dp,
                    useMorphing = false,
                    fallbackShape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Progress lives in the text column, between artwork and button, so the
                // bar reads as belonging to this track rather than underlining the pill.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist.ifBlank { if (isPlaying) "Сейчас играет" else "На паузе" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(7.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                ExpressivePlayButton(
                    isPlaying = isPlaying,
                    onClick = onTogglePlay,
                    modifier = Modifier.size(52.dp),
                    iconSize = 26.dp
                )
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
        shape = MaterialTheme.shapes.extraLarge,
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
                    style = MaterialTheme.typography.titleMedium
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
private fun ReloginRequiredCard(onRelogin: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сессия SoundCloud истекла",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Обновить её автоматически не вышло — войдите в аккаунт заново.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Button(
                onClick = onRelogin,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Войти заново")
            }
        }
    }
}

@Composable
private fun ClientIdExpiredWarningCard(onOpenSettings: () -> Unit, onAutoRefresh: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
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
                        style = MaterialTheme.typography.titleMedium
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
                    shape = MaterialTheme.shapes.medium
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
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Эквалайзер",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Настройка звуковых частот и пресетов",
                        style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.titleSmall
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
                    style = MaterialTheme.typography.titleSmall
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
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${currentLevel / 100} dB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
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
                        color = AppTheme.brand.soundCloud.color
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
                        textAlign = TextAlign.Center
                    )
                }
            }

            // WebView Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(AppShapes.bottomSheet)
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
                        // This overlay sits on the WebView's own white page, not on a theme
                        // surface, so it uses the unharmonized brand color — the harmonized
                        // one is tuned for contrast against the scheme, not against white.
                        CircularProgressIndicator(color = SoundCloudBrandSource)
                    }
                }
            }
        }

        // Authentication overlay
        if (isLoggingIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AppTheme.brand.soundCloud.color)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Авторизация в SoundCloud...",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * A sliding-pill selector.
 *
 * M3's stock [SegmentedButton] is the utilitarian choice — outlined cells with a check
 * mark on the selected one. This keeps the app's springier take: a single pill that
 * travels to the selection. It's built entirely from M3 color roles and a full-round
 * shape, so it still moves with the palette; only the motion is bespoke.
 */
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
            .height(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            val cellWidth = maxWidth / items.size

            val offset by animateDpAsState(
                targetValue = cellWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "segmentedOffset"
            )

            Box(
                modifier = Modifier
                    .offset(x = offset)
                    .width(cellWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )

            Row(modifier = Modifier.fillMaxSize()) {
                items.forEachIndexed { index, text ->
                    val isSelected = index == selectedIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(220),
                        label = "segmentedTextColor"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectedIndexChanged(index)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.titleMedium,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
        shape = AppShapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.size(82.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.QueueMusic,
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
    onMoveDownloadedToDownloads: () -> Unit,
    onDeletePlaylist: () -> Unit = {}
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
            TopBar(
                title = "",
                onBack = onBack,
                trailing = {
                    HomeIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Удалить плейлист",
                        onClick = onDeletePlaylist
                    )
                }
            )
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
                            .clip(AppShapes.extraLargeIncreased)
                            .clickable {
                                imagePicker.launch(arrayOf("image/*"))
                            }
                    ) {
                        if (playlist.artworkUrl != null) {
                            FolderArtwork(playlist.artworkUrl, 220.dp)
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = AppShapes.extraLargeIncreased,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.QueueMusic,
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
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
                                .padding(12.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
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
                                shape = MaterialTheme.shapes.large,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Text("Переместить скачанные в Скачанное")
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
        shape = AppShapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                val icon = if (isLikedPlaylist) Icons.Default.Favorite else Icons.AutoMirrored.Filled.QueueMusic
                Surface(
                    color = containerColor,
                    shape = MaterialTheme.shapes.extraLarge,
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
    onChangeArtwork: (String?) -> Unit,
    onHidePlaylist: () -> Unit = {}
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
            TopBar(
                title = "",
                onBack = onBack,
                trailing = {
                    HomeIconButton(
                        icon = Icons.Default.VisibilityOff,
                        contentDescription = "Скрыть плейлист",
                        onClick = onHidePlaylist
                    )
                }
            )
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
                            .clip(AppShapes.extraLargeIncreased)
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
                            val icon = if (isLikedPlaylist) Icons.Default.Favorite else Icons.AutoMirrored.Filled.QueueMusic
                            Surface(
                                color = containerColor,
                                shape = AppShapes.extraLargeIncreased,
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
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f))
                                .padding(12.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
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
                    TrackCard(
                        track = track,
                        isFavorite = favorite != null,
                        isSelected = track.id == currentTrackId,
                        downloadState = favorite?.downloadState,
                        progress = downloadProgress[track.id],
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
            AppTopBar(
                modifier = Modifier.padding(horizontal = 16.dp),
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                leadingDescription = "Назад",
                onLeadingClick = onDeselectPlaylist,
                // No title here: AlbumHeroCard right below already carries it.
                title = {}
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    AlbumHeroCard(
                        artworkUrl = selectedPlaylist.artworkUrl,
                        title = selectedPlaylist.title ?: "Без названия",
                        subtitle = artist.username.orEmpty(),
                        trackCount = selectedPlaylist.tracks.size,
                        isYandex = selectedPlaylist.permalinkUrl?.contains("yandex") == true
                    )
                }

                if (selectedPlaylist.tracks.isEmpty()) {
                    item {
                        EmptyState("Здесь пока нет треков.")
                    }
                } else {
                    item {
                        PagedTrackList(
                            tracks = selectedPlaylist.tracks,
                            favorites = favorites,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            downloadProgress = downloadProgress,
                            onPlayTrack = onPlayTrack,
                            onFavoriteClick = onFavoriteClick,
                            perPage = 4
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
            AppTopBar(
                modifier = Modifier.padding(horizontal = 16.dp),
                leadingIcon = Icons.AutoMirrored.Filled.ArrowBack,
                leadingDescription = "Назад",
                onLeadingClick = onBack,
                title = {
                    // The bar used to carry no title at all, so a scrolled artist page had
                    // nothing identifying it.
                    Text(
                        text = artist.username.orEmpty(),
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ArtistHeroCard(
                        artist = artist,
                        albumCount = playlists.size
                    )
                }

                if (!artist.description.isNullOrBlank()) {
                    item {
                        ExpandableDescription(text = artist.description)
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
                                color = if (artist.permalinkUrl?.startsWith("yandex") == true) AppTheme.brand.yandex.color else MaterialTheme.colorScheme.primary,
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
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        item {
                            PagedTrackList(
                                tracks = tracks,
                                favorites = favorites,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                downloadProgress = downloadProgress,
                                onPlayTrack = onPlayTrack,
                                onFavoriteClick = onFavoriteClick,
                                perPage = 4
                            )
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item {
                            Text(
                                text = "Альбомы и плейлисты",
                                style = MaterialTheme.typography.titleLarge,
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
                                        shape = MaterialTheme.shapes.extraLarge,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(124.dp)
                                                    .clip(MaterialTheme.shapes.large)
                                            ) {
                                                if (playlist.artworkUrl != null) {
                                                    FolderArtwork(playlist.artworkUrl, 124.dp)
                                                } else {
                                                    Surface(
                                                        color = if (isYandex) AppTheme.brand.yandex.container else MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = MaterialTheme.shapes.large,
                                                        modifier = Modifier.fillMaxSize()
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                Icons.Default.Album,
                                                                contentDescription = null,
                                                                tint = if (isYandex) AppTheme.brand.yandex.onContainer else MaterialTheme.colorScheme.onSecondaryContainer,
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
private fun SheetActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color,
    content: Color
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    // A real M3 modal bottom sheet rather than a Dialog imitating one: this brings the
    // spec scrim, drag handle, swipe-to-dismiss, predictive back and inset handling.
    val sheetState = rememberModalBottomSheetState()
    val sheetScope = rememberCoroutineScope()
    // Actions taken inside the sheet should play the same close animation as a swipe or a
    // scrim tap, so hide the sheet first and only then tear down the composition.
    val dismissSheet: () -> Unit = {
        sheetScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    var showPlaylistSelection by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TrackArtwork(artworkUrl = track.artworkUrl, size = 56.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title ?: "Unknown Track",
                                style = MaterialTheme.typography.titleMedium,
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column {
                                // M3 ListItem gives these rows the spec's two-line height,
                                // headline/supporting type roles and content colors, instead
                                // of a hand-built Row + Column approximating them.
                                ListItem(
                                    headlineContent = { Text("Добавить в плейлист") },
                                    supportingContent = { Text("Сохраните этот трек в свои подборки") },
                                    leadingContent = {
                                        SheetActionIcon(
                                            icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                                            container = MaterialTheme.colorScheme.primaryContainer,
                                            content = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    modifier = Modifier.clickable { showPlaylistSelection = true }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                ListItem(
                                    headlineContent = { Text("Отправить ссылку на трек") },
                                    supportingContent = { Text("Поделитесь треком с друзьями") },
                                    leadingContent = {
                                        SheetActionIcon(
                                            icon = Icons.Default.Share,
                                            container = MaterialTheme.colorScheme.primaryContainer,
                                            content = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    modifier = Modifier.clickable {
                                        onShare()
                                        dismissSheet()
                                    }
                                )

                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )

                                ListItem(
                                    headlineContent = { Text("Перескачать трек") },
                                    supportingContent = { Text("Скачать файл заново на устройство") },
                                    leadingContent = {
                                        SheetActionIcon(
                                            icon = Icons.Default.Refresh,
                                            container = MaterialTheme.colorScheme.primaryContainer,
                                            content = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    modifier = Modifier.clickable {
                                        onRedownload()
                                        dismissSheet()
                                    }
                                )
                            }
                        }

                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { showPlaylistSelection = false }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад"
                                )
                            }
                            Text(
                                text = "Выберите плейлист",
                                style = MaterialTheme.typography.titleMedium
                            )
                            FilledIconButton(
                                onClick = { showCreatePlaylistDialog = true }
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Создать плейлист"
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
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.extraLarge,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                ) {
                                    itemsIndexed(playlists) { index, playlist ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onAddToPlaylist(playlist)
                                                    dismissSheet()
                                                }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            if (!playlist.artworkUrl.isNullOrBlank()) {
                                                FolderArtwork(playlist.artworkUrl, size = 44.dp)
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.medium),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = playlist.name,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = "${playlist.tracks.size} треков",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (index < playlists.size - 1) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 14.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = dismissSheet,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                "Отмена",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        style = MaterialTheme.typography.titleLarge
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



