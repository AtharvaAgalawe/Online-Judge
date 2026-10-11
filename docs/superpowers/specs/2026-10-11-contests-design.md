# Contests — Design

Date: 2026-10-11
Status: Approved in chat; pending user review of this written spec
Scope: `common`, `backend`, `frontend` (no changes to `execution-worker`), plus PRD updates.

## 1. Context

The PRD lists Contests as a P2 feature and the V1 schema already contains
`contests`, `contest_problems`, and `contest_participants` (PRD §13) — but no Java or
frontend contest code exists. Today problem visibility is a single boolean
`problems.is_published`; unpublished problems are invisible and unsubmittable (404)
everywhere. The "hidden during a contest, public after" behavior is therefore a new
visibility mechanism layered on top of the existing flag.

Decisions already made with the user:

1. Contest problems are **unpublished problems**. A **public** contest auto-publishes its
   problems when it ends (they then appear on `/problems`); a **private** contest's
   problems are never published.
2. **Private = admin-only and unlisted** — no participants, no public visibility.
3. **Full Codeforces-style standings**: registration, points per problem, penalty for
   wrong attempts, frozen standings.
4. Admin can **create problems inline** in the contest editor and **attach existing
   unpublished problems**.

Architecture approach decisions (approved):

- **Auto-publish via scheduled job** (not query-time effective visibility) — public
  browsing code keeps its exact `publishedOnly()` semantics.
- **Standings computed on demand** from `submissions` (Java-side aggregation over an
  index-backed fetch) with a short Redis cache-aside (15s); no materialized table.
- **Contest-scoped endpoints** for statements; the existing submission endpoint gains
  one contest-aware validation branch.

## 2. Goals

- Admin creates/edits contests (name, description, start/end, private flag, freeze
  minutes) and populates them with problems (inline creation or attach unpublished).
- Public users see a `/contests` list (Upcoming / Running / Past) and a contest page
  with problems, registration, and standings.
- Registered participants can view contest problem statements and submit during the
  running window; verdicts flow through the existing judging pipeline unchanged.
- Public contest end → attached problems become published automatically → visible on
  `/problems`.
- Private contests are admin-only, unlisted, and their problems never publish.

## 3. Non-Goals (this iteration)

- Rating/Elo changes (PRD §36 keeps this as future work), hacks/challenges, pretests vs
  system tests (single test set), virtual participation, teams, contest announcements.
- Attaching already-published problems to a contest (attach validates unpublished).
- A problem in more than one contest (enforced: at most one).
- Registration limits/capacity, email notifications, countdown push updates.

## 4. Data Model

Existing V1 tables are reused unchanged except for additions. Migration
`V6__contests.sql`:

```sql
ALTER TABLE contests
  ADD COLUMN is_private BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN freeze_minutes INT NOT NULL DEFAULT 60,
  ADD COLUMN problems_published_at TIMESTAMPTZ;

ALTER TABLE contests
  ADD CONSTRAINT chk_contests_times CHECK (end_time > start_time);
ALTER TABLE contests
  ADD CONSTRAINT chk_contests_freeze CHECK (freeze_minutes >= 0);

CREATE INDEX idx_contests_times ON contests (start_time, end_time);
CREATE UNIQUE INDEX uq_contest_problems_problem ON contest_problems (problem_id);
CREATE INDEX idx_submissions_problem_time ON submissions (problem_id, submitted_at);
```

Index justification (PRD §8): `idx_contests_times` serves the public listing ordered by
time; `uq_contest_problems_problem` enforces one-contest-per-problem AND serves the
submission-validation lookup by `problem_id` (the unique index makes a separate
non-unique index redundant); `idx_submissions_problem_time` serves the standings fetch.
`chk_contests_times` validates existing rows — the dev database has none, so the
migration applies cleanly; if any violating row exists, the migration fails loudly
(acceptable for a pre-launch schema).

New entities in `common` (`com.onlinejudge.common.entity`), following existing entity
conventions (JPA annotations, `@Enumerated(STRING)` where applicable):

- `Contest` — `id`, `name`, `description`, `startTime`/`endTime` (`Instant`),
  `createdBy` (`@ManyToOne User`, not updatable), `createdAt`, `isPrivate`,
  `freezeMinutes`, `problemsPublishedAt`.
- `ContestProblem` — `@EmbeddedId ContestProblemId(contestId, problemId)`, `points`,
  `displayOrder`; `@ManyToOne Contest`, `@ManyToOne Problem`.
