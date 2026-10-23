@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.example.myapplication.ui

import com.example.myapplication.i18n.english
import com.example.myapplication.i18n.tr
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.animateDecay
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.SizeTransform
import androidx.compose.ui.unit.IntSize
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.em
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.util.lerp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import com.example.myapplication.data.LyricLine
import com.example.myapplication.data.YtAuth
import com.example.myapplication.data.YtShelf
import com.example.myapplication.data.social.ShowcaseSlot
import com.example.myapplication.data.isYtLikedMusic
import com.example.myapplication.data.youTubeTrackId
import com.example.myapplication.data.liveVideoId
import com.example.myapplication.data.youTubeVideoId
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.AutoAwesome
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.zIndex
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Checklist
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
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Translate
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import androidx.media3.common.Player
import com.example.myapplication.data.DownloadState
import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.MixSection
import com.example.myapplication.data.SoundCloudMix
import com.example.myapplication.data.SharedLinks
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
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
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
    // Looked up by the progress bars themselves as they draw: see [DownloadProgressOf].
    val downloadProgressMap = viewModel.downloadProgress.collectAsState()
    val downloadProgress = remember { DownloadProgressOf { downloadProgressMap.value[it] } }
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
    // Held, not read: it changes twice a second while a track plays, and read here it rebuilt this
    // whole screen each time. Only the seek bar, the lyrics and the mini player's progress read it.
    val playbackPosition = viewModel.playbackPositionMs.collectAsState()
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
    // The full player pulled off its place: see TrackDetailScreen's onPulledChange.
    var playerPulled by remember { mutableStateOf(false) }
    // A finger on the full player, which may be about to pull it down.
    var playerTouched by remember { mutableStateOf(false) }
    // Tracks being picked in "Скачанное": their toolbar has the bottom edge, as home's has.
    // The page over home, if any (a mix opens over it on its own, see mixShown), and whether one
    // is being pulled off its place.
    val pages = androidx.compose.animation.core.updateTransition(
        targetState = screen.takeIf { it != AppScreen.HOME && it != AppScreen.MIX_DETAIL },
        label = "pages"
    )
    var pagePulled by remember { mutableStateOf(false) }
    // A page opened from another page (the friends from the profile): pulled down, it uncovers
    // that page rather than home.
    val pageUnder by remember { derivedStateOf { viewModel.pageUnder(pages.targetState) } }
    val mixShown = remember { MutableTransitionState(false) }
    // It stays under the player opened from it, as any screen does: hidden while the player was
    // up, it went away with its list and came back in from the top of it, and pulling the player
    // down showed home instead of the mix.
    mixShown.targetState = selectedMix != null
    val playerShown = remember { MutableTransitionState(false) }
    playerShown.targetState = selectedTrack != null
    // The full player, opaque, covers the whole screen: the backdrop and the screens under it
    // aren't drawn meanwhile. With a video in the player they were painted again on every frame
    // of it, unseen — half of what the phone's graphics had to do. A finger on the player draws
    // them again at once, so that a pull finds them there.
    val playerCovers by remember {
        derivedStateOf { playerShown.isIdle && playerShown.currentState && !playerPulled && !playerTouched }
    }
    // How much of home shows: it fades back as a page comes over it — search and settings are
    // see-through between their cards, and home under them in full showed through as they came in
    // — and back in as the page goes. Under a page being pulled off it shows at once, whole: that
    // is what the pull uncovers.
    val homeShown = remember { Animatable(if (screen == AppScreen.HOME || screen == AppScreen.MIX_DETAIL) 1f else 0f) }
    LaunchedEffect(homeShown) {
        snapshotFlow { Triple(pages.targetState == null, pagePulled, pageUnder != null) }.collectLatest { (noPage, pulled, underPage) ->
            when {
                pulled && !underPage -> homeShown.snapTo(1f)
                noPage -> homeShown.animateTo(1f, tween(240))
                else -> homeShown.animateTo(0f, tween(160))
            }
        }
    }
    // Gone from under a page, or under a mix that has come up over it, home isn't drawn at all.
    val homeHidden by remember {
        derivedStateOf {
            (!pagePulled || pageUnder != null) && (homeShown.value == 0f || (mixShown.isIdle && mixShown.currentState))
        }
    }
    // What is covered, for what moves by itself there (see LocalCovered): read as it is needed,
    // so that nothing composes again each time a finger lands on the player.
    val homeCovered = remember { { homeHidden || playerCovers } }
    val pagesCovered = remember { { playerCovers } }
    val showDebugPercentage by viewModel.showDebugPercentage.collectAsState()
    val downloadedPercentages by viewModel.downloadedPercentages.collectAsState()
    val isAllArtistTracksLoaded by viewModel.isAllArtistTracksLoaded.collectAsState()
    val backgroundMotion by viewModel.settingsRepo.backgroundMotion.collectAsState()
    val smoothMotion by viewModel.settingsRepo.smoothMotion.collectAsState()
    // Read by the loops on every frame, not composed: see MotionPace.
    androidx.compose.runtime.SideEffect { MotionPace.smooth = smoothMotion }
    val playerCoverColors by viewModel.settingsRepo.playerCoverColors.collectAsState()
    val videoGlow by viewModel.settingsRepo.videoGlow.collectAsState()
    val videoGlowStyle by viewModel.settingsRepo.videoGlowStyle.collectAsState()
    val coverGlowStyle by viewModel.settingsRepo.coverGlowStyle.collectAsState()

    // The playing track's cover colours are worked out before the player opens, so it opens in
    // them instead of fading over from the app's own every time; and its neighbours' in the queue
    // as well, so the player turns to the next track's colours with its cover, not a while later.
    val coverContext = LocalContext.current
    LaunchedEffect(playerCoverColors, currentPlayingTrack?.id, activeQueue) {
        if (!playerCoverColors) return@LaunchedEffect
        val current = currentPlayingTrack ?: return@LaunchedEffect
        val index = activeQueue.indexOfFirst { it.id == current.id }
        val neighbours = if (index < 0) emptyList() else activeQueue.drop(index + 1).take(2) + listOfNotNull(activeQueue.getOrNull(index - 1))
        (listOf(current) + neighbours)
            .mapNotNull { it.artworkUrl?.takeIf(String::isNotBlank) }
            .forEach { prefetchCoverColors(coverContext, it) }
    }

    val yandexPlaylists by viewModel.yandexPlaylists.collectAsState()
    val yandexShelves by viewModel.yandexShelves.collectAsState()
    // What each service's home opens on, in its big carousel (see HomeSetupOverlay).
    val homeHeroes by viewModel.settingsRepo.homeHeroes.collectAsState()
    val yandexWaveOn by viewModel.yandexWaveOn.collectAsState()
    val yandexRadioOn by viewModel.yandexRadioOn.collectAsState()
    val yandexWavePicks by viewModel.yandexWavePicks.collectAsState()
    val trackFx by viewModel.trackFx.collectAsState()
    val playerFxButton by viewModel.settingsRepo.playerFxButton.collectAsState()
    val yandexWaveStarting by viewModel.yandexWaveStarting.collectAsState()
    val yandexToken by viewModel.yandexToken.collectAsState()
    val hasYandexToken = yandexToken.isNotEmpty()
    val yandexLoginUrl by viewModel.yandexLoginUrl.collectAsState()
    val antiBotCaptchaUrl by viewModel.antiBotCaptchaUrl.collectAsState()

    val searchOpenedPlaylist by viewModel.searchOpenedPlaylist.collectAsState()
    // An album opened over the artist's page: a collection screen, with its toolbar under the thumb.
    val artistAlbumOpen by viewModel.selectedArtistPlaylist.collectAsState()
    val playingFrom by viewModel.playingFrom.collectAsState()
    val togetherState by viewModel.together.state.collectAsState()
    val crossfadeSeconds by viewModel.settingsRepo.crossfadeSeconds.collectAsState()
    var showTogether by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    // The friends opened on the requests: from the profile's "заявки", or the avatar's menu.
    var friendsOnRequests by remember { mutableStateOf(false) }
    // The avatar's menu on home (see AccountMenu): shut once anything else comes over home.
    val accountMenu = rememberAccountMenuState()
    // Signed in, the profile has a dock at its foot, as search has, for the mini player to stand on.
    val socialSession by remember { com.example.myapplication.data.social.Social.get(coverContext) }.session.collectAsState()
    LaunchedEffect(screen, selectedMix != null, selectedTrack != null) {
        if (screen != AppScreen.HOME || selectedMix != null || selectedTrack != null) accountMenu.close()
    }

    val downloadedTracks = remember(favorites) { favorites.filter { it.downloadState == DownloadState.DOWNLOADED } }
    val history by viewModel.history.collectAsState()

    // Checks the SoundCloud session whenever the app comes back, so a token that lapsed in the
    // meantime is renewed before the next tap needs it; and «Любимое» hears of changes made on
    // other devices while the app is in sight.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.onAppForeground()
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) viewModel.onAppBackground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onAppBackground()
        }
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
            AppScreen.HISTORY -> viewModel.closeHistory()
            AppScreen.PROFILE -> viewModel.closeProfile()
            AppScreen.FRIENDS -> viewModel.closeFriends()
            AppScreen.PERSON -> viewModel.closePerson()
            AppScreen.PLAYLISTS -> viewModel.closePlaylists()
            AppScreen.SETTINGS -> viewModel.closeSettings()
            AppScreen.MIX_DETAIL -> viewModel.closeMix()
            AppScreen.PLAYLIST_DETAIL -> viewModel.closePlaylist()
            AppScreen.YANDEX_PLAYLIST_DETAIL -> viewModel.deselectYandexPlaylist()
            AppScreen.YTM_SET_DETAIL -> viewModel.closeYtSet()
            // An album opened on the artist's page closes back to the page, as pulling it down does.
            AppScreen.ARTIST_DETAIL -> if (viewModel.selectedArtistPlaylist.value != null) {
                viewModel.deselectArtistPlaylist()
            } else {
                viewModel.closeArtist()
            }
            AppScreen.HOME -> Unit
        }
    }

    // What glass shows: the screens over the backdrop (for the mini player, which floats over them
    // all). The backdrop itself is under the window, and glass opens down to it (see frosted).
    val screensFrost = rememberFrostSource()
    Box(modifier = Modifier.fillMaxSize()) {
        // The full player is opaque, and so is the sign-in screen; nothing behind them needs a
        // frame, or the accelerometer.
        ExpressiveBackground(motionEnabled = backgroundMotion, animated = selectedTrack == null && !isLoggedOut && !playerCovers)

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
                },
                onDownloadAlbum = { album, artistName ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.downloadAlbum(album, artistName)
                },
                onTogglePlay = viewModel::togglePlayPause,
                onShuffle = { tracks, source ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.playShuffled(tracks, source = source)
                },
                onToggleMix = { mix, title, tracks ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleMixSave(mix, title, tracks)
                },
                onDownloadMix = { mix, title, tracks ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.downloadMix(mix, title, tracks)
                }
            )
        }
        if (isLoggedOut) {
            SoundCloudLoginScreen(viewModel = viewModel)
        } else androidx.compose.runtime.CompositionLocalProvider(
            LocalAlbumLibrary provides albumLibrary,
            LocalFrostSources provides emptyList(),
            LocalNowPlaying provides NowPlaying(isPlaying, playingFrom, currentTrackId),
            LocalTogether provides togetherLabel(togetherState)?.let { label -> TogetherBadge(label) { showTogether = true } }
        ) {
            Box(modifier = Modifier.fillMaxSize().drawWithContent { if (!playerCovers) drawContent() }.frostSource(screensFrost)) {
            // Every screen stands on the moving backdrop: their pages, panels, buttons and cards
            // are glass. (Not the full player, which draws its own backdrop.)
            androidx.compose.runtime.CompositionLocalProvider(
                LocalGlass provides true,
                LocalPagePulled provides { pagePulled = it }
            ) {
            // Home at the bottom, always there: a page over it pulled down shows it under the page
            // at once, as the player shows what it was opened from, and coming back finds it as it
            // was left. Covered by a page that has settled it isn't drawn, nor does anything on it
            // move by itself (see LocalCovered).
            UnderPages(hidden = { homeHidden }, shown = { homeShown.value }) {
                androidx.compose.runtime.CompositionLocalProvider(LocalCovered provides homeCovered) {
                        HomeScreen(
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
                            onPlayYtTrack = { track, queue ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playQueuedTrack(track, queue)
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
                            downloadedCount = downloadedTracks.size,
                            downloadedFolderArtworkUri = downloadedFolderArtworkUri,
                            history = history,
                            playlists = playlists,
                            yandexPlaylists = yandexPlaylists,
                            yandexShelves = yandexShelves,
                            onPlayYandexTrack = { track, queue -> viewModel.playYandexTrack(track, queue) },
                            wave = HomeWave(
                                on = yandexWaveOn,
                                starting = yandexWaveStarting,
                                picks = yandexWavePicks,
                                onToggle = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleYandexWave()
                                },
                                onPick = viewModel::pickYandexWave,
                                onReset = viewModel::resetYandexWave
                            ),
                            heroes = homeHeroes,
                            miniPlayerShown = currentTrackTitle != null,
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
                            onOpenHistory = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openHistory()
                            },
                            onOpenSettings = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openSettings()
                            },
                            onOpenProfile = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openProfile()
                            },
                            onOpenFriends = { requests ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                friendsOnRequests = requests
                                viewModel.openFriends()
                            },
                            onOpenPerson = { id ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openPerson(id)
                            },
                            accountMenu = accountMenu,
                            onOpenMix = { mix ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.openMix(mix)
                            },
                            onReloadMixes = viewModel::loadMixes,
                            updates = viewModel.updates
                        )
                }
            }
            // The account's pages: as pages, and under one of them being pulled off (see pageUnder).
            val accountPage: @Composable (AppScreen) -> Unit = { page ->
                when (page) {
                    AppScreen.PROFILE -> ProfileScreen(
                        onClose = viewModel::closeProfile,
                        onOpenSettings = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openSettings()
                        },
                        onOpenFriends = { requests ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            friendsOnRequests = requests
                            viewModel.openFriends()
                        },
                        onOpenPerson = { id ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openPerson(id)
                        },
                        nowPlaying = currentPlayingTrack,
                        isPlaying = isPlaying,
                        onOpenNowPlaying = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentPlayingTrack?.let(viewModel::openTrack)
                        },
                        onPickShowcase = { slot ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.pickShowcase(slot)
                        },
                        onPlayLink = viewModel::openSharedText
                    )
                    AppScreen.FRIENDS -> FriendsScreen(
                        onClose = viewModel::closeFriends,
                        onOpenPerson = { id ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.openPerson(id)
                        },
                        startWithRequests = friendsOnRequests
                    )
                    AppScreen.PERSON -> {
                        val personId by viewModel.personId.collectAsState()
                        personId?.let { id ->
                            PersonScreen(
                                id = id,
                                onClose = viewModel::closePerson,
                                onPlayLink = viewModel::openSharedText
                            )
                        }
                    }
                    else -> Unit
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalCovered provides pagesCovered) {
            // What a page pulled off another page uncovers: that page, there as the pull begins, so
            // that it is what shows under the pull and what stays once the page has gone. It tells
            // nobody it is pulled: it isn't.
            pageUnder?.let { under ->
                if (pagePulled) androidx.compose.runtime.CompositionLocalProvider(LocalPagePulled provides {}) {
                    Box(modifier = Modifier.fillMaxSize()) { accountPage(under) }
                }
            }
            pages.AnimatedContent(
                transitionSpec = {
                    // Screens share the top-bar geometry now, so a soft fade+scale makes the bar
                    // look like it stays put while only the content beneath it swaps.
                    // No size animation: every screen fills the window, and animating the size only
                    // ever showed when a screen drew nothing for a moment — it then grew out of the
                    // top-left corner.
                    if (pagePulled && targetState != null && viewModel.pageUnder(initialState) == targetState) {
                        // Pulled off onto the page under it: that page is on screen already.
                        (EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None) using null
                    } else {
                        // Faded on what they draw (see glassFade).
                        (scaleIn(initialScale = 0.97f, animationSpec = tween(240)) togetherWith
                            scaleOut(targetScale = 1.02f, animationSpec = tween(160))) using null
                    }
                },
                contentKey = { it }
            ) { screen ->
                // Pulled off onto the page under it, that page is on screen already: no fade.
                val fade = glassFade(enter = if (pagePulled) snap() else tween(240), exit = if (pagePulled) snap() else tween(160))
                // Home is under the pages, not one of them.
                if (screen != null) Box(
                    // A touch on a page stops there: none reaches home under it.
                    modifier = Modifier.fillMaxSize().then(fade).pointerInput(Unit) { }
                ) {
                when (screen) {
                    AppScreen.HOME, AppScreen.MIX_DETAIL -> Unit
                    AppScreen.PROFILE, AppScreen.FRIENDS, AppScreen.PERSON -> accountPage(screen)
                    AppScreen.HISTORY -> HistoryScreen(
                        tracks = history,
                        favorites = favorites,
                        currentTrackId = currentTrackId,
                        downloadProgress = downloadProgress,
                        isPlaying = isPlaying,
                        onBack = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.closeHistory()
                        },
                        onPlayTrack = { track ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playHistoryTrack(track)
                        },
                        onTogglePlay = viewModel::togglePlayPause,
                        onShuffle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playHistoryShuffled()
                        },
                        onFavoriteClick = { track ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(track)
                        },
                        onClear = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.clearHistory()
                        }
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
                        // Picking for the profile's showcase: a tap on what it wants puts it there.
                        val showcasePick by viewModel.showcasePick.collectAsState()
                        SearchScreen(
                            title = showcasePick?.pickTitle ?: tr("Поиск"),
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
                                if (showcasePick == ShowcaseSlot.TRACK) viewModel.chooseShowcaseTrack(track) else viewModel.playQueuedTrack(track)
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
                                if (showcasePick == ShowcaseSlot.ARTIST) viewModel.chooseShowcaseArtist(artist) else viewModel.openArtistFromSearch(artist)
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
                                if (showcasePick == ShowcaseSlot.ALBUM) viewModel.chooseShowcaseSet(playlist) else viewModel.openSearchPlaylist(playlist)
                            },
                            onClosePlaylist = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.closeSearchPlaylist()
                            },
                            onPlayPlaylistTrack = { track, queue ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.playQueuedTrack(track, queue, source = searchOpenedPlaylist?.let { "set-${it.id}" })
                            }
                        )
                    }

                    AppScreen.DOWNLOADS -> DownloadsScreen(
                        tracks = downloadedTracks,
                        folderArtworkUri = downloadedFolderArtworkUri,
                        currentTrackId = currentTrackId,
                        playerOpen = selectedTrack != null,
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
                        onDeleteDownloads = { tracks ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            tracks.forEach(viewModel::deleteDownloadedTrack)
                        },
                        onImportTracks = viewModel::importLocalTracks,
                        onShuffle = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.playDownloadsShuffled()
                        },
                        showDebugPercentage = showDebugPercentage,
                        downloadedPercentages = downloadedPercentages
                    )

                    AppScreen.SETTINGS -> {
                        val soundcloudLikesSyncStatus by viewModel.soundcloudLikesSyncStatus.collectAsState()
                        val yandexLikesSyncStatus by viewModel.yandexLikesSyncStatus.collectAsState()
                        val likesPushStatus by viewModel.likesPushStatus.collectAsState()
                        SettingsScreen(
                            onOpenTogether = { showTogether = true },
                            onOpenStats = { showStats = true },
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
                                    viewModel.playQueuedTrack(track, playlist.tracks, source = "set-${playlist.id}")
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
                                    viewModel.playQueuedTrack(track, set.tracks, source = "set-${set.id}")
                                },
                                onFavoriteClick = { track ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleFavorite(track)
                                },
                                onArtistClick = { artist ->
                                    viewModel.openArtistDetails(
                                        userId = artist.id ?: 0L,
                                        permalinkUrl = artist.permalinkUrl,
                                        username = artist.username,
                                        avatarUrl = artist.avatarUrl
                                    )
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
                        val artistAlbumLoading by viewModel.artistAlbumLoading.collectAsState()
                        val artistError by viewModel.artistError.collectAsState()
                        val selectedArtistPlaylist by viewModel.selectedArtistPlaylist.collectAsState()
                        val artistFollow by viewModel.artistFollow.collectAsState()
                        val artistLives by viewModel.artistLives.collectAsState()
                        val artistShelves by viewModel.artistShelves.collectAsState()
                        val ytArtistShowAll by viewModel.settingsRepo.ytArtistShowAll.collectAsState()
                        val artistTracksAreVideos by viewModel.artistTracksAreVideos.collectAsState()
                        currentArtist?.let { artist ->
                            ArtistDetailScreen(
                                artist = artist,
                                tracks = currentArtistTracks,
                                playlists = currentArtistPlaylists,
                                tracksTitle = if (artistTracksAreVideos) tr("Видео") else tr("Популярные треки"),
                                follow = artistFollow,
                                onToggleFollow = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleArtistFollow()
                                },
                                lives = artistLives,
                                // The page whole, or as before: its tracks, albums and broadcasts.
                                shelves = if (ytArtistShowAll) artistShelves else emptyList(),
                                onPlayFrom = { track, queue ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.playQueuedTrack(track, queue)
                                },
                                onOpenArtist = { other ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.openArtistDetails(
                                        userId = 0L,
                                        permalinkUrl = other.permalinkUrl,
                                        username = other.username,
                                        avatarUrl = other.avatarUrl
                                    )
                                },
                                onRetry = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.retryArtist()
                                },
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
                                    viewModel.playQueuedTrack(track, queue, source = selectedArtistPlaylist?.let { "set-${it.id}" })
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
                                isAlbumLoading = artistAlbumLoading,
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

                }
                }
            }
            }

            // Under the player, still and not drawn, as the pages are.
            androidx.compose.runtime.CompositionLocalProvider(LocalCovered provides pagesCovered) {
            AnimatedVisibility(
                visibleState = mixShown,
                // Faded on what it draws (see glassFade), as fadeIn and fadeOut would.
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Box(modifier = Modifier.fillMaxSize().then(glassFade(PageFadeSpring, PageFadeSpring))) {
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
            }
            }
            }
            }

            // On home the floating toolbar owns the bottom edge; the mini player sits on top of it.
            // So it does on a collection's screen (an album, a playlist, a mix), whose toolbar is
            // laid out as home's (see CollectionToolbar).
            val searchTabsShown = screen == AppScreen.SEARCH && selectedMix == null && searchOpenedPlaylist == null
            val collectionShown = selectedMix != null || when (screen) {
                AppScreen.PLAYLIST_DETAIL, AppScreen.YANDEX_PLAYLIST_DETAIL, AppScreen.YTM_SET_DETAIL,
                AppScreen.DOWNLOADS, AppScreen.HISTORY -> true
                AppScreen.ARTIST_DETAIL -> artistAlbumOpen != null
                AppScreen.SEARCH -> searchOpenedPlaylist != null
                else -> false
            }
            val miniPlayerLift by animateDpAsState(
                targetValue = when {
                    collectionShown -> HomeToolbarClearance
                    screen == AppScreen.HOME -> HomeToolbarClearance
                    searchTabsShown -> searchDockHeight(hasYandexToken || ytMusicAccount != null) - 4.dp
                    screen == AppScreen.FRIENDS && selectedMix == null -> searchDockHeight(tabs = true) - 4.dp
                    screen == AppScreen.PROFILE && socialSession != null && selectedMix == null -> searchDockHeight(tabs = true) - 4.dp
                    else -> 0.dp
                },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "miniPlayerLift"
            )
            // Under a player being pulled down it is there already, in its place and without
            // coming in: the player folds away onto it, rather than it rising after the player
            // has gone.
            val sideways = isLandscape()
            AnimatedVisibility(
                // Not while the avatar's menu stands where it is.
                visible = currentTrackTitle != null && (selectedTrack == null || playerPulled) && !accountMenu.open,
                enter = if (selectedTrack != null) EnterTransition.None else slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    // Sideways, at the foot of the pane on the right, under the thumb.
                    .align(if (sideways) Alignment.BottomEnd else Alignment.BottomCenter)
                    .then(
                        if (sideways) {
                            Modifier
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                                .width(LandscapePaneWidth)
                        } else {
                            Modifier
                        }
                    )
                    .then(
                        // On search the tabs ride the keyboard, and the mini player rides them.
                        if (searchTabsShown) {
                            Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                        } else {
                            Modifier.navigationBarsPadding()
                        }
                    )
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + miniPlayerLift)
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalFrostSources provides listOf(screensFrost),
                    LocalGlass provides true
                ) {
                PlayerBar(
                    title = currentTrackTitle.orEmpty(),
                    artist = currentPlayingTrack?.user?.username.orEmpty(),
                    artworkUrl = currentPlayingTrack?.artworkUrl,
                    isPlaying = isPlaying,
                    progress = {
                        // A broadcast has no end to be part way to: the line runs full. Playing,
                        // where the player is this very frame, not the twice-a-second sample.
                        if (currentPlayingTrack?.liveVideoId != null) {
                            1f
                        } else if (playbackDurationMs > 0L) {
                            val at = if (isPlaying) viewModel.livePositionMs() else playbackPosition.value
                            at.coerceIn(0L, playbackDurationMs).toFloat() / playbackDurationMs.toFloat()
                        } else {
                            0f
                        }
                    },
                    onTogglePlay = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.togglePlayPause()
                    },
                    onOpen = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentPlayingTrack?.let(viewModel::openTrack)
                    },
                    onSwipe = viewModel::skipToNeighbourTrack,
                    canSwipe = viewModel::hasNeighbourTrack
                )
                }
            }

            AnimatedVisibility(
                visibleState = playerShown,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                selectedTrack?.let { track ->
                    val favorite = favorites.firstOrNull { it.id == track.id }
                    CoverTheme(enabled = playerCoverColors, artworkUrl = track.artworkUrl) {
                    TrackDetailScreen(
                        track = track,
                        activeQueue = activeQueue,
                        // A Yandex radio's order is the radio's; picking a track in it still plays it.
                        onReorderQueue = if (yandexRadioOn) null else viewModel::reorderActiveQueue,
                        queueNote = if (yandexRadioOn) {
                            tr("%s подбирает Яндекс и сам добавляет новые треки. Нажмите на трек, чтобы перейти к нему", if (yandexWaveOn) tr("Порядок «Моей волны»") else tr("Порядок радио"))
                        } else {
                            null
                        },
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
                        positionMs = { playbackPosition.value },
                        durationMs = max(playbackDurationMs, track.duration),
                        lyrics = lyrics?.takeIf { it.trackId == track.id }?.lines,
                        video = trackVideo?.takeIf { it.trackId == track.id },
                        upcomingVideo = pendingTrackVideo?.takeIf { it.trackId == track.id },
                        livePosition = viewModel::livePositionMs,
                        videoGlow = videoGlow,
                        videoGlowStyle = videoGlowStyle,
                        coverGlowStyle = coverGlowStyle,
                        hasNeighbourTrack = viewModel::hasNeighbourTrack,
                        onSwipeTrack = { next ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.skipToNeighbourTrack(next)
                        },
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
                        // The wave has no order to shuffle: in its place, "not for me".
                        onDislike = if (yandexWaveOn) viewModel::dislikeYandexTrack else null,
                        trackFx = trackFx.takeIf { playerFxButton },
                        onTrackFxChange = viewModel::setTrackFx,
                        ytAuth = viewModel.settingsRepo::ytMusicAuth,
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
                        },
                        onAlbumClick = viewModel::openTrackAlbum,
                        onPulledChange = { playerPulled = it },
                        onTouchedChange = { playerTouched = it }
                    )
                    }
                }
            }
        }
    }

    // Once signed in to SoundCloud (and whenever asked for again in settings): the other services,
    // each a tap from its sign-in, and what each service's home opens on.
    val homeSetupDone by viewModel.settingsRepo.homeSetupDone.collectAsState()
    val homeChoicesOpen by viewModel.settingsRepo.homeChoicesOpen.collectAsState()
    // The first time the app opens (and whenever asked for again in settings): the gestures
    // nothing on screen shows, each tried out on the real thing — after the setup.
    val onboardingDone by viewModel.settingsRepo.onboardingDone.collectAsState()
    if ((!homeSetupDone || homeChoicesOpen) && !isLoggedOut) {
        HomeSetupOverlay(
            services = listOf(
                SetupService("SoundCloud", ServiceIcons.SoundCloud, tr("Миксы, станции и тренды"), connected = true, onConnect = null),
                SetupService(
                    title = tr("Яндекс Музыка"),
                    icon = ServiceIcons.YandexMusic,
                    about = tr("Моя форма, медиатека и подборки"),
                    connected = hasYandexToken,
                    onConnect = viewModel::startYandexLogin
                ),
                SetupService(
                    title = "YouTube Music",
                    icon = ServiceIcons.YouTubeMusic,
                    about = tr("Подборки, клипы и эфиры"),
                    connected = ytMusicAccount != null,
                    onConnect = viewModel::openYtMusicLogin
                )
            ),
            heroes = heroChoices(homeHeroes, hasYandexToken, ytMusicAccount != null, ytHome, yandexShelves, playlists),
            onPick = viewModel.settingsRepo::setHomeHero,
            onFinish = {
                viewModel.settingsRepo.setHomeSetupDone(true)
                viewModel.settingsRepo.homeChoicesOpen.value = false
            },
            // Asked for again from the settings: the carousels alone.
            homeOnly = homeSetupDone
        )
    } else if (!onboardingDone && !isLoggedOut) {
        OnboardingOverlay(onFinish = {
            viewModel.settingsRepo.setOnboardingDone(true)
        })
    }

    val context = LocalContext.current
    if (showStats) {
        StatsSheet(onDismiss = { showStats = false })
    }
    if (showTogether) {
        TogetherSheet(
            together = viewModel.together,
            missingServices = remember { com.example.myapplication.together.TogetherServices.ALL - viewModel.signedInServices() },
            onHost = viewModel::hostTogether,
            onSearch = viewModel::searchTogether,
            onJoin = viewModel::joinTogether,
            onLeave = viewModel::leaveTogether,
            onDismiss = { showTogether = false }
        )
    }
    (togetherState as? com.example.myapplication.together.TogetherState.Hosting)?.request?.let { request ->
        TogetherRequestDialog(request) { accept ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.answerTogether(request, accept)
        }
    }

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
            // A file added from the phone has nowhere to be downloaded again from, and a broadcast
            // is never downloaded at all.
            onRedownload = if (capturedTrack.urn?.startsWith("local:") == true || capturedTrack.liveVideoId != null) null else {
                { viewModel.redownloadTrack(capturedTrack) }
            },
            // The player has no menu of its own at the top any more: a downloaded track is taken
            // off the phone from here.
            onDeleteDownload = favorites
                .firstOrNull { it.id == capturedTrack.id && it.downloadState == DownloadState.DOWNLOADED }
                ?.let { fav -> { viewModel.deleteDownloadedTrack(fav) } },
            onRadio = when {
                capturedTrack.youTubeVideoId != null -> {
                    { viewModel.playYtRadio(capturedTrack) }
                }
                capturedTrack.urn?.startsWith("yandex:track:") == true -> {
                    { viewModel.playYandexRadio(capturedTrack) }
                }
                else -> null
            },
            radioDescription = if (capturedTrack.youTubeVideoId != null) {
                tr("Трек и то, что YouTube Music поставит за ним")
            } else {
                tr("Трек, а за ним радио Яндекс Музыки — без конца, под то, что слушаешь")
            },
            onTogether = {
                showTrackActionsDialog = false
                showTogether = true
            },
            crossfadeSeconds = crossfadeSeconds,
            onCrossfade = viewModel.settingsRepo::setCrossfadeSeconds
        )
    }
}

/** The places home switches between, in the order the floating toolbar shows them. */
/** What home's toolbar picks between: a service, or what is on the device. */
private enum class HomeService(private val ru: String, val icon: (() -> ImageVector)?, val letter: String? = null) {
    SoundCloud("SoundCloud", { ServiceIcons.SoundCloud }),
    Yandex("Яндекс Музыка", { ServiceIcons.YandexMusic }),
    YouTube("YouTube Music", { ServiceIcons.YouTubeMusic }),
    // Not only downloads: history and playlists too, a library.
    Downloads("Моя музыка", { Icons.Default.LibraryMusic });

    // In the app's language as it is shown, not as the enum was made: it may change meanwhile.
    val title: String get() = tr(ru)
}

// The order is persisted (the saved tab is an ordinal): new sections go at the end.
private enum class HomeCategory(private val ru: String, val service: HomeService) {
    Mixes("Миксы", HomeService.SoundCloud),
    Stations("Станции", HomeService.SoundCloud),
    Trending("Тренды", HomeService.SoundCloud),
    YouTube("YouTube Music", HomeService.YouTube),
    Library("Медиатека", HomeService.Yandex),
    MyMusic("Моя музыка", HomeService.Downloads),
    MyWave("Моя форма", HomeService.Yandex),
    YandexShelf("Яндекс Музыка", HomeService.Yandex);

    val title: String get() = tr(ru)
}

/** Yandex's wave as home shows it, as "Моя форма"; see [MyWavePage]. */
private class HomeWave(
    val on: Boolean,
    val starting: Boolean,
    val picks: Map<String, String>,
    val onToggle: () -> Unit,
    val onPick: (key: String, seed: String) -> Unit,
    val onReset: () -> Unit
)

/**
 * A row of YouTube Music's home as hero tiles: each song its own tile, playing with the row's
 * songs after it, and each playlist, mix or album a tile that opens it.
 */
private fun ytShelfItems(
    shelf: YtShelf,
    onOpen: (SoundCloudPlaylist) -> Unit,
    onPlay: (SoundCloudTrack, List<SoundCloudTrack>) -> Unit
): List<HeroItem> = buildList {
    fun addSet(set: SoundCloudPlaylist) = add(
        HeroItem(
            key = "yt-set-${set.id}",
            title = set.title ?: tr("Без названия"),
            subtitle = set.user?.username,
            artworkUrl = set.artworkUrl,
            source = "set-${set.id}",
            onClick = { onOpen(set) }
        )
    )
    // The liked songs lead the row they're in, ahead of its songs: the first row's, always
    // (see YouTubeMusicClient.home).
    val (liked, sets) = shelf.sets.partition { it.isYtLikedMusic }
    liked.forEach(::addSet)
    shelf.tracks.forEach { track ->
        add(
            HeroItem(
                key = "yt-track-${track.id}",
                title = track.title ?: tr("Без названия"),
                subtitle = track.user?.username,
                artworkUrl = track.artworkUrl,
                trackId = track.id,
                onClick = { onPlay(track, shelf.tracks) }
            )
        )
    }
    sets.forEach(::addSet)
}.distinctBy { it.key }

/**
 * Home under the pages opened over it: drawn, and there for TalkBack, only while [hidden] says it
 * shows — while a page comes in or goes, or is pulled off it. [shown] is how much of it: fading
 * back a touch as it goes, as the screens did when one replaced another.
 */
@Composable
private fun UnderPages(hidden: () -> Boolean, shown: () -> Float = { 1f }, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent { if (!hidden()) drawContent() }
            .graphicsLayer {
                val amount = shown()
                alpha = amount
                val scale = 1f + 0.02f * (1f - amount)
                scaleX = scale
                scaleY = scale
            }
            .then(if (hidden()) Modifier.clearAndSetSemantics { } else Modifier)
    ) {
        content()
    }
}

/** What home's floating toolbar takes off the bottom edge, including its gap to the mini player. */
private val HomeToolbarClearance = 64.dp + 12.dp

/** What home's feed keeps clear at its foot: the floating toolbar, the mini player over it, a gap. */
private val FeedBottomRoom = HomeToolbarClearance + 72.dp + 12.dp + 16.dp

/** What a shelf shows its items as: picked from what it holds, never from its name. */
private enum class ShelfKind { Stage, Squares, Tracks, Circles, Live, Genres }

/** A note in a shelf's place — not signed in, not loaded, failed — and what can be done about it. */
@androidx.compose.runtime.Immutable
private class ShelfNote(val text: String, val action: String? = null, val onAction: () -> Unit = {})

/**
 * A shelf of home's feed: a section of a service as a row of what is in it, or a note in its place
 * while there is nothing to show.
 */
@androidx.compose.runtime.Immutable
private class HomeShelf(
    val key: String,
    val title: String,
    val kind: ShelfKind,
    items: List<HeroItem>,
    val note: ShelfNote? = null,
    val loading: Boolean = false,
    // Something in it is on air now, as YouTube's broadcasts are.
    val live: Boolean = false,
    // How many it holds, when one of its items only makes another (a new playlist).
    count: Int? = null
) {
    // Each once: a row's items are told apart by their keys, and a key twice in one row stops the app.
    val items: List<HeroItem> = items.distinctBy { it.key }
    val count: Int = count ?: this.items.size

    // Opened whole only when it holds more than a glance takes in.
    val openable: Boolean get() = note == null && items.size > 2
}

/** A row of YouTube Music's or Yandex Music's home as a shelf: songs alone as rows of songs, broadcasts as frames. */
private fun rowShelf(key: String, shelf: YtShelf, items: List<HeroItem>, lead: Boolean): HomeShelf {
    val live = shelf.tracks.any { it.liveVideoId != null }
    val kind = when {
        lead -> ShelfKind.Stage
        live -> ShelfKind.Live
        shelf.sets.isEmpty() && shelf.tracks.isNotEmpty() -> ShelfKind.Tracks
        else -> ShelfKind.Squares
    }
    return HomeShelf(key = key, title = shelf.title, kind = kind, items = items, live = live)
}

/** [this] with the shelf on the stage first, the rest in their order. */
private fun List<HomeShelf>.stageFirst(): List<HomeShelf> = sortedBy { if (it.kind == ShelfKind.Stage) 0 else 1 }

// «Моя форма» as Yandex's carousel: the choice's key.
private const val HERO_WAVE = "wave"

/**
 * What each service's carousel can open on, for the setup to offer (see HomeSetupOverlay):
 * SoundCloud's and the phone's shelves by their keys, Yandex's and YouTube's own rows by their
 * titles as the services have them now — YouTube changes its own, and the one picked may be gone
 * by the next day (its first opens then).
 */
