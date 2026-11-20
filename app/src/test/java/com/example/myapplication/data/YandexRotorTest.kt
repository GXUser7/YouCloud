package com.example.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YandexRotorTest {
    private fun rotorItem(id: String, available: Boolean? = true): YandexRotorItem =
        YandexRotorItem(
            type = "track",
            track = YandexTrack(
                id = id,
                title = "Track $id",
                artists = listOf(YandexArtist(id = "1", name = "Artist")),
                durationMs = 180_000L,
                coverUri = null,
                available = available
            )
        )

    @Test
    fun prefersFreshTracksAndSkipsUnavailableOnes() {
        val filter = YandexRotorFilter(heardBefore = listOf("100:1", "200:2"))
        val sequence = listOf(
            rotorItem("100"),
            rotorItem("300", available = false),
            rotorItem("400"),
            rotorItem("400"),
            rotorItem("500")
        )
        val fresh = filter.accept(sequence, allowHeardBefore = false)
        assertEquals(listOf("Track 400", "Track 500"), fresh.map { it.title })
    }

    @Test
    fun fallsBackToHeardBeforeOnNarrowFiltersWithoutRepeatingCurrentSession() {
        val filter = YandexRotorFilter(heardBefore = listOf("100:1", "200:2", "300:3"))
        filter.markGiven("100:1")
        val sequence = listOf(rotorItem("100"), rotorItem("200"), rotorItem("300"))

        assertTrue(filter.accept(sequence, allowHeardBefore = false).isEmpty())

        val fallback = filter.accept(sequence, allowHeardBefore = true)
        assertEquals(listOf("Track 200", "Track 300"), fallback.map { it.title })

        // Already queued in this session: never repeated immediately.
        assertTrue(filter.accept(sequence, allowHeardBefore = true).isEmpty())
    }

    @Test
    fun relaxesSessionHistoryOnlyAsLastResortKeepingRecentTracksBlocked() {
        val filter = YandexRotorFilter()
        (1..12).forEach { filter.markGiven("$it:1") }
        val pool = (1..12).map { rotorItem(it.toString()) }
        assertTrue(filter.accept(pool, allowHeardBefore = true).isEmpty())

        filter.relaxSessionHistory(keepRecent = 10)
        val reopened = filter.accept(pool, allowHeardBefore = true)
        assertEquals(listOf("Track 1", "Track 2"), reopened.map { it.title })
    }
}
