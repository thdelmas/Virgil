package com.virgil.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "virgil")

class EmergencyPreferences(private val context: Context) {

    private val contactsKey = stringPreferencesKey("contacts_json")
    private val messageKey = stringPreferencesKey("sms_message")
    private val fallEnabledKey = booleanPreferencesKey("fall_detection_enabled")
    private val countdownKey = intPreferencesKey("countdown_seconds")
    private val checkInEnabledKey = booleanPreferencesKey("checkin_enabled")
    private val checkInIntervalKey = longPreferencesKey("checkin_interval_hours")
    private val checkInSleepStartKey = intPreferencesKey("checkin_sleep_start_hour")
    private val checkInSleepEndKey = intPreferencesKey("checkin_sleep_end_hour")
    private val panicSendersKey = stringSetPreferencesKey("panic_connected_senders")

    val contacts: Flow<List<EmergencyContact>> = context.dataStore.data.map { prefs ->
        val json = prefs[contactsKey] ?: "[]"
        parseContacts(json)
    }

    /**
     * User-customised SMS template, or `null` if the user hasn't overridden the
     * default. A null return lets the dispatcher pull a localized default
     * per-contact (one message per recipient language).
     */
    val smsMessageOverride: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[messageKey]?.takeIf { it.isNotBlank() }
    }

    val fallDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[fallEnabledKey] ?: false
    }

    val countdownSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[countdownKey] ?: 30
    }

    val checkInEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[checkInEnabledKey] ?: false
    }

    /** Check-in interval in hours. */
    val checkInIntervalHours: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[checkInIntervalKey] ?: 6L
    }

    /** Hour of day when sleep mode starts (check-ins paused). */
    val checkInSleepStartHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[checkInSleepStartKey] ?: 23
    }

    /** Hour of day when sleep mode ends (check-ins resume). */
    val checkInSleepEndHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[checkInSleepEndKey] ?: 7
    }

    /**
     * Package names of PanicKit trigger apps the user has explicitly paired
     * with Virgil via the CONNECT intent flow. An incoming TRIGGER broadcast
     * is only honoured when this set is non-empty — see [PanicResponder].
     */
    val connectedPanicSenders: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[panicSendersKey] ?: emptySet()
    }

    suspend fun saveContacts(contacts: List<EmergencyContact>) {
        val jsonArray = JSONArray()
        for (contact in contacts) {
            jsonArray.put(JSONObject().apply {
                put("name", contact.name)
                put("phone", contact.phone)
                put("isPrimary", contact.isPrimary)
                contact.languageCode?.let { put("language", it) }
            })
        }
        context.dataStore.edit { it[contactsKey] = jsonArray.toString() }
    }

    suspend fun saveSmsMessage(message: String?) {
        context.dataStore.edit {
            if (message.isNullOrBlank()) it.remove(messageKey) else it[messageKey] = message
        }
    }

    suspend fun setFallDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[fallEnabledKey] = enabled }
    }

    suspend fun setCountdownSeconds(seconds: Int) {
        context.dataStore.edit { it[countdownKey] = seconds }
    }

    suspend fun setCheckInEnabled(enabled: Boolean) {
        context.dataStore.edit { it[checkInEnabledKey] = enabled }
    }

    suspend fun setCheckInIntervalHours(hours: Long) {
        context.dataStore.edit { it[checkInIntervalKey] = hours }
    }

    suspend fun setCheckInSleepHours(start: Int, end: Int) {
        context.dataStore.edit {
            it[checkInSleepStartKey] = start
            it[checkInSleepEndKey] = end
        }
    }


    suspend fun addConnectedPanicSender(packageName: String) {
        context.dataStore.edit {
            it[panicSendersKey] = (it[panicSendersKey] ?: emptySet()) + packageName
        }
    }

    suspend fun removeConnectedPanicSender(packageName: String) {
        context.dataStore.edit {
            val current = it[panicSendersKey] ?: return@edit
            it[panicSendersKey] = current - packageName
        }
    }

    private fun parseContacts(json: String): List<EmergencyContact> {
        val list = mutableListOf<EmergencyContact>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                EmergencyContact(
                    name = obj.getString("name"),
                    phone = obj.getString("phone"),
                    isPrimary = obj.optBoolean("isPrimary", false),
                    languageCode = obj.optString("language", "").takeIf { it.isNotBlank() },
                )
            )
        }
        return list
    }
}
