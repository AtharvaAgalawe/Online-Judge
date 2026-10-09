# Phase 11 — Frontend MVP: Design Spec

- **Status**: Approved for planning (2026-10-07)
- **Scope source**: `PRD.md` §7 (Functional Requirements) and Phase 11 in the roadmap.
- **Governing rules**: `AGENTS.md` (priority: correctness > security > maintainability > observability > performance > cleverness).
- **DoD (PRD Phase 11)**: a person can complete the full §6.1 solver journey through the UI alone.

## 1. Goal

Deliver a usable React + TypeScript single-page app covering the complete solver
journey: register/login → browse problems (filter + paginate) → open a problem
(statement + sample cases) → write code in an editor → pick a language → submit →
watch status transitions → see the verdict and which test case failed → view it in
submission history.

Explicitly **out of scope** for Phase 11 (recorded so it is not silently assumed):

- Admin problem/test-case authoring UI (Phase 12).
- SSE live streaming in place of polling (deferred; noted as a gap).
- User-level statistics / "solved count" (no backend endpoint exists; adding one is
  backend scope creep — deferred).
- Visual polish beyond a clean, functional, token-driven layout.

## 2. Assumptions and constraints

- The backend API is stable through Phase 10; the frontend consumes `/api/v1`
  through the Vite dev proxy (`/api` → `http://localhost:8080`) already configured
  in `frontend/vite.config.ts`. Production serving/CD is Phase 16.
- The app is a browser SPA; no server-side rendering.
- All submitted source code is untrusted but the frontend never executes it — it
  only transports it to the backend.
- Hidden test content is never available from the API and therefore never rendered;
  only `failedTestCase.index` and `isSample`, and per-case pass/fail results, are
  displayed.

### 2.1 Wire contracts the UI depends on (verified 2026-10-07)

| Endpoint | Method | Auth | Notes |
|---|---|---|---|
| `/auth/register` | POST | public | body `{username,email,password}` → 201 `UserResponse`; **no tokens** |
| `/auth/login` | POST | public | body `{username,password}` → `{accessToken,refreshToken,expiresIn}`; **no user object** |
| `/auth/refresh` | POST | public | body `{refreshToken}` → `{accessToken,expiresIn}` |
| `/auth/logout` | POST | auth | 204 |
| `/problems` | GET | public | `page,size,difficulty,tag,search` → `PagedResponse<ProblemSummaryResponse>` |
| `/problems/{slug}` | GET | public | `ProblemDetailResponse` (samples only) |
| `/problems/{slug}/stats` | GET | public | `{totalSubmissions,acceptedCount,acceptanceRate}` |
| `/languages` | GET | public | `LanguageResponse[]{id,name}` |
| `/submissions` | POST | auth | body `{problemId,languageId,sourceCode}`, optional `Idempotency-Key` header → 202 `{submissionId,status}` |
| `/submissions/{id}` | GET | auth | `SubmissionDetailResponse` |
| `/submissions/{id}/status` | GET | auth | `{status,verdict}` |
| `/submissions` | GET | auth | `problemId,userId,page,size` → `PagedResponse<SubmissionSummaryResponse>` |

- The JWT access token is HS256 with claims `sub` (username), `uid` (user id), and
  `roles` (e.g. `ROLE_ADMIN`). The client decodes the payload for display/future
  admin gating; it does not verify the signature (the server enforces authz).
- Errors are RFC 7807 `application/problem+json`.

## 3. Dependencies (AGENTS §17 / §22)

Every added dependency is pinned to an exact version.

| Dependency | Purpose | Why existing stack can't solve it | Alternative rejected |
|---|---|---|---|
| `react-router-dom` | Client routing for 6 routes and guarded routes | No router present; hand-rolled routing is untested glue | Hand-rolled history listener |
| `@tanstack/react-query` | Server-state cache, request dedup, and self-terminating polling via `refetchInterval` | Hand-rolled `setInterval` + status state is precisely the bug-prone pattern §25 warns against; Query makes "poll until terminal" declarative and testable | Hand-rolled polling hook |
| `@uiw/react-codemirror` + `@codemirror/lang-java` + `@codemirror/lang-python` | Code editor with syntax highlighting and indentation for the two judge languages | A `<textarea>` gives no highlighting/indentation; the app's core action is writing code | Monaco (much heavier); `<textarea>` (poor UX) |

