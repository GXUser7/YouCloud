package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException

/** Spotify supplies the catalog. The player resolves a matching complete recording using its other sources. */
internal object SpotifyAudio {
    private const val TAG = "SpotifyAudio"

    data class Stream(
        val url: String,
        val extension: String,
        val userAgent: String? = null,
        val chunked: Boolean = false,
        val cacheKey: String? = null,
        val candidateKey: String = "",
        val expectedDurationMs: Long = 0L
    )

    data class ResolvedVariant(
        val source: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val views: Long? = null,
        val matchMode: String = "strict",
        val candidateKey: String = ""
    )

    private const val CACHE_TTL_MS = 5 * 60_000L
    private const val FAILED_TTL_MS = 10 * 60_000L
    private const val VARIANTS_FILE = "spotify_variants.json"

    private val cache = android.util.LruCache<String, Pair<Long, Stream>>(64)
    private val failedCandidates = android.util.LruCache<String, MutableMap<String, Long>>(64)
    private val inFlight = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Stream>>()
    private val resolvedVariants = java.util.concurrent.ConcurrentHashMap<String, ResolvedVariant>()
    @Volatile
    private var variantsLoaded = false
    private val gson = com.google.gson.Gson()
    private val ytSearch = YouTubeMusicClient { null }

    /** Drops resolved Spotify audio links and temporary failure memory so tracks resolve afresh. */
    fun clearCache() {
        synchronized(cache) { cache.evictAll() }
        synchronized(failedCandidates) { failedCandidates.evictAll() }
    }

    private fun ensureVariantsLoaded(context: Context?) {
        if (variantsLoaded || context == null) return
        synchronized(resolvedVariants) {
            if (variantsLoaded) return
            variantsLoaded = true
            runCatching {
                val file = java.io.File(context.applicationContext.filesDir, VARIANTS_FILE)
                if (file.exists()) {
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, ResolvedVariant>>() {}.type
                    val map: Map<String, ResolvedVariant>? = gson.fromJson(file.readText(), type)
                    if (map != null) resolvedVariants.putAll(map)
                }
            }
        }
    }

    private fun rememberVariant(context: Context, spotifyId: String, variant: ResolvedVariant) {
        ensureVariantsLoaded(context)
        resolvedVariants[spotifyId] = variant
        val appContext = context.applicationContext
        kotlin.concurrent.thread(name = "spotify-variant-save", isDaemon = true) {
            runCatching {
                val snapshot = LinkedHashMap<String, ResolvedVariant>()
                for ((k, v) in resolvedVariants.entries.take(500)) snapshot[k] = v
                java.io.File(appContext.filesDir, VARIANTS_FILE).writeText(gson.toJson(snapshot))
            }
        }
    }

    fun resolvedVariant(context: Context?, spotifyId: String): ResolvedVariant? {
        ensureVariantsLoaded(context)
        return resolvedVariants[spotifyId]
    }

    fun resolvedVariantSummary(context: Context?, spotifyId: String): String {
        val v = resolvedVariant(context, spotifyId) ?: return tr("Вариант ещё подбирается…")
        val sec = (v.durationMs / 1000L).coerceAtLeast(0L)
        val dur = "%d:%02d".format(sec / 60L, sec % 60L)
        val mode = when (v.matchMode) {
            "popular" -> " · " + tr("самый популярный на YouTube")
            "loose" -> " · " + tr("свободная длительность")
            else -> ""
        }
        val artistPart = v.artist.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
        return "${v.source}: ${v.title}$artistPart ($dur$mode)"
    }

    fun uri(track: SoundCloudTrack): String? {
        val id = track.spotifyId ?: return null
        return Uri.Builder().scheme("spotify").authority("track").appendPath(id)
            .appendQueryParameter("title", track.title.orEmpty())
            .appendQueryParameter("duration", track.duration.toString())
            .apply {
                (track.artists.orEmpty().ifEmpty { listOfNotNull(track.user) }).mapNotNull { it.username }.take(8)
                    .forEach { appendQueryParameter("artist", it) }
            }
            .build().toString()
    }

