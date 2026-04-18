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
import com.virgil.app.data.InteractionTracker
import com.virgil.app.ui.emergency.EmergencyCountdownActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * Receives check-in alarms from [CheckInService].
 * Checks if the user has interacted with the phone recently.
 * If not, shows a check-in notification. If that goes unanswered
 * after 5 minutes, triggers emergency countdown.
 */
class CheckInReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = EmergencyPreferences(context)
        val force = intent.getBooleanExtra(EXTRA_FORCE, false)

        // Check if we're in sleep hours
        if (isDuringSleepHours(context)) {
            // Reschedule — CheckInService will handle next alarm
            if (!force) restartService(context)
            return
        }

        // Binary recent-interaction gate is skipped for baseline-triggered
        // escalations: those already decided activity is anomalously low.
        if (!force && hasRecentInteraction(context)) {
            // User is active, just reschedule
            restartService(context)
            return
        }

        // No recent activity — show check-in notification and ring
        val shownAt = System.currentTimeMillis()
        showCheckInNotification(context)
        AttentionSound.playCheckInRing(context)

        // If the user hasn't explicitly dismissed within the grace period, escalate.
        // Explicit dismiss (tap or swipe on the check-in notification) beats any
        // fuzzy "is it still in the tray" check, which can race with the tap handler.
        Handler(Looper.getMainLooper()).postDelayed({
            val dismissedAfterShow =
                InteractionTracker.lastCheckInDismissMs(context) >= shownAt
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancel(NOTIFICATION_ID)
            AttentionSound.stop()
            if (!dismissedAfterShow) triggerEmergency(context)
        }, GRACE_PERIOD_MS)
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

    private fun hasRecentInteraction(context: Context): Boolean {
        val intervalHours = runBlocking {
            EmergencyPreferences(context).checkInIntervalHours.first()
        }
        val windowMs = intervalHours * 3600L * 1000L
        return InteractionTracker.isRecent(context, windowMs)
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
        val intent = Intent(context, CheckInService::class.java)
        context.startForegroundService(intent)
    }

    companion object {
        const val NOTIFICATION_ID = 0x5649 // "VI"
        const val GRACE_PERIOD_MS = 5 * 60 * 1000L // 5 minutes
        const val EXTRA_FORCE = "force"
    }
}
