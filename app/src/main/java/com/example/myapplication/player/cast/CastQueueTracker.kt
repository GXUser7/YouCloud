package com.example.myapplication.player.cast

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

private val HLS_URI_ATTR = Regex("""URI="([^"]+)"""")

/**
 * Fixes Chromecast reporting the length of a single 10-second HLS segment (or 0 / unset) as the
 * whole track's duration, while still allowing legitimately shorter resolved streams (e.g. a
 * 1:20 stream replacing 2:06 Spotify metadata).
 */
internal fun resolveEffectiveCastDurationMs(
    castDurationMs: Long,
    castPositionMs: Long,
    trackDurationMs: Long?
): Long {
    val meta = trackDurationMs?.takeIf { it > 0L }
    if (castDurationMs == C.TIME_UNSET || castDurationMs <= 0L) {
        return meta ?: C.TIME_UNSET
    }
    if (meta != null) {
        if (castPositionMs > castDurationMs + 2_000L) return meta
        if (meta >= 30_000L && castDurationMs <= 25_000L) return meta
    }
    return castDurationMs
}

internal fun isHlsCastUri(uri: Uri): Boolean {
    val raw = uri.toString()
    val path = uri.path.orEmpty()
    return uri.scheme == "ytlive" ||
        path.endsWith(".m3u8", ignoreCase = true) ||
        raw.contains(".m3u8", ignoreCase = true) ||
        raw.contains("m3u8", ignoreCase = true)
}

internal fun rewriteHlsPlaylist(
    playlistUri: String,
    playlistText: String,
    registerSegment: (resolvedUri: String) -> String
): String {
    val base = java.net.URI(playlistUri)
    fun resolveUri(ref: String): String = runCatching { base.resolve(ref).toString() }.getOrDefault(ref)

    return playlistText.lines().joinToString("\n") { line ->
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("#EXT-X-MAP:") || trimmed.startsWith("#EXT-X-KEY:") -> {
                HLS_URI_ATTR.replace(trimmed) { match ->
                    val resolved = resolveUri(match.groupValues[1])
                    """URI="${registerSegment(resolved)}""""
                }
            }
            trimmed.startsWith("#") -> trimmed
            else -> registerSegment(resolveUri(trimmed))
        }
    }
}

/**
 * Mirrors every queue mutation and Cast playback transition in pure JVM state so unit tests
 * can verify queue synchronization without Android stubs.
 */
