package com.virgil.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.virgil.app.data.EmergencyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Honours PanicKit TRIGGER broadcasts from trigger apps the user paired via
 * [PanicKitConnectActivity]. See [PanicResponder] for the sender-identity
 * caveat that drives the "any paired" gate.
 */
class PanicKitReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PanicResponder.ACTION_TRIGGER) return

        val app = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                val connected = EmergencyPreferences(app).connectedPanicSenders.first()
                if (PanicResponder.shouldHandleTrigger(connected)) {
                    PanicTrigger.fire(app)
                } else {
                    Log.w(TAG, "ignored PanicKit trigger — no app paired via CONNECT")
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "VirgilPanicKit"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
