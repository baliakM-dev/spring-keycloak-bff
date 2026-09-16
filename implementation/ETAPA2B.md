# Stage 2B — Authentication state, home redirect and protected React route

Implement ONLY Stage 2B of `spring-keycloak-bff`. Stage 2A must already provide working real Keycloak login, authenticated Spring sessions and the protected proof endpoint. Verify this prerequisite; report an incomplete baseline instead of silently claiming Stage 2B is done.

## Goal and scope

Create a minimal usable authenticated frontend without moving OAuth responsibilities into React:

- Public home page at `/`.
- Successful login returns to `/` on the actual frontend origin.
- `GET /api/auth/me` supplies a small application-owned user view.
- React loads session state, shows a display name and provides a protected page at `/protected`.
- Session loss produces a clear signed-out UI without redirect loops.

Do NOT implement logout, SPA CSRF token plumbing, role mapping, USER/ADMIN authorization, token refresh customization, registration, password reset, MFA, session concurrency limits, business functionality or application-user persistence. Do not display a nonfunctional Logout button; that belongs to Stage 2C.

## 1. Architecture review focus

Ask the architect to settle the `/api/auth/me` contract, anonymous API behavior, minimal identity fields, success redirect, request-cache behavior, proxy routing and session-expiry UX. Inspect existing default authorization rules; allow only intended public resources and authentication endpoints.

## 2. Backend session view

Implement `GET /api/auth/me` with this contract:

Authenticated: HTTP 200, JSON containing only explicitly mapped fields:

```json
{
  "authenticated": true,
  "user": {
    "id": "OIDC subject identifier",
    "displayName": "safe display name"
  }
}
```

Anonymous or expired application session: HTTP 401, a small application-owned JSON response such as:

```json
{
  "authenticated": false
}
```

Do not redirect API requests to Keycloak or return a login HTML page with status 200. Ensure other protected application API requests return 401 when anonymous while explicit browser navigation to the OAuth login entry point still starts login normally. Keep public APIs public and preserve default protection elsewhere.

Use the validated OIDC principal. Choose and document a display-name fallback, e.g. name → preferred username → subject. Missing optional claims must not crash the endpoint. Do not add email, role claims, full profiles or personal fields that the UI does not need. Treat displayed values as untrusted text and render them normally through React.

Return `Cache-Control: no-store` for the session-view response, including its anonymous form. Prevent API authentication failures from becoming saved navigation destinations that interfere with login success.

## 3. Login success and routing

After a successful login, navigate to the public frontend `/` on the configured, trusted origin. Use supported Spring Security success handling. Preserve callback URI and browser-visible/internal Keycloak separation.

Do not derive external redirects from untrusted Host/forwarded headers, query parameters or arbitrary `returnTo` values. Returning to the originally requested protected route is deliberately out of scope; home is the deterministic success destination for this stage.

Use existing frontend routing if present; introduce only the minimal router needed otherwise, within version policy. Configure Vite and nginx so direct navigation and refresh at `/protected` load the SPA while `/api`, `/oauth2` and `/login` retain correct backend forwarding.

## 4. React authentication state

Create a small shared session-state mechanism with distinct states: loading, authenticated, anonymous and error. Bootstrap it through the same-origin `/api/auth/me` endpoint using session cookies. Keep profile state in memory; do not persist authentication assertions in browser storage.

- Loading: show a minimal loading state; do not flash protected content or start automatic login.
- Authenticated: home displays the user's display name and a link to `/protected`.
- Anonymous: home and the protected-route gate offer Login, using full browser navigation to the BFF authorization endpoint.
- Network/server failure: show a retryable error. Do not misreport every failed request as signed out.

The protected page must call the existing `/api/protected/hello` and display its non-sensitive response. A React route guard is UX only; Spring must enforce API authentication independently.

Centralize application API 401 handling sufficiently to clear stale user state and show the login gate after session expiry. Do not mistake 403 for an expired session. Re-check session state on page reload and on returning focus to the application, without introducing polling or automatic login loops. Prevent late responses from restoring stale protected data after session loss.

## 5. Tests and browser verification

Backend tests must cover anonymous 401 JSON, authenticated minimal DTO, missing optional display-name claims, no-store behavior, public endpoint access, protected API rejection and preserved login initiation. Assert the explicit response schema rather than serializing framework objects.

Frontend tests must cover loading without protected-content flash, signed-in home, anonymous route gate, Login destination, retryable errors, 401 clearing stale state and successful protected data display. Mock session-view/API responses, not OAuth token handling.

With a real browser verify:

1. A clean anonymous session sees home and the Login button.
2. Direct `/protected` navigation displays the login gate without protected data.
3. Clicking Login performs the real Keycloak flow and returns to `/`.
4. Home shows the user; `/protected` successfully calls the protected API.
5. Refresh/direct navigation works in the container-served frontend as well as development mode.
6. Invalidating/removing only the BFF session cookie in a disposable browser test makes the next session/API check anonymous without a loop. Record this as simulated session loss, not a measured server timeout.
7. Browser API calls use the session cookie; OAuth tokens are absent from application JSON and browser storage.

## Definition of Done

- [ ] Stage 2A real login still works.
- [ ] `/api/auth/me` follows the documented 200/401 contract and minimal schema.
- [ ] Login deterministically returns to frontend home.
- [ ] Home, loading, error and anonymous states behave correctly.
- [ ] `/protected` works on direct navigation and refresh; backend remains protected.
- [ ] Session loss clears stale authenticated UI on the next check.
- [ ] Automated checks, real browser validation and final review pass.
- [ ] README documents routes, contract, current stage and deferred Stage 2C/3 work.


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
