"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { NarrationAudioButton } from "@/components/NarrationAudioButton";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";
import {
  scene1Incarnation,
  scene2Beatitudes,
  scene3Touch,
  scene4Iam,
  scene4Teaching,
  scene5Passion,
  scene6Resurrection,
  scene7Ascension,
  scene7CrisisReminder,
  iamOf,
} from "@/lib/content/jesus-monologues";
import { SceneBootState } from "@/components/SceneBootState";
import { ScenePassage } from "@/components/ScenePassage";
import {
  TriggerWarningGate,
  readTriggerWarning,
} from "@/components/TriggerWarningGate";

/**
 * Jesus 미션 — 트랙 B 정점(capstone). Phase 2 완전 활성 (요셉·모세·다윗 동급).
 *
 * jesus.yml 7 Scene:
 *   1 cinematic(성육신) → 2 scripture_reading(팔복) → 3 gesture_sequence(만짐) →
 *   4 pick_one(길·진리·생명 3분기) → 5 contemplative(겟세마네·십자가, R4 게이트) →
 *   6 scripture_reading(부활 빈 무덤) → 7 outro(승천·생명의 강) → complete → 홈.
 *
 * echo(직전 결정/씬 모놀로그) 는 jesus-monologues.ts 의 frontend fallback 이 담당한다 —
 * backend 는 jesus.yml 에 monologues/outcomes/reactions map 이 없어 responseText=null.
 * shape 는 요셉/다윗과 동일하므로 9+15 캐시 시드 완료 후 backend round-trip 으로 교체 가능.
 *
 * ─────────────── 안전선 (예수는 정점이라 특히 엄격) ───────────────
 *  · R4 — Scene 5(겟세마네·십자가, 고통·죽음) 진입 전 정서 경고 동의 카드 + 건너뛰기.
 *    2026-08-12 까지 이 카드는 jesus.yml 의 trigger_warning 을 *읽지 않았다* — 조건이
 *    `sceneType === "contemplative"` 였고 레벨·트리거 종류·스킵 목적지가 이 파일에 박혀
 *    있었다. 그래서 안전 검토자가 yml 의 경고를 고쳐도 화면은 안 바뀌었고, 고친 사람은
 *    고쳐진 줄 알았다. 이제 `payload.trigger_warning` 으로 구동한다(공용
 *    TriggerWarningGate). 문구만 화면이 소유한다 — jesus.yml 에 consent_card_ko 가 없어서다.
 *    yml 에 정본이 생기면 게이트가 자동으로 그걸 우선 렌더한다.
 *  · R3 — Scene 6 부활을 "너도 부활/극복하라" 로 틀지 않는다. *이름이 불린다* 는 수동 은혜만.
 *    2026-08-11 백엔드 게이트 신설(jesus.yml safety_gates R3_no_resurrection_pressure, 55종).
 *  · R2 — Scene 5 겟세마네 흔들림 = 믿음의 결함 아님. 고난 미화 X.
 *    필수 footer 는 payload(extras.suffering_footer)에서 읽어 동의 게이트 위에 렌더한다.
 *  · R1 — Scene 7 outro 에 위기 라우팅 + 일기·트랙 A 안내.
 *    번호는 jesus-monologues.ts 가 @/lib/crisis-resources 에서 읽는다(2026-08-11).
 *    그전까지는 상수에 문자열로 박혀 있어 상담번호 정책이 바뀌면 이 화면만
 *    낡은 번호를 들고 남는 상태였다.
 *  · R5 — 모든 echo/outro 에 "AI 보조 — 본문은 성경 참조" footer.
 */
type Scene = JosephStartResponse;

interface DecisionEcho {
  fromScene: number;
  text: string;
}

interface OptionLike {
  id: string;
  label: string;
}

