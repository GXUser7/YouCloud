package com.example.myapplication.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.YandexWave
import com.example.myapplication.data.YandexWaveOption
import com.example.myapplication.data.YandexWaveSettings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The key an occasion is picked under, beside the settings' own. */
internal const val WAVE_OCCASION = "occasion"

/**
 * Yandex Music's wave on home ("Моя форма"). A large shape in the mood's colours and of the mood's
 * own form — a burst for lively, a flower for fun, a soft cookie for calm, nearly round for sad —
 * that flows into the next one as the mood changes and turns slowly while the wave plays; its
 * play button in the middle, round, squaring while it plays. Under it the wave's settings as
 * connected button groups (mood, character, language) and a row of occasions, each an icon button.
 * Picking one while the wave plays tunes it at once.
 */
@Composable
internal fun MyWavePage(
    settings: YandexWaveSettings?,
    picks: Map<String, String>,
    waveOn: Boolean,
    isPlaying: Boolean,
    starting: Boolean,
    bottomClearance: Dp,
    onToggle: () -> Unit,
    onPick: (key: String, seed: String) -> Unit,
    onAppear: () -> Unit
) {
    LaunchedEffect(Unit) { onAppear() }
    val shown = settings ?: YandexWave.DEFAULT
    val playing = waveOn && isPlaying
    val moodGroup = shown.groups.firstOrNull { it.key == YandexWave.MOOD }
    val mood = moodGroup?.options?.firstOrNull { it.seed == picks[YandexWave.MOOD] }?.value
    val look = moodLook(mood)
    val strong by animateColorAsState(look.strong, tween(700), label = "waveStrong")
    val soft by animateColorAsState(look.soft, tween(700), label = "waveSoft")
    val deep by animateColorAsState(look.deep, tween(700), label = "waveDeep")
    val picked = shown.groups.mapNotNull { group -> group.options.firstOrNull { it.seed == picks[group.key] }?.title } +
        listOfNotNull(shown.occasions.firstOrNull { it.seed == picks[WAVE_OCCASION] }?.title)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = bottomClearance),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val side = min(maxWidth.value * 0.74f, maxHeight.value * 0.94f).dp
            MoodShape(look = look, strong = strong, soft = soft, turning = playing, modifier = Modifier.size(side))
            val corner by animateFloatAsState(
                targetValue = if (playing) 30f else 50f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 420f),
                label = "wavePlayCorner"
            )
            Surface(
                onClick = onToggle,
                shape = RoundedCornerShape(percent = corner.roundToInt().coerceIn(0, 50)),
                color = deep,
                contentColor = soft,
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (starting) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp, color = soft)
                    } else {
                        Icon(
                            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Пауза" else "Слушать",
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = if (picked.isEmpty()) "Бесконечно, под твой вкус" else picked.joinToString(" · "),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.groups.forEach { group ->
                val isMood = group.key == YandexWave.MOOD
                ConnectedChoices(
                    options = group.options,
                    picked = picks[group.key],
                    onPick = { onPick(group.key, it) },
                    // Each mood in its own colour; the other settings in the one picked.
                    selectedFill = { option -> if (isMood) moodLook(option.value).strong else soft },
                    selectedContent = { option -> if (isMood) moodLook(option.value).deep else deep },
                    idleFill = { option -> if (isMood) moodLook(option.value).strong.copy(alpha = 0.16f) else IdleFill },
                    idleContent = { option -> if (isMood) moodLook(option.value).soft else IdleContent }
                )
            }
        }
        if (shown.occasions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Occasions(
                options = shown.occasions,
                picked = picks[WAVE_OCCASION],
                fill = strong,
                content = deep,
                onPick = { onPick(WAVE_OCCASION, it) }
            )
        }
    }
}

private val IdleFill = Color.White.copy(alpha = 0.08f)
private val IdleContent = Color.White.copy(alpha = 0.86f)

/**
 * A connected button group: a row of buttons joined by small gaps, the outer corners round and
 * the inner ones tight; the one picked swells and rounds off entirely, as Material 3 Expressive's
 * groups do. Picking it again leaves the setting to the wave.
 */
