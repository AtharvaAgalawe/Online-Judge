# Online Judge Platform — Product Requirements Document (PRD)

**Version:** 1.0
**Status:** Approved for implementation
**Owner:** Solo engineering project (B.Tech CSE / Data Science, 3rd year)
**Companion document:** `AGENTS.md` (engineering constitution for coding agents working on this repo)

---

## 1. Product Overview

A production-style Online Judge — a platform where users browse programming problems, submit source code in a language of their choice, have that code compiled and executed against hidden test cases inside isolated Docker sandboxes, and receive a verdict (Accepted, Wrong Answer, Time Limit Exceeded, Memory Limit Exceeded, Compilation Error, Runtime Error, Internal/System Error).

The system is architected as a **modular monolith** with a clearly separable, **independently scalable execution-worker service**. It is not a toy CRUD app: it demonstrates real queue-based asynchronous processing, sandboxed untrusted code execution, transactional correctness under concurrency, retry/idempotency handling, caching, and observability — the same problems real judge systems (Codeforces, HackerRank, LeetCode OJ, internal assessment platforms) solve.

## 2. Problem Statement

Running arbitrary, untrusted user code safely, quickly, and correctly at the same time is hard because three concerns pull in different directions:

- **Correctness** — a submission must be judged exactly once, against the right test data, with an accurate resource measurement, even when workers crash or messages are redelivered.
- **Security** — the code is adversarial by default. It may try to fork-bomb, read the host filesystem, open sockets, exhaust disk, or crash the runtime.
- **Throughput** — many users submit concurrently; the system must queue, execute, and report back without one slow/heavy submission starving others.

A student project that simply "runs code in a subprocess and compares stdout" solves none of this. This PRD specifies the actual architecture required to solve all three.

## 3. Goals

1. Judge submissions in 6+ languages with accurate verdicts and resource accounting.
2. Never execute untrusted code on the host process — always inside a locked-down, ephemeral Docker container.
3. Handle worker crashes, duplicate queue deliveries, and DB failures without corrupting submission state or double-judging.
4. Support many concurrent submissions via an async, queue-based pipeline that decouples the API from execution.
5. Provide a real admin workflow for authoring problems and hidden test cases.
6. Be observable: every submission's journey is traceable through logs and metrics.
7. Be testable: unit, integration (Testcontainers), and CI-gated.

## 4. Non-Goals (MVP)

- Multi-language plagiarism detection.
- Real-time collaborative contests / live rating system (Elo-style rating engine).
- Custom checkers / special judges (partial credit scoring) — noted as a future enhancement.
- Horizontal multi-region deployment, Kubernetes autoscaling — designed for, not built, in MVP.
- Support for arbitrary user-installed packages/dependencies in submitted code.
- Mobile app.

## 5. Target Users

| Persona | Description | Primary needs |
|---|---|---|
| **Solver** | Student/engineer practicing problems | Browse problems, submit code, fast feedback, history, stats |
| **Problem Setter / Admin** | Creates problems and test cases | CRUD on problems, upload hidden test cases, publish/unpublish |
| **Platform Operator** (also admin) | Keeps the judge healthy | Queue depth, worker health, failed-job visibility |

## 6. Core User Journeys

1. **Solve a problem**: Register/login → browse problem list (filter by tag/difficulty) → open a problem → read statement + sample tests → write code in the editor → pick language → submit → poll/observe status transitions → see verdict + which test case failed (if any) → view it in submission history.
2. **Author a problem** (admin): Login as admin → create problem (statement, limits, tags) → add sample + hidden test cases → publish → verify by submitting a reference solution.
3. **Investigate a stuck job** (admin/operator): Open admin queue-status view → see a submission stuck in `RUNNING` past its lease → system auto-recovers it or surfaces it for manual retry.

## 7. Functional Requirements

Each major feature below follows: *Why it exists → User story → Inputs → Outputs → Dependencies → Edge cases → Acceptance criteria.*

### 7.1 Problem Browsing & Detail View
- **Why**: Core discovery surface; users need to find problems matching their level.
- **User story**: As a solver, I want to filter problems by tag/difficulty and paginate through them so I can find something to practice.
- **Inputs**: query params `page`, `size`, `difficulty`, `tag`, `search`.
- **Outputs**: paginated list `{id, slug, title, difficulty, tags[], acceptanceRate}`.
- **Dependencies**: `problems`, `tags`, `problem_tags`, Redis cache (read-through) for list/detail.
- **Edge cases**: unpublished problems must never appear to non-admins; empty result set; tag with zero problems.
- **Acceptance criteria**: unauthenticated users can browse only `is_published = true` problems; response time p95 < 150ms with cache warm.

### 7.2 Submission Creation
- **Why**: The core write path of the whole system.
- **User story**: As a solver, I want to submit my code and immediately get a submission ID so I can track its progress without blocking on judging.
- **Inputs**: `problemId`, `languageId`, `sourceCode` (max 65KB), optional `Idempotency-Key` header.
- **Outputs**: `202 Accepted` with `{submissionId, status: "SUBMITTED"}`.
- **Dependencies**: `submissions` table, Redis (idempotency key store, rate limiter), RabbitMQ producer.
- **Edge cases**: duplicate submit due to client retry (network blip) → same idempotency key must return the *same* submission, not create a second one; submission to an unpublished/nonexistent problem → 404; source code exceeding size limit → 400; user exceeding submission rate limit → 429.
- **Acceptance criteria**: submission row is persisted and queued atomically from the caller's perspective (see §22/§23); duplicate idempotency key within its TTL never creates a second row.

### 7.3 Code Execution (Worker)
- **Why**: This is the actual judging engine and the highest-risk component (executes attacker-controlled code).
- **User story**: As a solver, I want my code compiled and run against every hidden test case with the same limits every other user gets.
- **Inputs**: a job message `{submissionId, problemId, languageId, timeLimitMs, memoryLimitKb, testCaseIds[]}`.
- **Outputs**: updated `submissions` row (status, verdict, timeUsedMs, memoryUsedKb, failedTestCaseId) + one `submission_results` row per test case attempted.
- **Dependencies**: Docker daemon, language runtime images, shared Postgres, `execution_jobs` lease table.
- **Edge cases**: covered exhaustively in §17 and §22 (compile failure, crash, TLE, MLE, worker death, duplicate delivery).
- **Acceptance criteria**: no submission is ever judged twice with two different terminal verdicts persisted; every execution is time- and memory-bounded regardless of what the code does; no submitted code can read/write outside its ephemeral container or reach the network.

### 7.4 Verdict & Result Reporting
- **Why**: Users need actionable feedback, not just "failed."
- **User story**: As a solver, I want to know exactly which test case failed and how long/much memory my accepted solution used.
- **Inputs**: submission ID.
- **Outputs**: `{status, verdict, timeUsedMs, memoryUsedKb, failedTestCase: {index, isSample}, compileError?}`. Hidden test case *content* is never returned — only pass/fail + index.
- **Dependencies**: `submissions`, `submission_results`, `test_cases`.
- **Edge cases**: request for a submission owned by another user (non-admin) → 403; request while still `QUEUED`/`RUNNING` → return current status, not an error.
- **Acceptance criteria**: verdict is stable once `status = COMPLETED` (or a terminal failure status) — never mutates after judged_at is set.

