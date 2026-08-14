import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactElement } from "react";

import { CRISIS_RESOURCES } from "@/lib/crisis-resources";
import TopicsPage from "./page";
import {
  addBookmark,
  fetchBookmarks,
  fetchScripturePassage,
  fetchTopicCards,
  fetchTopics,
  removeBookmark,
  type BookmarkedCard,
  type ScripturePassage,
  type Topic,
  type TopicCard,
} from "@/lib/api/content";

// 이 페이지의 모든 데이터는 api 모듈 한 곳을 통과한다. axios/HTTP 를 흉내내는 대신
// 그 경계를 모킹해야 "화면이 무엇을 보여주는가" 만 남고 전송 계층이 시야에서 빠진다.
vi.mock("@/lib/api/content", () => ({
  fetchTopics: vi.fn(),
  fetchTopicCards: vi.fn(),
  fetchBookmarks: vi.fn(),
  addBookmark: vi.fn(),
  removeBookmark: vi.fn(),
  // PassageModal 이 같은 모듈에서 끌어다 쓴다 — 모달까지 한 번에 덮인다.
  fetchScripturePassage: vi.fn(),
}));

const mockFetchTopics = vi.mocked(fetchTopics);
const mockFetchTopicCards = vi.mocked(fetchTopicCards);
const mockFetchBookmarks = vi.mocked(fetchBookmarks);
const mockAddBookmark = vi.mocked(addBookmark);
const mockRemoveBookmark = vi.mocked(removeBookmark);
const mockFetchScripturePassage = vi.mocked(fetchScripturePassage);

const TOPICS: Topic[] = [
  { id: 1, key: "anxiety", title: "불안과 염려" },
  { id: 6, key: "heart", title: "마음 지킴" },
];

function card(over: Partial<TopicCard> = {}): TopicCard {
  return {
    id: 101,
    topicId: 1,
    title: "염려를 맡기는 연습",
    scriptureRef: "벧전 5:7",
    body: "너희 염려를 다 주께 맡기라.",
    anchorCharacter: "david",
    targetEmotion: "ANXIOUS",
    difficulty: 2,
    publishedAt: "2026-08-01T00:00:00Z",
    ...over,
  };
}

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
  verseEnd: null,
  text: "여러분의 염려를 다 하나님께 맡기십시오.",
  themeTags: ["불안"],
  characterTags: null,
};

function renderPage(ui: ReactElement = <TopicsPage />) {
  // retry: false — 에러 경로를 재려면 react-query 의 기본 3회 재시도를 꺼야 한다.
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  mockFetchTopics.mockResolvedValue(TOPICS);
  mockFetchBookmarks.mockResolvedValue([]);
  mockFetchTopicCards.mockResolvedValue([card()]);
  mockFetchScripturePassage.mockResolvedValue(PASSAGE);
  mockAddBookmark.mockResolvedValue({
    id: "bm-1",
    topicContentId: 101,
    createdAt: "2026-08-13T00:00:00Z",
  });
  mockRemoveBookmark.mockResolvedValue(undefined);
});

