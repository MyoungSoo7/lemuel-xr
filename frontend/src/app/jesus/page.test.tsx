import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

// 백엔드 왕복은 전부 막는다 — 이 파일이 재는 것은 *화면의 상태 기계* 와 안전 배선이지
// HTTP 계층이 아니다. 반대로 공용 컴포넌트(TriggerWarningGate/SceneBootState/
// NarrationAudioButton)와 독백 상수는 **모킹하지 않는다** — 배선이 맞는지가 이 테스트의
// 목적이라 실물이 렌더돼야 의미가 있다.
vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

/*
  성경 자구는 `/api/scripture` 에서 온다. 실물 왕복만 막고, 응답은 **시드 SQL 을 그대로
  읽어** 돌려준다(`src/test/seed-passages.ts`) — 자구 사본을 테스트에 또 적으면 이 변경이
  없애려던 "두 벌" 이 세 벌이 된다.

  이 모킹이 없으면 `MonologueText` 가 전부 "본문 로드 중..." 으로 남는다. 그때 실패하는
  것은 모놀로그 단언이므로, 원인이 배선이 아니라 자구 공급이라는 게 바로 보이지 않는다.
*/
vi.mock("@/lib/api/content", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/content")>()),
  fetchScripturePassage: vi.fn(),
}));

import {
  completeMission,
  decideMission,
  startMission,
  type JosephStartResponse,
} from "@/lib/api/game";

import { fetchScripturePassage } from "@/lib/api/content";
import { render as renderMonologue, seedPassage } from "@/test/seed-passages";
import type { Monologue } from "@/lib/content/scripture-quote";
import {
  scene1Incarnation,
  scene2Beatitudes,
  scene3Touch,
  scene4Iam,
  scene4Teaching,
  scene5Passion,
  scene6Resurrection,
  scene7Ascension,
  scene7CrisisReminder,
} from "@/lib/content/jesus-monologues";
import JesusPage from "./page";

const startMock = vi.mocked(startMission);
const decideMock = vi.mocked(decideMission);
const completeMock = vi.mocked(completeMission);

/**
 * scenePayload 픽스처는 backend 가 실제로 만드는 모양을 따른다:
 * ScenarioYamlLoader 가 yml 씬의 비표준 키(= `extras` 블록, `trigger_warning`)를 Scene.extras 로
 * 모으고 ScenePayloadAssembler 가 그걸 payload 최상위로 펼친다. 그래서 화면이 보는 payload 는
 *   { sceneId, title, type, interaction, next, extras: {…yml extras…}, trigger_warning: {…} }
 * 이다. 픽스처를 실제와 다르게 만들면 게이트 테스트가 초록이어도 운영에서는 안 뜬다 —
 * 그게 정확히 2026-08-12 사고의 형태라서 값을 jesus.yml 에서 그대로 옮겨 왔다.
 */
