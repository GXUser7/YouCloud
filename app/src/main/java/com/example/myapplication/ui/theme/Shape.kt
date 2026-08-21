package com.example.myapplication.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * The shape scale, tuned rounder than M3 baseline.
 *
 * Baseline M3 is 4/8/12/16/28, which reads crisp and utilitarian. This app's character is
 * soft and generous — the original UI reached for 24-42dp radii by hand. Rather than
 * fight that or scatter one-off values, the scale itself is shifted up a step, which is
 * the direction M3 Expressive takes too. Every component that reads `MaterialTheme.shapes`
 * inherits the softer feel for free.
 */
val AppShapeScale = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * Rounder steps beyond the five spec roles, for the app's hero surfaces.
 *
 * Kept out of [AppShapeScale] so `MaterialTheme.shapes` stays exactly the five M3 roles,
 * while hero cards and sheets still get named tokens instead of magic numbers.
 */
object AppShapes {
    val largeIncreased = RoundedCornerShape(28.dp)
    val extraLargeIncreased = RoundedCornerShape(36.dp)
    val extraExtraLarge = RoundedCornerShape(44.dp)

    /** Top-rounded shape for bottom sheets and sheet-like panels. */
    val bottomSheet = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
}
