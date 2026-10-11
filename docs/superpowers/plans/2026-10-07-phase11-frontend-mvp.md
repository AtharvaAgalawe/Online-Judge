# Phase 11 — Frontend MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a React + TypeScript SPA that lets a solver complete the full §6.1 journey (register/login → browse → open problem → edit code → submit → watch status → see verdict → history).

**Architecture:** A single-page app with three separated concerns: a typed `fetch` transport (`api/client.ts`) that owns auth headers and one-shot refresh-on-401; TanStack Query owning all server state and terminal-state polling; a small `AuthContext` owning only the in-memory access token + decoded user. Pages are thin; components are presentational.

**Tech Stack:** Vite 5.4.21, React 18.3.1, TypeScript 5.6.3, Vitest 2.1.9, Testing Library; react-router-dom 7.18.4, @tanstack/react-query 5.104.1, @uiw/react-codemirror 4.25.12, @codemirror/lang-java 6.0.2, @codemirror/lang-python 6.2.1.

**Spec:** `docs/superpowers/specs/2026-10-07-phase11-frontend-mvp-design.md`

## Global Constraints

- **No new dependencies beyond the five pinned above.** No styling framework, no MSW, no axios. Exact versions, no ranges (AGENTS §17).
- **Access token in memory only; refresh token in `localStorage` key `oj.refreshToken`.** Access token never persisted.
- **API base is a relative path (`/api/v1/...`)** served by the Vite dev proxy. No hardcoded host, no secrets in frontend.
- **Hidden test content is never rendered.** Only `failedTestCase.index`, `isSample`, per-case `verdict`, and `errorMessage` are shown.
- **Terminal statuses** are exactly `COMPLETED, COMPILATION_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR, SYSTEM_ERROR`.
- **Login uses `username`, not email.** JWT claims: `sub` (username), `uid` (number), `roles` (string[]).
- **Do NOT commit or push.** The user commits themselves. Every task ends by staging changes and giving a suggested Conventional Commits message.
- After every task, `npm run lint && npm run test && npm run build` must pass. Run commands with the frontend as working directory.
- Each task leaves the repo buildable and all prior tests green.

---

### Task 1: Dependencies + app shell + test harness

**Files:**
- Modify: `frontend/package.json`
- Create: `frontend/src/styles/tokens.css`
- Modify: `frontend/src/index.css`
- Create: `frontend/src/components/Spinner.tsx`
- Create: `frontend/src/components/NavBar.tsx`
- Create: `frontend/src/components/Layout.tsx`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/App.tsx`
- Create: `frontend/src/test/renderWithProviders.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: nothing.
- Produces: `renderWithProviders(ui: ReactElement, options?: { route?: string }): RenderResult`; `Layout` (renders `<NavBar/>` + `<Outlet/>`); `Spinner({ label?: string })`; route table rooted at `/`.

- [ ] **Step 1: Install the pinned dependencies**

Run (workdir `frontend`):
```
npm install react-router-dom@7.18.4 @tanstack/react-query@5.104.1 @uiw/react-codemirror@4.25.12 @codemirror/lang-java@6.0.2 @codemirror/lang-python@6.2.1
```

- [ ] **Step 2: Write the failing shell test**

Replace `frontend/src/App.test.tsx` with:

```tsx
import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import App from './App'
import { renderWithProviders } from './test/renderWithProviders'

describe('App shell', () => {
  it('renders the brand and the landing page', () => {
    renderWithProviders(<App />)
    expect(screen.getByRole('link', { name: /online judge/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /problems/i })).toBeInTheDocument()
  })
})
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `npm run test -- src/App.test.tsx`
Expected: FAIL — `renderWithProviders` module not found.

- [ ] **Step 4: Create the design tokens and base styles**

`frontend/src/styles/tokens.css`:

```css
:root {
  --color-bg: #0f1117;
  --color-surface: #171a23;
  --color-border: #2a2f3a;
  --color-text: #e6e8ee;
  --color-muted: #9aa3b2;
  --color-accent: #5b8def;
  --color-success: #2ea043;
  --color-warn: #d29922;
  --color-danger: #e5534b;
  --radius: 8px;
  --space-1: 0.25rem;
  --space-2: 0.5rem;
  --space-3: 1rem;
  --space-4: 1.5rem;
  --space-5: 2.5rem;
  --font-mono: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
```

Replace `frontend/src/index.css` with:

```css
@import './styles/tokens.css';

:root {
  font-family: system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
  color-scheme: dark;
}

body {
  margin: 0;
  min-height: 100vh;
  background: var(--color-bg);
  color: var(--color-text);
}

a {
  color: var(--color-accent);
}

.nav {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--color-border);
  background: var(--color-surface);
}

.nav__brand {
  font-weight: 700;
  color: var(--color-text);
  text-decoration: none;
}

.nav__spacer {
  flex: 1;
}

.layout__main {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-4);
}

.spinner {
  padding: var(--space-4);
  color: var(--color-muted);
}
```

- [ ] **Step 5: Create `Spinner`, `NavBar`, and `Layout`**

`frontend/src/components/Spinner.tsx`:

```tsx
export function Spinner({ label }: { label?: string }) {
  return (
    <div className="spinner" role="status" aria-live="polite">
      {label ?? 'Loading'}…
    </div>
  )
}
```

`frontend/src/components/NavBar.tsx` (auth wiring arrives in Task 3; static for now):

```tsx
import { Link } from 'react-router-dom'

export function NavBar() {
  return (
    <nav className="nav">
      <Link to="/" className="nav__brand">
        Online Judge
      </Link>
      <span className="nav__spacer" />
      <Link to="/">Problems</Link>
    </nav>
  )
}
```

`frontend/src/components/Layout.tsx`:

```tsx
import { Outlet } from 'react-router-dom'
import { NavBar } from './NavBar'

export function Layout() {
  return (
    <>
      <NavBar />
      <main className="layout__main">
        <Outlet />
      </main>
    </>
  )
}
```

- [ ] **Step 6: Create the router + providers and landing route**

`frontend/src/App.tsx`:

```tsx
import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'

function LandingPage() {
  return (
    <section>
      <h1>Problems</h1>
      <p>Problem browsing arrives in the next task.</p>
    </section>
  )
}

function NotFoundPage() {
  return (
    <section>
      <h1>Not found</h1>
    </section>
  )
}

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<LandingPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
```

`frontend/src/main.tsx`:

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
)
```

- [ ] **Step 7: Create the test render helper**

`frontend/src/test/renderWithProviders.tsx`:

```tsx
import type { ReactElement } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

export function renderWithProviders(
  ui: ReactElement,
  options: { route?: string } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[options.route ?? '/']}>{ui}</MemoryRouter>
    </QueryClientProvider>,
  )
}
```

- [ ] **Step 8: Run the shell test to verify it passes**

Run: `npm run test -- src/App.test.tsx`
Expected: PASS.

- [ ] **Step 9: Run the full gate**

Run: `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 10: Stage (do not commit) and suggest a message**

```
git add frontend/package.json frontend/package-lock.json frontend/src
git status
```
Suggested message: `feat(frontend): add app shell with router, query client, and design tokens`

---

### Task 2: API transport layer (`types.ts` + `client.ts`)

**Files:**
- Create: `frontend/src/api/types.ts`
- Create: `frontend/src/api/client.ts`
- Test: `frontend/src/api/client.test.ts`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `ApiError extends Error` with readonly `status`, `title`, `detail`.
  - `configureAuthHooks(hooks: { getAccessToken: () => string | null; refreshAccessToken: () => Promise<string | null>; onSessionExpired: () => void }): void`
  - `parseError(response: Response): Promise<ApiError>`
  - `apiFetch<T>(path: string, init?: RequestInit, allowRetry?: boolean): Promise<T>`
  - Types: `SubmissionStatus`, `Verdict`, `Difficulty`, `isTerminalStatus`, `UserResponse`, `Registration`, `AuthTokensResponse`, `AccessTokenResponse`, `ProblemSummary`, `ProblemDetail`, `SampleTestCase`, `ProblemStats`, `Language`, `CreateSubmission`, `SubmissionAccepted`, `SubmissionStatusResponse`, `SubmissionDetail`, `SubmissionSummary`, `FailedTestCase`, `CaseResult`, `PagedResponse<T>`.

- [ ] **Step 1: Write the failing transport test**

`frontend/src/api/client.test.ts`:

```ts
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, apiFetch, configureAuthHooks } from './client'

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('apiFetch', () => {
  beforeEach(() => {
    configureAuthHooks({
      getAccessToken: () => 'token-1',
      refreshAccessToken: vi.fn(async () => null),
      onSessionExpired: vi.fn(),
    })
  })

  afterEach(() => vi.restoreAllMocks())

  it('attaches the bearer token and parses JSON', async () => {
    const fetchMock = vi.fn(async () => jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)
    const result = await apiFetch<{ ok: boolean }>('/api/v1/ping')
    expect(result).toEqual({ ok: true })
    const [, init] = fetchMock.mock.calls[0]
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer token-1')
  })

  it('throws ApiError parsed from problem+json', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({ title: 'Not Found', detail: 'no such problem' }, 404),
      ),
    )
    await expect(apiFetch('/api/v1/problems/none')).rejects.toMatchObject({
      status: 404,
      title: 'Not Found',
      detail: 'no such problem',
    })
  })

  it('refreshes once on 401 and retries the request', async () => {
    const refreshAccessToken = vi.fn(async () => 'token-2')
    configureAuthHooks({
      getAccessToken: () => 'expired',
      refreshAccessToken,
      onSessionExpired: vi.fn(),
    })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({}, 401))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiFetch<{ ok: boolean }>('/api/v1/me')).resolves.toEqual({ ok: true })
    expect(refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe(
      'Bearer token-2',
    )
  })

  it('signals session expiry when refresh fails', async () => {
    const onSessionExpired = vi.fn()
    configureAuthHooks({
      getAccessToken: () => 'expired',
      refreshAccessToken: vi.fn(async () => null),
      onSessionExpired,
    })
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse({}, 401)))
    await expect(apiFetch('/api/v1/me')).rejects.toBeInstanceOf(ApiError)
    expect(onSessionExpired).toHaveBeenCalledTimes(1)
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `npm run test -- src/api/client.test.ts`
Expected: FAIL — `./client` module not found.

