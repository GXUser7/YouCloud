package com.example.myapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    inversePrimary = DarkInversePrimary,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest
)

private val LocalBrandColors = staticCompositionLocalOf {
    BrandColors(
        soundCloud = brandColorRoles(SoundCloudBrandSource, LightPrimary, darkTheme = false)
    )
}

/**
 * Colours of the app's large colour blocks: the player, the mini player, collection heroes and
 * the mix header.
 *
 * In a dark theme these used `primaryContainer`, which is tone 30 of the accent. Laid across
 * half the screen that glowed next to the system UI, whose widgets, folders and themed icons
 * sit on tone 20 of the *secondary* palette (`system_accent2_800`): the same hue, calmer.
 * Panels now take exactly that tone, so the app reads as part of the home screen, and the
 * light accent is left to the one control on a panel that should pop.
 */
@Immutable
data class PanelColorRoles(
    val container: Color,
    val content: Color,
    val accent: Color,
    val onAccent: Color
)

private val LocalPanelColors = staticCompositionLocalOf {
    PanelColorRoles(
        container = LightSecondaryContainer,
        content = LightOnSecondaryContainer,
        accent = LightPrimary,
        onAccent = LightOnPrimary
    )
}

/**
 * Access point for app-level color roles that sit alongside [MaterialTheme.colorScheme].
 *
 * Usage mirrors MaterialTheme: `AppTheme.brand.soundCloud.container`.
 */
object AppTheme {
    val brand: BrandColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBrandColors.current

    val panel: PanelColorRoles
        @Composable
        @ReadOnlyComposable
        get() = LocalPanelColors.current
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Use the wallpaper palette (Material You) or the baseline M3 scheme above
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        useDynamic -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val panelColors = remember(colorScheme, darkTheme, useDynamic) {
        panelColorRoles(
            colorScheme,
            darkTheme,
            // Read straight from the system palette so it is the very tone the launcher uses.
            darkContainer = if (useDynamic) Color(context.getColor(android.R.color.system_accent2_800)) else null
        )
    }

    // The SoundCloud accent is harmonized against whichever primary the scheme ended up
    // with, so its orange sits inside the Material You palette instead of fighting it.
    // Yandex Music no longer has an accent of its own: its screens use the scheme's roles
    // like everything else.
    val brandColors = remember(colorScheme.primary, darkTheme) {
        BrandColors(
            soundCloud = brandColorRoles(SoundCloudBrandSource, colorScheme.primary, darkTheme)
        )
    }

    CompositionLocalProvider(
        LocalBrandColors provides brandColors,
        LocalPanelColors provides panelColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapeScale,
            content = content
        )
    }
}

/**
 * Panel roles for [colorScheme]. In a dark theme the container is tone 20 of the secondary
 * palette, which the scheme itself exposes as `onSecondary`; [darkContainer] overrides it with
 * the system's own value where there is one.
 */
fun panelColorRoles(
    colorScheme: ColorScheme,
    darkTheme: Boolean,
    darkContainer: Color? = null
): PanelColorRoles = if (darkTheme) {
    PanelColorRoles(
        container = darkContainer ?: colorScheme.onSecondary,
        content = colorScheme.onPrimaryContainer,
        accent = colorScheme.primary,
        onAccent = colorScheme.onPrimary
    )
} else {
    PanelColorRoles(
        container = colorScheme.secondaryContainer,
        content = colorScheme.onSecondaryContainer,
        accent = colorScheme.primary,
        onAccent = colorScheme.onPrimary
    )
}

/**
 * Re-themes one part of the app — the player, with colours taken from a cover — while keeping
 * the app's type and shapes. Panels inside follow the new scheme the same way the app's do.
 */
@Composable
fun ScopedColorTheme(
    colorScheme: ColorScheme,
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Panel roles of their own, when the caller picks them (the cover's accent at full strength);
    // otherwise derived from [colorScheme] the way the app's are.
    panel: PanelColorRoles? = null,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalPanelColors provides (panel ?: panelColorRoles(colorScheme, darkTheme))) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content
        )
    }
}
