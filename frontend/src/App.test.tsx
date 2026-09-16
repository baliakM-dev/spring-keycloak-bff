import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("App", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("renders a Login control that navigates to the BFF OAuth2 authorization endpoint", () => {
    // BackendStatus calls fetch on mount; stub it so the render doesn't
    // trigger an unhandled real network call. Not relevant to this test.
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("not used in this test")));

    render(<App />);

    const login = screen.getByRole("link", { name: "Login" });
    expect(login).toHaveAttribute("href", "/oauth2/authorization/bff-app");
  });
});
