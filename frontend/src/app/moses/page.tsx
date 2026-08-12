"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { NarrationAudioButton } from "@/components/NarrationAudioButton";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";
import {
  scene1Wilderness,
  scene2Monologues,
  scene4Reactions,
  scene5Monologue,
  scene6OutroByScene3,
  scene3AssignmentsToPattern,
  buildScene3Echo,
  type Scene2Gesture,
  type Scene3Pattern,
  type Scene4Choice,
} from "@/lib/content/moses-monologues";
import { SceneBootState } from "@/components/SceneBootState";

/**
 * Moses 미션 — Phase 2 완전 활성 (요셉과 동급).
 *
 * Scene 1 cinematic → Scene 2 gesture_sequence(경외) → Scene 3 distribute(다섯 변명 카드,
 * throw/heart) → Scene 4 pick_one(파라오 앞 지팡이) → Scene 5 gesture_sequence(홍해, 단일
 * 행동) → Scene 6 outro(회복) → complete → 홈.
 *
 * echo(직전 결정 모놀로그) 는 moses-monologues.ts 의 frontend fallback 이 담당한다 —
 * backend 의 responseText 는 moses.yml 에 monologues/outcomes/reactions map 이 없어 null.
 * shape 는 요셉과 동일하므로 자문 통과 후 backend round-trip 으로 교체 가능.
 */
type Scene = JosephStartResponse;

interface DecisionEcho {
  fromScene: number;
  text: string;
}

interface OptionLike {
  id: string;
  label: string;
}