private fun heroChoices(
    picked: Map<String, String>,
    yandex: Boolean,
    youTube: Boolean,
    ytRows: List<YtShelf>,
    yandexRows: List<YtShelf>,
    playlists: List<Playlist>
): List<HeroChoices> {
    fun rows(rows: List<YtShelf>, count: Int) = rows
        .filter { it.title.isNotBlank() && (it.tracks.isNotEmpty() || it.sets.isNotEmpty()) }
        .map { "row:" + it.title to it.title }
        .distinctBy { it.first }
        .take(count)
    // A row picked before and gone meanwhile still shows, as the one picked.
    fun withPicked(options: List<Pair<String, String>>, choice: String) =
        if (choice.startsWith("row:") && options.none { it.first == choice }) options + (choice to choice.removePrefix("row:")) else options
    return buildList {
        add(
            HeroChoices(
                service = HomeService.SoundCloud.name,
                title = HomeService.SoundCloud.title,
                icon = ServiceIcons.SoundCloud,
                options = listOf("mixes" to HomeCategory.Mixes.title, "stations" to HomeCategory.Stations.title, "trending" to HomeCategory.Trending.title),
                picked = picked[HomeService.SoundCloud.name] ?: "mixes"
            )
        )
        if (yandex) {
            val choice = picked[HomeService.Yandex.name] ?: HERO_WAVE
            add(
                HeroChoices(
                    service = HomeService.Yandex.name,
                    title = HomeService.Yandex.title,
                    icon = ServiceIcons.YandexMusic,
                    options = withPicked(listOf(HERO_WAVE to tr("Моя форма"), "library" to HomeCategory.Library.title) + rows(yandexRows, 3), choice),
                    picked = choice
                )
            )
        }
        if (youTube) {
            val choice = picked[HomeService.YouTube.name].orEmpty()
            val own = rows(ytRows, 5)
            add(
                HeroChoices(
                    service = HomeService.YouTube.name,
                    title = HomeService.YouTube.title,
                    icon = ServiceIcons.YouTubeMusic,
                    options = withPicked(listOf("" to tr("Что YouTube поставит первым")) + own, choice),
                    picked = choice,
                    note = if (own.isEmpty()) {
                        tr("Подборки появятся, когда YouTube Music загрузится: выбрать их можно будет в настройках.")
                    } else {
                        tr("YouTube Music меняет свои подборки: если выбранной не окажется, откроется первая.")
                    }
                )
            )
        }
        add(
            HeroChoices(
                service = HomeService.Downloads.name,
                title = HomeService.Downloads.title,
                icon = Icons.Default.LibraryMusic,
                options = listOf("device" to tr("На устройстве")) +
                    (if (playlists.any { it.isLikedAlbum }) listOf("albums" to tr("Альбомы")) else emptyList()) +
                    ("playlists" to tr("Плейлисты")),
                picked = picked[HomeService.Downloads.name] ?: "device"
            )
        )
    }
}

/** The service's own line under its name, over its feed. */
private fun feedLine(service: HomeService, shelves: List<HomeShelf>): String {
    val sections = plural(shelves.size, tr("раздел"), tr("раздела"), tr("разделов"))
    return when (service) {
        HomeService.Yandex -> tr("Моя форма и %s", sections)
        HomeService.Downloads -> tr("Скачанное, история и плейлисты")
        else -> {
            val total = shelves.sumOf { it.items.size }
            if (total == 0) sections else sections + " · " + plural(total, tr("подборка"), tr("подборки"), tr("подборок"))
        }
    }
}

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
    onPlayYtTrack: (SoundCloudTrack, List<SoundCloudTrack>) -> Unit,
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
    downloadedCount: Int,
    downloadedFolderArtworkUri: String?,
    history: List<SoundCloudTrack>,
    playlists: List<Playlist>,
    yandexPlaylists: List<SoundCloudPlaylist>,
    yandexShelves: List<YtShelf>,
    onPlayYandexTrack: (SoundCloudTrack, List<SoundCloudTrack>) -> Unit,
    wave: HomeWave,
    // What each service's carousel opens on, by service (see heroChoices).
    heroes: Map<String, String>,
    // Whether the mini player stands on the toolbar: the hero keeps clear of it.
    miniPlayerShown: Boolean,
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenYandexPlaylist: (SoundCloudPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onRelogin: () -> Unit,
    onAutoRefreshClientId: () -> Unit,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenFriends: (requests: Boolean) -> Unit,
    onOpenPerson: (String) -> Unit,
    accountMenu: AccountMenuState,
    onOpenMix: (SoundCloudMix) -> Unit,
    onReloadMixes: () -> Unit,
    updates: com.example.myapplication.data.UpdateRepository
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    // The toolbar picks a service; a service not connected in settings isn't offered.
    val services = remember(ytConnected, yandexConnected) {
        HomeService.entries.filter { service ->
            when (service) {
                HomeService.Yandex -> yandexConnected
                HomeService.YouTube -> ytConnected
                else -> true
            }
        }
    }
    // [selectedTab] is a category's ordinal, kept between launches: the service it belongs to.
    val savedCategory = HomeCategory.entries.getOrNull(selectedTab)
    var pickedService by remember { mutableStateOf(savedCategory?.service ?: HomeService.SoundCloud) }
    // Signed out of the service meanwhile: SoundCloud's page instead.
    val service = pickedService.takeIf { it in services } ?: HomeService.SoundCloud
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    // Each service's feed where it was left: there again when the service comes back into view,
    // the pager having let go of its page meanwhile.
    val feedStates = HomeService.entries.associateWith { rememberLazyListState() }
    // A shelf opened whole over its feed: by its service and its key, so that it shows what the
    // shelf holds now, not what it held when it was opened.
    var opened by remember { mutableStateOf<Pair<HomeService, String>?>(null) }

    val mixes = mixSection?.mixes.orEmpty()
    val stations = stationSection?.mixes.orEmpty()
    val trending = trendingSection?.mixes.orEmpty()
    val ytRows = remember(ytShelves) { ytShelves.filter { it.tracks.isNotEmpty() || it.sets.isNotEmpty() } }
    // Each service's carousel as picked (see heroChoices); what isn't there, the service's own first.
    val scLead = heroes[HomeService.SoundCloud.name]?.takeIf { it == "stations" || it == "trending" } ?: "mixes"
    val yaLead = heroes[HomeService.Yandex.name] ?: HERO_WAVE
    val ytLead = heroes[HomeService.YouTube.name].orEmpty()
    val dlLead = heroes[HomeService.Downloads.name] ?: "device"

    // The shelves, each worked out again only when what it shows changes: never as a frame goes by,
    // nor as playing starts or stops — the covers that stand for what plays light up by themselves
    // (see LocalNowPlaying).
    val soundCloudFeed = remember(mixes, stations, trending, hasOauthToken, mixesError, mixesLoading, loadingMixId, scLead) {
        val leadKind = when (scLead) {
            "stations" -> HomeCategory.Stations
            "trending" -> HomeCategory.Trending
            else -> HomeCategory.Mixes
        }
        fun items(list: List<SoundCloudMix>, kind: HomeCategory) = list.map { mix ->
            HeroItem(
                key = mix.id,
                title = when (kind) {
                    HomeCategory.Stations -> mix.title
                    HomeCategory.Trending -> localizedGenre(mix.title)
                    else -> localizedMixTitle(mix.title)
                },
                // Mixes ship their artist list in `description`. Stations put "Artist station"
                // there, which is not a caption — the station's artist is its title. A genre's
                // description is an English sentence about the chart.
                subtitle = when (kind) {
                    HomeCategory.Stations -> tr("Станция")
                    HomeCategory.Trending -> tr("Чарт недели")
                    else -> mix.description?.takeIf { it.isNotBlank() }
                },
                // The stage's cover is large; a station's circle and a genre's tile are not.
                artworkUrl = artworkUrlForSize(mix.artworkUrl, if (kind == HomeCategory.Mixes || kind == leadKind) 500.dp else 120.dp),
                // What a mix's queue is started from (see MusicViewModel.playingFrom).
                source = "mix-${mix.id}",
                isLoading = loadingMixId == mix.id,
                onClick = { onOpenMix(mix) }
            )
        }
        fun note(list: List<SoundCloudMix>, missing: String, error: String?): ShelfNote? = when {
            list.isNotEmpty() || mixesLoading -> null
            error != null -> ShelfNote(error, tr("Повторить"), onReloadMixes)
            else -> ShelfNote(missing, tr("Обновить"), onReloadMixes)
        }
        if (!hasOauthToken) {
            // Said once, on the first shelf: the other two would only say it again.
            listOf(
                HomeShelf(
                    key = "mixes",
                    title = HomeCategory.Mixes.title,
                    kind = ShelfKind.Stage,
                    items = emptyList(),
                    note = ShelfNote(tr("Добавь OAuth-токен в настройках, чтобы увидеть персональные миксы."), tr("Открыть настройки"), onOpenSettings)
                )
            )
        } else {
            fun kind(key: String, own: ShelfKind) = if (key == scLead) ShelfKind.Stage else own
            listOf(
                HomeShelf("mixes", HomeCategory.Mixes.title, kind("mixes", ShelfKind.Squares), items(mixes, HomeCategory.Mixes),
                    note = note(mixes, tr("Подборка пока не загрузилась."), mixesError), loading = mixesLoading && mixes.isEmpty()),
                HomeShelf("stations", HomeCategory.Stations.title, kind("stations", ShelfKind.Circles), items(stations, HomeCategory.Stations),
                    note = note(stations, tr("Станции пока не загрузились."), null), loading = mixesLoading && stations.isEmpty()),
                HomeShelf("trending", HomeCategory.Trending.title, kind("trending", ShelfKind.Genres), items(trending, HomeCategory.Trending),
                    note = note(trending, tr("Тренды пока не загрузились."), null), loading = mixesLoading && trending.isEmpty())
            ).stageFirst()
        }
    }
    val yandexFeed = remember(yandexPlaylists, yandexShelves, yaLead) {
        // The row picked, the first of that name.
        val leadRow = yandexShelves.indexOfFirst { "row:" + it.title == yaLead }
        (listOf(
            HomeShelf(
                key = "library",
                title = HomeCategory.Library.title,
                kind = if (yaLead == "library") ShelfKind.Stage else ShelfKind.Squares,
                items = yandexPlaylists.map { playlist ->
                    HeroItem(
                        key = "yandex-${playlist.id}",
                        title = playlist.title ?: tr("Без названия"),
                        subtitle = plural(playlist.trackCount, tr("трек"), tr("трека"), tr("треков")),
                        artworkUrl = playlist.artworkUrl,
                        icon = if (playlist.id == -100L) Icons.Rounded.Favorite else Icons.Default.Album,
                        source = "set-${playlist.id}",
                        onClick = { onOpenYandexPlaylist(playlist) }
                    )
                },
                note = if (yandexPlaylists.isEmpty()) ShelfNote(tr("Плейлисты Яндекс Музыки пока не загрузились.")) else null
            )
        ) + yandexShelves.mapIndexed { index, shelf ->
            // Each row of Yandex Music's home a shelf of its own, as YouTube Music's are.
            rowShelf("ya-$index-${shelf.title}", shelf, ytShelfItems(shelf, onOpenYtSet, onPlayYandexTrack), lead = index == leadRow)
        }).stageFirst()
    }
    // «Моя форма» is Yandex's carousel unless another shelf was picked for it, and is there.
    val yandexWaveLeads = yandexFeed.none { it.kind == ShelfKind.Stage }
    val youTubeFeed = remember(ytRows, ytError, ytLoading, ytLead) {
        if (ytRows.isEmpty()) {
            listOf(
                HomeShelf(
                    key = "yt",
                    title = "YouTube Music",
                    kind = ShelfKind.Stage,
                    items = emptyList(),
                    note = when {
                        ytError != null -> ShelfNote(ytError, tr("Повторить"), onReloadYt)
                        ytLoading -> null
                        else -> ShelfNote(tr("Подборки YouTube Music пока не загрузились."), tr("Обновить"), onReloadYt)
                    },
                    loading = ytLoading
                )
            )
        } else {
            // Each row of YouTube Music's home a shelf of its own, as on the site; on the stage the
            // one picked — YouTube changes its rows, and gone, its first.
            val leadRow = ytRows.indexOfFirst { "row:" + it.title == ytLead }.coerceAtLeast(0)
            ytRows.mapIndexed { index, shelf ->
                rowShelf("yt-$index-${shelf.title}", shelf, ytShelfItems(shelf, onOpenYtSet, onPlayYtTrack), lead = index == leadRow)
            }.stageFirst()
        }
    }
    val myMusicFeed = remember(downloadedCount, downloadedFolderArtworkUri, history, playlists, dlLead) {
        fun item(playlist: Playlist): HeroItem {
            val count = plural(playlist.tracks.size, tr("трек"), tr("трека"), tr("треков"))
            return HeroItem(
                key = "local-${playlist.id}",
                title = playlist.name,
                subtitle = if (playlist.isLikedAlbum) {
                    listOfNotNull(playlist.artist?.takeIf { it.isNotBlank() }, count).joinToString(" · ")
                } else {
                    count
                },
                artworkUrl = playlist.artworkUrl,
                icon = if (playlist.isLikedAlbum) Icons.Default.Album else Icons.AutoMirrored.Filled.QueueMusic,
                source = "playlist-${playlist.id}",
                onClick = { onOpenPlaylist(playlist) }
            )
        }
        // Liked albums newest first, as they are kept; hand-made playlists after, and a new one last.
        val (albums, own) = playlists.partition { it.isLikedAlbum }
        // Liked albums on the stage only while there are some.
        val lead = dlLead.takeIf { it != "albums" || albums.isNotEmpty() } ?: "device"
        fun kind(key: String) = if (key == lead) ShelfKind.Stage else ShelfKind.Squares
        buildList {
            // What is on the phone, on the stage as the other services lead with theirs: where
            // the listener goes most.
            add(
                HomeShelf(
                    key = "device",
                    title = tr("На устройстве"),
                    kind = kind("device"),
                    items = listOf(
                        HeroItem(
                            key = "downloads",
                            title = tr("Скачанное"),
                            subtitle = plural(downloadedCount, tr("трек"), tr("трека"), tr("треков")),
                            artworkUrl = downloadedFolderArtworkUri,
                            icon = Icons.Default.Download,
                            source = "downloads",
                            onClick = onOpenDownloads
                        ),
                        // What was listened to: its latest covers together, as a folder of them.
                        HeroItem(
                            key = "history",
                            title = tr("История"),
                            subtitle = if (history.isEmpty()) tr("Пока пусто") else plural(history.size, tr("трек"), tr("трека"), tr("треков")),
                            artworkUrl = null,
                            collage = recentCovers(history),
                            icon = Icons.Default.History,
                            source = "history",
                            onClick = onOpenHistory
                        )
                    )
                )
            )
            if (albums.isNotEmpty()) add(HomeShelf("albums", tr("Альбомы"), kind("albums"), albums.map(::item)))
            add(
                HomeShelf(
                    key = "playlists",
                    title = tr("Плейлисты"),
                    kind = kind("playlists"),
                    count = own.size,
                    items = own.map(::item) + HeroItem(
                        key = "create",
                        title = tr("Создать плейлист"),
                        subtitle = tr("Своя подборка"),
                        artworkUrl = null,
                        icon = Icons.Default.Add,
                        onClick = { showCreatePlaylistDialog = true }
                    )
                )
            )
        }.stageFirst()
    }
    fun shelvesOf(shown: HomeService): List<HomeShelf> = when (shown) {
        HomeService.SoundCloud -> soundCloudFeed
        HomeService.Yandex -> yandexFeed
        HomeService.YouTube -> youTubeFeed
        HomeService.Downloads -> myMusicFeed
    }
    val openedShelf = opened?.let { (shown, key) -> shelvesOf(shown).firstOrNull { it.key == key }?.let { shown to it } }

    // The services side by side, a page each, following the finger.
    val servicePager = rememberPagerState(initialPage = services.indexOf(service).coerceAtLeast(0)) { services.size }
    // The service the toolbar lights: the page the pager is on, even mid-swipe.
    val shownService = services.getOrElse(servicePager.currentPage) { service }
    // A page come to rest is the service picked, kept for the next launch.
    LaunchedEffect(servicePager.settledPage, services) {
        val settled = services.getOrNull(servicePager.settledPage) ?: return@LaunchedEffect
        if (settled != pickedService) {
            pickedService = settled
            HomeCategory.entries.firstOrNull { it.service == settled }?.let { onTabSelected(it.ordinal) }
        }
    }
    // A service signed in or out of: the pages shift, the one shown stays.
    LaunchedEffect(services) {
        val index = services.indexOf(service)
        if (index >= 0 && index != servicePager.currentPage) servicePager.scrollToPage(index)
    }

    val hasWarning = clientId.isBlank() || needsRelogin || isClientIdExpired
    val menuItems = accountMenuItems(
        open = accountMenu.open,
        onOpenProfile = onOpenProfile,
        onOpenSettings = onOpenSettings,
        onOpenFriends = onOpenFriends,
        onOpenPerson = onOpenPerson
    )
    val notices: @Composable () -> Unit = {
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
    }
    val landscape = isLandscape()
    // What floats over the foot of the feed: the toolbar, and the mini player on it while it shows.
    val chrome = if (miniPlayerShown) FeedBottomRoom else HomeToolbarClearance + 20.dp

    // Under a shelf opened whole: dimmed as it comes, there again at once while it is pulled
    // away, and once it has settled not drawn at all, nor moving by itself.
    val pageShown = remember { Animatable(0f) }
    var pagePulled by remember { mutableStateOf(false) }
    LaunchedEffect(openedShelf != null) { pageShown.animateTo(if (openedShelf != null) 1f else 0f, tween(if (openedShelf != null) 240 else 160)) }
    val feedHidden by remember { derivedStateOf { pageShown.value >= 1f && !pagePulled } }
    val outerCovered = LocalCovered.current
    val feedCovered = remember(outerCovered) { { outerCovered() || feedHidden } }
    // The feed and a shelf opened over it, recorded for the toolbar floating over them: its glass
    // shows them blurred, as the mini player's and search's tabs do, rather than the backdrop
    // alone painted over them. The glass inside the record keeps to the backdrop.
    val backdrop = LocalFrostSources.current
    val feedFrost = rememberFrostSource()

    Box(modifier = Modifier.fillMaxSize()) {
    // Home stands on the moving backdrop: its panels and buttons are glass.
    androidx.compose.runtime.CompositionLocalProvider(LocalGlass provides true) {
    Box(modifier = Modifier.fillMaxSize().frostSource(feedFrost)) {
        // A service a page: a swipe anywhere the rows don't take it drags the next one in, and lets
        // it settle on a spring.
        androidx.compose.runtime.CompositionLocalProvider(LocalCovered provides feedCovered) {
        HorizontalPager(
            state = servicePager,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent { if (!feedHidden) drawContent() }
                .graphicsLayer {
                    val under = if (pagePulled) 0f else pageShown.value
                    alpha = 1f - 0.65f * under
                    val scale = 1f - 0.02f * under
                    scaleX = scale
                    scaleY = scale
                },
            key = { services[it] },
            beyondViewportPageCount = 1,
            flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                state = servicePager,
                // A third of the way over is enough to be meant.
                snapPositionalThreshold = 0.3f,
                snapAnimationSpec = ServicePageSpring
            )
        ) { page ->
            val shown = services[page]
            val shelves = shelvesOf(shown)
            val line = feedLine(shown, shelves)
            val pageModifier = Modifier
                .fillMaxSize()
                // The page on its way out sinks back a little and dims, the one coming in rises
                // from it.
                .graphicsLayer {
                    val away = ((servicePager.currentPage - page) + servicePager.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                    val scale = 1f - 0.06f * away
                    scaleX = scale
                    scaleY = scale
                    // Entirely off to the side it isn't drawn at all: dimmed, the page was
                    // painted into a picture of its own on every frame, out of sight.
                    alpha = if (away >= 1f) 0f else 1f - 0.45f * away
                }
            // A service's page kept beside the one shown is covered too: «Моя форма» turning on it
            // drew frames nobody saw.
            val pageCovered = remember(page, feedCovered) {
                { feedCovered() || (servicePager.settledPage != page && !servicePager.isScrollInProgress) }
            }
            val feed: @Composable (Modifier, Boolean) -> Unit = { feedModifier, header ->
                androidx.compose.runtime.CompositionLocalProvider(LocalCovered provides pageCovered) {
                ServiceFeed(
                    service = shown,
                    line = line,
                    shelves = shelves,
                    wave = wave.takeIf { shown == HomeService.Yandex },
                    waveLeads = yandexWaveLeads,
                    isPlaying = isPlaying,
                    listState = feedStates.getValue(shown),
                    header = header,
                    chrome = chrome,
                    notices = notices,
                    onOpenShelf = { shelf ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        opened = shown to shelf.key
                    },
                    modifier = feedModifier
                )
                }
            }
            if (landscape) {
                // Sideways the shelves on the left, and on the right, by the thumb, the service's
                // name over the mini player and the toolbar at the foot.
                Row(modifier = pageModifier) {
                    feed(Modifier.weight(1f).fillMaxHeight(), false)
                    Column(
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                            .width(LandscapePaneWidth)
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .padding(top = 12.dp)
                    ) {
                        FeedHeader(service = shown, line = line)
                    }
                }
            } else {
                feed(pageModifier, true)
            }
        }
        }

        // A shelf opened whole, over the feed and under the toolbar.
        AnimatedContent(
            targetState = openedShelf,
            transitionSpec = {
                // Faded on what it draws (see glassFade).
                slideInVertically(tween(320)) { it / 30 } togetherWith androidx.compose.animation.ExitTransition.KeepUntilTransitionsFinished
            },
            contentKey = { it?.second?.key },
            label = "shelfPage",
            modifier = Modifier.fillMaxSize()
        ) { shown ->
            val fade = glassFade(enter = tween(240), exit = tween(160))
            if (shown != null) Box(modifier = Modifier.fillMaxSize().then(fade)) {
                androidx.compose.runtime.CompositionLocalProvider(LocalPagePulled provides { pagePulled = it }) {
                    ShelfPage(
                        service = shown.first,
                        shelf = shown.second,
                        onClose = {
                            // Pulled away, the feed is in full view already: it isn't dimmed again
                            // only to come back.
                            if (pagePulled) scope.launch { pageShown.snapTo(0f) }
                            pagePulled = false
                            opened = null
                        }
                    )
                }
            }
        }
    }
    }

        androidx.compose.runtime.CompositionLocalProvider(LocalFrostSources provides backdrop + feedFrost) {
        // Over home, under the toolbar: the avatar's button stays where it is, the menu's toggle.
        AccountMenuOverlay(
            state = accountMenu,
            items = menuItems,
            placement = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .then(
                    if (isLandscape()) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End)) else Modifier
                )
                // At the toolbar's margin, over the button and a gap.
                .padding(end = 16.dp, bottom = 16.dp + 64.dp + 12.dp)
        )
        HomeToolbar(
            services = services,
            selected = shownService,
            onSelect = { picked ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                when {
                    accountMenu.open -> accountMenu.close()
                    // The service shown tapped again: the shelf over it closes, or the feed goes
                    // back to its top.
                    picked == service && picked == shownService -> {
                        if (opened != null) {
                            opened = null
                        } else {
                            scope.launch { feedStates.getValue(picked).animateScrollToItem(0) }
                        }
                    }
                    else -> {
                        opened = null
                        val index = services.indexOf(picked)
                        if (index >= 0) scope.launch { servicePager.animateScrollToPage(index, animationSpec = ServicePageSpring) }
                    }
                }
            },
            onSearch = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (accountMenu.open) accountMenu.close() else onOpenSearch()
            },
            menu = accountMenu,
            menuItems = menuItems,
            modifier = if (isLandscape()) {
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                    .width(LandscapePaneWidth)
                    .padding(16.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            }
        )
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(tr("Создать плейлист")) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text(tr("Название плейлиста")) },
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
                    Text(tr("Создать"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(tr("Отмена"))
                }
            }
        )
    }
}

// A shelf's room above and below it, and the edges its rows keep.
private val ShelfTop = 24.dp
private val ShelfBottom = 4.dp
private val ShelfEdge = 20.dp
private val ShelfGap = 12.dp
private val StageCaptionHeight = 80.dp
private val ShelfTrackHeight = 66.dp
private val ShelfTrackCover = 54.dp

/**
 * How large a shelf's things are, worked out from how wide the feed is: sizes fixed for one phone
 * read as tiny on a larger one. The stage is as wide as the old home's carousel; a row of covers
 * shows two and a half, so it is plain it goes on.
 */
@androidx.compose.runtime.Immutable
private class ShelfMetrics(width: Dp, height: Dp) {
    // Sideways the feed is wider than it is tall: the stage never taller than half the screen.
    val stage: Dp = minOf(width - (16.dp + HeroStrip + HeroGap) * 2, height * 0.46f).coerceIn(200.dp, 440.dp)
    val square: Dp = ((width - ShelfEdge - ShelfGap * 2) / 2.55f).coerceIn(112.dp, 210.dp)
    val circle: Dp = square * 0.92f
    val live: Dp = ((width - ShelfEdge * 2) * 0.84f).coerceIn(240.dp, 460.dp)
    val trackRow: Dp = (width - ShelfEdge * 2 - 32.dp).coerceIn(260.dp, 440.dp)
    val genreWidth: Dp = ((width - ShelfEdge - ShelfGap) / 2.3f).coerceIn(150.dp, 260.dp)
    val genreHeight: Dp = genreWidth * 0.5f
    val wave: Dp = minOf(width * 0.64f, height * 0.42f).coerceIn(220.dp, 360.dp)
}

private val LocalShelfMetrics = staticCompositionLocalOf { ShelfMetrics(400.dp, 860.dp) }

/** [content] with the shelves sized for the room it has. */
@Composable
private fun SizedShelves(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(maxWidth, screenHeight) { ShelfMetrics(maxWidth, screenHeight) }
        androidx.compose.runtime.CompositionLocalProvider(LocalShelfMetrics provides metrics) {
            content()
        }
    }
}

/**
 * A service's home. Upright, it opens on its first thing — «Моя форма», its mixes, what YouTube
 * Music puts first, what is on the phone — held in focus on a page of its own (see [HeroPage]),
 * as the old home showed a section: its name large in the middle at the top, the covers under it,
 * and the way on to the rest under them. A swipe takes the feed on to the shelves, one under
 * another, each sideways on its own; flung back up, it settles on the hero again (see
 * [HeroMagnet]). Sideways the shelves simply run on from the top. Scrolled past the hero, the
 * service's name stays on a bar at the top.
 */
@Composable
private fun ServiceFeed(
    service: HomeService,
    line: String,
    shelves: List<HomeShelf>,
    wave: HomeWave?,
    // «Моя форма» on the hero's page; false, another shelf has it, and the wave is a shelf under it.
    waveLeads: Boolean = true,
    isPlaying: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    header: Boolean,
    // What floats over the foot of the screen, which the hero keeps clear of: the toolbar, and the
    // mini player on it while something plays.
    chrome: Dp,
    notices: @Composable () -> Unit,
    onOpenShelf: (HomeShelf) -> Unit,
    modifier: Modifier = Modifier
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val waveHero = wave != null && waveLeads
    val heroShelf = if (header && !waveHero) shelves.firstOrNull()?.takeIf { it.kind == ShelfKind.Stage } else null
    val heroPage = header && (waveHero || heroShelf != null)
    // «Моя форма» given up the hero's page: the first shelf under it.
    val waveShelf = if (heroPage && !waveHero) wave else null
    val rest = if (heroShelf != null) shelves.drop(1) else shelves
    val density = LocalDensity.current
    // How tall the hero's page came out, and where the shelves rest: the page scrolled away but for
    // the strip the bar at the top covers, so the first shelf's name stands right under the bar.
    var heroPx by remember { mutableIntStateOf(0) }
    val overlapPx = with(density) { HeroPageOverlap.toPx() }
    val shelvesAt = { (heroPx - overlapPx).coerceAtLeast(0f) }
    val downAt = remember { floatArrayOf(Float.MAX_VALUE) }
    val magnet = rememberHeroMagnet(listState, shelvesAt) { downAt[0] }
    // 0 on the hero, 1 once the shelves have it: read as it is drawn.
    val away = {
        val at = shelvesAt()
        when {
            listState.firstVisibleItemIndex > 0 -> 1f
            at <= 0f -> 0f
            else -> (listState.firstVisibleItemScrollOffset / at).coerceIn(0f, 1f)
        }
    }
    val plainFling = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
    // How tall each shelf after the hero came out: a short feed (SoundCloud's two) gets room at
    // its end for the shelves to come all the way up under the bar, not stop half way.
    val shelfHeights = remember { androidx.compose.runtime.mutableStateMapOf<String, Int>() }
    SizedShelves(modifier = modifier) {
        val pageHeight = maxHeight - top - 8.dp
        val viewportPx = with(density) { maxHeight.roundToPx() }
        val paddingPx = with(density) { (top + 8.dp + bottom + FeedBottomRoom).roundToPx() }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (heroPage) {
                        Modifier.pointerInput(listState) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                downAt[0] = if (listState.firstVisibleItemIndex == 0) {
                                    listState.firstVisibleItemScrollOffset.toFloat()
                                } else {
                                    Float.MAX_VALUE
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(top = top + 8.dp, bottom = bottom + FeedBottomRoom),
            flingBehavior = if (heroPage) magnet else plainFling
        ) {
            if (heroPage) {
                item(key = "hero", contentType = "hero") {
                    HeroPage(
                        height = pageHeight,
                        bottomRoom = bottom + chrome,
                        peek = rest.isNotEmpty() || waveShelf != null,
                        away = away,
                        top = {
                            HeroTitle(
                                service = service,
                                title = if (waveHero) tr("Моя форма") else heroShelf?.title.orEmpty(),
                                live = heroShelf?.live == true
                            )
                            notices()
                        },
                        hero = { size ->
                            if (wave != null && waveHero) {
                                WaveHero(wave = wave, isPlaying = isPlaying, size = size)
                            } else if (heroShelf != null) {
                                StageHero(shelf = heroShelf, size = size, onOpen = { onOpenShelf(heroShelf) })
                            }
                        },
                        modifier = Modifier.onSizeChanged { heroPx = it.height }
                    )
                }
                if (waveShelf != null) {
                    item(key = "wave", contentType = "wave") {
                        Box(
                            modifier = Modifier
                                .onSizeChanged { shelfHeights["wave"] = it.height }
                                // Looking in under the hero, quieter than it, as a shelf does.
                                .graphicsLayer {
                                    alpha = 0.6f + 0.4f * away()
                                    compositingStrategy = CompositingStrategy.ModulateAlpha
                                }
                        ) {
                            WaveShelfRow(wave = waveShelf, isPlaying = isPlaying)
                        }
                    }
                }
            } else {
                if (header) {
                    item(key = "header", contentType = "header") { FeedHeader(service = service, line = line) }
                }
                item(key = "notices", contentType = "notices") { notices() }
                if (wave != null) {
                    item(key = "wave", contentType = "wave") { WaveShelfRow(wave = wave, isPlaying = isPlaying) }
                }
            }
            itemsIndexed(rest, key = { _, shelf -> shelf.key }, contentType = { _, shelf -> shelf.kind }) { index, shelf ->
                Box(
                    modifier = if (heroPage) {
                        Modifier
                            .onSizeChanged { shelfHeights[shelf.key] = it.height }
                            // The shelf looking in under the hero, quieter than it: all there once
                            // the shelves have the feed.
                            .then(
                                if (index == 0 && waveShelf == null) {
                                    Modifier.graphicsLayer {
                                        alpha = 0.6f + 0.4f * away()
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    } else {
                        Modifier
                    }
                ) {
                    ShelfView(shelf = shelf, onOpen = { onOpenShelf(shelf) })
                }
            }
            if (heroPage) {
                item(key = "tail", contentType = "tail") {
                    val shelvesPx = rest.sumOf { shelfHeights[it.key] ?: 0 } + (if (waveShelf != null) shelfHeights["wave"] ?: 0 else 0)
                    val tailPx = (viewportPx - paddingPx - shelvesPx - overlapPx.toInt()).coerceAtLeast(0)
                    Spacer(modifier = Modifier.height(with(density) { tailPx.toDp() }))
                }
            }
        }
        if (header) CollapsingTopBar(title = service.title, collapsed = rememberCollapsed(listState, 40.dp))
    }
}

// How much of the hero's page stays scrolled in when the shelves have the feed: the strip under the
// bar at the top (72 dp under the status bar, the list starting 8 dp under it), and a little more,
// for the first shelf's name to stand clear of the bar.
private val HeroPageOverlap = 48.dp

// A fling faster than this goes to the hero or to the shelves, whichever way it is going.
private val HeroMagnetFling = 400.dp

// What shows of the shelf after the hero over the mini player and the toolbar: its name, and the
// room over it.
private val HeroPeek = ShelfTop + 48.dp

// Between the section's name and the hero, at the least.
private val HeroTopGap = 16.dp

/**
 * The hero's page of a service's feed: [top] at the top — the service, the section's name large in
 * the middle; a notice if there is one — and under it [hero], given its size. Under the page, over
 * the mini player and the toolbar ([bottomRoom]), the next shelf's name looks in ([peek]): the way
 * on to the rest is the rest itself.
 *
 * Every service's hero is laid out alike — a stage of covers, «Моя форма» — the same size at the
 * same height, worked out from the room alone, never from what is on it: a tab swiped to another
 * leaves the hero where it stood. What the wave has under its shape is the most anything has, so
 * the size leaves it room: the covers' caption under theirs is shorter, and the stage stands in the
 * middle of the room over the shelf looking in. As the feed leaves for the shelves ([away]) the
 * hero sinks back a little and fades.
 */
@Composable
private fun HeroPage(
    height: Dp,
    bottomRoom: Dp,
    peek: Boolean,
    away: () -> Float,
    top: @Composable () -> Unit,
    hero: @Composable (size: Dp) -> Unit,
    modifier: Modifier = Modifier
) {
    // The mini player comes and goes: the hero rises over it and settles back, on a spring.
    val room by animateDpAsState(
        targetValue = bottomRoom,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "heroRoom"
    )
    SubcomposeLayout(modifier = modifier.fillMaxWidth()) { constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val topPlaced = subcompose("top") { Column { top() } }.map { it.measure(loose) }
        val roomPx = room.roundToPx()
        val peekPx = HeroPeek.roundToPx()
        val heightPx = height.roundToPx()
        // Where the hero may stand: under the name, over the shelf looking in — kept for it on every
        // page, one with no shelf after the hero too, for the hero to stand where the others' do.
        val from = topPlaced.sumOf { it.height } + HeroTopGap.roundToPx()
        val area = heightPx - roomPx - peekPx - from
        val captionPx = HeroCaptionHeight.roundToPx()
        val wavePx = (WaveChoicesHeight + 10.dp).roundToPx()
        // The wave's choices may go down over the shelf looking in: on its page the shelf is lower.
        val widest = width - ((HeroStrip + HeroGap + 8.dp) * 2).roundToPx()
        val sizePx = minOf(widest, area + peekPx - wavePx, HeroMaxSize.roundToPx()).coerceAtLeast(HeroMinSize.roundToPx())
        val offset = minOf((area - sizePx - captionPx) / 2, area + peekPx - sizePx - wavePx).coerceAtLeast(0)
        val heroPlaced = subcompose("hero") {
            Box(
                modifier = Modifier.graphicsLayer {
                    val gone = away()
                    val scale = 1f - 0.06f * gone
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.5f * gone
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                }
            ) { hero(sizePx.toDp()) }
        }.map { it.measure(loose) }
        val heroY = from + offset
        val heroBottom = heroY + heroPlaced.sumOf { it.height }
        // Ends where the next shelf looks in; with none after it, at the foot of the screen. Never
        // shorter than what it holds: on a short screen, the wave's page goes on under the toolbar.
        val pageHeight = if (peek) {
            maxOf(heightPx - roomPx - peekPx, heroBottom)
        } else {
            maxOf(heightPx, heroBottom + roomPx)
        }
        layout(width, pageHeight) {
            var y = 0
            topPlaced.forEach { it.place(0, y); y += it.height }
            heroPlaced.forEach { it.place((width - it.width) / 2, heroY) }
        }
    }
}

// The hero's size, however much room there is or isn't.
private val HeroMinSize = 200.dp
private val HeroMaxSize = 360.dp

// Under the hero's covers: the one in front's name and what it is, and the way to all of them.
private val HeroCaptionHeight = 64.dp

// The section's name over the hero: as tall for one line as for two.
private val HeroTitleHeight = 64.dp

/**
 * Over the hero, as the old home had it: the service in small capitals with its mark, and the
 * section's name large in the middle — in a box as tall for one line as for two, for every
 * service's hero to stand at the same height.
 */
@Composable
private fun HeroTitle(service: HomeService, title: String, live: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ShelfEdge, top = 20.dp, end = ShelfEdge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            service.icon?.let { icon ->
                Icon(imageVector = icon(), contentDescription = null, tint = PanelColors.accent, modifier = Modifier.size(16.dp))
            }
            Kicker(text = service.title)
        }
        Box(
            modifier = Modifier.padding(top = 6.dp).height(HeroTitleHeight),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // On air now, as YouTube's broadcasts are: the red of the player's «В эфире».
                if (live) Box(modifier = Modifier.size(12.dp).background(Color(0xFFFF3B30), CircleShape))
                // A long name ("Послушать ещё раз") a step smaller, to keep to a line where it can.
                val long = title.length > 12
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = if (long) 26.sp else 34.sp,
                        lineHeight = if (long) 32.sp else 40.sp
                    ),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// What «Моя форма» needs under its shape: the mood and the mode, each under its name.
private val WaveChoicesHeight = 176.dp

/** «Моя форма» as a shelf among the others: its name, "Сбросить" once something is picked, the shape. */
@Composable
private fun WaveShelfRow(wave: HomeWave, isPlaying: Boolean) {
    Column(modifier = Modifier.padding(top = ShelfTop, bottom = ShelfBottom)) {
        ShelfHeader(
            title = tr("Моя форма"),
            count = null,
            action = if (wave.picks.isEmpty()) null else {
                { TextButton(onClick = wave.onReset) { Text(tr("Сбросить")) } }
            }
        )
        MyWaveShelf(
            waveOn = wave.on,
            isPlaying = isPlaying,
            starting = wave.starting,
            picks = wave.picks,
            onToggle = wave.onToggle,
            onPick = wave.onPick,
            size = LocalShelfMetrics.current.wave
        )
    }
}

/** «Моя форма» as Yandex Music's hero: the shape as large as every service's hero, [size]. */
@Composable
private fun WaveHero(wave: HomeWave, isPlaying: Boolean, size: Dp) {
    MyWaveShelf(
        waveOn = wave.on,
        isPlaying = isPlaying,
        starting = wave.starting,
        picks = wave.picks,
        onToggle = wave.onToggle,
        onPick = wave.onPick,
        size = size,
        onReset = wave.onReset
    )
}

/**
 * A service's first shelf as its hero: its covers on the stage, [size] each, the one in front's
 * name under them and the way to all of them beside it — or what stands in for them.
 */
@Composable
private fun StageHero(shelf: HomeShelf, size: Dp, onOpen: () -> Unit) {
    when {
        shelf.note != null -> Box(
            modifier = Modifier.fillMaxWidth().height(size + HeroCaptionHeight),
            contentAlignment = Alignment.Center
        ) { ShelfNoteView(shelf.note) }
        shelf.items.isEmpty() -> Box(
            modifier = Modifier.fillMaxWidth().height(size + HeroCaptionHeight),
            contentAlignment = Alignment.Center
        ) { AppLoadingIndicator(modifier = Modifier.size(80.dp)) }
        // A carousel of its own for each shelf: another one picked for the hero is another pager,
        // not the last one's pages counted over the new one's covers.
        else -> androidx.compose.runtime.key(shelf.key) {
            HomeHeroCarousel(
                items = shelf.items,
                cover = size,
                hero = true,
                action = if (shelf.openable) {
                    { ShelfAllButton(title = shelf.title, count = shelf.count, onClick = onOpen) }
                } else {
                    null
                }
            )
        }
    }
}

/**
 * The feed's magnet. The hero's page and the shelves are two places to rest, and between them the
 * feed never stops: let go between them it goes to the nearer, or to the one it was flung toward.
 * Among the shelves it flings as any list does. A fling up that began down among them stops at
 * their top, under the bar — a hard swipe from the bottom threw the feed all the way back to the
 * hero; from their top, the next swipe up goes on to it. Landing is a light tick under the finger.
 */
private class HeroMagnet(
    private val listState: androidx.compose.foundation.lazy.LazyListState,
    private val decay: androidx.compose.animation.core.DecayAnimationSpec<Float>,
    private val shelvesAt: () -> Float,
    // How far into the hero's page the feed was as the finger came down; past it, MAX_VALUE.
    private val downAt: () -> Float,
    // Down this little from the shelves' top still counts as at it.
    private val topSlop: Float,
    private val minFling: Float,
    private val onLanded: () -> Unit
) : androidx.compose.foundation.gestures.FlingBehavior {
    // How far into the hero's page the feed is; null once the page has gone by.
    private fun position(): Float? =
        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else null

    override suspend fun androidx.compose.foundation.gestures.ScrollScope.performFling(initialVelocity: Float): Float {
        val start = position()
        val fromShelves = downAt() > shelvesAt() + topSlop
        if (start == null || start >= shelvesAt()) {
            var velocity = initialVelocity
            if (kotlin.math.abs(velocity) > 1f) {
                var last = 0f
                androidx.compose.animation.core.AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(decay) {
                    val delta = value - last
                    val unused = delta - scrollBy(delta)
                    last = value
                    velocity = this.velocity
                    val at = position()
                    if (kotlin.math.abs(unused) > 0.5f || (this.velocity < 0f && at != null && at < shelvesAt())) cancelAnimation()
                }
            }
            val at = position() ?: return velocity
            if (at >= shelvesAt()) return velocity
            // Up from among the shelves: they stop at their top. Up from their top: on to the hero.
            if (fromShelves) settle(at, shelvesAt(), 0f) else settle(at, 0f, velocity)
            return 0f
        }
        // The end of a short feed is as far as the shelves can come.
        val shelves = if (listState.canScrollForward) shelvesAt() else minOf(start, shelvesAt())
        val target = when {
            // Dragged up from among the shelves into the hero's page: the nearer, whatever the speed.
            fromShelves -> if (start > shelves / 2f) shelves else 0f
            initialVelocity > minFling -> shelves
            initialVelocity < -minFling -> 0f
            start > shelves / 2f -> shelves
            else -> 0f
        }
        settle(start, target, initialVelocity)
        return 0f
    }

    private suspend fun androidx.compose.foundation.gestures.ScrollScope.settle(from: Float, to: Float, velocity: Float) {
        if (kotlin.math.abs(to - from) < 1f) return
        var last = from
        androidx.compose.animation.core.animate(
            initialValue = from,
            targetValue = to,
            initialVelocity = velocity,
            // Onto the hero without bouncing off the top; onto the shelves with a little give.
            animationSpec = spring(dampingRatio = if (to == 0f) 1f else 0.82f, stiffness = 420f)
        ) { value, _ ->
            scrollBy(value - last)
            last = value
        }
        onLanded()
    }
}

@Composable
private fun rememberHeroMagnet(
    listState: androidx.compose.foundation.lazy.LazyListState,
    shelvesAt: () -> Float,
    downAt: () -> Float
): androidx.compose.foundation.gestures.FlingBehavior {
    val decay = androidx.compose.animation.rememberSplineBasedDecay<Float>()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val minFling = with(density) { HeroMagnetFling.toPx() }
    val topSlop = with(density) { 24.dp.toPx() }
    val currentShelvesAt by rememberUpdatedState(shelvesAt)
    val currentDownAt by rememberUpdatedState(downAt)
    return remember(listState, decay, minFling, topSlop, haptic) {
        HeroMagnet(listState, decay, { currentShelvesAt() }, { currentDownAt() }, topSlop, minFling) {
            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}

/** The service over its feed: its mark in an Expressive cookie, its name, and its own line. */
@Composable
private fun FeedHeader(service: HomeService, line: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = ShelfEdge, top = 8.dp, end = ShelfEdge)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(PolygonShape(androidx.compose.material3.MaterialShapes.Cookie9Sided))
                .background(PanelColors.accent),
            contentAlignment = Alignment.Center
        ) {
            service.icon?.let { icon ->
                Icon(imageVector = icon(), contentDescription = null, tint = PanelColors.onAccent, modifier = Modifier.size(28.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = service.title,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 27.sp, lineHeight = 33.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = line,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A shelf's name and, when there is more than it shows, the way to all of it: the name itself, and
 * "Все · 50" at the end of the line — one pill of glass with how many it holds, where the number
 * and the arrow used to stand apart.
 */
@Composable
private fun ShelfHeader(
    title: String,
    count: Int?,
    live: Boolean = false,
    onOpen: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = ShelfEdge, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (onOpen != null) Modifier.clickable(onClickLabel = tr("Весь раздел"), onClick = onOpen) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // On air now, as YouTube's broadcasts are: the red of the player's «В эфире».
            if (live) Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF3B30), CircleShape))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        action?.invoke()
        if (onOpen != null) ShelfAllButton(title = title, count = count, onClick = onOpen)
    }
}

/** "Все · 50": a shelf whole, a tap away, with how many it holds. */
@Composable
private fun ShelfAllButton(title: String, count: Int?, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(36.dp)
            .glassOr(CircleShape, PanelColors.container)
            .semantics { contentDescription = tr("Весь раздел «%s»", title) },
        shape = CircleShape,
        color = if (glass) Color.Transparent else PanelColors.container,
        contentColor = PanelColors.content
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = if (count != null && count > 0) tr("Все · %s", count) else tr("Все"),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PanelColors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** A shelf: its header, then its items as its kind lays them out, or what stands in for them. */
@Composable
private fun ShelfView(shelf: HomeShelf, onOpen: () -> Unit) {
    val metrics = LocalShelfMetrics.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = ShelfTop, bottom = ShelfBottom)) {
        ShelfHeader(
            title = shelf.title,
            count = shelf.count.takeIf { shelf.note == null },
            live = shelf.live,
            onOpen = if (shelf.openable) onOpen else null
        )
        when {
            shelf.note != null -> ShelfNoteView(shelf.note)
            shelf.items.isEmpty() -> ShelfLoading(shelf.kind)
            else -> when (shelf.kind) {
                ShelfKind.Stage -> Box(modifier = Modifier.padding(top = 8.dp)) { HomeHeroCarousel(items = shelf.items, cover = metrics.stage) }
                ShelfKind.Squares -> ShelfRow(shelf.items) { ShelfSquare(it) }
                ShelfKind.Circles -> ShelfRow(shelf.items) { ShelfCircle(it) }
                ShelfKind.Live -> ShelfRow(shelf.items) { ShelfLive(it) }
                ShelfKind.Tracks -> ShelfGrid(shelf.items, rows = 3, rowHeight = ShelfTrackHeight, gap = 2.dp, column = metrics.trackRow) { ShelfTrack(it, Modifier.width(metrics.trackRow)) }
                ShelfKind.Genres -> ShelfGrid(shelf.items, rows = 2, rowHeight = metrics.genreHeight, gap = ShelfGap) { ShelfGenre(it, Modifier.width(metrics.genreWidth)) }
            }
        }
    }
}

/** A shelf's items in a sideways row. What the row has no use for of a swipe stays with it. */
@Composable
private fun ShelfRow(items: List<HeroItem>, item: @Composable (HeroItem) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .nestedScroll(KeepSidewaysSwipe),
        contentPadding = PaddingValues(start = ShelfEdge, top = 8.dp, end = ShelfEdge, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ShelfGap)
    ) {
        items(items, key = { it.key }, contentType = { "item" }) { item(it) }
    }
}

/** A shelf's items in [rows] going sideways, column by column: songs three to a column, genres two. */
@Composable
private fun ShelfGrid(
    items: List<HeroItem>,
    rows: Int,
    rowHeight: Dp,
    gap: Dp,
    // Columns most of the screen wide (songs): each comes to rest at the start, the last one too —
    // left at the end of the row, a sliver of the one before stood cut off at the edge.
    column: Dp? = null,
    item: @Composable (HeroItem) -> Unit
) {
    val state = rememberLazyGridState()
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val end = if (column != null) (maxWidth - ShelfEdge - column).coerceAtLeast(ShelfEdge) else ShelfEdge
        androidx.compose.foundation.lazy.grid.LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight * rows + gap * (rows - 1) + 12.dp)
                .nestedScroll(KeepSidewaysSwipe),
            contentPadding = PaddingValues(start = ShelfEdge, top = 8.dp, end = end, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(ShelfGap),
            verticalArrangement = Arrangement.spacedBy(gap),
            flingBehavior = if (column != null) {
                androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
                    lazyGridState = state,
                    snapPosition = androidx.compose.foundation.gestures.snapping.SnapPosition.Start
                )
            } else {
                androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior()
            }
        ) {
            items(items.size, key = { items[it].key }, contentType = { "item" }) { index -> item(items[index]) }
        }
    }
}

