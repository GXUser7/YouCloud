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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.setFrom
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.node.requireView
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlin.math.ceil

/*
 * Frosted glass. The moving backdrop is under the window (see ExpressiveBackground): glass opens
 * the window down to it and lays a wash of colour over it, and the backdrop, told where the glass
 * is, draws its shapes there blurred, as glass blurs them (see BackdropGlass) — on its own surface,
 * so that the window still draws nothing while they move. What lies over the backdrop, a screen's
 * scrolling list, Compose cannot blur from behind a box; so it is recorded as it is drawn, and a
 * frosted box draws that record again, the part under itself, blurred, under the wash.
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
    // Over the backdrop alone the glass opens the window down to it under a wash of colour, and
    // the backdrop blurs itself there (see BackdropBlurNode) — which still hides whatever else
    // passes behind, a list under a bar, as glass does. Blurring here is for what is over the
    // backdrop: a list scrolling under a bar, a screen under the mini player.
    if (sources.isEmpty()) {
        return this
            .then(BackdropBlurElement(shape))
            .clip(shape)
            .then(BackdropGlassElement(tint))
    }
    val here = remember { FrostOrigin() }
    return this
        .onGloballyPositioned { here.value = it.positionInRoot() }
        .then(BackdropBlurElement(shape))
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
                // BackdropGlassNode): what is blurred here is what lies over the backdrop, which
                // blurs itself.
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

/**
 * Glass telling the backdrop where it is (see [BackdropGlass]), for the backdrop to draw its
 * shapes under it blurred. Told again as the glass moves — scrolled, slid, scaled — changes its
 * shape, or fades (see [glassShown]); and only then: a pane at rest costs nothing.
 */
private data class BackdropBlurElement(val shape: Shape) : ModifierNodeElement<BackdropBlurNode>() {
    override fun create() = BackdropBlurNode(shape)

    override fun update(node: BackdropBlurNode) {
        node.shape = shape
        node.tell()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "backdropBlur"
    }
}

private class BackdropBlurNode(var shape: Shape) : Modifier.Node(), GlobalPositionAwareModifierNode, ObserverModifierNode {
    private var coordinates: LayoutCoordinates? = null
    private var shown = 1f
    // A clip round the glass from a box it is in (see glassClip), and that box.
    private var clip: GlassClipNode? = null
    private var clipShape: Shape? = null
    private val matrix = androidx.compose.ui.graphics.Matrix()
    private val clipMatrix = androidx.compose.ui.graphics.Matrix()
    private val location = IntArray(2)
    // What the backdrop was told last: told again only when some of it changes.
    private var toldShape: Shape? = null
    private var toldSize = IntSize.Zero
    private var toldBounds = Rect.Zero
    private var toldVisible = Rect.Zero
    private var toldShown = -1f
    private var toldClip: Shape? = null
    private var toldClipBounds = Rect.Zero
    private var toldX = 0
    private var toldY = 0

    override fun onAttach() = readShown()

    override fun onDetach() {
        coordinates = null
        toldShown = -1f
        BackdropGlass.remove(this)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        tell()
    }

    override fun onObservedReadsChanged() {
        readShown()
        tell()
    }

    private fun readShown() = observeReads {
        var amount = 1f
        traverseAncestors(GlassShownKey) { node ->
            amount *= (node as GlassShownNode).amount().coerceIn(0f, 1f)
            true
        }
        shown = amount
        // The nearest box that clips the glass now.
        var clipping: GlassClipNode? = null
        var clipped: Shape? = null
        traverseAncestors(GlassClipKey) { node ->
            clipped = (node as GlassClipNode).shape()
            if (clipped != null) clipping = node
            clipped == null
        }
        clip = clipping
        clipShape = clipped
    }

