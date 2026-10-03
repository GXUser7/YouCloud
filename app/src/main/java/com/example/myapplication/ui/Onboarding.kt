package com.example.myapplication.ui

import com.example.myapplication.i18n.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudUser
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The first time the app opens: the gestures that nothing on screen advertises, each shown by a
 * finger doing it and then tried out on the real thing — the mini player, the player, a window,
 * «Моя форма».
 *
 * Each is shown inside a small screen named on a tab over it (see [ScreenFrame]) — home, the
 * player, a window — so that a gesture is never taken for one of another place: the player's own
 * were read as home's before. Real components where there are some (the mini player, the wave
 * shape, its tuning pane, the track menu), fed a demo track; the rest drawn as the screens they
 * stand for. A step can be done in any order and skipped; nothing is required.
 *
 * Kept light for a weak phone: one looping animation for the finger on the step in view, the rest
 * moves only when touched.
 */
@Composable
internal fun OnboardingOverlay(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = OnboardingStep.entries.lastIndex
    BackHandler { if (step > 0) step-- else onFinish() }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to lerp(colors.background, colors.primaryContainer, 0.35f),
                    0.55f to colors.background,
                    1f to colors.background
                )
            )
            // Nothing under the overlay is reachable while it is up.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 8.dp)
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StepDots(current = step, count = OnboardingStep.entries.size, modifier = Modifier.weight(1f))
                if (step < last) TextButton(onClick = onFinish) { Text(tr("Пропустить")) }
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(320)) { w -> if (forward) w / 4 else -w / 4 } + fadeIn(tween(320))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> if (forward) -w / 4 else w / 4 } + fadeOut(tween(180)))
                },
                label = "onboardingStep",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { shown ->
                val next = { if (shown < last) step = shown + 1 else onFinish() }
                when (OnboardingStep.entries[shown]) {
                    OnboardingStep.Intro -> IntroStep(onNext = next)
                    OnboardingStep.MiniPlayer -> MiniPlayerStep(onNext = next)
                    OnboardingStep.PlayerCover -> PlayerCoverStep(onNext = next)
                    OnboardingStep.PlayerPull -> PlayerPullStep(onNext = next)
                    OnboardingStep.Windows -> WindowsStep(onNext = next)
                    OnboardingStep.Wave -> WaveStep(onNext = next)
                    OnboardingStep.Done -> DoneStep(onNext = next)
                }
            }
        }
    }
}

private enum class OnboardingStep { Intro, MiniPlayer, PlayerCover, PlayerPull, Windows, Wave, Done }

// The steps that teach a gesture, for "Жест 2 из 5".
private const val GESTURE_STEPS = 5

/** The gestures a finger demonstrates. */
private enum class Hint { SwipeLeft, SwipeRight, SwipeUp, SwipeDown, Tap, LongPress }

// ------------------------------------------------------------------------------------ Steps

@Composable
private fun IntroStep(onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    StepLayout(
        kicker = tr("Добро пожаловать"),
        title = tr("Это YouCloud"),
        text = tr("SoundCloud, Яндекс Музыка и YouTube Music в одном плеере. Кое-что здесь спрятано " +
            "в жестах — покажу за минуту. Над каждым жестом написано, где он в приложении, и всё " +
            "можно сразу попробовать."),
        button = tr("Показать"),
        onNext = onNext,
        demo = {
            WaveShape(
                lobes = 9,
                depth = 0.08f,
                light = lerp(colors.primary, Color.White, 0.3f),
                color = colors.primary,
                playing = true,
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .aspectRatio(1f)
            )
        }
    )
}

