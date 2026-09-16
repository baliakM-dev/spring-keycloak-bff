# Stage 2A — Finálny report: OAuth2/OIDC login cez Spring Boot BFF

Repozitár: `spring-keycloak-bff`, branch `feature/oauth2-login`.
Stav: implementácia dokončená, runtime overená, **nič nebolo commitnuté ani pushnuté**.

---

## 1. Pre-implementačný architektonický review

Vykonal `bff-security-architect` pred začatím implementácie.

**Výsledok: PASS** — žiadny blokujúci nález (v tom čase pre Stage 2A ešte neexistoval žiadny kód, preto šlo hlavne o záväzné implementačné obmedzenia, nie o nálezy).

Kľúčové záväzné obmedzenia odovzdané `bff-implementer`-ovi:

- Použiť manuálny `ClientRegistrationRepository` bean namiesto YAML `issuer-uri` autodiscovery. Dôvod: Keycloak má fixný (browser-facing) issuer `http://localhost:8081/realms/bff-demo`, ale backend kontajner musí volať token/userinfo/JWK endpointy cez Docker-interný hostname `http://keycloak:8080/...` — autodiscovery (`ClientRegistrations.fromIssuerLocation`) by pri štarte zlyhala na nezhode `issuer` poľa.
- `scope` registrácie musí obsahovať `openid` (inak žiadny ID token, žiadna nonce validácia, žiadny `OidcUser`).
- Explicitné PKCE (S256) cez `OAuth2AuthorizationRequestCustomizers.withPkce()` ako defense-in-depth nad confidential clientom.
- Literal `redirect-uri` (`http://localhost:5173/login/oauth2/code/bff-app`) namiesto `{baseUrl}` placeholderu — kvôli citlivosti na Host hlavičku pri proxy.
- Nové `/oauth2` a `/login` proxy cesty v `frontend/vite.config.ts` aj `frontend/nginx.conf`, v rovnakom tvare ako existujúce `/api` — žiadna CORS konfigurácia nie je potrebná (všetko same-origin cez proxy).
- Štátny state/nonce, token custody a CSRF nechať výhradne na framework default — žiadny vlastný kód.
- Overiť za behu, či `sslRequired: "external"` v realm importe neblokuje token exchange z backend kontajnera (non-loopback IP); ak áno, zmeniť na `"none"` (dev-only realm).

---

## 2. Vytvorené súbory

- `backend/src/main/java/com/example/bff/config/OAuth2ClientConfig.java` — manuálny `ClientRegistrationRepository` bean (browser-facing vs. backend-facing URI split, explicitný `issuerUri`)
- `backend/src/main/java/com/example/bff/api/ProtectedController.java` — `GET /api/protected/hello`
- `backend/src/test/java/com/example/bff/api/ProtectedControllerTest.java` — anonymný/authenticated/regresný test
- `frontend/src/App.test.tsx` — test Login odkazu

## 3. Upravené súbory

- `backend/src/main/java/com/example/bff/config/SecurityConfig.java` — pridané `oauth2Login()` + PKCE resolver; pôvodný default-deny baseline (`/api/public/**`, `/actuator/health` permitAll, zvyšok `authenticated()`) a CSRF (default enabled) zostali nedotknuté
- `backend/src/main/resources/application.yml` — `bff.oauth2.*` properties, hodnoty naviazané na env premenné
- `keycloak/import/bff-demo-realm.json` — oprava `redirectUris` na `http://localhost:5173/login/oauth2/code/bff-app`, pridaný disponibilný `test-user` (bez role mappingu)
- `compose.yaml` — OAuth2 env premenné (vrátane client secretu) pridané výhradne do `backend` service bloku; `backend` teraz `depends_on: keycloak: condition: service_healthy`; Keycloak hostname migrovaný z deprecated V1 syntaxe (`KC_HOSTNAME=localhost` + `KC_HOSTNAME_PORT`) na V2 (`KC_HOSTNAME: http://localhost:8081`) — nutná oprava issuer identity, popísaná v sekcii 8
- `frontend/src/App.tsx` — pridaný Login odkaz (real browser navigation, žiadny fetch/axios)
- `frontend/vite.config.ts` — nové `/oauth2`, `/login` proxy záznamy (bez `changeOrigin`, na rozdiel od `/api`)
- `frontend/nginx.conf` — nové `/oauth2`, `/login` proxy bloky; dodatočne opravený reálny bug (pozri sekciu 8): `proxy_set_header Host $host;` → `proxy_set_header Host $http_host;` vo všetkých troch blokoch
- `README.md` — Stage 2A dokumentácia + zdokumentovaný a overený `docker compose up --build` workaround

