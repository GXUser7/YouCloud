package com.example.myapplication.ui

import android.graphics.RuntimeShader
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * How a picture's light spreads around it: how soon it dims, and how soft it is.
 *
 * [fade]: past the picture's edge by d times its half-size, the light is 1 / (1 + fade·d²) as
 * bright. [detail]: how much the picture is softened before its edges are read, so that a spark or
 * a line of text at the edge doesn't flicker through the glow. [softness]: how much the spread light
 * is blurred afterwards, joining the rays it spreads in.
 */
internal class GlowSpread(val fade: Float, val detail: Dp, val softness: Dp)

/** The whole player's background: out to the bottom of the screen, dimming only a little on the way. */
internal val BackdropSpread = GlowSpread(fade = 0.05f, detail = 4.dp, softness = 10.dp)

/** The sides a video turned sideways leaves on the screen. */
internal val SidesSpread = GlowSpread(fade = 0.6f, detail = 4.dp, softness = 8.dp)

/**
 * The picture an [ambientGlow] is drawn from, told it as it draws: its layer, where in the box the
 * layer is drawn, and where in it the picture itself is.
 */
internal class GlowPicture {
    var layer: GraphicsLayer? = null
        private set
    var at = Offset.Zero
        private set
    var frame = Rect.Zero
        private set

    fun show(layer: GraphicsLayer, at: Offset, frame: Rect): Boolean {
        this.layer = layer
        this.at = at
        this.frame = frame
        return true
    }
}

/**
 * Fills the box with the light of a picture — a music video, or the cover — the way YouTube's
 * Ambilight extension makes it: the picture drawn once, at a quarter of the size; the colours at its
 * edges carried outwards along the lines from its middle, dimming as they go; all of it blurred once
 * and stretched over the box. One pass of one shader, on a sixteenth of the pixels.
 *
 * It reads in from each edge until the picture isn't black: the black bars of a film inside a
 * video's frame, or a dark border, don't turn the light around it black.
 *
 * [picture] tells which picture and where, given the box's size, and is `false` while there is
 * none; the box is black then.
 */
internal fun Modifier.ambientGlow(spread: GlowSpread, picture: GlowPicture.(Size) -> Boolean): Modifier =
    this then AmbientGlowElement(spread, picture)

private data class AmbientGlowElement(
    val spread: GlowSpread,
    val picture: GlowPicture.(Size) -> Boolean
) : ModifierNodeElement<AmbientGlowNode>() {
    override fun create() = AmbientGlowNode(spread, picture)

    override fun update(node: AmbientGlowNode) {
        node.spread = spread
        node.picture = picture
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "ambientGlow"
    }
}

private class AmbientGlowNode(
    var spread: GlowSpread,
    var picture: GlowPicture.(Size) -> Boolean
) : Modifier.Node(), DrawModifierNode {
    private val shown = GlowPicture()
    private var layer: GraphicsLayer? = null
    private var shader: RuntimeShader? = null

    // What the layer's effect was made for: made again only when the picture moves in the box.
    private var effectFrame = Rect.Zero
    private var effectDensity = 0f
    private var effectSpread: GlowSpread? = null

    override fun onAttach() {
        layer = requireGraphicsContext().createGraphicsLayer().also { it.clip = true }
    }

    override fun onDetach() {
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        effectSpread = null
    }

    override fun ContentDrawScope.draw() {
        val layer = layer
        val source = if (layer != null && shown.picture(size)) shown.layer else null
        if (layer == null || source == null) {
            drawRect(Color.Black)
            drawContent()
            return
        }
        val shrink = GLOW_SHRINK.toFloat()
        val frame = shown.frame
        val small = Rect(frame.left / shrink, frame.top / shrink, frame.right / shrink, frame.bottom / shrink)
        if (small != effectFrame || density != effectDensity || spread !== effectSpread) {
            layer.renderEffect = glowEffect(small)
            effectFrame = small
            effectDensity = density
            effectSpread = spread
        }
        val at = shown.at
        layer.record(
            IntSize(
                ceil(size.width / shrink).toInt().coerceAtLeast(1),
                ceil(size.height / shrink).toInt().coerceAtLeast(1)
            )
        ) {
            scale(1f / shrink, 1f / shrink, pivot = Offset.Zero) {
                translate(at.x, at.y) { drawLayer(source) }
            }
        }
        scale(shrink, shrink, pivot = Offset.Zero) { drawLayer(layer) }
        drawContent()
    }

    /** Softened, spread by the shader, blurred: the light of the picture at [frame] (in the small copy). */
    private fun Density.glowEffect(frame: Rect): androidx.compose.ui.graphics.RenderEffect {
        val shader = shader ?: RuntimeShader(AMBIENT_LIGHT_SHADER).also { shader = it }
        shader.setFloatUniform("frame", frame.left, frame.top, frame.right, frame.bottom)
        shader.setFloatUniform("fade", spread.fade)
        val detail = spread.detail.toPx() / GLOW_SHRINK
        val softness = spread.softness.toPx() / GLOW_SHRINK
        val softened = android.graphics.RenderEffect.createBlurEffect(
            detail, detail, android.graphics.Shader.TileMode.DECAL
        )
        val light = android.graphics.RenderEffect.createChainEffect(
            android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "picture"),
            softened
        )
        // Clamped at the box's edges, so the light doesn't darken towards the edges of the screen.
        return android.graphics.RenderEffect.createBlurEffect(
            softness, softness, light, android.graphics.Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    }
}

