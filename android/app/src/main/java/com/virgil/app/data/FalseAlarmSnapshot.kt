package com.virgil.app.data

import android.content.Context
import android.os.Build
import com.virgil.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Snapshot of the last alarm that the user hold-disarmed. Captured at the
 * moment the countdown is cancelled so the user has something concrete to
 * describe when reporting a false alarm — timestamp, trigger type, phase
 * reached, peak impact (for fall triggers), device info.
 *
 * Persisted as a single JSON file in app-internal storage so it survives
 * app restart but never leaves the device unless the user explicitly opts
 * in during report composition. On consent the data is included verbatim
 * in an outgoing email; otherwise only the free-text description is sent.
 */
data class FalseAlarmSnapshot(
    val triggeredAt: Long,
    val dismissedAt: Long,
    val triggerType: String,
    val peakAccel: Float?,
    val phaseAtDismiss: Int,
    val secondsLeftAtDismiss: Int,
    val appVersion: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidVersion: Int,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("triggeredAt", triggeredAt)
        put("dismissedAt", dismissedAt)
        put("triggerType", triggerType)
        if (peakAccel != null) put("peakAccel", peakAccel.toDouble())
        put("phaseAtDismiss", phaseAtDismiss)
        put("secondsLeftAtDismiss", secondsLeftAtDismiss)
        put("appVersion", appVersion)
        put("deviceManufacturer", deviceManufacturer)
        put("deviceModel", deviceModel)
        put("androidVersion", androidVersion)
    }

    /**
     * Human-readable block suitable for pasting into an email body.
     * Explicit, labelled fields — no personal data (location, contacts,
     * identifiers) ever appears here.
     */
    fun toReportText(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.ROOT)
        val triggered = fmt.format(Date(triggeredAt))
        val dismissed = fmt.format(Date(dismissedAt))
        val heldMs = (dismissedAt - triggeredAt).coerceAtLeast(0)
        return buildString {
            appendLine("Triggered at: $triggered")
            appendLine("Dismissed at: $dismissed (${heldMs / 1000}s later)")
            appendLine("Trigger type: $triggerType")
            if (peakAccel != null) {
                appendLine("Peak acceleration: %.2f m/s^2".format(Locale.ROOT, peakAccel))
            }
            appendLine("Phase reached: $phaseAtDismiss of 4")
            appendLine("Seconds left in phase at dismiss: $secondsLeftAtDismiss")
            appendLine("App version: $appVersion")
            appendLine("Device: $deviceManufacturer $deviceModel")
            appendLine("Android API: $androidVersion")
        }
    }

    companion object {
        fun capture(
            triggeredAt: Long,
            triggerType: String,
            peakAccel: Float?,
            phaseAtDismiss: Int,
            secondsLeftAtDismiss: Int,
        ): FalseAlarmSnapshot = FalseAlarmSnapshot(
            triggeredAt = triggeredAt,
            dismissedAt = System.currentTimeMillis(),
            triggerType = triggerType,
            peakAccel = peakAccel,
            phaseAtDismiss = phaseAtDismiss,
            secondsLeftAtDismiss = secondsLeftAtDismiss,
            appVersion = BuildConfig.VERSION_NAME,
            deviceManufacturer = Build.MANUFACTURER ?: "unknown",
            deviceModel = Build.MODEL ?: "unknown",
            androidVersion = Build.VERSION.SDK_INT,
        )

        fun fromJson(json: JSONObject): FalseAlarmSnapshot? = runCatching {
            FalseAlarmSnapshot(
                triggeredAt = json.getLong("triggeredAt"),
                dismissedAt = json.getLong("dismissedAt"),
                triggerType = json.getString("triggerType"),
                peakAccel = if (json.has("peakAccel")) json.getDouble("peakAccel").toFloat() else null,
                phaseAtDismiss = json.getInt("phaseAtDismiss"),
                secondsLeftAtDismiss = json.getInt("secondsLeftAtDismiss"),
                appVersion = json.getString("appVersion"),
                deviceManufacturer = json.getString("deviceManufacturer"),
                deviceModel = json.getString("deviceModel"),
                androidVersion = json.getInt("androidVersion"),
            )
        }.getOrNull()
    }
}

object FalseAlarmLog {

    private const val FILE_NAME = "last_false_alarm.json"

    fun save(context: Context, snapshot: FalseAlarmSnapshot) {
        runCatching {
            file(context).writeText(snapshot.toJson().toString())
        }
    }

    fun read(context: Context): FalseAlarmSnapshot? = runCatching {
        val f = file(context)
        if (!f.exists()) return null
        val json = JSONObject(f.readText())
        FalseAlarmSnapshot.fromJson(json)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)
}
