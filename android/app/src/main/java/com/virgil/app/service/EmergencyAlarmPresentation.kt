package com.virgil.app.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.virgil.app.R
import com.virgil.app.VirgilApp
import com.virgil.app.ui.emergency.EmergencyCountdownActivity

/**
 * Presentation helpers for [EmergencyAlarmService]: the alarm notification,
 * the per-phase audio cue, and the per-phase vibration pattern. Split out
 * so the service itself stays focused on lifecycle and state.
 */

fun buildAlarmNotification(
    context: Context,
    phase: EmergencyAlarmService.Phase,
    secondsLeft: Int,
    paused: Boolean,
    dispatching: Boolean,
    triggerType: String,
): Notification {
    val activityIntent = Intent(context, EmergencyCountdownActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        putExtra(FallDetectionService.EXTRA_TRIGGER_TYPE, triggerType)
    }
    val fullScreenPi = PendingIntent.getActivity(
        context, 0, activityIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        balAllowedActivityOptions(),
    )
    val cancelPi = PendingIntent.getService(
        context, 1,
        Intent(context, EmergencyAlarmService::class.java)
            .setAction(EmergencyAlarmService.ACTION_CANCEL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val title = when {
        dispatching -> context.getString(R.string.siren_dispatching)
        triggerType == "checkin" -> context.getString(R.string.countdown_title_no_response)
        else -> context.getString(R.string.countdown_title_fall)
    }
    val stepIndex = phase.stepIndex()
    val body = if (paused) {
        context.getString(
            R.string.countdown_notification_paused, stepIndex,
            EmergencyAlarmService.TOTAL_STEPS,
        )
    } else {
        context.getString(
            R.string.countdown_notification_step, stepIndex,
            EmergencyAlarmService.TOTAL_STEPS, secondsLeft,
        )
    }
    return NotificationCompat.Builder(context, VirgilApp.CHANNEL_EMERGENCY)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle(title)
        .setContentText(body)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setOngoing(true)
        .setContentIntent(fullScreenPi)
        .setFullScreenIntent(fullScreenPi, true)
        .addAction(
            android.R.drawable.ic_menu_close_clear_cancel,
            context.getString(R.string.countdown_im_ok),
            cancelPi,
        )
        .build()
}

fun applyPhaseAudio(context: Context, phase: EmergencyAlarmService.Phase) {
    when (phase) {
        EmergencyAlarmService.Phase.P1_CALM -> AttentionSound.stop()
        EmergencyAlarmService.Phase.P2_NOTIFY ->
            AttentionSound.playStagedAlarm(context, AttentionSound.Stage.RAMPING)
        EmergencyAlarmService.Phase.P3_WARN ->
            AttentionSound.playStagedAlarm(context, AttentionSound.Stage.STEADY)
        EmergencyAlarmService.Phase.P4_URGENT ->
            AttentionSound.playStagedAlarm(context, AttentionSound.Stage.URGENT)
    }
}

fun applyPhaseVibration(context: Context, phase: EmergencyAlarmService.Phase) {
    val vibrator = getVibrator(context) ?: return
    vibrator.cancel()
    val pattern = when (phase) {
        EmergencyAlarmService.Phase.P1_CALM ->
            longArrayOf(0, 400, 800, 400, 800, 400, 800)
        EmergencyAlarmService.Phase.P2_NOTIFY ->
            longArrayOf(0, 500, 500, 500, 500)
        EmergencyAlarmService.Phase.P3_WARN ->
            longArrayOf(0, 600, 300, 600, 300, 600, 300)
        EmergencyAlarmService.Phase.P4_URGENT ->
            longArrayOf(0, 250, 120, 250, 120, 250, 120, 600, 200)
    }
    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
}

fun cancelPhaseVibration(context: Context) {
    getVibrator(context)?.cancel()
}

private fun getVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    }
}

internal fun EmergencyAlarmService.Phase.stepIndex(): Int = when (this) {
    EmergencyAlarmService.Phase.P1_CALM -> 1
    EmergencyAlarmService.Phase.P2_NOTIFY -> 2
    EmergencyAlarmService.Phase.P3_WARN -> 3
    EmergencyAlarmService.Phase.P4_URGENT -> 4
}

/**
 * Android 14 changed the BAL default for PendingIntents fired from notifications,
 * and Android 15 tightened it further: without this opt-in the full-screen-intent
 * activity stays hidden behind a heads-up while the alarm rings. The *creator*
 * mode is the one legal at PendingIntent creation time — the sender mode throws
 * IllegalArgumentException here.
 */
internal fun balAllowedActivityOptions(): Bundle? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return ActivityOptions.makeBasic()
        .setPendingIntentCreatorBackgroundActivityStartMode(
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
        )
        .toBundle()
}
