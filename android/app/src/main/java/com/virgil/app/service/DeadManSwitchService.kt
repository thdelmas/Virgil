package com.virgil.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.virgil.app.data.EmergencyPreferences
import java.util.Calendar

/**
 * Dead man's switch service. Schedules periodic check-in alarms.
 * If the user doesn't interact with the phone within the configured interval,
 * a check-in notification is shown. If that goes unanswered, emergency alert fires.
 */
class DeadManSwitchService : Service() {

    private lateinit var prefs: EmergencyPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = EmergencyPreferences(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            cancelAlarm()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        scheduleNextCheckIn()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleNextCheckIn() {
        val intervalHours = runBlocking { prefs.dmsIntervalHours.first() }
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

    private fun cancelAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, CheckInReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            this, REQUEST_CHECKIN, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, VirgilApp.CHANNEL_FALL_DETECTION)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.dead_man_switch_running))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 2
        const val ACTION_STOP = "com.virgil.app.STOP_DMS"
        const val REQUEST_CHECKIN = 100
    }
}
