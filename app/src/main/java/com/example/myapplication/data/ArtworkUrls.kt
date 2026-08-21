package com.example.myapplication.data

/**
 * Both services encode the cover size in the URL, and both hand out a small variant by default.
 * The UI upgrades it per element size; this is the same trick for the copy we keep on disk, which
 * should be big enough for the player screen rather than the 200px thumbnail a listing carries.
 */
object ArtworkUrls {

    private val SizedPathPattern = Regex("\\d{2,4}x\\d{2,4}")

    fun highRes(url: String?): String? {
        if (url.isNullOrBlank()) return null
        // Already a local copy — nothing to upgrade.
        if (url.startsWith("file://") || url.startsWith("/")) return url
        if (url.contains("avatars.yandex.net") || url.contains("music-content")) {
            return SizedPathPattern.replace(url, "800x800")
        }
        return url.replace("large", "t500x500")
    }
}