- `ContestParticipant` — `@EmbeddedId ContestParticipantId(contestId, userId)`,
  `registeredAt`.

`ContestStatus` enum (`UPCOMING`, `RUNNING`, `ENDED`) lives in `common`
(`com.onlinejudge.common.enums`), derived by a pure function
`ContestStatus.of(start, end, now)` — never stored.

## 5. Lifecycle, Registration, Admin Rules

- **Status**: `now < start` → UPCOMING; `start <= now < end` → RUNNING; `now >= end` →
  ENDED. All times stored/compared in UTC (`TIMESTAMPTZ`); API serializes ISO-8601 with
  offset.
- **Registration** (public contests only): opens when the contest is created; closes at
  `start_time`. `POST /contests/{id}/register` is idempotent (already registered →
  200 with the same body). `DELETE` unregisters; allowed only before start. Any
  authenticated user; private contests are not registerable (admin-only).
- **Admin state guards** (throw `BusinessRuleException` → 400, matching the existing
  "publish with zero test cases" precedent): editing metadata, attaching/removing
  problems, inline problem creation, and contest deletion are allowed **before start
  only**. After start, all such operations are rejected with a clear message.
- **Attach validation**: the problem must be unpublished (`is_published = false`) and
  not already attached to any contest (unique index; violation → 409
  `DuplicateResourceException` via service pre-check + DB constraint).
- **Delete contest** (before start): cascade removes `contest_problems` and
  `contest_participants` (existing `ON DELETE CASCADE`); attached problems are left
  untouched (still unpublished).
- **Inline problem creation**: creates an unpublished problem with `createdBy` = the
  admin and an auto-generated unique slug (existing `SlugGenerator`), then attaches it
  with the given points. Test cases are managed afterward via the existing
  `/api/v1/admin/problems/{id}/test-cases` endpoints — no new test-case API.

## 6. Visibility & Submission Rules

- **Public browsing is unchanged**: `ProblemSpecifications.publishedOnly()` and the
  published-filter in `ProblemServiceImpl`/`SubmissionCreator` keep their exact
  semantics; contest problems are simply unpublished until the job publishes them.
- **Auto-publish job** (`ContestProblemPublisher`, backend, `@Scheduled(fixedDelay =
  60_000)`): this is the codebase's **first scheduled task** — add `@EnableScheduling`
  in a config class (PRD §23's reconciler is not yet implemented). For each contest
  where `is_private = false`, `end_time <= now`, and `problems_published_at IS NULL`:
  publish every attached problem that is still unpublished **and** has ≥ 1 test case
  (INFO log per problem); problems with zero test cases are skipped with a WARN log and
  left unpublished; then set `problems_published_at = now()` regardless. One
  transaction per contest; idempotent by the marker; private contests are never
  selected.
- **Contest problem statement access** — `GET /contests/{id}/problems/{slug}` allowed
  when: caller is admin, OR (contest is public AND RUNNING AND caller is a registered
  participant), OR (contest ENDED AND the problem is published). Otherwise
  `ResourceNotFoundException` (404; keeps private/locked contests indistinguishable
  from missing). Before start the problem list is not revealed.
- **Submission validation** — one new branch in `SubmissionCreator.create`: if the
  problem is unpublished, look up the contest attached to it (unique by design).
  - No attached contest → 404 (existing behavior, unchanged).
  - Public contest RUNNING: caller must be a registered participant, else throw the new
    `ContestRegistrationRequiredException` → 403 with the message "Register for this
    contest to submit" (new `@ExceptionHandler` in `GlobalExceptionHandler`, message
    passed through — contains nothing sensitive). Admin bypasses registration (for
    testing); admin submissions never appear in standings unless the admin is also
    registered.
  - Private contest RUNNING: admin only; non-admin → 404 (private stays invisible).
  - Contest not running (upcoming/ended-not-yet-published): 404 as today. The ≤60s
    window between `end_time` and the job's publish is an accepted limitation.
- Rate limiting, idempotency, and the judging pipeline are untouched.

## 7. API Surface (`/api/v1`)

All request DTOs are immutable records with Bean Validation; responses are records;
controllers thin (AGENTS.md §7). Public contest GETs are unauthenticated like problem
GETs.

### Public

