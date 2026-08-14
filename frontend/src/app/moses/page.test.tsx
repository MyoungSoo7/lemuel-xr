import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import MosesPage from "./page";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";
import {
  scene1Wilderness,
  scene2Monologues,
  scene3CardResponses,
  scene3Outcomes,
  scene4Reactions,
  scene5Monologue,
  scene6OutroByScene3,
} from "@/lib/content/moses-monologues";
// 위기 문구 픽스처는 정본에서 만든다 — 번호를 여기 적으면 정본이 바뀔 때
// 테스트만 옛 번호를 지키고 초록이 유지된다.
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";

/**
 * Moses 미션 화면 — 씬 1~6 상태 기계.
 *
 * 이 화면의 특이점은 echo(직전 결정의 모놀로그)를 **프론트 정본**
 * (`moses-monologues.ts`) 에서 만든다는 것이다. backend 는 moses.yml 에
 * monologues/outcomes/reactions map 이 없어 responseText 를 null 로 준다.
 * 그래서 여기서 재는 것은 "무슨 글자가 떴다" 가 아니라 **내가 방금 한 결정에 맞는
 * 정본 텍스트가 왔는가** — 즉 결정 → 텍스트 배선이다. 정본 모듈은 모킹하지 않고
 * 실물을 import 해서 대조한다.
 *
 * 백엔드 계약만 vi.mock 으로 끊고, payload 는 moses.yml + ScenePayloadAssembler 가
 * 실제로 만드는 모양(표준 필드는 flatten, yml `extras:` 블록은 payload.extras 로 중첩)을
 * 그대로 흉내낸다.
 */
vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

const mockStart = vi.mocked(startMission);
const mockDecide = vi.mocked(decideMission);
const mockComplete = vi.mocked(completeMission);

const SESSION = "sess-moses-1";

// ── payload 픽스처 (moses.yml 원문) ──────────────────────────────────────────
const SCENE1_PAYLOAD = {
  sceneId: 1,
  title: "광야의 침묵",
  type: "cinematic",
  next: 2,
  extras: { anchor: "자기부정·체념", skippable_after_sec: 3 },
};

const SCENE2_PAYLOAD = {
  sceneId: 2,
  title: "떨기나무 앞에서",
  type: "interaction",
  interaction: "gesture_sequence",
  next: 3,
  extras: {
    anchor: "경외·두려움·거부",
    steps: [
      { id: "approach", label: "다가가기" },
      { id: "remove_shoes", label: "신을 벗기" },
      { id: "look_up", label: "고개 들기" },
    ],
  },
};

const SCENE3_PAYLOAD = {
  sceneId: 3,
  title: "다섯 변명의 카드",
  type: "interaction",
  interaction: "distribute",
  next: 4,
  extras: {
    anchor: "자기방어·고백",
    cards: [
      { id: "who_am_i", label: "제가 누구이기에", scripture: "ex-3:11" },
      {
        id: "who_sent_me",
        label: "이름이 무엇이냐 물으면",
        scripture: "ex-3:13",
      },
      { id: "trust_me", label: "믿지 않으면", scripture: "ex-4:1" },
      { id: "cant_speak", label: "말이 둔하니", scripture: "ex-4:10" },
      {
        id: "send_other",
        label: "보낼 만한 자를 보내소서",
        scripture: "ex-4:13",
      },
    ],
    slots: ["throw", "heart"],
  },
};

const SCENE4_PAYLOAD = {
  sceneId: 4,
  title: "파라오 앞에서",
  type: "interaction",
  interaction: "pick_one",
  next: 5,
  extras: {
    anchor: "공포·동행 인식",
    options: [
      { id: "cast_now", label: "지팡이를 던진다 (즉시)" },
      { id: "hesitate", label: "망설인다 (5초)" },
      { id: "with_aaron", label: "아론을 본다 (동행)" },
    ],
  },
};

