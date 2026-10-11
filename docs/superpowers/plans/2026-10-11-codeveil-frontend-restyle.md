# Codeveil-Style Frontend Restyle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the entire Online Judge frontend to the light "Codeveil" blueprint/brutalist aesthetic and add a new landing page at `/`, moving the problem list to `/problems`.

**Architecture:** Approach A from the spec — keep the existing global-CSS strategy. Rewrite `tokens.css` + `index.css` (no CSS modules, no Tailwind, no new npm packages). Self-host three OFL fonts via a one-time download script. All component class names stay; markup changes are limited to NavBar (brand mark + button CTAs), Layout (print frame + footer), a new LandingPage, VerdictBadge (symbol prefix), and CodeEditor (border wrapper).

**Tech Stack:** React 18 + TypeScript + Vite, React Router 7, React Query, CodeMirror (@uiw/react-codemirror), Vitest + Testing Library. No dependency additions.

**Spec:** `docs/superpowers/specs/2026-10-11-codeveil-frontend-restyle-design.md` — read it before starting; this plan implements it section by section.

## Global Constraints

- Work only inside `frontend/` (plus the `PRD.md` §31 doc update in Task 6). No backend/API/schema changes.
- **No new npm dependencies.** Fonts are self-hosted static assets (SIL OFL 1.1), not packages.
- Color tokens: exact values from spec §4.1. Fonts: Instrument Serif 400/400-italic, Inter 400/500/600, JetBrains Mono 400/500.
- Border radius is `0` everywhere; the only shadow is the hard button hover `3px 3px 0 var(--ink)`.
- Keep accessible names stable where possible (heading text, link text, `aria-label`s) so existing tests keep passing; test updates are only the ones this plan explicitly lists.
- Definition of done for every task: `npm run lint && npm run test && npm run build` all pass (run from `frontend/`).
- Commit steps in this plan are task boundaries with Conventional Commit messages. Per the human's standing rule, only run the `git commit` steps if the human has approved committing; otherwise end each task at its verified state and say so.
- Never execute user-submitted code anywhere — the landing code window is a static display fixture containing our own snippet.

---

## File Structure

| File | Action | Responsibility |
| --- | --- | --- |
| `frontend/scripts/download-fonts.mjs` | Create | One-time font fetcher; downloads latin woff2 files + generates `fonts.css` |
| `frontend/public/fonts/*.woff2` | Generated (committed) | 7 self-hosted font files |
| `frontend/src/styles/fonts.css` | Generated (committed) | `@font-face` rules (generated header says so) |
| `frontend/src/styles/tokens.css` | Rewrite | Design tokens (paper/ink/verdict/typography/spacing) |
| `frontend/src/index.css` | Rewrite | Entire visual system |
| `frontend/index.html` | Modify | Font preloads |
| `frontend/src/components/Layout.tsx` | Modify | Print frame + footer |
| `frontend/src/components/NavBar.tsx` | Modify | Brand mark, `/problems` link, button CTAs |
| `frontend/src/pages/LandingPage.tsx` | Create | Hero + code window + stack strip |
| `frontend/src/pages/LandingPage.test.tsx` | Create | Landing behavior + content tests |
| `frontend/src/App.tsx` | Modify | `/` → landing, `/problems` → list |
| `frontend/src/App.test.tsx` | Modify | Landing-at-`/` assertions |
| `frontend/src/pages/LoginPage.tsx` | Modify | Fallback redirect `/` → `/problems` |
| `frontend/src/pages/LoginPage.test.tsx` | Modify | Route fixture follows the redirect change |
| `frontend/src/pages/RegisterPage.tsx` | Modify | Post-register redirect → `/problems` |
| `frontend/src/components/VerdictBadge.tsx` | Modify | Symbol prefix, aria-hidden |
| `frontend/src/components/VerdictBadge.test.tsx` | Modify | Symbol test |
| `frontend/src/components/CodeEditor.tsx` | Modify | `.code-editor` wrapper |
| `PRD.md` | Modify | §31 frontend requirements |

Everything else (pages, components, api, auth) is untouched.

---

### Task 1: Self-hosted fonts pipeline

**Files:**
- Create: `frontend/scripts/download-fonts.mjs`
- Generated: `frontend/public/fonts/*.woff2` (7 files), `frontend/src/styles/fonts.css`
- Modify: `frontend/index.html`, `frontend/src/index.css` (add import only)

**Interfaces:**
- Consumes: nothing.
- Produces: font files at `/fonts/<slug>-<weight>[-italic].woff2` served from `public/`, and `src/styles/fonts.css` with `@font-face` rules for `'Instrument Serif'`, `'Inter'`, `'JetBrains Mono'`. Task 2's `index.css` rewrite keeps the `@import './styles/fonts.css';` line.

- [ ] **Step 1: Write the download script**

Create `frontend/scripts/download-fonts.mjs`:

