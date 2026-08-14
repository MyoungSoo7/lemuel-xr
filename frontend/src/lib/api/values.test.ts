import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import {
  getValueProfile,
  recordPractice,
  updateValueProfile,
  type ProfileResponse,
} from "./values";

/**
 * 가치 프로필은 사용자가 직접 쓴 자기 서술(values)과 실천 기록(practice)이다.
 * 다시 만들 수 없는 데이터라, 여기서 재는 것은 "저장이 호출됐다"가 아니라
 * **보낸 것이 그대로 보내졌는가** — 서버로 나가는 바디가 사용자가 준 것과 같은가다.
 */
vi.mock("@/lib/api/client", () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);

const PROFILE: ProfileResponse = {
  values: { compassion: { title: "긍휼", anchor_character: "jesus" } },
  stats: {
    totalPractices7d: 4,
    countByValue: { compassion: 4 },
    cdrIndex: 0.7,
    tier: "GROWING",
  },
};

beforeEach(() => {
  get.mockResolvedValue({ data: PROFILE } as never);
  post.mockResolvedValue({ data: PROFILE } as never);
});

describe("getValueProfile", () => {
  it("GET /api/values/me — 내 프로필은 경로에 id 를 싣지 않는다(JWT 로 식별)", async () => {
    await getValueProfile();

    expect(get.mock.calls[0][0]).toBe("/api/values/me");
    expect(get.mock.calls[0][1]).toBeUndefined();
  });

  it("values 와 stats 를 그대로 돌려준다", async () => {
    await expect(getValueProfile()).resolves.toBe(PROFILE);
  });

  it("실패를 삼키지 않는다", async () => {
    get.mockRejectedValue(new Error("401"));

    await expect(getValueProfile()).rejects.toThrow("401");
  });
});

describe("updateValueProfile", () => {
  it("patch 를 감싸지 않고 최상위 바디로 보낸다", async () => {
    // { values: patch } 로 한 겹 감싸면 백엔드가 통째로 무시하고 조용히 200 을 준다.
    const patch = { courage: { title: "용기", anchor_scripture: "수 1:9" } };

    await updateValueProfile(patch);

    expect(post.mock.calls[0]).toEqual(["/api/values/profile", patch]);
  });

  it("부분 수정이라 보낸 키만 나간다 — 안 보낸 가치는 지워지지 않아야 한다", async () => {
    await updateValueProfile({ compassion: { title: "긍휼 (수정)" } });

    expect(Object.keys(post.mock.calls[0][1] as object)).toEqual([
      "compassion",
    ]);
  });

  it("갱신된 프로필을 돌려준다", async () => {
    await expect(updateValueProfile({})).resolves.toBe(PROFILE);
  });
});

describe("recordPractice", () => {
  it("실천 기록을 /api/values/practice 로 그대로 보낸다", async () => {
    const req = {
      valueId: 7,
      durationSec: 300,
      note: "저녁 기도",
      linkedCharacter: "joseph",
      linkedGameSession: "sess-1",
    };
    post.mockResolvedValue({
      data: { id: 1, practicedAt: "2026-08-14T10:00:00Z", ...req },
    } as never);

    const res = await recordPractice(req);

    expect(post.mock.calls[0]).toEqual(["/api/values/practice", req]);
    expect(res.id).toBe(1);
    expect(res.practicedAt).toBe("2026-08-14T10:00:00Z");
  });

  it("선택 필드를 생략하면 valueId 만 전송된다", async () => {
    post.mockResolvedValue({
      data: { id: 2, valueId: 3, practicedAt: "2026-08-14T11:00:00Z" },
    } as never);

    await recordPractice({ valueId: 3 });

    expect(JSON.stringify(post.mock.calls[0][1])).toBe('{"valueId":3}');
  });
});
