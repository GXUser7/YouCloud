package com.example.myapplication.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.myapplication.ui.theme.AppTheme
import com.example.myapplication.ui.theme.PanelColorRoles
import com.example.myapplication.ui.theme.ScopedColorTheme
import com.materialkolor.hct.Hct
import com.materialkolor.quantize.QuantizerCelebi
import com.materialkolor.palettes.TonalPalette
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.Variant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The player's colours from its cover.
 *
 * Not one colour but the cover's own pair: the colour most of it is painted in, and its most vivid
 * accent. The dominant colour becomes the surfaces — backdrop, panel, chips — and the accent
 * becomes what acts: the play button, the seek bar, whatever is switched on. A red bar on black
 * gives a black player with red controls, the way the cover itself looks; a single seed would have
 * tinted everything one muddy hue. The structure stays Material's Tonal Spot (panels on tone 20),
 * so the player is as calm as the rest of the system, only in the cover's colours.
 */

/** What a cover is made of. [accent] is null for a greyscale cover. */
private data class CoverColors(val dominant: Int, val accent: Int?)

/**
 * Covers already read, by URL. The player composes afresh each time it opens, and without this it
 * started in the app's colours and faded to the cover's all over again.
 */
private val coverColorsCache = android.util.LruCache<String, CoverColors>(64)

/** Reads [url]'s colours ahead of time, so the player opens already in them. */
internal suspend fun prefetchCoverColors(context: Context, url: String) {
    if (coverColorsCache.get(url) != null) return
    runCatching { coverColors(context, url) }.getOrNull()?.let { coverColorsCache.put(url, it) }
}

/** A scheme built from a cover, plus the panel roles that carry the accent itself. */
private class CoverPalette(val scheme: ColorScheme, val panel: PanelColorRoles)

/**
 * Themes [content] from [artworkUrl] when [enabled]; otherwise, or when a cover cannot be read,
 * [content] keeps the app's own scheme. Switching tracks fades between palettes instead of
 * snapping.
 */
@Composable
internal fun CoverTheme(
    enabled: Boolean,
    artworkUrl: String?,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    // Keeps the previous cover's colours while the next one loads, so the player never flashes
    // back to the app's colours between two tracks.
    val colors by produceState(initialValue = artworkUrl?.let { coverColorsCache.get(it) }, artworkUrl) {
        if (artworkUrl.isNullOrBlank()) {
            value = null
            return@produceState
        }
        coverColorsCache.get(artworkUrl)?.let {
            value = it
            return@produceState
        }
        value = runCatching { coverColors(context, artworkUrl) }.getOrNull()
            ?.also { coverColorsCache.put(artworkUrl, it) }
    }
    val appScheme = MaterialTheme.colorScheme
    val appPanel = AppTheme.panel
    val palette = remember(colors, darkTheme) { colors?.let { coverPalette(it, darkTheme) } }

    @Composable
    fun Color.animated(label: String): Color =
        animateColorAsState(this, animationSpec = tween(600), label = label).value

    val panel = PanelColorRoles(
        container = (palette?.panel?.container ?: appPanel.container).animated("panelContainer"),
        content = (palette?.panel?.content ?: appPanel.content).animated("panelContent"),
        accent = (palette?.panel?.accent ?: appPanel.accent).animated("panelAccent"),
        onAccent = (palette?.panel?.onAccent ?: appPanel.onAccent).animated("panelOnAccent")
    )
    ScopedColorTheme(
        colorScheme = animateColorScheme(palette?.scheme ?: appScheme),
        darkTheme = darkTheme,
        panel = panel,
        content = content
    )
}

private suspend fun coverColors(context: Context, url: String): CoverColors? = withContext(Dispatchers.Default) {
    val request = ImageRequest.Builder(context)
        .data(artworkUrlForSize(url, 64.dp))
        .size(112)
        // Pixels have to be readable, which hardware bitmaps are not.
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request) as? SuccessResult ?: return@withContext null
    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val quantized = QuantizerCelebi.quantize(pixels, 128)
    if (quantized.isEmpty()) return@withContext null
    val swatches = quantized.map { (argb, count) -> Swatch(argb, Hct.fromInt(argb), count.toFloat() / pixels.size) }
    CoverColors(
        dominant = swatches.maxBy { it.share }.argb,
        accent = vividAccent(swatches)
    )
}

private class Swatch(val argb: Int, val hct: Hct, val share: Float)

/**
 * The colour a cover is *about*. Android's wallpaper scoring weighs area first, which suits a
 * wallpaper; on a cover it picks the sepia of a face over the red bar the design is built around.
 * So a clearly vivid colour holding a real share of the picture wins, and area only decides
 * between colours of similar strength. Null when nothing is vivid enough: a greyscale cover.
 *
 * The share is its hue family's, not the swatch's own. Crayon, grain, film and JPEG noise split
 * one colour into dozens of near-identical swatches: a cover a third pink came out as ninety
 * pinks of 1–3% each, none past the bar alone, and the player went grey on it.
 */
private fun vividAccent(swatches: List<Swatch>): Int? {
    val vivid = swatches.filter { it.hct.chroma >= 24.0 && it.hct.tone in 20.0..90.0 }
    fun familyShare(center: Swatch) = vivid
        .filter { hueDistance(it.hct.hue, center.hct.hue) <= 20.0 }
        .sumOf { it.share.toDouble() }
    return vivid
        .map { it to familyShare(it) }
        .filter { (_, share) -> share >= 0.02 }
        .maxByOrNull { (swatch, share) -> swatch.hct.chroma * Math.pow(share, 0.35) }
        ?.first
        ?.argb
}

