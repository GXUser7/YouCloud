package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.social.Person
import com.example.myapplication.data.social.Relation
import com.example.myapplication.data.social.Social
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.delay

/*
 * Home's avatar button and what it opens: Material 3 Expressive's FAB menu — the friends playing
 * something now, the requests, the friends, the profile, the settings — standing over the button,
 * under the thumb. Tapped, the button opens it; pressed and slid up, the finger picks an item and
 * letting go opens it: one movement to the settings, or to a friend.
 */

/** Whether the menu is open, and which item a finger slid up from the button is over. */
@Stable
internal class AccountMenuState {
    var open by mutableStateOf(false)
        private set

    /** The item under a finger slid up from the button, while it is still down. */
    var hovered by mutableStateOf<String?>(null)
        internal set

    // Where the items shown are on the screen, and the button, for that finger.
    internal val bounds = mutableMapOf<String, Rect>()
    internal var buttonOrigin = Offset.Zero

    fun show() {
        open = true
    }

    fun close() {
        open = false
        hovered = null
    }

    fun toggle() = if (open) close() else show()

    /**
     * The item at [point] (in the root): the bands the items stand in, gaps shared out between
     * them, from a little left of them to the screen's edge — over the search button too, for a
     * finger that drifts right on its way up.
     */
    internal fun itemAt(point: Offset, slop: Float): String? = bounds.entries.firstOrNull { (_, item) ->
        point.y in (item.top - slop)..(item.bottom + slop) && point.x >= item.left - slop * 6
    }?.key
}

@Composable
internal fun rememberAccountMenuState(): AccountMenuState = remember { AccountMenuState() }

/** One of the menu's items; [person], a friend playing something now, shows as them, on the accent. */
@Immutable
internal class AccountMenuItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val person: Person? = null,
    val avatarUrl: String? = null,
    val line: String? = null,
    val count: Int = 0,
    // The count asks for attention (requests waiting), rather than only saying how many.
    val urgent: Boolean = false,
    val onClick: () -> Unit
)

private const val MAX_LISTENING = 3
private val ItemHeight = 56.dp
private val ItemGap = 8.dp

/**
 * The menu's items, top to bottom: the friends playing something now, the requests waiting, the
 * friends, the profile, the settings — the settings nearest the thumb. Signed out: signing in, and
 * the settings.
 */
@Composable
internal fun accountMenuItems(
    open: Boolean,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFriends: (requests: Boolean) -> Unit,
    onOpenPerson: (String) -> Unit
): List<AccountMenuItem> {
    val context = LocalContext.current
    val social = remember { Social.get(context) }
    val session by social.session.collectAsState()
    val me by social.me.collectAsState()
    val all by social.friends.collectAsState()
    // What friends play: asked again as the menu opens, and told as it changes while it is open.
    LaunchedEffect(open, session?.userId) {
        if (open && session != null) social.reloadFriends()
    }
    DisposableEffect(open, session?.userId) {
        val live = if (open) social.watchLive() else null
        onDispose { live?.close() }
    }
    val now = remember(open, all) { System.currentTimeMillis() }
    val settings = AccountMenuItem("settings", tr("Настройки"), Icons.Rounded.Settings, onClick = onOpenSettings)
    if (session == null || me == null) {
        return listOf(AccountMenuItem("profile", tr("Войти"), Icons.AutoMirrored.Rounded.Login, onClick = onOpenProfile), settings)
    }
    val people = all.orEmpty()
    val friends = people.filter { it.relationKind == Relation.FRIEND }
    val incoming = people.count { it.relationKind == Relation.INCOMING }
    return buildList {
        friends.filter { it.listeningNow(now) }
            .sortedByDescending { it.updatedAtMs ?: 0L }
            .take(MAX_LISTENING)
            .forEach { person ->
                add(
                    AccountMenuItem(
                        key = "person-${person.id}",
                        label = person.shownName,
                        person = person,
                        avatarUrl = social.avatarUrl(person.id, person.avatarV),
                        line = person.trackLine(now),
                        onClick = { onOpenPerson(person.id) }
                    )
                )
            }
        if (incoming > 0) {
            add(AccountMenuItem("requests", tr("Заявки"), Icons.Rounded.PersonAdd, count = incoming, urgent = true, onClick = { onOpenFriends(true) }))
        }
        add(AccountMenuItem("friends", tr("Друзья"), Icons.Rounded.Group, count = friends.size, onClick = { onOpenFriends(false) }))
        add(AccountMenuItem("profile", tr("Профиль"), Icons.Rounded.AccountCircle, onClick = onOpenProfile))
        add(settings)
    }
}

