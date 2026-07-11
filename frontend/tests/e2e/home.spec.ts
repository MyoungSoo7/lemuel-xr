import { test, expect } from "@playwright/test";

/**
 * 홈 랜딩 — 렌더 + 네비게이션 검증.
 *
 * 홈은 감정 입력폼 + (분류 결과 없을 때) 직접 진입 카드 묶음을 노출한다:
 *   - 매일의 단련: /values, /topics
 *   - VR 인물 미션: /joseph, /moses, /david, /jesus
 */

test.describe("홈 랜딩", () => {
  test("헤더 + 감정 입력폼 + 미션 카드가 렌더된다", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Lemuel/i);
    await expect(page.getByRole("heading", { name: /Lemuel XR/i })).toBeVisible();

    // 감정 입력 폼
    await expect(page.getByPlaceholder(/오늘 너무 외롭고/)).toBeVisible();
    await expect(
      page.getByRole("button", { name: /감정 분석/ }),
    ).toBeVisible();

    // VR 인물 미션 4카드
    await expect(page.getByRole("link", { name: /Joseph/ })).toBeVisible();
    await expect(page.getByRole("link", { name: /Moses/ })).toBeVisible();
    await expect(page.getByRole("link", { name: /David/ })).toBeVisible();
    await expect(page.getByRole("link", { name: /Jesus/ })).toBeVisible();

    // 매일의 단련 링크 (topics / values)
    await expect(page.getByRole("link", { name: /7 주제 카드/ })).toBeVisible();
    await expect(page.getByRole("link", { name: /7 가치 빌더/ })).toBeVisible();
  });

  test("topics 링크로 이동한다", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: /7 주제 카드/ }).click();
    await page.waitForURL(/\/topics$/);
    await expect(
      page.getByRole("heading", { name: /일상 영적 양식/ }),
    ).toBeVisible();
  });

  test("Joseph 미션 카드로 이동한다", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: /Joseph/ }).first().click();
    await page.waitForURL(/\/joseph$/);
    await expect(page.locator("header")).toContainText(/Joseph — Scene/i);
  });
});
