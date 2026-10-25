# Deployment

How to put this system on the internet. The stack is deployable as **one Linux VM running
Docker Compose** — `infrastructure/docker-compose.prod.yml` brings up Postgres, Redis,
RabbitMQ, the backend API, the execution-worker, and a Caddy edge that serves the built
frontend and reverse-proxies `/api` to the backend.

Why a single VM and not a PaaS: the `execution-worker` must reach the host Docker daemon
(`/var/run/docker.sock`) to launch sibling sandbox containers (PRD §30). Fly.io, Render,
Railway, Heroku, and unprivileged Kubernetes do not expose that socket. See
`docs/decisions/0002-deployment-topology.md`.

## 1. Where each piece can run

| Component | Requirement | Can run on |
|---|---|---|
| `frontend` (static build + `/api` proxy) | Any container host | The VM, or Vercel/Netlify/Cloudflare Pages **only with an `/api/*` rewrite to the backend** (the SPA calls relative paths; there is no `VITE_API_URL`) |
| `backend` | Java 21 + Postgres/Redis/RabbitMQ | The VM, or any container host (Fly, Render, Railway, ECS) with managed infra |
| `execution-worker` | Docker socket + host-shared workspace directory | **VM with Docker only.** Not Fly/Render/Railway/Heroku/unprivileged k8s |
| PostgreSQL / Redis / RabbitMQ | — | The VM (default), or managed: Neon/Supabase, Upstash, CloudAMQP |

Recommended hosts, cheapest first (prices are approximate, check before ordering):

| Host | Plan | Spec | Notes |
|---|---|---|---|
| **Hetzner Cloud** | **CPX31** (recommended) | 4 vCPU / 8 GB / 160 GB, x86_64 | Best price/performance; pick an x86_64 plan (CX/CPX), **not** CAX (arm64) |
| Hetzner Cloud | CX32 | 4 vCPU / 8 GB / 80 GB, x86_64 | Cheaper, last-gen CPU |
| DigitalOcean | Basic 4 vCPU / 8 GB | 160 GB SSD | $48/mo; simplest docs/UI |
| AWS Lightsail | 4 vCPU / 16 GB | 320 GB SSD | Pricier, but one flat bill |
| Vultr / OVH / Scaleway | 4 vCPU / 8 GB | varies | Equivalent |

**Use an x86_64 VM.** The judge runtime images pin amd64 base-image digests
(`infrastructure/docker/images/*/Dockerfile`); on an arm64 host (Hetzner CAX, Ampere,
Apple Silicon) those digests do not resolve and the builds fail. Re-pinning arm64 digests
is a separate change.

Sizing rationale: the worker runs 4 concurrent consumers, and each Go/Python/Java compile
container is capped at 512 MB. 4 vCPU / 8 GB keeps four compiles plus the API and datastores
inside the box without oversubscription (oversubscription corrupts TLE timings, PRD §34).

## 2. Before you start

- A VM with a public IPv4, SSH access, and Docker Engine + Compose plugin.
- A domain (e.g. `oj.example.com`) with an **A record pointing at the VM's IP**. Caddy
  cannot issue a certificate until DNS resolves.
- Ports 22, 80, 443 reachable from the internet (80/443 are needed for ACME HTTP-01).
- An x86_64 host (see above).

## 3. Provision the VM

```bash
# Ubuntu 24.04 as a sudo-capable user
sudo apt update && sudo apt install -y git openssl ufw

# Docker Engine + compose plugin from Docker's repository
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt update && sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Firewall: only SSH + HTTP/S. The datastores are not published outside the compose network.
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp
sudo ufw enable

# Service account
sudo useradd -r -m -d /opt/oj -s /bin/bash oj
sudo usermod -aG docker oj
```

## 4. Clone and configure

```bash
sudo -iu oj
# use your SSH remote (or a deploy key / PAT) if the repository is private
git clone https://github.com/AtharvaAgalawe/Online-Judge.git /opt/oj/app
cd /opt/oj/app
git log --oneline -1          # deploy a known commit/tag, not a moving branch
```

Create `infrastructure/.env` (git-ignored, `chmod 600`) — this is the **only** place real
values live:

```bash
umask 077
cat > infrastructure/.env <<EOF
DB_USERNAME=onlinejudge
DB_PASSWORD=$(openssl rand -hex 24)
RABBITMQ_USERNAME=onlinejudge
RABBITMQ_PASSWORD=$(openssl rand -hex 24)
REDIS_PASSWORD=
JWT_SECRET=$(openssl rand -base64 48)

# Public edge
SITE_ADDRESS=oj.example.com
PUBLIC_ORIGIN=https://oj.example.com

# GID of the host's docker group, so the worker container can open the mounted socket
DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)

# Bootstrap exactly one admin (turn off again after the first successful start)
BOOTSTRAP_ADMIN_ENABLED=false
BOOTSTRAP_ADMIN_USERNAME=admin
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_PASSWORD=
EOF
chmod 600 infrastructure/.env
```

