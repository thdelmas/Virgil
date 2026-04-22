package com.virgil.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.ui.report.ReportActivity

/**
 * Low-key, dismissible notification shown after the user hold-disarms an
 * alarm — "was that a false alarm? tap to report". Tapping opens
 * [ReportFalseAlarmActivity] where the user can describe what was
 * happening and, with explicit consent, attach the diagnostic snapshot
 * Virgil just captured.
 *
 * This is the only surface where the snapshot is offered to leave the
 * device, and even then: only on the user's mail app, via their own
 * send, to a recipient they choose.
 */
object FalseAlarmReportPrompt {

    const val NOTIFICATION_ID = 0x4641 // "FA"

    fun post(context: Context) {
        val openIntent = ReportActivity
            .intent(context, ReportActivity.Type.FALSE_ALARM)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, VirgilApp.CHANNEL_FALL_DETECTION)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.report_prompt_title))
            .setContentText(context.getString(R.string.report_prompt_body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(
                android.R.drawable.ic_menu_edit,
                context.getString(R.string.report_prompt_action),
                pi,
            )
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(NOTIFICATION_ID)
    }
}
