package com.virgil.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import kotlin.math.PI
import kotlin.math.sin
import kotlin.concurrent.thread

/**
 * Audible cues to wake the user (check-in ring) and to summon bystanders
 * (emergency siren). Both routed through USAGE_ALARM so they bypass silent
 * and Do-Not-Disturb the same way an alarm clock does.
 *
 * Process-wide singleton — only one cue plays at a time. Idempotent stop.
 */
object AttentionSound {

    private var ringtonePlayer: MediaPlayer? = null
    private var sirenTrack: AudioTrack? = null
    @Volatile private var sirenRunning = false
    private var sirenThread: Thread? = null

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Loop the system alarm tone until [stop]. Familiar, escalating, hard to sleep through. */
    @Synchronized
    fun playCheckInRing(context: Context) {
        stop()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: return

        ringtonePlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAttributes)
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
    }

    /**
     * Synthesised two-tone siren sweeping 1500↔3500 Hz. High-pitched on purpose:
     * older ears lose treble last for short bursts, and bystanders find the band
     * impossible to ignore.
     */
    @Synchronized
    fun playEmergencySiren(context: Context) {
        stop()
        val sampleRate = 44100
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(alarmAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        raiseAlarmVolume(context)

        sirenTrack = track
        sirenRunning = true
        track.play()

        sirenThread = thread(name = "VirgilSiren", isDaemon = true) {
            val chunkMs = 200
            val chunkFrames = sampleRate * chunkMs / 1000
            val buffer = ShortArray(chunkFrames)
            var phase = 0.0
            var t = 0.0
            try {
                while (sirenRunning) {
                    for (i in buffer.indices) {
                        // Sweep frequency in a 0.6s cycle between 1500 Hz and 3500 Hz.
                        val cycle = (t % 0.6) / 0.6
                        val freq = 1500.0 + 2000.0 * if (cycle < 0.5) cycle * 2 else (1 - cycle) * 2
                        phase += 2 * PI * freq / sampleRate
                        buffer[i] = (sin(phase) * Short.MAX_VALUE * 0.9).toInt().toShort()
                        t += 1.0 / sampleRate
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (_: Throwable) {
                // Track released from another thread — exit cleanly.
            }
        }
    }

    @Synchronized
    fun stop() {
        ringtonePlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        ringtonePlayer = null

        sirenRunning = false
        sirenThread = null
        sirenTrack?.runCatching {
            if (state == AudioTrack.STATE_INITIALIZED) {
                pause()
                flush()
                release()
            }
        }
        sirenTrack = null
    }

    private fun raiseAlarmVolume(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }
    }
}
