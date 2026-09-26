@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import kotlin.math.roundToInt
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
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.runtime.snapshotFlow
import com.example.myapplication.data.LyricLine
import com.example.myapplication.data.YtAuth
import com.example.myapplication.data.YtShelf
import com.example.myapplication.data.youTubeTrackId
import com.example.myapplication.data.youTubeVideoId
import androidx.compose.material.icons.filled.SmartDisplay
import kotlinx.coroutines.flow.first
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
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.runtime.DisposableEffect
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
import com.example.myapplication.data.ArtworkUrls
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
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.unit.DpSize
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.example.myapplication.data.sourceKey
import androidx.compose.material.icons.filled.Radio

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
    val trackVideo by viewModel.trackVideo.collectAsState()
    val pendingTrackVideo by viewModel.pendingTrackVideo.collectAsState()
    val currentTrackId by viewModel.currentTrackId.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val downloadedFolderArtworkUri by viewModel.downloadedFolderArtworkUri.collectAsState()
    val clientId by viewModel.clientId.collectAsState()
    val oauthToken by viewModel.oauthToken.collectAsState()
    val mixSection by viewModel.mixSection.collectAsState()
    val stationSection by viewModel.stationSection.collectAsState()
    val trendingSection by viewModel.trendingSection.collectAsState()
    val lyrics by viewModel.lyrics.collectAsState()
    val ytHome by viewModel.ytHome.collectAsState()
    val ytHomeLoading by viewModel.ytHomeLoading.collectAsState()
    val ytHomeError by viewModel.ytHomeError.collectAsState()
    val ytMusicAccount by viewModel.ytMusicAccount.collectAsState()
    val ytLoginOpen by viewModel.ytLoginOpen.collectAsState()
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
    val homeSelectedTab by viewModel.homeSelectedTab.collectAsState()
    var showTrackActionsDialog by remember { mutableStateOf(false) }
    val showDebugPercentage by viewModel.showDebugPercentage.collectAsState()
    val downloadedPercentages by viewModel.downloadedPercentages.collectAsState()
    val isAllArtistTracksLoaded by viewModel.isAllArtistTracksLoaded.collectAsState()
    val backgroundMotion by viewModel.settingsRepo.backgroundMotion.collectAsState()
    val playerCoverColors by viewModel.settingsRepo.playerCoverColors.collectAsState()

    // The playing track's cover colours are worked out before the player opens, so it opens in
    // them instead of fading over from the app's own every time.
    val coverContext = LocalContext.current
    LaunchedEffect(playerCoverColors, currentPlayingTrack?.artworkUrl) {
        val url = currentPlayingTrack?.artworkUrl
        if (playerCoverColors && !url.isNullOrBlank()) prefetchCoverColors(coverContext, url)
    }

    val yandexPlaylists by viewModel.yandexPlaylists.collectAsState()
    val yandexToken by viewModel.yandexToken.collectAsState()
    val hasYandexToken = yandexToken.isNotEmpty()
    val yandexLoginUrl by viewModel.yandexLoginUrl.collectAsState()
    val antiBotCaptchaUrl by viewModel.antiBotCaptchaUrl.collectAsState()

    val searchOpenedPlaylist by viewModel.searchOpenedPlaylist.collectAsState()

    val downloadedTracks = remember(favorites) { favorites.filter { it.downloadState == DownloadState.DOWNLOADED } }

    // Checks the SoundCloud session whenever the app comes back, so a token that lapsed in the
    // meantime is renewed before the next tap needs it.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.onAppForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = selectedTrack != null && !isLoggedOut) {
        viewModel.closeTrack()
    }

    BackHandler(enabled = selectedMix != null && selectedTrack == null && !isLoggedOut) {
        viewModel.closeMix()
    }

    BackHandler(enabled = selectedTrack == null && selectedMix == null && screen != AppScreen.HOME && !isLoggedOut) {
        when (screen) {
            AppScreen.SEARCH -> if (searchOpenedPlaylist != null) {
                viewModel.closeSearchPlaylist()
            } else {
                viewModel.closeSearch()
            }
            AppScreen.DOWNLOADS -> viewModel.closeDownloads()
            AppScreen.PLAYLISTS -> viewModel.closePlaylists()
            AppScreen.SETTINGS -> viewModel.closeSettings()
            AppScreen.MIX_DETAIL -> viewModel.closeMix()
            AppScreen.PLAYLIST_DETAIL -> viewModel.closePlaylist()
            AppScreen.YANDEX_PLAYLIST_DETAIL -> viewModel.deselectYandexPlaylist()
            AppScreen.YTM_SET_DETAIL -> viewModel.closeYtSet()
            AppScreen.ARTIST_DETAIL -> viewModel.closeArtist()
            AppScreen.HOME -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The full player is opaque; nothing behind it needs a frame, or the accelerometer.
        ExpressiveBackground(motionEnabled = backgroundMotion, animated = selectedTrack == null)

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

        antiBotCaptchaUrl?.let { url ->
            AntiBotCaptchaDialog(
                captchaUrl = url,
                onSolved = viewModel::onAntiBotCaptchaSolved,
                onDismiss = viewModel::dismissAntiBotCaptcha
            )
        }

        if (ytLoginOpen) {
            YtMusicLoginDialog(
                onCaptured = viewModel::onYtMusicLoginCaptured,
                onDismiss = viewModel::cancelYtMusicLogin
            )
        }

        val albumLibrary = remember(playlists) {
            AlbumLibrary(
                likedBySource = playlists.mapNotNull { p -> p.sourceKey?.let { it to p } }.toMap(),
                onToggleLike = { album, artistName ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleAlbumLike(album, artistName)
                },
                onDownload = { playlist ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.downloadPlaylist(playlist.id)
                }
            )
        }
        if (isLoggedOut) {
            SoundCloudLoginScreen(viewModel = viewModel)
        } else androidx.compose.runtime.CompositionLocalProvider(LocalAlbumLibrary provides albumLibrary) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    // Screens share the top-bar geometry now, so a soft fade+scale makes the bar
                    // look like it stays put while only the content beneath it swaps.
                    // No size animation: every screen fills the window, and animating the size only
                    // ever showed when a screen drew nothing for a moment — it then grew out of the
                    // top-left corner.
                    ((fadeIn(tween(240)) + scaleIn(initialScale = 0.97f, animationSpec = tween(240))) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 1.02f, animationSpec = tween(160)))) using null
                },
                label = "screenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.HOME -> HomeScreen(
                        mixSection = mixSection,
                        stationSection = stationSection,
                        trendingSection = trendingSection,
                        ytShelves = ytHome,
                        ytConnected = ytMusicAccount != null,
                        yandexConnected = hasYandexToken,
                        ytLoading = ytHomeLoading,
                        ytError = ytHomeError,
                        onOpenYtSet = { set ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openYtSet(set)
                        },
                        onYtLogin = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openYtMusicLogin()
                        },
                        onReloadYt = viewModel::loadYtHome,
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
                        onReloadMixes = viewModel::loadMixes,
                        updates = viewModel.updates
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
                        val searchSource by viewModel.searchSource.collectAsState()
                        val ytSearch by viewModel.ytSearch.collectAsState()
                        // SoundCloud always; the others once connected in settings.
                        val searchSources = buildList {
                            add(SearchSource.SOUNDCLOUD)
                            if (hasYandexToken) add(SearchSource.YANDEX)
                            if (ytMusicAccount != null) add(SearchSource.YOUTUBE)
                        }
                        val yandexSearchQuery by viewModel.yandexSearchQuery.collectAsState()
                        val yandexTracks by viewModel.yandexTracks.collectAsState()
                        val yandexLoading by viewModel.yandexLoading.collectAsState()
                        val yandexError by viewModel.yandexError.collectAsState()
                        val yandexHasMore by viewModel.yandexHasMore.collectAsState()
                        val yandexLoadingMore by viewModel.yandexLoadingMore.collectAsState()
                        val searchAlbums by viewModel.searchAlbums.collectAsState()
                        val searchPlaylists by viewModel.searchPlaylists.collectAsState()
                        val searchArtists by viewModel.searchArtists.collectAsState()
                        val yandexSearchAlbums by viewModel.yandexSearchAlbums.collectAsState()
                        val yandexSearchPlaylists by viewModel.yandexSearchPlaylists.collectAsState()
                        val yandexSearchArtists by viewModel.yandexSearchArtists.collectAsState()
                        val searchHasMore by viewModel.searchHasMore.collectAsState()
                        val searchLoadingMore by viewModel.searchLoadingMore.collectAsState()
                        val searchPlaylistLoading by viewModel.searchPlaylistLoading.collectAsState()
                        val searchPlaylistError by viewModel.searchPlaylistError.collectAsState()
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
                            source = searchSource,
                            sources = searchSources,
                            onSearchSourceChanged = viewModel::setSearchSource,
                            yandexQuery = yandexSearchQuery,
                            yandexTracks = yandexTracks,
                            yandexLoading = yandexLoading,
                            yandexError = yandexError,
                            onYandexQueryChange = viewModel::onYandexSearchQueryChange,
                            ytSearch = ytSearch,
                            onYtQueryChange = viewModel::onYtSearchQueryChange,
                            albums = searchAlbums,
                            playlists = searchPlaylists,
                            artists = searchArtists,
                            yandexAlbums = yandexSearchAlbums,
                            yandexPlaylists = yandexSearchPlaylists,
                            yandexArtists = yandexSearchArtists,
                            onOpenArtist = { artist ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openArtistFromSearch(artist)
                            },
                            hasMore = when (searchSource) {
                                SearchSource.SOUNDCLOUD -> searchHasMore
                                SearchSource.YANDEX -> yandexHasMore
                                SearchSource.YOUTUBE -> ytSearch.page?.continuation != null
                            },
                            isLoadingMore = when (searchSource) {
                                SearchSource.SOUNDCLOUD -> searchLoadingMore
                                SearchSource.YANDEX -> yandexLoadingMore
                                SearchSource.YOUTUBE -> ytSearch.loadingMore
                            },
                            onLoadMore = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when (searchSource) {
                                    SearchSource.SOUNDCLOUD -> viewModel.loadMoreSearchTracks()
                                    SearchSource.YANDEX -> viewModel.loadMoreYandexSearchTracks()
                                    SearchSource.YOUTUBE -> viewModel.loadMoreYtSearchTracks()
                                }
                            },
                            openedPlaylist = searchOpenedPlaylist,
                            isPlaylistLoading = searchPlaylistLoading,
                            playlistError = searchPlaylistError,
                            onOpenPlaylist = { playlist ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openSearchPlaylist(playlist)
                            },
                            onClosePlaylist = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.closeSearchPlaylist()
                            },
                            onPlayPlaylistTrack = { track, queue ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playQueuedTrack(track, queue)
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
                        val likesPushStatus by viewModel.likesPushStatus.collectAsState()
                        SettingsScreen(
                            settingsRepository = viewModel.settingsRepo,
                            soundcloudLikesSyncStatus = soundcloudLikesSyncStatus,
                            likesPushStatus = likesPushStatus,
                            pushLikesToSoundCloud = viewModel::pushLikesToSoundCloud,
                            resetLikesPushStatus = viewModel::resetLikesPushStatus,
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
                            },
                            ytMusicAccount = ytMusicAccount,
                            onYtMusicLoginClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openYtMusicLogin()
                            },
                            onYtMusicLogoutClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.logoutYtMusic()
                            },
                            updates = viewModel.updates
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
                                },
                                onShuffle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.playPlaylistShuffled(playlist)
                                },
                                onDownloadAll = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.downloadPlaylist(playlist.id)
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

                    AppScreen.YTM_SET_DETAIL -> {
                        val ytOpenedSet by viewModel.ytOpenedSet.collectAsState()
                        val ytSetLoading by viewModel.ytSetLoading.collectAsState()
                        val ytSetError by viewModel.ytSetError.collectAsState()
                        val set = ytOpenedSet
                        if (set != null) {
                            SetDetailContent(
                                playlist = set,
                                subtitle = set.user?.username.orEmpty(),
                                isLoading = ytSetLoading,
                                error = ytSetError,
                                favorites = favorites,
                                currentTrackId = currentTrackId,
                                isPlaying = isPlaying,
                                downloadProgress = downloadProgress,
                                onBack = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.closeYtSet()
                                },
                                onPlayTrack = { track ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.playQueuedTrack(track, set.tracks)
                                },
                                onFavoriteClick = { track ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleFavorite(track)
                                }
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AppLoadingIndicator()
                            }
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
                                },
                                onShuffle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.playShuffled(currentArtistTracks)
                                }
                            )
                        }
                        if (currentArtist == null) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AppLoadingIndicator()
                            }
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
                        isActive = playingMixId == mix.id,
                        onShuffle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playShuffled(mixTracks, fromMix = true)
                        },
                        onTogglePlay = viewModel::togglePlayPause,
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

            // On home the floating toolbar owns the bottom edge; the mini player sits on top of it.
            val miniPlayerLift by animateDpAsState(
                targetValue = if (screen == AppScreen.HOME && selectedMix == null) HomeToolbarClearance else 0.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "miniPlayerLift"
            )
            AnimatedVisibility(
                visible = currentTrackTitle != null && selectedTrack == null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + miniPlayerLift)
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
                    CoverTheme(enabled = playerCoverColors, artworkUrl = track.artworkUrl) {
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
                        lyrics = lyrics?.takeIf { it.trackId == track.id }?.lines,
                        video = trackVideo?.takeIf { it.trackId == track.id },
                        upcomingVideo = pendingTrackVideo?.takeIf { it.trackId == track.id },
                        livePosition = viewModel::livePositionMs,
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
            },
            onRadio = if (capturedTrack.youTubeVideoId != null) {
                { viewModel.playYtRadio(capturedTrack) }
            } else {
                null
            }
        )
    }
}

