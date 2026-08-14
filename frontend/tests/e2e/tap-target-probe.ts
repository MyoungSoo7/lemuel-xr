import type { Page } from "@playwright/test";

/**
 * 터치 타깃 측정 프로브 — `tap-targets` · `tap-targets-data` · `mission-tap-targets`
 * 세 스펙이 **같은 자로** 재게 하려고 뽑아 둔 것이다.
 *
 * 규칙을 파일마다 베껴 두면 셋이 서로 다르게 낡는다. 실제로 이 리포에서 처음 생긴
 * 검사(`tap-targets.spec.ts`)는 「44px 미만이 없다」만 봤고, 그래서 **잴 것이 0 개인
 * 화면에서도 초록**이었다. 뒤에 생긴 두 스펙은 그 구멍을 막으려고 `total` 을 더했지만
 * 처음 것은 그대로 남아, 같은 이름의 검사가 **서로 다른 것을 재는** 상태가 됐다.
 * 그 분기를 여기서 하나로 합친다 — 분자만이 아니라 **분모를 항상 같이 돌려준다.**
 *
 * 기준 44×44 CSS px 의 출처는 `tap-targets.spec.ts` 머리말에 적어 두었다
 * (WCAG 2.5.5 Enhanced / Apple HIG).
 */

export const MIN_TAP_PX = 44;

export interface TapTarget {
  tag: string;
  /** 사람이 알아볼 이름 — aria-label 이 있으면 그것, 없으면 보이는 글자. */
  name: string;
  w: number;
  h: number;
  cls: string;
}

export interface TapScan {
  /** 화면에 실제로 그려진 대화형 요소 수 — 이 검사의 **분모**. 0 이면 잰 것이 없다. */
  total: number;
  /** 그중 기준 미만인 것. */
  small: TapTarget[];
}

/**
 * 지금 이 화면의 대화형 요소를 전부 재서 돌려준다.
 *
 * ⚠️ 재는 것은 **경계 상자**지 실제 히트 영역이 아니다. `::before` 로 넓힌 타깃,
 *    겹친 요소, `touch-action` 은 여기서 안 보인다.
 */
export async function scanTapTargets(
  page: Page,
  min: number = MIN_TAP_PX,
): Promise<TapScan> {
  return page.evaluate((minPx) => {
    const small: {
      tag: string;
      name: string;
      w: number;
      h: number;
      cls: string;
    }[] = [];
    let total = 0;

    const nodes = document.querySelectorAll<HTMLElement>(
      'a[href], button, [role="button"], input:not([type="hidden"]), select, textarea',
    );
    for (const el of Array.from(nodes)) {
      // 자리지기 사본(CrisisFooter) 은 보이지도 눌리지도 않는다 — 세면 안 된다.
      if (el.closest('[aria-hidden="true"]')) continue;

      // 체크박스·라디오는 브라우저 기본 크기가 13×13 이다. 그런데 라벨이 감싸고 있으면
      // **글자를 눌러도 켜진다** — 즉 손가락이 실제로 닿는 상자는 라벨 쪽이다. 그 경우
      // 라벨 상자로 잰다. 입력을 키우라고 요구하면 화면이 이상해질 뿐 접근성은 안 는다.
      // (감싼 라벨이 없으면 예외 없이 요소 자신을 잰다 — for= 로만 이어진 라벨은
      //  눌러도 켜지지만 그 상자가 어디 있는지 여기서 보장할 수 없어 세지 않는다.)
      const label = el.tagName === "INPUT" ? el.closest("label") : null;
      const r = (label ?? el).getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue; // 안 그려진 것
      total++;
      if (r.width >= minPx && r.height >= minPx) continue;
      small.push({
        tag: el.tagName.toLowerCase(),
        name: (
          el.getAttribute("aria-label") ||
          (el.textContent ?? "").trim() ||
          el.getAttribute("placeholder") ||
          "(이름 없음)"
        ).slice(0, 40),
        w: Math.round(r.width),
        h: Math.round(r.height),
        cls: el.className.toString().slice(0, 60),
      });
    }
    return { total, small };
  }, min);
}

/** 실패 메시지 — 무엇이 몇 px 인지, 그리고 **분모가 얼마였는지** 같이 적는다. */
export function tapReport(
  scan: TapScan,
  where: string,
  min: number = MIN_TAP_PX,
): string {
  return (
    `${where} — 대화형 요소 ${scan.total} 개 중 ${scan.small.length} 개가 ${min}px 미만:\n` +
    scan.small
      .map((s) => `  ${s.tag} "${s.name}" ${s.w}×${s.h} — ${s.cls}`)
      .join("\n")
  );
}
