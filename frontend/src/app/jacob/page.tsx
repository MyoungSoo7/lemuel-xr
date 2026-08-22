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
 * Jacob 미션 — 내가 상처를 준 쪽일 때.
 *
 * `Character.JACOB` 은 2026-08-22 에 열렸다. 근거·범위·남은 빚은
 * `docs/JACOB-RUNTIME-SIGNOFF.md` 에 있고, 전문 신학·정신건강 검토는 **없다.**
 * 저작이 건 인간 안전검토자 사인오프도 없다 — 해제한 게 아니라 미해소 부채로
 * 대장에 적힌 채 열렸다. 신학 미확정 항목도 셋 열린 채다.
 *
 * jacob.yml 5 Scene:
 *   1 cinematic(속임 — R4 게이트) → 2 cinematic(부르짖음) →
 *   3 pick_one(이십 년 — 돌아가는 세 갈래) → 4 contemplative(얍복) →
 *   5 outro(받으니라 — R4 게이트) → complete → 홈.
 *
 * ─────────────── 이 화면이 지켜야 하는 것 ───────────────
 *
 * **화해는 요구되지 않는다.** 이 문장이 이 인물의 축이다. 앞의 인물들과 결정적으로
 * 다른 점은 사용자가 앉는 자리다 — 상처를 받은 쪽이 아니라 **상처를 준 쪽** 이다.
 * 그 자리에 앉혀 놓고 「그러니 돌아가라」로 끝나면 이 화면은 정확히 반대의 물건이 된다.
 * 그래서 여기서 지키는 것:
 *
 *  · 세 갈래의 순서는 **payload 가 준 순서 그대로** 다. 여기서 정렬하지 않는다
 *    (`card_order_policy: shuffle_per_session`). 번호·stepper·진행 막대를 붙이지 않는다
 *    (`card_render_style: flat_siblings`). 고정 순서와 번호는 세 장을 「돌아감을 향한
 *    진행 막대」로 만들고, 그 순간 「가지 않고 머문다」는 **아직 거기** 가 된다.
 *  · **고르지 않고 넘어가는 문이 항상 있다.** 그 사람도 Scene 5 에서 마감 문구를 받는다
 *    (`closing_texts.default.all`). 무응답은 실패가 아니라 정상 경로다.
 *  · Scene 5 의 동의 카드를 **건너뛴 사람도 같은 마감을 받는다.** 대면을 본 사람만 끝을
 *    받으면 그게 곧 화해 압박이다. 축약 블록(`5_alt`)은 `closing_texts` 를 덮지 않고
 *    자막만 갈아 끼우므로, 아래 `closingText` 는 두 경로에서 같은 값을 낸다.
 *
 * ─────────────── payload 를 읽는 방식 ───────────────
 *
 * 1) 본문은 전부 `extras.captions` 정본이다. 프론트에 성경 자구 사본이 0개인 것은 의도다
 *    — `check_monologue_quotes.py` 가 프론트에 자구가 있으면 빨개진다. 자막은 이미
 *    `"<자구> (창 32:25)"` 형태로 참조를 달고 오므로 여기서 참조를 다시 만들지 않는다.
 *
 * 2) `*_note` 필드(npc_note·observe_note·options_note·card_ui_note·threat_note·
 *    name_note·victory_note·wording_note·grip_debt_note·gift_note·closing_texts_note·
 *    scope_note 등)는 **저작자용 가드 주석** 이지 사용자 카피가 아니다. 렌더하지 않는다.
 *    `pre_branch_notice_ko` 는 이름이 `_ko` 로 끝나는 대로 **사용자 카피** 다 — 렌더한다.
 *
 * ─────────────── R4 — 동의 카드가 둘이고 모양이 다르다 ───────────────
 *
 * Scene 1(가족 기만)과 Scene 5(재회 대면). 다니엘과 같이 둘이지만 서로를 상속하지 않는다.
 *
 *  · Scene 1 의 목적지는 **정수 2** 다. 저작은 이 카드가 Scene 1~4 를 함께 덮는다고
 *    적었지만, 엔진은 건너뛰기 목적지가 `covers_scenes` 안에 있으면 반려한다 —
 *    `[1,2,3,4]` 로 적으면 목적지가 5뿐이고, 그러면 카드가 없애 주겠다고 한 적 없는
 *    얍복(3·4)까지 사라진다. 그래서 `covers_scenes: [1]` · 목적지 2다. 건너뛴 사람이
 *    무엇을 지나쳤는지는 `skip_bridge_narration_ko` 한 줄이 Scene 2 상단에 한 번 깔아 준다.
 *  · Scene 5 의 목적지는 **문자열 `5_alt`** 다. 마지막 씬(`next: null`)이라 그 모양이
 *    성립한다. 서버가 같은 씬에 축약 블록을 조립해 내려주고(`conditionalBlockId`),
 *    그때부터 이 화면은 동의 카드를 다시 띄우지 않는다(`alreadySkipped`).
 *
 * ─────────────── 저작의 셋째 문이 카드 밖에 있는 이유 ───────────────
 *
 * 저작의 Scene 1 카드는 문이 셋이다(들어간다 / 요약으로 본다 / **나는 이 자리에 서 있지
 * 않다**). 그런데 `cardDoors()` 는 문을 정확히 둘만 만든다 — 셋째를 카드에 욱여넣으면
 * 「요약으로 본다」가 사라진다.
 *
 * 그래서 셋째 문은 **모든 씬에 상시 떠 있는 이탈 버튼**(`extras.offramp`)이다. 저작이 그
 * 문에 건 조건이 오히려 이 모양에서 정확히 만족된다 — `records_decision: false`(결정으로
 * 보내지 않는다) · `closing_phrase: none`(마감 문구를 띄우지 않는다) ·
 * `mission_complete: true`(미완이 아니라 완료로 닫는다) · `available_at: [scene1..scene5]`
 * (다섯 씬 전부에 있다). Scene 1 의 카드 옵션으로 뒀다면 그 「상시」가 거짓말이 됐다.
 *
 * 이 문은 **마치는 것이지 끝을 받는 것이 아니다.** 그래서 마감 문구를 거치지 않고
 * 곧바로 완료로 닫는다.
 */
