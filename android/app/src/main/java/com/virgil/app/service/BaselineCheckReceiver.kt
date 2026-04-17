package com.virgil.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.virgil.app.data.ActivityBaseline
import com.virgil.app.permissions.PermissionMonitor

/**
 * Fires hourly alongside the hard-cap check-in alarm. When today's recent
 * activity falls meaningfully below the learned baseline, escalates
 * into the standard check-in flow (bypassing the binary interaction
 * gate — the user may have unlocked briefly, but not at typical rate).
 */
class BaselineCheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        PermissionMonitor.check(context)

        if (!ActivityBaseline(context).isAnomalouslyQuiet(LOOKBACK_HOURS)) return

        val forward = Intent(context, CheckInReceiver::class.java).apply {
            putExtra(CheckInReceiver.EXTRA_FORCE, true)
        }
        context.sendBroadcast(forward)
    }

    companion object {
        private const val LOOKBACK_HOURS = 2
    }
}
