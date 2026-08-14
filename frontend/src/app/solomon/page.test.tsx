import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import SolomonPage from "./page";
import {
  startMission,
  decideMission,
  completeMission,
  type JosephStartResponse,
} from "@/lib/api/game";

/**
 * Solomon 미션 화면 — 씬 1~5 상태 기계.
 *
 * 여기서 재는 것은 "컴포넌트가 죽지 않는다" 가 아니라 **사용자가 무엇을 보고 무엇을
 * 할 수 있는가** 다. 특히 세 축:
 *
 *  1. 끝까지 걸어가는 경로 — Scene 1 → 5 → complete. 중간에 화면이 비면 안 된다.
 *  2. 백엔드가 실패했을 때 사용자가 보는 것 — 빈 화면·먹통 금지.
 *  3. **R4 동의 게이트** — trigger_warning 이 있는 씬(3·4)에서 동의 카드가 *실제로 렌더되고*,
 *     동의 전에는 본문·선택지가 가려지는가. CI 의 check_frontend_trigger_warning.py 는
 *     "화면이 payload.trigger_warning 을 읽는가" 까지만 보고 카드가 그려지는지는 안 본다.
 *     그 구멍이 여기다.
 *
 * 백엔드 계약은 vi.mock 으로 끊고, payload 는 solomon.yml + ScenePayloadAssembler 가
 * 실제로 만드는 모양(표준 필드 flatten + yml `extras:` 블록은 payload.extras 로 중첩,
 * trigger_warning 은 payload 최상위)을 그대로 흉내낸다.
 */
vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

const mockStart = vi.mocked(startMission);
const mockDecide = vi.mocked(decideMission);
const mockComplete = vi.mocked(completeMission);

const SESSION = "sess-solomon-1";

// ── payload 픽스처 (solomon.yml 원문 축약 — 문구는 정본 그대로) ────────────────
const SCENE1_PAYLOAD = {
  sceneId: 1,
  title: "기브온의 일천번제",
  type: "interaction",
  interaction: "grab_and_place",
  next: 2,
  extras: {
    anchor: "부담·부족감",
    script: [
      {
        at_sec: 0,
        beat: "narration",
        text: "다윗의 아들, 젊은 왕이 기브온 산당에 올라 일천 번제를 드렸다.",
        ref: "1kgs-3:4",
      },
      {
        at_sec: 12,
        beat: "inner_voice",
        text: "아버지의 자리는 크고, 나는 작다. 이 왕관은 왜 이렇게 무거운가.",
      },
      // text 없는 비트 — 렌더 대상이 아니다 (filter 대상).
      { at_sec: 20, beat: "silence" },
    ],
    offering: { object_id: "offering_bundle", core: false },
  },
};

const SCENE2_PAYLOAD = {
  sceneId: 2,
  title: "꿈에 응답하신 하나님",
  type: "cinematic",
  next: 3,
  extras: {
    script: [
      {
        at_sec: 0,
        beat: "narration",
        text: "기브온에서 밤에 여호와께서 꿈에 솔로몬에게 나타나셨다.",
        ref: "1kgs-3:5",
      },
    ],
  },
};

const SCENE3_CONSENT_KO = [
  "다음 장면은 성경에서 가장 유명한 재판(열왕기상 3:16~28)을 다룹니다.",
  "· 갓난아기를 잃은 어머니의 이야기가 대사로 언급됩니다. (시각적 묘사는 없습니다)",
  "건너뛰어도 이야기는 온전히 이어집니다.",
  "계속하시겠어요?",
  "[계속한다]  [건너뛰기 — 재판 결과 요약 자막 후 다음 장면으로]",
  "음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]",
  "지금 힘드시면: 109 (자살예방 상담전화)",
].join("\n");

