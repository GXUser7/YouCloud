package com.example.myapplication.data

import com.google.gson.JsonParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Where new versions come from: the repository's GitHub releases, asked directly.
 *
 * Two ways of asking, and the second one is not paranoia. The API carries the notes and exact
 * asset URLs, but allows sixty unauthenticated requests an hour *per address* — behind a mobile
 * carrier's NAT that address is shared by a lot of people, so the pleasant path is also the one
 * that answers 403 to somebody who did nothing wrong. The plain `releases/latest` page has no such
 * limit and redirects to the tag, and the release's asset list is a page of its own. So: API first,
 * web pages second, and an update is missed only when both fail.
 *
 * Releases name their APK inconsistently — `YouCloud.apk` in older ones, `YouCloud.3.1.apk` since —
 * so the asset is picked by extension, preferring the one that carries the version.
 *
 * Blocking on purpose: every caller is already on [kotlinx.coroutines.Dispatchers.IO].
 */
class UpdateService(private val userAgent: String) {

    fun latest(): Release = runCatching { fromApi() }
        .getOrElse { apiFailure ->
            runCatching { fromWeb() }.getOrElse {
                // The API's complaint is the more informative one — rate limiting says so in as
                // many words — so that is the one that surfaces.
                throw apiFailure
            }
        }

    private fun fromApi(): Release {
        val body = get("$API/releases/latest") { connection ->
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        val release = JsonParser.parseString(body).asJsonObject
        val tag = release.get("tag_name")?.asString
            ?: throw IllegalStateException("Не удалось прочитать сведения о релизе")
        val version = tag.removePrefix("v")
        val notes = release.get("body")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
        val assets = release.getAsJsonArray("assets")?.map { it.asJsonObject }.orEmpty()
            .filter { it.get("name")?.asString.orEmpty().endsWith(".apk", ignoreCase = true) }
        val asset = assets.firstOrNull { it.get("name").asString.contains(version) }
            ?: assets.firstOrNull()
            ?: throw IllegalStateException("В релизе $tag нет APK")
        return Release(
            version = version,
            notes = notes.trim(),
            apkUrl = asset.get("browser_download_url").asString,
            sizeBytes = asset.get("size")?.asLong ?: 0L
        )
    }

    /** The tag from the redirect `releases/latest` performs, the APK from the tag's asset page. */
    private fun fromWeb(): Release {
        val connection = (URL("$SITE/releases/latest").openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            instanceFollowRedirects = false
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("User-Agent", userAgent)
        }
        val tag = try {
            connection.responseCode
            connection.getHeaderField("Location")?.substringAfterLast("/tag/")
        } finally {
            connection.disconnect()
        }
        if (tag.isNullOrBlank() || tag.contains('/')) {
            throw IllegalStateException("Не удалось прочитать сведения о релизе")
        }
        val version = tag.removePrefix("v")
        val page = get("$SITE/releases/expanded_assets/$tag") { }
        val links = AssetLink.findAll(page).map { it.groupValues[1] }.toList()
        val link = links.firstOrNull { it.contains(version) } ?: links.firstOrNull()
            ?: throw IllegalStateException("В релизе $tag нет APK")
        return Release(version = version, notes = "", apkUrl = "https://github.com$link", sizeBytes = 0L)
    }

    /**
     * Streams the APK to [target], reporting bytes so far and the total (zero when unknown).
     *
     * Written to a `.part` file and renamed at the end, and the byte count is checked against what
     * was announced: a connection cut mid-transfer ends the read loop just like a finished one,
     * and a truncated APK must never be offered to the installer as an update.
     */
    fun download(release: Release, target: File, onProgress: (Long, Long) -> Unit): File {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        part.delete()

        var current = release.apkUrl
        repeat(MAX_REDIRECTS) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    // GitHub hands the bytes off to another host: the ordinary path, not an edge case.
                    val location = connection.getHeaderField("Location")
                        ?: throw IllegalStateException("Не удалось скачать (HTTP $code)")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IllegalStateException("Не удалось скачать (HTTP $code)")

                val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
                var written = 0L
                connection.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            onProgress(written, total)
                        }
                    }
                }
                if (written <= 0L) throw IllegalStateException("Скачался пустой файл")
                if (total > 0 && written != total) {
                    throw IllegalStateException("Загрузка оборвалась: $written байт из $total")
                }
                target.delete()
                check(part.renameTo(target)) { "Не удалось сохранить ${part.name}" }
                return target
            } finally {
                connection.disconnect()
            }
        }
        throw IllegalStateException("Слишком много перенаправлений")
    }

    private fun get(url: String, configure: (HttpURLConnection) -> Unit): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("User-Agent", userAgent)
            configure(this)
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                // GitHub says why in the body — "API rate limit exceeded" being the one worth reading.
                val message = runCatching {
                    JsonParser.parseString(body).asJsonObject.get("message")?.asString
                }.getOrNull()
                throw IllegalStateException(message ?: "HTTP $code")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val SITE = "https://github.com/GXUser7/YouCloud"
        const val API = "https://api.github.com/repos/GXUser7/YouCloud"
        val AssetLink = Regex("href=\"(/GXUser7/YouCloud/releases/download/[^\"]+?\\.apk)\"", RegexOption.IGNORE_CASE)

        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val MAX_REDIRECTS = 5
    }
}
