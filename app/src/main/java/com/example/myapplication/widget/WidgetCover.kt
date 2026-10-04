package com.example.myapplication.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.core.graphics.drawable.toBitmap
import androidx.graphics.shapes.toPath
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.myapplication.data.ArtworkProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * The pictures the widgets show, as files the launcher reads itself ([ArtworkProvider]) rather
 * than pictures inside the widget: a widget carries a copy of each for every size the launcher
 * may draw it at, and with the covers in them — megabytes for four widgets — the system sent the
 * widgets again and again for seconds after each change, and their buttons answered late, if at
 * all.
 *
 * The cover is made once a track, for every widget: cut to the cookie the app's avatars are
 * (Material 3 Expressive's shapes), as a widget's picture can't be clipped, only drawn so.
 */
internal object WidgetCover {
    private const val COVER_PX = 400
    private const val MASK = "cookie"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cover = MutableStateFlow<Uri?>(null)
    private var artwork: String? = null
    private var making: Job? = null

    /** The cover of the track the widgets show; the last one stays until the next is ready. */
    val flow: StateFlow<Uri?> = cover.asStateFlow()

    /** Follows [track]'s cover (null: nothing plays), making it the first time it is asked for. */
    @Synchronized
    fun follow(context: Context, track: String?) {
        if (track == artwork) return
        artwork = track
        making?.cancel()
        if (track == null) {
            cover.value = null
            return
        }
        val app = context.applicationContext
        making = scope.launch {
            runCatching { make(app, track) }.getOrNull()?.let { cover.value = it }
        }
    }

    /**
     * The cookie alone, white, for the widgets to tint the panel's tone: the place of a cover not
     * there. Written once.
     */
    fun mask(context: Context): Uri {
        val file = File(ArtworkProvider.widgetDir(context), "$MASK.png")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val side = 240
            val mask = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            Canvas(mask).drawPath(cookiePath(side), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            write(mask, file)
        }
        return ArtworkProvider.widgetUri(MASK)
    }

    private suspend fun make(context: Context, track: String): Uri? {
        val request = ImageRequest.Builder(context)
            .data(track)
            .size(COVER_PX)
            .allowHardware(false)
            .build()
        val loaded = (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap(COVER_PX, COVER_PX) ?: return null
        // A name of its own a track, so the launcher, which keeps what it last read for a name,
        // reads the new one.
        val name = "cover-" + Integer.toHexString(track.hashCode())
        val dir = ArtworkProvider.widgetDir(context).apply { mkdirs() }
        write(cookie(loaded), File(dir, "$name.png"))
        // The covers before it are of no more use — but the last one, which the launcher may still
        // be reading until the widgets with the new one reach it; the mask stays.
        dir.listFiles { file -> file.name.startsWith("cover-") && file.name.endsWith(".png") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(2)
            ?.forEach { it.delete() }
        return ArtworkProvider.widgetUri(name)
    }

    /** [source] cut to the cookie, its edge smooth: the shape painted, then the cover into it. */
    private fun cookie(source: Bitmap): Bitmap {
        val side = minOf(source.width, source.height)
        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawPath(cookiePath(side), paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, null, Rect(0, 0, side, side), paint)
        return out
    }

    /** The cookie of [side] pixels: Material 3 Expressive's shape, given in a unit square. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun cookiePath(side: Int): Path =
        MaterialShapes.Cookie12Sided.toPath().apply {
            transform(Matrix().apply { setScale(side.toFloat(), side.toFloat()) })
        }

    // Written whole, then put in place: the launcher may be reading the name meanwhile.
    private fun write(bitmap: Bitmap, file: File) {
        val part = File(file.parentFile, file.name + ".part")
        part.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        part.renameTo(file)
    }
}
