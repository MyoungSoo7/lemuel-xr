import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
// 번호를 여기 적지 않고 정본에서 가져온다. 픽스처는 백엔드 CrisisTokenResolver 가
// `{{crisis_resources.default}}` 를 치환해 내려보내는 값을 흉내내는 것이므로,
// 정본에서 파생시키는 쪽이 실제 동작에 더 가깝기도 하다.
// (`scripts/check_frontend_hotline.py` — "화면에도 테스트에도 숫자를 다시 적지 않는다")
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";
import {
  addBookmark,
  fetchBookmarks,
  fetchEcclesiastesCategories,
  fetchEcclesiastesViews,
  fetchJournalGuidance,
  fetchPractices,
  fetchProverbsByTheme,
  fetchProverbsThemes,
  fetchScripturePassage,
  fetchTopicCards,
  fetchTopics,
  recordEcclesiastesView,
  recordPractice,
  recordProverbsInteraction,
  removeBookmark,
  requestJournalGuidance,
} from "./content";

/**
 * 이 모듈은 로직이 거의 없는 "얇은" 레이어처럼 보이지만, 프론트가 백엔드와 맺은
 * 계약이 **전부 여기에만** 적혀 있다 — 경로 문자열, 쿼리 파라미터 이름, 기본값,
 * 그리고 응답 봉투(envelope)를 벗기는 방식. 화면 코드는 이 함수들의 반환 모양만
 * 믿는다. 그래서 여기서 재는 것은 "axios 를 불렀다" 가 아니라
 *   입력 → (백엔드로 나간 요청) 과 (백엔드 응답 → 호출자가 받는 값)
 * 두 관찰 가능한 경계다.
 *
 * client.ts 의 axios 인스턴스는 토큰 발급 인터셉터를 물고 있어 실제로 네트워크를
 * 타므로 통째로 막는다.
 */
vi.mock("@/lib/api/client", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}));

const get = vi.mocked(api.get);
const post = vi.mocked(api.post);
const del = vi.mocked(api.delete);

/** axios 응답 봉투 흉내 — 이 모듈이 보는 것은 `res.data` 뿐이다. */
function ok<T>(data: T) {
  return { data } as never;
}

/** 마지막 요청의 [url, config] 를 꺼낸다. */
function lastGet(): [string, { params?: Record<string, unknown> } | undefined] {
  const call = get.mock.calls.at(-1) as unknown as [
    string,
    { params?: Record<string, unknown> } | undefined,
  ];
  return call;
}

beforeEach(() => {
  // setup 의 clearAllMocks 는 호출기록만 지우고 구현은 남긴다. 테스트가 서로의
  // 응답을 물려받지 않도록 매번 명시적으로 되돌린다.
  get.mockReset();
  post.mockReset();
  del.mockReset();
});

