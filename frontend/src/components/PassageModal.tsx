"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchScripturePassage, type ScripturePassage } from "@/lib/api/content";

const CHARACTER_LABEL: Record<string, string> = {
  joseph: "요셉",
  moses: "모세",
  david: "다윗",
  jesus: "예수",
  job: "욥",
  elijah: "엘리야",
};

/** scripture_ref 를 눌렀을 때 뜨는 본문 모달 — /topics 와 /topics/bookmarks 공용. */
export function PassageModal({
  reference,
  onClose,
}: {
  reference: string;
  onClose: () => void;
}) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["scripture", reference],
    queryFn: () => fetchScripturePassage(reference),
  });

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4"
      onClick={onClose}
    >
      <div
        className="max-w-2xl w-full bg-[var(--color-bg)] border border-[var(--color-primary)]/40 rounded-xl p-6"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-baseline justify-between mb-3">
          <h2 className="text-lg font-bold font-mono">{reference}</h2>
          <button
            onClick={onClose}
            className="text-[var(--color-warm)]/40 hover:text-[var(--color-warm)] text-2xl leading-none"
            aria-label="닫기"
          >
            ×
          </button>
        </div>

        {isLoading && (
          <p className="text-[var(--color-warm)]/40 text-sm">본문 로드 중...</p>
        )}
        {isError && (
          <p className="text-red-400 text-sm">
            본문을 불러올 수 없습니다: {(error as Error).message}
          </p>
        )}
        {data && <PassageBody passage={data} />}
      </div>
    </div>
  );
}

function PassageBody({ passage }: { passage: ScripturePassage }) {
  return (
    <div>
      <p className="text-xs text-[var(--color-warm)]/40 mb-2">
        {passage.book} {passage.chapter}장 {passage.verseStart}
        {passage.verseEnd && passage.verseEnd !== passage.verseStart
          ? `–${passage.verseEnd}`
          : ""}
        절 · {passage.translation}
      </p>
      <blockquote className="text-base text-[var(--color-warm)]/90 italic leading-relaxed border-l-2 border-[var(--color-primary)]/40 pl-4">
        {passage.text}
      </blockquote>
      {(passage.themeTags?.length || passage.characterTags?.length) && (
        <div className="flex flex-wrap gap-2 mt-4">
          {passage.themeTags?.map((tag) => (
            <span
              key={tag}
              className="text-[10px] px-2 py-1 rounded bg-[var(--color-primary)]/10 text-[var(--color-primary)]"
            >
              #{tag}
            </span>
          ))}
          {passage.characterTags?.map((tag) => (
            <span
              key={`c-${tag}`}
              className="text-[10px] px-2 py-1 rounded bg-[var(--color-warm)]/10 text-[var(--color-warm)]/70"
            >
              👤 {CHARACTER_LABEL[tag] || tag}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
