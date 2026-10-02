package com.example.myapplication.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.together.ListenTogether
import com.example.myapplication.together.TogetherPeer
import com.example.myapplication.together.TogetherRequest
import com.example.myapplication.together.TogetherSession
import com.example.myapplication.together.TogetherState
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sin

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

/** A phone's letter on its avatar: the first letter or digit of its name. */
private fun initialOf(name: String): String = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

/**
 * The steps of the sheet, each its own page; a step that only gains a guest or finds a host stays
 * the page it is, and grows its list in place.
 */
private fun stepOf(state: TogetherState): Int = when (state) {
    TogetherState.Idle -> 0
    is TogetherState.Hosting -> 1
    is TogetherState.Searching -> if (state.joining == null) 2 else 3
    is TogetherState.Joined -> 4
    is TogetherState.Failed -> 5
}

/**
 * "Слушать вместе": who hosts, who joins, and how it goes. Phones side by side, connected
 * directly: one calls the others in, they find it nearby; each plays the music itself, kept level
 * with the host's.
 *
 * Every step is laid out the same way, the full width of the sheet: a stage with who is in the
 * session over what is going on, the people under it, and the step's action across the bottom.
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
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        contentColor = colors.onBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Landscape leaves the sheet less height than a step with its list takes.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Kicker(text = "Рядом, без интернета между вами", color = PanelColors.accent)
                Text("Слушать вместе", style = MaterialTheme.typography.headlineSmall)
            }
            // Full width and centred whatever a step holds: a step narrower than the sheet was
            // left wherever the animation put it, against one side.
            AnimatedContent(
                targetState = state,
                modifier = Modifier.fillMaxWidth(),
                contentKey = ::stepOf,
                contentAlignment = Alignment.TopCenter,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 90)) togetherWith fadeOut(tween(90))) using SizeTransform(clip = false)
                },
                label = "togetherStep"
            ) { shown ->
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (shown) {
                        TogetherState.Idle -> IdleTogether(
                            denied = denied,
                            onHost = { withPermission(onHost) },
                            onSearch = { withPermission(onSearch) }
                        )
                        is TogetherState.Hosting -> HostingTogether(shown, onLeave)
                        is TogetherState.Searching -> {
                            val joining = shown.joining
                            if (joining == null) SearchingTogether(shown, onJoin, onLeave) else JoiningTogether(joining, shown.code, onLeave)
                        }
                        is TogetherState.Joined -> JoinedTogether(shown, together, onLeave)
                        is TogetherState.Failed -> {
                            // "Не удалось подключиться. Друг далеко": what failed as the title, why under it.
                            TogetherStage(
                                title = shown.message.substringBefore(". "),
                                text = shown.message.substringAfter(". ", "").ifBlank { null }
                            ) {
                                StageIcon(Icons.Rounded.LinkOff)
                            }
                            TogetherButton("Понятно", accent = true, onClick = onLeave)
                        }
                    }
                }
            }
        }
    }
}

// region Steps

@Composable
private fun IdleTogether(denied: Boolean, onHost: () -> Unit, onSearch: () -> Unit) {
    Text(
        "Телефоны соединяются напрямую по Bluetooth и Wi-Fi. Каждый слушает со своего телефона, " +
            "а YouCloud держит музыку вровень. Друзей может быть несколько.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    // One height for both, whichever says more.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TogetherTile(
            icon = Icons.Rounded.WifiTethering,
            title = "Позвать",
            text = "Ваш телефон станет виден рядом, музыку выбираете вместе",
            accent = true,
            onClick = onHost,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        TogetherTile(
            icon = Icons.Rounded.PersonSearch,
            title = "Присоединиться",
            text = "Найти друга, который уже позвал",
            accent = false,
            onClick = onSearch,
            modifier = Modifier.weight(1f).fillMaxHeight()
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

@Composable
private fun HostingTogether(state: TogetherState.Hosting, onLeave: () -> Unit) {
    val guests = state.guests
    TogetherStage(
        title = if (guests.isEmpty()) "Ждём друзей" else "Слушаете вместе",
        text = if (guests.isEmpty()) {
            "Пусть откроют «Слушать вместе» → «Присоединиться»"
        } else {
            "Что играет у вас, играет и у них. Их кнопки управляют вашим плеером"
        }
    ) {
        Crossfade(targetState = guests.isEmpty(), label = "hostingPicture") { waiting ->
            if (waiting) {
                NearbyPulse(Icons.Rounded.WifiTethering)
            } else {
                People(listOf("Вы") + guests.map { it.name }, firstIsYou = true)
            }
        }
    }
    if (guests.isNotEmpty()) {
        PeopleList(
            kicker = plural(guests.size, "друг рядом", "друга рядом", "друзей рядом"),
            people = guests,
            trailing = { OnPanelChip("слушает") }
        )
    }
    TogetherButton(if (guests.isEmpty()) "Отмена" else "Закончить", accent = false, onClick = onLeave)
}

@Composable
private fun SearchingTogether(state: TogetherState.Searching, onJoin: (TogetherPeer) -> Unit, onLeave: () -> Unit) {
    TogetherStage(
        title = if (state.hosts.isEmpty()) "Ищем рядом" else "Нашлись рядом",
        text = if (state.hosts.isEmpty()) {
            "У друга должно быть открыто «Слушать вместе» → «Позвать»"
        } else {
            "Выберите, к кому подключиться"
        }
    ) {
        NearbyPulse(Icons.Rounded.PersonSearch)
    }
    if (state.hosts.isNotEmpty()) {
        PeopleList(
            kicker = null,
            people = state.hosts,
            onClick = onJoin,
            trailing = {
                Surface(shape = CircleShape, color = PanelColors.accent, contentColor = PanelColors.onAccent) {
                    Text(
                        "Подключиться",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        )
    }
    TogetherButton("Отмена", accent = false, onClick = onLeave)
}

@Composable
private fun JoiningTogether(host: TogetherPeer, code: String?, onLeave: () -> Unit) {
    TogetherStage(
        title = "Подключаемся к «${host.name}»",
        text = if (code == null) "Ждём ответа…" else "На экране друга должен быть тот же код",
        below = code?.let { { CodeDigits(it) } }
    ) {
        Linking(host.name)
    }
    TogetherButton("Отмена", accent = false, onClick = onLeave)
}

@Composable
private fun JoinedTogether(state: TogetherState.Joined, together: ListenTogether, onLeave: () -> Unit) {
    val drift by together.driftMs.collectAsState()
    val latency by together.latencyMs.collectAsState()
    TogetherStage(
        title = "Вместе с «${state.host.name}»",
        text = "Ваши кнопки управляют общим плеером, а выбранный трек заиграет у всех",
        below = {
            val gap = drift
            OnPanelChip(
                when {
                    gap == null -> "сверяем часы"
                    abs(gap) <= 50 -> "играет вровень"
                    else -> "догоняем ${abs(gap)} мс"
                }
            )
        }
    ) {
        People(listOf(state.host.name, "Вы"), firstIsYou = false)
    }
    // The headphones' lag, as a panel of its own: the one thing a guest may need to set.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PanelColors.container)
            .padding(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Наушники запаздывают",
                style = MaterialTheme.typography.titleSmall,
                color = PanelColors.content,
                modifier = Modifier.weight(1f)
            )
            Text(
                "$latency мс",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = PanelColors.accent
            )
        }
        Slider(
            value = latency.toFloat(),
            onValueChange = { together.setLatency((it / 10f).roundToLong() * 10) },
            valueRange = -300f..500f,
            colors = SliderDefaults.colors(
                thumbColor = PanelColors.accent,
                activeTrackColor = PanelColors.accent,
                inactiveTrackColor = PanelColors.content.copy(alpha = 0.16f)
            )
        )
        Text(
            "Если с Bluetooth-наушниками звук отстаёт от друга, сдвиньте вправо.",
            style = MaterialTheme.typography.bodySmall,
            color = PanelColors.content.copy(alpha = 0.7f)
        )
    }
    TogetherButton("Отключиться", accent = false, onClick = onLeave)
}

// endregion

// region Parts

/**
 * The picture of a step on a panel: who is in the session (or the phone looking), over what is
 * going on. The picture keeps one height in every step, so going from one to the next the sheet
 * does not jump.
 */