const PAYLOADS: Record<number, Record<string, unknown>> = {
  1: {
    sceneId: 1,
    title: "성육신 — 말씀이 육신이 되어",
    type: "cinematic",
    next: 2,
    extras: { anchor: "낮아짐·임재·시작" },
  },
  2: {
    sceneId: 2,
    title: "산 위의 가르침 — 팔복",
    type: "interaction",
    interaction: "scripture_reading",
    next: 3,
    extras: {
      lines: [
        {
          ref: "mt-5:3",
          text: "심령이 가난한 자는 복이 있나니 천국이 그들의 것임이요",
        },
        {
          ref: "mt-5:4",
          text: "애통하는 자는 복이 있나니 그들이 위로를 받을 것임이요",
        },
        {
          ref: "mt-5:6",
          text: "의에 주리고 목마른 자는 복이 있나니 그들이 배부를 것임이요",
        },
      ],
      reflection_prompt:
        "여기서 '복'은 성취가 아니라 *비어 있음* 에서 시작됩니다. 오늘 당신의 비어 있는 자리 하나를 떠올려 보세요.",
    },
  },
  3: {
    sceneId: 3,
    title: "만짐 — 병자를 고치심",
    type: "interaction",
    interaction: "gesture_sequence",
    next: 4,
    extras: {
      steps: [
        { id: "approach", label: "다가간다" },
        { id: "reach_out", label: "손을 내민다" },
      ],
    },
  },
  4: {
    sceneId: 4,
    title: "길이요 진리요 생명 — 갈림길에서",
    type: "interaction",
    interaction: "pick_one",
    next: 5,
    extras: {
      context_line:
        "'주여 어디로 가시는지 우리가 알지 못하거늘 그 길을 어찌 알겠사옵나이까' 하는 도마에게 (요 14:5)",
      options: [
        { id: "the_way", label: "길 — 어디로 가야 할지 모를 때" },
        { id: "the_truth", label: "진리 — 무엇이 참인지 흔들릴 때" },
        { id: "the_life", label: "생명 — 살아갈 힘이 없을 때" },
      ],
    },
  },
  5: {
    sceneId: 5,
    title: "겟세마네와 십자가 — 뜻대로 마옵시고",
    type: "interaction",
    interaction: "contemplative",
    next: 6,
    extras: {
      suffering_footer:
        "이 묵상은 자발적인 고난의 의미를 다룹니다. 가정폭력·종교적 학대·정신적 학대 같은 피해 상황을 견디라는 강요가 아닙니다.",
    },
    // jesus.yml 원본 그대로. consent_card_ko 는 없다 → 게이트가 화면 fallbackProse 를 쓴다.
    trigger_warning: {
      level: "medium",
      content: ["suffering", "death"],
      consent_card_id: "jesus_scene5_passion_warning",
      skip_alternative_scene_id: 6,
    },
  },
  6: {
    sceneId: 6,
    title: "부활 — 빈 무덤",
    type: "interaction",
    interaction: "scripture_reading",
    next: 7,
    extras: {
      lines: [
        { ref: "jn-20:15", text: "여자여 어찌하여 울며 누구를 찾느냐" },
        { ref: "jn-20:16", text: "마리아야 — 랍오니" },
      ],
    },
  },
  7: {
    sceneId: 7,
    title: "승천과 생명의 강 — 보내신 성령",
    type: "outro",
    next: null,
    extras: {},
  },
};

function sceneResponse(
  n: number,
  overrides: Partial<JosephStartResponse> = {},
): JosephStartResponse {
  return {
    sessionId: "sess-jesus-1",
    userId: "guest-1",
    currentScene: n,
    scenePayload: PAYLOADS[n],
    responseText: null,
    ...overrides,
  };
}

/** payload 를 통째로 갈아 끼워 특정 씬 모양을 실험할 때 쓴다. */
function customScene(
  n: number,
  scenePayload: Record<string, unknown>,
): JosephStartResponse {
  return {
    sessionId: "sess-jesus-1",
    userId: "guest-1",
    currentScene: n,
    scenePayload,
    responseText: null,
  };
}

/**
 * `decide(sceneId)` 의 응답을 씬 번호로 지정한다. 기본은 yml 의 `next` 를 그대로 흉내 낸 sceneId+1.
 * (mockImplementationOnce 를 쓰면 "몇 번째 호출인지" 에 묶여 걸어가는 경로가 바뀔 때마다 깨진다.)
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
 * 독백/아웃트로는 줄바꿈이 있는 긴 문단이라 기본 문자열 매칭이 안 먹는다(공백 정규화).
 * 문단 하나의 전체 텍스트가 정확히 그 상수인지를 본다 — 일부만 새는 것을 잡기 위해 부분
 * 일치가 아니라 완전 일치로 잰다.
 */