const SCENE3_PAYLOAD = {
  sceneId: 3,
  title: "두 여인 재판",
  type: "interaction",
  interaction: "pick_one",
  next: 4,
  extras: {
    script: [
      {
        at_sec: 0,
        beat: "narration",
        text: "그 때에 두 여자가 왕에게 와서 그 앞에 섰더라",
        ref: "1kgs-3:16",
      },
      {
        at_sec: 108,
        beat: "awe",
        text: "온 이스라엘이 왕을 두려워하였으니 이는 하나님의 지혜가 그의 속에 있어 판결함을 봄이더라",
        ref: "1kgs-3:28",
      },
    ],
    options: [
      { id: "first_woman", label: "산 아이를 첫째 여인에게 주라" },
      { id: "second_woman", label: "산 아이를 둘째 여인에게 주라" },
      { id: "sword_test", label: "칼을 가져오라" },
    ],
    skip_summary_caption: {
      text: "왕은 칼을 가져오라 명하여 두 여인의 마음을 드러냈고, 산 아기는 참 어머니의 품으로 돌아갔다.",
      ref: "1kgs-3:27-28",
    },
    intensity_toggle: ["captions_only", "mild", "default"],
  },
  trigger_warning: {
    level: "medium",
    content: ["infant_loss", "bereavement"],
    consent_card_id: "solomon_scene3_infant_loss_warning",
    consent_card_ko: SCENE3_CONSENT_KO,
    skip_alternative_scene_id: 4,
  },
};

const RECONSIDER_TEXT =
  "왕이 첫째 여인에게 아이를 주려 하자, 둘째 여인이 부르짖었다 — '아니라, 산 것은 내 아들이라.'";

const SCENE4_PAYLOAD = {
  sceneId: 4,
  title: '영광의 정점에서 "헛되다"',
  type: "interaction",
  interaction: "pick_one",
  next: 5,
  extras: {
    script: [
      {
        at_sec: 55,
        beat: "caption",
        text: "헛되고 헛되며 헛되고 헛되니 모든 것이 헛되도다",
        ref: "eccl-1:2",
      },
    ],
    options: [
      { id: "emptiness", label: "다 이루었는데, 비어 있습니다" },
      {
        id: "restlessness",
        label: "멈추면 무너질 것 같아, 계속 쌓기만 합니다",
      },
      {
        id: "loss_of_meaning",
        label: "이 모든 것이 무슨 의미인지 모르겠습니다",
      },
    ],
    decision_key: "hevel_label",
    optional_selection: true,
    crisis_reminder:
      "다 가진 사람에게도 공허는 옵니다. 공허를 느끼는 것은 잘못이 아닙니다. 지금 힘드시면 109",
  },
  trigger_warning: {
    level: "low_medium",
    content: ["emptiness"],
    consent_card_ko: [
      '다음 장면은 모든 것을 가진 왕이 느낀 공허 — "헛되다"(전도서) — 를 다룹니다.',
      "계속하시겠어요?",
      "[계속한다]  [건너뛰기 — 바로 마지막 장면(Scene 5)으로 이동]",
    ].join("\n"),
    skip_alternative_scene_id: 5,
  },
};

const REORIENT_BALANCED =
  "다 가진 왕도 비어 있었다. 전도서는 그 공허를 꾸짖지 않고 먼저 끝까지 말하게 한다.";
const REORIENT_DEFAULT =
  "가장 많이 가진 왕이 마지막에 남긴 말은 하나뿐이었다 — 하나님을 경외하라.";

const SCENE5_PAYLOAD = {
  sceneId: 5,
  title: "재정향 — 하나님을 경외하라",
  type: "outro",
  next: null,
  value_prompt: "이 시간을 기록하시겠어요?",
  extras: {
    script: [
      {
        at_sec: 10,
        beat: "conclusion",
        text: "일의 결국을 다 들었으니 하나님을 경외하고 그의 명령들을 지킬지어다",
        ref: "eccl-12:13",
      },
    ],
    laydown: {
      objects: [
        {
          id: "crown",
          label: "왕관",
          meaning: "Scene 1 의 부담",
          appears: "always",
        },
        {
          id: "sword",
          label: "칼",
          meaning: "Scene 3 판결의 무게",
          appears: "scene3_completed",
        },
        {
          id: "verdict_scroll",
          label: "판결 두루마리",
          meaning: "칼 시각 트리거 회피",
          appears: "scene3_skipped",
        },
        {
          id: "treasure",
          label: "보물",
          meaning: "Scene 4 의 허무",
          appears: "scene4_visited",
        },
      ],
      required: false,
    },
    reorientation_texts: {
      emptiness: {
        strong: "부귀와 영광은 하나님이 덤으로 주신 것이었다 (왕상 3:13).",
        balanced: REORIENT_BALANCED,
        soft: "3000년 전, 가장 많이 가진 사람도 '헛되다'고 적었다.",
      },
      default: REORIENT_DEFAULT,
    },
    crisis_reminder: "109 (자살예방 상담전화).",
  },
};

