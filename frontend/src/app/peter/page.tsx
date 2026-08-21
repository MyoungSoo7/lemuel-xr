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
 * Peter 미션 — 숯불 앞에서 다시 불린 이름.
 *
 * `Character.PETER` 는 2026-08-22 에 열렸다. 근거·범위·남은 빚은
 * `docs/PETER-RUNTIME-SIGNOFF.md` 에 있고, 전문 신학·정신건강 검토는 **없다.**
 *
 * peter.yml 5 Scene:
 *   1 cinematic(실패보다 먼저 온 기도) → 2 pick_one(불 곁에서 세 번, R4 게이트) →
 *   3 contemplative(돌이켜 보시니) → 4 grab_and_place(다시 던진 그물) →
 *   5 outro(숯불 앞의 아침) → complete → 홈.
 *
 * ─────────────── 이 화면이 payload 를 읽는 방식 ───────────────
 *
 * 1) 본문은 전부 `extras.captions` 정본이다. 프론트에 성경 자구 사본이 0개인 것은
 *    의도다 — `check_monologue_quotes.py` 가 프론트에 자구가 있으면 빨개진다.
 *    자막은 이미 `"<자구> (눅 22:31)"` 형태로 참조를 달고 오므로 여기서 참조를
 *    다시 만들지 않는다.
 *
 * 2) `trigger_warning` 은 Scene 2 에만 있고 Scene 3 은 `consent_coverage`
 *    (inherited)로 덮인다. 즉 카드는 **한 번만** 뜨는 게 맞다 — Scene 3 에서 또
 *    뜨면 그건 저작이 아니라 화면의 버그다.
 *
 * 3) `*_note` 필드(order_note·framing_note·scope_note·npc_note·asset_note·
 *    options_note·interpretation_note·timeline_note)는 **저작자용 가드 주석** 이지
 *    사용자 카피가 아니다. 다른 페이지와 같이 렌더하지 않는다.
 *
 * ─────────────── 안전선 ───────────────
 *  · R4 — Scene 2 진입 전 동의 카드 + 건너뛰기(→ Scene 4, yml 의
 *    `skip_alternative_scene_id` 와 동일). 건너뛰어도 이야기는 끝까지 이어진다.
 *  · Scene 2 의 세 카드는 **결정으로 보내지 않는다.** 어느 것을 골라도 서버로 가는
 *    값은 `{ value: "next" }` 다. 부인 카드를 결정으로 기록하면 Scene 5 의 라벨과
 *    상관지을 수 있게 되고, 저작(`options_note`)이 금지한 것이 바로 그것이다.
 *  · Scene 5 의 라벨은 **고르지 않아도 된다.** 무응답은 실패 경로가 아니라 정상
 *    경로이고, 그때는 `recovery_texts.default` 가 나간다.
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
  selectable?: boolean;
}

type RestoreLabel = "unforgivable" | "unworthy" | "afraid_again";

/**
 * users.faith_tone (V3__expand_identity_emotion.sql, DEFAULT 'balanced') 이 마감 문구
 * 12조합의 두 번째 축인데, 게스트 세션에는 이 값을 프론트로 내려주는 엔드포인트가 아직 없다.
 * DB 기본값과 같은 'balanced' 로 고정한다 — 임의 선택이 아니라 서버 기본값 미러링이다.
 * (solomon 페이지가 같은 이유로 같은 상수를 쓴다.)
 */
const DEFAULT_FAITH_TONE = "balanced" as const;