/**
 * Pressed, a cover rounds off toward [pressedCorner] and gives a little, as Expressive's buttons do.
 * Read as it is drawn: a press moves the picture, it composes nothing.
 */
@Composable
private fun Modifier.pressMorph(interaction: MutableInteractionSource, corner: Dp, pressedCorner: Dp): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val shapeCorner = animateDpAsState(
        targetValue = if (pressed) pressedCorner else corner,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "pressCorner"
    )
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale"
    )
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        shape = RoundedCornerShape(shapeCorner.value)
        clip = true
    }
}

/** An item's cover at [size]: its picture asked for at that size, its folder's covers, or its mark. */
@Composable
private fun ShelfCover(item: HeroItem, size: Dp, modifier: Modifier = Modifier, badge: Boolean = true) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        val icon = item.icon
        when {
            item.collage.isNotEmpty() -> CoverCollage(urls = item.collage, cellSize = size / 2)
            !item.artworkUrl.isNullOrBlank() -> AsyncImage(
                model = artworkUrlForSize(item.artworkUrl, size) ?: item.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            icon != null -> IconCover(icon = icon, iconSize = size * 0.38f)
        }
        if (badge && (item.isPlaying || LocalNowPlaying.current.lights(item.source, item.trackId))) {
            NowPlayingBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .graphicsLayer {
                        scaleX = 0.8f
                        scaleY = 0.8f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                    }
            )
        }
        if (item.isLoading) AppContainedLoadingIndicator(modifier = Modifier.size(size * 0.4f))
    }
}

/** An item's name and the line under it, as every tile of a row has them. */
@Composable
private fun TileCaption(item: HeroItem, center: Boolean = false) {
    Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (center) TextAlign.Center else TextAlign.Start
        )
        if (!item.subtitle.isNullOrBlank()) {
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = if (center) TextAlign.Center else TextAlign.Start
            )
        }
    }
}

/** A collection: a square cover, its name in up to two lines, and a line under it. */
@Composable
private fun ShelfSquare(item: HeroItem, modifier: Modifier = Modifier, fill: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val size = LocalShelfMetrics.current.square
    Column(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier.width(size))
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                item.onClick()
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShelfCover(
            item = item,
            size = size,
            modifier = Modifier
                .then(if (fill) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(size))
                .pressMorph(interaction, corner = 24.dp, pressedCorner = size / 2)
        )
        TileCaption(item)
    }
}

/**
 * A station: rounder than a collection, its name under it in the middle. Not a circle: SoundCloud
 * draws a station's cover as a square with its name in a corner, and a circle cut it off. Pressed,
 * it does round off whole.
 */
@Composable
private fun ShelfCircle(item: HeroItem, modifier: Modifier = Modifier, fill: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val size = LocalShelfMetrics.current.circle
    Column(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier.width(size))
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                item.onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ShelfCover(
            item = item,
            size = size,
            modifier = Modifier
                .then(if (fill) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(size))
                .pressMorph(interaction, corner = size * 0.32f, pressedCorner = size / 2)
        )
        TileCaption(item.copy(subtitle = null), center = true)
    }
}

/** A broadcast: its frame, as wide as video is, marked on air, and what it is under it. */
@Composable
private fun ShelfLive(item: HeroItem, modifier: Modifier = Modifier, fill: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val width = LocalShelfMetrics.current.live
    Column(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier.width(width))
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                item.onClick()
            },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .pressMorph(interaction, corner = 24.dp, pressedCorner = 48.dp)
        ) {
            ShelfCover(item = item, size = width, modifier = Modifier.fillMaxSize())
            Text(
                text = tr("В эфире"),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color(0xFFFF3B30), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        TileCaption(item)
    }
}

/** A song: a small cover, its name and its artist, played at once when tapped; lit while it plays. */
@Composable
private fun ShelfTrack(item: HeroItem, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val playing = item.isPlaying || LocalNowPlaying.current.lights(item.source, item.trackId)
    Row(
        modifier = modifier
            .height(ShelfTrackHeight)
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                item.onClick()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ShelfCover(
            item = item,
            size = ShelfTrackCover,
            badge = false,
            modifier = Modifier
                .size(ShelfTrackCover)
                .pressMorph(interaction, corner = 14.dp, pressedCorner = ShelfTrackCover / 2)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (playing) PanelColors.accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.subtitle.isNullOrBlank()) {
                Text(text = item.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (playing) {
            NowPlayingBadge(
                modifier = Modifier.graphicsLayer {
                    scaleX = 0.8f
                    scaleY = 0.8f
                }
            )
        }
    }
}

/** A genre's chart: a tile with its name over its cover. */
@Composable
private fun ShelfGenre(item: HeroItem, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val metrics = LocalShelfMetrics.current
    Box(
        modifier = modifier
            .height(metrics.genreHeight)
            .pressMorph(interaction, corner = 24.dp, pressedCorner = 42.dp)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                item.onClick()
            }
    ) {
        ShelfCover(item = item, size = metrics.genreWidth, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Black.copy(alpha = 0.12f))))
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!item.subtitle.isNullOrBlank()) {
                Text(text = item.subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f), maxLines = 1)
            }
        }
    }
}

/** What stands in a shelf's place while it loads: still placeholders, nothing to redraw. */
@Composable
private fun ShelfLoading(kind: ShelfKind) {
    val metrics = LocalShelfMetrics.current
    if (kind == ShelfKind.Stage) {
        Box(
            modifier = Modifier.fillMaxWidth().height(metrics.stage + StageCaptionHeight + 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AppLoadingIndicator(modifier = Modifier.size(80.dp))
        }
        return
    }
    val placeholder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = ShelfEdge, top = 8.dp, end = ShelfEdge, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ShelfGap)
    ) {
        repeat(3) {
            Box(modifier = Modifier.size(metrics.square).background(placeholder, RoundedCornerShape(24.dp)))
        }
    }
}

/** A shelf's note: what is wrong, and the button that does something about it. */
@Composable
private fun ShelfNoteView(note: ShelfNote) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = ShelfEdge, top = 8.dp, end = ShelfEdge, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = note.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        note.action?.let { action ->
            Button(onClick = note.onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

/**
 * A shelf opened whole, over its feed: every item in it at once, as a grid of covers or a list of
 * songs. Like every page over home it has no back button: pulled down it closes, as do the system's
 * back and the service tapped again on the toolbar.
 */
@Composable
private fun ShelfPage(service: HomeService, shelf: HomeShelf, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val gridState = rememberLazyGridState()
    val collapsedPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val collapsed by remember(gridState) {
        derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > collapsedPx }
    }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    SizedShelves(
        modifier = Modifier
            .fillMaxSize()
            // A touch on the page stops there: none reaches the feed under it.
            .pointerInput(Unit) { }
            .pullToClose(onClose)
            .pageGlass(MaterialTheme.colorScheme.background)
    ) {
        // As many to a line as fit at about a shelf's size.
        val columns = when (shelf.kind) {
            ShelfKind.Tracks, ShelfKind.Live -> 1
            ShelfKind.Genres -> 2
            else -> ((maxWidth - ShelfEdge * 2) / (LocalShelfMetrics.current.square * 0.85f)).toInt().coerceIn(2, 6)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ShelfEdge, top = top + 16.dp, end = ShelfEdge, bottom = bottom + FeedBottomRoom),
            horizontalArrangement = Arrangement.spacedBy(ShelfGap),
            verticalArrangement = Arrangement.spacedBy(if (shelf.kind == ShelfKind.Tracks) 2.dp else 20.dp)
        ) {
            item(key = "head", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = "head") {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)) {
                    Text(
                        text = shelf.title,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 27.sp, lineHeight = 33.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = service.title + " · " + if (shelf.kind == ShelfKind.Tracks) {
                            plural(shelf.count, tr("трек"), tr("трека"), tr("треков"))
                        } else {
                            plural(shelf.count, tr("подборка"), tr("подборки"), tr("подборок"))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(shelf.items.size, key = { shelf.items[it].key }, contentType = { "item" }) { index ->
                val item = shelf.items[index]
                when (shelf.kind) {
                    ShelfKind.Tracks -> ShelfTrack(item, Modifier.fillMaxWidth())
                    ShelfKind.Circles -> ShelfCircle(item, fill = true)
                    ShelfKind.Live -> ShelfLive(item, fill = true)
                    ShelfKind.Genres -> ShelfGenre(item, Modifier.fillMaxWidth())
                    else -> ShelfSquare(item, fill = true)
                }
            }
        }
        CollapsingTopBar(title = shelf.title, collapsed = collapsed)
    }
}

// A sideways swipe on home moves to the next service once it is this long — or, a flick, this fast
// and at least this long.
private val SERVICE_SWIPE_DISTANCE = 40.dp
private val SERVICE_SWIPE_VELOCITY = 300.dp
private val SERVICE_SWIPE_MIN_FLICK = 12.dp

/**
 * Material 3 Expressive's floating toolbar: the services on the panel tone, each by its own mark,
 * the chosen one an accent pill, and search beside it as its own floating button. Marks only: the
 * title above the carousel already names what is shown.
 */
@Composable
private fun HomeToolbar(
    services: List<HomeService>,
    selected: HomeService,
    onSelect: (HomeService) -> Unit,
    onSearch: () -> Unit,
    menu: AccountMenuState,
    menuItems: List<AccountMenuItem>,
    modifier: Modifier = Modifier
) {
    // The rest of the bar steps back while the avatar's menu is open; touched, it shuts the menu.
    val rest by animateFloatAsState(if (menu.open) 0.4f else 1f, tween(220), label = "toolbarRest")
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val glass = LocalGlass.current
        Surface(
            modifier = Modifier
                .padding(end = 8.dp)
                .graphicsLayer { alpha = rest }
                .height(64.dp)
                .glassOr(CircleShape, PanelColors.container),
            shape = CircleShape,
            color = if (glass) Color.Transparent else PanelColors.container,
            contentColor = PanelColors.content,
            shadowElevation = if (glass) 0.dp else 6.dp
        ) {
            // Where each service's mark lies across the bar, for a finger sliding along it.
            val spans = remember { mutableMapOf<HomeService, ClosedFloatingPointRange<Float>>() }
            val currentSelected by rememberUpdatedState(selected)
            val currentOnSelect by rememberUpdatedState(onSelect)
            Row(
                modifier = Modifier
                    .padding(8.dp)
                    // Slid along, the bar picks whatever service is under the finger, as a
                    // segmented control does.
                    .pointerInput(services) {
                        var picked: HomeService? = null
                        fun pickAt(x: Float) {
                            val under = spans.entries.firstOrNull { x in it.value }?.key ?: return
                            if (under != picked) {
                                picked = under
                                if (under != currentSelected) currentOnSelect(under)
                            }
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { start ->
                                picked = currentSelected
                                pickAt(start.x)
                            },
                            onHorizontalDrag = { change, _ ->
                                pickAt(change.position.x)
                                change.consume()
                            }
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                services.forEach { service ->
                    HomeToolbarItem(
                        service = service,
                        selected = service == selected,
                        onClick = { onSelect(service) },
                        modifier = Modifier.onGloballyPositioned { coordinates ->
                            val left = coordinates.positionInParent().x
                            spans[service] = left..(left + coordinates.size.width)
                        }
                    )
                }
            }
        }
        val searchFill = androidx.compose.ui.graphics.lerp(PanelColors.container, PanelColors.content, 0.08f)
        Spacer(modifier = Modifier.weight(1f))
        Surface(
            onClick = onSearch,
            modifier = Modifier
                .graphicsLayer { alpha = rest }
                .size(64.dp)
                .glassOr(RoundedCornerShape(20.dp), searchFill),
            shape = RoundedCornerShape(20.dp),
            color = if (glass) Color.Transparent else searchFill,
            contentColor = PanelColors.accent,
            shadowElevation = if (glass) 0.dp else 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Search, contentDescription = tr("Поиск"), modifier = Modifier.size(28.dp))
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        // The avatar, at the edge: the friends, the profile and the settings, in its menu, which
        // stands at the edge over it (see AccountMenu).
        AccountMenuButton(state = menu, items = menuItems, fill = searchFill)
    }
}

@Composable
private fun HomeToolbarItem(
    service: HomeService,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
        modifier = modifier
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
            val icon = service.icon
            if (icon != null) {
                Icon(icon(), contentDescription = service.title, modifier = Modifier.size(24.dp))
            } else {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = service.letter.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.semantics { contentDescription = service.title }
                    )
                }
            }
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
    val glass = LocalGlass.current
    Surface(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .glassOr(RoundedCornerShape(16.dp), PanelColors.container),
        shape = RoundedCornerShape(16.dp),
        color = if (glass) Color.Transparent else PanelColors.container,
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
    downloadProgress: DownloadProgressOf,
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
                        progress = { downloadProgress[track.id] },
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

/** 45678 -> "45,7 тыс.", or "45.7K" in English. Raw six-digit follower counts are unreadable at a glance. */
private fun compactCount(value: Int): String {
    // Formatted against a fixed locale and then, in Russian, switched to a comma: the app's
    // language is its own whatever the device's, and "1.5 тыс." mixes conventions.
    val russian = !english()
    fun short(amount: Float, unit: String): String {
        val number = String.format(java.util.Locale.US, "%.1f", amount).removeSuffix(".0")
        return if (russian) number.replace('.', ',') + " " + unit else number + unit
    }
    return when {
        value >= 1_000_000 -> short(value / 1_000_000f, tr("млн"))
        value >= 10_000 -> tr("%s тыс.", value / 1000)
        value >= 1_000 -> short(value / 1000f, tr("тыс."))
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
                contentDescription = if (expanded) tr("Свернуть") else tr("Читать полностью"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { rotationZ = chevronTurn }
            )
        }
    }
}

/**
 * Russian needs three forms, and "1 треков" in the corner of the home screen looks broken. In
 * English, the words being translated already, [one] for one and [many] for the rest.
 */
private fun plural(count: Int, one: String, few: String, many: String): String {
    if (english()) return "$count ${if (count == 1) one else many}"
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
            label = tr("Скачанное"),
            caption = plural(downloadedCount, tr("трек"), tr("трека"), tr("треков")),
            onClick = onOpenDownloads,
            modifier = Modifier.weight(1f)
        )
        LibraryLaunchCard(
            icon = Icons.Default.LibraryMusic,
            label = tr("Плейлисты"),
            caption = plural(playlistCount, tr("плейлист"), tr("плейлиста"), tr("плейлистов")),
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
    // Several covers in one, as a folder of tracks shows (see [CoverCollage]); before [artworkUrl].
    val collage: List<String> = emptyList(),
    val icon: ImageVector? = null,
    // Lit as playing (see [NowPlaying.lights]): what the card stands for, a collection by where a
    // queue is started from, or a track by its id; [isPlaying] lights it whatever they say.
    val source: String? = null,
    val trackId: Long? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val onClick: () -> Unit
)

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
// Swallows what a sideways scroller leaves over of a swipe, so nothing sideways around it moves.
private val KeepSidewaysSwipe = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        Offset(available.x, 0f)

    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) =
        androidx.compose.ui.unit.Velocity(available.x, 0f)
}

// How fadeIn and fadeOut go by default, for a page faded on what it draws (see glassFade).
private val PageFadeSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow)

// How a page of services settles: soft, a touch of give, no wobble.
private val ServicePageSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 320f)

private const val HeroAsideWash = 0.45f
private const val HeroAsideShade = 0.18f

@Composable
private fun HomeHeroCarousel(
    items: List<HeroItem>,
    cover: Dp,
    // As a service's hero: the caption by the cover's left edge, [action] at its right — the way
    // to all of them under the thumb. On a shelf, the caption in the middle.
    hero: Boolean = false,
    action: (@Composable () -> Unit)? = null
) {
    if (items.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState { items.size }
    val density = LocalDensity.current
    val coverPx = with(density) { cover.toPx() }
    val stripPx = with(density) { HeroStrip.toPx() }
    val radiusPx = with(density) { 30.dp.toPx() }

    Column(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(cover)) {
            val sidePadding = ((maxWidth - cover) / 2).coerceAtLeast(0.dp)
            HorizontalPager(
                state = pagerState,
                pageSize = androidx.compose.foundation.pager.PageSize.Fixed(cover),
                contentPadding = PaddingValues(horizontal = sidePadding),
                pageSpacing = HeroGap,
                // The count and the list can change apart (a row refreshed): a page past the end
                // has no cover's key to give, only its place.
                key = { items.getOrNull(it)?.key ?: it },
                modifier = Modifier
                    .fillMaxSize()
                    // What the carousel doesn't use of a swipe, at its first or last cover, isn't
                    // passed on to the pages of services around it.
                    .nestedScroll(KeepSidewaysSwipe)
            ) { page ->
                val item = items.getOrNull(page) ?: return@HorizontalPager
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
                            // Re-centres the picture inside whatever the mask leaves visible. No blur
                            // on the covers aside any more: in a list that scrolls, a blurred layer
                            // was worked out again on every frame of the scroll, for two slivers.
                            .graphicsLayer { translationX = -kotlin.math.sign(distance()) * (coverPx - visibleWidth()) / 2f }
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.collage.isNotEmpty()) {
                            CoverCollage(urls = item.collage, cellSize = cover / 2)
                        } else if (!item.artworkUrl.isNullOrBlank()) {
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
                                IconCover(icon = item.icon, iconSize = cover * 0.36f)
                            }
                        }
                        if (item.isPlaying || LocalNowPlaying.current.lights(item.source, item.trackId)) {
                            NowPlayingBadge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(14.dp),
                                // Moving on the card in front only: aside it is a sliver.
                                running = page == pagerState.currentPage
                            )
                        }
                        if (item.isLoading) {
                            AppContainedLoadingIndicator(modifier = Modifier.size(72.dp))
                        }
                    }
                    // The theme's colour over the covers aside, and a little shade.
                    val wash = PanelColors.container
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val aside = distance().absoluteValue.coerceIn(0f, 1f)
                                if (aside > 0f) {
                                    drawRect(wash.copy(alpha = HeroAsideWash * aside))
                                    drawRect(Color.Black.copy(alpha = HeroAsideShade * aside))
                                }
                            }
                    )
                }
            }
        }

        val current = items.getOrNull(pagerState.currentPage) ?: items.first()
        if (hero) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(cover)
                    .height(HeroCaptionHeight)
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AnimatedContent(
                    targetState = current,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    contentKey = { it.key },
                    label = "heroCaption",
                    modifier = Modifier.weight(1f)
                ) { item ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!item.subtitle.isNullOrBlank()) {
                            Text(
                                text = item.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                action?.invoke()
            }
            return@Column
        }
        AnimatedContent(
            targetState = current,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
            contentKey = { it.key },
            label = "heroCaption",
            modifier = Modifier.fillMaxWidth()
        ) { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StageCaptionHeight)
                    .padding(start = 28.dp, top = 12.dp, end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
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

/** Width a neighbouring cover narrows to, and the gap between covers. */
private val HeroStrip = 36.dp
private val HeroGap = 8.dp
// Sideways, the room above the covers and below their caption.
private val HeroLandscapeMargin = 24.dp

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

/**
 * What plays, for the cards of the carousels to light the one it came from: whether it plays,
 * where its queue was started ([MusicViewModel.playingFrom]) and the track.
 */
@androidx.compose.runtime.Immutable
internal data class NowPlaying(val isPlaying: Boolean, val from: String?, val trackId: Long?) {
    /** Whether a card standing for [source] (a collection) or [trackId] (a track) is what plays. */
    fun lights(source: String?, trackId: Long?): Boolean =
        isPlaying && ((source != null && source == from) || (trackId != null && trackId == this.trackId))
}

/**
 * Read only by the cards that show it: a track changing, or playing stopping, composes those
 * again, not the screens around them.
 */
internal val LocalNowPlaying = androidx.compose.runtime.compositionLocalOf { NowPlaying(false, null, null) }

/**
 * Animated equaliser bars — the playing cue that replaced morphing the cover itself. [running]
 * false holds them still: on a card off to the side, where a moving badge redrew the screen
 * sixty times a second for a sliver of it.
 */
@Composable
internal fun NowPlayingBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    running: Boolean = true,
    glass: (@Composable BoxScope.() -> Unit)? = null
) {
    val clock = rememberLoopClock(running = running)
    val barCount = 4

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
                repeat(barCount) { index ->
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .animatedHeight(16.dp) { loopValue(clock.longValue, 420 + index * 130, 0.3f, 1f) }
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
            .statusBarsPadding()
            .readableSideways(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopBar(title = tr("Плейлисты"), onBack = onBack)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Мои плейлисты"),
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalIconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showCreatePlaylistDialog = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = tr("Создать плейлист"))
                }
            }
        }

        if (playlists.isEmpty() && (!hasYandexToken || yandexPlaylists.isEmpty())) {
            item {
                EmptyState(tr("Создайте свой первый плейлист, нажав кнопку выше."))
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
                        text = tr("Плейлисты Яндекс Музыки"),
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
            title = { Text(tr("Создать плейлист")) },
            text = {
                OutlinedTextField(
                    value = playlistNameInput,
                    onValueChange = { playlistNameInput = it },
                    placeholder = { Text(tr("Название плейлиста")) },
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
                    Text(tr("Создать"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text(tr("Отмена"))
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
    updates: com.example.myapplication.data.UpdateRepository,
    onOpenTogether: () -> Unit = {},
    onOpenStats: () -> Unit = {}
) {
    val yandexToken by settingsRepository.yandexToken.collectAsState()
    val hasYandexToken = yandexToken.isNotEmpty()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .readableSideways(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            TopBar(title = tr("Настройки"), onBack = onBack)
        }

        // Listening together: in settings too, for a guest with nothing playing yet.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ВМЕСТЕ"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer))
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.Groups,
                        title = tr("Слушать вместе"),
                        subtitle = tr("С друзьями рядом: одна музыка на нескольких телефонах, напрямую, без интернета между ними"),
                        onClick = onOpenTogether
                    )
                }
            }
        }

        // What has been heard: minutes, favourite artists and tracks, and a card of it to share.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ИТОГИ"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer))
                ) {
                    SettingsActionRow(
                        icon = Icons.Rounded.BarChart,
                        title = tr("Итоги прослушиваний"),
                        subtitle = tr("Минуты, любимые артисты и треки за неделю, месяц, год — и карточка для сторис"),
                        onClick = onOpenStats
                    )
                }
            }
        }

        // Section 0: Look
        item {
            val backgroundMotion by settingsRepository.backgroundMotion.collectAsState()
            val playerCoverColors by settingsRepository.playerCoverColors.collectAsState()
            val openPlayerOnTap by settingsRepository.openPlayerOnTap.collectAsState()
            val smoothMotion by settingsRepository.smoothMotion.collectAsState()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ОФОРМЛЕНИЕ"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer)
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val themeMode by settingsRepository.themeMode.collectAsState()
                        val themeModes = listOf(Appearance.SYSTEM, Appearance.LIGHT, Appearance.DARK)
                        SettingsChoiceRow(
                            icon = Icons.Rounded.Contrast,
                            title = tr("Тема"),
                            subtitle = tr("Светлая, тёмная или как в системе"),
                            items = listOf(tr("Системная"), tr("Светлая"), tr("Тёмная")),
                            selectedIndex = themeModes.indexOf(themeMode).coerceAtLeast(0),
                            onSelect = { index ->
                                val mode = themeModes[index]
                                if (mode != themeMode) {
                                    settingsRepository.setThemeMode(mode)
                                    Appearance.applyTheme(context, mode)
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        // Android's to keep: read from it, as the app's own language screen may change it too.
                        var language by remember { mutableStateOf(Appearance.language(context)) }
                        val languages = listOf(Appearance.SYSTEM, Appearance.RUSSIAN, Appearance.ENGLISH)
                        SettingsChoiceRow(
                            icon = Icons.Rounded.Translate,
                            title = tr("Язык"),
                            subtitle = tr("Язык приложения: как в системе, русский или английский"),
                            items = listOf(tr("Системный"), tr("Русский"), "English"),
                            selectedIndex = languages.indexOf(language).coerceAtLeast(0),
                            onSelect = { index ->
                                val chosen = languages[index]
                                if (chosen != language) {
                                    language = chosen
                                    Appearance.setLanguage(context, chosen)
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.ScreenRotation,
                            title = tr("Живой фон"),
                            subtitle = tr("Объёмные фигуры на фоне наклоняются и трясутся вместе с телефоном (акселерометр)"),
                            checked = backgroundMotion,
                            onCheckedChange = settingsRepository::setBackgroundMotion
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.Animation,
                            title = tr("Плавность 120 Гц"),
                            subtitle = tr("Фон, «Моя форма» и индикаторы двигаются с частотой экрана. Выключите — 60 кадров в секунду, экономнее для батареи"),
                            checked = smoothMotion,
                            onCheckedChange = settingsRepository::setSmoothMotion
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.Palette,
                            title = tr("Цвета плеера из обложки"),
                            subtitle = tr("Только плеер перекрашивается в оттенок обложки трека, остальное — по обоям"),
                            checked = playerCoverColors,
                            onCheckedChange = settingsRepository::setPlayerCoverColors
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.PlayArrow,
                            title = tr("Открывать плеер по нажатию"),
                            subtitle = tr("Нажатый в списке трек сразу открывает плеер. Выключите — трек заиграет в мини-плеере, а плеер откроется, если нажать на него"),
                            checked = openPlayerOnTap,
                            onCheckedChange = settingsRepository::setOpenPlayerOnTap
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsActionRow(
                            icon = Icons.Rounded.TouchApp,
                            title = tr("Обучение жестам"),
                            subtitle = tr("Мини-плеер, плеер, окна и «Моя форма» — показать ещё раз"),
                            onClick = { settingsRepository.setOnboardingDone(false) }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsActionRow(
                            icon = Icons.Rounded.ViewCarousel,
                            title = tr("Что на главной"),
                            subtitle = tr("Что каждый сервис открывает первым, в большой карусели"),
                            onClick = { settingsRepository.homeChoicesOpen.value = true }
                        )
                    }
                }
            }
        }

        // Section 0.25: Videos
        item {
            val playerVideos by settingsRepository.playerVideos.collectAsState()
            val videoYandex by settingsRepository.videoYandex.collectAsState()
            val videoGlow by settingsRepository.videoGlow.collectAsState()
            val videoGlowStyle by settingsRepository.videoGlowStyle.collectAsState()
            val coverGlowStyle by settingsRepository.coverGlowStyle.collectAsState()
            val videoDownload by settingsRepository.videoDownload.collectAsState()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ВИДЕО"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer)
                    )
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val divider: @Composable () -> Unit = {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )
                        }
                        SettingsSwitchRow(
                            icon = Icons.Default.SmartDisplay,
                            title = tr("Клипы в плеере"),
                            subtitle = tr("Видео трека вместо обложки, вертикальное — на весь плеер. Выключите, и в плеере останутся только обложки"),
                            checked = playerVideos,
                            onCheckedChange = settingsRepository::setPlayerVideos
                        )
                        divider()
                        SettingsSwitchRow(
                            icon = Icons.Default.Videocam,
                            title = tr("Видео Яндекс Музыки"),
                            subtitle = tr("Вертикальные видеошоты на весь плеер и короткие отрывки клипов"),
                            checked = videoYandex,
                            onCheckedChange = settingsRepository::setVideoYandex,
                            enabled = playerVideos
                        )
                        divider()
                        SettingsSwitchRow(
                            icon = Icons.Default.AutoAwesome,
                            title = tr("Подсветка"),
                            subtitle = tr("Свет клипа или обложки заполняет фон плеера и поля при повороте. Без неё — меньше нагрузка"),
                            checked = videoGlow,
                            onCheckedChange = settingsRepository::setVideoGlow
                        )
                        GlowStyleRow(
                            title = tr("Клип"),
                            selected = videoGlowStyle,
                            onSelect = settingsRepository::setVideoGlowStyle,
                            enabled = videoGlow && playerVideos
                        )
                        GlowStyleRow(
                            title = tr("Обложка"),
                            selected = coverGlowStyle,
                            onSelect = settingsRepository::setCoverGlowStyle,
                            enabled = videoGlow
                        )
                        Text(
                            text = tr("Ambilight — свет от краёв расходится по экрану, как у расширения для YouTube, и легче для телефона. Копии — прежние ступени увеличенных размытых копий"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 74.dp, end = 18.dp, bottom = 12.dp)
                                .graphicsLayer { alpha = if (videoGlow) 1f else 0.38f }
                        )
                        divider()
                        SettingsSwitchRow(
                            icon = Icons.Default.Download,
                            title = tr("Скачивать клипы"),
                            subtitle = tr("Вместе с треками в медиатеке, по Wi-Fi, чтобы играли без сети. Клип YouTube — десятки МБ"),
                            checked = videoDownload,
                            onCheckedChange = settingsRepository::setVideoDownload,
                            enabled = playerVideos
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
                    text = tr("ОБНОВЛЕНИЯ"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer)
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
                            title = tr("Проверять автоматически"),
                            subtitle = tr("Спрашивать GitHub о новой версии раз в 12 часов"),
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
                    text = tr("СИНХРОНИЗАЦИЯ"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer)
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
                                    text = tr("Автоскачивание лайкнутых треков SoundCloud"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (soundcloudLikesSyncStatus.state != SyncState.IDLE) {
                            val statusText = when (soundcloudLikesSyncStatus.state) {
                                SyncState.FETCHING_LIKES -> tr("Получение лайкнутых треков...")
                                SyncState.DOWNLOADING -> tr("Скачивание треков: %s из %s", soundcloudLikesSyncStatus.currentTrackIndex, soundcloudLikesSyncStatus.totalTracks)
                                SyncState.COMPLETED -> tr("Синхронизация завершена!")
                                SyncState.FAILED -> tr("Ошибка: %s", soundcloudLikesSyncStatus.errorMessage)
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
                                    text = tr("Скачивается: %s", soundcloudLikesSyncStatus.currentTrackTitle),
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
                                    text = tr("Успешно: %s | Ошибки: %s", soundcloudLikesSyncStatus.downloadedCount, soundcloudLikesSyncStatus.failedCount),
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
                                    Text(tr("Остановить"))
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
                                        text = if (soundcloudLikesSyncStatus.state == SyncState.COMPLETED || soundcloudLikesSyncStatus.state == SyncState.FAILED) tr("Синхронизировать заново") else tr("Синхронизировать лайки")
                                    )
                                }

                                if (soundcloudLikesSyncStatus.state == SyncState.COMPLETED || soundcloudLikesSyncStatus.state == SyncState.FAILED) {
                                    FilledTonalButton(
                                        onClick = resetSoundCloudLikesSyncStatus,
                                        modifier = Modifier.weight(1f),
                                        shape = MaterialTheme.shapes.large
                                    ) {
                                        Text(tr("Сбросить"))
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
                                    text = tr("Лайки на SoundCloud"),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = tr("Лайкнуть на SoundCloud скачанные треки, чьи лайки туда не дошли"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        val pushBusy = likesPushStatus.state == LikesPushState.CHECKING ||
                            likesPushStatus.state == LikesPushState.SENDING
                        if (likesPushStatus.state != LikesPushState.IDLE) {
                            val statusText = when (likesPushStatus.state) {
                                LikesPushState.CHECKING -> tr("Сверяю с лайками SoundCloud...")
                                LikesPushState.SENDING -> tr("Отправлено %s из %s", likesPushStatus.sent, likesPushStatus.total)
                                LikesPushState.PAUSED -> tr("Отправлено %s из %s, пауза", likesPushStatus.sent, likesPushStatus.total)
                                LikesPushState.COMPLETED -> likesPushStatus.message
                                    ?: tr("Готово: отправлено %s из %s", likesPushStatus.sent, likesPushStatus.total)
                                LikesPushState.FAILED -> tr("Ошибка: %s", likesPushStatus.message)
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
                                        LikesPushState.PAUSED -> tr("Продолжить")
                                        LikesPushState.COMPLETED, LikesPushState.FAILED -> tr("Проверить снова")
                                        else -> tr("Отправить лайки")
                                    }
                                )
                            }
                            if (likesPushStatus.state == LikesPushState.COMPLETED || likesPushStatus.state == LikesPushState.FAILED) {
                                FilledTonalButton(
                                    onClick = resetLikesPushStatus,
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text(tr("Сбросить"))
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
                                        text = tr("Яндекс.Музыка"),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = tr("Автоскачивание треков из плейлиста 'Мне нравится'"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (yandexLikesSyncStatus.state != SyncState.IDLE) {
                                val statusText = when (yandexLikesSyncStatus.state) {
                                    SyncState.FETCHING_LIKES -> tr("Получение лайкнутых треков...")
                                    SyncState.DOWNLOADING -> tr("Скачивание треков: %s из %s", yandexLikesSyncStatus.currentTrackIndex, yandexLikesSyncStatus.totalTracks)
                                    SyncState.COMPLETED -> tr("Синхронизация завершена!")
                                    SyncState.FAILED -> tr("Ошибка: %s", yandexLikesSyncStatus.errorMessage)
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
                                        text = tr("Скачивается: %s", yandexLikesSyncStatus.currentTrackTitle),
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
                                        text = tr("Успешно: %s | Ошибки: %s", yandexLikesSyncStatus.downloadedCount, yandexLikesSyncStatus.failedCount),
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
                                        Text(tr("Остановить"))
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
                                            text = if (yandexLikesSyncStatus.state == SyncState.COMPLETED || yandexLikesSyncStatus.state == SyncState.FAILED) tr("Синхронизировать заново") else tr("Синхронизировать лайки")
                                        )
                                    }

                                    if (yandexLikesSyncStatus.state == SyncState.COMPLETED || yandexLikesSyncStatus.state == SyncState.FAILED) {
                                        FilledTonalButton(
                                            onClick = resetYandexLikesSyncStatus,
                                            modifier = Modifier.weight(1f),
                                            shape = MaterialTheme.shapes.large
                                        ) {
                                            Text(tr("Сбросить"))
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
                    text = tr("АККАУНТЫ"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer)
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
                                    text = tr("Вы вошли в аккаунт"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = onRelogin,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(tr("Перезайти"))
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
                                    text = tr("Яндекс.Музыка"),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (hasYandexToken) tr("Подключен") else tr("Не подключен"),
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
                                    Text(tr("Выйти"))
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
                                    Text(tr("Войти"))
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
                                    text = ytMusicAccount ?: tr("Не подключен"),
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
                                    Text(tr("Выйти"))
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
                                    Text(tr("Войти"))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Everything that changes how YouTube is used, together: it was spread over the video, the
        // accounts and the debugging sections. Signing in stays with the other accounts.
        item {
            val playerVideos by settingsRepository.playerVideos.collectAsState()
            val videoYouTube by settingsRepository.videoYouTube.collectAsState()
            val ytArtistShowAll by settingsRepository.ytArtistShowAll.collectAsState()
            val ytWebSearch by settingsRepository.ytWebSearch.collectAsState()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "YOUTUBE",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer))
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        val divider: @Composable () -> Unit = {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                modifier = Modifier.padding(horizontal = 18.dp)
                            )
                        }
                        SettingsSwitchRow(
                            icon = Icons.Default.Search,
                            title = tr("Обычный поиск YouTube"),
                            subtitle = tr("Вкладка YouTube ищет по youtube.com, а не по YouTube Music: все видео и трансляции, с их каналами"),
                            checked = ytWebSearch,
                            onCheckedChange = settingsRepository::setYtWebSearch
                        )
                        divider()
                        SettingsSwitchRow(
                            icon = Icons.Default.SmartDisplay,
                            title = tr("Вся страница автора"),
                            subtitle = tr("Все ряды: видео, синглы, плейлисты, похожие исполнители. Выключено — треки, альбомы и трансляции"),
                            checked = ytArtistShowAll,
                            onCheckedChange = settingsRepository::setYtArtistShowAll
                        )
                        divider()
                        SettingsSwitchRow(
                            icon = Icons.Default.OndemandVideo,
                            title = tr("Клипы с YouTube"),
                            subtitle = if (playerVideos && ytMusicAccount == null) {
                                // Signed out, YouTube hands yt-dlp no video: nothing would ever show.
                                tr("Нужен вход в YouTube Music в «Аккаунтах»: без него YouTube не отдаёт клипы")
                            } else if (playerVideos) {
                                tr("Клипы треков YouTube Music, а для треков Яндекса — найденные на YouTube. Тратит трафик")
                            } else {
                                tr("Включаются вместе с «Клипами в плеере» в разделе «Видео»")
                            },
                            checked = videoYouTube,
                            onCheckedChange = settingsRepository::setVideoYouTube,
                            enabled = playerVideos
                        )
                    }
                }
            }
        }

        // The services' links: opened here when shared from their apps, and when Android is
        // allowed to send them here.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ССЫЛКИ"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
                LinksSettingsCard()
            }
        }

        // Section 3: Data & Cache
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ХРАНИЛИЩЕ И КЭШ"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer)
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
                                text = tr("Очистка кэша"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = tr("Сброс кэша миксов и станций"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                onClearCache()
                                Toast.makeText(context, tr("Кэш очищен"), Toast.LENGTH_SHORT).show()
                            },
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(tr("Очистить"))
                        }
                    }
                }
            }
        }

        // Section 4: Sound / Equalizer
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ЗВУК"),
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
                val fxButton by settingsRepository.playerFxButton.collectAsState()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer))
                ) {
                    val autoContinue by settingsRepository.autoContinue.collectAsState()
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        SettingsSwitchRow(
                            icon = Icons.Rounded.Tune,
                            title = tr("Эффекты в плеере"),
                            subtitle = tr("Кнопка реверба, замедления и ускорения — для каждого трека свои"),
                            checked = fxButton,
                            onCheckedChange = settingsRepository::setPlayerFxButton
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                        SettingsSwitchRow(
                            icon = Icons.Default.Radio,
                            title = tr("Автопродолжение"),
                            subtitle = tr("Альбом, плейлист или поиск закончились — дальше играет радио от последнего трека"),
                            checked = autoContinue,
                            onCheckedChange = settingsRepository::setAutoContinue
                        )
                    }
                }
            }
        }

        // Section 5: Debug & App
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr("ОТЛАДКА"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )

                val showDebugPercentageVal by settingsRepository.showDebugPercentage.collectAsState()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer)
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
                                text = tr("Дебаг информация"),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = tr("Процент скачивания на экране загрузок"),
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
                // For when something doesn't work on a friend's phone: what the app saw, sent on.
                val reportContext = LocalContext.current
                val reportScope = rememberCoroutineScope()
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer))
                ) {
                    SettingsActionRow(
                        icon = Icons.Default.BugReport,
                        title = tr("Отправить журнал"),
                        subtitle = tr("Если что-то не работает: журнал приложения и сведения о телефоне файлом — тому, кто разберётся. Ссылки, ключи и имена аккаунтов из него убраны"),
                        onClick = {
                            reportScope.launch { com.example.myapplication.data.DiagnosticsReport.share(reportContext, settingsRepository) }
                        }
                    )
                }
            }
        }
    }
}

