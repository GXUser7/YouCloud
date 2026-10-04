@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.example.myapplication.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.data.social.Profile
import com.example.myapplication.data.social.Relation
import com.example.myapplication.data.social.RecoveryCode
import com.example.myapplication.data.social.Social
import com.example.myapplication.data.social.socialMessage
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.PaddingValues
import com.example.myapplication.data.SoundCloudTrack
import com.example.myapplication.data.social.ShowcaseSlot
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthMode { Landing, SignUp, SignIn, Recover }

private val NICK_PATTERN = Regex("^[a-z0-9_.]{3,20}$")

/**
 * The profile, opened from home's button where settings were: one's own, with the friends and
 * the app's settings; or, signed out, the way in — a new account step by step, or signing in.
 */
@Composable
internal fun ProfileScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFriends: (requests: Boolean) -> Unit,
    onOpenPerson: (String) -> Unit,
    // What plays here, for the showcase; a tap there opens the player.
    nowPlaying: SoundCloudTrack? = null,
    isPlaying: Boolean = false,
    onOpenNowPlaying: () -> Unit = {},
    // A place of the showcase to pick in search; a picked one's page, opened as a shared link.
    onPickShowcase: (ShowcaseSlot) -> Unit = {},
    onPlayLink: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val social = remember { Social.get(context) }
    val session by social.session.collectAsState()
    var mode by rememberSaveable { mutableStateOf(AuthMode.Landing) }

    // Signing up goes on past the account being made, to its recovery code: it decides when it ends.
    if (mode == AuthMode.SignUp) {
        BackHandler { mode = AuthMode.Landing }
        SignUpFlow(
            social = social,
            onClose = onClose,
            onSignIn = { mode = AuthMode.SignIn },
            onDone = { mode = AuthMode.Landing }
        )
        return
    }
    if (session != null) {
        OwnProfile(
            social = social,
            onClose = onClose,
            onOpenSettings = onOpenSettings,
            onOpenFriends = onOpenFriends,
            onOpenPerson = onOpenPerson,
            nowPlaying = nowPlaying,
            isPlaying = isPlaying,
            onOpenNowPlaying = onOpenNowPlaying,
            onPickShowcase = onPickShowcase,
            onPlayLink = onPlayLink
        )
        return
    }
    when (mode) {
        AuthMode.SignIn, AuthMode.Recover -> {
            BackHandler { mode = if (mode == AuthMode.Recover) AuthMode.SignIn else AuthMode.Landing }
            SignInPage(
                social = social,
                recovering = mode == AuthMode.Recover,
                onRecover = { mode = if (it) AuthMode.Recover else AuthMode.SignIn },
                onSignUp = { mode = AuthMode.SignUp },
                onClose = onClose,
                onSignedIn = { mode = AuthMode.Landing }
            )
        }
        else -> SignedOutProfile(
            configured = social.configured,
            onClose = onClose,
            onSignUp = { mode = AuthMode.SignUp },
            onSignIn = { mode = AuthMode.SignIn },
            onOpenSettings = onOpenSettings
        )
    }
}

// region Signed out

