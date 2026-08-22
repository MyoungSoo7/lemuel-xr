"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { NarrationAudioButton } from "@/components/NarrationAudioButton";
import { CrisisReminder } from "@/components/CrisisReminder";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";
import { SceneBootState } from "@/components/SceneBootState";
import { ScenePassage } from "@/components/ScenePassage";
import {
  TriggerWarningGate,
  readTriggerWarning,
  cardDoors,
} from "@/components/TriggerWarningGate";

/**
 * Abraham 미션 — 기다림은 설명되지 않았고, 본문은 그것을 지우지 않았다.
 *
 * 이 화면은 2026-08-22 부터 도달 가능하다 — `Character` enum 에 `ABRAHAM("abraham")` 이
 * 들어가면서 `startMission("abraham")` 이 200 을 받는다. 그 전까지는 enum 한 줄을 비워 둔 채
 * 화면만 먼저 만들어 두었는데, 화면이 없으면 이 인물이 *덜 노출된* 게 아니라
 * **검사 사정권 밖**이 되기 때문이다 — 프론트 검사기 두 개
 * (`check_frontend_trigger_warning.py` · `mission-tap-targets.spec.ts`)가 화면을 읽는다.
 * 노출 근거·남은 빚은 `docs/ABRAHAM-RUNTIME-SIGNOFF.md`.
 *
 * abraham.yml 5 Scene:
 *   1 cinematic(떠남) → 2 cinematic(장막) → 3 pick_one(별, 그리고 사백 년) →
 *   4 cinematic(웃음) → 5 outro(이삭 — R4 게이트) → complete → 홈.
 *
 * ─────────────── 이 화면이 payload 를 읽는 방식 ───────────────
 *
 * 1) 본문은 전부 `extras.captions` 정본이다. 프론트에 성경 자구 사본이 0개인 것은 의도다 —
 *    `check_monologue_quotes.py` 가 프론트에 자구가 있으면 빨개진다. 자막은 이미
 *    `"<자구> (창 15:5)"` 형태로 참조를 달고 오므로 여기서 참조를 다시 만들지 않는다.
 *
 * 2) `*_note` 필드(options_note·npc_note·age_note·two_laughters_note·caption_provenance
 *    ·reason_scope_lock·gen_18_14_scope_lock 등)는 **저작자용 가드 주석**이지 사용자
 *    카피가 아니다. 렌더하지 않는다.
 *
 *    ⚠️ 단, `reason_scope_lock`·`gen_18_14_scope_lock` 이 지키려는 **문장 자체는
 *    자막에 들어 있다.** Scene 3 의 마지막 자막(「이 이유는 아브람에 관한 것이 아니었다.」)과
 *    Scene 4 의 마지막 자막(「이 기한은 사라에게 주어진 기한이다.」)이 그것이고,
 *    이 화면은 자막을 통째로 렌더하므로 그 두 줄도 함께 나간다. 자막을 잘라 보여 주는
 *    「개선」을 하면 그 두 줄이 가장 먼저 잘린다 — 하지 말 것.
 *
 * ─────────────── 안전선 — 이 인물의 고유 지점 ───────────────
 *
 * R4 동의 카드가 **하나뿐이고 마지막 Scene 에 붙어 있다.** 그래서 건너뛰기 목적지가
 * 정수(다음 씬 점프)일 수 없다 — 갈 다음이 없다. 문자열
 * `abraham_scene5_alt_laughter_only` 이고, 서버가 **같은 Scene 을 축약 블록으로 다시
 * 조립해** 돌려준다(`DecideSceneUseCase.altBlockPayload`). 자막 다섯이 하나로 줄고
 * sceneId 는 5 그대로다.
 *
 * ⚠️ 그 축약 payload 에는 `trigger_warning` 이 **그대로 살아 있다.** 동의 여부만 보고
 *    게이트를 세우면 건너뛴 사람에게 같은 카드가 영원히 다시 뜬다(sceneId 가 안 바뀌므로
 *    씬 전환 초기화도 그를 구해 주지 않는다). 그래서 게이트 조건에 `conditionalBlockId`
 *    부재를 함께 건다 — 서버가 그 키를 박아 주는 것이 「이미 건너뛴 사람」의 유일한 표시다.
 *    (다니엘 Scene 5 와 같은 함정이고, 같은 방식으로 막는다.)
 */