internal class CastQueueState<T>(
    private val idOf: (T) -> String,
    private val hasLocation: (T) -> Boolean = { true }
) {
    data class Snapshot<T>(
        val items: List<T>,
        val index: Int,
        val positionMs: Long,
        val playing: Boolean
    )

    private val lock = Any()
    private val items = mutableListOf<T>()
    private val known = HashMap<String, T>()
    private var currentIndex = 0
    private var currentPositionMs = 0L
    private var playWhenReady = false

    fun remember(item: T) {
        val id = idOf(item)
        if (id.isNotBlank() && hasLocation(item)) {
            synchronized(lock) { known[id] = item }
        }
    }

    fun known(mediaId: String?): T? = synchronized(lock) {
        mediaId?.takeIf { it.isNotBlank() }?.let(known::get)
    }

    private fun resolveItemLocked(item: T): T {
        val id = idOf(item)
        if (hasLocation(item)) {
            if (id.isNotBlank()) known[id] = item
            return item
        }
        return (if (id.isNotBlank()) known[id] else null) ?: item
    }

    fun seedFromLocal(localItems: List<T>, index: Int, positionMs: Long, playing: Boolean) {
        synchronized(lock) {
            known.clear()
            items.clear()
            for (item in localItems) {
                if (!hasLocation(item)) continue
                val id = idOf(item)
                if (id.isNotBlank()) known[id] = item
                items += item
            }
            currentIndex = if (items.isEmpty()) 0 else index.coerceIn(0, items.lastIndex)
            currentPositionMs = positionMs.coerceAtLeast(0L)
            playWhenReady = playing
        }
    }

    fun onSetMediaItems(newItems: List<T>, startIndex: Int, startPositionMs: Long) {
        synchronized(lock) {
            val resolved = newItems.map(::resolveItemLocked)
            items.clear()
            items.addAll(resolved)
            currentIndex = when {
                items.isEmpty() -> 0
                startIndex == C.INDEX_UNSET -> 0
                else -> startIndex.coerceIn(0, items.lastIndex)
            }
            currentPositionMs = if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs.coerceAtLeast(0L)
        }
    }

    fun onAddMediaItems(index: Int, added: List<T>, castCountBeforeAdd: Int? = null) {
        if (added.isEmpty()) return
        synchronized(lock) {
            val resolved = added.map(::resolveItemLocked)
            if (castCountBeforeAdd != null &&
                index >= castCountBeforeAdd &&
                items.size >= castCountBeforeAdd + resolved.size
            ) {
                val tailMatches = (0 until resolved.size).all { i ->
                    val existingId = idOf(items[castCountBeforeAdd + i])
                    val addedId = idOf(resolved[i])
                    existingId.isNotBlank() && existingId == addedId
                }
                if (tailMatches) return
            }
            val hadItems = items.isNotEmpty()
            val safeIdx = index.coerceIn(0, items.size)
            items.addAll(safeIdx, resolved)
            if (hadItems && safeIdx <= currentIndex) {
                currentIndex += resolved.size
            }
        }
    }

    fun onRemoveMediaItems(fromIndex: Int, toIndex: Int) {
        synchronized(lock) {
            val from = fromIndex.coerceIn(0, items.size)
            val to = toIndex.coerceIn(from, items.size)
            if (from >= to) return
            val removedCount = to - from
            items.subList(from, to).clear()
            when {
                items.isEmpty() -> {
                    currentIndex = 0
                    currentPositionMs = 0L
                }
                to <= currentIndex -> currentIndex -= removedCount
                from <= currentIndex -> {
                    currentIndex = from.coerceIn(0, items.lastIndex)
                    currentPositionMs = 0L
                }
            }
        }
    }

    fun onMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        synchronized(lock) {
            if (items.isEmpty()) return
            val from = fromIndex.coerceIn(0, items.size)
            val to = toIndex.coerceIn(from, items.size)
            if (from >= to) return
            val movedCount = to - from
            val insertAt = newIndex.coerceIn(0, items.size - movedCount)
            if (insertAt == from) return

            val currentWas = currentIndex
            val moved = items.subList(from, to).toList()
            items.subList(from, to).clear()
            items.addAll(insertAt, moved)

            currentIndex = if (currentWas in from until to) {
                insertAt + (currentWas - from)
            } else {
                val afterRemove = if (currentWas >= to) currentWas - movedCount else currentWas
                if (afterRemove >= insertAt) afterRemove + movedCount else afterRemove
            }.coerceIn(0, items.lastIndex)
        }
    }

    fun onReplaceMediaItems(fromIndex: Int, toIndex: Int, newItems: List<T>) {
        synchronized(lock) {
            val from = fromIndex.coerceIn(0, items.size)
            val to = toIndex.coerceIn(from, items.size)
            val resolved = newItems.map(::resolveItemLocked)
            val replacedCount = to - from
            items.subList(from, to).clear()
            items.addAll(from, resolved)
            when {
                items.isEmpty() -> {
                    currentIndex = 0
                    currentPositionMs = 0L
                }
                to <= currentIndex -> currentIndex = (currentIndex + resolved.size - replacedCount).coerceIn(0, items.lastIndex)
                currentIndex in from until to -> currentIndex = currentIndex.coerceIn(0, items.lastIndex)
            }
        }
    }

    fun onClearMediaItems() {
        synchronized(lock) {
            items.clear()
            currentIndex = 0
            currentPositionMs = 0L
        }
    }

    fun onSeek(mediaItemIndex: Int?, positionMs: Long) {
        synchronized(lock) {
            if (mediaItemIndex != null && mediaItemIndex in items.indices) {
                currentIndex = mediaItemIndex
            }
            if (positionMs != C.TIME_UNSET) {
                currentPositionMs = positionMs.coerceAtLeast(0L)
            }
        }
    }

    fun onPlayWhenReadyChanged(playing: Boolean) {
        synchronized(lock) { playWhenReady = playing }
    }

    fun syncFromCast(
        castMediaId: String?,
        castIndex: Int,
        castCount: Int,
        castPositionMs: Long,
        castPlaying: Boolean,
        castPlaybackState: Int
    ) {
        synchronized(lock) {
            if (castCount <= 0 || castPlaybackState == Player.STATE_IDLE || items.isEmpty()) return
            if (!castMediaId.isNullOrBlank()) {
                val matched = if (castIndex in items.indices && idOf(items[castIndex]) == castMediaId) {
                    castIndex
                } else {
                    items.indexOfFirst { idOf(it) == castMediaId }
                }
                if (matched < 0) return
                currentIndex = matched
            } else if (castCount == items.size && castIndex in items.indices) {
                currentIndex = castIndex
            } else {
                return
            }
            if (castPositionMs != C.TIME_UNSET) {
                currentPositionMs = castPositionMs.coerceAtLeast(0L)
            }
            playWhenReady = castPlaying
        }
    }

    fun size(): Int = synchronized(lock) { items.size }

    fun itemAt(index: Int): T? = synchronized(lock) { items.getOrNull(index) }

    fun currentTrackedItem(): T? = synchronized(lock) { items.getOrNull(currentIndex) }

    fun snapshot(): Snapshot<T> = synchronized(lock) {
        Snapshot(items.toList(), currentIndex, currentPositionMs, playWhenReady)
    }
}

