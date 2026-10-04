@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.example.myapplication.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplication.data.social.Person
import com.example.myapplication.data.social.Relation
import com.example.myapplication.data.social.Showcase
import com.example.myapplication.data.social.Social
import com.example.myapplication.data.social.socialMessage
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the friends and a friend's page ask again what plays, while they are open. */
private const val REFRESH_MS = 30_000L

// region Friends

/**
 * The friends, those playing something now first, and the requests either way. Opened from the
 * profile; pulled down, it goes back to it. Asked again every half a minute while open: there is
 * no live connection to the server, so this is how what friends play keeps up.
 */
@Composable
internal fun FriendsScreen(
    onClose: () -> Unit,
    onOpenPerson: (String) -> Unit,
    startWithRequests: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val social = remember { Social.get(context) }
    val all by social.friends.collectAsState()
    val me by social.me.collectAsState()
    // The friends and the requests side by side, a swipe apart, the tabs following the finger.
    val pager = rememberPagerState(initialPage = if (startWithRequests) 1 else 0) { 2 }
    var adding by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // What the list was last drawn against, for "now" and "5 мин назад" to move on.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            error = runCatching { social.loadFriends() }.exceptionOrNull()?.socialMessage()
            now = System.currentTimeMillis()
            delay(REFRESH_MS)
        }
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

    val friends = all.orEmpty().filter { it.relationKind == Relation.FRIEND }
    val listening = friends.filter { it.listeningNow(now) }
    val quiet = friends.filterNot { it.listeningNow(now) }
    val incoming = all.orEmpty().filter { it.relationKind == Relation.INCOMING }
    val outgoing = all.orEmpty().filter { it.relationKind == Relation.OUTGOING }

    val haptic = LocalHapticFeedback.current
    SocialPagedPage(
        title = tr("Друзья"),
        onClose = onClose,
        pager = pager,
        // The last rows clear of the dock, and of the mini player standing on it.
        bottomPadding = searchDockHeight(tabs = true) + 24.dp +
            if (LocalNowPlaying.current.trackId != null) MiniPlayerHeight + 12.dp else 0.dp,
        overlay = {
            // As search has it, under the thumb: (the mini player,) finding someone, the tabs.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(SearchDockGap)
            ) {
                FindByNickBar(onClick = { adding = true })
                SearchSourceTabs(
                    labels = listOf(tr("Друзья"), tr("Заявки")),
                    pager = pager,
                    onSelect = { index ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { pager.animateScrollToPage(index) }
                    },
                    badges = listOf(0, incoming.size)
                )
            }
        }
    ) { page ->
        if (error != null && all == null) {
            item(key = "error") { FormError(error, Modifier.padding(top = 20.dp)) }
        }
        if (all == null && error == null) {
            item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    AppLoadingIndicator()
                }
            }
        } else if (page == 0) {
            if (friends.isEmpty()) {
                item(key = "empty") {
                    EmptyFriends(text = tr("Пока никого. Найди друга по нику: он увидит заявку и сможет её принять"))
                }
            }
            if (listening.isNotEmpty()) {
                item(key = "listening-rule") { SectionRule(tr("Сейчас слушают · %s", listening.size)) }
                people(listening, social, now, onOpenPerson)
            }
            if (quiet.isNotEmpty()) {
                item(key = "quiet-rule") { SectionRule(if (listening.isEmpty()) tr("Все друзья · %s", quiet.size) else tr("Не в сети")) }
                people(quiet, social, now, onOpenPerson, dimmed = listening.isNotEmpty())
            }
        } else {
            if (incoming.isEmpty() && outgoing.isEmpty()) {
                item(key = "no-requests") {
                    EmptyFriends(text = tr("Заявок нет. Здесь появятся те, кто захочет с тобой дружить"))
                }
            }
            if (incoming.isNotEmpty()) {
                item(key = "incoming-rule") { SectionRule(tr("Входящие · %s", incoming.size)) }
                people(incoming, social, now, onOpenPerson) { person ->
                    SmallPill(tr("Принять"), Icons.Rounded.Check, primary = true) { act { social.accept(person.id) } }
                    RoundAction(Icons.Rounded.Close, tr("Отклонить заявку: %s", person.shownName)) { act { social.remove(person.id) } }
                }
            }
            if (outgoing.isNotEmpty()) {
                item(key = "outgoing-rule") { SectionRule(tr("Отправленные · %s", outgoing.size)) }
                people(outgoing, social, now, onOpenPerson, line = { tr("@%s · ждём ответа", it.nick) }) { person ->
                    SmallPill(tr("Отменить"), null, primary = false) { act { social.remove(person.id) } }
                }
            }
        }
    }

    if (adding) {
        AddFriendSheet(
            social = social,
            ownNick = me?.nick,
            onOpenPerson = {
                adding = false
                onOpenPerson(it)
            },
            onDismiss = { adding = false }
        )
    }
}

