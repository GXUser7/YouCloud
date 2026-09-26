package com.example.myapplication.data

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.write

/**
 * yt-dlp, run on the phone. youtubedl-android bundles Python, yt-dlp and QuickJS — the JavaScript
 * engine yt-dlp solves the player's challenges with — and runs yt-dlp as a process of its own.
 *
 * YouTube changes its player every few weeks and yt-dlp catches up within days, so the script
 * doesn't wait for an app release: it updates itself from yt-dlp's nightly builds, the channel
 * yt-dlp recommends for YouTube, once a day, and at once when an extraction fails.
 *
 * Signed out, YouTube asks VPN addresses to "sign in to confirm you're not a bot". The YouTube
 * Music session goes to yt-dlp as a cookie file, which is what gets past it.
 */
object YtDlp {
    private const val TAG = "YtDlp"
    private const val PREFS = "yt_dlp"
    private const val KEY_CHECKED_AT = "update_checked_at"

    /**
     * What to ask yt-dlp for. Plain HTTPS only, never an HLS or DASH manifest: the players and
     * the downloader here all expect one file.
     */
    enum class Kind(val format: String) {
        /** AAC in MP4 (itag 140) first: every device decodes it, and downloads are kept as it. */
        AUDIO("140/bestaudio[ext=m4a][protocol=https]/bestaudio[protocol=https]"),

        /** A music video's picture alone, H.264 first, no larger than the screen needs. */
        VIDEO(
            "bv[height<=720][vcodec^=avc1][protocol=https]/bv[height<=720][protocol=https]/" +
                "bv*[height<=720][protocol=https]"
        )
    }

    /**
     * A yt-dlp plugin that keeps the challenge solver's preprocessed player between runs. Solving
     * a challenge starts with QuickJS parsing YouTube's whole player script: six seconds on a
     * phone, every track, against under one from the cache. yt-dlp leaves that cache off for its
     * size, 4 MB a player, which [pruneCache] keeps in check.
     */
    private const val PLUGIN = """from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP

EJSBaseJCP._ENABLE_PREPROCESSED_PLAYER_CACHE = True
"""

    private val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
    private val CACHE_MAX_AGE_MS = TimeUnit.DAYS.toMillis(21)
    private const val PLAYERS_KEPT = 2
    private val FAILED_UPDATE_RETRY_MS = TimeUnit.MINUTES.toMillis(30)
    private val RUN_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(90)

    @Volatile
    private var initialised = false

    // An update replaces the script in place, and Python reads it lazily while it runs.
    private val scriptLock = ReentrantReadWriteLock()

