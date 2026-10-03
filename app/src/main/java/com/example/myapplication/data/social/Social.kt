package com.example.myapplication.data.social

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import com.example.myapplication.i18n.tr
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime

/** The signed-in account's own profile. */
data class Profile(
    val id: String,
    val nick: String,
    val name: String? = null,
    val color: String? = null,
    @SerializedName("avatar_v") val avatarV: Int = 0,
    @SerializedName("share_listening") val shareListening: Boolean = true
)

/** What someone is to the one looking. */
enum class Relation { SELF, FRIEND, OUTGOING, INCOMING, NONE }

/**
 * Someone else, as the friends list, search and a profile page see them; and, for a friend who
 * lets it be seen, what they play or played last.
 */
data class Person(
    val id: String,
    val nick: String,
    val name: String? = null,
    val color: String? = null,
    @SerializedName("avatar_v") val avatarV: Int = 0,
    val relation: String? = null,
    /** How many friends they have: on a profile page only. */
    val friends: Long? = null,
    val title: String? = null,
    val artist: String? = null,
    @SerializedName("cover_url") val coverUrl: String? = null,
    val service: String? = null,
    @SerializedName("track_url") val trackUrl: String? = null,
    val playing: Boolean? = null,
    @SerializedName("started_at") val startedAt: String? = null,
    @SerializedName("duration_ms") val durationMs: Long? = null,
    @SerializedName("updated_at") val updatedAt: String? = null
) {
    val shownName: String get() = name?.takeIf { it.isNotBlank() } ?: nick

    val relationKind: Relation get() = relationOf(relation)

    /** When the track was last told about, in epoch milliseconds. */
    val updatedAtMs: Long? get() = updatedAt?.let(::parseTime)

    /** Playing right now: said so, and said recently enough for the phone to be still there. */
    fun listeningNow(now: Long = System.currentTimeMillis()): Boolean {
        if (playing != true || title.isNullOrBlank()) return false
        val updated = updatedAtMs ?: return false
        return now - updated < NowPlayingPublisher.STALE_AFTER_MS
    }

    /** How far into the track they are, as far as can be told: only while it plays. */
    fun positionMs(now: Long = System.currentTimeMillis()): Long? {
        val started = startedAt?.let(::parseTime) ?: return null
        val position = now - started
        val duration = durationMs ?: return position.coerceAtLeast(0L)
        return position.coerceIn(0L, duration)
    }
}

/** A recovery code: 16 characters no one mistakes for others, shown in fours. */
internal object RecoveryCode {
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    fun generate(): String {
        val random = SecureRandom()
        return String(CharArray(16) { ALPHABET[random.nextInt(ALPHABET.length)] })
    }

    /** As typed: any case, with dashes or spaces. */
    fun normalize(text: String) = text.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun pretty(code: String) = code.chunked(4).joinToString("-")

