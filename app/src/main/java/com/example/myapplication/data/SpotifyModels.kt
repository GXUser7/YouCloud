package com.example.myapplication.data

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.text.Normalizer
import java.util.Locale

const val SPOTIFY_TRACK = "spotify:track:"
const val SPOTIFY_ARTIST = "spotify:artist:"
const val SPOTIFY_ALBUM = "spotify:album:"
const val SPOTIFY_PLAYLIST = "spotify:playlist:"
const val SPOTIFY_LIKED = "spotify:collection:tracks"

private val spotifyId = Regex("[A-Za-z0-9]{22}")

internal fun String.isSpotifyId(): Boolean = spotifyId.matches(this)

/** Stable, service-specific Long key for playback, downloads and saved tracks. */
internal fun spotifyKey(ref: String): Long {
    var hash = -0x340d631b7bdddcdbL
    for (c in ref) hash = (hash xor c.code.toLong()) * 0x100000001b3L
    return -(4_000_000_000_000L + (hash and 0xFF_FFFF_FFFFL))
}

val SoundCloudTrack.spotifyId: String?
    get() = urn.spotifyIdFor(SPOTIFY_TRACK)

internal fun String?.spotifyIdFor(prefix: String): String? =
    this?.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)?.takeIf { it.isSpotifyId() }

internal fun JsonElement?.spotifyObject(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.obj(key: String): JsonObject? = get(key).spotifyObject()
internal fun JsonObject.items(key: String): List<JsonObject> =
    get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.spotifyObject() }.orEmpty()

internal fun JsonObject.string(key: String): String? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }

internal fun JsonObject.number(key: String): Long? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong

private fun JsonObject.flag(key: String): Boolean? =
    get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

private fun JsonObject.spotifyData(): JsonObject = obj("data") ?: this

private fun JsonObject.serviceId(raw: JsonObject, ref: String): String? {
    (string("uri") ?: raw.string("_uri"))?.let { return it.spotifyIdFor(ref) }
    return string("id")?.takeIf(String::isSpotifyId)
}

private fun JsonObject.cover(): String? {
    val images = items("images") + obj("coverArt")?.items("sources").orEmpty() +
        obj("images")?.items("items")?.flatMap { it.items("sources") }.orEmpty() +
        obj("visuals")?.obj("avatarImage")?.items("sources").orEmpty() +
        obj("image")?.items("sources").orEmpty() +
        obj("visualIdentity")?.obj("squareCoverImage")?.obj("image")?.obj("data")?.items("sources").orEmpty()
    return images.firstNotNullOfOrNull { it.string("url")?.takeIf { url -> spotifyHttpsHost(url) != null } }
}

private fun JsonObject.credits(): List<JsonObject> =
    obj("artists")?.items("items")
        ?: (obj("firstArtist")?.items("items").orEmpty() + obj("otherArtists")?.items("items").orEmpty())
            .takeIf { it.isNotEmpty() }
        ?: items("artists")

internal fun spotifyArtist(raw: JsonObject): SoundCloudUser? {
    val data = raw.spotifyData()
    val name = data.obj("profile")?.string("name") ?: data.string("name") ?: return null
    val ref = data.serviceId(raw, SPOTIFY_ARTIST)?.let { SPOTIFY_ARTIST + it }
    return SoundCloudUser(
        id = ref?.let(::spotifyKey),
        username = name,
        avatarUrl = data.cover(),
        followersCount = data.obj("stats")?.number("followers")?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt(),
        permalinkUrl = ref
    )
}

