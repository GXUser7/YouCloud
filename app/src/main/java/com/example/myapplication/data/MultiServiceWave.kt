package com.example.myapplication.data

import java.text.Normalizer
import java.util.EnumMap
import java.util.Locale
import kotlin.random.Random

/** Which connected service contributed a track to the multi-service wave. */
enum class WaveSource {
    YANDEX,
    SOUNDCLOUD,
    YOUTUBE,
    SPOTIFY
}

/** True for real SoundCloud tracks (excluding local device files and other services). */
fun isSoundCloudTrack(track: SoundCloudTrack): Boolean {
    if (track.id <= 0L) return false
    if (track.spotifyId != null || track.youTubeVideoId != null || track.liveVideoId != null) return false
    val urn = track.urn
    return urn == null || urn.startsWith("soundcloud:")
}

fun waveSourceOf(track: SoundCloudTrack): WaveSource = when {
    track.spotifyId != null -> WaveSource.SPOTIFY
    track.youTubeVideoId != null || track.liveVideoId != null -> WaveSource.YOUTUBE
    track.urn?.startsWith("yandex:") == true -> WaveSource.YANDEX
    else -> WaveSource.SOUNDCLOUD
}

private val waveBracketNoise = Regex(
    """\s*[\[(](?:(?:feat\.?|ft\.?|featuring|with|prod\.?\s+by)\s+[^)\]]+|(?:official\s+)?(?:audio|video|music\s+video|lyric\s+video|lyrics?|visualizer|clip)|audio\s+only|original\s+audio|full\s+song|hd|uhd|4k|hq|explicit(?:\s+version|\s+edit)?|clean(?:\s+version|\s+edit)?|video\s+edit|bonus\s+track|(?:single|album|extended|short|radio)\s+version|radio\s+edit|original\s+mix|deluxe(?:\s+edition)?|remaster(?:ed)?(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?)[)\]]""",
    RegexOption.IGNORE_CASE
)
private val waveDashNoise = Regex(
    """\s+[-–—]\s+(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|(?:single|album|extended|short|radio)\s+version|(?:radio|video|clean|explicit)\s+edit|explicit|clean|bonus\s+track|original\s+mix|deluxe(?:\s+edition)?)$""",
    RegexOption.IGNORE_CASE
)
private val waveTrailingFeat = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+[^-–—]+$""", RegexOption.IGNORE_CASE)
private val waveTitleSplit = Regex("""\s*[-–—|]\s+|\s+[-–—|:]\s*""")
private val waveArtistSep = Regex("""\s*(?:,|&|\+|/|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\bx\b|\bи\b)\s*""", RegexOption.IGNORE_CASE)
private val waveMarks = Regex("\\p{M}+")
private val waveNonAlnum = Regex("[^\\p{L}\\p{N}]+")
private val waveApostrophe = Regex("""['’`ʼ]""")

private fun waveNormalize(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        .replace(waveMarks, "")
        .replace(waveApostrophe, "")
        .replace(waveNonAlnum, " ")
        .trim()

internal fun waveLeadArtistKey(track: SoundCloudTrack): String {
    val raw = track.artists?.firstOrNull()?.username
        ?: track.user?.username
        ?: return ""
    val first = raw.split(waveArtistSep).firstOrNull().orEmpty()
    return waveNormalize(first)
        .removeSuffix(" topic")
        .removeSuffix(" vevo")
        .removeSuffix(" official")
        .trim()
}

/**
 * Cross-service track fingerprint (`leadArtist::cleanTitle`) so the same song coming from Yandex,
 * YouTube Music, Spotify or SoundCloud isn't queued twice in a multi-service wave.
 */
fun waveTrackFingerprint(track: SoundCloudTrack): String {
    val artist = waveLeadArtistKey(track)
    var rawTitle = track.title.orEmpty().trim()
    val parts = waveTitleSplit.split(rawTitle, limit = 2)
    if (parts.size == 2 && artist.isNotEmpty() && waveNormalize(parts[0]).contains(artist)) {
        rawTitle = parts[1].trim()
    }
    repeat(3) {
        val cleaned = rawTitle
            .replace(waveBracketNoise, "")
            .replace(waveDashNoise, "")
            .replace(waveTrailingFeat, "")
            .trim()
        if (cleaned == rawTitle) return@repeat
        rawTitle = cleaned
    }
    val title = waveNormalize(rawTitle)
    return if (artist.isNotEmpty() && title.isNotEmpty()) "$artist::$title" else "id:${track.id}"
}

/**
 * Stateful mixer for the multi-service "Monster Wave": deduplicates across services by fingerprint,
 * rotates seeds with preference for tracks the listener finished or liked, filters disliked artists
 * across all services, and round-robin interleaves connected services so the stream feels unified.
 */
internal class MultiServiceWaveMixer(
    private val random: Random = Random.Default
) {
    private val seenIds = LinkedHashSet<Long>()
    private val seenFingerprints = LinkedHashSet<String>()
    private val dislikedArtists = HashSet<String>()
    private val preferredSeeds = EnumMap<WaveSource, ArrayDeque<SoundCloudTrack>>(WaveSource::class.java)
    private val usedSeedIds = EnumMap<WaveSource, HashSet<Long>>(WaveSource::class.java)

    fun markQueued(tracks: Collection<SoundCloudTrack>) {
        tracks.forEach { track ->
            seenIds.add(track.id)
            seenFingerprints.add(waveTrackFingerprint(track))
        }
    }

    /** A track finished or liked in the wave becomes a priority seed for its service's next fetch. */
    fun recordPositive(track: SoundCloudTrack) {
        if (track.urn?.startsWith("local:") == true) return
        val source = waveSourceOf(track)
        val queue = preferredSeeds.getOrPut(source) { ArrayDeque() }
        queue.removeAll { it.id == track.id }
        queue.addFirst(track)
        while (queue.size > MAX_PREFERRED_SEEDS) queue.removeLast()
    }

    /** Suppresses the disliked track and its lead artist across every service for this session. */
    fun recordDislike(track: SoundCloudTrack) {
        seenIds.add(track.id)
        seenFingerprints.add(waveTrackFingerprint(track))
        val artist = waveLeadArtistKey(track)
        if (artist.length >= 2) dislikedArtists.add(artist)
        preferredSeeds.values.forEach { queue ->
            queue.removeAll { it.id == track.id || (artist.length >= 2 && waveLeadArtistKey(it) == artist) }
        }
    }

    /** Picks a fresh seed track for [source], preferring recently enjoyed tracks before [fallbacks]. */
    fun pickSeed(source: WaveSource, fallbacks: List<SoundCloudTrack>): SoundCloudTrack? {
        val used = usedSeedIds.getOrPut(source) { HashSet() }
        val preferred = preferredSeeds[source].orEmpty().filter { it.id !in used && !isDisliked(it) }
        if (preferred.isNotEmpty()) {
            val chosen = preferred.first()
            used.add(chosen.id)
            return chosen
        }
        val freshFallbacks = fallbacks.filter { it.id !in used && !isDisliked(it) }
        if (freshFallbacks.isNotEmpty()) {
            val chosen = freshFallbacks[random.nextInt(freshFallbacks.size)]
            used.add(chosen.id)
            return chosen
        }
        val pool = (preferredSeeds[source].orEmpty() + fallbacks).filterNot(::isDisliked).distinctBy { it.id }
        if (pool.isEmpty()) return null
        used.clear()
        val chosen = pool[random.nextInt(pool.size)]
        used.add(chosen.id)
        return chosen
    }

    /**
     * Merges candidate lists from connected services into a single round-robin interleaved batch
     * up to [targetCount] tracks, dropping cross-service duplicates and disliked artists.
     */
    fun mix(
        byService: Map<WaveSource, List<SoundCloudTrack>>,
        targetCount: Int,
        excludeIds: Set<Long> = emptySet()
    ): List<SoundCloudTrack> {
        fun collect(strictExclude: Boolean, relaxHistory: Boolean): List<SoundCloudTrack> {
            if (relaxHistory) {
                val keepIds = seenIds.toList().takeLast(RECENT_BLOCKED)
                seenIds.clear()
                seenIds.addAll(keepIds)
                val keepFps = seenFingerprints.toList().takeLast(RECENT_BLOCKED)
                seenFingerprints.clear()
                seenFingerprints.addAll(keepFps)
            }
            val queues = EnumMap<WaveSource, ArrayDeque<SoundCloudTrack>>(WaveSource::class.java)
            val batchFps = HashSet<String>()
            val batchIds = HashSet<Long>()
            val sources = byService.keys.toMutableList().apply { shuffle(random) }
            for (source in sources) {
                val filtered = byService[source].orEmpty().filter { track ->
                    if (isDisliked(track)) return@filter false
                    if (track.id in seenIds || track.id in batchIds) return@filter false
                    if (strictExclude && track.id in excludeIds) return@filter false
                    val fp = waveTrackFingerprint(track)
                    if (fp in seenFingerprints || fp in batchFps) return@filter false
                    batchIds.add(track.id)
                    batchFps.add(fp)
                    true
                }
                if (filtered.isNotEmpty()) {
                    queues[source] = ArrayDeque(filtered)
                }
            }
            val out = ArrayList<SoundCloudTrack>(targetCount)
            val activeOrder = sources.filter { it in queues }.toMutableList()
            while (out.size < targetCount && activeOrder.isNotEmpty()) {
                val it = activeOrder.iterator()
                while (it.hasNext() && out.size < targetCount) {
                    val source = it.next()
                    val q = queues[source]
                    val next = q?.removeFirstOrNull()
                    if (next != null) {
                        out.add(next)
                        seenIds.add(next.id)
                        seenFingerprints.add(waveTrackFingerprint(next))
                    }
                    if (q == null || q.isEmpty()) it.remove()
                }
            }
            return out
        }

        val first = collect(strictExclude = excludeIds.isNotEmpty(), relaxHistory = false)
        if (first.size >= targetCount) return first
        val combined = ArrayList(first)
        if (excludeIds.isNotEmpty() && combined.size < targetCount) {
            combined += collect(strictExclude = false, relaxHistory = false).take(targetCount - combined.size)
        }
        if (combined.isEmpty() && byService.values.any { it.isNotEmpty() }) {
            combined += collect(strictExclude = false, relaxHistory = true).take(targetCount)
        }
        return combined
    }

    internal fun isDisliked(track: SoundCloudTrack): Boolean {
        val artist = waveLeadArtistKey(track)
        return artist.length >= 2 && artist in dislikedArtists
    }

    private companion object {
        const val MAX_PREFERRED_SEEDS = 16
        const val RECENT_BLOCKED = 20
    }
}
