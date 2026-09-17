# Stage 2C — Finálny report: SPA CSRF integrácia a plný application logout

Repozitár: `spring-keycloak-bff`, branch `feature/2c-csrf-logout` (vetvené z
dokončeného Stage 2B, `feature/2b-session-auth-protected-route`).

**Aktuálny stav (2026-09-17, nezávislé overenie Codex): hlavné browser
login/logout scenáre už prešli v skutočnom Codex In-app Browser. Etapa zostáva
s otvorenými kontrolami úložísk a explicitného bfcache obnovenia; nie je to
bezvýhradný PASS všetkých acceptance kritérií.** Backend 18/18 a frontend
24/24 testov prešli pri novom nezávislom spustení. Presné dôkazy a limity
sú v sekcii 16, ktorá nahrádza staršie súhrny stavu v sekciách 13–15.

Sekcie 1–15 zachovávajú históriu predchádzajúcej práce. Staršie tvrdenia o
nedostupnej browser validácii, `{baseUrl}/`, chýbajúcom `pageshow` listeneri
a počtoch testov opisujú vtedajší stav, nie finálnu implementáciu.
Codex v tomto kole nezmenil implementáciu, nemaže dáta ani volumes a
nevykonal commit ani push.

---

## 1. Pracovný postup

1. Prečítané `docs/stage-2a-report.md` a `docs/stage-2b-report.md`, overený
   aktuálny stav repozitára (branch `feature/2b-session-auth-protected-route`
   obsahuje skutočne dokončený Stage 2B kód a commit `feat: Finishing etap2`;
   `main` ho ešte neobsahuje). Vytvorená nová vetva `feature/2c-csrf-logout`
   z `feature/2b-session-auth-protected-route`. Baseline pred štartom overený
   priamo (10/10 backend, 12/12 frontend testov PASS).
2. `bff-security-architect` — pred-implementačný review (CSRF kontrakt,
   `end_session_endpoint` medzera, **kritická** medzera v odstraňovaní
   `OAuth2AuthorizedClient` záznamu, Keycloak `post.logout.redirect.uris`,
   `/logout` proxy routing).
3. Orchestrujúca relácia — nezávislé overenie troch neistých bodov
   architekta priamo proti skutočným, rozlíšeným zdrojovým súborom Spring
   Security 7.1.1 (`./mvnw dependency:sources` + priame čítanie `.java`
   súborov) **pred** odovzdaním implementerovi — potvrdené: CSRF defaulty,
   presný `end_session_endpoint` kľúč, neexistencia auto-wired odstránenia
   authorized-clienta. Navyše objavený (a zámerne nepoužitý) nový
   `.csrf(csrf -> csrf.spa())` v Spring Security 7.0+.
4. `bff-implementer` — implementácia podľa takto overených záväzných
   obmedzení.
5. Orchestrujúca relácia — nezávislá validácia: vlastné spustenie testov,
   preverenie mandatórneho acceptance-gate testu na netautologickosť
   (pozri sekciu 5), rebuild a redeploy Docker stacku, rozsiahly skriptovaný
   HTTP round-trip cez reálne bežiaci stack vrátane **nájdenia a opravy
   reálneho bugu** (sekcia 6), a non-deštruktívna aplikácia Keycloak
   atribútu na už bežiacu inštanciu (sekcia 7).
6. `bff-security-architect` — finálny review nad skutočným kódom a
   zozbieraným dôkazom.
