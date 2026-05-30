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
import com.virgil.app.data.ActivityBaseline
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.data.InteractionTracker
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
        val force = intent.getBooleanExtra(EXTRA_FORCE, false)

        // Airplane mode is a hard off-switch: no SMS, no call, no point
        // ringing to wake someone up only to fail the escalation.
        if (AirplaneMode.isOn(context)) return

        if (!shouldPrompt(context, force)) {
            // Nothing to ask this cycle — keep the periodic alarm chain alive.
            if (!force) restartService(context)
            return
        }

        // Show a *gentle* check-in: the notification channel carries a soft,
        // silent-/DND-respecting tone. The loud alarm-stream siren is reserved
        // for the no-response escalation below — a prompt should never blare.
        val shownAt = System.currentTimeMillis()
        showCheckInNotification(context)

        // If the user hasn't explicitly dismissed within the grace period, escalate.
        // Explicit dismiss (tap or swipe on the check-in notification) beats any
        // fuzzy "is it still in the tray" check, which can race with the tap handler.
        Handler(Looper.getMainLooper()).postDelayed({
            val dismissedAfterShow =
                InteractionTracker.lastCheckInDismissMs(context) >= shownAt
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.cancel(NOTIFICATION_ID)
            if (!dismissedAfterShow) triggerEmergency(context)
        }, GRACE_PERIOD_MS)
    }

    /**
     * Gathers the live signals and defers to [CheckInDecision]. A normal night
     * (habitually quiet, even past the fixed wake hour) no longer prompts once
     * the baseline knows it's normal; an unusual daytime silence still does.
     */
    private fun shouldPrompt(context: Context, force: Boolean): Boolean {
        val intervalHours = runBlocking {
            EmergencyPreferences(context).checkInIntervalHours.first()
        }
        val baseline = ActivityBaseline(context)
        return CheckInDecision.shouldPrompt(
            forced = force,
            duringSleep = isDuringSleepHours(context),
            hasRecent = InteractionTracker.isRecent(context, intervalHours * 3600_000L),
            baselineReady = baseline.hasBaseline(),
            anomalouslyQuiet = baseline.isAnomalouslyQuiet(
                intervalHours.toInt().coerceIn(1, CheckInDecision.MAX_BASELINE_LOOKBACK_HOURS)
            ),
        )
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
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setDeleteIntent(cancelPi)
            .setContentIntent(cancelPi)
            .setTimeoutAfter(GRACE_PERIOD_MS)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun triggerEmergency(context: Context) {
        EmergencyLauncher.launch(context, triggerType = "checkin")
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
