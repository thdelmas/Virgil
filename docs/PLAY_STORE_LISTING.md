# Virgil — Play Store Listing Copy

Source of truth for the strings paste into Google Play Console. Compliance-checked against [COMPLIANCE.md](COMPLIANCE.md) §1 (no medical framing) and §11 (capability honesty).

---

## App title (max 30 chars)

`Virgil — Silent Guardian`

(23 chars)

## Short description (max 80 chars)

`Detects falls. Notices silence. Hold for help. Texts the contacts you chose.`

(76 chars)

## Full description (max 4000 chars)

```
Virgil is a free personal safety app for people who live alone, hike alone, or simply want a quiet safety net.

It does three things — three ways an alert can fire, each tuned to a different situation:

• Fall detection. The accelerometer notices a hard fall — free-fall, impact, then stillness. A 60-second countdown appears with vibration and a full-screen alert. If you don't hold "I'm OK," Virgil texts the emergency contacts you chose with your GPS location.

• Check-in. You set an interval — say, every 6 hours during the day. If your phone sees no sign of life (no screen unlock, no movement), it asks if you're OK. If you don't respond, the same SMS goes out.

• Manual alarm. When you know something's wrong, hold the red button on the home screen for 1.5 seconds. A loud siren starts immediately (anti-tamper, deterrent), and the same SMS goes out — with a clear "this is not a fall" framing so the people you alerted understand the situation.

That's it. No streaks. No counters. No "incidents detected" badges.

WHO IT IS FOR
• An elderly parent or grandparent living alone
• Anyone at home by themselves who wants a simple safety net
• Hikers and lone workers in remote areas
• Anyone in a moment that suddenly feels unsafe — a stranger getting too close, a phone-theft attempt
• Anyone who wants peace of mind without paying a monthly fee

WHAT MAKES VIRGIL DIFFERENT

Your data stays on your phone. Virgil has no servers, no accounts, no analytics, no advertising, and no INTERNET permission. Your location is read only when an alert fires, and it leaves your phone exactly once: as an SMS, from your SIM, to the contacts you chose. You can verify this — the source code is public.

Free and open source. No subscription. No "premium tier." No ads. Ever.

Battery-conscious. Virgil uses sensor batching and minimal wake-ups. The check-in feature relies on activity signals the operating system already tracks; it does not poll sensors continuously.

Honest framing. Virgil is a phone app that notices when something might be wrong and tells the people you trust. It is not a medical device. It does not dial 911, 112, or any emergency dispatch service — it texts the contacts you added. If you grant the optional phone-call permission, fall and check-in alerts also place a follow-up call to your primary contact through the system dialer. The manual alarm does not call — the siren would drown the line. Virgil may miss events or trigger false alerts. Stay as careful as you would without it.

WHAT VIRGIL DOES NOT DO
• It does not call emergency services.
• It does not monitor your health, heart rate, or any vital signs.
• It does not store your data on any server.
• It does not share anything with anyone except the SMS contacts you chose.
• It does not require an account or a sign-in.
• It does not work without contacts you have added — it has nobody to alert.

PERMISSIONS, EXPLAINED
• SMS — to text your emergency contacts when an alert fires. Required.
• Phone — optional. If granted, fall and check-in alerts also call your primary contact via the system dialer. The manual alarm never calls.
• Call answering — optional, off by default. If you turn it on, then only during an active alert and only when one of your saved emergency contacts is the caller, Virgil silences the ring and picks up hands-free so you can speak without fighting the siren. Every other call rings normally — Virgil never blocks, screens, or reroutes your ordinary calls.
• Location — to attach your GPS coordinates to the emergency SMS. Read only at the moment of the alert.
• Foreground service and notifications — required by Android to keep the safety services running with a persistent notification you can see at all times.
• Sensors — to detect falls.

The app does not request INTERNET, BACKGROUND_LOCATION, READ_CONTACTS, BODY_SENSORS, ACTIVITY_RECOGNITION, BLUETOOTH, CAMERA, or microphone access.

LANGUAGES
English, French, Spanish, Portuguese, Catalan, Basque.

OPEN SOURCE
The code is public. Anyone can read it, audit it, build it, or fork it. Safety should not be a black box.

THE NAME
In Dante's Inferno, Virgil is the guide who walks beside the traveler through darkness. He doesn't fix anything. He doesn't judge. He's simply there — so you're not alone. That is what this app does. It walks beside you. It watches. And when something goes wrong, it speaks up — so the people who care about you can reach you.

Your silent guardian.

---

DISCLAIMER: Virgil is a personal safety tool, not a medical device. It is not a substitute for emergency services or professional medical alert systems. False alerts and missed events are possible.
```

