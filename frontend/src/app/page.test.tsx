import { readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
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
import type { ClassifyResponse } from "@/lib/api/emotion";
import HomePage from "./page";
// 번호 리터럴은 `@/lib/crisis-resources` 에만 산다 —
// `scripts/check_frontend_hotline.py` 가 화면·테스트·주석 전부를 검사한다.
// (이 자리들은 소프트 패턴이 같은 줄의 문맥 단어를 요구해서 게이트를 통과하고
//  있었지만, 그건 규칙의 취지가 아니라 정규식의 빈틈이다.)
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";

// 백엔드로 나가는 유일한 통로를 여기서 끊는다 (그 밑은 client.ts 의 axios).
vi.mock("@/lib/api/emotion", () => ({
  classifyEmotion: vi.fn(),
}));
const { classifyEmotion } = await import("@/lib/api/emotion");
const classifyMock = vi.mocked(classifyEmotion);

/**
 * 홈은 이 앱의 유일한 진입점이다. 여기서 재는 것은 두 갈래 —
 *
 *  (A) 분류를 *거치지 않고도* 콘텐츠에 들어갈 수 있는가.
 *      감정을 적는 것 자체가 부담인 사용자가 있고, 백엔드 분류가 죽어도
 *      인물 카드·가치빌더로는 들어갈 수 있어야 한다. 이 카드들이 조용히 사라지면
 *      "글을 써야만 들어갈 수 있는 앱" 이 된다.
 *  (B) 분류 결과가 실제 사용자가 누를 수 있는 링크로 바뀌는가.
 *      미구현 인물은 링크가 "#" 이어야 하고(막다른 404 대신), 신뢰도 표기는
 *      0~1 확률을 % 로 옮긴 값이어야 한다.
 */

const BACKEND = join(__dirname, "..", "..", "..", "backend", "src", "main");
const SCENARIOS = join(BACKEND, "resources", "scenarios");
const CHARACTER_KT = join(
  BACKEND,
  "kotlin",
  "github",
  "lms",
  "lemuel",
  "xr",
  "game",
  "domain",
  "Character.kt",
);

/**
 * 인물 목록의 정본은 **`Character.kt` 의 enum** 이다 — 시나리오 yml 디렉터리가 아니다.
 *
 * 2026-08-22 까지 이 파일은 디렉터리를 정본으로 썼다. 아브라함이 「enum 은 열렸는데 홈에
 * 입구가 없는」 상태로 나간 것을 잡으려던 것이고 그건 잘 잡혔지만, 정본을 잘못 짚고 있었다.
 * `ScenarioYamlLoader.loadAll()` 은 디렉터리를 훑지 않고 `Character.entries` 를 순회한다.
 * 즉 **yml 이 있어도 enum 에 없으면 사용자에게 그 인물은 존재하지 않는다.**
 *
 * 이 구별이 지금 실제로 필요하다. 라합은 런타임 대본(`scenarios/rahab.yml`)과 게이트가
 * 다 올라와 있고 enum 한 줄만 사람 결정을 기다린다(`docs/RAHAB-RUNTIME-SIGNOFF.md` 의
 * 결정 줄이 비어 있다). 디렉터리를 정본으로 두면 그 대기 상태가 「홈에 입구가 없는 결함」
 * 으로 잘못 신고되고, 그걸 없애려면 준비된 파일을 지우거나 결정 전에 문을 열어야 한다 —
 * 둘 다 틀린 방향이다.
 *
 * 뒤집힌 쪽(enum 에 있는데 yml 이 없는 것)은 `ScenarioYamlLoaderTest` 가 백엔드에서 막는다.
 * 여기서도 한 번 더 대조한다 — 이 파일의 정본이 공허해지지 않게 하는 확인이다.
 */
function exposedCharacters(): string[] {
  const src = readFileSync(CHARACTER_KT, "utf8");
  const body = src.slice(src.indexOf("enum class Character"));
  return [...body.matchAll(/^ {4}[A-Z_]+\("([a-z_]+)"\)/gm)].map((m) => m[1]);
}

/** yml 은 있는데 enum 에 없는 인물 — 저작은 끝났고 노출 결정만 남은 상태. */
function stagedCharacters(): string[] {
  const exposed = new Set(exposedCharacters());
  return readdirSync(SCENARIOS)
    .filter((f) => f.endsWith(".yml"))
    .map((f) => f.replace(/\.yml$/, ""))
    .filter((c) => !exposed.has(c))
    .sort();
}

/**
 * 「미구현 인물은 링크가 '#'」 검사에 쓸 이름. 손으로 적으면 그 인물이 열리는 날
 * 검사가 조용히 거짓이 된다(룻이 그랬다 — 열린 뒤에도 미구현 예시로 남아 있었다).
 * 그래서 대기 중인 인물에서 뽑고, 대기 중인 인물이 하나도 없으면 enum 에 없는 것이
 * 확실한 이름으로 떨어뜨린다 — 그때도 이 검사는 「모르는 인물을 404 로 보내지 않는다」를
 * 계속 잰다.
 */
const UNIMPLEMENTED = stagedCharacters()[0] ?? "no-such-character";

function renderHome() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <HomePage />
    </QueryClientProvider>,
  );
}

