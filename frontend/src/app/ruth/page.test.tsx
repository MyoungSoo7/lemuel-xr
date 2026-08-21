import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
// 번호 리터럴은 `@/lib/crisis-resources` 에만 산다 (`scripts/check_frontend_hotline.py`).
// 픽스처는 백엔드 `CrisisTokenResolver` 가 `{{crisis_resources.default}}` 를 치환해
// 내려보낸 결과를 흉내 내되, 번호를 여기 적지 않고 정본에서 파생시킨다.
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";

/*
  룻 미션 화면 테스트.

  이 화면 고유의 위험은 **두 문이 서로 다른 문이라는 것** 이다.

  룻의 동의 카드는 셋인데 Scene 3 만 `declined_route: closing` 을 선언한다 — 리포
  전체에서 유일하다. 그 카드의 둘째 문은 「여기서 마친다」이고 백엔드가 기다리는
  결정값은 `"skip"` 이 아니라 `"decline"` 이다(SceneSkipResolver). 두 문을 한 값으로
  뭉개도 **화면은 멀쩡히 돌고 어떤 게이트도 빨개지지 않는다** — 마치겠다고 고른
  사람에게 다음 씬이 열릴 뿐이다. CI 의 `check_frontend_trigger_warning.py` 는
  "화면이 payload 를 읽고 skip 을 보내는가" 까지만 보므로 이 구별은 못 잡는다.
  그래서 여기서 Scene 1(skip) 과 Scene 3(decline) 을 나란히 잰다.

  두 번째 위험은 **버튼 문구의 출처** 다. 세 카드의 문 이름은
  docs/RUTH-LOCKED-STRINGS.md 가 소유한 고정 문자열이고 payload 의 `consent_card_ko`
  안에 실려 온다. 프론트가 베껴 적으면 정본 개정이 화면에 안 따라오고 그 드리프트를
  재는 검사기가 없다(AC 는 yml·docs 만 대조한다). 그래서 파싱 결과가 정본과 같은지를
  직접 잰다.
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission, completeMission } from "@/lib/api/game";
import RuthPage, { cardDoors } from "./page";

const SESSION = "sess-ruth";

/**
 * 카드 정본 — backend/src/main/resources/scenarios/ruth.yml 의 `consent_card_ko` 를
 * 그대로 옮긴 것. 마지막 줄의 위기 안내만 서버가 치환한 뒤 모양으로 둔다.
 *
 * 문 이름(「사별 장면은 건너뛴다」 등)을 이 파일 어디에도 *따로* 적지 않는다 —
 * 기대값도 이 상수에서 뽑아 쓴다. 따로 적으면 정본이 바뀌어도 한쪽만 고쳐 초록이 된다.
 */
