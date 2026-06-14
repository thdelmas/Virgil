package com.virgil.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.virgil.app.R
import com.virgil.app.service.BatteryGuard

internal fun LazyListScope.reliabilitySection() {
    item { ReliabilitySectionContent() }
}

@Composable
private fun ReliabilitySectionContent() {
    val context = LocalContext.current
    var exempt by remember { mutableStateOf(BatteryGuard.isIgnoringBatteryOptimizations(context)) }

    // The exemption is granted in system UI, so re-check whenever we return.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exempt = BatteryGuard.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val autoStart = remember { BatteryGuard.oemAutoStartIntent(context) }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.settings_reliability_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.settings_reliability_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(
            if (exempt) R.string.settings_reliability_ok else R.string.settings_reliability_warn
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = if (exempt) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    )

    if (!exempt) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runCatching { context.startActivity(BatteryGuard.batteryOptimizationSettingsIntent()) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                stringResource(R.string.settings_reliability_battery_button),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    if (autoStart != null) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { runCatching { context.startActivity(autoStart) } },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                stringResource(R.string.settings_reliability_autostart_button),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_reliability_autostart_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}
