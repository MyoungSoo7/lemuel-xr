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

  이 화면 고유의 위험은 **사용자가 앉는 자리** 에서 나온다. 앞의 열두 인물과 달리
  여기서 사용자는 상처를 받은 쪽이 아니라 **준 쪽** 이다. 그 자리에 앉혀 놓고
  「그러니 돌아가라」로 끝나면 이 화면은 정확히 반대의 물건이 된다. 그래서 여기서
  재는 것들은 전부 한 문장으로 모인다 — **화해는 요구되지 않는다.**

  1) **마감 문구의 `default` 는 톤 셋이 아니라 한 문안(`all`)이다.** 다니엘·베드로
     화면을 그대로 베껴 오면 `node[faith_tone]` 만 보고 undefined 로 떨어져, 갈래를
     고르지 않은 사람은 **마감을 한 줄도 못 받는다.** 고르지 않는 것이 정상 경로라고
     저작이 못 박아 둔 인물에서 하필 그 사람만 빈 화면으로 끝난다.

  2) **Scene 5 를 건너뛴 사람도 같은 마감을 받는다.** 대면을 본 사람만 끝을 받으면
     그게 곧 화해 압박이다. 축약 블록(`5_alt`)은 자막만 갈아 끼우고 `closing_texts`
     를 덮지 않으므로 두 경로의 마감이 같아야 한다.

  3) **셋째 문은 카드 밖에 상시로 있다.** `cardDoors()` 가 문을 정확히 둘만 만들기
     때문에 저작의 셋째 문(「나는 이 자리에 서 있지 않다」)은 `extras.offramp` 로
     옮겨졌다. 그 문이 다섯 씬 중 하나에서라도 사라지면 저작이 `available_at:
     [scene1..scene5]` 라고 적어 둔 「상시」가 거짓말이 된다. 그리고 그 문은
     **결정으로 기록되지 않고 마감 문구도 거치지 않는다**(`records_decision: false`
     · `closing_phrase: none`) — 마치는 것이지 끝을 받는 것이 아니다.

  4) **세 갈래에 등급이 없다.** 순서는 payload 가 준 그대로여야 하고(`shuffle_per_
     session`), 번호·진행 막대를 붙이지 않는다(`flat_siblings`). 화면이 정렬하는
     순간 세 장은 「돌아감을 향한 진행 막대」가 되고, 머무는 카드는 「아직 거기」가 된다.
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
 * 동의 카드 정본 2장 — backend/src/main/resources/scenarios/jacob.yml 의
 * Scene 1·5 `consent_card_ko` 를 그대로 옮긴 것. 마지막 줄의 위기 안내만 서버가
 * 치환한 뒤 모양으로 둔다.
 *
 * 문 이름을 이 파일 어디에도 *따로* 적지 않는다 — 기대값도 이 상수에서 뽑아 쓴다.
 * 두 카드의 문 이름이 서로 다르다는 것이 이 인물에서도 그대로다.
 */
const CARD_SCENE1 = `이 미션에서 당신은 야곱의 자리에 섭니다. 야곱은 상처를 받은 쪽이 아니라 상처를 준 쪽입니다.
· 다루는 소재 — 가족 안의 기만과 편애 · 속은 형제의 절규 · 형이 품은 살해 의도.
· 약 1분. 곧이어 속은 형제의 부르짖음으로 이어집니다.
· 화해를 요구하는 묵상이 아닙니다. 돌아가지 않기로 하는 것도 이 묵상 안에서 온전한 끝입니다.
계속하시겠어요?
[들어간다]  [연출 없이 요약으로 본다]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const CARD_SCENE5 = `다음 장면은 가해자로서의 대면을 다룹니다.
· 다루는 소재 — 가해자로서의 대면 · 원치 않는 재회.
· 재회가 안전하지 않은 관계가 실재합니다.
· 넘어가도 되고 여기서 마쳐도 됩니다.
계속하시겠어요?
[들어간다]  [연출 없이 한 절로 마무리한다]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/** 정본에서 두 문의 이름을 뽑는다 — 기대값도 정본에서 파생시키기 위한 것. */
const DOORS_S1 = cardDoors(CARD_SCENE1)!;
const DOORS_S5 = cardDoors(CARD_SCENE5)!;

