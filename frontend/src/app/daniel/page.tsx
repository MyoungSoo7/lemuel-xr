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
 * Daniel 미션 — 어디까지 맞추고, 어디서 멈추는가.
 *
 * `Character.DANIEL` 은 2026-08-22 에 열렸다. 근거·범위·남은 빚은
 * `docs/DANIEL-RUNTIME-SIGNOFF.md` 에 있고, 전문 신학·정신건강 검토는 **없다.**
 * 저작(`content/daniel/README.md`)이 건 인간 안전검토자 사인오프도 없다 —
 * 해제한 게 아니라 미해소 부채로 대장에 적힌 채 열렸다.
 *
 * daniel.yml 5 Scene:
 *   1 cinematic(이름이 바뀌던 자리) → 2 pick_one(뜻을 정하다) →
 *   3 contemplative(열흘) → 4 cinematic(금령, R4 게이트 ①) →
 *   5 outro(창문, 그리고 아침 — R4 게이트 ②) → complete → 홈.
 *
 * ─────────────── 이 화면이 payload 를 읽는 방식 ───────────────
 *
 * 1) 본문은 전부 `extras.captions` 정본이다. 프론트에 성경 자구 사본이 0개인 것은
 *    의도다 — `check_monologue_quotes.py` 가 프론트에 자구가 있으면 빨개진다.
 *    자막은 이미 `"<자구> (단 6:10)"` 형태로 참조를 달고 오므로 여기서 참조를
 *    다시 만들지 않는다.
 *
 * 2) `*_note` 필드(options_note·framing_note·scope_note·npc_note·observe_note·
 *    card_note·angel_note·closing_texts_note·dwell_limit_note·name_change_note)는
 *    **저작자용 가드 주석** 이지 사용자 카피가 아니다. 렌더하지 않는다.
 *
 * ─────────────── 안전선 — 이 인물이 앞의 아홉과 다른 지점 ───────────────
 *
 * R4 동의 카드가 **둘이고 서로를 상속하지 않는다.** 앞선 인물들은 한 카드가 여러 씬을
 * 덮었지만(peter Scene 2 카드가 2·3 을 덮는다), 다니엘은 Scene 4 카드가 `covers_scenes: [4]`
 * 로 자기 하나만 덮는다. 그래서 Scene 5 에서 **카드가 또 뜨는 것이 맞다** — 여기서는
 * 두 번 뜨는 게 버그가 아니라 저작이 명시로 요구한 것이다(`card_note`: 「고지에 동의한
 * 것이 실행에 동의한 것은 아니다」). 카드를 한 번으로 합치는 「개선」을 하지 말 것.
 *
 * 건너뛰기 목적지도 두 모양이다 —
 *  · Scene 4: 정수 `5`. 서버가 다음 Scene 으로 점프시킨다. 건너뛴 사람은 금령 자막을
 *    보지 않은 채 Scene 5 에 도착하므로, 저작이 놓아 둔 `skip_bridge_narration_ko`
 *    넉 줄을 다리로 깔아 준다(아래 `echo`).
 *  · Scene 5: 문자열 `daniel_scene5_alt_quiet_window`. 마지막 Scene 이라 갈 다음이
 *    없어서, 서버가 **같은 Scene 을 축약 블록으로 다시 조립해** 돌려준다
 *    (`DecideSceneUseCase.altBlockPayload`). 자막 아홉이 넷으로 줄고 sceneId 는 그대로다.
 *
 * ⚠️ 그 축약 payload 에는 `trigger_warning` 이 **그대로 살아 있다.** 동의 여부만 보고
 *    게이트를 세우면 건너뛴 사람에게 같은 카드가 영원히 다시 뜬다(sceneId 가 안 바뀌므로
 *    씬 전환 초기화도 그를 구해 주지 않는다). 그래서 게이트 조건에 `conditionalBlockId`
 *    부재를 함께 건다 — 서버가 그 키를 박아 주는 것이 「이미 건너뛴 사람」의 유일한 표시다.
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

/** Scene 2 의 세 카드. 마감 문구 표의 첫 축이며, 고르지 않으면 `default` 로 간다. */
type RequestStyle = "direct_refusal" | "verifiable_proposal" | "seek_ally";

/**
 * users.faith_tone (V3__expand_identity_emotion.sql, DEFAULT 'balanced') 이 마감 문구
 * 12조합의 두 번째 축인데, 게스트 세션에는 이 값을 프론트로 내려주는 엔드포인트가 아직 없다.
 * DB 기본값과 같은 'balanced' 로 고정한다 — 임의 선택이 아니라 서버 기본값 미러링이다.
 * (peter·solomon 페이지가 같은 이유로 같은 상수를 쓴다.)
 */
const DEFAULT_FAITH_TONE = "balanced" as const;