- [ ] **Step 3: Create `types.ts`**

`frontend/src/api/types.ts`:

```ts
export type SubmissionStatus =
  | 'SUBMITTED'
  | 'QUEUED'
  | 'PICKED_UP'
  | 'COMPILING'
  | 'RUNNING'
  | 'EVALUATING'
  | 'COMPLETED'
  | 'COMPILATION_ERROR'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'SYSTEM_ERROR'

export const TERMINAL_STATUSES: readonly SubmissionStatus[] = [
  'COMPLETED',
  'COMPILATION_ERROR',
  'TIME_LIMIT_EXCEEDED',
  'MEMORY_LIMIT_EXCEEDED',
  'RUNTIME_ERROR',
  'SYSTEM_ERROR',
]

export function isTerminalStatus(status: SubmissionStatus): boolean {
  return TERMINAL_STATUSES.includes(status)
}

export type Verdict =
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'COMPILATION_ERROR'
  | 'RUNTIME_ERROR'
  | 'SYSTEM_ERROR'

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'

export interface UserResponse {
  id: number
  username: string
  email: string
}

export interface Registration {
  username: string
  email: string
  password: string
}

export interface AuthTokensResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface AccessTokenResponse {
  accessToken: string
  expiresIn: number
}

export interface ProblemSummary {
  id: number
  slug: string
  title: string
  difficulty: Difficulty
  tags: string[]
  acceptanceRate: number
}

export interface SampleTestCase {
  input: string
  expectedOutput: string
  order: number
}

export interface ProblemDetail {
  id: number
  slug: string
  title: string
  statement: string
  difficulty: Difficulty
  timeLimitMs: number
  memoryLimitKb: number
  tags: string[]
  sampleTestCases: SampleTestCase[]
}

export interface ProblemStats {
  totalSubmissions: number
  acceptedCount: number
  acceptanceRate: number
}

export interface Language {
  id: number
  name: string
}

export interface CreateSubmission {
  problemId: number
  languageId: number
  sourceCode: string
}

export interface SubmissionAccepted {
  submissionId: number
  status: SubmissionStatus
}

export interface SubmissionStatusResponse {
  status: SubmissionStatus
  verdict: Verdict | null
}

export interface FailedTestCase {
  index: number
  isSample: boolean
}

export interface CaseResult {
  index: number
  isSample: boolean
  verdict: Verdict
  timeUsedMs: number | null
  memoryUsedKb: number | null
}

export interface SubmissionDetail {
  id: number
  problemId: number
  problemSlug: string
  languageName: string
  status: SubmissionStatus
  verdict: Verdict | null
  timeUsedMs: number | null
  memoryUsedKb: number | null
  errorMessage: string | null
  sourceCode: string
  submittedAt: string
  judgedAt: string | null
  failedTestCase: FailedTestCase | null
  results: CaseResult[]
}

export interface SubmissionSummary {
  id: number
  problemId: number
  problemSlug: string
  languageName: string
  status: SubmissionStatus
  verdict: Verdict | null
  timeUsedMs: number | null
  memoryUsedKb: number | null
  submittedAt: string
}

export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
```

- [ ] **Step 4: Create `client.ts`**

`frontend/src/api/client.ts`:

```ts
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly detail: string

  constructor(status: number, title: string, detail: string) {
    super(detail || title)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.detail = detail
  }
}

interface AuthHooks {
  getAccessToken: () => string | null
  refreshAccessToken: () => Promise<string | null>
  onSessionExpired: () => void
}

let hooks: AuthHooks = {
  getAccessToken: () => null,
  refreshAccessToken: async () => null,
  onSessionExpired: () => {},
}

export function configureAuthHooks(next: AuthHooks): void {
  hooks = next
}

let refreshInFlight: Promise<string | null> | null = null

function refreshOnce(): Promise<string | null> {
  if (!refreshInFlight) {
    refreshInFlight = hooks.refreshAccessToken().finally(() => {
      refreshInFlight = null
    })
  }
  return refreshInFlight
}

export async function parseError(response: Response): Promise<ApiError> {
  let title = 'Request failed'
  let detail = `HTTP ${response.status}`
  try {
    const body = (await response.json()) as { title?: string; detail?: string }
    if (typeof body.title === 'string') title = body.title
    if (typeof body.detail === 'string') detail = body.detail
  } catch {
    // Non-JSON error body; keep HTTP-derived defaults.
  }
  return new ApiError(response.status, title, detail)
}

export async function apiFetch<T>(
  path: string,
  init: RequestInit = {},
  allowRetry = true,
): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  const token = hooks.getAccessToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)

  const response = await fetch(path, { ...init, headers })

  if (response.status === 401 && allowRetry) {
    const refreshed = await refreshOnce()
    if (refreshed) return apiFetch<T>(path, init, false)
    hooks.onSessionExpired()
    throw await parseError(response)
  }
  if (!response.ok) throw await parseError(response)
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
```

- [ ] **Step 5: Run the transport test to verify it passes**

Run: `npm run test -- src/api/client.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 6: Run the full gate**

Run: `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 7: Stage (do not commit) and suggest a message**

```
git add frontend/src/api
```
Suggested message: `feat(frontend): add typed API transport with one-shot 401 refresh`

---

### Task 3: Auth flow (decodeJwt, auth API, AuthContext, RequireAuth, Login/Register, NavBar)

**Files:**
- Create: `frontend/src/auth/decodeJwt.ts`
- Test: `frontend/src/auth/decodeJwt.test.ts`
- Create: `frontend/src/api/auth.ts`
- Create: `frontend/src/auth/AuthContext.tsx`
- Test: `frontend/src/auth/AuthContext.test.tsx`
- Create: `frontend/src/auth/RequireAuth.tsx`
- Create: `frontend/src/helpers/apiError.ts`
- Create: `frontend/src/components/ErrorBanner.tsx`
- Create: `frontend/src/pages/LoginPage.tsx`
- Create: `frontend/src/pages/RegisterPage.tsx`
- Modify: `frontend/src/components/NavBar.tsx`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/App.tsx`
- Test: `frontend/src/pages/LoginPage.test.tsx`
- Test: `frontend/src/auth/RequireAuth.test.tsx`

**Interfaces:**
- Consumes: `apiFetch`, `parseError`, `ApiError` (Task 2); `Registration`, `AuthTokensResponse` (Task 2).
- Produces:
  - `decodeJwt(token: string): JwtUser | null` where `JwtUser = { id: number; username: string; roles: string[] }`.
  - `authApi`: `login`, `register`, `refresh`, `logout`.
  - `AuthProvider`, `useAuth(): { status: 'loading' | 'authenticated' | 'anonymous'; user: JwtUser | null; login(u,p): Promise<void>; register(r: Registration): Promise<void>; logout(): Promise<void> }`.
  - `RequireAuth({ children }: { children: ReactElement })`.
  - `ErrorBanner({ error }: { error: unknown })`.
  - `messageOf(error: unknown): string`.

- [ ] **Step 1: Write the failing `decodeJwt` test**

`frontend/src/auth/decodeJwt.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { decodeJwt } from './decodeJwt'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

