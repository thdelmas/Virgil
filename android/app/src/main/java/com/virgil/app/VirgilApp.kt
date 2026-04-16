package com.virgil.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class VirgilApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val fallChannel = NotificationChannel(
            CHANNEL_FALL_DETECTION,
            getString(R.string.fall_detection_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent notification while fall detection is active"
        }

        val alertChannel = NotificationChannel(
            CHANNEL_EMERGENCY,
            "Emergency Alert",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Emergency alert when a fall or missed check-in is detected"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        }

        val checkinChannel = NotificationChannel(
            CHANNEL_CHECKIN,
            getString(R.string.dead_man_switch_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Check-in reminders from the dead man's switch"
            enableVibration(true)
        }

        manager.createNotificationChannel(fallChannel)
        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(checkinChannel)
    }

    companion object {
        const val CHANNEL_FALL_DETECTION = "fall_detection"
        const val CHANNEL_EMERGENCY = "emergency"
        const val CHANNEL_CHECKIN = "checkin"
    }
}
