# Online Judge

[![CI](https://github.com/AtharvaAgalawe/Online-Judge/actions/workflows/ci.yml/badge.svg)](https://github.com/AtharvaAgalawe/Online-Judge/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A production-style Online Judge: users submit source code, it is compiled and executed
against hidden test cases inside hardened, ephemeral Docker containers, and a verdict is
returned. The system is built around the three problems real judges actually face —
**correctness under concurrency and failure**, **safe execution of untrusted code**, and
**throughput** — rather than around CRUD.

| | |
|---|---|
| **Stack** | Java 21 · Spring Boot 3 · PostgreSQL · RabbitMQ · Redis · React 18 + TypeScript |
| **Core** | Async judging pipeline: API → queue → execution worker → Docker sandbox → verdict |
| **Languages** | Java 21, Python 3.12, C 17, C++ 17, JavaScript (Node 22), Go 1.23 |
| **Spec** | [`PRD.md`](PRD.md) (what & why) · [`AGENTS.md`](AGENTS.md) (how it's built) |

## Contents

- [Features](#features)
- [Architecture](#architecture)
- [Technology stack](#technology-stack)
- [Repository layout](#repository-layout)
- [Getting started](#getting-started)
- [Using the app](#using-the-app)
- [API](#api)
- [Configuration](#configuration)
- [Testing and verification](#testing-and-verification)
- [Security model](#security-model)
- [CI](#ci)
- [Project status](#project-status)
- [Documentation and decisions](#documentation-and-decisions)
- [License](#license)

## Features

**Accounts and access control**
- JWT authentication (short-lived HS256 access tokens; opaque refresh tokens stored only as SHA-256 hashes)
- BCrypt password hashing (cost 12), role-based authorization (`ROLE_USER`, `ROLE_ADMIN`) enforced at the service layer
- RFC 7807 (`application/problem+json`) error responses everywhere

**Problem catalog**
- Public browse with difficulty/tag/search filters, pagination, and per-problem acceptance rates
- Problem detail exposes statement, limits, and sample tests only — hidden test content is never returned by any endpoint
- Admin authoring: problem CRUD, test-case management, publish gating (a problem cannot be published without test cases), soft-unpublish, and protection of judged history

**Submission pipeline**
- `POST /submissions` returns `202` immediately with a submission id (judging is asynchronous)
- Idempotency keys: a retried request with the same `Idempotency-Key` returns the original submission, never a duplicate row
- Per-user submission rate limiting with `Retry-After`
- Submission row and execution job are written in one transaction; the job message is published **only after commit**

**Queueing and execution**
- RabbitMQ work queue with a TTL-based retry queue and a dead-letter queue for poison messages
- Lease-based job claiming via a single conditional `UPDATE` — duplicate deliveries are no-ops, retries are bounded, crashed attempts are safely re-entered
- Manual ack: a message is acknowledged only after the database result has committed
- Sandboxed execution: one ephemeral container per compile/run with `--network none`, read-only rootfs, dropped capabilities, `no-new-privileges`, memory/CPU/pids limits, a non-root user, and an external wall-clock watchdog independent of the in-container process
- Captured output is hard-capped, so an output bomb cannot exhaust the worker

**Contests**
- Admin: create/edit/delete before start, attach existing unpublished problems or author new ones inline, private contests, configurable standings freeze
- Solver: register/unregister before start, contest problems and submissions while running, standings with points/penalty (first AC earns the points; each prior rejected attempt adds 10 penalty minutes; ties use competition ranking)
- Contest problems stay out of the public catalog until the contest ends; a scheduled job auto-publishes them (within ~60s of `end_time`) once they have test cases
- Private contests and unrevealed problems are indistinguishable from missing ones (404) for non-admins

**Supported languages** (judge images pinned by digest)

| Language | Version | Runtime image | Mode |
|---|---|---|---|
| Java | 21 | `oj-java21` | compiled (`javac`) |
| Python | 3.12 | `oj-python312` | interpreted |
| C | 17 (GCC 13) | `oj-gcc13` | compiled |
| C++ | 17 (G++ 13) | `oj-gcc13` | compiled |
| JavaScript | Node 22 | `oj-node22` | interpreted |
| Go | 1.23 | `oj-go123` | compiled (`go build`) |

## Architecture

A modular monolith (`backend`) plus a separately deployable, independently scalable
execution worker (`execution-worker`). The only asynchronous boundary is the queue.

```
React frontend ──HTTP──▶ backend (Spring Boot) ──job message──▶ RabbitMQ ──▶ execution-worker
                                   │                                            │
                                   ▼                                            ▼
                             PostgreSQL ◀──────────── shared schema ─────────────┘
                                   ▲
                                 Redis (idempotency / rate limiting / standings cache)
```

The `execution-worker` is the only component with Docker socket access; the API process
never touches Docker. Submitted code runs exclusively inside sandbox containers.

**Submission lifecycle**

```
SUBMITTED → QUEUED → PICKED_UP → COMPILING → RUNNING → EVALUATING → COMPLETED
                                       └────────────▶ terminal failures (CE / TLE / MLE / RE / SYSTEM_ERROR)
```

## Technology stack

| Layer | Choices |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Web, Spring Security, Spring Data JPA/Hibernate, Flyway, springdoc-openapi |
| Worker | Java 21, Spring Boot 3, docker-java (Docker socket), RabbitMQ, Resilience4j |
| Data | PostgreSQL 17, Redis 7, RabbitMQ 4 (management plugin) |
| Frontend | React 18, TypeScript, Vite, React Router, TanStack Query, CodeMirror 6 |
| Judge images | eclipse-temurin 21, python 3.12, GCC 13, Node 22, Go 1.23 (all digest-pinned) |
| Build & CI | Maven multi-module (wrapper bundled), npm, Docker Compose, GitHub Actions |

## Repository layout

```
backend/            Spring Boot web app — api / service / domain / repository / security / messaging
execution-worker/   Judging service — consumer / execution (Docker sandbox) / judge
common/             Shared entities, enums, DTOs, and the job-message contract (data shapes only)
frontend/           React 18 + TypeScript (Vite) — pages / components / api / auth, plus verification scripts
infrastructure/     docker-compose.yml + digest-pinned judge runtime images (docker/images/)
config/             Checkstyle and SpotBugs configuration
docs/decisions/     Architectural decision records
.github/workflows/  CI pipeline (cd.yml is a roadmap item, Phase 16)
PRD.md              Product specification — read before implementing (see AGENTS.md)
AGENTS.md           Engineering constitution governing every change
```

## Getting started

### Prerequisites

- JDK 21+ (the build targets Java 21; the Maven wrapper is bundled — no Maven install needed)
- Node 18+ (CI uses Node 22)
- Docker (for the local infrastructure stack, the judge sandbox, and the Docker-backed test suite)

### 1. Configure the environment

Copy the template and fill in local passwords (the file is git-ignored — never commit it):

```
cp .env.example infrastructure/.env
```

At minimum set `DB_PASSWORD`, `RABBITMQ_PASSWORD`, and `JWT_SECRET` (a base64 value of at
least 32 decoded bytes, e.g. from `openssl rand -base64 48`). See
[Configuration](#configuration) for the full list.

The same values must be visible to the backend and worker processes when you run them
(set them in your shell or IDE run configuration). With bash:

```
set -a; . infrastructure/.env; set +a
```

On PowerShell set them individually, e.g. `$env:DB_URL = 'jdbc:postgresql://localhost:5432/onlinejudge'`.

### 2. Start the infrastructure

```
docker compose -f infrastructure/docker-compose.yml up -d
```

This brings up PostgreSQL (`:5432`), Redis (`:6379`), and RabbitMQ (`:5672`, management UI
at `:15672`).

### 3. Build the judge runtime images

The judge runs submissions in these digest-pinned images (referenced by the seeded
language rows in the `languages` table):

```
docker build -t oj-java21 infrastructure/docker/images/oj-java21
docker build -t oj-python312 infrastructure/docker/images/oj-python312
docker build -t oj-gcc13 infrastructure/docker/images/oj-gcc13
docker build -t oj-node22 infrastructure/docker/images/oj-node22
docker build -t oj-go123 infrastructure/docker/images/oj-go123
```

### 4. Run the backend and worker

With the environment variables from step 1 exported:

```
./mvnw -B package -DskipTests
java -jar backend/target/backend-0.1.0-SNAPSHOT.jar
java -jar execution-worker/target/execution-worker-0.1.0-SNAPSHOT.jar
```

The API listens on `:8080` (Swagger UI at `/swagger-ui.html`), the worker on `:8081`, and
Flyway applies the schema on backend startup.

### 5. Run the frontend

```
cd frontend
npm install
npm run dev        # Vite dev server on :5173; proxies /api to http://localhost:8080
```

Other frontend scripts:

```
npm run lint       # ESLint
npm run test       # Vitest (jsdom)
npm run build      # tsc + Vite production build
```

The frontend uses only self-hosted fonts (`frontend/scripts/download-fonts.mjs`).

## Using the app

**Public** — `/` landing page, `/problems` catalog with filters, `/problems/{slug}`
statement + sample tests + editor + submit, `/contests` list, `/contests/{id}` detail
(registration, problems, standings), `/login`, `/register`.

**Authenticated** — `/submissions` history, `/submissions/{id}` result view with live
polling down to a terminal verdict, `/contests/{id}/problems/{slug}` to solve inside a
contest.

**Admin** (all routes are `RequireAuth` + `RequireAdmin`; the client guard is UX only —
the backend enforces `ROLE_ADMIN` at the service layer):

| Route | Purpose |
|---|---|
| `/admin/problems` | problem list including unpublished, search + publish filter |
| `/admin/problems/new` | create a problem |
| `/admin/problems/:id` | edit problem, manage sample/hidden test cases, publish/unpublish |
| `/admin/contests` | contest list with metadata and publish state |
| `/admin/contests/new` | create a contest |
| `/admin/contests/:id` | edit metadata, attach or inline-create problems |
| `/admin/submissions` | submissions browser with problem/verdict filters |
| `/admin/queue` | queue-health snapshot (database-derived lease state) |

### Granting admin access

**Dev-only bootstrap (no SQL).** On the `local` profile the backend can create one admin
on startup from environment variables. It is inactive by default, never runs outside
`local`, creates nothing without a password (no weak default), and never modifies an
existing user:

```
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_PASSWORD=<pick-a-password>
# optional: BOOTSTRAP_ADMIN_EMAIL (defaults to <username>@localhost)
```

**Manual promotion.** Register a user, then promote it in Postgres:

```
docker exec <postgres-container> psql -U onlinejudge -d onlinejudge -c \
  "INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r WHERE u.username = '<name>' AND r.name = 'ROLE_ADMIN' ON CONFLICT DO NOTHING;"
```

There is deliberately no UI to self-assign roles.

## API

REST, versioned under `/api/v1`. OpenAPI is generated from controller annotations
(springdoc) — see `/swagger-ui.html` on a running backend; the spec is never
hand-maintained. Errors are always RFC 7807 `application/problem+json`.

| Group | Representative endpoints |
|---|---|
| Auth | `POST /auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Problems | `GET /problems`, `GET /problems/{slug}`, `GET /problems/{slug}/stats` |
| Languages | `GET /languages` |
| Submissions | `POST /submissions` (+ `Idempotency-Key`), `GET /submissions`, `GET /submissions/{id}`, `GET /submissions/{id}/status` |
| Contests | `GET /contests`, `GET /contests/{id}`, `GET /contests/{id}/problems/{slug}`, `GET /contests/{id}/standings`, `POST/DELETE /contests/{id}/register` |
| Admin | `/admin/problems*`, `/admin/contests*`, `/admin/submissions`, `/admin/system/queue-status` |

`/admin/system/queue-status` is **database-derived** (lease state from `execution_jobs`),
not RabbitMQ broker depth.

## Configuration

All credentials come from environment variables (see `.env.example`); nothing secret is
committed or logged.

| Variable | Purpose | Default |
|---|---|---|
| `DB_URL` | JDBC URL | `jdbc:postgresql://localhost:5432/onlinejudge` |
| `DB_USERNAME` / `DB_PASSWORD` | Database credentials | username `onlinejudge`, password required |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | Broker location | `localhost` / `5672` |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | Broker credentials | username `onlinejudge`, password required |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis location | `localhost` / `6379` / empty |
| `JWT_SECRET` | HS256 signing key (base64, ≥32 bytes decoded) | required |
| `CORS_ALLOWED_ORIGINS` | Backend CORS allow-list | `http://localhost:5173` |
| `BOOTSTRAP_ADMIN_*` | Dev-only admin bootstrap (local profile only) | disabled |

Worker execution limits (overridable in `application.yml`, PRD §26): compile wall-clock
60s; run wall-clock = problem time limit × language multiplier + 2s watchdog buffer;
compile memory 512MB; pids limit 64; captured output 64KB; source code 64KB; submission
rate 1 per 3s per user.

## Testing and verification

```
# Fast suite: unit tests + static analysis (Checkstyle, SpotBugs)
./mvnw -B verify

# Full suite: adds Docker-backed integration tests (Testcontainers + sandbox fixtures)
./mvnw -B verify -P docker-tests

# Frontend
cd frontend && npm install && npm run lint && npm run test && npm run build
```

The Docker-backed suite is not optional garnish: it includes deliberately adversarial
fixtures — infinite loops (watchdog TLE), memory hogs (cgroup OOM), network attempts,
filesystem escapes, thread explosions, and output bombs — plus the full judge flow
against real Postgres/RabbitMQ/Redis and the exact Dockerfiles shipped in
`infrastructure/docker/images/`.

**Live end-to-end verification** (requires infra + backend + worker and at least one
published problem with a test case):

```
node frontend/scripts/verify-phase11-live.mjs
```

Walks the exact API sequence the UI performs — reachable, register, login, refresh, list
problems, detail, stats, languages, submit, poll to a terminal status, history — printing
`PASS`/`FAIL` per step and exiting non-zero on the first failure. `BASE_URL` defaults to
`http://localhost:8080`.

**Admin journey verification** (requires infra + backend only — no worker or language
images needed):

```
BASE_URL=http://localhost:8080 ADMIN_USER=<admin-username> node frontend/scripts/verify-phase12-live.mjs
```

(`ADMIN_USER` must already hold `ROLE_ADMIN` — see [Granting admin access](#granting-admin-access).)
Commands above use bash syntax; on PowerShell set env vars with `$env:NAME='value'` first.

## Security model

- **Untrusted code never runs outside the sandbox.** The backend has zero Docker
  dependencies; only the worker touches the Docker socket and launches sibling containers.
- Every sandbox container is launched with `--network none`, read-only rootfs (writable
  `/tmp` tmpfs only), `--cap-drop=ALL`, `no-new-privileges`, memory and CPU limits, a pids
  limit, a non-root user, and **two independent time bounds**: an in-container process
  limit and an external wall-clock watchdog that force-kills the container.
- **Honest limitation (PRD §25):** Docker shares the host kernel — this is a hardened
  namespace/cgroup boundary, not a VM. gVisor/Kata/rootless-Docker hardening is documented
  as future work rather than overclaimed.
- Secrets come from the environment, are never hardcoded or logged; CI fails if any env
  file or credential material is tracked in git.

## CI

`.github/workflows/ci.yml` runs three jobs on every push and pull request:

1. **Repository hygiene** — fails if `.env` files or credential material are tracked.
2. **Backend + worker** — `./mvnw -B verify -P docker-tests` (compilation, all tests,
   static analysis, Docker-backed integration tests).
3. **Frontend** — `npm ci && npm run lint && npm run test && npm run build`.

`cd.yml` (image publishing/deployment) is tracked as Phase 16 in the roadmap and is not
implemented yet.

## Project status

Under active development, in verifiable increments:

- [x] Phase 1 — Repository skeleton, build, static analysis, CI
- [x] Phase 2 — Database schema and Flyway migrations
- [x] Phase 3 — Authentication and authorization
- [x] Phase 4 — Problem management (admin + public)
- [x] Phase 5 — Submission creation with idempotency and rate limiting
- [x] Phase 6 — Queue topology and after-commit publishing
- [x] Phase 7 — Execution worker: leases, manual ack, duplicate-delivery safety
- [x] Phase 8 — Docker sandbox with adversarial verification
- [x] Phase 9 — Verdict engine (output comparison, aggregation, retryable final write)
- [x] Phase 10 — Submission history and status endpoints with ownership enforcement
- [x] Phase 11 — Frontend (MVP) with live verification
- [x] Phase 12 — Admin dashboard (authoring UI, submissions browser, queue status)
- [x] Contests — scheduling, registration, contest solving, standings, auto-publish
- [x] Languages — Java, Python, C, C++, JavaScript, Go
- [ ] Phase 13–17 — Caching, observability, testing hardening, CI/CD, performance

## Documentation and decisions

- [`PRD.md`](PRD.md) — product specification: features, schemas, API, failure handling,
  resource limits, roadmap with per-phase definition of done.
- [`AGENTS.md`](AGENTS.md) — engineering constitution: architecture rules, coding
  standards, testing requirements, security rules, definition of done.
- [`docs/decisions/`](docs/decisions/) — recorded architectural decisions and their
  tradeoffs.

## License

MIT — see [LICENSE](LICENSE).
