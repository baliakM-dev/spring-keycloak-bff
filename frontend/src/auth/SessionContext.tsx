import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";

/**
 * Minimal, application-owned view of the authenticated user, mirroring the
 * backend's {@code UserView} shape from {@code GET /api/auth/me}. Never
 * contains tokens, raw OIDC claims, or roles.
 */
export interface UserView {
  id: string;
  displayName: string;
}

/**
 * Shared session-state shape for the whole app.
 *
 * - "loading": initial bootstrap in flight - render minimal loading UI only,
 *   never protected content, never an auto-triggered login.
 * - "authenticated"/"anonymous": resolved session state from the BFF.
 * - "error": the session check itself failed (network/server error) - this
 *   is NOT the same as being signed out, and must be retryable.
 */
export type SessionState =
  | { status: "loading" }
  | { status: "authenticated"; user: UserView }
  | { status: "anonymous" }
  | { status: "error" };

interface MeResponseBody {
  authenticated: boolean;
  user?: UserView;
}

interface SessionContextValue {
  session: SessionState;
  /** Re-checks the session against the BFF without forcing a loading flash. */
  refresh: () => void;
  /**
   * Centralized 401 handling: any component that receives a 401 from a
   * protected API call (not 403) should call this to immediately clear
   * stale authenticated state and show the login gate. Never call this from
   * a 403 - that is not a sign-out signal.
   */
  reportUnauthorized: () => void;
}

const SessionContext = createContext<SessionContextValue | undefined>(undefined);

export function SessionProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<SessionState>({ status: "loading" });

  // Guards against a late/out-of-order /api/auth/me response repopulating
  // authenticated state after a more recent 401 (or a more recent refresh)
  // already changed the session. Every fetch captures the generation at the
  // time it started; a response is only applied if the generation is still
  // current when it resolves.
  const generationRef = useRef(0);

  const load = useCallback((showLoading: boolean) => {
    const generation = ++generationRef.current;
    if (showLoading) {
      setSession({ status: "loading" });
    }

    fetch("/api/auth/me", { credentials: "same-origin" })
      .then(async (response) => {
        if (generationRef.current !== generation) {
          return;
        }
        if (response.status === 200) {
          const body = (await response.json()) as MeResponseBody;
          if (generationRef.current !== generation) {
            return;
          }
          if (body.authenticated && body.user) {
            setSession({ status: "authenticated", user: body.user });
          } else {
            setSession({ status: "anonymous" });
          }
          return;
        }
        if (response.status === 401) {
          setSession({ status: "anonymous" });
          return;
        }
        setSession({ status: "error" });
      })
      .catch(() => {
        if (generationRef.current !== generation) {
          return;
        }
        setSession({ status: "error" });
      });
  }, []);

  // Bootstrap on mount.
  useEffect(() => {
    load(true);
  }, [load]);

  // Re-check on window focus / visibilitychange - not polling.
  useEffect(() => {
    const handleRecheck = () => {
      if (document.visibilityState === "hidden") {
        return;
      }
      load(false);
    };
    window.addEventListener("focus", handleRecheck);
    document.addEventListener("visibilitychange", handleRecheck);
    return () => {
      window.removeEventListener("focus", handleRecheck);
      document.removeEventListener("visibilitychange", handleRecheck);
    };
  }, [load]);

  // Stage 2C: re-check on bfcache restoration (browser Back/Forward after a
  // real navigation, e.g. the full-page navigation logout performs). A
  // `pageshow` event with `persisted: true` fires when the browser instantly
  // repaints a frozen, previously-rendered page from its back/forward cache
  // - including whatever "authenticated" UI was on screen before the user
  // navigated away - without re-running any of this component's mount
  // logic. Neither `focus` nor `visibilitychange` are guaranteed to fire in
  // this case: the tab need not have lost focus or become hidden for the
  // browser to serve a bfcache entry (e.g. Back within the same, still-
  // focused, still-visible tab). `load(true)` (not `load(false)`) is used
  // deliberately: it forces an immediate "loading" state, hiding the
  // stale bfcache-painted content while the re-check is in flight, rather
  // than leaving stale authenticated UI on screen until the fetch resolves.
  useEffect(() => {
    const handlePageShow = (event: PageTransitionEvent) => {
      if (event.persisted) {
        load(true);
      }
    };
    window.addEventListener("pageshow", handlePageShow);
    return () => {
      window.removeEventListener("pageshow", handlePageShow);
    };
  }, [load]);

  const refresh = useCallback(() => load(false), [load]);

  const reportUnauthorized = useCallback(() => {
    // Bump the generation first so any /api/auth/me request already in
    // flight cannot overwrite this with a stale "authenticated" result.
    generationRef.current += 1;
    setSession({ status: "anonymous" });
  }, []);

  return (
    <SessionContext.Provider value={{ session, refresh, reportUnauthorized }}>{children}</SessionContext.Provider>
  );
}

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession must be used within a SessionProvider");
  }
  return context;
}
