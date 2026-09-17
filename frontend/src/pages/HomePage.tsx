import { Link } from "react-router";
import { BackendStatus } from "../BackendStatus";
import { useSession } from "../auth/SessionContext";
import { LogoutButton } from "../auth/LogoutButton";

/**
 * Public home page ({@code /}). Renders the same full-navigation Login
 * control as Stage 2A when signed out, a display name and a link to the
 * protected page when signed in, and a retryable message on session-check
 * failure. Never auto-triggers login.
 */
export function HomePage() {
  const { session, refresh } = useSession();

  return (
    <main>
      <h1>Spring Keycloak BFF</h1>
      <BackendStatus />

      {session.status === "loading" && <p data-testid="session-loading">Loading session...</p>}

      {session.status === "error" && (
        <div data-testid="session-error">
          <p>Could not check your session. Please try again.</p>
          <button type="button" onClick={refresh}>
            Retry
          </button>
        </div>
      )}

      {session.status === "anonymous" && <a href="/oauth2/authorization/bff-app">Login</a>}

      {session.status === "authenticated" && (
        <div data-testid="session-authenticated">
          <p>Signed in as {session.user.displayName}</p>
          <Link to="/protected">Go to protected page</Link>
          <LogoutButton />
        </div>
      )}
    </main>
  );
}
