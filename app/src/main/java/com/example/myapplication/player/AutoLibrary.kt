package com.example.myapplication.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.example.myapplication.data.ArtworkProvider
import com.example.myapplication.data.DownloadState
import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.FavoritesRepository
import com.example.myapplication.data.PlaylistsRepository
import java.io.File

/**
 * The browse tree Android Auto (and any other MediaBrowser client) sees.
 *
 * Everything here is built from what is already on disk — liked tracks, downloads and local
 * playlists — so the car gets a usable library without waiting on the network, and downloaded
 * tracks keep playing with no connection at all. Mixes and stations are deliberately absent:
 * they need live API calls, and a browse request that blocks on the network shows up as an
 * empty or spinning list in the car.
 */
object AutoLibrary {

    const val ROOT_ID = "auto_root"
    const val NODE_DOWNLOADS = "auto_downloads"
    const val NODE_FAVORITES = "auto_favorites"
    const val NODE_PLAYLISTS = "auto_playlists"
    private const val PLAYLIST_PREFIX = "auto_playlist:"

    fun rootItem(): MediaItem = browsable(ROOT_ID, "YouCloud")

    /**
     * Children of [parentId], or an empty list for an id we do not recognise.
     *
     * Repositories are constructed per call on purpose. They snapshot SharedPreferences at
     * construction, and the service holds its own instances separate from the ones the UI mutates,
     * so a cached instance would keep serving the library as it looked when playback first
     * started — a track downloaded since would never appear in the car.
     */
    fun children(context: Context, parentId: String): List<MediaItem> = when {
        parentId == ROOT_ID -> listOf(
            browsable(NODE_DOWNLOADS, "Скачанное"),
            browsable(NODE_FAVORITES, "Любимое"),
            browsable(NODE_PLAYLISTS, "Плейлисты")
        )

        parentId == NODE_DOWNLOADS -> downloadedTracks(context).map(::playable)

        parentId == NODE_FAVORITES -> FavoritesRepository(context).favorites.value.map(::playable)

        parentId == NODE_PLAYLISTS -> PlaylistsRepository(context).playlists.value.map { playlist ->
            browsable(PLAYLIST_PREFIX + playlist.id, playlist.name, playlist.artworkUrl)
        }

        parentId.startsWith(PLAYLIST_PREFIX) -> {
            val id = parentId.removePrefix(PLAYLIST_PREFIX)
            PlaylistsRepository(context).playlists.value
                .firstOrNull { it.id == id }
                ?.tracks
                ?.map(::playable)
                .orEmpty()
        }

        else -> emptyList()
    }

    /** The saved track behind a browse id, looked up wherever it lives. */
    fun findTrack(context: Context, mediaId: String): FavoriteTrack? {
        val id = mediaId.toLongOrNull() ?: return null
        FavoritesRepository(context).favorites.value.firstOrNull { it.id == id }?.let { return it }
        return PlaylistsRepository(context).playlists.value
            .flatMap { it.tracks }
            .firstOrNull { it.id == id }
    }

    /**
     * Turns a browse item — which arrives carrying only a media id — into something playable.
     * Returns the input untouched when the id is unknown, so a bad request fails as a skipped
     * item rather than a crash.
     */
    fun resolve(context: Context, item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item
        val track = findTrack(context, item.mediaId) ?: return item
        return playable(track, withUri = true)
    }

    private fun downloadedTracks(context: Context): List<FavoriteTrack> {
        val favorites = FavoritesRepository(context).favorites.value
        val fromPlaylists = PlaylistsRepository(context).playlists.value.flatMap { it.tracks }
        return (favorites + fromPlaylists)
            .filter { it.downloadState == DownloadState.DOWNLOADED && !it.streamUrl.isNullOrBlank() }
            .distinctBy { it.id }
    }

    /**
     * Playback source for a saved track: the local file when it is downloaded, otherwise the
     * custom scheme that `PlaybackService`'s ResolvingDataSource turns into a real stream on
     * demand. Mirrors what the app builds for its own queue, so the car and the phone resolve
     * a given track identically.
     */
    private fun playbackUri(track: FavoriteTrack): String {
        val local = track.streamUrl
        if (track.downloadState == DownloadState.DOWNLOADED && !local.isNullOrBlank()) {
            return if (local.startsWith("/")) "file://$local" else local
        }
        return if (track.urn.startsWith("yandex:track:")) {
            "yandex://track/${track.urn.removePrefix("yandex:track:")}"
        } else {
            "soundcloud://track/${track.id}"
        }
    }

    /**
     * Cover for a browse row. The cached copy wins whenever there is one: Android Auto fetches
     * artwork itself, so a remote URL means blank tiles as soon as the car is out of signal —
     * which is precisely when a downloaded library is being used. A local `file://` path is no
     * good either, since the Auto host cannot read our private directory, hence the provider.
     */
    private fun artworkUri(track: FavoriteTrack): Uri? {
        val cached = track.localArtworkPath?.let(::File)
        if (ArtworkProvider.hasArtwork(cached)) return ArtworkProvider.uriFor(track.id)
        return track.artworkUrl?.takeIf { it.isNotBlank() }?.let(Uri::parse)
    }

    private fun browsable(id: String, title: String, artworkUrl: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                    .build()
            )
            .build()

    private fun playable(track: FavoriteTrack, withUri: Boolean = false): MediaItem {
        val builder = MediaItem.Builder()
            // Plain track id, matching what MusicPlayer puts on its own queue items: the app reads
            // the current media id back as a Long to highlight the playing row, and a decorated id
            // would break that the moment playback started from the car.
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.displayArtist)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply { artworkUri(track)?.let { setArtworkUri(it) } }
                    .build()
            )

        if (withUri) {
            val uri = playbackUri(track)
            builder.setUri(uri)
            if (uri.startsWith("soundcloud://") || uri.contains("m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }
        return builder.build()
    }
}
