package com.example.myapplication.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** A message of a live stream's chat: who, what (text and emoji pictures), and a paid one's amount. */
data class LiveChatMessage(val id: String, val author: String, val parts: List<LiveChatPart>, val paid: String? = null)

sealed interface LiveChatPart {
    data class Text(val text: String) : LiveChatPart

    /** One of YouTube's own emoji, a picture: [key] names it, [url] is its picture. */
    data class Emoji(val key: String, val url: String) : LiveChatPart
}

/**
 * A YouTube live stream's chat, as youtube.com's own page reads it: the watch page's `next` names
 * the chat's first continuation, and each `get_live_chat` answers with what has been said since
 * and the next one. Signed in as the YouTube Music session when there is one, which is also what
 * lets a message be sent ([send]). Only while [messages] is collected is anything asked for: the
 * chat closed, it stops.
 */
class YouTubeLiveChat(private val videoId: String, private val auth: () -> YtAuth?) {
    private val _canSend = MutableStateFlow(false)
    /** Whether a message can be sent: signed in, and the chat open to the account. */
    val canSend: StateFlow<Boolean> = _canSend.asStateFlow()

    private val _sendBlocked = MutableStateFlow<String?>(null)
    /**
     * Why no message can be sent, in YouTube's own words where it gives them ("Чат доступен
     * только подписчикам…"); null while it isn't known yet, or when one can.
     */
    val sendBlocked: StateFlow<String?> = _sendBlocked.asStateFlow()

    @Volatile
    private var sendParams: String? = null

