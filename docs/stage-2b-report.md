# Stage 2B — Finálny report: autentifikačný stav, home redirect a chránená React route

Repozitár: `spring-keycloak-bff`, branch `feature/2b-session-auth-protected-route`.
Stav: implementácia dokončená, runtime aj browser overené, **nič nebolo zmazané, commitnuté ani pushnuté**.

Tento report nadväzuje na `docs/stage-2a-report.md` a pokrýva tri kolá
overenia: (1) implementácia + prvý review, kde bol `claude-in-chrome`
nedostupný a nahradil ho skriptovaný `curl` flow; (2) doplnenie reálnej
browser validácie po pripojení `claude-in-chrome` a overenie presného
príkazu `docker compose up --build` (sekcia 6.2); (3) uzatváracie kolo
pred Stage 2C — doriešenie `sessionStorage` overenia, kontrola runtime
logov na únik citlivých údajov, cielený re-review opravy race condition, a
oprava nezrovnalosti v tvrdení o počte úspešných testov presného
kombinovaného `docker compose up --build` príkazu (sekcia 6.3). Presné
rozlíšenie, čo bolo overené ktorým spôsobom a v ktorom kole, je v
príslušných sekciách nižšie.

---

## 1. Pracovný postup

1. `bff-security-architect` — pred-implementačný review (settlol kontrakt
   `/api/auth/me`, entry point pre `/api/**`, request cache/redirect, routing,
   session-expiry UX).
2. `bff-implementer` — implementácia podľa záväzných obmedzení.
3. Orchestrujúca relácia — nezávislá validácia (vlastné spustenie testov,
   skriptovaný HTTP login flow cez `curl` proti reálne bežiacemu Docker stacku).
4. `bff-security-architect` — finálny review nad skutočným kódom + zozbieraným
   dôkazom.
5. Orchestrujúca relácia — oprava jediného nájdeného MEDIUM nálezu (race
   condition, sekcia 5) + regresný test, overený proti neopravenému kódu.
6. Predchádzajúca relácia — dokončenie chýbajúcej browser validácie po
   pripojení `claude-in-chrome`, a presné overenie príkazu `docker compose
   up --build` (jeden úspešný beh).
7. **Táto relácia (uzatváracie kolo)** — pokus o overenie `sessionStorage`
   (nástroj nedostupný, zostáva NOT VERIFIED); priama kontrola runtime
   logov backendu a Keycloaku na únik citlivých údajov; cielený re-review
   opravy race condition + jej testu nezávislou invokáciou
   `bff-security-architect`; oprava nezrovnalosti v tvrdení o počte
   úspešných testov presného `docker compose up --build`.

---

## 2. Pre-implementačný architektonický review

**Výsledok: PASS WITH FINDINGS** (nálezy sa týkali existujúceho Stage 2A
nastavenia, nie navrhovaného Stage 2B kódu — boli to nutné doplnky, nie
regresie).

Kľúčové záväzné obmedzenia:

- `GET /api/auth/me` — `permitAll()` na úrovni filter chainu, autentifikačná
  kontrola vo vnútri controllera (`@AuthenticationPrincipal OidcUser`).
  `id` = `oidcUser.getSubject()` (**nie** `getName()`, keďže registrácia má
  `userNameAttributeName("preferred_username")`). `displayName` fallback:
  `getFullName()` → `getPreferredUsername()` → `getSubject()`, cez null-safe
  typované gettery. `Cache-Control: no-store` explicitne na oboch vetvách.
- Ostatné `/api/**` cesty (napr. `/api/protected/hello`) musia anonymnému
  volajúcemu vrátiť holý `401`, nie redirect — cez
  `defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(401), "/api/**")`.
  Reálna navigácia na `/oauth2/authorization/bff-app` musí zostať funkčná
  (obsluhuje ju `OAuth2AuthorizationRequestRedirectFilter`, pred týmto entry
  pointom).
- Request cache vypnúť (`requestCache(RequestCacheConfigurer::disable)`),
  fixný `defaultSuccessUrl("/", true)` — nikdy odvodený z Host/forwarded
  hlavičiek ani query parametra.
- Minimálny client-side router prípustný; `/protected` guard je len UX,
  nginx/vite fallback už funguje bez zmeny.
- React reaguje výhradne na `401` (nikdy `403` ani generický non-2xx);
  žiadny auto-login trigger; recheck len na focus/visibilitychange, žiadny
  polling.

---

## 3. Vytvorené a upravené súbory

### Backend (nové)

