import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import type { PracticeListResponse, PracticeResponse } from "@/lib/api/content";
import { fetchPractices, recordPractice } from "@/lib/api/content";
import PracticePage from "./page";

/**
 * /topics/practice — Theme 6·7 실천 기록 화면.
 *
 * 이 화면의 실패는 "레이아웃이 깨진다" 가 아니라 **사용자가 적은 것이 사라지거나,
 * 위기 신호를 보냈는데 상담 번호가 안 뜨는** 종류다. 그래서 여기서 재는 축은 셋이다.
 *  1. 사용자가 적은 값이 그대로 API 에 실려 나가는가 (payload)
 *  2. R1 — crisis.routed 응답이 오면 위기 카드 + 24시간 번호가 실제로 뜨는가
 *  3. 4-layer 안전선 3번 — "AI 보조" 라벨과 위기 자원 라인이 *어떤 상태에서도* 남는가
 *     (히스토리 API 가 죽어도 이 두 줄은 사라지면 안 된다)
 */

// api 모듈 전체를 모킹한다. axios/네트워크는 이 화면의 관심사가 아니고,
// 관심사는 "무엇을 보내고 무엇을 그리는가" 다.
vi.mock("@/lib/api/content", () => ({
  recordPractice: vi.fn(),
  fetchPractices: vi.fn(),
}));

const mockRecord = vi.mocked(recordPractice);
const mockFetch = vi.mocked(fetchPractices);

function renderPage(): { user: ReturnType<typeof userEvent.setup> } {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const ui: ReactElement = (
    <QueryClientProvider client={client}>
      <PracticePage />
    </QueryClientProvider>
  );
  render(ui);
  return { user: userEvent.setup() };
}

/** 카드는 화면에 둘씩 뜬다 — 제목(h3)이 속한 <article> 안으로 범위를 좁혀서 조작한다. */
function card(label: string) {
  const heading = screen.getByRole("heading", { name: label });
  const article = heading.closest("article");
  if (!article) throw new Error(`카드 <article> 을 찾지 못함: ${label}`);
  return within(article);
}

