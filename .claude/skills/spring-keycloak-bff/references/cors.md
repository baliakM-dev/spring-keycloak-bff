# CORS

## Two independent layers

```
Spring Boot CORS    -> controls which origins may call the Spring BFF's
                        own API endpoints from browser JS
Keycloak Web Origins -> controls which origins Keycloak itself allows
                        (see keycloak.md)
```

Fixing or reviewing one does not fix or review the other. Both need
explicit configuration.

## Spring Boot CORS

Cover, when reviewing or configuring:

- Allowed origins: explicit list of known frontend origin(s) — never `*`
  when credentials/cookies are allowed (browsers reject the combination
  outright, and even where they wouldn't, it defeats the purpose).
- `allowCredentials`: required for cookie-based auth to work cross-origin;
  only enable it alongside an explicit, non-wildcard origin list.
- Methods/headers: scope to what the frontend actually needs, not `*` by
  default.
- Deployment topology: if React and the BFF are served same-origin (e.g.
  the BFF serves the built frontend, or a reverse proxy unifies them),
  CORS configuration can often be minimal or unnecessary — prefer this
  topology where practical, since it also sidesteps a class of
  CORS/cookie edge cases.

## Invariant

A wildcard (`*`) `Access-Control-Allow-Origin` is not automatically a
HIGH/CRITICAL finding for every HTTP resource — severity depends on what
the endpoint actually exposes:

- For a credentialed, session-authenticated, or otherwise sensitive BFF
  endpoint, wildcard origin is unacceptable — this applies regardless of
  how the request to add it is phrased (e.g. "just to make dev work"
  applied to a shared/production config).
- For a genuinely public, unauthenticated, non-sensitive endpoint (e.g. a
  public health check or static public content with no credentials
  involved), a wildcard origin is not automatically wrong — assess the
  actual exposure of that specific endpoint rather than flagging `*` on
  sight.

Don't manufacture a false-positive finding purely because `*` appears
somewhere in a CORS configuration — check what the endpoint returns and
whether credentials are involved before calling it a violation.

## CORS is not CSRF

```
CORS  -> which origins may read the response of a cross-origin request
CSRF  -> whether a request can be forged and acted upon regardless of
         whether the attacker can read the response
```

A CORS error and a CSRF rejection look similar ("my request failed") but
have different causes and different fixes. Disabling either to make the
other's symptom go away is always wrong — diagnose which one is actually
failing first (see `csrf.md` for the CSRF diagnostic checklist).
