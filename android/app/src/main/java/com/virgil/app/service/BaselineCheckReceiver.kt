package com.virgil.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.virgil.app.data.ActivityBaseline
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.permissions.PermissionMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Fires hourly alongside the hard-cap check-in alarm. When today's recent
 * activity falls meaningfully below the learned baseline, escalates
 * into the standard check-in flow (bypassing the binary interaction
 * gate — the user may have unlocked briefly, but not at typical rate).
 */
class BaselineCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        PermissionMonitor.check(context)

        if (isDuringSleepHours(context)) return
        if (!ActivityBaseline(context).isAnomalouslyQuiet(LOOKBACK_HOURS)) return

        // Flag the monitoring notification so the user can see Virgil has
        // noticed unusual quiet before the check-in prompt fires — but
        // only if fall detection is currently running (otherwise we'd
        // start it just to edit a notification).
        val fallEnabled = runBlocking {
            EmergencyPreferences(context).fallDetectionEnabled.first()
        }
        if (fallEnabled) {
            // The preference may say "enabled" while the service was killed
            // by the OS; starting an FGS from a background receiver throws
            // ForegroundServiceStartNotAllowedException. Best-effort: if it's
            // already running this updates its notification; if not, swallow.
            runCatching {
                context.startForegroundService(
                    Intent(context, FallDetectionService::class.java)
                        .setAction(FallDetectionService.ACTION_MARK_LOW_ACTIVITY)
                )
            }.onFailure { Log.w("BaselineCheck", "FGS update skipped", it) }
        }

        val forward = Intent(context, CheckInReceiver::class.java).apply {
            putExtra(CheckInReceiver.EXTRA_FORCE, true)
        }
        context.sendBroadcast(forward)
    }

    private fun isDuringSleepHours(context: Context): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val prefs = EmergencyPreferences(context)
        val (sleepStart, sleepEnd) = runBlocking {
            prefs.checkInSleepStartHour.first() to prefs.checkInSleepEndHour.first()
        }
        return if (sleepStart > sleepEnd) {
            hour >= sleepStart || hour < sleepEnd
        } else {
            hour in sleepStart until sleepEnd
        }
    }

    companion object {
        private const val LOOKBACK_HOURS = 2
    }
}
