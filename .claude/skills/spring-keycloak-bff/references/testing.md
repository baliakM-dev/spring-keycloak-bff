# Security Testing Catalogue

## First rule

Select only the tests relevant to the behavior that actually changed. This
catalogue is a menu, not a mandatory checklist for every change — a change
to a public, unauthenticated endpoint doesn't need CSRF or role tests.

## Authentication

- anonymous request to a public endpoint → allowed
- anonymous request to a protected endpoint → denied
- authenticated request to a protected endpoint → allowed

## Authorization

- USER calling a USER-scoped resource → allowed
- USER calling an ADMIN-scoped resource → forbidden
- ADMIN calling an ADMIN-scoped resource → allowed

## CSRF

- unsafe authenticated request without a CSRF token → rejected
- same request with a valid CSRF token → accepted

## Session

- an authenticated session remains usable across normal
  requests/navigation/reload
- an expired or explicitly invalidated session no longer authenticates

## Logout

- logout invalidates the local session
- a subsequent request to a protected endpoint is no longer authenticated
- where relevant: Keycloak/OIDC-level logout behavior (see `logout.md`)

## Session limit (conditional — see `abuse-protection.md`)

Only for changes touching concurrent-session policy:

- sessions below the configured limit → allowed
- a session beyond the configured limit (e.g. a 4th when the limit is 3)
  → denied, per the configured policy (deny-new vs. terminate-oldest)
- existing sessions remain valid under the `Deny new session` policy

## Brute force (conditional — see `abuse-protection.md`)

Only for changes touching login-failure handling:

- repeated failed logins trigger the configured temporary protection
- successful login behavior follows the configured reset policy
- the error returned does not expose lockout state or account existence

## Rate limiting (conditional — see `abuse-protection.md`)

Only for changes adding or touching a rate limit:

- a request below the limit succeeds
- an excess request returns `429`
- the rate limit is scoped according to policy (per-account/per-IP/global
  as designed, not accidentally global)
- different users/IPs are not accidentally tied to the same bucket

## Request limits (conditional — see `abuse-protection.md`)

Only for changes touching input/resource bounds:

- an oversized payload is rejected
- an invalid oversized pagination/batch parameter is rejected
- valid input within bounds is unaffected

## Authorization abuse (conditional)

- a hidden/absent frontend control does not, by itself, allow bypassing
  backend authorization (a direct request to the underlying endpoint is
  still checked server-side — see `role-mapping.md`)

## Secrets (conditional)

Static/repository check for accidental commits of:

- access tokens, refresh tokens,
- client secrets,
- suspicious production-looking credentials.

## Frontend

- verify application code contains no OAuth access/refresh token storage
  or refresh logic (a grep for token handling in frontend source is often
  sufficient — this is a code-absence check, not a runtime test)

## Integration / E2E

Choose the smallest layer that actually proves the behavior:

- Spring Security test slice (`@WebMvcTest` + security test support) — for
  authorization/CSRF rules on a single controller/filter chain.
- Full Spring Boot integration test — when the behavior depends on real
  wiring (filter chain, session, multiple beans) that a slice test can't
  represent.
- Testcontainers (e.g. a real Keycloak container) — justified when the
  behavior under test is the actual OAuth2/OIDC exchange or
  Keycloak-specific configuration, not for testing logic that doesn't
  touch Keycloak.
- Playwright/E2E — justified for the actual browser-cookie/CSRF/redirect
  round trip (e.g. login flow, logout flow), not for backend-only logic
  that unit/integration tests already cover.

Don't require all four layers for a trivial, isolated change.
