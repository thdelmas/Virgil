package com.virgil.app.service

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.virgil.app.R

/**
 * Gate the panic-siren stop behind the device's existing screen lock so a
 * thief who grabs the phone can't silence the alarm by tapping "Stop". Uses
 * the platform [BiometricPrompt] (API 28+, available at minSdk 29) so we
 * don't pull in the AndroidX biometric library.
 *
 * Falls open if the user has no device lock configured at all — there is
 * nothing to gate against, and refusing to stop would leave the user with
 * only the 5-minute auto-stop and the power button. Configuring a lock is
 * an Android-level concern, not Virgil's to enforce.
 */
object StopAuthGate {

    fun authThenStop(activity: Activity, onAuthorized: () -> Unit) {
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            onAuthorized()
            return
        }

        val builder = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.panic_stop_auth_title))
            .setDescription(activity.getString(R.string.panic_stop_auth_body))

        // No negative button on either branch: when device-credential fallback
        // is enabled, the platform supplies its own cancel control and rejects
        // a custom negative button.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        val prompt = builder.build()
        val cancelSignal = CancellationSignal()
        val executor = ContextCompat.getMainExecutor(activity)

        prompt.authenticate(
            cancelSignal,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    onAuthorized()
                }
                // Errors and failures: do nothing — the siren keeps wailing,
                // which is the correct behavior for a thief or for an
                // accidental tap that the user dismissed.
            },
        )
    }
}
