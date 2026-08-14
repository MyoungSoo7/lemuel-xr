import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import { synthesizeTts, type SynthesizeTtsResponse } from "./tts";

/**
 * TTS 는 *보조 기능*이라 실패가 정상 시나리오에 포함된다(파일 상단 주석 참조).
 * 여기서 확인하는 계약은 두 가지다 — 요청이 정확한 모양으로 나가는가,
 * 그리고 실패를 **이 계층에서 삼키지 않고** 호출자(useTtsNarration)에게 넘기는가.
 * 여기서 삼켜 버리면 호출자는 오디오가 없는지 실패했는지 구분할 수 없다.
 */
vi.mock("@/lib/api/client", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

const post = vi.mocked(api.post);

const AUDIO: SynthesizeTtsResponse = {
  audioUrl: "/media/tts/abc.wav",
  durationMs: 4200,
  cached: true,
};

beforeEach(() => {
  post.mockResolvedValue({ data: AUDIO } as never);
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

  it("audioUrl·durationMs·cached 를 그대로 돌려준다", async () => {
    await expect(synthesizeTts("본문")).resolves.toBe(AUDIO);
  });

  it("durationMs 가 null 이어도 그대로 통과시킨다", async () => {
    // 길이를 모르는 스트리밍 응답이 0 으로 뭉개지면 재생 UI 가 즉시 끝난 것처럼 군다.
    post.mockResolvedValue({
      data: { ...AUDIO, durationMs: null },
    } as never);

    await expect(synthesizeTts("본문")).resolves.toMatchObject({
      durationMs: null,
    });
  });

  it("사이드카가 없는 환경의 502 를 삼키지 않는다", async () => {
    post.mockRejectedValue(new Error("502 Bad Gateway"));

    await expect(synthesizeTts("본문")).rejects.toThrow("502 Bad Gateway");
  });
});
