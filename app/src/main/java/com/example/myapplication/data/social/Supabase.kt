package com.example.myapplication.data.social

import android.content.Context
import android.util.Log
import com.example.myapplication.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Where the accounts live, and how a nick becomes the email Supabase Auth signs in with. */
internal object SupabaseConfig {
    val url: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val key: String = BuildConfig.SUPABASE_KEY

    /**
     * Never a real domain (RFC 2606), so no mail can go anywhere even if the project's "Confirm
     * email" were turned on. The database checks only the part before the @ against the nick.
     */
    const val EMAIL_DOMAIN = "youcloud.invalid"

    val configured: Boolean get() = url.isNotBlank() && key.isNotBlank()

    fun emailOf(nick: String) = "${nick.lowercase()}@$EMAIL_DOMAIN"
}

/** A signed-in session: the access token renewed with the refresh token as it runs out. */
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    /** Seconds since the epoch. */
    val expiresAt: Long,
    val userId: String
)

/**
 * What Supabase answered instead: [code] is its machine-readable reason where it gives one
 * ("user_already_exists", "invalid_credentials", "P0001"...), [status] the HTTP status, 0 when
 * there was no answer at all.
 */
class SupabaseException(val status: Int, val code: String?, message: String) : IOException(message)

/**
 * The little of Supabase the app needs, straight over its REST endpoints with the app's own
 * OkHttp and Gson: Auth (sign up and in with a password, renew, sign out), the database through
 * PostgREST (functions and rows), Storage (the avatar) and Edge Functions.
 *
 * One for the process: the screens and the playback service share the session.
 */