/**
 * Scene 1 을 건너뛴 사람에게 저작이 남겨 둔 다리 한 줄(`skip_bridge_narration_ko`).
 * 이 줄이 없으면 건너뛴 사람은 **야곱이 무엇을 했는지 모르는 채** 로 형의 절규
 * 앞에 도착한다 — Scene 2 의 「그가 나를 속임이 이것이 두 번째니이다」가 무엇을
 * 가리키는지 알 수 없는 문장이 된다.
 */
const BRIDGE = "야곱은 축복을 속임으로 취했다. (창 27:19, 27:35 요약)";

const CRISIS_S2 = `지금 이 장면이 버겁다면 멈추어도 됩니다. 도움을 받을 수 있는 곳이 있습니다 — ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;
const CRISIS_S5 = `여기까지 오시느라 애쓰셨습니다. 오늘 마음이 무겁게 남으셨다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/**
 * 저작 카드의 셋째 문. yml 에서 앵커(`&offramp`)로 다섯 씬 전부에 같은 블록이 실린다 —
 * 그래서 이 픽스처도 한 벌만 두고 다섯 곳에서 참조한다. 여기서 씬마다 다른 사본을
 * 만들면 「다섯 씬에 같은 문이 있다」가 아니라 「다섯 개의 문이 있다」를 재게 된다.
 */
const OFFRAMP = {
  label_ko: "나는 이 자리에 서 있지 않다 — 여기서 마치기",
  notice_ko:
    "가족 안에서 상처를 받은 쪽에 서 있다면, 이 미션은 당신의 자리를 다루지 않습니다. 회복할 것이 없다는 뜻이 아니라, 이 미션이 다른 자리를 다룬다는 뜻입니다. 여기서 마치셔도 됩니다.",
  records_decision: false,
  closing_phrase: "none",
  mission_complete: true,
};

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
  들어가고 그 밖의 씬 키(trigger_warning·value_prompt)는 루트에 온다.
  픽스처를 평평하게 적으면 화면이 두 겹을 잘못 읽어도 초록이 된다.
*/
const CAPTION_DECEIT = "나는 아버지의 맏아들 에서로소이다 (창 27:19) ※ 축약";
const CAPTION_CRY = "내 아버지여 내게 축복하소서 내게도 그리하소서 (창 27:34)";
const CAPTION_REUNION =
  "에서가 달려와서 그를 맞이하여 안고 목을 어긋맞추어 그와 입맞추고 서로 우니라 (창 33:4)";
const CAPTION_GIFT =
  "청하건대 내가 형님께 드리는 예물을 받으소서 하고 그에게 강권하매 받으니라 (창 33:11)";

const SCENE1 = scene(1, {
  title: "속임",
  type: "cinematic",
  scriptureRef: "gen-27:19",
  exposure_grade: "A",
  trigger_warning: {
    level: "high",
    content: [
      "family_deception",
      "parental_favoritism",
      "sibling_death_threat",
    ],
    consent_card_id: "cc_jacob_family_deception",
    // 저작은 이 카드가 1~4 를 함께 덮는다고 적었지만 엔진이 목적지를 `covers_scenes`
    // 밖에서만 받는다. `[1]` · 목적지 2 가 그 제약 아래 남은 유일한 모양이다.
    covers_scenes: [1],
    skip_alternative_scene_id: 2,
    consent_card_ko: CARD_SCENE1,
    skip_bridge_narration_ko: BRIDGE,
  },
  extras: {
    anchor: "이삭의 장막 안. 눈먼 아버지 앞. 낮은 등불.",
    additional_refs: ["gen-27:35"],
    captions: [{ speaker_ko: "야곱의 속말", text_ko: CAPTION_DECEIT }],
    npc_note: "이삭의 얼굴을 렌더하지 않는다.",
    observe_note: "사용자 조작이 없다. 이미 저지른 자리에서 시작한다.",
    offramp: OFFRAMP,
  },
});