    // Each run is a Python process of its own, tens of MB: the playing track and the next one.
    private val runs = Semaphore(2)
    private val pending = ConcurrentHashMap<String, FutureTask<YouTubeStreams.Stream?>>()
    private val lastUpdateAttempt = AtomicLong(0)
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "yt-dlp watchdog").apply { isDaemon = true }
    }

    /** Unpacks Python and brings yt-dlp up to date ahead of the first track, off the caller's thread. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        thread(name = "yt-dlp warm-up", isDaemon = true) { ensureReady(app) }
    }

    /**
     * The [kind] of stream of [videoId], or null when yt-dlp couldn't get one. Blocking — a Python
     * process and several round trips, seconds on a phone; call it off the main thread. Asking
     * again for a stream already being resolved waits for that run instead of starting another.
     */
    fun resolve(context: Context, videoId: String, auth: YtAuth?, kind: Kind): YouTubeStreams.Stream? {
        val app = context.applicationContext
        val key = "$kind:$videoId"
        val task = FutureTask { resolveOrUpdate(app, videoId, auth, kind) }
        val running = pending.putIfAbsent(key, task)
        if (running == null) {
            try {
                task.run()
            } finally {
                pending.remove(key, task)
            }
        }
        return try {
            (running ?: task).get()
        } catch (e: ExecutionException) {
            Log.w(TAG, "$videoId: ${e.cause}")
            null
        }
    }

    private fun resolveOrUpdate(context: Context, videoId: String, auth: YtAuth?, kind: Kind): YouTubeStreams.Stream? {
        if (!ensureReady(context)) return null
        extract(context, videoId, auth, kind)?.let { return it }
        // Most failures are YouTube having changed something a newer yt-dlp already handles.
        if (!update(context, minInterval = FAILED_UPDATE_RETRY_MS)) return null
        return extract(context, videoId, auth, kind)
    }

    private fun ensureReady(context: Context): Boolean {
        if (!initialised) {
            synchronized(this) {
                if (!initialised) {
                    try {
                        val started = System.currentTimeMillis()
                        YoutubeDL.init(context)
                        installPlugin(context)
                        pruneCache(context)
                        initialised = true
                        Log.i(TAG, "Unpacked in ${System.currentTimeMillis() - started} ms, " +
                            "yt-dlp ${YoutubeDL.versionName(context) ?: "bundled"}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Could not unpack yt-dlp", e)
                        return false
                    }
                }
            }
        }
        val checkedAt = prefs(context).getLong(KEY_CHECKED_AT, 0L)
        if (checkedAt == 0L) {
            // The copy inside the APK is as old as the library release — months, for YouTube.
            // Worth the wait on the very first track.
            update(context, minInterval = FAILED_UPDATE_RETRY_MS)
        } else if (System.currentTimeMillis() - checkedAt > UPDATE_INTERVAL_MS) {
            thread(name = "yt-dlp update", isDaemon = true) {
                update(context, minInterval = FAILED_UPDATE_RETRY_MS)
            }
        }
        return true
    }

    /** @return whether a newer yt-dlp was installed. */
    private fun update(context: Context, minInterval: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = lastUpdateAttempt.get()
        if (now - last < minInterval || !lastUpdateAttempt.compareAndSet(last, now)) return false
        return scriptLock.write {
            try {
                val status = YoutubeDL.updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
                prefs(context).edit().putLong(KEY_CHECKED_AT, now).apply()
                Log.i(TAG, "yt-dlp ${YoutubeDL.versionName(context)}: $status")
                status == YoutubeDL.UpdateStatus.DONE
            } catch (e: Exception) {
                Log.w(TAG, "yt-dlp update failed: $e")
                false
            }
        }
    }

    private fun extract(context: Context, videoId: String, auth: YtAuth?, kind: Kind): YouTubeStreams.Stream? {
        runs.acquire()
        try {
            return scriptLock.read { runYtDlp(context, videoId, auth, kind) }
        } finally {
            runs.release()
        }
    }

    private fun runYtDlp(context: Context, videoId: String, auth: YtAuth?, kind: Kind): YouTubeStreams.Stream? {
        val dir = runDir(context).apply { mkdirs() }
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId")
            .addOption("--dump-json")
            .addOption("--no-playlist")
            .addOption("-f", kind.format)
            .addOption("--socket-timeout", 15)
            // Keeps the player script and its solved challenges between runs; the library
            // otherwise turns the cache off.
            .addOption("--cache-dir", cacheDir(context).absolutePath)
            .addOption("--plugin-dirs", pluginDir(context).absolutePath)
            // Only the plain audio files are of use; the HLS manifest is one more request.
            .addOption("--extractor-args", "youtube:skip=hls,dash")
        // A file per run: yt-dlp writes the jar back when it exits, and two runs can overlap.
        val cookies = auth?.let { writeCookies(File(dir, "cookies-$kind-$videoId.txt"), it) }
        cookies?.let { request.addOption("--cookies", it.absolutePath) }

        val processId = "resolve-$kind-$videoId-${System.nanoTime()}"
        val timeout = watchdog.schedule(
            { YoutubeDL.destroyProcessById(processId) }, RUN_TIMEOUT_MS, TimeUnit.MILLISECONDS
        )
        return try {
            val response = YoutubeDL.execute(request, processId, false, null)
            val line = response.out.lineSequence().firstOrNull { it.trimStart().startsWith("{") }
            if (line == null) {
                Log.w(TAG, "$videoId: no JSON from yt-dlp. ${response.err.takeLast(600)}")
                return null
            }
            val json = JsonParser.parseString(line).asJsonObject
            fun field(name: String) = json.get(name)?.takeUnless { it.isJsonNull }
            val url = field("url")?.asString ?: return null
            val userAgent = json.getAsJsonObject("http_headers")?.get("User-Agent")?.asString
            val ext = field("ext")?.asString
            val media = if (kind == Kind.VIDEO) "video" else "audio"
            val mimeType = when (ext) {
                "m4a", "mp4" -> "$media/mp4"
                "webm" -> "$media/webm"
                else -> null
            }
            val length = (field("filesize") ?: field("filesize_approx"))?.asLong ?: -1L
            // A video's sound comes with the same answer: every format is listed, deciphered.
            val audioUrl = if (kind != Kind.VIDEO) null else json.getAsJsonArray("formats")
                ?.map { it.asJsonObject }
                ?.filter { format ->
                    format.get("vcodec")?.takeUnless { it.isJsonNull }?.asString == "none" &&
                        format.get("acodec")?.takeUnless { it.isJsonNull }?.asString.let { it != null && it != "none" } &&
                        format.get("protocol")?.takeUnless { it.isJsonNull }?.asString == "https"
                }
                ?.let { audio -> audio.firstOrNull { it.get("format_id")?.asString == "140" } ?: audio.firstOrNull() }
                ?.get("url")?.asString
            Log.i(TAG, "$videoId: $media ${field("format_id")?.asString} ($ext) " +
                "in ${response.elapsedTime} ms, signed in: ${cookies != null}")
            if (userAgent != null) {
                YouTubeStreams.Stream(url, mimeType, length, userAgent, audioUrl)
            } else {
                YouTubeStreams.Stream(url, mimeType, length, audioUrl = audioUrl)
            }
        } catch (e: YoutubeDL.CanceledException) {
            Log.w(TAG, "$videoId: yt-dlp gave no answer in ${RUN_TIMEOUT_MS / 1000} s")
            null
        } catch (e: Exception) {
            // yt-dlp's stderr: the reason is in its last lines.
            Log.w(TAG, "$videoId: ${e.message?.trim()?.lines()?.takeLast(4)?.joinToString(" | ") ?: e}")
            null
        } finally {
            timeout.cancel(false)
            cookies?.delete()
        }
    }

    /**
     * The session's cookies in the Netscape format yt-dlp reads. The app keeps them as one
     * `Cookie` header taken from music.youtube.com, without domains; they are all youtube.com's.
     */
    private fun writeCookies(file: File, auth: YtAuth): File {
        val expires = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + TimeUnit.DAYS.toSeconds(365)
        val lines = auth.cookie.split(';').mapNotNull { part ->
            val name = part.substringBefore('=').trim()
            val value = part.substringAfter('=', "").trim()
            if (name.isEmpty()) null else ".youtube.com\tTRUE\t/\tTRUE\t$expires\t$name\t$value"
        }
        file.writeText("# Netscape HTTP Cookie File\n" + lines.joinToString("\n") + "\n")
        return file
    }

    private fun runDir(context: Context) = File(context.noBackupFilesDir, "yt-dlp-run")

    private fun cacheDir(context: Context) = File(runDir(context), "cache")

    private fun pluginDir(context: Context) = File(runDir(context), "plugins")

    private fun installPlugin(context: Context) {
        // yt-dlp takes each folder in a plugin directory as a package root.
        val file = File(pluginDir(context), "youcloud/yt_dlp_plugins/extractor/youcloud_ejs_cache.py")
        if (file.exists() && file.readText() == PLUGIN) return
        file.parentFile?.mkdirs()
        file.writeText(PLUGIN)
    }

    /**
     * Drops what belongs to players YouTube has moved on from, never asked for again: all but the
     * newest preprocessed players, and anything else not written in weeks.
     */
    private fun pruneCache(context: Context) {
        File(cacheDir(context), "challenge-solver").listFiles { file -> file.name.startsWith("player") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(PLAYERS_KEPT)
            ?.forEach { it.delete() }
        val cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_MS
        cacheDir(context).walkBottomUp()
            .filter { it.isFile && it.lastModified() < cutoff }
            .forEach { it.delete() }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