const PAYLOADS: Record<number, Record<string, unknown>> = {
  1: SCENE1_PAYLOAD,
  2: SCENE2_PAYLOAD,
  3: SCENE3_PAYLOAD,
  4: SCENE4_PAYLOAD,
  5: SCENE5_PAYLOAD,
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

/** 기본 엔진 — 어떤 결정이든 다음 씬으로 넘긴다 (SceneConvergenceResolver 미개입 경로). */
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
      <SolomonPage />
    </QueryClientProvider>,
  );
}

/** Scene 1 이 떠 있는 상태까지 진행 — 대부분의 테스트의 출발선. */
async function bootToScene1() {
  mockStart.mockResolvedValue(sceneResponse(1));
  renderPage();
  return screen.findByRole("heading", { name: "기브온의 일천번제" });
}

/** 동의 게이트가 있는 씬(3·4)에서 카드를 통과한다 — 게이트 자체를 재는 테스트가 아닐 때 쓴다. */
async function consent(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole("button", { name: "계속한다" }));
}

beforeEach(() => {
  mockStart.mockReset();
  mockDecide.mockReset();
  mockComplete.mockReset();
  mockComplete.mockResolvedValue(undefined);
});

describe("Solomon — 세션 부팅", () => {
  it("세션이 열리기 전에는 진행 중임을 알린다", async () => {
    // 빈 화면이 아니라 상태를 말해야 한다. 영원히 pending 인 start 로 그 순간을 붙잡는다.
    mockStart.mockReturnValue(new Promise(() => {}));
    renderPage();
    expect(await screen.findByText("세션 시작 중...")).toBeInTheDocument();
  });

  it("start 가 실패하면 실패했다고 말하고 다시 시도할 손잡이를 준다", async () => {
    // 2026-08-06 회귀: 실패 시 화면이 "세션 시작 중..." 에서 영영 멈춰 있었다.
    mockStart.mockRejectedValue(new Error("boom"));
    renderPage();

    expect(
      await screen.findByText("세션을 시작하지 못했습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "다시 시도" }),
    ).toBeInTheDocument();
  });

  it("401 이면 세션 만료로 안내하고 상태 코드를 보여준다", async () => {
    mockStart.mockRejectedValue({ response: { status: 401 } });
    renderPage();

    expect(
      await screen.findByText("세션이 만료됐습니다. 다시 시작해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByText(/오류 코드 401/)).toBeInTheDocument();
  });

  it("다시 시도를 누르면 실제로 재요청하고 첫 씬으로 들어간다", async () => {
    const user = userEvent.setup();
    mockStart.mockRejectedValue(new Error("boom"));
    renderPage();

    const retry = await screen.findByRole("button", { name: "다시 시도" });
    mockStart.mockResolvedValue(sceneResponse(1));
    await user.click(retry);

    expect(
      await screen.findByRole("heading", { name: "기브온의 일천번제" }),
    ).toBeInTheDocument();
  });
});

describe("Solomon Scene 1 — 기브온의 일천번제 (grab_and_place)", () => {
  it("대사를 payload 에서 렌더한다 — 프론트에 사본을 두지 않는다", async () => {
    await bootToScene1();

    expect(
      screen.getByText(/다윗의 아들, 젊은 왕이 기브온 산당에 올라/),
    ).toBeInTheDocument();
    // ref 는 비트 옆 각주로.
    expect(screen.getByText("(1kgs-3:4)")).toBeInTheDocument();
    expect(screen.getByText(/Solomon — Scene 1\/5/)).toBeInTheDocument();
  });

  it("제물을 올리면 offering_placed 를 실어 다음 씬으로 간다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootToScene1();

    await user.click(screen.getByRole("button", { name: "제물을 올린다 →" }));

    expect(mockDecide).toHaveBeenCalledWith("solomon", SESSION, 1, {
      value: "next",
      offering_placed: true,
    });
    expect(
      await screen.findByRole("heading", { name: "꿈에 응답하신 하나님" }),
    ).toBeInTheDocument();
  });

  it("제물을 올리지 않아도 똑같이 다음 씬으로 간다 — 비핵심 연출(core:false)", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootToScene1();

    await user.click(
      screen.getByRole("button", { name: "올리지 않고 다음으로 →" }),
    );

    expect(mockDecide).toHaveBeenCalledWith("solomon", SESSION, 1, {
      value: "next",
    });
    expect(
      await screen.findByRole("heading", { name: "꿈에 응답하신 하나님" }),
    ).toBeInTheDocument();
  });

  it("decide 가 실패하면 오류 문구를 보여준다 — 조용히 먹통이 되지 않는다", async () => {
    const user = userEvent.setup();
    mockDecide.mockRejectedValue(new Error("502 Bad Gateway"));
    await bootToScene1();

    await user.click(screen.getByRole("button", { name: "제물을 올린다 →" }));

    expect(
      await screen.findByText(/오류: 502 Bad Gateway/),
    ).toBeInTheDocument();
    // 실패해도 씬은 그대로 남아 다시 시도할 수 있어야 한다.
    expect(
      screen.getByRole("button", { name: "제물을 올린다 →" }),
    ).toBeEnabled();
  });
});

describe("Solomon Scene 2 — 꿈의 응답 (cinematic)", () => {
  it("계속을 누르면 next 결정을 보낸다", async () => {
    const user = userEvent.setup();
    mockStart.mockResolvedValue(sceneResponse(2));
    advanceEngine();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속 →" }));

    // cinematic 은 문자열 결정을 그대로 보낸다 (api 계층이 {value} 로 감싼다).
    expect(mockDecide).toHaveBeenCalledWith("solomon", SESSION, 2, "next");
  });
});

describe("Solomon Scene 3 — R4 동의 게이트 (두 여인 재판)", () => {
  beforeEach(() => {
    mockStart.mockResolvedValue(sceneResponse(3));
  });

  it("동의 전에는 본문도 선택지도 보이지 않고 동의 카드만 보인다", async () => {
    renderPage();

    // 카드 본문은 payload 정본(consent_card_ko)에서 온다.
    expect(
      await screen.findByText(/다음 장면은 성경에서 가장 유명한 재판/),
    ).toBeInTheDocument();
    expect(screen.getByText("잠깐 — 다음 장면 안내")).toBeInTheDocument();

    // 씬 본문(script beat)과 판결 선택지는 동의 전에 가려져 있어야 한다.
    expect(screen.queryByText(/그 때에 두 여자가 왕에게 와서/)).toBeNull();
    expect(
      screen.queryByRole("button", { name: "산 아이를 첫째 여인에게 주라" }),
    ).toBeNull();
    expect(screen.queryByRole("button", { name: "칼을 가져오라" })).toBeNull();
  });

  it("카드 원문의 UI 지시 줄은 산문에서 걷어내고 실제 버튼으로 준다", async () => {
    renderPage();
    await screen.findByText(/다음 장면은 성경에서 가장 유명한 재판/);

    // "[계속한다] [건너뛰기 …]" · "음성/자막 강도: …" 는 문장이 아니라 조작부다.
    expect(screen.queryByText(/\[계속한다\]/)).toBeNull();
    expect(screen.queryByText(/음성\/자막 강도: \[/)).toBeNull();
    expect(
      screen.getByRole("button", { name: "계속한다" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "건너뛰기 →" }),
    ).toBeInTheDocument();
    // 위기 자원 줄은 산문에 남아야 한다 (서버가 치환한 정본).
    expect(screen.getByText(/지금 힘드시면: 109/)).toBeInTheDocument();
  });

  it("동의하면 본문과 선택지가 열린다", async () => {
    const user = userEvent.setup();
    renderPage();
    await consent(user);

    expect(
      screen.getByText(/그 때에 두 여자가 왕에게 와서/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "칼을 가져오라" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("잠깐 — 다음 장면 안내")).toBeNull();
  });

  it("건너뛰기는 skip 결정을 보내고 대체 요약 자막을 남긴다", async () => {
    const user = userEvent.setup();
    mockDecide.mockResolvedValue(sceneResponse(4));
    renderPage();

    await user.click(await screen.findByRole("button", { name: "건너뛰기 →" }));

    expect(mockDecide).toHaveBeenCalledWith("solomon", SESSION, 3, {
      value: "skip",
    });
    // 건너뛴 사람도 이야기의 결말을 안다 — 비묘사 요약 자막.
    expect(
      await screen.findByText(/왕은 칼을 가져오라 명하여 두 여인의 마음을/),
    ).toBeInTheDocument();
  });

  it("자막만을 고르면 나레이션 오디오 버튼을 노출하지 않는다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "자막만" }));
    await consent(user);

    expect(screen.getByText(/그 때에 두 여자가/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /듣기/ })).toBeNull();
  });

  it("기본 강도에서는 나레이션 오디오 버튼이 함께 나온다", async () => {
    const user = userEvent.setup();
    renderPage();
    await consent(user);

    expect(
      screen.getByRole("button", { name: /듣기 — 음성으로 듣기/ }),
    ).toBeInTheDocument();
  });

  it("[버그] 강도 '약'은 선택해도 어디에도 반영되지 않는다", async () => {
    // onIntensity 는 captions_only 여부만 본다(page.tsx:253). 그래서 '약'을 눌러도
    // 상태는 '기본' 과 구별되지 않고, 하이라이트조차 '기본' 에 남는다.
    // 정서 부담을 낮추려고 강도를 내린 사용자에게 *아무 일도 일어나지 않는다*.
    // 현재 동작을 고정해 두고 소스는 건드리지 않는다 (보고서에 기재).
    const user = userEvent.setup();
    renderPage();

    const mild = await screen.findByRole("button", { name: "약" });
    await user.click(mild);

    const selected = "bg-[var(--color-primary)]/15";
    expect(mild.className).not.toContain(selected);
    expect(screen.getByRole("button", { name: "기본" }).className).toContain(
      selected,
    );
  });
});

describe("Solomon Scene 3 — converges_to (엔진 집행 재고)", () => {
  beforeEach(() => {
    mockStart.mockResolvedValue(sceneResponse(3));
  });

  it("엔진이 같은 씬을 돌려주면 재고 텍스트를 띄우고 씬에 머문다", async () => {
    const user = userEvent.setup();
    // SceneConvergenceResolver: first_woman → 같은 씬(3) + 재고 텍스트.
    mockDecide.mockResolvedValue(sceneResponse(3, RECONSIDER_TEXT));
    renderPage();
    await consent(user);

    await user.click(
      screen.getByRole("button", { name: "산 아이를 첫째 여인에게 주라" }),
    );

    expect(await screen.findByText(RECONSIDER_TEXT)).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "두 여인 재판" }),
    ).toBeInTheDocument();
  });

  it("재고로 되돌아와도 동의 카드가 다시 뜨지 않는다", async () => {
    // 씬이 안 바뀌었는데 동의를 초기화하면 사용자는 같은 경고를 씬 도중에 다시 읽는다.
    const user = userEvent.setup();
    mockDecide.mockResolvedValue(sceneResponse(3, RECONSIDER_TEXT));
    renderPage();
    await consent(user);

    await user.click(
      screen.getByRole("button", { name: "산 아이를 둘째 여인에게 주라" }),
    );

    await screen.findByText(RECONSIDER_TEXT);
    expect(screen.queryByText("잠깐 — 다음 장면 안내")).toBeNull();
    expect(
      screen.getByRole("button", { name: "칼을 가져오라" }),
    ).toBeInTheDocument();
  });

  it("재고 후 수렴 선택을 하면 다음 씬으로 넘어가고 재고 텍스트가 사라진다", async () => {
    const user = userEvent.setup();
    mockDecide.mockResolvedValueOnce(sceneResponse(3, RECONSIDER_TEXT));
    renderPage();
    await consent(user);
    await user.click(
      screen.getByRole("button", { name: "산 아이를 첫째 여인에게 주라" }),
    );
    await screen.findByText(RECONSIDER_TEXT);

    mockDecide.mockResolvedValueOnce(sceneResponse(4));
    await user.click(screen.getByRole("button", { name: "칼을 가져오라" }));

    expect(
      await screen.findByRole("heading", { name: '영광의 정점에서 "헛되다"' }),
    ).toBeInTheDocument();
    expect(screen.queryByText(RECONSIDER_TEXT)).toBeNull();
  });
});