@Composable
private fun SignedOutProfile(
    configured: Boolean,
    onClose: () -> Unit,
    onSignUp: () -> Unit,
    onSignIn: () -> Unit,
    onOpenSettings: () -> Unit
) {
    SocialPage(title = "", onClose = onClose) {
        item(key = "hero") {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(24.dp))
                ShapeHero(icon = Icons.Rounded.Person)
                Spacer(modifier = Modifier.height(26.dp))
                Text(tr("Профиль"), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    tr("Войди, чтобы добавлять друзей и видеть, что они слушают. Нужны только ник и пароль"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
        item(key = "actions") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!configured) {
                    Text(
                        tr("В этой сборке аккаунты не настроены"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                PillButton(tr("Создать аккаунт"), onSignUp, Modifier.fillMaxWidth(), icon = Icons.Rounded.PersonAdd, enabled = configured)
                PillButton(tr("Войти"), onSignIn, Modifier.fillMaxWidth(), kind = PillKind.Glass, enabled = configured)
                Spacer(modifier = Modifier.height(14.dp))
                PillButton(tr("Настройки"), onOpenSettings, Modifier.fillMaxWidth(), icon = Icons.Rounded.Settings, kind = PillKind.Glass)
            }
        }
    }
}

/** The big shape at the top of the way in: the cookie of "Моя форма", a glyph in its middle. */
@Composable
private fun ShapeHero(icon: androidx.compose.ui.graphics.vector.ImageVector, size: Dp = 168.dp) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(AvatarShape)
                .background(PanelColors.accent)
        )
        Box(
            modifier = Modifier
                .size(size * 0.78f)
                .clip(PolygonShape(MaterialShapes.Cookie12Sided, rotation = 15f))
                .background(Color.White.copy(alpha = 0.22f))
        )
        Box(
            modifier = Modifier
                .size(size * 0.42f)
                .clip(CircleShape)
                .background(PanelColors.onAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PanelColors.accent, modifier = Modifier.size(size * 0.2f))
        }
    }
}

// endregion

// region Signing up, step by step

/**
 * A new account in four steps, one thing each: the nick (checked as it is typed), the password,
 * the avatar — and, the account made, the recovery code, the one way back in without an email.
 */
@Composable
private fun SignUpFlow(
    social: Social,
    onClose: () -> Unit,
    onSignIn: () -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var nick by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf(AvatarColors[3]) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Whether the nick is free, asked a moment after typing stops: null while unknown.
    var free by remember { mutableStateOf<Boolean?>(null) }
    val nickValid = NICK_PATTERN.matches(nick)
    LaunchedEffect(nick) {
        free = null
        if (!nickValid) return@LaunchedEffect
        delay(400)
        free = runCatching { social.nickAvailable(nick) }.getOrNull()
    }

    // Back steps back, until the account is made: from the code there is no going back.
    BackHandler(enabled = step in 1..2) { step-- }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photo = uri.toString()
    }

    fun create() {
        busy = true
        error = null
        scope.launch {
            try {
                code = social.signUp(nick, password, name.ifBlank { nick }, color)
                photo?.let { picked ->
                    runCatching { social.setAvatar(Uri.parse(picked)) }.onFailure {
                        Toast.makeText(context, tr("Фото не загрузилось: %s", it.socialMessage()), Toast.LENGTH_LONG).show()
                    }
                }
                step = 3
            } catch (e: Exception) {
                error = e.socialMessage()
            } finally {
                busy = false
            }
        }
    }

    val canGo = when (step) {
        0 -> nickValid && free == true
        1 -> password.length >= 8
        else -> true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToClose(onClose)
            .pageGlass(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.End
            ) {
                StepShapes(current = step, count = 4)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 36.dp)
            ) {
                Kicker(text = tr("Шаг %s из %s", step + 1, 4))
                Spacer(modifier = Modifier.height(10.dp))
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                            (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
                    },
                    label = "signUpStep"
                ) { shown ->
                    Column {
                        when (shown) {
                            0 -> NickStep(nick, { nick = it.lowercase().filterNot(Char::isWhitespace).take(20); error = null }, nickValid, free) {
                                nick = it
                            }
                            1 -> PasswordStep(password) { password = it }
                            2 -> AvatarStep(
                                nick = nick,
                                name = name,
                                onName = { name = it.take(40) },
                                color = color,
                                onColor = { color = it },
                                photo = photo,
                                onPickPhoto = {
                                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                                onRemovePhoto = { photo = null }
                            )
                            else -> CodeStep(code.orEmpty())
                        }
                    }
                }
                FormError(error, Modifier.padding(top = 14.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (step == 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Уже есть аккаунт?"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onSignIn) { Text(tr("Войти")) }
                        }
                    }
                }
                StepFab(
                    label = when (step) {
                        2 -> tr("Создать")
                        3 -> tr("Готово")
                        else -> null
                    },
                    enabled = canGo,
                    busy = busy,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        when (step) {
                            0, 1 -> step++
                            2 -> create()
                            else -> onDone()
                        }
                    }
                )
            }
        }
    }
}

