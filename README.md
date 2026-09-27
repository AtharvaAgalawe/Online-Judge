# Online Judge

[![CI](https://github.com/AtharvaAgalawe/Online-Judge/actions/workflows/ci.yml/badge.svg)](https://github.com/AtharvaAgalawe/Online-Judge/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

A production-style Online Judge platform: users submit source code, it is compiled and
executed against hidden test cases inside hardened, ephemeral Docker containers, and a
verdict is returned. The system is built around the three problems real judges actually
face — **correctness under concurrency and failure**, **safe execution of untrusted
code**, and **throughput** — rather than around CRUD.

## Features

**Accounts and access control**
- JWT authentication (short-lived HS256 access tokens, opaque refresh tokens stored only as SHA-256 hashes)
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

**Supported languages:** Java 21 and Python 3.12 (runtime images pinned by digest).

## Architecture

A modular monolith (`backend`) plus a separately deployable, independently scalable
execution worker (`execution-worker`). The only asynchronous boundary is the queue.

```
React frontend ──HTTP──▶ backend (Spring Boot) ──job message──▶ RabbitMQ ──▶ execution-worker
                                   │                                            │
                                   ▼                                            ▼
                             PostgreSQL ◀──────────── shared schema ─────────────┘
                                   ▲
                                 Redis (idempotency / rate limiting / cache)
```

The `execution-worker` is the only component with Docker socket access; the API process
never touches Docker. Submitted code runs exclusively inside sandbox containers.

**Submission lifecycle**

```
SUBMITTED → QUEUED → PICKED_UP → COMPILING → RUNNING → EVALUATING → COMPLETED
                                       └────────────▶ terminal failures (CE / TLE / MLE / RE / SYSTEM_ERROR)
```

## Repository layout

```
backend/            Spring Boot web app — api / service / domain / repository / security / messaging
execution-worker/   Judging service — consumer / execution (sandbox) / judge
common/             Shared entities, enums, and job-message contract (data shapes only)
frontend/           React 18 + TypeScript (Vite)
infrastructure/     docker-compose.yml, digest-pinned language runtime images
config/             Checkstyle / SpotBugs configuration
docs/decisions/     Recorded architectural decision rationales
.github/workflows/  CI/CD pipelines
```

`PRD.md` is the product specification (what and why); `AGENTS.md` is the engineering
constitution (how) that governs every change.

## Getting started

### Prerequisites

- JDK 21+ (the build targets Java 21; the Maven wrapper is bundled — no Maven install needed)
- Node 18+ (frontend)
- Docker (for the local infrastructure stack, the sandbox, and the Docker-backed test suite)

### 1. Start the infrastructure

Create `infrastructure/.env` (git-ignored — never commit it) with:

```
DB_URL=jdbc:postgresql://localhost:5432/onlinejudge
DB_USERNAME=onlinejudge
DB_PASSWORD=<choose>
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=onlinejudge
RABBITMQ_PASSWORD=<choose>
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
JWT_SECRET=<base64, at least 32 bytes decoded — e.g. `openssl rand -base64 48`>
```

Then start PostgreSQL, Redis, and RabbitMQ:

```
docker compose -f infrastructure/docker-compose.yml up -d
```

### 2. Build the language runtime images

The judge runs submissions in these images (referenced by the seeded language rows):

```
docker build -t oj-java21 infrastructure/docker/images/oj-java21
docker build -t oj-python312 infrastructure/docker/images/oj-python312
```

### 3. Run the services

The `local` Spring profile reads all credentials from environment variables (see
`AGENTS.md` §24); set them in your shell or IDE run configuration.

```
./mvnw -B package -DskipTests
java -jar backend/target/backend-0.1.0-SNAPSHOT.jar
java -jar execution-worker/target/execution-worker-0.1.0-SNAPSHOT.jar
```

The API listens on `:8080`, Swagger UI is at `/swagger-ui.html`, and the worker consumes
from RabbitMQ. Optionally run the frontend with `cd frontend && npm install && npm run dev`.

## Build and test

```
# Fast suite: unit tests + static analysis (Checkstyle, SpotBugs)
./mvnw -B verify

# Full suite: adds the Docker-backed integration tests (Testcontainers, sandbox fixtures)
./mvnw -B verify -P docker-tests

# Frontend
cd frontend && npm install && npm run lint && npm run test && npm run build
```

The Docker-backed suite includes deliberately adversarial fixtures — infinite loops
(watchdog TLE), memory hogs (cgroup OOM), network attempts, filesystem escapes, thread
explosions, and output bombs — and must pass in CI, not just locally.

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
- [ ] Phase 11–12 — Frontend and admin UI
- [ ] Phase 13–17 — Caching, observability, testing hardening, CI/CD, performance

## License

MIT — see [LICENSE](LICENSE).
