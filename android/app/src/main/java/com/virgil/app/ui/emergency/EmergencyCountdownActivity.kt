package com.virgil.app.ui.emergency

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.virgil.app.R
import com.virgil.app.data.AppLocale
import com.virgil.app.service.EmergencyAlarmService
import com.virgil.app.service.EmergencySirenService
import com.virgil.app.ui.theme.VirgilTheme

/**
 * Full-screen skin over [EmergencyAlarmService]. The service owns the
 * countdown state, audio, and vibration — this activity only renders them
 * and forwards the cancel tap. If the user tries to dismiss while the
 * alarm is still in flight, the service immediately re-launches this
 * activity so the alert cannot be abandoned mid-flow.
 */
class EmergencyCountdownActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        // Back must not dismiss the alarm — the only way out is to hold
        // the disarm button long enough for the gesture to complete.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Swallow. Relaunch so the UI cannot be hidden.
                EmergencyAlarmService.reportActivityVisibility(
                    this@EmergencyCountdownActivity, visible = false,
                )
            }
        })

        setContent {
            VirgilTheme {
                val state by EmergencyAlarmService.state.collectAsState()
                if (!state.active) {
                    // Service stopped — nothing to show. Finish so the
                    // activity isn't left floating without audio/state.
                    finish()
                } else if (state.done) {
                    SirenActiveScreen(
                        sent = state.finishedSent,
                        failed = state.finishedFailed,
                        onStop = { stopEverythingAndFinish() },
                    )
                } else if (state.dispatching) {
                    DispatchingScreen()
                } else {
                    CountdownScreen(state = state)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        EmergencyAlarmService.reportActivityVisibility(this, visible = true)
    }

    override fun onStop() {
        super.onStop()
        EmergencyAlarmService.reportActivityVisibility(this, visible = false)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home / recents: tell the service we've been backgrounded so it
        // re-fires the full-screen intent and brings us back.
        EmergencyAlarmService.reportActivityVisibility(this, visible = false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Volume / power shouldn't silence the alarm. Let them pass but
        // don't let them dismiss the activity.
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_POWER -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun stopEverythingAndFinish() {
        sendBroadcast(
            android.content.Intent(EmergencySirenService.ACTION_SILENCE)
                .setPackage(packageName)
        )
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguard = getSystemService(KeyguardManager::class.java)
            keyguard.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private val PHASE_COLORS = mapOf(
    EmergencyAlarmService.Phase.P1_CALM to Color(0xFF1565C0),
    EmergencyAlarmService.Phase.P2_NOTIFY to Color(0xFFF9A825),
    EmergencyAlarmService.Phase.P3_WARN to Color(0xFFE65100),
    EmergencyAlarmService.Phase.P4_URGENT to Color(0xFFB71C1C),
)

private fun EmergencyAlarmService.Phase.stepIndex(): Int = when (this) {
    EmergencyAlarmService.Phase.P1_CALM -> 1
    EmergencyAlarmService.Phase.P2_NOTIFY -> 2
    EmergencyAlarmService.Phase.P3_WARN -> 3
    EmergencyAlarmService.Phase.P4_URGENT -> 4
}

@Composable
private fun CountdownScreen(state: EmergencyAlarmService.State) {
    val titleRes = if (state.triggerType == "fall") {
        R.string.countdown_title_fall
    } else {
        R.string.countdown_title_no_response
    }
    val targetColor = PHASE_COLORS[state.phase] ?: Color(0xFFB71C1C)
    val bg by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 600),
        label = "phase-bg",
    )
    val stepIndex = state.phase.stepIndex()
    val totalSteps = EmergencyAlarmService.TOTAL_STEPS

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            StepIndicator(currentStep = stepIndex, totalSteps = totalSteps)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    R.string.countdown_step_label, stepIndex, totalSteps,
                ),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(titleRes),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (state.paused) {
                    stringResource(R.string.countdown_paused_label)
                } else {
                    stringResource(R.string.countdown_before)
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "${state.secondsLeftInPhase}",
                color = Color.White,
                fontSize = 112.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stringResource(R.string.countdown_seconds_label),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            HoldToDisarmButton(ringColor = bg)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.countdown_hold_to_cancel),
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HoldToDisarmButton(ringColor: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    val holdMs = EmergencyAlarmService.HOLD_TO_DISARM_MS

    val gestureModifier = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            isPressed = true
            EmergencyAlarmService.pause(context)
            val startedAt = System.currentTimeMillis()
            var completed = false
            val holdJob: Job = scope.launch {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startedAt
                    progress = (elapsed.toFloat() / holdMs).coerceAtMost(1f)
                    if (progress >= 1f) {
                        completed = true
                        EmergencyAlarmService.cancel(context)
                        break
                    }
                    delay(16L)
                }
            }
            waitForUpOrCancellation()
            holdJob.cancel()
            if (!completed) {
                EmergencyAlarmService.resume(context)
            }
            progress = 0f
            isPressed = false
        }
    }

    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f,
            )
            drawArc(
                color = Color.White.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(diameter, diameter),
                style = Stroke(width = strokeWidth),
            )
            if (progress > 0f) {
                drawArc(
                    color = Color.White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(width = strokeWidth),
                )
            }
        }
        Button(
            onClick = { /* swallow — gesture handler drives pause/cancel */ },
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .then(gestureModifier),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = CircleShape,
        ) {
            Text(
                text = if (isPressed) {
                    stringResource(R.string.countdown_keep_holding)
                } else {
                    stringResource(R.string.countdown_im_ok)
                },
                color = ringColor,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int, totalSteps: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..totalSteps) {
            val active = i <= currentStep
            val color = if (active) Color.White else Color.White.copy(alpha = 0.3f)
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            if (i < totalSteps) Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun DispatchingScreen() {
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
private fun SirenActiveScreen(sent: Int, failed: Int, onStop: () -> Unit) {
    val success = sent > 0
    val bg = if (success) Color(0xFF1B5E20) else Color(0xFFB71C1C)
    val titleRes = if (success) R.string.siren_sent_title else R.string.siren_failed_title
    val bodyRes = if (success) R.string.siren_sent_body else R.string.siren_failed_body

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
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
            Spacer(modifier = Modifier.height(48.dp))
            Button(
                onClick = onStop,
                modifier = Modifier
                    .size(220.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = CircleShape,
            ) {
                Text(
                    text = stringResource(R.string.siren_stop_button),
                    color = bg,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
            if (failed > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "$failed failed",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                )
            }
        }
    }
}
