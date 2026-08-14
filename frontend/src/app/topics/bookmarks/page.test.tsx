import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";

import BookmarksPage from "./page";
import {
  fetchBookmarks,
  fetchScripturePassage,
  removeBookmark,
  type BookmarkedCard,
  type ScripturePassage,
} from "@/lib/api/content";

// 목록·삭제·본문 모달이 전부 이 모듈 하나를 통과한다.
vi.mock("@/lib/api/content", () => ({
  fetchBookmarks: vi.fn(),
  removeBookmark: vi.fn(),
  fetchScripturePassage: vi.fn(),
}));

const mockFetchBookmarks = vi.mocked(fetchBookmarks);
const mockRemoveBookmark = vi.mocked(removeBookmark);
const mockFetchScripturePassage = vi.mocked(fetchScripturePassage);

function bookmark(over: Partial<BookmarkedCard> = {}): BookmarkedCard {
  return {
    topicContentId: 101,
    topicId: 1,
    title: "염려를 맡기는 연습",
    scriptureRef: "벧전 5:7",
    body: "너희 염려를 다 주께 맡기라.",
    anchorCharacter: "david",
    bookmarkedAt: "2026-08-10T00:00:00Z",
    ...over,
  };
}

const PASSAGE: ScripturePassage = {
  id: 7,
  reference: "벧전 5:7",
  translation: "modern",
  book: "베드로전서",
  bookCode: "1PE",
  chapter: 5,
  verseStart: 7,
  verseEnd: 8,
  text: "여러분의 염려를 다 하나님께 맡기십시오.",
  themeTags: null,
  characterTags: ["david"],
};

function renderPage(ui: ReactElement = <BookmarksPage />) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  mockFetchBookmarks.mockResolvedValue([bookmark()]);
  mockRemoveBookmark.mockResolvedValue(undefined);
  mockFetchScripturePassage.mockResolvedValue(PASSAGE);
});

describe("/topics/bookmarks — 목록", () => {
  it("불러오는 동안 로드 중임을 알린다", () => {
    mockFetchBookmarks.mockReturnValue(new Promise<BookmarkedCard[]>(() => {}));
    renderPage();

    expect(screen.getByText("로드 중...")).toBeInTheDocument();
    // 로딩 중에 '비어 있음' 을 같이 띄우면 사용자가 없는 걸로 오해한다.
    expect(
      screen.queryByText("아직 담아둔 카드가 없습니다."),
    ).not.toBeInTheDocument();
  });

  it("담아둔 카드의 제목·본문·주제·인물을 보여준다", async () => {
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "염려를 맡기는 연습" }),
    ).toBeInTheDocument();
    expect(screen.getByText("너희 염려를 다 주께 맡기라.")).toBeInTheDocument();
    expect(screen.getByText("주제 #1")).toBeInTheDocument();
    // 인물 코드가 한글 라벨로 번역돼야 한다.
    expect(screen.getByText("인물: 다윗")).toBeInTheDocument();
  });

  it("여러 장을 백엔드가 준 순서(최신순) 그대로 나열한다", async () => {
    mockFetchBookmarks.mockResolvedValue([
      bookmark({ topicContentId: 102, title: "나중에 담은 카드" }),
      bookmark({ topicContentId: 101, title: "먼저 담은 카드" }),
    ]);
    renderPage();

    const headings = await screen.findAllByRole("heading", { level: 3 });
    expect(headings.map((h) => h.textContent)).toEqual([
      "나중에 담은 카드",
      "먼저 담은 카드",
    ]);
  });

  it("주제·인물 정보가 없는 카드는 그 배지를 그리지 않는다", async () => {
    // topicId 는 0 이 될 수 있어 `!= null` 로 걸러야 한다 — null 만 숨겨지는지 확인.
    mockFetchBookmarks.mockResolvedValue([
      bookmark({ topicId: null, anchorCharacter: null, scriptureRef: null }),
    ]);
    renderPage();

    await screen.findByRole("heading", { name: "염려를 맡기는 연습" });
    expect(screen.queryByText(/^주제 #/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^인물:/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /📖/ }),
    ).not.toBeInTheDocument();
  });

  it("주제 번호 0 도 숨기지 않고 표시한다", async () => {
    mockFetchBookmarks.mockResolvedValue([bookmark({ topicId: 0 })]);
    renderPage();

    expect(await screen.findByText("주제 #0")).toBeInTheDocument();
  });

  it("알 수 없는 인물 코드는 배지 대신 아무것도 내보내지 않는다", async () => {
    mockFetchBookmarks.mockResolvedValue([
      bookmark({ anchorCharacter: "nobody" }),
    ]);
    renderPage();

    await screen.findByRole("heading", { name: "염려를 맡기는 연습" });
    expect(screen.queryByText(/nobody/)).not.toBeInTheDocument();
  });

  it("담아둔 게 없으면 비어 있음을 알리고 주제로 돌아갈 길을 준다", async () => {
    mockFetchBookmarks.mockResolvedValue([]);
    renderPage();

    expect(
      await screen.findByText("아직 담아둔 카드가 없습니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /카드를 ♡ 로 담아보세요/ }),
    ).toHaveAttribute("href", "/topics");
  });

  it("[버그] 목록 조회가 실패해도 '담아둔 카드가 없다'고 말한다", async () => {
    // 소스 현재 동작 고정. isError 분기가 없어서 서버 장애와 '진짜 0건' 이 구별되지
    // 않는다. 사용자는 담아둔 카드가 사라졌다고 오해하게 된다.
    mockFetchBookmarks.mockRejectedValue(new Error("network down"));
    renderPage();

    expect(
      await screen.findByText("아직 담아둔 카드가 없습니다."),
    ).toBeInTheDocument();
  });
});

