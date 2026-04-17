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
