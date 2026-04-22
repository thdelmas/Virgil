package com.virgil.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.virgil.app.data.EmergencyPreferences
import com.virgil.app.ui.emergency.EmergencyCountdownActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Owns the staged pre-dispatch alarm (four 15-second phases) and the audio
 * and vibration that go with it. Lives as a foreground service so the user
 * cannot silence the alert by swiping the Activity away — the screen is a
 * skin over this service.
 *
 * Phases (each 15s):
 *   1 — BLUE   — vibration only, silent. "Something happened, you can cancel."
 *   2 — YELLOW — calm ping, volume ramps low→high. "Still here. Tap OK?"
 *   3 — ORANGE — steady ping at full volume. "About to alert."
 *   4 — RED    — urgent pulses. "Last chance."
 *
 * After phase 4 expires, [EmergencyDispatcher] sends SMS + call, then
 * [EmergencySirenService] takes over for bystander audio.
 */
class EmergencyAlarmService : Service() {

    enum class Phase(val seconds: Int) {
        P1_CALM(15), P2_NOTIFY(15), P3_WARN(15), P4_URGENT(15)
    }

    data class State(
        val active: Boolean,
        val phase: Phase,
        val secondsLeftInPhase: Int,
        val triggerType: String,
        val dispatching: Boolean,
        val finishedSent: Int,
        val finishedFailed: Int,
        val done: Boolean,
        val paused: Boolean,
    )

    private val handler = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    private lateinit var dispatcher: EmergencyDispatcher
    private lateinit var prefs: EmergencyPreferences

    @Volatile private var phase: Phase = Phase.P1_CALM
    @Volatile private var secondsLeft: Int = Phase.P1_CALM.seconds
    @Volatile private var triggerType: String = "fall"
    @Volatile private var dispatching: Boolean = false
    @Volatile private var visibleToUser: Boolean = false
    @Volatile private var stopping: Boolean = false
    @Volatile private var paused: Boolean = false
    @Volatile private var triggeredAt: Long = 0L
    @Volatile private var peakAccel: Float = 0f

