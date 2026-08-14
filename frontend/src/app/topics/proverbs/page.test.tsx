import { describe, expect, it, vi, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type {
  ProverbsInteractionResponse,
  ProverbsThemeListResponse,
} from "@/lib/api/content";
import {
  fetchProverbsThemes,
  recordProverbsInteraction,
} from "@/lib/api/content";
import ProverbsThemePage from "./page";
// 번호 리터럴은 `@/lib/crisis-resources` 에만 산다 —
// `scripts/check_frontend_hotline.py` 가 화면·테스트·주석 전부를 검사한다.
// (이 자리들은 소프트 패턴이 같은 줄의 문맥 단어를 요구해서 게이트를 통과하고
//  있었지만, 그건 규칙의 취지가 아니라 정규식의 빈틈이다.)
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";

/**
 * /topics/proverbs — 기준2 (잠언과 지혜) 주제별 조회.
 *
 * §3.4 의 위험은 "잠언을 공식으로 읽는 것" 이다. 그래서 이 화면에서 재는 축은
 *  1. 구절만 덜렁 나오지 않고 *결과 보장이 아니라 방향* 안내(guidance)가 항상 붙는가
 *  2. 고른 구절이 그대로 기록 API 로 나가고, 저장 문구에 압박이 없는가
 *  3. 4-layer 안전선 3번 — "AI 보조" 라벨 + 위기 자원 라인이 로딩/성공/실패 어디서도 남는가
 */

vi.mock("@/lib/api/content", () => ({
  fetchProverbsThemes: vi.fn(),
  recordProverbsInteraction: vi.fn(),
}));

const mockThemes = vi.mocked(fetchProverbsThemes);
const mockRecord = vi.mocked(recordProverbsInteraction);

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ProverbsThemePage />
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

const THEMES: ProverbsThemeListResponse = {
  themes: [
    {
      key: "tongue",
      title: "말과 혀",
      summary: "말은 사람을 살리기도 하고 죽이기도 합니다.",
      guidance:
        "이 구절들은 결과를 보장하는 공식이 아니라 오늘 한 걸음의 방향입니다.",
      verses: [
        { ref: "잠 13:3", text: "입을 지키는 자는 자기의 생명을 보전하나" },
        { ref: "잠 15:1", text: "유순한 대답은 분노를 쉬게 하여도" },
      ],
    },
    {
      key: "diligence",
      title: "부지런함",
      summary: "게으름과 부지런함의 결이 다릅니다.",
      guidance: "부지런함이 곧 부를 보장한다는 뜻이 아닙니다.",
      verses: [{ ref: "잠 6:6", text: "게으른 자여 개미에게로 가서" }],
    },
  ],
  safetyFooter: "답을 다 지키지 않아도 됩니다.",
  aiFooter: "AI 보조 — 본문은 성경 참조. 잠언 외 자료는 쓰지 않습니다.",
};

function interactionResponse(): ProverbsInteractionResponse {
  return {
    id: 5,
    theme: "tongue",
    chosenProverbRef: "잠 13:3",
    safetyFooter: "답을 다 지키지 않아도 됩니다.",
    aiFooter: "AI 보조 — 본문은 성경 참조.",
  };
}

beforeEach(() => {
  mockThemes.mockResolvedValue(THEMES);
  mockRecord.mockResolvedValue(interactionResponse());
});

describe("/topics/proverbs", () => {
  it("주제를 불러오는 동안에도 위기 자원 라인과 AI 보조 fallback 라벨이 이미 떠 있다", async () => {
    // 로딩 상태. 안전선은 데이터보다 먼저 자리를 잡아야 한다.
    const d = deferred<ProverbsThemeListResponse>();
    mockThemes.mockReturnValue(d.promise);
    renderPage();

    expect(
      screen.getByText("위 주제에서 오늘 마음에 닿는 하나를 골라보세요."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /말과 혀/ }),
    ).not.toBeInTheDocument();
    const footer = screen.getByText(/AI 보조/);
    expect(footer).toHaveTextContent("잠언(성경) 외 자료는 사용하지 않습니다.");
    expect(footer).toHaveTextContent(new RegExp(CRISIS_DEFAULT.tel));

    await act(async () => {
      d.resolve(THEMES);
    });
  });

  it("주제를 받으면 제목과 요약이 버튼으로 뜨고, AI 라벨은 서버 문구로 바뀐다", async () => {
    renderPage();

    const tongue = await screen.findByRole("button", { name: /말과 혀/ });
    expect(tongue).toHaveTextContent(
      "말은 사람을 살리기도 하고 죽이기도 합니다.",
    );
    expect(
      screen.getByRole("button", { name: /부지런함/ }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/잠언 외 자료는 쓰지 않습니다/),
    ).toBeInTheDocument();
  });

  it("주제 조회가 실패해도 위기 자원 라인과 AI 보조 fallback 은 남는다", async () => {
    mockThemes.mockRejectedValue(new Error("503"));
    renderPage();

    await waitFor(() => expect(mockThemes).toHaveBeenCalled());
    const footer = screen.getByText(/AI 보조/);
    expect(footer).toHaveTextContent("잠언(성경) 외 자료는 사용하지 않습니다.");
    expect(footer).toHaveTextContent(new RegExp(CRISIS_DEFAULT.tel));
    expect(
      screen.getByText("위 주제에서 오늘 마음에 닿는 하나를 골라보세요."),
    ).toBeInTheDocument();
  });

  it("주제를 고르면 구절보다 먼저 '공식이 아니라 방향' 안내가 붙는다 (§3.4)", async () => {
    // 이 안내가 빠지면 잠언이 "이대로 하면 이렇게 된다" 는 공식으로 읽힌다.
    const { user } = renderPage();
    await user.click(await screen.findByRole("button", { name: /말과 혀/ }));

    const section = screen
      .getByRole("heading", { name: "말과 혀", level: 3 })
      .closest("section") as HTMLElement;
    const s = within(section);

    expect(
      s.getByText(
        "이 구절들은 결과를 보장하는 공식이 아니라 오늘 한 걸음의 방향입니다.",
      ),
    ).toBeInTheDocument();
    expect(s.getByText("📖 잠 13:3")).toBeInTheDocument();
    expect(
      s.getByText(/입을 지키는 자는 자기의 생명을 보전하나/),
    ).toBeInTheDocument();
    expect(s.getByText("📖 잠 15:1")).toBeInTheDocument();
    // 나레이션 버튼(고령자 접근성)은 주제를 열면 함께 뜬다.
    expect(s.getByRole("button", { name: /듣기/ })).toBeInTheDocument();
  });

  it("구절을 누르면 그 구절 참조가 그대로 기록되고 압박 없는 확인 문구가 뜬다", async () => {
    const { user } = renderPage();
    await user.click(await screen.findByRole("button", { name: /말과 혀/ }));

    await user.click(screen.getByRole("button", { name: /입을 지키는 자는/ }));

    await waitFor(() =>
      expect(mockRecord).toHaveBeenCalledWith({
        theme: "tongue",
        chosenProverbRef: "잠 13:3",
        dimension: "spiritual",
      }),
    );
    // R3 — "다 지켜야 한다" 는 압박을 명시적으로 걷어내는 문구.
    const saved = await screen.findByText(/오늘 고른 구절로 기록했습니다/);
    expect(saved).toHaveTextContent("답을 다 지켜야 한다는 압박 없이");
  });

  it("다른 구절을 누르면 그 구절로 다시 기록한다", async () => {
    const { user } = renderPage();
    await user.click(await screen.findByRole("button", { name: /말과 혀/ }));

    await user.click(screen.getByRole("button", { name: /입을 지키는 자는/ }));
    expect(
      await screen.findByText(/오늘 고른 구절로 기록했습니다/),
    ).toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: /유순한 대답은 분노를/ }),
    );

    await waitFor(() =>
      expect(mockRecord).toHaveBeenLastCalledWith({
        theme: "tongue",
        chosenProverbRef: "잠 15:1",
        dimension: "spiritual",
      }),
    );
    expect(mockRecord).toHaveBeenCalledTimes(2);
  });

  it("기록이 실패하면 실패를 알리고 저장 확인 문구는 띄우지 않는다", async () => {
    // 실패했는데 '기록했습니다' 가 뜨면 사용자는 남지 않은 기록을 남았다고 믿는다.
    mockRecord.mockRejectedValue(new Error("500"));
    const { user } = renderPage();
    await user.click(await screen.findByRole("button", { name: /부지런함/ }));

    await user.click(
      screen.getByRole("button", { name: /게으른 자여 개미에게로/ }),
    );

    expect(await screen.findByText("기록에 실패했습니다.")).toBeInTheDocument();
    expect(
      screen.queryByText(/오늘 고른 구절로 기록했습니다/),
    ).not.toBeInTheDocument();
  });

  it("[버그 문서화] 주제를 바꿔도 앞 주제의 '기록했습니다' 문구가 그대로 남는다", async () => {
    // ThemeVerses 에 key 가 없어 주제를 바꿔도 같은 인스턴스가 재사용된다.
    // 그래서 '말과 혀' 에서 저장한 뒤 '부지런함' 으로 옮기면, 이 주제에서는
    // 아무것도 고르지 않았는데 저장 확인 문구가 얹혀 있다.
    // 아래는 *현재 동작* 을 못박아 둔 것이고, 고쳐지면 이 테스트가 깨져 알려준다.
    const { user } = renderPage();
    await user.click(await screen.findByRole("button", { name: /말과 혀/ }));
    await user.click(screen.getByRole("button", { name: /입을 지키는 자는/ }));
    expect(
      await screen.findByText(/오늘 고른 구절로 기록했습니다/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /부지런함/ }));

    expect(
      screen.getByRole("heading", { name: "부지런함", level: 3 }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/오늘 고른 구절로 기록했습니다/),
    ).toBeInTheDocument();
  });

  it("헤더와 하단 안내가 이 화면의 성격을 밝힌다 — 주제 목록 링크 + AI 보조 라벨", async () => {
    renderPage();

    expect(
      screen.getByRole("heading", { name: "잠언과 지혜", level: 1 }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "← 주제" })).toHaveAttribute(
      "href",
      "/topics",
    );
    expect(
      screen.getByText(/지혜는 공식이 아니라 오늘 한 걸음의 방향입니다/),
    ).toBeInTheDocument();
    expect(await screen.findByText(/AI 보조/)).toBeInTheDocument();
  });
});
