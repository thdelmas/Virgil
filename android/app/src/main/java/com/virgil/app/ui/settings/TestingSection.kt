package com.virgil.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virgil.app.R
import com.virgil.app.ui.permissions.SoundPreviewRow
import com.virgil.app.ui.permissions.rememberSoundPreviewController

internal fun LazyListScope.testingSection(
    hasContacts: Boolean,
    onShowTestDialog: () -> Unit,
) {
    item {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.testing_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.testing_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = onShowTestDialog,
            enabled = hasContacts,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(
                stringResource(R.string.home_send_test_button),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.testing_sounds_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        val soundController = rememberSoundPreviewController()
        SoundPreviewRow(
            title = stringResource(R.string.sound_ring_title),
            description = stringResource(R.string.sound_ring_desc),
            soundKey = "ring",
            playing = soundController.playing,
            onStart = soundController.start,
            onStop = soundController.stop,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SoundPreviewRow(
            title = stringResource(R.string.sound_siren_title),
            description = stringResource(R.string.sound_siren_desc),
            soundKey = "siren",
            playing = soundController.playing,
            onStart = soundController.start,
            onStop = soundController.stop,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SoundPreviewRow(
            title = stringResource(R.string.sound_dismiss_title),
            description = stringResource(R.string.sound_dismiss_desc),
            soundKey = "dismiss",
            playing = soundController.playing,
            onStart = soundController.start,
            onStop = soundController.stop,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SoundPreviewRow(
            title = stringResource(R.string.sound_sent_title),
            description = stringResource(R.string.sound_sent_desc),
            soundKey = "sent",
            playing = soundController.playing,
            onStart = soundController.start,
            onStop = soundController.stop,
        )
        Spacer(modifier = Modifier.height(8.dp))
        SoundPreviewRow(
            title = stringResource(R.string.sound_failure_title),
            description = stringResource(R.string.sound_failure_desc),
            soundKey = "failure",
            playing = soundController.playing,
            onStart = soundController.start,
            onStop = soundController.stop,
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.testing_fall_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_fall_test_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        )
    }
}
