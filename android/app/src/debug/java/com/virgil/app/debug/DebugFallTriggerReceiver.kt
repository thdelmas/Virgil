package com.virgil.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.virgil.app.service.FallDetectionService
import java.io.File

/**
 * Debug-only entry point for replaying an accelerometer trace through
 * [FallDetectionService]. Stripped from release builds (lives in src/debug/).
 *
 * Canned trace (see [DebugTraces.ALL]):
 *   adb shell am broadcast \
 *     -a com.virgil.app.DEBUG_TRIGGER_FALL \
 *     -n com.virgil.app/.debug.DebugFallTriggerReceiver \
 *     --es trace fall
 *
 * Recorded CSV (from [SensorRecorderService]):
 *   adb shell am broadcast \
 *     -a com.virgil.app.DEBUG_TRIGGER_FALL \
 *     -n com.virgil.app/.debug.DebugFallTriggerReceiver \
 *     --es file trace-20260417-153045.csv
 *
 *   CSV is resolved relative to getExternalFilesDir("traces").
 *   Expected columns: elapsed_ms,x,y,z,magnitude (header row required).
 */
class DebugFallTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val filename = intent.getStringExtra("file")
        val (label, mags, delays) = when {
            filename != null -> loadCsv(context, filename) ?: return
            else -> {
                val name = intent.getStringExtra("trace") ?: "fall"
                val t = DebugTraces.ALL[name] ?: run {
                    Log.w(TAG, "unknown trace '$name'; known: ${DebugTraces.ALL.keys}")
                    return
                }
                Triple(name, t.magnitudes, t.delays)
            }
        }

        Log.i(TAG, "dispatching '$label' (${mags.size} samples)")
        val service = Intent(context, FallDetectionService::class.java).apply {
            action = FallDetectionService.ACTION_DEBUG_REPLAY
            putExtra(FallDetectionService.EXTRA_DEBUG_LABEL, label)
            putExtra(FallDetectionService.EXTRA_DEBUG_MAGNITUDES, mags)
            putExtra(FallDetectionService.EXTRA_DEBUG_DELAYS, delays)
        }
        context.startForegroundService(service)
    }

    private fun loadCsv(context: Context, filename: String): Triple<String, FloatArray, LongArray>? {
        val file = File(File(context.getExternalFilesDir(null), "traces"), filename)
        if (!file.isFile) {
            Log.w(TAG, "csv not found: ${file.absolutePath}")
            return null
        }
        val mags = mutableListOf<Float>()
        val times = mutableListOf<Long>()
        file.useLines { lines ->
            for ((i, raw) in lines.withIndex()) {
                if (i == 0) continue
                val parts = raw.split(',')
                if (parts.size < 5) continue
                val elapsed = parts[0].toLongOrNull() ?: continue
                val mag = parts[4].toFloatOrNull() ?: continue
                times += elapsed
                mags += mag
            }
        }
        if (mags.isEmpty()) {
            Log.w(TAG, "csv empty or unreadable: $filename")
            return null
        }
        val delays = LongArray(times.size)
        delays[0] = 0L
        for (i in 1 until times.size) {
            delays[i] = (times[i] - times[i - 1]).coerceAtLeast(0L)
        }
        return Triple(filename, mags.toFloatArray(), delays)
    }

    private companion object {
        const val TAG = "DebugFallTrigger"
    }
}
