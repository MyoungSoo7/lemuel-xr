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
 * Jacob 미션 — 내가 준 상처 앞에 다시 서기.
 *
 * ⚠️ 이 화면은 **아직 도달할 수 없다.** `Character` enum 에 `JACOB("jacob")` 이 없으면
 * `startMission("jacob")` 이 E_CHARACTER_UNKNOWN 을 받는다. 그 한 줄이 곧 사용자 노출이고,
 * 노출 근거·범위·남은 빚은 `docs/JACOB-RUNTIME-SIGNOFF.md` 에 있으며 그 대장의 결정 줄은
 * **사람이 채운다**(`RuntimeExposureSignoffTest`).
 *
 * 그런데도 화면을 먼저 만드는 이유는 아브라함 때와 같다 — 화면이 없으면 이 인물이
 * *덜 노출된* 게 아니라 **검사 사정권 밖**이 된다. 프론트 검사기 두 개
 * (`check_frontend_trigger_warning.py` · `mission-tap-targets.spec.ts`)가 화면을 읽는다.
 * 룻이 그 사각지대에 있었다.
 *
 * jacob.yml 5 Scene:
 *   1 cinematic(속임 — R4 게이트 ①) → 2 cinematic(부르짖음) → 3 pick_one(이십 년) →
 *   4 contemplative(얍복) → 5 outro(받으니라 — R4 게이트 ②) → complete → 홈.
 *
 * ─────────────── 이 인물의 자리 ───────────────
 *
 * 사용자는 **상처를 준 쪽**에 선다. 트랙 B 에서 이 자리를 다루는 인물은 야곱 하나다.
 * 그래서 이 화면에는 다른 인물에 없는 문이 하나 더 있다 — 아래 「이 자리가 내 자리가
 * 아닐 때」.
 *
 * ─────────────── R4 — 문이 세 개다 ───────────────
 *
 * 동의 카드는 둘이고 **서로를 상속하지 않는다.** Scene 1 에서 역할 고지에 동의한 것이
 * Scene 5 의 대면에 동의한 것은 아니다(`content/jacob/README.md` 의 명시 요구).
 *   · Scene 1 — 목적지가 **정수 2**. 저작이 써 둔 요약 한 줄을 다리로 받는다.
 *   · Scene 5 — 마지막 씬이라 목적지가 **문자열**이고, 서버가 같은 Scene 을 축약 블록으로
 *     다시 조립해 돌려준다(`DecideSceneUseCase.altBlockPayload`).
 *
 * 셋째 문 `not_my_seat_offramp` 는 건너뛰기와 **다르다.** 건너뛰기는 "소재는 괜찮지만
 * 연출이 부담"이고, 이 문은 "이 자리 자체가 내 자리가 아니다"(가족 안에서 상처를 **받은**
 * 쪽)이다. 저작이 5개 Scene 전부에 상시 제공을 요구했고 — 그래서 동의 게이트가 떠 있는
 * 동안에도 닫히지 않는다 — 기록을 남기지 않고 마무리 문구도 없이 끝난다.
 *
 * ⚠️ `TriggerWarningGate` 는 버튼을 **딱 둘** 만 그린다. 그래서 이 셋째 문을 카드 안에
 *    넣을 수 없다. `declined_route` 를 채워 둘째 버튼을 「거절」로 바꾸는 방법도 있지만
 *    그건 건너뛰기를 없애는 것이라 저작과 어긋난다. 별도 컨트롤로 그리는 것이 그
 *    제약을 숨기지 않는 유일한 방법이고, 그 차이(백엔드 decline 라우트가 아니라
 *    클라이언트 `completeMission`)는 대장에 등재돼 있다.
 *
 * ⚠️ Scene 5 의 축약 payload 에는 `trigger_warning` 이 **그대로 살아 있다.** 동의 여부만
 *    보고 게이트를 세우면 건너뛴 사람에게 같은 카드가 영원히 다시 뜬다(sceneId 가 5 그대로라
 *    씬 전환 초기화도 그를 구해 주지 않는다). 그래서 게이트 조건에 `conditionalBlockId`
 *    부재를 함께 건다 — 다니엘·아브라함과 같은 함정이고 같은 방식으로 막는다.
 *
 * ─────────────── 본문 자구 ───────────────
 *
 * 성경 자구 사본이 이 파일에 0개인 것은 의도다(`check_monologue_quotes.py`). 자막은
 * `"<자구> (창 32:26)"` 형태로 참조까지 붙어서 오므로 여기서 참조를 다시 만들지 않는다.
 * `*_note`·`*_ban`·`*_lock` 필드는 저작자용 가드 주석이지 사용자 카피가 아니라 렌더하지 않는다.
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
type ReturnLabel = "send_ahead" | "go_afraid" | "stay_and_pray";

