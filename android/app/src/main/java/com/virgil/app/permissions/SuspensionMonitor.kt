package com.virgil.app.permissions

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.ui.MainActivity

/**
 * Detects package suspension — Pixel's Extreme Battery Saver and Digital
 * Wellbeing "pause" apps this way. A suspended Virgil keeps its services
 * running but the OS hides every notification and intercepts every activity
 * launch: check-in prompts and the countdown disarm screen silently never
 * reach the user while SMS dispatch still fires.
 *
 * Nothing can be shown *during* suspension, so the warning posts from the
 * MY_PACKAGE_UNSUSPENDED broadcast — the first moment the user can see it.
 */
object SuspensionMonitor {

    private const val NOTIFICATION_ID = 0x5653 // "VS" — virgil-suspended

    fun isSuspended(context: Context): Boolean =
        runCatching { context.packageManager.isPackageSuspended }.getOrDefault(false)

    fun notifyWasPaused(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = context.getString(R.string.suspension_warning_body)
        val notif = NotificationCompat.Builder(context, VirgilApp.CHANNEL_PERMISSION)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(context.getString(R.string.suspension_warning_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        nm.notify(NOTIFICATION_ID, notif)
    }
}