/** Which [com.example.myapplication.data.GlowStyle] the glow of [title] has: a small two-way pill, under the glow's switch. */
@Composable
private fun GlowStyleRow(
    title: String,
    selected: com.example.myapplication.data.GlowStyle,
    onSelect: (com.example.myapplication.data.GlowStyle) -> Unit,
    enabled: Boolean
) {
    val styles = com.example.myapplication.data.GlowStyle.entries
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .padding(start = 74.dp, end = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(72.dp))
        SegmentedControl(
            items = styles.map { if (it == com.example.myapplication.data.GlowStyle.Ambilight) "Ambilight" else tr("Копии") },
            selectedIndex = styles.indexOf(selected),
            onSelectedIndexChanged = { if (enabled) onSelect(styles[it]) },
            modifier = Modifier.weight(1f),
            height = 40.dp,
            textStyle = MaterialTheme.typography.labelLarge
        )
    }
}

/** A settings row with a tinted icon puck, a title, a quiet caption and a switch. */
/** A settings row that does something rather than switching something. */
/**
 * Whether the services' links open in YouCloud. Shared from their apps ("Поделиться" →
 * YouCloud) they always do. Tapped anywhere else they do once Android is allowed to send them
 * here: the domains aren't ours to verify, so it is the listener who adds them, on the app's
 * "Open by default" screen, which this opens. How many are added is read again on coming back.
 */
@Composable
private fun LinksSettingsCard() {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var counted by remember { mutableStateOf(linkHostsAllowed(context)) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) counted = linkHostsAllowed(context)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val (allowed, total) = counted
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = glassFill(MaterialTheme.colorScheme.surfaceContainer))
    ) {
        SettingsActionRow(
            icon = Icons.Default.Link,
            title = tr("Открывать ссылки сервисов"),
            subtitle = when {
                total == 0 -> tr("Ссылки SoundCloud, Яндекс Музыки и YouTube открываются в YouCloud")
                allowed == 0 -> tr("Сейчас ссылки открываются в браузере. Нажмите и добавьте адреса — или делитесь ссылкой из приложения сервиса в YouCloud")
                allowed < total -> tr("Добавлено адресов: %s из %s. Нажмите, чтобы добавить остальные", allowed, total)
                else -> tr("Все ссылки SoundCloud, Яндекс Музыки и YouTube открываются в YouCloud")
            },
            onClick = {
                val intent = Intent(
                    android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                    android.net.Uri.parse("package:${context.packageName}")
                )
                runCatching { context.startActivity(intent) }
                    .onFailure {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                        )
                    }
            }
        )
    }
}

/** How many of the link addresses the app asks for Android sends here, of how many. */
private fun linkHostsAllowed(context: Context): Pair<Int, Int> {
    val manager = context.getSystemService(android.content.pm.verify.domain.DomainVerificationManager::class.java)
        ?: return 0 to 0
    val hosts = runCatching { manager.getDomainVerificationUserState(context.packageName)?.hostToStateMap }
        .getOrNull().orEmpty()
    val allowed = hosts.count { it.value != android.content.pm.verify.domain.DomainVerificationUserState.DOMAIN_STATE_NONE }
    return allowed to hosts.size
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A settings row choosing one of a few: its title over the choices, side by side. */
@Composable
private fun SettingsChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsIcon(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        SegmentedControl(
            items = items,
            selectedIndex = selectedIndex,
            onSelectedIndexChanged = onSelect,
            modifier = Modifier.padding(start = 56.dp),
            height = 44.dp,
            textStyle = MaterialTheme.typography.labelLarge
        )
    }
}

/** A settings row's glyph, on the tinted disc every row of the settings has. */
@Composable
private fun SettingsIcon(icon: ImageVector) {
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
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SettingsIcon(icon)
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
    // "Поиск"; or, picking for the profile's showcase, what is being picked.
    title: String,
    query: String,
    tracks: List<SoundCloudTrack>,
    favorites: List<FavoriteTrack>,
    currentTrackId: Long?,
    downloadProgress: DownloadProgressOf = NoDownloadProgress,
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
    // Hoisted, one per service, so that coming back from an album lands where the list was left.
    val listStates = remember { SearchSource.entries.associateWith { androidx.compose.foundation.lazy.LazyListState() } }
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
            onFavoriteClick = onFavoriteClick,
            onArtistClick = onOpenArtist
        )
        return
    }

    // A service signed out of meanwhile hands search back to SoundCloud.
    val activeSource = if (source in sources) source else SearchSource.SOUNDCLOUD
    LaunchedEffect(activeSource, source) {
        if (activeSource != source) onSearchSourceChanged(activeSource)
    }
    val ytPage = ytSearch.page
    fun resultsOf(of: SearchSource) = when (of) {
        SearchSource.SOUNDCLOUD -> SearchResults(query, tracks, isLoading, errorMessage, albums, playlists, artists)
        SearchSource.YANDEX -> SearchResults(
            yandexQuery, yandexTracks, yandexLoading, yandexError, yandexAlbums, yandexPlaylists, yandexArtists
        )
        SearchSource.YOUTUBE -> SearchResults(
            ytSearch.query,
            ytPage?.tracks.orEmpty(),
            ytSearch.loading,
            ytSearch.error,
            ytPage?.albums.orEmpty(),
            ytPage?.playlists.orEmpty(),
            ytPage?.artists.orEmpty()
        )
    }
    fun search(on: SearchSource, text: String) = when (on) {
        SearchSource.SOUNDCLOUD -> onQueryChange(text)
        SearchSource.YANDEX -> onYandexQueryChange(text)
        SearchSource.YOUTUBE -> onYtQueryChange(text)
    }
    val activeQuery = resultsOf(activeSource).query
    val favoritesMap = remember(favorites) { favorites.associateBy { it.id } }

    // The services side by side, a page each; the one come to rest is the one searched.
    val sourcePager = rememberPagerState(initialPage = sources.indexOf(activeSource).coerceAtLeast(0)) { sources.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sourcePager.settledPage, sources) {
        val settled = sources.getOrNull(sourcePager.settledPage) ?: return@LaunchedEffect
        if (settled != activeSource) onSearchSourceChanged(settled)
    }
    LaunchedEffect(activeSource, sources) {
        val index = sources.indexOf(activeSource)
        if (index >= 0 && index != sourcePager.currentPage && !sourcePager.isScrollInProgress) {
            sourcePager.scrollToPage(index)
        }
    }
    // The neighbours search what was typed too, once typing pauses, so a swipe finds their
    // results already there.
    LaunchedEffect(activeQuery, sources) {
        kotlinx.coroutines.delay(700)
        sources.filter { it != activeSource && resultsOf(it).query != activeQuery }.forEach { search(it, activeQuery) }
    }

    val backdrop = LocalFrostSources.current
    val results = rememberFrostSource()
    val tabsShown = sources.size > 1
    val playerShown = currentTrackId != null
    // Sideways the results have the left to themselves; the bar, the field, the services and the
    // mini player are in a pane on the right, under the thumb.
    val landscape = isLandscape()
    // Room at the bottom of each list for what floats over its end: the tabs, the mini player.
    val bottomRoom = if (landscape) {
        24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        dockedPageRoom(tabs = tabsShown, player = playerShown)
    }

    // Search stands on the moving backdrop: its fields, rows and tabs are glass.
    androidx.compose.runtime.CompositionLocalProvider(LocalGlass provides true) {
    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .statusBarsPadding()
                .then(if (landscape) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)) else Modifier)
        ) {
            // top = 0: the bar has to start at the same y as the home bar, or entering search
            // shifts the title and back button downward and the transition reads as a jump.
            if (!landscape) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    TopBar(title = title, onBack = onBack)
                }
            }
            HorizontalPager(
                state = sourcePager,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    // Above the keyboard: without it the last result sits under it with no way
                    // to scroll to it.
                    .imePadding()
                    // The results, recorded for the tabs floating over their end.
                    .frostSource(results),
                key = { sources[it] },
                beyondViewportPageCount = 1,
                flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                    state = sourcePager,
                    snapPositionalThreshold = 0.3f,
                    snapAnimationSpec = ServicePageSpring
                )
            ) { page ->
                val pageSource = sources[page]
                SearchResultsList(
                    source = pageSource,
                    results = resultsOf(pageSource),
                    listState = listStates.getValue(pageSource),
                    favoritesMap = favoritesMap,
                    currentTrackId = currentTrackId,
                    downloadProgress = downloadProgress,
                    isPlaying = isPlaying,
                    hasMore = hasMore && pageSource == activeSource,
                    isLoadingMore = isLoadingMore && pageSource == activeSource,
                    bottomRoom = bottomRoom,
                    onPlayTrack = onPlayTrack,
                    onFavoriteClick = onFavoriteClick,
                    onOpenArtist = onOpenArtist,
                    onOpenPlaylist = onOpenPlaylist,
                    onLoadMore = onLoadMore,
                    modifier = Modifier.graphicsLayer {
                        // The page going sinks back a little, the one coming rises from it.
                        val away = ((sourcePager.currentPage - page) + sourcePager.currentPageOffsetFraction)
                            .absoluteValue.coerceIn(0f, 1f)
                        val scale = 1f - 0.05f * away
                        scaleX = scale
                        scaleY = scale
                        // Off to the side, not drawn: see the pages of home.
                        alpha = if (away >= 1f) 0f else 1f - 0.4f * away
                    }
                )
            }
        }

        if (landscape) {
            Box(
                modifier = Modifier
                    .width(LandscapePaneWidth)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                    .padding(horizontal = 16.dp)
            ) {
                TopBar(title = title, onBack = onBack)
            }
        }
        }

        // The field and, when there is a choice, the services, down where the thumb is: on the
        // keyboard's top edge while it is up, at the bottom of the screen when it isn't; sideways,
        // at the foot of the pane on the right.
        androidx.compose.runtime.CompositionLocalProvider(LocalFrostSources provides backdrop + results) {
            Column(
                modifier = Modifier
                    .align(if (landscape) Alignment.BottomEnd else Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                    .then(
                        if (landscape) {
                            Modifier
                                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                                .width(LandscapePaneWidth)
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(SearchDockGap)
            ) {
                SearchField(
                    query = activeQuery,
                    onQueryChange = { newQuery -> search(activeSource, newQuery) },
                    focusRequester = focusRequester
                )
                if (tabsShown) SearchSourceTabs(
                    labels = sources.map { option ->
                        when (option) {
                            SearchSource.SOUNDCLOUD -> "SoundCloud"
                            SearchSource.YANDEX -> if (sources.size > 2) tr("Яндекс") else tr("Яндекс Музыка")
                            SearchSource.YOUTUBE -> if (sources.size > 2) "YouTube" else "YouTube Music"
                        }
                    },
                    pager = sourcePager,
                    onSelect = { index ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { sourcePager.animateScrollToPage(index, animationSpec = ServicePageSpring) }
                    }
                )
            }
        }
    }
    }
}

/** One service's results, as a search page shows them. */
private class SearchResults(
    val query: String,
    val tracks: List<SoundCloudTrack>,
    val loading: Boolean,
    val error: String?,
    val albums: List<SoundCloudPlaylist>,
    val playlists: List<SoundCloudPlaylist>,
    val artists: List<SoundCloudUser>
)

