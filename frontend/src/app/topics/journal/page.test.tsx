import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";

import {
  CRISIS_DEFAULT,
  CRISIS_LINE_FULL,
  CRISIS_LINE_SHORT,
  telHref,
} from "@/lib/crisis-resources";
import JournalGuidancePage from "./page";
import {
  fetchJournalGuidance,
  requestJournalGuidance,
  type Guidance,
  type GuidanceResponse,
} from "@/lib/api/content";

vi.mock("@/lib/api/content", () => ({
  fetchJournalGuidance: vi.fn(),
  requestJournalGuidance: vi.fn(),
}));

/*
  NarrationAudioButton 은 이 담당 범위 밖의 컴포넌트이고, 실제 구현은 TTS 훅/사이드카에
  묶여 있다. 여기서 재고 싶은 것은 버튼의 내부가 아니라 **페이지가 나레이션에 무엇을
  실어 보내는가** — 고령자가 화면 대신 귀로 받는 내용이 조언 전체인지다. 그래서
  스텁이 받은 text 를 화면에 드러내 검증 가능하게 만든다.
*/
vi.mock("@/components/NarrationAudioButton", () => ({
  NarrationAudioButton: ({ text, label }: { text: string; label?: string }) => (
    <button type="button" aria-label={label ?? "듣기"}>
      <span data-testid="narration-script">{text}</span>
    </button>
  ),
}));

const mockFetch = vi.mocked(fetchJournalGuidance);
const mockRequest = vi.mocked(requestJournalGuidance);

const AI_FOOTER =
  "AI 보조 — 본문은 성경 참조. 성경 외 자료는 조언 근거로 쓰지 않습니다.";
const SAFETY_FOOTER =
  "이 조언은 치료가 아닙니다. 필요하면 전문가를 찾아주세요.";

function guidance(over: Partial<Guidance> = {}): Guidance {
  return {
    emotion: "ANXIOUS",
    emotionLabel: "불안",
    validation: "불안한 마음은 성경 안에도 있습니다.",
    verses: [
      { ref: "시 55:22", text: "네 짐을 여호와께 맡기라." },
      { ref: "벧전 5:7", text: "너희 염려를 다 주께 맡기라." },
    ],
    reflectionQuestions: [
      "오늘 가장 무거웠던 순간은 언제였나요?",
      "누구에게 말해볼 수 있을까요?",
    ],
    ...over,
  };
}

function response(over: Partial<GuidanceResponse> = {}): GuidanceResponse {
  return {
    guidance: null,
    catalog: [],
    crisis: { routed: false, resources: [] },
    safetyFooter: SAFETY_FOOTER,
    aiFooter: AI_FOOTER,
    ...over,
  };
}

const CATALOG = response({
  catalog: [
    guidance(),
    guidance({ emotion: "SAD", emotionLabel: "슬픔" }),
    guidance({ emotion: "GRATEFUL", emotionLabel: "감사" }),
  ],
});

function renderPage(ui: ReactElement = <JournalGuidancePage />) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  // 인자 없는 호출 = 카탈로그 GET, 인자 있는 호출 = 그 감정의 단건 조언.
  mockFetch.mockImplementation(async (emotion?: string) =>
    emotion
      ? response({ guidance: guidance({ emotion, emotionLabel: "불안" }) })
      : CATALOG,
  );
  mockRequest.mockResolvedValue(response({ guidance: guidance() }));
});

describe("/topics/journal — AI 라벨링 (4-layer 안전선 3번)", () => {
  it("백엔드가 준 AI 라벨 문구를 화면 하단에 그대로 노출한다", async () => {
    // 이 라벨이 빠지면 사용자가 LLM/규칙 생성물을 사람의 조언으로 오해한다.
    renderPage();

    expect(
      await screen.findByText(new RegExp(AI_FOOTER.slice(0, 12))),
    ).toBeInTheDocument();
  });

  it("카탈로그 조회가 실패해도 'AI 보조' 라벨은 남는다", async () => {
    // 라벨은 백엔드 가용성에 의존하면 안 된다 — 폴백 상수가 그 보증이다.
    mockFetch.mockRejectedValue(new Error("catalog down"));
    renderPage();

    await waitFor(() =>
      expect(screen.getByText(/AI 보조/)).toBeInTheDocument(),
    );
  });

  it("조언이 화면에 뜬 상태에서도 AI 라벨이 함께 보인다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "불안" }));
    await screen.findByText("불안한 마음은 성경 안에도 있습니다.");

    // 조언 본문과 AI 라벨이 동시에 존재해야 라벨이 조언을 실제로 수식한다.
    expect(screen.getByText(/AI 보조/)).toBeInTheDocument();
  });

  it("위기 라우팅 화면에서도 AI 라벨이 사라지지 않는다", async () => {
    const user = userEvent.setup();
    mockRequest.mockResolvedValue(
      response({
        crisis: { routed: true, resources: [] },
        guidance: guidance(),
      }),
    );
    renderPage();

    await user.type(await screen.findByRole("textbox"), "너무 힘들어요");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    await screen.findByText("잠시 멈추고, 함께 안전을 살펴봐요.");
    expect(screen.getByText(/AI 보조/)).toBeInTheDocument();
  });
});

