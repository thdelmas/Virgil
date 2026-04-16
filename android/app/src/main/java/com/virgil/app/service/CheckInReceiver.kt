package com.virgil.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.ui.emergency.EmergencyCountdownActivity
import java.util.Calendar

/**
 * Receives check-in alarms from [DeadManSwitchService].
 * Checks if the user has interacted with the phone recently.
 * If not, shows a check-in notification. If that goes unanswered
 * after 5 minutes, triggers emergency countdown.
 */
class CheckInReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = EmergencyPreferences(context)

        // Check if we're in sleep hours
        if (isDuringSleepHours(context)) {
            // Reschedule — DeadManSwitchService will handle next alarm
            restartService(context)
            return
        }

        // Check if user has interacted with the phone recently
        if (hasRecentInteraction(context)) {
            // User is active, just reschedule
            restartService(context)
            return
        }

        // No recent activity — show check-in notification
        showCheckInNotification(context)

        // If no response in 5 minutes, trigger emergency
        Handler(Looper.getMainLooper()).postDelayed({
            val nm = context.getSystemService(NotificationManager::class.java)
            // If notification is still showing, user didn't respond
            val activeNotifications = nm.activeNotifications
            val stillPending = activeNotifications.any { it.id == NOTIFICATION_ID }
            if (stillPending) {
                nm.cancel(NOTIFICATION_ID)
                triggerEmergency(context)
            }
        }, GRACE_PERIOD_MS)
    }

    private fun isDuringSleepHours(context: Context): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        // Default sleep hours: 23-7
        val prefs = context.getSharedPreferences("virgil_quick", Context.MODE_PRIVATE)
        val sleepStart = prefs.getInt("sleep_start", 23)
        val sleepEnd = prefs.getInt("sleep_end", 7)

        return if (sleepStart > sleepEnd) {
            // Overnight range (e.g. 23-7)
            hour >= sleepStart || hour < sleepEnd
        } else {
            hour in sleepStart until sleepEnd
        }
    }

    private fun hasRecentInteraction(context: Context): Boolean {
        // Check if screen has been unlocked in the last interval period
        // Using a simple heuristic: check if the device is interactive
        val powerManager = context.getSystemService(android.os.PowerManager::class.java)
        return powerManager.isInteractive
    }

    private fun showCheckInNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        // Tapping the notification cancels it (counts as "I'm OK")
        val cancelIntent = Intent(context, CheckInDismissReceiver::class.java)
        val cancelPi = PendingIntent.getBroadcast(
            context, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, VirgilApp.CHANNEL_CHECKIN)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.checkin_notification_title))
            .setContentText(context.getString(R.string.checkin_notification_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setDeleteIntent(cancelPi)
            .setContentIntent(cancelPi)
            .setTimeoutAfter(GRACE_PERIOD_MS)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun triggerEmergency(context: Context) {
        val intent = Intent(context, EmergencyCountdownActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(FallDetectionService.EXTRA_TRIGGER_TYPE, "checkin")
        }
        context.startActivity(intent)
    }

    private fun restartService(context: Context) {
        val intent = Intent(context, DeadManSwitchService::class.java)
        context.startForegroundService(intent)
    }

    companion object {
        const val NOTIFICATION_ID = 0x5649 // "VI"
        const val GRACE_PERIOD_MS = 5 * 60 * 1000L // 5 minutes
    }
}
