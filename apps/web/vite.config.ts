import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// The M1 UI is a standalone SPA served by the Domain Service in production (ADR-0002),
// so the dev server proxies API calls to the backend instead of baking URLs into the bundle.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
