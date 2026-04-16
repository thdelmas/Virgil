package com.virgil.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.virgil.app.data.EmergencyPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Restarts fall detection and dead man's switch services after device boot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = EmergencyPreferences(context)

        val fallEnabled = runBlocking { prefs.fallDetectionEnabled.first() }
        if (fallEnabled) {
            context.startForegroundService(
                Intent(context, FallDetectionService::class.java)
            )
        }

        val dmsEnabled = runBlocking { prefs.deadManSwitchEnabled.first() }
        if (dmsEnabled) {
            context.startForegroundService(
                Intent(context, DeadManSwitchService::class.java)
            )
        }
    }
}
