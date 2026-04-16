package com.virgil.app.ui.emergency

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.service.EmergencyDispatcher
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showOverLockScreen()

        prefs = EmergencyPreferences(this)
        dispatcher = EmergencyDispatcher(this)

        val triggerType = intent.getStringExtra(FallDetectionService.EXTRA_TRIGGER_TYPE) ?: "fall"
        val title = if (triggerType == "fall") "Fall Detected" else "No Response Detected"

        setContent {
            VirgilTheme {
                CountdownScreen(
                    title = title,
                    onCancel = {
                        stopVibration()
                        finish()
                    },
                    onExpired = {
                        stopVibration()
                        triggerEmergency()
                    },
                )
            }
        }

        startVibration()
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

    private fun triggerEmergency() {
        val contacts = runBlocking { prefs.contacts.first() }
        val message = runBlocking { prefs.smsMessage.first() }

        if (contacts.isNotEmpty()) {
            dispatcher.dispatch(contacts, message)
        }

        finish()
    }
}

@Composable
fun CountdownScreen(
    title: String = "Fall Detected",
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
                text = title,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Emergency alert in",
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
                text = "seconds",
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
                    text = "I'm OK",
                    color = Color(0xFFB71C1C),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tap to cancel",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
    }
}
