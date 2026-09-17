import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchCsrfToken, unsafeFetch } from "./csrf";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("fetchCsrfToken", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns the token shape from GET /api/auth/csrf as a same-origin request", async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(jsonResponse({ token: "abc", headerName: "X-CSRF-TOKEN", parameterName: "_csrf" }, 200)),
    );
    vi.stubGlobal("fetch", fetchMock);

    const token = await fetchCsrfToken();

    expect(token).toEqual({ token: "abc", headerName: "X-CSRF-TOKEN", parameterName: "_csrf" });
    expect(fetchMock).toHaveBeenCalledWith("/api/auth/csrf", { credentials: "same-origin" });
  });

  it("throws when the CSRF endpoint does not respond with 2xx", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(jsonResponse({}, 500))));

    await expect(fetchCsrfToken()).rejects.toThrow();
  });
});

/**
 * Exercises the shared unsafe-request helper directly, against a test-only
 * mocked endpoint - no business mutation endpoint is added to the app
 * solely to demonstrate this.
 */
describe("unsafeFetch", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fetches a fresh CSRF token first and attaches it on the expected header before issuing the request", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
      void init;
      const url = typeof input === "string" ? input : input.toString();
      if (url.includes("/api/auth/csrf")) {
        return Promise.resolve(
          jsonResponse({ token: "fresh-token", headerName: "X-CSRF-TOKEN", parameterName: "_csrf" }, 200),
        );
      }
      return Promise.resolve(jsonResponse({ ok: true }, 200));
    });
    vi.stubGlobal("fetch", fetchMock);

    const response = await unsafeFetch("/api/test-only/unsafe-endpoint", { method: "POST" });

    expect(response.status).toBe(200);
    const unsafeCall = fetchMock.mock.calls.find(([input]) =>
      (typeof input === "string" ? input : (input as URL).toString()).includes("/api/test-only/unsafe-endpoint"),
    );
    expect(unsafeCall).toBeDefined();
    const [, init] = unsafeCall!;
    const headers = new Headers(init?.headers);
    expect(headers.get("X-CSRF-TOKEN")).toBe("fresh-token");
    expect(init?.credentials).toBe("same-origin");
    expect(init?.method).toBe("POST");
  });

  it("propagates a CSRF-fetch failure without issuing the unsafe request", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url.includes("/api/auth/csrf")) {
        return Promise.resolve(jsonResponse({}, 500));
      }
      return Promise.reject(new Error(`Unexpected call to ${url}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    await expect(unsafeFetch("/api/test-only/unsafe-endpoint", { method: "POST" })).rejects.toThrow();
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
