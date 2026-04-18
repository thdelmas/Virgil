package com.virgil.app.service

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.telephony.PhoneNumberUtils
import android.util.Log
import com.virgil.app.data.EmergencyContact
import com.virgil.app.data.EmergencyPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Helpers for the auto-answer feature: arming the window after a dispatch,
 * matching an incoming number against saved contacts, and routing audio
 * into speakerphone at a usable volume once a call is accepted.
 *
 * The siren is stopped here too — the whole point of auto-answering is that
 * the user can hear and be heard, which a full-volume alarm tone defeats.
 */
object AutoAnswer {

    private const val TAG = "VirgilAutoAnswer"

    /** How long after a dispatch the screener will accept callbacks. */
    const val ARM_WINDOW_MS: Long = 15 * 60 * 1000L

    /**
     * Mark auto-answer as armed for [ARM_WINDOW_MS] from now. Called from
     * [EmergencyDispatcher] after an alert goes out. No-op if the user hasn't
     * enabled the feature — the screener still reads the flag as a secondary
     * guard.
     */
    fun arm(context: Context) {
        runBlocking {
            val prefs = EmergencyPreferences(context)
            if (!prefs.autoAnswerEnabled.first()) return@runBlocking
            prefs.setAutoAnswerArmedUntil(System.currentTimeMillis() + ARM_WINDOW_MS)
            Log.i(TAG, "armed for ${ARM_WINDOW_MS / 1000}s")
        }
    }

    fun disarm(context: Context) {
        runBlocking {
            EmergencyPreferences(context).setAutoAnswerArmedUntil(0L)
        }
    }

    fun isNumberAnEmergencyContact(number: String?, contacts: List<EmergencyContact>): Boolean {
        if (number.isNullOrBlank()) return false
        return contacts.any { numbersMatch(it.phone, number) }
    }

    /**
     * Loose equality across formats: "+33 6 12 34 56 78" == "0612345678" when
     * the trailing digits agree. Uses the platform helper so variations in
     * country code / grouping / punctuation do not cause us to miss a
     * legitimate callback from a saved contact.
     */
    fun numbersMatch(saved: String, incoming: String): Boolean {
        if (saved.isBlank() || incoming.isBlank()) return false
        if (PhoneNumberUtils.compare(saved, incoming)) return true
        // Fallback: tail-match on the stripped digits. PhoneNumberUtils.compare
        // can be overly conservative for short national numbers on some OEMs.
        val a = digitsOnly(saved)
        val b = digitsOnly(incoming)
        if (a.isEmpty() || b.isEmpty()) return false
        val tail = minOf(a.length, b.length, 7)
        return a.takeLast(tail) == b.takeLast(tail)
    }

    private fun digitsOnly(s: String): String = s.filter(Char::isDigit)

    /**
     * Ask the siren service to shut down cleanly. Falls back to a direct
     * [AttentionSound.stop] in case the service isn't running (e.g. the alert
     * was sent silently as a test path).
     */
    private fun silenceSiren(context: Context) {
        val intent = Intent(EmergencySirenService.ACTION_SILENCE).setPackage(context.packageName)
        runCatching { context.sendBroadcast(intent) }
        AttentionSound.stop()
    }

    /**
     * Switch audio to the configuration the user needs while talking to the
     * contact who called them back: loud, hands-free, voice-call routing.
     * Stops the alarm siren too — leaving it on drowns out the conversation.
     * Best-effort on every line: a denied setSpeakerphoneOn should not stop
     * the volume raise, and vice-versa.
     */
    fun routeForHandsFreeCall(context: Context) {
        silenceSiren(context)

        val am = context.getSystemService(AudioManager::class.java) ?: return
        runCatching { am.mode = AudioManager.MODE_IN_CALL }
            .onFailure { Log.w(TAG, "mode failed: ${it.javaClass.simpleName}") }
        runCatching { am.isSpeakerphoneOn = true }
            .onFailure { Log.w(TAG, "speakerphone failed: ${it.javaClass.simpleName}") }

        val max = runCatching { am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) }
            .getOrDefault(0)
        if (max > 0) {
            runCatching {
                am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, max, 0)
            }.onFailure { Log.w(TAG, "voice volume failed: ${it.javaClass.simpleName}") }
        }
        Log.i(TAG, "hands-free routing applied")
    }
}
