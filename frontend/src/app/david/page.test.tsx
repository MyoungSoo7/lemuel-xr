import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
// 번호를 픽스처에 다시 적지 않는다. 이 문자열은 백엔드가 yml 의
// `{{crisis_resources.default}}` 를 치환해 내려보내는 값을 흉내내는 것이라,
// 정본에서 파생시키는 쪽이 실제 동작에도 더 가깝다.
// (`scripts/check_frontend_hotline.py`)
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";

// 백엔드 왕복만 막는다. 공용 컴포넌트(TriggerWarningGate/SceneBootState/
// NarrationAudioButton)와 david-monologues 는 **실물** 로 렌더한다 — 배선이 맞는지가
// 이 파일이 재려는 것이라 모킹하면 잴 것이 없어진다.
vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import {
  completeMission,
  decideMission,
  startMission,
  type JosephStartResponse,
} from "@/lib/api/game";
import {
  scene1Psalm23,
  scene2Reactions,
  scene4LastStone,
  scene5Monologue,
  scene6OutroByLastStone,
} from "@/lib/content/david-monologues";
import DavidPage from "./page";

const startMock = vi.mocked(startMission);
const decideMock = vi.mocked(decideMission);
const completeMock = vi.mocked(completeMission);

/**
 * 픽스처는 backend 가 실제로 조립하는 payload 모양을 따른다:
 * ScenarioYamlLoader 가 씬의 `extras:` 블록과 `trigger_warning:` 을 Scene.extras 로 모으고,
 * ScenePayloadAssembler 가 그것을 payload 최상위로 펼친다. 즉 화면이 받는 것은
 *   { sceneId, title, type, interaction, next, extras: {…}, trigger_warning: {…} }
 * 이다. 값은 david.yml 에서 그대로 옮겨 왔다 — 픽스처가 실제와 다르면 게이트 테스트가
 * 초록이어도 운영에서는 카드가 안 뜬다. 그게 2026-08-12 사고의 형태였다.
 */
const PAYLOADS: Record<number, Record<string, unknown>> = {
  1: {
    sceneId: 1,
    title: "양 떼와 시편",
    type: "interaction",
    interaction: "contemplative",
    next: 2,
    extras: {
      anchor: "평온·자기 존재감",
      activity: "lyre_strings",
      skippable_after_sec: 3,
    },
  },
  2: {
    sceneId: 2,
    title: "형들의 비웃음",
    type: "interaction",
    interaction: "pick_one",
    next: 3,
    extras: {
      anchor: "모욕감·내적 분노",
      options: [
        { id: "rebut", label: "반박한다" },
        { id: "silence", label: "침묵한다" },
        { id: "walk_past", label: "전선 쪽으로 걸어간다" },
      ],
    },
  },
  3: {
    sceneId: 3,
    title: "사울의 갑옷",
    type: "interaction",
    interaction: "gesture_sequence",
    next: 4,
    extras: {
      anchor: "자기 정체성·해방",
      steps: [
        { id: "wear", label: "갑옷을 입어본다" },
        { id: "walk", label: "걸어본다 (비틀거림)" },
        { id: "remove", label: "벗는다" },
      ],
    },
  },
  4: {
    sceneId: 4,
    title: "시냇가의 5돌",
    type: "interaction",
    interaction: "distribute",
    next: 5,
    extras: {
      anchor: "두려움+신뢰 통합",
      stones: [
        { id: "fear", label: "공포" },
        { id: "humiliation", label: "모욕" },
        { id: "loneliness", label: "외로움" },
        { id: "trust", label: "신뢰" },
        { id: "prayer", label: "기도" },
      ],
    },
  },
  5: {
    sceneId: 5,
    title: "골리앗",
    type: "interaction",
    interaction: "two_handed_throw",
    next: 6,
    extras: {
      anchor: "떨림 → 일어섬",
      // yml 이 "DEAD FLAG" 라고 적어 둔 레거시 boolean. 픽스처에 일부러 남긴다 —
      // 이게 살아 있는 채로도 게이트가 trigger_warning 으로만 움직이는지 재기 위해서다.
      violence_warning: true,
    },
    trigger_warning: {
      level: "medium",
      content: ["violence"],
      consent_card_id: "david_scene5_goliath_warning",
      skip_alternative_scene_id: 6,
    },
  },
  6: {
    sceneId: 6,
    title: "회복",
    type: "outro",
    next: null,
    extras: {
      anchor: "자기 작음의 평화",
      crisis_reminder: `지금 이 순간이 무겁다면, ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}.`,
    },
    value_prompt:
      "다윗의 시편은 그의 일기장이었습니다. 오늘 솔직한 한 줄을 적어보세요.",
  },
};

