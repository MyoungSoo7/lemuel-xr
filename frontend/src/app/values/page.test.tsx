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
import type { ProfileResponse } from "@/lib/api/values";
import ValuesPage from "./page";

// 백엔드로 나가는 유일한 통로 (그 밑은 client.ts 의 axios 인스턴스).
vi.mock("@/lib/api/values", () => ({
  getValueProfile: vi.fn(),
  updateValueProfile: vi.fn(),
  recordPractice: vi.fn(),
}));
const { getValueProfile, recordPractice, updateValueProfile } =
  await import("@/lib/api/values");
const getMock = vi.mocked(getValueProfile);
const updateMock = vi.mocked(updateValueProfile);
const practiceMock = vi.mocked(recordPractice);

/**
 * 가치 빌더는 이 앱의 AR(일상 습관) 쪽 전부다. VR 미션과 달리 *매일* 돌아오는
 * 화면이라, 여기서 조용히 깨지면 사용자는 며칠치 기록을 잃고서야 안다.
 *
 * 그래서 재는 축:
 *  - 네 상태(로딩 / 오류 / 빈 프로파일 / 정의된 프로파일)가 각각 다른 것을 보여 주는가
 *  - 정의·수정 폼이 백엔드로 *무엇을* 보내는가 (patch 모양이 곧 저장 결과다)
 *  - "오늘 실천 +1" 이 정확히 그 가치 id 로 나가는가
 *  - 저장 후 폼이 닫히고 목록이 갱신되는가
 */

function renderValues() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ValuesPage />
    </QueryClientProvider>,
  );
}

const EMPTY: ProfileResponse = {
  values: {},
  stats: { totalPractices7d: 0, countByValue: {}, cdrIndex: 0, tier: "씨앗" },
};

const FILLED: ProfileResponse = {
  values: {
    "1": {
      title: "흔들리지 않는 결정",
      anchor_character: "joseph",
      note: "풍년에 곳간을 짓는다",
    },
    "3": { title: "두려운 채로 한 걸음" },
  },
  stats: {
    totalPractices7d: 5,
    countByValue: { "1": 4, "3": 1 },
    cdrIndex: 72,
    tier: "뿌리",
  },
};

beforeEach(() => {
  getMock.mockReset();
  updateMock.mockReset();
  practiceMock.mockReset();
  getMock.mockResolvedValue(EMPTY);
});

describe("네 가지 화면 상태", () => {
  it("불러오는 동안 로딩만 보여 준다", () => {
    getMock.mockReturnValue(new Promise(() => {}));
    renderValues();

    expect(screen.getByText("불러오는 중...")).toBeInTheDocument();
    // 로딩 중에 빈 카드 7장이 먼저 뜨면 "아무것도 정의 안 했다"로 오독된다.
    expect(
      screen.queryByRole("heading", { name: "자기만의 7 가치" }),
    ).toBeNull();
  });

  it("실패하면 오류 사유를 그대로 보여 준다", async () => {
    getMock.mockRejectedValue(new Error("Request failed with status code 500"));
    renderValues();

    expect(
      await screen.findByText(/오류: Request failed with status code 500/),
    ).toBeInTheDocument();
    // 실패를 빈 프로파일로 위장하면 사용자가 기록이 날아갔다고 믿는다.
    expect(screen.queryByRole("button", { name: "정의하기" })).toBeNull();
  });

  it("첫 방문(빈 프로파일)에는 시작을 권하는 안내가 나온다", async () => {
    renderValues();

    expect(
      await screen.findByText(/아직 가치를 하나도 정의하지/),
    ).toBeInTheDocument();
    // 7칸이 전부 비어 있고 전부 "정의하기" 로 열려 있어야 한다.
    expect(screen.getAllByRole("button", { name: "정의하기" })).toHaveLength(7);
    expect(screen.getAllByText("아직 정의되지 않음")).toHaveLength(7);
    // 아직 아무것도 없으니 실천 버튼은 없다.
    expect(screen.queryByRole("button", { name: "오늘 실천 +1" })).toBeNull();
  });

  it("정의된 가치는 제목·연결 인물·7일 횟수를 보여 준다", async () => {
    getMock.mockResolvedValue(FILLED);
    renderValues();

    expect(await screen.findByText("흔들리지 않는 결정")).toBeInTheDocument();
    expect(screen.getByText("두려운 채로 한 걸음")).toBeInTheDocument();
    // anchor 는 코드값(joseph)이 아니라 한글 라벨로 나와야 한다.
    expect(screen.getByText(/요셉 의 가치와 연결/)).toBeInTheDocument();
    // 정의된 2개만 수정·실천 가능, 나머지 5개는 여전히 정의하기.
    expect(screen.getAllByRole("button", { name: "수정" })).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: "정의하기" })).toHaveLength(5);
    expect(
      screen.getAllByRole("button", { name: "오늘 실천 +1" }),
    ).toHaveLength(2);
    // 하나라도 정의했으면 시작 권유 안내는 사라진다.
    expect(screen.queryByText(/아직 가치를 하나도 정의하지/)).toBeNull();
  });
});

