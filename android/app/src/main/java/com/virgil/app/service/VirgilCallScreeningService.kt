package com.virgil.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.virgil.app.data.EmergencyPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Screens incoming calls while Virgil is the user's Call Screening Service
 * (granted via RoleManager.ROLE_CALL_SCREENING). When the feature is enabled,
 * the armed window is open, and the caller matches a saved emergency contact,
 * the call is silenced and then programmatically accepted hands-free — so the
 * user can hear and talk without fighting the siren or a muted speaker.
 *
 * Every call that does not meet all three conditions passes through untouched.
 * Non-matching callers never see different behaviour.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class VirgilCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val isIncoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        if (!isIncoming) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val shouldAnswer = shouldAutoAnswer(callDetails)
        if (!shouldAnswer) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // Silence the ringtone — the siren is about to stop too, and the user
        // should hear the caller, not a ring.
        respondToCall(
            callDetails,
            CallResponse.Builder().setSilenceCall(true).build(),
        )

        Handler(Looper.getMainLooper()).postDelayed(
            { acceptAndRouteHandsFree(applicationContext) },
            ACCEPT_DELAY_MS,
        )
    }

    private fun shouldAutoAnswer(details: Call.Details): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "skip: ANSWER_PHONE_CALLS not granted")
            return false
        }

        val number = details.handle?.schemeSpecificPart
        val (enabled, armedUntil, contacts) = runBlocking {
            val prefs = EmergencyPreferences(applicationContext)
            Triple(
                prefs.autoAnswerEnabled.first(),
                prefs.autoAnswerArmedUntilMs.first(),
                prefs.contacts.first(),
            )
        }
        if (!enabled) return false
        if (System.currentTimeMillis() > armedUntil) {
            Log.i(TAG, "skip: armed window expired")
            return false
        }
        val match = AutoAnswer.isNumberAnEmergencyContact(number, contacts)
        if (!match) Log.i(TAG, "skip: caller not a saved emergency contact")
        return match
    }

    private fun acceptAndRouteHandsFree(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val tm = context.getSystemService(TelecomManager::class.java) ?: return
        runCatching { tm.acceptRingingCall() }
            .onFailure { Log.w(TAG, "acceptRingingCall failed: ${it.javaClass.simpleName}") }

        // Disarm immediately so a second callback during the same alert does
        // not silently get answered. The user can still answer it manually.
        AutoAnswer.disarm(context)

        Handler(Looper.getMainLooper()).postDelayed(
            { AutoAnswer.routeForHandsFreeCall(context) },
            ROUTE_DELAY_MS,
        )
    }

    private companion object {
        private const val TAG = "VirgilScreener"
        // Give TelecomManager time to move the call into RINGING state before
        // acceptRingingCall(). 500 ms is enough on every device we've tried.
        private const val ACCEPT_DELAY_MS = 500L
        // Give the in-call UI a moment to come up before forcing speaker /
        // volume — some OEMs reset audio state when the call connects.
        private const val ROUTE_DELAY_MS = 800L
    }
}