/** Degrees between two hues, the short way round. */
private fun hueDistance(a: Double, b: Double): Double = Math.abs(((a - b) % 360.0 + 540.0) % 360.0 - 180.0)

private fun coverPalette(colors: CoverColors, darkTheme: Boolean): CoverPalette {
    val dominant = Hct.fromInt(colors.dominant)
    val accent = colors.accent?.let(Hct::fromInt)
    // Surfaces take the dominant colour's hue and only as much of its colourfulness as a surface
    // can carry; the accent keeps enough chroma to still read as itself.
    val surfaceChroma = dominant.chroma
    val primary = if (accent != null) {
        TonalPalette.fromHueAndChroma(accent.hue, accent.chroma.coerceIn(36.0, 72.0))
    } else {
        TonalPalette.fromHueAndChroma(dominant.hue, minOf(surfaceChroma, 6.0))
    }
    val secondary = TonalPalette.fromHueAndChroma(dominant.hue, minOf(surfaceChroma, 16.0))
    val tertiary = TonalPalette.fromHueAndChroma(
        accent?.let { (it.hue + 40.0) % 360.0 } ?: dominant.hue,
        if (accent != null) 24.0 else 4.0
    )
    val neutral = TonalPalette.fromHueAndChroma(dominant.hue, minOf(surfaceChroma / 4.0, 6.0))
    val neutralVariant = TonalPalette.fromHueAndChroma(dominant.hue, minOf(surfaceChroma / 2.0, 10.0))
    val s = DynamicScheme(
        accent ?: dominant, Variant.TONAL_SPOT, darkTheme, 0.0,
        primary, secondary, tertiary, neutral, neutralVariant
    )

    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = Color(s.primary),
        onPrimary = Color(s.onPrimary),
        primaryContainer = Color(s.primaryContainer),
        onPrimaryContainer = Color(s.onPrimaryContainer),
        inversePrimary = Color(s.inversePrimary),
        secondary = Color(s.secondary),
        onSecondary = Color(s.onSecondary),
        secondaryContainer = Color(s.secondaryContainer),
        onSecondaryContainer = Color(s.onSecondaryContainer),
        tertiary = Color(s.tertiary),
        onTertiary = Color(s.onTertiary),
        tertiaryContainer = Color(s.tertiaryContainer),
        onTertiaryContainer = Color(s.onTertiaryContainer),
        background = Color(s.background),
        onBackground = Color(s.onBackground),
        surface = Color(s.surface),
        onSurface = Color(s.onSurface),
        surfaceVariant = Color(s.surfaceVariant),
        onSurfaceVariant = Color(s.onSurfaceVariant),
        surfaceTint = Color(s.surfaceTint),
        inverseSurface = Color(s.inverseSurface),
        inverseOnSurface = Color(s.inverseOnSurface),
        outline = Color(s.outline),
        outlineVariant = Color(s.outlineVariant),
        surfaceBright = Color(s.surfaceBright),
        surfaceDim = Color(s.surfaceDim),
        surfaceContainerLowest = Color(s.surfaceContainerLowest),
        surfaceContainerLow = Color(s.surfaceContainerLow),
        surfaceContainer = Color(s.surfaceContainer),
        surfaceContainerHigh = Color(s.surfaceContainerHigh),
        surfaceContainerHighest = Color(s.surfaceContainerHighest)
    )

    // The accent is the cover's colour at full strength — a red bar gives a red button, not the
    // pastel tone 80 a dark scheme would otherwise use. A greyscale cover gets a white one.
    val (accentColor, onAccentColor) = if (accent != null) {
        Color(primary.tone(50)) to Color.White
    } else if (darkTheme) {
        Color(neutral.tone(92)) to Color(neutral.tone(10))
    } else {
        Color(neutral.tone(15)) to Color.White
    }
    val panel = PanelColorRoles(
        container = if (darkTheme) Color(secondary.tone(20)) else Color(secondary.tone(90)),
        // Near-neutral text, so the colour on the panel belongs to the controls.
        content = if (darkTheme) Color(neutral.tone(92)) else Color(neutral.tone(12)),
        accent = accentColor,
        onAccent = onAccentColor
    )
    return CoverPalette(scheme, panel)
}

/** Every role the player draws with, each fading to its new value. */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    @Composable
    fun Color.animated(label: String): Color =
        animateColorAsState(this, animationSpec = tween(600), label = label).value

    return target.copy(
        primary = target.primary.animated("primary"),
        onPrimary = target.onPrimary.animated("onPrimary"),
        primaryContainer = target.primaryContainer.animated("primaryContainer"),
        onPrimaryContainer = target.onPrimaryContainer.animated("onPrimaryContainer"),
        secondary = target.secondary.animated("secondary"),
        onSecondary = target.onSecondary.animated("onSecondary"),
        secondaryContainer = target.secondaryContainer.animated("secondaryContainer"),
        onSecondaryContainer = target.onSecondaryContainer.animated("onSecondaryContainer"),
        tertiary = target.tertiary.animated("tertiary"),
        background = target.background.animated("background"),
        onBackground = target.onBackground.animated("onBackground"),
        surface = target.surface.animated("surface"),
        onSurface = target.onSurface.animated("onSurface"),
        onSurfaceVariant = target.onSurfaceVariant.animated("onSurfaceVariant"),
        outlineVariant = target.outlineVariant.animated("outlineVariant"),
        surfaceContainer = target.surfaceContainer.animated("surfaceContainer"),
        surfaceContainerHigh = target.surfaceContainerHigh.animated("surfaceContainerHigh"),
        surfaceContainerHighest = target.surfaceContainerHighest.animated("surfaceContainerHighest")
    )
}
