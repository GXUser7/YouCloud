package com.example.myapplication.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateTo
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

/**
 * Up and down only. Sideways — home's and search's pages of services, a shelf, a carousel — there
 * is nothing to stretch, and a band there sprang: a pager hands back what it doesn't use of a
 * fling's speed once it has snapped, and taken for a fling run into an end, every turn of a page
 * went on past it and came back. Dragged past a row's end, the services around it move instead.
 */
private class GlassOverscroll : OverscrollEffect {
    // How far past its end the content is pulled, in the finger's pixels; on screen it goes less
    // and less of that the further it is pulled (see stretched). Down the screen only.
    private var pulled by mutableStateOf(0f)
    private var settling by mutableStateOf(false)
    // A finger came down on the list while it sprang back: the spring gives way to it.
    private var interrupted = false

    // Only while a finger holds it past its end. Springing back it isn't "in progress": a list in
    // progress takes every touch on it at once, and a swipe sideways — to a carousel's next cover,
    // the profile's other page — made while it sprang back moved nothing.
    override val isInProgress: Boolean
        get() = pulled != 0f && !settling

    override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset): Offset {
        if (settling && source == NestedScrollSource.UserInput) interrupted = true
        // A drag back first takes the content in to its place, then moves the list.
        val relaxed = relaxed(pulled, delta.y)
        if (relaxed != 0f) pulled += relaxed
        val left = delta - Offset(0f, relaxed)
        val scrolled = performScroll(left)
        val over = (left - scrolled).y
        // Only a finger pulls the content past its end; a fling running into it is applyToFling's.
        if (source == NestedScrollSource.UserInput && over != 0f) {
            pulled += over
            return Offset(scrolled.x, delta.y)
        }
        return Offset(scrolled.x, relaxed + scrolled.y)
    }

    override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
        if (pulled != 0f) {
            // Let go past its end: back in place, the finger's speed carried into the spring. Let
            // go moving on past it, the fling is offered round the list first: a page pulled down
            // to close by a finger that caught the list springing back at its top closes on it —
            // kept from it, the page went back up unless dragged most of the way.
            val onward = velocity.y != 0f && sign(velocity.y) == sign(pulled)
            settle(if (onward) performFling(velocity).y else velocity.y)
            return
        }
        // A fling run into its end goes on a little past it and comes back. Not one that has run
        // out by itself: what it hands back is a last breath of speed, not nothing, and taken for
        // the end it set the list springing after every fling, in the middle of it.
        val left = performFling(velocity)
        if (abs(left.y) > MIN_OVERSHOOT_VELOCITY) settle(left.y)
    }

    private suspend fun settle(velocity: Float) {
        settling = true
        interrupted = false
        try {
            AnimationState(initialValue = pulled, initialVelocity = velocity)
                .animateTo(0f, SettleSpring) { if (interrupted) cancelAnimation() else pulled = value }
        } finally {
            settling = false
            interrupted = false
        }
    }

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                // Read in the layer, not the layout: the content moves without being laid out again.
                placeable.placeWithLayer(0, 0) {
                    translationY = stretched(pulled, size.height)
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

// Faster than this, in pixels a second, what a fling hands back is a fling run into an end.
private const val MIN_OVERSHOOT_VELOCITY = 400f

// The rubber band iOS lists are pulled with.
private const val RUBBER = 0.55f
private val SettleSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
