package com.virgil.app.data

import android.content.Context

/**
 * Records timestamps of confirmed user-presence signals (screen unlock,
 * app launch, check-in dismissal) and answers whether one occurred recently.
 */
object InteractionTracker {

    private const val PREFS = "virgil_quick"
    private const val KEY_LAST_MS = "last_interaction_ms"
    private const val KEY_LAST_CHECKIN_DISMISS_MS = "last_checkin_dismiss_ms"

    fun record(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_MS, System.currentTimeMillis())
            .apply()
        ActivityBaseline(context).recordEvent()
    }

    /** Records an explicit "I'm OK" in response to a check-in notification. */
    fun recordCheckInDismiss(context: Context) {
        val now = System.currentTimeMillis()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_MS, now)
            .putLong(KEY_LAST_CHECKIN_DISMISS_MS, now)
            .apply()
        ActivityBaseline(context).recordEvent()
    }

    fun lastCheckInDismissMs(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_CHECKIN_DISMISS_MS, 0L)

    /** True if a presence signal was recorded within [windowMs] of now. */
    fun isRecent(context: Context, windowMs: Long): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_MS, 0L)
        if (last == 0L) return false
        return System.currentTimeMillis() - last <= windowMs
    }
}