describe('decodeJwt', () => {
  it('extracts sub, uid, and roles from the payload', () => {
    const token = `header.${encodeSegment({ sub: 'alice', uid: 7, roles: ['ROLE_USER'] })}.sig`
    expect(decodeJwt(token)).toEqual({ id: 7, username: 'alice', roles: ['ROLE_USER'] })
  })

  it('defaults roles to an empty array when absent', () => {
    const token = `header.${encodeSegment({ sub: 'bob', uid: 3 })}.sig`
    expect(decodeJwt(token)).toEqual({ id: 3, username: 'bob', roles: [] })
  })

  it('returns null for malformed tokens', () => {
    expect(decodeJwt('not-a-jwt')).toBeNull()
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test -- src/auth/decodeJwt.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `decodeJwt`**

`frontend/src/auth/decodeJwt.ts`:

```ts
export interface JwtUser {
  id: number
  username: string
  roles: string[]
}

function base64UrlDecode(segment: string): string {
  const normalized = segment.replace(/-/g, '+').replace(/_/g, '/')
  const padding = normalized.length % 4 === 0 ? '' : '='.repeat(4 - (normalized.length % 4))
  return atob(normalized + padding)
}

export function decodeJwt(token: string): JwtUser | null {
  const parts = token.split('.')
  if (parts.length < 2) return null
  try {
    const payload = JSON.parse(base64UrlDecode(parts[1])) as {
      sub?: unknown
      uid?: unknown
      roles?: unknown
    }
    if (typeof payload.sub !== 'string' || typeof payload.uid !== 'number') return null
    const roles = Array.isArray(payload.roles)
      ? payload.roles.filter((role): role is string => typeof role === 'string')
      : []
    return { id: payload.uid, username: payload.sub, roles }
  } catch {
    return null
  }
}
```

- [ ] **Step 4: Run it to verify it passes; then create `api/auth.ts` and helpers**

Run: `npm run test -- src/auth/decodeJwt.test.ts` → PASS.

`frontend/src/api/auth.ts`:

```ts
import { apiFetch, parseError } from './client'
import type {
  AccessTokenResponse,
  AuthTokensResponse,
  Registration,
  UserResponse,
} from './types'

interface Credentials {
  username: string
  password: string
}

async function rawPost<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!response.ok) throw await parseError(response)
  return (await response.json()) as T
}

export function login(credentials: Credentials): Promise<AuthTokensResponse> {
  return rawPost<AuthTokensResponse>('/api/v1/auth/login', credentials)
}

export function register(registration: Registration): Promise<UserResponse> {
  return rawPost<UserResponse>('/api/v1/auth/register', registration)
}

export function refresh(refreshToken: string): Promise<AccessTokenResponse> {
  return rawPost<AccessTokenResponse>('/api/v1/auth/refresh', { refreshToken })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/v1/auth/logout', { method: 'POST' })
}
```

`frontend/src/helpers/apiError.ts`:

```ts
import { ApiError } from '../api/client'

export function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.detail || error.title
  if (error instanceof Error) return error.message
  return 'Something went wrong'
}
```

`frontend/src/components/ErrorBanner.tsx`:

```tsx
import { messageOf } from '../helpers/apiError'

export function ErrorBanner({ error }: { error: unknown }) {
  return (
    <div role="alert" className="error-banner">
      {messageOf(error)}
    </div>
  )
}
```

Add to `frontend/src/index.css` (append):

```css
.error-banner {
  padding: var(--space-3);
  border: 1px solid var(--color-danger);
  border-radius: var(--radius);
  background: rgba(229, 83, 75, 0.12);
  color: var(--color-text);
  margin: var(--space-3) 0;
}
```

- [ ] **Step 5: Write the failing AuthContext test**

`frontend/src/auth/AuthContext.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, useAuth } from './AuthContext'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

const ACCESS = `h.${encodeSegment({ sub: 'alice', uid: 1, roles: ['ROLE_USER'] })}.s`

function Probe() {
  const { status, user, login } = useAuth()
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="user">{user?.username ?? ''}</span>
      <button onClick={() => void login('alice', 'secret123')}>login</button>
    </div>
  )
}

describe('AuthContext', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('logins, stores the refresh token, and exposes the decoded user', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(
          JSON.stringify({ accessToken: ACCESS, refreshToken: 'refresh-1', expiresIn: 900 }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await act(async () => {
      screen.getByRole('button', { name: 'login' }).click()
    })
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'))
    expect(screen.getByTestId('user').textContent).toBe('alice')
    expect(localStorage.getItem('oj.refreshToken')).toBe('refresh-1')
  })

  it('restores a session from a stored refresh token on boot', async () => {
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
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    )
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'))
  })
})
```

- [ ] **Step 6: Run it to verify it fails**

Run: `npm run test -- src/auth/AuthContext.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 7: Implement `AuthContext`**

`frontend/src/auth/AuthContext.tsx`:

```tsx
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { configureAuthHooks } from '../api/client'
import * as authApi from '../api/auth'
import { decodeJwt } from './decodeJwt'
import type { JwtUser } from './decodeJwt'
import type { Registration } from '../api/types'

export const REFRESH_TOKEN_KEY = 'oj.refreshToken'

type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  status: AuthStatus
  user: JwtUser | null
  login: (username: string, password: string) => Promise<void>
  register: (registration: Registration) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const tokenRef = useRef<string | null>(null)
  const [user, setUser] = useState<JwtUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  const applyToken = useCallback((token: string | null) => {
    tokenRef.current = token
    setUser(token ? decodeJwt(token) : null)
    setStatus(token ? 'authenticated' : 'anonymous')
  }, [])

  const clearSession = useCallback(() => {
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    applyToken(null)
  }, [applyToken])

  const refreshAccessToken = useCallback(async (): Promise<string | null> => {
    const stored = localStorage.getItem(REFRESH_TOKEN_KEY)
    if (!stored) return null
    try {
      const response = await authApi.refresh(stored)
      applyToken(response.accessToken)
      return response.accessToken
    } catch {
      clearSession()
      return null
    }
  }, [applyToken, clearSession])

  useEffect(() => {
    configureAuthHooks({
      getAccessToken: () => tokenRef.current,
      refreshAccessToken,
      onSessionExpired: clearSession,
    })
  }, [refreshAccessToken, clearSession])

  useEffect(() => {
    void refreshAccessToken()
  }, [refreshAccessToken])

  const login = useCallback(
    async (username: string, password: string) => {
      const tokens = await authApi.login({ username, password })
      localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken)
      applyToken(tokens.accessToken)
    },
    [applyToken],
  )

  const register = useCallback(
    async (registration: Registration) => {
      await authApi.register(registration)
      await login(registration.username, registration.password)
    },
    [login],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // Logging out locally regardless of the server response.
    }
    clearSession()
  }, [clearSession])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, register, logout }),
    [status, user, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within an AuthProvider')
  return context
}
```

- [ ] **Step 8: Run the AuthContext test to verify it passes**

Run: `npm run test -- src/auth/AuthContext.test.tsx`
Expected: PASS (2 tests).

- [ ] **Step 9: Implement `RequireAuth`, pages, NavBar, and wire routes**

`frontend/src/auth/RequireAuth.tsx`:

```tsx
import type { ReactElement } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { Spinner } from '../components/Spinner'
import { useAuth } from './AuthContext'

export function RequireAuth({ children }: { children: ReactElement }) {
  const { status } = useAuth()
  const location = useLocation()
  if (status === 'loading') return <Spinner label="Checking session" />
  if (status !== 'authenticated') {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />
  }
  return children
}
```

`frontend/src/pages/LoginPage.tsx`:

```tsx
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ErrorBanner } from '../components/ErrorBanner'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  const from = (location.state as { from?: string } | null)?.from ?? '/'

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username, password)
      navigate(from, { replace: true })
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <h1>Log in</h1>
      {error !== null && <ErrorBanner error={error} />}
      <form onSubmit={onSubmit}>
        <label>
          Username
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </section>
  )
}
```

`frontend/src/pages/RegisterPage.tsx`:

```tsx
import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { ErrorBanner } from '../components/ErrorBanner'
import { useAuth } from '../auth/AuthContext'

export function RegisterPage() {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await register({ username, email, password })
      navigate('/', { replace: true })
    } catch (caught) {
      setError(caught)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section>
      <h1>Register</h1>
      {error !== null && <ErrorBanner error={error} />}
      <form onSubmit={onSubmit}>
        <label>
          Username
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
            minLength={3}
            maxLength={30}
            pattern="[a-zA-Z0-9]+"
          />
        </label>
        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            maxLength={72}
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p>
        Already registered? <Link to="/login">Log in</Link>
      </p>
    </section>
  )
}
```

`frontend/src/components/NavBar.tsx` (replace):

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
        Online Judge
      </Link>
      <Link to="/">Problems</Link>
      {status === 'authenticated' && <Link to="/submissions">My Submissions</Link>}
      <span className="nav__spacer" />
      {status === 'authenticated' ? (
        <>
          <span className="nav__user">{user?.username}</span>
          <button type="button" onClick={onLogout}>
            Log out
          </button>
        </>
      ) : (
        <>
          <Link to="/login">Log in</Link>
          <Link to="/register">Register</Link>
        </>
      )}
    </nav>
  )
}
```

