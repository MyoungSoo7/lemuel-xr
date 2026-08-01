"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  fetchProverbsThemes,
  recordProverbsInteraction,
  type ProverbsTheme,
} from "@/lib/api/content";
import { NarrationAudioButton } from "@/components/NarrationAudioButton";

/**
 * /topics/proverbs — 기준2 (잠언과 지혜) 주제별 조회.
 *
 * TRACK-A-1-4-WISDOM-EMOTION §3 (Theme 2). /topics/ecclesiastes 스타일 재사용.
 * 잠언 지혜를 *주제(카테고리)로 분류* 해 보여주고, 사용자가 고른 구절을 기록한다.
 *
 * 안전선: 근거는 잠언(성경)만. R2/R3 — 잠언 단순화 위험(§3.4)을 피해 "결과 보장이 아니라 방향"
 * 안내 동반. 정죄·압박 배제. "AI 보조 — 본문은 성경 참조" footer.
 */
export default function ProverbsThemePage() {
  const { data } = useQuery({
    queryKey: ["proverbs-themes"],
    queryFn: fetchProverbsThemes,
  });

  const [selected, setSelected] = useState<ProverbsTheme | null>(null);

  const themes = data?.themes ?? [];

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-6">
        <div className="flex items-baseline justify-between">
          <h1 className="text-2xl font-bold">잠언과 지혜</h1>
          <Link
            href="/topics"
            className="text-xs text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
          >
            ← 주제
          </Link>
        </div>
        <p className="text-xs text-[var(--color-warm)]/40 mt-2">
          기준2 — 잠언의 지혜를 *주제로 나눠* 보는 자리. 지혜는 공식이 아니라 오늘 한 걸음의 방향입니다.
        </p>
      </header>

      <div className="max-w-3xl mx-auto w-full">
        {/* 주제 분류 */}
        <section className="mb-6">
          <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-3">
            잠언 주제
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {themes.map((t) => (
              <button
                key={t.key}
                onClick={() => setSelected(t)}
                className={`text-left px-4 py-3 rounded-lg border transition ${
                  selected?.key === t.key
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/5"
                    : "border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/60"
                }`}
              >
                <p className="font-semibold text-sm">{t.title}</p>
                <p className="text-xs text-[var(--color-warm)]/50 mt-1 leading-relaxed">
                  {t.summary}
                </p>
              </button>
            ))}
          </div>
        </section>

        {selected ? (
          <ThemeVerses theme={selected} />
        ) : (
          <p className="text-[var(--color-warm)]/40 text-sm italic mb-6">
            위 주제에서 오늘 마음에 닿는 하나를 골라보세요.
          </p>
        )}

        {/* 하단 영구 위기 자원 + AI footer */}
        <p className="text-[10px] text-[var(--color-warm)]/30 mt-8 leading-relaxed">
          위기 시: 109 (자살예방 상담전화) · 1577-0199 (정신건강위기상담) · 24시간
          <br />
          {data?.aiFooter ??
            "AI 보조 — 본문은 성경 참조. 잠언(성경) 외 자료는 사용하지 않습니다."}
        </p>
      </div>
    </main>
  );
}

function ThemeVerses({ theme }: { theme: ProverbsTheme }) {
  const [chosenRef, setChosenRef] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const mutation = useMutation({
    mutationFn: (ref: string) =>
      recordProverbsInteraction({
        theme: theme.key,
        chosenProverbRef: ref,
        dimension: "spiritual",
      }),
    onSuccess: () => setSaved(true),
  });

  return (
    <section className="mb-6 rounded-lg border border-[var(--color-primary)]/20 p-4">
      <h3 className="font-semibold text-[var(--color-warm)]">{theme.title}</h3>

      {/* R2/R3 — 단순화 방지 안내 */}
      <p className="text-xs text-[var(--color-warm)]/60 mt-2 leading-relaxed">
        {theme.guidance}
      </p>
      <div className="mt-2">
        <NarrationAudioButton
          text={[
            theme.summary,
            theme.guidance,
            ...theme.verses.map((v) => `${v.ref}. ${v.text}`),
          ].join("\n")}
          label="듣기"
          onUnavailable="hide"
        />
      </div>

      {/* 잠언 구절 리스트 (잠언만 근거) — 클릭 시 '고른 구절' 기록 */}
      <div className="mt-4 space-y-3">
        {theme.verses.map((v) => (
          <button
            key={v.ref}
            onClick={() => {
              setChosenRef(v.ref);
              setSaved(false);
              mutation.mutate(v.ref);
            }}
            className={`w-full text-left rounded-md border p-3 transition ${
              chosenRef === v.ref
                ? "border-[var(--color-primary)] bg-[var(--color-primary)]/5"
                : "border-[var(--color-primary)]/15 hover:border-[var(--color-primary)]/50"
            }`}
          >
            <p className="text-xs text-[var(--color-primary)] font-mono">📖 {v.ref}</p>
            <blockquote className="text-sm text-[var(--color-warm)]/90 italic leading-relaxed border-l-2 border-[var(--color-primary)]/40 pl-3 mt-1">
              {v.text}
            </blockquote>
          </button>
        ))}
      </div>

      {saved && (
        <p className="mt-4 text-[11px] text-[var(--color-warm)]/40 leading-relaxed">
          ✓ 오늘 고른 구절로 기록했습니다. 답을 다 지켜야 한다는 압박 없이, 이 한 구절만 붙잡으셔도 충분합니다.
        </p>
      )}
      {mutation.isError && (
        <p className="mt-3 text-xs text-red-400">기록에 실패했습니다.</p>
      )}
    </section>
  );
}
