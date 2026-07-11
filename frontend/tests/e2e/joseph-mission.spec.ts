import { test, expect, Page } from "@playwright/test";

/**
 * Joseph 5-Scene E2E — Phase 2 UI 에 맞춘 full playthrough.
 *
 * 흐름:
 *   홈 → "Joseph" 카드 클릭 → /joseph → Scene 1 cinematic → 계속 →
 *   Scene 2 pick_one → Scene 3 distribute (immigrant_first) → Scene 4 pick_one →
 *   Scene 5 outro → 미션 완료 → 홈 redirect.
 *
 * 백엔드 게이트:
 *   /api/game/* 는 disclaimer 동의(451 게이트) 를 요구한다. 프론트 client.ts 가
 *   게스트 토큰 발급 직후 /api/auth/accept-disclaimer 를 자동 호출하므로 e2e 는
 *   별도 동의 스텝 없이 통과한다.
 *
 * 헤더 포맷: "Joseph — Scene {n}/5 · Mode: VR" (src/app/joseph/page.tsx).
 */

const SCENE_HEADER = /Joseph — Scene/i;

async function waitForScene(page: Page, sceneNo: 1 | 2 | 3 | 4 | 5) {
  await expect(page.locator("header")).toContainText(
    new RegExp(`Scene ${sceneNo}/5`, "i"),
    { timeout: 20_000 },
  );
}

test.describe("Joseph 5-Scene mission", () => {
  test("immigrant_first 결말 흐름이 완주된다", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Lemuel/i);

    // 홈 — Joseph 카드 (링크 텍스트에 "Joseph" 포함)
    await page.getByRole("link", { name: /Joseph/ }).first().click();
    await page.waitForURL(/\/joseph$/);

    // Scene 1 cinematic → 계속
    await waitForScene(page, 1);
    await expect(page.locator("header")).toContainText(SCENE_HEADER);
    await page.getByRole("button", { name: /계속/ }).click();

    // Scene 2 pick_one — 첫 옵션 선택 (payload.options 첫 버튼)
    await waitForScene(page, 2);
    await pickFirstSceneButton(page);

    // Scene 3 distribute — immigrant_first (이주민 / 외국인 줄 우선)
    await waitForScene(page, 3);
    const immigrantBtn = page
      .getByRole("button", { name: /이주민|외국인|immigrant/i })
      .first();
    if (await immigrantBtn.count()) {
      await immigrantBtn.click();
    } else {
      await pickFirstSceneButton(page);
    }

    // Scene 4 pick_one — 첫 옵션
    await waitForScene(page, 4);
    await pickFirstSceneButton(page);

    // Scene 5 outro — 결말 텍스트 확인 + 미션 완료 버튼
    await waitForScene(page, 5);
    const outroText = await page.locator("main").innerText();
    expect(outroText.length).toBeGreaterThan(20);

    await page.getByRole("button", { name: /미션 완료/ }).click();

    // 미션 완료 후 홈으로 redirect (location.href = "/")
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 헤더가 진행마다 갱신된다 (직접 /joseph 진입)", async ({ page }) => {
    await page.goto("/joseph");
    await waitForScene(page, 1);

    await page.getByRole("button", { name: /계속/ }).click();
    await waitForScene(page, 2);

    // Scene 2 결정 직후 — Scene 3 진입.
    await pickFirstSceneButton(page);
    await waitForScene(page, 3);
  });
});

/**
 * 현재 Scene 의 첫 번째 "선택" 버튼을 누른다. cinematic/outro 의 계속·완료 버튼과
 * 홈 네비게이션 링크를 피하기 위해 main 내부의 선택지 버튼만 대상으로 한다.
 */
async function pickFirstSceneButton(page: Page) {
  const btn = page
    .locator("main section")
    .getByRole("button")
    .filter({ hasNotText: /계속|미션 완료|오류/ })
    .first();
  await expect(btn).toBeVisible({ timeout: 20_000 });
  await btn.click();
}
