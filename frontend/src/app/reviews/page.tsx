"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import {
  fetchReviewQueue,
  fetchReviewHistory,
  submitTheologyReview,
  submitClinicalReview,
  type QueueItem,
  type HistoryItem,
  type TimelineEntry,
} from "@/lib/api/reviews";

/**
 * 검토 큐 페이지 — 자문가용.
 *
 * 인증·권한·자격 매칭은 Phase 2-D 에서 보강. 현재는 *backend 가 in_review 콘텐츠 목록*
 * 을 노출하면 *어느 자문가든* 들어와 review POST 가능. 운영 시 reviewer_profiles 시드된
 * 사용자만 접근하도록 미들웨어 추가 예정.
 */
export default function ReviewsPage() {
  const queryClient = useQueryClient();
  const [activeRole, setActiveRole] = useState<"theology" | "clinical">("theology");
  const [selected, setSelected] = useState<QueueItem | null>(null);
  const [tab, setTab] = useState<"queue" | "history">("queue");

  const { data: queue = [], isLoading } = useQuery({
    queryKey: ["review-queue"],
    queryFn: fetchReviewQueue,
    refetchInterval: 30_000,
  });

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-5xl mx-auto w-full mb-6">
        <div className="flex items-baseline justify-between">
          <h1 className="text-2xl font-bold">검토 큐</h1>
          <Link href="/" className="text-xs text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70">
            ← 홈
          </Link>
        </div>
        <p className="text-xs text-[var(--color-warm)]/40 mt-2">
          신학·임상 자문가가 *in_review* 상태 콘텐츠를 검토 + verdict 등록.
          전체 거버넌스: <span className="font-mono">docs/governance/CLINICAL-REVIEW.md</span>
        </p>

        <nav className="flex gap-2 mt-4 border-b border-[var(--color-primary)]/20">
          {([
            { id: "queue", label: `Pending (${queue.length})` },
            { id: "history", label: "검토 이력" },
          ] as const).map((t) => (
            <button
              key={t.id}
              onClick={() => setTab(t.id)}
              className={`px-4 py-2 text-sm ${
                tab === t.id
                  ? "border-b-2 border-[var(--color-primary)] text-[var(--color-primary)]"
                  : "text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
              }`}
            >
              {t.label}
            </button>
          ))}
        </nav>
      </header>

      {tab === "history" ? (
        <HistoryView />
      ) : (

      <div className="max-w-5xl mx-auto w-full grid grid-cols-1 lg:grid-cols-[1fr_2fr] gap-6">
        {/* Queue list */}
        <section>
          <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-3">
            Pending ({queue.length})
          </h2>
          {isLoading && (
            <p className="text-[var(--color-warm)]/40 text-sm">로드 중...</p>
          )}
          <div className="space-y-2">
            {queue.map((q) => (
              <button
                key={q.contentVersionId}
                onClick={() => setSelected(q)}
                className={`w-full text-left px-4 py-3 rounded-lg border transition ${
                  selected?.contentVersionId === q.contentVersionId
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/5"
                    : "border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/60"
                }`}
              >
                <p className="text-xs text-[var(--color-warm)]/40">
                  {q.contentKind} · {q.version}
                </p>
                <p className="font-mono text-sm mt-1">{q.contentRef}</p>
                <div className="flex gap-2 mt-2 text-[10px]">
                  <span
                    className={
                      q.theologyReviewed
                        ? "text-green-400"
                        : "text-[var(--color-warm)]/40"
                    }
                  >
                    신학 {q.theologyReviewed ? "✓" : "○"}
                  </span>
                  <span
                    className={
                      q.clinicalReviewed
                        ? "text-green-400"
                        : "text-[var(--color-warm)]/40"
                    }
                  >
                    임상 {q.clinicalReviewed ? "✓" : "○"}
                  </span>
                </div>
              </button>
            ))}
            {!isLoading && queue.length === 0 && (
              <p className="text-[var(--color-warm)]/40 text-sm italic">
                in_review 상태 콘텐츠가 없습니다.
              </p>
            )}
          </div>
        </section>

        {/* Review form */}
        <section className={tab === "queue" ? "" : "hidden"}>
          {!selected ? (
            <p className="text-[var(--color-warm)]/40 text-sm italic">← 큐에서 항목을 선택하세요</p>
          ) : (
            <>
              <div className="mb-4">
                <p className="text-xs text-[var(--color-warm)]/40">
                  {selected.contentKind} · {selected.version}
                </p>
                <p className="font-mono text-sm mt-1">{selected.contentRef}</p>
                <p className="text-[10px] text-[var(--color-warm)]/40 mt-1">
                  generated_by: {selected.generatedBy ?? "(unknown)"}
                </p>
              </div>

              <div className="flex gap-2 mb-4 border-b border-[var(--color-primary)]/20">
                {(["theology", "clinical"] as const).map((r) => (
                  <button
                    key={r}
                    onClick={() => setActiveRole(r)}
                    className={`px-4 py-2 text-sm ${
                      activeRole === r
                        ? "border-b-2 border-[var(--color-primary)] text-[var(--color-primary)]"
                        : "text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
                    }`}
                  >
                    {r === "theology" ? "신학 자문" : "임상 자문"}
                  </button>
                ))}
              </div>

              {activeRole === "theology" ? (
                <TheologyForm
                  contentVersionId={selected.contentVersionId}
                  onSubmitted={() => {
                    queryClient.invalidateQueries({ queryKey: ["review-queue"] });
                    setSelected(null);
                  }}
                />
              ) : (
                <ClinicalForm
                  contentVersionId={selected.contentVersionId}
                  onSubmitted={() => {
                    queryClient.invalidateQueries({ queryKey: ["review-queue"] });
                    setSelected(null);
                  }}
                />
              )}
            </>
          )}
        </section>
      </div>
      )}
    </main>
  );
}

