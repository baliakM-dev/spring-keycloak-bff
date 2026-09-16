# Stage 2A — OAuth2/OIDC login through Spring Boot BFF

Implement Stage 2A of the `spring-keycloak-bff` reference project.

Use the existing:

- `bff-security-architect`
- `bff-implementer`
- `spring-keycloak-bff` skill
- project-level `CLAUDE.md`

Follow the current repository architecture, security rules and version policy.

Do not create new agents or skills for this task.

Run the workflow sequentially:

1. inspect repository
2. `bff-security-architect` pre-implementation review
3. wait for its result
4. `bff-implementer`
5. validation
6. `bff-security-architect` final review
7. final report

Do not launch unrelated/background/noop agents.

Do not commit.
Do not push.

---

## Goal

Implement a real OAuth2/OIDC Authorization Code login flow:

```text
Browser / React
        |
        | navigate to BFF login endpoint
        v
Spring Boot BFF
        |
        | OAuth2/OIDC Authorization Code
        v
Keycloak
        |
        | successful authentication
        v
Spring Boot callback
        |
        | server-side authenticated session
        v
Browser
```

```text
React
  ❌ access token
  ❌ refresh token
  ❌ client secret
  ❌ OAuth token handling

Spring Boot BFF
  ✅ OAuth2/OIDC client
  ✅ Authorization Code exchange
  ✅ server-side OAuth2AuthorizedClient/token custody
  ✅ authenticated Spring application session

Keycloak
  ✅ authentication
  ✅ OIDC provider
```

After successful authentication:

- Keycloak authenticates the user.
- Spring Boot owns the OAuth2/OIDC client interaction.
- Spring Security establishes the authenticated server-side application session.
- The browser receives only the application session cookie needed to communicate with the BFF.
- React must never receive, parse, store or manage OAuth access tokens or refresh tokens.

## Scope

Implement ONLY:

1. Spring Boot OAuth2/OIDC Client configuration.
2. Keycloak confidential bff-app client configuration.
3. Authorization Code login.
4. Server-side authenticated Spring session.
5. Minimal React Login button.
6. Minimal protected backend endpoint proving authentication.
7. A disposable local Keycloak test user if needed.
8. Relevant Stage 2A tests.
9. Runtime verification of the real login flow.
10. README update.

Do NOT implement yet:

- /api/auth/me
- frontend user/profile display
- application user profile
- USER/ADMIN role mapping
- role-based application authorization
- logout
- explicit custom refresh-token handling
- session concurrency limits
- brute-force tuning
- MFA/WebAuthn
- registration
- password reset
- business functionality
- full SPA CSRF integration
- database persistence for application users
- custom authentication/login page
- application-security-reviewer
- penetration testing
- OWASP ZAP
- unrelated infrastructure improvements

These belong to later stages.

Do not report these intentionally missing later-stage features as Stage 2A defects.

## 1. Inspect repository first

Before changing anything:

- inspect the current Git working tree
- inspect CLAUDE.md
- inspect existing .claude/agents
- inspect spring-keycloak-bff skill
- inspect current SecurityConfig
- inspect application.yml
- inspect pom.xml
- inspect React/Vite configuration
- inspect current Keycloak realm import
- inspect compose.yaml
- inspect Dockerfiles/nginx configuration
- inspect current tests
- inspect README

Determine the actual current architecture before proposing changes.

Do not overwrite unrelated or foreign uncommitted changes.

If the requested implementation would require modifying lines already changed by unrelated uncommitted work, stop and report the conflict instead of guessing or restoring files.

Do not perform destructive Git operations.

Forbidden examples:

```text
git reset --hard
git clean -fd
git restore <foreign-file>
git checkout -- <foreign-file>
git branch -D
git push --force
```

## 2. Pre-implementation security architecture review

Invoke:

bff-security-architect

Run it first and WAIT for the result before starting implementation.

Review scope:

Review the current repository and define the security constraints required to implement Stage 2A: OAuth2/OIDC Authorization Code login through Spring Boot BFF and Keycloak. Review only functionality relevant to this stage. Do not treat features intentionally assigned to later stages as defects.

The architect must specifically evaluate:

- Authorization Code flow
- Spring Boot acting as OAuth2/OIDC confidential client
- Keycloak client authentication
- redirect URI configuration
- issuer configuration
- authorization endpoint
- token endpoint
- JWK/metadata resolution
- OAuth state
- OIDC nonce
- PKCE support and applicability for the actual Spring Security + Keycloak versions
- server-side token custody
- Spring application session
- session cookie behavior
- browser-visible Keycloak URLs
- Docker-internal Keycloak URLs
- frontend login initiation
- default-deny behavior
- API anonymous behavior
- current CSRF posture without implementing Stage 2B/CSRF SPA integration

Do not manually recreate framework security mechanisms when Spring Security already provides them correctly.

If a blocking security issue exists, stop implementation and report it.

Otherwise continue.

## 3. Keycloak realm and client

Use the existing realm:

bff-demo

Use/configure the existing client:

bff-app

The BFF client must be appropriate for a server-side confidential OAuth2 client.

Required properties:

- Client authentication enabled / confidential client
- Authorization Code / Standard Flow enabled
- Direct Access Grants disabled
- Implicit Flow disabled
- Service Account not enabled unless there is a demonstrated need
- constrained redirect URIs
- no wildcard redirect URI unless absolutely technically necessary and explicitly justified
- no wildcard Web Origins for authenticated/sensitive functionality
- no production secret committed
- no real credentials committed

The client secret used by the local environment must be:

- development-only
- disposable
- clearly marked as local development configuration
- inaccessible to React/frontend code

Never place the client secret in:

```text
frontend/
VITE_*
window.*
localStorage
sessionStorage
frontend Docker image
browser bundle
```

Do not expose the secret through an API endpoint.

## 4. Local Keycloak test identity

If needed for runtime testing, create a disposable development-only user in the local realm.

For example:

```text
username: test-user
password: explicit local-development-only disposable password
```

Requirements:

- clearly document that it is DEVELOPMENT ONLY
- never reuse a real credential
- no real personal information
- do not create USER/ADMIN application role mapping yet

The account exists only to prove authentication.

## 5. Spring Boot OAuth2 Client configuration

Configure Spring Boot using supported Spring Security OAuth2 Client functionality.

Use the OAuth2 Client dependency already introduced in Stage 1.

Prefer framework configuration over custom OAuth implementations.

Spring Security should own:

- authorization request creation
- state
- OIDC nonce
- Authorization Code callback processing
- authorization code exchange
- ID token validation
- token processing
- OAuth2AuthorizedClient lifecycle
- authenticated SecurityContext
- application session

Do NOT manually implement:

- authorization code exchange using WebClient/RestClient
- access token parsing
- ID token parsing
- refresh-token exchange
- OAuth state generation/validation
- OIDC nonce generation/validation
- JWT verification logic

unless a concrete framework limitation is demonstrated.

Do not introduce:

```text
oauth2ResourceServer(...)
```

for browser authentication.

This application is not using the SPA bearer-token/resource-server model.

## 6. PKCE

Evaluate PKCE against the actual versions of:

- Spring Boot
- Spring Security
- Keycloak

If Spring Security supports PKCE cleanly for this confidential Authorization Code flow and it fits the existing architecture, enable/use it as defense in depth according to supported framework mechanisms.

Do not build a custom PKCE implementation.

If PKCE is not enabled, explicitly document the actual framework/version reason in the final report.

Do not claim PKCE is present unless runtime/configuration evidence supports it.

## 7. Spring Security configuration

Update the existing SecurityFilterChain minimally.

Maintain the current principle:

```text
/api/public/**       → public
/actuator/health     → public
authentication flow → required framework endpoints accessible
everything else      → default deny/authenticated as appropriate
```

