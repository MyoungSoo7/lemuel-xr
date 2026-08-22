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
 * Rahab — 먼저 있었던 일.
 *
 * ⚠️ **이 화면은 아직 사용자에게 열려 있지 않다.** `Character` enum 에 `RAHAB("rahab")`
 * 이 없어서 `/api/game/rahab/start` 는 E_CHARACTER_UNKNOWN 으로 떨어지고, 홈에도 카드가
 * 없다(홈 목록의 정본은 enum 이다). 여는 조건은 `docs/RAHAB-RUNTIME-SIGNOFF.md` 의
 * 결정 줄이고 **그 줄은 비어 있다.** 이 파일이 있는 것을 결정이 있는 것으로 읽지 않는다.
 * 2026-08-22 의 「인물6명 노출해」에 라합은 **포함되지 않았다**(`docs/JACOB-RUNTIME-SIGNOFF.md`).
 *
 * ─────────────── 이 인물은 미션이 아니라 낭독 트랙이다 ───────────────
 *
 * `docs/SEED-RAHAB.md` rev.15 D1. 다섯 Scene 전부 `interactions: []` · `branches: []` 이고,
 * 사용자가 하는 일은 셋뿐이다 — **속도 · 동의/거절 · 이탈**. 그래서 이 화면에는 앞의
 * 인물들에 있던 것이 없다: 갈래 버튼도, 고른 값에 따라 달라지는 마감 문구 표도,
 * 진행 단계 표시도 없다. 다섯 화면이 모두 「계속 →」 하나다.
 *
 * 그 대가로 **동의 카드가 이 미션의 유일한 통제**가 된다(카드는 다섯 Scene 전부에 있다).
 * 카드가 없애 주겠다고 한 자막이 실제로 빠지는지는 프론트가 아니라 서버에서 결정되고,
 * `RahabWithheldNarrativeTest` 가 저작 정본과 축자 대조한다. 여기서 지킬 것은 하나다 —
 * **동의 전에는 본문을 렌더하지 않는다**(`needsConsent`).
 *
 * ─────────────── 자막을 읽는 방식 ───────────────
 *
 * 1) 본문은 전부 `extras.captions` 정본이고, 프론트에 성경 자구 사본은 0개다.
 *
 * 2) 앞의 인물들과 달리 자막에 **`verse_ref` 가 별도 키로 온다.** 야곱까지는 참조를
 *    `text_ko` 안에 `"<자구> (창 32:25)"` 로 붙여 왔는데 라합은 그러지 않았다. 이유는
 *    저작 정본의 참조 표기 24개 중 11개가 「중」(부분 인용) 표시를 달고 있고, 그 표시의
 *    근거가 확인되지 않아서다(`docs/RAHAB-RUNTIME-SIGNOFF.md` 의 미해소 부채). 근거
 *    없는 표시를 화면 문장 안에 구워 넣는 대신 참조를 분리해 두고, 런타임 참조에서는
 *    「중」을 뺐다. 화면에는 참조가 자막 위 작은 글씨로 뜬다.
 *
 * 3) `*_note` 필드(npc_note·pacing_note·untold_note·attribution_note·closing_note·
 *    closing_symmetry_note·unknown_consent_note 등)는 **저작자용 가드 주석** 이지
 *    사용자 카피가 아니다. 렌더하지 않는다.
 *
 * ─────────────── R4 — 동의 카드가 다섯이고 모양이 세 가지다 ───────────────
 *
 *  · Scene 1·3·4·5 — 목적지가 **문자열**(같은 씬의 축약 블록). 씬 번호가 안 바뀌므로
 *    `conditionalBlockId` 가 박혔는지로만 「이미 건너뛴 상태」를 안다(`alreadySkipped`).
 *    이 한 줄이 없으면 같은 카드가 영원히 다시 뜬다.
 *  · Scene 2 — 목적지가 **정수 3**(씬 전체 건너뛰기). 저작은 같은 파일 안의 대체 라우트로
 *    설계했지만 그 라우트가 자기 씬을 가리키고 있어서(seed 정정 AQ), 엔진의 정수 목적지로
 *    옮겼다. 건너뛴 사람이 무엇을 지나쳤는지는 `skip_bridge_narration_ko` 한 줄이
 *    Scene 3 상단에 한 번 깔아 준다.
 *  · Scene 5 — 카드에 `covered_by_scene: 1` 이 붙어 있다. Scene 1 에서 이미 거절한 사람은
 *    서버가 도착 시점에 축약 블록을 얹어 내려주므로 카드를 다시 보지 않는다. 같은 낙인
 *    호칭에 대해 두 번 묻지 않는다는 뜻이고, Scene 1 카드가 「마지막 장면의 그 줄도 함께
 *    빠집니다」라고 약속한 그 자리다.
 *
 * ─────────────── 「본문이 아닙니다」 ───────────────
 *
 * 건너뛴 자리에 들어가는 대체 문장에는 화자로 「본문이 아닙니다」가 붙는다. 성경 자구와
 * 안내 문장이 같은 글꼴로 흐르면 사용자는 둘을 구별할 방법이 없다 — 그 표지가 유일한
 * 구별 수단이라 화면에서 지우지 않는다. 다리 문장(`skip_bridge_narration_ko`)도 첫 줄이
 * 그 표지다.
 *
 * ─────────────── Scene 4 의 배경이 둘인 이유 ───────────────
 *
 * 저작의 둘째 카드는 「창문과 줄은 화면에 띄우지 않는다」를 약속한다. 자막만 빼서는
 * 지킬 수 없는 약속이라(창과 줄은 배경에 있다) 축약 블록이 `background_variant` 를 함께
 * 갈아 끼운다. 이 화면은 그 값으로 배경 파일을 고른다 — 값이 바뀌었는데 그림이 그대로면
 * 카드가 한 약속이 화면에서 거짓이 된다.
 */