const SCENE2 = scene(2, {
  title: "부르짖음",
  type: "cinematic",
  scriptureRef: "gen-27:34",
  extras: {
    anchor: "같은 장막의 문간. 조금 물러선 자리.",
    captions: [{ speaker_ko: "에서", text_ko: CAPTION_CRY }],
    crisis_reminder: CRISIS_S2,
    threat_note: "27:41 은 정적 자막 전용이다.",
    offramp: OFFRAMP,
  },
});

const PRE_BRANCH = `이 다음은 세 장의 카드입니다.
세 장은 나란히 있습니다. 순서도 단계도 아니고, 더 나아간 카드가 없습니다.
고르지 않고 지나가셔도 됩니다.`;

const SCENE3 = scene(3, {
  title: "이십 년",
  type: "interaction",
  interaction: "pick_one",
  scriptureRef: "gen-32:9",
  extras: {
    anchor: "얍복 나루가 내려다보이는 언덕. 해가 지고 있다.",
    captions: [
      {
        speaker_ko: "해설",
        text_ko: "이십 년이 지났다. 야곱은 이제 돌아가는 길 위에 있다.",
      },
    ],
    pre_branch_notice_ko: PRE_BRANCH,
    options: [
      { id: "send_ahead", label_ko: "먼저 무언가를 보낸다" },
      { id: "go_afraid", label_ko: "두려운 채로 간다" },
      { id: "stay_and_pray", label_ko: "가지 않고 머문다" },
    ],
    options_note: "셋 다 정당하고 결말 품질에 차등이 없다.",
    card_ui_note: "세 카드는 같은 크기·같은 서체다.",
    decision_key: "return_label",
    offramp: OFFRAMP,
  },
});

const SCENE4 = scene(4, {
  title: "얍복",
  type: "interaction",
  interaction: "contemplative",
  scriptureRef: "gen-32:24",
  extras: {
    anchor: "밤의 나루터. 홀로 남았다.",
    captions: [
      {
        speaker_ko: "해설",
        text_ko: "야곱은 홀로 남았더니 (창 32:24) ※ 축약",
      },
    ],
    dwell: {
      min_seconds: 3,
      on_timeout: "auto_proceed_after_seconds_15",
      note_ko: "머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다.",
    },
    victory_note: "「이겼음이니라」를 사용자 성공 라벨로 재사용하지 않는다.",
    grip_debt_note: "연출이 빠진 것이지 규칙이 완화된 것이 아니다.",
    offramp: OFFRAMP,
  },
});

/*
  마감 10문의 정본 꼬리. 열 문장 전부가 이 문장으로 끝나고, 저작이 자구 변경을 금지한
  자리다. 픽스처의 열 문안을 표식으로 줄이더라도 **이 꼬리는 붙여 둔다** — 화면이
  꼬리를 잘라 내는 회귀가 생기면 그때 마감은 오늘 앉은 사람에게 내려지는 지시가 된다.
*/
const RELEASE =
  "이것은 야곱에게 있었던 일이며, 당신의 실제 관계에 적용하지 않아도 된다.";

const CLOSING_SEND_AHEAD = `야곱은 떼를 먼저 보냈다. ${RELEASE}`;
const CLOSING_STAY = `가지 않는 것도 하나의 자리다. ${RELEASE}`;
const CLOSING_DEFAULT = `여기까지 온 것으로 이 시간은 완결이다. ${RELEASE}`;

