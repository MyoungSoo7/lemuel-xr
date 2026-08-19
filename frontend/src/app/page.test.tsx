import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ClassifyResponse } from "@/lib/api/emotion";
import HomePage from "./page";
// 번호 리터럴은 `@/lib/crisis-resources` 에만 산다 —
// `scripts/check_frontend_hotline.py` 가 화면·테스트·주석 전부를 검사한다.
// (이 자리들은 소프트 패턴이 같은 줄의 문맥 단어를 요구해서 게이트를 통과하고
//  있었지만, 그건 규칙의 취지가 아니라 정규식의 빈틈이다.)
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";

// 백엔드로 나가는 유일한 통로를 여기서 끊는다 (그 밑은 client.ts 의 axios).
vi.mock("@/lib/api/emotion", () => ({
  classifyEmotion: vi.fn(),
}));
const { classifyEmotion } = await import("@/lib/api/emotion");
const classifyMock = vi.mocked(classifyEmotion);

/**
 * 홈은 이 앱의 유일한 진입점이다. 여기서 재는 것은 두 갈래 —
 *
 *  (A) 분류를 *거치지 않고도* 콘텐츠에 들어갈 수 있는가.
 *      감정을 적는 것 자체가 부담인 사용자가 있고, 백엔드 분류가 죽어도
 *      8인물·가치빌더로는 들어갈 수 있어야 한다. 이 카드들이 조용히 사라지면
 *      "글을 써야만 들어갈 수 있는 앱" 이 된다.
 *  (B) 분류 결과가 실제 사용자가 누를 수 있는 링크로 바뀌는가.
 *      미구현 인물은 링크가 "#" 이어야 하고(막다른 404 대신), 신뢰도 표기는
 *      0~1 확률을 % 로 옮긴 값이어야 한다.
 */

function renderHome() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <HomePage />
    </QueryClientProvider>,
  );
}

const RESULT: ClassifyResponse = {
  emotionLogId: 1,
  primary: { emotion: "LONELY", confidence: 0.876 },
  recommendations: {
    trackB: [
      { character: "elijah", rationale: "로뎀나무 아래의 탈진" },
      // 2026-08-20 까지 이 자리는 ruth 였다. `/ruth` 화면이 생기면서 더 이상
      // 미구현 예시가 아니게 돼 rahab 으로 바꿨다 — 게이트·자막 정본은 있는데
      // `frontend/src/app/rahab/` 가 없는, 지금 가장 앞서 있는 화면 없는 인물이다.
      { character: "rahab", rationale: "미구현 인물" },
    ],
    trackA: [
      { topicId: 3, title: "시편 — 탄식의 언어", rationale: "감정을 말로" },
      { topicId: 4, title: "잠언 — 마음지키기" },
    ],
  },
};

beforeEach(() => {
  classifyMock.mockReset();
});

describe("첫 화면 — 분류 없이도 들어갈 수 있는 길", () => {
  it("8인물 미션 카드가 전부 링크로 있다", () => {
    renderHome();
    const missions = screen.getByRole("heading", {
      name: /각성의 순간/,
    }).parentElement!;

    for (const [name, href] of [
      ["Joseph", "/joseph"],
      ["Moses", "/moses"],
      ["David", "/david"],
      ["Jesus", "/jesus"],
      ["Solomon", "/solomon"],
      ["Job", "/job"],
      ["Elijah", "/elijah"],
      // 룻은 2026-08-20 에 붙었다. 백엔드 enum 이 열린 뒤로도 한동안 이 목록에
      // 없어서 웹에서 들어갈 입구가 없었다 — 여기 없으면 화면이 있어도 못 간다.
      ["Ruth", "/ruth"],
    ]) {
      expect(
        within(missions).getByRole("link", { name: new RegExp(name) }),
      ).toHaveAttribute("href", href);
    }
  });

  it("AR 측 두 입구(가치 빌더 · 7주제 카드)가 있다", () => {
    renderHome();
    expect(
      screen.getByRole("link", { name: /자기만의 7 가치 빌더/ }),
    ).toHaveAttribute("href", "/values");
    expect(
      screen.getByRole("link", { name: /일상 영적 양식/ }),
    ).toHaveAttribute("href", "/topics");
  });

  it("위기 상담 번호 고지가 첫 화면부터 보인다", () => {
    // 홈 헤더의 고지는 CrisisFooter 와 별개다 — 이 앱이 임상 도구가 아니라는
    // 문장과 번호가 *분류를 시도하기 전에* 이미 시야에 있어야 한다.
    renderHome();
    expect(screen.getByText(/의료·임상 도구가 아닙니다/)).toBeInTheDocument();
    expect(
      screen.getByText(new RegExp(CRISIS_DEFAULT.tel)),
    ).toBeInTheDocument();
  });

  it("입력이 비어 있으면 제출 버튼이 잠겨 있다", () => {
    renderHome();
    expect(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    ).toBeDisabled();
  });
});