/** 히스토리 섹션. 카드 제목과 히스토리 항목 라벨이 같은 문자열이라 범위를 나눠야 한다. */
async function findHistorySection(): Promise<HTMLElement> {
  const heading = await screen.findByText("지금까지의 자리");
  const section = heading.closest("section");
  if (!section) throw new Error("히스토리 <section> 을 찾지 못함");
  return section;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function practiceResponse(
  over: Partial<PracticeResponse> = {},
): PracticeResponse {
  return {
    practice: {
      id: 1,
      topicId: 7,
      practiceKind: "courage_act",
      situation: "회의에서 한 번 아니라고 말했다",
      reflection: null,
      actionTaken: true,
      scriptureRef: "prov-29:25",
      dimension: "spiritual",
      createdAt: "2026-08-01T09:00:00Z",
    },
    crisis: { routed: false, resources: [] },
    safetyFooter: "오늘 한 걸음이면 충분합니다. 남은 것은 내일의 몫입니다.",
    aiFooter: "AI 보조 — 본문은 성경 참조.",
    ...over,
  };
}

const EMPTY_LIST: PracticeListResponse = {
  topicId: 7,
  items: [],
  actionCount: 0,
};

beforeEach(() => {
  mockFetch.mockResolvedValue(EMPTY_LIST);
  mockRecord.mockResolvedValue(practiceResponse());
});

describe("/topics/practice", () => {
  it("첫 진입은 Theme 7 — 두 주제 중 '사람 두려움' 을 먼저 연다 (§11)", async () => {
    // 기본 주제가 6으로 바뀌면 사용자는 설계된 순서(내면 → 밖으로)를 잃는다.
    renderPage();

    expect(
      screen.getByRole("heading", { name: "실천과 성찰", level: 1 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/사람을 두려워하면 올무에 걸리게 되거니와/),
    ).toBeInTheDocument();
    // Theme 7 의 두 입력 카드가 열려 있다.
    expect(
      screen.getByRole("heading", { name: "한 번의 거절 / 용기" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "생각 기록" }),
    ).toBeInTheDocument();
    // Theme 6 본문은 아직 없다.
    expect(screen.queryByText(/더욱 네 마음을 지키라/)).not.toBeInTheDocument();
  });

  it("주제 버튼을 누르면 본문·진입 안내·입력 카드가 통째로 Theme 6 으로 바뀐다", async () => {
    const { user } = renderPage();

    await user.click(screen.getByRole("button", { name: /마음을 지키는 것/ }));

    expect(screen.getByText("📖 prov-4:23")).toBeInTheDocument();
    expect(screen.getByText(/더욱 네 마음을 지키라/)).toBeInTheDocument();
    // R2 — 진입 단계부터 압박 없는 문구가 붙는다.
    expect(
      screen.getByText(/오늘 한 가지만 다르게 해보는 것도 충분합니다/),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "오늘 내 마음을 흔든 일" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "이번 주 한 사람에게 할 한 문장" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "한 번의 거절 / 용기" }),
    ).not.toBeInTheDocument();
  });

  it("체크박스 문구는 기록 종류마다 다르다 — '보냈어요/해봤어요/옮겼어요'", async () => {
    // 라벨이 뭉개지면 '문장을 보냈다' 와 '생각만 적었다' 가 같은 기록이 된다.
    const { user } = renderPage();

    expect(
      card("한 번의 거절 / 용기").getByLabelText("오늘 실제로 해봤어요"),
    ).toBeInTheDocument();
    expect(
      card("생각 기록").getByLabelText("행동으로 옮겼어요"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /마음을 지키는 것/ }));

    expect(
      card("이번 주 한 사람에게 할 한 문장").getByLabelText(
        "이 문장을 실제로 보냈어요",
      ),
    ).toBeInTheDocument();
    expect(
      card("오늘 내 마음을 흔든 일").getByLabelText("행동으로 옮겼어요"),
    ).toBeInTheDocument();
  });

  it("아무것도 없으면 기록 버튼이 잠기고, 체크만 해도 열린다", async () => {
    // 빈 기록이 저장되면 히스토리가 빈 카드로 오염된다. 반대로 '행동만 함'
    // (글 없이 체크만) 은 유효한 기록이므로 열려야 한다.
    const { user } = renderPage();
    const c = card("한 번의 거절 / 용기");

    expect(c.getByRole("button", { name: "기록하기" })).toBeDisabled();

    await user.click(c.getByLabelText("오늘 실제로 해봤어요"));
    expect(c.getByRole("button", { name: "기록하기" })).toBeEnabled();
  });

  it("공백만 적은 것은 입력으로 치지 않는다", async () => {
    const { user } = renderPage();
    const c = card("생각 기록");

    await user.type(c.getByRole("textbox"), "   ");
    expect(c.getByRole("button", { name: "기록하기" })).toBeDisabled();
  });

  it("적은 내용·체크·주제·성구 참조가 그대로 API 로 나가고, 성공하면 입력이 비워진다", async () => {
    const { user } = renderPage();
    const c = card("한 번의 거절 / 용기");

    await user.type(c.getByRole("textbox"), "오늘 야근 요청을 거절했다");
    await user.click(c.getByLabelText("오늘 실제로 해봤어요"));
    await user.click(c.getByRole("button", { name: "기록하기" }));

    await waitFor(() =>
      expect(mockRecord).toHaveBeenCalledWith({
        topicId: 7,
        practiceKind: "courage_act",
        situation: "오늘 야근 요청을 거절했다",
        actionTaken: true,
        scriptureRef: "prov-29:25",
        dimension: "spiritual",
      }),
    );

    // R2 safetyFooter 가 서버 값 그대로 보인다 (프론트가 임의 문구로 덮지 않는다).
    expect(await c.findByText("✓ 기록되었습니다.")).toBeInTheDocument();
    expect(
      c.getByText("오늘 한 걸음이면 충분합니다. 남은 것은 내일의 몫입니다."),
    ).toBeInTheDocument();
    // 다음 기록을 위해 폼이 초기화된다.
    expect(c.getByRole("textbox")).toHaveValue("");
    expect(c.getByLabelText("오늘 실제로 해봤어요")).not.toBeChecked();
  });

  it("글 없이 행동만 체크해 기록하면 situation 없이 보낸다", async () => {
    // 빈 문자열을 보내면 히스토리에 빈 줄짜리 카드가 남는다. '적지 않음' 은
    // 빈 문자열이 아니라 없음이어야 한다.
    const { user } = renderPage();
    const c = card("생각 기록");

    await user.click(c.getByLabelText("행동으로 옮겼어요"));
    await user.click(c.getByRole("button", { name: "기록하기" }));

    await waitFor(() =>
      expect(mockRecord).toHaveBeenCalledWith({
        topicId: 7,
        practiceKind: "thought_record",
        situation: undefined,
        actionTaken: true,
        scriptureRef: "prov-29:25",
        dimension: "rational",
      }),
    );
  });

  it("R1 — crisis.routed 응답이면 위기 카드와 24시간 상담 번호(109)를 띄운다", async () => {
    // 이 화면에서 가장 비싼 실패. 위기 신호를 보냈는데 평범한 '기록되었습니다' 만
    // 보이면 사용자는 아무 도움도 받지 못한 채 화면을 닫는다.
    mockRecord.mockResolvedValue(
      practiceResponse({ crisis: { routed: true, resources: [] } }),
    );
    const { user } = renderPage();
    const c = card("생각 기록");

    await user.type(c.getByRole("textbox"), "다 끝내고 싶다");
    await user.click(c.getByRole("button", { name: "기록하기" }));

    expect(
      await c.findByText("잠시 멈추고, 함께 안전을 살펴봐요."),
    ).toBeInTheDocument();
    // 위기 카드 안의 상담 라인은 번호와 "24시간" 을 함께 준다.
    expect(c.getByText(/109/)).toHaveTextContent("24시간");
    // 위기일 때는 일반 완료 문구로 덮지 않는다.
    expect(c.queryByText("✓ 기록되었습니다.")).not.toBeInTheDocument();
  });

  it("저장이 실패하면 실패를 알리고, 사용자가 쓴 글은 지우지 않는다", async () => {
    // 실패 시 텍스트까지 날아가면 사용자는 방금 쓴 문장을 통째로 잃는다.
    mockRecord.mockRejectedValue(new Error("500"));
    const { user } = renderPage();
    const c = card("한 번의 거절 / 용기");

    await user.type(c.getByRole("textbox"), "지우면 안 되는 문장");
    await user.click(c.getByRole("button", { name: "기록하기" }));

    expect(await c.findByText("저장에 실패했습니다.")).toBeInTheDocument();
    expect(c.getByRole("textbox")).toHaveValue("지우면 안 되는 문장");
  });

  it("저장 중에는 버튼이 '기록 중...' 으로 잠겨 중복 전송을 막는다", async () => {
    const d = deferred<PracticeResponse>();
    mockRecord.mockReturnValue(d.promise);
    const { user } = renderPage();
    const c = card("생각 기록");

    await user.type(c.getByRole("textbox"), "생각 하나");
    await user.click(c.getByRole("button", { name: "기록하기" }));

    const pending = await c.findByRole("button", { name: "기록 중..." });
    expect(pending).toBeDisabled();

    await act(async () => {
      d.resolve(practiceResponse());
    });
    expect(
      await c.findByRole("button", { name: "기록하기" }),
    ).toBeInTheDocument();
  });

  it("히스토리가 있으면 누적 행동 횟수·종류 라벨·본문을 보여준다", async () => {
    mockFetch.mockResolvedValue({
      topicId: 7,
      actionCount: 3,
      items: [
        {
          id: 11,
          topicId: 7,
          practiceKind: "courage_act",
          situation: "야근 요청을 거절했다",
          reflection: null,
          actionTaken: true,
          scriptureRef: "prov-29:25",
          dimension: "spiritual",
          createdAt: "2026-08-01T09:00:00Z",
        },
        {
          id: 12,
          topicId: 7,
          practiceKind: "thought_record",
          situation: null,
          reflection: null,
          actionTaken: false,
          scriptureRef: null,
          dimension: null,
          createdAt: "2026-08-02T09:00:00Z",
        },
      ],
    });
    renderPage();

    const history = within(await findHistorySection());
    expect(history.getByText("작은 행동 3회 누적")).toBeInTheDocument();
    // kind 코드가 아니라 사람이 읽는 라벨로 번역돼야 한다.
    expect(history.getByText("한 번의 거절 / 용기")).toBeInTheDocument();
    expect(history.getByText("생각 기록")).toBeInTheDocument();
    expect(history.getByText("야근 요청을 거절했다")).toBeInTheDocument();
    // 행동으로 옮긴 기록에만 ✓ 가 붙는다 (두 번째 항목은 actionTaken=false).
    expect(history.getAllByText(/· ✓ 행동/)).toHaveLength(1);
  });

  it("모르는 practiceKind 는 감추지 않고 원래 코드를 그대로 보여준다", async () => {
    // 백엔드가 새 종류를 추가했을 때 프론트가 항목을 통째로 숨기면 기록이 사라져 보인다.
    mockFetch.mockResolvedValue({
      topicId: 7,
      actionCount: 0,
      items: [
        {
          id: 21,
          topicId: 7,
          practiceKind: "gratitude_note",
          situation: "감사 기록",
          reflection: null,
          actionTaken: null,
          scriptureRef: null,
          dimension: null,
          createdAt: "2026-08-03T09:00:00Z",
        },
      ],
    });
    renderPage();

    expect(await screen.findByText("gratitude_note")).toBeInTheDocument();
    expect(screen.getByText("감사 기록")).toBeInTheDocument();
  });

  it("기록이 없으면 히스토리 섹션 자체를 만들지 않는다", async () => {
    renderPage();

    await waitFor(() => expect(mockFetch).toHaveBeenCalled());
    expect(screen.queryByText("지금까지의 자리")).not.toBeInTheDocument();
  });

  it("히스토리 조회가 실패해도 기록 폼·위기 자원 라인·AI 보조 라벨은 남는다", async () => {
    // 안전선은 API 가 죽어도 살아 있어야 한다. 이게 이 테스트의 전부다.
    mockFetch.mockRejectedValue(new Error("503"));
    renderPage();

    await waitFor(() => expect(mockFetch).toHaveBeenCalled());
    expect(
      screen.getByRole("heading", { name: "한 번의 거절 / 용기" }),
    ).toBeInTheDocument();
    expect(screen.getByText(/109/)).toBeInTheDocument();
    expect(screen.getByText(/AI 보조/)).toBeInTheDocument();
  });

  it("하단에 AI 보조 라벨과 위기 자원 라인이 항상 붙는다 (4-layer 안전선 3번)", () => {
    // 이게 빠지면 사용자는 화면의 문구를 사람이 쓴 상담으로 오해한다.
    renderPage();

    const footer = screen.getByText(/AI 보조 — storyteller/);
    expect(footer).toHaveTextContent("성경 이외 자료는 사용하지 않습니다.");
    expect(footer).toHaveTextContent(/109/);
    expect(screen.getByRole("link", { name: "← 주제" })).toHaveAttribute(
      "href",
      "/topics",
    );
  });

  it("주제를 바꾸면 그 주제의 히스토리를 다시 조회한다", async () => {
    const { user } = renderPage();

    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(7, 20));
    await user.click(screen.getByRole("button", { name: /마음을 지키는 것/ }));
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith(6, 20));
  });
});
