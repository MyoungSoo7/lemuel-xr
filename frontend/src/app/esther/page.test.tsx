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
  에스더 미션 화면 테스트.

  이 인물의 축은 한 문장이다 — **드러내는 것이 정답인 묵상이 아니다.** 화면에서
  그 축이 무너지는 길은 좁고 구체적이라, 여기서 재는 것도 그만큼 좁다.

  1) **마감 문구의 `default` 는 톤 셋이 아니라 한 문안(`all`)이다.** 다니엘 화면을
     그대로 베껴 오면 `node[faith_tone]` 만 보고 undefined 로 떨어져, 카드를 고르지
     않은 사람은 마감을 한 줄도 못 받는다. 저작이 "고르지 않는 것도 정상 경로" 라고
     못 박아 둔 인물에서 하필 그 사람만 빈 화면으로 끝난다.

  2) **세 카드에 등급이 없다.** 순서는 payload 가 준 그대로여야 하고
     (`card_order_policy: shuffle_per_session`), 번호·stepper·진행 막대를 붙이지
     않는다(`flat_siblings`). 화면이 정렬을 얹는 순간 세 장은 「개시를 향한 진행
     막대」가 되고, 맨 앞의 침묵 카드는 「아직 거기」가 된다.

  3) **동의 카드는 Scene 1 하나뿐이다.** 야곱(둘)·룻(다중 커버)과 다르다. 건너뛰기
     목적지는 정수 `2` 이고, 건너뛴 사람은 은폐(2:10)와 고발(3:8)을 못 본 채 통곡
     앞에 선다 — 저작이 남긴 `skip_bridge_narration_ko` 석 줄이 그 사이를 잇는다.
     그 줄이 사라져도 화면은 멀쩡히 돌기 때문에 여기서 안 재면 아무 데서도 안 잡힌다.

  4) **`*_note` 는 저작자용 가드 주석이지 사용자 카피가 아니다.** 화면에 새면
     저작이 자기에게 한 지시(「하만을 만화적 악당으로 그리지 않는다」)가 사용자에게
     읽히는 문장이 된다. `mandatory_clause` 도 같다 — 자막 안에 이미 있는 문자열의
     *선언* 이라, 따로 띄우면 같은 문장이 두 번 나온다.
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission, completeMission } from "@/lib/api/game";
import EstherPage from "./page";

const SESSION = "sess-esther";

/**
 * 동의 카드 정본 — backend/src/main/resources/scenarios/esther.yml Scene 1 의
 * `consent_card_ko`. 마지막 줄의 위기 안내만 서버가 치환한 뒤 모양으로 둔다.
 *
 * 문 이름을 이 파일 어디에도 *따로* 적지 않는다 — 기대값도 이 상수에서 뽑아 쓴다.
 * 화면이 정본의 이름을 쓰는지를 재려는 것이지, 내가 아는 이름과 같은지가 아니다.
 */
