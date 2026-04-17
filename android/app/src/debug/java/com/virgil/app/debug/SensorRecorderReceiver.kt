package com.virgil.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug-only. Start/stop [SensorRecorderService] via `adb shell am broadcast`.
 *
 *   adb shell am broadcast \
 *     -a com.virgil.app.DEBUG_RECORD_START \
 *     -n com.virgil.app/.debug.SensorRecorderReceiver \
 *     --es label couch_fall
 *
 *   adb shell am broadcast \
 *     -a com.virgil.app.DEBUG_RECORD_STOP \
 *     -n com.virgil.app/.debug.SensorRecorderReceiver
 */
class SensorRecorderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val service = Intent(context, SensorRecorderService::class.java)
        when (intent.action) {
            ACTION_START -> {
                service.action = SensorRecorderService.ACTION_START
                intent.getStringExtra("label")?.let {
                    service.putExtra(SensorRecorderService.EXTRA_LABEL, it)
                }
                context.startForegroundService(service)
            }
            ACTION_STOP -> {
                service.action = SensorRecorderService.ACTION_STOP
                context.startService(service)
            }
        }
    }

    companion object {
        const val ACTION_START = "com.virgil.app.DEBUG_RECORD_START"
        const val ACTION_STOP = "com.virgil.app.DEBUG_RECORD_STOP"
    }
}
