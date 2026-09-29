package com.example.myapplication.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Yandex Music's home (`landing3`) as rows for the app's home: each block a row of what can be
 * opened in it — the playlists made for the listener, new releases and playlists, popular ones.
 * The chart is left out, and so are promotions and links to tags, which have nowhere to go.
 */
object YandexLanding {
    const val BLOCKS = "personalplaylists,new-releases,new-playlists,playlists,albums"

    // Rows are carousels: past this many they are only swiped through.
    private const val MAX_ROW = 50

    private val gson = Gson()

    /**
     * The rows, whole: a home block holds only its first few, so new releases, new playlists and
     * the playlists made for the listener are asked for in full; any that fails keeps its few.
     */
    suspend fun rows(service: YandexMusicService): List<YtShelf> =
        parseBlocks(service.landing()).map { (type, shelf) ->
            val full = runCatching {
                when (type.replace("-", "")) {
                    "newreleases" -> newReleases(service)
                    "newplaylists" -> newPlaylists(service)
                    "personalplaylists" -> madeForYou(service)
                    else -> null
                }
            }.onFailure { Log.w("YandexLanding", "Whole \"${shelf.title}\" failed: $it") }.getOrNull()
            if (full != null && full.size > shelf.sets.size) shelf.copy(sets = full) else shelf
        }

    private suspend fun newReleases(service: YandexMusicService): List<SoundCloudPlaylist> {
        val ids = service.newReleases().getAsJsonObject("result")?.getAsJsonArray("newReleases")
            ?.mapNotNull { runCatching { it.asLong }.getOrNull() }?.take(MAX_ROW).orEmpty()
        if (ids.isEmpty()) return emptyList()
        return service.albums(ids.joinToString(",")).getAsJsonArray("result")
            ?.mapNotNull { runCatching { gson.fromJson(it, YandexAlbum::class.java).toSearchAlbum() }.getOrNull() }
            .orEmpty()
    }

    private suspend fun newPlaylists(service: YandexMusicService): List<SoundCloudPlaylist> {
        val ids = service.newPlaylists().getAsJsonObject("result")?.getAsJsonArray("newPlaylists")
            ?.mapNotNull { element ->
                val playlist = element.asObjectOrNull() ?: return@mapNotNull null
                val uid = playlist.string("uid") ?: return@mapNotNull null
                val kind = playlist.string("kind") ?: return@mapNotNull null
                "$uid:$kind"
            }?.take(MAX_ROW).orEmpty()
        if (ids.isEmpty()) return emptyList()
        return service.playlists(ids.joinToString(",")).getAsJsonArray("result")
            ?.mapNotNull { runCatching { gson.fromJson(it, YandexPlaylist::class.java).toSearchPlaylist() }.getOrNull() }
            .orEmpty()
    }

    /** The playlists made for the listener: of the day, Дежавю, Премьера, Тайник and the rest. */
    private suspend fun madeForYou(service: YandexMusicService): List<SoundCloudPlaylist> =
        service.feed().getAsJsonObject("result")?.getAsJsonArray("generatedPlaylists")
            ?.mapNotNull { element ->
                val generated = element.asObjectOrNull() ?: return@mapNotNull null
                if (generated.get("ready")?.takeIf { it.isJsonPrimitive }?.asBoolean == false) return@mapNotNull null
                runCatching { generated.get("data")?.let { gson.fromJson(it, YandexPlaylist::class.java) }?.toSearchPlaylist() }.getOrNull()
            }
            .orEmpty()

    private fun parseBlocks(json: JsonObject): List<Pair<String, YtShelf>> {
        val blocks = json.getAsJsonObject("result")?.getAsJsonArray("blocks") ?: return emptyList()
        return blocks.mapNotNull { element ->
            val block = element.asObjectOrNull() ?: return@mapNotNull null
            val type = block.string("type").orEmpty()
            if (type == "chart") return@mapNotNull null
            val title = block.string("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val tracks = mutableListOf<SoundCloudTrack>()
            val sets = mutableListOf<SoundCloudPlaylist>()
            block.getAsJsonArray("entities")?.forEach { entityElement ->
                val entity = entityElement.asObjectOrNull() ?: return@forEach
                val data = entity.get("data")?.asObjectOrNull() ?: return@forEach
                runCatching {
                    when (entity.string("type")) {
                        // A playlist made for the listener, and whether it is ready yet.
                        "personal-playlist" -> data.get("data")?.let { gson.fromJson(it, YandexPlaylist::class.java) }
                            ?.toSearchPlaylist()?.let(sets::add)
                        "playlist" -> gson.fromJson(data, YandexPlaylist::class.java).toSearchPlaylist()?.let(sets::add)
                        "album" -> sets += gson.fromJson(data, YandexAlbum::class.java).toSearchAlbum()
                        "chart-item" -> data.get("track")?.let { gson.fromJson(it, YandexTrack::class.java) }
                            ?.takeIf { it.available != false }
                            ?.toSoundCloudTrack()?.let(tracks::add)
                        else -> Unit
                    }
                }.onFailure { Log.w("YandexLanding", "Skipped a ${entity.string("type")} in \"$title\": $it") }
            }
            YtShelf(title, tracks.distinctBy { it.id }, sets.distinctBy { it.id })
                .takeIf { it.tracks.isNotEmpty() || it.sets.isNotEmpty() }
                ?.let { type to it }
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
