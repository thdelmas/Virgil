package com.virgil.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virgil.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val FIRE_HOLD_MS = 1500L

/**
 * Two-mode button:
 *
 *  - Not fired: hold-to-fire panic trigger. The hold is the only false-trigger
 *    guard — pocket presses won't sustain it, deliberate presses will.
 *  - Fired: single-tap stop. Tap pops the device-credential auth gate; the
 *    gate (not the gesture) is what keeps a thief from silencing the alarm.
 *    A hold-to-stop on top of auth would only slow down the legitimate user.
 */
@Composable
fun PanicButton(
    fired: Boolean,
    onFire: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }

    val gestureModifier = if (fired) {
        Modifier.clickable(onClick = onStop)
    } else {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                val startedAt = System.currentTimeMillis()
                var completed = false
                val holdJob: Job = scope.launch {
                    while (isActive) {
                        val elapsed = System.currentTimeMillis() - startedAt
                        progress = (elapsed.toFloat() / FIRE_HOLD_MS).coerceAtMost(1f)
                        if (progress >= 1f) {
                            completed = true
                            onFire()
                            break
                        }
                        delay(16L)
                    }
                }
                waitForUpOrCancellation()
                holdJob.cancel()
                if (!completed) progress = 0f
                isPressed = false
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier.size(180.dp),
            contentAlignment = Alignment.Center,
        ) {
            val ringColor = if (fired) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            Canvas(modifier = Modifier.size(180.dp)) {
                val stroke = 10.dp.toPx()
                val d = size.minDimension - stroke
                val tl = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                drawArc(
                    color = ringColor.copy(alpha = 0.20f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = tl, size = Size(d, d),
                    style = Stroke(width = stroke),
                )
                if (progress > 0f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f, sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = tl, size = Size(d, d),
                        style = Stroke(width = stroke),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .then(gestureModifier),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (fired) Icons.Default.Stop else Icons.Default.Warning,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (fired) R.string.home_panic_stop_label
                            else R.string.home_panic_label,
                        ),
                        color = ringColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val hintRes = when {
            isPressed -> R.string.home_panic_holding
            fired -> R.string.home_panic_fired
            else -> R.string.home_panic_hint
        }
        Text(
            text = stringResource(hintRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}
