package com.example.myapplication.data.social

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * What changes on the server, told as it changes: Supabase Realtime's channel over a WebSocket,
 * for the rows of now_playing, friendships and profiles the account may see — the database's own
 * row rules decide which: a friend's track, its own friendships. [onChange] hears the table.
 *
 * Open only while a screen showing friends is ([watch]), and a while after, for the next one; a
 * dropped connection is made again, a little later each time.
 */
internal class Realtime(private val supabase: Supabase, private val onChange: (table: String) -> Unit) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private var watchers = 0
    private var socket: WebSocket? = null
    private var connecting: Job? = null
    private var heartbeat: Job? = null
    private var closing: Job? = null
    private var ref = 0
    private var failures = 0

    /** Keeps the channel open until the handle is closed: a screen's, while it shows friends. */
    fun watch(): AutoCloseable {
        synchronized(lock) {
            watchers++
            closing?.cancel()
            if (socket == null && connecting?.isActive != true) connect(0L)
        }
        return AutoCloseable { release() }
    }

    private fun release() {
        synchronized(lock) {
            watchers = (watchers - 1).coerceAtLeast(0)
            if (watchers > 0) return
            closing?.cancel()
            closing = scope.launch {
                delay(LINGER_MS)
                synchronized(lock) { if (watchers == 0) disconnect() }
            }
        }
    }

    private fun connect(after: Long) {
        connecting = scope.launch {
            delay(after)
            val token = runCatching { supabase.accessToken() }.getOrNull() ?: return@launch
            val url = SupabaseConfig.url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") +
                "/realtime/v1/websocket?apikey=${SupabaseConfig.key}&vsn=1.0.0"
            synchronized(lock) {
                if (watchers == 0 || socket != null) return@launch
                socket = http.newWebSocket(Request.Builder().url(url).build(), Listener(token))
            }
        }
    }

    private fun disconnect() {
        connecting?.cancel()
        heartbeat?.cancel()
        socket?.close(1000, null)
        socket = null
    }

    private fun send(socket: WebSocket, topic: String, event: String, payload: JsonObject) {
        val message = JsonObject().apply {
            addProperty("topic", topic)
            addProperty("event", event)
            add("payload", payload)
            addProperty("ref", (++ref).toString())
            if (topic == TOPIC) addProperty("join_ref", "1")
        }
        socket.send(message.toString())
    }

    private inner class Listener(private var token: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            failures = 0
            val changes = JsonArray().apply {
                TABLES.forEach { table ->
                    add(JsonObject().apply {
                        addProperty("event", "*")
                        addProperty("schema", "public")
                        addProperty("table", table)
                    })
                }
            }
            send(webSocket, TOPIC, "phx_join", JsonObject().apply {
                add("config", JsonObject().apply {
                    add("broadcast", JsonObject().apply { addProperty("self", false) })
                    add("presence", JsonObject().apply { addProperty("key", "") })
                    add("postgres_changes", changes)
                })
                addProperty("access_token", token)
            })
            heartbeat?.cancel()
            heartbeat = scope.launch {
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    send(webSocket, "phoenix", "heartbeat", JsonObject())
                    // The token runs out after an hour: the channel is told the new one, or it
                    // would be closed on the old one.
                    val fresh = runCatching { supabase.accessToken() }.getOrNull()
                    if (fresh != null && fresh != token) {
                        token = fresh
                        send(webSocket, TOPIC, "access_token", JsonObject().apply { addProperty("access_token", fresh) })
                    }
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val message = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
            when (message.get("event")?.asString) {
                "postgres_changes" -> {
                    val table = message.getAsJsonObject("payload")?.getAsJsonObject("data")?.get("table")?.asString
                    if (table != null) onChange(table)
                }
                "phx_reply" -> {
                    val status = message.getAsJsonObject("payload")?.get("status")?.asString
                    if (status == "error") Log.w(TAG, "Realtime refused: $text")
                }
                "system" -> Log.d(TAG, "Realtime: ${message.get("payload")}")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = dropped(webSocket)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Realtime connection lost: $t")
            dropped(webSocket)
        }

        private fun dropped(webSocket: WebSocket) {
            synchronized(lock) {
                if (socket !== webSocket) return
                heartbeat?.cancel()
                socket = null
                if (watchers > 0) {
                    failures++
                    connect((RETRY_MS * failures).coerceAtMost(MAX_RETRY_MS))
                }
            }
        }
    }

    private companion object {
        const val TAG = "Realtime"
        const val TOPIC = "realtime:friends"
        val TABLES = listOf("now_playing", "friendships", "profiles")
        const val HEARTBEAT_MS = 25_000L
        const val LINGER_MS = 15_000L
        const val RETRY_MS = 3_000L
        const val MAX_RETRY_MS = 60_000L
    }
}