const squash = (s: string) => s.replace(/\s+/g, " ").trim();
/**
 * 기대값은 문자열이 아니라 **모놀로그 조각 배열** 이다(`scripture-quote.ts`). 화면에 뜨는
 * 것은 그 조각에 `/api/scripture` 자구를 채운 결과라, 비교 전에 같은 규칙으로 해석한다 —
 * 조각 배열끼리 비교하면 인용이 하나도 안 풀려도 기대가 성립한다.
 * 해석은 매처당 한 번만 한다(매처 함수 자체는 DOM 노드마다 불린다).
 */
const paragraphMatcher = (expected: Monologue) => {
  const text = squash(renderMonologue(expected));
  return (_content: string, el: Element | null) =>
    el?.tagName === "P" && squash(el.textContent ?? "") === text;
};
const findParagraph = (expected: Monologue) =>
  screen.findByText(paragraphMatcher(expected));
const queryParagraph = (expected: Monologue) =>
  screen.queryByText(paragraphMatcher(expected));

/**
 * Scene 4 의 한 문단 = 고른 "나는 ~이다" + 빈 줄 + 공통 가르침. `page.tsx` 의
 * `[...scene4Iam[iam], "\n\n", ...scene4Teaching]` 과 **같은 방식으로** 이어 붙인다 —
 * 여기서 문자열로 이어 붙이면 페이지가 조각을 어떻게 합치는지는 검사 밖으로 빠진다.
 */
const iamEcho = (iam: Monologue): Monologue => [
  ...iam,
  "\n\n",
  ...scene4Teaching,
];

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const user = userEvent.setup({ delay: null });
  render(
    <QueryClientProvider client={client}>
      <JesusPage />
    </QueryClientProvider>,
  );
  return { user };
}

// completeMission 성공 후 `location.href = "/"` 로 홈으로 나간다. jsdom 은 실제 네비게이션을
// 구현하지 않으므로 대입만 가로채 두고, 홈으로 보내려 했는지를 검증한다.
let navigatedTo: string | null = null;
Object.defineProperty(window, "location", {
  configurable: true,
  value: {
    get href() {
      return "http://localhost/jesus";
    },
    set href(v: string) {
      navigatedTo = v;
    },
  },
});

beforeEach(() => {
  vi.mocked(fetchScripturePassage).mockImplementation(seedPassage);
  navigatedTo = null;
  startMock.mockReset();
  decideMock.mockReset();
  completeMock.mockReset();
  startMock.mockResolvedValue(sceneResponse(1));
  decideReturns();
  completeMock.mockResolvedValue(undefined);
});

type User = ReturnType<typeof userEvent.setup>;

/** Scene 1 → 2 (계속) */
async function advanceToScene2(user: User) {
  await user.click(await screen.findByRole("button", { name: /계속/ }));
}

/** Scene 2 팔복 세 줄을 다 읽고 3으로. */
async function advanceToScene3(user: User) {
  await advanceToScene2(user);
  for (const re of [/심령이 가난한/, /애통하는 자는/, /의에 주리고/]) {
    await user.click(await screen.findByRole("button", { name: re }));
  }
  await user.click(screen.getByRole("button", { name: /다음으로/ }));
}

/** Scene 3 만짐 2단계 → 4 */
async function advanceToScene4(user: User) {
  await advanceToScene3(user);
  await user.click(await screen.findByRole("button", { name: /다가간다/ }));
  await user.click(screen.getByRole("button", { name: /손을 내민다/ }));
}

/** Scene 4 에서 optionLabel 을 골라 5(겟세마네)로. */
async function advanceToScene5(user: User, optionLabel: RegExp = /진리 —/) {
  await advanceToScene4(user);
  await user.click(await screen.findByRole("button", { name: optionLabel }));
}

