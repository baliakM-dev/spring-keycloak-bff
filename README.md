# spring-keycloak-bff

Reference implementation of a secure Backend-for-Frontend architecture
using Spring Boot, React and Keycloak.

## Current stage

**Stage 2B — Authentication state, home redirect and a protected React
route.**

Building on Stage 2A's real Keycloak login: a successful login now
deterministically returns to the frontend's public home page (`/`), which
loads session state from an application-owned `GET /api/auth/me` view and
shows the signed-in user's display name and a link to a protected React
route (`/protected`). Session loss (an expired/invalidated backend
session) produces a clear signed-out UI without redirect loops. React
still never receives, stores or manages OAuth tokens - it only ever reads
the small, explicit JSON shape from `/api/auth/me`. See
[Not implemented in this stage](#not-implemented-in-this-stage) for what is
intentionally still missing (logout, role mapping, SPA CSRF plumbing,
etc.).

## Architecture

### Current (Stage 2A + 2B)

```text
Browser / React
        |
        | application session cookie (JSESSIONID)
        v
Spring Boot BFF
        |
        | OAuth2 / OIDC Authorization Code flow (+ PKCE)
        | access + refresh tokens stay server-side
        v
Keycloak
        |
        v
PostgreSQL  (Keycloak's own database only)
```

React never owns an OAuth access or refresh token, never stores tokens in
`localStorage`/`sessionStorage`, and never contains the Keycloak client
secret. It only ever sees the application session cookie set by Spring
Boot. Concretely:

1. React renders a same-origin `<a href="/oauth2/authorization/bff-app">Login</a>`
   link - a real browser navigation, not a `fetch`/`axios` call.
2. Spring Security's `oauth2Login()` redirects the browser to Keycloak's
   authorization endpoint (browser-facing `http://localhost:8081`), with
   `state`, OIDC `nonce`, and a PKCE `code_challenge`/`code_challenge_method=S256`
   all generated and validated by the framework - no custom code.
3. The user authenticates against Keycloak.
4. Keycloak redirects back to the literal, pre-registered
   `http://localhost:5173/login/oauth2/code/bff-app` callback.
5. Spring Boot exchanges the authorization code for tokens directly against
   Keycloak's Docker-internal, backend-facing URL (`http://keycloak:8080`),
   validates the ID token's `iss` claim against Keycloak's real, fixed
   issuer (`http://localhost:8081/realms/bff-demo`), and calls the userinfo
   endpoint.
6. Spring Security establishes an authenticated `HttpSession`; the browser
   only receives the resulting `JSESSIONID` cookie.
7. **(Stage 2B)** The request cache is disabled and the OAuth2 login
   success handler has a fixed `defaultSuccessUrl("/", true)`, so the
   browser is always redirected to the frontend's public home page next -
   never to whatever URL happened to trigger authentication, and never
   derived from a header or request parameter.
8. **(Stage 2B)** React's `SessionProvider` calls the same-origin
   `GET /api/auth/me` (session cookie only) to learn `loading` /
   `authenticated` / `anonymous` / `error` state, renders the display name
   and a link to `/protected` when authenticated, and re-checks on window
   focus/tab-visibility change (not polling). `/protected` is a
   client-side-routed page whose guard is UX only: it still always calls
   the real, independently-enforced `GET /api/protected/hello`, and a 401
   from any protected call (never a 403) clears stale state back to
   `anonymous`.

### Not yet built (later stages)

Logout, USER/ADMIN role mapping, role-based authorization, explicit
refresh-token handling, session concurrency limits, brute-force tuning,
MFA, registration, password reset, full SPA CSRF integration, application-
user persistence, business functionality. See
[Not implemented in this stage](#not-implemented-in-this-stage).

## Stack

| Component | Version | Notes |
|---|---|---|
| Java | 25 (LTS) | |
| Spring Boot | 4.1.1 | latest stable 4.x |
| Spring Security | managed by Spring Boot 4.1.1 BOM (`spring-security-*` 7.1.1) | not pinned separately |
| React | 19.3.0 | |
| TypeScript | 6.0.3 | latest stable **6.x** - see rationale in git history; unchanged from Stage 1 |
| Vite | 8.3.0 | |
| Node.js | 24 (active LTS) | see `frontend/.nvmrc` and `engines` in `frontend/package.json` |
| react-router | 8.4.0 | latest stable; minimal client-side routing for `/` and `/protected` only - not an authorization mechanism |
| Keycloak | 26.7.3 | Docker image `keycloak/keycloak:26.7.3` |
| PostgreSQL | 18.6 | Docker image `postgres:18.6-alpine`, Keycloak's datastore only |

## Running locally

Preferred: run the whole stack with Docker Compose.

```bash
docker compose up --build
```

> **Known local issue — `docker compose up --build` hangs and never starts
> the stack (unresolved, environment issue, not an application defect):**
> on this project's dev machine (Docker Desktop 28.4.0, `docker-buildx`
> v0.28.0-desktop.1), `docker compose up --build` and even plain
> `docker compose build` reproducibly (confirmed on repeated, from-clean
> runs) hang forever and never start any container. This is **not** a
> build failure and **not** a slow build: the BuildKit progress output
> shows every layer completing and both images being exported and named
> (`naming to docker.io/library/spring-keycloak-bff-backend:latest done`,
> same for `frontend`) — confirmed by the resulting image IDs' `docker
> images` creation timestamps matching the build run. The hang happens
> *after* that, inside the `docker-buildx bake --file - --progress
> rawjson --metadata-file ...` subprocess Compose delegates the build to:
> it never writes its `--metadata-file` and never returns control to
> `docker compose`, so `docker compose up --build` never reaches the
> "create/start containers" step at all — `docker compose ps` shows no
> containers, however long you wait. Setting `COMPOSE_BAKE=false` (the
> documented escape hatch back to the pre-bake build path) does **not**
> help either: in that mode the build instead stalls between services
> (confirmed: backend image exported, frontend build never even starts)
> with zero CPU activity, for as long as observed (multiple minutes).
>
> **Verified workaround** — build and run as two separate, explicit
> steps instead of relying on `up --build`'s combined flow:
>
> ```bash
> docker compose build --progress=plain   # build only; let it run
> # Once you see BOTH services' "[<service>] exporting to image ... naming
> # to docker.io/library/spring-keycloak-bff-<service>:latest done" lines,
> # the images are genuinely ready even though the command keeps running -
> # Ctrl-C it (or `kill` the hung `docker-buildx bake` / `docker compose
> # build` processes from another terminal).
> docker compose up -d                    # starts containers from the
>                                          # images just built, no rebuild
> docker compose ps                       # confirm all 4 services healthy
> ```
>
> This was verified end-to-end on this machine: after the workaround,
> `docker inspect <container> --format '{{.Image}}'` for `backend` and
> `frontend` matched exactly the image IDs produced by the `--build` run
> that hung, confirming the running stack was not running stale images.
> This is a Docker Desktop/BuildKit CLI environment issue, not something
> fixable in this repository's `compose.yaml`/Dockerfiles — if you don't
> hit it on your machine, plain `docker compose up --build` should work
> as documented below.

This starts, in order (via healthchecks, not fixed sleeps):

1. `keycloak-db` — PostgreSQL for Keycloak
2. `keycloak` — Keycloak 26.7.3, dev mode, imports the `bff-demo` realm
   (confidential `bff-app` client + one disposable `test-user`)
3. `backend` — Spring Boot BFF, OAuth2/OIDC client for `bff-app`; waits for
   `keycloak` to be healthy before starting
4. `frontend` — the built React app served by nginx, which reverse-proxies
   `/api/*`, `/oauth2/*` and `/login/*` to the `backend` container

Stop and remove everything (including the Keycloak DB volume - required
for the realm import to be re-applied from a clean state):

```bash
docker compose down -v
```

### Alternative: frontend outside Docker

For a faster edit/reload loop you can run only the backend (and Keycloak)
in Docker and run the frontend with the Vite dev server, which proxies
`/api`, `/oauth2` and `/login` to the backend:

```bash
docker compose up --build backend keycloak-db keycloak
cd frontend
npm install
npm run dev
```

The dev proxy target defaults to `http://localhost:8080` and can be
overridden with the `BACKEND_URL` environment variable (see
`frontend/vite.config.ts`).

## Local test login (LOCAL DEVELOPMENT ONLY)

A single disposable user is imported into the `bff-demo` realm purely to
prove the login flow works. It has no application role mapping and no real
personal information:

| Field | Value |
|---|---|
| Username | `test-user` |
| Password | `test-user-local-dev-only` |

**These credentials are LOCAL DEVELOPMENT ONLY.** They protect nothing
beyond a throwaway local Keycloak container and must never be reused
anywhere else.

To log in: open http://localhost:5173, click **Login**, sign in with the
credentials above. You will be redirected back to `/` with an authenticated
session, showing "Signed in as Test User" and a link to `/protected`, which
calls `GET /api/protected/hello` and displays `{"message":"authenticated"}`.

## URLs

| Service | URL |
|---|---|
| Frontend home | http://localhost:5173 |
| Frontend protected page | http://localhost:5173/protected (UX-only route guard; real enforcement is server-side) |
| Backend | http://localhost:8080 |
| Backend public endpoint | http://localhost:8080/api/public/hello (also via the frontend at `/api/public/hello`) |
| Backend session view | http://localhost:8080/api/auth/me (also via the frontend at `/api/auth/me`; `200`/`{"authenticated":true,"user":{"id":...,"displayName":...}}` or `401`/`{"authenticated":false}`, always `Cache-Control: no-store`) |
| Backend protected endpoint | http://localhost:8080/api/protected/hello (requires an authenticated session, returns a bare `401` when anonymous; also via the frontend at `/api/protected/hello`) |
| Backend health | http://localhost:8080/actuator/health |
| Login (starts OAuth2/OIDC flow) | http://localhost:5173/oauth2/authorization/bff-app |
| Keycloak | http://localhost:8081 |

Docker-internal hostnames (`http://keycloak:8080`, `http://backend:8080`)
are used only for container-to-container communication (nginx proxying to
`backend`, the backend's token/userinfo/JWK calls to `keycloak`) - they are
never handed to the browser. The backend's `ClientRegistrationRepository`
(`backend/src/main/java/com/example/bff/config/OAuth2ClientConfig.java`)
explicitly separates these browser-facing and backend-facing URLs; see the
class Javadoc for why Spring Boot's `issuer-uri` autodiscovery cannot be
used here.

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

```text
Browser / React
        |
        | application session (JSESSIONID cookie only)
        v
Spring Boot BFF
        |
        | OAuth2/OIDC (Authorization Code + PKCE)
        v
Keycloak
```

React does not own OAuth access or refresh tokens, does not decode any
token, and does not contain the Keycloak client secret.

- Keycloak is the identity provider (OIDC issuer) for the `bff-demo` realm.
- Spring Boot is a **confidential** OAuth2/OIDC client (`bff-app`):
  `client-secret-basic` authentication, Standard Flow (Authorization Code)
  enabled, Direct Access Grants disabled, Implicit Flow disabled, Service
  Accounts disabled.
- PKCE (S256) is explicitly enabled as defense-in-depth on top of the
  confidential client, via a custom `authorizationRequestResolver`
  wrapping Spring Security's `OAuth2AuthorizationRequestCustomizers.withPkce()`.
  **Verified at runtime**: the real `/oauth2/authorization/bff-app`
  redirect against the running stack includes
  `code_challenge=...&code_challenge_method=S256`.
- OAuth state and OIDC nonce are handled entirely by Spring Security's
  default `HttpSessionOAuth2AuthorizationRequestRepository` and
  `OidcAuthorizationCodeAuthenticationProvider` - no custom code.
- The `ClientRegistrationRepository` is defined manually in Java
  (`OAuth2ClientConfig`), not via YAML `issuer-uri` autodiscovery, because
  Keycloak's fixed `KC_HOSTNAME`/`KC_HOSTNAME_PORT` makes every discovery
  document report the same `issuer` regardless of which URL fetched it,
  which is incompatible with Spring's autodiscovery equality check across
  the browser-facing vs. Docker-internal split this project needs.
- **Token custody**: the `OAuth2AuthorizedClient` (access + refresh token)
  is held server-side via Spring Security's default, unmodified
  `HttpSessionOAuth2AuthorizedClientRepository` - i.e. tied to the backend
  `HttpSession`, never serialized to the browser. The browser only ever
  receives the `JSESSIONID` session cookie (`HttpOnly`, no value logged or
  documented here).
- `GET /api/protected/hello` is covered by the same
  `anyRequest().authenticated()` default-deny rule as before; no
  endpoint-specific security code. **(Stage 2B)** Anonymous requests to it
  (and any other authenticated-only `/api/**` path) now receive a bare
  `401`, via a request-matcher-scoped
  `defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(401), "/api/**")`
  - not Spring's default redirect-to-Keycloak behavior, which would be
  indistinguishable from a network failure to a `fetch()` caller. Real
  browser navigation to `/oauth2/authorization/bff-app` is unaffected: it
  is served upstream by `OAuth2AuthorizationRequestRedirectFilter`, before
  this entry point ever applies.
- **(Stage 2B)** `GET /api/auth/me` is `permitAll()` at the filter-chain
  level and performs its own authentication check inside the controller
  (`@AuthenticationPrincipal OidcUser`), so it can return a real `401` JSON
  body to an anonymous caller instead of participating in the redirect
  behavior above. It returns only `id` (the OIDC `sub` claim, via
  `getSubject()` - **not** `getName()`, since the client registration sets
  `userNameAttributeName("preferred_username")`) and a `displayName`
  (fallback: `name` claim → `preferred_username` claim → `sub`). It never
  returns tokens, raw claims, email, roles, or a framework
  `Authentication`/`OidcUser`/`OAuth2AuthorizedClient` object, and always
  sets `Cache-Control: no-store`.
- **(Stage 2B)** The HTTP request cache is disabled
  (`requestCache(RequestCacheConfigurer::disable)`), so an anonymous hit on
  a protected `/api/**` path can never populate a saved-request session
  attribute that would otherwise hijack the post-login redirect target.
- Spring Security's default CSRF protection is **untouched** - not
  disabled, not weakened. The OAuth2 login/callback endpoints
  (`/oauth2/authorization/bff-app`, `/login/oauth2/code/bff-app`) and
  `/api/auth/me` are all `GET`-only, so no CSRF exemption was needed.
- No CORS configuration was added anywhere: the frontend, `/api`,
  `/oauth2` and `/login` are all same-origin through the dev/Docker proxy
  in both environments.
- **(Stage 2B)** React's session state (`frontend/src/auth/SessionContext.tsx`)
  is kept in memory only (React state), never written to `localStorage`/
  `sessionStorage`. It reacts specifically to a `401` (never a `403` or a
  generic non-2xx) to clear stale authenticated state, never auto-triggers
  login, and guards against a late/out-of-order `/api/auth/me` response
  re-authenticating the UI after a more recent sign-out via a monotonic
  generation counter (re-checked both before and after the response body
  is parsed - see `SessionContext.test.tsx` for the regression test).

### Session cookie / production note

The session cookie is left at Spring Boot's **default** configuration
(not forced `Secure`) - forcing `cookie.secure=true` globally would break
this plain-HTTP `localhost` Docker Compose setup by silently dropping the
session cookie. **This is a local-development-only posture.** A real
deployment must run behind HTTPS and set `server.servlet.session.cookie.secure=true`
(and equivalent `Secure`/environment-specific `SameSite` cookie
configuration) via environment-specific Spring configuration - this is not
implemented as part of Stage 2A.

## Keycloak configuration (`keycloak/import/bff-demo-realm.json`)

| Setting | Value |
|---|---|
| Realm | `bff-demo` |
| Client ID | `bff-app` |
| Client type | confidential (`publicClient: false`), `client-secret` authentication |
| Standard Flow (Authorization Code) | enabled |
| Direct Access Grants | disabled |
| Implicit Flow | disabled |
| Service Accounts | disabled |
| Redirect URIs | `http://localhost:5173/login/oauth2/code/bff-app` (literal, no wildcard) |
| Web Origins | `http://localhost:5173` |
| `sslRequired` | `external` (Keycloak's default; requests from the host machine/loopback are exempt - verified working end-to-end against the real Docker stack, so left unchanged) |

The client secret (`dev-only-placeholder-secret-CHANGE-ME` by default) is
only ever read by the `backend` service, via the `KEYCLOAK_CLIENT_SECRET`
environment variable in `compose.yaml` → `bff.oauth2.client-secret` in
`application.yml`. It is never passed to the `frontend` service/build,
never present in any frontend-reachable file, and never returned by any
endpoint.

## Not implemented in this stage

Explicitly out of scope for Stage 2B (planned for later stages):

- Logout (no logout button/endpoint yet - deliberately not displayed)
- Full SPA CSRF token plumbing (no unsafe-method frontend request needs it yet)
- USER/ADMIN role mapping
- Role-based application authorization
- Explicit custom refresh-token handling
- Session concurrency limits
- Brute-force protection tuning
- MFA/WebAuthn
- Registration
- Password reset
- Business functionality
- Database persistence for application users
- Custom login page (Keycloak's default login page is used)
- Returning to the originally requested protected route after login (home
  is always the deterministic post-login destination in this stage)

Do not treat any of the above as a Stage 2B defect - they are intentionally
deferred.

## Repository layout

```text
spring-keycloak-bff/
├── .github/workflows/   CI (backend mvn test, frontend npm ci/build/test)
├── backend/             Spring Boot BFF (Java 25, Maven)
│   └── src/main/java/com/example/bff/
│       ├── config/SecurityConfig.java       oauth2Login() + PKCE + /api/** 401 entry point +
│       │                                    disabled request cache + fixed post-login "/" redirect
│       ├── config/OAuth2ClientConfig.java   manual ClientRegistrationRepository (bff-app)
│       ├── api/ProtectedController.java     GET /api/protected/hello
│       └── api/AuthController.java          GET /api/auth/me (+ UserView, MeResponse DTOs)
├── frontend/            React + TypeScript + Vite (Login link, no OAuth library)
│   └── src/
│       ├── auth/SessionContext.tsx          loading/authenticated/anonymous/error session state
│       ├── pages/HomePage.tsx               public "/" - display name + link to /protected
│       ├── pages/ProtectedPage.tsx          "/protected" - UX-only guard, calls the real API
│       └── App.tsx                          react-router routes, wraps SessionProvider
├── keycloak/import/     Reproducible bff-demo realm import (dev-only)
├── compose.yaml         docker compose up --build brings up the whole stack
└── README.md
```
