import { defineConfig, devices } from '@playwright/test';

/**
 * E2E against the real local stack: `docker compose --profile apps --profile mail up -d --wait`
 * (Keycloak, API, Mailpit) plus `ng serve`, which Playwright starts unless one is running.
 * Every test is video-recorded; E2E_SLOWMO=150 makes the sprint recording watchable.
 */
export default defineConfig({
  testDir: 'e2e',
  outputDir: 'test-results',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env['CI'],
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4200',
    locale: 'fr-FR',
    viewport: { width: 1280, height: 800 },
    video: 'on',
    trace: 'retain-on-failure',
    launchOptions: { slowMo: Number(process.env['E2E_SLOWMO'] ?? 0) },
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm start',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env['CI'],
    timeout: 180_000,
  },
});
