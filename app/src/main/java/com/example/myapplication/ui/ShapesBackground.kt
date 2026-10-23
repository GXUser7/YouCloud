@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.myapplication.ui

import android.content.Context
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.getValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.currentStateAsState
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * The app's backdrop: Material 3 Expressive shapes — the very polygons the components use —
 * drifting behind the content, now with volume.
 *
 * Each shape is lit from one direction: a gradient from a lit face to a shaded one, a soft
 * highlight, a rim of light along the lit edge and a blurred shadow cast away from the light.
 * Shapes sit at different depths; nearer ones are larger, stronger and move more, farther ones
 * fade into the backdrop. Tilting the phone slides them by depth (parallax) and swings the light,
 * and a shake knocks them about on springs — so the scene reads as objects in a space behind the
 * glass, not as a flat pattern.
 *
 * Motion comes from the accelerometer rather than the gyroscope: gravity in it gives the tilt and
 * what is left over gives the shakes, from one cheap sensor every phone has. The gyroscope only
 * reports rotation speed, drifts when integrated, and cannot tell a shake from a turn.
 */

/** One shape of the scene. Positions and sizes are fractions of the screen. */
private class BackdropShape(
    val from: RoundedPolygon,
    val to: RoundedPolygon?,
    val cx: Float,
    val cy: Float,
    val size: Float,
    /** 0 = far back, 1 = right behind the glass. */
    val depth: Float,
    /** Degrees per second; the sign sets the direction. */
    val spin: Float,
    val morphSeconds: Float = 0f
) {
    val morph: Morph? = to?.let { Morph(from, it) }
    val basePath: android.graphics.Path = from.toPath()
}

private fun backdropShapes(): List<BackdropShape> = listOf(
    BackdropShape(MaterialShapes.SoftBurst, null, cx = 0.9f, cy = 0.74f, size = 0.6f, depth = 0.25f, spin = 3f),
    BackdropShape(MaterialShapes.Pentagon, null, cx = 0.28f, cy = 0.07f, size = 0.22f, depth = 0.3f, spin = 9f),
    BackdropShape(MaterialShapes.Cookie9Sided, null, cx = 0.94f, cy = 0.1f, size = 0.64f, depth = 0.5f, spin = 4f),
    BackdropShape(
        MaterialShapes.Flower, MaterialShapes.Cookie12Sided,
        cx = 0.7f, cy = 0.44f, size = 0.3f, depth = 0.65f, spin = -8f, morphSeconds = 11f
    ),
    BackdropShape(MaterialShapes.Clover4Leaf, null, cx = 0.04f, cy = 0.4f, size = 0.52f, depth = 0.8f, spin = -6f),
    BackdropShape(
        MaterialShapes.Pill, MaterialShapes.Sunny,
        cx = 0.14f, cy = 0.94f, size = 0.44f, depth = 0.95f, spin = -3f, morphSeconds = 14f
    )
)

/**
 * Shakes of the phone as the backdrop feels them, passed on to what spins with its shapes: the
 * wave's shape on home. Signed by which way the phone was thrown. Felt on the backdrop's thread,
 * read on the main one.
 */
internal object BackdropShake {
    private var pending = 0f

    @Synchronized
    fun add(kick: Float, kickX: Float) {
        if (kick > 0f) pending = (pending + if (kickX < 0f) -kick else kick).coerceIn(-MAX_PENDING, MAX_PENDING)
    }

    /** The shakes since last asked, and none left over for the next time. */
    @Synchronized
    fun take(): Float {
        val shake = pending
        pending = 0f
        return shake
    }

    private const val MAX_PENDING = 8f
}

/**
 * The panes of glass on the screen, as the backdrop blurs under them. Glass only opens the window
 * down to the backdrop (see frosted); the shapes it shows there, blurred as glass blurs what is
 * behind it, the backdrop draws itself, on its own surface, so that the window still has no frame
 * to draw while they move. Told by the panes on the main thread (see BackdropBlurNode), read on
 * the backdrop's.
 */
internal object BackdropGlass {
    /**
     * A pane: its [outline] on the window, the part of it [visible] (a list's edge clips it), how
     * much of it is there ([alpha]: a page fading in), and whether it is a plain [rect] — a page,
     * under which there is nothing sharp of the backdrop left. [window] is the window it is in: a
     * dialog's glass stands over its own window, not over the backdrop.
     */
    class Pane(
        val window: android.view.View,
        val outline: android.graphics.Path,
        val visible: android.graphics.RectF,
        val alpha: Float,
        val rect: Boolean
    )

    private val panes = LinkedHashMap<Any, Pane>()
    private var listener: Runnable? = null

    // The panes tell one by one as the screen is laid out; the backdrop hears of them once, when
    // the frame is done and has gone to the RenderThread. Told of the first alone, it drew the
    // others where they had been, and its drawing them again in the same frame HWUI dropped.
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var told = false
    private val tell = Runnable {
        told = false
        synchronized(this) { listener }?.run()
    }

    fun put(owner: Any, pane: Pane) {
        synchronized(this) { panes[owner] = pane }
        changed()
    }

