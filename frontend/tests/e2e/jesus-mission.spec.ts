import { test, expect, Page } from "@playwright/test";

/**
 * Jesus 7-Scene E2E — 트랙 B 정점(capstone) 완전 활성 full playthrough (요셉·모세·다윗 동급).
 *
 * 흐름:
 *   홈 → "Jesus" 카드 → /jesus → Scene 1 cinematic(성육신) → 계속 →
 *   Scene 2 scripture_reading(팔복, 세 줄 읽기) →
 *   Scene 3 gesture_sequence(만짐: 다가간다·손을 내민다) →
 *   Scene 4 pick_one(길·진리·생명 3분기) →
 *   Scene 5 contemplative(겟세마네·십자가, R4 정서 경고 동의 후 묵상) →
 *   Scene 6 scripture_reading(부활 빈 무덤) →
 *   Scene 7 outro(승천·생명의 강) → 미션 완료 → 홈 redirect.
 *
 * 헤더 포맷: "Jesus — Scene {n}/7 · Mode: VR" (src/app/jesus/page.tsx).
 * /api/game/* disclaimer 게이트는 client.ts 가 게스트 토큰 발급 직후 자동 동의로 통과.
 *
 * 안전 검증:
 *  · R4 — Scene 5 정서 경고 카드에서 (a) 동의→묵상 완주, (b) 건너뛰기→Scene 6 직행 둘 다.
 */

const SCENE_HEADER = /Jesus — Scene/i;

async function waitForScene(page: Page, sceneNo: 1 | 2 | 3 | 4 | 5 | 6 | 7) {
  await expect(page.locator("header")).toContainText(
    new RegExp(`Scene ${sceneNo}/7`, "i"),
    { timeout: 20_000 },
  );
}

test.describe("Jesus 7-Scene mission (capstone)", () => {
  test("예수 7씬 완주 흐름 (겟세마네 동의 → 완주)", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Lemuel/i);

    // 홈 — Jesus 카드
    await page.getByRole("link", { name: /Jesus/ }).first().click();
    await page.waitForURL(/\/jesus$/);

    // Scene 1 cinematic(성육신) → 계속
    await waitForScene(page, 1);
    await expect(page.locator("header")).toContainText(SCENE_HEADER);
    await page.getByRole("button", { name: /계속/ }).click();

    // Scene 2 scripture_reading(팔복) — 본문 줄 모두 읽고 다음으로
    await waitForScene(page, 2);
    await readAllScripture(page);

    // Scene 3 gesture_sequence(만짐) — 몸짓 순서대로
    await waitForScene(page, 3);
    await clickAllGestureSteps(page);

    // Scene 4 pick_one(길·진리·생명) — 첫 옵션 선택
    await waitForScene(page, 4);
    await pickFirstSceneButton(page);

    // Scene 5 contemplative(겟세마네·십자가) — R4 동의 후 묵상
    await waitForScene(page, 5);
    await expect(
      page.getByText(/고통과 죽음/).first(),
    ).toBeVisible({ timeout: 20_000 });
    await page.getByRole("button", { name: /준비됐어요/ }).click();
    await page.getByRole("button", { name: /잠시 머문다/ }).click();

    // Scene 6 scripture_reading(부활 빈 무덤) — 본문 읽고 다음으로
    await waitForScene(page, 6);
    await readAllScripture(page);

    // Scene 7 outro(승천·생명의 강) — 결말 텍스트 + 위기 안내 + 미션 완료
    await waitForScene(page, 7);
    const outro = await page.locator("main").innerText();
    expect(outro.length).toBeGreaterThan(20);
    // R1 — 위기 라우팅 안내가 outro 에 노출된다
    await expect(page.getByText(/1393/).first()).toBeVisible();

    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 5 정서 경고에서 '건너뛸게요' 로도 Scene 6·7 에 도달한다 (R4)", async ({
    page,
  }) => {
    await page.goto("/jesus");

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /계속/ }).click();

    await waitForScene(page, 2);
    await readAllScripture(page);

    await waitForScene(page, 3);
    await clickAllGestureSteps(page);

    await waitForScene(page, 4);
    await pickFirstSceneButton(page);

    // Scene 5 — 경고 카드에서 건너뛰기 → Scene 6 로 직행 (R4)
    await waitForScene(page, 5);
    await expect(
      page.getByText(/건너뛰어도 괜찮습니다/).first(),
    ).toBeVisible({ timeout: 20_000 });
    await page.getByRole("button", { name: /건너뛸게요/ }).click();

    await waitForScene(page, 6);
    await readAllScripture(page);

    await waitForScene(page, 7);
    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 헤더가 진행마다 갱신된다 (직접 /jesus 진입)", async ({ page }) => {
    await page.goto("/jesus");
    await waitForScene(page, 1);

    await page.getByRole("button", { name: /계속/ }).click();
    await waitForScene(page, 2);

    await readAllScripture(page);
    await waitForScene(page, 3);
  });
});

/**
 * scripture_reading — 본문 줄(✓ 안 붙은 것)을 하나씩 눌러 모두 읽은 뒤 "다음으로" 를 누른다.
 */
async function readAllScripture(page: Page) {
  const lineScope = () =>
    page
      .locator("main section")
      .getByRole("button")
      .filter({ hasNotText: /다음으로|모두 읽어|계속|미션 완료|오류|듣기|정지|불러오는 중|음성|Jesus|David|홈/ })
      .filter({ hasNotText: /✓/ });
  // 최대 5줄까지 (팔복 3줄 / 부활 2줄) — 남지 않을 때까지 클릭
  for (let i = 0; i < 6; i++) {
    const btn = lineScope().first();
    if ((await btn.count()) === 0) break;
    await btn.click();
  }
  await page.getByRole("button", { name: /다음으로/ }).click();
}

/**
 * gesture_sequence — main 안의 순서 몸짓 버튼(1./2.)을 모두 순서대로 누른다.
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
 * 현재 Scene 의 첫 번째 "선택" 버튼(pick_one). cinematic/outro 의 계속·완료,
 * 홈 네비 링크, gesture 번호 버튼을 피한다.
 */
async function pickFirstSceneButton(page: Page) {
  const btn = page
    .locator("main section")
    .getByRole("button")
    .filter({ hasNotText: /계속|미션 완료|오류|듣기|정지|불러오는 중|음성|다음으로|머문다|건너뛸게요|준비됐어요/ })
    .first();
  await expect(btn).toBeVisible({ timeout: 20_000 });
  await btn.click();
}