| Endpoint | Auth | Response / behavior |
|---|---|---|
| `GET /contests?status&page&size` | none | Paged `ContestSummary`; public only (`is_private = false`), ordered by `start_time` desc |
| `GET /contests/{id}` | optional | `ContestDetail`; 404 for private unless admin |
| `POST /contests/{id}/register` | auth | 200 `{registered: true}`; idempotent; 400 after start; 404 private |
| `DELETE /contests/{id}/register` | auth | 200; 400 after start |
| `GET /contests/{id}/problems/{slug}` | auth | `ContestProblemDetail`; access rules in §6 |
| `GET /contests/{id}/standings` | none | `StandingsResponse`; 404 private unless admin |

DTO shapes (exact field names are the frontend contract):

```text
ContestSummary      { id, name, startTime, endTime, status, participantCount, problemCount }
ContestDetail       { id, name, description, startTime, endTime, status, freezeMinutes,
                      participantCount, registered, registrationOpen, problemsRevealed,
                      problems: [ContestProblemSummary] | null }
ContestProblemSummary { problemId, slug, title, difficulty, displayOrder, points, solvedByMe }
ContestProblemDetail  { problemId, slug, title, statement, difficulty, timeLimitMs,
                        memoryLimitKb, points, contestId, contestName, sampleTestCases }
StandingsResponse   { frozen, frozenAt, rows: [ { rank, username, totalPoints, penalty,
                      problems: [ { problemId, solved, wrongAttempts, acceptedAtMinute } ] } ] }
```

`registered`/`registrationOpen` reflect the current caller (anonymous → false).
`solvedByMe` reflects the caller's own first-AC per problem (anonymous → false).
`problemsRevealed` is false before start for non-admins; problems are revealed to
admins at all times (needed for management), and `problems` is null only while
unrevealed to the caller.

### Admin (`/admin/contests`, class-level `@PreAuthorize("hasRole('ADMIN')")` + service-layer enforcement)

| Endpoint | Behavior |
|---|---|
| `GET /admin/contests?search&status&page&size` | Paged `ContestAdminSummary` (adds `isPrivate`, `problemsPublishedAt`) |
| `POST /admin/contests` | Create; `CreateContestRequest { name, description, startTime, endTime, isPrivate, freezeMinutes }`; validates `endTime > startTime` |
| `PUT /admin/contests/{id}` | Full metadata update; before start only |
| `DELETE /admin/contests/{id}` | Before start only |
| `POST /admin/contests/{id}/problems` | Attach `{ problemId, points }`; validates unpublished + unattached; before start only |
| `POST /admin/contests/{id}/problems/new` | Inline create `{ title, statement, difficulty, timeLimitMs, memoryLimitKb, points }` → returns `{ problemId }`; before start only |
| `PUT /admin/contests/{id}/problems/{problemId}` | `{ points, displayOrder }`; before start only |
| `DELETE /admin/contests/{id}/problems/{problemId}` | Detach (problem survives unpublished); before start only |

## 8. Standings Algorithm (exact)

Service loads terminal submissions (verdict not null, `status` terminal) by registered
participants to attached problems with `submitted_at` in `[start, end)` — served by
`idx_submissions_problem_time` — and aggregates in Java (participant/problem counts are
small at MVP scale; a SQL window-function query is the noted alternative if scale
demands it).

Per (participant, problem):
- `firstAc` = earliest `submitted_at` with verdict `ACCEPTED`; solved iff present.
- `wrongAttempts` = count of terminal non-`ACCEPTED` verdicts with
  `submitted_at < firstAc`; `SYSTEM_ERROR` excluded; compilation errors count.
  (0 when never solved.)
- Points = `contest_problems.points` when solved, else 0.
- Penalty contribution = solved ? `floor((firstAc - start) / 1 min) + 10 *
  wrongAttempts` : 0.

Row: `totalPoints` = Σ points; `penalty` = Σ contributions; tiebreak = last AC time
(earliest wins). Ranking: `totalPoints` DESC, `penalty` ASC, last-AC ASC; **competition
ranking** for ties (1, 2, 2, 4).

**Freeze**: if `freeze_minutes > 0` and the contest is RUNNING and
`now >= end - freeze_minutes`, compute with submissions where
`submitted_at < end - freeze_minutes`, set `frozen = true`, `frozenAt = end -
freeze_minutes`. After end: full standings, `frozen = false`. Snapshot semantics — no
"?" placeholders (documented simplification vs Codeforces).

**Cache**: Redis cache-aside key `oj:standings:<contestId>` with 15s TTL, JSON value;
fail-open (compute and serve if Redis is unavailable), mirroring the fail-open Redis
usage in `IdempotencyStore`/`SubmissionRateLimiter`. TTL-only invalidation (staleness
≤ 15s acceptable).