describe("/topics — 주제 인덱스", () => {
  it("주제를 불러오는 동안 로드 중임을 알린다", () => {
    // 사용자가 빈 화면을 고장으로 오해하지 않게 하는 유일한 신호라서 잰다.
    mockFetchTopics.mockReturnValue(new Promise<Topic[]>(() => {}));
    renderPage();

    expect(screen.getByText("로드 중...")).toBeInTheDocument();
  });

  it("주제 목록을 번호와 제목으로 렌더한다", async () => {
    renderPage();

    expect(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /마음 지킴/ }),
    ).toBeInTheDocument();
    // "#{t.id}" — 사용자가 주제 번호(AR 1~7)로 서로를 구분한다.
    expect(
      screen.getByRole("button", { name: /불안과 염려/ }),
    ).toHaveTextContent("#1");
    expect(screen.getByRole("button", { name: /마음 지킴/ })).toHaveTextContent(
      "#6",
    );
    expect(screen.queryByText("로드 중...")).not.toBeInTheDocument();
  });

  it("주제를 고르기 전에는 카드 대신 안내 문구를 보여준다", async () => {
    renderPage();

    expect(await screen.findByText("← 주제를 선택하세요")).toBeInTheDocument();
    // 아직 아무 주제도 안 골랐으므로 카드 요청 자체가 나가면 안 된다.
    expect(mockFetchTopicCards).not.toHaveBeenCalled();
  });

  it("주제를 누르면 그 주제의 카드를 최대 10장 요청해 보여준다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );

    expect(
      await screen.findByRole("heading", { name: "염려를 맡기는 연습" }),
    ).toBeInTheDocument();
    expect(screen.getByText("너희 염려를 다 주께 맡기라.")).toBeInTheDocument();
    // 인물/감정 코드가 한글 라벨로 번역돼야 한다 — 코드가 그대로 노출되면 안 된다.
    expect(screen.getByText("인물: 다윗")).toBeInTheDocument();
    expect(screen.getByText("감정: 불안")).toBeInTheDocument();
    expect(screen.getByText("난이도 ●●")).toBeInTheDocument();
    expect(mockFetchTopicCards).toHaveBeenCalledWith(1, undefined, 10);
  });

  it("카드를 불러오는 동안 카드 영역에 로딩을 표시한다", async () => {
    const user = userEvent.setup();
    mockFetchTopicCards.mockReturnValue(new Promise<TopicCard[]>(() => {}));
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );

    expect(await screen.findByText("카드 로드 중...")).toBeInTheDocument();
  });

  it("인물·감정·난이도가 없는 카드는 그 배지를 아예 그리지 않는다", async () => {
    // null 을 "null" 로 흘리거나 "난이도 " 빈 껍데기를 남기는 회귀를 잡는다.
    const user = userEvent.setup();
    mockFetchTopicCards.mockResolvedValue([
      card({
        anchorCharacter: null,
        targetEmotion: null,
        difficulty: null,
        scriptureRef: null,
      }),
    ]);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );

    expect(
      await screen.findByRole("heading", { name: "염려를 맡기는 연습" }),
    ).toBeInTheDocument();
    expect(screen.queryByText(/^인물:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^감정:/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^난이도/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /📖/ }),
    ).not.toBeInTheDocument();
  });

  it("알 수 없는 인물·감정 코드는 라벨을 만들지 못하고 배지를 숨긴다", async () => {
    const user = userEvent.setup();
    mockFetchTopicCards.mockResolvedValue([
      card({
        anchorCharacter: "unknown_person",
        targetEmotion: "UNKNOWN_MOOD",
      }),
    ]);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );

    await screen.findByRole("heading", { name: "염려를 맡기는 연습" });
    // 매핑에 없으면 undefined → falsy → 배지 없음. 코드 원문이 새어나오지 않는지 확인.
    expect(screen.queryByText(/unknown_person/)).not.toBeInTheDocument();
    expect(screen.queryByText(/UNKNOWN_MOOD/)).not.toBeInTheDocument();
  });

  it("카드가 없는 주제에는 비어 있음을 알린다", async () => {
    const user = userEvent.setup();
    mockFetchTopicCards.mockResolvedValue([]);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );

    expect(
      await screen.findByText("이 주제의 카드가 아직 없습니다."),
    ).toBeInTheDocument();
  });

  it("[버그] 카드 요청이 실패해도 '카드가 없다'고만 말한다", async () => {
    // 소스 현재 동작을 고정한다. 사용자는 서버 장애를 '콘텐츠 미제작'으로 오해하고
    // 재시도할 방법도 얻지 못한다. TopicCards 에 isError 분기가 없는 게 원인.
    const user = userEvent.setup();
    mockFetchTopicCards.mockRejectedValue(new Error("boom"));
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );

    expect(
      await screen.findByText("이 주제의 카드가 아직 없습니다."),
    ).toBeInTheDocument();
  });

  it("[버그] 주제 요청이 실패하면 아무 설명 없이 빈 목록이 된다", async () => {
    mockFetchTopics.mockRejectedValue(new Error("network down"));
    renderPage();

    await waitFor(() =>
      expect(screen.queryByText("로드 중...")).not.toBeInTheDocument(),
    );
    // 에러 문구도, 재시도 버튼도 없다 — 페이지가 정상인 척한다.
    expect(
      screen.queryByRole("button", { name: /불안과 염려/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("← 주제를 선택하세요")).toBeInTheDocument();
  });

  it("주제를 바꾸면 새 주제의 카드로 갈아끼운다", async () => {
    const user = userEvent.setup();
    mockFetchTopicCards.mockImplementation(async (topicId: number) =>
      topicId === 1
        ? [card()]
        : [
            card({
              id: 601,
              topicId: 6,
              title: "마음 지킴 카드",
              scriptureRef: null,
            }),
          ],
    );
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );
    await screen.findByRole("heading", { name: "염려를 맡기는 연습" });

    await user.click(screen.getByRole("button", { name: /마음 지킴/ }));

    expect(
      await screen.findByRole("heading", { name: "마음 지킴 카드" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "염려를 맡기는 연습" }),
    ).not.toBeInTheDocument();
  });
});

