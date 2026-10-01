package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the wave can be tuned by, and each value's seed for Rotor. */
internal object WaveTuning {
    const val MOOD = "mood"
    const val MODE = "mode"

    val moods = listOf(
        WaveChoice("settingMoodEnergy:active", "Бодрое", lobes = 12, depth = 0.085f, color = Color(0xFFFF7A4D)),
        WaveChoice("settingMoodEnergy:fun", "Весёлое", lobes = 6, depth = 0.15f, color = Color(0xFFFFC53D)),
        WaveChoice("settingMoodEnergy:calm", "Спокойное", lobes = 9, depth = 0.06f, color = Color(0xFF3DD3B0)),
        WaveChoice("settingMoodEnergy:sad", "Грустное", lobes = 5, depth = 0.04f, color = Color(0xFF9D8CFF))
    )

    val modes = listOf(
        WaveChoice("settingDiversity:favorite", "Любимое"),
        WaveChoice("settingDiversity:discover", "Незнакомое"),
        WaveChoice("settingDiversity:popular", "Популярное")
    )
}

/** One value to tune the wave by; a mood also has its own shape and colour. */
internal class WaveChoice(
    val seed: String,
    val title: String,
    val lobes: Int = DEFAULT_LOBES,
    val depth: Float = DEFAULT_DEPTH,
    val color: Color = Color.Unspecified
)

/**
 * Yandex Music's wave on home, as "Моя форма": one large shape in the theme's colours in the middle
 * of the page, turning slowly while the wave plays, and its play button in the middle of it —
 * round, squaring while it plays. The shape takes the form of the mood picked.
 *
 * Held down, it brings up the wave's settings — mood and mode — on a pane of the frosted glass the
 * rest of home stands on: that glass is drawn from the backdrop's own softened layer, not blurred
 * live, so it costs a weak phone nothing more than the rest of home does.
 */
@Composable
internal fun MyWavePage(
    waveOn: Boolean,
    isPlaying: Boolean,
    starting: Boolean,
    picks: Map<String, String>,
    onToggle: () -> Unit,
    onPick: (key: String, seed: String) -> Unit,
    onReset: () -> Unit
) {
    val playing = waveOn && isPlaying
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    var tuning by rememberSaveable { mutableStateOf(false) }
    val mood = WaveTuning.moods.firstOrNull { it.seed == picks[WaveTuning.MOOD] }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val side = min(maxWidth.value * 0.8f, maxHeight.value * 0.8f).dp
            WaveShape(
                lobes = mood?.lobes ?: DEFAULT_LOBES,
                depth = mood?.depth ?: DEFAULT_DEPTH,
                light = lerp(colors.primary, Color.White, 0.3f),
                color = colors.primary,
                playing = playing,
                modifier = Modifier
                    .size(side)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                tuning = true
                            }
                        )
                    }
            )
            val corner by animateFloatAsState(
                targetValue = if (playing) 30f else 50f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
                label = "wavePlayCorner"
            )
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(percent = corner.roundToInt().coerceIn(0, 50)),
                color = colors.onPrimary,
                contentColor = colors.primary,
                modifier = Modifier.size(side * 0.36f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (starting) {
                        CircularProgressIndicator(modifier = Modifier.size(side * 0.14f), strokeWidth = 3.dp, color = colors.primary)
                    } else {
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Пауза" else "Слушать",
                            modifier = Modifier.size(side * 0.19f)
                        )
                    }
                }
            }
        }

        // A tap beside the settings puts them away. Not dimmed: the page is only part of the
        // screen, and a shade over it stood out as a dark box.
        if (tuning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
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
                    onPick(key, seed)
                },
                onReset = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onReset()
                }
            )
        }
    }
    // Not from under a page opened over home, or the player: the back gesture is theirs.
    val covered = LocalCovered.current
    val hidden by remember(covered) { androidx.compose.runtime.derivedStateOf(covered) }
    if (tuning && !hidden) BackHandler { tuning = false }
}