const SCENE5_PAYLOAD = {
  sceneId: 5,
  title: "홍해 앞에서",
  type: "interaction",
  interaction: "gesture_sequence",
  next: 6,
  extras: {
    anchor: "책임의 무게·신뢰",
    steps: [{ id: "lift_staff", label: "지팡이를 들고 손을 뻗는다" }],
    one_action_only: true,
  },
};

const SCENE6_PAYLOAD = {
  sceneId: 6,
  title: "회복",
  type: "outro",
  next: null,
  value_prompt: "모세의 광야 40년은 *기다림* 의 시간이었습니다.",
  extras: { anchor: "안식·동반" },
};

const PAYLOADS: Record<number, Record<string, unknown>> = {
  1: SCENE1_PAYLOAD,
  2: SCENE2_PAYLOAD,
  3: SCENE3_PAYLOAD,
  4: SCENE4_PAYLOAD,
  5: SCENE5_PAYLOAD,
  6: SCENE6_PAYLOAD,
};

function sceneResponse(
  id: number,
  responseText: string | null = null,
): JosephStartResponse {
  return {
    sessionId: SESSION,
    userId: "guest-1",
    currentScene: id,
    scenePayload: PAYLOADS[id],
    responseText,
  };
}

/** 기본 엔진 — 어떤 결정이든 다음 씬으로. moses 는 responseText 를 늘 null 로 준다. */
function advanceEngine() {
  mockDecide.mockImplementation(async (_c, _s, sceneId) =>
    sceneResponse(sceneId + 1),
  );
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MosesPage />
    </QueryClientProvider>,
  );
}

/** 특정 씬에서 시작한다 — 씬 단위 테스트의 출발선. */
async function bootAt(id: number, title: string) {
  mockStart.mockResolvedValue(sceneResponse(id));
  renderPage();
  return screen.findByRole("heading", { name: title });
}

/** Scene 3 — 다섯 카드를 같은 슬롯으로 몰아 배정한다. */
async function assignAll(
  user: ReturnType<typeof userEvent.setup>,
  slot: "내려놓기" | "가슴에 품기",
) {
  for (const button of screen.getAllByRole("button", { name: slot })) {
    await user.click(button);
  }
}

/**
 * 정본 문구는 줄바꿈(\n\n)을 품고 있다. testing-library 의 기본 normalizer 는
 * *요소 쪽* 공백만 접기 때문에, 기대 문자열도 같은 규칙으로 접어야 대조가 된다.
 */
const collapse = (s: string) => s.replace(/\s+/g, " ").trim();

beforeEach(() => {
  mockStart.mockReset();
  mockDecide.mockReset();
  mockComplete.mockReset();
  mockComplete.mockResolvedValue(undefined);
});