@Composable
private fun MiniPlayerStep(onNext: () -> Unit) {
    val done = remember { mutableStateMapOf<Hint, Boolean>() }
    var track by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(true) }
    var opened by remember { mutableStateOf(0) }
    val tasks = listOf(
        Hint.SwipeLeft to tr("Смахни влево — следующий трек"),
        Hint.SwipeRight to tr("Смахни вправо — предыдущий"),
        Hint.SwipeUp to tr("Смахни вверх или нажми — откроется плеер"),
        Hint.Tap to tr("Кнопка справа — пауза и снова играть")
    )
    val pending = tasks.firstOrNull { done[it.first] != true }?.first
    val progress = rememberInfiniteTransition(label = "demoProgress")
    val position by progress.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing), RepeatMode.Restart),
        label = "demoPosition"
    )
    StepLayout(
        kicker = tr("Жест 1 из %s", GESTURE_STEPS),
        title = tr("Мини-плеер"),
        text = tr("Полоска внизу экрана, пока играет музыка, — на главной и в любом окне. " +
            "Переключать треки можно прямо на ней, не открывая плеер."),
        button = if (pending == null) tr("Дальше") else tr("Пропустить шаг"),
        onNext = onNext,
        tasks = tasks.map { (hint, label) -> label to (done[hint] == true) },
        demo = {
            ScreenFrame(place = tr("Внизу любого экрана"), aspect = 0.85f) {
                HomeMock()
                val demo = DemoTracks[track]
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalGlass provides false) {
                        PlayerBar(
                            title = demo.first,
                            artist = demo.second,
                            artworkUrl = null,
                            isPlaying = playing,
                            progress = { if (playing) position else 0.4f },
                            onTogglePlay = {
                                playing = !playing
                                done[Hint.Tap] = true
                            },
                            onOpen = {
                                opened++
                                done[Hint.SwipeUp] = true
                            },
                            onSwipe = { next ->
                                track = (track + (if (next) 1 else -1)).mod(DemoTracks.size)
                                done[if (next) Hint.SwipeLeft else Hint.SwipeRight] = true
                            }
                        )
                    }
                    // The finger over the bar, showing whatever has not been tried yet. It takes no
                    // touches of its own: they go through to the bar under it.
                    pending?.let { hint ->
                        GestureHint(
                            hint = hint,
                            modifier = Modifier.matchParentSize(),
                            anchor = if (hint == Hint.Tap) Alignment.CenterEnd else Alignment.Center,
                            anchorInset = if (hint == Hint.Tap) 36.dp else 0.dp
                        )
                    }
                }
                // What swiping up would have done, said where the player would have come from.
                AnimatedVisibility(
                    visible = opened > 0,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 12.dp)
                ) {
                    LaunchedEffect(opened) {
                        delay(1_600)
                        opened = 0
                    }
                    Notice(tr("Так открывается плеер — он дальше"))
                }
            }
        }
    )
}

@Composable
private fun PlayerCoverStep(onNext: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var swiped by remember { mutableStateOf(false) }
    var track by remember { mutableIntStateOf(0) }
    val tasks = listOf(
        tr("Смахни обложку вбок — соседний трек") to swiped,
        tr("Зажми обложку — меню трека") to pressed
    )
    StepLayout(
        kicker = tr("Жест 2 из %s", GESTURE_STEPS),
        title = tr("Плеер: обложка"),
        text = tr("Это уже сам плеер — он открывается из мини-плеера. Обложку можно смахнуть, чтобы " +
            "переключить трек, или зажать: там всё, что можно сделать с треком и музыкой — в плейлист, " +
            "поделиться, радио по нему, слушать вместе с друзьями, таймер сна, кроссфейд, перескачать " +
            "или удалить с телефона."),
        button = if (pressed && swiped) tr("Дальше") else tr("Пропустить шаг"),
        onNext = onNext,
        tasks = tasks,
        demo = {
            // The demo menu's crossfade: shown working, set nowhere.
            var crossfade by remember { mutableIntStateOf(0) }
            val slide = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            val demo = DemoTracks[track % DemoTracks.size]
            val hue = demoHue(track)
            ScreenFrame(place = tr("Плеер")) {
                PlayerMock(title = demo.first, artist = demo.second, hue = hue) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = slide.value
                                val away = (kotlin.math.abs(slide.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                scaleX = 1f - 0.08f * away
                                scaleY = 1f - 0.08f * away
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pressed = true
                                        menu = true
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        scope.launch { slide.snapTo(slide.value + amount) }
                                    },
                                    onDragEnd = {
                                        val width = size.width.toFloat()
                                        scope.launch {
                                            if (kotlin.math.abs(slide.value) > width * 0.25f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val side = if (slide.value < 0) -1f else 1f
                                                slide.animateTo(side * width, tween(160))
                                                track += if (side < 0) 1 else 2
                                                swiped = true
                                                slide.snapTo(-side * width * 0.5f)
                                            }
                                            slide.animateTo(0f, spring(dampingRatio = 0.82f, stiffness = 400f))
                                        }
                                    },
                                    onDragCancel = { scope.launch { slide.animateTo(0f) } }
                                )
                            }
                    ) {
                        DemoCover(hue = hue)
                    }
                    when {
                        !swiped -> GestureHint(hint = Hint.SwipeLeft, modifier = Modifier.matchParentSize())
                        !pressed -> GestureHint(hint = Hint.LongPress, modifier = Modifier.matchParentSize())
                    }
                }
            }
            if (menu) {
                // The real menu, on a demo track: whatever is picked only closes it.
                TrackActionsDialog(
                    track = SoundCloudTrack(id = DEMO_TRACK_ID, title = demo.first, user = SoundCloudUser(username = demo.second)),
                    playlists = emptyList(),
                    onDismiss = { menu = false },
                    onAddToPlaylist = { menu = false },
                    onCreatePlaylist = { menu = false },
                    onShare = { menu = false },
                    onRedownload = { menu = false },
                    onDeleteDownload = { menu = false },
                    onRadio = { menu = false },
                    radioDescription = tr("Трек, а за ним — похожие, без конца"),
                    onTogether = { menu = false },
                    crossfadeSeconds = crossfade,
                    onCrossfade = { crossfade = it },
                    demo = true
                )
            }
        }
    )
}