```js
import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const outDir = path.join(root, 'public', 'fonts')
const cssOut = path.join(root, 'src', 'styles', 'fonts.css')

const UA =
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36'

const FAMILIES = [
  { css: 'Instrument+Serif:ital@0;1', slug: 'instrument-serif' },
  { css: 'Inter:wght@400;500;600', slug: 'inter' },
  { css: 'JetBrains+Mono:wght@400;500', slug: 'jetbrains-mono' },
]

function parseLatinFaces(css, familyName, slug) {
  const faces = []
  const blocks = css.split(/\/\*\s*([\w-]+)\s*\*\//g)
  for (let i = 1; i < blocks.length; i += 2) {
    const subset = blocks[i]
    const block = blocks[i + 1]
    if (subset !== 'latin') continue
    const weight = /font-weight:\s*(\d+)/.exec(block)?.[1]
    const style = /font-style:\s*(\w+)/.exec(block)?.[1]
    const url = /src:\s*url\(([^)]+)\)/.exec(block)?.[1]?.replace(/['"]/g, '')
    if (!weight || !style || !url) continue
    faces.push({ familyName, slug, weight, style, url })
  }
  return faces
}

async function main() {
  await mkdir(outDir, { recursive: true })
  const rules = []
  for (const family of FAMILIES) {
    const cssUrl = `https://fonts.googleapis.com/css2?family=${family.css}&display=swap`
    const css = await (await fetch(cssUrl, { headers: { 'User-Agent': UA } })).text()
    const familyName = /font-family:\s*'([^']+)'/.exec(css)?.[1]
    const faces = parseLatinFaces(css, familyName, family.slug)
    if (faces.length === 0) throw new Error(`No latin faces found for ${family.css}`)
    for (const face of faces) {
      const file = `${face.slug}-${face.weight}${face.style === 'italic' ? '-italic' : ''}.woff2`
      const bytes = new Uint8Array(await (await fetch(face.url)).arrayBuffer())
      await writeFile(path.join(outDir, file), bytes)
      rules.push(
        [
          '@font-face {',
          `  font-family: '${face.familyName}';`,
          `  font-style: ${face.style};`,
          `  font-weight: ${face.weight};`,
          '  font-display: swap;',
          `  src: url('/fonts/${file}') format('woff2');`,
          '}',
        ].join('\n'),
      )
    }
  }
  const header = '/* Generated by scripts/download-fonts.mjs — do not edit by hand. */\n\n'
  await writeFile(cssOut, header + rules.join('\n\n') + '\n')
  console.log(`Wrote ${rules.length} @font-face rules to src/styles/fonts.css`)
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
```

- [ ] **Step 2: Run the script**

Run (from `frontend/`): `node scripts/download-fonts.mjs`
Expected: `Wrote 7 @font-face rules to src/styles/fonts.css` (2 Instrument Serif + 3 Inter + 2 JetBrains Mono).

- [ ] **Step 3: Verify generated artifacts**

Run: `Get-ChildItem public\fonts | Select-Object Name, Length`
Expected: exactly these 7 files, each a non-zero size (roughly 15–120KB):
`instrument-serif-400.woff2`, `instrument-serif-400-italic.woff2`, `inter-400.woff2`, `inter-500.woff2`, `inter-600.woff2`, `jetbrains-mono-400.woff2`, `jetbrains-mono-500.woff2`.
Also confirm `src/styles/fonts.css` starts with the generated header comment.

- [ ] **Step 4: Add preloads to `index.html`**

Replace the `<head>` of `frontend/index.html` with:

```html
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="preload" href="/fonts/instrument-serif-400.woff2" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="/fonts/inter-400.woff2" as="font" type="font/woff2" crossorigin />
    <link rel="preload" href="/fonts/jetbrains-mono-400.woff2" as="font" type="font/woff2" crossorigin />
    <title>Online Judge</title>
  </head>
```

- [ ] **Step 5: Import the generated stylesheet**

In `frontend/src/index.css`, add below the existing `@import './styles/tokens.css';` line:

```css
@import './styles/fonts.css';
```

(Task 2 rewrites this file but keeps both imports.)

- [ ] **Step 6: Verify build and asset copy**

Run (from `frontend/`): `npm run build`
Expected: build succeeds; then `Get-ChildItem dist\fonts` lists the same 7 woff2 files (Vite copies `public/`).

- [ ] **Step 7: Commit**

```bash
git add frontend/scripts/download-fonts.mjs frontend/public/fonts frontend/src/styles/fonts.css frontend/index.html frontend/src/index.css
git commit -m "feat(frontend): self-host Instrument Serif, Inter, and JetBrains Mono"
```

---

### Task 2: Design tokens + full stylesheet rewrite + frame/footer layout

**Files:**
- Rewrite: `frontend/src/styles/tokens.css`
- Rewrite: `frontend/src/index.css`
- Modify: `frontend/src/components/Layout.tsx`

**Interfaces:**
- Consumes: `/fonts/*` and `src/styles/fonts.css` from Task 1 (import kept).
- Produces: token names `--paper, --paper-raise, --ink, --ink-soft, --ink-faint, --grid-line, --grid-line-major, --tint, --verdict-ok, --verdict-ok-bg, --verdict-bad, --verdict-bad-bg, --verdict-warn, --verdict-warn-bg, --verdict-pending, --verdict-pending-bg, --font-display, --font-body, --font-mono, --space-1..6, --frame-margin`; class names `.frame`, `.tape`, `.footer`, `.btn`, `.btn--solid`, `.btn--outline`, `.nav__mark`, `.hero*`, `.code-window*`, `.stack-strip*`, `.tok-*`, `.verdict__icon`, `.code-editor`. Tasks 3–5 rely on exactly these names.

- [ ] **Step 1: Rewrite `frontend/src/styles/tokens.css`**

Full new content:

```css
:root {
  --paper: #f2f0eb;
  --paper-raise: #faf9f6;
  --ink: #141414;
  --ink-soft: #5f5f5f;
  --ink-faint: #9a9a9a;
  --grid-line: rgba(20, 20, 20, 0.07);
  --grid-line-major: rgba(20, 20, 20, 0.13);
  --tint: rgba(20, 20, 20, 0.04);
  --verdict-ok: #1f6f3f;
  --verdict-ok-bg: rgba(31, 111, 63, 0.08);
  --verdict-bad: #a63a31;
  --verdict-bad-bg: rgba(166, 58, 49, 0.08);
  --verdict-warn: #8a6a00;
  --verdict-warn-bg: rgba(138, 106, 0, 0.1);
  --verdict-pending: #5f5f5f;
  --verdict-pending-bg: rgba(95, 95, 95, 0.08);
  --font-display: 'Instrument Serif', Georgia, 'Times New Roman', serif;
  --font-body: 'Inter', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  --font-mono: 'JetBrains Mono', ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-3: 1rem;
  --space-4: 1.5rem;
  --space-5: 2.5rem;
  --space-6: 4rem;
  --frame-margin: 12px;
}
```

- [ ] **Step 2: Rewrite `frontend/src/index.css`**

Full new content:

```css
@import './styles/tokens.css';
@import './styles/fonts.css';

:root {
  font-family: var(--font-body);
  color-scheme: light;
}

body {
  margin: 0;
  min-height: 100vh;
  background-color: var(--paper);
  background-image:
    linear-gradient(var(--grid-line) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid-line) 1px, transparent 1px),
    linear-gradient(var(--grid-line-major) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid-line-major) 1px, transparent 1px);
  background-size: 24px 24px, 24px 24px, 120px 120px, 120px 120px;
  background-attachment: fixed;
  color: var(--ink);
  font-size: 15px;
  line-height: 1.6;
}

h1 {
  font-family: var(--font-display);
  font-weight: 400;
  font-size: 2rem;
  line-height: 1.15;
  letter-spacing: -0.01em;
  margin: 0 0 var(--space-3);
}

h2 {
  font-size: 1.05rem;
  font-weight: 600;
  margin: var(--space-4) 0 var(--space-2);
}

h3 {
  font-size: 0.95rem;
  font-weight: 600;
  margin: var(--space-3) 0 var(--space-2);
}

a {
  color: var(--ink);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
}

a:hover {
  background: var(--tint);
}

:focus-visible {
  outline: 2px solid var(--ink);
  outline-offset: 1px;
}

/* ---- print frame ---- */

