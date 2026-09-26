package com.example.myapplication.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Videos of downloaded tracks, kept with them so that they play offline as they do online: the
 * file, and how it goes with the track — looping or in step, which way up, the map that lines it
 * up. A track found to have no video is remembered too, for a while, so that it isn't looked up
 * again every time the app starts; a video may still turn up for it later.
 */
class OfflineVideoStore(context: Context) {
    private val dir = File(context.filesDir, "offline_video").apply { mkdirs() }
    private val gson = Gson()

    private class Meta(
        val loop: Boolean = false,
        val vertical: Boolean? = null,
        val segments: List<VideoSegment>? = null
    )

    private fun metaFile(trackId: Long) = File(dir, "$trackId.json")
    private fun videoFile(trackId: Long) = File(dir, "$trackId.mp4")
    private fun noneFile(trackId: Long) = File(dir, "$trackId.none")

    /** The track's saved video, as the player takes it: a local file. */
    fun get(trackId: Long): TrackVideo? {
        val video = videoFile(trackId).takeIf { it.exists() } ?: return null
        val meta = runCatching { gson.fromJson(metaFile(trackId).readText(), Meta::class.java) }.getOrNull() ?: return null
        return TrackVideo(
            trackId = trackId,
            url = Uri.fromFile(video).toString(),
            loop = meta.loop,
            vertical = meta.vertical,
            segments = meta.segments.orEmpty()
        )
    }

    /** Looked into already: a video is saved, or it was found lately to have none. */
    fun isSettled(trackId: Long): Boolean {
        if (videoFile(trackId).exists() && metaFile(trackId).exists()) return true
        val none = noneFile(trackId)
        return none.exists() && System.currentTimeMillis() - none.lastModified() < NONE_REMEMBERED_MS
    }

    fun markNone(trackId: Long) {
        noneFile(trackId).writeText("")
    }

    /** Downloads [video]'s stream and keeps it for its track. */
    suspend fun save(video: TrackVideo): Boolean {
        val partial = File(dir, "${video.trackId}.part")
        val headers = video.userAgent?.let { mapOf("User-Agent" to it) }.orEmpty()
        if (!RangedDownload.toFile(video.url, headers, partial)) return false
        partial.renameTo(videoFile(video.trackId))
        metaFile(video.trackId).writeText(gson.toJson(Meta(video.loop, video.vertical, video.segments)))
        noneFile(video.trackId).delete()
        return true
    }

    fun remove(trackId: Long) {
        listOf(videoFile(trackId), metaFile(trackId), noneFile(trackId), File(dir, "$trackId.part")).forEach { it.delete() }
    }

    /** Every track something is kept for. */
    fun trackIds(): Set<Long> =
        dir.listFiles().orEmpty().mapNotNull { it.name.substringBefore('.').toLongOrNull() }.toSet()

    private companion object {
        val NONE_REMEMBERED_MS = TimeUnit.DAYS.toMillis(14)
    }
}
