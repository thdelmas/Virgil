package com.virgil.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.data.InteractionTracker
import com.virgil.app.permissions.PermissionMonitor
import com.virgil.app.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Check-in service. Schedules periodic "are you OK?" alarms.
 * If the user doesn't interact with the phone within the configured interval,
 * a check-in notification is shown. If that goes unanswered, emergency alert fires.
 */
class CheckInService : Service() {

    private lateinit var prefs: EmergencyPreferences

    private val presenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            InteractionTracker.record(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = EmergencyPreferences(this)
        // ACTION_USER_PRESENT is a protected broadcast and must be registered at runtime.
        registerReceiver(presenceReceiver, IntentFilter(Intent.ACTION_USER_PRESENT))
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(presenceReceiver) }
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelAlarm()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        scheduleNextCheckIn()
        scheduleBaselineCheck()
        PermissionMonitor.check(this)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleNextCheckIn() {
        val intervalHours = runBlocking { prefs.checkInIntervalHours.first() }
        val intervalMs = intervalHours * 3600 * 1000

        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, CheckInReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, REQUEST_CHECKIN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + intervalMs,
            pi
        )
    }

    private fun scheduleBaselineCheck() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, BaselineCheckReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, REQUEST_BASELINE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + BASELINE_INTERVAL_MS,
            BASELINE_INTERVAL_MS,
            pi
        )
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val checkInIntent = Intent(this, CheckInReceiver::class.java)
        val checkInPi = PendingIntent.getBroadcast(
            this, REQUEST_CHECKIN, checkInIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(checkInPi)

        val baselineIntent = Intent(this, BaselineCheckReceiver::class.java)
        val baselinePi = PendingIntent.getBroadcast(
            this, REQUEST_BASELINE, baselineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(baselinePi)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, VirgilApp.CHANNEL_FALL_DETECTION)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.checkin_running))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.virgil.app.STOP_CHECKIN"
        const val REQUEST_CHECKIN = 100
        const val REQUEST_BASELINE = 101
        private const val BASELINE_INTERVAL_MS = 60L * 60 * 1000 // 1 hour
    }
}
