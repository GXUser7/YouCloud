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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.max
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.os.Message
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.Context
import android.widget.FrameLayout
import android.view.ViewGroup

@Composable
fun MusicScreen(viewModel: MusicViewModel) {
    val haptic = LocalHapticFeedback.current
    val isLoggedOut by viewModel.isLoggedOut.collectAsState(initial = true)
    val tracks by viewModel.tracks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
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
    val mixTracks by viewModel.mixTracks.collectAsState()
    val playbackPositionMs by viewModel.playbackPositionMs.collectAsState()
    val playbackDurationMs by viewModel.playbackDurationMs.collectAsState()
    val screen by viewModel.screen.collectAsState()
    val userId by viewModel.userId.collectAsState()

    val downloadedTracks = favorites.filter { it.downloadState == DownloadState.DOWNLOADED }

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
            AppScreen.HOME -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ExpressiveBackground()

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
                    onReloadMixes = viewModel::loadMixes
                )

                AppScreen.SEARCH -> SearchScreen(
                    query = searchQuery,
                    tracks = tracks,
                    favorites = favorites,
                    currentTrackId = currentTrackId,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onBack = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.closeSearch()
                    },
                    onQueryChange = viewModel::onSearchQueryChange,
                    onPlayTrack = { track ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.playTrack(track)
                    },
                    onFavoriteClick = { track ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleFavorite(track)
                    }
                )

                AppScreen.DOWNLOADS -> DownloadsScreen(
                    tracks = downloadedTracks,
                    folderArtworkUri = downloadedFolderArtworkUri,
                    currentTrackId = currentTrackId,
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
                    }
                )

                AppScreen.SETTINGS -> SettingsScreen(
                    settingsRepository = viewModel.settingsRepository,
                    onBack = viewModel::closeSettings,
                    onRelogin = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.logout()
                    }
                )

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
                        }
                    )
                }
            }
        }
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
    onOpenSearch: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayMix: (SoundCloudMix) -> Unit,
    onOpenMix: (SoundCloudMix) -> Unit,
    onReloadMixes: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
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
            item {
                ClientIdWarningCard(onOpenSettings = onOpenSettings)
            }
        }

        item {
            SearchLaunchCard(onClick = onOpenSearch)
        }

        item {
            DownloadedFolderCard(
                count = downloadedTracks.size,
                artworkUri = downloadedFolderArtworkUri,
                onClick = onOpenDownloads
            )
        }

        item {
            MixesSection(
                mixSection = mixSection,
                isLoading = mixesLoading,
                loadingMixId = loadingMixId,
                hasOauthToken = hasOauthToken,
                errorMessage = mixesError,
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
                    onPlayMix = onPlayMix,
                    onOpenMix = onOpenMix,
                    onReload = {},
                    onOpenSettings = onOpenSettings
                )
            }
        }

        if (downloadedTracks.isEmpty()) {
            item { EmptyState("Пока здесь тихо. Найди трек и нажми лайк, чтобы скачать его.") }
        }
    }
}

@Composable
private fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onRelogin: () -> Unit
) {
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
                        text = "Вы вошли в систему. Если у вас возникли проблемы со стримингом или вы хотите сменить аккаунт, вы можете войти заново.",
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
            val eqEnabled by settingsRepository.equalizerEnabled.collectAsState()
            val eqPreset by settingsRepository.equalizerPreset.collectAsState()
            EqualizerCard(
                settingsRepository = settingsRepository,
                eqEnabled = eqEnabled,
                eqPreset = eqPreset
            )
        }
    }
}

@Composable
private fun SearchScreen(
    query: String,
    tracks: List<SoundCloudTrack>,
    favorites: List<FavoriteTrack>,
    currentTrackId: Long?,
    isLoading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        focusRequester.requestFocus()
    }

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
            SearchField(query = query, onQueryChange = onQueryChange, focusRequester = focusRequester)
        }

        // Removed LinearProgressIndicator to prevent layout jumping

        if (errorMessage != null) {
            item { MessageCard(errorMessage) }
        }

        if (tracks.isEmpty() && !isLoading) {
            item {
                EmptyState(
                    if (query.isBlank()) "Напиши, что хочешь услышать."
                    else "Ничего не нашлось."
                )
            }
        } else {
            items(tracks, key = { "search-${it.id}" }) { track ->
                TrackCard(
                    track = track,
                    isFavorite = favorites.any { it.id == track.id },
                    isSelected = track.id == currentTrackId,
                    onClick = { onPlayTrack(track) },
                    onFavoriteClick = { onFavoriteClick(track) }
                )
            }
        }
    }
}

