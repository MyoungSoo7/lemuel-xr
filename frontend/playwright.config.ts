import { defineConfig, devices } from "@playwright/test";

/**
 * Playwright config — lemuel-xr frontend E2E.
 *
 * 두 가지 시나리오 모드:
 * 1. 로컬 dev — Next.js dev 서버를 webServer 가 띄움 (npm run dev).
 *    backend 는 별도 docker-compose up 으로 띄워둬야 한다 (NEXT_PUBLIC_API_BASE
 *    가 가리키는 곳, 보통 http://localhost:8081).
 *
 * 2. CI / preview — BASE_URL 환경변수로 임의 origin 지정 가능.
 *    예: BASE_URL=https://chat.lemuel.co.kr npx playwright test
 *
 * Test 디렉토리: ./tests/e2e
 */
export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: process.env.BASE_URL ?? "http://localhost:3000",
    trace: "on-first-retry",
    screenshot: "only-on-failure",
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
  webServer: process.env.BASE_URL
    ? undefined
    : {
        command: "npm run dev",
        url: "http://localhost:3000",
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
});