(approx. 3,400 chars — within 4000 limit)

---

## Category & tags

- **Category:** Tools (preferred) or Lifestyle. **Do not** pick Health & Fitness or Medical — see [COMPLIANCE.md §1](COMPLIANCE.md).
- **Tags:** personal safety, lone worker, hiking, family.

## Content rating

- Target audience: 18+. The app references emergency situations. Not directed at children.

## Data safety form (Play Console)

The Data Safety form must match [PRIVACY.md](PRIVACY.md). Answers:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No.** |
| Is all of the user data collected by your app encrypted in transit? | N/A (no data leaves the device through the app). |
| Do you provide a way for users to request that their data be deleted? | Yes — uninstall the app or clear app storage. There is no remote copy. |

For the per-data-type table: select **none** of the data types as "collected" or "shared." The location, contacts, and phone identifiers used by the app are **processed on the device only** and not transmitted to a server. This matches the Data Safety definition of "not collected."

> Important nuance: Google's "shared" definition excludes data the user themselves transmits via SMS to a recipient they chose. The emergency SMS falls under this exclusion. If the form requires a note, use: *"Location and contact phone numbers are used only to send an SMS from the user's own SIM to emergency contacts the user added. No data is sent to Virgil or to any third-party server."*

## Required app access permissions declarations

Paste these into the matching Play Console declaration forms. Each is reviewed independently; the most likely rejection points are **SMS** and **call answering** — get those wording-exact.

- **SMS access (`SEND_SMS`) — Permissions Declaration Form.** Virgil is **not** a default SMS handler, so this needs an approved exception use case. Select the closest core-functionality / personal-safety exception, and declare:
  > *"Virgil is an on-device personal-safety app. When a fall is detected, a scheduled check-in is missed, or the user holds the manual-alarm button, Virgil sends a single SMS — from the user's own SIM — to the emergency contacts the user has explicitly added, containing the user's GPS location and the trigger reason. SMS is the core delivery mechanism of the app's only feature: reaching the user's chosen contacts when the user may be unable to. The app declares no INTERNET permission and has no server; SMS is the sole outbound channel. The app does not read the SMS inbox or SMS log."*
  >
  > If the exception is rejected, the fallback is to drop `SEND_SMS` and launch the user's SMS app via an `ACTION_SENDTO` intent with a pre-filled message — degraded UX (the user must press send) but policy-safe.

- **Phone call (`CALL_PHONE`) — optional.** *"If the user grants this optional permission, Virgil places a follow-up call to the primary emergency contact via the system dialer for fall and missed-check-in alerts only (never for the manual alarm — its loud siren would render the call useless). Virgil never calls emergency services. The app does not read the call log."*

- **Call answering (`ANSWER_PHONE_CALLS` + Call Screening role) — highest-scrutiny declaration.** *"Virgil registers an optional, off-by-default CallScreeningService. Only while an alert is armed AND the incoming caller's number matches an emergency contact the user saved, Virgil silences the ringtone and accepts the call hands-free, so a user who may be injured or fighting the siren can speak to their contact without touching the phone. The window auto-disarms after one answered call. Every call that is not from a saved emergency contact during an active alert passes through completely untouched — Virgil never inspects, blocks, or reroutes ordinary calls. Virgil does not read the call log and holds no Call Log permissions. This feature stays disabled until the user explicitly grants the Call Screening role in settings."*

