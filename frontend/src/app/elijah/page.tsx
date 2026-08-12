"use client";

import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { CRISIS_DEFAULT, telHref } from "@/lib/crisis-resources";
import { NarrationAudioButton } from "@/components/NarrationAudioButton";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";
import { SceneBootState } from "@/components/SceneBootState";

/**
 * Elijah 미션 — 로뎀나무 아래, 먼저 먹고 자도 됩니다.
 *
 * Character.ELIJAH 는 enum 주석상 *Stage 1 MVP* (JOB 과 함께 최우선 인물)인데,
 * 정작 프론트 라우트가 없어 들어갈 문이 없었다. 이 페이지가 그 문이다.
 *
 * elijah.yml 5 Scene:
 *   1 cinematic(갈멜산 승리 후 도망) → 2 scripture_reading(로뎀나무·죽기를 간청, R4 게이트) →
 *   3 gesture_sequence(천사 — 먼저 음식과 잠) → 4 scripture_reading(호렙산 세미한 음성) →
 *   5 outro(너 혼자가 아니다) → complete → 홈.
 *
 * ─────────────── 라이브 백엔드 probe 로 확인한 계약 (solomon 과 다른 점) ───────────────
 *
 * 1) `responseText` 는 항상 null (elijah.yml 에 monologues/outcomes/reactions map 없음).
 *    그래서 본문은 payload 의 `extras.static_text` 정본을 그대로 렌더한다 — 프론트 사본 0개.
 *
 * 2) `trigger_warning` 에 `consent_card_ko` 가 **없다**. 그 필드를 가진 건 solomon.yml 뿐이고
 *    나머지 인물은 `consent_card_id` 만 내려온다(서버 어디에도 해당 id 의 본문이 없다).
 *    그래서 동의 카드 문구는 david/jesus 페이지와 같이 *프론트가 소유* 한다. solomon 처럼
 *    payload 를 렌더하려 하면 빈 카드가 뜬다.
 *
 * 3) Scene 2 는 `extras.crisis_check: true` 를 준다. 이 씬은 죽음 갈구를 다루므로 본문보다
 *    위기 연결이 우선이라는 저작 의도의 *기계 신호* 다. 동의 카드와 본문 양쪽에 위기 자원을
 *    눈에 띄게 올린다. (전역 CrisisFooter 는 layout 에 이미 상시 노출되지만, 이 씬은
 *    "아래 어딘가" 로 미루지 않는다.)
 *
 * 4) `*_note` 필드(language_note·framing_note·timeline_note)는 **저작자용 가드 주석** 이지
 *    사용자 카피가 아니다. 기존 페이지도 전부 렌더하지 않는다 — 여기서도 렌더하지 않는다.
 *
 * ─────────────── 안전선 ───────────────
 *  · R4 — Scene 2(죽음 갈구) 진입 전 동의 카드 + 건너뛰기(→ Scene 3, yml 의
 *    skip_alternative_scene_id 와 동일). 건너뛰어도 서사는 그대로 이어진다.
 *  · R3 — 회복에 시간표를 강요하지 않는다. Scene 3 의 두 동작(음식·잠)은 *권유* 이며
 *    하나도 누르지 않아도 다음으로 넘어간다.
 *  · R2 — 죽음 갈구를 책망하지 않되 낭만화하지도 않는다. yml 정본 문구만 쓴다.
 */
type Scene = JosephStartResponse;

interface TriggerWarning {
  level?: string;
  content?: string[];
  skip_alternative_scene_id?: number;
}

interface GestureStep {
  id: string;
  label: string;
  note?: string;
}

