package com.example.myapplication

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    // A link shared to the app, or opened with it, until the screen has taken it.
    private val incomingLink = MutableStateFlow<String?>(null)

    // A shortcut's, the quick settings tile's or the widget's action, until the screen has taken it.
    private val incomingAction = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.myapplication.ui.Appearance.keepLanguageOfEarlierVersion(this)
        // The language the app's screens are in, for the rest of it too (see i18n.tr): changed from
        // Android's own settings while the app ran in the background, the process kept the old one.
        android.os.LocaleList.setDefault(resources.configuration.locales)
        // Only the intent the app was opened with: brought back after the system let it go, the
        // activity is handed that same intent again, and the link was opened a second time.
        if (savedInstanceState == null) {
            takeLink(intent)
            takeAction(intent)
        }
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
                
                val link by incomingLink.collectAsState()
                LaunchedEffect(link) {
                    val text = link ?: return@LaunchedEffect
                    incomingLink.value = null
                    viewModel.openSharedText(text)
                }
                val action by incomingAction.collectAsState()
                LaunchedEffect(action) {
                    val wanted = action ?: return@LaunchedEffect
                    incomingAction.value = null
                    when (wanted) {
                        ACTION_WAVE -> viewModel.startWaveFromOutside()
                        ACTION_MY_MUSIC -> viewModel.openDownloads()
                        ACTION_SEARCH -> viewModel.openSearch()
                    }
                }

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

    // The app open already (it is single-task): a link shared or opened with it arrives here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeLink(intent)
        takeAction(intent)
    }

    private fun takeAction(intent: Intent?) {
        intent?.action?.takeIf { it in ACTIONS }?.let { incomingAction.value = it }
    }

    companion object {
        /** "Моя волна", played: from the icon's shortcut, the quick settings tile and the widget. */
        const val ACTION_WAVE = "com.example.myapplication.action.WAVE"
        const val ACTION_MY_MUSIC = "com.example.myapplication.action.MY_MUSIC"
        const val ACTION_SEARCH = "com.example.myapplication.action.SEARCH"
        private val ACTIONS = setOf(ACTION_WAVE, ACTION_MY_MUSIC, ACTION_SEARCH)
    }

    /** The text of a link opened with the app (VIEW) or shared to it (SEND). */
    private fun takeLink(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }
        if (!text.isNullOrBlank()) incomingLink.value = text
    }
}
