"use client";

import { useState } from "react";

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
 */
export function CrisisFooter() {
  const [expanded, setExpanded] = useState(false);

  return (
    <div
      className="fixed bottom-0 left-0 right-0 z-50 bg-black/85 backdrop-blur-sm border-t border-amber-500/30 text-white"
      role="region"
      aria-label="위기 상담 자원 안내"
    >
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        className="w-full px-4 py-2 flex items-center justify-center gap-2 text-xs sm:text-sm hover:bg-white/5 transition"
        aria-expanded={expanded}
      >
        <span className="text-amber-400">●</span>
        <span className="font-medium">
          위기 상태라면 — <a href="tel:1393" className="underline font-bold">1393</a>{" "}
          자살예방상담전화 (24시간, 무료)
        </span>
        <span className="text-amber-400/60 text-xs">
          {expanded ? "▲" : "▼"}
        </span>
      </button>

      {expanded && (
        <div className="px-4 pb-3 pt-1 text-xs sm:text-sm text-white/80 max-w-2xl mx-auto space-y-2">
          <p>
            지금 자해·자살에 대한 생각이 드시면 *혼자 견디지 마세요*. 즉시 도움을
            받을 수 있는 곳이 있습니다:
          </p>
          <ul className="space-y-1 pl-4">
            <li>
              <a href="tel:1393" className="underline">1393</a> — 자살예방상담전화 (24시간 무료, 전화)
            </li>
            <li>
              <a href="tel:1577-0199" className="underline">1577-0199</a> — 정신건강위기상담전화 (24시간 무료)
            </li>
            <li>
              <a href="tel:129" className="underline">129</a> — 보건복지상담센터 (24시간 무료)
            </li>
            <li>
              <a href="https://www.lifeline.or.kr" target="_blank" rel="noreferrer" className="underline">
                lifeline.or.kr
              </a>{" "}
              — 한국생명의전화 (온라인 채팅 상담)
            </li>
          </ul>
          <p className="text-white/60">
            본 서비스는 *영적 단련 교육 콘텐츠* 이며 의료·임상 도구가 아닙니다.
            진단·치료가 필요한 상태라면 위 자원을 우선 이용해 주세요.
          </p>
        </div>
      )}
    </div>
  );
}