type Scene = JosephStartResponse;

interface Caption {
  speaker_ko?: string;
  text_ko?: string;
}

interface Option {
  id: string;
  label_ko?: string;
}

interface Offramp {
  label_ko?: string;
  notice_ko?: string;
}

/** Scene 3 의 세 갈래. 마감 문구 표의 첫 축이며, 고르지 않으면 `default` 로 간다. */
type ReturnLabel = "send_ahead" | "go_afraid" | "stay_and_pray";

/**
 * users.faith_tone (V3__expand_identity_emotion.sql, DEFAULT 'balanced') 이 마감 문구
 * 9조합의 두 번째 축인데, 게스트 세션에는 이 값을 프론트로 내려주는 엔드포인트가 아직 없다.
 * DB 기본값과 같은 'balanced' 로 고정한다 — 임의 선택이 아니라 서버 기본값 미러링이다.
 * (peter·solomon·daniel·esther 페이지가 같은 이유로 같은 상수를 쓴다.)
 */
const DEFAULT_FAITH_TONE = "balanced" as const;

export default function JacobPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  /**
   * 다음 씬 상단에 한 번만 얹는 짧은 글. 이 인물에서는 한 자리에서만 쓴다 —
   * Scene 1 을 건너뛴 사람에게 저작이 남겨 둔 요약 한 줄
   * (`skip_bridge_narration_ko`). 다음 진행에서 지운다 — 남아 있으면 이야기 위에
   * 겹쳐 읽히는 다른 본문이 된다.
   *
   * 에스더와 달리 Scene 3 에는 얹을 것이 없다. 저작의 `card_text_ko` 넷은 라벨 자체를
   * 되풀이하는 문자열이라 화면에 다시 띄울 내용이 없고, 여기서 새 문장을 지어 넣으면
   * 그건 아무도 검토하지 않은 세 줄이 된다 — 세 줄의 길이·온도 차이가 곧 등급이다.
   */
  const [echo, setEcho] = useState<string | null>(null);
  // Scene 3 에서 고른 라벨. null 은 "아직/끝내 안 골랐다" 이자 정상 경로다.
  const [returnLabel, setReturnLabel] = useState<ReturnLabel | null>(null);
  // 이탈 버튼을 눌러 안내문을 펼친 상태. 펼치는 것만으로는 아무 일도 일어나지 않는다.
  const [offrampOpen, setOfframpOpen] = useState(false);

  const start = useMutation({
    mutationFn: () => startMission("jacob", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("jacob", scene!.sessionId, sceneId, decision),
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
    Scene 5 마감 — return_label × faith_tone 9 조합 + 라벨 없음 1.

    ⚠️ 라벨 없음(`default`)은 에스더와 같은 모양이다 — 톤 셋이 아니라 한 문안(`all`)이다.
    라벨이 없는 사람에게 임재 강도를 조절할 근거가 없다는 저작 판단이고, 그래서 톤을
    먼저 찾고 없으면 `all` 로 떨어진다. 다니엘 페이지를 그대로 베껴 오면 이 사람은
    마감 문구를 못 받는다.

    열 문장 전부가 「이것은 야곱에게 있었던 일이며, 당신의 실제 관계에 적용하지 않아도
    된다.」로 끝난다. 화면에서 그 꼬리를 잘라 내지 않는다 — 잘리는 순간 마감은 오늘
    여기 앉은 사람에게 내려지는 지시가 된다.
  */
  const closingText = useMemo(() => {
    if (scene?.currentScene !== 5) return null;
    const texts = extras.closing_texts as
      Record<string, Record<string, string> | string> | undefined;
    if (!texts) return null;
    const pick = (node: Record<string, string> | string | undefined) => {
      if (!node) return null;
      if (typeof node === "string") return node;
      return node[DEFAULT_FAITH_TONE] ?? node.all ?? null;
    };
    if (returnLabel) {
      const chosen = pick(texts[returnLabel]);
      if (chosen) return chosen;
    }
    return pick(texts.default);
  }, [scene?.currentScene, extras, returnLabel]);

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
    `conditionalBlockId` 는 서버가 축약 블록을 조립했을 때만 payload 에 박힌다
    (`DecideSceneUseCase.altBlockPayload`). Scene 5 의 건너뛰기가 정확히 그 경로다 —
    씬 번호가 안 바뀌므로 이 한 줄이 없으면 같은 카드가 영원히 다시 뜬다.
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
  // 씬마다 선언된 성경 참조. extras 가 아니라 payload 최상위다
  // (ScenePayloadAssembler.build — `sc.scriptureRef?.let { p["scriptureRef"] = it }`).
  const scriptureRef = payload.scriptureRef as string | undefined;
  const additionalRefs = field<string[]>("additional_refs");
  const captions = field<Caption[]>("captions") ?? [];
  const options = field<Option[]>("options") ?? [];
  const crisisReminder = field<string>("crisis_reminder");
  const dwell = field<Record<string, unknown>>("dwell");
  // Scene 3 — 갈래 앞에 서는 고지. 저작의 R3 선언을 화면 문장으로 옮긴 자리다.
  const preBranchNotice = field<string>("pre_branch_notice_ko");
  // 저작 카드의 셋째 문. 다섯 씬 전부의 extras 에 같은 블록이 실려 온다.
  const offramp = field<Offramp>("offramp");

  const advance = (sceneId: number, decision: unknown) => {
    setEcho(null);
    decide.mutate({ sceneId, decision });
  };

  const finish = () =>
    completeMission("jacob", scene.sessionId, "completed").then(
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
          Jacob — Scene {scene.currentScene}/5 · Mode: VR
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
        씬이 직접 둔 위기 안내(Scene 2·4·5). 서버 `CrisisTokenResolver` 가 번호를 치환해
        보낸 문자열이고, 이 렌더가 그 번호가 화면까지 도달하는 마지막 한 칸이다.
        프론트가 번호를 아는 일이 없어야 `ScenarioHotlineRatchetTest` 의 전제가 산다.
      */}
      <CrisisReminder text={crisisReminder} />

      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative bg-cover bg-center bg-stone-900"
        style={{
          backgroundImage: `url(/images/scenes/jacob/${scene.currentScene}.webp)`,
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-stone-900/85 via-stone-900/75 to-amber-950/85" />
        <div className="relative z-10 p-5">
          {needsConsent ? (
            <TriggerWarningGate
              warning={warning!}
              fallbackProse={
                <p>
                  이 미션에서 당신은{" "}
                  <strong>상처를 받은 쪽이 아니라 상처를 준 쪽</strong> 에
                  섭니다. 지금이 버겁다면{" "}
                  <strong>연출 없이 요약으로 넘어가도 괜찮습니다</strong> —
                  건너뛰어도 이야기는 끝까지 이어집니다.
                </p>
              }
              continueLabel={doors?.continueLabel}
              skipLabel={doors?.skipLabel}
              pending={decide.isPending}
              onContinue={() => setConsented(true)}
              /*
                둘째 문은 「건너뛰기」이고 두 카드 모두 `declined_route` 가 없다 —
                이야기를 여기서 마치는 문이 아니다(룻 Scene 3 과 다르다).
                Scene 1 은 서버가 Scene 2 로 점프시키고, Scene 5 는 같은 씬의 축약
                블록으로 갈아 끼운다. `advance` 를 쓰지 않는 이유는 그것이 방금 깔아
                둔 다리를 지우기 때문이다.

                마치는 문은 카드가 아니라 아래의 상시 이탈 버튼이다.
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
              {/* 성경 본문 — 동의 게이트 *안쪽* 이다. Scene 1 은 속임(창 27:19). */}
              <ScenePassage
                reference={scriptureRef}
                additional={additionalRefs}
              />
              {/* Scene 4·5 — 머무는 시간에 정답이 없다는 저작 문구는 사용자에게 보여준다. */}
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
          {/* Scene 1·2 — cinematic. 겪는 자리이고 조작이 없다. */}
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

            세 갈래는 **나란히** 있다. 순서도 단계도 아니고 더 나아간 갈래가 없다.
            여기서 정렬·번호·강조를 얹지 않는다 — 순서는 payload 가 준 그대로이고,
            세 버튼의 스타일이 같은 것이 이 화면에서 R3 를 지키는 실제 방식이다.

            고른 값은 그대로 서버에 보낸다(`decision_key: return_label`). 쓰이는 자리는
            Scene 5 의 마감 문구 하나뿐이고, 어느 갈래를 골라도 다음은 Scene 4 다.
          */}
          {sceneType === "pick_one" && (
            <div className="space-y-3">
              {preBranchNotice && (
                <p className="whitespace-pre-line text-sm leading-relaxed text-[var(--color-warm)]/80 border-l-2 border-[var(--color-primary)]/40 pl-3">
                  {preBranchNotice}
                </p>
              )}
              <div className="grid grid-cols-1 gap-3">
                {options.map((o) => (
                  <button
                    key={o.id}
                    onClick={() => {
                      setReturnLabel(o.id as ReturnLabel);
                      decide.mutate({
                        sceneId: scene.currentScene,
                        decision: { value: o.id },
                      });
                    }}
                    disabled={decide.isPending}
                    className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] text-left transition disabled:opacity-40"
                  >
                    <span className="font-semibold">{o.label_ko}</span>
                  </button>
                ))}
              </div>
              {/* 고르지 않고 지나가는 것도 정본이 정한 정상 경로다. 마감 문구도 받는다. */}
              <button
                onClick={() => advance(scene.currentScene, { value: "next" })}
                disabled={decide.isPending}
                className="w-full py-3 rounded-lg border border-[var(--color-warm)]/25 text-sm text-[var(--color-warm)]/70 hover:border-[var(--color-warm)]/50 disabled:opacity-40"
              >
                고르지 않고 넘어가기
              </button>
            </div>
          )}

          {/*
            Scene 4 — contemplative. 얍복. 머무는 시간에 정답이 없다(먼저 나가도 된다).
            타이머·진행 바·완료 뱃지를 두지 않는다 — 저작의 15초 지속 그립이 이 런타임에
            없지만, 그 동작에 걸려 있던 규칙(실패 판정 없음·재시도 안 셈·기록 없음)은
            그대로다. 성취 UI 를 얹는 순간 창 32:28 의 「이겼음이니라」가 사용자 성공
            라벨로 재사용된다.
          */}
          {sceneType === "contemplative" && (
            <button
              onClick={() => advance(scene.currentScene, { value: "next" })}
              disabled={decide.isPending}
              className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
            >
              {decide.isPending ? "..." : "계속 →"}
            </button>
          )}

          {/*
            Scene 5 — outro. 건너뛴 사람도, 갈래를 안 고른 사람도 여기까지 온다.
            축약 블록(`5_alt`)은 자막만 갈아 끼우고 `closing_texts` 를 덮지 않으므로
            두 경로의 마감이 같다.
          */}
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
                {field<string>("next_scene_suggestion") && (
                  <p className="text-xs text-[var(--color-warm)]/50">
                    다음에 이어보면 좋은 자리 —{" "}
                    {field<string>("next_scene_suggestion")}
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
            저작 카드의 셋째 문 — 「나는 이 자리에 서 있지 않다」.
            다섯 씬 전부에 있고, 마지막 씬에서는 이미 마감 버튼이 있으므로 띄우지 않는다.

            펼치는 것만으로는 아무 일도 일어나지 않는다. 안내문을 읽고 한 번 더 눌러야
            닫힌다 — 이 문을 실수로 밟는 사람이 없어야 한다.
            누르면 마감 문구를 거치지 않고 완료로 닫는다. **마치는 것이지 끝을 받는 것이
            아니다**(`closing_phrase: none`). 결정으로 보내지도 않는다
            (`records_decision: false`) — 서버에 남는 것은 완료 하나뿐이다.
          */}
          {offramp?.notice_ko && sceneType !== "outro" && (
            <div className="pt-1">
              {offrampOpen ? (
                <div className="px-4 py-3 rounded-lg border border-[var(--color-warm)]/25 space-y-3">
                  <p className="text-sm leading-relaxed text-[var(--color-warm)]/80">
                    {offramp.notice_ko}
                  </p>
                  <div className="flex gap-3">
                    <button
                      onClick={finish}
                      className="flex-1 min-h-11 px-4 py-2 rounded-lg border border-[var(--color-primary)]/50 hover:border-[var(--color-primary)] text-sm"
                    >
                      여기서 마치기
                    </button>
                    <button
                      onClick={() => setOfframpOpen(false)}
                      className="flex-1 min-h-11 px-4 py-2 rounded-lg border border-[var(--color-warm)]/25 hover:border-[var(--color-warm)]/50 text-sm text-[var(--color-warm)]/70"
                    >
                      계속 보기
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  onClick={() => setOfframpOpen(true)}
                  className="w-full py-2 text-xs text-[var(--color-warm)]/50 hover:text-[var(--color-warm)]/80 underline underline-offset-4"
                >
                  {offramp.label_ko ?? "나는 이 자리에 서 있지 않다"}
                </button>
              )}
            </div>
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
