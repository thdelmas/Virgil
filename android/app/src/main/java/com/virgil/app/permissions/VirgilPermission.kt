package com.virgil.app.permissions

import android.Manifest
import android.os.Build

/**
 * Single source of truth for every permission Virgil asks of the user.
 * Each entry carries the rationale shown before the request and whether
 * the app's core safety features can function without it.
 */
enum class VirgilPermission(
    val id: String,
    val androidName: String?,
    val title: String,
    val rationale: String,
    val mandatory: Boolean,
    val minSdk: Int = Build.VERSION_CODES.BASE,
    val kind: Kind = Kind.Runtime,
) {
    SMS(
        id = "sms",
        androidName = Manifest.permission.SEND_SMS,
        title = "Send text messages",
        rationale = "If you fall, Virgil will text your family to let them know.",
        mandatory = true,
    ),
    LOCATION(
        id = "location",
        androidName = Manifest.permission.ACCESS_FINE_LOCATION,
        title = "Know where you are",
        rationale = "So your family can find you if something happens.",
        mandatory = true,
    ),
    NOTIFICATIONS(
        id = "notifications",
        androidName = Manifest.permission.POST_NOTIFICATIONS,
        title = "Show reminders",
        rationale = "So Virgil can gently check in with you during the day.",
        mandatory = true,
        minSdk = Build.VERSION_CODES.TIRAMISU,
    ),
    EXACT_ALARM(
        id = "exact_alarm",
        androidName = null,
        title = "Remind you on time",
        rationale = "So Virgil's check-ins come exactly when they should.",
        mandatory = true,
        minSdk = Build.VERSION_CODES.S,
        kind = Kind.ExactAlarm,
    ),
    FULL_SCREEN_INTENT(
        id = "full_screen_intent",
        androidName = null,
        title = "Show on locked screen",
        rationale = "So you can cancel a false alarm without unlocking the phone.",
        mandatory = true,
        minSdk = Build.VERSION_CODES.TIRAMISU,
        kind = Kind.FullScreenIntent,
    ),
    CALL(
        id = "call",
        androidName = Manifest.permission.CALL_PHONE,
        title = "Call your family",
        rationale = "After texting, Virgil can also call your closest person. This one is optional.",
        mandatory = false,
    );

    enum class Kind { Runtime, ExactAlarm, FullScreenIntent }

    fun appliesOnThisDevice(): Boolean = Build.VERSION.SDK_INT >= minSdk

    companion object {
        fun runtime(): List<VirgilPermission> = entries.filter {
            it.kind == Kind.Runtime && it.appliesOnThisDevice()
        }

        fun runtimeMandatory(): List<VirgilPermission> = runtime().filter { it.mandatory }

        fun applicable(): List<VirgilPermission> = entries.filter { it.appliesOnThisDevice() }
    }
}
