package com.example.myapplication.player

import android.app.PendingIntent
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.myapplication.MainActivity

/**
 * "Моя волна" in the quick settings: a tap opens the app with the wave playing, as the shortcut on
 * its icon does. Lit while the wave plays.
 */
class WaveTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        tile.state = if (SessionBridge.canDislike.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_WAVE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }
}
