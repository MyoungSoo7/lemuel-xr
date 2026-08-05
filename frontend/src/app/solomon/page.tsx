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
import { SceneBootState } from "@/components/SceneBootState";

/**
 * Solomon 미션 — 해 아래, 빈 손. 백엔드 runtime 계약(backend/src/main/resources/scenarios/solomon.yml)
 * 은 2026-08-02 부터 살아 있었는데 들어갈 *문* 이 없었다. 이 페이지가 그 문이다.
 *
 * Scene 1 grab_and_place(기브온 일천번제 — 제물은 비핵심·optional) → Scene 2 cinematic(꿈의 응답) →
 * Scene 3 pick_one(두 여인 재판, R4 동의 게이트) → Scene 4 pick_one(헛되다 — 라벨링은 *초대*,
 * 미선택 허용, R4 동의 게이트) → Scene 5 outro(내려놓기 비강제 + 재정향) → complete → 홈.
 *
 * 이 페이지가 다른 인물 페이지와 다르게 하는 것 세 가지 — 전부 yml 이 이미 말하고 있던 것을
 * 프론트가 *실제로 소비* 하게 만든 지점이다:
 *
 * 1) 대사(script beat)를 페이지에 하드코딩하지 않는다. david/jesus 페이지는 씬 배경 카피를
 *    프론트에 적어 뒀지만, solomon.yml 은 모든 씬에 `script:` 비트를 갖고 있고 payload 로
 *    그대로 내려온다. 저작 정본을 한 곳에만 두려고 payload 를 렌더한다 (프론트 사본 0개).
 *
 * 2) R4 동의 카드도 payload 의 `trigger_warning.consent_card_ko` 를 렌더한다. 이 문자열은
 *    서버의 CrisisTokenResolver 가 `{{crisis_resources.default}}` 를 정본 번호로 치환해서
 *    보낸다 — 프론트에 번호를 적으면 정책 개정 때 낡은 번호가 남는다(ScenarioHotlineRatchetTest
 *    가 yml 쪽을 막는 것과 같은 이유).
 *
 * 3) Scene 3 의 `converges_to` 는 **엔진이** 집행한다(SceneConvergenceResolver).
 *    first_woman/second_woman 은 오답이 아니라 *재고* 이고, 엔진이 같은 씬을 그대로
 *    돌려주면서 재고 텍스트를 responseText 로 준다. 프론트는 응답을 따르기만 한다.
 *    (예전에는 이 페이지가 decide 호출 자체를 건너뛰어 국소 집행했다 — 그러면 이
 *    웹페이지 밖의 클라이언트에는 수렴 보호가 없었다.)
 *
 * responseText 는 solomon 의 정적 lookup 에서는 항상 null 이다 (solomon.yml 에
 * monologues/outcomes/reactions map 이 없음 — david 와 같은 상황). 유일한 예외가 위
 * 3) 의 재고 텍스트다. echo 는 payload 안의 정본 문자열만 쓰고, 프론트가 새 문장을
 * 지어내지 않는다.
 */
type Scene = JosephStartResponse;

interface OptionLike {
  id: string;
  label: string;
}

interface ScriptBeat {
  at_sec?: number;
  beat?: string;
  text?: string;
  ref?: string;
}

interface TriggerWarning {
  level?: string;
  consent_card_ko?: string;
  skip_alternative_scene_id?: number;
}

interface LaydownObject {
  id: string;
  label: string;
  meaning?: string;
  /** always | scene3_completed | scene3_skipped | scene4_visited */
  appears?: string;
}

type HevelLabel = "emptiness" | "restlessness" | "loss_of_meaning";

/**
 * users.faith_tone (V3__expand_identity_emotion.sql, DEFAULT 'balanced') 이 재정향 문구
 * 9조합의 두 번째 축인데, 게스트 세션에는 이 값을 프론트로 내려주는 엔드포인트가 아직 없다.
 * DB 기본값과 같은 'balanced' 로 고정한다 — 임의 선택이 아니라 서버 기본값 미러링이다.
 */
