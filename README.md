# Online Judge

A production-style Online Judge: users submit code, it is compiled and run against hidden
test cases inside isolated Docker containers, and a verdict is returned.

- `PRD.md` — product requirements (what and why)
- `AGENTS.md` — engineering constitution (how); binding for humans and AI agents
- `docs/decisions/` — recorded decision rationales

## Architecture

Modular monolith web application (`backend`) + separately deployable execution worker
(`execution-worker`) + shared data-shape module (`common`):

```
React frontend ──HTTP──▶ backend (Spring Boot) ──job message──▶ RabbitMQ ──▶ execution-worker
                                   │                                            │
                                   ▼                                            ▼
                             PostgreSQL ◀──────────── shared schema ─────────────┘
                                   ▲
                                 Redis (cache / idempotency / rate limit)
```

The execution-worker is the only component with Docker socket access; submitted code runs
exclusively inside locked-down, ephemeral sandbox containers.

## Repository layout

```
backend/            Spring Boot web app (api/service/domain/repository/security/messaging/config)
execution-worker/  Judging service (consumer/execution/judge)
common/            Shared entities/enums/DTOs (data shapes only)
frontend/          React 18 + TypeScript (Vite)
infrastructure/    docker-compose.yml, language runtime images
config/            Checkstyle / SpotBugs configuration
docs/              PRD, decisions
.github/           CI/CD workflows
```

## Build & test

Requires JDK 21+ (build targets Java 21) and Node 18+.

```
# Java (multi-module: unit tests + static analysis)
./mvnw -B verify

# Java including Docker-backed tests (Testcontainers/sandbox) — needs a Docker host
./mvnw -B verify -P docker-tests

# Frontend
cd frontend && npm install && npm run lint && npm run test && npm run build
```

CI (`.github/workflows/ci.yml`) runs both on every push and pull request.

## Local development

```
cp .env.example infrastructure/.env   # fill in secrets — never commit .env
docker compose -f infrastructure/docker-compose.yml up -d
```

Then run `backend` and `execution-worker` with the `local` Spring profile, which reads
secrets from environment variables only (AGENTS.md §24).
