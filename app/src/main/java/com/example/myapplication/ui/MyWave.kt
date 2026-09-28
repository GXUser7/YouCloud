package com.example.myapplication.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Yandex Music's wave on home, as "Моя форма": one large shape in the theme's colours in the middle
 * of the page, turning slowly while the wave plays, and its play button in the middle of it —
 * round, squaring while it plays.
 */
@Composable
internal fun MyWavePage(
    waveOn: Boolean,
    isPlaying: Boolean,
    starting: Boolean,
    onToggle: () -> Unit
) {
    val playing = waveOn && isPlaying
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value * 0.8f, maxHeight.value * 0.8f).dp
        WaveShape(
            light = lerp(colors.primary, Color.White, 0.3f),
            color = colors.primary,
            turning = playing,
            modifier = Modifier.size(side)
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
}

/**
 * A soft eight-lobed shape, [light] at its top left to [color], with a paler one inside it; both
 * turn slowly, in opposite directions, while [turning]. Only drawn again while it turns, no more
 * than 60 times a second.
 */
@Composable
private fun WaveShape(light: Color, color: Color, turning: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberLoopClock(turning)
    val outline = remember { Path() }
    Canvas(modifier = modifier) {
        val turn = clock.longValue / 1000f * 0.35f
        val radius = size.minDimension * 0.44f
        fun trace(scale: Float, rotation: Float) {
            outline.reset()
            for (i in 0..SHAPE_STEPS) {
                val a = i.toFloat() / SHAPE_STEPS * 2f * PI.toFloat()
                val r = radius * scale * (1f + SHAPE_DEPTH * cos(SHAPE_LOBES * (a + rotation)))
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
                colors = listOf(light, color),
                center = center + Offset(-radius * 0.3f, -radius * 0.35f),
                radius = radius * 1.6f
            )
        )
        trace(0.72f, -turn * 1.6f)
        drawPath(path = outline, color = Color.White.copy(alpha = 0.14f))
    }
}

private const val SHAPE_STEPS = 180
private const val SHAPE_LOBES = 8
private const val SHAPE_DEPTH = 0.07f
