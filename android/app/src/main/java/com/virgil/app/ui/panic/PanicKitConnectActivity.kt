package com.virgil.app.ui.panic

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.virgil.app.R
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.service.PanicResponder
import kotlinx.coroutines.launch

/**
 * Pairing endpoint for PanicKit trigger apps.
 *
 *  - `ACTION_CONNECT`: prompts the user to confirm that the calling app may
 *    fire Virgil's panic flow. On allow, the caller's package is added to
 *    the connected-senders set; the [com.virgil.app.service.PanicKitReceiver]
 *    will then honour TRIGGER broadcasts.
 *  - `ACTION_DISCONNECT`: silently revokes pairing for the caller — the user
 *    already chose this action in the trigger app's UI, no second prompt.
 *
 * Anonymous callers (`getCallingPackage() == null`, e.g. broadcast or shell)
 * are rejected: pairing is meaningless without a verifiable sender package.
 */
class PanicKitConnectActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sender = callingPackage
        if (sender == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        when (intent?.action) {
            PanicResponder.ACTION_CONNECT -> showConnectPrompt(sender)
            PanicResponder.ACTION_DISCONNECT -> revoke(sender)
            else -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }
    }

    private fun showConnectPrompt(sender: String) {
        val appLabel = readAppLabel(sender)
        setContent {
            ConnectDialog(
                appLabel = appLabel,
                onAllow = {
                    lifecycleScope.launch {
                        EmergencyPreferences(applicationContext).addConnectedPanicSender(sender)
                        setResult(Activity.RESULT_OK)
                        finish()
                    }
                },
                onDeny = {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                },
            )
        }
    }

    private fun revoke(sender: String) {
        lifecycleScope.launch {
            EmergencyPreferences(applicationContext).removeConnectedPanicSender(sender)
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun readAppLabel(pkg: String): String = try {
        val pm = packageManager
        val info = pm.getApplicationInfo(pkg, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        pkg
    }
}

@Composable
private fun ConnectDialog(
    appLabel: String,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = { Text(stringResource(R.string.panic_connect_title)) },
        text = { Text(stringResource(R.string.panic_connect_body, appLabel)) },
        confirmButton = {
            TextButton(onClick = onAllow) {
                Text(stringResource(R.string.panic_connect_allow))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(stringResource(R.string.panic_connect_deny))
            }
        },
    )
}