Notes:

- `JWT_SECRET` must decode to ≥ 32 bytes (`openssl rand -base64 48` gives 48).
- Compose sets the in-network addresses itself (`DB_URL=jdbc:postgresql://postgres:5432/...`,
  `RABBITMQ_HOST=rabbitmq`, `REDIS_HOST=redis`); the host-facing `DB_URL` in `.env` is not used
  by the prod stack.
- `SITE_ADDRESS=:80` instead of a domain serves plain HTTP on the IP — useful for a first
  smoke test before DNS/TLS is ready.

## 5. Build the judge runtime images

The `languages` table references these exact tags; submissions fail without them. They live
on the **host** daemon, which is the daemon the worker uses.

```bash
for img in oj-java21 oj-python312 oj-gcc13 oj-node22 oj-go123; do
  docker build -t "$img" "infrastructure/docker/images/$img"
done
docker images | grep '^oj-'
```

Rebuild them whenever the Dockerfiles under `infrastructure/docker/images/` change.

## 6. Prepare the worker's host-shared workspace

The worker creates per-submission directories and bind-mounts them into sandbox containers.
The Docker daemon resolves bind sources **on the host**, and the compose file mounts the same
absolute path into the worker container, so the host directory and the container's UID must
line up:

```bash
sudo install -d -o 10001 -g 10001 -m 0750 /var/lib/oj/workspace
```

(`10001` is the fixed non-root UID baked into the application images.)

## 7. Bring the stack up

```bash
cd /opt/oj/app
docker compose -f infrastructure/docker-compose.prod.yml --env-file infrastructure/.env up -d --build

docker compose -f infrastructure/docker-compose.prod.yml ps          # wait for "healthy"
docker compose -f infrastructure/docker-compose.prod.yml logs -f backend
```

First boot: Flyway applies the schema, then Tomcat listens on 8080 inside the network. The
frontend container starts only after the backend reports healthy, and Caddy obtains a
certificate on first request for `oj.example.com`.

## 8. Create the admin (once)

```bash
# set BOOTSTRAP_ADMIN_ENABLED=true and BOOTSTRAP_ADMIN_PASSWORD=<strong> in infrastructure/.env
docker compose -f infrastructure/docker-compose.prod.yml --env-file infrastructure/.env up -d backend
docker compose -f infrastructure/docker-compose.prod.yml --env-file infrastructure/.env logs backend | grep -i admin

# then turn it back off and recreate the container
sed -i 's/^BOOTSTRAP_ADMIN_ENABLED=.*/BOOTSTRAP_ADMIN_ENABLED=false/' infrastructure/.env
sed -i '/^BOOTSTRAP_ADMIN_PASSWORD=/d' infrastructure/.env
docker compose -f infrastructure/docker-compose.prod.yml --env-file infrastructure/.env up -d backend
```

Bootstrap only ever creates an account; it never modifies an existing one, and it only runs
on the (default) `local` profile — which is what the prod stack uses, so leaving it enabled
with a password present is the risk to avoid. There is deliberately no UI for role changes.

## 9. Verify — actually exercise it

```bash
curl -s https://oj.example.com/actuator/health                 # {"status":"UP"}
curl -sI https://oj.example.com/submissions/1 | head -1        # 200 (SPA fallback)
curl -s https://oj.example.com/api/v1/problems | head -c 200   # JSON page

# Full solver journey: register → login → refresh → problems → submit → poll verdict
# (run from any machine with Node 20+ — the VM itself does not need Node; BASE_URL points at the edge)
BASE_URL=https://oj.example.com node frontend/scripts/verify-phase11-live.mjs
```

Then log in through the browser, open a published problem, and submit a trivial correct
solution. Watch the judging path end to end:

```bash
docker compose -f infrastructure/docker-compose.prod.yml logs -f worker | grep -i submissionId
```

A verdict that never leaves `QUEUED` means the worker is not consuming (check
`docker compose ps`, then the RabbitMQ queue in the logs) — not a frontend problem.

## 10. TLS and the domain

`SITE_ADDRESS` decides the edge mode. With a domain set, Caddy requests a Let's
Encrypt certificate on first HTTPS request, renews it automatically, and redirects HTTP → HTTPS.
Certificates persist in the `caddy-data` volume, so do not delete it casually.

```bash
docker compose -f infrastructure/docker-compose.prod.yml --env-file infrastructure/.env \
  logs frontend | grep -i -E "certificate|acme"
```

