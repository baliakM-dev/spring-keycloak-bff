import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LogoutButton } from "./LogoutButton";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("LogoutButton", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("fetches a CSRF token and submits a real same-origin POST form to /logout with the CSRF field wired up", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn((input: RequestInfo | URL) => {
        const url = typeof input === "string" ? input : input.toString();
        if (url.includes("/api/auth/csrf")) {
          return Promise.resolve(
            jsonResponse({ token: "csrf-token-value", headerName: "X-CSRF-TOKEN", parameterName: "_csrf" }, 200),
          );
        }
        return Promise.reject(new Error(`Unexpected call to ${url}`));
      }),
    );
    const submitSpy = vi.spyOn(HTMLFormElement.prototype, "requestSubmit").mockImplementation(() => {});

    render(<LogoutButton />);
    screen.getByRole("button", { name: "Log out" }).click();

    await waitFor(() => expect(submitSpy).toHaveBeenCalledTimes(1));

    const form = document.querySelector("form")!;
    expect(form).toHaveAttribute("method", "POST");
    expect(form).toHaveAttribute("action", "/logout");
    const csrfInput = form.querySelector("input[type=hidden]") as HTMLInputElement;
    expect(csrfInput.name).toBe("_csrf");
    expect(csrfInput.value).toBe("csrf-token-value");
  });

  it("shows an actionable error and does not submit the form when the CSRF fetch fails", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(jsonResponse({}, 500))));
    const submitSpy = vi.spyOn(HTMLFormElement.prototype, "requestSubmit").mockImplementation(() => {});

    render(<LogoutButton />);
    screen.getByRole("button", { name: "Log out" }).click();

    await waitFor(() => expect(screen.getByTestId("logout-error")).toBeInTheDocument());
    expect(submitSpy).not.toHaveBeenCalled();
  });

  it("allows retrying after a CSRF fetch failure", async () => {
    const fetchMock = vi.fn(() => Promise.resolve(jsonResponse({}, 500)));
    vi.stubGlobal("fetch", fetchMock);
    const submitSpy = vi.spyOn(HTMLFormElement.prototype, "requestSubmit").mockImplementation(() => {});

    render(<LogoutButton />);
    screen.getByRole("button", { name: "Log out" }).click();
    await waitFor(() => expect(screen.getByTestId("logout-error")).toBeInTheDocument());

    fetchMock.mockImplementation(() =>
      Promise.resolve(jsonResponse({ token: "t", headerName: "X-CSRF-TOKEN", parameterName: "_csrf" }, 200)),
    );
    screen.getByRole("button", { name: "Try again" }).click();

    await waitFor(() => expect(submitSpy).toHaveBeenCalledTimes(1));
  });

  it("ignores a rapid duplicate click while a logout is already being prepared", async () => {
    let resolveCsrf: (response: Response) => void = () => {};
    const fetchMock = vi.fn(
      () =>
        new Promise<Response>((resolve) => {
          resolveCsrf = resolve;
        }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const submitSpy = vi.spyOn(HTMLFormElement.prototype, "requestSubmit").mockImplementation(() => {});

    render(<LogoutButton />);
    const button = screen.getByRole("button", { name: "Log out" });
    button.click();
    button.click();
    button.click();

    expect(fetchMock).toHaveBeenCalledTimes(1);

    resolveCsrf(jsonResponse({ token: "t", headerName: "X-CSRF-TOKEN", parameterName: "_csrf" }, 200));
    await waitFor(() => expect(submitSpy).toHaveBeenCalledTimes(1));
  });
});
