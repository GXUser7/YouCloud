package com.example.myapplication.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What was listened to, newest first and [LIMIT] at most: each track once, moved to the front when
 * heard again. Kept as the track that played, so it plays again from here the same way — from
 * whichever service it came, and from the phone when there is a copy on it.
 *
 * Read and written off the main thread: two hundred tracks turned into text on every track change
 * would cost a frame each time.
 */
class ListeningHistory(context: Context, scope: CoroutineScope) {
    private val preferences = context.getSharedPreferences("listening_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<SoundCloudTrack>>() {}.type

    private val _tracks = MutableStateFlow<List<SoundCloudTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val saved = read()
            // Anything heard while it was read stays in front of it.
            _tracks.update { heard -> (heard + saved).distinctBy { it.id }.take(LIMIT) }
            // Written as it changes; changes that come faster than a write are written once.
            var written = saved
            _tracks.collect { now ->
                if (now != written) {
                    preferences.edit().putString(KEY, gson.toJson(now)).apply()
                    written = now
                }
            }
        }
    }

    fun record(track: SoundCloudTrack) {
        _tracks.update { list ->
            val entry = list.firstOrNull { it.id == track.id }?.let(track::filledFrom) ?: track
            // Without a urn there is nothing to play it again by.
            if (entry.urn.isNullOrBlank()) return@update list
            (listOf(entry.kept()) + list.filterNot { it.id == track.id }).take(LIMIT)
        }
    }

    fun clear() {
        _tracks.value = emptyList()
    }

    private fun read(): List<SoundCloudTrack> {
        val json = preferences.getString(KEY, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<SoundCloudTrack>?>(json, listType) }
            .getOrNull()
            .orEmpty()
            // Gson may hand back nulls for entries it could not read.
            .filterNotNull()
    }

    companion object {
        const val LIMIT = 200
        private const val KEY = "tracks"
    }
}

/**
 * This track, with what it lacks taken from [earlier]: a queue restored after a restart plays stubs
 * that know only a title and a cover, and one of those heard again must not wipe out the track's
 * artists, length and link.
 */
private fun SoundCloudTrack.filledFrom(earlier: SoundCloudTrack): SoundCloudTrack = copy(
    urn = urn ?: earlier.urn,
    title = title ?: earlier.title,
    artworkUrl = artworkUrl ?: earlier.artworkUrl,
    permalinkUrl = permalinkUrl ?: earlier.permalinkUrl,
    user = if (user?.id == null && user?.permalinkUrl == null) earlier.user ?: user else user,
    artists = artists ?: earlier.artists,
    duration = duration.takeIf { it > 0L } ?: earlier.duration
)

/**
 * What is worth keeping of a track to show it and play it again. SoundCloud's stream addresses and
 * their authorisation go stale and are fetched again by id anyway (as for a playlist's tracks); an
 * artist's description can run to paragraphs.
 */
private fun SoundCloudTrack.kept(): SoundCloudTrack = copy(
    trackAuthorization = null,
    media = null,
    user = user?.kept(),
    artists = artists?.map { it.kept() }
)

private fun SoundCloudUser.kept(): SoundCloudUser = copy(description = null)
