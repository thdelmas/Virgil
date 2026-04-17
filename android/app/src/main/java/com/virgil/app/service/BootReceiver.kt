package com.virgil.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.permissions.PermissionMonitor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Restarts fall detection and check-in services after device boot or app update.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val prefs = EmergencyPreferences(context)

        val fallEnabled = runBlocking { prefs.fallDetectionEnabled.first() }
        if (fallEnabled) {
            context.startForegroundService(
                Intent(context, FallDetectionService::class.java)
            )
        }

        val checkInEnabled = runBlocking { prefs.checkInEnabled.first() }
        if (checkInEnabled) {
            context.startForegroundService(
                Intent(context, CheckInService::class.java)
            )
        }

        PermissionMonitor.check(context)
    }
}
