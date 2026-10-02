package com.example.myapplication.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.together.ListenTogether
import com.example.myapplication.together.TogetherPeer
import com.example.myapplication.together.TogetherRequest
import com.example.myapplication.together.TogetherSession
import com.example.myapplication.together.TogetherState
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Listening together as the player shows it, on the chip row over the title: what the session
 * is ([label]), and the way to its sheet. Null while not listening together.
 */
internal class TogetherBadge(val label: String, val onClick: () -> Unit)

internal val LocalTogether = staticCompositionLocalOf<TogetherBadge?> { null }

/** What the player's chip says of [state]; null when there is nothing to say. */
internal fun togetherLabel(state: TogetherState): String? = when (state) {
    is TogetherState.Hosting -> if (state.guests.isEmpty()) "Вместе · ждём" else "Вместе · ${state.guests.size + 1}"
    is TogetherState.Joined -> "Вместе"
    else -> null
}

private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "$count $word"
}

/**
 * "Слушать вместе": who hosts, who joins, and how it goes. Phones side by side, connected
 * directly: one calls the others in, they find it nearby; each plays the music itself, kept level
 * with the host's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TogetherSheet(
    together: ListenTogether,
    onHost: () -> Unit,
    onSearch: () -> Unit,
    onJoin: (TogetherPeer) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit
) {
    val state by together.state.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // What to do once the "Nearby devices" permission is given.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.all { it }
        denied = !granted
        if (granted) afterPermission?.invoke()
        afterPermission = null
    }
    fun withPermission(action: () -> Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        val missing = TogetherSession.PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            afterPermission = action
            permissions.launch(missing.toTypedArray())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Kicker(text = "Рядом, без интернета между вами", color = PanelColors.accent)
            Text("Слушать вместе", style = MaterialTheme.typography.headlineSmall)
            AnimatedContent(
                targetState = state,
                contentKey = { it::class },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "togetherState"
            ) { shown ->
                when (shown) {
                    TogetherState.Idle -> IdleTogether(
                        denied = denied,
                        onHost = { withPermission(onHost) },
                        onSearch = { withPermission(onSearch) }
                    )
                    is TogetherState.Hosting -> HostingTogether(shown, onLeave)
                    is TogetherState.Searching -> SearchingTogether(shown, onJoin, onLeave)
                    is TogetherState.Joined -> JoinedTogether(shown, together, onLeave)
                    is TogetherState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(shown.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Понятно") }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleTogether(denied: Boolean, onHost: () -> Unit, onSearch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Телефоны соединяются напрямую по Bluetooth и Wi-Fi. Каждый слушает со своего телефона, " +
                "а YouCloud держит музыку вровень. Друзей может быть несколько.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TogetherTile(
                icon = Icons.Rounded.WifiTethering,
                title = "Позвать",
                text = "Ваш телефон станет виден рядом, музыку выбираете вместе",
                accent = true,
                onClick = onHost,
                modifier = Modifier.weight(1f)
            )
            TogetherTile(
                icon = Icons.Rounded.PersonSearch,
                title = "Присоединиться",
                text = "Найти друга, который уже позвал",
                accent = false,
                onClick = onSearch,
                modifier = Modifier.weight(1f)
            )
        }
        if (denied) {
            Text(
                "Без доступа к устройствам поблизости телефоны не найдут друг друга. Его можно дать в настройках Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun TogetherTile(
    icon: ImageVector,
    title: String,
    text: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = if (accent) PanelColors.accent else PanelColors.container,
        contentColor = if (accent) PanelColors.onAccent else PanelColors.content
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodySmall, color = (if (accent) PanelColors.onAccent else PanelColors.content).copy(alpha = 0.8f))
        }
    }
}

/** Rings spreading from the middle: this phone being seen, or looking. */
@Composable
private fun NearbyPulse(modifier: Modifier = Modifier) {
    val clock = rememberLoopClock()
    val color = PanelColors.accent
    Box(modifier = modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val t = (clock.longValue % 2_400L) / 2_400f
            for (i in 0 until 3) {
                val p = (t + i / 3f) % 1f
                drawCircle(
                    color = color.copy(alpha = 0.5f * (1f - p)),
                    radius = size.minDimension / 2 * (0.3f + 0.7f * p),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        Surface(shape = CircleShape, color = PanelColors.accent, contentColor = PanelColors.onAccent, modifier = Modifier.size(52.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun PeerRow(name: String, trailing: String?, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(PanelColors.container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(PanelColors.accent),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = PanelColors.onAccent)
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = PanelColors.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = PanelColors.accent)
    }
}

@Composable
private fun HostingTogether(state: TogetherState.Hosting, onLeave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        NearbyPulse()
        Text(
            if (state.guests.isEmpty()) {
                "Ждём друзей рядом. Пусть откроют «Слушать вместе» → «Присоединиться»."
            } else {
                "Слушаете вместе. Что играет у вас, играет и у них, а их кнопки управляют вашим плеером."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        state.guests.forEach { PeerRow(it.name, "слушает") }
        TextButton(onClick = onLeave) { Text(if (state.guests.isEmpty()) "Отмена" else "Закончить") }
    }
}

@Composable
private fun SearchingTogether(state: TogetherState.Searching, onJoin: (TogetherPeer) -> Unit, onLeave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val joining = state.joining
        if (joining == null) {
            NearbyPulse()
            Text(
                if (state.hosts.isEmpty()) "Ищем рядом. У друга должно быть открыто «Слушать вместе» → «Позвать»." else "Нашлись рядом:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            state.hosts.forEach { host -> PeerRow(host.name, "Подключиться") { onJoin(host) } }
        } else {
            NearbyPulse()
            Text("Подключаемся к «${joining.name}»", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            state.code?.let { code ->
                Text("Код $code — на экране друга должен быть тот же", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        TextButton(onClick = onLeave) { Text("Отмена") }
    }
}

@Composable
private fun JoinedTogether(state: TogetherState.Joined, together: ListenTogether, onLeave: () -> Unit) {
    val drift by together.driftMs.collectAsState()
    val latency by together.latencyMs.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PeerRow(
            state.host.name,
            when {
                drift == null -> "ждём"
                abs(drift!!) <= 50 -> "вровень"
                else -> "догоняем ${abs(drift!!)} мс"
            }
        )
        Text(
            "Играет то же, что у «${state.host.name}». Ваши кнопки управляют общим плеером, а выбранный трек заиграет у всех.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Наушники запаздывают: ${latency} мс", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = latency.toFloat(),
            onValueChange = { together.setLatency((it / 10f).roundToLong() * 10) },
            valueRange = -300f..500f
        )
        Text(
            "Если с Bluetooth-наушниками звук отстаёт от друга, сдвиньте вправо.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Отключиться") }
    }
}

/** A guest asking the host in: who, and the code both screens show. */
@Composable
internal fun TogetherRequestDialog(request: TogetherRequest, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = { Icon(Icons.Rounded.Groups, contentDescription = null) },
        title = { Text("«${request.peer.name}» хочет слушать вместе") },
        text = { Text("Код на его экране: ${request.code}. Пустите, если он совпадает.") },
        confirmButton = { Button(onClick = { onAnswer(true) }) { Text("Пустить") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("Не пускать") } }
    )
}
