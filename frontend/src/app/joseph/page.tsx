"use client";
import { useEffect, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  startJoseph,
  decideJoseph,
  completeJoseph,
  type JosephStartResponse,
} from "@/lib/api/game";
import { Canvas } from "@react-three/fiber";
import { OrbitControls } from "@react-three/drei";

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
      <main className="min-h-screen flex items-center justify-center">
        <p className="text-[var(--color-warm)]/60">세션 시작 중...</p>
      </main>
    );
  }

  const payload = scene.scenePayload as Record<string, unknown>;
  const title = (payload.title as string) ?? "Scene";
  const sceneType = (payload.type as string) ?? "";

  return (
    <main className="min-h-screen flex flex-col p-6">
      <header className="max-w-3xl mx-auto w-full mb-4">
        <p className="text-xs text-[var(--color-warm)]/40 uppercase tracking-wider">
          Joseph — Scene {scene.currentScene}/5 · Mode: VR
        </p>
        <h1 className="text-2xl font-bold mt-1">{title}</h1>
      </header>

      <section className="flex-1 max-w-3xl mx-auto w-full rounded-xl border border-[var(--color-primary)]/20 overflow-hidden mb-4 relative">
        {/* Scene 별 배경 이미지 (Imagen 4.0 생성) */}
        <div
          className="absolute inset-0 bg-cover bg-center"
          style={{
            backgroundImage: `url(/images/scenes/${scene.currentScene}.jpg)`,
          }}
          aria-hidden
        />
        {/* 가독성용 어두운 overlay */}
        <div className="absolute inset-0 bg-gradient-to-b from-black/30 via-black/10 to-black/60" aria-hidden />

        <div className="relative h-[420px]">
          <Canvas camera={{ position: [3, 2, 4], fov: 50 }} style={{ background: "transparent" }}>
            <ambientLight intensity={0.5} />
            <directionalLight position={[5, 5, 5]} intensity={1.4} castShadow />
            <SceneMesh sceneId={scene.currentScene} />
            <OrbitControls enablePan={false} enableZoom={false} />
          </Canvas>
        </div>
      </section>

      <section className="max-w-3xl mx-auto w-full space-y-3">
        {sceneType === "cinematic" && (
          <button
            onClick={() => decide.mutate({ sceneId: scene.currentScene, decision: "next" })}
            className="w-full py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
          >
            계속 →
          </button>
        )}

        {sceneType === "pick_one" && Array.isArray(payload.options) && (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
            {(payload.options as Array<{ id: string; label: string }>).map((o) => (
              <button
                key={o.id}
                onClick={() => decide.mutate({ sceneId: scene.currentScene, decision: o.id })}
                className="px-4 py-4 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition"
              >
                {o.label}
              </button>
            ))}
          </div>
        )}

        {sceneType === "distribute" && Array.isArray(payload.queues) && (
          <div className="space-y-2">
            <p className="text-sm text-[var(--color-warm)]/60">
              MVP 웹 — 한 줄에 모든 곡식을 우선 분배할 줄 선택
            </p>
            <div className="grid grid-cols-3 gap-3">
              {(payload.queues as Array<{ id: string; label: string }>).map((q) => (
                <button
                  key={q.id}
                  onClick={() =>
                    decide.mutate({
                      sceneId: scene.currentScene,
                      decision: { priority: q.id },
                    })
                  }
                  className="px-4 py-6 rounded-lg border border-[var(--color-primary)]/30 hover:border-[var(--color-primary)] transition"
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
              "하나님이 생명을 구원하시려고 나를 너희 앞서 보내셨나니"
              <br />
              <span className="text-sm text-[var(--color-warm)]/50">(창 45:5)</span>
            </p>
            <button
              onClick={() => completeJoseph(scene.sessionId, "completed").then(() => location.href = "/")}
              className="px-6 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold"
            >
              미션 완료
            </button>
          </div>
        )}
      </section>

      {history.length > 0 && (
        <details className="max-w-3xl mx-auto w-full mt-6 text-xs text-[var(--color-warm)]/40">
          <summary>진행 기록</summary>
          <pre>{JSON.stringify(history, null, 2)}</pre>
        </details>
      )}
    </main>
  );
}

// MVP 3D 미리보기 — Scene 별 단순 mesh
function SceneMesh({ sceneId }: { sceneId: number }) {
  switch (sceneId) {
    case 1: // 파라오의 꿈 — 떠 있는 큐브 7개
      return (
        <>
          {Array.from({ length: 7 }).map((_, i) => (
            <mesh key={i} position={[Math.cos(i) * 2, Math.sin(i * 0.5) * 0.5, Math.sin(i) * 2]}>
              <boxGeometry args={[0.4, 0.4, 0.4]} />
              <meshStandardMaterial color={i < 4 ? "#c2a878" : "#5a4b3a"} />
            </mesh>
          ))}
        </>
      );
    case 2: // 풍년 — 곡식 자루 3개
      return (
        <>
          {[-1.5, 0, 1.5].map((x, i) => (
            <mesh key={i} position={[x, 0, 0]}>
              <sphereGeometry args={[0.6, 16, 12]} />
              <meshStandardMaterial color="#c2a878" />
            </mesh>
          ))}
        </>
      );
    case 3: // 분배 — 줄 3개
      return (
        <>
          {[-2, 0, 2].map((x, i) => (
            <group key={i}>
              {[0, 0.5, 1].map((z) => (
                <mesh key={z} position={[x, 0, z]}>
                  <cylinderGeometry args={[0.2, 0.2, 1, 8]} />
                  <meshStandardMaterial color="#8a7860" />
                </mesh>
              ))}
            </group>
          ))}
        </>
      );
    case 4: // 형제 재회
      return (
        <mesh position={[0, 0.5, 0]}>
          <torusGeometry args={[1, 0.3, 16, 32]} />
          <meshStandardMaterial color="#c2a878" />
        </mesh>
      );
    case 5: // 결말
      return (
        <mesh>
          <sphereGeometry args={[1.5, 32, 24]} />
          <meshStandardMaterial color="#f6f1e8" emissive="#c2a878" emissiveIntensity={0.3} />
        </mesh>
      );
    default:
      return null;
  }
}