describe("/topics/bookmarks — 빼기", () => {
  it("♥ 를 누르면 그 카드를 빼고 목록에서 사라진다", async () => {
    const user = userEvent.setup();
    mockFetchBookmarks
      .mockResolvedValueOnce([bookmark()])
      .mockResolvedValue([]);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "북마크 빼기" }),
    );

    expect(mockRemoveBookmark).toHaveBeenCalledWith(101);
    // 성공 후 invalidate → 재조회. 화면이 실제로 갱신되는지까지 본다.
    expect(
      await screen.findByText("아직 담아둔 카드가 없습니다."),
    ).toBeInTheDocument();
  });

  it("여러 장 중 누른 카드만 빼기 대상이 된다", async () => {
    const user = userEvent.setup();
    mockFetchBookmarks.mockResolvedValue([
      bookmark({ topicContentId: 101, title: "첫 카드" }),
      bookmark({ topicContentId: 202, title: "둘째 카드" }),
    ]);
    renderPage();

    const buttons = await screen.findAllByRole("button", {
      name: "북마크 빼기",
    });
    await user.click(buttons[1]);

    expect(mockRemoveBookmark).toHaveBeenCalledTimes(1);
    expect(mockRemoveBookmark).toHaveBeenCalledWith(202);
  });

  it("빼는 중에는 버튼이 잠겨 중복 요청이 나가지 않는다", async () => {
    const user = userEvent.setup();
    let release: (() => void) | undefined;
    mockRemoveBookmark.mockReturnValue(
      new Promise<void>((resolve) => {
        release = () => resolve();
      }),
    );
    renderPage();

    const heart = await screen.findByRole("button", { name: "북마크 빼기" });
    await user.click(heart);

    await waitFor(() => expect(heart).toBeDisabled());
    release?.();
  });

  it("이 목록의 하트는 항상 '담긴 상태'로 표기된다", async () => {
    // 북마크 목록에만 있는 카드라 aria-pressed 는 true 로 고정이다.
    renderPage();

    const heart = await screen.findByRole("button", { name: "북마크 빼기" });
    expect(heart).toHaveAttribute("aria-pressed", "true");
    expect(heart).toHaveTextContent("♥");
  });

  it("[버그] 빼기가 실패하면 아무 말 없이 카드가 그대로 남는다", async () => {
    // 소스 현재 동작 고정. onError 가 없어 사용자는 뺐다고 믿지만 실제로는 남아 있고,
    // 실패 사실을 알 방법도 없다.
    const user = userEvent.setup();
    mockRemoveBookmark.mockRejectedValue(new Error("500"));
    renderPage();

    const heart = await screen.findByRole("button", { name: "북마크 빼기" });
    await user.click(heart);

    await waitFor(() => expect(heart).not.toBeDisabled());
    expect(
      screen.getByRole("heading", { name: "염려를 맡기는 연습" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/실패|오류|불러오지/)).not.toBeInTheDocument();
  });
});

describe("/topics/bookmarks — 본문 모달", () => {
  it("📖 를 누르면 그 구절의 본문을 열고 닫을 수 있다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "📖 벧전 5:7" }),
    );

    expect(mockFetchScripturePassage).toHaveBeenCalledWith("벧전 5:7");
    expect(await screen.findByText(PASSAGE.text)).toBeInTheDocument();
    expect(
      screen.getByRole("heading", { name: "벧전 5:7" }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "닫기" }));

    await waitFor(() =>
      expect(screen.queryByText(PASSAGE.text)).not.toBeInTheDocument(),
    );
  });

  it("모달을 열기 전에는 본문을 요청하지 않는다", async () => {
    renderPage();

    await screen.findByRole("heading", { name: "염려를 맡기는 연습" });
    expect(mockFetchScripturePassage).not.toHaveBeenCalled();
  });
});

describe("/topics/bookmarks — 이동 경로", () => {
  it("주제 인덱스로 돌아가는 링크를 제공한다", async () => {
    renderPage();

    expect(await screen.findByRole("link", { name: /← 주제/ })).toHaveAttribute(
      "href",
      "/topics",
    );
  });
});
