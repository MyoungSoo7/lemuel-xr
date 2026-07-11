import { test, expect, Page } from "@playwright/test";

/**
 * David 6-Scene E2E — Phase 2 완전 활성 full playthrough (요셉·모세 동급).
 *
 * 흐름:
 *   홈 → "David" 카드 → /david → Scene 1 contemplative → 계속 →
 *   Scene 2 pick_one(형의 비웃음: 반박/침묵/전선) →
 *   Scene 3 gesture_sequence(사울 갑옷: 입기·걷기·벗기) →
 *   Scene 4 distribute(시냇가 5돌 = 5감정, 다섯을 순서대로 집기) →
 *   Scene 5 two_handed_throw(골리앗, 물맷돌 던지기) →
 *   Scene 6 outro → 미션 완료 → 홈 redirect.
 *
 * 헤더 포맷: "David — Scene {n}/6 · Mode: VR" (src/app/david/page.tsx).
 * /api/game/* disclaimer 게이트는 client.ts 가 게스트 토큰 발급 직후 자동 동의로 통과.
 */

const SCENE_HEADER = /David — Scene/i;

async function waitForScene(page: Page, sceneNo: 1 | 2 | 3 | 4 | 5 | 6) {
  await expect(page.locator("header")).toContainText(
    new RegExp(`Scene ${sceneNo}/6`, "i"),
    { timeout: 20_000 },
  );
}

test.describe("David 6-Scene mission", () => {
  test("다윗 6씬 완주 흐름 (마지막 돌 = 신뢰 결말)", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Lemuel/i);

    // 홈 — David 카드
    await page.getByRole("link", { name: /David/ }).first().click();
    await page.waitForURL(/\/david$/);

    // Scene 1 contemplative → 계속
    await waitForScene(page, 1);
    await expect(page.locator("header")).toContainText(SCENE_HEADER);
    await page.getByRole("button", { name: /계속/ }).click();

    // Scene 2 pick_one — 첫 옵션(반박) 선택
    await waitForScene(page, 2);
    await pickFirstSceneButton(page);

    // Scene 3 gesture_sequence — 갑옷 몸짓 순서대로
    await waitForScene(page, 3);
    await clickAllGestureSteps(page);

    // Scene 4 distribute — 다섯 돌을 순서대로 집고 결정
    await waitForScene(page, 4);
    await pickAllStones(page);
    await page.getByRole("button", { name: /들고 나아간다/ }).click();

    // Scene 5 two_handed_throw — 정서 부담 경고(R4) 동의 후 물맷돌 던지기
    await waitForScene(page, 5);
    await page.getByRole("button", { name: /준비됐어요/ }).click();
    await page.getByRole("button", { name: /물맷돌을 던진다/ }).click();

    // Scene 6 outro — 결말 텍스트 + 미션 완료
    await waitForScene(page, 6);
    const outro = await page.locator("main").innerText();
    expect(outro.length).toBeGreaterThan(20);

    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 5 폭력 경고에서 '건너뛸게요' 로도 Scene 6 결말에 도달한다 (R4)", async ({
    page,
  }) => {
    await page.goto("/david");

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /계속/ }).click();

    await waitForScene(page, 2);
    await pickFirstSceneButton(page);

    await waitForScene(page, 3);
    await clickAllGestureSteps(page);

    await waitForScene(page, 4);
    await pickAllStones(page);
    await page.getByRole("button", { name: /들고 나아간다/ }).click();

    // Scene 5 — 경고 카드에서 건너뛰기 → Scene 6 로 직행
    await waitForScene(page, 5);
    await expect(
      page.getByText(/건너뛰어도 괜찮습니다|건너뛰어도 결말/),
    ).toBeVisible({ timeout: 20_000 });
    await page.getByRole("button", { name: /건너뛸게요/ }).click();

    await waitForScene(page, 6);
    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 헤더가 진행마다 갱신된다 (직접 /david 진입)", async ({ page }) => {
    await page.goto("/david");
    await waitForScene(page, 1);

    await page.getByRole("button", { name: /계속/ }).click();
    await waitForScene(page, 2);

    await pickFirstSceneButton(page);
    await waitForScene(page, 3);
  });
});

/**
 * gesture_sequence — main 안의 순서 몸짓 버튼(1./2./3.)을 모두 순서대로 누른다.
 * 마지막 버튼을 누르면 다음 Scene 으로 advance.
 */
async function clickAllGestureSteps(page: Page) {
  const stepButtons = page
    .locator("main section")
    .getByRole("button")
    .filter({ hasText: /^\d+\./ });
  const count = await stepButtons.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    const btn = page
      .locator("main section")
      .getByRole("button")
      .filter({ hasText: /^\d+\./ })
      .filter({ hasNotText: /✓/ })
      .first();
    if ((await btn.count()) === 0) break;
    await btn.click();
  }
}

/**
 * distribute (5돌) — "N번째 ✓" 가 아직 안 붙은 돌 버튼을 하나씩 눌러 다섯을 모두 집는다.
 */
async function pickAllStones(page: Page) {
  const stoneScope = () =>
    page
      .locator("main section")
      .getByRole("button")
      .filter({ hasNotText: /계속|미션 완료|오류|듣기|정지|불러오는 중|음성|나아간다|모두 집으세요|Joseph|홈/ })
      .filter({ hasNotText: /번째 ✓/ });
  for (let i = 0; i < 5; i++) {
    const btn = stoneScope().first();
    if ((await btn.count()) === 0) break;
    await btn.click();
  }
}

/**
 * 현재 Scene 의 첫 번째 "선택" 버튼(pick_one). cinematic/outro 의 계속·완료,
 * 홈 네비 링크, gesture 번호 버튼을 피한다.
 */
async function pickFirstSceneButton(page: Page) {
  const btn = page
    .locator("main section")
    .getByRole("button")
    .filter({ hasNotText: /계속|미션 완료|오류|듣기|정지|불러오는 중|음성|나아간다|던진다/ })
    .first();
  await expect(btn).toBeVisible({ timeout: 20_000 });
  await btn.click();
}
