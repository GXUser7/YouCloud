package com.example.myapplication.data.social

import android.content.Context
import android.util.Log
import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.FavoritesRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant

/**
 * «Любимое» the same on every device signed in to the YouCloud account — other phones, the site
 * (supabase/migrations/20261011120000_favorites.sql): a track liked here is there too, and one
 * liked there comes here and is downloaded, as a like here is. Files from the phone stay on it.
 *
 * What the listener changes here waits as [Pending] until the server has it, so a change made
 * offline goes when the connection is back; of two changes to a track the later one stays, on the
 * server as here. What changed elsewhere comes as [Changes] since the last look ([exchange]),
 * for the caller to apply: adding means downloading, which only the screen's side does.
 *
 * The first look for an account puts the two lists together: what is only here goes up, what is
 * only there comes down, nothing is taken out.
 */
internal class FavoritesSync private constructor(context: Context) : FavoritesRepository.Listener {
    private val supabase = Supabase.get(context)
    private val preferences = context.getSharedPreferences("favorites_sync", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val lock = Any()

    /** A change made here and not yet on the server; [at] is when, by this phone's clock. */
    private data class Pending(val liked: Boolean, val at: Long)

    /** What changed on other devices, to be done here. */
    data class Changes(val added: List<FavoriteTrack>, val removed: List<Long>) {
        val isEmpty: Boolean get() = added.isEmpty() && removed.isEmpty()
    }

    private data class Look(val until: String? = null, val items: List<FavoriteRow>? = null)

    private val pending: MutableMap<String, Pending> = readPending()

    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))
    /** The listener's choice, in the profile; on by default. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    /**
     * Turned off, nothing goes either way and what waits is let go of; turned on again, the two
     * lists are put together anew, as on the first look.
     */
    fun setEnabled(on: Boolean) {
        synchronized(lock) {
            if (!on) pending.clear()
            preferences.edit().putBoolean(KEY_ENABLED, on).apply {
                if (!on) remove(KEY_OWNER).remove(KEY_UNTIL).remove(KEY_PENDING).remove(KEY_VERSION)
            }.apply()
        }
        _enabled.value = on
    }

    private val _wanted = MutableStateFlow(0L)
    /**
     * Counts the times there is something to sync — a change here, or one the server told of —
     * for the caller to sync a moment after.
     */
    val wanted: StateFlow<Long> = _wanted.asStateFlow()

    // Changes made on other devices, told as they are made (the account's own rows only).
    private val realtime = Realtime(supabase, topic = "realtime:favorites", tables = listOf("favorites"), ownColumn = "user_id") {
        _wanted.value = _wanted.value + 1
    }

    /** Keeps the server telling of changes until the handle is closed: while the app is in sight. */
    fun watchLive(): AutoCloseable =
        if (_enabled.value && SupabaseConfig.configured && supabase.session.value != null) realtime.watch() else AutoCloseable { }

    // region Changes made here

    override fun liked(tracks: List<FavoriteTrack>) {
        // Moved up together (a service's likes saved at once), each a millisecond after the one
        // below it, so the other devices list them in this order.
        val now = System.currentTimeMillis()
        record(tracks.mapIndexedNotNull { index, track -> FavoriteRows.keyOf(track)?.let { it.key to Pending(true, now - index) } })
    }

    override fun unliked(track: FavoriteTrack) {
        record(listOfNotNull(FavoriteRows.keyOf(track)?.let { it.key to Pending(false, System.currentTimeMillis()) }))
    }

    private fun record(changes: List<Pair<String, Pending>>) {
        if (changes.isEmpty() || !_enabled.value) return
        synchronized(lock) {
            changes.forEach { (key, change) -> pending[key] = change }
            savePending()
        }
        _wanted.value = _wanted.value + 1
    }

    // endregion

    /**
     * Sends what changed here and takes what changed elsewhere since the last look; [local] is
     * «Любимое» as it is here now. Null when signed out, or when the server can't be reached —
     * the changes wait for the next time.
     */
    suspend fun exchange(local: List<FavoriteTrack>): Changes? {
        if (!_enabled.value) return null
        val userId = supabase.session.value?.userId ?: return null
        return try {
            if (preferences.getString(KEY_OWNER, null) != userId) {
                firstLook(userId, local)
            } else {
                if (preferences.getInt(KEY_VERSION, 1) < VERSION) restoreOrder(local)
                nextLook(local)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SupabaseException) {
            Log.w(TAG, "Not synced: ${e.status} ${e.code} ${e.message}")
            null
        } catch (e: Exception) {
            // An answer not as expected: the changes wait, the app goes on.
            Log.w(TAG, "Not synced", e)
            null
        }
    }

    private suspend fun firstLook(userId: String, local: List<FavoriteTrack>): Changes? {
        // Changes made before are in [local] already, or were taken back; one made meanwhile waits.
        val before = synchronized(lock) { pending.toMap() }
        val look = look(null)
        val remote = look.items.orEmpty().filter { it.liked }
        val remoteKeys = remote.map { it.key }.toSet()
        val here = local.mapNotNull { track -> FavoriteRows.keyOf(track)?.let { it to track } }
        // Only here: up, in the order they are listed, the top one as liked last.
        val now = System.currentTimeMillis()
        val up = here.filter { (key, _) -> key.key !in remoteKeys }
            .mapIndexed { index, (key, track) -> FavoriteRows.rowOf(key, track, liked = true, at = now - index) }
        send(up)
        synchronized(lock) {
            // Turned off meanwhile: nothing kept.
            if (!_enabled.value) return null
            before.forEach { (key, change) -> if (pending[key] == change) pending.remove(key) }
            savePending()
            preferences.edit().putString(KEY_OWNER, userId).putString(KEY_UNTIL, look.until).putInt(KEY_VERSION, VERSION).apply()
        }
        val hereKeys = here.map { it.first.key }.toSet()
        val added = remote.filter { it.key !in hereKeys }.mapNotNull(FavoriteRows::trackOf)
        Log.d(TAG, "First look: ${up.size} up, ${added.size} down")
        return Changes(added, emptyList())
    }

    /**
     * Once, for a first look made before the server took a clock running ahead into account: it
     * held every time past its own now to now, and a phone a second or two ahead sent its whole
     * list liked at one moment, which the site showed in no order. The tracks of this list go
     * again, a millisecond apart from that moment on, the top one the latest — over the same
     * tracks there, never over a later change.
     */
    private suspend fun restoreOrder(local: List<FavoriteTrack>) {
        val remote = look(null).items.orEmpty().associateBy { it.key }
        val mine = local.mapNotNull { track -> FavoriteRows.keyOf(track)?.takeIf { it.key in remote }?.let { it to track } }
        val since = mine.maxOfOrNull { (key, _) -> remote.getValue(key.key).changedAt?.let(::parseTime) ?: 0L }
        if (since != null) {
            send(mine.mapIndexed { index, (key, track) -> FavoriteRows.rowOf(key, track, liked = true, at = since + (mine.size - 1 - index)) })
            Log.d(TAG, "Order of ${mine.size} put right")
        }
        synchronized(lock) {
            if (_enabled.value) preferences.edit().putInt(KEY_VERSION, VERSION).apply()
        }
    }

    private suspend fun nextLook(local: List<FavoriteTrack>): Changes? {
        val waiting = synchronized(lock) { pending.toMap() }
        if (waiting.isNotEmpty()) {
            val byKey = local.mapNotNull { track -> FavoriteRows.keyOf(track)?.let { it.key to (it to track) } }.toMap()
            val rows = waiting.mapNotNull { (key, change) ->
                val (service, trackId) = key.split(":", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                if (change.liked) {
                    // Liked and gone again before it was sent: the unlike that took it is waiting too.
                    val (trackKey, track) = byKey[key] ?: return@mapNotNull null
                    FavoriteRows.rowOf(trackKey, track, liked = true, at = change.at)
                } else {
                    FavoriteRow(service = service, trackId = trackId, liked = false, changedAt = iso(change.at))
                }
            }
            send(rows)
            synchronized(lock) {
                // Gone unless changed again meanwhile.
                waiting.forEach { (key, change) -> if (pending[key] == change) pending.remove(key) }
                savePending()
            }
        }

        val since = preferences.getString(KEY_UNTIL, null)?.let(::parseTime)?.let { iso(it - OVERLAP_MS) }
        val look = look(since)
        val stillWaiting = synchronized(lock) { pending.toMap() }
        val hereByKey = local.mapNotNull { track -> FavoriteRows.keyOf(track)?.let { it.key to track } }.toMap()
        val added = mutableListOf<FavoriteTrack>()
        val removed = mutableListOf<Long>()
        for (row in look.items.orEmpty().sortedByDescending { it.changedAt?.let(::parseTime) ?: 0L }) {
            // A change here, later than the one there, still to go.
            val mine = stillWaiting[row.key]
            if (mine != null && mine.at >= (row.changedAt?.let(::parseTime) ?: 0L)) continue
            val here = hereByKey[row.key]
            if (row.liked && here == null) FavoriteRows.trackOf(row)?.let(added::add)
            if (!row.liked && here != null) removed += here.id
        }
        synchronized(lock) {
            if (!_enabled.value) return null
            look.until?.let { preferences.edit().putString(KEY_UNTIL, it).apply() }
        }
        if (added.isNotEmpty() || removed.isNotEmpty()) Log.d(TAG, "${added.size} liked and ${removed.size} taken out elsewhere")
        return Changes(added, removed)
    }

    private suspend fun look(since: String?): Look {
        val answer: JsonElement = supabase.rpc("favorites_since", if (since == null) emptyMap() else mapOf("since" to since))
        return if (answer.isJsonObject) gson.fromJson(answer, Look::class.java) else Look()
    }

    private suspend fun send(rows: List<FavoriteRow>) {
        rows.chunked(BATCH).forEach { batch -> supabase.rpc("save_favorites", mapOf("items" to batch)) }
    }

    private fun readPending(): MutableMap<String, Pending> = preferences.getString(KEY_PENDING, null)?.let {
        runCatching { gson.fromJson<Map<String, Pending>>(it, object : TypeToken<Map<String, Pending>>() {}.type) }.getOrNull()
    }.orEmpty().toMutableMap()

    private fun savePending() {
        preferences.edit().putString(KEY_PENDING, gson.toJson(pending)).apply()
    }

    private fun iso(ms: Long) = Instant.ofEpochMilli(ms).toString()

    companion object {
        private const val TAG = "FavoritesSync"
        private const val KEY_OWNER = "owner"
        private const val KEY_UNTIL = "until"
        private const val KEY_PENDING = "pending"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_VERSION = "version"
        /** 2: the first look's order put right (see [restoreOrder]). */
        private const val VERSION = 2
        /** What the database takes at once. */
        private const val BATCH = 500
        /** Asked again from a little before the last look: a change written as it was made may carry an earlier time. */
        private const val OVERLAP_MS = 60_000L

        @Volatile
        private var instance: FavoritesSync? = null

        fun get(context: Context): FavoritesSync = instance ?: synchronized(this) {
            instance ?: FavoritesSync(context.applicationContext).also { instance = it }
        }
    }
}
