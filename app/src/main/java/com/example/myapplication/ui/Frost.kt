package com.example.myapplication.ui

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/*
 * Frosted glass. Compose can blur what a box draws, not what lies behind it; so what lies behind
 * is recorded as it is drawn — the app's moving backdrop, a screen's scrolling list — and a
 * frosted box draws that record again, the part under itself, blurred, under a wash of colour.
 *
 * A box must never draw a record it is itself part of: that is a layer drawing itself. So a
 * screen hands its glass the records of what is behind it and not around it — see
 * [LocalFrostSources].
 */

/** Something glass shows through: a layer the content is recorded into, and where it lies. */
@Stable
class FrostSource internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberFrostSource(): FrostSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) { FrostSource(layer) }
}

/** What glass here shows through, back to front. Never a record the glass is inside of. */
val LocalFrostSources = staticCompositionLocalOf<List<FrostSource>> { emptyList() }

/**
 * Whether the screen's panels, buttons and cards are glass rather than solid: home and search,
 * which stand on the moving backdrop.
 */
val LocalGlass = staticCompositionLocalOf { false }

/** Records what this box draws into [source], for glass elsewhere to show. */
fun Modifier.frostSource(source: FrostSource): Modifier = this
    .onGloballyPositioned { source.origin = it.positionInRoot() }
    .drawWithContent {
        source.layer.record { this@drawWithContent.drawContent() }
        drawLayer(source.layer)
    }

/**
 * Frosted glass cut to [shape]: whatever [LocalFrostSources] holds behind it, blurred, under
 * [tint]. The blur is made a few times smaller and stretched back, which a blurred picture
 * doesn't show and which keeps a screenful of glass cheap on every frame of the backdrop.
 */
@Composable
fun Modifier.frosted(
    shape: Shape = RectangleShape,
    tint: Color,
    blur: Dp = FrostBlur,
    // A hairline of light along the edge, as glass catches it.
    rim: Boolean = true
): Modifier {
    val sources = LocalFrostSources.current
    val here = remember { FrostOrigin() }
    return this
        .onGloballyPositioned { here.value = it.positionInRoot() }
        .clip(shape)
        .drawWithCache {
            val glass = obtainGraphicsLayer()
            val blurPx = blur.toPx()
            val shrink = (blurPx / MIN_SHRUNK_BLUR_PX).toInt().coerceIn(1, MAX_SHRINK).toFloat()
            glass.renderEffect = BlurEffect(blurPx / shrink, blurPx / shrink, TileMode.Clamp)
            val small = IntSize(
                ceil(size.width / shrink).toInt().coerceAtLeast(1),
                ceil(size.height / shrink).toInt().coerceAtLeast(1)
            )
            onDrawBehind {
                if (sources.isNotEmpty()) {
                    val at = here.value
                    glass.record(small) {
                        scale(1f / shrink, 1f / shrink, pivot = Offset.Zero) {
                            for (source in sources) {
                                translate(source.origin.x - at.x, source.origin.y - at.y) { drawLayer(source.layer) }
                            }
                        }
                    }
                    scale(shrink, shrink, pivot = Offset.Zero) { drawLayer(glass) }
                }
                drawRect(tint)
            }
        }
        .then(if (rim) Modifier.border(1.dp, Color.White.copy(alpha = 0.07f), shape) else Modifier)
}

// Where a glass box is, read only while drawing: moving it redraws the glass, not the screen.
@Stable
private class FrostOrigin {
    var value by mutableStateOf(Offset.Zero)
}

/** The glass version of a solid fill of [color], or the fill itself where there's no glass. */
@Composable
fun Modifier.glassOr(shape: Shape, color: Color, glassAlpha: Float = GlassAlpha): Modifier =
    if (LocalGlass.current) frosted(shape, color.copy(alpha = color.alpha * glassAlpha)) else this

internal val FrostBlur = 28.dp
internal const val GlassAlpha = 0.58f
private const val MIN_SHRUNK_BLUR_PX = 6f
private const val MAX_SHRINK = 8
