package com.example.myapplication.data

/**
 * Both services encode the cover size in the URL, and both hand out a small variant by default.
 * The UI upgrades it per element size; this is the same trick for the copy we keep on disk, which
 * should be big enough for the player screen rather than the 200px thumbnail a listing carries.
 */
object ArtworkUrls {

    // A downloaded track's cover, on the phone, by its remote URL (see [local]).
    @Volatile
    private var localByKey: Map<String, String> = emptyMap()

    /**
     * Tells which covers are on the phone: [covers], remote URL to local file. A downloaded track
     * reached from anywhere else — an album, a radio, the queue — came with its remote URL, and
     * where that didn't load (no network, a host a VPN hangs on) it had no cover at all.
     */
    fun setLocal(covers: Map<String, String>) {
        localByKey = covers.entries.associate { (url, file) -> key(url) to file }
    }

    /** The phone's copy of the cover at [url], when a download has one. */
    fun local(url: String?): String? = url?.let { localByKey[key(it)] }

    // The same picture in any size: a size asked of Google's or Yandex's servers is no other cover.
    private fun key(url: String): String = when {
        url.contains("googleusercontent.com") || url.contains("ggpht.com") -> url.substringBefore('=')
        url.contains("avatars.yandex.net") || url.contains("music-content") -> SizedPathPattern.replace(url, "")
        else -> url.replace(SoundCloudSizePattern, "")
    }

    private val SoundCloudSizePattern = Regex("-(large|t\\d{2,4}x\\d{2,4}|original)(?=\\.)")

    private val SizedPathPattern = Regex("\\d{2,4}x\\d{2,4}")

    fun highRes(url: String?): String? {
        if (url.isNullOrBlank()) return null
        // Already a local copy — nothing to upgrade.
        if (url.startsWith("file://") || url.startsWith("/")) return url
        local(url)?.let { return it }
        if (url.contains("avatars.yandex.net") || url.contains("music-content")) {
            return SizedPathPattern.replace(url, "800x800")
        }
        return url.replace("large", "t500x500")
    }
}
