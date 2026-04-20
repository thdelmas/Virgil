package com.virgil.app.analysis

/**
 * Pure algorithm for fall detection, extracted for testability.
 *
 * Three paths trigger a candidate impact:
 *  - Genuine free-fall (<~0.7g, lasting ≥40ms or reaching <~0.4g) followed
 *    within 500ms by an impact (>~2.3g)
 *  - Sustained prior motion + impact (>~3g) — trips and braced falls mid-walk
 *  - No prior motion + impact (>~5g) — falls from a chair or bed, where
 *    standing-up jolts alone shouldn't trigger but a body-hits-floor does
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

    private var freefallStartedAt: Long = 0
    private var minFreefallMag: Float = Float.MAX_VALUE
    private var hadFreefall: Boolean = false
    private var motionStartedAt: Long = 0
    private var lastMotionAt: Long = 0

    /**
     * Process a single accelerometer reading.
     * @param magnitude acceleration magnitude in m/s^2
     * @param timestamp current time in milliseconds
     * @return true if a fall was just detected on this sample
     */
    fun processSample(magnitude: Float, timestamp: Long): Boolean {
        fallDetected = false

        val hadSustainedMotion = motionStartedAt > 0 &&
            timestamp - motionStartedAt >= SUSTAINED_MOTION_MS

        if (magnitude < FREEFALL_THRESHOLD) {
            if (freefallDetectedAt == 0L) {
                freefallStartedAt = timestamp
                logger("freefall entered mag=$magnitude")
            }
            if (magnitude < minFreefallMag) minFreefallMag = magnitude
            freefallDetectedAt = timestamp
            updateMotionState(magnitude, timestamp)
            return false
        }

        if (impactDetectedAt == 0L) {
            val timeSinceFreefall = if (freefallDetectedAt > 0) timestamp - freefallDetectedAt else Long.MAX_VALUE
            val recentFreefall = timeSinceFreefall in 1..IMPACT_WINDOW_MS
            if (recentFreefall && magnitude > IMPACT_THRESHOLD) {
                val freefallDuration = freefallDetectedAt - freefallStartedAt
                val genuineFreefall = freefallDuration >= MIN_FREEFALL_DURATION_MS ||
                    minFreefallMag < DEEP_FREEFALL_THRESHOLD
                if (genuineFreefall) {
                    impactDetectedAt = timestamp
                    lastPeakAccel = magnitude
                    hadFreefall = true
                    logger("impact after freefall peak=$magnitude dt=${timeSinceFreefall}ms min=$minFreefallMag dur=${freefallDuration}ms")
                    return false
                }
                logger("freefall rejected (shallow/brief) min=$minFreefallMag dur=${freefallDuration}ms peak=$magnitude")
            }
            val largeImpactThreshold = if (hadSustainedMotion) LARGE_IMPACT_THRESHOLD else REST_IMPACT_THRESHOLD
            if (magnitude > largeImpactThreshold) {
                impactDetectedAt = timestamp
                lastPeakAccel = magnitude
                hadFreefall = false
                logger("large impact (no freefall) peak=$magnitude sustainedMotion=$hadSustainedMotion")
                return false
            }
            if (magnitude > IMPACT_THRESHOLD) {
                logger("impact candidate below threshold peak=$magnitude thresh=$largeImpactThreshold sustainedMotion=$hadSustainedMotion")
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
            freefallStartedAt = 0
            minFreefallMag = Float.MAX_VALUE
        }

        updateMotionState(magnitude, timestamp)
        return false
    }

    private fun updateMotionState(magnitude: Float, timestamp: Long) {
        val inStillnessBand = magnitude in STILLNESS_LOW..STILLNESS_HIGH
        if (inStillnessBand) {
            if (lastMotionAt > 0 && timestamp - lastMotionAt > MOTION_GAP_MS) {
                motionStartedAt = 0
            }
        } else {
            if (motionStartedAt == 0L) motionStartedAt = timestamp
            lastMotionAt = timestamp
        }
    }

    fun reset() {
        freefallDetectedAt = 0
        freefallStartedAt = 0
        impactDetectedAt = 0
        lastPeakAccel = 0f
        minFreefallMag = Float.MAX_VALUE
        hadFreefall = false
    }

    val isIdle: Boolean
        get() = freefallDetectedAt == 0L && impactDetectedAt == 0L

    companion object {
        const val FREEFALL_THRESHOLD = 6.86f       // ~0.7g — shallow dip still enters free-fall state
        const val DEEP_FREEFALL_THRESHOLD = 3.92f  // ~0.4g — a single deep sample qualifies as genuine
        const val MIN_FREEFALL_DURATION_MS = 40L   // or sustained ≥2 samples at ~50Hz
        const val IMPACT_THRESHOLD = 22.5f         // ~2.3g — with prior free-fall
        const val LARGE_IMPACT_THRESHOLD = 29.4f   // ~3g — requires sustained prior motion
        const val REST_IMPACT_THRESHOLD = 49.0f    // ~5g — fall from rest (chair, bed)
        const val IMPACT_WINDOW_MS = 500L
        const val STILLNESS_DELAY_MS = 300L
        const val STILLNESS_WINDOW_MS = 5000L
        const val STILLNESS_LOW = 7.35f            // ~0.75g
        const val STILLNESS_HIGH = 12.25f          // ~1.25g
        const val SUSTAINED_MOTION_MS = 1000L      // motion must last ≥1s to qualify
        const val MOTION_GAP_MS = 500L             // stillness longer than this ends a motion streak
    }
}
