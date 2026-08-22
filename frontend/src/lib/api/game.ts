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

export async function startJoseph(
  deviceType = "web",
): Promise<JosephStartResponse> {
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
    typeof decision === "string"
      ? { value: decision }
      : (decision as Record<string, unknown>);
  const res = await api.post<JosephStartResponse>(
    `/api/game/joseph/${sessionId}/decide`,
    { sceneId, decision: wrapped },
  );
  return res.data;
}

export async function completeJoseph(sessionId: string, finalOutcome: string) {
  await api.post(`/api/game/joseph/${sessionId}/complete`, { finalOutcome });
}

/**
 * B — Joseph 외 인물 ({moses,david,jesus,solomon,elijah,job,ruth,peter,daniel}) 의 동일 흐름 generic helper.
 *
 * 이 union 은 백엔드 `Character` enum 과 손으로 맞추는 자리다. 백엔드에 인물이
 * 등재됐는데 여기 없으면 화면을 만들 수조차 없다 — 룻이 그랬다. enum 에는 있어서
 * `/api/game/ruth/start` 는 200 을 주는데 프론트 타입이 막아 화면이 없었고,
 * `check_frontend_trigger_warning.py` 는 "화면이 없다" 며 판정을 건너뛰었다.
 * 건너뛴 건 통과가 아니다.
 */
export type MissionCharacter =
  | "joseph"
  | "moses"
  | "david"
  | "jesus"
  | "solomon"
  | "elijah"
  | "job"
  | "ruth"
  | "peter"
  | "daniel"
  // 에스더는 #98 에서 이 union 에 들어왔는데, 하루 뒤 #99(아브라함) 머지가 같은 줄을
  // 건드리면서 **조용히 빠졌다.** 백엔드 enum·화면·테스트는 다 남고 이 한 줄만 사라져서
  // `/esther` 는 열린 채로 `tsc --noEmit` 만 빨개진 상태가 됐다. 되돌려 놓는다.
  | "esther"
  // 아브라함은 2026-08-22 에 열렸다 — 백엔드 `Character` enum 에 `ABRAHAM("abraham")` 이
  // 들어가면서 `ScenarioYamlLoader.loadAll()` 이 abraham.yml 을 싣는다.
  // 이 union 자체는 게이트가 아니다(화면이 컴파일되게 하는 자리다). 게이트는
  // `docs/ABRAHAM-RUNTIME-SIGNOFF.md` + `RuntimeExposureSignoffTest` 다.
  | "abraham";

export async function startMission(
  character: MissionCharacter,
  deviceType = "web",
): Promise<JosephStartResponse> {
  const res = await api.post<JosephStartResponse>(
    `/api/game/${character}/start`,
    {
      userId: null,
      deviceType,
    },
  );
  return res.data;
}

export async function decideMission(
  character: MissionCharacter,
  sessionId: string,
  sceneId: number,
  decision: unknown,
): Promise<JosephStartResponse> {
  const wrapped =
    typeof decision === "string"
      ? { value: decision }
      : (decision as Record<string, unknown>);
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
  await api.post(`/api/game/${character}/${sessionId}/complete`, {
    finalOutcome,
  });
}