Add only the security configuration required for Stage 2A.

Enable supported:

```text
oauth2Login(...)
```

Do not:

- disable Spring Security globally
- disable CSRF merely because something fails
- add wildcard CORS
- add permitAll to /api/**
- add JWT bearer authentication for React
- expose management endpoints unnecessarily

Preserve the future BFF security model.

## 8. Login entry point

React should initiate login by navigating the browser to the Spring Security OAuth2 authorization endpoint.

Use Spring Security's standard mechanism, expected to resemble:

```text
/oauth2/authorization/<registration-id>
```

Use the actual configured registration ID.

Do not create a frontend OAuth implementation.

Expected flow:

```text
React Login button
        ↓
Spring OAuth2 authorization endpoint
        ↓
Keycloak authorization endpoint
        ↓
Keycloak login
        ↓
Spring OAuth2 callback
        ↓
authenticated Spring session
```

React must not know:

- client secret
- authorization code
- access token
- refresh token
- ID token

beyond normal browser redirects handled by the server/framework.

## 9. Protected proof endpoint

Create a minimal endpoint:

```text
GET /api/protected/hello
```

Authenticated response:

```json
{
  "message": "authenticated"
}
```

This endpoint exists only to prove that the Spring application session is authenticated.

Do NOT return:

- access token
- refresh token
- ID token
- authorization code
- client secret
- complete OIDC claims
- Keycloak credentials

Do not create /api/auth/me yet.

Anonymous access must not return protected data.

The architect/implementer should select appropriate API behavior for anonymous access according to the current Spring Security architecture and explain it.

Do not introduce unnecessary custom exception handling purely to force a particular status code if standard framework behavior is acceptable for this stage.

## 10. Spring application session

Successful authentication must establish an authenticated Spring application session.

The browser may receive the application session cookie.

OAuth access/refresh tokens remain server-side.

Do NOT:

- manually put OAuth tokens into cookies
- expose OAuth tokens through React
- expose OAuth tokens through JSON
- store OAuth tokens in localStorage
- store OAuth tokens in sessionStorage

Review the session cookie configuration for local development.

Do not weaken production expectations just to make localhost work.

Local HTTP may require development-appropriate Secure-cookie behavior, but production requirements must remain documented appropriately.

Do not claim a production-secure cookie configuration if only localhost HTTP has been tested.

## 11. Docker / Keycloak hostname and issuer handling

This is an important part of Stage 2A.

The complete real login flow must work when the application is started using the supported Docker Compose setup.

Correctly distinguish:

Browser-visible Keycloak URL

from:

Docker-internal service URL

The browser must never be redirected to a hostname such as:

```text
http://keycloak:8080
```

if that hostname is only resolvable inside Docker.

At the same time, the Spring Boot container must be able to reach the OIDC metadata/token/JWK endpoints it needs.

Do not "solve" this by introducing inconsistent issuer identities.

OIDC issuer validation must remain correct.

Inspect the existing Keycloak hostname configuration and use supported Keycloak/Spring mechanisms for front-channel/back-channel addressing where necessary.

The following must be validated against the real running containers:

- discovery metadata
- issuer value
- authorization endpoint reachable by browser
- callback URL
- token endpoint reachable by Spring
- JWK endpoint reachable by Spring
- issuer validation succeeds

Do not hardcode an architecture that only works when Spring is run outside Docker if the supported project startup is:

```sh
docker compose up
```

If there is a genuine limitation, report it explicitly rather than hiding it.

## 12. Docker Compose

Keep Stage 1's supported startup model:

```sh
docker compose up --build
```

Do not add unnecessary services.

Expected existing services remain approximately:

- frontend
- backend
- keycloak
- keycloak-db

Only modify environment/configuration necessary for Stage 2A.

Do not introduce:

- Redis
- Spring Session external store
- reverse proxy replacement
- Kubernetes
- cloud services

unless absolutely required, which is not expected for this stage.

## 13. React implementation

Make the smallest UI change needed to prove login works.

Add a button such as:

Login

Clicking it should perform browser navigation to the BFF OAuth2 login endpoint.

Prefer normal navigation:

```text
window.location.href = ...
```

or equivalent browser navigation appropriate to the current React code.

Do not fetch the Keycloak login endpoint manually.

Do not install:

- keycloak-js
- react-oidc-context
- oidc-client-ts
- OAuth SPA libraries
- JWT libraries

unless a blocking architecture requirement proves otherwise.

The frontend remains intentionally ignorant of OAuth tokens.

Do not implement global auth state yet.

## 14. Frontend proxy/routing

Ensure the development/frontend proxy setup supports the authentication flow as required.

Stage 1 already anticipated paths such as:

```text
/api
/oauth2
/login
/logout
```

Only enable/configure what Stage 2A actually needs.

Avoid production wildcard CORS.

Prefer same-origin/proxy architecture where practical.

Do not introduce hardcoded backend URLs throughout React source code.

If an absolute development URL is required for full-page browser navigation, centralize/document it instead of scattering it.

## 15. CSRF

Do NOT implement full React-to-Spring CSRF integration yet.

However:

- do not disable CSRF globally
- do not add csrf.disable()
- do not weaken CSRF configuration merely to make OAuth login work

OAuth2 login must work using the framework-supported security flow.

If Spring Security requires a narrow matcher/exception for a legitimate OAuth callback mechanism, verify it against framework defaults rather than adding arbitrary exclusions.

Stage 2B/2C will handle application mutation requests and SPA CSRF integration separately.

## 16. Tests — backend

Add only tests relevant to Stage 2A.

At minimum verify:

### Public endpoint

Anonymous:

```text
GET /api/public/hello
```

remains accessible.

### Protected endpoint

Anonymous request to:

```text
GET /api/protected/hello
```

must not receive protected data.

### Authenticated OIDC user

Using Spring Security Test, simulate a valid authenticated OIDC user and verify:

```text
GET /api/protected/hello
```

returns:

```json
{
  "message": "authenticated"
}
```

### Security regression

Verify the security configuration has not accidentally made protected APIs public.

Do not mock access/refresh token behavior into React.

Do not create a huge integration suite yet.

## 17. Tests — frontend

Add/update only small tests necessary for the Stage 2A UI change.

At minimum verify:

- Login button renders
- activating it targets the expected Spring BFF OAuth2 authorization route

Do not test Keycloak itself in frontend unit tests.

Do not mock an entire OAuth implementation in React.

## 18. Real runtime validation

This stage is NOT complete using MockMvc alone.

Start the real stack.

Prefer:

```sh
docker compose up --build
```

If Docker BuildKit hangs after successfully producing images, diagnose it separately and do not confuse that with an application failure.

Verify actual container status:

```sh
docker compose ps
```

All expected services should be healthy/running.

Then validate the real OAuth2/OIDC flow.

## 19. Real browser authentication validation

Using a real browser:

- Open the frontend.
- Click Login.
- Confirm navigation goes through the Spring BFF.
- Confirm Spring redirects to Keycloak.
- Confirm Keycloak login page is browser-accessible.
- Authenticate using the disposable local test account.
- Confirm Keycloak returns to the Spring callback.
- Confirm Spring accepts the callback.
- Confirm the application session becomes authenticated.
- Confirm /api/protected/hello succeeds using that application session.
- Refresh the application/browser page.
- Confirm the active Spring session continues to authenticate appropriately.

Do not expose OAuth token values while documenting this.

## 20. Browser security inspection

After successful login inspect browser state.

Verify:

### Cookies

An application session cookie is present as expected.

Do not reveal its value in the report.

Record only relevant properties such as:

- name
- HttpOnly
- SameSite
- Secure behavior in the tested LOCAL HTTP environment

Do not copy session identifiers into documentation.

### localStorage

Confirm no:

- access_token
- refresh_token
- id_token
- OAuth token
- client secret

### sessionStorage

Confirm the same.

### Network

Verify React API calls authenticate using the application session/cookie rather than a browser-owned Bearer token.

Do not copy token values into the final report.

## 21. Server-side token custody

Determine and document precisely where Spring Security stores/manages the OAuth2AuthorizedClient in the current implementation.

Do not simply say:

tokens are server-side

without checking the actual implementation/configuration.

Confirm that access/refresh tokens are not exposed to React.

Do not add custom token persistence unless Stage 2A requires it.

External/shared persistence can be evaluated later if/when the application moves beyond a single local instance.

## 22. Logging

Inspect runtime logs during authentication.

Ensure application-created logging does not print:

- authorization code
- access token
- refresh token
- ID token
- client secret
- session ID
- password

Do not enable TRACE-level security logging merely to inspect secrets.

Use normal safe diagnostic information.

## 23. README update

Update README.md.

Document:

### Current stage

Stage 2A — OAuth2/OIDC login through Spring Boot BFF

### Implemented

- Keycloak OIDC provider
- confidential BFF client
- Authorization Code flow
- Spring Boot OAuth2 Client
- authenticated Spring session
- minimal React Login button
- protected proof endpoint
- server-side token custody

### Security model

Document clearly:

```text
Browser / React
        |
        | application session
        v
Spring Boot BFF
        |
        | OAuth2/OIDC
        v
Keycloak
```

And explicitly:

React does not own OAuth access or refresh tokens.

### Local test login

If a disposable development account is committed as part of the realm import, clearly mark credentials:

LOCAL DEVELOPMENT ONLY

### Not implemented yet

Document:

- /api/auth/me
- role mapping
- logout
- explicit refresh behavior
- session limits
- brute-force tuning
- business functionality

Do not imply those features exist.

## 24. Validation commands

Run the relevant commands.

Backend:

```sh
cd backend
./mvnw test
```

Frontend:

```sh
cd frontend
npm test
npm run build
```

Docker:

```sh
docker compose config
docker compose up --build
docker compose ps
```

Where applicable also verify:

```text
/api/public/hello
/api/protected/hello
/actuator/health
```

Do not claim a PASS for anything not actually executed.

Distinguish:

- verified directly by orchestrating Claude session
- verified by bff-implementer
- statically inspected only
- not verified

## 25. Final security architecture review

After implementation and validation, invoke:

bff-security-architect

Run the final review against the ACTUAL files on disk.

Review scope:

Review only the completed Stage 2A implementation. Verify the OAuth2/OIDC login and BFF security model. Do not report intentionally deferred Stage 2B+ features as defects.

Specifically verify:

- Authorization Code flow is used
- Keycloak client is confidential
- client authentication is enabled
- Direct Access Grants disabled
- Implicit Flow disabled
- client secret not exposed to React
- access token not exposed to React
- refresh token not exposed to React
- ID token not unnecessarily exposed to React
- no JWT SPA/resource-server browser model
- no keycloak-js
- no frontend OAuth library
- no OAuth tokens in localStorage
- no OAuth tokens in sessionStorage
- redirect URI properly constrained
- no wildcard sensitive Web Origins
- no wildcard production CORS
- CSRF not globally disabled
- OAuth state/nonce handled through supported framework mechanisms
- PKCE status accurately documented
- correct issuer
- correct browser-facing Keycloak URL
- correct Docker/back-channel connectivity
- successful authenticated Spring session
- protected endpoint actually protected
- Actuator exposure has not expanded
- no secret/token leakage in logs/config/frontend bundle

Classify evidence honestly:

- CONFIRMED FINDING
- RISK / ASSUMPTION
- NOT VERIFIED

Do not invent findings solely because a future-stage feature is absent.

## 26. Stop conditions

Stop implementation and report clearly if:

- the login cannot be implemented without exposing tokens to React
- issuer identity would need to be falsified/inconsistently configured
- client secret would need to enter frontend code
- CSRF would need to be globally disabled
- wildcard sensitive CORS would be required
- unrelated user changes would need to be overwritten
- the real Docker topology makes the proposed flow invalid
- a CRITICAL/HIGH Stage 2A security violation cannot be safely resolved within scope

Do not bypass the security model to make the demo work.

## 27. Final output

Return the final result using exactly these sections.

1. Pre-implementation architecture review

Summarize the relevant constraints from bff-security-architect.

### 2. Files created

List files.

### 3. Files modified

List files.

### 4. Authentication architecture

Show the actual implemented flow:

```text
React
  ↓
Spring OAuth2 authorization endpoint
  ↓
Keycloak
  ↓
Spring OAuth2 callback
  ↓
Spring SecurityContext/session
  ↓
Browser application session
```

Adjust this diagram if the actual implementation differs.

### 5. Keycloak configuration

Report relevant settings without revealing secret values:

- realm
- client ID
- client type/authentication
- Standard Flow
- Direct Access Grants
- Implicit Flow
- redirect URIs
- Web Origins
- PKCE status

Redact all secret values.

### 6. Token custody

For each:

| Item | Location / owner | Exposed to React? |
|---|---|---|
| Authorization Code | | |
| Access Token | | |
| Refresh Token | | |
| ID Token | | |
| Client Secret | | |
| Application session | | |

Never print actual values.

### 7. Tests

Report:

| Test | Result | Evidence |
|---|---|---|

Include backend and frontend tests.

### 8. Runtime validation

Report:

| Runtime check | Result | Evidence |
|---|---|---|

Include:

- containers
- frontend
- backend
- Keycloak
- redirect to Keycloak
- login
- callback
- authenticated session
- protected endpoint
- browser storage inspection

### 9. Browser security validation

Report:

- session cookie characteristics
- localStorage check
- sessionStorage check
- network auth model
- token exposure result

Do not reveal cookie or token values.

### 10. Final security review

One of:

- PASS
- PASS WITH FINDINGS
- FAIL

Then list only actual findings/risks/not-verified items.

### 11. Intentionally not implemented

Explicitly list:

- /api/auth/me
- profile/user display
- USER/ADMIN role mapping
- role authorization
- logout
- explicit refresh handling
- session concurrency limits
- brute-force configuration
- MFA
- password reset
- registration
- business functionality
- full application CSRF mutation flow

### 12. Remaining issues

Only actual unresolved issues.

If none:

No blocking Stage 2A issues remain.

## Definition of Done

Stage 2A is complete only if ALL of the following are true:

- [ ] Spring Boot is configured as an OAuth2/OIDC client
- [ ] Keycloak bff-app is a confidential client
- [ ] Authorization Code flow works
- [ ] Direct Access Grants are disabled
- [ ] Implicit Flow is disabled
- [ ] Login starts from React via the BFF
- [ ] Browser is redirected to Keycloak
- [ ] Keycloak authentication succeeds
- [ ] Callback is processed by Spring Security
- [ ] Spring establishes an authenticated application session
- [ ] /api/protected/hello is protected
- [ ] authenticated session can access /api/protected/hello
- [ ] React receives no access token
- [ ] React receives no refresh token
- [ ] React receives no client secret
- [ ] localStorage contains no OAuth tokens
- [ ] sessionStorage contains no OAuth tokens
- [ ] CSRF has not been globally disabled
- [ ] no wildcard sensitive CORS was introduced
- [ ] Docker-hostname/issuer behavior works in the real stack
- [ ] backend tests pass
- [ ] frontend tests pass
- [ ] frontend build passes
- [ ] docker compose config passes
- [ ] real login flow was runtime-tested
- [ ] final bff-security-architect review completed
- [ ] README reflects actual Stage 2A state

If any required item cannot be verified, mark it as NOT VERIFIED rather than inventing a PASS.

Do not commit.
Do not push.
