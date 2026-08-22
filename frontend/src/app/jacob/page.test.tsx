import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
// 번호 리터럴은 `@/lib/crisis-resources` 에만 산다 (`scripts/check_frontend_hotline.py`).
// 픽스처는 백엔드 `CrisisTokenResolver` 가 `{{crisis_resources.default}}` 를 치환해
// 내려보낸 결과를 흉내 내되, 번호를 여기 적지 않고 정본에서 파생시킨다.
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";
import { cardDoors } from "@/components/TriggerWarningGate";

/*
  야곱 미션 화면 테스트.

  이 화면 고유의 위험 넷을 잰다.

  ① **동의 카드가 둘인데 서로를 상속하는 것.** Scene 1 은 「당신은 상처를 준 쪽에
     선다」는 역할 고지이고 Scene 5 는 대면·재회 고지다. 저작(`content/jacob/README.md`)
     이 명시적으로 「한 Scene 의 동의가 다른 Scene 의 동의를 대신하지 않는다」를 걸었다.
     화면이 `consented` 를 씬 전환에서 초기화하지 않으면 Scene 1 을 통과한 사람은
     Scene 5 의 카드를 **한 번도 보지 못한 채** 재회 장면으로 들어간다.

  ② **Scene 5 건너뛰기가 무한 루프가 되는 것.** 마지막 씬이라 목적지가 문자열이고,
     서버는 같은 sceneId 를 축약 블록으로 다시 조립해 돌려준다. 그 payload 에는
     `trigger_warning` 이 **그대로 살아 있다** — 동의 여부만 보고 게이트를 세우면
     건너뛰기를 누른 사람 앞에 같은 카드가 다시 뜬다.

  ③ **건너뛴 사람의 결말이 깎이는 것.** 저작의 R3(화해 압박 금지)가 지키는 대칭이
     바로 이것이다. 재회 연출을 건너뛴 사람도 마감 문구·위기 안내·가치 질문을 그대로
     받아야 한다. 백엔드에서는 `JacobReconciliationPressureTest` 가 이 대칭을 재고,
     여기서는 그 payload 가 실제로 화면까지 오는지를 잰다.

  ④ **「이 자리가 내 자리가 아니다」 문이 사라지는 것.** 이 문은 건너뛰기와 다르다 —
     가족 안에서 상처를 **받은** 쪽에 서 있는 사람을 위한 문이고, 저작이 5개 Scene
     전부에 상시 제공을 요구했다. 동의 카드는 버튼을 딱 둘만 그리므로 이 문은 카드
     밖의 별도 컨트롤로 존재한다 — 즉 **아무 공용 컴포넌트도 이 문을 지켜 주지 않는다.**
     `check_frontend_trigger_warning.py` 도 이 문은 보지 않는다. 여기가 유일한 통제다.
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission, completeMission } from "@/lib/api/game";
import JacobPage from "./page";

const SESSION = "sess-jacob";

/**
 * 동의 카드 정본 — `backend/src/main/resources/scenarios/jacob.yml` 의 두 카드를
 * 그대로 옮긴 것. 마지막 줄의 위기 안내만 서버가 치환한 뒤 모양으로 둔다.
 * 문 이름을 이 파일에 *따로* 적지 않는다 — 기대값도 이 상수에서 뽑아 쓴다.
 * 두 카드의 문 이름이 서로 다르다는 사실 자체가 ① 이 재는 것의 일부다.
 */
const CARD_S1 = `이 미션에서 당신은 야곱의 자리에 섭니다. 야곱은 상처를 받은 쪽이 아니라 상처를 준 쪽입니다.
다루는 소재 — 가족 안의 기만과 편애 · 속은 형제의 절규 · 형이 품은 살해 의도.
· 재연 없이 요약으로 보셔도 이야기는 이어집니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
[들어간다]  [연출 없이 요약으로 본다]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const CARD_S5 = `다루는 소재 — 가해자로서의 대면 · 원치 않는 재회.
재회가 안전하지 않은 관계가 실재합니다. 학대나 폭력이 있었던 관계에서 다시 만나는 것은 회복이 아니라 위험일 수 있습니다.
이것은 야곱에게 있었던 일이며, 당신의 실제 관계에 적용하지 않아도 됩니다. 넘어가도 되고 여기서 마쳐도 됩니다.
[그대로 본다]  [재회 연출 없이 넘어간다]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const DOORS_S1 = cardDoors(CARD_S1)!;
const DOORS_S5 = cardDoors(CARD_S5)!;

