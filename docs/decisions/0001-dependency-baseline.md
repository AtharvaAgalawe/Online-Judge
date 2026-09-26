# Dependency Baseline

Records the justification for every non-Spring-Boot-managed dependency, per AGENTS.md §17/§22. Spring Boot-managed dependencies (starters, Flyway, Postgres driver, Logback, etc.) inherit pinned versions from the Spring Boot 3.5.16 BOM.

## Runtime

| Dependency | Version | Why it exists; why the existing stack does not already solve it | License / maintenance |
|---|---|---|---|
| `spring-boot-starter-*` | 3.5.16 (BOM) | Fixed by PRD §4 technology rules. | Permissive (Apache 2.0 / EPL) / Pivotal-led, very active |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.9.1 | PRD §29/§9: OpenAPI spec generated from annotated controllers. Spring Boot has no built-in OpenAPI generator. | Apache 2.0 / active |
| `com.github.docker-java:docker-java-core` + `docker-java-transport-httpclient5` | 3.7.1 | PRD §30: the execution-worker launches sibling sandbox containers via the mounted Docker socket. This is the standard maintained JVM Docker client; Spring Boot has no Docker client. **Only the worker depends on it — the backend must never** (AGENTS.md §2). | Apache 2.0 / active |
| `io.github.resilience4j:resilience4j-spring-boot3` | 2.4.0 | PRD §23: bounded in-process retry (3 attempts, exponential backoff) around the transactional result write, as defense in depth beneath the queue-level retry. Spring Retry exists but Resilience4j is named by the PRD; adding both would duplicate capability. | Apache 2.0 / active |
| `net.logstash.logback:logstash-logback-encoder` | 8.1 | PRD §27: structured JSON logs via Logback. Logback alone cannot emit JSON. Compatible with the Logback 1.5.x line managed by Boot. | Apache 2.0 / active |
| `org.flywaydb:flyway-core` + `flyway-database-postgresql` | 11.7.2 (BOM) | PRD §4/AGENTS.md §8: versioned schema migrations instead of `ddl-auto`. | Apache 2.0 / active |

## Test

| Dependency | Version | Why |
|---|---|---|
| `org.testcontainers:*` | 1.21.4 (BOM-pinned explicitly) | PRD §28: real Postgres/RabbitMQ integration tests; mocking the database for critical workflows is forbidden (AGENTS.md §15). |
| `spring-boot-starter-test`, `spring-security-test`, `spring-rabbit-test` | BOM | JUnit 5, Mockito, MockMvc, security and AMQP test support — no second assertion library added. |

## Frontend

Pinned exactly in `frontend/package.json` (React 18 + Vite 5 + TypeScript 5.6, ESLint 9 flat config, Vitest 2 + Testing Library) — the stack fixed by PRD §4/§31. No UI/HTTP/state library beyond that yet; React Query arrives with Phase 11 and will be justified then.

## Notable exclusions

- **MapStruct** — AGENTS.md §7 prefers hand-written mappers while the mapping surface is small.
- **Kafka** — PRD §19 settles RabbitMQ for work-queue semantics; do not re-litigate.
- **Lombok** — annotation processing hides constructor injection and mutability from readers; plain Java is used instead.
