# Stage 3 — USER/ADMIN authorization in Spring BFF and React

Implement ONLY Stage 3 of `spring-keycloak-bff`. Require verified Stage 2A login, Stage 2B authentication state/routes and Stage 2C CSRF/full logout. Verify prerequisites and preserve their behavior.

## Goal and scope

Introduce two application roles, USER and ADMIN, enforced by Spring and reflected in React navigation. Add only non-sensitive proof endpoints and pages. Keycloak remains the identity/role provider; Spring maps trusted roles into application authorities; React receives only normalized application role names.

Do NOT implement business CRUD, role-management UI, an application-user database, dynamic permissions, organizations/tenants, resource ownership, Keycloak Admin API integration, token refresh customization, instant role revocation, session concurrency limits, registration, password reset or MFA.

## 1. Architecture review and authoritative role source

Inspect the actual OIDC principal, server-side claims, realm client/scopes/mappers and current authority mapping. Select ONE documented application-role source: prefer client roles `USER` and `ADMIN` belonging specifically to the existing `bff-app` client. Do not also grant application access from identically named realm roles or other clients.

Use supported Keycloak protocol mappers/client scopes to expose the intended client-role claim in the validated ID token and/or supported UserInfo response consumed by the BFF. Verify actual role availability for installed versions; do not assume access-token claims automatically appear in the OIDC principal. Prefer the ID-token claim path when it fits the existing architecture and validation.

Keep all claims server-side. Do not introduce resource-server browser authentication, a new JWT parser or custom unvalidated access-token decoding merely to obtain roles. Never trust roles supplied by React, request headers, query parameters or form data.

## 2. Role policy

Apply this exact application policy:

- USER may access user features.
- ADMIN may access both user and admin features.
- Authenticated users with neither recognized role may access their minimal session view and logout, but no role-protected feature.
- Unknown roles, other-client roles and Keycloak default roles grant no application access.

Map exact allowlisted values to Spring authorities `ROLE_USER` and `ROLE_ADMIN`. Enforce ADMIN's access to user features explicitly through authority mapping or a supported role hierarchy, consistently across all checks. Do not depend solely on assigning both roles to the development admin account: an ADMIN-only identity must satisfy the intended policy.

Missing role claims grant no application roles. Malformed role claims must never grant roles or cause an uncontrolled server error; choose and document safe handling. Preserve necessary framework OIDC authorities and existing login behavior.

Prefer supported OIDC user-service/authority-mapping extension points appropriate to the installed version. Keep the mapping small and independently testable.

## 3. Keycloak development configuration

Create/configure the application client roles and disposable local identities:

- A USER-only account.
- An ADMIN-only account to prove ADMIN can access user features.
- An authenticated account with neither application role for negative runtime checks.

Reuse existing disposable accounts where sensible. Keep development credentials clearly labeled and follow existing secret policy. Do not widen all client scopes or publish unrelated realm/client roles unnecessarily.

Explain how to apply role and mapper changes to persisted local Keycloak state without deleting volumes. Verify live configuration rather than relying solely on the import file.

Role changes are reflected after a new login for this stage; do not promise immediate propagation to an existing Spring session. Document this limitation and use logout/login in validation.

## 4. Backend authorization and API contract

Keep `/api/protected/hello` as the existing authentication-only proof endpoint. Add:

- `GET /api/user/hello`: USER or ADMIN; return a small proof response such as `{ "message": "user access" }`.
- `GET /api/admin/hello`: ADMIN only; return `{ "message": "admin access" }`.

Authorize at the backend, including requests made directly without the frontend. Use the existing security style; do not add redundant method-security scaffolding unless it helps a real boundary. Ensure matcher ordering does not expose broader `/api/**` routes.

Expected access:

| Identity | /api/auth/me | /api/protected/hello | /api/user/hello | /api/admin/hello |
|---|---|---|---|---|
| Anonymous | 401 | 401 | 401 | 401 |
| Authenticated, no app role | 200 | 200 | 403 | 403 |
| USER only | 200 | 200 | 200 | 403 |
| ADMIN only | 200 | 200 | 200 | 200 |

Keep JSON API errors free of stack traces and sensitive data. A 403 must not clear a valid frontend session or trigger login automatically.

Extend `/api/auth/me` with a top-level `roles` array of normalized effective application roles, e.g. USER -> `["USER"]`, ADMIN -> `["ADMIN", "USER"]`, no role -> `[]`. Preserve `authenticated`, minimal `user` fields, anonymous 401 behavior and `Cache-Control: no-store`. Never expose raw Keycloak role claims or every framework authority.

## 5. React pages and navigation

Keep public home, deterministic post-login home redirect, authentication-only `/protected` and working Logout. Add minimal `/user` and `/admin` pages backed by the corresponding proof endpoints.

Show the User link to USER/ADMIN and the Admin link to ADMIN. Route guards must wait for session loading. Anonymous users see the login gate; authenticated users without permission see an Access denied view with a Home link. Direct navigation and refresh must work through Vite and nginx.

An API 403 renders access denied while retaining authentication state. An API 401 follows Stage 2B session-loss handling. Hiding a navigation link is not authorization; backend checks remain authoritative. Do not store roles in localStorage or accept client-edited role state as security evidence.

## 6. Tests and real runtime matrix

Test the actual claim-to-authority mapping with recognized roles, missing claims, malformed claims, unknown roles and identically named roles for another client/realm. Verify ADMIN-only inheritance explicitly.

Backend filter-chain tests must cover every cell of the access matrix. Mocked `ROLE_*` endpoint tests alone do not prove Keycloak role mapping; include tests of the actual mapper and real Keycloak logins.

Frontend tests must cover role-specific links, route guards, loading behavior, direct unauthorized access, 403 without sign-out and 401 with session loss.

Using a real browser, log in and fully log out between the three disposable accounts. Verify:

1. Actual roles in the minimal session-view DTO match the intended policy.
2. Each account sees the appropriate home/navigation and page results.
3. USER cannot access the admin API directly; the no-role account cannot access user/admin APIs.
4. ADMIN-only can access both user and admin APIs.
5. Direct route refresh and logged-out access behave correctly.
6. Login, CSRF and full Keycloak logout still work after role mapping changes.
7. No token, secret or raw claim dump reaches React or browser storage.

## Definition of Done

- [ ] Application roles have one trusted, client-scoped source.
- [ ] Mapping grants only allowlisted roles and handles absent/malformed claims safely.
- [ ] ADMIN-only access to user features is verified.
- [ ] Every backend access-matrix cell is tested.
- [ ] Real Keycloak USER, ADMIN and no-role sessions confirm mapping and enforcement.
- [ ] React navigation/guards and 401/403 behavior are correct.
- [ ] Stage 2A–2C login, CSRF, session and logout behavior remains intact.
- [ ] Automated checks, browser validation and final review pass.
- [ ] README documents role source, mapping, matrix, local accounts and re-login limitation.

## Official reference

Consult the [Keycloak Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/) for client roles, role scope mappings and protocol mappers; check the documentation matching the actual installed version.


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