- **Foreground service (`specialUse`) — FGS declaration.** The manifest declares four `specialUse` services; declare each subtype and why no standard FGS type fits:
  > *"All four services run on-device for personal safety with no network use. We use `specialUse` because no standard foreground-service type matches: the work is sensor- and timer-driven safety monitoring, not media, data sync, location-sharing, or a phone call. (1) `on_device_fall_detection…` — continuous accelerometer monitoring; falls occur unpredictably and cannot be deferred. (2) `on_device_inactivity_check_in…` — exact-time inactivity check-in. (3) `on_device_emergency_siren…` — drives the alert siren. (4) `on_device_staged_emergency_countdown…` — the user-cancellable countdown before an alert fires. None use the network or leave the device."*

- **Full-screen intent (`USE_FULL_SCREEN_INTENT`) — Android 14+ declaration.** *"Used only to present the emergency countdown over the lock screen (EmergencyCountdownActivity) so the user can press 'I'm OK' to cancel a false alarm without unlocking. This is alarm-equivalent, time-critical safety UI — not advertising or engagement."*

- **Exact alarm (`SCHEDULE_EXACT_ALARM`).** *"Schedules the inactivity check-in prompt at the exact user-chosen time. Inexact alarms would let the safety check drift by minutes-to-hours, defeating the feature."*

## Screenshots checklist (3-4 phone screenshots, plus feature graphic)

1. Home screen — service running, contact list, simple toggle.
2. Add-contact flow — clean, single-step.
3. Emergency countdown — full-screen, "I'm OK" button visible.
4. Settings — check-in interval, sleep hours.

Style: large readable text, plain backgrounds, no fake demographic stock photos, no medical iconography.

## Feature graphic

1024×500 PNG. Use the flame/V monogram on a flat background with the tagline *"Your silent guardian."* No medical symbology, no red cross, no heart-rate motifs.

## App icon

512×512 PNG. Same flame/V monogram. Already in the repo at `android/app/src/main/res/mipmap-*` — verify it renders well at small sizes before upload.

---

## Review notes (for the Play Console "Notes for review team" field)

```
Virgil is a personal safety app — fall detection and inactivity check-in. It runs entirely on-device. There is no server, no account, no analytics, and no INTERNET permission declared.

Permission usage:
• SEND_SMS (required): when an alert fires (fall, missed check-in, or manual alarm), the app sends an SMS from the user's own SIM to the emergency contacts the user added. Sole outbound channel; no INTERNET permission is declared.
• CALL_PHONE (optional): if granted, the app additionally places a follow-up phone call to the primary contact via the system dialer for fall and missed-check-in alerts. The manual alarm intentionally does not call (the alarm's loud siren would render the call useless). The app does not call emergency services under any condition.
• ANSWER_PHONE_CALLS + Call Screening role (optional, off by default): while an alert is armed, if a saved emergency contact calls back, Virgil silences the ring and answers hands-free so the user can speak without fighting the siren. All other calls pass through untouched; Virgil never blocks or reroutes ordinary calls and holds no Call Log permission.
• ACCESS_FINE_LOCATION: GPS coordinates are read at the moment of the alert and attached to the SMS. No background location use; ACCESS_BACKGROUND_LOCATION is not declared.
• Foreground service type "specialUse" (four services): on-device fall detection, inactivity check-in, emergency siren, and the staged pre-alert countdown. No standard FGS type fits sensor/timer-driven safety monitoring; none use the network.
• USE_FULL_SCREEN_INTENT: shows the cancellable emergency countdown over the lock screen so the user can dismiss a false alarm.
• SCHEDULE_EXACT_ALARM: fires the inactivity check-in at the exact user-chosen time.

Privacy policy: <link to hosted PRIVACY.md>
Source code: <link to public repository>

Test instructions: install, accept the consent screen, add yourself as an emergency contact, then in Settings tap "Test alert" to confirm the SMS path. To test fall detection without an actual fall, use the developer-mode "simulate fall" entry under Settings > Diagnostics.
```

---

## Hosting the privacy policy

Play Console requires a public URL. Options:

1. Push the repo to GitHub and link the raw rendered file:
   `https://github.com/<user>/<repo>/blob/main/docs/PRIVACY.md`
2. Or publish the `web/` Vite landing page to Netlify (already scaffolded — see commit `459be42`) and serve `/privacy` from there.

Option 2 is preferable: the URL stays stable across repo renames and looks more professional in the listing. Netlify deploy of the existing `web/` scaffold gets a public URL in ~5 minutes.