### 7.5 Submission History
- **Why**: Users track progress; admins audit behavior.
- **User story**: As a solver, I want to see all my past submissions for a problem to see my progress.
- **Inputs**: `userId` (implicit from auth), optional `problemId` filter, pagination.
- **Outputs**: list of submissions with status/verdict/timestamps (source code included only for the owner or an admin).
- **Dependencies**: `submissions` indexed on `(user_id, submitted_at DESC)`.
- **Acceptance criteria**: a user can never see another user's source code via this endpoint.

### 7.6 Statistics
- **Why**: Feedback loop / gamification-lite; also validates the judge is producing sane data.
- **User story**: As a solver, I want to see my solved count and a problem's acceptance rate.
- **Inputs**: `username` or `problemSlug`.
- **Outputs**: `{solvedCount, totalSubmissions, acceptedSubmissions}` at user level; `{totalSubmissions, acceptedCount, acceptanceRate}` at problem level.
- **Dependencies**: Redis cache (aggregates are expensive to recompute per request; recomputed on submission completion, cached with short TTL, invalidated on new terminal verdict).
- **Acceptance criteria**: stats are eventually consistent within a few seconds of a submission completing; never blocks the judging path.

### 7.7 Admin Problem & Test Case Management
- **Why**: Without this, there is no content and no way to keep hidden tests hidden.
- **User story**: As an admin, I want to create a problem, attach hidden test cases, and publish it only when I'm confident it's correct.
- **Inputs**: problem metadata (title, statement, limits, tags), test case `{input, expectedOutput, isSample, order, points}`.
- **Outputs**: created/updated resource IDs.
- **Dependencies**: `ROLE_ADMIN` authorization, `problems`, `test_cases`.
- **Edge cases**: deleting a test case that has historical `submission_results` referencing it → soft-handled via `ON DELETE CASCADE` on results only, not by silently rewriting judged history; publishing a problem with zero test cases → rejected (400).
- **Acceptance criteria**: only `ROLE_ADMIN` can reach any `/admin/**` endpoint; unpublished problems are fully invisible to solvers, including via direct slug URL.

## 8. Non-Functional Requirements

| Category | Requirement |
|---|---|
| Availability | API remains responsive even if the worker pool is fully saturated (submissions queue instead of failing) |
| Consistency | Submission status transitions are monotonic and never regress except via explicit reconciliation |
| Isolation | No submitted code can access the host, the network, or another submission's data |
| Latency | p95 API response (non-judging endpoints) < 200ms; judging latency depends on language/queue depth, target p95 < 5s end-to-end for a typical problem under light load |
| Durability | A submission that has been `202 Accepted` is never silently lost, even across a worker crash |
| Observability | Every submission's lifecycle is reconstructable from logs via a correlation ID |
| Testability | Core business logic (verdict computation, lifecycle transitions, idempotency) is unit-tested without Docker; the sandbox itself is integration-tested with real Docker |

## 9. Feature Prioritization

| Priority | Features |
|---|---|
| P0 (MVP, must work end-to-end) | Auth, problem CRUD (admin), problem browse/detail, submission creation, RabbitMQ queue, worker + Docker sandbox for ≥2 languages, verdict engine, submission history, basic frontend |
| P1 | Redis caching, structured logging + Actuator metrics, statistics, rate limiting, CI (build+test), OpenAPI docs |
| P2 | Contests, admin queue-status dashboard, SSE live status, CD (image publish), Testcontainers integration suite |
| P3 (future) | Custom checkers/partial scoring, plagiarism detection, rating system, gVisor/Kata hardening, k8s |

## 10. System Architecture

**Style**: Modular monolith for the web application + a **separately deployable, independently scalable execution-worker service**. This is deliberate: microservices-per-noun would be premature complexity for this scope (violates "correctness/maintainability over cleverness"), but the *one* component with genuinely different scaling and risk characteristics — code execution — is split out because it (a) needs Docker socket access the API should never have, (b) needs to scale on CPU-bound container execution independent of HTTP traffic, and (c) is the security-critical boundary.

```
┌─────────────────┐        ┌──────────────────────┐
│   React + TS     │  HTTP  │   Backend (Spring)    │
│   Frontend        │───────▶│   api / service /     │
│                   │◀───────│   domain / persistence│
└─────────────────┘  JSON   └──────────┬───────────┘
                                       │  publishes job
                                       │  (after commit)
                                       ▼
                              ┌──────────────────┐
                              │    RabbitMQ        │
                              │  submission.jobs    │
                              │  (+ DLQ, retry TTL)  │
                              └────────┬───────────┘
                                       │ consumes
                                       ▼
                              ┌──────────────────────┐
                              │  Execution Worker      │
                              │  (Spring Boot, N pods) │
                              │  - claims job (lease)   │
                              │  - spawns Docker sandbox│
                              │  - writes result to DB  │
                              └──────────┬───────────┘
                                       │ shared schema
                                       ▼
                              ┌──────────────────┐
                              │   PostgreSQL        │
                              └──────────────────┘
                                       ▲
                              ┌──────────────────┐
                              │      Redis          │
                              │ cache / rate-limit /  │
                              │ idempotency / JWT      │
                              │ blacklist              │
                              └──────────────────┘
```

Async boundary: exactly at the RabbitMQ queue. Everything to the left (API, DB write, publish) is synchronous and transactional from the caller's point of view (they get a submission ID immediately). Everything to the right (consume → sandbox → judge → persist result) is asynchronous, retryable, and independently scalable.

## 11. Component Responsibilities

| Component | Responsibility | Must NOT do |
|---|---|---|
| `api` layer (controllers) | HTTP binding, request validation, DTO mapping, auth context extraction | Contain business logic, touch repositories directly |
| `service` layer | Use-case orchestration, transaction boundaries, publishing events | Know about HTTP (no `HttpServletRequest`), know about Docker |
| `domain` (entities/enums) | Model invariants (e.g., valid status transitions) | Depend on Spring MVC or AMQP types |
| `repository` layer | Spring Data JPA access | Contain business rules |
| `messaging` | Build/publish job messages, consume result acks | Contain judging logic |
| `execution-worker` | Claim jobs, run Docker sandbox, compute verdict, persist result | Expose any public HTTP API to the internet; trust the host network |
| `common` module | Shared entities/DTOs/enums used by both backend and worker | Contain business logic specific to one side |
| React frontend | Present state, poll/subscribe to status, code editor | Compute verdicts, trust client-side timing |

## 12. Data Flow