const DEFAULT_FAITH_TONE = "balanced" as const;

export default function SolomonPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [echo, setEcho] = useState<string | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬(3·4)은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  // Scene 3 intensity_toggle 소비 — captions_only 면 나레이션 오디오를 노출하지 않는다.
  const [captionsOnly, setCaptionsOnly] = useState(false);

  // Scene 3 converges_to 로컬 집행 — 재고 텍스트를 띄우고 같은 씬에 머문다.
  const [reconsider, setReconsider] = useState<string | null>(null);

  // Scene 5 laydown 오브젝트 조건부 등장에 필요한 경로 상태
  const [scene3Skipped, setScene3Skipped] = useState(false);
  const [scene4Visited, setScene4Visited] = useState(false);
  const [hevelLabel, setHevelLabel] = useState<HevelLabel | null>(null);
  const [laidDown, setLaidDown] = useState<Set<string>>(new Set());

  const start = useMutation({
    mutationFn: () => startMission("solomon", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("solomon", scene!.sessionId, sceneId, decision),
    onSuccess: (d, vars) => {
      // 엔진이 같은 씬을 돌려주면 = `converges_to` 재고다. 씬이 안 바뀌었으므로
      // 동의 상태를 초기화하면 안 된다 — 초기화하면 R4 동의 카드가 씬 도중에 다시 뜬다.
      const stayed = d.currentScene === vars.sceneId;
      setScene(d);
      if (stayed) {
        setReconsider(d.responseText ?? null);
        return;
      }
      setConsented(false);
      setReconsider(null);
      setHistory((h) => [
        ...h,
        String((d.scenePayload as Record<string, unknown>).title ?? ""),
      ]);
    },
  });

  useEffect(() => {
    if (!scene && !start.isPending && !start.isError) start.mutate();
  }, [scene, start]);

  const payload = (scene?.scenePayload ?? {}) as Record<string, unknown>;
  const extras = (payload.extras as Record<string, unknown> | undefined) ?? {};
  const field = <T,>(key: string): T | undefined =>
    (extras[key] as T | undefined) ?? (payload[key] as T | undefined);

  // Scene 5 재정향 — hevel_label × faith_tone 9 조합 + 라벨 없음 default (전부 payload 정본).
  const reorientation = useMemo(() => {
    if (scene?.currentScene !== 5) return null;
    const texts = extras.reorientation_texts as
      Record<string, string | Record<string, string>> | undefined;
    if (!texts) return null;
    if (hevelLabel) {
      const byTone = texts[hevelLabel];
      if (byTone && typeof byTone === "object") {
        const t = byTone[DEFAULT_FAITH_TONE];
        if (t) return t;
      }
    }
    return typeof texts.default === "string" ? texts.default : null;
  }, [scene?.currentScene, extras, hevelLabel]);

  if (!scene) {
    return (
      <SceneBootState
        isError={start.isError}
        error={start.error}
        onRetry={() => start.mutate()}
      />
    );
  }

  const title = (payload.title as string) ?? "Scene";
  const rawType = (payload.type as string) ?? "";
  const interaction = (payload.interaction as string) ?? "";
  const sceneType = rawType === "interaction" ? interaction : rawType;

  const warning = payload.trigger_warning as TriggerWarning | undefined;
  const needsConsent = !!warning && !consented;

  const beats = (field<ScriptBeat[]>("script") ?? []).filter((b) => !!b.text);
  const crisisReminder = field<string>("crisis_reminder");

  const advance = (sceneId: number, decision: unknown) => {
    decide.mutate({ sceneId, decision });
  };

  /** R4 skip — 다음 씬으로 넘기되, 경로 상태와 대체 요약(있으면)을 남긴다. */
  const skipScene = (sceneId: number) => {
    if (sceneId === 3) {
      setScene3Skipped(true);
      const caption = field<{ text?: string }>("skip_summary_caption");
      if (caption?.text) setEcho(caption.text);
    }
    advance(sceneId, { value: "skip" });
  };

  /**
   * pick_one 선택 처리 — 모든 선택을 그대로 엔진에 보낸다.
   *
   * Scene 3 의 `converges_to`(first_woman/second_woman) 는 이제 **엔진이** 집행한다
   * (SceneConvergenceResolver). 재고 선택이면 엔진이 같은 씬 + 재고 텍스트를 돌려주므로
   * 프론트는 응답을 따르기만 하면 된다. 예전에는 여기서 decide 호출 자체를 건너뛰었는데,
   * 그러면 이 웹페이지 밖(VR·API 직접 호출)에는 수렴 보호가 전혀 없었다.
   */
  const onPick = (optionId: string) => {
    if (field<string>("decision_key") === "hevel_label") {
      setHevelLabel(optionId as HevelLabel);
      setScene4Visited(true);
    }
    setReconsider(null);
    advance(scene.currentScene, { value: optionId });
  };

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Solomon — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      {/* 직전 씬에서 넘어온 정본 문구 (현재는 Scene 3 건너뛰기 요약 자막) */}
      {echo && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 italic text-sm text-[var(--color-warm)]/90">
          <p className="whitespace-pre-line">{echo}</p>
          <p className="text-[10px] not-italic text-[var(--color-warm)]/40 mt-2 text-right">
            * AI 보조 — 본문은 성경 참조 *
          </p>
        </section>
      )}

      {/* R2 영구 오버레이 — Scene 4·5 는 yml 이 위기 자원을 씬 안에 직접 둔다 (outro 를 기다리지 않음) */}
      {crisisReminder && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-2 rounded-lg border border-amber-500/30 bg-amber-500/5 text-xs text-[var(--color-warm)]/80">
          {crisisReminder}
        </section>
      )}

      <section className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 bg-gradient-to-b from-amber-950/40 via-stone-900 to-stone-950 p-5">
        {needsConsent ? (
          <ConsentGate
            warning={warning!}
            intensityOptions={field<string[]>("intensity_toggle")}
            captionsOnly={captionsOnly}
            onIntensity={(v) => setCaptionsOnly(v === "captions_only")}
            pending={decide.isPending}
            onContinue={() => setConsented(true)}
            onSkip={() => skipScene(scene.currentScene)}
          />
        ) : (
          <ScriptBeats beats={beats} audio={!captionsOnly} />
        )}
      </section>

      {!needsConsent && (
        <section className="max-w-3xl mx-auto w-full space-y-3">
          {/* Scene 1 — grab_and_place. 제물 올리기는 core:false (서사 상태 불변, 건너뛰어도 동일). */}
          {sceneType === "grab_and_place" && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <button
                onClick={() =>
                  advance(scene.currentScene, {
                    value: "next",
                    offering_placed: true,
                  })
                }
                disabled={decide.isPending}
                className="px-4 py-4 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
              >
                {decide.isPending ? "..." : "제물을 올린다 →"}
              </button>
              <button
                onClick={() => advance(scene.currentScene, { value: "next" })}
                disabled={decide.isPending}
                className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm text-[var(--color-warm)]/90 disabled:opacity-40"
              >
                올리지 않고 다음으로 →
              </button>
            </div>
          )}

          {/* Scene 2 — cinematic */}
          {sceneType === "cinematic" && (
            <button
              onClick={() => {
                setEcho(null);
                advance(scene.currentScene, "next");
              }}
              disabled={decide.isPending}
              className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
            >
              {decide.isPending ? "..." : "계속 →"}
            </button>
          )}

          {/* Scene 3·4 — pick_one */}
          {sceneType === "pick_one" && (
            <div className="space-y-3">
              {reconsider && (
                <p className="px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 italic text-sm text-[var(--color-warm)]/90 whitespace-pre-line">
                  {reconsider}
                </p>
              )}
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                {(field<OptionLike[]>("options") ?? []).map((o) => (
                  <button
                    key={o.id}
                    onClick={() => onPick(o.id)}
                    disabled={decide.isPending}
                    className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
                  >
                    {o.label}
                  </button>
                ))}
              </div>
              {/* Scene 4 — 라벨링은 초대이지 과제가 아니다 (optional_selection: true). */}
              {field<boolean>("optional_selection") === true && (
                <button
                  onClick={() => {
                    setScene4Visited(true);
                    advance(scene.currentScene, { value: "no_label" });
                  }}
                  disabled={decide.isPending}
                  className="w-full py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm text-[var(--color-warm)]/90 disabled:opacity-40"
                >
                  이름 붙이지 않고 계속 →
                </button>
              )}
            </div>
          )}

          {/* Scene 5 — outro. 내려놓기는 비강제(required:false) — 하나도 안 내려놓아도 완주. */}
          {sceneType === "outro" && (
            <div className="space-y-5">
              <Laydown
                objects={
                  field<{ objects?: LaydownObject[] }>("laydown")?.objects ?? []
                }
                scene3Skipped={scene3Skipped}
                scene4Visited={scene4Visited}
                laidDown={laidDown}
                onLay={(id) => setLaidDown((s) => new Set(s).add(id))}
              />

              {reorientation && (
                <div className="text-center space-y-2">
                  <p className="text-base whitespace-pre-line text-[var(--color-warm)]/90 italic max-w-prose mx-auto">
                    {reorientation}
                  </p>
                  <div className="flex justify-center">
                    <NarrationAudioButton
                      text={reorientation}
                      onUnavailable="hide"
                    />
                  </div>
                  <p className="text-[10px] not-italic text-[var(--color-warm)]/40">
                    * AI 보조 — 본문은 성경 참조 *
                  </p>
                </div>
              )}

              <div className="text-center space-y-2">
                {typeof payload.value_prompt === "string" && (
                  <p className="text-xs text-[var(--color-warm)]/60">
                    {payload.value_prompt}
                  </p>
                )}
                <button
                  onClick={() =>
                    completeMission(
                      "solomon",
                      scene.sessionId,
                      "completed",
                    ).then(() => (location.href = "/"))
                  }
                  className="px-6 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
                >
                  미션 완료
                </button>
              </div>
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
              href="/topics/ecclesiastes"
              className="flex-1 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
            >
              전도서 묵상 →
            </Link>
          </div>
        </section>
      )}

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
 * R4 동의 게이트 — 카드 본문은 payload 의 정본(consent_card_ko)을 그대로 쓴다.
 * 원문 안의 `[계속한다] [건너뛰기 …]` · `음성/자막 강도: …` 줄은 *UI 지시* 라 산문에서 걷어내고
 * 실제 버튼으로 렌더한다 (문구를 프론트에 다시 적지 않기 위한 최소 가공).
 */
function ConsentGate({
  warning,
  intensityOptions,
  captionsOnly,
  onIntensity,
  pending,
  onContinue,
  onSkip,
}: {
  warning: TriggerWarning;
  intensityOptions?: string[];
  captionsOnly: boolean;
  onIntensity: (v: string) => void;
  pending: boolean;
  onContinue: () => void;
  onSkip: () => void;
}) {
  const prose = (warning.consent_card_ko ?? "")
    .split("\n")
    .filter((l) => l.trim().length > 0)
    .filter((l) => !l.trim().startsWith("["))
    .filter((l) => !/^음성\/자막 강도:/.test(l.trim()))
    .join("\n");

  const INTENSITY_LABEL: Record<string, string> = {
    captions_only: "자막만",
    mild: "약",
    default: "기본",
  };

  return (
    <div className="space-y-4">
      <p className="text-xs uppercase tracking-wider text-amber-400/80">
        잠깐 — 다음 장면 안내
      </p>
      <p className="text-sm text-[var(--color-warm)]/90 whitespace-pre-line leading-relaxed">
        {prose}
      </p>

      {Array.isArray(intensityOptions) && intensityOptions.length > 0 && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-xs text-[var(--color-warm)]/50">
            음성/자막 강도
          </span>
          {intensityOptions.map((v) => {
            const active =
              v === "captions_only"
                ? captionsOnly
                : !captionsOnly && v === "default";
            return (
              <button
                key={v}
                type="button"
                onClick={() => onIntensity(v)}
                className={`text-xs px-3 py-1 rounded-full border transition ${
                  active
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/15"
                    : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
                }`}
              >
                {INTENSITY_LABEL[v] ?? v}
              </button>
            );
          })}
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <button
          onClick={onContinue}
          disabled={pending}
          className="px-4 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
        >
          계속한다
        </button>
        <button
          onClick={onSkip}
          disabled={pending}
          className="px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm text-[var(--color-warm)]/90 disabled:opacity-40"
        >
          건너뛰기 →
        </button>
      </div>
    </div>
  );
}

/** 씬 대본(script beat) 렌더 — 본문은 전부 payload 정본. 프론트에 사본을 두지 않는다. */
function ScriptBeats({
  beats,
  audio,
}: {
  beats: ScriptBeat[];
  audio: boolean;
}) {
  const joined = beats
    .map((b) => b.text)
    .filter(Boolean)
    .join("\n");
  if (beats.length === 0) return null;
  return (
    <div className="space-y-3">
      {beats.map((b, i) => (
        <p
          key={i}
          className="text-sm text-[var(--color-warm)]/85 leading-relaxed whitespace-pre-line"
        >
          {b.text}
          {b.ref && (
            <span className="ml-2 text-[10px] text-[var(--color-warm)]/40">
              ({b.ref})
            </span>
          )}
        </p>
      ))}
      {audio && (
        <div className="pt-1">
          <NarrationAudioButton text={joined} onUnavailable="hide" />
        </div>
      )}
    </div>
  );
}

/**
 * Scene 5 내려놓기 — required:false · nudge_prompts:false. 재촉하지 않으며, 하나도
 * 내려놓지 않아도 "미션 완료" 는 항상 열려 있다. 오브젝트 등장은 경로 의존:
 *   crown=always · sword=scene3_completed · verdict_scroll=scene3_skipped · treasure=scene4_visited
 * (칼을 건너뛴 사용자에게 칼을 다시 보여주지 않기 위한 yml 의 시각 트리거 회피 규칙)
 */
function Laydown({
  objects,
  scene3Skipped,
  scene4Visited,
  laidDown,
  onLay,
}: {
  objects: LaydownObject[];
  scene3Skipped: boolean;
  scene4Visited: boolean;
  laidDown: Set<string>;
  onLay: (id: string) => void;
}) {
  const visible = objects.filter((o) => {
    switch (o.appears) {
      case "scene3_completed":
        return !scene3Skipped;
      case "scene3_skipped":
        return scene3Skipped;
      case "scene4_visited":
        return scene4Visited;
      default:
        return true;
    }
  });
  if (visible.length === 0) return null;

  return (
    <div className="space-y-2">
      <p className="text-xs text-[var(--color-warm)]/60">
        손에 들려 있던 것들을 제단 앞에 내려놓아도 좋습니다.{" "}
        <strong>내려놓지 않아도 괜찮습니다</strong> — 이 자리는 시험이 아닙니다.
      </p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
        {visible.map((o) => {
          const done = laidDown.has(o.id);
          return (
            <button
              key={o.id}
              onClick={() => onLay(o.id)}
              disabled={done}
              className={`px-4 py-3 rounded-lg border text-left transition disabled:cursor-default ${
                done
                  ? "border-[var(--color-primary)] bg-[var(--color-primary)]/15 opacity-80"
                  : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
              }`}
            >
              {o.label}
              {o.meaning && (
                <span className="block text-[10px] text-[var(--color-warm)]/40 mt-1">
                  {o.meaning}
                </span>
              )}
              {done && (
                <span className="ml-2 text-[var(--color-primary)] text-xs">
                  내려놓음 ✓
                </span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}