/** The places home switches between, in the order the floating toolbar shows them. */
private enum class HomeCategory(val title: String, val icon: ImageVector) {
    Mixes("Миксы", Icons.AutoMirrored.Filled.QueueMusic),
    Stations("Станции", Icons.Default.Radio),
    Trending("Тренды", Icons.AutoMirrored.Filled.TrendingUp),
    YouTube("YouTube Music", Icons.Default.SmartDisplay),
    Library("Медиатека", Icons.Default.LibraryMusic),
    MyMusic("Моя музыка", Icons.Default.Download)
}

/**
 * YouTube Music's home as hero tiles: each row of songs becomes one tile that opens with those
 * songs ("Быстрый выбор"), and each playlist, mix or album is a tile of its own, captioned with
 * the row it came from.
 */
private fun ytHeroItems(shelves: List<YtShelf>, onOpen: (SoundCloudPlaylist) -> Unit): List<HeroItem> =
    shelves.flatMap { shelf ->
        buildList {
            if (shelf.tracks.isNotEmpty()) {
                val row = SoundCloudPlaylist(
                    id = youTubeTrackId("shelf:" + shelf.title),
                    title = shelf.title,
                    tracks = shelf.tracks,
                    trackCount = shelf.tracks.size,
                    artworkUrl = shelf.tracks.first().artworkUrl,
                    user = SoundCloudUser(username = "YouTube Music")
                )
                add(
                    HeroItem(
                        key = "yt-row-${row.id}",
                        title = shelf.title,
                        subtitle = plural(shelf.tracks.size, "трек", "трека", "треков"),
                        artworkUrl = row.artworkUrl,
                        onClick = { onOpen(row) }
                    )
                )
            }
            shelf.sets.forEach { set ->
                add(
                    HeroItem(
                        key = "yt-set-${set.id}",
                        title = set.title ?: "Без названия",
                        subtitle = shelf.title,
                        artworkUrl = set.artworkUrl,
                        onClick = { onOpen(set) }
                    )
                )
            }
        }
    }.distinctBy { it.key }.take(40)

/** What home's floating toolbar takes off the bottom edge, including its gap to the mini player. */
private val HomeToolbarClearance = 64.dp + 12.dp