/** 건너뛴 구간을 잇는, 저작이 써 둔 한 줄. 새로 쓴 산문이 아니다. */
const BRIDGE_S1 = "야곱은 축복을 속임으로 취했다. (창 27:19, 27:35 요약)";

/**
 * 마감 문구 — 정본에서 옮기되 **성구 자구가 없는 갈래만** 골랐다.
 * 프론트에 성경 자구 사본을 늘리지 않기 위해서다(`check_monologue_quotes.py`).
 * 그래서 `stay_and_pray` 의 `strong` 은 픽스처에 없다 — 톤 선택이 `balanced` 를
 * 고르는지를 재는 데는 두 갈래로 충분하다.
 */
const CLOSING_STAY_SOFT =
  "가지 않는 것도 하나의 자리다. 그 자리는 잠시 미뤄 둔 자리가 아니라, 끝까지 그대로 두어도 되는 자리다. 이것은 야곱에게 있었던 일이며, 당신의 실제 관계에 적용하지 않아도 된다.";
const CLOSING_STAY_BALANCED =
  "야곱은 강을 건너기 전에 홀로 남아 밤을 지냈다(창 32:24). 야곱이 이튿날 건넌 것은 야곱에게 일어난 일이고, 건너지 않는 것 역시 그 자체로 온전한 이야기다. 머무름은 다음 장을 기다리는 여백이 아니다. 이것은 야곱에게 있었던 일이며, 당신의 실제 관계에 적용하지 않아도 된다.";
/**
 * 아무것도 고르지 않은 사람의 마감. 정본에서 이 갈래만 **톤 분기가 없는 문자열
 * 하나** 다 — 화면이 표를 한 모양으로만 가정하면 여기서 조용히 사라진다.
 * 그 사람이 이 미션에서 가장 취약한 자리다.
 */
const CLOSING_DEFAULT =
  "여기까지 온 것으로 이 시간은 완결이다. 야곱의 이야기는 형제가 마주 서는 것으로 이어지지만, 그 장면이 당신에게 요구되는 것은 아니다. 오늘 여기서 멈추어도 된다. 이것은 야곱에게 있었던 일이며, 당신의 실제 관계에 적용하지 않아도 된다.";

const OFFRAMP = {
  id: "cc_jacob_not_my_seat",
  label_ko: "나는 이 자리에 서 있지 않다",
  notice_ko:
    "가족 안에서 상처를 받은 쪽에 서 있다면, 이 미션은 당신의 자리를 다루지 않습니다. 회복할 것이 없다는 뜻이 아니라, 이 미션이 다른 자리를 다룬다는 뜻입니다. 여기서 마치셔도 됩니다.",
  records_decision: false,
  closing_phrase: "none",
  mission_complete: true,
};

