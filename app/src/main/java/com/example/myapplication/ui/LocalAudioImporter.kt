package com.example.myapplication.ui

import com.example.myapplication.i18n.tr
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.myapplication.data.DownloadState
import com.example.myapplication.data.FavoriteTrack
import com.example.myapplication.data.OfflineVideoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Copies [uris] — audio files, or videos — from the phone into the app as downloaded tracks. A
 * video becomes a track with its clip: its sound is the track, and the video itself, kept once in
 * [videos], plays in the player alongside it, to the frame; a frame from its middle is the cover.
 */
suspend fun importLocalAudio(context: Context, uris: List<Uri>, videos: OfflineVideoStore): List<FavoriteTrack> = withContext(Dispatchers.IO) {
    val importedTracks = mutableListOf<FavoriteTrack>()
    val localMusicDir = File(context.filesDir, "local_music").apply { mkdirs() }
    val localArtDir = File(context.filesDir, "local_music_artworks").apply { mkdirs() }

    for (uri in uris) {
        try {
            // Copy file to internal storage
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            val isVideo = mimeType.startsWith("video/")
            val fileExtension = mimeType.substringAfterLast("/", "").ifBlank { "mp3" }
            val uniqueId = System.currentTimeMillis() + UUID.randomUUID().hashCode()
            val destFile = if (isVideo) {
                videos.localVideoFile(uniqueId)
            } else {
                File(localMusicDir, "local_track_$uniqueId.$fileExtension")
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Extract metadata
            val retriever = MediaMetadataRetriever()
            var title = ""
            var artist = ""
            var duration = 0L
            var artworkPath: String? = null
            var vertical: Boolean? = null

            try {
                retriever.setDataSource(destFile.absolutePath)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                duration = durationStr?.toLongOrNull() ?: 0L

                val artworkBytes = retriever.embeddedPicture
                if (artworkBytes != null && artworkBytes.isNotEmpty()) {
                    val artFile = File(localArtDir, "local_art_$uniqueId.jpg")
                    FileOutputStream(artFile).use { fos ->
                        fos.write(artworkBytes)
                    }
                    artworkPath = artFile.absolutePath
                }
                if (isVideo) {
                    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    if (width != null && height != null) {
                        vertical = if (rotation % 180 == 0) height > width else width > height
                    }
                    // Without a cover of its own, a frame from a third of the way in.
                    if (artworkPath == null) {
                        val frame = retriever.getFrameAtTime(duration * 1000 / 3, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (frame != null) {
                            val artFile = File(localArtDir, "local_art_$uniqueId.jpg")
                            FileOutputStream(artFile).use { fos -> frame.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, fos) }
                            frame.recycle()
                            artworkPath = artFile.absolutePath
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ImportAudio", "Error retrieving metadata for $uri", e)
            } finally {
                retriever.release()
            }

            // Fallback for title/artist
            if (title.isBlank()) {
                title = getFileName(context, uri)?.substringBeforeLast('.') ?: tr("Локальный трек %s", uniqueId)
            }
            if (isVideo) videos.markLocal(uniqueId, vertical)
            if (artist.isBlank()) {
                artist = tr("Устройство")
            }

            val favoriteTrack = FavoriteTrack(
                id = uniqueId,
                urn = "local:track:$uniqueId",
                title = title,
                artworkUrl = artworkPath,
                permalinkUrl = null,
                artist = artist,
                duration = duration,
                streamUrl = destFile.absolutePath,
                downloadState = DownloadState.DOWNLOADED
            )
            importedTracks.add(favoriteTrack)
        } catch (e: Exception) {
            Log.e("ImportAudio", "Failed to import $uri", e)
        }
    }
    importedTracks
}

private fun getFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = cursor.getString(index)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path?.substringAfterLast("/")
    }
    return name
}