    /** What the database keeps of it, as the recover function works it out too. */
    fun hash(userId: String, code: String): String =
        MessageDigest.getInstance("SHA-256").digest("$userId:$code".toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/**
 * Accounts and friends: who is signed in, their profile, their friends and what those play.
 * Shared by the screens and the playback service (which says what plays, see
 * [NowPlayingPublisher]); one for the process.
 */
class Social private constructor(private val context: Context) {
    private val supabase = Supabase.get(context)
    private val preferences = context.getSharedPreferences("social", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** For what outlives a screen: telling the server what plays, refreshing the friends list. */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val configured: Boolean get() = SupabaseConfig.configured

    val session: StateFlow<AuthSession?> get() = supabase.session

    private val _me = MutableStateFlow(readMe())
    /** The signed-in profile, as last fetched: kept on the phone, so it shows offline too. */
    val me: StateFlow<Profile?> = _me.asStateFlow()

    private val _friends = MutableStateFlow<List<Person>?>(null)
    /** Friends and requests either way; null until first fetched. */
    val friends: StateFlow<List<Person>?> = _friends.asStateFlow()

    init {
        scope.launch {
            supabase.session.collect { session ->
                if (session == null) {
                    keepMe(null)
                    _friends.value = null
                }
            }
        }
    }

    // region Account

    suspend fun nickAvailable(nick: String): Boolean =
        supabase.rpc("nick_available", mapOf("candidate" to nick), signedIn = false).asBoolean

    /** Makes the account and signs in; answers the recovery code, shown once. */
    suspend fun signUp(nick: String, password: String, name: String, color: String): String {
        supabase.signUp(
            SupabaseConfig.emailOf(nick),
            password,
            mapOf("nick" to nick.lowercase(), "name" to name.trim(), "color" to color)
        )
        val code = newRecoveryCode()
        refreshMe()
        return code
    }

    suspend fun signIn(nick: String, password: String) {
        supabase.signIn(SupabaseConfig.emailOf(nick.trim().removePrefix("@")), password)
        refreshMe()
    }

    /** Signs out; friends stop seeing a track that is no longer told about. */
    suspend fun signOut() {
        val id = supabase.session.value?.userId
        if (id != null) runCatching { supabase.delete("now_playing", "user_id=eq.$id") }
        supabase.signOut()
    }

    /** A new password with the recovery code, then signed in with it. */
    suspend fun recover(nick: String, code: String, password: String) {
        val cleanNick = nick.trim().removePrefix("@").lowercase()
        supabase.invoke("recover", mapOf("nick" to cleanNick, "code" to RecoveryCode.normalize(code), "password" to password))
        signIn(cleanNick, password)
    }

    /** A new recovery code in place of the old one; answered once, never kept on the phone. */
    suspend fun newRecoveryCode(): String {
        val id = supabase.session.value?.userId ?: throw SupabaseException(401, "not_signed_in", "Not signed in")
        val code = RecoveryCode.generate()
        supabase.rpc("set_recovery_code", mapOf("hash" to RecoveryCode.hash(id, code)))
        return code
    }

    suspend fun changePassword(password: String) = supabase.updatePassword(password)

    suspend fun refreshMe(): Profile? {
        val id = supabase.session.value?.userId ?: return null
        val rows = supabase.select("profiles", "id=eq.$id&select=id,nick,name,color,avatar_v,share_listening")
        val profile = gson.fromJson<List<Profile>>(rows, object : TypeToken<List<Profile>>() {}.type).firstOrNull()
        keepMe(profile)
        return profile
    }

    suspend fun setShareListening(on: Boolean) {
        val me = _me.value ?: return
        supabase.update("profiles", "id=eq.${me.id}", mapOf("share_listening" to on))
        keepMe(me.copy(shareListening = on))
        if (!on) runCatching { supabase.delete("now_playing", "user_id=eq.${me.id}") }
    }

    suspend fun setName(name: String) {
        val me = _me.value ?: return
        val clean = name.trim().take(40)
        supabase.update("profiles", "id=eq.${me.id}", mapOf("name" to clean))
        keepMe(me.copy(name = clean))
    }

    suspend fun setColor(color: String) {
        val me = _me.value ?: return
        supabase.update("profiles", "id=eq.${me.id}", mapOf("color" to color))
        keepMe(me.copy(color = color))
    }

    /** The picture at [uri], cut square, made small and put up as the avatar. */
    suspend fun setAvatar(uri: Uri) {
        val me = _me.value ?: return
        val bytes = withContext(Dispatchers.Default) { avatarWebp(uri) }
        supabase.upload(AVATARS, "${me.id}/avatar.webp", bytes, "image/webp")
        val version = me.avatarV + 1
        supabase.update("profiles", "id=eq.${me.id}", mapOf("avatar_v" to version))
        keepMe(me.copy(avatarV = version))
    }

    suspend fun removeAvatar() {
        val me = _me.value ?: return
        supabase.update("profiles", "id=eq.${me.id}", mapOf("avatar_v" to 0))
        keepMe(me.copy(avatarV = 0))
    }

    /** The avatar's address, or none for someone who has only a colour. */
    fun avatarUrl(id: String, version: Int): String? =
        if (version > 0 && configured) "${supabase.publicUrl(AVATARS, "$id/avatar.webp")}?v=$version" else null

    private fun keepMe(profile: Profile?) {
        _me.value = profile
        preferences.edit().apply {
            if (profile == null) remove(KEY_ME) else putString(KEY_ME, gson.toJson(profile))
        }.apply()
    }

    private fun readMe(): Profile? = preferences.getString(KEY_ME, null)?.let {
        runCatching { gson.fromJson(it, Profile::class.java) }.getOrNull()
    }

    private fun avatarWebp(uri: Uri): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Decoded already near the size it ends at: a phone photo is tens of megapixels.
            val shorter = minOf(info.size.width, info.size.height)
            decoder.setTargetSampleSize((shorter / (AVATAR_PX * 2)).coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val side = minOf(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(square, AVATAR_PX, AVATAR_PX, true)
        return ByteArrayOutputStream().use { out ->
            small.compress(Bitmap.CompressFormat.WEBP_LOSSY, 82, out)
            out.toByteArray()
        }
    }

    // endregion

    // region Friends

    suspend fun loadFriends(): List<Person> {
        val list = people(supabase.rpc("friends_overview"))
        _friends.value = list
        return list
    }

    /** Refreshes the list in the background, as after a change made elsewhere. */
    fun reloadFriends() {
        scope.launch { runCatching { loadFriends() }.onFailure { Log.w(TAG, "Friends not loaded", it) } }
    }

    suspend fun search(query: String): List<Person> =
        if (query.isBlank()) emptyList() else people(supabase.rpc("search_profiles", mapOf("q" to query)))

    suspend fun profile(id: String): Person? = people(supabase.rpc("profile_card", mapOf("target" to id))).firstOrNull()

    /** Asks to be friends (or accepts their request, if they asked first); what they are now. */
    suspend fun request(id: String): Relation = changed(supabase.rpc("request_friend", mapOf("target" to id)))

    suspend fun accept(id: String): Relation = changed(supabase.rpc("accept_friend", mapOf("target" to id)))

    /** Unfriends, declines their request or takes one's own back. */
    suspend fun remove(id: String): Relation = changed(supabase.rpc("remove_friend", mapOf("target" to id)))

    private fun changed(answer: JsonElement): Relation {
        reloadFriends()
        return relationOf(answer.takeIf { it.isJsonPrimitive }?.asString)
    }

    private fun people(json: JsonElement): List<Person> =
        if (json.isJsonArray) gson.fromJson(json, object : TypeToken<List<Person>>() {}.type) else emptyList()

    // endregion

    // region What plays

    /** Tells friends what plays now; nothing when signed out or not sharing. */
    internal fun publishNowPlaying(snapshot: NowPlayingPublisher.Snapshot) {
        val session = supabase.session.value ?: return
        if (_me.value?.shareListening == false) return
        scope.launch {
            runCatching {
                supabase.upsert(
                    "now_playing",
                    mapOf(
                        "user_id" to session.userId,
                        "title" to snapshot.title,
                        "artist" to snapshot.artist,
                        "cover_url" to snapshot.coverUrl,
                        "service" to snapshot.service,
                        "track_url" to snapshot.trackUrl,
                        "playing" to snapshot.playing,
                        "started_at" to snapshot.startedAtMs?.let { Instant.ofEpochMilli(it).toString() },
                        "duration_ms" to snapshot.durationMs,
                        "updated_at" to Instant.now().toString()
                    ),
                    onConflict = "user_id"
                )
            }.onFailure { Log.w(TAG, "Now playing not told", it) }
        }
    }

    // endregion

    companion object {
        private const val TAG = "Social"
        private const val KEY_ME = "me"
        private const val AVATARS = "avatars"
        private const val AVATAR_PX = 256

        @Volatile
        private var instance: Social? = null

        fun get(context: Context): Social = instance ?: synchronized(this) {
            instance ?: Social(context.applicationContext).also { instance = it }
        }
    }
}

private fun relationOf(text: String?): Relation = when (text) {
    "self" -> Relation.SELF
    "friend" -> Relation.FRIEND
    "outgoing" -> Relation.OUTGOING
    "incoming" -> Relation.INCOMING
    else -> Relation.NONE
}

internal fun parseTime(text: String): Long? = runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()

/** What went wrong, in words for the screen. */
fun Throwable.socialMessage(): String {
    val e = this as? SupabaseException ?: return message ?: tr("Что-то пошло не так")
    return when {
        e.code == "network" -> tr("Нет связи с сервером. Проверь интернет")
        e.code == "not_configured" -> tr("В этой сборке аккаунты не настроены")
        e.code == "user_already_exists" || e.code == "email_exists" -> tr("Этот ник уже занят")
        e.code == "invalid_credentials" || e.code == "invalid_grant" -> tr("Неверный ник или пароль")
        e.code == "weak_password" -> tr("Пароль слишком простой")
        e.code == "wrong_code" -> tr("Неверный ник или код")
        e.code == "email_confirmation_required" -> tr("На сервере включено подтверждение почты: его нужно выключить")
        e.code == "email_address_invalid" -> tr("Сервер не принял адрес входа")
        e.code == "over_request_rate_limit" || e.status == 429 -> tr("Слишком много попыток. Попробуй чуть позже")
        e.code == "not_signed_in" -> tr("Нужно войти в аккаунт")
        else -> e.message ?: tr("Что-то пошло не так")
    }
}
