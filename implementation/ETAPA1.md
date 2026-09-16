Implementuj ETAPU 1 referenčného projektu `spring-keycloak-bff`.

Použi existujúce:

- `bff-security-architect`
- `bff-implementer`
- `spring-keycloak-bff` skill

Toto je prvá reálna implementačná etapa.

# Cieľ

Vytvor minimálny, production-minded a lokálne spustiteľný skeleton:

```text
React + TypeScript
        |
        | HTTP
        v
Spring Boot BFF
        |
        | zatiaľ bez OAuth login flow
        v
Keycloak

Keycloak
        |
        v
PostgreSQL
```

Po skončení musí developer vedieť spustiť celý základ projektu a overiť:

- React beží,
- Spring Boot beží,
- Keycloak beží,
- PostgreSQL pre Keycloak beží,
- healthchecky fungujú,
- React vie zavolať jednoduchý public backend endpoint.

NEIMPLEMENTUJ ešte:

- OAuth2 login,
- `/api/auth/me`,
- logout,
- refresh token handling,
- role mapping,
- USER/ADMIN authorization,
- CSRF integration medzi React a Spring,
- session limit,
- brute-force konfiguráciu,
- business funkcionalitu.

Tieto veci budú samostatné etapy.

---

# 1. Najprv inspect

Pred implementáciou:

1. skontroluj aktuálny repository,
2. skontroluj existujúce `.claude` agenty a skill,
3. skontroluj working tree,
4. zachovaj všetky existujúce súbory a cudzie zmeny.

Nevykonávaj destructive git operácie.

---

# 2. Architecture review

Najprv použi `bff-security-architect`.

Úloha:

> Review the proposed Stage 1 skeleton for a future React + Spring Boot + Keycloak BFF application. Focus only on architectural/security constraints relevant to the skeleton. Do not review future login/session implementation that does not exist yet.

Architect má:

- zostať read-only,
- použiť `spring-keycloak-bff`,
- definovať security constraints pre skeleton,
- neexpandovať scope.

Ak nenájde blocking problém, pokračuj.

---

# 3. Versions

Použi aktuálne stabilné, vzájomne kompatibilné verzie.

Pred výberom verzií:

- over aktuálnu stabilnú verziu Spring Boot,
- over podporovanú Java verziu,
- over aktuálny stabilný React,
- over aktuálny Vite,
- over aktuálny stabilný Keycloak,
- over aktuálny stabilný PostgreSQL vhodný pre Keycloak.

Preferuj oficiálnu dokumentáciu jednotlivých projektov.

Nepoužívaj zastaranú verziu len preto, že ju poznáš z tréningových dát.

Nevykonávaj experimentálne/pre-release upgrady.

V README zaznamenaj použité verzie.

---

# 4. Backend

Vytvor:

```text
backend/
```

Použi:

- Java
- Maven
- Spring Boot
- Spring Web
- Spring Security
- OAuth2 Client dependency pripravenú pre budúcu etapu
- Actuator
- Spring Boot Test
- Spring Security Test

Zatiaľ NEKONFIGURUJ OAuth2 login.

Vytvor minimálny endpoint:

```http
GET /api/public/hello
```

Response napr.:

```json
{
  "message": "spring-keycloak-bff"
}
```

Endpoint musí byť verejný.

Zatiaľ môže byť security konfigurácia minimálna, ale nesmie:

- vypínať security mechanizmy globálne bez dôvodu,
- zavádzať JWT resource-server model,
- zavádzať browser-owned OAuth tokens.

Nevytváraj unnecessary architecture layers.

---

# 5. Backend health

Actuator:

```http
GET /actuator/health
```

má byť použiteľný pre local Docker healthcheck.

Nevystavuj všetky Actuator endpointy wildcardom.

Zatiaľ stačí minimum potrebné pre health.

---

# 6. Frontend

Vytvor:

```text
frontend/
```

Použi:

- React
- TypeScript
- Vite

Minimal UI:

```text
Spring Keycloak BFF

Backend status:
Connected / Error
```

Frontend nech pri načítaní zavolá:

```http
GET /api/public/hello
```

a zobrazí výsledok.

Žiadny:

- Keycloak JS,
- OAuth token handling,
- access token,
- refresh token,
- localStorage auth,
- login button.

To príde neskôr.

---

# 7. Development proxy

Pre local development preferuj Vite proxy:

```text
/api → Spring Boot
```

