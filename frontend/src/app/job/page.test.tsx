import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
// 번호 리터럴은 `@/lib/crisis-resources` 에만 산다. 픽스처(백엔드가 yml 의
// `{{crisis_resources.default}}` 를 치환해 내려보내는 값)도 기대값도 정본에서 파생시킨다.
// (`scripts/check_frontend_hotline.py`)
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";

/*
  욥 미션 화면 테스트.

  이 화면 고유의 위험은 Scene 3 이다. job.yml 은 각 선택지에 저작 정본 `response` 를
  달아 두었는데, 화면이 선택 즉시 다음 씬으로 넘어가면 그 문구는 **한 번도 뜨지 않고**
  사라진다 — 화면은 멀쩡히 도는데 저작물이 통째로 유실되는 종류의 회귀다. 그래서
  "고르면 응답이 먼저 뜨고, 그 다음에야 씬이 넘어간다" 를 명시적으로 잰다.

  그리고 R4 동의 게이트 — job.yml Scene 2 는 level medium ·
  content [death_wish, birth_lament] · skip_alternative_scene_id 3 을 선언한다.
  CI 게이트는 "화면이 payload 를 읽는가" 까지만 보고 카드가 실제로 렌더되는지는 안 본다.
  그 구멍을 여기서 메운다.
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission, completeMission } from "@/lib/api/game";
import JobPage from "./page";

const SESSION = "sess-job";

/**
 * outro 위기 안내 문구. 픽스처와 기대값이 **같은 상수**를 보게 묶어 둔다 — 둘을 따로
 * 적으면 화면이 문구를 통째로 떨어뜨려도 한쪽만 고치면 초록이 유지된다.
 */
const CRISIS_REMINDER = `지금 이 순간이 무겁다면, ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}.`;

function scene(currentScene: number, scenePayload: Record<string, unknown>) {
  return {
    sessionId: SESSION,
    userId: "guest-1",
    currentScene,
    scenePayload,
    responseText: null,
  };
}

// backend/src/main/resources/scenarios/job.yml 의 실제 형태를 따른 payload.
const SCENE1 = scene(1, {
  title: "침묵의 7일",
  type: "cinematic",
  extras: {
    anchor: "함께 있음의 동행",
    static_text: "친구 세 명이 말없이 7일 밤낮 함께 앉았습니다.",
  },
});

const SCENE2 = scene(2, {
  title: "욥의 첫 외침 — 태어난 날을 저주",
  type: "interaction",
  interaction: "scripture_reading",
  extras: {
    anchor: "비탄의 언어 정당화",
    static_text: "욥은 침묵을 깨고 자기 태어난 날을 저주합니다.",
    reflection_prompt:
      "말할 수 없었던 외침이 있다면, 한 단어라도 떠올려 보세요.",
  },
  trigger_warning: {
    level: "medium",
    content: ["death_wish", "birth_lament"],
    consent_card_id: "job_scene2_lament_warning",
    skip_alternative_scene_id: 3,
  },
});

const SCENE3 = scene(3, {
  title: "친구들의 위로 — 정답이 아닌 것",
  type: "interaction",
  interaction: "pick_one",
  extras: {
    static_text: "친구들은 답을 가지고 왔습니다.",
    options: [
      {
        id: "heard_it_before",
        label: "나도 이런 말 들어본 적 있다",
        response: "당신만이 아닙니다. 그때의 외로움은 정당한 감정입니다.",
      },
      {
        id: "said_it_before",
        label: "내가 다른 사람에게 이런 말 했을지도 모른다",
        response: "그 자각이 욥의 친구들이 끝까지 못 한 것입니다.",
      },
      { id: "not_sure", label: "잘 모르겠다" },
    ],
  },
});

const SCENE4 = scene(4, {
  title: "폭풍 가운데서 하나님의 응답",
  type: "interaction",
  interaction: "scripture_reading",
  extras: {
    static_text: "신은 욥의 질문에 답하지 않으셨습니다.",
    reflection_prompt:
      "함께 있을 수 있는 사람이 한 명 있다면 — 그 자리를 부탁해 보세요.",
  },
});

const SCENE5 = scene(5, {
  title: "회복 — 그러나 답 없이",
  type: "outro",
  value_prompt: "오늘 솔직한 한 줄을 일기에 적어보세요.",
  extras: {
    static_text: "욥은 설명을 듣지 못했습니다. 그러나 만남을 얻었습니다.",
    suffering_footer:
      "이 묵상은 피해 상황을 견디라는 강요가 아닙니다. 안전하지 않다면 상담 자원에 연결되세요.",
    crisis_reminder: CRISIS_REMINDER,
    next_scene_suggestion: "엘리야 — 천사가 음식과 잠을 주신 이야기",
  },
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <JobPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(startMission).mockReset();
  vi.mocked(decideMission).mockReset();
  vi.mocked(completeMission).mockReset();
  vi.mocked(completeMission).mockResolvedValue(undefined);
});