export default function MosesPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);
  const [echo, setEcho] = useState<DecisionEcho | null>(null);
  const [scene3Pattern, setScene3Pattern] = useState<Scene3Pattern | null>(
    null,
  );

  const start = useMutation({
    mutationFn: () => startMission("moses", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("moses", scene!.sessionId, sceneId, decision),
    onSuccess: (d, vars) => {
      // backend responseText 우선, 없으면 frontend fallback (moses 는 항상 fallback).
      const local = buildLocalEcho(vars.sceneId, vars.decision);
      const text = d.responseText ?? local?.text ?? null;
      if (text) setEcho({ fromScene: vars.sceneId, text });
      if (local?.scene3Pattern) setScene3Pattern(local.scene3Pattern);
      setScene(d);
      setHistory((h) => [...h, JSON.stringify(d.scenePayload.title)]);
    },
  });

  useEffect(() => {
    if (!scene && !start.isPending && !start.isError) start.mutate();
  }, [scene, start]);

  // Scene 6 (outro) 진입 시 Scene 3 패턴 기반 결말 톤
  const outroText = useMemo(() => {
    if (scene?.currentScene !== 6) return null;
    return scene3Pattern
      ? scene6OutroByScene3[scene3Pattern]
      : scene6OutroByScene3.mixed; // fallback
  }, [scene?.currentScene, scene3Pattern]);

  if (!scene) {
    return (
      <SceneBootState
        isError={start.isError}
        error={start.error}
        onRetry={() => start.mutate()}
      />
    );
  }

  const payload = scene.scenePayload as Record<string, unknown>;
  const title = (payload.title as string) ?? "Scene";
  const rawType = (payload.type as string) ?? "";
  const interaction = (payload.interaction as string) ?? "";
  const sceneType = rawType === "interaction" ? interaction : rawType;

  // moses.yml 은 interaction 데이터(steps/cards/slots/options)를 씬의 `extras:`
  // 블록 안에 중첩한다 (joseph.yml 은 씬 top-level). loader 가 그 블록을 통째로
  // payload.extras 로 넘기므로, 여기서 extras 를 우선 읽고 top-level 을 fallback.
  const extras = (payload.extras as Record<string, unknown> | undefined) ?? {};
  const field = <T,>(key: string): T | undefined =>
    (extras[key] as T | undefined) ?? (payload[key] as T | undefined);

  const advance = (sceneId: number, decision: unknown) => {
    decide.mutate({ sceneId, decision });
  };

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Moses — Scene {scene.currentScene}/6 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      {/* Decision echo — 직전 결정의 모놀로그 / 아웃컴 */}
      {echo && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 italic text-sm text-[var(--color-warm)]/90">
          <p className="whitespace-pre-line">{echo.text}</p>
          <div className="mt-2">
            <NarrationAudioButton text={echo.text} onUnavailable="hide" />
          </div>
          <p className="text-[10px] not-italic text-[var(--color-warm)]/40 mt-2 text-right">
            * AI 보조 — 본문은 성경 참조 *
          </p>
        </section>
      )}

      {/* Scene 배경 — moses 전용 이미지 없으면 grad placeholder */}
      <section className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative aspect-video bg-gradient-to-b from-stone-900 via-stone-800 to-stone-950">
        <div className="absolute inset-0 flex items-end p-5">
          <p className="text-sm text-[var(--color-warm)]/80 italic max-w-prose">
            {scene.currentScene === 1 &&
              "광야의 40년. 양 떼와 침묵 — 부름 받기 전의 시간."}
            {scene.currentScene === 2 &&
              "떨기나무에 불이 붙었으나 사위지 않는다. 신을 벗어라, 이 땅은 거룩하다."}
            {scene.currentScene === 3 &&
              "다섯 개의 변명이 손 안에 있다. 내려놓을 것인가, 품을 것인가."}
            {scene.currentScene === 4 &&
              "파라오 앞에 섰다. 손에는 지팡이. 어떻게 할 것인가."}
            {scene.currentScene === 5 &&
              "홍해가 앞을 막는다. 단 하나의 행동이 남았다."}
            {scene.currentScene === 6 &&
              "광야 40년은 기다림의 시간이었다. 함께 가시는 분은 이미 거기 계신다."}
          </p>
        </div>
      </section>

      <section className="max-w-3xl mx-auto w-full space-y-3">
        {/* Scene 1 — cinematic (광야 40년 내레이션 본문 렌더 후 계속) */}
        {sceneType === "cinematic" && (
          <>
            {scene.currentScene === 1 && (
              <div className="px-4 py-4 rounded-lg bg-black/20 border border-[var(--color-primary)]/20">
                <p className="text-sm text-[var(--color-warm)]/90 whitespace-pre-line leading-relaxed">
                  {scene1Wilderness}
                </p>
                <div className="mt-3">
                  <NarrationAudioButton
                    text={scene1Wilderness}
                    onUnavailable="hide"
                  />
                </div>
              </div>
            )}
            <button
              onClick={() => {
                setEcho(null);
                advance(scene.currentScene, "next");
              }}
              className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
              disabled={decide.isPending}
            >
              {decide.isPending ? "..." : "계속 →"}
            </button>
          </>
        )}

        {/* Scene 2 — gesture_sequence(경외/거부 분기): 다가가기·신 벗기 뒤,
            고개를 든다(경외, 출 3:5) 또는 얼굴을 가린다(거부·머뭇거림, 출 3:6)로 갈린다. */}
        {sceneType === "gesture_sequence" && scene.currentScene === 2 && (
          <ReverenceGesture
            steps={field<OptionLike[]>("steps") ?? []}
            pending={decide.isPending}
            onComplete={(gesture) =>
              advance(scene.currentScene, { value: "completed", gesture })
            }
          />
        )}

        {/* Scene 5 — gesture_sequence(홍해, 단일 행동) */}
        {sceneType === "gesture_sequence" && scene.currentScene !== 2 && (
          <GestureSequence
            steps={field<OptionLike[]>("steps") ?? []}
            pending={decide.isPending}
            onComplete={() =>
              advance(scene.currentScene, { value: "lift_staff" })
            }
          />
        )}

        {/* Scene 3 — distribute (다섯 변명 카드 → throw/heart) */}
        {sceneType === "distribute" && (
          <ExcuseCards
            cards={
              field<Array<OptionLike & { scripture?: string }>>("cards") ?? []
            }
            slots={field<string[]>("slots") ?? ["throw", "heart"]}
            pending={decide.isPending}
            onSubmit={(assignments) =>
              advance(scene.currentScene, {
                priority: scene3AssignmentsToPattern(assignments),
                assignments,
              })
            }
          />
        )}

        {/* Scene 4 — pick_one */}
        {sceneType === "pick_one" &&
          Array.isArray(field<OptionLike[]>("options")) && (
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {(field<OptionLike[]>("options") as OptionLike[]).map((o) => (
                <button
                  key={o.id}
                  onClick={() => advance(scene.currentScene, o.id)}
                  disabled={decide.isPending}
                  className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
                >
                  {o.label}
                </button>
              ))}
            </div>
          )}

        {/* Scene 6 — outro */}
        {sceneType === "outro" && (
          <div className="text-center space-y-4">
            <p className="text-base whitespace-pre-line text-[var(--color-warm)]/90 italic max-w-prose mx-auto">
              {outroText}
            </p>
            <p className="text-[10px] not-italic text-[var(--color-warm)]/40 mt-2">
              * AI 보조 — 본문은 성경 참조 *
            </p>
            <button
              onClick={() =>
                completeMission("moses", scene.sessionId, "completed").then(
                  () => (location.href = "/"),
                )
              }
              className="px-6 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
            >
              미션 완료
            </button>
          </div>
        )}

        {decide.isError && (
          <p className="text-red-400 text-sm mt-2">
            오류: {(decide.error as Error).message}
          </p>
        )}

        <div className="pt-2 flex gap-3">
          <Link
            href="/"
            className="flex-1 flex items-center justify-center min-h-11 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
          >
            ← 홈
          </Link>
          <Link
            href="/joseph"
            className="flex-1 flex items-center justify-center min-h-11 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
          >
            Joseph 미션 →
          </Link>
        </div>
      </section>

      {history.length > 0 && (
        <details className="max-w-3xl mx-auto w-full mt-6 text-xs text-[var(--color-warm)]/40">
          <summary>진행 기록</summary>
          <pre className="overflow-x-auto">
            {JSON.stringify(history, null, 2)}
          </pre>
        </details>
      )}
    </main>
  );
}

