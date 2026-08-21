package com.example.myapplication

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.player.AutoLibrary
import com.example.myapplication.player.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Connects to [PlaybackService] the same way Android Auto does — as a MediaBrowser — and walks the
 * tree it serves. Without a car or the Desktop Head Unit this is the only way to catch the failure
 * that matters: a library session that connects but hands back nothing, which shows up in the car
 * as an app that opens to an empty list.
 *
 * WARNING: `connectedAndroidTest` uninstalls the app when the run finishes, and uninstalling wipes
 * /data/data — saved tracks, downloaded audio, playlists and API tokens all go with it. Never run
 * this against a phone whose library matters. Use a spare device or an emulator.
 *
 * MediaBrowser insists on being driven from the application thread, so every call is dispatched
 * there and only the resulting future is awaited from the test thread.
 */
@RunWith(AndroidJUnit4::class)
class AutoBrowseTreeTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun <T> awaitOnMain(block: () -> ListenableFuture<T>): T {
        var future: ListenableFuture<T>? = null
        instrumentation.runOnMainSync { future = block() }
        return future!!.get(20, TimeUnit.SECONDS)
    }

    private fun connect(): MediaBrowser {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        return awaitOnMain { MediaBrowser.Builder(context, token).buildAsync() }
    }

    private fun release(browser: MediaBrowser) = instrumentation.runOnMainSync { browser.release() }

    @Test
    fun rootExposesTheExpectedCategories() {
        val browser = connect()
        try {
            val root = awaitOnMain { browser.getLibraryRoot(null) }
            assertEquals("library root request failed", 0, root.resultCode)
            val rootItem = root.value
            assertNotNull("root item missing", rootItem)
            assertEquals(AutoLibrary.ROOT_ID, rootItem!!.mediaId)

            val children = awaitOnMain { browser.getChildren(rootItem.mediaId, 0, 50, null) }
            assertEquals("root children request failed", 0, children.resultCode)
            val ids = children.value.orEmpty().map { it.mediaId }
            assertTrue(
                "root is missing categories, got $ids",
                ids.containsAll(
                    listOf(AutoLibrary.NODE_DOWNLOADS, AutoLibrary.NODE_FAVORITES, AutoLibrary.NODE_PLAYLISTS)
                )
            )
            children.value.orEmpty().forEach {
                assertTrue("${it.mediaId} must be browsable", it.mediaMetadata.isBrowsable == true)
            }
        } finally {
            release(browser)
        }
    }

    @Test
    fun categoriesResolveAndTracksArePlayable() {
        val browser = connect()
        try {
            var playableSeen = 0
            listOf(AutoLibrary.NODE_DOWNLOADS, AutoLibrary.NODE_FAVORITES, AutoLibrary.NODE_PLAYLISTS)
                .forEach { node ->
                    val result = awaitOnMain { browser.getChildren(node, 0, 200, null) }
                    assertEquals("children request failed for $node", 0, result.resultCode)
                    result.value.orEmpty().forEach { item ->
                        val meta = item.mediaMetadata
                        assertTrue(
                            "$node child ${item.mediaId} is neither playable nor browsable",
                            meta.isPlayable == true || meta.isBrowsable == true
                        )
                        if (meta.isPlayable == true) {
                            playableSeen++
                            assertNotNull("playable item has no title", meta.title)
                            assertNotNull(
                                "playable id ${item.mediaId} is not a track id",
                                item.mediaId.toLongOrNull()
                            )
                        }
                    }
                }
            // The device under test has a real library, so an entirely empty tree means the
            // repositories were not reachable from the service rather than "nothing saved yet".
            assertTrue("no playable tracks anywhere in the tree", playableSeen > 0)
        } finally {
            release(browser)
        }
    }
}
