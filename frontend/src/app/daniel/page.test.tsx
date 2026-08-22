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
  다니엘 미션 화면 테스트.

  이 화면 고유의 위험은 **동의 카드가 둘이라는 것** 이다.

  앞선 아홉 인물은 카드가 한 번 뜨고 그 뒤 씬은 `consent_coverage.inherited` 로
  덮였다. 그래서 지금까지 화면 테스트가 재 온 명제는 「카드는 한 번만 뜬다」였다.
  다니엘은 그 명제가 **틀린 인물** 이다 — Scene 4 카드는 `covers_scenes: [4]` 로
  자기 하나만 덮고, Scene 5 는 자기 카드로 다시 묻는다. 저작(`card_note`)이
  「고지에 동의한 것이 실행에 동의한 것은 아니다」라고 못 박아 둔 결과다.

  그래서 여기서 재는 것은 반대 방향이다 — Scene 4 에서 동의했다는 이유로 Scene 5 의
  카드가 사라지지 않는지. 사라지면 어떤 게이트도 빨개지지 않는다. 화면은 더 매끄러워
  보이고, 굴 안으로 들어가는 데 동의한 적 없는 사람이 굴 안에 들어가 있게 된다.

  두 번째 위험은 **Scene 5 건너뛰기가 무한 루프가 되는 것** 이다. 마지막 씬이라
  갈 다음이 없어서 서버는 같은 sceneId 를 축약 블록으로 다시 조립해 돌려주는데
  (`DecideSceneUseCase.altBlockPayload`), 그 payload 에는 `trigger_warning` 이
  **그대로 살아 있다.** 동의 여부만 보고 게이트를 세우면 건너뛴 사람 앞에 같은 카드가
  다시 뜨고, 다시 건너뛰면 또 뜬다. 「건너뛰기」를 누른 사람이 영원히 건너뛰지 못한다.

  세 번째는 **Scene 2 의 값이 서버로 가야 한다는 것** 이다. 베드로와 정반대다 —
  거기서는 고른 값을 보내면 안 됐지만(부인의 방식이 기록된다), 여기서는 저작이
  `decision_key: request_style` 로 이 값을 쓰겠다고 선언했고 쓰이는 자리는 Scene 5 의
  마감 문구 하나뿐이다. 값이 안 가면 12갈래 중 9갈래가 도달 불가능한 채로 초록이 된다.
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission, completeMission } from "@/lib/api/game";
import DanielPage from "./page";

const SESSION = "sess-daniel";

/**
 * 동의 카드 정본 2장 — backend/src/main/resources/scenarios/daniel.yml 의
 * Scene 4·5 `consent_card_ko` 를 그대로 옮긴 것. 마지막 줄의 위기 안내만 서버가
 * 치환한 뒤 모양으로 둔다.
 *
 * 문 이름을 이 파일 어디에도 *따로* 적지 않는다 — 기대값도 이 상수에서 뽑아 쓴다.
 * 두 카드의 문 이름이 서로 다르다는 것이 이 인물의 특징이라 더 그렇다.
 */
const CARD_SCENE4 = `다음 장면에는 신앙을 이유로 한 처벌을 정한 왕의 금령이 본문 그대로 나옵니다.
처형 방법을 명시한 문장이 포함됩니다.
· 읽지 않고 넘어가셔도 이야기는 이어집니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
[본문 그대로 보기]  [요약만 듣고 넘어가기]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const CARD_SCENE5 = `다음 장면에는 좁고 어두운 공간과 맹수 소리가 나옵니다.
· 폐쇄된 공간이 부담되시면 건너뛰셔도 괜찮습니다.
· 건너뛰셔도 마무리는 그대로 받으십니다.
[그대로 보기]  [조용한 장면으로 넘어가기]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/*
  `daniel.yml` Scene 4 의 `skip_bridge_narration_ko` 정본 넉 줄. 줄여 적으면 안 된다 —
  이 픽스처가 잰다고 믿는 것은 "저작이 쓴 다리가 화면까지 온다" 인데, 여기서 두 줄만
  적으면 나머지 두 줄(금령을 구한 사람들과 도장)이 도중에 사라져도 초록불이다.
  건너뛴 사람이 통째로 지나친 구간이 바로 그 두 줄이고, 그 두 줄이 없으면 Scene 5 의
  「도장이 찍힌 것을 알고도」가 무엇을 알고도인지 알 수 없는 문장이 된다.
*/
const BRIDGE = `다니엘은 총리 셋 중 하나로 나라를 다스렸습니다.
그를 고발할 근거를 찾던 사람들이 아무것도 찾지 못했습니다.
그래서 그들은 왕에게 한 금령을 세우기를 구했습니다.
삼십 일 동안 왕 외의 다른 이에게 무엇을 구하지 못하게 하는 법이었고, 왕의 도장이 찍혔습니다.`;

