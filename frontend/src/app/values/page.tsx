"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getValueProfile,
  recordPractice,
  updateValueProfile,
  type ValueDef,
  type ValuesMap,
} from "@/lib/api/values";
import Link from "next/link";

const CHARACTER_LABEL: Record<string, string> = {
  joseph: "요셉",
  moses: "모세",
  david: "다윗",
  jesus: "예수",
};

const VALUE_IDS = [1, 2, 3, 4, 5, 6, 7] as const;

/**
 * 자기만의 7 가치 빌더 + 일상 실천 기록.
 * docs/MISSION.md §3 의 AR (일상 습관) 측 핵심 UI.
 *
 * 구성:
 *  - 빈 프로파일 (첫 방문): 7 카드 placeholder, 각 카드에 "정의하기" CTA
 *  - 정의된 프로파일: 각 카드에 title + anchor + "오늘 실천" 버튼
 *  - 상단 stats: 최근 7일 합계, CDR Index, tier
 */
export default function ValuesPage() {
  const qc = useQueryClient();
  const [editing, setEditing] = useState<number | null>(null);

  const { data, isLoading, error } = useQuery({
    queryKey: ["values-me"],
    queryFn: getValueProfile,
  });

  const updateMutation = useMutation({
    mutationFn: (patch: Partial<ValuesMap>) => updateValueProfile(patch),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["values-me"] });
      setEditing(null);
    },
  });

  const practiceMutation = useMutation({
    mutationFn: (valueId: number) => recordPractice({ valueId }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["values-me"] }),
  });

  if (isLoading) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6">
        <p className="text-[var(--color-warm)]/60">불러오는 중...</p>
      </main>
    );
  }

  if (error) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6">
        <p className="text-red-400 text-sm">오류: {(error as Error).message}</p>
      </main>
    );
  }

  const values = data?.values ?? {};
  const stats = data?.stats ?? {
    totalPractices7d: 0,
    countByValue: {},
    cdrIndex: 0,
    tier: "—",
  };
  const definedCount = Object.keys(values).length;

  return (
    <main className="min-h-screen px-6 py-10 max-w-3xl mx-auto">
      <header className="mb-8">
        <Link
          href="/"
          className="inline-flex items-center min-h-11 min-w-11 pr-3 text-xs text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
        >
          ← 홈
        </Link>
        <h1 className="text-2xl sm:text-3xl font-bold mt-2">자기만의 7 가치</h1>
        <p className="text-sm text-[var(--color-warm)]/60 mt-1">
          매일의 작은 실천이 위기의 순간에 빛을 발합니다.
        </p>
      </header>

      <section className="mb-8 grid grid-cols-3 gap-3">
        <StatCard label="이번 주 실천" value={`${stats.totalPractices7d}회`} />
        <StatCard label="CDR Index" value={`${stats.cdrIndex}`} />
        <StatCard label="단계" value={stats.tier} />
      </section>

      {definedCount === 0 && (
        <div className="mb-6 px-4 py-3 rounded-lg border border-amber-500/30 bg-amber-500/5 text-sm">
          아직 가치를 하나도 정의하지 않으셨네요. 7가지 중 *하나라도* 시작해
          보세요 — 어렵게 잡지 말고 *내가 지키고 싶은 것* 한 줄.
        </div>
      )}

      <section className="space-y-3">
        {VALUE_IDS.map((id) => {
          const def = values[String(id)] as ValueDef | undefined;
          const count = stats.countByValue[String(id)] ?? 0;
          const isEditing = editing === id;

          return (
            <article
              key={id}
              className="border border-[var(--color-primary)]/20 rounded-lg p-4"
            >
              <div className="flex items-baseline justify-between gap-3">
                <div className="flex-1 min-w-0">
                  <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
                    가치 {id}
                  </p>
                  {def ? (
                    <h3 className="text-base sm:text-lg font-semibold mt-0.5 truncate">
                      {def.title}
                    </h3>
                  ) : (
                    <p className="text-[var(--color-warm)]/40 mt-0.5 italic">
                      아직 정의되지 않음
                    </p>
                  )}
                  {def?.anchor_character && (
                    <p className="text-xs text-[var(--color-warm)]/50 mt-1">
                      ↳ {CHARACTER_LABEL[def.anchor_character]} 의 가치와 연결
                    </p>
                  )}
                </div>
                <div className="text-right shrink-0">
                  <p className="text-xs text-[var(--color-warm)]/40">7일</p>
                  <p className="text-lg font-bold">{count}</p>
                </div>
              </div>

              {isEditing ? (
                <EditValueForm
                  initial={def}
                  onCancel={() => setEditing(null)}
                  onSave={(d) => updateMutation.mutate({ [String(id)]: d })}
                  pending={updateMutation.isPending}
                />
              ) : (
                <div className="mt-3 flex gap-2">
                  <button
                    type="button"
                    onClick={() => setEditing(id)}
                    className="min-h-11 text-xs px-3 py-1.5 rounded border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
                  >
                    {def ? "수정" : "정의하기"}
                  </button>
                  {def && (
                    <button
                      type="button"
                      onClick={() => practiceMutation.mutate(id)}
                      disabled={practiceMutation.isPending}
                      className="min-h-11 text-xs px-3 py-1.5 rounded bg-[var(--color-primary)] text-black font-medium disabled:opacity-50 hover:bg-[var(--color-primary)]/90"
                    >
                      오늘 실천 +1
                    </button>
                  )}
                </div>
              )}
            </article>
          );
        })}
      </section>
    </main>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="text-center px-3 py-3 rounded-lg border border-[var(--color-primary)]/20">
      <p className="text-[10px] sm:text-xs text-[var(--color-warm)]/50 uppercase tracking-wider">
        {label}
      </p>
      <p className="text-base sm:text-lg font-bold mt-1">{value}</p>
    </div>
  );
}

