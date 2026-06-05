package com.virgil.app.service

import com.virgil.app.analysis.FallDetectionAlgorithm

/**
 * Pure decision logic for the post-candidate "verify" phase, extracted from
 * [FallDetectionService] for testability and to keep the service lean.
 *
 * After a fall candidate, verify runs a silent window, then a haptic (buzzing)
 * window, then escalates to the emergency countdown — *unless* the user shows
 * they are fine first. Three signals stand verify down:
 *  - the user resumes motion (≥[motionCancelSamples] out-of-stillness samples),
 *  - the user is actively using the phone (screen interactive), or
 *  - the user explicitly cancels (handled by the service, not here).
 *
 * [tick] is fed accelerometer samples and the current time; the caller acts on
 * the returned [Decision]. The controller owns no Android dependencies and no
 * clock — the service passes `SystemClock.elapsedRealtime()` as `now`.
 */
class FallVerifyController(
    private val silentMs: Long = VERIFY_SILENT_MS,
    private val hapticMs: Long = VERIFY_HAPTIC_MS,
    private val buzzIntervalMs: Long = HAPTIC_BUZZ_INTERVAL_MS,
    private val motionCancelSamples: Int = VERIFY_MOTION_CANCEL_SAMPLES,
) {
    var startedAt: Long = 0
        private set
    var peakAccel: Float = 0f
        private set
    var hapticPhaseEntered: Boolean = false
        private set

    private var motionSamples = 0
    private var lastBuzzAt = 0L

    val isVerifying: Boolean get() = startedAt != 0L

    fun start(now: Long, peak: Float) {
        startedAt = now
        peakAccel = peak
        motionSamples = 0
        hapticPhaseEntered = false
        lastBuzzAt = 0
    }

    fun stop() {
        startedAt = 0
        peakAccel = 0f
        motionSamples = 0
        hapticPhaseEntered = false
        lastBuzzAt = 0
    }

    sealed interface Decision {
        data object None : Decision
        data object EnterHaptic : Decision
        data object Buzz : Decision
        data class Cancel(val reason: String) : Decision
        data class Escalate(val peak: Float) : Decision
    }

    /**
     * @param interactive whether the user is actively using the phone *and* that
     *   should stand verify down. The service gates this (release builds pass
     *   `isInteractive`; debug builds pass false so the flow stays testable with
     *   the screen on).
     */
    fun tick(now: Long, magnitude: Float, interactive: Boolean): Decision {
        if (startedAt == 0L) return Decision.None
        if (interactive) return Decision.Cancel("screen interactive")

        val elapsed = now - startedAt
        if (elapsed >= silentMs + hapticMs) return Decision.Escalate(peakAccel)

        if (elapsed >= silentMs) {
            if (!hapticPhaseEntered) {
                hapticPhaseEntered = true
                lastBuzzAt = now
                return Decision.EnterHaptic
            }
            if (now - lastBuzzAt >= buzzIntervalMs) {
                lastBuzzAt = now
                return Decision.Buzz
            }
        }

        val inStillnessBand = magnitude in
            FallDetectionAlgorithm.STILLNESS_LOW..FallDetectionAlgorithm.STILLNESS_HIGH
        if (!inStillnessBand) {
            motionSamples++
            if (motionSamples >= motionCancelSamples) {
                return Decision.Cancel("motion resumed ($motionSamples samples)")
            }
        }
        return Decision.None
    }

    companion object {
        const val VERIFY_SILENT_MS = 15_000L
        const val VERIFY_HAPTIC_MS = 15_000L
        const val HAPTIC_BUZZ_INTERVAL_MS = 3_000L
        const val VERIFY_MOTION_CANCEL_SAMPLES = 8
    }
}
