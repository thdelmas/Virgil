# Survival testing — does the watcher stay alive?

An always-on guardian is worthless if the OS kills its foreground service. This
is the make-or-break reliability question for Virgil (tracking: issue #1). You
do **not** need to own every phone to make progress — work outward from the
baseline.

## The instrument

`ServiceHeartbeat` (wired into `FallDetectionService`) appends a line every
~minute it is alive, plus `create`/`destroy` markers. Read it two ways:

- **Release build (real survival test):** Settings → Testing → *Service
  heartbeat*. Shows last-confirmed-alive, beats logged, longest gap.
- **Debug build (lab):** `adb shell run-as com.virgil.app cat files/heartbeat.log`.

A **gap** longer than the beat interval = the service was not beating in that
window. Disambiguate:

- gap **followed by a `create` line** → the service was *killed* and
  `START_STICKY` restarted it. This is the OEM-kill we're hunting.
- gap **with no `create`** → the process stayed alive but stopped receiving
  sensor data (deep Doze with a non-wake-up sensor). Still means "not
  monitoring" for that window — also worth knowing.
- elapsed-realtime **resetting to zero** → a reboot, not a kill. `BootReceiver`
  should bring the service back; confirm a `create` appears after.

## Tier 1 — stock Doze baseline (any device, today)

If Virgil dies under plain AOSP Doze, no OEM is to blame. Prove the baseline on
whatever device (or emulator) you have, with a **debug build installed**:

```
scripts/doze-survival-test.sh                 # 12 compressed Doze cycles
scripts/doze-survival-test.sh --cycles 48     # longer soak
```

It forces repeated idle/maintenance cycles, then reads the heartbeat log and
prints PASS/FAIL with any gaps. Clears the AOSP floor before you chase MIUI.

## Tier 2 — know your enemy (no device)

[dontkillmyapp.com](https://dontkillmyapp.com) documents exactly what each OEM
(MIUI, One UI, OxygenOS, …) does to background services and ranks them by
aggressiveness. Use it to decide which mitigations to ship — see the in-app
**Reliability** section (`ReliabilitySection`), which already surfaces the
battery-optimisation exemption and, on known-aggressive OEMs, an auto-start
shortcut.

## Tier 3 — free remote real devices

- **Samsung Remote Test Lab** — free, real One UI hardware (session-limited to
  hours, not 24h, but real OEM behaviour).
- **Firebase Test Lab** — free tier, real devices incl. some Xiaomi, ~45-min
  session cap. A short-survival smoke test, not a soak.

## Tier 4 — the real fleet (the readout exists for this)

Virgil's users own the phones we can't test. The in-app readout was built so a
non-technical tester can screenshot "longest gap: 3h 12m". Recruit a handful of
Xiaomi/Samsung owners, have them run 24h on **default** battery settings (not
"unrestricted" — that's not what a real user gets), and report the readout.
This is the only source of true default-battery real-world data.

## What this does NOT cover

- `CheckInService` is alarm-driven, not sensor-driven — it has no opportunistic
  beat yet. Its survival needs a separate hook.
- Real **SMS delivery** (trigger a panic, confirm the GPS text lands on a second
  phone) is a separate end-to-end check.
