# Token Refresh

## Invariant

React does not refresh Keycloak tokens. Refresh is a server-side OAuth2
Client responsibility, full stop — never build a frontend `/refresh-token`
endpoint or equivalent as a "solution" to token expiry.

## How it actually works

- The access token has a short lifetime; the refresh token (when issued)
  has a longer one.
- Spring's OAuth2 Client (`OAuth2AuthorizedClientManager` /
  `OAuth2AuthorizedClientProvider`) is responsible for detecting an
  expired access token and using the refresh token to obtain a new one,
  transparently, when the BFF next needs to call a downstream resource
  with it.
- Access/refresh tokens belong to the `OAuth2AuthorizedClient` — a
  concept separate from the Spring `SecurityContext`/HTTP session that
  represents the user's authenticated application session.

## Refresh failure vs. application session

If the refresh token itself is expired or revoked, refresh fails. What
that invalidates is the `OAuth2AuthorizedClient` (the stored OAuth
credential for that user/client pair) — it does **not**, by itself,
terminate the user's local Spring application session or
`SecurityContext`.

Whether the application then requires re-login is a policy decision, not
an automatic consequence:

- if the operation in progress needs a valid downstream OAuth
  authorization (e.g. it's about to call Keycloak or a resource server
  with the access token) and refresh just failed, the application should
  require reauthorization/re-login for that operation, per its own
  policy;
- but a refresh failure MUST NOT be wired directly into an automatic,
  global "end the local authenticated session" action — don't invalidate
  `SecurityContext`/the session as a side effect of a failed refresh
  without going through the application's own re-authentication policy.

Check how the existing implementation actually handles this before
assuming either behavior — this is a common source of both incorrect
"silent full logout" and incorrect "session says authenticated but every
downstream call fails" bugs.

## Relationship to session lifetime

The application session (what React holds) and the OAuth token lifetime
are related but not identical: a session can span multiple silent token
refreshes. Don't conflate "session expired" with "access token expired" —
only the latter should trigger a refresh attempt; the former should
require re-authentication.

## What not to do

- Don't expose the access or refresh token to React "just to check if it's
  expired" — expiry/validity is a server-side concern reflected through
  the session state (e.g. `/api/auth/me` failing or returning
  unauthenticated).
- Don't build a polling or manual "refresh now" endpoint callable by
  React — the server-side client already refreshes on demand.
