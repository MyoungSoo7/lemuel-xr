import { test, expect, Page } from "@playwright/test";

/**
 * 44px 터치 타깃 검사 — **인물 미션 씬 안쪽**. 2026-08-12 신설.
 *
 * `tap-targets.spec.ts` 는 백엔드가 필요 없는 7개 화면만 본다. 미션 씬은 `/api/game/*`
 * 로 씬을 받아야 그려지므로 거기 들어가지 못했고, 그래서 README §10 과 CI 주석에
 * 「미션 씬은 한 번도 안 쟀다」고 적어 두었다. 이 파일이 그 자리를 메운다.
 *
 * 처음 재고 나온 것(2026-08-12, 320px):
 *   · /elijah 위기 확인 씬의 상담번호 링크 **26×17** — 앱이 「이 사용자가 위험할 수
 *     있다」고 판단한 화면에서 전화 거는 링크가 앱 전체에서 제일 작았다.
 *   · /moses 변명 배정 버튼 68×30 · 81×30 (열 개가 촘촘히 붙어 있다)
 *   · /solomon 음성/자막 강도 버튼 36×26 — 자극을 낮추는 손잡이다.
 *   요셉·다윗·욥·예수는 깨끗했다.
 *
 * **씬 흐름을 인물마다 적지 않는다.** 인물별 정답 경로를 손으로 적으면 시나리오가
 * 바뀔 때마다 같이 썩고, 무엇보다 「그 경로에서만」 재게 된다. 대신 눌러지는 첫 버튼을
 * 계속 눌러 씬을 흘려보내며 매 단계 잰다. 어디로 흘러가든 **그 화면에 있는 것은 다
 * 잰다**는 쪽이 이 검사의 목적에 맞다.
 *
 * 한계 —
 *   · 분기마다 다 들어가지 않는다. 첫 버튼을 따라간 한 갈래만 본다.
 *   · **백엔드가 있어야 돈다.** 그래서 CI 의 프론트 잡에는 넣지 않았다(거기엔 백엔드가
 *     없다). 백엔드를 띄운 곳에서 돌려야 의미가 있고, 안 돌면 이 세 군데는 지켜지지
 *     않는다는 뜻이다. 조용히 초록이 되지 않도록 아래에서 분모를 함께 본다.
 */

const MIN = 44;
const STEPS = 12;

const CHARACTERS = [
  "/joseph",
  "/david",
  "/elijah",
  "/job",
  "/jesus",
  "/moses",
  "/solomon",
];

type Small = { tag: string; text: string; w: number; h: number; cls: string };

async function measure(page: Page) {
  return page.evaluate((min) => {
    const small: Small[] = [];
    let total = 0;
    const nodes = document.querySelectorAll<HTMLElement>(
      'a[href], button, [role="button"], input:not([type="hidden"]), select, textarea',
    );
    for (const el of Array.from(nodes)) {
      // 자리지기 사본(CrisisFooter) 은 보이지도 눌리지도 않는다 — 세면 안 된다.
      if (el.closest('[aria-hidden="true"]')) continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue;
      total++;
      if (r.width >= min && r.height >= min) continue;
      small.push({
        tag: el.tagName.toLowerCase(),
        text: (el.textContent ?? "").trim().slice(0, 30),
        w: Math.round(r.width),
        h: Math.round(r.height),
        cls: el.className.toString().slice(0, 60),
      });
    }
    return { total, small };
  }, MIN);
}

for (const route of CHARACTERS) {
  test(`${route} — 씬을 넘겨 가며 모든 터치 타깃이 ${MIN}px 이상이다`, async ({
    page,
  }) => {
    await page.goto(route);
    await page.waitForLoadState("networkidle");

    const violations: string[] = [];
    let measured = 0;

    for (let step = 0; step < STEPS; step++) {
      const { total, small } = await measure(page);
      // 분모를 함께 본다. 백엔드가 없거나 씬을 못 받아 아무것도 안 그려지면
      // 「작은 것 0 개」가 참이 되어 검사가 조용히 통과한다 — 그 초록은 거짓이다.
      if (total > 0) measured++;
      for (const s of small) {
        violations.push(
          `  [step ${step}] ${s.tag} "${s.text}" ${s.w}×${s.h} — ${s.cls}`,
        );
      }

      const next = page
        .locator('main button:not([disabled]), main a[href^="/"]')
        .first();
      if ((await next.count()) === 0) break;
      try {
        await next.click({ timeout: 3000 });
        await page.waitForTimeout(800);
      } catch {
        break; // 더 못 넘긴다 — 여기까지 잰 것으로 판정한다.
      }
    }

    expect(
      measured,
      `${route} 에서 잴 타깃이 한 단계도 없었다 — 씬이 안 그려졌다는 뜻이고, ` +
        `이 통과는 무의미하다. 백엔드가 떠 있는지 확인할 것.`,
    ).toBeGreaterThan(0);

    expect(violations, `${MIN}px 미만 타깃:\n${violations.join("\n")}`).toEqual(
      [],
    );
  });
}