describe("상단 통계", () => {
  it("주간 합계·CDR·단계를 백엔드 값 그대로 표시한다", async () => {
    getMock.mockResolvedValue(FILLED);
    renderValues();

    expect(await screen.findByText("5회")).toBeInTheDocument();
    expect(screen.getByText("72")).toBeInTheDocument();
    expect(screen.getByText("뿌리")).toBeInTheDocument();
  });

  it("가치별 7일 횟수는 해당 카드에 붙는다", async () => {
    getMock.mockResolvedValue(FILLED);
    renderValues();

    const card = (await screen.findByText("흔들리지 않는 결정")).closest(
      "article",
    )!;
    expect(within(card).getByText("4")).toBeInTheDocument();

    // 기록이 없는 가치는 빈 칸이 아니라 0 이어야 한다 — 빈 칸은 로딩으로 읽힌다.
    const untouched = screen.getByText("가치 2").closest("article")!;
    expect(within(untouched).getByText("0")).toBeInTheDocument();
  });

  it("응답에 stats·values 가 빠져 있어도 0 과 '—' 로 떨어진다", async () => {
    // 백엔드가 필드를 통째로 생략해도 화면이 죽으면 안 된다 —
    // 여기서 터지면 가치 화면 전체가 흰 화면이 된다.
    getMock.mockResolvedValue({} as never);
    renderValues();

    expect(await screen.findByText("0회")).toBeInTheDocument();
    expect(screen.getByText("—")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "정의하기" })).toHaveLength(7);
  });
});