    fun remove(owner: Any) {
        val removed = synchronized(this) { panes.remove(owner) != null }
        if (removed) changed()
    }

    /** The panes there are now, into [into], back to front. */
    fun panes(into: MutableList<Pane>) = synchronized(this) {
        into.clear()
        into.addAll(panes.values)
    }

    fun watch(listener: Runnable) = synchronized(this) { this.listener = listener }

    fun unwatch(listener: Runnable) = synchronized(this) { if (this.listener === listener) this.listener = null }

    // On the main thread, as the panes are.
    private fun changed() {
        if (told) return
        told = true
        main.postAtFrontOfQueue(tell)
    }
}

/** Positions and velocities of the shapes, integrated once per frame. */
private class BackdropMotion(count: Int) {
    val x = FloatArray(count)
    val y = FloatArray(count)
    val vx = FloatArray(count)
    val vy = FloatArray(count)
    val wobble = FloatArray(count)
    val wobbleVelocity = FloatArray(count)
    val angle = FloatArray(count) { it * 37f }
    var time = 0f
    /** Where the light comes from, as a unit vector; swings with the tilt. */
    var lightX = -0.55f
    var lightY = -0.83f
    /**
     * Whether a shape is moving faster than the slow drift — a tilt being followed, a shake
     * settling. Otherwise the scene only turns and breathes, under a pixel a frame at thirty frames a
     * second, and blurred: drawing it more often than that is work nobody can see.
     */
    var lively = true
        private set

    fun step(dt: Float, shapes: List<BackdropShape>, tilt: TiltSensor, shiftPx: Float) {
        time += dt
        var fastest = 0f
        var fastestWobble = 0f
        // Shakes since the last frame, consumed by this one.
        val kickX = tilt.kickX
        val kickY = tilt.kickY
        val kick = tilt.kick
        tilt.clearImpulse()
        BackdropShake.add(kick, kickX)
        shapes.forEachIndexed { i, shape ->
            // Springs toward the parallax position: nearer shapes travel further.
            val targetX = -tilt.tiltX * shape.depth * shiftPx
            val targetY = tilt.tiltY * shape.depth * shiftPx
            vx[i] += ((targetX - x[i]) * STIFFNESS - vx[i] * DAMPING) * dt
            vy[i] += ((targetY - y[i]) * STIFFNESS - vy[i] * DAMPING) * dt
            // A shake throws them the other way, then the springs pull them home.
            vx[i] = (vx[i] - kickX * shape.depth * KICK_GAIN * shiftPx).coerceIn(-MAX_SPEED * shiftPx, MAX_SPEED * shiftPx)
            vy[i] = (vy[i] + kickY * shape.depth * KICK_GAIN * shiftPx).coerceIn(-MAX_SPEED * shiftPx, MAX_SPEED * shiftPx)
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt

            wobbleVelocity[i] += (-wobble[i] * WOBBLE_STIFFNESS - wobbleVelocity[i] * WOBBLE_DAMPING) * dt
            wobbleVelocity[i] = (wobbleVelocity[i] + kick * (if (i % 2 == 0) 1f else -1f) * WOBBLE_KICK * (0.5f + shape.depth))
                .coerceIn(-MAX_WOBBLE_SPEED, MAX_WOBBLE_SPEED)
            wobble[i] += wobbleVelocity[i] * dt

            angle[i] = (angle[i] + shape.spin * dt) % 360f
            fastest = maxOf(fastest, kotlin.math.abs(vx[i]), kotlin.math.abs(vy[i]))
            fastestWobble = maxOf(fastestWobble, kotlin.math.abs(wobbleVelocity[i]))
        }
        lively = fastest > LIVELY_SPEED * shiftPx || fastestWobble > LIVELY_WOBBLE
        val lx = -0.55f + tilt.tiltX * 0.9f
        val ly = -0.83f - tilt.tiltY * 0.9f
        val length = sqrt(lx * lx + ly * ly).coerceAtLeast(0.001f)
        lightX = lx / length
        lightY = ly / length
    }

    private companion object {
        const val STIFFNESS = 38f
        const val DAMPING = 7f
        // A firm shake (about half a g past gravity) throws the nearest shape roughly its full
        // parallax distance; the caps keep a violent one from flinging shapes off screen.
        const val KICK_GAIN = 8f
        const val MAX_SPEED = 16f
        const val WOBBLE_STIFFNESS = 30f
        const val WOBBLE_DAMPING = 4.5f
        const val WOBBLE_KICK = 260f
        const val MAX_WOBBLE_SPEED = 600f
        // A quarter of the parallax distance a second, a few degrees a second of wobble: past
        // these a shape moves far enough between frames for thirty of them to show.
        const val LIVELY_SPEED = 0.25f
        const val LIVELY_WOBBLE = 3f
    }
}

/**
 * Tilt and shakes from the accelerometer. Gravity is its low-passed signal; tilt is measured
 * against a slowly drifting rest pose, so the scene centres itself on however the phone is being
 * held and only answers movement. What gravity does not explain is the phone being shaken.
 */
