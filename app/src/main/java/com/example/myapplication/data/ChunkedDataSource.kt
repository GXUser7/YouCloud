package com.example.myapplication.data

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import kotlin.math.min

/**
 * Reads a stream [chunkSize] bytes per request. googlevideo sends a range left open at the end at
 * about the pace the stream plays, which leaves a music video no margin and stalls it; a bounded
 * range comes at full speed. yt-dlp downloads YouTube in pieces for the same reason.
 */
@UnstableApi
class ChunkedDataSource(private val upstream: DataSource, private val chunkSize: Long) : DataSource {

    class Factory(private val upstream: DataSource.Factory, private val chunkSize: Long) : DataSource.Factory {
        override fun createDataSource(): DataSource = ChunkedDataSource(upstream.createDataSource(), chunkSize)
    }

    private var dataSpec: DataSpec? = null
    private var position = 0L
    // Where the read ends, exclusive; unknown until a server says how long the stream is.
    private var end = C.LENGTH_UNSET.toLong()
    private var chunkLeft = 0L
    private var upstreamOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        position = dataSpec.position
        end = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.position + dataSpec.length else C.LENGTH_UNSET.toLong()
        openChunk()
        if (end == C.LENGTH_UNSET.toLong()) {
            streamLength()?.let { end = it }
        }
        return if (end != C.LENGTH_UNSET.toLong()) end - position else C.LENGTH_UNSET.toLong()
    }

    private fun openChunk() {
        val spec = dataSpec ?: return
        val length = if (end != C.LENGTH_UNSET.toLong()) min(chunkSize, end - position) else chunkSize
        val opened = upstream.open(spec.buildUpon().setPosition(position).setLength(length).build())
        upstreamOpen = true
        chunkLeft = if (opened != C.LENGTH_UNSET.toLong()) opened else length
    }

    /** The whole stream's length: from the server's `Content-Range`, or googlevideo's `clen`. */
    private fun streamLength(): Long? =
        upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }
            ?.value?.firstOrNull()
            ?.substringAfter('/')?.toLongOrNull()
            ?: dataSpec?.uri?.getQueryParameter("clen")?.toLongOrNull()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (end != C.LENGTH_UNSET.toLong() && position >= end) return C.RESULT_END_OF_INPUT
        if (chunkLeft <= 0L) {
            upstream.close()
            upstreamOpen = false
            try {
                openChunk()
            } catch (e: HttpDataSource.InvalidResponseCodeException) {
                // Past the end of a stream whose length nobody gave.
                if (e.responseCode == 416 && end == C.LENGTH_UNSET.toLong()) return C.RESULT_END_OF_INPUT
                throw e
            }
        }
        val read = upstream.read(buffer, offset, min(length.toLong(), chunkLeft).toInt())
        if (read == C.RESULT_END_OF_INPUT) {
            chunkLeft = 0L
            if (end == C.LENGTH_UNSET.toLong() || position >= end) return C.RESULT_END_OF_INPUT
            return read(buffer, offset, length)
        }
        position += read
        chunkLeft -= read
        return read
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        if (upstreamOpen) {
            upstreamOpen = false
            upstream.close()
        }
        dataSpec = null
    }
}
