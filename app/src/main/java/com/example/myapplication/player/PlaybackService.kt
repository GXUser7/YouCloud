package com.example.myapplication.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.app.PendingIntent
import android.content.Intent
import com.example.myapplication.MainActivity
import com.example.myapplication.data.OfflineMusicStore
import java.io.IOException

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private lateinit var preferences: SharedPreferences

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) return@OnSharedPreferenceChangeListener
        val eq = equalizer ?: return@OnSharedPreferenceChangeListener
        try {
            if (key == "equalizer_enabled") {
                val enabled = preferences.getBoolean(key, false)
                eq.enabled = enabled
            } else if (key.startsWith("eq_band_")) {
                val band = key.removePrefix("eq_band_").toIntOrNull()
                if (band != null) {
                    val level = preferences.getInt(key, 0)
                    val minLevel = eq.bandLevelRange[0]
                    val maxLevel = eq.bandLevelRange[1]
                    val coercedLevel = level.coerceIn(minLevel.toInt(), maxLevel.toInt())
                    eq.setBandLevel(band.toShort(), coercedLevel.toShort())
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackService", "Error updating equalizer parameter", e)
        }
    }

    @androidx.media3.common.util.UnstableApi
    override fun onCreate() {
        super.onCreate()

        preferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(prefListener)

        val baseFactory = OfflineMusicStore.getInstance(this).cacheDataSourceFactory
        val resolvingFactory = androidx.media3.datasource.ResolvingDataSource.Factory(
            baseFactory,
            object : androidx.media3.datasource.ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme == "soundcloud") {
                        val trackId = uri.lastPathSegment?.toLongOrNull()
                        if (trackId != null) {
                            val resolvedUri = resolveSoundCloudTrack(trackId)
                            if (resolvedUri != null) {
                                return dataSpec.buildUpon().setUri(android.net.Uri.parse(resolvedUri)).build()
                            }
                        }
                    } else if (uri.scheme == "yandex") {
                        val trackId = uri.lastPathSegment
                        if (trackId != null) {
                            val resolvedUri = resolveYandexTrack(trackId)
                            if (resolvedUri != null) {
                                return dataSpec.buildUpon().setUri(android.net.Uri.parse(resolvedUri)).build()
                            }
                        }
                    }
                    return dataSpec
                }
            }
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    initEqualizer(audioSessionId)
                }
            }
        })

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()
    }

    // Lazy-initialized shared instances (#13: avoid re-creating on each track resolve)
    private val lazyFavoritesRepository by lazy { com.example.myapplication.data.FavoritesRepository(this) }
    private val lazyPlaylistsRepository by lazy { com.example.myapplication.data.PlaylistsRepository(this) }
    private val lazyOkHttpClient by lazy { okhttp3.OkHttpClient() }

    // Cached regex patterns (#34: avoid recompilation on each call)
    companion object {
        private val XML_TAG_REGEXES = mutableMapOf<String, Regex>()
        private fun xmlTagRegex(tag: String): Regex {
            return XML_TAG_REGEXES.getOrPut(tag) { "<$tag>(.*?)</$tag>".toRegex() }
        }
    }

    private fun resolveSoundCloudTrack(trackId: Long): String? {
        val fav = lazyFavoritesRepository.get(trackId)
        if (fav != null && fav.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED && !fav.streamUrl.isNullOrBlank()) {
            val localPath = fav.streamUrl
            val finalUrl = if (localPath.startsWith("/") && !localPath.startsWith("file://")) {
                "file://$localPath"
            } else {
                localPath
            }
            Log.d("PlaybackService", "Playing track from cache: $trackId, url: $finalUrl")
            return finalUrl
        }
 
        val playlistTrack = lazyPlaylistsRepository.playlists.value
            .flatMap { it.tracks }
            .firstOrNull { it.id == trackId && it.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED && !it.streamUrl.isNullOrBlank() }
        if (playlistTrack != null) {
            val localPath = playlistTrack.streamUrl!!
            val finalUrl = if (localPath.startsWith("/") && !localPath.startsWith("file://")) {
                "file://$localPath"
            } else {
                localPath
            }
            Log.d("PlaybackService", "Playing track from playlist cache: $trackId, url: $finalUrl")
            return finalUrl
        }

        val clientId = preferences.getString("soundcloud_client_id", "") ?: ""
        val oauthToken = preferences.getString("soundcloud_oauth_token", "") ?: ""

        if (clientId.isBlank()) {
            Log.e("PlaybackService", "Client ID is blank, cannot resolve track: $trackId")
            return null
        }

        return kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(15_000L) {
            try {
                val service = com.example.myapplication.data.SoundCloudApi.createService { oauthToken }
                val playbackResolver = com.example.myapplication.data.SoundCloudPlaybackResolver(service)
                val track = service.getTrack(trackId, clientId)
                playbackResolver.resolve(track, clientId)
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error resolving track online: $trackId", e)
                if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                    val newClientId = com.example.myapplication.data.SoundCloudApi.fetchSoundCloudClientId()
                    if (newClientId != null) {
                        preferences.edit().putString("soundcloud_client_id", newClientId).apply()
                        try {
                            val service = com.example.myapplication.data.SoundCloudApi.createService { oauthToken }
                            val playbackResolver = com.example.myapplication.data.SoundCloudPlaybackResolver(service)
                            val track = service.getTrack(trackId, newClientId)
                            playbackResolver.resolve(track, newClientId)
                        } catch (retryEx: Exception) {
                            Log.e("PlaybackService", "Error resolving track on retry: $trackId", retryEx)
                            null
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
            }
        }
    }

    private fun resolveYandexTrack(trackId: String): String? {
        val rawTrackId = trackId.substringBefore(":")
        // Match ID generation from YandexMusicModels.kt (#6)
        val numericId = rawTrackId.toLongOrNull()
        val generatedId = if (numericId != null) {
            -(numericId + 1_000_000_000L)
        } else {
            val hash = rawTrackId.hashCode().toLong()
            -(if (hash == Int.MIN_VALUE.toLong()) Int.MAX_VALUE.toLong() else kotlin.math.abs(hash)) - 1_000_000_000L
        }
        val fav = lazyFavoritesRepository.get(generatedId)
        if (fav != null && fav.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED && !fav.streamUrl.isNullOrBlank()) {
            val localPath = fav.streamUrl
            val finalUrl = if (localPath.startsWith("/") && !localPath.startsWith("file://")) {
                "file://$localPath"
            } else {
                localPath
            }
            Log.d("PlaybackService", "Playing Yandex track from cache: $trackId, url: $finalUrl")
            return finalUrl
        }

        val token = preferences.getString("yandex_music_token", "") ?: ""
        return kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(15_000L) {
            try {
                val service = com.example.myapplication.data.YandexMusicApi.createService { token }
                val response = service.getDownloadInfo(trackId)
                val bestItem = response.result.orEmpty().firstOrNull { it.codec == "mp3" } ?: response.result.orEmpty().firstOrNull()
                    ?: return@withTimeout null
                
                val client = lazyOkHttpClient
                val request = okhttp3.Request.Builder()
                    .url(bestItem.downloadInfoUrl)
                    .header("Authorization", "OAuth $token")
                    .build()
                
                val xmlString = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Yandex HTTP ${response.code}")
                    response.body?.string() ?: ""
                }
                
                if (xmlString.isEmpty()) return@withTimeout null
                
                val host = xmlTagRegex("host").find(xmlString)?.groupValues?.get(1).orEmpty()
                val path = xmlTagRegex("path").find(xmlString)?.groupValues?.get(1).orEmpty()
                val ts = xmlTagRegex("ts").find(xmlString)?.groupValues?.get(1).orEmpty()
                val s = xmlTagRegex("s").find(xmlString)?.groupValues?.get(1).orEmpty()
                
                if (host.isNotEmpty() && path.isNotEmpty() && ts.isNotEmpty() && s.isNotEmpty()) {
                    com.example.myapplication.data.YandexMusicApi.generateDirectLink(host, path, ts, s)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error resolving Yandex track: $trackId", e)
                null
            }
            }
        }
    }

    private fun initEqualizer(audioSessionId: Int) {
        try {
            equalizer?.release()
            val eq = Equalizer(0, audioSessionId)
            val enabled = preferences.getBoolean("equalizer_enabled", false)
            eq.enabled = enabled
            
            val numBands = eq.numberOfBands.toInt()
            for (i in 0 until numBands) {
                val level = preferences.getInt("eq_band_$i", 0)
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val coercedLevel = level.coerceIn(minLevel.toInt(), maxLevel.toInt())
                eq.setBandLevel(i.toShort(), coercedLevel.toShort())
            }
            equalizer = eq
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to init Equalizer", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(prefListener)
        equalizer?.release()
        equalizer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
