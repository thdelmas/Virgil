package com.virgil.app.ui.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virgil.app.R
import com.virgil.app.service.EmergencyDispatcher

private enum class SirenOutcome { ALL_SENT, PARTIAL, ALL_FAILED }

@Composable
internal fun DispatchingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF212121)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.siren_dispatching),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun SirenActiveScreen(
    sent: Int,
    failed: Int,
    contactResults: List<EmergencyDispatcher.ContactResult>,
    onStop: () -> Unit,
) {
    val outcome = when {
        sent == 0 -> SirenOutcome.ALL_FAILED
        failed == 0 -> SirenOutcome.ALL_SENT
        else -> SirenOutcome.PARTIAL
    }
    val bg = when (outcome) {
        SirenOutcome.ALL_SENT -> Color(0xFF1B5E20)
        SirenOutcome.PARTIAL -> Color(0xFFE65100)
        SirenOutcome.ALL_FAILED -> Color(0xFFB71C1C)
    }
    val titleRes = when (outcome) {
        SirenOutcome.ALL_SENT -> R.string.siren_sent_title
        SirenOutcome.PARTIAL -> R.string.siren_partial_title
        SirenOutcome.ALL_FAILED -> R.string.siren_failed_title
    }
    val bodyRes = when (outcome) {
        SirenOutcome.ALL_SENT -> R.string.siren_sent_body
        SirenOutcome.PARTIAL -> R.string.siren_partial_body
        SirenOutcome.ALL_FAILED -> R.string.siren_failed_body
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 32.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(bodyRes),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
            )
            if (contactResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                ContactStatusList(results = contactResults)
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onStop,
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = CircleShape,
            ) {
                Text(
                    text = stringResource(R.string.siren_stop_button),
                    color = bg,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ContactStatusList(results: List<EmergencyDispatcher.ContactResult>) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        for (r in results) {
            ContactStatusRow(result = r)
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ContactStatusRow(result: EmergencyDispatcher.ContactResult) {
    val ok = result.status.isSuccess
    val symbol = if (ok) "✓" else "✗"
    val statusRes = when (result.status) {
        EmergencyDispatcher.SmsStatus.SENT -> R.string.siren_status_sent
        EmergencyDispatcher.SmsStatus.FAILED_NO_PERMISSION -> R.string.siren_status_no_permission
        EmergencyDispatcher.SmsStatus.FAILED_NO_SERVICE -> R.string.siren_status_no_signal
        EmergencyDispatcher.SmsStatus.FAILED_RADIO_OFF -> R.string.siren_status_radio_off
        EmergencyDispatcher.SmsStatus.FAILED_TIMEOUT -> R.string.siren_status_unconfirmed
        EmergencyDispatcher.SmsStatus.FAILED_GENERIC,
        EmergencyDispatcher.SmsStatus.FAILED_FRAMEWORK -> R.string.siren_status_failed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Text(
            text = symbol,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = result.name.ifBlank { result.phone },
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(statusRes),
            color = Color.White.copy(alpha = if (ok) 0.85f else 1f),
            fontSize = 16.sp,
        )
    }
}
