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
    obj("artists")?.items("items") ?: obj("firstArtist")?.items("items") ?: items("artists")

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

internal fun spotifyHomeRows(data: JsonObject): List<YtShelf> =
    data.obj("home")?.obj("sectionContainer")?.obj("sections")?.items("items").orEmpty().mapNotNull { section ->
        val title = section.obj("data")?.obj("title") ?: return@mapNotNull null
        val name = title.string("transformedLabel") ?: title.string("text") ?: return@mapNotNull null
        val cards = section.obj("sectionItems")?.items("items").orEmpty().mapNotNull { it.obj("content") }
        val sets = cards.mapNotNull { card -> spotifySet(card, isAlbum = false) ?: spotifySet(card, isAlbum = true) }
        val tracks = cards.mapNotNull(::spotifyTrack)
        val artists = cards.filter { it.spotifyData().serviceId(it, SPOTIFY_ARTIST) != null }.mapNotNull(::spotifyArtist)
        YtShelf(name, tracks.distinctBy { it.id }, sets.distinctBy { it.id }, artists.distinctBy { it.id })
            .takeIf { it.tracks.isNotEmpty() || it.sets.isNotEmpty() || it.artists.isNotEmpty() }
    }

private val titleNotes = Regex(
    """\s*[\[(](?:feat\.?|ft\.?|featuring)\s+[^)\]]+[)\]]|\s+-\s+(?:\d{4}\s+)?remaster(?:ed)?(?:\s+\d{4})?$""",
    RegexOption.IGNORE_CASE
)
private val editionWords = Regex(
    """\b(?:live|remix|cover|karaoke|instrumental|acoustic|sped\s+up|slowed|nightcore|demo|edit|reverb)\b""",
    RegexOption.IGNORE_CASE
)
private val marks = Regex("\\p{M}+")
private val separators = Regex("[^\\p{L}\\p{N}]+")

private fun normalized(text: String): String =
    Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        .replace(marks, "").replace(separators, " ").trim()

internal fun spotifyAudioQuery(track: SoundCloudTrack): String {
    val artist = track.artists?.firstOrNull()?.username ?: track.user?.username
    return listOfNotNull(artist, track.title?.replace(titleNotes, "")).joinToString(" ")
}

/** A download or play must have the same recording, not merely a similar title. */
internal fun spotifyMatchScore(target: SoundCloudTrack, candidate: SoundCloudTrack): Long? {
    if (candidate.duration <= 0 || target.duration <= 0) return null
    val difference = kotlin.math.abs(target.duration - candidate.duration)
    if (difference > minOf(8_000L, target.duration / 12)) return null
    val title = normalized(target.title.orEmpty().replace(titleNotes, ""))
    val other = normalized(candidate.title.orEmpty().replace(titleNotes, ""))
    if (title.isEmpty() || title != other) return null
    val targetEdition = editionWords.findAll(target.title.orEmpty()).map { it.value.lowercase(Locale.ROOT) }.toSet()
    val otherEdition = editionWords.findAll(candidate.title.orEmpty()).map { it.value.lowercase(Locale.ROOT) }.toSet()
    if (targetEdition != otherEdition) return null
    fun artistNames(track: SoundCloudTrack): List<String> =
        (track.artists.orEmpty() + listOfNotNull(track.user)).mapNotNull { it.username }
            .map(::normalized).filter(String::isNotEmpty).distinct()
    val lead = artistNames(target).firstOrNull() ?: return null
    val credits = artistNames(candidate)
    if (credits.none { it == lead || it.removeSuffix(" topic").removeSuffix(" vevo") == lead }) return null
    return 8_000L - difference
}