describe("Moses — 세션 부팅", () => {
  it("세션이 열리기 전에는 진행 중임을 알린다", async () => {
    mockStart.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(await screen.findByText("세션 시작 중...")).toBeInTheDocument();
  });

  it("start 가 실패하면 실패를 말하고 다시 시도할 손잡이를 준다", async () => {
    // 실패 분기가 없던 시절엔 "세션 시작 중..." 에서 영영 멈췄다(2026-08-06 /joseph 제보).
    mockStart.mockRejectedValue(new Error("boom"));
    renderPage();

    expect(
      await screen.findByText("세션을 시작하지 못했습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });

  it("403 이면 만료 안내와 상태 코드를 보여준다", async () => {
    mockStart.mockRejectedValue({ response: { status: 403 } });
    renderPage();

    expect(
      await screen.findByText("세션이 만료됐습니다. 다시 시작해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByText(/오류 코드 403/)).toBeInTheDocument();
  });

  it("다시 시도를 누르면 실제로 재요청해 첫 씬으로 들어간다", async () => {
    const user = userEvent.setup();
    mockStart.mockRejectedValue(new Error("boom"));
    renderPage();

    const retry = await screen.findByRole("button", { name: "다시 시도" });
    mockStart.mockResolvedValue(sceneResponse(1));
    await user.click(retry);

    expect(
      await screen.findByRole("heading", { name: "광야의 침묵" }),
    ).toBeInTheDocument();
  });
});

describe("Moses Scene 1 — 광야의 침묵 (cinematic)", () => {
  it("광야 40년 내레이션 정본을 화면에 편다", async () => {
    await bootAt(1, "광야의 침묵");

    // 정본은 moses-monologues.ts — 페이지가 문장을 따로 갖고 있지 않다.
    expect(screen.getByText(collapse(scene1Wilderness))).toBeInTheDocument();
    expect(screen.getByText(/Moses — Scene 1\/6/)).toBeInTheDocument();
    expect(screen.getByText(/광야의 40년. 양 떼와 침묵/)).toBeInTheDocument();
  });

  it("계속을 누르면 next 결정을 보내고 Scene 2 로 간다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(1, "광야의 침묵");

    await user.click(screen.getByRole("button", { name: "계속 →" }));

    expect(mockDecide).toHaveBeenCalledWith("moses", SESSION, 1, "next");
    expect(
      await screen.findByRole("heading", { name: "떨기나무 앞에서" }),
    ).toBeInTheDocument();
  });

  it("decide 가 실패하면 오류를 보여주고 다시 누를 수 있게 둔다", async () => {
    const user = userEvent.setup();
    mockDecide.mockRejectedValue(new Error("503 Service Unavailable"));
    await bootAt(1, "광야의 침묵");

    await user.click(screen.getByRole("button", { name: "계속 →" }));

    expect(
      await screen.findByText(/오류: 503 Service Unavailable/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "계속 →" })).toBeEnabled();
  });
});

describe("Moses Scene 2 — 떨기나무 (경외/머뭇거림 분기)", () => {
  it("순서대로 몸짓을 다 밟으면 경외로 완료하고 그 모놀로그가 뜬다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(2, "떨기나무 앞에서");

    await user.click(screen.getByRole("button", { name: /다가가기/ }));
    await user.click(screen.getByRole("button", { name: /신을 벗기/ }));
    await user.click(screen.getByRole("button", { name: /고개 들기/ }));

    expect(mockDecide).toHaveBeenCalledWith("moses", SESSION, 2, {
      value: "completed",
      gesture: "reverence",
    });
    expect(
      await screen.findByText(collapse(scene2Monologues.reverence)),
    ).toBeInTheDocument();
  });

  it("밟은 몸짓은 ✓ 로 남고 다시 누를 수 없다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(2, "떨기나무 앞에서");

    const approach = screen.getByRole("button", { name: /다가가기/ });
    await user.click(approach);

    expect(approach).toBeDisabled();
    expect(approach).toHaveTextContent("✓");
  });

  it("언제든 얼굴을 가릴 수 있고, 그 선택은 실패가 아닌 머뭇거림으로 읽힌다", async () => {
    // R 가드 — 거부·머뭇거림(출 3:6)은 오답 처리하지 않는다. 번호 몸짓을 밟지 않고
    // 바로 눌러도 씬이 완료돼야 한다.
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(2, "떨기나무 앞에서");

    await user.click(screen.getByRole("button", { name: /얼굴을 가린다/ }));

    expect(mockDecide).toHaveBeenCalledWith("moses", SESSION, 2, {
      value: "completed",
      gesture: "hesitation",
    });
    expect(
      await screen.findByText(collapse(scene2Monologues.hesitation)),
    ).toBeInTheDocument();
  });

  it("steps 가 비어 있으면 기본 3단계 몸짓을 대신 그린다", async () => {
    // payload 가 비어도 화면이 빈 채로 멈추지 않아야 한다.
    mockStart.mockResolvedValue({
      ...sceneResponse(2),
      scenePayload: { ...SCENE2_PAYLOAD, extras: {} },
    });
    renderPage();

    expect(
      await screen.findByRole("button", { name: /다가가기/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /신을 벗기/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /고개 들기/ }),
    ).toBeInTheDocument();
  });
});

describe("Moses Scene 3 — 다섯 변명의 카드 (distribute)", () => {
  it("전부 배정하기 전에는 제출할 수 없고 무엇이 남았는지 말해준다", async () => {
    const user = userEvent.setup();
    await bootAt(3, "다섯 변명의 카드");

    const submit = screen.getByRole("button", {
      name: "모든 변명을 배정하세요",
    });
    expect(submit).toBeDisabled();

    // 다섯 장 중 하나만 배정 — 여전히 잠겨 있어야 한다.
    await user.click(screen.getAllByRole("button", { name: "내려놓기" })[0]);
    expect(
      screen.getByRole("button", { name: "모든 변명을 배정하세요" }),
    ).toBeDisabled();
  });

  it("다섯 변명을 모두 내려놓으면 all_throw 로 제출되고 카드별 본문이 이어진다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(3, "다섯 변명의 카드");

    await assignAll(user, "내려놓기");
    await user.click(screen.getByRole("button", { name: "결정 →" }));

    expect(mockDecide).toHaveBeenCalledWith("moses", SESSION, 3, {
      priority: "all_throw",
      assignments: {
        who_am_i: "throw",
        who_sent_me: "throw",
        trust_me: "throw",
        cant_speak: "throw",
        send_other: "throw",
      },
    });
    const echo = await screen.findByText(
      new RegExp(scene3Outcomes.all_throw.slice(0, 20)),
    );
    // 내려놓은 카드마다 떨기나무 본문 응답(§3.4)이 붙는다.
    expect(echo).toHaveTextContent(
      scene3CardResponses.who_am_i.slice(0, 20).trim(),
    );
    expect(echo).toHaveTextContent(
      scene3CardResponses.send_other.slice(0, 20).trim(),
    );
  });

  it("모두 품으면 all_heart 로 제출되고 대표 응답 하나만 붙는다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(3, "다섯 변명의 카드");

    await assignAll(user, "가슴에 품기");
    await user.click(screen.getByRole("button", { name: "결정 →" }));

    expect(mockDecide).toHaveBeenCalledWith(
      "moses",
      SESSION,
      3,
      expect.objectContaining({ priority: "all_heart" }),
    );
    // 자기방어를 실패로 읽지 않는 정본 문구가 그대로 나와야 한다.
    expect(
      await screen.findByText(
        new RegExp(scene3Outcomes.all_heart.slice(0, 20)),
      ),
    ).toBeInTheDocument();
  });

  it("섞어 배정하면 mixed 로 제출된다 — 마지막 선택이 이긴다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(3, "다섯 변명의 카드");

    await assignAll(user, "내려놓기");
    // 첫 카드만 마음을 바꿔 품는다.
    await user.click(screen.getAllByRole("button", { name: "가슴에 품기" })[0]);
    await user.click(screen.getByRole("button", { name: "결정 →" }));

    expect(mockDecide).toHaveBeenCalledWith(
      "moses",
      SESSION,
      3,
      expect.objectContaining({
        priority: "mixed",
        assignments: expect.objectContaining({
          who_am_i: "heart",
          send_other: "throw",
        }),
      }),
    );
  });

  it("카드에 붙은 성경 참조를 함께 보여준다", async () => {
    await bootAt(3, "다섯 변명의 카드");

    expect(screen.getByText("ex-4:10")).toBeInTheDocument();
    expect(screen.getByText("말이 둔하니")).toBeInTheDocument();
  });

  it("cards 가 비어 있어도 화면이 비지 않는다", async () => {
    mockStart.mockResolvedValue({
      ...sceneResponse(3),
      scenePayload: { ...SCENE3_PAYLOAD, extras: {} },
    });
    renderPage();

    expect(await screen.findByText("변명")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "모든 변명을 배정하세요" }),
    ).toBeDisabled();
  });
});

