package com.virgil.app.service

import android.content.Context
import android.provider.Settings

/**
 * Airplane mode is a hard off-switch. Virgil cannot fulfil its contract —
 * no SMS, no call — when radios are off, and the user has explicitly told
 * the system "don't do phone things right now". So while airplane mode is
 * on we stop sensor listening, cancel any pending alerts, and hold the
 * services in a paused state until it's switched back off.
 */
object AirplaneMode {
    fun isOn(context: Context): Boolean =
        Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON,
            0,
        ) != 0
}