/** The rows of a group of people, each opening their page. */
private fun LazyListScope.people(
    list: List<Person>,
    social: Social,
    now: Long,
    onOpenPerson: (String) -> Unit,
    dimmed: Boolean = false,
    line: (Person) -> String? = { it.trackLine(now) ?: "@${it.nick}" },
    actions: (@Composable (Person) -> Unit)? = null
) {
    itemsIndexed(list, key = { _, person -> "person-${person.id}-${person.relation}" }) { index, person ->
        GroupedCard(
            shape = groupedShape(index, list.size),
            modifier = Modifier.padding(bottom = 3.dp),
            onClick = { onOpenPerson(person.id) }
        ) {
            PersonAvatarWithBadge(
                person = person,
                avatarUrl = social.avatarUrl(person.id, person.avatarV),
                size = 52.dp,
                listening = person.listeningNow(now),
                dimmed = dimmed
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(person.shownName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                line(person)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (actions != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { actions(person) }
            }
        }
    }
}

// Words only: finding someone is the field at the foot of the page, right under them.
@Composable
private fun EmptyFriends(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 36.dp, start = 12.dp, end = 12.dp)
    )
}

/** At the bottom of the friends, as search's field is: where finding someone by their nick starts. */
@Composable
private fun FindByNickBar(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(30.dp)
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchFieldHeight)
            .glassOr(shape, MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = shape,
        color = glassFill(MaterialTheme.colorScheme.surfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Text(tr("Найти друга по нику"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SmallPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, primary: Boolean, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = Modifier.height(40.dp),
        shape = CircleShape,
        color = if (primary) PanelColors.accent else PanelColors.content.copy(alpha = 0.1f),
        contentColor = if (primary) PanelColors.onAccent else PanelColors.content
    ) {
        Row(modifier = Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        color = PanelColors.content.copy(alpha = 0.1f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp)) }
    }
}

// endregion

// region Adding a friend

/** Finding someone by the first letters of their nick, and asking them; or one's own nick to give out. */
@Composable
internal fun AddFriendSheet(
    social: Social,
    ownNick: String?,
    onOpenPerson: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Person>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // What a request just sent or taken back made of someone, before the search is asked again.
    val changed = remember { mutableStateMapOf<String, Relation>() }
    // Opened to type a nick into: the field has the keyboard from the start.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(250)
        runCatching { focus.requestFocus() }
    }

    LaunchedEffect(query) {
        error = null
        if (query.isBlank()) {
            results = null
            return@LaunchedEffect
        }
        delay(300)
        try {
            results = social.search(query)
        } catch (e: Exception) {
            error = e.socialMessage()
        }
    }

    fun act(id: String, block: suspend () -> Relation) {
        scope.launch {
            try {
                changed[id] = block()
            } catch (e: Exception) {
                Toast.makeText(context, e.socialMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        NoGlass {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().imePadding(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)
            ) {
                item(key = "title") {
                    Column {
                        Text(tr("Добавить друга"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 4.dp, bottom = 16.dp))
                        GlassField(
                            value = query,
                            onValueChange = { query = it.take(30) },
                            modifier = Modifier.focusRequester(focus),
                            label = tr("Ник друга"),
                            leading = { AtMark() },
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }
                val found = results
                when {
                    error != null -> item(key = "error") { FormError(error) }
                    query.isBlank() -> item(key = "hint") {
                        Text(
                            tr("Начни вводить ник, найдём по первым буквам"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    found == null -> item(key = "searching") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { AppLoadingIndicator() }
                    }
                    found.isEmpty() -> item(key = "nobody") {
                        Text(
                            tr("Никого с ником @%s не нашли", query.trim().removePrefix("@")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )
                    }
                    else -> itemsIndexed(found, key = { _, person -> "found-${person.id}" }) { index, person ->
                        val relation = changed[person.id] ?: person.relationKind
                        GroupedCard(
                            shape = groupedShape(index, found.size),
                            modifier = Modifier.padding(bottom = 3.dp),
                            onClick = { onOpenPerson(person.id) }
                        ) {
                            PersonAvatar(person.shownName, person.color, social.avatarUrl(person.id, person.avatarV), 48.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(person.shownName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("@${person.nick}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            when (relation) {
                                Relation.NONE -> SmallPill(tr("Добавить"), Icons.Rounded.PersonAdd, primary = true) { act(person.id) { social.request(person.id) } }
                                Relation.OUTGOING -> SmallPill(tr("Отправлено"), Icons.Rounded.Check, primary = false) { act(person.id) { social.remove(person.id) } }
                                Relation.INCOMING -> SmallPill(tr("Принять"), Icons.Rounded.Check, primary = true) { act(person.id) { social.accept(person.id) } }
                                Relation.FRIEND -> Text(tr("В друзьях"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Relation.SELF -> Unit
                            }
                        }
                    }
                }
                if (ownNick != null) item(key = "own") {
                    Column(modifier = Modifier.padding(top = 22.dp)) {
                        Text(
                            tr("Или дай другу свой ник"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(PanelColors.container)
                                .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Kicker(tr("Твой ник"))
                                Text("@$ownNick", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = MaterialTheme.typography.displaySmall.fontFamily))
                            }
                            RoundAction(Icons.Rounded.ContentCopy, tr("Скопировать ник")) {
                                copyText(context, "@$ownNick")
                                Toast.makeText(context, tr("Ник скопирован"), Toast.LENGTH_SHORT).show()
                            }
                            Surface(
                                onClick = { shareText(context, inviteText(ownNick)) },
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = PanelColors.accent,
                                contentColor = PanelColors.onAccent
                            ) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Share, contentDescription = tr("Поделиться ником")) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// endregion

// region Someone's page

/**
 * Someone's page: who they are, what they are to the one looking — with the one button that
 * fits it (ask, take back, accept, unfriend) — and, for a friend, what plays at theirs, which
 * can be put on here too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PersonScreen(
    id: String,
    onClose: () -> Unit,
    onPlayLink: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val social = remember { Social.get(context) }
    var person by remember(id) { mutableStateOf<Person?>(null) }
    // Their favourite artist, track and album: asked once, they change seldom.
    var showcase by remember(id) { mutableStateOf(Showcase()) }
    var error by remember(id) { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        runCatching { social.showcaseOf(id) }.onSuccess { showcase = it }
    }
    var busy by remember { mutableStateOf(false) }
    var confirmUnfriend by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(id) {
        while (true) {
            try {
                person = social.profile(id)
                error = null
            } catch (e: Exception) {
                error = e.socialMessage()
            }
            now = System.currentTimeMillis()
            delay(REFRESH_MS)
        }
    }
    // The position moves on between askings: a second at a time while it plays.
    LaunchedEffect(person?.listeningNow(now)) {
        while (person?.listeningNow() == true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    fun change(block: suspend () -> Unit) {
        busy = true
        scope.launch {
            try {
                block()
                person = social.profile(id)
            } catch (e: Exception) {
                Toast.makeText(context, e.socialMessage(), Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    SocialPage(title = "", onClose = onClose) {
        val shown = person
        if (shown == null) {
            item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                    if (error != null) FormError(error) else AppLoadingIndicator()
                }
            }
            return@SocialPage
        }
        val relation = shown.relationKind
        val listening = shown.listeningNow(now)
        item(key = "head") {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                PersonAvatarWithBadge(
                    person = shown,
                    avatarUrl = social.avatarUrl(shown.id, shown.avatarV),
                    size = 136.dp,
                    listening = listening && relation == Relation.FRIEND
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(shown.shownName, style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip("@${shown.nick}")
                    shown.friends?.let { InfoChip("$it ${countWord(it, tr("друг"), tr("друга"), tr("друзей"))}") }
                    // Being friends is said once, by the button under it.
                }
            }
        }
        item(key = "actions") {
            Column(modifier = Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (relation == Relation.INCOMING) {
                    Text(tr("Хочет добавить тебя в друзья"), style = MaterialTheme.typography.bodyLarge)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (relation) {
                        Relation.NONE -> PillButton(
                            tr("Добавить в друзья"), { change { social.request(id) } }, Modifier.weight(1f),
                            icon = Icons.Rounded.PersonAdd, loading = busy, height = 64.dp
                        )
                        Relation.OUTGOING -> {
                            PillButton(
                                tr("Заявка отправлена"), {}, Modifier.weight(1f),
                                icon = Icons.Rounded.Schedule, kind = PillKind.Tonal, enabled = false, height = 64.dp
                            )
                            GlassIconButton(Icons.Rounded.Close, tr("Отменить заявку"), { change { social.remove(id) } }, size = 64.dp, tint = PanelColors.content)
                        }
                        Relation.INCOMING -> {
                            PillButton(tr("Принять"), { change { social.accept(id) } }, Modifier.weight(1f), icon = Icons.Rounded.Check, loading = busy, height = 64.dp)
                            PillButton(tr("Отклонить"), { change { social.remove(id) } }, Modifier.weight(1f), kind = PillKind.Glass, height = 64.dp)
                        }
                        Relation.FRIEND -> PillButton(
                            tr("В друзьях"), { confirmUnfriend = true }, Modifier.weight(1f),
                            icon = Icons.Rounded.Check, kind = PillKind.Glass, height = 64.dp
                        )
                        Relation.SELF -> Unit
                    }
                }
            }
        }
        item(key = "playing") {
            Box(modifier = Modifier.padding(top = 22.dp)) {
                when {
                    relation == Relation.FRIEND || relation == Relation.SELF -> NowPlayingCard(shown, now, onPlayLink)
                    else -> LockedCard(shown.shownName)
                }
            }
        }
        // Part of the profile, as the name is: for anyone who opens it.
        item(key = "showcase") {
            Box(modifier = Modifier.padding(top = 6.dp)) {
                ShowcaseSection(showcase = showcase, onOpen = { item -> item.link?.let(onPlayLink) })
            }
        }
    }

    if (confirmUnfriend) {
        AlertDialog(
            onDismissRequest = { confirmUnfriend = false },
            title = { Text(tr("Удалить из друзей?")) },
            text = { Text(tr("%s больше не увидит, что ты слушаешь, а ты — что слушает %s.", person?.shownName.orEmpty(), person?.shownName.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnfriend = false
                    change { social.remove(id) }
                }) { Text(tr("Удалить")) }
            },
            dismissButton = { TextButton(onClick = { confirmUnfriend = false }) { Text(tr("Отмена")) } }
        )
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = CircleShape,
        color = PanelColors.content.copy(alpha = 0.1f),
        contentColor = PanelColors.content
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

/** What plays at a friend's, or played last: cover, track, where it is, and putting it on here. */
@Composable
private fun NowPlayingCard(person: Person, now: Long, onPlayLink: (String) -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassOr(shape, PanelColors.container)
            .background(glassFill(PanelColors.container), shape)
            .padding(18.dp)
    ) {
        val title = person.title
        if (title.isNullOrBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Text(tr("Пока ничего не играет"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        val listening = person.listeningNow(now)
        val service = serviceName(person.service)
        val kicker = when {
            listening -> listOfNotNull(service, tr("сейчас")).joinToString(" · ")
            else -> listOfNotNull(service, person.updatedAtMs?.let { tr("играло %s", timeAgo(it, now)) }).joinToString(" · ")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OnPanelChip(text = kicker, modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.weight(1f))
            if (listening) Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = PanelColors.accent)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PanelColors.content.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = PanelColors.content.copy(alpha = 0.4f))
                if (person.coverUrl != null) {
                    AsyncImage(model = person.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!person.artist.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(person.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val duration = person.durationMs
        val position = if (listening) person.positionMs(now) else null
        if (position != null && duration != null && duration > 0) {
            Spacer(modifier = Modifier.height(18.dp))
            AppLinearProgress(progress = position.toFloat() / duration, modifier = Modifier.fillMaxWidth(), color = PanelColors.accent)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text(clock(position), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(clock(duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val link = person.trackUrl
        if (link != null) {
            Spacer(modifier = Modifier.height(14.dp))
            PillButton(tr("Включить у себя"), { onPlayLink(link) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.PlayArrow, kind = PillKind.Tonal, height = 52.dp)
        }
    }
}

private fun clock(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun LockedCard(name: String) {
    val shape = RoundedCornerShape(32.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassOr(shape, PanelColors.container)
            .background(glassFill(PanelColors.container), shape)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(PanelColors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = PanelColors.accent)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(tr("Что играет у %s, видят только друзья", name), style = MaterialTheme.typography.titleMedium)
            Text(tr("Добавь в друзья, и будете видеть музыку друг друга"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// endregion