const CARD = `다음 장면은 한 민족 전체를 없애라는 국가 조서를 다룹니다.
· 조서 본문(에 3:13)에는 어린이와 여인을 포함해 죽이라는 표현이 인용 자막으로 잠시 나옵니다.
· 약 1분. 곧이어 그 소식을 들은 사람들의 장면으로 이어집니다.
· 이 묵상은 정체를 드러낼 것인가를 다룹니다. 드러내는 것이 정답인 묵상이 아닙니다.
계속하시겠어요?
[계속한다]  [건너뛰기 — 조서 낭독 없이 요약으로]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const DOORS = cardDoors(CARD)!;

/**
 * Scene 1 을 건너뛴 사람에게 저작이 남겨 둔 석 줄. 은폐(2:10)와 고발(3:8)이
 * 여기 들어 있다 — 이 줄이 없으면 건너뛴 사람은 **무엇 때문에 우는지** 모르는 채
 * Scene 2 의 통곡 앞에 선다.
 */
const BRIDGE = `에스더가 자기의 민족과 종족을 말하지 아니하니 (에 2:10) ※ 축약
한 민족이 왕의 나라 각 지방 백성 중에 흩어져 거하는데 (에 3:8) ※ 축약
한 민족 전체를 없애라는 조서가 온 지방에 내려졌다.`;

/**
 * 위 석 줄이 화면에서 갖는 모양.
 *
 * 화면은 `whitespace-pre-line` 으로 줄바꿈을 살려 그리지만, Testing Library 는
 * 비교 전에 element 의 텍스트를 **공백 정규화** 한다(줄바꿈도 공백 한 칸이 된다).
 * 그래서 줄바꿈이 든 원문으로는 어떤 매처도 절대 안 맞는다 — 화면이 멀쩡해도
 * 빨개지는 종류의 함정이라 여기서 한 번 접어 두고, 기대값도 이 상수에서 파생시킨다.
 * (석 줄을 통째로 비교하는 것은 유지한다. 한 줄만 도달해도 통과하는 검사가 되면
 *  「다리가 놓였다」가 아니라 「다리 조각이 있다」를 재게 된다.)
 */
const BRIDGE_ONE_LINE = BRIDGE.split("\n").join(" ");

const CRISIS_S1 = `한 민족을 없애라는 조서가 나오는 장면입니다. 지금 힘드시면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;
const CRISIS_S3 = `말할지 말지를 고르는 자리입니다. 지금 힘드시면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;
const CRISIS_S4 = `힘든 문장이 지나갔습니다. 지금 마음이 무겁다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;
const CRISIS_S5 = `여기까지 오시느라 애쓰셨습니다. 오늘 마음이 무겁게 남으셨다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

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
const CAPTION_HIDDEN =
  "에스더가 자기의 민족과 종족을 말하지 아니하니 (에 2:10)";
const CAPTION_ACCUSE =
  "한 민족이 왕의 나라 각 지방 백성 중에 흩어져 거하는데 (에 3:8)";
const CAPTION_WAIL =
  "모르드개가 이 모든 일을 알고 자기의 옷을 찢고 (에 4:1) ※ 축약";

/**
 * 4:14 의 **방어 절반절**. 저작 게이트(R3)가 「선언(`mandatory_clause`)과 본문이
 * 동시에 있어야 한다」고 정해 둔 문자열이라, 픽스처의 자막에도 그대로 들어 있어야
 * 이 화면에서 "선언은 렌더하지 않는다" 를 잴 수 있다.
 */
const MANDATORY = "다른 데로 말미암아 놓임과 구원을 얻으려니와";
const CAPTION_LAW = `이 때에 네가 만일 잠잠하여 말이 없으면 유다인은 ${MANDATORY} (에 4:14) ※ 축약`;
const CAPTION_THREE_DAYS = "사흘이 지나갔다.";
const CAPTION_SCEPTER =
  "왕이 손에 잡았던 금 규를 그에게 내미니 (에 5:2) ※ 축약";

const SCENE1 = scene(1, {
  title: "이름 없는 민족",
  type: "cinematic",
  scriptureRef: "est-2:10",
  exposure_grade: "A",
  trigger_warning: {
    level: "high",
    content: ["genocide_threat", "ethnic_persecution", "child_endangerment"],
    consent_card_id: "esther_scene1_edict_warning",
    covers_scenes: [1],
    // 정수 목적지 — 서버가 Scene 2 로 점프시킨다. 야곱 Scene 5 의 문자열
    // 목적지(`5_alt`)와 다르고, 그래서 `conditionalBlockId` 경로를 타지 않는다.
    skip_alternative_scene_id: 2,
    consent_card_ko: CARD,
    skip_bridge_narration_ko: BRIDGE,
  },
  extras: {
    anchor: "수산 궁 알현실. 차고 격식 있는 낮빛.",
    additional_refs: ["est-3:8", "est-3:9", "est-3:13"],
    captions: [
      { speaker_ko: "해설", text_ko: CAPTION_HIDDEN },
      { speaker_ko: "하만", text_ko: CAPTION_ACCUSE },
    ],
    crisis_reminder: CRISIS_S1,
    npc_note: "하만을 만화적 악당으로 그리지 않는다.",
    order_note: "2:10 을 먼저 두고 3:8 을 뒤에 둔다.",
    observe_note:
      "사용자 조작이 없다. 조서 선포는 관찰 대상이지 개입 대상이 아니다.",
    dwell_limit_note: "조서 자막에는 체류 상한 3초가 걸려 있다.",
    scope_note: "에스더 1장과 2장 서사의 배제 사유는 docs/MVP-ESTHER.md 참조.",
  },
});

const SCENE2 = scene(2, {
  title: "성문 밖의 통곡",
  type: "cinematic",
  scriptureRef: "est-4:1",
  extras: {
    anchor: "수산 성문 밖이 내려다보이는 궁 안 난간. 흐린 한낮.",
    captions: [{ speaker_ko: "해설", text_ko: CAPTION_WAIL }],
    npc_note: "모르드개도 재에 누운 군중도 원경 실루엣만이다.",
    distance_note: "사용자는 궁 안에 있고 통곡은 성문 밖에 있다.",
    leak_note: "조서 전문은 Scene 1 전용이며 여기서 다시 렌더하지 않는다.",
  },
});

const PRE_BRANCH = `이 다음은 세 장의 카드입니다.
세 장은 나란히 있습니다. 순서도 단계도 아니고, 더 나아간 카드가 없습니다.
· 드러내는 것이 정답인 묵상이 아닙니다. 말하지 않기로 하는 것도 끝까지 온전한 선택입니다.
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/**
 * 저작이 카드마다 놓아 둔 한 마디. 화면은 이걸 **다음 씬 상단에 한 번** 얹고
 * 다음 진행에서 지운다. 남아 있으면 이야기 위에 겹쳐 읽히는 다른 본문이 된다.
 */
