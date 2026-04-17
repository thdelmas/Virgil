package com.virgil.app.data

import android.content.Context

/**
 * Records timestamps of confirmed user-presence signals (screen unlock,
 * app launch, check-in dismissal) and answers whether one occurred recently.
 */
object InteractionTracker {

    private const val PREFS = "virgil_quick"
    private const val KEY_LAST_MS = "last_interaction_ms"

    fun record(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_MS, System.currentTimeMillis())
            .apply()
        ActivityBaseline(context).recordEvent()
    }

    /** True if a presence signal was recorded within [windowMs] of now. */
    fun isRecent(context: Context, windowMs: Long): Boolean {
        val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_MS, 0L)
        if (last == 0L) return false
        return System.currentTimeMillis() - last <= windowMs
    }
}
