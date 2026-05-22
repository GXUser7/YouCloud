package com.example.myapplication.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.myapplication.data.DownloadState
import com.example.myapplication.data.FavoriteTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

suspend fun importLocalAudio(context: Context, uris: List<Uri>): List<FavoriteTrack> = withContext(Dispatchers.IO) {
    val importedTracks = mutableListOf<FavoriteTrack>()
    val localMusicDir = File(context.filesDir, "local_music").apply { mkdirs() }
    val localArtDir = File(context.filesDir, "local_music_artworks").apply { mkdirs() }

    for (uri in uris) {
        try {
            // Copy file to internal storage
            val fileExtension = context.contentResolver.getType(uri)?.substringAfterLast("/") ?: "mp3"
            val uniqueId = System.currentTimeMillis() + UUID.randomUUID().hashCode()
            val destFile = File(localMusicDir, "local_track_$uniqueId.$fileExtension")

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
            } catch (e: Exception) {
                Log.e("ImportAudio", "Error retrieving metadata for $uri", e)
            } finally {
                retriever.release()
            }

            // Fallback for title/artist
            if (title.isBlank()) {
                title = getFileName(context, uri) ?: "Локальный трек $uniqueId"
            }
            if (artist.isBlank()) {
                artist = "Устройство"
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
