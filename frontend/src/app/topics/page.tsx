"use client";

import { useState } from "react";
import Link from "next/link";
import { CRISIS_RESOURCES } from "@/lib/crisis-resources";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchTopics,
  fetchTopicCards,
  fetchBookmarks,
  addBookmark,
  removeBookmark,
  type Topic,
  type TopicCard,
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

const EMOTION_LABEL: Record<string, string> = {
  ANXIOUS: "불안",
  SAD: "슬픔",
  ANGRY: "분노",
  CONFUSED: "혼란",
  LONELY: "외로움",
  EXHAUSTED: "지침",
  GRATEFUL: "감사",
};

/**
 * /topics — AR 1~7 일상 영적 양식 카드 페이지.
 *
 * MISSION.md §3 의 AR (일상 습관) 측 콘텐츠 진입점.
 * 사용자 흐름: 토픽 list → 카드 → scripture_ref 클릭 → 본문 모달.
 * 카드 하트(♡/♥) → 북마크 담기/빼기 → /topics/bookmarks 에서 다시 봄.
 */
export default function TopicsPage() {
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [openPassage, setOpenPassage] = useState<string | null>(null);

  const { data: topics = [], isLoading } = useQuery({
    queryKey: ["topics"],
    queryFn: fetchTopics,
  });

  const { data: bookmarks = [] } = useQuery({
    queryKey: ["bookmarks"],
    queryFn: fetchBookmarks,
  });
  const bookmarkedIds = new Set(bookmarks.map((b) => b.topicContentId));

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-5xl mx-auto w-full mb-6">
        <div className="flex items-baseline justify-between">
          <h1 className="text-2xl font-bold">일상 영적 양식</h1>
          <Link
            href="/"
            className="text-xs text-[var(--color-warm)]/40 hover:text-[var(--color-warm)]/70"
          >
            ← 홈
          </Link>
        </div>
        <p className="text-xs text-[var(--color-warm)]/40 mt-2">
          AR 1~7 — 매일 한 카드씩 만나는 *내면 단련* 의 자리. *큐티 톤* 으로 짧게.
        </p>
      </header>

      <div className="max-w-5xl mx-auto w-full grid grid-cols-1 lg:grid-cols-[1fr_2fr] gap-6">
        {/* Topic list */}
        <section>
          <h2 className="text-sm uppercase tracking-wider text-[var(--color-warm)]/60 mb-3">
            7 가지 주제
          </h2>
          {isLoading && (
            <p className="text-[var(--color-warm)]/40 text-sm">로드 중...</p>
          )}
          <div className="space-y-2">
            {topics.map((t) => (
              <button
                key={t.id}
                onClick={() => setSelectedTopic(t)}
                className={`w-full text-left px-4 py-3 rounded-lg border transition ${
                  selectedTopic?.id === t.id
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/5"
                    : "border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/60"
                }`}
              >
                <p className="text-xs text-[var(--color-warm)]/40">#{t.id}</p>
                <p className="font-semibold mt-1">{t.title}</p>
              </button>
            ))}
          </div>
          <Link
            href="/topics/bookmarks"
            className="block mt-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)]/80 transition"
          >
            <p className="text-xs text-[var(--color-warm)]/40">내 북마크</p>
            <p className="font-semibold mt-1 text-sm">
              ♥ 담아둔 카드 다시 보기{bookmarks.length > 0 ? ` (${bookmarks.length})` : ""} →
            </p>
          </Link>
          <Link
            href="/topics/practice"
            className="block mt-2 px-4 py-3 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]/70 transition"
          >
            <p className="text-xs text-[var(--color-warm)]/40">#6 · #7 실천</p>
            <p className="font-semibold mt-1 text-sm">마음 지킴 · 사람 두려움 실천하기 →</p>
          </Link>
          <Link
            href="/topics/ecclesiastes"
            className="block mt-2 px-4 py-3 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]/70 transition"
          >
            <p className="text-xs text-[var(--color-warm)]/40">기준4 전도서</p>
            <p className="font-semibold mt-1 text-sm">전도서와 인생 — 헛됨과 의미 성찰하기 →</p>
          </Link>
          <Link
            href="/topics/journal"
            className="block mt-2 px-4 py-3 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]/70 transition"
          >
            <p className="text-xs text-[var(--color-warm)]/40">기준1 일기</p>
            <p className="font-semibold mt-1 text-sm">일기와 조언 — 감정에 맞는 성경 구절 받기 →</p>
          </Link>
          <Link
            href="/topics/proverbs"
            className="block mt-2 px-4 py-3 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]/70 transition"
          >
            <p className="text-xs text-[var(--color-warm)]/40">기준2 잠언</p>
            <p className="font-semibold mt-1 text-sm">잠언과 지혜 — 주제로 지혜 구절 찾기 →</p>
          </Link>
          <p className="text-[10px] text-[var(--color-warm)]/30 mt-4 leading-relaxed">
            * 본문 인용: 현대인의 성경 (생명의말씀사) — MVP 비공개 테스터 fair use.
            <br />
            ** 위기 시 {CRISIS_RESOURCES[0].tel} ({CRISIS_RESOURCES[0].shortLabel} 24시간)
          </p>
        </section>

        {/* Cards */}
        <section>
          {!selectedTopic ? (
            <p className="text-[var(--color-warm)]/40 text-sm italic">
              ← 주제를 선택하세요
            </p>
          ) : (
            <TopicCards
              topic={selectedTopic}
              bookmarkedIds={bookmarkedIds}
              onPassageOpen={(ref) => setOpenPassage(ref)}
            />
          )}
        </section>
      </div>

      {/* Scripture passage modal */}
      {openPassage && (
        <PassageModal
          reference={openPassage}
          onClose={() => setOpenPassage(null)}
        />
      )}
    </main>
  );
}

