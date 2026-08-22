import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

/*
  미션 화면 전부가 `scenePayload.scriptureRef` 를 **실제로 연다** 는 것을 재는 검사.
  (아래 `PAGES` 가 그 전부이고, `scripts/mission_passage_check.py` 의 runtime-test 가
   인물이 새로 열릴 때마다 이 목록에 그 인물이 들어왔는지 대조한다. 여기 개수를
   숫자로 적어 두면 인물이 늘 때마다 그 숫자만 조용히 틀려진다.)

  ─────────────── 왜 새로 필요했나 ───────────────

  이미 초록이던 검사가 둘 있었다.
    · `scripture-ref:all`  — 참조 60개가 `scripture_passages` 행과 맞느냐
    · `scripture-text:all` — 그 92행의 자구가 `translation` 라벨과 맞느냐
  둘 다 **열 수 있다** 까지만 잰다. 그래서 2026-08-20 까지, 참조 60개와 92행의 축자
  정렬이 **화면에 한 번도 도달하지 않은 채** 전부 PASS 였다. 사용자가 미션을 처음부터
  끝까지 걸어도 성경 본문은 한 줄도 뜨지 않았고, yml 의 `static_text`(저자 요약 산문)만
  읽혔다. 구조 검사(`scripts/mission_passage_check.py`)는 배선이 있느냐를 보지만,
  배선이 있어도 렌더가 안 되는 경우는 여기서만 잡힌다.

  ─────────────── 재는 것 ───────────────

  1) 화면 전부 — payload 에 `scriptureRef` 가 있으면 **API 응답 본문이 화면에 뜬다**.
  2) 참조가 없는 씬에서는 빈 껍데기를 그리지 않는다(본문 없는 씬과 구별돼야 한다).
  3) 본문 텍스트는 프론트 사본이 아니라 `fetchScripturePassage` 응답에서만 온다 —
     그래서 픽스처 문자열은 이 파일이 지어낸 표식이고, 화면에 그게 떠야 통과다.
     (성경 자구를 기대값에 적지 않는다. 그건 `scripture_text_check.py` 의 일이다.)
*/

vi.mock("@/lib/api/game", () => ({
  startMission: vi.fn(),
  decideMission: vi.fn(),
  completeMission: vi.fn(),
  startJoseph: vi.fn(),
  decideJoseph: vi.fn(),
  completeJoseph: vi.fn(),
}));

vi.mock("@/lib/api/content", () => ({
  fetchScripturePassage: vi.fn(),
}));

import { startMission, startJoseph } from "@/lib/api/game";
import { fetchScripturePassage } from "@/lib/api/content";

import DanielPage from "./daniel/page";
import DavidPage from "./david/page";
import ElijahPage from "./elijah/page";
import JesusPage from "./jesus/page";
import JobPage from "./job/page";
import JosephPage from "./joseph/page";
import MosesPage from "./moses/page";
import PeterPage from "./peter/page";
import RuthPage from "./ruth/page";
import SolomonPage from "./solomon/page";

/** 실제 참조 형식(`scripture_ref` 정본과 같은 모양) 하나면 족하다. */
const REF = "1kgs-19:4";

/**
 * 본문 자리에 뜨는지만 보는 표식. 성경 자구를 적지 않는다 — 이 검사가 재는 것은
 * "응답이 화면에 도달하느냐" 이지 "그 자구가 맞느냐" 가 아니다(그건 별도 게이트).
 */
const MARKER = "여기에-API-본문이-온다";

const PASSAGE = {
  id: 1,
  reference: REF,
  translation: "rev",
  book: "열왕기상",
  bookCode: "1kgs",
  chapter: 19,
  verseStart: 4,
  verseEnd: 4,
  text: MARKER,
  themeTags: null,
  characterTags: null,
};

/**
 * 화면들이 공유하는 최소 payload. 씬 타입은 어느 화면에서도 상호작용을 요구하지 않는
 * `cinematic` 으로 두고, 경고(trigger_warning)는 달지 않는다 — 동의 게이트가 뜨면
 * 본문이 가려지는 것이 **정상 동작** 이라 그 상태에서 재면 아무것도 못 잰다.
 */
function scene(scenePayload: Record<string, unknown>) {
  return {
    sessionId: "sess-passage",
    userId: "guest-1",
    currentScene: 1,
    scenePayload,
    responseText: null,
  };
}

const WITH_REF = scene({
  title: "본문이 있는 씬",
  type: "cinematic",
  scriptureRef: REF,
  extras: { static_text: "저자가 쓴 틀 문장." },
});

