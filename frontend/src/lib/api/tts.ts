import { api } from "./client";

/**
 * TTS 나레이션 — POST /api/tts/synthesize.
 *
 * 프로덕션엔 TTS 사이드카(Coqui XTTS-v2)가 배포돼 실제 한국어 음성을 생성한다.
 * 로컬/개발엔 사이드카가 없어 502·503·타임아웃 등으로 실패할 수 있으며, 그건 정상이다.
 * 오디오는 *보조 기능* 이므로 호출자는 실패를 조용히 삼키고 UI 를 비활성화해야 한다
 * (에러 표면화 금지 — useTtsNarration / NarrationAudioButton 참조).
 *
 * voiceId 는 굳이 노출하지 않고 백엔드 기본값을 쓴다.
 */
export interface SynthesizeTtsResponse {
  audioUrl: string;
  durationMs: number | null;
  cached: boolean;
}

export async function synthesizeTts(
  text: string,
  voiceId?: string,
): Promise<SynthesizeTtsResponse> {
  const res = await api.post<SynthesizeTtsResponse>("/api/tts/synthesize", {
    text,
    voiceId,
  });
  return res.data;
}
