package com.virgil.app.analysis

/**
 * Pure algorithm for fall detection, extracted for testability.
 *
 * Detection phases:
 * 1. Free-fall: acceleration magnitude drops below ~0.5g
 * 2. Impact: magnitude spikes above ~3g within 500ms of free-fall
 * 3. Stillness: magnitude stays near 1g for a period after impact (person lying on ground)
 */
class FallDetectionAlgorithm {

    var freefallDetectedAt: Long = 0
        private set
    var impactDetectedAt: Long = 0
        private set
    var lastPeakAccel: Float = 0f
        private set
    var fallDetected: Boolean = false
        private set

    /**
     * Process a single accelerometer reading.
     * @param magnitude acceleration magnitude in m/s^2
     * @param timestamp current time in milliseconds
     * @return true if a fall was just detected on this sample
     */
    fun processSample(magnitude: Float, timestamp: Long): Boolean {
        fallDetected = false

        // Phase 1: Free-fall detection
        if (magnitude < FREEFALL_THRESHOLD) {
            freefallDetectedAt = timestamp
            return false
        }

        // Phase 2: Impact detection
        if (freefallDetectedAt > 0 && magnitude > IMPACT_THRESHOLD) {
            val timeSinceFreefall = timestamp - freefallDetectedAt
            if (timeSinceFreefall in 1..IMPACT_WINDOW_MS) {
                impactDetectedAt = timestamp
                lastPeakAccel = magnitude
                return false
            }
        }

        // Phase 3: Post-fall stillness
        if (impactDetectedAt > 0) {
            val timeSinceImpact = timestamp - impactDetectedAt
            if (timeSinceImpact > STILLNESS_DELAY_MS && timeSinceImpact < STILLNESS_WINDOW_MS) {
                val nearOneG = magnitude in STILLNESS_LOW..STILLNESS_HIGH
                if (nearOneG) {
                    fallDetected = true
                    reset()
                    return true
                }
            }
            if (timeSinceImpact > STILLNESS_WINDOW_MS) {
                reset()
            }
        }

        // Reset freefall if too much time passed without impact
        if (freefallDetectedAt > 0 && timestamp - freefallDetectedAt > IMPACT_WINDOW_MS) {
            freefallDetectedAt = 0
        }

        return false
    }

    fun reset() {
        freefallDetectedAt = 0
        impactDetectedAt = 0
        lastPeakAccel = 0f
    }

    companion object {
        const val FREEFALL_THRESHOLD = 4.9f   // ~0.5g in m/s^2
        const val IMPACT_THRESHOLD = 29.4f     // ~3g in m/s^2
        const val IMPACT_WINDOW_MS = 500L
        const val STILLNESS_DELAY_MS = 500L
        const val STILLNESS_WINDOW_MS = 3000L
        const val STILLNESS_LOW = 8.8f         // ~0.9g
        const val STILLNESS_HIGH = 10.8f       // ~1.1g
    }
}
