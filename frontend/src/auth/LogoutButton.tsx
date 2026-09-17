import { useRef, useState } from "react";
import { fetchCsrfToken } from "./csrf";

type LogoutStatus = "idle" | "preparing" | "error";

/**
 * Logout control. Rendered only for an authenticated session (see caller -
 * {@code HomePage}).
 *
 * <p>On click, this fetches a fresh CSRF token and submits a real,
 * same-origin, top-level {@code <form method="POST" action="/logout">} -
 * never a {@code fetch}/XHR call - so the browser itself follows the full
 * BFF -> Keycloak -> frontend redirect chain that Spring Security's {@code
 * OidcClientInitiatedLogoutSuccessHandler} drives server-side. React never
 * sees an ID token or builds any logout URL; it only fills in the CSRF
 * field Spring Security's {@code CsrfFilter} requires for the {@code POST}.
 *
 * <p>The duplicate-submission guard ({@code submittingRef}) is a plain
 * ref, not React state: it must take effect synchronously, before the
 * async CSRF fetch starts, so a second rapid click - which can happen
 * before React re-renders the disabled button - is still blocked.
 */
export function LogoutButton() {
  const formRef = useRef<HTMLFormElement>(null);
  const csrfInputRef = useRef<HTMLInputElement>(null);
  const submittingRef = useRef(false);
  const [status, setStatus] = useState<LogoutStatus>("idle");

  const handleLogoutClick = async () => {
    if (submittingRef.current) {
      return;
    }
    submittingRef.current = true;
    setStatus("preparing");

    try {
      const csrf = await fetchCsrfToken();
      const form = formRef.current;
      const csrfInput = csrfInputRef.current;
      if (!form || !csrfInput) {
        throw new Error("Logout form is not ready");
      }
      // Set directly on the DOM nodes (not via React state) so the form
      // submitted immediately below is guaranteed to carry this exact,
      // freshly-fetched token - never a missing or stale one.
      csrfInput.name = csrf.parameterName;
      csrfInput.value = csrf.token;
      form.requestSubmit();
      // Do not reset submittingRef/status here on success: the browser is
      // about to navigate away via the form submission and subsequent
      // redirect chain, so no further clicks are reachable in this view.
    } catch {
      submittingRef.current = false;
      setStatus("error");
    }
  };

  return (
    <div>
      {/* Real top-level form POST - not fetch/XHR. Hidden CSRF field name
          is wired up at submit time from the freshly-fetched token. */}
      <form ref={formRef} method="POST" action="/logout">
        <input ref={csrfInputRef} type="hidden" name="_csrf" defaultValue="" />
      </form>
      <button type="button" onClick={handleLogoutClick} disabled={status === "preparing"}>
        Log out
      </button>
      {status === "error" && (
        <p role="alert" data-testid="logout-error">
          Could not prepare logout. <button type="button" onClick={handleLogoutClick}>Try again</button>
        </p>
      )}
    </div>
  );
}
