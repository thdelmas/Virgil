package com.virgil.app.debug

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * Debug-only foreground service that records raw accelerometer samples to a CSV
 * in the app's external files dir, pullable with `adb pull` without root.
 *
 * CSV columns: elapsed_ms,x,y,z,magnitude
 *
 * Auto-stops after [MAX_DURATION_MS] to guard against forgotten recordings.
 */
class SensorRecorderService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var writer: BufferedWriter? = null
    private var startElapsed: Long = 0L
    private var sampleCount: Int = 0
    private var outputFile: File? = null
    private val autoStopHandler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable {
        Log.i(TAG, "auto-stop after ${MAX_DURATION_MS / 1000}s")
        stopRecording()
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent.getStringExtra(EXTRA_LABEL))
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val w = writer ?: return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val mag = sqrt(x * x + y * y + z * z)
        val elapsed = SystemClock.elapsedRealtime() - startElapsed
        w.write("$elapsed,$x,$y,$z,$mag\n")
        sampleCount++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startRecording(label: String?) {
        if (writer != null) {
            Log.w(TAG, "already recording to ${outputFile?.name}")
            return
        }
        val dir = File(getExternalFilesDir(null), "traces").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val suffix = label?.takeIf { it.isNotBlank() }?.let { "-$it" } ?: ""
        val file = File(dir, "trace-$stamp$suffix.csv")
        writer = BufferedWriter(FileWriter(file)).apply {
            write("elapsed_ms,x,y,z,magnitude\n")
        }
        outputFile = file
        startElapsed = SystemClock.elapsedRealtime()
        sampleCount = 0

        startForeground(
            NOTIFICATION_ID,
            buildNotification(file.name),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        accelerometer?.let { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        autoStopHandler.postDelayed(autoStopRunnable, MAX_DURATION_MS)
        Log.i(TAG, "recording -> ${file.absolutePath}")
    }

    private fun stopRecording() {
        autoStopHandler.removeCallbacks(autoStopRunnable)
        sensorManager.unregisterListener(this)
        try {
            writer?.flush()
            writer?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close failed", e)
        }
        val file = outputFile
        writer = null
        outputFile = null
        val path = file?.absolutePath ?: "(none)"
        Log.i(TAG, "stopped: $sampleCount samples -> $path")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (writer != null) stopRecording()
        super.onDestroy()
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Debug sensor recorder", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Debug-only accelerometer trace recording" }
            )
        }
    }

    private fun buildNotification(filename: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Recording sensor trace")
            .setContentText(filename)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.virgil.app.debug.RECORD_START"
        const val ACTION_STOP = "com.virgil.app.debug.RECORD_STOP"
        const val EXTRA_LABEL = "label"
        private const val NOTIFICATION_ID = 9901
        private const val CHANNEL = "debug_recorder"
        private const val MAX_DURATION_MS = 10 * 60 * 1000L
        private const val TAG = "SensorRecorder"
    }
}