/** A page of search: one service's best result, artists, albums, playlists and tracks. */
@Composable
private fun SearchResultsList(
    source: SearchSource,
    results: SearchResults,
    listState: androidx.compose.foundation.lazy.LazyListState,
    favoritesMap: Map<Long, FavoriteTrack>,
    currentTrackId: Long?,
    downloadProgress: DownloadProgressOf,
    isPlaying: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    bottomRoom: Dp,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    onOpenArtist: (SoundCloudUser) -> Unit,
    onOpenPlaylist: (SoundCloudPlaylist) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val topResult = remember(results.query, results.albums, results.playlists) {
        pickTopResult(results.query, results.albums, results.playlists)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Rows of a group sit flush, so spacing between blocks is set per item instead.
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = bottomRoom)
    ) {
        if (results.loading) {
            item(key = "search-loading") {
                LoadingBlock(modifier = Modifier.padding(top = 16.dp), height = 120.dp)
            }
        }

        if (results.error != null) {
            item(key = "search-error") {
                Box(modifier = Modifier.padding(top = 16.dp)) { MessageCard(results.error) }
            }
        }

        if (topResult != null) {
            item(key = "search-top-result") {
                Column(modifier = Modifier.padding(top = 24.dp)) {
                    Kicker(text = tr("Лучший результат"))
                    Spacer(modifier = Modifier.height(10.dp))
                    TopResultCard(
                        kicker = setCaption(topResult),
                        title = topResult.title ?: tr("Без названия"),
                        // YouTube Music doesn't say how many tracks a set has until it is opened.
                        subtitle = listOfNotNull(
                            topResult.user?.username,
                            topResult.trackCount.takeIf { it > 0 }?.let { plural(it, tr("трек"), tr("трека"), tr("треков")) }
                        ).joinToString(" · "),
                        artworkUrl = topResult.displayArtworkUrl,
                        onClick = { onOpenPlaylist(topResult) }
                    )
                }
            }
        }

        if (results.artists.isNotEmpty()) {
            item(key = "search-artists-header") {
                SectionTitle(tr("Исполнители"), modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            }
            item(key = "search-artists") {
                ArtistRow(artists = results.artists, onOpen = onOpenArtist)
            }
        }

        if (results.albums.isNotEmpty()) {
            item(key = "search-albums-header") {
                SectionTitle(tr("Альбомы"), modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            }
            item(key = "search-albums") {
                AlbumCarousel(
                    albums = results.albums.map { album ->
                        CarouselAlbum(
                            key = album.id,
                            title = album.title ?: tr("Без названия"),
                            subtitle = album.user?.username.orEmpty(),
                            caption = setCaption(album),
                            artworkUrl = album.displayArtworkUrl,
                            onClick = { onOpenPlaylist(album) }
                        )
                    }
                )
            }
        }

        if (results.playlists.isNotEmpty()) {
            item(key = "search-playlists-header") {
                SectionTitle(
                    tr("Плейлисты и сборники"),
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }
            // Two to a row, up to three rows; the rest are a scroll of the search further on.
            results.playlists.take(6).chunked(2).forEachIndexed { rowIndex, pair ->
                item(key = "search-playlists-row-$rowIndex") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { playlist ->
                            CompactCollectionCard(
                                title = playlist.title ?: tr("Без названия"),
                                caption = if (playlist.trackCount > 0) {
                                    plural(playlist.trackCount, tr("трек"), tr("трека"), tr("треков"))
                                } else {
                                    setCaption(playlist)
                                },
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

        if (results.tracks.isNotEmpty()) {
            if (results.albums.isNotEmpty() || results.playlists.isNotEmpty() || results.artists.isNotEmpty() || topResult != null) {
                item(key = "search-tracks-header") {
                    SectionTitle(tr("Треки"), modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                }
            } else {
                item(key = "search-tracks-gap") { Spacer(modifier = Modifier.height(20.dp)) }
            }
            val showLoadMore = hasMore && !results.loading
            val rowCount = results.tracks.size + if (showLoadMore) 1 else 0
            itemsIndexed(
                results.tracks,
                key = { _, track -> "${source.name}-search-${track.id}" }
            ) { index, track ->
                val favorite = favoritesMap[track.id]
                TrackCard(
                    track = track,
                    isFavorite = favorite != null,
                    isSelected = track.id == currentTrackId,
                    downloadState = favorite?.downloadState,
                    progress = { downloadProgress[track.id] },
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
        } else if (!results.loading && results.query.isNotBlank() && results.error == null &&
            results.albums.isEmpty() && results.playlists.isEmpty() && results.artists.isEmpty()
        ) {
            item(key = "search-empty") {
                Box(modifier = Modifier.padding(top = 20.dp)) { EmptyState(tr("Ничего не нашлось.")) }
            }
        }
    }
}

/**
 * The services a search can ask, on a strip of glass at the bottom — over the keyboard while
 * typing. The lit pill rides along with the pages as they are swiped. The friends' dock has them
 * too, a tab with a count ([badges]) of what waits there.
 */
@Composable
internal fun SearchSourceTabs(
    labels: List<String>,
    pager: androidx.compose.foundation.pager.PagerState,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList()
) {
    val spans = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val density = LocalDensity.current
    val accent = PanelColors.accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchTabsHeight)
            .frosted(CircleShape, PanelColors.container.copy(alpha = GlassAlpha))
            .padding(6.dp)
    ) {
        // The pill, between the tabs the pages are between.
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .drawBehind {
                    val position = (pager.currentPage + pager.currentPageOffsetFraction)
                        .coerceIn(0f, (labels.size - 1).toFloat())
                    val from = spans[kotlin.math.floor(position).toInt()] ?: return@drawBehind
                    val to = spans[kotlin.math.ceil(position).toInt()] ?: from
                    val t = position - kotlin.math.floor(position)
                    val left = from.first + (to.first - from.first) * t
                    val width = from.second + (to.second - from.second) * t
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(left, 0f),
                        size = androidx.compose.ui.geometry.Size(width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f)
                    )
                }
                .fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxSize()) {
            labels.forEachIndexed { index, label ->
                // Derived: read straight, the swipe's every frame composed the tabs again.
                val lit by remember(pager, index) {
                    androidx.compose.runtime.derivedStateOf {
                        kotlin.math.abs(pager.currentPage + pager.currentPageOffsetFraction - index) < 0.5f
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onGloballyPositioned { spans[index] = it.positionInParent().x to it.size.width.toFloat() }
                        .clip(CircleShape)
                        .clickable { onSelect(index) },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (lit) PanelColors.onAccent else PanelColors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val badge = badges.getOrNull(index) ?: 0
                    if (badge > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (lit) PanelColors.onAccent else PanelColors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = badge.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (lit) PanelColors.accent else PanelColors.onAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

internal val SearchTabsHeight = 56.dp
internal val SearchFieldHeight = 60.dp
internal val SearchDockGap = 8.dp

// The field and the tabs under it, with the gap above the keyboard (or the screen's foot). The
// friends' page has the same dock.
internal fun searchDockHeight(tabs: Boolean): Dp =
    12.dp + SearchFieldHeight + (if (tabs) SearchDockGap + SearchTabsHeight else 0.dp)

/**
 * Room under the last row of a page with search's dock at its foot (the field, the tabs) and the
 * mini player standing on it: the gesture bar under both counted in — left out, the row ended
 * exactly where the mini player began — and a gap over them.
 */
@Composable
internal fun dockedPageRoom(tabs: Boolean = true, player: Boolean = LocalNowPlaying.current.trackId != null): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        24.dp + searchDockHeight(tabs) + (if (player) MiniPlayerHeight + 12.dp else 0.dp)

// The mini player's height, with its padding, for what must leave room for it.
internal val MiniPlayerHeight = 72.dp

/** Whether the phone is on its side: the screens then put their controls in a pane on the right. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

// Sideways, the pane on the right a screen's controls go in, under the thumb, the mini player at its
// foot; and the room kept for it there.
internal val LandscapePaneWidth = 400.dp
private val PaneMiniPlayerRoom = MiniPlayerHeight + 16.dp + 16.dp

/**
 * Sideways, a list of settings or playlists across the whole width is hard to read: it keeps to a
 * column clear of the pane on the right, where the mini player is.
 */
@Composable
private fun Modifier.readableSideways(): Modifier =
    if (isLandscape()) {
        this
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
            .padding(end = LandscapePaneWidth - 80.dp)
    } else {
        this
    }

/** Set for a collection's header drawn in the side pane rather than on top of its list. */
private val LocalHeaderPane = androidx.compose.runtime.staticCompositionLocalOf { false }

/**
 * A collection's screen (an album, a playlist, a mix, an artist): upright, its [header] tops the
 * list and scrolls away with it; sideways, the list takes the left and the header stands in a pane
 * on the right, under the thumb, with the mini player at its foot.
 */
@Composable
private fun DetailFrame(
    listState: androidx.compose.foundation.lazy.LazyListState,
    bottomPadding: Dp,
    header: @Composable () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    if (isLandscape()) {
        Row(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
                // Nothing of the bar above is over the list (its actions are over the pane); the
                // mini player is in the pane, not over the list.
                contentPadding = PaddingValues(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                    bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
                content = content
            )
            androidx.compose.runtime.CompositionLocalProvider(LocalHeaderPane provides true) {
                Box(modifier = Modifier.width(LandscapePaneWidth).fillMaxHeight()) { header() }
            }
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // Over the system's bar as well: the toolbar and the mini player stand on it.
            contentPadding = PaddingValues(bottom = bottomPadding + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            item(key = "detail-hero") { header() }
            content()
        }
    }
}

/**
 * A collection's header as a side pane: its picture filling the pane's top and fading into the
 * page, [text] over its lower edge, then [actions] on the right, where the thumb is, and room below
 * for the mini player.
 */
@Composable
private fun HeaderPane(
    picture: @Composable BoxScope.() -> Unit,
    onPictureClick: (() -> Unit)?,
    text: @Composable ColumnScope.() -> Unit,
    actions: (@Composable RowScope.() -> Unit)?,
    // Kept clear at the pane's foot: the mini player, and a screen's toolbar under it.
    bottomRoom: Dp = PaneMiniPlayerRoom
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .then(if (onPictureClick != null) Modifier.clickable(onClick = onPictureClick) else Modifier)
                .fadedDownward(CoverFade)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = picture
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.3f), 0.18f to Color.Transparent))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.End))
                .padding(start = 20.dp, end = 16.dp, bottom = bottomRoom)
        ) {
            text()
            if (actions != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
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
                    ?: artist.trackCount?.takeIf { it > 0 }?.let { plural(it, tr("трек"), tr("трека"), tr("треков")) }
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
        "album" -> tr("Альбом")
        "ep" -> "EP"
        "single" -> tr("Сингл")
        "compilation" -> tr("Сборник")
        else -> if (set.isAlbum == true) tr("Альбом") else tr("Плейлист")
    }
    val year = set.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }
    return when {
        year != null -> "$kind · $year"
        // YouTube Music doesn't say how many tracks a set has until it is opened.
        set.trackCount <= 0 -> kind
        else -> "$kind · ${plural(set.trackCount, tr("трек"), tr("трека"), tr("треков"))}"
    }
}

@Composable
private fun DownloadsScreen(
    tracks: List<FavoriteTrack>,
    folderArtworkUri: String?,
    currentTrackId: Long?,
    // The player opened over the screen: picking ends, or the first "back" from the player
    // would unpick tracks out of sight instead of closing it.
    playerOpen: Boolean = false,
    downloadProgress: DownloadProgressOf = NoDownloadProgress,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onChangeArtwork: (String?) -> Unit,
    onPlayTrack: (FavoriteTrack) -> Unit,
    onDeleteDownloads: (List<FavoriteTrack>) -> Unit,
    onImportTracks: (List<android.net.Uri>) -> Unit,
    onShuffle: () -> Unit = {},
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

    val albumLibrary = LocalAlbumLibrary.current
    val isActive = currentTrackId != null && tracks.any { it.id == currentTrackId }
    val listState = rememberLazyListState()
    val collapsed = rememberCollapsed(listState, MixCoverHeight - 140.dp)
    // The tracks asked to be deleted, until that is confirmed or called off.
    var pendingDelete by remember { mutableStateOf<List<FavoriteTrack>?>(null) }
    // A row held: its menu, to delete the track or start picking tracks.
    var menuFor by remember { mutableStateOf<Long?>(null) }
    // The tracks picked, to delete together; null while nothing is being picked. The last one
    // unpicked ends the picking, as Android's own lists do.
    var picked by remember { mutableStateOf<Set<Long>?>(null) }
    // A track still downloading is neither held nor picked: there is no file yet to delete.
    val pickable = remember(tracks) { tracks.filter { it.downloadState != DownloadState.DOWNLOADING } }
    // Deleted from elsewhere meanwhile: what's gone is no longer picked.
    LaunchedEffect(pickable) {
        val ids = pickable.mapTo(HashSet()) { it.id }
        picked = picked?.filterTo(HashSet()) { it in ids }?.ifEmpty { null }
    }
    fun togglePicked(id: Long) {
        val now = picked.orEmpty()
        picked = (if (id in now) now - id else now + id).ifEmpty { null }
    }
    LaunchedEffect(playerOpen) {
        if (playerOpen) {
            picked = null
            menuFor = null
        }
    }
    BackHandler(enabled = picked != null) { picked = null }
    val picking = picked != null
    var showMore by remember { mutableStateOf(false) }
    val importTracks = { audioPicker.launch(arrayOf("audio/*", "video/*")) }
    val artwork: @Composable BoxScope.() -> Unit = {
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
    }

    // A folder, opened as an album is: its cover across the top, the tracks, and under the thumb
    // its toolbar — or, while tracks are picked, theirs.
    CollectionPage(
        onBack = onBack,
        page = {
            DetailFrame(
                listState = listState,
                // The last rows clear of the toolbar (the folder's or the picked tracks') and the mini
                // player over it.
                bottomPadding = FeedBottomRoom,
                header = {
                    CoverHeader(
                        artwork = artwork,
                        kicker = tr("Папка · на устройстве"),
                        title = tr("Скачанное"),
                        subtitle = tr("Играет без сети"),
                        // The cover opens "Ещё", where changing it is.
                        onArtworkClick = { showMore = true }
                    )
                }
            ) {
                item(key = "downloads-count") {
                    CountRule(if (tracks.isEmpty()) tr("Нет треков") else plural(tracks.size, tr("трек"), tr("трека"), tr("треков")))
                }

                if (tracks.isEmpty()) {
                    item(key = "downloads-empty") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            EmptyState(tr("Здесь появятся треки, которые ты сохранишь на устройство."))
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { _, track -> "downloaded-${track.id}" }) { _, track ->
                        val canPick = track.downloadState != DownloadState.DOWNLOADING
                        val picking = picked
                        // No bin on every row: one stray tap on it threw a download away, and a column
                        // of them was all the list showed. Holding a row offers deleting instead.
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            DownloadedTrackCard(
                                track = track,
                                isSelected = track.id == currentTrackId,
                                progress = { downloadProgress[track.id] },
                                isPlaying = isPlaying,
                                onClick = {
                                    when {
                                        picking == null -> onPlayTrack(track)
                                        canPick -> togglePicked(track.id)
                                    }
                                },
                                onLongClick = if (!canPick) null else {
                                    { if (picking == null) menuFor = track.id else togglePicked(track.id) }
                                },
                                // One still downloading keeps its progress in the mark's place.
                                checked = picking?.let { track.id in it },
                                showDebugPercentage = showDebugPercentage,
                                debugPercentage = downloadedPercentages[track.id],
                                flat = true
                            )
                            DropdownMenu(
                                expanded = menuFor == track.id,
                                onDismissRequest = { menuFor = null },
                                // Under the row's text, not its cover.
                                offset = DpOffset(x = 76.dp, y = 0.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(tr("Выбрать")) },
                                    leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null) },
                                    onClick = {
                                        menuFor = null
                                        picked = setOf(track.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(tr("Удалить")) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        menuFor = null
                                        pendingDelete = listOf(track)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            CollapsingTopBar(title = tr("Скачанное"), collapsed = collapsed)
        }
    ) {
        // Adding from the phone is what the folder offers next; the rest is in "Ещё".
        CollectionBar(
            step = CollectionStep(StepKind.Add),
            onStep = importTracks,
            onMore = { showMore = true },
            labelled = !collapsed,
            isActive = isActive,
            isPlaying = isPlaying,
            onPlay = if (tracks.isEmpty()) null else {
                {
                    if (isActive && albumLibrary != null) albumLibrary.onTogglePlay() else onPlayTrack(tracks.first())
                }
            },
            onShuffle = if (tracks.size < 2) null else onShuffle,
            visible = !picking
        )

        // Picking: what to do with the picked tracks, in the folder's toolbar's place.
        // The count stays put while the toolbar slides away.
        val lastCount = remember { intArrayOf(0) }
        val shownCount = picked?.size?.also { lastCount[0] = it } ?: lastCount[0]
        AnimatedVisibility(
            visible = picking,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = thumbBarPlacement()
        ) {
            val all = picked?.size == pickable.size
            PickingToolbar(
                count = shownCount,
                allPicked = all,
                onCancel = { picked = null },
                onToggleAll = { picked = if (all) null else pickable.mapTo(HashSet()) { it.id } },
                onDelete = {
                    val ids = picked.orEmpty()
                    pendingDelete = pickable.filter { it.id in ids }.ifEmpty { null }
                }
            )
        }
    }

    if (showMore) {
        CollectionSheet(
            artwork = artwork,
            kicker = tr("Папка · на устройстве"),
            title = tr("Скачанное"),
            subtitle = if (tracks.isEmpty()) tr("Нет треков") else plural(tracks.size, tr("трек"), tr("трека"), tr("треков")),
            tiles = listOfNotNull(
                CollectionAction(
                    icon = Icons.Default.Add,
                    label = tr("Добавить"),
                    subtitle = tr("Импортировать треки и видео с устройства"),
                    onClick = importTracks
                ),
                CollectionAction(Icons.Rounded.Shuffle, tr("Вперемешку"), onClick = onShuffle).takeIf { tracks.size >= 2 },
                CollectionAction(Icons.Default.Image, tr("Обложка"), onClick = { imagePicker.launch(arrayOf("image/*")) })
            ),
            rows = listOfNotNull(
                // What holding a row offers, here where it can be found.
                CollectionAction(
                    icon = Icons.Rounded.Checklist,
                    label = tr("Выбрать треки"),
                    subtitle = tr("Чтобы удалить несколько сразу"),
                    onClick = { picked = emptySet() }
                ).takeIf { pickable.isNotEmpty() }
            ),
            onDismiss = { showMore = false }
        )
    }

    pendingDelete?.let { doomed ->
        val single = doomed.singleOrNull()
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("Удалить с устройства?")) },
            text = {
                Text(
                    if (single != null) {
                        tr("«%s» — %s. Скачанный файл удалится с телефона, в любимых трек останется.", single.title, single.displayArtist)
                    } else {
                        plural(doomed.size, tr("трек"), tr("трека"), tr("треков")) +
                            tr(": скачанные файлы удалятся с телефона, в любимых треки останутся.")
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        picked = null
                        onDeleteDownloads(doomed)
                    }
                ) {
                    Text(tr("Удалить"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(tr("Отмена"))
                }
            }
        )
    }
}

/** The latest distinct covers of [tracks], as many as a [CoverCollage] shows. */
private fun recentCovers(tracks: List<SoundCloudTrack>): List<String> =
    tracks.asSequence()
        .mapNotNull { it.artworkUrl?.takeIf(String::isNotBlank) }
        .distinct()
        .take(4)
        .toList()

/**
 * A folder's cover made of the covers of what is in it: four in a square once there are four,
 * otherwise the latest alone.
 */
@Composable
private fun CoverCollage(urls: List<String>, modifier: Modifier = Modifier, cellSize: Dp? = null) {
    // Asked for at the size a cell is shown when it is known: on home's stage, four 800 px covers
    // were fetched for a square of 224 dp.
    @Composable
    fun Cover(url: String, cell: Modifier) = AsyncImage(
        model = cellSize?.let { artworkUrlForSize(url, it) } ?: ArtworkUrls.highRes(url) ?: url,
        contentDescription = null,
        modifier = cell,
        contentScale = ContentScale.Crop
    )
    if (urls.size < 4) {
        urls.firstOrNull()?.let { Cover(it, modifier.fillMaxSize()) }
        return
    }
    Column(modifier = modifier.fillMaxSize()) {
        urls.take(4).chunked(2).forEach { row ->
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                row.forEach { url -> Cover(url, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/**
 * "История": the last [com.example.myapplication.data.ListeningHistory.LIMIT] tracks listened to,
 * newest first, opened as a folder is. A track plays on through what was heard before it.
 */
@Composable
private fun HistoryScreen(
    tracks: List<SoundCloudTrack>,
    favorites: List<FavoriteTrack>,
    currentTrackId: Long?,
    downloadProgress: DownloadProgressOf = NoDownloadProgress,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onTogglePlay: () -> Unit,
    onShuffle: () -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    onClear: () -> Unit
) {
    val listState = rememberLazyListState()
    val collapsed = rememberCollapsed(listState, MixCoverHeight - 140.dp)
    val favoritesMap = remember(favorites) { favorites.associateBy { it.id } }
    var confirmClear by remember { mutableStateOf(false) }
    // What plays goes to the top of the history once heard: the top playing is the history
    // playing, and the big button pauses it rather than starting it over.
    val topPlaying = currentTrackId != null && tracks.firstOrNull()?.id == currentTrackId
    val covers = remember(tracks) { recentCovers(tracks) }
    var showMore by remember { mutableStateOf(false) }
    val artwork: @Composable BoxScope.() -> Unit = {
        if (covers.isEmpty()) IconCover(icon = Icons.Default.History) else CoverCollage(urls = covers)
    }

    CollectionPage(
        onBack = onBack,
        page = {
            DetailFrame(
                listState = listState,
                // The last rows clear of the toolbar and the mini player over it.
                bottomPadding = FeedBottomRoom,
                header = {
                    CoverHeader(
                        artwork = artwork,
                        kicker = tr("Папка"),
                        title = tr("История"),
                        subtitle = tr("Что ты слушал, последнее сверху"),
                        onArtworkClick = if (tracks.isEmpty()) null else {
                            { showMore = true }
                        }
                    )
                }
            ) {
                item(key = "history-count") {
                    CountRule(if (tracks.isEmpty()) tr("Пока пусто") else plural(tracks.size, tr("трек"), tr("трека"), tr("треков")))
                }

                if (tracks.isEmpty()) {
                    item(key = "history-empty") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            EmptyState(tr("Здесь появятся треки, которые ты слушал."))
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { _, track -> "history-${track.id}" }) { _, track ->
                        val favorite = favoritesMap[track.id]
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            TrackCard(
                                track = track,
                                isFavorite = favorite != null,
                                isSelected = track.id == currentTrackId,
                                downloadState = favorite?.downloadState,
                                progress = { downloadProgress[track.id] },
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track) },
                                onFavoriteClick = { onFavoriteClick(track) },
                                flat = true
                            )
                        }
                    }
                }
            }

            CollapsingTopBar(title = tr("История"), collapsed = collapsed)
        }
    ) {
        // Nothing to keep or download: "Ещё" in words, with clearing the history in it.
        CollectionBar(
            step = if (tracks.isEmpty()) null else CollectionStep(StepKind.More),
            onStep = { showMore = true },
            onMore = null,
            labelled = !collapsed,
            isActive = topPlaying,
            isPlaying = isPlaying,
            onPlay = if (tracks.isEmpty()) null else {
                { if (topPlaying) onTogglePlay() else onPlayTrack(tracks.first()) }
            },
            onShuffle = if (tracks.size < 2) null else onShuffle
        )
    }

    if (showMore) {
        CollectionSheet(
            artwork = artwork,
            kicker = tr("Папка"),
            title = tr("История"),
            subtitle = plural(tracks.size, tr("трек"), tr("трека"), tr("треков")),
            tiles = listOfNotNull(
                CollectionAction(Icons.Rounded.Shuffle, tr("Вперемешку"), onClick = onShuffle).takeIf { tracks.size >= 2 }
            ),
            rows = listOf(
                CollectionAction(
                    icon = Icons.Default.DeleteSweep,
                    label = tr("Очистить историю"),
                    subtitle = tr("Треки, любимые и скачанное останутся"),
                    onClick = { confirmClear = true },
                    danger = true
                )
            ),
            onDismiss = { showMore = false }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Очистить историю?")) },
            text = { Text(tr("Список прослушанного удалится. Сами треки, любимые и скачанное останутся.")) },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClear = false
                        onClear()
                    }
                ) {
                    Text(tr("Очистить"))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(tr("Отмена"))
                }
            }
        )
    }
}

/**
 * The tracks picked in "Скачанное" and what to do with them, as home's floating toolbar is laid
 * out: how many on the panel-tone pill, with the cross that stops picking, and on the right the
 * two actions as its floating buttons — picking them all, and deleting, in the accent.
 */
@Composable
private fun PickingToolbar(
    count: Int,
    allPicked: Boolean,
    onCancel: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val glass = LocalGlass.current
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier
                .padding(end = 8.dp)
                .height(64.dp)
                .glassOr(CircleShape, PanelColors.container),
            shape = CircleShape,
            color = if (glass) Color.Transparent else PanelColors.container,
            contentColor = PanelColors.content,
            shadowElevation = if (glass) 0.dp else 6.dp
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCancel()
                    },
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = PanelColors.content.copy(alpha = 0.12f),
                    contentColor = PanelColors.content
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = tr("Отменить выбор"), modifier = Modifier.size(24.dp))
                    }
                }
                Text(
                    text = tr("Выбрано: %s", count),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        val toggleFill = androidx.compose.ui.graphics.lerp(PanelColors.container, PanelColors.content, 0.08f)
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleAll()
            },
            modifier = Modifier
                .size(64.dp)
                .glassOr(RoundedCornerShape(20.dp), toggleFill),
            shape = RoundedCornerShape(20.dp),
            color = if (glass) Color.Transparent else toggleFill,
            contentColor = PanelColors.accent,
            shadowElevation = if (glass) 0.dp else 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (allPicked) Icons.Default.Deselect else Icons.Default.SelectAll,
                    contentDescription = if (allPicked) tr("Снять выбор") else tr("Выбрать все"),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            },
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(20.dp),
            color = PanelColors.accent,
            contentColor = PanelColors.onAccent,
            shadowElevation = if (glass) 0.dp else 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Delete, contentDescription = tr("Удалить выбранные"), modifier = Modifier.size(26.dp))
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
                text = tr("Найти трек"),
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
    val glass = LocalGlass.current
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(SearchFieldHeight)
            .glassOr(RoundedCornerShape(30.dp), fill)
            .focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(30.dp),
        textStyle = MaterialTheme.typography.titleMedium,
        placeholder = { Text(tr("Трек, альбом или артист")) },
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
                    Icon(Icons.Default.Close, contentDescription = tr("Очистить"))
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (glass) Color.Transparent else fill,
            unfocusedContainerColor = if (glass) Color.Transparent else fill,
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
        leadingDescription = tr("Назад"),
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
                !hasOauthToken -> tr("Добавь OAuth-токен в настройках, чтобы увидеть персональные миксы.")
                mixSection == null && !isLoading -> tr("Подборка пока не загрузилась.")
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
                        text = tr("Открыть настройки и вставить OAuth"),
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
                Text(tr("Повторить"))
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
                EmptyState(tr("Подборка your-moods пуста."))
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
    raw.isNullOrBlank() -> tr("Миксы")
    raw.contains("Station", ignoreCase = true) -> tr("Станции")
    raw.startsWith("Mixed for", ignoreCase = true) -> tr("Твои миксы")
    raw.contains("Your Mixes", ignoreCase = true) -> tr("Твои миксы")
    else -> raw
}

private val YourMixPattern = Regex("""^Your Mix\s*(\d+)$""", RegexOption.IGNORE_CASE)

private fun localizedGenre(raw: String): String = when (raw.trim().lowercase()) {
    "all genres", "all music genres" -> tr("Все жанры")
    else -> raw
}

private fun localizedMixTitle(raw: String): String =
    YourMixPattern.find(raw.trim())?.let { tr("Твой микс %s", it.groupValues[1]) } ?: raw

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
 * Liked albums as seen from any album screen, wherever it was opened from (artist page, search),
 * and the player as those screens' big buttons work it. Provided once at the root so those
 * screens don't each thread five more parameters through.
 */
private class AlbumLibrary(
    val likedBySource: Map<String, Playlist>,
    val onToggleLike: (album: SoundCloudPlaylist, artistName: String?) -> Unit,
    val onDownload: (Playlist) -> Unit,
    // Saves the album first when it isn't in the library yet.
    val onDownloadAlbum: (album: SoundCloudPlaylist, artistName: String?) -> Unit,
    val onTogglePlay: () -> Unit,
    // With the card it is from (see MusicViewModel.playingFrom).
    val onShuffle: (List<SoundCloudTrack>, source: String?) -> Unit,
    // A mix as it is today: kept in the library or let go of, and downloaded whole.
    val onToggleMix: (mix: SoundCloudMix, title: String, tracks: List<SoundCloudTrack>) -> Unit,
    val onDownloadMix: (mix: SoundCloudMix, title: String, tracks: List<SoundCloudTrack>) -> Unit
)

private val LocalAlbumLibrary = androidx.compose.runtime.staticCompositionLocalOf<AlbumLibrary?> { null }

/**
 * The next thing to do with a collection, which its toolbar says in words: save an album to the
 * library, then download it, how far that has got, and done; add tracks from the phone (to
 * "Скачанное"); "Ещё" itself when there is nothing next (a Yandex playlist, "История"), and
 * "Готово" while tracks are being taken out of a playlist.
 */
private enum class StepKind { Save, Download, Downloading, Downloaded, Add, More, Done }

private data class CollectionStep(val kind: StepKind, val saved: Int = 0, val total: Int = 0)

/**
 * Where downloading a saved playlist or liked album stands, as its toolbar says it. The tracks
 * land on the device for this playlist only; "Скачанное" does not change. Null with nothing to
 * download (files imported from the phone are on it already).
 */
private fun downloadStep(playlist: Playlist): CollectionStep? {
    val total = playlist.tracks.count { !it.urn.startsWith("local:") }
    if (total == 0) return null
    val saved = playlist.tracks.count { it.downloadState == DownloadState.DOWNLOADED && !it.urn.startsWith("local:") }
    return when {
        playlist.tracks.any { it.downloadState == DownloadState.DOWNLOADING } -> CollectionStep(StepKind.Downloading, saved, total)
        saved == total -> CollectionStep(StepKind.Downloaded)
        else -> CollectionStep(StepKind.Download)
    }
}

private fun stepLabel(step: CollectionStep): String = when (step.kind) {
    StepKind.Save -> tr("В медиатеку")
    StepKind.Download -> tr("Скачать")
    StepKind.Downloading -> tr("%s из %s", step.saved, step.total)
    StepKind.Downloaded -> tr("Скачано")
    StepKind.Add -> tr("Добавить")
    StepKind.More -> tr("Ещё")
    StepKind.Done -> tr("Готово")
}

private fun stepDescription(step: CollectionStep): String = when (step.kind) {
    StepKind.Save -> tr("Сохранить в медиатеку")
    StepKind.Download -> tr("Скачать все треки")
    StepKind.Downloading -> tr("Скачано %s из %s", step.saved, step.total)
    StepKind.Downloaded -> tr("Все треки на устройстве")
    StepKind.Add -> tr("Импортировать треки и видео с устройства")
    StepKind.More -> tr("Ещё")
    StepKind.Done -> tr("Готово")
}

/**
 * A collection's screen: its [page] on the page's glass, pulled down to close, and its [bar]
 * floating over it. The page is recorded for the bar, as home's feed is for home's toolbar: the
 * bar's glass shows the cover and the rows under it blurred, frosted as home's buttons are, rather
 * than the backdrop alone painted over them.
 */
@Composable
private fun CollectionPage(
    onBack: () -> Unit,
    page: @Composable BoxScope.() -> Unit,
    bar: @Composable BoxScope.() -> Unit
) {
    val backdrop = LocalFrostSources.current
    val pageFrost = rememberFrostSource()
    Box(
        modifier = Modifier
            .fillMaxSize()
            // No back button: pulled down, the page closes (see pullToClose).
            .pullToClose(onBack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .frostSource(pageFrost)
                .pageGlass(MaterialTheme.colorScheme.background),
            content = page
        )
        androidx.compose.runtime.CompositionLocalProvider(LocalFrostSources provides backdrop + pageFrost) {
            bar()
        }
    }
}

/** Where a screen's floating toolbar stands: at the bottom, under the thumb; sideways, at the foot of the pane on the right. */
@Composable
private fun BoxScope.thumbBarPlacement(): Modifier =
    if (isLandscape()) {
        Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
            .width(LandscapePaneWidth)
            .padding(16.dp)
    } else {
        Modifier
            .align(Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(16.dp)
    }

/**
 * A collection's floating toolbar, laid out as home's, with the mini player standing on it the
 * same way: on the panel-tone pill the next step in words and "Ещё" beside it, and on the right
 * "Перемешать" and "Слушать" as its floating buttons. Everything done with an album, a playlist
 * or a mix is here, under the thumb; the top of the screen is the cover's, to look at.
 *
 * It rises in with the screen, and sinks away while another toolbar has the bottom edge
 * ([visible] false: picking tracks). The step's words show while the cover is in view and fold
 * into its icon once the list is being read, as an extended FAB does; a new step lights up in the
 * accent and settles back into the pill.
 */
@Composable
private fun BoxScope.CollectionBar(
    step: CollectionStep?,
    onStep: () -> Unit,
    onMore: (() -> Unit)?,
    labelled: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    visible: Boolean = true
) {
    val shown = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) { shown.targetState = visible }
    // Sideways the pane has room, and the list beside it is not under the bar.
    val words = labelled || isLandscape()
    AnimatedVisibility(
        visibleState = shown,
        enter = slideInVertically(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = thumbBarPlacement()
    ) {
        CollectionToolbar(
            step = step,
            onStep = onStep,
            onMore = onMore,
            labelled = words,
            isActive = isActive,
            isPlaying = isPlaying,
            onPlay = onPlay,
            onShuffle = onShuffle
        )
    }
}

@Composable
private fun CollectionToolbar(
    step: CollectionStep?,
    onStep: () -> Unit,
    onMore: (() -> Unit)?,
    labelled: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?
) {
    val haptic = LocalHapticFeedback.current
    val glass = LocalGlass.current
    val pop = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (step != null || onMore != null) {
            Surface(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .height(64.dp)
                    .glassOr(CircleShape, PanelColors.container),
                shape = CircleShape,
                color = if (glass) Color.Transparent else PanelColors.container,
                contentColor = PanelColors.content,
                shadowElevation = if (glass) 0.dp else 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step != null) {
                        CollectionStepButton(
                            step = step,
                            labelled = labelled,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStep()
                            }
                        )
                    }
                    // "Ещё" in words is the step itself.
                    if (onMore != null && step?.kind != StepKind.More) {
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMore()
                            },
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = Color.Transparent,
                            contentColor = PanelColors.content
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.MoreHoriz, contentDescription = tr("Ещё"), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        // Both spring in once there are tracks to play.
        AnimatedVisibility(
            visible = onShuffle != null,
            enter = scaleIn(pop) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            val shuffleFill = androidx.compose.ui.graphics.lerp(PanelColors.container, PanelColors.content, 0.08f)
            Surface(
                onClick = { onShuffle?.invoke() },
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(64.dp)
                    .glassOr(RoundedCornerShape(20.dp), shuffleFill),
                shape = RoundedCornerShape(20.dp),
                color = if (glass) Color.Transparent else shuffleFill,
                contentColor = PanelColors.accent,
                shadowElevation = if (glass) 0.dp else 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = tr("Перемешать"), modifier = Modifier.size(26.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = onPlay != null,
            enter = scaleIn(pop) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            CollectionPlayButton(isActive = isActive, isPlaying = isPlaying, onClick = { onPlay?.invoke() })
        }
    }
}

/** The pill's step: its icon, and its words beside it while there is room for them. */
@Composable
private fun CollectionStepButton(step: CollectionStep, labelled: Boolean, onClick: () -> Unit) {
    // A new step flashes the accent and fades back into the pill's tone.
    val flash = remember { Animatable(0f) }
    var shownKind by remember { mutableStateOf(step.kind) }
    LaunchedEffect(step.kind) {
        if (step.kind != shownKind) {
            shownKind = step.kind
            flash.snapTo(1f)
            flash.animateTo(0f, tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }
    val bounce = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
        visibilityThreshold = IntSize.VisibilityThreshold
    )
    val lit = flash.value
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(48.dp)
            .semantics { contentDescription = stepDescription(step) },
        shape = CircleShape,
        color = androidx.compose.ui.graphics.lerp(PanelColors.content.copy(alpha = 0.1f), PanelColors.accent, lit),
        contentColor = androidx.compose.ui.graphics.lerp(PanelColors.content, PanelColors.onAccent, lit)
    ) {
        AnimatedContent(
            targetState = step,
            contentKey = { it.kind },
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) { it } + fadeIn(tween(180)))
                    .togetherWith(slideOutVertically(tween(160)) { -it } + fadeOut(tween(120)))
                    .using(SizeTransform(clip = false) { _, _ -> bounce })
            },
            label = "collectionStep"
        ) { shownStep ->
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tint = androidx.compose.ui.graphics.lerp(PanelColors.accent, PanelColors.onAccent, lit)
                if (shownStep.kind == StepKind.Downloading) {
                    AppCircularProgress(
                        progress = shownStep.saved.toFloat() / shownStep.total.coerceAtLeast(1),
                        modifier = Modifier.size(24.dp),
                        color = tint
                    )
                } else {
                    Icon(
                        imageVector = when (shownStep.kind) {
                            StepKind.Save -> Icons.Rounded.LibraryAdd
                            StepKind.Download -> Icons.Default.Download
                            StepKind.Downloaded -> Icons.Default.DownloadDone
                            StepKind.Add -> Icons.Default.Add
                            StepKind.More -> Icons.Rounded.MoreHoriz
                            else -> Icons.Rounded.Check
                        },
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
                AnimatedVisibility(
                    visible = labelled,
                    enter = expandHorizontally(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkHorizontally(spring(stiffness = Spring.StiffnessMedium)) + fadeOut(tween(120))
                ) {
                    Text(
                        text = stepLabel(shownStep),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp, end = 6.dp)
                    )
                }
            }
        }
    }
}

/**
 * "Слушать" on the toolbar: the call to action in the accent until the collection is playing; then
 * the player's toggle, lit and squarer while it plays, round and tonal while paused.
 */
@Composable
private fun CollectionPlayButton(isActive: Boolean, isPlaying: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val playing = isActive && isPlaying
    val corner by animateDpAsState(
        targetValue = if (playing) 18.dp else 26.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "collectionPlayCorner"
    )
    val (toggleContainer, toggleContent) = playToggleColors(isPlaying)
    val glass = LocalGlass.current
    // Paused, it is frosted glass as the toolbar's other buttons are; lit, a solid accent.
    val frosted = glass && isActive && !isPlaying
    val shape = RoundedCornerShape(corner)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .size(64.dp)
            .then(if (frosted) Modifier.glassOr(shape, PanelColors.container) else Modifier),
        shape = shape,
        color = when {
            !isActive -> PanelColors.accent
            frosted -> Color.Transparent
            else -> toggleContainer
        },
        contentColor = if (isActive) toggleContent else PanelColors.onAccent,
        shadowElevation = if (glass) 0.dp else 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) tr("Пауза") else tr("Слушать"),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** One action of a collection's "Ещё": a tile on top, or a row under the tiles. */
private class CollectionAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val subtitle: String? = null,
    val selected: Boolean = false,
    // Removes something: last, and in red.
    val danger: Boolean = false
)

/**
 * A collection's "Ещё", laid out as a track's menu: the collection on top, its main actions as
 * tiles, and the rarer ones on a panel below. What used to stand at the top of the screen — the ⋮
 * menu, the cover's own button — is here, opening from the bottom where the thumb is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionSheet(
    artwork: @Composable BoxScope.() -> Unit,
    kicker: String,
    title: String,
    subtitle: String?,
    tiles: List<CollectionAction>,
    rows: List<CollectionAction>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    // An action plays the sheet's own close, as a swipe or a scrim tap does.
    val run: (CollectionAction) -> Unit = { action ->
        action.onClick()
        sheetScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.onBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceContainerHigh),
                    content = artwork
                )
                Column(modifier = Modifier.weight(1f)) {
                    Kicker(text = kicker, color = PanelColors.accent)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (tiles.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tiles.forEach { tile ->
                        SheetTile(
                            icon = tile.icon,
                            label = tile.label,
                            description = tile.subtitle,
                            selected = tile.selected,
                            onClick = { run(tile) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // A lone tile keeps to half the width rather than a bar across the sheet.
                    if (tiles.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            if (rows.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(PanelColors.container)
                ) {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) SheetDivider()
                        SheetRow(
                            leading = { SheetRowIcon(row.icon, tint = if (row.danger) colors.error else PanelColors.accent) },
                            title = row.label,
                            subtitle = row.subtitle,
                            titleColor = if (row.danger) colors.error else PanelColors.content,
                            onClick = { run(row) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The bar over a screen that opens on a big picture: nothing while the picture is in view but the
 * screen's own actions ([trailing]), then the backdrop tone and the title once it has scrolled away.
 * There is no back button: these screens close when pulled down (see [pullToClose]).
 */
