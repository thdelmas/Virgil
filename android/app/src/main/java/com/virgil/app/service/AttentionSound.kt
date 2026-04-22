package com.virgil.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.concurrent.thread

/**
 * Audible cues to wake the user (check-in ring) and to summon bystanders
 * (emergency siren). Both routed through USAGE_ALARM so they bypass silent
 * and Do-Not-Disturb the same way an alarm clock does.
 *
 * All sounds are synthesised locally — never the system's default ringtone.
 * A Virgil alert must be instantly recognisable as Virgil so the user isn't
 * left wondering "is that a call? is that my alarm? how do I stop it?".
 *
 * Process-wide singleton — only one looping cue plays at a time. Idempotent
 * stop.
 */
object AttentionSound {

    enum class Stage { RAMPING, STEADY, URGENT }

    private var loopingTrack: AudioTrack? = null
    private var cueTrack: AudioTrack? = null
    @Volatile private var loopingRunning = false
    private var loopingThread: Thread? = null

    private const val SAMPLE_RATE = 44100

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /**
     * Distinctive synthesised "Virgil is asking if you're there" ring —
     * a two-tone bell (C5 → G5) that repeats. Deliberately not the user's
     * system ringtone: if Virgil ever rings, it should be unmistakably
     * Virgil, not "did someone call me?".
     */
    @Synchronized
    fun playCheckInRing(context: Context) {
        if (loopingRunning) return
        stop()
        raiseAlarmVolume(context)
        val track = buildLoopingTrack()
        loopingTrack = track
        loopingRunning = true
        track.play()
        loopingThread = thread(name = "VirgilCheckInRing", isDaemon = true) {
            val bell = synthCheckInBell()
            try {
                while (loopingRunning) {
                    track.write(bell, 0, bell.size)
                }
            } catch (_: Throwable) {
                // Track released from another thread — exit cleanly.
            }
        }
    }