private class TiltSensor : SensorEventListener {
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set

    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var restX = 0f
    private var restY = 0f
    private var primed = false
    var kickX = 0f
        private set
    var kickY = 0f
        private set
    var kick = 0f
        private set

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        if (!primed) {
            gx = x; gy = y; gz = z
            restX = x; restY = y
            primed = true
        }
        gx += (x - gx) * 0.2f
        gy += (y - gy) * 0.2f
        gz += (z - gz) * 0.2f
        restX += (gx - restX) * 0.01f
        restY += (gy - restY) * 0.01f
        tiltX = ((gx - restX) / SensorManager.GRAVITY_EARTH * 2.2f).coerceIn(-1f, 1f)
        tiltY = ((gy - restY) / SensorManager.GRAVITY_EARTH * 2.2f).coerceIn(-1f, 1f)

        val lx = x - gx
        val ly = y - gy
        val lz = z - gz
        val magnitude = sqrt(lx * lx + ly * ly + lz * lz)
        if (magnitude > SHAKE_THRESHOLD) {
            kickX += lx / SensorManager.GRAVITY_EARTH
            kickY += ly / SensorManager.GRAVITY_EARTH
            kick += (magnitude - SHAKE_THRESHOLD) / SensorManager.GRAVITY_EARTH
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** The shakes read by a frame: without a Triple for them on each one. */
    fun clearImpulse() {
        kickX = 0f; kickY = 0f; kick = 0f
    }

    fun reset() {
        tiltX = 0f; tiltY = 0f
        kickX = 0f; kickY = 0f; kick = 0f
        primed = false
    }

    private companion object {
        const val SHAKE_THRESHOLD = 1.6f
    }
}

/**
 * The backdrop. [motionEnabled] ties the shapes to the accelerometer; [animated] false freezes
 * the scene on its last frame (nothing is drawn then).
 *
 * It stands on a surface of its own under the window (see [BackdropView]), its frames timed and
 * drawn from a thread of its own. Drawn in the window, each of its frames — thirty a second while
 * it only drifted — drew the whole screen again over it, every cover, line and pane of glass, for
 * shapes that had moved under a pixel; the window's frames are now the screens' own, and the
 * backdrop's draw it alone. Glass over it opens the window down to it (see [frosted]), and the
 * backdrop draws itself blurred there (see [BackdropGlass]).
 */
@Composable
internal fun ExpressiveBackground(motionEnabled: Boolean, animated: Boolean = true) {
    val palette = backdropPalette()
    // Paused, the scene stands still and the accelerometer is let go.
    val resumed by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val running = animated && resumed.isAtLeast(Lifecycle.State.RESUMED)
    AndroidView(
        factory = { context -> BackdropView(context) },
        update = { view -> view.configure(palette, motionEnabled = motionEnabled, animated = running) },
        onRelease = { view -> view.release() },
        modifier = Modifier.fillMaxSize()
    )
}

/** The backdrop's tones from the theme's. */
@Composable
private fun backdropPalette(): BackdropPalette {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = scheme.background.luminance() < 0.5f
    // A ground a step lighter than the app's background, so the shapes and the covers in front of
    // them have something to stand off from instead of all sinking into the same near-black.
    val ground = if (darkTheme) scheme.surfaceContainer else scheme.surfaceContainerLow
    // One family of tones — the secondary palette the launcher's widgets and folders use — so the
    // scene reads as one material at different depths rather than a handful of coloured stickers.
    return BackdropPalette(
        ground = ground,
        // Light, tone 90 shapes on a tone 96 ground were barely there: deepened towards the
        // secondary colour, so the near-white panels over them read as panels.
        body = if (darkTheme) scheme.secondaryContainer else lerp(scheme.secondaryContainer, scheme.secondary, 0.12f),
        light = if (darkTheme) scheme.secondary else Color.White,
        dark = if (darkTheme) Color.Black else scheme.secondary,
        darkTheme = darkTheme
    )
}

/**
 * The backdrop's surface, under the window: the window shows it wherever nothing is drawn over it.
 * Its frames are timed on a thread of its own by that thread's Choreographer, recorded there into
 * render nodes and drawn by HWUI as the window's are — the main thread has no part in them. The
 * shapes are drawn as they were in the window: the far ones four times smaller and blurred, the
 * near ones twice, both stretched back over the ground.
 *
 * Under glass (see [BackdropGlass]) the scene is drawn once more, eight times smaller and blurred
 * as glass blurs, cut to each pane. A pane that moves is drawn there again at once, out of turn and
 * from the layers as they are: only the cut changes, not the shapes.
 */
private class BackdropView(context: Context) : SurfaceView(context), SurfaceHolder.Callback {
    private val density = context.resources.displayMetrics.density
    private val shiftPx = PARALLAX_SHIFT_DP * density
    private val shapes = backdropShapes().sortedBy { it.depth }
    private val far = shapes.indices.filter { shapes[it].depth < NEAR_DEPTH }
    private val near = shapes.indices.filter { shapes[it].depth >= NEAR_DEPTH }
    private val motion = BackdropMotion(shapes.size)
    private val tilt = TiltSensor()
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val thread = HandlerThread("Backdrop", android.os.Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val handler = Handler(thread.looper)

    // From here on, the backdrop thread's alone.
    private val renderer = HardwareRenderer()
    private val root = RenderNode("backdrop")
    private val farNode = RenderNode("backdropFar")
    private val nearNode = RenderNode("backdropNear")
    private val glassNode = RenderNode("backdropGlass")
    private val paneNodes = ArrayList<RenderNode>()
    private val panes = ArrayList<BackdropGlass.Pane>()
    private val shown = ArrayList<BackdropGlass.Pane>()
    private val screen = android.graphics.RectF()
    // The frame of the scene the shapes are at, and the one each layer was recorded at: a layer
    // left out for a while (the sharp ones under a page) is recorded afresh as it comes back.
    private var scene = 0L
    private var farAt = -1L
    private var nearAt = -1L
    private var glassAt = -1L
    private var choreographer: Choreographer? = null
    private var surface: Surface? = null
    private var width = 0
    private var height = 0
    private var palette: BackdropPalette? = null
    private var farPaints: BackdropPaints? = null
    private var nearPaints: BackdropPaints? = null
    private var animated = false
    private var motionEnabled = false
    private var looping = false
    private var listening = false
    private var released = false
    private var last = 0L
    private var drawn = -1L

    // From the main thread: the window the backdrop is under, and where in it.
    @Volatile
    private var window: android.view.View? = null
    @Volatile
    private var originX = 0
    @Volatile
    private var originY = 0
    private val location = IntArray(2)

    // The frame the choreographer gave last, and the one before.
    private var vsync = 0L
    private var vsyncBefore = 0L
    // The shapes have moved on to a slot not drawn yet; HWUI dropped a frame, to be drawn again.
    private var sceneDue = false
    private var redrawDue = false
    // A frame callback, or a wake for one, is on its way.
    private var chained = false
    // Glass counts as moving until then.
    private var glassUntil = 0L

    /*
     * Glass that moves is drawn on the frame it moves on, once its place is told: a few
     * milliseconds into the frame, as the window's frame goes to the RenderThread (see
     * BackdropGlass). HWUI draws a surface once per frame and drops a second frame asked for in
     * the same one, so while glass moves the backdrop draws nothing at the frame's start — the
     * shapes' next step goes with the glass, or, told nothing by three quarters of the frame, is
     * drawn then (see late).
     */
    private val glassPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val glassMoved = Runnable {
        glassPending.set(false)
        if (released) return@Runnable
        val now = System.nanoTime()
        val moving = now < glassUntil
        glassUntil = now + GLASS_QUIET_NANOS
        handler.removeCallbacks(late)
        // Every frame watched from here on, for its time (see frame).
        if (!moving || !chained) frameNow()
        draw(if (vsync > 0L) vsync else now)
    }
    private val glassChanged = Runnable {
        if (glassPending.compareAndSet(false, true)) handler.postAtFrontOfQueue(glassMoved)
    }
    private val late = Runnable { if (!released && (sceneDue || redrawDue)) draw(if (vsync > 0L) vsync else System.nanoTime()) }

    init {
        holder.addCallback(this)
        // The ground covers it all: nothing under the surface shows through it.
        holder.setFormat(PixelFormat.OPAQUE)
        renderer.setContentRoot(root)
        renderer.setOpaque(true)
        handler.post { choreographer = Choreographer.getInstance() }
        BackdropGlass.watch(glassChanged)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        window = rootView
        glassChanged.run()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        getLocationInWindow(location)
        if (location[0] != originX || location[1] != originY) {
            originX = location[0]
            originY = location[1]
            glassChanged.run()
        }
    }

    /** What the screen says, from the main thread. */
    fun configure(palette: BackdropPalette, motionEnabled: Boolean, animated: Boolean) {
        handler.post {
            if (released) return@post
            if (palette != this.palette) {
                this.palette = palette
                farPaints = BackdropPaints(shapes, far, palette)
                nearPaints = BackdropPaints(shapes, near, palette)
                sceneDue = true
                draw(System.nanoTime())
            }
            this.motionEnabled = motionEnabled
            this.animated = animated
            update()
        }
    }

    fun release() {
        BackdropGlass.unwatch(glassChanged)
        handler.post {
            released = true
            animated = false
            update()
            renderer.destroy()
            thread.quitSafely()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) = Unit

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        val shown = holder.surface
        handler.post {
            if (released) return@post
            surface = shown
            renderer.setSurface(shown)
            this.width = width
            this.height = height
            // Its size settled: the nodes are laid out again for it on the next frame.
            farNode.setPosition(0, 0, 0, 0)
            nearNode.setPosition(0, 0, 0, 0)
            glassNode.setPosition(0, 0, 0, 0)
            sceneDue = true
            draw(System.nanoTime())
            update()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // The surface goes back to the system as this returns: nothing may draw to it after.
        val done = java.util.concurrent.CountDownLatch(1)
        handler.post {
            surface = null
            if (!released) renderer.setSurface(null)
            update()
            done.countDown()
        }
        done.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /** Frames asked for and the accelerometer listened to while they are any use. */
    private fun update() {
        val run = animated && !released && surface != null && palette != null
        if (run && !looping) {
            looping = true
            last = 0L
            frameNow()
        } else if (!run && looping) {
            looping = false
            chained = false
            choreographer?.removeFrameCallback(frame)
            handler.removeCallbacks(wake)
            handler.removeCallbacks(late)
        }
        val listen = run && motionEnabled && accelerometer != null
        if (listen && !listening) {
            sensors?.registerListener(tilt, accelerometer, SensorManager.SENSOR_DELAY_GAME, handler)
            listening = true
        } else if (!listen && listening) {
            sensors?.unregisterListener(tilt)
            tilt.reset()
            listening = false
        }
    }

    private val wake: Runnable = Runnable { choreographer?.postFrameCallback(frame) }

    /** The next frame of the screen asked for now, rather than at the next slot. */
    private fun frameNow() {
        handler.removeCallbacks(wake)
        choreographer?.removeFrameCallback(frame)
        choreographer?.postFrameCallback(frame)
        chained = true
    }

    private val frame: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            chained = false
            vsyncBefore = vsync
            vsync = now
            var slotNanos = 0L
            if (looping) {
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                motion.step(dt, shapes, tilt, shiftPx)
                // As often as MotionPace says, on the frames all else that moves by itself is drawn
                // on (see frameSlot).
                slotNanos = MotionPace.slotNanos(lively = motion.lively)
                val slot = frameSlot(now, slotNanos)
                if (slot != drawn) {
                    drawn = slot
                    sceneDue = true
                }
            }
            if (now < glassUntil) {
                // Glass is moving: drawn with it as it tells where it is now, or late in the frame.
                val wait = ((now - vsyncBefore).coerceIn(MIN_FRAME_NANOS, MAX_FRAME_NANOS) * 3 / 4) / 1_000_000L
                handler.removeCallbacks(late)
                handler.postDelayed(late, wait)
                choreographer?.postFrameCallback(this)
                chained = true
                return
            }
            if (sceneDue || redrawDue) draw(now)
            if (!looping) return
            // Tilted or shaken, on the next frame; drifting, it sleeps to its next slot.
            chained = true
            if (slotNanos <= 0L) {
                choreographer?.postFrameCallback(this)
            } else {
                val next = (now / slotNanos + 1) * slotNanos
                val wait = (next - System.nanoTime() - SLOT_WAKE_EARLY_NANOS) / 1_000_000L
                if (wait > 0L) handler.postDelayed(wake, wait) else choreographer?.postFrameCallback(this)
            }
        }
    }

    /**
     * A frame: the shapes where they are now if they have moved on to a slot since the last
     * ([sceneDue]), else only the glass over them cut again.
     */
    private fun draw(vsyncNanos: Long) {
        val palette = palette ?: return
        val far = farPaints ?: return
        val near = nearPaints ?: return
        if (surface == null || width <= 0 || height <= 0) return
        if (sceneDue) scene++
        sceneDue = false
        val ox = originX
        val oy = originY
        glassOver(ox, oy)
        // A page all there over the whole screen: nothing sharp shows anywhere, only the scene as
        // the glass blurs it.
        val whole = shown.any { it.rect && it.alpha >= 1f && it.visible.contains(screen) }
        if (!whole) {
            if (farAt != scene) record(farNode, this.far, far, FAR_SHRINK, FAR_BLUR.value).also { farAt = scene }
            if (nearAt != scene) record(nearNode, this.near, near, NEAR_SHRINK, NEAR_BLUR.value).also { nearAt = scene }
        }
        if (shown.isNotEmpty() && glassAt != scene) recordGlass(palette, far, near).also { glassAt = scene }
        val canvas = root.beginRecording(width, height)
        if (whole) {
            canvas.drawRenderNode(glassNode)
        } else {
            canvas.drawColor(palette.ground.toArgb())
            canvas.drawRenderNode(farNode)
            canvas.drawRenderNode(nearNode)
            shown.forEachIndexed { index, pane -> canvas.drawRenderNode(pane(index, pane, ox, oy)) }
        }
        root.endRecording()
        root.setPosition(0, 0, width, height)
        val result = renderer.createRenderRequest().setVsyncTime(vsyncNanos).syncAndDraw()
        // Dropped as a second frame in one: what was recorded is drawn on the next.
        redrawDue = result and HardwareRenderer.SYNC_FRAME_DROPPED != 0
        if (redrawDue && !chained) frameNow()
    }

    /**
     * Into [shown], the panes of glass over the backdrop: in its window, on the screen, there at
     * all — and not inside a page at least as much there as they are, which blurs that part already
     * (a card on the settings page).
     */
    private fun glassOver(ox: Int, oy: Int) {
        BackdropGlass.panes(panes)
        screen.set(ox.toFloat(), oy.toFloat(), (ox + width).toFloat(), (oy + height).toFloat())
        val window = window
        shown.clear()
        for (pane in panes) {
            if (pane.window === window && pane.alpha > 0f && android.graphics.RectF.intersects(pane.visible, screen)) shown += pane
        }
        if (shown.size < 2) return
        val inside = BooleanArray(shown.size) { index ->
            val pane = shown[index]
            shown.indices.any { other ->
                val page = shown[other]
                // Of two pages over each other, the one under stays.
                other != index && page.rect && page.alpha >= pane.alpha && page.visible.contains(pane.visible) &&
                    !(pane.rect && pane.alpha >= page.alpha && pane.visible.contains(page.visible) && index < other)
            }
        }
        var index = 0
        shown.removeAll { inside[index++] }
    }

    /**
     * One layer of the scene: drawn [shrink] times smaller and stretched back, blurred on the way —
     * soft shapes lose nothing by it, and a full-screen blur on every frame was most of what the
     * backdrop cost.
     */
    private fun record(node: RenderNode, indices: List<Int>, paints: BackdropPaints, shrink: Int, blurDp: Float) {
        val canvas = beginLayer(node, shrink, blurDp, android.graphics.Shader.TileMode.DECAL)
        drawBackdropShapes(canvas, shapes, indices, motion, paints, width.toFloat(), height.toFloat(), density)
        node.endRecording()
    }

    /** The whole scene as glass shows it: the ground and every shape, blurred as [frosted] blurs. */
    private fun recordGlass(palette: BackdropPalette, far: BackdropPaints, near: BackdropPaints) {
        // Clamped at the screen's edges, where the ground goes on: not faded out into nothing.
        val canvas = beginLayer(glassNode, GLASS_SHRINK, FrostBlur.value, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawColor(palette.ground.toArgb())
        drawBackdropShapes(canvas, shapes, this.far, motion, far, width.toFloat(), height.toFloat(), density)
        drawBackdropShapes(canvas, shapes, this.near, motion, near, width.toFloat(), height.toFloat(), density)
        glassNode.endRecording()
    }

    /** [node] recording, laid out [shrink] times smaller than the screen and stretched back over it. */
    private fun beginLayer(node: RenderNode, shrink: Int, blurDp: Float, edges: android.graphics.Shader.TileMode): android.graphics.RecordingCanvas {
        val small = (width + shrink - 1) / shrink
        val tall = (height + shrink - 1) / shrink
        if (node.width != small || node.height != tall) {
            node.setPosition(0, 0, small, tall)
            node.setPivotX(0f)
            node.setPivotY(0f)
            node.setScaleX(shrink.toFloat())
            node.setScaleY(shrink.toFloat())
            val radius = blurDp * density / shrink
            node.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius, radius, edges))
        }
        val canvas = node.beginRecording(small, tall)
        canvas.scale(1f / shrink, 1f / shrink)
        return canvas
    }

