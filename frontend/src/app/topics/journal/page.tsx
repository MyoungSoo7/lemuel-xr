"use client";

import { useState } from "react";
import Link from "next/link";
import { CRISIS_LINE_FULL, CRISIS_LINE_SHORT } from "@/lib/crisis-resources";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  fetchJournalGuidance,
  requestJournalGuidance,
  type Guidance,
  type GuidanceResponse,
} from "@/lib/api/content";
import { NarrationAudioButton } from "@/components/NarrationAudioButton";

/**
 * /topics/journal — 기준1 (일기와 묵상) 성경 기반 조언.
 *
 * TRACK-A-1-4-WISDOM-EMOTION §2 (Theme 1). /topics/ecclesiastes · /topics/practice 스타일 재사용.
 * 감정을 고르거나 일기를 적으면 *성경 구절 + 성찰 질문* 을 돌려준다. LLM 없이 규칙형 카탈로그.
 *
 * 안전선: 근거는 성경만. R2/R3 — "이 감정도 성경 안에 있다" 인증, 정죄·압박 배제.
 * R1 — 일기 텍스트 위기 스캔 → crisis routing → 위기 카드 + 일기(#1) 라우팅.
 * "AI 보조 — 본문은 성경 참조" footer.
 */
export default function JournalGuidancePage() {
  // 전체 감정 선택지 (카탈로그).
  const { data: catalog } = useQuery({
    queryKey: ["journal-guidance-catalog"],
    queryFn: () => fetchJournalGuidance(),
  });

  const [selectedEmotion, setSelectedEmotion] = useState<string | null>(null);
  const [text, setText] = useState("");
  const [result, setResult] = useState<GuidanceResponse | null>(null);

  const emotions = catalog?.catalog ?? [];

  const byEmotion = useMutation({
    mutationFn: (emotion: string) => fetchJournalGuidance(emotion),
    onSuccess: (res) => setResult(res),
  });

  const fromText = useMutation({
    mutationFn: () =>
      requestJournalGuidance({
        text: text.trim() || undefined,
        emotion: selectedEmotion ?? undefined,
      }),
    onSuccess: (res) => setResult(res),
  });

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-6">
        <div className="flex items-baseline justify-between">
          <h1 className="text-2xl font-bold">일기와 조언</h1>
          <Link
            href="/topics"
            className="inline-flex items-center min-h-11 min-w-11 pr-3 text-xs text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
          >
            ← 주제
          </Link>
        </div>
        <p className="text-xs text-[var(--color-warm)]/40 mt-2">
          기준1 — 오늘의 감정을 성경 앞에 그대로 두는 자리. 감정을 고르거나, 일기를 적으면 *성경 구절과
          성찰 질문* 을 드립니다.
        </p>
      </header>

      <div className="max-w-3xl mx-auto w-full">
        {/* 감정 선택 */}
        <section className="mb-6">
          <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-3">
            지금 마음에 가까운 감정
          </h2>
          <div className="flex flex-wrap gap-1.5">
            {emotions.map((g) => (
              <button
                key={g.emotion}
                onClick={() => {
                  setSelectedEmotion(g.emotion);
                  byEmotion.mutate(g.emotion);
                }}
                className={`text-sm px-3 py-1.5 rounded-full border transition ${
                  selectedEmotion === g.emotion
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]"
                    : "border-[var(--color-primary)]/20 text-[var(--color-warm)]/70 hover:border-[var(--color-primary)]/50"
                }`}
              >
                {g.emotionLabel}
              </button>
            ))}
          </div>
        </section>

        {/* 일기 입력 → 조언 (R1 위기 스캔) */}
        <section className="mb-6 rounded-lg border border-[var(--color-primary)]/20 p-4">
          <label className="text-xs text-[var(--color-warm)]/60">
            오늘 있었던 일 / 지금 마음을 그대로 적어보세요 (선택)
          </label>
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            placeholder="누가 볼까 다듬지 않으셔도 됩니다. 있는 그대로의 문장이면 충분합니다."
            rows={4}
            maxLength={5000}
            className="w-full mt-2 px-3 py-2 rounded-md bg-transparent border border-[var(--color-primary)]/20 text-sm text-[var(--color-warm)]/90 leading-relaxed focus:outline-none focus:border-[var(--color-primary)]/60"
          />
          <div className="flex items-center gap-3 mt-3">
            <button
              onClick={() => fromText.mutate()}
              disabled={fromText.isPending || (!text.trim() && !selectedEmotion)}
              className="inline-flex items-center justify-center min-h-11 px-4 py-2 rounded-md text-sm bg-[var(--color-primary)]/15 border border-[var(--color-primary)]/40 hover:bg-[var(--color-primary)]/25 disabled:opacity-40 transition"
            >
              {fromText.isPending ? "조언 받는 중..." : "성경 조언 받기"}
            </button>
            {(fromText.isError || byEmotion.isError) && (
              <span className="text-xs text-red-400">불러오지 못했습니다.</span>
            )}
          </div>
        </section>

        {/* R1 — 위기 라우팅 응답 (조언보다 먼저) */}
        {result?.crisis.routed && (
          <div className="mb-6 rounded-md border border-red-400/40 bg-red-400/5 p-4">
            <p className="text-sm text-red-300 font-semibold">
              잠시 멈추고, 함께 안전을 살펴봐요.
            </p>
            <p className="text-xs text-[var(--color-warm)]/70 mt-1 leading-relaxed">
              지금 마음이 많이 힘드시다면 혼자 두지 않겠습니다. 아래 자원은 24시간 열려 있어요.
            </p>
            <p className="text-xs text-red-300 mt-2 font-mono">{CRISIS_LINE_SHORT}</p>
            <Link
              href="/topics/journal"
              className="inline-block mt-2 text-xs text-[var(--color-primary)] hover:underline"
            >
              일기(#1)로 마음을 먼저 적어보기 →
            </Link>
          </div>
        )}

        {/* 조언 표시 */}
        {result?.guidance && !result.crisis.routed && (
          <GuidanceView guidance={result.guidance} safetyFooter={result.safetyFooter} />
        )}

        {/* 하단 영구 위기 자원 + AI footer */}
        <p className="text-[10px] text-[var(--color-warm)]/30 mt-8 leading-relaxed">
          {CRISIS_LINE_FULL}
          <br />
          {catalog?.aiFooter ??
            "AI 보조 — 본문은 성경 참조. 성경 외 자료는 조언 근거로 쓰지 않습니다."}
        </p>
      </div>
    </main>
  );
}