/**
 * gesture_sequence — steps 를 순서대로 눌러 완료. 마지막 step 을 누르면 onComplete.
 * Scene 2(다가가기·신 벗기·고개 들기) 와 Scene 5(지팡이 들고 손 뻗기, 단일) 공용.
 */
function GestureSequence({
  steps,
  pending,
  onComplete,
}: {
  steps: OptionLike[];
  pending: boolean;
  onComplete: () => void;
}) {
  const [done, setDone] = useState<Set<string>>(new Set());
  const list = steps.length > 0 ? steps : [{ id: "act", label: "행동하기" }];

  const handle = (id: string, isLast: boolean) => {
    const nextDone = new Set(done);
    nextDone.add(id);
    setDone(nextDone);
    if (isLast || nextDone.size >= list.length) onComplete();
  };

  return (
    <div className="space-y-2">
      <p className="text-xs text-[var(--color-warm)]/60">
        순서대로 몸짓을 이어가세요
      </p>
      <div className="grid grid-cols-1 gap-3">
        {list.map((s, i) => {
          const isLast = i === list.length - 1;
          const already = done.has(s.id);
          return (
            <button
              key={s.id}
              onClick={() => handle(s.id, isLast)}
              disabled={pending || already}
              className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-40 text-left"
            >
              <span className="text-[var(--color-warm)]/50 mr-2">{i + 1}.</span>
              {s.label}
              {already && (
                <span className="ml-2 text-[var(--color-primary)]">✓</span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/**
 * Scene 2 gesture (경외/거부 분기) — 마지막 단계 전까지는 순서 몸짓(다가가기·신 벗기)을 밟고,
 * 마지막에 *고개를 든다*(경외, 출 3:5) 또는 *얼굴을 가린다*(거부·머뭇거림, 출 3:6) 중 하나를 고른다.
 * 두 최종 선택 모두 "3. …" 처럼 번호로 시작해, 기존 gesture e2e(번호 버튼 순차 클릭)와 호환된다.
 * yml steps 의 마지막(look_up, 고개 들기)은 경외 브랜치로 흡수하고, 얼굴 가림을 추가 브랜치로 제공한다.
 */
function ReverenceGesture({
  steps,
  pending,
  onComplete,
}: {
  steps: OptionLike[];
  pending: boolean;
  onComplete: (gesture: Scene2Gesture) => void;
}) {
  const list =
    steps.length > 0
      ? steps
      : [
          { id: "approach", label: "다가가기" },
          { id: "remove_shoes", label: "신을 벗기" },
          { id: "look_up", label: "고개 들기" },
        ];
  const [done, setDone] = useState<Set<string>>(new Set());

  // 순차 몸짓(다가가기·신 벗기·고개 들기)을 다 밟으면 = 경외(reverence)로 완료(출 3:5).
  // 마지막 step(고개 들기)이 곧 경외 행동이므로, 번호 step 을 다 누르면 자동으로 reverence 완료.
  // 이는 기존 gesture e2e(번호 버튼 순차 클릭 → 마지막에 advance)와 그대로 호환된다.
  const handleStep = (id: string, isLast: boolean) => {
    const next = new Set(done);
    next.add(id);
    setDone(next);
    if (isLast || next.size >= list.length) onComplete("reverence");
  };

  // 언제든 얼굴을 가리면(출 3:6) = 거부·머뭇거림(hesitation)으로 완료. 번호 없는 별도 버튼이라
  // 번호 버튼만 훑는 gesture e2e 에는 잡히지 않는다(회귀 없음).
  return (
    <div className="space-y-2">
      <p className="text-xs text-[var(--color-warm)]/60">
        순서대로 몸짓을 이어가세요 — 혹은, 지금이 버겁다면 얼굴을 가려도 됩니다.
      </p>
      <div className="grid grid-cols-1 gap-3">
        {list.map((s, i) => {
          const isLast = i === list.length - 1;
          const already = done.has(s.id);
          return (
            <button
              key={s.id}
              onClick={() => handleStep(s.id, isLast)}
              disabled={pending || already}
              className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-40 text-left"
            >
              <span className="text-[var(--color-warm)]/50 mr-2">{i + 1}.</span>
              {s.label}
              {already && (
                <span className="ml-2 text-[var(--color-primary)]">✓</span>
              )}
            </button>
          );
        })}
        <button
          onClick={() => onComplete("hesitation")}
          disabled={pending}
          className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/20 hover:border-[var(--color-primary)]/60 transition disabled:opacity-40 text-left text-[var(--color-warm)]/70"
        >
          얼굴을 가린다 — 뵙기가 두렵다 (출 3:6, 머뭇거림)
        </button>
      </div>
    </div>
  );
}

/**
 * distribute (Scene 3) — 다섯 변명 카드를 throw(내려놓기) / heart(품기) 로 배정.
 * 모든 카드를 배정하면 제출 버튼 활성. all_throw / all_heart / mixed 로 결말 분기.
 */
function ExcuseCards({
  cards,
  slots,
  pending,
  onSubmit,
}: {
  cards: Array<OptionLike & { scripture?: string }>;
  slots: string[];
  pending: boolean;
  onSubmit: (assignments: Record<string, "throw" | "heart">) => void;
}) {
  const [assign, setAssign] = useState<Record<string, "throw" | "heart">>({});
  const list = cards.length > 0 ? cards : [{ id: "excuse", label: "변명" }];
  const throwSlot = (slots[0] as "throw") ?? "throw";
  const heartSlot = (slots[1] as "heart") ?? "heart";
  const allAssigned = list.every((c) => assign[c.id]);

  const set = (cardId: string, slot: "throw" | "heart") =>
    setAssign((prev) => ({ ...prev, [cardId]: slot }));

  return (
    <div className="space-y-3">
      <p className="text-xs text-[var(--color-warm)]/60">
        각 변명을 <strong>내려놓기</strong> 또는 <strong>가슴에 품기</strong> 로
        배정하세요
      </p>
      <div className="space-y-2">
        {list.map((c) => (
          <div
            key={c.id}
            className="rounded-lg border border-[var(--color-primary)]/20 px-4 py-3 flex items-center justify-between gap-3"
          >
            <span className="text-sm">
              {c.label}
              {c.scripture && (
                <span className="ml-2 text-[10px] text-[var(--color-warm)]/40 uppercase">
                  {c.scripture}
                </span>
              )}
            </span>
            {/*
              두 버튼 다 `min-h-11`(44px, WCAG 2.5.5 / Apple HIG). 전에는 py-1.5 뿐이라
              320px 에서 68×30·81×30 이었다(2026-08-12 실측). 다섯 카드 × 두 개라
              열 개가 촘촘히 붙어 있어서, 작게 두면 옆 카드의 반대 선택을 누르기 쉽다 —
              이 씬은 「변명을 내려놓는가 품는가」를 고르는 곳이라 오조작이 곧 오답이다.
            */}
            <div className="flex gap-2 shrink-0">
              <button
                onClick={() => set(c.id, throwSlot)}
                disabled={pending}
                className={`min-h-11 px-3 py-1.5 rounded-md border text-xs transition ${
                  assign[c.id] === "throw"
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/20"
                    : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
                }`}
              >
                내려놓기
              </button>
              <button
                onClick={() => set(c.id, heartSlot)}
                disabled={pending}
                className={`min-h-11 px-3 py-1.5 rounded-md border text-xs transition ${
                  assign[c.id] === "heart"
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/20"
                    : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
                }`}
              >
                가슴에 품기
              </button>
            </div>
          </div>
        ))}
      </div>
      <button
        onClick={() => onSubmit(assign)}
        disabled={pending || !allAssigned}
        className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
      >
        {allAssigned ? "결정 →" : "모든 변명을 배정하세요"}
      </button>
    </div>
  );
}

/**
 * 직전 결정 (sceneId + decision) → echo 텍스트. Scene 3 은 Scene 6 결말 톤용 pattern 도 반환.
 */
function buildLocalEcho(
  fromScene: number,
  decision: unknown,
): { text: string; scene3Pattern?: Scene3Pattern } | null {
  if (fromScene === 2) {
    // gesture 결과에 따라 경외(고개 듦, 출 3:5) / 거부·머뭇거림(얼굴 가림, 출 3:6) 분기.
    const gesture = readGesture(decision);
    return { text: scene2Monologues[gesture] };
  }
  if (fromScene === 3) {
    const priority = readPriority(decision);
    const pattern: Scene3Pattern =
      priority === "all_throw" || priority === "all_heart" ? priority : "mixed";
    // 카드별 떨기나무 본문 응답(§3.4)을 outcome 뒤에 잇는다.
    const assignments = readAssignments(decision);
    return {
      text: buildScene3Echo(pattern, assignments),
      scene3Pattern: pattern,
    };
  }
  if (fromScene === 4) {
    const key = readValue(decision) as Scene4Choice | null;
    if (key && key in scene4Reactions) return { text: scene4Reactions[key] };
    return null;
  }
  if (fromScene === 5) {
    return { text: scene5Monologue.lift_staff };
  }
  return null;
}

function readPriority(decision: unknown): string | null {
  if (typeof decision === "object" && decision !== null) {
    const p = (decision as { priority?: unknown }).priority;
    if (typeof p === "string") return p;
  }
  return null;
}

/** Scene 2 gesture 결과 → reverence/hesitation. 불명확하면 reverence(경외) fallback. */
function readGesture(decision: unknown): Scene2Gesture {
  if (typeof decision === "object" && decision !== null) {
    const g = (decision as { gesture?: unknown }).gesture;
    if (g === "hesitation" || g === "reverence") return g;
  }
  return "reverence";
}

/** Scene 3 배정(카드 id → throw/heart) 추출. 없으면 빈 객체. */
function readAssignments(decision: unknown): Record<string, "throw" | "heart"> {
  if (typeof decision === "object" && decision !== null) {
    const a = (decision as { assignments?: unknown }).assignments;
    if (a && typeof a === "object") {
      return a as Record<string, "throw" | "heart">;
    }
  }
  return {};
}

function readValue(decision: unknown): string | null {
  if (typeof decision === "string") return decision;
  if (typeof decision === "object" && decision !== null) {
    const v = (decision as { value?: unknown }).value;
    if (typeof v === "string") return v;
  }
  return null;
}