/**
 * The player as a whole: up, the queue comes after the finger; down, the player folds into the
 * mini player over the screen it was opened from — and the mini player opens it again.
 */
@Composable
private fun PlayerPullStep(onNext: () -> Unit) {
    var queued by remember { mutableStateOf(false) }
    var folded by remember { mutableStateOf(false) }
    // Folded at least once, for the list: it may have been opened again since.
    var foldedOnce by remember { mutableStateOf(false) }
    val tasks = listOf(
        tr("Смахни плеер вверх — выедет очередь") to queued,
        tr("Потяни плеер вниз — свернётся в мини-плеер") to foldedOnce
    )
    StepLayout(
        kicker = tr("Жест 3 из %s", GESTURE_STEPS),
        title = tr("Плеер: вверх и вниз"),
        text = tr("Смахни плеер вверх — выедет очередь. Потяни вниз — плеер свернётся обратно в " +
            "мини-плеер, а под ним будет экран, с которого ты его открыл."),
        button = if (queued && foldedOnce) tr("Дальше") else tr("Пропустить шаг"),
        onNext = onNext,
        tasks = tasks,
        demo = {
            val haptic = LocalHapticFeedback.current
            val scope = rememberCoroutineScope()
            val pull = remember { Animatable(0f) }
            val queue = remember { Animatable(0f) }
            val flingPx = with(LocalDensity.current) { QueueFlingVelocity.toPx() }
            ScreenFrame(place = tr("Плеер")) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    val sheetPx = heightPx * DEMO_QUEUE_FRACTION
                    fun progress() = (pull.value / heightPx).coerceIn(0f, 1f)
                    // Under the player: the screen it was opened from, with the mini player.
                    HomeMock()
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        CompositionLocalProvider(LocalGlass provides false) {
                            PlayerBar(
                                title = DemoTracks[0].first,
                                artist = DemoTracks[0].second,
                                artworkUrl = null,
                                isPlaying = true,
                                progress = { 0.4f },
                                onTogglePlay = {},
                                onOpen = {
                                    if (folded) {
                                        folded = false
                                        scope.launch { pull.animateTo(0f, QueueSpring) }
                                    }
                                }
                            )
                        }
                    }
                    // Over it, the player, dimming what is under it until it comes away. The drag is
                    // taken on a box that stays put: the player moving under the finger would eat
                    // into the finger's own movement.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val pulled = progress()
                                if (pulled < 1f) drawRect(Color.Black.copy(alpha = PulledScrim * (1f - pulled)))
                            }
                            .pointerInput(folded) {
                                if (folded) return@pointerInput
                                val velocity = VelocityTracker()
                                // 0 undecided, 1 the queue, 2 the player.
                                var moving = 0
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        velocity.resetTracking()
                                        moving = if (queue.value > 0f) 1 else 0
                                    },
                                    onVerticalDrag = { change, dy ->
                                        velocity.addPosition(change.uptimeMillis, change.position)
                                        change.consume()
                                        if (moving == 0) moving = if (dy < 0f) 1 else 2
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            if (moving == 1) {
                                                queue.snapTo((queue.value - dy / sheetPx).coerceIn(0f, 1f))
                                            } else {
                                                pull.snapTo((pull.value + dy).coerceAtLeast(0f))
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val speed = velocity.calculateVelocity().y
                                        if (moving == 1) {
                                            val open = if (kotlin.math.abs(speed) > flingPx) speed < 0f else queue.value > 0.35f
                                            if (open && !queued) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (open) queued = true
                                            scope.launch { queue.animateTo(if (open) 1f else 0f, QueueSpring) }
                                        } else if (moving == 2) {
                                            val away = if (kotlin.math.abs(speed) > flingPx) speed > 0f else progress() > CollapseMeantFraction
                                            if (away) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                folded = true
                                                foldedOnce = true
                                            }
                                            scope.launch {
                                                pull.animateTo(if (away) heightPx else 0f, if (away) CollapseSpring else QueueSpring, initialVelocity = speed)
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch { queue.animateTo(if (queue.value > 0.5f) 1f else 0f, QueueSpring) }
                                        scope.launch { pull.animateTo(0f, QueueSpring) }
                                    }
                                )
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize().pulledAway(pull, ::progress)) {
                            PlayerMock(title = DemoTracks[0].first, artist = DemoTracks[0].second, hue = demoHue(0)) {
                                DemoCover(hue = demoHue(0))
                            }
                            QueueSheetMock(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .fillMaxHeight(DEMO_QUEUE_FRACTION)
                                    .graphicsLayer { translationY = (1f - queue.value) * size.height }
                            )
                        }
                        when {
                            folded -> Unit
                            !queued -> GestureHint(hint = Hint.SwipeUp, modifier = Modifier.matchParentSize())
                            !foldedOnce -> GestureHint(hint = Hint.SwipeDown, modifier = Modifier.matchParentSize())
                        }
                    }
                    AnimatedVisibility(
                        visible = folded,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 12.dp)
                    ) {
                        Notice(tr("Свернулся. Нажми мини-плеер — откроется снова"))
                    }
                }
            }
        }
    )
}

