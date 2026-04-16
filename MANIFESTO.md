# Virgil

## Your silent guardian.

In Dante's Inferno, Virgil is the guide who walks beside you through darkness. He doesn't fix anything. He doesn't judge. He's simply there — so you're not alone.

Millions of people live alone. Many are elderly. Some are fragile. When they fall and can't get up, or when they simply stop moving, nobody knows. Hours pass. Sometimes days.

The phone in their pocket has everything needed to change this. An accelerometer that detects falls. A clock that can notice silence. A radio that can call for help. Nobody connected those dots into something simple and free.

Virgil does.

---

## What It Does

**Fall detection.** Your phone detects a hard fall — free-fall, impact, then stillness. A countdown appears. If you don't tap "I'm OK" within 30 seconds, Virgil sends your GPS location to your emergency contacts and calls the first one.

**Dead man's switch.** You set a check-in interval — say, every 6 hours during the day. If your phone sees no sign of life (no screen unlock, no movement, no tap), it asks if you're OK. If you don't respond, same thing: alert your contacts with your location.

That's it. Two features. Both save lives.

---

## What It Is Not

Virgil is not a medical device. It does not diagnose anything. It does not monitor health. It does not talk to doctors, hospitals, or insurance companies.

Virgil is a phone app that notices when something might be wrong and tells the people you trust.

---

## Principles

### 1. Simple enough for anyone

If you can install an app and add a phone number, you can use Virgil. No accounts. No configuration screens with 40 options. No tutorials. Your grandparent should be able to set it up with one phone call from you.

### 2. Your data stays on your phone

No cloud. No servers. No analytics. No accounts. Virgil runs entirely on your device. Your location is only shared when an alert fires — and only with the contacts you chose.

### 3. Free and open source

Safety should not be a subscription. Virgil is free, with no ads, no premium tier, no "upgrade to unlock" gating. The code is open so anyone can verify what it does.

### 4. Works everywhere

Virgil works in any country, in any language, on any Android phone with an accelerometer — which is virtually all of them. No special hardware. No SIM restrictions. No regional limitations.

### 5. Battery-conscious

A guardian that kills your phone battery is no guardian at all. Virgil uses efficient sensor batching and minimal wake-ups. The dead man's switch checks activity signals the OS already tracks — it doesn't poll sensors continuously.

---

## How It Works

### Fall Detection

```
Accelerometer (always-on, low-power)
  |
  v
Phase 1: Free-fall detected (acceleration drops below 0.5g)
  |
  v
Phase 2: Impact detected (acceleration spikes above 3g within 500ms)
  |
  v
Phase 3: Stillness confirmed (near 1g for 2+ seconds — person is lying down)
  |
  v
Emergency countdown (30 seconds, full screen, vibration)
  |
  "I'm OK" tapped? --> cancel
  No response?     --> SMS with GPS location to all contacts + call primary contact
```

### Dead Man's Switch

```
Check-in timer (user-configured interval, e.g. every 6 hours)
  |
  v
Has the user interacted with the phone since last check?
(screen unlock, movement, app interaction)
  |
  Yes --> reset timer, do nothing
  No  --> "Are you there?" notification + vibration (5-minute window)
         |
         Response?   --> reset timer
         No response --> same emergency flow as fall detection
  |
  Sleep hours (e.g. 23h-7h) --> timer paused automatically
```

---

## Tech

- **Android** (Kotlin, Jetpack Compose)
- **On-device only** — no backend, no cloud, no network dependency for core function
- **SMS + phone call** for alerts — works without internet
- **GPS location** attached to emergency messages
- **Foreground service** with persistent notification (required by Android for always-on sensors)

---

## Who This Is For

- An elderly parent living alone
- Anyone recovering from surgery or illness at home
- A person with a condition that causes fainting or seizures
- Someone hiking or working alone in a remote area
- Anyone who wants a safety net that doesn't cost money or require hardware

---

## The Name

In the Divine Comedy, Virgil guides Dante through the darkness — not by carrying him, but by being present. He walks beside him. He watches. When something goes wrong, he speaks up.

That's what this app does. It walks beside you. It watches. And when something goes wrong, it speaks up — so the people who care about you can reach you.

Your silent guardian.