const CRISIS_S4 = `신앙 때문에 위협을 받는 이야기가 이어집니다. 지금 힘드시면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;
const CRISIS_S5 = `여기까지 오시느라 애쓰셨습니다. 오늘 마음이 무겁게 남으셨다면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/** 정본에서 두 문의 이름을 뽑는다 — 기대값도 정본에서 파생시키기 위한 것. */
const DOORS_S4 = cardDoors(CARD_SCENE4)!;
const DOORS_S5 = cardDoors(CARD_SCENE5)!;

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
const SCENE1 = scene(1, {
  title: "이름이 바뀌던 자리",
  type: "cinematic",
  scriptureRef: "dan-1:7",
  exposure_grade: "B",
  extras: {
    anchor: "바벨론 왕궁의 안뜰. 횃불이 낮게 타는 저녁.",
    captions: [
      {
        speaker_ko: "해설",
        text_ko:
          "환관장이 그들의 이름을 고쳐 다니엘은 벨드사살이라 하고 (단 1:7)",
      },
    ],
    name_change_note: "사용자 조작이 없다. 개명은 선택지가 아니라 겪는 일이다.",
    npc_note: "실사 인물 형상을 쓰지 않는다.",
  },
});

const SCENE2 = scene(2, {
  title: "뜻을 정하다",
  type: "interaction",
  interaction: "pick_one",
  scriptureRef: "dan-1:8",
  extras: {
    additional_refs: ["dan-1:9", "dan-1:11", "dan-1:12"],
    captions: [
      {
        speaker_ko: "해설",
        text_ko:
          "다니엘은 뜻을 정하여 왕의 음식과 그가 마시는 포도주로 자기를 더럽히지 아니하리라 하고 (단 1:8)",
      },
    ],
    options: [
      { id: "direct_refusal", ref: "단 1:8", label_ko: "여기까지라고 말한다" },
      {
        id: "verifiable_proposal",
        ref: "단 1:12-13",
        label_ko: "확인해 볼 수 있는 방법을 낸다",
      },
      {
        id: "seek_ally",
        ref: "단 1:11",
        label_ko: "결정할 수 있는 사람을 찾는다",
      },
    ],
    options_note: "셋 다 본문에 있는 반응이고 틀린 카드가 없다.",
    decision_key: "request_style",
  },
});

const SCENE3 = scene(3, {
  title: "열흘",
  type: "interaction",
  interaction: "contemplative",
  scriptureRef: "dan-1:15",
  extras: {
    captions: [{ speaker_ko: "해설", text_ko: "열흘이 지났다." }],
    dwell: {
      min_seconds: 3,
      on_timeout: "auto_proceed_after_seconds_15",
      note_ko: "머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다.",
    },
    framing_note: "열흘을 「버텨냈다」로 연출하지 않는다.",
  },
});

const SCENE4 = scene(4, {
  title: "금령",
  type: "cinematic",
  scriptureRef: "dan-6:4",
  trigger_warning: {
    level: "mid",
    content: ["religious_persecution", "death_threat"],
    consent_card_id: "daniel_s4_consent",
    covers_scenes: [4],
    skip_alternative_scene_id: 5,
    consent_card_ko: CARD_SCENE4,
    skip_bridge_narration_ko: BRIDGE,
  },
  extras: {
    additional_refs: ["dan-6:7"],
    captions: [
      {
        speaker_ko: "해설",
        text_ko:
          "이에 총리들과 고관들이 국사에 대하여 다니엘을 고발할 근거를 찾고자 하였으나 (단 6:4)",
      },
    ],
    crisis_reminder: CRISIS_S4,
    observe_note:
      "사용자 조작이 없다. 금령 선포는 관찰 대상이지 개입 대상이 아니다.",
    scope_note: "고발 경위는 이 미션의 범위 밖이다.",
  },
});

const CLOSING_PROPOSAL =
  '다니엘은 "안 됩니다"라는 답을 들은 뒤에, 확인해 볼 수 있는 열흘을 제안했습니다. 거절이 대화의 끝은 아니었습니다. 당신이 찾는 방법도 아직 다 나오지 않았을 뿐입니다.';
