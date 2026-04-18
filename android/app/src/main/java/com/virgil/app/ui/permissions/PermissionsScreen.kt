package com.virgil.app.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var states by remember { mutableStateOf(PermissionChecker.statuses(context)) }

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

    val missingMandatory = states.count {
        it.permission.mandatory && it.status == PermissionChecker.Status.Denied
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.permissions_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Header(missingMandatory = missingMandatory) }

            items(states) { state ->
                PermissionCard(
                    state = state,
                    onTurnOn = {
                        when (state.permission.kind) {
                            VirgilPermission.Kind.Runtime ->
                                state.permission.androidName?.let { runtimeLauncher.launch(it) }
                            VirgilPermission.Kind.ExactAlarm ->
                                activity?.let(::openExactAlarmSettings)
                            VirgilPermission.Kind.FullScreenIntent ->
                                activity?.let(::openFullScreenIntentSettings)
                        }
                    },
                    onManage = { activity?.let(::openAppSettings) },
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    LaunchedEffect(Unit) { refresh() }
}

@Composable
private fun Header(missingMandatory: Int) {
    Spacer(modifier = Modifier.height(8.dp))
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
    Text(title, style = MaterialTheme.typography.headlineMedium, color = color)
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
    )
    Spacer(modifier = Modifier.height(8.dp))
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
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon = iconFor(state.permission), granted = granted)
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(state.permission.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (!state.permission.mandatory) {
                        Text(
                            text = stringResource(R.string.permissions_optional),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(state.permission.rationaleRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (granted) {
                GrantedRow(onManage = onManage)
            } else {
                Button(
                    onClick = onTurnOn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
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
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = stringResource(R.string.permissions_working),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.weight(1f))
        androidx.compose.material3.TextButton(onClick = onManage) {
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
    Surface(color = bg, shape = CircleShape, modifier = Modifier.size(48.dp)) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
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
