"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { NarrationAudioButton } from "@/components/NarrationAudioButton";
import { CrisisReminder } from "@/components/CrisisReminder";
import { SceneBootState } from "@/components/SceneBootState";
import { ScenePassage } from "@/components/ScenePassage";
import {
  TriggerWarningGate,
  readTriggerWarning,
} from "@/components/TriggerWarningGate";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";

/**
 * 룻 미션 — 「무자격자가 속하게 되는 것」. 백엔드 런타임은 2026-08-20(PR #77)부터
 * 열려 있었는데 들어갈 *문* 이 없었다. `Character` enum 의 RUTH 한 줄이 노출이라
 * `/api/game/ruth/start` 는 200 을 주는데 화면이 없어 API 로만 도달했다. 이 페이지가
 * 그 문이다.
 *
 * Scene 1 갈림길(scripture_reading, R4 진입 카드) → Scene 2 빈 손의 귀향(pick_one,
 * 카드는 Scene 1 에 덮임) → Scene 3 이방 여인(grab_and_place, R4 중간 카드) →
 * Scene 4 타작 마당의 밤(scripture_reading, 등급 C · R4 카드) → Scene 5 성문에서(outro).
 *
 * ─────────────── 이 화면이 특별히 조심하는 것 ───────────────
 *
 * 1) **두 문이 서로 다르다.** 룻의 세 카드 중 Scene 3 만 `declined_route: closing` 을
 *    선언한다 — 리포 전체에서 유일하다. 그 카드의 둘째 문은 건너뛰기가 아니라 *거절* 이고
 *    백엔드가 기다리는 결정값도 `"decline"` 이다(SceneSkipResolver). 공용 게이트가
 *    `onSkip("skip" | "decline")` 으로 어느 문인지 알려 주므로 여기서는 그대로 보낸다.
 *    한 값으로 뭉개면 「여기서 마친다」 버튼이 다음 씬을 여는 상태로 조용히 초록이 된다.
 *
 * 2) **버튼 문구를 프론트에 적지 않는다.** 세 카드의 두 문 이름(「사별 장면은 건너뛴다」·
 *    「여기서 마친다」·「건너뛰기 — 성문 장면으로 이동」)은 docs/RUTH-LOCKED-STRINGS.md
 *    가 소유한 고정 문자열이고, payload 의 `consent_card_ko` 안에 그대로 실려 온다.
 *    여기서는 그 정본 줄을 파싱해서 라벨로 쓴다([cardDoors]). 베껴 적으면 정본 개정이
 *    화면에 안 따라오고, 그 드리프트를 재는 검사기도 없다(AC 는 yml·docs 만 대조한다).
 *
 * 3) **본문도 전부 payload 정본이다.** 자막·마감 한 줄·종결 자막·위기 안내 문자열을
 *    프론트에 사본으로 두지 않는다. 특히 위기 안내는 서버 `CrisisTokenResolver` 가
 *    `{{crisis_resources.default}}` 를 정본 번호로 치환해서 보낸다 — 번호를 프론트에
 *    적으면 정책 개정 때 낡은 번호가 남는다.
 *
 * ─────────────── 지금 구현하지 않은 것 (알고 남긴 구멍) ───────────────
 *
 * · **씬 배경 이미지 없음.** `frontend/public/images/scenes/` 에 ruth 폴더가 없다 —
 *   아직 한 장도 저작되지 않았다. 다른 인물처럼 경로를 적어 두면 5장 전부 검은 화면이
 *   되고, `scene-backgrounds.test.ts` 는 CHARACTERS 목록에 ruth 가 없으니 초록이다.
 *   없는 것을 없다고 두고, 대신 대비가 확보되는 단색 배경을 쓴다.
 * · **음성/자막 강도 토글 없음.** 동의 카드 정본에 「[ 자막만 ] [ 약 ] [ 기본 ]」 줄이
 *   있지만, ruth.yml 에는 solomon 의 `intensity_toggle` 같은 *동작을 정의하는 키* 가
 *   없다. 「약」이 무엇을 줄이는지 저작에 없는 상태에서 매핑을 지어내면 그건 구현이
 *   아니라 발명이다. 웹에서는 자막이 기본이고 나레이션 오디오는 눌러야 재생되므로
 *   「자막만」이 사실상 기본 상태다. 공용 게이트가 그 줄을 산문에서 걷어내므로
 *   없는 손잡이를 약속하지도 않는다.
 * · **F66 진입 상태 게이트 없음.** `closing_lines.forced_null_when: F66_entry_state_gate`
 *   는 백엔드 진입 조회가 집행하기로 한 것이고(ruth.yml `machine_enforced: false`),
 *   아직 구현돼 있지 않다. 프론트가 흉내 내면 두 곳이 서로 다르게 판정한다.
 */
