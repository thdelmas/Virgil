# Virgil — Working Notes for AI Assistants

Read this before touching code. It tells you the stack, the rules, and what not to do.

## Source of truth

- **Product vision, features, principles, architecture:** [MANIFESTO.md](MANIFESTO.md). If a code change conflicts with the manifesto, the manifesto wins — surface the conflict, don't silently deviate.
- **Regulatory / Play Store / distribution guardrails:** [docs/COMPLIANCE.md](docs/COMPLIANCE.md). Enforced by `make compliance` (also part of `make quality` and `make quality-fast`). Violations block pre-commit. Read it before touching user-facing strings, permissions, the manifest, or dependencies. Especially: §1 (never medical framing), §11 (never imply Virgil dials emergency services — it texts and calls the user's emergency contacts, full stop).

## Stack

- **Platform:** Android, `minSdk=29`, `targetSdk=35`, `compileSdk=35`, JVM target 17.
- **Language:** Kotlin with Jetpack Compose (Material 3). See [android/app/build.gradle.kts](android/app/build.gradle.kts) for exact deps and versions.
- **Architecture:** Single app module. App package `com.virgil.app`, entry `.ui.MainActivity`. Sensor logic runs in foreground services of type `specialUse` with a personal-safety subtype (see [AndroidManifest.xml](android/app/src/main/AndroidManifest.xml)). Do not revert to `health` — see [docs/COMPLIANCE.md §4](docs/COMPLIANCE.md).
- **Deps already in place** — prefer these, don't introduce alternatives: AndroidX core-ktx, lifecycle-runtime-ktx, activity-compose, Compose BOM, Navigation Compose, DataStore Preferences, Google Play Services Location. Tests: JUnit 4 + kotlin-test-junit.

## Source layout

- Kotlin: `android/app/src/main/java/com/virgil/app/…` (package mirrors directory)
- Unit tests: `android/app/src/test/java/com/virgil/app/…`
- Resources: `android/app/src/main/res/`
- Manifest: `android/app/src/main/AndroidManifest.xml`

## Conventions

- **Files ≤ 500 lines.** Enforced by [scripts/check-file-length.sh](scripts/check-file-length.sh). Split before asking for new features, not after.
- **One logical change per commit.** Small, reviewable edits beat one sprawling diff.
- **Names match Compose / AndroidX idioms.** E.g. `FallDetectionService`, `EmergencyCountdownActivity` — follow the Manifest's class names (don't rename without need).
- **No comments that restate the code.** Comments explain *why*, never *what*. Default to no comment.
- **Kotlin style:** idiomatic Kotlin — `val` over `var`, scope functions where natural, no Java-style getters/setters.

## Non-negotiable constraints (from the manifesto)

1. **On-device only.** No backend, no cloud, no analytics, no accounts, no network calls for core fall-detection or check-in logic. Location leaves the device *only* via SMS to the user's contacts, only on alert.
2. **No new dependencies without explicit approval.** Ask first — especially for anything that adds network or telemetry.
3. **Battery is a feature.** Use sensor batching, OS-provided activity signals, and minimal wake-ups. Never continuous polling. Never a wakelock without a matching release.
4. **Permissions are already declared** in the manifest. Don't add new ones casually — each one is a UX cost and a trust cost.
5. **Free and open source.** No paid SDKs, no ads SDKs, no telemetry SDKs. Ever.

## Before you write code

- [ ] Read [MANIFESTO.md](MANIFESTO.md) if you haven't in this session.
- [ ] Know the one file you're changing and its siblings. Don't refactor unrelated code in the same pass.
- [ ] Check for an existing pattern in the repo before inventing one.
- [ ] If tests exist for the area, read them — they encode the contract.

## Before you commit

- [ ] `make quality-fast` passes (file length + Kotlin compile).
- [ ] For behavior changes, add or update a unit test under `android/app/src/test/…`.
- [ ] No secrets, tokens, or PII in code, logs, or commit messages.
- [ ] No `--no-verify`. If the hook fails, fix the cause.
- [ ] Commit message is one imperative sentence about *why*, not *what*.

## Commands

- `make hooks-install` — one-time per clone; points git at `.githooks/`.
- `make quality-fast` — file length + compile. Runs on pre-commit.
- `make quality` — file length + lint + unit tests. Runs in CI.
- `make assemble` / `make install` / `make run` — build, install, launch.
- `make logs` — tail app logcat.
- `make help` — everything else.

## Hard "don't" list

- Don't add a cloud SDK, analytics SDK, or crash-reporter SDK.
- Don't add a new permission without asking.
- Don't introduce a new dependency without asking.
- Don't bypass pre-commit (`--no-verify`) as a habit.
- Don't refactor code you aren't touching.
- Don't invent APIs — verify against the in-repo code or the pinned dep versions.
- Don't write speculative "for future use" code. Delete it when YAGNI.
- Don't hand-roll what AndroidX / Compose already provides.

## When you're stuck

- Re-read the relevant section of `MANIFESTO.md`. Most design questions are already answered there.
- If logs would help, add verbose logging, run, and reason from the logs — don't guess.
- If a prompt isn't landing, state the failing check output and the exact file/line, not "it doesn't work."