internal fun spotifyTrack(raw: JsonObject, album: JsonObject? = null): SoundCloudTrack? {
    val data = raw.spotifyData()
    if (data.flag("is_local") == true || data.flag("is_playable") == false || data.obj("restrictions") != null) return null
    if (data.string("__typename")?.let { it != "Track" } == true ||
        data.string("type")?.let { !it.equals("track", ignoreCase = true) } == true) return null
    val id = data.serviceId(raw, SPOTIFY_TRACK) ?: return null
    val title = data.string("name") ?: return null
    val duration = (data.obj("duration")?.number("totalMilliseconds")
        ?: data.obj("trackDuration")?.number("totalMilliseconds")
        ?: data.number("duration_ms"))?.takeIf { it > 0 } ?: return null
    val artists = data.credits().mapNotNull(::spotifyArtist).distinctBy { it.permalinkUrl ?: it.username }
    val ref = SPOTIFY_TRACK + id
    return SoundCloudTrack(
        id = spotifyKey(ref),
        urn = ref,
        kind = "track",
        title = title,
        artworkUrl = (data.obj("albumOfTrack") ?: data.obj("album"))?.cover() ?: album?.cover(),
        permalinkUrl = "https://open.spotify.com/track/$id",
        user = artists.firstOrNull(),
        artists = artists,
        duration = duration,
        streamable = true
    )
}

internal fun spotifySet(raw: JsonObject, isAlbum: Boolean): SoundCloudPlaylist? {
    val data = raw.spotifyData()
    val typename = data.string("__typename")
    if (typename == "NotFound") return null
    val prefix = if (isAlbum) SPOTIFY_ALBUM else SPOTIFY_PLAYLIST
    if (typename in setOf("Track", "Artist", "Episode", "Show")) return null
    val id = data.serviceId(raw, prefix) ?: return null
    val name = data.string("name") ?: return null
    val ref = prefix + id
    val count = if (isAlbum) {
        data.obj("tracksV2")?.number("totalCount") ?: data.number("total_tracks")
    } else {
        data.obj("content")?.number("totalCount") ?: (data.obj("items") ?: data.obj("tracks"))?.number("total")
    }
    return SoundCloudPlaylist(
        id = spotifyKey(ref),
        title = name,
        trackCount = count?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
        artworkUrl = data.cover(),
        permalinkUrl = ref,
        user = if (isAlbum) data.credits().firstNotNullOfOrNull(::spotifyArtist)
        else (data.obj("ownerV2")?.obj("data") ?: data.obj("owner"))?.let {
            SoundCloudUser(username = it.string("name") ?: it.string("display_name") ?: it.string("id"))
        },
        isAlbum = isAlbum,
        setType = data.string("type")?.lowercase(Locale.ROOT) ?: data.string("album_type"),
        releaseDate = data.obj("date")?.string("isoString") ?: data.string("release_date")
    )
}

private class MutableSpotifyShelf(
    val title: String,
    val tracks: MutableList<SoundCloudTrack> = mutableListOf(),
    val sets: MutableList<SoundCloudPlaylist> = mutableListOf(),
    val artists: MutableList<SoundCloudUser> = mutableListOf()
)

internal fun spotifyHomeRows(data: JsonObject): List<YtShelf> {
    // Spotify's home payload frequently emits several adjacent sections under the same heading.
    // Merge them in first-seen order so home shows one combined shelf per heading instead of splits.
    val merged = LinkedHashMap<String, MutableSpotifyShelf>()
    for (section in data.obj("home")?.obj("sectionContainer")?.obj("sections")?.items("items").orEmpty()) {
        val title = section.obj("data")?.obj("title") ?: continue
        val name = (title.string("transformedLabel") ?: title.string("text"))
            ?.replace('\u00a0', ' ')?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val cards = section.obj("sectionItems")?.items("items").orEmpty().mapNotNull { it.obj("content") }
        val sets = cards.mapNotNull { card -> spotifySet(card, isAlbum = false) ?: spotifySet(card, isAlbum = true) }
        val tracks = cards.mapNotNull(::spotifyTrack)
        val artists = cards.filter { it.spotifyData().serviceId(it, SPOTIFY_ARTIST) != null }.mapNotNull(::spotifyArtist)
        if (tracks.isEmpty() && sets.isEmpty() && artists.isEmpty()) continue
        val bucket = merged.getOrPut(name.lowercase(Locale.ROOT)) { MutableSpotifyShelf(name) }
        bucket.tracks += tracks
        bucket.sets += sets
        bucket.artists += artists
    }
    return merged.values.map { shelf ->
        YtShelf(
            title = shelf.title,
            tracks = shelf.tracks.distinctBy { it.id },
            sets = shelf.sets.distinctBy { it.id },
            artists = shelf.artists.distinctBy { it.permalinkUrl ?: it.id ?: it.username }
        )
    }
}