describe("토픽 · 카드 · 성경 본문", () => {
  it("fetchTopics 는 봉투를 벗겨 topics 배열만 돌려준다", async () => {
    // 화면은 `res.data.topics` 를 모른다 — 배열만 받는다는 게 계약이다.
    const topics = [{ id: 1, key: "anxiety", title: "불안" }];
    get.mockResolvedValue(ok({ topics }));

    await expect(fetchTopics()).resolves.toBe(topics);
    expect(lastGet()[0]).toBe("/api/content/topics");
  });

  it("fetchTopics 는 목록이 비어도 빈 배열을 그대로 준다", async () => {
    // "없음" 을 null 이나 예외로 바꾸지 않는다 — 화면이 .map 을 그냥 돌릴 수 있어야 한다.
    get.mockResolvedValue(ok({ topics: [] }));
    await expect(fetchTopics()).resolves.toEqual([]);
  });

  it("fetchTopics 는 네트워크 실패를 삼키지 않고 그대로 던진다", async () => {
    // 조용히 []를 돌려주면 화면은 "토픽이 없음"으로 오인한다. 반드시 표면화돼야 한다.
    const boom = Object.assign(new Error("Network Error"), {
      code: "ERR_NETWORK",
    });
    get.mockRejectedValue(boom);

    await expect(fetchTopics()).rejects.toBe(boom);
  });

  it("fetchTopicCards 는 topicId 를 경로에 넣고 기본 limit 5 로 조회한다", async () => {
    const cards = [{ id: 10, topicId: 6, title: "카드" }];
    get.mockResolvedValue(ok({ topicId: 6, cards }));

    await expect(fetchTopicCards(6)).resolves.toBe(cards);

    const [url, config] = lastGet();
    expect(url).toBe("/api/content/topics/6/cards");
    expect(config?.params?.limit).toBe(5);
    // 감정 미지정이면 필터가 붙지 않아야 한다(axios 는 undefined 파라미터를 뺀다).
    expect(config?.params?.emotion).toBeUndefined();
  });

  it("fetchTopicCards 는 emotion 을 쿼리 파라미터로 전달한다", async () => {
    get.mockResolvedValue(ok({ topicId: 7, cards: [] }));

    await fetchTopicCards(7, "fear", 3);

    expect(lastGet()[1]?.params).toEqual({ emotion: "fear", limit: 3 });
  });

  it("fetchTopicCards 는 limit 0 을 기본값 5 로 덮어쓰지 않는다", async () => {
    // 기본값은 undefined 일 때만 적용된다. 0 을 5 로 바꿔버리면 "빈 목록 요청" 이
    // 조용히 5건 요청으로 둔갑한다.
    get.mockResolvedValue(ok({ topicId: 6, cards: [] }));

    await fetchTopicCards(6, undefined, 0);

    expect(lastGet()[1]?.params?.limit).toBe(0);
  });

  it("fetchTopicCards 는 limit 을 명시적으로 undefined 로 줘도 5 를 쓴다", async () => {
    get.mockResolvedValue(ok({ topicId: 6, cards: [] }));

    await fetchTopicCards(6, "anger", undefined);

    expect(lastGet()[1]?.params).toEqual({ emotion: "anger", limit: 5 });
  });

  it("fetchScripturePassage 는 본문 객체를 그대로(봉투 없이) 준다", async () => {
    const passage = { id: 1, reference: "Psalm 23:1", translation: "rev" };
    get.mockResolvedValue(ok(passage));

    await expect(fetchScripturePassage("Psalm 23:1")).resolves.toBe(passage);
    // 번역본 미지정 시 기본값 rev(개역개정) — 화면이 매번 넘기지 않아도 같은 본문이 온다.
    // 2026-08-21 에 modern(KLB) 에서 옮겼다. 모놀로그가 개역개정 자구라 한 화면에 같은
    // 절이 두 자구로 떴다. 이 단언이 그 기본값을 못박는다 — 백엔드에도 같은 기본값이
    // 있어서(`ScriptureController`) 한쪽만 바뀌면 조용히 어긋난다.
    expect(lastGet()[1]?.params).toEqual({ translation: "rev" });
  });

  it("fetchScripturePassage 는 번역본을 지정하면 그것을 쓴다", async () => {
    get.mockResolvedValue(ok({ id: 2 }));

    await fetchScripturePassage("Psalm 23:1", "kjv");

    expect(lastGet()[1]?.params).toEqual({ translation: "kjv" });
  });

  it("fetchScripturePassage 는 한글·공백·콜론이 든 참조를 왕복 가능하게 인코딩한다", async () => {
    // 인코딩 결과 문자열을 외우는 대신 "디코딩하면 원본" 이라는 성질을 잰다.
    // 날것으로 나가면 공백 때문에 요청 자체가 깨진다.
    const ref = "시편 23:1-6";
    get.mockResolvedValue(ok({ id: 3 }));

    await fetchScripturePassage(ref);

    const [url] = lastGet();
    const encoded = url.replace("/api/scripture/", "");
    expect(encoded).not.toContain(" ");
    expect(decodeURIComponent(encoded)).toBe(ref);
  });

  it("fetchScripturePassage 는 슬래시를 이스케이프해 경로를 벗어나지 못하게 한다", async () => {
    // 참조 문자열은 사용자 입력에서도 올 수 있다. `../` 가 살아 나가면 다른
    // 엔드포인트를 때린다.
    get.mockResolvedValue(ok({ id: 4 }));

    await fetchScripturePassage("../admin/secrets");

    const [url] = lastGet();
    expect(url.startsWith("/api/scripture/")).toBe(true);
    expect(url).not.toContain("/admin/");
    // "" / "api" / "scripture" / 인코딩된 한 조각 — 참조가 경로 세그먼트를 늘리지 못한다.
    expect(url.split("/")).toHaveLength(4);
  });
});

