package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import java.util.concurrent.ConcurrentHashMap

/*
 * The app's typographic voice, expressed as a full Material 3 type scale.
 *
 * Two faces. Unbounded for the display roles — the big page titles, "Миксы", "Моя форма",
 * "Моя музыка": wide, round and heavy, its forms close to the blobs behind them, with a Cyrillic
 * drawn as its own rather than added on. Onest for everything else — track titles, artists,
 * captions, buttons: a calm modern grotesque, as good in Cyrillic as in Latin, that leaves the
 * covers the attention.
 *
 * Sizes and line heights follow the M3 spec, except the display roles: Unbounded is so wide that
 * at the spec's sizes a title of two words no longer fits across a phone. Weight and tracking are
 * where this app has an opinion: display and headline roles run heavy, which is what gave screens
 * like "Моя музыка" their punch. That opinion lives here, once, instead of being re-stated as
 * `fontWeight = FontWeight.Black` at fifty call sites.
 *
 * Body roles stay at spec weight and tracking — that's reading text, and tightening it would only
 * cost legibility.
 */

/**
 * Loads variable fonts straight through Android's native minikin [android.graphics.fonts.Font.Builder]
 * and [android.graphics.Typeface.CustomFallbackBuilder] instead of [androidx.core.content.res.ResourcesCompat.getFont]
 * + `Paint.setFontVariationSettings`:
 * 1) OEM shells (MIUI / HyperOS, ColorOS, One UI, MagicOS) with a custom system theme font hook
 *    `Resources.getFont` / `Paint.setTypeface` and replace app fonts with the phone's theme font;
 * 2) Compose's `ResourceFont` path applies Android 12+ `Configuration.fontWeightAdjustment` twice
 *    (once in `AndroidFontResolveInterceptor` and again in `PlatformFontVariationSettings`), pushing
 *    weights past the TTF `fvar` range (e.g. 1000 > 900 or 100 < 200 on Unbounded) so the variation
 *    call is rejected or bloated.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private class LockedVariableFont(
    val resId: Int,
    override val weight: FontWeight,
    val axisWeight: Int
) : AndroidFont(
    loadingStrategy = FontLoadingStrategy.Blocking,
    typefaceLoader = LockedTypefaceLoader,
    variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight))
) {
    override val style: FontStyle = FontStyle.Normal
}

private object LockedTypefaceLoader : AndroidFont.TypefaceLoader {
    private val cache = ConcurrentHashMap<Long, android.graphics.Typeface>()

    override fun loadBlocking(context: android.content.Context, font: AndroidFont): android.graphics.Typeface {
        val locked = font as LockedVariableFont
        val key = (locked.resId.toLong() shl 32) or (locked.axisWeight.toLong() and 0xFFFF_FFFFL)
        cache[key]?.let { return it }
        val built = buildLockedTypeface(context, locked.resId, locked.axisWeight)
        cache[key] = built
        return built
    }

    override suspend fun awaitLoad(context: android.content.Context, font: AndroidFont): android.graphics.Typeface =
        loadBlocking(context, font)

    private fun buildLockedTypeface(context: android.content.Context, resId: Int, axisWeight: Int): android.graphics.Typeface {
        runCatching {
            val platformFont = android.graphics.fonts.Font.Builder(context.resources, resId)
                .setWeight(axisWeight)
                .setSlant(android.graphics.fonts.FontStyle.FONT_SLANT_UPRIGHT)
                .setFontVariationSettings(arrayOf(android.graphics.fonts.FontVariationAxis("wght", axisWeight.toFloat())))
                .build()
            val family = android.graphics.fonts.FontFamily.Builder(platformFont).build()
            return android.graphics.Typeface.CustomFallbackBuilder(family)
                .setSystemFallback("sans-serif")
                .build()
        }
        val base = androidx.core.content.res.ResourcesCompat.getFont(context, resId)
            ?: return android.graphics.Typeface.DEFAULT
        val paint = android.graphics.Paint().apply {
            typeface = base
            fontVariationSettings = "'wght' $axisWeight"
        }
        return paint.typeface ?: base
    }
}

/** Every weight of a variable font [res], clamped to its real `fvar` `wght` axis bounds. */
private fun variableFamily(res: Int, minAxisWeight: Int = 100, maxAxisWeight: Int = 900): FontFamily = FontFamily(
    (100..900 step 100).map { weight ->
        val clamped = weight.coerceIn(minAxisWeight, maxAxisWeight)
        LockedVariableFont(resId = res, weight = FontWeight(weight), axisWeight = clamped)
    }
)

internal val Display = variableFamily(R.font.unbounded, minAxisWeight = 200, maxAxisWeight = 900)
internal val Sans = variableFamily(R.font.onest, minAxisWeight = 100, maxAxisWeight = 900)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 46.sp,
        lineHeight = 54.sp,
        letterSpacing = (-0.75).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    displaySmall = TextStyle(
        fontFamily = Display,
        fontWeight = FontWeight.Bold,
        fontSize = 29.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.75).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.25).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
