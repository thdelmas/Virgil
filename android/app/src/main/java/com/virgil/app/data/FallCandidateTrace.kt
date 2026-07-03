package com.virgil.app.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Rolling on-device trace of fall-detector decisions — candidates that
 * triggered, and near-misses that a guard suppressed. Exists because
 * logcat rotates within hours: when a user reports yesterday's false
 * alarm, this file still knows what the detector saw.
 *
 * Kinematic numbers and timestamps only — never location, contacts, or
 * identifiers. Stays in app-internal storage; its tail is offered for
 * export solely through the false-alarm report flow, under the same
 * consent checkbox (and byte-identical preview) as the snapshot.
 */
object FallCandidateTrace {

    private const val FILE_NAME = "fall_candidates.log"
    private const val MAX_BYTES = 16 * 1024L
    private const val KEEP_BYTES = 8 * 1024

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "fall-trace").apply { isDaemon = true }
    }

    fun append(context: Context, line: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val file = file(context)
        io.execute {
            runCatching { appendToFile(file, "$stamp $line") }
        }
    }

    fun readTail(context: Context, maxLines: Int): String? = runCatching {
        tail(file(context), maxLines)
    }.getOrNull()

    internal fun appendToFile(file: File, stampedLine: String) {
        file.appendText(stampedLine + "\n")
        if (file.length() <= MAX_BYTES) return
        val text = file.readText()
        val cut = text.indexOf('\n', text.length - KEEP_BYTES)
        if (cut >= 0) file.writeText(text.substring(cut + 1))
    }

    internal fun tail(file: File, maxLines: Int): String? {
        if (!file.exists()) return null
        val lines = file.readText().trim().lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        return lines.takeLast(maxLines).joinToString("\n")
    }

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)
}