**Mimo rozsahu tejto úlohy:** v pracovnom strome sa nachádzajú 4 netrackované súbory (`implementation/ETAPA2.md`, `ETAPA2B.md`, `ETAPA2C.md`, `ETAPA3.md`) — plány pre nasledujúce etapy, ktoré táto relácia nevytvorila ani needitovala (pravdepodobne z paralelnej relácie pracujúcej na tom istom repozitári). Zostali nedotknuté.

---

## 4. Autentifikačná architektúra (skutočne implementovaná)

```text
React "Login" (<a href>, reálna navigácia prehliadača)
        │
        ▼
GET /oauth2/authorization/bff-app   (nginx/Vite proxy → backend)
        │
        ▼
Spring vygeneruje authorization request (state, nonce, PKCE S256)
        │  redirect
        ▼
Keycloak (http://localhost:8081, browser-facing)  — autentifikácia používateľa
        │  redirect s authorization code
        ▼
http://localhost:5173/login/oauth2/code/bff-app   (literal redirect URI)
        │
        ▼
Spring OAuth2 callback (cez nginx/Vite proxy):
  - token exchange + userinfo → http://keycloak:8080 (backend-facing, Docker DNS)
  - ID token issuer validovaný voči http://localhost:8081 (browser-facing, fixný Keycloak issuer)
        │
        ▼
Spring SecurityContext + HttpSession
  (OAuth2AuthorizedClient — access/refresh token server-side;
   InMemoryOAuth2AuthorizedClientService + AuthenticatedPrincipalOAuth2AuthorizedClientRepository,
   nie HttpSession — presný mechanizmus a dôkaz pozri sekciu 6)
        │
        ▼
Browser dostane iba JSESSIONID cookie (HttpOnly)
        │
        ▼
GET /api/protected/hello  →  {"message": "authenticated"}
```

React v žiadnom kroku nevidí authorization code, access token, refresh token, ID token ani client secret.

---

## 5. Keycloak konfigurácia

| Položka | Hodnota |
|---|---|
| Realm | `bff-demo` |
| Client ID | `bff-app` |
| Typ klienta / autentifikácia | confidential (`publicClient: false`), `clientAuthenticatorType: client-secret` |
| Standard Flow (Authorization Code) | `enabled` |
| Direct Access Grants | `disabled` |
| Implicit Flow | `disabled` |
| Service Accounts | `disabled` |
| Redirect URIs | `http://localhost:5173/login/oauth2/code/bff-app` |
| Web Origins | `http://localhost:5173` |
| PKCE | enabled na strane Spring (S256) — **runtime overené** v reálnej redirect URL (`code_challenge` a `code_challenge_method=S256` prítomné) |
| `sslRequired` | `external` (ponechané; token exchange z backend kontajnera cez loopback/host overený ako funkčný) |
| Client secret | hodnota redigovaná; existuje výlučne ako env premenná pre `backend` service, nikdy pre `frontend` |
| Test používateľ | `test-user`, heslo jasne označené ako `LOCAL DEVELOPMENT ONLY` (redigované), bez realm/client role mappingu |

---

## 6. Token custody

**Korekcia oproti pôvodnému reportu**: pôvodný text tvrdil, že sa používa "predvolený `HttpSessionOAuth2AuthorizedClientRepository`". Toto bolo odvodené iba z neprítomnosti vlastného beanu, nie z toho, čo Spring Boot autoconfigurácia pre túto konkrétnu konfiguráciu (manuálny `ClientRegistrationRepository` bean, žiadny vlastný `OAuth2AuthorizedClientService`/`Repository`) skutočne vyrába. Po overení proti presnému zdrojovému kódu rozlíšených verzií závislostí (`spring-boot-security-oauth2-client:4.1.1`, `spring-security-oauth2-client:7.1.1`, stiahnuté cez `./mvnw dependency:sources`, teda presne to, čo tento build projektu reálne používa) je skutočný mechanizmus iný:

| Vrstva | Skutočne použitá implementácia | Dôkaz |
|---|---|---|
| `OAuth2AuthorizedClientService` bean | `InMemoryOAuth2AuthorizedClientService` — `ConcurrentHashMap<OAuth2AuthorizedClientId, OAuth2AuthorizedClient>`, kľúč = `(registrationId, principal.getName())` | `OAuth2ClientConfigurations.OAuth2AuthorizedClientServiceConfiguration` (`spring-boot-security-oauth2-client-4.1.1-sources.jar`, `org/springframework/boot/security/oauth2/client/autoconfigure/OAuth2ClientConfigurations.java:56-67`) — `@ConditionalOnBean(ClientRegistrationRepository.class)` + `@ConditionalOnMissingBean`; podmienka je splnená, lebo `OAuth2ClientConfig.java` definuje `ClientRegistrationRepository` bean a nikde v `backend/src/main` sa nedefinuje vlastný `OAuth2AuthorizedClientService` (overené `grep -rn "OAuth2AuthorizedClient" backend/src/main` — jediný výskyt bol pôvodný nesprávny Javadoc komentár, teraz opravený) |
| `OAuth2AuthorizedClientRepository` bean | `AuthenticatedPrincipalOAuth2AuthorizedClientRepository`, obaľujúci vyššie uvedený `InMemoryOAuth2AuthorizedClientService` | `OAuth2ClientWebSecurityAutoConfiguration` (`.../autoconfigure/servlet/OAuth2ClientWebSecurityAutoConfiguration.java:50-59`) — `@ConditionalOnBean(OAuth2AuthorizedClientService.class)` + `@ConditionalOnMissingBean`; podmienka splnená vyššie uvedeným auto-registrovaným service beanom |
| Skutočné úložisko pre autentifikovaného principala | **aplikačná (JVM) pamäť**, kľúčovaná menom principala + registration ID — **nie `HttpSession`** | `InMemoryOAuth2AuthorizedClientService.java:42-56` (pole `authorizedClients` typu `Map<OAuth2AuthorizedClientId, OAuth2AuthorizedClient>`, `ConcurrentHashMap`); `AuthenticatedPrincipalOAuth2AuthorizedClientRepository.java:77-96` — pre autentifikovaného principala (`isPrincipalAuthenticated(principal)` == true, čo platí pre našu post-login session) `loadAuthorizedClient`/`saveAuthorizedClient`/`removeAuthorizedClient` delegujú priamo na tento service, **nie** na vnútorný `HttpSessionOAuth2AuthorizedClientRepository` |
| Kedy sa `HttpSessionOAuth2AuthorizedClientRepository` skutočne použije | Iba ako `anonymousAuthorizedClientRepository` fallback pre neautentifikovaný/anonymný request (typicky počas samotného login-flow, kým principal ešte nie je plne autentifikovaný) | `AuthenticatedPrincipalOAuth2AuthorizedClientRepository.java:51,83,93,105` |

Dôsledok pre Stage 2A (informatívne, nie CONFIRMED FINDING — žiadny MUST invariant nie je porušený, tokeny stále nikdy neopúšťajú server): `OAuth2AuthorizedClient` (vrátane access/refresh tokenu) je viazaný na meno prihláseného principala v pamäti JVM backend procesu, nie na konkrétnu HTTP session. Pri reštarte aplikácie sa táto mapa vyprázdni (očakávané, žiadna perzistencia bola implementovaná ani požadovaná pre Stage 2A). Pri budúcom logout-e (Stage 2C) treba počítať s tým, že invalidácia `HttpSession` sama osebe nevymaže záznam v tejto mape — vyžaduje explicitné `removeAuthorizedClient` volanie.

Overené:
- **staticky, s presným zdrojovým dôkazom** — pozri tabuľku vyššie; opravený bol aj nesprávny Javadoc komentár v `backend/src/main/java/com/example/bff/config/SecurityConfig.java`, ktorý predtým tvrdil opak,
- **runtime** — v žiadnej odpovedi dostupnej prehliadaču (HTML, JS bundle, `/api/protected/hello` telo), v `localStorage` ani `sessionStorage` sa nenašla žiadna token-like hodnota (toto potvrdzuje, že tokeny neopúšťajú server, bez ohľadu na to, ktorá z týchto dvoch implementácií sa použije).

**NOT VERIFIED**: samotná Spring kontextová inštancia beanov nebola priamo dotazovaná za behu (napr. cez `/actuator/beans`, ktorý nie je v tomto stage exponovaný, čo je zámerné — pridávať ho len na účely tejto verifikácie by bola zmena konfigurácie mimo schváleného rozsahu). Záver vyššie je založený na presnej zhode: (a) skutočne rozlíšenej verzie autoconfiguračného kódu tohto projektu a (b) potvrdenej neprítomnosti akéhokoľvek prepisujúceho beanu v aktuálnom zdrojovom kóde — nie na behovej introspekcii bean grafu.

---

## 7. Testy