// How many times smaller than the box the light is made: blurred as it is, it has nothing finer.
private const val GLOW_SHRINK = 4

/*
 * For each point: how many times the picture's size a copy of it would have to be to reach it (its
 * "level", as a rounded rectangle, so no seam runs out from the corners), and so which point of the
 * picture's edge its light comes from. Read there from the edge inwards, the first colour that
 * isn't black counting for most — a film's black bars inside a video's frame, cropped at the sides
 * to fill the player and still there above and below, are passed over as Ambilight passes them, or
 * all the light above and below the video came out black; and read along the edge too, over a stretch that widens the further out the point is,
 * as light from a window spreads — carried straight out as it is, a bright spot at the edge ran on
 * across the screen as a ray, wider and wider. A little more saturated, spread thin as it is; dimmer
 * the further out. Inside the picture — under it, where only its faded edges show — it is the picture.
 */
private const val AMBIENT_LIGHT_SHADER = """
    uniform shader picture;
    uniform float4 frame;
    uniform float fade;

    const float3 LUMA = float3(0.2126, 0.7152, 0.0722);
    // How far round, in radians, the light a point gets comes from, far out: at the edge, none.
    const float SPREAD = 0.35;

    float levelOf(float2 v, float2 extent) {
        float2 d = abs(v) / extent;
        d = d * d;
        d = d * d;
        return max(sqrt(sqrt(d.x + d.y)), 1.0);
    }

    half4 main(float2 p) {
        float2 middle = (frame.xy + frame.zw) * 0.5;
        float2 extent = max((frame.zw - frame.xy) * 0.5, float2(1.0));
        float2 v = p - middle;
        float level = levelOf(v, extent);
        float turn = SPREAD * 0.5 * (1.0 - 1.0 / level);

        float3 light = float3(0.0);
        float weight = 0.0;
        for (int j = 0; j < 5; j++) {
            float k = float(j) - 2.0;
            float a = turn * k;
            float2 ray = float2(v.x * cos(a) - v.y * sin(a), v.x * sin(a) + v.y * cos(a));
            float2 edge = ray / levelOf(ray, extent);
            float along = exp(-0.5 * k * k);
            // From the edge inwards: the first colour that isn't black gives most of the light.
            float remain = 1.0;
            for (int i = 0; i < 6; i++) {
                float4 c = float4(picture.eval(middle + edge * (0.96 - 0.09 * float(i))));
                if (c.a > 0.004) {
                    float3 rgb = c.rgb / c.a;
                    float content = smoothstep(0.015, 0.06, dot(rgb, LUMA));
                    float w = along * c.a * remain * (0.03 + content);
                    light += rgb * w;
                    weight += w;
                    remain *= 1.0 - 0.7 * content;
                }
            }
        }
        if (weight > 0.0) {
            light /= weight;
        }
        light = clamp(mix(float3(dot(light, LUMA)), light, 1.25), 0.0, 1.0);
        float far = level - 1.0;
        return half4(half3(light / (1.0 + fade * far * far)), 1.0);
    }
"""