const WITHOUT_REF = scene({
  title: "본문이 없는 씬",
  type: "cinematic",
  extras: { static_text: "저자가 쓴 틀 문장." },
});

const PAGES: Array<[string, () => React.ReactElement]> = [
  ["daniel", () => <DanielPage />],
  ["david", () => <DavidPage />],
  ["elijah", () => <ElijahPage />],
  ["jesus", () => <JesusPage />],
  ["job", () => <JobPage />],
  ["joseph", () => <JosephPage />],
  ["moses", () => <MosesPage />],
  ["peter", () => <PeterPage />],
  ["ruth", () => <RuthPage />],
  ["solomon", () => <SolomonPage />],
];

function renderPage(el: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(<QueryClientProvider client={qc}>{el}</QueryClientProvider>);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(fetchScripturePassage).mockResolvedValue(PASSAGE);
});

describe("미션 화면은 scriptureRef 를 실제로 연다", () => {
  it.each(PAGES)("%s — 본문이 화면에 뜬다", async (_name, make) => {
    vi.mocked(startMission).mockResolvedValue(WITH_REF);
    vi.mocked(startJoseph).mockResolvedValue(WITH_REF);

    renderPage(make());

    expect(await screen.findByText(MARKER)).toBeInTheDocument();
    // 조회 키는 payload 가 준 참조 그대로여야 한다. 화면이 자기 참조를 지어내면
    // 시드와 어긋나도 이 검사가 초록이 되는 구멍이 생긴다.
    expect(fetchScripturePassage).toHaveBeenCalledWith(REF);
  });

  it.each(PAGES)(
    "%s — 참조가 없는 씬에서는 본문 블록 자체를 그리지 않는다",
    async (_name, make) => {
      vi.mocked(startMission).mockResolvedValue(WITHOUT_REF);
      vi.mocked(startJoseph).mockResolvedValue(WITHOUT_REF);

      renderPage(make());

      // 씬 제목으로 「화면이 떴다」를 확인한다. `static_text` 를 쓰면 안 된다 —
      // 그 필드를 실제로 렌더하는 화면은 elijah·job 둘뿐이고(나머지는 각자의
      // 콘텐츠 모듈·자막을 쓴다), 그 나머지에서는 「본문 블록이 없다」가 아니라
      // 「화면이 아직 안 떴다」를 재게 된다.
      expect(await screen.findByText("본문이 없는 씬")).toBeInTheDocument();
      expect(screen.queryByTestId("scene-passage")).toBeNull();
      // "한 번도 안 불렸다" 로는 못 잰다 — joseph/david/moses/jesus 는 씬 본문과 별개로
      // *모놀로그 인용* 을 같은 API 에서 받는다(`useMonologueTexts`). 여기서 재려는 것은
      // "씬이 참조를 안 줬는데 화면이 참조를 지어내지 않았다" 이므로, 그 참조로 불리지
      // 않았는지만 본다.
      expect(fetchScripturePassage).not.toHaveBeenCalledWith(REF);
    },
  );

  it.each(PAGES)(
    "%s — additional_refs 의 나머지 절도 함께 뜬다",
    async (_name, make) => {
      // 참조 키가 절 단위라, 편 전체를 쓰는 씬은 `scripture_ref` 하나 + 나머지를
      // `additional_refs` 로 편다(david 6+6 · ruth 5+10 · job 5+2). 이 배선이 빠지면
      // 화면에 1절만 뜨는데, `scripture_ref_check.py` 는 그 상태에서도 초록이다 —
      // 그쪽은 시드에 행이 있는지만 보기 때문이다.
      vi.mocked(fetchScripturePassage).mockImplementation(
        async (ref: string) => ({
          ...PASSAGE,
          reference: ref,
          text: `${MARKER}:${ref}`,
        }),
      );
      const WITH_MANY = scene({
        title: "본문이 여럿인 씬",
        type: "cinematic",
        scriptureRef: REF,
        extras: { additional_refs: ["1kgs-19:5", "1kgs-19:6"] },
      });
      vi.mocked(startMission).mockResolvedValue(WITH_MANY);
      vi.mocked(startJoseph).mockResolvedValue(WITH_MANY);

      renderPage(make());

      expect(await screen.findByText(`${MARKER}:${REF}`)).toBeInTheDocument();
      expect(screen.getByText(`${MARKER}:1kgs-19:5`)).toBeInTheDocument();
      expect(screen.getByText(`${MARKER}:1kgs-19:6`)).toBeInTheDocument();
    },
  );
});
