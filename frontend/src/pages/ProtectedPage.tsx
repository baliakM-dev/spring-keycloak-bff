import { useEffect, useState } from "react";
import { useSession } from "../auth/SessionContext";

interface HelloResponse {
  message: string;
}

type HelloState = { status: "loading" } | { status: "loaded"; message: string } | { status: "error" };

/**
 * Protected page ({@code /protected}). This route guard is UX only - Spring
 * Security enforces the real authorization on {@code GET
 * /api/protected/hello}; an anonymous or session-expired caller here simply
 * sees a login gate instead of the (never fetched) protected data.
 */
export function ProtectedPage() {
  const { session, reportUnauthorized } = useSession();
  const [hello, setHello] = useState<HelloState>({ status: "loading" });

  useEffect(() => {
    if (session.status !== "authenticated") {
      return;
    }

    let cancelled = false;
    setHello({ status: "loading" });

    fetch("/api/protected/hello", { credentials: "same-origin" })
      .then(async (response) => {
        if (cancelled) {
          return;
        }
        if (response.status === 401) {
          // Centralized 401 handling: session expired since the gate above
          // was evaluated - clear stale state and fall back to the login
          // gate. 403 is intentionally not handled here.
          reportUnauthorized();
          return;
        }
        if (!response.ok) {
          setHello({ status: "error" });
          return;
        }
        const body = (await response.json()) as HelloResponse;
        setHello({ status: "loaded", message: body.message });
      })
      .catch(() => {
        if (!cancelled) {
          setHello({ status: "error" });
        }
      });

    return () => {
      cancelled = true;
    };
  }, [session.status, reportUnauthorized]);

  if (session.status === "loading") {
    return <p data-testid="session-loading">Loading session...</p>;
  }

  if (session.status === "error") {
    return <p data-testid="session-error">Could not check your session. Please try again.</p>;
  }

  if (session.status === "anonymous") {
    return (
      <div data-testid="protected-login-gate">
        <p>You must be signed in to view this page.</p>
        <a href="/oauth2/authorization/bff-app">Login</a>
      </div>
    );
  }

  return (
    <div>
      <h2>Protected</h2>
      {hello.status === "loading" && <p>Loading protected data...</p>}
      {hello.status === "error" && <p>Failed to load protected data.</p>}
      {hello.status === "loaded" && <p data-testid="protected-message">{hello.message}</p>}
    </div>
  );
}