@Composable
private fun CollapsingTopBar(
    title: String,
    collapsed: Boolean,
    trailing: (@Composable () -> Unit)? = null
) {
    // Sideways the title stands in the pane beside the list: the bar never takes over.
    @Suppress("NAME_SHADOWING")
    val collapsed = collapsed && !isLandscape()
    val barColor by animateColorAsState(
        targetValue = if (collapsed) MaterialTheme.colorScheme.background else Color.Transparent,
        animationSpec = tween(220),
        label = "collapsingBar"
    )
    val glass = LocalGlass.current
    val barShown by animateFloatAsState(if (collapsed) 1f else 0f, tween(220), label = "collapsingBarGlass")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (glass) Modifier else Modifier.background(barColor))
    ) {
        if (glass) {
            // Only drawn once there is a bar to show: glass under a see-through bar would blur
            // the picture it lies over.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = barShown }
                    .then(
                        if (barShown > 0f) {
                            Modifier.frosted(tint = MaterialTheme.colorScheme.background.copy(alpha = PageGlassAlpha), rim = false)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // In line with the page's own text below it.
            Box(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                androidx.compose.animation.AnimatedVisibility(
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
            trailing?.invoke()
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
                Text(tr("Слушать"), style = MaterialTheme.typography.titleMedium)
            }
        }
        val shuffleShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp)
        Surface(
            onClick = onShuffle,
            modifier = Modifier
                .size(width = 64.dp, height = 56.dp)
                .glassOr(shuffleShape, PanelColors.container),
            shape = shuffleShape,
            color = glassFill(PanelColors.container),
            contentColor = PanelColors.accent
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Shuffle, contentDescription = tr("Перемешать"), modifier = Modifier.size(24.dp))
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
    onShuffle: (() -> Unit)?,
    follow: ArtistFollow? = null,
    onToggleFollow: () -> Unit = {}
) {
    val followers = artist.followersCount ?: 0
    val trackTotal = artist.trackCount ?: 0
    val stats = buildList {
        if (followers > 0) add(compactCount(followers) + tr(" подписчиков"))
        if (trackTotal > 0) add(plural(trackTotal, tr("трек"), tr("трека"), tr("треков")))
        if (albumCount > 0) add(plural(albumCount, tr("альбом"), tr("альбома"), tr("альбомов")))
        if (follow?.following == true) add(tr("вы подписаны"))
    }.joinToString(" · ")
    val backdrop = MaterialTheme.colorScheme.background
    if (LocalHeaderPane.current) {
        HeaderPane(
            picture = {
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
            },
            onPictureClick = null,
            text = {
                Kicker(
                    text = if (artist.permalinkUrl?.startsWith("yandex") == true) tr("Артист · Яндекс Музыка") else tr("Артист"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = artist.username.orEmpty(),
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (stats.isNotBlank()) {
                    Text(
                        text = stats,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = if ((onPlay == null || onShuffle == null) && follow == null) null else {
                {
                    if (follow != null) FollowButton(follow = follow, onToggle = onToggleFollow)
                    if (onPlay != null && onShuffle != null) PlayShuffleGroup(onPlay = onPlay, onShuffle = onShuffle)
                }
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ArtistPortraitHeight)
                .fadedDownward(PortraitFade)
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
            // Dark at the top for the status bar and the back button; the portrait fades into the
            // page below, so the name sits on something calm.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.18f to Color.Transparent
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
                text = if (artist.permalinkUrl?.startsWith("yandex") == true) tr("Артист · Яндекс Музыка") else tr("Артист"),
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
            val canPlay = onPlay != null && onShuffle != null
            if (canPlay || follow != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onPlay != null && onShuffle != null) PlayShuffleGroup(onPlay = onPlay, onShuffle = onShuffle)
                    Spacer(modifier = Modifier.weight(1f))
                    if (follow != null) FollowButton(follow = follow, onToggle = onToggleFollow)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private val ArtistPortraitHeight = 420.dp

/**
 * Following the artist in its own service: a subscription on YouTube, a like on Yandex Music, a
 * follow on SoundCloud. Round and glassy until followed; then it squares off, filled with the
 * accent, the Expressive way of a toggle.
 */
@Composable
private fun FollowButton(follow: ArtistFollow, onToggle: () -> Unit) {
    val corner by animateDpAsState(if (follow.following) 18.dp else 28.dp, label = "followCorner")
    val shape = RoundedCornerShape(corner)
    Surface(
        onClick = onToggle,
        enabled = !follow.busy,
        modifier = Modifier
            .size(56.dp)
            .then(if (follow.following) Modifier else Modifier.glassOr(shape, PanelColors.container)),
        shape = shape,
        color = if (follow.following) PanelColors.accent else glassFill(PanelColors.container),
        contentColor = if (follow.following) PanelColors.onAccent else PanelColors.accent
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (follow.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.5.dp
                )
            } else {
                Icon(
                    imageVector = if (follow.following) Icons.Rounded.HowToReg else Icons.Rounded.PersonAddAlt1,
                    contentDescription = if (follow.following) tr("Отписаться") else tr("Подписаться"),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** A mix's or station's top: its cover, "Микс" or "Станция", its name and description. */
@Composable
private fun MixCoverHeader(
    mix: SoundCloudMix,
    title: String,
    isStation: Boolean,
    kicker: String
) {
    CoverHeader(
        artwork = {
            AsyncImage(
                model = ArtworkUrls.highRes(mix.artworkUrl) ?: mix.artworkUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        },
        kicker = kicker,
        title = title,
        // Stations put "Artist station" in the description, which their title already says.
        subtitle = if (isStation) null else mix.description?.takeIf { it.isNotBlank() }
    )
}

/**
 * The top of a collection screen — a mix, an album, a playlist, a folder: its cover as the
 * backdrop of the screen's top and the title over its lower edge, across the whole width. What is
 * done with the collection is on its toolbar under the thumb (see [CollectionBar]), not here.
 */
@Composable
private fun CoverHeader(
    artwork: @Composable BoxScope.() -> Unit,
    kicker: String,
    title: String,
    subtitle: String?,
    onArtworkClick: (() -> Unit)? = null,
    // In [subtitle]'s place: the collection's artists as chips.
    byline: (@Composable () -> Unit)? = null
) {
    if (LocalHeaderPane.current) {
        HeaderPane(
            picture = artwork,
            onPictureClick = onArtworkClick,
            // Sideways the toolbar stands at the pane's foot, the mini player over it.
            bottomRoom = PaneMiniPlayerRoom + HomeToolbarClearance,
            text = {
                Kicker(text = kicker, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (byline != null) {
                    byline()
                } else if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            actions = null
        )
        return
    }
    val backdrop = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MixCoverHeight)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (onArtworkClick != null) Modifier.clickable(onClick = onArtworkClick) else Modifier)
                // Covers often carry lettering low down (a station's name, "STATION") right where
                // the title goes, so the cover is all but gone by then or the two overprint.
                .fadedDownward(CoverFade)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = artwork
        )
        // The status bar and the back button stay legible on a bright cover.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.2f to Color.Transparent
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
                Kicker(text = kicker, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // The track count is the rule right below.
                if (byline != null) {
                    byline()
                } else if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val MixCoverHeight = 420.dp

// Where a picture at the top of a page is whole, and where it has faded out into the page: on the
// header's last line, under the title. It used to be gone above the title, and a short one ("Funk")
// stood alone in an empty band.
private val CoverFade = 0.42f to 1f
private val PortraitFade = 0.45f to 1f

/** Fades what this box draws out downward, between [fade]'s two fractions of its height. */
private fun Modifier.fadedDownward(fade: Pair<Float, Float>): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                fade.first to Color.Black,
                fade.second to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }

/**
 * The line between a collection's cover and its list: how many tracks, and a rule. What is done
 * with the collection as a whole is on its toolbar (see [CollectionBar]).
 */
@Composable
private fun CountRule(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 8.dp)
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = text,
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

@Composable
private fun MixDetailScreen(
    mix: SoundCloudMix,
    tracks: List<SoundCloudTrack>,
    currentTrackId: Long?,
    favorites: List<FavoriteTrack>,
    downloadProgress: DownloadProgressOf = NoDownloadProgress,
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
    val isStation = mix.permalink.contains("station") || mix.id.contains("station")
    val title = if (isStation) mix.title else localizedMixTitle(mix.title)
    val listState = rememberLazyListState()
    val collapsed = rememberCollapsed(listState, MixCoverHeight - 140.dp)
    val context = LocalContext.current
    val albumLibrary = LocalAlbumLibrary.current
    // Kept today: SoundCloud makes the mix anew, and a copy keeps the day's tracks.
    val key = remember(mix.id) { mix.sourceKey() }
    val saved = albumLibrary?.likedBySource?.get(key)
    val library = albumLibrary?.takeIf { tracks.isNotEmpty() }
    val link = remember(mix) { SharedLinks.linkOf(mix) }
    var showMore by remember { mutableStateOf(false) }
    val step = when {
        library == null -> null
        saved == null -> CollectionStep(StepKind.Save)
        else -> downloadStep(saved)
    }
    val kind = if (isStation) tr("Станция") else tr("Микс")
    val kicker = if (saved != null) tr("%s · в медиатеке", kind) else kind
    val artwork: @Composable BoxScope.() -> Unit = {
        AsyncImage(
            model = ArtworkUrls.highRes(mix.artworkUrl) ?: mix.artworkUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }

    // The cover is this screen's picture; the moving shapes behind a flat list only made it busy.
    CollectionPage(
        onBack = onBack,
        page = {
            DetailFrame(
                listState = listState,
                // The last rows clear of the toolbar and the mini player over it.
                bottomPadding = FeedBottomRoom,
                header = { MixCoverHeader(mix = mix, title = title, isStation = isStation, kicker = kicker) }
            ) {
                // A rule between the cover and the list, so the two never blur together.
                item(key = "mix-count") {
                    CountRule(if (tracks.isEmpty()) tr("Загружаем треки") else plural(tracks.size, tr("трек"), tr("трека"), tr("треков")))
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
                                progress = { downloadProgress[track.id] },
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track) },
                                onFavoriteClick = { onFavoriteClick(track) },
                                flat = true
                            )
                        }
                    }
                }
            }

            CollapsingTopBar(title = title, collapsed = collapsed)
        }
    ) {
        // Kept as it is today, then downloaded, as an album is; shared from "Ещё".
        CollectionBar(
            step = step,
            onStep = {
                when {
                    library == null -> Unit
                    saved == null -> library.onToggleMix(mix, title, tracks)
                    step?.kind == StepKind.Download -> library.onDownload(saved)
                    else -> showMore = true
                }
            },
            onMore = if (library != null || link != null) {
                { showMore = true }
            } else {
                null
            },
            labelled = !collapsed,
            isActive = isActive,
            isPlaying = isPlaying,
            onPlay = if (tracks.isEmpty()) null else {
                { if (isActive) onTogglePlay() else onPlayTrack(tracks.first()) }
            },
            onShuffle = if (tracks.isEmpty()) null else onShuffle
        )
    }

    if (showMore && (library != null || link != null)) {
        val download = saved?.let { downloadStep(it) }
        CollectionSheet(
            artwork = artwork,
            kicker = kicker,
            title = title,
            subtitle = tracks.size.takeIf { it > 0 }?.let { plural(it, tr("трек"), tr("трека"), tr("треков")) },
            tiles = listOfNotNull(
                CollectionAction(
                    icon = Icons.Rounded.LibraryAdd,
                    label = tr("В медиатеку"),
                    subtitle = tr("Сегодняшние треки"),
                    onClick = { library?.onToggleMix(mix, title, tracks) }
                ).takeIf { library != null && saved == null },
                when {
                    library == null -> null
                    // Not kept yet: downloading keeps it on the way.
                    saved == null -> CollectionAction(
                        icon = Icons.Default.Download,
                        label = tr("Скачать"),
                        subtitle = tr("Микс сохранится в медиатеку"),
                        onClick = { library.onDownloadMix(mix, title, tracks) }
                    )
                    download != null -> CollectionAction(
                        icon = if (download.kind == StepKind.Downloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                        label = stepLabel(download),
                        selected = download.kind == StepKind.Downloaded,
                        onClick = { if (download.kind == StepKind.Download) library.onDownload(saved) }
                    )
                    else -> null
                },
                link?.let { url -> CollectionAction(Icons.Default.Share, tr("Поделиться"), onClick = { shareText(context, url) }) }
            ),
            rows = listOfNotNull(
                CollectionAction(
                    icon = Icons.Default.Delete,
                    label = tr("Убрать из медиатеки"),
                    subtitle = if ((saved?.downloadedCount ?: 0) > 0) tr("Скачанные для него треки удалятся с телефона") else null,
                    onClick = { library?.onToggleMix(mix, title, tracks) },
                    danger = true
                ).takeIf { saved != null && library != null }
            ),
            onDismiss = { showMore = false }
        )
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
    onLongClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val shape = if (flat) RoundedCornerShape(20.dp) else position.shape()
    val fill = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        flat -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val glass = LocalGlass.current && fill != Color.Transparent
    // The surface clips to its shape, so the ripple of the clickable inside keeps to the row.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (glass) Modifier.glassOr(shape, fill, if (isSelected) 0.8f else GlassAlpha) else Modifier),
        shape = shape,
        color = if (glass) Color.Transparent else fill,
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Column(
            modifier = Modifier.combinedClickable(
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                onLongClick = onLongClick?.let { longClick ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        longClick()
                    }
                }
            )
        ) {
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
    val clock = rememberLoopClock(running = animate)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .animatedHeight(18.dp) { if (animate) loopValue(clock.longValue, 420 + index * 130, 0.3f, 1f) else 0.45f }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Milliseconds since the loop began: the clock of the small looping animations (the playing bars,
 * the chevron under home's carousel). Moved on as often as [MotionPace] says — every frame with
 * "Плавность 120 Гц", sixty a second without — and on the same frames as the backdrop and every
 * other clock: each of their frames repaints the whole screen, backdrop and glass and all.
 */
@Composable
internal fun rememberLoopClock(running: Boolean = true): androidx.compose.runtime.MutableLongState {
    val clock = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    // Under a page or the player it stands still: a clock that ran on there kept the window drawing
    // frames for a screen no one could see. Watched, not read: nothing composes again for it.
    val covered = LocalCovered.current
    LaunchedEffect(running, covered) {
        if (!running) return@LaunchedEffect
        snapshotFlow { covered() }.collectLatest { hidden ->
            if (hidden) return@collectLatest
            var start = 0L
            var shown = -1L
            var last = 0L
            while (true) {
                sleepToSlot(last, MotionPace.slotNanos(lively = true))
                withFrameNanos { now ->
                    last = now
                    if (start == 0L) start = now - clock.longValue * 1_000_000L
                    // As often as MotionPace says, on the frames the backdrop and every other clock
                    // move on (see frameSlot).
                    val slot = frameSlot(now, MotionPace.slotNanos(lively = true))
                    if (slot != shown) {
                        shown = slot
                        clock.longValue = (now - start) / 1_000_000L
                    }
                }
            }
        }
    }
    return clock
}

/**
 * A tick for what follows the playing track — the mini player's line, the player's bar — while
 * [running] and the screen shows: sixty a second with "Плавность 120 Гц", thirty without, on the
 * same frames as the backdrop (see [MotionPace]). Read where the thing is drawn, it redraws that
 * alone and composes nothing; paused, or under a page, no frames are asked for at all. The track's
 * position is sampled twice a second, which moved the bars in jumps.
 */
@Composable
internal fun rememberPlaybackTick(running: Boolean): androidx.compose.runtime.LongState {
    val tick = remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    val covered = LocalCovered.current
    LaunchedEffect(running, covered) {
        if (!running) return@LaunchedEffect
        snapshotFlow { covered() }.collectLatest { hidden ->
            if (hidden) return@collectLatest
            var shown = -1L
            var last = 0L
            while (true) {
                sleepToSlot(last, MotionPace.slotNanos(lively = false))
                withFrameNanos { now ->
                    last = now
                    val slot = frameSlot(now, MotionPace.slotNanos(lively = false))
                    if (slot != shown) {
                        shown = slot
                        tick.longValue = now
                    }
                }
            }
        }
    }
    return tick
}


/**
 * Whether the screen this is in is covered whole — by a page opened over it, or by the full
 * player — and not drawn meanwhile. What moves by itself there (the looping clocks, the wave's
 * turning shape) waits, and its back handling leaves the back gesture to what is on top.
 */
internal val LocalCovered = androidx.compose.runtime.staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * What `infiniteRepeatable(tween(durationMs, FastOutSlowInEasing), RepeatMode.Reverse)` from [from]
 * to [to] gives at [timeMs]: there and back, eased both ways.
 */
private fun loopValue(timeMs: Long, durationMs: Int, from: Float, to: Float): Float {
    val cycle = timeMs % (2L * durationMs)
    val t = if (cycle < durationMs) cycle / durationMs.toFloat() else (2L * durationMs - cycle) / durationMs.toFloat()
    return from + (to - from) * FastOutSlowInEasing.transform(t)
}

/**
 * `height(max * fraction())`, with the fraction read while laying out rather than while composing:
 * bars that move on every frame are measured again then, not composed again with all around them.
 */
private fun Modifier.animatedHeight(max: Dp, fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val height = (max * fraction()).roundToPx().coerceAtLeast(0).coerceIn(constraints.minHeight, constraints.maxHeight)
    val placeable = measurable.measure(constraints.copy(minHeight = height, maxHeight = height))
    layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
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
    progress: () -> Float? = NoProgress,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A broadcast says so, where a track has its length.
                if (track.liveVideoId != null) {
                    LiveBadge()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = track.artistLine() + duration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
                contentDescription = if (isFavorite) tr("Убрать из любимых") else tr("В любимые"),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isFavorite) 1f else 0.6f)
            )
        }
    }
}

@Composable
private fun DownloadedTrackCard(
    track: FavoriteTrack,
    isSelected: Boolean = false,
    progress: () -> Float? = NoProgress,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    // The bin at the row's end; without it the row has nothing there (see [DownloadsScreen]).
    onDeleteDownload: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    // Picking tracks: whether this one is picked, shown by a mark in the bin's place; null
    // when nothing is being picked.
    checked: Boolean? = null,
    showDebugPercentage: Boolean = false,
    debugPercentage: Int? = null,
    position: GroupPosition = GroupPosition.Single,
    flat: Boolean = false
) {
    val downloading = track.downloadState == DownloadState.DOWNLOADING
    val trailing = downloading || checked != null || onDeleteDownload != null
    TrackRowFrame(
        position = position,
        // While tracks are picked the fill marks the picked ones; the playing one keeps its bars.
        isSelected = checked ?: isSelected,
        onClick = onClick,
        flat = flat,
        onLongClick = onLongClick
    ) {
        // Cached cover when the track is downloaded, so the row still shows artwork offline.
        TrackRowArtwork(track.displayArtworkUrl, isCurrent = isSelected, isPlaying = isPlaying)
        // Nothing at the end: the text stops as far from the edge as the cover starts.
        Column(modifier = Modifier.weight(1f).then(if (trailing) Modifier else Modifier.padding(end = 8.dp))) {
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
        when {
            downloading -> AppCircularProgress(
                progress = progress,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(36.dp)
            )
            checked != null -> Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (checked) tr("Выбран") else tr("Не выбран"),
                    tint = if (checked) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.5f)
                )
            }
            onDeleteDownload != null -> IconButton(onClick = onDeleteDownload) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = tr("Удалить"),
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
    // Null when the queue's order isn't the listener's to change; [queueNote] says why.
    onReorderQueue: ((Int, Int) -> Unit)?,
    onPlayTrackFromQueue: (SoundCloudTrack) -> Unit,
    queueNote: String? = null,
    isFavorite: Boolean,
    favoriteTrack: FavoriteTrack?,
    downloadState: DownloadState?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isLoading: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    // Read where it is shown, not here: see MusicScreen.
    positionMs: () -> Long,
    durationMs: Long,
    lyrics: List<LyricLine>?,
    video: com.example.myapplication.data.TrackVideo?,
    upcomingVideo: com.example.myapplication.data.TrackVideo?,
    livePosition: () -> Long,
    // The glow around a video or the cover, and how each is made; see [VideoBackdrop].
    videoGlow: Boolean = true,
    videoGlowStyle: com.example.myapplication.data.GlowStyle = com.example.myapplication.data.GlowStyle.Ambilight,
    coverGlowStyle: com.example.myapplication.data.GlowStyle = com.example.myapplication.data.GlowStyle.Ambilight,
    // Swiping the cover sideways: whether there is a track that way, and moving to it.
    hasNeighbourTrack: (next: Boolean) -> Boolean = { false },
    onSwipeTrack: (next: Boolean) -> Unit = {},
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavoriteClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    onDislike: (() -> Unit)? = null,
    // The track's effects, with the button for them; null while that button is off in settings.
    trackFx: com.example.myapplication.data.TrackFx? = null,
    onTrackFxChange: (com.example.myapplication.data.TrackFx) -> Unit = {},
    // The YouTube session, which a live stream's chat reads and writes as.
    ytAuth: () -> com.example.myapplication.data.YtAuth? = { null },
    onLongPressCover: () -> Unit,
    onArtistClick: (SoundCloudUser) -> Unit,
    // The title: the album the track is from, where there is one to open.
    onAlbumClick: ((SoundCloudTrack) -> Unit)? = null,
    // Whether the player has been pulled off its place: the screen keeps the mini player ready
    // under it meanwhile, so a pull that folds the player away ends on it.
    onPulledChange: (Boolean) -> Unit = {},
    // Whether a finger is on the player: the screen draws what lies under it again, ready for a pull.
    onTouchedChange: (Boolean) -> Unit = {}
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
    // The queue: a sheet from below that follows the finger both ways and settles on a spring.
    // 1: out of sight, 0: all the way up.
    val queueHidden = remember { Animatable(1f) }
    val queueScope = rememberCoroutineScope()
    var queueHeightPx by remember { mutableFloatStateOf(1f) }
    val showQueue by remember { androidx.compose.runtime.derivedStateOf { queueHidden.value < 1f } }
    val flingPx = with(LocalDensity.current) { QueueFlingVelocity.toPx() }
    // Moves the sheet by [dy] pixels (down is positive); what it could take of them.
    val dragQueue: (Float) -> Float = { dy ->
        val before = queueHidden.value
        val after = (before + dy / queueHeightPx).coerceIn(0f, 1f)
        // Undispatched: moved in this very frame, not the next, or it trails the finger.
        queueScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { queueHidden.snapTo(after) }
        (after - before) * queueHeightPx
    }
    // Where the sheet was when the finger took it: a fifth of the way from there is enough.
    var queueDragFrom by remember { mutableFloatStateOf(1f) }
    // Let go: a fling decides, else how far it came; the fling's speed carries into the spring.
    val releaseQueue: (Float) -> Unit = { velocity ->
        val open = when {
            kotlin.math.abs(velocity) > flingPx -> velocity < 0f
            queueDragFrom > 0.5f -> queueHidden.value < 1f - QueueMeantFraction
            else -> queueHidden.value < QueueMeantFraction
        }
        queueScope.launch {
            queueHidden.animateTo(if (open) 0f else 1f, QueueSpring, initialVelocity = velocity / queueHeightPx)
        }
    }
    val setQueueOpen: (Boolean) -> Unit = { open ->
        queueScope.launch { queueHidden.animateTo(if (open) 0f else 1f, QueueSpring) }
    }
    // Pulled down, the whole player follows the finger — shrinking back, its corners rounding,
    // the screen it was opened from showing above it — and, let go far or fast enough, folds
    // away into the mini player; otherwise it springs back.
    val collapse = remember { Animatable(0f) }
    var playerHeightPx by remember { mutableFloatStateOf(1f) }
    val currentOnBack by rememberUpdatedState(onBack)
    val collapseProgress: () -> Float = { (collapse.value / playerHeightPx).coerceIn(0f, 1f) }
    val currentOnPulledChange by rememberUpdatedState(onPulledChange)
    LaunchedEffect(collapse) {
        snapshotFlow { collapse.value > 0f }.collect { currentOnPulledChange(it) }
    }
    val currentOnTouchedChange by rememberUpdatedState(onTouchedChange)
    DisposableEffect(Unit) {
        onDispose {
            currentOnPulledChange(false)
            currentOnTouchedChange(false)
        }
    }
    val releaseCollapse: (Float) -> Unit = { velocity ->
        val away = if (kotlin.math.abs(velocity) > flingPx) velocity > 0f else collapseProgress() > CollapseMeantFraction
        queueScope.launch {
            if (away) {
                // On at the finger's own speed: a fixed curve that set off slowly made a flick
                // stall the moment it was let go, and then the player dropped away.
                collapse.animateTo(playerHeightPx, CollapseSpring, initialVelocity = velocity)
                currentOnBack()
            } else {
                collapse.animateTo(0f, QueueSpring, initialVelocity = velocity)
            }
        }
    }
    var showLyrics by remember(track.id) { mutableStateOf(false) }
    // A broadcast has no lyrics; its chat is shown in their place.
    val liveId = track.liveVideoId
    val liveChat = remember(liveId) { liveId?.let { com.example.myapplication.data.YouTubeLiveChat(it, ytAuth) } }
    // A message being written: the field floats over the keyboard, the panel stays put.
    var chatComposing by remember(liveId) { mutableStateOf(false) }
    val lyricsShown = showLyrics && (!lyrics.isNullOrEmpty() || liveId != null)
    val atStart by remember(positionMs) { androidx.compose.runtime.derivedStateOf { positionMs() == 0L } }
    val showLoading = (downloadState != DownloadState.DOWNLOADED) &&
        (isBuffering || isLoading || (atStart && !isPlaying))
    // Nothing is blurred under the queue any more: a scrim that follows the sheet dims the player,
    // and a full-screen blur changing on every frame of a drag cost more than it gave.
    val blurRadius = 0.dp
    // Lyrics take the cover's place: it blurs into a backdrop behind them.
    // Held, not read: the blurs read them as they draw (see lightBlur), so that while they move
    // only the blur is drawn again, not the player composed again on every frame.
    val coverBlur = animateDpAsState(
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
    // A video in the cover's place fills the whole background with copies of itself — unless
    // that glow is turned off, and the video stands on the player's own background.
    val coverVideo = videoState?.takeIf { !it.isPortrait }
    val backdropVideo = coverVideo?.takeIf { videoGlow }
    // Paused, a video blurs, as if it had stopped to wait.
    val pauseBlur = animateDpAsState(
        targetValue = if (!isPlaying && videoState?.showing == true) 24.dp else 0.dp,
        animationSpec = tween(durationMillis = 400),
        label = "videoPauseBlur"
    )
    // Turned sideways with a video playing: the video alone, on the whole screen.
    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Without a video the cover gets the same treatment: whole, between the buttons and the
    // panel, its glow filling the screen around it and the panel frosted over it.
    val coverGlowMode = videoGlow && !landscape
    val coverGlow = rememberCoverGlow()

    // Swiping the cover (or the video) sideways moves to the track next to this one. While it is
    // dragged it shrinks back from the edges, its glow goes out and the panel turns solid; it
    // follows the finger and, let go far or fast enough, goes, and the next one comes in.
    val swipe = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val currentTrackId by rememberUpdatedState(track.id)
    val currentHasNeighbour by rememberUpdatedState(hasNeighbourTrack)
    val currentOnSwipeTrack by rememberUpdatedState(onSwipeTrack)
    // The moment a drag starts the glow goes out, the panel turns solid and the picture shrinks
    // back — not by how far it has been dragged; they return once it has settled.
    val swipeActive = remember { Animatable(0f) }
    val swipeProgress: () -> Float = { swipeActive.value }
    // Whether the picture is swiped aside at all: only changes at the start and the end of a swipe.
    val swiping by remember { derivedStateOf { swipeActive.value > 0f } }

    if (landscape && videoState != null && videoState.showing) {
        // Pulled down, the video folds away into the mini player, as the player does upright; the
        // controls' taps and the seek bar's drags are theirs still.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { playerHeightPx = it.height.toFloat() }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        currentOnTouchedChange(true)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                        currentOnTouchedChange(false)
                    }
                }
                .pointerInput(Unit) {
                    val velocity = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = {
                            velocity.resetTracking()
                            queueScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { collapse.stop() }
                        },
                        onVerticalDrag = { change, dy ->
                            velocity.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            queueScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                collapse.snapTo((collapse.value + dy).coerceAtLeast(0f))
                            }
                        },
                        onDragEnd = { releaseCollapse(velocity.calculateVelocity().y) },
                        onDragCancel = { releaseCollapse(0f) }
                    )
                }
                .drawBehind {
                    val pulled = collapseProgress()
                    if (pulled > 0f) drawRect(Color.Black.copy(alpha = PulledScrim * (1f - pulled)))
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val pulled = collapseProgress()
                        translationY = collapse.value
                        val scale = 1f - 0.08f * pulled
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        if (pulled > 0f) {
                            shape = RoundedCornerShape(PulledCorner * (pulled / 0.15f).coerceAtMost(1f))
                            clip = true
                        }
                        alpha = 1f - ((pulled - CollapseFadeFrom) / (1f - CollapseFadeFrom)).coerceIn(0f, 1f)
                    }
            ) {
                FullScreenVideo(state = videoState, blur = { pauseBlur.value }, glow = videoGlow, glowStyle = videoGlowStyle) {
                    LandscapeControls(
                        isPlaying = isPlaying,
                        live = track.liveVideoId != null,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        onTogglePlay = onTogglePlay,
                        onSeek = onSeek,
                        onPrevious = onPrevious,
                        onNext = onNext
                    )
                }
            }
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged {
                queueHeightPx = it.height * QueueHeightFraction
                playerHeightPx = it.height.toFloat()
            }
            // Watches for a finger without taking anything from what it touches.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentOnTouchedChange(true)
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    currentOnTouchedChange(false)
                }
            }
            // A swipe anywhere nothing else takes it: up pulls the queue after the finger, down
            // pulls the player itself down. The first movement decides which; a queue already
            // out is what a pull down moves. On a box that stays put, so that the player moving
            // under the finger doesn't eat into the finger's own movement.
            .pointerInput(Unit) {
                val velocity = VelocityTracker()
                var pulling = PlayerPull.Undecided
                detectVerticalDragGestures(
                    onDragStart = {
                        velocity.resetTracking()
                        queueDragFrom = queueHidden.value
                        pulling = when {
                            queueHidden.value < 1f -> PlayerPull.Queue
                            collapse.value > 0f -> PlayerPull.Player
                            else -> PlayerPull.Undecided
                        }
                        queueScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { queueHidden.stop() }
                        queueScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { collapse.stop() }
                    },
                    onVerticalDrag = { change, dy ->
                        velocity.addPosition(change.uptimeMillis, change.position)
                        if (pulling == PlayerPull.Undecided) {
                            pulling = if (dy < 0f) PlayerPull.Queue else PlayerPull.Player
                        }
                        change.consume()
                        when (pulling) {
                            PlayerPull.Queue -> dragQueue(dy)
                            PlayerPull.Player -> queueScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                collapse.snapTo((collapse.value + dy).coerceAtLeast(0f))
                            }
                            PlayerPull.Undecided -> Unit
                        }
                    },
                    onDragEnd = {
                        val speed = velocity.calculateVelocity().y
                        when (pulling) {
                            PlayerPull.Queue -> releaseQueue(speed)
                            PlayerPull.Player -> releaseCollapse(speed)
                            PlayerPull.Undecided -> Unit
                        }
                    },
                    onDragCancel = {
                        when (pulling) {
                            PlayerPull.Queue -> releaseQueue(0f)
                            PlayerPull.Player -> releaseCollapse(0f)
                            PlayerPull.Undecided -> Unit
                        }
                    }
                )
            }
            // The screen it was opened from, dimmed under the player as it starts coming down and
            // clearing as it goes: a card lifted off the screen, not a hole cut in it.
            .drawBehind {
                val pulled = collapseProgress()
                if (pulled > 0f) drawRect(Color.Black.copy(alpha = PulledScrim * (1f - pulled)))
            }
    ) {
    val glowSpace = remember { GlowSpace() }
    androidx.compose.runtime.CompositionLocalProvider(LocalGlowSpace provides glowSpace) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val pulled = collapseProgress()
                translationY = collapse.value
                // Narrowing to about the mini player's width by the bottom.
                val scale = 1f - 0.08f * pulled
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
                if (pulled > 0f) {
                    // Rounded almost as soon as it moves, as a card lifted off the screen.
                    shape = RoundedCornerShape(PulledCorner * (pulled / 0.15f).coerceAtMost(1f))
                    clip = true
                }
                // The last of the way down it fades into the mini player waiting under it.
                alpha = 1f - ((pulled - CollapseFadeFrom) / (1f - CollapseFadeFrom)).coerceIn(0f, 1f)
            }
            .glowSpace(glowSpace)
            // Drawn over the app's own screens, so the player owns its whole backdrop.
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (coverGlowMode) {
            VideoBackdrop(
                state = coverGlow,
                alpha = 1f,
                style = coverGlowStyle,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Under a video's glow, which is opaque, it isn't drawn at all: it shows
                        // only while that glow comes in, or goes out for a swipe. It was painted
                        // unseen on every frame, and again in all its steps each time the cover
                        // under the video settled back for a pause.
                        alpha = if (backdropVideo != null && videoShown >= 1f && swipeProgress() == 0f) {
                            0f
                        } else {
                            1f - swipeProgress()
                        }
                    }
                    // Under the lyrics the glow blurs with the cover, or its near steps would
                    // stay sharper than the cover they come from.
                    .lightBlur({ blurRadius + coverBlur.value }),
                held = { swiping }
            )
        }
        if (backdropVideo != null) {
            VideoBackdrop(
                state = backdropVideo,
                alpha = videoShown,
                style = videoGlowStyle,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - swipeProgress() }
                    // Paused, or under the lyrics, the glow blurs with the video, or its near steps
                    // would stay sharper than the video they come from.
                    .lightBlur({ blurRadius + pauseBlur.value + coverBlur.value }),
                held = { swiping }
            )
        }
        if (immersiveVideo != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = videoShown
                        swiped(swipe.value, swipeProgress(), rounded = false)
                    }
                    .lightBlur({ coverBlur.value + blurRadius + pauseBlur.value })
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
                landscape = landscape,
                artwork = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                val velocity = VelocityTracker()
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        velocity.resetTracking()
                                        swipeScope.launch { swipe.stop() }
                                        swipeScope.launch { swipeActive.animateTo(1f, tween(160)) }
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        velocity.addPosition(change.uptimeMillis, change.position)
                                        change.consume()
                                        val towardNext = swipe.value + amount < 0
                                        // No track that way: it gives, but grudgingly.
                                        val give = if (currentHasNeighbour(towardNext)) 1f else 0.3f
                                        swipeScope.launch { swipe.snapTo(swipe.value + amount * give) }
                                    },
                                    onDragCancel = {
                                        swipeScope.launch {
                                            swipe.animateTo(0f, SwipeSettle)
                                            swipeActive.animateTo(0f, tween(260))
                                        }
                                    },
                                    onDragEnd = {
                                        val offset = swipe.value
                                        val speed = velocity.calculateVelocity().x
                                        val width = size.width.toFloat()
                                        val next = offset < 0
                                        val meant = kotlin.math.abs(offset) > width * 0.25f ||
                                            (kotlin.math.abs(speed) > SwipeFlingVelocity.toPx() &&
                                                kotlin.math.sign(speed) == kotlin.math.sign(offset) &&
                                                kotlin.math.abs(offset) > SwipeMinFling.toPx())
                                        swipeScope.launch {
                                            if (!meant || !currentHasNeighbour(next)) {
                                                swipe.animateTo(0f, SwipeSettle)
                                                swipeActive.animateTo(0f, tween(260))
                                                return@launch
                                            }
                                            val leaving = currentTrackId
                                            val side = if (next) -1f else 1f
                                            swipe.animateTo(side * width, tween(170, easing = FastOutLinearInEasing))
                                            currentOnSwipeTrack(next)
                                            // The next track's cover comes in from the other side
                                            // once the player has moved to it.
                                            kotlinx.coroutines.withTimeoutOrNull(900) {
                                                androidx.compose.runtime.snapshotFlow { currentTrackId }.first { it != leaving }
                                            }
                                            swipe.snapTo(-side * width * 0.55f)
                                            swipe.animateTo(0f, SwipeSettle)
                                            swipeActive.animateTo(0f, tween(260))
                                        }
                                    }
                                )
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                // Over a vertical video the cover gives way to it once it plays.
                                .graphicsLayer { alpha = if (immersiveVideo != null) 1f - videoShown else 1f }
                                // Unbounded: cut at the edge of its box, which reaches under the
                                // panel, the blur left a hard line across the panel's top.
                                .lightBlur({ coverBlur.value + pauseBlur.value }, unbounded = true)
                        ) {
                            PlayerArtwork(
                                track = track,
                                isPlaying = isPlaying,
                                showLoading = showLoading,
                                vibrator = vibrator,
                                onLongPress = onLongPressCover,
                                video = videoState?.takeIf { immersiveVideo == null },
                                videoShown = videoShown,
                                coverGlow = coverGlow.takeIf { coverGlowMode },
                                swipeOffset = { swipe.value },
                                swipeProgress = swipeProgress
                            )
                        }
                        TextShade(shown = lyricsShown, chat = liveChat != null)
                        AnimatedVisibility(
                            visible = lyricsShown,
                            enter = fadeIn(animationSpec = tween(350)),
                            exit = fadeOut(animationSpec = tween(250)),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (liveChat != null) {
                                LiveChatOverlay(chat = liveChat, composing = chatComposing)
                            } else {
                                LyricsOverlay(
                                    lines = lyrics.orEmpty(),
                                    positionMs = positionMs,
                                    onSeek = onSeek
                                )
                            }
                        }
                    }
                },
                panel = {
                    PlayerPanel(
                        landscape = landscape,
                        glass = when {
                            immersiveVideo != null && videoShown > 0f -> {
                                {
                                    FrostedVideoGlass(
                                        state = immersiveVideo,
                                        tint = PanelColors.container.copy(alpha = 0.42f)
                                    )
                                }
                            }
                            // Over the copies of the video or the cover, already blurred: a tint makes
                            // the frost.
                            coverGlowMode || (backdropVideo != null && videoShown > 0f) -> {
                                {
                                    val frost = when {
                                        backdropVideo != null && videoShown > 0f -> 1f - 0.58f * videoShown
                                        // A cover's glow is as bright as the cover, often brighter than
                                        // a video's: a little more colour keeps the panel readable —
                                        // and in a light theme most of it, or the panel was the glow.
                                        else -> lightened(CoverFrost)
                                    }
                                    val panel = PanelColors.container
                                    Box(
                                        modifier = Modifier
                                            .matchParentSize()
                                            .drawBehind {
                                                drawRect(panel.copy(alpha = frost + (1f - frost) * swipeProgress()))
                                            }
                                    )
                                }
                            }
                            else -> null
                        },
                        track = track,
                        activeQueue = activeQueue,
                        isFavorite = isFavorite,
                        downloadState = downloadState,
                        isPlaying = isPlaying,
                        repeatMode = repeatMode,
                        shuffleEnabled = shuffleEnabled,
                        positionMs = positionMs,
                        livePosition = livePosition,
                        durationMs = durationMs,
                        vibrator = vibrator,
                        onTogglePlay = onTogglePlay,
                        onSeek = onSeek,
                        onFavoriteClick = onFavoriteClick,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onRepeat = onRepeat,
                        onShuffle = onShuffle,
                        onDislike = onDislike,
                        trackFx = trackFx,
                        onTrackFxChange = onTrackFxChange,
                        liveChat = liveChat,
                        onChatCompose = { chatComposing = true },
                        onArtistClick = onArtistClick,
                        onAlbumClick = onAlbumClick,
                        lyricsAvailable = !lyrics.isNullOrEmpty() || liveId != null,
                        lyricsShown = lyricsShown,
                        onToggleLyrics = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showLyrics = !showLyrics
                        },
                        onOpenQueue = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            setQueueOpen(true)
                        }
                    )
                }
            )
        }

        if (liveChat != null && chatComposing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                FloatingChatComposer(chat = liveChat, onDone = { chatComposing = false })
            }
        }

        if (showQueue) {
            // The player dims as the sheet comes up; a tap on it sends the sheet back down.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - queueHidden.value }
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) { detectTapGestures { setQueueOpen(false) } }
            )
            QueueManagerPanel(
                activeQueue = activeQueue,
                currentTrack = track,
                isPlaying = isPlaying,
                onDismiss = { setQueueOpen(false) },
                onReorder = onReorderQueue,
                note = queueNote,
                onPlayTrack = onPlayTrackFromQueue,
                sheetOffset = { queueHidden.value * queueHeightPx },
                onSheetDragStart = { queueDragFrom = queueHidden.value },
                onSheetDrag = dragQueue,
                onSheetRelease = releaseQueue,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(QueueHeightFraction)
                    .graphicsLayer { translationY = queueHidden.value * size.height }
            )
        }
    }
    }
    }
}

/** What a vertical drag on the player moves, decided by its first movement. */
private enum class PlayerPull { Undecided, Queue, Player }

// Pulled down this far (of its height), or flung, the player folds away; its corners round to this.
internal const val CollapseMeantFraction = 0.22f
internal val PulledCorner = 36.dp
// Folding away: no bounce, quick, and it takes up the fling's speed.
internal val CollapseSpring = spring<Float>(dampingRatio = 1f, stiffness = 900f, visibilityThreshold = 1f)
// How dark the screen behind is as the player starts coming down.
internal const val PulledScrim = 0.45f
// From this far down the player fades into the mini player.
internal const val CollapseFadeFrom = 0.7f

/**
 * Cover on top, panel at the bottom. The panel is measured first and keeps its natural height;
 * the cover takes whatever is left plus the overlap the panel's rounded top sits on, so tall
 * phones get a bigger cover and short ones never push the controls off screen.
 */
@Composable
private fun PlayerLayout(
    artwork: @Composable () -> Unit,
    panel: @Composable () -> Unit,
    // Sideways: the cover on the left, as tall as the screen, the panel beside it.
    landscape: Boolean = false
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
        if (landscape) {
            val artWidth = minOf(height, width / 2)
            val overlapPx = overlap.roundToPx()
            val artPlaceable = measurables[0].measure(Constraints.fixed(artWidth, height))
            val panelPlaceable = measurables[1].measure(Constraints.fixed(width - artWidth + overlapPx, height))
            return@Layout layout(width, height) {
                artPlaceable.place(0, 0)
                panelPlaceable.place(artWidth - overlapPx, 0)
            }
        }
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
    videoShown: Float = 0f,
    // The cover whole, its glow drawn from it: see [GlowingCover].
    coverGlow: CoverGlow? = null,
    // Where a sideways swipe has the picture, and how far into it the screen is.
    swipeOffset: () -> Float = { 0f },
    swipeProgress: () -> Float = { 0f }
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
            .background(
                if (coverGlow != null) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (video != null) 1f - videoShown else 1f)
                }
            )
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
        if (coverGlow != null) {
            // Paused, the cover settles back a little, as if set down.
            val restScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.9f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
                label = "coverRestScale"
            )
            GlowingCover(
                artworkUrl = track.artworkUrl,
                glow = coverGlow,
                scale = { restScale * pressScale.value },
                alpha = if (video != null) 1f - videoShown else 1f,
                swipeOffset = swipeOffset,
                swipeProgress = swipeProgress
            )
            if (video != null) {
                AmbientVideo(
                    state = video,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                    bottom = PlayerPanelOverlap + 36.dp,
                    overBackdrop = true,
                    alpha = videoShown,
                    edgeFade = { 1f - swipeProgress() },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { swiped(swipeOffset(), swipeProgress(), rounded = false) }
                )
            }
        } else Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    swiped(swipeOffset(), swipeProgress())
                    scaleX *= playScale * pressScale.value
                    scaleY *= playScale * pressScale.value
                }
        ) {
            AsyncImage(
                model = artworkUrlForSize(track.artworkUrl, 500.dp),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (video != null) 1f - videoShown else 1f },
                contentScale = ContentScale.Crop
            )
            // The music video fades in over the cover once it plays: from the line the buttons
            // over it start at, down to a little above the panel, glowing into the space around it.
            if (video != null) {
                AmbientVideo(
                    state = video,
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
                    bottom = PlayerPanelOverlap + 36.dp,
                    overBackdrop = true,
                    alpha = videoShown,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Keeps the status bar and the buttons over the cover legible on bright artwork. A video's
        // glow is left at its own brightness, and so is the cover's: the buttons over it are glass.
        if (coverGlow == null) Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .graphicsLayer { alpha = if (video != null) 1f - videoShown else 1f }
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

/**
 * The cover whole: a square as large as fits between the buttons at the top and the panel, its
 * picture recorded as [glow] for the glow that fills the rest of the screen.
 */
@Composable
private fun GlowingCover(
    artworkUrl: String?,
    glow: CoverGlow,
    scale: () -> Float,
    alpha: Float,
    swipeOffset: () -> Float,
    swipeProgress: () -> Float
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val top = statusBar + CoverTopClearance
        val bottom = PlayerPanelOverlap + CoverBottomGap
        val room = maxHeight - top - bottom
        val side = minOf(room, maxWidth - CoverSideMargin * 2).coerceAtLeast(96.dp)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = top + ((room - side) / 2).coerceAtLeast(0.dp))
                .size(side)
                // Swiped, it goes with the finger; the glow, out meanwhile, is drawn from where
                // it is.
                .graphicsLayer { swiped(swipeOffset(), swipeProgress(), rounded = false) }
                .glowSource(glow, LocalGlowSpace.current)
                .graphicsLayer {
                    val s = scale()
                    scaleX = s
                    scaleY = s
                    this.alpha = alpha
                    shape = RoundedCornerShape(CoverCorner)
                    clip = true
                }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = artworkUrlForSize(artworkUrl, 500.dp),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// From the buttons' line at the top — they lie over its corners, as glass — to the panel's edge,
// and from one side of the screen to the other if the height allows.
private val CoverTopClearance = 12.dp
private val CoverBottomGap = 0.dp
private val CoverSideMargin = 0.dp
private val CoverCorner = 28.dp

// How much of the panel's colour frosts it over the cover's glow.
private const val CoverFrost = 0.55f

// The cover swiped sideways: how far it goes before it has shrunk all it will, and what a flick is.
private val SwipeFlingVelocity = 900.dp
private val SwipeMinFling = 24.dp
private val SwipeSettle = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

/** The artwork (or a video) as the swipe has it: along with the finger, shrunk back, rounded. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.swiped(offset: Float, progress: Float, rounded: Boolean = true) {
    translationX = offset
    val s = 1f - 0.14f * progress
    scaleX = s
    scaleY = s
    if (rounded && progress > 0f) {
        shape = RoundedCornerShape(32.dp * progress)
        clip = true
    }
}

/**
 * The colour block: title, artists, seek bar, transport and what plays next. With [glass] it
 * stands on that (frosted video) instead of its solid colour.
 */
@Composable
private fun PlayerPanel(
    glass: (@Composable BoxScope.() -> Unit)? = null,
    landscape: Boolean = false,
    track: SoundCloudTrack,
    activeQueue: List<SoundCloudTrack>,
    isFavorite: Boolean,
    downloadState: DownloadState?,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    positionMs: () -> Long,
    // Where the player is this very frame (see MusicPlayer.livePositionMs), for the bar to follow.
    livePosition: () -> Long,
    durationMs: Long,
    vibrator: android.os.Vibrator?,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onFavoriteClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
    onShuffle: () -> Unit,
    // In the shuffle's place when there is one: "not for me", while the wave plays.
    onDislike: (() -> Unit)? = null,
    trackFx: com.example.myapplication.data.TrackFx? = null,
    onTrackFxChange: (com.example.myapplication.data.TrackFx) -> Unit = {},
    // A broadcast's chat: its switch in the repeat button's place, and its field over "Далее".
    liveChat: com.example.myapplication.data.YouTubeLiveChat? = null,
    onChatCompose: () -> Unit = {},
    onArtistClick: (SoundCloudUser) -> Unit,
    onAlbumClick: ((SoundCloudTrack) -> Unit)? = null,
    lyricsAvailable: Boolean,
    lyricsShown: Boolean,
    onToggleLyrics: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val onPanel = PanelColors.content

    Surface(
        modifier = if (landscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
        // Rounded toward the cover: above it upright, beside it sideways.
        shape = if (landscape) {
            RoundedCornerShape(topStart = 44.dp, bottomStart = 44.dp)
        } else {
            RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp)
        },
        color = if (glass != null) Color.Transparent else PanelColors.container,
        contentColor = onPanel
    ) {
        BoxWithConstraints {
            glass?.invoke(this)
            // Sideways the panel is wide and short: its content keeps to a phone's width in the
            // middle, centred up and down, set tighter, and never scrolls — on a phone too short
            // even for that, it is drawn a little smaller instead.
            ScaleDownToFit(
                enabled = landscape,
                modifier = if (landscape) {
                    Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 560.dp)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                } else {
                    Modifier
                }
            ) {
            Column(
                modifier = if (landscape) {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 16.dp)
                },
                verticalArrangement = Arrangement.Top
            ) {
                // A track added from the phone's own files is from no service, and its artist is only
                // a name in the file: there is no page to go to.
                val fromPhone = track.urn?.startsWith("local:") == true
                val live = track.liveVideoId != null
                var fxOpen by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                // Gives way first when the row is full: the session's chip and the effects stay.
                Box(modifier = Modifier.weight(1f)) {
                OnPanelChip(
                    text = buildString {
                        append(
                            when {
                                fromPhone -> tr("С телефона")
                                live -> tr("YouTube · в эфире")
                                track.urn?.startsWith("yandex:") == true -> tr("Яндекс Музыка")
                                track.youTubeVideoId != null -> "YouTube Music"
                                else -> "SoundCloud"
                            }
                        )
                        if (!fromPhone && downloadState == DownloadState.DOWNLOADED) append(tr(" · на устройстве"))
                    }
                )
                }
                LocalTogether.current?.let { badge ->
                    Spacer(modifier = Modifier.width(8.dp))
                    OnPanelChip(text = badge.label, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = badge.onClick))
                }
                if (trackFx != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    TrackFxButton(fx = trackFx, live = live, color = onPanel, onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        fxOpen = true
                    })
                }
                }
                if (fxOpen && trackFx != null) {
                    TrackFxSheet(fx = trackFx, live = live, onChange = onTrackFxChange, onDismiss = { fxOpen = false })
                }
                Spacer(modifier = Modifier.height(if (landscape) 8.dp else 12.dp))
                // A track of Yandex Music or YouTube Music is from an album: its title opens it.
                val hasAlbum = onAlbumClick != null && !live && !fromPhone &&
                    (track.urn?.startsWith("yandex:track:") == true || track.youTubeVideoId != null)
                Text(
                    text = track.title ?: "Unknown Track",
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = if (landscape) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (hasAlbum) {
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onAlbumClick?.invoke(track)
                            }
                    } else {
                        Modifier
                    }
                )
                Spacer(modifier = Modifier.height(if (landscape) 6.dp else 10.dp))

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
                            enabled = !fromPhone,
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

                Spacer(modifier = Modifier.height(if (landscape) 6.dp else 14.dp))
                // A broadcast has no length to seek along: in the bar's place, that it is on air.
                if (live) {
                    LiveMark(color = onPanel)
                } else {
                    PlayerSeekBar(
                        trackId = track.id,
                        positionMs = positionMs,
                        livePosition = livePosition,
                        isPlaying = isPlaying,
                        durationMs = durationMs,
                        vibrator = vibrator,
                        onSeek = onSeek
                    )
                }

                Spacer(modifier = Modifier.height(if (landscape) 4.dp else 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PanelIconButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) tr("Убрать из любимых") else tr("В любимые"),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onFavoriteClick()
                        },
                        selected = isFavorite,
                        size = 48.dp
                    )
                    PanelIconButton(
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = tr("Предыдущий трек"),
                        onClick = onPrevious,
                        size = 68.dp,
                        iconSize = 32.dp
                    )
                    PanelPlayButton(isPlaying = isPlaying, onClick = onTogglePlay)
                    PanelIconButton(
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = tr("Следующий трек"),
                        onClick = onNext,
                        size = 68.dp,
                        iconSize = 32.dp
                    )
                    if (onDislike != null) {
                        PanelIconButton(
                            icon = Icons.Rounded.HeartBroken,
                            contentDescription = tr("Не нравится"),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDislike()
                            },
                            size = 48.dp
                        )
                    } else {
                        PanelIconButton(
                            icon = Icons.Rounded.Shuffle,
                            contentDescription = if (shuffleEnabled) tr("Перемешивание включено") else tr("Перемешать"),
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onShuffle()
                            },
                            selected = shuffleEnabled,
                            size = 48.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (landscape) 10.dp else 18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val currentIndex = activeQueue.indexOfFirst { it.id == track.id }
                    val nextTrack = if (currentIndex >= 0) activeQueue.getOrNull(currentIndex + 1) else null
                    if (liveChat != null && lyricsShown) {
                        ChatComposerButton(chat = liveChat, onCompose = onChatCompose, modifier = Modifier.weight(1f))
                    } else {
                        QueuePeek(
                            nextTrack = nextTrack,
                            queueSize = activeQueue.size,
                            chat = false,
                            // A broadcast's chat has a button of its own, beside this.
                            lyricsAvailable = lyricsAvailable && liveChat == null,
                            lyricsShown = lyricsShown,
                            onToggleLyrics = onToggleLyrics,
                            onClick = onOpenQueue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // A broadcast doesn't repeat: in the repeat button's place, its chat.
                    if (liveChat != null) PanelIconButton(
                        icon = Icons.Rounded.Forum,
                        contentDescription = if (lyricsShown) tr("Скрыть чат") else tr("Чат трансляции"),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleLyrics()
                        },
                        selected = lyricsShown,
                        size = 64.dp
                    ) else PanelIconButton(
                        icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> tr("Повтор трека")
                            Player.REPEAT_MODE_ALL -> tr("Повтор очереди")
                            else -> tr("Повтор выключен")
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
}

/**
 * Lays [content] out at its own height and, where that is more than there is room for, draws it
 * smaller, whole and centred, rather than cutting it off or making it scroll. [enabled] false: in
 * the ordinary way, as tall as it is.
 */
@Composable
private fun ScaleDownToFit(enabled: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        if (!enabled) {
            val placeable = measurables.first().measure(constraints)
            return@Layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        val placeable = measurables.first().measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        val height = constraints.maxHeight
        val scale = if (placeable.height > height) height.toFloat() / placeable.height else 1f
        val width = constraints.maxWidth
        layout(width, height) {
            placeable.placeWithLayer((width - placeable.width) / 2, (height - placeable.height) / 2) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.5f)
            }
        }
    }
}

