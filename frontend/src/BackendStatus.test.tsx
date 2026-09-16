import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { BackendStatus } from "./BackendStatus";

describe("BackendStatus", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("shows Connected when the public backend endpoint responds", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: "spring-keycloak-bff" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    render(<BackendStatus />);

    await waitFor(() => expect(screen.getByTestId("backend-status")).toHaveTextContent("Connected"));
  });

  it("shows Error when the public backend endpoint is unreachable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("network error")));

    render(<BackendStatus />);

    await waitFor(() => expect(screen.getByTestId("backend-status")).toHaveTextContent("Error"));
  });
});
