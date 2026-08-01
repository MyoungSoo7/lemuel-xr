"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  recordPractice,
  fetchPractices,
  type PracticeTopicId,
  type PracticeKind,
  type PracticeResponse,
} from "@/lib/api/content";

/**
 * /topics/practice — Theme 6 (마음 지킴, 잠 4:23) · 7 (사람 두려움, 잠 29:25/사 51:7)
 * 실천/성찰 기록 UI.
 *
 * TRACK-A-5-7-ACTION-GUIDANCE §3·§4·§9. /topics 카드 스타일 재사용.
 * 안전선: R1 (crisis routing 응답 → 위기 카드), R2 (safetyFooter), R3 (압박 X 문구),
 *        "AI 보조 — 본문은 성경 참조" footer.
 */

interface ThemeSpec {
  topicId: PracticeTopicId;
  title: string;
  scriptureRef: string;
  scriptureText: string;
  /** R2 — 피해 상황 사용자 보호 (진입 단계부터). */
  entryNote: string;
  kinds: { kind: PracticeKind; label: string; placeholder: string; dimension: "spiritual" | "emotional" | "rational" }[];
}

// 성경 이외 자료 배제 — 본문·문구는 성경/예수의 의도만.
const THEMES: Record<PracticeTopicId, ThemeSpec> = {
  6: {
    topicId: 6,
    title: "마음을 지키는 것",
    scriptureRef: "prov-4:23",
    scriptureText:
      "모든 지킬 만한 것 중에 더욱 네 마음을 지키라 생명의 근원이 이에서 남이니라 (잠 4:23)",
    entryNote:
      "마음 지킴은 마음을 닫는 도구가 아니라, 하나님이 맡기신 영역에 경계를 짓는 자리입니다. 오늘 한 가지만 다르게 해보는 것도 충분합니다.",
    kinds: [
      {
        kind: "heart_checkin",
        label: "오늘 내 마음을 흔든 일",
        placeholder: "오늘 마음을 흔든 사건 한 가지를 적어보세요. 지금 이건 내가 책임지는 영역인가요, 맡기는 영역인가요.",
        dimension: "emotional",
      },
      {
        kind: "boundary_sentence",
        label: "이번 주 한 사람에게 할 한 문장",
        placeholder: "‘네 입의 말을 다스리라’ (잠 13:3). 한 사람에게 전할 한 문장을 만들어보세요.",
        dimension: "rational",
      },
    ],
  },
  7: {
    topicId: 7,
    title: "사람을 두려워하지 않는 것",
    scriptureRef: "prov-29:25",
    scriptureText:
      "사람을 두려워하면 올무에 걸리게 되거니와 여호와를 의지하는 자는 안전하리라 (잠 29:25) · 너희는 사람의 비방을 두려워하지 말라 (사 51:7)",
    entryNote:
      "두려움 = 0 이 목표가 아닙니다. 두려움의 방향만 살펴봅시다. 오늘 한 결정에서 한 사람의 시선만 잠시 옆으로 두는 것도 충분합니다.",
    kinds: [
      {
        kind: "courage_act",
        label: "한 번의 거절 / 용기",
        placeholder: "오늘 한 사람의 시선을 옆으로 두고 내린 결정, 혹은 해본 거절 하나를 적어보세요.",
        dimension: "spiritual",
      },
      {
        kind: "thought_record",
        label: "생각 기록",
        placeholder: "떠오른 생각 → 이게 사실인가 추측인가 → 균형 잡힌 생각. 순서대로 적어보세요.",
        dimension: "rational",
      },
    ],
  },
};

export default function PracticePage() {
  const [topicId, setTopicId] = useState<PracticeTopicId>(7); // §11 — Theme 7 먼저.
  const theme = THEMES[topicId];

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-6">
        <div className="flex items-baseline justify-between">
          <h1 className="text-2xl font-bold">실천과 성찰</h1>
          <Link
            href="/topics"
            className="text-xs text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
          >
            ← 주제
          </Link>
        </div>
        <p className="text-xs text-[var(--color-warm)]/40 mt-2">
          Theme 6·7 — *내면 들여다보기* 를 지나 *밖으로 한 걸음* 디디는 자리.
        </p>
      </header>

      <div className="max-w-3xl mx-auto w-full">
        {/* Theme 전환 */}
        <div className="flex gap-2 mb-5">
          {([6, 7] as PracticeTopicId[]).map((tid) => (
            <button
              key={tid}
              onClick={() => setTopicId(tid)}
              className={`flex-1 text-left px-4 py-3 rounded-lg border transition ${
                topicId === tid
                  ? "border-[var(--color-primary)] bg-[var(--color-primary)]/5"
                  : "border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/60"
              }`}
            >
              <p className="text-xs text-[var(--color-warm)]/40">#{tid}</p>
              <p className="font-semibold mt-1">{THEMES[tid].title}</p>
            </button>
          ))}
        </div>

        {/* 성경 본문 + R2 진입 note */}
        <section className="mb-5 rounded-lg border border-[var(--color-primary)]/20 p-4">
          <p className="text-xs text-[var(--color-primary)] font-mono">
            📖 {theme.scriptureRef}
          </p>
          <blockquote className="text-sm text-[var(--color-warm)]/90 italic leading-relaxed border-l-2 border-[var(--color-primary)]/40 pl-3 mt-2">
            {theme.scriptureText}
          </blockquote>
          <p className="text-xs text-[var(--color-warm)]/60 mt-3 leading-relaxed">
            {theme.entryNote}
          </p>
        </section>

        <PracticeForms theme={theme} />
        <History topicId={topicId} />

        {/* 하단 영구 위기 자원 라인 (§9) */}
        <p className="text-[10px] text-[var(--color-warm)]/30 mt-8 leading-relaxed">
          위기 시: 109 (자살예방 상담전화) · 1577-0199 (정신건강위기상담) · 24시간
          <br />
          AI 보조 — storyteller, 본문은 성경 참조. 성경 이외 자료는 사용하지 않습니다.
        </p>
      </div>
    </main>
  );
}

