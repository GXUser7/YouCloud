package com.example.myapplication.data

class SoundCloudPlaybackResolver(
    private val service: SoundCloudService
) {
    /** [progressiveFirst]: a single file over a segmented stream, when there is one — for a TV, which can seek in it. */
    suspend fun resolve(track: SoundCloudTrack, clientId: String, progressiveFirst: Boolean = false): String? {
        if (!soundCloudPlaysWhole(track)) return null

        val authorization = track.trackAuthorization ?: return null
        val transcoding = track.media
            ?.transcodings
            ?.filter(::isWholeStream)
            ?.minByOrNull { if (progressiveFirst && it.format?.protocol == "progressive") -1 else priority(it) }
            ?: return null

        return service.resolveTranscoding(
            transcodingUrl = transcoding.url,
            clientId = clientId,
            trackAuthorization = authorization
        ).url
    }

    private fun priority(transcoding: SoundCloudTranscoding): Int {
        val preset = transcoding.preset.orEmpty()
        val protocol = transcoding.format?.protocol.orEmpty()

        return when {
            preset.startsWith("aac_160") -> 0
            preset.startsWith("aac_96") -> 1
            preset.startsWith("abr") -> 2
            protocol == "hls" -> 3
            protocol == "progressive" -> 4
            else -> 5
        }
    }
}