/*
  ⚠️ `default` 만 모양이 다르다 — 톤 셋이 아니라 `all` 하나다. 라벨이 없는 사람에게
  임재 강도를 조절할 근거가 없다는 저작 판단이고, 이 비대칭이 이 픽스처의 요점이다.
  세 갈래를 톤 셋으로, default 를 `all` 로 적어야 화면이 둘 다 읽는지 잴 수 있다.
*/
const CLOSING_TEXTS = {
  send_ahead: {
    strong: "s-send",
    balanced: CLOSING_SEND_AHEAD,
    soft: "f-send",
  },
  go_afraid: { strong: "s-afraid", balanced: "b-afraid", soft: "f-afraid" },
  stay_and_pray: { strong: "s-stay", balanced: CLOSING_STAY, soft: "f-stay" },
  default: { all: CLOSING_DEFAULT },
};

const SCENE5 = scene(5, {
  title: "받으니라",
  type: "outro",
  scriptureRef: "gen-33:1",
  value_prompt:
    "오늘 한 가지만 — 지금 두려운 것을 감추지 않고 한 줄로 적어 보는 것.",
  trigger_warning: {
    level: "high",
    content: ["confrontation_as_offender", "unwanted_reunion"],
    consent_card_id: "cc_jacob_confrontation",
    covers_scenes: [5],
    // 문자열 목적지 — 같은 Scene 의 축약 블록이다. 마지막 씬이라 갈 다음이 없다.
    skip_alternative_scene_id: "5_alt",
    consent_card_ko: CARD_SCENE5,
    skip_bridge_narration_ko: CAPTION_GIFT,
  },
  extras: {
    anchor: "새벽의 벌판. 해가 올라오고 있다.",
    captions: [
      { speaker_ko: "해설", text_ko: CAPTION_REUNION },
      { speaker_ko: "해설", text_ko: CAPTION_GIFT },
    ],
    closing_texts: CLOSING_TEXTS,
    crisis_reminder: CRISIS_S5,
    next_scene_suggestion: "시편 32 — 숨기지 않고 아뢰는 기도",
    closing_texts_note: "열 문장 전부가 같은 해제 문장으로 끝난다.",
    theophany_note: "33:10 을 신현으로 확정하지 않는다.",
    scope_note: "이 Scene 은 창세기 33:11 에서 끝난다.",
    offramp: OFFRAMP,
  },
});

