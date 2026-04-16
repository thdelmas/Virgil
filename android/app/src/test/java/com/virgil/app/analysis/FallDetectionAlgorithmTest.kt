package com.virgil.app.analysis

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FallDetectionAlgorithmTest {

    private lateinit var algo: FallDetectionAlgorithm

    @Before
    fun setUp() {
        algo = FallDetectionAlgorithm()
    }

    @Test
    fun `full fall sequence triggers detection`() {
        var time = 1000L

        // Normal walking (~1g)
        repeat(10) {
            assertFalse(algo.processSample(9.8f, time))
            time += 20
        }

        // Phase 1: Free-fall (~0.3g)
        assertFalse(algo.processSample(3.0f, time))
        time += 100

        // Phase 2: Impact (~4g)
        assertFalse(algo.processSample(39.2f, time))
        val impactTime = time
        time += 20

        // Wait for stillness delay (500ms)
        repeat(25) {
            assertFalse(algo.processSample(15.0f, time))
            time += 20
        }

        // Phase 3: Stillness (~1g)
        time = impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        assertTrue(algo.processSample(9.8f, time))
    }

    @Test
    fun `no detection without freefall phase`() {
        var time = 1000L
        assertFalse(algo.processSample(39.2f, time))
        time += 600
        assertFalse(algo.processSample(9.8f, time))
    }

    @Test
    fun `no detection without impact phase`() {
        var time = 1000L
        assertFalse(algo.processSample(3.0f, time))
        time += 100
        assertFalse(algo.processSample(9.8f, time))
        time += 1000
        assertFalse(algo.processSample(9.8f, time))
    }

    @Test
    fun `no detection without stillness phase`() {
        var time = 1000L
        assertFalse(algo.processSample(3.0f, time))
        time += 100
        assertFalse(algo.processSample(39.2f, time))
        val impactTime = time
        time += 20

        while (time - impactTime < FallDetectionAlgorithm.STILLNESS_WINDOW_MS + 100) {
            assertFalse(algo.processSample(15.0f, time))
            time += 20
        }
    }

    @Test
    fun `impact too late after freefall is ignored`() {
        var time = 1000L
        assertFalse(algo.processSample(3.0f, time))
        time += FallDetectionAlgorithm.IMPACT_WINDOW_MS + 100
        assertFalse(algo.processSample(39.2f, time))
        time += 600
        assertFalse(algo.processSample(9.8f, time))
    }

    @Test
    fun `reset clears all state`() {
        var time = 1000L
        algo.processSample(3.0f, time)
        time += 100
        algo.processSample(39.2f, time)
        algo.reset()
        time += 600
        assertFalse(algo.processSample(9.8f, time))
    }

    @Test
    fun `multiple falls can be detected sequentially`() {
        assertTrue(simulateFall(0L))
        assertEquals(0L, algo.freefallDetectedAt)
        assertEquals(0L, algo.impactDetectedAt)
        assertTrue(simulateFall(10_000L))
    }

    @Test
    fun `normal walking does not trigger`() {
        var time = 1000L
        repeat(500) { i ->
            val mag = if (i % 25 < 5) 14.7f else 9.0f
            assertFalse(algo.processSample(mag, time))
            time += 20
        }
    }

    private fun simulateFall(startTime: Long): Boolean {
        var time = startTime + 1000
        algo.processSample(3.0f, time)
        time += 100
        algo.processSample(39.2f, time)
        val impactTime = time
        time = impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        return algo.processSample(9.8f, time)
    }
}
