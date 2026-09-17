import { act, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider, useSession } from "./SessionContext";

function Probe({ onReady }: { onReady: (reportUnauthorized: () => void) => void }) {
  const { session, reportUnauthorized } = useSession();
  onReady(reportUnauthorized);
  return <div data-testid="session-status">{session.status}</div>;
}

describe("SessionProvider", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("does not let a slow, stale /api/auth/me 200 response re-authenticate after a more recent sign-out", async () => {
    // The HTTP response arrives (status 200) immediately, but parsing its
    // body is what's slow and independently controllable - this is the
    // actual race: the generation check at the top of the `.then` callback
    // passes (nothing has signed out yet), then `await response.json()`
    // yields to the microtask queue, and only *then* does a sign-out happen
    // elsewhere before the body resolves.
    let resolveJson: (body: unknown) => void = () => {};
    const pendingResponse = {
      status: 200,
      json: () => new Promise((resolve) => {
        resolveJson = resolve;
      }),
    } as unknown as Response;
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve(pendingResponse)));

    let reportUnauthorized: () => void = () => {};
    render(
      <SessionProvider>
        <Probe
          onReady={(fn) => {
            reportUnauthorized = fn;
          }}
        />
      </SessionProvider>,
    );

    // Let the fetch promise and the `.then` callback's generation check run,
    // parking at `await response.json()`.
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    // Something else (e.g. a 401 from a protected API call, or a focus
    // re-check) reports sign-out while the body of the original bootstrap
    // response is still being parsed.
    act(() => {
      reportUnauthorized();
    });
    expect(screen.getByTestId("session-status")).toHaveTextContent("anonymous");

    // The stale response body now resolves, claiming authenticated. It must
    // be discarded - it belongs to an earlier generation than the sign-out
    // that already happened.
    await act(async () => {
      resolveJson({ authenticated: true, user: { id: "user-1", displayName: "Jane Doe" } });
    });

    expect(screen.getByTestId("session-status")).toHaveTextContent("anonymous");
  });
});
