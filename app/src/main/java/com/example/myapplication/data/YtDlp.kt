package com.example.myapplication.data

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.thread
import kotlin.concurrent.withLock
import kotlin.concurrent.write

/**
 * yt-dlp, run on the phone. youtubedl-android bundles Python, yt-dlp and QuickJS — the JavaScript
 * engine yt-dlp solves the player's challenges with.
 *
 * yt-dlp is kept running between requests ([Server]): started afresh for each, it spent seconds
 * starting Python and fetching YouTube's player script again, every time. youtubedl-android's
 * one-off run is the fallback when a server can't be had.
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
     * What to ask yt-dlp for. Plain HTTPS only, never an HLS or DASH manifest — the players and
     * the downloader here expect one file — but for a live stream, which is only HLS.
     */
    enum class Kind(
        val format: String,
        val clients: String? = null,
        // The manifests yt-dlp doesn't fetch: both, for a file, as they are only one more request.
        val skip: String = "hls,dash"
    ) {
        /** AAC in MP4 (itag 140) first: every device decodes it, and downloads are kept as it. */
        AUDIO("140/bestaudio[ext=m4a][protocol=https]/bestaudio[protocol=https]"),

        /**
         * A music video's picture alone, no larger than the screen needs, VP9 first — where the
         * phone decodes it in hardware: [VideoDecoders.videoFormat] has the format asked for, this
         * is it on such a phone. YouTube's H.264 of a busy scene can be missing frames — a third
         * of them, in an anime opening — where its VP9 and the YouTube app are smooth. The clients
         * yt-dlp asks when signed in only hand out H.264 at that size; the full TV client has them
         * all. The pared-down one stays for when it won't answer, and the web clients are left
         * out: they need a player script of their own, seconds more, for no formats the TV ones
         * lack — but for accounts the TV clients turn away ([TV_REFUSED], [OTHER_VIDEO_CLIENTS]).
         */
        VIDEO(
            "bv[height<=720][vcodec^=vp][protocol=https]/bv[height<=720][vcodec^=avc1][protocol=https]/" +
                "bv[height<=720][protocol=https]/bv*[height<=720][protocol=https]",
            clients = "tv,tv_downgraded"
        ),

        /**
         * A live stream's sound: its audio-only HLS (itags 233, 234), else the smallest whole one.
         * A broadcast is only HLS, so that manifest is fetched; the web client hands it out, where
         * the ones asked for files have none.
         */
        LIVE_AUDIO("bestaudio[protocol^=m3u8]/234/233/worst[protocol^=m3u8]", clients = "web_safari,tv,mweb", skip = "dash"),

        /** A live stream's picture, for the player to show: HLS no larger than it needs. */
        LIVE_VIDEO("best[height<=720][protocol^=m3u8]/94/93/best[protocol^=m3u8]", clients = "web_safari,tv,mweb", skip = "dash")
    }

    /**
     * What the TV clients answer some signed-in accounts since September 2026, a test of
     * YouTube's: every video "UNPLAYABLE" (yt-dlp issue 17389). The sound is had all the same —
     * yt-dlp's own clients are asked for it — but a video asked of the TV clients alone never came.
     */
    private const val TV_REFUSED = "The page needs to be reloaded"

    /**
     * A video's clients for such an account, as yt-dlp's maintainers advise for it: yt-dlp's own
     * and the embedded player's, which needs no PO token. H.264 more often than VP9, but a clip.
     */
    private const val OTHER_VIDEO_CLIENTS = "default,web_embedded"

    // The session (a hash of its cookies) the TV clients turned away: its videos are asked of the
    // others straight away, not a refusal first each time. Until the app is restarted, as YouTube's
    // tests end.
    @Volatile
    private var tvRefusedSession: Int? = null

    /**
     * A yt-dlp plugin that keeps the challenge solver's preprocessed player between runs. Solving
     * a challenge starts with QuickJS parsing YouTube's whole player script: six seconds on a
     * phone, every track, against under one from the cache. yt-dlp leaves that cache off for its
     * size, 4 MB a player, which [pruneCache] keeps in check.
     */
    private const val PLUGIN = """from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP

EJSBaseJCP._ENABLE_PREPROCESSED_PLAYER_CACHE = True
"""

    /**
     * The server: yt-dlp as a library, taking one request a line on stdin — a URL and a format —
     * and answering each with a line of JSON on stdout. The format selector is built with the
     * instance, so each request's is swapped in.
     */
    private const val SERVER = """import json
import sys

sys.path.insert(0, sys.argv[1])

import yt_dlp  # noqa: E402

try:
    from yt_dlp.extractor.youtube.jsc._builtin.ejs import EJSBaseJCP
    EJSBaseJCP._ENABLE_PREPROCESSED_PLAYER_CACHE = True
except Exception:
    pass

KEEP = ("url", "ext", "format_id", "filesize", "filesize_approx", "http_headers", "vcodec", "height", "fps")
YOUTUBE_ARGS = "youtube"
FORMAT_KEEP = ("format_id", "url", "vcodec", "acodec", "protocol", "abr", "height", "fps")


def main():
    opts = yt_dlp.parse_options(json.loads(sys.argv[2])).ydl_opts
    # An extraction that fails raises, its reason in the answer, rather than giving None.
    opts["ignoreerrors"] = False
    ydl = yt_dlp.YoutubeDL(opts)
    base_args = dict(ydl.params.get("extractor_args") or {})
    # The format selector is built once, with the instance; each request brings its own.
    selectors = {}
    print(json.dumps({"ready": True}), flush=True)
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        request = json.loads(line)
        try:
            wanted = request["format"]
            if wanted not in selectors:
                selectors[wanted] = ydl.build_format_selector(wanted)
            ydl.params["format"] = wanted
            ydl.format_selector = selectors[wanted]
            # The clients to ask, per request too: the extractor reads its arguments afresh.
            youtube_args = dict(base_args.get(YOUTUBE_ARGS) or {})
            if request.get("clients"):
                youtube_args["player_client"] = request["clients"].split(",")
            # The manifests not to fetch, per request: a live stream is only HLS.
            if "skip" in request:
                youtube_args["skip"] = [part for part in request["skip"].split(",") if part]
            ydl.params["extractor_args"] = {**base_args, YOUTUBE_ARGS: youtube_args}
            info = ydl.extract_info(request["url"], download=False)
            if info is None:
                raise Exception("yt-dlp found nothing")
            reply = {key: info.get(key) for key in KEEP}
            reply["formats"] = [{key: f.get(key) for key in FORMAT_KEEP} for f in info.get("formats") or []]
            print(json.dumps({"id": request.get("id"), "info": reply}), flush=True)
        except Exception as e:  # one failed request must not end the server
            print(json.dumps({"id": request.get("id"), "error": str(e)}), flush=True)


main()
"""

    private val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(1)
    private val SERVER_START_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(40)
    private val SERVER_IDLE_MS = TimeUnit.MINUTES.toMillis(10)

    // Any long-lived video will do to warm a server up.
    private const val WARM_UP_VIDEO = "jNQXAC9IVRw"
    private val CACHE_MAX_AGE_MS = TimeUnit.DAYS.toMillis(21)
    private const val PLAYERS_KEPT = 3
    private val FAILED_UPDATE_RETRY_MS = TimeUnit.MINUTES.toMillis(30)
    private val RUN_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(90)

    @Volatile
    private var initialised = false

    // An update replaces the script in place, and Python reads it lazily while it runs.
    private val scriptLock = ReentrantReadWriteLock()

    /**
     * A stream being resolved, and whether someone wants it now: one fetched ahead (the next
     * track's) that the player then asks for itself goes as wanted now.
     */
    private class Pending(val task: FutureTask<YouTubeStreams.Stream?>, val wantedNow: AtomicBoolean)

    private val pending = ConcurrentHashMap<String, Pending>()
    private val lastUpdateAttempt = AtomicLong(0)
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "yt-dlp watchdog").apply { isDaemon = true }
    }

    /**
     * Unpacks Python, brings yt-dlp up to date and starts a server for [auth]'s session ahead of
     * the first track, off the caller's thread.
     */
    fun warmUp(context: Context, auth: YtAuth?) {
        val app = context.applicationContext
        thread(name = "yt-dlp warm-up", isDaemon = true) {
            if (!ensureReady(app)) return@thread
            // Only with nothing else to do: a track asked for warms yt-dlp up itself.
            if (!Runs.acquireWarmUp()) return@thread
            try {
                scriptLock.read {
                    synchronized(idleServers) { if (idleServers.isNotEmpty()) return@read }
                    val server = startServer(app, auth) ?: return@read
                    // A first request fetches YouTube's player script and solves its challenges from
                    // scratch; made now, it spares the first track that wait.
                    release(server)
                    askServer(app, WARM_UP_VIDEO, auth, Kind.AUDIO)
                }
            } finally {
                Runs.releaseWarmUp()
            }
        }
    }

    /**
     * The [kind] of stream of [videoId], or null when yt-dlp couldn't get one. Blocking — a Python
     * process and several round trips, seconds on a phone; call it off the main thread. Asking
     * again for a stream already being resolved waits for that run instead of starting another.
     *
     * [urgent]: whether it is wanted now (the track playing, its video) rather than fetched ahead
     * (the next track, a download), asked again while it waits its turn; see [Runs].
     */
    fun resolve(
        context: Context,
        videoId: String,
        auth: YtAuth?,
        kind: Kind,
        urgent: () -> Boolean = { true }
    ): YouTubeStreams.Stream? {
        val app = context.applicationContext
        val key = "$kind:$videoId"
        val wantedNow = AtomicBoolean(false)
        val mine = Pending(FutureTask { resolveOrUpdate(app, videoId, auth, kind) { wantedNow.get() || urgent() } }, wantedNow)
        val running = pending.putIfAbsent(key, mine)
        if (running == null) {
            try {
                mine.task.run()
            } finally {
                pending.remove(key, mine)
            }
        } else if (urgent()) {
            // Fetched ahead, and wanted now: it goes before what is still only fetched ahead.
            running.wantedNow.set(true)
        }
        return try {
            (running ?: mine).task.get()
        } catch (e: ExecutionException) {
            Log.w(TAG, "$videoId: ${e.cause}")
            null
        }
    }

    private fun resolveOrUpdate(context: Context, videoId: String, auth: YtAuth?, kind: Kind, urgent: () -> Boolean): YouTubeStreams.Stream? {
        if (!ensureReady(context)) return null
        extract(context, videoId, auth, kind, urgent)?.let { return it }
        // Most failures are YouTube having changed something a newer yt-dlp already handles.
        if (!update(context, minInterval = FAILED_UPDATE_RETRY_MS)) return null
        return extract(context, videoId, auth, kind, urgent)
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
                // Servers have the old script open, and Python reads it lazily.
                stopServers()
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

    private fun extract(context: Context, videoId: String, auth: YtAuth?, kind: Kind, urgent: () -> Boolean): YouTubeStreams.Stream? {
        val now = Runs.acquire(urgent)
        try {
            return scriptLock.read { runYtDlp(context, videoId, auth, kind) }
        } finally {
            Runs.release(ahead = !now)
        }
    }

    /**
     * yt-dlp's runs, two at a time — each a Python process of its own, tens of MB, and as much work
     * as a phone's cores take — given to what is wanted now before what is fetched ahead. What is
     * fetched ahead (the next tracks, downloads, the warm-up) takes one of the two at most, and none
     * while something wanted now waits. Queued behind the next tracks' sound, the playing track's
     * video waited its turn for seconds on a fast phone; on a slower one, for most of the track.
     */
    private object Runs {
        private const val ALL = 2
        private const val AHEAD = 1
        private val lock = ReentrantLock()
        private val freed = lock.newCondition()
        private var busy = 0
        private var busyAhead = 0
        private val waiting = mutableListOf<() -> Boolean>()

        /** Waits for a run of its own; true when it was given as wanted now. */
        fun acquire(urgent: () -> Boolean): Boolean = lock.withLock {
            waiting += urgent
            try {
                var now = urgent()
                while (!(busy < ALL && (now || (busyAhead < AHEAD && waiting.none { it !== urgent && it() })))) {
                    // Asked again now and then: the next track may have become the one playing.
                    freed.await(250, TimeUnit.MILLISECONDS)
                    now = urgent()
                }
                busy++
                if (!now) busyAhead++
                now
            } finally {
                waiting.remove(urgent)
            }
        }

        fun release(ahead: Boolean) = lock.withLock {
            busy--
            if (ahead) busyAhead--
            freed.signalAll()
        }

        /**
         * The warm-up's turn, when nothing else runs or waits: in the lane of what is fetched
         * ahead, but none of the two runs — a track asked for while it runs has both. Held, a run
         * kept the first track's video waiting on the warm-up at every start.
         */
        fun acquireWarmUp(): Boolean = lock.withLock {
            if (busy > 0 || busyAhead >= AHEAD || waiting.isNotEmpty()) {
                false
            } else {
                busyAhead++
                true
            }
        }

        fun releaseWarmUp() = lock.withLock {
            busyAhead--
            freed.signalAll()
        }
    }

    /** What to ask yt-dlp for: a video in the codec this phone decodes well ([VideoDecoders]). */
    private fun formatOf(context: Context, kind: Kind): String =
        if (kind == Kind.VIDEO) VideoDecoders.videoFormat(context) else kind.format

    private fun runYtDlp(context: Context, videoId: String, auth: YtAuth?, kind: Kind): YouTubeStreams.Stream? {
        if (kind != Kind.VIDEO) return runYtDlp(context, videoId, auth, kind, kind.clients).stream
        val session = auth?.cookie.hashCode()
        if (tvRefusedSession != session) {
            val tv = runYtDlp(context, videoId, auth, kind, kind.clients)
            if (tv.stream != null || tv.failure?.contains(TV_REFUSED) != true) return tv.stream
            Log.i(TAG, "The TV clients turn this account away; its videos are asked of $OTHER_VIDEO_CLIENTS")
            tvRefusedSession = session
        }
        return runYtDlp(context, videoId, auth, kind, OTHER_VIDEO_CLIENTS).stream
    }

    /** A run's stream, or why there is none, as yt-dlp put it. */
    private class Attempt(val stream: YouTubeStreams.Stream?, val failure: String? = null)

    private fun runYtDlp(context: Context, videoId: String, auth: YtAuth?, kind: Kind, clients: String?): Attempt {
        when (val answer = askServer(context, videoId, auth, kind, clients)) {
            is ServerAnswer.Found -> return Attempt(answer.stream)
            is ServerAnswer.Failed -> return Attempt(null, answer.reason)
            ServerAnswer.Unavailable -> Unit
        }
        val dir = runDir(context).apply { mkdirs() }
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId")
            .addOption("--dump-json")
            .addOption("--no-playlist")
            .addOption("-f", formatOf(context, kind))
            .addOption("--socket-timeout", 15)
            // Keeps the player script and its solved challenges between runs; the library
            // otherwise turns the cache off.
            .addOption("--cache-dir", cacheDir(context).absolutePath)
            .addOption("--plugin-dirs", pluginDir(context).absolutePath)
            // Only the plain audio files are of use; the HLS manifest is one more request.
            .addOption("--extractor-args", "youtube:skip=${kind.skip}" + clients?.let { ";player_client=$it" }.orEmpty())
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
                return Attempt(null, response.err)
            }
            Attempt(streamFrom(JsonParser.parseString(line).asJsonObject, kind)?.also { stream ->
                Log.i(TAG, "$videoId: ${kind.name.lowercase()} ${stream.mimeType} in ${response.elapsedTime} ms (one-off run), " +
                    "signed in: ${cookies != null}")
            })
        } catch (e: YoutubeDL.CanceledException) {
            Log.w(TAG, "$videoId: yt-dlp gave no answer in ${RUN_TIMEOUT_MS / 1000} s")
            Attempt(null)
        } catch (e: Exception) {
            // yt-dlp's stderr: the reason is in its last lines.
            Log.w(TAG, "$videoId: ${e.message?.trim()?.lines()?.takeLast(4)?.joinToString(" | ") ?: e}")
            Attempt(null, e.message)
        } finally {
            timeout.cancel(false)
            cookies?.delete()
        }
    }

    // A video format in a word, for the log: "136 avc1.4d401f 720p24".
    private fun describe(format: JsonObject): String {
        fun field(name: String) = format.get(name)?.takeUnless { it.isJsonNull }?.asString
        return "${field("format_id")} ${field("vcodec")} ${field("height")}p${field("fps")}"
    }

    private fun videoFormats(info: JsonObject): String =
        info.getAsJsonArray("formats")
            ?.map { it.asJsonObject }
            ?.filter { it.get("vcodec")?.takeUnless { v -> v.isJsonNull }?.asString.let { v -> v != null && v != "none" } }
            ?.joinToString(", ") { describe(it) }
            .orEmpty()

    /** yt-dlp's answer for one format, as a stream: `--dump-json`'s, or a server's. */
    private fun streamFrom(json: JsonObject, kind: Kind): YouTubeStreams.Stream? {
        fun field(name: String) = json.get(name)?.takeUnless { it.isJsonNull }
        val url = field("url")?.asString ?: return null
        val userAgent = json.getAsJsonObject("http_headers")?.get("User-Agent")?.asString
        val media = if (kind == Kind.VIDEO || kind == Kind.LIVE_VIDEO) "video" else "audio"
        val mimeType = if (field("protocol")?.asString?.startsWith("m3u8") == true) "application/x-mpegURL" else when (field("ext")?.asString) {
            "m4a", "mp4" -> "$media/mp4"
            "webm" -> "$media/webm"
            else -> null
        }
        val length = (field("filesize") ?: field("filesize_approx"))?.asLong ?: -1L
        val codec = field("vcodec")?.asString?.takeUnless { it == "none" }
        // A video's sound comes with the same answer: every format is listed, deciphered. It is
        // only analysed ([ClipAligner]), so the smallest is tried first: over a VPN the download,
        // not the decoding, is what takes the time. The others are there for when a server won't
        // hand one out.
        val audioUrls = if (kind != Kind.VIDEO) emptyList() else json.getAsJsonArray("formats")
            ?.map { it.asJsonObject }
            ?.filter { format ->
                format.get("vcodec")?.takeUnless { it.isJsonNull }?.asString == "none" &&
                    format.get("acodec")?.takeUnless { it.isJsonNull }?.asString.let { it != null && it != "none" } &&
                    format.get("protocol")?.takeUnless { it.isJsonNull }?.asString == "https"
            }
            ?.sortedBy { it.get("abr")?.takeUnless { abr -> abr.isJsonNull }?.asDouble ?: Double.MAX_VALUE }
            ?.mapNotNull { it.get("url")?.takeUnless { url -> url.isJsonNull }?.asString }
            .orEmpty()
        return if (userAgent != null) {
            YouTubeStreams.Stream(url, mimeType, length, userAgent, audioUrls, codec)
        } else {
            YouTubeStreams.Stream(url, mimeType, length, audioUrls = audioUrls, codec = codec)
        }
    }

    // ------------------------------------------------------------------------------ servers

    /** A running yt-dlp, signed in as [session] (a hash of the cookies it was started with). */
    private class Server(
        val process: Process,
        val input: java.io.BufferedWriter,
        val output: java.io.BufferedReader,
        val session: Int,
        val cookies: File?
    ) {
        var lastUsed = System.currentTimeMillis()

        fun stop() {
            process.destroy()
            cookies?.delete()
        }
    }

    private sealed interface ServerAnswer {
        class Found(val stream: YouTubeStreams.Stream) : ServerAnswer

        /** yt-dlp ran and found nothing, for [reason]: a one-off run would do no better. */
        class Failed(val reason: String) : ServerAnswer

        /** No server could be had, or it died: worth a one-off run instead. */
        object Unavailable : ServerAnswer
    }

    // Servers between requests; at most [runs]' two, as each request holds one.
    private val idleServers = ArrayDeque<Server>()

    init {
        watchdog.scheduleWithFixedDelay({
            val now = System.currentTimeMillis()
            synchronized(idleServers) {
                idleServers.filter { now - it.lastUsed > SERVER_IDLE_MS || !it.process.isAlive }.forEach {
                    it.stop()
                    idleServers.remove(it)
                }
            }
        }, 1, 1, TimeUnit.MINUTES)
    }

    private fun askServer(context: Context, videoId: String, auth: YtAuth?, kind: Kind, clients: String? = kind.clients): ServerAnswer {
        val server = borrow(context, auth) ?: return ServerAnswer.Unavailable
        val started = System.currentTimeMillis()
        val id = "${System.nanoTime()}"
        val request = JsonObject().apply {
            addProperty("id", id)
            addProperty("url", "https://www.youtube.com/watch?v=$videoId")
            addProperty("format", formatOf(context, kind))
            clients?.let { addProperty("clients", it) }
            addProperty("skip", kind.skip)
        }
        // A request that hangs takes its server with it.
        val timeout = watchdog.schedule({ server.stop() }, RUN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        val line = try {
            server.input.write(request.toString())
            server.input.newLine()
            server.input.flush()
            // This request's answer: anything else on stdout (yt-dlp printing a message there,
            // an answer left over from a request that timed out) is passed over.
            generateSequence { server.output.readLine() }.firstOrNull { reply ->
                reply.startsWith("{") && runCatching {
                    JsonParser.parseString(reply).asJsonObject.get("id")?.asString == id
                }.getOrDefault(false)
            }
        } catch (e: java.io.IOException) {
            null
        } finally {
            timeout.cancel(false)
        }
        if (line == null) {
            Log.w(TAG, "$videoId: the yt-dlp server died")
            server.stop()
            return ServerAnswer.Unavailable
        }
        release(server)
        val reply = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()
            ?: return ServerAnswer.Unavailable
        reply.get("error")?.takeUnless { it.isJsonNull }?.let { error ->
            Log.w(TAG, "$videoId: ${error.asString.trim().lines().takeLast(3).joinToString(" | ")}")
            return ServerAnswer.Failed(error.asString)
        }
        val info = reply.getAsJsonObject("info")
        val stream = info?.let { streamFrom(it, kind) } ?: return ServerAnswer.Failed("no stream in the answer")
        Log.i(TAG, "$videoId: ${kind.name.lowercase()} ${stream.mimeType} in ${System.currentTimeMillis() - started} ms, " +
            "signed in: ${auth != null}")
        if (kind == Kind.VIDEO) Log.d(TAG, "$videoId: picked ${describe(info)}; had ${videoFormats(info)}")
        return ServerAnswer.Found(stream)
    }

    private fun borrow(context: Context, auth: YtAuth?): Server? {
        val session = auth?.cookie.hashCode()
        synchronized(idleServers) {
            // Signed in as someone else by now, or gone: of no more use.
            idleServers.filter { it.session != session || !it.process.isAlive }.forEach {
                it.stop()
                idleServers.remove(it)
            }
            idleServers.removeFirstOrNull()?.let { return it }
        }
        return startServer(context, auth)
    }

    private fun release(server: Server) {
        if (!server.process.isAlive) {
            server.stop()
            return
        }
        server.lastUsed = System.currentTimeMillis()
        synchronized(idleServers) { idleServers.addLast(server) }
    }

    private fun stopServers() {
        synchronized(idleServers) {
            idleServers.forEach { it.stop() }
            idleServers.clear()
        }
    }

    /**
     * Starts yt-dlp as a server, in the environment youtubedl-android runs it in: its Python,
     * unpacked under no-backup files, its QuickJS beside the app's native libraries.
     */
    private fun startServer(context: Context, auth: YtAuth?): Server? {
        val started = System.currentTimeMillis()
        val dir = runDir(context).apply { mkdirs() }
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val base = File(context.noBackupFilesDir, YoutubeDL.baseName)
        val pythonHome = File(base, "packages/python/usr")
        val ytDlp = File(base, "${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")
        if (!ytDlp.exists() || !pythonHome.exists()) return null
        val script = File(dir, "server.py")
        if (!script.exists() || script.readText() != SERVER) script.writeText(SERVER)
        val cookies = auth?.let { writeCookies(File(dir, "cookies-server-${System.nanoTime()}.txt"), it) }
        val args = com.google.gson.JsonArray().apply {
            listOfNotNull(
                "--no-playlist",
                // stdout carries the answers; yt-dlp's own messages would be mixed into them.
                "--quiet",
                "--socket-timeout", "15",
                "--cache-dir", cacheDir(context).absolutePath,
                "--extractor-args", "youtube:skip=hls,dash",
                "--js-runtimes", "quickjs:$nativeDir/libqjs.so",
                cookies?.let { "--cookies" }, cookies?.absolutePath
            ).forEach(::add)
        }
        return try {
            val process = ProcessBuilder(File(nativeDir, "libpython.so").absolutePath, script.absolutePath, ytDlp.absolutePath, args.toString())
                .apply {
                    environment()["LD_LIBRARY_PATH"] = "${pythonHome.absolutePath}/lib"
                    environment()["SSL_CERT_FILE"] = "${pythonHome.absolutePath}/etc/tls/cert.pem"
                    environment()["PYTHONHOME"] = pythonHome.absolutePath
                    environment()["HOME"] = pythonHome.absolutePath
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                    environment()["PATH"] = System.getenv("PATH") + ":" + nativeDir
                }
                .start()
            // Drained, or a full pipe would stall the server; warnings are worth keeping.
            thread(name = "yt-dlp server stderr", isDaemon = true) {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        if ("ERROR" in line || "Traceback" in line || "Error" in line) Log.w(TAG, "server: $line")
                    }
                }
            }
            val server = Server(
                process = process,
                input = process.outputStream.bufferedWriter(),
                output = process.inputStream.bufferedReader(),
                session = auth?.cookie.hashCode(),
                cookies = cookies
            )
            val timeout = watchdog.schedule({ server.stop() }, SERVER_START_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val ready = try {
                server.output.readLine()
            } finally {
                timeout.cancel(false)
            }
            if (ready == null || "ready" !in ready) {
                Log.w(TAG, "The yt-dlp server didn't start: $ready")
                server.stop()
                null
            } else {
                Log.i(TAG, "yt-dlp server up in ${System.currentTimeMillis() - started} ms")
                server
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start a yt-dlp server: $e")
            cookies?.delete()
            null
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