describe("/topics/journal — 감정 선택", () => {
  it("카탈로그의 감정만 칩으로 그린다", async () => {
    renderPage();

    expect(
      await screen.findByRole("button", { name: "불안" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "슬픔" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "감사" })).toBeInTheDocument();
  });

  it("카탈로그가 비어 있으면 칩이 하나도 없다", async () => {
    // 2026-08-12 기록 — 백엔드 없이 도는 CI 에서 칩이 아예 안 그려졌다.
    // 그 '잴 것이 없는' 상태를 명시적으로 고정해 둔다.
    mockFetch.mockResolvedValue(response({ catalog: [] }));
    renderPage();

    await screen.findByRole("heading", { name: "일기와 조언" });
    expect(
      screen.queryByRole("button", { name: "불안" }),
    ).not.toBeInTheDocument();
  });

  it("감정 칩을 누르면 그 감정의 조언을 받아 보여준다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "슬픔" }));

    expect(mockFetch).toHaveBeenCalledWith("SAD");
    expect(await screen.findByText(/감정 ·/)).toBeInTheDocument();
    expect(
      screen.getByText("불안한 마음은 성경 안에도 있습니다."),
    ).toBeInTheDocument();
  });
});

describe("/topics/journal — 일기 텍스트로 조언 받기", () => {
  it("일기도 감정도 없으면 조언 버튼을 누를 수 없다", async () => {
    renderPage();

    expect(
      await screen.findByRole("button", { name: "성경 조언 받기" }),
    ).toBeDisabled();
  });

  it("공백만 입력한 것은 입력으로 치지 않는다", async () => {
    // text.trim() 가드 — 빈 요청이 백엔드로 나가지 않아야 한다.
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByRole("textbox"), "   ");

    expect(
      screen.getByRole("button", { name: "성경 조언 받기" }),
    ).toBeDisabled();
  });

  it("감정만 골라도 조언을 요청할 수 있다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "감사" }));
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    // 일기 텍스트는 선택 사항 — undefined 로 빠지고 감정만 실려 나간다.
    expect(mockRequest).toHaveBeenCalledWith({
      text: undefined,
      emotion: "GRATEFUL",
    });
  });

  it("일기 텍스트를 다듬어(trim) 감정과 함께 보낸다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "불안" }));
    await user.type(
      await screen.findByRole("textbox"),
      "  오늘 일이 손에 잡히지 않았다  ",
    );
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    expect(mockRequest).toHaveBeenCalledWith({
      text: "오늘 일이 손에 잡히지 않았다",
      emotion: "ANXIOUS",
    });
  });

  it("요청 중에는 버튼 문구가 진행 상태로 바뀌고 잠긴다", async () => {
    const user = userEvent.setup();
    let release: ((r: GuidanceResponse) => void) | undefined;
    mockRequest.mockReturnValue(
      new Promise<GuidanceResponse>((resolve) => {
        release = resolve;
      }),
    );
    renderPage();

    await user.type(await screen.findByRole("textbox"), "오늘 하루");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    const pending = await screen.findByRole("button", {
      name: "조언 받는 중...",
    });
    expect(pending).toBeDisabled();
    release?.(response({ guidance: guidance() }));
  });

  it("요청이 실패하면 실패했다고 말한다", async () => {
    const user = userEvent.setup();
    mockRequest.mockRejectedValue(new Error("500"));
    renderPage();

    await user.type(await screen.findByRole("textbox"), "오늘 하루");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    expect(await screen.findByText("불러오지 못했습니다.")).toBeInTheDocument();
  });

  it("감정 칩 조회가 실패해도 같은 자리에 실패를 알린다", async () => {
    const user = userEvent.setup();
    mockFetch.mockImplementation(async (emotion?: string) => {
      if (emotion) throw new Error("boom");
      return CATALOG;
    });
    renderPage();

    await user.click(await screen.findByRole("button", { name: "불안" }));

    expect(await screen.findByText("불러오지 못했습니다.")).toBeInTheDocument();
  });

  it("일기 입력은 5000자로 제한된다", async () => {
    // 서버 저장 한도와 어긋나면 사용자는 다 쓰고 나서야 거절당한다.
    renderPage();

    expect(await screen.findByRole("textbox")).toHaveAttribute(
      "maxLength",
      "5000",
    );
  });
});

