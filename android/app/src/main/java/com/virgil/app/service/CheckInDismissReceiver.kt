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
        InteractionTracker.recordCheckInDismiss(context)

        AttentionSound.stop()

        val nm = context.getSystemService(NotificationManager::class.java)
        nm?.cancel(CheckInReceiver.NOTIFICATION_ID)

        val serviceIntent = Intent(context, CheckInService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
