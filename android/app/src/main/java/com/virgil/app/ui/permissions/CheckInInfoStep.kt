package com.virgil.app.ui.permissions

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.virgil.app.R

@Composable
internal fun CheckInInfoStep(
    stepNumber: Int,
    totalSteps: Int,
    onContinue: () -> Unit,
) {
    val controller = rememberSoundPreviewController()
    OnboardingScaffold(
        progress = stepNumber to totalSteps,
        scrollable = true,
        content = {
            BigIcon(Icons.Filled.NotificationsActive)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.onboarding_checkin_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.onboarding_checkin_how),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(20.dp))
            SoundPreviewRow(
                title = stringResource(R.string.sound_ring_title),
                description = stringResource(R.string.sound_ring_desc),
                soundKey = "ring",
                playing = controller.playing,
                onStart = controller.start,
                onStop = controller.stop,
            )
        },
        primaryAction = {
            PrimaryButton(stringResource(R.string.onboarding_continue)) {
                controller.stop()
                onContinue()
            }
        },
    )
}