type Scene = JosephStartResponse;

interface Caption {
  speaker_ko?: string;
  ref?: string;
  text_ko?: string;
}

interface SceneInteraction {
  id?: string;
  type?: string;
  decision_key?: string;
  prompt_ko?: string;
  options?: Array<{ value?: string; label_ko?: string }>;
}

interface ClosingLines {
  selected_by?: string;
  variants?: Record<string, string>;
}

interface ClosingScreen {
  closing_caption?: { ref?: string; text_ko?: string };
}

/** 카드 정본의 UI 지시줄 — `[계속한다]  [여기서 마친다]` 처럼 줄 전체가 대괄호다. */
const DOOR_LINE = /^(?:\[[^\]]*\]\s*)+$/;

/**
 * 동의 카드 정본에서 **두 문의 이름** 을 꺼낸다.
 *
 * 세 카드가 서로 다른 이름을 쓴다 — 「사별 장면은 건너뛴다」(Scene 1) · 「여기서
 * 마친다」(Scene 3) · 「건너뛰기 — 성문 장면으로 이동」(Scene 4). 이건 문구 취향이
 * 아니라 **그 문이 무엇을 하는지에 대한 설명** 이라, 공용 기본값("이 장면은
 * 건너뛸게요 →")으로 덮으면 Scene 3 에서는 거짓말이 된다.
 *
 * 파싱에 실패하면 undefined 를 준다 — 게이트 기본 라벨로 돌아가고, 문 자체는 남는다.
 * 라벨을 못 읽었다고 나갈 문을 없애는 쪽이 훨씬 나쁘다.
 *
 * 「음성/자막 강도: [ 자막만 ] …」 줄은 대괄호로 *시작하지 않으므로* 여기 안 걸린다.
 */
export function cardDoors(
  consentCardKo?: string,
): { continueLabel: string; skipLabel: string } | undefined {
  const line = (consentCardKo ?? "")
    .split("\n")
    .map((l) => l.trim())
    .find((l) => DOOR_LINE.test(l));
  if (!line) return undefined;

  const doors = Array.from(line.matchAll(/\[([^\]]*)\]/g))
    .map((m) => m[1].trim())
    .filter((t) => t.length > 0);
  if (doors.length < 2) return undefined;

  return { continueLabel: doors[0], skipLabel: doors[1] };
}

