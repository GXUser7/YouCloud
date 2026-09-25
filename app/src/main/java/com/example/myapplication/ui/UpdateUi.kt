package com.example.myapplication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.UpdateRepository
import com.example.myapplication.data.UpdateState
import java.util.Locale

/** "12,4 МБ" — how big the download is, when the server said. */
private fun megabytes(bytes: Long): String =
    String.format(Locale.US, "%.1f", bytes / 1_048_576f).replace('.', ',') + " МБ"

/**
 * The update row in settings: what is installed, what the last check found, and the one next
 * step — check, download, install or retry — as its button.
 */
@Composable
internal fun UpdateSettingsRow(updates: UpdateRepository) {
    val state by updates.state.collectAsState()
    val context = LocalContext.current
    val busy = state is UpdateState.Checking || state is UpdateState.Downloading

    Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state is UpdateState.Failed) Icons.Default.ErrorOutline else Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = when (val s = state) {
                        UpdateState.Idle -> "Новые версии берутся из релизов на GitHub"
                        UpdateState.Checking -> "Проверяю…"
                        is UpdateState.UpToDate -> "Это самая свежая версия"
                        is UpdateState.Available -> "Доступна версия ${s.release.version}"
                        is UpdateState.Downloading -> "Скачиваю ${s.release.version}…"
                        is UpdateState.Ready -> "Версия ${s.release.version} готова к установке"
                        is UpdateState.Failed -> s.message
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state is UpdateState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = {
                    when (state) {
                        is UpdateState.Available -> updates.download()
                        is UpdateState.Ready -> updates.install(context)
                        else -> updates.check(manual = true)
                    }
                },
                enabled = !busy,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    when (val s = state) {
                        is UpdateState.Available ->
                            if (s.release.sizeBytes > 0) "Скачать · ${megabytes(s.release.sizeBytes)}" else "Скачать"
                        is UpdateState.Ready -> "Установить"
                        is UpdateState.Failed -> "Повторить"
                        UpdateState.Checking, is UpdateState.Downloading -> "…"
                        else -> "Проверить"
                    }
                )
            }
        }
        val downloading = state as? UpdateState.Downloading
        if (downloading != null) {
            AppLinearProgress(
                progress = downloading.total.takeIf { it > 0 }?.let { downloading.downloaded.toFloat() / it },
                modifier = Modifier.fillMaxWidth()
            )
        }
        val notes = (state as? UpdateState.Available)?.release?.notes?.takeIf { it.isNotBlank() }
        if (notes != null) {
            Text(
                text = notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * On home, only while there is something to act on: a new version out, downloading, or ready.
 * A small panel-toned card with the next step as its button, dismissable until the next check.
 */
@Composable
internal fun UpdateBanner(updates: UpdateRepository, modifier: Modifier = Modifier) {
    val state by updates.state.collectAsState()
    val context = LocalContext.current
    val visible = state is UpdateState.Available || state is UpdateState.Downloading || state is UpdateState.Ready

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = PanelColors.container,
            contentColor = PanelColors.content
        ) {
            Column(modifier = Modifier.padding(start = 18.dp, top = 10.dp, end = 8.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Kicker(text = "Обновление", color = PanelColors.content.copy(alpha = 0.72f))
                        Text(
                            text = when (val s = state) {
                                is UpdateState.Available -> "Вышла версия ${s.release.version}"
                                is UpdateState.Downloading -> "Скачиваю ${s.release.version}…"
                                is UpdateState.Ready -> "Версия ${s.release.version} готова"
                                else -> ""
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    when (state) {
                        is UpdateState.Available -> PanelActionButton("Скачать", Icons.Default.Download) { updates.download() }
                        is UpdateState.Ready -> PanelActionButton("Установить", Icons.Default.SystemUpdate) { updates.install(context) }
                        else -> Unit
                    }
                    if (state !is UpdateState.Downloading) {
                        IconButton(onClick = updates::clear) {
                            Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = PanelColors.content.copy(alpha = 0.72f))
                        }
                    }
                }
                val downloading = state as? UpdateState.Downloading
                if (downloading != null) {
                    AppLinearProgress(
                        progress = downloading.total.takeIf { it > 0 }?.let { downloading.downloaded.toFloat() / it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, end = 10.dp),
                        color = PanelColors.accent
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelActionButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = PanelColors.accent,
        contentColor = PanelColors.onAccent
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
