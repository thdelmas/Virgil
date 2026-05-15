# Virgil — Roadmap

**Start date:** 2026-04-16
**Play Store target:** 2026-06-01
**NLnet application target:** 2026-05-01

---

## Ground rules

1. **Virgil gets 2-3 days/week.** The rest goes to income track (DINUM, NLnet, BSC) and PreuJust distribution.
2. **Ship ugly, iterate fast.** The first Play Store version doesn't need to be beautiful. It needs to work.
3. **No feature creep.** Two features: fall detection + check-in. That's it until v1.0 is on the store with real users.
4. **Test on real devices every week.** Not the emulator. Your actual phones.
5. **Track one number:** active installs. Not GitHub stars, not downloads. Active installs.

---

## Phase 1 — Working app (Weeks 1-3)

**Goal:** App builds, installs, and both features work end-to-end on a real device.

### Week 1 — Apr 16-22: Build and basic testing

- [ ] Build succeeds (`make assemble`)
- [ ] Install on Pixel 9a (`make run-pixel9a`)
- [ ] Fall detection works: drop phone on cushion from ~1m, countdown appears
- [ ] Emergency contacts: add 2 contacts, verify they persist across app restart
- [ ] Emergency SMS: trigger a test fall, verify SMS arrives with GPS link
- [ ] Emergency call: verify primary contact gets called
- [ ] Fix any crashes or permission issues found during testing

**Done when:** You can demo the app to someone and it doesn't crash.

### Week 2 — Apr 23-29: Check-in end-to-end

- [ ] Set check-in interval to 1 minute for testing
- [ ] Verify: no interaction → notification appears after interval
- [ ] Verify: tap notification → timer resets, no emergency
- [ ] Verify: ignore notification → emergency countdown appears after 5 min grace
- [ ] Verify: sleep hours respected (no check-ins between configured hours)
- [ ] Verify: boot receiver restarts services after phone reboot
- [ ] InteractionTracker: verify screen unlock / app interaction resets the timer
- [ ] Battery test: run both services for 8 hours, measure battery impact

**Done when:** Both features work reliably for a full day without false alarms or missed events.

### Week 3 — Apr 30 - May 6: Bug fixes and edge cases

