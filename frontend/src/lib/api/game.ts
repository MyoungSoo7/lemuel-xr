import { api } from "./client";

export interface JosephStartResponse {
  sessionId: string;
  userId: string;
  currentScene: number;
  scenePayload: Record<string, unknown>;
  /**
   * Phase 2-A — backend 가 yml 의 monologues/outcomes/reactions 에서 매칭한
   * 직전 결정의 응답 텍스트. Scene 진입 시 *상단 quote 영역* 에 표시.
   * decide 응답에만 있고, start 응답에는 없음 (optional).
   */
  responseText?: string | null;
}

export async function startJoseph(deviceType = "web"): Promise<JosephStartResponse> {
  const res = await api.post<JosephStartResponse>("/api/game/joseph/start", {
    userId: null,
    deviceType,
  });
  return res.data;
}

/**
 * decision 은 항상 Map 형태로 wrap (backend Jackson 이 Map<String,Object> 로 받음):
 *  - cinematic next  → { value: "next" }
 *  - pick_one        → { value: "save_33" }
 *  - distribute      → { priority: "farmer" }
 *
 * 호출 측은 wrap 안 해도 됨 — 본 함수가 string 입력을 자동으로 { value } 로 감싼다.
 */
export async function decideJoseph(
  sessionId: string,
  sceneId: number,
  decision: unknown,
): Promise<JosephStartResponse> {
  const wrapped =
    typeof decision === "string" ? { value: decision } : (decision as Record<string, unknown>);
  const res = await api.post<JosephStartResponse>(
    `/api/game/joseph/${sessionId}/decide`,
    { sceneId, decision: wrapped },
  );
  return res.data;
}

export async function completeJoseph(sessionId: string, finalOutcome: string) {
  await api.post(`/api/game/joseph/${sessionId}/complete`, { finalOutcome });
}

/** B — Joseph 외 인물 ({moses,david,jesus}) 의 동일 흐름 generic helper. */
export type MissionCharacter = "joseph" | "moses" | "david" | "jesus";

export async function startMission(
  character: MissionCharacter,
  deviceType = "web",
): Promise<JosephStartResponse> {
  const res = await api.post<JosephStartResponse>(`/api/game/${character}/start`, {
    userId: null,
    deviceType,
  });
  return res.data;
}

export async function decideMission(
  character: MissionCharacter,
  sessionId: string,
  sceneId: number,
  decision: unknown,
): Promise<JosephStartResponse> {
  const wrapped =
    typeof decision === "string" ? { value: decision } : (decision as Record<string, unknown>);
  const res = await api.post<JosephStartResponse>(
    `/api/game/${character}/${sessionId}/decide`,
    { sceneId, decision: wrapped },
  );
  return res.data;
}

export async function completeMission(
  character: MissionCharacter,
  sessionId: string,
  finalOutcome: string,
) {
  await api.post(`/api/game/${character}/${sessionId}/complete`, { finalOutcome });
}
