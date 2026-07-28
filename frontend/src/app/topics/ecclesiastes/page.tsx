"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchEcclesiastesCategories,
  recordEcclesiastesView,
  fetchEcclesiastesViews,
  type EcclesiastesCategory,
  type EcclesiastesResponse,
} from "@/lib/api/content";

/**
 * /topics/ecclesiastes — 기준4 (전도서와 인생).
 *
 * TRACK-A-1-4-WISDOM-EMOTION §4 (Theme 3). /topics/practice 스타일 재사용.
 * 인생에 관한 전도서 진리를 *카테고리로 분류* 해 보여주고, 사용자가 자신의 '인생 계절'에 맞춰
 * 헛됨(futility)과 의미(meaning)를 성찰·기록.
 *
 * 안전선: 근거는 성경만. R2 — 헛됨을 절망이 아니라 "유한함의 정직한 인정 → 창조주 경외(전 12:13)"로
 * 마무리하는 톤, R3 압박 배제. R1 — crisis routing 응답 → 위기 카드. "AI 보조 — 본문은 성경 참조" footer.
 */
export default function EcclesiastesPage() {
  const { data } = useQuery({
    queryKey: ["ecclesiastes-categories"],
    queryFn: fetchEcclesiastesCategories,
  });

  const [selected, setSelected] = useState<EcclesiastesCategory | null>(null);

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-6">
        <div className="flex items-baseline justify-between">
          <h1 className="text-2xl font-bold">전도서와 인생</h1>
          <Link
            href="/topics"
            className="text-xs text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
          >
            ← 주제
          </Link>
        </div>
        <p className="text-xs text-[var(--color-warm)]/40 mt-2">
          기준4 — *해 아래 유한함* 을 정직하게 인정하는 자리. 전 3:1 &ldquo;범사에 기한이 있다&rdquo;.
        </p>
      </header>

      <div className="max-w-3xl mx-auto w-full">
        {/* 인생 진리 — 카테고리 분류 */}
        <section className="mb-6">
          <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-3">
            인생에 관한 전도서의 진리
          </h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {(data?.categories ?? []).map((c) => (
              <button
                key={c.key}
                onClick={() => setSelected(c)}
                className={`text-left px-4 py-3 rounded-lg border transition ${
                  selected?.key === c.key
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/5"
                    : "border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/60"
                }`}
              >
                <p className="text-xs text-[var(--color-primary)] font-mono">📖 {c.chapterRef}</p>
                <p className="font-semibold mt-1 text-sm">{c.title}</p>
              </button>
            ))}
          </div>
        </section>

        {selected ? (
          <ReflectionSection
            category={selected}
            seasons={data?.seasons ?? []}
          />
        ) : (
          <p className="text-[var(--color-warm)]/40 text-sm italic mb-6">
            위 카테고리에서 오늘 마음에 닿는 진리를 하나 골라보세요.
          </p>
        )}

        <History />

        {/* 하단 영구 위기 자원 + AI footer */}
        <p className="text-[10px] text-[var(--color-warm)]/30 mt-8 leading-relaxed">
          위기 시: 1393 (자살예방상담전화) · 1577-0199 (정신건강위기상담) · 24시간
          <br />
          {data?.aiFooter ??
            "AI 보조 — 본문은 성경 참조. 전도서와 관련 성구 외 자료는 사용하지 않습니다."}
        </p>
      </div>
    </main>
  );
}