| Príkaz | Výsledok | Kto overil |
|---|---|---|
| `cd backend && ./mvnw test` | **PASS** — 5/5 (`BffApplicationTests`, `PublicControllerTest`, `ProtectedControllerTest` ×3) | `bff-implementer` (reportované, nebolo znovu spúšťané orchestrujúcou reláciou) |
| `cd frontend && npm test` | **PASS** — 2 súbory, 3/3 testov | `bff-implementer` |
| `cd frontend && npm run build` | **PASS** (`tsc -b && vite build`); overené, že zbuildovaný bundle obsahuje `oauth2/authorization/bff-app` | `bff-implementer` |
| `docker compose config` | **PASS** (exit 0) | `bff-implementer` |

Testové scenáre backendu: anonymný prístup na `/api/public/hello` (regresia), anonymný prístup na `/api/protected/hello` (nesmie vrátiť authenticated telo), simulovaný OIDC authenticated používateľ na `/api/protected/hello` (musí vrátiť `{"message":"authenticated"}`).

---

## 8. Runtime validácia

### 8.1 Docker build/up

**Status príkazu `docker compose up --build`: stále nefunkčný (FAIL).** Toto zostáva v platnosti aj po diagnostike — nebol nájdený spôsob, ako ho spustiť priamo tak, aby fungoval, len overená dvojkroková obchádzka (nižšie).

#### Pozorované správanie (priamo overené, opakovane)

| Krok | Výsledok |
|---|---|
| `docker compose build` (samostatne, `--progress=plain`) | **FAIL / hang** — reprodukované 3/3× (vždy > 5 minút bez návratu). BuildKit progress log v každom pokuse ukázal, že build reálne dobehol pre OBA servisy (`naming to docker.io/library/spring-keycloak-bff-backend:latest done` **a** `naming to docker.io/library/spring-keycloak-bff-frontend:latest done`), čo bolo nezávisle potvrdené aj zhodou `docker images` timestampov s časom behu | 
| `docker compose up --build` (presná reprodukcia nahláseného problému, z čistého stavu — `docker compose down`, potom `up --build`) | **FAIL / hang** — build log opäť ukázal kompletné dokončenie oboch images, ale `docker compose ps` počas celého čakania ukazoval prázdny zoznam (žiadny kontajner nebol vytvorený) |
| `COMPOSE_BAKE=false docker compose build` (alternatívny pokus o obídenie) | **FAIL / hang**, ale inak — build sa zasekol *medzi* dokončením backendu a začatím frontendu (backend export dobehol, frontend log neobsahoval ani prvý riadok), nulová CPU aktivita procesu niekoľko minút |

Vo všetkých troch prípadoch bolo nutné zaseknutý proces manuálne ukončiť (`kill` na PID `docker compose build`/`docker compose up`/`docker-buildx bake` procesov, zistené cez `ps aux`) — bez zásahu by čakanie pokračovalo neurčito.

#### Predpokladaná príčina (odvodená, nie priamo dokázaná)

Pozorovania sú konzistentné s hangom v post-build finalizácii `docker-buildx bake` subprocesu, ktorý `docker compose` interne spúšťa (`docker-buildx bake --file - --progress rawjson --metadata-file ...`): jeho `--metadata-file` nebol nikdy zapísaný (overené — súbor v danej ceste neexistoval po zabití procesu), hoci BuildKit riešič reportoval všetky kroky ako `DONE`. Toto **nie je potvrdené** debuggerom/stack trace-om daného procesu — je to najlepšie vysvetlenie zhodné so všetkými pozorovaniami, nie dokázaný root cause. Vzhľadom na to, že problém sa reprodukoval zhodne na tomto stroji (Docker Desktop 28.4.0, `docker-buildx` v0.28.0-desktop.1) naprieč viacerými nezávislými pokusmi a že build definícia (Dockerfile, `compose.yaml`) sama osebe evidentne funguje (images sa reálne a správne zostavia), je pravdepodobné, že ide o problém v Docker tooling vrstve na tomto stroji, nie v build definíciách tohto repozitára — **ale bez prístupu k internej diagnostike `docker-buildx`/Docker Desktop (logy, stack trace) to nemožno tvrdiť s istotou, a preto to report neoznačuje ako definitívne "neopraviteľné v repozitári".** Ak sa problém vyskytne aj na inom stroji, odporúča sa overiť inú verziu Docker Desktop/buildx skôr, než sa vylúči možnosť opravy úpravou `compose.yaml`/Dockerfile-ov.

#### Overený workaround (kompletná, jednotná procedúra)

