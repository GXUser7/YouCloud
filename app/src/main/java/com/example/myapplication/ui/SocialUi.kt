@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.myapplication.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil.compose.AsyncImage
import com.example.myapplication.data.social.Person
import com.example.myapplication.data.social.Relation
import com.example.myapplication.data.social.Social
import com.example.myapplication.i18n.tr

// region Shapes and avatars

/** A Material shape (normalised into the unit square) as a Compose [Shape], to clip by. */
internal class PolygonShape(private val polygon: RoundedPolygon, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath()
        val matrix = android.graphics.Matrix().apply {
            setTranslate(-0.5f, -0.5f)
            postRotate(rotation)
            postScale(size.width, size.height)
            postTranslate(size.width / 2f, size.height / 2f)
        }
        path.transform(matrix)
        return Outline.Generic(path.asComposePath())
    }
}

/** Everyone's avatar is cut to this: the scalloped cookie of the app's backdrop. */
internal val AvatarShape: Shape by lazy { PolygonShape(MaterialShapes.Cookie12Sided) }

/** One's own, larger, on the profile: the sun of "Моя форма". */
internal val OwnAvatarShape: Shape by lazy { PolygonShape(MaterialShapes.Sunny) }

/** The colours an avatar without a picture can be. */
internal val AvatarColors = listOf("#C98B6B", "#8FA6C9", "#B79AD1", "#9CC2A2", "#D9B36C", "#E39A8E")

internal fun colorOf(hex: String?): Color? = hex?.let {
    runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
}

/**
 * Someone's avatar: their picture, cut to [shape]; or, without one, the first letter of their
 * name on their colour.
 */
@Composable
internal fun PersonAvatar(
    name: String,
    color: String?,
    imageUrl: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = AvatarShape
) {
    val fill = colorOf(color) ?: MaterialTheme.colorScheme.primaryContainer
    val ink = if (fill.luminance() > 0.4f) Color(0xFF2A1D14) else Color.White
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.displaySmall.copy(fontSize = (size.value * 0.38f).sp, lineHeight = (size.value * 0.44f).sp),
            color = ink
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

/** The little mark on an avatar of someone playing something right now. */
@Composable
internal fun ListeningBadge(size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(2.dp)
            .clip(CircleShape)
            .background(PanelColors.accent),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.GraphicEq, contentDescription = tr("Сейчас слушает"), tint = PanelColors.onAccent, modifier = Modifier.size(size * 0.6f))
    }
}

/** A person's avatar with the mark when they are playing something now. */
@Composable
internal fun PersonAvatarWithBadge(person: Person, avatarUrl: String?, size: Dp, listening: Boolean, dimmed: Boolean = false) {
    Box(modifier = Modifier.size(size)) {
        PersonAvatar(
            name = person.shownName,
            color = person.color,
            imageUrl = avatarUrl,
            size = size,
            modifier = if (dimmed) Modifier.graphicsLayer { alpha = 0.55f } else Modifier
        )
        if (listening) ListeningBadge(size = size * 0.46f, modifier = Modifier.align(Alignment.BottomEnd).offset(x = 3.dp, y = 3.dp))
    }
}

/**
 * Home's button for the profile: one's avatar once signed in, with a dot while friend requests
 * wait; a figure before.
 */
@Composable
internal fun ProfileGlyph() {
    val context = LocalContext.current
    val social = remember { Social.get(context) }
    val session by social.session.collectAsState()
    val me by social.me.collectAsState()
    val friends by social.friends.collectAsState()
    // Once a start, for the dot: the friends screen asks again on its own.
    LaunchedEffect(session?.userId) { if (session != null) social.reloadFriends() }
    val profile = me
    if (session == null || profile == null) {
        Icon(Icons.Rounded.Person, contentDescription = tr("Профиль"), modifier = Modifier.size(28.dp))
        return
    }
    val waiting = friends.orEmpty().any { it.relationKind == Relation.INCOMING }
    val description = tr("Профиль")
    Box(modifier = Modifier.size(44.dp).semantics { contentDescription = description }) {
        PersonAvatar(
            name = profile.name?.takeIf { it.isNotBlank() } ?: profile.nick,
            color = profile.color,
            imageUrl = social.avatarUrl(profile.id, profile.avatarV),
            size = 44.dp
        )
        if (waiting) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(PanelColors.accent)
            )
        }
    }
}

