# AGENTS.md — Engineering Constitution for AI Coding Agents

This file governs how any AI coding agent (or human, for that matter) works on this repository. It is not aspirational — it is enforced by convention and by CI. If something here conflicts with a shortcut that would "just make the task pass," this file wins.

**Read `PRD.md` before implementing any feature.** This file tells you *how* to build; the PRD tells you *what* and *why*. If the two ever seem to conflict, stop and reconcile explicitly rather than picking one silently.

---

## 1. Project Purpose

A production-style Online Judge: users submit code, it is compiled and run against hidden test cases inside isolated Docker containers, and a verdict is returned. The system's hardest problems are correctness under concurrency/failure, and safe execution of untrusted code — not CRUD. Every decision should be evaluated against those two concerns first.

## 2. Architecture Rules

- **Modular monolith** for the web application (`backend`) + a **separately deployable execution-worker** (`execution-worker`). Do not split further into microservices "for scalability" — that is not justified at this project's scope and would violate the stated priority order (§25).
- Layering inside `backend` is strict: **api → service → domain/repository**. Controllers call services. Services call repositories and other services. Controllers never call repositories directly. Domain entities never depend on Spring MVC, AMQP, or web types.
- The async boundary is exactly the RabbitMQ queue (see PRD §12). Anything before publish is synchronous/transactional; anything after consume is async/retryable. Do not blur this — e.g., never make the API wait synchronously on a worker result beyond the initial insert+publish.
- The `execution-worker` is the **only** component with access to the Docker socket. The `backend` module must never depend on Docker client libraries or touch `/var/run/docker.sock`.
- Shared code (entities/enums/DTOs used by both `backend` and `execution-worker`) lives in the `common` Maven module. Do not duplicate entity definitions across modules — that has caused real schema-drift bugs in similar systems. Do not, however, put business logic in `common`; it holds data shapes, not behavior.
- Do not introduce a "domain model separate from JPA entities" (a full hexagonal port/adapter layer). At this project's scope, JPA entities annotated with invariant-enforcing methods **are** the domain model. This is a deliberate, documented tradeoff (simplicity over textbook hexagonal purity) — do not silently "improve" it into a bigger pattern without discussing it in a PR description first (see §21).

## 3. Repository Structure

```
online-judge/
├── backend/                     # Spring Boot web app (api/service/persistence)
│   ├── src/main/java/com/onlinejudge/backend/
│   │   ├── api/                 # controllers, request/response DTOs, exception handlers
│   │   ├── service/              # business logic, interfaces + impl
│   │   ├── domain/                # JPA entities, enums, invariants
│   │   ├── repository/            # Spring Data JPA repositories
│   │   ├── security/               # JWT, filters, UserDetailsService, config
│   │   ├── messaging/               # RabbitMQ producers, job message DTOs, config
│   │   └── config/                   # OpenAPI, CORS, Redis, general beans
│   └── src/test/java/...
├── execution-worker/             # Independently deployable judging service
│   └── src/main/java/com/onlinejudge/worker/
│       ├── consumer/              # RabbitMQ listeners
│       ├── execution/               # Docker sandbox orchestration
│       ├── judge/                    # verdict computation, output comparison
│       └── config/
├── common/                        # Shared entities/enums/DTOs (Maven module, no logic)
├── frontend/                        # React + TypeScript
│   └── src/{pages,components,hooks,api,types}/
├── infrastructure/
│   ├── docker-compose.yml
│   └── docker/images/               # Language runtime Dockerfiles (oj-java21, oj-python312, ...)
├── docs/
│   ├── PRD.md
│   ├── AGENTS.md
│   └── api/                          # generated OpenAPI spec
└── .github/workflows/{ci.yml,cd.yml}
```

New top-level directories require justification in the PR description (see §21). Do not invent a new module without checking whether an existing package boundary already fits.

## 4. Technology Rules

Fixed for this project (do not swap without updating both PRD.md and this file, and stating the tradeoff — see §22):

