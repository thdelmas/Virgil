# Virgil — Scope

What belongs in Virgil, what doesn't, and why.

## In scope

Virgil is a single Android app for **personal safety events that the phone can sense or that the user can trigger**, where the response is the same: notify the user's emergency contacts with location.

Currently in scope:

- **Fall detection** — sensor-triggered, passive.
- **Check-in / no-response** — silence-triggered, passive.
- **Manual panic / aggression** — user-triggered, active. Includes anti-tamper on the panic flow only (see [feedback memory](../.claude/projects/-home-mia-Virgil/memory/feedback_anti_tamper_panic_only.md)).

All three share the same downstream pipeline: emergency contacts, SMS dispatch with GPS, primary-contact call, foreground-service plumbing, siren, countdown UI.

## Out of scope

- Medical monitoring of any kind ([COMPLIANCE.md §1](COMPLIANCE.md)).
- Anything that dials emergency services ([COMPLIANCE.md §11](COMPLIANCE.md)).
- Cloud, accounts, analytics, telemetry ([MANIFESTO.md](../MANIFESTO.md)).
- Features whose response path is not "text and call the user's contacts." If a new idea needs a different response, it does not belong here.

## Why one app, not several

The fall-detection user (elderly, solo activity) and the panic-button user (assault, mugging, stalking) have different mental models. The temptation is to ship two apps with cleaner positioning each.

We keep them in one app:

- **Shared infrastructure is ~80% of the code.** Contacts, SMS dispatch, location, siren, foreground service, manifest, compliance review. A solo FOSS project cannot afford to fork that.
- **Users want one safety app, not a portfolio.** Discovery, install, setup, and trust each cost something; doing them twice costs more than twice as much.
- **The manifesto is "personal safety," not "fall detection."** Virgil is the silent guardian. The trigger is an implementation detail; the promise — *your contacts hear about it* — is the product.

## How we manage the audience tension

Per-feature toggles in settings, with sensible defaults:

- Each feature (fall, check-in, panic) is independently on/off.
- First-run picks defaults from a single high-level question about primary use, then everything is editable.
- Listing copy and screenshots can foreground different features for different markets without forking the binary.

## When to revisit

Split into a second app only if **all** of the following are true:

1. A feature requires permissions, foreground-service types, or compliance posture that materially conflicts with the existing app's Play Store listing.
2. The shared-infrastructure ratio drops below ~50% (i.e. the new feature brings its own dispatch, contacts model, or response path).
3. Maintenance load on the combined app is demonstrably worse than two separate apps would be.

Until then: one app, toggles, shared pipeline.