// endregion

// region Page

/**
 * A page of the account: over the backdrop as glass, closed by pulling it down as every page is,
 * with no back button; its content a list under a big title.
 */
@Composable
internal fun SocialPage(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    bottomPadding: Dp = 140.dp,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    content: LazyListScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pullToClose(onClose)
            .pageGlass(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, top = 28.dp, end = 16.dp, bottom = bottomPadding)
        ) {
            if (title.isNotEmpty()) item(key = "page-title") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(start = 4.dp, bottom = 20.dp)
                )
            }
            content()
        }
        overlay()
    }
}

/**
 * A [SocialPage] of pages side by side, swiped between anywhere on the screen: the title stays put,
 * each page is its own list, edge to edge as it slides, and what picks them goes in [overlay], at
 * the foot, as search's tabs.
 */
@Composable
internal fun SocialPagedPage(
    title: String,
    onClose: () -> Unit,
    pager: androidx.compose.foundation.pager.PagerState,
    bottomPadding: Dp = 140.dp,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
    page: LazyListScope.(Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToClose(onClose)
            .pageGlass(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 20.dp, top = 28.dp, end = 16.dp, bottom = 8.dp)
            )
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalAlignment = Alignment.Top
            ) { index ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding)
                ) {
                    page(index)
                }
            }
        }
        overlay()
    }
}

/** A rule with what follows written on it: "Сейчас слушают · 3 ———". */
@Composable
internal fun SectionRule(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 22.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = PanelColors.accent, maxLines = 1)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(PanelColors.accent.copy(alpha = 0.3f))
        )
    }
}

/**
 * A row of a group, as Material 3 Expressive lists have them now: each its own glass card, set
 * a little apart, rounded fully at the group's ends and only slightly between.
 */
internal fun groupedShape(index: Int, count: Int, outer: Dp = 28.dp, inner: Dp = 8.dp): Shape = when {
    count <= 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
    else -> RoundedCornerShape(inner)
}

@Composable
internal fun GroupedCard(
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val fill = PanelColors.container
    val inner: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content
        )
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().glassOr(shape, fill),
            shape = shape,
            color = glassFill(fill),
            contentColor = PanelColors.content,
            content = inner
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth().glassOr(shape, fill),
            shape = shape,
            color = glassFill(fill),
            contentColor = PanelColors.content,
            content = inner
        )
    }
}

// endregion

// region Controls

enum class PillKind { Primary, Tonal, Glass }

/** The account's buttons: wide pills, the main action in the accent. */
@Composable
internal fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    kind: PillKind = PillKind.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = 60.dp,
    contentColor: Color? = null
) {
    val haptic = LocalHapticFeedback.current
    val (container, content) = when (kind) {
        PillKind.Primary -> PanelColors.accent to PanelColors.onAccent
        PillKind.Tonal -> PanelColors.accent.copy(alpha = 0.18f) to PanelColors.accent
        PillKind.Glass -> PanelColors.container to PanelColors.content
    }
    val shape = RoundedCornerShape(height / 2)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = enabled && !loading,
        modifier = modifier
            .height(height)
            .then(if (kind == PillKind.Glass) Modifier.glassOr(shape, container) else Modifier),
        shape = shape,
        color = if (kind == PillKind.Glass) glassFill(container) else container.copy(alpha = if (enabled) container.alpha else container.alpha * 0.4f),
        contentColor = (contentColor ?: content).copy(alpha = if (enabled) 1f else 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                AppLoadingIndicator(modifier = Modifier.size(28.dp), color = contentColor ?: content)
            } else {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A square glass button with an icon in the accent, as home's. */
@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    tint: Color = PanelColors.accent
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(size * 0.34f)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .size(size)
            .glassOr(shape, PanelColors.container),
        shape = shape,
        color = glassFill(PanelColors.container),
        contentColor = tint
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
        }
    }
}