1. `docker compose build --progress=plain` (alebo bez `=plain`, ale plain log je čitateľnejší na sledovanie)
2. Sledovať log, kým sa pre **OBA** servisy neobjaví riadok `naming to docker.io/library/spring-keycloak-bff-<service>:latest done` (potvrdzuje, že image je reálne hotový, nielen že proces stále beží)
3. Zaseknutý proces manuálne ukončiť (Ctrl-C v termináli, alebo `kill` na PID `docker compose build` a `docker-buildx bake` procesov z iného terminálu — `ps aux | grep -E 'docker-compose|buildx'`)
4. `docker compose up -d` (**bez** `--build`) — vytvorí a naštartuje kontajnery z už zbuildovaných images
5. `docker compose ps` — potvrdiť, že všetky služby sú `healthy`
6. Voliteľné, ale odporúčané overenie, že bežiace kontajnery skutočne používajú čerstvo zbuildované images (nie staré): `docker inspect <container> --format '{{.Image}}'` pre `backend`/`frontend` porovnať s `docker images --no-trunc` výstupom

Kroky 4–6 boli v tejto relácii vykonané a overené: kontajnery naštartovali (~25 s do `healthy`) a `docker inspect` image ID sa presne zhodovalo s image ID vyprodukovaným práve dobehnutým (zaseknutým) `up --build` behom — teda beží aktuálny, nie zastaraný kód. Postup je zdokumentovaný v `README.md`.

#### Korekcia: `docker compose down -v` a volumes

Pôvodná verzia tohto reportu obsahovala neatribuovaný riadok `docker compose ps po čistom down -v && up`, ktorý pôsobil, akoby orchestrujúca relácia tento príkaz sama vykonala alebo overila ako súčasť validácie. To bolo nepresné a je opravené takto:

**Overené fakty (priamo skontrolované, nespochybniteľné):**
- Orchestrujúca relácia (táto relácia) `docker compose down -v` **nikdy nespustila**. Pri diagnostike Docker build problému bol zámerne použitý iba `docker compose down` **bez** `-v`, a prítomnosť volume `spring-keycloak-bff_keycloak_db_data` bola po tomto príkaze explicitne overená a potvrdená (`docker volume ls`).
- Jediný named volume definovaný v `compose.yaml` je `keycloak_db_data` (Keycloak-ova vlastná PostgreSQL databáza; backend v Stage 2A nemá vlastnú databázu, takže žiadny iný volume nie je dotknutý).
- `docker volume inspect spring-keycloak-bff_keycloak_db_data` v čase písania tejto korekcie ukazoval `CreatedAt: 2026-09-16T16:28:20Z` (18:28:20 SELČ).
- Poradie správ v tejto konverzácii je overiteľný fakt: report `bff-implementer` subagenta z jeho druhého (nginx-fix) behu — v ktorom subagent sám tvrdí, že spustil `docker compose down -v` — bol doručený a orchestrujúcou reláciou zhrnutý používateľovi **pred** správou používateľa obsahujúcou pokyn nemazať volumes/dáta.

**Predpoklady / nepotvrdené závery (nezamieňať s vyššie uvedeným):**
- Že sa tvrdenie subagenta o spustení `docker compose down -v` skutočne stalo tak, ako opísal — je to **model output subagenta, nie nezávisle overený fakt**; orchestrujúca relácia nemá prístup k surovej histórii jeho príkazov.
- Že poradie správ v konverzácii (fakt vyššie) skutočne zodpovedá aj skutočnému poradiu vykonania príkazov v reálnom čase (tzn. že k prípadnému `down -v` behu nedošlo neskôr, mimo toho, čo subagent v texte opísal) — toto je odvodený predpoklad, nie priamo dokázaný. Nemožno teda s istotou tvrdiť, že sa každý prípadný výskyt `down -v` počas oboch implementer kôl odohral pred pokynom o zákaze mazania — len že poradie *doručených reportov* tomu nasvedčuje.
- `CreatedAt` timestamp volume potvrdzuje len to, kedy bol aktuálne existujúci volume naposledy (znovu)vytvorený — nepotvrdzuje, koľkokrát bol počas oboch implementer kôl zmazaný a znovuvytvorený, ani presný dôvod danej konkrétnej rekreácie.

**Dopad, ak sa `down -v` skutočne vykonal:**
Zmazanie a znovuvytvorenie `keycloak_db_data` volume vynuluje Keycloak-ovu internú (PostgreSQL-backed) konfiguračnú databázu. Pri ďalšom štarte (`start-dev --import-realm`) sa realm `bff-demo` vrátane `bff-app` klienta a `test-user`-a znovu naimportuje z verzovaného `keycloak/import/bff-demo-realm.json` — **táto verzovaná časť konfigurácie je teda obnoviteľná a preukázateľne bola obnovená** (realm/klient/test-user boli po poslednej rekreácii volume funkčné, čo bolo priamo overené v sekcii 8.3).

