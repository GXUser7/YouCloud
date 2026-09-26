package com.example.myapplication.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit

class SoundCloudMixesRepository(
    private val service: SoundCloudService,
    context: Context
) {
    private val prefs = context.getSharedPreferences("soundcloud_mixes_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun fetchHomeSectionsFlow(clientId: String): Flow<HomeSections> = flow {
        // v2: the cache used to be a pair of sections, before trending joined them.
        val cacheKey = "home_sections_v2"
        val cachedJson = prefs.getString(cacheKey, null)

        var cachedData: HomeSections? = null
        if (cachedJson != null) {
            try {
                cachedData = gson.fromJson(cachedJson, HomeSections::class.java)
                if (cachedData != null) {
                    emit(cachedData)
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        try {
            val networkData = fetchHomeSectionsNetwork(clientId)

            // Clean up cached track data for mixes that are no longer present
            if (cachedData != null) {
                val removedMixIds = cachedData.mixIds - networkData.mixIds
                if (removedMixIds.isNotEmpty()) {
                    val editor = prefs.edit()
                    for (mixId in removedMixIds) {
                        editor.remove("mix_tracks_$mixId")
                        editor.remove("mix_tracks_${mixId}_time")
                    }
                    editor.apply()
                }
            }

            prefs.edit()
                .putString(cacheKey, gson.toJson(networkData))
                .putLong("${cacheKey}_time", System.currentTimeMillis())
                .apply()

            if (networkData != cachedData) {
                emit(networkData)
            }
        } catch (e: Exception) {
            if (cachedData == null) {
                throw e
            }
        }
    }

    private suspend fun fetchHomeSectionsNetwork(clientId: String): HomeSections {
        val response = service.getMixedSelections(
            clientId = clientId,
            // Trending sits fourth in the list; ten leaves room if SoundCloud reorders it.
            limit = 10,
            offset = 0,
            linkedPartitioning = 1,
            appVersion = SoundCloudApi.APP_VERSION,
            appLocale = "en"
        )

        val moodsSelection = response.collection.firstOrNull { it.trackingFeatureName == "your-moods" }
        val stationsSelection = response.collection.firstOrNull { 
            it.title?.contains("Discover with Station", ignoreCase = true) == true || 
            it.trackingFeatureName == "stations" ||
            it.trackingFeatureName == "discover-with-station"
        }

        val moodsSection = moodsSelection?.let { selection ->
            val mixes = selection.items?.collection?.mapNotNull(SoundCloudMixPlaylist::toMix).orEmpty()
            if (mixes.isNotEmpty()) MixSection(selection.title?.takeIf { it.isNotBlank() } ?: "Your Mixes", mixes) else null
        }

        val stationsSection = stationsSelection?.let { selection ->
            val stations = selection.items?.collection?.mapNotNull(SoundCloudMixPlaylist::toMix).orEmpty()
            if (stations.isNotEmpty()) MixSection(selection.title?.takeIf { it.isNotBlank() } ?: "Discover with Station", stations) else null
        }

        // Each genre is a system playlist carrying its chart as track stubs, the same shape as a
        // mix, so it opens through [loadMixTracks] like one.
        val trendingSection = response.collection
            .firstOrNull { it.trackingFeatureName == "trending-by-genre-playlists" }
            ?.let { selection ->
                val genres = selection.items?.collection?.mapNotNull(SoundCloudMixPlaylist::toMix).orEmpty()
                if (genres.isNotEmpty()) MixSection(selection.title?.takeIf { it.isNotBlank() } ?: "Trending by genre", genres) else null
            }

        return HomeSections(moods = moodsSection, stations = stationsSection, trending = trendingSection)
    }

    suspend fun loadMixTracks(mix: SoundCloudMix, clientId: String): List<SoundCloudTrack> {
        val cacheKey = "mix_tracks_${mix.id}"
        val cachedJson = prefs.getString(cacheKey, null)
        val cacheTime = prefs.getLong("${cacheKey}_time", 0L)
        val isCacheValid = (System.currentTimeMillis() - cacheTime) < TimeUnit.HOURS.toMillis(24)

        if (cachedJson != null && isCacheValid) {
            try {
                val type = object : TypeToken<List<SoundCloudTrack>>() {}.type
                val cachedData: List<SoundCloudTrack> = gson.fromJson(cachedJson, type)
                if (cachedData.isNotEmpty()) return cachedData
            } catch (e: Exception) {}
        }

        val networkData = fetchMixTracksNetwork(mix, clientId)
        if (networkData.isNotEmpty()) {
            prefs.edit()
                .putString(cacheKey, gson.toJson(networkData))
                .putLong("${cacheKey}_time", System.currentTimeMillis())
                .apply()
        }
        return networkData
    }

    private suspend fun fetchMixTracksNetwork(mix: SoundCloudMix, clientId: String): List<SoundCloudTrack> {
        val playlistTracks = runCatching {
            service.getSystemPlaylist(
                permalink = mix.permalink,
                clientId = clientId,
                appVersion = SoundCloudApi.APP_VERSION,
                appLocale = "en"
            ).resolvedTracks()
        }.getOrDefault(emptyList())

        if (playlistTracks.isNotEmpty()) {
            return playlistTracks
        }

        return loadTracksByIds(mix.trackIds, clientId)
    }

    private suspend fun loadTracksByIds(ids: List<Long>, clientId: String): List<SoundCloudTrack> {
        if (ids.isEmpty()) return emptyList()

        return ids.chunked(20).flatMap { chunk ->
            runCatching {
                service.getTracksByIds(
                    ids = chunk.joinToString(","),
                    clientId = clientId
                )
            }.getOrElse {
                chunk.mapNotNull { trackId ->
                    runCatching { service.getTrack(trackId, clientId) }.getOrNull()
                }
            }
        }
    }

    private fun SoundCloudSystemPlaylist.resolvedTracks(): List<SoundCloudTrack> =
        tracks.orEmpty().filter { track ->
            track.kind == "track" && !track.title.isNullOrBlank()
        }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
