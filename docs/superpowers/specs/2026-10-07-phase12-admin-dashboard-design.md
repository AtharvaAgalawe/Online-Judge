# Phase 12 — Admin Dashboard: Design Spec

- **Status**: Approved for planning (2026-10-07)
- **Scope source**: `PRD.md` §7.7 + Phase 12, **expanded** per the Phase 10 handoff (authoring + submissions browser + queue status).
- **Governing rules**: `AGENTS.md` (correctness > security > maintainability > observability > performance > cleverness).
- **DoD**: an admin can author and publish a problem entirely through the UI; an admin can browse all submissions; an admin can see queue health including stale-lease jobs.

## 1. Goal

Deliver an admin-only area of the web app plus the backend read endpoints it needs. An admin can:

1. Create a problem, add/edit/delete sample and hidden test cases, and publish/unpublish it — all through the UI.
2. Browse and inspect **all** users' submissions (with filters), including viewing source code via the existing detail endpoint.
3. View queue health: counts plus the list of jobs whose lease has expired while non-terminal ("stuck in RUNNING past its lease" per PRD §6.3).

## 2. Scope

### In scope
- Four new `ROLE_ADMIN` backend read endpoints (no schema change).
- Admin frontend: routing/guard, problem authoring, submissions browser, queue-status view.
- Unit + Testcontainers integration tests for new backend endpoints; component tests for new frontend.
- Live verification and PRD/README updates.

### Out of scope (recorded so it is not silently assumed)
- Manual retry / re-queue of stuck jobs (the reconciler is a separate, deferred concern; PRD §23). The queue view **surfaces** stale jobs only.
- Reading live RabbitMQ queue depth via its management API (would add an integration + config). The endpoint is DB-derived only.
- User management (creating admins, roles) — out of scope.
- Any change to the public solver API surface or schema.

## 3. Assumptions and constraints

- The admin **write** API already exists and is reused unchanged: `POST /api/v1/admin/problems`, `PUT /api/v1/admin/problems/{id}` (partial; `published` is the publish toggle), `DELETE /api/v1/admin/problems/{id}` (unpublish), and `GET/POST/PUT/DELETE /api/v1/admin/problems/{id}/test-cases...`. `CreateTestCaseRequest`/`UpdateTestCaseRequest` = `{input, expectedOutput, isSample, order, points}`; `TestCaseResponse` = `{id, input, expectedOutput, isSample, order, points}`.
- `CreateProblemRequest` = `{title, statement, difficulty, timeLimitMs, memoryLimitKb, tags[]}`; `UpdateProblemRequest` adds optional `published`; `ProblemCreatedResponse` = `{id, slug}`.
- `Problem` entity has `published`, `createdBy`, `createdAt`, `updatedAt`, `tags`, `testCases`. `ExecutionJob` has `lockedBy`, `lockedAt`, `leaseExpiresAt`, `retryCount`, `maxRetries`, `createdAt`, and `hasActiveLease()`/`retriesExhausted()`.
- Authorization is enforced at **both** the controller (`@PreAuthorize("hasRole('ADMIN')")`) and the service layer (AGENTS §10), using the existing `ADMIN_AUTHORITY` convention.
- The JWT already carries `roles` (e.g. `ROLE_ADMIN`); the Phase 11 client decodes it (`auth/decodeJwt.ts`). No `/me` endpoint is needed.
- **No schema migration** is required. Flyway migrations are immutable (AGENTS §23).

## 4. Backend

### 4.1 New endpoints

| Method | Path | Auth | Query/body | Response |
|---|---|---|---|---|
| GET | `/api/v1/admin/problems` | ADMIN | `published?` (bool), `search?`, `page` (0), `size` (20) | `PagedResponse<AdminProblemSummaryResponse>` |
| GET | `/api/v1/admin/problems/{id}` | ADMIN | — | `AdminProblemDetailResponse` |
| GET | `/api/v1/admin/submissions` | ADMIN | `problemId?`, `userId?`, `status?`, `verdict?`, `page` (0), `size` (20) | `PagedResponse<SubmissionSummaryResponse>` |
| GET | `/api/v1/admin/system/queue-status` | ADMIN | — | `QueueStatusResponse` |