Priprav architektúru tak, aby sme neskôr mohli pridať aj:

```text
/oauth2
/login
/logout
```

ale tieto routes zatiaľ nemusíš používať.

Cieľ:

React nemá hardcodovať backend URL do application code, ak tomu vieme rozumne predísť development proxy konfiguráciou.

Nevytváraj wildcard production CORS iba kvôli developmentu.

---

# 8. Keycloak

Priprav:

```text
keycloak/
```

a lokálny Keycloak v Docker Compose.

Použi PostgreSQL ako Keycloak datastore.

Vytvor development realm:

```text
bff-demo
```

Priprav reproducible local realm import.

Realm export nesmie obsahovať:

- production secrets,
- reálne credentials,
- osobné údaje.

Môže obsahovať explicitne development-only disposable hodnoty.

Zatiaľ nevytváraj komplikované:

- users,
- role mapping,
- authentication flows,
- brute-force tuning,
- session limits.

Ak Keycloak potrebuje development admin credential, označ ho jednoznačne ako:

```text
LOCAL DEVELOPMENT ONLY
```

Nesmie sa tváriť ako production secret.

---

# 9. Keycloak client

Môžeš pripraviť základ clientu pre budúci BFF:

```text
bff-app
```

Ak ho vytvoríš, musí byť pripravený pre budúci confidential BFF client.

Ale zatiaľ:

- neimplementuj login,
- neimplementuj callback,
- neimplementuj OAuth token exchange v aplikácii.

Nevytváraj konfiguráciu navyše iba preto, že ju budeme možno potrebovať neskôr.

---

# 10. PostgreSQL

PostgreSQL v tejto etape slúži Keycloaku.

Neprepájaj Spring aplikáciu na databázu iba preto, aby sme mali PostgreSQL aj na backend strane.

Backend persistence pridáme, keď vznikne business funkcionalita.

Použi:

- dedicated Keycloak database,
- dedicated DB user,
- development-only credentials pre local Compose.

---

# 11. Docker

Vytvor root:

```text
compose.yaml
```

Minimálne services:

```text
keycloak-db
keycloak
backend
frontend
```

Ak je pre development frontend efektívnejšie spúšťať mimo Dockeru, môžeš navrhnúť túto alternatívu, ale výsledný repository musí mať jednoduchý reprodukovateľný spôsob spustenia celého stacku.

Preferujem:

```bash
docker compose up --build
```

ako jeden podporovaný spôsob spustenia.

Nepoužívaj obsolete Compose syntax.

Pridaj rozumné healthchecky tam, kde dávajú zmysel.

Nevytváraj rigidné startup order hacky.

---

# 12. Docker networking

Správne rozlišuj:

```text
browser-visible hostname
```

vs.

```text
Docker internal service hostname
```

Nevystavuj browseru URL typu:

```text
http://keycloak:8080
```

len preto, že funguje medzi containermi.

Local browser-facing URLs musia byť reálne dostupné z host systému.

---

# 13. Dockerfiles

Vytvor:

```text
backend/Dockerfile
frontend/Dockerfile
```

Použi multi-stage build tam, kde to dáva zmysel.

Neoptimalizuj image do extrému.

Preferuj:

- reprodukovateľný build,
- rozumnú veľkosť,
- non-root runtime tam, kde je to praktické,
- žiadne secrets baked into images.

Frontend production-like image môže používať jednoduchý static web server/reverse proxy, ak je to pre túto štruktúru rozumné.

Nevytváraj Kubernetes.

---

# 14. Configuration

Rozlišuj:

```text
development configuration
```

a budúce production secrets.

Nevkladaj do repository:

- production credentials,
- production Keycloak secret,
- API keys.

Development-only disposable hodnoty musia byť jasne označené.

Pridaj:

```text
.env.example
```

iba ak je skutočne potrebný.

Necommituj reálny `.env`.

---

# 15. Git ignore

Over alebo vytvor `.gitignore`.

Minimálne podľa potreby:

```text
.env
node_modules/
dist/
target/
.idea/
*.log
```

Neignoruj development realm config, ak má byť zámerne verzovaný.

---

# 16. Tests

Použi scoped testing.

Backend minimálne:

- context/startup test,
- `/api/public/hello` test,
- overenie, že public endpoint je accessible.

Frontend:

- jeden malý test relevantný k zobrazovaniu backend statusu, ak test infrastructure vytváraš už teraz.

Nevytváraj veľkú test infraštruktúru iba preto, aby existovala.