describe("Moses Scene 4 — 파라오 앞 (pick_one)", () => {
  it("세 선택지가 모두 열려 있고, 고른 것에 맞는 반응이 돌아온다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(4, "파라오 앞에서");

    expect(
      screen.getByRole("button", { name: "망설인다 (5초)" }),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "지팡이를 던진다 (즉시)" }),
    );

    expect(mockDecide).toHaveBeenCalledWith("moses", SESSION, 4, "cast_now");
    expect(
      await screen.findByText(collapse(scene4Reactions.cast_now)),
    ).toBeInTheDocument();
  });

  it("망설임도 동행 요청도 '덜 믿음' 으로 처리하지 않는다", async () => {
    // R 가드 — hesitate / with_aaron 에 책망 톤이 붙지 않는지 정본으로 확인한다.
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(4, "파라오 앞에서");

    await user.click(
      screen.getByRole("button", { name: "아론을 본다 (동행)" }),
    );

    expect(
      await screen.findByText(collapse(scene4Reactions.with_aaron)),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/동행은 나약함의 증거가 아니라/),
    ).toBeInTheDocument();
  });

  it("backend 가 responseText 를 주면 프론트 fallback 보다 그것을 쓴다", async () => {
    // moses 는 지금 항상 null 이지만, 배선은 backend 우선이어야 한다.
    const user = userEvent.setup();
    mockDecide.mockResolvedValue(sceneResponse(5, "서버가 보낸 응답 텍스트"));
    await bootAt(4, "파라오 앞에서");

    await user.click(
      screen.getByRole("button", { name: "지팡이를 던진다 (즉시)" }),
    );

    expect(
      await screen.findByText("서버가 보낸 응답 텍스트"),
    ).toBeInTheDocument();
    expect(screen.queryByText(collapse(scene4Reactions.cast_now))).toBeNull();
  });
});