- `AdminProblemSummaryResponse` = `{id, slug, title, difficulty, published, testCaseCount, tags[], createdAt, updatedAt}`.
- `AdminProblemDetailResponse` = `{id, slug, title, statement, difficulty, timeLimitMs, memoryLimitKb, published, createdBy, tags[], testCaseCount, createdAt, updatedAt}`.
- `QueueStatusResponse` = `{queued, activeLease, staleLease, retried, oldestQueuedAgeSeconds, stale[]}` where `stale` is a bounded list (`staleJobLimit`, default 50) of `{submissionId, status, lockedBy, leaseExpiresAt, retryCount, ageSeconds}`.
- `AdminProblemSummaryResponse` is ordered by `updatedAt DESC`; admin submissions by `submittedAt DESC`.

### 4.2 Queue-status derivation (DB-only, explicitly NOT RabbitMQ)
- `queued` = count of `execution_jobs` whose submission status is `SUBMITTED` or `QUEUED`.
- `activeLease` = count of jobs with `lease_expires_at > now()` and submission non-terminal.
- `staleLease` = count of jobs with `lease_expires_at <= now()` and submission non-terminal.
- `retried` = count of jobs with `retry_count > 0`.
- `oldestQueuedAgeSeconds` = now − min(`createdAt`) over queued jobs, or `null` if none.
- `stale` = the (up to limit) stale jobs, oldest lease first.
- The endpoint's Javadoc and the PRD note that this reflects DB lease state, not the broker's queue depth (honesty rule, AGENTS §11/§14 spirit).

### 4.3 Layering
- Controllers (thin) → services → repositories. Controllers never touch repositories.
- New services: `AdminProblemQueryService`(+`Impl`), `AdminSubmissionService`(+`Impl`), `QueueStatusService`(+`Impl`). Business/authorization logic in services.
- `ProblemRepository` already extends `JpaSpecificationExecutor`; add a specification-based admin query. `SubmissionRepository` gains an admin specification query (or a dedicated `@Query`); `ExecutionJobRepository` gains counting/top-N queries (JPQL via `@Query`).
- DTOs are `record`s; entities are never returned from controllers.

## 5. Frontend

### 5.1 Guarding
- `RequireAdmin` renders children only when `user.roles.includes('ROLE_ADMIN')`; otherwise a "Forbidden (admin only)" view. It sits inside `RequireAuth` (must be logged in first).
- NavBar shows an **Admin** link only for admins.
- No client-side gating is treated as security — the backend enforces it; the guard is UX only.

### 5.2 Routes and pages
| Path | Page | Purpose |
|---|---|---|
| `/admin/problems` | `AdminProblemListPage` | list all (incl. unpublished), filter by published/search, paginate; "New problem" button |
| `/admin/problems/new` | `AdminProblemCreatePage` | create form → on success navigate to edit page |
| `/admin/problems/:id` | `AdminProblemEditPage` | edit metadata; test-case table (add/edit/delete, sample toggle); publish/unpublish toggle; link to public `/problems/:slug` |
| `/admin/submissions` | `AdminSubmissionsPage` | filters (problemId, userId, status, verdict) + table → `/submissions/:id` |
| `/admin/queue` | `AdminQueueStatusPage` | counters + stale-jobs table; polled every 5s |

### 5.3 Module structure (additions)
```
frontend/src/
  api/admin.ts                 # admin problem/submission/queue calls + types
  auth/RequireAdmin.tsx
  pages/admin/AdminProblemListPage.tsx
  pages/admin/AdminProblemCreatePage.tsx
  pages/admin/AdminProblemEditPage.tsx
  pages/admin/AdminSubmissionsPage.tsx
  pages/admin/AdminQueueStatusPage.tsx
  components/admin/TestCaseEditor.tsx
  components/admin/PublishToggle.tsx
```
Reuses `Spinner`, `ErrorBanner`, `Pagination`, `VerdictBadge`, `CodeEditor`, `apiFetch`. **No new dependencies.**