@Composable
private fun TogetherStage(
    title: String,
    text: String?,
    below: (@Composable () -> Unit)? = null,
    picture: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(PanelColors.container)
            .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(136.dp),
            contentAlignment = Alignment.Center
        ) {
            picture()
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = PanelColors.content,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (text != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = PanelColors.content.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
        if (below != null) {
            Spacer(modifier = Modifier.height(14.dp))
            below()
        }
    }
}

/** Rings spreading from the middle: this phone being seen, or looking. */
@Composable
private fun NearbyPulse(icon: ImageVector) {
    val clock = rememberLoopClock()
    val color = PanelColors.accent
    Box(modifier = Modifier.size(136.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(136.dp)) {
            // Read here, as it is drawn: the rings move without composing the sheet again.
            val t = (clock.longValue % 2_400L) / 2_400f
            for (i in 0 until 3) {
                val p = (t + i / 3f) % 1f
                drawCircle(
                    color = color.copy(alpha = 0.5f * (1f - p)),
                    radius = size.minDimension / 2 * (0.42f + 0.58f * p),
                    style = Stroke(width = 3.dp.toPx())
                )
            }
        }
        StageIcon(icon)
    }
}

/** The accent disc with the step's glyph, at the middle of the stage. */
@Composable
private fun StageIcon(icon: ImageVector) {
    Surface(shape = CircleShape, color = PanelColors.accent, contentColor = PanelColors.onAccent, modifier = Modifier.size(64.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
        }
    }
}