- `backend/src/main/java/com/example/bff/api/AuthController.java` — `GET /api/auth/me`
- `backend/src/main/java/com/example/bff/api/UserView.java`, `MeResponse.java` — DTO
- `backend/src/test/java/com/example/bff/api/AuthControllerTest.java`
- `backend/src/test/java/com/example/bff/config/SecurityConfigTest.java`

### Backend (upravené)

- `backend/src/main/java/com/example/bff/config/SecurityConfig.java` —
  `/api/auth/me` pridané do `permitAll()`; `/api/**`-scoped `401` entry
  point; vypnutý request cache; `defaultSuccessUrl("/", true)`.
- `backend/src/test/java/com/example/bff/api/ProtectedControllerTest.java` —
  anonymný test sprísnený na `status().isUnauthorized()`.

### Frontend (nové)

- `frontend/src/auth/SessionContext.tsx` — `loading/authenticated/anonymous/error`
  stav, bootstrap cez `/api/auth/me`, recheck na focus/visibilitychange,
  `reportUnauthorized()` centralizované 401 handling, generation counter
  proti late-response race (viď sekcia 5 — opravené v tejto relácii).
- `frontend/src/auth/SessionContext.test.tsx` — **nový, pridaný orchestrujúcou
  reláciou** (regresný test na race condition).
- `frontend/src/pages/HomePage.tsx`, `ProtectedPage.tsx` (+ testy).

### Frontend (upravené)

- `frontend/src/App.tsx` — `react-router` `BrowserRouter`/`Routes`, `/` a `/protected`.
- `frontend/src/App.test.tsx`, `frontend/package.json`/`package-lock.json`
  (`react-router@8.4.0`, overené ako aktuálna stabilná verzia, peer deps
  `react/react-dom >=19.2.7` splnené projektovou verziou `19.3.0`).

### Dokumentácia (upravené)

- `README.md` — Stage 2B sekcia, architektúra, URL tabuľka, security model,
  zoznam zámerne odložených položiek.

---

## 4. Testy (spustené priamo orchestrujúcou reláciou, nie len reportované)

| Príkaz | Výsledok |
|---|---|
| `cd backend && ./mvnw -o test` | **PASS** — 10/10 (`BffApplicationTests`, `PublicControllerTest`, `ProtectedControllerTest` ×3, `AuthControllerTest` ×4, `SecurityConfigTest`) |
| `cd frontend && npx vitest run` | **PASS** — 5 súborov, 12/12 testov (vrátane nového `SessionContext.test.tsx`) |
| `cd frontend && npm run build` | **PASS** (`tsc -b && vite build`) |

Testové scenáre backendu: anonymný `401` + `no-store` + schéma pre
`/api/auth/me`, autentifikovaný DTO tvar, `displayName` fallback reťazec,
verejný endpoint bez zmeny, `/api/protected/hello` teraz garantovane `401`
pre anonymného volajúceho, `/oauth2/authorization/bff-app` naďalej vydáva
reálny redirect na Keycloak.

Testové scenáre frontendu: loading bez flash-u chráneného obsahu,
authenticated home, anonymný gate na `/protected` (bez volania chráneného
API), Login odkaz/cieľ, retryable error stav, 401 čistí stale stav,
úspešné zobrazenie chránených dát, **a race condition regresný test**
(sekcia 5).

---

## 5. Nájdený a opravený race condition (MEDIUM)

Finálny `bff-security-architect` review nad hotovým kódom našiel jeden reálny
nález:

**MEDIUM — `SessionContext.tsx` nekontroloval `generationRef` po `await response.json()`**

- Scenár: bootstrap `fetch("/api/auth/me")` vráti `200`, generation check na
  začiatku `.then` prejde (ešte nedošlo k sign-outu). Počas `await
  response.json()` (yield do microtask queue) niekto iný (napr. `401` z
  `/api/protected/hello`) zavolá `reportUnauthorized()`, ktorý synchrónne
  zvýši `generationRef` a nastaví stav na `anonymous`. Keď sa
  `response.json()` vyrieši, pôvodný callback pokračuje **bez opätovnej
  kontroly generation** a prepíše stav späť na `authenticated` — napriek
  tomu, že medzitým reálne došlo k sign-outu.
- Dopad: krátkodobo nesprávny UI stav ("Signed in as ...") po reálnom
  sign-oute. **Nie je to server-side bypass** — akékoľvek reálne volanie na
  `/api/protected/hello` by aj tak dostalo `401` z backendu bez ohľadu na
  klientský stav.
