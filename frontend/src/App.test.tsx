import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function stubAnonymousSession() {
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
}

describe("App", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.history.pushState({}, "", "/");
  });

  it("renders the home route at / with a Login control that navigates to the BFF OAuth2 authorization endpoint", async () => {
    stubAnonymousSession();

    render(<App />);

    const login = await screen.findByRole("link", { name: "Login" });
    expect(login).toHaveAttribute("href", "/oauth2/authorization/bff-app");
  });

  it("routes direct navigation to /protected to the protected page's (UX-only) login gate", async () => {
    window.history.pushState({}, "", "/protected");
    stubAnonymousSession();

    render(<App />);

    await waitFor(() => expect(screen.getByTestId("protected-login-gate")).toBeInTheDocument());
  });
});