const CLOSING_DEFAULT =
  "오늘은 아무것도 고르지 않아도 되는 날이었습니다. 선택하지 않는 것도 하나의 자리입니다. 여기까지 온 것으로 이 시간은 끝납니다.";

const CLOSING_TEXTS = {
  direct_refusal: {
    strong: "s-direct",
    balanced: "b-direct",
    soft: "f-direct",
  },
  verifiable_proposal: {
    strong: "s-proposal",
    balanced: CLOSING_PROPOSAL,
    soft: "f-proposal",
  },
  seek_ally: { strong: "s-ally", balanced: "b-ally", soft: "f-ally" },
  default: {
    strong: "s-default",
    balanced: CLOSING_DEFAULT,
    soft: "f-default",
  },
};

const CAPTION_DEN =
  "이에 왕이 명령하매 다니엘을 끌어다가 사자 굴에 던져 넣는지라 (단 6:16)";
const CAPTION_WINDOW =
  "예루살렘으로 향한 창문을 열고 전에 하던 대로 하루 세 번씩 무릎을 꿇고 기도하며 (단 6:10)";

const SCENE5 = scene(5, {
  title: "창문, 그리고 아침",
  type: "outro",
  scriptureRef: "dan-6:10",
  value_prompt:
    "오늘 한 가지만 — 어디까지는 맞출 수 있고 어디서부터는 아닌지, 한 줄로 적어 보는 것.",
  trigger_warning: {
    level: "mid",
    content: ["confinement", "death_threat"],
    consent_card_id: "daniel_s5_consent",
    covers_scenes: [5],
    // 문자열 목적지 — 같은 Scene 의 축약 블록이다. 마지막 씬이라 갈 다음이 없다.
    skip_alternative_scene_id: "daniel_scene5_alt_quiet_window",
    consent_card_ko: CARD_SCENE5,
  },
  extras: {
    captions: [
      { speaker_ko: "해설", text_ko: CAPTION_WINDOW },
      { speaker_ko: "해설", text_ko: CAPTION_DEN },
    ],
    closing_texts: CLOSING_TEXTS,
    crisis_reminder: CRISIS_S5,
    next_scene_suggestion: "시편 121 — 지키시는 이를 부르는 기도",
    closing_texts_note: "어디에도 결과 보장 서술을 두지 않는다.",
    angel_note: "그 존재의 신원을 확정 서술하지 않는다.",
    scope_note: "이 Scene 은 다니엘 6:23 에서 끝난다.",
  },
});

/*
  Scene 5 를 건너뛴 사람이 받는 것. `DecideSceneUseCase.altBlockPayload` 가 같은 Scene 을
  다시 조립한 결과라 **sceneId 가 5 그대로이고 `trigger_warning` 도 그대로 남는다.**
  달라지는 것은 두 가지뿐 — 자막이 축약본으로 덮이고, `conditionalBlockId` 가 박힌다.
  이 픽스처의 요점이 바로 그 두 가지를 정확히 흉내 내는 것이다.
*/
const SCENE5_ALT = scene(5, {
  ...SCENE5.scenePayload,
  conditionalBlockId: "daniel_scene5_alt_quiet_window",
  extras: {
    ...(SCENE5.scenePayload.extras as Record<string, unknown>),
    // 굴 안의 자막 넷이 빠진다. 창문은 남는다 — 부담의 근원은 굴이지 창문이 아니다.
    captions: [{ speaker_ko: "해설", text_ko: CAPTION_WINDOW }],
  },
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <DanielPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(startMission).mockReset();
  vi.mocked(decideMission).mockReset();
  vi.mocked(completeMission).mockReset();
  vi.mocked(completeMission).mockResolvedValue(undefined);
});