describe("감정 입력", () => {
  it("샘플 문구를 누르면 입력란이 채워지고 제출이 열린다", async () => {
    const user = userEvent.setup();
    renderHome();

    await user.click(
      screen.getByRole("button", { name: "오늘 너무 외롭고 지쳐있어" }),
    );

    expect(screen.getByRole("textbox")).toHaveValue(
      "오늘 너무 외롭고 지쳐있어",
    );
    expect(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    ).toBeEnabled();
  });

  it("공백만 입력하면 제출이 잠긴 채로 남는다", async () => {
    const user = userEvent.setup();
    renderHome();

    await user.type(screen.getByRole("textbox"), "   ");

    expect(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    ).toBeDisabled();
    expect(classifyMock).not.toHaveBeenCalled();
  });

  it("버튼을 우회한 폼 제출도 빈 입력이면 막는다", () => {
    // disabled 버튼은 시각적 가드일 뿐이다. form.requestSubmit() 이나
    // 확장/자동화가 submit 이벤트를 직접 쏘면 그대로 통과한다 —
    // onSubmit 안의 trim 가드가 두 번째 방벽이고, 여기서만 잴 수 있다.
    renderHome();
    const form = screen.getByRole("textbox").closest("form")!;
    fireEvent.submit(form);
    expect(classifyMock).not.toHaveBeenCalled();
  });

  it("입력 상한이 1000자다 — 백엔드 검증과 같은 숫자", () => {
    renderHome();
    expect(screen.getByRole("textbox")).toHaveAttribute("maxlength", "1000");
  });

  it("제출 중에는 버튼 문구가 바뀌고 다시 눌리지 않는다", async () => {
    const user = userEvent.setup();
    // 두 번 눌러 감정 로그가 두 줄 쌓이는 것을 막는 가드.
    let release!: (v: ClassifyResponse) => void;
    classifyMock.mockReturnValue(
      new Promise<ClassifyResponse>((r) => {
        release = r;
      }),
    );
    renderHome();

    await user.type(screen.getByRole("textbox"), "지쳤어");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    const pending = await screen.findByRole("button", { name: "분류 중..." });
    expect(pending).toBeDisabled();

    release(RESULT);
    await screen.findByText("외로움");
    expect(classifyMock).toHaveBeenCalledTimes(1);
  });
});