export default function RuthPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [consented, setConsented] = useState(false);
  /** 직전 씬에서 넘어온 정본 한 줄 (Scene 1 건너뛰기의 다리 나레이션). */
  const [echo, setEcho] = useState<string | null>(null);
  /** Scene 2 의 belonging_label. 미선택은 실패가 아니라 null 이다. */
  const [belonging, setBelonging] = useState<string | null>(null);
  /** Scene 3 이삭 줍기를 한 번이라도 했는가. **개수는 세지 않는다** (yield_display: false). */
  const [gleaned, setGleaned] = useState(false);
  const [history, setHistory] = useState<string[]>([]);

  const start = useMutation({
    mutationFn: () => startMission("ruth", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("ruth", scene!.sessionId, sceneId, decision),
    onSuccess: (d) => {
      setScene(d);
      setConsented(false);
      setHistory((h) => [
        ...h,
        String((d.scenePayload as Record<string, unknown>).title ?? "—"),
      ]);
    },
  });

  useEffect(() => {
    if (!scene && !start.isPending && !start.isError) start.mutate();
  }, [scene, start]);

  /*
    payload·extras 를 메모한다. `?? {}` 를 그냥 두면 매 렌더 새 객체가 되고, 그 객체를
    의존성으로 쓰는 아래 useMemo 가 매번 무효화된다.

    두 겹인 이유 — 로더가 표준 9필드만 걷어내므로 yml 의 `extras:` 블록이 한 겹 더
    들어간다. 즉 `trigger_warning`·`consent_coverage` 는 payload 루트에, 자막·마감 줄·
    종결 화면은 `payload.extras` 안에 있다.
  */
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

  const advance = (sceneId: number, decision: unknown) => {
    setEcho(null);
    decide.mutate({ sceneId, decision });
  };

  /*
    카드를 거절하면 백엔드는 `{ type: "end" }` 만 돌려준다(DecideSceneUseCase 의
    Skip.Closing). ruth.yml 의 `closing_screen.reached_by` 는 `consent_declined_sentinel`
    을 포함하지만 **그 화면은 이 경로로 오지 않는다** — 저작된 종결 자막(SR-1)이
    payload 에 실리지 않는다. 여기서 문구를 지어내면 리포 어디에도 없는 마지막 문장이
    생기므로, 마쳤다는 사실만 알리고 나가는 문을 준다. 남은 구멍은 백엔드 쪽이다.
  */
  const ended = payload.type === "end";

  const title = (payload.title as string) ?? "Scene";
  const rawType = (payload.type as string) ?? "";
  const interaction = (payload.interaction as string) ?? "";
  const sceneType = rawType === "interaction" ? interaction : rawType;

  // 씬마다 선언된 성경 참조. extras 가 아니라 payload 최상위다
  // (ScenePayloadAssembler.build — `sc.scriptureRef?.let { p["scriptureRef"] = it }`).
  // 종결 화면(`type: "end"`)에는 참조가 없다 — 그때는 컴포넌트가 null 을 낸다.
  const scriptureRef = payload.scriptureRef as string | undefined;
  // 절 단위 관례상 편 전체를 쓰는 씬은 나머지 절을 `additional_refs` 로 편다
  // (extras 안에 산다 — 그래서 payload 직독이 아니라 field 로 읽는다).
  const additionalRefs = field<string[]>("additional_refs");
  const warning = readTriggerWarning(payload);
  const needsConsent = !ended && !!warning && !consented;
  const doors = cardDoors(warning?.consent_card_ko);

  const captions = field<Caption[]>("captions") ?? [];
  const interactions = field<SceneInteraction[]>("interactions") ?? [];
  const pick = interactions.find((i) => i.type === "pick_one");
  const grab = interactions.find((i) => i.type === "grab_and_place");

  const valuePrompt = field<string>("value_prompt");
  const closingLines = field<ClosingLines>("closing_lines");
  const closingScreen = field<ClosingScreen>("closing_screen");
  /*
    마감 한 줄은 Scene 2 의 선택으로 갈린다(selected_by: belonging_label). 미선택이면
    default_ko 다 — 세 갈래 전부 저작돼 있고, 없는 갈래를 프론트가 메우지 않는다.
  */
  const closingLine = closingLines?.variants
    ? ((belonging ? closingLines.variants[`${belonging}_ko`] : undefined) ??
      closingLines.variants.default_ko)
    : undefined;

  /** 둘째 문 — 어느 문인지는 게이트가 카드의 `declined_route` 를 보고 알려 준다. */
  const onSecondDoor = (action: "skip" | "decline") => {
    if (action === "decline") {
      advance(scene.currentScene, { value: "decline" });
      return;
    }
    /*
      Scene 1 진입 카드는 Scene 1·2 를 함께 덮고 목적지가 3 이다(next 2 가 아니다).
      건너뛴 구간을 잇는 한 줄이 카드 안에 저작돼 있으므로 그걸 그대로 띄운다 —
      건너뛴 사람이 "룻이 누구인지" 를 모른 채 Scene 3 에 도착하지 않게 하는 줄이다.
    */
    setEcho(warning?.skip_bridge_narration_ko ?? null);
    decide.mutate({
      sceneId: scene.currentScene,
      decision: { value: "skip" },
    });
  };

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Ruth — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{ended ? "—" : title}</h1>
      </header>

      {echo && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 italic text-sm text-[var(--color-warm)]/90">
          <p className="whitespace-pre-line">{echo}</p>
        </section>
      )}

      {/* Scene 5 가 씬 안에 직접 둔 위기 안내. 서버가 번호를 치환해서 보낸 문자열이다. */}
      <CrisisReminder text={field<string>("crisis_reminder")} />

      <section className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 bg-gradient-to-b from-stone-900 via-stone-950 to-black">
        <div className="p-5">
          {ended ? (
            <p className="text-sm text-[var(--color-warm)]/85 leading-relaxed">
              여기서 마쳤습니다. 남은 장면은 열지 않았습니다.
            </p>
          ) : needsConsent ? (
            <TriggerWarningGate
              warning={warning!}
              /*
                룻의 세 카드는 전부 consent_card_ko 정본을 싣고 있어 이 산문은 실제로는
                쓰이지 않는다. 그래도 비워 두지 않는 이유는, 정본이 빠지는 날 카드가
                제목과 버튼만 남는 걸 막기 위해서다.
              */
              fallbackProse={
                <p>
                  다음 장면에는 지금 버겁게 느껴질 수 있는 내용이 있습니다.
                  <strong> 어느 지점에서든 멈추거나 나갈 수 있습니다.</strong>
                </p>
              }
              continueLabel={doors?.continueLabel}
              skipLabel={doors?.skipLabel}
              pending={decide.isPending}
              onContinue={() => setConsented(true)}
              onSkip={onSecondDoor}
            />
          ) : (
            <>
              <Captions captions={captions} />
              {/* 성경 본문 — 동의 게이트 *안쪽* 이다(룻의 세 카드가 이 자리를 막는다). */}
              <div className="mt-4">
                <ScenePassage
                  reference={scriptureRef}
                  additional={additionalRefs}
                />
              </div>
            </>
          )}
        </div>
      </section>

      {!needsConsent && (
        <section className="max-w-3xl mx-auto w-full space-y-3">
          {/* Scene 1·4 — scripture_reading. 자동 진행 없음(caption_pacing.advance: user). */}
          {!ended && sceneType === "scripture_reading" && (
            <button
              onClick={() => advance(scene.currentScene, { value: "next" })}
              disabled={decide.isPending}
              className="w-full min-h-11 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
            >
              {decide.isPending ? "..." : "계속 →"}
            </button>
          )}

          {/* Scene 2 — pick_one. 타이머 없음 · 기본 선택 없음 · 채점 없음. */}
          {!ended && sceneType === "pick_one" && (
            <div className="space-y-3">
              {pick?.prompt_ko && (
                <p className="text-sm text-[var(--color-warm)]/80">
                  {pick.prompt_ko}
                </p>
              )}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {(pick?.options ?? []).map((o) => (
                  <button
                    key={o.value}
                    onClick={() => {
                      setBelonging(o.value ?? null);
                      advance(scene.currentScene, { value: o.value });
                    }}
                    disabled={decide.isPending}
                    className="min-h-11 px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
                  >
                    {o.label_ko}
                  </button>
                ))}
              </div>
              {/*
                미선택은 실패가 아니라 null 이다(`unselected_value: null`). 이 문이 없으면
                "고르지 않음" 이 화면에서 도달 불가능해지고, 상실 곁에서의 거리를 반드시
                고르게 만드는 기계가 된다 — yml 이 명시적으로 거부한 결말이다.
              */}
              <button
                onClick={() => {
                  setBelonging(null);
                  advance(scene.currentScene, { value: null });
                }}
                disabled={decide.isPending}
                className="w-full min-h-11 py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm text-[var(--color-warm)]/90 disabled:opacity-40"
              >
                고르지 않고 계속 →
              </button>
            </div>
          )}

          {/* Scene 3 — grab_and_place. 반복 가능 · 목표 개수 없음 · 수확량 미표시. */}
          {!ended && sceneType === "grab_and_place" && (
            <div className="space-y-3">
              {grab?.prompt_ko && (
                <p className="text-sm text-[var(--color-warm)]/80">
                  {grab.prompt_ko}
                </p>
              )}
              {/*
                개수를 세어 보여주지 않는다(`yield_display: false` · `scoring: none` ·
                `target_count: null`). 숫자가 뜨는 순간 이 동작은 과제가 되고, 몸을
                굽히는 자세가 곧 의미라는 저작 의도가 성취도로 바뀐다.

                그렇다고 아무 반응이 없는 버튼을 두지도 않는다 — 눌러도 화면이 꿈쩍
                않으면 사용자는 고장으로 읽고, 스크린리더 사용자에게는 눌렸다는 사실
                자체가 전달되지 않는다. 「주웠다」는 사실만 알리고 **몇 개인지는
                말하지 않는다.** 그래서 아래 문구에 수치가 없다.
              */}
              <button
                type="button"
                onClick={() => setGleaned(true)}
                className="w-full min-h-11 py-3 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] text-sm"
              >
                이삭을 줍는다
              </button>
              <p
                aria-live="polite"
                className="text-xs text-[var(--color-warm)]/50 text-center"
              >
                {gleaned ? "이삭을 주워 담았습니다." : ""}
              </p>
              <button
                onClick={() => advance(scene.currentScene, { value: "next" })}
                disabled={decide.isPending}
                className="w-full min-h-11 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
              >
                {decide.isPending ? "..." : "계속 →"}
              </button>
            </div>
          )}

          {/* Scene 5 — outro. 마감 한 줄이 **먼저**, 종결 자막이 그 다음이다(순서 고정). */}
          {!ended && sceneType === "outro" && (
            <div className="space-y-5">
              {closingLine && (
                <div className="text-center space-y-2">
                  <p className="text-base italic text-[var(--color-warm)]/90">
                    {closingLine}
                  </p>
                  <div className="flex justify-center">
                    <NarrationAudioButton
                      text={closingLine}
                      onUnavailable="hide"
                    />
                  </div>
                </div>
              )}

              {closingScreen?.closing_caption?.text_ko && (
                <div className="text-center space-y-1 px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 bg-black/30">
                  <p className="text-sm text-[var(--color-warm)]/90 leading-relaxed">
                    {closingScreen.closing_caption.text_ko}
                  </p>
                  {closingScreen.closing_caption.ref && (
                    <p className="text-[10px] text-[var(--color-warm)]/40">
                      ({closingScreen.closing_caption.ref})
                    </p>
                  )}
                </div>
              )}

              <div className="text-center space-y-2">
                {valuePrompt && (
                  <p className="text-xs text-[var(--color-warm)]/60">
                    {valuePrompt}
                  </p>
                )}
                <button
                  onClick={() =>
                    completeMission("ruth", scene.sessionId, "completed").then(
                      () => (location.href = "/"),
                    )
                  }
                  className="min-h-11 px-6 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
                >
                  미션 완료
                </button>
              </div>
            </div>
          )}

          {ended && (
            <div className="text-center">
              <button
                onClick={() =>
                  completeMission("ruth", scene.sessionId, "declined").then(
                    () => (location.href = "/"),
                  )
                }
                className="min-h-11 px-6 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
              >
                홈으로
              </button>
            </div>
          )}

          {decide.isError && (
            <p className="text-red-400 text-sm mt-2">
              오류: {(decide.error as Error).message}
            </p>
          )}

          {/* exit_button — ruth.yml 이 5개 Scene 과 종결 화면 전부에 요구하는 상시 나가기. */}
          <div className="pt-2 flex gap-3">
            <Link
              href="/"
              className="flex-1 flex items-center justify-center min-h-11 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
            >
              ← 홈
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
 * 자막 렌더 — 화자 이름을 **항상** 붙인다.
 *
 * SR-8 은 Scene 2 에만 `speaker_attribution_persistent: true` 를 걸어 두었지만, 그건
 * 그 씬이 최악의 경우이기 때문이지(나오미의 탄식이 1인칭 XR 에서 사용자 자신의 문장으로
 * 읽힌다) 다른 씬은 붙이지 말라는 뜻이 아니다. 룻의 자막은 전부 특정 화자의 말이고,
 * 이름 없이 흘리면 「나는 이방 여인이거늘」도 같은 방식으로 오독된다.
 */
function Captions({ captions }: { captions: Caption[] }) {
  const joined = captions
    .map((c) => c.text_ko)
    .filter(Boolean)
    .join("\n");
  if (captions.length === 0) return null;

  return (
    <div className="space-y-4">
      {captions.map((c, i) => (
        <div key={i} className="space-y-1">
          {c.speaker_ko && (
            <p className="text-[11px] uppercase tracking-wider text-[var(--color-primary)]/70">
              {c.speaker_ko}
            </p>
          )}
          <p className="text-sm text-[var(--color-warm)]/85 leading-relaxed whitespace-pre-line">
            {c.text_ko}
            {c.ref && (
              <span className="ml-2 text-[10px] text-[var(--color-warm)]/40">
                ({c.ref})
              </span>
            )}
          </p>
        </div>
      ))}
      <div className="pt-1">
        <NarrationAudioButton text={joined} onUnavailable="hide" />
      </div>
    </div>
  );
}
