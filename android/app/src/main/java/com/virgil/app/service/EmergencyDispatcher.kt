package com.virgil.app.service

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.virgil.app.R
import com.virgil.app.data.AppLocale
import com.virgil.app.data.EmergencyContact
import com.virgil.app.data.InteractionTracker
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Dispatches emergency actions: gets location, sends each contact an SMS in
 * their preferred language, then calls the primary contact. All on-device;
 * SMS and calls are the only off-device traffic, and only on alert.
 *
 * Per-message sent status comes back from the radio layer via PendingIntent
 * broadcasts (Android's [SmsManager.sendMultipartTextMessage] sentIntent
 * contract). A "sent" result means the modem accepted the message — not just
 * that the framework call returned without throwing — so the post-alert UI
 * can honestly show which contacts got the alert and which didn't.
 */
class EmergencyDispatcher(private val context: Context) {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    enum class TriggerType { FALL, NO_RESPONSE, PANIC }

    enum class SmsStatus {
        SENT,
        FAILED_NO_PERMISSION,
        FAILED_NO_SERVICE,
        FAILED_RADIO_OFF,
        FAILED_GENERIC,
        FAILED_TIMEOUT,
        FAILED_FRAMEWORK;

        val isSuccess: Boolean get() = this == SENT
    }

    data class ContactResult(
        val name: String,
        val phone: String,
        val status: SmsStatus,
    )

    data class Result(
        val locationAvailable: Boolean,
        val contactResults: List<ContactResult>,
    ) {
        val smsSent: Int get() = contactResults.count { it.status.isSuccess }
        val smsFailed: Int get() = contactResults.size - smsSent
    }

