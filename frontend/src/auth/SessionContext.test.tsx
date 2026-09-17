import { act, render, screen, waitFor } from "@testing-library/react";
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

  it("re-checks the session on a bfcache restoration (pageshow with persisted=true), not just focus/visibilitychange", async () => {
    let callCount = 0;
    vi.stubGlobal(
      "fetch",
      vi.fn(() => {
        callCount += 1;
        // First call (mount bootstrap): authenticated - simulates a page
        // that was rendered and then frozen into bfcache while signed in.
        // Second call (pageshow/persisted re-check): anonymous - simulates
        // the session having actually ended (e.g. a real logout navigation)
        // while this frozen page sat in bfcache.
        const body =
          callCount === 1
            ? { authenticated: true, user: { id: "user-1", displayName: "Jane Doe" } }
            : { authenticated: false };
        return Promise.resolve(
          new Response(JSON.stringify(body), { status: callCount === 1 ? 200 : 401 }),
        );
      }),
    );

    render(
      <SessionProvider>
        <Probe onReady={() => {}} />
      </SessionProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("session-status")).toHaveTextContent("authenticated"));

    // Simulate the browser restoring this page from bfcache. jsdom has no
    // native PageTransitionEvent, so a plain Event is dispatched with
    // `persisted` attached directly - the handler only reads that property.
    const pageshowEvent = new Event("pageshow") as PageTransitionEvent;
    Object.defineProperty(pageshowEvent, "persisted", { value: true });
    // Synchronous act: flushes only the synchronous setSession({status:
    // "loading"}) load(true) performs before it starts the fetch - not the
    // fetch's own (already-resolved-mock) promise, so the transient
    // "loading" state below is actually observable.
    act(() => {
      window.dispatchEvent(pageshowEvent);
    });

    // load(true) is used for this path, so the stale authenticated content
    // must not remain on screen while the re-check is in flight.
    expect(screen.getByTestId("session-status")).toHaveTextContent("loading");

    await waitFor(() => expect(screen.getByTestId("session-status")).toHaveTextContent("anonymous"));
    expect(callCount).toBe(2);
  });

  it("does not re-check on an ordinary pageshow (persisted=false) - only on bfcache restoration", async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(
        new Response(JSON.stringify({ authenticated: true, user: { id: "user-1", displayName: "Jane Doe" } }), {
          status: 200,
        }),
      ),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(
      <SessionProvider>
        <Probe onReady={() => {}} />
      </SessionProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("session-status")).toHaveTextContent("authenticated"));
    expect(fetchMock).toHaveBeenCalledTimes(1);

    const pageshowEvent = new Event("pageshow") as PageTransitionEvent;
    Object.defineProperty(pageshowEvent, "persisted", { value: false });
    await act(async () => {
      window.dispatchEvent(pageshowEvent);
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("session-status")).toHaveTextContent("authenticated");
  });
});