describe("Solomon Scene 4 — 헛되다 (동의 게이트 + 라벨링은 초대)", () => {
  beforeEach(() => {
    mockStart.mockResolvedValue(sceneResponse(4));
  });

  it("씬 4 도 자기 몫의 동의 카드를 갖는다", async () => {
    renderPage();

    expect(
      await screen.findByText(/다음 장면은 모든 것을 가진 왕이 느낀 공허/),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "다 이루었는데, 비어 있습니다" }),
    ).toBeNull();
  });

  it("intensity_toggle 이 없는 씬에서는 강도 조절부를 그리지 않는다", async () => {
    renderPage();
    await screen.findByText("잠깐 — 다음 장면 안내");

    expect(screen.queryByText("음성/자막 강도")).toBeNull();
  });

  it("R2 위기 자원 오버레이는 동의 전에도 항상 보인다", async () => {
    // 이 씬이 R1 감도가 가장 높다 — outro 를 기다리지 않고 여기에서 노출한다.
    renderPage();

    expect(
      await screen.findByText(/다 가진 사람에게도 공허는 옵니다/),
    ).toBeInTheDocument();
  });

  it("라벨을 고르면 다음 씬으로 간다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    renderPage();
    await consent(user);

    await user.click(
      screen.getByRole("button", { name: "다 이루었는데, 비어 있습니다" }),
    );

    expect(mockDecide).toHaveBeenCalledWith("solomon", SESSION, 4, {
      value: "emptiness",
    });
  });

  it("이름 붙이지 않고도 계속할 수 있다 — optional_selection", async () => {
    const user = userEvent.setup();
    advanceEngine();
    renderPage();
    await consent(user);

    await user.click(
      screen.getByRole("button", { name: "이름 붙이지 않고 계속 →" }),
    );

    expect(mockDecide).toHaveBeenCalledWith("solomon", SESSION, 4, {
      value: "no_label",
    });
  });
});