describe("북마크", () => {
  it("fetchBookmarks 는 배열 응답을 그대로 준다", async () => {
    const list = [
      {
        topicContentId: 11,
        topicId: 6,
        title: "담아둔 카드",
        scriptureRef: null,
        body: "본문",
        anchorCharacter: null,
        bookmarkedAt: "2026-08-13T00:00:00Z",
      },
    ];
    get.mockResolvedValue(ok(list));

    await expect(fetchBookmarks()).resolves.toBe(list);
    expect(lastGet()[0]).toBe("/api/content/bookmarks");
  });

  it("addBookmark 은 id 를 본문(body)으로 POST 한다 — 경로가 아니라", async () => {
    // 담기는 멱등이어야 하고, 서버는 body 의 topicContentId 를 본다.
    const dto = {
      id: "b-1",
      topicContentId: 11,
      createdAt: "2026-08-13T00:00:00Z",
    };
    post.mockResolvedValue(ok(dto));

    await expect(addBookmark(11)).resolves.toBe(dto);
    expect(post).toHaveBeenCalledWith("/api/content/bookmarks", {
      topicContentId: 11,
    });
  });

  it("removeBookmark 은 id 를 경로에 넣고 DELETE 하며 아무것도 돌려주지 않는다", async () => {
    // 응답 본문이 있더라도 호출자에게 흘리지 않는다(토글 UI 는 반환값을 안 본다).
    del.mockResolvedValue(ok({ deleted: true }));

    await expect(removeBookmark(11)).resolves.toBeUndefined();
    expect(del).toHaveBeenCalledWith("/api/content/bookmarks/11");
  });

  it("removeBookmark 은 삭제 실패를 그대로 던진다", async () => {
    // 실패를 삼키면 UI 가 "빠짐" 으로 낙관 갱신한 뒤 새로고침에서 되살아난다.
    const err = Object.assign(new Error("Request failed"), {
      response: { status: 404 },
    });
    del.mockRejectedValue(err);

    await expect(removeBookmark(99)).rejects.toBe(err);
  });
});

describe("실천/성찰 (Theme 6·7)", () => {
  it("recordPractice 는 요청 객체를 손대지 않고 전송하고 응답을 그대로 준다", async () => {
    // 여기서 필드를 골라 담으면 새 필드가 조용히 유실된다.
    const req = {
      topicId: 6 as const,
      practiceKind: "heart_checkin" as const,
      situation: "회의 직전",
      reflection: { mood: 3, note: "긴장" },
      actionTaken: false, // falsy 도 살아남아야 한다 — 미실천은 "무응답" 과 다르다
      scriptureRef: "잠 4:23",
      dimension: "emotional" as const,
    };
    const resp = {
      practice: { id: 1, topicId: 6 },
      crisis: { routed: false, resources: [] },
      safetyFooter: "안내",
      aiFooter: "AI 고지",
    };
    post.mockResolvedValue(ok(resp));

    await expect(recordPractice(req)).resolves.toBe(resp);

    const [url, body] = post.mock.calls[0] as unknown as [string, typeof req];
    expect(url).toBe("/api/content/practice");
    expect(body).toEqual(req);
    expect(body.actionTaken).toBe(false);
    expect(body.reflection).toEqual({ mood: 3, note: "긴장" });
  });

  it("recordPractice 는 위기 라우팅이 켜진 응답도 변형 없이 전달한다", async () => {
    // 위기 자원은 화면이 즉시 띄워야 하는 정보다. 여기서 깎이면 사람이 다친다.
    const resp = {
      practice: { id: 2, topicId: 7 },
      crisis: { routed: true, resources: [{ tel: CRISIS_DEFAULT.tel }] },
      safetyFooter: "지금 도움을 받을 수 있습니다",
      aiFooter: "AI 고지",
    };
    post.mockResolvedValue(ok(resp));

    const out = await recordPractice({
      topicId: 7,
      practiceKind: "courage_act",
    });

    expect(out.crisis.routed).toBe(true);
    expect(out.crisis.resources).toEqual([{ tel: CRISIS_DEFAULT.tel }]);
  });

  it("recordPractice 는 서버 4xx 를 그대로 던진다", async () => {
    const err = Object.assign(new Error("Bad Request"), {
      response: { status: 400, data: { message: "practiceKind invalid" } },
    });
    post.mockRejectedValue(err);

    await expect(
      recordPractice({ topicId: 6, practiceKind: "boundary_sentence" }),
    ).rejects.toBe(err);
  });

  it("fetchPractices 는 topicId 를 쿼리로 보내고 기본 limit 20 을 쓴다", async () => {
    // 카드 조회와 달리 topicId 가 경로가 아니라 쿼리다 — 이 차이가 계약이다.
    const resp = { topicId: 6, items: [], actionCount: 0 };
    get.mockResolvedValue(ok(resp));

    await expect(fetchPractices(6)).resolves.toBe(resp);

    const [url, config] = lastGet();
    expect(url).toBe("/api/content/practice");
    expect(config?.params).toEqual({ topicId: 6, limit: 20 });
  });

  it("fetchPractices 는 limit 을 넘기면 그 값을 쓴다", async () => {
    get.mockResolvedValue(ok({ topicId: 7, items: [], actionCount: 0 }));

    await fetchPractices(7, 1);

    expect(lastGet()[1]?.params).toEqual({ topicId: 7, limit: 1 });
  });
});