## 9. Frontend

Routes (React Router, existing Layout/theme):

- `/contests` — status tabs (Upcoming / Running / Past via `?status=`), blueprint table:
  name, start, duration, status chip, participants; empty states.
- `/contests/:id` — header: name, status chip, client-side ticking countdown to
  start/end, description; registration button states (anonymous → "Sign in to
  register"; open → Register; registered → "Registered ✓" + Unregister before start;
  closed after start); sections:
  - **Problems**: letter (A, B, C…), title, difficulty, points, `solvedByMe` ✓;
    locked message before start.
  - **Standings**: rank, user, total points, penalty; per-problem cells `+N` (solved,
    N wrong attempts), `−N` (unsolved, N attempts), `·` (none); frozen badge with
    frozenAt note; React Query `refetchInterval` 15s while running.
- `/contests/:id/problems/:slug` — statement + samples + CodeMirror + language select +
  submit, reusing existing components (`CodeEditor`, `LanguageSelect`, `ErrorBanner`,
  `Spinner`); header shows contest name, points, back link; after end (public) a banner
  links to `/problems/:slug`. Submit uses the existing `createSubmission` API.
- NavBar: "Contests" link for everyone; "Contests" admin link for admins.

Admin pages (`RequireAdmin`):

- `/admin/contests` — all contests (public + private) with status, private chip,
  problems-published marker.
- `/admin/contests/new` and `/admin/contests/:id` — metadata form (`datetime-local`
  inputs converted to/from ISO UTC, private checkbox, freeze minutes); Problems panel:
  attach picker (searchable list of unpublished, unattached problems), inline create
  form (title, statement, difficulty, limits, points; on success link "Manage test
  cases" → existing `/admin/problems/:id`), per-row points/order editing, detach.
  Before-start guards surfaced as disabled UI + server errors rendered via
  `ErrorBanner`.
- New API module `frontend/src/api/contests.ts` + types in `types.ts`; admin functions
  in the same module or `admin.ts` following existing conventions.

## 10. Testing

Backend unit: `ContestStatus` derivation; standings aggregation (points, penalty,
wrong-attempt counting, freeze snapshot, competition ranking, tiebreak); auto-publish
job eligibility + idempotency (marker set, zero-test-case skip); submission-validation
branch (registered allowed / unregistered 403 / no contest 404 / private non-admin 404 /
admin bypass).

Backend integration (Testcontainers): admin contest CRUD + state guards; attach
validation (published problem rejected, double attach 409); registration windows +
idempotency; private contests absent from public endpoints; contest problem absent from
`/problems` during the contest and present after the job runs (job invoked directly);
registered-participant submission during a running contest succeeds through the normal
pipeline; standings endpoint shape + freeze flag.

Frontend (Vitest + Testing Library): contests list (tabs, empty state); contest detail
(register button states, locked problems, standings rendering incl. frozen badge and
cell formats); contest problem page (renders statement, submit calls the API with the
right problemId); admin editor (form validation, attach picker, inline create flow)
against mocked APIs.

Manual: end-to-end walkthrough with the backend running — create contest → attach
problems → register → submit → standings → let end pass → job publishes → problems page
shows them.

## 11. Documentation Updates

- PRD §7: new subsection §7.8 Contests (functional requirements as above).
- PRD §9: note Contests P2 is now implemented (status row).
- PRD §13/§14: V6 additions + entity relationships already present.
- PRD §15: contest endpoint tables (public + admin).
- PRD §24: standings cache row (15s TTL).
- PRD §27: auto-publish job logging/monitoring note.
- PRD §31/§32: frontend + admin contest pages.
- No AGENTS.md changes (no new conventions or architecture).

## 12. Accepted Limitations & Risks

- Up to 60s publish lag after a contest ends (submissions in that window → 404).
- Frozen standings are a snapshot at freeze time, not Codeforces' "?" live placeholder.
- Private contests are admin-only (no participant access) — per the user's choice.
- Admin can manually publish a contest problem early (trusted action; breaks hiding).
- One contest per problem (unique index) — re-use across contests is future work.
- Standings aggregation is O(submissions in window) in Java; revisit with a
  materialized table if contest sizes grow.
- A contest with a zero-test-case problem: that problem silently stays unpublished
  after the contest (WARN logged); admin must fix and publish manually.
