import { useEffect, useState } from "react";

type Status = "checking" | "connected" | "error";

interface HelloResponse {
  message: string;
}

/**
 * Calls the public backend health endpoint on mount and reports whether the
 * BFF is reachable.
 *
 * Stage 1 only: this calls an unauthenticated public endpoint via a
 * relative path (proxied by Vite in dev). It does not read, store, or
 * handle any authentication token - there is none yet.
 */
export function BackendStatus() {
  const [status, setStatus] = useState<Status>("checking");

  useEffect(() => {
    const controller = new AbortController();

    fetch("/api/public/hello", { signal: controller.signal })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`Unexpected response status: ${response.status}`);
        }
        return response.json() as Promise<HelloResponse>;
      })
      .then(() => setStatus("connected"))
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") {
          return;
        }
        setStatus("error");
      });

    return () => controller.abort();
  }, []);

  const label = status === "connected" ? "Connected" : status === "error" ? "Error" : "Checking...";

  return (
    <p>
      Backend status: <span data-testid="backend-status">{label}</span>
    </p>
  );
}