@Composable
private fun DownloadsScreen(
    tracks: List<FavoriteTrack>,
    folderArtworkUri: String?,
    currentTrackId: Long?,
    onBack: () -> Unit,
    onChangeArtwork: (String?) -> Unit,
    onPlayTrack: (FavoriteTrack) -> Unit,
    onDeleteDownload: (FavoriteTrack) -> Unit
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TopBar(title = "Скачанные", onBack = onBack)
        }

        item {
            FolderHero(
                count = tracks.size,
                artworkUri = folderArtworkUri,
                onChangeArtwork = { imagePicker.launch(arrayOf("image/*")) }
            )
        }

        if (tracks.isEmpty()) {
            item { EmptyState("Здесь появятся треки, которые ты сохранишь на устройство.") }
        } else {
            items(tracks, key = { "downloaded-${it.id}" }) { track ->
                DownloadedTrackCard(
                    track = track,
                    isSelected = track.id == currentTrackId,
                    onClick = { onPlayTrack(track) },
                    onDeleteDownload = { onDeleteDownload(track) }
                )
            }
        }
    }
}

@Composable
private fun ExpressiveBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    )
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
private fun FolderHero(count: Int, artworkUri: String?, onChangeArtwork: () -> Unit) {
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

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChangeArtwork()
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.Image, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Сменить обложку")
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
                        MixCard(
                            mix = mix,
                            isLoading = loadingMixId == mix.id,
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                if (mix.artworkUrl != null) {
                    TrackArtwork(mix.artworkUrl, 166.dp)
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.size(166.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(48.dp))
                        }
                    }
                }

                // Play button overlay
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPlay()
                    },
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(48.dp),
                    tonalElevation = 8.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            // Subtle loading: just change icon or add a very faint pulse
                            // Removing CircularProgressIndicator as requested to prevent visual noise/shifting
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                modifier = Modifier.size(30.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(30.dp)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    onBack: () -> Unit,
    onPlayTrack: (SoundCloudTrack) -> Unit,
    onFavoriteClick: (SoundCloudTrack) -> Unit
) {
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
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        TrackArtwork(mix.artworkUrl, 240.dp)
                        
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
                    TrackCard(
                        track = track,
                        isFavorite = favorites.any { it.id == track.id },
                        isSelected = track.id == currentTrackId,
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
            TrackArtwork(track.artworkUrl, size = 68.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
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
    onClick: () -> Unit,
    onDeleteDownload: () -> Unit
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
            TrackArtwork(track.artworkUrl, 64.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

@Composable
private fun TrackDetailScreen(
    track: SoundCloudTrack,
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
    onDeleteDownload: (FavoriteTrack) -> Unit
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
    var lastVibratedRatio by remember { mutableStateOf(0f) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Rich dynamic gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )

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
                    FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
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
                                Icon(Icons.Default.Delete, contentDescription = null)
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
                val coroutineScope = rememberCoroutineScope()
                
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = artworkScale * artworkPressedScale.value
                            scaleY = artworkScale * artworkPressedScale.value
                        }
                        .pointerInput(track.permalinkUrl) {
                            detectTapGestures(
                                onLongPress = {
                                    track.permalinkUrl?.let { url ->
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

                                            // 3. Native share intent
                                            val sendIntent = Intent().apply {
                                                action = Intent.ACTION_SEND
                                                putExtra(Intent.EXTRA_TEXT, url)
                                                type = "text/plain"
                                            }
                                            val shareIntent = Intent.createChooser(sendIntent, null)
                                            context.startActivity(shareIntent)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    TrackArtwork(track.artworkUrl, size = 320.dp)
                }
                
                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = track.title,
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
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.weight(1f))

                Slider(
                    value = if (durationMs > 0) {
                        positionMs.coerceIn(0L, durationMs).toFloat() / durationMs.toFloat()
                    } else {
                        0f
                    },
                    onValueChange = { ratio ->
                        onSeek((ratio * durationMs).toLong())
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
                        Icon(Icons.Default.SkipPrevious, contentDescription = null, modifier = Modifier.size(32.dp))
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
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(32.dp))
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

@Composable
private fun TrackArtwork(artworkUrl: String?, size: Dp) {
    AsyncImage(
        model = artworkUrl?.replace("large", "t500x500"),
        contentDescription = null,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 100.dp) 36.dp else 18.dp))
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
                            
                            // Clear cookies, cache, and history to ensure clean login
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                            clearCache(true)
                            clearHistory()

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
                                            setSupportMultipleWindows(true)
                                            javaScriptCanOpenWindowsAutomatically = true
                                            userAgentString = mainWebView.settings.userAgentString
                                        }
                                        
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

