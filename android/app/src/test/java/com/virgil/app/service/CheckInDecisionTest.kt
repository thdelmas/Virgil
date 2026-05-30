package com.virgil.app.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckInDecisionTest {

    private fun decide(
        forced: Boolean = false,
        duringSleep: Boolean = false,
        hasRecent: Boolean = false,
        baselineReady: Boolean = true,
        anomalouslyQuiet: Boolean = true,
    ) = CheckInDecision.shouldPrompt(
        forced = forced,
        duringSleep = duringSleep,
        hasRecent = hasRecent,
        baselineReady = baselineReady,
        anomalouslyQuiet = anomalouslyQuiet,
    )

    @Test
    fun sleepWindowNeverPrompts() {
        assertFalse(decide(duringSleep = true))
        // Sleep is an absolute floor — even a forced anomaly stays quiet at night.
        assertFalse(decide(forced = true, duringSleep = true))
    }

    @Test
    fun forcedAnomalyPromptsOutsideSleep() {
        // BaselineCheckReceiver already decided activity is anomalously low.
        assertTrue(decide(forced = true, hasRecent = true))
    }

    @Test
    fun recentActivitySkips() {
        assertFalse(decide(hasRecent = true))
    }

    @Test
    fun warmUpKeepsOriginalSafetyNet() {
        // No baseline yet: any quiet period still prompts (now gently).
        assertTrue(decide(baselineReady = false, anomalouslyQuiet = false))
    }

    @Test
    fun normalQuietDoesNotPromptOnceBaselineKnown() {
        // The reported bug: a habitual quiet stretch (e.g. sleeping past the
        // 07:00 wake hour) must NOT prompt once we know it's normal for the user.
        assertFalse(decide(baselineReady = true, anomalouslyQuiet = false))
    }

    @Test
    fun anomalousQuietPrompts() {
        assertTrue(decide(baselineReady = true, anomalouslyQuiet = true))
    }
}