describe("분류 결과", () => {
  it("감정 코드를 한글 라벨과 % 신뢰도로 보여 준다", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "오늘 너무 외롭고 지쳐있어");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText("외로움")).toBeInTheDocument();
    // 0.876 → 88% (반올림). 소수점이 그대로 새면 "87.6% 신뢰도" 가 나온다.
    expect(screen.getByText(/88% 신뢰도/)).toBeInTheDocument();
    expect(classifyMock).toHaveBeenCalledWith("오늘 너무 외롭고 지쳐있어");
  });

  it("매핑에 없는 감정 코드는 원문 그대로 내보낸다", async () => {
    // 백엔드가 감정 종류를 늘리면 프론트 라벨 맵보다 먼저 도착한다.
    // 그때 빈 칸이 아니라 원문 코드라도 보여야 한다.
    const user = userEvent.setup();
    classifyMock.mockResolvedValue({
      ...RESULT,
      primary: { emotion: "HOPEFUL" as never, confidence: 0.5 },
    });
    renderHome();

    await user.type(screen.getByRole("textbox"), "괜찮아지고 있어");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText("HOPEFUL")).toBeInTheDocument();
  });

  it("결과가 나오면 직접 진입 카드 묶음은 물러난다", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "외로워");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    await screen.findByText("외로움");
    expect(screen.queryByRole("heading", { name: /각성의 순간/ })).toBeNull();
    expect(screen.queryByRole("link", { name: /7 가치 빌더/ })).toBeNull();
  });

  it("Track B — 구현된 인물은 실제 경로로, 미구현은 Phase 2 + '#' 으로", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "외로워");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    await screen.findByText("외로움");
    expect(screen.getByRole("link", { name: /elijah/ })).toHaveAttribute(
      "href",
      "/elijah",
    );
    // 미구현 인물을 /rahab 으로 보내면 404 다. 막다른 길 대신 그 자리에 머문다.
    const rahab = screen.getByRole("link", { name: /rahab/ });
    expect(rahab).toHaveAttribute("href", "#");
    expect(within(rahab).getByText("Phase 2")).toBeInTheDocument();
    expect(screen.getByText("로뎀나무 아래의 탈진")).toBeInTheDocument();
  });

  it("Track B 의 '구현됨' 판정이 홈 카드 목록과 어긋나지 않는다", async () => {
    // 이 화면은 인물이 열렸는지를 **두 곳에서 따로** 판정한다 — 위쪽 미션 카드는
    // DIRECT_MISSIONS 의 `active`, 아래쪽 추천 카드는 map 안의 인라인 ACTIVE 집합.
    // 룻을 붙일 때 둘 다 고쳐야 했고, 한쪽만 고치면 아무 테스트도 빨개지지 않은 채
    // "홈에서는 들어가지는데 추천에서는 Phase 2 로 막히는" 반쪽 문이 남는다.
    // 그래서 두 목록을 서로 대조한다 — 소스를 읽지 않고 렌더된 결과끼리 맞춘다.
    const user = userEvent.setup();
    renderHome();

    const missions = screen.getByRole("heading", {
      name: /각성의 순간/,
    }).parentElement!;
    const opened = within(missions)
      .getAllByRole("link")
      .map((a) => a.getAttribute("href"))
      .filter((h): h is string => !!h && h.startsWith("/"))
      .map((h) => h.slice(1));
    expect(opened.length).toBeGreaterThan(0); // 파싱이 0건이면 아래는 아무것도 안 잰다

    classifyMock.mockResolvedValue({
      ...RESULT,
      recommendations: {
        ...RESULT.recommendations,
        trackB: opened.map((character) => ({ character, rationale: "대조용" })),
      },
    });

    await user.type(screen.getByRole("textbox"), "외로워");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );
    await screen.findByText("외로움");

    const blocked = opened.filter(
      (c) =>
        screen
          .getByRole("link", { name: new RegExp(c) })
          .getAttribute("href") !== `/${c}`,
    );
    expect(
      blocked,
      `홈 카드는 열려 있는데 Track B 추천은 Phase 2 로 막는 인물: ${blocked.join(", ")}`,
    ).toEqual([]);
  });

  it("Track A — 제목은 항상, 이유는 있을 때만 보여 준다", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "외로워");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText("시편 — 탄식의 언어")).toBeInTheDocument();
    expect(screen.getByText("감정을 말로")).toBeInTheDocument();
    // rationale 이 없는 항목도 제목은 나와야 한다.
    expect(screen.getByText("잠언 — 마음지키기")).toBeInTheDocument();
  });

  it("추천 배열 자체가 빠진 응답에도 죽지 않는다", async () => {
    // 백엔드가 trackA/trackB 키를 생략하면 .length 접근에서 통째로 터진다.
    // `?? 0` 가드가 그 자리를 막고 있는지 실제로 확인한다 —
    // 여기서 새면 분류에 성공해 놓고 흰 화면이 뜬다.
    const user = userEvent.setup();
    classifyMock.mockResolvedValue({
      ...RESULT,
      recommendations: {} as never,
    });
    renderHome();

    await user.type(screen.getByRole("textbox"), "괜찮아");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText("외로움")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /Track/ })).toBeNull();
  });

  it("추천이 비어도 결과 화면이 깨지지 않는다", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue({
      ...RESULT,
      recommendations: { trackA: [], trackB: [] },
    });
    renderHome();

    await user.type(screen.getByRole("textbox"), "괜찮아");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    await screen.findByText("외로움");
    expect(screen.queryByRole("heading", { name: /Track B/ })).toBeNull();
    expect(screen.queryByRole("heading", { name: /Track A/ })).toBeNull();
    // 추천이 하나도 없어도 요셉 미션으로 가는 탈출구는 남는다.
    expect(
      screen.getByRole("link", { name: /요셉 미션 바로 시작/ }),
    ).toHaveAttribute("href", "/joseph");
  });
});

describe("분류 실패", () => {
  it("오류 메시지를 보여 주고, 직접 진입 카드는 그대로 남는다", async () => {
    const user = userEvent.setup();
    classifyMock.mockRejectedValue(new Error("Network Error"));
    renderHome();

    await user.type(screen.getByRole("textbox"), "지쳤어");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText(/오류: Network Error/)).toBeInTheDocument();
    // 백엔드가 죽어도 콘텐츠로 들어가는 길이 살아 있어야 한다는 게 요점이다.
    await waitFor(() =>
      expect(screen.getByRole("link", { name: /Joseph/ })).toBeInTheDocument(),
    );
  });

  it("재시도하면 다시 호출한다 — 실패 상태에 갇히지 않는다", async () => {
    const user = userEvent.setup();
    classifyMock
      .mockRejectedValueOnce(new Error("Network Error"))
      .mockResolvedValueOnce(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "지쳤어");
    const submit = screen.getByRole("button", {
      name: "감정 분석 + 본문 추천",
    });
    await user.click(submit);
    await screen.findByText(/오류: Network Error/);

    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );
    expect(await screen.findByText("외로움")).toBeInTheDocument();
    expect(classifyMock).toHaveBeenCalledTimes(2);
  });
});
