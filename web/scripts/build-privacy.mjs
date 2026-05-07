// Render docs/PRIVACY.md → web/public/privacy.html at build time so the
// Play Store gets a stable URL backed by the markdown source of truth.
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { marked } from 'marked'

const here = dirname(fileURLToPath(import.meta.url))
const webRoot = resolve(here, '..')
const src = resolve(webRoot, '..', 'docs', 'PRIVACY.md')
const out = resolve(webRoot, 'public', 'privacy.html')

const md = readFileSync(src, 'utf8')
const body = marked.parse(md, { gfm: true })

const html = `<!doctype html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <meta name="theme-color" content="#0D1B2A" />
  <title>Virgil — Privacy Policy</title>
  <style>
    :root { color-scheme: light dark; }
    body { font-family: system-ui, -apple-system, "Segoe UI", sans-serif; max-width: 720px; margin: 2rem auto; padding: 0 1rem 4rem; line-height: 1.55; color: #1a1a1a; background: #fff; }
    h1, h2, h3 { line-height: 1.2; margin-top: 2rem; }
    h1 { border-bottom: 1px solid #e3e3e3; padding-bottom: 0.5rem; }
    h2 { font-size: 1.35rem; }
    h3 { font-size: 1.1rem; }
    p, li { font-size: 1rem; }
    code { background: #f3f3f3; padding: 0.1rem 0.35rem; border-radius: 3px; font-size: 0.92em; }
    table { border-collapse: collapse; margin: 1rem 0; width: 100%; }
    th, td { border: 1px solid #ddd; padding: 0.5rem 0.75rem; text-align: left; vertical-align: top; }
    th { background: #f6f6f6; font-weight: 600; }
    a { color: #0d57a8; }
    hr { border: 0; border-top: 1px solid #e3e3e3; margin: 2rem 0; }
    @media (prefers-color-scheme: dark) {
      body { color: #e8e8e8; background: #0d1b2a; }
      h1 { border-bottom-color: #1f3148; }
      hr { border-top-color: #1f3148; }
      code { background: #1f3148; }
      th { background: #16263a; }
      th, td { border-color: #1f3148; }
      a { color: #7fb3ff; }
    }
  </style>
</head>
<body>
${body}
</body>
</html>
`

mkdirSync(dirname(out), { recursive: true })
writeFileSync(out, html)
console.log(`Wrote ${out} (${html.length} bytes)`)