### 5.4 Data flow
- React Query keys: `['adminProblems', filters]`, `['adminProblem', id]`, `['adminTestCases', id]`, `['adminSubmissions', filters]`, `['adminQueueStatus']`.
- Mutations (create/update problem, add/update/delete test case, publish) invalidate the relevant admin queries; publish also invalidates public `['problem', slug]`/`['problems']` so the solver view reflects it.
- Queue-status polls with `refetchInterval: 5000` (always-on view).

### 5.5 Test-case authoring UX
- Table of test cases with `isSample`, `order`, `points`; add/edit via a form using `CodeEditor` (or a plain monospace textarea) for input/expected output; delete with confirmation.
- Hidden test content is displayed **only** on admin endpoints (by design) — never on solver surfaces.

## 6. Error handling

- Reuse Phase 11 `ApiError`/`ErrorBanner`. 403 → "Admin only"; 404 → friendly not-found; 409/400 from publish-without-test-cases → show the backend `detail`.
- Publish toggle reflects server truth (re-fetch on success); a failed publish keeps the previous state and shows the error.

## 7. Security

- All four endpoints require `ROLE_ADMIN` at controller **and** service layer. Unauthenticated → 401; authenticated non-admin → 403.
- Unpublished problems remain invisible to solvers; admin listing is the only place they appear.
- Hidden test case content is returned only by admin endpoints, which is why they are strictly guarded.
- No secrets; no schema change; no new dependencies.

## 8. Testing

### 8.1 Backend
- **Unit** (Mockito): queue-status aggregation and stale-boundary logic (lease exactly at/before `now`), counts, `oldestQueuedAgeSeconds` null-when-empty; admin problem list filtering; service-layer authorization rejects non-admins.
- **Integration** (Testcontainers, `docker-tests` profile): each new endpoint returns 401 anonymously and 403 for a normal user; 200 with correct shape for an admin; unpublished problems appear only in the admin list; queue-status reflects seeded `execution_jobs` (active vs stale vs queued).
- `mvn -B verify` and `mvn -B verify -P docker-tests` must pass.

### 8.2 Frontend
- Component tests: `RequireAdmin` admits admin / blocks non-admin; problem create form posts the right body and navigates; test-case add/delete invalidates; publish toggle calls `PUT` with `published`; submissions filters map to query params; queue page renders counters + stale rows.
- `npm run lint && npm run test && npm run build` must pass.

### 8.3 Live verification
- Extend the Phase 11 wrapper: register+promote an admin, create a problem, add a sample + a hidden test case, publish, fetch it via the admin list/detail, confirm the public list shows it, open the admin submissions browser, and fetch queue-status — assert each step `PASS`, then tear down.

## 9. Documentation

- `PRD.md` §7.7/Phase 12: mark Complete, record the expanded scope and the four new endpoints and the DB-derived (not broker) nature of queue-status.
- `README.md`: admin area overview + how to exercise it.

## 10. Increments (each leaves the repo green)

1. Backend: admin problem read endpoints (`GET /admin/problems`, `GET /admin/problems/{id}`) + services + DTOs + tests.
2. Backend: admin submissions list + queue-status + services + DTOs + tests.
3. Frontend: `RequireAdmin`, admin problem list/create/edit + test-case management + publish toggle, NavBar link.
4. Frontend: submissions browser + queue-status view.
5. Live verification extension + PRD/README updates.

## 11. Open questions

None outstanding. Resolved during brainstorming: expanded scope (authoring + browser + queue); four dedicated admin read endpoints; queue-status = DB-derived counts + bounded stale list (no RabbitMQ dependency).