describe("Solomon Scene 5 — 재정향과 내려놓기 (outro)", () => {
  beforeEach(() => {
    mockStart.mockResolvedValue(sceneResponse(5));
  });

  it("라벨 없이 들어오면 default 재정향 문구를 쓴다", async () => {
    renderPage();

    expect(await screen.findByText(REORIENT_DEFAULT)).toBeInTheDocument();
    expect(screen.getByText("이 시간을 기록하시겠어요?")).toBeInTheDocument();
  });

  it("항상 등장하는 오브젝트만 보이고, 경로 의존 오브젝트는 숨는다", async () => {
    renderPage();
    await screen.findByRole("button", { name: /왕관/ });

    // scene3 을 밟지 않았어도 sword(=scene3_completed) 는 기본값 true 로 보인다.
    expect(screen.getByRole("button", { name: /칼/ })).toBeInTheDocument();
    // 건너뛰지 않았으므로 판결 두루마리는 없고, scene4 미방문이라 보물도 없다.
    expect(screen.queryByRole("button", { name: /판결 두루마리/ })).toBeNull();
    expect(screen.queryByRole("button", { name: /보물/ })).toBeNull();
  });

  it("내려놓으면 표시가 남고 다시 누를 수 없다 — 그러나 강제는 아니다", async () => {
    const user = userEvent.setup();
    renderPage();

    const crown = await screen.findByRole("button", { name: /왕관/ });
    await user.click(crown);

    expect(within(crown).getByText("내려놓음 ✓")).toBeInTheDocument();
    expect(crown).toBeDisabled();
    // 하나도 안 내려놓아도 완료 버튼은 처음부터 열려 있다 (required:false).
    expect(screen.getByRole("button", { name: "미션 완료" })).toBeEnabled();
  });

  it("미션 완료를 누르면 completed 로 세션을 닫는다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "미션 완료" }));

    expect(mockComplete).toHaveBeenCalledWith("solomon", SESSION, "completed");
  });

  it("전도서 묵상으로 이어지는 길을 남긴다", async () => {
    renderPage();

    const link = await screen.findByRole("link", { name: "전도서 묵상 →" });
    expect(link).toHaveAttribute("href", "/topics/ecclesiastes");
    expect(screen.getByRole("link", { name: "← 홈" })).toHaveAttribute(
      "href",
      "/",
    );
  });
});

