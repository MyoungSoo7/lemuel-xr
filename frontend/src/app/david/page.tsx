"use client";

import Link from "next/link";

/**
 * David 미션 — Phase 2 안내 페이지.
 *
 * Joseph 미션이 자문 (CLINICAL-REVIEW.md / REVIEW-REQUEST-PACKAGE.md) 1차 통과 후
 * 같은 패턴으로 다윗 6 Scene 활성화 예정.
 *
 * 현재는 *MVP-DAVID.md §12 의 6 Scene 구조 + 감정 anchor + 진입 모드* 를
 * 사용자에게 *예고* 하는 정적 페이지. 자문 통과 전 demo 한정.
 */
const SCENE_PREVIEW = [
  { id: 1, title: "양 떼와 시편", anchor: "평온 · 자기 존재감" },
  { id: 2, title: "형들의 비웃음", anchor: "모욕감 · 내적 분노" },
  { id: 3, title: "사울의 갑옷", anchor: "자기 정체성 확립 · 해방", core: true },
  { id: 4, title: "시냇가의 5돌", anchor: "두려움 + 신뢰의 혼합", core: true },
  { id: 5, title: "골리앗", anchor: "떨림 → 일어섬" },
  { id: 6, title: "회복 메시지", anchor: "안식 + 다음 거인 대비" },
];

export default function DavidPage() {
  return (
    <main className="min-h-screen flex flex-col items-center px-6 py-12">
      <div className="w-full max-w-2xl">
        <header className="mb-10 text-center">
          <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
            Phase 2 — 자문 통과 후 활성
          </p>
          <h1 className="text-3xl font-bold mt-2">David — 다섯 개의 돌</h1>
          <p className="text-sm text-[var(--color-warm)]/70 mt-3 italic max-w-prose mx-auto">
            *작다는 것은 약함이 아니다. 누구의 옷을 입지 않은 자유다.*
            <br />
            <span className="text-xs not-italic text-[var(--color-warm)]/40">
              — Identity Formation (Erikson · Marcia) + Affect Labeling (Lieberman · ACT) 기반
            </span>
          </p>
        </header>

        <section className="space-y-3 mb-10">
          <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-2">
            6 Scene 구조 — 5~7 분
          </h2>
          {SCENE_PREVIEW.map((s) => (
            <div
              key={s.id}
              className={`px-5 py-3 rounded-lg border flex items-baseline justify-between gap-3 ${
                s.core
                  ? "border-[var(--color-primary)]/60 bg-[var(--color-primary)]/5"
                  : "border-[var(--color-primary)]/20"
              }`}
            >
              <div>
                <p className="text-xs text-[var(--color-warm)]/40">
                  Scene {s.id}
                  {s.core && (
                    <span className="ml-1 text-[var(--color-primary)]">★ 핵심</span>
                  )}
                </p>
                <p className="font-semibold">{s.title}</p>
              </div>
              <p className="text-xs text-[var(--color-warm)]/60 italic text-right shrink-0">
                {s.anchor}
              </p>
            </div>
          ))}
        </section>

        <section className="rounded-lg border border-[var(--color-primary)]/30 bg-black/30 px-5 py-4 mb-8">
          <p className="text-sm text-[var(--color-warm)]/80">
            본 미션은 신학·임상 자문 검토 후 활성됩니다. Joseph 미션을 먼저
            체험해 보세요.
          </p>
          <p className="text-[10px] text-[var(--color-warm)]/40 mt-2">
            * 설계 — MVP-DAVID.md §12 (영성·감성·이성 3차원 + 정신건강 효과 5 메커니즘 +
            시편 23편 임상 효과) *
          </p>
        </section>

        <div className="flex gap-3">
          <Link
            href="/"
            className="flex-1 text-center px-5 py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)]"
          >
            ← 홈
          </Link>
          <Link
            href="/joseph"
            className="flex-1 text-center px-5 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold hover:bg-[var(--color-primary)]/90"
          >
            Joseph 미션 →
          </Link>
        </div>
      </div>
    </main>
  );
}