export default function DanielPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  /**
   * 건너뛴 구간을 잇는, 저작이 써 둔 넉 줄(`skip_bridge_narration_ko`).
   *
   * Scene 4 를 건너뛴 사람은 금령이 무엇인지 모르는 채 Scene 5 의 창문 앞에 선다.
   * 그러면 「도장이 찍힌 것을 알고도」가 무엇을 알고도인지 알 수 없는 문장이 된다.
   * 건너뛰기가 이야기를 잃는 일이 되지 않게 하려고 놓아 둔 다리다.
   */
  const [echo, setEcho] = useState<string | null>(null);
  // Scene 2 에서 고른 요청 방식. null 은 "아직/끝내 안 골랐다" 이자 정상 경로다.
  const [requestStyle, setRequestStyle] = useState<RequestStyle | null>(null);

  const start = useMutation({
    mutationFn: () => startMission("daniel", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("daniel", scene!.sessionId, sceneId, decision),
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

  // Scene 5 마감 — request_style × faith_tone 9 조합 + 라벨 없음 default (전부 payload 정본).
  const closingText = useMemo(() => {
    if (scene?.currentScene !== 5) return null;
    const texts = extras.closing_texts as
      Record<string, Record<string, string> | string> | undefined;
    if (!texts) return null;
    if (requestStyle) {
      const byTone = texts[requestStyle];
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
  }, [scene?.currentScene, extras, requestStyle]);

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
    (`DecideSceneUseCase.altBlockPayload`). 그 키가 있으면 이 사람은 **이미 건너뛴
    사람** 이고, 같은 씬에 남아 있는 `trigger_warning` 은 지나간 카드의 흔적이다.
    이 조건이 없으면 Scene 5 건너뛰기가 무한 루프가 된다.
  */
  const alreadySkipped = typeof payload.conditionalBlockId === "string";
  const needsConsent = !!warning && !consented && !alreadySkipped;
  /*
    두 문의 이름은 저작 정본(`consent_card_ko` 의 마지막 줄)이 소유한다. 여기 베껴 적으면
    정본이 개정돼도 화면은 옛 이름을 계속 쓰고, 그 어긋남을 재는 검사기가 없다.
    두 카드의 문 이름이 서로 다르다는 점도 이유다 — Scene 4 는 「본문 그대로 보기 /
    요약만 듣고 넘어가기」, Scene 5 는 「그대로 보기 / 조용한 장면으로 넘어가기」.
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

  // 다리 넉 줄은 *건너뛴 다음 씬 한 번* 만 보여 준다. 그 뒤로도 남아 있으면
  // 이야기 위에 겹쳐 읽히는 다른 본문이 된다.
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
          Daniel — Scene {scene.currentScene}/5 · Mode: VR
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
        씬이 직접 둔 위기 안내(Scene 4·5). 서버 `CrisisTokenResolver` 가 번호를 치환해
        보낸 문자열이고, 이 렌더가 그 번호가 화면까지 도달하는 마지막 한 칸이다.
        프론트가 번호를 아는 일이 없어야 `ScenarioHotlineRatchetTest` 의 전제가 산다.
      */}
      <CrisisReminder text={crisisReminder} />

      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative bg-cover bg-center bg-stone-900"
        style={{
          backgroundImage: `url(/images/scenes/daniel/${scene.currentScene}.webp)`,
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-stone-900/85 via-stone-900/75 to-amber-950/85" />
        <div className="relative z-10 p-5">
          {needsConsent ? (
            <TriggerWarningGate
              warning={warning!}
              fallbackProse={
                scene.currentScene === 4 ? (
                  <p>
                    다음 장면에는{" "}
                    <strong>신앙을 이유로 한 처벌을 정한 금령</strong> 이 본문
                    그대로 나옵니다. 지금이 버겁다면{" "}
                    <strong>요약만 듣고 넘어가도 괜찮습니다</strong> —
                    건너뛰어도 이야기는 끝까지 이어집니다.
                  </p>
                ) : (
                  <p>
                    다음 장면에는 <strong>좁고 어두운 공간</strong> 과 맹수
                    소리가 나옵니다. 폐쇄된 공간이 부담되시면{" "}
                    <strong>건너뛰셔도 괜찮습니다</strong> — 건너뛰셔도 마무리는
                    그대로 받으십니다.
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
                Scene 4 는 Scene 5 로 점프하고, Scene 5 는 같은 씬의 축약본이 된다.
                `advance` 를 쓰지 않는 이유는 그것이 다리 넉 줄을 지우기 때문이다.
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
              {/* 성경 본문 — 동의 게이트 *안쪽* 이다. Scene 4 는 금령(단 6:7). */}
              <ScenePassage
                reference={scriptureRef}
                additional={additionalRefs}
              />
              {/* Scene 3·5 — 머무는 시간에 정답이 없다는 저작 문구는 사용자에게 보여준다. */}
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
          {/* Scene 1·4 — cinematic. 겪는 자리이고 조작이 없다. */}
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
            Scene 2 — pick_one. 이 미션의 유일한 사용자 선택이다.
            세 카드 전부 본문에 있는 반응이고 틀린 답이 없다(`options_note`).
            베드로와 달리 **고른 값을 그대로 서버에 보낸다** — 저작이
            `decision_key: request_style` 로 이 값을 쓰겠다고 선언했고,
            쓰이는 자리는 Scene 5 의 마감 문구 하나뿐이다. 점수도 순위도 없고,
            어느 카드를 골라도 다음은 Scene 3 이다.
          */}
          {sceneType === "pick_one" && (
            <div className="space-y-3">
              <div className="grid grid-cols-1 gap-3">
                {options.map((o) => (
                  <button
                    key={o.id}
                    onClick={() => {
                      setRequestStyle(o.id as RequestStyle);
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
              {/* 고르지 않고 지나가는 것도 정본이 정한 정상 경로다. */}
              <button
                onClick={() => advance(scene.currentScene, { value: "next" })}
                disabled={decide.isPending}
                className="w-full py-3 rounded-lg border border-[var(--color-warm)]/25 text-sm text-[var(--color-warm)]/70 hover:border-[var(--color-warm)]/50 disabled:opacity-40"
              >
                고르지 않고 넘어가기
              </button>
            </div>
          )}

          {/* Scene 3 — contemplative. 머무는 시간에 정답이 없다(먼저 나가도 된다). */}
          {sceneType === "contemplative" && (
            <button
              onClick={() => advance(scene.currentScene, { value: "next" })}
              disabled={decide.isPending}
              className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
            >
              {decide.isPending ? "..." : "계속 →"}
            </button>
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
                    completeMission(
                      "daniel",
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