function PracticeForms({ theme }: { theme: ThemeSpec }) {
  return (
    <div className="space-y-4">
      {theme.kinds.map((k) => (
        <PracticeCard
          key={k.kind}
          topicId={theme.topicId}
          kind={k.kind}
          label={k.label}
          placeholder={k.placeholder}
          dimension={k.dimension}
          scriptureRef={theme.scriptureRef}
        />
      ))}
    </div>
  );
}

function PracticeCard({
  topicId,
  kind,
  label,
  placeholder,
  dimension,
  scriptureRef,
}: {
  topicId: PracticeTopicId;
  kind: PracticeKind;
  label: string;
  placeholder: string;
  dimension: "spiritual" | "emotional" | "rational";
  scriptureRef: string;
}) {
  const qc = useQueryClient();
  const [text, setText] = useState("");
  const [actionTaken, setActionTaken] = useState(false);
  const [result, setResult] = useState<PracticeResponse | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      recordPractice({
        topicId,
        practiceKind: kind,
        situation: text.trim() || undefined,
        actionTaken,
        scriptureRef,
        dimension,
      }),
    onSuccess: (res) => {
      setResult(res);
      setText("");
      setActionTaken(false);
      qc.invalidateQueries({ queryKey: ["practices", topicId] });
    },
  });

  const actionLabel =
    kind === "boundary_sentence"
      ? "이 문장을 실제로 보냈어요"
      : kind === "courage_act"
        ? "오늘 실제로 해봤어요"
        : "행동으로 옮겼어요";

  return (
    <article className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/20">
      <h3 className="font-semibold text-[var(--color-warm)]">{label}</h3>
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder}
        rows={3}
        maxLength={5000}
        className="w-full mt-3 px-3 py-2 rounded-md bg-transparent border border-[var(--color-primary)]/20 text-sm text-[var(--color-warm)]/90 leading-relaxed focus:outline-none focus:border-[var(--color-primary)]/60"
      />

      <label className="flex items-center gap-2 mt-3 text-xs text-[var(--color-warm)]/70">
        <input
          type="checkbox"
          checked={actionTaken}
          onChange={(e) => setActionTaken(e.target.checked)}
        />
        {actionLabel}
      </label>

      <div className="flex items-center gap-3 mt-3">
        <button
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending || (!text.trim() && !actionTaken)}
          className="px-4 py-2 rounded-md text-sm bg-[var(--color-primary)]/15 border border-[var(--color-primary)]/40 hover:bg-[var(--color-primary)]/25 disabled:opacity-40 transition"
        >
          {mutation.isPending ? "기록 중..." : "기록하기"}
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
            지금 마음이 많이 힘드시다면 혼자 두지 않겠습니다. 아래 자원은 24시간 열려 있어요.
          </p>
          <p className="text-xs text-red-300 mt-2 font-mono">
            109 · 1577-0199 (24시간)
          </p>
          <Link
            href="/topics"
            className="inline-block mt-2 text-xs text-[var(--color-primary)] hover:underline"
          >
            일기(#1)로 마음을 먼저 적어보기 →
          </Link>
        </div>
      )}

      {/* R2 safetyFooter + AI footer */}
      {result && !result.crisis.routed && (
        <div className="mt-4 text-[11px] text-[var(--color-warm)]/40 leading-relaxed">
          <p>✓ 기록되었습니다.</p>
          <p className="mt-1">{result.safetyFooter}</p>
        </div>
      )}
    </article>
  );
}

function History({ topicId }: { topicId: PracticeTopicId }) {
  const { data } = useQuery({
    queryKey: ["practices", topicId],
    queryFn: () => fetchPractices(topicId, 20),
  });

  const items = data?.items ?? [];
  const actionCount = data?.actionCount ?? 0;

  const kindLabel = useMemo(
    () => (kind: string) => {
      const spec = THEMES[topicId].kinds.find((k) => k.kind === kind);
      return spec?.label ?? kind;
    },
    [topicId],
  );

  if (items.length === 0) return null;

  return (
    <section className="mt-8">
      <div className="flex items-baseline justify-between mb-3">
        <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60">
          지금까지의 자리
        </h2>
        <span className="text-xs text-[var(--color-warm)]/50">
          작은 행동 {actionCount}회 누적
        </span>
      </div>
      <div className="space-y-2">
        {items.map((it) => (
          <div
            key={it.id}
            className="px-3 py-2 rounded-md border border-[var(--color-primary)]/15 text-sm"
          >
            <div className="flex items-baseline justify-between gap-2">
              <span className="text-xs text-[var(--color-warm)]/50">
                {kindLabel(it.practiceKind)}
              </span>
              <span className="text-[10px] text-[var(--color-warm)]/30">
                {new Date(it.createdAt).toLocaleDateString("ko-KR")}
                {it.actionTaken ? " · ✓ 행동" : ""}
              </span>
            </div>
            {it.situation && (
              <p className="text-[var(--color-warm)]/80 mt-1 whitespace-pre-line leading-relaxed">
                {it.situation}
              </p>
            )}
          </div>
        ))}
      </div>
    </section>
  );
}
