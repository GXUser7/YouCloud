package com.example.myapplication.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands cached cover art to other processes, read-only.
 *
 * Android Auto draws browse lists itself, in the Auto host process, and loads artwork from the URI
 * we publish. That process cannot open files under our private data directory, so a `file://` path
 * would simply fail — hence a provider. Only the JPEGs written by [OfflineMusicStore.downloadArtwork]
 * are reachable, and only for reading.
 */
class ArtworkProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Artwork is read-only: $uri")
        val context = context ?: throw FileNotFoundException("No context for $uri")

        // The id is the only thing taken from the caller, so it is matched strictly rather than
        // pasted into a path — anything else would let a caller walk out of the artwork directory.
        val id = uri.lastPathSegment?.takeIf { TRACK_ID.matches(it) }?.toLongOrNull()
            ?: throw FileNotFoundException("Bad artwork id in $uri")

        val file = OfflineMusicStore.getInstance(context).artworkFile(id)
        if (!file.exists()) throw FileNotFoundException("No cached artwork for $id")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.example.myapplication.artwork"

        // Track ids are negative for Yandex, so the sign has to be allowed through.
        private val TRACK_ID = Regex("-?\\d+")

        fun uriFor(trackId: Long): Uri =
            Uri.parse("content://$AUTHORITY/art/$trackId")

        /** True when there is something for [uriFor] to serve. */
        fun hasArtwork(file: File?): Boolean = file != null && file.exists() && file.length() > 0L
    }
}
