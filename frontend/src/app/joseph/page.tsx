"use client";
import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  startJoseph,
  decideJoseph,
  completeJoseph,
  type JosephStartResponse,
} from "@/lib/api/game";
import {
  scene1Dream,
  scene2Monologues,
  scene3Outcomes,
  scene4Reactions,
  scene5OutroByScene3,
  scene3DecisionToPattern,
  type Scene2Choice,
  type Scene3Pattern,
  type Scene4Choice,
} from "@/lib/content/joseph-monologues";
import { NarrationAudioButton } from "@/components/NarrationAudioButton";
import { SceneBootState } from "@/components/SceneBootState";

type Scene = JosephStartResponse;

/**
 * 직전 결정에 대한 *모놀로그/아웃컴* 메모리. Scene 진입 시 상단 quote 영역에 표시.
 * 자문 통과 후 backend 가 LLM 응답으로 교체하면 본 상태는 제거 가능 (shape 동일).
 */
interface DecisionEcho {
  fromScene: number;
  text: string;
}

export default function JosephPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);
  const [echo, setEcho] = useState<DecisionEcho | null>(null);
  const [scene3Pattern, setScene3Pattern] = useState<Scene3Pattern | null>(null);

  const start = useMutation({
    mutationFn: () => startJoseph("web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({ sceneId, decision }: { sceneId: number; decision: unknown }) =>
      decideJoseph(scene!.sessionId, sceneId, decision),
    onSuccess: (d, vars) => {
      // 결정 직후 — backend 의 responseText 우선, 없으면 frontend hardcode fallback.
      // Phase 2-A 마이그레이션 패턴: 점진 전환, frontend fallback 은 backend 가 매칭 못
      // 하는 경우의 안전망 (예: yml 에 monologues 추가 전).
      const localFallback = buildLocalEcho(vars.sceneId, vars.decision);
      const text = d.responseText ?? localFallback?.text ?? null;
      if (text) setEcho({ fromScene: vars.sceneId, text });
      if (localFallback?.scene3Pattern) setScene3Pattern(localFallback.scene3Pattern);

      setScene(d);
      setHistory((h) => [...h, JSON.stringify(d.scenePayload.title)]);
    },
  });

  useEffect(() => {
    if (!scene && !start.isPending && !start.isError) start.mutate();
  }, [scene, start]);

  // Scene 5 (outro) 진입 시 Scene 3 패턴 기반 결말 톤 적용
  const outroText = useMemo(() => {
    if (scene?.currentScene !== 5) return null;
    return scene3Pattern
      ? scene5OutroByScene3[scene3Pattern]
      : scene5OutroByScene3.farmer_first; // fallback
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
  const sceneType =
    rawType === "interaction" ? ((payload.interaction as string) ?? "") : rawType;

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Joseph — Scene {scene.currentScene}/5 · Mode: VR
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

      {/* Scene 배경 이미지 */}
      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative aspect-video bg-cover bg-center"
        style={{ backgroundImage: `url(/images/scenes/${scene.currentScene}.jpg)` }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-black/20 via-transparent to-black/70" />
        <div className="relative h-full flex items-end p-5">
          <p className="text-sm text-[var(--color-warm)]/80 italic max-w-prose">
            {sceneType === "cinematic" && "파라오의 꿈을 해석한다. 7년 풍년과 7년 흉년이 다가온다."}
            {sceneType === "pick_one" && scene.currentScene === 2 && "이집트 전체 곡식 중 얼마를 저장할 것인가?"}
            {sceneType === "distribute" && "흉년 — 농민·이주민·상인 중 어느 줄에 곡식을 먼저 줄 것인가?"}
            {sceneType === "pick_one" && scene.currentScene === 4 && "형제들이 곡식을 구하러 왔다. 어떻게 응대할 것인가?"}
            {sceneType === "outro" && "여기까지 온 당신의 결정이 누군가의 양식이 된다."}
          </p>
        </div>
      </section>

      <section className="max-w-3xl mx-auto w-full space-y-3">
        {sceneType === "cinematic" && (
          <>
            {/* Scene 1 성육/꿈 내레이션 본문 — 캡션만이 아니라 실제 대본을 렌더 */}
            {scene.currentScene === 1 && (
              <div className="px-4 py-4 rounded-lg bg-black/20 border border-[var(--color-primary)]/20">
                <p className="text-sm text-[var(--color-warm)]/90 whitespace-pre-line leading-relaxed">
                  {scene1Dream}
                </p>
                <div className="mt-3">
                  <NarrationAudioButton text={scene1Dream} onUnavailable="hide" />
                </div>
              </div>
            )}
            <button
              onClick={() => {
                setEcho(null); // cinematic → 다음 진입 시 echo 클리어
                decide.mutate({ sceneId: scene.currentScene, decision: "next" });
              }}
              className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
              disabled={decide.isPending}
            >
              {decide.isPending ? "..." : "계속 →"}
            </button>
          </>
        )}

        {sceneType === "pick_one" && Array.isArray(payload.options) && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {(payload.options as Array<{ id: string; label: string }>).map((o) => (
              <button
                key={o.id}
                onClick={() => decide.mutate({ sceneId: scene.currentScene, decision: o.id })}
                disabled={decide.isPending}
                className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
              >
                {o.label}
              </button>
            ))}
          </div>
        )}

        {sceneType === "distribute" && Array.isArray(payload.queues) && (
          <div className="space-y-2">
            <p className="text-xs text-[var(--color-warm)]/60">
              한 줄에 모든 곡식을 우선 분배할 줄 선택
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {(payload.queues as Array<{ id: string; label: string }>).map((q) => (
                <button
                  key={q.id}
                  onClick={() =>
                    decide.mutate({
                      sceneId: scene.currentScene,
                      decision: { priority: q.id },
                    })
                  }
                  disabled={decide.isPending}
                  className="px-4 py-5 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
                >
                  {q.label}
                </button>
              ))}
            </div>
          </div>
        )}

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
                completeJoseph(scene.sessionId, "completed").then(() => (location.href = "/"))
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
 * 직전 결정 (vars.sceneId + vars.decision) → 사용자에게 보여줄 *모놀로그 또는 아웃컴* 매핑.
 * Scene 3 의 경우 Scene 5 결말 톤 결정에 쓸 *pattern* 도 함께 반환.
 */
function buildLocalEcho(
  fromScene: number,
  decision: unknown,
): { text: string; scene3Pattern?: Scene3Pattern } | null {
  if (fromScene === 2 && typeof decision === "string") {
    const text = scene2Monologues[decision as Scene2Choice];
    return text ? { text } : null;
  }
  if (fromScene === 3) {
    const pattern = scene3DecisionToPattern(decision);
    if (!pattern) return null;
    return { text: scene3Outcomes[pattern], scene3Pattern: pattern };
  }
  if (fromScene === 4 && typeof decision === "string") {
    const text = scene4Reactions[decision as Scene4Choice];
    return text ? { text } : null;
  }
  return null;
}
