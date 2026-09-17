import { BrowserRouter, Route, Routes } from "react-router";
import { SessionProvider } from "./auth/SessionContext";
import { HomePage } from "./pages/HomePage";
import { ProtectedPage } from "./pages/ProtectedPage";

/**
 * Stage 2B: a minimal client-side router so {@code /protected} works as a
 * route. This is UX-only routing - it does not participate in
 * authentication. Session state is bootstrapped once here (via
 * {@link SessionProvider}) and shared by every route through
 * {@code useSession()}.
 *
 * The Login control itself (rendered by {@code HomePage}/{@code
 * ProtectedPage}) remains a plain same-origin link performing a real
 * top-level browser navigation to the Spring Security OAuth2 authorization
 * endpoint - see Stage 2A notes previously here. This is intentionally NOT
 * a fetch/axios call: the browser must follow the full server-driven
 * redirect chain (BFF -> Keycloak -> BFF callback) itself. React never sees
 * an authorization code, token, or client secret as part of this - only the
 * resulting authenticated session cookie set by the BFF once the flow
 * completes.
 */
function App() {
  return (
    <SessionProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/protected" element={<ProtectedPage />} />
        </Routes>
      </BrowserRouter>
    </SessionProvider>
  );
}

export default App;
