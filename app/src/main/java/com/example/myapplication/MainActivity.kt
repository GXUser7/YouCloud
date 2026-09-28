package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.FavoritesRepository
import com.example.myapplication.data.OfflineMusicStore
import com.example.myapplication.data.PlaylistsRepository
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.player.MusicPlayer
import com.example.myapplication.ui.MusicScreen
import com.example.myapplication.ui.MusicViewModel
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // The app's backdrop is opaque and covers the whole window, so the window's own background
        // under it was a screenful of colour painted on every frame and never seen.
        window.setBackgroundDrawable(null)

        setContent {
            MyApplicationTheme {
                val viewModel: MusicViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        // Made with the view model, once, and not on every onCreate: a recreated
                        // activity keeps the view model it had, and the player, repositories and
                        // the libraries they read from disk here went unused — the player's
                        // controller and its position ticker still running, never released.
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MusicViewModel(
                                context = this@MainActivity,
                                musicPlayer = MusicPlayer(this@MainActivity),
                                favoritesRepository = FavoritesRepository(applicationContext),
                                playlistsRepository = PlaylistsRepository(applicationContext),
                                offlineMusicStore = OfflineMusicStore.getInstance(this@MainActivity),
                                settingsRepository = SettingsRepository(
                                    context = this@MainActivity,
                                    defaultClientId = BuildConfig.DEFAULT_SOUNDCLOUD_CLIENT_ID.trim(),
                                    defaultOauthToken = BuildConfig.DEFAULT_SOUNDCLOUD_OAUTH_TOKEN.trim()
                                )
                            ) as T
                        }
                    }
                )
                
                // What a Surface in the background colour gave, without its fill: the backdrop
                // covers it on every screen, and it too was painted on every frame for nothing.
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.contentColorFor(MaterialTheme.colorScheme.background)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), propagateMinConstraints = true) {
                        MusicScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
