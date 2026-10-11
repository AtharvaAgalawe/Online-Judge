# Codeveil-Style Frontend Restyle — Design

Date: 2026-10-11
Status: Approved in chat; pending user review of this written spec
Scope: `frontend/` only (React + TypeScript + Vite). No backend, API, or schema changes.

## 1. Context

The Online Judge frontend currently uses a dark navy theme (`frontend/src/styles/tokens.css`:
`#0f1117` background, `#5b8def` accent) with a single global stylesheet
(`frontend/src/index.css`, 207 lines) and `className`-based styling. All styling is
global CSS; there are no CSS modules, no CSS-in-JS, and no utility framework.

The user wants the app restyled to match the "Codeveil" reference design from
motionsites.ai (`https://motionsites.ai/?prompt=codeveil`, preview video
`https://pub-86dc5b5484314368ac5436a674b0d919.r2.dev/designs/codeveil_developer_tools.mp4`),
and additionally wants a new landing page at `/` carrying the Codeveil hero.

Reference design characteristics (observed from the preview):

- Cream/paper background with a faint blueprint grid
- Thin black borders; sharp corners; brutalist print/technical-drawing aesthetic
- Hazard-tape (45° black/white diagonal stripe) divider bands
- Large high-contrast serif display headline, with the second line faded gray
- Mock macOS-style code editor window (traffic lights, file tree, syntax-highlighted code)
- Grayscale logo strip of bordered cells
- Overall monochrome: black, white, grays

Decisions already made with the user:

1. Restyle the **whole app** (all existing pages), not just a landing page.
2. Verdicts: **monochrome chrome + functional verdict colors** (muted, print-like) so
   results remain scannable.
3. Fonts: **self-hosted** (no Google Fonts CDN at runtime).
4. Hero placement: **new landing route at `/`**; problem list moves to `/problems`.
5. CSS strategy: **Approach A — extend the existing global CSS** (`tokens.css` +
   `index.css`). No Tailwind, no CSS modules, no new styling dependency.

## 2. Goals

- Apply the Codeveil aesthetic consistently to every existing screen: problem list,
  problem detail (+ submit panel + CodeMirror), submission result, submission history,
  login, register, not-found, and all admin pages.
- Add a new landing page at `/` with the Codeveil hero, a mock code-editor window, and a
  bordered "stack" strip.
- Move the problem list to `/problems` and update all internal links/redirects.
- Keep all existing functionality and tests green; `npm run lint && npm run test &&
  npm run build` must pass.
- Update `PRD.md` §31 to describe the new design system, landing page, and routes.

## 3. Non-Goals

- No backend, API, or database changes.
- No new routing features beyond the `/` → landing and `/problems` → list moves.
- No dark-mode variant (the reference is light-only; the app becomes light-only).
- No new pages beyond the landing page (no marketing sections beyond hero + stack strip).
- No functional/behavioral changes to pages; this is a visual restyle plus route move.
- No animation library; motion is limited to CSS transitions (hover states).

## 4. Design System

### 4.1 Color tokens (replaces current `tokens.css` values)

| Token            | Value                | Use                                  |
| ---------------- | -------------------- | ------------------------------------ |
| `--paper`        | `#F2F0EB`            | Page background (cream)              |
| `--paper-raise`  | `#FAF9F6`            | Panels, inputs, code window          |
| `--ink`          | `#141414`            | Primary text; all borders            |
| `--ink-soft`     | `#5F5F5F`            | Muted/secondary text                 |
| `--ink-faint`    | `#9A9A9A`            | Faded hero second line               |
| `--grid-line`    | `rgba(20, 20, 20, .07)` | Blueprint grid strokes            |
| `--verdict-ok`   | `#1F6F3F`            | ACCEPTED                             |
| `--verdict-bad`  | `#A63A31`            | WRONG_ANSWER, RUNTIME_ERROR, errors  |
| `--verdict-warn` | `#8A6A00`            | TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED |
| `--verdict-pending` | `#5F5F5F`         | SUBMITTED/QUEUED/RUNNING etc.        |

- Border radius: `0` everywhere.
- Shadows: none, except a hard offset shadow (`3px 3px 0 var(--ink)`) on button hover.
- Focus: `2px solid var(--ink)` outline with `1px` offset.

Old token names (`--color-bg`, `--color-surface`, `--color-border`, `--color-text`,
`--color-muted`, `--color-accent`, `--color-success`, `--color-warn`, `--color-danger`)
are removed. Before removal, grep for `var(--color-` across `frontend/src` to confirm the
only references are in `index.css` (expected) and update any component inline styles found.