.frame {
  margin: var(--frame-margin);
  border: 1px solid var(--ink);
  background: var(--paper);
  min-height: calc(100vh - 2 * var(--frame-margin));
  display: flex;
  flex-direction: column;
}

.layout__main {
  flex: 1;
  width: 100%;
  max-width: 1100px;
  margin: 0 auto;
  padding: var(--space-5) var(--space-4) var(--space-6);
  box-sizing: border-box;
}

.footer {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  border-top: 1px solid var(--ink);
  padding: var(--space-3) var(--space-4);
  font-size: 12px;
  color: var(--ink-soft);
}

.footer__spacer {
  flex: 1;
}

.footer__brand {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.02em;
}

/* ---- nav ---- */

.nav {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--ink);
  background: var(--paper);
}

.nav__brand {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  font-family: var(--font-mono);
  font-weight: 500;
  font-size: 14px;
  letter-spacing: 0.02em;
  text-decoration: none;
}

.nav__brand:hover {
  background: transparent;
}

.nav__mark {
  width: 26px;
  height: 26px;
  border: 1.5px solid var(--ink);
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-mono);
  font-size: 9px;
}

.nav a:not(.nav__brand):not(.btn) {
  font-size: 13px;
}

.nav__user {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--ink-soft);
}

.nav__spacer {
  flex: 1;
}

/* ---- buttons ---- */

button,
.btn {
  font: inherit;
  font-size: 13px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--ink);
  border-radius: 0;
  background: var(--paper);
  color: var(--ink);
  cursor: pointer;
  text-decoration: none;
  transition: box-shadow 120ms ease, transform 120ms ease;
}

button:hover:not(:disabled),
.btn:hover {
  box-shadow: 3px 3px 0 var(--ink);
  transform: translate(-1px, -1px);
  background: var(--paper);
}

button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.btn--solid {
  background: var(--ink);
  color: var(--paper);
}

.btn--solid:hover {
  background: var(--ink);
  color: var(--paper);
  box-shadow: 3px 3px 0 var(--ink-soft);
}

.btn--outline {
  background: transparent;
}

/* ---- forms ---- */

input,
select,
textarea {
  font: inherit;
  font-size: 14px;
  display: block;
  width: 100%;
  max-width: 320px;
  box-sizing: border-box;
  padding: var(--space-2);
  margin-top: var(--space-1);
  border: 1px solid var(--ink);
  border-radius: 0;
  background: var(--paper-raise);
  color: var(--ink);
}

textarea {
  max-width: 640px;
  font-family: var(--font-mono);
  font-size: 13px;
}

form label,
.filters label {
  display: block;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--ink-soft);
}

form label {
  margin: var(--space-3) 0;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: var(--space-3);
  margin: var(--space-3) 0 var(--space-4);
}

.filters label {
  margin: 0;
}

/* ---- hazard tape ---- */

.tape {
  height: 26px;
  border-top: 1px solid var(--ink);
  border-bottom: 1px solid var(--ink);
  background: repeating-linear-gradient(45deg, var(--ink) 0 7px, var(--paper) 7px 14px);
}

/* ---- tables ---- */

.problem-table {
  width: 100%;
  border-collapse: collapse;
  margin: var(--space-4) 0;
  background: var(--paper-raise);
}

.problem-table th,
.problem-table td {
  text-align: left;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--ink);
}

.problem-table th {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.problem-table tbody tr:hover {
  background: var(--tint);
}

.linklike {
  background: none;
  border: none;
  padding: 0;
  font: inherit;
  color: var(--ink);
  text-decoration: underline;
  text-decoration-thickness: 1px;
  text-underline-offset: 3px;
  cursor: pointer;
}

.linklike:hover {
  box-shadow: none;
  transform: none;
  background: var(--tint);
}

/* ---- feedback ---- */

.spinner {
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--ink-soft);
  padding: var(--space-4) 0;
}

.spinner::after {
  content: '▍';
  margin-left: var(--space-1);
  animation: blink 1s steps(1) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.error-banner {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  padding: var(--space-3);
  border: 1px solid var(--verdict-bad);
  border-left-width: 4px;
  background: var(--paper-raise);
  color: var(--ink);
  margin: var(--space-3) 0;
}

.error-banner::before {
  content: 'ERROR';
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--verdict-bad);
}

.error-inline {
  color: var(--verdict-bad);
  font-size: 13px;
}

.error-detail {
  border-color: var(--verdict-bad);
}

.notice {
  border: 1px solid var(--ink);
  padding: var(--space-2) var(--space-3);
  font-family: var(--font-mono);
  font-size: 12px;
}

/* ---- problem content ---- */

.statement {
  white-space: pre-wrap;
  max-width: 72ch;
}

.problem-meta {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--ink-soft);
}

.sample-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--ink-soft);
  margin: var(--space-2) 0 var(--space-1);
}

pre {
  background: var(--paper-raise);
  border: 1px solid var(--ink);
  padding: var(--space-2) var(--space-3);
  font-family: var(--font-mono);
  font-size: 13px;
  overflow: auto;
}

.submit-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  border: 1px solid var(--ink);
  background: var(--paper-raise);
  padding: var(--space-4);
  margin-top: var(--space-3);
}

/* ---- timeline ---- */