    /**
     * @param customTemplate overrides the built-in default for every contact
     *     when non-null; when null, each contact receives the built-in default
     *     translated into their own language. The default differs per
     *     [triggerType] so responders can triage — fall vs missed check-in
     *     mean different things.
     */
    fun dispatch(
        contacts: List<EmergencyContact>,
        customTemplate: String?,
        triggerType: TriggerType = TriggerType.FALL,
        isTest: Boolean = false,
        onComplete: (Result) -> Unit = {},
    ) {
        Log.i(TAG, "dispatch: contacts=${contacts.size} trigger=$triggerType isTest=$isTest")
        if (contacts.isEmpty()) {
            onComplete(Result(locationAvailable = false, contactResults = emptyList()))
            return
        }

        if (!isTest) PanicBroadcast.emit(context)

        val defaultRes = when {
            isTest -> R.string.test_sms_message
            triggerType == TriggerType.FALL -> R.string.emergency_sms_fall
            triggerType == TriggerType.PANIC -> R.string.emergency_sms_panic
            else -> R.string.emergency_sms_no_response
        }

        val lastActivityMs = if (isTest) 0L else InteractionTracker.lastInteractionMs(context)

        getLocation { location ->
            val statuses = arrayOfNulls<SmsStatus>(contacts.size)
            val pending = AtomicInteger(contacts.size)
            val finalized = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())

            val finalize = finalize@{
                if (!finalized.compareAndSet(false, true)) return@finalize
                handler.removeCallbacksAndMessages(null)
                val perContact = contacts.mapIndexed { i, c ->
                    ContactResult(
                        name = c.name,
                        phone = c.phone,
                        status = statuses[i] ?: SmsStatus.FAILED_TIMEOUT,
                    )
                }
                val sent = perContact.count { it.status.isSuccess }
                val failed = perContact.size - sent
                Log.i(TAG, "dispatch done: sent=$sent failed=$failed locationAvailable=${location != null}")
                onComplete(Result(location != null, perContact))
            }

            // Hard ceiling: if confirmations don't arrive (radio stuck, modem
            // bug, OEM weirdness) we still need to finish so the UI updates
            // and the bystander siren takes over. Any unresolved contact is
            // marked TIMEOUT — distinct from a clean radio failure.
            handler.postDelayed({ finalize() }, SMS_VERIFY_TIMEOUT_MS)

            for ((i, contact) in contacts.withIndex()) {
                val localized = localizedContext(contact.languageCode)
                val template = customTemplate ?: localized.getString(defaultRes)
                val body = buildMessage(localized, template, location, lastActivityMs)
                sendSmsVerified(contact.phone, body) { status ->
                    statuses[i] = status
                    if (pending.decrementAndGet() == 0) finalize()
                }
            }

            // Call the primary regardless of SMS confirmation — radio waits
            // would silence the loudest signal we have. PANIC skips: the
            // alarm is screaming over the mic, contact decides via SMS.
            if (!isTest && triggerType != TriggerType.PANIC) {
                val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.first()
                makeCall(primaryContact.phone)
            }
        }
    }

    private fun localizedContext(languageCode: String?): Context {
        // null → use whatever locale the app context already uses (system or
        // the user's app-wide override). A non-null per-contact override wins.
        val effective = languageCode ?: AppLocale.read(context)
        return AppLocale.wrap(context, effective)
    }

    /**
     * Sends a one-off, non-emergency introduction SMS to a contact the user
     * just added, in that contact's preferred language. No location, no panic
     * broadcast, no call — just a heads-up so the recipient knows to expect
     * alerts from this number. Contract matches docs/COMPLIANCE.md §10.
     */
    fun sendIntro(contact: EmergencyContact): Boolean {
        val localized = localizedContext(contact.languageCode)
        val template = localized.getString(R.string.intro_sms_default)
        return sendSmsFireAndForget(contact.phone, template)
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

    private fun buildMessage(
        localized: Context,
        template: String,
        location: Location?,
        lastActivityMs: Long,
    ): String {
        val lastActivityLine = if (lastActivityMs > 0L) {
            localized.getString(R.string.sms_last_activity, formatDateTime(lastActivityMs))
        } else {
            null
        }
        if (location == null) {
            val unavailable = localized.getString(R.string.sms_location_unavailable)
            val sentAt = localized.getString(
                R.string.sms_alert_time,
                formatDateTime(System.currentTimeMillis()),
            )
            val tail = buildList {
                add(unavailable)
                add(sentAt)
                lastActivityLine?.let(::add)
            }.joinToString("\n")
            return "$template\n\n$tail"
        }
        val altitudeM = if (location.hasAltitude()) location.altitude.toInt() else null
        val verticalAccM = if (location.hasVerticalAccuracy()) {
            location.verticalAccuracyMeters.toInt()
        } else {
            null
        }
        val locationBlock = formatLocation(
            localized,
            location.latitude,
            location.longitude,
            location.accuracy.toInt(),
            fixTimeMs = location.time,
            altitudeM = altitudeM,
            verticalAccuracyM = verticalAccM,
        )
        return template + locationBlock + (lastActivityLine?.let { "\n$it" } ?: "")
    }

    private fun smsManagerOrNull(): SmsManager? =
        context.getSystemService(SmsManager::class.java)

    private fun divideMessageSafe(smsManager: SmsManager, message: String): ArrayList<String> =
        try {
            smsManager.divideMessage(message)
        } catch (_: SecurityException) {
            // divideMessage → getGroupIdLevel1 requires READ_PHONE_STATE on some
            // OEMs (Android 11+). We refuse to declare that permission, so split
            // manually at the UCS-2 multipart segment size (safe for any encoding).
            ArrayList(message.chunked(SMS_CHUNK_SIZE))
        }

    /**
     * Async send that listens to the radio layer's per-part sent broadcast and
     * reports an honest [SmsStatus] — including the carrier-side failure
     * reason when one is reported. Caller MUST eventually receive exactly one
     * [onResult] callback (the timeout in [dispatch] is the upstream backstop).
     */
    private fun sendSmsVerified(
        phone: String,
        message: String,
        onResult: (SmsStatus) -> Unit,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "sendSms skipped for ${maskPhone(phone)}: SEND_SMS not granted")
            onResult(SmsStatus.FAILED_NO_PERMISSION)
            return
        }
        val smsManager = smsManagerOrNull()
        if (smsManager == null) {
            Log.w(TAG, "sendSms skipped for ${maskPhone(phone)}: SmsManager unavailable")
            onResult(SmsStatus.FAILED_FRAMEWORK)
            return
        }

        val parts = divideMessageSafe(smsManager, message)
        val partCount = parts.size
        // Unique action per send so each receiver only hears its own parts.
        val action = "${context.packageName}.SMS_SENT.${UUID.randomUUID()}"
        val partsLeft = AtomicInteger(partCount)
        val worstStatus = AtomicInteger(Int.MIN_VALUE)
        val finalized = AtomicBoolean(false)

        lateinit var receiver: BroadcastReceiver
        val emit = emit@{ status: SmsStatus ->
            if (!finalized.compareAndSet(false, true)) return@emit
            runCatching { context.unregisterReceiver(receiver) }
            onResult(status)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val rc = resultCode
                if (rc != Activity.RESULT_OK) {
                    // Keep the worst (highest-numbered) error code — distinct
                    // SmsManager error codes are positive small ints, all ≥1.
                    val cur = worstStatus.get()
                    if (rc > cur) worstStatus.set(rc)
                }
                if (partsLeft.decrementAndGet() == 0) {
                    val worst = worstStatus.get()
                    val finalStatus = if (worst == Int.MIN_VALUE) {
                        SmsStatus.SENT
                    } else {
                        mapSmsErrorCode(worst)
                    }
                    Log.i(
                        TAG,
                        "sendSms confirm ${maskPhone(phone)}: status=$finalStatus parts=$partCount",
                    )
                    emit(finalStatus)
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(action),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val sentIntents = ArrayList<PendingIntent>(partCount)
        for (i in 0 until partCount) {
            val intent = Intent(action).setPackage(context.packageName)
            val pi = PendingIntent.getBroadcast(
                context,
                i,
                intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )
            sentIntents.add(pi)
        }

        try {
            smsManager.sendMultipartTextMessage(phone, null, parts, sentIntents, null)
            Log.i(TAG, "sendSms queued ${maskPhone(phone)} ($partCount part(s))")
        } catch (t: Throwable) {
            Log.e(TAG, "sendSms threw for ${maskPhone(phone)}: ${t.javaClass.simpleName}: ${t.message}")
            emit(SmsStatus.FAILED_FRAMEWORK)
        }
    }

    /**
     * Synchronous fire-and-forget send for non-emergency intro messages where
     * the user is already in the app and can re-send manually if needed. Kept
     * separate from the verified path so the emergency flow's accuracy
     * guarantees aren't paid for by every intro SMS.
     */
    private fun sendSmsFireAndForget(phone: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "sendSms skipped for ${maskPhone(phone)}: SEND_SMS not granted")
            return false
        }
        val smsManager = smsManagerOrNull() ?: return false
        val parts = divideMessageSafe(smsManager, message)
        return try {
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
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

        // Modem usually confirms within 1–3s; 8s leaves headroom for slow
        // networks without holding up the bystander siren handoff for long.
        const val SMS_VERIFY_TIMEOUT_MS = 8_000L

        private val DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

        internal fun mapSmsErrorCode(code: Int): SmsStatus = when (code) {
            SmsManager.RESULT_ERROR_NO_SERVICE -> SmsStatus.FAILED_NO_SERVICE
            SmsManager.RESULT_ERROR_RADIO_OFF -> SmsStatus.FAILED_RADIO_OFF
            else -> SmsStatus.FAILED_GENERIC
        }

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