/*
  Scene 5 를 건너뛴 사람이 받는 것. `DecideSceneUseCase.altBlockPayload` 가 같은 Scene 을
  다시 조립한 결과라 **sceneId 가 5 그대로이고 `trigger_warning` 도 그대로 남는다.**
  달라지는 것은 자막이 한 절로 줄고 `conditionalBlockId` 가 박히는 것뿐이며,
  `closing_texts` 는 덮이지 않는다 — 그래서 마감이 본 경로와 같아야 한다.
*/
const SCENE5_ALT = scene(5, {
  ...SCENE5.scenePayload,
  conditionalBlockId: "5_alt",
  extras: {
    ...(SCENE5.scenePayload.extras as Record<string, unknown>),
    captions: [{ speaker_ko: "해설", text_ko: CAPTION_GIFT }],
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
  vi.mocked(startMission).mockReset();
  vi.mocked(decideMission).mockReset();
  vi.mocked(completeMission).mockReset();
  vi.mocked(completeMission).mockResolvedValue(undefined);
});

describe("야곱 미션 — 씬 상태 기계", () => {
  it("Scene 1 → 5 → 미션 완료까지 끝까지 걸어간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    // Scene 1 — R4 게이트 ①. 동의 전에는 속임의 본문이 뜨지 않는다.
    expect(
      await screen.findByRole("button", { name: DOORS_S1.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(CAPTION_DECEIT)).toBeNull();
    // 카드에 적힌 목적지(Scene 2)와 백엔드가 보내 주는 씬이 같아야 한다.
    expect(
      screen.getByText(/건너뛰면 Scene 2 으로 이어집니다/),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: DOORS_S1.continueLabel }),
    );
    expect(await screen.findByText(CAPTION_DECEIT)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(1, "jacob", SESSION, 1, {
      value: "next",
    });

    // Scene 2 — 카드가 없다. Scene 1 의 카드가 이 씬을 고지했다(consent_coverage).
    expect(await screen.findByText(CAPTION_CRY)).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(CRISIS_DEFAULT.tel);
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // Scene 3 — 이 미션의 유일한 선택. 고른 값이 그대로 서버로 간다.
    expect(await screen.findByText(/이십 년이 지났다/)).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /먼저 무언가를 보낸다/ }),
    );
    expect(decideMission).toHaveBeenNthCalledWith(3, "jacob", SESSION, 3, {
      value: "send_ahead",
    });

    // Scene 4 — 얍복. 카드가 없고, 머무는 시간에 정답이 없다는 문구는 사용자 카피다.
    expect(await screen.findByText(/야곱은 홀로 남았더니/)).toBeInTheDocument();
    expect(
      screen.getByText("머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // Scene 5 — R4 게이트 ②. Scene 1 에서 동의했어도 여기서 **다시 묻는다.**
    expect(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(CAPTION_REUNION)).toBeNull();

    await user.click(
      screen.getByRole("button", { name: DOORS_S5.continueLabel }),
    );
    expect(await screen.findByText(CAPTION_REUNION)).toBeInTheDocument();

    // Scene 3 에서 고른 갈래의 마감 문구가 나온다 — 꼬리까지 그대로.
    expect(screen.getByText(CLOSING_SEND_AHEAD)).toBeInTheDocument();
    expect(screen.getByText(/한 줄로 적어 보는 것/)).toBeInTheDocument();
    expect(
      screen.getByText(/시편 32 — 숨기지 않고 아뢰는 기도/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith("jacob", SESSION, "completed");
  });

  it("시작이 실패하면 부팅 화면에서 다시 시도할 수 있다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission)
      .mockRejectedValueOnce(new Error("boom"))
      .mockResolvedValueOnce(SCENE2);
    renderPage();

    // 실패한 채로 멈춰 있으면 이 미션은 열려 있는 것이 아니다.
    await screen.findByRole("button", { name: /다시 시도/ });
    await user.click(screen.getByRole("button", { name: /다시 시도/ }));
    expect(await screen.findByText(CAPTION_CRY)).toBeInTheDocument();
  });
});

describe("야곱 미션 — 동의 카드는 둘이고 서로를 상속하지 않는다", () => {
  it("Scene 1 을 건너뛴 사람은 다리 한 줄을 받고 Scene 2 로 간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValue(SCENE2);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S1.skipLabel }),
    );
    expect(decideMission).toHaveBeenCalledWith("jacob", SESSION, 1, {
      value: "skip",
    });

    /*
      건너뛴 사람은 야곱이 무엇을 했는지 모르는 채 형의 절규 앞에 선다.
      저작이 놓아 둔 다리 한 줄이 그 사이를 잇는다. 이 줄이 사라져도 화면은
      멀쩡히 돌기 때문에, 여기서 재지 않으면 아무 데서도 안 잡힌다.
    */
    expect(await screen.findByText(BRIDGE)).toBeInTheDocument();
    expect(screen.getByText(CAPTION_CRY)).toBeInTheDocument();
  });

  it("다리는 다음 진행에서 지워진다 — 이야기 위에 겹쳐 읽히지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S1.skipLabel }),
    );
    await screen.findByText(BRIDGE);
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    expect(await screen.findByText(/이십 년이 지났다/)).toBeInTheDocument();
    expect(screen.queryByText(BRIDGE)).toBeNull();
  });

  it("Scene 1 에서 동의해도 Scene 5 는 자기 카드로 다시 묻는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S1.continueLabel }),
    );
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // 카드가 또 떠야 한다. 그리고 그 카드는 Scene 1 의 카드가 아니라 Scene 5 의 것이다.
    expect(
      await screen.findByRole("button", { name: DOORS_S5.skipLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(CAPTION_REUNION)).toBeNull();
    expect(
      screen.queryByRole("button", { name: DOORS_S1.skipLabel }),
    ).toBeNull();
  });

  it("문자열 목적지에는 Scene 번호를 지어내지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    /*
      Scene 5 의 목적지는 `5_alt` — 씬 번호가 아니다. `toSceneId` 가 undefined 를
      주므로 안내는 목적지 없는 문장으로 떨어져야 한다. 숫자를 지어내면(예: "Scene 6")
      존재하지 않는 씬을 약속하는 카드가 된다.
    */
    expect(
      await screen.findByText(/건너뛰어도 이야기는 이어집니다/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/으로 이어집니다/)).toBeNull();
  });
});

