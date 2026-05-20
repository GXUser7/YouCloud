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

    fun fetchHomeSectionsFlow(clientId: String): Flow<Pair<MixSection?, MixSection?>> = flow {
        val cacheKey = "home_sections"
        val cachedJson = prefs.getString(cacheKey, null)

        var cachedData: Pair<MixSection?, MixSection?>? = null
        if (cachedJson != null) {
            try {
                val type = object : TypeToken<Pair<MixSection?, MixSection?>>() {}.type
                cachedData = gson.fromJson(cachedJson, type)
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
                val cachedMixIds = mutableSetOf<String>()
                cachedData.first?.mixes?.map { it.id }?.let { cachedMixIds.addAll(it) }
                cachedData.second?.mixes?.map { it.id }?.let { cachedMixIds.addAll(it) }

                val networkMixIds = mutableSetOf<String>()
                networkData.first?.mixes?.map { it.id }?.let { networkMixIds.addAll(it) }
                networkData.second?.mixes?.map { it.id }?.let { networkMixIds.addAll(it) }

                val removedMixIds = cachedMixIds - networkMixIds
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

    private suspend fun fetchHomeSectionsNetwork(clientId: String): Pair<MixSection?, MixSection?> {
        val response = service.getMixedSelections(
            clientId = clientId,
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

        return Pair(moodsSection, stationsSection)
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
                ).collection
            }.getOrElse {
                chunk.mapNotNull { trackId ->
                    runCatching { service.getTrack(trackId, clientId) }.getOrNull()
                }
            }
        }
    }

    private fun SoundCloudSystemPlaylist.resolvedTracks(): List<SoundCloudTrack> =
        tracks.orEmpty().filter { track ->
            track.kind == "track" && track.title.isNotBlank()
        }

    fun clearCache() {
        prefs.edit().clear().apply()
    }
}
