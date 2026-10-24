package com.example.myapplication.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/*
 * Overscroll on the glass screens: a list pulled past its end follows the finger part of the way
 * and springs back — moved, not stretched. Android's stretch draws the list into a picture of its
 * own while it stretches, and the glass in the list then opened only that picture, not the window
 * down to the backdrop: every pane of it changed for as long as the stretch lasted, a flicker, and
 * the backdrop's blur under the panes stayed where they had been. Moved by a layer's translation,
 * the list stays in the window, and its glass tells the backdrop where it went (see frosted).
 */

/** Overscroll for where [LocalGlass] is on: see [GlassOverscroll]. */
internal object GlassOverscrollFactory : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = GlassOverscroll()

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class GlassOverscroll : OverscrollEffect {
    // How far past its end the content is pulled, in the finger's pixels; on screen it goes less
    // and less of that the further it is pulled (see stretched).
    private var pulled by mutableStateOf(Offset.Zero)
    private var settling by mutableStateOf(false)

    override val isInProgress: Boolean
        get() = pulled != Offset.Zero || settling

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        // A drag back first takes the content in to its place, then moves the list.
        val relaxed = Offset(relaxed(pulled.x, delta.x), relaxed(pulled.y, delta.y))
        if (relaxed != Offset.Zero) pulled += relaxed
        val left = delta - relaxed
        val scrolled = performScroll(left)
        val over = left - scrolled
        // Only a finger pulls the content past its end; a fling running into it is applyToFling's.
        if (source == NestedScrollSource.UserInput && over != Offset.Zero) {
            pulled += over
            return delta
        }
        return relaxed + scrolled
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (pulled != Offset.Zero) {
            // Let go past its end: back in place, the finger's speed carried into the spring.
            settle(velocity)
            return
        }
        // A fling run into its end goes on a little past it and comes back.
        val left = performFling(velocity)
        if (left != Velocity.Zero) settle(left)
    }

    private suspend fun settle(velocity: Velocity) = coroutineScope {
        settling = true
        try {
            launch {
                animate(pulled.x, 0f, velocity.x, SettleSpring) { value, _ -> pulled = pulled.copy(x = value) }
            }
            launch {
                animate(pulled.y, 0f, velocity.y, SettleSpring) { value, _ -> pulled = pulled.copy(y = value) }
            }.join()
        } finally {
            settling = false
        }
    }

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                // Read in the layer, not the layout: the content moves without being laid out again.
                placeable.placeWithLayer(0, 0) {
                    translationX = stretched(pulled.x, size.width)
                    translationY = stretched(pulled.y, size.height)
                }
            }
        }
    }
}

/** What of a drag [delta] takes content pulled [pulled] past its end back toward its place. */
private fun relaxed(pulled: Float, delta: Float): Float = when {
    pulled == 0f || delta == 0f || sign(delta) == sign(pulled) -> 0f
    abs(delta) >= abs(pulled) -> -pulled
    else -> delta
}

/** Content pulled [pulled] past its end, on screen: half or so of a short pull, never the [extent]. */
private fun stretched(pulled: Float, extent: Float): Float {
    if (pulled == 0f || extent <= 0f) return 0f
    return sign(pulled) * (1f - 1f / (abs(pulled) * RUBBER / extent + 1f)) * extent
}

// The rubber band iOS lists are pulled with.
private const val RUBBER = 0.55f
private val SettleSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
