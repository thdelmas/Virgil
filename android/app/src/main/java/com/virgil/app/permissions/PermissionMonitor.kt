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
 * Raises a high-importance notification when any mandatory permission has been
 * revoked while Virgil believed itself to be on duty. Safe to call from any
 * component — if nothing is missing, no notification is shown and any existing
 * alert is cleared.
 */
object PermissionMonitor {

    private const val NOTIFICATION_ID = 0x5650 // "VP"

    fun check(context: Context) {
        val missing = PermissionChecker.missingMandatory(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        if (missing.isEmpty()) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        val openPermissions = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_PERMISSIONS, true)
        }
        val pi = PendingIntent.getActivity(
            context, 0, openPermissions,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val titles = missing.joinToString { it.title }
        val body = context.getString(R.string.permission_lost_body, titles)

        val notif = NotificationCompat.Builder(context, VirgilApp.CHANNEL_PERMISSION)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(context.getString(R.string.permission_lost_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(NOTIFICATION_ID, notif)
    }
}