describe("전도서와 인생", () => {
  it("fetchEcclesiastesCategories 는 카테고리·계절·AI 고지를 한 번에 준다", async () => {
    const resp = {
      categories: [
        {
          key: "toil",
          title: "수고",
          chapterRef: "전 2",
          verse: "…",
          honestNote: "해 아래 헛됨",
          meaningNote: "창조주 경외",
        },
      ],
      seasons: [{ key: "mourn", label: "슬퍼할 때", verse: "전 3:4" }],
      aiFooter: "AI 고지",
    };
    get.mockResolvedValue(ok(resp));

    await expect(fetchEcclesiastesCategories()).resolves.toBe(resp);
    expect(lastGet()[0]).toBe("/api/content/ecclesiastes/categories");
    // 카테고리 목록은 쿼리 파라미터 없이 고정 조회다.
    expect(lastGet()[1]).toBeUndefined();
  });

  it("recordEcclesiastesView 는 빈 요청도 그대로 POST 한다", async () => {
    // 모든 필드가 optional 이다 — "아무것도 안 적고 열어보기만 함" 도 기록 대상.
    const resp = {
      view: { id: 1 },
      crisis: { routed: false, resources: [] },
      safetyFooter: "",
      conclusionInvite: "전 12:13 을 읽어보시겠어요?",
      aiFooter: "AI 고지",
    };
    post.mockResolvedValue(ok(resp));

    await expect(recordEcclesiastesView({})).resolves.toBe(resp);
    expect(post).toHaveBeenCalledWith("/api/content/ecclesiastes", {});
  });

  it("recordEcclesiastesView 는 boolean false 필드를 보존한다", async () => {
    // listenedAudio:false 가 빠지면 서버는 "미지정" 으로 읽는다 — 의미가 다르다.
    post.mockResolvedValue(ok({ view: { id: 2 } }));

    const req = {
      chapterRef: "전 3",
      userSeason: "mourn",
      futilityNote: "헛되다",
      meaningNote: "그래도",
      listenedAudio: false,
      conclusionViewed: false,
    };
    await recordEcclesiastesView(req);

    const [, body] = post.mock.calls[0] as unknown as [string, typeof req];
    expect(body.listenedAudio).toBe(false);
    expect(body.conclusionViewed).toBe(false);
    expect(body.userSeason).toBe("mourn");
  });

  it("fetchEcclesiastesViews 는 기본 limit 20 으로 목록을 가져온다", async () => {
    const resp = { items: [], conclusionViewedCount: 0, conclusionInvite: "" };
    get.mockResolvedValue(ok(resp));

    await expect(fetchEcclesiastesViews()).resolves.toBe(resp);
    expect(lastGet()[0]).toBe("/api/content/ecclesiastes");
    expect(lastGet()[1]?.params).toEqual({ limit: 20 });
  });

  it("fetchEcclesiastesViews 는 limit 0 을 기본값으로 덮지 않는다", async () => {
    get.mockResolvedValue(
      ok({ items: [], conclusionViewedCount: 0, conclusionInvite: "" }),
    );

    await fetchEcclesiastesViews(0);

    expect(lastGet()[1]?.params).toEqual({ limit: 0 });
  });
});