describe("Moses — yml 과 프론트 정본이 어긋날 때", () => {
  it("[버그] 프론트가 모르는 선택지 id 면 직전 씬의 echo 가 그대로 남는다", async () => {
    // echo 는 setEcho(null) 없이 `if (text)` 로만 갱신된다(page.tsx:75).
    // moses.yml 의 옵션 id 를 바꾸면(= scene4Reactions 에 없는 id) 새 echo 가 없고,
    // 화면에는 *한 씬 전의 독백* 이 남아 지금 결정에 대한 응답인 척한다.
    // 현재 동작을 고정해 두고 소스는 건드리지 않는다 (보고서에 기재).
    const user = userEvent.setup();
    await bootAt(3, "다섯 변명의 카드");

    // Scene 4 의 옵션 id 가 정본 map(scene4Reactions) 에 없는 값으로 바뀐 상황.
    const driftedScene4 = {
      ...SCENE4_PAYLOAD,
      extras: {
        ...SCENE4_PAYLOAD.extras,
        options: [{ id: "throw_immediately", label: "지팡이를 던진다 (즉시)" }],
      },
    };
    mockDecide.mockResolvedValueOnce({
      ...sceneResponse(4),
      scenePayload: driftedScene4,
    });

    await assignAll(user, "내려놓기");
    await user.click(screen.getByRole("button", { name: "결정 →" }));
    await screen.findByRole("heading", { name: "파라오 앞에서" });

    mockDecide.mockResolvedValueOnce(sceneResponse(5));
    await user.click(
      screen.getByRole("button", { name: "지팡이를 던진다 (즉시)" }),
    );
    await screen.findByRole("heading", { name: "홍해 앞에서" });

    // Scene 3 의 echo 가 Scene 5 화면에 그대로 살아 있다.
    expect(
      screen.getByText(new RegExp(scene3Outcomes.all_throw.slice(0, 20))),
    ).toBeInTheDocument();
  });
});

