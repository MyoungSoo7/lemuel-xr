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
 * Esther 미션 — 드러낼 것인가, 언제 어떻게 드러낼 것인가.
 *
 * `Character.ESTHER` 는 2026-08-22 에 열렸다. 근거·범위·남은 빚은
 * `docs/ESTHER-RUNTIME-SIGNOFF.md` 에 있고, 전문 신학·정신건강 검토는 **없다.**
 * 저작이 건 인간 안전검토자 사인오프도 없다 — 해제한 게 아니라 미해소 부채로
 * 대장에 적힌 채 열렸다.
 *
 * esther.yml 5 Scene:
 *   1 cinematic(이름 없는 민족 — R4 게이트) → 2 cinematic(성문 밖의 통곡) →
 *   3 pick_one(법과 삼십 일) → 4 contemplative(사흘) →
 *   5 outro(안 뜰, 금 규, 세 번의 물음) → complete → 홈.
 *
 * ─────────────── 이 화면이 지켜야 하는 것 ───────────────
 *
 * **드러내는 것이 정답인 묵상이 아니다.** 이 문장이 이 인물의 축이고, 화면에서 무너지는
 * 길은 둘이다 — 세 카드에 등급을 주는 것, 그리고 고르지 않은 사람을 미완으로 만드는 것.
 * 그래서 여기서 지키는 것:
 *
 *  · 카드 순서는 **payload 가 준 순서 그대로** 다. 여기서 정렬하지 않는다. 정본의 첫 자리는
 *    침묵(`nondisclosure`)이고, 서버가 세션마다 섞어 보낼 수 있다
 *    (`card_order_policy: shuffle_per_session`). 프론트가 정렬을 얹으면 그 설계가 죽는다.
 *  · 번호·stepper·진행 막대를 붙이지 않는다(`card_render_style: flat_siblings`).
 *    세 장이 「개시를 향한 단계」로 읽히는 순간 맨 앞 카드는 「아직 거기」가 된다.
 *  · **고르지 않고 넘어가는 문이 항상 있다.** 그 사람도 Scene 5 에서 마감 문구를 받는다
 *    (`closing_texts.default.all`). 무응답은 실패가 아니라 정상 경로다.
 *
 * ─────────────── payload 를 읽는 방식 ───────────────
 *
 * 1) 본문은 전부 `extras.captions` 정본이다. 프론트에 성경 자구 사본이 0개인 것은 의도다
 *    — `check_monologue_quotes.py` 가 프론트에 자구가 있으면 빨개진다. 자막은 이미
 *    `"<자구> (에 4:14)"` 형태로 참조를 달고 오므로 여기서 참조를 다시 만들지 않는다.
 *
 * 2) `*_note` 필드(order_note·npc_note·observe_note·options_note·card_ui_note·
 *    delay_note·achievement_note·closing_texts_note·hidden_god_note·scope_note 등)는
 *    **저작자용 가드 주석** 이지 사용자 카피가 아니다. 렌더하지 않는다.
 *    `pre_branch_notice_ko` 는 이름이 `_ko` 로 끝나는 대로 **사용자 카피** 다 — 렌더한다.
 *    `mandatory_clause` 도 렌더하지 않는다. 그건 자막 안에 이미 들어 있는 문자열의
 *    **선언** 이고, 따로 띄우면 같은 문장이 두 번 나온다.
 *
 * ─────────────── R4 — 이 인물이 다니엘과 다른 지점 ───────────────
 *
 * 동의 카드가 **하나뿐이고 Scene 1 에 있다.** 다니엘(둘)·룻(다중 커버)과 다르다.
 * 건너뛰기 목적지는 정수 `2` — 서버가 Scene 2 로 점프시킨다.
 *
 * 저작은 이 건너뛰기를 Scene 1 안의 40초짜리 대체 라우트로 설계했는데, 엔진은 문자열
 * 목적지를 `next: null` 인 마지막 Scene 에서만 허용한다. 그래서 그 라우트의 자막을
 * `skip_bridge_narration_ko` 석 줄로 옮겼고, 이 화면이 Scene 2 상단에 **한 번** 깔아 준다
 * (`echo`). 건너뛴 사람이 은폐(2:10)와 고발(3:8)을 모르는 채 통곡 앞에 서면
 * 「무엇 때문에 우는지」가 사라진다. 전문은 `docs/MVP-ESTHER-CONTENT.md` §4.
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

/** Scene 3 의 세 카드. 마감 문구 표의 첫 축이며, 고르지 않으면 `default` 로 간다. */
type DiscloseLabel = "nondisclosure" | "gather_first" | "speak_now";

/**
 * users.faith_tone (V3__expand_identity_emotion.sql, DEFAULT 'balanced') 이 마감 문구
 * 9조합의 두 번째 축인데, 게스트 세션에는 이 값을 프론트로 내려주는 엔드포인트가 아직 없다.
 * DB 기본값과 같은 'balanced' 로 고정한다 — 임의 선택이 아니라 서버 기본값 미러링이다.
 * (peter·solomon·daniel 페이지가 같은 이유로 같은 상수를 쓴다.)
 */
const DEFAULT_FAITH_TONE = "balanced" as const;