export default function PeterPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  // Scene 4 — 그물을 던졌는가. 던지지 않아도 넘어간다(yml `on_timeout: treat_as_complete`).
  const [netCast, setNetCast] = useState(false);
  /**
   * 건너뛴 구간을 잇는, 저작이 써 둔 한 줄(`skip_bridge_narration_ko`).
   *
   * Scene 2 의 카드는 Scene 2·3 을 함께 덮고 목적지가 4 다. 건너뛴 사람은 부인의 밤도
   * 돌이켜 보시는 시선도 보지 않은 채 바다 장면에 도착한다 — 이 줄이 없으면 그 사람은
   * **무슨 일이 있었는지 모르는 채로** 「다시 던진 그물」을 본다. 건너뛰기가 이야기를
   * 잃는 일이 되지 않게 하려고 저작자가 놓아 둔 다리다.
   */
  const [echo, setEcho] = useState<string | null>(null);
  // Scene 5 — 스스로에게 붙인 이름. null 은 "아직 안 골랐다" 이자 정상 경로다.
  const [restoreLabel, setRestoreLabel] = useState<RestoreLabel | null>(null);

  const start = useMutation({
    mutationFn: () => startMission("peter", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("peter", scene!.sessionId, sceneId, decision),
    onSuccess: (d) => {
      setConsented(false);
      setNetCast(false);
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

  // Scene 5 마감 — restore_label × faith_tone 9 조합 + 라벨 없음 default (전부 payload 정본).
  const recoveryText = useMemo(() => {
    if (scene?.currentScene !== 5) return null;
    const texts = extras.recovery_texts as
      Record<string, Record<string, string> | string> | undefined;
    if (!texts) return null;
    if (restoreLabel) {
      const byTone = texts[restoreLabel];
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
  }, [scene?.currentScene, extras, restoreLabel]);

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
  const needsConsent = !!warning && !consented;
  /*
    두 문의 이름은 저작 정본(`consent_card_ko` 의 「[이어서 보기]  [건너뛰기]」 줄)이
    소유한다. 여기 베껴 적으면 정본이 개정돼도 화면은 옛 이름을 계속 쓰고, 그 어긋남을
    재는 검사기가 없다. 못 읽으면 게이트 기본 라벨로 돌아간다 — 문은 남는다.
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
  const gesture = field<Record<string, unknown>>("gesture");

  // 다리 한 줄은 *건너뛴 다음 씬 한 번* 만 보여 준다. 그 뒤로도 남아 있으면
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
          Peter — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
        {anchor && (
          <p className="text-xs text-[var(--color-warm)]/50 mt-1">{anchor}</p>
        )}
      </header>

      {/*
        위기 안내. 저작이 이 씬에 `crisis_reminder` 를 넣은 곳(3·5)에서만 뜬다 —
        문구는 payload 정본이고, 연락처는 서버가 토큰을 치환해 실어 보낸다.
        프론트가 번호를 아는 일이 없어야 `ScenarioHotlineRatchetTest` 의 전제가 산다.
      */}
      {echo && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 italic text-sm text-[var(--color-warm)]/90">
          <p className="whitespace-pre-line">{echo}</p>
        </section>
      )}

      {/*
        씬이 직접 둔 위기 안내(Scene 3·5). 서버 `CrisisTokenResolver` 가 번호를 치환해
        보낸 문자열이고, 이 렌더가 그 번호가 화면까지 도달하는 마지막 한 칸이다.
      */}
      <CrisisReminder text={crisisReminder} />

      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative bg-cover bg-center bg-stone-900"
        style={{
          backgroundImage: `url(/images/scenes/peter/${scene.currentScene}.webp)`,
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-stone-900/85 via-stone-900/75 to-amber-950/85" />
        <div className="relative z-10 p-5">
          {needsConsent ? (
            <TriggerWarningGate
              warning={warning!}
              fallbackProse={
                <p>
                  다음 장면에는 <strong>예수를 안다고 하지 않은 밤</strong> 과,
                  그 뒤에 남은 마음이 나옵니다. 지금이 버겁다면{" "}
                  <strong>건너뛰어도 괜찮습니다</strong> — 건너뛰어도 이야기는
                  끝까지 이어집니다.
                </p>
              }
              continueLabel={doors?.continueLabel}
              skipLabel={doors?.skipLabel}
              pending={decide.isPending}
              onContinue={() => setConsented(true)}
              /*
                이 카드에는 `declined_route` 가 없다 — 둘째 문은 언제나 건너뛰기이고
                이야기는 Scene 4 로 이어진다(룻 Scene 3 의 「여기서 마친다」와 다른 문이다).
                `advance` 를 쓰지 않는 이유는 그것이 다리 한 줄을 지우기 때문이다.
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
              {/* 성경 본문 — 동의 게이트 *안쪽* 이다. Scene 2 는 세 번의 부인(눅 22:54-60). */}
              <ScenePassage
                reference={scriptureRef}
                additional={additionalRefs}
              />
              {/* Scene 3 — 머무는 시간에 정답이 없다는 저작 문구는 사용자에게 보여준다. */}
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
          {/* Scene 1 — cinematic */}
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
            Scene 2 — pick_one. 세 카드 전부 본문이고 틀린 답이 없다.
            세 번째(`selectable: false`)는 고를 수 있는 것처럼 두지 않는다 — 그렇게 두면
            「고르지 않을 수도 있었다」가 되고, 그건 본문에도 없고 이 미션의 축에도 반대다.
            고른 값은 서버로 보내지 않는다(위 안전선 참조).
          */}
          {sceneType === "pick_one" && (
            <div className="space-y-3">
              <div className="grid grid-cols-1 gap-3">
                {options
                  .filter((o) => o.selectable !== false)
                  .map((o) => (
                    <button
                      key={o.id}
                      onClick={() =>
                        advance(scene.currentScene, { value: "next" })
                      }
                      disabled={decide.isPending}
                      className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] text-left transition disabled:opacity-40"
                    >
                      <span className="font-semibold">{o.label_ko}</span>
                    </button>
                  ))}
              </div>
              {options
                .filter((o) => o.selectable === false)
                .map((o) => (
                  <p
                    key={o.id}
                    className="px-4 py-3 rounded-lg border border-dashed border-[var(--color-warm)]/20 text-sm text-[var(--color-warm)]/55"
                  >
                    {o.label_ko}
                  </p>
                ))}
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

          {/* Scene 4 — grab_and_place. 던지지 않아도 넘어간다(treat_as_complete). */}
          {sceneType === "grab_and_place" && (
            <div className="space-y-3">
              <button
                onClick={() => setNetCast(true)}
                disabled={netCast}
                className={`w-full px-4 py-4 rounded-lg border text-left transition ${
                  netCast
                    ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10"
                    : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
                }`}
              >
                <span className="font-semibold">
                  {netCast ? "✓ " : ""}그물을 오른편에 던진다
                </span>
                {typeof gesture?.throw_side === "string" && (
                  <span className="block text-xs text-[var(--color-warm)]/60 mt-1">
                    던지지 않아도 이야기는 이어집니다.
                  </span>
                )}
              </button>
              <button
                onClick={() => advance(scene.currentScene, { value: "next" })}
                disabled={decide.isPending}
                className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
              >
                {decide.isPending ? "..." : "계속 →"}
              </button>
            </div>
          )}

          {/* Scene 5 — outro */}
          {sceneType === "outro" && (
            <div className="space-y-5">
              {options.length > 0 && (
                <div className="space-y-3">
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    {options.map((o) => {
                      const picked = restoreLabel === o.id;
                      return (
                        <button
                          key={o.id}
                          onClick={() =>
                            setRestoreLabel(
                              picked ? null : (o.id as RestoreLabel),
                            )
                          }
                          className={`px-4 py-4 rounded-lg border text-left transition ${
                            picked
                              ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10"
                              : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
                          }`}
                        >
                          <span className="font-semibold">
                            {picked ? "✓ " : ""}
                            {o.label_ko}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                  {typeof field<string>("options_note") === "string" && (
                    <p className="text-xs text-[var(--color-warm)]/50">
                      고르지 않아도 됩니다.
                    </p>
                  )}
                </div>
              )}

              {recoveryText && (
                <p className="text-base leading-relaxed text-[var(--color-warm)]/90 border-l-2 border-[var(--color-primary)]/40 pl-3">
                  {recoveryText}
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
                    completeMission("peter", scene.sessionId, "completed").then(
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
