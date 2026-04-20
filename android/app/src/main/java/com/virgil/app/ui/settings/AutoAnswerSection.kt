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
 * Lazy-list item that renders the "Auto-answer emergency contacts" toggle.
 *
 * The switch drives both the Virgil preference and the Android
 * ROLE_CALL_SCREENING grant — toggling on launches the system role dialog
 * when needed, and a denial reverts the switch. The row is tagged as a
 * Virgil setting; the Permissions section below owns the system-level view.
 */
fun LazyListScope.autoAnswerSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    hasContacts: Boolean,
) {
    item {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.settings_tag_virgil_setting),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(2.dp))
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
        AutoAnswerSwitchRow(
            enabled = enabled,
            hasContacts = hasContacts,
            onToggle = onToggle,
        )
        if (enabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.settings_auto_answer_explainer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun AutoAnswerSwitchRow(
    enabled: Boolean,
    hasContacts: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var roleHeld by remember { mutableStateOf(isCallScreeningRoleHeld(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) roleHeld = isCallScreeningRoleHeld(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        val held = isCallScreeningRoleHeld(context)
        roleHeld = held
        // System denial — keep Virgil's pref in sync with reality.
        if (!held) onToggle(false)
    }

    LaunchedEffect(Unit) { roleHeld = isCallScreeningRoleHeld(context) }

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
            val subtitle = when {
                !hasContacts -> stringResource(R.string.settings_auto_answer_needs_contact)
                enabled && !roleHeld -> stringResource(R.string.settings_auto_answer_role_needed)
                else -> null
            }
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        Switch(
            checked = enabled,
            onCheckedChange = { wantOn ->
                if (!wantOn) {
                    onToggle(false)
                    return@Switch
                }
                onToggle(true)
                if (roleHeld) return@Switch
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@Switch
                val rm = context.getSystemService(RoleManager::class.java) ?: return@Switch
                if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return@Switch
                roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
            },
            enabled = hasContacts,
        )
    }
}

private fun isCallScreeningRoleHeld(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val rm = context.getSystemService(RoleManager::class.java) ?: return false
    if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return false
    return rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}
