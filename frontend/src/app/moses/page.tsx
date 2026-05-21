"use client";

import Link from "next/link";

/**
 * Moses 미션 — Phase 2 안내 페이지.
 *
 * Joseph 미션이 자문 (CLINICAL-REVIEW.md / REVIEW-REQUEST-PACKAGE.md) 1차 통과 후
 * 같은 패턴으로 모세 6 Scene 활성화 예정.
 *
 * 현재는 *MVP-MOSES.md §11 의 6 Scene 구조 + 감정 anchor + 진입 모드* 를
 * 사용자에게 *예고* 하는 정적 페이지. 자문 통과 전 demo 한정.
 */
const SCENE_PREVIEW = [
  { id: 1, title: "광야의 침묵", anchor: "자기부정 · 체념" },
  { id: 2, title: "떨기나무 앞에서", anchor: "경외 → 두려움 → 거부" },
  { id: 3, title: "다섯 번의 변명", anchor: "자기방어 → 한 발 양보" },
  { id: 4, title: "파라오 앞에서", anchor: "공포 → 동행 인식" },
  { id: 5, title: "홍해 앞에서", anchor: "책임의 무게 → 신뢰" },
  { id: 6, title: "회복 메시지", anchor: "안식" },
];

export default function MosesPage() {
  return (
    <main className="min-h-screen flex flex-col items-center px-6 py-12">
      <div className="w-full max-w-2xl">
        <header className="mb-10 text-center">
          <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
            Phase 2 — 자문 통과 후 활성
          </p>
          <h1 className="text-3xl font-bold mt-2">Moses — 떨기나무 앞에서</h1>
          <p className="text-sm text-[var(--color-warm)]/70 mt-3 italic max-w-prose mx-auto">
            *두려움이 사라진 후가 아니라 두려운 채로 가는 것이 용기.*
            <br />
            <span className="text-xs not-italic text-[var(--color-warm)]/40">
              — ACT (Acceptance and Commitment Therapy) 기반
            </span>
          </p>
        </header>

        <section className="space-y-3 mb-10">
          <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-2">
            6 Scene 구조 — 6~8 분
          </h2>
          {SCENE_PREVIEW.map((s) => (
            <div
              key={s.id}
              className="px-5 py-3 rounded-lg border border-[var(--color-primary)]/20 flex items-baseline justify-between gap-3"
            >
              <div>
                <p className="text-xs text-[var(--color-warm)]/40">Scene {s.id}</p>
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
            * 설계 — MVP-MOSES.md §11 (영성·감성·이성 3차원 + 정신건강 효과 4 메커니즘) *
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
