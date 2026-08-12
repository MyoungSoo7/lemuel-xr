"use client";

import { useState } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchBookmarks,
  removeBookmark,
  type BookmarkedCard,
} from "@/lib/api/content";
import { PassageModal } from "@/components/PassageModal";

const CHARACTER_LABEL: Record<string, string> = {
  joseph: "요셉",
  moses: "모세",
  david: "다윗",
  jesus: "예수",
  job: "욥",
  elijah: "엘리야",
};

/**
 * /topics/bookmarks — '내 북마크' 목록 (ooo Seed AC2·AC3).
 * 담아둔 AR 토픽 카드를 최신순으로. 카드에서 원문(📖)을 바로 열고, ♥ 로 뺀다.
 */
export default function BookmarksPage() {
  const [openPassage, setOpenPassage] = useState<string | null>(null);

  const { data: bookmarks = [], isLoading } = useQuery({
    queryKey: ["bookmarks"],
    queryFn: fetchBookmarks,
  });

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-6">
        <div className="flex items-baseline justify-between">
          <h1 className="text-2xl font-bold">내 북마크</h1>
          <Link
            href="/topics"
            className="inline-flex items-center min-h-11 min-w-11 pr-3 text-xs text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
          >
            ← 주제
          </Link>
        </div>
        <p className="text-xs text-[var(--color-warm)]/40 mt-2">
          담아둔 카드 — 최신순. 카드의 📖 로 원문을 열고, ♥ 로 뺄 수 있어요.
        </p>
      </header>

      <div className="max-w-3xl mx-auto w-full">
        {isLoading && (
          <p className="text-[var(--color-warm)]/40 text-sm">로드 중...</p>
        )}
        {!isLoading && bookmarks.length === 0 && (
          <div className="text-center py-16">
            <p className="text-[var(--color-warm)]/40 text-sm italic">
              아직 담아둔 카드가 없습니다.
            </p>
            <Link
              href="/topics"
              className="inline-block mt-4 text-sm text-[var(--color-primary)] hover:underline"
            >
              주제에서 카드를 ♡ 로 담아보세요 →
            </Link>
          </div>
        )}

        <div className="space-y-3">
          {bookmarks.map((c) => (
            <BookmarkCard
              key={c.topicContentId}
              card={c}
              onPassageOpen={(ref) => setOpenPassage(ref)}
            />
          ))}
        </div>
      </div>

      {openPassage && (
        <PassageModal
          reference={openPassage}
          onClose={() => setOpenPassage(null)}
        />
      )}
    </main>
  );
}

function BookmarkCard({
  card,
  onPassageOpen,
}: {
  card: BookmarkedCard;
  onPassageOpen: (ref: string) => void;
}) {
  const qc = useQueryClient();
  const character = card.anchorCharacter ? CHARACTER_LABEL[card.anchorCharacter] : null;

  const remove = useMutation({
    mutationFn: () => removeBookmark(card.topicContentId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["bookmarks"] }),
  });

  return (
    <article className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/50 transition">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="font-semibold text-[var(--color-warm)]">{card.title}</h3>
        <div className="flex items-center gap-2 text-[10px] text-[var(--color-warm)]/50 shrink-0">
          {card.topicId != null && <span>주제 #{card.topicId}</span>}
          {character && <span>인물: {character}</span>}
          <button
            onClick={() => remove.mutate()}
            disabled={remove.isPending}
            aria-label="북마크 빼기"
            aria-pressed={true}
            className="text-base leading-none text-[var(--color-primary)] hover:opacity-70 transition disabled:opacity-40"
          >
            ♥
          </button>
        </div>
      </div>

      {card.scriptureRef && (
        <button
          onClick={() => onPassageOpen(card.scriptureRef!)}
          className="text-xs text-[var(--color-primary)] font-mono mt-2 hover:underline"
        >
          📖 {card.scriptureRef}
        </button>
      )}

      <p className="text-sm text-[var(--color-warm)]/80 mt-3 whitespace-pre-line leading-relaxed">
        {card.body}
      </p>
    </article>
  );
}
