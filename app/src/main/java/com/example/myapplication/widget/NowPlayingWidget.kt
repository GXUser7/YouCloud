package com.example.myapplication.widget

import com.example.myapplication.i18n.tr
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.SquareIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * What plays, on the home screen, in the wallpaper's colours as Android's own widgets are, laid
 * out for the size it is given:
 * - two cells by one: the cover and play;
 * - two by two: the cover over all of it, play in its corner;
 * - four by one: the cover, the track, play and next;
 * - four by two: the cover and the track over all the buttons and "Моя волна".
 * Nothing playing, it offers the wave and "Моя музыка". The cover is always square.
 *
 * The playback service tells it what plays ([NowPlayingState]); its buttons reach the service
 * through a [MediaController], as the notification's do.
 */
class NowPlayingWidget : GlanceAppWidget() {
    // Laid out for the size it really is: a square cover needs the height.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val states = NowPlayingState.flow(context)
        provideContent {
            // Followed while the session runs, the cover with it.
            val state by states.collectAsState()
            var cover by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(state.artwork, state.active) {
                cover = state.artwork?.takeIf { state.active }?.let { cover(context, it) }
            }
            GlanceTheme {
                Content(context, state, cover)
            }
        }
    }

    @Composable
    private fun Content(context: Context, state: NowPlayingState.Snapshot, cover: Bitmap?) {
        val size = LocalSize.current
        val wide = size.width >= WIDE_MIN
        val tall = size.height >= TALL_MIN
        val frame = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp)
        when {
            wide && tall -> Large(context, state, cover, frame)
            wide -> Strip(context, state, cover, frame)
            tall -> Square(context, state, cover, frame)
            else -> Small(context, state, cover, frame)
        }
    }

    /** Four by two: the cover and the track above, the buttons spread out below. */
    @Composable
    private fun Large(context: Context, state: NowPlayingState.Snapshot, cover: Bitmap?, frame: GlanceModifier) {
        val size = LocalSize.current
        val side = minOf(size.height - PAD * 2 - BUTTONS - GAP, size.width * 0.45f)
        Column(modifier = frame.padding(PAD)) {
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                Cover(context, cover, side)
                Spacer(modifier = GlanceModifier.width(14.dp))
                Titles(context, state, big = true, modifier = GlanceModifier.defaultWeight())
            }
            Spacer(modifier = GlanceModifier.height(GAP))
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (state.active) {
                    Round(R.drawable.ic_glyph_skip_previous, tr("Предыдущий"), command(COMMAND_PREVIOUS))
                    Spread()
                    Play(state)
                    Spread()
                    Round(R.drawable.ic_glyph_skip_next, tr("Следующий"), command(COMMAND_NEXT))
                    Spread()
                    Round(R.drawable.ic_glyph_wave, tr("Моя волна"), open(context, MainActivity.ACTION_WAVE))
                } else {
                    FilledButton(
                        text = tr("Моя волна"),
                        onClick = open(context, MainActivity.ACTION_WAVE),
                        icon = ImageProvider(R.drawable.ic_glyph_wave),
                        modifier = GlanceModifier.defaultWeight()
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Round(R.drawable.ic_glyph_library, tr("Моя музыка"), open(context, MainActivity.ACTION_MY_MUSIC))
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Round(R.drawable.ic_glyph_search, tr("Поиск"), open(context, MainActivity.ACTION_SEARCH))
                }
            }
        }
    }

    /** Four by one: everything in a row. */
    @Composable
    private fun Strip(context: Context, state: NowPlayingState.Snapshot, cover: Bitmap?, frame: GlanceModifier) {
        val side = LocalSize.current.height - STRIP_PAD * 2
        Row(modifier = frame.padding(STRIP_PAD), verticalAlignment = Alignment.CenterVertically) {
            Cover(context, cover, side, corner = 16.dp)
            Spacer(modifier = GlanceModifier.width(12.dp))
            Titles(context, state, big = false, modifier = GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(8.dp))
            if (state.active) {
                Play(state, size = 48.dp)
                Spacer(modifier = GlanceModifier.width(6.dp))
                Round(R.drawable.ic_glyph_skip_next, tr("Следующий"), command(COMMAND_NEXT), size = 42.dp)
            } else {
                Wave(context, size = 48.dp)
            }
        }
    }

    /** Two by two: the cover over all of it, play in its corner. */
    @Composable
    private fun Square(context: Context, state: NowPlayingState.Snapshot, cover: Bitmap?, frame: GlanceModifier) {
        Box(modifier = frame, contentAlignment = Alignment.BottomEnd) {
            if (cover != null) {
                Image(
                    provider = ImageProvider(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(28.dp).clickable(openApp(context))
                )
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(PAD).clickable(openApp(context)),
                    verticalAlignment = Alignment.Top
                ) {
                    Glyph(40.dp)
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    Titles(context, state, big = false)
                }
            }
            Box(modifier = GlanceModifier.padding(10.dp)) {
                if (state.active) Play(state, size = 52.dp) else Wave(context, size = 52.dp)
            }
        }
    }

    /** Two by one: the cover and play. */
    @Composable
    private fun Small(context: Context, state: NowPlayingState.Snapshot, cover: Bitmap?, frame: GlanceModifier) {
        val side = LocalSize.current.height - STRIP_PAD * 2
        Row(modifier = frame.padding(STRIP_PAD), verticalAlignment = Alignment.CenterVertically) {
            Cover(context, cover, side, corner = 16.dp)
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (state.active) Play(state, size = 48.dp) else Wave(context, size = 48.dp)
            Spacer(modifier = GlanceModifier.width(4.dp))
        }
    }

    @Composable
    private fun Titles(context: Context, state: NowPlayingState.Snapshot, big: Boolean, modifier: GlanceModifier = GlanceModifier) {
        Column(modifier = modifier.clickable(openApp(context))) {
            Text(
                text = if (state.active) state.title ?: "YouCloud" else "YouCloud",
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = if (big) 17.sp else 15.sp, fontWeight = FontWeight.Bold),
                maxLines = if (big) 2 else 1
            )
            Text(
                text = if (state.active) state.artist.orEmpty() else tr("Ничего не играет"),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = if (big) 14.sp else 13.sp),
                maxLines = 1
            )
        }
    }

    /** The cover, square; the app's notes on the panel's tone while nothing plays. */
    @Composable
    private fun Cover(context: Context, cover: Bitmap?, side: Dp, corner: Dp = 20.dp) {
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.size(side).cornerRadius(corner).clickable(openApp(context))
            )
        } else {
            Box(modifier = GlanceModifier.clickable(openApp(context))) { Glyph(side, corner) }
        }
    }

    @Composable
    private fun Glyph(side: Dp, corner: Dp = 16.dp) {
        Box(
            modifier = GlanceModifier.size(side).cornerRadius(corner).background(GlanceTheme.colors.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_glyph_library),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(side * 0.45f)
            )
        }
    }

    @Composable
    private fun Play(state: NowPlayingState.Snapshot, size: Dp = 56.dp) {
        SquareIconButton(
            imageProvider = ImageProvider(if (state.playing) R.drawable.ic_glyph_pause else R.drawable.ic_glyph_play),
            contentDescription = if (state.playing) tr("Пауза") else tr("Играть"),
            onClick = command(COMMAND_TOGGLE),
            backgroundColor = GlanceTheme.colors.primary,
            contentColor = GlanceTheme.colors.onPrimary,
            modifier = GlanceModifier.size(size)
        )
    }

    @Composable
    private fun Round(icon: Int, label: String, onClick: Action, size: Dp = 46.dp) {
        CircleIconButton(
            imageProvider = ImageProvider(icon),
            contentDescription = label,
            onClick = onClick,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
            contentColor = GlanceTheme.colors.onSecondaryContainer,
            modifier = GlanceModifier.size(size)
        )
    }

    /** "Моя волна", the accent one: what the widget offers while nothing plays. */
    @Composable
    private fun Wave(context: Context, size: Dp) {
        SquareIconButton(
            imageProvider = ImageProvider(R.drawable.ic_glyph_wave),
            contentDescription = tr("Моя волна"),
            onClick = open(context, MainActivity.ACTION_WAVE),
            backgroundColor = GlanceTheme.colors.primary,
            contentColor = GlanceTheme.colors.onPrimary,
            modifier = GlanceModifier.size(size)
        )
    }

    @Composable
    private fun RowScope.Spread() {
        Spacer(modifier = GlanceModifier.defaultWeight())
    }

    private fun openApp(context: Context): Action = actionStartActivity(Intent(context, MainActivity::class.java))

    private fun open(context: Context, action: String): Action =
        actionStartActivity(Intent(context, MainActivity::class.java).setAction(action))

    private fun command(name: String) = actionRunCallback<MediaCommand>(actionParametersOf(CommandKey to name))

    /** The cover, small: a widget's pictures go through a parcel with a size limit. */
    private suspend fun cover(context: Context, uri: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(COVER_PX)
            .allowHardware(false)
            .build()
        (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.toBitmap(COVER_PX, COVER_PX)
    }.getOrNull()

    companion object {
        // From this wide the track and its buttons fit beside the cover; from this tall, above them.
        private val WIDE_MIN = 220.dp
        private val TALL_MIN = 140.dp
        private val PAD = 14.dp
        private val STRIP_PAD = 10.dp
        private val BUTTONS = 56.dp
        private val GAP = 10.dp
        private const val COVER_PX = 360

        internal val CommandKey = ActionParameters.Key<String>("command")
        internal const val COMMAND_TOGGLE = "toggle"
        internal const val COMMAND_NEXT = "next"
        internal const val COMMAND_PREVIOUS = "previous"
    }
}

/** A widget's play, pause and skip, sent to the playback service as the notification's are. */
class MediaCommand : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[NowPlayingWidget.CommandKey] ?: return
        // The app's own context: the receiver's this runs in may not bind to a service.
        val app = context.applicationContext
        // A controller lives on the main thread.
        withContext(Dispatchers.Main) {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            val controller = MediaController.Builder(app, token).buildAsync().await()
            try {
                // The app was closed, and its queue with it: the widget offers the wave instead.
                if (controller.mediaItemCount == 0) {
                    NowPlayingState.publish(app, NowPlayingState.Snapshot())
                    return@withContext
                }
                when (command) {
                    NowPlayingWidget.COMMAND_TOGGLE -> if (controller.isPlaying) controller.pause() else controller.play()
                    NowPlayingWidget.COMMAND_NEXT -> controller.seekToNext()
                    NowPlayingWidget.COMMAND_PREVIOUS -> controller.seekToPrevious()
                }
            } finally {
                controller.release()
            }
        }
    }

    private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get())
            } catch (e: Exception) {
                continuation.resumeWithException(e.cause ?: e)
            }
        }, Runnable::run)
        continuation.invokeOnCancellation { cancel(false) }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
