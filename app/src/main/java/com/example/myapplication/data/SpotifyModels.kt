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
    """\s*[\[(](?:(?:feat\.?|ft\.?|featuring|with|prod\.?\s+by)\s+[^)\]]+|(?:official\s+)?(?:audio|video|music\s+video|lyric\s+video|lyrics?|visualizer|clip|mv)|(?:color\s+coded\s+)?lyrics?(?:\s+[^)\]]*)?|kan[/\s]+rom[/\s]+eng[^)\]]*|audio\s+only|original\s+audio|full(?:\s+song|\s+version)?|hd|uhd|4k|hq|explicit(?:\s+version|\s+edit)?|clean(?:\s+version|\s+edit)?|video\s+edit|bonus\s+track|(?:single|album|extended|short|radio)\s+version|radio\s+edit|original\s+mix|deluxe(?:\s+edition)?|remaster(?:ed)?(?:\s+\d{4})?|\d{4}\s+remaster(?:ed)?|from\s+["“].*?["”])[)\]]""",
    RegexOption.IGNORE_CASE
)
private val dashNoise = Regex(
    """\s+[-–—]\s+(?:(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?|(?:single|album|extended|short|radio)\s+version|(?:radio|video|clean|explicit)\s+edit|explicit|clean|bonus\s+track|original\s+mix|deluxe(?:\s+edition)?|from\s+["“].*?["”].*)$""",
    RegexOption.IGNORE_CASE
)
private val trailingFeat = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+[^-–—]+$""", RegexOption.IGNORE_CASE)
private val trailingLyrics = Regex("""\s+(?:color\s+coded\s+)?(?:lyric\s+video|lyrics?|official\s+audio|official\s+video|official\s+mv)\s*$""", RegexOption.IGNORE_CASE)
private val safeEditionNoise = Regex(
    """\b(?:(?:radio|video|clean|explicit|album|single|short)\s+edit|(?:single|album|extended|short|radio)\s+version|original\s+mix)\b""",
    RegexOption.IGNORE_CASE
)
private val editionWords = Regex(
    """\b(?:live|remix|cover|karaoke|instrumental|acoustic|arrangement|soundfont|jammable|ai\s+cover|ai\s+version|version\s+by|sung\s+by|played\s+by|performed\s+by|in\s+the\s+style\s+of|sped[\s-]+up|slowed|nightcore|8d|mashup|demo|edit|reverb|bootleg|acapella|a\s+cappella|backing\s+track|minus(?:\s+one)?|off\s+vocal|no\s+vocal(?:s)?|without\s+vocal(?:s)?|vocal(?:s)?\s+only|guitar|piano|bass|drum(?:s)?|violin|cello|flute|saxophone|orchestra(?:l)?|symphonic|trio|quartet|duo|music\s+box|lullaby|city\s+pop|8[\s-]*bit|chiptune|lo[\s-]*fi|lofi|synthesia|tutorial|lesson|tabs?|chords?|midi|ringtone|pitched|pitch[\s-]+shifted|daycore|doomer|but|dance\s+practice|choreography|behind\s+the\s+scenes|making\s+of|teaser|trailer|reaction|analysis|review|breakdown|interview|commentary|snippet|preview|soviet\s+ver(?:sion)?|минус(?:овка)?|кавер|караоке|инструментал|под\s+гитару|на\s+пианино|на\s+гитаре|без\s+слов|без\s+вокала)\b|[+-]\d+\s*key\b|原曲歌手|カラオケ|弾いてみた|歌ってみた|叩いてみた|踊ってみた|吹いてみた|切り抜き|リアクション|ピアノ|ギター""",
    RegexOption.IGNORE_CASE
)
private val suspiciousChannelWords = Regex(
    """\b(?:backing\s+track(?:s)?|minus(?:\s+tracks?|\s+one)?|karaoke|instrumental(?:s)?|covers?|piano|guitar|violin|drum(?:s)?|bass|trio|quartet|orchestra|music\s+box|lullaby|acapella|a\s+cappella|jammable|soundfont|synthesia|tabs?|tutorial(?:s)?|nightcore|slowed|sped[\s-]+up|8d\s+audio|gameplay|liveplay|beatmaps?|typing|keyboard|asmr|beat\s+saber|clone\s+hero|geometry\s+dash|минус(?:овка|овки)?|караоке|кавер(?:ы)?|прохождение|геймплей)\b|(?<![\p{L}\p{N}])osu(?:![\p{L}]*)?(?![\p{L}\p{N}])|歌っちゃ王""",
    RegexOption.IGNORE_CASE
)
private val gameplayPatterns = Regex(
    """(?:^\s*\d{6}\s+|(?<![\p{L}\p{N}])osu(?:![\p{L}]*)?(?![\p{L}\p{N}])|\b(?:gameplay|playthrough|liveplay|handcam|beatmap|full\s+combo|all\s+perfect|beat\s+saber|geometry\s+dash|clone\s+hero|guitar\s+hero|rock\s+band|stepmania|etterna|quaver|muse\s+dash|project\s+sekai|proseka|arcaea|cytus|phigros|friday\s+night\s+funkin|fnf|maimai|chunithm|sound\s+voltex|sdvx|typing|keyboard|keycaps|sound\s+test|asmr|speedrun|прохождение|геймплей)\b|\b(?:hd|hr|dt|nc|ez|fl|ht){2,}\b|\+\s*(?:hd|hr|dt|nc|ez|fl|ht)+\b|\b\d{2,4}\s*pp\b|\b\d{2,3}\.\d{1,2}\s*%|[\[(]\s*(?:fc|ss|choke|nomod|nm|dt|hr)\s*[)\]])""",
    RegexOption.IGNORE_CASE
)
private val titleSplit = Regex("""\s*[-–—|]\s+|\s+[-–—|:]\s*|:\s+""")
private val bySplit = Regex("""\s+by\s+""", RegexOption.IGNORE_CASE)
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

internal fun cleanAudioTitle(raw: String): String {
    var result = raw.trim()
    repeat(3) {
        val next = result
            .replace(bracketNoise, "")
            .replace(dashNoise, "")
            .replace(trailingFeat, "")
            .replace(trailingLyrics, "")
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

/** Official artist channel music videos often carry a 25–60s intro/outro or credits. */
internal fun spotifyOfficialMvDurationTolerance(targetDurationMs: Long): Long =
    maxOf(spotifyTrackDurationTolerance(targetDurationMs), minOf(60_000L, targetDurationMs / 3))

internal fun spotifyHasOfficialChannel(target: SoundCloudTrack, candidate: SoundCloudTrack): Boolean {
    val targetArtists = splitArtistCredits(target)
    val leadTarget = targetArtists.firstOrNull() ?: return false
    return artistMatchBonus(targetArtists, splitArtistCredits(candidate), leadTarget) != null
}

/**
 * Post-extraction YouTube metadata filter: rejects third-party gameplay/rhythm-game replays
 * (e.g. a 47-view osu! replay titled "Kenshi Yonezu - KICK BACK" in category "Gaming") while
 * never touching official artist/Topic channels or regular Music/People & Blogs uploads.
 */
internal fun spotifyYouTubeMetadataRejected(
    target: SoundCloudTrack,
    isOfficialChannel: Boolean,
    category: String?,
    tags: List<String>,
    viewCount: Long?
): Boolean {
    if (isOfficialChannel) return false
    val targetGameTags = gameplayPatterns.findAll(target.title.orEmpty()).map { it.value.lowercase(Locale.ROOT) }.toSet()
    if (tags.isNotEmpty()) {
        val joinedTags = tags.joinToString(" ")
        val hasExtraGameplayTag = gameplayPatterns.findAll(joinedTags)
            .any { it.value.lowercase(Locale.ROOT) !in targetGameTags }
        if (hasExtraGameplayTag) return true
    }
    if (category.equals("Gaming", ignoreCase = true)) {
        // Low/medium-view third-party Gaming uploads are almost always osu!/rhythm-game/gameplay
        // clips with hitsounds or SFX; legitimate soundtrack uploads have high views or official channels.
        if (viewCount == null || viewCount < 50_000L) return true
    }
    return false
}

private val popularFallbackBlockedEditions = setOf(
    "karaoke", "backing track", "minus", "minus one", "off vocal", "no vocal", "no vocals",
    "without vocal", "without vocals", "tutorial", "lesson", "tab", "tabs", "chord", "chords",
    "synthesia", "midi", "ringtone", "reaction", "analysis", "review", "breakdown", "interview",
    "commentary", "teaser", "trailer", "snippet", "preview", "минус", "минусовка", "караоке",
    "под гитару", "на пианино", "на гитаре", "без слов", "без вокала", "прохождение", "геймплей",
    "カラオケ", "原曲歌手", "リアクション", "切り抜き"
)

/**
 * Last-resort fallback used ONLY when both strict matching and loose-duration matching found zero
 * candidates across all services: picks the most popular music-length YouTube upload from the
 * search results that shares title/artist words and is not gameplay, karaoke or a backing track.
 */
internal fun spotifyPopularYouTubeFallbackScore(target: SoundCloudTrack, candidate: SoundCloudTrack): Long? {
    val minDuration = if (target.duration in 15_000L..45_000L) maxOf(12_000L, target.duration / 2) else 30_000L
    val maxDuration = if (target.duration > 450_000L) target.duration * 2 else 900_000L
    if (candidate.duration > 0L && candidate.duration !in minDuration..maxDuration) return null
    val rawCandidateTitle = candidate.title.orEmpty()
    if (rawCandidateTitle.isBlank()) return null

    val targetEditions = editionTags(target.title.orEmpty())
    val extraEditions = editionTags(rawCandidateTitle) - targetEditions
    if (extraEditions.any { it.startsWith("game:") || it in popularFallbackBlockedEditions }) return null

    val extraChannelVariants = channelVariantTags(candidate) - channelVariantTags(target) - targetEditions
    val channelPenalty = if (extraChannelVariants.isNotEmpty()) 4_000L else 0L

    val targetTitleWords = normalized(cleanAudioTitle(target.title.orEmpty())).split(' ').filter { it.length >= 2 }
    val targetArtistWords = splitArtistCredits(target).flatMap { it.split(' ') }.filter { it.length >= 2 }
    val candWords = (
        normalized(rawCandidateTitle).split(' ') +
            splitArtistCredits(candidate).flatMap { it.split(' ') }
        ).filter { it.length >= 2 }.toSet()

    val titleHits = targetTitleWords.count { it in candWords }
    val artistHits = targetArtistWords.count { it in candWords }
    val officialBonus = if (spotifyHasOfficialChannel(target, candidate)) 5_000L else 0L
    val hasOverlap = titleHits > 0 || artistHits > 0 || officialBonus > 0L
    val tierBase = if (hasOverlap) 15_000L else 1_000L
    val views = (candidate.playbackCount ?: 0L).coerceAtLeast(0L)
    val viewsScore = if (views > 0L) (kotlin.math.log10(views.toDouble() + 1.0) * 2_000.0).toLong() else 0L
    val durationPenalty = if (target.duration > 0L && candidate.duration > 0L) {
        minOf(6_000L, kotlin.math.abs(target.duration - candidate.duration) / 50L)
    } else 0L
    return maxOf(
        1L,
        tierBase + viewsScore + officialBonus + titleHits * 800L + artistHits * 600L - durationPenalty - channelPenalty
    )
}

private fun editionTags(title: String): Set<String> {
    val cleaned = title.replace(safeEditionNoise, " ")
    val editions = editionWords.findAll(cleaned)
        .map { it.value.lowercase(Locale.ROOT).replace(Regex("""[\s-]+"""), " ").trim() }
    val gameplay = gameplayPatterns.findAll(cleaned)
        .map { "game:" + it.value.lowercase(Locale.ROOT).replace(Regex("""[\s-]+"""), " ").trim() }
    return (editions + gameplay).toSet()
}

private fun channelVariantTags(track: SoundCloudTrack): Set<String> {
    val raw = (track.artists.orEmpty() + listOfNotNull(track.user))
        .mapNotNull { it.username?.trim()?.takeIf(String::isNotEmpty) }
        .joinToString(" ")
    if (raw.isEmpty()) return emptySet()
    return suspiciousChannelWords.findAll(raw)
        .map { it.value.lowercase(Locale.ROOT).replace(Regex("""[\s-]+"""), " ").trim() }
        .toSet()
}

private fun addArtistNameVariants(out: MutableSet<String>, raw: String) {
    val cleaned = normalized(raw)
        .removeSuffix(" - topic")
        .removeSuffix(" topic")
        .removeSuffix(" vevo")
        .removeSuffix(" official")
        .trim()
    if (cleaned.isEmpty()) return
    out += cleaned
    // Official bilingual artist channels & titles like "Kenshi Yonezu  米津玄師" combine Latin and
    // non-Latin names separated only by whitespace; expose each script segment independently.
    val words = cleaned.split(' ').filter { it.isNotEmpty() }
    val latinWords = words.filter { w -> w.any { it in 'a'..'z' } && w.none { it.code > 127 } }
    val nonLatinWords = words.filter { w -> w.any { it.code > 127 } }
    if (latinWords.isNotEmpty() && nonLatinWords.isNotEmpty()) {
        val latinPart = latinWords.joinToString(" ")
        val nonLatinPart = nonLatinWords.joinToString(" ")
        if (latinPart.length >= 2) out += latinPart
        if (nonLatinPart.length >= 2) out += nonLatinPart
    }
}

private fun splitArtistCredits(track: SoundCloudTrack): List<String> {
    val rawNames = (track.artists.orEmpty() + listOfNotNull(track.user))
        .mapNotNull { it.username?.trim()?.takeIf(String::isNotEmpty) }
    val out = LinkedHashSet<String>()
    for (raw in rawNames) {
        addArtistNameVariants(out, raw)
        for (part in raw.split(artistSeparators)) {
            addArtistNameVariants(out, part)
        }
    }
    return out.toList()
}

private fun artistPartVariants(part: String): List<String> {
    val out = LinkedHashSet<String>()
    addArtistNameVariants(out, part)
    for (sub in part.split(artistSeparators)) {
        addArtistNameVariants(out, sub)
    }
    return out.filter { it.length >= 2 }
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
internal fun spotifyMatchScore(target: SoundCloudTrack, candidate: SoundCloudTrack): Long? =
    spotifyMatchScoreInternal(target, candidate, looseDuration = false)

/**
 * Fallback used only when strict matching found zero candidates across all services: allows a
 * wider duration window (at least 50s and 55–145% of Spotify's length) ONLY for exact title +
 * lead-artist matches with clean edition and channel tags (e.g. shorter reuploads of underground
 * tracks without ever accepting covers, backing tracks or 30s previews).
 */
internal fun spotifyLooseDurationMatchScore(target: SoundCloudTrack, candidate: SoundCloudTrack): Long? =
    spotifyMatchScoreInternal(target, candidate, looseDuration = true)

private fun spotifyMatchScoreInternal(
    target: SoundCloudTrack,
    candidate: SoundCloudTrack,
    looseDuration: Boolean
): Long? {
    if (candidate.duration <= 0 || target.duration <= 0) return null

    val rawTargetTitle = target.title.orEmpty()
    val rawCandidateTitle = candidate.title.orEmpty()
    val targetEditions = editionTags(rawTargetTitle)
    if (targetEditions != editionTags(rawCandidateTitle)) return null

    val candChannelVariants = channelVariantTags(candidate)
    if (candChannelVariants.isNotEmpty()) {
        val allowed = channelVariantTags(target) + targetEditions
        if (candChannelVariants.any { it !in allowed }) return null
    }

    val targetArtists = splitArtistCredits(target)
    val leadTarget = targetArtists.firstOrNull() ?: return null
    val cleanTarget = normalized(cleanAudioTitle(rawTargetTitle))
    if (cleanTarget.isEmpty()) return null

    val candCredits = splitArtistCredits(candidate)
    val channelArtistBonus = artistMatchBonus(targetArtists, candCredits, leadTarget)

    val difference = kotlin.math.abs(target.duration - candidate.duration)
    val strictTol = spotifyTrackDurationTolerance(target.duration)
    val isOfficialMvOverrun = !looseDuration &&
        channelArtistBonus != null &&
        candidate.duration > target.duration &&
        difference > strictTol &&
        difference <= spotifyOfficialMvDurationTolerance(target.duration)
    if (looseDuration) {
        if (candidate.duration < 50_000L) return null
        val ratio = candidate.duration.toDouble() / target.duration.toDouble()
        if (ratio !in 0.55..1.45) return null
    } else if (!isOfficialMvOverrun && difference > strictTol) {
        return null
    }

    var bestTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(rawCandidateTitle)))
    var bestArtistBonus = channelArtistBonus

    // SoundCloud & YouTube uploads often put "Artist - Title", "Title - Artist", "Prefix: Artist - Title"
    // or "Title by Artist" into the title.
    fun considerSplit(artistPart: String, titlePart: String) {
        val partArtists = artistPartVariants(artistPart)
        val partArtistBonus = artistMatchBonus(targetArtists, partArtists, leadTarget)
        val partTitleScore = titleSimilarityScore(cleanTarget, normalized(cleanAudioTitle(titlePart)))
        if (partTitleScore != null && (partArtistBonus != null || bestArtistBonus != null)) {
            val combinedBonus = maxOf(partArtistBonus ?: 0L, bestArtistBonus ?: 0L)
            if (bestTitleScore == null || bestArtistBonus == null || partTitleScore > bestTitleScore!!) {
                bestTitleScore = partTitleScore
                bestArtistBonus = combinedBonus
            }
        }
    }

    val allParts = titleSplit.split(rawCandidateTitle).map { it.trim() }.filter { it.isNotEmpty() }
    if (allParts.size >= 2) {
        considerSplit(artistPart = allParts[0], titlePart = allParts.drop(1).joinToString(" - "))
        considerSplit(artistPart = allParts.last(), titlePart = allParts.dropLast(1).joinToString(" - "))
        for (i in 0 until allParts.lastIndex) {
            considerSplit(artistPart = allParts[i], titlePart = allParts[i + 1])
            considerSplit(artistPart = allParts[i + 1], titlePart = allParts[i])
        }
    }
    val byParts = bySplit.split(rawCandidateTitle, limit = 2)
    if (byParts.size == 2) {
        considerSplit(artistPart = byParts[1], titlePart = byParts[0])
    }

    val titleScore = bestTitleScore ?: return null
    val artistScore = bestArtistBonus ?: return null
    if ((looseDuration || isOfficialMvOverrun) && (titleScore < 9_500L || artistScore < 2_000L)) return null
    // Official artist / Topic channels decisively outrank third-party reuploads, and micro-view
    // third-party uploads (e.g. 47-view osu! replays) are heavily penalized so official MVs win.
    val officialChannelBonus = if (channelArtistBonus != null) 2_500L else 0L
    val views = candidate.playbackCount
    val viewsAdjustment = when {
        views == null -> 0L
        channelArtistBonus != null -> if (views >= 1_000L) minOf(1_500L, ((kotlin.math.log10(views.toDouble()) - 2.0) * 250.0).toLong()) else 0L
        views < 500L -> -3_000L
        views < 2_000L -> -1_000L
        views >= 10_000L -> minOf(1_200L, ((kotlin.math.log10(views.toDouble()) - 3.0) * 300.0).toLong())
        else -> 0L
    }
    val durationPenalty = if (isOfficialMvOverrun) minOf(2_500L, difference / 10L) else difference / 10L
    return maxOf(1L, titleScore + artistScore + officialChannelBonus + viewsAdjustment - durationPenalty)
}

internal fun spotifyMatchRejectReason(target: SoundCloudTrack, candidate: SoundCloudTrack): String {
    if (candidate.duration <= 0) return "no_duration"
    val diff = kotlin.math.abs(target.duration - candidate.duration)
    val tol = spotifyTrackDurationTolerance(target.duration)
    val officialChannel = spotifyHasOfficialChannel(target, candidate)
    val allowedTol = if (officialChannel && candidate.duration > target.duration) {
        spotifyOfficialMvDurationTolerance(target.duration)
    } else tol
    if (diff > allowedTol) return "duration(cand=${candidate.duration / 1000}s, target=${target.duration / 1000}s, tol=${allowedTol / 1000}s)"
    val te = editionTags(target.title.orEmpty())
    val ce = editionTags(candidate.title.orEmpty())
    if (te != ce) return "edition(target=$te, cand=$ce)"
    val candChannelVariants = channelVariantTags(candidate)
    if (candChannelVariants.isNotEmpty()) {
        val allowed = channelVariantTags(target) + te
        val extra = candChannelVariants.filterNot { it in allowed }
        if (extra.isNotEmpty()) return "channel_variant($extra)"
    }
    val tArtists = splitArtistCredits(target)
    val cArtists = splitArtistCredits(candidate)
    val lead = tArtists.firstOrNull() ?: return "no_target_artist"
    if (artistMatchBonus(tArtists, cArtists, lead) == null) {
        fun partHits(part: String) = artistMatchBonus(tArtists, artistPartVariants(part), lead) != null
        val rawCand = candidate.title.orEmpty()
        val allParts = titleSplit.split(rawCand).map { it.trim() }.filter { it.isNotEmpty() }
        val byParts = bySplit.split(rawCand, limit = 2)
        val titleArtistHit = (allParts.size >= 2 && allParts.any(::partHits)) ||
            (byParts.size == 2 && partHits(byParts[1]))
        if (!titleArtistHit) return "artist(target=${tArtists.take(3)}, cand=${cArtists.take(3)})"
    }
    return "title(target='${cleanAudioTitle(target.title.orEmpty())}', cand='${cleanAudioTitle(candidate.title.orEmpty())}')"
}