    /**
     * The [index]th pane of glass on the backdrop: the blurred scene cut to its outline, as much as
     * it is there. The backdrop's own place in the window is at [ox], [oy].
     */
    private fun pane(index: Int, pane: BackdropGlass.Pane, ox: Int, oy: Int): RenderNode {
        if (index == paneNodes.size) {
            // Its alpha on the one thing it draws, without a picture of it to fade.
            paneNodes += RenderNode("backdropPane").apply { setHasOverlappingRendering(false) }
        }
        val node = paneNodes[index]
        val left = (kotlin.math.floor(pane.visible.left).toInt() - ox).coerceIn(0, width)
        val top = (kotlin.math.floor(pane.visible.top).toInt() - oy).coerceIn(0, height)
        val right = (kotlin.math.ceil(pane.visible.right).toInt() - ox).coerceIn(left, width)
        val bottom = (kotlin.math.ceil(pane.visible.bottom).toInt() - oy).coerceIn(top, height)
        node.setPosition(left, top, right, bottom)
        node.setAlpha(pane.alpha)
        val canvas = node.beginRecording(right - left, bottom - top)
        // The outline is on the window, the scene on the backdrop.
        canvas.translate(-(left + ox).toFloat(), -(top + oy).toFloat())
        canvas.clipPath(pane.outline)
        canvas.translate(ox.toFloat(), oy.toFloat())
        canvas.drawRenderNode(glassNode)
        node.endRecording()
        return node
    }
}

private data class BackdropPalette(
    val ground: Color,
    val body: Color,
    val light: Color,
    val dark: Color,
    val darkTheme: Boolean
)

/**
 * What a layer paints its shapes with. The gradients are made once per palette, laid along a unit
 * line or circle, and on each frame the canvas is moved to where they fall on the shape instead:
 * the shapes are redrawn sixty times a second, and brushes, shaders and strokes made afresh on
 * every one of those frames kept the garbage collector busy for nothing. What reaches the screen
 * is the same — the same gradients between the same points, over the same paths.
 */
private class BackdropPaints(shapes: List<BackdropShape>, indices: List<Int>, palette: BackdropPalette) {
    // From (0, 0) to (1, 0): lit face, body, shaded face.
    val body = arrayOfNulls<android.graphics.Shader>(shapes.size)
    // Radius 1 around (0, 0): the soft highlight.
    val highlight = arrayOfNulls<android.graphics.Shader>(shapes.size)
    // From (0, 0) to (1, 0): the rim of light, gone by the middle.
    val rim = arrayOfNulls<android.graphics.Shader>(shapes.size)