/**
 * The windows opened over home — albums, playlists, artists, «Скачанное», «История» — have no back
 * button: pulled down, they close the way the player folds away, onto what they were opened from.
 * And in «Скачанное» a track held is the way to pick several.
 */
@Composable
private fun WindowsStep(onNext: () -> Unit) {
    var closedOnce by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(false) }
    val picked = remember { mutableStateMapOf<Int, Boolean>() }
    var pickedOnce by remember { mutableStateOf(false) }
    val tasks = listOf(
        tr("Потяни окно вниз — оно закроется") to closedOnce,
        tr("Зажми трек — можно выбрать несколько") to pickedOnce
    )
    StepLayout(
        kicker = tr("Жест 4 из %s", GESTURE_STEPS),
        title = tr("Окна закрываются свайпом"),
        text = tr("Альбомы, плейлисты, артисты, «Скачанное» и «История» закрываются как плеер: " +
            "потяни окно вниз. Кнопка «назад» осталась только в поиске и настройках. А в " +
            "«Скачанном» трек можно зажать, чтобы выбрать сразу несколько и удалить."),
        button = if (closedOnce && pickedOnce) tr("Дальше") else tr("Пропустить шаг"),
        onNext = onNext,
        tasks = tasks,
        demo = {
            val haptic = LocalHapticFeedback.current
            val scope = rememberCoroutineScope()
            val pull = remember { Animatable(0f) }
            val flingPx = with(LocalDensity.current) { QueueFlingVelocity.toPx() }
            ScreenFrame(place = tr("Окно «Скачанное»")) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    fun progress() = (pull.value / heightPx).coerceIn(0f, 1f)
                    // Closed, it comes back by itself after a moment, to be tried again.
                    LaunchedEffect(closed) {
                        if (!closed) return@LaunchedEffect
                        delay(1_800)
                        pull.animateTo(0f, QueueSpring)
                        closed = false
                    }
                    // What the window was opened from: home.
                    HomeMock()
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val pulled = progress()
                                if (pulled < 1f) drawRect(Color.Black.copy(alpha = PulledScrim * (1f - pulled)))
                            }
                            .pointerInput(closed) {
                                if (closed) return@pointerInput
                                val velocity = VelocityTracker()
                                detectVerticalDragGestures(
                                    onDragStart = { velocity.resetTracking() },
                                    onVerticalDrag = { change, dy ->
                                        velocity.addPosition(change.uptimeMillis, change.position)
                                        change.consume()
                                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            pull.snapTo((pull.value + dy).coerceAtLeast(0f))
                                        }
                                    },
                                    onDragEnd = {
                                        val speed = velocity.calculateVelocity().y
                                        val away = if (kotlin.math.abs(speed) > flingPx) speed > 0f else progress() > CollapseMeantFraction
                                        if (away) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            closed = true
                                            closedOnce = true
                                            picked.clear()
                                        }
                                        scope.launch {
                                            pull.animateTo(if (away) heightPx else 0f, if (away) CollapseSpring else QueueSpring, initialVelocity = speed)
                                        }
                                    },
                                    onDragCancel = { scope.launch { pull.animateTo(0f, QueueSpring) } }
                                )
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize().pulledAway(pull, ::progress)) {
                            WindowMock(
                                picked = picked,
                                onHold = { row ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    picked[row] = true
                                    pickedOnce = true
                                },
                                onTap = { row ->
                                    if (picked.isNotEmpty()) {
                                        if (picked[row] == true) picked.remove(row) else picked[row] = true
                                    }
                                },
                                onCancel = { picked.clear() },
                                hint = { over ->
                                    if (closedOnce && !pickedOnce) GestureHint(hint = Hint.LongPress, modifier = over)
                                }
                            )
                        }
                        if (!closedOnce && !closed) GestureHint(hint = Hint.SwipeDown, modifier = Modifier.matchParentSize())
                    }
                    AnimatedVisibility(
                        visible = closed,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 12.dp)
                    ) {
                        Notice(tr("Закрылось — под ним главная"))
                    }
                }
            }
        }
    )
}

