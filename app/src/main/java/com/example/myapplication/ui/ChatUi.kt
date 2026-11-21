@file:OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)

package com.example.myapplication.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.myapplication.data.social.ChatMessage
import com.example.myapplication.data.social.ChatNotifications
import com.example.myapplication.data.social.ChatReaction
import com.example.myapplication.data.social.ChatSharePayload
import com.example.myapplication.data.social.ChatThreadSummary
import com.example.myapplication.data.social.Person
import com.example.myapplication.data.social.Relation
import com.example.myapplication.data.social.Social
import com.example.myapplication.data.social.displayRatingOf
import com.example.myapplication.data.social.groupedEmojiReactions
import com.example.myapplication.data.social.socialMessage
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val QUICK_EMOJIS = listOf("👍", "👎", "💩", "🤡", "❤️", "🔥", "😂")
private val EXTRA_EMOJIS = listOf("😭", "🤯", "💀", "💯", "😍", "🤮", "🫡", "⚡", "🎧")

private data class RatingOption(
    val score: Int,
    val label: String,
    val color: Color?,
    val shape: Shape
)

private val RATING_OPTIONS: List<RatingOption> by lazy {
    listOf(
        RatingOption(1, "мимо", Color(0xFF6B7280), CircleShape),
        RatingOption(2, "вяло", Color(0xFF5C7C99), PolygonShape(MaterialShapes.Clover4Leaf)),
        RatingOption(3, "норм", Color(0xFF9C8C5E), PolygonShape(MaterialShapes.Cookie9Sided)),
        RatingOption(4, "качает", Color(0xFFD47C48), PolygonShape(MaterialShapes.Cookie12Sided)),
        RatingOption(5, "топ", null, PolygonShape(MaterialShapes.Sunny))
    )
}

// region Chat List (Variant B: "Новое от друзей" + grouped 28/8dp chats + bottom search dock)

