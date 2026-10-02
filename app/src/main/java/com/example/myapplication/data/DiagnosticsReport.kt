package com.example.myapplication.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.myapplication.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Отправить журнал": what the app has logged since it started, with what it runs on, as a file
 * for whoever is asked to look into something that doesn't work on someone else's phone — a
 * friend's clips that never show, say.
 *
 * It says which accounts are connected, not whose; and what the log carries that is no one else's
 * business goes: the queries of addresses (stream links are signed for the phone's address, and
 * name it), tokens, cookies.
 */
object DiagnosticsReport {
    private const val LOG_LINES = 6000

    suspend fun share(context: Context, settings: SettingsRepository) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            File(dir, "youcloud-journal.txt").apply { writeText(report(context, settings)) }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Журнал YouCloud ${BuildConfig.VERSION_NAME}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, "Отправить журнал").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun report(context: Context, settings: SettingsRepository): String = buildString {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(it)
        }
        fun yesNo(value: Boolean) = if (value) "да" else "нет"
        appendLine("YouCloud ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Собран: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())}")
        appendLine("Телефон: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Процессор: ${Build.SUPPORTED_ABIS.joinToString()}; ${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}")
        appendLine("Память: ${memory.totalMem / (1 shl 20)} МБ, свободно ${memory.availMem / (1 shl 20)} МБ${if (memory.lowMemory) ", мало" else ""}")
        appendLine("VP9 в железе: ${yesNo(VideoDecoders.vp9(context))}")
        appendLine("yt-dlp: ${runCatching { com.yausername.youtubedl_android.YoutubeDL.versionName(context) }.getOrNull() ?: "не обновлялся"}")
        appendLine("Аккаунты: Яндекс ${yesNo(settings.yandexTokenValue().isNotBlank())}, YouTube Music ${yesNo(settings.ytMusicAuth() != null)}, SoundCloud ${yesNo(settings.oauthTokenValue().isNotBlank())}")
        appendLine(
            "Настройки: клипы ${yesNo(settings.playerVideos.value)}, клипы с YouTube ${yesNo(settings.videoYouTube.value)}, " +
                "видеошоты Яндекса ${yesNo(settings.videoYandex.value)}, кроссфейд ${settings.crossfadeSeconds.value} с, " +
                "автопродолжение ${yesNo(settings.autoContinue.value)}"
        )
        appendLine()
        appendLine("— журнал —")
        logLines().forEach { appendLine(redact(it)) }
    }

    /** This run of the app's own log: an app reads only its own, and no permission is needed. */
    private fun logLines(): List<String> = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "time", "--pid", android.os.Process.myPid().toString(), "-t", LOG_LINES.toString()
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readLines() }.also { process.waitFor() }
    }.getOrElse { listOf("(журнал не прочитался: $it)") }

    private val URL_QUERY = Regex("""(https?://[^\s?"'<>]+)\?[^\s"'<>]*""")
    private val SECRET = Regex("""(?i)\b(oauth|bearer|token|authorization|cookie|sapisid\w*|client_id|access_token)\b(\s*[:=]\s*|\s+)("[^"]*"|[^\s,;&]+)""")

    /** A line without what is no one else's: the queries of addresses, tokens, cookies. */
    private fun redact(line: String): String = line
        .replace(URL_QUERY, "$1?…")
        .replace(SECRET) { "${it.groupValues[1]}${it.groupValues[2]}…" }
}