internal class Supabase private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("social", Context.MODE_PRIVATE)
    private val gson = Gson()
    // For a row's changes: a column set to null is cleared, not left out as Gson leaves nulls.
    private val changes = com.google.gson.GsonBuilder().serializeNulls().create()
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private val refreshLock = Mutex()

    private val _session = MutableStateFlow(readSession())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    // region Auth

    /** A new account; Auth must hand back a session straight away (no email to confirm). */
    suspend fun signUp(email: String, password: String, data: Map<String, Any?>): AuthSession {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
            add("data", gson.toJsonTree(data))
        }
        val answer = call(authRequest("signup").post(body.toBody()), authorized = false).asJsonObject
        if (!answer.has("access_token")) {
            throw SupabaseException(200, "email_confirmation_required", "Confirm email is on in the project's Auth settings")
        }
        return keep(answer)
    }

    suspend fun signIn(email: String, password: String): AuthSession {
        val body = JsonObject().apply {
            addProperty("email", email)
            addProperty("password", password)
        }
        return keep(call(authRequest("token?grant_type=password").post(body.toBody()), authorized = false).asJsonObject)
    }

    /** Signs out here whatever the server says: the session is let go of on the phone either way. */
    suspend fun signOut() {
        val session = _session.value ?: return
        runCatching {
            call(
                authRequest("logout").header("Authorization", "Bearer ${session.accessToken}").post(EMPTY_JSON.toBody()),
                authorized = false
            )
        }
        forget()
    }

    suspend fun updatePassword(password: String) {
        val body = JsonObject().apply { addProperty("password", password) }
        call(authRequest("user").put(body.toBody()))
    }

    /** The access token, renewed first if it runs out within a minute. */
    suspend fun accessToken(): String {
        val current = _session.value ?: throw SupabaseException(401, "not_signed_in", "Not signed in")
        if (current.expiresAt - nowSeconds() > 60) return current.accessToken
        return refreshLock.withLock {
            val again = _session.value ?: throw SupabaseException(401, "not_signed_in", "Not signed in")
            if (again.expiresAt - nowSeconds() > 60) return@withLock again.accessToken
            val body = JsonObject().apply { addProperty("refresh_token", again.refreshToken) }
            try {
                keep(call(authRequest("token?grant_type=refresh_token").post(body.toBody()), authorized = false).asJsonObject).accessToken
            } catch (e: SupabaseException) {
                // The refresh token itself refused (revoked, or the account gone): signed out. A
                // network failure (status 0) keeps the session for the next try.
                if (e.status in 400..499) forget()
                throw e
            }
        }
    }

    private fun keep(answer: JsonObject): AuthSession {
        val expiresAt = answer.get("expires_at")?.asLong
            ?: (nowSeconds() + (answer.get("expires_in")?.asLong ?: 3600L))
        val session = AuthSession(
            accessToken = answer.get("access_token").asString,
            refreshToken = answer.get("refresh_token").asString,
            expiresAt = expiresAt,
            userId = answer.getAsJsonObject("user").get("id").asString
        )
        preferences.edit().putString(KEY_SESSION, gson.toJson(session)).apply()
        _session.value = session
        return session
    }

    private fun forget() {
        preferences.edit().remove(KEY_SESSION).apply()
        _session.value = null
    }

    private fun readSession(): AuthSession? = preferences.getString(KEY_SESSION, null)?.let {
        runCatching { gson.fromJson(it, AuthSession::class.java) }.getOrNull()
    }

    // endregion

    // region Database

    /** Calls the database function [name] with [args], as the signed-in user (or anonymously). */
    suspend fun rpc(name: String, args: Map<String, Any?> = emptyMap(), signedIn: Boolean = true): JsonElement =
        call(restRequest("rpc/$name").post(gson.toJsonTree(args).toBody()), authorized = signedIn)

    /** Writes [row] into [table], or over the row with the same [onConflict] columns. */
    suspend fun upsert(table: String, row: Map<String, Any?>, onConflict: String) {
        call(
            restRequest("$table?on_conflict=$onConflict")
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(gson.toJsonTree(row).toBody())
        )
    }

    /**
     * Changes [row]'s columns in the rows of [table] that [filter] (PostgREST's, `id=eq.…`) picks;
     * a null clears its column.
     */
    suspend fun update(table: String, filter: String, row: Map<String, Any?>) {
        call(restRequest("$table?$filter").header("Prefer", "return=minimal").patch(changes.toJson(row).toRequestBody(JSON)))
    }

    suspend fun delete(table: String, filter: String) {
        call(restRequest("$table?$filter").header("Prefer", "return=minimal").delete())
    }

    suspend fun select(table: String, query: String): JsonElement = call(restRequest("$table?$query").get())

    // endregion

    // region Storage and functions

    /** Puts [bytes] at [path] in [bucket], over what was there. */
    suspend fun upload(bucket: String, path: String, bytes: ByteArray, contentType: String) {
        call(
            Request.Builder()
                .url("${SupabaseConfig.url}/storage/v1/object/$bucket/$path")
                .header("x-upsert", "true")
                .header("Cache-Control", "max-age=31536000")
                .post(bytes.toRequestBody(contentType.toMediaType()))
        )
    }

    fun publicUrl(bucket: String, path: String) = "${SupabaseConfig.url}/storage/v1/object/public/$bucket/$path"

    /** Runs the Edge Function [name] with [body], without a session. */
    suspend fun invoke(name: String, body: Map<String, Any?>): JsonElement = call(
        Request.Builder().url("${SupabaseConfig.url}/functions/v1/$name").post(gson.toJsonTree(body).toBody()),
        authorized = false
    )

    // endregion

    private fun authRequest(path: String) = Request.Builder().url("${SupabaseConfig.url}/auth/v1/$path")

    private fun restRequest(path: String) = Request.Builder()
        .url("${SupabaseConfig.url}/rest/v1/$path")
        .header("Accept", "application/json")

    /**
     * Sends [builder] with the project's key, and as the signed-in user when [authorized]. A
     * publishable key isn't a token: without a session only the key goes, and the database
     * takes the call as anonymous.
     */
    private suspend fun call(builder: Request.Builder, authorized: Boolean = true): JsonElement {
        if (!SupabaseConfig.configured) throw SupabaseException(0, "not_configured", "Supabase is not set up")
        builder.header("apikey", SupabaseConfig.key)
        if (authorized) builder.header("Authorization", "Bearer ${accessToken()}")
        return withContext(Dispatchers.IO) {
            val response = try {
                http.newCall(builder.build()).execute()
            } catch (e: IOException) {
                throw SupabaseException(0, "network", e.message ?: "No connection")
            }
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw failure(it.code, text)
                if (text.isBlank()) JsonObject() else runCatching { JsonParser.parseString(text) }.getOrElse { JsonObject() }
            }
        }
    }

    /** Auth, PostgREST and Storage each word an error their own way; the reason and the words. */
    private fun failure(status: Int, text: String): SupabaseException {
        val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
        fun field(name: String) = json?.get(name)?.takeIf { it.isJsonPrimitive }?.asString
        val code = field("error_code") ?: field("code") ?: field("error")
        val message = field("msg") ?: field("message") ?: field("error_description") ?: field("error") ?: "HTTP $status"
        Log.w("Supabase", "HTTP $status: $code $message")
        return SupabaseException(status, code, message)
    }

    private fun JsonElement.toBody(): RequestBody = gson.toJson(this).toRequestBody(JSON)

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    companion object {
        private const val KEY_SESSION = "session"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_JSON = JsonObject()

        @Volatile
        private var instance: Supabase? = null

        fun get(context: Context): Supabase = instance ?: synchronized(this) {
            instance ?: Supabase(context.applicationContext).also { instance = it }
        }
    }
}
