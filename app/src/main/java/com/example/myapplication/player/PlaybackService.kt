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

    override fun onCreate() {
        super.onCreate()

        preferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(prefListener)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    OfflineMusicStore.getInstance(this).cacheDataSourceFactory
                )
            )
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
