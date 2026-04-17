## What & why

<!-- One or two sentences. What changes, and why now. Link any relevant issue. -->

## Manifesto alignment

<!-- Tick the ones that apply; explain any "no". See MANIFESTO.md. -->

- [ ] Stays **on-device only** (no cloud, no analytics, no new network calls in core paths).
- [ ] Adds **no new permission** in `AndroidManifest.xml` (or explains why it's necessary).
- [ ] Adds **no new dependency** (or the new dep is named, justified, and OSS-compatible).
- [ ] **Battery-conscious** — no new continuous polling, wakelocks released, sensors batched.

## Author checklist

- [ ] `make quality-fast` passes locally.
- [ ] No file exceeds 500 lines.
- [ ] If behavior changed, a unit test under `android/app/src/test/…` covers it.
- [ ] No secrets, tokens, keys, or PII in code, logs, or commit messages.
- [ ] Imports and APIs verified — nothing invented or hallucinated.
- [ ] No unrelated refactors bundled in.
- [ ] Commit messages describe *why*, not *what*.

## Testing

<!-- How was this verified? Which devices (Pixel 4a / Pixel 9a / Samsung)?
     Manual repro steps for reviewer, if applicable. -->

## Notes for reviewer

<!-- Anything out of the ordinary, tradeoffs taken, follow-ups deferred. -->