export default function ElijahPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  // Scene 3 gesture_sequence — 누른 동작(비강제, 완주 조건 아님).
  const [doneSteps, setDoneSteps] = useState<Set<string>>(new Set());

  const start = useMutation({
    mutationFn: () => startMission("elijah", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("elijah", scene!.sessionId, sceneId, decision),
    onSuccess: (d) => {
      setConsented(false);
      setDoneSteps(new Set());
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

  const payload = (scene?.scenePayload ?? {}) as Record<string, unknown>;
  const extras = (payload.extras as Record<string, unknown> | undefined) ?? {};
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

  const warning = payload.trigger_warning as TriggerWarning | undefined;
  const needsConsent = !!warning && !consented;

  const anchor = field<string>("anchor");
  const staticText = field<string>("static_text");
  const reflectionPrompt = field<string>("reflection_prompt");
  const crisisCheck = field<boolean>("crisis_check") === true;
  const crisisReminder = field<string>("crisis_reminder");

  const advance = (sceneId: number, decision: unknown) =>
    decide.mutate({ sceneId, decision });

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6 pb-16">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Elijah — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
        {anchor && (
          <p className="text-xs text-[var(--color-warm)]/50 mt-1">{anchor}</p>
        )}
      </header>

      {/* R1/R2 — crisis_check 씬은 위기 연결을 본문보다 위에 둔다 (yml 저작 의도) */}
      {(crisisCheck || crisisReminder) && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-amber-500/40 bg-amber-500/10 text-sm text-[var(--color-warm)]/90">
          {crisisReminder ? (
            <p>{crisisReminder}</p>
          ) : (
            /*
              번호를 문장 안의 인라인 링크로 두지 않는다.

              이 블록은 앱이 「이 사용자가 위험할 수 있다」고 판단한 씬에서만 뜬다.
              그런데 320px 에서 재 보면 그 전화 링크가 **26×17px** 였다 — 앱을 통틀어
              제일 작은 타깃이 하필 제일 급한 동작이었다(2026-08-12 실측).
              CrisisFooter 에서 고친 23×15 와 같은 결함이 씬 안에 또 있었던 것이다.
              문장은 그대로 두되 번호는 줄에서 떼어내 44px 짜리 블록으로 만든다
              (WCAG 2.5.5 / Apple HIG).
            */
            <>
              <p>지금 실제로 그 마음이 크다면, 본문보다 연결이 먼저입니다.</p>
              <a
                href={telHref(CRISIS_DEFAULT)}
                className="mt-2 min-h-11 px-4 flex items-center justify-center gap-2 rounded-md border border-amber-500/50 bg-amber-500/10 font-bold hover:bg-amber-500/20 transition"
              >
                <span className="underline">{CRISIS_DEFAULT.tel}</span>
                <span className="font-normal">
                  {CRISIS_DEFAULT.label} (24시간, 무료)
                </span>
              </a>
            </>
          )}
        </section>
      )}

      <section className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 bg-gradient-to-b from-stone-900 via-stone-900 to-emerald-950/30 p-5">
        {needsConsent ? (
          <ConsentGate
            warning={warning!}
            pending={decide.isPending}
            onContinue={() => setConsented(true)}
            onSkip={() => advance(scene.currentScene, { value: "skip" })}
          />
        ) : (
          <div className="space-y-4">
            {staticText && (
              <>
                <p className="text-base leading-relaxed whitespace-pre-line text-[var(--color-warm)]/90">
                  {staticText}
                </p>
                <div className="flex justify-start">
                  <NarrationAudioButton
                    text={staticText}
                    onUnavailable="hide"
                  />
                </div>
              </>
            )}
            {reflectionPrompt && (
              <p className="text-sm italic text-[var(--color-warm)]/70 border-l-2 border-[var(--color-primary)]/40 pl-3 whitespace-pre-line">
                {reflectionPrompt}
              </p>
            )}
            {/* Scene 4 — 세미한 소리의 예시들 */}
            {(field<string[]>("examples") ?? []).length > 0 && (
              <ul className="space-y-1 text-sm text-[var(--color-warm)]/75 pl-4 list-disc">
                {field<string[]>("examples")!.map((ex) => (
                  <li key={ex}>{ex}</li>
                ))}
              </ul>
            )}
            <p className="text-[10px] text-[var(--color-warm)]/40 text-right">
              * AI 보조 — 본문은 성경 참조 *
            </p>
          </div>
        )}
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

          {/* Scene 2·4 — scripture_reading */}
          {sceneType === "scripture_reading" && (
            <button
              onClick={() => advance(scene.currentScene, { value: "next" })}
              disabled={decide.isPending}
              className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
            >
              {decide.isPending ? "..." : "계속 →"}
            </button>
          )}

          {/* Scene 3 — gesture_sequence. 회복은 몸부터. 누르지 않아도 넘어간다 (R3). */}
          {sceneType === "gesture_sequence" && (
            <div className="space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {(field<GestureStep[]>("steps") ?? []).map((s) => {
                  const done = doneSteps.has(s.id);
                  return (
                    <button
                      key={s.id}
                      onClick={() =>
                        setDoneSteps((prev) => new Set(prev).add(s.id))
                      }
                      className={`px-4 py-4 rounded-lg border text-left transition ${
                        done
                          ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10"
                          : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
                      }`}
                    >
                      <span className="font-semibold">
                        {done ? "✓ " : ""}
                        {s.label}
                      </span>
                      {s.note && (
                        <span className="block text-xs text-[var(--color-warm)]/60 mt-1">
                          {s.note}
                        </span>
                      )}
                    </button>
                  );
                })}
              </div>

              {(field<string[]>("practical_reminders") ?? []).length > 0 && (
                <ul className="space-y-1 text-xs text-[var(--color-warm)]/70 px-4 py-3 rounded-lg border border-[var(--color-primary)]/20 list-disc pl-8">
                  {field<string[]>("practical_reminders")!.map((r) => (
                    <li key={r}>{r}</li>
                  ))}
                </ul>
              )}

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
                      "elijah",
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

/**
 * R4 동의 게이트.
 *
 * solomon 과 달리 elijah.yml 의 trigger_warning 은 `consent_card_ko` 를 주지 않으므로
 * (consent_card_id 만 있고 서버에 본문이 없다) 문구를 프론트가 소유한다 — david/jesus 와 동일.
 * 건너뛰기는 yml 의 skip_alternative_scene_id(=3) 로 가는데, 이 씬의 next 도 3 이라 결과적으로
 * 같은 자리다. 즉 *건너뛰기는 서사를 잃지 않고 죽음 갈구 묘사만 건너뛴다*.
 */
function ConsentGate({
  warning,
  pending,
  onContinue,
  onSkip,
}: {
  warning: TriggerWarning;
  pending: boolean;
  onContinue: () => void;
  onSkip: () => void;
}) {
  return (
    <div className="space-y-4">
      <p className="text-sm text-[var(--color-warm)]/90 leading-relaxed">
        다음 장면은 <strong>죽고 싶다는 마음(죽음 갈구)</strong> 을 다룹니다.
        엘리야가 그렇게 기도했고, 신은 그 기도를{" "}
        <strong>책망하지 않으셨습니다</strong>. 직접적인 묘사는 없지만 지금이
        버겁다면 <strong>건너뛰어도 괜찮습니다</strong> — 건너뛰어도 이야기는
        온전히 이어집니다.
      </p>
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
      {warning.level && (
        <p className="text-[10px] text-[var(--color-warm)]/40">
          정서 강도: {warning.level}
        </p>
      )}
    </div>
  );
}
