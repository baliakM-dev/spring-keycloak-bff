# spring-keycloak-bff

Reference implementation of a secure Backend-for-Frontend architecture
using Spring Boot, React and Keycloak.

## Current stage

**Stage 1 — infrastructure/skeleton only. Authentication flow is not
implemented yet.**

This stage only proves that the four pieces of the stack (React, Spring
Boot, Keycloak, PostgreSQL) run together locally, that healthchecks work,
and that the frontend can call a public backend endpoint. There is no
login, no session, no CSRF integration, and no authorization logic yet.
See [Not implemented in this stage](#not-implemented-in-this-stage).

## Architecture

### Current (Stage 1)

```text
React + TypeScript (Vite)
        |
        | HTTP (relative /api path, via dev proxy or nginx reverse proxy)
        v
Spring Boot BFF
        |
        | (no OAuth2 login flow configured yet)
        v
Keycloak  (running, reachable, not used by the app yet)
        |
        v
PostgreSQL  (Keycloak's own database only)
```

### Future (target BFF security model — not implemented yet)

```text
Browser / React
        |
        | server-side application session cookie + CSRF protection
        v
Spring Boot BFF
        |
        | OAuth2 / OIDC Authorization Code flow
        | (access + refresh tokens stay server-side)
        v
Keycloak
        |
        v
PostgreSQL
```

React will never own an OAuth access or refresh token, never store tokens
in `localStorage`/`sessionStorage`, and never contain the Keycloak client
secret. Spring Boot will be the OAuth2/OIDC client, keep tokens
server-side, and enforce authorization server-side. This is not built yet
- Stage 1 only prepares the ground (dependency present, client config
placeholder, network topology) for it.

## Stack

| Component | Version | Notes |
|---|---|---|
| Java | 25 (LTS) | |
| Spring Boot | 4.1.1 | latest stable 4.x |
| Spring Security | managed by Spring Boot 4.1.1 BOM (`spring-security-*` 7.1.1) | not pinned separately |
| React | 19.3.0 | |
| TypeScript | 6.0.3 | latest stable **6.x**, not the npm `latest` dist-tag (7.0.2). TypeScript 7.0 is a ground-up rewrite in Go and ships **without a stable programmatic compiler API until 7.1** (targeted for later in 2026) — the TypeScript team's own 7.0 announcement recommends staying on 6.0 for anything that needs that API, and `typescript-eslint`'s current peer range excludes 7.x entirely. TypeScript 6.0 is the last release on the classic JS-based compiler (full API, so ESLint/ts-jest/etc. keep working) and is also the release that introduces the stricter defaults 7.0 assumes (`strict` on by default, `module: esnext`, explicit `types`, modern `moduleResolution`) — this project's `tsconfig.app.json`/`tsconfig.node.json` already used that stricter style, so the 5.9.3 → 6.0.3 upgrade required no config changes. Revisit 7.x once 7.1 ships a stable API and the ecosystem (ESLint, etc.) catches up. |
| Vite | 8.3.0 | |
| Node.js | 24 (active LTS) | see `frontend/.nvmrc` and `engines` in `frontend/package.json` |
| Keycloak | 26.7.3 | Docker image `keycloak/keycloak:26.7.3` |
| PostgreSQL | 18.6 | Docker image `postgres:18.6-alpine`, Keycloak's datastore only |

## Running locally

Preferred: run the whole stack with Docker Compose.

```bash
docker compose up --build
```

This starts, in order (via healthchecks, not fixed sleeps):

1. `keycloak-db` — PostgreSQL for Keycloak
2. `keycloak` — Keycloak 26.7.3, dev mode, imports the `bff-demo` realm
3. `backend` — Spring Boot BFF
4. `frontend` — the built React app served by nginx, which reverse-proxies
   `/api/*` to the `backend` container

Stop and remove everything (including the Keycloak DB volume):

```bash
docker compose down -v
```

### Alternative: frontend outside Docker

For a faster edit/reload loop you can run only the backend (and
optionally Keycloak) in Docker and run the frontend with the Vite dev
server, which proxies `/api` to the backend:

```bash
docker compose up --build backend keycloak-db keycloak   # optional: drop keycloak(-db) if not needed yet
cd frontend
npm install
npm run dev
```

The dev proxy target defaults to `http://localhost:8080` and can be
overridden with the `BACKEND_URL` environment variable (see
`frontend/vite.config.ts`). The proxy currently only forwards `/api`; it is
structured so that `/oauth2`, `/login` and `/logout` routes can be added in
the same shape in a later stage, without rework.

## URLs

| Service | URL |
|---|---|
| Frontend | http://localhost:5173 |
| Backend | http://localhost:8080 |
| Backend public endpoint | http://localhost:8080/api/public/hello (also reachable via the frontend at `/api/public/hello`) |
| Backend health | http://localhost:8080/actuator/health |
| Keycloak | http://localhost:8081 |

These are all real, host-reachable `localhost` URLs. Docker-internal
hostnames (e.g. `http://keycloak:8080`, `http://backend:8080`) are used
only for container-to-container communication (e.g. nginx proxying `/api`
to the `backend` container, or the backend talking to Keycloak in a future
stage) - they are never handed to the browser.

## Test commands

Backend:

```bash
cd backend
./mvnw test
```

Frontend:

```bash
cd frontend
npm test        # vitest, run once (non-watch)
npm run build   # type-check (tsc -b) + production build
```

## Security model

**Not implemented yet in this stage** - described here so the intended
target architecture is explicit and Stage 1 is not mistaken for a finished
auth flow:

- Keycloak will be the identity provider (OIDC issuer).
- Spring Boot will be the confidential OAuth2/OIDC client and will perform
  the Authorization Code flow with Keycloak on the user's behalf.
- OAuth2 access and refresh tokens will be held **server-side only**, tied
  to a server-side HTTP session. React will never receive, store, decode,
  or refresh a token.
- React will authenticate against the BFF using the application session
  cookie, and will learn "who am I" / role information from a future BFF
  endpoint (e.g. `/api/auth/me`) - not by inspecting a token.
- Spring Security's CSRF protection will remain enabled for
  cookie-authenticated, state-changing requests once a session exists.

None of the above is wired up yet. `spring-boot-starter-oauth2-client` is
already a backend dependency and a placeholder `bff-app` Keycloak client
exists in the `bff-demo` realm import, but there is no
`spring.security.oauth2.client.*` configuration, no `oauth2Login()` /
`oauth2Client()` DSL usage, and no callback/token exchange implemented.

## Not implemented in this stage

Explicitly out of scope for Stage 1 (planned for later stages):

- OAuth2 login
- `/api/auth/me`
- CSRF integration between React and Spring
- Logout
- Refresh token handling
- Role mapping / USER-ADMIN authorization
- Session limits
- Brute-force protection tuning
- Business functionality

## Repository layout

```text
spring-keycloak-bff/
├── .github/workflows/   CI (backend mvn test, frontend npm ci/build/test)
├── backend/             Spring Boot BFF (Java 25, Maven)
├── frontend/            React + TypeScript + Vite
├── keycloak/import/     Reproducible bff-demo realm import (dev-only)
├── compose.yaml         docker compose up --build brings up the whole stack
└── README.md
```