`frontend/src/main.tsx` (replace) — add `AuthProvider` inside `BrowserRouter`:

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  </React.StrictMode>,
)
```

`frontend/src/App.tsx` (replace) — add login/register routes:

```tsx
import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

function LandingPage() {
  return (
    <section>
      <h1>Problems</h1>
      <p>Problem browsing arrives in the next task.</p>
    </section>
  )
}

function NotFoundPage() {
  return (
    <section>
      <h1>Not found</h1>
    </section>
  )
}

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<LandingPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
```

`frontend/src/App.test.tsx` — App no longer renders without an AuthProvider; wrap it. Replace the `renderWithProviders(<App />)` call with a helper that also mounts `AuthProvider`, and stub `fetch` so the boot refresh is a no-op:

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

  it('renders the brand and the landing page', () => {
    renderWithProviders(
      <AuthProvider>
        <App />
      </AuthProvider>,
    )
    expect(screen.getByRole('link', { name: /online judge/i })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /problems/i })).toBeInTheDocument()
  })
})
```

Also append to `frontend/src/index.css`:

```css
.nav__user {
  color: var(--color-muted);
}

input,
button {
  font: inherit;
}

form label {
  display: block;
  margin: var(--space-2) 0;
}

form input {
  display: block;
  width: 100%;
  max-width: 320px;
  padding: var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-surface);
  color: var(--color-text);
}
```

- [ ] **Step 10: Write the failing `RequireAuth` + `LoginPage` tests**

`frontend/src/auth/RequireAuth.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from './AuthContext'
import { RequireAuth } from './RequireAuth'
import { renderWithProviders } from '../test/renderWithProviders'

describe('RequireAuth', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('redirects anonymous users to the login page', async () => {
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route
            path="/secret"
            element={
              <RequireAuth>
                <div>secret</div>
              </RequireAuth>
            }
          />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </AuthProvider>,
      { route: '/secret' },
    )
    await waitFor(() => expect(screen.getByText('login page')).toBeInTheDocument())
  })
})
```

`frontend/src/pages/LoginPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { LoginPage } from './LoginPage'
import { renderWithProviders } from '../test/renderWithProviders'

function encodeSegment(value: object): string {
  return btoa(JSON.stringify(value)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

describe('LoginPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('logs in and navigates home', async () => {
    const access = `h.${encodeSegment({ sub: 'alice', uid: 1, roles: [] })}.s`
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ accessToken: access, refreshToken: 'r', expiresIn: 900 }), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<div>home page</div>} />
        </Routes>
      </AuthProvider>,
      { route: '/login' },
    )
    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'alice' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))
    await waitFor(() => expect(screen.getByText('home page')).toBeInTheDocument())
  })
})
```

- [ ] **Step 11: Run the new tests to verify they pass**

Run: `npm run test -- src/auth src/pages/LoginPage.test.tsx src/App.test.tsx`
Expected: PASS.

- [ ] **Step 12: Run the full gate**

Run: `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 13: Stage (do not commit) and suggest a message**

```
git add frontend/src
```
Suggested message: `feat(frontend): add auth flow with JWT decode, silent refresh, and gated routes`

---

### Task 4: Problem browsing (list + detail + stats)

**Files:**
- Create: `frontend/src/api/problems.ts`
- Create: `frontend/src/api/languages.ts`
- Create: `frontend/src/components/Pagination.tsx`
- Create: `frontend/src/components/ProblemTable.tsx`
- Create: `frontend/src/pages/ProblemListPage.tsx`
- Test: `frontend/src/pages/ProblemListPage.test.tsx`
- Create: `frontend/src/pages/ProblemDetailPage.tsx`
- Test: `frontend/src/pages/ProblemDetailPage.test.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `apiFetch` (Task 2); types from Task 2.
- Produces:
  - `fetchProblems(filters: ProblemFilters): Promise<PagedResponse<ProblemSummary>>`; `ProblemFilters = { page?, size?, difficulty?: Difficulty | '', tag?, search? }`.
  - `fetchProblem(slug: string): Promise<ProblemDetail>`, `fetchProblemStats(slug: string): Promise<ProblemStats>`.
  - `fetchLanguages(): Promise<Language[]>`.
  - `Pagination({ page, totalPages, onPageChange })`, `ProblemTable({ problems, onSelect })`.
  - Routes `/` → `ProblemListPage`, `/problems/:slug` → `ProblemDetailPage`.

- [ ] **Step 1: Write the failing list-page test**

`frontend/src/pages/ProblemListPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import { ProblemListPage } from './ProblemListPage'
import { renderWithProviders } from '../test/renderWithProviders'

const PAGE = {
  content: [
    {
      id: 1,
      slug: 'two-sum',
      title: 'Two Sum',
      difficulty: 'EASY',
      tags: ['array'],
      acceptanceRate: 72.5,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

describe('ProblemListPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders problems and includes the difficulty filter in the request', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify(PAGE), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)

    renderWithProviders(<ProblemListPage />)
    expect(await screen.findByText('Two Sum')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Difficulty'), { target: { value: 'EASY' } })
    await waitFor(() => {
      const calledWithEasy = fetchMock.mock.calls.some((call) =>
        String(call[0]).includes('difficulty=EASY'),
      )
      expect(calledWithEasy).toBe(true)
    })
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test -- src/pages/ProblemListPage.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `api/problems.ts`, `api/languages.ts`, `Pagination`, `ProblemTable`**

`frontend/src/api/problems.ts`:

```ts
import { apiFetch } from './client'
import type {
  Difficulty,
  PagedResponse,
  ProblemDetail,
  ProblemStats,
  ProblemSummary,
} from './types'

export interface ProblemFilters {
  page?: number
  size?: number
  difficulty?: Difficulty | ''
  tag?: string
  search?: string
}

export function fetchProblems(
  filters: ProblemFilters,
): Promise<PagedResponse<ProblemSummary>> {
  const params = new URLSearchParams()
  if (filters.page !== undefined) params.set('page', String(filters.page))
  if (filters.size !== undefined) params.set('size', String(filters.size))
  if (filters.difficulty) params.set('difficulty', filters.difficulty)
  if (filters.tag) params.set('tag', filters.tag)
  if (filters.search) params.set('search', filters.search)
  return apiFetch<PagedResponse<ProblemSummary>>(`/api/v1/problems?${params.toString()}`)
}

export function fetchProblem(slug: string): Promise<ProblemDetail> {
  return apiFetch<ProblemDetail>(`/api/v1/problems/${encodeURIComponent(slug)}`)
}

export function fetchProblemStats(slug: string): Promise<ProblemStats> {
  return apiFetch<ProblemStats>(`/api/v1/problems/${encodeURIComponent(slug)}/stats`)
}
```

`frontend/src/api/languages.ts`:

```ts
import { apiFetch } from './client'
import type { Language } from './types'