.timeline {
  display: flex;
  flex-wrap: wrap;
  list-style: none;
  padding: 0;
  margin: var(--space-3) 0;
}

.timeline__step {
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--ink);
  margin-right: -1px;
  background: var(--paper);
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.timeline__step--done {
  background: var(--ink);
  color: var(--paper);
}

.timeline__failure {
  color: var(--verdict-bad);
  font-weight: 600;
}

/* ---- pagination ---- */

.pagination {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-top: var(--space-3);
  font-size: 13px;
}

/* ---- verdicts ---- */

.verdict {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  padding: 1px 6px;
  border: 1px solid currentColor;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
}

.verdict__icon {
  font-family: var(--font-body);
  font-size: 11px;
}

.verdict--ok {
  color: var(--verdict-ok);
  background: var(--verdict-ok-bg);
}

.verdict--bad {
  color: var(--verdict-bad);
  background: var(--verdict-bad-bg);
}

.verdict--warn {
  color: var(--verdict-warn);
  background: var(--verdict-warn-bg);
}

.verdict--pending {
  color: var(--verdict-pending);
  background: var(--verdict-pending-bg);
}

.counter-list {
  display: flex;
  gap: var(--space-4);
  list-style: none;
  padding: 0;
  flex-wrap: wrap;
}

/* ---- code editor ---- */

.code-editor {
  border: 1px solid var(--ink);
  background: var(--paper-raise);
}

.code-editor .cm-editor {
  font-family: var(--font-mono);
  font-size: 13px;
}

.code-editor .cm-editor.cm-focused {
  outline: none;
}

.code-editor .cm-gutters {
  background: var(--paper);
  border-right: 1px solid var(--ink);
}

/* ---- landing ---- */

.hero {
  text-align: center;
  padding: var(--space-6) var(--space-2) var(--space-5);
}

.hero__title {
  font-family: var(--font-display);
  font-weight: 400;
  font-size: clamp(2.5rem, 6vw, 4.25rem);
  line-height: 1.05;
  letter-spacing: -0.02em;
  margin: 0;
}

.hero__title-faded {
  display: block;
  color: var(--ink-faint);
}

.hero__sub {
  max-width: 46ch;
  margin: var(--space-3) auto 0;
  color: var(--ink-soft);
}

.hero__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--space-3);
  margin-top: var(--space-4);
}

.code-window {
  max-width: 720px;
  margin: var(--space-5) auto 0;
  border: 1px solid var(--ink);
  background: var(--paper-raise);
  text-align: left;
}

.code-window__chrome {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-3);
  border-bottom: 1px solid var(--ink);
}

.code-window__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid var(--ink);
}

.code-window__dot--red {
  background: #e5484d;
}

.code-window__dot--amber {
  background: #f5a524;
}

.code-window__dot--green {
  background: #2ea043;
}

.code-window__title {
  margin-left: var(--space-2);
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--ink-soft);
}

.code-window__body {
  display: grid;
  grid-template-columns: minmax(140px, 180px) 1fr;
}

.code-window__tree {
  border-right: 1px solid var(--ink);
  padding: var(--space-3);
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--ink-soft);
  line-height: 1.8;
}

.code-window__tree-title {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--ink);
}

.code-window__tree ul {
  list-style: none;
  margin: var(--space-2) 0 0;
  padding: 0;
}

.code-window__code {
  margin: 0;
  border: none;
  padding: var(--space-3);
  font-size: 12.5px;
  line-height: 1.55;
  background: transparent;
  overflow: auto;
}

.code-window__line {
  display: block;
  white-space: pre;
}

.tok-key {
  color: #3b5bdb;
}

.tok-str {
  color: #a63a31;
}

.tok-com {
  color: var(--ink-faint);
  font-style: italic;
}

.tok-type {
  color: #1f6f3f;
}

.tok-num {
  color: #8a6a00;
}

.stack-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  border-bottom: 1px solid var(--ink);
}

.stack-strip__cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-4) var(--space-2);
  border-right: 1px solid var(--ink);
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.stack-strip__cell:last-child {
  border-right: none;
}

.stack-strip__icon {
  width: 16px;
  height: 16px;
  flex: none;
}

/* ---- small screens ---- */

@media (max-width: 720px) {
  .nav {
    flex-wrap: wrap;
  }

  .code-window__body {
    grid-template-columns: 1fr;
  }

  .code-window__tree {
    border-right: none;
    border-bottom: 1px solid var(--ink);
  }

  .stack-strip {
    grid-template-columns: 1fr 1fr;
  }

  .stack-strip__cell:nth-child(2n) {
    border-right: none;
  }

  .stack-strip__cell:nth-child(-n + 2) {
    border-bottom: 1px solid var(--ink);
  }
}
```

- [ ] **Step 3: Update `frontend/src/components/Layout.tsx`**

Full new content:

```tsx
import { Link, Outlet } from 'react-router-dom'
import { NavBar } from './NavBar'

