package com.virgil.app.ui.settings

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.virgil.app.R

/**
 * Lazy-list item that renders the "Auto-answer emergency contacts" toggle,
 * an explanation of how the feature behaves, and the Call Screener role
 * request button when the user has opted in but not yet granted the role.
 *
 * Kept in its own file so [EmergencySettingsScreen] stays under the project's
 * 500-line cap — the explanatory copy alone is ~20 lines.
 */
fun LazyListScope.autoAnswerSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    hasContacts: Boolean,
) {
    item {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_auto_answer_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_auto_answer_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_auto_answer_switch),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (!hasContacts) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.settings_auto_answer_needs_contact),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = hasContacts,
            )
        }

        if (enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            CallScreenerRolePrompt()
            Spacer(modifier = Modifier.height(12.dp))
            AutoAnswerExplanation()
        }
    }
}

@Composable
private fun CallScreenerRolePrompt() {
    val context = LocalContext.current
    var roleHeld by remember { mutableStateOf(isCallScreeningRoleHeld(context)) }

    // Re-check on resume: the user may grant / revoke the role in system
    // settings and come back. Without this, the button claims a stale state.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                roleHeld = isCallScreeningRoleHeld(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { roleHeld = isCallScreeningRoleHeld(context) }

    LaunchedEffect(Unit) { roleHeld = isCallScreeningRoleHeld(context) }

    if (roleHeld) {
        Text(
            text = stringResource(R.string.settings_auto_answer_role_granted),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        return
    }

    Text(
        text = stringResource(R.string.settings_auto_answer_role_needed),
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@OutlinedButton
            val rm = context.getSystemService(RoleManager::class.java) ?: return@OutlinedButton
            if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return@OutlinedButton
            launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(stringResource(R.string.settings_auto_answer_role_button))
    }
}

@Composable
private fun AutoAnswerExplanation() {
    Text(
        text = stringResource(R.string.settings_auto_answer_explainer),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
    )
}

private fun isCallScreeningRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val rm = context.getSystemService(RoleManager::class.java) ?: return false
    if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return false
    return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}