    fun fromUri(uri: Uri): SoundCloudTrack? {
        if (uri.scheme != "spotify" || uri.host != "track" || uri.pathSegments.size != 1) return null
        val id = uri.lastPathSegment?.takeIf(String::isSpotifyId) ?: return null
        val ref = SPOTIFY_TRACK + id
        val title = uri.getQueryParameter("title")?.takeIf { it.isNotBlank() && it.length <= 512 } ?: return null
        val duration = uri.getQueryParameter("duration")?.toLongOrNull()?.takeIf { it in 1..86_400_000 } ?: return null
        val artists = uri.getQueryParameters("artist").take(8).filter { it.isNotBlank() && it.length <= 256 }
            .map { SoundCloudUser(username = it) }
        return SoundCloudTrack(spotifyKey(ref), urn = ref, kind = "track", title = title, duration = duration, user = artists.firstOrNull(), artists = artists)
    }

    @UnstableApi
    fun markStreamFailed(spotifyId: String, context: Context? = null, reason: String? = null) {
        val now = android.os.SystemClock.elapsedRealtime()
        val matched = mutableListOf<Stream>()
        val snapshot = synchronized(cache) { cache.snapshot() }
        for ((key, entry) in snapshot) {
            if (key.startsWith("$spotifyId:")) {
                matched += entry.second
                cache.remove(key)
            }
        }
        val failedMap = synchronized(failedCandidates) {
            failedCandidates.get(spotifyId) ?: LinkedHashMap<String, Long>().also { failedCandidates.put(spotifyId, it) }
        }
        for (stream in matched) {
            if (stream.candidateKey.isNotEmpty()) {
                synchronized(failedMap) { failedMap[stream.candidateKey] = now + FAILED_TTL_MS }
            }
            Log.w(TAG, "[$spotifyId] Marked failed ${stream.candidateKey} (${reason ?: "playback error"})")
            val cacheKey = stream.cacheKey
            if (context != null && cacheKey != null) {
                val appContext = context.applicationContext
                kotlin.concurrent.thread(name = "spotify-cache-drop", isDaemon = true) {
                    runCatching { StreamCache.cache(appContext).removeResource(cacheKey) }
                }
            }
        }
    }

    private fun activeFailedKeys(spotifyId: String, now: Long): Set<String> {
        val map = synchronized(failedCandidates) { failedCandidates.get(spotifyId) } ?: return emptySet()
        return synchronized(map) {
            val it = map.entries.iterator()
            while (it.hasNext()) {
                if (it.next().value <= now) it.remove()
            }
            map.keys.toSet()
        }
    }

    internal enum class SourceKind { YOUTUBE, YANDEX, SOUNDCLOUD }

    internal const val TIER_STRICT = 150_000L
    internal const val TIER_LOOSE = 80_000L
    internal const val BONUS_YT_SONG = 26_000L
    internal const val BONUS_YT_VIDEO = 25_000L
    internal const val BONUS_YT_WEB = 24_000L
    internal const val BONUS_YANDEX = 10_000L
    internal const val BONUS_SOUNDCLOUD = 0L

    private data class ScoredCandidate(
        val key: String,
        val kind: SourceKind,
        val track: SoundCloudTrack,
        val score: Long,
        val officialChannel: Boolean = false,
        val sourceLabel: String = "",
        val matchMode: String = "strict"
    )

    suspend fun resolve(context: Context, settings: SettingsRepository, track: SoundCloudTrack, urgent: Boolean): Stream {
        val id = track.spotifyId ?: throw unavailable()
        val ytAuth = settings.ytMusicAuth()
        val yandexToken = settings.yandexTokenValue()
        val clientId = settings.clientId.value
        val key = "$id:${ytAuth?.cookie.orEmpty().hashCode()}:${clientId.isNotEmpty()}:${yandexToken.isNotEmpty()}"
        val now = android.os.SystemClock.elapsedRealtime()
        cache.get(key)?.takeIf { (expires, _) -> expires > now }?.let {
            Log.d(TAG, "[$id] Cache hit -> ${it.second.candidateKey}")
            return it.second
        }
        val existing = inFlight[key]
        if (existing != null) {
            Log.d(TAG, "[$id] Joining in-flight resolve (urgent=$urgent)")
            return existing.await()
        }
        val deferred = kotlinx.coroutines.CompletableDeferred<Stream>()
        val prev = inFlight.putIfAbsent(key, deferred)
        if (prev != null) {
            Log.d(TAG, "[$id] Joining in-flight resolve (urgent=$urgent)")
            return prev.await()
        }
        try {
            val resolved = resolveInternal(context, settings, track, id, key, ytAuth, yandexToken, clientId, now, urgent)
            deferred.complete(resolved)
            return resolved
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(key, deferred)
        }
    }

