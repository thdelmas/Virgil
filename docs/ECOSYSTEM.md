# Ecosystem

Virgil is part of a small family of sibling Android apps — Bios (sensor hub),
W2F (mood/bipolar), SoulRadio (ambient radio) — that compose into a modular
bio-hacking suite. Virgil is the **outlier**: it does not need the family to
work, and the family does not need Virgil. This doc exists to keep that
boundary honest as the suite grows.

For the suite-wide rule, see Bios's
[`docs/ECOSYSTEM_BOUNDARIES.md`](../../Bios/docs/ECOSYSTEM_BOUNDARIES.md).

## Standalone first, always

Virgil's user is, by definition, someone who lives alone and may not own a
wearable, may not have installed Bios, and may not want any of it. The
feature set in [MANIFESTO.md](../MANIFESTO.md) — fall detection, check-in,
panic — must remain fully functional with **no sibling installed and no
inter-app permission granted**.

Anything we wire into the ecosystem is a *graceful enhancement*. The day the
sibling app is uninstalled, Virgil keeps working exactly as before.

## What the manifesto forbids — and what it does not

The MANIFESTO §2 rule is "no cloud, no servers, no analytics, no accounts."
Read it carefully: every clause is about **data leaving the device**.

On-device reads from a sibling app, via Android's standard IPC primitives
(ContentProvider with signature-level permission, sticky DataStore export,
intent broadcasts), are **not cloud**. They are local. The sibling and Virgil
share the same physical device, the same user, the same threat model.

That said, three rules survive intact:

1. **No outbound data to a sibling.** Virgil never tells anything else about
   the user. Not "an alert fired," not "the user is OK," not "GPS coords."
   The only off-device traffic Virgil generates is SMS and a phone call to
   the user's chosen contacts, dispatched directly from
   [`EmergencyDispatcher`](../android/app/src/main/java/com/virgil/app/service/EmergencyDispatcher.kt).
2. **No sibling becomes a dependency.** A sibling read is opt-in, surfaced
   in settings, and gracefully absent. If the sibling is missing or returns
   nothing, Virgil falls back to its standalone behavior.
3. **No expansion of the privacy surface.** A sibling read that requires a
   new runtime permission Virgil wouldn't otherwise need (e.g. body sensors,
   location for non-alert purposes) is forbidden. Virgil's permission list
   is the floor, not a starting point.

## What inbound integrations are admissible

Each row below is a *candidate*, not a commitment. None ships without
explicit user opt-in and a settings toggle.

| Source | Signal | Purpose in Virgil | Permission shape |
|---|---|---|---|
| Bios | `MetricType.STEPS`, `ACTIVE_MINUTES` (last 10 min) | Suppress check-in expiry during active exercise — don't fire on a user who's just out for a walk | Bios ContentProvider read, signature-perm |
| Bios | `MetricType.HEART_RATE` (live, last 60s) | Same as above — high HR during a fall verify window is consistent with exertion, not collapse | Bios ContentProvider read, signature-perm |
| Any media app (incl. SoulRadio) | `AudioManager.AudioPlaybackCallback` start-of-playback transition | "User tapped play" = presence signal — record on `InteractionTracker` like a screen unlock. Sustained playback does *not* keep refreshing the timer; only the discrete start event counts. | None new; `AudioPlaybackCallback` (API 26+) needs no runtime permission |
| W2F | `mood_drift_score` = SOS (anhedonia/hypomania) | *None.* Virgil does not act on mood state. See "What is forbidden" below. | n/a |

The first integration **wired** is the media-playback presence signal,
via [`MusicActivityWatcher`](../android/app/src/main/java/com/virgil/app/service/MusicActivityWatcher.kt).
Registered alongside the existing `ACTION_USER_PRESENT` receiver in
[`CheckInService`](../android/app/src/main/java/com/virgil/app/service/CheckInService.kt),
it records a presence event whenever any media app transitions from
not-playing to playing. This is broader than SoulRadio specifically —
any music app counts — which is correct: the signal is "the user just
pressed play," not "SoulRadio is in the suite."

The "default off" gate in §"Adding an integration" applies to signals
that *persistently suppress* alerts. A discrete presence event that
decays naturally with time, equivalent to a screen unlock, follows the
same default-on convention as `ACTION_USER_PRESENT`.

