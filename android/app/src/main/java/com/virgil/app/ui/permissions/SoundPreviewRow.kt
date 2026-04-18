package com.virgil.app.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.virgil.app.R
import com.virgil.app.service.AttentionSound

/**
 * A row with a sound title, short description, and a Play/Stop button.
 *
 * Only one sound plays at a time process-wide (AttentionSound is a singleton),
 * so we track a single shared "currently-playing" key across all rows and flip
 * every row's button back to Play when a different row starts.
 */
@Composable
internal fun SoundPreviewRow(
    title: String,
    description: String,
    soundKey: String,
    playing: String?,
    onStart: (String) -> Unit,
    onStop: () -> Unit,
) {
    val isPlaying = playing == soundKey
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = { if (isPlaying) onStop() else onStart(soundKey) },
                modifier = Modifier.height(44.dp),
            ) {
                if (isPlaying) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sound_preview_stop))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.sound_preview_play))
                }
            }
        }
    }
}

/**
 * Plays a sound via AttentionSound and ensures it stops when the composition
 * leaves. Returns a (playing, start, stop) triple for managing a single-active
 * preview across multiple rows.
 */
@Composable
internal fun rememberSoundPreviewController(): SoundPreviewController {
    val context = LocalContext.current
    var playing by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { AttentionSound.stop() }
    }

    fun finished(key: String) {
        // AudioTrack's listener thread — guard against a newer sound having
        // started in the meantime by only resetting when the key still matches.
        if (playing == key) playing = null
    }

    return SoundPreviewController(
        playing = playing,
        start = { key ->
            AttentionSound.stop()
            when (key) {
                "ring" -> AttentionSound.playCheckInRing(context)
                "siren" -> AttentionSound.playEmergencySiren(context)
                "dismiss" -> AttentionSound.playDismissCue(context) { finished("dismiss") }
                "sent" -> AttentionSound.playSentCue(context) { finished("sent") }
                "failure" -> AttentionSound.playFailureCue(context) { finished("failure") }
            }
            playing = key
        },
        stop = {
            AttentionSound.stop()
            playing = null
        },
    )
}

internal data class SoundPreviewController(
    val playing: String?,
    val start: (String) -> Unit,
    val stop: () -> Unit,
)
