package com.virgil.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virgil.app.R
import com.virgil.app.ui.report.ReportActivity

internal fun LazyListScope.helpSection() {
    item { HelpSectionContent() }
}

@Composable
private fun HelpSectionContent() {
    val context = LocalContext.current
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.settings_help_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = stringResource(R.string.settings_help_desc),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            context.startActivity(
                ReportActivity.intent(context, ReportActivity.Type.MISSED_FALL)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            stringResource(R.string.settings_help_missed_fall),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            context.startActivity(
                ReportActivity.intent(context, ReportActivity.Type.FALSE_ALARM)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text(
            stringResource(R.string.settings_help_false_alarm),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
