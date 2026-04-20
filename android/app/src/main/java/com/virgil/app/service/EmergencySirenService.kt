package com.virgil.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp

/**
 * Keeps the emergency siren wailing after the countdown dispatches, so a
 * bystander walking past an unconscious user has an audible cue. Lives in
 * its own foreground service because the dialer/SMS UI takes over the
 * activity stack — the activity gets torn down, the siren must not.
 *
 * Auto-stops after [MAX_DURATION_MS] so a forgotten siren can't run forever.
 */
class EmergencySirenService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopSelfClean() }

    private val silenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            stopSelfClean()
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            silenceReceiver,
            IntentFilter(ACTION_SILENCE),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        AttentionSound.playEmergencySiren(this)
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, MAX_DURATION_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        runCatching { unregisterReceiver(silenceReceiver) }
        AttentionSound.stop()
        EmergencyLauncher.clearAlarmInFlight()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopSelfClean() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val silencePi = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_SILENCE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, VirgilApp.CHANNEL_EMERGENCY)
            .setContentTitle(getString(R.string.emergency_siren_title))
            .setContentText(getString(R.string.emergency_siren_body))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .addAction(
                android.R.drawable.ic_lock_silent_mode,
                getString(R.string.emergency_siren_silence),
                silencePi,
            )
            .setContentIntent(silencePi)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 0x5352 // "SR"
        const val ACTION_SILENCE = "com.virgil.app.SILENCE_SIREN"
        const val MAX_DURATION_MS = 5L * 60 * 1000

        fun start(context: Context) {
            val intent = Intent(context, EmergencySirenService::class.java)
            context.startForegroundService(intent)
        }
    }
}