@Composable
private fun ConnectedChoices(
    options: List<YandexWaveOption>,
    picked: String?,
    onPick: (String) -> Unit,
    selectedFill: (YandexWaveOption) -> Color,
    selectedContent: (YandexWaveOption) -> Color,
    idleFill: (YandexWaveOption) -> Color,
    idleContent: (YandexWaveOption) -> Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, option ->
            val selected = option.seed == picked
            val weight by animateFloatAsState(
                targetValue = if (selected) 1.3f else 1f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
                label = "choiceWeight"
            )
            val inner by animateFloatAsState(
                targetValue = if (selected) 50f else 14f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                label = "choiceCorner"
            )
            val innerPercent = inner.roundToInt().coerceIn(0, 50)
            val start = if (index == 0) 50 else innerPercent
            val end = if (index == options.lastIndex) 50 else innerPercent
            val fill by animateColorAsState(if (selected) selectedFill(option) else idleFill(option), tween(300), label = "choiceFill")
            val content by animateColorAsState(if (selected) selectedContent(option) else idleContent(option), tween(300), label = "choiceContent")
            Surface(
                onClick = { onPick(option.seed) },
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
                        text = option.title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** What you're doing, as a row of icon buttons with their names: a rounded square, round once picked. */
@Composable
private fun Occasions(
    options: List<YandexWaveOption>,
    picked: String?,
    fill: Color,
    content: Color,
    onPick: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        items(options, key = { it.seed }) { option ->
            val selected = option.seed == picked
            val corner by animateFloatAsState(
                targetValue = if (selected) 50f else 30f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
                label = "occasionCorner"
            )
            val background by animateColorAsState(if (selected) fill else IdleFill, tween(300), label = "occasionFill")
            val foreground by animateColorAsState(if (selected) content else IdleContent, tween(300), label = "occasionContent")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.width(64.dp)
            ) {
                Surface(
                    onClick = { onPick(option.seed) },
                    shape = RoundedCornerShape(percent = corner.roundToInt().coerceIn(0, 50)),
                    color = background,
                    contentColor = foreground,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(occasionIcon(option), contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Text(
                    text = option.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (selected) 1f else 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** An occasion's icon, by what Rotor calls it (`activity:wake-up`) or its name. */
private fun occasionIcon(option: YandexWaveOption): ImageVector {
    val id = (option.seed + " " + option.title).lowercase()
    return when {
        "wake" in id || "просып" in id -> Icons.Rounded.WbSunny
        "road" in id || "drive" in id || "car" in id || "дорог" in id -> Icons.Rounded.DirectionsCar
        "workout" in id || "sport" in id || "run" in id || "трен" in id || "спорт" in id -> Icons.Rounded.FitnessCenter
        "work" in id || "работ" in id -> Icons.Rounded.Laptop
        "sleep" in id || "засып" in id || "сон" in id -> Icons.Rounded.Bedtime
        "party" in id || "вечерин" in id -> Icons.Rounded.Celebration
        "study" in id || "учёб" in id || "учеб" in id -> Icons.Rounded.School
        "relax" in id || "отдых" in id || "медит" in id -> Icons.Rounded.SelfImprovement
        else -> Icons.Rounded.Explore
    }
}

/**
 * A mood's colours and form. [lobes] and [depth]: the shape's outline is a circle rippled that
 * many times, that deeply — Material's burst, flower, cookie and near-circle, as a formula, so
 * that one flows into the next.
 */
private class MoodLook(val strong: Color, val soft: Color, val deep: Color, val lobes: Int, val depth: Float)

private val Lively = MoodLook(Color(0xFFFF6A3D), Color(0xFFFFC2A6), Color(0xFF4A1405), lobes = 12, depth = 0.085f)
private val Fun = MoodLook(Color(0xFFFFC23D), Color(0xFFFFE3A1), Color(0xFF3F2A00), lobes = 6, depth = 0.15f)
private val Calm = MoodLook(Color(0xFF2FD1AE), Color(0xFFA6EEDC), Color(0xFF00362B), lobes = 9, depth = 0.06f)
private val Sad = MoodLook(Color(0xFF9A88FF), Color(0xFFD3CAFF), Color(0xFF1E1260), lobes = 5, depth = 0.04f)
private val AnyMood = MoodLook(Color(0xFFFF5F9E), Color(0xFFFFC6DC), Color(0xFF4A0F28), lobes = 8, depth = 0.07f)

private fun moodLook(mood: String?): MoodLook = when (mood) {
    "active" -> Lively
    "fun" -> Fun
    "calm" -> Calm
    "sad" -> Sad
    else -> AnyMood
}

/**
 * The mood's shape: flowing into the new one's form with a little overshoot as the mood changes,
 * turning slowly while [turning], a lighter one inside it turning the other way. Only drawn again
 * while it moves, no more than 60 times a second.
 */
@Composable
private fun MoodShape(look: MoodLook, strong: Color, soft: Color, turning: Boolean, modifier: Modifier = Modifier) {
    var from by remember { mutableStateOf(look) }
    var to by remember { mutableStateOf(look) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(look) {
        if (look === to) return@LaunchedEffect
        from = to
        to = look
        morph.snapTo(0f)
        morph.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val clock = rememberLoopClock(turning)
    val outline = remember { Path() }
    Canvas(modifier = modifier) {
        val t = morph.value
        val turn = clock.longValue / 1000f * 0.35f
        val radius = size.minDimension * 0.42f
        fun trace(scale: Float, rotation: Float) {
            outline.reset()
            for (i in 0..SHAPE_STEPS) {
                val a = i.toFloat() / SHAPE_STEPS * 2f * PI.toFloat()
                val r0 = 1f + from.depth * cos(from.lobes * (a + rotation))
                val r1 = 1f + to.depth * cos(to.lobes * (a + rotation))
                val r = radius * scale * (r0 + (r1 - r0) * t)
                val x = center.x + r * cos(a)
                val y = center.y + r * sin(a)
                if (i == 0) outline.moveTo(x, y) else outline.lineTo(x, y)
            }
            outline.close()
        }
        trace(1f, turn)
        drawPath(
            path = outline,
            brush = Brush.radialGradient(
                colors = listOf(soft, strong),
                center = center + Offset(-radius * 0.3f, -radius * 0.35f),
                radius = radius * 1.6f
            )
        )
        trace(0.72f, -turn * 1.6f)
        drawPath(path = outline, color = Color.White.copy(alpha = 0.14f))
    }
}

private const val SHAPE_STEPS = 180
