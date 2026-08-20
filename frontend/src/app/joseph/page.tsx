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
import { ScenePassage } from "@/components/ScenePassage";
import { CrisisReminder } from "@/components/CrisisReminder";
import {
  TriggerWarningGate,
  readTriggerWarning,
} from "@/components/TriggerWarningGate";

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
  const [scene3Pattern, setScene3Pattern] = useState<Scene3Pattern | null>(
    null,
  );
  /*
    R4 — trigger_warning 이 있는 씬은 동의 전 상호작용을 렌더하지 않는다.

    2026-08-12 까지 이 화면에는 **동의 카드가 아예 없었다.** joseph.yml Scene 4(형제 재회)는
    level medium · content [betrayal, family_trauma] · skip_alternative_scene_id 5 를
    선언해 두고 있었는데, 화면에는 경고도 건너뛰기도 없어 가족 배신 트라우마 씬으로
    곧장 들어갔다. yml 은 건너뛸 길까지 마련해 뒀는데 그 문이 화면에 없었다.
    job·elijah·solomon 은 같은 필드를 이미 읽고 있었으므로 "규약이 아직 없어서" 가
    아니라 이 화면만 빠져 있었던 것이다.
  */
  const [consented, setConsented] = useState(false);

  const start = useMutation({
    mutationFn: () => startJoseph("web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideJoseph(scene!.sessionId, sceneId, decision),
    onSuccess: (d, vars) => {
      // 결정 직후 — backend 의 responseText 우선, 없으면 frontend hardcode fallback.
      // Phase 2-A 마이그레이션 패턴: 점진 전환, frontend fallback 은 backend 가 매칭 못
      // 하는 경우의 안전망 (예: yml 에 monologues 추가 전).
      const localFallback = buildLocalEcho(vars.sceneId, vars.decision);
      const text = d.responseText ?? localFallback?.text ?? null;
      if (text) setEcho({ fromScene: vars.sceneId, text });
      if (localFallback?.scene3Pattern)
        setScene3Pattern(localFallback.scene3Pattern);

      setConsented(false); // R4 — 다음 씬 진입 시 동의 게이트 초기화
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
    rawType === "interaction"
      ? ((payload.interaction as string) ?? "")
      : rawType;

  /*
    ScenePayloadAssembler 는 yml 의 표준 필드는 payload 최상위로 펼치고, `extras:`
    블록은 `payload.extras` 아래에 그대로 둔다. joseph.yml 의 `crisis_reminder` 는
    extras 안이므로 최상위만 보면 못 찾는다. 다른 화면(elijah·job·solomon)이
    `extras[key] ?? payload[key]` 순으로 보는 것과 같은 이유다.
  */
  const extras = (payload.extras as Record<string, unknown>) ?? {};
  const crisisReminder = (extras.crisis_reminder ?? payload.crisis_reminder) as
    string | undefined;

  // 씬마다 선언된 성경 참조. extras 가 아니라 payload 최상위다
  // (ScenePayloadAssembler.build — `sc.scriptureRef?.let { p["scriptureRef"] = it }`).
  const scriptureRef = payload.scriptureRef as string | undefined;
  // 절 단위 관례상 편 전체를 쓰는 씬은 나머지 절을 `additional_refs` 로 편다.
  // 이 화면에는 공용 field 헬퍼가 없어 extras 를 직접 읽는다(joseph.yml 은 현재
  // 이 키를 쓰지 않지만, 저작이 붙는 날 화면이 조용히 1절만 띄우지 않도록 배선해 둔다).
  const additionalRefs = (extras.additional_refs ?? payload.additional_refs) as
    string[] | undefined;

  // R4 — 게이트 여부는 payload 가 정한다. 씬 번호를 조건으로 쓰지 않는다:
  // yml 이 다른 씬에 경고를 붙이면 그 씬도 자동으로 닫혀야 한다.
  const warning = readTriggerWarning(payload);
  const needsConsent = !!warning && !consented;

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Joseph — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      {/*
        R1 — yml 이 선언하고 백엔드가 정본 번호로 치환해 내려보낸 위기 안내.
        읽지 않으면 outro 에 위기 자원이 한 줄도 뜨지 않는다(2026-08-14 까지 이
        화면이 그랬다). `check_frontend_hotline.py` 는 "번호가 하드코딩됐나" 만
        보므로 없는 것은 잡지 못한다.
      */}
      <CrisisReminder text={crisisReminder} />

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
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative aspect-video bg-cover bg-center bg-stone-900"
        style={{
          backgroundImage: `url(/images/scenes/joseph/${scene.currentScene}.webp)`,
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-black/20 via-transparent to-black/70" />
        <div className="relative h-full flex items-end p-5">
          <p className="text-sm text-[var(--color-warm)]/80 italic max-w-prose">
            {sceneType === "cinematic" &&
              "파라오의 꿈을 해석한다. 7년 풍년과 7년 흉년이 다가온다."}
            {sceneType === "pick_one" &&
              scene.currentScene === 2 &&
              "이집트 전체 곡식 중 얼마를 저장할 것인가?"}
            {sceneType === "distribute" &&
              "흉년 — 농민·이주민·상인 중 어느 줄에 곡식을 먼저 줄 것인가?"}
            {sceneType === "pick_one" &&
              scene.currentScene === 4 &&
              "형제들이 곡식을 구하러 왔다. 어떻게 응대할 것인가?"}
            {sceneType === "outro" &&
              "여기까지 온 당신의 결정이 누군가의 양식이 된다."}
          </p>
        </div>
      </section>

      <section className="max-w-3xl mx-auto w-full space-y-3">
        {needsConsent && warning ? (
          /* R4 동의 게이트. 동의 전에는 이 씬의 선택지를 렌더하지 않는다.
             문구는 화면이 소유한다 — joseph.yml 에 consent_card_ko 가 없다.
             내용은 그 씬의 forgiveness_note("정답 없음 / 즉시 용서 못 함은 결함 아님 /
             현실의 학대·배신 상황에선 화해보다 안전이 우선")에 맞춘다. */
          <TriggerWarningGate
            warning={warning}
            pending={decide.isPending}
            continueLabel="준비됐어요 · 들어갈게요"
            fallbackProse={
              <p>
                다음 장면은 <strong>가족의 배신과 재회</strong> 를 다룹니다.
                형제들이 다시 앞에 섭니다. 비슷한 일을 겪었다면 지금이 버거울 수
                있습니다 — <strong>건너뛰어도 괜찮습니다</strong>. 건너뛰어도
                이야기는 온전히 이어집니다. 어느 선택도 정답이 아니고, 지금 바로
                용서하지 못하는 마음은 믿음의 결함이 아닙니다.
              </p>
            }
            onContinue={() => setConsented(true)}
            onSkip={() =>
              decide.mutate({
                sceneId: scene.currentScene,
                decision: { value: "skip" },
              })
            }
          />
        ) : (
          <>
            {/* 성경 본문 — 동의 게이트 *안쪽* 이다(Scene 4 는 배신·재회 경고를 단다). */}
            <ScenePassage
              reference={scriptureRef}
              additional={additionalRefs}
            />

            {sceneType === "cinematic" && (
              <>
                {/* Scene 1 성육/꿈 내레이션 본문 — 캡션만이 아니라 실제 대본을 렌더 */}
                {scene.currentScene === 1 && (
                  <div className="px-4 py-4 rounded-lg bg-black/20 border border-[var(--color-primary)]/20">
                    <p className="text-sm text-[var(--color-warm)]/90 whitespace-pre-line leading-relaxed">
                      {scene1Dream}
                    </p>
                    <div className="mt-3">
                      <NarrationAudioButton
                        text={scene1Dream}
                        onUnavailable="hide"
                      />
                    </div>
                  </div>
                )}
                <button
                  onClick={() => {
                    setEcho(null); // cinematic → 다음 진입 시 echo 클리어
                    decide.mutate({
                      sceneId: scene.currentScene,
                      decision: "next",
                    });
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
                {(payload.options as Array<{ id: string; label: string }>).map(
                  (o) => (
                    <button
                      key={o.id}
                      onClick={() =>
                        decide.mutate({
                          sceneId: scene.currentScene,
                          decision: o.id,
                        })
                      }
                      disabled={decide.isPending}
                      className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
                    >
                      {o.label}
                    </button>
                  ),
                )}
              </div>
            )}

            {sceneType === "distribute" && Array.isArray(payload.queues) && (
              <div className="space-y-2">
                <p className="text-xs text-[var(--color-warm)]/60">
                  한 줄에 모든 곡식을 우선 분배할 줄 선택
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                  {(payload.queues as Array<{ id: string; label: string }>).map(
                    (q) => (
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
                    ),
                  )}
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
                    completeJoseph(scene.sessionId, "completed").then(
                      () => (location.href = "/"),
                    )
                  }
                  className="px-6 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
                >
                  미션 완료
                </button>
              </div>
            )}
          </>
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
          <pre className="overflow-x-auto">
            {JSON.stringify(history, null, 2)}
          </pre>
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