describe("욥 미션 — 씬 상태 기계", () => {
  it("Scene 1 → 5 → 미션 완료까지 끝까지 걸어간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "침묵의 7일" }),
    ).toBeInTheDocument();
    expect(screen.getByText("함께 있음의 동행")).toBeInTheDocument();
    expect(screen.getByText(/7일 밤낮 함께 앉았습니다/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(1, "job", SESSION, 1, {
      value: "next",
    });

    // Scene 2 — 경고 씬. 동의해야 본문이 열린다.
    await user.click(await screen.findByRole("button", { name: "계속한다" }));
    expect(
      await screen.findByText(/태어난 날을 저주합니다/),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(2, "job", SESSION, 2, {
      value: "next",
    });

    // Scene 3 — 고르면 응답이 먼저, 계속을 눌러야 씬이 넘어간다
    await user.click(
      await screen.findByRole("button", {
        name: "나도 이런 말 들어본 적 있다",
      }),
    );
    expect(decideMission).toHaveBeenCalledTimes(2); // 아직 넘어가지 않았다
    expect(
      screen.getByText("당신만이 아닙니다. 그때의 외로움은 정당한 감정입니다."),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(3, "job", SESSION, 3, {
      value: "heard_it_before",
    });

    // Scene 4
    expect(
      await screen.findByText(/질문에 답하지 않으셨습니다/),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // Scene 5 outro — 안전 고지(suffering_footer)는 사용자 대상이므로 반드시 뜬다
    expect(
      await screen.findByText(/피해 상황을 견디라는 강요가 아닙니다/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/오늘 솔직한 한 줄을 일기에 적어보세요/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/엘리야 — 천사가 음식과 잠을 주신 이야기/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith("job", SESSION, "completed");
  });

  it("응답 문구가 없는 선택지는 라벨만 되돌려 준다 — 빈 인용 상자를 만들지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "잘 모르겠다" }),
    );

    expect(screen.getByText("잘 모르겠다")).toBeInTheDocument();
    // 다른 선택지의 응답이 잘못 새어 나오면 안 된다.
    expect(screen.queryByText(/당신만이 아닙니다/)).toBeNull();
    expect(screen.getByRole("button", { name: "계속 →" })).toBeEnabled();
  });

  it("선택을 고르면 나머지 선택지는 화면에서 내려간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: "내가 다른 사람에게 이런 말 했을지도 모른다",
      }),
    );

    expect(
      screen.getByText(/그 자각이 욥의 친구들이 끝까지 못 한 것입니다/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "잘 모르겠다" })).toBeNull();
  });

  it("진행 기록에 지나온 씬 제목이 쌓인다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValueOnce(SCENE2);
    renderPage();

    expect(screen.queryByText("진행 기록")).toBeNull();
    await user.click(await screen.findByRole("button", { name: "계속 →" }));

    const summary = await screen.findByText("진행 기록");
    expect(summary.parentElement).toHaveTextContent("태어난 날을 저주");
  });

  it("어느 씬에서든 홈·일기 출구가 열려 있다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    expect(await screen.findByRole("link", { name: "← 홈" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.getByRole("link", { name: "일기 쓰기 →" })).toHaveAttribute(
      "href",
      "/topics/journal",
    );
  });

  it("outro 의 위기 안내는 씬 상단에 뜬다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    expect(await screen.findByText(CRISIS_REMINDER)).toBeInTheDocument();
  });
});

describe("욥 미션 — R4 동의 게이트 (job.yml Scene 2)", () => {
  it("동의 전에는 비탄 본문이 가려지고 경고 카드만 보인다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    renderPage();

    expect(
      await screen.findByText(/태어난 날을 저주하는 비탄/),
    ).toBeInTheDocument();
    expect(screen.getByText(/정서 강도: medium/)).toBeInTheDocument();

    expect(screen.queryByText(/욥은 침묵을 깨고/)).toBeNull();
    expect(screen.queryByText(/한 단어라도 떠올려 보세요/)).toBeNull();
    expect(screen.queryByRole("button", { name: "계속 →" })).toBeNull();
  });

  it("동의하면 본문과 성찰 질문이 열린다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속한다" }));

    expect(await screen.findByText(/욥은 침묵을 깨고/)).toBeInTheDocument();
    expect(screen.getByText(/한 단어라도 떠올려 보세요/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "계속한다" })).toBeNull();
  });

  it("건너뛰기는 skip 결정을 보내고, 서사는 다음 씬으로 이어진다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(await screen.findByRole("button", { name: "건너뛰기 →" }));

    expect(decideMission).toHaveBeenCalledWith("job", SESSION, 2, {
      value: "skip",
    });
    expect(
      await screen.findByRole("button", { name: "잘 모르겠다" }),
    ).toBeEnabled();
  });

  it("경고가 없는 씬은 게이트 없이 바로 본문을 연다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    renderPage();

    expect(
      await screen.findByText(/질문에 답하지 않으셨습니다/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "계속한다" })).toBeNull();
  });
});

describe("욥 미션 — 백엔드가 실패했을 때 사용자가 보는 것", () => {
  it("세션 시작 실패는 먹통 대신 재시도 손잡이를 준다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission)
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce(SCENE1);
    renderPage();

    expect(
      await screen.findByText("세션을 시작하지 못했습니다."),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(
      await screen.findByRole("heading", { name: "침묵의 7일" }),
    ).toBeInTheDocument();
  });

  it("401 은 만료 안내와 상태 코드를 보여준다", async () => {
    vi.mocked(startMission).mockRejectedValue({ response: { status: 401 } });
    renderPage();

    expect(
      await screen.findByText("세션이 만료됐습니다. 다시 시작해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByText(/오류 코드 401/)).toBeInTheDocument();
  });

  it("결정 전송이 실패하면 오류를 알리고 그 자리에 남긴다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockRejectedValue(
      new Error("서버가 응답하지 않습니다"),
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속 →" }));

    expect(
      await screen.findByText("오류: 서버가 응답하지 않습니다"),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "계속 →" })).toBeEnabled();
  });

  it("전송 중에는 버튼이 잠긴다 — 중복 결정 방지", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockReturnValue(new Promise(() => {}));
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속 →" }));

    expect(await screen.findByRole("button", { name: "..." })).toBeDisabled();
  });
});
