package com.virgil.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.virgil.app.R
import com.virgil.app.data.AppLocale
import com.virgil.app.data.EmergencyContact
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Dispatches emergency actions: gets location, sends each contact an SMS in
 * their preferred language, then calls the primary contact. All on-device;
 * SMS and calls are the only off-device traffic, and only on alert.
 */
class EmergencyDispatcher(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    data class Result(val locationAvailable: Boolean, val smsSent: Int, val smsFailed: Int)

    /**
     * @param customTemplate overrides the built-in default for every contact
     *     when non-null; when null, each contact receives the built-in default
     *     translated into their own language.
     */
    fun dispatch(
        contacts: List<EmergencyContact>,
        customTemplate: String?,
        isTest: Boolean = false,
        onComplete: (Result) -> Unit = {},
    ) {
        Log.i(TAG, "dispatch: contacts=${contacts.size} isTest=$isTest")
        if (contacts.isEmpty()) {
            onComplete(Result(locationAvailable = false, smsSent = 0, smsFailed = 0))
            return
        }

        if (!isTest) PanicBroadcast.emit(context)

        getLocation { location ->
            var sent = 0
            var failed = 0
            for (contact in contacts) {
                val localized = localizedContext(contact.languageCode)
                val template = customTemplate ?: localized.getString(
                    if (isTest) R.string.test_sms_message else R.string.emergency_sms_default
                )
                if (sendSms(contact.phone, buildMessage(localized, template, location))) sent++
                else failed++
            }

            if (!isTest) {
                val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.first()
                makeCall(primaryContact.phone)
            }

            Log.i(TAG, "dispatch done: sent=$sent failed=$failed locationAvailable=${location != null}")
            onComplete(Result(location != null, sent, failed))
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
            val unavailable = localized.getString(R.string.sms_location_unavailable)
            val sentAt = localized.getString(
                R.string.sms_alert_time,
                formatDateTime(System.currentTimeMillis()),
            )
            return "$template\n\n$unavailable\n$sentAt"
        }
        val altitudeM = if (location.hasAltitude()) location.altitude.toInt() else null
        val verticalAccM = if (location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters.toInt()
        } else {
            null
        }
        return template + formatLocation(
            localized,
            location.latitude,
            location.longitude,
            location.accuracy.toInt(),
            fixTimeMs = location.time,
            altitudeM = altitudeM,
            verticalAccuracyM = verticalAccM,
        )
    }

    private fun sendSms(phone: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "sendSms skipped for ${maskPhone(phone)}: SEND_SMS not granted")
            return false
        }

        val smsManager = context.getSystemService(SmsManager::class.java)
        if (smsManager == null) {
            Log.w(TAG, "sendSms skipped for ${maskPhone(phone)}: SmsManager unavailable")
            return false
        }
        val parts = try {
            smsManager.divideMessage(message)
        } catch (_: SecurityException) {
            // divideMessage → getGroupIdLevel1 requires READ_PHONE_STATE on some
            // OEMs (Android 11+). We refuse to declare that permission, so split
            // manually at the UCS-2 multipart segment size (safe for any encoding).
            ArrayList(message.chunked(SMS_CHUNK_SIZE))
        }
        return try {
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            Log.i(TAG, "sendSms ok for ${maskPhone(phone)} (${parts.size} part(s))")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "sendSms threw for ${maskPhone(phone)}: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    private fun makeCall(phone: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "makeCall skipped for ${maskPhone(phone)}: CALL_PHONE not granted")
            return
        }

        val callIntent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$phone")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(callIntent)
            Log.i(TAG, "makeCall dialed ${maskPhone(phone)}")
        } catch (t: Throwable) {
            Log.e(TAG, "makeCall failed for ${maskPhone(phone)}: ${t.javaClass.simpleName}")
        }
    }

    private fun maskPhone(phone: String): String {
        if (phone.length <= 4) return "****"
        val prefix = if (phone.startsWith("+")) {
            // Keep the leading "+" and up to 3 country-code digits so a missing
            // country prefix is diagnosable from logs (e.g. "+33***7009").
            val digits = phone.drop(1).take(3).takeWhile(Char::isDigit)
            "+$digits"
        } else {
            ""
        }
        return "$prefix***${phone.takeLast(4)}"
    }

    internal companion object {
        private const val TAG = "VirgilDispatcher"
        const val SMS_CHUNK_SIZE = 67
        private const val COORD_DECIMALS = 5

        private val DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

        internal fun formatLocation(
            context: Context,
            lat: Double,
            lon: Double,
            accuracyM: Int,
            fixTimeMs: Long,
            altitudeM: Int?,
            verticalAccuracyM: Int?,
        ): String {
            val coords = formatCoords(lat, lon)
            val header = context.getString(R.string.sms_location_label) + ":"
            val fixTime = context.getString(R.string.sms_fix_time, formatDateTime(fixTimeMs))
            val altitude = altitudeM?.let { alt ->
                if (verticalAccuracyM != null) {
                    context.getString(R.string.sms_altitude_with_accuracy, alt, verticalAccuracyM)
                } else {
                    context.getString(R.string.sms_altitude, alt)
                }
            }
            val accuracy = context.getString(R.string.sms_accuracy, accuracyM)
            val gmaps = context.getString(R.string.sms_map_google) +
                ": https://maps.google.com/?q=$coords"
            val osm = context.getString(R.string.sms_map_osm) +
                ": https://osm.org/?mlat=${formatCoord(lat)}&mlon=${formatCoord(lon)}"

            val lines = buildList {
                add(header)
                add(fixTime)
                add(coords)
                altitude?.let(::add)
                add(accuracy)
                add(gmaps)
                add(osm)
            }
            return "\n\n" + lines.joinToString("\n")
        }

        internal fun formatDateTime(
            epochMs: Long,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): String {
            val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zoneId)
            val datePart = zdt.format(DATE_TIME_FORMATTER)
            val offsetPart = formatOffset(zdt.offset.totalSeconds)
            return "$datePart $offsetPart"
        }

        internal fun formatOffset(offsetSeconds: Int): String {
            val sign = if (offsetSeconds >= 0) "+" else "-"
            val absSec = abs(offsetSeconds)
            val hours = absSec / 3600
            val minutes = (absSec % 3600) / 60
            return if (minutes == 0) {
                "GMT$sign$hours"
            } else {
                "GMT$sign$hours:${"%02d".format(minutes)}"
            }
        }

        private fun formatCoord(value: Double): String =
            String.format(Locale.ROOT, "%.${COORD_DECIMALS}f", value)

        private fun formatCoords(lat: Double, lon: Double): String =
            "${formatCoord(lat)}, ${formatCoord(lon)}"
    }
}