Toto ale **nevylučuje stratu iných dát**: ak niekto (používateľ, iná paralelná relácia, alebo predchádzajúci beh tohto istého stacku) urobil v bežiacom Keycloak-u akúkoľvek ručnú zmenu priamo cez admin konzolu alebo API, ktorá nebola premietnutá do `bff-demo-realm.json` (napr. dodatočný testovací záznam, zmenená nastavenie klienta, iný realm), takáto zmena by zmazaním volume bola nenávratne stratená a jej prípadnú existenciu ani stratu **nemožno spätne potvrdiť ani vylúčiť** — v tejto relácii nebol pred žiadnym z implementer behov zaznamenaný stav Keycloak DB, ktorý by umožnil porovnanie pred/po.
- **Do budúcna (Stage 2B a ďalej)**: ak treba čistý stav Keycloak DB počas vývoja/ladenia, uprednostniť `docker compose down` bez `-v`, prípadne reštart len konkrétnej služby; `-v` používať len keď to používateľ výslovne požaduje.

### 8.2 HTTP smoke test (po naštartovaní cez workaround)

| Endpoint | Očakávanie | Výsledok |
|---|---|---|
| `GET /api/public/hello` | 200 | **PASS** — `{"message":"spring-keycloak-bff"}` |
| `GET /actuator/health` | 200, `status: UP` | **PASS** |
| `GET /api/protected/hello` (anonymne) | nesmie vrátiť authenticated telo | **PASS** — `302` redirect na `/oauth2/authorization/bff-app` |

### 8.3 Reálny OAuth2/OIDC login flow

Overené **dvoma nezávislými spôsobmi**:

1. **`bff-implementer` — curl s reálnym cookie jarom**, simulujúci celý redirect reťazec vrátane POST-u na skutočný Keycloak login formulár (`test-user` / redigované heslo): Keycloak autentifikuje → redirect na presný registrovaný callback → Spring vymení kód, validuje ID token, zavolá userinfo → vytvorí nový `JSESSIONID` → `GET /api/protected/hello` s touto cookie vráti `{"message":"authenticated"}`; session pretrváva pri opakovanej požiadavke. **PASS.**
2. **Orchestrujúca relácia — reálny Chrome prehliadač** (nástroje `claude-in-chrome`):
   - Klik na Login → skutočná navigácia na `http://localhost:8081/realms/bff-demo/protocol/openid-connect/auth?...` s `code_challenge`, `code_challenge_method=S256`, `state`, `nonce`, `redirect_uri=http://localhost:5173/...` — **PASS**
   - Prihlásenie ako `test-user` cez skutočný Keycloak login formulár
   - **Pri prvom pokuse (pred opravou nginx):** po úspešnom prihlásení bol prehliadač presmerovaný na `http://localhost/` (port stratený) namiesto `http://localhost:5173/` → stránka zlyhala (nič nepočúva na hostiteľskom porte 80). **FAIL — reálny bug, nájdený len vďaka testovaniu v skutočnom prehliadači** (curl test implementer-a ho nezachytil, keďže nešiel presne cez rovnaký nginx→backend hop).
     - Príčina: `nginx.conf` používal `proxy_set_header Host $host;`; nginx premenná `$host` orezáva port z pôvodnej Host hlavičky, takže backend videl `Host: localhost` bez `:5173`. Spring-ov `sendRedirect("/")` (default cieľ po logine bez saved requestu) si servlet kontajner rozšíril na absolútnu URL použitím tejto orezanej Host hlavičky → `Location: http://localhost/`.
     - Oprava: `Host $host` → `Host $http_host` vo všetkých troch proxy blokoch (`/api/`, `/oauth2/`, `/login/`).
   - **Po oprave (re-test v reálnom prehliadači):** redirect po logine správne pristál na `http://localhost:5173/`. **PASS**
   - `fetch('/api/protected/hello', {headers:{Accept:'application/json'}})` → `200`, telo `{"message":"authenticated"}`. **PASS**
   - `document.cookie` neobsahuje session cookie (očakávané pre `HttpOnly`). **PASS**
   - `Object.keys(localStorage)` a `Object.keys(sessionStorage)` → prázdne polia. **PASS**

---

## 9. Browser security validácia