type Scene = JosephStartResponse;

interface Caption {
  speaker_ko?: string;
  text_ko?: string;
}

interface Option {
  id: string;
  label_ko?: string;
  ref?: string;
}

/** Scene 3 의 세 카드. 마감 문구의 유일한 축이며, 고르지 않으면 `null` 로 남는다. */
type WaitLabel = "voice_the_lack" | "hold_the_promise" | "sit_with_unknowing";

interface ClosingRoute {
  when?: { wait_label?: string | null };
  static_text_ko?: string;
}

interface Branch {
  id?: string;
  decision_key?: string;
  routes?: ClosingRoute[];
}

export default function AbrahamPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  /*
    Scene 3 에서 고른 기다림의 이름. `null` 은 "아직/끝내 안 골랐다" 이자 **정상 경로**다 —
    저작이 20초 타임아웃에 `wait_label: null` 을 명시했고, Scene 5 마감 문구에 그 경우
    전용 폴백이 있다. 라벨 없이 도착한 사용자가 가장 취약한 자리라서 그렇다.
  */
  const [waitLabel, setWaitLabel] = useState<WaitLabel | null>(null);

  const start = useMutation({
    mutationFn: () => startMission("abraham", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("abraham", scene!.sessionId, sceneId, decision),
    onSuccess: (d) => {
      setConsented(false);
      setScene(d);
      setHistory((h) => [
        ...h,
        String((d.scenePayload as Record<string, unknown>).title ?? ""),
      ]);
    },
  });

  useEffect(() => {
    if (!scene && !start.isPending && !start.isError) start.mutate();
  }, [scene, start]);

  const payload = useMemo(
    () => (scene?.scenePayload ?? {}) as Record<string, unknown>,
    [scene?.scenePayload],
  );
  const extras = useMemo(
    () => (payload.extras as Record<string, unknown> | undefined) ?? {},
    [payload],
  );

  /*
    Scene 5 마감 한 줄 — `extras.branches[].routes[]` 정본에서 고른다.
    `wait_label` 이 null 인 경로가 표에 실제로 있고, 그것이 폴백이다.
    프론트가 문구를 갖고 있지 않은 것은 의도다 — 정본이 개정되면 화면도 같이 바뀐다.
  */
  const closingText = useMemo(() => {
    if (scene?.currentScene !== 5) return null;
    const branches = extras.branches as Branch[] | undefined;
    const routes = branches?.find((b) => b.decision_key === "closing_key")
      ?.routes;
    if (!routes) return null;
    const hit =
      routes.find((r) => (r.when?.wait_label ?? null) === waitLabel) ??
      routes.find((r) => (r.when?.wait_label ?? null) === null);
    return hit?.static_text_ko ?? null;
  }, [scene?.currentScene, extras, waitLabel]);

  const field = <T,>(key: string): T | undefined =>
    (extras[key] as T | undefined) ?? (payload[key] as T | undefined);

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

  const warning = readTriggerWarning(payload);
  /*
    `conditionalBlockId` 는 서버가 축약 블록을 조립했을 때만 payload 에 박힌다.
    그 키가 있으면 이 사람은 **이미 건너뛴 사람** 이고, 같은 씬에 남아 있는
    `trigger_warning` 은 지나간 카드의 흔적이다. 이 조건이 없으면 무한 루프가 된다.
  */
  const alreadySkipped = typeof payload.conditionalBlockId === "string";
  const needsConsent = !!warning && !consented && !alreadySkipped;
  /*
    두 문의 이름은 저작 정본(`consent_card_ko` 의 마지막 줄)이 소유한다. 여기 베껴 적으면
    정본이 개정돼도 화면은 옛 이름을 계속 쓰고, 그 어긋남을 재는 검사기가 없다.
    못 읽으면 게이트 기본 라벨로 돌아간다 — 문은 남는다.
  */
  const doors = cardDoors(warning?.consent_card_ko);

  const anchor = field<string>("anchor");
  // 씬마다 선언된 성경 참조. extras 가 아니라 payload 최상위다(ScenePayloadAssembler.build).
  const scriptureRef = payload.scriptureRef as string | undefined;
  const additionalRefs = field<string[]>("additional_refs");
  const captions = field<Caption[]>("captions") ?? [];
  const options = field<Option[]>("options") ?? [];
  const optionsNote = field<string>("options_note");
  const crisisReminderRaw = field<unknown>("crisis_reminder");
  /*
    저작이 위기 안내를 `{ text_ko, position }` 객체로 두었다(다니엘은 문자열이었다).
    문자열도 함께 받는 이유는 축약 블록이 이 키를 문자열로 덮을 수 있어서다 —
    형태 하나만 가정하면 그 경로에서 위기 안내가 조용히 사라진다.
  */
  const crisisReminder =
    typeof crisisReminderRaw === "string"
      ? crisisReminderRaw
      : ((crisisReminderRaw as { text_ko?: string } | undefined)?.text_ko ??
        undefined);

  const advance = (sceneId: number, decision: unknown) =>
    decide.mutate({ sceneId, decision });

  const narration = captions
    .map((c) => c.text_ko ?? "")
    .filter(Boolean)
    .join("\n");

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6 pb-16">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Abraham — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
        {anchor && (
          <p className="text-xs text-[var(--color-warm)]/50 mt-1">{anchor}</p>
        )}
      </header>

      {/*
        씬이 직접 둔 위기 안내(Scene 3·4·5). 서버 `CrisisTokenResolver` 가 번호를 치환해
        보낸 문자열이고, 이 렌더가 그 번호가 화면까지 도달하는 마지막 한 칸이다.
        프론트가 번호를 아는 일이 없어야 `ScenarioHotlineRatchetTest` 의 전제가 산다.
      */}
      <CrisisReminder text={crisisReminder} />

      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative bg-cover bg-center bg-stone-900"
        style={{
          backgroundImage: `url(/images/scenes/abraham/${scene.currentScene}.webp)`,
        }}
      >
        {/*
          배경 이미지는 아직 없다(`generate_all_scenes.py` 에 abraham 프롬프트가 없다).
          그래서 실제로 보이는 것은 아래 그라디언트다 — 룻이 이미지 생성 전에 출시된 것과
          같은 상태이고, 대장 §5 에 적어 두었다.
        */}
        <div className="absolute inset-0 bg-gradient-to-b from-stone-900/85 via-stone-900/75 to-amber-950/85" />
        <div className="relative z-10 p-5">
          {needsConsent ? (
            <TriggerWarningGate
              warning={warning!}
              fallbackProse={
                <p>
                  다음 장면에는 <strong>오랜 기다림 끝의 출산 이야기</strong> 가
                  나옵니다. 지금이 버겁다면{" "}
                  <strong>건너뛰셔도 괜찮습니다</strong> — 건너뛰셔도 마무리는
                  그대로 받으십니다.
                </p>
              }
              continueLabel={doors?.continueLabel}
              skipLabel={doors?.skipLabel}
              pending={decide.isPending}
              onContinue={() => setConsented(true)}
              /*
                둘째 문은 「건너뛰기」이고 `declined_route` 가 없다 — 이야기를 여기서
                마치는 문이 아니다(룻 Scene 3 과 다르다). 같은 씬의 축약본으로 간다.
              */
              onSkip={() =>
                decide.mutate({
                  sceneId: scene.currentScene,
                  decision: { value: "skip" },
                })
              }
            />
          ) : (
            <div className="space-y-4">
              {captions.length > 0 && (
                <>
                  <div className="space-y-3">
                    {captions.map((c, i) => (
                      <p
                        key={`${c.speaker_ko ?? "narration"}-${i}`}
                        className="text-base leading-relaxed text-[var(--color-warm)]/90"
                      >
                        {c.speaker_ko && (
                          <span className="block text-xs text-[var(--color-warm)]/50 mb-0.5">
                            {c.speaker_ko}
                          </span>
                        )}
                        {c.text_ko}
                      </p>
                    ))}
                  </div>
                  <div className="flex justify-start">
                    <NarrationAudioButton
                      text={narration}
                      onUnavailable="hide"
                    />
                  </div>
                </>
              )}
              {/* 성경 본문 — 동의 게이트 *안쪽* 이다. */}
              <ScenePassage
                reference={scriptureRef}
                additional={additionalRefs}
              />
              <p className="text-[10px] text-[var(--color-warm)]/40 text-right">
                * AI 보조 — 본문은 성경 참조 *
              </p>
            </div>
          )}
        </div>
      </section>

      {!needsConsent && (
        <section className="max-w-3xl mx-auto w-full space-y-3">
          {/* Scene 1·2·4 — cinematic. 겪는 자리이고 조작이 없다. */}
          {sceneType === "cinematic" && (
            <button
              onClick={() => advance(scene.currentScene, { value: "next" })}
              disabled={decide.isPending}
              className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
            >
              {decide.isPending ? "..." : "계속 →"}
            </button>
          )}

          {/*
            Scene 3 — pick_one. 이 미션의 유일한 사용자 선택이다.
            세 카드 전부 본문에 있는 반응이고 틀린 답이 없다(`options_note`).
            고른 값은 그대로 서버에 보낸다 — 저작이 `decision_key: wait_label` 로 선언했고,
            쓰이는 자리는 Scene 5 의 마감 한 줄 하나뿐이다. 점수도 순위도 없고,
            어느 카드를 골라도 다음은 Scene 4 다.
          */}
          {sceneType === "pick_one" && (
            <div className="space-y-3">
              <div className="grid grid-cols-1 gap-3">
                {options.map((o) => (
                  <button
                    key={o.id}
                    onClick={() => {
                      setWaitLabel(o.id as WaitLabel);
                      advance(scene.currentScene, { value: o.id });
                    }}
                    disabled={decide.isPending}
                    className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] text-left transition disabled:opacity-40"
                  >
                    <span className="font-semibold">{o.label_ko}</span>
                    {o.ref && (
                      <span className="block text-xs text-[var(--color-warm)]/50 mt-1">
                        {o.ref}
                      </span>
                    )}
                  </button>
                ))}
              </div>
              {/*
                고르지 않고 지나가는 것도 정본이 정한 정상 경로다. 라벨은 null 로 남고
                Scene 5 는 그 경우 전용 마감 문구를 갖고 있다.
              */}
              <button
                onClick={() => advance(scene.currentScene, { value: "next" })}
                disabled={decide.isPending}
                className="w-full py-3 rounded-lg border border-[var(--color-warm)]/25 text-sm text-[var(--color-warm)]/70 hover:border-[var(--color-warm)]/50 disabled:opacity-40"
              >
                고르지 않고 넘어가기
              </button>
              {optionsNote && (
                <p className="sr-only" data-testid="options-note">
                  {optionsNote}
                </p>
              )}
            </div>
          )}

          {/* Scene 5 — outro. 축약 경로로 온 사람도 여기까지 온다. */}
          {sceneType === "outro" && (
            <div className="space-y-5">
              {closingText && (
                <p className="text-base leading-relaxed text-[var(--color-warm)]/90 border-l-2 border-[var(--color-primary)]/40 pl-3">
                  {closingText}
                </p>
              )}

              <div className="text-center space-y-2">
                {typeof payload.value_prompt === "string" && (
                  <p className="text-xs text-[var(--color-warm)]/60 max-w-prose mx-auto">
                    {payload.value_prompt}
                  </p>
                )}
                <button
                  onClick={() =>
                    completeMission(
                      "abraham",
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
              className="flex-1 flex items-center justify-center min-h-11 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
            >
              ← 홈
            </Link>
            <Link
              href="/topics/journal"
              className="flex-1 flex items-center justify-center min-h-11 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
            >
              일기 쓰기 →
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