describe("야곱 미션 — 건너뛴 사람도 같은 끝을 받는다", () => {
  /*
    이 인물에서 가장 무거운 회귀가 여기다. 대면을 본 사람만 마감을 받으면, 화면은
    「끝을 받으려면 만나야 한다」고 말하는 물건이 된다 — 저작이 축으로 세운 문장의
    정반대다. 축약 블록은 자막만 갈고 `closing_texts` 를 덮지 않는다.
  */
  it("Scene 5 를 건너뛰면 카드가 다시 뜨지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.skipLabel }),
    );
    expect(decideMission).toHaveBeenCalledWith("jacob", SESSION, 5, {
      value: "skip",
    });

    /*
      축약본이 도착했다 — 예물 한 절은 남고 대면 자막은 없다.

      33:11 이 **두 번** 뜨는 것이 정본대로다. 이 카드의 `skip_bridge_narration_ko`
      가 33:11 이고 축약 블록의 자막도 33:11 이라, 다리와 자막이 같은 절이다.
      한 번만 기대하면 이 단정은 「다리가 사라졌다」와 「자막이 사라졌다」를
      구별하지 못한 채 통과한다.
    */
    expect(await screen.findAllByText(CAPTION_GIFT)).toHaveLength(2);
    expect(screen.queryByText(CAPTION_REUNION)).toBeNull();
    // 그리고 카드는 다시 뜨지 않는다.
    expect(
      screen.queryByRole("button", { name: DOORS_S5.skipLabel }),
    ).toBeNull();
  });

  it("건너뛴 사람도 마감 문구·위기 안내·미션 완료에 도달한다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.skipLabel }),
    );

    expect(await screen.findByText(CLOSING_DEFAULT)).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(CRISIS_DEFAULT.tel);
    expect(screen.getByText(/한 줄로 적어 보는 것/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith("jacob", SESSION, "completed");
  });
});

describe("야곱 미션 — 세 갈래에 등급이 없고 고르지 않아도 끝을 받는다", () => {
  it("세 갈래는 payload 순서 그대로 나란히 뜬다 — 번호를 붙이지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    await screen.findByText(/이십 년이 지났다/);
    const labels = screen
      .getAllByRole("button")
      .map((b) => b.textContent ?? "")
      .filter((t) =>
        ["먼저 무언가를 보낸다", "두려운 채로 간다", "가지 않고 머문다"].some(
          (l) => t.includes(l),
        ),
      );
    // 순서가 payload 그대로여야 한다. 화면이 정렬하면 세 장은 진행 막대가 된다.
    expect(labels).toEqual([
      "먼저 무언가를 보낸다",
      "두려운 채로 간다",
      "가지 않고 머문다",
    ]);
    // 갈래 앞의 고지는 사용자 카피다(`_ko` 로 끝난다). 이 문단이 R3 를 화면에 옮긴다.
    expect(screen.getByText(/순서도 단계도 아니고/)).toBeInTheDocument();
  });

  it("고르지 않고 넘어가면 default 마감(all)이 나간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "고르지 않고 넘어가기" }),
    );
    expect(decideMission).toHaveBeenCalledWith("jacob", SESSION, 3, {
      value: "next",
    });

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    );

    /*
      ⚠️ 이 단정이 이 파일에서 가장 잘 깨지는 것이다. `default` 는 톤 셋이 아니라
      `all` 하나뿐이라, 톤만 보고 떨어지는 구현에서는 **여기가 빈 화면이 된다.**
      무응답이 정상 경로라고 저작이 못 박은 인물에서 하필 그 사람만 끝을 못 받는다.
    */
    expect(await screen.findByText(CLOSING_DEFAULT)).toBeInTheDocument();
    expect(screen.queryByText(CLOSING_SEND_AHEAD)).toBeNull();
  });

  it("「가지 않고 머문다」도 자기 마감을 받는다 — 미완의 자리가 아니다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /가지 않고 머문다/ }),
    );
    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    );

    expect(await screen.findByText(CLOSING_STAY)).toBeInTheDocument();
    expect(screen.getByText(CLOSING_STAY)).toHaveTextContent(RELEASE);
  });
});