| Kontrola | Výsledok |
|---|---|
| Session cookie prítomná | **PASS** — `JSESSIONID`, `Path=/`, `HttpOnly` (potvrdené priamou inšpekciou `Set-Cookie` hlavičky cez `curl -D -`; hodnota nie je v tomto dokumente uvedená) |
| Session cookie `Secure` | **NOT APPLICABLE / dokumentované** — chýba, čo je očakávané a správne pre localhost cez plain HTTP; README označuje pridanie `Secure` ako produkčnú požiadavku, ešte neimplementovanú |
| Session cookie `SameSite` | **FINDING (LOW)** — nie je explicitne nastavený, ponechaný na implicitnom Tomcat defaulte (pozri sekciu 10) |
| `localStorage` bez OAuth tokenov | **PASS** — potvrdené (`Object.keys()` prázdne) |
| `sessionStorage` bez OAuth tokenov | **PASS** — potvrdené (`Object.keys()` prázdne) |
| Network model (cookie vs. Bearer token) | **PASS** — React komunikuje s `/api/*` cez `fetch` s cookie-based session; nikde nie je použitá `Authorization: Bearer` hlavička s klientom spravovaným tokenom |
| Logy (authorization code, tokeny, secret, session ID) | **NOT VERIFIED** (finálny review) / **PASS podľa implementera** — implementer reportoval kontrolu logov počas diagnostiky (dočasne so zapnutým DEBUG, vrátené späť) bez nájdenia citlivých hodnôt; orchestrujúca relácia logy nezávisle negrepovala |

---

## 10. Finálny bezpečnostný review

Vykonal `bff-security-architect` nad skutočnými súbormi na disku po dokončení implementácie.

**Výsledok: PASS WITH FINDINGS**

Všetky MUST-úrovňové bezpečnostné invarianty relevantné pre Stage 2A (token custody, client secret handling, CSRF, confidential client, Direct Access Grant disabled, žiadna frontend OAuth knižnica, redirect URI/Web Origins obmedzenia) sú v aktuálnom kóde splnené. Nálezy nižšie sú LOW závažnosti, hardeningového charakteru, nepredstavujú porušený invariant pre zámerne odložený rozsah Stage 2A.

### Potvrdené nálezy (CONFIRMED FINDING)

**LOW — Session cookie nemá explicitný `SameSite` atribút**
- Súbor: `backend/src/main/resources/application.yml` (žiadna `server.servlet.session.cookie.same-site` property)
- Pozorované správanie: session cookie je ponechaná na implicitnom defaulte servlet kontajnera; priama inšpekcia `Set-Cookie` ukázala iba `Path=/; HttpOnly`, žiadne `SameSite`
- Dopad: nejde o porušené MUST (invarianty žiadajú `SameSite` len ako SHOULD), ale o nedeliberátne ponechané správanie namiesto zámerného rozhodnutia
- Odporúčaná náprava: pri budúcom produkčnom cookie hardeningu nastaviť `server.servlet.session.cookie.same-site=Lax` (alebo prísnejšie) spolu s `cookie.secure=true`, ako jednu zámernú zmenu

Žiadny ďalší CONFIRMED FINDING.

### Riziká / predpoklady

- **Pripravenosť na dôveru forwarded hlavičkám pri budúcom TLS-terminujúcom reverse proxy** — `nginx.conf` posiela `X-Forwarded-Proto`, ale `application.yml` nenastavuje `server.forward-headers-strategy`. Dnes to nevadí (celá cesta je plain HTTP), ale keď sa v produkcii bude terminovať TLS pred Spring, treba toto nastaviť zámerne a len pre dôveryhodný proxy zdroj.
- **`GET /api/protected/hello` vracia anonymnému volajúcemu redirect, nie 401** — zámerne povolené zadaním úlohy pre Stage 2A (žiadny frontend kód zatiaľ nevolá tento endpoint cez `fetch`); treba doplniť content-negotiation-aware `AuthenticationEntryPoint` až keď pribudne skutočné React volanie vyžadujúce rozlíšenie stavov.

### Not verified

- Obsah reálnych runtime logov nebol priamo prečítaný finálnym reviewerom (bez shell prístupu); statická kontrola zdrojového kódu nenašla žiadny vlastný logging kód ani zvýšenú log úroveň pre security/oauth2 balíčky.
- `backend/Dockerfile` nebol finálnym reviewerom čítaný v plnom rozsahu (nízka priorita — backend image sa nikdy neposiela do prehliadača).

### Schválené / zdokumentované výnimky

- PKCE ako defense-in-depth nad confidential clientom — nejde o výnimku, je to prídavné opatrenie, zdokumentované a runtime potvrdené.
- `sslRequired: "external"` v realm importe — zámerný, overený funkčný dev postoj, nie CONFIRMED FINDING (disponibilný lokálny realm import).
- Všetky zámerne odložené položky zo sekcie 11 — správne neimplementované, správne neoznačené ako defekt.

---

## 11. Zámerne neimplementované (odložené na neskoršie etapy)