    private suspend fun resolveInternal(
        context: Context,
        settings: SettingsRepository,
        track: SoundCloudTrack,
        id: String,
        key: String,
        ytAuth: YtAuth?,
        yandexToken: String,
        clientId: String,
        now: Long,
        urgent: Boolean
    ): Stream {
        val primaryQuery = spotifyAudioQuery(track).trim()
        if (primaryQuery.isEmpty() || track.duration <= 0) {
            Log.w(TAG, "[$id] Invalid metadata for resolve: query='$primaryQuery' duration=${track.duration}")
            throw unavailable()
        }

        val startedAt = now
        val artistsText = track.artists.orEmpty().mapNotNull { it.username }
            .ifEmpty { listOfNotNull(track.user?.username) }.joinToString(", ")
        Log.i(
            TAG,
            "[$id] Resolving '${track.title}' by [$artistsText] (${track.duration / 1000}s) urgent=$urgent " +
                "auth={yt=${ytAuth != null}, ya=${yandexToken.isNotBlank()}, scId=${clientId.isNotBlank()}, scAuth=${settings.oauthTokenValue().isNotBlank()}} query='$primaryQuery'"
        )

        val failed = activeFailedKeys(id, now)
        if (failed.isNotEmpty()) Log.d(TAG, "[$id] Skipping failed candidates: $failed")
        var networkError: IOException? = null
        val yt = if (ytAuth == null) ytSearch else YouTubeMusicClient { ytAuth }
        val yandexService = if (yandexToken.isNotBlank()) YandexMusicApi.createService(settings::yandexTokenValue) else null
        val soundCloudService = if (clientId.isNotBlank()) {
            SoundCloudApi.createService(
                oauthTokenProvider = settings::oauthTokenValue,
                clientIdProvider = { settings.clientId.value },
                onClientIdRefreshed = settings::saveClientId
            )
        } else null
        val scResolver = soundCloudService?.let(::SoundCloudPlaybackResolver)

        suspend fun searchCandidates(query: String, label: String): List<ScoredCandidate> = coroutineScope {
            suspend fun <T> safeSearch(source: String, timeoutMs: Long, call: suspend () -> T): T? = try {
                kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { call() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Log.w(TAG, "[$id] $source search IO error for '$query': $e")
                networkError = e
                null
            } catch (e: Exception) {
                Log.w(TAG, "[$id] $source search failed for '$query': ${e.javaClass.simpleName}: ${e.message}")
                null
            }

            val ytJob = async { safeSearch("YTM", 6_500L) { yt.searchAudioCandidates(query) } }
            val ytWebJob = async {
                safeSearch("YTWeb", 5_000L) { YouTubeWeb.search(query, ytAuth).tracks }
                    .orEmpty()
                    .filter { it.youTubeVideoId != null && it.liveVideoId == null && it.duration > 0 }
            }
            val yandexJob = async {
                if (yandexService == null) emptyList()
                else safeSearch("Yandex", 4_500L) { yandexService.searchTracks(query).result?.tracks?.results.orEmpty() }.orEmpty()
            }
            val scJob = async {
                if (soundCloudService == null) emptyList()
                else safeSearch("SoundCloud", 4_500L) { soundCloudService.searchTracks(query, settings.clientId.value, limit = 15).collection }.orEmpty()
            }

            val strictPool = mutableListOf<ScoredCandidate>()
            val loosePool = mutableListOf<ScoredCandidate>()
            val popularPool = mutableListOf<ScoredCandidate>()
            val ytRes = ytJob.await()
            val ytSongsRaw = ytRes?.songs.orEmpty()
            val ytVideosRaw = ytRes?.videos.orEmpty()
            var ytSongsMatched = 0
            var ytMatched = 0
            for (song in ytSongsRaw) {
                val vid = song.youTubeVideoId ?: continue
                val cKey = "yt:$vid"
                if (cKey in failed) continue
                val official = spotifyHasOfficialChannel(track, song)
                val score = spotifyMatchScore(track, song)
                if (score != null) {
                    ytSongsMatched++
                    ytMatched++
                    strictPool += ScoredCandidate(cKey, SourceKind.YOUTUBE, song, TIER_STRICT + score + BONUS_YT_SONG, official, "YouTube Music", "strict")
                } else {
                    val loose = spotifyLooseDurationMatchScore(track, song)
                    if (loose != null) {
                        loosePool += ScoredCandidate(cKey, SourceKind.YOUTUBE, song, TIER_LOOSE + loose + BONUS_YT_SONG, official, "YouTube Music", "loose")
                    } else {
                        spotifyPopularYouTubeFallbackScore(track, song)?.let { pop ->
                            popularPool += ScoredCandidate(cKey, SourceKind.YOUTUBE, song, pop + 1_000L, official, "YouTube Music", "popular")
                        }
                    }
                }
            }
            for (video in ytVideosRaw) {
                val vid = video.youTubeVideoId ?: continue
                val cKey = "yt:$vid"
                if (cKey in failed) continue
                val official = spotifyHasOfficialChannel(track, video)
                val score = spotifyMatchScore(track, video)
                if (score != null) {
                    ytMatched++
                    strictPool += ScoredCandidate(cKey, SourceKind.YOUTUBE, video, TIER_STRICT + score + BONUS_YT_VIDEO, official, "YouTube Music", "strict")
                } else {
                    val loose = spotifyLooseDurationMatchScore(track, video)
                    if (loose != null) {
                        loosePool += ScoredCandidate(cKey, SourceKind.YOUTUBE, video, TIER_LOOSE + loose + BONUS_YT_VIDEO, official, "YouTube Music", "loose")
                    } else {
                        spotifyPopularYouTubeFallbackScore(track, video)?.let { pop ->
                            popularPool += ScoredCandidate(cKey, SourceKind.YOUTUBE, video, pop, official, "YouTube", "popular")
                        }
                    }
                }
            }

            val ytWebTracks = ytWebJob.await()
            val ytWebCount = ytWebTracks.size
            for (webTrack in ytWebTracks) {
                val vid = webTrack.youTubeVideoId ?: continue
                val cKey = "yt:$vid"
                if (cKey in failed) continue
                val official = spotifyHasOfficialChannel(track, webTrack)
                val score = spotifyMatchScore(track, webTrack)
                if (score != null) {
                    if (ytSongsMatched == 0) {
                        ytMatched++
                        strictPool += ScoredCandidate(cKey, SourceKind.YOUTUBE, webTrack, TIER_STRICT + score + BONUS_YT_WEB, official, "YouTube", "strict")
                    } else {
                        loosePool += ScoredCandidate(cKey, SourceKind.YOUTUBE, webTrack, TIER_LOOSE + score + BONUS_YT_WEB, official, "YouTube", "strict")
                    }
                } else {
                    val loose = spotifyLooseDurationMatchScore(track, webTrack)
                    if (loose != null) {
                        loosePool += ScoredCandidate(cKey, SourceKind.YOUTUBE, webTrack, TIER_LOOSE + loose + BONUS_YT_WEB, official, "YouTube", "loose")
                    } else {
                        spotifyPopularYouTubeFallbackScore(track, webTrack)?.let { pop ->
                            popularPool += ScoredCandidate(cKey, SourceKind.YOUTUBE, webTrack, pop, official, "YouTube", "popular")
                        }
                    }
                }
            }

            val yaRaw = yandexJob.await()
            var yaMatched = 0
            for (yaTrack in yaRaw) {
                val mapped = yaTrack.toSoundCloudTrack()
                val matchTrack = mapped.copy(title = yaTrack.fullTitle())
                val raw = mapped.urn?.removePrefix("yandex:track:")?.substringBefore(':') ?: continue
                val cKey = "ya:$raw"
                if (cKey in failed) continue
                val score = spotifyMatchScore(track, matchTrack)
                if (score != null) {
                    yaMatched++
                    strictPool += ScoredCandidate(cKey, SourceKind.YANDEX, mapped, TIER_STRICT + score + BONUS_YANDEX, sourceLabel = "Яндекс Музыка", matchMode = "strict")
                } else {
                    spotifyLooseDurationMatchScore(track, matchTrack)?.let { loose ->
                        loosePool += ScoredCandidate(cKey, SourceKind.YANDEX, mapped, TIER_LOOSE + loose + BONUS_YANDEX, sourceLabel = "Яндекс Музыка", matchMode = "loose")
                    }
                }
            }

            val scRaw = scJob.await()
            val scPlayable = scRaw.filter(::soundCloudPlaysWhole)
            val scProgressive = scPlayable.filter { sc ->
                sc.media?.transcodings.orEmpty().any { isWholeStream(it) && it.format?.protocol == "progressive" }
            }
            var scMatched = 0
            var scHlsOnlyMatched = 0
            for (scTrack in scPlayable) {
                val hasProgressive = scTrack in scProgressive
                val cKey = "sc:${scTrack.id}"
                if (cKey in failed) continue
                val score = spotifyMatchScore(track, scTrack)
                if (score != null) {
                    if (!hasProgressive) {
                        scHlsOnlyMatched++
                        continue
                    }
                    scMatched++
                    strictPool += ScoredCandidate(cKey, SourceKind.SOUNDCLOUD, scTrack, TIER_STRICT + score + BONUS_SOUNDCLOUD, sourceLabel = "SoundCloud", matchMode = "strict")
                } else if (hasProgressive) {
                    spotifyLooseDurationMatchScore(track, scTrack)?.let { loose ->
                        loosePool += ScoredCandidate(cKey, SourceKind.SOUNDCLOUD, scTrack, TIER_LOOSE + loose + BONUS_SOUNDCLOUD, sourceLabel = "SoundCloud", matchMode = "loose")
                    }
                }
            }

            // Keep all three tiers in descending score order so if a strict candidate fails
            // extraction or post-extraction Gaming metadata validation, we seamlessly fall through
            // to loose-duration and popular YouTube candidates instead of skipping the track.
            val combined = buildList {
                addAll(strictPool.sortedByDescending { it.score })
                addAll(loosePool.sortedByDescending { it.score })
                addAll(popularPool.sortedByDescending { it.score })
            }.distinctBy { it.key }

            Log.i(
                TAG,
                "[$id] Search($label) raw={ytmS=${ytSongsRaw.size}, ytmV=${ytVideosRaw.size}, ytw=$ytWebCount, ya=${yaRaw.size}, sc=${scRaw.size}/${scProgressive.size}prog} " +
                    "matched={yt=$ytMatched, ya=$yaMatched, sc=$scMatched, scHlsOnly=$scHlsOnlyMatched, loose=${loosePool.size}, popular=${popularPool.size}}"
            )
            if (strictPool.isEmpty()) {
                val sampleRejects = buildList {
                    ytSongsRaw.firstOrNull()?.let { add("ytmS:${spotifyMatchRejectReason(track, it)}") }
                    ytVideosRaw.firstOrNull()?.let { add("ytmV:${spotifyMatchRejectReason(track, it)}") }
                    yaRaw.firstOrNull()?.let { ya ->
                        add("ya:${spotifyMatchRejectReason(track, ya.toSoundCloudTrack().copy(title = ya.fullTitle()))}")
                    }
                    scPlayable.firstOrNull()?.let { add("sc:${spotifyMatchRejectReason(track, it)}") }
                }
                if (sampleRejects.isNotEmpty()) {
                    Log.i(TAG, "[$id] Sample rejects($label): ${sampleRejects.joinToString(" | ")}")
                }
            }
            combined
        }

        var ranked = searchCandidates(primaryQuery, "primary")
        val hasPrimaryStrictOrLoose = ranked.any { it.matchMode != "popular" }
        if (!hasPrimaryStrictOrLoose) {
            spotifyAudioFallbackQuery(track)?.let { fallbackQuery ->
                val fallbackRanked = searchCandidates(fallbackQuery, "fallback")
                if (fallbackRanked.isNotEmpty()) {
                    val fallbackStrictOrLoose = fallbackRanked.filter { it.matchMode != "popular" }
                    val fallbackPopular = fallbackRanked.filter { it.matchMode == "popular" }
                    ranked = (fallbackStrictOrLoose + ranked + fallbackPopular).distinctBy { it.key }
                }
            }
        }

        suspend fun <T> tryResolve(source: String, call: suspend () -> T): T? = try {
            call()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "[$id] $source stream IO error: $e")
            networkError = e
            null
        } catch (e: Exception) {
            Log.w(TAG, "[$id] $source stream error: ${e.javaClass.simpleName}: ${e.message}")
            null
        }

        // Cap extraction attempts per source so YouTube leads when available, while Yandex/SoundCloud remain reachable.
        var ytAttempts = 0
        var ytRejected = 0
        var yaAttempts = 0
        var scAttempts = 0
        for (cand in ranked) {
            when (cand.kind) {
                SourceKind.YOUTUBE -> {
                    if (ytAttempts >= 5 || ytAttempts + ytRejected >= 8) continue
                    val vid = cand.track.youTubeVideoId ?: continue
                    val stream = tryResolve(cand.key) {
                        withContext(Dispatchers.IO) { YouTubeStreams.resolve(context, vid, ytAuth, urgent = { urgent }) }
                    }
                    if (stream == null) {
                        ytAttempts++
                        Log.w(TAG, "[$id] ${cand.key} returned null stream (score=${cand.score})")
                        continue
                    }
                    val views = stream.viewCount ?: cand.track.playbackCount
                    if (spotifyYouTubeMetadataRejected(track, cand.officialChannel, stream.category, stream.tags, views)) {
                        ytRejected++
                        Log.w(
                            TAG,
                            "[$id] Rejected ${cand.key}: category=${stream.category}, views=$views, tags=${stream.tags.take(5)}"
                        )
                        continue
                    }
                    ytAttempts++
                    val extension = if (stream.mimeType?.startsWith("audio/webm") == true) "webm" else "m4a"
                    val itag = Uri.parse(stream.url).getQueryParameter("itag") ?: "0"
                    val cacheKey = "${StreamCache.KEY_PREFIX}spotify:$id:yt_${vid}_$itag"
                    Log.i(
                        TAG,
                        "[$id] Resolved via ${cand.key} (score=${cand.score}, ext=$extension, clen=${stream.contentLength}) " +
                            "in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
                    )
                    rememberVariant(
                        context,
                        id,
                        ResolvedVariant(
                            source = cand.sourceLabel.ifBlank { "YouTube" },
                            title = cand.track.title.orEmpty(),
                            artist = cand.track.artistNames.orEmpty(),
                            durationMs = cand.track.duration,
                            views = views,
                            matchMode = cand.matchMode,
                            candidateKey = cand.key
                        )
                    )
                    return remember(
                        key,
                        Stream(
                            url = stream.url,
                            extension = extension,
                            userAgent = stream.userAgent,
                            chunked = stream.contentLength > 0,
                            cacheKey = cacheKey,
                            candidateKey = cand.key,
                            expectedDurationMs = cand.track.duration
                        )
                    )
                }
                SourceKind.YANDEX -> {
                    if (yaAttempts >= 3) continue
                    yaAttempts++
                    val raw = cand.track.urn?.removePrefix("yandex:track:")?.substringBefore(':') ?: continue
                    val url = tryResolve(cand.key) { YandexMusicApi.resolveTrackStream(raw, yandexToken) }
                    if (url == null) {
                        Log.w(TAG, "[$id] ${cand.key} returned null stream (score=${cand.score})")
                        continue
                    }
                    val cacheKey = "${StreamCache.KEY_PREFIX}spotify:$id:ya_$raw"
                    Log.i(
                        TAG,
                        "[$id] Resolved via ${cand.key} (score=${cand.score}) in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
                    )
                    rememberVariant(
                        context,
                        id,
                        ResolvedVariant(
                            source = "Яндекс Музыка",
                            title = cand.track.title.orEmpty(),
                            artist = cand.track.artistNames.orEmpty(),
                            durationMs = cand.track.duration,
                            matchMode = cand.matchMode,
                            candidateKey = cand.key
                        )
                    )
                    return remember(
                        key,
                        Stream(
                            url = url,
                            extension = "mp3",
                            cacheKey = cacheKey,
                            candidateKey = cand.key,
                            expectedDurationMs = cand.track.duration
                        )
                    )
                }
                SourceKind.SOUNDCLOUD -> {
                    if (scAttempts >= 3) continue
                    scAttempts++
                    val resolver = scResolver ?: continue
                    // Playback uses DefaultMediaSourceFactory with a non-HLS spotify:// MediaItem: progressive only avoids M3U8 parser failures.
                    val url = tryResolve(cand.key) {
                        resolver.resolve(cand.track, settings.clientId.value, progressiveFirst = true, progressiveOnly = true)
                    }
                    if (url == null) {
                        Log.w(TAG, "[$id] ${cand.key} returned null progressive stream (score=${cand.score})")
                        continue
                    }
                    val cacheKey = "${StreamCache.KEY_PREFIX}spotify:$id:sc_${cand.track.id}"
                    Log.i(
                        TAG,
                        "[$id] Resolved via ${cand.key} (score=${cand.score}) in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
                    )
                    rememberVariant(
                        context,
                        id,
                        ResolvedVariant(
                            source = "SoundCloud",
                            title = cand.track.title.orEmpty(),
                            artist = cand.track.artistNames.orEmpty(),
                            durationMs = cand.track.duration,
                            views = cand.track.playbackCount,
                            matchMode = cand.matchMode,
                            candidateKey = cand.key
                        )
                    )
                    return remember(
                        key,
                        Stream(
                            url = url,
                            extension = "mp3",
                            cacheKey = cacheKey,
                            candidateKey = cand.key,
                            expectedDurationMs = cand.track.duration
                        )
                    )
                }
            }
        }

        val error = networkError
        Log.w(
            TAG,
            "[$id] FAILED to resolve '${track.title}' (candidates=${ranked.size}, netErr=${error != null}) " +
                "in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
        )
        if (error != null) throw IOException(tr("Не удалось найти источник звука для трека Spotify."), error)
        throw unavailable()
    }

