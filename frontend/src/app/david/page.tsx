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
 * David 미션 — B 활성화 단계.
 *
 * Scene 1 (양과 시편 cinematic) 까지만 실제 backend 호출 + UI 인터랙션.
 * Scene 2~6 의 *interaction* (gesture / 5돌 = 5감정 / 사울 갑옷 / 골리앗) 은
 * 자문 검토 통과 후 활성. Joseph 미션과 같은 골격.
 */
type Scene = JosephStartResponse;

const SCENE_PREVIEW = [
  { id: 1, title: "양 떼와 시편", anchor: "평온 · 자기 존재감", active: true },
  { id: 2, title: "형들의 비웃음", anchor: "모욕감 · 내적 분노", active: false },
  { id: 3, title: "사울의 갑옷", anchor: "자기 정체성 확립 · 해방 ★", active: false },
  { id: 4, title: "시냇가의 5돌", anchor: "두려움 + 신뢰의 혼합 ★", active: false },
  { id: 5, title: "골리앗", anchor: "떨림 → 일어섬", active: false },
  { id: 6, title: "회복 메시지", anchor: "안식 + 다음 거인 대비", active: false },
];

export default function DavidPage() {
  const [scene, setScene] = useState<Scene | null>(null);

  const start = useMutation({
    mutationFn: () => startMission("david", "web"),
    onSuccess: (d) => setScene(d),
  });
  const decide = useMutation({
    mutationFn: ({ sceneId, decision }: { sceneId: number; decision: unknown }) =>
      decideMission("david", scene!.sessionId, sceneId, decision),
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
          David — Scene {scene.currentScene}/6 · Phase {scene.currentScene === 1 ? "1" : "2 (안내)"}
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      <section className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative aspect-video bg-gradient-to-b from-stone-900 via-stone-800 to-stone-950">
        <div className="absolute inset-0 flex items-end p-5">
          <p className="text-sm text-[var(--color-warm)]/80 italic max-w-prose">
            {scene.currentScene === 1 && "양 떼 곁의 평온. 어린 시인 다윗의 새벽 — 시편 23편이 흐른다."}
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
              본 미션의 Scene 2~6 인터랙션은 신학·임상 자문 검토 후 활성됩니다. ★ = 핵심 Scene.
            </p>
            <p className="text-xs text-[var(--color-warm)]/60 italic whitespace-pre-line">
              {SCENE_PREVIEW.slice(1)
                .map((s) => `Scene ${s.id} — ${s.title} (${s.anchor})`)
                .join("\n")}
            </p>
            <button
              onClick={() =>
                completeMission("david", scene.sessionId, "phase1_only").then(
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
            href="/joseph"
            className="flex-1 text-center px-4 py-2 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm"
          >
            Joseph 미션 →
          </Link>
        </div>
      </section>
    </main>
  );
}