/**
 * A phone in the session: its letter on a disc, the accent one for whoever leads, ringed in the
 * colour under it so overlapping discs stay apart.
 */
@Composable
private fun Avatar(label: String, accent: Boolean, size: Dp, ring: Color = PanelColors.container) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(ring)
            .padding(3.dp)
            .clip(CircleShape)
            .background(if (accent) PanelColors.accent else lerp(PanelColors.container, PanelColors.content, 0.2f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = if (size >= 64.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) PanelColors.onAccent else PanelColors.content,
            maxLines = 1
        )
    }
}

/**
 * Everyone in the session side by side, overlapping a little, whoever leads first and largest:
 * "Вы" for this phone, a letter for the others. Past five, the rest as "+N".
 */
@Composable
private fun People(names: List<String>, firstIsYou: Boolean) {
    val shown = names.take(5)
    val more = names.size - shown.size
    Overlapping(overlap = 16.dp) {
        shown.forEachIndexed { i, name ->
            // This phone: the leader when it hosts, the last one when it is a guest.
            val you = if (firstIsYou) i == 0 else i == shown.lastIndex
            Avatar(
                label = if (you) "Вы" else initialOf(name),
                accent = i == 0,
                size = if (i == 0) 84.dp else 64.dp
            )
        }
        if (more > 0) Avatar(label = "+$more", accent = false, size = 64.dp)
    }
}

/** Children in a row, each over the one before by [overlap], the first on top. */
@Composable
private fun Overlapping(overlap: Dp, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val step = overlap.roundToPx()
        val width = (placeables.sumOf { it.width } - step * (placeables.size - 1)).coerceAtLeast(0)
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            var x = 0
            placeables.forEachIndexed { i, placeable ->
                placeable.place(x, (height - placeable.height) / 2, zIndex = (placeables.size - i).toFloat())
                x += placeable.width - step
            }
        }
    }
}

