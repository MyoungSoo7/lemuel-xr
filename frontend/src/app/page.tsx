"use client";
import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { classifyEmotion, type ClassifyResponse } from "@/lib/api/emotion";
import Link from "next/link";

const EMOTION_LABEL: Record<string, string> = {
  ANXIOUS: "불안",
  SAD: "슬픔",
  ANGRY: "분노",
  CONFUSED: "혼란",
  LONELY: "외로움",
  EXHAUSTED: "지침",
  GRATEFUL: "감사",
};

const SAMPLE_PROMPTS = [
  "오늘 너무 외롭고 지쳐있어",
  "내일이 두려워 잠이 안 와",
  "왜 이렇게 답답할까",
  "감사한 하루였어",
];

/** 분류 결과 없이도 보이는 *직접 진입* 미션 카드. D — 분류 거치지 않고도 진입 가능. */
const DIRECT_MISSIONS: Array<{
  href: string;
  name: string;
  korean: string;
  tagline: string;
  active: boolean;
}> = [
  {
    href: "/joseph",
    name: "Joseph",
    korean: "요셉",
    tagline: "곡식 7년 — 풍년의 결단이 흉년에 돌아온다",
    active: true,
  },
  {
    href: "/moses",
    name: "Moses",
    korean: "모세",
    tagline: "떨기나무 앞에서 — 두려운 채로 가는 용기 (Phase 2)",
    active: true,
  },
  {
    href: "/david",
    name: "David",
    korean: "다윗",
    tagline: "다섯 개의 돌 — 작다는 것은 약함이 아니다 (Phase 2)",
    active: true,
  },
  {
    href: "/jesus",
    name: "Jesus",
    korean: "예수",
    tagline: "길이요 진리요 생명 — 네 구원을 감싸는 정점 (자문 검토 중)",
    active: false,
  },
];

