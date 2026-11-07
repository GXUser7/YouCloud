package com.example.myapplication.player.cast

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.myapplication.player.AudioOutputs
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import org.json.JSONObject

/**
 * Google Cast: a TV, or a speaker with Chromecast built in, picked from the track's menu plays the
 * queue in the phone's place. The session's player becomes a [CastPlayer] (so the app, the
 * notification and the lock screen go on showing and controlling the music, on the TV now), and
 * the queue moves over from where it was; the session ending moves it back to the phone, where the
 * TV left off. What the TV plays the phone serves it ([CastStreamServer]).
 *
 * A session only suspended — the Wi-Fi gone for a moment — leaves the music with the TV, which
 * plays on: it comes back, or ends and the phone takes over, or after [SUSPENDED_MS] the phone
 * takes the queue back, paused.
 *
 * Without Google Play services (or Cast) on the phone, none of it is set up and nothing changes.
 */
@UnstableApi
internal class CastSupport(
    private val context: Context,
    private val local: ExoPlayer,
    private val session: () -> MediaSession?,
    private val server: CastStreamServer
) {
    private var cast: CastPlayer? = null
    private var castContext: CastContext? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // The TV's queue and place in it, kept as it plays: once its session has ended, the Cast
    // player has nothing left to hand back. Kept as the phone's own items, each with its address:
    // what the Cast player hands back can lack it — the track playing is an empty item when the
    // TV's status came before the queue, or after the app was started again — and the phone's
    // player given an item without an address fell over (the app closed when the TV's connection
    // dropped).
    private var lastItems: List<MediaItem> = emptyList()
    private var lastIndex = 0
    private var lastPosition = 0L
    private var lastPlaying = false
    // Every track handed to the TV, by its id, as the phone plays it.
    private val known = HashMap<String, MediaItem>()
    // The connection to the TV is broken for now; it may come back (see [attach]).
    private var suspended = false
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val snapshot = object : Runnable {
        override fun run() {
            val player = cast ?: return
            if (player.mediaItemCount > 0) {
                val items = playable((0 until player.mediaItemCount).map(player::getMediaItemAt))
                if (items != null) {
                    lastItems = items
                    lastIndex = player.currentMediaItemIndex
                } else {
                    // Some of the TV's items weren't told apart: where it is in them still is.
                    val id = player.currentMediaItem?.mediaId
                    lastItems.indexOfFirst { it.mediaId == id }.takeIf { it >= 0 }?.let { lastIndex = it }
                }
                lastPosition = player.currentPosition
                lastPlaying = player.playWhenReady
            }
            handler.postDelayed(this, 1_000)
        }
    }

    // Suspended this long, without coming back or ending: the phone takes the queue back, paused.
    private val giveUp = Runnable {
        if (suspended) {
            suspended = false
            moveTo(local, pause = true)
        }
    }

    fun start() {
        try {
            CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context))
                .addOnSuccessListener(::attach)
                .addOnFailureListener { Log.w(TAG, "No Cast on this phone", it) }
        } catch (e: Exception) {
            Log.w(TAG, "No Cast on this phone", e)
        }
    }

    private fun attach(castContext: CastContext) {
        this.castContext = castContext
        val player = CastPlayer(castContext, Converter())
        player.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                suspended = false
                handler.removeCallbacks(giveUp)
                moveTo(player)
            }

            // Told when a session ends and also when it is only suspended: which it was is known
            // once every listener has heard (see below), so it is decided a moment later.
            override fun onCastSessionUnavailable() {
                handler.post {
                    if (suspended) {
                        handler.removeCallbacks(giveUp)
                        handler.postDelayed(giveUp, SUSPENDED_MS)
                    } else {
                        moveTo(local)
                    }
                }
            }
        })
        castContext.sessionManager.addSessionManagerListener(object : SessionManagerListener<CastSession> {
            override fun onSessionSuspended(session: CastSession, reason: Int) {
                suspended = true
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                suspended = false
                handler.removeCallbacks(giveUp)
            }

            // A suspended session that ends: the Cast player says nothing more, so it is done here.
            override fun onSessionEnded(session: CastSession, error: Int) = endedWhileSuspended()
            override fun onSessionResumeFailed(session: CastSession, error: Int) = endedWhileSuspended()

            override fun onSessionStarting(session: CastSession) {}
            override fun onSessionStarted(session: CastSession, sessionId: String) {}
            override fun onSessionStartFailed(session: CastSession, error: Int) {}
            override fun onSessionEnding(session: CastSession) {}
            override fun onSessionResuming(session: CastSession, sessionId: String) {}
        }, CastSession::class.java)
        cast = player
        if (player.isCastSessionAvailable) moveTo(player)
    }

    private fun endedWhileSuspended() {
        if (!suspended) return
        suspended = false
        handler.removeCallbacks(giveUp)
        handler.post { moveTo(local) }
    }

    /** [items] as the phone plays them, each with its address; null if one can't be found. */
    private fun playable(items: List<MediaItem>): List<MediaItem>? = items.mapIndexed { i, item ->
        when {
            item.localConfiguration != null -> item
            known.containsKey(item.mediaId) -> known.getValue(item.mediaId)
            // The empty item the Cast player puts for one it doesn't know yet: the one at the same
            // place in the queue handed over, while the TV's queue is still that one.
            items.size == lastItems.size -> lastItems[i]
            else -> null
        } ?: return null
    }

    /**
     * The queue, where it was and whether it played, carried over to [target], which then is the
     * session's — paused there when [pause].
     */
    private fun moveTo(target: Player, pause: Boolean = false) {
        val mediaSession = session() ?: return
        val from = mediaSession.player
        val toCast = target !== local
        if (toCast) {
            server.start()
            hold(true)
        }
        if (from !== target) {
            val fromCast = from !== local
            if (fromCast) snapshot.run()
            handler.removeCallbacks(snapshot)
            val items: List<MediaItem>
            val index: Int
            val position: Long
            val playing: Boolean
            if (fromCast) {
                // Back from the TV: what it played, as the phone plays it.
                items = lastItems
                index = lastIndex
                position = lastPosition
                playing = lastPlaying
            } else {
                items = (0 until from.mediaItemCount).map(from::getMediaItemAt).filter { it.localConfiguration != null }
                index = from.currentMediaItem?.let(items::indexOf)?.takeIf { it >= 0 } ?: 0
                position = from.currentPosition
                playing = from.playWhenReady
                known.clear()
                items.forEach { known[it.mediaId] = it }
                lastItems = items
                lastIndex = index
                lastPosition = position
                lastPlaying = playing
            }
            from.stop()
            if (items.isNotEmpty()) {
                try {
                    target.setMediaItems(items, index.coerceIn(0, items.size - 1), position)
                    target.playWhenReady = playing && !pause
                    target.prepare()
                } catch (e: Exception) {
                    // Whatever is wrong with the queue, the music stops rather than the app.
                    Log.w(TAG, "Couldn't hand the queue over", e)
                }
            }
            mediaSession.player = target
            if (toCast) handler.post(snapshot)
        }
        // What the phone was restoring isn't heard now.
        if (toCast) {
            com.example.myapplication.player.restore.RestoreState.report("main", false)
            com.example.myapplication.player.restore.RestoreState.report("helper", false)
            com.example.myapplication.player.restore.RestoreState.off(target.currentMediaItem?.mediaId)
        }
        AudioOutputs.castingTo.value = if (toCast) {
            castContext?.sessionManager?.currentCastSession?.castDevice?.friendlyName ?: "Chromecast"
        } else {
            null
        }
        if (!toCast) {
            hold(false)
            server.stop()
        }
    }

    /** Wi-Fi and the processor kept awake while the TV reads from the phone. */
    private fun hold(on: Boolean) {
        if (on) {
            if (wifiLock == null) {
                wifiLock = context.getSystemService(WifiManager::class.java)
                    ?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "YouCloud:cast")
                    ?.apply { setReferenceCounted(false); acquire() }
            }
            if (wakeLock == null) {
                wakeLock = context.getSystemService(PowerManager::class.java)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "YouCloud:cast")
                    ?.apply { setReferenceCounted(false); acquire(6 * 60 * 60 * 1000L) }
            }
        } else {
            runCatching { wifiLock?.release() }
            runCatching { wakeLock?.release() }
            wifiLock = null
            wakeLock = null
        }
    }

    fun release() {
        handler.removeCallbacks(snapshot)
        handler.removeCallbacks(giveUp)
        cast?.setSessionAvailabilityListener(null)
        cast?.release()
        cast = null
        hold(false)
        server.stop()
        AudioOutputs.castingTo.value = null
    }

    /**
     * A queue item as the TV is handed it: the phone's address for its sound, its title, artist
     * and cover; and, kept with it, the track as the app knows it, to come back with.
     */
    private inner class Converter : MediaItemConverter {
        override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
            val original = mediaItem.localConfiguration?.uri ?: mediaItem.requestMetadata.mediaUri ?: Uri.EMPTY
            val url = server.urlFor(original) ?: original.toString()
            val meta = mediaItem.mediaMetadata
            val cover = tvArtwork(meta.artworkUri)
            val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                meta.title?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, it.toString()) }
                meta.artist?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, it.toString()) }
                meta.albumTitle?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
                cover?.let { addImage(WebImage(Uri.parse(it), TV_COVER_PX, TV_COVER_PX)) }
            }
            val custom = JSONObject()
                .put("mediaId", mediaItem.mediaId)
                .put("uri", original.toString())
                .put("title", meta.title?.toString())
                .put("artist", meta.artist?.toString())
                .put("album", meta.albumTitle?.toString())
                .put("artwork", meta.artworkUri?.toString())
                // What YouCloud's own screen on the TV shows (see [RECEIVER_APP_ID]).
                .put("cover", cover)
            val info = MediaInfo.Builder(url)
                .setContentUrl(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentTypeOf(original))
                .setMetadata(castMeta)
                .setCustomData(custom)
                .build()
            return MediaQueueItem.Builder(info).setCustomData(custom).build()
        }

        override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
            val custom = mediaQueueItem.customData ?: mediaQueueItem.media?.customData ?: JSONObject()
            val id = custom.optString("mediaId")
            // A track the phone handed over: as it plays it.
            known[id]?.let { return it }
            val metadata = MediaMetadata.Builder()
                .setTitle(custom.optString("title").takeIf { it.isNotEmpty() && it != "null" })
                .setArtist(custom.optString("artist").takeIf { it.isNotEmpty() && it != "null" })
                .setAlbumTitle(custom.optString("album").takeIf { it.isNotEmpty() && it != "null" })
                .setArtworkUri(custom.optString("artwork").takeIf { it.isNotEmpty() && it != "null" }?.let(Uri::parse))
                .build()
            return MediaItem.Builder()
                .setMediaId(id)
                .setUri(custom.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse))
                .setMediaMetadata(metadata)
                .build()
        }
    }

    /**
     * The cover as a TV fetches it: the service's own, in a size for a big screen, or, for a
     * download whose cover is only on the phone, served by the phone ([CastStreamServer]). Without
     * one the TV showed the title on grey.
     */
    private fun tvArtwork(artwork: Uri?): String? {
        val url = artwork?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val sized = com.example.myapplication.ui.artworkUrlForSize(url, androidx.compose.ui.unit.Dp(TV_COVER_DP)) ?: url
        if (sized.startsWith("https://") || sized.startsWith("http://")) return sized
        val file = if (sized.startsWith("/")) Uri.fromFile(java.io.File(sized)) else Uri.parse(sized)
        return server.imageUrlFor(file)
    }

    private fun contentTypeOf(uri: Uri): String = when (uri.scheme) {
        "ytmusic" -> "audio/mp4"
        "file" -> when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "m4a", "mp4", "aac" -> "audio/mp4"
            "opus", "ogg" -> "audio/ogg"
            "webm" -> "audio/webm"
            "flac" -> "audio/flac"
            else -> "audio/mpeg"
        }
        else -> "audio/mpeg"
    }

    private companion object {
        const val TAG = "CastSupport"
        const val SUSPENDED_MS = 45_000L
        // A cover asked for as for a 320 dp tile: 500–1000 px, what the services have.
        const val TV_COVER_DP = 320f
        const val TV_COVER_PX = 1000
    }
}

/**
 * YouCloud's own screen on the TV — the cover large, the player's colours taken from it, the title
 * and the time as the app sets them (web/cast.html, served by the site at /cast.html) — once it is
 * registered in the Google Cast SDK Developer Console under that address: its application id goes
 * here. Empty, the TV shows Google's own player: the cover and the title, but not the app's look.
 */
internal const val RECEIVER_APP_ID = ""

/** What the Cast framework asks the app for: the receiver, YouCloud's screen or Google's own player on the TV. */
class CastOptionsProvider : com.google.android.gms.cast.framework.OptionsProvider {
    override fun getCastOptions(context: Context): com.google.android.gms.cast.framework.CastOptions =
        com.google.android.gms.cast.framework.CastOptions.Builder()
            .setReceiverApplicationId(RECEIVER_APP_ID.ifEmpty { com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID })
            .setStopReceiverApplicationWhenEndingSession(true)
            // The app's own media session and notification go on: the framework's would be a second.
            .setCastMediaOptions(
                com.google.android.gms.cast.framework.media.CastMediaOptions.Builder()
                    .setNotificationOptions(null)
                    .setMediaSessionEnabled(false)
                    .build()
            )
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<com.google.android.gms.cast.framework.SessionProvider>? = null
}