function sceneResponse(
  n: number,
  overrides: Partial<JosephStartResponse> = {},
): JosephStartResponse {
  return {
    sessionId: "sess-david-1",
    userId: "guest-1",
    currentScene: n,
    scenePayload: PAYLOADS[n],
    responseText: null,
    ...overrides,
  };
}

function customScene(
  n: number,
  scenePayload: Record<string, unknown>,
): JosephStartResponse {
  return {
    sessionId: "sess-david-1",
    userId: "guest-1",
    currentScene: n,
    scenePayload,
    responseText: null,
  };
}

/**
 * decide(sceneId) 의 응답을 *씬 번호로* 지정한다. 기본은 yml 의 next 를 흉내 낸 sceneId+1.
 * (mockImplementationOnce 는 "몇 번째 호출" 에 묶여서 걸어가는 경로가 바뀌면 엉뚱한 씬을 덮는다.)
 */
function decideReturns(overrides: Record<number, JosephStartResponse> = {}) {
  decideMock.mockImplementation(
    async (_c, _s, sceneId) => overrides[sceneId] ?? sceneResponse(sceneId + 1),
  );
}

function deferred<T>() {
  let resolve!: (v: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

/**
 * 모놀로그·아웃트로는 줄바꿈이 든 긴 문단이라 기본 문자열 매칭이 공백 정규화에 걸린다.
 * 문단 하나의 전체 텍스트가 정확히 그 상수인지로 잰다 — 부분 유출을 놓치지 않으려고
 * 부분 일치가 아니라 완전 일치를 쓴다.
 */
const squash = (s: string) => s.replace(/\s+/g, " ").trim();
const paragraphMatcher =
  (expected: string) => (_content: string, el: Element | null) =>
    el?.tagName === "P" && squash(el.textContent ?? "") === squash(expected);
const findParagraph = (expected: string) =>
  screen.findByText(paragraphMatcher(expected));
const queryParagraph = (expected: string) =>
  screen.queryByText(paragraphMatcher(expected));

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const user = userEvent.setup({ delay: null });
  render(
    <QueryClientProvider client={client}>
      <DavidPage />
    </QueryClientProvider>,
  );
  return { user };
}

// completeMission 성공 후 `location.href = "/"`. jsdom 은 네비게이션을 구현하지 않으므로
// 대입만 가로채 "홈으로 보내려 했는가" 를 검증한다.
let navigatedTo: string | null = null;
Object.defineProperty(window, "location", {
  configurable: true,
  value: {
    get href() {
      return "http://localhost/david";
    },
    set href(v: string) {
      navigatedTo = v;
    },
  },
});

beforeEach(() => {
  navigatedTo = null;
  startMock.mockReset();
  decideMock.mockReset();
  completeMock.mockReset();
  startMock.mockResolvedValue(sceneResponse(1));
  decideReturns();
  completeMock.mockResolvedValue(undefined);
});

type User = ReturnType<typeof userEvent.setup>;

/** Scene 1 → 2 */
async function advanceToScene2(user: User) {
  await user.click(await screen.findByRole("button", { name: /계속/ }));
}

/** Scene 2 에서 한 반응을 골라 3으로. */
async function advanceToScene3(user: User, option: RegExp = /침묵한다/) {
  await advanceToScene2(user);
  await user.click(await screen.findByRole("button", { name: option }));
}

/** Scene 3 갑옷 3동작 → 4 */
async function advanceToScene4(user: User) {
  await advanceToScene3(user);
  await user.click(
    await screen.findByRole("button", { name: /갑옷을 입어본다/ }),
  );
  await user.click(screen.getByRole("button", { name: /걸어본다/ }));
  await user.click(screen.getByRole("button", { name: /벗는다/ }));
}

/** Scene 4 에서 다섯 돌을 지정한 순서로 집고 제출 → 5. 기본 순서의 마지막은 공포. */
async function advanceToScene5(
  user: User,
  order: RegExp[] = [/기도/, /신뢰/, /외로움/, /모욕/, /공포/],
) {
  await advanceToScene4(user);
  for (const stone of order) {
    await user.click(await screen.findByRole("button", { name: stone }));
  }
  await user.click(
    screen.getByRole("button", { name: /다섯 돌을 들고 나아간다/ }),
  );
}

describe("David 미션 화면", () => {
  describe("세션 부팅", () => {
    it("start 가 실패하면 빈 화면 대신 실패 안내와 재시도를 준다", async () => {
      startMock.mockRejectedValueOnce({ response: { status: 500 } });
      const { user } = renderPage();

      expect(
        await screen.findByText("세션을 시작하지 못했습니다."),
      ).toBeInTheDocument();
      expect(screen.getByText(/오류 코드 500/)).toBeInTheDocument();

      startMock.mockResolvedValueOnce(sceneResponse(1));
      await user.click(screen.getByRole("button", { name: "다시 시도" }));

      expect(
        await screen.findByRole("heading", { name: "양 떼와 시편" }),
      ).toBeInTheDocument();
      expect(startMock).toHaveBeenCalledWith("david", "web");
      expect(startMock).toHaveBeenCalledTimes(2);
    });

    it("401 은 만료로 구분해 안내한다", async () => {
      startMock.mockRejectedValue({ response: { status: 403 } });
      renderPage();

      expect(
        await screen.findByText("세션이 만료됐습니다. 다시 시작해 주세요."),
      ).toBeInTheDocument();
    });

    it("응답 전에는 로딩 문구를 보여준다", async () => {
      const gate = deferred<JosephStartResponse>();
      startMock.mockReturnValueOnce(gate.promise);
      renderPage();

      expect(await screen.findByText("세션 시작 중...")).toBeInTheDocument();
      gate.resolve(sceneResponse(1));
      expect(
        await screen.findByRole("heading", { name: "양 떼와 시편" }),
      ).toBeInTheDocument();
    });
  });

  describe("R4 — trigger_warning 동의 게이트 (2026-08-12 회귀 방지)", () => {
    it("동의 전에는 골리앗 씬의 던지기 상호작용이 렌더되지 않는다", async () => {
      const { user } = renderPage();
      await advanceToScene5(user);

      expect(
        await screen.findByText("잠깐 — 다음 장면 안내"),
      ).toBeInTheDocument();
      // payload 의 값들이 그대로 화면에 나와야 한다 — yml 을 고치면 화면이 따라온다는 뜻.
      expect(screen.getByText("폭력")).toBeInTheDocument();
      expect(screen.getByText(/정서 강도: 중간/)).toBeInTheDocument();
      expect(
        screen.getByText(/건너뛰면 Scene 6 으로 이어집니다/),
      ).toBeInTheDocument();
      expect(screen.getByText(/건너뛰어도 괜찮습니다/)).toBeInTheDocument();

      expect(
        screen.queryByRole("button", { name: /물맷돌을 던진다/ }),
      ).not.toBeInTheDocument();
    });

    it("동의해야 던지기가 열리고, 동의 자체는 서버로 나가지 않는다", async () => {
      const { user } = renderPage();
      await advanceToScene5(user);

      const before = decideMock.mock.calls.length;
      await user.click(
        await screen.findByRole("button", { name: /준비됐어요 · 들어갈게요/ }),
      );

      expect(
        await screen.findByRole("button", { name: /물맷돌을 던진다/ }),
      ).toBeInTheDocument();
      expect(screen.queryByText("잠깐 — 다음 장면 안내")).toBeNull();
      expect(decideMock.mock.calls.length).toBe(before);
    });

    it("건너뛰면 skip 결정이 나가고 골리앗 묘사는 한 줄도 노출되지 않는다", async () => {
      const { user } = renderPage();
      await advanceToScene5(user);

      await user.click(
        await screen.findByRole("button", { name: /건너뛸게요/ }),
      );

      expect(decideMock).toHaveBeenLastCalledWith("david", "sess-david-1", 5, {
        value: "skip",
      });
      expect(
        await screen.findByRole("heading", { name: "회복" }),
      ).toBeInTheDocument();
      // 건너뛴 사람에게 물맷돌 모놀로그가 따라오면 게이트가 무의미해진다.
      expect(queryParagraph(scene5Monologue.throw)).toBeNull();
      expect(screen.queryByText(/돌은 네 손을 떠났다/)).toBeNull();
    });

    it("레거시 violence_warning 플래그만으로는 게이트가 뜨지 않는다", async () => {
      // 2026-08-12 이전 배선은 이 boolean 하나로 카드를 띄웠다. 그래서 yml 의
      // trigger_warning(level·content·skip 목적지) 을 고쳐도 화면이 안 바뀌었다.
      // 플래그가 죽었다는 것을 *실행으로* 확인한다 — yml 주석의 주장만으로는 부족하다.
      const legacyOnly = {
        ...PAYLOADS[5],
        extras: { ...(PAYLOADS[5].extras as object), violence_warning: true },
      };
      delete (legacyOnly as Record<string, unknown>).trigger_warning;
      decideReturns({ 4: customScene(5, legacyOnly) });
      const { user } = renderPage();
      await advanceToScene5(user);

      expect(
        await screen.findByRole("button", { name: /물맷돌을 던진다/ }),
      ).toBeInTheDocument();
      expect(screen.queryByText("잠깐 — 다음 장면 안내")).toBeNull();
    });

    it("게이트는 씬 타입이 아니라 payload 가 연다 — 경고 붙은 pick_one 도 닫힌다", async () => {
      // 조건이 `sceneType === "two_handed_throw"` 로 되돌아가면 여기서 깨진다.
      decideReturns({
        1: customScene(2, {
          ...PAYLOADS[2],
          trigger_warning: {
            level: "high",
            content: ["family_trauma", "isolation"],
            skip_alternative_scene_id: 3,
          },
        }),
      });
      const { user } = renderPage();
      await advanceToScene2(user);

      expect(
        await screen.findByText("잠깐 — 다음 장면 안내"),
      ).toBeInTheDocument();
      expect(screen.getByText("가족 관계의 상처")).toBeInTheDocument();
      expect(screen.getByText("고립")).toBeInTheDocument();
      expect(screen.getByText(/정서 강도: 높음/)).toBeInTheDocument();
      // 형의 비웃음 선택지가 동의 전에 보이면 안 된다.
      expect(screen.queryByRole("button", { name: "침묵한다" })).toBeNull();
    });

    it("동의는 씬마다 초기화된다 — 다음 씬에 경고가 있으면 다시 묻는다", async () => {
      decideReturns({
        3: customScene(4, {
          ...PAYLOADS[4],
          trigger_warning: { level: "low", content: ["despair"] },
        }),
      });
      const { user } = renderPage();
      await advanceToScene4(user);

      expect(
        await screen.findByText("잠깐 — 다음 장면 안내"),
      ).toBeInTheDocument();
      expect(screen.getByText("절망")).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /공포/ })).toBeNull();

      // 동의 후 열린 뒤, 다시 경고 없는 씬으로 가면 게이트는 없어야 한다.
      await user.click(
        screen.getByRole("button", { name: /준비됐어요 · 들어갈게요/ }),
      );
      expect(
        await screen.findByRole("button", { name: /공포/ }),
      ).toBeInTheDocument();
    });

    it("yml 정본(consent_card_ko)이 오면 화면 문구 대신 그것을 렌더한다", async () => {
      decideReturns({
        4: customScene(5, {
          ...PAYLOADS[5],
          trigger_warning: {
            ...(PAYLOADS[5].trigger_warning as Record<string, unknown>),
            consent_card_ko:
              "다음 장면에는 거인 앞의 대치가 있습니다.\n[계속한다] [건너뛰기 → Scene 6]\n음성/자막 강도: 보통",
          },
        }),
      });
      const { user } = renderPage();
      await advanceToScene5(user);

      expect(
        await screen.findByText(/다음 장면에는 거인 앞의 대치가 있습니다\./),
      ).toBeInTheDocument();
      // UI 지시 줄은 산문에서 걷어내고 실제 버튼으로만 남긴다.
      expect(screen.queryByText(/\[계속한다\]/)).toBeNull();
      expect(screen.queryByText(/음성\/자막 강도/)).toBeNull();
      // 정본이 있으면 화면 소유 fallback 문구는 쓰이지 않는다.
      expect(screen.queryByText(/강한 심장 박동/)).toBeNull();
    });
  });

  describe("씬 1 → 6 완주", () => {
    it("각 상호작용을 순서대로 통과해 미션 완료까지 걸어간다", async () => {
      const { user } = renderPage();

      // Scene 1 — 시편 23 도입이 실제로 렌더되고 나레이션 버튼이 붙는다.
      expect(
        await screen.findByRole("heading", { name: "양 떼와 시편" }),
      ).toBeInTheDocument();
      expect(screen.getByText(/어린 시인 다윗의 수금/)).toBeInTheDocument();
      expect(await findParagraph(scene1Psalm23)).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /듣기 — 음성으로 듣기/ }),
      ).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /계속/ }));

      // Scene 2 — 세 반응. 판정하지 않는 설계라 셋 다 열려 있어야 한다.
      expect(
        await screen.findByRole("heading", { name: "형들의 비웃음" }),
      ).toBeInTheDocument();
      expect(screen.getByText(/형 엘리압이 비웃는다/)).toBeInTheDocument();
      for (const label of ["반박한다", "침묵한다", "전선 쪽으로 걸어간다"]) {
        expect(screen.getByRole("button", { name: label })).toBeEnabled();
      }
      await user.click(screen.getByRole("button", { name: "침묵한다" }));
      expect(decideMock).toHaveBeenCalledWith(
        "david",
        "sess-david-1",
        2,
        "silence",
      );

      // Scene 3 — 직전 반응의 echo + 갑옷 3동작.
      expect(
        await screen.findByRole("heading", { name: "사울의 갑옷" }),
      ).toBeInTheDocument();
      expect(await findParagraph(scene2Reactions.silence)).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /갑옷을 입어본다/ }));
      // 마지막 동작 전에는 넘어가지 않는다.
      expect(decideMock).toHaveBeenCalledTimes(2);
      await user.click(screen.getByRole("button", { name: /걸어본다/ }));
      expect(decideMock).toHaveBeenCalledTimes(2);
      await user.click(screen.getByRole("button", { name: /벗는다/ }));
      expect(decideMock).toHaveBeenLastCalledWith("david", "sess-david-1", 3, {
        value: "armor_removed",
      });

      // Scene 4 — 다섯 돌. 다 집기 전에는 제출이 잠겨 있다.
      expect(
        await screen.findByRole("heading", { name: "시냇가의 5돌" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "다섯 돌을 모두 집으세요" }),
      ).toBeDisabled();

      await user.click(screen.getByRole("button", { name: /기도/ }));
      // 집은 순서가 사용자에게 보여야 한다 — 마지막 돌이 결말을 정하기 때문.
      expect(
        screen.getByRole("button", { name: /기도.*1.*번째/ }),
      ).toBeDisabled();
      await user.click(screen.getByRole("button", { name: /신뢰/ }));
      await user.click(screen.getByRole("button", { name: /외로움/ }));
      await user.click(screen.getByRole("button", { name: /모욕/ }));
      expect(
        screen.getByRole("button", { name: "다섯 돌을 모두 집으세요" }),
      ).toBeDisabled();
      await user.click(screen.getByRole("button", { name: /공포/ }));

      await user.click(
        screen.getByRole("button", { name: /다섯 돌을 들고 나아간다/ }),
      );
      // 마지막에 쥔 돌이 priority 로, 집은 순서 전체가 order 로 나간다.
      expect(decideMock).toHaveBeenLastCalledWith("david", "sess-david-1", 4, {
        priority: "last_fear",
        order: ["prayer", "trust", "loneliness", "humiliation", "fear"],
      });

      // Scene 5 — 마지막 돌(공포) echo + R4 게이트.
      expect(
        await screen.findByRole("heading", { name: "골리앗" }),
      ).toBeInTheDocument();
      expect(await findParagraph(scene4LastStone.fear)).toBeInTheDocument();
      await user.click(
        screen.getByRole("button", { name: /준비됐어요 · 들어갈게요/ }),
      );
      await user.click(screen.getByRole("button", { name: /물맷돌을 던진다/ }));

      // Scene 6 — 던짐 echo + 마지막 돌 기반 결말 톤(공포).
      expect(
        await screen.findByRole("heading", { name: "회복" }),
      ).toBeInTheDocument();
      expect(await findParagraph(scene5Monologue.throw)).toBeInTheDocument();
      expect(
        await findParagraph(scene6OutroByLastStone.fear),
      ).toBeInTheDocument();
      // R3 — 회복을 재촉하지 않는 문장이 결말에 실제로 남아 있는지.
      expect(screen.getByText(/두려운 채로도 괜찮다는/)).toBeInTheDocument();
      // R5 — AI 보조 고지.
      expect(
        screen.getAllByText("* AI 보조 — 본문은 성경 참조 *").length,
      ).toBeGreaterThan(0);
      expect(screen.getByText("진행 기록")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "미션 완료" }));
      expect(completeMock).toHaveBeenCalledWith(
        "david",
        "sess-david-1",
        "completed",
      );
      await vi.waitFor(() => expect(navigatedTo).toBe("/"));
    });
  });

  describe("Scene 2 — 세 반응 어느 것도 오답이 아니다", () => {
    it.each([
      ["반박한다", scene2Reactions.rebut],
      ["전선 쪽으로 걸어간다", scene2Reactions.walk_past],
    ])("%s 를 고르면 그 반응의 모놀로그가 온다", async (label, expected) => {
      const { user } = renderPage();
      await advanceToScene2(user);
      await user.click(await screen.findByRole("button", { name: label }));

      expect(await findParagraph(expected)).toBeInTheDocument();
    });
  });

  describe("Scene 4 — 마지막에 쥔 돌이 결말 톤을 정한다", () => {
    it("신뢰를 마지막에 쥐면 신뢰 톤의 결말이 나온다", async () => {
      const { user } = renderPage();
      await advanceToScene5(user, [/공포/, /모욕/, /외로움/, /기도/, /신뢰/]);

      expect(decideMock).toHaveBeenLastCalledWith("david", "sess-david-1", 4, {
        priority: "last_trust",
        order: ["fear", "humiliation", "loneliness", "prayer", "trust"],
      });
      expect(await findParagraph(scene4LastStone.trust)).toBeInTheDocument();

      await user.click(
        await screen.findByRole("button", { name: /준비됐어요 · 들어갈게요/ }),
      );
      await user.click(screen.getByRole("button", { name: /물맷돌을 던진다/ }));
      expect(
        await findParagraph(scene6OutroByLastStone.trust),
      ).toBeInTheDocument();
    });

    it("돌 목록이 비어 있어도 기본 다섯 감정으로 진행할 수 있다", async () => {
      decideReturns({ 3: customScene(4, { ...PAYLOADS[4], extras: {} }) });
      const { user } = renderPage();
      await advanceToScene4(user);

      for (const stone of [/공포/, /모욕/, /외로움/, /신뢰/, /기도/]) {
        await user.click(await screen.findByRole("button", { name: stone }));
      }
      await user.click(
        screen.getByRole("button", { name: /다섯 돌을 들고 나아간다/ }),
      );
      expect(decideMock).toHaveBeenLastCalledWith("david", "sess-david-1", 4, {
        priority: "last_prayer",
        order: ["fear", "humiliation", "loneliness", "trust", "prayer"],
      });
    });

    it("모르는 돌 id 만 와도 결말이 비지 않고 '신뢰' 로 착지한다", async () => {
      decideReturns({
        3: customScene(4, {
          ...PAYLOADS[4],
          extras: { stones: [{ id: "mystery", label: "수수께끼 돌" }] },
        }),
      });
      const { user } = renderPage();
      await advanceToScene4(user);

      await user.click(
        await screen.findByRole("button", { name: /수수께끼 돌/ }),
      );
      await user.click(
        screen.getByRole("button", { name: /다섯 돌을 들고 나아간다/ }),
      );
      expect(decideMock).toHaveBeenLastCalledWith("david", "sess-david-1", 4, {
        priority: "last_trust",
        order: ["mystery"],
      });
      expect(await findParagraph(scene4LastStone.trust)).toBeInTheDocument();
    });

    it("Scene 4 를 건너뛰고 outro 로 점프해도 결말 문단이 비지 않는다", async () => {
      // lastStone 이 없는 상태로 Scene 6 에 도달하는 경로(백엔드 분기 변경 등).
      decideReturns({ 3: sceneResponse(6) });
      const { user } = renderPage();
      await advanceToScene4(user);

      expect(
        await screen.findByRole("heading", { name: "회복" }),
      ).toBeInTheDocument();
      expect(
        await findParagraph(scene6OutroByLastStone.trust),
      ).toBeInTheDocument();
    });
  });

  describe("outro 위기 안내 (david.yml crisis_reminder)", () => {
    /*
      david.yml Scene 6 은 `extras.crisis_reminder` 를 선언하고, 백엔드
      `CrisisTokenResolver` 가 `{{crisis_resources.default}}` 를 DB 정본 번호로
      치환해 내려보낸다. 그런데 2026-08-14 까지 이 화면은 그 값을 **읽지도
      않았다** — 치환까지 끝난 안내가 payload 안에서 그대로 버려졌고, 폭력
      경고(Scene 5)를 지나온 미션의 결말에 위기 자원이 한 줄도 안 떴다.

      CI 가 못 잡은 자리다. `check_frontend_hotline.py` 는 "번호를 하드코딩했나"
      를 보지 "번호가 뜨나" 는 보지 않는다 — 없는 것은 하드코딩이 아니므로
      초록이다. 화면에 뜨는지는 테스트만 잰다.
    */
    it("결말 화면이 payload 의 위기 안내를 낸다", async () => {
      startMock.mockResolvedValue(sceneResponse(6));
      renderPage();

      await screen.findByRole("heading", { name: "회복" });
      // 문구는 백엔드가 만든다. 화면이 문안을 다듬으면 안전 검토가 무의미해지므로
      // 픽스처 문자열이 그대로 나오는지 본다.
      const extras = PAYLOADS[6].extras as { crisis_reminder: string };
      expect(screen.getByRole("note")).toHaveTextContent(
        extras.crisis_reminder,
      );
    });

    it("안내가 없는 결말은 빈 상자를 만들지 않는다", async () => {
      // 테두리만 남은 상자는 "안내가 있는 줄 알았는데 비어 있다" 는 인상을 준다.
      startMock.mockResolvedValue(
        customScene(6, {
          sceneId: 6,
          title: "회복",
          type: "outro",
          next: null,
        }),
      );
      renderPage();

      await screen.findByRole("heading", { name: "회복" });
      expect(screen.queryByRole("note")).toBeNull();
    });
  });

  describe("백엔드가 이상한 것을 줘도 화면이 비지 않는다", () => {
    it("decide 가 실패하면 오류를 말하고 현재 씬은 그대로 남는다", async () => {
      decideMock.mockRejectedValueOnce(new Error("결정 저장 실패"));
      const { user } = renderPage();
      await user.click(await screen.findByRole("button", { name: /계속/ }));

      expect(
        await screen.findByText("오류: 결정 저장 실패"),
      ).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /계속/ })).toBeEnabled();
    });

    it("요청이 진행 중이면 버튼이 잠기고 진행 표시가 뜬다", async () => {
      const gate = deferred<JosephStartResponse>();
      decideMock.mockReturnValueOnce(gate.promise);
      const { user } = renderPage();
      await user.click(await screen.findByRole("button", { name: /계속/ }));

      const pending = await screen.findByRole("button", { name: "..." });
      expect(pending).toBeDisabled();
      gate.resolve(sceneResponse(2));
      expect(
        await screen.findByRole("heading", { name: "형들의 비웃음" }),
      ).toBeInTheDocument();
    });

    it("몸짓 step 이 비어 있어도 최소 한 동작으로 진행할 수 있다", async () => {
      decideReturns({ 2: customScene(3, { ...PAYLOADS[3], extras: {} }) });
      const { user } = renderPage();
      await advanceToScene3(user);

      await user.click(await screen.findByRole("button", { name: /행동하기/ }));
      expect(decideMock).toHaveBeenLastCalledWith("david", "sess-david-1", 3, {
        value: "armor_removed",
      });
    });

    it("extras 없이 최상위 키로 온 payload 도 읽는다", async () => {
      // loader 는 extras 블록을 통째로 넘기지만 씬 최상위 키도 fallback 으로 읽는다.
      // 두 배선 중 하나만 살아 있으면 그 씬은 상호작용 없는 빈 화면이 된다.
      decideReturns({
        1: customScene(2, {
          sceneId: 2,
          title: "최상위 payload 씬",
          type: "interaction",
          interaction: "pick_one",
          options: [{ id: "rebut", label: "반박한다" }],
        }),
      });
      const { user } = renderPage();
      await advanceToScene2(user);

      await user.click(await screen.findByRole("button", { name: "반박한다" }));
      expect(await findParagraph(scene2Reactions.rebut)).toBeInTheDocument();
    });

    it("제목도 타입도 없는 payload 면 기본 제목만 두고 탈출 링크는 남긴다", async () => {
      startMock.mockResolvedValueOnce(customScene(1, {}));
      renderPage();

      expect(
        await screen.findByRole("heading", { name: "Scene" }),
      ).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /홈/ })).toHaveAttribute(
        "href",
        "/",
      );
      expect(screen.getByRole("link", { name: /Joseph 미션/ })).toHaveAttribute(
        "href",
        "/joseph",
      );
    });

    it("backend responseText 가 있으면 프론트 fallback 보다 그것을 쓴다", async () => {
      decideReturns({
        2: sceneResponse(3, { responseText: "백엔드가 만든 응답입니다." }),
      });
      const { user } = renderPage();
      await advanceToScene3(user);

      expect(
        await screen.findByText("백엔드가 만든 응답입니다."),
      ).toBeInTheDocument();
      expect(queryParagraph(scene2Reactions.silence)).toBeNull();
    });

    it("[알려진 버그] echo 를 만들지 못하는 씬을 지나도 이전 echo 가 남는다", async () => {
      // Scene 2 의 반응 문구가 Scene 4(돌 고르기) 화면까지 따라온다. 사용자는 그것을
      // *방금 한 결정* 의 응답으로 읽는다. jesus/page.tsx 는 같은 자리에서
      // `else setEcho(null)` 로 지우는데 david 는 그 한 줄이 없다 (page.tsx onSuccess).
      // 소스를 고치지 않기로 한 작업이라 *현재 동작* 을 고정해 둔다 —
      // 버그가 고쳐지면 이 테스트가 큰 소리로 깨지고, 그때 기대값을 뒤집으면 된다.
      const { user } = renderPage();
      await advanceToScene4(user);

      expect(
        await screen.findByRole("heading", { name: "시냇가의 5돌" }),
      ).toBeInTheDocument();
      // Scene 3(갑옷)은 자기 echo 가 없다 → Scene 2 의 침묵 반응이 그대로 남아 있다.
      expect(queryParagraph(scene2Reactions.silence)).not.toBeNull();
    });

    it("Scene 1 의 '계속' 은 echo 를 직접 지운다 — 도입으로 되돌아와도 잔상이 없다", async () => {
      // 유일하게 echo 를 명시적으로 비우는 경로. 백엔드가 1번 씬을 다시 주는 상황을 만든다.
      decideReturns({ 2: customScene(1, PAYLOADS[1]) });
      const { user } = renderPage();
      await advanceToScene3(user);

      expect(await findParagraph(scene2Reactions.silence)).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /계속/ }));
      expect(queryParagraph(scene2Reactions.silence)).toBeNull();
    });
  });
});
