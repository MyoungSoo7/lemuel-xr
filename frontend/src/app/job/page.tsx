"use client";

import { useEffect, useState } from "react";
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
import { ScenePassage } from "@/components/ScenePassage";
import { CrisisReminder } from "@/components/CrisisReminder";
import {
  TriggerWarningGate,
  readTriggerWarning,
} from "@/components/TriggerWarningGate";

/**
 * Job 미션 — 너의 비탄은 부끄러운 것이 아니다.
 *
 * Character.JOB 은 enum 주석상 *Stage 1 MVP* (엘리야와 함께 최우선 인물)인데 프론트
 * 라우트가 없어 들어갈 문이 없었다. 이 페이지가 그 문이다.
 *
 * job.yml 5 Scene:
 *   1 cinematic(침묵의 7일) → 2 scripture_reading(태어난 날을 저주, R4 게이트) →
 *   3 pick_one(친구들의 위로 — 정답이 아닌 것) → 4 scripture_reading(폭풍 가운데 응답) →
 *   5 outro(회복 — 그러나 답 없이) → complete → 홈.
 *
 * ─────────────── 라이브 백엔드 probe 로 확인한 계약 ───────────────
 *
 * 1) `responseText` 는 항상 null (job.yml 에 monologues/outcomes/reactions map 없음).
 *    대신 Scene 3 의 각 option 이 `response` 문자열을 직접 들고 내려온다. 그래서 선택은
 *    *즉시 씬을 넘기지 않고* 그 response 를 먼저 보여준 뒤 계속 버튼으로 넘어간다 —
 *    바로 advance 하면 저작된 응답 문구가 화면에 한 번도 뜨지 않고 사라진다.
 *
 * 2) `trigger_warning` 에 `consent_card_ko` 가 없다(consent_card_id 만). solomon.yml 만
 *    본문을 갖고 있으므로 동의 카드 문구는 프론트가 소유한다 — david/jesus/elijah 와 동일.
 *
 * 3) `*_note` 필드(theology_note·language_note·restoration_note)는 **저작자용 가드 주석**
 *    이라 렌더하지 않는다. 반면 outro 의 `suffering_footer` 는 사용자 대상 안전 고지라
 *    반드시 렌더한다 — 이 둘을 뭉뚱그리면 안 된다.
 *
 * ─────────────── 안전선 ───────────────
 *  · R4 — Scene 2(죽음 갈구·출생 비탄) 진입 전 동의 카드 + 건너뛰기(→ Scene 3).
 *  · R3 — 결말은 의도적으로 *답 없는 만남* 에서 멈춘다. 욥 42:10 의 '갑절 회복' 을
 *    결말로 쓰지 않는다(회복 보장 서사 금지). 프론트도 완주 문구에 회복을 약속하지 않는다.
 *  · R2 — 고난의 원인을 당사자의 죄로 돌리는 프레임 금지(욥 42:7). 어떤 선택지도
 *    '네 탓' 으로 되돌아오지 않으며, 세 선택 모두 동등하게 유효하다.
 *  · outro 의 suffering_footer — 학대·폭력 상황을 견디라는 뜻이 아님을 명시.
 */
type Scene = JosephStartResponse;

/*
  경고 타입도 공용 정의를 쓴다. 여기 있던 로컬 interface 는 `consent_card_ko` 를
  아예 몰라서, 안전 검토자가 job.yml 에 정본 동의 문구를 넣어도 이 화면은 그 필드를
  타입에서부터 못 보고 프론트 문구를 계속 썼다 — 고친 사람은 고쳐진 줄 안다.
*/

interface JobOption {
  id: string;
  label: string;
  /** 선택 시 보여줄 저작 정본 응답. responseText 가 null 이라 이 값이 유일한 echo 원천. */
  response?: string;
}