const ACK = {
  nondisclosure: "말하지 않기로 하셨군요. 그 선택은 그대로 괜찮습니다.",
  gather_first: "먼저 곁에 있을 사람을 떠올리셨군요.",
  speak_now: "말하기로 하셨군요. 속도는 당신이 정합니다.",
};

const SCENE3 = scene(3, {
  title: "법과 삼십 일",
  type: "interaction",
  interaction: "pick_one",
  scriptureRef: "est-4:11",
  extras: {
    anchor: "수산 궁 안쪽 방. 창으로 드는 빛이 낮다.",
    additional_refs: ["est-4:13", "est-4:14"],
    captions: [{ speaker_ko: "해설", text_ko: CAPTION_LAW }],
    mandatory_clause: MANDATORY,
    pre_branch_notice_ko: PRE_BRANCH,
    /*
      정본 순서 — 침묵이 맨 앞이다. 서버가 세션마다 섞어 보낼 수 있으므로
      (`shuffle_per_session`) 화면은 **받은 순서 그대로** 그려야 하고, 그걸 재려면
      픽스처가 알파벳순도 아니고 「개시 강도순」도 아닌 정본 순서여야 한다.
    */
    options: [
      { id: "nondisclosure", label_ko: "말하지 않기로 한다" },
      { id: "gather_first", label_ko: "먼저 곁을 만든다" },
      { id: "speak_now", label_ko: "지금 말하기로 한다" },
    ],
    options_note: "셋 다 정당하고 결말 품질에 차등이 없다.",
    card_order_policy: "shuffle_per_session",
    card_render_style: "flat_siblings",
    card_ui_note: "번호 매김·stepper·진행 막대를 쓰지 않는다.",
    decision_key: "disclose_label",
    acknowledgements: ACK,
    crisis_reminder: CRISIS_S3,
    open_question_note: "「누가 알겠느냐」는 의문형 그대로 둔다.",
  },
});

const DWELL_NOTE = "머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다.";

