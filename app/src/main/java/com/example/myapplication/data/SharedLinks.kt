package com.example.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * What a link from one of the three services points at, as YouCloud opens it: a track plays, an
 * album or playlist opens as a set, an artist or channel opens its page.
 */
sealed interface SharedLink {
    /** A Yandex Music track; [albumId] when the link names the album it is from. */
    data class YandexTrack(val trackId: String, val albumId: String?) : SharedLink
    data class YandexAlbum(val albumId: Long) : SharedLink
    data class YandexArtist(val artistId: String) : SharedLink
    /** Someone's playlist by its owner (a login or a uid) and number: `users/<owner>/playlists/<kind>`. */
    data class YandexPlaylist(val owner: String, val kind: String) : SharedLink
    /** A playlist by the id the newer links carry: `playlists/<uuid>`. */
    data class YandexPlaylistUuid(val uuid: String) : SharedLink

    /** A YouTube or YouTube Music video or song, a broadcast too. */
    data class YouTubeVideo(val videoId: String) : SharedLink
    /** An album or playlist: [browseId] opens its page, [playlistId] plays it (a mix has only that). */
    data class YouTubeSet(val browseId: String, val playlistId: String) : SharedLink
    data class YouTubeChannel(val channelId: String) : SharedLink

    /** Anything on soundcloud.com — a track, a set, a profile: SoundCloud's own `resolve` says which. */
    data class SoundCloud(val url: String) : SharedLink

    /** A short link (on.soundcloud.com, ya.cc…) that has to be followed to see where it goes. */
    data class Short(val url: String) : SharedLink
}

object SharedLinks {
    private val URL = Regex("""https?://[^\s<>"'«»]+""", RegexOption.IGNORE_CASE)

    // Pages of soundcloud.com that are no one's profile.
    private val SOUNDCLOUD_RESERVED = setOf(
        "discover", "search", "you", "stream", "upload", "settings", "charts", "pages", "terms-of-use",
        "mobile", "popular", "tags", "stations", "messages", "notifications", "people", "signin", "logout",
        "feed", "home", "jobs", "imprint", "creators", "go", "connect", "artists"
    )

    // Short links worth following: the services' own, and the shorteners they share through.
    private val SHORTENERS = setOf("on.soundcloud.com", "soundcloud.app.goo.gl", "ya.cc", "clck.ru")

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * The first link in [text]: shared from an app it often comes inside words ("Слушайте … в
     * Яндекс Музыке: https://…"), and with punctuation stuck to its end.
     */
    fun findUrl(text: String): String? =
        URL.find(text)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']')

    /**
     * The page of an album or playlist on its service, as a link to it shared would be — what
     * [parse] opens again. Null for a set of the phone's own.
     */
    fun linkOf(set: SoundCloudPlaylist): String? {
        val ref = set.permalinkUrl ?: return null
        YouTubeMusicClient.parseSetRef(ref)?.let { (browseId, playlistId) ->
            return when {
                browseId.isNotBlank() -> "https://music.youtube.com/browse/$browseId"
                playlistId.isNotBlank() -> "https://music.youtube.com/playlist?list=$playlistId"
                else -> null
            }
        }
        if (ref.startsWith("yandex:album:")) return "https://music.yandex.ru/album/${ref.removePrefix("yandex:album:")}"
        if (ref.startsWith("yandex:playlist:")) {
            val parts = ref.removePrefix("yandex:playlist:").split(':')
            return if (parts.size == 2) "https://music.yandex.ru/users/${parts[0]}/playlists/${parts[1]}" else null
        }
        return ref.takeIf { it.startsWith("https://soundcloud.com/") }
    }

    /** An artist's page on their service, likewise. */
    fun linkOf(artist: SoundCloudUser): String? {
        val ref = artist.permalinkUrl ?: return null
        if (ref.startsWith(YT_ARTIST_REF)) return "https://music.youtube.com/channel/${ref.removePrefix(YT_ARTIST_REF)}"
        if (ref.startsWith("yandex:artist:")) return "https://music.yandex.ru/artist/${ref.removePrefix("yandex:artist:")}"
        return ref.takeIf { it.startsWith("https://soundcloud.com/") }
    }

    /** Which service [link] is a page of, as the app names them ("youtube", "yandex", "soundcloud"). */
    fun serviceOf(link: String?): String? = when {
        link == null -> null
        "youtube.com" in link || "youtu.be" in link -> "youtube"
        "music.yandex." in link -> "yandex"
        "soundcloud.com" in link -> "soundcloud"
        else -> null
    }