function TopicCards({
  topic,
  bookmarkedIds,
  onPassageOpen,
}: {
  topic: Topic;
  bookmarkedIds: Set<number>;
  onPassageOpen: (ref: string) => void;
}) {
  const { data: cards = [], isLoading } = useQuery({
    queryKey: ["topic-cards", topic.id],
    queryFn: () => fetchTopicCards(topic.id, undefined, 10),
  });

  return (
    <div>
      <div className="mb-4">
        <p className="text-xs text-[var(--color-warm)]/40">#{topic.id}</p>
        <h2 className="text-xl font-bold mt-1">{topic.title}</h2>
      </div>

      {isLoading && (
        <p className="text-[var(--color-warm)]/40 text-sm">카드 로드 중...</p>
      )}
      {!isLoading && cards.length === 0 && (
        <p className="text-[var(--color-warm)]/40 text-sm italic">
          이 주제의 카드가 아직 없습니다.
        </p>
      )}

      <div className="space-y-3">
        {cards.map((c) => (
          <Card
            key={c.id}
            card={c}
            isBookmarked={bookmarkedIds.has(c.id)}
            onPassageOpen={onPassageOpen}
          />
        ))}
      </div>
    </div>
  );
}

function Card({
  card,
  isBookmarked,
  onPassageOpen,
}: {
  card: TopicCard;
  isBookmarked: boolean;
  onPassageOpen: (ref: string) => void;
}) {
  const qc = useQueryClient();
  const character = card.anchorCharacter ? CHARACTER_LABEL[card.anchorCharacter] : null;
  const emotion = card.targetEmotion ? EMOTION_LABEL[card.targetEmotion] : null;

  const toggle = useMutation({
    mutationFn: () =>
      isBookmarked ? removeBookmark(card.id) : addBookmark(card.id).then(() => undefined),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["bookmarks"] }),
  });

  return (
    <article className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/50 transition">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="font-semibold text-[var(--color-warm)]">{card.title}</h3>
        <div className="flex items-center gap-2 text-[10px] text-[var(--color-warm)]/50 shrink-0">
          {character && <span>인물: {character}</span>}
          {emotion && <span>감정: {emotion}</span>}
          {card.difficulty && (
            <span>난이도 {"●".repeat(card.difficulty)}</span>
          )}
          <button
            onClick={() => toggle.mutate()}
            disabled={toggle.isPending}
            aria-label={isBookmarked ? "북마크 빼기" : "북마크 담기"}
            aria-pressed={isBookmarked}
            className={`text-base leading-none transition disabled:opacity-40 ${
              isBookmarked
                ? "text-[var(--color-primary)]"
                : "text-[var(--color-warm)]/30 hover:text-[var(--color-primary)]/70"
            }`}
          >
            {isBookmarked ? "♥" : "♡"}
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