@Composable
private fun HomeScreen(
    mixSection: MixSection?,
    stationSection: MixSection?,
    trendingSection: MixSection?,
    ytShelves: List<YtShelf>,
    ytConnected: Boolean,
    yandexConnected: Boolean,
    ytLoading: Boolean,
    ytError: String?,
    onOpenYtSet: (SoundCloudPlaylist) -> Unit,
    onYtLogin: () -> Unit,
    onReloadYt: () -> Unit,
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
    onReloadMixes: () -> Unit,
    updates: com.example.myapplication.data.UpdateRepository
) {
    val haptic = LocalHapticFeedback.current
    // A service not connected in settings has no row here at all.
    val categories = remember(ytConnected, yandexConnected) {
        HomeCategory.entries.filter { category ->
            when (category) {
                HomeCategory.YouTube -> ytConnected
                HomeCategory.Library -> yandexConnected
                else -> true
            }
        }
    }
    // [selectedTab] is the category's ordinal, so it survives rows coming and going.
    val selectedIndex = categories.indexOf(HomeCategory.entries.getOrNull(selectedTab)).coerceAtLeast(0)

    // Categories still stack vertically — a swipe up or down moves between them, as it always
    // did — and the floating toolbar at the bottom is the same axis as buttons.
    val categoryPager = rememberPagerState(initialPage = selectedIndex) { categories.size }
    val scope = rememberCoroutineScope()
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }

    // Only a page the pager has come to rest on is remembered. Reporting every page it passes
    // made a jump from "Моя музыка" to "Миксы" save "Медиатека" on the way, and the saved tab then
    // pulled the pager back there mid-flight.
    LaunchedEffect(categoryPager.settledPage) {
        val settled = categories.getOrNull(categoryPager.settledPage) ?: return@LaunchedEffect
        if (settled.ordinal != selectedTab) {
            onTabSelected(settled.ordinal)
        }
    }
    // A row appearing or leaving shifts the pages; stay on the same category.
    LaunchedEffect(categories) {
        if (categoryPager.currentPage != selectedIndex) categoryPager.scrollToPage(selectedIndex)
    }

    val mixes = mixSection?.mixes.orEmpty()
    val stations = stationSection?.mixes.orEmpty()
    val trending = trendingSection?.mixes.orEmpty()
    val ytItems = remember(ytShelves) { ytHeroItems(ytShelves, onOpenYtSet) }
    val subtitles = mapOf(
        HomeCategory.Mixes to if (mixes.isEmpty()) {
            "Подборки для тебя"
        } else {
            plural(mixes.size, "подборка", "подборки", "подборок") + " для тебя"
        },
        HomeCategory.Stations to if (stations.isEmpty()) {
            "Станции по артистам"
        } else {
            plural(stations.size, "станция", "станции", "станций")
        },
        HomeCategory.Trending to if (trending.isEmpty()) {
            "Чарты SoundCloud по жанрам"
        } else {
            "Чарты: " + plural(trending.size, "жанр", "жанра", "жанров")
        },
        HomeCategory.YouTube to when {
            ytItems.isEmpty() -> "Подборки для тебя"
            else -> plural(ytItems.size, "подборка", "подборки", "подборок") + " для тебя"
        },
        HomeCategory.Library to if (yandexPlaylists.isEmpty()) {
            "Плейлисты Яндекс Музыки"
        } else {
            plural(yandexPlaylists.size, "плейлист", "плейлиста", "плейлистов") + " Яндекс Музыки"
        },
        HomeCategory.MyMusic to plural(downloadedCount, "трек", "трека", "треков") + " на устройстве"
    )
    val hasWarning = clientId.isBlank() || needsRelogin || isClientIdExpired

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 12.dp, end = 16.dp),
                verticalAlignment = Alignment.Top
            ) {
                AnimatedContent(
                    targetState = categories.getOrElse(categoryPager.currentPage) { categories.first() },
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        (slideInVertically { h -> if (forward) h / 2 else -h / 2 } + fadeIn()) togetherWith
                            (slideOutVertically { h -> if (forward) -h / 2 else h / 2 } + fadeOut())
                    },
                    modifier = Modifier.weight(1f),
                    label = "homeTitle"
                ) { category ->
                    Column {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitles[category].orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
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

            UpdateBanner(
                updates = updates,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp)
            )

            VerticalPager(
                state = categoryPager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (categories.getOrElse(page) { categories.first() }) {
                    HomeCategory.Mixes, HomeCategory.Stations, HomeCategory.Trending -> {
                        val category = categories[page]
                        MixCarousel(
                            mixes = when (category) {
                                HomeCategory.Stations -> stations
                                HomeCategory.Trending -> trending
                                else -> mixes
                            },
                            kind = category,
                            isLoading = mixesLoading,
                            hasOauthToken = hasOauthToken,
                            errorMessage = if (category == HomeCategory.Mixes) mixesError else null,
                            loadingMixId = loadingMixId,
                            playingMixId = playingMixId,
                            isPlaying = isPlaying,
                            onOpenMix = onOpenMix,
                            onReload = onReloadMixes,
                            onOpenSettings = onOpenSettings
                        )
                    }

                    HomeCategory.YouTube -> when {
                        ytItems.isEmpty() && ytError != null -> CarouselMessage(
                            text = ytError,
                            actionLabel = "Повторить",
                            onAction = onReloadYt
                        )
                        ytItems.isEmpty() && ytLoading -> CarouselSkeleton()
                        ytItems.isEmpty() -> CarouselMessage(
                            text = "Подборки YouTube Music пока не загрузились.",
                            actionLabel = "Обновить",
                            onAction = onReloadYt
                        )
                        else -> HomeHeroCarousel(items = ytItems)
                    }

                    HomeCategory.Library -> {
                        if (yandexPlaylists.isEmpty()) {
                            CarouselEmptyText("Плейлисты Яндекс Музыки пока не загрузились.")
                        } else {
                            HomeHeroCarousel(
                                items = yandexPlaylists.map { playlist ->
                                    HeroItem(
                                        key = "yandex-${playlist.id}",
                                        title = playlist.title ?: "Без названия",
                                        subtitle = plural(playlist.trackCount, "трек", "трека", "треков"),
                                        artworkUrl = playlist.artworkUrl,
                                        icon = if (playlist.id == -100L) Icons.Rounded.Favorite else Icons.Default.Album,
                                        onClick = { onOpenYandexPlaylist(playlist) }
                                    )
                                }
                            )
                        }
                    }

                    HomeCategory.MyMusic -> HomeHeroCarousel(
                        items = buildList {
                            add(
                                HeroItem(
                                    key = "downloads",
                                    title = "Скачанное",
                                    subtitle = plural(downloadedCount, "трек", "трека", "треков"),
                                    artworkUrl = downloadedFolderArtworkUri,
                                    icon = Icons.Default.Download,
                                    onClick = onOpenDownloads
                                )
                            )
                            // Liked albums sit right next to "Скачанное", newest first; hand-made
                            // playlists follow.
                            playlists.sortedByDescending { it.isLikedAlbum }.forEach { playlist ->
                                val count = plural(playlist.tracks.size, "трек", "трека", "треков")
                                add(
                                    HeroItem(
                                        key = "local-${playlist.id}",
                                        title = playlist.name,
                                        subtitle = if (playlist.isLikedAlbum) {
                                            listOfNotNull(playlist.artist?.takeIf { it.isNotBlank() }, count)
                                                .joinToString(" · ")
                                        } else {
                                            count
                                        },
                                        artworkUrl = playlist.artworkUrl,
                                        icon = if (playlist.isLikedAlbum) Icons.Default.Album else Icons.AutoMirrored.Filled.QueueMusic,
                                        onClick = { onOpenPlaylist(playlist) }
                                    )
                                )
                            }
                            add(
                                HeroItem(
                                    key = "create",
                                    title = "Создать плейлист",
                                    subtitle = "Своя подборка",
                                    artworkUrl = null,
                                    icon = Icons.Default.Add,
                                    onClick = { showCreatePlaylistDialog = true }
                                )
                            )
                        }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(
                    16.dp + HomeToolbarClearance + if (playerVisible) 72.dp + 12.dp else 0.dp
                )
            )
        }

        HomeToolbar(
            categories = categories,
            selected = categoryPager.currentPage,
            onSelect = { index ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch { categoryPager.animateScrollToPage(index) }
            },
            onSearch = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onOpenSearch()
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
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
 * Material 3 Expressive's floating toolbar: the categories on the panel tone, the chosen one an
 * accent pill, and search beside it as its own floating button. Icons only: six labelled pills
 * don't fit a phone's width, and the title above the carousel already names the chosen one.
 */
@Composable
private fun HomeToolbar(
    categories: List<HomeCategory>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = 8.dp)
                .height(64.dp),
            shape = CircleShape,
            color = PanelColors.container,
            contentColor = PanelColors.content,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEachIndexed { index, category ->
                    HomeToolbarItem(
                        category = category,
                        selected = index == selected,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
        Surface(
            onClick = onSearch,
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(20.dp),
            color = androidx.compose.ui.graphics.lerp(PanelColors.container, PanelColors.content, 0.08f),
            contentColor = PanelColors.accent,
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Search, contentDescription = "Поиск", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun HomeToolbarItem(category: HomeCategory, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (selected) PanelColors.accent else Color.Transparent,
        animationSpec = tween(250),
        label = "toolbarItemContainer"
    )
    val content by animateColorAsState(
        targetValue = if (selected) PanelColors.onAccent else PanelColors.content,
        animationSpec = tween(250),
        label = "toolbarItemContent"
    )
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = CircleShape,
        color = container,
        contentColor = content
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(category.icon, contentDescription = category.title, modifier = Modifier.size(24.dp))
        }
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
 * Rounded square with an accent glyph on the panel tone — the launcher's themed-icon look.
 * Separation from the backdrop comes from the container tone alone — the washes behind it are
 * held low enough for that to hold (see ExpressiveBackground).
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
        color = PanelColors.container,
        contentColor = PanelColors.accent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = PanelColors.accent,
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
    // Rows sit flush, each page one rounded container; every row but the first carries a
    // one-dp divider.
    val rowHeight = TrackRowHeight + 1.dp
    val rowGap = 0.dp
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
                        onFavoriteClick = { onFavoriteClick(track) },
                        position = groupPosition(index - from, to - from)
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
 * "Об артисте": the bio as a card at the end of the page, three lines until tapped. Up in the
 * header it read as part of the numbers, and any "more" control next to it looked tacked on.
 */
@Composable
private fun ExpandableDescription(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val chevronTurn by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "bioChevron"
    )
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier
                .padding(start = 18.dp, top = 16.dp, end = 12.dp, bottom = 16.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Свернуть" else "Читать полностью",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { rotationZ = chevronTurn }
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

/** One cover in a home carousel: a mix, a station, a playlist, or an action tile. */
private data class HeroItem(
    val key: Any,
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
    val icon: ImageVector? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val onClick: () -> Unit
)

@Composable
private fun MixCarousel(
    mixes: List<SoundCloudMix>,
    kind: HomeCategory,
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
    val isStations = kind == HomeCategory.Stations
    val isTrending = kind == HomeCategory.Trending
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
                text = when {
                    isStations -> "Станции пока не загрузились."
                    isTrending -> "Тренды пока не загрузились."
                    else -> "Подборка пока не загрузилась."
                },
                actionLabel = "Обновить",
                onAction = onReload
            )
        }
        return
    }

    HomeHeroCarousel(
        items = mixes.map { mix ->
            HeroItem(
                key = mix.id,
                title = when {
                    isStations -> mix.title
                    isTrending -> localizedGenre(mix.title)
                    else -> localizedMixTitle(mix.title)
                },
                // Mixes ship their artist list in `description`. Stations put "Artist station"
                // there, which is not a caption — the station's artist is its title. A genre's
                // description is an English sentence about the chart.
                subtitle = when {
                    isStations -> "Станция"
                    isTrending -> "Чарт недели"
                    else -> mix.description?.takeIf { it.isNotBlank() }
                },
                artworkUrl = artworkUrlForSize(mix.artworkUrl, 500.dp),
                isPlaying = playingMixId == mix.id && isPlaying,
                isLoading = loadingMixId == mix.id,
                onClick = { onOpenMix(mix) }
            )
        }
    )
}

@Composable
private fun CarouselEmptyText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A centred hero carousel: the cover in focus always sits in the middle — the first one too — and
 * its neighbours squeeze into narrow strips on both sides as they leave, rather than sliding away
 * whole. Each cover is masked and re-centred inside its strip, never squashed.
 *
 * Built on a pager rather than Material's HorizontalCenteredHeroCarousel, which shifts its first
 * and last items to the edges, so the focused cover is not centred there. The geometry closes by
 * itself: pages are [coverWidth] wide and centred by the content padding, so a neighbour's page
 * starts exactly [HeroStrip] plus the margin from the screen edge, and the mask only has to
 * narrow from the full cover to that strip as the page moves out.
 */
@Composable
private fun HomeHeroCarousel(items: List<HeroItem>) {
    if (items.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { items.size }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val captionHeight = 88.dp
        val margin = 16.dp
        val coverWidth = maxWidth - (margin + HeroStrip + HeroGap) * 2
        val sidePadding = (maxWidth - coverWidth) / 2
        // Close to square: a little taller than wide at most, and never taller than the room left.
        val carouselHeight = (maxHeight - captionHeight - 24.dp).coerceIn(160.dp, coverWidth * 1.08f)
        val density = LocalDensity.current
        val coverPx = with(density) { coverWidth.toPx() }
        val stripPx = with(density) { HeroStrip.toPx() }
        val radiusPx = with(density) { 32.dp.toPx() }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            HorizontalPager(
                state = pagerState,
                pageSize = androidx.compose.foundation.pager.PageSize.Fixed(coverWidth),
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = HeroGap,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(carouselHeight)
            ) { page ->
                val item = items[page]
                // Signed distance from the focused slot: positive to the right.
                fun distance() = page - (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                fun visibleWidth() = lerp(coverPx, stripPx, distance().absoluteValue.coerceIn(0f, 1f))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val d = distance()
                            val w = visibleWidth()
                            // The mask keeps the side that faces the focused cover.
                            val left = if (d > 0f) 0f else coverPx - w
                            clip = true
                            shape = HeroStripShape(left, w, radiusPx)
                            // The cover in front floats above the backdrop; strips sit on it.
                            shadowElevation = (1f - d.absoluteValue.coerceIn(0f, 1f)) * 14.dp.toPx()
                        }
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            // A strip brings its cover to the front; the cover in front opens.
                            if (page == pagerState.currentPage) {
                                item.onClick()
                            } else {
                                scope.launch { pagerState.animateScrollToPage(page) }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Re-centres the picture inside whatever the mask leaves visible.
                                translationX = -kotlin.math.sign(distance()) * (coverPx - visibleWidth()) / 2f
                            }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!item.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = item.artworkUrl,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else if (item.icon != null) {
                            // A glyph cut down to a strip reads as a glitch, so it fades out first.
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = (1f - distance().absoluteValue * 1.8f).coerceIn(0f, 1f)
                                    }
                            ) {
                                IconCover(icon = item.icon, iconSize = 84.dp)
                            }
                        }
                        if (item.isPlaying) {
                            NowPlayingBadge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp)
                            )
                        }
                        if (item.isLoading) {
                            AppContainedLoadingIndicator(modifier = Modifier.size(72.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val current = items.getOrNull(pagerState.currentPage) ?: items.first()
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                contentKey = { it.key },
                label = "heroCaption"
            ) { item ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(captionHeight)
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!item.subtitle.isNullOrBlank()) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** Width a neighbouring cover narrows to, and the gap between covers. */
private val HeroStrip = 36.dp
private val HeroGap = 8.dp

/** A rounded rectangle over [width] of the page from [left]: the part of a cover left visible. */
private class HeroStripShape(
    private val left: Float,
    private val width: Float,
    private val radius: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            androidx.compose.ui.geometry.RoundRect(
                left = left,
                top = 0f,
                right = left + width,
                bottom = size.height,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.coerceAtMost(width / 2f))
            )
        )
}

/** Animated equaliser bars — the playing cue that replaced morphing the cover itself. */
@Composable
private fun NowPlayingBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    glass: (@Composable BoxScope.() -> Unit)? = null
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
        // The panel accent, so in a cover-coloured player it is the cover's accent too; over a
        // video, frosted glass.
        color = if (glass != null) Color.Transparent else PanelColors.accent,
        contentColor = if (glass != null) PanelColors.content else PanelColors.onAccent
    ) {
        Box(contentAlignment = Alignment.Center) {
            glass?.invoke(this)
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barColor = LocalContentColor.current
                heights.forEach { height ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp * height.value)
                            .clip(CircleShape)
                            .background(barColor)
                    )
                }
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        AppLoadingIndicator(modifier = Modifier.size(96.dp))
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
    likesPushStatus: LikesPushStatus,
    pushLikesToSoundCloud: () -> Unit,
    resetLikesPushStatus: () -> Unit,
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
    onYandexLogoutClick: () -> Unit,
    ytMusicAccount: String?,
    onYtMusicLoginClick: () -> Unit,
    onYtMusicLogoutClick: () -> Unit,
    updates: com.example.myapplication.data.UpdateRepository
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

        // Section 0: Look
        item {
            val backgroundMotion by settingsRepository.backgroundMotion.collectAsState()
            val playerCoverColors by settingsRepository.playerCoverColors.collectAsState()
            val playerVideos by settingsRepository.playerVideos.collectAsState()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ОФОРМЛЕНИЕ",
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
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingsSwitchRow(
                            icon = Icons.Default.ScreenRotation,
                            title = "Живой фон",
                            subtitle = "Объёмные фигуры на фоне наклоняются и трясутся вместе с телефоном (акселерометр)",
                            checked = backgroundMotion,
                            onCheckedChange = settingsRepository::setBackgroundMotion
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.Palette,
                            title = "Цвета плеера из обложки",
                            subtitle = "Только плеер перекрашивается в оттенок обложки трека, остальное — по обоям",
                            checked = playerCoverColors,
                            onCheckedChange = settingsRepository::setPlayerCoverColors
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.SmartDisplay,
                            title = "Клипы в плеере",
                            subtitle = "Клип трека вместо обложки, вертикальное видео — на весь плеер. Яндекс Музыка и YouTube Music, тратит трафик",
                            checked = playerVideos,
                            onCheckedChange = settingsRepository::setPlayerVideos
                        )
                    }
                }
            }
        }

        // Section 0.5: Updates
        item {
            val autoCheck by settingsRepository.updateAutoCheck.collectAsState()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ОБНОВЛЕНИЯ",
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
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        UpdateSettingsRow(updates = updates)
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.Refresh,
                            title = "Проверять автоматически",
                            subtitle = "Спрашивать GitHub о новой версии раз в 12 часов",
                            checked = autoCheck,
                            onCheckedChange = settingsRepository::setUpdateAutoCheck
                        )
                    }
                }
            }
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

                                AppLinearProgress(
                                    progress = progress,
                                    color = AppTheme.brand.soundCloud.color,
                                    trackColor = AppTheme.brand.soundCloud.container,
                                    modifier = Modifier
                                        .fillMaxWidth()
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

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        // The other direction: downloaded tracks whose like never reached SoundCloud.
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
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = AppTheme.brand.soundCloud.onContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Лайки на SoundCloud",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Лайкнуть на SoundCloud скачанные треки, чьи лайки туда не дошли",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val pushBusy = likesPushStatus.state == LikesPushState.CHECKING ||
                            likesPushStatus.state == LikesPushState.SENDING
                        if (likesPushStatus.state != LikesPushState.IDLE) {
                            val statusText = when (likesPushStatus.state) {
                                LikesPushState.CHECKING -> "Сверяю с лайками SoundCloud..."
                                LikesPushState.SENDING -> "Отправлено ${likesPushStatus.sent} из ${likesPushStatus.total}"
                                LikesPushState.PAUSED -> "Отправлено ${likesPushStatus.sent} из ${likesPushStatus.total}, пауза"
                                LikesPushState.COMPLETED -> likesPushStatus.message
                                    ?: "Готово: отправлено ${likesPushStatus.sent} из ${likesPushStatus.total}"
                                LikesPushState.FAILED -> "Ошибка: ${likesPushStatus.message}"
                                LikesPushState.IDLE -> ""
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = if (likesPushStatus.state == LikesPushState.FAILED) MaterialTheme.colorScheme.error else AppTheme.brand.soundCloud.color
                            )
                            if (likesPushStatus.state == LikesPushState.PAUSED && likesPushStatus.message != null) {
                                Text(
                                    text = likesPushStatus.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if ((likesPushStatus.state == LikesPushState.SENDING || likesPushStatus.state == LikesPushState.PAUSED) &&
                                likesPushStatus.total > 0
                            ) {
                                AppLinearProgress(
                                    progress = likesPushStatus.sent.toFloat() / likesPushStatus.total,
                                    color = AppTheme.brand.soundCloud.color,
                                    trackColor = AppTheme.brand.soundCloud.container,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = pushLikesToSoundCloud,
                                enabled = !pushBusy,
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.large,
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = AppTheme.brand.soundCloud.color,
                                    contentColor = AppTheme.brand.soundCloud.onColor
                                )
                            ) {
                                Text(
                                    text = when (likesPushStatus.state) {
                                        LikesPushState.PAUSED -> "Продолжить"
                                        LikesPushState.COMPLETED, LikesPushState.FAILED -> "Проверить снова"
                                        else -> "Отправить лайки"
                                    }
                                )
                            }
                            if (likesPushStatus.state == LikesPushState.COMPLETED || likesPushStatus.state == LikesPushState.FAILED) {
                                FilledTonalButton(
                                    onClick = resetLikesPushStatus,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text("Сбросить")
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
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                                    color = if (yandexLikesSyncStatus.state == SyncState.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
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

                                    AppLinearProgress(
                                        progress = progress,
                                        modifier = Modifier
                                            .fillMaxWidth()
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
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
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
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("Войти")
                                }
                            }
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        // YouTube Music Account
                        Row(
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
                                    imageVector = Icons.Default.SmartDisplay,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "YouTube Music",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = ytMusicAccount ?: "Не подключен",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (ytMusicAccount != null) {
                                FilledTonalButton(
                                    onClick = onYtMusicLogoutClick,
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
                                    onClick = onYtMusicLoginClick,
                                    shape = MaterialTheme.shapes.medium,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
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

/** A settings row with a tinted icon puck, a title, a quiet caption and a switch. */
@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
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
    source: SearchSource,
    sources: List<SearchSource>,
    onSearchSourceChanged: (SearchSource) -> Unit,
    yandexQuery: String,
    yandexTracks: List<SoundCloudTrack>,
    yandexLoading: Boolean,
    yandexError: String?,
    onYandexQueryChange: (String) -> Unit,
    ytSearch: YtSearchState,
    onYtQueryChange: (String) -> Unit,
    albums: List<SoundCloudPlaylist> = emptyList(),
    playlists: List<SoundCloudPlaylist> = emptyList(),
    artists: List<SoundCloudUser> = emptyList(),
    yandexAlbums: List<SoundCloudPlaylist> = emptyList(),
    yandexPlaylists: List<SoundCloudPlaylist> = emptyList(),
    yandexArtists: List<SoundCloudUser> = emptyList(),
    onOpenArtist: (SoundCloudUser) -> Unit = {},
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    openedPlaylist: SoundCloudPlaylist? = null,
    isPlaylistLoading: Boolean = false,
    playlistError: String? = null,
    onOpenPlaylist: (SoundCloudPlaylist) -> Unit = {},
    onClosePlaylist: () -> Unit = {},
    onPlayPlaylistTrack: (SoundCloudTrack, List<SoundCloudTrack>) -> Unit = { _, _ -> }
) {
    val focusRequester = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    // Hoisted so that coming back from an album lands where the list was left.
    val listState = rememberLazyListState()
    val albumsRowState = rememberLazyListState()
    val playlistsRowState = rememberLazyListState()
    val isFieldShown by rememberUpdatedState(openedPlaylist == null)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        // The field isn't composed while an album is open, and focusing a detached requester throws.
        if (isFieldShown) focusRequester.requestFocus()
    }

    if (openedPlaylist != null) {
        SetDetailContent(
            playlist = openedPlaylist,
            subtitle = openedPlaylist.user?.username.orEmpty(),
            isLoading = isPlaylistLoading,
            error = playlistError,
            favorites = favorites,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            downloadProgress = downloadProgress,
            onBack = onClosePlaylist,
            onPlayTrack = { track -> onPlayPlaylistTrack(track, openedPlaylist.tracks) },
            onFavoriteClick = onFavoriteClick
        )
        return
    }

    // A service signed out of meanwhile hands search back to SoundCloud.
    val activeSource = if (source in sources) source else SearchSource.SOUNDCLOUD
    LaunchedEffect(activeSource, source) {
        if (activeSource != source) onSearchSourceChanged(activeSource)
    }
    val ytPage = ytSearch.page
    val activeQuery = when (activeSource) {
        SearchSource.SOUNDCLOUD -> query
        SearchSource.YANDEX -> yandexQuery
        SearchSource.YOUTUBE -> ytSearch.query
    }
    val activeTracks = when (activeSource) {
        SearchSource.SOUNDCLOUD -> tracks
        SearchSource.YANDEX -> yandexTracks
        SearchSource.YOUTUBE -> ytPage?.tracks.orEmpty()
    }
    val activeLoading = when (activeSource) {
        SearchSource.SOUNDCLOUD -> isLoading
        SearchSource.YANDEX -> yandexLoading
        SearchSource.YOUTUBE -> ytSearch.loading
    }
    val activeError = when (activeSource) {
        SearchSource.SOUNDCLOUD -> errorMessage
        SearchSource.YANDEX -> yandexError
        SearchSource.YOUTUBE -> ytSearch.error
    }
    val activeAlbums = when (activeSource) {
        SearchSource.SOUNDCLOUD -> albums
        SearchSource.YANDEX -> yandexAlbums
        SearchSource.YOUTUBE -> ytPage?.albums.orEmpty()
    }
    val activePlaylists = when (activeSource) {
        SearchSource.SOUNDCLOUD -> playlists
        SearchSource.YANDEX -> yandexPlaylists
        SearchSource.YOUTUBE -> ytPage?.playlists.orEmpty()
    }
    val activeArtists = when (activeSource) {
        SearchSource.SOUNDCLOUD -> artists
        SearchSource.YANDEX -> yandexArtists
        SearchSource.YOUTUBE -> ytPage?.artists.orEmpty()
    }
    val topResult = remember(activeQuery, activeAlbums, activePlaylists) {
        pickTopResult(activeQuery, activeAlbums, activePlaylists)
    }
    val favoritesMap = remember(favorites) { favorites.associateBy { it.id } }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            // The keyboard opens itself here, so without this the last result sits under it
            // with no way to scroll to it.
            .imePadding(),
        // top = 0: the bar has to start at the same y as the home bar, or entering search
        // shifts the title and back button downward and the transition reads as a jump.
        // Rows of a group sit flush, so spacing between blocks is set per item instead.
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 120.dp)
    ) {
        item(key = "search-top-bar") {
            TopBar(title = "Поиск", onBack = onBack)
        }

        item(key = "search-field") {
            SearchField(
                query = activeQuery,
                onQueryChange = { newQuery ->
                    when (activeSource) {
                        SearchSource.SOUNDCLOUD -> onQueryChange(newQuery)
                        SearchSource.YANDEX -> onYandexQueryChange(newQuery)
                        SearchSource.YOUTUBE -> onYtQueryChange(newQuery)
                    }
                },
                focusRequester = focusRequester,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Only when there is a choice: services not connected in settings aren't offered.
        if (sources.size > 1) {
            item(key = "search-source") {
                SegmentedControl(
                    items = sources.map { option ->
                        when (option) {
                            SearchSource.SOUNDCLOUD -> "SoundCloud"
                            SearchSource.YANDEX -> if (sources.size > 2) "Яндекс" else "Яндекс Музыка"
                            SearchSource.YOUTUBE -> if (sources.size > 2) "YouTube" else "YouTube Music"
                        }
                    },
                    selectedIndex = sources.indexOf(activeSource),
                    onSelectedIndexChanged = { index ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSearchSourceChanged(sources[index])
                    },
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }

        if (activeLoading) {
            item(key = "search-loading") {
                LoadingBlock(modifier = Modifier.padding(top = 16.dp), height = 120.dp)
            }
        }

        if (activeError != null) {
            item(key = "search-error") {
                Box(modifier = Modifier.padding(top = 16.dp)) { MessageCard(activeError) }
            }
        }

        if (topResult != null) {
            item(key = "search-top-result") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    Kicker(text = "Лучший результат")
                    Spacer(modifier = Modifier.height(10.dp))
                    TopResultCard(
                        kicker = setCaption(topResult),
                        title = topResult.title ?: "Без названия",
                        subtitle = listOfNotNull(
                            topResult.user?.username,
                            plural(topResult.trackCount, "трек", "трека", "треков")
                        ).joinToString(" · "),
                        artworkUrl = topResult.displayArtworkUrl,
                        onClick = { onOpenPlaylist(topResult) }
                    )
                }
            }
        }

        if (activeArtists.isNotEmpty()) {
            item(key = "search-artists-header") {
                SectionTitle("Исполнители", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            }
            item(key = "search-artists") {
                ArtistRow(artists = activeArtists, onOpen = onOpenArtist)
            }
        }

        if (activeAlbums.isNotEmpty()) {
            item(key = "search-albums-header") {
                SectionTitle("Альбомы", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            }
            item(key = "search-albums") {
                AlbumCarousel(
                    albums = activeAlbums.map { album ->
                        CarouselAlbum(
                            key = album.id,
                            title = album.title ?: "Без названия",
                            subtitle = album.user?.username.orEmpty(),
                            caption = setCaption(album),
                            artworkUrl = album.displayArtworkUrl,
                            onClick = { onOpenPlaylist(album) }
                        )
                    }
                )
            }
        }

        if (activePlaylists.isNotEmpty()) {
            item(key = "search-playlists-header") {
                SectionTitle(
                    "Плейлисты и сборники",
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }
            // Two to a row, up to three rows; the rest are a scroll of the search further on.
            activePlaylists.take(6).chunked(2).forEachIndexed { rowIndex, pair ->
                item(key = "search-playlists-row-$rowIndex") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { playlist ->
                            CompactCollectionCard(
                                title = playlist.title ?: "Без названия",
                                caption = plural(playlist.trackCount, "трек", "трека", "треков"),
                                artworkUrl = playlist.displayArtworkUrl,
                                onClick = { onOpenPlaylist(playlist) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (activeTracks.isNotEmpty()) {
            if (activeAlbums.isNotEmpty() || activePlaylists.isNotEmpty() || activeArtists.isNotEmpty() || topResult != null) {
                item(key = "search-tracks-header") {
                    SectionTitle("Треки", modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                }
            } else {
                item(key = "search-tracks-gap") { Spacer(modifier = Modifier.height(20.dp)) }
            }
            val showLoadMore = hasMore && !activeLoading
            val rowCount = activeTracks.size + if (showLoadMore) 1 else 0
            itemsIndexed(
                activeTracks,
                key = { _, track -> "${activeSource.name}-search-${track.id}" }
            ) { index, track ->
                val favorite = favoritesMap[track.id]
                TrackCard(
                    track = track,
                    isFavorite = favorite != null,
                    isSelected = track.id == currentTrackId,
                    downloadState = favorite?.downloadState,
                    progress = downloadProgress[track.id],
                    isPlaying = isPlaying,
                    onClick = { onPlayTrack(track) },
                    onFavoriteClick = { onFavoriteClick(track) },
                    position = groupPosition(index, rowCount)
                )
            }
            if (showLoadMore) {
                item(key = "search-load-more") {
                    LoadMoreRow(
                        isLoading = isLoadingMore,
                        onClick = onLoadMore,
                        position = groupPosition(rowCount - 1, rowCount)
                    )
                }
            }
        } else if (!activeLoading && activeAlbums.isEmpty() && activePlaylists.isEmpty() && activeArtists.isEmpty()) {
            item(key = "search-empty") {
                Box(modifier = Modifier.padding(top = 20.dp)) {
                    EmptyState(
                        if (activeQuery.isBlank()) "Напиши, что хочешь услышать."
                        else "Ничего не нашлось."
                    )
                }
            }
        }
    }
}

/** Artists a search found: round portraits with a name, the way people show everywhere else. */
@Composable
private fun ArtistRow(artists: List<SoundCloudUser>, onOpen: (SoundCloudUser) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(artists, key = { "artist-" + (it.permalinkUrl ?: it.id.toString()) }) { artist ->
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpen(artist) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CoverImage(url = artworkUrlForSize(artist.avatarUrl, 96.dp), size = 84.dp, shape = CircleShape)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = artist.username.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val caption = artist.followersCount?.takeIf { it > 0 }?.let { compactCount(it) }
                    ?: artist.trackCount?.takeIf { it > 0 }?.let { plural(it, "трек", "трека", "треков") }
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * The set to single out above the results. SoundCloud's own ranking can't be trusted for this:
 * for "persona 5 music" its first album is a lullaby compilation that merely mentions "5 hours"
 * and "music". So sets are scored on how much of the query their title carries, with a bonus
 * for containing its opening words as a phrase, and only a convincing match gets the spot.
 */
private fun pickTopResult(
    query: String,
    albums: List<SoundCloudPlaylist>,
    playlists: List<SoundCloudPlaylist>
): SoundCloudPlaylist? {
    val tokens = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return null
    val phrase = tokens.take(2).joinToString(" ")

    fun score(set: SoundCloudPlaylist): Double {
        val title = set.title?.lowercase() ?: return 0.0
        val words = title.split(Regex("[^\\p{L}\\p{N}]+")).toSet()
        val matched = tokens.count { it in words }.toDouble() / tokens.size
        val phraseBonus = if (tokens.size >= 2 && title.contains(phrase)) 0.5 else 0.0
        return matched + phraseBonus
    }

    fun best(sets: List<SoundCloudPlaylist>): SoundCloudPlaylist? =
        sets.take(8)
            .map { it to score(it) }
            .filter { it.second >= 1.0 }
            .maxWithOrNull(compareBy<Pair<SoundCloudPlaylist, Double>> { it.second }.thenBy { it.first.trackCount })
            ?.first

    return best(albums) ?: best(playlists)
}

/** "Альбом · 2017", "EP · 2020", "Плейлист · 42 трека". */
private fun setCaption(set: SoundCloudPlaylist): String {
    val kind = when (set.setType?.lowercase()) {
        "album" -> "Альбом"
        "ep" -> "EP"
        "single" -> "Сингл"
        "compilation" -> "Сборник"
        else -> if (set.isAlbum == true) "Альбом" else "Плейлист"
    }
    val year = set.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    return if (year != null) "$kind · $year" else "$kind · ${plural(set.trackCount, "трек", "трека", "треков")}"
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
            // No title here: the hero right below already carries it.
            TopBar(title = "", onBack = onBack)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp)
        ) {
            item(key = "downloads-hero") {
                CollectionHero(
                    title = "Скачанное",
                    kicker = "На устройстве",
                    subtitle = plural(tracks.size, "трек", "трека", "треков") + " доступны офлайн",
                    onArtworkClick = { imagePicker.launch(arrayOf("image/*")) },
                    artwork = {
                        if (!folderArtworkUri.isNullOrBlank()) {
                            AsyncImage(
                                model = folderArtworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            IconCover(icon = Icons.Default.Download)
                        }
                    },
                    actions = {
                        if (tracks.isNotEmpty()) {
                            PanelPrimaryButton(
                                text = "Слушать",
                                icon = Icons.Default.PlayArrow,
                                onClick = { onPlayTrack(tracks.first()) }
                            )
                        }
                        PanelIconButton(
                            icon = Icons.Default.Image,
                            contentDescription = "Сменить обложку",
                            onClick = { imagePicker.launch(arrayOf("image/*")) }
                        )
                        PanelIconButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Импортировать треки с устройства",
                            onClick = { audioPicker.launch(arrayOf("audio/*")) }
                        )
                    }
                )
            }

            item(key = "downloads-gap") { Spacer(modifier = Modifier.height(20.dp)) }

            if (tracks.isEmpty()) {
                item(key = "downloads-empty") {
                    EmptyState("Здесь появятся треки, которые ты сохранишь на устройство.")
                }
            } else {
                itemsIndexed(tracks, key = { _, track -> "downloaded-${track.id}" }) { index, track ->
                    DownloadedTrackCard(
                        track = track,
                        isSelected = track.id == currentTrackId,
                        progress = downloadProgress[track.id],
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onDeleteDownload = { onDeleteDownload(track) },
                        showDebugPercentage = showDebugPercentage,
                        debugPercentage = downloadedPercentages[track.id],
                        position = groupPosition(index, tracks.size)
                    )
                }
            }
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
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(30.dp),
        textStyle = MaterialTheme.typography.titleMedium,
        placeholder = { Text("Трек, альбом или артист") },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Очистить")
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent
        )
    )
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

private fun localizedGenre(raw: String): String = when (raw.trim().lowercase()) {
    "all genres", "all music genres" -> "Все жанры"
    else -> raw
}

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
                AppLoadingIndicator(modifier = Modifier.size(48.dp))
            }
        }

        // No label: these covers are self-labelling, so a caption under the tile just
        // said the same thing twice. The Russian section header above carries the
        // context, and the morphing artwork carries the playing state.
    }
}

/**
 * Liked albums as seen from any album screen, wherever it was opened from (artist page, search).
 * Provided once at the root so those screens don't each thread three more parameters through.
 */
private class AlbumLibrary(
    val likedBySource: Map<String, Playlist>,
    val onToggleLike: (album: SoundCloudPlaylist, artistName: String?) -> Unit,
    val onDownload: (Playlist) -> Unit
)

private val LocalAlbumLibrary = androidx.compose.runtime.staticCompositionLocalOf<AlbumLibrary?> { null }

/**
 * "Скачать все треки" for a saved playlist or liked album. The tracks land on the device for this
 * playlist only; "Скачанное" does not change. While they download the button turns into a wavy
 * ring filling with the share already saved; once everything is saved it stays lit.
 */
@Composable
private fun PlaylistDownloadButton(playlist: Playlist, onDownload: () -> Unit) {
    val total = playlist.tracks.count { !it.urn.startsWith("local:") }
    if (total == 0) return
    val saved = playlist.tracks.count { it.downloadState == DownloadState.DOWNLOADED && !it.urn.startsWith("local:") }
    val downloading = playlist.tracks.any { it.downloadState == DownloadState.DOWNLOADING }
    when {
        downloading -> Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = PanelColors.content.copy(alpha = 0.12f),
            contentColor = PanelColors.accent
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppCircularProgress(
                    progress = saved.toFloat() / total,
                    modifier = Modifier.size(40.dp),
                    color = PanelColors.accent
                )
            }
        }

        saved == total -> PanelIconButton(
            icon = Icons.Default.DownloadDone,
            contentDescription = "Все треки на устройстве",
            onClick = {},
            selected = true
        )

        else -> PanelIconButton(
            icon = Icons.Default.Download,
            contentDescription = "Скачать все треки",
            onClick = onDownload
        )
    }
}

/**
 * The bar over a screen that opens on a big picture: just the back button while the picture is in
 * view, then the backdrop tone and the title once it has scrolled away.
 */
@Composable
private fun CollapsingTopBar(title: String, collapsed: Boolean, onBack: () -> Unit) {
    val barColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.background else Color.Transparent,
        animationSpec = tween(220),
        label = "collapsingBar"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (collapsed) PanelColors.container else MaterialTheme.colorScheme.background.copy(alpha = 0.55f),
                contentColor = if (collapsed) PanelColors.accent else MaterialTheme.colorScheme.onSurface
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            AnimatedVisibility(
                visible = collapsed,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Scroll past this much of a big picture and its screen's bar takes over. */
@Composable
private fun rememberCollapsed(listState: androidx.compose.foundation.lazy.LazyListState, threshold: Dp): Boolean {
    val thresholdPx = with(LocalDensity.current) { threshold.roundToPx() }
    val collapsed by remember(listState, thresholdPx) {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > thresholdPx
        }
    }
    return collapsed
}

/**
 * "Слушать" and "Перемешать" as one connected Material 3 Expressive button group: the outer
 * corners fully round, the inner ones tight, the primary action in the accent.
 */
@Composable
private fun PlayShuffleGroup(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Surface(
            onClick = onPlay,
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 8.dp, bottomEnd = 8.dp),
            color = PanelColors.accent,
            contentColor = PanelColors.onAccent
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 26.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Text("Слушать", style = MaterialTheme.typography.titleMedium)
            }
        }
        Surface(
            onClick = onShuffle,
            modifier = Modifier.size(width = 64.dp, height = 56.dp),
            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp),
            color = PanelColors.container,
            contentColor = PanelColors.accent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Перемешать", modifier = Modifier.size(24.dp))
            }
        }
    }
}

/**
 * The top of an artist page: the portrait across the whole width, sinking into the backdrop, and
 * the name in large heavy type over its lower edge with the numbers and the actions.
 */
@Composable
private fun ArtistPortraitHeader(
    artist: SoundCloudUser,
    albumCount: Int,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?
) {
    val followers = artist.followersCount ?: 0
    val trackTotal = artist.trackCount ?: 0
    val stats = buildList {
        if (followers > 0) add(compactCount(followers) + " подписчиков")
        if (trackTotal > 0) add(plural(trackTotal, "трек", "трека", "треков"))
        if (albumCount > 0) add(plural(albumCount, "альбом", "альбома", "альбомов"))
    }.joinToString(" · ")
    val backdrop = MaterialTheme.colorScheme.background

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ArtistPortraitHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            if (!artist.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrlForSize(artist.avatarUrl, 500.dp),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                IconCover(icon = Icons.Default.Person, iconSize = 120.dp)
            }
            // Dark at the top for the status bar and the back button, then the portrait sinks into
            // the backdrop so the name below it sits on something calm.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.18f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.78f to backdrop.copy(alpha = 0.82f),
                            1f to backdrop
                        )
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = ArtistPortraitHeight - 120.dp, end = 20.dp)
        ) {
            Kicker(
                text = if (artist.permalinkUrl?.startsWith("yandex") == true) "Артист · Яндекс Музыка" else "Артист",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = artist.username.orEmpty(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 46.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (stats.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onPlay != null && onShuffle != null) {
                Spacer(modifier = Modifier.height(18.dp))
                PlayShuffleGroup(onPlay = onPlay, onShuffle = onShuffle)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private val ArtistPortraitHeight = 420.dp

/**
 * The mix or station cover as the backdrop of the screen's top, the title over its lower edge and
 * the big play button beside it, where the cover meets the list.
 */
@Composable
private fun MixCoverHeader(
    mix: SoundCloudMix,
    title: String,
    isStation: Boolean,
    trackCount: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?
) {
    val backdrop = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MixCoverHeight)
    ) {
        AsyncImage(
            model = ArtworkUrls.highRes(mix.artworkUrl) ?: mix.artworkUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    // Covers often carry lettering low down (a station's name, "STATION") right where
                    // the title goes, so the fade is nearly opaque by then or the two overprint.
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.2f to Color.Transparent,
                        0.36f to backdrop.copy(alpha = 0.3f),
                        0.6f to backdrop.copy(alpha = 0.9f),
                        0.72f to backdrop
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Kicker(text = if (isStation) "Станция" else "Микс", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // Stations put "Artist station" in the description, which their title already says,
                // and their track count is the rule right below.
                val subtitle = if (isStation) null else mix.description?.takeIf { it.isNotBlank() }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onPlay != null && onShuffle != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = onShuffle,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = PanelColors.container,
                        contentColor = PanelColors.accent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Перемешать", modifier = Modifier.size(24.dp))
                        }
                    }
                    MixPlayButton(isActive = isActive, isPlaying = isPlaying, onClick = onPlay)
                }
            }
        }
    }
}

private val MixCoverHeight = 420.dp

/**
 * The big play button of a mix. Until the mix is playing it is the call to action, in the accent.
 * Once it is the active queue it behaves like the player's toggle: lit and squarer while playing,
 * round and tonal while paused.
 */
@Composable
private fun MixPlayButton(isActive: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val playing = isActive && isPlaying
    val corner by animateDpAsState(
        targetValue = if (playing) 24.dp else 32.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "mixPlayCorner"
    )
    val (toggleContainer, toggleContent) = playToggleColors(isPlaying)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.size(80.dp),
        shape = RoundedCornerShape(corner),
        color = if (isActive) toggleContainer else PanelColors.accent,
        contentColor = if (isActive) toggleContent else PanelColors.onAccent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Пауза" else "Слушать",
                modifier = Modifier.size(38.dp)
            )
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
    isActive: Boolean = false,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    onShuffle: () -> Unit,
    onTogglePlay: () -> Unit
) {
    // Map for O(1) favorite lookup (#37)
    val favoritesMap = remember(favorites) { favorites.associateBy { it.id } }
    val playerVisible = currentTrackId != null
    val isStation = mix.permalink.contains("station") || mix.id.contains("station")
    val title = if (isStation) mix.title else localizedMixTitle(mix.title)
    val listState = rememberLazyListState()
    val collapsed = rememberCollapsed(listState, MixCoverHeight - 140.dp)

    // The cover is this screen's picture; the moving shapes behind a flat list only made it busy.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (playerVisible) 120.dp else 32.dp)
        ) {
            item(key = "mix-hero") {
                MixCoverHeader(
                    mix = mix,
                    title = title,
                    isStation = isStation,
                    trackCount = tracks.size,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    onPlay = if (tracks.isEmpty()) null else {
                        { if (isActive) onTogglePlay() else onPlayTrack(tracks.first()) }
                    },
                    onShuffle = if (tracks.isEmpty()) null else onShuffle
                )
            }

            // A rule between the cover and the list, so the two never blur together.
            item(key = "mix-count") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (tracks.isEmpty()) "Загружаем треки" else plural(tracks.size, "трек", "трека", "треков"),
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
            }

            if (tracks.isEmpty()) {
                item(key = "mix-loading") { LoadingBlock(height = 200.dp) }
            } else {
                itemsIndexed(tracks, key = { index, track -> "mix-${track.id}-$index" }) { _, track ->
                    val favorite = favoritesMap[track.id]
                    Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                        TrackCard(
                            track = track,
                            isFavorite = favorite != null,
                            isSelected = track.id == currentTrackId,
                            downloadState = favorite?.downloadState,
                            progress = downloadProgress[track.id],
                            isPlaying = isPlaying,
                            onClick = { onPlayTrack(track) },
                            onFavoriteClick = { onFavoriteClick(track) },
                            flat = true
                        )
                    }
                }
            }
        }

        CollapsingTopBar(title = title, collapsed = collapsed, onBack = onBack)
    }
}

