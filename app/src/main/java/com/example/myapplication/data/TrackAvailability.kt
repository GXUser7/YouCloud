package com.example.myapplication.data

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.io.FileNotFoundException
import java.lang.reflect.ParameterizedType
import java.lang.reflect.WildcardType

/**
 * A track its service won't play at all: withdrawn or not licensed here on Yandex, blocked,
 * a preview only or locked behind DRM on SoundCloud. Not the network's fault, so trying again
 * won't help — the player goes on to the next track instead of stopping on it. A
 * [FileNotFoundException], so the player doesn't load it again a few times first, seconds apart.
 */
class TrackUnavailableException(message: String) : FileNotFoundException(message)

/** Whether [error] (or what caused it) is a track its service won't play. */
fun isTrackUnavailable(error: Throwable?): Boolean {
    var cause = error
    var depth = 0
    while (cause != null && depth++ < 12) {
        if (cause is TrackUnavailableException) return true
        cause = cause.cause
    }
    return false
}

/**
 * Leaves out of every Yandex answer the tracks Yandex itself marks as not playable
 * (`"available": false`): in search, albums, playlists, an artist's tracks, likes and the waves
 * they never show up, so nothing is listed that would only fail to play. A single track that
 * isn't available reads as none.
 */
object YandexAvailability : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType == YandexTrack::class.java) {
            val delegate = gson.getDelegateAdapter(this, type)
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T) = delegate.write(out, value)
                override fun read(reader: JsonReader): T? =
                    delegate.read(reader)?.takeUnless { (it as YandexTrack).available == false }
            }
        }
        if (Collection::class.java.isAssignableFrom(type.rawType) && elementOf(type) == YandexTrack::class.java) {
            val delegate = gson.getDelegateAdapter(this, type)
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

                // The tracks left out read as nulls; the list closes up over them.
                @Suppress("UNCHECKED_CAST")
                override fun read(reader: JsonReader): T? {
                    val list = delegate.read(reader) as Collection<Any?>? ?: return null
                    if (list.none { it == null }) return list as T
                    return ArrayList(list.filterNotNull()) as T
                }
            }
        }
        return null
    }

    private fun elementOf(type: TypeToken<*>): java.lang.reflect.Type? {
        val parameterized = type.type as? ParameterizedType ?: return null
        val argument = parameterized.actualTypeArguments.singleOrNull() ?: return null
        return (argument as? WildcardType)?.upperBounds?.firstOrNull() ?: argument
    }
}

/**
 * Whether SoundCloud would play the whole of [track] here: not blocked, not a preview for Go+,
 * and with a stream that isn't cut short or encrypted — a label's track often comes with nothing
 * but DRM streams, which no player outside SoundCloud's own can open.
 */
fun soundCloudPlaysWhole(track: SoundCloudTrack): Boolean {
    if (track.streamable != true) return false
    if (track.policy in SOUNDCLOUD_UNPLAYABLE_POLICIES) return false
    val transcodings = track.media?.transcodings ?: return false
    return transcodings.any(::isWholeStream)
}

/** A stream of the whole track, in the clear. */
fun isWholeStream(transcoding: SoundCloudTranscoding): Boolean {
    val protocol = transcoding.format?.protocol.orEmpty()
    return !transcoding.snipped && !protocol.contains("encrypted", ignoreCase = true)
}

private val SOUNDCLOUD_UNPLAYABLE_POLICIES = setOf("BLOCK", "SNIP", "SNIPPET", "PREVIEW")
