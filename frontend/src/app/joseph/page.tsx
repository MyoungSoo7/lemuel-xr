"use client";
import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  startJoseph,
  decideJoseph,
  completeJoseph,
  type JosephStartResponse,
} from "@/lib/api/game";

type Scene = JosephStartResponse;

export default function JosephPage() {
  const [scene, setScene] = useState<Scene | null>(null);
  const [history, setHistory] = useState<string[]>([]);

  const start = useMutation({
    mutationFn: () => startJoseph("web"),
    onSuccess: (d) => setScene(d),
  });

  const decide = useMutation({
    mutationFn: ({ sceneId, decision }: { sceneId: number; decision: unknown }) =>
      decideJoseph(scene!.sessionId, sceneId, decision),
    onSuccess: (d) => {
      setScene(d);
      setHistory((h) => [...h, JSON.stringify(d.scenePayload.title)]);
    },
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

  return (
    <main className="min-h-screen flex flex-col p-4 sm:p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Joseph — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      {/* Scene 배경 이미지 — R3F 제거, 정적 이미지 + 그라데이션 overlay (모바일 호환성↑) */}
      <section
        className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative aspect-video bg-cover bg-center"
        style={{ backgroundImage: `url(/images/scenes/${scene.currentScene}.jpg)` }}
      >
        <div className="absolute inset-0 bg-gradient-to-b from-black/20 via-transparent to-black/70" />
        <div className="relative h-full flex items-end p-5">
          <p className="text-sm text-[var(--color-warm)]/80 italic max-w-prose">
            {sceneType === "cinematic" && "파라오의 꿈을 해석한다. 7년 풍년과 7년 흉년이 다가온다."}
            {sceneType === "pick_one" && scene.currentScene === 2 && "이집트 전체 곡식 중 얼마를 저장할 것인가?"}
            {sceneType === "distribute" && "흉년 — 농민·이주민·상인 중 어느 줄에 곡식을 먼저 줄 것인가?"}
            {sceneType === "pick_one" && scene.currentScene === 4 && "형제들이 곡식을 구하러 왔다. 어떻게 응대할 것인가?"}
            {sceneType === "outro" && "여기까지 온 당신의 결정이 누군가의 양식이 된다."}
          </p>
        </div>
      </section>

      <section className="max-w-3xl mx-auto w-full space-y-3">
        {sceneType === "cinematic" && (
          <button
            onClick={() => decide.mutate({ sceneId: scene.currentScene, decision: "next" })}
            className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
            disabled={decide.isPending}
          >
            {decide.isPending ? "..." : "계속 →"}
          </button>
        )}

        {sceneType === "pick_one" && Array.isArray(payload.options) && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {(payload.options as Array<{ id: string; label: string }>).map((o) => (
              <button
                key={o.id}
                onClick={() => decide.mutate({ sceneId: scene.currentScene, decision: o.id })}
                disabled={decide.isPending}
                className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
              >
                {o.label}
              </button>
            ))}
          </div>
        )}

        {sceneType === "distribute" && Array.isArray(payload.queues) && (
          <div className="space-y-2">
            <p className="text-xs text-[var(--color-warm)]/60">
              한 줄에 모든 곡식을 우선 분배할 줄 선택
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
              {(payload.queues as Array<{ id: string; label: string }>).map((q) => (
                <button
                  key={q.id}
                  onClick={() =>
                    decide.mutate({
                      sceneId: scene.currentScene,
                      decision: { priority: q.id },
                    })
                  }
                  disabled={decide.isPending}
                  className="px-4 py-5 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition disabled:opacity-50"
                >
                  {q.label}
                </button>
              ))}
            </div>
          </div>
        )}

        {sceneType === "outro" && (
          <div className="text-center space-y-4">
            <p className="text-lg italic">
              &ldquo;하나님이 생명을 구원하시려고 나를 너희 앞서 보내셨나니&rdquo;
              <br />
              <span className="text-sm text-[var(--color-warm)]/50">(창 45:5)</span>
            </p>
            <button
              onClick={() =>
                completeJoseph(scene.sessionId, "completed").then(() => (location.href = "/"))
              }
              className="px-6 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
            >
              미션 완료
            </button>
          </div>
        )}

        {decide.isError && (
          <p className="text-red-400 text-sm mt-2">
            오류: {(decide.error as Error).message}
          </p>
        )}
      </section>

      {history.length > 0 && (
        <details className="max-w-3xl mx-auto w-full mt-6 text-xs text-[var(--color-warm)]/40">
          <summary>진행 기록</summary>
          <pre className="overflow-x-auto">{JSON.stringify(history, null, 2)}</pre>
        </details>
      )}
    </main>
  );
}
