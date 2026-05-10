<p align="center">
  <img src="docs/assets/logo.svg" width="140" alt="Virgil lantern logo"/>
</p>

# Virgil

> Your silent guardian.

A free, open-source Android app that watches over you when you're alone. Three triggers, one emergency contract.

---

## What it does

Three ways an alert can fire — each tuned to a different situation:

**Fall detection.** Your phone's accelerometer notices a hard fall — free-fall, impact, then stillness. A full-screen countdown appears on your screen, escalating over 60 seconds through four stages (calm ping → steady warning → urgent pulse). If you don't hold "I'm OK" for a few seconds, Virgil sends an SMS with your GPS location to the emergency contacts you chose.

**Check-in.** At an interval you set, Virgil quietly notices whether your phone has seen any sign of life — a screen unlock, a tap, a step. If not, it gently asks if you're OK. If you don't respond within five minutes, it triggers the same emergency SMS.

**Manual alarm.** When *you* know something's wrong — aggression, a theft attempt, a moment that suddenly feels unsafe — hold the red button on the home screen for 1.5 seconds. No countdown (you already decided). A loud siren starts immediately as a deterrent and a beacon for bystanders, and the same SMS goes out to your contacts.

If you grant the optional call permission, fall and check-in alerts also place a follow-up phone call to your primary contact through the system dialer. The manual alarm doesn't call — the siren would drown the line.

---

## Who it's for

- An elderly parent living alone
- Anyone spending long stretches at home by themselves
- Someone prone to moments of reduced awareness or balance trouble
- Someone hiking or working alone in a remote area
- Anyone who wants a safety net without extra hardware, a subscription, or a cloud service

---

## What makes Virgil different

- **Free forever.** No accounts, no subscription, no premium tier, no ads.
- **Your data stays on your phone.** No cloud, no servers, no analytics. Your location is only shared when an alert fires — and only with the contacts you chose.
- **No wearable needed.** Your existing Android phone is all the hardware required.
- **Works offline.** Alerts go out by SMS — no internet required.
- **Open source.** Anyone can read the code and verify what it does.

---

## Installing

Virgil is not yet on the Play Store (target: mid-2026). Until then, you can build it yourself:

```bash
git clone https://github.com/thdelmas/Virgil.git
cd Virgil
make install
```

You'll need an Android device with USB debugging enabled, running Android 10 or newer.

---

## Setting up

1. Open Virgil and grant the permissions it asks for (SMS, phone, location, notifications).
2. Add at least one emergency contact — ideally two, with one marked as primary (they'll be the first one called).
3. Flip on Fall Detection and/or Check-In on the home screen.

That's it. No accounts. No tutorials.

---

## How it works

### Fall detection

```
Accelerometer (always-on, low-power)
  → free-fall detected (acceleration drops below 0.5g)
  → impact detected within 500ms (acceleration spikes above 3g)
  → stillness confirmed (near 1g, 300 ms–5 s after impact)
  → 60-second staged countdown, full-screen, escalating audio + haptics
     → "I'm OK" held for 5s → cancel
     → no response         → SMS with GPS to all contacts (+ optional call to primary)
```

### Check-in

```
Timer fires at your chosen interval (default: every 6 hours)
  → has the phone seen activity since the last check?
    yes → reset, wait for next interval
    no  → "Are you there?" notification (5-minute grace period)
          → tap → reset
          → no response → same SMS flow as fall detection
  → sleep hours (default 23:00–07:00) → checks paused
```

Virgil also quietly learns your typical daily rhythm. If your phone goes unusually quiet compared to your normal activity for this time of day, Virgil can raise a check-in earlier than the fixed interval — while keeping the scheduled check as a safety backstop.

### Manual alarm

```
Hold the red button on the home screen for 1.5 seconds
  → loud siren starts immediately (anti-tamper, full volume)
  → SMS with GPS to all contacts ("this is not a fall")
  → no countdown (you already decided)
  → no auto-call (the siren would make the line useless)
```

The hold duration is the only false-trigger guard — pocket presses won't sustain it; deliberate presses will.

---

## Your data

Virgil runs entirely on your device. There is no server. There are no accounts. There is no telemetry. The only data that ever leaves your phone is the emergency SMS — text you chose, sent to contacts you chose, only when an alert fires.

---

## The name

In Dante's *Divine Comedy*, Virgil guides Dante through the dark — not by carrying him, but by walking beside him. He watches. When something goes wrong, he speaks up.

That's what this app does.

---

## For developers

Contributions are welcome. Start with:

- [MANIFESTO.md](MANIFESTO.md) — the non-negotiable principles
- [ROADMAP.md](ROADMAP.md) — what's shipping when
- [CLAUDE.md](CLAUDE.md) — architecture and compliance rules
- [docs/COMPLIANCE.md](docs/COMPLIANCE.md) — coding standards

Common commands:

```bash
make help        # list every command
make assemble    # build the APK
make install     # build + install on a connected device
make test        # run unit tests
make quality     # full CI-equivalent check
```

---

## License

[Apache License 2.0](LICENSE). Same license as Virgil's dependencies (AndroidX, Compose, DataStore), includes an explicit patent grant, and permits closed-source extensions — so the core stays free forever while leaving room for optional paid add-ons down the road.