### 4.2 Typography (self-hosted)

Fonts (all SIL OFL 1.1, self-hosting permitted):

- **Instrument Serif** 400 + 400 italic — display (hero, page `h1`s).
- **Inter** 400 / 500 / 600 — body and UI text.
- **JetBrains Mono** 400 / 500 — code, verdict chips, micro-labels, file tree, nav brand.

Delivery: `woff2` files under `frontend/public/fonts/`, declared via `@font-face` in
`index.css` with `font-display: swap`. Dependency justification (AGENTS.md §17/§22):
self-hosted OFL fonts, no runtime third-party dependency, no build tooling added.

Scale:

- Hero: Instrument Serif, `clamp(2.5rem, 6vw, 4.25rem)`, line-height 1.05, letter-spacing -0.02em
- Page `h1`: Instrument Serif 2rem
- Body: Inter 15px / 1.6
- Micro-labels (nav links, table headers, tape-adjacent labels): Inter 11px uppercase, letter-spacing 0.08em
- Code: JetBrains Mono 13px

### 4.3 Layout primitives

- **Blueprint grid**: `body` background is `--paper` overlaid with two repeating
  linear-gradient layers — 24px minor grid + 120px major grid using `--grid-line`.
- **Print frame**: a `.frame` wrapper around the app content: `1px solid var(--ink)`
  border, 12px page margin (paper visible around the frame), `min-height: calc(100vh - 24px)`.
  Main content is centered with `max-width: 1100px`.
- **Hazard tape** `.tape`: 26px tall band, `repeating-linear-gradient(45deg, var(--ink) 0 7px,
  var(--paper) 7px 14px)`, with 1px ink top and bottom borders. Used under the NavBar on
  the landing page and between landing sections. On app pages, the NavBar bottom border is
  a plain 1px ink line (tape only on landing) to stay tasteful.
- **Footer**: slim, inside the frame — 1px top border, muted "Online Judge" text and a
  Problems link.

### 4.4 Component treatment

Class names remain the existing ones (`.nav`, `.problem-table`, `.verdict--ok`, etc.) so
markup churn is minimal; the CSS for each is rewritten.

- **NavBar**: circular ink-stroke glyph containing mono "OJ" + wordmark; links in Inter
  13px (Problems → `/problems`, My Submissions, Admin); anonymous right side: "Sign in"
  (outline) + "Register" (solid ink); authenticated: mono username + "Log out" (outline).
- **Buttons**: sharp, 1px ink border. Primary: ink background, paper text. Secondary:
  transparent/paper background, ink text. Hover: hard offset shadow `3px 3px 0 ink` plus
  `translate(-1px, -1px)`.
- **Inputs / selects / textareas**: `--paper-raise` background, 1px ink border, radius 0;
  labels as micro-labels.
- **Tables** (`ProblemTable`, history, admin tables): full 1px ink cell grid; header row
  micro-label styling; row hover = 4% ink tint.
- **Panels** (submit panel, result panel, code window, admin forms): `--paper-raise`
  background, 1px ink border, radius 0.
- **VerdictBadge**: mono uppercase chip, 1px ink border, 8% tinted background in the
  verdict color, symbol prefix (`✓` ACCEPTED, `✗` WRONG_ANSWER, `!` TLE/MLE,
  `▲` SYSTEM_ERROR/RE, `…` pending) so the state is readable without color.
- **StatusTimeline**: connected bordered cells; completed steps = ink background/paper text.
- **Spinner**: mono text with a blinking block cursor, e.g. `Loading problems ▍`.
- **ErrorBanner**: brick-red (`--verdict-bad`) 1px border with a thicker left bar, mono
  `ERROR` prefix, ink text.
- **CodeMirror**: default light theme (already the default; no theme prop), restyled with
  JetBrains Mono and an ink-border wrapper.

### 4.5 Landing page (`/`)

Structure top to bottom:

1. NavBar (global) + hazard tape.
2. Hero, centered:
   - Line 1 (Instrument Serif, ink): "Where Your Code Meets Its Judge"
   - Line 2 (same serif, `--ink-faint`): "And 'TLE' Wasn't Your Fault"
   - Sub-line (Inter, `--ink-soft`): "Compiled, sandboxed, and run in isolated
     containers — verdicts in seconds, not excuses."
   - Buttons: **[Browse Problems]** → `/problems` (primary) and, when anonymous,
     **[Create Account]** → `/register` (secondary); when authenticated, **[My Submissions]**
     → `/submissions` (secondary).