const CARD_SCENE1 = `이 이야기에는 사별한 사람들이 나옵니다.
남편을 잃은 두 여인과, 남편과 두 아들을 모두 잃은 한 여인입니다.
· 전체 5-7분입니다. 어느 지점에서든 멈추거나 나갈 수 있습니다.
계속하시겠어요?
[계속한다]  [사별 장면은 건너뛴다]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const CARD_SCENE3 = `여기서부터는 낯선 땅에서의 일입니다.
한 여인이 남의 밭에서 이삭을 줍고, 사람들은 그를 "모압 여인"이라고 부릅니다.
· 약 3분입니다. 여기서 마치셔도 이야기는 온전히 끝맺어집니다.
계속하시겠어요?
[계속한다]  [여기서 마친다]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const CARD_SCENE4 = `다음 장면은 밤의 타작 마당입니다.
본문은 어떤 성적인 일도 서술하지 않으며, 이 장면은 그런 일을 재연하지 않습니다.
계속하시겠어요?
[계속한다]  [건너뛰기 — 성문 장면으로 이동]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const BRIDGE =
  "두 며느리 중 하나는 돌아가고, 하나는 남았다. 남은 사람의 이름은 룻이었다.";

const CRISIS_REMINDER = `${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}.`;

/** SR-1 — 종결 자막은 리포 전체에 한 벌만 존재한다(`ruth.yml` closing_screen). */
const CLOSING_CAPTION =
  "이스라엘의 하나님 여호와께서 그의 날개 아래에 보호를 받으러 온 네게";

function scene(currentScene: number, scenePayload: Record<string, unknown>) {
  return {
    sessionId: SESSION,
    userId: "guest-1",
    currentScene,
    scenePayload,
    responseText: null,
  };
}

/*
  payload 는 두 겹이다 — 로더가 표준 9필드만 걷어내므로 yml 의 `extras:` 블록이 한 겹
  더 들어간다. 즉 `trigger_warning`·`consent_coverage` 는 루트, 자막·상호작용·마감 줄은
  `extras` 안이다. 픽스처를 평평하게 적으면 화면이 두 겹을 잘못 읽어도 초록이 된다.
*/
const SCENE1 = scene(1, {
  title: "갈림길",
  type: "interaction",
  interaction: "scripture_reading",
  trigger_warning: {
    level: "mid",
    content: ["bereavement", "isolation"],
    consent_card_id: "ruth_entry_consent",
    consent_card_ko: CARD_SCENE1,
    covers_scenes: [1, 2],
    skip_alternative_scene_id: 3,
    skip_bridge_narration_ko: BRIDGE,
  },
  extras: {
    captions: [
      {
        speaker_ko: "나오미",
        ref: "룻 1:8",
        text_ko: "너희는 각기 어머니의 집으로 돌아가라",
      },
    ],
    interactions: [],
  },
});

const SCENE2 = scene(2, {
  title: "빈 손의 귀향",
  type: "interaction",
  interaction: "pick_one",
  consent_coverage: {
    inherited: true,
    consent_card_id: "ruth_entry_consent",
    covered_by_scene: 1,
    skip_alternative_scene_id: 3,
  },
  extras: {
    captions: [
      {
        speaker_ko: "나오미",
        ref: "룻 1:21",
        text_ko:
          "내가 풍족하게 나갔더니 여호와께서 나를 비어 돌아오게 하셨느니라",
      },
    ],
    interactions: [
      {
        id: "mourning_distance",
        type: "pick_one",
        decision_key: "belonging_label",
        prompt_ko: "지금 어디에 서 계시겠어요?",
        options: [
          { value: "stay_beside", label_ko: "곁에 남는다" },
          { value: "step_back", label_ko: "한 걸음 물러선다" },
        ],
        unselected_value: null,
      },
    ],
  },
});

const SCENE3 = scene(3, {
  title: "이방 여인",
  type: "interaction",
  interaction: "grab_and_place",
  trigger_warning: {
    level: "low_mid",
    content: ["bereavement"],
    consent_card_id: "ruth_midpoint_consent",
    consent_card_ko: CARD_SCENE3,
    covers_scenes: [3, 5],
    skip_alternative_scene_id: 4,
    declined_route: "closing",
  },
  extras: {
    captions: [
      { speaker_ko: "룻", ref: "룻 2:10", text_ko: "나는 이방 여인이거늘" },
    ],
    interactions: [
      {
        id: "gleaning",
        type: "grab_and_place",
        prompt_ko: "떨어진 이삭을 주워 담아 보세요.",
        repeatable: true,
        scoring: "none",
        yield_display: false,
        target_count: null,
      },
    ],
  },
});

const SCENE4 = scene(4, {
  title: "타작 마당의 밤",
  type: "interaction",
  interaction: "scripture_reading",
  exposure_grade: "C",
  trigger_warning: {
    level: "high",
    content: ["bereavement"],
    consent_card_id: "ruth_scene4_night_warning",
    consent_card_ko: CARD_SCENE4,
    covers_scenes: [4],
    skip_alternative_scene_id: 5,
  },
  extras: {
    captions: [
      { speaker_ko: "보아스", ref: "룻 3:11", text_ko: "두려워하지 말라" },
    ],
    interactions: [],
  },
});

const SCENE5 = scene(5, {
  title: "성문에서",
  type: "outro",
  consent_coverage: {
    inherited: true,
    covered_by_scene: 3,
    skip_alternative_scene_id: "ruth_scene5_alt_short",
  },
  extras: {
    captions: [
      { speaker_ko: "장로들", ref: "룻 4:11", text_ko: "우리가 증인이 되나니" },
    ],
    interactions: [],
    value_prompt: "무자격자가 속하게 되는 일을 오늘 어디서 보셨습니까?",
    crisis_reminder: CRISIS_REMINDER,
    closing_lines: {
      selected_by: "belonging_label",
      variants: {
        stay_beside_ko: "곁에 남은 자리에도 그늘이 닿았다.",
        step_back_ko: "한 걸음 물러선 자리에도 그늘이 닿았다.",
        default_ko: "이 자리에도 그늘이 닿았다.",
      },
    },
    closing_screen: {
      entry_mode: "closing_only",
      closing_caption: {
        ref: "룻 2:12 중",
        text_ko:
          "이스라엘의 하나님 여호와께서 그의 날개 아래에 보호를 받으러 온 네게",
      },
    },
  },
});

/** 카드 정본에서 둘째 문 이름을 뽑는다 — 기대값도 정본에서 파생시키기 위한 것. */
function secondDoor(card: string): string {
  return cardDoors(card)!.skipLabel;
}

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <RuthPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(startMission).mockReset();
  vi.mocked(decideMission).mockReset();
  vi.mocked(completeMission).mockReset();
  vi.mocked(completeMission).mockResolvedValue(undefined);
});

describe("cardDoors — 문 이름은 정본에서 읽는다", () => {
  it("세 카드의 두 문 이름을 정본 그대로 뽑는다", () => {
    expect(cardDoors(CARD_SCENE1)).toEqual({
      continueLabel: "계속한다",
      skipLabel: "사별 장면은 건너뛴다",
    });
    expect(cardDoors(CARD_SCENE3)).toEqual({
      continueLabel: "계속한다",
      skipLabel: "여기서 마친다",
    });
    expect(cardDoors(CARD_SCENE4)).toEqual({
      continueLabel: "계속한다",
      skipLabel: "건너뛰기 — 성문 장면으로 이동",
    });
  });

  it("강도 줄을 문으로 착각하지 않는다", () => {
    // 「음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]」 은 대괄호로 *시작하지 않는다*.
    // 이 줄을 먼저 집으면 계속 버튼이 「자막만」이 되고, 카드에 나갈 문이 사라진다.
    expect(
      cardDoors("음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]"),
    ).toBeUndefined();
  });

  it("읽지 못하면 undefined — 문을 없애지 않고 기본 라벨로 돌아간다", () => {
    expect(cardDoors(undefined)).toBeUndefined();
    expect(cardDoors("계속하시겠어요?")).toBeUndefined();
    expect(cardDoors("[계속한다]")).toBeUndefined(); // 문이 하나뿐이면 짝이 아니다
  });
});

describe("룻 미션 — 씬 상태 기계", () => {
  it("Scene 1 → 5 → 미션 완료까지 끝까지 걸어간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    // Scene 1 — 카드가 먼저다. 본문 자막은 동의 전에는 뜨지 않는다.
    expect(
      await screen.findByRole("button", { name: "계속한다" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/어머니의 집으로 돌아가라/)).toBeNull();

    await user.click(screen.getByRole("button", { name: "계속한다" }));
    expect(
      await screen.findByText(/어머니의 집으로 돌아가라/),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(1, "ruth", SESSION, 1, {
      value: "next",
    });

    // Scene 2 — 카드에 덮인 씬이라 게이트 없이 바로 본문이다.
    expect(
      await screen.findByText(/비어 돌아오게 하셨느니라/),
    ).toBeInTheDocument();
    expect(screen.getByText("지금 어디에 서 계시겠어요?")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "곁에 남는다" }));
    expect(decideMission).toHaveBeenNthCalledWith(2, "ruth", SESSION, 2, {
      value: "stay_beside",
    });

    // Scene 3 — 다시 카드. 동의 후 이삭 줍기.
    await user.click(await screen.findByRole("button", { name: "계속한다" }));
    expect(
      await screen.findByText("떨어진 이삭을 주워 담아 보세요."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(3, "ruth", SESSION, 3, {
      value: "next",
    });

    // Scene 4 — 등급 C. 카드 없이 본문이 열리면 안 된다.
    expect(
      await screen.findByRole("button", { name: "계속한다" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("두려워하지 말라")).toBeNull();
    await user.click(screen.getByRole("button", { name: "계속한다" }));
    await user.click(await screen.findByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(4, "ruth", SESSION, 4, {
      value: "next",
    });

    // Scene 5 outro — 마감 한 줄이 **먼저**, 종결 자막이 그 다음.
    expect(
      await screen.findByText("곁에 남은 자리에도 그늘이 닿았다."),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/그의 날개 아래에 보호를 받으러 온 네게/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/무자격자가 속하게 되는 일을 오늘 어디서 보셨습니까/),
    ).toBeInTheDocument();
    // 씬이 직접 선언한 위기 안내가 화면까지 온다 (백엔드가 번호를 치환해 보낸 값).
    expect(screen.getByRole("note")).toHaveTextContent(CRISIS_DEFAULT.tel);

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith("ruth", SESSION, "completed");
  });
});

describe("룻 미션 — 두 문은 다른 문이다", () => {
  it("Scene 1 둘째 문은 건너뛰기 — skip 을 보내고 다리 나레이션을 띄운다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValue(SCENE3);
    renderPage();

    const door = await screen.findByRole("button", {
      name: secondDoor(CARD_SCENE1),
    });
    await user.click(door);

    expect(decideMission).toHaveBeenCalledWith("ruth", SESSION, 1, {
      value: "skip",
    });
    // 이 카드는 Scene 1·2 를 함께 덮고 목적지가 3 이다. 건너뛴 사람이 룻이라는
    // 이름을 처음 만나는 대목을 통째로 지나치므로, 저작된 다리 한 줄을 띄운다.
    expect(await screen.findByText(BRIDGE)).toBeInTheDocument();
  });

  it("Scene 3 둘째 문은 거절 — decline 을 보내고 종결로 간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    /*
      백엔드 Skip.Closing 이 돌려주는 payload 그대로 — 허용목록 3키다
      (`type` · `closing_screen` · `crisis_reminder`). 거절은 Scene 5 의 내용까지
      거절한 것이라(중간 카드 `covers_scenes: [3, 5]`) 성문 낭독 자막은 오지 않는다.

      ⚠️ 이 픽스처는 2026-08-22 이전에 `{ type: "end" }` 였다. 백엔드가 실제로 그것만
      보냈고, 그래서 거절한 사용자에게는 종결 자막도 위기 안내도 가지 않았다.
    */
    vi.mocked(decideMission).mockResolvedValue(
      scene(3, {
        type: "end",
        closing_screen: {
          entry_mode: "closing_only",
          ui_overlays: [
            "suffering_disclaimer",
            "crisis_reminder",
            "exit_button",
          ],
          closing_caption: { ref: "룻 2:12 중", text_ko: CLOSING_CAPTION },
        },
        crisis_reminder: CRISIS_REMINDER,
      }),
    );
    renderPage();

    const door = await screen.findByRole("button", {
      name: secondDoor(CARD_SCENE3),
    });
    await user.click(door);

    expect(decideMission).toHaveBeenCalledWith("ruth", SESSION, 3, {
      value: "decline",
    });
    expect(await screen.findByText(/여기서 마쳤습니다/)).toBeInTheDocument();

    /*
      거절한 사용자도 저작된 종결 자막(SR-1)을 받는다 — ruth.yml 이 「세 경로가 모두
      이 블록에 착지한다. 덜 본 사람에게 덜한 결말을 주지 않는다」고 적어 둔 그것이다.
    */
    expect(screen.getByText(CLOSING_CAPTION)).toBeInTheDocument();
    /*
      그 화면의 `ui_overlays` 는 `[suffering_disclaimer, crisis_reminder, exit_button]`
      이다. 빠졌던 것은 자막이 아니라 **위기 안내와 나가기 버튼**이었고, 카드를 거절한
      사용자야말로 그 셋이 필요한 쪽이다.
    */
    expect(
      screen.getByText(new RegExp(CRISIS_DEFAULT.tel)),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "← 홈" })).toBeInTheDocument();

    // 마쳤다고 고른 사람에게 다음 씬의 손잡이가 남아 있으면 안 된다.
    expect(screen.queryByRole("button", { name: "계속 →" })).toBeNull();

    await user.click(screen.getByRole("button", { name: "홈으로" }));
    expect(completeMission).toHaveBeenCalledWith("ruth", SESSION, "declined");
  });

  it("거절 카드의 안내는 '이어집니다' 가 아니라 '종결' 이라고 적는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    // Scene 3 은 skip_alternative_scene_id: 4 와 declined_route: closing 을 둘 다
    // 선언한다. 앞의 값을 읽으면 「여기서 마친다」 버튼 밑에 "Scene 4 로 이어집니다"
    // 라고 적히게 된다 — 버튼과 안내가 서로 다른 말을 하는 카드다.
    expect(await screen.findByText(/종결 화면으로 갑니다/)).toBeInTheDocument();
    expect(screen.queryByText(/Scene 4 으로 이어집니다/)).toBeNull();
  });

  it("Scene 4 둘째 문은 건너뛰기 — 거절 문구가 새지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    expect(
      await screen.findByText(/건너뛰면 Scene 5 으로 이어집니다/),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: secondDoor(CARD_SCENE4) }),
    );
    expect(decideMission).toHaveBeenCalledWith("ruth", SESSION, 4, {
      value: "skip",
    });
  });
});

describe("룻 미션 — 고르지 않을 자유", () => {
  it("Scene 2 를 고르지 않고 지나가면 null 을 보내고 마감은 default 로 간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "고르지 않고 계속 →" }),
    );
    // `unselected_value: null` — 미선택은 실패가 아니라 값이 없는 것이다.
    expect(decideMission).toHaveBeenCalledWith("ruth", SESSION, 2, {
      value: null,
    });

    expect(
      await screen.findByText("이 자리에도 그늘이 닿았다."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/곁에 남은 자리에도/)).toBeNull();
  });

  it("한 걸음 물러선 사람은 그 갈래의 마감을 받는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "한 걸음 물러선다" }),
    );
    expect(
      await screen.findByText("한 걸음 물러선 자리에도 그늘이 닿았다."),
    ).toBeInTheDocument();
  });
});

describe("룻 미션 — 이삭 줍기는 과제가 아니다", () => {
  it("주워도 개수를 세어 보여주지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속한다" }));
    const glean = await screen.findByRole("button", { name: "이삭을 줍는다" });

    // 누르기 전에는 아무 말도 하지 않는다.
    expect(screen.queryByText(/주워 담았습니다/)).toBeNull();

    await user.click(glean);
    await user.click(glean);
    await user.click(glean);

    // 눌렸다는 사실은 알린다(스크린리더 포함) — 그러나 **몇 개인지는 말하지 않는다**.
    // `yield_display: false` · `scoring: none` · `target_count: null` 이다.
    const ack = screen.getByText("이삭을 주워 담았습니다.");
    expect(ack).toBeInTheDocument();
    expect(ack).toHaveAttribute("aria-live", "polite");
    expect(ack.textContent).not.toMatch(/\d/);
  });
});