Zatiaľ netreba:

- Playwright auth E2E,
- Testcontainers Keycloak auth flow,
- login tests,
- CSRF tests,
- role tests.

Tie patria do ďalších etáp.

---

# 17. CI

Pridaj minimálny GitHub Actions workflow.

Pri push / pull request:

Backend:

```text
mvn test
```

Frontend:

```text
npm ci
npm run build
```

Ak frontend test script existuje:

```text
npm test
```

alebo príslušný non-watch variant.

Nepoužívaj deployment.

Toto je iba build/test CI.

---

# 18. README

Vytvor kvalitný root `README.md`.

Musí obsahovať:

## Project purpose

Vysvetli:

> Reference implementation of a secure Backend-for-Frontend architecture using Spring Boot, React and Keycloak.

## Current stage

Explicitne:

```text
Stage 1 — infrastructure/skeleton only.
Authentication flow is not implemented yet.
```

## Architecture diagram

Minimálne:

```text
React
  |
  v
Spring Boot BFF

Future authentication:
Spring Boot BFF
  |
  v
Keycloak
  |
  v
PostgreSQL
```

Nesimuluj, že OAuth už funguje.

## Stack

Použité verzie.

## Running locally

Preferovane:

```bash
docker compose up --build
```

## URLs

Napr.:

```text
Frontend
Backend
Keycloak
Health
```

Použi skutočné hodnoty z implementácie.

## Test commands

Backend/frontend.

## Security model

Stručne vysvetli budúci model:

- OAuth tokens server-side,
- React bude používať application session,
- Keycloak bude identity provider.

Jasne označ, že login ešte nie je implementovaný.

---

# 19. File structure

Po implementácii očakávam približne:

```text
spring-keycloak-bff/

├── .claude/
│   ├── agents/
│   └── skills/
│
├── .github/
│   └── workflows/
│
├── backend/
│
├── frontend/
│
├── keycloak/
│
├── compose.yaml
├── .gitignore
└── README.md
```

Neber tento tree ako dôvod vytvárať nepotrebné placeholder directories.

---

# 20. Implementation

Na samotnú implementáciu použi:

`bff-implementer`

Implementuj smallest safe solution.

Nevykonávaj unrelated refactoring.

---

# 21. Validation

Po implementácii over minimálne:

```text
backend tests
frontend build
docker compose config
```

Ak je možné celý stack reálne spustiť v dostupnom prostredí, over:

```text
Spring health
public endpoint
frontend
Keycloak health/readiness
```

Ak niečo nemôžeš reálne spustiť, povedz presne čo nebolo overené.

Nevymýšľaj PASS.

---

# 22. Final architecture review

Po implementácii znovu použi:

`bff-security-architect`

Review scope:

> Review only Stage 1 implementation. Verify that the skeleton does not violate the future BFF security model. Do not report missing login/session/CSRF/role behavior as defects because those belong to later stages.

Architect musí najmä overiť:

- žiadne OAuth tokeny v Reacte,
- žiadny client secret v Reacte,
- žiadny JWT SPA model,
- žiadne `csrf.disable()` ako budúci shortcut,
- rozumný Keycloak/local Docker setup,
- development secrets sú jasne development-only,
- žiadne zbytočne otvorené Actuator endpoints,
- žiadny wildcard production CORS zavedený iba kvôli developmentu.

---

# 23. Stop conditions

Ak vznikne BLOCKING SECURITY GAP:

- neobchádzaj ho,
- nevypínaj ochrany,
- nezväčšuj scope bez vysvetlenia.

Reportuj ho.

---

# 24. Final output

Po dokončení vráť:

## Architecture review before implementation

Krátke constraints z `bff-security-architect`.

## Files created

## Files modified

## Versions used

Table:

| Component | Version |
|---|---|

## Architecture

Skutočná implementovaná topológia.

## Validation

Table:

| Check | Result | Evidence |
|---|---|---|

## Tests

Presné commands + results.

## Final security review

- PASS
- PASS WITH FINDINGS
- FAIL

a findings, ak existujú.

## Not implemented intentionally

Explicitne vypíš:

- OAuth login
- `/api/auth/me`
- CSRF SPA integration
- logout
- refresh
- role mapping
- session limits
- brute-force tuning

aby bolo jasné, že nejde o zabudnuté funkcionality.

## Remaining issues

Iba skutočné issues.

Do not commit.
Do not push.