describe("/topics — 북마크 하트", () => {
  it("담기지 않은 카드는 ♡ 이고, 누르면 담긴 뒤 ♥ 로 바뀐다", async () => {
    const user = userEvent.setup();
    // 담기 성공 후 invalidate → 재조회. 두 번째 응답에 그 카드가 들어온다.
    mockFetchBookmarks
      .mockResolvedValueOnce([])
      .mockResolvedValue([bookmark({ topicContentId: 101 })]);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );
    const heart = await screen.findByRole("button", { name: "북마크 담기" });
    expect(heart).toHaveAttribute("aria-pressed", "false");
    expect(heart).toHaveTextContent("♡");

    await user.click(heart);

    expect(mockAddBookmark).toHaveBeenCalledWith(101);
    const filled = await screen.findByRole("button", { name: "북마크 빼기" });
    expect(filled).toHaveAttribute("aria-pressed", "true");
    expect(filled).toHaveTextContent("♥");
  });

  it("이미 담긴 카드의 ♥ 를 누르면 담기가 아니라 빼기를 호출한다", async () => {
    // 토글 방향이 뒤집히면 사용자는 뺄 방법을 잃는다 — 방향 자체를 잰다.
    const user = userEvent.setup();
    mockFetchBookmarks
      .mockResolvedValueOnce([bookmark({ topicContentId: 101 })])
      .mockResolvedValue([]);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );
    await user.click(
      await screen.findByRole("button", { name: "북마크 빼기" }),
    );

    expect(mockRemoveBookmark).toHaveBeenCalledWith(101);
    expect(mockAddBookmark).not.toHaveBeenCalled();
    expect(
      await screen.findByRole("button", { name: "북마크 담기" }),
    ).toBeInTheDocument();
  });

  it("담는 중에는 하트를 다시 누를 수 없다 (중복 요청 방지)", async () => {
    const user = userEvent.setup();
    let release: (() => void) | undefined;
    mockAddBookmark.mockReturnValue(
      new Promise((resolve) => {
        release = () =>
          resolve({
            id: "bm-1",
            topicContentId: 101,
            createdAt: "2026-08-13T00:00:00Z",
          });
      }),
    );
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );
    const heart = await screen.findByRole("button", { name: "북마크 담기" });
    await user.click(heart);

    await waitFor(() => expect(heart).toBeDisabled());
    release?.();
  });

  it("북마크 링크에 담아둔 개수를 함께 보여준다", async () => {
    mockFetchBookmarks.mockResolvedValue([
      bookmark({ topicContentId: 101 }),
      bookmark({ topicContentId: 102 }),
    ]);
    renderPage();

    expect(
      await screen.findByRole("link", { name: /담아둔 카드 다시 보기 \(2\)/ }),
    ).toBeInTheDocument();
  });

  it("담아둔 게 없으면 개수 괄호를 붙이지 않는다", async () => {
    renderPage();

    const link = await screen.findByRole("link", {
      name: /담아둔 카드 다시 보기/,
    });
    expect(link).not.toHaveTextContent(/\(\d+\)/);
  });
});

describe("/topics — 본문 모달", () => {
  it("scriptureRef 를 누르면 본문 모달이 열리고 닫을 수 있다", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: /불안과 염려/ }),
    );
    await user.click(
      await screen.findByRole("button", { name: "📖 벧전 5:7" }),
    );

    // 모달 안에서만 본문을 찾는다 — 카드 본문과 헷갈리지 않게.
    const heading = await screen.findByRole("heading", { name: "벧전 5:7" });
    expect(mockFetchScripturePassage).toHaveBeenCalledWith("벧전 5:7");
    expect(await screen.findByText(PASSAGE.text)).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "닫기" }));

    await waitFor(() =>
      expect(
        screen.queryByRole("heading", { name: "벧전 5:7" }),
      ).not.toBeInTheDocument(),
    );
    expect(heading).not.toBeInTheDocument();
  });
});

describe("/topics — 항상 보이는 안전·이동 경로", () => {
  it("위기 상담 번호를 정본 모듈에서 가져와 각주에 노출한다", async () => {
    // 번호를 화면에 직접 적는 회귀(= 개정 시 낡은 번호 잔존)를 막는다.
    renderPage();

    const note = await screen.findByText(/위기 시/);
    expect(note).toHaveTextContent(CRISIS_RESOURCES[0].tel);
    expect(note).toHaveTextContent(CRISIS_RESOURCES[0].shortLabel);
  });

  it("본문 인용 출처(fair use) 고지를 남긴다", async () => {
    renderPage();

    expect(await screen.findByText(/현대인의 성경/)).toBeInTheDocument();
  });

  it("자매 페이지로 가는 링크들이 올바른 경로를 가리킨다", async () => {
    renderPage();

    const links = Object.fromEntries(
      (await screen.findAllByRole("link")).map((a) => [
        a.getAttribute("href"),
        a,
      ]),
    );
    expect(links["/topics/bookmarks"]).toBeDefined();
    expect(links["/topics/practice"]).toBeDefined();
    expect(links["/topics/ecclesiastes"]).toBeDefined();
    expect(links["/topics/journal"]).toBeDefined();
    expect(links["/topics/proverbs"]).toBeDefined();
    expect(within(links["/"]).getByText("← 홈")).toBeInTheDocument();
  });
});
