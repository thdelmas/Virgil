package com.virgil.app.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.virgil.app.data.EmergencyContact

/**
 * Dispatches emergency actions: gets location, sends SMS to all contacts,
 * calls the primary contact. All on-device, no network/cloud required for call+SMS.
 */
class EmergencyDispatcher(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fun dispatch(
        contacts: List<EmergencyContact>,
        messageTemplate: String,
        isTest: Boolean = false,
        onComplete: (locationAvailable: Boolean) -> Unit = {},
    ) {
        if (contacts.isEmpty()) {
            onComplete(false)
            return
        }

        if (!isTest) PanicBroadcast.emit(context)

        getLocation { location ->
            val message = buildMessage(messageTemplate, location)

            for (contact in contacts) {
                sendSms(contact.phone, message)
            }

            if (!isTest) {
                val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.first()
                makeCall(primaryContact.phone)
            }

            onComplete(location != null)
        }
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

    private fun buildMessage(template: String, location: Location?): String {
        val locationText = if (location != null) {
            val lat = location.latitude
            val lon = location.longitude
            "\n\nLocation: https://maps.google.com/?q=$lat,$lon" +
                "\n(${lat}, ${lon})" +
                "\nAccuracy: ${location.accuracy.toInt()}m"
        } else {
            "\n\n(Location unavailable)"
        }
        return template + locationText
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

    private companion object {
        const val SMS_CHUNK_SIZE = 67
    }
}
