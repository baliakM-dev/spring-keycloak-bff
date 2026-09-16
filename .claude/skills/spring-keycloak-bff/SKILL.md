---
name: spring-keycloak-bff
description: Opinionated reference for implementing and reviewing secure Spring Boot + React + Keycloak Backend-for-Frontend (BFF) applications — OAuth2/OIDC Authorization Code flow, server-side sessions, CSRF, CORS, authorization, logout, token refresh, and security testing. Load when a task touches authentication, authorization, session/cookie handling, CSRF, CORS, Keycloak configuration, or security testing in a Spring Boot + React BFF codebase.
---

# Spring + Keycloak BFF Skill

## Purpose

Single source of truth for implementing and reviewing a Spring Boot + React
+ Keycloak Backend-for-Frontend application safely and consistently. This
file is a router and decision layer, not an encyclopedia — load only the
`references/` file relevant to the current task.

## BFF model

```
Browser / React
       |
       | application session cookie + CSRF protection
       v
Spring Boot BFF
       |
       | OAuth2 / OIDC — Authorization Code flow
       v
Keycloak
```

OAuth access and refresh tokens remain server-side. React never owns them —
it authenticates against Spring using the application session, and gets
identity/roles from `/api/auth/me` (or equivalent). Full flow:
`references/architecture.md`.

## Non-negotiable invariants (condensed)

Full detail, classification (MUST/MUST NOT/SHOULD), and rationale:
`references/security-invariants.md`. Short version:

- React MUST NOT store, own, decode-as-authorization, or refresh OAuth
  tokens, and MUST NOT contain the Keycloak client secret.
- Spring Boot MUST be the OAuth2/OIDC client, hold tokens server-side,
  enforce authorization server-side, and keep CSRF protection enabled for
  cookie/session-authenticated unsafe requests.
- Keycloak client config MUST avoid wildcard production origins; Direct
  Access Grant is disabled by default and any exception needs explicit
  architectural justification (see `references/keycloak.md`).
- Frontend role visibility is UX only, never a security boundary.

## Routing — load only what the task needs

| Task touches | Read |
|---|---|
| Overall login/session flow, `/api/auth/me` | `references/architecture.md` |
| Canonical invariant list / review checklist | `references/security-invariants.md` |
| `SecurityFilterChain`, `oauth2Login`/`oauth2Client` wiring | `references/spring-security.md` |
| Keycloak realm/client/roles config | `references/keycloak.md` |
| CSRF errors, CSRF implementation | `references/csrf.md` |
| Session/cookie behavior, scaling | `references/sessions.md` |
| Logout, SSO session behavior | `references/logout.md` |
| Token expiry, refresh behavior | `references/token-refresh.md` |
| Role → authority mapping, endpoint authorization | `references/role-mapping.md` |
| CORS configuration or CORS/CSRF confusion | `references/cors.md` |
| Choosing/writing security tests | `references/testing.md` |
| Docker Compose, local Keycloak, realm import, container networking, dev proxy, environment config, local startup | `references/docker.md` |
| Brute force, credential stuffing, login abuse, rate limiting, session limits, DoS/DDoS, WAF, API abuse | `references/abuse-protection.md` |

Do not read references unrelated to the current task.

## Security review levels

Scale the review to the change — don't turn every task into a full audit.

- **LOCAL** — a small, isolated endpoint/component change. Review only the
  directly affected controls.
- **SECURITY-SENSITIVE** — touches login, session, Keycloak, OAuth,
  authorization, CSRF, CORS, password reset, or role changes. Load the
  relevant security reference(s) above and run the relevant tests
  (`references/testing.md`).
- **INFRA / EDGE** — touches proxy, Docker, WAF, deployment, rate
  limiting, DDoS, or traffic management. Load `references/abuse-protection.md`,
  `references/docker.md`, and any relevant CORS/session references.

Pick the level, then load only what that level implies — not every
security reference for every task.

## Workflow

1. Understand the task.
2. Identify whether it touches an authentication/authorization/security
   boundary. If not, most of this skill doesn't apply — proceed normally.
3. Load only the reference file(s) relevant to that boundary.
4. Inspect the existing implementation before proposing changes — this
   codebase's actual architecture wins over the reference diagram (see
   "Existing implementation vs. this diagram" in `architecture.md`).
5. Check the project's actual Spring Boot / Spring Security / Keycloak
   versions before selecting APIs (`spring-security.md`, `keycloak.md`).
   Don't assume a version.
6. Determine the smallest safe change that satisfies the task.
7. Validate the planned change against `references/security-invariants.md`.
8. Implement or review, according to the calling agent's role.
9. Select only the tests relevant to the changed behavior
   (`references/testing.md`).
10. Validate the result (build/tests as applicable).
11. Report actual unresolved risks — don't invent findings, and don't
    suppress a real one to keep the report short.

## Decision rules

- A deviation from the reference diagram is not a defect by itself; it's a
  defect only if it violates an invariant, creates a security risk, or is a
  demonstrable architectural bug.
- CORS and CSRF are different controls solving different problems — never
  resolve one by disabling the other.
- Frontend hiding a control is not authorization. Any role-gated UI element
  must have a matching server-side check.
- Refresh, token custody, and CSRF are never "fixed" by moving them into
  React.
- Don't propose dependency upgrades, infra changes, or version migrations
  as a side effect of a security fix — flag them, don't perform them,
  unless the task asked for it.

## Final validation

Before calling a task done: confirm no OAuth token left the server side,
CSRF protection is intact for unsafe cookie-authenticated requests,
authorization is enforced server-side (not just hidden in the UI), and the
tests actually run cover the behavior that changed.
