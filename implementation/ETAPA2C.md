# Stage 2C — SPA CSRF integration and full application logout

Implement ONLY Stage 2C of `spring-keycloak-bff`. Require working Stage 2A login and Stage 2B session state, `/api/auth/me`, home redirect and protected route. Verify the actual baseline first.

## Goal and scope

Add a functioning Logout button and framework-supported CSRF integration. Logout must invalidate the local Spring session, initiate logout of the corresponding Keycloak SSO session, and return the browser to the public frontend home page.

Do NOT implement roles, business mutations, token refresh customization, global logout across devices, administrative session revocation, back-channel/front-channel logout receivers, session concurrency limits, registration, password reset or MFA. Do not build a custom OAuth logout protocol.

## 1. Architecture review focus

Inspect actual Spring Security/Keycloak versions, CSRF defaults, deferred token handling, BREACH protection, local session cleanup, OAuth2AuthorizedClient storage and OIDC end-session metadata. Decide the exact framework configuration before implementation.

The manually configured Stage 2A ClientRegistration may not contain discovery metadata such as `end_session_endpoint`. Verify what the supported OIDC logout handler needs and supply verified provider metadata if required. Preserve the canonical issuer and use a browser-reachable Keycloak logout URL, not a Docker-only service hostname.

## 2. CSRF contract

Prefer a small same-origin `GET /api/auth/csrf` endpoint which resolves Spring's CSRF token and returns only:

```json
{
  "token": "CSRF value",
  "headerName": "framework-selected header name",
  "parameterName": "framework-selected form parameter name"
}
```

These placeholders describe the API schema; never print actual values in reports. A CSRF token is distinct from an OAuth token and must be available to the frontend for this integration. Keep the application session cookie HttpOnly.

Use the framework's session-backed CSRF repository unless the actual repository architecture warrants an alternative approved by the architect. Preserve supported deferred-token and BREACH handling for the installed version; do not copy version-incompatible configuration.

The bootstrap endpoint may be anonymous to allow fresh CSRF initialization after logout. Return `Cache-Control: no-store`; do not expose it cross-origin through permissive CORS. Resolving a token may establish an anonymous session; this is not an authenticated session.

Use a small shared same-origin request helper for unsafe application requests, attaching the correct CSRF header. Keep safe GET requests free of mutations. Refresh token state after login/logout boundaries; do not indefinitely reuse a pre-authentication CSRF token. Do not blindly retry unsafe requests after a 403 or conflate every 403 with CSRF failure. Do not add a fake business mutation endpoint solely to demonstrate CSRF.

## 3. Logout implementation

Use Spring Security's supported `POST /logout` processing and servlet OIDC client-initiated logout support appropriate to the installed version, e.g. `OidcClientInitiatedLogoutSuccessHandler` where supported.

The React Logout button should fetch a fresh CSRF token and submit a same-origin top-level HTML form POST to `/logout`, including the framework CSRF parameter. This lets the browser follow the BFF-to-Keycloak redirects. Do not fetch the entire cross-origin logout redirect chain as an AJAX API request. Show an actionable error if preparation fails; prevent duplicate submissions.

The backend must invalidate the local authenticated session and clear its security context through supported logout mechanisms. Inspect and correctly handle the actual server-side authorized-client lifecycle and session-cookie path. Do not claim globally revoked tokens or sessions merely because local logout succeeded.

Configure an exact, trusted post-logout redirect URI to frontend `/`, including Keycloak's allowed post-logout URI settings. Do not accept arbitrary external destinations or introduce wildcard redirects. Preserve working login callback and PKCE settings.

GET `/logout` must not silently terminate the local session. If framework defaults show a confirmation page, it must still require a CSRF-protected POST to perform logout. Missing or invalid CSRF must not log the user out.

If framework RP-initiated logout uses `id_token_hint`, keep its construction in the BFF. React must not receive the ID token through an API or build the logout URL. Document honestly that protocol redirects may carry this hint through the browser to Keycloak; do not claim that an ID token never traverses the browser at the protocol level. Redact sensitive redirect query values from evidence and logs.

## 4. UI and failure behavior

Show Logout only for an authenticated user. After the completed navigation to home, bootstrap `/api/auth/me` again and show the anonymous UI. Protected data must not reappear from stale frontend state when navigating Back or refocusing the app; use Stage 2B revalidation and appropriate private-response caching controls.

If Keycloak logout fails or Keycloak is unavailable, local session invalidation must still have occurred. Do not claim SSO logout succeeded in that case. Document the observable failure and avoid an automatic login loop. A new anonymous CSRF session cookie after logout is acceptable if `/api/auth/me` remains 401.

## 5. Tests and browser verification

Backend tests must verify CSRF bootstrap, no-store, missing/invalid-token POST logout rejection, valid-token local logout, old-session rejection, and configured OIDC redirect destinations. Test CSRF behavior with the actual filter chain, not only a mocked controller. Verify that GET logout does not silently invalidate authentication.

Frontend tests must cover authenticated Logout visibility, CSRF acquisition and correct form submission, preparation errors, duplicate-action prevention and the signed-out state after return. Test the shared unsafe-request helper without adding business APIs.

Real browser checks:

1. Login, open the protected page, then click Logout.
2. Observe browser navigation through the BFF and Keycloak and return to home.
3. Confirm `/api/auth/me` is 401 and protected APIs reject the old session. Do not copy cookie values into evidence.
4. Confirm Back/refresh does not expose usable protected content and API access remains denied.
5. Click Login again and verify the previous Keycloak SSO session no longer silently authenticates the user. Use a clean disposable account/browser context without an external upstream SSO provider, and distinguish password-manager autofill from SSO.
6. Verify actual missing/invalid-CSRF logout attempts cannot terminate the active local session.
7. Login and logout a second time to detect stale CSRF state.
8. Check storage, session-cookie properties and network behavior without reporting sensitive values.

