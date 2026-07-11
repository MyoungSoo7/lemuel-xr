import { test, expect, Page } from "@playwright/test";

/**
 * Gated 인물 미션 (jesus) — Scene 1 cinematic 까지만 검증.
 *
 * jesus 는 Scene 2~ 인터랙션이 신학·임상 자문 검토 통과 전이라 비활성.
 * 완주 불가하므로:
 *   1. 카드 → 게임 진입 → Scene 1 cinematic 렌더 확인
 *   2. "계속" 후 Phase 2 안내(자문 통과 후 활성) 문구 렌더 확인
 *
 * moses / david 는 Phase 2 완전 활성(요셉 동급)이라 이 gated 테스트에서 제외됨 —
 * 완주 검증은 각각 moses-mission.spec.ts / david-mission.spec.ts 참조.
 */

interface GatedMission {
  slug: string;
  headerRe: RegExp;
  linkName: RegExp;
}

/**
 * jesus Scene 1 → "cinematic" (계속 버튼 활성 → 누른 뒤 Phase 2 안내).
 * 최종적으로 "자문 통과 후 활성" 안내 + "미션 종료" 버튼에 도달한다.
 */
const MISSIONS: GatedMission[] = [
  { slug: "jesus", headerRe: /Jesus — Scene/i, linkName: /Jesus/ },
];

async function waitScene1(page: Page, headerRe: RegExp) {
  await expect(page.locator("header")).toContainText(headerRe, {
    timeout: 20_000,
  });
  await expect(page.locator("header")).toContainText(/Scene 1\//i);
}

test.describe("Gated 인물 미션 (scene 1 까지)", () => {
  for (const m of MISSIONS) {
    test(`${m.slug} — 홈 카드 → Scene 1 cinematic → Phase 2 안내`, async ({
      page,
    }) => {
      await page.goto("/");
      await page.getByRole("link", { name: m.linkName }).first().click();
      await page.waitForURL(new RegExp(`/${m.slug}$`));

      // Scene 1 렌더
      await waitScene1(page, m.headerRe);

      // jesus 는 cinematic — "계속" 을 눌러 Phase 2 안내로 진행.
      const continueBtn = page.getByRole("button", { name: /계속/ });
      if (await continueBtn.isVisible().catch(() => false)) {
        await continueBtn.click();
      }

      // 최종 — Phase 2 안내 문구 + 미션 종료 버튼
      await expect(page.getByText(/자문 통과 후 활성/)).toBeVisible({
        timeout: 20_000,
      });
      await expect(
        page.getByRole("button", { name: /미션 종료/ }),
      ).toBeVisible();
    });
  }

  test("jesus — 직접 URL 진입도 Scene 1 이 렌더된다", async ({ page }) => {
    await page.goto("/jesus");
    await waitScene1(page, /Jesus — Scene/i);
    await expect(
      page.getByRole("button", { name: /계속/ }),
    ).toBeVisible();
  });
});