describe("/topics/journal — 조언 표시", () => {
  it("인증 문구 · 성경 구절 · 성찰 질문 · 안전 footer 를 모두 보여준다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByRole("textbox"), "불안했다");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    expect(
      await screen.findByText("불안한 마음은 성경 안에도 있습니다."),
    ).toBeInTheDocument();
    expect(screen.getByText("감정 · 불안")).toBeInTheDocument();
    expect(screen.getByText("📖 시 55:22")).toBeInTheDocument();
    expect(screen.getByText("네 짐을 여호와께 맡기라.")).toBeInTheDocument();
    expect(screen.getByText("📖 벧전 5:7")).toBeInTheDocument();
    expect(
      screen.getByText("오늘 가장 무거웠던 순간은 언제였나요?"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("누구에게 말해볼 수 있을까요?"),
    ).toBeInTheDocument();
    expect(screen.getByText(SAFETY_FOOTER)).toBeInTheDocument();
  });

  it("들려주는 스크립트에 인증 문구·구절·성찰 질문이 모두 들어간다", async () => {
    // 눈으로 좇기 어려운 사용자는 이 스크립트가 조언의 전부다 — 빠지면 조용히 반쪽이 된다.
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByRole("textbox"), "불안했다");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    const script = await screen.findByTestId("narration-script");
    expect(script).toHaveTextContent("불안한 마음은 성경 안에도 있습니다.");
    expect(script).toHaveTextContent("시 55:22. 네 짐을 여호와께 맡기라.");
    expect(script).toHaveTextContent("성찰 질문입니다.");
    expect(script).toHaveTextContent("오늘 가장 무거웠던 순간은 언제였나요?");
  });

  it("성찰 질문이 없으면 스크립트에 그 안내를 넣지 않는다", async () => {
    const user = userEvent.setup();
    mockRequest.mockResolvedValue(
      response({ guidance: guidance({ reflectionQuestions: [] }) }),
    );
    renderPage();

    await user.type(await screen.findByRole("textbox"), "불안했다");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    const script = await screen.findByTestId("narration-script");
    expect(script).toHaveTextContent("불안한 마음은 성경 안에도 있습니다.");
    expect(script).not.toHaveTextContent("성찰 질문입니다.");
  });

  it("조언 자체가 없는 응답이면 조언 영역을 그리지 않는다", async () => {
    const user = userEvent.setup();
    mockRequest.mockResolvedValue(response({ guidance: null }));
    renderPage();

    await user.type(await screen.findByRole("textbox"), "그냥");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    await waitFor(() => expect(mockRequest).toHaveBeenCalled());
    expect(screen.queryByText("성찰 질문")).not.toBeInTheDocument();
    expect(screen.queryByText(SAFETY_FOOTER)).not.toBeInTheDocument();
  });
});

describe("/topics/journal — R1 위기 라우팅", () => {
  it("위기로 판정되면 조언 대신 안전 안내와 상담 번호를 먼저 보여준다", async () => {
    const user = userEvent.setup();
    mockRequest.mockResolvedValue(
      response({
        crisis: { routed: true, resources: [] },
        guidance: guidance(),
      }),
    );
    renderPage();

    await user.type(await screen.findByRole("textbox"), "다 그만두고 싶다");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    expect(
      await screen.findByText("잠시 멈추고, 함께 안전을 살펴봐요."),
    ).toBeInTheDocument();
    expect(screen.getByText(CRISIS_LINE_SHORT)).toBeInTheDocument();
    // 위기 응답에 guidance 가 함께 와도 조언 카드는 가려져야 한다 (안전이 먼저).
    expect(screen.queryByText("감정 · 불안")).not.toBeInTheDocument();
    expect(screen.queryByText("성찰 질문")).not.toBeInTheDocument();
  });

  it("위기 카드는 자기 자신이 아니라 전화로 연결한다", async () => {
    // 원래 이 자리는 「일기(#1)로 마음을 먼저 적어보기 →」 링크였고 목적지가
    // /topics/journal — 즉 **지금 보고 있는 이 페이지** 였다. 눌러도 아무 데도 못
    // 가므로, 위기 순간에 제시되는 유일한 다음 행동이 죽어 있었다.
    //
    // 다른 두 화면(전도서·실천)은 그 링크가 맞다 — 거기서는 실제로 일기로 이동한다.
    // 여기서만 이동이 아니라 행동(전화)을 준다.
    const user = userEvent.setup();
    mockRequest.mockResolvedValue(
      response({ crisis: { routed: true, resources: [] } }),
    );
    renderPage();

    await user.type(await screen.findByRole("textbox"), "다 그만두고 싶다");
    await user.click(screen.getByRole("button", { name: "성경 조언 받기" }));

    const link = await screen.findByRole("link", {
      name: new RegExp(`${CRISIS_DEFAULT.tel} 지금 전화하기`),
    });
    expect(link).toHaveAttribute("href", telHref(CRISIS_DEFAULT));
    // 자기 자신으로 가는 링크는 이 카드에 남아 있으면 안 된다.
    expect(
      screen.queryByRole("link", { name: /마음을 먼저 적어보기/ }),
    ).toBeNull();
  });
});

describe("/topics/journal — 상시 노출", () => {
  it("위기 자원 전체 표기를 항상 하단에 둔다", async () => {
    renderPage();

    expect(
      await screen.findByText(new RegExp(CRISIS_LINE_FULL.slice(0, 10))),
    ).toBeInTheDocument();
  });

  it("주제 인덱스로 돌아가는 링크를 제공한다", async () => {
    renderPage();

    expect(await screen.findByRole("link", { name: /← 주제/ })).toHaveAttribute(
      "href",
      "/topics",
    );
  });
});