describe("가치 정의·수정 폼", () => {
  it("제목만 넣어도 저장된다 — 최소 입력이 한 줄이라는 게 설계 의도", async () => {
    const user = userEvent.setup();
    updateMock.mockResolvedValue(EMPTY);
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "정의하기" }))[0],
    );
    await user.type(
      screen.getByPlaceholderText(/흔들리지 않는 결정/),
      "  매일 걷기  ",
    );
    await user.click(screen.getByRole("button", { name: "저장" }));

    // 키가 문자열 "1" 이어야 백엔드 JSONB patch 와 맞물린다.
    // 앞뒤 공백은 잘라서 보낸다 — 안 자르면 목록이 들쭉날쭉해진다.
    await waitFor(() =>
      expect(updateMock).toHaveBeenCalledWith({ "1": { title: "매일 걷기" } }),
    );
  });

  it("연결 인물과 메모를 넣으면 함께 보낸다", async () => {
    const user = userEvent.setup();
    updateMock.mockResolvedValue(EMPTY);
    renderValues();

    const cards = await screen.findAllByRole("button", { name: "정의하기" });
    await user.click(cards[1]); // 가치 2
    const form = screen.getByRole("button", { name: "저장" }).closest("form")!;

    await user.type(
      within(form).getByPlaceholderText(/흔들리지 않는 결정/),
      "용기",
    );
    await user.selectOptions(within(form).getByRole("combobox"), "moses");
    await user.type(
      within(form).getByPlaceholderText("메모 (선택)"),
      "떨기나무",
    );
    await user.click(within(form).getByRole("button", { name: "저장" }));

    await waitFor(() =>
      expect(updateMock).toHaveBeenCalledWith({
        "2": { title: "용기", anchor_character: "moses", note: "떨기나무" },
      }),
    );
  });

  it("선택 항목을 비워 두면 키 자체를 보내지 않는다", async () => {
    // undefined 를 실어 보내면 백엔드가 기존 값을 지울지 무시할지 갈린다.
    const user = userEvent.setup();
    updateMock.mockResolvedValue(EMPTY);
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "정의하기" }))[0],
    );
    const form = screen.getByRole("button", { name: "저장" }).closest("form")!;
    await user.type(
      within(form).getByPlaceholderText(/흔들리지 않는 결정/),
      "쉼",
    );
    // 메모에 공백만 넣어도 note 는 붙지 않아야 한다.
    await user.type(within(form).getByPlaceholderText("메모 (선택)"), "   ");
    await user.click(within(form).getByRole("button", { name: "저장" }));

    await waitFor(() => expect(updateMock).toHaveBeenCalled());
    expect(Object.keys(updateMock.mock.calls[0][0]["1"]!)).toEqual(["title"]);
  });

  it("제목이 비면 저장이 잠긴다", async () => {
    const user = userEvent.setup();
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "정의하기" }))[0],
    );
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();

    await user.type(screen.getByPlaceholderText(/흔들리지 않는 결정/), "   ");
    expect(screen.getByRole("button", { name: "저장" })).toBeDisabled();
    expect(updateMock).not.toHaveBeenCalled();
  });

  it("제목이 비면 Enter 로도, 폼 제출로도 저장되지 않는다", async () => {
    // 제목은 <input type="text"> 라 Enter 가 곧 제출 시도다. 지금은 기본 제출
    // 버튼이 disabled 라 브라우저가 암묵적 제출을 막아 주지만, 그건 버튼 상태에
    // 기댄 방어다. onSubmit 안의 trim 가드가 두 번째 방벽이라 제출 이벤트를
    // 직접 쏴서 둘 다 확인한다 — 새면 제목 없는 가치가 저장된다.
    const user = userEvent.setup();
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "정의하기" }))[0],
    );
    const input = screen.getByPlaceholderText(/흔들리지 않는 결정/);
    await user.type(input, "   {Enter}");
    expect(updateMock).not.toHaveBeenCalled();

    fireEvent.submit(input.closest("form")!);
    expect(updateMock).not.toHaveBeenCalled();
    // 폼은 열린 채로 남는다 — 사용자가 계속 쓸 수 있어야 한다.
    expect(screen.getByRole("button", { name: "저장" })).toBeInTheDocument();
  });

  it("수정을 열면 기존 값이 채워져 있다 — 다시 타이핑하게 만들지 않는다", async () => {
    const user = userEvent.setup();
    getMock.mockResolvedValue(FILLED);
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "수정" }))[0],
    );
    const form = screen.getByRole("button", { name: "저장" }).closest("form")!;

    expect(within(form).getByPlaceholderText(/흔들리지 않는 결정/)).toHaveValue(
      "흔들리지 않는 결정",
    );
    expect(within(form).getByRole("combobox")).toHaveValue("joseph");
    expect(within(form).getByPlaceholderText("메모 (선택)")).toHaveValue(
      "풍년에 곳간을 짓는다",
    );
  });

  it("취소하면 아무것도 보내지 않고 폼이 닫힌다", async () => {
    const user = userEvent.setup();
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "정의하기" }))[0],
    );
    await user.click(screen.getByRole("button", { name: "취소" }));

    expect(screen.queryByRole("button", { name: "저장" })).toBeNull();
    expect(updateMock).not.toHaveBeenCalled();
    expect(screen.getAllByRole("button", { name: "정의하기" })).toHaveLength(7);
  });

  it("한 번에 한 카드만 편집한다", async () => {
    const user = userEvent.setup();
    renderValues();

    const buttons = await screen.findAllByRole("button", { name: "정의하기" });
    await user.click(buttons[0]);
    await user.click(screen.getAllByRole("button", { name: "정의하기" })[0]); // 이제 가치 2

    // 폼이 두 개 열려 있으면 어느 쪽이 저장되는지 사용자가 알 수 없다.
    expect(screen.getAllByRole("button", { name: "저장" })).toHaveLength(1);
  });

  it("저장 중에는 버튼이 '저장 중...' 으로 잠긴다", async () => {
    const user = userEvent.setup();
    let release!: (v: ProfileResponse) => void;
    updateMock.mockReturnValue(
      new Promise<ProfileResponse>((r) => {
        release = r;
      }),
    );
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "정의하기" }))[0],
    );
    await user.type(screen.getByPlaceholderText(/흔들리지 않는 결정/), "쉼");
    await user.click(screen.getByRole("button", { name: "저장" }));

    const pending = await screen.findByRole("button", { name: "저장 중..." });
    expect(pending).toBeDisabled();
    release(EMPTY);
  });

  it("저장에 성공하면 폼이 닫히고 목록을 다시 불러온다", async () => {
    const user = userEvent.setup();
    updateMock.mockResolvedValue(EMPTY);
    getMock.mockResolvedValueOnce(EMPTY).mockResolvedValue(FILLED);
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "정의하기" }))[0],
    );
    await user.type(screen.getByPlaceholderText(/흔들리지 않는 결정/), "쉼");
    await user.click(screen.getByRole("button", { name: "저장" }));

    // invalidateQueries 로 재조회 → 방금 저장한 내용이 화면에 반영된다.
    expect(await screen.findByText("흔들리지 않는 결정")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "저장" })).toBeNull();
    expect(getMock.mock.calls.length).toBeGreaterThan(1);
  });

  it("저장이 실패하면 폼을 닫지 않는다 — 쓴 내용을 잃지 않게", async () => {
    const user = userEvent.setup();
    updateMock.mockRejectedValue(new Error("500"));
    renderValues();

    await user.click(
      (await screen.findAllByRole("button", { name: "정의하기" }))[0],
    );
    await user.type(screen.getByPlaceholderText(/흔들리지 않는 결정/), "쉼");
    await user.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(updateMock).toHaveBeenCalled());
    expect(screen.getByPlaceholderText(/흔들리지 않는 결정/)).toHaveValue("쉼");
  });
});

