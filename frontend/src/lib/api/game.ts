import { api } from "./client";

export interface JosephStartResponse {
  sessionId: string;
  userId: string;
  currentScene: number;
  scenePayload: Record<string, unknown>;
}

export async function startJoseph(deviceType = "web"): Promise<JosephStartResponse> {
  const res = await api.post<JosephStartResponse>("/api/game/joseph/start", {
    userId: null,
    deviceType,
  });
  return res.data;
}

export async function decideJoseph(
  sessionId: string,
  sceneId: number,
  decision: unknown,
): Promise<JosephStartResponse> {
  const res = await api.post<JosephStartResponse>(
    `/api/game/joseph/${sessionId}/decide`,
    { sceneId, decision },
  );
  return res.data;
}

export async function completeJoseph(sessionId: string, finalOutcome: string) {
  await api.post(`/api/game/joseph/${sessionId}/complete`, { finalOutcome });
}
