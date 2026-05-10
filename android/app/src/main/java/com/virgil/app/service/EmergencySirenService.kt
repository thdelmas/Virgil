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
import com.virgil.app.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private var antiTamper: Boolean = false

    // Re-asserts max alarm volume on a tick so a thief or attacker can't mute
    // the deterrent via the volume rocker. Legitimate stop is the Silence
    // notification action — never the volume buttons.
    private val volumeGuard = object : Runnable {
        override fun run() {
            AttentionSound.raiseAlarmVolumePublic(this@EmergencySirenService)
            handler.postDelayed(this, VOLUME_GUARD_INTERVAL_MS)
        }
    }

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
        antiTamper = intent?.getBooleanExtra(EXTRA_ANTI_TAMPER, false) == true
        startForeground(NOTIFICATION_ID, buildNotification())
        AttentionSound.playEmergencySiren(this)
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, MAX_DURATION_MS)
        handler.removeCallbacks(volumeGuard)
        // Anti-tamper applies only to manual panic: in fall/no-response cases
        // a bystander or relative may legitimately want to lower the siren to
        // talk to the user or call paramedics, so volume buttons must work.
        if (antiTamper) {
            handler.postDelayed(volumeGuard, VOLUME_GUARD_INTERVAL_MS)
        }
        _activeFlow.value = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        handler.removeCallbacks(volumeGuard)
        runCatching { unregisterReceiver(silenceReceiver) }
        AttentionSound.stop()
        EmergencyLauncher.clearAlarmInFlight()
        _activeFlow.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopSelfClean() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val builder = NotificationCompat.Builder(this, VirgilApp.CHANNEL_EMERGENCY)
            .setContentTitle(getString(R.string.emergency_siren_title))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)

        if (antiTamper) {
            // Panic-mode siren: stop is gated by the in-app auth flow. Don't
            // expose a one-tap Silence action here — that would let a thief
            // pulling down the shade bypass the device-credential check.
            val openAppPi = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentText(getString(R.string.emergency_siren_body_panic))
                .setContentIntent(openAppPi)
        } else {
            val silencePi = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_SILENCE).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.setContentText(getString(R.string.emergency_siren_body))
                .addAction(
                    android.R.drawable.ic_lock_silent_mode,
                    getString(R.string.emergency_siren_silence),
                    silencePi,
                )
                .setContentIntent(silencePi)
        }
        return builder.build()
    }

    companion object {
        const val NOTIFICATION_ID = 0x5352 // "SR"
        const val ACTION_SILENCE = "com.virgil.app.SILENCE_SIREN"
        const val MAX_DURATION_MS = 5L * 60 * 1000
        const val VOLUME_GUARD_INTERVAL_MS = 500L
        const val EXTRA_ANTI_TAMPER = "anti_tamper"

        private val _activeFlow = MutableStateFlow(false)
        val activeFlow: StateFlow<Boolean> = _activeFlow.asStateFlow()

        fun start(context: Context, antiTamper: Boolean = false) {
            val intent = Intent(context, EmergencySirenService::class.java)
                .putExtra(EXTRA_ANTI_TAMPER, antiTamper)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            // Same path the notification's Silence action uses — keeps a single
            // shutdown route so behavior is identical regardless of who asked.
            val intent = Intent(ACTION_SILENCE).setPackage(context.packageName)
            context.sendBroadcast(intent)
        }
    }
}