## What outbound integrations are admissible

Virgil captures one class of signal nothing else in the suite can: discrete
fall and check-in events. These are clinically meaningful — recurrent falls
flag gait instability, syncope, orthostatic hypotension, neuropathy,
hypoglycemia, MS relapse, medication side effects, and alcohol. The Bios
condition-pattern engine and any future neurological companion (Fil) will
want them.

Admissible outbound, all gated by an explicit settings toggle defaulted
**off**:

| Event | Bios `MetricType` key (proposed) | When written |
|---|---|---|
| Verified fall, dispatch fired | `FALL_EVENT` | After `EmergencyDispatcher.dispatch` returns |
| Fall detected, user cancelled during countdown | `NEAR_MISS_FALL` | After "I'm OK" hold cancels the countdown |
| Check-in non-response (5-min grace expired) | `CHECK_IN_MISS` | After the same path that escalates to alarm |

Each write carries a timestamp and an opaque event-id (no SMS contents, no
location, no contact identifiers). Bios stores them in its time-series
like any other metric; companions correlate against HRV, sleep, activity
to triage cause. None of this leaves the device.

These keys do not yet exist in Bios's `MetricType` enum. Adding them is a
Bios-side change ([CONSUMER_API.md](../../Bios/docs/CONSUMER_API.md)
companion-write URI must also be extended to accept them). Until then,
the toggle in Virgil settings stays hidden.

## What outbound integrations are forbidden

- **No alert broadcasts beyond the metric write.** The SMS/call to the
  user's chosen humans remains the only external channel. Virgil does not
  emit a "SOS active" intent for siblings to react to. SoulRadio noticing
  the siren via audio focus is incidental, not architectural.
- **No GPS, SMS contents, or contact identity in the metric write.**
  Bios receives a fall event, not the alert's payload. Locating who was
  texted or where the user fell is not Bios's business.
- **No false-alarm telemetry.** The opt-in
  [`FalseAlarmSnapshot`](../android/app/src/main/java/com/virgil/app/data/FalseAlarmSnapshot.kt)
  flow stays local. The `NEAR_MISS_FALL` metric above is the only
  cancelled-countdown signal that crosses the boundary.
- **No reading the user's mood/health to *gate* an alert.** Virgil does not
  decide whether to text an emergency contact based on inferred bipolar
  state, depression, or anything else. The decision is "did the user
  acknowledge the countdown" — full stop. Layering mood into that path is
  paternalism dressed as integration.
- **No panic-trigger event on the bus.** The panic button is the user's
  expressive act, not a biometric. Even with opt-in, it is not published.

## The keystore decision

Bios's `BiosHealthProvider` uses **signature-level permission**. Reading from
it requires Virgil's APK to be signed by the same keystore as Bios. This is
a single, irreversible decision the suite has to make.

Until it is made:

- Virgil ships with its current keystore (whatever the Play Store release
  uses).
- The Bios-read code paths in this doc are *aspirational*, not implemented.
- No code under [`android/app/src/main/java/com/virgil/app/`](../android/app/src/main/java/com/virgil/app/)
  may import a Bios contract artifact.

When the decision is made (either "Virgil joins Bios's keystore" or "Bios
relaxes to a different permission model"), update this section with the
chosen path and remove the aspirational caveat.

## Adding an integration: the gate

Before wiring any sibling read, answer all four:

1. **Does Virgil still work with this sibling uninstalled?** If no — stop.
2. **Does this require a new runtime permission?** If yes — stop, unless
   the same permission is needed for an existing Virgil feature.
3. **Does this signal change *whether* an alert fires?** If yes —
   document explicitly in MANIFESTO §6 ("Honest framing") and surface as a
   user-visible setting with the default *off*.
4. **Is the sibling's output stable, versioned, and documented?** Bios
   metric keys, MediaSession state — fine. Ad-hoc broadcasts a sibling
   might rename in v0.x — not fine.

## Cross-references

- [MANIFESTO.md](../MANIFESTO.md) — what Virgil is for
- [DESIGN.md](../DESIGN.md) — how the code is organized
- [docs/SCOPE.md](SCOPE.md) — what does not belong in Virgil
- [Bios/docs/ECOSYSTEM_BOUNDARIES.md](../../Bios/docs/ECOSYSTEM_BOUNDARIES.md) — the suite-wide rule
