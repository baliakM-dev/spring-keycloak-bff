import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "../auth/SessionContext";
import { HomePage } from "./HomePage";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function renderHome() {
  render(
    <MemoryRouter>
      <SessionProvider>
        <HomePage />
      </SessionProvider>
    </MemoryRouter>,
  );
}

describe("HomePage", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows a loading state without flashing protected content while the session check is pending", async () => {
    let resolveMe: (response: Response) => void = () => {};
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = typeof input === "string" ? input : input.toString();
        if (url.includes("/api/auth/me")) {
          return new Promise<Response>((resolve) => {
            resolveMe = resolve;
          });
        }
        return Promise.resolve(jsonResponse({ message: "ok" }, 200));
      }),
    );

    renderHome();

    expect(screen.getByTestId("session-loading")).toBeInTheDocument();
    expect(screen.queryByText(/Signed in as/)).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Login" })).not.toBeInTheDocument();

    resolveMe(jsonResponse({ authenticated: false }, 401));
    await waitFor(() => expect(screen.getByRole("link", { name: "Login" })).toBeInTheDocument());
  });

  it("shows the display name and a link to the protected page when signed in", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = typeof input === "string" ? input : input.toString();
        if (url.includes("/api/auth/me")) {
          return Promise.resolve(
            jsonResponse({ authenticated: true, user: { id: "user-1", displayName: "Jane Doe" } }, 200),
          );
        }
        return Promise.resolve(jsonResponse({ message: "ok" }, 200));
      }),
    );

    renderHome();

    await waitFor(() => expect(screen.getByText("Signed in as Jane Doe")).toBeInTheDocument());
    expect(screen.getByRole("link", { name: /protected page/i })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Login" })).not.toBeInTheDocument();
  });

  it("shows a Login control that performs a full navigation when signed out", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = typeof input === "string" ? input : input.toString();
        if (url.includes("/api/auth/me")) {
          return Promise.resolve(jsonResponse({ authenticated: false }, 401));
        }
        return Promise.resolve(jsonResponse({ message: "ok" }, 200));
      }),
    );

    renderHome();

    const login = await screen.findByRole("link", { name: "Login" });
    expect(login).toHaveAttribute("href", "/oauth2/authorization/bff-app");
  });

  it("shows a retryable error state on network failure, distinct from signed-out", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url.includes("/api/auth/me")) {
        return Promise.reject(new Error("network error"));
      }
      return Promise.resolve(jsonResponse({ message: "ok" }, 200));
    });
    vi.stubGlobal("fetch", fetchMock);

    renderHome();

    await waitFor(() => expect(screen.getByTestId("session-error")).toBeInTheDocument());
    expect(screen.queryByRole("link", { name: "Login" })).not.toBeInTheDocument();

    // Retry re-checks the session; simulate recovery.
    fetchMock.mockImplementation((input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url.includes("/api/auth/me")) {
        return Promise.resolve(jsonResponse({ authenticated: false }, 401));
      }
      return Promise.resolve(jsonResponse({ message: "ok" }, 200));
    });
    screen.getByRole("button", { name: "Retry" }).click();

    await waitFor(() => expect(screen.getByRole("link", { name: "Login" })).toBeInTheDocument());
  });
});
