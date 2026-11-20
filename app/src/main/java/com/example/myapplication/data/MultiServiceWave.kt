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
    """\s*[\[(](?:(?:feat\.?|ft\.?|featuring|with|prod\.?\s+by)\s+[^)\]]+|(?:official\s+)?(?:audio|video|music\s+video|lyric\s+video|lyrics?|visualizer|clip|mv)|audio\s+only|original\s+audio|full\s+song|hd|uhd|4k|hq|explicit(?:\s+version|\s+edit)?|clean(?:\s+version|\s+edit)?|video\s+edit|bonus\s+track|(?:single|album|extended|short|radio)\s+version|radio\s+edit|original\s+mix|deluxe(?:\s+edition)?|remaster(?:ed)?(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?|sped\s+up(?:\s+version)?|slowed(?:\s*(?:\+|&|and)\s*reverb)?|nightcore|daycore)[)\]]""",
    RegexOption.IGNORE_CASE
)
private val waveDashNoise = Regex(
    """\s+[-–—]\s+(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|(?:single|album|extended|short|radio)\s+version|(?:radio|video|clean|explicit)\s+edit|explicit|clean|bonus\s+track|original\s+mix|deluxe(?:\s+edition)?|sped\s+up(?:\s+version)?|slowed(?:\s*(?:\+|&|and)\s*reverb)?|nightcore|daycore)$""",
    RegexOption.IGNORE_CASE
)
private val waveEditionOnlyPart = Regex(
    """^(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|(?:single|album|extended|short|radio|solo|acoustic|live|instrumental)\s+(?:version|ver\.?|edit|mix)|(?:radio|video|clean|explicit)\s+edit|sped\s+up|slowed(?:\s*(?:\+|&|and)\s*reverb)?|nightcore|daycore|ost|op\d*|ed\d*)$""",
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

private fun cleanWaveTitle(raw: String): String {
    var cur = raw.trim()
    repeat(3) {
        val cleaned = cur
            .replace(waveBracketNoise, "")
            .replace(waveDashNoise, "")
            .replace(waveTrailingFeat, "")
            .trim()
        if (cleaned == cur) return@repeat
        cur = cleaned
    }
    return cur
}

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
    var rawTitle = cleanWaveTitle(track.title.orEmpty())
    val parts = waveTitleSplit.split(rawTitle, limit = 2)
    if (parts.size == 2) {
        val leftNorm = waveNormalize(parts[0])
        val rightClean = cleanWaveTitle(parts[1])
        val rightNorm = waveNormalize(rightClean)
        if (artist.isNotEmpty() && leftNorm.contains(artist) && rightNorm.isNotEmpty()) {
            rawTitle = rightClean
        } else if (isSoundCloudTrack(track) && leftNorm.length in 2..36 && rightNorm.length >= 2 && !waveEditionOnlyPart.matches(rightClean)) {
            // SoundCloud titles commonly embed "Real Artist - Song Title" while user.username is a repost/archive channel.
            val embeddedArtist = parts[0].split(waveArtistSep).firstOrNull()?.let(::waveNormalize).orEmpty()
            if (embeddedArtist.length >= 2) {
                return "$embeddedArtist::$rightNorm"
            }
        }
    }
    val title = waveNormalize(cleanWaveTitle(rawTitle))
    return if (artist.isNotEmpty() && title.isNotEmpty()) "$artist::$title" else "id:${track.id}"
}

/** All equivalent fingerprints for [track] (uploader-based and embedded-title-artist-based). */
internal fun waveTrackFingerprints(track: SoundCloudTrack): Set<String> {
    val out = LinkedHashSet<String>(3)
    out += waveTrackFingerprint(track)
    val artist = waveLeadArtistKey(track)
    val cleaned = cleanWaveTitle(track.title.orEmpty())
    val normFull = waveNormalize(cleaned)
    if (artist.isNotEmpty() && normFull.isNotEmpty()) {
        out += "$artist::$normFull"
    }
    val parts = waveTitleSplit.split(cleaned, limit = 2)
    if (parts.size == 2) {
        val embeddedArtist = parts[0].split(waveArtistSep).firstOrNull()?.let(::waveNormalize).orEmpty()
        val rightNorm = waveNormalize(cleanWaveTitle(parts[1]))
        if (embeddedArtist.length >= 2 && rightNorm.length >= 2 && !waveEditionOnlyPart.matches(parts[1].trim())) {
            out += "$embeddedArtist::$rightNorm"
            if (artist.isNotEmpty()) out += "$artist::$rightNorm"
        }
    }
    return out
}

