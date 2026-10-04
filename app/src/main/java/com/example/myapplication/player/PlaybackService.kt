package com.example.myapplication.player

import com.example.myapplication.i18n.tr
import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import com.example.myapplication.MainActivity
import com.example.myapplication.data.OfflineMusicStore
import com.example.myapplication.data.StreamCache
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.IOException

// MediaLibraryService rather than MediaSessionService: Android Auto needs a browsable content
// tree, not just a transport session. MediaLibraryService extends MediaSessionService, so the
// app's own MediaController keeps connecting exactly as before.
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibraryService.MediaLibrarySession? = null

    // The equalizer on the main player's audio session, and on the crossfade's helper's: by who
    // plays there. It sets the volume of all a session plays as one, so the two are kept apart.
    private val equalizers = mutableMapOf<String, Equalizer>()
    private lateinit var preferences: SharedPreferences

    // One at a time, so a prefetch never holds up the track that is actually starting.
    private val youTubePrefetch = java.util.concurrent.Executors.newSingleThreadExecutor()

    // Fetches the next tracks in the queue into the stream cache while one plays.
    private var prefetcher: StreamPrefetcher? = null

    // The reverb of a track's effects, in the player's own sound chain.
    private val reverbProcessor = ReverbAudioProcessor()

    // The crossfade between tracks and the sleep timer's fade.
    private var fades: PlaybackFades? = null

    // Every track heard, for "Итоги".
    private var playLog: PlayLog.Tracker? = null
    private var nowPlaying: com.example.myapplication.data.social.NowPlayingPublisher? = null

    // Follows what the app says of the track playing, for the buttons beside play and skip.
    private val scope = kotlinx.coroutines.MainScope()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) return@OnSharedPreferenceChangeListener
        // The track playing had its effects changed, in the player on screen.
        if (key.startsWith(com.example.myapplication.data.TrackFx.KEY_PREFIX)) {
            (mediaSession?.player as? ExoPlayer)?.let(::applyTrackFx)
            return@OnSharedPreferenceChangeListener
        }
        if (key == KEY_CROSSFADE_SECONDS) {
            fades?.ensureTicking()
            return@OnSharedPreferenceChangeListener
        }
        for (eq in equalizers.values) {
            try {
                if (key == "equalizer_enabled") {
                    val enabled = preferences.getBoolean(key, false)
                    eq.enabled = enabled
                } else if (key.startsWith("eq_band_")) {
                    val band = key.removePrefix("eq_band_").toIntOrNull()
                    if (band != null) {
                        val level = preferences.getInt(key, 0)
                        val minLevel = eq.bandLevelRange[0]
                        val maxLevel = eq.bandLevelRange[1]
                        val coercedLevel = level.coerceIn(minLevel.toInt(), maxLevel.toInt())
                        eq.setBandLevel(band.toShort(), coercedLevel.toShort())
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error updating equalizer parameter", e)
            }
        }
    }

    @androidx.media3.common.util.UnstableApi
    override fun onCreate() {
        super.onCreate()

        preferences = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(prefListener)

        val offlineStore = OfflineMusicStore.getInstance(this)
        if (!preferences.getBoolean(KEY_UNVERSIONED_STREAMS_DROPPED, false)) {
            Thread {
                runCatching { StreamCache.dropUnversionedKeys(this) }
                    .onSuccess { Log.d("PlaybackService", "Dropped $it tracks kept without their file") }
                    .onFailure { Log.w("PlaybackService", "Couldn't drop tracks kept without their file", it) }
                preferences.edit().putBoolean(KEY_UNVERSIONED_STREAMS_DROPPED, true).apply()
            }.start()
        }
        if (!preferences.getBoolean(KEY_STREAM_LEFTOVERS_DROPPED, false)) {
            Thread {
                runCatching { offlineStore.dropStreamedLeftovers() }
                    .onFailure { Log.w("PlaybackService", "Couldn't drop streamed leftovers", it) }
                preferences.edit().putBoolean(KEY_STREAM_LEFTOVERS_DROPPED, true).apply()
            }.start()
        }
        val baseFactory = offlineStore.playbackDataSourceFactory
        val resolver = object : androidx.media3.datasource.ResolvingDataSource.Resolver {
                override fun resolveDataSpec(dataSpec: androidx.media3.datasource.DataSpec): androidx.media3.datasource.DataSpec {
                    val uri = dataSpec.uri
                    if (uri.scheme == "soundcloud") {
                        val trackId = uri.lastPathSegment?.toLongOrNull()
                        if (trackId != null) {
                            val resolvedUri = resolveSoundCloudTrack(trackId)
                            if (resolvedUri != null) {
                                return dataSpec.buildUpon().setUri(android.net.Uri.parse(resolvedUri)).build()
                            }
                        }
                    } else if (uri.scheme == "ytmusic") {
                        val videoId = uri.lastPathSegment
                        if (videoId != null) {
                            val local = downloadedYouTubeTrack(videoId)
                            if (local != null) {
                                return dataSpec.buildUpon().setUri(android.net.Uri.parse(local)).build()
                            }
                            // Heard or fetched ahead whole: played from the cache at once, without
                            // the seconds yt-dlp takes to find it, and without the network.
                            StreamCache.cachedYouTubeKey(this@PlaybackService, videoId)?.let { key ->
                                return dataSpec.buildUpon().setKey(key).build()
                            }
                            val audio = com.example.myapplication.data.YouTubeStreams.resolve(this@PlaybackService, videoId, youTubeAuth())
                            if (audio != null) {
                                // Keyed by the video and its format, not the URL: a fresh URL for
                                // the same track still finds what the cache holds, and another
                                // format is never stitched onto it. Fetched as the client the URL
                                // was issued to.
                                val itag = android.net.Uri.parse(audio.url).getQueryParameter("itag") ?: "0"
                                return dataSpec.buildUpon()
                                    .setUri(android.net.Uri.parse(audio.url))
                                    .setKey(StreamCache.youTubeKey(videoId, itag))
                                    .setHttpRequestHeaders(dataSpec.httpRequestHeaders + ("User-Agent" to audio.userAgent))
                                    .build()
                            }
                        }
                    } else if (uri.scheme == "ytlive") {
                        // A broadcast: its HLS playlist, fetched as the client it was issued to.
                        val videoId = uri.lastPathSegment
                        val live = videoId?.let {
                            com.example.myapplication.data.YouTubeStreams.resolveLive(this@PlaybackService, it, youTubeAuth())
                        }
                        if (live != null) {
                            return dataSpec.buildUpon()
                                .setUri(android.net.Uri.parse(live.url))
                                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + ("User-Agent" to live.userAgent))
                                .build()
                        }
                    } else if (uri.scheme == "yandex") {
                        val trackId = uri.lastPathSegment
                        if (trackId != null) {
                            val rawId = trackId.substringBefore(':')
                            val downloaded = downloadedYandexTrack(trackId)
                            if (downloaded != null) {
                                return dataSpec.buildUpon().setUri(android.net.Uri.parse(downloaded)).build()
                            }
                            StreamCache.cachedYandexKey(this@PlaybackService, rawId)?.let { key ->
                                return dataSpec.buildUpon().setKey(key).build()
                            }
                            val resolved = resolveYandexTrack(trackId)
                            if (resolved != null) {
                                // Kept under the track and its file, not its link, which is signed
                                // anew each time; a file on the phone under nothing.
                                return dataSpec.buildUpon()
                                    .setUri(android.net.Uri.parse(resolved.url))
                                    .apply { resolved.variant?.let { setKey(StreamCache.yandexKey(rawId, it)) } }
                                    .build()
                            }
                        }
                    }
                    return dataSpec
                }
            }
        val resolvingFactory = androidx.media3.datasource.ResolvingDataSource.Factory(baseFactory, resolver)

        val player = ExoPlayer.Builder(this)
            // The reverb of a track's effects, worked out in the player: Android's own, an effect
            // on the output, is left idle on some phones.
            .setRenderersFactory(object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink =
                    androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setAudioProcessorChain(androidx.media3.exoplayer.audio.DefaultAudioSink.DefaultAudioProcessorChain(reverbProcessor))
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .build()
            })
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            // "Previous" five seconds into a track starts it over instead.
            .setMaxSeekToPreviousPositionMs(5_000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            // The phone kept awake, and its Wi-Fi, while it plays: with the screen off it slept
            // between the sound's buffers, and what ran meanwhile stalled — the next track being
            // found, telling friends what plays (they saw a friend come and go while the music went
            // on). As Media3 asks of an app that streams.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Only ever heard: a track that is a video (one imported from the phone) has its picture
        // shown by the player on screen, and decoding it here too was work for nothing.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    initEqualizer(EQ_MAIN, audioSessionId)
                }
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                prefetchNextYouTubeTrack(player)
                applyTrackFx(player)
                updateButtons()
            }
        })
        prefetcher = StreamPrefetcher(this, player, resolver).also(player::addListener)
        playLog = PlayLog.Tracker(this, player).also(player::addListener)
        // Friends see what plays (when signed in, and only if the account lets them).
        nowPlaying = com.example.myapplication.data.social.NowPlayingPublisher(this, player).also(player::addListener)
        // A new run of the player, empty until the app hands it its queue: the widget hears so.
        publishToWidget(player)
        // The home screen widget follows what plays.
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED
                    )
                ) {
                    publishToWidget(player)
                }
            }
        })
        fades = PlaybackFades(
            context = this,
            main = player,
            mediaSourceFactory = DefaultMediaSourceFactory(resolvingFactory),
            // Listening together, a guest's crossfade is the host's, so the two hear the same.
            crossfadeMs = { (SessionBridge.crossfadeOverride.value ?: preferences.getInt(KEY_CROSSFADE_SECONDS, 0)) * 1000L },
            soundOf = ::trackSoundOf,
            onHelperSession = { initEqualizer(EQ_HELPER, it) }
        )
        scope.launch { SessionBridge.crossfadeOverride.collect { fades?.ensureTicking() } }

        // A stream that breaks off over a slow connection (a VPN's, say) is tried again, a little
        // later each time, rather than the player stopping at an error until touched. Preparing
        // again resolves the link anew, so an expired one (a 403) is mended too.
        player.addListener(object : Player.Listener {
            private var retries = 0
            private val handler = android.os.Handler(android.os.Looper.getMainLooper())

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (error.errorCode !in RETRIED_ERRORS || retries >= RETRY_DELAYS_MS.size) {
                    retries = 0
                    return
                }
                val wait = RETRY_DELAYS_MS[retries++]
                val itemId = player.currentMediaItem?.mediaId
                Log.w("PlaybackService", "Playback broke off (${error.errorCodeName}), again in $wait ms")
                handler.postDelayed({
                    // Not if the listener has moved on, or it came back by itself.
                    if (player.playerError != null && player.currentMediaItem?.mediaId == itemId) player.prepare()
                }, wait)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) retries = 0
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                retries = 0
            }
        })

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibraryService.MediaLibrarySession.Builder(this, player, librarySessionCallback)
            .setSessionActivity(pendingIntent)
            .build()
        scope.launch {
            kotlinx.coroutines.flow.combine(SessionBridge.liked, SessionBridge.canDislike) { _, _ -> }.collect { updateButtons() }
        }
    }

    /**
     * "Нравится" (filled once liked) and, while the wave plays, "Не нравится": beside play and
     * skip in the notification, on the lock screen and in the car.
     */
    private fun buttons(): List<androidx.media3.session.CommandButton> {
        val trackId = mediaSession?.player?.currentMediaItem?.mediaId?.toLongOrNull()
        val liked = SessionBridge.liked.value?.takeIf { it.first == trackId }?.second == true
        val like = androidx.media3.session.CommandButton.Builder(
            if (liked) androidx.media3.session.CommandButton.ICON_HEART_FILLED else androidx.media3.session.CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName(if (liked) tr("Убрать из любимых") else tr("Нравится"))
            .setSessionCommand(LIKE_COMMAND)
            .setSlots(androidx.media3.session.CommandButton.SLOT_OVERFLOW)
            .build()
        if (!SessionBridge.canDislike.value) return listOf(like)
        val dislike = androidx.media3.session.CommandButton.Builder(androidx.media3.session.CommandButton.ICON_THUMB_DOWN_UNFILLED)
            .setDisplayName(tr("Не нравится"))
            .setSessionCommand(DISLIKE_COMMAND)
            .setSlots(androidx.media3.session.CommandButton.SLOT_OVERFLOW)
            .build()
        return listOf(like, dislike)
    }

    private fun updateButtons() {
        mediaSession?.setMediaButtonPreferences(buttons())
    }

    private fun publishToWidget(player: Player) {
        val metadata = player.mediaMetadata
        com.example.myapplication.widget.NowPlayingState.publish(
            this,
            com.example.myapplication.widget.NowPlayingState.Snapshot(
                title = metadata.title?.toString(),
                artist = metadata.artist?.toString(),
                artwork = metadata.artworkUri?.toString(),
                // Pause shown while it loads, as the player on screen shows it.
                playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                active = player.mediaItemCount > 0
            )
        )
    }

    /**
     * Serves the browse tree to Android Auto and resolves the items it hands back for playback.
     * Browse requests answer immediately from disk — the car's media browser treats a slow reply
     * as a broken app.
     */
    private val librarySessionCallback = object : MediaLibraryService.MediaLibrarySession.Callback {

        // Every controller may press "Нравится" and "Не нравится": the notification's, the
        // system's media controls, the car.
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(LIKE_COMMAND)
                    .add(DISLIKE_COMMAND)
                    .build()
            )
            .setMediaButtonPreferences(buttons())
            .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: androidx.media3.session.SessionCommand,
            args: Bundle
        ): ListenableFuture<androidx.media3.session.SessionResult> {
            val trackId = session.player.currentMediaItem?.mediaId?.toLongOrNull()
            if (trackId != null) {
                when (customCommand.customAction) {
                    LIKE_COMMAND.customAction -> SessionBridge.ask(SessionBridge.Action.Like(trackId))
                    DISLIKE_COMMAND.customAction -> SessionBridge.ask(SessionBridge.Action.Dislike(trackId))
                }
            }
            return Futures.immediateFuture(androidx.media3.session.SessionResult(androidx.media3.session.SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<androidx.media3.common.MediaItem>> {
            val extras = Bundle().apply {
                // Categories as a grid, tracks as a list — the layout Auto users expect.
                putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 2)
                putInt("android.media.browse.CONTENT_STYLE_PLAYABLE_HINT", 1)
            }
            val rootParams = MediaLibraryService.LibraryParams.Builder().setExtras(extras).build()
            return Futures.immediateFuture(LibraryResult.ofItem(AutoLibrary.rootItem(), rootParams))
        }

        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<androidx.media3.common.MediaItem>>> {
            val children = AutoLibrary.children(this@PlaybackService, parentId)
            // Remember the whole folder, not just this page, so a tap can be expanded into it.
            lastBrowsedFolder = parentId to children.map { it.mediaId }

            // Honour the requested page. Returning the entire library on every request would grow
            // the binder payload with the collection and eventually fail outright on a big one.
            // Longs throughout: Auto passes Int.MAX_VALUE as pageSize when it wants everything.
            val slice = if (pageSize <= 0) {
                children
            } else {
                val from = (page.toLong() * pageSize).coerceIn(0L, children.size.toLong()).toInt()
                val to = (from.toLong() + pageSize).coerceAtMost(children.size.toLong()).toInt()
                children.subList(from, to)
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(slice), params)
            )
        }

        override fun onGetItem(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<androidx.media3.common.MediaItem>> {
            val track = AutoLibrary.findTrack(this@PlaybackService, mediaId)
                ?: return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            val item = AutoLibrary.resolve(
                this@PlaybackService,
                androidx.media3.common.MediaItem.Builder().setMediaId(track.id.toString()).build()
            )
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<androidx.media3.common.MediaItem>
        ): ListenableFuture<MutableList<androidx.media3.common.MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { AutoLibrary.resolve(this@PlaybackService, it) }.toMutableList()
            )

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<androidx.media3.common.MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            // Auto sends only the row that was tapped, which would strand the car on a one-track
            // queue with nothing to skip to. Expand it back to the folder it came from; browsing is
            // sequential, so the folder listed most recently is the one holding the tap.
            val tapped = mediaItems.singleOrNull()
            val folder = lastBrowsedFolder?.second
            if (tapped != null && folder != null) {
                val index = folder.indexOf(tapped.mediaId)
                if (index >= 0) {
                    val expanded = folder.map { id ->
                        AutoLibrary.resolve(
                            this@PlaybackService,
                            androidx.media3.common.MediaItem.Builder().setMediaId(id).build()
                        )
                    }
                    return Futures.immediateFuture(
                        MediaSession.MediaItemsWithStartPosition(expanded, index, startPositionMs)
                    )
                }
            }
            val resolved = mediaItems.map { AutoLibrary.resolve(this@PlaybackService, it) }
            val safeIndex = if (startIndex in resolved.indices) startIndex else 0
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(resolved, safeIndex, startPositionMs)
            )
        }
    }

    /** Last folder handed to a browser, as (parent id, child media ids). See [onSetMediaItems]. */
    private var lastBrowsedFolder: Pair<String, List<String>>? = null

    // Lazy-initialized shared instances (#13: avoid re-creating on each track resolve)
    private val lazyFavoritesRepository by lazy { com.example.myapplication.data.FavoritesRepository(this) }
    private val lazyPlaylistsRepository by lazy { com.example.myapplication.data.PlaylistsRepository(this) }
    private val lazyOkHttpClient by lazy { okhttp3.OkHttpClient() }

    // Cached regex patterns (#34: avoid recompilation on each call)
    companion object {
        private const val EQ_MAIN = "main"
        private const val EQ_HELPER = "helper"

        // What a broken-off stream waits before each try again, and which errors are worth one:
        // the network's, and the player tripping over itself (a decoder taken away, the audio
        // output gone, a fault inside it), which leaves it stopped for good otherwise — and,
        // listening together, everyone with it.
        private val RETRY_DELAYS_MS = longArrayOf(2_000, 5_000, 10_000)
        private val RETRIED_ERRORS = setOf(
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            androidx.media3.common.PlaybackException.ERROR_CODE_TIMEOUT,
            androidx.media3.common.PlaybackException.ERROR_CODE_UNSPECIFIED,
            androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            androidx.media3.common.PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
        )

        // The crossfade's length in seconds, 0 for none; set in the app's settings.
        const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"

        private val LIKE_COMMAND = androidx.media3.session.SessionCommand("com.example.myapplication.LIKE", Bundle.EMPTY)
        private val DISLIKE_COMMAND = androidx.media3.session.SessionCommand("com.example.myapplication.DISLIKE", Bundle.EMPTY)

        // Set once what playback had left in the downloads' cache has been cleared out.
        private const val KEY_STREAM_LEFTOVERS_DROPPED = "stream_leftovers_dropped"
        // Set once what the stream cache kept under a track alone, without its file, is gone.
        private const val KEY_UNVERSIONED_STREAMS_DROPPED = "unversioned_streams_dropped"

        private val XML_TAG_REGEXES = mutableMapOf<String, Regex>()
        private fun xmlTagRegex(tag: String): Regex {
            return XML_TAG_REGEXES.getOrPut(tag) { "<$tag>(.*?)</$tag>".toRegex() }
        }
    }

    /** The YouTube Music session the app signed in with; see SettingsRepository. */
    private fun youTubeAuth(): com.example.myapplication.data.YtAuth? {
        val cookie = preferences.getString("ytmusic_cookie", null)?.takeIf { it.isNotBlank() } ?: return null
        return com.example.myapplication.data.YtAuth(
            cookie = cookie,
            visitorData = preferences.getString("ytmusic_visitor_data", null),
            authUser = preferences.getString("ytmusic_auth_user", null) ?: "0"
        )
    }

    /**
     * Finding a YouTube track's audio takes yt-dlp several seconds. For the next track in the queue
     * that happens while this one plays, so skipping to it starts at once.
     */
    private fun prefetchNextYouTubeTrack(player: Player) {
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: return
        val uri = player.getMediaItemAt(next).localConfiguration?.uri ?: return
        if (uri.scheme != "ytmusic") return
        val videoId = uri.lastPathSegment ?: return
        if (downloadedYouTubeTrack(videoId) != null) return
        val auth = youTubeAuth()
        youTubePrefetch.execute {
            // Fetched ahead: what plays now, and its video, go first.
            com.example.myapplication.data.YouTubeStreams.resolve(this, videoId, auth, urgent = { false })
        }
    }

    /** A YouTube track saved in "Скачанное" or with a liked playlist, as a `file://` URL. */
    private fun downloadedYouTubeTrack(videoId: String): String? {
        val trackId = com.example.myapplication.data.youTubeTrackId(videoId)
        val saved = lazyFavoritesRepository.get(trackId)
            ?.takeIf { it.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED }
            ?.streamUrl
            ?: lazyPlaylistsRepository.playlists.value
                .flatMap { it.tracks }
                .firstOrNull { it.id == trackId && it.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED }
                ?.streamUrl
        val path = saved?.takeIf { it.isNotBlank() } ?: return null
        return if (path.startsWith("/")) "file://$path" else path
    }

    private fun resolveSoundCloudTrack(trackId: Long): String? {
        val fav = lazyFavoritesRepository.get(trackId)
        if (fav != null && fav.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED && !fav.streamUrl.isNullOrBlank()) {
            val localPath = fav.streamUrl
            val finalUrl = if (localPath.startsWith("/") && !localPath.startsWith("file://")) {
                "file://$localPath"
            } else {
                localPath
            }
            Log.d("PlaybackService", "Playing track from cache: $trackId, url: $finalUrl")
            return finalUrl
        }
 
        val playlistTrack = lazyPlaylistsRepository.playlists.value
            .flatMap { it.tracks }
            .firstOrNull { it.id == trackId && it.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED && !it.streamUrl.isNullOrBlank() }
        if (playlistTrack != null) {
            val localPath = playlistTrack.streamUrl!!
            val finalUrl = if (localPath.startsWith("/") && !localPath.startsWith("file://")) {
                "file://$localPath"
            } else {
                localPath
            }
            Log.d("PlaybackService", "Playing track from playlist cache: $trackId, url: $finalUrl")
            return finalUrl
        }

        val clientId = preferences.getString("soundcloud_client_id", "") ?: ""
        val oauthToken = preferences.getString("soundcloud_oauth_token", "") ?: ""

        return kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(20_000L) {
            try {
                // The service refreshes and retries a rotated client_id on its own, reading the
                // current one per request — so playback no longer dies just because the stored id
                // went stale between the app launching and the track being resolved.
                val service = com.example.myapplication.data.SoundCloudApi.createService(
                    oauthTokenProvider = { preferences.getString("soundcloud_oauth_token", "") ?: oauthToken },
                    clientIdProvider = { preferences.getString("soundcloud_client_id", "") ?: "" },
                    onClientIdRefreshed = { fresh ->
                        preferences.edit().putString("soundcloud_client_id", fresh).apply()
                    },
                    // Playback from Android Auto or the notification runs without the app's
                    // screen, so it has to renew an expired session by itself.
                    onSessionExpired = { staleToken ->
                        val renewed = com.example.myapplication.data.SoundCloudSessionRefresher.refresh(
                            context = this@PlaybackService,
                            staleToken = staleToken,
                            clientId = preferences.getString("soundcloud_client_id", "") ?: ""
                        )
                        if (renewed != null) {
                            preferences.edit()
                                .putString("soundcloud_client_id", renewed.clientId)
                                .putString("soundcloud_oauth_token", renewed.oauthToken)
                                .putString("soundcloud_user_id", renewed.userId.toString())
                                .commit()
                        }
                        renewed != null
                    }
                )
                val playbackResolver = com.example.myapplication.data.SoundCloudPlaybackResolver(service)
                val currentClientId = preferences.getString("soundcloud_client_id", "") ?: clientId
                val track = service.getTrack(trackId, currentClientId)
                playbackResolver.resolve(track, preferences.getString("soundcloud_client_id", "") ?: currentClientId)
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error resolving track online: $trackId", e)
                null
            }
            }
        }
    }

    /** A Yandex track downloaded to the phone, as a `file://` URL. */
    private fun downloadedYandexTrack(trackId: String): String? {
        val rawTrackId = trackId.substringBefore(":")
        val numericId = rawTrackId.toLongOrNull()
        val generatedId = if (numericId != null) {
            -(numericId + 1_000_000_000L)
        } else {
            val hash = rawTrackId.hashCode().toLong()
            -(if (hash == Int.MIN_VALUE.toLong()) Int.MAX_VALUE.toLong() else kotlin.math.abs(hash)) - 1_000_000_000L
        }
        val fav = lazyFavoritesRepository.get(generatedId)
            ?.takeIf { it.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED }
            ?: return null
        val localPath = fav.streamUrl?.takeIf { it.isNotBlank() } ?: return null
        return if (localPath.startsWith("/")) "file://$localPath" else localPath
    }

    /** A Yandex track's address, and which of its files it is ([variant], "mp3-320"); none for one on the phone. */
    private class YandexStream(val url: String, val variant: String?)

    private fun resolveYandexTrack(trackId: String): YandexStream? {
        val rawTrackId = trackId.substringBefore(":")
        // Match ID generation from YandexMusicModels.kt (#6)
        val numericId = rawTrackId.toLongOrNull()
        val generatedId = if (numericId != null) {
            -(numericId + 1_000_000_000L)
        } else {
            val hash = rawTrackId.hashCode().toLong()
            -(if (hash == Int.MIN_VALUE.toLong()) Int.MAX_VALUE.toLong() else kotlin.math.abs(hash)) - 1_000_000_000L
        }
        val fav = lazyFavoritesRepository.get(generatedId)
        if (fav != null && fav.downloadState == com.example.myapplication.data.DownloadState.DOWNLOADED && !fav.streamUrl.isNullOrBlank()) {
            val localPath = fav.streamUrl
            val finalUrl = if (localPath.startsWith("/") && !localPath.startsWith("file://")) {
                "file://$localPath"
            } else {
                localPath
            }
            Log.d("PlaybackService", "Playing Yandex track from cache: $trackId, url: $finalUrl")
            return YandexStream(finalUrl, null)
        }

        val token = preferences.getString("yandex_music_token", "") ?: ""
        return kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeout(15_000L) {
            try {
                val service = com.example.myapplication.data.YandexMusicApi.createService { token }
                val response = service.getDownloadInfo(trackId)
                // The best MP3, whatever order Yandex lists them in: "the first one" was one file
                // on one call and another on the next, and the two were stitched together.
                val items = response.result.orEmpty()
                val bestItem = items.filter { it.codec == "mp3" }.maxByOrNull { it.bitrateInKbps }
                    ?: items.maxByOrNull { it.bitrateInKbps }
                    ?: return@withTimeout null
                
                val client = lazyOkHttpClient
                val request = okhttp3.Request.Builder()
                    .url(bestItem.downloadInfoUrl)
                    .header("Authorization", "OAuth $token")
                    .build()
                
                val xmlString = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Yandex HTTP ${response.code}")
                    response.body?.string() ?: ""
                }
                
                if (xmlString.isEmpty()) return@withTimeout null
                
                val host = xmlTagRegex("host").find(xmlString)?.groupValues?.get(1).orEmpty()
                val path = xmlTagRegex("path").find(xmlString)?.groupValues?.get(1).orEmpty()
                val ts = xmlTagRegex("ts").find(xmlString)?.groupValues?.get(1).orEmpty()
                val s = xmlTagRegex("s").find(xmlString)?.groupValues?.get(1).orEmpty()
                
                if (host.isNotEmpty() && path.isNotEmpty() && ts.isNotEmpty() && s.isNotEmpty()) {
                    YandexStream(
                        com.example.myapplication.data.YandexMusicApi.generateDirectLink(host, path, ts, s),
                        "${bestItem.codec}-${bestItem.bitrateInKbps}"
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error resolving Yandex track: $trackId", e)
                null
            }
            }
        }
    }

    /**
     * The track playing's own effects ([com.example.myapplication.data.TrackFx]): its speed, with
     * or without its pitch, and its reverb, worked out in the player ([ReverbAudioProcessor]).
     */
    private fun applyTrackFx(player: ExoPlayer) {
        val sound = player.currentMediaItem?.let(::trackSoundOf) ?: TrackSound(androidx.media3.common.PlaybackParameters.DEFAULT, 0)
        player.playbackParameters = sound.parameters
        reverbProcessor.amount = sound.reverb
    }

    /** How [item] is played by its own effects: also the crossfade's, for the track coming in. */
    private fun trackSoundOf(item: androidx.media3.common.MediaItem): TrackSound {
        val fx = item.mediaId.toLongOrNull()?.let {
            com.example.myapplication.data.TrackFx.decode(preferences.getString(com.example.myapplication.data.TrackFx.KEY_PREFIX + it, null))
        } ?: com.example.myapplication.data.TrackFx()
        // A broadcast goes at its own pace: sped up it ran into its live edge and stalled waiting
        // for more, slowed down it fell further and further behind.
        val live = item.localConfiguration?.uri?.scheme == "ytlive"
        val speed = if (live) 1f else fx.speed
        return TrackSound(androidx.media3.common.PlaybackParameters(speed, if (fx.keepPitch) 1f else speed), fx.reverb)
    }

    private fun initEqualizer(owner: String, audioSessionId: Int) {
        try {
            equalizers.remove(owner)?.release()
            val eq = Equalizer(0, audioSessionId)
            val enabled = preferences.getBoolean("equalizer_enabled", false)
            eq.enabled = enabled
            
            val numBands = eq.numberOfBands.toInt()
            for (i in 0 until numBands) {
                val level = preferences.getInt("eq_band_$i", 0)
                val minLevel = eq.bandLevelRange[0]
                val maxLevel = eq.bandLevelRange[1]
                val coercedLevel = level.coerceIn(minLevel.toInt(), maxLevel.toInt())
                eq.setBandLevel(i.toShort(), coercedLevel.toShort())
            }
            equalizers[owner] = eq
        } catch (e: Exception) {
            Log.e("PlaybackService", "Failed to init Equalizer", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // Nothing queued any more: the widget offers the wave and "Моя музыка" instead.
        com.example.myapplication.widget.NowPlayingState.publish(this, com.example.myapplication.widget.NowPlayingState.Snapshot())
        scope.cancel()
        playLog?.release()
        playLog = null
        nowPlaying?.release()
        nowPlaying = null
        fades?.release()
        fades = null
        youTubePrefetch.shutdownNow()
        prefetcher?.release()
        prefetcher = null
        preferences.unregisterOnSharedPreferenceChangeListener(prefListener)
        equalizers.values.forEach { it.release() }
        equalizers.clear()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
