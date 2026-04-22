package com.virgil.app.service

import android.app.NotificationManager
import android.content.Context

/**
 * Thin compatibility wrapper that routes every "start the emergency" call
 * to [EmergencyAlarmService], which owns the staged countdown, audio, and
 * vibration. Kept because existing call sites (fall verification,
 * check-in escalation) already reference it by name.
 *
 * Android 15 blocks direct `startActivity` calls from background foreground
 * services (BAL restrictions). [EmergencyAlarmService] posts a full-screen
 * notification and also calls startActivity — whichever path the OS allows.
 */
object EmergencyLauncher {

    // Legacy — the old single-phase launcher used a bare notification to
    // open the activity before the service existed. Kept as a no-op ID so
    // we can still cancel any stale notification posted by older builds.
    private const val LEGACY_NOTIFICATION_ID = 0x454D

    fun launch(context: Context, triggerType: String, peakAccel: Float = 0f) {
        if (AirplaneMode.isOn(context)) return
        FallDetectionService.alarmInFlight = true
        EmergencyAlarmService.start(context, triggerType, peakAccel)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(LEGACY_NOTIFICATION_ID)
    }

    fun clearAlarmInFlight() {
        FallDetectionService.alarmInFlight = false
    }
}
