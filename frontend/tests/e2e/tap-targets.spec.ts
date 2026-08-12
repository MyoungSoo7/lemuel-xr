import { test, expect } from "@playwright/test";

/**
 * 터치 타깃 최소 크기 검사 — 2026-08-12 신설.
 *
 * 기준은 44×44 CSS px 다. WCAG 2.5.5 Target Size (Enhanced, AAA) 와 Apple 의
 * Human Interface Guidelines 가 같은 수를 쓴다.
 *   · https://www.w3.org/WAI/WCAG22/Understanding/target-size-enhanced.html
 *   · https://developer.apple.com/design/human-interface-guidelines/accessibility
 *
 * 왜 이 앱에서 유독 이걸 재는가. 이 화면의 상시 컨트롤 뒤에 있는 것이 **자살예방
 * 상담 번호**다. 손가락이 빗나가는 비용이 「불편」이 아니다. 실제로 이 검사를 처음
 * 돌렸을 때 나온 것이, 위기 푸터에서 **펼치기 타깃은 44px 인데 전화 거는 타깃은
 * 23×15px** 라는 사실이었다 — 제일 급한 동작이 제일 누르기 어려웠다.
 *
 * ⚠️ 이 초록의 주장 범위:
 *   · 재는 것은 **경계 상자**지 실제 히트 영역이 아니다. `::before` 로 넓힌 타깃,
 *     겹친 요소, `touch-action` 은 여기서 안 보인다.
 *   · 아래 ROUTES 는 **백엔드 없이도 렌더되는 화면**만이다. 미션 화면(요셉·모세·
 *     다윗·엘리야·욥·예수·솔로몬)의 씬 내부 버튼은 **한 번도 재지 않았다** —
 *     백엔드를 띄우고 돌려야 한다.
 *   · 320px(iPhone SE) 에서 가장 잘 걸린다. 좁을수록 줄바꿈으로 높이가 변한다.
 */

const ROUTES = [
  "/",
  "/topics",
  "/values",
  "/topics/journal",
  "/topics/proverbs",
  "/topics/ecclesiastes",
  "/topics/bookmarks",
];

const MIN = 44;

type Small = { tag: string; text: string; w: number; h: number; cls: string };

for (const route of ROUTES) {
  test(`${route} — 모든 터치 타깃이 ${MIN}px 이상이다`, async ({ page }) => {
    await page.goto(route);
    await page.waitForLoadState("networkidle");

    const small: Small[] = await page.evaluate((min) => {
      const out: Small[] = [];
      const nodes = document.querySelectorAll<HTMLElement>(
        'a[href], button, [role="button"], input:not([type="hidden"]), select, textarea',
      );
      for (const el of Array.from(nodes)) {
        // 자리지기 사본(CrisisFooter) 은 보이지도 눌리지도 않는다 — 세면 안 된다.
        if (el.closest('[aria-hidden="true"]')) continue;
        const r = el.getBoundingClientRect();
        if (r.width === 0 && r.height === 0) continue; // 안 그려진 것
        if (r.width >= min && r.height >= min) continue;
        out.push({
          tag: el.tagName.toLowerCase(),
          text: (el.textContent ?? "").trim().slice(0, 30),
          w: Math.round(r.width),
          h: Math.round(r.height),
          cls: el.className.toString().slice(0, 60),
        });
      }
      return out;
    }, MIN);

    expect(
      small,
      `${MIN}px 미만 타깃:\n` +
        small
          .map((s) => `  ${s.tag} "${s.text}" ${s.w}×${s.h} — ${s.cls}`)
          .join("\n"),
    ).toEqual([]);
  });
}
