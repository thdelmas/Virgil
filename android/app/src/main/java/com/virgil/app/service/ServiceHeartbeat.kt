package com.virgil.app.service

import android.content.Context
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors

/**
 * Survival instrument for the always-on services.
 *
 * The open question for any always-on guardian is whether an aggressive OEM
 * (Xiaomi, Samsung, …) silently kills the foreground service after some hours.
 * When that happens there is no crash and no log — the watcher just stops, and
 * the user keeps trusting an app that is no longer running.
 *
 * This makes the silence visible. [beat] appends one line — wall-clock millis,
 * elapsed-realtime millis, tag — to a local file, at most once per
 * [minIntervalMs]. [mark] records lifecycle transitions unconditionally so a
 * graceful stop (a `destroy` line) is distinguishable from an OS kill (a gap
 * with no `destroy`). A gap between consecutive beats longer than the beat
 * interval is the service not running during that window.
 *
 * Nothing here schedules a wake-up. Callers beat opportunistically off work the
 * service already does (sensor callbacks), so the instrument never alters the
 * survival behaviour it measures, and adds no battery cost of its own.
 */
class ServiceHeartbeat(
    context: Context,
    private val minIntervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    private val logFile = File(context.applicationContext.filesDir, FILE_NAME)
    private val io = Executors.newSingleThreadExecutor()

    @Volatile private var lastBeatElapsed = Long.MIN_VALUE

    /** Opportunistic, throttled. Safe to call on every sensor event. */
    fun beat(tag: String) {
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed - lastBeatElapsed < minIntervalMs) return
        lastBeatElapsed = elapsed
        write(System.currentTimeMillis(), elapsed, tag)
    }

    /** Unconditional. Use for lifecycle transitions worth seeing every time. */
    fun mark(tag: String) {
        val elapsed = SystemClock.elapsedRealtime()
        lastBeatElapsed = elapsed
        write(System.currentTimeMillis(), elapsed, tag)
    }

    private fun write(wallMs: Long, elapsedMs: Long, tag: String) {
        io.execute {
            runCatching {
                if (logFile.length() > MAX_BYTES) trim()
                logFile.appendText("$wallMs,$elapsedMs,$tag\n")
            }
        }
    }

    /** Drop the oldest half so a long-running install never grows unbounded. */
    private fun trim() {
        val lines = logFile.readLines()
        logFile.writeText(lines.drop(lines.size / 2).joinToString("\n", postfix = "\n"))
    }

    companion object {
        const val FILE_NAME = "heartbeat.log"
        const val DEFAULT_INTERVAL_MS = 60_000L
        private const val MAX_BYTES = 256L * 1024

        /** A stretch of wall-clock time the service was not beating. */
        data class Gap(val fromElapsedMs: Long, val toElapsedMs: Long) {
            val durationMs: Long get() = toElapsedMs - fromElapsedMs
        }

        /** At-a-glance liveness, for the in-app readout. */
        data class Summary(
            val beats: Int,
            val lastWallMs: Long?,
            val longestGapMs: Long,
            val gapCount: Int,
        )

        /** Reads and summarises the log. Call off the main thread (touches disk). */
        fun summarize(context: Context, gapThresholdMs: Long = 5 * DEFAULT_INTERVAL_MS): Summary {
            val lines = runCatching {
                File(context.applicationContext.filesDir, FILE_NAME).readLines()
            }.getOrDefault(emptyList())
            val gaps = findGaps(parseElapsed(lines), gapThresholdMs)
            return Summary(
                beats = parseElapsed(lines).size,
                lastWallMs = lines.lastOrNull()?.split(",")?.firstOrNull()?.trim()?.toLongOrNull(),
                longestGapMs = gaps.maxOfOrNull { it.durationMs } ?: 0L,
                gapCount = gaps.size,
            )
        }

        /** Elapsed-realtime column of each log line, in order, malformed lines skipped. */
        fun parseElapsed(lines: List<String>): List<Long> =
            lines.mapNotNull { it.split(",").getOrNull(1)?.trim()?.toLongOrNull() }

        /**
         * Windows longer than [thresholdMs] between consecutive beats.
         *
         * elapsedRealtime resets to zero on reboot, so a decrease marks a reboot
         * boundary, not a gap — the service was legitimately down across a power
         * cycle and [BootReceiver] restarts it. We only flag forward jumps, which
         * are the service dying while the phone stayed on.
         */
        fun findGaps(elapsed: List<Long>, thresholdMs: Long): List<Gap> =
            elapsed.zipWithNext()
                .filter { (prev, cur) -> cur >= prev && cur - prev > thresholdMs }
                .map { (prev, cur) -> Gap(prev, cur) }
    }
}
