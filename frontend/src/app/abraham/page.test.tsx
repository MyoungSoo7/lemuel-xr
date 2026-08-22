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
  아브라함 미션 화면 테스트.

  이 화면 고유의 위험 셋을 잰다.

  ① **Scene 5 건너뛰기가 무한 루프가 되는 것.** 동의 카드가 하나뿐인데 그게 마지막
     씬에 붙어 있어서, 건너뛰기 목적지가 정수(다음 씬 점프)일 수 없다. 서버는 같은
     sceneId 를 축약 블록으로 다시 조립해 돌려주고(`altBlockPayload`) 그 payload 에는
     `trigger_warning` 이 **그대로 살아 있다.** 동의 여부만 보고 게이트를 세우면
     「건너뛰기」를 누른 사람 앞에 같은 카드가 다시 뜬다 — 영원히 건너뛰지 못한다.

  ② **라벨 없이 도착한 사람이 마감 문구 없이 끝나는 것.** Scene 3 은 고르지 않고
     넘어가는 것이 정상 경로이고(20초 타임아웃도 같은 자리로 온다), 그 사람이 가장
     취약하다. `wait_label: null` 전용 폴백이 화면까지 오는지를 잰다. 폴백이 안 잡히면
     아무 게이트도 빨개지지 않고, 화면은 그냥 조용히 빈 채로 끝난다.

  ③ **안전 잠금 자막 두 줄이 화면에서 잘리는 것.** Scene 3 의
     「이 이유는 아브람에 관한 것이 아니었다.」와 Scene 4 의 「이 기한은 사라에게
     주어진 기한이다.」는 이 미션의 R2 통제 그 자체다. 둘 다 그냥 자막이라
     **백엔드에는 이를 지키는 검사가 없다.** 화면이 자막을 골라 렌더하도록 「개선」되면
     가장 먼저 잘리는 것이 이 두 줄이다. 여기서 재는 것은 그 절반 — 화면이 자막을
     통째로 렌더한다 — 이고, yml 에서 그 줄이 사라지는 경우는 여전히 사람의 몫이다.
     (`docs/ABRAHAM-RUNTIME-SIGNOFF.md` §2 에 그 한계를 적어 두었다.)
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission } from "@/lib/api/game";
import AbrahamPage from "./page";

const SESSION = "sess-abraham";

/**
 * 동의 카드 정본 — `backend/src/main/resources/scenarios/abraham.yml` Scene 5 의
 * `consent_card_ko` 를 그대로 옮긴 것. 마지막 줄의 위기 안내만 서버가 치환한 뒤 모양으로 둔다.
 * 문 이름을 이 파일에 *따로* 적지 않는다 — 기대값도 이 상수에서 뽑아 쓴다.
 */