/**
 * One row of a grouped track list. Rows sit flush against each other and [position] rounds
 * the outer corners of the first and last, so a run of them reads as one container with
 * hairlines between the rows — the list style of the colour-block redesign.
 */
@Composable
private fun TrackRowFrame(
    position: GroupPosition,
    isSelected: Boolean,
    onClick: () -> Unit,
    // Straight on the backdrop, without the shared container: the mix and station screens,
    // where the cover above is the only block. Only the playing row gets a fill there.
    flat: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = if (flat) RoundedCornerShape(20.dp) else position.shape(),
        color = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            flat -> Color.Transparent
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Column {
            if (position.hasDividerAbove && !flat) GroupDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TrackRowHeight)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                content = content
            )
        }
    }
}

/** Height of a track row's content; a row with a divider above is one dp taller. */
private val TrackRowHeight = 72.dp

/** Cover thumbnail for a row; the playing row shows moving equaliser bars over it. */
@Composable
private fun TrackRowArtwork(artworkUrl: String?, isCurrent: Boolean, isPlaying: Boolean) {
    Box(contentAlignment = Alignment.Center) {
        CoverImage(url = artworkUrl, size = 52.dp, shape = RoundedCornerShape(14.dp))
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                EqualizerBars(
                    animate = isPlaying,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun EqualizerBars(animate: Boolean, color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rowBars")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val height by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 420 + index * 130, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rowBar$index"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp * (if (animate) height else 0.45f))
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

private fun SoundCloudTrack.artistLine(): String =
    // Was just the uploader, so a collaboration listed one name here while the player showed
    // several.
    artists?.takeIf { it.isNotEmpty() }
        ?.mapNotNull { it.username }
        ?.joinToString(", ")
        ?: user?.username
        ?: "Unknown Artist"

@Composable
private fun TrackCard(
    track: SoundCloudTrack,
    isFavorite: Boolean,
    isSelected: Boolean = false,
    downloadState: DownloadState? = null,
    progress: Float? = null,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    position: GroupPosition = GroupPosition.Single,
    // Chart position, for an artist's top tracks.
    number: Int? = null,
    flat: Boolean = false
) {
    TrackRowFrame(position = position, isSelected = isSelected, onClick = onClick, flat = flat) {
        if (number != null) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(22.dp)
            )
        }
        TrackRowArtwork(track.artworkUrl, isCurrent = isSelected, isPlaying = isPlaying)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: "Unknown Track",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val duration = track.duration.takeIf { it > 0L }?.let { " · " + formatDuration(it) }.orEmpty()
            Text(
                text = track.artistLine() + duration,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (downloadState == DownloadState.DOWNLOADING) {
                Spacer(modifier = Modifier.height(4.dp))
                AppLinearProgress(progress = progress, modifier = Modifier.fillMaxWidth())
            }
        }

        val favoriteScale by animateFloatAsState(
            targetValue = if (isFavorite) 1.15f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
            label = "favoriteScale"
        )
        IconButton(
            onClick = onFavoriteClick,
            modifier = Modifier.graphicsLayer {
                scaleX = favoriteScale
                scaleY = favoriteScale
            }
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isFavorite) "Убрать из любимых" else "В любимые",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isFavorite) 1f else 0.6f)
            )
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
    debugPercentage: Int? = null,
    position: GroupPosition = GroupPosition.Single
) {
    TrackRowFrame(position = position, isSelected = isSelected, onClick = onClick) {
        // Cached cover when the track is downloaded, so the row still shows artwork offline.
        TrackRowArtwork(track.displayArtworkUrl, isCurrent = isSelected, isPlaying = isPlaying)
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
                    color = LocalContentColor.current.copy(alpha = 0.72f),
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
                        } else {
                            LocalContentColor.current.copy(alpha = 0.72f)
                        }
                    )
                }
            }
        }
        if (track.downloadState == DownloadState.DOWNLOADING) {
            AppCircularProgress(
                progress = progress,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(36.dp)
            )
        } else {
            IconButton(onClick = onDeleteDownload) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = LocalContentColor.current.copy(alpha = 0.8f)
                )
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
    lyrics: List<LyricLine>?,
    video: com.example.myapplication.data.TrackVideo?,
    upcomingVideo: com.example.myapplication.data.TrackVideo?,
    livePosition: () -> Long,
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
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember(track.id) { mutableStateOf(false) }
    val lyricsShown = showLyrics && !lyrics.isNullOrEmpty()
    val showLoading = (downloadState != DownloadState.DOWNLOADED) &&
        (isBuffering || isLoading || (positionMs == 0L && !isPlaying))
    val blurRadius by animateDpAsState(
        targetValue = if (showQueue) 10.dp else 0.dp,
        animationSpec = tween(durationMillis = 300),
        label = "blurRadius"
    )
    // Lyrics take the cover's place: it blurs into a backdrop behind them.
    val coverBlur by animateDpAsState(
        targetValue = if (lyricsShown) 28.dp else 0.dp,
        animationSpec = tween(durationMillis = 350),
        label = "lyricsCoverBlur"
    )

    // A music video plays in the cover's place. A vertical one (Yandex's videoshots) fills the
    // whole player instead, and the panel turns to frosted glass over it.
    // The video on screen, and the one to take over from it, buffering unseen meanwhile. Keyed by
    // stream, so the player that buffered it carries on when it takes over.
    val shownVideo = video ?: upcomingVideo
    var videoState: PlayerVideoState? = null
    for (candidate in listOfNotNull(shownVideo, upcomingVideo?.takeIf { it.url != shownVideo?.url })) {
        val state = androidx.compose.runtime.key(candidate.url) { rememberPlayerVideoState(candidate, isPlaying, livePosition) }
        if (candidate === shownVideo) videoState = state
    }
    val videoShown by animateFloatAsState(
        targetValue = if (videoState?.showing == true) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "videoShown"
    )
    val immersiveVideo = videoState?.takeIf { it.isPortrait }
    // Paused, a video blurs, as if it had stopped to wait.
    val pauseBlur by animateDpAsState(
        targetValue = if (!isPlaying && videoState?.showing == true) 24.dp else 0.dp,
        animationSpec = tween(durationMillis = 400),
        label = "videoPauseBlur"
    )
    // Over a video the buttons at the top turn to frosted glass as well, like the panel.
    val buttonGlass: (@Composable BoxScope.() -> Unit)? = videoState?.takeIf { it.showing }?.let { shown ->
        { FrostedVideoGlass(state = shown, tint = PanelColors.container.copy(alpha = 0.42f)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Drawn over the app's own screens, so the player owns its whole backdrop.
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(showQueue) {
                detectDragGestures(
                    onDrag = { _, dragAmount ->
                        if (dragAmount.y < -40f && !showQueue) {
                            showQueue = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                )
            }
    ) {
        if (immersiveVideo != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = videoShown }
                    .blur(coverBlur + blurRadius + pauseBlur)
            ) {
                VideoSurface(state = immersiveVideo, modifier = Modifier.fillMaxSize())
                // Keeps the status bar and the buttons over the video legible.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent)))
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
        ) {
            PlayerLayout(
                artwork = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // Over a vertical video the cover gives way to it once it plays.
                                .graphicsLayer { alpha = if (immersiveVideo != null) 1f - videoShown else 1f }
                                .blur(coverBlur + pauseBlur)
                        ) {
                            PlayerArtwork(
                                track = track,
                                isPlaying = isPlaying,
                                showLoading = showLoading,
                                vibrator = vibrator,
                                onLongPress = onLongPressCover,
                                video = videoState?.takeIf { immersiveVideo == null },
                                videoShown = videoShown
                            )
                        }
                        AnimatedVisibility(
                            visible = lyricsShown,
                            enter = fadeIn(animationSpec = tween(350)),
                            exit = fadeOut(animationSpec = tween(250)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LyricsOverlay(
                                lines = lyrics.orEmpty(),
                                positionMs = positionMs,
                                onSeek = onSeek
                            )
                        }
                    }
                },
                panel = {
                    PlayerPanel(
                        glass = if (immersiveVideo != null && videoShown > 0f) {
                            {
                                FrostedVideoGlass(
                                    state = immersiveVideo,
                                    tint = PanelColors.container.copy(alpha = 0.42f)
                                )
                            }
                        } else {
                            null
                        },
                        track = track,
                        activeQueue = activeQueue,
                        isFavorite = isFavorite,
                        downloadState = downloadState,
                        isPlaying = isPlaying,
                        repeatMode = repeatMode,
                        shuffleEnabled = shuffleEnabled,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        vibrator = vibrator,
                        onTogglePlay = onTogglePlay,
                        onSeek = onSeek,
                        onFavoriteClick = onFavoriteClick,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onRepeat = onRepeat,
                        onShuffle = onShuffle,
                        onArtistClick = onArtistClick,
                        lyricsAvailable = !lyrics.isNullOrEmpty(),
                        lyricsShown = lyricsShown,
                        onToggleLyrics = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLyrics = !showLyrics
                        },
                        onOpenQueue = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showQueue = true
                        }
                    )
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OverArtworkButton(
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Свернуть плеер",
                    onClick = onBack,
                    glass = buttonGlass
                )
                // The indicator is always the equaliser, whatever the download state — tapping
                // it reveals the state and the destructive action, instead of a separate delete
                // button appearing out of nowhere.
                val state = downloadState ?: DownloadState.NONE
                var showTrackMenu by remember { mutableStateOf(false) }
                Box {
                    NowPlayingBadge(onClick = { showTrackMenu = true }, glass = buttonGlass)
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

/**
 * Cover on top, panel at the bottom. The panel is measured first and keeps its natural height;
 * the cover takes whatever is left plus the overlap the panel's rounded top sits on, so tall
 * phones get a bigger cover and short ones never push the controls off screen.
 */
@Composable
private fun PlayerLayout(
    artwork: @Composable () -> Unit,
    panel: @Composable () -> Unit
) {
    val overlap = 44.dp
    Layout(
        modifier = Modifier.fillMaxSize(),
        content = {
            Box { artwork() }
            Box { panel() }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val panelPlaceable = measurables[1].measure(
            Constraints(minWidth = width, maxWidth = width, maxHeight = height)
        )
        val artHeight = (height - panelPlaceable.height + overlap.roundToPx()).coerceAtLeast(0)
        val artPlaceable = measurables[0].measure(Constraints.fixed(width, artHeight))
        layout(width, height) {
            artPlaceable.place(0, 0)
            panelPlaceable.place(0, height - panelPlaceable.height)
        }
    }
}

@Composable
private fun PlayerArtwork(
    track: SoundCloudTrack,
    isPlaying: Boolean,
    showLoading: Boolean,
    vibrator: android.os.Vibrator?,
    onLongPress: () -> Unit,
    video: PlayerVideoState? = null,
    videoShown: Float = 0f
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val pressScale = remember { Animatable(1f) }
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 1.04f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "artworkPlayScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerInput(track.permalinkUrl) {
                detectTapGestures(
                    onLongPress = {
                        // Long-pressing the cover opens the track's actions, with a heavy click
                        // and a spring pulse so the gesture reads as deliberate.
                        try {
                            vibrator?.vibrate(
                                android.os.VibrationEffect.createPredefined(
                                    android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                                )
                            )
                        } catch (e: Exception) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        coroutineScope.launch {
                            pressScale.animateTo(1.06f, animationSpec = tween(150))
                            pressScale.animateTo(
                                1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                            onLongPress()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = playScale * pressScale.value
                    scaleY = playScale * pressScale.value
                }
        ) {
            AsyncImage(
                model = artworkUrlForSize(track.artworkUrl, 500.dp),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            // The music video fades in over the cover once it plays: from the line the buttons
            // over it start at, down, glowing into the space above.
            if (video != null) {
                AmbientVideo(
                    state = video,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                    alpha = videoShown,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Keeps the status bar and the buttons over the cover legible on bright artwork.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent)
                    )
                )
        )

        AnimatedVisibility(
            visible = showLoading,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            AppContainedLoadingIndicator(modifier = Modifier.size(84.dp))
        }
    }
}

@Composable
private fun OverArtworkButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    glass: (@Composable BoxScope.() -> Unit)? = null
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(16.dp),
        // The same accent as the playing badge across from it, so the two corners match — the
        // cover's own colour, or white/black for a greyscale cover. Solid: over a busy cover,
        // or lyrics scrolling beneath it, a see-through button blurred into what was behind it.
        // Over a video, frosted glass instead: the video blurred, not what lies behind it.
        color = if (glass != null) Color.Transparent else PanelColors.accent,
        contentColor = if (glass != null) PanelColors.content else PanelColors.onAccent
    ) {
        Box(contentAlignment = Alignment.Center) {
            glass?.invoke(this)
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(26.dp))
        }
    }
}

/**
 * The colour block: title, artists, seek bar, transport and what plays next. With [glass] it
 * stands on that (frosted video) instead of its solid colour.
 */
@Composable
private fun PlayerPanel(
    glass: (@Composable BoxScope.() -> Unit)? = null,
    track: SoundCloudTrack,
    activeQueue: List<SoundCloudTrack>,
    isFavorite: Boolean,
    downloadState: DownloadState?,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    positionMs: Long,
    durationMs: Long,
    vibrator: android.os.Vibrator?,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavoriteClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onArtistClick: (SoundCloudUser) -> Unit,
    lyricsAvailable: Boolean,
    lyricsShown: Boolean,
    onToggleLyrics: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val onPanel = PanelColors.content

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp),
        color = if (glass != null) Color.Transparent else PanelColors.container,
        contentColor = onPanel
    ) {
        Box {
            glass?.invoke(this)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
            ) {
                OnPanelChip(
                    text = buildString {
                        append(
                            when {
                                track.urn?.startsWith("yandex:") == true -> "Яндекс Музыка"
                                track.youTubeVideoId != null -> "YouTube Music"
                                else -> "SoundCloud"
                            }
                        )
                        if (downloadState == DownloadState.DOWNLOADED) append(" · на устройстве")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = track.title ?: "Unknown Track",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Every credited artist gets its own tappable chip: a collaboration used to show a
                // single name with no way to reach anyone else on the track.
                val credited = remember(track) {
                    track.artists?.takeIf { it.isNotEmpty() } ?: listOfNotNull(track.user)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (credited.isEmpty()) {
                        Text(
                            text = "Unknown Artist",
                            style = MaterialTheme.typography.titleSmall,
                            color = onPanel.copy(alpha = 0.8f)
                        )
                    }
                    credited.forEach { artist ->
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onArtistClick(artist)
                            },
                            shape = CircleShape,
                            color = onPanel.copy(alpha = 0.12f),
                            contentColor = onPanel
                        ) {
                            Text(
                                text = artist.username ?: "Unknown Artist",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                PlayerSeekBar(
                    trackId = track.id,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    vibrator = vibrator,
                    onSeek = onSeek
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelIconButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Убрать из любимых" else "В любимые",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFavoriteClick()
                        },
                        selected = isFavorite,
                        size = 48.dp
                    )
                    PanelIconButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        onClick = onPrevious,
                        size = 68.dp,
                        iconSize = 32.dp
                    )
                    PanelPlayButton(isPlaying = isPlaying, onClick = onTogglePlay)
                    PanelIconButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = "Следующий трек",
                        onClick = onNext,
                        size = 68.dp,
                        iconSize = 32.dp
                    )
                    PanelIconButton(
                        icon = Icons.Rounded.Shuffle,
                        contentDescription = if (shuffleEnabled) "Перемешивание включено" else "Перемешать",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShuffle()
                        },
                        selected = shuffleEnabled,
                        size = 48.dp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val currentIndex = activeQueue.indexOfFirst { it.id == track.id }
                    val nextTrack = if (currentIndex >= 0) activeQueue.getOrNull(currentIndex + 1) else null
                    QueuePeek(
                        nextTrack = nextTrack,
                        queueSize = activeQueue.size,
                        lyricsAvailable = lyricsAvailable,
                        lyricsShown = lyricsShown,
                        onToggleLyrics = onToggleLyrics,
                        onClick = onOpenQueue,
                        modifier = Modifier.weight(1f)
                    )
                    PanelIconButton(
                        icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Повтор трека"
                            Player.REPEAT_MODE_ALL -> "Повтор очереди"
                            else -> "Повтор выключен"
                        },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onRepeat()
                        },
                        selected = repeatMode != Player.REPEAT_MODE_OFF,
                        size = 64.dp
                    )
                }
            }
        }
    }
}

