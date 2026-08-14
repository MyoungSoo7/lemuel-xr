import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import {
  completeJoseph,
  completeMission,
  decideJoseph,
  decideMission,
  startJoseph,
  startMission,
  type JosephStartResponse,
  type MissionCharacter,
} from "./game";

/**
 * 이 모듈은 게임 진행의 *배선*이다. 여기서 URL 이나 바디 모양이 한 글자 어긋나면
 * 백엔드는 404/400 을 내고 사용자는 미션 도중에 멈춘다. 그래서 재는 것은
 * "post 가 불렸다"가 아니라 **어떤 경로에 어떤 바디가 나갔는가**다.
 *
 * 특히 decision wrapping — 백엔드 Jackson 이 Map<String,Object> 로 받기 때문에
 * 문자열 결정은 반드시 { value } 로 감싸져야 하고, 이미 Map 인 결정은 감싸면 안 된다.
 */
vi.mock("@/lib/api/client", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

const post = vi.mocked(api.post);

const SCENE: JosephStartResponse = {
  sessionId: "sess-1",
  userId: "user-1",
  currentScene: 3,
  scenePayload: { title: "꿈 해석" },
};

function resolveWith(data: unknown) {
  post.mockResolvedValue({ data } as never);
}

/** 마지막 post 호출의 [url, body] */
function lastCall(): [string, unknown] {
  const call = post.mock.calls.at(-1) as [string, unknown];
  return [call[0], call[1]];
}

beforeEach(() => {
  resolveWith(SCENE);
});

describe("startJoseph", () => {
  it("userId 를 null 로 보낸다 — 신원은 JWT 에서만 나온다", async () => {
    await startJoseph();

    expect(lastCall()).toEqual([
      "/api/game/joseph/start",
      { userId: null, deviceType: "web" },
    ]);
  });

  it("deviceType 기본값은 web, 인자로 덮을 수 있다", async () => {
    await startJoseph("quest3");

    expect(lastCall()[1]).toEqual({ userId: null, deviceType: "quest3" });
  });

  it("응답 body 를 그대로 돌려준다 (axios envelope 를 벗긴다)", async () => {
    await expect(startJoseph()).resolves.toBe(SCENE);
  });

  it("네트워크 실패는 삼키지 않고 호출자에게 넘긴다", async () => {
    post.mockRejectedValue(new Error("503"));

    await expect(startJoseph()).rejects.toThrow("503");
  });
});

describe("decideJoseph — decision wrapping", () => {
  it("문자열 결정은 { value } 로 감싼다 (cinematic next / pick_one)", async () => {
    await decideJoseph("sess-1", 4, "save_33");

    expect(lastCall()).toEqual([
      "/api/game/joseph/sess-1/decide",
      { sceneId: 4, decision: { value: "save_33" } },
    ]);
  });

  it("이미 Map 인 결정은 그대로 보낸다 (distribute)", async () => {
    await decideJoseph("sess-1", 7, { priority: "farmer" });

    expect(lastCall()[1]).toEqual({
      sceneId: 7,
      decision: { priority: "farmer" },
    });
  });

  it("sessionId 가 경로에 들어간다 — 세션이 바뀌면 경로도 바뀐다", async () => {
    await decideJoseph("sess-42", 1, "next");

    expect(lastCall()[0]).toBe("/api/game/joseph/sess-42/decide");
  });

  it("responseText(Phase 2-A quote)를 잃지 않고 전달한다", async () => {
    // 직전 결정의 반응 텍스트가 여기서 잘리면 화면 상단 quote 가 조용히 빈다.
    resolveWith({ ...SCENE, responseText: "형들이 고개를 떨궜다" });

    const res = await decideJoseph("sess-1", 2, "next");

    expect(res.responseText).toBe("형들이 고개를 떨궜다");
  });
});

describe("completeJoseph", () => {
  it("finalOutcome 만 보내고 아무것도 돌려주지 않는다", async () => {
    const res = await completeJoseph("sess-1", "forgiveness");

    expect(lastCall()).toEqual([
      "/api/game/joseph/sess-1/complete",
      { finalOutcome: "forgiveness" },
    ]);
    expect(res).toBeUndefined();
  });
});

describe("generic mission helper", () => {
  const characters: MissionCharacter[] = [
    "joseph",
    "moses",
    "david",
    "jesus",
    "solomon",
    "elijah",
    "job",
  ];

  it.each(characters)("%s 의 start 경로를 만든다", async (character) => {
    await startMission(character);

    expect(lastCall()).toEqual([
      `/api/game/${character}/start`,
      { userId: null, deviceType: "web" },
    ]);
  });

  it("startMission 도 deviceType 을 덮을 수 있다", async () => {
    await startMission("moses", "vision-pro");

    expect(lastCall()[1]).toEqual({ userId: null, deviceType: "vision-pro" });
  });

  it("decideMission 도 문자열 결정을 { value } 로 감싼다", async () => {
    await decideMission("david", "sess-9", 5, "confess");

    expect(lastCall()).toEqual([
      "/api/game/david/sess-9/decide",
      { sceneId: 5, decision: { value: "confess" } },
    ]);
  });

  it("decideMission 도 Map 결정은 통과시킨다", async () => {
    await decideMission("job", "sess-9", 2, { priority: "silence" });

    expect(lastCall()[1]).toEqual({
      sceneId: 2,
      decision: { priority: "silence" },
    });
  });

  it("completeMission 은 인물별 complete 경로로 보낸다", async () => {
    await completeMission("elijah", "sess-3", "restored");

    expect(lastCall()).toEqual([
      "/api/game/elijah/sess-3/complete",
      { finalOutcome: "restored" },
    ]);
  });

  it("joseph 전용 함수와 generic 함수가 같은 요청을 만든다", async () => {
    // 두 경로가 갈라지면 요셉만 다른 계약으로 굳는다 — 그걸 여기서 묶어 둔다.
    await startJoseph();
    const dedicatedStart = lastCall();
    await startMission("joseph");
    expect(lastCall()).toEqual(dedicatedStart);

    await decideJoseph("s", 1, "next");
    const dedicatedDecide = lastCall();
    await decideMission("joseph", "s", 1, "next");
    expect(lastCall()).toEqual(dedicatedDecide);

    await completeJoseph("s", "done");
    const dedicatedComplete = lastCall();
    await completeMission("joseph", "s", "done");
    expect(lastCall()).toEqual(dedicatedComplete);
  });
});