No styling framework: a small `tokens.css` plus plain CSS. No `MSW`: tests stub
`fetch` with `vi.fn()` to avoid adding a second mocking library.

## 4. Architecture

Single-page app. Three concerns kept separate:

1. **Transport** — a typed `apiClient` over `fetch` that owns auth headers, error
   normalization, and the refresh-once-on-401 behavior.
2. **Server state** — TanStack Query owns all data fetched from the API (caching,
   retries, polling). No API data is duplicated into React state or context.
3. **Session state** — a small `AuthContext` owns only the in-memory access token,
   the decoded user, and login/logout/refresh actions.

### 4.1 Module structure

```
frontend/src/
  main.tsx                 # QueryClientProvider > BrowserRouter > AuthProvider > App
  App.tsx                  # route table
  api/
    client.ts              # fetch wrapper, ApiError, auth header, 401 refresh+retry
    types.ts               # TS mirrors of backend DTOs + enums
    auth.ts problems.ts languages.ts submissions.ts
  auth/
    AuthContext.tsx        # access token (memory), user, login/register/logout/refresh
    RequireAuth.tsx        # route guard -> redirect to /login
    decodeJwt.ts           # base64url decode of JWT payload (sub, uid, roles)
  pages/
    ProblemListPage.tsx ProblemDetailPage.tsx SubmissionResultPage.tsx
    HistoryPage.tsx LoginPage.tsx RegisterPage.tsx
  components/
    Layout.tsx NavBar.tsx ProblemTable.tsx Pagination.tsx CodeEditor.tsx
    LanguageSelect.tsx VerdictBadge.tsx StatusTimeline.tsx ErrorBanner.tsx Spinner.tsx
  hooks/                   # query-option helpers, terminal-status predicate
  styles/ tokens.css app.css
  test/ setup.ts
```

### 4.2 Data flow

- `main.tsx` sets up the `QueryClient`, router, and `AuthProvider`.
- On boot, `AuthProvider` checks `localStorage` for a refresh token; if present it
  calls `/auth/refresh`, stores the access token in memory, and decodes the JWT to
  build the current user. Otherwise the session is anonymous.
- `apiClient` reads the current access token through an injected getter (no circular
  import), attaches `Authorization: Bearer`, and on 401 performs a **single-flight**
  refresh then retries the original request **once**. A failed refresh clears the
  session and routes to `/login`.
- Query keys: `['problems', filters]`, `['problem', slug]`, `['problemStats', slug]`,
  `['languages']`, `['submission', id]`, `['history', filters]`.

### 4.3 Routes

| Path | Guard | Page |
|---|---|---|
| `/` | public | Problem list with filters + pagination |
| `/problems/:slug` | public | Detail (statement, limits, samples, acceptance rate) + editor/submit when authed |
| `/login` | public | Login |
| `/register` | public | Register (auto-login on success) |
| `/submissions/:id` | `RequireAuth` | Result + polling |
| `/submissions` | `RequireAuth` | History table |

## 5. Feature detail

### 5.1 Auth
- Login uses **`username` + `password`** (not email).
- Register returns a user but no tokens → the client **auto-logs-in** via
  `/auth/login` to preserve a smooth journey; if auto-login fails, redirect to
  `/login` with a message.
- Logout calls `/auth/logout`, clears the in-memory access token and the stored
  refresh token, and returns to `/`.
- After login, the client decodes the JWT for `{username, uid, roles}`; role is
  retained for Phase 12 gating but not used to gate anything in Phase 11.

### 5.2 Problem browsing
- Filters `difficulty`, `tag`, `search` and pagination map directly to query params;
  filter state is reflected in the URL so results are shareable/back-navigable.
- Row click navigates to `/problems/:slug`.

### 5.3 Problem detail + submit
- Renders statement, time/memory limits, tags, and sample cases (input +
  expected output), plus acceptance rate from `/problems/{slug}/stats`.
