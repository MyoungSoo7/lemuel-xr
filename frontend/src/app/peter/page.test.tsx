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
  베드로 미션 화면 테스트.

  이 화면 고유의 위험은 **고른 것을 기록하면 안 된다는 것** 이다.

  Scene 2 의 세 카드는 본문의 세 부인이고, `options_note` 가
  「이 세 값은 결정으로 기록하지 않고 Scene 5 의 라벨과 상관시키지 않는다」고 못 박아
  두었다. 화면이 `{ value: "deny_knowing" }` 을 보내도 **아무것도 빨개지지 않는다** —
  백엔드는 모르는 값을 그냥 흘리고, 게이트는 payload 를 읽는지까지만 본다. 그러나 그
  순간 「어떻게 부인했는가」가 세션에 남고, 다음 판에서 Scene 5 의 라벨과 이을 수 있는
  자료가 된다. 저작이 금지한 것이 정확히 그것이라 여기서 보내는 값을 직접 잰다.

  두 번째 위험은 **카드가 두 번 뜨는 것** 이다. Scene 2 의 카드는
  `covers_scenes: [2, 3]` 이고 Scene 3 은 `consent_coverage.inherited` 로 덮인다.
  Scene 3 이 자기 카드를 또 띄우면 사용자는 같은 동의를 두 번 받게 되고, 그때부터
  카드는 넘기는 것이 된다 — 그러면 진짜 경고도 안 읽힌다.

  세 번째는 **문 이름의 출처** 다. 정본 카드의 문은 「[이어서 보기] [건너뛰기]」인데
  공용 게이트의 기본 라벨은 「준비됐어요 · 들어갈게요」다. 베껴 적지 않고 정본에서
  뽑아 쓰는지를 잰다(룻이 같은 이유로 같은 검사를 갖고 있다).
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission, completeMission } from "@/lib/api/game";
import PeterPage from "./page";

const SESSION = "sess-peter";

/**
 * 동의 카드 정본 — backend/src/main/resources/scenarios/peter.yml Scene 2 의
 * `consent_card_ko` 를 그대로 옮긴 것. 마지막 줄의 위기 안내만 서버가 치환한 뒤 모양으로 둔다.
 *
 * 문 이름(「이어서 보기」·「건너뛰기」)을 이 파일 어디에도 *따로* 적지 않는다 —
 * 기대값도 이 상수에서 뽑아 쓴다. 따로 적으면 정본이 바뀌어도 한쪽만 고쳐 초록이 된다.
 */