/**
 * 조언 화면을 음성으로 읽어줄 스크립트를 만든다 — 인증 문구 + 성경 구절 + 성찰 질문.
 * 고령자 대상: 화면을 눈으로 좇지 않아도 조언 전체를 귀로 들을 수 있게 한다.
 */
function buildGuidanceNarration(guidance: Guidance): string {
  const parts: string[] = [guidance.validation];
  for (const v of guidance.verses) {
    parts.push(`${v.ref}. ${v.text}`);
  }
  if (guidance.reflectionQuestions.length > 0) {
    parts.push("성찰 질문입니다.");
    parts.push(...guidance.reflectionQuestions);
  }
  return parts.join("\n");
}

function GuidanceView({
  guidance,
  safetyFooter,
}: {
  guidance: Guidance;
  safetyFooter: string;
}) {
  return (
    <section className="mb-6 rounded-lg border border-[var(--color-primary)]/20 p-4">
      <p className="text-xs text-[var(--color-primary)]">감정 · {guidance.emotionLabel}</p>

      {/* R2/R3 — 인증 문구 (정죄·압박 배제) */}
      <p className="text-sm text-[var(--color-warm)]/80 mt-2 leading-relaxed">
        {guidance.validation}
      </p>
      <div className="mt-2">
        <NarrationAudioButton
          text={buildGuidanceNarration(guidance)}
          label="듣기"
          onUnavailable="hide"
        />
      </div>

      {/* 성경 구절 (성경만 근거) */}
      <div className="mt-4 space-y-3">
        {guidance.verses.map((v) => (
          <div key={v.ref}>
            <p className="text-xs text-[var(--color-primary)] font-mono">📖 {v.ref}</p>
            <blockquote className="text-sm text-[var(--color-warm)]/90 italic leading-relaxed border-l-2 border-[var(--color-primary)]/40 pl-3 mt-1">
              {v.text}
            </blockquote>
          </div>
        ))}
      </div>

      {/* 성찰 질문 (답을 강요하지 않고 여는 형태) */}
      <div className="mt-5">
        <p className="text-xs uppercase tracking-wider text-[var(--color-warm)]/60 mb-2">
          성찰 질문
        </p>
        <ul className="space-y-2">
          {guidance.reflectionQuestions.map((q, i) => (
            <li
              key={i}
              className="text-sm text-[var(--color-warm)]/80 leading-relaxed flex gap-2"
            >
              <span className="text-[var(--color-primary)]/60">·</span>
              <span>{q}</span>
            </li>
          ))}
        </ul>
      </div>

      {/* R2 safetyFooter */}
      <p className="mt-5 text-[11px] text-[var(--color-warm)]/40 leading-relaxed">
        {safetyFooter}
      </p>
    </section>
  );
}
