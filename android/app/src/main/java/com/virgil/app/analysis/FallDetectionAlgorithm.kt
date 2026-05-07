package com.virgil.app.analysis

import kotlin.math.abs

/**
 * Pure algorithm for fall detection, extracted for testability.
 *
 * Three paths trigger a candidate impact:
 *  - Genuine free-fall (<~0.7g, lasting ≥40ms or reaching <~0.4g) followed
 *    within 500ms by an impact (>~2.3g)
 *  - Sustained prior motion + impact (>~3g) — trips and braced falls mid-walk
 *  - No prior motion + impact (>~5g) — falls from a chair or bed, where
 *    standing-up jolts alone shouldn't trigger but a body-hits-floor does
 * When a gyroscope is available, impacts are only accepted if accompanied by
 * recent rotation — a pocket drop tumbles briefly but doesn't rotate the way
 * a falling body does (pivoting around ankles/hips or swinging on a lanyard).
 * An impact must then be followed by near-1g stillness to confirm a fall.
 * When a gravity sensor is available, candidates are also rejected if the
 * orientation went from flat-ish (phone in hand) to vertical (phone in
 * pocket): that is the pocket-insertion gesture, not a fall.
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
    private var gyroEnabled: Boolean = false
    private var lastHighRotationAt: Long = 0
    private var peakRotation: Float = 0f
    private var gravityEnabled: Boolean = false
    private var gravX: Float = 0f
    private var gravY: Float = 0f
    private var gravZ: Float = 0f
    private var preGravX: Float = 0f
    private var preGravY: Float = 0f
    private var preGravZ: Float = 0f
    private var hasPreGravity: Boolean = false

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
            val rotationOk = rotationConfirmed(timestamp)
            val timeSinceFreefall = if (freefallDetectedAt > 0) timestamp - freefallDetectedAt else Long.MAX_VALUE
            val recentFreefall = timeSinceFreefall in 1..IMPACT_WINDOW_MS
            if (recentFreefall && magnitude > IMPACT_THRESHOLD) {
                val freefallDuration = freefallDetectedAt - freefallStartedAt
                val genuineFreefall = freefallDuration >= MIN_FREEFALL_DURATION_MS ||
                    minFreefallMag < DEEP_FREEFALL_THRESHOLD
                if (genuineFreefall && rotationOk) {
                    impactDetectedAt = timestamp
                    lastPeakAccel = magnitude
                    hadFreefall = true
                    logger("impact after freefall peak=$magnitude dt=${timeSinceFreefall}ms min=$minFreefallMag dur=${freefallDuration}ms rot=$peakRotation")
                    return false
                }
                if (genuineFreefall) {
                    logger("freefall impact suppressed (no rotation) peak=$magnitude min=$minFreefallMag dur=${freefallDuration}ms")
                } else {
                    logger("freefall rejected (shallow/brief) min=$minFreefallMag dur=${freefallDuration}ms peak=$magnitude")
                }
            }
            val largeImpactThreshold = if (hadSustainedMotion) LARGE_IMPACT_THRESHOLD else REST_IMPACT_THRESHOLD
            if (magnitude > largeImpactThreshold) {
                if (rotationOk) {
                    impactDetectedAt = timestamp
                    lastPeakAccel = magnitude
                    hadFreefall = false
                    logger("large impact (no freefall) peak=$magnitude sustainedMotion=$hadSustainedMotion rot=$peakRotation")
                    return false
                }
                logger("large impact suppressed (no rotation) peak=$magnitude sustainedMotion=$hadSustainedMotion")
            } else if (magnitude > IMPACT_THRESHOLD) {
                logger("impact candidate below threshold peak=$magnitude thresh=$largeImpactThreshold sustainedMotion=$hadSustainedMotion rotationOk=$rotationOk")
            }
        }

        if (impactDetectedAt > 0) {
            val timeSinceImpact = timestamp - impactDetectedAt
            if (timeSinceImpact > STILLNESS_DELAY_MS && timeSinceImpact < STILLNESS_WINDOW_MS) {
                val nearOneG = magnitude in STILLNESS_LOW..STILLNESS_HIGH
                if (nearOneG) {
                    if (pocketInsertionSignature()) {
                        logger("stillness suppressed (pocket insertion: pre z=$preGravZ → post y=$gravY z=$gravZ)")
                        reset()
                        return false
                    }
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

    /**
     * Called from the service when a gyroscope sample arrives. Also records
     * that a gyro is present so [rotationConfirmed] can gate impact detection.
     * @param rotationMagnitude angular velocity magnitude in rad/s
     */
    fun processGyro(rotationMagnitude: Float, timestamp: Long) {
        gyroEnabled = true
        if (rotationMagnitude > ROTATION_THRESHOLD) {
            lastHighRotationAt = timestamp
        }
        if (rotationMagnitude > peakRotation) peakRotation = rotationMagnitude
        if (lastHighRotationAt > 0 && timestamp - lastHighRotationAt > ROTATION_WINDOW_MS) {
            peakRotation = rotationMagnitude
        }
    }

    private fun rotationConfirmed(timestamp: Long): Boolean {
        if (!gyroEnabled) return true
        return lastHighRotationAt > 0 && timestamp - lastHighRotationAt <= ROTATION_WINDOW_MS
    }

    /**
     * Called from the service when a gravity sample arrives. The pre-impact
     * orientation is latched from the last sample seen *before* free-fall or
     * impact — i.e., the steady-state orientation just before the event.
     */
    fun processGravity(x: Float, y: Float, z: Float) {
        gravityEnabled = true
        gravX = x; gravY = y; gravZ = z
        if (impactDetectedAt == 0L && freefallDetectedAt == 0L) {
            preGravX = x; preGravY = y; preGravZ = z
            hasPreGravity = true
        }
    }

    /**
     * True when the orientation went from "flat-ish" (phone in hand, screen
     * roughly facing the user) to "upright" (phone vertical, portrait) —
     * the signature of putting the phone back in a pocket.
     */
    private fun pocketInsertionSignature(): Boolean {
        if (!gravityEnabled || !hasPreGravity) return false
        val preFlat = abs(preGravZ) > FLAT_PRE_Z_THRESHOLD
        val postUpright = abs(gravY) > UPRIGHT_Y_THRESHOLD && abs(gravZ) < UPRIGHT_Z_THRESHOLD
        return preFlat && postUpright
    }

    fun reset() {
        freefallDetectedAt = 0
        freefallStartedAt = 0
        impactDetectedAt = 0
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
        const val ROTATION_THRESHOLD = 4.0f        // rad/s (~229°/s) — above normal necklace swing
        const val ROTATION_WINDOW_MS = 500L        // rotation must occur within 500ms of impact
        const val FLAT_PRE_Z_THRESHOLD = 5.0f      // pre-impact: |gz| > 0.5g → phone was flat-ish
        const val UPRIGHT_Y_THRESHOLD = 8.0f       // post-impact: |gy| > 0.8g → phone is portrait-upright
        const val UPRIGHT_Z_THRESHOLD = 4.0f       // post-impact: |gz| < 0.4g → phone is not flat
    }
}
