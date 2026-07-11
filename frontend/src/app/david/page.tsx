"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";
import {
  scene1Psalm23,
  scene2Reactions,
  scene4LastStone,
  scene5Monologue,
  scene6OutroByLastStone,
  lastStoneOf,
  type Scene2Choice,
  type Scene4LastStone,
} from "@/lib/content/david-monologues";

/**
 * David 미션 — Phase 2 완전 활성 (요셉·모세 동급).
 *
 * Scene 1 contemplative(양 떼·시편, 계속) → Scene 2 pick_one(형의 비웃음: 반박/침묵/전선) →
 * Scene 3 gesture_sequence(사울 갑옷: 입기/걷기/벗기) → Scene 4 distribute(시냇가 5돌 = 5감정,
 * 마지막에 쥔 돌로 결말 분기) → Scene 5 two_handed_throw(골리앗, 단일 나아감) → Scene 6 outro
 * (회복 · 일기 라우팅) → complete → 홈.
 *
 * david.yml 은 Scene 1 이 `type: interaction / interaction: contemplative` 이다 (moses/joseph
 * 의 cinematic 과 다름). 씬 type 에 따라 "계속" 버튼 유무를 분기한다.
 *
 * echo(직전 결정 모놀로그) 는 david-monologues.ts 의 frontend fallback 이 담당한다 —
 * backend 는 david.yml 에 monologues/outcomes/reactions map 이 없어 responseText=null.
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

export default function DavidPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);
  const [echo, setEcho] = useState<DecisionEcho | null>(null);
  const [lastStone, setLastStone] = useState<Scene4LastStone | null>(null);
  // R4 — Scene 5(골리앗 폭력·PULSE_HARD 햅틱) 진입 전 정서 부담 경고 카드 동의 게이트.
  // david.yml Scene 5 extras.violence_warning=true 를 프론트가 실제로 소비하는 지점.
  // 동의(들어간다) 전에는 throw 버튼을 렌더하지 않으며, 건너뛰기는 화면 위에 노출한다.
  const [violenceConsented, setViolenceConsented] = useState(false);

  const start = useMutation({
    mutationFn: () => startMission("david", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({ sceneId, decision }: { sceneId: number; decision: unknown }) =>
      decideMission("david", scene!.sessionId, sceneId, decision),
    onSuccess: (d, vars) => {
      // backend responseText 우선, 없으면 frontend fallback (david 는 항상 fallback).
      const local = buildLocalEcho(vars.sceneId, vars.decision);
      const text = d.responseText ?? local?.text ?? null;
      if (text) setEcho({ fromScene: vars.sceneId, text });
      if (local?.lastStone) setLastStone(local.lastStone);
      setViolenceConsented(false); // R4 — 다음 씬 진입 시 동의 게이트 초기화
      setScene(d);
      setHistory((h) => [...h, JSON.stringify(d.scenePayload.title)]);
    },
  });

  useEffect(() => {
    if (!scene && !start.isPending && !start.isError) start.mutate();
  }, [scene, start]);

  // Scene 6 (outro) 진입 시 마지막으로 쥔 돌(감정) 기반 결말 톤
  const outroText = useMemo(() => {
    if (scene?.currentScene !== 6) return null;
    return lastStone
      ? scene6OutroByLastStone[lastStone]
      : scene6OutroByLastStone.trust; // fallback — 신뢰
  }, [scene?.currentScene, lastStone]);

  if (!scene) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6">
        <p className="text-[var(--color-warm)]/60">세션 시작 중...</p>
      </main>
    );
  }

  const payload = scene.scenePayload as Record<string, unknown>;
  const title = (payload.title as string) ?? "Scene";
  const rawType = (payload.type as string) ?? "";
  const interaction = (payload.interaction as string) ?? "";
  const sceneType = rawType === "interaction" ? interaction : rawType;

  // david.yml 은 interaction 데이터(options/steps/stones)를 씬의 `extras:` 블록 안에
  // 중첩한다 (moses.yml 과 동일). loader 가 그 블록을 통째로 payload.extras 로 넘기므로,
  // extras 를 우선 읽고 top-level 을 fallback.
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
          David — Scene {scene.currentScene}/6 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      {/* Decision echo — 직전 결정의 모놀로그 / 아웃컴 */}
      {echo && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 italic text-sm text-[var(--color-warm)]/90">
          <p className="whitespace-pre-line">{echo.text}</p>
          <p className="text-[10px] not-italic text-[var(--color-warm)]/40 mt-2 text-right">
            * AI 보조 — 본문은 성경 참조 *
          </p>
        </section>
      )}

      {/* Scene 배경 — david 전용 이미지 없으면 grad placeholder */}
      <section className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative aspect-video bg-gradient-to-b from-stone-900 via-stone-800 to-stone-950">
        <div className="absolute inset-0 flex items-end p-5">
          <p className="text-sm text-[var(--color-warm)]/80 italic max-w-prose">
            {scene.currentScene === 1 && "양 떼 곁의 새벽. 어린 시인 다윗의 수금이 울린다 — 시편 23편이 흐른다."}
            {scene.currentScene === 2 && "형 엘리압이 비웃는다. 모욕이 가슴을 스친다 — 어떻게 응답할 것인가."}
            {scene.currentScene === 3 && "사울이 자기 갑옷을 입힌다. 무겁고, 낯설다. 남의 갑옷인가, 나의 걸음인가."}
            {scene.currentScene === 4 && "시냇가에서 다섯 돌을 고른다 — 공포·모욕·외로움·신뢰·기도. 무엇을 마지막으로 쥘 것인가."}
            {scene.currentScene === 5 && "골리앗이 앞을 막는다. 떨림 속에, 단 하나의 나아감이 남았다."}
            {scene.currentScene === 6 && "거인은 넘어졌다. 그러나 오늘의 너를 재촉하지 않는다 — 솔직한 한 줄이 남았다."}
          </p>
        </div>
      </section>

      <section className="max-w-3xl mx-auto w-full space-y-3">
        {/* Scene 1 — contemplative (시편 23 도입 내레이션 렌더 후 계속) */}
        {sceneType === "contemplative" && (
          <>
            {scene.currentScene === 1 && (
              <p className="px-4 py-4 rounded-lg bg-black/20 border border-[var(--color-primary)]/20 text-sm text-[var(--color-warm)]/90 whitespace-pre-line leading-relaxed">
                {scene1Psalm23}
              </p>
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

        {/* Scene 2 — pick_one (형의 비웃음) */}
        {sceneType === "pick_one" && Array.isArray(field<OptionLike[]>("options")) && (
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

        {/* Scene 3 — gesture_sequence (사울 갑옷) */}
        {sceneType === "gesture_sequence" && (
          <GestureSequence
            steps={field<OptionLike[]>("steps") ?? []}
            pending={decide.isPending}
            onComplete={() => advance(scene.currentScene, { value: "armor_removed" })}
          />
        )}

        {/* Scene 4 — distribute (5돌 = 5감정, 집는 순서 기록) */}
        {sceneType === "distribute" && (
          <StonePicker
            stones={field<OptionLike[]>("stones") ?? []}
            pending={decide.isPending}
            onComplete={(order) =>
              advance(scene.currentScene, {
                priority: `last_${lastStoneOf(order)}`,
                order,
              })
            }
          />
        )}

        {/* Scene 5 — two_handed_throw (골리앗, 단일 나아감) */}
        {sceneType === "two_handed_throw" &&
          field<boolean>("violence_warning") === true &&
          !violenceConsented && (
            <div className="space-y-3 px-4 py-4 rounded-lg border border-[var(--color-primary)]/40 bg-black/30">
              <p className="text-sm text-[var(--color-warm)]/90">
                이 장면에는 거인 앞의 대치와 강한 심장 박동(진동) 연출이 있습니다.
                지금이 버겁다면 이 장면은 <strong>건너뛰어도 괜찮습니다</strong> — 건너뛰어도
                결말과 오늘의 한 줄은 그대로 이어집니다.
              </p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <button
                  onClick={() => setViolenceConsented(true)}
                  disabled={decide.isPending}
                  className="px-4 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
                >
                  준비됐어요 · 들어갈게요
                </button>
                <button
                  onClick={() => advance(scene.currentScene, { value: "skip" })}
                  disabled={decide.isPending}
                  className="px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm text-[var(--color-warm)]/90"
                >
                  이 장면은 건너뛸게요 →
                </button>
              </div>
            </div>
          )}
        {sceneType === "two_handed_throw" &&
          (field<boolean>("violence_warning") !== true || violenceConsented) && (
            <button
              onClick={() => advance(scene.currentScene, { value: "throw" })}
              disabled={decide.isPending}
              className="w-full py-4 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
            >
              {decide.isPending ? "..." : "물맷돌을 던진다 →"}
            </button>
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
                completeMission("david", scene.sessionId, "completed").then(
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
            className="flex-1 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
          >
            ← 홈
          </Link>
          <Link
            href="/joseph"
            className="flex-1 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
          >
            Joseph 미션 →
          </Link>
        </div>
      </section>

      {history.length > 0 && (
        <details className="max-w-3xl mx-auto w-full mt-6 text-xs text-[var(--color-warm)]/40">
          <summary>진행 기록</summary>
          <pre className="overflow-x-auto">{JSON.stringify(history, null, 2)}</pre>
        </details>
      )}
    </main>
  );
}

/**
 * gesture_sequence (Scene 3) — steps 를 순서대로 눌러 완료. 마지막 step 을 누르면 onComplete.
 * 사울 갑옷: 입어본다 → 걸어본다(비틀거림) → 벗는다.
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
      <p className="text-xs text-[var(--color-warm)]/60">순서대로 몸짓을 이어가세요</p>
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
              {already && <span className="ml-2 text-[var(--color-primary)]">✓</span>}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/**
 * distribute (Scene 4) — 다섯 돌(감정)을 순서대로 집는다. 집는 *순서* 를 기록하고,
 * 다섯 개를 모두 집으면 제출. 마지막으로 집은 돌이 Scene 6 결말 톤을 결정한다.
 * 어느 감정도 틀리지 않다 — 다윗은 다섯을 다 가지고 나아갔다.
 */
function StonePicker({
  stones,
  pending,
  onComplete,
}: {
  stones: OptionLike[];
  pending: boolean;
  onComplete: (order: string[]) => void;
}) {
  const [order, setOrder] = useState<string[]>([]);
  const list =
    stones.length > 0
      ? stones
      : [
          { id: "fear", label: "공포" },
          { id: "humiliation", label: "모욕" },
          { id: "loneliness", label: "외로움" },
          { id: "trust", label: "신뢰" },
          { id: "prayer", label: "기도" },
        ];
  const allPicked = order.length >= list.length;

  const pick = (id: string) => {
    if (order.includes(id)) return;
    setOrder((prev) => [...prev, id]);
  };

  return (
    <div className="space-y-3">
      <p className="text-xs text-[var(--color-warm)]/60">
        다섯 돌을 하나씩 집어 자루에 담으세요. <strong>마지막으로 쥔 감정</strong> 이 오늘의 한 줄을 엽니다.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {list.map((s) => {
          const idx = order.indexOf(s.id);
          const picked = idx >= 0;
          return (
            <button
              key={s.id}
              onClick={() => pick(s.id)}
              disabled={pending || picked}
              className={`px-4 py-4 rounded-lg border text-left transition disabled:cursor-default ${
                picked
                  ? "border-[var(--color-primary)] bg-[var(--color-primary)]/15 opacity-80"
                  : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
              }`}
            >
              {s.label}
              {picked && (
                <span className="ml-2 text-[var(--color-primary)] text-xs">
                  {idx + 1}번째 ✓
                </span>
              )}
            </button>
          );
        })}
      </div>
      <button
        onClick={() => onComplete(order)}
        disabled={pending || !allPicked}
        className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
      >
        {allPicked ? "다섯 돌을 들고 나아간다 →" : "다섯 돌을 모두 집으세요"}
      </button>
    </div>
  );
}

/**
 * 직전 결정 (sceneId + decision) → echo 텍스트. Scene 4 는 Scene 6 결말 톤용 lastStone 도 반환.
 */
function buildLocalEcho(
  fromScene: number,
  decision: unknown,
): { text: string; lastStone?: Scene4LastStone } | null {
  if (fromScene === 2) {
    const key = readValue(decision) as Scene2Choice | null;
    if (key && key in scene2Reactions) return { text: scene2Reactions[key] };
    return null;
  }
  if (fromScene === 4) {
    const priority = readPriority(decision);
    const stone = (priority?.replace(/^last_/, "") ?? null) as Scene4LastStone | null;
    if (stone && stone in scene4LastStone) {
      return { text: scene4LastStone[stone], lastStone: stone };
    }
    return null;
  }
  if (fromScene === 5) {
    // 건너뛰기(skip) 시엔 폭력 묘사 echo 를 띄우지 않는다 (R4).
    if (readValue(decision) === "skip") return null;
    return { text: scene5Monologue.throw };
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

function readValue(decision: unknown): string | null {
  if (typeof decision === "string") return decision;
  if (typeof decision === "object" && decision !== null) {
    const v = (decision as { value?: unknown }).value;
    if (typeof v === "string") return v;
  }
  return null;
}