describe("오늘 실천 +1", () => {
  it("누른 카드의 가치 id 로 기록한다", async () => {
    const user = userEvent.setup();
    getMock.mockResolvedValue(FILLED);
    practiceMock.mockResolvedValue({
      id: 1,
      valueId: 3,
      practicedAt: "2026-08-14T00:00:00Z",
    });
    renderValues();

    // 두 번째 정의된 가치는 id 3 이다 (2 는 비어 있음).
    const card = (await screen.findByText("두려운 채로 한 걸음")).closest(
      "article",
    )!;
    await user.click(
      within(card).getByRole("button", { name: "오늘 실천 +1" }),
    );

    // id 가 어긋나면 다른 가치에 기록이 쌓인다 — 화면상 오류는 안 난다.
    await waitFor(() =>
      expect(practiceMock).toHaveBeenCalledWith({ valueId: 3 }),
    );
  });

  it("기록 후 목록을 다시 불러와 횟수가 갱신된다", async () => {
    const user = userEvent.setup();
    getMock.mockResolvedValueOnce(FILLED).mockResolvedValue({
      ...FILLED,
      stats: {
        ...FILLED.stats,
        totalPractices7d: 6,
        countByValue: { "1": 5, "3": 1 },
      },
    });
    practiceMock.mockResolvedValue({
      id: 2,
      valueId: 1,
      practicedAt: "2026-08-14T00:00:00Z",
    });
    renderValues();

    const card = (await screen.findByText("흔들리지 않는 결정")).closest(
      "article",
    )!;
    await user.click(
      within(card).getByRole("button", { name: "오늘 실천 +1" }),
    );

    expect(await screen.findByText("6회")).toBeInTheDocument();
  });

  it("기록 중에는 모든 실천 버튼이 잠긴다 — 연타로 중복 기록되지 않게", async () => {
    const user = userEvent.setup();
    getMock.mockResolvedValue(FILLED);
    practiceMock.mockReturnValue(new Promise(() => {}));
    renderValues();

    const card = (await screen.findByText("흔들리지 않는 결정")).closest(
      "article",
    )!;
    await user.click(
      within(card).getByRole("button", { name: "오늘 실천 +1" }),
    );

    await waitFor(() => {
      for (const b of screen.getAllByRole("button", { name: "오늘 실천 +1" })) {
        expect(b).toBeDisabled();
      }
    });
    expect(practiceMock).toHaveBeenCalledTimes(1);
  });
});

describe("네비게이션", () => {
  it("홈으로 돌아가는 링크가 있다", async () => {
    renderValues();
    expect(await screen.findByRole("link", { name: /홈/ })).toHaveAttribute(
      "href",
      "/",
    );
  });
});

describe("알려진 결함 고정 — 연결 인물 라벨", () => {
  it("CHARACTER_LABEL 에 없는 인물이면 이름 없는 빈 줄이 남는다", async () => {
    // 홈 화면은 7인물(솔로몬·욥·엘리야 포함)을 노출하는데, 이 페이지의
    // CHARACTER_LABEL 과 EditValueForm 의 <select> 는 4인물(요셉·모세·다윗·예수)뿐이다.
    // 백엔드에 solomon 이 저장돼 있으면 "↳  의 가치와 연결" — 화살표만 남고
    // 인물 이름이 빠진 줄이 나온다. 지금 동작을 고정해 두고, 고칠 때
    // 이 테스트가 먼저 빨개지게 한다.
    getMock.mockResolvedValue({
      ...EMPTY,
      values: {
        "1": {
          title: "빈 손의 지혜",
          anchor_character: "solomon" as never,
        },
      },
    });
    renderValues();

    const line = await screen.findByText(/의 가치와 연결/);
    expect(line.textContent).toBe("↳  의 가치와 연결");
    expect(line.textContent).not.toContain("솔로몬");

    // 게다가 <select> 에도 solomon 선택지가 없어, 수정 화면을 열면 연결이
    // 조용히 "선택 안 함" 으로 초기화되고 그대로 저장하면 지워진다.
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "수정" }));
    expect(screen.getByRole("combobox")).toHaveValue("");
  });
});