- [ ] Test with phone locked (fall countdown shows over lock screen)
- [ ] Test with Do Not Disturb on
- [ ] Test with battery saver on (alarms may be deferred — handle gracefully)
- [ ] Test with no SIM (SMS fails gracefully, call still attempted)
- [ ] Test with no GPS (message says "Location unavailable", doesn't crash)
- [ ] Test with 0 contacts configured (features stay disabled, no crash)
- [ ] Fix all crashes and edge cases found in weeks 1-2

**Done when:** You trust the app enough to install it on a family member's phone.

---

## Phase 2 — Store-ready (Weeks 4-6)

**Goal:** App is polished enough for public release on Play Store.

### Week 4 — May 7-13: i18n + onboarding

- [ ] Add French strings (`values-fr/strings.xml`)
- [ ] Add Spanish strings (`values-es/strings.xml`)
- [ ] Simple onboarding flow: first launch → explain what Virgil does (1 screen) → request permissions → add first contact
- [ ] Verify onboarding works for someone who has never seen the app
- [ ] App icon: simple, recognizable, not medical-looking (a small flame, a thread, or the letter V — keep it minimal)

**Done when:** A French-speaking non-technical person can install and set up the app without help.

### Week 5 — May 14-20: Play Store prep

- [ ] Privacy policy (simple, honest: "Virgil stores nothing off your device. Location is shared only during emergencies, only with your chosen contacts.")
- [ ] Play Store listing: title, short description, full description, screenshots (3-4), feature graphic
- [ ] Create Google Play developer account if you don't have one ($25 one-time fee)
- [ ] Upload to internal testing track
- [ ] Recruit 12+ testers for closed testing (Play Store requires 14 days of closed testing with 12+ testers before production release)
- [ ] Testers: family, friends, 42 alumni, anyone willing. They just need to install and use it for 2 weeks.

**Done when:** App is on internal testing track, 12+ testers have access.

### Week 6 — May 21-27: Closed testing period begins

- [ ] Closed testing live on Play Store
- [ ] Monitor crash reports daily (Play Console → Android Vitals)
- [ ] Fix any crashes reported by testers
- [ ] Collect feedback: what's confusing? What's missing? What breaks?
- [ ] Do NOT add features. Only fix bugs and clarify UX.

**Done when:** Closed testing is running, crash rate is under 1%.

---

## Phase 3 — Launch (Weeks 7-8)

### Week 7 — May 28 - Jun 3: Production release

- [ ] 14-day closed testing requirement met
- [ ] Submit for production review on Play Store
- [ ] Prepare launch post: LinkedIn (FR + EN), one paragraph, link to Play Store
- [ ] Write a short blog post or guide on theophile.world explaining why you built Virgil

**Done when:** Virgil is live on the Play Store. Anyone can install it.

### Week 8 — Jun 4-10: First distribution push

- [ ] Publish LinkedIn post (French first — your strongest network)
- [ ] Share in relevant communities: r/france, r/elderly (or equivalent), forums for caregivers, expat groups
- [ ] Email 5 people personally: "I built this free app, would you install it for your parent/grandparent?"
- [ ] Track: how many installs in week 1?

**Done when:** 20+ installs from real people (not friends testing).

---

## Phase 4 — Growth and funding (Weeks 9-16)

### Week 9-10 — Jun 11-24: NLnet application

- [ ] Write NLnet proposal for Virgil (NGI Zero Commons Fund)
- [ ] Frame: free, open-source, privacy-first safety app for isolated people. No cloud, no subscription, no data collection.
- [ ] Milestones to propose:
  - M1: Wearable integration (WearOS companion — tap "I'm OK" from wrist)
  - M2: Caregiver dashboard (simple web page showing last check-in time, shared via link)
  - M3: iOS port (or KMP shared logic)
  - M4: Accessibility audit + cognitive impairment UX testing
- [ ] Budget: €15k-€30k
- [ ] Submit

**Done when:** NLnet application submitted.

### Week 11-12 — Jun 25 - Jul 8: Iterate from real usage

- [ ] Review Play Store reviews and crash reports
- [ ] Interview 3 actual users (call them, ask what works and what doesn't)
- [ ] Fix top 3 pain points
- [ ] Release v1.1 with fixes

### Week 13-16 — Jul 9 - Aug 5: Sustained distribution

- [ ] One LinkedIn post per week about Virgil (user stories, technical decisions, why it's free)
- [ ] Reach out to 3 organizations that work with elderly/isolated people (associations, mairies, CCAS in France, social services)
- [ ] Pitch to one tech journalist (French or Spanish press)
- [ ] Target: 100+ active installs by Aug 5

---

## Success metrics

| Milestone | Target date | Metric |
|---|---|---|
| App works on real device | Apr 22 | Both features tested manually |
| Trusted enough for family | May 6 | Zero crashes in 24h test |
| Play Store closed testing | May 21 | 12+ testers, app uploaded |
| Play Store production | Jun 3 | Live on Play Store |
| First real users | Jun 10 | 20+ installs |
| NLnet application | Jun 24 | Submitted |
| Growth milestone | Aug 5 | 100+ active installs |

---

## What NOT to do

- **Don't add health monitoring.** Not step counting, not heart rate, not sleep tracking. Virgil is not a health app. The moment you add health features, you enter medical device regulation territory and Play Store health app policies. Stay simple.
- **Don't build a backend.** No cloud sync, no user accounts, no analytics dashboard. The app works offline. That's a feature, not a limitation.
- **Don't build an iOS version yet.** Android first. iOS comes after you have 100+ Android users and NLnet funding.
- **Don't redesign the UI.** The current UI is functional. Pretty comes later. Ship comes now.
- **Don't spend more than 30 minutes on the app icon.** Use a simple vector. Move on.

---

## The trap to watch for

You will want to add "just one more feature" before launching. A widget. A watch app. A caregiver view. Better animations. A nicer onboarding.

Every feature you add before launch is a feature that delays the moment a real person's life could be saved by this app.

Ship it. Then improve it.

---

## Phase 5 — Ecosystem integration (Post-launch, defer until 100+ installs)

Virgil sits in a small modular suite — Bios (sensor hub), W2F (mood),
SoulRadio (ambient radio). The full boundary lives in
[docs/ECOSYSTEM.md](docs/ECOSYSTEM.md). This phase is **post-launch only**:
nothing here blocks the Play Store target.

### Wired today (no keystore needed)

- ✅ **Media-playback presence signal** — `MusicActivityWatcher` records the
  not-playing → playing transition as a presence event, equivalent to a
  screen unlock. Any music app counts; no new permissions. See
  [service/MusicActivityWatcher.kt](android/app/src/main/java/com/virgil/app/service/MusicActivityWatcher.kt).

### Deferred (need keystore decision)

- **Fall → Bios outbound.** Three opt-in metric keys (`FALL_EVENT`,
  `NEAR_MISS_FALL`, `CHECK_IN_MISS`) written to Bios's companion-write URI
  after `EmergencyDispatcher.dispatch`. Highest-value single edge in the
  suite — fall frequency is a clinical signal for gait instability,
  syncope, orthostatic hypotension, neuropathy, MS relapse, medication
  side effects. Defaults **off** in Virgil settings. Requires shared
  keystore with Bios (signature-perm) or a runtime-grant alternative.
  Spec: [docs/ECOSYSTEM.md "What outbound integrations are admissible"](docs/ECOSYSTEM.md).
  - **Bios side: shipped.** The three `MetricType.SAFETY` keys, the
    URI whitelist for `com.virgil.app`, and four event-driven
    `ConditionPattern`s consuming them all landed in
    [Bios Phase 7.1 / 7.2 / 7.3](../Bios/docs/ROADMAP.md). The schema
    is ready; the open work is entirely Virgil-side (keystore decision,
    `BiosClient` writer mirroring the Smokeless pattern, settings
    opt-out, end-to-end verify on a real device with Bios installed).
  - **Verification step before Phase 5 closes:** one fall event,
    one near-miss, and one check-in miss each round-trip from Virgil
    dispatch → Bios provider → Bios diagnostics surface, with
    timestamp parity and `sourceId = com.virgil.app` provenance.
- **Exercise-context check-in suppression.** Read Bios's `STEPS` /
  `ACTIVE_MINUTES` / `HEART_RATE` to suppress check-in expiry during
  detected exercise. Marginal value (the stillness verify already handles
  most exercise false-positives) — defer indefinitely.

### Not planned

- Reading mood state to gate alerts (manifesto §6 violation — paternalism
  dressed as integration).
- Publishing panic-trigger events (the panic button is the user's
  expressive act, not a biometric).
- Cloud anything (manifesto §2 absolute).
