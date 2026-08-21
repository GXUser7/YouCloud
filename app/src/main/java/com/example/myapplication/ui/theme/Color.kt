package com.example.myapplication.ui.theme

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/*
 * Material Design 3 baseline tonal roles.
 *
 * These are the fallback schemes used when Material You (dynamic color) is turned off.
 * Every role in the M3 spec is filled in here so container/on-container pairs always
 * meet contrast — previously only a handful of roles were overridden, which left
 * containers paired against the default on-colors.
 */

// region Light scheme roles
val LightPrimary = Color(0xFF6750A4)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEADDFF)
val LightOnPrimaryContainer = Color(0xFF21005D)
val LightSecondary = Color(0xFF625B71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE8DEF8)
val LightOnSecondaryContainer = Color(0xFF1D192B)
val LightTertiary = Color(0xFF7D5260)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFD8E4)
val LightOnTertiaryContainer = Color(0xFF31111D)
val LightError = Color(0xFFB3261E)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFF9DEDC)
val LightOnErrorContainer = Color(0xFF410E0B)
val LightBackground = Color(0xFFFEF7FF)
val LightOnBackground = Color(0xFF1D1B20)
val LightSurface = Color(0xFFFEF7FF)
val LightOnSurface = Color(0xFF1D1B20)
val LightSurfaceVariant = Color(0xFFE7E0EC)
val LightOnSurfaceVariant = Color(0xFF49454F)
val LightOutline = Color(0xFF79747E)
val LightOutlineVariant = Color(0xFFCAC4D0)
val LightScrim = Color(0xFF000000)
val LightInverseSurface = Color(0xFF322F35)
val LightInverseOnSurface = Color(0xFFF5EFF7)
val LightInversePrimary = Color(0xFFD0BCFF)
val LightSurfaceDim = Color(0xFFDED8E1)
val LightSurfaceBright = Color(0xFFFEF7FF)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF7F2FA)
val LightSurfaceContainer = Color(0xFFF3EDF7)
val LightSurfaceContainerHigh = Color(0xFFECE6F0)
val LightSurfaceContainerHighest = Color(0xFFE6E0E9)
// endregion

// region Dark scheme roles
val DarkPrimary = Color(0xFFD0BCFF)
val DarkOnPrimary = Color(0xFF381E72)
val DarkPrimaryContainer = Color(0xFF4F378B)
val DarkOnPrimaryContainer = Color(0xFFEADDFF)
val DarkSecondary = Color(0xFFCCC2DC)
val DarkOnSecondary = Color(0xFF332D41)
val DarkSecondaryContainer = Color(0xFF4A4458)
val DarkOnSecondaryContainer = Color(0xFFE8DEF8)
val DarkTertiary = Color(0xFFEFB8C8)
val DarkOnTertiary = Color(0xFF492532)
val DarkTertiaryContainer = Color(0xFF633B48)
val DarkOnTertiaryContainer = Color(0xFFFFD8E4)
val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)
val DarkBackground = Color(0xFF141218)
val DarkOnBackground = Color(0xFFE6E0E9)
val DarkSurface = Color(0xFF141218)
val DarkOnSurface = Color(0xFFE6E0E9)
val DarkSurfaceVariant = Color(0xFF49454F)
val DarkOnSurfaceVariant = Color(0xFFCAC4D0)
val DarkOutline = Color(0xFF938F99)
val DarkOutlineVariant = Color(0xFF49454F)
val DarkScrim = Color(0xFF000000)
val DarkInverseSurface = Color(0xFFE6E0E9)
val DarkInverseOnSurface = Color(0xFF322F35)
val DarkInversePrimary = Color(0xFF6750A4)
val DarkSurfaceDim = Color(0xFF141218)
val DarkSurfaceBright = Color(0xFF3B383E)
val DarkSurfaceContainerLowest = Color(0xFF0F0D13)
val DarkSurfaceContainerLow = Color(0xFF1D1B20)
val DarkSurfaceContainer = Color(0xFF211F26)
val DarkSurfaceContainerHigh = Color(0xFF2B2930)
val DarkSurfaceContainerHighest = Color(0xFF36343B)
// endregion

/*
 * Brand colors.
 *
 * SoundCloud orange and Yandex yellow are fixed brand assets, but dropping them into the
 * UI as raw hex fights whatever palette Material You derived from the wallpaper. M3 solves
 * this with *color harmonization*: rotate the brand hue a small amount toward the scheme's
 * primary hue so the color keeps its identity but belongs to the palette.
 *
 * From those harmonized sources we derive a full set of roles (color / onColor /
 * container / onContainer) so brand accents behave like any other M3 color role.
 */

val SoundCloudBrandSource = Color(0xFFFF5500)
val YandexBrandSource = Color(0xFFFFCC00)

@Immutable
data class BrandColorRoles(
    val color: Color,
    val onColor: Color,
    val container: Color,
    val onContainer: Color
)

@Immutable
data class BrandColors(
    val soundCloud: BrandColorRoles,
    val yandex: BrandColorRoles
)

private fun Color.toHsv(): FloatArray = FloatArray(3).also { AndroidColor.colorToHSV(toArgb(), it) }

private fun hsvColor(hue: Float, saturation: Float, value: Float): Color = Color(
    AndroidColor.HSVToColor(
        floatArrayOf(
            ((hue % 360f) + 360f) % 360f,
            saturation.coerceIn(0f, 1f),
            value.coerceIn(0f, 1f)
        )
    )
)

/**
 * Shifts [design] toward the hue of [toward] by [ratio], preserving its saturation and value.
 * This is the hue-rotation half of Material's color harmonization.
 */
fun harmonize(design: Color, toward: Color, ratio: Float = 0.15f): Color {
    val designHsv = design.toHsv()
    val towardHsv = toward.toHsv()
    var delta = towardHsv[0] - designHsv[0]
    if (delta > 180f) delta -= 360f
    if (delta < -180f) delta += 360f
    return hsvColor(designHsv[0] + delta * ratio, designHsv[1], designHsv[2])
}

/** Picks a legible on-color for [background] rather than always using white. */
private fun onColorFor(background: Color): Color =
    if (background.luminance() > 0.45f) Color(0xFF1B1B1B) else Color(0xFFFFFFFF)

/**
 * Derives a full M3 role set for [source], harmonized against the active scheme's [primary].
 */
fun brandColorRoles(source: Color, primary: Color, darkTheme: Boolean): BrandColorRoles {
    val harmonized = harmonize(source, primary).toHsv()
    val hue = harmonized[0]
    val saturation = harmonized[1]

    return if (darkTheme) {
        val color = hsvColor(hue, saturation * 0.78f, 0.94f)
        BrandColorRoles(
            color = color,
            onColor = onColorFor(color),
            container = hsvColor(hue, saturation * 0.85f, 0.34f),
            onContainer = hsvColor(hue, saturation * 0.35f, 0.94f)
        )
    } else {
        val color = hsvColor(hue, saturation, 0.82f)
        BrandColorRoles(
            color = color,
            onColor = onColorFor(color),
            container = hsvColor(hue, saturation * 0.24f, 0.97f),
            onContainer = hsvColor(hue, saturation, 0.25f)
        )
    }
}
