package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.FavoritesRepository
import com.example.myapplication.data.OfflineMusicStore
import com.example.myapplication.data.SettingsRepository
import com.example.myapplication.player.MusicPlayer
import com.example.myapplication.ui.MusicScreen
import com.example.myapplication.ui.MusicViewModel
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val offlineMusicStore = OfflineMusicStore.getInstance(this)
        val favoritesRepository = FavoritesRepository(this)
        val settingsRepository = SettingsRepository(
            context = this,
            defaultClientId = BuildConfig.DEFAULT_SOUNDCLOUD_CLIENT_ID.trim(),
            defaultOauthToken = BuildConfig.DEFAULT_SOUNDCLOUD_OAUTH_TOKEN.trim()
        )
        val musicPlayer = MusicPlayer(this)
        
        setContent {
            MyApplicationTheme {
                val viewModel: MusicViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return MusicViewModel(
                                context = this@MainActivity,
                                musicPlayer = musicPlayer,
                                favoritesRepository = favoritesRepository,
                                offlineMusicStore = offlineMusicStore,
                                settingsRepository = settingsRepository
                            ) as T
                        }
                    }
                )
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicScreen(viewModel = viewModel)
                }
            }
        }
    }
}