1. Client `POST /api/v1/submissions` → controller validates DTO → service opens a transaction → inserts `submissions` row (`status=SUBMITTED`) and `execution_jobs` row → transaction commits → **after** commit (`@TransactionalEventListener(AFTER_COMMIT)`) a job message is published to RabbitMQ → controller returns `202` with the submission ID (already available before publish, since the ID is assigned at insert).
2. Worker consumer receives the message → attempts to acquire the lease row (`UPDATE execution_jobs SET locked_by=?, lease_expires_at=? WHERE submission_id=? AND locked_by IS NULL OR lease_expires_at < now()`) → on success, transitions `status → PICKED_UP`.
3. Worker pulls problem limits + test cases (cached in Redis, read-through from Postgres) → transitions `status → COMPILING` → runs compile step in an ephemeral container → on failure, terminal `COMPILATION_ERROR`, ack message, done.
4. On successful compile, `status → RUNNING` → for each test case (stop-on-first-failure for hidden tests; run-all for samples), spin an ephemeral run container, capture stdout/stderr/exit code/time/memory → compare output.
5. `status → EVALUATING` while aggregating results → write `submission_results` rows + final `submissions` update (`status=COMPLETED`, `verdict=...`) in one transaction → **only then** ack the RabbitMQ message.
6. Frontend polls `GET /submissions/{id}/status` (or subscribes via SSE) until a terminal status is observed.

## 13. Database Schema

All tables use `BIGSERIAL`/`SERIAL` PKs, `TIMESTAMPTZ` timestamps, and explicit FKs. Naming: `snake_case`.

```sql
-- Identity -------------------------------------------------------
CREATE TABLE roles (
  id            SERIAL PRIMARY KEY,
  name          VARCHAR(30) NOT NULL UNIQUE          -- ROLE_USER, ROLE_ADMIN
);

CREATE TABLE users (
  id             BIGSERIAL PRIMARY KEY,
  username       VARCHAR(50)  NOT NULL UNIQUE,
  email          VARCHAR(255) NOT NULL UNIQUE,
  password_hash  VARCHAR(255) NOT NULL,
  is_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
  user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_id  INT    NOT NULL REFERENCES roles(id),
  PRIMARY KEY (user_id, role_id)
);

-- Catalog ----------------------------------------------------------
CREATE TABLE languages (
  id                     SERIAL PRIMARY KEY,
  name                   VARCHAR(50)  NOT NULL UNIQUE,   -- "Java 21", "Python 3.12", "C++17"
  source_filename        VARCHAR(100) NOT NULL,          -- "Main.java"
  compile_cmd            TEXT,                           -- NULL for interpreted languages
  run_cmd                TEXT NOT NULL,
  docker_image           VARCHAR(200) NOT NULL,
  time_limit_multiplier  NUMERIC(3,2) NOT NULL DEFAULT 1.0,
  is_enabled             BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE tags (
  id    SERIAL PRIMARY KEY,
  name  VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE problems (
  id              BIGSERIAL PRIMARY KEY,
  slug            VARCHAR(120) NOT NULL UNIQUE,
  title           VARCHAR(200) NOT NULL,
  statement       TEXT NOT NULL,
  difficulty      VARCHAR(20) NOT NULL CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
  time_limit_ms   INT NOT NULL DEFAULT 2000,
  memory_limit_kb INT NOT NULL DEFAULT 262144,
  is_published    BOOLEAN NOT NULL DEFAULT FALSE,
  created_by      BIGINT REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_problems_published ON problems (is_published);

CREATE TABLE problem_tags (
  problem_id  BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
  tag_id      INT    NOT NULL REFERENCES tags(id),
  PRIMARY KEY (problem_id, tag_id)
);

CREATE TABLE test_cases (
  id               BIGSERIAL PRIMARY KEY,
  problem_id       BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
  input            TEXT NOT NULL,
  expected_output  TEXT NOT NULL,
  is_sample        BOOLEAN NOT NULL DEFAULT FALSE,
  display_order    INT NOT NULL DEFAULT 0,
  points           INT NOT NULL DEFAULT 1,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_test_cases_problem ON test_cases (problem_id, display_order);

-- Judging -----------------------------------------------------------
CREATE TABLE submissions (
  id                   BIGSERIAL PRIMARY KEY,
  user_id              BIGINT NOT NULL REFERENCES users(id),
  problem_id           BIGINT NOT NULL REFERENCES problems(id),
  language_id          INT NOT NULL REFERENCES languages(id),
  source_code          TEXT NOT NULL,
  status               VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  verdict              VARCHAR(30),
  time_used_ms         INT,
  memory_used_kb       INT,
  failed_test_case_id  BIGINT REFERENCES test_cases(id),
  error_message        TEXT,
  idempotency_key      VARCHAR(100) UNIQUE,
  version              INT NOT NULL DEFAULT 0,
  submitted_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  judged_at            TIMESTAMPTZ
);
CREATE INDEX idx_submissions_user_time ON submissions (user_id, submitted_at DESC);
CREATE INDEX idx_submissions_problem_user ON submissions (problem_id, user_id);
CREATE INDEX idx_submissions_status ON submissions (status);

CREATE TABLE submission_results (
  id              BIGSERIAL PRIMARY KEY,
  submission_id   BIGINT NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
  test_case_id    BIGINT NOT NULL REFERENCES test_cases(id),
  verdict         VARCHAR(30) NOT NULL,
  time_used_ms    INT,
  memory_used_kb  INT,
  stdout_snippet  VARCHAR(2000),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (submission_id, test_case_id)
);

CREATE TABLE execution_jobs (
  id                BIGSERIAL PRIMARY KEY,
  submission_id     BIGINT NOT NULL UNIQUE REFERENCES submissions(id) ON DELETE CASCADE,
  locked_by         VARCHAR(100),
  locked_at         TIMESTAMPTZ,
  lease_expires_at  TIMESTAMPTZ,
  retry_count       INT NOT NULL DEFAULT 0,
  max_retries       INT NOT NULL DEFAULT 3,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Contests (P2) -------------------------------------------------------
CREATE TABLE contests (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR(200) NOT NULL,
  description  TEXT,
  start_time   TIMESTAMPTZ NOT NULL,
  end_time     TIMESTAMPTZ NOT NULL,
  created_by   BIGINT REFERENCES users(id),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE contest_problems (
  contest_id     BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
  problem_id     BIGINT NOT NULL REFERENCES problems(id),
  display_order  INT NOT NULL DEFAULT 0,
  points         INT NOT NULL DEFAULT 100,
  PRIMARY KEY (contest_id, problem_id)
);

CREATE TABLE contest_participants (
  contest_id     BIGINT NOT NULL REFERENCES contests(id) ON DELETE CASCADE,
  user_id        BIGINT NOT NULL REFERENCES users(id),
  registered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (contest_id, user_id)
);
```

**Key query patterns this schema is optimized for:**
- "My recent submissions" → `idx_submissions_user_time`.
- "Has this user solved this problem?" → `idx_submissions_problem_user` filtered `verdict='ACCEPTED'`.
- "Stuck/reconcilable jobs" → `execution_jobs` where `lease_expires_at < now()`.
- "Sample tests for a problem page" → `idx_test_cases_problem` filtered `is_sample = true`.