/** The wave's settings on frosted glass: the moods and the modes, a connected button group each. */
@Composable
internal fun WaveTuningPane(
    picks: Map<String, String>,
    onPick: (key: String, seed: String) -> Unit,
    onReset: () -> Unit
) {
    val shape = RoundedCornerShape(36.dp)
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            // Frosted a little thicker than home's panels, for the settings to read over the shape.
            .glassOr(shape, colors.surfaceContainerHigh, glassAlpha = 0.74f)
            .background(glassFill(colors.surfaceContainerHigh), shape)
            // Taps inside don't reach the page behind, where they would put the pane away.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Настроить волну",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (picks.isNotEmpty()) TextButton(onClick = onReset) { Text("Сбросить") }
        }
        PaneLabel("Настроение")
        ChoiceGroup(
            choices = WaveTuning.moods,
            picked = picks[WaveTuning.MOOD],
            onPick = { onPick(WaveTuning.MOOD, it) },
            // Four in a row: a size smaller, for "Спокойное" to fit beside the others.
            textStyle = MaterialTheme.typography.labelMedium
        )
        PaneLabel("Режим")
        ChoiceGroup(
            choices = WaveTuning.modes,
            picked = picks[WaveTuning.MODE],
            onPick = { onPick(WaveTuning.MODE, it) },
            textStyle = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun PaneLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * [choices] as a connected button group: joined by small gaps, the outer corners round and the
 * inner ones tight; the one picked swells and rounds off entirely. Picked again, it is let go.
 */
@Composable
private fun ChoiceGroup(
    choices: List<WaveChoice>,
    picked: String?,
    onPick: (String) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp)
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        choices.forEachIndexed { index, mode ->
            val selected = mode.seed == picked
            val weight by animateFloatAsState(
                targetValue = if (selected) 1.3f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
                label = "modeWeight"
            )
            val inner by animateFloatAsState(
                targetValue = if (selected) 50f else 14f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                label = "modeCorner"
            )
            val innerPercent = inner.roundToInt().coerceIn(0, 50)
            val start = if (index == 0) 50 else innerPercent
            val end = if (index == choices.lastIndex) 50 else innerPercent
            val fill by animateColorAsState(
                if (selected) colors.primary else colors.onSurface.copy(alpha = 0.08f),
                tween(250),
                label = "modeFill"
            )
            val content by animateColorAsState(
                if (selected) colors.onPrimary else colors.onSurface,
                tween(250),
                label = "modeContent"
            )
            Surface(
                onClick = { onPick(mode.seed) },
                shape = RoundedCornerShape(
                    topStartPercent = start,
                    topEndPercent = end,
                    bottomEndPercent = end,
                    bottomStartPercent = start
                ),
                color = fill,
                contentColor = content,
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(
                        text = mode.title,
                        style = textStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * The page's shape: [lobes] ripples [depth] deep around a circle, [light] at its top left to
 * [color], with a paler one inside; both turn slowly, in opposite directions, while [turning]. A new
 * mood's form is flowed into with a little overshoot. Only drawn again while it moves, no more
 * than 60 times a second.
 */
@Composable
internal fun WaveShape(
    lobes: Int,
    depth: Float,
    light: Color,
    color: Color,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    var from by remember { mutableStateOf(lobes to depth) }
    var to by remember { mutableStateOf(lobes to depth) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(lobes, depth) {
        if (to == lobes to depth) return@LaunchedEffect
        from = to
        to = lobes to depth
        morph.snapTo(0f)
        morph.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    // Its turn, in radians: slowly by itself, a little faster while the wave plays, and spun by a
    // shake of the phone when the backdrop follows its movement — flung the way it was thrown, then
    // slowing. Redrawn thirty times a second while it only turns, the backdrop's own pace when it
    // is still; sixty while playing or spun.
    val turn = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    // Still under a page or the player, where it isn't drawn: turning on there redrew the window.
    val covered = LocalCovered.current
    LaunchedEffect(playing, covered) {
        snapshotFlow { covered() }.collectLatest { hidden ->
            if (hidden) return@collectLatest
            BackdropShake.take()
            var angle = turn.floatValue
            var spin = 0f
            var last = 0L
            var shown = -1L
            while (true) {
                androidx.compose.runtime.withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    last = now
                    spin = (spin + BackdropShake.take() * SHAKE_SPIN).coerceIn(-MAX_SPIN, MAX_SPIN)
                    spin *= kotlin.math.exp(-SPIN_DAMPING * dt)
                    angle += ((if (playing) PLAYING_TURN else IDLE_TURN) + spin) * dt
                    // On the frames the backdrop and the clocks move on (see frameSlot).
                    val slot = frameSlot(now, MotionPace.slotNanos(lively = playing || kotlin.math.abs(spin) > 0.05f))
                    if (slot != shown) {
                        shown = slot
                        turn.floatValue = angle
                    }
                }
            }
        }
    }
    val outline = remember { Path() }
    Canvas(modifier = modifier) {
        val t = morph.value
        val turn = turn.floatValue
        val radius = size.minDimension * 0.44f
        outline.traceMorph(center, radius, from, to, t, turn)
        drawPath(
            path = outline,
            brush = Brush.radialGradient(
                colors = listOf(light, color),
                center = center + Offset(-radius * 0.3f, -radius * 0.35f),
                radius = radius * 1.6f
            )
        )
        outline.traceMorph(center, radius * 0.72f, from, to, t, -turn * 1.6f)
        drawPath(path = outline, color = Color.White.copy(alpha = 0.14f))
    }
}

/** Part way, [t], from one rippled circle to another: the radii blended, angle by angle. */
private fun Path.traceMorph(
    center: Offset,
    radius: Float,
    from: Pair<Int, Float>,
    to: Pair<Int, Float>,
    t: Float,
    rotation: Float
) {
    reset()
    for (i in 0..SHAPE_STEPS) {
        val a = i.toFloat() / SHAPE_STEPS * 2f * PI.toFloat()
        val r0 = 1f + from.second * cos(from.first * (a + rotation))
        val r1 = 1f + to.second * cos(to.first * (a + rotation))
        val r = radius * (r0 + (r1 - r0) * t)
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private const val SHAPE_STEPS = 180
// Radians a second: by itself, and while the wave plays.
private const val IDLE_TURN = 0.12f
private const val PLAYING_TURN = 0.35f
// A firm shake (a few units of the backdrop's kick) spins it a turn or so a second; it slows to
// its own pace again over two or three.
private const val SHAKE_SPIN = 3f
private const val MAX_SPIN = 14f
private const val SPIN_DAMPING = 1.1f
private const val DEFAULT_LOBES = 8
private const val DEFAULT_DEPTH = 0.07f
