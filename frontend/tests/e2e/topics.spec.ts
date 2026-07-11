import { test, expect } from "@playwright/test";

/**
 * /topics — 7 주제 카드 렌더 + 하위 페이지 진입 검증.
 *
 * 백엔드 /api/content/topics 는 disclaimer 게이트 예외(공개 카탈로그) 지만,
 * /cards·/journal/guidance 등은 게이트 대상 — client.ts 자동 동의로 통과.
 */

test.describe("Topics 페이지", () => {
  test("7 주제 카드가 렌더된다", async ({ page }) => {
    await page.goto("/topics");
    await expect(
      page.getByRole("heading", { name: /일상 영적 양식/ }),
    ).toBeVisible();

    // 주제 목록 — 백엔드에서 7개 topic 로드. 최소 몇 개 이상 버튼이 떠야 함.
    const topicButtons = page.locator("section button");
    await expect
      .poll(async () => topicButtons.count(), { timeout: 20_000 })
      .toBeGreaterThanOrEqual(5);

    // 대표 주제 텍스트 확인 — 주제 list 버튼 안의 것으로 한정
    // (하위 진입 링크에도 유사 문구가 있어 strict-mode 충돌 방지)
    await expect(
      page.getByRole("button", { name: /일기와 묵상/ }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: /^#2 잠언과 지혜$/ }),
    ).toBeVisible();
  });

  test("주제 선택 시 카드 영역이 반응한다", async ({ page }) => {
    await page.goto("/topics");
    await expect(page.getByText(/← 주제를 선택하세요/)).toBeVisible();

    // 첫 주제 버튼 클릭 → placeholder 안내 사라지고 카드 헤더 표시
    await page.getByRole("button", { name: /일기와 묵상/ }).click();
    await expect(page.getByText(/← 주제를 선택하세요/)).toHaveCount(0);
  });

  const subPages: Array<{ path: string; heading: RegExp }> = [
    { path: "/topics/journal", heading: /일기와 조언/ },
    { path: "/topics/proverbs", heading: /잠언과 지혜/ },
    { path: "/topics/ecclesiastes", heading: /전도서와 인생/ },
    { path: "/topics/practice", heading: /실천과 성찰/ },
  ];

  for (const { path, heading } of subPages) {
    test(`하위 페이지 ${path} 가 열리고 핵심 요소가 렌더된다`, async ({ page }) => {
      await page.goto(path);
      await expect(page.getByRole("heading", { name: heading })).toBeVisible({
        timeout: 20_000,
      });
    });
  }
});
