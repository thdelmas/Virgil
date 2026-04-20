package com.virgil.app.analysis

/**
 * Pure algorithm for fall detection, extracted for testability.
 *
 * Two paths trigger a candidate impact:
 *  - Free-fall (<~0.7g) followed within 500ms by an impact (>~2.3g)
 *  - A clearly large impact (>~3g) with no preceding free-fall — catches
 *    trips, slumps, and braced falls where the phone never truly free-falls
 * An impact must then be followed by near-1g stillness to confirm a fall.
 */
class FallDetectionAlgorithm(
    private val logger: (String) -> Unit = {},
) {

    var freefallDetectedAt: Long = 0
        private set
    var impactDetectedAt: Long = 0
        private set
    var lastPeakAccel: Float = 0f
        private set
    var fallDetected: Boolean = false
        private set

    private var minFreefallMag: Float = Float.MAX_VALUE
    private var hadFreefall: Boolean = false

    /**
     * Process a single accelerometer reading.
     * @param magnitude acceleration magnitude in m/s^2
     * @param timestamp current time in milliseconds
     * @return true if a fall was just detected on this sample
     */
    fun processSample(magnitude: Float, timestamp: Long): Boolean {
        fallDetected = false

        if (magnitude < FREEFALL_THRESHOLD) {
            if (freefallDetectedAt == 0L) {
                logger("freefall entered mag=$magnitude")
            }
            if (magnitude < minFreefallMag) minFreefallMag = magnitude
            freefallDetectedAt = timestamp
            return false
        }

        if (impactDetectedAt == 0L) {
            val timeSinceFreefall = if (freefallDetectedAt > 0) timestamp - freefallDetectedAt else Long.MAX_VALUE
            val recentFreefall = timeSinceFreefall in 1..IMPACT_WINDOW_MS
            if (recentFreefall && magnitude > IMPACT_THRESHOLD) {
                impactDetectedAt = timestamp
                lastPeakAccel = magnitude
                hadFreefall = true
                logger("impact after freefall peak=$magnitude dt=${timeSinceFreefall}ms min=$minFreefallMag")
                return false
            }
            if (magnitude > LARGE_IMPACT_THRESHOLD) {
                impactDetectedAt = timestamp
                lastPeakAccel = magnitude
                hadFreefall = false
                logger("large impact (no freefall) peak=$magnitude")
                return false
            }
        }

        if (impactDetectedAt > 0) {
            val timeSinceImpact = timestamp - impactDetectedAt
            if (timeSinceImpact > STILLNESS_DELAY_MS && timeSinceImpact < STILLNESS_WINDOW_MS) {
                val nearOneG = magnitude in STILLNESS_LOW..STILLNESS_HIGH
                if (nearOneG) {
                    logger("stillness matched mag=$magnitude dt=${timeSinceImpact}ms hadFreefall=$hadFreefall peak=$lastPeakAccel")
                    fallDetected = true
                    reset()
                    return true
                }
            }
            if (timeSinceImpact > STILLNESS_WINDOW_MS) {
                logger("stillness window expired peak=$lastPeakAccel hadFreefall=$hadFreefall")
                reset()
            }
        }

        if (freefallDetectedAt > 0 && timestamp - freefallDetectedAt > IMPACT_WINDOW_MS) {
            freefallDetectedAt = 0
            minFreefallMag = Float.MAX_VALUE
        }

        return false
    }

    fun reset() {
        freefallDetectedAt = 0
        impactDetectedAt = 0
        lastPeakAccel = 0f
        minFreefallMag = Float.MAX_VALUE
        hadFreefall = false
    }

    companion object {
        const val FREEFALL_THRESHOLD = 6.86f       // ~0.7g — partial catches still count
        const val IMPACT_THRESHOLD = 22.5f         // ~2.3g — with prior free-fall
        const val LARGE_IMPACT_THRESHOLD = 29.4f   // ~3g — no free-fall required
        const val IMPACT_WINDOW_MS = 500L
        const val STILLNESS_DELAY_MS = 300L
        const val STILLNESS_WINDOW_MS = 5000L
        const val STILLNESS_LOW = 7.35f            // ~0.75g
        const val STILLNESS_HIGH = 12.25f          // ~1.25g
    }
}
