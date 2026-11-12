import { defineConfig } from '@playwright/test'

/**
 * Browser E2E for the M1.5 UI. The backend must already be running with a *fresh* data root
 * (the flow performs first-run setup), e.g.:
 *
 *   java -jar services/domain/target/mammoth-domain-service.jar --server.port=18130 \
 *     --mammoth.data-root=%TEMP%\mammoth-e2e
 *   cd apps/web && npm run test:e2e
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.MAMMOTH_E2E_BASE ?? 'http://127.0.0.1:18130',
    trace: 'retain-on-failure',
    locale: 'zh-CN',
  },
})