const RESULT: ClassifyResponse = {
  emotionLogId: 1,
  primary: { emotion: "LONELY", confidence: 0.876 },
  recommendations: {
    trackB: [
      { character: "elijah", rationale: "로뎀나무 아래의 탈진" },
      // 이 자리는 ruth → rahab 순으로 손으로 고쳐 왔다. 이제 손으로 적지 않는다 —
      // 위 UNIMPLEMENTED 주석 참조.
      { character: UNIMPLEMENTED, rationale: "미구현 인물" },
    ],
    trackA: [
      { topicId: 3, title: "시편 — 탄식의 언어", rationale: "감정을 말로" },
      { topicId: 4, title: "잠언 — 마음지키기" },
    ],
  },
};

beforeEach(() => {
  classifyMock.mockReset();
});

describe("첫 화면 — 분류 없이도 들어갈 수 있는 길", () => {
  it("저작된 인물 전부가 홈 카드로 있다 — 손으로 적은 목록과 대조하지 않는다", () => {
    // 인물 목록을 여기 손으로 적으면 안 된다. 이 검사는 원래 여덟 개를 나열하고
    // 있었고, 그래서 peter·daniel·esther·jacob 이 열릴 때도, **아브라함이
    // 어느 목록에도 안 들어온 채 열렸을 때도** 초록이었다. 나열형 검사는 자기가
    // 아는 것만 세고, 모르는 인물이 빠진 것은 정의상 못 본다.
    //
    // 그래서 `Character.kt` 의 enum 을 정본으로 쓴다(위 exposedCharacters 주석 참조).
    // 인물이 런타임에 열렸는데 홈에 입구가 없으면, 사용자는 주소를 직접 치지 않는 한
    // 그 화면에 도달할 길이 없다. 실제로 아브라함이 그 상태였다(#100 에서 enum 은
    // 열렸고 홈은 그대로였다).
    const exposed = exposedCharacters();
    const authored = exposed.map((c) => `/${c}`).sort();
    expect(authored.length).toBeGreaterThan(0); // 경로가 틀리면 아래가 공허하게 통과한다

    // enum 에 있는데 yml 이 없으면 로더가 warn 만 남기고 조용히 건너뛴다 — 홈에 입구는
    // 있는데 눌러도 콘텐츠가 없는 인물이 된다. 백엔드에도 같은 검사가 있지만, 여기
    // 정본이 실재하는 저작을 가리키는지 이 파일 안에서 확인해 둔다.
    const yml = new Set(
      readdirSync(SCENARIOS)
        .filter((f) => f.endsWith(".yml"))
        .map((f) => f.replace(/\.yml$/, "")),
    );
    const dangling = exposed.filter((c) => !yml.has(c));
    expect(
      dangling,
      `enum 에는 열렸는데 시나리오 yml 이 없는 인물: ${dangling.join(", ")}`,
    ).toEqual([]);

    renderHome();
    const missions = screen.getByRole("heading", {
      name: /각성의 순간/,
    }).parentElement!;
    const linked = within(missions)
      .getAllByRole("link")
      .map((a) => a.getAttribute("href"))
      .filter((h): h is string => !!h)
      .sort();

    // 중복부터 이름을 대고 잡는다. 아래 `toEqual` 도 중복을 잡기는 하지만, 실패 메시지가
    // 40줄짜리 배열 두 개의 diff 라 무엇이 겹쳤는지 읽히지 않는다. 실제로 그렇게 났다 —
    // #103 과 #104 가 「홈에 아브라함 입구가 없다」를 서로 모른 채 같은 날 각자 고쳤고,
    // 서로 다른 줄에 넣어서 git 이 충돌로 보지 않았다. 두 PR 의 CI 는 각자의 시점에
    // 초록이었는데 합쳐진 main 은 카드가 둘이고 React key 도 겹친 상태였다.
    const duplicated = linked.filter((h, i) => linked.indexOf(h) !== i);
    expect(
      duplicated,
      `홈 카드가 두 번 실린 인물: ${duplicated.join(", ")}`,
    ).toEqual([]);

    // 양방향으로 맞춘다. 빠진 쪽은 「들어갈 입구가 없는 화면」이고, 남는 쪽은
    // 「눌리는데 없는 화면으로 가는 404」다 — 둘 다 사용자에게 보이는 결함이다.
    expect(linked).toEqual(authored);
  });

  it("AR 측 두 입구(가치 빌더 · 7주제 카드)가 있다", () => {
    renderHome();
    expect(
      screen.getByRole("link", { name: /자기만의 7 가치 빌더/ }),
    ).toHaveAttribute("href", "/values");
    expect(
      screen.getByRole("link", { name: /일상 영적 양식/ }),
    ).toHaveAttribute("href", "/topics");
  });

  it("위기 상담 번호 고지가 첫 화면부터 보인다", () => {
    // 홈 헤더의 고지는 CrisisFooter 와 별개다 — 이 앱이 임상 도구가 아니라는
    // 문장과 번호가 *분류를 시도하기 전에* 이미 시야에 있어야 한다.
    renderHome();
    expect(screen.getByText(/의료·임상 도구가 아닙니다/)).toBeInTheDocument();
    expect(
      screen.getByText(new RegExp(CRISIS_DEFAULT.tel)),
    ).toBeInTheDocument();
  });

  it("입력이 비어 있으면 제출 버튼이 잠겨 있다", () => {
    renderHome();
    expect(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    ).toBeDisabled();
  });
});

