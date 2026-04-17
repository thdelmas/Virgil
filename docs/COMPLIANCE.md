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

`CALL_PHONE`, `SEND_SMS`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `VIBRATE`, `USE_FULL_SCREEN_INTENT`, `HIGH_SAMPLING_RATE_SENSORS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`.

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