describe("다니엘 미션 — 씬 상태 기계", () => {
  it("Scene 1 → 5 → 미션 완료까지 끝까지 걸어간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    // Scene 1 — 카드 없는 씬이라 바로 본문이다.
    expect(await screen.findByText(/벨드사살이라 하고/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(1, "daniel", SESSION, 1, {
      value: "next",
    });

    // Scene 2 — 이 미션의 유일한 선택. 고른 값이 그대로 서버로 간다.
    expect(await screen.findByText(/뜻을 정하여/)).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /확인해 볼 수 있는 방법을 낸다/ }),
    );
    expect(decideMission).toHaveBeenNthCalledWith(2, "daniel", SESSION, 2, {
      value: "verifiable_proposal",
    });

    // Scene 3 — 카드가 없다. 이 인물은 부담 구간이 뒤쪽(4·5)에 몰려 있다.
    expect(await screen.findByText("열흘이 지났다.")).toBeInTheDocument();
    expect(
      screen.getByText("머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // Scene 4 — R4 게이트 ①. 동의 전에는 금령 본문이 뜨지 않는다.
    expect(
      await screen.findByRole("button", { name: DOORS_S4.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/고발할 근거를 찾고자/)).toBeNull();
    // 카드에 적힌 목적지(Scene 5)와 백엔드가 보내 주는 씬이 같아야 한다.
    expect(
      screen.getByText(/건너뛰면 Scene 5 으로 이어집니다/),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: DOORS_S4.continueLabel }),
    );
    expect(await screen.findByText(/고발할 근거를 찾고자/)).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(CRISIS_DEFAULT.tel);
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(4, "daniel", SESSION, 4, {
      value: "next",
    });

    // Scene 5 — R4 게이트 ②. Scene 4 에서 동의했어도 여기서 **다시 묻는다.**
    expect(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(CAPTION_DEN)).toBeNull();

    await user.click(
      screen.getByRole("button", { name: DOORS_S5.continueLabel }),
    );
    expect(await screen.findByText(CAPTION_DEN)).toBeInTheDocument();

    // Scene 2 에서 고른 갈래의 마감 문구가 나온다.
    expect(screen.getByText(CLOSING_PROPOSAL)).toBeInTheDocument();
    expect(screen.getByText(/한 줄로 적어 보는 것/)).toBeInTheDocument();
    expect(
      screen.getByText(/시편 121 — 지키시는 이를 부르는 기도/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith(
      "daniel",
      SESSION,
      "completed",
    );
  });
});

describe("다니엘 미션 — 동의 카드는 둘이고 서로를 상속하지 않는다", () => {
  /*
    이 인물에서 가장 밟기 쉬운 「개선」이 여기다. 앞선 아홉 인물의 화면 규약은
    「카드는 한 번만」이었고, 두 번 뜨는 것을 버그로 읽으면 합치게 된다.
    그러면 금령 *고지* 에 동의한 것이 굴 안 *실행* 에 대한 동의로 승격된다 —
    저작(`card_note`)이 명시로 금지한 바로 그것이다.
  */
  it("Scene 4 에서 동의해도 Scene 5 는 자기 카드로 다시 묻는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S4.continueLabel }),
    );
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // 카드가 또 떠야 한다. 그리고 그 카드는 Scene 4 의 카드가 아니라 Scene 5 의 것이다.
    expect(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(CAPTION_DEN)).toBeNull();
    expect(
      screen.queryByRole("button", { name: DOORS_S4.continueLabel }),
    ).toBeNull();
  });

  it("Scene 4 를 건너뛴 사람도 Scene 5 에서 다시 묻는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S4.skipLabel }),
    );
    expect(decideMission).toHaveBeenCalledWith("daniel", SESSION, 4, {
      value: "skip",
    });

    /*
      건너뛴 사람은 금령이 무엇이었는지 모르는 채 창문 앞에 선다. 저작이 놓아 둔
      다리 넉 줄이 그 사이를 잇는다.

      `normalizer` 를 항등으로 두는 이유: 기본 정규화는 줄바꿈을 공백으로 접는다.
      그러면 네 줄이 한 줄로 붙은 텍스트와 비교하게 되고, 화면이 `whitespace-pre-line`
      을 잃어 줄바꿈이 전부 사라져도 이 단정은 통과한다. 다리는 *줄 나눔까지가 저작* 이다.
    */
    expect(
      await screen.findByText(BRIDGE, { normalizer: (s) => s }),
    ).toBeInTheDocument();
    // 고지를 건너뛴 것이 실행에 대한 동의가 될 수는 더더욱 없다.
    expect(
      screen.getByRole("button", { name: DOORS_S5.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(CAPTION_DEN)).toBeNull();
  });

  it("문 이름은 카드 정본에서 읽는다 — 두 카드의 문 이름이 서로 다르다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    // Scene 5 의 문은 「그대로 보기 / 조용한 장면으로 넘어가기」다.
    // Scene 4 의 「본문 그대로 보기 / 요약만 듣고 넘어가기」로 덮이면
    // 카드에 적힌 말과 버튼에 적힌 말이 달라진다.
    expect(
      await screen.findByRole("button", { name: DOORS_S5.skipLabel }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: DOORS_S4.skipLabel }),
    ).toBeNull();
    expect(
      screen.queryByRole("button", { name: "준비됐어요 · 들어갈게요" }),
    ).toBeNull();
  });

  it("문자열 목적지에는 Scene 번호를 지어내지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    /*
      Scene 5 의 목적지는 `daniel_scene5_alt_quiet_window` — 씬 번호가 아니다.
      `toSceneId` 가 undefined 를 주므로 안내는 목적지 없는 문장으로 떨어져야 한다.
      숫자를 지어내면(예: "Scene 6") 존재하지 않는 씬을 약속하는 카드가 된다.
    */
    expect(
      await screen.findByText(/건너뛰어도 이야기는 이어집니다/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/으로 이어집니다/)).toBeNull();
  });
});

