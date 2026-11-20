package com.example.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.SPOTIFY_REDIRECT
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
    initialClientId: String,
    busy: Boolean,
    error: String?,
    onLogin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var clientId by rememberSaveable { mutableStateOf(initialClientId) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(ServiceIcons.Spotify, contentDescription = null) },
        title = { Text(tr("Вход в Spotify")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Возьмите Client ID в Spotify Developer Dashboard и добавьте в приложении Spotify этот Redirect URI."))
                Text(SPOTIFY_REDIRECT, style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") },
                    enabled = !busy,
                    singleLine = true,
                    isError = error != null
                )
                if (busy) Text(tr("Завершите вход в браузере."))
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = { onLogin(clientId) }, enabled = !busy) { Text(tr("Войти")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } }
    )
}
