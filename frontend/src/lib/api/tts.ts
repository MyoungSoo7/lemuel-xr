import axios from "axios";
import { api } from "./client";

/**
 * TTS 나레이션 — 비동기(202 + 폴링) 계약.
 *
 * 프로덕션엔 TTS 사이드카(Coqui XTTS-v2)가 배포돼 실제 한국어 음성을 생성한다.
 * CPU-only XTTS-v2 는 오디오 1초당 CPU 4.1~4.6초를 쓰기 때문에, 합성을 HTTP 요청
 * 하나가 붙잡고 기다릴 수가 없다 (실측: 30초 타임아웃에 걸려 502 가 났고, 그때
 * 사이드카는 같은 요청을 200 으로 처리하고 있었다). 그래서 백엔드는
 *
 *   POST /api/tts/synthesize  → 200 (캐시 히트, 바로 재생) 또는 202 + jobId
 *   GET  /api/tts/jobs/{id}   → pending / ready / failed
 *
 * 로 나뉜다. 로컬/개발엔 사이드카가 없어 실패할 수 있으며 그건 정상이다.
 * 오디오는 *보조 기능* 이므로 호출자는 실패를 조용히 삼키고 UI 를 비활성화해야 한다
 * (에러 표면화 금지 — useTtsNarration / NarrationAudioButton 참조).
 *
 * voiceId 는 굳이 노출하지 않고 백엔드 기본값을 쓴다.
 */
export type TtsJobStatus = "ready" | "pending" | "failed" | "busy";

export interface SynthesizeTtsResponse {
  status: TtsJobStatus;
  /** status === "ready" 일 때만 채워진다. */
  audioUrl: string | null;
  durationMs: number | null;
  cached: boolean;
  /** status === "pending" 일 때 폴링에 쓰는 키. */
  jobId: string | null;
  /** status === "busy" 일 때 서버가 알려준 재시도 대기(초). */
  retryAfterSeconds?: number;
}

/**
 * 백엔드는 null 필드를 직렬화에서 빼기 때문에 audioUrl/durationMs/jobId 가 아예
 * 없는 응답이 온다. 훅이 매번 undefined 를 신경 쓰지 않도록 여기서 null 로 고른다.
 */
function normalize(raw: Partial<SynthesizeTtsResponse>): SynthesizeTtsResponse {
  return {
    status: raw.status ?? "failed",
    audioUrl: raw.audioUrl ?? null,
    durationMs: raw.durationMs ?? null,
    cached: raw.cached ?? false,
    jobId: raw.jobId ?? null,
  };
}

/** 큐 포화(429)는 예외가 아니라 정상적인 응답 상태다 — busy 로 바꿔 돌려준다. */
const DEFAULT_RETRY_AFTER_SECONDS = 30;

export async function synthesizeTts(
  text: string,
  voiceId?: string,
): Promise<SynthesizeTtsResponse> {
  try {
    const res = await api.post<Partial<SynthesizeTtsResponse>>(
      "/api/tts/synthesize",
      { text, voiceId },
    );
    return normalize(res.data);
  } catch (e) {
    // 워커가 이미 꽉 찼다는 뜻이지 고장이 아니다. 호출자가 "잠시 뒤"를 알 수 있게
    // 에러 대신 busy 로 표현한다. 그 외 오류는 그대로 던져 기존 처리(조용한 비활성)로.
    if (axios.isAxiosError(e) && e.response?.status === 429) {
      const header = e.response.headers?.["retry-after"];
      const parsed = Number(header);
      return {
        status: "busy",
        audioUrl: null,
        durationMs: null,
        cached: false,
        jobId: null,
        retryAfterSeconds:
          Number.isFinite(parsed) && parsed > 0
            ? parsed
            : DEFAULT_RETRY_AFTER_SECONDS,
      };
    }
    throw e;
  }
}

/** 폴링 — 아직이면 pending, 끝났으면 ready + audioUrl, 실패면 failed. */
export async function pollTtsJob(
  jobId: string,
): Promise<SynthesizeTtsResponse> {
  const res = await api.get<Partial<SynthesizeTtsResponse>>(
    `/api/tts/jobs/${encodeURIComponent(jobId)}`,
  );
  return normalize(res.data);
}
