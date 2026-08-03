import { test, expect, Page } from "@playwright/test";

/**
 * Solomon 5-Scene E2E — 백엔드에만 있던 solomon 런타임에 프론트 문(門)을 낸 뒤의 완주 검증.
 *
 * 흐름:
 *   홈 → "Solomon" 카드 → /solomon → Scene 1 grab_and_place(제물 — optional) →
 *   Scene 2 cinematic(꿈의 응답) →
 *   Scene 3 pick_one(두 여인 재판; R4 동의 게이트 + converges_to 로컬 집행) →
 *   Scene 4 pick_one(헛되다; R4 동의 게이트 + 라벨링 optional) →
 *   Scene 5 outro(내려놓기 비강제 + 재정향) → 미션 완료 → 홈 redirect.
 *
 * 헤더 포맷: "Solomon — Scene {n}/5 · Mode: VR" (src/app/solomon/page.tsx).
 * /api/game/* disclaimer 게이트는 client.ts 가 게스트 토큰 발급 직후 자동 동의로 통과.
 */

async function waitForScene(page: Page, sceneNo: 1 | 2 | 3 | 4 | 5) {
  await expect(page.locator("header")).toContainText(
    new RegExp(`Scene ${sceneNo}/5`, "i"),
    { timeout: 20_000 },
  );
}

/** R4 동의 게이트를 통과한다 (Scene 3·4 는 trigger_warning 이 있어 본문 전에 게이트가 뜬다). */
async function consent(page: Page) {
  const btn = page.getByRole("button", { name: "계속한다" });
  await expect(btn).toBeVisible({ timeout: 20_000 });
  await btn.click();
}

test.describe("Solomon 5-Scene mission", () => {
  test("솔로몬 5씬 완주 흐름 (재판 → 허무 라벨링 → 재정향)", async ({
    page,
  }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Lemuel/i);

    // 홈 — Solomon 카드가 실제로 존재해야 한다 (이 미션의 유일한 입구)
    await page
      .getByRole("link", { name: /Solomon/ })
      .first()
      .click();
    await page.waitForURL(/\/solomon$/);

    // Scene 1 grab_and_place — 제물은 core:false 지만 여기서는 올리는 경로로 진행
    await waitForScene(page, 1);
    await page.getByRole("button", { name: /제물을 올린다/ }).click();

    // Scene 2 cinematic
    await waitForScene(page, 2);
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 3 — R4 동의 카드가 본문보다 먼저 떠야 한다
    await waitForScene(page, 3);
    await expect(
      page.getByText(/건너뛰어도 이야기는 온전히 이어집니다/),
    ).toBeVisible();
    await consent(page);

    // converges_to 로컬 집행 — 첫째/둘째 여인 선택은 씬을 넘기지 않고 재고 텍스트를 띄운다
    await page.getByRole("button", { name: /첫째 여인에게 주라/ }).click();
    await expect(page.getByText(/판결을 멈추고/)).toBeVisible({
      timeout: 10_000,
    });
    await waitForScene(page, 3); // 여전히 Scene 3

    // 칼을 가져오라 — 이 선택만 실제로 씬을 넘긴다
    await page.getByRole("button", { name: /칼을 가져오라/ }).click();

    // Scene 4 — R4 동의 후 허무 라벨 선택
    await waitForScene(page, 4);
    await consent(page);
    await page
      .getByRole("button", { name: /다 이루었는데, 비어 있습니다/ })
      .click();

    // Scene 5 outro — 재정향 문구 + 내려놓기(비강제) + 완료
    await waitForScene(page, 5);
    const outro = await page.locator("main").innerText();
    expect(outro).toContain("경외");

    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 3·4 를 모두 건너뛰어도 결말에 도달한다 (R4)", async ({
    page,
  }) => {
    await page.goto("/solomon");

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /올리지 않고 다음으로/ }).click();

    await waitForScene(page, 2);
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 3 건너뛰기 → 비묘사 요약 자막이 대신 표시된다 (서사 연속성 유지)
    await waitForScene(page, 3);
    await page.getByRole("button", { name: /건너뛰기/ }).click();

    await waitForScene(page, 4);
    await expect(page.getByText(/산 아기는 참 어머니의 품으로/)).toBeVisible();

    // Scene 4 도 건너뛰기 → 라벨 없음 경로
    await page.getByRole("button", { name: /건너뛰기/ }).click();

    await waitForScene(page, 5);
    // 라벨 없는 경로의 default 재정향 문구 ("하나님을 경외하라" 는 씬 제목에도 있어
    // default 문구 고유 구절로 특정한다)
    await expect(
      page.getByText(/가장 많이 가진 왕이 마지막에 남긴 말은 하나뿐이었다/),
    ).toBeVisible();
    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 4 는 이름을 붙이지 않고도 넘어갈 수 있다 (라벨링은 초대이지 과제가 아님)", async ({
    page,
  }) => {
    await page.goto("/solomon");

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /올리지 않고 다음으로/ }).click();

    await waitForScene(page, 2);
    await page.getByRole("button", { name: /계속 →/ }).click();

    await waitForScene(page, 3);
    await consent(page);
    await page.getByRole("button", { name: /칼을 가져오라/ }).click();

    await waitForScene(page, 4);
    await consent(page);
    // R2 — 이 씬은 outro 를 기다리지 않고 위기 자원을 화면에 직접 노출한다
    await expect(
      page.getByText(/공허를 느끼는 것은 잘못이 아닙니다/),
    ).toBeVisible();
    await page.getByRole("button", { name: /이름 붙이지 않고 계속/ }).click();

    await waitForScene(page, 5);
    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });
});
