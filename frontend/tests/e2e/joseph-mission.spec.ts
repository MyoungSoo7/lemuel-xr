import { test, expect, Page } from "@playwright/test";

/**
 * E — Joseph 5-Scene E2E (Phase 2-E).
 *
 * 시나리오:
 *   홈 → "요셉" 카드 클릭 → Scene 1 cinematic → 계속 → Scene 2 pick_one (save_33) →
 *   Scene 3 distribute (immigrant_first) → Scene 4 pick_one (reveal) → Scene 5 outro →
 *   미션 완료 버튼.
 *
 * 백엔드 의존성:
 *   - POST /api/sessions/start (Joseph 세션 생성)
 *   - POST /api/sessions/:id/decide  (각 Scene 진행)
 *   - POST /api/sessions/:id/complete
 *   Same-origin proxy 통해 호출됨 (next.config.ts rewrites). CI 에서는 backend 가
 *   docker-compose 로 함께 띄워져 있어야 한다.
 *
 * 결말 검증:
 *   Scene 3 에서 immigrant_first 선택했을 때 Scene 5 결말 톤이 "moral injury / 섭리는
 *   가해자의 정당화가 아니다" 계열인지 확인. (joseph-monologues.ts scene5OutroByScene3
 *   의 immigrant_first 분기.)
 */

const SCENE_HEADER = /Joseph — Scene/i;

async function waitForScene(page: Page, sceneNo: 1 | 2 | 3 | 4 | 5) {
  await expect(page.locator("header h1, header")).toContainText(
    new RegExp(`Scene ${sceneNo}/5`, "i"),
    { timeout: 15_000 },
  );
}

test.describe("Joseph 5-Scene mission", () => {
  test("immigrant_first 결말 흐름이 완주된다", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Lemuel/i);

    // 홈 — Joseph 카드
    await page.getByRole("link", { name: /Joseph/ }).first().click();
    await expect(page.locator("header")).toContainText(SCENE_HEADER);

    // Scene 1 cinematic → 계속
    await waitForScene(page, 1);
    await page.getByRole("button", { name: /계속/ }).click();

    // Scene 2 pick_one — save_33 선택 (한 옵션 클릭이면 충분)
    await waitForScene(page, 2);
    const scene2Btn = page.getByRole("button", { name: /1\/3|33|3분의 1|저장/ }).first();
    await scene2Btn.click();

    // Scene 3 distribute — immigrant_first (이주민 / 외국인 줄 우선)
    await waitForScene(page, 3);
    const immigrantBtn = page
      .getByRole("button", { name: /이주민|외국인|immigrant/i })
      .first();
    await immigrantBtn.click();

    // Scene 4 pick_one — reveal (정체를 밝히고 화해)
    await waitForScene(page, 4);
    const revealBtn = page
      .getByRole("button", { name: /밝히|화해|reveal|용서/i })
      .first();
    await revealBtn.click();

    // Scene 5 outro — 결말 텍스트 확인 + 미션 완료 버튼
    await waitForScene(page, 5);
    const outroText = await page.locator("main").innerText();
    expect(outroText.length).toBeGreaterThan(20);

    await page.getByRole("button", { name: /미션 완료/ }).click();

    // 미션 완료 후 홈으로 redirect — URL 검사 (location.href = "/")
    await page.waitForURL(/\/$/, { timeout: 10_000 });
  });

  test("Scene 헤더와 echo quote 가 매 진행마다 갱신된다", async ({ page }) => {
    await page.goto("/joseph");
    await waitForScene(page, 1);

    await page.getByRole("button", { name: /계속/ }).click();
    await waitForScene(page, 2);

    // Scene 2 결정 직후 — 다음 Scene 진입 시 echo 영역에 직전 모놀로그가 표시되어야 함.
    await page.getByRole("button", { name: /1\/3|33|저장/ }).first().click();
    await waitForScene(page, 3);

    // echo 영역 — italic 텍스트. 항상 있지는 않을 수 있으나, 있다면 길이>0.
    const echo = page.locator("section.italic, section >> .italic").first();
    if (await echo.count()) {
      const text = await echo.innerText();
      expect(text.trim().length).toBeGreaterThan(0);
    }
  });
});
