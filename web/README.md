# Virgil — web

Landing page for [Virgil](../MANIFESTO.md), served at
`https://theophile.world/apps/virgil`.

Plain Vite + React. No tracking. No analytics. No backend.

## Local

```bash
cd web
npm install
npm run dev       # http://localhost:5173/apps/virgil/
npm run build     # → web/dist
npm run preview   # serve the built output
```

## Deploy (Netlify)

Standalone Netlify site. Point Netlify at this repo; [`netlify.toml`](../netlify.toml)
at the repo root pins the build:

- **base:** `web`
- **command:** `npm run build`
- **publish:** `web/dist`

Add `theophile.world` as the custom domain in the Netlify site settings.
Because the app is mounted at `/apps/virgil/` (via Vite's `base` in
[vite.config.js](vite.config.js)), the site root (`/`) intentionally 404s —
the app lives only under `/apps/virgil`.
