package com.virgil.app.service

import com.virgil.app.analysis.FallDetectionAlgorithm
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class FallVerifyControllerTest {

    private val t0 = 10_000L // a realistic non-zero elapsedRealtime base
    private val silent = FallVerifyController.VERIFY_SILENT_MS
    private val haptic = FallVerifyController.VERIFY_HAPTIC_MS
    private val still = 9.8f // ~1g, inside the stillness band
    private val moving = 20f // outside the stillness band

    private fun controller() = FallVerifyController()

    @Test
    fun `idle controller returns None`() {
        assertEquals(FallVerifyController.Decision.None, controller().tick(t0, still, false))
    }

    @Test
    fun `interactive stands verify down immediately`() {
        val c = controller()
        c.start(t0, 25f)
        val d = c.tick(t0 + 100, still, interactive = true)
        assertTrue(d is FallVerifyController.Decision.Cancel)
    }

    @Test
    fun `silent phase stays quiet`() {
        val c = controller()
        c.start(t0, 25f)
        assertEquals(FallVerifyController.Decision.None, c.tick(t0 + silent - 1, still, false))
        assertFalse(c.hapticPhaseEntered)
    }

    @Test
    fun `enters haptic phase once silent window elapses`() {
        val c = controller()
        c.start(t0, 25f)
        assertEquals(FallVerifyController.Decision.EnterHaptic, c.tick(t0 + silent, still, false))
        assertTrue(c.hapticPhaseEntered)
        // The immediate next tick should not re-enter or re-buzz.
        assertEquals(FallVerifyController.Decision.None, c.tick(t0 + silent + 1, still, false))
    }

    @Test
    fun `buzzes on the configured interval during haptic phase`() {
        val c = controller()
        c.start(t0, 25f)
        c.tick(t0 + silent, still, false) // EnterHaptic, buzz at t=silent
        val interval = FallVerifyController.HAPTIC_BUZZ_INTERVAL_MS
        assertEquals(FallVerifyController.Decision.None, c.tick(t0 + silent + interval - 1, still, false))
        assertEquals(FallVerifyController.Decision.Buzz, c.tick(t0 + silent + interval, still, false))
    }

    @Test
    fun `escalates with the candidate peak once full window elapses`() {
        val c = controller()
        c.start(t0, 23.16f)
        c.tick(t0 + silent, still, false) // into haptic
        val d = c.tick(t0 + silent + haptic, still, false)
        assertTrue(d is FallVerifyController.Decision.Escalate)
        assertEquals(23.16f, (d as FallVerifyController.Decision.Escalate).peak)
    }

    @Test
    fun `sustained motion cancels before escalation`() {
        val c = controller()
        c.start(t0, 25f)
        var last: FallVerifyController.Decision = FallVerifyController.Decision.None
        repeat(FallVerifyController.VERIFY_MOTION_CANCEL_SAMPLES) { i ->
            last = c.tick(t0 + i + 1, moving, false)
        }
        assertTrue(last is FallVerifyController.Decision.Cancel)
    }

    @Test
    fun `stillness samples do not count toward motion cancel`() {
        val c = controller()
        c.start(t0, 25f)
        repeat(50) { i ->
            assertEquals(FallVerifyController.Decision.None, c.tick(t0 + i + 1, still, false))
        }
    }

    @Test
    fun `stop resets state`() {
        val c = controller()
        c.start(t0, 25f)
        c.tick(t0 + silent, still, false)
        c.stop()
        assertFalse(c.isVerifying)
        assertFalse(c.hapticPhaseEntered)
        assertEquals(0f, c.peakAccel)
        assertEquals(FallVerifyController.Decision.None, c.tick(t0 + silent + haptic, still, false))
    }

    @Test
    fun `stillness band boundaries are not treated as motion`() {
        val c = controller()
        c.start(t0, 25f)
        // Exactly on the band edges should be "still".
        assertEquals(
            FallVerifyController.Decision.None,
            c.tick(t0 + 1, FallDetectionAlgorithm.STILLNESS_LOW, false),
        )
        assertEquals(
            FallVerifyController.Decision.None,
            c.tick(t0 + 2, FallDetectionAlgorithm.STILLNESS_HIGH, false),
        )
    }
}
