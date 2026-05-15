# Virgil — Design

How the code is organized and why. Read this when you need to put a change in
the right place. For *what* belongs in the product, see
[MANIFESTO.md](MANIFESTO.md) and [docs/SCOPE.md](docs/SCOPE.md). For
distribution / Play Store guardrails, see [docs/COMPLIANCE.md](docs/COMPLIANCE.md).

## One pipeline, three triggers

Virgil is one emergency pipeline with three entry points. Everything downstream
of the trigger is shared: countdown, siren, contacts, SMS dispatch, primary
call. If a new feature can't reuse this pipeline, it doesn't belong in Virgil
(see [docs/SCOPE.md](docs/SCOPE.md)).

```
trigger ──► verify ──► EmergencyAlarmService ──► EmergencyDispatcher ──► EmergencySirenService
                       (4×15s staged countdown)   (location + SMS + call)   (bystander audio)
```

The three triggers:

| Trigger | Source | Verify gate | Skips countdown |
|---|---|---|---|
| Fall | `FallDetectionService` (accelerometer + gyro + gravity) | 30s on-device verify (silent → haptic) | no |
| No-response | `CheckInReceiver` after silent interval | 5-min "Are you there?" notification | no |
| Manual panic | `PanicTrigger` (1.5s hold on home button) | hold duration only | yes — siren fires immediately |

