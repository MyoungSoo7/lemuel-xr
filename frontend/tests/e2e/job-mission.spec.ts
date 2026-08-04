import { test, expect, Page } from "@playwright/test";

/**
 * Job 5-Scene E2E — 백엔드에만 있던 job 런타임에 프론트 문(門)을 낸 뒤의 완주 검증.
 *
 * 흐름:
 *   홈 → "Job" 카드 → /job → Scene 1 cinematic(침묵의 7일) →
 *   Scene 2 scripture_reading(태어난 날을 저주; R4 동의 게이트) →
 *   Scene 3 pick_one(친구들의 위로 — 선택 시 response 를 먼저 보여준 뒤 진행) →
 *   Scene 4 scripture_reading(폭풍 가운데 응답) →
 *   Scene 5 outro(답 없는 만남 + suffering_footer) → 미션 완료 → 홈 redirect.
 *
 * 헤더 포맷: "Job — Scene {n}/5 · Mode: VR" (src/app/job/page.tsx).
 */

async function waitForScene(page: Page, sceneNo: 1 | 2 | 3 | 4 | 5) {
  await expect(page.locator("header")).toContainText(
    new RegExp(`Scene ${sceneNo}/5`, "i"),
    { timeout: 20_000 },
  );
}

test.describe("Job 5-Scene mission", () => {
  test("욥 5씬 완주 흐름 (비탄 → 거짓 위로 인식 → 답 없는 만남)", async ({
    page,
  }) => {
    await page.goto("/");
    await page.getByRole("link", { name: /Job/ }).first().click();
    await page.waitForURL(/\/job$/);

    await waitForScene(page, 1);
    await expect(page.getByText(/7일 밤낮 함께 앉았습니다/)).toBeVisible();
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 2 — R4 동의 게이트
    await waitForScene(page, 2);
    await expect(
      page.getByText(/건너뛰어도 이야기는 온전히 이어집니다/),
    ).toBeVisible();
    await expect(page.getByText(/태어난 날을 저주합니다/)).toHaveCount(0);
    await page.getByRole("button", { name: "계속한다" }).click();
    await expect(page.getByText(/태어난 날을 저주합니다/)).toBeVisible();
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 3 — 선택은 씬을 바로 넘기지 않고 저작된 response 를 먼저 보여준다
    await waitForScene(page, 3);
    await page
      .getByRole("button", { name: /나도 이런 말 들어본 적 있다/ })
      .click();
    await expect(page.getByText(/당신만이 아닙니다/)).toBeVisible();
    await waitForScene(page, 3); // 아직 Scene 3
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 4
    await waitForScene(page, 4);
    await expect(page.getByText(/우주의 광활함/)).toBeVisible();
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 5 outro — R3(답 없는 만남) + 학대 상황 고지
    await waitForScene(page, 5);
    await expect(page.getByText(/만남.*얻었습니다/)).toBeVisible();
    await expect(page.getByText(/가정폭력·종교적 학대/)).toBeVisible();

    await page.getByRole("button", { name: /미션 완료/ }).click();
    await page.waitForURL((url) => url.pathname === "/", { timeout: 15_000 });
  });

  test("Scene 3 의 세 선택지는 모두 유효하며 '네 탓' 으로 되돌아오지 않는다 (R2)", async ({
    page,
  }) => {
    await page.goto("/job");

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /계속 →/ }).click();
    await waitForScene(page, 2);
    await page.getByRole("button", { name: /건너뛰기/ }).click();

    await waitForScene(page, 3);
    // "잘 모르겠다" 도 정당한 선택 — 욥기는 답이 아닌 질문의 책
    await page.getByRole("button", { name: /잘 모르겠다/ }).click();
    await expect(
      page.getByText(/답이 아닌 \*질문\* 의 책입니다/),
    ).toBeVisible();
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
    await page.goto("/job");

    await waitForScene(page, 1);
    await page.getByRole("button", { name: /계속 →/ }).click();
    await waitForScene(page, 2);
    await page.getByRole("button", { name: "계속한다" }).click();
    // language_note
    await expect(page.getByText(/R 가드/)).toHaveCount(0);
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 3 theology_note — '인과응보' 는 저작 가드 문구지 사용자 카피가 아니다
    await waitForScene(page, 3);
    await expect(page.getByText(/인과응보/)).toHaveCount(0);
    await expect(page.getByText(/신학 가드/)).toHaveCount(0);
    await page.getByRole("button", { name: /잘 모르겠다/ }).click();
    await page.getByRole("button", { name: /계속 →/ }).click();

    await waitForScene(page, 4);
    await page.getByRole("button", { name: /계속 →/ }).click();

    // Scene 5 restoration_note — '갑절 회복' 은 결말로 쓰이지 않는다 (R3)
    await waitForScene(page, 5);
    await expect(page.getByText(/갑절 회복/)).toHaveCount(0);
    await expect(page.getByText(/R3 가드/)).toHaveCount(0);
  });
});
