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
    fun `no detection without freefall phase for sub-large impact`() {
        var time = 1000L
        // 25 m/s^2 is above IMPACT_THRESHOLD but below LARGE_IMPACT_THRESHOLD,
        // so without a preceding free-fall this must not count as an impact.
        assertFalse(algo.processSample(25.0f, time))
        time += 600
        assertFalse(algo.processSample(9.8f, time))
    }

    @Test
    fun `trip during sustained motion triggers at 3g without freefall`() {
        var time = 1000L
        // Walk for ~1.5s: periodic footfall bumps above the stillness band.
        repeat(75) { i ->
            val mag = if (i % 5 == 0) 14.7f else 9.0f
            assertFalse(algo.processSample(mag, time))
            time += 20
        }
        // Trip: 3.06g impact (above LARGE but below REST threshold).
        assertFalse(algo.processSample(30.0f, time))
        val impactTime = time
        time = impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        assertTrue(algo.processSample(9.8f, time))
    }

    @Test
    fun `standing up from rest with 3g pocket jolt does not trigger`() {
        var time = 1000L
        // Sit still for 2 seconds.
        repeat(100) {
            assertFalse(algo.processSample(9.8f, time))
            time += 20
        }
        // Short rise: 200ms of out-of-band motion — below SUSTAINED_MOTION_MS.
        repeat(10) {
            assertFalse(algo.processSample(13.0f, time))
            time += 20
        }
        // 3.16g pocket spike as the user stands.
        assertFalse(algo.processSample(31.0f, time))
        val spikeTime = time
        // User is now standing still — must NOT confirm as a fall.
        time = spikeTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        assertFalse(algo.processSample(9.8f, time))
    }

    @Test
    fun `fall from seated rest triggers at 5g without freefall`() {
        var time = 1000L
        // Sit still for 2 seconds.
        repeat(100) {
            assertFalse(algo.processSample(9.8f, time))
            time += 20
        }
        // Body hits floor from seated height: 5.1g impact, no prior motion.
        assertFalse(algo.processSample(50.0f, time))
        val impactTime = time
        time = impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        assertTrue(algo.processSample(9.8f, time))
    }

    @Test
    fun `shallow one-sample freefall dip does not trigger even with hard impact`() {
        // Regression from the real-device false alarm: one sample at ~0.66g
        // (not genuine free-fall), then a 4.68g pocket whip as the user stood
        // up from a chair. Neither path should fire.
        var time = 1000L
        repeat(100) {
            assertFalse(algo.processSample(9.8f, time))
            time += 20
        }
        assertFalse(algo.processSample(6.47f, time))
        time += 67
        assertFalse(algo.processSample(45.9f, time))
        val spikeTime = time
        time = spikeTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        assertFalse(algo.processSample(9.8f, time))
    }

    @Test
    fun `sustained shallow freefall still counts as genuine`() {
        var time = 1000L
        // Three samples of shallow free-fall (0.6g) spanning 60ms — long
        // enough to count even though none is below the deep threshold.
        repeat(3) {
            assertFalse(algo.processSample(5.88f, time))
            time += 30
        }
        time += 50
        assertFalse(algo.processSample(39.2f, time))
        val impactTime = time
        time = impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        assertTrue(algo.processSample(9.8f, time))
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
        // Use a sub-large impact so only the freefall+impact path could apply,
        // and verify it is rejected because the free-fall expired.
        assertFalse(algo.processSample(25.0f, time))
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
    fun `rotation gate allows fall when gyro confirms body rotation`() {
        var time = 1000L
        // 1.6s of walking: low baseline rotation (~1 rad/s), below gate.
        repeat(80) { i ->
            algo.processGyro(1.0f, time)
            val mag = if (i % 5 == 0) 14.7f else 9.0f
            assertFalse(algo.processSample(mag, time))
            time += 20
        }
        // Trip: high angular velocity (body pivots around ankles) + 3g impact.
        algo.processGyro(8.0f, time)
        assertFalse(algo.processSample(30.0f, time))
        val impactTime = time
        time = impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        algo.processGyro(0.5f, time)
        assertTrue(algo.processSample(9.8f, time))
    }

    @Test
    fun `rotation gate suppresses phone drop without body rotation`() {
        var time = 1000L
        // Stationary with gyro active: user holding phone, low rotation.
        repeat(50) {
            algo.processGyro(1.0f, time)
            assertFalse(algo.processSample(9.8f, time))
            time += 20
        }
        // Drop: clean deep free-fall, only modest tumble (<4 rad/s).
        repeat(5) {
            algo.processGyro(1.5f, time)
            assertFalse(algo.processSample(1.0f, time))
            time += 20
        }
        // Impact above the REST threshold — would fire without the gyro gate.
        algo.processGyro(2.0f, time)
        assertFalse(algo.processSample(50.0f, time))
        val impactTime = time
        time = impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100
        algo.processGyro(0.5f, time)
        assertFalse(algo.processSample(9.8f, time))
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
