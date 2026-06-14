package com.virgil.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virgil.app.R
import com.virgil.app.service.ServiceHeartbeat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app readout of [ServiceHeartbeat]. The log lives in private storage, so on
 * a signed release build (the one you actually run a multi-day survival test on)
 * this is the only way to see whether the watcher kept beating.
 */
@Composable
internal fun HeartbeatStatus() {
    val context = LocalContext.current
    val summary by produceState<ServiceHeartbeat.Companion.Summary?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { ServiceHeartbeat.summarize(context) }
    }

    Text(
        text = stringResource(R.string.heartbeat_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.heartbeat_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(8.dp))

    val s = summary
    if (s?.lastWallMs == null) {
        Text(
            text = stringResource(R.string.heartbeat_none),
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }

    Column {
        val ago = formatDuration(System.currentTimeMillis() - s.lastWallMs)
        Text(
            text = stringResource(R.string.heartbeat_alive, ago, s.beats),
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (s.gapCount == 0) {
                stringResource(R.string.heartbeat_gaps_none)
            } else {
                stringResource(R.string.heartbeat_gaps, s.gapCount, formatDuration(s.longestGapMs))
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (s.gapCount == 0) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        totalMinutes > 0 -> "${minutes}m"
        else -> "${ms / 1000}s"
    }
}