    /**
     * Synthesised two-tone siren sweeping 1500↔3500 Hz. High-pitched on
     * purpose: older ears lose treble last for short bursts, and bystanders
     * find the band impossible to ignore.
     */
    @Synchronized
    fun playEmergencySiren(context: Context) {
        if (loopingRunning) return
        stop()
        raiseAlarmVolume(context)
        val track = buildLoopingTrack()
        loopingTrack = track
        loopingRunning = true
        track.play()
        loopingThread = thread(name = "VirgilSiren", isDaemon = true) {
            val chunkFrames = SAMPLE_RATE / 5
            val buffer = ShortArray(chunkFrames)
            var phase = 0.0
            var t = 0.0
            try {
                while (loopingRunning) {
                    for (i in buffer.indices) {
                        val cycle = (t % 0.6) / 0.6
                        val freq = 1500.0 + 2000.0 *
                            if (cycle < 0.5) cycle * 2 else (1 - cycle) * 2
                        phase += 2 * PI * freq / SAMPLE_RATE
                        buffer[i] = (sin(phase) * Short.MAX_VALUE * 0.9).toInt().toShort()
                        t += 1.0 / SAMPLE_RATE
                    }
                    track.write(buffer, 0, buffer.size)
                }
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Legacy pre-dispatch warning — kept so callers that used the old
     * single-phase countdown still work. New staged countdowns should call
     * [playStagedAlarm] with the appropriate [Stage].
     */
    fun playCountdownWarning(context: Context) {
        playStagedAlarm(context, Stage.STEADY)
    }

    /**
     * Phase-appropriate audio for the staged pre-dispatch countdown.
     * - [Stage.RAMPING]: calm 880 Hz ping, volume fades from ~10% to 90% over 15 s.
     * - [Stage.STEADY]: same ping at full volume.
     * - [Stage.URGENT]: denser two-tone pulse, full volume.
     */
    @Synchronized
    fun playStagedAlarm(context: Context, stage: Stage) {
        stop()
        raiseAlarmVolume(context)
        val track = buildLoopingTrack()
        loopingTrack = track
        loopingRunning = true
        track.play()
        loopingThread = thread(name = "VirgilStaged-$stage", isDaemon = true) {
            try {
                when (stage) {
                    Stage.RAMPING -> runRampingPing(track)
                    Stage.STEADY -> runSteadyPing(track)
                    Stage.URGENT -> runUrgentPulse(track)
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun runRampingPing(track: AudioTrack) {
        val freq = 880.0
        val beepFrames = SAMPLE_RATE * 180 / 1000
        val silenceFrames = SAMPLE_RATE * 520 / 1000
        val buffer = ShortArray(beepFrames + silenceFrames)
        var cycleIdx = 0
        val rampCycles = 15 * SAMPLE_RATE / (beepFrames + silenceFrames)
        while (loopingRunning) {
            val progress = min(1.0, cycleIdx.toDouble() / rampCycles)
            val vol = 0.10 + 0.80 * progress
            fillBeep(buffer, beepFrames, freq, vol)
            track.write(buffer, 0, buffer.size)
            cycleIdx++
        }
    }

    private fun runSteadyPing(track: AudioTrack) {
        val freq = 880.0
        val beepFrames = SAMPLE_RATE * 180 / 1000
        val silenceFrames = SAMPLE_RATE * 520 / 1000
        val buffer = ShortArray(beepFrames + silenceFrames)
        fillBeep(buffer, beepFrames, freq, 0.9)
        while (loopingRunning) {
            track.write(buffer, 0, buffer.size)
        }
    }

    private fun runUrgentPulse(track: AudioTrack) {
        // Alternating 880 / 1320 Hz, tight 140 ms on / 180 ms off — more
        // urgent than the steady ping but still tonal (not siren-like).
        val beepFrames = SAMPLE_RATE * 140 / 1000
        val silenceFrames = SAMPLE_RATE * 180 / 1000
        val buffer = ShortArray(beepFrames + silenceFrames)
        var toggle = false
        while (loopingRunning) {
            val f = if (toggle) 1320.0 else 880.0
            fillBeep(buffer, beepFrames, f, 0.95)
            track.write(buffer, 0, buffer.size)
            toggle = !toggle
        }
    }

    private fun fillBeep(buffer: ShortArray, beepFrames: Int, freq: Double, volume: Double) {
        val envFrames = (SAMPLE_RATE * 15 / 1000).coerceAtMost(beepFrames / 2)
        var phase = 0.0
        for (i in 0 until beepFrames) {
            phase += 2 * PI * freq / SAMPLE_RATE
            val env = when {
                i < envFrames -> i.toDouble() / envFrames
                i >= beepFrames - envFrames -> (beepFrames - i).toDouble() / envFrames
                else -> 1.0
            }
            buffer[i] = (sin(phase) * Short.MAX_VALUE * volume * env).toInt().toShort()
        }
        for (i in beepFrames until buffer.size) buffer[i] = 0
    }

    private fun synthCheckInBell(): ShortArray {
        // Two-tone bell: 523 Hz (C5) 180 ms, 784 Hz (G5) 220 ms, 800 ms silence.
        // Total cycle 1.2 s — kindly insistent, obviously synthetic.
        val tone1 = SAMPLE_RATE * 180 / 1000
        val tone2 = SAMPLE_RATE * 220 / 1000
        val silence = SAMPLE_RATE * 800 / 1000
        val total = tone1 + tone2 + silence
        val out = ShortArray(total)
        var idx = 0
        var phase = 0.0
        val envFrames = SAMPLE_RATE * 15 / 1000
        for (pair in listOf(523.25 to tone1, 783.99 to tone2)) {
            val (freq, frames) = pair
            for (i in 0 until frames) {
                phase += 2 * PI * freq / SAMPLE_RATE
                val env = when {
                    i < envFrames -> i.toDouble() / envFrames
                    i >= frames - envFrames -> (frames - i).toDouble() / envFrames
                    else -> 1.0
                }
                out[idx++] = (sin(phase) * Short.MAX_VALUE * 0.7 * env).toInt().toShort()
            }
        }
        return out
    }

    private fun buildLoopingTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        return AudioTrack.Builder()
            .setAudioAttributes(alarmAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    fun playDismissCue(context: Context, onFinished: () -> Unit = {}) {
        playCue(context, listOf(Tone(700.0, 160), Tone(500.0, 200)), onFinished)
    }

    fun playSentCue(context: Context, onFinished: () -> Unit = {}) {
        playCue(
            context,
            listOf(Tone(523.25, 140), Tone(659.25, 140), Tone(783.99, 260)),
            onFinished,
        )
    }

    fun playFailureCue(context: Context, onFinished: () -> Unit = {}) {
        playCue(
            context,
            listOf(
                Tone(400.0, 220), Tone(0.0, 120),
                Tone(400.0, 220), Tone(0.0, 120),
                Tone(400.0, 340),
            ),
            onFinished,
        )
    }

    private data class Tone(val freq: Double, val ms: Int)

    @Synchronized
    private fun playCue(context: Context, tones: List<Tone>, onFinished: () -> Unit) {
        stop()
        val totalFrames = tones.sumOf { SAMPLE_RATE * it.ms / 1000 }
        if (totalFrames <= 0) return

        val buffer = ShortArray(totalFrames)
        var idx = 0
        var phase = 0.0
        for (tone in tones) {
            val frames = SAMPLE_RATE * tone.ms / 1000
            if (tone.freq <= 0.0) {
                idx += frames
                phase = 0.0
                continue
            }
            val envFrames = (SAMPLE_RATE * 15 / 1000).coerceAtMost(frames / 2)
            for (i in 0 until frames) {
                phase += 2 * PI * tone.freq / SAMPLE_RATE
                val env = when {
                    i < envFrames -> i.toDouble() / envFrames
                    i >= frames - envFrames -> (frames - i).toDouble() / envFrames
                    else -> 1.0
                }
                buffer[idx++] = (sin(phase) * Short.MAX_VALUE * 0.85 * env).toInt().toShort()
            }
        }

        raiseAlarmVolume(context)

        val track = AudioTrack.Builder()
            .setAudioAttributes(alarmAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(totalFrames * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        cueTrack = track
        track.setNotificationMarkerPosition(totalFrames)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) {
                    val finished = synchronized(this@AttentionSound) {
                        if (cueTrack === t) {
                            runCatching { t.release() }
                            cueTrack = null
                            true
                        } else false
                    }
                    if (finished) onFinished()
                }
                override fun onPeriodicNotification(t: AudioTrack) = Unit
            }
        )
        track.play()
    }

    @Synchronized
    fun stop() {
        loopingRunning = false
        loopingThread = null
        loopingTrack?.runCatching {
            if (state == AudioTrack.STATE_INITIALIZED) {
                pause()
                flush()
                release()
            }
        }
        loopingTrack = null
        cueTrack?.runCatching {
            if (state == AudioTrack.STATE_INITIALIZED) {
                pause()
                flush()
                release()
            }
        }
        cueTrack = null
    }

    fun raiseAlarmVolumePublic(context: Context) = raiseAlarmVolume(context)

    private fun raiseAlarmVolume(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0) }
    }
}