    // Compose's own defaults for a drawn path: antialiased, bitmap filtering, butt caps, mitre joins.
    val fill = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    val stroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.BUTT
        strokeJoin = android.graphics.Paint.Join.MITER
        strokeMiter = 4f
    }
    val shadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
        color = Color.Black.copy(alpha = if (palette.darkTheme) 0.38f else 0.12f).toArgb()
    }

    // Scratch objects, reused on every frame.
    val work = android.graphics.Path()
    val local = android.graphics.Path()
    val matrix = android.graphics.Matrix()
    val toScreen = android.graphics.Matrix()
    val toLocal = android.graphics.Matrix()

    init {
        val clamp = android.graphics.Shader.TileMode.CLAMP
        val transparent = Color.Transparent.toArgb()
        for (i in indices) {
            val depth = shapes[i].depth
            // Far shapes stay close to the ground, near ones stand out of it.
            val bodyColor = lerp(palette.ground, palette.body, 0.45f + 0.55f * depth)
            val lit = lerp(bodyColor, palette.light, if (palette.darkTheme) 0.22f else 0.5f)
            val shaded = lerp(bodyColor, palette.dark, if (palette.darkTheme) 0.3f else 0.16f)
            body[i] = android.graphics.LinearGradient(
                0f, 0f, 1f, 0f,
                intArrayOf(lit.toArgb(), bodyColor.toArgb(), shaded.toArgb()), null, clamp
            )
            highlight[i] = android.graphics.RadialGradient(
                0f, 0f, 1f,
                intArrayOf(Color.White.copy(alpha = 0.07f * (0.4f + depth)).toArgb(), transparent), null, clamp
            )
            rim[i] = android.graphics.LinearGradient(
                0f, 0f, 1f, 0f,
                intArrayOf(lit.copy(alpha = 0.7f).toArgb(), transparent), null, clamp
            )
        }
    }
}

