package com.example.myapplication.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherSyncTest {
    @Test
    fun clockOffsetTakesTheQuickestRoundTrip() {
        val sync = ClockSync()
        assertNull(sync.offsetMs)
        // Host clock 1000 ahead. A slow ping, delayed on the way back only, skews the offset.
        sync.add(sentAt = 0, hostAt = 1010, receivedAt = 300)
        sync.add(sentAt = 500, hostAt = 1510, receivedAt = 520)
        assertEquals(1000L, sync.offsetMs)
        assertEquals(20L, sync.rttMs)
        assertEquals(6000L, sync.hostNow(5000))
    }

    @Test
    fun keepsOnlyTheLatestSamples() {
        val sync = ClockSync(keep = 2)
        sync.add(0, 100, 2) // offset 99, quickest
        sync.add(10, 160, 20)
        sync.add(20, 170, 30)
        assertEquals(145L, sync.offsetMs)
    }

    @Test
    fun expectedPositionMovesOnOnlyWhilePlaying() {
        assertEquals(15_000L, DriftCorrection.expectedPosition(10_000, at = 1_000, playing = true, hostNow = 6_000, latencyMs = 0))
        assertEquals(10_000L, DriftCorrection.expectedPosition(10_000, at = 1_000, playing = false, hostNow = 6_000, latencyMs = 0))
        assertEquals(15_200L, DriftCorrection.expectedPosition(10_000, at = 1_000, playing = true, hostNow = 6_000, latencyMs = 200))
    }

    @Test
    fun driftHoldsNudgesOrSeeks() {
        assertEquals(DriftAction.Hold, DriftCorrection.decide(localMs = 10_000, expectedMs = 10_020))
        val behind = DriftCorrection.decide(localMs = 10_000, expectedMs = 10_200) as DriftAction.Nudge
        assertTrue(behind.speed > 1f && behind.speed <= 1f + DriftCorrection.MAX_NUDGE)
        val ahead = DriftCorrection.decide(localMs = 10_200, expectedMs = 10_000) as DriftAction.Nudge
        assertTrue(ahead.speed < 1f && ahead.speed >= 1f - DriftCorrection.MAX_NUDGE)
        assertEquals(DriftAction.Seek(12_150), DriftCorrection.decide(localMs = 10_000, expectedMs = 12_000))
        assertEquals(DriftAction.Seek(150), DriftCorrection.decide(localMs = 5_000, expectedMs = 0))
    }
}
