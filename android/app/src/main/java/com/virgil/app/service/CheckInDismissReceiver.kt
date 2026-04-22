package com.virgil.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.data.InteractionTracker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Receives the "I'm OK" signal when the user taps or dismisses
 * the check-in notification. Cancels pending emergency and reschedules.
 */
class CheckInDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        InteractionTracker.recordCheckInDismiss(context)

        AttentionSound.stop()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(CheckInReceiver.NOTIFICATION_ID)

        // User explicitly said "I'm OK" — clear the low-activity flag on
        // the monitoring notification so it returns to the calm state.
        // Only meaningful if fall detection is currently running.
        val fallEnabled = runBlocking {
            EmergencyPreferences(context).fallDetectionEnabled.first()
        }
        if (fallEnabled) {
            context.startForegroundService(
                Intent(context, FallDetectionService::class.java)
                    .setAction(FallDetectionService.ACTION_CLEAR_LOW_ACTIVITY)
            )
        }

        val serviceIntent = Intent(context, CheckInService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
