package com.example.myapplication.player.restore

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The two restoration models: the light one, in the app (300 KB, about 70 thousand weights), and
 * the strong one (2 MB, half a million), downloaded from the app's repository when asked for —
 * finer bands, twice the memory, closer to the original on songs neither has heard.
 */
object RestoreModels {
    private const val TAG = "RestoreModels"

    // The settings, in the app's preferences: read by the playback service as they change.
    const val KEY_ENABLED = "restore_enabled"
    const val KEY_STRONG = "restore_strong"
    private const val LIGHT_ASSET = "restore/light-v1.ycrm"
    private const val STRONG_FILE = "restore/strong-v1.ycrm"

    /** The strong model's file, its size and its SHA-256: a download that isn't exactly it is thrown away. */
    private val STRONG_URLS = listOf(
        "https://raw.githubusercontent.com/GXUser7/YouCloud/main/models/restore/strong-v1.ycrm",
        "https://raw.githubusercontent.com/GXUser7/YouCloud/claude/upbeat-hawking-vzmm6w/models/restore/strong-v1.ycrm"
    )

    sealed interface Download {
        data object Absent : Download
        data class Running(val fraction: Float) : Download
        data object Ready : Download
        data class Failed(val reason: String) : Download
    }

    private val _strong = MutableStateFlow<Download>(Download.Absent)
    val strong: StateFlow<Download> = _strong.asStateFlow()

    @Volatile
    private var light: RestoreModel? = null

    @Volatile
    private var strongModel: RestoreModel? = null

    fun refresh(context: Context) {
        if (_strong.value is Download.Running) return
        _strong.value = if (strongFile(context).exists()) Download.Ready else Download.Absent
    }

    private fun strongFile(context: Context) = File(context.filesDir, STRONG_FILE)

    /** The light model, read once from the app's assets. */
    fun light(context: Context): RestoreModel? = light ?: synchronized(this) {
        light ?: runCatching { context.assets.open(LIGHT_ASSET).use(RestoreModel::read) }
            .onFailure { Log.e(TAG, "Couldn't read the light model", it) }
            .getOrNull()
            .also { light = it }
    }

    /** The strong model, if it has been downloaded; read once. */
    fun strong(context: Context): RestoreModel? = strongModel ?: synchronized(this) {
        strongModel ?: strongFile(context).takeIf { it.exists() }?.let { file ->
            runCatching { file.inputStream().use(RestoreModel::read) }
                .onFailure {
                    Log.e(TAG, "Couldn't read the strong model; deleting it", it)
                    file.delete()
                    _strong.value = Download.Absent
                }
                .getOrNull()
        }.also { strongModel = it }
    }

    // Its own scope: leaving the settings doesn't stop a download.
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** Downloads the strong model, unless it is under way; [onReady] once it is on the phone. */
    fun startStrongDownload(context: Context, onReady: () -> Unit) {
        val app = context.applicationContext
        scope.launch {
            downloadStrong(app, OkHttpClient())
            if (_strong.value == Download.Ready) onReady()
        }
    }

    private suspend fun downloadStrong(context: Context, client: OkHttpClient) = withContext(Dispatchers.IO) {
        if (_strong.value is Download.Running) return@withContext
        _strong.value = Download.Running(0f)
        val target = strongFile(context)
        val part = File(target.parentFile, target.name + ".part")
        target.parentFile?.mkdirs()
        var failure = "Нет связи"
        for (url in STRONG_URLS) {
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("empty")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var read = 0L
                    part.outputStream().use { out ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                out.write(buffer, 0, n)
                                digest.update(buffer, 0, n)
                                read += n
                                _strong.value = Download.Running((read.toFloat() / STRONG_SIZE).coerceAtMost(1f))
                            }
                        }
                    }
                    val sha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (read != STRONG_SIZE || sha != STRONG_SHA256) throw IOException("not the model ($read bytes, $sha)")
                    // Read once before it counts: a file the app can't use is never kept.
                    part.inputStream().use(RestoreModel::read)
                    if (!part.renameTo(target)) throw IOException("rename")
                }
                strongModel = null
                _strong.value = Download.Ready
                return@withContext
            } catch (e: Exception) {
                Log.w(TAG, "Strong model from $url failed", e)
                failure = e.message ?: failure
                part.delete()
            }
        }
        _strong.value = Download.Failed(failure)
    }

    fun deleteStrong(context: Context) {
        strongModel = null
        strongFile(context).delete()
        _strong.value = Download.Absent
    }
}