const SCENE4 = scene(4, {
  title: "사흘",
  type: "interaction",
  interaction: "contemplative",
  scriptureRef: "est-4:16",
  extras: {
    anchor: "등불 하나만 남은 방. 사흘이 지나간다.",
    captions: [{ speaker_ko: "해설", text_ko: CAPTION_THREE_DAYS }],
    dwell: {
      min_seconds: 3,
      on_timeout: "auto_proceed_after_seconds_15",
      note_ko: DWELL_NOTE,
    },
    crisis_reminder: CRISIS_S4,
    achievement_note:
      "금식의 사흘은 성취가 아니다. 타이머·진행 바를 두지 않는다.",
    quotation_only_note: "4:16 은 인용 자막으로만 노출한다.",
  },
});

/*
  ⚠️ `default` 만 모양이 다르다 — 톤 셋이 아니라 `all` 하나다. 라벨이 없는 사람에게
  임재 강도를 조절할 근거가 없다는 저작 판단이고, 이 비대칭이 이 픽스처의 요점이다.
  세 갈래를 톤 셋으로, default 를 `all` 로 적어야 화면이 둘 다 읽는지 잴 수 있다.
  (화면은 `balanced` 를 쓴다 — users.faith_tone 의 DB 기본값 미러링.)
*/
const CLOSING_NONDISCLOSURE =
  "말하지 않는 시간은 실패의 다른 이름이 아닙니다. 말하지 않기로 한 것은 당신이 당신을 지킨 방식입니다.";
const CLOSING_SPEAK_NOW =
  "에스더는 왕이 세 번 물은 뒤에야 말했습니다. 말하기로 한 오늘의 당신도, 속도를 스스로 정할 수 있습니다.";
const CLOSING_DEFAULT =
  "에스더서는 '누가 알겠느냐'는 물음으로 남습니다. 답을 고르지 않고 여기까지 온 것도 이 묵상의 온전한 끝입니다.";

const CLOSING_TEXTS = {
  nondisclosure: {
    strong: "s-non",
    balanced: CLOSING_NONDISCLOSURE,
    soft: "f-non",
  },
  gather_first: { strong: "s-gather", balanced: "b-gather", soft: "f-gather" },
  speak_now: {
    strong: "s-speak",
    balanced: CLOSING_SPEAK_NOW,
    soft: "f-speak",
  },
  default: { all: CLOSING_DEFAULT },
};

const VALUE_PROMPT =
  "오늘 한 가지만 — 지금 말하지 않기로 한 것이 무엇이고 그게 왜 당신을 지키는지, 한 줄로 적어 보는 것.";

const SCENE5 = scene(5, {
  title: "안 뜰, 금 규, 그리고 세 번의 물음",
  type: "outro",
  scriptureRef: "est-5:1",
  // 야곱과 달리 **마지막 씬에 동의 카드가 없다.** 이 인물의 게이트는 Scene 1 하나다.
  value_prompt: VALUE_PROMPT,
  extras: {
    anchor: "왕궁 안 뜰과 어전. 그리고 잔치의 자리.",
    captions: [{ speaker_ko: "해설", text_ko: CAPTION_SCEPTER }],
    closing_texts: CLOSING_TEXTS,
    crisis_reminder: CRISIS_S5,
    next_scene_suggestion: "시편 27 — 숨겨 주시는 이를 부르는 기도",
    delay_note: "두 번의 미룸은 실패가 아니다.",
    repetition_note:
      "5:3 과 5:6 과 7:2 는 같은 자리에 같은 서체로 반복 렌더한다.",
    closing_texts_note: "사용자의 상태를 단정하지 않는다.",
    hidden_god_note: "이 책에는 하나님이 이름으로 등장하지 않는다.",
    scope_note: "이 Scene 은 에스더 8:6 인용에서 끝난다.",
  },
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <EstherPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(startMission).mockReset();
  vi.mocked(decideMission).mockReset();
  vi.mocked(completeMission).mockReset();
  vi.mocked(completeMission).mockResolvedValue(undefined);
});

