package com.example.myapplication.data

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

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
            page?.findAll("sendLiveChatMessageEndpoint")?.firstNotNullOfOrNull { it.str("params") }?.let {
                sendParams = it
                _canSend.value = true
            }
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

    /** The chat's first continuation: its "top chat", as the watch page opens it. */
    private fun firstContinuation(): String? {
        val next = post("next", JsonObject().apply { addProperty("videoId", videoId) })
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

    private fun post(endpoint: String, body: JsonObject): JsonElement {
        val session = auth()
        body.add("context", JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "WEB")
                addProperty("clientVersion", CLIENT_VERSION)
                addProperty("hl", "ru")
                addProperty("gl", "RU")
                session?.visitorData?.let { addProperty("visitorData", it) }
            })
        })
        val request = Request.Builder()
            .url("$API$endpoint?prettyPrint=false")
            .header("User-Agent", USER_AGENT)
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .apply {
                val sapisid = session?.sapisid
                if (session != null && sapisid != null) {
                    header("Cookie", session.cookie)
                    header("Authorization", sapisidAuthorization(sapisid, ORIGIN))
                    header("X-Goog-AuthUser", session.authUser)
                }
            }
            .post(body.toString().toRequestBody(JSON))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube HTTP ${response.code}")
            return JsonParser.parseString(response.body?.string().orEmpty())
        }
    }

    private companion object {
        const val ORIGIN = "https://www.youtube.com"
        const val API = "$ORIGIN/youtubei/v1/"
        const val CLIENT_VERSION = "2.20260925.01.00"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
        // What is kept on screen; the oldest go as new ones come.
        const val KEPT = 150
        // YouTube asks for a chat every ten seconds, and a whole batch then came at once: every few
        // is still light, and reads as a conversation.
        const val MIN_WAIT_MS = 2_000L
        const val MAX_WAIT_MS = 5_000L
        val JSON = "application/json".toMediaType()

        val http: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
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