/** Thick expressive seek bar with a tick every 2% of a manual drag. */
/**
 * The controls over a video on the whole screen, as YouTube has them: previous, play and next in
 * the middle, the time and a bar to seek along at the bottom, on a shade over the picture. A tap
 * brings them up, and while it plays they go again a few seconds after the last touch; a tap on
 * them puts them away at once. Everything on the screen is taken by the video meanwhile: taps
 * used to fall through the picture to the screens under the player.
 */
@Composable
private fun BoxScope.LandscapeControls(
    isPlaying: Boolean,
    live: Boolean,
    positionMs: () -> Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    var shown by remember { mutableStateOf(true) }
    // Bumped by every touch on the controls, so they stay while being used.
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(shown, touched, isPlaying) {
        if (shown && isPlaying) {
            delay(LANDSCAPE_CONTROLS_HIDE_MS)
            shown = false
        }
    }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(Unit) { detectTapGestures { shown = !shown } }
    )
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(260)),
        modifier = Modifier.matchParentSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .pointerInput(Unit) { detectTapGestures { shown = false } }
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp)
            ) {
                IconButton(
                    onClick = { touched++; haptic.performHapticFeedback(HapticFeedbackType.LongPress); onPrevious() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = tr("Предыдущий трек"), tint = Color.White, modifier = Modifier.size(40.dp))
                }
                IconButton(
                    onClick = { touched++; haptic.performHapticFeedback(HapticFeedbackType.LongPress); onTogglePlay() },
                    modifier = Modifier
                        .size(76.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) tr("Пауза") else tr("Играть"),
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }
                IconButton(
                    onClick = { touched++; haptic.performHapticFeedback(HapticFeedbackType.LongPress); onNext() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = tr("Следующий трек"), tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 16.dp)
            ) {
                if (live) {
                    LiveMark(color = Color.White)
                } else {
                    // The time as the bar draws it, and the bar itself: only these follow the
                    // track, while the controls are up.
                    var dragged by remember { mutableStateOf<Float?>(null) }
                    val duration = durationMs.coerceAtLeast(1L)
                    val position = dragged?.let { (it * duration).toLong() } ?: positionMs().coerceIn(0L, duration)
                    Text(
                        text = formatDuration(position) + " / " + formatDuration(durationMs),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                    Slider(
                        value = position.toFloat() / duration,
                        onValueChange = { touched++; dragged = it },
                        onValueChangeFinished = {
                            dragged?.let { onSeek((it * duration).toLong()) }
                            dragged = null
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = PanelColors.accent,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    }
}

private const val LANDSCAPE_CONTROLS_HIDE_MS = 3_000L

/** The button for a track's effects, across from where it is from: lit while it has any. */
@Composable
private fun TrackFxButton(fx: com.example.myapplication.data.TrackFx, live: Boolean, color: Color, onClick: () -> Unit) {
    // A broadcast's speed isn't changed: only its reverb counts.
    val speed = if (live) 1f else fx.speed
    val active = speed != 1f || fx.reverb > 0
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) PanelColors.accent else color.copy(alpha = 0.12f),
        contentColor = if (active) PanelColors.onAccent else color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = when {
                    speed != 1f -> "%.2f×".format(speed)
                    fx.reverb > 0 -> tr("Реверб")
                    else -> tr("Эффекты")
                },
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

/**
 * A track's effects: its speed (with presets for slowed and sped up), whether its pitch stays put,
 * and its reverb. Each change is heard at once and kept for the track.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackFxSheet(
    fx: com.example.myapplication.data.TrackFx,
    // A broadcast: no speed, it goes at its own pace.
    live: Boolean,
    onChange: (com.example.myapplication.data.TrackFx) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Эффекты трека"), style = MaterialTheme.typography.titleLarge)
                    Text(
                        tr("Сохраняются для этого трека"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!fx.isDefault) TextButton(onClick = { onChange(com.example.myapplication.data.TrackFx()) }) { Text(tr("Сбросить")) }
            }

            if (live) {
                Text(
                    tr("Эфир идёт в реальном времени: его скорость не меняется"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Скорость"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("%.2f×".format(fx.speed), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = fx.speed,
                onValueChange = { onChange(fx.copy(speed = (Math.round(it * 20f) / 20f))) },
                valueRange = com.example.myapplication.data.TrackFx.MIN_SPEED..com.example.myapplication.data.TrackFx.MAX_SPEED,
                steps = 19
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.8f to tr("Замедлить"), 1f to tr("Обычная"), 1.25f to tr("Ускорить")).forEach { (speed, name) ->
                    FilterChip(
                        selected = fx.speed == speed,
                        onClick = { onChange(fx.copy(speed = speed)) },
                        label = { Text(if (speed == 1f) name else "$name ${"%.2f".format(speed).trimEnd('0').trimEnd('.')}×") }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Сохранять тон"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Без этого голос ниже в замедлении и выше в ускорении, как у пластинки"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = fx.keepPitch, onCheckedChange = { onChange(fx.copy(keepPitch = it)) })
            }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr("Реверб"), style = MaterialTheme.typography.titleMedium)
                    Text(
                        tr("Эхо большого зала"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = fx.reverb > 0,
                    onCheckedChange = { onChange(fx.copy(reverb = if (it) DEFAULT_REVERB else 0)) }
                )
            }
            if (fx.reverb > 0) {
                Slider(
                    value = fx.reverb.toFloat(),
                    onValueChange = { onChange(fx.copy(reverb = it.roundToInt().coerceIn(5, 100))) },
                    valueRange = 5f..100f
                )
            }
        }
    }
}

private const val DEFAULT_REVERB = 40

/** "В ЭФИРЕ" on red: marks a live stream in a list of tracks. */
@Composable
private fun LiveBadge() {
    Text(
        text = tr("В ЭФИРЕ"),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .background(Color(0xFFE5322D), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** "В эфире", with a red dot: a live stream's place for the seek bar, as tall as it. */
@Composable
private fun LiveMark(color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color(0xFFFF3B30), CircleShape)
        )
        Text(
            text = tr("В эфире"),
            style = MaterialTheme.typography.titleSmall,
            color = color
        )
    }
}

/**
 * The player's bar and its times. While the track plays the bar follows the player on every tick
 * (see [rememberPlaybackTick]), set on the slider's own state rather than composed: only the bar
 * is laid out and drawn again, on its own layer. Paused, it keeps to the last sample and to seeks.
 * The times under it change once a second, as they read. Dragged, it is the finger's until let go.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSeekBar(
    trackId: Long,
    positionMs: () -> Long,
    livePosition: () -> Long,
    isPlaying: Boolean,
    durationMs: Long,
    vibrator: android.os.Vibrator?,
    onSeek: (Long) -> Unit
) {
    val onPanel = PanelColors.content
    val interactionSource = remember { MutableInteractionSource() }
    val colors = SliderDefaults.colors(
        thumbColor = PanelColors.accent,
        activeTrackColor = PanelColors.accent,
        inactiveTrackColor = onPanel.copy(alpha = 0.18f),
        activeTickColor = PanelColors.onAccent,
        inactiveTickColor = PanelColors.accent
    )
    val currentDuration by rememberUpdatedState(durationMs)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentSampled by rememberUpdatedState(positionMs)
    val currentLive by rememberUpdatedState(livePosition)
    fun ratioOf(ms: Long): Float {
        val duration = currentDuration
        return if (duration > 0) ms.coerceIn(0L, duration).toFloat() / duration.toFloat() else 0f
    }
    var dragging by remember(trackId) { mutableStateOf(false) }
    val lastVibrated = remember(trackId) { floatArrayOf(0f) }
    val state = remember(trackId) { SliderState(value = ratioOf(currentSampled())) }
    state.onValueChange = { ratio ->
        dragging = true
        state.value = ratio
        if (kotlin.math.abs(ratio - lastVibrated[0]) >= 0.02f) {
            try {
                vibrator?.vibrate(android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK))
            } catch (e: Exception) {
                // No vibrator, no tick.
            }
            lastVibrated[0] = ratio
        }
    }
    state.onValueChangeFinished = {
        currentOnSeek((state.value * currentDuration).toLong())
        dragging = false
    }
    val tick = rememberPlaybackTick(running = isPlaying && !dragging)
    LaunchedEffect(state, isPlaying, dragging, durationMs) {
        if (dragging) return@LaunchedEffect
        if (isPlaying) {
            snapshotFlow { tick.longValue }.collect { state.value = ratioOf(currentLive()) }
        } else {
            snapshotFlow { currentSampled() }.collect { state.value = ratioOf(it) }
        }
    }

    Slider(
        state = state,
        colors = colors,
        interactionSource = interactionSource,
        thumb = { sliderState ->
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                sliderState = sliderState,
                colors = colors,
                thumbSize = DpSize(5.dp, 44.dp)
            )
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                trackCornerSize = 8.dp,
                modifier = Modifier.height(16.dp),
                colors = colors,
                thumbTrackGapSize = 6.dp
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Whole seconds: composed again once a second, not with every sample.
        val seconds by remember(state) {
            derivedStateOf {
                if (dragging) (state.value * currentDuration).toLong() / 1000L else currentSampled() / 1000L
            }
        }
        Text(
            formatDuration(seconds * 1000L),
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
                contentDescription = if (isPlaying) tr("Пауза") else tr("Играть"),
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
    // A broadcast: the switch is for its chat.
    chat: Boolean,
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
                    text = if (nextTrack != null) tr("Далее") else tr("Очередь"),
                    color = onPanel.copy(alpha = 0.72f)
                )
                Text(
                    text = nextTrack?.title ?: plural(queueSize, tr("трек"), tr("трека"), tr("треков")),
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
                            if (chat) Icons.Rounded.Forum else Icons.Default.Lyrics,
                            contentDescription = when {
                                chat -> if (lyricsShown) tr("Скрыть чат") else tr("Чат трансляции")
                                else -> if (lyricsShown) tr("Скрыть текст") else tr("Текст песни")
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = tr("Открыть очередь"))
            }
        }
    }
}

/**
 * A live stream's chat over its blurred picture, where a track's lyrics go, and faded at both ends
 * as they are: newest at the bottom, over the panel's edge, the list staying on it unless scrolled
 * back. YouTube's own emoji are their pictures, inline. Read only while shown: opening it starts
 * the reading, closing it (or the app going to the background) stops it.
 *
 * Kept light for a weak phone: the fade is one mask over the whole list, drawn again only when a
 * message comes (every few seconds at most); no animation per message; emoji pictures decoded at
 * the size of a line of text, and only the lines on screen composed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveChatOverlay(chat: com.example.myapplication.data.YouTubeLiveChat, composing: Boolean = false) {
    val state = remember(chat) {
        chat.messages
            .map<List<com.example.myapplication.data.LiveChatMessage>, LiveChatState> { LiveChatState.Messages(it) }
            .catch { emit(LiveChatState.Failed(it.message ?: tr("Чат недоступен"))) }
    }.collectAsStateWithLifecycle(initialValue = LiveChatState.Loading).value
    val onPanel = PanelColors.content
    val accent = PanelColors.accent
    val anchor = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp
    val ground = MaterialTheme.colorScheme.background
    // While a message is written the newest lines rise over the field above the keyboard: by the
    // keyboard's final height, read once as it starts to open rather than on every frame of it.
    val density = LocalDensity.current
    val keyboard = WindowInsets.imeAnimationTarget.getBottom(density)
    val screenHeight = LocalWindowInfo.current.containerSize.height
    var overlayBottom by remember { mutableIntStateOf(0) }
    val liftPx = if (composing && keyboard > 0) {
        val fieldTop = screenHeight - keyboard - with(density) { (ComposerBarHeight + 16.dp).roundToPx() }
        (overlayBottom - fieldTop).coerceAtLeast(0)
    } else {
        0
    }
    val lift = with(density) { liftPx.toDp() }
    val listState = rememberLazyListState()
    // It stands on the player's shade (see TextShade), the same however many messages there are.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayBottom = it.boundsInWindow().bottom.toInt() }
    ) {
        when (state) {
            LiveChatState.Loading -> Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(32.dp), color = accent, strokeWidth = 3.dp)
                Text(tr("Подключаюсь к чату…"), style = MaterialTheme.typography.bodyMedium, color = onPanel.copy(alpha = 0.8f))
            }
            is LiveChatState.Failed -> Text(
                text = state.reason,
                style = MaterialTheme.typography.bodyLarge,
                color = onPanel.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp)
            )
            is LiveChatState.Messages -> if (state.list.isEmpty()) {
                Text(
                    tr("В чате пока тихо"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = onPanel.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // Upside down: the newest is item 0, at the bottom, where the list rests.
                val newestFirst = remember(state.list) { state.list.asReversed() }
                val scope = rememberCoroutineScope()
                // Following the newest, as YouTube's chat does: settled at the bottom, the list
                // keeps to each new message; read back, it stays put, then returns by itself a few
                // seconds after the last touch. Decided only when a scroll ends.
                var following by remember { mutableStateOf(true) }
                var settled by remember { mutableIntStateOf(0) }
                LaunchedEffect(listState) {
                    androidx.compose.runtime.snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                        if (!scrolling) {
                            following = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24
                            settled++
                        }
                    }
                }
                val newestId = newestFirst.firstOrNull()?.id
                LaunchedEffect(newestId) {
                    if (following && !listState.isScrollInProgress) listState.scrollToItem(0)
                }
                LaunchedEffect(following, settled) {
                    if (!following) {
                        delay(CHAT_RETURN_MS)
                        if (!listState.isScrollInProgress) listState.animateScrollToItem(0)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        // Faded at both ends, as the lyrics are: the top over the status bar's
                        // way, the bottom just short of the panel, the newest above it clear.
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val h = size.height
                            val topClear = (anchor - 16.dp).toPx().coerceAtLeast(0f)
                            val topSolid = topClear + LyricsFade.toPx()
                            val bottomClear = h - PlayerPanelOverlap.toPx() - liftPx
                            val bottomSolid = (bottomClear - ChatBottomFade.toPx()).coerceAtLeast(topSolid)
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
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 24.dp,
                        end = 24.dp,
                        top = anchor,
                        bottom = PlayerPanelOverlap + ChatBottomFade + lift
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(newestFirst, key = { it.id }) { message -> ChatLine(message, accent, onPanel, ground) }
                }
                // Read back: the way to the newest, a tap away.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !following,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = PlayerPanelOverlap + 12.dp + lift)
                ) {
                    Surface(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        shape = CircleShape,
                        color = accent,
                        contentColor = PanelColors.onAccent,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp))
                            Text(tr("Новые сообщения"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

// How long after the last touch a chat read back returns to the newest by itself.
private const val CHAT_RETURN_MS = 4_000L

/** A message: its author in the accent, a paid one's amount, then the words and emoji pictures. */
@Composable
private fun ChatLine(message: com.example.myapplication.data.LiveChatMessage, accent: Color, onPanel: Color, ground: Color) {
    val emojis = message.parts.filterIsInstance<com.example.myapplication.data.LiveChatPart.Emoji>()
    val text = remember(message) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                append(message.author.removePrefix("@"))
            }
            message.paid?.let { amount -> withStyle(SpanStyle(color = accent)) { append("  $amount") } }
            append("  ")
            withStyle(SpanStyle(color = onPanel)) {
                message.parts.forEach { part ->
                    when (part) {
                        is com.example.myapplication.data.LiveChatPart.Text -> append(part.text)
                        is com.example.myapplication.data.LiveChatPart.Emoji -> appendInlineContent(part.url, part.key)
                    }
                }
            }
        }
    }
    val inline = remember(message) {
        emojis.distinctBy { it.url }.associate { emoji ->
            emoji.url to androidx.compose.foundation.text.InlineTextContent(
                androidx.compose.ui.text.Placeholder(
                    width = 1.35.em,
                    height = 1.35.em,
                    placeholderVerticalAlign = androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter
                )
            ) {
                AsyncImage(model = emoji.url, contentDescription = emoji.key, modifier = Modifier.fillMaxSize())
            }
        }
    }
    // A soft shadow of the theme's ground around the letters, so they stand off any picture.
    val style = MaterialTheme.typography.bodyLarge.copy(
        shadow = androidx.compose.ui.graphics.Shadow(color = ground.copy(alpha = 0.9f), blurRadius = 8f)
    )
    Text(text = text, inlineContent = inline, style = style)
}

private sealed interface LiveChatState {
    data object Loading : LiveChatState
    data class Failed(val reason: String) : LiveChatState
    data class Messages(val list: List<com.example.myapplication.data.LiveChatMessage>) : LiveChatState
}

// The bottom fade of the chat: short, so the newest message is read whole just above the panel.
private val ChatBottomFade = 18.dp

/**
 * In place of "Далее" while a live chat is open: what a message would be written in. A tap opens
 * the field over the keyboard ([FloatingChatComposer]); where no message can be sent, it says why,
 * in YouTube's words.
 */
@Composable
private fun ChatComposerButton(
    chat: com.example.myapplication.data.YouTubeLiveChat,
    onCompose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onPanel = PanelColors.content
    val canSend by chat.canSend.collectAsState()
    val blocked by chat.sendBlocked.collectAsState()
    val subscribersOnly by chat.subscribersOnly.collectAsState()
    val subscribed by chat.subscribed.collectAsState()
    val detail by chat.blockedDetail.collectAsState()
    val wrongSubscription by chat.wrongSubscription.collectAsState()
    var subscribing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Subscribed but not let in yet (YouTube may want the subscription a few minutes old): asked
    // again once a minute, only while the chat is open.
    val waiting = !canSend && subscribersOnly && subscribed == true
    // The subscription as YouTube holds it won't do: made again, it will.
    val resubscribe = !canSend && subscribed == true && wrongSubscription
    LaunchedEffect(waiting && !resubscribe) {
        while (waiting && !resubscribe) {
            delay(CHAT_RECHECK_MS)
            chat.recheck()
        }
    }
    val reason = when {
        canSend -> tr("Сообщение в чат…")
        subscribing -> tr("Оформляю подписку…")
        resubscribe -> tr("YouTube считает, что подписка оформлена на детском видео — оформим заново")
        waiting -> tr("Вы подписаны. ") + (detail ?: tr("YouTube пустит в чат чуть позже"))
        blocked != null -> blocked!!
        else -> tr("Подключаюсь к чату…")
    }
    Surface(
        // Blocked, a tap shows the reason whole, with YouTube's explanation.
        onClick = {
            if (canSend) {
                onCompose()
            } else {
                val full = listOfNotNull(blocked, detail).distinct().joinToString("\n").ifBlank { reason }
                android.widget.Toast.makeText(context, full, android.widget.Toast.LENGTH_LONG).show()
            }
        },
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(22.dp),
        color = onPanel.copy(alpha = 0.12f),
        contentColor = onPanel
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = reason,
                style = if (canSend) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                color = onPanel.copy(alpha = if (canSend) 0.6f else 0.75f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (canSend) Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
            // Only subscribers may write: subscribing is a tap away.
            if (!canSend && ((subscribersOnly && subscribed != true) || resubscribe)) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        subscribing = true
                        val again = resubscribe
                        scope.launch {
                            val done = chat.subscribe(again = again)
                            android.widget.Toast.makeText(
                                context,
                                when {
                                    !done -> tr("Не удалось подписаться")
                                    again -> tr("Подписка оформлена заново")
                                    else -> tr("Вы подписались на канал")
                                },
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            subscribing = false
                        }
                    },
                    enabled = !subscribing,
                    shape = RoundedCornerShape(16.dp),
                    color = PanelColors.accent,
                    contentColor = PanelColors.onAccent,
                    modifier = Modifier.height(40.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        if (subscribing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PanelColors.onAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (resubscribe) tr("Заново") else tr("Подписаться"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

// How often a chat the account is subscribed to but not yet let into is asked again.
private const val CHAT_RECHECK_MS = 60_000L

/**
 * The field a message to a live chat is written in: across the whole width, just over the
 * keyboard, while the player stays where it is and the chat above stays in sight. It goes when the
 * keyboard does.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FloatingChatComposer(
    chat: com.example.myapplication.data.YouTubeLiveChat,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val onPanel = PanelColors.content
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Put away with the keyboard, however it went (back, the keyboard's own button).
    val imeVisible = WindowInsets.isImeVisible
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) opened = true else if (opened) onDone()
    }
    BackHandler(onBack = onDone)
    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        sending = true
        scope.launch {
            if (chat.send(text)) {
                draft = ""
            } else {
                android.widget.Toast.makeText(context, tr("Сообщение не отправилось"), android.widget.Toast.LENGTH_SHORT).show()
            }
            sending = false
        }
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(ComposerBarHeight),
        shape = RoundedCornerShape(26.dp),
        color = PanelColors.container,
        contentColor = onPanel,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) {
                    Text(tr("Сообщение в чат…"), style = MaterialTheme.typography.bodyLarge, color = onPanel.copy(alpha = 0.55f))
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(200) },
                    enabled = !sending,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = onPanel),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(PanelColors.accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { send() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
            }
            val ready = draft.isNotBlank() && !sending
            Surface(
                onClick = { send() },
                enabled = ready,
                shape = CircleShape,
                color = if (ready) PanelColors.accent else onPanel.copy(alpha = 0.1f),
                contentColor = if (ready) PanelColors.onAccent else onPanel.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = tr("Отправить"), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

// The floating field's height, which the chat above keeps clear of.
private val ComposerBarHeight = 60.dp

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
    positionMs: () -> Long,
    onSeek: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    // The position arrives twice a second; leading it a little keeps lines from lighting late.
    // Derived, so the lyrics are composed again only when the line changes, not on every tick.
    val current by remember(lines, positionMs) {
        androidx.compose.runtime.derivedStateOf { lines.indexOfLast { it.timeMs <= positionMs() + 300 } }
    }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var lastUserScrollAt by remember { mutableStateOf(0L) }
    LaunchedEffect(isDragged) {
        if (isDragged || lastUserScrollAt != 0L) lastUserScrollAt = System.currentTimeMillis()
    }

    // Where the lit line's top sits: a little below the status bar. The list's top padding is
    // exactly this, so scrolling a line to the list's start puts it here.
    val anchor = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 24.dp

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
    // The lines stand on the player's shade (see TextShade).
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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

/**
 * The shade lyrics or a broadcast's chat stand on: the whole player dimmed evenly — the cover, the
 * glow around it, and on under the panel, whose glass shows it — fading in and out with the text.
 * It was only around the lines, so a lighter band stayed above the panel and in its corners.
 * Drawn from the cover's box past its edges to the player's: the box ends a little way under the
 * panel, and a shade stopping there drew a hard line across the glass. Thicker under a chat:
 * small text over whatever the broadcast shows. Only drawn again as it fades, never composed.
 */
@Composable
private fun TextShade(shown: Boolean, chat: Boolean) {
    val shade = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = if (shown) 350 else 250),
        label = "textShade"
    )
    val color = MaterialTheme.colorScheme.background.copy(alpha = if (chat) 0.62f else 0.38f)
    // The cover's box starts at the player's corner, upright or sideways: the window's size from
    // there reaches every edge, and the player cuts it to its own.
    val window = LocalWindowInfo.current.containerSize
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val amount = shade.value
                if (amount > 0f) {
                    drawRect(
                        color = color.copy(alpha = color.alpha * amount),
                        size = Size(
                            maxOf(size.width, window.width.toFloat()),
                            maxOf(size.height, window.height.toFloat())
                        )
                    )
                }
            }
    )
}

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
    // Null: the order can't be changed, no row is dragged.
    onReorder: ((Int, Int) -> Unit)?,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    // The sheet's own movement, done by the screen that holds it: how far down it is, moving it,
    // and letting it go with a speed.
    sheetOffset: () -> Float,
    onSheetDragStart: () -> Unit,
    onSheetDrag: (Float) -> Float,
    onSheetRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    // A line over the queue: whose order it is, when not the listener's.
    note: String? = null
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
                onReorder?.invoke(from, target.index)
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

    // The list at its top hands a pull downward to the sheet, which then follows the finger; a
    // pull back up takes the sheet up first before the list scrolls again.
    val sheetScroll = remember(onSheetDrag, onSheetRelease) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0f && sheetOffset() > 0f) return Offset(0f, onSheetDrag(available.y))
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    if (sheetOffset() == 0f) onSheetDragStart()
                    return Offset(0f, onSheetDrag(available.y))
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (sheetOffset() > 0f) {
                    onSheetRelease(available.y)
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

        Card(
            shape = AppShapes.bottomSheet,
            colors = CardDefaults.cardColors(
                // Nearly solid: the player behind is only dimmed now, not blurred.
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
            ),
            modifier = modifier
                // Taps on the sheet stay on it rather than reaching the scrim behind.
                .pointerInput(Unit) {}
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // The handle, and the whole strip around it, drags the sheet.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .pointerInput(Unit) {
                            val velocity = VelocityTracker()
                            detectVerticalDragGestures(
                                onDragStart = {
                                    velocity.resetTracking()
                                    onSheetDragStart()
                                },
                                onVerticalDrag = { change, dy ->
                                    velocity.addPosition(change.uptimeMillis, change.position)
                                    change.consume()
                                    onSheetDrag(dy)
                                },
                                onDragEnd = { onSheetRelease(velocity.calculateVelocity().y) },
                                onDragCancel = { onSheetRelease(0f) }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp, 5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    )
                }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(sheetScroll)
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (note != null) {
                    item(key = "queue-note") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Waves,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (activeQueue.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tr("Очередь пуста"),
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
                        // The whole row plays its track: only the words in it did, and a tap on
                        // the cover or beside the title missed.
                        onClick = {
                            if (!isCurrent && draggedIndex == null) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayTrack(trackItem)
                            }
                        },
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
                            .pointerInput(trackItem.id, onReorder != null) {
                                if (onReorder == null) return@pointerInput
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
                            if (onReorder != null) {
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
                            }

                            TrackArtwork(
                                artworkUrl = trackItem.artworkUrl,
                                size = 50.dp,
                                isPlaying = isCurrent && isPlaying
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
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

// The queue sheet: how much of the screen it takes, how fast a flick of it has to be to count,
// and how it settles — with the flick's speed carried in, a little give, no wobble.
private const val QueueHeightFraction = 0.9f
private const val QueueMeantFraction = 0.2f
internal val QueueFlingVelocity = 700.dp
internal val QueueSpring = spring<Float>(dampingRatio = 0.86f, stiffness = 360f)

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
    val stillShape = fallbackShape ?: RoundedCornerShape(if (size > 100.dp) 36.dp else 18.dp)
    // Read as the cover is drawn, not composed: its morph and its turn moved on every frame of the
    // screen, 120 a second on some, and each composed the row again. The turn is a loop clock's
    // (sixty a second at most), from nought whenever playing starts, as it was.
    val morphProgress = if (useMorphing) {
        animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            label = "morphProgress"
        )
    } else {
        null
    }
    val clock = rememberLoopClock(running = useMorphing && isPlaying)
    LaunchedEffect(isPlaying) { if (!isPlaying) clock.longValue = 0L }

    AsyncImage(
        model = artworkUrlForSize(artworkUrl, size),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val progress = morphProgress?.value ?: 0f
                // Not morphing — so don't pay for a morphing shape. MorphingArtworkShape builds a
                // 72-segment path each time, and with useMorphing defaulting to true that ran for
                // every row of every list, playing or not.
                shape = if (progress <= 0.001f) {
                    stillShape
                } else {
                    val phase = if (isPlaying) (clock.longValue % 16_000L) / 16_000f * (2f * Math.PI.toFloat()) else 0f
                    MorphingArtworkShape(progress, phase)
                }
                clip = true
            }
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
                    DownloadState.DOWNLOADED -> tr("Загружено")
                    DownloadState.DOWNLOADING -> tr("Загрузка")
                    DownloadState.FAILED -> tr("Ошибка")
                    DownloadState.NONE -> tr("Онлайн")
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    val glass = LocalGlass.current
    Surface(
        color = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .glassOr(MaterialTheme.shapes.extraLarge, MaterialTheme.colorScheme.surfaceContainerHighest)
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
 *
 * It follows the finger as the full player's cover does: swiped sideways its track slides with it
 * and, let go far or fast enough, gives way to the next one (to the left) or the previous one; swiped
 * up, it opens the player. The bar itself only moves while dragged — a translation, nothing redrawn.
 */
@Composable
internal fun PlayerBar(
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    // Read by the indicator as it draws: the bar isn't composed again as the track plays.
    progress: () -> Float,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
    // A sideways swipe: towards the next track (to the left) or the previous one. Null, no swipe.
    onSwipe: ((next: Boolean) -> Unit)? = null,
    // Whether there is a track that way; a swipe towards none gives, grudgingly, and springs back.
    canSwipe: (next: Boolean) -> Boolean = { true }
) {
    val haptic = LocalHapticFeedback.current
    val onPanel = PanelColors.content
    val glass = LocalGlass.current
    val tick = rememberPlaybackTick(running = isPlaying)
    val slide = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }
    val swipeScope = rememberCoroutineScope()
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val currentCanSwipe by rememberUpdatedState(canSwipe)
    val currentOnOpen by rememberUpdatedState(onOpen)
    Surface(
        onClick = onOpen,
        color = if (glass) Color.Transparent else PanelColors.container,
        contentColor = onPanel,
        shape = RoundedCornerShape(32.dp),
        // A shadow shows through glass as a smudge.
        shadowElevation = if (glass) 0.dp else 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = lift.value }
            .pointerInput(Unit) {
                val velocity = VelocityTracker()
                // Which way this drag goes, decided by its first movement: sideways the track,
                // upward the player.
                var sideways: Boolean? = null
                detectDragGestures(
                    onDragStart = {
                        velocity.resetTracking()
                        sideways = null
                    },
                    onDrag = { change, amount ->
                        velocity.addPosition(change.uptimeMillis, change.position)
                        change.consume()
                        val horizontal = sideways ?: (kotlin.math.abs(amount.x) >= kotlin.math.abs(amount.y)).also { sideways = it }
                        swipeScope.launch {
                            if (horizontal && currentOnSwipe != null) {
                                val next = slide.value + amount.x < 0
                                val give = if (currentCanSwipe(next)) 1f else 0.3f
                                slide.snapTo(slide.value + amount.x * give)
                            } else if (!horizontal) {
                                // Up follows the finger; down only a little, there is nowhere to go.
                                val y = lift.value + amount.y
                                lift.snapTo(if (y > 0f) y * 0.3f else y)
                            }
                        }
                    },
                    onDragCancel = {
                        swipeScope.launch { slide.animateTo(0f, SwipeSettle) }
                        swipeScope.launch { lift.animateTo(0f, SwipeSettle) }
                    },
                    onDragEnd = {
                        val speed = velocity.calculateVelocity()
                        val width = size.width.toFloat()
                        if (sideways == true) {
                            val offset = slide.value
                            val next = offset < 0
                            val meant = kotlin.math.abs(offset) > width * 0.25f ||
                                (kotlin.math.abs(speed.x) > SwipeFlingVelocity.toPx() &&
                                    kotlin.math.sign(speed.x) == kotlin.math.sign(offset) &&
                                    kotlin.math.abs(offset) > SwipeMinFling.toPx())
                            swipeScope.launch {
                                if (!meant || !currentCanSwipe(next) || currentOnSwipe == null) {
                                    slide.animateTo(0f, SwipeSettle)
                                    return@launch
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val side = if (next) -1f else 1f
                                slide.animateTo(side * width, tween(150, easing = FastOutLinearInEasing))
                                currentOnSwipe?.invoke(next)
                                // The next track comes in from the other side.
                                slide.snapTo(-side * width * 0.5f)
                                slide.animateTo(0f, SwipeSettle)
                            }
                        } else {
                            val opened = lift.value < -MiniPlayerOpenDistance.toPx() ||
                                speed.y < -SwipeFlingVelocity.toPx()
                            if (opened) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnOpen()
                            }
                            swipeScope.launch { lift.animateTo(0f, SwipeSettle) }
                        }
                    }
                )
            }
            .glassOr(RoundedCornerShape(32.dp), PanelColors.container)
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    translationX = slide.value
                    alpha = 1f - (kotlin.math.abs(slide.value) / size.width.coerceAtLeast(1f) * 0.8f).coerceIn(0f, 0.8f)
                }
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
                    text = artist.ifBlank { if (isPlaying) tr("Сейчас играет") else tr("На паузе") },
                    style = MaterialTheme.typography.labelMedium,
                    color = onPanel.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Waves while playing, lies flat while paused.
                LinearWavyProgressIndicator(
                    // Read as it draws, the tick with it: on its own layer, only the line redraws.
                    progress = {
                        tick.longValue
                        progress().coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .graphicsLayer { },
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
                        contentDescription = if (isPlaying) tr("Пауза") else tr("Играть"),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

// How far up the mini player has to be pulled for the player to open.
private val MiniPlayerOpenDistance = 48.dp

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
                    text = tr("Не указан SoundCloud client_id"),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tr("Нажмите, чтобы открыть настройки и ввести рабочий ключ, иначе поиск и воспроизведение работать не будут."),
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
                        text = tr("Сессия SoundCloud истекла"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = tr("Обновить её автоматически не вышло — войдите в аккаунт заново."),
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
                Text(tr("Войти заново"))
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
                        text = tr("SoundCloud client_id устарел"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr("Текущий ключ SoundCloud больше недействителен. Попробуйте обновить его автоматически или укажите рабочий вручную."),
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
                    Text(tr("Настройки"), color = MaterialTheme.colorScheme.error)
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
                    Text(tr("Обновить автоматически"))
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
                        text = tr("Эквалайзер"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = tr("Настройка звуковых частот и пресетов"),
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
                    text = tr("Пресеты"),
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
                    text = tr("Полосы частот"),
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
                    text = tr("Войдите в свой аккаунт, чтобы настроить приложение"),
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
                        text = tr("Авторизация в SoundCloud..."),
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
                    text = tr("%s треков", playlist.tracks.size),
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
    downloadProgress: DownloadProgressOf = NoDownloadProgress,
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

    val albumLibrary = LocalAlbumLibrary.current
    val tracks = playlist.tracks
    val isActive = currentTrackId != null && tracks.any { it.id == currentTrackId }
    val listState = rememberLazyListState()
    val collapsed = rememberCollapsed(listState, MixCoverHeight - 140.dp)
    var showMore by remember { mutableStateOf(false) }
    // Taking tracks out one by one: only then has each row its bin, and the toolbar says "Готово".
    var removing by remember(playlist.id) { mutableStateOf(false) }
    val download = downloadStep(playlist)
    val kicker = if (playlist.isLikedAlbum) tr("Альбом · в медиатеке") else tr("Плейлист")
    val context = LocalContext.current
    // A liked album's page on its service; a playlist made here, or a mix kept, has none.
    val link = remember(playlist.sourceKey) { SharedLinks.linkOfRef(playlist.sourceKey) }
    val artwork: @Composable BoxScope.() -> Unit = {
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
    }

    CollectionPage(
        onBack = onBack,
        page = {
            DetailFrame(
                listState = listState,
                // The last rows clear of the toolbar and the mini player over it.
                bottomPadding = FeedBottomRoom,
                header = {
                    CoverHeader(
                        artwork = artwork,
                        kicker = kicker,
                        title = playlist.name,
                        subtitle = playlist.artist?.takeIf { playlist.isLikedAlbum && it.isNotBlank() },
                        // The cover opens "Ещё", where changing it is.
                        onArtworkClick = { showMore = true }
                    )
                }
            ) {
                item(key = "playlist-count") {
                    CountRule(
                        text = listOfNotNull(
                            if (tracks.isEmpty()) tr("Нет треков") else plural(tracks.size, tr("трек"), tr("трека"), tr("треков")),
                            playlist.downloadedCount.takeIf { it > 0 }?.let { tr("%s на устройстве", it) }
                        ).joinToString(" · ")
                    )
                }

                if (tracks.isEmpty()) {
                    item(key = "playlist-empty") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                            EmptyState(tr("Здесь пока нет треков. Зажмите обложку трека в плеере, чтобы добавить его."))
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { _, track -> "playlist-${playlist.id}-${track.id}" }) { _, track ->
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            DownloadedTrackCard(
                                track = track,
                                isSelected = track.id == currentTrackId,
                                progress = { downloadProgress[track.id] },
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track) },
                                onDeleteDownload = if (removing) {
                                    { onRemoveTrack(track) }
                                } else {
                                    null
                                },
                                flat = true
                            )
                        }
                    }
                }
            }

            CollapsingTopBar(title = playlist.name, collapsed = collapsed)
        }
    ) {
        CollectionBar(
            step = when {
                removing -> CollectionStep(StepKind.Done)
                else -> download
            },
            onStep = {
                when {
                    removing -> removing = false
                    download?.kind == StepKind.Download -> onDownloadAll()
                    else -> showMore = true
                }
            },
            onMore = { showMore = true },
            labelled = !collapsed,
            isActive = isActive,
            isPlaying = isPlaying,
            onPlay = if (tracks.isEmpty()) null else {
                {
                    if (isActive && albumLibrary != null) albumLibrary.onTogglePlay() else onPlayTrack(tracks.first())
                }
            },
            onShuffle = if (tracks.size < 2) null else onShuffle
        )
    }

    if (showMore) {
        val hasDownloaded = tracks.any { it.downloadState == DownloadState.DOWNLOADED }
        CollectionSheet(
            artwork = artwork,
            kicker = kicker,
            title = playlist.name,
            subtitle = if (tracks.isEmpty()) tr("Нет треков") else plural(tracks.size, tr("трек"), tr("трека"), tr("треков")),
            tiles = listOfNotNull(
                download?.let { step ->
                    CollectionAction(
                        icon = if (step.kind == StepKind.Downloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                        label = stepLabel(step),
                        selected = step.kind == StepKind.Downloaded,
                        onClick = { if (step.kind == StepKind.Download) onDownloadAll() }
                    )
                },
                // Shuffling is on the toolbar too: sharing takes its tile where there is a link.
                link?.let { url -> CollectionAction(Icons.Default.Share, tr("Поделиться"), onClick = { shareText(context, url) }) }
                    ?: CollectionAction(Icons.Rounded.Shuffle, tr("Вперемешку"), onClick = onShuffle).takeIf { tracks.size >= 2 },
                CollectionAction(Icons.Default.Image, tr("Обложка"), onClick = { imagePicker.launch(arrayOf("image/*")) })
            ),
            rows = listOfNotNull(
                CollectionAction(
                    icon = Icons.Rounded.RemoveCircleOutline,
                    label = tr("Убрать треки по одному"),
                    subtitle = tr("У каждого трека появится корзина"),
                    onClick = { removing = true }
                ).takeIf { tracks.isNotEmpty() && !removing },
                CollectionAction(
                    icon = Icons.Default.Download,
                    label = tr("Переместить скачанные в «Скачанное»"),
                    onClick = onMoveDownloadedToDownloads
                ).takeIf { hasDownloaded && !playlist.isLikedAlbum },
                CollectionAction(
                    icon = Icons.Default.Delete,
                    label = if (playlist.isLikedAlbum) tr("Убрать из медиатеки") else tr("Удалить плейлист"),
                    subtitle = if (hasDownloaded) tr("Скачанные для него треки удалятся с телефона") else null,
                    onClick = onDeletePlaylist,
                    danger = true
                )
            ),
            onDismiss = { showMore = false }
        )
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
                    text = playlist.title ?: tr("Без названия"),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = tr("%s треков", playlist.trackCount),
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
    downloadProgress: DownloadProgressOf = NoDownloadProgress,
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

    val albumLibrary = LocalAlbumLibrary.current
    val tracks = playlist.tracks
    val title = playlist.title ?: tr("Без названия")
    val isLikedPlaylist = playlist.id == -100L
    val isActive = currentTrackId != null && tracks.any { it.id == currentTrackId }
    val favoritesMap = remember(favorites) { favorites.associateBy { it.id } }
    val listState = rememberLazyListState()
    val collapsed = rememberCollapsed(listState, MixCoverHeight - 140.dp)
    var showMore by remember { mutableStateOf(false) }
    val artwork: @Composable BoxScope.() -> Unit = {
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
    }
    val shuffle: (() -> Unit)? = if (tracks.size < 2 || albumLibrary == null) null else {
        { albumLibrary.onShuffle(tracks, "set-${playlist.id}") }
    }
    val context = LocalContext.current
    val link = remember(playlist.permalinkUrl) { SharedLinks.linkOf(playlist) }

    CollectionPage(
        onBack = onBack,
        page = {
            DetailFrame(
                listState = listState,
                // The last rows clear of the toolbar and the mini player over it.
                bottomPadding = FeedBottomRoom,
                header = {
                    CoverHeader(
                        artwork = artwork,
                        kicker = tr("Плейлист · Яндекс Музыка"),
                        title = title,
                        subtitle = null,
                        // The cover opens "Ещё", where changing it is.
                        onArtworkClick = { showMore = true }
                    )
                }
            ) {
                item(key = "yandex-playlist-count") {
                    CountRule(
                        text = when {
                            playlist.trackCount > 0 -> plural(playlist.trackCount, tr("трек"), tr("трека"), tr("треков"))
                            isLoading -> tr("Загружаем треки")
                            else -> tr("Нет треков")
                        }
                    )
                }

                if (isLoading) {
                    item(key = "yandex-playlist-loading") { LoadingBlock(height = 200.dp) }
                } else if (tracks.isEmpty()) {
                    item(key = "yandex-playlist-empty") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp)) { EmptyState(tr("Здесь пока нет треков.")) }
                    }
                } else {
                    itemsIndexed(
                        tracks,
                        key = { _, track -> "yandex-playlist-detail-${playlist.id}-${track.id}" }
                    ) { _, track ->
                        val favorite = favoritesMap[track.id]
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            TrackCard(
                                track = track,
                                isFavorite = favorite != null,
                                isSelected = track.id == currentTrackId,
                                downloadState = favorite?.downloadState,
                                progress = { downloadProgress[track.id] },
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track) },
                                onFavoriteClick = { onFavoriteClick(track) },
                                flat = true
                            )
                        }
                    }
                }
            }

            CollapsingTopBar(title = title, collapsed = collapsed)
        }
    ) {
        // An account's playlist is neither saved nor downloaded from here: "Ещё" in words is the
        // whole of the pill.
        CollectionBar(
            step = CollectionStep(StepKind.More),
            onStep = { showMore = true },
            onMore = { showMore = true },
            labelled = !collapsed,
            isActive = isActive,
            isPlaying = isPlaying,
            onPlay = if (tracks.isEmpty()) null else {
                {
                    if (isActive && albumLibrary != null) albumLibrary.onTogglePlay() else onPlayTrack(tracks.first())
                }
            },
            onShuffle = shuffle
        )
    }

    if (showMore) {
        CollectionSheet(
            artwork = artwork,
            kicker = tr("Плейлист · Яндекс Музыка"),
            title = title,
            subtitle = playlist.trackCount.takeIf { it > 0 }?.let { plural(it, tr("трек"), tr("трека"), tr("треков")) },
            tiles = listOfNotNull(
                link?.let { url -> CollectionAction(Icons.Default.Share, tr("Поделиться"), onClick = { shareText(context, url) }) }
                    ?: shuffle?.let { CollectionAction(Icons.Rounded.Shuffle, tr("Вперемешку"), onClick = it) },
                CollectionAction(Icons.Default.Image, tr("Обложка"), onClick = { imagePicker.launch(arrayOf("image/*")) })
            ),
            rows = emptyList(),
            onDismiss = { showMore = false }
        )
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
    downloadProgress: DownloadProgressOf = NoDownloadProgress,
    isPlaying: Boolean = false,
    favorites: List<FavoriteTrack>,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    onPlaylistClick: (SoundCloudPlaylist) -> Unit,
    selectedPlaylist: SoundCloudPlaylist? = null,
    // Its tracks on the way: the album's own loading, not the page's.
    isAlbumLoading: Boolean = false,
    onDeselectPlaylist: () -> Unit = {},
    isAllTracksLoaded: Boolean = false,
    onLoadAllTracks: () -> Unit = {},
    onShuffle: () -> Unit = {},
    follow: ArtistFollow? = null,
    onToggleFollow: () -> Unit = {},
    lives: List<SoundCloudTrack> = emptyList(),
    shelves: List<YtShelf> = emptyList(),
    onPlayFrom: (SoundCloudTrack, List<SoundCloudTrack>) -> Unit = { track, _ -> onPlayTrack(track) },
    onOpenArtist: (SoundCloudUser) -> Unit = {},
    onRetry: (() -> Unit)? = null,
    tracksTitle: String = tr("Популярные треки")
) {
    // An album opened on the artist's page lies over the page, which stays as it was left under
    // it: pulled down, the album shows the page there, and closing it lands where the page was
    // scrolled, its albums' carousel where it was turned to. Covered by the album that has come
    // in, the page isn't drawn, and nothing on it moves by itself.
    var albumPulled by remember { mutableStateOf(false) }
    val albums = androidx.compose.animation.core.updateTransition(selectedPlaylist, label = "artistAlbum")
    val pageHidden by remember {
        derivedStateOf { albums.currentState != null && albums.targetState != null && !albumPulled }
    }
    val outerCovered = LocalCovered.current
    val pageCovered = remember(outerCovered) { { pageHidden || outerCovered() } }
    Box(modifier = Modifier.fillMaxSize()) {
        UnderPages(hidden = { pageHidden }) {
            androidx.compose.runtime.CompositionLocalProvider(LocalCovered provides pageCovered) {
                ArtistPage(
                artist = artist,
                tracks = tracks,
                playlists = playlists,
                isLoading = isLoading,
                error = error,
                currentTrackId = currentTrackId,
                downloadProgress = downloadProgress,
                isPlaying = isPlaying,
                favorites = favorites,
                onBack = onBack,
                onPlayTrack = onPlayTrack,
                onFavoriteClick = onFavoriteClick,
                onPlaylistClick = onPlaylistClick,
                isAllTracksLoaded = isAllTracksLoaded,
                onLoadAllTracks = onLoadAllTracks,
                onShuffle = onShuffle,
                follow = follow,
                onToggleFollow = onToggleFollow,
                lives = lives,
                shelves = shelves,
                onPlayFrom = onPlayFrom,
                onOpenArtist = onOpenArtist,
                onRetry = onRetry,
                tracksTitle = tracksTitle
                )
            }
        }
        albums.AnimatedContent(
            transitionSpec = {
                // Faded on what they draw (see glassFade).
                (scaleIn(initialScale = 0.97f, animationSpec = tween(240)) togetherWith
                    scaleOut(targetScale = 1.02f, animationSpec = tween(160))) using null
            },
            // The same album filled in as its tracks arrive is the same page, not a new one.
            contentKey = { it?.id }
        ) { album ->
            val fade = glassFade(enter = tween(240), exit = tween(160))
            if (album != null) Box(modifier = Modifier.fillMaxSize().then(fade)) {
                // Its pull shows the artist's page under it, not home: told here, not to the screen.
                androidx.compose.runtime.CompositionLocalProvider(LocalPagePulled provides { albumPulled = it }) {
                    Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) { }) {
                        SetDetailContent(
                            playlist = album,
                            subtitle = artist.username.orEmpty(),
                            isLoading = isAlbumLoading,
                            favorites = favorites,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            downloadProgress = downloadProgress,
                            onBack = onDeselectPlaylist,
                            onPlayTrack = onPlayTrack,
                            onFavoriteClick = onFavoriteClick,
                            artistName = artist.username
                        )
                    }
                }
            }
        }
    }
}