3. Mock code window (display-only fixture; nothing is executed):
   - Window chrome bar with three colored traffic-light dots (red/yellow/green, as in the
     reference) and a mono title.
   - Left file tree (mono): `src/`, `Main.java`, `solution.py`, `tests/`.
   - Right pane: ~14-line static Java snippet with hand-authored `<span>` classes for
     keyword/string/comment/type highlighting (no syntax-highlighting library added).
4. Hazard tape + stack strip: 4 bordered cells, centered mono labels — Java 21 ·
   Python 3.12 · Docker Sandbox · RabbitMQ.
5. Slim footer (global).

The landing page performs no API calls (static, trivially testable).

## 5. Routing & Integration Changes

| Change | File(s) |
| --- | --- |
| `/` renders new `LandingPage`; `ProblemListPage` moves to `/problems` | `src/App.tsx` |
| Brand link → `/`; "Problems" link → `/problems` | `src/components/NavBar.tsx` |
| Login fallback `from` value `/` → `/problems` | `src/pages/LoginPage.tsx` |
| Post-register redirect `/` → `/problems` | `src/pages/RegisterPage.tsx` |
| New `LandingPage` + test | `src/pages/LandingPage.tsx`, `src/pages/LandingPage.test.tsx` |

Logout continues to navigate to `/` (now the landing page) — acceptable and intended.

## 6. Files Touched (expected)

- Rewritten: `src/styles/tokens.css`, `src/index.css`
- New: `src/pages/LandingPage.tsx`, `src/pages/LandingPage.test.tsx`,
  `public/fonts/*.woff2` (7 files: Instrument Serif 400 + italic, Inter 400/500/600,
  JetBrains Mono 400/500), `index.html` (font preload hints; page title unchanged)
- Edited: `src/App.tsx`, `src/App.test.tsx`, `src/components/NavBar.tsx`,
  `src/pages/LoginPage.tsx`, `src/pages/RegisterPage.tsx`, `PRD.md` §31
- Reviewed and only edited if needed for the new look (markup wrappers only, no behavior):
  `src/components/{ProblemTable,VerdictBadge,StatusTimeline,Spinner,ErrorBanner,CodeEditor,Layout,Pagination,LanguageSelect}.tsx`,
  `src/pages/{ProblemDetailPage,SubmissionResultPage,HistoryPage}.tsx`,
  `src/pages/admin/*.tsx`
- Component tests updated only where DOM structure changes break queries (prefer keeping
  accessible roles/text stable to avoid churn).

## 7. Testing & Verification

- Update `src/App.test.tsx`: landing heading assertions; nav brand links to `/`;
  "Problems" link points to `/problems`.
- Add `src/pages/LandingPage.test.tsx`: renders headline; primary CTA links to
  `/problems`; CTA is `My Submissions` when authenticated, `Create Account` when anonymous.
- Fix any existing page test that mounts routes expecting the list at `/`
  (check `src/test/renderWithProviders.tsx` initial entries; adjust per test, not globally).
- Run: `npm run lint`, `npm run test`, `npm run build` — all must pass.
- Manual smoke via `npm run dev`: landing page renders without backend; with backend
  running, spot-check list/detail (CodeMirror light), login, history, result, admin.

## 8. Documentation

- `PRD.md` §31 (Frontend Requirements): describe the new visual system (light blueprint
  theme, self-hosted fonts, monochrome + functional verdict colors), the landing page at
  `/`, and the moved problem list at `/problems`.
- No API/OpenAPI changes; no AGENTS.md changes (no architecture or convention change).

## 9. Risks & Notes

- **Font files**: woff2 subsets are ~30–60KB each; total added payload ≈ 250–400KB.
  Acceptable for self-hosting; `font-display: swap` avoids invisible text.
- **Route move is user-visible**: `/` no longer lists problems. Login/register redirects
  and nav links are updated; any external bookmarks to `/` land on the landing page.
- **Test churn**: keep `getByRole` queries stable (headings, links, buttons keep their
  accessible names) to minimize test edits.
- **Contrast**: verify `--ink-soft` (#5F5F5F) on `--paper` (#F2F0EB) meets WCAG AA for
  body text (expected ratio ≈ 5.6:1); verdict colors chosen for AA on paper background.
- The sandbox/security rules (AGENTS.md §10) are untouched: the landing code window is a
  static display fixture containing our own snippet; no submitted code is ever rendered
  or executed in the frontend.
