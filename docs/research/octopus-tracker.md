# Octopus Investigation — Virgil

Method: [theophile.world/guides/octopus-investigation](https://theophile.world/guides/octopus-investigation).
Goal: discover FOSS projects and maintainers whose work directly informs Virgil's on-device fall-detection and check-in mechanics, without adding network or telemetry surface.

## Tentacles

- **T1 — Panic / emergency interop** — Android-wide contracts for panic triggers and receivers.
- **T2 — On-device sensor tripwires** — accelerometer / microphone / light event pipelines comparable to fall detection.
- **T3 — Check-in & duress UX** — cadence, grace windows, cancel-to-abort flows.
- **T4 — Battery-conscious sensor + location** — batching, activity-recognition, wakelock discipline.
- **T5 — Privacy-first FOSS Android** — F-Droid-grade patterns, no-network idioms, minimal permissions.

## Origins crawled

| Origin | Date | Depth | Result |
| --- | --- | --- | --- |
| `guardianproject` | 2026-04-17 | repos only | 3 direct hits, 1 reference, leads queued |

## Findings

| Repo | Tentacle | License | Status | Relevance |
| --- | --- | --- | --- | --- |
| [`guardianproject/PanicKit`](https://github.com/guardianproject/PanicKit) | T1 | LGPL-2.1 | **Emitted (wire-compat, no dep)** | Standard Android Intent contract between panic triggers and receivers. Virgil emits `info.guardianproject.panic.action.TRIGGER` from `EmergencyDispatcher.dispatch()` via [`PanicBroadcast`](../../android/app/src/main/java/com/virgil/app/service/PanicBroadcast.kt); no library dep taken. |
| [`guardianproject/haven`](https://github.com/guardianproject/haven) | T2 | GPL-3.0 | **Study** | Production on-device sensor monitors (accelerometer, microphone, light, camera). Reference for event thresholding, debouncing, and foreground-service lifecycle — same shape as `FallDetectionService`. Read-only unless Virgil relicenses to GPL-3.0. |
| [`guardianproject/ripple`](https://github.com/guardianproject/ripple) | T1, T3 | GPL-3.0 | **Study** | Reference PanicKit *trigger* app. UX for arming, confirmation, cancel. Read its alert dispatch and configuration flow before designing Virgil's duress screen. |
| [`guardianproject/TrustedIntents`](https://github.com/guardianproject/TrustedIntents) | T1 | Apache-2.0 | Reference | Signing-key-pinned Intent dispatch. Relevant if Virgil becomes a PanicKit receiver and needs to constrain who can trigger it. |
| [`guardianproject/LocationPrivacy`](https://github.com/guardianproject/LocationPrivacy) | T5 | — | Reference | Location-sharing minimizer. Patterns worth comparing to Virgil's SMS-location payload. |
| [`guardianproject/InTheClear`](https://github.com/guardianproject/InTheClear) | T3 | — | Archive-read | 2015 alert/wipe app. Historical reference for dead-man UX; likely stale APIs. |

## Top recommendation (landed)

**PanicKit-compatible broadcast emitted from the single dispatch site.** All panic paths (fall detection, missed check-in, future triggers) converge at `EmergencyDispatcher.dispatch()`; that is the only place panic becomes real (after countdown, before SMS). Virgil now emits the PanicKit `info.guardianproject.panic.action.TRIGGER` broadcast there, explicitly per responder discovered via `PackageManager`, so manifest-declared receivers on Android 8+ reach their targets. No library dependency; `<queries>` added to the manifest for Android 11+ package visibility.

- Files: [`PanicBroadcast.kt`](../../android/app/src/main/java/com/virgil/app/service/PanicBroadcast.kt), [`EmergencyDispatcher.kt`](../../android/app/src/main/java/com/virgil/app/service/EmergencyDispatcher.kt), [`AndroidManifest.xml`](../../android/app/src/main/AndroidManifest.xml), [`PanicBroadcastTest.kt`](../../android/app/src/test/java/com/virgil/app/service/PanicBroadcastTest.kt).
- Manifesto fit: on-device IPC only; no network, no new permission, no new dep.
- Secondary: read [`haven`](https://github.com/guardianproject/haven)'s `monitor` package before the next fall-detection tuning pass.

## Queued unexplored leads

Next origins to crawl when this search is resumed. Selected to broaden beyond Guardian Project's graph.

- `bitfireAT` — DAVx5 maintainers; FOSS Android app quality / battery discipline (T4, T5).
- `d4rken-org` — SD Maid, Capod; battery & sensor-adjacent Android work (T4).
- `IzzyOnDroid` — F-Droid curator; broad reach into privacy-first Android app graph (T5).
- `commonsguy` (Mark Murphy) — Android idiom reference; stars map to well-engineered libraries (T4).
- Top contributors to `haven` and `ripple` — graph edges from the org-origin into individual maintainers (T1, T2).
- GitHub code search: `info.guardianproject.panic` usages — maps all apps on the PanicKit protocol (T1, integration partners).

## Log

- 2026-04-17 — Investigation opened. Origin: `guardianproject`. Tentacles T1–T3 seeded. PanicKit surfaced as top adopt-candidate.
- 2026-04-17 — PanicKit trigger-broadcast emission landed at `EmergencyDispatcher.dispatch()`; compile + unit tests green. `make quality-fast` still red on pre-existing compliance failures unrelated to this change.
- 2026-04-17 — Deep read: `guardianproject/haven`'s `AccelerometerMonitor.java`. See **Haven deep dive** below.

## Haven deep dive — fall-detection algorithm comparison

Haven's accelerometer monitor is **not adoptable as code** (it solves intrusion detection, not fall detection), but the comparison surfaces one concrete Virgil tuning experiment.

- **Sample rate.** Haven runs at `SENSOR_DELAY_NORMAL` (~200ms) and further gates itself to 100ms; Virgil runs at `SENSOR_DELAY_GAME` (~20ms). Virgil's 50Hz is ~10× more aggressive than Haven's ~5Hz. Fall physics (freefall ≈ 300ms, impact spike ≈ 50ms) does not need 50Hz, but does need more than 5Hz. Middle ground `SENSOR_DELAY_UI` (~60ms) likely preserves detection while cutting the accelerometer's duty-cycle power draw. **Experiment to run next:** replay stored traces via the debug path at [`FallDetectionService.kt:102-129`](../../android/app/src/main/java/com/virgil/app/service/FallDetectionService.kt#L102-L129) after decimating samples to each rate; confirm detection survives before changing the registration rate.
- **Gravity handling.** Haven uses delta + leaky IIR (`mAccel = mAccel * 0.9 + delta`) which naturally removes gravity bias. Virgil uses absolute magnitude because freefall detection needs to see values near zero. Haven's filter is **incompatible with Virgil's state machine** — do not port.
- **State machine.** Virgil's three-phase freefall → impact → stillness (see [`FallDetectionAlgorithm.kt`](../../android/app/src/main/java/com/virgil/app/analysis/FallDetectionAlgorithm.kt)) is strictly better for the fall problem than Haven's single-threshold approach, which would false-positive on a phone being set down firmly.
- **Debounce.** Haven's `remainingAlertPeriod` cooldown is marginally cleaner than Virgil's `reset()` for suppressing re-fire while the countdown activity is on screen, but both work. Revisit only if the countdown path ever produces duplicate triggers in the field.
