package com.example.myapplication.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Yandex Music's home (`landing3`) as rows for the app's home: each block a row of what can be
 * played or opened in it — the playlists made for the listener and other playlists, albums, the
 * chart's tracks. Promotions and links to tags have nowhere to go in the app and are left out.
 */
object YandexLanding {
    const val BLOCKS = "personalplaylists,new-releases,new-playlists,chart,playlists,albums"

    private val gson = Gson()

    fun parse(json: JsonObject): List<YtShelf> {
        val blocks = json.getAsJsonObject("result")?.getAsJsonArray("blocks") ?: return emptyList()
        return blocks.mapNotNull { element ->
            val block = element.asObjectOrNull() ?: return@mapNotNull null
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
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
    private fun JsonObject.string(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString
}