describe("감정 입력", () => {
  it("샘플 문구를 누르면 입력란이 채워지고 제출이 열린다", async () => {
    const user = userEvent.setup();
    renderHome();

    await user.click(
      screen.getByRole("button", { name: "오늘 너무 외롭고 지쳐있어" }),
    );

    expect(screen.getByRole("textbox")).toHaveValue(
      "오늘 너무 외롭고 지쳐있어",
    );
    expect(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    ).toBeEnabled();
  });

  it("공백만 입력하면 제출이 잠긴 채로 남는다", async () => {
    const user = userEvent.setup();
    renderHome();

    await user.type(screen.getByRole("textbox"), "   ");

    expect(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    ).toBeDisabled();
    expect(classifyMock).not.toHaveBeenCalled();
  });

  it("버튼을 우회한 폼 제출도 빈 입력이면 막는다", () => {
    // disabled 버튼은 시각적 가드일 뿐이다. form.requestSubmit() 이나
    // 확장/자동화가 submit 이벤트를 직접 쏘면 그대로 통과한다 —
    // onSubmit 안의 trim 가드가 두 번째 방벽이고, 여기서만 잴 수 있다.
    renderHome();
    const form = screen.getByRole("textbox").closest("form")!;
    fireEvent.submit(form);
    expect(classifyMock).not.toHaveBeenCalled();
  });

  it("입력 상한이 1000자다 — 백엔드 검증과 같은 숫자", () => {
    renderHome();
    expect(screen.getByRole("textbox")).toHaveAttribute("maxlength", "1000");
  });

  it("제출 중에는 버튼 문구가 바뀌고 다시 눌리지 않는다", async () => {
    const user = userEvent.setup();
    // 두 번 눌러 감정 로그가 두 줄 쌓이는 것을 막는 가드.
    let release!: (v: ClassifyResponse) => void;
    classifyMock.mockReturnValue(
      new Promise<ClassifyResponse>((r) => {
        release = r;
      }),
    );
    renderHome();

    await user.type(screen.getByRole("textbox"), "지쳤어");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    const pending = await screen.findByRole("button", { name: "분류 중..." });
    expect(pending).toBeDisabled();

    release(RESULT);
    await screen.findByText("외로움");
    expect(classifyMock).toHaveBeenCalledTimes(1);
  });
});

