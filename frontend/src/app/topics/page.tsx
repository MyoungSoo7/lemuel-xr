"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import {
  fetchTopics,
  fetchTopicCards,
  fetchScripturePassage,
  type Topic,
  type TopicCard,
  type ScripturePassage,
} from "@/lib/api/content";

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
 */
export default function TopicsPage() {
  const [selectedTopic, setSelectedTopic] = useState<Topic | null>(null);
  const [openPassage, setOpenPassage] = useState<string | null>(null);

  const { data: topics = [], isLoading } = useQuery({
    queryKey: ["topics"],
    queryFn: fetchTopics,
  });

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
            href="/topics/practice"
            className="block mt-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]/70 transition"
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
          <p className="text-[10px] text-[var(--color-warm)]/30 mt-4 leading-relaxed">
            * 본문 인용: 현대인의 성경 (생명의말씀사) — MVP 비공개 테스터 fair use.
            <br />
            ** 위기 시 1393 (자살예방상담전화 24시간)
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
  onPassageOpen,
}: {
  topic: Topic;
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
          <Card key={c.id} card={c} onPassageOpen={onPassageOpen} />
        ))}
      </div>
    </div>
  );
}

function Card({
  card,
  onPassageOpen,
}: {
  card: TopicCard;
  onPassageOpen: (ref: string) => void;
}) {
  const character = card.anchorCharacter ? CHARACTER_LABEL[card.anchorCharacter] : null;
  const emotion = card.targetEmotion ? EMOTION_LABEL[card.targetEmotion] : null;

  return (
    <article className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/50 transition">
      <div className="flex items-baseline justify-between gap-3">
        <h3 className="font-semibold text-[var(--color-warm)]">{card.title}</h3>
        <div className="flex gap-2 text-[10px] text-[var(--color-warm)]/50 shrink-0">
          {character && <span>인물: {character}</span>}
          {emotion && <span>감정: {emotion}</span>}
          {card.difficulty && (
            <span>난이도 {"●".repeat(card.difficulty)}</span>
          )}
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

function PassageModal({
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