/**
 * Stateful mixer for the multi-service "Monster Wave": deduplicates across services by fingerprint,
 * persists heard tracks across wave restarts, rotates seeds with preference for tracks the listener
 * finished or liked, spaces out tracks by the same artist, filters disliked artists across all
 * services, and round-robin interleaves connected services so the stream feels unified.
 */
internal class MultiServiceWaveMixer(
    private val random: Random = Random.Default,
    initialSeenIds: Collection<Long> = emptyList(),
    initialSeenFingerprints: Collection<String> = emptyList()
) {
    private val seenIds = LinkedHashSet<Long>().apply { addAll(initialSeenIds) }
    private val seenFingerprints = LinkedHashSet<String>().apply { addAll(initialSeenFingerprints) }
    private val dislikedArtists = HashSet<String>()
    private val preferredSeeds = EnumMap<WaveSource, ArrayDeque<SoundCloudTrack>>(WaveSource::class.java)
    private val usedSeedIds = EnumMap<WaveSource, LinkedHashSet<Long>>(WaveSource::class.java)
    private val recentArtists = ArrayDeque<String>()

    fun markQueued(tracks: Collection<SoundCloudTrack>) {
        tracks.forEach { track ->
            seenIds.add(track.id)
            seenFingerprints.addAll(waveTrackFingerprints(track))
            val artist = waveLeadArtistKey(track)
            if (artist.length >= 2) rememberRecentArtist(artist)
        }
        trimHistory()
    }

    fun seenIdsSnapshot(limit: Int = MAX_HISTORY_KEPT): List<Long> =
        seenIds.toList().takeLast(limit)

    fun seenFingerprintsSnapshot(limit: Int = MAX_HISTORY_KEPT): List<String> =
        seenFingerprints.toList().takeLast(limit)

    private fun trimHistory() {
        while (seenIds.size > MAX_HISTORY_KEPT) {
            val it = seenIds.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
        while (seenFingerprints.size > MAX_HISTORY_KEPT * 2) {
            val it = seenFingerprints.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }
    }

    private fun rememberRecentArtist(artist: String) {
        recentArtists.remove(artist)
        recentArtists.addLast(artist)
        while (recentArtists.size > RECENT_ARTIST_WINDOW) recentArtists.removeFirst()
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
        seenFingerprints.addAll(waveTrackFingerprints(track))
        val artist = waveLeadArtistKey(track)
        if (artist.length >= 2) dislikedArtists.add(artist)
        preferredSeeds.values.forEach { queue ->
            queue.removeAll { it.id == track.id || (artist.length >= 2 && waveLeadArtistKey(it) == artist) }
        }
    }

    /** Picks up to [count] distinct fresh seed tracks for [source]. */
    fun pickSeeds(source: WaveSource, fallbacks: List<SoundCloudTrack>, count: Int): List<SoundCloudTrack> {
        if (count <= 0) return emptyList()
        val picked = ArrayList<SoundCloudTrack>(count)
        repeat(count) {
            val next = pickSeedInternal(source, fallbacks, excludeIds = picked.mapTo(HashSet()) { it.id })
            if (next != null) picked.add(next)
        }
        return picked
    }

    /** Picks a fresh seed track for [source], preferring recently enjoyed tracks before [fallbacks]. */
    fun pickSeed(source: WaveSource, fallbacks: List<SoundCloudTrack>): SoundCloudTrack? =
        pickSeedInternal(source, fallbacks, emptySet())

    private fun pickSeedInternal(
        source: WaveSource,
        fallbacks: List<SoundCloudTrack>,
        excludeIds: Set<Long>
    ): SoundCloudTrack? {
        val used = usedSeedIds.getOrPut(source) { LinkedHashSet() }
        val preferred = preferredSeeds[source].orEmpty().filter { it.id !in used && it.id !in excludeIds && !isDisliked(it) }
        if (preferred.isNotEmpty()) {
            val chosen = preferred.first()
            used.add(chosen.id)
            return chosen
        }
        val freshFallbacks = fallbacks.filter { it.id !in used && it.id !in excludeIds && !isDisliked(it) }
        if (freshFallbacks.isNotEmpty()) {
            val chosen = freshFallbacks[random.nextInt(freshFallbacks.size)]
            used.add(chosen.id)
            return chosen
        }
        val pool = (preferredSeeds[source].orEmpty() + fallbacks)
            .filter { it.id !in excludeIds && !isDisliked(it) }
            .distinctBy { it.id }
        if (pool.isEmpty()) return null
        // Keep the most recently used seeds blocked so a small pool doesn't pick the same seed twice in a row.
        val keepRecent = used.toList().takeLast((pool.size / 2).coerceAtMost(8))
        used.clear()
        used.addAll(keepRecent)
        val candidates = pool.filter { it.id !in used }.ifEmpty { pool }
        val chosen = candidates[random.nextInt(candidates.size)]
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
        fun collect(strictExclude: Boolean, relaxHistory: Boolean, enforceArtistCap: Boolean): List<SoundCloudTrack> {
            if (relaxHistory) {
                val keepIds = seenIds.toList().takeLast(RECENT_BLOCKED)
                seenIds.clear()
                seenIds.addAll(keepIds)
                val keepFps = seenFingerprints.toList().takeLast(RECENT_BLOCKED * 2)
                seenFingerprints.clear()
                seenFingerprints.addAll(keepFps)
            }
            val queues = EnumMap<WaveSource, ArrayDeque<SoundCloudTrack>>(WaveSource::class.java)
            val batchFps = HashSet<String>()
            val batchIds = HashSet<Long>()
            val artistCounts = HashMap<String, Int>()
            val sources = byService.keys.toMutableList().apply { shuffle(random) }
            for (source in sources) {
                val filtered = byService[source].orEmpty().filter { track ->
                    if (isDisliked(track)) return@filter false
                    if (track.id in seenIds || track.id in batchIds) return@filter false
                    if (strictExclude && track.id in excludeIds) return@filter false
                    val fps = waveTrackFingerprints(track)
                    if (fps.any { it in seenFingerprints || it in batchFps }) return@filter false
                    val artist = waveLeadArtistKey(track)
                    if (enforceArtistCap && artist.length >= 2) {
                        if (artist in recentArtists) return@filter false
                        val count = artistCounts[artist] ?: 0
                        if (count >= MAX_PER_ARTIST_IN_BATCH) return@filter false
                        artistCounts[artist] = count + 1
                    }
                    batchIds.add(track.id)
                    batchFps.addAll(fps)
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
                        seenFingerprints.addAll(waveTrackFingerprints(next))
                        val artist = waveLeadArtistKey(next)
                        if (artist.length >= 2) rememberRecentArtist(artist)
                    }
                    if (q == null || q.isEmpty()) it.remove()
                }
            }
            trimHistory()
            return out
        }

        val first = collect(strictExclude = excludeIds.isNotEmpty(), relaxHistory = false, enforceArtistCap = true)
        if (first.size >= targetCount) return first
        val combined = ArrayList(first)
        if (combined.size < targetCount) {
            combined += collect(strictExclude = excludeIds.isNotEmpty(), relaxHistory = false, enforceArtistCap = false)
                .take(targetCount - combined.size)
        }
        if (excludeIds.isNotEmpty() && combined.size < targetCount) {
            combined += collect(strictExclude = false, relaxHistory = false, enforceArtistCap = false)
                .take(targetCount - combined.size)
        }
        if (combined.isEmpty() && byService.values.any { it.isNotEmpty() }) {
            combined += collect(strictExclude = false, relaxHistory = true, enforceArtistCap = false).take(targetCount)
        }
        return combined
    }

    internal fun isDisliked(track: SoundCloudTrack): Boolean {
        val artist = waveLeadArtistKey(track)
        return artist.length >= 2 && artist in dislikedArtists
    }

    private companion object {
        const val MAX_PREFERRED_SEEDS = 24
        const val RECENT_BLOCKED = 200
        const val MAX_HISTORY_KEPT = 600
        const val RECENT_ARTIST_WINDOW = 3
        const val MAX_PER_ARTIST_IN_BATCH = 1
    }
}
