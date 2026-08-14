import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type {
  EcclesiastesCategoriesResponse,
  EcclesiastesListResponse,
  EcclesiastesResponse,
} from "@/lib/api/content";
import {
  fetchEcclesiastesCategories,
  fetchEcclesiastesViews,
  recordEcclesiastesView,
} from "@/lib/api/content";
import EcclesiastesPage from "./page";

/**
 * /topics/ecclesiastes — 기준4 (전도서와 인생).
 *
 * 이 화면은 "헛됨" 을 정면으로 다루기 때문에 안전선이 걸린 자리가 많다. 재는 축:
 *  1. R2 — 헛됨만 남기지 않는다: 전 12:13 결론 문구와 서버 safetyFooter 가 실제로 뜨는가
 *  2. R1 — crisis.routed 면 위기 카드 + 24시간 번호
 *  3. 4-layer 안전선 3번 — "AI 보조" 라벨. 서버 aiFooter 가 오면 그걸, API 가 죽으면
 *     프론트 fallback 을 쓴다. *어느 쪽이든 라벨이 사라지면 안 된다* 는 게 핵심.
 *  4. 사용자가 적은 성찰이 그대로 payload 로 나가는가
 */

vi.mock("@/lib/api/content", () => ({
  fetchEcclesiastesCategories: vi.fn(),
  recordEcclesiastesView: vi.fn(),
  fetchEcclesiastesViews: vi.fn(),
}));

const mockCategories = vi.mocked(fetchEcclesiastesCategories);
const mockRecord = vi.mocked(recordEcclesiastesView);
const mockViews = vi.mocked(fetchEcclesiastesViews);

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <EcclesiastesPage />
    </QueryClientProvider>,
  );
  return { user: userEvent.setup() };
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

const CATEGORIES: EcclesiastesCategoriesResponse = {
  categories: [
    {
      key: "labor",
      title: "수고와 그 결과",
      chapterRef: "전 2:11",
      verse:
        "내가 나의 손으로 한 모든 일과 수고를 돌아본즉 다 헛되어 바람을 잡는 것이며",
      honestNote:
        "손에 남지 않았다는 감각은 실패가 아니라 유한함의 정직한 인정입니다.",
      meaningNote: "오늘 하나님이 맡기신 몫 한 가지를 적어보세요.",
    },
    {
      key: "time",
      title: "때와 기한",
      chapterRef: "전 3:1",
      verse: "범사에 기한이 있고 천하 만사가 다 때가 있나니",
      honestNote: "지금이 그 때가 아닐 수 있다는 것도 정직한 인정입니다.",
      meaningNote: "지금 이 계절에만 할 수 있는 일을 적어보세요.",
    },
  ],
  seasons: [
    { key: "plant", label: "심을 때", verse: "심을 때가 있고 (전 3:2)" },
    { key: "weep", label: "울 때", verse: "울 때가 있고 (전 3:4)" },
  ],
  aiFooter: "AI 보조 — 본문은 성경 참조. 전도서 외 자료는 쓰지 않습니다.",
};

const EMPTY_VIEWS: EcclesiastesListResponse = {
  items: [],
  conclusionViewedCount: 0,
  conclusionInvite: "결론까지 함께 읽어요.",
};

function ecclesiastesResponse(
  over: Partial<EcclesiastesResponse> = {},
): EcclesiastesResponse {
  return {
    view: {
      id: 1,
      chapterRef: "전 2:11",
      userSeason: "plant",
      futilityNote: "손에 남은 게 없다",
      meaningNote: "오늘의 밥과 잠",
      listenedAudio: null,
      conclusionViewed: true,
      createdAt: "2026-08-01T09:00:00Z",
    },
    crisis: { routed: false, resources: [] },
    safetyFooter: "헛됨을 인정한 자리에서 경외가 시작됩니다 (전 12:13).",
    conclusionInvite: "결론까지 함께 읽어요.",
    aiFooter: "AI 보조 — 본문은 성경 참조.",
    ...over,
  };
}

/** 카테고리를 하나 고른 뒤의 성찰 섹션. */
async function openReflection(user: ReturnType<typeof userEvent.setup>) {
  await user.click(
    await screen.findByRole("button", { name: /수고와 그 결과/ }),
  );
}