export function Layout() {
  return (
    <div className="frame">
      <NavBar />
      <main className="layout__main">
        <Outlet />
      </main>
      <footer className="footer">
        <span className="footer__brand">Online Judge</span>
        <span className="footer__spacer" />
        <Link to="/problems">Problems</Link>
      </footer>
    </div>
  )
}
```

- [ ] **Step 4: Verify no stale token references remain**

Run (from repo root): `rg "var\(--color-" frontend/src`
Expected: no matches (the old tokens are fully replaced; all references were in `index.css`).

- [ ] **Step 5: Run lint, tests, build**

Run (from `frontend/`): `npm run lint && npm run test && npm run build`
Expected: all pass. (No markup/behavior changed in this task beyond Layout's frame/footer, which no test asserts against.)

- [ ] **Step 6: Visual smoke check**

Start `npm run dev` in the background; open `http://localhost:5173/` in the browser tooling; confirm: cream paper background with visible grid, 1px ink frame with margin, nav with circular "OJ" mark, footer strip. Screenshot for the record. Stop the dev server.
Note: at this point `/` is still the problem list; without the backend running an error banner is expected — that is fine for this smoke check.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/styles/tokens.css frontend/src/index.css frontend/src/components/Layout.tsx
git commit -m "feat(frontend): blueprint design tokens, global styles, print frame"
```

---

### Task 3: Route move + NavBar + landing page (hero/CTAs) + redirects

**Files:**
- Modify: `frontend/src/App.tsx`, `frontend/src/App.test.tsx`
- Modify: `frontend/src/components/NavBar.tsx`
- Modify: `frontend/src/pages/LoginPage.tsx`, `frontend/src/pages/LoginPage.test.tsx`
- Modify: `frontend/src/pages/RegisterPage.tsx`
- Create: `frontend/src/pages/LandingPage.tsx`, `frontend/src/pages/LandingPage.test.tsx`

**Interfaces:**
- Consumes: `.btn`, `.btn--solid`, `.btn--outline`, `.nav__mark`, `.hero`, `.hero__title`, `.hero__title-faded`, `.hero__sub`, `.hero__actions` from Task 2.
- Produces: `LandingPage` component exporting `export function LandingPage()`; routes `/` (landing), `/problems` (list). Task 4 extends `LandingPage.tsx` with the code window and stack strip.

- [ ] **Step 1: Write the failing tests**

Replace `frontend/src/App.test.tsx` with:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import App from './App'
import { renderWithProviders } from './test/renderWithProviders'
import { AuthProvider } from './auth/AuthContext'

describe('App shell', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('renders the landing page at / with navigation to the problem list', () => {
    renderWithProviders(
      <AuthProvider>
        <App />
      </AuthProvider>,
    )
    expect(
      screen.getByRole('heading', { name: /where your code meets its judge/i }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /online judge/i })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Problems' })).toHaveAttribute('href', '/problems')
  })
})
```

Create `frontend/src/pages/LandingPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { AuthProvider } from '../auth/AuthContext'
import { LandingPage } from './LandingPage'
import { renderWithProviders } from '../test/renderWithProviders'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const ACCESS = `h.${encodeSegment({ sub: 'alice', uid: 1, roles: [] })}.s`

describe('LandingPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('shows the hero and links to the problem list and registration', () => {
    renderWithProviders(
      <AuthProvider>
        <LandingPage />
      </AuthProvider>,
    )
    expect(
      screen.getByRole('heading', { name: /where your code meets its judge/i }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /browse problems/i })).toHaveAttribute(
      'href',
      '/problems',
    )
    expect(screen.getByRole('link', { name: /create account/i })).toHaveAttribute(
      'href',
      '/register',
    )
  })

  it('links to submissions instead of registration when authenticated', async () => {
    localStorage.setItem('oj.refreshToken', 'refresh-1')
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ accessToken: ACCESS, expiresIn: 900 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    renderWithProviders(
      <AuthProvider>
        <LandingPage />
      </AuthProvider>,
    )
    expect(await screen.findByRole('link', { name: /my submissions/i })).toHaveAttribute(
      'href',
      '/submissions',
    )
    expect(screen.queryByRole('link', { name: /create account/i })).not.toBeInTheDocument()
  })
})
```

Update `frontend/src/pages/LoginPage.test.tsx` — replace the two `<Route>` lines inside `renderWithProviders`:

```tsx
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/problems" element={<div>home page</div>} />
        </Routes>
```

(The rest of the file, including `{ route: '/login' }` and the `home page` assertion, stays unchanged.)

- [ ] **Step 2: Run the tests to verify they fail**

Run (from `frontend/`): `npm run test`
Expected: FAIL — `LandingPage` module does not exist; `App.test` cannot find the hero heading; `LoginPage.test` fails because the fallback still redirects to `/` which no longer has a matching route in its fixture.

- [ ] **Step 3: Create `frontend/src/pages/LandingPage.tsx`** (hero + CTAs version)

```tsx
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function LandingPage() {
  const { status } = useAuth()
  const authenticated = status === 'authenticated'

  return (
    <div className="landing">
      <section className="hero">
        <h1 className="hero__title">
          {'Where Your Code Meets Its Judge'}
          <span className="hero__title-faded">{"And 'TLE' Wasn't Your Fault"}</span>
        </h1>
        <p className="hero__sub">
          Compiled, sandboxed, and run in isolated containers — verdicts in seconds, not excuses.
        </p>
        <div className="hero__actions">
          <Link to="/problems" className="btn btn--solid">
            Browse Problems
          </Link>
          {authenticated ? (
            <Link to="/submissions" className="btn btn--outline">
              My Submissions
            </Link>
          ) : (
            <Link to="/register" className="btn btn--outline">
              Create Account
            </Link>
          )}
        </div>
      </section>
    </div>
  )
}
```

- [ ] **Step 4: Update `frontend/src/App.tsx`**

Change the import block and the first route only (everything else stays):

```tsx
import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { LandingPage } from './pages/LandingPage'
import { ProblemListPage } from './pages/ProblemListPage'
```

and in the routes:

```tsx
      <Route element={<Layout />}>
        <Route index element={<LandingPage />} />
        <Route path="problems" element={<ProblemListPage />} />
        <Route path="problems/:slug" element={<ProblemDetailPage />} />
```

(All other routes unchanged.)

- [ ] **Step 5: Update `frontend/src/components/NavBar.tsx`**

Full new content:

```tsx
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

export function NavBar() {
  const { status, user, logout } = useAuth()
  const navigate = useNavigate()

  async function onLogout() {
    await logout()
    navigate('/')
  }

  return (
    <nav className="nav">
      <Link to="/" className="nav__brand">
        <span className="nav__mark" aria-hidden="true">
          OJ
        </span>
        Online Judge
      </Link>
      <Link to="/problems">Problems</Link>
      {status === 'authenticated' && <Link to="/submissions">My Submissions</Link>}
      {status === 'authenticated' && user?.roles.includes('ROLE_ADMIN') && (
        <Link to="/admin/problems">Admin</Link>
      )}
      <span className="nav__spacer" />
      {status === 'authenticated' ? (
        <>
          <span className="nav__user">{user?.username}</span>
          <button type="button" className="btn btn--outline" onClick={onLogout}>
            Log out
          </button>
        </>
      ) : (
        <>
          <Link to="/login" className="btn btn--outline">
            Sign in
          </Link>
          <Link to="/register" className="btn btn--solid">
            Register
          </Link>
        </>
      )}
    </nav>
  )
}
```