/** The steps as shapes: a cookie for each done, a sun for this one, a ring for each to come. */
@Composable
private fun StepShapes(current: Int, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(count) { index ->
            val size by animateDpAsState(if (index == current) 34.dp else if (index < current) 22.dp else 14.dp, label = "stepSize")
            when {
                index < current -> Box(Modifier.size(size).clip(PolygonShape(MaterialShapes.Cookie9Sided)).background(PanelColors.accent))
                index == current -> Box(Modifier.size(size).clip(PolygonShape(MaterialShapes.Sunny)).background(PanelColors.accent))
                else -> Box(
                    Modifier
                        .size(size)
                        .border(2.dp, PanelColors.content.copy(alpha = 0.3f), CircleShape)
                )
            }
        }
    }
}

/** The big button that moves the steps on: a square with an arrow, or a pill with a word. */
@Composable
private fun StepFab(label: String?, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.height(84.dp).then(if (label == null) Modifier.width(84.dp) else Modifier),
        shape = RoundedCornerShape(28.dp),
        color = PanelColors.accent.copy(alpha = if (enabled) 1f else 0.38f),
        contentColor = PanelColors.onAccent
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = if (label == null) 0.dp else 30.dp)) {
            when {
                busy -> AppLoadingIndicator(modifier = Modifier.size(40.dp), color = PanelColors.onAccent)
                label == null -> Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = tr("Дальше"), modifier = Modifier.size(32.dp))
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.displaySmall)
    Spacer(modifier = Modifier.height(10.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(26.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NickStep(nick: String, onNick: (String) -> Unit, valid: Boolean, free: Boolean?, onPick: (String) -> Unit) {
    StepTitle(tr("Как тебя найдут друзья?"), tr("Ник — это и логин, и то, по чему тебя ищут. Латиница, цифры, «_» и «.»"))
    GlassField(
        value = nick,
        onValueChange = onNick,
        label = tr("Ник"),
        leading = { AtMark() },
        // A keyboard with Latin letters: a nick has nothing else.
        keyboardType = KeyboardType.Ascii,
        large = true,
        imeAction = ImeAction.Next,
        isError = valid && free == false
    )
    Spacer(modifier = Modifier.height(12.dp))
    val (text, color) = when {
        nick.isEmpty() -> tr("От 3 до 20 символов") to MaterialTheme.colorScheme.onSurfaceVariant
        !valid -> tr("От 3 до 20 символов: латиница, цифры, «_» и «.»") to MaterialTheme.colorScheme.onSurfaceVariant
        free == null -> tr("Проверяем…") to MaterialTheme.colorScheme.onSurfaceVariant
        free -> tr("@%s свободен", nick) to PanelColors.accent
        else -> tr("@%s уже занят, вот свободные:", nick) to MaterialTheme.colorScheme.error
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(horizontal = 10.dp))
    if (valid && free == false) {
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("${nick}_7", "$nick.music", "its.$nick").filter { NICK_PATTERN.matches(it) }.forEach { suggestion ->
                Surface(
                    onClick = { onPick(suggestion) },
                    shape = CircleShape,
                    color = PanelColors.content.copy(alpha = 0.1f),
                    contentColor = PanelColors.content
                ) {
                    Text("@$suggestion", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}

@Composable
private fun PasswordStep(password: String, onPassword: (String) -> Unit) {
    StepTitle(tr("Придумай пароль"), tr("Почты у аккаунта нет, так что пароль лучше сохранить в менеджере паролей"))
    GlassField(
        value = password,
        onValueChange = onPassword,
        label = tr("Пароль"),
        leading = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = PanelColors.accent) },
        password = true,
        large = true,
        imeAction = ImeAction.Done
    )
    val hasDigit = password.any(Char::isDigit)
    val hasUpper = password.any(Char::isUpperCase)
    var score = 0
    if (password.length >= 8) score++
    if (hasDigit) score++
    if (hasUpper) score++
    if (password.length >= 12) score++
    if (password.length < 8) score = minOf(score, 1)
    Spacer(modifier = Modifier.height(20.dp))
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        Text(tr("Надёжность"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(
            when {
                password.length < 8 -> tr("Слишком короткий")
                score >= 4 -> tr("Надёжный")
                score == 3 -> tr("Хороший")
                score == 2 -> tr("Нормальный")
                else -> tr("Слабый")
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (password.length < 8) MaterialTheme.colorScheme.error else if (score >= 3) PanelColors.accent else PanelColors.content
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    AppLinearProgress(
        progress = listOf(0.06f, 0.28f, 0.54f, 0.8f, 1f)[score],
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        color = PanelColors.accent
    )
    Spacer(modifier = Modifier.height(20.dp))
    listOf(
        tr("Не короче 8 символов") to (password.length >= 8),
        tr("Есть цифра") to hasDigit,
        tr("Есть заглавная буква") to hasUpper
    ).forEach { (rule, met) ->
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .then(
                        if (met) Modifier.background(PanelColors.accent)
                        else Modifier.border(2.dp, PanelColors.content.copy(alpha = 0.25f), CircleShape)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (met) Icon(Icons.Rounded.Check, contentDescription = null, tint = PanelColors.onAccent, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(rule, style = MaterialTheme.typography.bodyLarge, color = if (met) PanelColors.content else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AvatarStep(
    nick: String,
    name: String,
    onName: (String) -> Unit,
    color: String,
    onColor: (String) -> Unit,
    photo: String?,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit
) {
    StepTitle(tr("Добавь аватарку"), tr("Её увидят друзья в списке и в профиле. Можно и без фото, просто цвет"))
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(196.dp)) {
            PersonAvatar(name = name.ifBlank { nick }, color = color, imageUrl = photo, size = 196.dp)
            Surface(
                onClick = onPickPhoto,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(64.dp),
                shape = RoundedCornerShape(22.dp),
                color = PanelColors.accent,
                contentColor = PanelColors.onAccent,
                border = BorderStroke(5.dp, MaterialTheme.colorScheme.background)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = tr("Выбрать фото"), modifier = Modifier.size(26.dp))
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(18.dp))
    if (photo != null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextButton(onClick = onRemovePhoto) { Text(tr("Убрать фото")) }
        }
    } else {
        Text(
            tr("Нет фото? Выбери цвет"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        ColorSwatches(selected = color, onSelect = onColor)
    }
    Spacer(modifier = Modifier.height(22.dp))
    GlassField(
        value = name,
        onValueChange = onName,
        label = tr("Имя для друзей (необязательно)"),
        leading = { Icon(Icons.Rounded.Badge, contentDescription = null, tint = PanelColors.accent) },
        imeAction = ImeAction.Done
    )
}

@Composable
private fun ColorSwatches(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    ) {
        AvatarColors.forEach { hex ->
            val picked = hex.equals(selected, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(3.dp, if (picked) PanelColors.accent else Color.Transparent, CircleShape)
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(PolygonShape(MaterialShapes.Cookie9Sided))
                        .background(colorOf(hex) ?: Color.Gray)
                )
            }
        }
    }
}

@Composable
private fun CodeStep(code: String) {
    StepTitle(tr("Сохрани код восстановления"), tr("Почты у аккаунта нет. Если забудешь пароль, войти получится только с этим кодом"))
    RecoveryCodeCard(code)
    Spacer(modifier = Modifier.height(18.dp))
    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
        Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            tr("Код показываем только сейчас. Новый можно сделать в профиле, и тогда старый перестанет работать"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The code in four groups of four, big, with copying and sharing it. */
@Composable
internal fun RecoveryCodeCard(code: String) {
    val context = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassOr(shape, PanelColors.container)
            .background(glassFill(PanelColors.container), shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        code.chunked(4).chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { group ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(PanelColors.content.copy(alpha = 0.07f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(group, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.displaySmall.fontFamily), color = PanelColors.content)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = if (copied) tr("Скопировано") else tr("Скопировать"),
                onClick = {
                    copyText(context, RecoveryCode.pretty(code))
                    copied = true
                },
                modifier = Modifier.weight(1f),
                icon = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                kind = PillKind.Tonal,
                height = 52.dp
            )
            PillButton(
                text = tr("Поделиться"),
                onClick = { shareText(context, tr("Код восстановления YouCloud: %s", RecoveryCode.pretty(code))) },
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Share,
                kind = PillKind.Tonal,
                height = 52.dp
            )
        }
    }
}

// endregion

// region Signing in

@Composable
private fun SignInPage(
    social: Social,
    recovering: Boolean,
    onRecover: (Boolean) -> Unit,
    onSignUp: () -> Unit,
    onClose: () -> Unit,
    onSignedIn: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var nick by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember(recovering) { mutableStateOf<String?>(null) }

    fun go() {
        busy = true
        error = null
        scope.launch {
            try {
                if (recovering) social.recover(nick, code, password) else social.signIn(nick, password)
                onSignedIn()
            } catch (e: Exception) {
                error = e.socialMessage()
            } finally {
                busy = false
            }
        }
    }

    val ready = nick.isNotBlank() && password.length >= (if (recovering) 8 else 1) &&
        (!recovering || RecoveryCode.normalize(code).length == 16)

    SocialPage(title = "", onClose = onClose) {
        item(key = "form") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ShapeHero(icon = if (recovering) Icons.Rounded.Key else Icons.Rounded.Person, size = 136.dp)
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    if (recovering) tr("Новый пароль по коду") else tr("С возвращением"),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (recovering) tr("Код дали при регистрации. После входа сделай новый в профиле") else tr("Войди, чтобы видеть, что слушают друзья"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
                GlassField(
                    value = nick,
                    onValueChange = { nick = it.lowercase().filterNot(Char::isWhitespace).removePrefix("@").take(20) },
                    label = tr("Ник"),
                    leading = { AtMark() },
                    keyboardType = KeyboardType.Ascii
                )
                if (recovering) {
                    GlassField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(24) },
                        label = tr("Код восстановления"),
                        keyboardType = KeyboardType.Ascii,
                        leading = { Icon(Icons.Rounded.Key, contentDescription = null, tint = PanelColors.accent) }
                    )
                }
                GlassField(
                    value = password,
                    onValueChange = { password = it },
                    label = if (recovering) tr("Новый пароль") else tr("Пароль"),
                    leading = { Icon(Icons.Rounded.Lock, contentDescription = null, tint = PanelColors.accent) },
                    password = true,
                    imeAction = ImeAction.Done,
                    onIme = { if (ready && !busy) go() }
                )
                FormError(error)
                Spacer(modifier = Modifier.height(4.dp))
                PillButton(
                    text = if (recovering) tr("Сменить пароль и войти") else tr("Войти"),
                    onClick = ::go,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.AutoMirrored.Rounded.ArrowForward,
                    enabled = ready,
                    loading = busy
                )
                if (!recovering) {
                    PillButton(tr("Создать аккаунт"), onSignUp, Modifier.fillMaxWidth(), kind = PillKind.Tonal)
                }
                TextButton(
                    onClick = { onRecover(!recovering) },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(if (recovering) tr("Помнишь пароль? Войти") else tr("Не помнишь пароль? Войти по коду"))
                }
            }
        }
    }
}

// endregion

// region One's own profile

/**
 * One's own profile: the avatar, the name and the nick, and under them two pages a swipe apart —
 * the showcase, what the others see (what plays here, the favourite artist, track and album), and
 * the account. At the foot, as search has it, adding a friend and the tabs.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnProfile(
    social: Social,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFriends: (requests: Boolean) -> Unit,
    onOpenPerson: (String) -> Unit,
    nowPlaying: SoundCloudTrack?,
    isPlaying: Boolean,
    onOpenNowPlaying: () -> Unit,
    onPickShowcase: (ShowcaseSlot) -> Unit,
    onPlayLink: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val me by social.me.collectAsState()
    var adding by rememberSaveable { mutableStateOf(false) }
    val friends by social.friends.collectAsState()
    var avatarBusy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<ProfileDialog?>(null) }
    // A picked place of the showcase held: to be changed or taken away.
    var holding by remember { mutableStateOf<ShowcaseSlot?>(null) }
    val pager = rememberPagerState { 2 }

    LaunchedEffect(Unit) {
        runCatching { social.refreshMe() }
        runCatching { social.loadFriends() }
    }
    // The friends and the requests counted as they change, while the profile is open.
    DisposableEffect(Unit) {
        val live = social.watchLive()
        onDispose { live.close() }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                Toast.makeText(context, e.socialMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            avatarBusy = true
            act {
                try {
                    social.setAvatar(uri)
                } finally {
                    avatarBusy = false
                }
            }
        }
    }

    val profile = me
    val friendCount = friends?.count { it.relationKind == Relation.FRIEND } ?: 0
    val requestCount = friends?.count { it.relationKind == Relation.INCOMING } ?: 0

    SocialPage(
        title = "",
        onClose = onClose,
        // The last rows clear of the dock, and of the mini player standing on it.
        bottomPadding = dockedPageRoom(),
        overlay = {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(SearchDockGap)
            ) {
                // Finding friends is what the profile is for: the one button in the accent.
                PillButton(
                    text = tr("Добавить друга"),
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PersonAdd,
                    height = SearchFieldHeight
                )
                SearchSourceTabs(
                    labels = listOf(tr("Витрина"), tr("Аккаунт")),
                    pager = pager,
                    onSelect = { page ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { pager.animateScrollToPage(page) }
                    }
                )
            }
        }
    ) {
        item(key = "hero") {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.size(196.dp)) {
                    PersonAvatar(
                        name = profile?.shownName() ?: "?",
                        color = profile?.color,
                        imageUrl = profile?.let { social.avatarUrl(it.id, it.avatarV) },
                        size = 196.dp,
                        shape = OwnAvatarShape
                    )
                    if (avatarBusy) {
                        Box(
                            modifier = Modifier.matchParentSize().clip(OwnAvatarShape).background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) { AppLoadingIndicator(color = Color.White) }
                    }
                    Surface(
                        onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = (-6).dp).size(62.dp),
                        shape = RoundedCornerShape(22.dp),
                        color = PanelColors.accent,
                        contentColor = PanelColors.onAccent,
                        border = BorderStroke(5.dp, MaterialTheme.colorScheme.background)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = tr("Сменить фото"), modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    profile?.shownName() ?: "",
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable { dialog = ProfileDialog.Name }
                )
                Spacer(modifier = Modifier.height(10.dp))
                // The nick to copy, the friends and the requests to open, the profile to share.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProfileChip(
                        onClick = {
                            profile?.let { copyText(context, "@${it.nick}") }
                            Toast.makeText(context, tr("Ник скопирован"), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("@${profile?.nick.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.Rounded.ContentCopy, contentDescription = tr("Скопировать ник"), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ProfileChip(onClick = { onOpenFriends(false) }) {
                        Icon(Icons.Rounded.Group, contentDescription = null, modifier = Modifier.size(18.dp), tint = PanelColors.accent)
                        Text(
                            "$friendCount ${countWord(friendCount.toLong(), tr("друг"), tr("друга"), tr("друзей"))}",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (requestCount > 0) {
                        ProfileChip(onClick = { onOpenFriends(true) }, accent = true) {
                            Icon(Icons.Rounded.Inbox, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                "$requestCount ${countWord(requestCount.toLong(), tr("заявка"), tr("заявки"), tr("заявок"))}",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                    ProfileChip(onClick = { profile?.let { shareText(context, inviteText(it.nick)) } }) {
                        Icon(Icons.Rounded.Share, contentDescription = tr("Поделиться профилем"), modifier = Modifier.size(18.dp), tint = PanelColors.accent)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
        item(key = "pages") {
            HorizontalPager(
                state = pager,
                // Edge to edge, past the list's margins, for a page to slide in from the screen's
                // edge rather than from a line short of it; the margins go inside.
                modifier = Modifier.layout { measurable, constraints ->
                    val margins = 32.dp.roundToPx()
                    val width = constraints.maxWidth + margins
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(constraints.maxWidth, placeable.height) { placeable.place(-margins / 2, 0) }
                },
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 32.dp,
                verticalAlignment = Alignment.Top
            ) { page ->
                if (page == 0) {
                    // The showcase: what plays here, and the favourites.
                    Column {
                        if (nowPlaying != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OwnNowPlayingRow(
                                title = nowPlaying.title ?: tr("Без названия"),
                                artist = nowPlaying.user?.username,
                                cover = nowPlaying.artworkUrl,
                                playing = isPlaying,
                                onClick = onOpenNowPlaying
                            )
                        }
                        ShowcaseSection(
                            showcase = profile?.showcase ?: com.example.myapplication.data.social.Showcase(),
                            onOpen = { item -> item.link?.let(onPlayLink) },
                            onPick = onPickShowcase,
                            onHold = { holding = it }
                        )
                        Text(
                            tr("Любимое видно всем, кто откроет твой профиль. Подержи, чтобы заменить или убрать"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp)
                        )
                    }
                } else {
                    // The account: who sees what, the name, the password, the way back in, leaving.
                    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        val rows = 4
                        SettingRow(
                            icon = Icons.Rounded.Visibility,
                            title = tr("Показывать, что я слушаю"),
                            subtitle = tr("Друзья видят трек, который у тебя играет"),
                            shape = groupedShape(0, rows),
                            trailing = {
                                Switch(
                                    checked = profile?.shareListening != false,
                                    onCheckedChange = { on -> act { social.setShareListening(on) } },
                                    thumbContent = if (profile?.shareListening != false) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
                                    } else null
                                )
                            }
                        )
                        SettingRow(Icons.Rounded.Badge, tr("Имя"), profile?.shownName(), groupedShape(1, rows)) { dialog = ProfileDialog.Name }
                        SettingRow(Icons.Rounded.Lock, tr("Сменить пароль"), null, groupedShape(2, rows)) { dialog = ProfileDialog.Password }
                        SettingRow(Icons.Rounded.Key, tr("Код восстановления"), tr("Сделать новый, если старый потерялся"), groupedShape(3, rows)) {
                            dialog = ProfileDialog.NewCode
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        SettingRow(Icons.Rounded.Settings, tr("Настройки приложения"), null, RoundedCornerShape(28.dp)) { onOpenSettings() }
                        PillButton(
                            text = tr("Выйти из аккаунта"),
                            onClick = { dialog = ProfileDialog.SignOut },
                            modifier = Modifier.fillMaxWidth().padding(top = 13.dp),
                            icon = Icons.AutoMirrored.Rounded.Logout,
                            kind = PillKind.Glass,
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    holding?.let { slot ->
        AlertDialog(
            onDismissRequest = { holding = null },
            title = { Text(slot.pickTitle) },
            text = { Text(profile?.showcase?.get(slot)?.title.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    holding = null
                    onPickShowcase(slot)
                }) { Text(tr("Заменить")) }
            },
            dismissButton = {
                TextButton(onClick = {
                    holding = null
                    act { social.setShowcase(slot, null) }
                }) { Text(tr("Убрать"), color = MaterialTheme.colorScheme.error) }
            }
        )
    }

    if (adding) {
        AddFriendSheet(
            social = social,
            ownNick = profile?.nick,
            onOpenPerson = {
                adding = false
                onOpenPerson(it)
            },
            onDismiss = { adding = false }
        )
    }

    when (val shown = dialog) {
        null -> Unit
        ProfileDialog.Name -> TextDialog(
            title = tr("Имя для друзей"),
            initial = profile?.name.orEmpty(),
            label = tr("Имя"),
            onDismiss = { dialog = null },
            onSave = { act { social.setName(it) } }
        )
        ProfileDialog.Password -> TextDialog(
            title = tr("Новый пароль"),
            initial = "",
            label = tr("Не короче 8 символов"),
            password = true,
            valid = { it.length >= 8 },
            onDismiss = { dialog = null },
            onSave = { act { social.changePassword(it); Toast.makeText(context, tr("Пароль сменён"), Toast.LENGTH_SHORT).show() } }
        )
        ProfileDialog.NewCode -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(tr("Новый код восстановления?")) },
            text = { Text(tr("Старый код перестанет работать.")) },
            confirmButton = {
                TextButton(onClick = {
                    act { dialog = ProfileDialog.ShowCode(social.newRecoveryCode()) }
                }) { Text(tr("Сделать")) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Отмена")) } }
        )
        is ProfileDialog.ShowCode -> AlertDialog(
            onDismissRequest = {},
            title = { Text(tr("Сохрани новый код")) },
            text = { NoGlass { RecoveryCodeCard(shown.code) } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(tr("Готово")) } }
        )
        ProfileDialog.SignOut -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(tr("Выйти из аккаунта?")) },
            text = { Text(tr("Войти снова можно по нику и паролю.")) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    act { social.signOut() }
                }) { Text(tr("Выйти")) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("Отмена")) } }
        )
    }
}

private sealed interface ProfileDialog {
    data object Name : ProfileDialog
    data object Password : ProfileDialog
    data object NewCode : ProfileDialog
    data class ShowCode(val code: String) : ProfileDialog
    data object SignOut : ProfileDialog
}

private fun Profile.shownName(): String = name?.takeIf { it.isNotBlank() } ?: nick

/** A small pill of the profile's head: the nick, the friends, the requests (on the accent), sharing. */
@Composable
private fun ProfileChip(onClick: () -> Unit, accent: Boolean = false, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (accent) PanelColors.accent else PanelColors.content.copy(alpha = 0.1f),
        contentColor = if (accent) PanelColors.onAccent else PanelColors.content
    ) {
        Row(
            modifier = Modifier.height(40.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    shape: androidx.compose.ui.graphics.Shape,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    GroupedCard(shape = shape, onClick = onClick) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PanelColors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = PanelColors.accent, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) trailing() else if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    initial: String,
    label: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    password: Boolean = false,
    valid: (String) -> Boolean = { true }
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            NoGlass { GlassField(value = value, onValueChange = { value = it }, label = label, password = password, imeAction = ImeAction.Done) }
        },
        confirmButton = {
            TextButton(
                enabled = valid(value),
                onClick = {
                    onDismiss()
                    onSave(value)
                }
            ) { Text(tr("Сохранить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена")) } }
    )
}

// endregion

/**
 * What is in a dialog or a sheet is in a window of its own, with nothing of the screen behind it
 * to show through: solid there, not glass.
 */
@Composable
internal fun NoGlass(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalGlass provides false, content = content)
}

internal fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("YouCloud", text))
}

internal fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}