/** This phone reaching for the host: the two of them, dots running from one to the other. */
@Composable
private fun Linking(hostName: String) {
    val clock = rememberLoopClock()
    val dot = PanelColors.accent
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(label = "Вы", accent = false, size = 64.dp)
        Canvas(modifier = Modifier.size(width = 56.dp, height = 12.dp)) {
            val t = (clock.longValue % 1_200L) / 1_200f
            val r = size.height / 2
            for (i in 0 until 3) {
                // Each dot lights in turn, from this phone towards the host.
                val phase = ((t - i / 3f) % 1f + 1f) % 1f
                val glow = sin(phase * Math.PI).toFloat().coerceAtLeast(0f)
                drawCircle(
                    color = dot.copy(alpha = 0.25f + 0.75f * glow),
                    radius = r * (0.7f + 0.3f * glow),
                    center = Offset(r + i * (size.width - 2 * r) / 2, size.height / 2)
                )
            }
        }
        Avatar(label = initialOf(hostName), accent = true, size = 84.dp)
    }
}

/** The pairing code both screens show, a digit a box: what two people read out to each other. */
@Composable
private fun CodeDigits(code: String, boxColor: Color = PanelColors.content.copy(alpha = 0.1f)) {
    val digitColor = PanelColors.accent
    if (code.length > 6) {
        Text(code, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = digitColor)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        code.forEach { char ->
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(boxColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    char.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = digitColor
                )
            }
        }
    }
}

/** The phones of a step — guests in, hosts found — on one panel, a row each. */
@Composable
private fun PeopleList(
    kicker: String?,
    people: List<TogetherPeer>,
    onClick: ((TogetherPeer) -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (kicker != null) Kicker(text = kicker, modifier = Modifier.padding(start = 12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(PanelColors.container)
        ) {
            people.forEachIndexed { i, peer ->
                if (i > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 72.dp, end = 14.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(PanelColors.content.copy(alpha = 0.08f))
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (onClick != null) Modifier.clickable { onClick(peer) } else Modifier)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Avatar(label = initialOf(peer.name), accent = onClick != null, size = 44.dp)
                    Text(
                        peer.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = PanelColors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    trailing()
                }
            }
        }
    }
}

/** One of the two ways in: its glyph on a disc, its name and what it does. */
@Composable
private fun TogetherTile(
    icon: ImageVector,
    title: String,
    text: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = if (accent) PanelColors.onAccent else PanelColors.content
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = if (accent) PanelColors.accent else PanelColors.container,
        contentColor = content
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (accent) PanelColors.onAccent.copy(alpha = 0.12f) else PanelColors.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PanelColors.onAccent, modifier = Modifier.size(26.dp))
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f))
        }
    }
}

/** A step's action, the width of the sheet: the accent to go on, the panel tone to leave. */
@Composable
private fun TogetherButton(
    text: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    container: Color = if (accent) PanelColors.accent else PanelColors.container,
    content: Color = if (accent) PanelColors.onAccent else PanelColors.content
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// endregion

/**
 * A guest asking the host in: who, and the code both screens show — large, a digit a box, to be
 * compared at a glance; the two answers side by side, as wide as each other.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TogetherRequestDialog(request: TogetherRequest, onAnswer: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BasicAlertDialog(onDismissRequest = { onAnswer(false) }) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(label = initialOf(request.peer.name), accent = true, size = 72.dp, ring = colors.surfaceContainerHigh)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "«${request.peer.name}»",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "хочет слушать вместе",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Kicker(text = "Код")
                Spacer(modifier = Modifier.height(10.dp))
                CodeDigits(request.code, boxColor = colors.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Пустите, если у друга на экране такой же",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(22.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TogetherButton(
                        "Не пускать",
                        accent = false,
                        onClick = { onAnswer(false) },
                        modifier = Modifier.weight(1f),
                        container = colors.onSurface.copy(alpha = 0.08f),
                        content = colors.onSurface
                    )
                    TogetherButton("Пустить", accent = true, onClick = { onAnswer(true) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