/** A text field of the account's forms: a glass pill, outlined in the accent while typed into. */
@Composable
internal fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onIme: () -> Unit = {},
    isError: Boolean = false,
    large: Boolean = false
) {
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    val glass = LocalGlass.current
    var shown by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (large) 36.dp else 30.dp)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .glassOr(shape, fill),
        singleLine = true,
        shape = shape,
        textStyle = if (large) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.titleMedium,
        // A placeholder, as search's field has, not a floating label: the label lifts the outline
        // below the field's top while the glass is cut to the whole field, and the two part.
        placeholder = { Text(label, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium) },
        leadingIcon = leading,
        isError = isError,
        trailingIcon = when {
            password -> {
                {
                    IconButton(onClick = { shown = !shown }) {
                        Icon(
                            if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (shown) tr("Скрыть пароль") else tr("Показать пароль")
                        )
                    }
                }
            }
            value.isNotEmpty() && !password -> {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("Очистить"))
                    }
                }
            }
            else -> null
        },
        visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = imeAction,
            autoCorrectEnabled = false
        ),
        keyboardActions = KeyboardActions(onAny = { onIme() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (glass) Color.Transparent else fill,
            unfocusedContainerColor = if (glass) Color.Transparent else fill,
            errorContainerColor = if (glass) Color.Transparent else fill,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

/** "@" before a nick, in the accent. */
@Composable
internal fun AtMark() {
    Text("@", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = PanelColors.accent)
}

/** A message under a form: what went wrong, in the error colour. */
@Composable
internal fun FormError(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}

// endregion

// region Words

/**
 * The word for [count] of something, in Russian's three forms; in English [one] or [many], the
 * forms passed already translated.
 */
internal fun countWord(count: Long, one: String, few: String, many: String): String {
    if (com.example.myapplication.i18n.english()) return if (count == 1L) one else many
    val mod10 = count % 10
    val mod100 = count % 100
    return when {
        mod10 == 1L && mod100 != 11L -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
}

/** What is sent to someone to be friends: the nick, and where to get the app. */
internal fun inviteText(nick: String): String =
    tr("Добавь меня в друзья в YouCloud: @%s", nick) + "\n" +
        tr("Скачать: %s", "https://github.com/GXUser7/YouCloud/releases/latest")

/** The service a track plays from, as named on screen. */
internal fun serviceName(service: String?): String? = when (service) {
    "soundcloud" -> "SoundCloud"
    "youtube" -> "YouTube"
    "yandex" -> tr("Яндекс Музыка")
    "device" -> tr("На устройстве")
    else -> null
}

/** How long ago [ms] was, briefly: "5 мин назад", "вчера". */
internal fun timeAgo(ms: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - ms) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> tr("только что")
        minutes < 60 -> tr("%s мин назад", minutes)
        minutes < 24 * 60 -> tr("%s ч назад", minutes / 60)
        minutes < 48 * 60 -> tr("вчера")
        else -> tr("%s дн. назад", minutes / (24 * 60))
    }
}

/** Under a friend's name: what plays now, or what played last and when. */
internal fun Person.trackLine(now: Long = System.currentTimeMillis()): String? {
    val track = title?.takeIf { it.isNotBlank() } ?: return null
    val what = if (artist.isNullOrBlank()) track else "$track — $artist"
    if (listeningNow(now)) return what
    val at = updatedAtMs ?: return what
    return "${timeAgo(at, now)} · $what"
}

// endregion
