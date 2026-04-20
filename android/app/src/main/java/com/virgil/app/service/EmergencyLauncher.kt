package com.virgil.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.ui.emergency.EmergencyCountdownActivity

/**
 * Launches [EmergencyCountdownActivity] via a full-screen-intent notification.
 *
 * Android 15 blocks direct `startActivity` calls from background foreground
 * services (BAL restrictions). A notification with `setFullScreenIntent` is
 * the sanctioned path for safety/alarm apps — it shows over the lockscreen
 * and auto-launches the activity when the screen is off.
 */
object EmergencyLauncher {

    private const val NOTIFICATION_ID = 0x454D // "EM"

    fun launch(context: Context, triggerType: String, peakAccel: Float = 0f) {
        val activityIntent = Intent(context, EmergencyCountdownActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(FallDetectionService.EXTRA_TRIGGER_TYPE, triggerType)
            if (peakAccel != 0f) putExtra(FallDetectionService.EXTRA_PEAK_ACCEL, peakAccel)
        }
        val fullScreenPi = PendingIntent.getActivity(
            context, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when (triggerType) {
            "checkin" -> context.getString(R.string.countdown_title_no_response)
            else -> context.getString(R.string.countdown_title_fall)
        }

        val notification = NotificationCompat.Builder(context, VirgilApp.CHANNEL_EMERGENCY)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.countdown_before))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreenPi)
            .setFullScreenIntent(fullScreenPi, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, notification)

        // Start the activity too — on foregrounded/unlocked devices this
        // succeeds and feels instant; if BAL blocks it, the full-screen
        // intent still fires.
        runCatching { context.startActivity(activityIntent) }
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }
}
