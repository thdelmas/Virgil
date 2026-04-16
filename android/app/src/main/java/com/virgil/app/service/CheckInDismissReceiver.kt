package com.virgil.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the "I'm OK" signal when the user taps or dismisses
 * the check-in notification. Cancels pending emergency and reschedules.
 */
class CheckInDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Cancel the check-in notification
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(CheckInReceiver.NOTIFICATION_ID)

        // Restart the dead man's switch service to reschedule next check-in
        val serviceIntent = Intent(context, DeadManSwitchService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