function EditValueForm({
  initial,
  onCancel,
  onSave,
  pending,
}: {
  initial: ValueDef | undefined;
  onCancel: () => void;
  onSave: (d: ValueDef) => void;
  pending: boolean;
}) {
  const [title, setTitle] = useState(initial?.title ?? "");
  const [anchor, setAnchor] = useState<ValueDef["anchor_character"] | "">(
    initial?.anchor_character ?? "",
  );
  const [note, setNote] = useState(initial?.note ?? "");

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (title.trim().length === 0) return;
        const d: ValueDef = { title: title.trim() };
        if (anchor) d.anchor_character = anchor as ValueDef["anchor_character"];
        if (note.trim()) d.note = note.trim();
        onSave(d);
      }}
      className="mt-3 space-y-2"
    >
      <input
        type="text"
        value={title}
        onChange={(e) => setTitle(e.target.value)}
        placeholder="예: 흔들리지 않는 결정"
        maxLength={60}
        autoFocus
        className="w-full px-3 py-2 rounded bg-black/30 border border-[var(--color-primary)]/30 text-sm focus:outline-none focus:border-[var(--color-primary)]"
      />
      <select
        value={anchor}
        onChange={(e) =>
          setAnchor(e.target.value as ValueDef["anchor_character"] | "")
        }
        className="w-full px-3 py-2 rounded bg-black/30 border border-[var(--color-primary)]/30 text-sm focus:outline-none focus:border-[var(--color-primary)]"
      >
        <option value="">연결 인물 (선택 안 함)</option>
        <option value="joseph">요셉 — 배신·기다림</option>
        <option value="moses">모세 — 두려움·소명</option>
        <option value="david">다윗 — 죄책·회복</option>
        <option value="jesus">예수 — 고난·용서</option>
      </select>
      <textarea
        value={note}
        onChange={(e) => setNote(e.target.value)}
        placeholder="메모 (선택)"
        rows={2}
        maxLength={300}
        className="w-full px-3 py-2 rounded bg-black/30 border border-[var(--color-primary)]/30 text-sm focus:outline-none focus:border-[var(--color-primary)] resize-none"
      />
      <div className="flex gap-2">
        <button
          type="submit"
          disabled={pending || title.trim().length === 0}
          className="text-xs px-3 py-1.5 rounded bg-[var(--color-primary)] text-black font-medium disabled:opacity-50"
        >
          {pending ? "저장 중..." : "저장"}
        </button>
        <button
          type="button"
          onClick={onCancel}
          className="text-xs px-3 py-1.5 rounded border border-[var(--color-primary)]/30"
        >
          취소
        </button>
      </div>
    </form>
  );
}