describe("분류 결과", () => {
  it("감정 코드를 한글 라벨과 % 신뢰도로 보여 준다", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "오늘 너무 외롭고 지쳐있어");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText("외로움")).toBeInTheDocument();
    // 0.876 → 88% (반올림). 소수점이 그대로 새면 "87.6% 신뢰도" 가 나온다.
    expect(screen.getByText(/88% 신뢰도/)).toBeInTheDocument();
    expect(classifyMock).toHaveBeenCalledWith("오늘 너무 외롭고 지쳐있어");
  });

  it("매핑에 없는 감정 코드는 원문 그대로 내보낸다", async () => {
    // 백엔드가 감정 종류를 늘리면 프론트 라벨 맵보다 먼저 도착한다.
    // 그때 빈 칸이 아니라 원문 코드라도 보여야 한다.
    const user = userEvent.setup();
    classifyMock.mockResolvedValue({
      ...RESULT,
      primary: { emotion: "HOPEFUL" as never, confidence: 0.5 },
    });
    renderHome();

    await user.type(screen.getByRole("textbox"), "괜찮아지고 있어");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText("HOPEFUL")).toBeInTheDocument();
  });

  it("결과가 나오면 직접 진입 카드 묶음은 물러난다", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "외로워");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    await screen.findByText("외로움");
    expect(screen.queryByRole("heading", { name: /각성의 순간/ })).toBeNull();
    expect(screen.queryByRole("link", { name: /7 가치 빌더/ })).toBeNull();
  });

  it("Track B — 구현된 인물은 실제 경로로, 미구현은 Phase 2 + '#' 으로", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "외로워");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    await screen.findByText("외로움");
    expect(screen.getByRole("link", { name: /elijah/ })).toHaveAttribute(
      "href",
      "/elijah",
    );
    // 미구현 인물을 `/{이름}` 으로 보내면 404 다. 막다른 길 대신 그 자리에 머문다.
    const pending = screen.getByRole("link", {
      name: new RegExp(UNIMPLEMENTED),
    });
    expect(pending).toHaveAttribute("href", "#");
    expect(within(pending).getByText("Phase 2")).toBeInTheDocument();
    expect(screen.getByText("로뎀나무 아래의 탈진")).toBeInTheDocument();
  });

  it("Track B 의 '구현됨' 판정이 홈 카드 목록과 어긋나지 않는다", async () => {
    // 이 화면은 인물이 열렸는지를 **두 곳에서 따로** 판정한다 — 위쪽 미션 카드는
    // DIRECT_MISSIONS 의 `active`, 아래쪽 추천 카드는 map 안의 인라인 ACTIVE 집합.
    // 룻을 붙일 때 둘 다 고쳐야 했고, 한쪽만 고치면 아무 테스트도 빨개지지 않은 채
    // "홈에서는 들어가지는데 추천에서는 Phase 2 로 막히는" 반쪽 문이 남는다.
    // 그래서 두 목록을 서로 대조한다 — 소스를 읽지 않고 렌더된 결과끼리 맞춘다.
    const user = userEvent.setup();
    renderHome();

    const missions = screen.getByRole("heading", {
      name: /각성의 순간/,
    }).parentElement!;
    const opened = within(missions)
      .getAllByRole("link")
      .map((a) => a.getAttribute("href"))
      .filter((h): h is string => !!h && h.startsWith("/"))
      .map((h) => h.slice(1));
    expect(opened.length).toBeGreaterThan(0); // 파싱이 0건이면 아래는 아무것도 안 잰다

    classifyMock.mockResolvedValue({
      ...RESULT,
      recommendations: {
        ...RESULT.recommendations,
        trackB: opened.map((character) => ({ character, rationale: "대조용" })),
      },
    });

    await user.type(screen.getByRole("textbox"), "외로워");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );
    await screen.findByText("외로움");

    const blocked = opened.filter(
      (c) =>
        screen
          .getByRole("link", { name: new RegExp(c) })
          .getAttribute("href") !== `/${c}`,
    );
    expect(
      blocked,
      `홈 카드는 열려 있는데 Track B 추천은 Phase 2 로 막는 인물: ${blocked.join(", ")}`,
    ).toEqual([]);
  });

  it("Track A — 제목은 항상, 이유는 있을 때만 보여 준다", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "외로워");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText("시편 — 탄식의 언어")).toBeInTheDocument();
    expect(screen.getByText("감정을 말로")).toBeInTheDocument();
    // rationale 이 없는 항목도 제목은 나와야 한다.
    expect(screen.getByText("잠언 — 마음지키기")).toBeInTheDocument();
  });

  it("추천 배열 자체가 빠진 응답에도 죽지 않는다", async () => {
    // 백엔드가 trackA/trackB 키를 생략하면 .length 접근에서 통째로 터진다.
    // `?? 0` 가드가 그 자리를 막고 있는지 실제로 확인한다 —
    // 여기서 새면 분류에 성공해 놓고 흰 화면이 뜬다.
    const user = userEvent.setup();
    classifyMock.mockResolvedValue({
      ...RESULT,
      recommendations: {} as never,
    });
    renderHome();

    await user.type(screen.getByRole("textbox"), "괜찮아");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText("외로움")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: /Track/ })).toBeNull();
  });

  it("추천이 비어도 결과 화면이 깨지지 않는다", async () => {
    const user = userEvent.setup();
    classifyMock.mockResolvedValue({
      ...RESULT,
      recommendations: { trackA: [], trackB: [] },
    });
    renderHome();

    await user.type(screen.getByRole("textbox"), "괜찮아");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    await screen.findByText("외로움");
    expect(screen.queryByRole("heading", { name: /Track B/ })).toBeNull();
    expect(screen.queryByRole("heading", { name: /Track A/ })).toBeNull();
    // 추천이 하나도 없어도 요셉 미션으로 가는 탈출구는 남는다.
    expect(
      screen.getByRole("link", { name: /요셉 미션 바로 시작/ }),
    ).toHaveAttribute("href", "/joseph");
  });
});