describe("Solomon — 끝까지 걸어가기", () => {
  it("Scene 1 → 5 를 걸어 완주하면 경로가 결말에 반영된다", async () => {
    const user = userEvent.setup();
    advanceEngine();
    await bootToScene1();

    // Scene 1 — 제물 없이 통과
    await user.click(
      screen.getByRole("button", { name: "올리지 않고 다음으로 →" }),
    );
    // Scene 2 — cinematic
    await user.click(await screen.findByRole("button", { name: "계속 →" }));

    // Scene 3 — 동의 후 수렴 선택
    await screen.findByText("잠깐 — 다음 장면 안내");
    await consent(user);
    await user.click(screen.getByRole("button", { name: "칼을 가져오라" }));

    // Scene 4 — 다시 동의 카드가 뜬다 (씬마다 새로 묻는다)
    await screen.findByText(/다음 장면은 모든 것을 가진 왕이 느낀 공허/);
    await consent(user);
    await user.click(
      screen.getByRole("button", { name: "다 이루었는데, 비어 있습니다" }),
    );

    // Scene 5 — 고른 라벨(emptiness) × faith_tone balanced 재정향 문구
    expect(await screen.findByText(REORIENT_BALANCED)).toBeInTheDocument();
    // scene4 를 방문했으므로 보물이 등장하고, scene3 을 완주했으므로 칼도 있다.
    expect(screen.getByRole("button", { name: /보물/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /칼/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /판결 두루마리/ })).toBeNull();

    // 진행 기록에는 지나온 씬 제목이 쌓인다.
    await user.click(screen.getByText("진행 기록"));
    expect(screen.getByText(/꿈에 응답하신 하나님/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(mockComplete).toHaveBeenCalledWith("solomon", SESSION, "completed");
  });

  it("Scene 3 을 건너뛰면 칼 대신 판결 두루마리가 놓인다 — 시각 트리거 회피", async () => {
    const user = userEvent.setup();
    advanceEngine();
    mockStart.mockResolvedValue(sceneResponse(3));
    renderPage();

    await user.click(await screen.findByRole("button", { name: "건너뛰기 →" }));

    // Scene 4 — 라벨 없이 통과
    await screen.findByText("잠깐 — 다음 장면 안내");
    await consent(user);
    await user.click(
      screen.getByRole("button", { name: "이름 붙이지 않고 계속 →" }),
    );

    await screen.findByText(REORIENT_DEFAULT);
    expect(
      screen.getByRole("button", { name: /판결 두루마리/ }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^칼/ })).toBeNull();
  });
});

describe("Solomon — payload 가 얇을 때도 길이 막히지 않는다", () => {
  it("script 비트가 없는 씬도 진행 버튼은 살아 있다", async () => {
    // 대사를 payload 에만 두는 설계라, 비트가 비면 본문 영역이 통째로 빈다.
    // 그때 진행 손잡이까지 사라지면 사용자는 갇힌다.
    const user = userEvent.setup();
    advanceEngine();
    mockStart.mockResolvedValue({
      ...sceneResponse(2),
      scenePayload: { ...SCENE2_PAYLOAD, extras: {} },
    });
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속 →" }));
    expect(mockDecide).toHaveBeenCalledWith("solomon", SESSION, 2, "next");
  });

  it("laydown·재정향이 없는 outro 에서도 미션을 닫을 수 있다", async () => {
    const user = userEvent.setup();
    mockStart.mockResolvedValue({
      ...sceneResponse(5),
      scenePayload: { ...SCENE5_PAYLOAD, extras: { script: [] } },
    });
    renderPage();

    await user.click(await screen.findByRole("button", { name: "미션 완료" }));

    expect(mockComplete).toHaveBeenCalledWith("solomon", SESSION, "completed");
    expect(screen.queryByText(/내려놓아도 좋습니다/)).toBeNull();
  });

  it("[구멍] consent_card_ko 가 없으면 경고 카드가 *문구 없이* 뜬다", async () => {
    // 이 화면은 카드 본문을 payload 정본에만 의존한다. level·content 는 읽지도 않고,
    // 공용 TriggerWarningGate 의 fallbackProse 같은 대체 문구도 없다.
    // 그래서 정본 문구가 빠진 yml(예: david·joseph 형태) 을 이 화면에 물리면
    // 사용자는 *무엇에 대한 경고인지 모른 채* 계속/건너뛰기를 고르게 된다.
    mockStart.mockResolvedValue({
      ...sceneResponse(3),
      scenePayload: {
        ...SCENE3_PAYLOAD,
        trigger_warning: {
          level: "medium",
          content: ["infant_loss"],
          skip_alternative_scene_id: 4,
        },
      },
    });
    renderPage();

    expect(
      await screen.findByText("잠깐 — 다음 장면 안내"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "계속한다" }),
    ).toBeInTheDocument();
    // 경고의 내용(레벨·트리거 종류)은 어디에도 나오지 않는다.
    expect(screen.queryByText(/영아 상실/)).toBeNull();
    expect(screen.queryByText(/medium/)).toBeNull();
    // 그래도 본문은 확실히 가려져 있다 — 게이트 자체는 작동한다.
    expect(screen.queryByText(/그 때에 두 여자가/)).toBeNull();
  });
});

describe("Solomon — 동의 게이트에서의 실패 처리", () => {
  it("[버그] 건너뛰기가 실패하면 사용자는 아무 안내도 받지 못한다", async () => {
    // decide.isError 문구는 `!needsConsent` 섹션 안에만 있다(page.tsx:263·392).
    // 동의 게이트에 머무는 동안 skip 이 실패하면 화면에 아무 변화가 없다 —
    // 사용자는 버튼이 먹지 않는다고 느낀다. 현재 동작을 고정해 두고 보고한다.
    const user = userEvent.setup();
    mockStart.mockResolvedValue(sceneResponse(3));
    mockDecide.mockRejectedValue(new Error("502 Bad Gateway"));
    renderPage();

    await user.click(await screen.findByRole("button", { name: "건너뛰기 →" }));

    // 요약 자막은 이미 떠 있는데(= 건너뛴 것처럼 보이는데) 실제로는 씬이 안 넘어갔다.
    expect(
      await screen.findByText(/왕은 칼을 가져오라 명하여/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/오류: 502 Bad Gateway/)).toBeNull();
    expect(screen.getByText("잠깐 — 다음 장면 안내")).toBeInTheDocument();
  });
});
