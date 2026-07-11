import { test, expect } from "@playwright/test";

/**
 * 감정 분류 → 본문/인물 추천 e2e.
 *
 * 백엔드 의존성: AI 사이드카(/classify-emotion). 로컬/CI 에서는 AI_MOCK=1 로 띄운
 * 결정형 키워드 분류기를 사용한다(LLM API 키 불필요). "불안/걱정" → ANXIOUS.
 * 프론트 client 가 게스트 발급 + disclaimer 동의를 자동 처리하므로 별도 인증 스텝 불필요.
 */
test.describe("감정 분류 → 추천", () => {
  test("불안 텍스트를 분류하면 결과와 추천이 노출된다", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Lemuel/i);

    await page.locator("textarea").fill("요즘 너무 불안하고 걱정돼서 잠이 안 와요");
    await page.getByRole("button", { name: /감정 분석/ }).click();

    // 결과 섹션 — 분류 결과 + 신뢰도 + 감정 라벨(ANXIOUS → 불안)
    await expect(page.getByText("분류 결과")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/신뢰도/)).toBeVisible();
    await expect(page.getByText("불안", { exact: false }).first()).toBeVisible();
  });

  test("감사 텍스트는 GRATEFUL 로 분류된다", async ({ page }) => {
    await page.goto("/");
    await page.locator("textarea").fill("오늘 하루 정말 감사하고 다행이라는 마음이 들어요");
    await page.getByRole("button", { name: /감정 분석/ }).click();

    await expect(page.getByText("분류 결과")).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText("감사", { exact: false }).first()).toBeVisible();
  });
});