describe("에스더 미션 — 씬 상태 기계", () => {
  it("Scene 1 → 5 → 미션 완료까지 끝까지 걸어간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    // Scene 1 — R4 게이트. 동의 전에는 조서 자막이 뜨지 않는다.
    expect(
      await screen.findByRole("button", { name: DOORS.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(CAPTION_ACCUSE)).toBeNull();
    // 카드에 적힌 목적지와 백엔드가 보내 주는 씬이 같아야 한다.
    expect(
      screen.getByText(/건너뛰면 Scene 2 으로 이어집니다/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: DOORS.continueLabel }));
    expect(await screen.findByText(CAPTION_HIDDEN)).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(CRISIS_DEFAULT.tel);
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(1, "esther", SESSION, 1, {
      value: "next",
    });

    // Scene 2 — 카드가 없다. Scene 1 의 카드가 고지한 범위 안이다.
    expect(await screen.findByText(CAPTION_WAIL)).toBeInTheDocument();
    // 이 씬은 위기 안내를 선언하지 않았다 — 없는 씬에 빈 안내를 그리지 않는다.
    expect(screen.queryByRole("note")).toBeNull();
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // Scene 3 — 이 미션의 유일한 선택. 고른 값이 그대로 서버로 간다.
    expect(await screen.findByText(CAPTION_LAW)).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /지금 말하기로 한다/ }),
    );
    expect(decideMission).toHaveBeenNthCalledWith(3, "esther", SESSION, 3, {
      value: "speak_now",
    });

    // Scene 4 — 사흘. 머무는 시간에 정답이 없다는 문구는 사용자 카피다.
    expect(await screen.findByText(CAPTION_THREE_DAYS)).toBeInTheDocument();
    expect(screen.getByText(DWELL_NOTE)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // Scene 5 — outro. 여기서는 다시 묻지 않는다(동의 카드가 없다).
    expect(await screen.findByText(CAPTION_SCEPTER)).toBeInTheDocument();
    expect(screen.getByText(CLOSING_SPEAK_NOW)).toBeInTheDocument();
    expect(screen.getByText(VALUE_PROMPT)).toBeInTheDocument();
    expect(
      screen.getByText(/시편 27 — 숨겨 주시는 이를 부르는 기도/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith(
      "esther",
      SESSION,
      "completed",
    );
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
    expect(await screen.findByText(CAPTION_WAIL)).toBeInTheDocument();
  });

  it("진행이 실패하면 화면에 남아 이유를 말한다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockRejectedValue(new Error("네트워크 끊김"));
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속 →" }));

    // 조용히 아무 일도 안 일어나면 사용자는 자기가 잘못 눌렀다고 읽는다.
    expect(await screen.findByText(/네트워크 끊김/)).toBeInTheDocument();
    expect(screen.getByText(CAPTION_WAIL)).toBeInTheDocument();
  });
});

describe("에스더 미션 — 동의 카드는 Scene 1 하나뿐이다", () => {
  it("카드는 저작이 등재한 트리거를 한국어로 말한다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    /*
      `genocide_threat`·`ethnic_persecution` 은 이 인물이 처음 쓰는 태그다.
      `CONTENT_LABEL` 에 등재하지 않으면 영문 토큰이 그대로 뜨는데, 그건 *모르는*
      태그를 위한 안전망이지 방금 배포한 태그의 자리가 아니다. level 이 high 라
      가장 무거운 고지에서 잡음이 뜨는 셈이 된다.
    */
    expect(
      await screen.findByText("한 민족 전체를 없애려는 위협"),
    ).toBeInTheDocument();
    expect(screen.getByText("민족을 이유로 한 박해")).toBeInTheDocument();
    expect(screen.getByText("아이가 위험에 놓임")).toBeInTheDocument();
    expect(screen.getByText(/정서 강도: 높음/)).toBeInTheDocument();
  });

  it("건너뛴 사람은 은폐와 고발을 잇는 석 줄을 받고 Scene 2 로 간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValue(SCENE2);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS.skipLabel }),
    );
    expect(decideMission).toHaveBeenCalledWith("esther", SESSION, 1, {
      value: "skip",
    });

    /*
      이 줄이 없어도 화면은 멀쩡히 돈다. 사라진 걸 알아채는 유일한 방법은 건너뛴
      사람이 되어 Scene 2 에 서 보는 것뿐이고, 그게 이 검사다.
    */
    expect(await screen.findByText(BRIDGE_ONE_LINE)).toBeInTheDocument();
    expect(screen.getByText(CAPTION_WAIL)).toBeInTheDocument();
  });

  it("다리는 다음 진행에서 지워진다 — 이야기 위에 겹쳐 읽히지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS.skipLabel }),
    );
    await screen.findByText(BRIDGE_ONE_LINE);
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    expect(await screen.findByText(CAPTION_LAW)).toBeInTheDocument();
    expect(screen.queryByText(BRIDGE_ONE_LINE)).toBeNull();
  });

  it("Scene 5 는 카드 없이 열린다 — 게이트를 상속하지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    /*
      야곱은 마지막 씬에서 **다시** 묻는다(대면이 그 자체로 트리거이므로). 에스더는
      묻지 않는다 — 저작이 Scene 5 에 `trigger_warning` 을 두지 않았다. 화면이
      "outro 면 카드" 같은 자기 규칙을 만들면 정본과 어긋난 문이 생긴다.
    */
    expect(await screen.findByText(CAPTION_SCEPTER)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: DOORS.skipLabel })).toBeNull();
    expect(screen.getByRole("button", { name: "미션 완료" })).toBeEnabled();
  });
});