export default function EstherPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  /**
   * 다음 씬 상단에 한 번만 얹는 짧은 글.
   *
   * 두 자리에서 쓴다 — (a) Scene 1 을 건너뛴 사람에게 저작이 남겨 둔 석 줄
   * (`skip_bridge_narration_ko`), (b) Scene 3 에서 카드를 고른 사람에게 저작이 남겨 둔
   * 한 줄(`acknowledgements`). 둘 다 「지나온 자리를 잇는 한 마디」라는 같은 성격이고,
   * 다음 진행에서 지운다 — 남아 있으면 이야기 위에 겹쳐 읽히는 다른 본문이 된다.
   */
  const [echo, setEcho] = useState<string | null>(null);
  // Scene 3 에서 고른 라벨. null 은 "아직/끝내 안 골랐다" 이자 정상 경로다.
  const [discloseLabel, setDiscloseLabel] = useState<DiscloseLabel | null>(
    null,
  );

  const start = useMutation({
    mutationFn: () => startMission("esther", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("esther", scene!.sessionId, sceneId, decision),
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
    Scene 5 마감 — disclose_label × faith_tone 9 조합 + 라벨 없음 1.

    ⚠️ 라벨 없음(`default`)은 다니엘과 **모양이 다르다.** 다니엘의 default 는 톤 셋으로
    나뉘어 있지만, 에스더의 default 는 한 문안(`all`)이다. 저작이 명시적으로 쪼개지 않기로
    했다 — 라벨이 없는 사람에게 임재 강도를 조절할 근거가 없다는 판단이다.
    그래서 여기서는 톤을 먼저 찾고, 없으면 `all` 로 떨어진다. 다니엘 페이지를 그대로
    베껴 오면 이 사람은 마감 문구를 못 받는다.
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
    if (discloseLabel) {
      const chosen = pick(texts[discloseLabel]);
      if (chosen) return chosen;
    }
    return pick(texts.default);
  }, [scene?.currentScene, extras, discloseLabel]);

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
    (`DecideSceneUseCase.altBlockPayload`). 이 인물의 건너뛰기 목적지는 정수라 그 경로를
    타지 않지만, 조건은 그대로 둔다 — 뒤에 축약 블록이 생기면 이 한 줄이 없을 때
    같은 카드가 영원히 다시 뜬다(sceneId 가 안 바뀌므로 씬 전환 초기화도 못 구한다).
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
  // Scene 3 — 카드 앞에 서는 고지. 저작이 사용자 카피로 쓴 문단이다.
  const preBranchNotice = field<string>("pre_branch_notice_ko");
  const acknowledgements =
    field<Record<string, string>>("acknowledgements") ?? {};

  const advance = (sceneId: number, decision: unknown) => {
    setEcho(null);
    decide.mutate({ sceneId, decision });
  };

  const narration = captions
    .map((c) => c.text_ko ?? "")
    .filter(Boolean)
    .join("\n");

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6 pb-16">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Esther — Scene {scene.currentScene}/5 · Mode: VR
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
        씬이 직접 둔 위기 안내(Scene 1·3·4·5). 서버 `CrisisTokenResolver` 가 번호를 치환해
        보낸 문자열이고, 이 렌더가 그 번호가 화면까지 도달하는 마지막 한 칸이다.
        프론트가 번호를 아는 일이 없어야 `ScenarioHotlineRatchetTest` 의 전제가 산다.
      */}
      <CrisisReminder text={crisisReminder} />

      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative bg-cover bg-center bg-stone-900"
        style={{
          backgroundImage: `url(/images/scenes/esther/${scene.currentScene}.webp)`,
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-stone-900/85 via-stone-900/75 to-amber-950/85" />
        <div className="relative z-10 p-5">
          {needsConsent ? (
            <TriggerWarningGate
              warning={warning!}
              fallbackProse={
                <p>
                  다음 장면에는{" "}
                  <strong>한 민족 전체를 없애라는 국가 조서</strong> 가 본문
                  그대로 나옵니다. 지금이 버겁다면{" "}
                  <strong>조서 낭독 없이 요약으로 넘어가도 괜찮습니다</strong> —
                  건너뛰어도 이야기는 끝까지 이어집니다.
                </p>
              }
              continueLabel={doors?.continueLabel}
              skipLabel={doors?.skipLabel}
              pending={decide.isPending}
              onContinue={() => setConsented(true)}
              /*
                둘째 문은 「건너뛰기」이고 `declined_route` 가 없다 — 이야기를 여기서
                마치는 문이 아니다(룻 Scene 3 과 다르다). 서버가 Scene 2 로 점프시킨다.
                `advance` 를 쓰지 않는 이유는 그것이 방금 깔아 둔 다리를 지우기 때문이다.
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
              {/* 성경 본문 — 동의 게이트 *안쪽* 이다. Scene 1 은 조서(에 3:13). */}
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

            세 장은 **나란히** 있다. 순서도 단계도 아니고 더 나아간 카드가 없다.
            여기서 정렬·번호·강조를 얹지 않는다 — 순서는 payload 가 준 그대로이고,
            세 버튼의 스타일이 같은 것이 이 화면에서 R3 를 지키는 실제 방식이다.

            고른 값은 그대로 서버에 보낸다(`decision_key: disclose_label`). 쓰이는 자리는
            Scene 5 의 마감 문구 하나뿐이고, 어느 카드를 골라도 다음은 Scene 4 다.
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
                      setDiscloseLabel(o.id as DiscloseLabel);
                      // 저작이 카드마다 놓아 둔 한 마디. 다음 씬 상단에 한 번 얹는다.
                      setEcho(acknowledgements[o.id] ?? null);
                      decide.mutate({
                        sceneId: scene.currentScene,
                        decision: { value: o.id },
                      });
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
            Scene 4 — contemplative. 사흘. 머무는 시간에 정답이 없다(먼저 나가도 된다).
            타이머·진행 바·완료 뱃지를 두지 않는다 — 금식은 성취가 아니다.
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

          {/* Scene 5 — outro. 건너뛴 사람도, 카드를 안 고른 사람도 여기까지 온다. */}
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
                    completeMission(
                      "esther",
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