/** Thick expressive seek bar with a tick every 2% of a manual drag. */
@Composable
private fun PlayerSeekBar(
    trackId: Long,
    positionMs: Long,
    durationMs: Long,
    vibrator: android.os.Vibrator?,
    onSeek: (Long) -> Unit
) {
    val onPanel = PanelColors.content
    var sliderProgress by remember { mutableStateOf<Float?>(null) }
    var lastVibratedRatio by remember(trackId) { mutableStateOf(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(
        thumbColor = PanelColors.accent,
        activeTrackColor = PanelColors.accent,
        inactiveTrackColor = onPanel.copy(alpha = 0.18f),
        activeTickColor = PanelColors.onAccent,
        inactiveTickColor = PanelColors.accent
    )
    val shownRatio = sliderProgress ?: if (durationMs > 0) {
        positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()
    } else {
        0f
    }

    Slider(
        value = shownRatio,
        onValueChange = { ratio ->
            sliderProgress = ratio
            if (kotlin.math.abs(ratio - lastVibratedRatio) >= 0.02f) {
                try {
                    vibrator?.vibrate(
                        android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK)
                    )
                } catch (e: Exception) {
                    // No vibrator, no tick.
                }
                lastVibratedRatio = ratio
            }
        },
        onValueChangeFinished = {
            sliderProgress?.let { ratio -> onSeek((ratio * durationMs).toLong()) }
            sliderProgress = null
        },
        colors = colors,
        interactionSource = interactionSource,
        thumb = { state ->
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                sliderState = state,
                colors = colors,
                thumbSize = DpSize(5.dp, 44.dp)
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                trackCornerSize = 8.dp,
                modifier = Modifier.height(16.dp),
                colors = colors,
                thumbTrackGapSize = 6.dp
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val shownPosition = sliderProgress?.let { (it * durationMs).toLong() } ?: positionMs
        Text(
            formatDuration(shownPosition),
            style = MaterialTheme.typography.labelLarge,
            color = onPanel.copy(alpha = 0.85f)
        )
        Text(
            formatDuration(durationMs),
            style = MaterialTheme.typography.labelLarge,
            color = onPanel.copy(alpha = 0.85f)
        )
    }
}

/**
 * Colours of a play/pause toggle on a panel. It switches on like the other panel toggles:
 * accent fill while the track plays, a quiet tonal fill with an accent glyph while it is
 * paused — a paused player used to show the same lit-up button and looked as if it played.
 */
@Composable
private fun playToggleColors(isPlaying: Boolean): Pair<Color, Color> {
    val container by animateColorAsState(
        targetValue = if (isPlaying) PanelColors.accent else PanelColors.content.copy(alpha = 0.14f),
        animationSpec = tween(300),
        label = "playToggleContainer"
    )
    val content by animateColorAsState(
        targetValue = if (isPlaying) PanelColors.onAccent else PanelColors.accent,
        animationSpec = tween(300),
        label = "playToggleContent"
    )
    return container to content
}

/** Big play/pause on the panel: squarer and lit while playing, round and tonal while paused. */
@Composable
private fun PanelPlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 30.dp else 48.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "panelPlayCorner"
    )
    val (container, content) = playToggleColors(isPlaying)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.size(96.dp),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Пауза" else "Играть",
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

/**
 * "Далее": the next track in the queue; tapping it opens the queue. When the track has synced
 * lyrics, its end holds their switch instead of the arrow.
 */
@Composable
private fun QueuePeek(
    nextTrack: SoundCloudTrack?,
    queueSize: Int,
    lyricsAvailable: Boolean,
    lyricsShown: Boolean,
    onToggleLyrics: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onPanel = PanelColors.content
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(24.dp),
        color = onPanel.copy(alpha = 0.10f),
        contentColor = onPanel
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (nextTrack != null) {
                CoverImage(url = nextTrack.artworkUrl, size = 44.dp, shape = RoundedCornerShape(14.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(onPanel.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Kicker(
                    text = if (nextTrack != null) "Далее" else "Очередь",
                    color = onPanel.copy(alpha = 0.72f)
                )
                Text(
                    text = nextTrack?.title ?: plural(queueSize, "трек", "трека", "треков"),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (lyricsAvailable) {
                val container by animateColorAsState(
                    targetValue = if (lyricsShown) PanelColors.accent else onPanel.copy(alpha = 0.12f),
                    animationSpec = tween(250),
                    label = "lyricsButtonContainer"
                )
                val content by animateColorAsState(
                    targetValue = if (lyricsShown) PanelColors.onAccent else onPanel,
                    animationSpec = tween(250),
                    label = "lyricsButtonContent"
                )
                Surface(
                    onClick = onToggleLyrics,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = container,
                    contentColor = content
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lyrics,
                            contentDescription = if (lyricsShown) "Скрыть текст" else "Текст песни",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Открыть очередь")
            }
        }
    }
}

/**
 * Synced lyrics over the blurred cover, in the player's colours. The line being sung is lit,
 * grows and thickens, and sits right under the buttons at the top; the lines already sung
 * dissolve into a fade above it, and the lines to come fade out the same way at the bottom,
 * where the panel begins. A tap on a line seeks to it. Scrolling by hand pauses the following
 * for a few seconds, so reading ahead isn't yanked back.
 */
@Composable
private fun LyricsOverlay(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    // The position arrives twice a second; leading it a little keeps lines from lighting late.
    val current = remember(lines, positionMs) { lines.indexOfLast { it.timeMs <= positionMs + 300 } }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var lastUserScrollAt by remember { mutableStateOf(0L) }
    LaunchedEffect(isDragged) {
        if (isDragged || lastUserScrollAt != 0L) lastUserScrollAt = System.currentTimeMillis()
    }

    // Where the lit line's top sits: just below the collapse button (status bar, the button
    // row's 12dp padding, the 48dp button) with a little air. The list's top padding is exactly
    // this, so scrolling a line to the list's start puts it here.
    val anchor = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp + 48.dp + 16.dp

    // Opens on the current line rather than scrolling there from the top.
    LaunchedEffect(Unit) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        if (current > 0) listState.scrollToItem(current)
    }
    LaunchedEffect(current) {
        if (current < 0 || isDragged) return@LaunchedEffect
        if (System.currentTimeMillis() - lastUserScrollAt < 3_000) return@LaunchedEffect
        listState.animateScrollToItem(current)
    }

    val lit = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.38f))
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Fades the text out at both ends: drawn offscreen, then masked by a gradient
                // that is clear above the fade, solid between the two fades, and clear again
                // where the panel covers the cover.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val h = size.height
                    val topSolid = (anchor - 6.dp).toPx()
                    val topClear = (topSolid - LyricsFade.toPx()).coerceAtLeast(0f)
                    val bottomClear = h - PlayerPanelOverlap.toPx()
                    val bottomSolid = (bottomClear - LyricsFade.toPx()).coerceAtLeast(topSolid)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            topClear / h to Color.Transparent,
                            topSolid / h to Color.Black,
                            bottomSolid / h to Color.Black,
                            bottomClear / h to Color.Transparent,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            contentPadding = PaddingValues(
                start = 28.dp,
                end = 28.dp,
                top = anchor,
                // Enough room below the last line for it, too, to be scrolled up to the anchor.
                bottom = (maxHeight - anchor - 48.dp).coerceAtLeast(PlayerPanelOverlap + LyricsFade)
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                val color by animateColorAsState(
                    targetValue = when {
                        index == current -> lit
                        index < current -> lit.copy(alpha = 0.32f)
                        else -> lit.copy(alpha = 0.5f)
                    },
                    animationSpec = tween(300),
                    label = "lyricLineColor"
                )
                // 0 → 1 as the line becomes the sung one; the spring lets it settle with a slight
                // overshoot, and it eases back as the next line takes over.
                val emphasis by animateFloatAsState(
                    targetValue = if (index == current) 1f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "lyricLineEmphasis"
                )
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.headlineSmall,
                    // Medium to extra bold, in hundreds: the system font has a weight per
                    // hundred and nothing between, so finer steps would only re-lay the text out
                    // each frame for nothing. The scale below is what reads as smooth.
                    fontWeight = FontWeight(500 + 100 * (3 * emphasis.coerceIn(0f, 1f)).roundToInt()),
                    color = color,
                    modifier = Modifier
                        // Wraps short of the edge: the lit line grows by LyricsLitScale to the
                        // right, and a full-width one would run off the screen.
                        .fillMaxWidth(1f / LyricsLitScale)
                        .graphicsLayer {
                            val scale = 1f + (LyricsLitScale - 1f) * emphasis
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSeek(line.timeMs)
                        }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

// How much bigger the lit lyric line is than the rest.
private const val LyricsLitScale = 1.16f

// Height of the fades lyrics dissolve into, above the lit line and above the panel.
private val LyricsFade = 64.dp

// How far the player's panel reaches up over the cover (see PlayerLayout).
private val PlayerPanelOverlap = 44.dp

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
                AppLoadingIndicator(modifier = Modifier.size(20.dp))
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

/**
 * The player's colour-block panel, collapsed: same colours, so opening it reads as the bar
 * growing into the full player.
 */
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
    val haptic = LocalHapticFeedback.current
    val onPanel = PanelColors.content
    Surface(
        onClick = onOpen,
        color = PanelColors.container,
        contentColor = onPanel,
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(url = artworkUrl, size = 52.dp, shape = RoundedCornerShape(18.dp))

            Spacer(modifier = Modifier.width(14.dp))

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
                    color = onPanel.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Waves while playing, lies flat while paused.
                LinearWavyProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    color = PanelColors.accent,
                    trackColor = onPanel.copy(alpha = 0.22f),
                    amplitude = { if (isPlaying) 1f else 0f }
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            val corner by animateDpAsState(
                targetValue = if (isPlaying) 16.dp else 26.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "miniPlayCorner"
            )
            val (playContainer, playContent) = playToggleColors(isPlaying)
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTogglePlay()
                },
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(corner),
                color = playContainer,
                contentColor = playContent
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Играть",
                        modifier = Modifier.size(28.dp)
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
                        AppLoadingIndicator(color = SoundCloudBrandSource)
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
                    AppLoadingIndicator(color = AppTheme.brand.soundCloud.color)
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
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
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
                            style = textStyle,
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
    onDeletePlaylist: () -> Unit = {},
    onShuffle: () -> Unit = {},
    onDownloadAll: () -> Unit = {}
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
            // The panel keeps the three things done most — play, shuffle, save on the device — and
            // everything else lives here, or the row overflows the panel.
            var showMenu by remember { mutableStateOf(false) }
            val hasDownloaded = playlist.tracks.any { it.downloadState == DownloadState.DOWNLOADED }
            TopBar(
                title = "",
                onBack = onBack,
                trailing = {
                    Box {
                        HomeIconButton(
                            icon = Icons.Default.MoreVert,
                            contentDescription = "Ещё",
                            onClick = { showMenu = true }
                        )
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Сменить обложку") },
                                leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    imagePicker.launch(arrayOf("image/*"))
                                }
                            )
                            if (hasDownloaded && !playlist.isLikedAlbum) {
                                DropdownMenuItem(
                                    text = { Text("Переместить скачанные в «Скачанное»") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onMoveDownloadedToDownloads()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(if (playlist.isLikedAlbum) "Убрать из медиатеки" else "Удалить плейлист")
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onDeletePlaylist()
                                }
                            )
                        }
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp)
        ) {
            item(key = "playlist-hero") {
                CollectionHero(
                    title = playlist.name,
                    kicker = if (playlist.isLikedAlbum) "Альбом · в медиатеке" else "Плейлист",
                    subtitle = listOfNotNull(
                        playlist.artist?.takeIf { playlist.isLikedAlbum && it.isNotBlank() },
                        plural(playlist.tracks.size, "трек", "трека", "треков"),
                        playlist.downloadedCount.takeIf { it > 0 }?.let { "$it на устройстве" }
                    ).joinToString(" · "),
                    onArtworkClick = { imagePicker.launch(arrayOf("image/*")) },
                    artwork = {
                        if (playlist.artworkUrl != null) {
                            AsyncImage(
                                model = playlist.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            IconCover(icon = Icons.AutoMirrored.Filled.QueueMusic)
                        }
                    },
                    actions = {
                        if (playlist.tracks.isNotEmpty()) {
                            PanelPrimaryButton(
                                text = "Слушать",
                                icon = Icons.Default.PlayArrow,
                                onClick = { onPlayTrack(playlist.tracks.first()) }
                            )
                            PanelIconButton(
                                icon = Icons.Rounded.Shuffle,
                                contentDescription = "Перемешать",
                                onClick = onShuffle
                            )
                            // Saves the tracks for this playlist only; "Скачанное" stays as it is.
                            PlaylistDownloadButton(playlist = playlist, onDownload = onDownloadAll)
                        } else {
                            PanelIconButton(
                                icon = Icons.Default.Image,
                                contentDescription = "Сменить обложку",
                                onClick = { imagePicker.launch(arrayOf("image/*")) }
                            )
                        }
                    }
                )
            }

            item(key = "playlist-gap") { Spacer(modifier = Modifier.height(20.dp)) }

            if (playlist.tracks.isEmpty()) {
                item(key = "playlist-empty") {
                    EmptyState("Здесь пока нет треков. Зажмите обложку трека в плеере, чтобы добавить его.")
                }
            } else {
                itemsIndexed(playlist.tracks, key = { _, track -> "playlist-${playlist.id}-${track.id}" }) { index, track ->
                    DownloadedTrackCard(
                        track = track,
                        isSelected = track.id == currentTrackId,
                        progress = downloadProgress[track.id],
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onDeleteDownload = { onRemoveTrack(track) },
                        position = groupPosition(index, playlist.tracks.size)
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
                val containerColor = MaterialTheme.colorScheme.primaryContainer
                val tintColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp)
        ) {
            item(key = "yandex-playlist-hero") {
                val isLikedPlaylist = playlist.id == -100L
                CollectionHero(
                    title = playlist.title ?: "Без названия",
                    kicker = "Яндекс Музыка",
                    subtitle = plural(playlist.trackCount, "трек", "трека", "треков"),
                    onArtworkClick = { imagePicker.launch(arrayOf("image/*")) },
                    artwork = {
                        if (playlist.artworkUrl != null) {
                            AsyncImage(
                                model = playlist.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            IconCover(
                                icon = if (isLikedPlaylist) Icons.Default.Favorite else Icons.AutoMirrored.Filled.QueueMusic
                            )
                        }
                    },
                    actions = {
                        if (playlist.tracks.isNotEmpty()) {
                            PanelPrimaryButton(
                                text = "Слушать",
                                icon = Icons.Default.PlayArrow,
                                onClick = { onPlayTrack(playlist.tracks.first()) }
                            )
                        }
                        PanelIconButton(
                            icon = Icons.Default.Image,
                            contentDescription = "Сменить обложку",
                            onClick = { imagePicker.launch(arrayOf("image/*")) }
                        )
                    }
                )
            }

            item(key = "yandex-playlist-gap") { Spacer(modifier = Modifier.height(20.dp)) }

            if (isLoading) {
                item(key = "yandex-playlist-loading") { LoadingBlock() }
            } else if (playlist.tracks.isEmpty()) {
                item(key = "yandex-playlist-empty") {
                    EmptyState("Здесь пока нет треков.")
                }
            } else {
                val favoritesMap = favorites.associateBy { it.id }
                itemsIndexed(
                    playlist.tracks,
                    key = { _, track -> "yandex-playlist-detail-${playlist.id}-${track.id}" }
                ) { index, track ->
                    val favorite = favoritesMap[track.id]
                    TrackCard(
                        track = track,
                        isFavorite = favorite != null,
                        isSelected = track.id == currentTrackId,
                        downloadState = favorite?.downloadState,
                        progress = downloadProgress[track.id],
                        isPlaying = isPlaying,
                        onClick = { onPlayTrack(track) },
                        onFavoriteClick = { onFavoriteClick(track) },
                        position = groupPosition(index, playlist.tracks.size)
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
    onLoadAllTracks: () -> Unit = {},
    onShuffle: () -> Unit = {}
) {
    if (selectedPlaylist != null) {
        SetDetailContent(
            playlist = selectedPlaylist,
            subtitle = artist.username.orEmpty(),
            isLoading = isLoading,
            favorites = favorites,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            downloadProgress = downloadProgress,
            onBack = onDeselectPlaylist,
            onPlayTrack = onPlayTrack,
            onFavoriteClick = onFavoriteClick,
            artistName = artist.username
        )
        return
    }

    val listState = rememberLazyListState()
    val collapsed = rememberCollapsed(listState, ArtistPortraitHeight - 160.dp)
    // Five tracks until asked for the rest, then the whole list — loading it if only the
    // highlights are in.
    var showAllTracks by remember(artist.id, artist.username) { mutableStateOf(false) }
    val shownTracks = if (showAllTracks) tracks else tracks.take(TOP_TRACKS)
    val totalTracks = maxOf(artist.trackCount ?: 0, tracks.size)
    // YouTube Music doesn't say how many songs an artist has, only where to find them all.
    val countKnown = artist.trackCount != null
    val canShowMore = !showAllTracks &&
        (tracks.size > TOP_TRACKS || (!isAllTracksLoaded && (totalTracks > TOP_TRACKS || !countKnown)))

    // The portrait is this screen's picture; it gets a quiet backdrop rather than the shapes.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item(key = "artist-hero") {
                ArtistPortraitHeader(
                    artist = artist,
                    albumCount = playlists.size,
                    onPlay = tracks.firstOrNull()?.let { first -> { onPlayTrack(first) } },
                    onShuffle = if (tracks.isEmpty()) null else onShuffle
                )
            }

            if (isLoading) {
                item(key = "artist-loading") { LoadingBlock() }
            } else if (error != null) {
                item(key = "artist-error") {
                    Box(modifier = Modifier.padding(16.dp)) { MessageCard(error) }
                }
            } else {
                if (tracks.isNotEmpty()) {
                    item(key = "artist-tracks-title") {
                        SectionTitle(
                            text = "Популярные треки",
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            actionLabel = when {
                                canShowMore -> if (countKnown) "Все $totalTracks" else "Все"
                                showAllTracks && tracks.size > TOP_TRACKS -> "Свернуть"
                                else -> null
                            },
                            onAction = {
                                if (showAllTracks) {
                                    showAllTracks = false
                                } else {
                                    showAllTracks = true
                                    if (!isAllTracksLoaded) onLoadAllTracks()
                                }
                            }
                        )
                    }
                    itemsIndexed(shownTracks, key = { index, track -> "artist-track-${track.id}-$index" }) { index, track ->
                        val favorite = favorites.firstOrNull { it.id == track.id }
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            TrackCard(
                                track = track,
                                isFavorite = favorite != null,
                                isSelected = track.id == currentTrackId,
                                downloadState = favorite?.downloadState,
                                progress = downloadProgress[track.id],
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track) },
                                onFavoriteClick = { onFavoriteClick(track) },
                                position = groupPosition(index, shownTracks.size),
                                number = index + 1
                            )
                        }
                    }
                }

                if (playlists.isNotEmpty()) {
                    item(key = "artist-sets-title") {
                        SectionTitle(
                            "Альбомы и плейлисты",
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    item(key = "artist-sets") {
                        val isYandexArtist = artist.permalinkUrl?.startsWith("yandex") == true
                        AlbumCarousel(
                            albums = playlists.map { playlist ->
                                val isAlbum = isYandexArtist ||
                                    playlist.permalinkUrl?.startsWith("yandex:album:") == true
                                CarouselAlbum(
                                    key = playlist.id,
                                    title = playlist.title ?: "Альбом",
                                    subtitle = "",
                                    caption = if (isAlbum) {
                                        "Альбом · " + plural(playlist.trackCount, "трек", "трека", "треков")
                                    } else {
                                        setCaption(playlist)
                                    },
                                    artworkUrl = playlist.displayArtworkUrl,
                                    onClick = { onPlaylistClick(playlist) }
                                )
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (!artist.description.isNullOrBlank()) {
                    item(key = "artist-about-title") {
                        SectionTitle(
                            "Об артисте",
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    item(key = "artist-about") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            ExpandableDescription(text = artist.description)
                        }
                    }
                }
            }
        }

        CollapsingTopBar(title = artist.username.orEmpty(), collapsed = collapsed, onBack = onBack)
    }
}

/** How many of an artist's tracks show before "Все". */
private const val TOP_TRACKS = 5

/** Album or playlist opened from the artist page or from search: cover, then its tracks. */
@Composable
private fun SetDetailContent(
    playlist: SoundCloudPlaylist,
    subtitle: String,
    isLoading: Boolean,
    favorites: List<FavoriteTrack>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    downloadProgress: Map<Long, Float>,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    error: String? = null,
    artistName: String? = null
) {
    val isYandex = playlist.permalinkUrl?.contains("yandex") == true
    val albumLibrary = LocalAlbumLibrary.current
    val liked = albumLibrary?.likedBySource?.get(playlist.sourceKey())
    val tracks = playlist.knownTracks
    // Until the full list arrives only a few tracks are known; the set's own count is closer
    // to what's about to appear.
    val trackCount = if (isLoading) maxOf(playlist.trackCount, tracks.size) else tracks.size
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
            // No title here: the hero right below already carries it.
            title = {}
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "set-hero") {
                val artworkUrl = ArtworkUrls.highRes(playlist.displayArtworkUrl)
                CollectionHero(
                    title = playlist.title ?: "Без названия",
                    kicker = if (isYandex) "Альбом" else setCaption(playlist),
                    subtitle = listOf(subtitle, plural(trackCount, "трек", "трека", "треков"))
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    artwork = {
                        if (artworkUrl != null) {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            IconCover(icon = Icons.Default.Album)
                        }
                    },
                    actions = if (tracks.isNotEmpty()) {
                        {
                            PanelPrimaryButton(
                                text = "Слушать",
                                icon = Icons.Default.PlayArrow,
                                onClick = { onPlayTrack(tracks.first()) }
                            )
                            if (albumLibrary != null) {
                                // Liking saves the album as a playlist next to "Скачанное"; from
                                // then on it can be downloaded as a whole.
                                PanelIconButton(
                                    icon = if (liked != null) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (liked != null) "Убрать из медиатеки" else "Сохранить в медиатеку",
                                    onClick = { albumLibrary.onToggleLike(playlist, artistName ?: subtitle) },
                                    selected = liked != null
                                )
                                if (liked != null) {
                                    PlaylistDownloadButton(playlist = liked, onDownload = { albumLibrary.onDownload(liked) })
                                }
                            }
                        }
                    } else {
                        null
                    }
                )
            }

            if (isLoading) {
                item(key = "set-loading") { LoadingBlock(height = 120.dp) }
            }

            if (error != null) {
                item(key = "set-error") { MessageCard(error) }
            }

            if (tracks.isEmpty()) {
                if (!isLoading && error == null) {
                    item(key = "set-empty") {
                        EmptyState("Здесь пока нет треков.")
                    }
                }
            } else {
                item(key = "set-tracks") {
                    PagedTrackList(
                        tracks = tracks,
                        favorites = favorites,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        downloadProgress = downloadProgress,
                        onPlayTrack = onPlayTrack,
                        onFavoriteClick = onFavoriteClick,
                        perPage = 5
                    )
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
    onRedownload: () -> Unit = {},
    // Only for tracks with a radio to start — YouTube Music's.
    onRadio: (() -> Unit)? = null
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

                                if (onRadio != null) {
                                    ListItem(
                                        headlineContent = { Text("Радио по треку") },
                                        supportingContent = { Text("Трек и то, что YouTube Music поставит за ним") },
                                        leadingContent = {
                                            SheetActionIcon(
                                                icon = Icons.Default.Radio,
                                                container = MaterialTheme.colorScheme.tertiaryContainer,
                                                content = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        },
                                        colors = ListItemDefaults.colors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                                        ),
                                        modifier = Modifier.clickable {
                                            onRadio()
                                            dismissSheet()
                                        }
                                    )

                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                }

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

/**
 * The captcha SoundCloud's bot protection (DataDome) answers a like with from a network it
 * distrusts. The page is DataDome's own: once solved it calls `window.android.onCaptchaSuccess`
 * with the cookie that lets further requests through — the hook DataDome's Android SDK uses.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun AntiBotCaptchaDialog(
    captchaUrl: String,
    onSolved: (cookie: String) -> Unit,
    onDismiss: () -> Unit
) {
    var isWebViewLoading by remember { mutableStateOf(true) }
    val currentOnSolved by rememberUpdatedState(onSolved)

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
                        text = "Проверка SoundCloud",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Text(
                    text = "SoundCloud не принимает лайки с этого адреса, пока не пройдена проверка. " +
                        "После неё лайк отправится сам.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // The user agent is left alone: DataDome binds the solved
                                // captcha to this browser, and the likes that follow go out from
                                // another WebView of the same app, which must look identical.
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                }
                                var delivered = false
                                addJavascriptInterface(
                                    object {
                                        @JavascriptInterface
                                        fun onCaptchaSuccess(cookie: String) {
                                            // Called on a WebView thread.
                                            post {
                                                if (!delivered && cookie.isNotBlank()) {
                                                    delivered = true
                                                    currentOnSolved(cookie)
                                                }
                                            }
                                        }
                                    },
                                    "android"
                                )
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebViewLoading = false
                                    }
                                }
                                loadUrl(captchaUrl)
                            }
                        },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isWebViewLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            AppLoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Signing in to YouTube Music on its own site, as Metrolist does it: Google's sign-in page, then
 * music.youtube.com. Once there with a session, its cookies and the page's visitor id and account
 * index are what [com.example.myapplication.data.YouTubeMusicClient] signs requests with. The
 * WebView keeps its own user agent: Google refuses sign-ins from browsers pretending to be others.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YtMusicLoginDialog(
    onCaptured: (YtAuth) -> Unit,
    onDismiss: () -> Unit
) {
    var isWebViewLoading by remember { mutableStateOf(true) }
    val currentOnCaptured by rememberUpdatedState(onCaptured)

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Вход в YouTube Music", style = MaterialTheme.typography.titleLarge)
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                val cookies = CookieManager.getInstance()
                                cookies.setAcceptCookie(true)
                                cookies.setAcceptThirdPartyCookies(this, true)
                                var delivered = false
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebViewLoading = false
                                        val page = view ?: return
                                        if (delivered || android.net.Uri.parse(url.orEmpty()).host != "music.youtube.com") return
                                        val cookie = cookies.getCookie("https://music.youtube.com").orEmpty()
                                        if ("SAPISID=" !in cookie) return
                                        page.evaluateJavascript(
                                            "(function(){var c=window.yt&&window.yt.config_;" +
                                                "return JSON.stringify({v:c?c.VISITOR_DATA:null,u:c?String(c.SESSION_INDEX||0):'0'});})()"
                                        ) { result ->
                                            if (delivered) return@evaluateJavascript
                                            delivered = true
                                            cookies.flush()
                                            // The value comes back JSON-encoded: a string holding the object.
                                            val config = runCatching {
                                                com.google.gson.JsonParser.parseString(
                                                    com.google.gson.JsonParser.parseString(result).asString
                                                ).asJsonObject
                                            }.getOrNull()
                                            currentOnCaptured(
                                                YtAuth(
                                                    cookie = cookie,
                                                    visitorData = config?.get("v")?.takeUnless { it.isJsonNull }?.asString,
                                                    authUser = config?.get("u")?.takeUnless { it.isJsonNull }?.asString
                                                        ?.filter(Char::isDigit)?.ifBlank { null } ?: "0"
                                                )
                                            )
                                        }
                                    }
                                }
                                loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
                            }
                        },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isWebViewLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            AppLoadingIndicator()
                        }
                    }
                }
            }
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
                            AppLoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}