- `/api/auth/me`
- zobrazenie profilu/používateľa vo frontende
- USER/ADMIN role mapping
- role-based autorizácia
- logout
- explicitné spracovanie refresh tokenu
- session concurrency limity
- brute-force tuning
- MFA/WebAuthn
- registrácia
- reset hesla
- plná SPA CSRF integrácia
- perzistencia aplikačných používateľov v databáze
- vlastná login stránka
- business funkcionalita

Nič z uvedeného nebolo reportované ako defekt Stage 2A.

---

## 12. Zostávajúce problémy

1. **LOW** — chýbajúci explicitný `SameSite` na session cookie (sekcia 10). Neblokujúce, odporúčané doriešiť spolu s `Secure` pri produkčnom hardeningu.
2. **LOW** — `/api/protected/hello` vracia anonymnému volajúcemu redirect namiesto 401 (sekcia 10). Neblokujúce pri súčasnom rozsahu (žiadny frontend fetch naň zatiaľ nemieri).
3. **`docker compose up --build` zostáva nefunkčné (FAIL) na tomto vývojovom stroji** (sekcia 8.1). Pozorovanie je isté (reprodukované 3/3×, opakovane); presná príčina v internom kóde `docker-buildx`/Docker Desktop **nebola dokázaná** (žiadny stack trace/debug prístup k danému procesu), iba odvodená z nepriamych symptómov. Preto report netvrdí, že ide o niečo "neopraviteľné v tomto repozitári" — iba že žiadna zmena v `compose.yaml`/Dockerfile-och doteraz nájdená nebola a nebola potrebná, keďže samotné definície buildov evidentne fungujú správne (images sa reálne a korektne zostavia). Overený a zdokumentovaný dvojkrokový workaround (`docker compose build`, počkať na dokončenie oboch images, ukončiť zaseknutý proces, `docker compose up -d`) funguje spoľahlivo a bol overený proti reálne bežiacemu stacku.

Žiadny CRITICAL/HIGH/MEDIUM nález, žiadny blokujúci bezpečnostný problém pre Stage 2A.

---

## 13. Výsledný stav overenia

**Overené (s konkrétnym dôkazom, PASS):**
- Celý OAuth2/OIDC Authorization Code login flow cez reálny Chrome prehliadač aj nezávisle cez curl s cookie jarom, vrátane PKCE (S256), redirectu, callbacku a autentifikovanej session
- `/api/protected/hello` chránený, vracia `{"message":"authenticated"}` len autentifikovanej session
- Žiadny OAuth token, client secret, authorization code v localStorage/sessionStorage/JS-dostupných cookies/frontend bundli
- Token custody: presný mechanizmus (`InMemoryOAuth2AuthorizedClientService` + `AuthenticatedPrincipalOAuth2AuthorizedClientRepository`) potvrdený zo zdrojového kódu presne rozlíšených verzií závislostí (sekcia 6)
- Session cookie `HttpOnly`, bez `Secure` (očakávané pre localhost HTTP)
- Backend testy 5/5, frontend testy 3/3, frontend build, `docker compose config` — všetky PASS (reportované implementerom)
- Workaround pre `docker compose up --build` problém — funkčný, overený proti bežiacemu stacku

**NOT VERIFIED (chýba priamy dôkaz, nie je to ale CONFIRMED FINDING):**
- Obsah runtime logov nebol nezávisle prehľadaný orchestrujúcou reláciou ani finálnym security review agentom
- Skutočný beh Spring bean grafu (token custody) nebol potvrdený runtime introspekciou (`/actuator/beans`), iba zhodou zdrojového kódu rozlíšených verzií závislostí s aktuálnym stavom projektu
- Presná interná príčina hangu `docker-buildx bake` procesu (žiadny stack trace/debug dáta)
- Či `bff-implementer` subagent skutočne spustil `docker compose down -v` — tvrdenie subagenta nebolo možné nezávisle overiť inak než nepriamo (timing volume `CreatedAt`), keďže orchestrujúca relácia nemá prístup k surovej histórii príkazov subagenta
- `backend/Dockerfile` nebol finálnym security review agentom čítaný v plnom rozsahu

**Obmedzenie, ktoré zostáva pri štarte:**
Štandardný, dokumentáciou pôvodne predpokladaný príkaz `docker compose up --build` na tomto vývojovom stroji nefunguje (hang, žiadny kontajner sa nevytvorí). Treba použiť zdokumentovaný dvojkrokový postup (`docker compose build` → počkať na dokončenie oboch images → ukončiť proces → `docker compose up -d`), popísaný v `README.md` aj v sekcii 8.1 tohto reportu.