@Composable
private fun WaveStep(onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var tuning by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val picks = remember { mutableStateMapOf<String, String>() }
    val mood = WaveTuning.moods.firstOrNull { it.seed == picks[WaveTuning.MOOD] }
    val tasks = listOf(
        tr("Зажми форму — откроются настройки волны") to pressed,
        tr("Выбери настроение — форма изменится") to (mood != null)
    )
    StepLayout(
        kicker = tr("Жест 5 из %s", GESTURE_STEPS),
        title = tr("«Моя форма» на главной"),
        text = tr("Во вкладке Яндекс Музыки на главной. Нажатие включает волну, а если подержать — " +
            "откроются настроение и режим."),
        button = if (pressed && mood != null) tr("Дальше") else tr("Пропустить шаг"),
        onNext = onNext,
        tasks = tasks,
        demo = {
            ScreenFrame(place = tr("Главная · Яндекс Музыка")) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(tr("Моя форма"), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            WaveShape(
                                lobes = mood?.lobes ?: 8,
                                depth = mood?.depth ?: 0.07f,
                                light = lerp(mood?.color?.takeIf { it != Color.Unspecified } ?: colors.primary, Color.White, 0.3f),
                                color = mood?.color?.takeIf { it != Color.Unspecified } ?: colors.primary,
                                playing = false,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                pressed = true
                                                tuning = true
                                            }
                                        )
                                    }
                            )
                            if (!pressed) GestureHint(hint = Hint.LongPress, modifier = Modifier.matchParentSize())
                        }
                    }
                }
            }
            // The pane over the whole demo, as wide as it is on home: the small screen is too narrow
            // for it.
            if (tuning) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            tuning = false
                        }
                )
            }
            AnimatedVisibility(
                visible = tuning,
                enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.7f, stiffness = 520f), initialScale = 0.85f),
                exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.92f),
                modifier = Modifier.align(Alignment.Center)
            ) {
                WaveTuningPane(
                    picks = picks,
                    onPick = { key, seed ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (picks[key] == seed) picks.remove(key) else picks[key] = seed
                    },
                    onReset = { picks.clear() }
                )
            }
        }
    )
}