describe("분류 실패", () => {
  it("오류 메시지를 보여 주고, 직접 진입 카드는 그대로 남는다", async () => {
    const user = userEvent.setup();
    classifyMock.mockRejectedValue(new Error("Network Error"));
    renderHome();

    await user.type(screen.getByRole("textbox"), "지쳤어");
    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );

    expect(await screen.findByText(/오류: Network Error/)).toBeInTheDocument();
    // 백엔드가 죽어도 콘텐츠로 들어가는 길이 살아 있어야 한다는 게 요점이다.
    await waitFor(() =>
      expect(screen.getByRole("link", { name: /Joseph/ })).toBeInTheDocument(),
    );
  });

  it("재시도하면 다시 호출한다 — 실패 상태에 갇히지 않는다", async () => {
    const user = userEvent.setup();
    classifyMock
      .mockRejectedValueOnce(new Error("Network Error"))
      .mockResolvedValueOnce(RESULT);
    renderHome();

    await user.type(screen.getByRole("textbox"), "지쳤어");
    const submit = screen.getByRole("button", {
      name: "감정 분석 + 본문 추천",
    });
    await user.click(submit);
    await screen.findByText(/오류: Network Error/);

    await user.click(
      screen.getByRole("button", { name: "감정 분석 + 본문 추천" }),
    );
    expect(await screen.findByText("외로움")).toBeInTheDocument();
    expect(classifyMock).toHaveBeenCalledTimes(2);
  });
});