    /** The chat, newest last, whenever something new is said, for as long as it is collected. */
    val messages: Flow<List<LiveChatMessage>> = flow {
        var continuation = firstContinuation() ?: throw IOException("У этой трансляции нет чата")
        val kept = ArrayDeque<LiveChatMessage>()
        val seen = HashSet<String>()
        var first = true
        while (true) {
            val page = post("live_chat/get_live_chat", JsonObject().apply { addProperty("continuation", continuation) })
                .at("continuationContents", "liveChatContinuation")
            page?.at("actionPanel")?.let(::readActionPanel) ?: if (first) readActionPanel(null) else Unit
            var added = false
            page.arr("actions").forEach { action ->
                val message = parseMessage(action) ?: return@forEach
                if (!seen.add(message.id)) return@forEach
                kept.addLast(message)
                added = true
                if (kept.size > KEPT) seen.remove(kept.removeFirst().id)
            }
            if (added || first) emit(kept.toList())
            first = false
            val next = page.arr("continuations").firstOrNull()?.asObjectOrNull()?.entrySet()?.firstOrNull()?.value
            continuation = next.str("continuation") ?: break
            val timeout = next.at("timeoutMs")?.takeIf { it.isJsonPrimitive }?.asLong ?: MAX_WAIT_MS
            delay(timeout.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS))
        }
    }.flowOn(Dispatchers.IO)

    /** Sends [text] to the chat as the signed-in account; it comes back in the next read. */
    suspend fun send(text: String): Boolean = withContext(Dispatchers.IO) {
        val params = sendParams ?: return@withContext false
        runCatching {
            post("live_chat/send_message", JsonObject().apply {
                addProperty("params", params)
                addProperty("clientMessageId", UUID.randomUUID().toString())
                add("richMessage", JsonObject().apply {
                    add("textSegments", JsonArray().apply { add(JsonObject().apply { addProperty("text", text) }) })
                })
            })
            true
        }.getOrDefault(false)
    }

    /**
     * The chat's panel for writing: its send button's parameters when the account may write, else
     * what YouTube says in its place (subscribers only, members only, slow mode's wait…).
     */
    private fun readActionPanel(panel: JsonElement?) {
        val params = panel?.findAll("sendLiveChatMessageEndpoint")?.firstNotNullOfOrNull { it.str("params") }
        if (params != null) {
            sendParams = params
            _canSend.value = true
            _sendBlocked.value = null
            return
        }
        sendParams = null
        _canSend.value = false
        val said = panel?.let(::panelMessage)
        _subscribersOnly.value = said != null && SUBSCRIBERS.containsMatchIn(said)
        _sendBlocked.value = when {
            auth()?.sapisid == null -> "Войдите в YouTube Music в настройках, чтобы писать"
            said != null -> said
            else -> "YouTube не даёт писать в этот чат"
        }
    }

    /**
     * What the panel says, its buttons and links left out: "Только для подписчиков", not the
     * "Только для подписчиковПодробнее" of every text in it run together.
     */
    private fun panelMessage(panel: JsonElement): String? {
        val blocks = mutableListOf<String>()
        fun walk(node: JsonElement, inButton: Boolean) {
            when {
                node.isJsonObject -> node.asJsonObject.entrySet().forEach { (name, value) ->
                    val button = inButton || name.contains("button", ignoreCase = true) || name.endsWith("Endpoint") ||
                        name.endsWith("Command")
                    when {
                        button -> Unit
                        name == "runs" && value.isJsonArray -> value.asJsonArray
                            .filter { it.at("navigationEndpoint") == null }
                            .mapNotNull { it.str("text") }
                            .filterNot { it.trim().lowercase() in LINK_WORDS }
                            .joinToString("")
                            .trim()
                            .takeIf { it.isNotEmpty() }
                            ?.let(blocks::add)
                        name == "simpleText" && value.isJsonPrimitive ->
                            value.asString.trim().takeIf { it.isNotEmpty() && it.lowercase() !in LINK_WORDS }?.let(blocks::add)
                        else -> walk(value, false)
                    }
                }
                node.isJsonArray -> node.asJsonArray.forEach { walk(it, inButton) }
            }
        }
        walk(panel, false)
        val said = blocks.distinct()
        // A title and its explanation read as two sentences.
        return when (said.size) {
            0 -> null
            1 -> said.single()
            else -> said.joinToString(" ") { if (it.last() in ".!?…") it else "$it." }
        }
    }

    private val _subscribersOnly = MutableStateFlow(false)
    /** Whether only the channel's subscribers may write: subscribing is then the way in. */
    val subscribersOnly: StateFlow<Boolean> = _subscribersOnly.asStateFlow()

    @Volatile
    private var ownerChannel: String? = null

    /** The channel broadcasting, as its watch page names it, once the chat has been read. */
    val channelId: String? get() = ownerChannel

    /**
     * Subscribes the account to the channel broadcasting, for a chat only its subscribers may
     * write in, and reads what it may do now (YouTube may still ask to wait a few minutes).
     */
    suspend fun subscribe(): Boolean {
        val channel = ownerChannel ?: return false
        val done = try {
            YouTubeWeb.subscribe(channel, true, auth())
            true
        } catch (e: IOException) {
            false
        }
        if (done) recheck()
        return done
    }

    /**
     * Reads the panel for writing again, as a fresh chat page opens it: after subscribing, say,
     * when what the account may do has changed.
     */
    suspend fun recheck() = withContext(Dispatchers.IO) {
        runCatching {
            val continuation = firstContinuation() ?: return@runCatching
            val page = post("live_chat/get_live_chat", JsonObject().apply { addProperty("continuation", continuation) })
                .at("continuationContents", "liveChatContinuation")
            readActionPanel(page?.at("actionPanel"))
        }
        Unit
    }

    /** The chat's first continuation: its "top chat", as the watch page opens it. */
    private fun firstContinuation(): String? {
        val next = post("next", JsonObject().apply { addProperty("videoId", videoId) })
        next.findAll("videoOwnerRenderer").firstOrNull()
            ?.str("navigationEndpoint", "browseEndpoint", "browseId")
            ?.takeIf { it.startsWith("UC") }
            ?.let { ownerChannel = it }
        return next.findAll("liveChatRenderer").firstOrNull()
            ?.arr("continuations")
            ?.firstNotNullOfOrNull { it.str("reloadContinuationData", "continuation") }
    }

    private fun parseMessage(action: JsonElement): LiveChatMessage? {
        val item = action.at("addChatItemAction", "item") ?: return null
        val text = item.at("liveChatTextMessageRenderer")
        val paid = item.at("liveChatPaidMessageRenderer")
        val renderer = text ?: paid ?: return null
        val id = renderer.str("id") ?: return null
        val author = renderer.str("authorName", "simpleText") ?: return null
        val parts = renderer.arr("message", "runs").mapNotNull { run ->
            run.str("text")?.let { return@mapNotNull LiveChatPart.Text(it) }
            val emoji = run.at("emoji") ?: return@mapNotNull null
            val emojiId = emoji.str("emojiId").orEmpty()
            // A standard emoji is the character itself; YouTube's own (":face-red-heart-shape:")
            // and a channel's are pictures.
            if (emojiId.isNotEmpty() && !emojiId.contains('/') && emojiId.length <= 8) {
                LiveChatPart.Text(emojiId)
            } else {
                val url = emoji.arr("image", "thumbnails").lastOrNull().str("url")
                val key = emoji.arr("shortcuts").firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString ?: emojiId
                if (url != null) LiveChatPart.Emoji(key, if (url.startsWith("//")) "https:$url" else url) else LiveChatPart.Text(key)
            }
        }
        if (parts.isEmpty() && paid == null) return null
        return LiveChatMessage(id, author, parts, paid?.str("purchaseAmountText", "simpleText"))
    }

    private fun post(endpoint: String, body: JsonObject): JsonElement = YouTubeWeb.post(endpoint, body, auth())

    private companion object {
        // What is kept on screen; the oldest go as new ones come.
        const val KEPT = 150
        // YouTube asks for a chat every ten seconds, and a whole batch then came at once: every few
        // is still light, and reads as a conversation.
        const val MIN_WAIT_MS = 2_000L
        const val MAX_WAIT_MS = 5_000L
        // A link after the reason, not part of it.
        val LINK_WORDS = setOf("подробнее", "learn more", "подробнее…", "подробнее...")
        val SUBSCRIBERS = Regex("подписч|subscri", RegexOption.IGNORE_CASE)
    }
}

private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

private fun JsonElement?.at(vararg path: String): JsonElement? {
    var node = this
    for (key in path) node = node?.asObjectOrNull()?.get(key) ?: return null
    return node
}

private fun JsonElement?.str(vararg path: String): String? =
    at(*path)?.takeIf { it.isJsonPrimitive }?.asString

private fun JsonElement?.arr(vararg path: String): List<JsonElement> =
    at(*path)?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()

private fun JsonElement.findAll(key: String, into: MutableList<JsonElement> = mutableListOf()): List<JsonElement> {
    when {
        isJsonObject -> asJsonObject.entrySet().forEach { (name, value) ->
            if (name == key) into += value
            value.findAll(key, into)
        }
        isJsonArray -> asJsonArray.forEach { it.findAll(key, into) }
    }
    return into
}