export default function HomePage() {
  const [text, setText] = useState("");
  const [result, setResult] = useState<ClassifyResponse | null>(null);

  const mutation = useMutation({
    mutationFn: (t: string) => classifyEmotion(t),
    onSuccess: (data) => setResult(data),
  });

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (text.trim().length === 0) return;
    mutation.mutate(text);
  };

  return (
    <main className="min-h-screen flex flex-col items-center px-6 py-12">
      <div className="w-full max-w-2xl">
        <header className="mb-12 text-center">
          <h1 className="text-3xl font-bold mb-2">Lemuel XR</h1>
          <p className="text-[var(--color-warm)]/70 text-sm">
            지금 마음에 떠오르는 한 줄을 적어 주세요. 성경 인물의 절망 극복
            서사로 *비상 대비 영적 단련* 을 시작합니다.
          </p>
          <p className="mt-3 text-[var(--color-warm)]/40 text-xs">
            큐티가 일상 영적 양식, 민방위교육이 비상 대비 훈련이라면 — Lemuel XR
            은 *절망 비상* 에 대비하는 영적 단련 프로그램입니다.<br />
            의료·임상 도구가 아닙니다. 위기 신호 시 1577-0199 · 1393 (24시간).
          </p>
        </header>

        <form onSubmit={onSubmit} className="space-y-4">
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="예: 오늘 너무 외롭고 지쳐있어"
            maxLength={1000}
            rows={3}
            className="w-full px-4 py-3 rounded-lg bg-black/30 border border-[var(--color-primary)]/30 placeholder:text-[var(--color-warm)]/30 focus:outline-none focus:border-[var(--color-primary)]"
          />

          <div className="flex flex-wrap gap-2">
            {SAMPLE_PROMPTS.map((p) => (
              <button
                key={p}
                type="button"
                onClick={() => setText(p)}
                className="text-xs px-3 py-1 rounded-full border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
              >
                {p}
              </button>
            ))}
          </div>

          <button
            type="submit"
            disabled={mutation.isPending || text.trim().length === 0}
            className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40 hover:bg-[var(--color-primary)]/90 transition"
          >
            {mutation.isPending ? "분류 중..." : "감정 분석 + 본문 추천"}
          </button>
        </form>

        {mutation.isError && (
          <p className="mt-4 text-red-400 text-sm">
            오류: {(mutation.error as Error).message}
          </p>
        )}

        {/* D — 분류 결과가 없을 때만 노출되는 *직접 진입* 카드 묶음 */}
        {!result && (
          <>
            <section className="mt-12 pt-8 border-t border-[var(--color-primary)]/20">
              <h2 className="text-xs uppercase tracking-wider text-[var(--color-warm)]/40 mb-4 text-center">
                매일의 단련 — AR 7가지 가치
              </h2>
              <div className="space-y-3">
                <Link
                  href="/values"
                  className="block px-5 py-4 rounded-lg border border-amber-500/30 bg-amber-500/5 hover:bg-amber-500/10 transition"
                >
                  <p className="font-semibold">
                    자기만의 7 가치 빌더
                    <span className="ml-2 text-xs text-amber-500/80 uppercase tracking-wider">
                      Practice
                    </span>
                  </p>
                  <p className="text-xs text-[var(--color-warm)]/60 mt-1">
                    매일 5분 — 가치를 정의하고, 실천을 기록하고, 습관으로 새깁니다.
                  </p>
                </Link>
                <Link
                  href="/topics"
                  className="block px-5 py-4 rounded-lg border border-[var(--color-primary)]/30 bg-[var(--color-primary)]/5 hover:bg-[var(--color-primary)]/10 transition"
                >
                  <p className="font-semibold">
                    일상 영적 양식 — 7 주제 카드
                    <span className="ml-2 text-xs text-[var(--color-primary)]/80 uppercase tracking-wider">
                      Read
                    </span>
                  </p>
                  <p className="text-xs text-[var(--color-warm)]/60 mt-1">
                    일기 · 잠언 · 전도서 · 시편 · 고통(욥) · 마음지키기 · 사람두려워하지않기 — 매일 한 카드.
                  </p>
                </Link>
              </div>
            </section>

            <section className="mt-8 pt-8 border-t border-[var(--color-primary)]/20">
              <h2 className="text-xs uppercase tracking-wider text-[var(--color-warm)]/40 mb-4 text-center">
                각성의 순간 — VR 인물 미션
              </h2>
              <div className="space-y-3">
                {DIRECT_MISSIONS.map((m) => (
                  <Link
                    key={m.href}
                    href={m.href}
                    className="block px-5 py-4 rounded-lg border border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/60 transition"
                  >
                    <p className="font-semibold">
                      {m.name}
                      <span className="ml-2 text-sm text-[var(--color-warm)]/60">
                        {m.korean}
                      </span>
                    </p>
                    <p className="text-xs text-[var(--color-warm)]/60 mt-1">{m.tagline}</p>
                  </Link>
                ))}
              </div>
            </section>
          </>
        )}

        {result && (
          <section className="mt-10 space-y-6">
            <div className="rounded-lg border border-[var(--color-primary)]/30 px-5 py-4">
              <p className="text-xs text-[var(--color-warm)]/60 mb-1">분류 결과</p>
              <p className="text-2xl font-bold">
                {EMOTION_LABEL[result.primary.emotion] ?? result.primary.emotion}
                <span className="ml-2 text-sm text-[var(--color-warm)]/60">
                  ({Math.round(result.primary.confidence * 100)}% 신뢰도)
                </span>
              </p>
            </div>

            {(result.recommendations.trackB?.length ?? 0) > 0 && (
              <div>
                <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-3">
                  Track B — 인물 서사
                </h2>
                <div className="grid grid-cols-2 gap-3">
                  {result.recommendations.trackB.map((c) => {
                    const ACTIVE = new Set(["joseph", "moses", "david"]);
                    const href = ACTIVE.has(c.character) ? `/${c.character}` : "#";
                    const phase2 = c.character !== "joseph";
                    return (
                      <Link
                        key={c.character}
                        href={href}
                        className="block px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition"
                      >
                        <p className="font-semibold">
                          {c.character}
                          {phase2 && (
                            <span className="ml-2 text-[10px] text-[var(--color-warm)]/40 uppercase">
                              Phase 2
                            </span>
                          )}
                        </p>
                        {c.rationale && (
                          <p className="text-xs text-[var(--color-warm)]/60 mt-1">
                            {c.rationale}
                          </p>
                        )}
                      </Link>
                    );
                  })}
                </div>
              </div>
            )}

            {(result.recommendations.trackA?.length ?? 0) > 0 && (
              <div>
                <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-3">
                  Track A — 정적 회복 콘텐츠
                </h2>
                <ul className="space-y-2">
                  {result.recommendations.trackA.map((t) => (
                    <li
                      key={t.topicId}
                      className="px-4 py-3 rounded-lg border border-[var(--color-primary)]/20"
                    >
                      <p className="font-medium">{t.title}</p>
                      {t.rationale && (
                        <p className="text-xs text-[var(--color-warm)]/60 mt-1">
                          {t.rationale}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            )}

            <div className="pt-4 border-t border-[var(--color-primary)]/20">
              <Link
                href="/joseph"
                className="inline-block px-5 py-2 rounded-lg bg-[var(--color-primary)] text-black font-semibold hover:bg-[var(--color-primary)]/90"
              >
                요셉 미션 바로 시작 →
              </Link>
            </div>
          </section>
        )}
      </div>
    </main>
  );
}
