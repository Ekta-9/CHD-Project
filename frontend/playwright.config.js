// @ts-check
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
  testDir: './tests/e2e',
  timeout: 30_000,
  retries: 0,
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npx http-server -p 4173 -c-1 .',
    url: 'http://localhost:4173/login.html',
    reuseExistingServer: true,
    timeout: 30_000,
  },
});
