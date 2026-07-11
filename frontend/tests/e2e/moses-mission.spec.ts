import { test, expect, Page } from "@playwright/test";

/**
 * Moses 6-Scene E2E — Phase 2 완전 활성 full playthrough (요셉 동급).
 *
 * 흐름:
 *   홈 → "Moses" 카드 → /moses → Scene 1 cinematic → 계속 →
 *   Scene 2 gesture_sequence(다가가기·신 벗기·고개 들기) →
 *   Scene 3 distribute(다섯 변명 카드 → 내려놓기/품기 배정 → 결정) →
 *   Scene 4 pick_one(파라오 앞 지팡이) →
 *   Scene 5 gesture_sequence(홍해, 단일 행동) →
 *   Scene 6 outro → 미션 완료 → 홈 redirect.
 *
 * 헤더 포맷: "Moses — Scene {n}/6 · Mode: VR" (src/app/moses/page.tsx).
 * /api/game/* disclaimer 게이트는 client.ts 가 게스트 토큰 발급 직후 자동 동의로 통과.
 */

const SCENE_HEADER = /Moses — Scene/i;

async function waitForScene(page: Page, sceneNo: 1 | 2 | 3 | 4 | 5 | 6) {
  await expect(page.locator("header")).toContainText(
    new RegExp(`Scene ${sceneNo}/6`, "i"),
    { timeout: 20_000 },
  );
}

test.describe("Moses 6-Scene mission", () => {
  test("모세 6씬 완주 흐름 (mixed 결말)", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Lemuel/i);

    // 홈 — Moses 카드
    await page.getByRole("link", { name: /Moses/ }).first().click();
    await page.waitForURL(/\/moses$/);

    // Scene 1 cinematic → 계속
    await waitForScene(page, 1);
    await expect(page.locator("header")).toContainText(SCENE_HEADER);
    await page.getByRole("button", { name: /계속/ }).click();

    // Scene 2 gesture_sequence — 세 몸짓을 순서대로 누른다
    await waitForScene(page, 2);
    await clickAllGestureSteps(page);

    // Scene 3 distribute — 다섯 변명 카드를 섞어서 배정(mixed) 후 결정
    await waitForScene(page, 3);
    await assignExcuseCardsMixed(page);
    await page.getByRole("button", { name: /결정/ }).click();

    // Scene 4 pick_one — 첫 옵션 선택
    await waitForScene(page, 4);
    await pickFirstSceneButton(page);

    // Scene 5 gesture_sequence — 단일 행동
    await waitForScene(page, 5);
    await clickAllGestureSteps(page);

    // Scene 6 outro — 결말 텍스트 + 미션 완료
    await waitForScene(page, 6);
    const outro = await page.locator("main").innerText();
    expect(outro.length).toBeGreaterThan(20);

    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 헤더가 진행마다 갱신된다 (직접 /moses 진입)", async ({ page }) => {
    await page.goto("/moses");
    await waitForScene(page, 1);

    await page.getByRole("button", { name: /계속/ }).click();
    await waitForScene(page, 2);

    await clickAllGestureSteps(page);
    await waitForScene(page, 3);
  });
});

/**
 * gesture_sequence — main 안의 순서 몸짓 버튼(1./2./3.)을 모두 순서대로 누른다.
 * 마지막 버튼을 누르면 다음 Scene 으로 advance.
 */
async function clickAllGestureSteps(page: Page) {
  // 버튼 라벨이 "1. 다가가기" 처럼 번호로 시작 — 홈 네비/결정 버튼과 구분.
  const stepButtons = page
    .locator("main section")
    .getByRole("button")
    .filter({ hasText: /^\d+\./ });
  const count = await stepButtons.count();
  expect(count).toBeGreaterThan(0);
  for (let i = 0; i < count; i++) {
    // 클릭할 때마다 done 표시(✓)가 붙어 disabled 되므로 매번 첫 활성 버튼을 다시 잡는다.
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
 * Scene 3 다섯 변명 카드 — 홀수 카드는 "내려놓기", 짝수 카드는 "가슴에 품기" 로
 * 배정해 mixed 패턴을 만든다.
 */
async function assignExcuseCardsMixed(page: Page) {
  const throwBtns = page.getByRole("button", { name: /내려놓기/ });
  const heartBtns = page.getByRole("button", { name: /가슴에 품기/ });
  const total = await throwBtns.count();
  expect(total).toBeGreaterThan(1);
  for (let i = 0; i < total; i++) {
    if (i % 2 === 0) {
      await throwBtns.nth(i).click();
    } else {
      await heartBtns.nth(i).click();
    }
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
    .filter({ hasNotText: /계속|미션 완료|오류|결정|내려놓기|가슴에 품기/ })
    .first();
  await expect(btn).toBeVisible({ timeout: 20_000 });
  await btn.click();
}
