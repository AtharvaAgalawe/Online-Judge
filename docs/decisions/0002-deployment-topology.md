# 0002 — Production deployment topology

**Status:** accepted · **Date:** 2026-10-25

## Context

PRD §29 scoped actual deployment out of the MVP, and no application images existed: only the
judge runtime images under `infrastructure/docker/images/`. We need a repeatable way to run the
whole system (frontend + backend + worker + datastores) on a real host, without inventing a
platform we cannot operate.

Hard constraints found while implementing this:

1. The SPA calls **relative** `/api/v1/...` paths (`frontend/src/api/client.ts`); there is no
   build-time API base URL. It must be served same-origin behind a proxy, or the code changes.
2. `execution-worker` launches sandbox containers through the **host** Docker daemon
   (`/var/run/docker.sock`) and bind-mounts a workspace directory into them.
3. The backend owns Flyway (`flyway.enabled: false` on the worker), so it must start first.

## Decision

1. **Single VM, Docker Compose** (`infrastructure/docker-compose.prod.yml`), not Kubernetes and
   not a PaaS. Constraint 2 rules out Fly/Render/Railway/Heroku and unprivileged k8s; Caddy on
   the same host satisfies constraint 1 with zero application changes.
2. **The worker runs in a container but with the workspace mounted at an identical absolute
   path** (`/var/lib/oj/workspace:/var/lib/oj/workspace`) and `-Djava.io.tmpdir` pointed at it,
   because the daemon resolves bind sources on the host. Socket access is granted via
   `group_add` with the host `docker` GID rather than running the container as root.
3. **Caddy instead of nginx + certbot** as the edge. One config file, automatic certificate
   issuance/renewal, and no cron-driven reload to forget. Rejected nginx+certbot because it is
   two moving parts and an extra renewal failure mode for a single-VM deployment; rejected a
   host-level nginx because the container needs the same config everywhere.
4. Application images use a fixed non-root UID 10001, matching the ownership of the mounted
   workspace and of the files the sandbox containers must read.

## Consequences

- Deploying is one command per host, but the host is a single failure domain: Postgres, Redis,
  and RabbitMQ have volumes, not replicas (PRD §34 documents the scale-out path).
- The VM must be **x86_64** while the judge images pin amd64 digests; arm64 support means
  re-pinning those images (deferred, not blocked).
- The application still runs on the default `local` Spring profile; adding a `prod` profile is
  a follow-up that does not change this topology.
- `cd.yml` (PRD Phase 16) stays unimplemented; images are built on the host by Compose. Setting
  `*_IMAGE` in `.env` allows pulling pre-built images instead, without touching the compose file.