- Oprava (aplikovaná orchestrujúcou reláciou):
  ```ts
  if (response.status === 200) {
    const body = (await response.json()) as MeResponseBody;
    if (generationRef.current !== generation) {
      return;
    }
    ...
  }
  ```
- **Overenie opravy vlastným testom** (`frontend/src/auth/SessionContext.test.tsx`):
  1. Test s kontrolovateľným `response.json()` (fake `Response` objekt,
     ktorého `.json()` sa vyrieši až na explicitný povel) reprodukoval presne
     tento race.
  2. Test **zámerne spustený proti neopravenému kódu** (dočasné odstránenie
     druhého generation-checku) — **zlyhal**, presne s očakávaným "expected
     anonymous, received authenticated". Toto potvrdzuje, že test skutočne
     deteguje chybu, nie je tautologický.
  3. Po vrátení opravy — **PASS**, aj celá zvyšná test suite (12/12).

Po tejto oprave a znovuzostavení Docker images (`docker compose build` +
`up -d`) bola oprava opätovne overená proti bežiacemu kontajnerizovanému
stacku (`/api/auth/me` a `/api/protected/hello` odpovede nezmenené,
očakávané).

---

## 6. Runtime a browser validácia

### 6.1 Presný príkaz `docker compose up --build`

README dokumentuje historický, na tomto stroji opakovane reprodukovaný hang
tohto presného príkazu (pozri `docs/stage-2a-report.md` sekcia 8.1). Naprieč
oboma reláciami Stage 2B prebehli dva odlišné testy, **len jeden z nich bol
skutočne presný kombinovaný príkaz**:

| Pokus | Kontext | Výsledok |
|---|---|---|
| 1 (predchádzajúca relácia) | `docker compose build` samostatne, potom `docker compose up -d` (dvojkrokový, nie kombinovaný príkaz — teda **netestoval** presne pôvodne hlásený scenár) | **PASS** — build aj štart bez zaseknutia |
| 2 (táto/predchádzajúca relácia*) | `docker compose down` (čistý stav, bez `-v`) → **`docker compose up --build`** (presný, kombinovaný príkaz, na pozadí, sledovaný cez log + `docker ps`) — **jediný skutočný test pôvodne hláseného scenára v Stage 2B** | **PASS** — všetky 4 kontajnery (`keycloak-db`, `keycloak`, `backend`, `frontend`) dosiahli `healthy` do ~30 sekúnd, žiadne zaseknutie, proces zostal pripojený (foreground/attached) počas celého behu |

*Pokus 2 prebehol v relácii bezprostredne predchádzajúcej tejto (v rámci
toho istého Stage 2B pracovného prúdu, pred týmto uzatváracím kolom) — nie
v tejto konkrétnej relácii, ktorá tento report opravuje. **Presný kombinovaný
príkaz bol teda doteraz úspešne otestovaný presne raz (n=1), nie v oboch
reláciách zvlášť.**

**Presné znenie zisťovania, nič viac netvrdím:** tento úspešný beh
**nedokazuje, že sa pôvodne pozorovaný hang v `docker-buildx bake`
finalizácii nemôže zopakovať** — dokazuje len, že sa v tomto konkrétnom
behu (na tomto stroji, v tomto stave Docker Desktop/buildx) nezopakoval.
Pôvodný report (Stage 2A, sekcia 8.1) reprodukoval hang 3/3× pri predošlom
testovaní: aktuálny úspešný beh je preto vzorka `n=1` proti predošlej
vzorke `n=3` zlyhaní, nie vyvrátenie pôvodného zistenia. `README.md`
zostáva nezmenený v tejto veci (dokumentovaný workaround ostáva odporúčaný
default postup), keďže jeden úspešný beh nie je dostatočný dôkaz na zmenu
odporúčania.

### 6.2 Reálna browser validácia (`claude-in-chrome`, pripojený v predchádzajúcej relácii)

Kolo 1 (uvedené v predošlom finálnom reporte tohto stage, nie v samostatnom
súbore) malo tento nástroj nedostupný a nahradilo ho skriptovaným `curl`
flow — to bolo explicitne označené ako NOT VERIFIED pre skutočné DOM
správanie. Kolo 2 (relácia bezprostredne predchádzajúca tomuto
uzatváraciemu kolu) to doplnilo:

| Kontrola | Výsledok | Poznámka |
|---|---|---|
| Anonymná home (`/`) | **PASS** | Zobrazuje `Login` odkaz, žiadny chránený obsah, žiadny flash |
| Priama navigácia na `/protected` (anonymne) | **PASS** | "You must be signed in to view this page." + `Login`; sieťový log potvrdil, že **`/api/protected/hello` nebolo vôbec zavolané** (len `/api/auth/me` → `401`) — potvrdzuje, že guard je čisto UX a appka sa ani nepokúša o chránené dáta bez session |
| Reálny login cez Keycloak (`test-user` / heslo z `keycloak/import/bff-demo-realm.json`) | **PASS** | Kliknutie na `Login` → skutočný redirect na `http://localhost:8081/realms/bff-demo/...` s `code_challenge`/`code_challenge_method=S256`/`state`/`nonce` → vyplnenie a odoslanie reálneho Keycloak login formulára → návrat presne na `http://localhost:5173/` s textom "Signed in as Test User" |
| `/protected` po prihlásení | **PASS** | Zobrazuje "Protected" / "authenticated" — reálne dáta z `GET /api/protected/hello` |
| Refresh (`navigate` na tú istú URL) na `/protected` v authenticated stave | **PASS** | Stránka sa znova načíta cez SPA fallback a znova zobrazí "authenticated" bez straty stavu |
| `localStorage` prázdny (anonymný aj authenticated stav) | **PASS** | `Object.keys(localStorage)` → `[]` v oboch stavoch, priamo overené cez `javascript_tool` |
| `document.cookie` priamy JS prístup | **NOT VERIFIED priamym čítaním** | Nástroj `claude-in-chrome` sám blokuje čítanie (`[BLOCKED: Cookie access]`) ako vlastné bezpečnostné opatrenie extension-u — nie je to aplikačné správanie. Nezávisle od tohto blocku bol `HttpOnly` atribút `JSESSIONID` cookie potvrdený priamo zo `Set-Cookie` hlavičky (`curl -i`) v predchádzajúcom kole — to je samostatný dôkaz **len pre cookie**, prečo by aj skutočný útočníkov JS `document.cookie` nikdy nevidel jej hodnotu |
| `sessionStorage` priamy JS prístup | **NOT VERIFIED** | Nástroj `claude-in-chrome` blokuje čítanie (`[BLOCKED: Sensitive key]`) ako vlastné bezpečnostné opatrenie — **nie je jasné, či ide o blokovanie na základe obsahu, alebo len na základe názvu kľúča `sessionStorage` v návratovom objekte**; nebolo to v tomto ani predchádzajúcom kole rozlíšené. **Dôsledok tejto relácie (pozri sekciu 6.3): `HttpOnly` stav `JSESSIONID` cookie nie je náhradným dôkazom pre `sessionStorage`** — sú to dva nezávislé úložiská (cookie sa spravuje prehliadačom/HTTP vrstvou, `sessionStorage` je JS-dostupné API, ktoré appka mohla teoreticky použiť na uloženie čohokoľvek) a záver o jednom nemožno preniesť na druhé. Zostáva explicitne NOT VERIFIED, kým sa nepodarí buď pripojiť `claude-in-chrome` a nájsť nezablokovanú cestu čítania, alebo overiť iným povoleným nástrojom |
| API autentifikácia cez session cookie (nie Bearer token) | **PASS** | Zdrojový kód (`SessionContext.tsx`, `ProtectedPage.tsx`) používa výhradne `fetch(url, { credentials: "same-origin" })`, nikde sa nenastavuje `Authorization` hlavička; sieťový log potvrdil funkčné `200` odpovede bez akéhokoľvek vlastného auth headera |

#### Simulácia straty session (BFF session cookie)