- [ ] **Step 6: Update the two redirect fallbacks**

In `frontend/src/pages/LoginPage.tsx`, change:

```tsx
  const from = locationState?.from ?? '/'
```

to:

```tsx
  const from = locationState?.from ?? '/problems'
```

In `frontend/src/pages/RegisterPage.tsx`, change:

```tsx
      navigate('/', { replace: true })
```

to:

```tsx
      navigate('/problems', { replace: true })
```

- [ ] **Step 7: Run the tests to verify they pass**

Run (from `frontend/`): `npm run test`
Expected: PASS — all suites, including the rewritten `App.test.tsx`, new `LandingPage.test.tsx`, and updated `LoginPage.test.tsx`.

- [ ] **Step 8: Lint and build**

Run: `npm run lint && npm run build`
Expected: both pass.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/components/NavBar.tsx frontend/src/pages/LandingPage.tsx frontend/src/pages/LandingPage.test.tsx frontend/src/pages/LoginPage.tsx frontend/src/pages/LoginPage.test.tsx frontend/src/pages/RegisterPage.tsx
git commit -m "feat(frontend): landing route at /, problem list moves to /problems"
```

---

### Task 4: Landing visuals — hazard tape, mock code window, stack strip

**Files:**
- Modify: `frontend/src/pages/LandingPage.tsx`
- Modify: `frontend/src/pages/LandingPage.test.tsx`

**Interfaces:**
- Consumes: `.tape`, `.code-window*`, `.tok-*`, `.stack-strip*` classes from Task 2.
- Produces: nothing consumed by later tasks (final landing markup).

- [ ] **Step 1: Add the failing assertions**

Append this test inside the `describe('LandingPage', ...)` block in `frontend/src/pages/LandingPage.test.tsx`:

```tsx
  it('shows the mock editor window and the stack strip', () => {
    renderWithProviders(
      <AuthProvider>
        <LandingPage />
      </AuthProvider>,
    )
    expect(screen.getByText('Main.java — two-sum')).toBeInTheDocument()
    expect(screen.getByText('Main.java')).toBeInTheDocument()
    expect(screen.getByText('solution.py')).toBeInTheDocument()
    expect(screen.getByText('Docker Sandbox')).toBeInTheDocument()
    expect(screen.getByText('RabbitMQ')).toBeInTheDocument()
  })
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `frontend/`): `npx vitest run src/pages/LandingPage.test.tsx`
Expected: FAIL — `Main.java — two-sum` not found (window not implemented yet).

- [ ] **Step 3: Replace `frontend/src/pages/LandingPage.tsx` with the full version**

```tsx
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

type Token = [className: string | null, text: string]

const SNIPPET: Token[][] = [
  [['tok-com', '// two-sum — find two indices that add up to target']],
  [['tok-key', 'import'], [null, ' java.util.*;']],
  [],
  [
    ['tok-key', 'public'],
    [null, ' '],
    ['tok-key', 'class'],
    [null, ' '],
    ['tok-type', 'Main'],
    [null, ' {'],
  ],
  [
    [null, '    '],
    ['tok-key', 'public'],
    [null, ' '],
    ['tok-key', 'static'],
    [null, ' '],
    ['tok-key', 'void'],
    [null, ' '],
    ['tok-type', 'main'],
    [null, '('],
    ['tok-type', 'String'],
    [null, '[] args) {'],
  ],
  [
    [null, '        '],
    ['tok-type', 'Scanner'],
    [null, ' sc = '],
    ['tok-key', 'new'],
    [null, ' '],
    ['tok-type', 'Scanner'],
    [null, '('],
    ['tok-type', 'System'],
    [null, '.in);'],
  ],
  [[null, '        '], ['tok-key', 'int'], [null, ' target = sc.nextInt();']],
  [
    [null, '        '],
    ['tok-key', 'int'],
    [null, '[] nums = { '],
    ['tok-num', '2'],
    [null, ', '],
    ['tok-num', '7'],
    [null, ', '],
    ['tok-num', '11'],
    [null, ', '],
    ['tok-num', '15'],
    [null, ' };'],
  ],
  [
    [null, '        '],
    ['tok-type', 'Map'],
    [null, '<'],
    ['tok-type', 'Integer'],
    [null, ', '],
    ['tok-type', 'Integer'],
    [null, '> seen = '],
    ['tok-key', 'new'],
    [null, ' '],
    ['tok-type', 'HashMap'],
    [null, '<>();'],
  ],
  [
    [null, '        '],
    ['tok-key', 'for'],
    [null, ' ('],
    ['tok-key', 'int'],
    [null, ' i = '],
    ['tok-num', '0'],
    [null, '; i < nums.length; i++) {'],
  ],
  [[null, '            '], ['tok-key', 'int'], [null, ' need = target - nums[i];']],
  [[null, '            '], ['tok-key', 'if'], [null, ' (seen.containsKey(need)) {']],
  [
    [null, '                '],
    ['tok-type', 'System'],
    [null, '.out.println(seen.get(need) + '],
    ['tok-str', '" "'],
    [null, ' + i);'],
  ],
  [[null, '                '], ['tok-key', 'return'], [null, ';']],
  [[null, '            '], [null, '}']],
  [[null, '            '], [null, 'seen.put(nums[i], i);']],
  [[null, '        '], [null, '}']],
  [[null, '    '], [null, '}']],
  [[null, '}']],
]

const STACK: { label: string; icon: JSX.Element }[] = [
  {
    label: 'Java 21',
    icon: (
      <svg className="stack-strip__icon" viewBox="0 0 16 16" aria-hidden="true">
        <circle cx="8" cy="8" r="6.5" fill="none" stroke="currentColor" />
      </svg>
    ),
  },
  {
    label: 'Python 3.12',
    icon: (
      <svg className="stack-strip__icon" viewBox="0 0 16 16" aria-hidden="true">
        <polygon points="8,1.5 14.5,13.5 1.5,13.5" fill="none" stroke="currentColor" />
      </svg>
    ),
  },
  {
    label: 'Docker Sandbox',
    icon: (
      <svg className="stack-strip__icon" viewBox="0 0 16 16" aria-hidden="true">
        <rect x="2" y="2" width="12" height="12" fill="none" stroke="currentColor" />
      </svg>
    ),
  },
  {
    label: 'RabbitMQ',
    icon: (
      <svg className="stack-strip__icon" viewBox="0 0 16 16" aria-hidden="true">
        <polygon points="8,1 15,8 8,15 1,8" fill="none" stroke="currentColor" />
      </svg>
    ),
  },
]

export function LandingPage() {
  const { status } = useAuth()
  const authenticated = status === 'authenticated'

  return (
    <div className="landing">
      <section className="hero">
        <h1 className="hero__title">
          {'Where Your Code Meets Its Judge'}
          <span className="hero__title-faded">{"And 'TLE' Wasn't Your Fault"}</span>
        </h1>
        <p className="hero__sub">
          Compiled, sandboxed, and run in isolated containers — verdicts in seconds, not excuses.
        </p>
        <div className="hero__actions">
          <Link to="/problems" className="btn btn--solid">
            Browse Problems
          </Link>
          {authenticated ? (
            <Link to="/submissions" className="btn btn--outline">
              My Submissions
            </Link>
          ) : (
            <Link to="/register" className="btn btn--outline">
              Create Account
            </Link>
          )}
        </div>
        <div className="code-window">
          <div className="code-window__chrome">
            <span className="code-window__dot code-window__dot--red" />
            <span className="code-window__dot code-window__dot--amber" />
            <span className="code-window__dot code-window__dot--green" />
            <span className="code-window__title">Main.java — two-sum</span>
          </div>
          <div className="code-window__body">
            <div className="code-window__tree">
              <div className="code-window__tree-title">Explorer</div>
              <ul>
                <li>online-judge/</li>
                <li>├─ src/</li>
                <li>│  ├─ Main.java</li>
                <li>│  └─ solution.py</li>
                <li>└─ tests/</li>
              </ul>
            </div>
            <pre className="code-window__code">
              {SNIPPET.map((line, lineIndex) => (
                <span className="code-window__line" key={lineIndex}>
                  {line.map(([className, text], tokenIndex) =>
                    className ? (
                      <span key={tokenIndex} className={className}>
                        {text}
                      </span>
                    ) : (
                      <span key={tokenIndex}>{text}</span>
                    ),
                  )}
                  {'\n'}
                </span>
              ))}
            </pre>
          </div>
        </div>
      </section>
      <div className="tape" />
      <div className="stack-strip">
        {STACK.map((item) => (
          <div className="stack-strip__cell" key={item.label}>
            {item.icon}
            {item.label}
          </div>
        ))}
      </div>
    </div>
  )
}
```

