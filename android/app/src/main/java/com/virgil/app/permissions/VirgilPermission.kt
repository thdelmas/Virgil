package com.virgil.app.permissions

import android.Manifest
import android.os.Build
import com.virgil.app.R

/**
 * Single source of truth for every permission Virgil asks of the user.
 * Each entry carries the rationale shown before the request and whether
 * the app's core safety features can function without it.
 */
enum class VirgilPermission(
    val id: String,
    val androidName: String?,
    val titleRes: Int,
    val rationaleRes: Int,
    val mandatory: Boolean,
    val minSdk: Int = Build.VERSION_CODES.BASE,
    val kind: Kind = Kind.Runtime,
) {
    SMS(
        id = "sms",
        androidName = Manifest.permission.SEND_SMS,
        titleRes = R.string.perm_sms_title,
        rationaleRes = R.string.perm_sms_rationale,
        mandatory = true,
    ),
    LOCATION(
        id = "location",
        androidName = Manifest.permission.ACCESS_FINE_LOCATION,
        titleRes = R.string.perm_location_title,
        rationaleRes = R.string.perm_location_rationale,
        mandatory = true,
    ),
    NOTIFICATIONS(
        id = "notifications",
        androidName = Manifest.permission.POST_NOTIFICATIONS,
        titleRes = R.string.perm_notifications_title,
        rationaleRes = R.string.perm_notifications_rationale,
        mandatory = true,
        minSdk = Build.VERSION_CODES.TIRAMISU,
    ),
    EXACT_ALARM(
        id = "exact_alarm",
        androidName = null,
        titleRes = R.string.perm_exact_alarm_title,
        rationaleRes = R.string.perm_exact_alarm_rationale,
        mandatory = true,
        minSdk = Build.VERSION_CODES.S,
        kind = Kind.ExactAlarm,
    ),
    FULL_SCREEN_INTENT(
        id = "full_screen_intent",
        androidName = null,
        titleRes = R.string.perm_full_screen_title,
        rationaleRes = R.string.perm_full_screen_rationale,
        mandatory = true,
        minSdk = Build.VERSION_CODES.TIRAMISU,
        kind = Kind.FullScreenIntent,
    ),
    CALL(
        id = "call",
        androidName = Manifest.permission.CALL_PHONE,
        titleRes = R.string.perm_call_title,
        rationaleRes = R.string.perm_call_rationale,
        mandatory = false,
    ),
    ANSWER_CALLS(
        id = "answer_calls",
        androidName = Manifest.permission.ANSWER_PHONE_CALLS,
        titleRes = R.string.perm_answer_title,
        rationaleRes = R.string.perm_answer_rationale,
        mandatory = false,
    ),
    CALL_SCREENING(
        id = "call_screening",
        androidName = null,
        titleRes = R.string.perm_call_screening_title,
        rationaleRes = R.string.perm_call_screening_rationale,
        mandatory = false,
        minSdk = Build.VERSION_CODES.Q,
        kind = Kind.CallScreeningRole,
    );

    enum class Kind { Runtime, ExactAlarm, FullScreenIntent, CallScreeningRole }

    fun appliesOnThisDevice(): Boolean = Build.VERSION.SDK_INT >= minSdk

    companion object {
        fun runtime(): List<VirgilPermission> = entries.filter {
            it.kind == Kind.Runtime && it.appliesOnThisDevice()
        }

        fun runtimeMandatory(): List<VirgilPermission> = runtime().filter { it.mandatory }

        fun applicable(): List<VirgilPermission> = entries.filter { it.appliesOnThisDevice() }
    }
}
