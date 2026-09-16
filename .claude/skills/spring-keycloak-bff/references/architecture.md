# BFF Architecture — Login & Identity Flow

## Canonical flow

```
React                         Spring BFF                      Keycloak
  |                                |                                |
  |--- GET /login ---------------->|                                |
  |                                |--- redirect (auth request) --->|
  |                                |                                |--- user authenticates
  |                                |<---- redirect w/ auth code ----|
  |                                |--- token exchange (server) --->|
  |                                |<---- access + refresh token ---|
  |                          [stores tokens server-side,            |
  |                           creates authenticated session]        |
  |<--- Set-Cookie: session -------|                                |
  |                                |                                |
  |--- GET /api/auth/me ---------->|                                |
  |<--- { user, roles } -----------|                                |
```

Key points:

- The **authorization code** is returned to Spring, never to React.
- The **token exchange** (code → access/refresh token) happens
  server-to-server, between Spring and Keycloak.
- React receives only a session cookie — never an OAuth token.
- React retrieves identity and roles through an endpoint like
  `/api/auth/me`, backed by the server-side authenticated principal, not
  by decoding a token itself.

Full invariant list: `security-invariants.md`.

## Identity endpoint

Expose one endpoint (canonically `/api/auth/me`) that returns the
authenticated user's identity and roles, derived from the server-side
`OidcUser`/`Authentication`, not from a client-supplied token. This is the
only mechanism React needs to answer "who is logged in" and "what can I
show".

## Frontend state vs. security boundary

```
frontend authentication state  → UX only (what to render)
backend authentication/authz   → the actual security boundary
```

React can and should track "am I logged in" / "what's my role" for
rendering purposes (e.g. via the `/api/auth/me` response). That state must
never be treated as authoritative by the backend, and no endpoint should
trust a role or identity claim sent from the client instead of resolving it
from the server-side session.

## Existing implementation vs. this diagram

If the codebase already implements the flow differently (e.g. a different
identity endpoint name, additional claims, a different session store):
that is not automatically wrong. Treat this diagram as the reference for
where the security boundary must sit, not as a literal template to
enforce. See `security-invariants.md` for what actually constitutes a
violation.
