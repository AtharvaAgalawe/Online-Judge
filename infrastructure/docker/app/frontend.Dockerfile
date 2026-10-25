# Frontend image: static Vite build served by Caddy, which also reverse-proxies /api to the
# backend so the SPA stays same-origin (frontend/src/api/* uses relative paths).
# Build context is the repository root: `docker build -f infrastructure/docker/app/frontend.Dockerfile .`
FROM node:22-alpine AS build
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Caddy instead of nginx+certbot: one container, automatic Let's Encrypt issuance and
# renewal, and a config file short enough to audit (docs/decisions/0002).
FROM caddy:2.11.7-alpine
COPY infrastructure/docker/app/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist /srv
EXPOSE 80 443
