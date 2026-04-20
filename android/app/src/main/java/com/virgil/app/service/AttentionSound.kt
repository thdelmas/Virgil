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

    private val ringAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    /**
     * Loop the user's default phone ringtone until [stop]. A check-in is a
     * "hey, are you OK?" — not an emergency — so we use the familiar ring, not
     * the alarm clock. Routed through USAGE_ALARM so it still bypasses silent
     * and DND (missing it would defeat the whole point).
     */
    @Synchronized
    fun playCheckInRing(context: Context) {
        stop()
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        ringtonePlayer = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(ringAttributes)
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
        // Idempotent: the launcher and the countdown activity both call this
        // on the same alarm. Second call is a no-op so the siren keeps going
        // smoothly instead of restarting and clipping the audio.
        if (sirenRunning) return
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

    /**
     * Looping "tap to cancel" beep during the pre-dispatch countdown.
     * Lower and more sparse than [playEmergencySiren] on purpose — the user
     * should hear a clearly cancellable warning, not the all-hands bystander
     * alarm that follows if nothing is tapped.
     */
    @Synchronized
    fun playCountdownWarning(context: Context) {
        if (sirenRunning) return
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

        sirenThread = thread(name = "VirgilCountdown", isDaemon = true) {
            val freq = 880.0
            val beepMs = 180
            val silenceMs = 520
            val beepFrames = sampleRate * beepMs / 1000
            val silenceFrames = sampleRate * silenceMs / 1000
            val buffer = ShortArray(beepFrames + silenceFrames)
            val envFrames = (sampleRate * 15 / 1000).coerceAtMost(beepFrames / 2)
            var phase = 0.0
            for (i in 0 until beepFrames) {
                phase += 2 * PI * freq / sampleRate
                val env = when {
                    i < envFrames -> i.toDouble() / envFrames
                    i >= beepFrames - envFrames -> (beepFrames - i).toDouble() / envFrames
                    else -> 1.0
                }
                buffer[i] = (sin(phase) * Short.MAX_VALUE * 0.85 * env).toInt().toShort()
            }
            try {
                while (sirenRunning) {
                    track.write(buffer, 0, buffer.size)
                }
            } catch (_: Throwable) {
                // Track released from another thread — exit cleanly.
            }
        }
    }

    /**
     * Short status cues that identify the countdown's outcome. Non-looping;
     * release the track when the tone sequence finishes. Safe to call even
     * while the siren is playing — each cue calls [stop] first.
     */
    fun playDismissCue(context: Context, onFinished: () -> Unit = {}) {
        playCue(context, listOf(Tone(700.0, 160), Tone(500.0, 200)), onFinished)
    }

    fun playSentCue(context: Context, onFinished: () -> Unit = {}) {
        // Ascending C5-E5-G5 major triad → reassuring "handled" feel.
        playCue(
            context,
            listOf(Tone(523.25, 140), Tone(659.25, 140), Tone(783.99, 260)),
            onFinished,
        )
    }

    fun playFailureCue(context: Context, onFinished: () -> Unit = {}) {
        // Three low harsh bursts with short gaps → urgent "something went wrong".
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
        val sampleRate = 44100
        val totalFrames = tones.sumOf { sampleRate * it.ms / 1000 }
        if (totalFrames <= 0) return

        val buffer = ShortArray(totalFrames)
        var idx = 0
        var phase = 0.0
        for (tone in tones) {
            val frames = sampleRate * tone.ms / 1000
            if (tone.freq <= 0.0) {
                idx += frames
                phase = 0.0
                continue
            }
            for (i in 0 until frames) {
                phase += 2 * PI * tone.freq / sampleRate
                // Light envelope: 15 ms attack / release to avoid clicks.
                val envFrames = (sampleRate * 15 / 1000).coerceAtMost(frames / 2)
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
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(totalFrames * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        sirenTrack = track
        sirenRunning = false
        track.setNotificationMarkerPosition(totalFrames)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) {
                    val finished = synchronized(this@AttentionSound) {
                        if (sirenTrack === t) {
                            runCatching { t.release() }
                            sirenTrack = null
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