    private val visibilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            visibleToUser = intent.getBooleanExtra(EXTRA_VISIBLE, false)
            if (!visibleToUser && !stopping) relaunchActivity()
        }
    }

    private val airplaneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_AIRPLANE_MODE_CHANGED &&
                AirplaneMode.isOn(context)
            ) {
                // User just flipped airplane on mid-alarm — tear down.
                cancelAlarm()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        dispatcher = EmergencyDispatcher(this)
        prefs = EmergencyPreferences(this)
        registerReceiver(
            visibilityReceiver,
            IntentFilter(ACTION_ACTIVITY_VISIBILITY),
            Context.RECEIVER_NOT_EXPORTED,
        )
        registerReceiver(
            airplaneReceiver,
            IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED),
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onDestroy() {
        stopping = true
        stopTicking()
        stopAudioVibration()
        runCatching { unregisterReceiver(visibilityReceiver) }
        runCatching { unregisterReceiver(airplaneReceiver) }
        isActive = false
        _state.value = IDLE_STATE
        EmergencyLauncher.clearAlarmInFlight()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelAlarm()
                return START_NOT_STICKY
            }
            ACTION_RELAUNCH_UI -> {
                relaunchActivity()
                return START_STICKY
            }
            ACTION_PAUSE -> {
                pauseCountdown()
                return START_STICKY
            }
            ACTION_RESUME -> {
                resumeCountdown()
                return START_STICKY
            }
        }

        // Idempotent — if the launcher is re-invoked while an alarm is running,
        // keep the existing countdown instead of resetting it.
        if (isActive) {
            startForegroundInternal()
            relaunchActivity()
            return START_STICKY
        }

        // Airplane mode is a hard off-switch — we can't dispatch SMS or a
        // call with radios off, so starting the countdown would only panic
        // the user without a useful outcome.
        if (AirplaneMode.isOn(this)) {
            FallDetectionService.alarmInFlight = false
            stopSelf()
            return START_NOT_STICKY
        }

        triggerType = intent?.getStringExtra(FallDetectionService.EXTRA_TRIGGER_TYPE) ?: "fall"
        peakAccel = intent?.getFloatExtra(FallDetectionService.EXTRA_PEAK_ACCEL, 0f) ?: 0f
        triggeredAt = System.currentTimeMillis()
        phase = Phase.P1_CALM
        secondsLeft = phase.seconds
        dispatching = false
        stopping = false
        isActive = true
        FallDetectionService.alarmInFlight = true

        startForegroundInternal()
        emitState()
        AttentionSound.raiseAlarmVolumePublic(this)
        applyPhaseAudio(this, phase)
        applyPhaseVibration(this, phase)
        launchActivity()
        startTicking()
        return START_STICKY
    }

    private fun startForegroundInternal() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startTicking() {
        stopTicking()
        val r = object : Runnable {
            override fun run() {
                if (stopping || !isActive) return
                secondsLeft -= 1
                if (secondsLeft <= 0) {
                    advancePhase()
                } else {
                    applyPhaseAudioTick()
                    emitState()
                    refreshNotification()
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        tickRunnable = r
        handler.postDelayed(r, 1000L)
    }

    private fun stopTicking() {
        tickRunnable?.let(handler::removeCallbacks)
        tickRunnable = null
    }

    private fun advancePhase() {
        val next = when (phase) {
            Phase.P1_CALM -> Phase.P2_NOTIFY
            Phase.P2_NOTIFY -> Phase.P3_WARN
            Phase.P3_WARN -> Phase.P4_URGENT
            Phase.P4_URGENT -> null
        }
        if (next == null) {
            onCountdownExpired()
            return
        }
        phase = next
        secondsLeft = next.seconds
        applyPhaseAudio(this, next)
        applyPhaseVibration(this, next)
        emitState()
        refreshNotification()
        if (!visibleToUser) relaunchActivity()
        handler.postDelayed(tickRunnable ?: return, 1000L)
    }

    private fun onCountdownExpired() {
        dispatching = true
        stopAudioVibration()
        emitState()
        refreshNotification()

        val dispatchTrigger = if (triggerType == "fall") {
            EmergencyDispatcher.TriggerType.FALL
        } else {
            EmergencyDispatcher.TriggerType.NO_RESPONSE
        }
        val contacts = runBlocking { prefs.contacts.first() }
        val customTemplate = runBlocking { prefs.smsMessageOverride.first() }

        if (contacts.isEmpty()) {
            dispatching = false
            AttentionSound.playFailureCue(applicationContext)
            _state.value = State(
                active = true, phase = Phase.P4_URGENT, secondsLeftInPhase = 0,
                triggerType = triggerType, dispatching = false,
                finishedSent = 0, finishedFailed = 0, done = true,
                paused = false,
            )
            return
        }

        dispatcher.dispatch(contacts, customTemplate, dispatchTrigger) { result ->
            if (result.smsSent > 0) {
                AttentionSound.playSentCue(applicationContext)
            } else {
                AttentionSound.playFailureCue(applicationContext)
            }
            _state.value = State(
                active = true, phase = Phase.P4_URGENT, secondsLeftInPhase = 0,
                triggerType = triggerType, dispatching = false,
                finishedSent = result.smsSent, finishedFailed = result.smsFailed,
                done = true,
                paused = false,
            )
            // Hand off to the bystander siren, then stop this service so the
            // siren owns the alarm state from here on.
            handler.postDelayed({
                EmergencySirenService.start(applicationContext)
                // Don't clear alarmInFlight here — the siren service owns it
                // now and will clear it when it stops.
                stopping = true
                isActive = false
                _state.value = IDLE_STATE
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }, 1200L)
        }
    }

    private fun cancelAlarm() {
        stopping = true
        stopTicking()
        stopAudioVibration()
        AttentionSound.playDismissCue(applicationContext)

        // Record what just happened so the user has something concrete to
        // describe if they want to report a false alarm. Snapshot lives
        // on-device; nothing leaves the phone until the user opts in.
        val peak = if (triggerType == "fall" && peakAccel > 0f) peakAccel else null
        val snapshot = com.virgil.app.data.FalseAlarmSnapshot.capture(
            triggeredAt = triggeredAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
            triggerType = triggerType,
            peakAccel = peak,
            phaseAtDismiss = when (phase) {
                Phase.P1_CALM -> 1
                Phase.P2_NOTIFY -> 2
                Phase.P3_WARN -> 3
                Phase.P4_URGENT -> 4
            },
            secondsLeftAtDismiss = secondsLeft,
        )
        com.virgil.app.data.FalseAlarmLog.save(applicationContext, snapshot)
        FalseAlarmReportPrompt.post(applicationContext)

        isActive = false
        _state.value = IDLE_STATE
        EmergencyLauncher.clearAlarmInFlight()
        EmergencyLauncher.cancel(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseCountdown() {
        if (paused || !isActive || dispatching) return
        paused = true
        stopTicking()
        stopAudioVibration()
        emitState()
        refreshNotification()
    }

    private fun resumeCountdown() {
        if (!paused || !isActive || dispatching) return
        paused = false
        applyPhaseAudio(this, phase)
        applyPhaseVibration(this, phase)
        emitState()
        refreshNotification()
        startTicking()
    }

    private fun emitState() {
        _state.value = State(
            active = true, phase = phase, secondsLeftInPhase = secondsLeft,
            triggerType = triggerType, dispatching = dispatching,
            finishedSent = 0, finishedFailed = 0, done = false,
            paused = paused,
        )
    }

    private fun applyPhaseAudioTick() {
        // Volume ramp during P2 is driven by the audio thread itself via the
        // elapsed-time cue; nothing to do on each tick here. Retained as a
        // hook point so future phases can adjust per-second.
    }

    private fun stopAudioVibration() {
        AttentionSound.stop()
        cancelPhaseVibration(this)
    }

    private fun launchActivity() {
        val activityIntent = Intent(this, EmergencyCountdownActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            putExtra(FallDetectionService.EXTRA_TRIGGER_TYPE, triggerType)
        }
        runCatching { startActivity(activityIntent) }
    }

    /**
     * Re-posts the full-screen notification so Android re-fires the intent
     * when the user swiped us away. Foreground services can also directly
     * call startActivity — we do both so whichever path the OS permits wins.
     */
    private fun relaunchActivity() {
        refreshNotification()
        launchActivity()
    }

    private fun buildNotification(): Notification =
        buildAlarmNotification(
            context = this,
            phase = phase,
            secondsLeft = secondsLeft,
            paused = paused,
            dispatching = dispatching,
            triggerType = triggerType,
        )

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        const val NOTIFICATION_ID = 0x4156 // "AV" — alarm-virgil
        const val ACTION_CANCEL = "com.virgil.app.ALARM_CANCEL"
        const val ACTION_PAUSE = "com.virgil.app.ALARM_PAUSE"
        const val ACTION_RESUME = "com.virgil.app.ALARM_RESUME"
        const val ACTION_RELAUNCH_UI = "com.virgil.app.ALARM_RELAUNCH_UI"
        const val ACTION_ACTIVITY_VISIBILITY = "com.virgil.app.ALARM_ACTIVITY_VISIBILITY"
        const val EXTRA_VISIBLE = "visible"
        const val TOTAL_STEPS = 4
        const val HOLD_TO_DISARM_MS = 5000L

        @Volatile
        var isActive: Boolean = false
            private set

        private val IDLE_STATE = State(
            active = false, phase = Phase.P1_CALM, secondsLeftInPhase = 0,
            triggerType = "fall", dispatching = false,
            finishedSent = 0, finishedFailed = 0, done = false,
            paused = false,
        )

        private val _state = MutableStateFlow(IDLE_STATE)
        val state: StateFlow<State> = _state.asStateFlow()

        fun start(context: Context, triggerType: String, peakAccel: Float = 0f) {
            val intent = Intent(context, EmergencyAlarmService::class.java)
                .putExtra(FallDetectionService.EXTRA_TRIGGER_TYPE, triggerType)
                .putExtra(FallDetectionService.EXTRA_PEAK_ACCEL, peakAccel)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, EmergencyAlarmService::class.java)
                .setAction(ACTION_CANCEL)
            context.startService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, EmergencyAlarmService::class.java)
                .setAction(ACTION_PAUSE)
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, EmergencyAlarmService::class.java)
                .setAction(ACTION_RESUME)
            context.startService(intent)
        }

        fun reportActivityVisibility(context: Context, visible: Boolean) {
            val i = Intent(ACTION_ACTIVITY_VISIBILITY)
                .setPackage(context.packageName)
                .putExtra(EXTRA_VISIBLE, visible)
            context.sendBroadcast(i)
        }
    }
}
