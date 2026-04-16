package com.virgil.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    private val dmsEnabledKey = booleanPreferencesKey("dead_man_switch_enabled")
    private val dmsIntervalKey = longPreferencesKey("dms_interval_hours")
    private val dmsSleepStartKey = intPreferencesKey("dms_sleep_start_hour")
    private val dmsSleepEndKey = intPreferencesKey("dms_sleep_end_hour")

    val contacts: Flow<List<EmergencyContact>> = context.dataStore.data.map { prefs ->
        val json = prefs[contactsKey] ?: "[]"
        parseContacts(json)
    }

    val smsMessage: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[messageKey] ?: context.getString(
            com.virgil.app.R.string.emergency_sms_default
        )
    }

    val fallDetectionEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[fallEnabledKey] ?: false
    }

    val countdownSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[countdownKey] ?: 30
    }

    val deadManSwitchEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[dmsEnabledKey] ?: false
    }

    /** Check-in interval in hours. */
    val dmsIntervalHours: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[dmsIntervalKey] ?: 6L
    }

    /** Hour of day when sleep mode starts (check-ins paused). */
    val dmsSleepStartHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[dmsSleepStartKey] ?: 23
    }

    /** Hour of day when sleep mode ends (check-ins resume). */
    val dmsSleepEndHour: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[dmsSleepEndKey] ?: 7
    }

    suspend fun saveContacts(contacts: List<EmergencyContact>) {
        val jsonArray = JSONArray()
        for (contact in contacts) {
            jsonArray.put(JSONObject().apply {
                put("name", contact.name)
                put("phone", contact.phone)
                put("isPrimary", contact.isPrimary)
            })
        }
        context.dataStore.edit { it[contactsKey] = jsonArray.toString() }
    }

    suspend fun saveSmsMessage(message: String) {
        context.dataStore.edit { it[messageKey] = message }
    }

    suspend fun setFallDetectionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[fallEnabledKey] = enabled }
    }

    suspend fun setCountdownSeconds(seconds: Int) {
        context.dataStore.edit { it[countdownKey] = seconds }
    }

    suspend fun setDeadManSwitchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[dmsEnabledKey] = enabled }
    }

    suspend fun setDmsIntervalHours(hours: Long) {
        context.dataStore.edit { it[dmsIntervalKey] = hours }
    }

    suspend fun setDmsSleepHours(start: Int, end: Int) {
        context.dataStore.edit {
            it[dmsSleepStartKey] = start
            it[dmsSleepEndKey] = end
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
                )
            )
        }
        return list
    }
}