describe("일기 조언", () => {
  it("감정을 지정하면 그 감정만 쿼리로 보낸다", async () => {
    const resp = {
      guidance: {
        emotion: "anger",
        emotionLabel: "분노",
        validation: "",
        verses: [],
        reflectionQuestions: [],
      },
      catalog: [],
      crisis: { routed: false, resources: [] },
      safetyFooter: "",
      aiFooter: "",
    };
    get.mockResolvedValue(ok(resp));

    await expect(fetchJournalGuidance("anger")).resolves.toBe(resp);

    const [url, config] = lastGet();
    expect(url).toBe("/api/content/journal/guidance");
    expect(config?.params).toEqual({ emotion: "anger" });
  });

  it("감정 미지정이면 params 자체를 붙이지 않는다 — 전체 카탈로그 조회", async () => {
    // `?emotion=` 처럼 빈 파라미터가 나가면 백엔드가 "빈 감정" 으로 오인할 수 있다.
    get.mockResolvedValue(
      ok({ guidance: null, catalog: [{ emotion: "fear" }] }),
    );

    const out = await fetchJournalGuidance();

    expect(lastGet()[1]?.params).toBeUndefined();
    expect(out.guidance).toBeNull();
    expect(out.catalog).toHaveLength(1);
  });

  it("빈 문자열 감정은 '미지정' 과 똑같이 취급된다", async () => {
    // 폼에서 아무것도 고르지 않은 상태("")가 그대로 흘러들어와도 필터 없는
    // 카탈로그 조회가 된다. 다른 함수(fetchTopicCards)와 다른 처리라 명시적으로 못박는다.
    get.mockResolvedValue(ok({ guidance: null, catalog: [] }));

    await fetchJournalGuidance("");

    expect(lastGet()[1]?.params).toBeUndefined();
  });

  it("POST 조언은 일기 텍스트를 본문으로 보내고 위기 라우팅을 전달한다", async () => {
    // R1 위기 스캔 결과가 응답에 실린다 — 이 경로가 안전 기능의 입구다.
    const resp = {
      guidance: {
        emotion: "despair",
        emotionLabel: "절망",
        validation: "",
        verses: [],
        reflectionQuestions: [],
      },
      catalog: [],
      crisis: { routed: true, resources: [{ tel: CRISIS_DEFAULT.tel }] },
      safetyFooter: "도움을 받으세요",
      aiFooter: "AI 고지",
    };
    post.mockResolvedValue(ok(resp));

    const out = await requestJournalGuidance({ text: "오늘 너무 힘들다" });

    expect(post).toHaveBeenCalledWith("/api/content/journal/guidance", {
      text: "오늘 너무 힘들다",
    });
    expect(out.crisis.routed).toBe(true);
    expect(out.safetyFooter).toBe("도움을 받으세요");
  });

  it("POST 조언은 text 없이 emotion 만으로도 보낼 수 있다", async () => {
    post.mockResolvedValue(ok({ guidance: null, catalog: [] }));

    await requestJournalGuidance({ emotion: "fear" });

    expect(post).toHaveBeenCalledWith("/api/content/journal/guidance", {
      emotion: "fear",
    });
  });

  it("POST 조언 실패는 그대로 던진다", async () => {
    const err = Object.assign(new Error("Service Unavailable"), {
      response: { status: 503 },
    });
    post.mockRejectedValue(err);

    await expect(requestJournalGuidance({ text: "x" })).rejects.toBe(err);
  });
});