Java 21 · Spring Boot 3.x · Spring Web · Spring Security · Spring Data JPA/Hibernate · Maven (multi-module) · PostgreSQL · Flyway (schema migrations — not `ddl-auto` in any environment beyond a developer's throwaway local run) · Redis · **RabbitMQ** (not Kafka — see PRD §19 for the justification; do not re-litigate this without a documented, concrete reason tied to an actual requirement change) · Docker (sibling containers via mounted socket from `execution-worker`, not Docker-in-Docker) · React 18 + TypeScript (Vite) · Docker Compose · GitHub Actions · JUnit 5 + Mockito + Testcontainers · springdoc-openapi.

## 5. Coding Standards (General)

- **Naming**: classes `PascalCase`, methods/fields `camelCase`, constants `UPPER_SNAKE_CASE`, packages all-lowercase, no underscores. Test classes: `<ClassUnderTest>Test` (unit), `<Feature>IntegrationTest` (integration).
- **Method size**: prefer methods under ~30 lines and doing one thing. A method that needs a comment saying "first we do X, then Y, then Z" is a signal to extract X/Y/Z into named methods.
- **Class organization**: one public top-level class per file; fields → constructors → public methods → private helpers. Constructor injection only (no field injection via `@Autowired` on fields) — it makes dependencies explicit and testable without reflection tricks.
- **Null handling**: avoid returning `null` from service methods — use `Optional<T>` for "may not exist" and throw a specific exception for "must exist but doesn't" (e.g., `ResourceNotFoundException`). Never use `null` to mean "not yet computed" in a DTO returned over the API — omit the field or use an explicit sentinel documented in the DTO's Javadoc.
- **Exception handling**: never swallow an exception silently (`catch (Exception e) {}`). Catch specific exception types where possible. Business-relevant failures use custom unchecked exceptions (`SubmissionNotFoundException`, `UnauthorizedAccessException`, etc.) mapped centrally by a `@ControllerAdvice` to RFC 7807 responses (see §9). Infra-transient failures (used with Resilience4j retry) are distinguished from unretryable ones by exception type, not by string-matching a message.
- **Immutability**: DTOs and job messages are immutable (Java `record`s) wherever the shape allows it. JPA entities are mutable by necessity (Hibernate) but should not expose setters for fields that must never change post-creation (e.g., `submission.submittedAt`, `submission.userId`).

## 6. Java Conventions

- Target Java 21 language features where they improve clarity: `record`s for DTOs/job messages, pattern matching for `instanceof`/`switch` on the verdict/status enums, text blocks for embedded SQL/JSON test fixtures. Do not reach for exotic features (virtual threads, sealed interfaces) unless they solve a concrete problem stated in the PRD — see §18 (performance rules) before adding virtual threads for "throughput."
- Enums (`SubmissionStatus`, `Verdict`, `Role`) are the source of truth for valid values; do not represent these as bare strings anywhere except at the DB column boundary (stored as `VARCHAR`, converted via a JPA `AttributeConverter` or `@Enumerated(STRING)` — never `ORDINAL`, which breaks silently on reordering).
- `SubmissionStatus` transitions are validated in one place (a method on the entity or a dedicated `SubmissionLifecycle` class), e.g. `submission.transitionTo(RUNNING)` throws `IllegalStateTransitionException` if the current status doesn't permit it. Do not scatter "is this transition legal" checks across services.

## 7. Spring Boot Conventions

- **Controllers are thin**: bind request → call one service method → map result to response DTO. No `if` business logic in controllers beyond request shape validation (which Bean Validation annotations already handle declaratively).
- **`@Transactional` boundaries live on service methods**, not repositories, not controllers. A service method that spans multiple repository calls that must succeed/fail together is the transaction boundary. Never mark a controller method `@Transactional`.
- **Configuration** via `application.yml` with Spring profiles (`local`, `test`, `docker`, `prod`); no environment-specific logic branching in Java code — branch via configuration/profile-scoped beans instead.
- **DTOs at every API boundary** — never return a JPA entity directly from a controller (leaks lazy-loading proxies, internal fields, and couples the wire format to the schema). Mapping via MapStruct or explicit hand-written mappers (hand-written preferred while the schema is still small — avoid the MapStruct build-time codegen dependency until the mapping surface actually justifies it).
- Use constructor-based `@ConfigurationProperties` classes for grouped settings (e.g., execution limits, JWT settings) instead of scattering `@Value("${...}")` across classes.

## 8. Database Conventions

- **All schema changes go through Flyway migrations** (`V{n}__description.sql`), never through `ddl-auto: update`/`create` in any environment that isn't a developer's local throwaway DB. See §23.
- Every table has an explicit primary key, explicit FKs with `ON DELETE` behavior chosen deliberately (documented in the migration's comment if not obviously `CASCADE`/`RESTRICT`), and indexes matching the query patterns in PRD §13 — do not add an index "just in case" without a query that needs it (unindexed writes are cheaper; justify every index).
- Repositories are Spring Data JPA interfaces; custom queries use `@Query` with JPQL for anything beyond simple derived queries, and native `@Query` only when JPQL genuinely cannot express it (document why in a comment).
- Any query touching more than one table in a hot path (submission creation, lease acquisition) must be reviewed for the transaction boundary it needs — see PRD §13's "Transaction boundaries" subsection, which is authoritative.

## 9. API Conventions

- REST, versioned under `/api/v1`. Resource-oriented nouns, plural (`/problems`, `/submissions`), sub-resources nested (`/problems/{id}/test-cases`).
- Errors are always RFC 7807 `application/problem+json` via a single `@RestControllerAdvice`. Do not hand-roll ad hoc error JSON shapes in individual controllers.
- Every endpoint has an explicit `@PreAuthorize` (or is deliberately public and documented as such) — never rely on "it's not exposed in the frontend" as an access control mechanism.
- Request DTOs use Bean Validation annotations (`@NotBlank`, `@Size`, `@Email`, etc.); do not hand-validate in the controller body what an annotation already expresses.
- OpenAPI/Swagger is generated from annotated controllers (springdoc), not hand-maintained separately — if the code and the spec could drift, the code wins and the spec must be regenerated, never edited by hand.

## 10. Security Rules

- Apply authorization **at the service layer**, not only via controller `@PreAuthorize`, for anything touching another user's data (defense in depth: a controller reorganization should not silently remove an auth check).
- Every piece of external input (request bodies, path/query params, file uploads if ever added) is validated before touching the database or the file system.
- All DB access goes through Spring Data JPA/Hibernate parameterized queries. Raw/native SQL, if ever necessary, must use parameter binding (`?`/named params) — string concatenation into SQL is an automatic rejection, no exceptions.
- **Submitted source code is always untrusted.** It is never executed, `eval`'d, or interpreted anywhere except inside the `execution-worker`'s sandbox containers, and never on a developer's machine, a CI runner, or the `backend` process, for any reason (including "quick manual testing" — use the actual sandbox path, even in dev).
- Passwords: BCrypt only, cost factor 12 minimum. JWT secrets/keys: loaded from environment/secret manager, never hardcoded, never committed (see §24).

## 11. Docker / Code-Execution Rules

- Only `execution-worker` touches the Docker socket. `backend` must have zero Docker client dependency.
- Every sandbox container run must set: `--network none`, `--read-only` (with a scoped `tmpfs` for `/tmp` only), `--cap-drop=ALL`, `--security-opt no-new-privileges`, a memory limit, a CPU limit, `--pids-limit`, a non-root user, and `--rm`. A container launch missing any of these flags is a bug, not a style nit — treat it as a security regression in code review.
- Every run has **two independent time bounds**: the in-container process limit and an external wall-clock watchdog in the worker that force-kills the container. Never rely on only one.
- Language runtime images are pinned by digest in `infrastructure/docker/images/`, rebuilt in CI, never referenced as `:latest` in any environment beyond local iteration.
- Never claim in code comments or docs that this sandbox is a complete security boundary — PRD §18/§25 state the real limitations; keep code comments consistent with that honesty rather than overselling isolation guarantees.

## 12. Concurrency Rules

- Lease acquisition for a job is a **single conditional `UPDATE`** on `execution_jobs` — do not introduce an application-level lock (e.g., a Java `synchronized` block, a distributed lock via Redis) for this; the database row is already the correct serialization point.
- Worker consumer concurrency is a bounded, explicitly configured thread pool — never "one thread per message" unbounded.
- Any code that reads-then-writes a submission's status across two separate statements (rather than one atomic conditional update) must use `@Version` optimistic locking and handle `OptimisticLockException` explicitly (log + discard if the reconciler is authoritative, per PRD §21) — do not let it propagate as an unhandled 500.

## 13. Error-Handling Rules

- Distinguish **judging outcomes** (expected results of running arbitrary code — never retried, always terminal) from **infrastructure failures** (retried per PRD §23, then dead-lettered). This distinction must be explicit in the exception hierarchy — do not use a single generic `JudgingException` for both.
- A caught exception is always either handled meaningfully (retried, mapped to a user-facing error, or explicitly logged-and-swallowed with a comment explaining why swallowing is correct) — never caught and ignored by omission.
- Global exception handling lives in one `@RestControllerAdvice` in `backend` and one centralized handler around the worker's message-processing loop — not duplicated per controller/listener.

## 14. Logging Rules

- Structured JSON logs (Logback). Every log line inside a submission's processing path includes `correlationId` and `submissionId` via MDC.
- Log levels: `ERROR` for infra failures needing attention, `WARN` for retried-but-recoverable events (lease contention, retry attempt N), `INFO` for lifecycle transitions (status changes), `DEBUG` for anything verbose enough to be noisy in production.
- **Never log submitted source code or hidden test case content at `INFO` or above** — both are sensitive/user data; if needed for debugging, log at `DEBUG` behind a profile that is never enabled in `prod`.
- Never log secrets, tokens, or password hashes, at any level.

## 15. Testing Rules

- Non-trivial business logic (lifecycle transitions, idempotency handling, verdict comparison/aggregation, lease acquisition semantics) requires unit tests. "The code compiles" is never sufficient — see §20.
- Critical workflows (submission creation → queued, full judge flow, auth flow, admin CRUD authorization) require integration tests using Testcontainers — no mocking the database for these.
- Sandbox behavior (TLE, MLE, network isolation, filesystem isolation) requires actual Docker-backed tests, tagged so they can be run separately from the fast unit suite if needed, but they must run in CI, not just locally.
- New code touching a module without existing tests should add a minimal test scaffold for that module rather than leaving it permanently untested "because it wasn't tested before."

## 16. Git Rules

- Small, focused commits — one logical change per commit, not "implement phase 5" as a single 40-file commit.
- **Conventional Commits** format: `feat(submissions): add idempotency key handling`, `fix(worker): correct lease expiry check`, `test(judge): add TLE fixture`, `docs(prd): update queue architecture section`, `chore(deps): bump testcontainers to 1.20.x`.
- Never commit: build artifacts, `.env` files, IDE-specific files not covered by `.gitignore`, credentials, generated coverage reports, `node_modules`, `target/`.
- Never bundle unrelated changes into one commit (e.g., a formatting pass mixed into a feature commit) — separate them.
- Commit messages describe *why*, not just *what*, when the "why" isn't obvious from the diff.

## 17. Dependency Rules

- No new library is added without a one-paragraph justification recorded in the PR description (or a `docs/decisions/` note for anything architecturally significant): what problem it solves, why the existing stack (Spring ecosystem, standard library, already-present dependencies) doesn't already solve it, and its maintenance/license status.
- Prefer what's already in the stack: don't add a second HTTP client library, a second JSON library, a second test-assertion library, etc.
- Pin versions explicitly (no floating `+`/range versions) in `pom.xml`/`package.json`.

## 18. Performance Rules

- Do not optimize before measuring. PRD §33/§17 (Phase 17) call for an actual load test before claiming any latency number. Do not write comments or docs claiming specific performance characteristics that haven't been measured — use placeholders and mark them "to be measured."
- Correctness and security take priority over performance per the stated order (§25 here / PRD's priority list): e.g., the "one container per test case" decision (PRD §18) deliberately trades some latency for isolation — do not "optimize" this away without updating the PRD and stating the new tradeoff explicitly.

## 19. Documentation Rules

- Any change to architecture, API surface, schema, or lifecycle **must** update the corresponding PRD.md section in the same PR — a behavior change without a doc update is an incomplete change, not a "will do later."
- OpenAPI spec is regenerated (not hand-edited) whenever controller annotations change.
- New non-obvious decisions get a short rationale comment at the point of the decision, or a `docs/decisions/NNN-title.md` entry for anything with real tradeoffs (mirrors how this PRD documents tradeoffs inline — keep that habit).

## 20. Definition of Done

A task is complete only when **all** of the following are true — "it compiles" is never sufficient:

1. The relevant PRD.md section was read (and updated, if behavior/architecture changed).
2. The implementation follows the layering and conventions in this file.
3. Unit tests exist for new non-trivial logic; integration tests exist for new critical workflows.
4. `mvn -B verify` passes (compilation + all tests + static analysis) locally or in CI.
5. Frontend: `npm run lint && npm run test && npm run build` passes, if frontend code changed.
6. No secrets, credentials, or generated junk are present in the diff.
7. API/database behavior was actually exercised (via a test or a manual `curl`/Swagger UI check), not assumed from reading the code.
8. Documentation (PRD.md, OpenAPI, this file if conventions changed) is updated to match.
9. A summary of changed files and what was verified is provided.

## 21. Rules for Modifying Architecture

- Never silently change architecture (e.g., swapping the queue technology, merging the worker back into the monolith, introducing a new persistence pattern) as a side effect of an unrelated task.
- Any architectural change is proposed explicitly (in the PR description or a `docs/decisions/` entry) with the tradeoff stated, before or alongside the implementation — not discovered by the next reader diffing the code against the PRD.
- If an architectural change is made, PRD.md is updated in the same change set. An architecture that only lives in code and has drifted from PRD.md is treated as a bug.

## 22. Rules for Adding New Dependencies

(See also §17.) Before adding a dependency: (1) check whether an existing dependency already solves the problem; (2) check the license is compatible (permissive — Apache 2.0/MIT/BSD; avoid copyleft for anything linked into the shipped artifact); (3) pin the exact version; (4) document the justification per §17; (5) verify it doesn't duplicate an existing capability (e.g., don't add a second retry library when Resilience4j is already the project's retry mechanism).

## 23. Rules for Migrations

- Every schema change is a new Flyway migration file, never an edit to a previously-applied migration (once a migration has been merged to `main`, it is immutable — fix forward with a new migration).
- Migrations are reversible in spirit (avoid destructive changes without a documented backfill/rollback plan) — for an early-stage project this means: prefer additive changes; when a column must be dropped/renamed, do it in two migrations (add new, backfill, then drop old in a later migration) once there is real data to protect, but a single-step migration is acceptable pre-launch while the schema is still actively taking shape.
- Migration files are named `V{next_number}__{snake_case_description}.sql` and reviewed for the transaction boundaries described in PRD §13.

## 24. Rules for Handling Secrets / Configuration

- No secret (DB password, JWT signing key, Redis auth, RabbitMQ credentials) is ever hardcoded in source, committed in `application.yml` with a real value, or logged.
- Local/dev: `.env` file (git-ignored) consumed by Docker Compose; `application-local.yml` references environment variables (`${DB_PASSWORD}`), never literal values.
- CI: secrets injected via GitHub Actions encrypted secrets, never printed in logs (mask them if a tool would otherwise echo them).
- Production (when this exists beyond MVP): a real secret manager (e.g., environment injection from the hosting platform's secret store) — this file does not prescribe which one, but "hardcoded in the JAR" is never acceptable.
- `.gitignore` must include `.env`, `*.local.yml` with real values, and any credential file — verify this is actually true, don't assume.

## 25. Rules for Agent Behavior

**Priority order for every decision, when multiple valid approaches exist:**

```
CORRECTNESS > SECURITY > MAINTAINABILITY > OBSERVABILITY > PERFORMANCE > CLEVERNESS
```

Do not reach for a distributed-systems pattern, a new abstraction layer, or a "more impressive-sounding" technology because it looks good on a resume. Every technology and pattern in this project has a concrete stated purpose (PRD.md) — if you can't point to the requirement it serves, don't add it.

**The agent must, always:**
- Read `AGENTS.md` (this file) and the relevant `PRD.md` section(s) before implementing.
- Inspect the existing repository structure and code before creating new files — reuse existing services/DTOs/utilities where they already fit; do not create a parallel implementation of something that already exists.
- Avoid unnecessary rewrites — prefer the smallest coherent change that correctly implements the task.
- Never silently change architecture (§21).
- Never introduce a new library without documenting why (§17/§22).
- Never hardcode secrets or commit credentials (§24).
- Never execute user-submitted source code directly on the development host, under any circumstance, including "just to test it quickly."
- Treat all submission input as untrusted; validate all external input.
- Use parameterized queries/ORM safely — no string-built SQL.
- Apply authorization at the service/API boundary (§10).
- Keep controllers thin; keep business and persistence logic out of controllers (§7).
- Prefer immutable data where reasonable (§5).
- Use DTOs at API boundaries, never leak entities (§7).
- Handle errors consistently (§13).
- Add tests for non-trivial business logic and critical workflows (§15).
- Update documentation whenever behavior or architecture changes (§19).
- Run formatting, compilation, tests, and static checks before considering any task complete — and never declare a feature complete merely because the code compiles (§20).

**Exact agent workflow for every task:**

1. Read `AGENTS.md` (this file).
2. Read the relevant `PRD.md` section(s) for the feature being touched.
3. Inspect the repository — actually look at the current state of the affected modules/files, don't assume from memory.
4. Identify which modules/layers are affected (api/service/domain/repository/messaging/worker/frontend).
5. Propose an implementation approach internally (mentally or in the PR description) — pick one, don't leave it ambiguous.
6. Implement the smallest coherent change that satisfies the task.
7. Run tests (`mvn -B verify` / `npm test`).
8. Run static analysis (Checkstyle/SpotBugs / `npm run lint`).
9. Verify actual API/database behavior (integration test, or a manual check against a running instance) — don't infer correctness purely from reading the diff.
10. Update documentation (PRD.md, OpenAPI, this file if conventions changed).
11. Summarize changed files and what was verified, in the response to the human.

**For large tasks**: divide into logical increments (e.g., "add the entity + migration" as one increment, "add the service + tests" as the next, "add the controller + integration test" as the next) rather than attempting one large rewrite in a single pass. Each increment should leave the repository in a buildable, testable state.
