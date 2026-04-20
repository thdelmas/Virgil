package com.virgil.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
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
    private val algorithm = FallDetectionAlgorithm { msg ->
        android.util.Log.i(TAG, msg)
    }
    private var isListening = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        // Prefer the wake-up accelerometer when the hardware sensor hub offers
        // one: events keep flowing with the CPU asleep, no wake lock needed.
        // Fall back to the default (non-wake-up) sensor + partial wake lock.
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSensor()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
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
        startSensor()
        PermissionMonitor.check(this)
        return START_STICKY
    }

    override fun onDestroy() {
        stopSensor()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val now = System.currentTimeMillis()

        if (algorithm.processSample(magnitude, now)) {
            onFallDetected()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startSensor() {
        if (isListening) return
        val sensor = accelerometer ?: return
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
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
        EmergencyLauncher.launch(this, triggerType = "fall", peakAccel = algorithm.lastPeakAccel)
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

        return NotificationCompat.Builder(this, VirgilApp.CHANNEL_FALL_DETECTION)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.fall_detection_running))
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
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.virgil.app.STOP_FALL_DETECTION"
        const val ACTION_DEBUG_REPLAY = "com.virgil.app.DEBUG_REPLAY"
        const val EXTRA_PEAK_ACCEL = "peak_accel"
        const val EXTRA_TRIGGER_TYPE = "trigger_type"
        const val EXTRA_DEBUG_MAGNITUDES = "debug_magnitudes"
        const val EXTRA_DEBUG_DELAYS = "debug_delays"
        const val EXTRA_DEBUG_LABEL = "debug_label"
        private const val TAG = "FallDetectionService"
    }
}