The dispatch contract — who gets texted, what the SMS says, when the primary
gets called — lives entirely in [`EmergencyDispatcher`](android/app/src/main/java/com/virgil/app/service/EmergencyDispatcher.kt).
Triggers pass a `TriggerType`; the dispatcher chooses the message template and
whether to place a follow-up call (panic does not call — the siren would
swallow the line; see [`EmergencyDispatcher.dispatch`](android/app/src/main/java/com/virgil/app/service/EmergencyDispatcher.kt#L85-L155)).

## Module map

```
android/app/src/main/java/com/virgil/app/
├── analysis/        Pure detection logic, no Android deps — easy to unit-test
│   └── FallDetectionAlgorithm  Free-fall → impact → stillness state machine
├── data/            On-device persistence (DataStore) and small value types
│   ├── EmergencyPreferences    Contacts, intervals, SMS overrides, sleep hours
│   ├── InteractionTracker      Last-activity timestamp for check-in scheduling
│   ├── ActivityBaseline        Learned daily-rhythm (for early check-in)
│   └── FalseAlarmSnapshot      Local-only snapshot for opt-in user reporting
├── permissions/     Permission state + monitor (notify when something is missing)
├── service/         All foreground services, receivers, the dispatch pipeline
│   ├── FallDetectionService    Sensor owner, verify state machine
│   ├── CheckInService          Schedules check-in alarms via AlarmManager
│   ├── EmergencyAlarmService   The 4-phase staged countdown
│   ├── EmergencyDispatcher     Location, SMS-with-confirmation, call
│   ├── EmergencySirenService   Bystander siren after dispatch
│   ├── PanicTrigger            Manual panic entry point
│   ├── StopAuthGate            BiometricPrompt-gated panic stop
│   ├── BootReceiver            Restart services after reboot / package replace
│   ├── AirplaneMode            One central read; everyone consults it
│   ├── MusicActivityWatcher    Treat media-playback start as a presence signal
│   └── EmergencyLauncher       Single funnel that starts the alarm service
└── ui/              Jetpack Compose
    ├── MainActivity, HomeScreen, PanicButton
    ├── permissions/ Onboarding flow
    ├── settings/    Emergency settings, contacts, language, intro SMS
    ├── emergency/   Countdown + siren screens (lock-screen capable)
    └── report/      Opt-in false-alarm report screen
```

`VirgilApp` (Application class) owns notification channels and process-wide
init. `MainActivity` is the only exported activity; the countdown activity is
internal and shown via full-screen intent + `showOnLockScreen`.

## Service ownership rules

Services are not a free-for-all. Each one owns a discrete phase of the
pipeline; ownership transfers explicitly.

- **`FallDetectionService`** owns the accelerometer / gyro / gravity sensor
  registration and the verify state machine. It is the sole writer of
  `FallDetectionService.alarmInFlight` (a process-global flag that suppresses
  re-entrant detection while a countdown or siren is up). It hands off to
  `EmergencyAlarmService` via `EmergencyLauncher.launch`.
- **`CheckInService`** owns the AlarmManager schedule and the
  `InteractionTracker` reset path. It does not start the countdown directly —
  `CheckInReceiver` does, after the 5-minute grace expires unanswered.
- **`EmergencyAlarmService`** owns the staged countdown (P1_CALM →
  P2_NOTIFY → P3_WARN → P4_URGENT, 15s each), the audio/vibration pattern, and
  the visibility handshake with `EmergencyCountdownActivity`. On phase 4
  expiry it drives `EmergencyDispatcher.dispatch`, then hands off to
  `EmergencySirenService` and stops itself.
- **`EmergencySirenService`** owns post-dispatch bystander audio and the
  authenticated stop flow (via `StopAuthGate`). It clears `alarmInFlight` when
  it stops.

Two invariants worth preserving:

1. **One alarm at a time.** `alarmInFlight` is the gate. Setting it without a
   matching clear leaves the device deaf to subsequent falls. Clearing it
   while audio is still playing lets a second alarm stack on top.
2. **Airplane mode is a hard off-switch.** Each service watches
   `ACTION_AIRPLANE_MODE_CHANGED`; if the user flips airplane on, the alarm
   tears down. We can't dispatch SMS or place a call with radios off, and
   continuing the countdown would just frighten the user without an outcome.

## Data flow: nothing leaves the device until alert

The manifesto rule (#1) is absolute. The only off-device traffic is:

- **SMS**, sent only by `EmergencyDispatcher` on alert dispatch (or the
  one-shot intro SMS the user explicitly triggers from settings).
- **Phone call** to the primary contact (fall and no-response only).
- **Location read** from `FusedLocationProviderClient` — happens on-device,
  but the result is included in the SMS body.

Everything else lives in `androidx.datastore.preferences` on the device.
There is no analytics SDK, no crash reporter, no remote config, no
auto-update path beyond the Play Store itself. Adding any of these requires
explicit approval (see CLAUDE.md "Hard don't list").

The intro-SMS path uses `sendSmsFireAndForget`; the alert path uses
`sendSmsVerified` — same SmsManager, but the alert path listens to the
per-part `sentIntent` broadcast and reports an honest `SmsStatus`. The
post-alert UI shows which contacts the modem confirmed and which it didn't.
The 8s timeout in [`EmergencyDispatcher.SMS_VERIFY_TIMEOUT_MS`](android/app/src/main/java/com/virgil/app/service/EmergencyDispatcher.kt#L420-L422)
is the upstream backstop so the bystander siren handoff isn't held hostage by
a stuck modem.

## Threading & lifecycle conventions

- **Main thread for everything visible.** Service callbacks
  (`onStartCommand`, `onSensorChanged`) run on the main thread; we keep them
  cheap. The detection algorithm does not allocate per sample.
- **Coroutines are limited.** A few `runBlocking { prefs.X.first() }` reads
  appear at well-defined sync points (countdown expiry, service start) where
  reading from DataStore on main is acceptable. Don't sprinkle coroutines
  through services; they're not async-by-default.
- **Wake locks are conditional.** `FallDetectionService` only takes a
  partial wake lock when the device lacks a wake-up accelerometer (sensor
  hub). On modern phones with wake-up sensors, the CPU sleeps between
  samples. Every `acquire()` has a matching `release()` in `stopSensor`.
- **Foreground service type is `specialUse`** with a per-service subtype
  string declared in [AndroidManifest.xml](android/app/src/main/AndroidManifest.xml).
  Do not revert to `health` (see [docs/COMPLIANCE.md §4](docs/COMPLIANCE.md)).

## Detection algorithm: read this before tweaking thresholds

[`FallDetectionAlgorithm`](android/app/src/main/java/com/virgil/app/analysis/FallDetectionAlgorithm.kt)
is intentionally pure (no Android imports) so it can be exercised against
recorded sensor traces in unit tests. It accepts three candidate paths into
an "impact" state, then requires near-1g stillness 300 ms–5 s after impact:

1. Genuine free-fall (≥40 ms below ~0.7g, or any sample below ~0.4g) followed
   within 500 ms by an impact above ~2.3g.
2. Sustained prior motion (≥1 s of non-stillness) plus impact above ~3g.
3. No prior motion plus a hard impact above ~5g (fall from a chair / bed).

When a gyroscope is present, impacts must coincide with rotation above
~4 rad/s within a 500 ms window — this rejects pocket-drops that don't
tumble the way a body does. When a gravity sensor is present, candidates
where orientation went from "flat" to "upright" are rejected as
pocket-insertion gestures, not falls.

The verify gate in `FallDetectionService` is a separate layer on top:
15 s silent + 15 s haptic before the staged countdown starts. Motion during
verify cancels. This protects against algorithm false-positives without
making the user wait through every false alert in silence.

## Persistence & migrations

DataStore Preferences only. The keys are defined in [`EmergencyPreferences`](android/app/src/main/java/com/virgil/app/data/EmergencyPreferences.kt).
Don't reach for Room / SQLite — there is no relational data here.

When changing a preference shape, write a one-shot read-old → write-new
migration in `EmergencyPreferences` rather than versioning the schema. Most
preferences have safe defaults; use them.

## UI layering

- **Compose only.** No XML layouts. Theme lives in `ui/theme/`.
- **`MainActivity`** is the single Compose host. Navigation is in-process
  (Navigation Compose).
- **`EmergencyCountdownActivity`** is the only other entry. It is a thin
  skin over `EmergencyAlarmService.state` (a `StateFlow`) — the service is
  the source of truth, the activity just renders. If the user swipes the
  activity away, the service re-launches it (`relaunchActivity`) so the
  countdown can't be silenced by dismissal.
- **Localization is the contract.** Per-contact language is honored: each
  contact's SMS is localized via `AppLocale.wrap(context, languageCode)`. Add
  new strings to every `values-*/strings.xml` (en, fr, es) at once or `make
  compliance` will block the commit.

## Tests

Unit tests live under [android/app/src/test/](android/app/src/test/).
The detection algorithm has trace-replay tests; the dispatcher has tests for
SMS chunking, error-code mapping, and message formatting. Add tests for any
new branch in the trigger → dispatch path.

Recorded sensor traces (CSV) live under [traces/](traces/) and are replayable
via the `ACTION_DEBUG_REPLAY` debug-only intent on `FallDetectionService`.

## Adding a feature: where it goes

A new feature is a question of **which entry point** and **what the response
is**:

- **Sensor-driven trigger?** Add it as a new service alongside
  `FallDetectionService`, reuse `EmergencyLauncher` to start the alarm. Add
  a new `TriggerType` to `EmergencyDispatcher` and a corresponding SMS
  template. Add a new manifest service entry with a `specialUse` subtype.
- **User-driven trigger?** Follow `PanicTrigger`'s shape — a UI element +
  a service that calls `EmergencyLauncher`.
- **Different response (not text-and-call contacts)?** It probably doesn't
  belong here — read [docs/SCOPE.md](docs/SCOPE.md) before proposing it.
- **Different message content?** Add a `R.string.emergency_sms_*` per
  language and a branch in `EmergencyDispatcher.dispatch`. Don't fork the
  dispatcher.

## Things that look weird but are deliberate

- `runBlocking { prefs.X.first() }` in a service hot path. DataStore reads
  are fast and cached after first access; the alternative (lifting the
  service into a coroutine scope) costs more complexity than the blocking
  buys back.
- Both `startActivity` and a full-screen-intent notification when launching
  the countdown. Either path can be silently denied by the OS depending on
  background-launch policy; we do both so whichever the OS permits wins.
- Per-contact language stored on the contact, not derived from app locale.
  An English-speaking user may have a Spanish-speaking emergency contact;
  the SMS goes in the contact's language, not the app's.
- The siren keeps playing after dispatch. It's a beacon for bystanders and
  a deterrent in panic-trigger cases — not a UI sound. Stopping it is
  authenticated (see [`StopAuthGate`](android/app/src/main/java/com/virgil/app/service/StopAuthGate.kt))
  on the panic flow.