const CRISIS_S5 = `지금 이 장면이 버겁다면 멈추어도 됩니다. 도움을 받을 수 있는 곳이 있습니다 — ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const VALUE_PROMPT = "당신이 지금 붙들고 있는 것은 무엇입니까.";

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
  payload 는 두 겹이다 — `ScenePayloadAssembler.build` 가 표준 필드를 세팅한 뒤
  `putAll(sc.extras)` 를 하므로, yml 의 `extras:` 블록이 `payload.extras` 로 한 겹 더
  들어가고 그 밖의 씬 키(`value_prompt` 등)는 루트에 온다. 픽스처를 평평하게 적으면
  화면이 두 겹을 잘못 읽어도 초록이 된다.
*/
const SCENE1 = scene(1, {
  title: "속임",
  type: "cinematic",
  scriptureRef: "gen-27:19",
  trigger_warning: {
    level: "mid",
    content: ["family_deception", "betrayal"],
    consent_card_id: "cc_jacob_family_deception",
    covers_scenes: [1],
    skip_alternative_scene_id: 2,
    consent_card_ko: CARD_S1,
    skip_bridge_narration_ko: BRIDGE_S1,
  },
  extras: {
    anchor: "어두운 천막 안.",
    captions: [
      { speaker_ko: "야곱", text_ko: "나는 아버지의 맏아들 에서로소이다 (창 27:19)" },
    ],
    not_my_seat_offramp: OFFRAMP,
  },
});

const SCENE2 = scene(2, {
  title: "부르짖음",
  type: "cinematic",
  scriptureRef: "gen-27:34",
  extras: {
    captions: [{ speaker_ko: "에서", text_ko: "내게 축복하소서 (창 27:34)" }],
    not_my_seat_offramp: OFFRAMP,
  },
});

const SCENE3 = scene(3, {
  title: "이십 년",
  type: "interaction",
  interaction: "pick_one",
  scriptureRef: "gen-32:9",
  extras: {
    prompt_ko: "돌아가는 길, 당신은 지금 무엇을 하겠습니까.",
    options: [
      { id: "send_ahead", label_ko: "먼저 예물을 보낸다", ref: "창 33:8" },
      { id: "go_afraid", label_ko: "두려운 채로 간다", ref: "창 32:11" },
      { id: "stay_and_pray", label_ko: "가지 않고 머물러 기도한다", ref: "창 32:9-10" },
    ],
    decision_key: "return_label",
    not_my_seat_offramp: OFFRAMP,
  },
});

const SCENE5_EXTRAS = {
  captions: [
    { speaker_ko: "해설", text_ko: "야곱이 눈을 들어 보니 (창 33:1)" },
  ],
  additional_refs: ["gen-33:4"],
  crisis_reminder: CRISIS_S5,
  closing_texts: {
    stay_and_pray: {
      soft: CLOSING_STAY_SOFT,
      balanced: CLOSING_STAY_BALANCED,
    },
    default: CLOSING_DEFAULT,
  },
  conditional_blocks: [
    {
      id: "jacob_scene5_alt_receiving_only",
      reached_by: "skip_from_scene5",
      renders: ["closing_texts", "crisis_reminder", "value_prompt"],
    },
  ],
  next_scene_suggestion: "시편 32 — 숨기지 않고 아뢴 자리",
  not_my_seat_offramp: OFFRAMP,
};

const SCENE5 = scene(5, {
  title: "받으니라",
  type: "outro",
  scriptureRef: "gen-33:11",
  next: null,
  value_prompt: VALUE_PROMPT,
  trigger_warning: {
    level: "mid",
    content: ["confrontation", "unwanted_reunion"],
    consent_card_id: "cc_jacob_confrontation",
    covers_scenes: [5],
    skip_alternative_scene_id: "jacob_scene5_alt_receiving_only",
    consent_card_ko: CARD_S5,
  },
  extras: SCENE5_EXTRAS,
});

/** 건너뛴 사람이 받는 축약 payload — 서버가 `conditionalBlockId` 를 박아 준다. */
const SCENE5_ALT = scene(5, {
  ...SCENE5.scenePayload,
  conditionalBlockId: "jacob_scene5_alt_receiving_only",
  extras: {
    ...SCENE5_EXTRAS,
    captions: [
      {
        speaker_ko: "해설",
        text_ko: "내가 형님께 드리는 예물을 받으소서 (창 33:11)",
      },
    ],
    additional_refs: [],
  },
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <JacobPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("야곱 — 동의 카드 둘", () => {
  it("Scene 1 — 동의 전에는 속임 자막을 렌더하지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    expect(await screen.findByText(DOORS_S1.continueLabel)).toBeInTheDocument();
    expect(screen.queryByText(/맏아들 에서로소이다/)).toBeNull();
  });

  it("Scene 1 의 동의가 Scene 5 의 동의를 대신하지 않는다", async () => {
    // ① 저작이 명시적으로 건 요구. `consented` 를 씬 전환에서 초기화하지 않으면
    //    Scene 1 을 통과한 사람이 대면 고지를 **한 번도 보지 못한 채** 들어간다.
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await userEvent.click(await screen.findByText(DOORS_S1.continueLabel));
    await userEvent.click(await screen.findByText("계속 →"));

    expect(await screen.findByText(DOORS_S5.continueLabel)).toBeInTheDocument();
    expect(screen.queryByText(/야곱이 눈을 들어 보니/)).toBeNull();
  });

  it("Scene 1 건너뛰기는 저작이 써 둔 다리 한 줄을 남긴다", async () => {
    // 건너뛴 사람은 야곱이 무엇을 했는지 모르는 채 Scene 2 의 절규 앞에 선다.
    // 다리가 없으면 에서가 왜 우는지 알 수 없는 장면이 된다.
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValue(SCENE2);
    renderPage();

    await userEvent.click(await screen.findByText(DOORS_S1.skipLabel));

    expect(decideMission).toHaveBeenCalledWith("jacob", SESSION, 1, {
      value: "skip",
    });
    expect(await screen.findByText(BRIDGE_S1)).toBeInTheDocument();
  });

  it("이미 건너뛴 사람에게 Scene 5 카드를 다시 세우지 않는다", async () => {
    // ② 축약 payload 에도 trigger_warning 이 살아 있다. `conditionalBlockId` 를
    //    보지 않으면 건너뛰기가 무한 루프가 된다.
    vi.mocked(startMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    expect(await screen.findByText(/예물을 받으소서/)).toBeInTheDocument();
    expect(screen.queryByText(DOORS_S5.continueLabel)).toBeNull();
  });
});

describe("야곱 — 마감 문구", () => {
  it("건너뛴 사람도 마감 문구·위기 안내·가치 질문을 그대로 받는다", async () => {
    // ③ 재회 연출을 건너뛴 사람의 결말이 깎이면 그것이 곧 화해 압박이다.
    vi.mocked(startMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    expect(await screen.findByText(CLOSING_DEFAULT)).toBeInTheDocument();
    expect(screen.getByText(CRISIS_S5)).toBeInTheDocument();
    expect(screen.getByText(VALUE_PROMPT)).toBeInTheDocument();
  });

  it("Scene 3 에서 고른 라벨이 마감 문구를 고른다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await userEvent.click(
      await screen.findByText("가지 않고 머물러 기도한다"),
    );

    expect(decideMission).toHaveBeenCalledWith("jacob", SESSION, 3, {
      value: "stay_and_pray",
    });
    // 톤 축의 기본값은 서버 DB 기본값과 같은 balanced 다. soft 가 나오면 화면이
    // 표의 첫 값을 집은 것이고, 그건 사용자 설정과 무관한 임의 선택이다.
    expect(await screen.findByText(CLOSING_STAY_BALANCED)).toBeInTheDocument();
    expect(screen.queryByText(CLOSING_STAY_SOFT)).toBeNull();
  });

  it("고르지 않고 넘어간 사람은 톤 분기 없는 갈래를 받는다", async () => {
    // 이 갈래만 정본에서 문자열 하나다. 표를 한 모양으로만 가정하면 여기가 빈다.
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await userEvent.click(await screen.findByText("고르지 않고 넘어가기"));

    expect(decideMission).toHaveBeenCalledWith("jacob", SESSION, 3, {
      value: "next",
    });
    expect(await screen.findByText(CLOSING_DEFAULT)).toBeInTheDocument();
  });
});

describe("야곱 — 이 자리가 내 자리가 아닐 때", () => {
  beforeEach(() => {
    // 이탈로는 `completeMission` 뒤 홈으로 보낸다. jsdom 은 실제 이동을 못 하므로
    // location 을 갈아 끼운다 — 이동 자체가 아니라 *무엇을 호출했나* 를 잰다.
    Object.defineProperty(window, "location", {
      configurable: true,
      writable: true,
      value: { href: "" },
    });
  });

  it("동의 카드가 떠 있는 동안에도 문이 닫히지 않는다", async () => {
    // ④ 이 문이 가장 필요한 순간이 바로 그 카드 앞이다.
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    expect(await screen.findByText(DOORS_S1.continueLabel)).toBeInTheDocument();
    expect(screen.getByText(OFFRAMP.label_ko)).toBeInTheDocument();
  });

  it("펼치는 것만으로는 아무 일도 일어나지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    await userEvent.click(await screen.findByText(OFFRAMP.label_ko));

    expect(await screen.findByText(OFFRAMP.notice_ko)).toBeInTheDocument();
    expect(completeMission).not.toHaveBeenCalled();
    expect(decideMission).not.toHaveBeenCalled();
  });

  it("마치면 결정을 남기지 않고 세션만 완료한다", async () => {
    // `records_decision: false` — 이 자리가 자기 자리가 아니라고 말한 사람에게
    // 「미완」을 남기면 그것이 곧 압박이다. 그래서 decide 가 아니라 complete 다.
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(completeMission).mockResolvedValue(undefined);
    renderPage();

    await userEvent.click(await screen.findByText(OFFRAMP.label_ko));
    await userEvent.click(await screen.findByText("여기서 마칩니다"));

    expect(completeMission).toHaveBeenCalledWith("jacob", SESSION, "completed");
    expect(decideMission).not.toHaveBeenCalled();
  });
});
