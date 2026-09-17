import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "../auth/SessionContext";
import { ProtectedPage } from "./ProtectedPage";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderProtected() {
  render(
    <MemoryRouter initialEntries={["/protected"]}>
      <SessionProvider>
        <ProtectedPage />
      </SessionProvider>
    </MemoryRouter>,
  );
}

describe("ProtectedPage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows a login gate (UX only) for an anonymous session, without calling the protected endpoint", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url.includes("/api/auth/me")) {
        return Promise.resolve(jsonResponse({ authenticated: false }, 401));
      }
      return Promise.reject(new Error(`Unexpected call to ${url}`));
    });
    vi.stubGlobal("fetch", fetchMock);

    renderProtected();

    const gate = await screen.findByTestId("protected-login-gate");
    expect(gate).toBeInTheDocument();
    const login = screen.getByRole("link", { name: "Login" });
    expect(login).toHaveAttribute("href", "/oauth2/authorization/bff-app");

    const calledUrls = fetchMock.mock.calls.map(([input]) =>
      typeof input === "string" ? input : (input as URL).toString(),
    );
    expect(calledUrls.some((url) => url.includes("/api/protected/hello"))).toBe(false);
  });

  it("displays the protected endpoint's response for an authenticated session", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = typeof input === "string" ? input : input.toString();
        if (url.includes("/api/auth/me")) {
          return Promise.resolve(
            jsonResponse({ authenticated: true, user: { id: "user-1", displayName: "Jane Doe" } }, 200),
          );
        }
        if (url.includes("/api/protected/hello")) {
          return Promise.resolve(jsonResponse({ message: "authenticated" }, 200));
        }
        return Promise.reject(new Error(`Unexpected call to ${url}`));
      }),
    );

    renderProtected();

    await waitFor(() => expect(screen.getByTestId("protected-message")).toHaveTextContent("authenticated"));
  });

  it("clears stale authenticated state and shows the login gate on a 401 from the protected endpoint", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = typeof input === "string" ? input : input.toString();
        if (url.includes("/api/auth/me")) {
          // Session looked valid at bootstrap...
          return Promise.resolve(
            jsonResponse({ authenticated: true, user: { id: "user-1", displayName: "Jane Doe" } }, 200),
          );
        }
        if (url.includes("/api/protected/hello")) {
          // ...but has since expired by the time the protected call is made.
          return Promise.resolve(jsonResponse({}, 401));
        }
        return Promise.reject(new Error(`Unexpected call to ${url}`));
      }),
    );

    renderProtected();

    await waitFor(() => expect(screen.getByTestId("protected-login-gate")).toBeInTheDocument());
  });
});