export function fetchLanguages(): Promise<Language[]> {
  return apiFetch<Language[]>('/api/v1/languages')
}
```

`frontend/src/components/Pagination.tsx`:

```tsx
export function Pagination({
  page,
  totalPages,
  onPageChange,
}: {
  page: number
  totalPages: number
  onPageChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <div className="pagination">
      <button type="button" disabled={page <= 0} onClick={() => onPageChange(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {totalPages}
      </span>
      <button
        type="button"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </button>
    </div>
  )
}
```

`frontend/src/components/ProblemTable.tsx`:

```tsx
import type { ProblemSummary } from '../api/types'

export function ProblemTable({
  problems,
  onSelect,
}: {
  problems: ProblemSummary[]
  onSelect: (slug: string) => void
}) {
  return (
    <table className="problem-table">
      <thead>
        <tr>
          <th>Title</th>
          <th>Difficulty</th>
          <th>Tags</th>
          <th>Acceptance</th>
        </tr>
      </thead>
      <tbody>
        {problems.map((problem) => (
          <tr key={problem.id}>
            <td>
              <button type="button" className="linklike" onClick={() => onSelect(problem.slug)}>
                {problem.title}
              </button>
            </td>
            <td>{problem.difficulty}</td>
            <td>{problem.tags.join(', ')}</td>
            <td>{problem.acceptanceRate.toFixed(1)}%</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
```

- [ ] **Step 4: Implement `ProblemListPage`**

`frontend/src/pages/ProblemListPage.tsx`:

```tsx
import { useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchProblems } from '../api/problems'
import type { ProblemFilters } from '../api/problems'
import type { Difficulty } from '../api/types'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { ProblemTable } from '../components/ProblemTable'
import { Spinner } from '../components/Spinner'

export function ProblemListPage() {
  const [params, setParams] = useSearchParams()
  const navigate = useNavigate()

  const filters = useMemo<ProblemFilters>(
    () => ({
      page: Number(params.get('page') ?? '0'),
      size: 20,
      difficulty: (params.get('difficulty') as Difficulty | null) ?? '',
      tag: params.get('tag') ?? '',
      search: params.get('search') ?? '',
    }),
    [params],
  )

  const query = useQuery({
    queryKey: ['problems', filters],
    queryFn: () => fetchProblems(filters),
  })

  function update(patch: Record<string, string | number>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(patch)) {
      if (value === '' || value === undefined) next.delete(key)
      else next.set(key, String(value))
    }
    if (!('page' in patch)) next.set('page', '0')
    setParams(next)
  }

  return (
    <section>
      <h1>Problems</h1>
      <div className="filters">
        <label>
          Difficulty
          <select
            value={filters.difficulty}
            onChange={(event) => update({ difficulty: event.target.value })}
          >
            <option value="">All</option>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </label>
        <label>
          Tag
          <input
            value={filters.tag}
            onChange={(event) => update({ tag: event.target.value })}
          />
        </label>
        <label>
          Search
          <input
            value={filters.search}
            onChange={(event) => update({ search: event.target.value })}
          />
        </label>
      </div>

      {query.isPending && <Spinner label="Loading problems" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && query.data.content.length === 0 && <p>No problems found.</p>}
      {query.isSuccess && query.data.content.length > 0 && (
        <>
          <ProblemTable
            problems={query.data.content}
            onSelect={(slug) => navigate(`/problems/${slug}`)}
          />
          <Pagination
            page={query.data.page}
            totalPages={query.data.totalPages}
            onPageChange={(page) => update({ page })}
          />
        </>
      )}
    </section>
  )
}
```

- [ ] **Step 5: Run the list-page test to verify it passes**

Run: `npm run test -- src/pages/ProblemListPage.test.tsx`
Expected: PASS.

- [ ] **Step 6: Write the failing detail-page test**

`frontend/src/pages/ProblemDetailPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { ProblemDetailPage } from './ProblemDetailPage'
import { renderWithProviders } from '../test/renderWithProviders'

const DETAIL = {
  id: 1,
  slug: 'two-sum',
  title: 'Two Sum',
  statement: 'Add two numbers.',
  difficulty: 'EASY',
  timeLimitMs: 2000,
  memoryLimitKb: 262144,
  tags: ['array'],
  sampleTestCases: [{ input: '1 2', expectedOutput: '3', order: 1 }],
}

describe('ProblemDetailPage', () => {
  afterEach(() => vi.restoreAllMocks())

  it('renders the statement and sample cases', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        const body = url.includes('/stats')
          ? { totalSubmissions: 10, acceptedCount: 5, acceptanceRate: 50 }
          : DETAIL
        return new Response(JSON.stringify(body), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }),
    )

    renderWithProviders(
      <Routes>
        <Route path="/problems/:slug" element={<ProblemDetailPage />} />
      </Routes>,
      { route: '/problems/two-sum' },
    )

    expect(await screen.findByRole('heading', { name: 'Two Sum' })).toBeInTheDocument()
    expect(screen.getByText('Add two numbers.')).toBeInTheDocument()
    expect(screen.getByText('1 2')).toBeInTheDocument()
    expect(screen.getByText('50.0%')).toBeInTheDocument()
  })
})
```

- [ ] **Step 7: Run it to verify it fails**

Run: `npm run test -- src/pages/ProblemDetailPage.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 8: Implement `ProblemDetailPage` (read-only; submit arrives in Task 5)**

`frontend/src/pages/ProblemDetailPage.tsx`:

```tsx
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchProblem, fetchProblemStats } from '../api/problems'
import { ErrorBanner } from '../components/ErrorBanner'
import { Spinner } from '../components/Spinner'

export function ProblemDetailPage() {
  const { slug = '' } = useParams()
  const detail = useQuery({ queryKey: ['problem', slug], queryFn: () => fetchProblem(slug) })
  const stats = useQuery({
    queryKey: ['problemStats', slug],
    queryFn: () => fetchProblemStats(slug),
  })

  if (detail.isPending) return <Spinner label="Loading problem" />
  if (detail.isError) return <ErrorBanner error={detail.error} />

  const problem = detail.data
  return (
    <section>
      <h1>{problem.title}</h1>
      <p className="problem-meta">
        {problem.difficulty} · {problem.timeLimitMs} ms ·{' '}
        {Math.round(problem.memoryLimitKb / 1024)} MB · {problem.tags.join(', ')}
      </p>
      {stats.isSuccess && <p>Acceptance: {stats.data.acceptanceRate.toFixed(1)}%</p>}
      <article className="statement">{problem.statement}</article>
      <h2>Sample cases</h2>
      <ul>
        {problem.sampleTestCases.map((sample) => (
          <li key={sample.order}>
            <pre>Input:\n{sample.input}</pre>
            <pre>Expected:\n{sample.expectedOutput}</pre>
          </li>
        ))}
      </ul>
    </section>
  )
}
```

- [ ] **Step 9: Wire the routes and append styles**

`frontend/src/App.tsx` — replace `LandingPage` with `ProblemListPage` and add the detail route:

```tsx
import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { ProblemListPage } from './pages/ProblemListPage'
import { ProblemDetailPage } from './pages/ProblemDetailPage'

function NotFoundPage() {
  return (
    <section>
      <h1>Not found</h1>
    </section>
  )
}

function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ProblemListPage />} />
        <Route path="problems/:slug" element={<ProblemDetailPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
```

Append to `frontend/src/index.css`:

```css
.problem-table {
  width: 100%;
  border-collapse: collapse;
  margin: var(--space-3) 0;
}

.problem-table th,
.problem-table td {
  text-align: left;
  padding: var(--space-2);
  border-bottom: 1px solid var(--color-border);
}

.linklike {
  background: none;
  border: none;
  color: var(--color-accent);
  cursor: pointer;
  padding: 0;
}

.filters {
  display: flex;
  gap: var(--space-3);
  margin: var(--space-3) 0;
}

.pagination {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.statement {
  white-space: pre-wrap;
}

.problem-meta {
  color: var(--color-muted);
}
```

- [ ] **Step 10: Run the full gate**

Run: `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 11: Stage (do not commit) and suggest a message**

```
git add frontend/src
```
Suggested message: `feat(frontend): add problem list and detail pages with filters`

---

### Task 5: Submit + result polling (editor, languages, submissions API, result page)

**Files:**
- Create: `frontend/src/api/submissions.ts`
- Create: `frontend/src/components/CodeEditor.tsx`
- Create: `frontend/src/components/LanguageSelect.tsx`
- Create: `frontend/src/components/VerdictBadge.tsx`
- Test: `frontend/src/components/VerdictBadge.test.tsx`
- Create: `frontend/src/components/StatusTimeline.tsx`
- Create: `frontend/src/pages/SubmissionResultPage.tsx`
- Test: `frontend/src/pages/SubmissionResultPage.test.tsx`
- Modify: `frontend/src/pages/ProblemDetailPage.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `apiFetch` (Task 2); `useAuth` (Task 3); `fetchLanguages` (Task 4); types from Task 2.
- Produces:
  - `createSubmission(input: CreateSubmission, idempotencyKey: string): Promise<SubmissionAccepted>`
  - `fetchSubmission(id: number): Promise<SubmissionDetail>`
  - `fetchSubmissionStatus(id: number): Promise<SubmissionStatusResponse>`
  - `fetchHistory(params: { problemId?: number; page?: number; size?: number }): Promise<PagedResponse<SubmissionSummary>>`
  - `CodeEditor({ value, languageName, onChange, readOnly? })`
  - `LanguageSelect({ languages, value, onChange })`
  - `VerdictBadge({ verdict })`, `StatusTimeline({ status })`
  - Route `/submissions/:id` → `SubmissionResultPage` (guarded).

- [ ] **Step 1: Create the submissions API**

`frontend/src/api/submissions.ts`:

```ts
import { apiFetch } from './client'
import type {
  CreateSubmission,
  PagedResponse,
  SubmissionAccepted,
  SubmissionDetail,
  SubmissionStatusResponse,
  SubmissionSummary,
} from './types'

export function createSubmission(
  input: CreateSubmission,
  idempotencyKey: string,
): Promise<SubmissionAccepted> {
  return apiFetch<SubmissionAccepted>('/api/v1/submissions', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify(input),
  })
}

export function fetchSubmission(id: number): Promise<SubmissionDetail> {
  return apiFetch<SubmissionDetail>(`/api/v1/submissions/${id}`)
}

export function fetchSubmissionStatus(id: number): Promise<SubmissionStatusResponse> {
  return apiFetch<SubmissionStatusResponse>(`/api/v1/submissions/${id}/status`)
}

export function fetchHistory(params: {
  problemId?: number
  page?: number
  size?: number
}): Promise<PagedResponse<SubmissionSummary>> {
  const query = new URLSearchParams()
  if (params.problemId !== undefined) query.set('problemId', String(params.problemId))
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  return apiFetch<PagedResponse<SubmissionSummary>>(`/api/v1/submissions?${query.toString()}`)
}
```

- [ ] **Step 2: Write the failing `VerdictBadge` test**

`frontend/src/components/VerdictBadge.test.tsx`:

```tsx
import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { VerdictBadge } from './VerdictBadge'

describe('VerdictBadge', () => {
  it('renders a human-readable label for a known verdict', () => {
    render(<VerdictBadge verdict="WRONG_ANSWER" />)
    expect(screen.getByText('Wrong Answer')).toBeInTheDocument()
  })

  it('renders a pending state when the verdict is null', () => {
    render(<VerdictBadge verdict={null} />)
    expect(screen.getByText('Pending')).toBeInTheDocument()
  })
})
```

- [ ] **Step 3: Run it to verify it fails**

Run: `npm run test -- src/components/VerdictBadge.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 4: Implement the presentational components**

`frontend/src/components/VerdictBadge.tsx`:

```tsx
import type { Verdict } from '../api/types'

const LABELS: Record<Verdict, string> = {
  ACCEPTED: 'Accepted',
  WRONG_ANSWER: 'Wrong Answer',
  TIME_LIMIT_EXCEEDED: 'Time Limit Exceeded',
  MEMORY_LIMIT_EXCEEDED: 'Memory Limit Exceeded',
  COMPILATION_ERROR: 'Compilation Error',
  RUNTIME_ERROR: 'Runtime Error',
  SYSTEM_ERROR: 'System Error',
}

const CLASS: Record<Verdict, string> = {
  ACCEPTED: 'verdict--ok',
  WRONG_ANSWER: 'verdict--bad',
  TIME_LIMIT_EXCEEDED: 'verdict--warn',
  MEMORY_LIMIT_EXCEEDED: 'verdict--warn',
  COMPILATION_ERROR: 'verdict--bad',
  RUNTIME_ERROR: 'verdict--bad',
  SYSTEM_ERROR: 'verdict--bad',
}

export function VerdictBadge({ verdict }: { verdict: Verdict | null }) {
  if (!verdict) return <span className="verdict verdict--pending">Pending</span>
  return <span className={`verdict ${CLASS[verdict]}`}>{LABELS[verdict]}</span>
}
```

`frontend/src/components/StatusTimeline.tsx`:

```tsx
import type { SubmissionStatus } from '../api/types'

const STAGES: SubmissionStatus[] = [
  'SUBMITTED',
  'QUEUED',
  'PICKED_UP',
  'COMPILING',
  'RUNNING',
  'EVALUATING',
  'COMPLETED',
]

export function StatusTimeline({ status }: { status: SubmissionStatus }) {
  const activeIndex = STAGES.indexOf(status)
  return (
    <ol className="timeline">
      {STAGES.map((stage, index) => (
        <li
          key={stage}
          className={index <= activeIndex ? 'timeline__step timeline__step--done' : 'timeline__step'}
        >
          {stage}
        </li>
      ))}
    </ol>
  )
}
```

`frontend/src/components/LanguageSelect.tsx`:

```tsx
import type { Language } from '../api/types'

export function LanguageSelect({
  languages,
  value,
  onChange,
}: {
  languages: Language[]
  value: number | null
  onChange: (languageId: number) => void
}) {
  return (
    <select
      aria-label="Language"
      value={value ?? ''}
      onChange={(event) => onChange(Number(event.target.value))}
    >
      <option value="" disabled>
        Select a language
      </option>
      {languages.map((language) => (
        <option key={language.id} value={language.id}>
          {language.name}
        </option>
      ))}
    </select>
  )
}
```

`frontend/src/components/CodeEditor.tsx`:

```tsx
import CodeMirror from '@uiw/react-codemirror'
import { java } from '@codemirror/lang-java'
import { python } from '@codemirror/lang-python'
import type { Extension } from '@codemirror/state'

export const BOILERPLATE: Record<'java' | 'python', string> = {
  java: 'public class Main {\n    public static void main(String[] args) {\n        \n    }\n}\n',
  python: 'def main():\n    pass\n\n\nif __name__ == "__main__":\n    main()\n',
}

export function extensionFor(languageName: string): Extension[] {
  const name = languageName.toLowerCase()
  if (name.startsWith('java')) return [java()]
  if (name.startsWith('python')) return [python()]
  return []
}

export function CodeEditor({
  value,
  languageName,
  onChange,
  readOnly = false,
}: {
  value: string
  languageName: string
  onChange: (value: string) => void
  readOnly?: boolean
}) {
  return (
    <CodeMirror
      value={value}
      height="320px"
      readOnly={readOnly}
      extensions={extensionFor(languageName)}
      onChange={(next) => onChange(next)}
    />
  )
}
```

> Note: `@codemirror/state` is a transitive dependency of `@uiw/react-codemirror`; importing the `Extension` type from it is type-only and adds no new runtime dependency. If the import is flagged as missing, declare it by adding `@codemirror/state` as a direct dependency — but only after confirming it is not already resolvable.

- [ ] **Step 5: Run the `VerdictBadge` test to verify it passes**

Run: `npm run test -- src/components/VerdictBadge.test.tsx`
Expected: PASS.

- [ ] **Step 6: Add the editor + submit to `ProblemDetailPage`**

`frontend/src/pages/ProblemDetailPage.tsx` (replace):

```tsx
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchProblem, fetchProblemStats } from '../api/problems'
import { fetchLanguages } from '../api/languages'
import { createSubmission } from '../api/submissions'
import { useAuth } from '../auth/AuthContext'
import { BOILERPLATE, CodeEditor } from '../components/CodeEditor'
import { ErrorBanner } from '../components/ErrorBanner'
import { LanguageSelect } from '../components/LanguageSelect'
import { Spinner } from '../components/Spinner'

const MAX_SOURCE_LENGTH = 65_536

function boilerplateFor(languageName: string): string {
  const name = languageName.toLowerCase()
  if (name.startsWith('java')) return BOILERPLATE.java
  if (name.startsWith('python')) return BOILERPLATE.python
  return ''
}

export function ProblemDetailPage() {
  const { slug = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { status: authStatus } = useAuth()

  const detail = useQuery({ queryKey: ['problem', slug], queryFn: () => fetchProblem(slug) })
  const stats = useQuery({
    queryKey: ['problemStats', slug],
    queryFn: () => fetchProblemStats(slug),
  })
  const languages = useQuery({ queryKey: ['languages'], queryFn: fetchLanguages })

  const [languageId, setLanguageId] = useState<number | null>(null)
  const [sourceCode, setSourceCode] = useState('')

  const submit = useMutation({
    mutationFn: (input: { sourceCode: string; languageId: number; idempotencyKey: string }) =>
      createSubmission(
        {
          problemId: detail.data!.id,
          languageId: input.languageId,
          sourceCode: input.sourceCode,
        },
        input.idempotencyKey,
      ),
    onSuccess: (accepted) => {
      queryClient.invalidateQueries({ queryKey: ['history'] })
      navigate(`/submissions/${accepted.submissionId}`)
    },
  })

  function onLanguageChange(nextId: number) {
    setLanguageId(nextId)
    const selected = languages.data?.find((language) => language.id === nextId)
    if (selected && sourceCode.trim() === '') setSourceCode(boilerplateFor(selected.name))
  }

  if (detail.isPending) return <Spinner label="Loading problem" />
  if (detail.isError) return <ErrorBanner error={detail.error} />

  const problem = detail.data
  const tooLong = sourceCode.length > MAX_SOURCE_LENGTH

  return (
    <section>
      <h1>{problem.title}</h1>
      <p className="problem-meta">
        {problem.difficulty} · {problem.timeLimitMs} ms ·{' '}
        {Math.round(problem.memoryLimitKb / 1024)} MB · {problem.tags.join(', ')}
      </p>
      {stats.isSuccess && <p>Acceptance: {stats.data.acceptanceRate.toFixed(1)}%</p>}
      <article className="statement">{problem.statement}</article>
      <h2>Sample cases</h2>
      <ul>
        {problem.sampleTestCases.map((sample) => (
          <li key={sample.order}>
            <pre>{`Input:\n${sample.input}`}</pre>
            <pre>{`Expected:\n${sample.expectedOutput}`}</pre>
          </li>
        ))}
      </ul>

      <h2>Submit</h2>
      {authStatus !== 'authenticated' ? (
        <p>
          <Link to="/login">Log in</Link> to submit a solution.
        </p>
      ) : (
        <div className="submit-panel">
          {languages.isError && <ErrorBanner error={languages.error} />}
          {submit.isError && <ErrorBanner error={submit.error} />}
          <LanguageSelect
            languages={languages.data ?? []}
            value={languageId}
            onChange={onLanguageChange}
          />
          <CodeEditor
            value={sourceCode}
            languageName={
              languages.data?.find((language) => language.id === languageId)?.name ?? ''
            }
            onChange={setSourceCode}
          />
          {tooLong && <p className="error-inline">Source exceeds {MAX_SOURCE_LENGTH} characters.</p>}
          <button
            type="button"
            disabled={submit.isPending || languageId === null || sourceCode.trim() === '' || tooLong}
            onClick={() =>
              submit.mutate({
                sourceCode,
                languageId: languageId!,
                idempotencyKey: crypto.randomUUID(),
              })
            }
          >
            {submit.isPending ? 'Submitting…' : 'Submit'}
          </button>
        </div>
      )}
    </section>
  )
}
```

- [ ] **Step 7: Write the failing result-page test**

`frontend/src/pages/SubmissionResultPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { SubmissionResultPage } from './SubmissionResultPage'
import { renderWithProviders } from '../test/renderWithProviders'

const DETAIL = {
  id: 1,
  problemId: 1,
  problemSlug: 'two-sum',
  languageName: 'Python 3.12',
  status: 'COMPLETED',
  verdict: 'WRONG_ANSWER',
  timeUsedMs: 12,
  memoryUsedKb: 4096,
  errorMessage: null,
  sourceCode: 'print(1)',
  submittedAt: '2026-01-01T00:00:00Z',
  judgedAt: '2026-01-01T00:00:01Z',
  failedTestCase: { index: 2, isSample: false },
  results: [{ index: 2, isSample: false, verdict: 'WRONG_ANSWER', timeUsedMs: 12, memoryUsedKb: 4096 }],
}

describe('SubmissionResultPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('shows the verdict and the failed test case index only', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify(DETAIL), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )

    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/submissions/:id" element={<SubmissionResultPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/submissions/1' },
    )

    expect(await screen.findByText('Wrong Answer')).toBeInTheDocument()
    expect(screen.getByText(/test case #2/i)).toBeInTheDocument()
  })
})
```

- [ ] **Step 8: Run it to verify it fails**

Run: `npm run test -- src/pages/SubmissionResultPage.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 9: Implement `SubmissionResultPage`**

`frontend/src/pages/SubmissionResultPage.tsx`:

```tsx
import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchSubmission } from '../api/submissions'
import { isTerminalStatus } from '../api/types'
import { ErrorBanner } from '../components/ErrorBanner'
import { Spinner } from '../components/Spinner'
import { StatusTimeline } from '../components/StatusTimeline'
import { VerdictBadge } from '../components/VerdictBadge'

export function SubmissionResultPage() {
  const { id = '' } = useParams()
  const submissionId = Number(id)

  const query = useQuery({
    queryKey: ['submission', submissionId],
    queryFn: () => fetchSubmission(submissionId),
    enabled: Number.isFinite(submissionId),
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status && isTerminalStatus(status) ? false : 1000
    },
  })

  if (query.isPending) return <Spinner label="Loading submission" />
  if (query.isError) return <ErrorBanner error={query.error} />

  const submission = query.data
  return (
    <section>
      <h1>Submission #{submission.id}</h1>
      <p className="problem-meta">
        <VerdictBadge verdict={submission.verdict} /> · {submission.languageName} ·{' '}
        {submission.problemSlug}
      </p>
      <StatusTimeline status={submission.status} />
      {!isTerminalStatus(submission.status) && <p>Judging… this page updates automatically.</p>}
      {submission.timeUsedMs !== null && <p>Time: {submission.timeUsedMs} ms</p>}
      {submission.memoryUsedKb !== null && (
        <p>Memory: {Math.round(submission.memoryUsedKb / 1024)} MB</p>
      )}
      {submission.failedTestCase && (
        <p>
          Failed on test case #{submission.failedTestCase.index}
          {submission.failedTestCase.isSample ? ' (sample)' : ''}
        </p>
      )}
      {submission.errorMessage && <pre className="error-detail">{submission.errorMessage}</pre>}

      {submission.results.length > 0 && (
        <table className="problem-table">
          <thead>
            <tr>
              <th>#</th>
              <th>Verdict</th>
              <th>Time (ms)</th>
            </tr>
          </thead>
          <tbody>
            {submission.results.map((result) => (
              <tr key={result.index}>
                <td>{result.index}</td>
                <td>
                  <VerdictBadge verdict={result.verdict} />
                </td>
                <td>{result.timeUsedMs ?? '-'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
```

- [ ] **Step 10: Wire the guarded result route**

`frontend/src/App.tsx` — add the import and route:

```tsx
import { RequireAuth } from './auth/RequireAuth'
import { SubmissionResultPage } from './pages/SubmissionResultPage'
```

```tsx
<Route
  path="submissions/:id"
  element={
    <RequireAuth>
      <SubmissionResultPage />
    </RequireAuth>
  }
/>
```

Append to `frontend/src/index.css`:

```css
.submit-panel {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.timeline {
  display: flex;
  gap: var(--space-2);
  list-style: none;
  padding: 0;
  flex-wrap: wrap;
}

.timeline__step {
  padding: var(--space-1) var(--space-2);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  color: var(--color-muted);
}

.timeline__step--done {
  border-color: var(--color-accent);
  color: var(--color-text);
}

.verdict {
  font-weight: 600;
}

.verdict--ok {
  color: var(--color-success);
}

.verdict--bad {
  color: var(--color-danger);
}

.verdict--warn {
  color: var(--color-warn);
}

.verdict--pending {
  color: var(--color-muted);
}

.error-inline {
  color: var(--color-danger);
}

.error-detail {
  background: var(--color-surface);
  padding: var(--space-3);
  border-radius: var(--radius);
  overflow: auto;
}

pre {
  background: var(--color-surface);
  padding: var(--space-2);
  border-radius: var(--radius);
  overflow: auto;
}
```

- [ ] **Step 11: Run the full gate**

Run: `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 12: Stage (do not commit) and suggest a message**

```
git add frontend/src
```
Suggested message: `feat(frontend): add code editor, submit, and polling result view`

---

### Task 6: Submission history page

**Files:**
- Create: `frontend/src/pages/HistoryPage.tsx`
- Test: `frontend/src/pages/HistoryPage.test.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `fetchHistory` (Task 5); `VerdictBadge`, `Pagination`, `Spinner`, `ErrorBanner`.
- Produces: route `/submissions` → guarded `HistoryPage`.

- [ ] **Step 1: Write the failing history test**

`frontend/src/pages/HistoryPage.test.tsx`:

```tsx
import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import { AuthProvider } from '../auth/AuthContext'
import { HistoryPage } from './HistoryPage'
import { renderWithProviders } from '../test/renderWithProviders'

const PAGE = {
  content: [
    {
      id: 42,
      problemId: 1,
      problemSlug: 'two-sum',
      languageName: 'Python 3.12',
      status: 'COMPLETED',
      verdict: 'ACCEPTED',
      timeUsedMs: 10,
      memoryUsedKb: 2048,
      submittedAt: '2026-01-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
}

describe('HistoryPage', () => {
  afterEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
  })

  it('lists the caller submissions', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify(PAGE), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    )
    renderWithProviders(
      <AuthProvider>
        <Routes>
          <Route path="/submissions" element={<HistoryPage />} />
        </Routes>
      </AuthProvider>,
      { route: '/submissions' },
    )
    expect(await screen.findByText('Accepted')).toBeInTheDocument()
    expect(screen.getByText('two-sum')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

Run: `npm run test -- src/pages/HistoryPage.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `HistoryPage`**

`frontend/src/pages/HistoryPage.tsx`:

```tsx
import { Link, useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchHistory } from '../api/submissions'
import { ErrorBanner } from '../components/ErrorBanner'
import { Pagination } from '../components/Pagination'
import { Spinner } from '../components/Spinner'
import { VerdictBadge } from '../components/VerdictBadge'

export function HistoryPage() {
  const [params, setParams] = useSearchParams()
  const page = Number(params.get('page') ?? '0')
  const problemIdParam = params.get('problemId')
  const problemId = problemIdParam ? Number(problemIdParam) : undefined

  const query = useQuery({
    queryKey: ['history', { problemId, page }],
    queryFn: () => fetchHistory({ problemId, page, size: 20 }),
  })

  function setPage(next: number) {
    const updated = new URLSearchParams(params)
    updated.set('page', String(next))
    setParams(updated)
  }

  return (
    <section>
      <h1>My Submissions</h1>
      {query.isPending && <Spinner label="Loading submissions" />}
      {query.isError && <ErrorBanner error={query.error} />}
      {query.isSuccess && query.data.content.length === 0 && <p>No submissions yet.</p>}
      {query.isSuccess && query.data.content.length > 0 && (
        <>
          <table className="problem-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Problem</th>
                <th>Language</th>
                <th>Verdict</th>
                <th>Time (ms)</th>
              </tr>
            </thead>
            <tbody>
              {query.data.content.map((submission) => (
                <tr key={submission.id}>
                  <td>
                    <Link to={`/submissions/${submission.id}`}>{submission.id}</Link>
                  </td>
                  <td>{submission.problemSlug}</td>
                  <td>{submission.languageName}</td>
                  <td>
                    <VerdictBadge verdict={submission.verdict} />
                  </td>
                  <td>{submission.timeUsedMs ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Pagination page={page} totalPages={query.data.totalPages} onPageChange={setPage} />
        </>
      )}
    </section>
  )
}
```

- [ ] **Step 4: Wire the guarded history route**

`frontend/src/App.tsx` — import `HistoryPage` and add:

```tsx
<Route
  path="submissions"
  element={
    <RequireAuth>
      <HistoryPage />
    </RequireAuth>
  }
/>
```

- [ ] **Step 5: Run the full gate**

Run: `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 6: Stage (do not commit) and suggest a message**

```
git add frontend/src
```
Suggested message: `feat(frontend): add submission history page`

---

### Task 7: Live verification script + docs

**Files:**
- Create: `frontend/scripts/verify-phase11-live.mjs`
- Modify: `PRD.md` (Phase 11 section)
- Modify: `README.md` (frontend section)

**Interfaces:**
- Consumes: the running backend at `BASE_URL` (default `http://localhost:8080`).
- Produces: a Node script that exits `0` on full success and non-zero on the first failed step, printing `PASS`/`FAIL` per step.

- [ ] **Step 1: Create the live journey script**

`frontend/scripts/verify-phase11-live.mjs`:

```js
// Exercises the exact API sequence the Phase 11 UI performs.
// Usage: BASE_URL=http://localhost:8080 node scripts/verify-phase11-live.mjs
const BASE_URL = process.env.BASE_URL ?? 'http://localhost:8080'
const USERNAME = process.env.OJ_USER ?? `phase11_${Date.now()}`
const PASSWORD = 'secret123'
const EMAIL = `${USERNAME}@example.com`

let failed = false

function pass(step, detail = '') {
  console.log(`PASS: ${step}${detail ? ` (${detail})` : ''}`)
}

function fail(step, error) {
  failed = true
  console.error(`FAIL: ${step} -> ${error}`)
}

async function step(name, fn) {
  try {
    const result = await fn()
    pass(name, result ?? '')
    return result
  } catch (error) {
    fail(name, error?.message ?? error)
    throw error
  }
}

async function json(path, init) {
  const response = await fetch(`${BASE_URL}${path}`, init)
  const text = await response.text()
  const body = text ? JSON.parse(text) : undefined
  if (!response.ok) throw new Error(`${response.status} ${body?.title ?? ''} ${body?.detail ?? ''}`)
  return body
}

async function main() {
  const health = await step('backend reachable', async () => {
    const response = await fetch(`${BASE_URL}/api/v1/languages`)
    if (!response.ok) throw new Error(`status ${response.status}`)
    return `${response.status}`
  })
  void health

  await step('register', () =>
    json('/api/v1/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USERNAME, email: EMAIL, password: PASSWORD }),
    }),
  )

  const tokens = await step('login', () =>
    json('/api/v1/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: USERNAME, password: PASSWORD }),
    }),
  )

  await step('refresh', () =>
    json('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: tokens.refreshToken }),
    }),
  )

  const authHeaders = { Authorization: `Bearer ${tokens.accessToken}` }

  const problems = await step('list problems', () => json('/api/v1/problems?page=0&size=5'))
  if (!problems.content || problems.content.length === 0) {
    throw new Error('no published problems to submit against; seed one before running')
  }
  const problem = problems.content[0]

  await step('problem detail', () => json(`/api/v1/problems/${problem.slug}`))
  await step('problem stats', () => json(`/api/v1/problems/${problem.slug}/stats`))

  const languages = await step('languages', async () => {
    const list = await json('/api/v1/languages')
    if (!Array.isArray(list) || list.length === 0) throw new Error('no languages available')
    return list
  })

  const detail = await json(`/api/v1/problems/${problem.slug}`)
  void detail

  const accepted = await step('submit', () =>
    json('/api/v1/submissions', {
      method: 'POST',
      headers: { ...authHeaders, 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
      body: JSON.stringify({
        problemId: problem.id,
        languageId: languages[0].id,
        sourceCode: 'print("hello")',
      }),
    }),
  )

  await step('poll status to terminal', async () => {
    const deadline = Date.now() + 60_000
    const terminal = new Set([
      'COMPLETED',
      'COMPILATION_ERROR',
      'TIME_LIMIT_EXCEEDED',
      'MEMORY_LIMIT_EXCEEDED',
      'RUNTIME_ERROR',
      'SYSTEM_ERROR',
    ])
    while (Date.now() < deadline) {
      const status = await json(`/api/v1/submissions/${accepted.submissionId}/status`, {
        headers: authHeaders,
      })
      if (terminal.has(status.status)) return status.status
      await new Promise((resolve) => setTimeout(resolve, 1000))
    }
    throw new Error('timed out waiting for a terminal status')
  })

  await step('history', async () => {
    const history = await json('/api/v1/submissions?page=0&size=5', { headers: authHeaders })
    if (!history.content || history.content.length === 0) throw new Error('history is empty')
    return `${history.totalElements} row(s)`
  })

  if (failed) {
    console.error('\nRESULT: FAIL')
    process.exit(1)
  }
  console.log('\nRESULT: PASS')
}

main().catch(() => {
  console.error('\nRESULT: FAIL')
  process.exit(1)
})
```

- [ ] **Step 2: Create the bounded wrapper (adapted from `verify-phase10.ps1`)**

At `C:\Users\Atharva\AppData\Local\Temp\opencode\verify-phase11.ps1`, copy `verify-phase10.ps1` and change the final action to, in order: start Docker infra (postgres:17-alpine on 5433, redis:7.4-alpine, rabbitmq:4.1-management-alpine), build `oj-java21` and `oj-python312`, start the backend and worker jars with the same env as Phase 10, seed at least one **published** problem with a sample test case (reuse the Phase 10 seeding block), run `node frontend/scripts/verify-phase11-live.mjs`, capture the exit code, print `PHASE 11 LIVE: PASS`/`FAIL`, then tear down all containers and processes. Add `$env:JAVA_HOME = "C:\Program Files\Java\jdk-23"` and prepend `C:\Program Files\Docker\Docker\resources\bin` to `$PATH` at the top, as in the Phase 10 template.

- [ ] **Step 3: Run the live verification**

Run: `powershell -ExecutionPolicy Bypass -File C:\Users\Atharva\AppData\Local\Temp\opencode\verify-phase11.ps1`
Expected: final line `PHASE 11 LIVE: PASS`; no `FAIL:` lines.

- [ ] **Step 4: Run the frontend gate one last time**

Run (workdir `frontend`): `npm run lint && npm run test && npm run build`
Expected: all pass.

- [ ] **Step 5: Update `PRD.md`**

In the Phase 11 section, change the status to complete and add a short "Implementation notes" block recording: the three dependency groups added and their versions; the refresh-token-in-localStorage tradeoff (access token in memory); the polling (not SSE) decision; and that user-level stats and admin authoring remain deferred.

- [ ] **Step 6: Update `README.md`**

Under the frontend section, document: `npm install`, `npm run dev` (proxies `/api` to `:8080`), `npm run lint`, `npm run test`, `npm run build`, and `node scripts/verify-phase11-live.mjs` (with `BASE_URL`).

- [ ] **Step 7: Stage (do not commit) and suggest a message**

```
git add frontend PRD.md README.md
```
Suggested message: `feat(frontend): complete Phase 11 solver UI with live verification and docs`

---

## Self-Review

**Spec coverage:**
- §4 module structure → Tasks 1–6 create every listed module (Landing placeholder replaced in Task 4; `hooks/` intentionally folded into pages as inline `refetchInterval`, noted here so it is not a silent omission).
- §5.1 auth → Task 3. §5.2 browsing → Task 4. §5.3 detail + editor → Tasks 4–5. §5.4 submit + polling → Task 5. §5.5 history → Task 6.
- §6 error handling → `ApiError`/`ErrorBanner` in Tasks 2–3, used throughout. §7 security → Global Constraints + Task 3. §8 testing → each task's tests + Task 7 live script. §9 docs → Task 7.
- Dependencies → Task 1 (exact pinned versions). Refresh-token storage → Task 3. Polling-not-SSE → Task 5.

**Placeholder scan:** No "TBD"/"add error handling"-style steps; every code step contains the full code. The one environment-dependent item (the PowerShell wrapper) is explicitly derived from an existing, named template rather than left vague, and the API-level script it runs is fully specified.

**Type consistency:** `isTerminalStatus`, `ApiError`, `fetchSubmission`, `createSubmission`, `fetchHistory`, `VerdictBadge`, `Pagination`, `ProblemFilters`, and `REFRESH_TOKEN_KEY` are defined once and referenced with identical names/signatures in later tasks. `renderWithProviders` signature is stable across Tasks 1–6.