    fun tell() {
        val coordinates = coordinates?.takeIf { isAttached && it.isAttached } ?: return
        if (shown <= 0f) {
            if (toldShown > 0f) BackdropGlass.remove(this)
            toldShown = 0f
            return
        }
        matrix.reset()
        coordinates.findRootCoordinates().transformFrom(coordinates, matrix)
        val size = coordinates.size
        val bounds = matrix.map(size.toSize().toRect())
        // Cut by whatever clips around it: a list scrolled under its edge.
        val visible = coordinates.boundsInRoot()
        val view = requireView()
        view.getLocationInWindow(location)
        val clipShape = clipShape
        val clipCoordinates = clip?.takeIf { clipShape != null && it.isAttached }?.coordinates()
        var clipBounds = Rect.Zero
        if (clipCoordinates != null) {
            clipMatrix.reset()
            coordinates.findRootCoordinates().transformFrom(clipCoordinates, clipMatrix)
            clipBounds = clipMatrix.map(clipCoordinates.size.toSize().toRect())
        }
        if (shape == toldShape && size == toldSize && bounds == toldBounds && visible == toldVisible &&
            shown == toldShown && clipShape == toldClip && clipBounds == toldClipBounds &&
            location[0] == toldX && location[1] == toldY
        ) return
        toldShape = shape
        toldSize = size
        toldBounds = bounds
        toldVisible = visible
        toldShown = shown
        toldClip = clipShape
        toldClipBounds = clipBounds
        toldX = location[0]
        toldY = location[1]
        val outline = shape.createOutline(size.toSize(), requireLayoutDirection(), requireDensity())
        val path = onWindow(outline, matrix)
        if (clipShape != null && clipCoordinates != null) {
            val around = clipShape.createOutline(clipCoordinates.size.toSize(), requireLayoutDirection(), requireDensity())
            path.op(onWindow(around, clipMatrix), android.graphics.Path.Op.INTERSECT)
        }
        BackdropGlass.put(
            this,
            BackdropGlass.Pane(
                window = view.rootView,
                outline = path,
                visible = android.graphics.RectF(
                    visible.left + location[0], visible.top + location[1],
                    visible.right + location[0], visible.bottom + location[1]
                ),
                alpha = shown,
                rect = outline is androidx.compose.ui.graphics.Outline.Rectangle && clipShape == null
            )
        )
    }

    /** [outline], in the coordinates [toRoot] takes to the root, on the window. */
    private fun onWindow(outline: androidx.compose.ui.graphics.Outline, toRoot: androidx.compose.ui.graphics.Matrix): android.graphics.Path {
        val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }.asAndroidPath()
        path.transform(
            android.graphics.Matrix().apply {
                setFrom(toRoot)
                postTranslate(location[0].toFloat(), location[1].toFloat())
            }
        )
        return path
    }
}

/**
 * A clip round the glass in this box other than the glass's own shape, for the backdrop to blur
 * inside it only: a page pulled away, its corners rounded by the layer it moves in. [shape] null
 * while nothing is clipped. Placed after the layer that clips, to be where the clip is.
 */
fun Modifier.glassClip(shape: () -> Shape?): Modifier = this then GlassClipElement(shape)

private class GlassClipElement(val shape: () -> Shape?) : ModifierNodeElement<GlassClipNode>() {
    override fun create() = GlassClipNode(shape)

    override fun update(node: GlassClipNode) {
        node.shape = shape
    }

    override fun equals(other: Any?) = other is GlassClipElement && other.shape === shape

    override fun hashCode() = shape.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "glassClip"
    }
}

private class GlassClipNode(var shape: () -> Shape?) : Modifier.Node(), TraversableNode {
    override val traverseKey: Any get() = GlassClipKey

    fun coordinates(): LayoutCoordinates? = if (isAttached) requireLayoutCoordinates() else null
}

private object GlassClipKey

/**
 * How much of the glass in this box is there, for the backdrop to blur under it as much: a page
 * fading in, home fading out under it. Glass inside several is there as much as all of them make.
 */
fun Modifier.glassShown(amount: () -> Float): Modifier = this then GlassShownElement(amount)

private class GlassShownElement(val amount: () -> Float) : ModifierNodeElement<GlassShownNode>() {
    override fun create() = GlassShownNode(amount)

    override fun update(node: GlassShownNode) {
        node.amount = amount
    }

    override fun equals(other: Any?) = other is GlassShownElement && other.amount === amount

    override fun hashCode() = amount.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "glassShown"
    }
}

private class GlassShownNode(var amount: () -> Float) : Modifier.Node(), TraversableNode {
    override val traverseKey: Any get() = GlassShownKey
}

private object GlassShownKey

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
    // Snapped in, a page is there from its first frame. Faded from nothing in no time, it was
    // nothing for that frame: the profile under friends pulled off it went, the profile taking its
    // place wasn't there yet, and the backdrop alone showed a moment.
    val instant = enter is androidx.compose.animation.core.SnapSpec<*>
    val shown = transition.animateFloat(
        transitionSpec = { if (targetState == EnterExitState.Visible) enter else exit },
        label = "glassFade"
    ) { state -> if (state == EnterExitState.Visible || (instant && state == EnterExitState.PreEnter)) 1f else 0f }
    val amount = remember(shown) { { shown.value } }
    return Modifier
        .graphicsLayer {
            alpha = shown.value
            compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        }
        // The backdrop blurred under its glass as much too, rather than all at once.
        .glassShown(amount)
}

/**
 * For glass coming and going with fadeIn() and fadeOut() at their usual pace: the backdrop blurred
 * under it as much as it is there, rather than all at once.
 */
@Composable
fun AnimatedVisibilityScope.glassFading(): Modifier {
    val shown = transition.animateFloat(
        transitionSpec = { androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow) },
        label = "glassFading"
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val amount = remember(shown) { { shown.value } }
    return Modifier.glassShown(amount)
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
