package com.virgil.app.service

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import com.virgil.app.data.InteractionTracker

/**
 * Records media playback start as a presence signal, equivalent to a screen
 * unlock. Only the not-playing → playing transition is recorded. Ongoing
 * playback does NOT keep the user "alive": a passive media stream playing
 * for hours while the user is incapacitated will not suppress a check-in
 * escalation, because no new transition fires.
 *
 * Uses [AudioManager.AudioPlaybackCallback], API 26+, no runtime permission.
 */
class MusicActivityWatcher(private val context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(AudioManager::class.java)

    @Volatile private var wasPlaying: Boolean = false

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(
            configs: MutableList<AudioPlaybackConfiguration>?
        ) {
            val anyPlaying = configs?.isNotEmpty() == true
            if (!wasPlaying && anyPlaying) {
                InteractionTracker.record(context)
            }
            wasPlaying = anyPlaying
        }
    }

    fun start() {
        // Seed initial state — a watcher started while music is already
        // playing must not fire a spurious "start" on the first change event.
        wasPlaying = audioManager?.isMusicActive == true
        audioManager?.registerAudioPlaybackCallback(callback, null)
    }

    fun stop() {
        audioManager?.unregisterAudioPlaybackCallback(callback)
    }
}
