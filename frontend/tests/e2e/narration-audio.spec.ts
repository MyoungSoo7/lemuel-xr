import { test, expect, Page } from "@playwright/test";

/**
 * 나레이션 오디오("🔊 듣기") E2E — graceful degradation 검증.
 *
 * 오디오는 *보조 기능* 이다. 로컬엔 TTS 사이드카(Coqui XTTS-v2)가 없어
 * /api/tts/synthesize 는 502/에러를 내는 게 정상이다. 검증 대상:
 *   1. 텍스트가 있는 화면(게임 Scene 1)에 "듣기" 버튼이 렌더된다.
 *   2. 버튼을 눌러도 앱이 깨지지 않는다 — TTS 실패 시 조용히 비활성/숨김.
 *
 * 실제 오디오 재생은 검증 대상이 아니다 (사이드카 없음).
 */

async function waitForJosephScene1(page: Page) {
  await expect(page.locator("header")).toContainText(/Joseph — Scene 1\/5/i, {
    timeout: 20_000,
  });
}

test.describe("Narration audio (듣기) — graceful degradation", () => {
  test("Joseph Scene 1 내레이션에 '듣기' 버튼이 렌더된다", async ({ page }) => {
    await page.goto("/joseph");
    await waitForJosephScene1(page);

    // "듣기" 버튼(aria-label 에 '음성' 포함)이 Scene 1 본문 아래 존재.
    const listenBtn = page.getByRole("button", { name: /음성/ }).first();
    await expect(listenBtn).toBeVisible({ timeout: 20_000 });
  });

  test("'듣기' 클릭 후에도 앱이 깨지지 않고 계속 진행된다", async ({ page }) => {
    await page.goto("/joseph");
    await waitForJosephScene1(page);

    const listenBtn = page.getByRole("button", { name: /음성/ }).first();
    await expect(listenBtn).toBeVisible({ timeout: 20_000 });

    // TTS 사이드카 없음 → synthesize 실패. 클릭해도 예외/크래시 없이 조용히 처리돼야 한다.
    await listenBtn.click();

    // 앱이 살아있음을 확인 — 헤더는 그대로, "계속" 버튼으로 정상 진행 가능.
    await expect(page.locator("header")).toContainText(/Joseph — Scene 1\/5/i);
    const continueBtn = page.getByRole("button", { name: /계속/ });
    await expect(continueBtn).toBeEnabled({ timeout: 20_000 });
    await continueBtn.click();

    // Scene 2 로 정상 전이 → 앱이 안 깨졌다는 증거.
    await expect(page.locator("header")).toContainText(/Joseph — Scene 2\/5/i, {
      timeout: 20_000,
    });
  });
});
