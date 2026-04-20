package com.virgil.app.ui.emergency

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.virgil.app.data.AppLocale
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.service.AttentionSound
import com.virgil.app.service.EmergencyDispatcher
import com.virgil.app.service.EmergencyLauncher
import com.virgil.app.service.EmergencySirenService
import com.virgil.app.service.FallDetectionService
import com.virgil.app.ui.theme.VirgilTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Full-screen emergency countdown shown when a fall is detected or check-in expires.
 * Large "I'm OK" button to cancel. If countdown expires, triggers emergency dispatch.
 */
class EmergencyCountdownActivity : ComponentActivity() {

    private lateinit var prefs: EmergencyPreferences
    private lateinit var dispatcher: EmergencyDispatcher

    private var sirenHandedOff = false
    private var cueHandoff = false

    private sealed interface Phase {
        data object Countdown : Phase
        data object Dispatching : Phase
        data class Done(val sent: Int, val failed: Int) : Phase
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EmergencyLauncher.cancel(this)
        showOverLockScreen()

        prefs = EmergencyPreferences(this)
        dispatcher = EmergencyDispatcher(this)

        val triggerType = intent.getStringExtra(FallDetectionService.EXTRA_TRIGGER_TYPE) ?: "fall"
        val isFall = triggerType == "fall"
        val titleRes = if (isFall) {
            R.string.countdown_title_fall
        } else {
            R.string.countdown_title_no_response
        }
        val dispatchTrigger = if (isFall) {
            EmergencyDispatcher.TriggerType.FALL
        } else {
            EmergencyDispatcher.TriggerType.NO_RESPONSE
        }

        setContent {
            VirgilTheme {
                var phase: Phase by remember { mutableStateOf(Phase.Countdown) }

                when (val p = phase) {
                    Phase.Countdown -> CountdownScreen(
                        titleRes = titleRes,
                        onCancel = {
                            stopVibration()
                            AttentionSound.stop()
                            AttentionSound.playDismissCue(this)
                            cueHandoff = true
                            finish()
                        },
                        onExpired = {
                            stopVibration()
                            AttentionSound.stop()
                            cueHandoff = true
                            phase = Phase.Dispatching
                            triggerEmergency(dispatchTrigger) { result ->
                                phase = Phase.Done(result.smsSent, result.smsFailed)
                            }
                        },
                    )
                    Phase.Dispatching -> DispatchingScreen()
                    is Phase.Done -> SirenActiveScreen(
                        sent = p.sent,
                        failed = p.failed,
                        onStop = { stopEverythingAndFinish() },
                    )
                }
            }
        }

        startVibration()
        AttentionSound.playEmergencySiren(this)
    }

    override fun onDestroy() {
        if (!sirenHandedOff && !cueHandoff) AttentionSound.stop()
        super.onDestroy()
    }

    private fun stopEverythingAndFinish() {
        sendBroadcast(
            Intent(EmergencySirenService.ACTION_SILENCE).setPackage(packageName)
        )
        AttentionSound.stop()
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

    private fun startVibration() {
        val pattern = longArrayOf(0, 500, 300, 500, 300, 500, 300)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(VibratorManager::class.java)
            manager.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = getSystemService(Vibrator::class.java)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        }
    }

    private fun stopVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java).cancel()
        }
    }

    private fun triggerEmergency(
        triggerType: EmergencyDispatcher.TriggerType,
        onDone: (EmergencyDispatcher.Result) -> Unit,
    ) {
        val contacts = runBlocking { prefs.contacts.first() }
        val customTemplate = runBlocking { prefs.smsMessageOverride.first() }
        val appContext = applicationContext

        if (contacts.isEmpty()) {
            AttentionSound.playFailureCue(appContext)
            onDone(EmergencyDispatcher.Result(locationAvailable = false, smsSent = 0, smsFailed = 0))
            return
        }

        dispatcher.dispatch(contacts, customTemplate, triggerType) { result ->
            if (result.smsSent > 0) {
                AttentionSound.playSentCue(appContext)
            } else {
                AttentionSound.playFailureCue(appContext)
            }
            // Let the outcome cue (~0.5–0.8s) be heard before the bystander
            // siren drowns it out.
            Handler(Looper.getMainLooper()).postDelayed({
                sirenHandedOff = true
                EmergencySirenService.start(appContext)
            }, 1200)
            onDone(result)
        }
    }
}

@Composable
fun CountdownScreen(
    titleRes: Int = R.string.countdown_title_fall,
    totalSeconds: Int = 30,
    onCancel: () -> Unit,
    onExpired: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(totalSeconds) }

    val progress by animateFloatAsState(
        targetValue = remaining.toFloat() / totalSeconds,
        animationSpec = tween(durationMillis = 1000),
        label = "countdown",
    )

    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
        onExpired()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB71C1C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(titleRes),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.countdown_before),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "$remaining",
                color = Color.White,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stringResource(R.string.countdown_seconds_label),
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 18.sp,
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onCancel,
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                ),
                shape = CircleShape,
            ) {
                Text(
                    text = stringResource(R.string.countdown_im_ok),
                    color = Color(0xFFB71C1C),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.countdown_tap_cancel),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 18.sp,
            )
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