**Transaction boundaries:**
- Submission creation: **one transaction** = insert `submissions` + insert `execution_jobs`. Publish happens *after* commit (never inside it — a broker call must never be part of a DB transaction).
- Worker lease acquisition: **one transaction**, a single conditional `UPDATE ... RETURNING`, relying on row-level locking implicit in the `UPDATE` — no explicit `SELECT FOR UPDATE` needed since the predicate is on the row itself.
- Final judgment write: **one transaction** = update `submissions` (status/verdict/metrics) + insert all `submission_results` rows. The RabbitMQ ack is only sent after this transaction commits successfully (manual ack mode).

## 14. Entity Relationships

```
users (1)───(N) submissions (N)───(1) problems (1)───(N) test_cases
  │                    │                    │
  │(N)          (1)    │(1)          (N)    │(N)         (1)
user_roles ── roles     submission_results    problem_tags ── tags
                          (N)───(1) test_cases
submissions (1)───(1) execution_jobs
problems (N)───(N) contests   (via contest_problems)
users (N)───(N) contests      (via contest_participants)
languages (1)───(N) submissions
```

## 15. API Specification

All endpoints under `/api/v1`. Auth via `Authorization: Bearer <JWT>` unless marked public. Errors use RFC 7807 `application/problem+json`: `{type, title, status, detail, instance, errors?: [{field, message}]}`.

### Auth
| Method | Path | Auth | Body | Response | Errors |
|---|---|---|---|---|---|
| POST | `/auth/register` | Public | `{username, email, password}` | 201 `{id, username, email}` | 409 duplicate username/email, 400 validation |
| POST | `/auth/login` | Public | `{username, password}` | 200 `{accessToken, refreshToken, expiresIn}` | 401 bad credentials |
| POST | `/auth/refresh` | Public | `{refreshToken}` | 200 `{accessToken, expiresIn}` | 401 expired/blacklisted |
| POST | `/auth/logout` | User | — | 204 | — |

### Problems
| Method | Path | Auth | Body | Response | Errors |
|---|---|---|---|---|---|
| GET | `/problems?page&size&difficulty&tag&search` | Public | — | 200 paginated list | — |
| GET | `/problems/{slug}` | Public | — | 200 statement + samples + limits | 404 |
| GET | `/problems/{slug}/stats` | Public | — | 200 `{totalSubmissions, acceptedCount, acceptanceRate}` | 404 |
| POST | `/admin/problems` | Admin | `{title, statement, difficulty, timeLimitMs, memoryLimitKb, tags[]}` | 201 `{id, slug}` | 400 |
| PUT | `/admin/problems/{id}` | Admin | same as create (partial); optional `published` is the publish/unpublish toggle and publishing requires ≥1 test case | 200 | 404, 400 |
| DELETE | `/admin/problems/{id}` | Admin | — | 204 (soft: unpublish) | 404 |
| GET | `/admin/problems/{id}/test-cases` | Admin | — | 200 full list incl. hidden | 404 |
| POST | `/admin/problems/{id}/test-cases` | Admin | `{input, expectedOutput, isSample, order, points}` | 201 | 400 |
| PUT | `/admin/problems/{id}/test-cases/{tcId}` | Admin | same | 200 | 404 |
| DELETE | `/admin/problems/{id}/test-cases/{tcId}` | Admin | — | 204 | 404 |

### Languages
| Method | Path | Auth | Response |
|---|---|---|---|
| GET | `/languages` | Public | 200 list of enabled languages |

### Submissions
| Method | Path | Auth | Body | Response | Errors |
|---|---|---|---|---|---|
| POST | `/submissions` | User | `{problemId, languageId, sourceCode}` + optional header `Idempotency-Key` | 202 `{submissionId, status}` | 404 problem/lang, 400 code too large, 429 rate-limited |
| GET | `/submissions/{id}` | User (owner) / Admin | — | 200 full detail | 403, 404 |
| GET | `/submissions/{id}/status` | User (owner) / Admin | — | 200 `{status, verdict}` (lightweight polling) | 403, 404 |
| GET | `/submissions?problemId&page&size` | User (own) / Admin (any `userId`) | — | 200 paginated history | — |
| GET | `/submissions/{id}/stream` | User (owner) | — | SSE stream of status updates | 403, 404 |

### Admin / Observability
| Method | Path | Auth | Response |
|---|---|---|---|
| GET | `/admin/submissions?status&userId` | Admin | filtered submission list |
| GET | `/admin/system/queue-status` | Admin | `{queueDepth, activeWorkers, stuckJobs}` |

**Validation rules** (Bean Validation): `username` 3–30 alnum, `password` ≥ 8 chars, `sourceCode` ≤ 65536 bytes non-blank, `problemId`/`languageId` must reference existing enabled rows.

## 16. Authentication and Authorization

- **Mechanism**: JWT access tokens (short-lived, 15 min) + refresh tokens (7 days, opaque random string stored hashed in Postgres or Redis with TTL).
- **Password storage**: BCrypt (cost factor 12).
- **Roles**: `ROLE_USER`, `ROLE_ADMIN` via `roles`/`user_roles`. Enforced with `@PreAuthorize("hasRole('ADMIN')")` at the service layer (not just controller annotations — see AGENTS.md security rules) so authorization survives even if a controller boundary is bypassed.
- **Token revocation**: logout adds the refresh token (or its hash) to a Redis blacklist with TTL = remaining validity; access-token revocation is not attempted (short lifetime makes it unnecessary — accepted tradeoff for MVP simplicity over building a full revocation list for access tokens).
- **CORS**: explicit allow-list of the frontend origin(s); credentials mode off (tokens sent via header, not cookies, to avoid CSRF surface).
- **Ownership checks**: any submission-scoped endpoint checks `submission.userId == principal.id OR principal.hasRole(ADMIN)` in the service layer, never trusting a path/query `userId` from the client for authorization decisions.

## 17. Submission Lifecycle

```
SUBMITTED → QUEUED → PICKED_UP → COMPILING ─┬─▶ COMPILATION_ERROR   (terminal)
                                             └─▶ RUNNING ─┬─▶ EVALUATING → COMPLETED (terminal, verdict=ACCEPTED|WRONG_ANSWER)
                                                           ├─▶ TIME_LIMIT_EXCEEDED   (terminal)
                                                           ├─▶ MEMORY_LIMIT_EXCEEDED(terminal)
                                                           ├─▶ RUNTIME_ERROR         (terminal)
                                                           └─▶ SYSTEM_ERROR          (terminal, infra failure — retried first, see §23)
```

`status` tracks pipeline stage; `verdict` is the final judgement (only set at a terminal state). `SUBMITTED` and `QUEUED` are effectively the same to the client but distinguished internally: `SUBMITTED` = row exists, not yet published; `QUEUED` = message published, sitting in RabbitMQ.

**Explicit failure-mode handling (as required):**