Note: the file must start with the `import` lines shown; `JSX.Element` requires no extra import under this tsconfig (global JSX namespace). If `tsc` complains about `JSX.Element`, change the `STACK` type annotation to `{ label: string; icon: React.ReactElement }` and add `import type { ReactElement } from 'react'`, using `ReactElement` in the annotation.

- [ ] **Step 4: Run the tests to verify they pass**

Run (from `frontend/`): `npm run test`
Expected: PASS — all suites.

- [ ] **Step 5: Lint and build**

Run: `npm run lint && npm run build`
Expected: both pass.

- [ ] **Step 6: Visual check**

Start `npm run dev`, open `http://localhost:5173/`, confirm: tape band under the hero, code window with traffic lights/file tree/syntax colors, 4-cell stack strip with icons, nothing overlapping on a 1280px and a 480px viewport. Screenshot for the record. Stop the dev server.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/LandingPage.tsx frontend/src/pages/LandingPage.test.tsx
git commit -m "feat(frontend): landing hero code window, tape, and stack strip"
```

---

### Task 5: Verdict symbol badges + code editor frame

**Files:**
- Modify: `frontend/src/components/VerdictBadge.tsx`
- Modify: `frontend/src/components/VerdictBadge.test.tsx`
- Modify: `frontend/src/components/CodeEditor.tsx`

**Interfaces:**
- Consumes: `.verdict`, `.verdict__icon`, `.verdict--*` classes and `.code-editor` wrapper styles from Task 2.
- Produces: `VerdictBadge` renders two spans (icon with `aria-hidden="true"`, label) inside the outer `.verdict` span. The label text remains exactly `VERDICT_LABELS[verdict]` (so `getByText('Wrong Answer')` still matches) and `'Pending'` for null.

- [ ] **Step 1: Add the failing test**

Append inside the `describe('VerdictBadge', ...)` block in `frontend/src/components/VerdictBadge.test.tsx`:

```tsx
  it('prefixes the verdict with a symbol hidden from assistive tech', () => {
    render(<VerdictBadge verdict="ACCEPTED" />)
    const icon = screen.getByText('✓')
    expect(icon).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByText('Accepted')).toBeInTheDocument()
  })
```

- [ ] **Step 2: Run the test to verify it fails**

Run (from `frontend/`): `npx vitest run src/components/VerdictBadge.test.tsx`
Expected: FAIL — `✓` not found.

- [ ] **Step 3: Replace `frontend/src/components/VerdictBadge.tsx`**

```tsx
import type { Verdict } from '../api/types'
import { VERDICT_LABELS } from './verdictLabels'

const CLASS: Record<Verdict, string> = {
  ACCEPTED: 'verdict--ok',
  WRONG_ANSWER: 'verdict--bad',
  TIME_LIMIT_EXCEEDED: 'verdict--warn',
  MEMORY_LIMIT_EXCEEDED: 'verdict--warn',
  COMPILATION_ERROR: 'verdict--bad',
  RUNTIME_ERROR: 'verdict--bad',
  SYSTEM_ERROR: 'verdict--bad',
}

const ICON: Record<Verdict, string> = {
  ACCEPTED: '✓',
  WRONG_ANSWER: '✗',
  TIME_LIMIT_EXCEEDED: '!',
  MEMORY_LIMIT_EXCEEDED: '!',
  COMPILATION_ERROR: '✗',
  RUNTIME_ERROR: '▲',
  SYSTEM_ERROR: '▲',
}

