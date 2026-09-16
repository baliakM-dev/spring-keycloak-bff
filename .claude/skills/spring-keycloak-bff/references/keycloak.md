# Keycloak Configuration

## Client setup

- Confidential client, client authentication enabled (the BFF holds a
  client secret server-side).
- Authorization Code flow enabled for the browser-facing BFF client.
- Redirect URIs: explicit, minimal, environment-specific — no wildcards.
- Web Origins: explicit, minimal — see the CORS distinction below.

## PKCE

Authorization Code flow remains the canonical flow for this BFF client.
Add PKCE (Proof Key for Code Exchange) as defense-in-depth on top of it
when the actual Spring Security and Keycloak versions/configuration
support it for a confidential client — check the project's real versions
before assuming an API or default (see `spring-security.md`).

PKCE does not change the BFF token-custody model: access and refresh
tokens still stay server-side, and React still never becomes the OAuth
client or holds any token — PKCE only hardens the code exchange itself.

## Direct Access Grant (Resource Owner Password Credentials)

**Disabled by default** for this BFF client. It is not a convenient
substitute for Authorization Code flow, and it is not the right tool for
CLI, service-to-service, or "trusted application" scenarios either:

- service-to-service calls that don't act on behalf of a user → use
  `client_credentials` on a dedicated service client, not Direct Access
  Grant.
- interactive CLI/device login → use a flow suited to that surface (e.g.
  Device Authorization Grant, where the platform/Keycloak version
  supports it), not Direct Access Grant.

Enabling Direct Access Grant on any client requires an explicit,
documented architectural justification specific to that client's actual
constraints — see `security-invariants.md`.

## Realm roles vs. client roles

- Realm roles apply across the whole realm; client roles are scoped to one
  client. Pick one consistently per application rather than mixing
  arbitrarily — check what the existing realm already uses before adding a
  new role.
- Whichever is used, the mapping into Spring `GrantedAuthority` must be
  explicit and consistent (see `role-mapping.md`).

## Client secret handling

Server-side configuration only (env var / secret manager), never
committed in plaintext, never present in anything shipped to the browser.

## Logout considerations

Keycloak has its own SSO session, separate from the Spring application
session. Ending one does not automatically end the other — see
`logout.md` for what to actually check/implement.

## Session lifetime

Keycloak has independent settings for session lifetime, distinct from
both OAuth token lifetime (`token-refresh.md`) and the Spring application
session (`sessions.md`):

- **SSO Session Idle** / **SSO Session Max** — realm-wide bounds on the
  Keycloak browser SSO session.
- **Client Session Idle** / **Client Session Max** — per-client overrides,
  where this BFF's client needs different bounds than the realm default.

Do not assume these should numerically match the Spring application
session timeout or the OAuth token lifetime — they answer different
questions and can legitimately differ. Align them deliberately; a
realm-wide change affects every client in that realm, so check the
existing realm configuration before changing it for this client's sake.
Reference starting point: keep these consistent with the reference Spring
application values in `sessions.md` (~30 min idle / ~8 h max) unless the
realm is shared with clients that need different values.

## Concurrent session limits

See `abuse-protection.md` for the concurrent-session-limit policy
(baseline, ownership, deny-vs-terminate tradeoff). Keycloak is the
preferred owner of this policy via **User Session Count Limiter** —
prefer a client-specific limit for this BFF over a realm-wide one.

## Web Origins vs. Spring Boot CORS

These are two different controls:

```
Keycloak Web Origins   → which origins Keycloak allows to receive
                          tokens/responses directly from it (relevant in
                          non-BFF or hybrid flows, and for CORS on
                          Keycloak's own endpoints)

Spring Boot CORS        → which origins may call the Spring BFF's own
                          API directly from browser JS
```

Configuring one does not configure the other. Review and set both
explicitly; never assume fixing a CORS error in one layer means the other
is also correct. Full detail: `cors.md`.

Never use `*` as a production Web Origin.
