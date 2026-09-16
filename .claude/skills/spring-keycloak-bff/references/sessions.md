# Sessions

## Cookie attributes

- `HttpOnly`: always, so JS cannot read the session cookie.
- `Secure`: required whenever the app is served over HTTPS (i.e.
  effectively always outside local plain-HTTP dev).
- `SameSite`: choose based on actual cross-site navigation needs — `Lax`
  is a reasonable default for a same-site BFF+SPA deployment; only relax
  it if there's a real cross-site requirement, and understand the CSRF
  implications before doing so.

## Environment differences

Local development over plain HTTP typically can't use `Secure` cookies —
don't carry a dev-only relaxation into a production profile. Keep
environment-specific cookie/session settings in environment-specific
configuration, not a single hardcoded value.

## Session fixation

Spring Security's default session management already changes the session
ID on authentication — don't disable or bypass this.

## Timeout / expiry

Three different lifetime concepts apply here, and they are not the same
duration:

```
OAuth token lifetime          -> how long an access/refresh token is
                                  valid at Keycloak (see token-refresh.md)
Keycloak SSO session lifetime -> how long the user's Keycloak login
                                  itself stays valid (see keycloak.md)
Spring application session
lifetime                       -> how long the BFF's own HTTP session
                                  (what the browser cookie represents)
                                  stays valid
```

A session can reasonably outlive a single access token (refreshed
transparently) — don't conflate "session expired" with "access token
expired" (see `token-refresh.md`).

For the Spring application session, distinguish:

- **Idle timeout** — how long an inactive session stays valid
  (`server.servlet.session.timeout` or equivalent).
- **Maximum session lifetime** — an absolute cap regardless of activity,
  if the application needs one (not always required — decide based on the
  application's actual sensitivity).

Reference starting values for this project — **reference defaults,
application policy must override when requirements differ**, and more
sensitive applications should use shorter values:

- application idle timeout: ~30 minutes
- maximum session lifetime: ~8 hours (a working day)

See `keycloak.md` for the matching Keycloak-side SSO/client session
settings, which should be considered together with these, not set
independently.

## Concurrent sessions

Limiting how many sessions a user may hold at once is a distinct concern
from timeout/expiry above — see `abuse-protection.md` for the policy
(baseline limit, ownership, deny-vs-terminate tradeoff). Don't implement
an independent Spring-side concurrent-session limit without first
checking that section — uncoordinated, duplicate enforcement between
Keycloak and Spring is worse than a single clear owner.

## Scaling

Don't introduce a distributed session store (e.g. Redis + Spring Session)
unless the application actually runs multiple instances that need shared
session state. For a single-instance deployment, the default in-memory
session store is correct — adding infrastructure for a problem that
doesn't exist yet is a scope violation.
