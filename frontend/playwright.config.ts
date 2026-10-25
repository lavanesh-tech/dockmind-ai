import { defineConfig } from '@playwright/test';

/**
 * End-to-end tests against the whole running system (see scripts/run-local.sh):
 * Keycloak on :8180, the services behind the gateway on :8080, this app on :4200.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  retries: 0,
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
  },
  reporter: [['list']],
});
