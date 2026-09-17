/**
 * Shape of {@code GET /api/auth/csrf}'s response body, sourced directly from
 * Spring Security's resolved {@code CsrfToken} - header/parameter names are
 * never hardcoded on the frontend either.
 */
export interface CsrfToken {
  token: string;
  headerName: string;
  parameterName: string;
}

/**
 * Fetches a fresh CSRF token from the BFF. Always a same-origin, safe GET -
 * mutation-free. May establish an anonymous session as a side effect (per
 * the backend contract); this is not an authenticated session and must not
 * be treated as one.
 */
export async function fetchCsrfToken(): Promise<CsrfToken> {
  const response = await fetch("/api/auth/csrf", { credentials: "same-origin" });
  if (!response.ok) {
    throw new Error(`Failed to fetch CSRF token: ${response.status}`);
  }
  return (await response.json()) as CsrfToken;
}

/**
 * Shared helper for same-origin, session-authenticated unsafe (state
 * changing) requests: always fetches a fresh CSRF token first and attaches
 * it on the header Spring Security expects, before issuing the actual
 * request. Never use this for safe GET requests - those must stay
 * mutation-free and do not need a CSRF token.
 *
 * Does not retry on a 403 - a 403 is not necessarily a CSRF failure, and
 * blindly retrying an unsafe request risks a duplicate side effect.
 */
export async function unsafeFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const csrf = await fetchCsrfToken();
  const headers = new Headers(init.headers);
  headers.set(csrf.headerName, csrf.token);
  return fetch(input, {
    ...init,
    credentials: "same-origin",
    headers,
  });
}
