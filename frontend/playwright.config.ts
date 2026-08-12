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
  /**
   * 2026-08-12 — 모바일 프로젝트 추가.
   *
   * 그전까지 이 배열은 Desktop Chrome 하나였고, `frontend/README.md` 는 「모바일 성능
   * 미검증 — 데스크탑 Chrome 우선 검증, 모바일은 별도 phase」라고 스스로 적어 두고
   * 있었다. 즉 모바일이 깨져 있었던 게 아니라 **아무도 재지 않았다.** 재기 시작하니
   * 첫 실행에서 위기 푸터가 본문을 덮는 것이 나왔다(펼침 205px vs 여백 48px).
   *
   * `iPhone SE` 를 넣은 이유는 이 리포에서 가장 좁은 실기기 폭(320px)이기 때문이다 —
   * 줄바꿈으로 높이가 늘어나는 경계가 거기서 처음 걸린다. 넓은 기기만 재면 통과한다.
   *
   * ⚠️ 이 프로젝트들은 Playwright 의 기기 에뮬레이션이다. 뷰포트·DPR·터치·UA 는
   *    실기기를 따르지만 **iOS 의 실제 안전영역 값은 재현되지 않는다**(전부 0 이다).
   *    safe-area 처리는 여기서 초록이어도 검증된 것이 아니다 — 실기기 몫이다.
   *
   * ⚠️ **엔진은 넷 다 Chromium 이다.** `devices["iPhone …"]` 은 기본 엔진으로 WebKit 을
   *    끌고 오는데, 이 리포에서 그 조합이 돌지 않는다 — Playwright 1.60 이 받는 WebKit
   *    빌드는 `webkit-mac-15.zip` 하나뿐이고(mac-14·mac-26 은 CDN 에 없다) 이 개발기는
   *    macOS 26.6 이다. 프로세스는 뜨지만 `browserContext.newPage()` 가 영영 안 돌아온다
   *    (앱을 열기도 전이다. 2026-08-12 직접 확인). 그래서 여기서는 엔진을 Chromium 으로
   *    못 박는다 — 못 도는 프로젝트를 남겨 두면 **빨간 줄이 상수가 되고 곧 아무도 안 본다.**
   *
   *    대신 `iPhone` 이라는 이름이 뜻하는 범위를 좁혀 둔다: **뷰포트·DPR·터치·모바일 UA**
   *    지 **Safari 엔진이 아니다.** 이 축에서 잡히는 것은 레이아웃 결함(덮임·넘침)이고,
   *    실제로 이 파일을 만들게 한 위기 푸터 덮임도 그 부류였다. WebKit 고유 결함
   *    (`svh` 계산, `env()` 해석, `-webkit-` 접두어 동작)은 **여기서 안 잡힌다.**
   *    `PW_WEBKIT=1` 을 주면 아래 Safari 프로젝트가 붙는다 — WebKit 이 도는 환경
   *    (CI 의 ubuntu, 또는 macOS 15 이하)에서 쓰라고 남겨 둔 문이다.
   */
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    {
      name: "mobile-iphone-se",
      use: { ...devices["iPhone SE"], browserName: "chromium" },
    },
    {
      name: "mobile-iphone-15",
      use: { ...devices["iPhone 15"], browserName: "chromium" },
    },
    { name: "mobile-pixel-7", use: { ...devices["Pixel 7"] } },
    ...(process.env.PW_WEBKIT
      ? [{ name: "safari-iphone-15", use: { ...devices["iPhone 15"] } }]
      : []),
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