describe("다니엘 미션 — 건너뛴 사람은 건너뛴 채로 남는다", () => {
  /*
    마지막 씬의 건너뛰기는 씬을 바꾸지 않는다. 서버가 같은 sceneId 를 축약 블록으로
    다시 조립해 돌려주고 `trigger_warning` 은 살아서 온다. 동의 여부만 보고 게이트를
    세우면 카드 → 건너뛰기 → 카드 → 건너뛰기 로 갇힌다. 「건너뛰기」를 누른 사람이
    영원히 건너뛰지 못하는, 이 화면에서 가장 조용한 실패다.
  */
  it("Scene 5 를 건너뛰면 카드가 다시 뜨지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.skipLabel }),
    );
    expect(decideMission).toHaveBeenCalledWith("daniel", SESSION, 5, {
      value: "skip",
    });

    // 축약본이 도착했다 — 창문은 남고 굴은 없다.
    expect(await screen.findByText(CAPTION_WINDOW)).toBeInTheDocument();
    expect(screen.queryByText(CAPTION_DEN)).toBeNull();
    // 그리고 카드는 다시 뜨지 않는다.
    expect(
      screen.queryByRole("button", { name: DOORS_S5.skipLabel }),
    ).toBeNull();
    expect(screen.queryByText(/잠깐 — 다음 장면 안내/)).toBeNull();
  });

  it("건너뛴 사람도 마감 문구·위기 안내·미션 완료에 도달한다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.skipLabel }),
    );

    // 축약 블록의 `renders: [closing_texts, crisis_reminder, value_prompt]` 가
    // 약속한 세 가지다. 「건너뛰셔도 마무리는 그대로 받으십니다」가 카드의 약속이었다.
    expect(await screen.findByText(CLOSING_DEFAULT)).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(CRISIS_DEFAULT.tel);
    expect(screen.getByText(/한 줄로 적어 보는 것/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith(
      "daniel",
      SESSION,
      "completed",
    );
  });
});

describe("다니엘 미션 — 고르지 않을 자유", () => {
  it("Scene 2 를 고르지 않고 넘어가면 default 마감 문구가 나간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "고르지 않고 넘어가기" }),
    );
    expect(decideMission).toHaveBeenCalledWith("daniel", SESSION, 2, {
      value: "next",
    });

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    );

    // 무응답은 실패 경로가 아니다. 12갈래 표의 네 번째 행이 그 자리다.
    expect(await screen.findByText(CLOSING_DEFAULT)).toBeInTheDocument();
    expect(screen.queryByText(CLOSING_PROPOSAL)).toBeNull();
  });
});

describe("다니엘 미션 — 저작자용 주석은 화면에 나가지 않는다", () => {
  it("Scene 1 의 name_change_note · npc_note", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    await screen.findByText(/벨드사살이라 하고/);
    // extras 는 payload 로 통째 복사된다 — 가드 주석까지 함께 온다.
    // 그걸 그대로 뿌리면 저작자에게 한 말이 사용자에게 간다.
    expect(screen.queryByText(/개명은 선택지가 아니라 겪는 일이다/)).toBeNull();
    expect(screen.queryByText(/실사 인물 형상을 쓰지 않는다/)).toBeNull();
  });

  it("Scene 5 의 closing_texts_note · angel_note · scope_note", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS_S5.continueLabel }),
    );
    await screen.findByText(CAPTION_DEN);

    expect(screen.queryByText(/결과 보장 서술을 두지 않는다/)).toBeNull();
    expect(screen.queryByText(/신원을 확정 서술하지 않는다/)).toBeNull();
    expect(screen.queryByText(/다니엘 6:23 에서 끝난다/)).toBeNull();
  });
});
