# Abuse Protection — Brute Force, Rate Limiting, DoS/DDoS, Resource Exhaustion

Load this file only for tasks touching: brute force, login attempts,
credential stuffing, bot abuse, rate limiting, session limits, DoS/DDoS,
traffic spikes, API abuse, expensive endpoints, WAF, or reverse-proxy
protection. Routine endpoint work (e.g. adding `/api/auth/me`) does not
need this file — see `SKILL.md` routing.

## Concurrent session limits

Baseline for this reference project: **3 concurrent sessions per user,
per BFF client**. Configurable — a starting point, not a fixed constant;
adjust per the application's actual requirements.

Ownership: **Keycloak is the preferred owner** of concurrent-session
policy for SSO/identity, via the **User Session Count Limiter** (check
what's available in the project's actual Keycloak version before
configuring).

- Distinguish **realm-wide** limits (apply to every client in the realm)
  from **client-specific** limits (apply only to this BFF's client). For
  this BFF, prefer a **client-specific** limit of 3, so other clients
  sharing the realm aren't affected by a policy chosen for this
  application.
- Default behavior on limit reached: **deny new session** (security-first
  default — refuses the newest login attempt once the limit is hit).
- Alternative: **terminate oldest session** (lets the new login in, ends
  the least-recently-used one). Document this as an option, not as
  universally better — it removes login friction but means an active
  session can be silently ended by another login, which has its own
  abuse potential if credentials are shared or compromised.

**Do not double-enforce.** Decide ownership first. Keycloak owns the
identity/SSO concurrent-session policy by default. Spring MUST NOT
independently implement its own, separate concurrent-session limit unless
there is a concrete, application-level reason Keycloak's enforcement
doesn't cover. If Spring does implement one, it must be deliberately
coordinated with the Keycloak-side policy, not a second, independently
tuned copy of "3" that can disagree with Keycloak's count.

See `sessions.md` for session timeout/lifetime, a distinct concern from
the concurrent-session count here.

## Brute-force protection (Keycloak)

**Brute Force Detection must be enabled for production password
authentication** in Keycloak.

Do not default to a permanent-lockout mechanism. An attacker can abuse
account lockout as a denial-of-service against the victim (lock a known
username out of their own account by deliberately failing logins).
Prefer:

- temporary lockout / increasing backoff over permanent lockout,
- a generic login error (don't reveal whether lockout, wrong password, or
  unknown account was the cause),
- monitoring/alerting (see "Logging and monitoring" below) over silent
  failure,
- edge-level rate limiting as an additional layer (see "Rate limiting").

Reference baseline for this project — **these are reference starting
values, not universal security constants**; production values must be set
from the application's actual user population, threat model, telemetry,
and false-positive tolerance:

- Max Login Failures: 5
- Permanent lockout: **off** by default
- Increasing temporary wait/backoff between attempts
- Maximum temporary wait: ~15 minutes
- Failure counter reset after a reasonable quiet period

## Credential stuffing protection

Per-account brute-force protection alone is not enough — it doesn't stop
an attacker trying one password across many accounts, or many accounts
from one source. Use two independent layers:

- **Per-account protection** — keyed on `username`/account identifier.
  Protects one account from a distributed attack (many sources, one
  target account).
- **Per-source protection** — keyed on `IP` / trusted client source (see
  "Proxy/IP trust" below for what "trusted" means). Protects the system
  from one source hammering many accounts.

Do not use a single combined bucket like `IP + username` as the only
control — it catches neither scenario cleanly. Run both layers
independently.

Do not leak, in any error response:

- whether the limit hit was per-account or per-source,
- the exact number of attempts remaining,
- whether the account exists at all.

Use generic responses (see "User enumeration" below).

## Authentication endpoint protection — where it actually belongs

React never sends username/password to the Spring BFF — credential
authentication happens at Keycloak (see `architecture.md`). Because of
that, primary brute-force/login protection belongs at:

1. **Keycloak** (Brute Force Detection, above),
2. the **edge/reverse proxy** in front of Keycloak,
3. a **WAF/anti-bot** layer, where one exists.

Do not implement a login-attempt counter in a Spring controller that
never actually sees the credentials — it protects nothing, since the
credential exchange doesn't go through it. Spring's own `/login` /
OAuth2-redirect endpoint can reasonably have its own abuse limit (e.g.
against excessive session-start requests), but that is a supplementary
control, not a substitute for protecting Keycloak's actual login
endpoint.

## Rate limiting

Use layered rate limiting rather than relying on a single point:

**Edge / reverse proxy** — the preferred place to absorb large-volume
abuse: CDN/WAF, reverse proxy, ingress/load balancer. High-volume traffic
should be stopped before it reaches the application.

**Application-level** — use for operations where a generic edge rule
isn't precise enough, e.g.:

- expensive API operations,
- email sending,
- password-reset / verification-request operations,
- exports, report generation,
- search,
- calls to external APIs,
- public API endpoints without another gate in front of them.

Prefer **token bucket** or **sliding window** algorithms over a naive
fixed window where burst behavior matters (a fixed window allows a 2x
burst at the window boundary) — pick based on what the specific endpoint
actually needs to tolerate.

Rate limits MUST be configurable (externalized configuration), not magic
numbers hardcoded in Java source.

## HTTP 429

Use `429 Too Many Requests` where it's semantically correct for
application/edge rate limiting.

Do not leak in the response:

- internal rate-limit bucket details (remaining quota internals, bucket
  keys),
- whether limiting was per-account, per-IP, or global,
- backend topology.

## DDoS — layered defense

**Spring Boot alone is not a DDoS protection system.** There is no
application-level setting that guarantees protection against all DDoS
attacks — describe this capability as **"DDoS resilience"** or **"layered
DoS/DDoS protection"**, never as **"DDoS-proof"**.

Large volumetric attacks must be stopped upstream of the application:

```
Internet
   v
CDN / DDoS protection / WAF
   v
reverse proxy / load balancer
   v
Spring Boot BFF
   v
database / downstream services
```

And for Keycloak specifically:

```
Internet
   v
WAF / reverse proxy
   v
Keycloak
```

Do not claim that an in-process limiter (e.g. Bucket4j, a Spring
`Filter`) provides volumetric DDoS protection — it can shed excess
*application-level* load once traffic reaches the JVM, but a large
volumetric attack must be absorbed before that point.

## Reverse proxy / edge controls

When a task touches proxy/edge configuration, consider:

- connection limits, request-rate limits, burst limits,
- connection timeout, read timeout, write timeout, header timeout,
- maximum body size, maximum upload size, header-size limits,
- maximum concurrent connections,
- slow-client / slowloris mitigations.

Exact configuration syntax depends on the actual infrastructure (nginx,
Traefik, a cloud load balancer, a CDN/WAF) — do not hardcode guidance for
one specific provider. On an implementation task, first identify what
infrastructure the project actually uses before proposing configuration.

## Spring Boot resource-exhaustion protection

Cover, where relevant:

- maximum request header size,
- maximum request body/form size,
- multipart upload limits,
- maximum number of parameters,
- connection timeout,
- keep-alive behavior,
- server thread/connection limits, where applicable to the embedded
  server in use.

Set these to the **smallest value that safely supports legitimate
application traffic** — don't pick arbitrarily low values without
knowing what the application actually needs to accept, and don't tune
these without knowing the actual deployment (embedded server, container
resource limits, expected payload sizes).

## API input/resource limits

Any endpoint that can allocate significant resources based on
client-supplied input needs a bounded input, e.g.:

- pagination max page size,
- export row limits,
- upload size,
- collection size, batch size,
- search query complexity,
- report/date ranges,
- downstream result limits.

Never allow an effectively unbounded client-controlled size, such as
`size=Integer.MAX_VALUE` or an equivalent unclamped parameter — clamp
server-side regardless of what the client requests.

## Expensive operations

For CPU/database/external-service-expensive endpoints, consider — based
on the actual use case, not automatically:

rate limit, concurrency limit, queue, async processing, caching,
backpressure, bulkhead, timeout, circuit breaker.

Apply only the patterns that have a concrete justification for that
specific endpoint; don't add all of them by default.

## Outbound calls

Any external API call the BFF makes must have:

- a connect timeout,
- a response/read timeout,
- bounded retry (a fixed maximum, not unbounded),
- backoff between retries,
- retry limited to failures that are actually safe to retry (idempotent
  operations / transient errors), not blindly retrying everything.

Never allow an unbounded retry loop — under load, that turns a downstream
outage into a retry storm that can take down the BFF itself or amplify
load on the already-struggling downstream service.

## Security headers

For production, review:

- HSTS (when served over HTTPS),
- Content-Security-Policy,
- X-Content-Type-Options,
- Referrer-Policy,
- Permissions-Policy (scoped to what the application actually uses),
- frame protection / `frame-ancestors`,
- `Cache-Control` on responses containing sensitive data.

Check what Spring Security and the reverse proxy already set by default
before adding headers — don't add legacy or redundant headers just to
lengthen a checklist.

## Admin endpoints

Keycloak's Admin Console/Admin API and Spring's management endpoints
(Actuator, health, metrics, any debug endpoint) must not be unnecessarily
publicly exposed. Prefer, in order:

- private/internal network only,
- authentication required,
- explicit, deliberate exposure only for what genuinely needs to be
  public (e.g. a plain liveness check with no sensitive data).

Never expose sensitive Actuator endpoints via a wildcard/catch-all
configuration.

## User enumeration

Authentication and account-recovery flows must not reveal, through
response content, timing, or status code, whether:

- an account exists,
- an account does not exist,
- an account is temporarily locked,

unless a flow has been explicitly, deliberately designed to reveal this
safely. Prefer generic responses ("if an account exists, we've sent
instructions") over precise ones.

## Password-reset / email abuse

Rate limit:

- password reset requests,
- resend-verification-email requests,
- invitation emails,
- OTP resend,
- any other user-triggered outbound email/contact action.

Protect with per-account, per-IP/source, and (where warranted) a global
emergency limit. A public endpoint that triggers an email must never
allow unbounded email bombing against a target address.

## MFA

Recommend MFA/OTP/WebAuthn for admin or otherwise privileged accounts,
and step-up authentication for sensitive operations based on the
application's actual risk model.

MFA does not need to be mandatory for every ordinary/demo user session —
but privileged-account protection must be explicitly addressed, not
silently skipped.

## Logging and monitoring

Security controls without telemetry aren't verifiable. Log:

- login failures,
- lockouts (temporary or permanent),
- rate-limit hits,
- suspicious request-volume spikes,
- forbidden/denied authorization attempts,
- abnormal session-creation patterns,
- Keycloak security events.

Never log:

- passwords,
- access tokens, refresh tokens,
- client secrets,
- session IDs,
- CSRF tokens/secrets,
- sensitive PII without a specific, justified reason.

Alert on: sudden login-failure spikes, 429 spikes, 401/403 spikes,
session-creation spikes, general traffic spikes, resource saturation.

## Availability protection

- Distinguish liveness from readiness checks.
- Bound database connection pools and any executor/queue used for async
  work — unbounded queues turn overload into an out-of-memory failure
  instead of graceful degradation.
- Prefer graceful overload handling (reject/shed load with a clear
  response) over silently queueing everything.
- Fail fast where that's the safer behavior for the specific dependency.
- Use circuit breaking / downstream isolation where a downstream failure
  could otherwise cascade.

The health endpoint itself must not perform an expensive operation on
every request — a liveness/readiness check should be cheap.

## Proxy / IP / scheme trust

Rate limiting or auditing "by IP" is only meaningful if the application
correctly identifies the real client IP. **Never blindly trust
`X-Forwarded-For` (or a similar forwarded-client-IP header) from an
arbitrary client** — an attacker can set this header directly and spoof
any IP, defeating both rate limiting and audit logging.

Only trust forwarded-IP headers when they come from a known, configured
reverse proxy/load balancer (the application is configured to trust a
specific set of upstream proxies and takes the client IP from the correct
position in the header chain) — not from any request that happens to
include the header.

The same applies to forwarded scheme/protocol headers (`X-Forwarded-Proto`,
the standardized `Forwarded` header, or equivalents): never trust them from
an arbitrary client. In a BFF deployed behind TLS-terminating
infrastructure, the app's belief about whether the original request was
HTTPS drives `Secure` cookie behavior, HTTPS redirects, OAuth2 redirect URI
generation, and external/base URL calculation — a spoofed or misconfigured
scheme header can silently downgrade any of these. Trust it only from the
same configured, known reverse proxy set as above. Exact configuration
(e.g. `server.forward-headers-strategy` and trusted-proxy settings) depends
on the actual deployment topology — inspect it before configuring, rather
than assuming one proxy/provider's defaults.
