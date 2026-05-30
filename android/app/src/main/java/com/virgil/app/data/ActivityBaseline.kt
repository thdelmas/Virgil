package com.virgil.app.data

import android.content.Context
import java.util.Calendar
import kotlin.math.sqrt

/**
 * Learns the user's typical per-hour presence-event count and flags
 * anomalously quiet periods. Uses EWMA over completed days so storage
 * is O(48 floats + 48 ints) regardless of history length.
 *
 * Warm-up: [isAnomalouslyQuiet] returns false until [MIN_WARMUP_DAYS]
 * days have been observed, to avoid false alarms before a baseline exists.
 */
class ActivityBaseline(private val context: Context) {

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Called on every confirmed presence signal. */
    fun recordEvent() {
        rolloverIfNeeded()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val key = KEY_TODAY_HOUR_PREFIX + hour
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    /**
     * True if the last [lookbackHours] completed hours of activity are
     * more than [sigmaThreshold]σ below the expected baseline for those
     * hours-of-day. Returns false during warm-up or when the expected
     * baseline is near zero (naturally quiet hours, e.g. sleep).
     */
    fun isAnomalouslyQuiet(lookbackHours: Int, sigmaThreshold: Float = 2f): Boolean {
        rolloverIfNeeded()
        if (prefs.getInt(KEY_OBSERVED_DAYS, 0) < MIN_WARMUP_DAYS) return false

        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        var currentSum = 0
        var expectedMean = 0f
        var expectedVar = 0f
        for (h in 1..lookbackHours) {
            val hour = (currentHour - h + 24) % 24
            val onToday = currentHour - h >= 0
            val bucketKey = (if (onToday) KEY_TODAY_HOUR_PREFIX else KEY_YESTERDAY_HOUR_PREFIX) + hour
            currentSum += prefs.getInt(bucketKey, 0)
            expectedMean += prefs.getFloat(KEY_MEAN_PREFIX + hour, 0f)
            expectedVar += prefs.getFloat(KEY_VAR_PREFIX + hour, 0f)
        }

        if (expectedMean < MIN_EXPECTED_ACTIVITY) return false

        val threshold = (expectedMean - sigmaThreshold * sqrt(expectedVar.toDouble()).toFloat())
            .coerceAtLeast(0f)
        return currentSum < threshold
    }

    /** True once enough days have been observed for the baseline to be meaningful. */
    fun hasBaseline(): Boolean {
        rolloverIfNeeded()
        return prefs.getInt(KEY_OBSERVED_DAYS, 0) >= MIN_WARMUP_DAYS
    }

    private fun rolloverIfNeeded() {
        val today = dayOfEpoch()
        val lastDay = prefs.getInt(KEY_LAST_DAY, -1)
        if (lastDay == today) return
        if (lastDay < 0) {
            prefs.edit().putInt(KEY_LAST_DAY, today).apply()
            return
        }

        val daysSince = minOf(today - lastDay, MAX_ROLLOVER_DAYS)
        val editor = prefs.edit()

        for (dayOffset in 0 until daysSince) {
            val isMostRecent = dayOffset == daysSince - 1
            for (hour in 0 until 24) {
                val count = if (dayOffset == 0) {
                    prefs.getInt(KEY_TODAY_HOUR_PREFIX + hour, 0)
                } else {
                    0
                }
                val prevMean = prefs.getFloat(KEY_MEAN_PREFIX + hour, count.toFloat())
                val newMean = (1 - EWMA_ALPHA) * prevMean + EWMA_ALPHA * count
                val prevVar = prefs.getFloat(KEY_VAR_PREFIX + hour, 0f)
                val delta = count - prevMean
                val newVar = (1 - EWMA_ALPHA) * prevVar + EWMA_ALPHA * delta * delta
                editor.putFloat(KEY_MEAN_PREFIX + hour, newMean)
                editor.putFloat(KEY_VAR_PREFIX + hour, newVar)
                if (isMostRecent) {
                    editor.putInt(KEY_YESTERDAY_HOUR_PREFIX + hour, count)
                }
            }
        }

        for (hour in 0 until 24) {
            editor.remove(KEY_TODAY_HOUR_PREFIX + hour)
        }

        editor.putInt(KEY_OBSERVED_DAYS, prefs.getInt(KEY_OBSERVED_DAYS, 0) + daysSince)
        editor.putInt(KEY_LAST_DAY, today)
        editor.apply()
    }

    /** Local-calendar day identifier. Only equality is used, so year-packing is safe. */
    private fun dayOfEpoch(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    companion object {
        private const val PREFS = "virgil_baseline"
        private const val KEY_LAST_DAY = "last_day"
        private const val KEY_OBSERVED_DAYS = "observed_days"
        private const val KEY_TODAY_HOUR_PREFIX = "t_"
        private const val KEY_YESTERDAY_HOUR_PREFIX = "y_"
        private const val KEY_MEAN_PREFIX = "m_"
        private const val KEY_VAR_PREFIX = "v_"

        private const val MIN_WARMUP_DAYS = 7
        private const val MAX_ROLLOVER_DAYS = 30
        private const val EWMA_ALPHA = 0.2f
        private const val MIN_EXPECTED_ACTIVITY = 1.5f
    }
}
