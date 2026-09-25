package com.example.myapplication.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Checking for, fetching and handing over a new version of the app — the same scheme as Yumi's.
 *
 * YouCloud is distributed as an APK from GitHub releases, so nothing updates it unless it updates
 * itself. What this never does is install anything on its own: the last step is Android's package
 * installer, with its own confirmation. An app that could silently replace its own code would be
 * a worse thing to run than an out-of-date one.
 */
class UpdateRepository(
    private val context: Context,
    private val service: UpdateService,
    private val settings: SettingsRepository,
    private val currentVersion: String,
    private val scope: CoroutineScope
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var working: Job? = null

    /**
     * Asks twice a day while the app runs. Compared against a persisted timestamp rather than
     * counted from launch, so opening the app six times a day still means one request; and not
     * right away, because launch is when mixes and the session are loading.
     */
    fun startSchedule() {
        scope.launch {
            delay(FIRST_CHECK_DELAY_MILLIS)
            while (isActive) {
                if (isDue()) check(manual = false)
                delay(TICK_MILLIS)
            }
        }
    }

    private fun isDue(): Boolean {
        if (!settings.updateAutoCheck.value) return false
        val last = settings.lastUpdateCheck
        val since = System.currentTimeMillis() - last
        // A negative interval means the clock went backwards; waiting for it to catch up could
        // suspend checking indefinitely.
        return last == 0L || since < 0L || since >= INTERVAL_MILLIS
    }

    /**
     * @param manual a person pressed the button. The difference is only in what is said after:
     *   an automatic check that finds nothing or fails leaves no trace, a manual one has to answer
     *   or the button looks broken.
     */
    fun check(manual: Boolean) {
        if (working?.isActive == true) return
        working = scope.launch {
            _state.value = UpdateState.Checking
            settings.markUpdateChecked()
            val result = withContext(Dispatchers.IO) { runCatching { service.latest() } }
            result
                .onSuccess { release ->
                    _state.value = if (Version.isNewer(release.version, currentVersion)) {
                        UpdateState.Available(release)
                    } else if (manual) {
                        UpdateState.UpToDate(currentVersion)
                    } else {
                        UpdateState.Idle
                    }
                }
                .onFailure { error ->
                    _state.value = if (manual) {
                        UpdateState.Failed(error.message ?: "Не удалось проверить обновления")
                    } else {
                        UpdateState.Idle
                    }
                }
        }
    }

    fun download() {
        val release = (_state.value as? UpdateState.Available)?.release ?: return
        if (working?.isActive == true) return
        working = scope.launch {
            _state.value = UpdateState.Downloading(release, 0, release.sizeBytes)
            val target = File(File(context.filesDir, DIRECTORY), "YouCloud-${release.version}.apk")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Whatever is left from an earlier version is dead weight.
                    target.parentFile?.listFiles()?.forEach { if (it != target) it.delete() }
                    service.download(release, target) { done, total ->
                        _state.value = UpdateState.Downloading(release, done, total)
                    }
                }
            }
            result
                .onSuccess { _state.value = UpdateState.Ready(release, it) }
                .onFailure { error ->
                    _state.value = UpdateState.Failed(error.message ?: "Не удалось скачать обновление")
                }
        }
    }

    /** Dismisses whatever the last check said. */
    fun clear() {
        if (working?.isActive == true) return
        _state.value = UpdateState.Idle
    }

    /**
     * Hands the downloaded file to Android's installer. The permission to install packages lives
     * in a settings screen rather than a dialog, so when it is missing this opens that screen —
     * a button that silently does nothing is the one outcome worth avoiding.
     *
     * @return false when the user was sent elsewhere instead of to the installer.
     */
    fun install(activity: Context): Boolean {
        val ready = _state.value as? UpdateState.Ready ?: return false

        if (!context.packageManager.canRequestPackageInstalls()) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }

        // Android refuses an update signed by another key anyway, but only this can say *why*.
        // Without it a truncated file or a build signed elsewhere comes back from the system as
        // "App not installed", which names nothing.
        verify(ready.file)?.let { reason ->
            _state.value = UpdateState.Failed("Обновление не подходит: $reason")
            return false
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready.file)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        return true
    }

    /** What is wrong with the downloaded file, or null when nothing is. Cheapest question first. */
    private fun verify(file: File): String? {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val candidate = runCatching {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }.getOrNull() ?: return "скачанный файл — не устанавливаемый пакет"

        if (candidate.packageName != context.packageName) {
            return "внутри пакет ${candidate.packageName}, а не это приложение"
        }
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }.getOrNull() ?: return null

        val ours = signersOf(installed)
        val theirs = signersOf(candidate)
        // Not knowing is not evidence of a mismatch; refusing on it would block every update on a
        // phone that answers differently.
        if (ours.isNullOrEmpty() || theirs.isNullOrEmpty()) return null
        if (ours != theirs) return "подписано другим ключом, чем установленное приложение"
        return null
    }

    private fun signersOf(info: PackageInfo): Set<String>? =
        info.signingInfo?.apkContentsSigners?.mapNotNull { it?.toCharsString() }?.toSet()

    private companion object {
        const val DIRECTORY = "updates"
        const val INTERVAL_MILLIS = 12 * 60 * 60 * 1000L
        const val FIRST_CHECK_DELAY_MILLIS = 20_000L
        const val TICK_MILLIS = 60_000L
    }
}