@UnstableApi
internal class CastQueueTracker {
    private val state = CastQueueState<MediaItem>(
        idOf = { it.mediaId },
        hasLocation = { it.localConfiguration != null }
    )

    fun remember(item: MediaItem) = state.remember(item)
    fun known(mediaId: String?): MediaItem? = state.known(mediaId)
    fun seedFromLocal(localItems: List<MediaItem>, index: Int, positionMs: Long, playing: Boolean) =
        state.seedFromLocal(localItems, index, positionMs, playing)
    fun onSetMediaItems(newItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) =
        state.onSetMediaItems(newItems, startIndex, startPositionMs)
    fun onAddMediaItems(index: Int, added: List<MediaItem>, castCountBeforeAdd: Int? = null) =
        state.onAddMediaItems(index, added, castCountBeforeAdd)
    fun onRemoveMediaItems(fromIndex: Int, toIndex: Int) =
        state.onRemoveMediaItems(fromIndex, toIndex)
    fun onMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) =
        state.onMoveMediaItems(fromIndex, toIndex, newIndex)
    fun onReplaceMediaItems(fromIndex: Int, toIndex: Int, newItems: List<MediaItem>) =
        state.onReplaceMediaItems(fromIndex, toIndex, newItems)
    fun onClearMediaItems() = state.onClearMediaItems()
    fun onSeek(mediaItemIndex: Int?, positionMs: Long) = state.onSeek(mediaItemIndex, positionMs)
    fun onPlayWhenReadyChanged(playing: Boolean) = state.onPlayWhenReadyChanged(playing)
    fun syncFromCast(
        castMediaId: String?,
        castIndex: Int,
        castCount: Int,
        castPositionMs: Long,
        castPlaying: Boolean,
        castPlaybackState: Int
    ) = state.syncFromCast(castMediaId, castIndex, castCount, castPositionMs, castPlaying, castPlaybackState)
    fun size(): Int = state.size()
    fun itemAt(index: Int): MediaItem? = state.itemAt(index)
    fun currentTrackedItem(): MediaItem? = state.currentTrackedItem()
    fun snapshot(): CastQueueState.Snapshot<MediaItem> = state.snapshot()
}