export function VerdictBadge({ verdict }: { verdict: Verdict | null }) {
  if (!verdict) {
    return (
      <span className="verdict verdict--pending">
        <span className="verdict__icon" aria-hidden="true">
          …
        </span>
        <span>Pending</span>
      </span>
    )
  }
  return (
    <span className={`verdict ${CLASS[verdict]}`}>
      <span className="verdict__icon" aria-hidden="true">
        {ICON[verdict]}
      </span>
      <span>{VERDICT_LABELS[verdict]}</span>
    </span>
  )
}
```

- [ ] **Step 4: Update `frontend/src/components/CodeEditor.tsx`**

Change the return statement to wrap the editor:

```tsx
  return (
    <div className="code-editor">
      <CodeMirror
        value={value}
        height="320px"
        readOnly={readOnly}
        extensions={extensionFor(languageName)}
        onChange={(next) => onChange(next)}
      />
    </div>
  )
```

- [ ] **Step 5: Run the full test suite**

Run (from `frontend/`): `npm run test`
Expected: PASS — including the existing `VerdictBadge`, `SubmissionResultPage` (`findByText('Wrong Answer')`), and `HistoryPage` (`findByText('Accepted')`) tests, which match the inner label spans.

- [ ] **Step 6: Lint and build**

Run: `npm run lint && npm run build`
Expected: both pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/VerdictBadge.tsx frontend/src/components/VerdictBadge.test.tsx frontend/src/components/CodeEditor.tsx
git commit -m "feat(frontend): symbol verdict badges and editor frame"
```

---

### Task 6: PRD §31 update + full verification walkthrough

**Files:**
- Modify: `PRD.md` (§31 only)

**Interfaces:**
- Consumes: everything from Tasks 1–5.
- Produces: final verified state; no code interfaces.

- [ ] **Step 1: Update `PRD.md` §31**

Replace the body of section `## 31. Frontend Requirements` (currently a single paragraph) with:

```markdown
## 31. Frontend Requirements

React 18 + TypeScript, component-based, feature-folder structure (`pages/`, `components/`, `hooks/`, `api/`, `types/`). Key screens: landing page at `/` (hero with a mock editor window and a stack strip; CTAs to browse problems or register), problem list at `/problems` (filter/paginate), problem detail (statement + code editor, e.g. Monaco/CodeMirror + language selector + submit button), submission result view (status polling with backoff, verdict + failed test index), submission history table, admin problem editor, admin test case manager. State: React Query (or SWR) for server state/polling, no need for Redux given the app's shape. Auth: access token in memory (not `localStorage`, to reduce XSS token-theft surface), refresh token flow handled via a silent-refresh interceptor.

Visual system: a light "blueprint/brutalist print" theme — cream paper background with a faint grid, 1px ink borders, sharp corners (radius 0), hazard-tape divider bands, serif display headings (Instrument Serif), Inter for body/UI, JetBrains Mono for code — all self-hosted (SIL OFL), served from `/fonts`. The chrome is monochrome (black/white/grays); verdicts use muted functional colors (deep green / brick red / dark amber) plus symbol prefixes so state is readable without color. Routing: `/` is the landing page; the problem list lives at `/problems`.
```

- [ ] **Step 2: Run the full frontend definition of done**

Run (from `frontend/`): `npm run lint && npm run test && npm run build`
Expected: all pass, zero failures.

- [ ] **Step 3: Full manual walkthrough**

Start `npm run dev` (and the backend stack if available via `docker compose -f infrastructure/docker-compose.yml up -d` per the repo README). With browser tooling:

1. `/` — landing: hero, tape, code window, stack strip, no console errors.
2. `/problems` — list renders (or graceful error banner if backend is down).
3. `/login`, `/register` — forms render with the new theme.
4. With backend: log in → `/problems` list → open a problem → submit panel (CodeMirror light with ink border) → submission result (timeline + symbol badges) → `/submissions` history.
5. Admin (if an admin account is available): `/admin/problems`, `/admin/submissions`, `/admin/queue`.

Screenshot key screens for the record. Note in the final summary which checks ran with the backend and which were frontend-only.

- [ ] **Step 4: Summarize changed files and verification (AGENTS.md §20.9)**

Post a short summary: files created/modified, test results, build result, which manual checks passed, and anything left unverified (e.g., backend-dependent screens if the backend was not running).

- [ ] **Step 5: Commit**

```bash
git add PRD.md
git commit -m "docs(prd): describe blueprint frontend theme, landing page, and routes"
```

---

## Plan Self-Review

**Spec coverage check (spec section → task):**

- §4.1 color tokens → Task 2 Step 1. ✔
- §4.2 typography/fonts → Task 1 (files, preloads) + Task 2 (scale rules). ✔
- §4.3 layout primitives (grid, frame, tape, footer) → Task 2. ✔
- §4.4 component treatment → Task 2 (CSS) + Task 3 (NavBar) + Task 5 (badge/editor). ✔
- §4.5 landing page → Task 3 (hero/CTAs) + Task 4 (window/strip/tape). ✔
- §5 routing & integration → Task 3. ✔
- §6 files touched → matches File Structure table. ✔
- §7 testing & verification → Tasks 1–6 verification steps. ✔
- §8 documentation → Task 6. ✔
- §9 risks (fonts payload, route move, contrast, no code execution) → addressed by design; no task needed. ✔

**Placeholder scan:** no TBD/TODO/"handle edge cases" steps; every code step carries complete code; every command has expected output. The one conditional note (JSX.Element fallback) states the exact alternative. ✔

**Type consistency:** `Token` tuple, `SNIPPET`, `STACK` used consistently within Task 4; `LandingPage` export name matches imports in `App.tsx` and tests; class names in TSX match Task 2's CSS (`.nav__mark`, `.btn--solid`, `.hero__title-faded`, `.code-window__*`, `.stack-strip__*`, `.verdict__icon`, `.code-editor`, `.frame`, `.footer`). Token names in CSS match Task 2 Step 1 exactly. ✔

**Known accepted risks (from spec §9):** `LoginPage.test.tsx` required a fixture update because the login fallback moved; `VerdictBadge` markup split keeps all text queries stable; CodeMirror gutter override may lose to uiw's injected theme styles in edge cases — visual check in Task 6 covers it, and the border wrapper alone still delivers the design.