/**
 * The shapes at [indices] onto [canvas], at their places on a screen [fullWidth] by [fullHeight],
 * each lit from the light's side: a shadow thrown away from it, the lit face to the shaded one, a
 * soft highlight and a rim of light along the lit edge.
 */
private fun drawBackdropShapes(
    canvas: android.graphics.Canvas,
    shapes: List<BackdropShape>,
    indices: List<Int>,
    motion: BackdropMotion,
    paints: BackdropPaints,
    fullWidth: Float,
    fullHeight: Float,
    density: Float
) {
    val unit = min(fullWidth, fullHeight)
    val lightX = motion.lightX
    val lightY = motion.lightY
    val work = paints.work
    indices.forEach { i ->
        val shape = shapes[i]
        val diameter = shape.size * unit
        val centerX = shape.cx * fullWidth + motion.x[i]
        val centerY = shape.cy * fullHeight + motion.y[i] +
            sin(motion.time * 0.35f + i * 1.7f) * 6f * density * shape.depth
        buildShapePath(shape, motion, i, diameter, centerX, centerY, work, paints.matrix)
        val radius = diameter / 2f

        // A shadow thrown away from the light; nearer shapes float higher, so theirs falls
        // further. Drawn hard: the layer's blur is what softens it.
        val distance = (8f + 20f * shape.depth) * density
        canvas.save()
        canvas.translate(-lightX * distance, -lightY * distance)
        canvas.drawPath(work, paints.shadow)
        canvas.restore()

        // Lit face to shaded, across the whole shape from the light's side.
        paints.fill.shader = paints.body[i]
        drawAlong(
            canvas, paints, paints.fill,
            fromX = centerX + lightX * radius, fromY = centerY + lightY * radius,
            toX = centerX - lightX * radius, toY = centerY - lightY * radius
        )

        // A soft highlight toward the light, inside the shape.
        paints.fill.shader = paints.highlight[i]
        val glow = radius * 0.85f
        canvas.save()
        canvas.clipPath(work)
        canvas.translate(centerX + lightX * (radius * 0.45f), centerY + lightY * (radius * 0.45f))
        canvas.scale(glow, glow)
        canvas.drawCircle(0f, 0f, 1f, paints.fill)
        canvas.restore()

        // A rim of light along the lit edge.
        paints.stroke.shader = paints.rim[i]
        drawAlong(
            canvas, paints, paints.stroke,
            fromX = centerX + lightX * radius, fromY = centerY + lightY * radius,
            toX = centerX, toY = centerY,
            strokeWidth = 1.5f * density
        )
    }
}

