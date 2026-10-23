package com.example.myapplication.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/*
 * Frosted glass. The moving backdrop is under the window (see ExpressiveBackground): glass opens
 * the window down to it and lays a wash of colour over it — blurred already, it needs no blur of
 * its own. What lies over the backdrop, a screen's scrolling list, Compose cannot blur from behind
 * a box; so it is recorded as it is drawn, and a frosted box draws that record again, the part
 * under itself, blurred, under the wash.
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
 *
 * No rim along the edge: a hairline of light round every pill and panel made the glass look drawn
 * on rather than matte; the tint alone sets it off the backdrop.
 */
@Composable
fun Modifier.frosted(
    shape: Shape = RectangleShape,
    tint: Color,
    blur: Dp = FrostBlur
): Modifier {
    val sources = LocalFrostSources.current
    // Blurring is only worth it over something sharp: a list scrolling under a bar, a screen
    // under the mini player. Over the backdrop alone (blurred already) the glass opens the window
    // down to it under a wash of colour — which still hides whatever else passes behind, a list
    // under a bar, as glass does.
    if (sources.isEmpty()) {
        return this
            .clip(shape)
            .then(BackdropGlassElement(tint))
    }
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
                // Down to the backdrop under the window first, as glass over it alone does (see
                // BackdropGlassNode): what is blurred here is what lies over the backdrop.
                drawRect(Color.Black, blendMode = BlendMode.DstOut)
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
}

/** Glass over the backdrop alone: the window opened down to it, under [tint]. */
private data class BackdropGlassElement(val tint: Color) : ModifierNodeElement<BackdropGlassNode>() {
    override fun create() = BackdropGlassNode(tint)

    override fun update(node: BackdropGlassNode) {
        node.tint = tint
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "backdropGlass"
    }
}

private class BackdropGlassNode(var tint: Color) : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        // The backdrop stands under the window (see ExpressiveBackground): the glass opens the
        // window down to it, which hides what passes behind just as the backdrop painted here did —
        // as much of it as the glass is there: half of it, on a page faded half in (see glassFade).
        drawRect(Color.Black, blendMode = BlendMode.DstOut)
        drawRect(tint)
        drawContent()
    }
}

// Where a glass box is, read only while drawing: moving it redraws the glass, not the screen.
@Stable
private class FrostOrigin {
    var value by mutableStateOf(Offset.Zero)
}

/**
 * A page faded in and out as fadeIn and fadeOut do, [enter] and [exit] long, but on what it draws
 * rather than as a picture of it: its glass then takes away as much of what is under the page as
 * the page is there, as the backdrop painted on it once did. Faded as a picture, the glass opened
 * that picture alone, and home showed through a page coming in, then went at once as it settled.
 */
@Composable
fun AnimatedVisibilityScope.glassFade(enter: FiniteAnimationSpec<Float>, exit: FiniteAnimationSpec<Float>): Modifier {
    val shown = transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
        label = "glassFade"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    return Modifier.graphicsLayer {
        alpha = shown.value
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
    }
}

/** The glass version of a solid fill of [color], or the fill itself where there's no glass. */
@Composable
fun Modifier.glassOr(shape: Shape, color: Color, glassAlpha: Float = GlassAlpha): Modifier =
    if (LocalGlass.current) frosted(shape, color.copy(alpha = color.alpha * lightened(glassAlpha))) else this

/**
 * Whether the theme is light: its glass has to be denser — light glass over a light backdrop let
 * so much of it through that panels and backdrop were one tone.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun isLightTheme(): Boolean = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() > 0.5f

/** A glass [alpha] as the theme has it: as it is in a dark theme, a third of the way to solid in a light one. */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
internal fun lightened(alpha: Float): Float = if (isLightTheme()) alpha + (1f - alpha) * 0.3f else alpha

/** A solid fill's colour, or none where the box is glass instead. */
@Composable
fun glassFill(color: Color): Color = if (LocalGlass.current) Color.Transparent else color

/**
 * A whole page as glass: the backdrop blurred behind it under most of the page's own colour, so
 * the shapes still move, faintly, behind whatever the page shows.
 */
@Composable
fun Modifier.pageGlass(color: Color): Modifier =
    if (LocalGlass.current) frosted(RectangleShape, color.copy(alpha = lightened(PageGlassAlpha))) else background(color)

internal val FrostBlur = 28.dp
internal const val PageGlassAlpha = 0.68f
internal const val GlassAlpha = 0.58f
private const val MIN_SHRUNK_BLUR_PX = 6f
private const val MAX_SHRINK = 8