describe("Moses Scene 5 — 홍해 앞 (단일 행동)", () => {
  it("한 동작으로 씬이 끝나고 신뢰의 모놀로그가 뜬다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(5, "홍해 앞에서");

    await user.click(
      screen.getByRole("button", { name: /지팡이를 들고 손을 뻗는다/ }),
    );

    expect(mockDecide).toHaveBeenCalledWith("moses", SESSION, 5, {
      value: "lift_staff",
    });
    expect(
      await screen.findByText(collapse(scene5Monologue.lift_staff)),
    ).toBeInTheDocument();
  });

  it("steps 가 비어 있으면 단일 '행동하기' 버튼으로 대체한다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    mockStart.mockResolvedValue({
      ...sceneResponse(5),
      scenePayload: { ...SCENE5_PAYLOAD, extras: {} },
    });
    renderPage();

    await user.click(await screen.findByRole("button", { name: /행동하기/ }));

    expect(mockDecide).toHaveBeenCalledWith("moses", SESSION, 5, {
      value: "lift_staff",
    });
  });
});

describe("Moses Scene 6 — 회복 (outro)", () => {
  it("Scene 3 을 거치지 않고 들어오면 mixed 결말로 폴백한다", async () => {
    await bootAt(6, "회복");

    expect(
      await screen.findByText(collapse(scene6OutroByScene3.mixed)),
    ).toBeInTheDocument();
    // R5 — AI 보조 고지는 항상 노출.
    expect(screen.getByText(/AI 보조 — 본문은 성경 참조/)).toBeInTheDocument();
  });

  it("미션 완료를 누르면 completed 로 세션을 닫는다", async () => {
    const user = userEvent.setup();
    await bootAt(6, "회복");

    await user.click(screen.getByRole("button", { name: "미션 완료" }));

    expect(mockComplete).toHaveBeenCalledWith("moses", SESSION, "completed");
  });

  it("홈과 Joseph 미션으로 나갈 길을 둔다", async () => {
    await bootAt(6, "회복");

    expect(screen.getByRole("link", { name: "← 홈" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.getByRole("link", { name: "Joseph 미션 →" })).toHaveAttribute(
      "href",
      "/joseph",
    );
  });

  /*
    moses.yml Scene 6 은 `crisis_reminder` 를 선언하고 백엔드
    `CrisisTokenResolver` 가 `{{crisis_resources.default}}` 를 정본 번호로 치환해
    내려보낸다. 2026-08-14 까지 이 화면은 그 값을 읽지 않았다 — 치환까지 끝난
    안내가 payload 안에서 그대로 버려졌다.

    CI 는 이걸 못 잡는다. `check_frontend_hotline.py` 는 "번호를 하드코딩했나" 만
    보고, 아무것도 안 그리는 화면은 하드코딩이 아니라서 초록이다. 화면에 뜨는지는
    테스트만 잰다.
  */
  it("payload 의 위기 안내를 결말에 낸다", async () => {
    // 문구는 백엔드가 만든다. 화면은 그대로 실어 나르기만 해야 한다.
    const reminder = `지금 이 순간이 무겁다면, ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}.`;
    mockStart.mockResolvedValue({
      sessionId: SESSION,
      userId: "guest-1",
      currentScene: 6,
      scenePayload: { ...SCENE6_PAYLOAD, crisis_reminder: reminder },
      responseText: null,
    });
    renderPage();

    expect(await screen.findByRole("note")).toHaveTextContent(reminder);
  });

  it("안내가 없으면 빈 상자를 만들지 않는다", async () => {
    await bootAt(6, "회복");

    expect(screen.queryByRole("note")).toBeNull();
  });
});

describe("Moses — 끝까지 걸어가기", () => {
  it("Scene 1 → 6 을 걸으면 Scene 3 의 선택이 결말 톤을 바꾼다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(1, "광야의 침묵");

    // 1 — cinematic
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // 2 — 경외 몸짓 3단계
    await screen.findByRole("heading", { name: "떨기나무 앞에서" });
    await user.click(screen.getByRole("button", { name: /다가가기/ }));
    await user.click(screen.getByRole("button", { name: /신을 벗기/ }));
    await user.click(screen.getByRole("button", { name: /고개 들기/ }));

    // 3 — 다섯 변명 모두 내려놓기(all_throw)
    await screen.findByRole("heading", { name: "다섯 변명의 카드" });
    await assignAll(user, "내려놓기");
    await user.click(screen.getByRole("button", { name: "결정 →" }));

    // 4 — 파라오 앞
    await screen.findByRole("heading", { name: "파라오 앞에서" });
    await user.click(
      screen.getByRole("button", { name: "지팡이를 던진다 (즉시)" }),
    );

    // 5 — 홍해
    await screen.findByRole("heading", { name: "홍해 앞에서" });
    await user.click(
      screen.getByRole("button", { name: /지팡이를 들고 손을 뻗는다/ }),
    );

    // 6 — 결말은 Scene 3 패턴(all_throw)을 따라간다
    await screen.findByRole("heading", { name: "회복" });
    expect(
      screen.getByText(collapse(scene6OutroByScene3.all_throw)),
    ).toBeInTheDocument();
    expect(screen.queryByText(collapse(scene6OutroByScene3.mixed))).toBeNull();

    // 진행 기록에 지나온 씬 제목이 쌓인다.
    await user.click(screen.getByText("진행 기록"));
    expect(screen.getByText(/다섯 변명의 카드/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(mockComplete).toHaveBeenCalledWith("moses", SESSION, "completed");
  });

  it("Scene 3 에서 모두 품고 가면 결말도 그 톤으로 간다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootAt(3, "다섯 변명의 카드");

    await assignAll(user, "가슴에 품기");
    await user.click(screen.getByRole("button", { name: "결정 →" }));
    await screen.findByRole("heading", { name: "파라오 앞에서" });
    await user.click(screen.getByRole("button", { name: "망설인다 (5초)" }));
    await screen.findByRole("heading", { name: "홍해 앞에서" });
    await user.click(
      screen.getByRole("button", { name: /지팡이를 들고 손을 뻗는다/ }),
    );

    await screen.findByRole("heading", { name: "회복" });
    expect(
      screen.getByText(collapse(scene6OutroByScene3.all_heart)),
    ).toBeInTheDocument();
  });
});

describe("Moses — R4 동의 게이트 배선", () => {
  it("[구멍] payload 에 trigger_warning 이 와도 이 화면은 동의 카드를 띄우지 않는다", async () => {
    // moses.yml 에는 현재 trigger_warning 이 없어서 CI 의
    // check_frontend_trigger_warning.py 는 moses 를 아예 검사하지 않는다.
    // 그래서 이 화면에는 R4 배선이 *한 줄도* 없다 — 나중에 yml 에 경고를 넣는
    // 사람은 "넣으면 화면이 띄운다" 는 규약을 믿을 텐데, 여기서는 조용히 무시된다.
    // 현재 동작을 고정해 두고 소스는 건드리지 않는다 (보고서에 기재).
    mockStart.mockResolvedValue({
      ...sceneResponse(4),
      scenePayload: {
        ...SCENE4_PAYLOAD,
        trigger_warning: {
          level: "medium",
          content: ["violence"],
          consent_card_ko:
            "다음 장면에는 폭력 묘사가 있습니다. 계속하시겠어요?",
          skip_alternative_scene_id: 5,
        },
      },
    });
    renderPage();

    // 경고 카드도, 건너뛸 길도 없이 본문이 바로 열린다.
    await screen.findByRole("heading", { name: "파라오 앞에서" });
    expect(screen.queryByText(/다음 장면에는 폭력 묘사가 있습니다/)).toBeNull();
    expect(screen.queryByRole("button", { name: /건너뛰기/ })).toBeNull();
    expect(
      screen.getByRole("button", { name: "지팡이를 던진다 (즉시)" }),
    ).toBeInTheDocument();
  });
});