export default function JesusPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);
  const [echo, setEcho] = useState<DecisionEcho | null>(null);
  // R4 — trigger_warning 이 있는 씬은 동의 전 본문/상호작용을 렌더하지 않는다.
  // 어느 씬이 게이트 대상인지는 payload 가 정한다 (현재 jesus.yml 은 Scene 5). 씬 전환마다 초기화.
  const [passionConsented, setPassionConsented] = useState(false);

  const start = useMutation({
    mutationFn: () => startMission("jesus", "web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({
      sceneId,
      decision,
    }: {
      sceneId: number;
      decision: unknown;
    }) => decideMission("jesus", scene!.sessionId, sceneId, decision),
    onSuccess: (d, vars) => {
      // backend responseText 우선, 없으면 frontend fallback (jesus 는 항상 fallback).
      const local = buildLocalEcho(vars.sceneId, vars.decision);
      const text = d.responseText ?? local ?? null;
      if (text) setEcho({ fromScene: vars.sceneId, text });
      else setEcho(null);
      setPassionConsented(false); // R4 — 다음 씬 진입 시 동의 게이트 초기화
      setScene(d);
      setHistory((h) => [...h, JSON.stringify(d.scenePayload.title)]);
    },
  });

  useEffect(() => {
    if (!scene && !start.isPending && !start.isError) start.mutate();
  }, [scene, start]);

  // Scene 7 (outro) 진입 시 승천·생명의 강 텍스트
  const outroText = useMemo(() => {
    if (scene?.currentScene !== 7) return null;
    return scene7Ascension;
  }, [scene?.currentScene]);

  if (!scene) {
    return (
      <SceneBootState
        isError={start.isError}
        error={start.error}
        onRetry={() => start.mutate()}
      />
    );
  }

  const payload = scene.scenePayload as Record<string, unknown>;
  const title = (payload.title as string) ?? "Scene";
  const rawType = (payload.type as string) ?? "";
  const interaction = (payload.interaction as string) ?? "";
  const sceneType = rawType === "interaction" ? interaction : rawType;

  // jesus.yml 은 interaction 데이터(options/steps/lines)를 씬의 `extras:` 블록 안에
  // 중첩한다 (moses/david.yml 과 동일). loader 가 그 블록을 통째로 payload.extras 로 넘기므로,
  // extras 를 우선 읽고 top-level 을 fallback.
  const extras = (payload.extras as Record<string, unknown> | undefined) ?? {};
  const field = <T,>(key: string): T | undefined =>
    (extras[key] as T | undefined) ?? (payload[key] as T | undefined);

  const advance = (sceneId: number, decision: unknown) => {
    decide.mutate({ sceneId, decision });
  };

  const lines = field<Array<{ ref?: string; text?: string }>>("lines") ?? [];

  // R2 필수 footer. jesus.yml Scene 5 extras.suffering_footer 를 *payload 에서* 읽는다.
  // 2026-08-11 까지 이 페이지는 이 키를 한 번도 읽지 않았다 — yml 에는 2026-08-05 부터
  // 있었고 욥 페이지는 렌더하는데(job/page.tsx) 예수 페이지만 빠져 있었다. 즉 고난 미화
  // 방지 고지가 정작 *십자가 씬* 에서만 사용자에게 안 갔다. 프론트 상수(jesus-monologues.ts)
  // 에도 이 문구는 없어서 다른 경로로도 새어 나가지 않았다 — 전수 확인.
  // 프론트에 문구를 복사하지 않고 payload 에서 읽는 이유: 복사본은 yml 이 개정돼도 안 따라온다.
  const sufferingFooter = field<string>("suffering_footer");

  // R4 — 게이트 여부는 payload 가 정한다. 씬 타입을 조건으로 쓰지 않는다:
  // yml 이 다른 씬에 경고를 붙이면 그 씬도 자동으로 닫혀야 한다.
  // 씬마다 선언된 성경 참조. extras 가 아니라 payload 최상위다
  // (ScenePayloadAssembler.build — `sc.scriptureRef?.let { p["scriptureRef"] = it }`).
  const scriptureRef = payload.scriptureRef as string | undefined;
  // 절 단위 관례상 편 전체를 쓰는 씬은 나머지 절을 `additional_refs` 로 편다
  // (extras 안에 산다 — 그래서 payload 직독이 아니라 field 로 읽는다).
  const additionalRefs = field<string[]>("additional_refs");
  const warning = readTriggerWarning(payload);
  const needsConsent = !!warning && !passionConsented;

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Jesus — Scene {scene.currentScene}/7 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      {/* Decision echo — 직전 결정/씬의 모놀로그 */}
      {echo && (
        <section className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 bg-black/30 italic text-sm text-[var(--color-warm)]/90">
          <p className="whitespace-pre-line">{echo.text}</p>
          <div className="mt-2">
            <NarrationAudioButton text={echo.text} onUnavailable="hide" />
          </div>
          <p className="text-[10px] not-italic text-[var(--color-warm)]/40 mt-2 text-right">
            * AI 보조 — 본문은 성경 참조 *
          </p>
        </section>
      )}

      {/* Scene 배경 이미지 */}
      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative aspect-video bg-cover bg-center bg-slate-900"
        style={{
          backgroundImage: `url(/images/scenes/jesus/${scene.currentScene}.webp)`,
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-black/20 via-transparent to-black/70" />
        <div className="relative h-full flex items-end p-5">
          <p className="text-sm text-[var(--color-warm)]/80 italic max-w-prose">
            {scene.currentScene === 1 &&
              "베들레헴 외곽의 밤. 별빛 아래 소박한 구유 — 하늘이 낮은 자리로 내려온다."}
            {scene.currentScene === 2 &&
              "갈릴리 언덕의 아침. 무리 가운데 앉아 팔복을 듣는다 — 비어 있음이 복이라 하신다."}
            {scene.currentScene === 3 &&
              "군중이 물러선 자리. 나병 환자에게 예수는 오히려 다가가신다 — 손을 내밀어 닿으신다."}
            {scene.currentScene === 4 &&
              "어둑한 다락방. 세 갈래 빛의 길이 갈린다 — 길·진리·생명, 어느 결핍으로 다가갈 것인가."}
            {scene.currentScene === 5 &&
              "감람산 겟세마네의 밤. 빛과 그림자로만 그려지는 잔 — 뜻대로 마옵시고."}
            {scene.currentScene === 6 &&
              "이른 새벽 동산 무덤. 어둠이 여명으로 밝아 온다 — 이름을 부르시는 음성."}
            {scene.currentScene === 7 &&
              "밝은 묵상의 자리. 발치에서 작은 물줄기가 흐르기 시작한다 — 생명의 강."}
          </p>
        </div>
      </section>

      <section className="max-w-3xl mx-auto w-full space-y-3">
        {/* R2 고난 미화 방지 고지.
            동의 게이트 *위* 에 둔다. 동의를 받은 뒤에 띄우면 "견뎌라"로 읽힐 수 있는
            장면에 이미 들어간 뒤가 되고, 건너뛴 사람은 아예 못 본다. 판단 재료는
            판단 전에 있어야 한다. 그래서 동의 여부와 무관하게 이 씬 내내 붙어 있다.
            조건은 씬 타입이 아니라 payload 에 이 키가 있느냐다. */}
        {sufferingFooter && (
          <p className="text-xs text-[var(--color-warm)]/60 leading-relaxed max-w-prose mx-auto border-l-2 border-[var(--color-primary)]/30 pl-3">
            {sufferingFooter}
          </p>
        )}

        {needsConsent && warning ? (
          /* R4 동의 게이트. 동의 전에는 이 씬의 상호작용을 아예 렌더하지 않는다. */
          <TriggerWarningGate
            warning={warning}
            pending={decide.isPending}
            continueLabel="준비됐어요 · 함께 머물게요"
            skipLabel="이 장면은 건너뛸게요 →"
            fallbackProse={
              <p>
                다음 장면은 <strong>고통과 죽음(겟세마네·십자가)</strong> 을
                다룹니다. 약 2분. 직접 묘사 없이 빛과 그림자·본문으로만
                그려지지만, 지금이 버겁다면 이 장면은{" "}
                <strong>건너뛰어도 괜찮습니다</strong> — 건너뛰어도 결말과
                부활은 그대로 이어집니다.
              </p>
            }
            onContinue={() => setPassionConsented(true)}
            onSkip={() => advance(scene.currentScene, { value: "skip" })}
          />
        ) : (
          <>
            {/* 성경 본문 — 동의 게이트 *안쪽* 이다(Scene 4·5 는 겟세마네·십자가 경고를 단다). */}
            <ScenePassage
              reference={scriptureRef}
              additional={additionalRefs}
            />

            {/* Scene 1 — cinematic (성육신). 캡션만이 아니라 성육신 본문을 렌더한 뒤 계속. */}
            {sceneType === "cinematic" && (
              <>
                {scene.currentScene === 1 && (
                  <div className="px-4 py-4 rounded-lg bg-black/20 border border-[var(--color-primary)]/20">
                    <p className="text-sm text-[var(--color-warm)]/90 whitespace-pre-line leading-relaxed">
                      {scene1Incarnation}
                    </p>
                    <div className="mt-3">
                      <NarrationAudioButton
                        text={scene1Incarnation}
                        onUnavailable="hide"
                      />
                    </div>
                  </div>
                )}
                <button
                  onClick={() => {
                    setEcho(null);
                    advance(scene.currentScene, "next");
                  }}
                  className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
                  disabled={decide.isPending}
                >
                  {decide.isPending ? "..." : "계속 →"}
                </button>
              </>
            )}

            {/* Scene 2·6 — scripture_reading (팔복 / 부활 본문 건드리며 읽기) */}
            {sceneType === "scripture_reading" && (
              <ScriptureReading
                lines={lines}
                reflection={field<string>("reflection_prompt")}
                pending={decide.isPending}
                onComplete={() =>
                  advance(scene.currentScene, { value: "read" })
                }
              />
            )}

            {/* Scene 3 — gesture_sequence (만짐: 다가간다 → 손을 내민다) */}
            {sceneType === "gesture_sequence" && (
              <GestureSequence
                steps={field<OptionLike[]>("steps") ?? []}
                pending={decide.isPending}
                onComplete={() =>
                  advance(scene.currentScene, { value: "touch" })
                }
              />
            )}

            {/* Scene 4 — pick_one (길·진리·생명 3분기) */}
            {sceneType === "pick_one" &&
              Array.isArray(field<OptionLike[]>("options")) && (
                <div className="space-y-3">
                  {field<string>("context_line") && (
                    <p className="text-xs text-[var(--color-warm)]/60 italic px-1">
                      {field<string>("context_line")}
                    </p>
                  )}
                  <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    {(field<OptionLike[]>("options") as OptionLike[]).map(
                      (o) => (
                        <button
                          key={o.id}
                          onClick={() => advance(scene.currentScene, o.id)}
                          disabled={decide.isPending}
                          className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
                        >
                          {o.label}
                        </button>
                      ),
                    )}
                  </div>
                </div>
              )}

            {/* Scene 5 — contemplative (겟세마네·십자가). 동의를 받은 뒤에만 여기에 온다. */}
            {sceneType === "contemplative" && (
              <button
                onClick={() =>
                  advance(scene.currentScene, { value: "contemplate" })
                }
                disabled={decide.isPending}
                className="w-full py-4 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
              >
                {decide.isPending ? "..." : "잔 앞에 잠시 머문다 →"}
              </button>
            )}

            {/* Scene 7 — outro (승천·생명의 강 + 위기 라우팅) */}
            {sceneType === "outro" && (
              <div className="text-center space-y-4">
                <p className="text-base whitespace-pre-line text-[var(--color-warm)]/90 italic max-w-prose mx-auto">
                  {outroText}
                </p>
                <p className="text-xs whitespace-pre-line text-[var(--color-warm)]/60 max-w-prose mx-auto not-italic">
                  {scene7CrisisReminder}
                </p>
                <p className="text-[10px] not-italic text-[var(--color-warm)]/40 mt-2">
                  * AI 보조 — 본문은 성경 참조 *
                </p>
                <button
                  onClick={() =>
                    completeMission("jesus", scene.sessionId, "completed").then(
                      () => (location.href = "/"),
                    )
                  }
                  className="px-6 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
                >
                  미션 완료
                </button>
              </div>
            )}
          </>
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
            href="/david"
            className="flex-1 flex items-center justify-center min-h-11 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
          >
            David 미션 →
          </Link>
        </div>
      </section>

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
 * scripture_reading (Scene 2 팔복 / Scene 6 부활) — 본문 줄을 하나씩 손으로 건드려(눌러) 읽고,
 * 모두 읽으면 성찰 프롬프트가 열린 뒤 다음 씬으로 advance. 다윗 수금·모세 낭독과 동형.
 */
function ScriptureReading({
  lines,
  reflection,
  pending,
  onComplete,
}: {
  lines: Array<{ ref?: string; text?: string }>;
  reflection?: string;
  pending: boolean;
  onComplete: () => void;
}) {
  const [read, setRead] = useState<Set<number>>(new Set());
  const list =
    lines.length > 0 ? lines : [{ text: "본문을 천천히 읽어 봅니다." }];
  const allRead = read.size >= list.length;

  return (
    <div className="space-y-3">
      <p className="text-xs text-[var(--color-warm)]/60">
        한 줄씩 손으로 짚어 천천히 읽어 보세요.
      </p>
      <div className="space-y-2">
        {list.map((ln, i) => {
          const done = read.has(i);
          return (
            <button
              key={i}
              onClick={() => setRead((prev) => new Set(prev).add(i))}
              disabled={pending || done}
              className={`w-full text-left px-4 py-3 rounded-lg border transition disabled:cursor-default ${
                done
                  ? "border-[var(--color-primary)] bg-[var(--color-primary)]/10"
                  : "border-[var(--color-primary)]/30 hover:border-[var(--color-primary)]"
              }`}
            >
              <span className="text-sm text-[var(--color-warm)]/90">
                {ln.text}
              </span>
              {ln.ref && (
                <span className="ml-2 text-[10px] text-[var(--color-warm)]/40 uppercase">
                  {ln.ref}
                </span>
              )}
              {done && (
                <span className="ml-2 text-[var(--color-primary)]">✓</span>
              )}
            </button>
          );
        })}
      </div>
      {allRead && reflection && (
        <p className="px-4 py-3 rounded-lg bg-black/20 border border-[var(--color-primary)]/20 text-sm italic text-[var(--color-warm)]/80 whitespace-pre-line">
          {reflection}
        </p>
      )}
      <button
        onClick={onComplete}
        disabled={pending || !allRead}
        className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
      >
        {allRead ? "다음으로 →" : "본문을 모두 읽어 보세요"}
      </button>
    </div>
  );
}

/**
 * gesture_sequence (Scene 3 만짐) — steps 를 순서대로 눌러 완료. 마지막 step 에서 onComplete.
 * 다가간다 → 손을 내민다. 예수의 *접촉하시는 성품* 을 몸으로 체험 (치유 즉효 약속 아님).
 */
function GestureSequence({
  steps,
  pending,
  onComplete,
}: {
  steps: OptionLike[];
  pending: boolean;
  onComplete: () => void;
}) {
  const [done, setDone] = useState<Set<string>>(new Set());
  const list = steps.length > 0 ? steps : [{ id: "act", label: "손을 내민다" }];

  const handle = (id: string, isLast: boolean) => {
    const nextDone = new Set(done);
    nextDone.add(id);
    setDone(nextDone);
    if (isLast || nextDone.size >= list.length) onComplete();
  };

  return (
    <div className="space-y-2">
      <p className="text-xs text-[var(--color-warm)]/60">
        순서대로 몸짓을 이어가세요
      </p>
      <div className="grid grid-cols-1 gap-3">
        {list.map((s, i) => {
          const isLast = i === list.length - 1;
          const already = done.has(s.id);
          return (
            <button
              key={s.id}
              onClick={() => handle(s.id, isLast)}
              disabled={pending || already}
              className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-40 text-left"
            >
              <span className="text-[var(--color-warm)]/50 mr-2">{i + 1}.</span>
              {s.label}
              {already && (
                <span className="ml-2 text-[var(--color-primary)]">✓</span>
              )}
            </button>
          );
        })}
      </div>
    </div>
  );
}

/**
 * 직전 결정 (sceneId + decision) → echo 텍스트. jesus 는 씬별 정적 모놀로그(frontend fallback).
 * Scene 5 skip(건너뛰기) 시엔 십자가 고난 echo 를 띄우지 않는다 (R4).
 * Scene 4 는 선택(길/진리/생명)에 따른 예수의 응답 + teaching_note.
 */
function buildLocalEcho(fromScene: number, decision: unknown): string | null {
  switch (fromScene) {
    case 2: // 팔복
      return scene2Beatitudes;
    case 3: // 만짐
      return scene3Touch;
    case 4: {
      // 길·진리·생명 — 선택 분기 응답 + 세 얼굴이 한 분임(요 14:6 의도)
      const iam = iamOf(readValue(decision));
      return `${scene4Iam[iam]}\n\n${scene4Teaching}`;
    }
    case 5:
      // 건너뛰기(skip) 시엔 십자가 고난 묘사 echo 를 띄우지 않는다 (R4).
      if (readValue(decision) === "skip") return null;
      return scene5Passion;
    case 6: // 부활 — 이름이 불린다(R3)
      return scene6Resurrection;
    default:
      return null;
  }
}

function readValue(decision: unknown): string | null {
  if (typeof decision === "string") return decision;
  if (typeof decision === "object" && decision !== null) {
    const v = (decision as { value?: unknown }).value;
    if (typeof v === "string") return v;
  }
  return null;
}
