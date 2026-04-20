package com.virgil.app.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import android.app.role.RoleManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.virgil.app.R
import com.virgil.app.permissions.PermissionChecker
import com.virgil.app.permissions.PermissionMonitor
import com.virgil.app.permissions.PermissionState
import com.virgil.app.permissions.VirgilPermission

/**
 * Lifecycle-aware permission-state holder. Refreshes the list whenever the
 * hosting lifecycle resumes (so a user returning from the system Settings app
 * sees their change reflected immediately) and exposes a runtime-permission
 * launcher for the flows that can request in-app.
 */
internal class PermissionSectionState(
    val states: List<PermissionState>,
    val runtimeLauncher: ManagedActivityResultLauncher<String, Boolean>,
    val expanded: Boolean,
    val onToggleExpanded: () -> Unit,
)

@Composable
internal fun rememberPermissionSectionState(): PermissionSectionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var states by remember { mutableStateOf(PermissionChecker.statuses(context)) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        states = PermissionChecker.statuses(context)
        PermissionMonitor.check(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh() }

    LaunchedEffect(Unit) { refresh() }

    return PermissionSectionState(
        states = states,
        runtimeLauncher = runtimeLauncher,
        expanded = expanded,
        onToggleExpanded = { expanded = !expanded },
    )
}

internal fun LazyListScope.permissionsSection(
    state: PermissionSectionState,
    activity: Activity?,
) {
    val missingMandatory = state.states.count {
        it.permission.mandatory && it.status == PermissionChecker.Status.Denied
    }
    val showCards = missingMandatory > 0 || state.expanded
    item {
        PermissionsHeader(
            missingMandatory = missingMandatory,
            showToggle = missingMandatory == 0,
            expanded = state.expanded,
            onToggle = state.onToggleExpanded,
        )
    }
    if (showCards) {
        items(state.states) { pState ->
            PermissionCard(
                state = pState,
                onTurnOn = {
                    when (pState.permission.kind) {
                        VirgilPermission.Kind.Runtime ->
                            pState.permission.androidName?.let { state.runtimeLauncher.launch(it) }
                        VirgilPermission.Kind.ExactAlarm ->
                            activity?.let(::openExactAlarmSettings)
                        VirgilPermission.Kind.FullScreenIntent ->
                            activity?.let(::openFullScreenIntentSettings)
                        VirgilPermission.Kind.CallScreeningRole ->
                            activity?.let(::requestCallScreeningRole)
                    }
                },
                onManage = { activity?.let(::openAppSettings) },
            )
        }
    }
}

@Composable
private fun PermissionsHeader(
    missingMandatory: Int,
    showToggle: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val (title, subtitle, color) = when (missingMandatory) {
        0 -> Triple(
            stringResource(R.string.permissions_all_set_title),
            stringResource(R.string.permissions_all_set_body),
            MaterialTheme.colorScheme.primary,
        )
        1 -> Triple(
            stringResource(R.string.permissions_one_more_title),
            stringResource(R.string.permissions_one_more_body),
            MaterialTheme.colorScheme.error,
        )
        else -> Triple(
            stringResource(R.string.permissions_few_steps_title),
            stringResource(R.string.permissions_few_steps_body, missingMandatory),
            MaterialTheme.colorScheme.error,
        )
    }
    Text(
        text = stringResource(R.string.settings_tag_android_permission),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, color = color)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
    if (showToggle) {
        TextButton(
            onClick = onToggle,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp),
        ) {
            Text(
                stringResource(
                    if (expanded) R.string.permissions_hide_details
                    else R.string.permissions_show_details
                ),
            )
        }
    }
}

@Composable
private fun PermissionCard(
    state: PermissionState,
    onTurnOn: () -> Unit,
    onManage: () -> Unit,
) {
    val granted = state.status == PermissionChecker.Status.Granted
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = iconFor(state.permission), granted = granted)
                Spacer(modifier = Modifier.size(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(state.permission.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!state.permission.mandatory) {
                        Text(
                            text = stringResource(R.string.permissions_optional),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(state.permission.rationaleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (granted) {
                GrantedRow(onManage = onManage)
            } else {
                Button(
                    onClick = onTurnOn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = if (state.permission.mandatory) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_turn_on),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun GrantedRow(onManage: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.permissions_working),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onManage) {
            Text(stringResource(R.string.permissions_change))
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, granted: Boolean) {
    val bg = if (granted) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    }
    Surface(color = bg, shape = CircleShape, modifier = Modifier.size(40.dp)) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

private fun iconFor(permission: VirgilPermission): ImageVector = when (permission) {
    VirgilPermission.SMS -> Icons.Filled.Sms
    VirgilPermission.LOCATION -> Icons.Filled.LocationOn
    VirgilPermission.NOTIFICATIONS -> Icons.Filled.Notifications
    VirgilPermission.EXACT_ALARM -> Icons.Filled.Alarm
    VirgilPermission.FULL_SCREEN_INTENT -> Icons.Filled.ScreenLockPortrait
    VirgilPermission.CALL -> Icons.Filled.Phone
    VirgilPermission.ANSWER_CALLS -> Icons.AutoMirrored.Filled.PhoneCallback
    VirgilPermission.CALL_SCREENING -> Icons.AutoMirrored.Filled.CallReceived
}

private fun openAppSettings(activity: Activity) {
    activity.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", activity.packageName, null),
        )
    )
}

private fun openExactAlarmSettings(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    activity.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
    )
}

private fun openFullScreenIntentSettings(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    activity.startActivity(
        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
    )
}

private fun requestCallScreeningRole(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val rm = activity.getSystemService(RoleManager::class.java) ?: return
    if (!rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return
    activity.startActivity(rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
}
