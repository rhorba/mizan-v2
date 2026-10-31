import { defineConfig, devices } from '@playwright/test';

/**
 * Story 7.4: the happy path against the public preprod URL, recorded. Run by hand, never in CI:
 * it spends one real model call. Needs E2E_PUBLIC_EMAIL and E2E_PUBLIC_PASSWORD.
 */
export default defineConfig({
  testDir: 'e2e-public',
  outputDir: 'test-results-public',
  timeout: 300_000,
  expect: { timeout: 20_000 },
  workers: 1,
  retries: 0, // a retry would be a second billed call
  reporter: 'list',
  use: {
    baseURL: process.env['E2E_PUBLIC_URL'] ?? 'https://144.24.200.83.sslip.io',
    locale: 'fr-FR',
    viewport: { width: 1280, height: 800 },
    video: 'on',
    trace: 'retain-on-failure',
    launchOptions: { slowMo: Number(process.env['E2E_SLOWMO'] ?? 0) },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
