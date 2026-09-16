# Docker / Local Development

## Local architecture

Typical local stack (a development/reference pattern, not a deployment
requirement):

```
React (dev server or built assets)
       |
Spring Boot BFF (container or local process)
       |
Keycloak (container)
       |
PostgreSQL (container, if Keycloak needs persistent storage)
```

Docker Compose is a convenient way to run this stack locally. It is not a
requirement for every deployment — production topology may differ
entirely (managed Keycloak, separate DB, no compose at all). Nothing here
implies Docker is mandatory.

## Keycloak

- Run Keycloak as a container for local development.
- Realm import: import a development realm/client definition at startup
  rather than configuring by hand each time (see "Realm export" below for
  what that file must and must not contain).
- Client configuration for development: confidential client, client
  authentication enabled — same invariants as `security-invariants.md`.
  Development convenience never means relaxing who the OAuth2 client is
  or how it authenticates.
- Redirect URI and Web Origins for development: explicit values matching
  the actual local URLs React/Spring are reachable on (e.g.
  `http://localhost:5173/...`, `http://localhost:8080/...`), not a
  wildcard — the redirect URI list is a real security control even in
  development.
- Keep development and production Keycloak configuration (realm, client,
  secrets) separate and clearly distinguishable — different realms/clients
  per environment, not one client reused with environment-conditional
  behavior.
- No production secrets stored in the repository, in compose files, or in
  a development realm export — see "Secrets" below.

## Secrets

Canonical rule: `security-invariants.md` §Client secret. Summary as it
applies to a local Compose setup — a credential may live in a compose
file/`.env` only if it meets **all four** of that section's
development-only conditions (belongs exclusively to a disposable local
Keycloak, protects no external/shared environment, is explicitly labeled
development-only, has no security impact if exposed). Anything else — a
production, staging, or shared-environment secret — MUST NOT be
committed, in any file (compose file, `.env`, realm export, README).
Don't assume a secret is safe to commit just because it's in a file
labeled "dev" — check it actually meets all four conditions.

Additionally: secrets MUST NOT be passed into the React build as
build-time variables — anything baked into a frontend build is public,
and React has no legitimate reason to hold a Keycloak client secret at
all, in any environment.

## Networking

Distinguish these clearly — this is the most common source of local BFF
breakage:

```
Docker service hostname   -> resolvable only between containers on the
                              same Docker network (e.g. "keycloak")
Browser hostname           -> whatever the host machine exposes
                              (e.g. "localhost", a mapped port)
Spring -> Keycloak          -> server-to-server; can use the Docker
                              service hostname
Browser -> Spring           -> browser-to-server; must use a hostname
                              the browser can actually resolve
```

Common failure: `http://keycloak:8080` works for Spring's server-to-server
calls (token exchange, JWKS, etc.) because Spring runs inside the Docker
network, but the browser cannot resolve `keycloak` and will fail on any
URL it is given directly (e.g. the authorization redirect, or an
issuer/public URL surfaced to the client).

Design the issuer URL, redirect URIs, and any URL exposed to the browser
around the actual topology in use:

- If both Spring and the browser need to reach Keycloak, Keycloak may need
  distinct internal (Docker-network) and external (browser-reachable) URLs
  configured consistently, or a single browser-reachable URL used by both
  — check what Spring Security's OIDC client configuration and Keycloak's
  hostname/frontend-URL settings expect for the project's actual versions
  before choosing an approach.
- There is no single hostname that works for every deployment topology —
  derive it from where the browser and each service actually run relative
  to each other, don't hardcode one as "the" answer.

## Cookies / HTTPS

Development may reasonably use different cookie/HTTPS settings than
production (e.g. no TLS on localhost). Production still requires `Secure`
cookies and HTTPS per `sessions.md`.

Keep this difference in environment-specific configuration (profiles,
env-specific properties), not by disabling a security mechanism globally.
A relaxation that's harmless because localhost isn't network-reachable is
not safe as a default that could ship to production.

## CORS / CSRF

Local Docker development is never a justification for:

- `csrf.disable()`,
- wildcard production CORS,
- giving the browser an OAuth token.

If React and Spring run on different origins during development (e.g.
separate dev server and backend ports/containers), handle it with an
explicit development origin allow-list, or — often simpler — a dev proxy
that makes them appear same-origin to the browser:

```
React dev server
   -> proxy /api, /oauth2, /login, /logout
   -> Spring Boot BFF
```

This (e.g. a Vite dev server proxy) is one good option when it simplifies
the app to a same-origin model, not the only valid one — an explicit CORS
configuration scoped to the actual development origin is equally
legitimate. Either way, see `cors.md` and `csrf.md` for what must still
hold regardless of environment.

## Healthchecks

Give services in Compose reasonable healthcheck/dependency behavior (e.g.
Spring waiting on Keycloak's readiness) so local startup doesn't fail on a
race. Prefer relying on the application's own retry/backoff behavior for
transient unavailability over building a rigid, hand-maintained startup
ordering — only add explicit `depends_on`/healthcheck gating where the
application genuinely cannot recover from starting before a dependency is
ready.

## Realm export

A realm/client export can be a good way to make local development
reproducible (checked into the repo, imported on container start). It
must:

- contain no production secrets,
- contain no real user credentials,
- be safe to commit and share — every value in it should already be
  treated as public/disposable.

If an export was generated from a real environment, treat it as untrusted
until reviewed — don't assume an export is development-safe just because
it's labeled "dev".

## Version awareness

Don't hardcode a specific Keycloak (or Postgres) image tag as a permanent
rule of this skill. Before changing or adding Docker configuration, check
the versions actually pinned in the project's existing compose
file/Dockerfiles and stay consistent with them, rather than introducing a
different version as a side effect of an unrelated change.