**Dôležitá poznámka k metodike:** `JSESSIONID` je `HttpOnly`, takže ju nie je
možné vymazať cez stránkový JavaScript (`document.cookie` zápis nemôže
prepísať/vymazať existujúcu `HttpOnly` cookie rovnakého mena — je to
zámerné bezpečnostné obmedzenie prehliadača, nie limit tohto nástroja).
`claude-in-chrome` ani neexponuje žiadny cookie-manažment nástroj a
`chrome://` stránky (Settings → Cookies) nie sú touto automatizáciou
dosiahnuteľné ("Can't interact with browser-internal or unparseable
URLs").

Namiesto toho bola strata session simulovaná **funkčne rovnocenným,
reálnejším spôsobom**: `docker compose restart backend`. Toto vyprázdni
in-memory `HttpSession` store (Spring Security default, nepersistovaný),
takže konkrétny `JSESSIONID`, ktorý prehliadač stále posiela, sa stane pre
backend neplatným — presne to isté funkčné dôsledok, ako keby session
cookie vypršala alebo bola invalidovaná na serveri, len bez nutnosti
obchádzať `HttpOnly` ochranu prehliadača. Prehliadač počas celého testu
**nedostal žiadnu inštrukciu vymazať/zmeniť cookie** — jediná zmena bola na
strane servera.

Postup a výsledok:

1. Stav pred: karta na `http://localhost:5173/protected`, zobrazené
   "Protected" / "authenticated" (reálne dáta z predošlého prihlásenia).
2. `docker compose restart backend` → kontajner `healthy` do ~6 sekúnd.
   Karta v prehliadači sa **nemenila** (žiadny reload, žiadna akcia v UI) —
   React stále držal stale `authenticated` stav v pamäti.
3. Sieťový log vynulovaný, následne `window.dispatchEvent(new
   Event('focus'))` + `document.dispatchEvent(new Event('visibilitychange'))`
   spustené cez `javascript_tool` — toto je syntetický, ale legitímny
   spôsob, ako v automatizovanom teste vyvolať presne tú istú vetvu kódu,
   ktorú by spustil skutočný alt-tab/refocus používateľa (appka počúva na
   `focus`/`visibilitychange` eventy, nie na skutočný OS-level fokus).
4. **Výsledok, priamo pozorovaný**:
   - Sieťový log: `GET /api/auth/me` → **`401`** (dvakrát, raz na `focus`,
     raz na `visibilitychange` listener — očakávané, nejde o polling, len o
     dva nezávislé listenery na jednu simulovanú udalosť).
   - UI sa okamžite zmenil na "You must be signed in to view this page." +
     `Login` — **stale "authenticated"/"Protected" obsah zmizol**.
   - **URL zostala `http://localhost:5173/protected`** — žiadny redirect,
     žiadna slučka, žiadna navigácia.
   - Konzola bez chýb/výnimiek (`read_console_messages`, `onlyErrors: true`).
5. Overené aj na home page (`/`) po tej istej invalidácii — zobrazuje
   `Login`, žiadny stale používateľ.
6. Následné kliknutie na `Login` (Keycloak SSO cookie na porte `8081` bola
   stále platná — backend restart sa jej netýka) prebehlo **bez zobrazenia
   Keycloak login formulára** (tiché SSO) a vrátilo naspäť na `/` s
   "Signed in as Test User" — potvrdzuje, že celý flow je reprodukovateľný
   opakovane, nie jednorazovo.

**PASS** — presne požadované správanie: po strate BFF session sa pri
refocuse vyčistí používateľ aj chránené dáta, bez presmerovacej slučky.

#### Vedľajšie pozorovanie (nie nález, tooling)

Pri prvom pokuse o klik na `Login` odkaz hneď po `navigate` (súradnicový
klik tesne po načítaní stránky) sa navigácia nespustila — pravdepodobne
automatizačný nástroj klikol skôr, než React plne hydratoval event
listener na danom mieste. Klik cez element `ref` (z `read_page`) fungoval
okamžite a pri neskorších pokusoch fungoval aj súradnicový klik bez
problému. Toto je nezávislé od Stage 2B kódu — nejde o aplikačný defekt,
je to poznámka pre budúce browser testovanie tohto repozitára (uprednostniť
`ref`-based klik alebo krátke `wait` po `navigate` pred klikom na odkaz
tesne po načítaní stránky).

### 6.3 Uzatváracie kolo pred Stage 2C: `sessionStorage`, runtime logy, cielený re-review

Toto kolo bolo vyžiadané explicitne na doriešenie štyroch konkrétnych
otvorených bodov pred začatím Stage 2C. Vykonala ho **táto relácia** (t. j.
relácia nasledujúca po tej, ktorá dokončila browser validáciu v sekcii 6.2).

#### `sessionStorage` — pokus o overenie povoleným spôsobom

`claude-in-chrome` bol na začiatku tohto kola **odpojený** (nie len
blokujúci konkrétne čítanie ako v predošlom kole — `tabs_context_mcp`
vracal "Browser extension is not connected"). Pripojenie bolo skúsené
opakovane (3×) v priebehu tohto kola, vždy s rovnakým výsledkom. V súlade s
pokynom nebolo pokračované v ďalších opakovaných pokusoch.

**Výsledok: `sessionStorage` zostáva NOT VERIFIED.** Dôvod je tentoraz
nedostupnosť nástroja, nie (len) jeho vlastný content-based blok z
predošlého kola. Ani jeden dôvod nezakladá nárok na náhradný dôkaz —
konkrétne, `HttpOnly` stav `JSESSIONID` cookie (potvrdený v predošlých
kolách) **sa v tomto reporte explicitne nepoužíva ako dôkaz o
`sessionStorage`**, keďže ide o nezávislé úložiská s odlišným
bezpečnostným mechanizmom (cookie flag vs. JS-dostupné API). Ak sa
`claude-in-chrome` v budúcom kole pripojí, odporúčaný ďalší krok je
zopakovať `Object.keys(sessionStorage)` a — ak nástroj opäť vráti
`[BLOCKED: Sensitive key]` — overiť, či je blok viazaný na názov kľúča
`sessionStorage` v návratovej hodnote (čo by bol tooling false-positive na
prázdnom poli) alebo na skutočný obsah; toto rozlíšenie sa v tomto ani
predošlom kole nepodarilo urobiť.

#### Runtime logy backendu a Keycloaku — kontrola úniku citlivých údajov

Vykonané priamo touto reláciou, bez tlačenia skutočných hodnôt do tohto
reportu (nižšie sú len počty zhôd a metodika, nie nájdené dáta — pretože
nič nájdené nebolo).

- **Rozsah logov**: `docker logs spring-keycloak-bff-backend-1` (46 riadkov,
  pokrývajúcich pôvodný štart pri `docker compose up --build`, celý reálny
  prihlasovací flow vykonaný v sekcii 6.2 pred reštartom backendu, i štart
  po reštarte) a `docker logs spring-keycloak-bff-keycloak-1` (20 riadkov,
  Keycloak kontajner nebol počas Stage 2B nikdy reštartovaný, teda pokrýva
  celý beh). Oba logy boli prečítané v plnom rozsahu (nie len `tail`).
- **Metodika**: `grep -ci` (case-insensitive počet zhôd, nie výpis
  hodnôt) pre vzory: `access_token`, `refresh_token`, `id_token`,
  `client_secret`, `JSESSIONID`, `Authorization: Bearer`, `password`,
  `KEYCLOAK_IDENTITY`, `code=`, `session_state`, `Set-Cookie`.
- **Výsledok: PASS — 0 zhôd pre všetky vzory v oboch logoch.**
- **Vysvetlenie (overené priamym prečítaním obsahu, nie len grepom)**:
  Spring Boot na predvolenej `INFO` úrovni nemá zapnutý žiadny
  request/response access log ani logovanie OAuth2 výmeny (žiadny vlastný
  logging kód pre tieto hodnoty neexistuje — potvrdené aj v Stage 2A
  finálnom reviewe). Keycloak v dev móde na `INFO` úrovni loguje len
  bootstrap/štart (Quarkus augmentation, realm import, verzie), nie
  jednotlivé autentifikačné udalosti (event logging do konzoly nie je
  predvolene zapnutý). Preto v logoch **nie je čo uniknúť** pri súčasnej
  (predvolenej) konfigurácii — nejde o to, že by citlivé hodnoty boli
  logované a následne redigované, ale o to, že sa tam vôbec nezapisujú.
- **NOT VERIFIED**: obsah logov pred reštartom backendu (07:34–07:37) bol
  súčasťou toho istého log súboru (Docker `restart` nezmazal predchádzajúci
  log, len naň nadviazal) — to bolo priamo overené z časových pečiatok v
  logu (posledný riadok pred `GracefulShutdown` na `07:37:45`, prvý riadok
  po reštarte na `07:37:46`). Nebolo však nezávisle overené, či Docker
  log driver (`json-file`) mohol log pri reštarte ticho rotovať/orezať bez
  chyby — spoliehame sa na kontinuitu časovej osi ako nepriamy dôkaz, nie
  na explicitné potvrdenie od log drivera.

#### Cielený re-review race condition opravy (`bff-security-architect`)

Vyžiadaný explicitne, so zúženým rozsahom len na dva súbory
(`frontend/src/auth/SessionContext.tsx` a
`frontend/src/auth/SessionContext.test.tsx`), bez opakovania celého Stage
2B reviewu.

**Overil: `bff-security-architect` (samostatná, cielená invokácia v tomto
uzatváracom kole).**

**Výsledok: PASS.** Doslovný záver agenta: *"the fix in
`frontend/src/auth/SessionContext.tsx` correctly closes the TOCTOU race,
and the regression test in `frontend/src/auth/SessionContext.test.tsx`
genuinely exercises that fix (it is not tautological; it would fail
against the original, unfixed code)."*

Kľúčové body z jeho analýzy:

- Druhá `generationRef.current !== generation` kontrola je umiestnená presne
  medzi `await response.json()` a oboma `setSession` vetvami — presne tam,
  kde to MEDIUM nález vyžadoval.
- Skontroloval aj ostatné vetvy (`401`, fallback `error`, `.catch`,
  `reportUnauthorized`, `refresh`, focus/visibilitychange efekt) na iné
  neošetrené `await` medzery — žiadna nenájdená.
- Test genuinely simuluje presne opísaný race (kontrolovateľné `response.json()`
  nezávisle od vonkajšieho `fetch()` promise, `reportUnauthorized()` zavolaný
  presne v zraniteľnom okne) a jeho výsledok je preukázateľne závislý od
  opravy (bez opravy by finálne tvrdenie zlyhalo) — nejde o tautologický test.
- **NOT VERIFIED touto agentskou invokáciou**: samotné spustenie testu
  (`npm test`) nebolo agentom vykonané, keďže má len read-only nástroje —
  jeho záver je založený na statickej analýze poradia `await`/microtask, nie
  na behovom dôkaze. Skutočné spustenie testu (PASS, vrátane overenia, že
  zlyháva bez opravy) bolo už vykonané a zdokumentované orchestrujúcou
  reláciou v sekcii 5 tohto reportu — táto agentská invokácia to nezávisle
  nepotvrdila behovo, len staticky.
- Žiadny ďalší nález, žiadne riziko, žiadna schválená výnimka.

---

## 7. Finálny bezpečnostný review

Vykonal `bff-security-architect` nad skutočným kódom po implementácii.

**Výsledok: PASS WITH FINDINGS → PASS po oprave** (jediný nález, sekcia 5,
opravený a overený vlastným regresným testom; oprava aj test dodatočne
cielene re-overené tým istým agentom v samostatnej invokácii — sekcia 6.3,
**Výsledok: PASS**).

Všetky MUST-úrovňové invarianty pre Stage 2B (token custody, `/api/auth/me`
minimálna schéma, `no-store`, `401` namiesto redirectu pre API cesty, fixný
post-login redirect, žiadny frontend OAuth kód, UX-only route guard so
zachovaným server-side enforcementom) sú v aktuálnom kóde splnené — potvrdené
statickou inšpekciou aj runtime dôkazom (curl + reálny prehliadač).

### Riziká / predpoklady

Žiadne nové oproti Stage 2A. Otvorené položky zo Stage 2A (napr. chýbajúci
explicitný `SameSite` na session cookie) zostávajú nezmenené — mimo rozsahu
Stage 2B.

### Not verified

- Presná interná príčina historického `docker-buildx bake` hangu — stále
  nepotvrdená (žiadny debug prístup k danému procesu); pozri sekciu 6.1 pre
  presné rozlíšenie, čo presne jeden úspešný beh dokazuje a čo nie.
- `sessionStorage` priamy JS-based obsah — stále NOT VERIFIED; pozri sekciu
  6.3 pre presný dôvod (nástroj nedostupný/blokujúci v oboch pokusoch) a pre
  explicitné odmietnutie použiť `HttpOnly` stav cookie ako náhradný dôkaz.
- Kontinuita Docker `json-file` log driveru cez `docker compose restart`
  (t. j. že log naozaj nebol ticho orezaný) — overené len nepriamo, cez
  časovú os v logu, nie potvrdením od log drivera samotného (sekcia 6.3).

**Doriešené v tomto uzatváracom kole (už nie NOT VERIFIED):**

- Obsah runtime logov backendu a Keycloaku bol priamo prečítaný v plnom
  rozsahu a prehľadaný na 11 vzorov citlivých hodnôt — **0 zhôd** (sekcia
  6.3). Predtým bola táto kontrola len odvodená zo statickej kontroly
  zdrojového kódu; teraz je potvrdená priamou inšpekciou behúcich logov.

### Schválené / zdokumentované výnimky

Žiadne nové.

---

## 8. Zámerne neimplementované (odložené na neskoršie etapy)

Nezmenené oproti `README.md` "Not implemented in this stage": logout, plná
SPA CSRF token plumbing, USER/ADMIN role mapping, role-based autorizácia,
explicitné spracovanie refresh tokenu, session concurrency limity,
brute-force tuning, MFA/WebAuthn, registrácia, reset hesla, business
funkcionalita, perzistencia aplikačných používateľov, vlastná login
stránka, návrat na pôvodne požadovanú chránenú route po logine.

Nič z uvedeného nebolo reportované ako defekt Stage 2B.

---

## 9. Zostávajúce problémy

Žiadny CRITICAL/HIGH/MEDIUM nález nezostáva otvorený — jediný nájdený
MEDIUM nález (race condition, sekcia 5) bol opravený, overený vlastným
regresným testom, a dodatočne cielene re-overený nezávislou invokáciou
`bff-security-architect` (sekcia 6.3, **Výsledok: PASS**, žiadny ďalší
nález v opravených súboroch).

Neblokujúce poznámky prenesené zo Stage 2A, stále platné:

1. **LOW** — chýbajúci explicitný `SameSite` na session cookie. Mimo
   rozsahu Stage 2B, odporúčané doriešiť pri produkčnom cookie hardeningu.
2. Presná príčina historického `docker-buildx bake` hangu zostáva
   nepotvrdená; dokumentovaný dvojkrokový workaround v `README.md` zostáva
   odporúčaným defaultom napriek tomu, že presný kombinovaný príkaz bol raz
   (n=1, nie v oboch reláciách) úspešne otestovaný bez zaseknutia (sekcia 6.1).

---

## 10. Výsledný stav overenia

**Overené (s konkrétnym dôkazom, PASS):**

- Backend testy 10/10, frontend testy 12/12, frontend build — všetky
  spustené priamo touto reláciou.
- Race condition v `SessionContext.tsx` nájdený finálnym security review,
  opravený, overený vlastným regresným testom (zlyhá bez opravy, prejde s
  opravou), a **dodatočne cielene re-overený nezávislou invokáciou
  `bff-security-architect`** v uzatváracom kole (sekcia 6.3) — verdikt
  **PASS**, oprava aj test uznané za genuine (netautologické).
- Presný príkaz `docker compose up --build` prebehol bez zaseknutia — **raz
  (n=1)**, nie v oboch reláciách (opravená nezrovnalosť, sekcia 6.1); nejde
  o dôkaz trvalej opravy historického problému.
- **Reálna browser validácia** (`claude-in-chrome`): anonymná home a
  `/protected` gate bez chránených dát; kompletný reálny Keycloak login
  flow s PKCE; deterministický návrat na `/`; `/protected` s reálnymi
  dátami; refresh funguje; simulovaná strata BFF session (cez reštart
  backendu, keďže `JSESSIONID` je `HttpOnly` a nedá sa vymazať zo
  stránkového JS ani cez dostupné nástroje) vyčistí používateľa aj chránené
  dáta na refocus, bez presmerovacej slučky; `localStorage` prázdny v oboch
  stavoch; API autentifikácia potvrdená ako výhradne cookie-based (žiadny
  `Authorization` header v kóde).
- **Runtime logy backendu a Keycloaku** (celý rozsah, priamo prečítané v
  tomto uzatváracom kole): 0 zhôd pre 11 vzorov citlivých hodnôt
  (`access_token`, `refresh_token`, `client_secret`, `JSESSIONID`,
  `password`, ...) — sekcia 6.3.

**NOT VERIFIED (chýba priamy dôkaz, nie je to ale CONFIRMED FINDING):**

- Presná interná príčina historického `docker-buildx bake` hangu.
- **`sessionStorage` priamy JS-based obsah** — nástroj `claude-in-chrome`
  bol v uzatváracom kole nedostupný (3 pokusy o pripojenie), predtým
  blokoval čítanie vlastným bezpečnostným opatrením bez jasného rozlíšenia,
  či ide o content-based alebo key-name-based blok. **Explicitne
  nenahradené** `HttpOnly` stavom `JSESSIONID` cookie — ide o nezávislé
  úložiská, záver o jednom sa nesmie preniesť na druhé (sekcia 6.3).
- `document.cookie` priamy JS-based obsah zostáva tiež NOT VERIFIED tým
  istým spôsobom (nástroj blokuje), ale má vlastný nezávislý nepriamy dôkaz
  (`Set-Cookie: ... HttpOnly` hlavička z `curl -i`), ktorý sa netýka
  `sessionStorage`.
- Kontinuita Docker `json-file` log driveru cez `docker compose restart`
  (overené len nepriamo cez časovú os v logu).

**Nič nebolo zmazané, commitnuté ani pushnuté.** Docker stack zostáva
bežať (`docker compose ps` → všetky 4 služby `healthy`) pre prípadné ďalšie
manuálne overenie.
