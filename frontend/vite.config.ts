/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Backend BFF origin used only for the local dev proxy. The browser never
// sees this value directly - it always talks to the Vite dev origin, and
// Vite forwards matching paths server-side. This keeps the frontend free
// of any hardcoded backend URL in application code.
const backendTarget = process.env.BACKEND_URL ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: backendTarget,
        changeOrigin: true,
      },
      // Stage 2A: OAuth2/OIDC login endpoints, proxied to the backend BFF.
      // changeOrigin is intentionally NOT set here (unlike /api above):
      // rewriting the outbound Host header would make Spring's
      // {baseUrl}-relative computations diverge from nginx's Host-preserving
      // proxy_pass behavior used in Docker/production (frontend/nginx.conf).
      "/oauth2": {
        target: backendTarget,
      },
      "/login": {
        target: backendTarget,
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/setupTests.ts"],
  },
});