private val bracketNoise = Regex(
    """\s*[\[(](?:(?:feat\.?|ft\.?|featuring|with|prod\.?\s+by)\s+[^)\]]+|(?:official\s+)?(?:audio|video|music\s+video|lyric\s+video|lyrics?|visualizer|clip)|audio\s+only|original\s+audio|full\s+song|hd|uhd|4k|hq|explicit(?:\s+version|\s+edit)?|clean(?:\s+version|\s+edit)?|video\s+edit|bonus\s+track|(?:single|album|extended|short|radio)\s+version|radio\s+edit|original\s+mix|deluxe(?:\s+edition)?|remaster(?:ed)?(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?|from\s+["“].*?["”])[)\]]""",
    RegexOption.IGNORE_CASE
)
private val dashNoise = Regex(
    """\s+[-–—]\s+(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|(?:single|album|extended|short|radio)\s+version|(?:radio|video|clean|explicit)\s+edit|explicit|clean|bonus\s+track|original\s+mix|deluxe(?:\s+edition)?|from\s+["“].*?["”].*)$""",
    RegexOption.IGNORE_CASE
)
private val trailingFeat = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+[^-–—]+$""", RegexOption.IGNORE_CASE)
private val safeEditionNoise = Regex(
    """\b(?:(?:radio|video|clean|explicit|album|single|short)\s+edit|(?:single|album|extended|short|radio)\s+version|original\s+mix)\b""",
    RegexOption.IGNORE_CASE
)
private val editionWords = Regex(
    """\b(?:live|remix|cover|karaoke|instrumental|acoustic|sped[\s-]+up|slowed|nightcore|8d|mashup|demo|edit|reverb|bootleg|acapella|a\s+cappella)\b""",
    RegexOption.IGNORE_CASE
)
private val titleSplit = Regex("""\s*[-–—|]\s+|\s+[-–—|:]\s*""")
private val artistSeparators = Regex("""\s*(?:,|&|\+|/|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\band\b|\bx\b|\bи\b)\s*""", RegexOption.IGNORE_CASE)
private val anyBrackets = Regex("""\s*[\[(][^)\]]*[)\]]""")
private val apostrophes = Regex("""['’`ʼ]""")
private val inlineBang = Regex("""(?<=[\p{L}\p{N}])!(?=[\p{L}\p{N}])""")
private val inlineDollar = Regex("""(?<=[\p{L}\p{N}])\$(?=[\p{L}\p{N}])""")
private val marks = Regex("\\p{M}+")
private val separators = Regex("[^\\p{L}\\p{N}]+")

private fun normalized(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        .replace(marks, "")
        .replace(apostrophes, "")
        .replace(inlineBang, "i")
        .replace(inlineDollar, "s")
        .replace("&", " and ")
        .replace(separators, " ")
        .trim()

private fun cleanAudioTitle(raw: String): String {
    var result = raw.trim()
    repeat(3) {
        val next = result
            .replace(bracketNoise, "")
            .replace(dashNoise, "")
            .replace(trailingFeat, "")
            .trim()
        if (next == result) return@repeat
        result = next
    }
    return result.ifEmpty { raw.trim() }
}

internal fun spotifyAudioQuery(track: SoundCloudTrack): String {
    val artist = track.artists?.firstOrNull()?.username ?: track.user?.username
    val title = cleanAudioTitle(track.title.orEmpty())
    return listOfNotNull(artist, title.takeIf { it.isNotBlank() }).joinToString(" ")
}

internal fun spotifyAudioFallbackQuery(track: SoundCloudTrack): String? {
    val artist = track.artists?.firstOrNull()?.username ?: track.user?.username
    val rawTitle = track.title.orEmpty()
    val simplified = cleanAudioTitle(rawTitle)
        .replace(anyBrackets, " ")
        .replace(Regex("""\s+[-–—]\s+.*$"""), "")
        .trim()
        .ifBlank { cleanAudioTitle(rawTitle) }
    val primary = spotifyAudioQuery(track)
    val fallback = listOfNotNull(artist, simplified.takeIf { it.isNotBlank() }).joinToString(" ").trim()
    return fallback.takeIf { it.isNotBlank() && !it.equals(primary, ignoreCase = true) }
}

internal fun spotifyTrackDurationTolerance(targetDurationMs: Long): Long =
    maxOf(12_000L, minOf(25_000L, targetDurationMs / 8))

private fun editionTags(title: String): Set<String> =
    editionWords.findAll(title.replace(safeEditionNoise, " "))
        .map { it.value.lowercase(Locale.ROOT).replace(Regex("""[\s-]+"""), " ").trim() }
        .toSet()

private fun splitArtistCredits(track: SoundCloudTrack): List<String> {
    val rawNames = (track.artists.orEmpty() + listOfNotNull(track.user))
        .mapNotNull { it.username?.trim()?.takeIf(String::isNotEmpty) }
    val out = LinkedHashSet<String>()
    for (raw in rawNames) {
        val cleaned = normalized(raw)
            .removeSuffix(" - topic")
            .removeSuffix(" topic")
            .removeSuffix(" vevo")
            .removeSuffix(" official")
            .trim()
        if (cleaned.isNotEmpty()) out += cleaned
        for (part in raw.split(artistSeparators)) {
            val sub = normalized(part)
                .removeSuffix(" topic")
                .removeSuffix(" vevo")
                .removeSuffix(" official")
                .trim()
            if (sub.length >= 2) out += sub
        }
    }
    return out.toList()
}

private fun artistMatchBonus(targetArtists: List<String>, candArtists: List<String>, leadTarget: String): Long? {
    if (targetArtists.isEmpty() || candArtists.isEmpty()) return null
    if (leadTarget in candArtists) return 2_000L
    if (targetArtists.any { it in candArtists }) return 1_400L
    return null
}

private fun titleSimilarityScore(targetTitle: String, candidateTitle: String): Long? {
    if (targetTitle.isEmpty() || candidateTitle.isEmpty()) return null
    if (targetTitle == candidateTitle) return 10_000L
    if (targetTitle.replace(" ", "") == candidateTitle.replace(" ", "")) return 9_500L
    val tWords = targetTitle.split(' ').filter { it.isNotEmpty() }
    val cWords = candidateTitle.split(' ').filter { it.isNotEmpty() }
    if (tWords.isEmpty() || cWords.isEmpty()) return null
    // Single-word titles are prone to false matches like "Love" vs "Love Story": require exact match.
    if (tWords.size == 1) {
        return if (cWords.size == 1 && tWords[0] == cWords[0]) 10_000L else null
    }
    val cSet = cWords.toSet()
    val tSet = tWords.toSet()
    val intersection = tSet.count { it in cSet }
    val union = (tSet + cSet).size
    val jaccard = intersection.toDouble() / union
    val extraWords = kotlin.math.abs(cWords.size - tWords.size)
    val targetContained = (" $candidateTitle ").contains(" $targetTitle ") && extraWords <= 3
    val candContained = (" $targetTitle ").contains(" $candidateTitle ") && extraWords <= 2
    if (!targetContained && !candContained && jaccard < 0.72) return null
    val bonus = if (targetContained || candContained) 1_000L else 0L
    return 6_000L + (jaccard * 2_500.0).toLong() + bonus
}

/** A download or play must have the same recording, not merely a similar title. */
internal fun spotifyMatchScore(target: SoundCloudTrack, candidate: SoundCloudTrack): Long? {
    if (candidate.duration <= 0 || target.duration <= 0) return null
    val difference = kotlin.math.abs(target.duration - candidate.duration)
    if (difference > spotifyTrackDurationTolerance(target.duration)) return null

    val rawTargetTitle = target.title.orEmpty()
    val rawCandidateTitle = candidate.title.orEmpty()
    if (editionTags(rawTargetTitle) != editionTags(rawCandidateTitle)) return null

    val targetArtists = splitArtistCredits(target)
    val leadTarget = targetArtists.firstOrNull() ?: return null
    val cleanTarget = normalized(cleanAudioTitle(rawTargetTitle))
    if (cleanTarget.isEmpty()) return null

    val candCredits = splitArtistCredits(candidate)
    var bestTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(rawCandidateTitle)))
    var bestArtistBonus = artistMatchBonus(targetArtists, candCredits, leadTarget)

    // SoundCloud & YouTube uploads often put "Artist - Title" into the track title itself.
    val splitParts = titleSplit.split(rawCandidateTitle, limit = 2)
    if (splitParts.size == 2) {
        val leftArtists = splitParts[0].split(artistSeparators)
            .map(::normalized).filter { it.length >= 2 } + listOf(normalized(splitParts[0]))
        val prefixArtistBonus = artistMatchBonus(targetArtists, leftArtists.filter(String::isNotEmpty), leadTarget)
        val rightTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(splitParts[1])))
        if (rightTitleScore != null && (prefixArtistBonus != null || bestArtistBonus != null)) {
            val combinedBonus = maxOf(prefixArtistBonus ?: 0L, bestArtistBonus ?: 0L)
            if (bestTitleScore == null || bestArtistBonus == null || rightTitleScore > bestTitleScore) {
                bestTitleScore = rightTitleScore
                bestArtistBonus = combinedBonus
            }
        }
    }

    val titleScore = bestTitleScore ?: return null
    val artistScore = bestArtistBonus ?: return null
    return maxOf(1L, titleScore + artistScore - difference / 10L)
}