export default function JobPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  // R4 — trigger_warning 이 있는 씬은 동의 전 본문을 렌더하지 않는다. 씬 전환마다 초기화.
  const [consented, setConsented] = useState(false);
  // Scene 3 — 고른 선택지의 response 를 보여주는 중간 상태 (아직 씬을 넘기지 않음).
  const [picked, setPicked] = useState<JobOption | null>(null);

  const start = useMutation({
    mutationFn: () => startMission("job", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("job", scene!.sessionId, sceneId, decision),
    onSuccess: (d) => {
      setConsented(false);
      setPicked(null);
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

  const warning = readTriggerWarning(payload);
  const needsConsent = !!warning && !consented;

  const anchor = field<string>("anchor");
  // 씬마다 선언된 성경 참조. extras 가 아니라 payload 최상위다
  // (ScenePayloadAssembler.build — `sc.scriptureRef?.let { p["scriptureRef"] = it }`).
  const scriptureRef = payload.scriptureRef as string | undefined;
  // 절 단위 관례상 편 전체를 쓰는 씬은 나머지 절을 `additional_refs` 로 편다
  // (extras 안에 산다 — 그래서 payload 직독이 아니라 field 로 읽는다).
  const additionalRefs = field<string[]>("additional_refs");
  const staticText = field<string>("static_text");
  const reflectionPrompt = field<string>("reflection_prompt");
  const crisisReminder = field<string>("crisis_reminder");
  const sufferingFooter = field<string>("suffering_footer");

  const advance = (sceneId: number, decision: unknown) =>
    decide.mutate({ sceneId, decision });

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6 pb-16">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Job — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
        {anchor && (
          <p className="text-xs text-[var(--color-warm)]/50 mt-1">{anchor}</p>
        )}
      </header>

      {/*
        원래 여기 있던 블록을 그대로 옮긴 것이다. 손으로 베낀 사본이 화면마다
        갈라진 것이 이번 결함의 원인이라(7개 시나리오가 선언, 3개 화면만 렌더)
        렌더를 한 벌로 모았다.
      */}
      <CrisisReminder text={crisisReminder} />

      {/* Scene 배경 이미지 */}
      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative bg-cover bg-center bg-stone-950"
        style={{
          backgroundImage: `url(/images/scenes/job/${scene.currentScene}.webp)`,
        }}
      >
        {/* 요셉 쪽과 달리 본문이 이미지 위에 직접 얹히므로 오버레이를 훨씬 진하게 깐다 */}
        <div className="absolute inset-0 bg-gradient-to-b from-stone-950/85 via-stone-950/75 to-slate-900/85" />
        <div className="relative z-10 p-5">
          {needsConsent ? (
            <TriggerWarningGate
              warning={warning!}
              fallbackProse={
                <p>
                  다음 장면은 <strong>태어난 날을 저주하는 비탄</strong> 을
                  다룹니다. 욥이 실제로 그렇게 말했고, 신은 그 외침을{" "}
                  <strong>책망하지 않으셨습니다</strong>. 직접적인 묘사는 없지만
                  지금이 버겁다면 <strong>건너뛰어도 괜찮습니다</strong> —
                  건너뛰어도 이야기는 온전히 이어집니다.
                </p>
              }
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
              {/* 성경 본문 — 동의 게이트 *안쪽* 이다. Scene 2 는 태어난 날의 저주(욥 3). */}
              <ScenePassage
                reference={scriptureRef}
                additional={additionalRefs}
              />
              {reflectionPrompt && (
                <p className="text-sm italic text-[var(--color-warm)]/70 border-l-2 border-[var(--color-primary)]/40 pl-3 whitespace-pre-line">
                  {reflectionPrompt}
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

          {/* Scene 3 — pick_one. 세 선택 모두 동등하게 유효하다 (오답 없음, R2). */}
          {sceneType === "pick_one" && (
            <div className="space-y-3">
              {picked ? (
                <>
                  <div className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 space-y-2">
                    <p className="text-xs text-[var(--color-warm)]/50">
                      {picked.label}
                    </p>
                    {picked.response && (
                      <p className="text-sm italic text-[var(--color-warm)]/90 whitespace-pre-line">
                        {picked.response}
                      </p>
                    )}
                    <p className="text-[10px] not-italic text-[var(--color-warm)]/40 text-right">
                      * AI 보조 — 본문은 성경 참조 *
                    </p>
                  </div>
                  <button
                    onClick={() =>
                      advance(scene.currentScene, { value: picked.id })
                    }
                    disabled={decide.isPending}
                    className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
                  >
                    {decide.isPending ? "..." : "계속 →"}
                  </button>
                </>
              ) : (
                <div className="grid grid-cols-1 gap-3">
                  {(field<JobOption[]>("options") ?? []).map((o) => (
                    <button
                      key={o.id}
                      onClick={() => setPicked(o)}
                      disabled={decide.isPending}
                      className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition text-left disabled:opacity-50"
                    >
                      {o.label}
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Scene 5 — outro. R3 — '갑절 회복' 을 결말로 쓰지 않는다. */}
          {sceneType === "outro" && (
            <div className="space-y-4">
              {sufferingFooter && (
                <p className="px-4 py-3 rounded-lg border border-amber-500/30 bg-amber-500/5 text-xs text-[var(--color-warm)]/80 whitespace-pre-line">
                  {sufferingFooter}
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
                    completeMission("job", scene.sessionId, "completed").then(
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