/**
 * The menu's button, in place of the profile's: one's avatar ([ProfileGlyph]) while the menu is
 * shut, a close mark on the accent once it is open, the rounded square turning round between the
 * two, as the FAB menu's button does. [fill]: its glass's tone, the search button's.
 */
@Composable
internal fun AccountMenuButton(
    state: AccountMenuState,
    items: List<AccountMenuItem>,
    fill: Color,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val glass = LocalGlass.current
    val openness by animateFloatAsState(
        targetValue = if (state.open) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "accountMenuButton"
    )
    val shape = RoundedCornerShape(androidx.compose.ui.unit.lerp(20.dp, 32.dp, openness.coerceIn(0f, 1f)))
    val currentItems by rememberUpdatedState(items)
    val description = tr("Профиль и друзья")
    Surface(
        modifier = modifier
            .size(64.dp)
            .onGloballyPositioned { state.buttonOrigin = it.positionInRoot() }
            .glassOr(shape, fill)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                onClick {
                    state.toggle()
                    true
                }
            }
            .pointerInput(state) { pressAndSlide(state, { currentItems }, haptic) },
        shape = shape,
        color = lerp(if (glass) Color.Transparent else fill, PanelColors.accent, openness.coerceIn(0f, 1f)),
        contentColor = PanelColors.accent,
        shadowElevation = if (glass) 0.dp else 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = (1f - openness * 2f).coerceIn(0f, 1f)
                    val scale = 1f - 0.3f * openness.coerceIn(0f, 1f)
                    scaleX = scale
                    scaleY = scale
                }
            ) {
                ProfileGlyph()
            }
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = PanelColors.onAccent,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        alpha = (openness * 2f - 1f).coerceIn(0f, 1f)
                        rotationZ = (1f - openness) * -90f
                    }
            )
        }
    }
}

/**
 * The button's touch: a tap opens the menu or shuts it; held, or slid off the button, the menu
 * opens under the finger, the item it is over lights up, and letting go there opens that item.
 * Let go over the button, the menu stays, for a tap; anywhere else, it is called off.
 */