beforeEach(() => {
  mockCategories.mockResolvedValue(CATEGORIES);
  mockViews.mockResolvedValue(EMPTY_VIEWS);
  mockRecord.mockResolvedValue(ecclesiastesResponse());
});

describe("/topics/ecclesiastes", () => {
  it("카테고리를 불러오는 동안에도 위기 자원 라인과 AI 보조 라벨은 이미 떠 있다", async () => {
    // 로딩 상태. 네트워크가 느린 사람에게도 안전선은 처음부터 보여야 한다.
    const d = deferred<EcclesiastesCategoriesResponse>();
    mockCategories.mockReturnValue(d.promise);
    renderPage();

    expect(
      screen.getByText(
        "위 카테고리에서 오늘 마음에 닿는 진리를 하나 골라보세요.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /수고와 그 결과/ }),
    ).not.toBeInTheDocument();
    // 서버 aiFooter 가 아직 없으니 프론트 fallback 이 자리를 지킨다.
    const footer = screen.getByText(/AI 보조/);
    expect(footer).toHaveTextContent(
      "전도서와 관련 성구 외 자료는 사용하지 않습니다.",
    );
    expect(footer).toHaveTextContent(/109/);

    await act(async () => {
      d.resolve(CATEGORIES);
    });
  });

  it("카테고리를 받으면 성구 참조와 제목이 버튼으로 뜨고, AI 라벨은 서버 문구로 바뀐다", async () => {
    renderPage();

    expect(
      await screen.findByRole("button", { name: /수고와 그 결과/ }),
    ).toBeInTheDocument();
    expect(screen.getByText("📖 전 2:11")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /때와 기한/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/전도서 외 자료는 쓰지 않습니다/),
    ).toBeInTheDocument();
  });

  it("카테고리 조회가 실패해도 위기 자원 라인과 AI 보조 fallback 은 남는다", async () => {
    // 안전선은 API 가 죽어도 살아 있어야 한다.
    mockCategories.mockRejectedValue(new Error("503"));
    renderPage();

    await waitFor(() => expect(mockCategories).toHaveBeenCalled());
    const footer = screen.getByText(/AI 보조/);
    expect(footer).toHaveTextContent(
      "전도서와 관련 성구 외 자료는 사용하지 않습니다.",
    );
    expect(footer).toHaveTextContent(/109/);
    expect(
      screen.getByText(
        "위 카테고리에서 오늘 마음에 닿는 진리를 하나 골라보세요.",
      ),
    ).toBeInTheDocument();
  });

  it("카테고리를 고르면 본문·정직한 인정 문구·전 12:13 결론이 함께 열린다", async () => {
    // §4.4 결론 회피 금지 — 헛됨 축만 열리고 결론이 빠지면 이 화면은 절망만 남긴다.
    const { user } = renderPage();
    await openReflection(user);

    expect(
      screen.getByText(/내가 나의 손으로 한 모든 일과 수고를 돌아본즉/),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /손에 남지 않았다는 감각은 실패가 아니라 유한함의 정직한 인정입니다./,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/일의 결국을 다 들었으니 하나님을 경외하고/),
    ).toBeInTheDocument();
    expect(
      screen.getByLabelText("헛됨만 보지 않고 이 결론까지 함께 읽었어요"),
    ).toBeInTheDocument();
    // 의미 축 placeholder 는 카테고리별 문구를 그대로 쓴다.
    expect(
      screen.getByPlaceholderText(
        "오늘 하나님이 맡기신 몫 한 가지를 적어보세요.",
      ),
    ).toBeInTheDocument();
  });

  it("계절 선택지가 없는 응답이면 계절 UI 자체를 만들지 않는다", async () => {
    mockCategories.mockResolvedValue({ ...CATEGORIES, seasons: [] });
    const { user } = renderPage();
    await openReflection(user);

    expect(
      screen.queryByText(/지금 나는 인생의 어떤 계절에 있나요/),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "심을 때" }),
    ).not.toBeInTheDocument();
  });

  it("계절 하나만 골라도 기록할 수 있고, 다시 누르면 선택이 풀린다", async () => {
    // 계절 선택은 클래스로만 표시돼 접근성 상태가 없다 — 그래서 '기록 버튼이
    // 열렸는가' 라는 사용자에게 보이는 결과로 토글을 확인한다.
    const { user } = renderPage();
    await openReflection(user);

    const submit = screen.getByRole("button", { name: "성찰 기록하기" });
    expect(submit).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "심을 때" }));
    expect(submit).toBeEnabled();

    await user.click(screen.getByRole("button", { name: "심을 때" }));
    expect(submit).toBeDisabled();
  });

  it("결론 체크만 해도 기록할 수 있다", async () => {
    const { user } = renderPage();
    await openReflection(user);

    await user.click(
      screen.getByLabelText("헛됨만 보지 않고 이 결론까지 함께 읽었어요"),
    );
    expect(screen.getByRole("button", { name: "성찰 기록하기" })).toBeEnabled();
  });

  it("공백만 적은 성찰은 입력으로 치지 않는다", async () => {
    const { user } = renderPage();
    await openReflection(user);

    await user.type(
      screen.getByPlaceholderText(/해 아래에서 붙잡으려 애썼지만/),
      "   ",
    );
    expect(
      screen.getByRole("button", { name: "성찰 기록하기" }),
    ).toBeDisabled();
  });

  it("헛됨·의미·계절·결론 체크가 그대로 API 로 나가고, 성공하면 폼이 비워진다", async () => {
    const { user } = renderPage();
    await openReflection(user);

    await user.click(screen.getByRole("button", { name: "울 때" }));
    await user.type(
      screen.getByPlaceholderText(/해 아래에서 붙잡으려 애썼지만/),
      "10년 일한 결과가 남지 않았다",
    );
    await user.type(
      screen.getByPlaceholderText(
        "오늘 하나님이 맡기신 몫 한 가지를 적어보세요.",
      ),
      "오늘의 밥과 잠",
    );
    await user.click(
      screen.getByLabelText("헛됨만 보지 않고 이 결론까지 함께 읽었어요"),
    );
    await user.click(screen.getByRole("button", { name: "성찰 기록하기" }));

    await waitFor(() =>
      expect(mockRecord).toHaveBeenCalledWith({
        chapterRef: "전 2:11",
        userSeason: "weep",
        futilityNote: "10년 일한 결과가 남지 않았다",
        meaningNote: "오늘의 밥과 잠",
        conclusionViewed: true,
      }),
    );

    // R2 — 서버 safetyFooter(헛됨 → 경외) 를 그대로 보여준다.
    expect(await screen.findByText("✓ 기록되었습니다.")).toBeInTheDocument();
    expect(
      screen.getByText("헛됨을 인정한 자리에서 경외가 시작됩니다 (전 12:13)."),
    ).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/해 아래에서 붙잡으려 애썼지만/),
    ).toHaveValue("");
    expect(
      screen.getByLabelText("헛됨만 보지 않고 이 결론까지 함께 읽었어요"),
    ).not.toBeChecked();
  });

  it("R1 — crisis.routed 면 '헛됨은 포기하라는 말이 아니다' 안내와 24시간 번호를 띄운다", async () => {
    // 헛됨을 다루는 화면이라 위기 신호가 실제로 들어오는 자리다.
    mockRecord.mockResolvedValue(
      ecclesiastesResponse({ crisis: { routed: true, resources: [] } }),
    );
    const { user } = renderPage();
    await openReflection(user);

    await user.type(
      screen.getByPlaceholderText(/해 아래에서 붙잡으려 애썼지만/),
      "다 의미가 없다",
    );
    await user.click(screen.getByRole("button", { name: "성찰 기록하기" }));

    expect(
      await screen.findByText("잠시 멈추고, 함께 안전을 살펴봐요."),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/인생을 포기하라는 말이 아닙니다/),
    ).toBeInTheDocument();
    const crisisLine = screen
      .getAllByText(/109/)
      .find((el) => el.tagName === "P");
    expect(crisisLine).toHaveTextContent("24시간");
    expect(screen.queryByText("✓ 기록되었습니다.")).not.toBeInTheDocument();
  });

  it("저장이 실패하면 실패를 알리고 사용자가 쓴 성찰은 지우지 않는다", async () => {
    mockRecord.mockRejectedValue(new Error("500"));
    const { user } = renderPage();
    await openReflection(user);

    await user.type(
      screen.getByPlaceholderText(/해 아래에서 붙잡으려 애썼지만/),
      "지우면 안 되는 문장",
    );
    await user.click(screen.getByRole("button", { name: "성찰 기록하기" }));

    expect(await screen.findByText("저장에 실패했습니다.")).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/해 아래에서 붙잡으려 애썼지만/),
    ).toHaveValue("지우면 안 되는 문장");
  });

  it("저장 중에는 버튼이 '기록 중...' 으로 잠긴다", async () => {
    const d = deferred<EcclesiastesResponse>();
    mockRecord.mockReturnValue(d.promise);
    const { user } = renderPage();
    await openReflection(user);

    await user.click(
      screen.getByLabelText("헛됨만 보지 않고 이 결론까지 함께 읽었어요"),
    );
    await user.click(screen.getByRole("button", { name: "성찰 기록하기" }));

    expect(
      await screen.findByRole("button", { name: "기록 중..." }),
    ).toBeDisabled();

    await act(async () => {
      d.resolve(ecclesiastesResponse());
    });
    expect(
      await screen.findByRole("button", { name: "성찰 기록하기" }),
    ).toBeInTheDocument();
  });

  it("지난 성찰이 있으면 결론 조회 횟수와 헛됨·의미 노트를 보여준다", async () => {
    mockViews.mockResolvedValue({
      conclusionViewedCount: 2,
      conclusionInvite: "결론까지 함께 읽어요.",
      items: [
        {
          id: 31,
          chapterRef: "전 2:11",
          userSeason: "plant",
          futilityNote: "손에 남은 게 없다",
          meaningNote: "그래도 오늘의 몫",
          listenedAudio: null,
          conclusionViewed: true,
          createdAt: "2026-08-01T09:00:00Z",
        },
        {
          id: 32,
          chapterRef: null,
          userSeason: null,
          futilityNote: null,
          meaningNote: null,
          listenedAudio: null,
          conclusionViewed: false,
          createdAt: "2026-08-02T09:00:00Z",
        },
      ],
    });
    renderPage();

    expect(await screen.findByText("지금까지의 성찰")).toBeInTheDocument();
    expect(screen.getByText("결론(전 12:13)까지 봄 2회")).toBeInTheDocument();
    expect(screen.getByText("헛됨 · 손에 남은 게 없다")).toBeInTheDocument();
    expect(screen.getByText("의미 · 그래도 오늘의 몫")).toBeInTheDocument();
    // chapterRef 가 비면 "전도서" 로 대체된다 — 빈 칸을 남기지 않는다.
    expect(screen.getByText("전도서")).toBeInTheDocument();
    // 결론까지 본 기록에만 ✓ 가 붙는다.
    expect(screen.getAllByText(/· ✓ 결론/)).toHaveLength(1);
  });

  it("지난 성찰이 없으면 히스토리 섹션 자체를 만들지 않는다", async () => {
    renderPage();

    await waitFor(() => expect(mockViews).toHaveBeenCalledWith(20));
    expect(screen.queryByText("지금까지의 성찰")).not.toBeInTheDocument();
  });

  it("[버그 문서화] 카테고리를 바꿔도 앞 카테고리의 입력과 완료 문구가 그대로 남는다", async () => {
    // ReflectionSection 에 key 가 없어 카테고리를 바꿔도 같은 컴포넌트 인스턴스가
    // 재사용된다. 그 결과 '전 2:11' 에 적은 성찰과 '✓ 기록되었습니다.' 가 '전 3:1'
    // 화면에 그대로 얹힌다. 아래는 *현재 동작* 을 못박아 둔 것이고, 고쳐지면
    // 이 테스트가 깨져서 알려준다.
    const { user } = renderPage();
    await openReflection(user);

    await user.type(
      screen.getByPlaceholderText(/해 아래에서 붙잡으려 애썼지만/),
      "앞 카테고리에 적은 글",
    );
    await user.click(screen.getByRole("button", { name: /때와 기한/ }));

    expect(screen.getByText(/범사에 기한이 있고/)).toBeInTheDocument();
    expect(
      screen.getByPlaceholderText(/해 아래에서 붙잡으려 애썼지만/),
    ).toHaveValue("앞 카테고리에 적은 글");
  });

  it("헤더의 '← 주제' 는 주제 목록으로 돌아간다", async () => {
    renderPage();

    const back = screen.getByRole("link", { name: "← 주제" });
    expect(back).toHaveAttribute("href", "/topics");
    expect(
      within(back.closest("header") as HTMLElement).getByRole("heading", {
        name: "전도서와 인생",
        level: 1,
      }),
    ).toBeInTheDocument();
  });
});
