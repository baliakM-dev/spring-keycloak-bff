# Logout

## Two separate sessions

```
local application logout   -> ends the Spring session (this app only)
OIDC / Keycloak logout      -> ends the Keycloak SSO session (all clients
                                sharing that SSO session)
```

Invalidating only the local Spring session does **not** automatically end
the Keycloak SSO session — a user could still be silently re-authenticated
against Keycloak by another client, or by this app itself, without seeing
a login prompt. Whether that matters depends on the application's actual
SSO requirements — check before assuming either behavior is "the bug".

## What logout should do

- Invalidate the local server-side session.
- Clear the session cookie.
- Where the architecture requires ending SSO too: redirect through
  Keycloak's end-session/logout endpoint, with a valid post-logout
  redirect URI configured on the client.
- Apply the same CSRF requirements to the logout request as to any other
  unsafe, session-authenticated request, unless the existing
  `SecurityFilterChain` deliberately exempts it.

## What to test

See `testing.md` — minimum: logout invalidates the local session, and a
subsequent request to a protected endpoint is no longer authenticated.