private suspend fun PointerInputScope.pressAndSlide(
    state: AccountMenuState,
    items: () -> List<AccountMenuItem>,
    haptic: HapticFeedback
) {
    val band = (ItemGap / 2).toPx()
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        // A tap (true), or a finger that moved off or stayed down (false, or null: held).
        val tapped = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var result: Boolean? = null
            while (result == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                result = when {
                    change == null -> true
                    !change.pressed -> {
                        change.consume()
                        true
                    }
                    (change.position - down.position).getDistance() > viewConfiguration.touchSlop -> false
                    else -> null
                }
            }
            result
        }
        if (tapped == true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            state.toggle()
            return@awaitEachGesture
        }
        if (!state.open) {
            state.show()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        var last = down.position
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            last = change.position
            if (!change.pressed) break
            change.consume()
            val over = state.itemAt(state.buttonOrigin + change.position, band)
            if (over != state.hovered) {
                state.hovered = over
                if (over != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
        val picked = state.hovered?.let { key -> items().firstOrNull { it.key == key } }
        val overButton = last.x in 0f..size.width.toFloat() && last.y in -band..size.height.toFloat()
        when {
            picked != null -> {
                state.close()
                picked.onClick()
            }
            overButton -> state.hovered = null
            else -> state.close()
        }
    }
}

/**
 * The open menu over home: home dimmed under it, and the items standing over the button, coming up
 * from it one after the other, the nearest first. A tap outside, or "back", shuts it. [placement]
 * puts the items' column with its bottom-end corner at the button's top-end one.
 */
@Composable
internal fun BoxScope.AccountMenuOverlay(
    state: AccountMenuState,
    items: List<AccountMenuItem>,
    placement: Modifier
) {
    val scrim by animateFloatAsState(if (state.open) 1f else 0f, tween(220), label = "accountMenuScrim")
    BackHandler(enabled = state.open) { state.close() }
    if (!state.open && scrim == 0f) return
    Box(
        modifier = Modifier
            .matchParentSize()
            .drawBehind { drawRect(Color.Black, alpha = 0.45f * scrim) }
            .then(
                if (state.open) {
                    Modifier.pointerInput(state) {
                        awaitEachGesture {
                            awaitFirstDown().consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            state.close()
                        }
                    }
                } else {
                    Modifier
                }
            )
    )
    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        // What fits over the button, sideways too: the friends playing go first when not all does.
        val fits = ((maxHeight - 200.dp) / (ItemHeight + ItemGap)).toInt().coerceAtLeast(2)
        val shown = items.takeLast(fits)
        Column(
            modifier = placement,
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(ItemGap)
        ) {
            shown.forEachIndexed { index, item ->
                val fromButton = shown.size - 1 - index
                val appear = remember(item.key) { Animatable(0f) }
                LaunchedEffect(item.key, state.open) {
                    if (state.open) {
                        delay(fromButton * 30L)
                        appear.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 600f))
                    } else {
                        appear.animateTo(0f, spring(stiffness = 1500f))
                    }
                }
                DisposableEffect(item.key) { onDispose { state.bounds.remove(item.key) } }
                AccountMenuPill(
                    item = item,
                    hovered = state.hovered == item.key,
                    appear = { appear.value },
                    onClick = {
                        state.close()
                        item.onClick()
                    },
                    modifier = Modifier.onGloballyPositioned { state.bounds[item.key] = it.boundsInRoot() }
                )
            }
        }
    }
}

/**
 * An item: a pill of glass with its mark and name, or — a friend playing something now, or the
 * item a finger is over — one of the accent. It grows from the button's corner as it comes in.
 */
@Composable
private fun AccountMenuPill(
    item: AccountMenuItem,
    hovered: Boolean,
    appear: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lift by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "accountMenuPillLift"
    )
    val accent = item.person != null || hovered
    val container = if (accent) PanelColors.accent else PanelColors.container
    val content = if (accent) PanelColors.onAccent else PanelColors.content
    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                val shown = appear()
                alpha = shown.coerceIn(0f, 1f)
                val scale = (0.6f + 0.4f * shown) * (1f + 0.06f * lift)
                scaleX = scale
                scaleY = scale
                translationY = (1f - shown) * 24.dp.toPx()
                transformOrigin = TransformOrigin(1f, 1f)
            }
            .height(ItemHeight)
            .widthIn(max = 300.dp)
            .then(if (accent) Modifier else Modifier.glassOr(CircleShape, container)),
        shape = CircleShape,
        color = if (accent) container else glassFill(container),
        contentColor = content
    ) {
        val person = item.person
        Row(
            modifier = Modifier.padding(start = if (person != null) 8.dp else 18.dp, end = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (person != null) {
                PersonAvatar(name = person.shownName, color = person.color, imageUrl = item.avatarUrl, size = 40.dp)
                Column(modifier = Modifier.widthIn(max = 220.dp)) {
                    Text(item.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.line?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = content.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } else {
                item.icon?.let { Icon(it, contentDescription = null, tint = if (accent) content else PanelColors.accent, modifier = Modifier.size(24.dp)) }
                Text(item.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                if (item.count > 0) {
                    if (item.urgent && !accent) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                                .clip(CircleShape)
                                .background(PanelColors.accent)
                                .padding(horizontal = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(item.count.toString(), style = MaterialTheme.typography.labelMedium, color = PanelColors.onAccent)
                        }
                    } else {
                        Text(item.count.toString(), style = MaterialTheme.typography.titleMedium, color = content.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}