- If authenticated: CodeMirror editor seeded with per-language boilerplate, a
  language `<select>` from `/languages`, and a submit button with client-side
  max-length guard (65,536 chars).
- If not authenticated: the editor is replaced with a "log in to submit" prompt.

### 5.4 Submit + result polling
- Submit generates one `Idempotency-Key` (`crypto.randomUUID()`) per attempt and
  reuses it on retry of that attempt, so a network retry cannot create a second
  submission.
- On `202`, navigate to `/submissions/:id`.
- The result page uses `useQuery` on the detail endpoint with `refetchInterval`
  returning `false` once `status.isTerminal()` (a local enum mirror of
  `SubmissionStatus`). It shows status, verdict, time/memory used, failed case
  **index + isSample**, per-case pass/fail, and `errorMessage` (compile error) when
  present. Hidden test content is never displayed.
- A successful submit invalidates the history query.

### 5.5 History
- Table of the caller's submissions with status/verdict/time/language/date, linking
  to the result page; optional `problemId` filter derived from context when arriving
  from a problem page.

## 6. Error handling and UX states

- `ApiError { status, title, detail }` parsed from RFC 7807; rendered by
  `ErrorBanner`. 401 → refresh flow; 403/404 → friendly message; network failure →
  retry affordance.
- Explicit loading and empty states on every data surface.
- Verdict and status are color-coded; no raw enum strings shown where a label
  improves clarity.

## 7. Security considerations

- Refresh token in `localStorage` is XSS-exposed. This is a **deliberate, documented
  tradeoff** for the MVP (the httpOnly-cookie option requires backend changes and was
  declined). The access token is held in memory only and never persisted.
- No secrets are embedded in the frontend; the API base is a relative path served by
  the dev proxy / same origin in production.
- The frontend never executes submitted code and never requests or renders hidden
  test data.

## 8. Testing strategy

### 8.1 Unit / component (Vitest + Testing Library; `fetch` stubbed)
- `ApiError` normalization from `problem+json`.
- 401 triggers a single-flight refresh and one retry; a failed refresh logs out.
- Polling self-terminates once status is terminal.
- Problem-list filters map to the expected query params.
- Submit sends an `Idempotency-Key` header and navigates on 202.
- `RequireAuth` redirects anonymous users.
- `VerdictBadge`/`StatusTimeline` render known enum values correctly.

### 8.2 Definition of Done gate
`npm run lint && npm run test && npm run build` must pass.

### 8.3 Live verification (bounded, PASS/FAIL, auto-teardown)
1. Start Docker infra (Postgres, Redis, RabbitMQ) and build the language images.
2. Start the backend and worker jars; seed an admin, a published problem with sample
   test cases, and confirm `/languages` is populated via the API.
3. Run a live-tagged test that performs the **exact sequence the UI performs**:
   `register → login → refresh → problems → detail → stats → languages → submit →
   poll status to terminal → history`, asserting each response shape.
4. Report PASS/FAIL and tear everything down.
5. Provide the human a short manual UI checklist (open `/`, browse, open a problem,
   submit, watch the result update, view history).

## 9. Documentation updates

- `PRD.md` Phase 11 marked complete, recording: the three dependency additions, the
  localStorage refresh-token tradeoff, and the polling (not SSE) decision.
- `README.md` frontend section updated with run/build/test commands.

## 10. Increments (each leaves the repo buildable)

1. Dependencies + app shell (router, QueryClientProvider, AuthProvider skeleton,
   layout, `tokens.css`) with a smoke test.
2. API layer + `types.ts` + auth flow (login/register/refresh/logout, `RequireAuth`,
   JWT decode) with tests.
3. Problem list + detail + stats pages with tests.
4. Editor + submit + result polling + history pages with tests.
5. Live verification script + `PRD.md`/`README.md` updates.

## 11. Open questions

None outstanding — every fork was resolved during brainstorming:
router/Query/CodeMirror stack, localStorage refresh token, strict §6.1 scope, and
functional-clean visual direction.
