package com.example.myapplication.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A message of a live stream's chat. */
data class LiveChatMessage(val id: String, val author: String, val text: String, val paid: String? = null)

/**
 * A YouTube live stream's chat, as youtube.com's own page reads it, signed out: the watch page's
 * `next` names the chat's first continuation, and each `get_live_chat` answers with what has been
 * said since and the next one. Only while collected: nothing is asked for once the chat is closed.
 */
object YouTubeLiveChat {
    private const val API = "https://www.youtube.com/youtubei/v1/"
    private const val CLIENT_VERSION = "2.20260925.01.00"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    // What is kept on screen; the oldest go as new ones come.
    private const val KEPT = 150
    // YouTube asks for a chat every ten seconds, and a whole batch then came at once: every few
    // is still light, and reads as a conversation.
    private const val MIN_WAIT_MS = 2_000L
    private const val MAX_WAIT_MS = 5_000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    /** The chat of [videoId], newest last, whenever something new is said, for as long as it is collected. */
    fun messages(videoId: String): Flow<List<LiveChatMessage>> = flow {
        var continuation = firstContinuation(videoId) ?: throw IOException("У этой трансляции нет чата")
        val kept = ArrayDeque<LiveChatMessage>()
        val seen = HashSet<String>()
        var first = true
        while (true) {
            val page = post("live_chat/get_live_chat", JsonObject().apply { addProperty("continuation", continuation) })
                .at("continuationContents", "liveChatContinuation")
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

    /** The chat's first continuation: its "top chat", as the watch page opens it. */
    private fun firstContinuation(videoId: String): String? {
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
        val body = renderer.arr("message", "runs").joinToString("") { run ->
            run.str("text")
                // An emoji: the character itself, or a channel's own by its short name.
                ?: run.str("emoji", "emojiId")?.takeIf { !it.contains('/') && it.length <= 8 }
                ?: run.arr("emoji", "shortcuts").firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString
                ?: ""
        }
        if (body.isBlank() && paid == null) return null
        return LiveChatMessage(id, author, body, paid?.str("purchaseAmountText", "simpleText"))
    }

    private fun post(endpoint: String, body: JsonObject): JsonElement {
        body.add("context", JsonObject().apply {
            add("client", JsonObject().apply {
                addProperty("clientName", "WEB")
                addProperty("clientVersion", CLIENT_VERSION)
                addProperty("hl", "ru")
                addProperty("gl", "RU")
            })
        })
        val request = Request.Builder()
            .url("$API$endpoint?prettyPrint=false")
            .header("User-Agent", USER_AGENT)
            .header("Origin", "https://www.youtube.com")
            .post(body.toString().toRequestBody(json))
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube HTTP ${response.code}")
            return JsonParser.parseString(response.body?.string().orEmpty())
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
}