// ============= History view =============
function HistoryView() {
  const { data: items = [], isLoading } = useQuery({
    queryKey: ["review-history"],
    queryFn: () => fetchReviewHistory(30),
  });

  return (
    <div className="max-w-5xl mx-auto w-full">
      <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-3">
        최근 결정 ({items.length})
      </h2>
      {isLoading && <p className="text-[var(--color-warm)]/40 text-sm">로드 중...</p>}
      {!isLoading && items.length === 0 && (
        <p className="text-[var(--color-warm)]/40 text-sm italic">
          결정 완료된 콘텐츠가 아직 없습니다.
        </p>
      )}
      <div className="space-y-3">
        {items.map((it) => (
          <HistoryCard key={it.contentVersionId} item={it} />
        ))}
      </div>
    </div>
  );
}

function HistoryCard({ item }: { item: HistoryItem }) {
  const [open, setOpen] = useState(false);
  const statusStyle: Record<string, string> = {
    published: "text-green-400 border-green-500/30 bg-green-500/5",
    rejected: "text-red-400 border-red-500/30 bg-red-500/5",
    changes_requested: "text-yellow-400 border-yellow-500/30 bg-yellow-500/5",
  };
  const cls = statusStyle[item.status] ?? "text-[var(--color-warm)]/60 border-[var(--color-primary)]/20";
  const merged: TimelineEntry[] = [...item.theology, ...item.clinical].sort(
    (a, b) => new Date(a.reviewedAt).getTime() - new Date(b.reviewedAt).getTime(),
  );

  return (
    <article className={`rounded-lg border ${cls.split(" ").slice(1).join(" ")}`}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full text-left px-4 py-3 flex items-center justify-between"
      >
        <div>
          <p className="text-xs text-[var(--color-warm)]/40">
            {item.contentKind} · {item.version} · {new Date(item.createdAt).toLocaleString()}
          </p>
          <p className="font-mono text-sm mt-1">{item.contentRef}</p>
        </div>
        <span className={`text-xs px-2 py-1 rounded font-semibold ${cls}`}>
          {item.status}
        </span>
      </button>
      {open && (
        <div className="border-t border-[var(--color-primary)]/10 px-4 py-3 space-y-2">
          {merged.length === 0 ? (
            <p className="text-[var(--color-warm)]/40 text-xs italic">
              검토 row 없음 (수동 조정).
            </p>
          ) : (
            merged.map((t, i) => (
              <TimelineRow key={`${t.side}-${i}`} entry={t} />
            ))
          )}
        </div>
      )}
    </article>
  );
}