const CARD_SCENE2 = `다음 장면에는 예수를 안다고 하지 않은 밤과, 그 뒤에 남은 마음이 나옵니다.
· 건너뛰어도 이야기는 끝까지 이어집니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
[이어서 보기]  [건너뛰기]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/*
  `peter.yml` 의 `skip_bridge_narration_ko` 정본 네 줄. 줄여 적으면 안 된다 —
  이 픽스처가 잰다고 믿는 것은 "저작이 쓴 다리가 화면까지 온다" 인데, 여기서
  두 줄만 적으면 나머지 두 줄(닭 울음과 시선, 그리고 나감)이 도중에 사라져도
  초록불이다. 건너뛴 사람이 통째로 지나친 구간이 바로 그 두 줄이다.
*/
const BRIDGE = `그 밤, 베드로는 멀찍이 따라갔습니다.
불 곁에서 세 번, 예수를 안다고 하지 않았습니다.
닭이 울었고, 예수께서 돌이켜 그를 보셨습니다.
그리고 그는 밖으로 나갔습니다.`;

const CRISIS_S3 = `지금 마음이 힘들다면, 이야기를 들어줄 곳이 있습니다. ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;
const CRISIS_S5 = `미션이 끝난 뒤에도 이야기를 들어줄 곳이 있습니다. ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

/** 정본에서 두 문의 이름을 뽑는다 — 기대값도 정본에서 파생시키기 위한 것. */
const DOORS = cardDoors(CARD_SCENE2)!;

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
  들어가고 그 밖의 씬 키(trigger_warning·consent_coverage·value_prompt)는 루트에 온다.
  픽스처를 평평하게 적으면 화면이 두 겹을 잘못 읽어도 초록이 된다.
*/
const SCENE1 = scene(1, {
  title: "실패보다 먼저 온 기도",
  type: "cinematic",
  scriptureRef: "lk-22:31",
  exposure_grade: "B",
  extras: {
    anchor: "만찬이 끝나가는 다락방. 등불 하나.",
    additional_refs: ["lk-22:32", "lk-22:33", "lk-22:34"],
    captions: [
      {
        speaker_ko: "예수",
        text_ko:
          "시몬아, 시몬아, 보라 사탄이 너희를 밀 까부르듯 하려고 요구하였으나 (눅 22:31)",
      },
      {
        speaker_ko: "예수",
        text_ko:
          "그러나 내가 너를 위하여 네 믿음이 떨어지지 않기를 기도하였노니 (눅 22:32)",
      },
    ],
    order_note: "이 네 자막의 순서를 바꾸지 않는다.",
  },
});

const SCENE2 = scene(2, {
  title: "불 곁에서, 세 번",
  type: "interaction",
  interaction: "pick_one",
  scriptureRef: "lk-22:54",
  trigger_warning: {
    level: "mid",
    content: ["배신", "수치", "자기정죄"],
    consent_card_id: "peter_s2_consent",
    covers_scenes: [2, 3],
    skip_alternative_scene_id: 4,
    consent_card_ko: CARD_SCENE2,
    skip_bridge_narration_ko: BRIDGE,
  },
  extras: {
    captions: [
      {
        speaker_ko: "베드로",
        text_ko:
          "베드로가 부인하여 이르되 이 여자여 내가 그를 알지 못하노라 하더라 (눅 22:57)",
      },
    ],
    options: [
      {
        id: "deny_knowing",
        label_ko: "나는 그 사람을 모릅니다.",
        selectable: true,
      },
      {
        id: "deny_belonging",
        label_ko: "나는 그 사람들 중 하나가 아닙니다.",
        selectable: true,
      },
      {
        id: "deny_understanding",
        label_ko: "나는 무슨 말인지 모르겠습니다.",
        selectable: false,
        auto_advance: true,
      },
    ],
    options_note: "이 세 값은 결정으로 기록하지 않는다.",
  },
});

const SCENE3 = scene(3, {
  title: "돌이켜 보시니",
  type: "interaction",
  interaction: "contemplative",
  scriptureRef: "lk-22:61",
  consent_coverage: {
    inherited: true,
    consent_card_id: "peter_s2_consent",
    covered_by_scene: 2,
    skip_alternative_scene_id: 4,
  },
  extras: {
    captions: [
      { speaker_ko: "해설", text_ko: "밖에 나가서 심히 통곡하니라 (눅 22:62)" },
    ],
    dwell: {
      min_seconds: 3,
      on_timeout: "auto_proceed_after_seconds_15",
      note_ko: "머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다.",
    },
    crisis_reminder: CRISIS_S3,
    interpretation_note: "이 시선에 뜻을 붙이지 않는다.",
  },
});

const SCENE4 = scene(4, {
  title: "다시 던진 그물",
  type: "interaction",
  interaction: "grab_and_place",
  scriptureRef: "jn-21:1",
  extras: {
    captions: [
      {
        speaker_ko: "해설",
        text_ko:
          "이르시되 그물을 배 오른편에 던지라 그리하면 잡으리라 하시니 (요 21:6)",
      },
    ],
    gesture: {
      id: "net_cast",
      throw_side: "right_fixed",
      on_timeout: "treat_as_complete",
    },
    framing_note: "고기잡이로 돌아간 것을 도피나 배교로 연출하지 않는다.",
    scope_note: "요 21:7-8 은 이 Scene 의 범위 밖이다.",
  },
});

const RECOVERY_UNWORTHY =
  "그 아침에 자격을 묻는 말은 없었습니다. 세 번째 물음까지도, 묻는 분이 그를 부르는 이름은 계속 「요한의 아들 시몬」이었습니다.";
const RECOVERY_DEFAULT =
  "무엇이라고 이름 붙이지 않아도 괜찮습니다. 그 아침에 이름을 부른 분이 부른 것은 어떤 상태가 아니라 한 사람의 이름이었습니다.";

const SCENE5 = scene(5, {
  title: "숯불 앞의 아침",
  type: "outro",
  scriptureRef: "jn-21:9",
  value_prompt:
    "오늘 한 가지만 — 스스로에게 붙인 이름 말고, 불린 이름을 한 번 떠올려 보는 것.",
  extras: {
    captions: [
      {
        speaker_ko: "해설",
        text_ko:
          "육지에 올라보니 숯불이 있는데 그 위에 생선이 놓였고 떡도 있더라 (요 21:9)",
      },
    ],
    options: [
      { id: "unforgivable", label_ko: "돌이킬 수 없다" },
      { id: "unworthy", label_ko: "자격이 없다" },
      { id: "afraid_again", label_ko: "또 그럴까 두렵다" },
    ],
    options_note: "무응답도 정상 경로다.",
    recovery_texts: {
      unforgivable: {
        strong: "s-unforgivable",
        balanced: "b-unforgivable",
        soft: "f-unforgivable",
      },
      unworthy: {
        strong: "s-unworthy",
        balanced: RECOVERY_UNWORTHY,
        soft: "f-unworthy",
      },
      afraid_again: {
        strong: "s-afraid",
        balanced: "b-afraid",
        soft: "f-afraid",
      },
      default: {
        strong: "s-default",
        balanced: RECOVERY_DEFAULT,
        soft: "f-default",
      },
    },
    crisis_reminder: CRISIS_S5,
    next_scene_suggestion: "시편 51 — 자기를 아는 자리에서 드리는 기도",
    timeline_note: "회복에 시간표를 붙이지 않는다.",
    scope_note: "이 Scene 은 요한복음 21:17 에서 끝난다.",
  },
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <PeterPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(startMission).mockReset();
  vi.mocked(decideMission).mockReset();
  vi.mocked(completeMission).mockReset();
  vi.mocked(completeMission).mockResolvedValue(undefined);
});

describe("베드로 미션 — 씬 상태 기계", () => {
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
    expect(await screen.findByText(/밀 까부르듯 하려고/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(1, "peter", SESSION, 1, {
      value: "next",
    });

    // Scene 2 — 카드가 먼저다. 본문 자막은 동의 전에는 뜨지 않는다.
    expect(
      await screen.findByRole("button", { name: DOORS.continueLabel }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/내가 그를 알지 못하노라/)).toBeNull();

    await user.click(screen.getByRole("button", { name: DOORS.continueLabel }));
    expect(
      await screen.findByText(/내가 그를 알지 못하노라/),
    ).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: "나는 그 사람을 모릅니다." }),
    );
    expect(decideMission).toHaveBeenNthCalledWith(2, "peter", SESSION, 2, {
      value: "next",
    });

    // Scene 3 — Scene 2 의 카드에 덮인 씬이라 게이트 없이 바로 본문이다.
    expect(await screen.findByText(/심히 통곡하니라/)).toBeInTheDocument();
    expect(
      screen.getByText("머무는 시간에 정답이 없습니다. 먼저 나가셔도 됩니다."),
    ).toBeInTheDocument();
    // 정서적 정점 — 씬이 선언한 위기 안내가 화면까지 온다.
    expect(screen.getByRole("note")).toHaveTextContent(CRISIS_DEFAULT.tel);
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(3, "peter", SESSION, 3, {
      value: "next",
    });

    // Scene 4 — 그물을 던지지 않아도 넘어간다(on_timeout: treat_as_complete).
    expect(await screen.findByText(/배 오른편에 던지라/)).toBeInTheDocument();
    await user.click(
      screen.getByRole("button", { name: /그물을 오른편에 던진다/ }),
    );
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(4, "peter", SESSION, 4, {
      value: "next",
    });

    // Scene 5 outro — 라벨을 고르면 그 갈래의 마감 문구.
    expect(await screen.findByText(/숯불이 있는데/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "자격이 없다" }));
    expect(await screen.findByText(RECOVERY_UNWORTHY)).toBeInTheDocument();
    expect(screen.getByText(/불린 이름을 한 번 떠올려/)).toBeInTheDocument();
    expect(
      screen.getByText(/시편 51 — 자기를 아는 자리에서 드리는 기도/),
    ).toBeInTheDocument();
    expect(screen.getByRole("note")).toHaveTextContent(CRISIS_DEFAULT.tel);

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith("peter", SESSION, "completed");
  });
});

describe("베드로 미션 — 부인은 기록되지 않는다", () => {
  it("어느 카드를 골라도 서버로 가는 값은 같다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS.continueLabel }),
    );
    await user.click(
      screen.getByRole("button", {
        name: "나는 그 사람들 중 하나가 아닙니다.",
      }),
    );

    /*
      `{ value: "deny_belonging" }` 을 보내도 화면은 똑같이 돌고 어떤 게이트도
      빨개지지 않는다. 그러나 그 값이 세션에 남는 순간 「어떻게 부인했는가」가
      Scene 5 의 라벨과 이어 붙일 수 있는 자료가 된다 — `options_note` 가 금지한 것.
    */
    expect(decideMission).toHaveBeenCalledWith("peter", SESSION, 2, {
      value: "next",
    });
    expect(decideMission).not.toHaveBeenCalledWith(
      "peter",
      SESSION,
      2,
      expect.objectContaining({ value: "deny_belonging" }),
    );
  });

  it("세 번째 부인은 고를 수 있는 것처럼 두지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: DOORS.continueLabel }),
    );

    // `selectable: false` — 본문에는 세 번째 부인도 일어났다. 자리는 보이되
    // 손잡이는 없다. 버튼으로 두면 「고르지 않을 수도 있었다」가 되고, 그건
    // 본문에도 없고 이 미션의 축(실패는 이미 일어났다)에도 반대다.
    expect(
      screen.getByText("나는 무슨 말인지 모르겠습니다."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "나는 무슨 말인지 모르겠습니다." }),
    ).toBeNull();
  });
});

describe("베드로 미션 — 동의 카드는 한 번만", () => {
  it("Scene 3 은 자기 카드를 띄우지 않는다 (Scene 2 의 카드에 덮인다)", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    // 같은 동의를 두 번 받으면 카드는 습관적으로 넘기는 것이 되고,
    // 그때부터 진짜 경고도 안 읽힌다.
    expect(await screen.findByText(/심히 통곡하니라/)).toBeInTheDocument();
    expect(screen.queryByText(/잠깐 — 다음 장면 안내/)).toBeNull();
    expect(screen.queryByRole("button", { name: DOORS.skipLabel })).toBeNull();
  });

  it("둘째 문은 skip 을 보내고 저작된 다리 한 줄을 띄운다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockResolvedValue(SCENE4);
    renderPage();

    // 카드에 적힌 목적지(Scene 4)와 백엔드가 보내 주는 씬이 같아야 한다.
    expect(
      await screen.findByText(/건너뛰면 Scene 4 으로 이어집니다/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: DOORS.skipLabel }));
    expect(decideMission).toHaveBeenCalledWith("peter", SESSION, 2, {
      value: "skip",
    });

    /*
      이 카드는 Scene 2·3 을 함께 덮고 목적지가 4 다. 건너뛴 사람은 부인의 밤과
      돌이켜 보시는 시선을 통째로 지나치므로, 저작된 다리 한 줄이 그 사이를 잇는다.
      이 줄을 안 읽으면 건너뛰기가 「이야기를 잃는 일」이 된다.
    */
    /*
      `normalizer` 를 항등으로 두는 이유: 기본 정규화는 줄바꿈을 공백으로 접는다.
      그러면 네 줄이 한 줄로 붙은 텍스트와 비교하게 되고, 화면이 `whitespace-pre-line`
      을 잃어 줄바꿈이 전부 사라져도 이 단정은 통과한다. 다리 한 줄은 *줄 나눔까지가
      저작* 이라 그 손실을 재야 한다.
    */
    expect(
      await screen.findByText(BRIDGE, { normalizer: (s) => s }),
    ).toBeInTheDocument();
  });

  it("문 이름은 카드 정본에서 읽는다 — 공용 기본 라벨로 덮지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    renderPage();

    expect(
      await screen.findByRole("button", { name: DOORS.continueLabel }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: DOORS.skipLabel }),
    ).toBeInTheDocument();
    // 게이트 기본 라벨이 정본을 덮으면 카드에 적힌 말과 버튼에 적힌 말이 달라진다.
    expect(
      screen.queryByRole("button", { name: "준비됐어요 · 들어갈게요" }),
    ).toBeNull();
  });
});

describe("베드로 미션 — 고르지 않을 자유", () => {
  it("라벨을 고르지 않으면 default 마감 문구가 나간다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    // 무응답은 실패 경로가 아니다. 고르라고 막아 세우지 않는다.
    expect(await screen.findByText(RECOVERY_DEFAULT)).toBeInTheDocument();
    expect(screen.getByText("고르지 않아도 됩니다.")).toBeInTheDocument();
    expect(screen.queryByText(RECOVERY_UNWORTHY)).toBeNull();
  });

  it("고른 라벨을 다시 누르면 해제되고 default 로 돌아간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    const label = await screen.findByRole("button", { name: /자격이 없다/ });
    await user.click(label);
    expect(await screen.findByText(RECOVERY_UNWORTHY)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /자격이 없다/ }));
    expect(await screen.findByText(RECOVERY_DEFAULT)).toBeInTheDocument();
  });
});

describe("베드로 미션 — 저작자용 주석은 화면에 나가지 않는다", () => {
  it("*_note 필드를 사용자 카피로 렌더하지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    await screen.findByText(/밀 까부르듯 하려고/);
    // extras 는 payload 로 통째 복사된다 — 가드 주석까지 함께 온다.
    // 그걸 그대로 뿌리면 저작자에게 한 말이 사용자에게 간다.
    expect(screen.queryByText(/이 네 자막의 순서를 바꾸지 않는다/)).toBeNull();
  });

  it("Scene 4 의 framing_note · scope_note 도 마찬가지다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    renderPage();

    await screen.findByText(/배 오른편에 던지라/);
    expect(screen.queryByText(/도피나 배교로 연출하지 않는다/)).toBeNull();
    expect(
      screen.queryByText(/요 21:7-8 은 이 Scene 의 범위 밖이다/),
    ).toBeNull();
  });
});
