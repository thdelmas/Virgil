package com.virgil.app.service

import android.content.Context
import android.util.Log
import com.virgil.app.data.EmergencyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Orchestrates a user-triggered panic alert: aggression or phone-theft scenarios
 * where the fall pipeline can't fire and the user has milliseconds to act.
 *
 * Contract differs from fall detection in three ways: (1) no countdown — the
 * user already decided, a delay only helps an attacker; (2) no auto-call — the
 * siren screaming over the mic would make the call useless; (3) the bystander
 * siren starts *immediately* alongside the dispatcher, not after dispatch
 * succeeds, so phone-theft deterrence kicks in even if SMS is still in flight.
 *
 * Hold-to-activate on the UI button is the only false-trigger guard.
 */
object PanicTrigger {

    private const val TAG = "VirgilPanic"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun fire(context: Context) {
        val app = context.applicationContext
        Log.i(TAG, "panic fired")

        // Siren first: even if contacts are empty or dispatch fails, the loud
        // alarm is the anti-theft / anti-aggression deterrent. Anti-tamper on
        // because this branch is specifically for theft/aggression — fall
        // detection's siren passes false so a bystander can lower the volume.
        EmergencySirenService.start(app, antiTamper = true)

        scope.launch {
            val prefs = EmergencyPreferences(app)
            val contacts = prefs.contacts.first()
            if (contacts.isEmpty()) {
                Log.w(TAG, "panic dispatch skipped: no contacts configured")
                return@launch
            }
            val customTemplate = prefs.smsMessageOverride.first()
            EmergencyDispatcher(app).dispatch(
                contacts = contacts,
                customTemplate = customTemplate,
                triggerType = EmergencyDispatcher.TriggerType.PANIC,
            )
        }
    }
}