7. Reálna browser validácia cez `claude-in-chrome` **nebola v tomto kole
   dostupná** (3 pokusy o pripojenie, vždy "Browser extension is not
   connected") — nahradená rozsiahlym skriptovaným HTTP overením; presne
   označené, čo zostáva NOT VERIFIED (sekcia 10).

---

## 2. Pre-implementačný architektonický review

**Výsledok: PASS WITH FINDINGS** (nálezy = nutné doplnky na uzavretie
existujúcich medzier zo Stage 2A/2B, nie regresie navrhovaného Stage 2C
kódu).

### Kľúčové nálezy a záväzné obmedzenia

**HIGH — `OAuth2AuthorizedClient` sa neodstráni žiadnym frameworkovým
defaultom** (**bod, ktorý explicitne zadal používateľ**): aktuálne
serverové úložisko je `InMemoryOAuth2AuthorizedClientService` cez
`AuthenticatedPrincipalOAuth2AuthorizedClientRepository` (potvrdené v
`docs/stage-2a-report.md` §6 presnou zhodou zdrojového kódu) — mapa
kľúčovaná `(registrationId, principal.getName())`, nezávislá od
`HttpSession`. Invalidácia session sama osebe túto mapu nevyprázdni.
Záväzné: explicitný `LogoutHandler`, ktorý zavolá
`OAuth2AuthorizedClientRepository.removeAuthorizedClient(...)`, s
mandátnym testovým dôkazom (nielen že session zmizla, ale aj že
`OAuth2AuthorizedClientService.loadAuthorizedClient(...)` vracia `null`).

**HIGH — chýbajúce `end_session_endpoint` metadata**: manuálne budovaný
`ClientRegistration` (žiadna OIDC discovery) nemá
`providerConfigurationMetadata` — `OidcClientInitiatedLogoutSuccessHandler`
by bez neho nevedel zostaviť Keycloak logout redirect. Záväzné: pridať
`.providerConfigurationMetadata(Map.of("end_session_endpoint", ...))` s
**browser-facing** issuer URI.

**MEDIUM — chýbajúci `post.logout.redirect.uris` Keycloak atribút**:
RP-initiated logout nemá kam validne presmerovať späť. Záväzné: pridať
literálnu hodnotu do realm JSON-u **a** neduštruktívne aplikovať na už
bežiacu, volume-persistovanú inštanciu (import sa pri existujúcom realme
preskakuje).

**MEDIUM — chýbajúca `/logout` proxy cesta** v `nginx.conf`/`vite.config.ts`
— rovnaká trieda bugu, akú Stage 2A už raz našiel a opravil pre
`/oauth2`/`/login` (`Host` vs. `$http_host`).

### Dodatočné overenie orchestrujúcou reláciou (pred implementáciou)

Architekt nemal shell prístup a svoje tri kľúčové technické predpoklady
označil ako neisté (Risks/assumptions). Orchestrujúca relácia ich pred
odovzdaním implementerovi priamo overila proti skutočným, rozlíšeným
zdrojom `spring-security-{web,config,oauth2-client}-7.1.1-sources.jar`:

- **CSRF defaulty**: `CsrfConfigurer.java` — pole `csrfTokenRepository`
  inicializované na `new HttpSessionCsrfTokenRepository()`; `CsrfFilter.java`
  — pole `requestHandler` inicializované na
  `new XorCsrfTokenRequestAttributeHandler()`, použité vždy, keď Configurer
  nedostal iný handler. Potvrdené: bez `.csrf(...)` je toto presne aktívny
  default.
- **`end_session_endpoint` kľúč**: `OidcClientInitiatedLogoutSuccessHandler.java`
  — `providerDetails.getConfigurationMetadata().get("end_session_endpoint")`
  — presne tento literálny reťazec.
- **Žiadne auto-wired odstránenie authorized-clienta**: `grep -rl
  "removeAuthorizedClient"` naprieč `spring-security-oauth2-client` a
  `spring-security-config` zdrojmi nenašiel žiadnu `LogoutHandler`
  implementáciu, ktorá by to robila automaticky.
- **Objavené navyše**: Spring Security 7.0 pridalo `.csrf(csrf -> csrf.spa())`
  (cookie-based `CookieCsrfTokenRepository.withHttpOnlyFalse()` +
  `SpaCsrfTokenRequestHandler`) — zámerne nepoužité, aby sa do inak plne
  HttpOnly-cookie architektúry nezaviedla nová JS-čitateľná cookie.

Tieto tri overenia boli odovzdané implementerovi ako **overené fakty**, nie
predpoklady — znížilo to riziko implementačného zle-nasmerovania na nule pre
tieto konkrétne body.

---

## 3. Vytvorené a upravené súbory

### Backend (nové)

- `backend/src/main/java/com/example/bff/api/CsrfResponse.java` — DTO pre
  `GET /api/auth/csrf`.
- `backend/src/test/java/com/example/bff/config/LogoutTest.java` — CSRF
  enforcement na `/logout`, `GET /logout` inertnosť, valid-CSRF logout +
  Keycloak redirect, **mandátny test odstránenia authorized-clienta**.

### Backend (upravené)

- `backend/src/main/java/com/example/bff/config/SecurityConfig.java` —
  `/api/auth/csrf` pridané do `permitAll()`; `.logout(...)` s explicitným
  `LogoutHandler` (odstránenie authorized-clienta) a
  `OidcClientInitiatedLogoutSuccessHandler` (`setPostLogoutRedirectUri`,
  pozri sekciu 6 pre presnú hodnotu). Žiadna `.csrf(...)` customizácia.
- `backend/src/main/java/com/example/bff/config/OAuth2ClientConfig.java` —
  `.providerConfigurationMetadata(Map.of("end_session_endpoint", ...))`.
- `backend/src/main/java/com/example/bff/api/AuthController.java` —
  `GET /api/auth/csrf`, explicitné `csrfToken.getToken()`, `no-store`.
- `backend/src/test/java/com/example/bff/api/AuthControllerTest.java` —
  CSRF kontrakt testy.

### Keycloak / infra

- `keycloak/import/bff-demo-realm.json` — `"attributes": {"post.logout.redirect.uris": "http://localhost:5173/"}`.
- `frontend/nginx.conf`, `frontend/vite.config.ts` — nová `/logout` proxy
  cesta (rovnaký `Host`-preserving vzor ako `/oauth2`/`/login`).

### Frontend (nové)

- `frontend/src/auth/csrf.ts` — `fetchCsrfToken()` + zdieľaný `unsafeFetch()`
  helper (nepoužitý žiadnym business endpointom — žiadny pridaný, ako
  úloha vyžadovala).
- `frontend/src/auth/LogoutButton.tsx` — reálny `<form method="POST">`,
  `requestSubmit()`, synchrónny `useRef` guard proti duplicitným kliknutiam,
  chybový stav s retry.
- `frontend/src/auth/csrf.test.ts`, `frontend/src/auth/LogoutButton.test.tsx`.

### Frontend (upravené)

- `frontend/src/pages/HomePage.tsx` — `<LogoutButton />` len v
  `authenticated` vetve.
- `frontend/src/pages/HomePage.test.tsx` — viditeľnosť Logout tlačidla.

---

## 4. CSRF kontrakt (skutočne implementovaný)

`GET /api/auth/csrf` — `permitAll()`, vracia presne
`{"token": "...", "headerName": "X-CSRF-TOKEN", "parameterName": "_csrf"}`
(hodnoty `headerName`/`parameterName` sú skutočné Spring Security defaulty,
nikdy nie hardcoded v aplikačnom kóde — **overené runtime**, sekcia 8).
`Cache-Control: no-store` explicitne nastavené. Žiadna CORS konfigurácia.
Frontend nikdy necachuje získaný token — `fetchCsrfToken()` sa volá
znovu tesne pred každým unsafe requestom (viď sekcia 9, riziko/overenie
"stale CSRF state").

---

## 5. Logout implementácia a mandátny test (CRITICAL bod)

`.logout(...)` registruje **dve** veci navyše k Spring Security defaultom
(`CsrfLogoutHandler`, `SecurityContextLogoutHandler` — tie ostávajú
nedotknuté, teda CSRF enforcement a session invalidácia fungujú aj bez
tejto úlohy):

1. Explicitný `LogoutHandler` (lambda), ktorý volá
   `OAuth2AuthorizedClientRepository.removeAuthorizedClient("bff-app", authentication, request, response)`.
2. `OidcClientInitiatedLogoutSuccessHandler` s `end_session_endpoint`
   metadátami z `OAuth2ClientConfig` a fixným post-logout redirectom
   (sekcia 6).

### Overenie, že mandátny test nie je tautologický

`LogoutTest.postLogoutRemovesTheAuthorizedClientRecordSeparatelyFromTheSession`
manuálne uloží `OAuth2AuthorizedClient` pre `TEST_PRINCIPAL_NAME`, prihlási
sa cez `oidcLogin().idToken(token -> token.subject(TEST_PRINCIPAL_NAME))`
proti **skutočnej** `bff-app` `ClientRegistration` (nie syntetickej testovej
registrácii), zavolá CSRF-valid `POST /logout`, a overí **oddelene** (a) že
session je invalidná a (b) že `loadAuthorizedClient(...)` teraz vracia
`null`.

Kľúčová otázka: keďže skutočná `ClientRegistration` má
`userNameAttributeName("preferred_username")`, rovná sa
`authentication.getName()` v tomto mockovanom teste naozaj
`TEST_PRINCIPAL_NAME` (nastavenému len ako `sub` claim), alebo test
testuje dva rôzne, nikdy neprepojené kľúče (čo by ho urobilo vacuous)?

- **Orchestrujúca relácia** overila priamo zo zdrojového kódu
  `spring-security-test-7.1.1`: `OidcLoginRequestPostProcessor.defaultPrincipal()`
  volá `new DefaultOidcUser(authorities, idToken, userInfo)` — **3-argumentový**
  konštruktor, ktorý `nameAttributeKey` vždy defaultuje na
  `IdTokenClaimNames.SUB` (potvrdené priamo v `DefaultOidcUser.java`),
  **bez ohľadu** na `.clientRegistration(...)`-om nastavený
  `userNameAttributeName`. `authentication.getName()` v tomto teste sa teda
  skutočne rovná `sub` claimu = `TEST_PRINCIPAL_NAME`.
- **Finálny `bff-security-architect` review** (bez shell prístupu)
  nezávisle potvrdil rovnaký záver iným spôsobom: keby by mock skutočne
  použil `"preferred_username"` ako nameAttributeKey (ktorý v tomto teste
  nie je nastavený vôbec), `DefaultOAuth2User`-ov konštruktor by pri chýbajúcom
  atribúte vyhodil `IllegalArgumentException` **pred** vykonaním assertions
  — test by zlyhal s výnimkou, nie tichým false-positive průchodom. Keďže
  test prechádza (potvrdené nezávisle spusteným `16/16` Maven behom), je to
  mechanizmový dôkaz, že sa použil `sub`, nie `preferred_username`.

**Záver: test je skutočný, nie tautologický — genuinely dokazuje, že
`LogoutHandler` funguje.**

---

## 6. Historická oprava (neskôr nahradená pevnou URI v §15.1): reálny bug nájdený a opravený orchestrujúcou reláciou: chýbajúca koncová lomka v `post_logout_redirect_uri`

Implementer pôvodne nastavil `handler.setPostLogoutRedirectUri("{baseUrl}")`
(bez koncovej lomky). Orchestrujúca relácia to reprodukovala **end-to-end
proti reálne bežiacemu Docker stacku** (skutočný OAuth2/PKCE login s
`test-user`, `GET /api/auth/csrf`, CSRF-valid `POST /logout`):

- `{baseUrl}` sa rozvinie na `http://localhost:5173` (**bez** koncovej
  lomky — kontextová cesta appky je prázdna), zatiaľ čo registrovaný
  Keycloak atribút `post.logout.redirect.uris` je literálne
  `http://localhost:5173/` (**s** lomkou).
- Keycloak validuje `post_logout_redirect_uri` presnou zhodou reťazcov —
  tieto sa nezhodovali.
- **Priamo pozorované**: Keycloak vrátil `HTTP 400 Bad Request`, telo
  stránky obsahovalo `"Invalid redirect uri"`.

**Oprava**: `handler.setPostLogoutRedirectUri("{baseUrl}/")` (pridaná
literálna koncová lomka do šablóny).

**Overenie opravy vlastným (upraveným) testom**:
`LogoutTest.postLogoutWithValidCsrfTokenInvalidatesSessionAndRedirectsToKeycloakEndSession`
teraz assertuje `containsString("post_logout_redirect_uri=http://localhost/")`
(s lomkou, zodpovedá MockMvc defaultnému test-hostu `http://localhost`)
namiesto pôvodného slabšieho `containsString("post_logout_redirect_uri=")`.
Test bol **zámerne spustený proti neopravenému kódu** (dočasné vrátenie na
`{baseUrl}` bez lomky) — **zlyhal** presne s očakávaným nesúladom
(`"http://localhost"` namiesto `"http://localhost/"`). Po vrátení opravy —
**PASS**, aj celá zvyšná test suite (16/16).

**Overenie proti reálnemu stacku po oprave** (backend image
znovu-zostavený a nasadený): Keycloak teraz vracia `302`, nastavuje
`Set-Cookie: KEYCLOAK_IDENTITY=; ...; Max-Age=0` (skutočne maže vlastnú SSO
identity cookie), a finálny redirect pristáva presne na
`http://localhost:5173/`, `200 OK`.

**(c) Kontrola iných výskytov tej istej triedy bugu**: finálny
`bff-security-architect` review prehľadal repozitár na ďalšie
`{baseUrl}`/redirect-URI/hardcoded-origin vzory. Jediný ďalší externe
validovaný redirect URI (`bff.oauth2.redirect-uri` v `application.yml`) je
plne špecifikovaný literál, presne zhodný s realm-om registrovaným
`redirectUris` — žiadny ďalší výskyt tejto triedy bugu nenájdený.

---

## 7. Neduštruktívna aplikácia Keycloak atribútu na už bežiacu inštanciu

Keďže realm import sa pri existujúcom realme preskakuje (potvrdené v
Stage 2A/2B reportoch) a **mazanie Docker volumes/dát bolo explicitne
zakázané**, orchestrujúca relácia aplikovala zmenu na živú, volume-persistovanú
inštanciu cez Keycloak Admin REST API (existujúce disponibilné bootstrap
admin credentials `admin`/`admin-local-dev-only` z `compose.yaml`):

1. `POST /realms/master/protocol/openid-connect/token` (password grant,
   `client_id=admin-cli`) → admin access token (hodnota nikdy nevypísaná,
   uložená len do dočasného súboru zmazaného po použití).
2. `GET /admin/realms/bff-demo/clients?clientId=bff-app` → interné UUID
   klienta.
3. `GET` aktuálnej reprezentácie klienta → **zistené, že Keycloak sám
   pridal defaultný atribút `post.logout.redirect.uris: "+"`** (špeciálna
   hodnota = "zdedi registrované `redirectUris`" — čo by v praxi
   neumožnilo presmerovanie na `/`, keďže registrovaný `redirectUris`
   obsahuje len OAuth callback cestu, nie `/`). Toto potvrdilo, že MEDIUM
   nález architekta bol reálny a stále aktuálny, nie len teoretický.
4. `PUT` s pridaným/nahradeným `attributes.post.logout.redirect.uris = "http://localhost:5173/"`
   → `204 No Content`.
5. Overené opätovným `GET` — hodnota sa skutočne zmenila a pretrvala.

Žiadny Docker volume ani dáta neboli zmazané; zmena je aplikovaná na
existujúcu, bežiacu inštanciu.

---

## 8. Testy (spustené priamo orchestrujúcou reláciou, nie len reportované)

| Príkaz | Výsledok |
|---|---|
| `cd backend && ./mvnw -o test` | **PASS** — 16/16 (vrátane `LogoutTest` ×4, `AuthControllerTest` ×6) |
| `cd frontend && npx vitest run` | **PASS** — 7 súborov, 22/22 testov |
| `cd frontend && npm run build` | **PASS** (`tsc -b && vite build`) |
| `docker compose config` | **PASS** (exit 0) |
| `docker compose build` (samostatne) + `docker compose up -d` | **PASS** — oba images zostavené bez zaseknutia, kontajnery `healthy` |

Backend testové scenáre: CSRF bootstrap kontrakt (tvar, `no-store`),
missing-CSRF `POST /logout` odmietnutý (session prežije), `GET /logout`
inertný, valid-CSRF `POST /logout` (session invalidovaná + presný Keycloak
redirect vrátane koncovej lomky), **mandátny test odstránenia authorized-clienta**.

Frontend testové scenáre: Logout viditeľnosť len pre authenticated,
CSRF acquisition + korektné form submission (action/method/hidden field),
chybový stav pri zlyhaní CSRF fetchu s retry, duplicate-click prevention,
zdieľaný `unsafeFetch` helper bez business API.

---

## 9. Runtime HTTP validácia (skriptovaná, proti reálne bežiacemu Docker stacku)

Vykonaná orchestrujúcou reláciou priamo cez `curl` s reálnym cookie jarom
proti kontajnerizovanému nginx→backend→Keycloak stacku (nie mock, nie
implementer-reportované) — **tri nezávislé login/logout cykly**:

| Kontrola | Výsledok |
|---|---|
| Reálny OAuth2/PKCE login (realm `test-user`) | **PASS** — landing presne na `http://localhost:5173/`, `/api/auth/me` → `200 authenticated:true` |
| `GET /api/auth/csrf` tvar | **PASS** — presne `token`/`headerName`/`parameterName`, `headerName=X-CSRF-TOKEN`, `parameterName=_csrf` (skutočné Spring defaulty) |
| `POST /logout` **bez** CSRF | **PASS** — `403`; session **preukázateľne prežila** (`/api/auth/me` hneď potom stále `200 authenticated:true`) |
| `POST /logout` **s** platným CSRF | **PASS** (po oprave sekcie 6) — `302` na Keycloak end-session s `id_token_hint=`/`post_logout_redirect_uri=http://localhost:5173/` |
| Keycloak end-session odpoveď | **PASS** — `302`, `Set-Cookie: KEYCLOAK_IDENTITY=; ...Max-Age=0` (Keycloak **skutočne** zmazal vlastnú SSO cookie) |
| Finálny post-logout redirect | **PASS** — presne `http://localhost:5173/`, `200 OK` |
| `/api/auth/me` po logout-e | **PASS** — `401 {"authenticated":false}` |
| `/api/protected/hello` so starou (odhlásenou) cookie | **PASS** — `401` (nielen `/api/auth/me`, aj iné chránené API) |
| Opätovné kliknutie Login po logout-e | **PASS** — vrátil **skutočný Keycloak login formulár** (`kc-form-login` prítomný v HTML), **nie** tichý SSO redirect s kódom — potvrdzuje, že Keycloak SSO session bola skutočne ukončená, nielen lokálna Spring session |
| Druhý kompletný login/logout cyklus | **PASS** — funguje identicky, čerstvý CSRF token, reálny login formulár po logout-e opäť prítomný |
| Tretí login + pokus o `POST /logout` s CSRF tokenom z **inej, už mŕtvej** session | **PASS** — `403`; session **preukázateľne prežila** (potvrdzuje, že CSRF tokeny nie sú prenosné naprieč session hranicami) |
| `Cache-Control` na `/api/auth/me` a `/api/protected/hello` | **PASS** — `no-store` resp. `no-cache, no-store, max-age=0, must-revalidate` na oboch |

Vo všetkých prípadoch, kde odpoveď obsahovala `id_token_hint`/`state` v
query stringu, bola hodnota v tomto reporte a vo výstupoch príkazov
**redigovaná** (nikdy nevypísaná v plnom znení).

---

## 10. Finálny bezpečnostný review

Vykonal `bff-security-architect` nad skutočným kódom a vyššie uvedeným
dôkazom.

**Výsledok: PASS.** Žiadny CONFIRMED FINDING. Nezávisle potvrdil všetkých 7
záväzných bodov z pred-implementačného review, trailing-slash opravu (a, b),
absenciu ďalších výskytov rovnakej triedy bugu (c), a — **nezávisle inou
metódou** (`IllegalArgumentException`-argument namiesto priameho čítania
zdroja, keďže sám nemal shell prístup) — potvrdil netautologickosť
mandátneho testu zo sekcie 5.

Hodnotenie CSRF/session hranice pri login→logout→re-login: **nízke
reziduálne riziko** — `frontend/src/auth/csrf.ts` nikdy necachuje token
(žiadna modulová premenná, žiadne úložisko), `fetchCsrfToken()` sa volá
vždy čerstvo tesne pred unsafe requestom, takže neexistuje kódová cesta,
ktorá by mohla predložiť starý token naprieč session hranicou. Toto bolo
navyše priamo behovo overené orchestrujúcou reláciou v sekcii 9 (tretí
cyklus, cudzí token → `403`).

Explicitné stanovisko architekta k chýbajúcej browser validácii: **NOT
VERIFIED, ale nie blokujúce** pre uzavretie bezpečnostných/architektonických
cieľov Stage 2C — hĺbka skriptovaného HTTP dôkazu (reálny PKCE login,
reálne 403/200 rozlíšenie podľa CSRF, reálne zmazanie Keycloak
`KEYCLOAK_IDENTITY` cookie, reálne 401 na dvoch rôznych endpointoch po
logout-e, reálny login formulár namiesto tichého SSO obchádzania) plus
prechádzajúce test suites toto hodnotí ako UX-polish/regression-safety-net
overenie, nie kontrolu, o ktorej by zozbieraný dôkaz nechával skutočnú
pochybnosť.

### Security constraints (pre budúcich maintainerov, z finálneho review)

- Nepridávať `.csrf(...)` customizáciu — zero-config default je zámerný a
  load-bearing.
- Nemeniť `setPostLogoutRedirectUri(...)` bez re-overenia presnej zhody
  reťazca voči Keycloak `post.logout.redirect.uris`.
- Budúce pridanie druhej OAuth2AuthorizedClient-nesúcej registrácie musí
  rozšíriť `LogoutHandler` (aktuálne hardcoded `"bff-app"`).
- Frontend musí naďalej fetch-ovať čerstvý CSRF token tesne pred každým
  unsafe requestom, nikdy ho necachovať.
- `LogoutButton` musí zostať reálny form POST, nie fetch/XHR.

---

## 11. Historické zistenie bfcache rizika (kódová oprava v §15.3, aktuálna validácia v §16)

Pri kontrole frontend kódu (nie súčasť súborov daných finálnemu
architektovi na review, keďže `SessionContext.tsx` nebol touto etapou
menený) orchestrujúca relácia zistila: `frontend/src/auth/SessionContext.tsx`
počúva len na `focus` a `visibilitychange` eventy (zo Stage 2B), **nie** na
`pageshow` s kontrolou `event.persisted`. Úloha Stage 2C explicitne
vyžaduje: *"Protected data must not reappear from stale frontend state when
navigating Back or refocusing the app."*

Pre SPA client-side routing (react-router `pushState`) toto pravdepodobne
nie je problém — interné route zmeny nevytvárajú samostatné
bfcache-oprávnené dokumenty. Reálne riziko je užšie: ak prehliadač po
logout-e (skutočná plná navigácia cez form POST → redirect reťazec →
nový dokument) uloží do bfcache predchádzajúci plný dokument (napr. pôvodné
načítanie appky v authenticated stave) a používateľ stlačí Back, stránka by
sa mohla momentálne obnoviť z bfcache bez toho, aby `focus`/`visibilitychange`
vôbec vystrelili (tab nemusel stratiť fokus ani viditeľnosť) — teda bez
nového `/api/auth/me` recheck-u.

**Toto nebolo možné overiť** bez reálneho prehliadača (bfcache je čisto
prehliadačový mechanizmus, curl ho nevie reprodukovať). Nebolo pridané
žiadne špekulatívne riešenie (napr. `pageshow` listener) bez behového
dôkazu, že problém skutočne nastáva — v súlade so zásadou neriešiť
neoverené hypotézy mimo schváleného rozsahu. **Explicitne NOT VERIFIED**,
odporúčané ako prvá vec na otestovanie v reálnom prehliadači pri
nasledujúcom kole.

---

## 12. Zámerne neimplementované (odložené na neskoršie etapy)

USER/ADMIN role mapping, role-based autorizácia, business mutácie,
explicitné spracovanie refresh tokenu, globálny logout naprieč zariadeniami,
administratívna revokácia session, back-channel/front-channel logout
receivers, session concurrency limity, registrácia, reset hesla, MFA,
vlastný OAuth logout protokol.

Nič z uvedeného nebolo reportované ako defekt Stage 2C.

---

## 13. Historický stav pred uzatváracím kolom (aktuálny stav v §16)

Žiadny CRITICAL/HIGH/MEDIUM nález nezostáva otvorený — trailing-slash bug
(sekcia 6) bol nájdený a opravený v tejto relácii, mandátny CRITICAL bod
(odstránenie authorized-clienta) overený dvoma nezávislými metódami.

1. **NOT VERIFIED** — reálna browser validácia (klikanie na Logout tlačidlo
   v skutočnom prehliadači, vizuálne loading/error/duplicate-click stavy,
   priama inšpekcia `localStorage`/`sessionStorage`/cookies tohto kola,
   druhý live-browser login/logout cyklus, Back/refresh po logout-e) —
   `claude-in-chrome` bol nedostupný (3 pokusy o pripojenie). Finálny
   architekt review to hodnotí ako nie blokujúce vzhľadom na hĺbku HTTP
   dôkazu, ale odporúča ako prvý krok ďalšieho kola.
2. **Riziko, NOT VERIFIED** — chýbajúci `pageshow`/bfcache-restoration
   listener v `SessionContext.tsx` (sekcia 11) — potenciálne stale UI po
   Back stlačení post-logout, neoveriteľné bez reálneho prehliadača.
3. Presný kombinovaný príkaz `docker compose up --build` **nebol v tomto
   kole znovu testovaný** (bol použitý dvojkrokový `docker compose build` +
   `up -d` workaround, zdokumentovaný ako default od Stage 2A) — na rozdiel
   od Stage 2B uzatváracieho kola, kde bol tento presný príkaz explicitne
   znovu overený. Neoznačujem ho ako otestovaný v tejto relácii.
4. Prenesené zo Stage 2A/2B, stále platné a mimo rozsahu Stage 2C:
   chýbajúci explicitný `SameSite` na session cookie; presná príčina
   historického `docker-buildx bake` hangu zostáva nepotvrdená.

---

## 14. Historický súhrn overenia (aktuálny stav v §16)

**Overené (s konkrétnym dôkazom, PASS):**

- Backend testy 16/16, frontend testy 22/22, frontend build,
  `docker compose config` — všetky spustené priamo touto reláciou.
- Mandátny test odstránenia `OAuth2AuthorizedClient` záznamu overený ako
  netautologický dvoma nezávislými metódami (orchestrátor: priame čítanie
  zdroja; finálny architekt: argument cez `IllegalArgumentException`).
- Reálny trailing-slash bug nájdený end-to-end proti bežiacemu stacku,
  opravený, overený vlastným regresným testom (zlyhá bez opravy) aj
  opätovným end-to-end behom po oprave.
- Kompletný reálny OAuth2/PKCE login → CSRF bootstrap → CSRF enforcement
  (403 bez tokenu, session prežije) → CSRF-valid logout → skutočné
  zmazanie Keycloak `KEYCLOAK_IDENTITY` cookie → návrat na `http://localhost:5173/`
  → `401` na `/api/auth/me` aj `/api/protected/hello` → opätovný Login
  ukazuje reálny formulár (nie tichý SSO bypass) — **trikrát nezávisle
  zopakované**, vrátane testu cudzieho CSRF tokenu naprieč session hranicou.
- Keycloak `post.logout.redirect.uris` atribút aplikovaný na už bežiacu,
  volume-persistovanú inštanciu neduštruktívne cez Admin REST API — žiadne
  volume ani dáta zmazané.
- Finálny `bff-security-architect` review: **PASS**, žiadny CONFIRMED
  FINDING.

**NOT VERIFIED (chýba priamy dôkaz, nie je to ale CONFIRMED FINDING):**

- Reálna rendered-browser validácia (Logout klik, vizuálne stavy, storage
  inšpekcia, Back/refresh po logout-e) — nástroj nedostupný v tomto kole.
- `pageshow`/bfcache-restoration UI staleness riziko (sekcia 11).
- Presný kombinovaný `docker compose up --build` príkaz nebol v tomto kole
  znovu testovaný.

**Nič nebolo zmazané (vrátane Docker volumes/dát), commitnuté ani
pushnuté.** Docker stack zostáva bežať (`docker compose ps` → všetky 4
služby `healthy`) pre prípadné ďalšie manuálne overenie.

---

## 15. Uzatváracie kolo: Host-header redirect zraniteľnosti, bfcache fix, čakajúca browser validácia

Toto kolo bolo vyžiadané explicitne so štyrmi konkrétnymi bodmi pred
uzavretím Stage 2C, s výslovným pripomenutím, že HTTP testy ani architektov
PASS nenahrádzajú reálnu browser validáciu. **Táto relácia stage
neuzatvára** — pozri 15.5.

### 15.1 Bod 4 — post-logout redirect nesmie závisieť od Host hlavičky

Pôvodná implementácia (`handler.setPostLogoutRedirectUri("{baseUrl}/")`)
bola presne to, na čo používateľ upozornil: `{baseUrl}` sa vyhodnocuje z
`request.getServerName()`/`getServerPort()`, ktoré tento proxy stack
odvodzuje priamo z prichádzajúcej `Host` hlavičky (`nginx.conf`/
`vite.config.ts` ju posielajú ako `$http_host` bez akéhokoľvek allowlistu;
`server.forward-headers-strategy` nie je nastavené).

**Overenie zraniteľnosti (pred opravou, proti reálne bežiacemu Docker
stacku)**: reálny prihlásený session + platný CSRF token + `POST /logout`
s falšovanou `Host: attacker.example` hlavičkou → BFF vrátil `Location`
smerujúci na Keycloak s **`post_logout_redirect_uri=http://attacker.example/`**
— teda BFF sám vypočítal a odovzdal Keycloak-u útočníkom kontrolovanú
hodnotu. Keycloak vlastný `post.logout.redirect.uris` allowlist túto
konkrétnu hodnotu odmietol (`400 Bad Request`) — čo je len náhodná obrana
zo strany Keycloak-u, nie niečo, na čo sa BFF smie spoliehať ako na jedinú
ochranu (presne vzor "odvodenie externého presmerovania z nedôveryhodnej
Host hlavičky", ktorý invarianty projektu zakazujú).

**Oprava**: nová konfiguračná vlastnosť `bff.oauth2.post-logout-redirect-uri`
(`OAUTH2_POST_LOGOUT_REDIRECT_URI`, default `http://localhost:5173/`,
presne zhodná s Keycloak `post.logout.redirect.uris`) — `SecurityConfig.java`
teraz volá `handler.setPostLogoutRedirectUri(postLogoutRedirectUri)` s
týmto pevným literálom namiesto `{baseUrl}`.

**Overenie opravy**:
- Nový test `LogoutTest.postLogoutRedirectIsFixedAndIgnoresForgedHostHeader`
  (falšuje `request.setServerName("attacker.example")` cez MockMvc
  `RequestPostProcessor` — priamy ekvivalent falšovanej `Host` hlavičky
  dopadajúcej na reálny servlet kontajner cez tento Host-forwardujúci
  proxy) — assertuje, že `Location` neobsahuje `"attacker.example"` a
  obsahuje presne nakonfigurovanú hodnotu. **Zámerne spustený proti
  neopravenému kódu** (dočasné vrátenie na `{baseUrl}/`) — **zlyhal presne
  s očakávanou hodnotou** `post_logout_redirect_uri=http://attacker.example/`
  v `Location` hlavičke. Po vrátení opravy — PASS, 17/17 backend testov.
- **Reálne, druhýkrát, proti prekompilovanému a znovu nasadenému Docker
  stacku**: rovnaký falšovaný `Host: attacker.example` test → `Location`
  teraz obsahuje presne `post_logout_redirect_uri=http://localhost:5173/`,
  žiadna zmienka o `attacker.example`.

### 15.2 Bonusový nález: identická zraniteľnosť v post-LOGIN redirecte (Stage 2B kód, nie Stage 2C)

Pri cielenom re-review opravy z 15.1 `bff-security-architect` **nezávisle
našiel** rovnakú triedu zraniteľnosti v `.defaultSuccessUrl("/", true)` —
pôvodne zo Stage 2B, nedotknuté touto etapou. Orchestrujúca relácia to
okamžite overila naživo proti reálnemu stacku:

- Reálny prihlasovací OAuth2 callback (`GET /login/oauth2/code/bff-app?code=...`)
  s falšovanou `Host: attacker.example` hlavičkou → **`Location: http://attacker.example/`
  priamo** — **závažnejšie než logout prípad**: žiadna Keycloak allowlist
  kontrola tu vôbec nezasahuje (toto je čisto interné Spring presmerovanie
  po úspešnom, reálnom prihlásení), takže by prehliadač reálne skončil na
  útočníkovej doméne ihneď po platnom prihlásení.
- **Rozhodnutie**: keďže ide o rovnaký súbor (`SecurityConfig.java`), rovnaký
  koreňový problém, a mal som už overený vzor opravy aj nástroje po ruke,
  opravil som to v rámci tohto kola ako priamo súvisiacu, úzko zacielenú
  prácu (nie špekulatívne rozšírenie rozsahu) — transparentne zdokumentované
  tu ako dodatočná práca nad rámec pôvodných štyroch bodov.
- **Oprava**: nová vlastnosť `bff.oauth2.post-login-redirect-uri`
  (`OAUTH2_POST_LOGIN_REDIRECT_URI`, default `http://localhost:5173/`,
  absolútna, schémou kvalifikovaná literálna hodnota). `.defaultSuccessUrl("/", true)`
  → `.defaultSuccessUrl(postLoginRedirectUri, true)`.
- **Overenie mechanizmu zo zdrojového kódu**: `AbstractAuthenticationTargetUrlRequestHandler.setDefaultTargetUrl`
  Javadoc explicitne dokumentuje, že absolútna URL (so schémou) je
  podporovaná; `DefaultRedirectStrategy.calculateRedirectUrl` pri
  `contextRelative=false` (default, nikde v projekte nezmenené) pre
  absolútnu URL priamo vracia `return url;` — bez akejkoľvek účasti dát z
  requestu.
- **Dôležité obmedzenie testovacej infraštruktúry, zdokumentované čestne, nie
  zamlčané**: `MockHttpServletResponse.sendRedirect()` (Spring test double)
  NEreprodukuje správanie reálneho servlet kontajnera, ktorý relatívny cieľ
  expanduje na absolútny `Location` pomocou dát z requestu — ukladá
  hocijaký reťazec doslovne. Pokus napísať MockMvc test, ktorý by dokázal
  zraniteľnosť pri relatívnom `"/"` cieli, preto **zlyhal na overenie**
  (`MockHttpServletResponse.getRedirectedUrl()` vrátil doslovne `"/"`, nie
  expandovanú URL s falšovaným hostom) — nie chyba opravy, ale limit
  Mock-infraštruktúry. Pokus o plnohodnotný test cez skutočný embedded
  Tomcat (`TestRestTemplate`) narazil na to, že táto trieda bola **v Spring
  Boot 4.1.1 odstránená/premiestnená** (nenájdená v žiadnom rozlíšenom
  `spring-boot-test` jare) — pridanie novej testovej závislosti len pre
  tento jeden test bolo vyhodnotené ako neproporcionálne rozšírenie mimo
  aktuálnej priority (reálna browser validácia), preto **zámerne
  nedokončené**. Ponechaný je menší, čestne okomentovaný unit test
  (`SecurityConfigTest.configuredAbsolutePostLoginRedirectIsReturnedVerbatim`),
  ktorý potvrdzuje, že nakonfigurovaná hodnota sa vracia doslovne, s
  explicitným Javadoc komentárom vysvetľujúcim, že sám osebe nedokazuje
  bezpečnosť voči falšovanému hostu (to dokazuje len reálny beh nižšie).
- **Overenie opravy naživo, dvakrát (pred aj po), proti reálne bežiacemu
  Docker stacku** (nie iba unit test): pred opravou — potvrdené
  `Location: http://attacker.example/`; po prekompilovaní a redeployi
  backendu — rovnaký falšovaný Host test teraz vracia presne
  `Location: http://localhost:5173/`.

### 15.3 Bod 2 — `pageshow`/bfcache re-check v `SessionContext.tsx`

`frontend/src/auth/SessionContext.tsx` (zo Stage 2B) počúval len na
`focus`/`visibilitychange`. Pridaný tretí `useEffect` s `pageshow`
listenerom: pri `event.persisted === true` (bfcache obnovenie) volá
`load(true)` (nie `load(false)`) — zámerne, aby sa prípadný stale
"authenticated" obsah okamžite skryl za loading stav, kým prebehne
recheck.

**Dva nové testy** v `SessionContext.test.tsx`:
1. simuluje bfcache obnovenie (`persisted: true`) po reálnom sign-oute,
   ktorý sa medzitým stal — assertuje `authenticated` → `loading`
   (synchrónne, pred vyriešením fetchu) → `anonymous`;
2. potvrdzuje, že bežný `pageshow` (`persisted: false`) NEvyvolá druhý
   fetch.

**Overenie, že testy nie sú tautologické**: dočasne odstránený celý nový
`useEffect`/listener — prvý test **zlyhal** (finálny stav nesprávne ostal
`authenticated` namiesto prechodu cez `loading`/`anonymous`). Po vrátení
opravy — PASS, 24/24 frontend testov.

**Cielený `bff-security-architect` re-review** (spolu s bodom 15.1)
potvrdil: `load(true)` je správna voľba (neprevádza sa na obyčajný
`pageshow` bez `persisted`, takže sa nevracia pôvodný Stage 2B problém s
"flash chráneného obsahu"); nové listenery prechádzajú cez tú istú `load()`
funkciu a `generationRef` guard, takže nevzniká nové race-condition okno.
Explicitne poznamenal (a orchestrátor súhlasí): **skutočné bfcache
správanie reálneho prehliadača pre presné navigačné/redirect vzory tejto
appky (najmä po `POST /logout` → Keycloak → späť) nebolo a nemôže byť
overené jsdom simuláciou** — vyžaduje reálny prehliadač.

### 15.4 Bod 7 — neduštruktívna aplikácia zostáva v platnosti

Žiadna zmena od sekcie 7 — `post.logout.redirect.uris` zostáva aplikovaný
na živú Keycloak inštanciu, žiadny volume ani dáta neboli v tomto kole
zmazané. Nová `bff.oauth2.post-login-redirect-uri`/`post-logout-redirect-uri`
konfigurácia sa týka len backend kontajnera (env premenné v `compose.yaml`),
nevyžaduje žiadnu zmenu na Keycloak strane.

### 15.5 Body 1 a 3 — BLOKOVANÉ: `claude-in-chrome` nedostupný

Napriek opakovaným pokusom o pripojenie (viac než 5× v priebehu tohto kola,
v pravidelných intervaloch počas práce na bodoch 2/4) nástroj hlásil
"Browser extension is not connected" pri každom pokuse. V súlade s
pokynom nebolo pokračované v ďalšom bezhlavom opakovaní pokusov.

**Preto výslovne BLOKOVANÉ a NOT VERIFIED, presne ako bolo zadané**:

- **Bod 1**: reálny Login → Logout → Keycloak → verejná home v skutočnom
  prehliadači, druhý kompletný cyklus, overenie že nové prihlásenie
  vyžaduje reálny Keycloak login formulár (nie tichý SSO bypass) a že
  chránené API po odhlásení vracajú `401`. **Nahradené v tomto kole len
  opakovanou skriptovanou HTTP validáciou** (sekcie 9 a 15.1/15.2 vyššie) —
  čo používateľ explicitne označil za nedostatočnú náhradu za skutočnú
  browser validáciu. Neoznačujem to za ekvivalentné.
- **Bod 2 (živá časť)**: skutočné Back/refresh/návrat na kartu po
  odhlásení v reálnom prehliadači, potvrdzujúce, že `pageshow` fix zo
  sekcie 15.3 skutočne zabraňuje stale UI v praxi (kód a jsdom testy sú
  hotové a overené, ale skutočné prehliadačové bfcache správanie nie).
- **Bod 3**: `localStorage` aj `sessionStorage` neboli v tomto kole vôbec
  skontrolované (nástroj nedostupný od začiatku kola, nie len blokujúci
  konkrétne volanie ako v predchádzajúcom Stage 2B uzatváracom kole) —
  **obe explicitne NOT VERIFIED**, žiadny náhradný dôkaz nepoužitý.

**Presný kombinovaný `docker compose up --build` príkaz** tiež nebol v
tomto kole znovu testovaný (použitý bol dvojkrokový `docker compose build`
+ `up -d` postup, opakovane, pre backend aj pre celý stack) — zostáva
NOT VERIFIED pre tento presný príkaz v tomto kole.

**Odporúčaný ďalší krok**: pripojiť `claude-in-chrome` (reštart Chrome
prehliadača/rozšírenia môže byť potrebný) a zopakovať presne body 1–3 zo
zadania tohto kola. Do tej doby Stage 2C **zostáva otvorená**, nie
uzavretá — v súlade s výslovnou požiadavkou, že HTTP testy a architektov
PASS bez browser dôkazu nestačia na vyhlásenie etapy za hotovú.

### 15.6 Zhrnutie zmien súborov v tomto kole

- `backend/src/main/resources/application.yml` — nové `bff.oauth2.post-login-redirect-uri`,
  `bff.oauth2.post-logout-redirect-uri`.
- `compose.yaml` — nové `OAUTH2_POST_LOGIN_REDIRECT_URI`,
  `OAUTH2_POST_LOGOUT_REDIRECT_URI` env premenné pre `backend`.
- `backend/src/main/java/com/example/bff/config/SecurityConfig.java` —
  `.defaultSuccessUrl(postLoginRedirectUri, true)` namiesto `"/"`;
  `oidcLogoutSuccessHandler(...)` teraz berie `postLogoutRedirectUri` ako
  parameter namiesto `"{baseUrl}/"`; opravené nesprávne tvrdenie v Javadoc
  komentári (pôvodne tvrdil, že relatívna cesta je bezpečná — nebola).
- `backend/src/test/java/com/example/bff/config/LogoutTest.java` — nový
  test `postLogoutRedirectIsFixedAndIgnoresForgedHostHeader`; upravená
  existujúca assercia na presnú nakonfigurovanú hodnotu.
- `backend/src/test/java/com/example/bff/config/SecurityConfigTest.java` —
  nový, čestne okomentovaný test
  `configuredAbsolutePostLoginRedirectIsReturnedVerbatim`.
- `frontend/src/auth/SessionContext.tsx` — nový `pageshow`/`persisted`
  `useEffect`.
- `frontend/src/auth/SessionContext.test.tsx` — dva nové testy.

Testy po tomto kole: **backend 18/18**, **frontend 24/24**, oba spustené
priamo touto reláciou, oba PASS pred aj po dočasnom zámernom vrátení
každej opravy (regresné testy potvrdené ako netautologické).

### 15.7 Cielený architektov re-review tohto kola

**Výsledok: PASS WITH FINDINGS** (nález = bonusová zraniteľnosť opísaná v
15.2, ktorá bola v rámci toho istého kola aj opravená — nie otvorený
nález).

Kľúčové body z jeho analýzy: potvrdil oba fixy (15.1, 15.3) ako štrukturálne
správne a testy ako genuine/netautologické; sám našiel a zdokumentoval
bonusovú `defaultSuccessUrl` zraniteľnosť (odvodenú zo zdrojového kódu a
zdokumentovaného servlet-kontajner správania, nie z vlastného live
exploitu — tú časť dôkazu dodala orchestrujúca relácia naživo, sekcia 15.2);
explicitne potvrdil, že chýbajúca reálna browser validácia pre `pageshow`
fix je legitímna medzera (nie niečo, čo by static review mohol nahradiť).

---


---

## 16. Nezávislé overenie Codex — 2026-09-17

Vykonal Codex priamo na aktuálnej pracovnej vetve
`feature/2c-csrf-logout`. Nejde o prevzatie výsledkov implementera.
Použitý bol reálny **Codex In-app Browser**, nie curl ani jsdom.
Chrome rozšírenie Claude nebolo potrebné na vykonané UI scenáre.

### 16.1 Priamo vykonané kontroly

| Kontrola | Výsledok | Priamy dôkaz |
|---|---|---|
| Stav Docker stacku | PASS | `docker compose ps`: všetky štyri služby healthy |
| Backend testy aktuálneho pracovného stromu | PASS | `./mvnw -o test`: 18 testov, 0 failures/errors/skipped, BUILD SUCCESS |
| Frontend testy aktuálneho pracovného stromu | PASS | `npm test -- --run`: 7 súborov, 24 testov PASS |
| Anonymná home | PASS | Na `/` je Login, bez používateľa a Logout tlačidla |
| Prvý reálny login | PASS | Klik na Login, skutočný Keycloak formulár, zadanie existujúcej disposable lokálnej identity, návrat na `/`, „Signed in as Test User“ a Log out |
| Chránená stránka a refresh | PASS | Klik na `/protected` zobrazil „authenticated“; reload zachoval funkčný prihlásený stav |
| Prvý reálny logout | PASS | Klik na Log out v vykreslenej aplikácii skončil na anonymnej home s Login |
| Späť a refresh po prvom logout-e | PASS pre pozorovaný scenár | Skutočné browser Back a následný reload zobrazili anonymnú home, bez starého používateľa a chráneného obsahu v zachytenom UI |
| Login po prvom logout-e | PASS | Znovu sa zobrazil skutočný Keycloak formulár; neprebehlo tiché SSO prihlásenie |
| Druhý kompletný login/logout | PASS | Opätovné vyplnenie Keycloak formulára → authenticated home → klik na Log out → anonymous home |
| Priama `/protected` navigácia a refresh po druhom logout-e | PASS | Obe zobrazili „You must be signed in to view this page.“ a Login, bez chránených dát |
| Login po druhom logout-e | PASS | Opäť skutočný Keycloak formulár bez tichého SSO; ďalšie prihlásenie už nebolo vykonané |
| Finálny stav prehliadača | PASS | Aplikácia ponechaná na anonymnej home `/` |

Browser scenáre neodhalili trvalo obnovený stale authenticated obsah.
Pozorovanie vykresleného UI však nie je meranie jednotlivých frame-ov a
nepotvrdzuje neprítomnosť krátkeho vizuálneho záblesku.
Presné HTTP 401 po logout-e a interné odstránenie authorized-clienta zostávajú
podložené predchádzajúcou HTTP validáciou a aktuálne prechádzajúcimi testami;
Codex v tomto kole nezachytával stavové kódy browser sieťových požiadaviek.

### 16.2 Cielená kontrola finálneho kódu

Codex priamo prečítal `SecurityConfig.java`, `SecurityConfigTest.java`,
`SessionContext.tsx`, `LogoutButton.tsx` a `csrf.ts`.

- Login používa `.defaultSuccessUrl(postLoginRedirectUri, true)` a logout
  `setPostLogoutRedirectUri(postLogoutRedirectUri)`, obe hodnoty sú injektované
  z konfigurácie. V týchto cieľoch už nie je relatívne `/` ani `{baseUrl}`.
- `pageshow` listener pri `persisted === true` volá `load(true)`; pokračuje
  cez existujúcu kontrolu generácie odpovede. Samotný listener nepreukazuje,
  že testovaná navigácia využila bfcache.
- Logout tlačidlo získava čerstvý CSRF token a odosiela skutočný form POST;
  reálne odoslanie bolo potvrdené oboma úspešnými browser cyklami.
- Toto je nezávislá cielená kontrola Codex, nie nová invokácia pomenovaného
  `bff-security-architect` agenta ani úplný audit celého projektu.

### 16.3 Čo zostáva otvorené

1. **Priame `localStorage`/`sessionStorage`: NOT VERIFIED.** Dostupné
   read-only vyhodnocovanie stránky nesprístupnilo storage API (pokus vrátil
   TypeError pri čítaní `length`, nie obsah ani počet položiek). Následný
   pokus o prístup k Chrome/DevTools cez natívne ovládanie skončil správou
   „Computer Use permissions are not granted“. Oprávnenie nebolo obchádzané.
   Nejde o aplikačný nález a žiadne úložisko nebolo označené ako prázdne.
2. **Explicitné bfcache obnovenie (`pageshow.persisted === true`) a návrat
   na kartu: NOT VERIFIED.** Skutočný Back/reload bol vykonaný a UI zostalo
   anonymné, ale nebolo instrumentované, či dokument prišiel práve z bfcache.
3. **Presný `docker compose up --build` nebol v tomto kole znovu spustený.**
   Existujúci stack bol zdravý; historické zistenia sa tým nemenia.

Pre úplné uzavretie zostáva kontrola úložísk cez povolené DevTools alebo
manuálne používateľom a doplnenie navigačných scenárov uvedených vyššie.
Nevyžaduje sa nové funkčné rozšírenie ani ďalšie opakovanie už úspešných
backend/frontend testov bez novej zmeny.

Codex v tomto kole upravil iba tento report. Implementáciu, index Gitu,
Docker volumes ani dáta nemenil; nič necommitol ani nepushol.
