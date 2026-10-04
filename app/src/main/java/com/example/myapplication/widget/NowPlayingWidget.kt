package com.example.myapplication.widget

import com.example.myapplication.i18n.tr
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.ImageProvider
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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
 * - two cells by one: the cover and play, side by side and as big;
 * - two by two: the cover, play on its corner as the profile's camera button is on the avatar;
 * - four by one: the cover, the track and play, play as big as the cover;
 * - four by two: the cover and the track under it beside the buttons, three rows of them — play
 *   across, back and next side by side, "Моя волна" across.
 * Nothing playing, it offers the wave and "Моя музыка". The cover is cut to the cookie the app's
 * avatars are (Material 3 Expressive's shapes), play is a rounded square.
 *
 * The playback service tells it what plays ([NowPlayingState]); its buttons reach the service
 * through a [MediaController], as the notification's do.
 */
class NowPlayingWidget : GlanceAppWidget() {
    // Laid out for the size it really is: a square cover needs the height.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // What plays as it is now, and its cover (an address the launcher reads, see
            // WidgetCover): not followed here, but drawn again for each change (see WidgetRenderer).
            val state = NowPlayingState.flow(context).value
            val cover = WidgetCover.flow.value
            GlanceTheme {
                Content(context, state, cover?.takeIf { state.active })
            }
        }
    }

    @Composable
    private fun Content(context: Context, state: NowPlayingState.Snapshot, cover: Uri?) {
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

    /**
     * Four by two: on the left the cover, the track under it; on the right the buttons, as keys in
     * three rows — play across, back and next a half each, "Моя волна" across. Nothing playing:
     * the wave across, "Моя музыка" and search under it.
     */
    @Composable
    private fun Large(context: Context, state: NowPlayingState.Snapshot, cover: Uri?, frame: GlanceModifier) {
        val size = LocalSize.current
        val inner = size.height - PAD * 2
        val half = (size.width - PAD * 2 - COLUMN_GAP) / 2
        Row(modifier = frame.padding(PAD), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.width(half).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Cover(context, cover, minOf(half, inner - TITLES))
                Spacer(modifier = GlanceModifier.height(6.dp))
                Titles(context, state, big = false)
            }
            Spacer(modifier = GlanceModifier.width(COLUMN_GAP))
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                // The wave's word where it fits beside its mark.
                val waveText = tr("Моя волна").takeIf { half >= 140.dp }
                if (state.active) {
                    val row = (inner - KEY_GAP * 2) / 3
                    Key(
                        icon = if (state.playing) R.drawable.ic_glyph_pause else R.drawable.ic_glyph_play,
                        label = if (state.playing) tr("Пауза") else tr("Играть"),
                        onClick = command(COMMAND_TOGGLE),
                        height = row,
                        accent = true,
                        modifier = GlanceModifier.fillMaxWidth()
                    )
                    Spacer(modifier = GlanceModifier.height(KEY_GAP))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Key(R.drawable.ic_glyph_skip_previous, tr("Предыдущий"), command(COMMAND_PREVIOUS), row, modifier = GlanceModifier.defaultWeight())
                        Spacer(modifier = GlanceModifier.width(KEY_GAP))
                        Key(R.drawable.ic_glyph_skip_next, tr("Следующий"), command(COMMAND_NEXT), row, modifier = GlanceModifier.defaultWeight())
                    }
                    Spacer(modifier = GlanceModifier.height(KEY_GAP))
                    Key(
                        R.drawable.ic_glyph_wave, tr("Моя волна"), open(context, MainActivity.ACTION_WAVE), row,
                        text = waveText, modifier = GlanceModifier.fillMaxWidth()
                    )
                } else {
                    val row = (inner - KEY_GAP) / 2
                    Key(
                        R.drawable.ic_glyph_wave, tr("Моя волна"), open(context, MainActivity.ACTION_WAVE), row,
                        accent = true, text = waveText, modifier = GlanceModifier.fillMaxWidth()
                    )
                    Spacer(modifier = GlanceModifier.height(KEY_GAP))
                    Row(modifier = GlanceModifier.fillMaxWidth()) {
                        Key(R.drawable.ic_glyph_library, tr("Моя музыка"), open(context, MainActivity.ACTION_MY_MUSIC), row, modifier = GlanceModifier.defaultWeight())
                        Spacer(modifier = GlanceModifier.width(KEY_GAP))
                        Key(R.drawable.ic_glyph_search, tr("Поиск"), open(context, MainActivity.ACTION_SEARCH), row, modifier = GlanceModifier.defaultWeight())
                    }
                }
            }
        }
    }

    /**
     * A key of the large widget: a rounded rectangle [height] tall, as wide as [modifier] makes it,
     * its mark (and [text]) in the middle; the accent's for play, the panel's tone for the rest.
     */
    @Composable
    private fun Key(
        icon: Int,
        label: String,
        onClick: Action,
        height: Dp,
        accent: Boolean = false,
        text: String? = null,
        modifier: GlanceModifier = GlanceModifier
    ) {
        val content = if (accent) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSecondaryContainer
        Box(
            modifier = modifier
                .height(height)
                .cornerRadius(height * ACCENT_CORNER)
                .background(if (accent) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer)
                .clickable(onClick),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    provider = ImageProvider(icon),
                    contentDescription = label,
                    colorFilter = ColorFilter.tint(content),
                    modifier = GlanceModifier.size(minOf(height * 0.46f, 28.dp))
                )
                if (text != null) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(text, style = TextStyle(color = content, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
    }

    /** Four by one: everything in a row. */
    @Composable
    private fun Strip(context: Context, state: NowPlayingState.Snapshot, cover: Uri?, frame: GlanceModifier) {
        val side = LocalSize.current.height - STRIP_PAD * 2
        Row(modifier = frame.padding(STRIP_PAD), verticalAlignment = Alignment.CenterVertically) {
            Cover(context, cover, side)
            Spacer(modifier = GlanceModifier.width(12.dp))
            Titles(context, state, big = false, modifier = GlanceModifier.defaultWeight())
            Spacer(modifier = GlanceModifier.width(12.dp))
            if (state.active) Play(state, size = side) else Wave(context, size = side)
        }
    }

    /**
     * Two by two: the cover over most of it, play sitting on its corner as the profile's camera
     * button sits on the avatar — set off from it by a rim of the widget's own colour.
     */
    @Composable
    private fun Square(context: Context, state: NowPlayingState.Snapshot, cover: Uri?, frame: GlanceModifier) {
        val size = LocalSize.current
        val side = minOf(size.width, size.height) - SQUARE_PAD * 2
        Box(modifier = frame, contentAlignment = Alignment.BottomEnd) {
            Box(modifier = GlanceModifier.fillMaxSize().padding(SQUARE_PAD), contentAlignment = Alignment.Center) {
                Cover(context, cover, side)
            }
            // A third of the cover, as the camera button is of the avatar.
            val button = side * 0.36f
            Box(
                modifier = GlanceModifier
                    .padding(4.dp)
                    .cornerRadius(button * ACCENT_CORNER + 4.dp)
                    .background(GlanceTheme.colors.widgetBackground)
                    .padding(4.dp)
            ) {
                if (state.active) Play(state, size = button) else Wave(context, size = button)
            }
        }
    }

    /** Two by one: the cover and play, side by side, each a square as tall as the widget lets it. */
    @Composable
    private fun Small(context: Context, state: NowPlayingState.Snapshot, cover: Uri?, frame: GlanceModifier) {
        val size = LocalSize.current
        val side = minOf(size.height - STRIP_PAD * 2, (size.width - STRIP_PAD * 2 - 8.dp) / 2)
        Row(modifier = frame.padding(STRIP_PAD), verticalAlignment = Alignment.CenterVertically) {
            Cover(context, cover, side)
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (state.active) Play(state, size = side) else Wave(context, size = side)
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

    /** The cover, cut to the cookie (see WidgetCover); the app's notes on a cookie of the panel's tone while there is none. */
    @Composable
    private fun Cover(context: Context, cover: Uri?, side: Dp) {
        if (cover != null) {
            Image(
                provider = ImageProvider(cover),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(side).clickable(openApp(context))
            )
        } else {
            Box(modifier = GlanceModifier.clickable(openApp(context))) { Glyph(context, side) }
        }
    }

    @Composable
    private fun Glyph(context: Context, side: Dp) {
        Box(modifier = GlanceModifier.size(side), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(WidgetCover.mask(context)),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer),
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxSize()
            )
            Image(
                provider = ImageProvider(R.drawable.ic_glyph_library),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
                modifier = GlanceModifier.size(side * 0.4f)
            )
        }
    }

    @Composable
    private fun Play(state: NowPlayingState.Snapshot, size: Dp = 56.dp) {
        Accent(
            icon = if (state.playing) R.drawable.ic_glyph_pause else R.drawable.ic_glyph_play,
            label = if (state.playing) tr("Пауза") else tr("Играть"),
            onClick = command(COMMAND_TOGGLE),
            size = size
        )
    }

    /**
     * A rounded square on the accent, as big as it is given — on the smaller widgets as big as the
     * cover — its corners and its mark in proportion: Glance's own keeps its mark one size, a dot
     * on a big button.
     */
    @Composable
    private fun Accent(icon: Int, label: String, onClick: Action, size: Dp) {
        Box(
            modifier = GlanceModifier
                .size(size)
                .cornerRadius(size * ACCENT_CORNER)
                .background(GlanceTheme.colors.primary)
                .clickable(onClick),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = label,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(size * 0.42f)
            )
        }
    }

    /** "Моя волна", the accent one: what the widget offers while nothing plays. */
    @Composable
    private fun Wave(context: Context, size: Dp) {
        Accent(R.drawable.ic_glyph_wave, tr("Моя волна"), open(context, MainActivity.ACTION_WAVE), size)
    }

    private fun openApp(context: Context): Action = actionStartActivity(Intent(context, MainActivity::class.java))

    private fun open(context: Context, action: String): Action =
        actionStartActivity(Intent(context, MainActivity::class.java).setAction(action))

    private fun command(name: String) = actionRunCallback<MediaCommand>(actionParametersOf(CommandKey to name))

    companion object {
        // From this wide the track and its buttons fit beside the cover; from this tall, above them.
        private val WIDE_MIN = 220.dp
        private val TALL_MIN = 140.dp
        private val PAD = 14.dp
        private val STRIP_PAD = 10.dp
        // The large widget's: between its two halves, between its keys, the track's two lines.
        private val COLUMN_GAP = 12.dp
        private val KEY_GAP = 6.dp
        private val TITLES = 44.dp
        private val SQUARE_PAD = 10.dp
        // The accent buttons' corners, as a part of their side: the camera button's on the avatar.
        private const val ACCENT_CORNER = 0.3f

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

    // What the system asks for is drawn as a change is, once (see WidgetRenderer), rather than by
    // a session of Glance's left to follow what plays.
    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        WidgetRenderer.render(context, delayMs = 0) { pending.finish() }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        val pending = goAsync()
        WidgetRenderer.render(context) { pending.finish() }
    }
}
