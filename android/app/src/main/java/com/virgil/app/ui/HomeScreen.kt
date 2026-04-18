package com.virgil.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.virgil.app.R
import com.virgil.app.data.EmergencyContact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier,
    fallEnabled: Boolean,
    checkInEnabled: Boolean,
    contacts: List<EmergencyContact>,
    intervalHours: Long,
    onToggleFall: (Boolean) -> Unit,
    onToggleCheckIn: (Boolean) -> Unit,
    onTest: () -> Unit,
) {
    val anyOn = fallEnabled || checkInEnabled
    val primaryName = contacts.firstOrNull { it.isPrimary }?.name
        ?: contacts.first().name

    Column(
        modifier = modifier
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Icon(
            painter = painterResource(R.drawable.ic_lantern),
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = if (anyOn) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(
                if (anyOn) R.string.home_watching else R.string.home_ready
            ),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (!anyOn) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.home_turn_on_prompt),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(24.dp))

        DisclaimerBanner()

        Spacer(Modifier.height(24.dp))

        FeatureCard(
            icon = { tint ->
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(40.dp),
                )
            },
            title = stringResource(R.string.home_feature_fall_title),
            description = if (fallEnabled) {
                stringResource(R.string.home_feature_fall_on, primaryName)
            } else {
                stringResource(R.string.home_feature_fall_off)
            },
            checked = fallEnabled,
            onCheckedChange = onToggleFall,
        )

        Spacer(Modifier.height(14.dp))

        FeatureCard(
            icon = { tint ->
                Icon(
                    painter = painterResource(R.drawable.ic_lantern),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(40.dp),
                )
            },
            title = stringResource(R.string.home_feature_checkin_title),
            description = if (checkInEnabled) {
                stringResource(R.string.home_feature_checkin_on, intervalHours.toInt())
            } else {
                stringResource(R.string.home_feature_checkin_off)
            },
            checked = checkInEnabled,
            onCheckedChange = onToggleCheckIn,
        )

        Spacer(Modifier.height(28.dp))

        OutlinedButton(
            onClick = onTest,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                stringResource(R.string.home_send_test_button),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = contactSummary(contacts, primaryName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureCard(
    icon: @Composable (Color) -> Unit,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val bg = if (checked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val iconTint = if (checked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }

    Card(
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon(iconTint)
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun DisclaimerBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.home_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun contactSummary(contacts: List<EmergencyContact>, primaryName: String): String =
    when (contacts.size) {
        1 -> stringResource(R.string.home_contacts_summary_one, primaryName)
        2 -> stringResource(R.string.home_contacts_summary_two, primaryName)
        else -> stringResource(
            R.string.home_contacts_summary_many,
            primaryName,
            contacts.size - 1,
        )
    }
