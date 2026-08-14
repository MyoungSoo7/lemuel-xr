import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { PassageModal } from "./PassageModal";
import {
  fetchScripturePassage,
  type ScripturePassage,
} from "@/lib/api/content";

vi.mock("@/lib/api/content", () => ({
  fetchScripturePassage: vi.fn(),
}));

const mockFetch = vi.mocked(fetchScripturePassage);

/** 재시도를 끄지 않으면 실패 케이스가 기본 3회 재시도 동안 매달린다. */
function 감싸기(ui: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>{ui}</QueryClientProvider>,
  );
}

function 본문(over: Partial<ScripturePassage> = {}): ScripturePassage {
  return {
    id: 1,
    reference: "시편 23:1-3",
    translation: "개역개정",
    book: "시편",
    bookCode: "PSA",
    chapter: 23,
    verseStart: 1,
    verseEnd: 3,
    text: "여호와는 나의 목자시니 내게 부족함이 없으리로다",
    themeTags: null,
    characterTags: null,
    ...over,
  };
}

/**
 * scripture_ref 모달. 사용자가 카드에서 원문을 열었을 때 **본문이 실제로 보이는가**,
 * 그리고 실패했을 때 모달이 빈 껍데기로 남지 않는가를 잰다.
 */
describe("PassageModal", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("로딩 중에도 어떤 구절을 여는지 제목으로 알 수 있다", () => {
    mockFetch.mockReturnValue(new Promise(() => {}));
    감싸기(<PassageModal reference="시편 23:1-3" onClose={vi.fn()} />);

    expect(
      screen.getByRole("heading", { name: "시편 23:1-3" }),
    ).toBeInTheDocument();
    expect(screen.getByText("본문 로드 중...")).toBeInTheDocument();
  });

  it("요청한 reference 그대로 조회한다", async () => {
    mockFetch.mockResolvedValue(본문());
    감싸기(<PassageModal reference="시편 23:1-3" onClose={vi.fn()} />);
    await waitFor(() => expect(mockFetch).toHaveBeenCalledWith("시편 23:1-3"));
  });

  it("성공하면 본문과 출처(책·장·절·역본)가 보인다", async () => {
    mockFetch.mockResolvedValue(본문());
    감싸기(<PassageModal reference="시편 23:1-3" onClose={vi.fn()} />);

    expect(
      await screen.findByText(
        "여호와는 나의 목자시니 내게 부족함이 없으리로다",
      ),
    ).toBeInTheDocument();
    // 어느 역본인지 안 보이면 사용자는 어떤 번역을 읽는지 모른다.
    expect(screen.getByText("시편 23장 1–3절 · 개역개정")).toBeInTheDocument();
    expect(screen.queryByText("본문 로드 중...")).not.toBeInTheDocument();
  });

  it("한 절짜리(verseEnd 없음)는 범위 표기를 붙이지 않는다", async () => {
    mockFetch.mockResolvedValue(본문({ verseStart: 1, verseEnd: null }));
    감싸기(<PassageModal reference="시편 23:1" onClose={vi.fn()} />);
    expect(
      await screen.findByText("시편 23장 1절 · 개역개정"),
    ).toBeInTheDocument();
  });

  it("시작·끝 절이 같으면 '1–1' 같은 군더더기를 만들지 않는다", async () => {
    mockFetch.mockResolvedValue(본문({ verseStart: 1, verseEnd: 1 }));
    감싸기(<PassageModal reference="시편 23:1" onClose={vi.fn()} />);
    expect(
      await screen.findByText("시편 23장 1절 · 개역개정"),
    ).toBeInTheDocument();
  });

  it("주제·인물 태그가 있으면 인물 코드는 한국어로, 모르는 코드는 원문으로 뜬다", async () => {
    mockFetch.mockResolvedValue(
      본문({ themeTags: ["위로", "인도"], characterTags: ["david", "ruth"] }),
    );
    감싸기(<PassageModal reference="시편 23:1-3" onClose={vi.fn()} />);

    expect(await screen.findByText("#위로")).toBeInTheDocument();
    expect(screen.getByText("#인도")).toBeInTheDocument();
    expect(screen.getByText("👤 다윗")).toBeInTheDocument();
    // 라벨 사전에 없는 인물은 코드 그대로 — 조용히 사라지지 않는다.
    expect(screen.getByText("👤 ruth")).toBeInTheDocument();
  });

  it("태그가 null 이면 태그 영역 자체가 없다", async () => {
    mockFetch.mockResolvedValue(본문());
    감싸기(<PassageModal reference="시편 23:1-3" onClose={vi.fn()} />);
    await screen.findByText(/여호와는 나의 목자시니/);
    expect(screen.queryByText(/^#/)).not.toBeInTheDocument();
    expect(screen.queryByText(/👤/)).not.toBeInTheDocument();
  });

  it("태그가 빈 배열이면 모달에 숫자 '0' 이 새어 나온다 — 소스 버그", async () => {
    // `(themeTags?.length || characterTags?.length) && (...)` 가 0 으로 평가되고,
    // React 는 0 을 *렌더한다*(false/null 만 건너뛴다). 백엔드가 태그를 null 이
    // 아니라 [] 로 주는 순간 본문 아래에 정체불명의 0 이 찍힌다.
    // 현재 동작을 못으로 박아 두되, 고쳐야 할 버그로 남긴다.
    mockFetch.mockResolvedValue(본문({ themeTags: [], characterTags: [] }));
    const { container } = 감싸기(
      <PassageModal reference="시편 23:1-3" onClose={vi.fn()} />,
    );
    await screen.findByText(/여호와는 나의 목자시니/);
    const 본문영역 = container.querySelector("blockquote")?.parentElement;
    expect(본문영역?.textContent).toContain("0");
  });

  it("실패하면 빈 모달이 아니라 실패 사유를 보여 준다", async () => {
    mockFetch.mockRejectedValue(new Error("네트워크 오류"));
    감싸기(<PassageModal reference="시편 23:1-3" onClose={vi.fn()} />);

    expect(
      await screen.findByText("본문을 불러올 수 없습니다: 네트워크 오류"),
    ).toBeInTheDocument();
    expect(screen.queryByText("본문 로드 중...")).not.toBeInTheDocument();
  });

  it("닫기 버튼으로 닫힌다", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    mockFetch.mockResolvedValue(본문());
    감싸기(<PassageModal reference="시편 23:1-3" onClose={onClose} />);

    await user.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("바깥 어둠을 눌러도 닫히지만, 본문 안을 누르면 닫히지 않는다", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    mockFetch.mockResolvedValue(본문());
    const { container } = 감싸기(
      <PassageModal reference="시편 23:1-3" onClose={onClose} />,
    );
    await screen.findByText(/여호와는 나의 목자시니/);

    // 본문을 드래그·클릭하다 모달이 닫히면 읽던 자리를 잃는다.
    await user.click(screen.getByText(/여호와는 나의 목자시니/));
    expect(onClose).not.toHaveBeenCalled();

    await user.click(container.firstChild as HTMLElement);
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