function ReflectionSection({
  category,
  seasons,
}: {
  category: EcclesiastesCategory;
  seasons: { key: string; label: string; verse: string }[];
}) {
  const qc = useQueryClient();
  const [season, setSeason] = useState<string>("");
  const [futility, setFutility] = useState("");
  const [meaning, setMeaning] = useState("");
  const [conclusionViewed, setConclusionViewed] = useState(false);
  const [result, setResult] = useState<EcclesiastesResponse | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      recordEcclesiastesView({
        chapterRef: category.chapterRef,
        userSeason: season || undefined,
        futilityNote: futility.trim() || undefined,
        meaningNote: meaning.trim() || undefined,
        conclusionViewed,
      }),
    onSuccess: (res) => {
      setResult(res);
      setFutility("");
      setMeaning("");
      setSeason("");
      setConclusionViewed(false);
      qc.invalidateQueries({ queryKey: ["ecclesiastes-views"] });
    },
  });

  return (
    <section className="mb-6 rounded-lg border border-[var(--color-primary)]/20 p-4">
      {/* 성경 본문 (성경만 근거) */}
      <p className="text-xs text-[var(--color-primary)] font-mono">📖 {category.chapterRef}</p>
      <blockquote className="text-sm text-[var(--color-warm)]/90 italic leading-relaxed border-l-2 border-[var(--color-primary)]/40 pl-3 mt-2">
        {category.verse}
      </blockquote>

      {/* R2 — 헛됨 축 안내 (유한함의 정직한 인정) */}
      <p className="text-xs text-[var(--color-warm)]/60 mt-3 leading-relaxed">
        {category.honestNote}
      </p>

      {/* 인생 계절 선택 (전 3:1-8) */}
      {seasons.length > 0 && (
        <div className="mt-4">
          <p className="text-xs text-[var(--color-warm)]/50 mb-2">
            지금 나는 인생의 어떤 계절에 있나요? (전 3:1~8)
          </p>
          <div className="flex flex-wrap gap-1.5">
            {seasons.map((s) => (
              <button
                key={s.key}
                onClick={() => setSeason(season === s.key ? "" : s.key)}
                title={s.verse}
                className={`text-xs px-3 py-1.5 rounded-full border transition ${
                  season === s.key
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10 text-[var(--color-primary)]"
                    : "border-[var(--color-primary)]/20 text-[var(--color-warm)]/70 hover:border-[var(--color-primary)]/50"
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* 헛됨 성찰 */}
      <div className="mt-4">
        <label className="text-xs text-[var(--color-warm)]/60">
          오늘 헛되게 느껴지는 것 — 그대로 인정해도 괜찮습니다
        </label>
        <textarea
          value={futility}
          onChange={(e) => setFutility(e.target.value)}
          placeholder="해 아래에서 붙잡으려 애썼지만 손에 남지 않은 것을 적어보세요."
          rows={3}
          maxLength={5000}
          className="w-full mt-2 px-3 py-2 rounded-md bg-transparent border border-[var(--color-primary)]/20 text-sm text-[var(--color-warm)]/90 leading-relaxed focus:outline-none focus:border-[var(--color-primary)]/60"
        />
      </div>

      {/* 의미 성찰 — 창조주 경외로 향함 */}
      <div className="mt-3">
        <label className="text-xs text-[var(--color-warm)]/60">
          그럼에도 오늘 하나님이 주신 몫 / 의미 (전 12:13 결론까지)
        </label>
        <textarea
          value={meaning}
          onChange={(e) => setMeaning(e.target.value)}
          placeholder={category.meaningNote}
          rows={3}
          maxLength={5000}
          className="w-full mt-2 px-3 py-2 rounded-md bg-transparent border border-[var(--color-primary)]/20 text-sm text-[var(--color-warm)]/90 leading-relaxed focus:outline-none focus:border-[var(--color-primary)]/60"
        />
      </div>

      {/* §4.4 결론 회피 금지 — 전 12:13 결론 함께 보기 */}
      <div className="mt-4 rounded-md border border-[var(--color-primary)]/20 bg-[var(--color-primary)]/5 p-3">
        <p className="text-xs text-[var(--color-warm)]/80 leading-relaxed">
          &ldquo;일의 결국을 다 들었으니 하나님을 경외하고 그의 명령들을 지킬지어다 이것이 모든 사람의
          본분이니라&rdquo; (전 12:13)
        </p>
        <label className="flex items-center gap-2 mt-2 text-xs text-[var(--color-warm)]/70">
          <input
            type="checkbox"
            checked={conclusionViewed}
            onChange={(e) => setConclusionViewed(e.target.checked)}
          />
          헛됨만 보지 않고 이 결론까지 함께 읽었어요
        </label>
      </div>

      <div className="flex items-center gap-3 mt-4">
        <button
          onClick={() => mutation.mutate()}
          disabled={
            mutation.isPending ||
            (!futility.trim() && !meaning.trim() && !season && !conclusionViewed)
          }
          className="px-4 py-2 rounded-md text-sm bg-[var(--color-primary)]/15 border border-[var(--color-primary)]/40 hover:bg-[var(--color-primary)]/25 disabled:opacity-40 transition"
        >
          {mutation.isPending ? "기록 중..." : "성찰 기록하기"}
        </button>
        {mutation.isError && (
          <span className="text-xs text-red-400">저장에 실패했습니다.</span>
        )}
      </div>

      {/* R1 — 위기 라우팅 응답 */}
      {result?.crisis.routed && (
        <div className="mt-4 rounded-md border border-red-400/40 bg-red-400/5 p-3">
          <p className="text-sm text-red-300 font-semibold">
            잠시 멈추고, 함께 안전을 살펴봐요.
          </p>
          <p className="text-xs text-[var(--color-warm)]/70 mt-1 leading-relaxed">
            전도서의 &ldquo;헛됨&rdquo;은 인생을 포기하라는 말이 아닙니다. 지금 마음이 많이
            힘드시다면 혼자 두지 않겠습니다. 아래 자원은 24시간 열려 있어요.
          </p>
          <p className="text-xs text-red-300 mt-2 font-mono">1577-0199 · 1393 (24시간)</p>
          <Link
            href="/topics/practice"
            className="inline-block mt-2 text-xs text-[var(--color-primary)] hover:underline"
          >
            일기(#1)로 마음을 먼저 적어보기 →
          </Link>
        </div>
      )}

      {/* R2 safetyFooter (헛됨 → 창조주 경외) */}
      {result && !result.crisis.routed && (
        <div className="mt-4 text-[11px] text-[var(--color-warm)]/40 leading-relaxed">
          <p>✓ 기록되었습니다.</p>
          <p className="mt-1">{result.safetyFooter}</p>
        </div>
      )}
    </section>
  );
}

function History() {
  const { data } = useQuery({
    queryKey: ["ecclesiastes-views"],
    queryFn: () => fetchEcclesiastesViews(20),
  });

  const items = data?.items ?? [];
  if (items.length === 0) return null;

  return (
    <section className="mt-8">
      <div className="flex items-baseline justify-between mb-3">
        <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60">
          지금까지의 성찰
        </h2>
        <span className="text-xs text-[var(--color-warm)]/50">
          결론(전 12:13)까지 봄 {data?.conclusionViewedCount ?? 0}회
        </span>
      </div>
      <div className="space-y-2">
        {items.map((it) => (
          <div
            key={it.id}
            className="px-3 py-2 rounded-md border border-[var(--color-primary)]/15 text-sm"
          >
            <div className="flex items-baseline justify-between gap-2">
              <span className="text-xs text-[var(--color-primary)] font-mono">
                {it.chapterRef ?? "전도서"}
              </span>
              <span className="text-[10px] text-[var(--color-warm)]/30">
                {new Date(it.createdAt).toLocaleDateString("ko-KR")}
                {it.conclusionViewed ? " · ✓ 결론" : ""}
              </span>
            </div>
            {it.futilityNote && (
              <p className="text-[var(--color-warm)]/60 mt-1 whitespace-pre-line leading-relaxed">
                헛됨 · {it.futilityNote}
              </p>
            )}
            {it.meaningNote && (
              <p className="text-[var(--color-warm)]/80 mt-1 whitespace-pre-line leading-relaxed">
                의미 · {it.meaningNote}
              </p>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}