/**
 * Draws [BackdropPaints.work] with [paint], whose shader runs from (0, 0) to (1, 0), laid from
 * (fromX, fromY) to (toX, toY): the canvas is turned and scaled onto that line, and the path,
 * moved the opposite way, lands where it was. A stroke is thinned by the scale to keep its width.
 */
private fun drawAlong(
    canvas: android.graphics.Canvas,
    paints: BackdropPaints,
    paint: android.graphics.Paint,
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    strokeWidth: Float = 0f
) {
    val dx = toX - fromX
    val dy = toY - fromY
    val length = sqrt(dx * dx + dy * dy)
    if (length <= 0f) return
    val toScreen = paints.toScreen
    toScreen.setScale(length, length)
    toScreen.postRotate(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat())
    toScreen.postTranslate(fromX, fromY)
    if (!toScreen.invert(paints.toLocal)) return
    paints.work.transform(paints.toLocal, paints.local)
    if (strokeWidth > 0f) paint.strokeWidth = strokeWidth / length
    canvas.save()
    canvas.concat(toScreen)
    canvas.drawPath(paints.local, paint)
    canvas.restore()
}

private fun buildShapePath(
    shape: BackdropShape,
    motion: BackdropMotion,
    index: Int,
    diameter: Float,
    centerX: Float,
    centerY: Float,
    work: android.graphics.Path,
    matrix: android.graphics.Matrix
) {
    work.rewind()
    val morph = shape.morph
    if (morph != null && shape.morphSeconds > 0f) {
        // Eased back and forth, lingering at each end so both shapes can be read.
        val phase = (motion.time / shape.morphSeconds) * 2f * PI.toFloat()
        val progress = (1f - cos(phase)) / 2f
        morph.toPath(progress, work)
    } else {
        work.set(shape.basePath)
    }
    // MaterialShapes are normalised into the unit square.
    matrix.setTranslate(-0.5f, -0.5f)
    matrix.postScale(diameter, diameter)
    matrix.postRotate(motion.angle[index] + motion.wobble[index])
    matrix.postTranslate(centerX, centerY)
    work.transform(matrix)
}