| Scenario | Handling |
|---|---|
| **Compilation fails** | Worker captures compiler stderr (truncated to 4KB), sets `status=COMPILATION_ERROR`, `error_message=<output>`, no test cases run, message acked. |
| **Code crashes (non-zero exit / signal)** | `verdict=RUNTIME_ERROR` for that test case; if it's the first hidden failure, stop and set submission verdict; stderr captured (truncated) for the user's own reference on sample tests only. |
| **Code exceeds memory** | Container's cgroup memory limit triggers OOM-kill; worker detects exit code 137 / OOM event via `docker inspect`, sets `verdict=MEMORY_LIMIT_EXCEEDED`. |
| **Code exceeds time** | Worker's external watchdog (wall-clock timer per run, independent of the container) sends `docker kill` at `timeLimitMs * language.time_limit_multiplier + fixedBufferMs`; sets `verdict=TIME_LIMIT_EXCEEDED`. |
| **Worker crashes mid-job** | Message remains unacked (manual ack mode) → RabbitMQ redelivers after consumer disconnect is detected. New worker instance attempts lease acquisition; `execution_jobs.lease_expires_at` from the dead worker will have expired (leases are short, e.g. 60s, renewed while actively processing), so the new worker acquires it, increments `retry_count`, and reprocesses. If `retry_count > max_retries`, submission is marked `SYSTEM_ERROR` and the message is dead-lettered for manual inspection. |
| **Queue message duplicated** (at-least-once delivery) | Lease acquisition is the dedup point: `UPDATE execution_jobs SET locked_by=... WHERE submission_id=? AND (locked_by IS NULL OR lease_expires_at < now())`. A second concurrent delivery finds 0 rows updated → it's a no-op, ack and discard. If the submission is already in a terminal state when a duplicate is picked up, the worker acks immediately without redoing work (idempotent by status check). |
| **Database transaction fails** (deadlock/connection loss) during result write | Spring transaction rolls back; message is **not** acked (still in-flight); on requeue, the same idempotent lease/status check applies, so retry is safe. A bounded retry (Resilience4j, 3 attempts, exponential backoff) wraps the DB write before giving up and dead-lettering. |
| **Execution container dies unexpectedly** (Docker daemon issue, OOM at host level) | Treated the same as a worker-detected failure → bounded retry via `retry_count`, then `SYSTEM_ERROR`. |
| **Multiple users submit simultaneously** | Each submission is an independent row/message; RabbitMQ fans out to N concurrent worker consumers (prefetch = concurrency), each bound to one Docker execution slot. No shared mutable state between concurrent judgments — see §21. |

## 18. Code Execution Architecture

**Never executes submitted code on the host process.** Every compile and run step happens inside an ephemeral, single-use Docker container built from a pinned, minimal, non-root base image per language (e.g., `eclipse-temurin:21-jdk-alpine` for Java, `python:3.12-alpine` for Python, `gcc:13-bookworm` slimmed for C++).

