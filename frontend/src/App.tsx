import { BackendStatus } from "./BackendStatus";

/**
 * Stage 2A: a plain same-origin link that performs a real top-level browser
 * navigation to the Spring Security OAuth2 authorization endpoint - proxied
 * to the backend BFF in both dev (Vite) and Docker (nginx). This is
 * intentionally NOT a fetch/axios call: the browser must follow the full
 * server-driven redirect chain (BFF -> Keycloak -> BFF callback) itself.
 *
 * React never sees an authorization code, token, or client secret as part
 * of this - only the resulting authenticated session cookie set by the BFF
 * once the flow completes.
 */
function App() {
  return (
    <main>
      <h1>Spring Keycloak BFF</h1>
      <BackendStatus />
      <a href="/oauth2/authorization/bff-app">Login</a>
    </main>
  );
}

export default App;
