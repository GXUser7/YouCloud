package com.example.myapplication.data

import android.net.Uri
import android.util.Base64
import com.example.myapplication.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.coroutineContext

internal const val SPOTIFY_REDIRECT = "http://127.0.0.1/callback"
internal const val SPOTIFY_ACCOUNTS = "https://accounts.spotify.com"
private const val CALLBACK_PATH = "/callback"

internal class SpotifyFailure(message: String, val status: Int? = null) : IOException(message)

internal class SpotifyCode(val code: String, val verifier: String, val redirect: String)

/** Authorization code with PKCE in the system browser. A one-time listener binds to loopback only. */
internal suspend fun spotifyAuthorize(
    clientId: String,
    openBrowser: suspend (Uri) -> Unit
): SpotifyCode = withContext(Dispatchers.IO) {
    val verifier = randomUrlToken()
    val state = randomUrlToken()
    val challenge = urlBase64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    ServerSocket().use { server ->
        server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        server.soTimeout = 750
        val redirect = "http://127.0.0.1:${server.localPort}$CALLBACK_PATH"
        val authorization = Uri.parse("$SPOTIFY_ACCOUNTS/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("scope", "user-read-private user-library-read user-library-modify user-follow-read user-follow-modify playlist-read-private playlist-read-collaborative user-read-recently-played user-top-read")
            .appendQueryParameter("redirect_uri", redirect)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .build()
        openBrowser(authorization)
        val deadline = android.os.SystemClock.elapsedRealtime() + 5 * 60_000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            coroutineContext.ensureActive()
            val peer = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            }
            val result = peer.use { socket ->
                socket.soTimeout = 1000
                try {
                    finishSpotifyCallback(socket, state)
                } catch (_: IOException) {
                    null
                }
            } ?: continue
            val code = result.getQueryParameter("code")?.takeIf { it.isNotBlank() }
                ?: throw SpotifyFailure(tr("Вход в Spotify отменён."))
            return@withContext SpotifyCode(code, verifier, redirect)
        }
        throw SpotifyFailure(tr("Время ожидания входа в Spotify истекло."))
    }
}

private fun finishSpotifyCallback(socket: Socket, state: String): Uri? {
    val input = socket.getInputStream().buffered()
    var remaining = 16_384
    val deadline = android.os.SystemClock.elapsedRealtime() + 1500L
    fun readLine(): String? {
        val line = StringBuilder()
        while (remaining-- > 0 && android.os.SystemClock.elapsedRealtime() < deadline) {
            when (val byte = input.read()) {
                -1 -> return null
                '\n'.code -> return line.toString().trimEnd('\r')
                else -> line.append(byte.toChar())
            }
        }
        return null
    }
    val request = readLine() ?: return null
    while ((readLine() ?: return null).isNotEmpty()) { /* Drain bounded request headers. */ }
    val target = request.takeIf { it.startsWith("GET $CALLBACK_PATH?") }
        ?.removePrefix("GET ")?.substringBefore(' ') ?: return null
    val callback = Uri.parse(target).takeIf {
        it.encodedPath == CALLBACK_PATH && it.getQueryParameter("state") == state
    }
    val accepted = callback?.getQueryParameter("code")?.isNotBlank() == true
    val status = if (callback != null) "200 OK" else "404 Not Found"
    val message = if (accepted) "You can return to YouCloud." else "Spotify authorization was not completed."
    val body = message.toByteArray(Charsets.UTF_8)
    val headers = "HTTP/1.1 $status\r\nContent-Type: text/plain; charset=utf-8\r\n" +
        "Content-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n"
    socket.getOutputStream().apply {
        write(headers.toByteArray(Charsets.US_ASCII))
        write(body)
        flush()
    }
    return callback
}

private fun randomUrlToken(): String = urlBase64(ByteArray(32).also(SecureRandom()::nextBytes))

private fun urlBase64(bytes: ByteArray): String =
    Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