const CARD_SCENE5 = `다음 장면에는 오랜 기다림 끝의 출산 이야기가 나옵니다.
· 약 1분입니다. 지금 보지 않으셔도 이야기는 온전히 끝맺어집니다.
· 어느 지점에서든 멈추거나 나갈 수 있습니다.
[계속한다]  [건너뛰기 — 웃음 자막 한 장과 마무리로 이동]
음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
지금 힘드시면: ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

const DOORS_S5 = cardDoors(CARD_SCENE5)!;

/** 이 미션의 R2 안전 잠금 두 줄 — 정본 자구 그대로. */
const LOCK_S3 = "이 이유는 아브람에 관한 것이 아니었다.";
const LOCK_S4 = "이 기한은 사라에게 주어진 기한이다.";

/** Scene 5 마감 문구 — `wait_label` 이 없는 사람이 받아야 하는 폴백. */
const CLOSING_FALLBACK =
  "아브라함은 오래 기다렸다. 이 이야기는 그 기다림을 지우지 않고 그대로 두었다.";
const CLOSING_UNKNOWING =
  "사백 년의 이유는 다른 곳에 있었다. 아브라함은 그 이유를 끝내 다 알지 못한 채로 살았다.";

const CRISIS_S5 = `지금 힘드시면 ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}`;

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
  들어가고 그 밖의 씬 키는 루트에 온다. 픽스처를 평평하게 적으면 화면이 두 겹을
  잘못 읽어도 초록이 된다.
*/
const SCENE3 = scene(3, {
  title: "별, 그리고 사백 년",
  type: "interaction",
  interaction: "pick_one",
  scriptureRef: "gen-15:5",
  extras: {
    anchor: "달 없는 밤. 열린 들판과 별.",
    captions: [
      { speaker_ko: "여호와의 음성", text_ko: "네 자손이 이와 같으리라 (창 15:5)" },
      { speaker_ko: "해설", text_ko: LOCK_S3 },
    ],
    options: [
      { id: "voice_the_lack", label_ko: "나는 자식이 없습니다", ref: "창 15:2" },
      { id: "hold_the_promise", label_ko: "그래도 그 약속을 붙듭니다", ref: "창 15:6" },
      {
        id: "sit_with_unknowing",
        label_ko: "왜인지 모른 채로 있겠습니다",
        ref: "창 15:16",
      },
    ],
    decision_key: "wait_label",
  },
});

const SCENE4 = scene(4, {
  title: "웃음",
  type: "cinematic",
  scriptureRef: "gen-17:17",
  extras: {
    captions: [
      { speaker_ko: "해설", text_ko: "사라에게 아들이 있으리라 (창 18:14)" },
      { speaker_ko: "해설", text_ko: LOCK_S4 },
    ],
  },
});

/** Scene 5 의 마감 문구 표 — 네 갈래 전부. 화면은 여기서 골라 온다. */
const CLOSING_BRANCH = {
  id: "br_s5_closing_message",
  decision_key: "closing_key",
  routes: [
    {
      when: { wait_label: "voice_the_lack" },
      static_text_ko: "아브람은 자기에게 없는 것을 그대로 말했다.",
    },
    {
      when: { wait_label: "hold_the_promise" },
      static_text_ko: "아브람은 믿었고, 그 뒤로도 오래 기다렸다.",
    },
    { when: { wait_label: "sit_with_unknowing" }, static_text_ko: CLOSING_UNKNOWING },
    { when: { wait_label: null }, static_text_ko: CLOSING_FALLBACK },
  ],
};

const SCENE5 = scene(5, {
  title: "이삭",
  type: "outro",
  scriptureRef: "gen-21:6",
  next: null,
  trigger_warning: {
    level: "low_mid",
    content: ["childbirth", "aging"],
    consent_card_id: "abraham_scene5_birth_warning",
    covers_scenes: [5],
    skip_alternative_scene_id: "abraham_scene5_alt_laughter_only",
    consent_card_ko: CARD_SCENE5,
  },
  extras: {
    captions: [
      { speaker_ko: "해설", text_ko: "노년의 아브라함에게 아들을 낳으니 (창 21:2)" },
      { speaker_ko: "사라", text_ko: "하나님이 나를 웃게 하시니 (창 21:6)" },
    ],
    additional_refs: ["gen-21:1", "gen-21:2"],
    crisis_reminder: { text_ko: CRISIS_S5, position: "above_closing_text" },
    branches: [CLOSING_BRANCH],
  },
});

/** 건너뛴 사람이 받는 축약 payload — 서버가 `conditionalBlockId` 를 박아 준다. */
const SCENE5_ALT = scene(5, {
  ...SCENE5.scenePayload,
  conditionalBlockId: "abraham_scene5_alt_laughter_only",
  extras: {
    ...(SCENE5.scenePayload.extras as Record<string, unknown>),
    captions: [{ speaker_ko: "사라", text_ko: "하나님이 나를 웃게 하시니 (창 21:6)" }],
    additional_refs: [],
  },
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <AbrahamPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("아브라함 — Scene 5 동의 카드", () => {
  it("동의 전에는 출산 자막을 렌더하지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    expect(await screen.findByText(DOORS_S5.continueLabel!)).toBeInTheDocument();
    expect(screen.queryByText(/노년의 아브라함에게 아들을 낳으니/)).toBeNull();
  });

  it("이미 건너뛴 사람에게 같은 카드를 다시 세우지 않는다", async () => {
    // ① 축약 payload 에도 trigger_warning 이 살아 있다. `conditionalBlockId` 를 보지
    //    않으면 여기서 카드가 다시 뜨고, 건너뛰기가 무한 루프가 된다.
    vi.mocked(startMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    expect(await screen.findByText(/하나님이 나를 웃게 하시니/)).toBeInTheDocument();
    expect(screen.queryByText(DOORS_S5.continueLabel!)).toBeNull();
  });

  it("건너뛰기는 이야기를 끝내지 않고 서버에 skip 을 보낸다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await userEvent.click(await screen.findByText(DOORS_S5.skipLabel!));

    expect(decideMission).toHaveBeenCalledWith(
      "abraham",
      SESSION,
      5,
      { value: "skip" },
    );
  });
});

describe("아브라함 — 마감 문구", () => {
  it("고르지 않고 온 사람도 마감 문구를 받는다", async () => {
    // ② wait_label 이 null 인 경로. 이 폴백이 없으면 화면이 조용히 빈 채로 끝난다.
    vi.mocked(startMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    expect(await screen.findByText(CLOSING_FALLBACK)).toBeInTheDocument();
  });

  it("Scene 3 에서 고른 라벨이 마감 문구를 고른다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await userEvent.click(await screen.findByText("왜인지 모른 채로 있겠습니다"));

    expect(decideMission).toHaveBeenCalledWith("abraham", SESSION, 3, {
      value: "sit_with_unknowing",
    });
    expect(await screen.findByText(CLOSING_UNKNOWING)).toBeInTheDocument();
  });

  it("고르지 않고 넘어가기는 라벨을 남기지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    vi.mocked(decideMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    await userEvent.click(await screen.findByText("고르지 않고 넘어가기"));

    expect(decideMission).toHaveBeenCalledWith("abraham", SESSION, 3, {
      value: "next",
    });
    expect(await screen.findByText(CLOSING_FALLBACK)).toBeInTheDocument();
  });
});

describe("아브라함 — R2 안전 잠금 자막", () => {
  // ③ 백엔드에 이 두 줄을 지키는 검사가 없다. 화면이 자막을 골라 렌더하게 되면
  //    가장 먼저 잘리는 것이 이것이다.
  it("Scene 3 — 사백 년의 이유가 아브람에 관한 것이 아님을 화면에 남긴다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    expect(await screen.findByText(LOCK_S3)).toBeInTheDocument();
  });

  it("Scene 4 — 기한이 사라에게 주어진 것임을 화면에 남긴다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    renderPage();

    expect(await screen.findByText(LOCK_S4)).toBeInTheDocument();
  });
});

describe("아브라함 — 위기 안내", () => {
  it("객체 형태의 crisis_reminder 도 화면까지 온다", async () => {
    // 저작이 `{ text_ko, position }` 객체로 두었다(다니엘은 문자열이었다).
    // 문자열만 가정하면 이 인물에서 위기 안내가 조용히 사라진다.
    vi.mocked(startMission).mockResolvedValue(SCENE5_ALT);
    renderPage();

    expect(await screen.findByText(CRISIS_S5)).toBeInTheDocument();
  });
});