    /** What [url] points at, or null for a link of no service YouCloud knows. */
    fun parse(url: String): SharedLink? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }.map(::decode)
        val query = parseQuery(uri.rawQuery)
        return when {
            host in SHORTENERS -> SharedLink.Short(url)
            host.startsWith("music.yandex.") -> parseYandex(path)
            host == "youtu.be" -> path.firstOrNull()?.takeIf(::isVideoId)?.let(SharedLink::YouTubeVideo)
            host == "youtube.com" || host == "music.youtube.com" -> parseYouTube(path, query)
            host == "soundcloud.com" -> parseSoundCloud(path)
            else -> null
        }
    }

    /**
     * Where the short link [url] leads: followed through its redirects, as a browser would. Null
     * when it leads nowhere, or off to something no service of ours.
     */
    suspend fun follow(url: String): SharedLink? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).get().header("User-Agent", "Mozilla/5.0 (Android) YouCloud").build()
        val landed = runCatching { http.newCall(request).execute().use { it.request.url.toString() } }.getOrNull()
        landed?.let(::parse)?.takeIf { it !is SharedLink.Short }
    }

    private fun parseYandex(path: List<String>): SharedLink? {
        fun at(i: Int) = path.getOrNull(i)
        return when (at(0)) {
            "album" -> when {
                at(2) == "track" && at(3)?.all(Char::isDigit) == true -> SharedLink.YandexTrack(at(3)!!, at(1))
                else -> at(1)?.toLongOrNull()?.let(SharedLink::YandexAlbum)
            }
            "track" -> at(1)?.takeIf { it.all(Char::isDigit) }?.let { SharedLink.YandexTrack(it, null) }
            "artist" -> at(1)?.takeIf { it.all(Char::isDigit) }?.let(SharedLink::YandexArtist)
            "users" -> if (at(2) == "playlists" && at(1) != null && at(3) != null) {
                SharedLink.YandexPlaylist(owner = at(1)!!, kind = at(3)!!)
            } else {
                null
            }
            "playlists" -> at(1)?.let(SharedLink::YandexPlaylistUuid)
            else -> null
        }
    }

    private fun parseYouTube(path: List<String>, query: Map<String, String>): SharedLink? {
        val first = path.firstOrNull()
        return when {
            first == null -> null
            first == "watch" -> query["v"]?.takeIf(::isVideoId)?.let(SharedLink::YouTubeVideo)
                ?: query["list"]?.let(::youTubeList)
            first == "live" || first == "shorts" || first == "embed" ->
                path.getOrNull(1)?.takeIf(::isVideoId)?.let(SharedLink::YouTubeVideo)
            first == "playlist" -> query["list"]?.let(::youTubeList)
            first == "channel" -> path.getOrNull(1)?.takeIf { it.startsWith("UC") }?.let(SharedLink::YouTubeChannel)
            first == "browse" -> {
                val id = path.getOrNull(1) ?: return null
                when {
                    id.startsWith("UC") -> SharedLink.YouTubeChannel(id)
                    id.startsWith("MPRE") -> SharedLink.YouTubeSet(id, "")
                    id.startsWith("VL") -> SharedLink.YouTubeSet(id, id.removePrefix("VL"))
                    else -> null
                }
            }
            else -> null
        }
    }

    /**
     * A playlist id as a set: a radio or mix (`RD…`) has no page of its own, only its queue; an
     * album (`OLAK5uy_…`) and any other playlist have one under `VL<id>`.
     */
    private fun youTubeList(id: String): SharedLink? = when {
        id.isBlank() -> null
        id.startsWith("RD") -> SharedLink.YouTubeSet("", id)
        else -> SharedLink.YouTubeSet("VL$id", id)
    }

    private fun parseSoundCloud(path: List<String>): SharedLink? {
        val first = path.firstOrNull() ?: return null
        if (first in SOUNDCLOUD_RESERVED) return null
        // Without the query (`?si=…`, `?utm_…`): `resolve` wants the page itself.
        return SharedLink.SoundCloud("https://soundcloud.com/" + path.joinToString("/"))
    }

    private fun isVideoId(id: String) = id.length == 11 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private fun parseQuery(raw: String?): Map<String, String> =
        raw.orEmpty().split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            decode(key) to decode(part.substringAfter('=', ""))
        }.toMap()

    private fun decode(s: String): String = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
}
