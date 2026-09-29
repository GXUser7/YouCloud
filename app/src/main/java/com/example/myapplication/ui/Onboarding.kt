package com.example.myapplication.ui

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
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.SoundCloudUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The first time the app opens: the gestures that nothing on screen advertises, each shown by a
 * finger doing it and then tried out on the real thing — the mini player, «Моя форма», the cover.
 *
 * Real components rather than pictures of them: the same mini player, wave shape, tuning pane and
 * track menu the app uses, fed a demo track instead of a real one. Whatever is learnt here is then
 * exactly what the user finds. A step can be done in any order and skipped; nothing is required.
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
                if (step < last) TextButton(onClick = onFinish) { Text("Пропустить") }
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
                    OnboardingStep.Wave -> WaveStep(onNext = next)
                    OnboardingStep.Cover -> CoverStep(onNext = next)
                    OnboardingStep.Done -> DoneStep(onNext = next)
                }
            }
        }
    }
}

private enum class OnboardingStep { Intro, MiniPlayer, Wave, Cover, Done }

/** The gestures a finger demonstrates. */
private enum class Hint { SwipeLeft, SwipeRight, SwipeUp, Tap, LongPress }

// ------------------------------------------------------------------------------------ Steps

@Composable
private fun IntroStep(onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    StepLayout(
        kicker = "Добро пожаловать",
        title = "Это YouCloud",
        text = "SoundCloud, Яндекс Музыка и YouTube Music в одном плеере. Кое-что здесь спрятано " +
            "в жестах — покажу за минуту, и всё можно сразу попробовать.",
        button = "Показать",
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
        Hint.SwipeLeft to "Смахни влево — следующий трек",
        Hint.SwipeRight to "Смахни вправо — предыдущий",
        Hint.SwipeUp to "Смахни вверх или нажми — откроется плеер",
        Hint.Tap to "Кнопка справа — пауза и снова играть"
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
        kicker = "Жест 1 из 3",
        title = "Мини-плеер понимает жесты",
        text = "Он внизу экрана, пока играет музыка. Не нужно открывать плеер, чтобы переключить трек.",
        button = if (pending == null) "Дальше" else "Пропустить шаг",
        onNext = onNext,
        tasks = tasks.map { (hint, label) -> label to (done[hint] == true) },
        demo = {
            val demo = DemoTracks[track]
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .padding(horizontal = 8.dp),
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
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                LaunchedEffect(opened) {
                    delay(1_600)
                    opened = 0
                }
                Notice("Так открывается плеер")
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
        "Зажми форму — откроются настройки волны" to pressed,
        "Выбери настроение — форма изменится" to (mood != null)
    )
    StepLayout(
        kicker = "Жест 2 из 3",
        title = "Зажми «Мою форму»",
        text = "На главной во вкладке Яндекс Музыки. Нажатие включает волну, а если подержать — " +
            "откроются настроение и режим.",
        button = if (pressed && mood != null) "Дальше" else "Пропустить шаг",
        onNext = onNext,
        tasks = tasks,
        demo = {
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.9f)
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
private fun CoverStep(onNext: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var swiped by remember { mutableStateOf(false) }
    var track by remember { mutableIntStateOf(0) }
    val tasks = listOf(
        "Зажми обложку — меню трека" to pressed,
        "Смахни обложку вбок — соседний трек" to swiped
    )
    StepLayout(
        kicker = "Жест 3 из 3",
        title = "Зажми обложку в плеере",
        text = "Там всё, что можно сделать с треком: в плейлист, поделиться, радио по нему, " +
            "перескачать. А смахнув обложку в сторону, переключишь трек.",
        button = if (pressed && swiped) "Дальше" else "Пропустить шаг",
        onNext = onNext,
        tasks = tasks,
        demo = {
            val slide = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            val colors = MaterialTheme.colorScheme
            // Each demo track its own colours, so a swipe visibly lands on another one.
            val hue = listOf(colors.primary, colors.tertiary, colors.secondary)[track % 3]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.72f)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = slide.value
                                val away = (kotlin.math.abs(slide.value) / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                                scaleX = 1f - 0.08f * away
                                scaleY = 1f - 0.08f * away
                            }
                            .clip(RoundedCornerShape(36.dp))
                            .background(Brush.linearGradient(listOf(lerp(hue, Color.White, 0.25f), lerp(hue, Color.Black, 0.35f))))
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
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.fillMaxSize(0.38f)
                        )
                    }
                    when {
                        !pressed -> GestureHint(hint = Hint.LongPress, modifier = Modifier.matchParentSize())
                        !swiped -> GestureHint(hint = Hint.SwipeLeft, modifier = Modifier.matchParentSize())
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(DemoTracks[track % DemoTracks.size].first, style = MaterialTheme.typography.titleLarge)
                Text(
                    DemoTracks[track % DemoTracks.size].second,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (menu) {
                val demo = DemoTracks[track % DemoTracks.size]
                // The real menu, on a demo track: whatever is picked only closes it.
                TrackActionsDialog(
                    track = SoundCloudTrack(id = DEMO_TRACK_ID, title = demo.first, user = SoundCloudUser(username = demo.second)),
                    playlists = emptyList(),
                    onDismiss = { menu = false },
                    onAddToPlaylist = { menu = false },
                    onCreatePlaylist = { menu = false },
                    onShare = { menu = false },
                    onRedownload = { menu = false },
                    onRadio = { menu = false },
                    radioDescription = "Трек, а за ним — похожие, без конца"
                )
            }
        }
    )
}

@Composable
private fun DoneStep(onNext: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    StepLayout(
        kicker = "Готово",
        title = "Можно слушать",
        text = "Показать это ещё раз можно в любой момент: Настройки → Оформление → Обучение жестам.",
        button = "Начать",
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

// What the demo plays: made up, nobody's music.
private val DemoTracks = listOf(
    "Ночной город" to "Демо-исполнитель",
    "Тёплый ветер" to "Демо-исполнитель",
    "Последний поезд" to "Демо-исполнитель"
)
private const val DEMO_TRACK_ID = -7_777_777L
private const val HINT_LOOP_MILLIS = 2_000
