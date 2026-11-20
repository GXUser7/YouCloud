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

private fun JsonObject.cover(): String? =
    items("images").firstNotNullOfOrNull { it.string("url")?.takeIf { url -> url.startsWith("https://") } }

internal fun spotifyArtist(raw: JsonObject): SoundCloudUser? {
    val id = raw.string("id")?.takeIf(String::isSpotifyId) ?: return null
    val name = raw.string("name") ?: return null
    val ref = SPOTIFY_ARTIST + id
    return SoundCloudUser(
        id = spotifyKey(ref),
        username = name,
        avatarUrl = raw.cover(),
        permalinkUrl = ref
    )
}

internal fun spotifyTrack(raw: JsonObject, album: JsonObject? = raw.obj("album")): SoundCloudTrack? {
    if (raw.flag("is_local") == true || raw.flag("is_playable") == false || raw.obj("restrictions") != null) return null
    if (raw.string("type")?.let { it != "track" } == true) return null
    val id = raw.string("id")?.takeIf(String::isSpotifyId) ?: return null
    val title = raw.string("name") ?: return null
    val duration = raw.number("duration_ms")?.takeIf { it > 0 } ?: return null
    val artists = raw.items("artists").mapNotNull(::spotifyArtist).distinctBy { it.permalinkUrl }
    val ref = SPOTIFY_TRACK + id
    return SoundCloudTrack(
        id = spotifyKey(ref),
        urn = ref,
        kind = "track",
        title = title,
        artworkUrl = album?.cover(),
        permalinkUrl = "https://open.spotify.com/track/$id",
        user = artists.firstOrNull(),
        artists = artists,
        duration = duration,
        streamable = true
    )
}

internal fun spotifySet(raw: JsonObject, isAlbum: Boolean): SoundCloudPlaylist? {
    val id = raw.string("id")?.takeIf(String::isSpotifyId) ?: return null
    val name = raw.string("name") ?: return null
    val ref = (if (isAlbum) SPOTIFY_ALBUM else SPOTIFY_PLAYLIST) + id
    val count = if (isAlbum) raw.number("total_tracks") else (raw.obj("items") ?: raw.obj("tracks"))?.number("total")
    return SoundCloudPlaylist(
        id = spotifyKey(ref),
        title = name,
        trackCount = count?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
        artworkUrl = raw.cover(),
        permalinkUrl = ref,
        user = if (isAlbum) raw.items("artists").firstNotNullOfOrNull(::spotifyArtist)
        else raw.obj("owner")?.let { SoundCloudUser(username = it.string("display_name") ?: it.string("id")) },
        isAlbum = isAlbum,
        setType = raw.string("album_type"),
        releaseDate = raw.string("release_date")
    )
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
