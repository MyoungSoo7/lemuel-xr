import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import { pollTtsJob, synthesizeTts } from "./tts";

/**
 * TTS 는 *보조 기능*이라 실패가 정상 시나리오에 포함된다(tts.ts 상단 주석 참조).
 * 여기서 확인하는 계약은 셋이다 — 요청이 정확한 모양으로 나가는가, 비동기 응답
 * (202/pending, 429/busy)을 훅이 쓸 수 있는 형태로 정규화하는가, 그리고 진짜 실패를
 * **이 계층에서 삼키지 않고** 호출자(useTtsNarration)에게 넘기는가.
 * 여기서 삼켜 버리면 호출자는 오디오가 없는지 실패했는지 구분할 수 없다.
 */
vi.mock("@/lib/api/client", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

const post = vi.mocked(api.post);
const get = vi.mocked(api.get);

/** 캐시 히트 — 200 으로 바로 재생 가능한 응답. */
const READY = {
  status: "ready",
  audioUrl: "/media/tts/abc.wav",
  durationMs: 4200,
  cached: true,
  jobId: null,
};

/** 429 를 흉내낸다 — axios.isAxiosError 는 이 플래그를 본다. */
function axiosError(status: number, headers: Record<string, string> = {}) {
  return Object.assign(new Error(`HTTP ${status}`), {
    isAxiosError: true,
    response: { status, headers },
  });
}

beforeEach(() => {
  post.mockResolvedValue({ data: READY } as never);
});

describe("synthesizeTts", () => {
  it("텍스트를 /api/tts/synthesize 로 보낸다", async () => {
    await synthesizeTts("여호와는 나의 목자시니");

    expect(post.mock.calls[0][0]).toBe("/api/tts/synthesize");
    expect(post.mock.calls[0][1]).toEqual({
      text: "여호와는 나의 목자시니",
      voiceId: undefined,
    });
  });

  it("voiceId 를 안 주면 키 자체가 전송되지 않는다 — 백엔드 기본 목소리 사용", async () => {
    await synthesizeTts("안녕");

    expect(JSON.stringify(post.mock.calls[0][1])).toBe('{"text":"안녕"}');
  });

  it("voiceId 를 주면 함께 보낸다", async () => {
    await synthesizeTts("안녕", "ko-female-1");

    expect(post.mock.calls[0][1]).toEqual({
      text: "안녕",
      voiceId: "ko-female-1",
    });
  });

  it("캐시 히트(ready)는 audioUrl 을 그대로 돌려준다", async () => {
    await expect(synthesizeTts("본문")).resolves.toEqual(READY);
  });

  it("202 pending 은 jobId 를 담아 돌려준다", async () => {
    post.mockResolvedValue({
      data: { status: "pending", cached: false, jobId: "job-1" },
    } as never);

    await expect(synthesizeTts("본문")).resolves.toEqual({
      status: "pending",
      audioUrl: null,
      durationMs: null,
      cached: false,
      jobId: "job-1",
    });
  });

  it("빠진 null 필드를 undefined 가 아니라 null 로 채운다", async () => {
    // 백엔드는 null 필드를 직렬화에서 뺀다. 훅이 매번 undefined 를 신경 쓰지 않도록
    // 여기서 모양을 고정한다.
    post.mockResolvedValue({ data: { status: "failed" } } as never);

    await expect(synthesizeTts("본문")).resolves.toEqual({
      status: "failed",
      audioUrl: null,
      durationMs: null,
      cached: false,
      jobId: null,
    });
  });

  it("durationMs 가 null 이어도 그대로 통과시킨다", async () => {
    // 길이를 모르는 스트리밍 응답이 0 으로 뭉개지면 재생 UI 가 즉시 끝난 것처럼 군다.
    post.mockResolvedValue({
      data: { ...READY, durationMs: null },
    } as never);

    await expect(synthesizeTts("본문")).resolves.toMatchObject({
      durationMs: null,
    });
  });

  it("429 는 예외가 아니라 busy 상태로 돌려주고 Retry-After 를 읽는다", async () => {
    // 큐가 찼다는 뜻이지 고장이 아니다 — 훅이 버튼을 영구히 죽이지 않게 구분해 준다.
    post.mockRejectedValue(axiosError(429, { "retry-after": "60" }));

    await expect(synthesizeTts("본문")).resolves.toEqual({
      status: "busy",
      audioUrl: null,
      durationMs: null,
      cached: false,
      jobId: null,
      retryAfterSeconds: 60,
    });
  });

  it("Retry-After 가 없거나 이상하면 기본 대기값을 쓴다", async () => {
    post.mockRejectedValue(axiosError(429, { "retry-after": "곧" }));

    await expect(synthesizeTts("본문")).resolves.toMatchObject({
      status: "busy",
      retryAfterSeconds: 30,
    });
  });

  it("사이드카가 없는 환경의 502 를 삼키지 않는다", async () => {
    post.mockRejectedValue(new Error("502 Bad Gateway"));

    await expect(synthesizeTts("본문")).rejects.toThrow("502 Bad Gateway");
  });
});

describe("pollTtsJob", () => {
  it("jobId 로 GET /api/tts/jobs/{id} 를 부른다", async () => {
    get.mockResolvedValue({ data: READY } as never);

    await expect(pollTtsJob("job-1")).resolves.toEqual(READY);
    expect(get.mock.calls[0][0]).toBe("/api/tts/jobs/job-1");
  });

  it("jobId 를 URL 인코딩한다", async () => {
    // jobId 는 sha256 hex 라 지금은 안전하지만, 그 가정이 깨져도 경로가 깨지지 않게.
    get.mockResolvedValue({ data: { status: "pending" } } as never);

    await pollTtsJob("a/b?c");

    expect(get.mock.calls[0][0]).toBe("/api/tts/jobs/a%2Fb%3Fc");
  });

  it("pending 응답도 정규화해서 돌려준다", async () => {
    get.mockResolvedValue({ data: { status: "pending", jobId: "j" } } as never);

    await expect(pollTtsJob("j")).resolves.toEqual({
      status: "pending",
      audioUrl: null,
      durationMs: null,
      cached: false,
      jobId: "j",
    });
  });
});