describe("Jesus 미션 화면", () => {
  describe("세션 부팅", () => {
    it("start 가 실패하면 빈 화면이 아니라 실패 안내와 재시도 손잡이를 준다", async () => {
      // 원래 이 화면들은 실패 분기가 없어 "세션 시작 중..." 에서 영구히 멈췄다.
      // 사용자에게는 그냥 먹통이므로, 실패가 *말이 되어* 나오는지를 잰다.
      startMock.mockRejectedValueOnce({ response: { status: 500 } });
      const { user } = renderPage();

      expect(
        await screen.findByText("세션을 시작하지 못했습니다."),
      ).toBeInTheDocument();
      expect(screen.getByText(/오류 코드 500/)).toBeInTheDocument();

      // 재시도는 실제로 다시 요청을 보내고, 성공하면 씬으로 넘어가야 한다.
      startMock.mockResolvedValueOnce(sceneResponse(1));
      await user.click(screen.getByRole("button", { name: "다시 시도" }));

      expect(
        await screen.findByRole("heading", {
          name: "성육신 — 말씀이 육신이 되어",
        }),
      ).toBeInTheDocument();
      expect(startMock).toHaveBeenCalledTimes(2);
      expect(startMock).toHaveBeenCalledWith("jesus", "web");
    });

    it("401 은 만료로 구분해 안내한다 — 재시도해도 같은 결과일 토큰이기 때문", async () => {
      startMock.mockRejectedValue({ response: { status: 401 } });
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
        await screen.findByRole("heading", {
          name: "성육신 — 말씀이 육신이 되어",
        }),
      ).toBeInTheDocument();
    });
  });

  describe("R4 — trigger_warning 동의 게이트 (2026-08-12 회귀 방지)", () => {
    it("동의 전에는 겟세마네 씬의 상호작용이 아예 렌더되지 않는다", async () => {
      const { user } = renderPage();
      await advanceToScene5(user);

      // 게이트가 실제로 떠 있고, payload 의 값(레벨·트리거 종류·스킵 목적지)이 화면에 나온다.
      expect(
        await screen.findByText("잠깐 — 다음 장면 안내"),
      ).toBeInTheDocument();
      expect(screen.getByText("고통")).toBeInTheDocument();
      expect(screen.getByText("죽음")).toBeInTheDocument();
      expect(screen.getByText(/정서 강도: 중간/)).toBeInTheDocument();
      expect(
        screen.getByText(/건너뛰면 Scene 6 으로 이어집니다/),
      ).toBeInTheDocument();

      // 동의 전에는 본문(잔 앞에 머무는 상호작용)이 없어야 한다.
      expect(
        screen.queryByRole("button", { name: /잔 앞에 잠시 머문다/ }),
      ).not.toBeInTheDocument();

      // R2 고난 미화 방지 고지는 동의 *전* 에 보여야 한다 — 판단 재료는 판단 전에.
      expect(screen.getByText(/견디라는 강요가 아닙니다/)).toBeInTheDocument();
    });

    it("동의하면 그때서야 본문 상호작용이 열린다", async () => {
      const { user } = renderPage();
      await advanceToScene5(user);

      await user.click(
        await screen.findByRole("button", {
          name: /준비됐어요 · 함께 머물게요/,
        }),
      );

      expect(
        await screen.findByRole("button", { name: /잔 앞에 잠시 머문다/ }),
      ).toBeInTheDocument();
      expect(
        screen.queryByText("잠깐 — 다음 장면 안내"),
      ).not.toBeInTheDocument();
      // 동의는 화면 안에서 끝나는 결정이다 — 서버로 결정을 보내지 않는다.
      expect(decideMock).toHaveBeenCalledTimes(4); // scene 1~4 만
    });

    it("건너뛰면 skip 결정이 나가고 십자가 고난 묘사는 한 줄도 노출되지 않는다", async () => {
      const { user } = renderPage();
      await advanceToScene5(user);

      await user.click(
        await screen.findByRole("button", { name: /건너뛸게요/ }),
      );

      expect(decideMock).toHaveBeenLastCalledWith("jesus", "sess-jesus-1", 5, {
        value: "skip",
      });
      // 건너뛴 사람에게 겟세마네·십자가 echo 가 따라오면 게이트의 의미가 없다.
      expect(
        await screen.findByRole("heading", { name: "부활 — 빈 무덤" }),
      ).toBeInTheDocument();
      expect(screen.queryByText(/이 잔을 내게서 옮기시옵소서/)).toBeNull();
      expect(screen.queryByText(/다 이루었다/)).toBeNull();
    });

    it("게이트는 씬 타입이 아니라 payload 가 연다 — 경고 붙은 낭독 씬도 닫힌다", async () => {
      // 2026-08-12 까지 조건이 `sceneType === "contemplative"` 였다. 그래서 yml 이 다른 씬에
      // 경고를 붙여도 화면은 그냥 열렸다. 이 테스트가 그 회귀를 정확히 잡는다.
      decideReturns({
        1: customScene(2, {
          ...PAYLOADS[2],
          trigger_warning: {
            level: "high",
            content: ["death_wish"],
            skip_alternative_scene_id: 3,
          },
        }),
      });
      const { user } = renderPage();
      await advanceToScene2(user);

      expect(
        await screen.findByText("잠깐 — 다음 장면 안내"),
      ).toBeInTheDocument();
      expect(screen.getByText("죽음을 바라는 마음")).toBeInTheDocument();
      expect(screen.getByText(/정서 강도: 높음/)).toBeInTheDocument();
      // 낭독 본문이 동의 전에 보이면 안 된다.
      expect(
        screen.queryByRole("button", { name: /심령이 가난한/ }),
      ).toBeNull();
    });

    it("경고가 없는 contemplative 씬은 게이트 없이 바로 열린다", async () => {
      // 반대 방향도 재야 payload 구동이라는 게 증명된다 — 씬 타입으로 여는 구현이면 여기서 걸린다.
      const bare = { ...PAYLOADS[5] };
      delete (bare as Record<string, unknown>).trigger_warning;
      decideReturns({ 4: customScene(5, bare) });
      const { user } = renderPage();
      await advanceToScene5(user);

      expect(
        await screen.findByRole("button", { name: /잔 앞에 잠시 머문다/ }),
      ).toBeInTheDocument();
      expect(screen.queryByText("잠깐 — 다음 장면 안내")).toBeNull();
    });

    it("동의는 씬마다 초기화된다 — 다음 씬에 경고가 있으면 다시 묻는다", async () => {
      decideReturns({
        5: customScene(6, {
          ...PAYLOADS[6],
          trigger_warning: { level: "low", content: ["despair"] },
        }),
      });
      const { user } = renderPage();
      await advanceToScene5(user);

      await user.click(
        await screen.findByRole("button", {
          name: /준비됐어요 · 함께 머물게요/,
        }),
      );
      await user.click(
        await screen.findByRole("button", { name: /잔 앞에 잠시 머문다/ }),
      );

      // Scene 6 진입 — 앞 씬 동의가 이월되면 안 된다.
      expect(
        await screen.findByText("잠깐 — 다음 장면 안내"),
      ).toBeInTheDocument();
      expect(screen.getByText("절망")).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: /여자여 어찌하여/ }),
      ).toBeNull();
    });

    it("yml 정본(consent_card_ko)이 오면 화면 문구 대신 그것을 렌더한다", async () => {
      // 정본이 생겼는데 화면 문구를 계속 쓰면 문구 개정이 또 안 따라온다.
      decideReturns({
        4: customScene(5, {
          ...PAYLOADS[5],
          trigger_warning: {
            ...(PAYLOADS[5].trigger_warning as Record<string, unknown>),
            consent_card_ko:
              "다음 장면은 겟세마네와 십자가입니다.\n[계속한다] [건너뛰기 → Scene 6]\n음성/자막 강도: 보통",
          },
        }),
      });
      const { user } = renderPage();
      await advanceToScene5(user);

      expect(
        await screen.findByText(/다음 장면은 겟세마네와 십자가입니다\./),
      ).toBeInTheDocument();
      // UI 지시 줄은 산문에서 걷어내고 실제 버튼으로만 남는다.
      expect(screen.queryByText(/\[계속한다\]/)).toBeNull();
      expect(screen.queryByText(/음성\/자막 강도/)).toBeNull();
      // 화면 소유 fallback 문구는 정본이 있으면 쓰이지 않는다.
      expect(screen.queryByText(/약 2분/)).toBeNull();
    });
  });

  describe("씬 1 → 7 완주", () => {
    it("각 씬의 상호작용을 순서대로 통과해 미션 완료까지 걸어간다", async () => {
      const { user } = renderPage();

      // Scene 1 — 성육신 본문이 캡션만이 아니라 실제로 렌더되고, 나레이션 버튼이 붙는다.
      expect(
        await screen.findByRole("heading", {
          name: "성육신 — 말씀이 육신이 되어",
        }),
      ).toBeInTheDocument();
      expect(screen.getByText(/별빛 아래 소박한 구유/)).toBeInTheDocument();
      expect(await findParagraph(scene1Incarnation)).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: /듣기 — 음성으로 듣기/ }),
      ).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /계속/ }));

      // Scene 2 — 다 읽기 전에는 다음으로 못 간다.
      expect(
        await screen.findByRole("heading", { name: "산 위의 가르침 — 팔복" }),
      ).toBeInTheDocument();
      expect(
        screen.getByRole("button", { name: "본문을 모두 읽어 보세요" }),
      ).toBeDisabled();
      expect(screen.queryByText(/오늘 당신의 비어 있는 자리/)).toBeNull();

      await user.click(screen.getByRole("button", { name: /심령이 가난한/ }));
      await user.click(screen.getByRole("button", { name: /애통하는 자는/ }));
      // 마지막 한 줄이 남아 있는 동안에도 여전히 잠겨 있어야 한다.
      expect(
        screen.getByRole("button", { name: "본문을 모두 읽어 보세요" }),
      ).toBeDisabled();
      await user.click(screen.getByRole("button", { name: /의에 주리고/ }));

      // 다 읽으면 성찰 프롬프트가 열리고 진행 버튼이 풀린다.
      expect(
        screen.getByText(/오늘 당신의 비어 있는 자리/),
      ).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /다음으로/ }));

      // Scene 3 — 직전 결정의 echo(팔복 독백)가 뜬다.
      expect(
        await screen.findByRole("heading", { name: "만짐 — 병자를 고치심" }),
      ).toBeInTheDocument();
      expect(await findParagraph(scene2Beatitudes)).toBeInTheDocument();
      expect(decideMock).toHaveBeenCalledWith("jesus", "sess-jesus-1", 2, {
        value: "read",
      });

      await user.click(screen.getByRole("button", { name: /다가간다/ }));
      // 첫 몸짓만으로는 넘어가지 않는다 — 마지막 step 이 완료 조건이다.
      expect(decideMock).toHaveBeenCalledTimes(2);
      await user.click(screen.getByRole("button", { name: /손을 내민다/ }));

      // Scene 4 — 만짐 echo + 세 갈래 선택지 + 문맥 문장.
      expect(
        await screen.findByRole("heading", {
          name: "길이요 진리요 생명 — 갈림길에서",
        }),
      ).toBeInTheDocument();
      expect(await findParagraph(scene3Touch)).toBeInTheDocument();
      expect(screen.getByText(/하는 도마에게 \(요 14:5\)/)).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /생명 —/ }));

      // Scene 5 — 선택한 문(생명)의 응답 + 하나의 문이라는 teaching 이 함께 온다.
      expect(
        await screen.findByRole("heading", {
          name: "겟세마네와 십자가 — 뜻대로 마옵시고",
        }),
      ).toBeInTheDocument();
      expect(
        await findParagraph(iamEcho(scene4Iam.the_life)),
      ).toBeInTheDocument();
      expect(decideMock).toHaveBeenCalledWith(
        "jesus",
        "sess-jesus-1",
        4,
        "the_life",
      );

      // R4 게이트 통과 후 묵상.
      await user.click(
        screen.getByRole("button", { name: /준비됐어요 · 함께 머물게요/ }),
      );
      await user.click(
        screen.getByRole("button", { name: /잔 앞에 잠시 머문다/ }),
      );

      // Scene 6 — 동의하고 들어간 사람에게만 십자가 echo 가 온다.
      expect(
        await screen.findByRole("heading", { name: "부활 — 빈 무덤" }),
      ).toBeInTheDocument();
      expect(await findParagraph(scene5Passion)).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: /여자여 어찌하여/ }));
      await user.click(screen.getByRole("button", { name: /마리아야/ }));
      await user.click(screen.getByRole("button", { name: /다음으로/ }));

      // Scene 7 — 부활 echo(R3: 이름이 불린다) + 승천 outro + 위기 라우팅.
      expect(
        await screen.findByRole("heading", {
          name: "승천과 생명의 강 — 보내신 성령",
        }),
      ).toBeInTheDocument();
      expect(await findParagraph(scene6Resurrection)).toBeInTheDocument();
      expect(await findParagraph(scene7Ascension)).toBeInTheDocument();
      // R1 — outro 에는 위기 상담 번호가 반드시 있어야 한다. 번호를 여기 다시 적는 대신
      // 화면이 렌더하는 정본 문구(scene7CrisisReminder, 자체가 CRISIS_RESOURCES 파생)를
      // 그대로 기대한다 — 번호가 개정돼도 이 기대는 따라 움직인다.
      // (`scripts/check_frontend_hotline.py`)
      expect(screen.getByText(renderMonologue(scene7CrisisReminder))).toBeInTheDocument();
      // R5 — AI 보조 고지.
      expect(
        screen.getAllByText("* AI 보조 — 본문은 성경 참조 *").length,
      ).toBeGreaterThan(0);

      // 결정이 쌓인 진행 기록.
      expect(screen.getByText("진행 기록")).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: "미션 완료" }));
      expect(completeMock).toHaveBeenCalledWith(
        "jesus",
        "sess-jesus-1",
        "completed",
      );
      await vi.waitFor(() => expect(navigatedTo).toBe("/"));
    });
  });

  describe("Scene 4 — 세 갈래가 같은 한 분께 도착한다", () => {
    it.each([
      ["길 —", scene4Iam.the_way],
      ["진리 —", scene4Iam.the_truth],
    ])("%s 를 고르면 그 결핍에 맞는 응답이 온다", async (label, expected) => {
      const { user } = renderPage();
      await advanceToScene4(user);
      await user.click(
        await screen.findByRole("button", { name: new RegExp(label) }),
      );

      expect(
        await findParagraph(iamEcho(expected)),
      ).toBeInTheDocument();
    });

    it("모르는 선택 id 가 와도 빈 echo 가 아니라 '길' 로 안전 착지한다", async () => {
      decideReturns({
        3: customScene(4, {
          ...PAYLOADS[4],
          extras: { options: [{ id: "mystery_door", label: "알 수 없는 문" }] },
        }),
      });
      const { user } = renderPage();
      await advanceToScene3(user);
      await user.click(await screen.findByRole("button", { name: /다가간다/ }));
      await user.click(screen.getByRole("button", { name: /손을 내민다/ }));
      await user.click(
        await screen.findByRole("button", { name: /알 수 없는 문/ }),
      );

      expect(
        await findParagraph(iamEcho(scene4Iam.the_way)),
      ).toBeInTheDocument();
    });
  });

  describe("백엔드가 이상한 것을 줘도 화면이 비지 않는다", () => {
    it("decide 가 실패하면 오류를 말하고 현재 씬 상호작용은 그대로 남는다", async () => {
      decideMock.mockRejectedValueOnce(new Error("결정 저장 실패"));
      const { user } = renderPage();
      await user.click(await screen.findByRole("button", { name: /계속/ }));

      expect(
        await screen.findByText("오류: 결정 저장 실패"),
      ).toBeInTheDocument();
      // 사용자가 다시 시도할 수 있어야 한다 — 화면이 잠기면 안 된다.
      expect(screen.getByRole("button", { name: /계속/ })).toBeEnabled();
    });

    it("요청이 진행 중이면 버튼이 잠기고 진행 중 표시가 뜬다", async () => {
      const gate = deferred<JosephStartResponse>();
      decideMock.mockReturnValueOnce(gate.promise);
      const { user } = renderPage();
      await user.click(await screen.findByRole("button", { name: /계속/ }));

      const pending = await screen.findByRole("button", { name: "..." });
      expect(pending).toBeDisabled();
      gate.resolve(sceneResponse(2));
      expect(
        await screen.findByRole("heading", { name: "산 위의 가르침 — 팔복" }),
      ).toBeInTheDocument();
    });

    it("본문 줄이 비어 있어도 낭독 씬이 백지가 되지 않는다", async () => {
      decideReturns({ 1: customScene(2, { ...PAYLOADS[2], extras: {} }) });
      const { user } = renderPage();
      await advanceToScene2(user);

      expect(
        await screen.findByRole("button", {
          name: /본문을 천천히 읽어 봅니다/,
        }),
      ).toBeInTheDocument();
    });

    it("몸짓 step 이 비어 있어도 최소 한 동작으로 진행할 수 있다", async () => {
      decideReturns({ 2: customScene(3, { ...PAYLOADS[3], extras: {} }) });
      const { user } = renderPage();
      await advanceToScene3(user);

      await user.click(
        await screen.findByRole("button", { name: /손을 내민다/ }),
      );
      expect(decideMock).toHaveBeenLastCalledWith("jesus", "sess-jesus-1", 3, {
        value: "touch",
      });
    });

    it("extras 없이 최상위 키로 온 payload 도 읽는다", async () => {
      // 지금 loader 는 extras 블록을 통째로 넘기지만, 씬 최상위에 직접 적힌 키도 fallback 으로
      // 읽는다. 두 배선 중 하나만 살아 있으면 그 씬은 상호작용 없는 빈 화면이 된다.
      decideReturns({
        3: customScene(4, {
          sceneId: 4,
          title: "최상위 payload 씬",
          type: "interaction",
          interaction: "pick_one",
          context_line: "최상위 문맥 문장",
          options: [{ id: "the_truth", label: "진리로 간다" }],
        }),
      });
      const { user } = renderPage();
      await advanceToScene4(user);

      expect(screen.getByText("최상위 문맥 문장")).toBeInTheDocument();
      await user.click(screen.getByRole("button", { name: "진리로 간다" }));
      expect(
        await findParagraph(iamEcho(scene4Iam.the_truth)),
      ).toBeInTheDocument();
    });

    it("제목도 타입도 없는 payload 면 기본 제목만 두고 홈 경로는 남긴다", async () => {
      startMock.mockResolvedValueOnce(customScene(1, {}));
      renderPage();

      expect(
        await screen.findByRole("heading", { name: "Scene" }),
      ).toBeInTheDocument();
      // 알 수 없는 씬에 갇히지 않도록 탈출 링크는 항상 있어야 한다.
      expect(screen.getByRole("link", { name: /홈/ })).toHaveAttribute(
        "href",
        "/",
      );
      expect(screen.getByRole("link", { name: /David 미션/ })).toHaveAttribute(
        "href",
        "/david",
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
      expect(queryParagraph(scene2Beatitudes)).toBeNull();
    });

    it("이전 씬 echo 는 다음 결정에서 반드시 지워진다", async () => {
      // Scene 2 의 echo 가 Scene 4 까지 따라오면 사용자는 방금 한 결정의 응답으로 오해한다.
      const { user } = renderPage();
      await advanceToScene4(user);

      expect(await findParagraph(scene3Touch)).toBeInTheDocument();
      expect(queryParagraph(scene2Beatitudes)).toBeNull();
    });
  });
});
