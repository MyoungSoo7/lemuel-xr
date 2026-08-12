"use client";

import { useState } from "react";
import {
  CRISIS_DEFAULT,
  CRISIS_LIFELINE,
  CRISIS_RESOURCES,
  telHref,
} from "@/lib/crisis-resources";

/**
 * 모든 사용자 facing 화면에 영구 노출되는 위기자원 footer.
 * docs/safety-guidelines.md §1 의 핵심 룰 — *어떤 페이지든 사라지지 않는다*.
 *
 * 디자인 원칙:
 *   - 닫기 버튼 없음 — *영구 노출*. 사용자가 위기 상태로 우연히 접속해도
 *     이 한 줄이 항상 시야에 있어야 한다.
 *   - 펼치기 / 접기 만 제공 — 콘텐츠 몰입을 위해 압축 모드 옵션은 두되,
 *     완전 비표시는 불가능.
 *   - fixed bottom — 모든 페이지 위에 떠있음.
 *   - 모바일·데스크톱·VR 브라우저 모두 가시.
 *
 * 번호는 `@/lib/crisis-resources` 가 정본이다. 이 파일에 숫자를 다시 적지 말 것 —
 * 원래 여기 있던 번호 정본 주석도 그쪽으로 옮겼다. 프론트 전체에서 번호 리터럴이
 * 있어도 되는 파일은 그 하나뿐이고 `scripts/check_frontend_hotline.py` 가 강제한다.
 */
/*
  `SHELL` — 높이에 기여하는 클래스만 모았다. 아래 두 벌(자리지기 사본 / 실제 푸터)이
  **같은 높이**여야 하므로, 테두리·안전영역 여백처럼 높이를 바꾸는 것은 반드시 여기에
  둔다. 한쪽에만 붙이면 그 차이만큼 본문이 다시 깔린다.

  `pb-[env(safe-area-inset-bottom)]` — standalone(홈화면 설치)으로 띄우면 브라우저 하단
  바가 사라지고 iOS 홈 인디케이터가 이 푸터 위에 겹친다. 상담 번호가 그 막대 밑에 깔리는
  것이라 미관 문제가 아니다. `layout.tsx` 의 viewportFit:"cover" 가 없으면 이 env() 는
  0 을 주고 여기는 조용히 무효가 된다 — 한 벌로 봐야 한다.
*/
const SHELL = "border-t border-amber-500/30 pb-[env(safe-area-inset-bottom)]";

