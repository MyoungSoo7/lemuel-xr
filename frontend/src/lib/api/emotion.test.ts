import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import { classifyEmotion, type ClassifyResponse } from "./emotion";

/**
 * 감정 분류는 사용자가 쓴 문장이 그대로 백엔드로 나가는 경로다. 문장이 잘리거나
 * 다른 필드에 실려 나가면 분류가 어긋나고, 어긋난 분류는 곧 잘못된 추천(트랙 A/B)이 된다.
 * 그래서 *직렬화된 바디* 수준에서 무엇이 나가는지를 잰다.
 */
vi.mock("@/lib/api/client", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

const post = vi.mocked(api.post);

const RESULT: ClassifyResponse = {
  emotionLogId: 12,
  primary: { emotion: "ANXIOUS", confidence: 0.82 },
  recommendations: {
    trackA: [{ topicId: 3, title: "불안", rationale: "호흡" }],
    trackB: [{ character: "joseph" }],
  },
};

beforeEach(() => {
  post.mockResolvedValue({ data: RESULT } as never);
});

describe("classifyEmotion", () => {
  it("사용자 문장을 그대로 /api/emotion/classify 로 보낸다", async () => {
    await classifyEmotion("요즘 잠이 안 와요");

    expect(post.mock.calls[0][0]).toBe("/api/emotion/classify");
    expect((post.mock.calls[0][1] as { text: string }).text).toBe(
      "요즘 잠이 안 와요",
    );
  });

  it("preferredMode 가 없으면 context 키 자체가 전송되지 않는다", async () => {
    // undefined 는 JSON 직렬화에서 사라진다 — 백엔드가 null context 를 따로
    // 처리하지 않아도 되게 하는 성질이라 wire 레벨로 확인한다.
    await classifyEmotion("괜찮아요");

    expect(JSON.stringify(post.mock.calls[0][1])).toBe('{"text":"괜찮아요"}');
  });

  it("preferredMode 를 주면 context 로 감싸서 보낸다", async () => {
    await classifyEmotion("답답해요", "mr");

    expect(post.mock.calls[0][1]).toEqual({
      text: "답답해요",
      context: { preferredMode: "mr" },
    });
  });

  it("분류 결과와 추천을 손대지 않고 그대로 돌려준다", async () => {
    await expect(classifyEmotion("무기력해요")).resolves.toBe(RESULT);
  });

  it("실패는 호출자에게 전달한다 — 조용히 빈 추천을 만들지 않는다", async () => {
    post.mockRejectedValue(new Error("500"));

    await expect(classifyEmotion("힘들어요")).rejects.toThrow("500");
  });
});