internal fun spotifyMatchRejectReason(target: SoundCloudTrack, candidate: SoundCloudTrack): String {
    if (candidate.duration <= 0) return "no_duration"
    val diff = kotlin.math.abs(target.duration - candidate.duration)
    val tol = spotifyTrackDurationTolerance(target.duration)
    if (diff > tol) return "duration(cand=${candidate.duration / 1000}s, target=${target.duration / 1000}s, tol=${tol / 1000}s)"
    val te = editionTags(target.title.orEmpty())
    val ce = editionTags(candidate.title.orEmpty())
    if (te != ce) return "edition(target=$te, cand=$ce)"
    val tArtists = splitArtistCredits(target)
    val cArtists = splitArtistCredits(candidate)
    val lead = tArtists.firstOrNull() ?: return "no_target_artist"
    if (artistMatchBonus(tArtists, cArtists, lead) == null) {
        val splitParts = titleSplit.split(candidate.title.orEmpty(), limit = 2)
        val leftHit = splitParts.size == 2 && artistMatchBonus(
            tArtists,
            (splitParts[0].split(artistSeparators).map(::normalized) + listOf(normalized(splitParts[0]))).filter { it.length >= 2 },
            lead
        ) != null
        if (!leftHit) return "artist(target=${tArtists.take(3)}, cand=${cArtists.take(3)})"
    }
    return "title(target='${cleanAudioTitle(target.title.orEmpty())}', cand='${cleanAudioTitle(candidate.title.orEmpty())}')"
}
