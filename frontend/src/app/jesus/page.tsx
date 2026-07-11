"use client";

import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";

/**
 * Jesus 미션 — B 트랙의 정점(capstone).
 *
 * Scene 1 (성육신 cinematic) 까지만 실제 backend 호출 + UI 인터랙션.
 * Scene 2~7 의 *interaction* (팔복 / 만짐 / 길·진리·생명 / 겟세마네 / 부활 / 승천)
 * 은 신학·임상 자문 검토 통과 후 활성. Joseph/David 미션과 같은 골격.
 *
 * 영적 구원이 경제(요셉)·정치(모세)·외세(다윗) 구원을 감싸는 포괄 서사.
 */
type Scene = JosephStartResponse;

const SCENE_PREVIEW = [
  { id: 1, title: "성육신 — 말씀이 육신이 되어", anchor: "낮아짐 · 임재 · 시작", active: true },
  { id: 2, title: "산 위의 가르침 — 팔복", anchor: "뒤집힌 복 · 마음의 가난", active: false },
  { id: 3, title: "만짐 — 병자를 고치심", anchor: "접촉 · 존엄 회복", active: false },
  { id: 4, title: "길이요 진리요 생명", anchor: "방향 · 신뢰 · 핵심 통찰 ★", active: false },
  { id: 5, title: "겟세마네와 십자가", anchor: "내려놓음 · 함께 고통하심 ★", active: false },
  { id: 6, title: "부활 — 빈 무덤", anchor: "소망 · 부르심 · 이름", active: false },
  { id: 7, title: "승천과 생명의 강", anchor: "임재의 지속 · 흐르는 생명", active: false },
];

export default function JesusPage() {
  const [scene, setScene] = useState<Scene | null>(null);

  const start = useMutation({
    mutationFn: () => startMission("jesus", "web"),
    onSuccess: (d) => setScene(d),
  });
  const decide = useMutation({
    mutationFn: ({ sceneId, decision }: { sceneId: number; decision: unknown }) =>
      decideMission("jesus", scene!.sessionId, sceneId, decision),
    onSuccess: (d) => setScene(d),
  });

  useEffect(() => {
    if (!scene && !start.isPending && !start.isError) start.mutate();
  }, [scene, start]);

  if (!scene) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6">
        <p className="text-[var(--color-warm)]/60">세션 시작 중...</p>
      </main>
    );
  }

  const payload = scene.scenePayload as Record<string, unknown>;
  const title = (payload.title as string) ?? "Scene";
  const sceneType = (payload.type as string) ?? "";

  const isActiveCinematic = scene.currentScene === 1 && sceneType === "cinematic";

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Jesus — Scene {scene.currentScene}/7 · Phase {scene.currentScene === 1 ? "1" : "2 (안내)"}
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      <section className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative aspect-video bg-gradient-to-b from-stone-900 via-stone-800 to-stone-950">
        <div className="absolute inset-0 flex items-end p-5">
          <p className="text-sm text-[var(--color-warm)]/80 italic max-w-prose">
            {scene.currentScene === 1 && "말씀이 육신이 되어 우리 가운데 오셨다. 가장 낮은 자리에서 시작되는 임재 — 요한복음 1장이 흐른다."}
            {scene.currentScene > 1 && "Phase 2 — 자문 통과 후 본 Scene 의 인터랙션이 활성됩니다."}
          </p>
        </div>
      </section>

      <section className="max-w-3xl mx-auto w-full space-y-3">
        {isActiveCinematic && (
          <button
            onClick={() => decide.mutate({ sceneId: scene.currentScene, decision: "next" })}
            className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
            disabled={decide.isPending}
          >
            {decide.isPending ? "..." : "계속 →"}
          </button>
        )}

        {!isActiveCinematic && (
          <div className="rounded-lg border border-[var(--color-primary)]/30 bg-black/30 px-5 py-4 space-y-3">
            <p className="text-sm text-[var(--color-warm)]/80">
              <strong className="text-[var(--color-primary)]">Phase 2 — 자문 통과 후 활성</strong>
              <br />
              본 미션의 Scene 2~7 인터랙션은 신학·임상 자문 검토 후 활성됩니다. ★ = 핵심 Scene.
            </p>
            <p className="text-xs text-[var(--color-warm)]/60 italic whitespace-pre-line">
              {SCENE_PREVIEW.slice(1)
                .map((s) => `Scene ${s.id} — ${s.title} (${s.anchor})`)
                .join("\n")}
            </p>
            <button
              onClick={() =>
                completeMission("jesus", scene.sessionId, "phase1_only").then(
                  () => (location.href = "/"),
                )
              }
              className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold mt-2"
            >
              미션 종료 (Phase 1 완료)
            </button>
          </div>
        )}

        <div className="pt-2 flex gap-3">
          <Link
            href="/"
            className="flex-1 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
          >
            ← 홈
          </Link>
          <Link
            href="/david"
            className="flex-1 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
          >
            David 미션 →
          </Link>
        </div>
      </section>
    </main>
  );
}
