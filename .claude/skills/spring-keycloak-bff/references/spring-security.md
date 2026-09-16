# Spring Security Implementation Patterns

Prefer framework-native Spring Security mechanisms. Before applying any
pattern below, check the project's actual Spring Boot / Spring Security
version and use APIs appropriate to it — don't assume a version.

## Building blocks

- `SecurityFilterChain` bean — defines the security rules for the BFF.
  Unsafe methods require CSRF (see `csrf.md`); protected endpoints require
  authentication; role-gated endpoints require the specific
  authority/role, not just `authenticated()`.
- `oauth2Login()` — drives the Authorization Code flow against Keycloak
  and establishes the authenticated session on success.
- `oauth2Client()` / `OAuth2AuthorizedClientManager` — owns server-side
  token storage and refresh (see `token-refresh.md`).
- OIDC user info — the `OidcUser`/`OAuth2User` principal is the source for
  the `/api/auth/me` response; map it to a small DTO rather than exposing
  the raw principal.
- Endpoint authorization — `authorizeHttpRequests` with explicit
  `hasRole`/`hasAuthority` for role-gated paths, `permitAll` only for
  genuinely public endpoints, `authenticated()` for "any logged-in user".
- Exception handling — unauthenticated access to a protected API endpoint
  should return 401, not redirect-to-login, when the caller is React
  fetching JSON (distinguish API endpoints from browser-navigated ones).

## What not to build

Spring Security already solves these — reimplementing them is both a scope
violation and a likely security regression:

- custom token parsing/validation in application code,
- a custom OAuth2 Authorization Code exchange implementation,
- a custom `/refresh-token` endpoint for React (see `token-refresh.md`),
- manual session/auth-state tracking that duplicates what
  `SecurityContext`/the session already provides.

If the existing codebase already does one of these, treat it as a finding
against `security-invariants.md`, not as a pattern to imitate.