describe("에스더 미션 — 세 카드에 등급이 없고, 고르지 않아도 끝을 받는다", () => {
  it("카드는 payload 가 준 순서 그대로이고 번호가 붙지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    await screen.findByText(CAPTION_LAW);
    const labels = screen
      .getAllByRole("button")
      .map((b) => b.textContent?.trim() ?? "")
      .filter((t) =>
        ["말하지 않기로 한다", "먼저 곁을 만든다", "지금 말하기로 한다"].some(
          (l) => t.startsWith(l),
        ),
      );

    // 침묵이 맨 앞이다. 화면이 정렬을 얹으면 여기가 먼저 깨진다.
    expect(labels).toEqual([
      "말하지 않기로 한다",
      "먼저 곁을 만든다",
      "지금 말하기로 한다",
    ]);
    // 번호·stepper·진행 막대 금지(`flat_siblings`) — 「1.」 이 붙는 순간
    // 세 장은 단계가 되고 맨 앞 카드는 「아직 거기」가 된다.
    expect(screen.queryByText(/^1[.)]/)).toBeNull();
    // 카드 앞의 고지는 사용자 카피다 — 이건 렌더한다.
    expect(screen.getByText(/말하지 않기로 하는 것도/)).toBeInTheDocument();
  });

  it("고르지 않고 넘어간 사람도 마감 문구를 받는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "고르지 않고 넘어가기" }),
    );
    expect(decideMission).toHaveBeenCalledWith("esther", SESSION, 3, {
      value: "next",
    });

    /*
      이 화면에서 가장 조용한 회귀가 여기다. `default` 가 톤 셋이라고 넘겨짚으면
      (다니엘이 그렇다) 이 사람만 마감을 한 줄도 못 받고 끝난다 — 저작이 정상
      경로라고 못 박은 사람이 하필 빈 화면으로 끝나는 것이다.
    */
    expect(await screen.findByText(CLOSING_DEFAULT)).toBeInTheDocument();
    expect(screen.queryByText(CLOSING_NONDISCLOSURE)).toBeNull();
  });

  it("침묵을 고른 사람은 침묵의 마감을 받는다 — 등급이 아니라 다른 문안이다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /말하지 않기로 한다/ }),
    );
    await screen.findByText(CAPTION_THREE_DAYS);
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    expect(await screen.findByText(CLOSING_NONDISCLOSURE)).toBeInTheDocument();
    expect(screen.queryByText(CLOSING_DEFAULT)).toBeNull();
  });

  it("카드에 참조가 달려 오면 부제로 함께 뜬다", async () => {
    /*
      정본은 지금 `ref` 를 안 쓴다. 그래도 화면이 이 필드를 읽는 이유는 저작이
      카드에 근거 절을 달 수 있게 열어 둔 자리이기 때문이고, 재지 않으면 그 자리가
      **조용히 죽은 코드** 로 남는다 — 저작이 나중에 달아도 화면에 안 뜨고,
      안 뜨는 걸 알아챌 방법이 없다.
    */
    vi.mocked(startMission).mockResolvedValue(
      scene(3, {
        ...SCENE3.scenePayload,
        extras: {
          ...(SCENE3.scenePayload.extras as Record<string, unknown>),
          options: [
            {
              id: "nondisclosure",
              label_ko: "말하지 않기로 한다",
              ref: "에 4:11",
            },
          ],
        },
      }),
    );
    renderPage();

    expect(await screen.findByText("에 4:11")).toBeInTheDocument();
  });
});

