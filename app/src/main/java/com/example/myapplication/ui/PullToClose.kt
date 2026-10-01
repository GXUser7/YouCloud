package com.example.myapplication.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Told whether a page is pulled off its place: what lies under the page is drawn again then, ready
 * to be shown as it comes away (see MusicScreen).
 */
internal val LocalPagePulled = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

/**
 * A page that closes the way the full player folds away: pulled down — past the top of its list, or
 * anywhere on it nothing scrolls — the whole page follows the finger, shrinking back with its
 * corners rounding, and let go far or fast enough it goes on down and [onClose] is called; short of
 * that it springs back. A drag back up gives the page its place again before the list scrolls.
 *
 * The pages it is on have no back button: this and the system's back gesture are how they close.
 */
@Composable
internal fun Modifier.pullToClose(onClose: () -> Unit): Modifier {
    val pull = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableFloatStateOf(1f) }
    // Let go on its way out: what is left of the gesture changes nothing.
    var closing by remember { mutableStateOf(false) }
    val flingPx = with(LocalDensity.current) { QueueFlingVelocity.toPx() }
    val currentOnClose by rememberUpdatedState(onClose)
    val reportPulled = LocalPagePulled.current
    LaunchedEffect(pull) { snapshotFlow { pull.value > 0f }.collect { reportPulled(it) } }
    DisposableEffect(Unit) { onDispose { reportPulled(false) } }

    fun progress() = (pull.value / heightPx).coerceIn(0f, 1f)

    // Moves the page by [dy] pixels (down is positive); what it could take of them.
    fun moveBy(dy: Float): Float {
        if (closing) return 0f
        val before = pull.value
        val after = (before + dy).coerceAtLeast(0f)
        // Undispatched: moved in this very frame, not the next, or it trails the finger.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { pull.snapTo(after) }
        return after - before
    }

    fun release(velocity: Float) {
        if (closing || pull.value <= 0f) return
        val away = if (abs(velocity) > flingPx) velocity > 0f else progress() > CollapseMeantFraction
        if (away) closing = true
        scope.launch {
            if (away) {
                pull.animateTo(heightPx, CollapseSpring, initialVelocity = velocity)
                currentOnClose()
            } else {
                pull.animateTo(0f, QueueSpring, initialVelocity = velocity)
            }
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                // Pulled down, a drag back up raises the page before the list moves.
                if (pull.value > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                    Offset(0f, moveBy(available.y))
                } else {
                    Offset.Zero
                }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                // What the list has no use for, at its top, pulls the page. Only a finger's: a
                // fling running into the top stretches the list as before.
                if (available.y > 0f && source == NestedScrollSource.UserInput) {
                    Offset(0f, moveBy(available.y))
                } else {
                    Offset.Zero
                }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull.value <= 0f) return Velocity.Zero
                release(available.y)
                return available
            }
        }
    }

    return this
        .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
        .nestedScroll(connection)
        // The finger off with the page still pulled and nothing under way: the list handed over
        // no fling to decide by. It is decided as if let go at rest, so the page never stays
        // halfway. Two frames on, after any fling the list does hand over.
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                } while (event.changes.any { it.pressed })
                scope.launch {
                    withFrameNanos { }
                    withFrameNanos { }
                    if (!pull.isRunning) release(0f)
                }
            }
        }
        // Where nothing scrolls (a side pane, a page still loading) the drag is taken here. A list
        // under the finger takes its drags first, and these then see them taken.
        .pointerInput(Unit) {
            val velocity = VelocityTracker()
            detectVerticalDragGestures(
                onDragStart = {
                    velocity.resetTracking()
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { if (!closing) pull.stop() }
                },
                onVerticalDrag = { change, dy ->
                    velocity.addPosition(change.uptimeMillis, change.position)
                    change.consume()
                    moveBy(dy)
                },
                onDragEnd = { release(velocity.calculateVelocity().y) },
                onDragCancel = { release(0f) }
            )
        }
        // What the page came over, dimmed as it starts coming down and clearing as it goes.
        .drawBehind {
            val pulled = progress()
            if (pulled > 0f) drawRect(Color.Black.copy(alpha = PulledScrim * (1f - pulled)))
        }
        .graphicsLayer {
            val pulled = progress()
            translationY = pull.value
            val scale = 1f - 0.08f * pulled
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, 0f)
            if (pulled > 0f) {
                // Rounded almost as soon as it moves, as a card lifted off the screen.
                shape = RoundedCornerShape(PulledCorner * (pulled / 0.15f).coerceAtMost(1f))
                clip = true
            }
            alpha = 1f - ((pulled - CollapseFadeFrom) / (1f - CollapseFadeFrom)).coerceIn(0f, 1f)
        }
        // Moved as a whole, it is moved as the picture it is: its glass isn't drawn again for it.
        .holdFrost { pull.value > 0f }
}
