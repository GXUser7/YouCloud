package com.example.myapplication.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
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

/**
 * Something glass shows through: a layer the content is recorded into, and where it lies. [soft]:
 * blurred already (the backdrop's shapes are), so glass over it alone needs no blur of its own —
 * only a wash of colour, which looks the same and costs nothing on every frame of the backdrop.
 */
@Stable
class FrostSource internal constructor(internal val layer: GraphicsLayer, internal val soft: Boolean) {
    internal var origin by mutableStateOf(Offset.Zero)
}

@Composable
fun rememberFrostSource(soft: Boolean = false): FrostSource {
    val layer = rememberGraphicsLayer()
    return remember(layer) {
        // A soft source — the backdrop — is drawn again by every piece of glass over it, on every
        // frame: kept as one picture, redrawn only when it changes, rather than its ground and its
        // two blurred layers each time over. It is opaque, so it looks just the same. On a phone
        // of a few years ago this halved the frames that stuttered while search scrolled.
        if (soft) layer.compositingStrategy = CompositingStrategy.Offscreen
        FrostSource(layer, soft)
    }
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
    // Blurring is only worth it over something sharp: a list scrolling under a bar, a screen
    // under the mini player. Over the backdrop alone (blurred already) the glass draws it as it
    // is under a wash of colour — which still hides whatever else passes behind, a list under a
    // bar, as glass does.
    if (sources.all { it.soft }) {
        return this
            .clip(shape)
            .then(SoftFrostElement(sources, tint))
            .then(if (rim) Modifier.border(1.dp, Color.White.copy(alpha = 0.07f), shape) else Modifier)
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

/**
 * Glass over soft sources only: the part of them under the box, under [tint]. Where the box is
 * kept in the node, not in state: a list of glass rows scrolling wrote every row's position into
 * state on every frame, when all it needed was the row redrawn.
 */
private data class SoftFrostElement(val sources: List<FrostSource>, val tint: Color) :
    ModifierNodeElement<SoftFrostNode>() {
    override fun create() = SoftFrostNode(sources, tint)

    override fun update(node: SoftFrostNode) {
        node.sources = sources
        node.tint = tint
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "softFrost"
    }
}

private class SoftFrostNode(var sources: List<FrostSource>, var tint: Color) :
    Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var at = Offset.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        if (position != at) {
            at = position
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        for (source in sources) {
            translate(source.origin.x - at.x, source.origin.y - at.y) { drawLayer(source.layer) }
        }
        drawRect(tint)
        drawContent()
    }
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

/** A solid fill's colour, or none where the box is glass instead. */
@Composable
fun glassFill(color: Color): Color = if (LocalGlass.current) Color.Transparent else color

/**
 * A whole page as glass: the backdrop blurred behind it under most of the page's own colour, so
 * the shapes still move, faintly, behind whatever the page shows.
 */
@Composable
fun Modifier.pageGlass(color: Color): Modifier =
    if (LocalGlass.current) frosted(RectangleShape, color.copy(alpha = PageGlassAlpha), rim = false) else background(color)

internal val FrostBlur = 28.dp
internal const val PageGlassAlpha = 0.68f
internal const val GlassAlpha = 0.58f
private const val MIN_SHRUNK_BLUR_PX = 6f
private const val MAX_SHRINK = 8