export function CrisisFooter() {
  const [expanded, setExpanded] = useState(false);

  const panel = (
    <>
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        /*
          `min-h-11` = 44px. 손가락으로 누르는 유일한 상시 컨트롤이고, 그 뒤에 있는 것이
          상담 번호다. WCAG 2.5.5 / Apple HIG 의 최소 타깃이 44px 다 — 이 버튼만은
          그 밑으로 내려가지 않게 바닥을 박는다.
        */
        className="w-full min-h-11 px-4 py-2 flex items-center justify-center gap-2 text-xs sm:text-sm hover:bg-white/5 transition"
        aria-expanded={expanded}
      >
        <span className="text-amber-400">●</span>
        <span className="font-medium">
          위기 상태라면 —{" "}
          <a href={telHref(CRISIS_DEFAULT)} className="underline font-bold">
            {CRISIS_DEFAULT.tel}
          </a>{" "}
          {/*
            접힌 줄의 이용 조건은 펼친 목록과 문안이 다르다("24시간, 무료" vs
            "24시간 무료, 전화"). 통합하면서 note 로 갈아끼우지 않는다 — 숫자만
            정본에서 읽고, 검토를 거친 문장은 있던 그대로 둔다.
          */}
          {CRISIS_DEFAULT.label} (24시간, 무료)
        </span>
        <span className="text-amber-400/60 text-xs">
          {expanded ? "▲" : "▼"}
        </span>
      </button>

      {expanded && (
        /*
          `max-h-[60svh] overflow-y-auto` — 펼친 목록은 205px 다. 세로 568px 짜리
          기기를 가로로 눕히면 화면 높이가 320px 가 되고, 그 상태에서 이 블록은
          화면의 3분의 2를 먹는다. 잘라 내는 대신 이 블록 안에서만 스크롤시킨다 —
          자원 목록은 한 줄도 사라지면 안 되는 것이라서 (safety-guidelines §1).
          `svh` 를 쓰는 이유는 모바일 주소창이 접혔다 펴질 때 `vh` 가 튀기 때문이다.
        */
        <div className="px-4 pb-3 pt-1 text-xs sm:text-sm text-white/80 max-w-2xl mx-auto space-y-2 max-h-[60svh] overflow-y-auto">
          <p>
            지금 자해·자살에 대한 생각이 드시면 *혼자 견디지 마세요*. 즉시
            도움을 받을 수 있는 곳이 있습니다:
          </p>
          {/*
            목록을 손으로 세 번 쓰지 않는다. 자원이 추가·삭제·교체될 때 여기서
            한 줄만 빠뜨려도 그 자원은 조용히 사라진다 — 화면은 멀쩡해 보인다.
          */}
          <ul className="space-y-1 pl-4">
            {CRISIS_RESOURCES.map((r) => (
              <li key={r.tel}>
                <a href={telHref(r)} className="underline">
                  {r.tel}
                </a>{" "}
                — {r.label} ({r.note})
              </li>
            ))}
            <li>
              <a
                href={CRISIS_LIFELINE.url}
                target="_blank"
                rel="noreferrer"
                className="underline"
              >
                {CRISIS_LIFELINE.url.replace(/^https:\/\/www\./, "")}
              </a>{" "}
              — {CRISIS_LIFELINE.label} ({CRISIS_LIFELINE.note})
            </li>
          </ul>
          <p className="text-white/60">
            본 서비스는 *영적 단련 교육 콘텐츠* 이며 의료·임상 도구가 아닙니다.
            진단·치료가 필요한 상태라면 위 자원을 우선 이용해 주세요.
          </p>
        </div>
      )}
    </>
  );

  /*
    두 벌을 렌더한다. 왜 이렇게까지 하는가 —

    이 푸터는 `fixed` 라 문서 흐름에서 빠져 있고, 그래서 **자기가 덮는 만큼의 자리를
    본문에 남겨 주지 않는다.** 예전에는 `layout.tsx` 에서 `pb-12`(48px) 로 그 자리를
    손으로 짐작했고, 펼치면 205px 라 본문 하단 157px 이 깔렸다.

    그 다음 시도는 JS 로 실제 높이를 재서 CSS 변수로 알리는 것이었다. 그것도 틀렸다 —
    **효과는 hydration 뒤에야 돈다.** 첫 페인트에서는 fallback 값이 쓰이고, iPhone SE
    폭(320px)에서는 접힌 줄이 두 줄로 접혀 49px 가 되어 fallback 48px 를 1px 넘겼다
    (2026-08-12 실측: hydration 전 padding 48px / 푸터 49px, 3초 뒤 49px/49px).
    「잠깐만 가려진다」는 이 앱에서 받아들일 수 있는 결함이 아니다 — 가려지는 것이
    상담 번호로 가는 길이고, 첫 화면이 제일 위험한 순간이다.

    그래서 자리는 **CSS 로, 서버 렌더 시점부터** 잡는다. 아래 사본은 같은 내용을 그대로
    그리되 `invisible`(visibility:hidden) 이라 보이지도 눌리지도 초점을 받지도 않고,
    오직 **높이만** 차지한다. 접힘/펼침·화면 폭·글꼴 로드·회전 어느 쪽으로 높이가 변해도
    사본이 같이 변하므로 숫자를 맞출 일이 없다. `aria-hidden` 으로 접근성 트리에서도
    빼서 스크린 리더가 상담 자원을 두 번 읽지 않게 한다.

    내용을 두 번 적지 않는다는 점이 중요하다 — `panel` 하나를 두 곳에 꽂는다. 손으로
    복제하면 언젠가 한쪽에서 자원 한 줄이 조용히 빠진다(safety-guidelines §1).
  */
  return (
    <>
      <div aria-hidden="true" className={`invisible ${SHELL}`}>
        {panel}
      </div>
      <div
        className={`fixed bottom-0 left-0 right-0 z-50 bg-black/85 backdrop-blur-sm text-white ${SHELL}`}
        role="region"
        aria-label="위기 상담 자원 안내"
      >
        {panel}
      </div>
    </>
  );
}