@Composable
private fun DoneStep(onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    StepLayout(
        kicker = tr("Готово"),
        title = tr("Можно слушать"),
        text = tr("Показать это ещё раз можно в любой момент: Настройки → Оформление → Обучение жестам."),
        button = tr("Начать"),
        onNext = onNext,
        demo = {
            Surface(
                shape = CircleShape,
                color = colors.primary,
                contentColor = colors.onPrimary,
                modifier = Modifier.size(140.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(72.dp))
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------- Small screens

/**
 * A small screen around a demo, named on a tab over it: where in the app the gesture lives — home,
 * the player, a window — so that none is taken for another place's.
 */
@Composable
private fun ScreenFrame(
    place: String,
    // Width to height: a phone's by default; wider where what is shown needs the width (the mini
    // player's bar, which cramped its title in a phone's proportions).
    aspect: Float = 0.56f,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = PanelColors.container,
            contentColor = PanelColors.accent
        ) {
            Text(
                text = place,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                // Sideways there is less height to go round: a squarer screen keeps it usable.
                .aspectRatio(if (isLandscape()) maxOf(aspect, 0.8f) else aspect, matchHeightConstraintsFirst = true)
                .clip(shape)
                .background(colors.background)
                .border(1.dp, colors.outlineVariant, shape),
            content = content
        )
    }
}

/**
 * What a pulled-down screen does, as the real ones do (see pullToClose): it follows the finger,
 * shrinking back with its corners rounding, and fades the last of the way.
 */
private fun Modifier.pulledAway(pull: Animatable<Float, *>, progress: () -> Float): Modifier =
    graphicsLayer {
        val pulled = progress()
        translationY = pull.value
        val scale = 1f - 0.08f * pulled
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin(0.5f, 0f)
        if (pulled > 0f) {
            shape = RoundedCornerShape(24.dp * (pulled / 0.15f).coerceAtMost(1f))
            clip = true
        }
        alpha = 1f - ((pulled - CollapseFadeFrom) / (1f - CollapseFadeFrom)).coerceIn(0f, 1f)
    }

/** Home as the small screens show it: a section's title and the cover in front of its carousel. */
@Composable
private fun HomeMock() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(tr("Моя музыка"), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(lerp(colors.tertiary, Color.White, 0.2f), lerp(colors.primary, Color.Black, 0.3f))))
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(tr("Скачанное"), style = MaterialTheme.typography.titleSmall, maxLines = 1)
    }
}

/**
 * The player as the small screens show it: the cover on top ([cover], where a step puts what it
 * does with it), the panel under it with the title, the line of the track and the controls.
 */
@Composable
private fun PlayerMock(title: String, artist: String, hue: Color, cover: @Composable BoxScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lerp(colors.background, hue, 0.14f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp)
                .aspectRatio(1f),
            content = cover
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(PanelColors.container)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = PanelColors.content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.bodySmall, color = PanelColors.content.copy(alpha = 0.7f), maxLines = 1)
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(PanelColors.content.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(PanelColors.accent)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MockControl(Icons.Rounded.SkipPrevious, 36.dp, accent = false)
                MockControl(Icons.Rounded.Pause, 50.dp, accent = true)
                MockControl(Icons.Rounded.SkipNext, 36.dp, accent = false)
            }
        }
    }
}

@Composable
private fun MockControl(icon: ImageVector, size: Dp, accent: Boolean) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(if (accent) RoundedCornerShape(size * 0.3f) else CircleShape)
            .background(if (accent) PanelColors.accent else PanelColors.content.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) PanelColors.onAccent else PanelColors.content,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** A demo track's cover: its colour, so a swipe visibly lands on another, and a note. */
@Composable
private fun DemoCover(hue: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(lerp(hue, Color.White, 0.25f), lerp(hue, Color.Black, 0.35f)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxSize(0.38f)
        )
    }
}

