package com.virgil.app.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.ui.MainActivity

/**
 * Presentation helpers for [FallDetectionService]: the verify heads-up and
 * trace notifications, and the verify-phase buzz. Split out so the service
 * itself stays focused on sensors and state.
 */

fun postVerifyHeadsUp(context: Context) {
    val cancelIntent = PendingIntent.getService(
        context, 2,
        Intent(context, FallDetectionService::class.java).apply {
            action = FallDetectionService.ACTION_CANCEL_VERIFY
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notif = NotificationCompat.Builder(context, VirgilApp.CHANNEL_VERIFY)
        .setContentTitle(context.getString(R.string.verify_notif_title))
        .setContentText(context.getString(R.string.verify_notif_body))
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(cancelIntent)
        .setAutoCancel(true)
        .setOngoing(true)
        .build()
    context.getSystemService(NotificationManager::class.java)
        ?.notify(FallDetectionService.VERIFY_NOTIFICATION_ID, notif)
}

fun dismissVerifyHeadsUp(context: Context) {
    context.getSystemService(NotificationManager::class.java)
        ?.cancel(FallDetectionService.VERIFY_NOTIFICATION_ID)
}

fun postVerifyTrace(context: Context) {
    val openApp = PendingIntent.getActivity(
        context, 3,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val notif = NotificationCompat.Builder(context, VirgilApp.CHANNEL_FALL_DETECTION)
        .setContentTitle(context.getString(R.string.verify_trace_title))
        .setContentText(context.getString(R.string.verify_trace_body))
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setContentIntent(openApp)
        .setAutoCancel(true)
        .build()
    context.getSystemService(NotificationManager::class.java)
        ?.notify(FallDetectionService.VERIFY_TRACE_NOTIFICATION_ID, notif)
}

fun verifyBuzz(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    } ?: return
    vibrator.vibrate(VibrationEffect.createWaveform(VERIFY_BUZZ_PATTERN, -1))
}

private val VERIFY_BUZZ_PATTERN = longArrayOf(0, 250, 120, 250, 120, 400)