If issuance fails: DNS not pointing at the VM yet, port 80 blocked, or the domain was already
rate-limited by Let's Encrypt — fix DNS/firewall first.

## 11. Updates and rollback

```bash
cd /opt/oj/app
git fetch --tags && git checkout <commit-or-tag>      # pin what you deploy
docker compose -f infrastructure/docker-compose.prod.yml --env-file infrastructure/.env up -d --build
```

Migrations run on backend start. To roll back code, check out the previous commit and rebuild;
Flyway migrations are forward-only (AGENTS.md §23) — a rollback that must undo a schema change
needs a new forward migration, not a downgrade.

To deploy images built elsewhere instead of building on the VM, set `BACKEND_IMAGE`,
`WORKER_IMAGE`, `FRONTEND_IMAGE` in `.env` and drop `--build`.

## 12. Backups

```bash
# nightly at 03:15 via cron on the VM
docker compose -f /opt/oj/app/infrastructure/docker-compose.prod.yml --env-file /opt/oj/app/infrastructure/.env \
  exec -T postgres pg_dump -U onlinejudge onlinejudge | gzip > /opt/oj/backup-$(date +%F).sql.gz
```

Restore into a fresh volume:

```bash
gunzip -c backup-2026-10-25.sql.gz | docker compose -f infrastructure/docker-compose.prod.yml \
  --env-file infrastructure/.env exec -T postgres psql -U onlinejudge -d onlinejudge
```

Also back up `infrastructure/.env` (the JWT secret invalidates every session if lost) —
in a secrets manager, never in git.

## 13. Scaling

- **Workers**: `docker compose ... up -d --scale worker=2` (leases are atomic conditional
  updates, so replicas are safe). Size each replica's `mem_limit` and remember each runs 4
  containers concurrently.
- **Backend**: stateless; put several replicas behind a load balancer. Caddy here proxies to a
  single `backend:8080` upstream — enabling Compose's DNS round-robin or listing several
  upstreams in the Caddyfile is required before scaling it out.
- **Datastores**: the single Postgres instance is the eventual bottleneck (PRD §34). Move to a
  managed instance before going multi-VM.

## 14. Known limits (be honest about these)

- The application runs on the **default `local` Spring profile**; there is no separate `prod`
  profile yet. It is environment-driven, so this is safe as long as `.env` carries real secrets
  and `BOOTSTRAP_ADMIN_ENABLED=false`.
- Membership in the `docker` group is root-equivalent. The worker container is trusted code
  (not user code) but treat the VM as single-tenant, and never co-locate untrusted workloads.
- The sandbox is a hardened namespace/cgroup boundary on a shared kernel, not a VM
  (PRD §18/§25). A host-level kernel exploit is out of scope of what Docker can prevent.
- `cd.yml` (PRD Phase 16) is not built yet: deployment builds images on the VM. That takes a
  few minutes and needs network access to Maven Central, npm, and Docker Hub.
- The stack targets **Linux**. On Docker Desktop / Colima the daemon runs inside a VM, so a
  nested bind-mount source (the worker's workspace) is resolved inside that VM; the containerized
  worker is not expected to judge there. For macOS development use the host-process workflow in
  the README instead of the prod compose file.
- One host = one failure domain. Postgres/Redis/RabbitMQ have volumes but no replica.

## 15. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `frontend` exits, Caddy logs "no such host" | DNS for `SITE_ADDRESS` not resolvable yet |
| HTTPS certificate not issued | Port 80 blocked, DNS wrong, or ACME rate limit |
| `502` from the edge | Backend unhealthy: `docker compose ps`, then `logs backend` |
| Backend won't start: JWT error | `JWT_SECRET` missing or decodes to < 32 bytes |
| Backend won't start: DB auth failed | `postgres-data` volume was initialized with different credentials — `down -v` or fix `.env` |
| Submissions stuck in `QUEUED` | Worker not running, or not connected to RabbitMQ |
| Worker: "Cannot connect to the Docker daemon" | `/var/run/docker.sock` not mounted, or `DOCKER_GID` wrong (`stat -c '%g' /var/run/docker.sock`) |
| Worker: `java.net.BindException: Permission denied` when creating a container | Same root cause: the container's supplementary GID is not the socket's group. Fix `DOCKER_GID` in `.env` and `up -d worker` again |
| Worker runs but judging fails with a mount error | `/var/lib/oj/workspace` missing, wrong owner (must be uid 10001), or a different path inside the container |
| Judge fails: image not found | Judge runtime images not built on this host (§5) |
| Port 80/443 already allocated | A host web server (nginx/apache) is running — stop it; Caddy owns those ports |
