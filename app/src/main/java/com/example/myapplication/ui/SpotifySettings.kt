package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myapplication.data.SPOTIFY_LOGIN
import com.example.myapplication.data.SpotifyLogin
import com.example.myapplication.data.SpotifyWebSession
import com.example.myapplication.i18n.tr

@Composable
internal fun SpotifyAccountRow(account: String?, onLogin: () -> Unit, onLogout: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(ServiceIcons.Spotify, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text("Spotify", style = MaterialTheme.typography.titleMedium)
            Text(
                account ?: tr("Не подключен"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (account == null) Button(onClick = onLogin) { Text(tr("Войти")) }
        else FilledTonalButton(onClick = onLogout) { Text(tr("Выйти")) }
    }
}

@Composable
internal fun SpotifyLoginDialog(
    busy: Boolean,
    error: String?,
    onSession: (SpotifyWebSession) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    var attempt by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var pageError by remember { mutableStateOf(false) }
    val currentOnSession by rememberUpdatedState(onSession)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = tr("Закрыть"))
                    }
                    Text(tr("Вход в Spotify"), style = MaterialTheme.typography.titleLarge)
                }
                val message = error ?: if (pageError) tr("Не удалось открыть страницу входа Spotify.") else null
                if (message != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        FilledTonalButton(onClick = {
                            onRetry()
                            pageError = false
                            loading = true
                            attempt++
                        }, enabled = !busy) { Text(tr("Повторить")) }
                    }
                } else if (busy) {
                    Text(tr("Подключаем Spotify…"), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    key(attempt) {
                        if (!pageError) AndroidView(
                            factory = { context ->
                                SpotifyLogin.webView(
                                    context,
                                    onSession = { currentOnSession(it) },
                                    onLoading = { loading = it },
                                    onError = {
                                        loading = false
                                        pageError = true
                                    }
                                ).apply { loadUrl(SPOTIFY_LOGIN) }
                            },
                            onRelease = { it.destroy() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
