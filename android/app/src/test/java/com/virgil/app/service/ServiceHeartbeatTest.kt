package com.virgil.app.service

import com.virgil.app.service.ServiceHeartbeat.Companion.findGaps
import com.virgil.app.service.ServiceHeartbeat.Companion.parseElapsed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServiceHeartbeatTest {

    @Test
    fun parseElapsedReadsSecondColumnAndSkipsMalformed() {
        val lines = listOf(
            "1700000000000,60000,sensor",
            "garbage",
            "1700000060000,120000,sensor",
            "1700000120000,,create", // missing elapsed
        )
        assertEquals(listOf(60_000L, 120_000L), parseElapsed(lines))
    }

    @Test
    fun steadyBeatsHaveNoGaps() {
        val elapsed = generateSequence(0L) { it + 60_000 }.take(10).toList()
        assertTrue(findGaps(elapsed, thresholdMs = 90_000).isEmpty())
    }

    @Test
    fun silentDeathShowsAsGap() {
        // Beats every minute, then nothing for two hours, then beats resume —
        // the OEM killed the service and START_STICKY eventually revived it.
        val elapsed = listOf(0L, 60_000, 120_000, 7_320_000, 7_380_000)
        val gaps = findGaps(elapsed, thresholdMs = 90_000)
        assertEquals(1, gaps.size)
        assertEquals(120_000L, gaps[0].fromElapsedMs)
        assertEquals(7_320_000L, gaps[0].toElapsedMs)
        assertEquals(7_200_000L, gaps[0].durationMs)
    }

    @Test
    fun rebootIsNotAGap() {
        // elapsedRealtime resets to zero on reboot: the decrease is a power
        // cycle, not a silent kill, so it must not be flagged.
        val elapsed = listOf(3_000_000L, 3_060_000L, 0L, 60_000L)
        assertTrue(findGaps(elapsed, thresholdMs = 90_000).isEmpty())
    }

    @Test
    fun gapAndRebootCoexist() {
        val elapsed = listOf(0L, 60_000L, 600_000L, /* reboot */ 0L, 60_000L)
        val gaps = findGaps(elapsed, thresholdMs = 90_000)
        assertEquals(1, gaps.size)
        assertEquals(60_000L to 600_000L, gaps[0].fromElapsedMs to gaps[0].toElapsedMs)
    }
}
