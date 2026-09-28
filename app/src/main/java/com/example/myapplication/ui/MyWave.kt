package com.example.myapplication.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.YandexWave
import com.example.myapplication.data.YandexWaveSettings
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The key an occasion is picked under, beside the settings' own. */
internal const val WAVE_OCCASION = "occasion"

/**
 * Yandex Music's "Моя волна" on home: a glowing orb in the colours of the mood picked, flowing while
 * the wave plays, with its play button in the middle; under it the wave's settings — mood,
 * character, language, and what you're doing — a row of choices each. Picking one while the wave
 * plays tunes it at once.
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
    val mood = shown.groups.firstOrNull { it.key == YandexWave.MOOD }
        ?.options?.firstOrNull { it.seed == picks[YandexWave.MOOD] }?.value
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
            val orb = min(maxWidth.value, maxHeight.value * 0.82f).dp * 0.9f
            WaveOrb(
                colors = moodColors(mood),
                flowing = playing,
                modifier = Modifier.size(orb)
            )
            Surface(
                onClick = onToggle,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.92f),
                contentColor = Color(0xFF15121C),
                shadowElevation = 6.dp,
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (starting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp,
                            color = Color(0xFF15121C)
                        )
                    } else {
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Пауза" else "Слушать волну",
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
            Text(
                text = when {
                    picked.isNotEmpty() -> picked.joinToString(" · ")
                    playing -> "Играет — под твой вкус"
                    else -> "Бесконечно, под твой вкус"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            shown.groups.forEach { group ->
                // Each mood marked with its colour.
                val dots = if (group.key == YandexWave.MOOD) {
                    group.options.associate { it.seed to moodColors(it.value).first() }
                } else {
                    null
                }
                WaveChoiceRow(
                    title = group.title,
                    options = group.options.map { it.seed to it.title },
                    picked = picks[group.key],
                    dot = dots?.let { colors -> { seed -> colors[seed] ?: Color.Gray } },
                    onPick = { onPick(group.key, it) }
                )
            }
            if (shown.occasions.isNotEmpty()) {
                WaveChoiceRow(
                    title = "Занятие",
                    options = shown.occasions.map { it.seed to it.title },
                    picked = picks[WAVE_OCCASION],
                    dot = null,
                    onPick = { onPick(WAVE_OCCASION, it) }
                )
            }
        }
    }
}

@Composable
private fun WaveChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    picked: String?,
    dot: ((String) -> Color)?,
    onPick: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 20.dp)
                .size(width = 92.dp, height = 20.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(options, key = { it.first }) { (seed, name) ->
                val selected = seed == picked
                FilterChip(
                    selected = selected,
                    onClick = { onPick(seed) },
                    label = { Text(name) },
                    leadingIcon = dot?.let { colorOf ->
                        {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(colorOf(seed), CircleShape)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    border = null
                )
            }
        }
    }
}

/** The mood's colours: warm for lively, sunny for fun, sea for calm, violet for sad; all sorts without one. */
private fun moodColors(mood: String?): List<Color> = when (mood) {
    "active" -> listOf(Color(0xFFFF6A3D), Color(0xFFFF2D6F), Color(0xFFFFB13B))
    "fun" -> listOf(Color(0xFFFFD43B), Color(0xFFFF5FA8), Color(0xFFFF8F3B))
    "calm" -> listOf(Color(0xFF3DDBC6), Color(0xFF3D8CFF), Color(0xFF8FE3FF))
    "sad" -> listOf(Color(0xFF6C5CFF), Color(0xFF3240B8), Color(0xFFB85CFF))
    else -> listOf(Color(0xFFFF5F9E), Color(0xFF7C5CFF), Color(0xFFFFA95C))
}

/**
 * Soft lights in [colors] drifting round one another inside a glow: still while the wave is quiet,
 * flowing and breathing while it plays. Only drawn again as it moves, and no more than 60 times a
 * second.
 */
@Composable
private fun WaveOrb(colors: List<Color>, flowing: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberLoopClock(flowing)
    val first by animateColorAsState(colors[0], tween(900), label = "waveColor0")
    val second by animateColorAsState(colors[1], tween(900), label = "waveColor1")
    val third by animateColorAsState(colors[2], tween(900), label = "waveColor2")
    Canvas(modifier = modifier) {
        val t = clock.longValue / 1000f
        val radius = size.minDimension / 2f
        val breathe = 1f + 0.035f * sin(t * 2.1f)
        val r = radius * breathe
        drawCircle(
            brush = Brush.radialGradient(
                0f to first.copy(alpha = 0.42f),
                0.55f to second.copy(alpha = 0.18f),
                1f to Color.Transparent,
                center = center,
                radius = r
            ),
            radius = r,
            center = center
        )
        val lights = WaveLights
        for (i in lights.indices) {
            val light = lights[i]
            val color = when (i % 3) {
                0 -> first
                1 -> second
                else -> third
            }
            val angle = light.phase + t * light.speed
            val at = center + Offset(cos(angle), sin(angle * 1.3f)) * (r * light.orbit)
            val size = r * light.size
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = 0.85f),
                    0.6f to color.copy(alpha = 0.25f),
                    1f to Color.Transparent,
                    center = at,
                    radius = size
                ),
                radius = size,
                center = at
            )
        }
        // A bright core, for the button to sit in.
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.35f),
                1f to Color.Transparent,
                center = center,
                radius = r * 0.42f
            ),
            radius = r * 0.42f,
            center = center
        )
    }
}

private class WaveLight(val phase: Float, val speed: Float, val orbit: Float, val size: Float)

private val WaveLights = listOf(
    WaveLight(phase = 0f, speed = 0.35f, orbit = 0.26f, size = 0.6f),
    WaveLight(phase = 2.1f, speed = -0.27f, orbit = 0.3f, size = 0.52f),
    WaveLight(phase = 4.2f, speed = 0.22f, orbit = 0.22f, size = 0.48f),
    WaveLight(phase = 1.0f, speed = -0.41f, orbit = 0.32f, size = 0.4f),
    WaveLight(phase = 3.3f, speed = 0.3f, orbit = 0.16f, size = 0.36f)
)
