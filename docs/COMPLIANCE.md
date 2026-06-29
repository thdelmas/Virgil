# Virgil — Compliance Guidelines

These rules keep Virgil out of three failure modes: **regulatory classification as a medical device**, **Play Store removal**, and **the "it's just another cloud app" trap** that erases our differentiation. They are enforced by [scripts/check-compliance.sh](../scripts/check-compliance.sh), which runs in `make quality` (CI) and `make compliance` (on demand).

If a check blocks you, the fix is to change the code or copy — **not** to weaken the check. If you genuinely need an exception, add an inline allow marker (see §7) and explain in the PR.

---

## 1. Non-medical framing (hardest rule, highest stakes)

**FDA and EU MDR classify software by *stated purpose*, not by what the software actually does.** If we describe Virgil in medical language anywhere — UI strings, store copy, manifesto, README, docs, comments — we invite regulatory scope. Apple, Google, and Samsung ship fall detection *without* medical classification by framing it as "personal safety." We do the same.

### Banned vocabulary (anywhere user-visible)

| Banned | Use instead |
|---|---|
| medical device / medical alert | personal safety app |
| diagnose, diagnosis | detect, notice |
| patient(s) | person, user |
| clinical, clinically | (delete — don't claim precision) |
| vital signs, heart rate, blood pressure | (don't reference — Virgil doesn't measure them) |
| disease, illness, condition | (don't name — describe scenarios, not diagnoses) |
| prescription, therapy, treatment, cure | (delete) |
| FDA approved, CE marked | (delete — we are not) |
| health monitoring | activity detection, safety check |
| epileptic, seizure | "fainting spells" → prefer "moments of reduced awareness" or don't specify |
| recovery from surgery / post-op | (drop — lifestyle framing instead) |

### Required framing

- **Who it's for:** "people who live alone," "hikers," "lone workers," "anyone who wants a safety net." Not diagnosis-based categories.
- **What it does:** "notices unusual stillness," "alerts contacts you chose," "detects a hard fall." Not "monitors you" or "watches your health."
- **What it isn't:** Every top-level user-facing doc (README, MANIFESTO, Play Store description) must contain the disclaimer: *"Virgil is not a medical device. Not a substitute for emergency services or professional medical alert systems."*

### SMS/alert message template (legal hygiene)

The outgoing emergency SMS must put the responsibility on the human recipient, not imply Virgil has assessed anything:

> *"Automated alert from Virgil: [name] may need help — no response to check-in / hard impact detected at [time]. Location: [link]. Please check on them or call local emergency services."*

Never: *"[name] has fallen"* or *"[name] is unconscious."* We don't know that — we only know the phone saw a pattern.

---

## 2. On-device only (the privacy moat)

Virgil's differentiation against Google Personal Safety, Life360, and every subscription app is that **your data never leaves the device** except the emergency SMS you configured. Every network call, analytics event, or cloud dependency erases the moat.

### Hard bans

- **No network libraries.** No OkHttp, Retrofit, Ktor client, Volley, Firebase (any module), Google Analytics, Crashlytics, Sentry, Bugsnag, Amplitude, Mixpanel, Datadog, New Relic.
- **No `INTERNET` permission.** The app should not even *be able* to make network calls. This is the strongest privacy proof we can give users.
- **No `java.net.*` HTTP APIs, no `HttpURLConnection`, no `URL.openStream`** in source.
- **No account systems.** No sign-in, no "pro tier," no server-side state, no auth tokens.
- **No Google Play Services** beyond `play-services-location` (FusedLocationProvider runs locally once granted).
- **No telemetry of any kind.** Not even "anonymous usage stats." Not even opt-in. A user auditing the APK should find zero network code.

### Allowed

- Reading from / writing to DataStore (local preferences).
- `SmsManager` (the user's SIM sends the message — we don't).
- `TelephonyManager` to place a call via `Intent.ACTION_CALL`.
- `FusedLocationProviderClient` for GPS (on-device).
- Sensor APIs.

---

## 3. Permissions hygiene

Every permission is a **UX cost** (another dialog to accept), a **trust cost** (more surface for "what does this app want?"), and a **Play Store review cost** (some trigger extra scrutiny).

### Currently declared — keep

`CALL_PHONE`, `SEND_SMS`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `VIBRATE`, `USE_FULL_SCREEN_INTENT`, `USE_BIOMETRIC`, `HIGH_SAMPLING_RATE_SENSORS`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`.

### Rationale for the high-scrutiny entries

Three of the above carry extra Play-review weight or a mandatory Play Console declaration. Each must stay justified here so the doc matches the manifest:

| Permission | Why it's declared | Play Console obligation |
|---|---|---|
| `FOREGROUND_SERVICE_SPECIAL_USE` | Required by the `specialUse` FGS type chosen in §4 (the manifest declares four `specialUse` services). | Mandatory `specialUse` declaration explaining why no standard FGS type fits — see [docs/PLAY_STORE_LISTING.md](PLAY_STORE_LISTING.md). |
| `USE_BIOMETRIC` | Gates panic-stop (`StopAuthGate`) so a bystander can't silence anti-tamper mode without the user's biometric. | None beyond normal listing. |
| `WAKE_LOCK` | Holds the CPU awake only for the duration of an active alert/siren so the alert isn't dropped when the screen is off. Every acquire has a matching release (manifesto §"battery is a feature"). | None beyond normal listing. |

> **No call-answering.** Virgil deliberately does **not** request `ANSWER_PHONE_CALLS` or register a `CallScreeningService`. Its only outbound actions are the alert SMS and the optional follow-up *outgoing* call (`CALL_PHONE`). Reaching the user's contacts is a one-way push — Virgil never answers, screens, or intercepts incoming calls. Do not reintroduce auto-answer without explicit project-owner approval.

### Banned (compliance check will fail)

| Permission | Why banned |
|---|---|
| `INTERNET` | Breaks the on-device-only guarantee (§2) |
| `ACCESS_NETWORK_STATE` | Implies network use |
| `READ_CONTACTS` | Use the system contact picker intent instead — no permission needed |
| `WRITE_CONTACTS` | Out of scope |
| `CAMERA`, `RECORD_AUDIO` | Out of scope, regulatory risk |
| `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` | DataStore is sufficient |
| `READ_PHONE_STATE`, `READ_CALL_LOG`, `PROCESS_OUTGOING_CALLS` | Extra Play Store scrutiny; not needed |
| `BLUETOOTH*` | Out of scope |
| `BODY_SENSORS`, `ACTIVITY_RECOGNITION` | Medical-device classification risk. Fall detection works from `Sensor.TYPE_ACCELEROMETER` alone. |
| `FOREGROUND_SERVICE_HEALTH` | See §4 |
| `ACCESS_BACKGROUND_LOCATION` | Heavy Play Store review. If ever needed, must go through a separate PR with justification. |

### Adding a new permission

Forbidden without explicit approval from the project owner in an issue. The PR that adds it must include:
1. The user-facing reason shown in the permission rationale dialog.
2. The Play Store declaration form text, if the permission requires one.
3. An updated §3 entry here.

---

## 4. Foreground service type must not be `health`

Android 14+ requires `foregroundServiceType` on every foreground service. The value we choose is a signal to Google Play reviewers about what the app claims to be.

**`health` is an own-goal.** It literally asks the reviewer to classify us as health-adjacent. Do not use it. Do not declare the matching `FOREGROUND_SERVICE_HEALTH` permission.

**Use `specialUse` instead** (Android 14+), with an explicit `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>` subtype describing the personal-safety purpose (e.g. `"on_device_fall_and_inactivity_detection"`). For older SDKs `dataSync` or `location` are acceptable fallbacks — whichever one actually matches what the service is doing on that API level.

The compliance check blocks `foregroundServiceType="health"` and the `FOREGROUND_SERVICE_HEALTH` permission.

---

## 5. Dependencies

Adding a new dependency requires approval (CLAUDE.md §"Non-negotiable constraints"). The compliance check additionally blocks dependencies that would break §2:

- Any artifact under `com.squareup.okhttp3`, `com.squareup.retrofit2`, `io.ktor:ktor-client*`, `com.android.volley`.
- Any `com.google.firebase:*` except none (there is no exception).
- Any analytics/crash SDK (Sentry, Bugsnag, Crashlytics, Amplitude, Mixpanel, Datadog, New Relic, Adjust, AppsFlyer, Branch, Segment).
- Any closed-source SDK. Paid SDKs specifically forbidden by manifesto.

The allowlist of `play-services-*` modules is **location only**. Adding `play-services-auth`, `play-services-ads`, etc. is a build failure.

---

## 6. Liability language

### Required in-app elements (enforced by convention, not script)

- **First-run consent screen** with the text: *"Virgil is a personal safety tool, not a medical device. It may miss events or trigger false alerts. It is not a substitute for emergency services. I understand and accept this."* — with a required checkbox before contact setup.
- **Outgoing SMS template** per §1 — never claims fact, always asks the human to verify.
- **Privacy policy file** at [docs/PRIVACY.md](PRIVACY.md) — required for Play Store listing. Must state: zero collection, zero transmission except user-triggered/alert SMS, zero retention beyond the device.

### Never do this

- Quote accuracy rates ("detects 95% of falls"). We have no clinical validation and making the claim *creates* the regulatory problem.
- Cite medical studies.
- Integrate with Google Health Connect, Samsung Health, Apple HealthKit.
- Partner with hospitals, insurance companies, pharma, or monitoring centers.
- Accept payment. The moment money changes hands the classification analysis gets harder.

---

## 7. Exceptions

If a compliance check blocks something that is actually fine (a legitimate use of a banned word in a disclaimer, for example), add an inline allow marker on the offending line:

```markdown
Virgil is not a medical device. <!-- compliance-allow: disclaimer text -->
```

```kotlin
// Fired when the user confirms consent — see MANIFESTO.md §"not a medical device" // compliance-allow: doc pointer
```

Rules for allow markers:
- Must include a reason after the colon. "compliance-allow:" alone is not accepted.
- One marker allows one line only — it doesn't disable the whole file.
- Each marker is reviewable in code review — don't add them routinely.

For permission / dependency / service-type violations there are **no allow markers**. Those rules are absolute.

---

## 8. Running the checks

```sh
make compliance        # run all compliance checks
make quality           # runs compliance + lint + unit tests (same as CI)
make quality-fast      # runs compliance + kotlin compile (same as pre-commit)
```

The script lives at [scripts/check-compliance.sh](../scripts/check-compliance.sh). Each failed check prints the offending file, line, and the rule that was broken. If you see a violation whose fix is non-obvious, re-read the section of this document cited in the error message.

---

## 9. When this document changes

Tightening the rules (adding bans) is fine at any time — expect to fix new violations in the same PR. Loosening the rules (removing bans, adding exceptions) requires explicit approval from the project owner and a note in the commit message explaining why the risk has changed.

---

## 10. Introduction SMS (non-emergency, one-shot)

When the user adds an emergency contact, Virgil offers to send that contact a **one-time, non-emergency SMS** as a courtesy heads-up. This is the only non-alert SMS the app ever sends.

### Contract

- **Opt-in per add.** A checkbox in the add-contact dialog governs it; unchecking skips the SMS. Default is checked, on the reasoning that recipients benefit from knowing they're listed before they ever receive an alert, and the message itself invites opt-out.
- **No location.** The message contains no coordinates, altitude, map link, or any other position data. It is purely an identity / expectation statement.
- **No medical framing.** Per §1: "safety app" and "emergency contact" only. Never "monitoring," "health," "medical alert."
- **No recurrence.** One SMS per add, ever. There is no follow-up, no reminder, no re-introduction.
- **Initiated only on explicit user action.** The SMS is fired from the user tapping "Add" with the box checked — never from a background service, timer, or boot receiver.

### Why this is still on-device-only (§2 compliant)

The introduction SMS is transmitted by the user's SIM via `SmsManager`, the same allowed path as the emergency SMS. Virgil makes no network call. `SmsManager` is explicitly allowed by §2 "Allowed" list.

### Why this is compatible with the manifesto

The manifesto principle "Location leaves the device only via SMS, only on alert" remains true — the introduction SMS carries **no location**. A broader "network calls for core logic" reading is preserved by "only on explicit user action" and "one per contact, ever."

---

## 11. Capability honesty

Virgil's mechanism is narrow and well-defined: it **sends an SMS to the user's emergency contacts**. It does **not** dial emergency services (911 / 112 / SAMU), does **not** summon an ambulance, and does **not** interact with any dispatch centre. User-facing copy must say this exactly. Ambiguous phrasing like *"Virgil sends for help"* leads the user to rely on the app for a capability it doesn't have — the worst kind of safety-app failure.

This rule complements §1 (medical framing) and MANIFESTO.md §6 (*Honest framing — no false confidence*). §1 keeps us out of regulatory scope; §11 keeps the user's mental model of what Virgil can actually do aligned with what it actually does.

### What is guaranteed vs. best-effort

| Action | When | Status |
|---|---|---|
| SMS to all configured contacts (with GPS, time, last activity) | every alert (fall, life signal, manual alarm) | **guaranteed** when SEND_SMS is granted |
| Call to the primary contact via system dialer | fall + life signal only | **best-effort** — only if CALL_PHONE is granted (optional permission); skipped on manual alarm by design (siren would drown the line) |
| Loud siren | every alert (and every countdown stage) | guaranteed |

Headline copy (README, Play Store short/full description, landing page hero, social posts) MUST describe Virgil's outgoing action as **"alerts/notifies/texts the emergency contacts you chose"**. It MUST NOT promise a phone call as the headline action — the call is optional, conditional on a permission grant, and absent for the manual alarm.

Deeper copy (in-app permission rationale, the privacy policy's per-permission table, the Play Store permission declarations) MAY describe the optional call, provided it is framed as conditional ("if you grant call permission, Virgil also calls your primary contact for fall and life-signal alerts") and never as guaranteed.

### Banned phrasing (anywhere user-visible)

| Banned | Use instead |
|---|---|
| "send for help" / "call for help" / "get help" | "text your emergency contacts" / "alert the people you chose" / "reach your emergency contacts" |
| "ask for help" / "cry for help" | "alert your emergency contacts" / "notify the people you chose" |
| "Virgil calls emergency services" / "dispatches help" | (never — Virgil doesn't) |
| "help is on the way" (after an alert) | "your emergency contacts have been notified" |
| Headline: "Virgil texts and calls your contacts" | Headline: "Virgil texts the contacts you chose" (move the call into the deeper, conditional copy) |

The compliance check enforces `\bfor help\b` as the canonical trigger. Narrative or motivational framing in internal docs (e.g. the MANIFESTO preface describing what a phone *could* do in the abstract) can carry the inline `compliance-allow: <reason>` marker; user-facing strings and the top-level README cannot.

### Allowed

- **First-person SMS templates** sent *by the user* to their contact (e.g. *"URGENT: I may have fallen and need help."*) — this is the user's own voice, not a description of Virgil's capability. The phrase "need help" is fine; "for help" is the banned structure.
- **Describing the user's state** ("if you need help") as distinct from Virgil's action ("contacts the people you chose"). The former is about them; the latter is about us.
- **The legal disclaimer** — "not a substitute for emergency services" — with an inline allow marker, since the phrase there is denying a capability rather than claiming one.
- **Conditional call mentions** in deeper copy, framed as best-effort and permission-dependent, and only for fall + life-signal triggers.

---

## 12. PanicKit interop (responder)

Virgil acts as a [PanicKit](https://github.com/guardianproject/PanicKit) responder so users can fire the manual panic flow from external trigger apps (Ripple, Haven, hardware-button or wear companions). This adds two exported components:

- An **activity** for `info.guardianproject.panic.action.CONNECT` and `…DISCONNECT` — the pairing endpoint.
- A **broadcast receiver** for `info.guardianproject.panic.action.TRIGGER` — the fire endpoint.

### Pairing model (required)

Virgil only honours a TRIGGER from an app the user has explicitly paired via CONNECT. The user sees a confirmation dialog with the calling app's display name before pairing is saved. An unpaired or anonymous TRIGGER is ignored.

### Limitation: sender identity on a BroadcastReceiver

Android does **not** convey the sender package to a manifest-declared `BroadcastReceiver`. The CONNECT activity uses `getCallingPackage()` and is precise on the way in; the receiver-side gate, by necessity, is coarser: "the user has paired *at least one* PanicKit app." Any app that knows the action and our package could spoof a TRIGGER, but only if pairing already exists at all — i.e. the user has knowingly opted into the responder pathway. The receiver's only effect is to start the same panic flow the user can already trigger from the home screen, so the failure mode of a spoofed trigger is a noisy false alarm, not a privacy or capability escalation.

### Destructive responder semantics — NOT implemented

PanicKit's optional "destructive responder" contract (wipe local data on TRIGGER) is **deliberately not implemented**. Virgil has nothing sensitive to wipe — contacts and preferences are exactly the data we need to *send the alert*. Silently destroying them would directly conflict with the app's purpose. The "Disconnect" action simply removes the sender from the paired-senders set; no further side effects.

### No new permission, no new dependency

Both endpoints use only platform IPC. PanicKit itself is **not** a Gradle dependency — wire constants are duplicated as `PanicResponder.ACTION_*` and pinned by a unit test that asserts the exact strings.
