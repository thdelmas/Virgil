# Launch posts

A log of public posts announcing Virgil, kept for reference (tone, copy, what resonated). Personal identifiers omitted — see git history / source platforms for attribution.

---

## LinkedIn — launch post (FR)

- **Date:** ~2026-04-19 (≈ 3 weeks before 2026-05-10)
- **Platform:** LinkedIn
- **Language:** French
- **Link:** https://github.com/thdelmas/Virgil (project URL, as posted)

### Body

> J'ai fait une appli de détection de chute et signal de vie.
>
> Virgil fait deux trucs :
> 1) Détection de chute → compte à rebours de 30s → SMS avec position GPS + appel au contact d'urgence
> 2) Signal de vie → si aucune interaction avec le téléphone pendant X heures → "t'es là ?" → pas de réponse → même alerte
>
> Pour qui ?
> - un parent âgé
> - quelqu'un qui vit seul
> - qui randonne seul
> - qui travaille isolé
> - sujet aux malaises ou aux pertes d'équilibre.
>
> Tout se passe sur le téléphone.
> Pas de cloud, pas de compte, pas d'abo.
> Code ouvert, open source.
>
> J'ai fait ça parce que j'avais besoin de veiller sur moi et mes proches.
> J'avais besoin de quelque chose de plus efficace et complémentaire à ce que je peux faire
>
> Bientôt sur les stores android / apple, déjà dispo sur github: github.com/thdelmas/Virgil
>
> Si tu connais quelqu'un à qui ça peut servir, c'est gratuit.

### Stats (as of 2026-05-10)

| Metric      | Value |
|-------------|-------|
| Impressions | 773   |
| Reactions   | 23    |
| Comments    | 1     |
| Reposts     | 5     |
| Images      | 4+ (carousel) |

### Notes

- Positioning emphasizes on-device, no-cloud, no-account, no-subscription, FOSS.
- Target audience framed broadly: elderly, lone-living, solo hikers, isolated workers, people prone to faintness or balance loss.
- Avoids medical framing (per [docs/COMPLIANCE.md §1](../COMPLIANCE.md)).
- Phrasing used: "SMS + appel au contact d'urgence" — at the time of the post the call was framed as part of the headline. Subsequently, [docs/COMPLIANCE.md §11](../COMPLIANCE.md) was tightened: the call is best-effort (optional `CALL_PHONE` permission, skipped on the manual alarm) and must not appear in headline copy. Future posts should lead with "SMS aux contacts que tu as choisis" and treat the call as a deeper, conditional detail.

---

## LinkedIn — three-trigger update (DRAFT, FR)

Draft for the follow-up post announcing the new manual alarm. Not yet published.

### Body (draft)

> Virgil a maintenant un 3e déclencheur.
>
> Avant : Virgil veillait quand tu ne pouvais pas — détection de chute (capteurs) + signal de vie (timer).
> Maintenant : Virgil obéit instantanément quand toi, tu sais que ça part en vrille.
>
> 🟥 Bouton « J'ai besoin d'aide » sur l'écran d'accueil.
> Tu maintiens 1,5 seconde → sirène à plein volume immédiatement (anti-vol, anti-agression) + SMS avec ta position GPS aux contacts que tu as choisis.
> Pas de compte à rebours : si tu appuies, c'est que t'as déjà décidé.
> Pas d'appel automatique : la sirène hurle, la ligne serait inutile.
>
> Pour qui ce 3e mode change quelque chose :
> - une rue qui se vide trop vite
> - un inconnu qui s'approche trop
> - une tentative de vol de téléphone
> - un moment qui devient brutalement pas safe
>
> Les trois déclencheurs partagent le même contrat : tout reste sur le téléphone, rien dans le cloud, rien chez moi, pas de compte, pas d'abo. Le SMS part de ton SIM vers les contacts que tu as ajoutés. C'est tout.
>
> Open source, gratuit, bientôt sur les stores. Code : github.com/thdelmas/Virgil

### Body (draft, EN — for cross-posting if useful)

> Virgil has a third trigger now.
>
> Before: Virgil watched when you couldn't — fall detection (sensors) + life signal (timer).
> Now: Virgil obeys instantly when you already know something's wrong.
>
> 🟥 "I need help" button on the home screen.
> Hold for 1.5 seconds → loud siren immediately (anti-theft, anti-aggression) + SMS with your GPS to the contacts you chose.
> No countdown: if you pressed it, you already decided.
> No auto-call: the siren is screaming over the mic.
>
> Who this third trigger is for:
> - a street that empties too fast
> - a stranger getting too close
> - a phone-theft attempt
> - a moment that suddenly stops feeling safe
>
> All three triggers share the same contract: everything stays on the phone, nothing in the cloud, nothing on my side, no account, no subscription. The SMS goes from your SIM to the contacts you added. That's it.
>
> Open source, free, coming to the stores soon. Code: github.com/thdelmas/Virgil

### Notes for the draft

- Headline action is **"SMS aux contacts que tu as choisis"** / **"SMS to the contacts you chose"** — no "calls" in the headline (compliance §11).
- Use cases broaden the audience without pulling Virgil into medical framing (compliance §1).
- Echoes the original launch post's framing ("Tout se passe sur le téléphone. Pas de cloud, pas de compte, pas d'abo.") so the two posts read as a coherent arc.
- Carousel idea: 1) home screen with the red button, 2) hold-to-fire animation, 3) siren + SMS receipt mockup, 4) "no cloud / no account / no subscription" recap.