/** 「이 자리가 내 자리가 아닐 때」 — 건너뛰기와 다른 문. 5개 Scene 상시. */
interface Offramp {
  label_ko?: string;
  notice_ko?: string;
}

/**
 * `users.faith_tone` (V3__expand_identity_emotion.sql, DEFAULT 'balanced') 이 마감 문구
 * 9조합의 두 번째 축인데, 게스트 세션에는 이 값을 프론트로 내려주는 엔드포인트가 아직 없다.
 * DB 기본값과 같은 'balanced' 로 고정한다 — 임의 선택이 아니라 서버 기본값 미러링이다.
 * (daniel·peter·solomon 페이지가 같은 이유로 같은 상수를 쓴다.)
 */
const DEFAULT_FAITH_TONE = "balanced" as const;

export default function JacobPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  /**
   * 건너뛴 구간을 잇는, 저작이 써 둔 요약 한 줄(`skip_bridge_narration_ko`).
   *
   * Scene 1 을 건너뛴 사람은 야곱이 무엇을 했는지 모르는 채 Scene 2 의 절규 앞에 선다.
   * 그러면 에서가 왜 우는지 알 수 없는 장면이 된다. 건너뛰기가 이야기를 잃는 일이
   * 되지 않게 하려고 놓아 둔 다리다.
   */
  const [echo, setEcho] = useState<string | null>(null);
  // Scene 3 에서 고른 돌아가는 방식. null 은 "아직/끝내 안 골랐다" 이자 정상 경로다.
  const [returnLabel, setReturnLabel] = useState<ReturnLabel | null>(null);
  // 이탈로 안내문을 펼친 상태. 여는 것만으로는 아무 일도 일어나지 않는다.
  const [offrampOpen, setOfframpOpen] = useState(false);
  const [leaving, setLeaving] = useState(false);

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
      setOfframpOpen(false);
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
    Scene 5 마감 한 줄 — `extras.closing_texts` 정본에서 고른다.
    라벨 3종은 톤 분기를 갖고, 라벨 없음(`default`)은 톤 분기 없는 문자열 하나다.
    저작에 없는 세 문을 지어내지 않은 결과이고, 그래서 두 모양을 다 받는다 —
    한 모양만 가정하면 고르지 않은 사람의 마감이 조용히 사라진다. 그 사람이 이 미션에서
    가장 취약한 자리다.
    프론트가 문구를 갖고 있지 않은 것은 의도다 — 정본이 개정되면 화면도 같이 바뀐다.
  */
  const closingText = useMemo(() => {
    if (scene?.currentScene !== 5) return null;
    const texts = extras.closing_texts as
      | Record<string, Record<string, string> | string>
      | undefined;
    if (!texts) return null;
    if (returnLabel) {
      const byTone = texts[returnLabel];
      if (byTone && typeof byTone === "object") {
        const t = byTone[DEFAULT_FAITH_TONE];
        if (t) return t;
      }
    }
    const fallback = texts.default;
    if (fallback && typeof fallback === "object") {
      return fallback[DEFAULT_FAITH_TONE] ?? null;
    }
    return typeof fallback === "string" ? fallback : null;
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
  const alreadySkipped = typeof payload.conditionalBlockId === "string";
  const needsConsent = !!warning && !consented && !alreadySkipped;
  /*
    두 문의 이름은 저작 정본(`consent_card_ko` 의 마지막 줄)이 소유한다. 여기 베껴 적으면
    정본이 개정돼도 화면은 옛 이름을 계속 쓰고, 그 어긋남을 재는 검사기가 없다.
    두 카드의 문 이름이 서로 다르다 — Scene 1 은 「들어간다 / 연출 없이 요약으로 본다」,
    Scene 5 는 「그대로 본다 / 재회 연출 없이 넘어간다」.
    못 읽으면 게이트 기본 라벨로 돌아간다 — 문은 남는다.
  */
  const doors = cardDoors(warning?.consent_card_ko);

  const anchor = field<string>("anchor");
  // 씬마다 선언된 성경 참조. extras 가 아니라 payload 최상위다(ScenePayloadAssembler.build).
  const scriptureRef = payload.scriptureRef as string | undefined;
  const additionalRefs = field<string[]>("additional_refs");
  const captions = field<Caption[]>("captions") ?? [];
  const options = field<Option[]>("options") ?? [];
  const crisisReminder = field<string>("crisis_reminder");
  const dwellNote = (field<Record<string, unknown>>("dwell")?.note_ko ??
    undefined) as string | undefined;
  const offramp = field<Offramp>("not_my_seat_offramp");

  // 다리 한 줄은 *건너뛴 다음 씬 한 번* 만 보여 준다. 그 뒤로도 남아 있으면
  // 이야기 위에 겹쳐 읽히는 다른 본문이 된다.
  const advance = (sceneId: number, decision: unknown) => {
    setEcho(null);
    decide.mutate({ sceneId, decision });
  };

  /*
    이탈로. `records_decision: false` · `closing_phrase: none` · `mission_complete: true` 라
    결정을 서버에 보내지 않고 세션만 완료 처리한다. 「미완」으로 남기지 않는 것이 핵심이다 —
    이 자리가 자기 자리가 아니라고 말한 사람에게 미완 배지를 남기면 그것이 곧 압박이다.
  */
  const leaveSeat = () => {
    setLeaving(true);
    completeMission("jacob", scene.sessionId, "completed")
      .then(() => (location.href = "/"))
      .catch(() => setLeaving(false));
  };

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
        씬이 직접 둔 위기 안내(Scene 2·5). 서버 `CrisisTokenResolver` 가 번호를 치환해
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
        {/*
          배경 이미지는 아직 없다(`generate_all_scenes.py` 에 jacob 프롬프트가 없다).
          그래서 실제로 보이는 것은 아래 그라디언트다 — 룻·아브라함이 이미지 생성 전에
          출시된 것과 같은 상태이고, 대장에 적어 두었다.
        */}
        <div className="absolute inset-0 bg-gradient-to-b from-stone-900/85 via-stone-900/75 to-amber-950/85" />
        <div className="relative z-10 p-5">
          {needsConsent ? (
            <TriggerWarningGate
              warning={warning}
              fallbackProse={
                scene.currentScene === 1 ? (
                  <p>
                    이 미션에서 당신은{" "}
                    <strong>상처를 준 쪽의 자리</strong> 에 섭니다. 가족 안의
                    기만과 속은 형제의 절규가 나옵니다. 지금이 버겁다면{" "}
                    <strong>요약으로 보셔도 괜찮습니다</strong> — 건너뛰어도
                    이야기는 끝까지 이어집니다.
                  </p>
                ) : (
                  <p>
                    다음 장면에는 <strong>가해자로서의 대면과 재회</strong> 가
                    나옵니다. 재회가 안전하지 않은 관계가 있습니다. 지금이
                    버겁다면 <strong>건너뛰셔도 괜찮습니다</strong> —
                    건너뛰셔도 마무리는 그대로 받으십니다.
                  </p>
                )
              }
              continueLabel={doors?.continueLabel}
              skipLabel={doors?.skipLabel}
              pending={decide.isPending}
              onContinue={() => setConsented(true)}
              /*
                두 카드 모두 둘째 문은 「건너뛰기」이고 `declined_route` 가 없다 —
                이야기를 여기서 마치는 문이 아니다(룻 Scene 3 과 다르다).
                Scene 1 은 Scene 2 로 점프하고, Scene 5 는 같은 씬의 축약본이 된다.
                `advance` 를 쓰지 않는 이유는 그것이 다리 한 줄을 지우기 때문이다.
              */
              onSkip={() => {
                setEcho(warning.skip_bridge_narration_ko ?? null);
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
            세 값 전부 본문에 있는 반응이고 틀린 답이 없다. 고른 값은 그대로 서버에 보낸다 —
            저작이 `decision_key: return_label` 로 선언했고, 쓰이는 자리는 Scene 5 의 마감
            한 줄 하나뿐이다. 점수도 순위도 없고, 어느 값을 골라도 다음은 Scene 4 다.
            특히 「가지 않고 머문다」는 유예가 아니라 그 자체로 종결된 상태다.
          */}
          {sceneType === "pick_one" && (
            <div className="space-y-3">
              <div className="grid grid-cols-1 gap-3">
                {options.map((o) => (
                  <button
                    key={o.id}
                    onClick={() => {
                      setReturnLabel(o.id as ReturnLabel);
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
            </div>
          )}

          {/*
            Scene 4 — contemplative. 저작의 정점은 두 손으로 붙잡고 버티는 VR 제스처인데
            웹에는 대응물이 없어서 체류로 내려앉았다(대장에 등재된 격차다).
            실패 판정이 없다는 성질만은 양쪽에서 같다 — 먼저 나가도 재시도 횟수가 남지 않는다.
          */}
          {sceneType === "contemplative" && (
            <div className="space-y-2">
              <button
                onClick={() => advance(scene.currentScene, { value: "next" })}
                disabled={decide.isPending}
                className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
              >
                {decide.isPending ? "..." : "계속 →"}
              </button>
              {dwellNote && (
                <p className="text-xs text-[var(--color-warm)]/50 text-center">
                  {dwellNote}
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
                {field<string>("next_scene_suggestion") && (
                  <p className="text-xs text-[var(--color-warm)]/50">
                    다음에 이어보면 좋은 자리 —{" "}
                    {field<string>("next_scene_suggestion")}
                  </p>
                )}
                <button
                  onClick={() =>
                    completeMission("jacob", scene.sessionId, "completed").then(
                      () => (location.href = "/"),
                    )
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

      {/*
        이탈로 — 5개 Scene 상시. **동의 게이트가 떠 있는 동안에도 닫히지 않는다.**
        가장 필요한 순간이 바로 그 카드 앞이기 때문이다. 문구는 정본이 소유하므로
        `notice_ko` 가 없으면 아예 그리지 않는다 — 이름 없는 종료 버튼보다 없는 편이 낫다.
      */}
      {offramp?.notice_ko && (
        <section
          data-testid="not-my-seat"
          className="max-w-3xl mx-auto w-full mt-6 rounded-lg border border-[var(--color-warm)]/20 p-3"
        >
          <button
            onClick={() => setOfframpOpen((v) => !v)}
            aria-expanded={offrampOpen}
            className="w-full min-h-11 text-left text-sm text-[var(--color-warm)]/70 hover:text-[var(--color-warm)]"
          >
            {offramp.label_ko ?? "이 자리가 내 자리가 아닐 때"}
          </button>
          {offrampOpen && (
            <div className="mt-3 space-y-3">
              <p className="text-sm leading-relaxed text-[var(--color-warm)]/85">
                {offramp.notice_ko}
              </p>
              <button
                onClick={leaveSeat}
                disabled={leaving}
                className="w-full min-h-11 py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm disabled:opacity-40"
              >
                {leaving ? "..." : "여기서 마칩니다"}
              </button>
            </div>
          )}
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