/** The artist's own page, under whatever album is opened on it (see [ArtistDetailScreen]). */
@Composable
private fun ArtistPage(
    artist: SoundCloudUser,
    tracks: List<SoundCloudTrack>,
    playlists: List<SoundCloudPlaylist>,
    isLoading: Boolean,
    error: String?,
    currentTrackId: Long?,
    downloadProgress: DownloadProgressOf = NoDownloadProgress,
    isPlaying: Boolean = false,
    favorites: List<FavoriteTrack>,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    onPlaylistClick: (SoundCloudPlaylist) -> Unit,
    isAllTracksLoaded: Boolean = false,
    onLoadAllTracks: () -> Unit = {},
    onShuffle: () -> Unit = {},
    follow: ArtistFollow? = null,
    onToggleFollow: () -> Unit = {},
    lives: List<SoundCloudTrack> = emptyList(),
    shelves: List<YtShelf> = emptyList(),
    onPlayFrom: (SoundCloudTrack, List<SoundCloudTrack>) -> Unit = { track, _ -> onPlayTrack(track) },
    onOpenArtist: (SoundCloudUser) -> Unit = {},
    onRetry: (() -> Unit)? = null,
    tracksTitle: String = tr("Популярные треки")
) {
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
            // No back button: pulled down, the page closes (see pullToClose).
            .pullToClose(onBack)
            .pageGlass(MaterialTheme.colorScheme.background)
    ) {
        DetailFrame(
            listState = listState,
            bottomPadding = 120.dp,
            header = {
                ArtistPortraitHeader(
                    artist = artist,
                    albumCount = playlists.size,
                    onPlay = tracks.firstOrNull()?.let { first -> { onPlayTrack(first) } },
                    onShuffle = if (tracks.isEmpty()) null else onShuffle,
                    follow = follow,
                    onToggleFollow = onToggleFollow
                )
            }
        ) {
            if (isLoading) {
                item(key = "artist-loading") { LoadingBlock() }
            } else if (error != null) {
                item(key = "artist-error") {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MessageCard(error)
                        if (onRetry != null) {
                            FilledTonalButton(onClick = onRetry, shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tr("Повторить"))
                            }
                        }
                    }
                }
            } else {
                if (tracks.isNotEmpty()) {
                    item(key = "artist-tracks-title") {
                        SectionTitle(
                            text = tracksTitle,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                            actionLabel = when {
                                canShowMore -> if (countKnown) tr("Все %s", totalTracks) else tr("Все")
                                showAllTracks && tracks.size > TOP_TRACKS -> tr("Свернуть")
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
                                progress = { downloadProgress[track.id] },
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track) },
                                onFavoriteClick = { onFavoriteClick(track) },
                                position = groupPosition(index, shownTracks.size),
                                number = index + 1
                            )
                        }
                    }
                }

                val isYandexArtist = artist.permalinkUrl?.startsWith("yandex") == true
                // What the channel is broadcasting now.
                if (lives.isNotEmpty()) {
                    item(key = "artist-lives-title") {
                        SectionTitle(
                            tr("Трансляции"),
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    item(key = "artist-lives") {
                        VideoTileRow(tracks = lives, currentTrackId = currentTrackId, onPlay = { onPlayFrom(it, lives) })
                    }
                }

                if (shelves.isNotEmpty()) {
                    // The page whole, row by row as YouTube lays it out.
                    shelves.forEachIndexed { index, shelf ->
                        item(key = "artist-shelf-title-$index") {
                            SectionTitle(
                                shelf.title,
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                            )
                        }
                        item(key = "artist-shelf-$index") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (shelf.tracks.isNotEmpty()) {
                                    VideoTileRow(
                                        tracks = shelf.tracks,
                                        currentTrackId = currentTrackId,
                                        onPlay = { onPlayFrom(it, shelf.tracks) }
                                    )
                                }
                                if (shelf.sets.isNotEmpty()) {
                                    ArtistSetsCarousel(shelf.sets, isYandexArtist, onPlaylistClick)
                                }
                                if (shelf.artists.isNotEmpty()) {
                                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                                        ArtistRow(artists = shelf.artists, onOpen = onOpenArtist)
                                    }
                                }
                            }
                        }
                    }
                } else if (playlists.isNotEmpty()) {
                    item(key = "artist-sets-title") {
                        SectionTitle(
                            tr("Альбомы и плейлисты"),
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
                        )
                    }
                    item(key = "artist-sets") {
                        ArtistSetsCarousel(playlists, isYandexArtist, onPlaylistClick)
                    }
                }

                if (!artist.description.isNullOrBlank()) {
                    item(key = "artist-about-title") {
                        SectionTitle(
                            tr("Об артисте"),
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

        CollapsingTopBar(title = artist.username.orEmpty(), collapsed = collapsed)
    }
}

/** How many of an artist's tracks show before "Все". */
private const val TOP_TRACKS = 5

/** A collection's artists as chips, each opening the artist's page, as the player's do. */
@Composable
private fun ArtistPills(artists: List<SoundCloudUser>, onClick: (SoundCloudUser) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        artists.forEach { artist ->
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick(artist)
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Text(
                    text = artist.username.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** An artist's albums and playlists in the multi-browse carousel. */
@Composable
private fun ArtistSetsCarousel(
    playlists: List<SoundCloudPlaylist>,
    isYandexArtist: Boolean,
    onPlaylistClick: (SoundCloudPlaylist) -> Unit
) {
    AlbumCarousel(
        albums = playlists.map { playlist ->
            val isAlbum = isYandexArtist || playlist.permalinkUrl?.startsWith("yandex:album:") == true
            CarouselAlbum(
                key = playlist.id,
                title = playlist.title ?: tr("Альбом"),
                subtitle = "",
                caption = if (isAlbum) {
                    tr("Альбом · ") + plural(playlist.trackCount, tr("трек"), tr("трека"), tr("треков"))
                } else {
                    setCaption(playlist)
                },
                artworkUrl = playlist.displayArtworkUrl,
                source = "set-${playlist.id}",
                onClick = { onPlaylistClick(playlist) }
            )
        },
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

/**
 * A row of a channel's videos, as its page shows them: wide pictures with the title beneath, a
 * broadcast marked "В ЭФИРЕ", the one playing ringed. Songs keep their square covers.
 */
@Composable
private fun VideoTileRow(
    tracks: List<SoundCloudTrack>,
    currentTrackId: Long?,
    onPlay: (SoundCloudTrack) -> Unit
) {
    // A video's picture is YouTube's own, wide; a song's cover is square.
    val wide = tracks.firstOrNull()?.artworkUrl?.contains("/vi/") == true
    val width = if (wide) 216.dp else 148.dp
    val shape = RoundedCornerShape(20.dp)
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            Column(
                modifier = Modifier
                    .width(width)
                    .clip(shape)
                    .clickable { onPlay(track) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (wide) 16f / 9f else 1f)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(
                            if (track.id == currentTrackId) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
                        )
                ) {
                    AsyncImage(
                        model = artworkUrlForSize(track.artworkUrl, width),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (track.kind == "live") {
                        Box(modifier = Modifier.padding(8.dp)) { LiveBadge() }
                    }
                }
                Text(
                    text = track.title.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 4.dp)
                )
            }
        }
    }
}

/**
 * Album or playlist opened from the artist page or from search: laid out as a mix is, the cover
 * across the top with the title over it and the big play button, then the tracks.
 */
@Composable
private fun SetDetailContent(
    playlist: SoundCloudPlaylist,
    subtitle: String,
    isLoading: Boolean,
    favorites: List<FavoriteTrack>,
    currentTrackId: Long?,
    isPlaying: Boolean,
    downloadProgress: DownloadProgressOf,
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit,
    error: String? = null,
    artistName: String? = null,
    // Its artists' chips open their pages; null, a plain name (the artist's own page opened it).
    onArtistClick: ((SoundCloudUser) -> Unit)? = null
) {
    val isYandex = playlist.permalinkUrl?.contains("yandex") == true
    val albumLibrary = LocalAlbumLibrary.current
    val liked = albumLibrary?.likedBySource?.get(playlist.sourceKey())
    val tracks = playlist.knownTracks
    // Until the full list arrives only a few tracks are known; the set's own count is closer
    // to what's about to appear.
    val trackCount = if (isLoading) maxOf(playlist.trackCount, tracks.size) else tracks.size
    val favoritesMap = remember(favorites) { favorites.associateBy { it.id } }
    val title = playlist.title ?: tr("Без названия")
    // Playing from this set: the big button pauses and resumes rather than starting over.
    val isActive = currentTrackId != null && tracks.any { it.id == currentTrackId }
    val listState = rememberLazyListState()
    val collapsed = rememberCollapsed(listState, MixCoverHeight - 140.dp)
    // Who it is by, with a page each: the set's owner where it links one; an album's artists, as
    // its tracks name them, otherwise.
    val setArtists = remember(playlist.user, tracks) {
        val owner = playlist.user?.takeIf { !it.permalinkUrl.isNullOrBlank() }
        val album = playlist.isAlbum == true || playlist.permalinkUrl?.startsWith("yandex:album:") == true
        when {
            owner != null -> listOf(owner)
            album -> tracks.firstOrNull()
                ?.let { first -> first.artists?.takeIf { it.isNotEmpty() } ?: listOfNotNull(first.user) }
                ?.filter { !it.permalinkUrl.isNullOrBlank() }
                .orEmpty()
            else -> emptyList()
        }
    }

    var showMore by remember { mutableStateOf(false) }
    val kicker = when {
        liked != null -> tr("%s · в медиатеке", if (isYandex) tr("Альбом") else setCaption(playlist))
        isYandex -> tr("Альбом")
        else -> setCaption(playlist)
    }
    val artworkUrl = ArtworkUrls.highRes(playlist.displayArtworkUrl) ?: playlist.displayArtworkUrl
    val artwork: @Composable BoxScope.() -> Unit = {
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
    }
    // Saving the album keeps it as a playlist next to "Скачанное"; from then on it downloads as a
    // whole. Both say what they do in words now, under the thumb, and the heart stays the tracks'.
    val library = albumLibrary?.takeIf { tracks.isNotEmpty() }
    val byName = artistName ?: subtitle
    val step = when {
        library == null -> null
        liked == null -> CollectionStep(StepKind.Save)
        else -> downloadStep(liked)
    }
    val shuffle: (() -> Unit)? = if (tracks.size < 2 || albumLibrary == null) null else {
        { albumLibrary.onShuffle(tracks, "set-${playlist.id}") }
    }
    val context = LocalContext.current
    // Its page on its service, to share.
    val link = remember(playlist.permalinkUrl) { SharedLinks.linkOf(playlist) }

    CollectionPage(
        onBack = onBack,
        page = {
            DetailFrame(
                listState = listState,
                // The last rows clear of the toolbar and the mini player over it.
                bottomPadding = FeedBottomRoom,
                header = {
                    CoverHeader(
                        artwork = artwork,
                        kicker = kicker,
                        title = title,
                        subtitle = subtitle.takeIf { it.isNotBlank() },
                        byline = setArtists.takeIf { it.isNotEmpty() && onArtistClick != null }?.let { artists ->
                            { ArtistPills(artists = artists, onClick = { onArtistClick?.invoke(it) }) }
                        },
                        onArtworkClick = if (library != null) {
                            { showMore = true }
                        } else {
                            null
                        }
                    )
                }
            ) {
                item(key = "set-count") {
                    CountRule(
                        text = when {
                            trackCount > 0 -> plural(trackCount, tr("трек"), tr("трека"), tr("треков"))
                            isLoading -> tr("Загружаем треки")
                            else -> tr("Нет треков")
                        }
                    )
                }

                if (error != null) {
                    item(key = "set-error") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { MessageCard(error) }
                    }
                }

                if (tracks.isEmpty()) {
                    if (isLoading) {
                        item(key = "set-loading") { LoadingBlock(height = 200.dp) }
                    } else if (error == null) {
                        item(key = "set-empty") {
                            Box(modifier = Modifier.padding(horizontal = 16.dp)) { EmptyState(tr("Здесь пока нет треков.")) }
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { index, track -> "set-${track.id}-$index" }) { _, track ->
                        val favorite = favoritesMap[track.id]
                        Box(modifier = Modifier.padding(horizontal = 8.dp)) {
                            TrackCard(
                                track = track,
                                isFavorite = favorite != null,
                                isSelected = track.id == currentTrackId,
                                downloadState = favorite?.downloadState,
                                progress = { downloadProgress[track.id] },
                                isPlaying = isPlaying,
                                onClick = { onPlayTrack(track) },
                                onFavoriteClick = { onFavoriteClick(track) },
                                flat = true
                            )
                        }
                    }
                    // The first few are in; the rest are on their way.
                    if (isLoading) {
                        item(key = "set-loading-more") { LoadingBlock(height = 120.dp) }
                    }
                }
            }

            CollapsingTopBar(title = title, collapsed = collapsed)
        }
    ) {
        CollectionBar(
            step = step,
            onStep = {
                when {
                    library == null -> Unit
                    liked == null -> library.onToggleLike(playlist, byName)
                    step?.kind == StepKind.Download -> library.onDownload(liked)
                    else -> showMore = true
                }
            },
            onMore = if (library != null || link != null) {
                { showMore = true }
            } else {
                null
            },
            labelled = !collapsed,
            isActive = isActive,
            isPlaying = isPlaying,
            onPlay = if (tracks.isEmpty()) null else {
                {
                    if (isActive && albumLibrary != null) albumLibrary.onTogglePlay() else onPlayTrack(tracks.first())
                }
            },
            onShuffle = shuffle
        )
    }

    if (showMore && (library != null || link != null)) {
        val download = liked?.let { downloadStep(it) }
        CollectionSheet(
            artwork = artwork,
            kicker = kicker,
            title = title,
            subtitle = trackCount.takeIf { it > 0 }?.let { plural(it, tr("трек"), tr("трека"), tr("треков")) },
            tiles = listOfNotNull(
                CollectionAction(
                    icon = Icons.Rounded.LibraryAdd,
                    label = tr("В медиатеку"),
                    onClick = { library?.onToggleLike(playlist, byName) }
                ).takeIf { library != null && liked == null },
                when {
                    library == null -> null
                    // Not saved yet: downloading saves it on the way.
                    liked == null -> CollectionAction(
                        icon = Icons.Default.Download,
                        label = tr("Скачать"),
                        subtitle = tr("Альбом сохранится в медиатеку"),
                        onClick = { library.onDownloadAlbum(playlist, byName) }
                    )
                    download != null -> CollectionAction(
                        icon = if (download.kind == StepKind.Downloaded) Icons.Default.DownloadDone else Icons.Default.Download,
                        label = stepLabel(download),
                        selected = download.kind == StepKind.Downloaded,
                        onClick = { if (download.kind == StepKind.Download) library.onDownload(liked) }
                    )
                    else -> null
                },
                // Shuffling is on the toolbar too: sharing takes its tile where there is a link.
                link?.let { url -> CollectionAction(Icons.Default.Share, tr("Поделиться"), onClick = { shareText(context, url) }) }
                    ?: shuffle?.let { CollectionAction(Icons.Rounded.Shuffle, tr("Вперемешку"), onClick = it) }
            ),
            rows = listOfNotNull(
                CollectionAction(
                    icon = Icons.Default.Delete,
                    label = tr("Убрать из медиатеки"),
                    subtitle = if ((liked?.downloadedCount ?: 0) > 0) tr("Скачанные для него треки удалятся с телефона") else null,
                    onClick = { library?.onToggleLike(playlist, byName) },
                    danger = true
                ).takeIf { liked != null && library != null }
            ),
            onDismiss = { showMore = false }
        )
    }
}


/**
 * What can be done with a track: the menu a held cover or clip opens. In the app's own look — the
 * backdrop's tone with panels on it, the accent only where something is on — as a Material 3
 * Expressive sheet: the track on top, its three actions as tiles, what concerns its file below.
 *
 * "В плейлист" opens the playlists right under the tiles, lit while they are open, instead of a
 * second page with a back arrow: a tap on the tile again folds them away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsDialog(
    track: SoundCloudTrack,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onShare: () -> Unit,
    onRedownload: (() -> Unit)? = null,
    // Only for a track downloaded to the phone.
    onDeleteDownload: (() -> Unit)? = null,
    // Only for tracks with a radio to start — YouTube Music's.
    onRadio: (() -> Unit)? = null,
    radioDescription: String = tr("Трек и то, что YouTube Music поставит за ним"),
    // "Слушать вместе", opened from here.
    onTogether: (() -> Unit)? = null,
    // The crossfade between tracks, in seconds, and setting it. Null: neither it nor the sleep
    // timer is offered.
    crossfadeSeconds: Int = 0,
    onCrossfade: ((Int) -> Unit)? = null,
    // The onboarding's demo menu: the sleep timer it shows is one of its own, which stops nothing.
    demo: Boolean = false
) {
    // A real M3 modal bottom sheet rather than a Dialog imitating one: this brings the
    // spec scrim, drag handle, swipe-to-dismiss, predictive back and inset handling.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    // Actions taken inside the sheet should play the same close animation as a swipe or a
    // scrim tap, so hide the sheet first and only then tear down the composition.
    val dismissSheet: () -> Unit = {
        sheetScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    var choosingPlaylist by remember { mutableStateOf(false) }
    // The sleep timer's and the crossfade's choices; one open at a time, with the playlists.
    var choosingSleep by remember { mutableStateOf(false) }
    var choosingCrossfade by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var playlistNameInput by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.onBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // The track: where it is from over its name, as the player's chip says it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TrackArtwork(artworkUrl = track.artworkUrl, size = 64.dp, useMorphing = false)
                Column(modifier = Modifier.weight(1f)) {
                    Kicker(
                        text = when {
                            track.urn?.startsWith("local:") == true -> tr("С телефона")
                            track.liveVideoId != null -> tr("YouTube · в эфире")
                            track.urn?.startsWith("yandex:") == true -> tr("Яндекс Музыка")
                            track.youTubeVideoId != null -> "YouTube Music"
                            else -> "SoundCloud"
                        },
                        color = PanelColors.accent
                    )
                    Text(
                        text = track.title ?: tr("Без названия"),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artistLine(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetTile(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = tr("В плейлист"),
                    selected = choosingPlaylist,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        choosingPlaylist = !choosingPlaylist
                        choosingSleep = false
                        choosingCrossfade = false
                    },
                    modifier = Modifier.weight(1f)
                )
                SheetTile(
                    icon = Icons.Default.Share,
                    label = tr("Поделиться"),
                    onClick = {
                        onShare()
                        dismissSheet()
                    },
                    modifier = Modifier.weight(1f)
                )
                if (onRadio != null) {
                    SheetTile(
                        icon = Icons.Default.Radio,
                        label = tr("Радио"),
                        description = radioDescription,
                        onClick = {
                            onRadio()
                            dismissSheet()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (onTogether != null) {
                    SheetTile(
                        icon = Icons.Rounded.Groups,
                        label = tr("Вместе"),
                        description = tr("Слушать вместе с друзьями рядом"),
                        onClick = onTogether,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // How the music goes on: the sleep timer and the crossfade between tracks. For all of it,
            // not this track alone, but where the hand already is.
            if (onCrossfade != null) {
                val realTimer by com.example.myapplication.player.SleepTimer.state.collectAsState()
                var demoTimer by remember { mutableStateOf<com.example.myapplication.player.SleepTimer.State>(com.example.myapplication.player.SleepTimer.State.Off) }
                val timer = if (demo) demoTimer else realTimer
                val setTimer: (com.example.myapplication.player.SleepTimer.State) -> Unit = { state ->
                    if (demo) {
                        demoTimer = state
                    } else {
                        when (state) {
                            is com.example.myapplication.player.SleepTimer.State.At -> com.example.myapplication.player.SleepTimer.set(state.minutes)
                            com.example.myapplication.player.SleepTimer.State.EndOfTrack -> com.example.myapplication.player.SleepTimer.endOfTrack()
                            com.example.myapplication.player.SleepTimer.State.Off -> com.example.myapplication.player.SleepTimer.cancel()
                        }
                    }
                }
                var minutesLeft by remember { mutableStateOf(com.example.myapplication.player.SleepTimer.minutesLeft()) }
                LaunchedEffect(timer) {
                    while (timer is com.example.myapplication.player.SleepTimer.State.At) {
                        minutesLeft = if (demo) timer.minutes else com.example.myapplication.player.SleepTimer.minutesLeft()
                        delay(5_000)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SheetTile(
                        icon = Icons.Rounded.Bedtime,
                        label = when (timer) {
                            is com.example.myapplication.player.SleepTimer.State.At -> tr("Сон · %s мин", minutesLeft ?: 0)
                            com.example.myapplication.player.SleepTimer.State.EndOfTrack -> tr("Сон · трек")
                            com.example.myapplication.player.SleepTimer.State.Off -> tr("Таймер сна")
                        },
                        description = tr("Музыка плавно стихнет и остановится"),
                        selected = choosingSleep || timer != com.example.myapplication.player.SleepTimer.State.Off,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            choosingSleep = !choosingSleep
                            choosingCrossfade = false
                            choosingPlaylist = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                    SheetTile(
                        icon = Icons.AutoMirrored.Rounded.CallMerge,
                        label = if (crossfadeSeconds > 0) tr("Кроссфейд · %s с", crossfadeSeconds) else tr("Кроссфейд"),
                        description = tr("Треки плавно перетекают друг в друга"),
                        selected = choosingCrossfade || crossfadeSeconds > 0,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            choosingCrossfade = !choosingCrossfade
                            choosingSleep = false
                            choosingPlaylist = false
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                AnimatedVisibility(
                    visible = choosingSleep,
                    enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut()
                ) {
                    val at = timer as? com.example.myapplication.player.SleepTimer.State.At
                    SheetChoices(
                        caption = when (timer) {
                            is com.example.myapplication.player.SleepTimer.State.At -> tr("Остановится через %s мин — последние полминуты музыка стихает", minutesLeft ?: 0)
                            com.example.myapplication.player.SleepTimer.State.EndOfTrack -> tr("Остановится, когда закончится этот трек")
                            com.example.myapplication.player.SleepTimer.State.Off -> tr("Через сколько остановить музыку. Последние полминуты она плавно стихает")
                        },
                        choices = SLEEP_MINUTES.map { minutes ->
                            SheetChoice(tr("%s мин", minutes), selected = at?.minutes == minutes) {
                                setTimer(com.example.myapplication.player.SleepTimer.State.At(android.os.SystemClock.elapsedRealtime() + minutes * 60_000L, minutes))
                                dismissSheet()
                            }
                        } + SheetChoice(tr("Конец трека"), selected = timer == com.example.myapplication.player.SleepTimer.State.EndOfTrack) {
                            setTimer(com.example.myapplication.player.SleepTimer.State.EndOfTrack)
                            dismissSheet()
                        } + listOfNotNull(
                            SheetChoice(tr("Выключить"), selected = false) {
                                setTimer(com.example.myapplication.player.SleepTimer.State.Off)
                                choosingSleep = false
                            }.takeIf { timer != com.example.myapplication.player.SleepTimer.State.Off }
                        )
                    )
                }
                AnimatedVisibility(
                    visible = choosingCrossfade,
                    enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut()
                ) {
                    SheetChoices(
                        caption = if (crossfadeSeconds > 0) {
                            tr("Конец трека стихает, а следующий уже начинается — %s с одновременно", crossfadeSeconds)
                        } else {
                            tr("Конец трека стихает, а следующий уже начинается. Для всех треков")
                        },
                        choices = CROSSFADE_SECONDS.map { seconds ->
                            SheetChoice(if (seconds == 0) tr("Выкл") else tr("%s с", seconds), selected = crossfadeSeconds == seconds) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCrossfade(seconds)
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = choosingPlaylist,
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(PanelColors.container)
                ) {
                    item(key = "new-playlist") {
                        SheetRow(
                            leading = {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PanelColors.accent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = PanelColors.onAccent)
                                }
                            },
                            title = tr("Новый плейлист"),
                            subtitle = tr("С этим треком в нём"),
                            onClick = { showCreatePlaylistDialog = true }
                        )
                    }
                    itemsIndexed(playlists, key = { _, playlist -> "playlist-${playlist.id}" }) { _, playlist ->
                        SheetDivider()
                        SheetRow(
                            leading = {
                                if (!playlist.artworkUrl.isNullOrBlank()) {
                                    FolderArtwork(playlist.artworkUrl, size = 44.dp)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(PanelColors.content.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                            contentDescription = null,
                                            tint = PanelColors.accent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            },
                            title = playlist.name,
                            subtitle = plural(playlist.tracks.size, tr("трек"), tr("трека"), tr("треков")),
                            onClick = {
                                onAddToPlaylist(playlist)
                                dismissSheet()
                            }
                        )
                    }
                }
            }

            // What concerns the file on the phone, apart from what is done with the track.
            if (onRedownload != null || onDeleteDownload != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(PanelColors.container)
                ) {
                    if (onRedownload != null) {
                        SheetRow(
                            leading = { SheetRowIcon(Icons.Default.Refresh) },
                            title = tr("Перескачать"),
                            subtitle = tr("Скачать файл заново на устройство"),
                            onClick = {
                                onRedownload()
                                dismissSheet()
                            }
                        )
                    }
                    if (onRedownload != null && onDeleteDownload != null) SheetDivider()
                    if (onDeleteDownload != null) {
                        SheetRow(
                            leading = { SheetRowIcon(Icons.Default.Delete, tint = colors.error) },
                            title = tr("Удалить с устройства"),
                            subtitle = tr("Файл удалится с телефона, в любимых трек останется"),
                            titleColor = colors.error,
                            onClick = {
                                onDeleteDownload()
                                dismissSheet()
                            }
                        )
                    }
                }
            }
        }

        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text(tr("Новый плейлист")) },
                text = {
                    OutlinedTextField(
                        value = playlistNameInput,
                        onValueChange = { playlistNameInput = it },
                        placeholder = { Text(tr("Название плейлиста")) },
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
                        Text(tr("Создать"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text(tr("Отмена"))
                    }
                }
            )
        }
    }
}

/**
 * One of the sheet's three actions: the accent glyph over its name on a panel-tone tile — the
 * launcher's themed-icon look. A tile that opens something (the playlists) is lit while it is
 * open, and rounds out, as the app's toggles do.
 */
@Composable
private fun SheetTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    description: String? = null
) {
    val container by animateColorAsState(
        if (selected) PanelColors.accent else PanelColors.container,
        tween(250),
        label = "sheetTileContainer"
    )
    val content by animateColorAsState(
        if (selected) PanelColors.onAccent else PanelColors.accent,
        tween(250),
        label = "sheetTileContent"
    )
    val corner by animateDpAsState(
        if (selected) 32.dp else 22.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "sheetTileCorner"
    )
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(88.dp)
            .semantics { if (description != null) contentDescription = "$label. $description" },
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) PanelColors.onAccent else PanelColors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val SLEEP_MINUTES = listOf(15, 30, 45, 60, 90)
private val CROSSFADE_SECONDS = listOf(0, 3, 5, 8, 12)

private class SheetChoice(val label: String, val selected: Boolean, val onClick: () -> Unit)

/** A line of what it does over pills to choose from, on a panel of the sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetChoices(caption: String, choices: List<SheetChoice>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PanelColors.container)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(caption, style = MaterialTheme.typography.bodySmall, color = PanelColors.content.copy(alpha = 0.75f))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                Surface(
                    onClick = choice.onClick,
                    shape = CircleShape,
                    color = if (choice.selected) PanelColors.accent else PanelColors.content.copy(alpha = 0.1f),
                    contentColor = if (choice.selected) PanelColors.onAccent else PanelColors.content
                ) {
                    Text(
                        choice.label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/** A row of the sheet's panels: a picture or icon, a name and a line under it. */
@Composable
private fun SheetRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    titleColor: Color = PanelColors.content
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PanelColors.content.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SheetRowIcon(icon: ImageVector, tint: Color = PanelColors.accent) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PanelColors.content.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .padding(start = 72.dp, end = 14.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(PanelColors.content.copy(alpha = 0.08f))
    )
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
                        text = tr("Проверка SoundCloud"),
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Text(
                    text = tr("SoundCloud не принимает лайки с этого адреса, пока не пройдена проверка. " +
                        "После неё лайк отправится сам."),
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
                    Text(text = tr("Вход в YouTube Music"), style = MaterialTheme.typography.titleLarge)
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
                        text = tr("Вход в Яндекс Музыку"),
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



