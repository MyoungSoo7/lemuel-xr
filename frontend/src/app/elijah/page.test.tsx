import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
// 번호 리터럴은 `@/lib/crisis-resources` 에만 산다. 픽스처도 기대값도 정본에서
// 파생시킨다 — 번호가 개정되면 이 파일은 정본을 따라 움직이고,
// 화면이 낡은 번호를 렌더하면 그때 빨개진다.
// (`scripts/check_frontend_hotline.py`)
import { CRISIS_DEFAULT, telHref } from "@/lib/crisis-resources";

/*
  엘리야 미션 화면 테스트.

  이 화면이 다루는 것은 *죽음 갈구* 다. 그래서 재는 축이 셋이다.

  1) 5개 씬을 끝까지 걸어가는 경로 (cinematic → scripture_reading → gesture_sequence →
     scripture_reading → outro → complete). 중간이 끊기면 사용자는 멈춘 화면에 남는다.

  2) R4 동의 게이트 — elijah.yml Scene 2 는 level medium · content [death_wish] ·
     skip_alternative_scene_id 3 을 선언한다. CI 게이트(check_frontend_trigger_warning.py)는
     "화면이 payload 를 읽는가" 까지만 본다. 여기서는 **카드가 실제로 뜨는지**, 동의 전에
     본문(죽음 갈구 서술)이 정말 가려지는지를 잰다.

  3) crisis_check 씬의 위기 연결. 이 블록은 앱이 "이 사용자가 위험할 수 있다" 고 판단한
     자리에서만 뜬다 — 조용히 사라지면 그게 제일 나쁜 회귀다.
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
}));

import { startMission, decideMission, completeMission } from "@/lib/api/game";
import ElijahPage from "./page";

const SESSION = "sess-elijah";

function scene(currentScene: number, scenePayload: Record<string, unknown>) {
  return {
    sessionId: SESSION,
    userId: "guest-1",
    currentScene,
    scenePayload,
    responseText: null,
  };
}

// backend/src/main/resources/scenarios/elijah.yml 의 실제 형태를 따른 payload.
const SCENE1 = scene(1, {
  title: "갈멜산의 승리 후 도망",
  type: "cinematic",
  extras: {
    anchor: "번아웃·소진",
    static_text: "엘리야는 바알 선지자 450명을 이긴 직후 도망쳤습니다.",
  },
});

const SCENE2 = scene(2, {
  title: "로뎀나무 아래 — 죽기를 간청",
  type: "interaction",
  interaction: "scripture_reading",
  extras: {
    anchor: "죽음 갈구의 정당성",
    static_text: "엘리야의 기도: 여호와여 이제 됐습니다. 내 생명을 거두소서.",
    reflection_prompt: "그 마음을 부끄럽게 여기지 않는 자리가 여기에 있습니다.",
    crisis_check: true,
  },
  trigger_warning: {
    level: "medium",
    content: ["death_wish"],
    consent_card_id: "elijah_scene2_deathwish_warning",
    skip_alternative_scene_id: 3,
  },
});

const SCENE3 = scene(3, {
  title: "천사 — 먼저 음식과 잠",
  type: "interaction",
  interaction: "gesture_sequence",
  extras: {
    anchor: "신체 회복 우선",
    static_text: "신은 영적 설교가 아니라 음식과 잠을 먼저 주셨습니다.",
    steps: [
      {
        id: "receive_food",
        label: "음식을 받는다",
        note: "마지막으로 따뜻한 음식을 드신 게 언제인가요?",
      },
      { id: "lie_down", label: "누워서 잔다" },
    ],
    practical_reminders: [
      "오늘 한 끼 따뜻한 음식을 드세요.",
      "오늘 평소보다 30분 일찍 누우세요.",
    ],
  },
});

const SCENE4 = scene(4, {
  title: "호렙산 — 세미한 음성",
  type: "interaction",
  interaction: "scripture_reading",
  extras: {
    static_text: "신은 바람·지진·불 가운데 계시지 않았습니다.",
    examples: [
      "오늘 잠시라도 미소가 나왔던 순간",
      "30초간 마음이 평온했던 자리",
    ],
  },
});

const SCENE5 = scene(5, {
  title: "마지막 한 마디 — 너 혼자가 아니다",
  type: "outro",
  value_prompt: "오늘 1분 호흡 + 따뜻한 한 끼 — 가장 작은 마음 지키기.",
  extras: {
    static_text: "신의 마지막 말씀: 7000명이 남았다.",
    crisis_reminder: `${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}.`,
    next_scene_suggestion: "시편 88 — 답 없는 시편",
  },
});

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <ElijahPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(startMission).mockReset();
  vi.mocked(decideMission).mockReset();
  vi.mocked(completeMission).mockReset();
  vi.mocked(completeMission).mockResolvedValue(undefined);
});

describe("엘리야 미션 — 씬 상태 기계", () => {
  it("Scene 1 → 5 → 미션 완료까지 끝까지 걸어간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    // Scene 1 cinematic — 본문과 앵커가 함께 뜬다
    expect(
      await screen.findByRole("heading", { name: "갈멜산의 승리 후 도망" }),
    ).toBeInTheDocument();
    expect(screen.getByText("번아웃·소진")).toBeInTheDocument();
    expect(
      screen.getByText(/450명을 이긴 직후 도망쳤습니다/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(1, "elijah", SESSION, 1, {
      value: "next",
    });

    // Scene 2 — 경고 씬. 동의해야 본문이 열린다.
    await user.click(await screen.findByRole("button", { name: "계속한다" }));
    expect(await screen.findByText(/내 생명을 거두소서/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(2, "elijah", SESSION, 2, {
      value: "next",
    });

    // Scene 3 gesture_sequence — 아무 동작도 누르지 않고 넘어갈 수 있어야 한다 (R3).
    expect(
      await screen.findByRole("button", { name: /음식을 받는다/ }),
    ).toBeEnabled();
    expect(
      screen.getByText("오늘 한 끼 따뜻한 음식을 드세요."),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));
    expect(decideMission).toHaveBeenNthCalledWith(3, "elijah", SESSION, 3, {
      value: "next",
    });

    // Scene 4 — 세미한 소리의 예시 목록
    expect(
      await screen.findByText("30초간 마음이 평온했던 자리"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "계속 →" }));

    // Scene 5 outro
    expect(
      await screen.findByText(/오늘 1분 호흡 \+ 따뜻한 한 끼/),
    ).toBeInTheDocument();
    expect(screen.getByText(/시편 88 — 답 없는 시편/)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeMission).toHaveBeenCalledWith(
      "elijah",
      SESSION,
      "completed",
    );
  });

  it("몸 돌봄 동작은 누른 것만 체크 표시된다 — 강제하지 않는 권유", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE3);
    renderPage();

    const food = await screen.findByRole("button", { name: /음식을 받는다/ });
    expect(food).not.toHaveTextContent("✓");
    // note 는 부담을 낮추는 질문이라 라벨과 함께 보여야 한다.
    expect(food).toHaveTextContent(
      "마지막으로 따뜻한 음식을 드신 게 언제인가요?",
    );

    await user.click(food);
    expect(
      await screen.findByRole("button", { name: /✓ 음식을 받는다/ }),
    ).toBeInTheDocument();
    // 다른 동작은 여전히 미체크 — 하나 눌렀다고 다 눌린 것처럼 보이면 안 된다.
    expect(
      screen.getByRole("button", { name: /누워서 잔다/ }),
    ).not.toHaveTextContent("✓");
  });

  it("extras 없이 top-level 로 내려온 payload 도 그대로 읽는다", async () => {
    // field() 는 extras → payload 순으로 본다. 백엔드가 평평하게 내려도 화면이 비면 안 된다.
    vi.mocked(startMission).mockResolvedValue(
      scene(1, {
        title: "갈멜산의 승리 후 도망",
        type: "cinematic",
        anchor: "번아웃·소진",
        static_text: "평평하게 내려온 본문입니다.",
      }),
    );
    renderPage();

    expect(
      await screen.findByText("평평하게 내려온 본문입니다."),
    ).toBeInTheDocument();
    expect(screen.getByText("번아웃·소진")).toBeInTheDocument();
  });

  it("진행 기록에 지나온 씬 제목이 쌓인다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    vi.mocked(decideMission).mockResolvedValueOnce(SCENE2);
    renderPage();

    expect(screen.queryByText("진행 기록")).toBeNull();
    await user.click(await screen.findByRole("button", { name: "계속 →" }));

    const summary = await screen.findByText("진행 기록");
    expect(summary.parentElement).toHaveTextContent("죽기를 간청");
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
});

describe("엘리야 미션 — R4 동의 게이트 (elijah.yml Scene 2)", () => {
  it("동의 전에는 죽음 갈구 본문이 가려지고 경고 카드만 보인다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    renderPage();

    expect(
      await screen.findByText(/죽고 싶다는 마음\(죽음 갈구\)/),
    ).toBeInTheDocument();
    expect(screen.getByText(/정서 강도: medium/)).toBeInTheDocument();

    // 본문·성찰 질문·계속 버튼 전부 동의 뒤로 미뤄져야 한다.
    expect(screen.queryByText(/내 생명을 거두소서/)).toBeNull();
    expect(screen.queryByText(/부끄럽게 여기지 않는 자리/)).toBeNull();
    expect(screen.queryByRole("button", { name: "계속 →" })).toBeNull();
  });

  it("동의하면 본문과 성찰 질문이 열린다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속한다" }));

    expect(await screen.findByText(/내 생명을 거두소서/)).toBeInTheDocument();
    expect(screen.getByText(/부끄럽게 여기지 않는 자리/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "계속한다" })).toBeNull();
  });

  it("건너뛰기는 skip 결정을 보내고, 서사는 다음 씬으로 이어진다", async () => {
    const user = userEvent.setup();
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    vi.mocked(decideMission).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(await screen.findByRole("button", { name: "건너뛰기 →" }));

    expect(decideMission).toHaveBeenCalledWith("elijah", SESSION, 2, {
      value: "skip",
    });
    expect(
      await screen.findByRole("button", { name: /음식을 받는다/ }),
    ).toBeEnabled();
  });

  it("경고가 없는 씬은 게이트 없이 바로 본문을 연다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE4);
    renderPage();

    expect(
      await screen.findByText(/바람·지진·불 가운데 계시지 않았습니다/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "계속한다" })).toBeNull();
  });
});

describe("엘리야 미션 — 위기 연결", () => {
  it("crisis_check 씬은 동의 전에도 기본 상담번호를 전화 링크로 올려 둔다", async () => {
    // 본문은 가려도 위기 연결은 가리지 않는다. 이 순서가 뒤집히면 제일 급한 사람이 막힌다.
    vi.mocked(startMission).mockResolvedValue(SCENE2);
    renderPage();

    const tel = await screen.findByRole("link", {
      name: new RegExp(CRISIS_DEFAULT.tel),
    });
    expect(tel).toHaveAttribute("href", telHref(CRISIS_DEFAULT));
    expect(tel).toHaveTextContent(CRISIS_DEFAULT.label);
    expect(
      screen.getByText(
        "지금 실제로 그 마음이 크다면, 본문보다 연결이 먼저입니다.",
      ),
    ).toBeInTheDocument();
  });

  it("crisis_reminder 가 있으면 그 문구를 대신 쓴다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE5);
    renderPage();

    expect(
      await screen.findByText(`${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}.`),
    ).toBeInTheDocument();
    // 정본 문구가 있으면 화면 소유 안내문은 중복 노출하지 않는다.
    expect(screen.queryByText(/본문보다 연결이 먼저입니다/)).toBeNull();
  });

  it("위기 신호가 없는 씬에는 위기 블록을 띄우지 않는다", async () => {
    vi.mocked(startMission).mockResolvedValue(SCENE1);
    renderPage();

    expect(
      await screen.findByRole("button", { name: "계속 →" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("link", { name: new RegExp(CRISIS_DEFAULT.tel) }),
    ).toBeNull();
  });
});

describe("엘리야 미션 — 백엔드가 실패했을 때 사용자가 보는 것", () => {
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
      await screen.findByRole("heading", { name: "갈멜산의 승리 후 도망" }),
    ).toBeInTheDocument();
  });

  it("403 은 만료 안내와 상태 코드를 보여준다", async () => {
    vi.mocked(startMission).mockRejectedValue({ response: { status: 403 } });
    renderPage();

    expect(
      await screen.findByText("세션이 만료됐습니다. 다시 시작해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByText(/오류 코드 403/)).toBeInTheDocument();
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
