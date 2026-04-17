package com.virgil.app.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.virgil.app.data.InteractionTracker

/**
 * Receives the "I'm OK" signal when the user taps or dismisses
 * the check-in notification. Cancels pending emergency and reschedules.
 */
class CheckInDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        InteractionTracker.record(context)

        AttentionSound.stop()

        // Cancel the check-in notification
        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(CheckInReceiver.NOTIFICATION_ID)

        // Restart the check-in service to reschedule next check-in
        val serviceIntent = Intent(context, CheckInService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