**Per-submission flow:**
1. Worker writes the submitted source to a temp directory on the host, owned by a dedicated low-privilege OS user, mode `0600`.
2. **Compile container** (only for compiled languages): mounts the source read-only and a scoped writable workspace directory for build output (a bind mount from the worker's temp directory — artifacts are therefore already on the host when the container exits, and the container rootfs stays read-only), `--network none`, `--memory=512m --cpus=1 --pids-limit=64 --cap-drop=ALL --security-opt no-new-privileges --read-only`, hard wall-clock timeout (e.g. 10s), `--rm`.
3. **Run container**, one fresh instance **per test case** (see tradeoff below): mounts the compiled artifact/source read-only, stdin piped from the test case's `input`, `--network none`, `--memory=<problem.memory_limit_kb>`, `--memory-swap` equal to `--memory` (no swap), `--cpus=1`, `--pids-limit=64`, non-root user, `--read-only` root filesystem with a small writable `tmpfs` for `/tmp` only, `--cap-drop=ALL`, `--security-opt no-new-privileges`, `--security-opt seccomp=<restrictive-profile>.json`, `--rm`, and an external wall-clock watchdog thread that issues `docker kill` if the container outlives `timeLimitMs * multiplier + buffer`.
4. Stdout is captured with a hard cap (e.g., 64KB) — output beyond the cap is truncated and the run is treated as a presentation/format failure against expected output (prevents an output-bomb from exhausting worker memory).
5. Container removed (`--rm`), temp directory wiped, regardless of outcome (`finally` block).

**Design tradeoff — one container per test case vs. one container reused across test cases**: reusing one container across test cases is faster (no repeated container startup cost) but risks state leakage between test runs (a submission that writes a marker file or forks a background process could influence the next test's timing). Per the stated priority order (correctness/security over performance), this PRD chooses **one fresh run container per test case**. Startup overhead (~50–150ms with Alpine images) is an accepted cost; a future performance enhancement (warm container pools) is noted in §36.

**Limitations of container-based isolation (must not be overstated):** Docker containers share the host kernel. A kernel-level vulnerability (a namespace or cgroup escape) can, in principle, allow adversarial code to break out of the container. Docker's default seccomp/capability drop reduces attack surface but is not a formal security boundary equivalent to a VM. Additional hardening this project documents but does not fully implement in MVP: running under **gVisor** or **Kata Containers/Firecracker microVMs** for a real syscall-level or hardware-virtualized boundary, running the Docker daemon **rootless**, and placing worker nodes on isolated hosts/VMs separate from the main application and database so that a successful container escape cannot directly reach production data. These are called out explicitly in §25 and §36 rather than glossed over.

## 19. Queue Architecture

**Choice: RabbitMQ over Kafka.** Justification: this system needs classic **work-queue** semantics — one job, consumed by exactly one worker, with per-message ack/nack, retry, and dead-lettering. RabbitMQ's model (direct exchange → queue → competing consumers, DLX for poison messages, per-message TTL for backoff) maps directly onto "distribute judging jobs to available workers." Kafka is built for high-throughput, replayable, ordered *event log* consumption by multiple independent consumer groups — valuable at large scale for things like activity streams or analytics pipelines, but it adds real operational weight (partition planning, consumer group rebalancing, log compaction, no native per-message ack/retry model) that this project's job-dispatch problem doesn't need. Choosing Kafka here would be exactly the kind of "distributed pattern for resume appeal" this PRD explicitly avoids.

**Topology:**
- Exchange `submission.exchange` (direct) → queue `submission.jobs` (durable), routing key `submit`.
- Consumers: execution-worker instances, concurrency = configurable thread pool (default 4 per instance), `prefetch = concurrency` so one slow container doesn't starve other workers of messages.
- **Manual ack mode.** A message is acked only after the DB write of the final (or compile-error) result commits successfully.
- **Retry/backoff**: on a transient failure, the message is published to `submission.retry` (a queue with a per-message TTL, e.g. 5s, and no consumers) whose dead-letter-exchange routes back to `submission.jobs`. `retry_count` in `execution_jobs` bounds this to `max_retries` (default 3).
- **Dead-letter queue** `submission.dlq`: after `max_retries` exceeded, or on an unretryable error, the message is routed here for manual/admin inspection; submission is marked `SYSTEM_ERROR`.

## 20. Test Case Evaluation Logic

- Comparison is **exact match** with whitespace normalization: trailing whitespace per line stripped, trailing blank lines ignored, all other differences (including internal spacing) treated as `WRONG_ANSWER`. This is the default/MVP comparator; a pluggable "special judge" interface is designed for but not implemented in MVP (see §36).
- **Sample test cases** (`is_sample=true`): always all run and all results returned to the user (helps debugging), regardless of pass/fail.
- **Hidden test cases**: run in `display_order`; execution **stops at the first non-`ACCEPTED` result** (matches real judge UX and saves compute) — the submission's verdict becomes that test case's verdict, and `failed_test_case_id` records which one (but never its content).
- If all hidden test cases pass: verdict `ACCEPTED`.
- `time_used_ms`/`memory_used_kb` on the submission record the **maximum** observed across all executed test cases (the binding constraint for pass/fail against the limits).

## 21. Concurrency Model

- **API layer**: standard Spring MVC thread-per-request over a bounded Tomcat thread pool; submission creation does minimal work (one INSERT + one event publish) so it stays fast under load.
- **Worker layer**: each worker instance runs a fixed-size pool of consumer threads (default 4), each bound to at most one Docker container execution at a time — this bounds host CPU/memory consumption per instance predictably (`workers × containers-per-worker × memory_limit` must fit the host).
- **No shared mutable state** between concurrent judgments: each submission's execution is fully isolated (own container, own temp dir, own DB rows). The only shared contention point is the `execution_jobs` lease row, guarded by a single conditional `UPDATE`, which Postgres serializes per-row without needing application-level locks.
- **Optimistic locking** (`submissions.version`) guards against a theoretical double-write race (e.g., a stale worker instance finishing after a reconciliation job already reset the row) — a version mismatch on the final update is caught, logged, and the write is discarded (the reconciling path is authoritative).

## 22. Failure Handling

(See the exhaustive table in §17 for the specific scenarios required by the brief; this section states the general policy.)

**Policy**: every failure is classified as either (a) a **judging outcome** (compile error, wrong answer, TLE, MLE, runtime error) — these are *correct, expected results* of running arbitrary code, not bugs, and are terminal, recorded, and never retried; or (b) an **infrastructure failure** (DB unreachable, Docker daemon error, worker crash) — these are retried with backoff up to `max_retries`, then surfaced as `SYSTEM_ERROR` for operator attention via the DLQ and admin queue-status endpoint. The system never guesses; when it cannot distinguish "the code is wrong" from "the infrastructure is broken," it treats it as an infrastructure failure and retries, since misclassifying an infra issue as a judging outcome would unfairly penalize a user's correct code.

## 23. Retry Strategy

- **Infra failures during DB write**: Resilience4j `@Retry` (3 attempts, exponential backoff 200ms→2s) around the transactional result-write method, before falling back to nack/DLQ.
- **Queue-level retry**: TTL-based retry queue as described in §19, bounded by `execution_jobs.retry_count` vs `max_retries` (default 3), independent of Resilience4j's in-process retries (defense in depth — one handles transient blips within a single attempt, the other handles the job needing an entirely fresh attempt/worker).
- **No retry** for judging outcomes (compile error, WA, TLE, MLE, RE) — retrying these would waste compute and cannot change a deterministic verdict (barring genuine flakiness, which is a known, accepted limitation of time-based judging in general, not something this system tries to solve).
- **Reconciliation job**: a scheduled task (every 30s) scans `execution_jobs` for `lease_expires_at < now()` on submissions not yet in a terminal state, and re-queues them (bounded by `max_retries`) — this is the safety net for a worker that died without RabbitMQ noticing (e.g., killed process that still holds the TCP connection briefly).

## 24. Caching Strategy

| What | Pattern | TTL | Invalidation |
|---|---|---|---|
| Problem detail (statement, samples, limits) | Cache-aside, read-through | 10 min | Explicit `DEL` on admin update/publish |
| Problem list pages | Cache-aside | 60s | Time-based only (acceptable staleness) |
| Problem/user statistics | Cache-aside, computed on demand if miss | 30s | Time-based; also refreshed on submission completion event |
| Idempotency keys (submission creation) | Redis `SET NX` with TTL | 24h | TTL only |
| Rate limiting (submission endpoint) | Redis token bucket / fixed window counter per user | rolling window | TTL only |
| JWT refresh-token blacklist | Redis `SET` with TTL = remaining token validity | matches token expiry | TTL only |
| Test case content | **Never cached client-side reachable**; worker-side cache only, keyed internally, never exposed via any API cache that a client request could read | 5 min (worker-local) | On admin test-case edit |

## 25. Security Model

- **Threat model**: the primary adversary is the *submitted source code itself* — assume it will try to escape, exhaust resources, read/write outside its sandbox, or reach the network. Secondary adversary: a malicious/compromised user account attempting to access another user's data or admin functions.
- **Defense in depth for code execution** (see §18): network none, read-only rootfs, capability drop, no-new-privileges, seccomp, non-root, resource cgroups, ephemeral containers, output size caps, wall-clock watchdog independent of in-container timing.
- **Realistic limitations, stated plainly**: Docker alone is a namespace/cgroup boundary on a shared kernel, not a hardened sandbox equivalent to a VM. This project's stated hardening ceiling for MVP is "safe against typical adversarial competitive-programming submissions" (fork bombs, infinite loops, memory hogs, attempts to read `/etc/passwd` or hit the network) — not "safe against a nation-state-grade kernel 0-day." Production-grade hardening beyond MVP: gVisor/Kata/Firecracker for a real isolation boundary, rootless Docker, dedicated low-trust worker hosts network-segmented from the database and API tier, and regular base-image patching.
- **Application security**: parameterized queries exclusively via Spring Data JPA/Hibernate (no string-concatenated SQL, ever); all external input validated via Bean Validation at the DTO boundary; authorization enforced at the service layer via `@PreAuthorize`, not only at the controller; secrets never hardcoded (see AGENTS.md §Secrets); BCrypt password hashing; JWT signed with a rotatable secret/key loaded from environment/secret manager, never committed.

## 26. Resource Limits

| Limit | Default | Configurable per |
|---|---|---|
| CPU time (compile) | 10s wall clock | Global |
| CPU time (run) | `problem.time_limit_ms × language.time_limit_multiplier` | Problem × Language |
| Memory | `problem.memory_limit_kb` (default 256MB) | Problem |
| Process/thread count (`pids-limit`) | 64 | Global |
| Stdout/stderr captured | 64KB | Global |
| Source code size | 65KB | Global |
| Submission rate | 1 per 3s per user (token bucket) | Global |

## 27. Logging and Monitoring

- **Structured JSON logs** (Logback + `logstash-logback-encoder`) with MDC fields: `correlationId`, `submissionId`, `userId`, `status`. The `correlationId` is generated at API request time and propagated into the RabbitMQ message headers so a submission's full journey — API → queue → worker → DB — is greppable by one ID across both services' logs.
- **Spring Boot Actuator**: `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` (Micrometer registry) exposed internally (not through the public API gateway/CORS-allowed surface).
- **Key metrics**: submission throughput (counter), queue depth (gauge, via RabbitMQ management API or Micrometer AMQP binder), judging latency (timer, from `SUBMITTED` to terminal status), worker container concurrent count (gauge), verdict distribution (counter by verdict label), DLQ size (gauge — alertable).
- **Future enhancement** (not MVP): OpenTelemetry distributed tracing spans across API→queue→worker for visual trace waterfalls (noted in §36).

## 28. Testing Strategy

| Layer | Tool | What it covers |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service-layer business logic (lifecycle transitions, idempotency key handling, verdict aggregation), pure domain logic, DTO mapping |
| Repository | JUnit 5 + Testcontainers (Postgres) | JPA queries, constraints, index-backed query correctness |
| Integration | JUnit 5 + Testcontainers (Postgres + RabbitMQ) + `MockMvc`/`WebTestClient` | Full submission-creation-to-queued flow, auth flow, admin CRUD |
| Sandbox / worker | JUnit 5 + Testcontainers (Docker-in-Docker) or gated `@Tag("docker")` tests | Actual container execution: compile error, TLE via a busy-loop fixture, MLE via an allocator fixture, network isolation (fixture attempts an outbound call and must fail) |
| Contract | springdoc-openapi generated spec + a schema-diff CI check | API doesn't silently break contracts |
| Frontend | Vitest + React Testing Library | Component behavior, status-polling hook |

Test fixtures include deliberately adversarial submissions (infinite loop, fork bomb attempt, large allocation, filesystem write attempt, network call attempt) as first-class integration test cases — this is not optional given the threat model.

## 29. CI/CD

**GitHub Actions**, two workflows:
- `ci.yml` (on PR + push to any branch): checkout → set up JDK 21 + Node → `mvn -B verify` (unit + Testcontainers-backed integration tests, services spun up via Testcontainers itself, no manual `services:` block needed for Postgres/RabbitMQ) → static analysis (Checkstyle + SpotBugs) → frontend `npm ci && npm run lint && npm run test && npm run build`.
- `cd.yml` (on merge to `main`, manual approval gate for now): build and tag Docker images for `backend`, `execution-worker`, `frontend` → push to GitHub Container Registry. Actual deployment (to a VM/cloud target) is out of MVP scope but the image-publish step is real and demonstrable.

## 30. Docker Architecture

`docker-compose.yml` (local dev/demo) brings up: `postgres`, `redis`, `rabbitmq` (with management plugin), `backend`, `execution-worker`, `frontend`. The `execution-worker` container is granted access to the host's Docker daemon via the Docker socket (`/var/run/docker.sock` bind mount) so it can launch sibling sandbox containers — it does **not** run Docker-in-Docker (nested daemons), which is slower and has its own privilege quirks; sibling containers via the mounted socket is the standard, lower-overhead pattern, with the accepted tradeoff that the worker process itself must be trusted (it is *our* code, not user code, that touches the socket). Language runtime images (`oj-java21`, `oj-python312`, `oj-cpp17`, etc.) are pre-built, minimal, pinned-version images built in CI and referenced by digest, never `latest`.

## 31. Frontend Requirements

React 18 + TypeScript, component-based, feature-folder structure (`pages/`, `components/`, `hooks/`, `api/`, `types/`). Key screens: problem list (filter/paginate), problem detail (statement + code editor, e.g. Monaco/CodeMirror + language selector + submit button), submission result view (status polling with backoff, verdict + failed test index), submission history table, admin problem editor, admin test case manager. State: React Query (or SWR) for server state/polling, no need for Redux given the app's shape. Auth: access token in memory (not `localStorage`, to reduce XSS token-theft surface), refresh token flow handled via a silent-refresh interceptor.

## 32. Admin Requirements

Admin UI (still React, role-gated route) for: problem CRUD with a markdown-preview statement editor, test case upload (manual entry + a bulk "paste input/output pairs" helper), publish/unpublish toggle (blocked if zero test cases), a queue-status view backed by `/admin/system/queue-status`, and a submissions browser with status/verdict filters for debugging user reports.

## 33. Performance Requirements

- API (non-judging) endpoints: p95 < 200ms under moderate load (cache warm).
- Submission creation: p95 < 100ms (it's just an INSERT + event publish, not the judging itself).
- Judging latency: dominated by container startup + compile + N × (container startup + run); target p95 < 5s for a typical Java/Python problem with ≤ 20 test cases at low queue depth. This is a target to validate empirically, not a guarantee — see §36 for actual measurement as a follow-up.
- Queue: sized so that under burst load, submissions queue (increasing latency) rather than being rejected — backpressure is via queue depth, not 5xx errors.

## 34. Scalability Considerations

- **Execution workers scale horizontally** independent of the API: add worker instances/pods, RabbitMQ fans out competing consumers automatically, no code change needed.
- **API scales horizontally** behind a load balancer since it's stateless (JWT, no server-side session) — the only shared state is Postgres/Redis.
- **Database** is the eventual bottleneck at scale; read-heavy problem browsing is cache-offloaded (§24); write-heavy submission inserts are cheap single-row inserts; `submission_results` grows unboundedly — a partitioning-by-date strategy is a noted future enhancement (§36), not needed at MVP scale.
- **Host capacity for execution**: each worker instance's container concurrency × per-container memory limit must be sized against the host's actual RAM/CPU — this is an explicit ops parameter, not auto-detected, to avoid oversubscription that would corrupt timing measurements (noisy-neighbor effects would make TLE verdicts unreliable).

## 35. MVP Scope

See §9 (P0 row) for the feature list. Concretely: auth, 2+ languages (recommend Java + Python first — deliberately including the platform's own implementation language exercises the sandbox honestly), problem browse/detail, submission creation → RabbitMQ → worker → Docker sandbox → verdict, submission history, a functional (not necessarily beautiful) React frontend, admin problem/test-case CRUD.

## 36. Future Enhancements

Custom checkers/special judges (partial scoring), plagiarism detection (e.g., MOSS-style token similarity), rating system for contests, gVisor/Kata/Firecracker sandbox hardening, OpenTelemetry tracing, warm container pools for lower judging latency, `submission_results` partitioning, k8s deployment + HPA on worker queue depth, WebSocket (instead of SSE/polling) for live status, GitHub OAuth login.

## 37. Development Milestones

See the phase breakdown at the end of this document (mirrors the requested build order — Repository setup → DB/migrations → Auth → Problem management → Submission creation → Queue → Execution worker → Docker sandbox → Verdict engine → Submission history → Frontend → Admin dashboard → Redis → Observability → Testing → CI/CD → Performance hardening). Each phase's objective/tasks/DoD is detailed in the roadmap section below.

## 38. Acceptance Criteria (System-Level)

- A user can register, log in, browse a published problem, submit a correct Java or Python solution, and see `ACCEPTED` within a few seconds, with a full audit trail in submission history.
- A user submitting an infinite loop receives `TIME_LIMIT_EXCEEDED`, not a hung request and not a hung worker (watchdog kills it).
- A user submitting code that tries to read `/etc/passwd`, fork-bomb, or open a network socket cannot succeed at any of those, and the submission still resolves to a terminal verdict (not stuck).
- Killing a worker process mid-execution results in the submission eventually reaching a terminal state (via reconciliation/retry), never stuck forever in `RUNNING`.
- Two rapid duplicate submission requests with the same `Idempotency-Key` result in exactly one `submissions` row.
- An admin can create a problem with hidden test cases that are never exposed via any public API response, even to the submission's own owner.
- CI fails the build if unit or integration tests fail; merge to `main` produces built, tagged Docker images.

---

## Development Roadmap (Phases)

Each phase lists: objective, tasks, dependencies, expected output, definition of done (DoD). Sequenced for an early working vertical slice, per the brief.

### Phase 1 — Repository Setup
- **Objective**: A buildable multi-module skeleton exists.
- **Tasks**: create `backend/` (Maven multi-module: `common`, `backend`, `execution-worker`), `frontend/` (Vite + React + TS), `infrastructure/` (`docker-compose.yml` stub), `docs/` (this PRD + AGENTS.md), root `.gitignore`, `.editorconfig`, license.
- **Dependencies**: none.
- **Expected output**: `mvn -pl common,backend,execution-worker -am install` succeeds; `npm install && npm run build` succeeds in `frontend/`.
- **DoD**: CI workflow file exists and runs (even if it only compiles at this stage).

### Phase 2 — Database & Migrations
- **Objective**: Schema from §13 is versioned and applied automatically.
- **Tasks**: add Flyway (preferred over Hibernate `ddl-auto` for a project claiming production-style rigor); write `V1__init_schema.sql` reflecting §13; wire Postgres in `docker-compose.yml`.
- **Dependencies**: Phase 1.
- **Expected output**: `backend` boots against a local/Testcontainers Postgres with migrations applied.
- **DoD**: a Testcontainers-based repository test proves at least one constraint (e.g., unique username) actually fires.

### Phase 3 — Authentication
- **Objective**: Register/login/refresh work end-to-end with JWT + roles.
- **Tasks**: Spring Security config, JWT filter, `UserDetailsService`, BCrypt, role seeding (`ROLE_USER`, `ROLE_ADMIN`), register/login/refresh/logout endpoints.
- **Dependencies**: Phase 2.
- **Expected output**: authenticated requests to a protected test endpoint succeed with a valid token, fail with 401 without one.
- **DoD**: integration test covers register → login → access protected resource → refresh → logout → blacklisted token rejected.

### Phase 4 — Problem Management
- **Objective**: Admin can create problems + test cases; public can browse published ones.
- **Tasks**: `problems`, `tags`, `test_cases` entities/repositories/services/controllers (admin + public), DTOs, validation.
- **Dependencies**: Phase 3 (needs `ROLE_ADMIN`).
- **Expected output**: full CRUD via Swagger UI.
- **DoD**: unpublished problems verified invisible to non-admins in an integration test.

### Phase 5 — Submission Creation
- **Objective**: `POST /submissions` persists correctly with idempotency, no queue yet.
- **Tasks**: `submissions`/`execution_jobs` entities, service transaction, idempotency-key check (Redis), rate limiter (Redis).
- **Dependencies**: Phase 4.
- **Expected output**: submission row created, status `SUBMITTED`.
- **DoD**: duplicate idempotency key test passes; rate-limit test passes.

### Phase 6 — Queue
- **Objective**: Submission creation publishes a job; a stub consumer proves delivery.
- **Tasks**: RabbitMQ config (exchange/queue/DLX/retry queue per §19), `@TransactionalEventListener(AFTER_COMMIT)` publisher, a temporary logging-only consumer.
- **Dependencies**: Phase 5.
- **Expected output**: submitting via API produces a visible message in the RabbitMQ management UI.
- **DoD**: integration test (Testcontainers RabbitMQ) asserts message is published only after commit, never on rollback.

### Phase 7 — Execution Worker (skeleton)
- **Objective**: Real consumer that claims the lease and transitions status, no Docker yet (fake/no-op judge).
- **Tasks**: `execution-worker` module consumer, lease acquisition query, status transition logic, manual ack.
- **Dependencies**: Phase 6.
- **Expected output**: submission moves `SUBMITTED → QUEUED → PICKED_UP → COMPLETED` (stub verdict) end-to-end.
- **DoD**: duplicate-delivery test proves only one worker "wins" the lease.

### Phase 8 — Docker Sandbox
- **Objective**: Real compile/run inside ephemeral containers for Java + Python.
- **Tasks**: build `oj-java21`/`oj-python312` images, implement `ContainerRunner` (compile + run) per §18's flags, wall-clock watchdog, output capture/truncation.
- **Dependencies**: Phase 7.
- **Expected output**: a real submission compiles and runs inside a container with resource limits enforced.
- **DoD**: adversarial fixtures (infinite loop → killed by watchdog, memory hog → OOM-killed, network attempt → fails) each produce the correct verdict in a Docker-gated integration test.

### Phase 9 — Verdict Engine
- **Objective**: Full comparison logic against real test cases, `submission_results` populated.
- **Tasks**: output normalization/comparison (§20), stop-on-first-failure for hidden tests, aggregation of time/memory, final transactional write + ack.
- **Dependencies**: Phase 8.
- **Expected output**: a correct solution gets `ACCEPTED`; an incorrect one gets `WRONG_ANSWER` with the right failed test index.
- **DoD**: end-to-end test submits both a correct and an incorrect reference solution against a seeded problem and asserts verdicts.

### Phase 10 — Submission History & Status Endpoints
- **Objective**: `GET /submissions`, `/submissions/{id}`, `/submissions/{id}/status`.
- **Dependencies**: Phase 9.
- **Expected output/DoD**: ownership checks enforced (test: user B cannot fetch user A's source code).

### Phase 11 — Frontend (MVP)
- **Objective**: Usable UI for the full solver journey.
- **Tasks**: problem list/detail pages, code editor + submit, polling result view, history table, auth screens.
- **Dependencies**: Phases 4, 5, 10 (needs stable API).
- **DoD**: a person can complete the full journey in §6.1 through the UI alone.

### Phase 12 — Admin Dashboard
- **Objective**: Usable UI for problem/test-case authoring.
- **Dependencies**: Phase 11's component library, Phase 4's API.
- **DoD**: an admin can author and publish a new problem entirely through the UI.

### Phase 13 — Redis (caching layer)
- **Objective**: Wire the caching strategy from §24 (idempotency/rate-limit already exist from Phase 5; this phase adds problem/list/stats caching).
- **DoD**: cache-hit test proves a second identical request skips the DB (via a Testcontainers Redis + a spy/counter on the repository).

### Phase 14 — Observability
- **Objective**: Structured logs with correlation IDs, Actuator + Micrometer metrics per §27.
- **DoD**: a single `correlationId` is greppable across backend and worker logs for one submission's full journey.

### Phase 15 — Testing Hardening
- **Objective**: Fill gaps to reach the coverage described in §28, including the adversarial fixture suite.
- **DoD**: CI test suite includes at least one test per failure mode listed in §17's table.

### Phase 16 — CI/CD
- **Objective**: Full `ci.yml`/`cd.yml` per §29.
- **DoD**: a PR triggers build+test+lint; a merge to `main` publishes tagged images.

### Phase 17 — Performance Hardening
- **Objective**: Measure real p95 judging latency under load (simple load-test script), tune worker concurrency/queue prefetch, document actual numbers (do not fabricate before measuring).
- **DoD**: a short load-test report with real measured numbers is added to `docs/`.