type Scene = JosephStartResponse;

interface Caption {
  speaker_ko?: string;
  /** 앞 인물들에 없던 키. 위 주석 2) 참조. */
  verse_ref?: string;
  text_ko?: string;
}

interface ClosingScreen {
  mission_title_ko?: string;
  closing_line_ko?: string;
  /** 위기 자원 토큰이 든 문장. 서버가 번호를 치환해 내려준다. */
  closing_footer_ko?: string;
}

/** 배경 그림 파일. Scene 4 만 변형이 있다(`4-alt.webp` — 창문도 줄도 없는 판). */
const NO_WINDOW_VARIANT = "no_window_no_cord";

export default function RahabPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  /**
   * 다음 화면 상단에 한 번만 얹는 짧은 글. 건너뛴 사람에게 저작이 남겨 둔 다리 문장이고,
   * 다음 진행에서 지운다 — 남아 있으면 이야기 위에 겹쳐 읽히는 다른 본문이 된다.
   */
  const [echo, setEcho] = useState<string | null>(null);

  const start = useMutation({
    mutationFn: () => startMission("rahab", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("rahab", scene!.sessionId, sceneId, decision),
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
  const sceneType = (payload.type as string) ?? "";

  const warning = readTriggerWarning(payload);
  const alreadySkipped = typeof payload.conditionalBlockId === "string";
  const needsConsent = !!warning && !consented && !alreadySkipped;
  /*
    두 문의 이름은 저작 정본(`consent_card_ko` 의 대괄호 줄)이 소유한다. 여기 베껴 적으면
    정본이 개정돼도 화면은 옛 이름을 계속 쓰고, 그 어긋남을 재는 검사기가 없다.

    ⚠️ Scene 4 의 카드는 저작의 잠긴 문구 **두 장을 이어 붙인 것**이라 대괄호 줄이 둘이다.
    `cardDoors()` 는 첫 줄만 문 이름으로 쓰고 나머지 대괄호 줄은 본문에서 지운다. 그래서
    화면의 문은 「계약 문구 두 줄은 건너뛴다」 하나지만, 그 문을 통과하면 둘째 카드가
    약속한 것(창문과 줄을 띄우지 않음)까지 함께 지켜진다 — 축약 블록이 자막과 배경을 같이
    갈아 끼우기 때문이다. 문 하나가 약속 둘을 지고 있다는 뜻이고, 이건 저작과 갈라진
    자리라 대장(`docs/RAHAB-RUNTIME-SIGNOFF.md`)에 적혀 있다.
  */
  const doors = cardDoors(warning?.consent_card_ko);

  const anchor = field<string>("anchor");
  // 씬마다 선언된 성경 참조. extras 가 아니라 payload 최상위다
  // (ScenePayloadAssembler.build — `sc.scriptureRef?.let { p["scriptureRef"] = it }`).
  const scriptureRef = payload.scriptureRef as string | undefined;
  const additionalRefs = field<string[]>("additional_refs");
  const captions = field<Caption[]>("captions") ?? [];
  const crisisReminder = field<string>("crisis_reminder");
  const dwell = field<Record<string, unknown>>("dwell");
  const closingScreen = field<ClosingScreen>("closing_screen");
  const backgroundVariant = field<string>("background_variant");

  /*
    Scene 4 만 변형 배경이 있다. 값으로 고른다 — 블록 id 로 고르면 블록 이름이 바뀌는 날
    그림이 조용히 옛것으로 돌아간다.
  */
  const background =
    backgroundVariant === NO_WINDOW_VARIANT
      ? `/images/scenes/rahab/${scene.currentScene}-alt.webp`
      : `/images/scenes/rahab/${scene.currentScene}.webp`;

  const advance = (sceneId: number, decision: unknown) => {
    setEcho(null);
    decide.mutate({ sceneId, decision });
  };

  const finish = () =>
    completeMission("rahab", scene.sessionId, "completed").then(
      () => (location.href = "/"),
    );

  const narration = captions
    .map((c) => c.text_ko ?? "")
    .filter(Boolean)
    .join("\n");

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6 pb-16">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Rahab — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
        {anchor && (
          <p className="text-xs text-[var(--color-warm)]/50 mt-1">{anchor}</p>
        )}
      </header>

      {echo && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 italic text-sm text-[var(--color-warm)]/90">
          <p className="whitespace-pre-line">{echo}</p>
        </section>
      )}

      {/*
        다섯 씬 전부에 있는 위기 안내. 서버 `CrisisTokenResolver` 가 번호를 치환해 보낸
        문자열이고, 이 렌더가 그 번호가 화면까지 도달하는 마지막 한 칸이다.
        프론트가 번호를 아는 일이 없어야 `ScenarioHotlineRatchetTest` 의 전제가 산다.
      */}
      <CrisisReminder text={crisisReminder} />

      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative bg-cover bg-center bg-stone-900"
        style={{ backgroundImage: `url(${background})` }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-stone-900/85 via-stone-900/75 to-amber-950/85" />
        <div className="relative z-10 p-5">
          {needsConsent ? (
            <TriggerWarningGate
              warning={warning!}
              fallbackProse={
                <p>
                  이 이야기는 <strong>한 여인이 겪은 일</strong> 을 성경 본문이
                  말한 데까지만 따라갑니다. 지금이 버겁다면{" "}
                  <strong>해당하는 자막을 건너뛰어도 괜찮습니다</strong> —
                  건너뛰어도 이야기는 끝까지 이어집니다.
                </p>
              }
              continueLabel={doors?.continueLabel}
              skipLabel={doors?.skipLabel}
              pending={decide.isPending}
              onContinue={() => setConsented(true)}
              /*
                다섯 카드 모두 `declined_route` 가 없다 — 이야기를 여기서 마치는 문이
                아니다(룻 Scene 3 과 다르다). Scene 2 는 서버가 Scene 3 으로 점프시키고,
                나머지 넷은 같은 씬의 축약 블록으로 갈아 끼운다. `advance` 를 쓰지 않는
                이유는 그것이 방금 깔아 둔 다리를 지우기 때문이다.
              */
              onSkip={() => {
                setEcho(warning!.skip_bridge_narration_ko ?? null);
                decide.mutate({
                  sceneId: scene.currentScene,
                  decision: { value: "skip" },
                });
              }}
            />
          ) : (
            <div className="space-y-4">
              {captions.length > 0 && (
                <>
                  <div className="space-y-3">
                    {captions.map((c, i) => (
                      <p
                        key={`${c.verse_ref ?? c.speaker_ko ?? "narration"}-${i}`}
                        className="text-base leading-relaxed text-[var(--color-warm)]/90"
                      >
                        {(c.speaker_ko || c.verse_ref) && (
                          <span className="block text-xs text-[var(--color-warm)]/50 mb-0.5">
                            {c.speaker_ko}
                            {c.speaker_ko && c.verse_ref && " · "}
                            {c.verse_ref}
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
              {/* Scene 5 — 머무는 시간에 정답이 없다는 저작 문구는 사용자에게 보여준다. */}
              {typeof dwell?.note_ko === "string" && (
                <p className="text-sm italic text-[var(--color-warm)]/70 border-l-2 border-[var(--color-primary)]/40 pl-3">
                  {dwell.note_ko as string}
                </p>
              )}
              <p className="text-[10px] text-[var(--color-warm)]/40 text-right">
                * AI 보조 — 본문은 성경 참조 *
              </p>
            </div>
          )}
        </div>
      </section>

      {!needsConsent && (
        <section className="max-w-3xl mx-auto w-full space-y-3">
          {/*
            Scene 1~4 — cinematic. 겪는 자리이고 조작이 없다. 이 미션에는 이 버튼 말고
            사용자가 고를 것이 없다(D1 — 낭독 트랙). 여기에 선택지를 만들면 본문이 라합에게
            돌린 행위를 사용자가 가로채는 형태가 열린다.
          */}
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
            Scene 5 — outro. 건너뛴 사람도 여기까지 오고, 같은 마감 화면을 받는다.
            마감 문구는 하나뿐이다 — 고른 것이 없으니 갈라질 축도 없다.
          */}
          {sceneType === "outro" && (
            <div className="space-y-5">
              {closingScreen && (
                <div className="space-y-2 border-l-2 border-[var(--color-primary)]/40 pl-3">
                  {closingScreen.closing_line_ko && (
                    <p className="text-base leading-relaxed text-[var(--color-warm)]/90">
                      {closingScreen.closing_line_ko}
                    </p>
                  )}
                  {/*
                    범위를 못 박는 문장이다 — 「이 미션은 라합이 겪은 일이 어떤 목적을 위해
                    주어졌다고 말하지 않습니다」. 위기 자원 토큰이 이 문장 안에 있으므로
                    잘라 내면 마감 화면에서 위기 안내가 사라진다.
                  */}
                  {closingScreen.closing_footer_ko && (
                    <p className="text-sm leading-relaxed text-[var(--color-warm)]/70">
                      {closingScreen.closing_footer_ko}
                    </p>
                  )}
                </div>
              )}

              <div className="text-center space-y-2">
                {typeof payload.value_prompt === "string" && (
                  <p className="text-xs text-[var(--color-warm)]/60 max-w-prose mx-auto">
                    {payload.value_prompt}
                  </p>
                )}
                <button
                  onClick={finish}
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

          {/*
            카드가 「어느 지점에서든 멈추거나 나갈 수 있습니다」라고 약속한다. 이 미션에는
            야곱 같은 별도 이탈 블록(`offramp`)이 없으므로 그 약속을 지는 것은 이 홈 링크다 —
            다섯 씬 전부에, 동의 카드를 지난 뒤 항상 떠 있다.
          */}
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