@Composable
internal fun ChatListScreen(
    onClose: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenItem: (link: String) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val social = remember { Social.get(context) }
    val session by social.session.collectAsState()
    val me by social.me.collectAsState()
    val inbox by social.chatInbox.collectAsState()
    val friendsAll by social.friends.collectAsState()
    val changes by social.changes.collectAsState()
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(session?.userId) {
        val live = social.watchLive()
        onDispose { live.close() }
    }
    LaunchedEffect(session?.userId, changes) {
        if (session == null) return@LaunchedEffect
        error = runCatching { social.loadChatInbox() }.exceptionOrNull()?.socialMessage()
        if (friendsAll == null) runCatching { social.loadFriends() }
        now = System.currentTimeMillis()
    }

    val acceptedFriends = remember(friendsAll) {
        friendsAll.orEmpty().filter { it.relationKind == Relation.FRIEND }
    }
    val q = searchQuery.trim()
    val filteredRecent = remember(inbox?.newItems, q) {
        val raw = inbox?.newItems.orEmpty()
        if (q.isEmpty()) raw else raw.filter {
            it.senderShownName.contains(q, ignoreCase = true) ||
                it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true)
        }
    }
    val allThreads = inbox?.threads.orEmpty()
    val filteredThreads = remember(allThreads, q) {
        if (q.isEmpty()) allThreads else allThreads.filter {
            it.shownName.contains(q, ignoreCase = true) ||
                it.nick.contains(q, ignoreCase = true) ||
                it.lastMessage?.title?.contains(q, ignoreCase = true) == true ||
                it.lastMessage?.artist?.contains(q, ignoreCase = true) == true
        }
    }
    val threadPeerIds = remember(allThreads) { allThreads.mapTo(HashSet()) { it.peerId } }
    val friendsWithoutThread = remember(acceptedFriends, threadPeerIds, q) {
        acceptedFriends.filter { it.id !in threadPeerIds }.let { list ->
            if (q.isEmpty()) list else list.filter {
                it.shownName.contains(q, ignoreCase = true) || it.nick.contains(q, ignoreCase = true)
            }
        }
    }

    SocialPage(
        title = tr("Чат"),
        onClose = onClose,
        bottomPadding = dockedPageRoom(tabs = false),
        overlay = {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                ChatSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )
            }
        }
    ) {
        if (error != null && inbox == null) {
            item(key = "chat-error") { FormError(error, Modifier.padding(top = 20.dp)) }
        }
        if (inbox == null && error == null) {
            item(key = "chat-loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    AppLoadingIndicator()
                }
            }
        } else {
            if (filteredRecent.isNotEmpty()) {
                item(key = "chat-recent-rule") {
                    SectionRule(tr("Новое от друзей"))
                }
                item(key = "chat-recent-carousel") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(filteredRecent, key = { "recent-${it.id}" }) { msg ->
                            RecentFriendItemCard(
                                msg = msg,
                                avatarUrl = social.avatarUrl(msg.senderId, msg.senderAvatarV),
                                now = now,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenItem(msg.itemUrl)
                                }
                            )
                        }
                    }
                }
            }

            if (filteredThreads.isNotEmpty()) {
                item(key = "chat-threads-rule") {
                    SectionRule(tr("Чаты"))
                }
                itemsIndexed(filteredThreads, key = { _, thread -> "thread-${thread.peerId}" }) { index, thread ->
                    ChatThreadRowCard(
                        thread = thread,
                        myUserId = me?.id,
                        avatarUrl = social.avatarUrl(thread.peerId, thread.avatarV),
                        shape = groupedShape(index, filteredThreads.size),
                        now = now,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenChat(thread.peerId)
                        }
                    )
                }
            }

            if (friendsWithoutThread.isNotEmpty()) {
                item(key = "chat-friends-rule") {
                    SectionRule(if (filteredThreads.isEmpty()) tr("Друзья") else tr("Начать чат"))
                }
                itemsIndexed(friendsWithoutThread, key = { _, person -> "start-${person.id}" }) { index, person ->
                    GroupedCard(
                        shape = groupedShape(index, friendsWithoutThread.size),
                        modifier = Modifier.padding(bottom = 4.dp),
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenChat(person.id)
                        }
                    ) {
                        PersonAvatarWithBadge(
                            person = person,
                            avatarUrl = social.avatarUrl(person.id, person.avatarV),
                            size = 48.dp,
                            listening = person.listeningNow(now)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = person.shownName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = tr("Нажмите, чтобы открыть чат"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (filteredRecent.isEmpty() && filteredThreads.isEmpty() && friendsWithoutThread.isEmpty()) {
                item(key = "chat-empty") {
                    Text(
                        text = if (q.isNotEmpty()) {
                            tr("Ничего не нашлось")
                        } else {
                            tr("Поделись треком или альбомом с другом — диалог появится здесь")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, start = 16.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentFriendItemCard(
    msg: ChatMessage,
    avatarUrl: String?,
    now: Long,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val fill = PanelColors.container
    Surface(
        onClick = onClick,
        shape = shape,
        color = glassFill(fill),
        contentColor = PanelColors.content,
        modifier = Modifier
            .width(156.dp)
            .glassOr(shape, fill)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PersonAvatar(
                    name = msg.senderShownName,
                    color = msg.senderColor,
                    imageUrl = avatarUrl,
                    size = 20.dp
                )
                Text(
                    text = msg.senderShownName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                msg.createdAtMs?.let { ts ->
                    Text(
                        text = compactAge(ts, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PanelColors.content.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (msg.isAlbum) Icons.Rounded.Album else Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = PanelColors.content.copy(alpha = 0.42f),
                    modifier = Modifier.size(34.dp)
                )
                if (!msg.coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = msg.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = msg.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    msg.isAlbum && msg.artist.isNotBlank() -> tr("Альбом · %s", msg.artist)
                    msg.isAlbum -> tr("Альбом")
                    msg.artist.isNotBlank() -> msg.artist
                    else -> serviceShortName(msg.service)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChatThreadRowCard(
    thread: ChatThreadSummary,
    myUserId: String?,
    avatarUrl: String?,
    shape: Shape,
    now: Long,
    onClick: () -> Unit
) {
    GroupedCard(
        shape = shape,
        modifier = Modifier.padding(bottom = 4.dp),
        onClick = onClick
    ) {
        PersonAvatar(
            name = thread.shownName,
            color = thread.color,
            imageUrl = avatarUrl,
            size = 48.dp
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = thread.shownName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                thread.lastMessage?.createdAtMs?.let { ts ->
                    Text(
                        text = compactAge(ts, now),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (thread.unreadCount > 0) PanelColors.accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val last = thread.lastMessage
                if (last != null) {
                    val mine = myUserId != null && last.senderId == myUserId
                    val prefix = if (mine) tr("Вы: ") else ""
                    val kindTag = if (last.isAlbum) tr("Альбом: ") else ""
                    val artistPart = last.artist.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
                    if (last.isAlbum) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Text(
                        text = "$prefix$kindTag${last.title}$artistPart",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = "@${thread.nick}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (thread.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                            .clip(CircleShape)
                            .background(PanelColors.accent)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = thread.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PanelColors.onAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val glass = LocalGlass.current
    val fill = MaterialTheme.colorScheme.surfaceContainerHigh
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(30.dp)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchFieldHeight)
            .glassOr(shape, fill),
        singleLine = true,
        shape = shape,
        textStyle = MaterialTheme.typography.titleMedium,
        placeholder = { Text(tr("Поиск друга или трека...")) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = PanelColors.accent
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = tr("Очистить"))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = if (glass) Color.Transparent else fill,
            unfocusedContainerColor = if (glass) Color.Transparent else fill,
            focusedBorderColor = PanelColors.accent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

// endregion

// region Chat Thread (Variant 2A · R1)

@Composable
internal fun ChatThreadScreen(
    peerId: String,
    onClose: () -> Unit,
    onOpenPerson: (String) -> Unit,
    onPlayTrackLink: (String) -> Unit,
    onOpenAlbumLink: (String) -> Unit,
    onLikeTrackLink: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val social = remember { Social.get(context) }
    val me by social.me.collectAsState()
    val session by social.session.collectAsState()
    val myId = me?.id ?: session?.userId
    val friendsAll by social.friends.collectAsState()
    val inbox by social.chatInbox.collectAsState()
    val changes by social.changes.collectAsState()

    var peerCard by remember(peerId) { mutableStateOf<Person?>(null) }
    var messages by remember(peerId) { mutableStateOf<List<ChatMessage>?>(null) }
    var error by remember(peerId) { mutableStateOf<String?>(null) }
    var activeMenuMsg by remember { mutableStateOf<ChatMessage?>(null) }
    var pickerExpanded by remember { mutableStateOf(false) }

    val knownFriend = remember(friendsAll, peerId) { friendsAll.orEmpty().firstOrNull { it.id == peerId } }
    val knownThread = remember(inbox, peerId) { inbox?.threads?.firstOrNull { it.peerId == peerId } }
    val peerName = knownFriend?.shownName ?: peerCard?.shownName ?: knownThread?.shownName ?: tr("Чат")
    val peerNick = knownFriend?.nick ?: peerCard?.nick ?: knownThread?.nick
    val peerColor = knownFriend?.color ?: peerCard?.color ?: knownThread?.color
    val peerAvatarV = knownFriend?.avatarV ?: peerCard?.avatarV ?: knownThread?.avatarV ?: 0
    val nowMs = remember(knownFriend, peerCard, changes) { System.currentTimeMillis() }
    val peerStatus = knownFriend?.trackLine(nowMs)?.let { line ->
        if (knownFriend.listeningNow(nowMs)) tr("Слушает: %s", line) else line
    } ?: peerCard?.trackLine(nowMs) ?: peerNick?.let { "@$it" }

    DisposableEffect(peerId) {
        val live = social.watchLive()
        ChatNotifications.setActivePeer(peerId)
        ChatNotifications.cancelForPeer(context, peerId)
        onDispose {
            ChatNotifications.clearActivePeer(peerId)
            live.close()
        }
    }

    LaunchedEffect(peerId) {
        if (knownFriend == null) {
            runCatching { social.profile(peerId) }.onSuccess { if (it != null) peerCard = it }
        }
    }

    LaunchedEffect(peerId, changes, inbox?.unreadTotal) {
        runCatching { social.loadChatMessages(peerId) }
            .onSuccess { loaded ->
                messages = loaded
                error = null
                val hasUnreadIncoming = myId != null && loaded.any { it.senderId != myId && it.readAt == null }
                if (hasUnreadIncoming) {
                    runCatching { social.markChatRead(peerId) }
                }
            }
            .onFailure {
                if (messages == null) error = it.socialMessage()
            }
    }

    fun applyReaction(msg: ChatMessage, newEmoji: String?, clearEmoji: Boolean, newRating: Int?, clearRating: Boolean) {
        val uid = myId ?: return
        val prev = messages ?: return
        val existing = msg.reactions.firstOrNull { it.userId == uid }
        val optimisticEmoji = when {
            clearEmoji -> null
            newEmoji != null -> newEmoji
            else -> existing?.emoji
        }
        val optimisticRating = when {
            clearRating -> null
            newRating != null -> newRating
            else -> existing?.rating
        }
        val nextReactions = msg.reactions.filterNot { it.userId == uid } +
            if (optimisticEmoji != null || optimisticRating != null) {
                listOf(ChatReaction(userId = uid, emoji = optimisticEmoji, rating = optimisticRating))
            } else {
                emptyList()
            }
        messages = prev.map { if (it.id == msg.id) it.copy(reactions = nextReactions) else it }
        scope.launch {
            runCatching {
                social.setChatReaction(
                    messageId = msg.id,
                    emoji = newEmoji,
                    clearEmoji = clearEmoji,
                    rating = newRating,
                    clearRating = clearRating
                )
            }.onSuccess { updated ->
                messages = messages?.map { if (it.id == msg.id) it.copy(reactions = updated) else it }
            }.onFailure { err ->
                messages = prev
                Toast.makeText(context, err.socialMessage(), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteMessage(msg: ChatMessage) {
        val prev = messages ?: return
        messages = prev.filterNot { it.id == msg.id }
        scope.launch {
            runCatching { social.deleteChatMessage(msg.id) }
                .onFailure { err ->
                    messages = prev
                    Toast.makeText(context, err.socialMessage(), Toast.LENGTH_SHORT).show()
                }
        }
    }

    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hasPlayer = LocalNowPlaying.current.trackId != null
    val listBottomPadding = navBottom + if (hasPlayer) MiniPlayerHeight + 24.dp else 20.dp
    val listState = rememberLazyListState()
    val newestFirst = remember(messages) { messages.orEmpty().asReversed() }
    val zone = remember { ZoneId.systemDefault() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullToClose(onClose)
            .pageGlass(MaterialTheme.colorScheme.background)
    ) {
        val headerBlock: @Composable (Modifier) -> Unit = { mod ->
            Row(
                modifier = mod
                    .clip(RoundedCornerShape(22.dp))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenPerson(peerId)
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PersonAvatar(
                    name = peerName,
                    color = peerColor,
                    imageUrl = social.avatarUrl(peerId, peerAvatarV),
                    size = 44.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peerName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!peerStatus.isNullOrBlank()) {
                        Text(
                            text = peerStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (knownFriend?.listeningNow(nowMs) == true) PanelColors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        val messagesList: @Composable (Modifier, PaddingValues) -> Unit = { mod, pad ->
            when {
                messages == null && error != null -> Box(mod.padding(top = 80.dp, start = 20.dp, end = 20.dp)) {
                    FormError(error)
                }
                messages == null -> Box(mod, contentAlignment = Alignment.Center) { AppLoadingIndicator() }
                newestFirst.isEmpty() -> Box(mod.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = tr("Отправь %s трек или альбом через «Поделиться»", peerName),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = pad,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = mod
                ) {
                    itemsIndexed(newestFirst, key = { _, m -> m.id }) { idx, msg ->
                        val isMine = myId != null && msg.senderId == myId
                        val curDay = msg.createdAtMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                        val olderDay = newestFirst.getOrNull(idx + 1)?.createdAtMs?.let {
                            Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                        ) {
                            if (curDay != null && curDay != olderDay) {
                                DayDividerPill(curDay, zone)
                            }
                            ChatBubble2AR1(
                                msg = msg,
                                isMine = isMine,
                                myUserId = myId,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (msg.isAlbum) onOpenAlbumLink(msg.itemUrl) else onPlayTrackLink(msg.itemUrl)
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pickerExpanded = false
                                    activeMenuMsg = msg
                                },
                                onEmojiChipClick = { emoji, alreadyMine ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    applyReaction(
                                        msg = msg,
                                        newEmoji = if (alreadyMine) null else emoji,
                                        clearEmoji = alreadyMine,
                                        newRating = null,
                                        clearRating = false
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        if (isLandscape()) {
            Row(modifier = Modifier.fillMaxSize()) {
                messagesList(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start)),
                    PaddingValues(
                        start = 16.dp,
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp,
                        end = 16.dp,
                        bottom = navBottom + 24.dp
                    )
                )
                Column(
                    modifier = Modifier
                        .width(LandscapePaneWidth)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.End))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                ) {
                    headerBlock(Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                headerBlock(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
                messagesList(
                    Modifier.fillMaxWidth().weight(1f),
                    PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = listBottomPadding)
                )
            }
        }
    }

    val pressed = activeMenuMsg
    if (pressed != null) {
        val currentMsg = messages?.firstOrNull { it.id == pressed.id } ?: pressed
        val ownReaction = currentMsg.reactions.firstOrNull { myId != null && it.userId == myId }
        val ownEmoji = ownReaction?.emoji
        val ownRating = ownReaction?.rating
        val isMine = myId != null && currentMsg.senderId == myId
        Dialog(
            onDismissRequest = { activeMenuMsg = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.56f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { activeMenuMsg = null },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {},
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                ) {
                    // 1. Floating reaction & R1 rating capsule
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = PanelColors.container,
                        contentColor = PanelColors.content,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                QUICK_EMOJIS.forEach { emoji ->
                                    val selected = ownEmoji == emoji
                                    EmojiPickerCell(emoji = emoji, selected = selected) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        activeMenuMsg = null
                                        applyReaction(
                                            msg = currentMsg,
                                            newEmoji = if (selected) null else emoji,
                                            clearEmoji = selected,
                                            newRating = null,
                                            clearRating = false
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { pickerExpanded = !pickerExpanded },
                                    shape = CircleShape,
                                    color = PanelColors.content.copy(alpha = 0.12f),
                                    contentColor = PanelColors.content,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (pickerExpanded) Icons.Rounded.Remove else Icons.Rounded.Add,
                                            contentDescription = tr("Ещё эмодзи"),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            AnimatedVisibility(visible = pickerExpanded) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    EXTRA_EMOJIS.forEach { emoji ->
                                        val selected = ownEmoji == emoji
                                        EmojiPickerCell(emoji = emoji, selected = selected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            activeMenuMsg = null
                                            applyReaction(
                                                msg = currentMsg,
                                                newEmoji = if (selected) null else emoji,
                                                clearEmoji = selected,
                                                newRating = null,
                                                clearRating = false
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = PanelColors.content.copy(alpha = 0.10f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RATING_OPTIONS.forEach { option ->
                                    val selected = ownRating == option.score
                                    val fill = option.color ?: PanelColors.accent
                                    val textTint = if (option.color == null) PanelColors.onAccent else Color.White
                                    val scale by animateFloatAsState(
                                        targetValue = if (selected) 1.12f else 1f,
                                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                                        label = "ratingScale"
                                    )
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                activeMenuMsg = null
                                                applyReaction(
                                                    msg = currentMsg,
                                                    newEmoji = null,
                                                    clearEmoji = false,
                                                    newRating = if (selected) null else option.score,
                                                    clearRating = selected
                                                )
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .graphicsLayer {
                                                    scaleX = scale
                                                    scaleY = scale
                                                }
                                                .clip(option.shape)
                                                .background(if (selected) fill else fill.copy(alpha = 0.24f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = option.score.toString(),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected) textTint else PanelColors.content
                                            )
                                        }
                                        Text(
                                            text = tr(option.label),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) PanelColors.accent else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Lifted bubble preview
                    ChatBubble2AR1(
                        msg = currentMsg,
                        isMine = isMine,
                        myUserId = myId,
                        onClick = {},
                        onLongClick = {},
                        onEmojiChipClick = { _, _ -> }
                    )

                    // 3. Context menu card
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = PanelColors.container,
                        contentColor = PanelColors.content,
                        modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
                    ) {
                        Column {
                            if (currentMsg.isAlbum) {
                                ContextMenuActionRow(
                                    icon = Icons.Rounded.Album,
                                    label = tr("Открыть альбом"),
                                    onClick = {
                                        activeMenuMsg = null
                                        onOpenAlbumLink(currentMsg.itemUrl)
                                    }
                                )
                            } else {
                                ContextMenuActionRow(
                                    icon = Icons.Rounded.FavoriteBorder,
                                    label = tr("В любимое"),
                                    onClick = {
                                        activeMenuMsg = null
                                        onLikeTrackLink(currentMsg.itemUrl)
                                    }
                                )
                                HorizontalDivider(color = PanelColors.content.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    icon = Icons.Rounded.PlayArrow,
                                    label = tr("Открыть трек"),
                                    onClick = {
                                        activeMenuMsg = null
                                        onPlayTrackLink(currentMsg.itemUrl)
                                    }
                                )
                            }
                            if (isMine) {
                                HorizontalDivider(color = PanelColors.content.copy(alpha = 0.08f))
                                ContextMenuActionRow(
                                    icon = Icons.Rounded.DeleteOutline,
                                    label = tr("Удалить"),
                                    tint = MaterialTheme.colorScheme.error,
                                    onClick = {
                                        activeMenuMsg = null
                                        deleteMessage(currentMsg)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDividerPill(date: LocalDate, zone: ZoneId) {
    val today = remember(zone) { LocalDate.now(zone) }
    val label = remember(date, today) {
        when (date) {
            today -> tr("Сегодня")
            today.minusDays(1) -> tr("Вчера")
            else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = PanelColors.container.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ChatBubble2AR1(
    msg: ChatMessage,
    isMine: Boolean,
    myUserId: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEmojiChipClick: (emoji: String, alreadyMine: Boolean) -> Unit
) {
    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 8.dp)
    } else {
        RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 22.dp)
    }
    val fill = if (isMine) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        PanelColors.container
    }
    val textColor = if (isMine) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        PanelColors.content
    }
    val mutedColor = textColor.copy(alpha = 0.72f)
    val glassAlpha = if (isMine) 0.78f else GlassAlpha
    val rating = remember(msg.reactions, myUserId) { displayRatingOf(msg.reactions, myUserId) }
    val grouped = remember(msg.reactions, myUserId) { groupedEmojiReactions(msg.reactions, myUserId) }

    Surface(
        shape = bubbleShape,
        color = glassFill(fill),
        contentColor = textColor,
        modifier = Modifier
            .widthIn(min = 210.dp, max = 324.dp)
            .glassOr(bubbleShape, fill, glassAlpha)
            .clip(bubbleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Incoming: R1 stamp on the LEFT
                if (!isMine && rating != null) {
                    RatingStamp(rating = rating)
                }

                // Cover without any play-button overlay
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(textColor.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (msg.isAlbum) Icons.Rounded.Album else Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = mutedColor,
                        modifier = Modifier.size(22.dp)
                    )
                    if (!msg.coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = msg.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = msg.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (msg.artist.isNotBlank()) {
                        Text(
                            text = msg.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (msg.isAlbum) {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = mutedColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                val albumMeta = msg.trackCount?.takeIf { it > 0 }?.let {
                                    tr("Альбом · %s", albumTracksWord(it))
                                } ?: tr("Альбом")
                                Text(
                                    text = albumMeta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mutedColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                val trackMeta = listOfNotNull(
                                    msg.durationMs?.takeIf { it > 0L }?.let(::formatChatDuration),
                                    serviceShortName(msg.service)
                                ).joinToString(" · ")
                                Text(
                                    text = trackMeta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mutedColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        msg.createdAtMs?.let { ts ->
                            Text(
                                text = formatHourMinute(ts),
                                style = MaterialTheme.typography.labelSmall,
                                color = mutedColor
                            )
                        }
                    }
                }

                // Outgoing: R1 stamp on the RIGHT
                if (isMine && rating != null) {
                    RatingStamp(rating = rating)
                }
            }

            // Emoji row collapses tightly when absent and animates shrinking when removed
            AnimatedVisibility(
                visible = grouped.isNotEmpty(),
                enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    grouped.forEach { reaction ->
                        val chipFill = if (reaction.mine) PanelColors.accent else textColor.copy(alpha = 0.10f)
                        val chipInk = if (reaction.mine) PanelColors.onAccent else textColor
                        Surface(
                            onClick = { onEmojiChipClick(reaction.emoji, reaction.mine) },
                            shape = CircleShape,
                            color = chipFill,
                            contentColor = chipInk
                        ) {
                            Text(
                                text = if (reaction.count > 1) "${reaction.emoji} ${reaction.count}" else reaction.emoji,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingStamp(rating: Int) {
    val option = RATING_OPTIONS.firstOrNull { it.score == rating } ?: return
    val fill = option.color ?: PanelColors.accent
    val ink = if (option.color == null) PanelColors.onAccent else Color.White
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(option.shape)
            .background(fill),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = option.score.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ink
        )
    }
}

@Composable
private fun EmojiPickerCell(emoji: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) PanelColors.accent else Color.Transparent,
        modifier = Modifier.size(34.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = emoji, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ContextMenuActionRow(
    icon: ImageVector,
    label: String,
    tint: Color = PanelColors.content,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// endregion

// region Share to Friend Panel (Variant B2 · S1 — below all tiles in TrackActionsDialog & CollectionSheet)

@Composable
internal fun ShareToFriendPanel(
    payload: ChatSharePayload,
    friends: List<Person>,
    social: Social,
    onSent: () -> Unit,
    onExternalShare: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val selectedIds = remember(payload.itemUrl) { mutableStateListOf<String>() }
    var sending by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(PanelColors.container)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = if (selectedIds.isEmpty()) {
                tr("Отправить в чат")
            } else {
                tr("Выбрано: %s", selectedIds.size)
            },
            style = MaterialTheme.typography.labelMedium,
            color = PanelColors.content.copy(alpha = 0.72f)
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(friends, key = { "share-friend-${it.id}" }) { person ->
                val isSelected = person.id in selectedIds
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !sending) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isSelected) selectedIds.remove(person.id) else selectedIds.add(person.id)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        PersonAvatar(
                            name = person.shownName,
                            color = person.color,
                            imageUrl = social.avatarUrl(person.id, person.avatarV),
                            size = 52.dp,
                            shape = AvatarShape,
                            modifier = if (isSelected) {
                                Modifier.border(2.5.dp, PanelColors.accent, AvatarShape)
                            } else {
                                Modifier
                            }
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(PanelColors.accent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = PanelColors.onAccent,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = person.shownName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) PanelColors.accent else PanelColors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // B2 · S1 bottom row: text-only send pill + 48dp external share icon tile right beside it
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (selectedIds.isEmpty() || sending) return@Button
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    sending = true
                    scope.launch {
                        runCatching {
                            social.sendChatItem(selectedIds.toList(), payload)
                        }.onSuccess {
                            Toast.makeText(context, tr("Отправлено в чат"), Toast.LENGTH_SHORT).show()
                            sending = false
                            onSent()
                        }.onFailure { err ->
                            sending = false
                            Toast.makeText(context, err.socialMessage(), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = selectedIds.isNotEmpty() && !sending,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanelColors.accent,
                    contentColor = PanelColors.onAccent,
                    disabledContainerColor = PanelColors.content.copy(alpha = 0.12f),
                    disabledContentColor = PanelColors.content.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            ) {
                if (sending) {
                    AppLoadingIndicator(modifier = Modifier.size(22.dp), color = PanelColors.onAccent)
                } else {
                    val label = when (selectedIds.size) {
                        0 -> tr("Выбери друга")
                        1 -> tr("Отправить другу")
                        else -> tr("Отправить (%s)", selectedIds.size)
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExternalShare()
                },
                shape = RoundedCornerShape(16.dp),
                color = PanelColors.content.copy(alpha = 0.10f),
                contentColor = PanelColors.content,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = tr("Отправить через другое приложение"),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// endregion

// region Helpers

private fun compactAge(ms: Long, now: Long): String {
    val minutes = ((now - ms) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1 -> tr("сейчас")
        minutes < 60 -> "${minutes}м"
        minutes < 24 * 60 -> "${minutes / 60}ч"
        else -> "${minutes / (24 * 60)}д"
    }
}

private fun formatHourMinute(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

private fun formatChatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}

private fun albumTracksWord(count: Int): String {
    if (com.example.myapplication.i18n.english()) return if (count == 1) "1 track" else "$count tracks"
    val mod10 = count % 10
    val mod100 = count % 100
    val word = when {
        mod10 == 1 && mod100 != 11 -> "трек"
        mod10 in 2..4 && mod100 !in 12..14 -> "трека"
        else -> "треков"
    }
    return "$count $word"
}

private fun serviceShortName(service: String): String = when (service) {
    "youtube" -> "YouTube"
    "soundcloud" -> "SoundCloud"
    "yandex" -> tr("Яндекс")
    "spotify" -> "Spotify"
    else -> service
}

// endregion
