package com.example.myapplication.data

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which of YouTube's codecs this phone gets a music video in: VP9 only where a hardware decoder
 * takes it at the size the player asks for, H.264 elsewhere. A phone without one decoded VP9 in
 * software, which on a modest phone fell behind at 720p: the video never caught up with the track,
 * and never showed. H.264 is decoded in hardware everywhere.
 *
 * A phone that says it can and then fails to is remembered ([vp9Failed]), and gets H.264 from then
 * on.
 */
object VideoDecoders {
    private const val TAG = "VideoDecoders"
    private const val PREFS = "video_decoders"
    private const val KEY_VP9_FAILED = "vp9_failed"
    private const val WIDTH = 1280
    private const val HEIGHT = 720

    private enum class Vp9 { All, UpTo30, None }

    @Volatile
    private var hardware: Vp9? = null

    // Whether VP9 is still to be asked for, as state: videos looked up before it failed are looked
    // up again.
    @Volatile
    private var allowed: MutableStateFlow<Boolean>? = null

    /** Whether VP9 may be asked for: not failed here, and decoded in hardware. */
    fun vp9(context: Context): Boolean = vp9Allowed(context).value && support() != Vp9.None

    /** As state, so that what was looked up in VP9 is looked up again once it has failed. */
    fun vp9Allowed(context: Context): StateFlow<Boolean> = (allowed ?: synchronized(this) {
        allowed ?: MutableStateFlow(!prefs(context).getBoolean(KEY_VP9_FAILED, false)).also { allowed = it }
    }).asStateFlow()

    /** VP9 failed to decode here after all: H.264 from now on. */
    fun vp9Failed(context: Context) {
        if (!vp9Allowed(context).value) return
        Log.w(TAG, "VP9 failed to decode on this phone: H.264 from now on")
        prefs(context).edit().putBoolean(KEY_VP9_FAILED, true).apply()
        allowed?.value = false
    }

    /**
     * yt-dlp's format for a music video's picture, no larger than the player needs. VP9 first where
     * it is decoded in hardware — YouTube's H.264 of a busy scene can be missing frames, a third of
     * them in an anime opening, where its VP9 is smooth — at 30 frames a second where the decoder
     * goes no faster. Else H.264; never AV1, which a phone without VP9 in hardware hasn't either.
     */
    fun videoFormat(context: Context): String {
        val avc = "bv[height<=720][vcodec^=avc1][protocol=https]"
        val any = "bv[height<=720][protocol=https]/bv*[height<=720][protocol=https]"
        if (!vp9Allowed(context).value) return "$avc/bv[height<=720][vcodec!^=av01][protocol=https]/$any"
        return when (support()) {
            Vp9.All -> "bv[height<=720][vcodec^=vp][protocol=https]/$avc/$any"
            Vp9.UpTo30 -> "bv[height<=720][fps<=30][vcodec^=vp][protocol=https]/$avc/$any"
            Vp9.None -> "$avc/bv[height<=720][vcodec!^=av01][protocol=https]/$any"
        }
    }

    private fun support(): Vp9 = hardware ?: findHardware().also { hardware = it }

    private fun findHardware(): Vp9 {
        val decoders = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.isHardwareAccelerated &&
                    info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_VP9, ignoreCase = true) }
            }
        }.getOrDefault(emptyList())
        fun takes(fps: Double) = decoders.any { info ->
            runCatching {
                info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_VP9).videoCapabilities
                    ?.areSizeAndRateSupported(WIDTH, HEIGHT, fps) == true
            }.getOrDefault(false)
        }
        val found = when {
            takes(60.0) -> Vp9.All
            takes(30.0) -> Vp9.UpTo30
            else -> Vp9.None
        }
        Log.i(TAG, "Hardware VP9 at ${HEIGHT}p: $found (${decoders.joinToString { it.name }})")
        return found
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