describe("에스더 미션 — 고른 뒤의 한 마디는 한 번만 얹힌다", () => {
  it("고른 카드의 한 마디가 다음 씬 상단에 뜨고, 그다음 진행에서 지워진다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /먼저 곁을 만든다/ }),
    );

    expect(await screen.findByText(ACK.gather_first)).toBeInTheDocument();
    // 다른 카드의 한 마디가 같이 뜨면 그건 고른 것을 잘못 읽은 것이다.
    expect(screen.queryByText(ACK.speak_now)).toBeNull();

    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(await screen.findByText(CAPTION_SCEPTER)).toBeInTheDocument();
    expect(screen.queryByText(ACK.gather_first)).toBeNull();
  });
});

describe("에스더 미션 — 저작자용 주석은 화면에 나가지 않는다", () => {
  /*
    `*_note` 는 저작이 **자기에게** 한 지시다. 화면에 새면 「하만을 만화적 악당으로
    그리지 않는다」 같은 연출 지침이 사용자에게 읽히는 문장이 된다. payload 를
    통째로 렌더하는 실수(디버그용 `<pre>{JSON.stringify(extras)}</pre>` 를 지우지
    않는 것 같은)에서 이 검사가 유일한 방벽이다.
  */
  const NOTES = [
    "하만을 만화적 악당으로 그리지 않는다.",
    "2:10 을 먼저 두고 3:8 을 뒤에 둔다.",
    "사용자 조작이 없다. 조서 선포는 관찰 대상이지 개입 대상이 아니다.",
    "셋 다 정당하고 결말 품질에 차등이 없다.",
    "번호 매김·stepper·진행 막대를 쓰지 않는다.",
    "금식의 사흘은 성취가 아니다. 타이머·진행 바를 두지 않는다.",
    "두 번의 미룸은 실패가 아니다.",
    "사용자의 상태를 단정하지 않는다.",
    "이 책에는 하나님이 이름으로 등장하지 않는다.",
    "이 Scene 은 에스더 8:6 인용에서 끝난다.",
  ];

  it.each([
    ["Scene 1", SCENE1],
    ["Scene 3", SCENE3],
    ["Scene 4", SCENE4],
    ["Scene 5", SCENE5],
  ])("%s 의 가드 주석은 렌더되지 않는다", async (_name, payload) => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(payload);
    renderPage();

    // 게이트가 떠 있으면 본문 자체가 안 그려져 「안 샌다」가 공허하게 참이 된다.
    const gate = screen.queryByRole("button", { name: DOORS.continueLabel });
    if (gate) await user.click(gate);

    for (const note of NOTES) {
      expect(screen.queryByText(note)).toBeNull();
    }
  });

  it("mandatory_clause 는 자막 안에만 있고 따로 뜨지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    /*
      이건 *선언* 이지 카피가 아니다 — 저작 게이트(R3)가 「4:14 의 방어 절반절이
      본문에 남아 있는가」를 확인하려고 같은 문자열을 한 번 더 적어 둔 것이다.
      화면이 그것까지 그리면 같은 문장이 두 번 나오고, 사용자에게는 반복이 강조로
      읽힌다 — 「다른 데로 말미암아」는 강조하면 안 되는 자리의 문장이다.
    */
    expect(await screen.findByText(CAPTION_LAW)).toBeInTheDocument();
    expect(screen.queryByText(MANDATORY)).toBeNull();
  });
});
