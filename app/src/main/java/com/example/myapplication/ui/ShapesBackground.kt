@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.myapplication.ui

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.PI
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

    fun step(dt: Float, shapes: List<BackdropShape>, tilt: TiltSensor, shiftPx: Float) {
        time += dt
        val (kickX, kickY, kick) = tilt.takeImpulse()
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
        }
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
    private var kickX = 0f
    private var kickY = 0f
    private var kick = 0f

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

    /** Shakes since the last frame, consumed by it. */
    fun takeImpulse(): Triple<Float, Float, Float> {
        val result = Triple(kickX, kickY, kick)
        kickX = 0f; kickY = 0f; kick = 0f
        return result
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

/** Listens to the accelerometer while [enabled] and the screen is resumed, and not otherwise. */
@Composable
private fun rememberTiltSensor(enabled: Boolean): TiltSensor {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tilt = remember { TiltSensor() }
    DisposableEffect(enabled, lifecycleOwner) {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (!enabled || manager == null || sensor == null) {
            tilt.reset()
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    manager.registerListener(tilt, sensor, SensorManager.SENSOR_DELAY_GAME)
                Lifecycle.Event.ON_PAUSE -> {
                    manager.unregisterListener(tilt)
                    tilt.reset()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            manager.unregisterListener(tilt)
            tilt.reset()
        }
    }
    return tilt
}

/**
 * The backdrop. [motionEnabled] ties the shapes to the accelerometer; [animated] false freezes
 * the scene (nothing is redrawn per frame then).
 */
@Composable
internal fun ExpressiveBackground(motionEnabled: Boolean, animated: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    val darkTheme = scheme.background.luminance() < 0.5f
    val shapes = remember { backdropShapes().sortedBy { it.depth } }
    val motion = remember { BackdropMotion(shapes.size) }
    val tilt = rememberTiltSensor(enabled = motionEnabled && animated)
    val shiftPx = with(LocalDensity.current) { PARALLAX_SHIFT_DP * density }
    val frame = remember { mutableLongStateOf(0L) }

    LaunchedEffect(animated) {
        if (!animated) return@LaunchedEffect
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
                last = now
                motion.step(dt, shapes, tilt, shiftPx)
                frame.longValue = now
            }
        }
    }

    // A ground a step lighter than the app's background, so the shapes and the covers in front of
    // them have something to stand off from instead of all sinking into the same near-black.
    val ground = if (darkTheme) scheme.surfaceContainer else scheme.surfaceContainerLow
    // One family of tones — the secondary palette the launcher's widgets and folders use — so the
    // scene reads as one material at different depths rather than a handful of coloured stickers.
    val palette = BackdropPalette(
        ground = ground,
        body = scheme.secondaryContainer,
        light = if (darkTheme) scheme.secondary else Color.White,
        dark = if (darkTheme) Color.Black else scheme.secondary,
        darkTheme = darkTheme
    )
    val far = remember(shapes) { shapes.indices.filter { shapes[it].depth < NEAR_DEPTH } }
    val near = remember(shapes) { shapes.indices.filter { shapes[it].depth >= NEAR_DEPTH } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ground)
    ) {
        // Depth of field: the far shapes are the softest, the near ones less so, and the content in
        // front is the only thing in focus — which is what separates a cover from the backdrop.
        BackdropLayer(shapes, far, motion, frame, palette, blur = FAR_BLUR)
        BackdropLayer(shapes, near, motion, frame, palette, blur = NEAR_BLUR)
    }
}

private class BackdropPalette(
    val ground: Color,
    val body: Color,
    val light: Color,
    val dark: Color,
    val darkTheme: Boolean
)

@Composable
private fun BackdropLayer(
    shapes: List<BackdropShape>,
    indices: List<Int>,
    motion: BackdropMotion,
    frame: androidx.compose.runtime.MutableLongState,
    palette: BackdropPalette,
    blur: Dp
) {
    val work = remember { android.graphics.Path() }
    val matrix = remember { android.graphics.Matrix() }
    val shadowPaint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .blur(blur, BlurredEdgeTreatment.Unbounded)
    ) {
        // Read so that every frame of the simulation redraws, without recomposing anything.
        frame.longValue
        val unit = min(size.width, size.height)
        val light = Offset(motion.lightX, motion.lightY)
        indices.forEach { i ->
            val shape = shapes[i]
            val diameter = shape.size * unit
            val center = Offset(
                shape.cx * size.width + motion.x[i],
                shape.cy * size.height + motion.y[i] + sin(motion.time * 0.35f + i * 1.7f) * 6f * density * shape.depth
            )
            buildShapePath(shape, motion, i, diameter, center, work, matrix)
            val path = work.asComposePath()

            // Far shapes stay close to the ground, near ones stand out of it.
            val body = lerp(palette.ground, palette.body, 0.45f + 0.55f * shape.depth)
            val lit = lerp(body, palette.light, if (palette.darkTheme) 0.22f else 0.5f)
            val shaded = lerp(body, palette.dark, if (palette.darkTheme) 0.3f else 0.16f)
            val radius = diameter / 2f

            drawCastShadow(work, light, shape.depth, shadowPaint, palette.darkTheme)
            drawPath(
                path,
                Brush.linearGradient(
                    colors = listOf(lit, body, shaded),
                    start = center + light * radius,
                    end = center - light * radius
                )
            )
            clipPath(path) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.07f * (0.4f + shape.depth)), Color.Transparent),
                        center = center + light * (radius * 0.45f),
                        radius = radius * 0.85f
                    ),
                    radius = radius * 0.85f,
                    center = center + light * (radius * 0.45f)
                )
            }
            drawPath(
                path,
                Brush.linearGradient(
                    colors = listOf(lit.copy(alpha = 0.7f), Color.Transparent),
                    start = center + light * radius,
                    end = center
                ),
                style = Stroke(width = 1.5f * density)
            )
        }
    }
}

private fun buildShapePath(
    shape: BackdropShape,
    motion: BackdropMotion,
    index: Int,
    diameter: Float,
    center: Offset,
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
    matrix.postTranslate(center.x, center.y)
    work.transform(matrix)
}

/**
 * A shadow thrown away from the light; nearer shapes float higher, so theirs falls further. Drawn
 * hard: the layer's blur is what softens it.
 */
private fun DrawScope.drawCastShadow(
    path: android.graphics.Path,
    light: Offset,
    depth: Float,
    paint: android.graphics.Paint,
    darkTheme: Boolean
) {
    val distance = (8f + 20f * depth) * density
    paint.color = Color.Black.copy(alpha = if (darkTheme) 0.38f else 0.12f).toArgb()
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        native.translate(-light.x * distance, -light.y * distance)
        native.drawPath(path, paint)
        native.restore()
    }
}

/** How far the nearest shape slides at full tilt. */
private const val PARALLAX_SHIFT_DP = 34f

/** Shapes from this depth on are drawn in the sharper, near layer. */
private const val NEAR_DEPTH = 0.6f
private val FAR_BLUR = 8.dp
private val NEAR_BLUR = 3.dp
