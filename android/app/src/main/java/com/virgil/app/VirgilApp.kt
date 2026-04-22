package com.virgil.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.virgil.app.data.AppLocale

class VirgilApp : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

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
            description = getString(R.string.fall_channel_description)
        }

        val alertChannel = NotificationChannel(
            CHANNEL_EMERGENCY,
            getString(R.string.emergency_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.emergency_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
        }

        val checkinChannel = NotificationChannel(
            CHANNEL_CHECKIN,
            getString(R.string.checkin_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.checkin_channel_description)
            enableVibration(true)
        }

        val permissionChannel = NotificationChannel(
            CHANNEL_PERMISSION,
            getString(R.string.permission_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.permission_channel_description)
            enableVibration(true)
        }

        // High-importance, no channel-level vibration — the service drives its
        // own buzz pattern, and we only need this channel for the heads-up so
        // the user can see *why* their phone just vibrated.
        val verifyChannel = NotificationChannel(
            CHANNEL_VERIFY,
            getString(R.string.verify_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.verify_channel_description)
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannel(fallChannel)
        manager.createNotificationChannel(alertChannel)
        manager.createNotificationChannel(checkinChannel)
        manager.createNotificationChannel(permissionChannel)
        manager.createNotificationChannel(verifyChannel)
    }

    companion object {
        const val CHANNEL_FALL_DETECTION = "fall_detection"
        const val CHANNEL_EMERGENCY = "emergency"
        const val CHANNEL_CHECKIN = "checkin"
        const val CHANNEL_PERMISSION = "permission"
        const val CHANNEL_VERIFY = "fall_verify"
    }
}
