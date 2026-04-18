package com.virgil.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.virgil.app.R
import com.virgil.app.data.AppLocale
import com.virgil.app.data.EmergencyContact
import java.util.Locale

/**
 * Dispatches emergency actions: gets location, sends each contact an SMS in
 * their preferred language, then calls the primary contact. All on-device;
 * SMS and calls are the only off-device traffic, and only on alert.
 */
class EmergencyDispatcher(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * @param customTemplate overrides the built-in default for every contact
     *     when non-null; when null, each contact receives the built-in default
     *     translated into their own language.
     */
    fun dispatch(
        contacts: List<EmergencyContact>,
        customTemplate: String?,
        isTest: Boolean = false,
        onComplete: (locationAvailable: Boolean) -> Unit = {},
    ) {
        if (contacts.isEmpty()) {
            onComplete(false)
            return
        }

        if (!isTest) PanicBroadcast.emit(context)

        getLocation { location ->
            for (contact in contacts) {
                val localized = localizedContext(contact.languageCode)
                val template = customTemplate ?: localized.getString(
                    if (isTest) R.string.test_sms_message else R.string.emergency_sms_default
                )
                sendSms(contact.phone, buildMessage(localized, template, location))
            }

            if (!isTest) {
                val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.first()
                makeCall(primaryContact.phone)
            }

            onComplete(location != null)
        }
    }

    private fun localizedContext(languageCode: String?): Context {
        // null → use whatever locale the app context already uses (system or
        // the user's app-wide override). A non-null per-contact override wins.
        val effective = languageCode ?: AppLocale.read(context)
        return AppLocale.wrap(context, effective)
    }

    private fun getLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }

        val cancellationToken = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationToken.token,
        ).addOnSuccessListener { location ->
            // getCurrentLocation returns null-on-success when the system can't
            // produce a fix (location services off, no satellites, cold start).
            // Fall through to lastLocation in that case.
            if (location != null) callback(location) else fallbackToLast(callback)
        }.addOnFailureListener {
            fallbackToLast(callback)
        }
    }

    private fun fallbackToLast(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callback(null)
            return
        }
        fusedLocationClient.lastLocation
            .addOnSuccessListener { callback(it) }
            .addOnFailureListener { callback(null) }
    }

    private fun buildMessage(localized: Context, template: String, location: Location?): String {
        if (location == null) {
            return template + "\n\n" + localized.getString(R.string.sms_location_unavailable)
        }
        val ageMs = (System.currentTimeMillis() - location.time).coerceAtLeast(0)
        return template + formatLocation(
            localized,
            location.latitude,
            location.longitude,
            location.accuracy.toInt(),
            ageMs,
        )
    }

    private fun sendSms(phone: String, message: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val smsManager = context.getSystemService(SmsManager::class.java)
        val parts = try {
            smsManager.divideMessage(message)
        } catch (_: SecurityException) {
            // divideMessage → getGroupIdLevel1 requires READ_PHONE_STATE on some
            // OEMs (Android 11+). We refuse to declare that permission, so split
            // manually at the UCS-2 multipart segment size (safe for any encoding).
            ArrayList(message.chunked(SMS_CHUNK_SIZE))
        }
        smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
    }

    private fun makeCall(phone: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(callIntent)
    }

    internal companion object {
        const val SMS_CHUNK_SIZE = 67
        private const val FRESH_THRESHOLD_SEC = 60L

        internal fun formatLocation(
            context: Context,
            lat: Double,
            lon: Double,
            accuracyM: Int,
            ageMs: Long,
        ): String {
            val ageSuffix = formatAge(context, ageMs)
            val locationWord = context.getString(R.string.sms_location_label)
            val accuracyLine = context.getString(R.string.sms_accuracy, accuracyM)
            val head = if (ageSuffix.isEmpty()) "$locationWord:" else "$locationWord $ageSuffix:"
            return "\n\n$head https://maps.google.com/?q=$lat,$lon" +
                "\n($lat, $lon)" +
                "\n$accuracyLine"
        }

        private fun formatAge(context: Context, ageMs: Long): String {
            val ageSec = ageMs / 1000
            return when {
                ageSec < FRESH_THRESHOLD_SEC -> ""
                ageSec < 3600 -> context.getString(R.string.sms_age_minutes, (ageSec / 60).toInt())
                else -> {
                    val hours = (ageSec / 3600).toInt()
                    val minutes = ((ageSec % 3600) / 60).toInt()
                    context.getString(R.string.sms_age_hours, hours, minutes)
                }
            }
        }
    }
}