    private fun latestCachedStream(spotifyId: String): Stream? {
        val now = android.os.SystemClock.elapsedRealtime()
        val prefix = "$spotifyId:"
        val snapshot = synchronized(cache) { cache.snapshot() }
        return snapshot.entries
            .filter { (key, entry) -> key.startsWith(prefix) && entry.first > now }
            .maxByOrNull { (_, entry) -> entry.first }
            ?.value?.second
    }

    fun cachedMimeType(spotifyId: String): String? = when (latestCachedStream(spotifyId)?.extension?.lowercase()) {
        "webm" -> "audio/webm"
        "m4a", "mp4", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        else -> null
    }

    fun cachedYouTubeVideoId(spotifyId: String): String? =
        latestCachedStream(spotifyId)?.candidateKey?.takeIf { it.startsWith("yt:") }?.removePrefix("yt:")?.takeIf { it.isNotBlank() }

    fun cachedYandexTrackId(spotifyId: String): String? =
        latestCachedStream(spotifyId)?.candidateKey?.takeIf { it.startsWith("ya:") }?.removePrefix("ya:")?.takeIf { it.isNotBlank() }

    private fun remember(key: String, stream: Stream): Stream {
        cache.put(key, (android.os.SystemClock.elapsedRealtime() + CACHE_TTL_MS) to stream)
        return stream
    }

    private fun unavailable() = TrackUnavailableException(tr("Не удалось найти источник звука для трека Spotify."))
}