function TimelineRow({ entry }: { entry: TimelineEntry }) {
  const sideColor = entry.side === "theology" ? "text-blue-300" : "text-purple-300";
  const verdictColor =
    entry.verdict === "approve"
      ? "text-green-400"
      : entry.verdict === "reject"
        ? "text-red-400"
        : "text-yellow-400";
  return (
    <div className="text-xs flex gap-3 items-start py-1.5 border-b border-[var(--color-primary)]/5 last:border-b-0">
      <span className="text-[var(--color-warm)]/40 font-mono whitespace-nowrap">
        {new Date(entry.reviewedAt).toLocaleString()}
      </span>
      <span className={`${sideColor} font-semibold w-14`}>
        {entry.side === "theology" ? "신학" : "임상"}
      </span>
      <span className={`${verdictColor} w-24 font-mono`}>
        {entry.verdict}
        {entry.vetoUsed ? " ⚠VETO" : ""}
      </span>
      <span className="text-[var(--color-warm)]/60 flex-1 whitespace-pre-line">
        {entry.notes || <em className="text-[var(--color-warm)]/30">(no notes)</em>}
      </span>
    </div>
  );
}

// ============= Theology form =============
function TheologyForm({
  contentVersionId,
  onSubmitted,
}: {
  contentVersionId: string;
  onSubmitted: () => void;
}) {
  const [verdict, setVerdict] = useState<"approve" | "request_changes" | "reject">("approve");
  const [scriptureAccuracy, setScriptureAccuracy] = useState(4);
  const [doctrinalBalance, setDoctrinalBalance] = useState(4);
  const [therapeuticSafety, setTherapeuticSafety] = useState(4);
  const [notes, setNotes] = useState("");
  const [referencedPmids, setReferencedPmids] = useState("");

  const mut = useMutation({
    mutationFn: () =>
      submitTheologyReview({
        contentVersionId,
        verdict,
        scriptureAccuracy,
        doctrinalBalance,
        therapeuticSafety,
        notes: notes || undefined,
        referencedPmids: referencedPmids ? referencedPmids.split(/\s+/).filter(Boolean) : undefined,
      }),
    onSuccess: onSubmitted,
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        mut.mutate();
      }}
      className="space-y-4"
    >
      <VerdictRadio value={verdict} onChange={setVerdict} />
      <ScoreSlider label="성경 정확성 (scripture_accuracy)" value={scriptureAccuracy} onChange={setScriptureAccuracy} />
      <ScoreSlider label="교리 균형 (doctrinal_balance)" value={doctrinalBalance} onChange={setDoctrinalBalance} />
      <ScoreSlider label="치유적 안전성 (therapeutic_safety)" value={therapeuticSafety} onChange={setTherapeuticSafety} />
      <TextArea label="메모" value={notes} onChange={setNotes} />
      <TextArea
        label="인용 PMID (공백 구분)"
        value={referencedPmids}
        onChange={setReferencedPmids}
        placeholder="35609469 33634766 ..."
      />
      <SubmitButton disabled={mut.isPending} error={mut.isError ? (mut.error as Error).message : null} />
    </form>
  );
}

