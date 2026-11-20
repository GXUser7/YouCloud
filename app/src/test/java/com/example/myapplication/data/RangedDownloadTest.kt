package com.example.myapplication.data

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class RangedDownloadTest {
    private lateinit var tempDir: File
    private val servers = mutableListOf<HttpServer>()

    @Before
    fun setUp() {
        RangedDownload.clearFailedHosts()
        tempDir = Files.createTempDirectory("ranged-download-test").toFile()
    }

    @After
    fun tearDown() {
        servers.forEach { it.stop(0) }
        servers.clear()
        tempDir.deleteRecursively()
        RangedDownload.clearFailedHosts()
    }

    @Test
    fun extractsClenMirrorsAndAdaptiveChunkSizes() {
        val url = "https://rr1---sn-aaa.googlevideo.com/videoplayback?expire=1999999999&itag=140&clen=3456789&mn=sn-aaa%2Csn-bbb%2Csn-ccc"
        assertEquals(3_456_789L, RangedDownload.extractClen(url))
        assertNull(RangedDownload.extractClen("https://example.com/audio.mp3"))

        val mirrors = RangedDownload.mirrorsOf(url)
        assertEquals(3, mirrors.size)
        assertTrue(mirrors[0].startsWith("https://rr1---sn-aaa.googlevideo.com/"))
        assertTrue(mirrors[1].startsWith("https://rr1---sn-bbb.googlevideo.com/"))
        assertTrue(mirrors[2].startsWith("https://rr1---sn-ccc.googlevideo.com/"))

        assertEquals(listOf("https://example.com/a.mp3"), RangedDownload.mirrorsOf("https://example.com/a.mp3"))

        // 3.2 MB track splits cleanly into 4 ~800 KB chunks (1 wave at t=0)
        assertEquals(800L * 1024L, RangedDownload.adaptiveChunkBytes(3_200L * 1024L))
        // Tiny file clamps at 512 KB
        assertEquals(512L * 1024L, RangedDownload.adaptiveChunkBytes(200L * 1024L))
        // Large 20 MB video clamps at 2 MB
        assertEquals(2L * 1024L * 1024L, RangedDownload.adaptiveChunkBytes(20L * 1024L * 1024L))
    }

    @Test
    fun downloadsInParallelFromZeroWhenClenIsKnown() = runBlocking {
        val payload = deterministicBytes(2_600_000)
        val inFlight = AtomicInteger(0)
        val peakInFlight = AtomicInteger(0)
        val server = startServer { exchange ->
            val current = inFlight.incrementAndGet()
            peakInFlight.accumulateAndGet(current, ::maxOf)
            try {
                // Brief pause lets sibling workers overlap so we can assert true t=0 concurrency.
                Thread.sleep(60)
                serveRange(exchange, payload)
            } finally {
                inFlight.decrementAndGet()
            }
        }
        val url = "http://127.0.0.1:${server.address.port}/audio?clen=${payload.size}"
        val target = File(tempDir, "parallel.m4a")
        var lastProgress = 0f

        val outcome = RangedDownload.toFileOutcome(
            url = url,
            headers = emptyMap(),
            target = target,
            onProgress = { lastProgress = it }
        )

        assertEquals(RangedDownload.Outcome.OK, outcome)
        assertArrayEquals(payload, target.readBytes())
        assertTrue("Expected concurrent chunk requests at t=0, got peak=$peakInFlight", peakInFlight.get() >= 2)
        assertEquals(1f, lastProgress, 0.001f)
    }

    @Test
    fun fallsBackToFull200ResponseEvenWhenClenWasGiven() = runBlocking {
        val payload = deterministicBytes(900_000)
        val server = startServer { exchange ->
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        val url = "http://127.0.0.1:${server.address.port}/full?clen=${payload.size}"
        val target = File(tempDir, "full200.m4a")

        val outcome = RangedDownload.toFileOutcome(url, emptyMap(), target)

        assertEquals(RangedDownload.Outcome.OK, outcome)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun failsOverPerChunkToHealthyMirrorAndDemotesBadHost() = runBlocking {
        val payload = deterministicBytes(2_200_000)
        val mirror1Offsets = CopyOnWriteArrayList<Long>()
        val mirror2Offsets = CopyOnWriteArrayList<Long>()

        // Mirror 1 serves only offset 0 and hard-fails all later chunks (simulating mid-transfer VPN hang/drop).
        val mirror1 = startServer { exchange ->
            val (start, _) = parseRange(exchange, payload.size)
            mirror1Offsets += start
            if (start == 0L) {
                serveRange(exchange, payload)
            } else {
                // Close connection abruptly to trigger an IOException and mark this host unhealthy.
                exchange.close()
            }
        }
        val mirror2 = startServer { exchange ->
            val (start, _) = parseRange(exchange, payload.size)
            mirror2Offsets += start
            serveRange(exchange, payload)
        }

        val url1 = "http://127.0.0.1:${mirror1.address.port}/audio?clen=${payload.size}"
        val url2 = "http://127.0.0.1:${mirror2.address.port}/audio?clen=${payload.size}"
        val target = File(tempDir, "failover.m4a")

        val outcome = RangedDownload.toFileOutcome(
            url = url1,
            headers = emptyMap(),
            target = target,
            mirrorsOverride = listOf(url1, url2)
        )

        assertEquals(RangedDownload.Outcome.OK, outcome)
        assertArrayEquals(payload, target.readBytes())
        assertTrue("Mirror 1 should have served chunk 0", 0L in mirror1Offsets)
        assertFalse("Mirror 2 should not have had to re-download chunk 0", 0L in mirror2Offsets)
        assertTrue("Mirror 2 should have picked up failed chunks", mirror2Offsets.isNotEmpty())

        // Bad host must now be demoted behind the healthy mirror for subsequent tracks.
        val reordered = RangedDownload.orderMirrors(listOf(url1, url2))
        assertEquals(listOf(url2, url1), reordered)
    }

    @Test
    fun reportsHttp403WithoutLeavingPartialFile() = runBlocking {
        val server = startServer { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        val url = "http://127.0.0.1:${server.address.port}/denied?clen=1048576"
        val target = File(tempDir, "denied.m4a")

        val outcome = RangedDownload.toFileOutcome(url, emptyMap(), target)

        assertEquals(RangedDownload.Outcome.REJECTED_403, outcome)
        assertFalse(target.exists())
    }

    @Test
    fun picksBestNonDrcDefaultItag140FromAndroidVrFormats() {
        val raw = JsonParser.parseString(
            """[
                {"itag": 251, "mimeType": "audio/webm; codecs=\"opus\"", "bitrate": 160000, "url": "https://a/251"},
                {"itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 130000, "isDrc": true, "url": "https://a/140-drc"},
                {"itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 129000, "audioTrack": {"audioIsDefault": false}, "url": "https://a/140-dub"},
                {"itag": 140, "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"", "bitrate": 128000, "audioTrack": {"audioIsDefault": true}, "url": "https://a/140-orig"}
            ]"""
        ).asJsonArray.map { it.asJsonObject }

        val picked = YouTubeStreams.pickBestVrAudio(raw)
        assertEquals("https://a/140-orig", picked?.get("url")?.asString)
    }

    private fun startServer(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange -> handler(exchange) }
        server.start()
        servers += server
        return server
    }

    private fun parseRange(exchange: HttpExchange, total: Int): Pair<Long, Long> {
        val header = exchange.requestHeaders.getFirst("Range").orEmpty()
        val match = Regex("""bytes=(\d+)-(\d*)""").matchEntire(header)
            ?: return 0L to (total - 1).toLong()
        val start = match.groupValues[1].toLong()
        val end = match.groupValues[2].toLongOrNull()?.coerceAtMost(total - 1L) ?: (total - 1L)
        return start to end
    }

    private fun serveRange(exchange: HttpExchange, payload: ByteArray) {
        val (start, end) = parseRange(exchange, payload.size)
        val length = (end - start + 1).toInt()
        exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${payload.size}")
        exchange.sendResponseHeaders(206, length.toLong())
        exchange.responseBody.use { out ->
            out.write(payload, start.toInt(), length)
        }
    }

    private fun deterministicBytes(size: Int): ByteArray = ByteArray(size) { i -> ((i * 31 + 17) and 0xFF).toByte() }
}