describe("잠언 주제", () => {
  it("fetchProverbsThemes 는 주제 목록을 봉투째 준다 (푸터 포함)", async () => {
    // safetyFooter/aiFooter 를 벗겨내면 화면이 고지 문구를 못 띄운다.
    const resp = {
      themes: [
        {
          key: "money",
          title: "돈",
          summary: "",
          guidance: "",
          verses: [{ ref: "잠 3:9", text: "…" }],
        },
      ],
      safetyFooter: "안내",
      aiFooter: "AI 고지",
    };
    get.mockResolvedValue(ok(resp));

    const out = await fetchProverbsThemes();

    expect(lastGet()[0]).toBe("/api/content/proverbs/themes");
    expect(out).toBe(resp);
    expect(out.safetyFooter).toBe("안내");
  });

  it("fetchProverbsByTheme 은 주제를 쿼리 파라미터로 보낸다", async () => {
    const resp = {
      theme: {
        key: "money",
        title: "돈",
        summary: "",
        guidance: "",
        verses: [],
      },
      safetyFooter: "",
      aiFooter: "",
    };
    get.mockResolvedValue(ok(resp));

    await expect(fetchProverbsByTheme("money")).resolves.toBe(resp);

    const [url, config] = lastGet();
    expect(url).toBe("/api/content/proverbs/by-theme");
    expect(config?.params).toEqual({ theme: "money" });
  });

  it("fetchProverbsByTheme 은 주제 문자열을 경로에 끼워넣지 않는다", async () => {
    // 쿼리로 보내므로 특수문자가 있어도 경로가 흔들리지 않는다.
    get.mockResolvedValue(ok({ theme: {}, safetyFooter: "", aiFooter: "" }));

    await fetchProverbsByTheme("돈/재물 & 정직");

    const [url, config] = lastGet();
    expect(url).toBe("/api/content/proverbs/by-theme");
    expect(config?.params).toEqual({ theme: "돈/재물 & 정직" });
  });

  it("fetchProverbsByTheme 은 알 수 없는 주제의 404 를 그대로 던진다", async () => {
    const err = Object.assign(new Error("Not Found"), {
      response: { status: 404 },
    });
    get.mockRejectedValue(err);

    await expect(fetchProverbsByTheme("nope")).rejects.toBe(err);
  });

  it("recordProverbsInteraction 은 선택 필드까지 그대로 POST 한다", async () => {
    const resp = {
      id: 5,
      theme: "money",
      chosenProverbRef: "잠 3:9",
      safetyFooter: "",
      aiFooter: "",
    };
    post.mockResolvedValue(ok(resp));

    const req = {
      theme: "money",
      situation: "빚 상환",
      chosenProverbRef: "잠 3:9",
      dimension: "rational",
    };
    await expect(recordProverbsInteraction(req)).resolves.toBe(resp);
    expect(post).toHaveBeenCalledWith(
      "/api/content/proverbs/interactions",
      req,
    );
  });

  it("recordProverbsInteraction 은 theme 만으로도 기록된다", async () => {
    // 구절을 아직 고르지 않은 단계의 상호작용도 남는다.
    post.mockResolvedValue(
      ok({ id: 6, theme: "anger", chosenProverbRef: null }),
    );

    const out = await recordProverbsInteraction({ theme: "anger" });

    expect(post).toHaveBeenCalledWith("/api/content/proverbs/interactions", {
      theme: "anger",
    });
    expect(out.chosenProverbRef).toBeNull();
  });
});

describe("응답 봉투가 깨졌을 때의 실제 동작", () => {
  /**
   * 아래 두 개는 "이상적인 동작" 이 아니라 **현재 동작을 못박는** 테스트다.
   * 봉투 안쪽 키가 없으면 이 레이어는 undefined 를 그대로 내보내고, 폭발은
   * 화면(.map 호출부)에서 일어난다. 방어 로직을 넣는 순간 여기가 빨개지므로
   * 그때 화면 쪽 기대치도 같이 손보라는 신호가 된다.
   */
  it("fetchTopics 는 topics 키가 없으면 빈 배열이 아니라 undefined 를 준다", async () => {
    get.mockResolvedValue(ok({}));

    await expect(fetchTopics()).resolves.toBeUndefined();
  });

  it("fetchTopicCards 는 cards 키가 없으면 undefined 를 준다", async () => {
    get.mockResolvedValue(ok({ topicId: 6 }));

    await expect(fetchTopicCards(6)).resolves.toBeUndefined();
  });

  it("data 자체가 null 이면 봉투를 벗기다 TypeError 로 터진다", async () => {
    // 204/빈 본문이 오면 조용한 undefined 가 아니라 예외다 — 호출부가 catch 로
    // 잡을 수 있는 형태라는 것을 기록해 둔다.
    get.mockResolvedValue(ok(null));

    await expect(fetchTopics()).rejects.toBeInstanceOf(TypeError);
  });
});
