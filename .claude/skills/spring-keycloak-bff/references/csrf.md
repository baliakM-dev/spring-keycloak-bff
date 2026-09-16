# CSRF

## Why it applies here

```
browser automatically sends the authentication cookie on every request
  -> any unsafe (state-changing) request is vulnerable to CSRF
  -> unsafe requests must carry a CSRF token the browser can't forge
```

This applies specifically because authentication is cookie/session-based.
It would not apply the same way to a pure bearer-token API — which is
exactly why this architecture keeps CSRF protection on rather than
treating it as legacy.

## Rules

- GET/HEAD/OPTIONS: safe, no CSRF token required.
- POST/PUT/PATCH/DELETE: unsafe, must carry a valid CSRF token when the
  caller is authenticated via the session cookie.
- React obtains the CSRF token from wherever Spring Security exposes it
  (e.g. a readable cookie via `CookieCsrfTokenRepository`, or a value
  returned by an endpoint) and sends it back on the expected header on
  every unsafe request.
- Login/logout: treat according to the existing `SecurityFilterChain`
  configuration — check whether they're already CSRF-exempt or protected
  before changing either.

## Invariant

**Do not solve a frontend integration problem by disabling CSRF.** If a
request "doesn't work" because of CSRF, the fix is almost always on the
React side (token not read, not sent, wrong header name) or a
cookie/origin misconfiguration — not removing the protection.

## Diagnosing a CSRF failure

Work through in order:

1. Is a CSRF token actually being generated/issued by Spring for this
   session?
2. Can the browser access the token in the form React expects (cookie
   readable, or fetched from an endpoint)?
3. Is React sending it on the header Spring Security expects?
4. Is the session cookie itself included on the request (credentials
   mode, same-site/proxy setup)?
5. Is the reverse proxy / dev server preserving cookies and headers
   correctly between origins?
6. Does the token Spring receives match the token tied to the current
   session (not stale, not from a different session)?
7. Is the session still valid at all, independent of CSRF (a 401
   masquerading as a CSRF failure)?

## Testing

See `testing.md` — minimum: an unsafe authenticated request without a
CSRF token is rejected, and the same request with a valid token is
accepted.