## Definition of Done

- [ ] Framework-backed CSRF integration works without disabling protection.
- [ ] Logout requires POST with valid CSRF and invalidates local authentication.
- [ ] Real Keycloak SSO logout and trusted return to home are verified.
- [ ] Old-session requests and stale UI cannot grant protected access.
- [ ] Repeated login/logout works with fresh CSRF state.
- [ ] No OAuth token management or client secret was introduced into React.
- [ ] Automated checks, browser validation and final review pass.
- [ ] README documents CSRF contract, logout scope, failure behavior and protocol-hint distinction.

## Official references

Check documentation matching the installed versions:

- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Security logout](https://docs.spring.io/spring-security/reference/servlet/authentication/logout.html)
- [Spring Security OIDC logout](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/logout.html)
- [Keycloak OIDC endpoints](https://www.keycloak.org/securing-apps/oidc-layers)


## Working rules and sequential workflow

Use the existing `bff-security-architect`, `bff-implementer`, `spring-keycloak-bff` skill and project-level `CLAUDE.md`. Read their actual definitions before starting. Do not create replacement agents or skills. If required definitions are unavailable, report the missing prerequisite.

Follow the repository architecture and version policy. Inspect actual dependency versions; do not upgrade frameworks merely to match a documentation example.

Run sequentially:

1. Inspect the working tree, project instructions, previous-stage implementation and tests.
2. Invoke `bff-security-architect` for this stage only; wait for its findings and concrete constraints.
3. Resolve blocking design findings before implementation. Invoke `bff-implementer` and wait for completion.
4. Validate the actual files on disk, automated tests, Docker stack and real browser behavior.
5. Invoke `bff-security-architect` again against the final implementation and evidence.
6. Fix in-scope findings, repeat affected checks where necessary, and report the result honestly.

Do not launch unrelated/background/noop agents. A required agent may execute in the background only if the orchestrator waits before starting the next dependent step. Do not treat an agent's summary as independent runtime evidence.

Do not commit or push. Do not overwrite unrelated uncommitted changes. If the implementation requires changing conflicting user edits, report the conflict. Do not reset the working tree, delete Docker volumes/data, run broad Docker prune commands or perform destructive Git operations.

## BFF security invariants

- Spring Boot owns OAuth2/OIDC and server-side token custody; the browser authenticates to the application using its Spring session cookie.
- React must never receive OAuth tokens through application APIs, parse them, store them, manage them or construct OAuth requests using them. Never expose the client secret.
- Keep confidential Authorization Code login, existing PKCE protection, state/nonce validation and correct issuer validation.
- Do not add `keycloak-js`, a frontend OAuth client, JWT browser authentication or an OAuth resource-server model for React.
- Preserve CSRF protection, constrained redirect destinations, same-origin/proxy architecture and appropriately limited Actuator exposure. No wildcard credentialed CORS.
- Do not serialize framework principals, authentication objects, OAuth2AuthorizedClient, raw claims or credentials into API responses.
- Never include token values, session IDs, passwords or secrets in logs, screenshots or reports. Disposable local credentials may remain in their explicitly labeled development configuration according to repository policy.
- Preserve the actual Stage 2A Docker/browser topology. Inspect manual ClientRegistrationRepository, browser-facing issuer, internal endpoints, fixed callback URL, PKCE and proxy routes before editing. Do not replace working explicit configuration with discovery without proving it works in containers.

## Inspection and runtime requirements

Inspect `SecurityConfig`, OAuth client registration, session configuration, controllers, React state/routing, Vite proxy, nginx routes, realm import, Compose, README and current tests. Determine actual ports, URLs, commands and versions instead of assuming examples are authoritative.

Run the repository's non-watch backend and frontend tests, frontend production build and `docker compose config`. Validate the supported `docker compose up --build` startup and both the development frontend and container-served frontend where routing differs.

A previously reported build problem involved a completed image export followed by a hanging Compose/Buildx Bake process. Treat this as historical context, not a confirmed current diagnosis. If it recurs, capture the failing phase, check both frontend and backend image freshness, and verify any workaround. Existing healthy containers alone do not prove current code was built. Do not claim the standard startup passes if only a workaround passes; document the limitation separately.

Use a real browser to verify each user flow. HTTP scripts and mocked OIDC tests complement browser validation but do not replace it. The orchestrator should perform browser checks if the implementer lacks browser tools. If no browser is available, mark those checks NOT VERIFIED and do not declare the stage fully complete.

No production credentials or real personal data. Use disposable local identities. Explain how realm changes reach an existing local Keycloak database: do not assume editing a realm import updates persisted realms automatically. Do not delete volumes to apply changes; use a documented targeted development update or report the required user action.

## Final report

Report:

1. Pre-implementation review constraints and any prerequisite gaps.
2. Files created and modified, with a short explanation.
3. Actual implementation and browser/API behavior.
4. Security decisions, including relevant URLs without sensitive query values.
5. Tests and runtime checks using the following table.
6. Final architecture review: PASS, PASS WITH FINDINGS or FAIL, with actual findings.
7. Intentionally deferred features and real unresolved issues.

| Check | Result | Evidence and who verified it |
|---|---|---|

Use PASS / FAIL / NOT VERIFIED. Distinguish direct observation, implementer-reported evidence and static inspection. Do not label a deferred later-stage feature as a defect. A stage is complete only when all required checks are verified and no blocking finding remains.

Do not commit.
Do not push.
