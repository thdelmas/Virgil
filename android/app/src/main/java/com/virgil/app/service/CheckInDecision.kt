package com.virgil.app.service

/**
 * Pure decision for whether a check-in cycle should surface a prompt at all.
 * Extracted from [CheckInReceiver] so the gate is unit-testable without Android.
 *
 * The night a phone is plugged in for normal sleep should stay silent: a fixed
 * clock window can't know the user's real schedule (it fired a check-in at 07:24,
 * just past a hard-coded 07:00 "wake" hour), so we also lean on the learned
 * activity baseline — a habitually-quiet stretch is not an anomaly.
 */
object CheckInDecision {

    /** Cap the baseline lookback so a long check-in interval can't span a whole day. */
    const val MAX_BASELINE_LOOKBACK_HOURS = 6

    fun shouldPrompt(
        forced: Boolean,
        duringSleep: Boolean,
        hasRecent: Boolean,
        baselineReady: Boolean,
        anomalouslyQuiet: Boolean,
    ): Boolean {
        if (duringSleep) return false          // sleep window is an absolute floor
        if (forced) return true                // baseline receiver already saw an anomaly
        if (hasRecent) return false            // user is demonstrably active
        if (!baselineReady) return true        // no baseline yet — keep the periodic net
        return anomalouslyQuiet                // only nag when this quiet is unusual for them
    }
}
