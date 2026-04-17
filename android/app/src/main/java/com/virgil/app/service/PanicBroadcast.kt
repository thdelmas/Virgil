package com.virgil.app.service

import android.content.Context
import android.content.Intent

/**
 * Emits the PanicKit panic-trigger broadcast so that any installed responder app
 * (Ripple, Haven, secure-wipe utilities, etc.) subscribed to the standardized
 * action can react. Pure on-device IPC, no network, no new permission.
 *
 * Wire-compatible with https://github.com/guardianproject/PanicKit without
 * taking the library as a dependency. Implicit broadcasts do not reach
 * manifest-declared receivers on Android 8+, so we resolve receivers via the
 * PackageManager and dispatch one explicit broadcast per responder — matching
 * PanicTrigger.sendTrigger's behaviour.
 */
object PanicBroadcast {

    const val ACTION_TRIGGER = "info.guardianproject.panic.action.TRIGGER"

    fun emit(context: Context) {
        val base = Intent(ACTION_TRIGGER)
        val pm = context.packageManager
        val selfPackage = context.packageName

        pm.queryBroadcastReceivers(base, 0)
            .map { it.activityInfo.packageName }
            .filter { it != null && it != selfPackage }
            .distinct()
            .forEach { pkg ->
                val explicit = Intent(ACTION_TRIGGER).setPackage(pkg)
                context.sendBroadcast(explicit)
            }
    }
}
