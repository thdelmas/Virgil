# Virgil — Privacy Policy

**Effective date:** 2026-05-04
**App:** Virgil (`com.virgil.app`)
**Publisher:** Théophile Delmas
**Contact:** theophile.delmas.leguery@gmail.com

This is the Play Store privacy policy for Virgil. It is short on purpose. Virgil is a personal safety app that runs entirely on your phone. <!-- compliance-allow: legal classification phrase, not a capability claim -->

## In one sentence

Virgil collects nothing, sends nothing to any server, and shares your information only by sending an SMS — from your own phone, over your own SIM — to the emergency contacts you chose, and only when you trigger an alert or confirm an introduction message.

## What Virgil stores on your device

The app stores the following locally, in private app storage that no other app can read:

- The names and phone numbers of the emergency contacts you added.
- Your preferences (check-in interval, sleep hours, fall-detection sensitivity, language).
- Whether you have accepted the first-run consent screen.

This data never leaves your device through Virgil. It is removed when you uninstall the app or clear its storage.

## What Virgil does *not* do

- No account, no sign-in, no profile, no user ID.
- No analytics, telemetry, crash reporting, or "anonymous usage stats."
- No advertising, no advertising IDs, no third-party SDKs of any kind.
- No cloud sync, no backup to a remote server, no Google Drive integration.
- No background upload of location, sensor data, contacts, or anything else.
- No `INTERNET` permission is declared in the app — Virgil cannot make network calls even if it wanted to. You can verify this in `AndroidManifest.xml` of the open-source repository.

## Information that *does* leave your phone — and how

Virgil sends data off your device in exactly two cases. Both are SMS messages, transmitted by your SIM via the standard Android `SmsManager`. Your mobile carrier handles the message the same way it handles any text you type yourself; Virgil does not route it through any Virgil-controlled server.

### 1. Emergency alert (automatic or user-triggered)

When a fall is detected, when a check-in is missed, or when you trigger the manual alarm, Virgil sends an SMS to **each emergency contact you added**. The message contains:

- A short statement that an alert was triggered (with the trigger type — fall, missed check-in, or manual alarm).
- The time of the alert.
- A Google Maps link with your GPS coordinates at the time of the alert.
- The time of your last detected activity on the phone.

If you have granted the optional `CALL_PHONE` permission, fall and missed-check-in alerts also place a follow-up phone call to your **primary** emergency contact through the system dialer. The manual alarm intentionally does not call — its loud siren is running at full volume and would make the line useless. Virgil does **not** dial 911, 112, SAMU, or any emergency dispatch number under any condition — only the contacts you chose. <!-- compliance-allow: capability disclaimer per docs/COMPLIANCE.md §11 -->

### 2. Introduction SMS (one-time, opt-in, no location)

When you add an emergency contact, Virgil offers to send that person a one-time courtesy SMS letting them know they are listed as your contact. This message contains **no location data**. It is sent only if you leave the box checked when adding the contact, and it is sent only once per contact. There is no follow-up.

## Permissions Virgil uses, and why

| Permission | Why |
|---|---|
| `SEND_SMS` | To send the emergency alert and (optional) introduction SMS to the contacts you chose. Required for any alert. |
| `CALL_PHONE` | Optional. If granted, fall and missed-check-in alerts also place a follow-up call to your primary contact via the system dialer. The manual alarm never calls. |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | To attach your current GPS coordinates to the emergency SMS. Location is read at the moment of the alert; it is not logged or transmitted at any other time. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Required by Android 14+ to run the always-on accelerometer and check-in timer with a persistent notification. |
| `HIGH_SAMPLING_RATE_SENSORS` | To read the accelerometer at the rate needed for fall detection. Sensor readings are processed on-device in real time and never stored or transmitted. |
| `POST_NOTIFICATIONS` | To show the persistent service notification, the check-in prompt, and the emergency countdown. |
| `VIBRATE`, `USE_FULL_SCREEN_INTENT` | To get your attention during the countdown. |
| `WAKE_LOCK` | To keep the phone awake during the countdown so it doesn't sleep through the alert. |
| `RECEIVE_BOOT_COMPLETED` | To restart the safety services after the phone reboots, so protection resumes without you opening the app. |
| `SCHEDULE_EXACT_ALARM` | To run the check-in timer at the exact time you configured. |
| `ANSWER_PHONE_CALLS` | To support the call-screening service that lets a known contact get through during a do-not-disturb / silenced state. |

`READ_CONTACTS` is **not** requested. When you add a contact from your address book, Virgil uses the system contact picker, which returns only the entry you select.

`ACCESS_BACKGROUND_LOCATION` is **not** requested. Location is only read in the foreground, at the moment an alert fires.

`INTERNET` is **not** requested.

## Children

Virgil is not directed to children. It does not knowingly collect any data from children, because it does not collect data from anyone.

## Sharing with third parties

Virgil does not share anything with any third party, because Virgil does not transmit anything to any party other than the SMS recipients you configured. Your mobile carrier transports the SMS the same way it transports any text message; that relationship is between you and your carrier.

## Your control

- **See your data:** open the app — every contact and setting Virgil has stored is visible there.
- **Delete your data:** remove a contact, or uninstall the app / clear its storage. There is no remote copy to delete because there is no remote copy.
- **Stop alerts:** disable fall detection or check-in from the home screen. Both can be turned off at any time.

## Open source

Virgil's source code is public. You can verify everything in this policy by reading the code at the project repository. If something in the code contradicts this policy, the code is the bug — please open an issue.

## Changes to this policy

If this policy changes, the new version will be published in the same `docs/PRIVACY.md` file in the repository, and a link to it will appear in the Play Store listing. Material changes that affect what leaves your device will be announced in the app's release notes.

## Disclaimer

Virgil is a personal safety tool, not a medical device. <!-- compliance-allow: legal disclaimer required by docs/COMPLIANCE.md §1 --> It may miss events or trigger false alerts. It is not a substitute for emergency services or professional medical alert systems. <!-- compliance-allow: disclaimer denying capability, per docs/COMPLIANCE.md §11 -->

## Contact

Questions about this policy: theophile.delmas.leguery@gmail.com
