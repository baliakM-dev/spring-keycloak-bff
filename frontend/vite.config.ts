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
      // Stage 1: only the public API is proxied.
      // Future stages will add "/oauth2", "/login" and "/logout" entries
      // here (same shape) once server-side OAuth2 login is implemented -
      // no rework of this proxy structure will be needed for that.
      "/api": {
        target: backendTarget,
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/setupTests.ts"],
  },
});