@Composable
private fun demoHue(track: Int): Color {
    val colors = MaterialTheme.colorScheme
    return listOf(colors.primary, colors.tertiary, colors.secondary)[track.mod(3)]
}

/** The queue as the player pulls it up: what plays next, under a handle. */
@Composable
private fun QueueSheetMock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(PanelColors.container)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(PanelColors.content.copy(alpha = 0.3f))
        )
        Text(tr("ОЧЕРЕДЬ"), style = MaterialTheme.typography.labelMedium, color = PanelColors.accent)
        DemoTracks.drop(1).forEachIndexed { index, (title, artist) ->
            MockRow(title = title, artist = artist, hue = demoHue(index + 1), content = PanelColors.content)
        }
    }
}

@Composable
private fun MockRow(title: String, artist: String, hue: Color, content: Color, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(lerp(hue, Color.White, 0.25f), lerp(hue, Color.Black, 0.35f))))
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.7f), maxLines = 1)
        }
        trailing()
    }
}

/**
 * «Скачанное» as the small screens show it: its picture and name on top, a few tracks, and — once
 * one is held — the marks of picking and the toolbar at the foot. [hint] is laid over the first
 * track, for the finger that shows holding one.
 */
@Composable
private fun WindowMock(
    picked: Map<Int, Boolean>,
    onHold: (Int) -> Unit,
    onTap: (Int) -> Unit,
    onCancel: () -> Unit,
    hint: @Composable (Modifier) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val picking = picked.isNotEmpty()
    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.3f)
                    .background(Brush.verticalGradient(listOf(lerp(colors.secondary, Color.Black, 0.2f), colors.background)))
                    .padding(14.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(tr("ПАПКА"), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(tr("Скачанное"), style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                DemoTracks.forEachIndexed { index, (title, artist) ->
                    val on = picked[index] == true
                    Box {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (on) colors.secondaryContainer else Color.Transparent)
                                .pointerInput(index) {
                                    detectTapGestures(onLongPress = { onHold(index) }, onTap = { onTap(index) })
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            MockRow(title = title, artist = artist, hue = demoHue(index), content = colors.onSurface) {
                                if (picking) {
                                    Icon(
                                        imageVector = if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (on) colors.primary else colors.onSurface.copy(alpha = 0.5f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                        if (index == 0) hint(Modifier.matchParentSize())
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = picking,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PanelColors.container)
                        .clickable(onClick = onCancel)
                        .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = tr("Отменить выбор"), tint = PanelColors.content, modifier = Modifier.size(20.dp))
                    Text(tr("Выбрано: %s", picked.size), style = MaterialTheme.typography.labelLarge, color = PanelColors.content)
                }
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PanelColors.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = PanelColors.onAccent, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- Pieces

/**
 * A step: what it is about, the thing to try, what trying it involves, and the way on. Upright the
 * demo sits between the text and the list; sideways, the text and the list on the left and the demo
 * beside them, where the thumb is.
 */
@Composable
private fun StepLayout(
    kicker: String,
    title: String,
    text: String,
    button: String,
    onNext: () -> Unit,
    tasks: List<Pair<String, Boolean>> = emptyList(),
    demo: @Composable BoxScope.() -> Unit
) {
    val words: @Composable ColumnScope.() -> Unit = {
        Text(
            text = kicker.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = title, style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val checklist: @Composable ColumnScope.() -> Unit = {
        if (tasks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tasks.forEach { (label, done) -> TaskRow(label, done) }
            }
        }
    }
    val action: @Composable () -> Unit = {
        val allDone = tasks.isEmpty() || tasks.all { it.second }
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = if (allDone) {
                ButtonDefaults.buttonColors()
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        ) {
            Text(button, style = MaterialTheme.typography.titleMedium)
        }
    }

    if (isLandscape()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                words()
                Spacer(modifier = Modifier.height(14.dp))
                checklist()
                Spacer(modifier = Modifier.weight(1f))
                action()
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center,
                content = demo
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            words()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
                content = demo
            )
            checklist()
            Spacer(modifier = Modifier.height(16.dp))
            action()
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TaskRow(label: String, done: Boolean) {
    val tint by animateColorAsState(
        if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        tween(250),
        label = "taskTint"
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedContent(targetState = done, transitionSpec = { scaleIn() togetherWith scaleOut() }, label = "taskIcon") { checked ->
            Icon(
                imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (done) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Where the steps are: a pill for the one in view, dots for the rest. */
@Composable
private fun StepDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val width by androidx.compose.animation.core.animateDpAsState(
                if (index == current) 24.dp else 8.dp,
                spring(dampingRatio = 0.7f, stiffness = 500f),
                label = "dotWidth"
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= current) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                    )
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

/**
 * A finger doing [hint] over whatever lies under it, on a loop: a touch mark where it presses and a
 * hand beside it, travelling for a swipe and holding, ringed, for a long press. It takes no touches
 * itself. [anchor] is where in its bounds the gesture happens, [anchorInset] how far in from that
 * edge.
 */
@Composable
private fun GestureHint(
    hint: Hint,
    modifier: Modifier = Modifier,
    anchor: Alignment = Alignment.Center,
    anchorInset: Dp = 0.dp
) {
    val loop = rememberInfiniteTransition(label = "gestureHint")
    val t by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(HINT_LOOP_MILLIS, easing = LinearEasing)),
        label = "gestureHintT"
    )
    val density = LocalDensity.current
    val travel = with(density) { 90.dp.toPx() }
    val inset = with(density) { anchorInset.toPx() }
    val mark = MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(anchor)
                .padding(
                    end = if (anchor == Alignment.CenterEnd) anchorInset else 0.dp
                )
                .size(80.dp)
                .graphicsLayer {
                    // Appear, act, lift, rest: the phases of one loop.
                    val appear = (t / 0.12f).coerceIn(0f, 1f)
                    val act = ((t - 0.12f) / 0.55f).coerceIn(0f, 1f)
                    val lifted = ((t - 0.72f) / 0.12f).coerceIn(0f, 1f)
                    val eased = FastOutSlowInEasing.transform(act)
                    alpha = appear * (1f - lifted)
                    when (hint) {
                        Hint.SwipeLeft -> translationX = travel * 0.5f - travel * eased
                        Hint.SwipeRight -> translationX = -travel * 0.5f + travel * eased
                        Hint.SwipeUp -> translationY = travel * 0.25f - travel * 0.7f * eased
                        Hint.SwipeDown -> translationY = -travel * 0.25f + travel * 0.7f * eased
                        Hint.Tap, Hint.LongPress -> {
                            val press = if (act > 0f && lifted == 0f) 0.88f else 1f
                            scaleX = press
                            scaleY = press
                        }
                    }
                    if (inset != 0f && anchor != Alignment.CenterEnd) translationX += inset
                }
                .drawBehind {
                    val radius = size.minDimension * 0.22f
                    // The touch itself.
                    drawCircle(color = mark.copy(alpha = 0.22f), radius = radius * 1.35f)
                    drawCircle(color = Color.White.copy(alpha = 0.92f), radius = radius)
                    // A long press rings out while it holds.
                    if (hint == Hint.LongPress) {
                        val hold = ((t - 0.12f) / 0.55f).coerceIn(0f, 1f)
                        if (hold > 0f && t < 0.72f) {
                            drawCircle(
                                color = mark.copy(alpha = 0.5f * (1f - hold)),
                                radius = radius * (1.2f + 1.3f * hold),
                                style = Stroke(width = 3.dp.toPx())
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.TouchApp,
                contentDescription = null,
                tint = mark.copy(alpha = 0.85f),
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        translationX = 14.dp.toPx()
                        translationY = 22.dp.toPx()
                    }
            )
        }
    }
}

// What the demo plays: made up, nobody's music. In the app's language as it is shown.
private val DemoTracks: List<Pair<String, String>>
    get() = listOf(
        tr("Ночной город") to tr("Демо-исполнитель"),
        tr("Тёплый ветер") to tr("Демо-исполнитель"),
        tr("Последний поезд") to tr("Демо-исполнитель")
    )
private const val DEMO_TRACK_ID = -7_777_777L
private const val HINT_LOOP_MILLIS = 2_000
// How much of the small player the queue takes when pulled up.
private const val DEMO_QUEUE_FRACTION = 0.62f
