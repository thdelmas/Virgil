package com.virgil.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.virgil.app.BuildConfig
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.analysis.FallDetectionAlgorithm
import com.virgil.app.permissions.PermissionMonitor
import com.virgil.app.ui.MainActivity
import kotlin.math.sqrt

/**
 * Always-on foreground service that monitors the accelerometer for fall events.
 * Detection is delegated to [FallDetectionAlgorithm].
 */
class FallDetectionService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private val algorithm = FallDetectionAlgorithm { msg ->
        android.util.Log.i(TAG, msg)
    }
    private var isListening = false
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var verifyingStartedAt: Long = 0
    private var verifyMotionSamples = 0
    private var verifyPeakAccel: Float = 0f
    private var hapticPhaseEntered = false
    private var lastHapticBuzzAt: Long = 0

    private enum class NotifState {
        MONITORING, VERIFYING, ALARMING, LOW_ACTIVITY, AIRPLANE_PAUSED
    }
    @Volatile private var notifState: NotifState = NotifState.MONITORING
    @Volatile private var airplanePaused: Boolean = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                cancelVerify("screen turned on")
            }
        }
    }

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Battery saver can kill or deprioritise background work. Our
            // foreground service should already be exempt, but if the
            // system transitions fire listener changes we want to make
            // absolutely sure the sensor is registered and the foreground
            // notification is up.
            when (intent.action) {
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED,
                Intent.ACTION_BATTERY_OKAY,
                Intent.ACTION_BATTERY_LOW -> ensureRunning()
            }
        }
    }

    private val airplaneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                applyAirplaneMode()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        // Prefer the wake-up accelerometer when the hardware sensor hub offers
        // one: events keep flowing with the CPU asleep, no wake lock needed.
        // Fall back to the default (non-wake-up) sensor + partial wake lock.
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        registerReceiver(
            screenReceiver,
            IntentFilter(Intent.ACTION_SCREEN_ON),
            Context.RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(
            powerSaveReceiver,
            IntentFilter().apply {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                addAction(Intent.ACTION_BATTERY_OKAY)
                addAction(Intent.ACTION_BATTERY_LOW)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(
            airplaneReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { unregisterReceiver(powerSaveReceiver) }
        runCatching { unregisterReceiver(airplaneReceiver) }
        stopSensor()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSensor()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MARK_LOW_ACTIVITY -> {
                ensureRunning()
                setNotifState(NotifState.LOW_ACTIVITY)
                return START_STICKY
            }
            ACTION_CLEAR_LOW_ACTIVITY -> {
                if (notifState == NotifState.LOW_ACTIVITY) {
                    setNotifState(NotifState.MONITORING)
                }
                return START_STICKY
            }
            ACTION_CANCEL_VERIFY -> {
                cancelVerify("user tapped verify notification")
                return START_STICKY
            }
            ACTION_DEBUG_REPLAY -> if (BuildConfig.DEBUG) {
                val magnitudes = intent.getFloatArrayExtra(EXTRA_DEBUG_MAGNITUDES)
                val delays = intent.getLongArrayExtra(EXTRA_DEBUG_DELAYS)
                val label = intent.getStringExtra(EXTRA_DEBUG_LABEL) ?: "unnamed"
                if (magnitudes != null && delays != null && magnitudes.size == delays.size) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                    replayTrace(label, magnitudes, delays)
                }
                return START_STICKY
            }
        }

        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
        applyAirplaneMode()
        PermissionMonitor.check(this)
        return START_STICKY
    }

    private fun applyAirplaneMode() {
        val on = AirplaneMode.isOn(this)
        airplanePaused = on
        if (on) {
            stopSensor()
            // If an alarm is in flight, tear it down — we can't dispatch
            // SMS or a call with the radios off, and the user has told
            // the system not to do phone things.
            if (alarmInFlight) EmergencyAlarmService.cancel(this)
            setNotifState(NotifState.AIRPLANE_PAUSED)
        } else {
            setNotifState(NotifState.MONITORING)
            startSensor()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (airplanePaused) return
        // Suppress detection while an emergency screen is up — otherwise the
        // user's own handling (or the activity's own vibration) trips a second
        // alarm on top of the first.
        if (alarmInFlight) {
            if (!algorithm.isIdle) algorithm.reset()
            if (verifyingStartedAt != 0L) cancelVerify("alarm in flight", isUserDisarm = false)
            setNotifState(NotifState.ALARMING)
            return
        }
        if (notifState == NotifState.ALARMING) setNotifState(NotifState.MONITORING)

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (algorithm.processSample(magnitude, now)) onFallDetected()
                tickVerify(magnitude)
            }
            Sensor.TYPE_GYROSCOPE -> algorithm.processGyro(magnitude, now)
            Sensor.TYPE_GRAVITY -> algorithm.processGravity(x, y, z)
        }
    }

    private fun tickVerify(magnitude: Float) {
        val startedAt = verifyingStartedAt
        if (startedAt == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        if (elapsed >= VERIFY_SILENT_MS + VERIFY_HAPTIC_MS) {
            val peak = verifyPeakAccel
            cancelVerify("handed off to countdown", isUserDisarm = false)
            android.util.Log.i(TAG, "fall verify timeout — escalating peak=$peak")
            EmergencyLauncher.launch(this, triggerType = "fall", peakAccel = peak)
            return
        }
        if (elapsed >= VERIFY_SILENT_MS) {
            if (!hapticPhaseEntered) {
                hapticPhaseEntered = true
                android.util.Log.i(TAG, "fall verify entering haptic phase")
                postVerifyHeadsUp()
                buzz()
                lastHapticBuzzAt = SystemClock.elapsedRealtime()
            } else if (SystemClock.elapsedRealtime() - lastHapticBuzzAt >= HAPTIC_BUZZ_INTERVAL_MS) {
                buzz()
                lastHapticBuzzAt = SystemClock.elapsedRealtime()
            }
        }
        val inStillnessBand = magnitude in FallDetectionAlgorithm.STILLNESS_LOW..FallDetectionAlgorithm.STILLNESS_HIGH
        if (!inStillnessBand) {
            verifyMotionSamples++
            if (verifyMotionSamples >= VERIFY_MOTION_CANCEL_SAMPLES) {
                cancelVerify("motion resumed (${verifyMotionSamples} samples)")
            }
        }
    }

    private fun cancelVerify(reason: String, isUserDisarm: Boolean = true) {
        if (verifyingStartedAt == 0L) return
        val leaveTrace = hapticPhaseEntered && isUserDisarm
        verifyingStartedAt = 0
        verifyMotionSamples = 0
        verifyPeakAccel = 0f
        hapticPhaseEntered = false
        lastHapticBuzzAt = 0
        dismissVerifyHeadsUp()
        android.util.Log.i(TAG, "fall verify canceled: $reason")
        if (leaveTrace) postVerifyTrace()
        if (!alarmInFlight) setNotifState(NotifState.MONITORING)
    }

    private fun postVerifyHeadsUp() {
        val cancelIntent = PendingIntent.getService(
            this, 2,
            Intent(this, FallDetectionService::class.java).apply { action = ACTION_CANCEL_VERIFY },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, VirgilApp.CHANNEL_VERIFY)
            .setContentTitle(getString(R.string.verify_notif_title))
            .setContentText(getString(R.string.verify_notif_body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(cancelIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(VERIFY_NOTIFICATION_ID, notif)
    }

    private fun dismissVerifyHeadsUp() {
        getSystemService(android.app.NotificationManager::class.java)
            ?.cancel(VERIFY_NOTIFICATION_ID)
    }

    private fun postVerifyTrace() {
        val openApp = PendingIntent.getActivity(
            this, 3,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(this, VirgilApp.CHANNEL_FALL_DETECTION)
            .setContentTitle(getString(R.string.verify_trace_title))
            .setContentText(getString(R.string.verify_trace_body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(VERIFY_TRACE_NOTIFICATION_ID, notif)
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        } ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(BUZZ_PATTERN, -1))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startSensor() {
        if (isListening) return
        val sensor = accelerometer ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        gyroscope?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        isListening = true
        // Only hold a wake lock when the hardware doesn't have a wake-up
        // accelerometer — otherwise the sensor hub delivers events through
        // suspend and we pay no CPU cost between samples.
        if (!sensor.isWakeUpSensor) acquireWakeLock()
    }

    private fun stopSensor() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "virgil:fall-detection").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun replayTrace(label: String, magnitudes: FloatArray, delays: LongArray) {
        val wasListening = isListening
        stopSensor()
        algorithm.reset()

        val handler = Handler(Looper.getMainLooper())
        val base = System.currentTimeMillis()
        var offset = 0L

        android.util.Log.i(TAG, "debug replay start: $label (${magnitudes.size} samples)")

        for (i in magnitudes.indices) {
            offset += delays[i]
            val t = base + offset
            val mag = magnitudes[i]
            handler.postDelayed({
                if (algorithm.processSample(mag, t)) {
                    android.util.Log.i(TAG, "debug replay: fall detected in '$label'")
                    onFallDetected()
                }
            }, offset)
        }

        handler.postDelayed({
            android.util.Log.i(TAG, "debug replay end: $label")
            if (wasListening) startSensor()
        }, offset + 500L)
    }

    private fun onFallDetected() {
        if (verifyingStartedAt != 0L) return
        val pm = getSystemService(PowerManager::class.java)
        // Release builds skip detection while the screen is on — "the phone
        // is in the user's hand, they're fine". Debug builds must still run
        // the verify → alarm flow so falls can be tested with the screen up.
        if (!BuildConfig.DEBUG && pm?.isInteractive == true) {
            android.util.Log.i(TAG, "fall ignored: screen interactive (phone in use)")
            return
        }
        verifyingStartedAt = SystemClock.elapsedRealtime()
        verifyMotionSamples = 0
        verifyPeakAccel = algorithm.lastPeakAccel
        hapticPhaseEntered = false
        lastHapticBuzzAt = 0
        setNotifState(NotifState.VERIFYING)
        android.util.Log.i(TAG, "fall candidate — ${VERIFY_SILENT_MS}ms silent then ${VERIFY_HAPTIC_MS}ms haptic peak=$verifyPeakAccel")
    }

    private fun setNotifState(next: NotifState) {
        if (notifState == next) return
        notifState = next
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun ensureRunning() {
        // Re-assert the foreground notification and sensor registration.
        // Safe to call repeatedly — startForeground is idempotent, and
        // registerListener returns false (without error) on a duplicate.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        if (!airplanePaused) startSensor()
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FallDetectionService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val textRes = when (notifState) {
            NotifState.MONITORING -> R.string.fall_detection_running
            NotifState.VERIFYING -> R.string.fall_detection_verifying
            NotifState.ALARMING -> R.string.fall_detection_alarming
            NotifState.LOW_ACTIVITY -> R.string.fall_detection_low_activity
            NotifState.AIRPLANE_PAUSED -> R.string.fall_detection_airplane_paused
        }

        return NotificationCompat.Builder(this, VirgilApp.CHANNEL_FALL_DETECTION)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(textRes))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openApp)
            .addAction(
                android.R.drawable.ic_delete,
                getString(R.string.fall_notification_stop),
                stopIntent,
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        /**
         * True while an emergency UI (countdown or bystander siren) owns the
         * user's attention. [onSensorChanged] short-circuits — we don't want
         * fresh alarms stacking on top of the one already in progress.
         */
        @Volatile
        var alarmInFlight: Boolean = false

        const val NOTIFICATION_ID = 1
        const val VERIFY_NOTIFICATION_ID = 0x5646 // "VF" — verify-fall
        const val VERIFY_TRACE_NOTIFICATION_ID = 0x5654 // "VT" — verify-trace
        const val ACTION_STOP = "com.virgil.app.STOP_FALL_DETECTION"
        const val ACTION_MARK_LOW_ACTIVITY = "com.virgil.app.MARK_LOW_ACTIVITY"
        const val ACTION_CLEAR_LOW_ACTIVITY = "com.virgil.app.CLEAR_LOW_ACTIVITY"
        const val ACTION_CANCEL_VERIFY = "com.virgil.app.CANCEL_VERIFY"
        const val ACTION_DEBUG_REPLAY = "com.virgil.app.DEBUG_REPLAY"
        const val EXTRA_PEAK_ACCEL = "peak_accel"
        const val EXTRA_TRIGGER_TYPE = "trigger_type"
        const val EXTRA_DEBUG_MAGNITUDES = "debug_magnitudes"
        const val EXTRA_DEBUG_DELAYS = "debug_delays"
        const val EXTRA_DEBUG_LABEL = "debug_label"
        private const val TAG = "FallDetectionService"
        private const val VERIFY_SILENT_MS = 15_000L
        private const val VERIFY_HAPTIC_MS = 15_000L
        private const val HAPTIC_BUZZ_INTERVAL_MS = 3_000L
        private const val VERIFY_MOTION_CANCEL_SAMPLES = 8
        private val BUZZ_PATTERN = longArrayOf(0, 250, 120, 250, 120, 400)
    }
}
