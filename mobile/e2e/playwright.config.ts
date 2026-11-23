import path from 'node:path';
import { defineConfig } from '@playwright/test';

// Run from frontend/ so @playwright/test 1.61.0 and its cached chromium are reused (see mobile/README.md):
//   cd frontend && NODE_PATH=$PWD/node_modules npx playwright test -c ../mobile/e2e/playwright.config.ts
// The fixture (customer, approved hotel org with stock) comes from the web e2e global setup/teardown.
const fe = path.resolve(__dirname, '../../frontend/e2e');
export default defineConfig({
  testDir: __dirname,
  testMatch: /.*\.spec\.ts/,
  workers: 1,
  timeout: 180_000,
  expect: { timeout: 20_000 },
  reporter: [['list']],
  outputDir: path.resolve(__dirname, '../e2e-results/artifacts'),
  globalSetup: path.join(fe, 'global-setup.ts'),
  globalTeardown: path.join(fe, 'global-teardown.ts'),
  use: {
    baseURL: process.env.MOBILE_WEB_URL || 'http://localhost:8081',
    headless: true,
    viewport: { width: 412, height: 915 },
    screenshot: 'only-on-failure',
  },
});