/** How far the nearest shape slides at full tilt. */
private const val PARALLAX_SHIFT_DP = 34f

/** Shapes from this depth on are drawn in the sharper, near layer. */
private const val NEAR_DEPTH = 0.6f
private val FAR_BLUR = 8.dp
private val NEAR_BLUR = 3.dp
private const val FAR_SHRINK = 4
private const val NEAR_SHRINK = 2
// As small as frosted makes what it blurs: under glass the scene is all blur.
private const val GLASS_SHRINK = 8

/**
 * Which frame of a grid of [slotNanos] the frame at [frameNanos] falls in. What moves by itself —
 * the backdrop, the wave's shape, the looping clocks (the playing bars, the chevron under home's
 * carousel) — moves on when its slot changes, and so all of it on the same frames of the screen.
 * Each counting from its own last frame instead, two things at sixty a second landed on different
 * frames of a 120 Hz screen and between them drew on every one: the playing bars on a cover with
 * the mini player's wave under it, 125 frames a second where 63 do.
 */
internal fun frameSlot(frameNanos: Long, slotNanos: Long): Long =
    if (slotNanos <= 0L) frameNanos else frameNanos / slotNanos

/**
 * Sleeps until shortly before the frame of the screen on which the grid of [slotNanos] moves on
 * (see [frameSlot]), the last frame having been at [lastNanos]. A loop that asked for every frame
 * of a 120 Hz screen, to draw on every fourth, was woken four times for each — a frame of Compose's
 * every time: most of what home cost the main thread while nothing on it moved. None to wait for
 * while what moves is drawn on every frame.
 */
internal suspend fun sleepToSlot(lastNanos: Long, slotNanos: Long) {
    if (slotNanos <= 0L || lastNanos <= 0L) return
    val next = (lastNanos / slotNanos + 1) * slotNanos
    val wait = next - System.nanoTime() - SLOT_WAKE_EARLY_NANOS
    if (wait > 0L) kotlinx.coroutines.delay(wait / 1_000_000L)
}

// Woken this much ahead of a slot's first frame, for the frame not to be missed.
private const val SLOT_WAKE_EARLY_NANOS = 6_000_000L

// Glass still counts as moving this long after it last told so: every frame is watched meanwhile.
private const val GLASS_QUIET_NANOS = 120_000_000L
// A frame of the screen, from 120 Hz to 60, for when part of one is waited.
private const val MIN_FRAME_NANOS = 8_000_000L
private const val MAX_FRAME_NANOS = 17_000_000L

// Sixty a second; and thirty, on every other of the same frames.
internal const val SLOT_FAST_NANOS = 16_666_666L
internal const val SLOT_CALM_NANOS = 2 * SLOT_FAST_NANOS

/**
 * How often what moves by itself is drawn — the backdrop, the wave's shape, the looping clocks —
 * as "Плавность 120 Гц" in the settings has it ([smooth]; set by the screen from the setting).
 *
 * Smooth, what moves for all to see (shaken, tilted, turning while the wave plays, the playing
 * bars) is drawn on every frame of the screen, 120 a second where it has them; what only drifts,
 * sixty — under a pixel a frame and blurred, it would redraw home 120 times a second while it sat
 * still. Otherwise sixty and thirty, as before the setting: lighter on a weak phone.
 */
internal object MotionPace {
    @Volatile
    var smooth: Boolean = true

    /** The grid (see [frameSlot]) for something that moves [lively], or only drifts. */
    fun slotNanos(lively: Boolean): Long = when {
        smooth && lively -> 0L
        smooth || lively -> SLOT_FAST_NANOS
        else -> SLOT_CALM_NANOS
    }
}