// ============= Clinical form =============
function ClinicalForm({
  contentVersionId,
  onSubmitted,
}: {
  contentVersionId: string;
  onSubmitted: () => void;
}) {
  const [verdict, setVerdict] = useState<"approve" | "request_changes" | "reject">("approve");
  const [traumaSafety, setTraumaSafety] = useState(4);
  const [crisisResource, setCrisisResource] = useState(4);
  const [moralInjuryRisk, setMoralInjuryRisk] = useState(4);
  const [evidenceQuality, setEvidenceQuality] = useState(4);
  const [vetoUsed, setVetoUsed] = useState(false);
  const [vetoReason, setVetoReason] = useState("");
  const [notes, setNotes] = useState("");
  const [referencedPmids, setReferencedPmids] = useState("");

  const mut = useMutation({
    mutationFn: () =>
      submitClinicalReview({
        contentVersionId,
        verdict,
        traumaSafety,
        crisisResourceCompliance: crisisResource,
        moralInjuryRisk,
        evidenceQuality,
        vetoUsed,
        vetoReason: vetoUsed ? vetoReason : undefined,
        notes: notes || undefined,
        referencedPmids: referencedPmids ? referencedPmids.split(/\s+/).filter(Boolean) : undefined,
      }),
    onSuccess: onSubmitted,
  });

  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        mut.mutate();
      }}
      className="space-y-4"
    >
      <VerdictRadio value={verdict} onChange={setVerdict} />
      <ScoreSlider label="트라우마 안전 (trauma_safety)" value={traumaSafety} onChange={setTraumaSafety} />
      <ScoreSlider label="위기 자원 활용 (crisis_resource_compliance)" value={crisisResource} onChange={setCrisisResource} />
      <ScoreSlider
        label="moral injury 위험 (낮을수록 위험 — Jones 2022)"
        value={moralInjuryRisk}
        onChange={setMoralInjuryRisk}
      />
      <ScoreSlider label="근거 질 (evidence_quality)" value={evidenceQuality} onChange={setEvidenceQuality} />

      <div className="space-y-2 px-3 py-3 rounded-lg border border-red-500/30 bg-red-500/5">
        <label className="flex items-center gap-2 text-sm">
          <input
            type="checkbox"
            checked={vetoUsed}
            onChange={(e) => setVetoUsed(e.target.checked)}
          />
          <span className="font-semibold text-red-400">Veto — 단독 reject 사용</span>
        </label>
        {vetoUsed && (
          <TextArea
            label="Veto 사유 (필수)"
            value={vetoReason}
            onChange={setVetoReason}
            placeholder="예: moral injury 위험 발견. 고난 정당화 footer 없음."
          />
        )}
      </div>

      <TextArea label="메모" value={notes} onChange={setNotes} />
      <TextArea
        label="인용 PMID (공백 구분)"
        value={referencedPmids}
        onChange={setReferencedPmids}
        placeholder="35609469 40425169 ..."
      />
      <SubmitButton disabled={mut.isPending} error={mut.isError ? (mut.error as Error).message : null} />
    </form>
  );
}

// ============= Common form parts =============

function VerdictRadio({
  value,
  onChange,
}: {
  value: "approve" | "request_changes" | "reject";
  onChange: (v: "approve" | "request_changes" | "reject") => void;
}) {
  const opts: Array<{ id: "approve" | "request_changes" | "reject"; label: string; color: string }> = [
    { id: "approve", label: "Approve", color: "text-green-400" },
    { id: "request_changes", label: "Request changes", color: "text-yellow-400" },
    { id: "reject", label: "Reject", color: "text-red-400" },
  ];
  return (
    <div className="flex gap-2">
      {opts.map((o) => (
        <label
          key={o.id}
          className={`flex-1 px-3 py-2 rounded-lg border text-sm cursor-pointer text-center transition ${
            value === o.id
              ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10"
              : "border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/60"
          }`}
        >
          <input
            type="radio"
            name="verdict"
            checked={value === o.id}
            onChange={() => onChange(o.id)}
            className="sr-only"
          />
          <span className={o.color}>{o.label}</span>
        </label>
      ))}
    </div>
  );
}

function ScoreSlider({
  label,
  value,
  onChange,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
}) {
  return (
    <div>
      <div className="flex justify-between text-sm mb-1">
        <span>{label}</span>
        <span className="font-mono">{value} / 5</span>
      </div>
      <input
        type="range"
        min={1}
        max={5}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full"
      />
    </div>
  );
}

function TextArea({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <div>
      <p className="text-sm mb-1">{label}</p>
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        rows={3}
        className="w-full px-3 py-2 rounded-lg bg-black/30 border border-[var(--color-primary)]/30 placeholder:text-[var(--color-warm)]/30 focus:outline-none focus:border-[var(--color-primary)] text-sm"
      />
    </div>
  );
}

function SubmitButton({ disabled, error }: { disabled: boolean; error: string | null }) {
  return (
    <>
      <button
        type="submit"
        disabled={disabled}
        className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40 hover:bg-[var(--color-primary)]/90"
      >
        {disabled ? "전송 중..." : "검토 결과 등록"}
      </button>
      {error && <p className="text-red-400 text-xs mt-2">오류: {error}</p>}
    </>
  );
}
