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
        val segments: List<VideoSegment>? = null,
        // A YouTube video's codec, and which choice of YouTube's formats it was picked by.
        val codec: String? = null,
        val formats: Int = 0
    )

    private fun metaFile(trackId: Long) = File(dir, "$trackId.json")
    private fun videoFile(trackId: Long) = File(dir, "$trackId.mp4")
    private fun noneFile(trackId: Long) = File(dir, "$trackId.none")

    /** The track's saved video, as the player takes it: a local file. */
    fun get(trackId: Long): TrackVideo? {
        val video = videoFile(trackId).takeIf { it.exists() } ?: return null
        val meta = meta(trackId) ?: return null
        return TrackVideo(
            trackId = trackId,
            url = Uri.fromFile(video).toString(),
            loop = meta.loop,
            vertical = meta.vertical,
            segments = meta.segments.orEmpty()
        )
    }

    private fun meta(trackId: Long): Meta? =
        runCatching { gson.fromJson(metaFile(trackId).readText(), Meta::class.java) }.getOrNull()

    /**
     * Looked into already: a video is saved, or it was found lately to have none. A YouTube
     * video picked from YouTube's formats as they were chosen before is looked into again.
     */
    fun isSettled(trackId: Long): Boolean {
        if (videoFile(trackId).exists() && metaFile(trackId).exists()) {
            val meta = meta(trackId) ?: return false
            return meta.loop || meta.formats >= YOUTUBE_FORMATS
        }
        val none = noneFile(trackId)
        return none.exists() && System.currentTimeMillis() - none.lastModified() < NONE_REMEMBERED_MS
    }

    fun markNone(trackId: Long) {
        noneFile(trackId).writeText("")
    }

    /** Every track looked into again: what was found to have no video was looked for less widely. */
    fun forgetNone() {
        dir.listFiles { file -> file.name.endsWith(".none") }.orEmpty().forEach { it.delete() }
    }

    /**
     * Downloads [video]'s stream and keeps it for its track — unless the one kept already is in
     * the same codec, so the same picture.
     */
    suspend fun save(video: TrackVideo): Boolean {
        val kept = videoFile(video.trackId)
        if (kept.exists() && video.codec != null && codecFamily(video.codec) == keptCodecFamily(video.trackId)) {
            writeMeta(video)
            return true
        }
        val partial = File(dir, "${video.trackId}.part")
        val headers = video.userAgent?.let { mapOf("User-Agent" to it) }.orEmpty()
        if (!RangedDownload.toFile(video.url, headers, partial)) return false
        partial.renameTo(videoFile(video.trackId))
        writeMeta(video)
        noneFile(video.trackId).delete()
        return true
    }

    private fun writeMeta(video: TrackVideo) {
        val meta = Meta(video.loop, video.vertical, video.segments, video.codec, YOUTUBE_FORMATS)
        metaFile(video.trackId).writeText(gson.toJson(meta))
    }

    // The kept video's codec: as saved with it, or, for one kept before that was, as its file says.
    private fun keptCodecFamily(trackId: Long): String? {
        meta(trackId)?.codec?.let { return codecFamily(it) }
        val extractor = android.media.MediaExtractor()
        return try {
            extractor.setDataSource(videoFile(trackId).absolutePath)
            (0 until extractor.trackCount)
                .mapNotNull { extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME) }
                .firstOrNull { it.startsWith("video/") }
                ?.let(::codecFamily)
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    // "avc", "vp9" or "av1", from YouTube's codec names or a MIME type.
    private fun codecFamily(codec: String): String = when {
        codec.startsWith("avc") || codec == android.media.MediaFormat.MIMETYPE_VIDEO_AVC -> "avc"
        codec.startsWith("vp9") || codec.startsWith("vp09") || codec == android.media.MediaFormat.MIMETYPE_VIDEO_VP9 -> "vp9"
        codec.startsWith("av01") || codec == android.media.MediaFormat.MIMETYPE_VIDEO_AV1 -> "av1"
        else -> codec
    }

    fun remove(trackId: Long) {
        listOf(videoFile(trackId), metaFile(trackId), noneFile(trackId), File(dir, "$trackId.part")).forEach { it.delete() }
    }

    /** Every track something is kept for. */
    fun trackIds(): Set<Long> =
        dir.listFiles().orEmpty().mapNotNull { it.name.substringBefore('.').toLongOrNull() }.toSet()

    private companion object {
        val NONE_REMEMBERED_MS = TimeUnit.DAYS.toMillis(14)

        // Raised whenever the choice among YouTube's formats changes, so that videos kept before
        // are looked into again. 1: VP9 first, from the TV client too.
        const val YOUTUBE_FORMATS = 1
    }
}
