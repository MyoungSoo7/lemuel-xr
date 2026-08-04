import { test, expect, Page } from "@playwright/test";

/**
 * Elijah 5-Scene E2E — 백엔드에만 있던 elijah 런타임에 프론트 문(門)을 낸 뒤의 완주 검증.
 *
 * 흐름:
 *   홈 → "Elijah" 카드 → /elijah → Scene 1 cinematic(갈멜산 후 도망) →
 *   Scene 2 scripture_reading(로뎀나무·죽음 갈구; R4 동의 게이트) →
 *   Scene 3 gesture_sequence(음식·잠 — 비강제) →
 *   Scene 4 scripture_reading(세미한 음성) →
 *   Scene 5 outro(7000명) → 미션 완료 → 홈 redirect.
 *
 * 헤더 포맷: "Elijah — Scene {n}/5 · Mode: VR" (src/app/elijah/page.tsx).
 */

async function waitForScene(page: Page, sceneNo: 1 | 2 | 3 | 4 | 5) {
  await expect(page.locator("header")).toContainText(
    new RegExp(`Scene ${sceneNo}/5`, "i"),
    { timeout: 20_000 },
  );
}

test.describe("Elijah 5-Scene mission", () => {
  test("엘리야 5씬 완주 흐름 (죽음 갈구 → 음식과 잠 → 세미한 음성)", async ({
    page,
  }) => {
    await page.goto("/");
    await page
      .getByRole("link", { name: /Elijah/ })
      .first()
      .click();
    await page.waitForURL(/\/elijah$/);

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 2 — R4 동의 카드가 본문보다 먼저 떠야 한다
    await waitForScene(page, 2);
    await expect(
      page.getByText(/건너뛰어도 이야기는 온전히 이어집니다/),
    ).toBeVisible();
    // 동의 전에는 죽음 갈구 본문이 노출되면 안 된다
    await expect(page.getByText(/여호와여 이제 됐습니다/)).toHaveCount(0);
    // crisis_check 씬이므로 위기 연결이 본문보다 위에 있어야 한다.
    // 전역 CrisisFooter(layout) 에도 109 링크가 상시 있으므로 main 안으로 한정한다 —
    // 즉 "씬 자체가" 위기 자원을 올렸는지를 본다.
    await expect(
      page.getByRole("main").getByRole("link", { name: "109" }),
    ).toBeVisible();

    await page.getByRole("button", { name: "계속한다" }).click();
    await expect(page.getByText(/여호와여 이제 됐습니다/)).toBeVisible();
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 3 — 두 동작(음식·잠) + 실천 리마인더
    await waitForScene(page, 3);
    await page.getByRole("button", { name: /음식을 받는다/ }).click();
    await page.getByRole("button", { name: /누워서 잔다/ }).click();
    await expect(
      page.getByText(/오늘 한 끼 따뜻한 음식을 드세요/),
    ).toBeVisible();
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 4 — 세미한 음성 + 예시 목록
    await waitForScene(page, 4);
    await expect(page.getByText(/30초간 마음이 평온했던 자리/)).toBeVisible();
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 5 outro
    await waitForScene(page, 5);
    await expect(page.getByText(/7000명이 남았다/)).toBeVisible();

    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 2 를 건너뛰어도 결말에 도달하고, 죽음 갈구 본문은 노출되지 않는다 (R4)", async ({
    page,
  }) => {
    await page.goto("/elijah");

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /계속 →/ }).click();

    await waitForScene(page, 2);
    await page.getByRole("button", { name: /건너뛰기/ }).click();

    // skip_alternative_scene_id(=3) 로 이동 — 서사는 이어지되 묘사는 건너뛴다
    await waitForScene(page, 3);
    await expect(page.getByText(/여호와여 이제 됐습니다/)).toHaveCount(0);

    // Scene 3 는 아무 동작도 누르지 않고 넘어갈 수 있어야 한다 (R3 — 비강제)
    await page.getByRole("button", { name: /계속 →/ }).click();
    await waitForScene(page, 4);
    await page.getByRole("button", { name: /계속 →/ }).click();

    await waitForScene(page, 5);
    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("저작자용 가드 주석(*_note)은 사용자 화면에 절대 렌더되지 않는다", async ({
    page,
  }) => {
    await page.goto("/elijah");

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /계속 →/ }).click();
    await waitForScene(page, 2);
    await page.getByRole("button", { name: "계속한다" }).click();

    // language_note 는 저작자에게 주는 R 가드 메모다 — 제품 카피가 아니다
    await expect(page.getByText(/R 가드/)).toHaveCount(0);

    await page.getByRole("button", { name: /계속 →/ }).click();
    await waitForScene(page, 3);
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 4 framing_note
    await waitForScene(page, 4);
    await expect(page.getByText(/R\/신학 가드/)).toHaveCount(0);

    await page.getByRole("button", { name: /계속 →/ }).click();
    // Scene 5 timeline_note
    await waitForScene(page, 5);
    await expect(page.getByText(/R3 가드/)).toHaveCount(0);
  });
});
