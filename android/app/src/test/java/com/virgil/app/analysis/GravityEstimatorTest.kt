package com.virgil.app.analysis

import org.junit.Test
import kotlin.math.abs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GravityEstimatorTest {

    @Test
    fun `first sample seeds the estimate directly`() {
        val est = GravityEstimator()
        est.process(0f, 0f, 9.8f)
        assertTrue(abs(est.z - 9.8f) < 0.001f)
    }

    @Test
    fun `converges to a steady orientation`() {
        val est = GravityEstimator()
        est.process(0f, 0f, 9.8f)
        // Phone rotates to portrait-upright and stays there.
        repeat(50) { est.process(0f, 9.8f, 0f) }
        assertTrue(est.y > 9.5f)
        assertTrue(est.z < 0.3f)
    }

    @Test
    fun `brief freefall transient does not destroy the estimate`() {
        val est = GravityEstimator()
        repeat(50) { est.process(0f, 0f, 9.8f) }
        // Three near-zero freefall samples (~60ms at 50Hz).
        repeat(3) { est.process(0f, 0f, 0.5f) }
        assertTrue(est.z > 4.9f)
    }

    @Test
    fun `estimator-fed gravity restores pocket-insertion rejection without hardware sensors`() {
        // Galaxy A06 scenario: no gyroscope, no gravity sensor. The phone is
        // held flat, jabbed into a pocket (deep freefall + impact), then sits
        // portrait-upright — must be suppressed via the estimated gravity.
        val algo = FallDetectionAlgorithm(hasGyroscope = false)
        val est = GravityEstimator()
        var time = 1000L

        fun feed(ax: Float, ay: Float, az: Float, mag: Float): Boolean {
            est.process(ax, ay, az)
            algo.processGravity(est.x, est.y, est.z)
            return algo.processSample(mag, time)
        }

        // In hand, screen up: gravity on Z.
        repeat(50) {
            assertFalse(feed(0f, 1.0f, 9.7f, 9.8f))
            time += 20
        }
        // Downward jab: sustained deep freefall (genuine on the strict
        // profile, which requires ≥120ms without a gyro).
        repeat(8) {
            assertFalse(feed(0f, 0.5f, 1.0f, 1.2f))
            time += 20
        }
        // Meets pocket fabric: sharp deceleration, now portrait-upright.
        assertFalse(feed(0f, 30.0f, 5.0f, 30.5f))
        val impactTime = time

        // Resting upright in the pocket until the stillness check.
        while (time < impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS + 100) {
            time += 20
            if (time < impactTime + FallDetectionAlgorithm.STILLNESS_DELAY_MS) {
                assertFalse(feed(0f, 9.7f, 1.0f, 13.0f))
            }
        }
        assertFalse(feed(0f, 9.7f, 1.0f, 9.8f))
    }
}