describe("야곱 미션 — 셋째 문은 상시로 있고 결정으로 기록되지 않는다", () => {
  it("Scene 1 의 동의 카드를 지나면 이탈 버튼이 있다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S1.continueLabel }),
    );
    expect(
      screen.getByRole("button", { name: OFFRAMP.label_ko }),
    ).toBeInTheDocument();
  });

  it("펼치는 것만으로는 아무 일도 일어나지 않는다 — 되돌아올 수 있다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: OFFRAMP.label_ko }),
    );
    expect(screen.getByText(OFFRAMP.notice_ko)).toBeInTheDocument();
    // 실수로 밟는 사람이 없어야 한다 — 안내문을 읽고 한 번 더 눌러야 닫힌다.
    expect(completeMission).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "계속 보기" }));
    expect(screen.queryByText(OFFRAMP.notice_ko)).toBeNull();
    expect(completeMission).not.toHaveBeenCalled();
  });

  it("「여기서 마치기」는 마감 문구 없이 완료로 닫고 결정을 남기지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: OFFRAMP.label_ko }),
    );
    await user.click(screen.getByRole("button", { name: "여기서 마치기" }));

    expect(completeMission).toHaveBeenCalledWith("jacob", SESSION, "completed");
    // `records_decision: false` — 서버에 남는 것은 완료 하나뿐이다.
    expect(decideMission).not.toHaveBeenCalled();
    // `closing_phrase: none` — 마치는 것이지 끝을 받는 것이 아니다.
    expect(screen.queryByText(CLOSING_DEFAULT)).toBeNull();
  });

  it("마지막 씬에는 띄우지 않는다 — 이미 마감 버튼이 있다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    );
    await screen.findByText(CAPTION_REUNION);

    expect(screen.queryByRole("button", { name: OFFRAMP.label_ko })).toBeNull();
    expect(
      screen.getByRole("button", { name: "미션 완료" }),
    ).toBeInTheDocument();
  });
});

describe("야곱 미션 — 저작자용 주석은 화면에 나가지 않는다", () => {
  it("Scene 1 의 npc_note · observe_note", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S1.continueLabel }),
    );
    await screen.findByText(CAPTION_DECEIT);

    // extras 는 payload 로 통째 복사된다 — 가드 주석까지 함께 온다.
    // 그걸 그대로 뿌리면 저작자에게 한 말이 사용자에게 간다.
    expect(screen.queryByText(/이삭의 얼굴을 렌더하지 않는다/)).toBeNull();
    expect(screen.queryByText(/이미 저지른 자리에서 시작한다/)).toBeNull();
  });

  it("Scene 3 의 options_note · card_ui_note", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    await screen.findByText(/이십 년이 지났다/);
    expect(screen.queryByText(/결말 품질에 차등이 없다/)).toBeNull();
    expect(screen.queryByText(/같은 크기·같은 서체다/)).toBeNull();
  });

  it("Scene 5 의 closing_texts_note · theophany_note · scope_note", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    );
    await screen.findByText(CAPTION_REUNION);

    expect(screen.queryByText(/같은 해제 문장으로 끝난다/)).toBeNull();
    expect(screen.queryByText(/신현으로 확정하지 않는다/)).toBeNull();
    expect(screen.queryByText(/창세기 33:11 에서 끝난다/)).toBeNull();
  });
});
