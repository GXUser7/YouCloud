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
import com.google.android.gms.common.images.WebImage
import org.json.JSONObject

/**
 * Google Cast: a TV, or a speaker with Chromecast built in, picked from the track's menu plays the
 * queue in the phone's place. The session's player becomes a [CastPlayer] (so the app, the
 * notification and the lock screen go on showing and controlling the music, on the TV now), and
 * the queue moves over from where it was; the session ending moves it back to the phone, where the
 * TV left off. What the TV plays the phone serves it ([CastStreamServer]).
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
            override fun onCastSessionAvailable() = moveTo(player)
            override fun onCastSessionUnavailable() = moveTo(local)
        })
        cast = player
        if (player.isCastSessionAvailable) moveTo(player)
    }

    /** The queue, where it was and whether it played, carried over to [target], which then is the session's. */
    private fun moveTo(target: Player) {
        val mediaSession = session() ?: return
        val from = mediaSession.player
        val toCast = target !== local
        if (toCast) {
            server.start()
            hold(true)
        }
        if (from !== target) {
            val items = (0 until from.mediaItemCount).map(from::getMediaItemAt)
            val index = from.currentMediaItemIndex
            val position = from.currentPosition
            val playing = from.playWhenReady
            from.stop()
            if (items.isNotEmpty()) {
                target.setMediaItems(items, index.coerceIn(0, items.size - 1), position)
                target.playWhenReady = playing
                target.prepare()
            }
            mediaSession.player = target
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
            val castMeta = com.google.android.gms.cast.MediaMetadata(com.google.android.gms.cast.MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
                meta.title?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_TITLE, it.toString()) }
                meta.artist?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_ARTIST, it.toString()) }
                meta.albumTitle?.let { putString(com.google.android.gms.cast.MediaMetadata.KEY_ALBUM_TITLE, it.toString()) }
                meta.artworkUri?.takeIf { it.scheme == "http" || it.scheme == "https" }?.let { addImage(WebImage(it)) }
            }
            val custom = JSONObject()
                .put("mediaId", mediaItem.mediaId)
                .put("uri", original.toString())
                .put("title", meta.title?.toString())
                .put("artist", meta.artist?.toString())
                .put("album", meta.albumTitle?.toString())
                .put("artwork", meta.artworkUri?.toString())
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
            val metadata = MediaMetadata.Builder()
                .setTitle(custom.optString("title").takeIf { it.isNotEmpty() && it != "null" })
                .setArtist(custom.optString("artist").takeIf { it.isNotEmpty() && it != "null" })
                .setAlbumTitle(custom.optString("album").takeIf { it.isNotEmpty() && it != "null" })
                .setArtworkUri(custom.optString("artwork").takeIf { it.isNotEmpty() && it != "null" }?.let(Uri::parse))
                .build()
            return MediaItem.Builder()
                .setMediaId(custom.optString("mediaId"))
                .setUri(custom.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse))
                .setMediaMetadata(metadata)
                .build()
        }
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
    }
}

/** What the Cast framework asks the app for: the receiver, Google's own player on the TV. */
class CastOptionsProvider : com.google.android.gms.cast.framework.OptionsProvider {
    override fun getCastOptions(context: Context): com.google.android.gms.cast.framework.CastOptions =
        com.google.android.gms.cast.framework.CastOptions.Builder()
            .setReceiverApplicationId(com.google.android.gms.cast.CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
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
