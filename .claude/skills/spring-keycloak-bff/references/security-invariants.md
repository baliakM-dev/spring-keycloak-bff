# Security Invariants — Canonical List

This is the single detailed source for BFF security invariants. Other
reference files link here instead of repeating the full list — if you're
looking for the complete rationale behind a rule, it's here.

## Token custody

- MUST: OAuth access tokens and refresh tokens are stored and used only
  server-side (Spring's authorized-client store), never sent to the
  browser in any form (response body, header, cookie payload).
- MUST NOT: React store, read, or forward an OAuth access or refresh
  token, in any storage (`localStorage`, `sessionStorage`, a JS variable
  persisted across reloads, a cookie set by frontend code).
- MUST NOT: any endpoint return a raw access/refresh token to the client
  "for convenience" (e.g. to call a third-party API directly from React).
- MUST NOT: the OAuth/Keycloak client secret exist in any frontend-shipped
  code, bundle, or environment variable exposed to the browser.

## Session ownership

- MUST: Spring Boot maintains the authenticated session; React only holds
  a session cookie, never application logic that manages token state.
- MUST: session cookie is `HttpOnly` and `Secure` in any environment
  reachable over the network (see `sessions.md`).
- SHOULD: `SameSite=Lax` or stricter for the session cookie, chosen based
  on the actual cross-site navigation needs of the app (see `sessions.md`).

## Authorization

- MUST: authorization decisions are enforced server-side, on every
  protected endpoint, independent of what the frontend renders.
- MUST NOT: a client-supplied role, claim, or header be trusted as an
  authorization source.
- MUST NOT: an admin-only or role-gated action be reachable through a
  backend endpoint that only checks `authenticated()` instead of the
  actual role/authority.

## CSRF

- MUST: CSRF protection stays enabled for unsafe (state-changing) requests
  made using cookie/session authentication.
- MUST NOT: CSRF be disabled to resolve a frontend integration problem —
  diagnose and fix the actual cause (see `csrf.md`).

## CORS

- MUST NOT: wildcard (`*`) allowed origins in a production configuration
  that also allows credentials/cookies, or on any credentialed,
  session-authenticated, or otherwise sensitive BFF endpoint.
- SHOULD: for a genuinely public, unauthenticated, non-sensitive endpoint,
  evaluate wildcard origin exposure on its own merits rather than treating
  `*` as an automatic violation — the correctness bar is what the endpoint
  exposes and to whom, not the mere presence of `*` (see `cors.md`).
- MUST: CORS configuration and Keycloak Web Origins be reviewed and
  configured independently — they are different controls (see `cors.md`).

## Client secret

MUST NOT — never commit to the repository, in any file:

- production secrets,
- shared or staging environment secrets,
- any credential protecting a real external or shared system,
- real user credentials.

Development-only exception — a credential may be committed only if all of
the following hold:

- it belongs exclusively to a disposable local development Keycloak
  instance,
- it protects no external or shared environment,
- it is explicitly labeled as a public, development-only value,
- its exposure has no security impact.

Never describe such a value as a "production secret" or treat it as one
(see `docker.md` for how this applies to local Compose setups).

MUST: regardless of environment — including development — React MUST NOT
receive the Keycloak client secret in any form.

## Role handling

- MUST: role mapping from Keycloak (realm/client roles) to Spring
  `GrantedAuthority` happens server-side and is the sole basis for backend
  authorization (see `role-mapping.md`).
- SHOULD NOT: frontend role checks be described or documented as
  "security" — they are UX affordances only.

## Refresh

- MUST: token refresh is a server-side OAuth2 Client responsibility.
- MUST NOT: React implement, call, or expose a bespoke "refresh token"
  endpoint that hands a fresh access token to the browser (see
  `token-refresh.md`).

## Logout

- MUST: local application session is invalidated and the session cookie is
  cleared on logout.
- SHOULD: OIDC/Keycloak-level (SSO) logout is triggered when the
  architecture requires ending the Keycloak session too — do not assume
  local session invalidation alone ends SSO (see `logout.md`).

## Browser storage

- MUST NOT: `localStorage`/`sessionStorage`/`IndexedDB` hold OAuth tokens,
  client secrets, or any data that would let a script reconstruct
  authority without going through the server.

## Keycloak configuration

- MUST: confidential client with client authentication enabled for the BFF
  client.
- MUST: explicit, minimal redirect URIs and Web Origins — no wildcards in
  production.
- MUST NOT: Direct Access Grant (Resource Owner Password Credentials) be
  used as a convenience alternative to the Authorization Code flow. For
  this React + Spring BFF architecture, Direct Access Grant is **disabled
  by default** — it is not the right tool for CLI, service-to-service, or
  "trusted application" scenarios either (service-to-service: use
  `client_credentials`; interactive CLI/device: use a modern device flow
  where the platform supports it — see `keycloak.md`). Enabling it on any
  client requires an explicit, documented architectural justification
  specific to that client, not a generic exception.

## Abuse & availability protection

- MUST: Keycloak Brute Force Detection is enabled for production password
  authentication.
- MUST NOT: account-lockout policy default to permanent lockout — prefer
  temporary/increasing backoff, since permanent lockout can itself be used
  as a denial-of-service against the account owner (see
  `abuse-protection.md`).
- MUST: concurrent-session policy has a single owner (Keycloak, by
  default). MUST NOT: independently duplicate the same limit in Spring
  without a documented application-level reason and explicit coordination
  with the Keycloak-side policy (see `abuse-protection.md`).
- MUST NOT: rate-limiting or audit logic trust `X-Forwarded-For` (or an
  equivalent forwarded-client-IP header) from an untrusted source — only
  from a configured, known reverse proxy (see `abuse-protection.md`).
- MUST NOT: trust forwarded scheme/protocol headers (`X-Forwarded-Proto`,
  the standardized `Forwarded` header, or equivalents) from an untrusted
  source — only from a configured, known reverse proxy. Scheme-sensitive
  decisions (`Secure` cookie behavior, HTTPS redirects, OAuth2 redirect URI
  generation, external/base URL calculation) depend on this being correct
  and must not be derived from a client-supplied header (see
  `abuse-protection.md`).

## Legitimate exceptions

Any exception to a MUST/MUST NOT above requires an explicit, documented
justification tied to the actual application's constraints — it is never
assumed or applied silently.
