import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { beforeEach, describe, expect, it, vi } from "vitest";

/*
  요셉 미션 화면 테스트.

  이 화면에서 조용히 깨질 수 있는 것은 두 가지다.

  1) 씬 상태 기계 — start → decide → decide … → complete 로 *끝까지 걸어가는* 경로.
     중간 한 칸이 끊기면 사용자는 멈춘 화면을 본다. 그래서 렌더 단언이 아니라
     Scene 1 부터 완료 버튼까지 실제로 눌러서 간다.

  2) R4 동의 게이트 — joseph.yml Scene 4 는 level medium · content [betrayal,
     family_trauma] · skip_alternative_scene_id 5 를 선언해 두었는데 2026-08-12 까지
     이 화면에는 **카드가 아예 없었다**. CI 의 check_frontend_trigger_warning.py 는
     "화면이 payload.trigger_warning 을 읽는가" 까지만 본다 — 읽고서 안 그려도 통과한다.
     그 구멍이 여기다: 카드가 실제로 뜨는지, 동의 전에 선택지가 가려지는지를 잰다.
*/

vi.mock("@/lib/api/game", () => ({
  startJoseph: vi.fn(),
  decideJoseph: vi.fn(),
  completeJoseph: vi.fn(),
}));

/*
  성경 자구는 `/api/scripture` 에서 온다. 실물 왕복만 막고, 응답은 **시드 SQL 을 그대로
  읽어** 돌려준다(`src/test/seed-passages.ts`) — 자구 사본을 테스트에 또 적으면 이 변경이
  없애려던 "두 벌" 이 세 벌이 된다.

  이 모킹이 없으면 `MonologueText` 가 전부 "본문 로드 중..." 으로 남는다. 그때 실패하는
  것은 모놀로그 단언이므로, 원인이 배선이 아니라 자구 공급이라는 게 바로 보이지 않는다.
*/
vi.mock("@/lib/api/content", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/lib/api/content")>()),
  fetchScripturePassage: vi.fn(),
}));

import { startJoseph, decideJoseph, completeJoseph } from "@/lib/api/game";

import { fetchScripturePassage } from "@/lib/api/content";
import { seedPassage } from "@/test/seed-passages";
// 위기 문구 픽스처는 정본에서 만든다. 번호를 여기에 적으면 정본이 바뀔 때
// 테스트만 옛 번호를 지키고 초록을 유지한다 — check_frontend_hotline.py 가 막는 것.
import { CRISIS_DEFAULT } from "@/lib/crisis-resources";
import JosephPage from "./page";

const SESSION = "sess-joseph";

function scene(
  currentScene: number,
  scenePayload: Record<string, unknown>,
  responseText: string | null = null,
) {
  return {
    sessionId: SESSION,
    userId: "guest-1",
    currentScene,
    scenePayload,
    responseText,
  };
}

// 아래 payload 들은 backend/src/main/resources/scenarios/joseph.yml 의 형태를 따른다.
const SCENE1 = scene(1, { title: "파라오의 꿈", type: "cinematic" });

const SCENE2 = scene(2, {
  title: "저장 비율 결정",
  type: "interaction",
  interaction: "pick_one",
  options: [
    { id: "save_20", label: "20% 저장" },
    { id: "save_33", label: "33% 저장" },
    { id: "save_50", label: "50% 저장" },
  ],
});

const SCENE3 = scene(3, {
  title: "흉년 — 누구에게 먼저",
  type: "interaction",
  interaction: "distribute",
  queues: [
    { id: "farmer", label: "농민 줄" },
    { id: "immigrant", label: "이주민 줄" },
    { id: "merchant", label: "상인 줄" },
  ],
});

const SCENE4_WARNING = {
  level: "medium",
  content: ["betrayal", "family_trauma"],
  consent_card_id: "joseph_scene4_reunion_warning",
  skip_alternative_scene_id: 5,
};

const SCENE4 = scene(4, {
  title: "형제들이 왔다",
  type: "interaction",
  interaction: "pick_one",
  options: [
    { id: "reveal", label: "정체를 밝힌다" },
    { id: "test", label: "시험한다" },
    { id: "silent", label: "침묵한다" },
  ],
  trigger_warning: SCENE4_WARNING,
});

const SCENE5 = scene(5, { title: "결말", type: "outro" });

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <JosephPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.mocked(fetchScripturePassage).mockImplementation(seedPassage);
  vi.mocked(startJoseph).mockReset();
  vi.mocked(decideJoseph).mockReset();
  vi.mocked(completeJoseph).mockReset();
  vi.mocked(completeJoseph).mockResolvedValue(undefined);
});

describe("요셉 미션 — 씬 상태 기계", () => {
  it("Scene 1 은 캡션만이 아니라 꿈 대본 본문까지 렌더한다", async () => {
    // 캡션(배경 위 한 줄)과 대본(scene1Dream)은 다른 것이다. 대본이 빠지면
    // 화면은 멀쩡해 보이는데 사용자는 읽을 게 없다.
    vi.mocked(startJoseph).mockResolvedValue(SCENE1);
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "파라오의 꿈" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "파라오의 꿈을 해석한다. 7년 풍년과 7년 흉년이 다가온다.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/일곱 좋은 암소는 일곱 해요/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "계속 →" })).toBeEnabled();
  });

  it("Scene 1 → 5 → 미션 완료까지 끝까지 걸어간다", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(SCENE1);
    vi.mocked(decideJoseph)
      .mockResolvedValueOnce(SCENE2)
      .mockResolvedValueOnce(SCENE3)
      .mockResolvedValueOnce(SCENE4)
      .mockResolvedValueOnce(SCENE5);
    renderPage();

    // Scene 1 cinematic → 2
    await user.click(await screen.findByRole("button", { name: "계속 →" }));
    expect(decideJoseph).toHaveBeenNthCalledWith(1, SESSION, 1, "next");

    // Scene 2 pick_one → 고른 비율의 모놀로그가 echo 로 뜬다
    await user.click(await screen.findByRole("button", { name: "33% 저장" }));
    expect(decideJoseph).toHaveBeenNthCalledWith(2, SESSION, 2, "save_33");
    expect(
      await screen.findByText(/요셉이 실제로 따른 비율/),
    ).toBeInTheDocument();

    // Scene 3 distribute → 선택한 줄의 아웃컴이 echo 로 뜬다
    await user.click(await screen.findByRole("button", { name: "이주민 줄" }));
    expect(decideJoseph).toHaveBeenNthCalledWith(3, SESSION, 3, {
      priority: "immigrant",
    });
    expect(
      await screen.findByText(/야곱의 가문이 너를 통해 살았다/),
    ).toBeInTheDocument();

    // Scene 4 는 trigger_warning 이 붙은 씬 — 동의 전에는 선택지가 없어야 한다
    expect(
      await screen.findByText("잠깐 — 다음 장면 안내"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "정체를 밝힌다" }),
    ).not.toBeInTheDocument();

    await user.click(
      screen.getByRole("button", { name: "준비됐어요 · 들어갈게요" }),
    );
    await user.click(
      await screen.findByRole("button", { name: "정체를 밝힌다" }),
    );
    expect(decideJoseph).toHaveBeenNthCalledWith(4, SESSION, 4, "reveal");
    expect(await screen.findByText(/나는 요셉이다/)).toBeInTheDocument();

    // Scene 5 outro — Scene 3 에서 이주민을 골랐으므로 결말 톤도 그 갈래여야 한다
    expect(
      await screen.findByText(/너를 판 형제가 너의 양식으로 살았다/),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "미션 완료" }));
    expect(completeJoseph).toHaveBeenCalledWith(SESSION, "completed");
  });

  it("진행 기록에 지나온 씬 제목이 쌓인다", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(SCENE1);
    vi.mocked(decideJoseph).mockResolvedValueOnce(SCENE2);
    renderPage();

    // 시작 직후에는 지나온 씬이 없으므로 기록 자체가 뜨지 않는다.
    expect(await screen.findByRole("button", { name: "계속 →" })).toBeEnabled();
    expect(screen.queryByText("진행 기록")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "계속 →" }));
    const summary = await screen.findByText("진행 기록");
    expect(summary.parentElement).toHaveTextContent("저장 비율 결정");
  });

  it("Scene 5 로 바로 들어오면 결말 톤은 기본값(농민)으로 떨어진다", async () => {
    // Scene 3 을 지나지 않아 패턴이 없는 경우. 빈 화면 대신 fallback 결말이 나와야 한다.
    vi.mocked(startJoseph).mockResolvedValue(SCENE5);
    renderPage();

    expect(await screen.findByText(/이집트는 살았다/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "미션 완료" })).toBeEnabled();
  });
});

describe("요셉 미션 — outro 위기 안내 (joseph.yml crisis_reminder)", () => {
  /*
    joseph.yml Scene 5 는 `extras.crisis_reminder` 를 선언하고, 백엔드
    `CrisisTokenResolver` 가 `{{crisis_resources.default}}` 를 DB 정본 번호로
    치환해서 내려보낸다. 그런데 2026-08-14 까지 이 화면은 그 값을 **읽지도
    않았다** — 치환까지 끝난 안내 문구가 payload 안에서 그대로 버려졌고,
    배신·가족 트라우마를 다룬 미션의 결말에 위기 자원이 한 줄도 안 떴다.

    CI 가 못 잡은 이유가 이 테스트의 존재 이유다. `check_frontend_hotline.py` 는
    "번호를 하드코딩했나" 를 보지 "번호가 뜨나" 는 보지 않는다 — 없는 것은
    하드코딩이 아니므로 초록이다. 화면에 뜨는지는 오직 테스트만 잰다.
  */
  it("outro 는 payload 의 위기 안내를 그대로 낸다", async () => {
    // 문구는 백엔드가 만든다. 화면이 문안을 지어내면 안전 검토가 무의미해지므로
    // 픽스처 문자열이 **그대로** 나오는지를 본다.
    const reminder = `지금 이 순간이 무겁다면, ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}.`;
    vi.mocked(startJoseph).mockResolvedValue(
      scene(5, {
        title: "결말",
        type: "outro",
        extras: { crisis_reminder: reminder },
      }),
    );
    renderPage();

    expect(await screen.findByRole("note")).toHaveTextContent(reminder);
  });

  it("최상위로 펼쳐져 내려와도 읽는다", async () => {
    /*
      ScenePayloadAssembler 는 표준 필드는 payload 최상위로 펼치고 `extras:` 블록만
      중첩해 둔다. yml 이 어느 쪽에 쓰였느냐로 안내가 사라지면 안 되므로 두 경로를
      다 받는다 — 다른 화면(elijah·job·solomon)의 `extras[key] ?? payload[key]` 와
      같은 규칙이다.
    */
    const reminder = `혼자 두지 않겠습니다. ${CRISIS_DEFAULT.label} ${CRISIS_DEFAULT.tel}.`;
    vi.mocked(startJoseph).mockResolvedValue(
      scene(5, { title: "결말", type: "outro", crisis_reminder: reminder }),
    );
    renderPage();

    expect(await screen.findByRole("note")).toHaveTextContent(reminder);
  });

  it("안내가 없는 씬은 빈 상자를 만들지 않는다", async () => {
    vi.mocked(startJoseph).mockResolvedValue(SCENE5);
    renderPage();

    await screen.findByText(/이집트는 살았다/);
    expect(screen.queryByRole("note")).toBeNull();
  });
});

describe("요셉 미션 — R4 동의 게이트 (joseph.yml Scene 4)", () => {
  it("동의 전에는 경고 카드가 뜨고 선택지는 가려진다", async () => {
    vi.mocked(startJoseph).mockResolvedValue(SCENE4);
    renderPage();

    // 카드 자체 — 2026-08-12 까지 이 화면에 없던 바로 그것.
    expect(
      await screen.findByText("잠깐 — 다음 장면 안내"),
    ).toBeInTheDocument();
    expect(screen.getByText(/가족의 배신과 재회/)).toBeInTheDocument();

    // yml 의 content 태그가 화면 라벨로 나와야 한다 (조용히 버려지면 경고 없는 것과 같다).
    expect(screen.getByText("배신")).toBeInTheDocument();
    expect(screen.getByText("가족 관계의 상처")).toBeInTheDocument();
    // level·skip 목적지도 payload 에서 온다 — 화면에 박아 둔 값이 아니어야 한다.
    expect(screen.getByText(/정서 강도: 중간/)).toBeInTheDocument();
    expect(
      screen.getByText(/건너뛰면 Scene 5 으로 이어집니다/),
    ).toBeInTheDocument();

    // 본문(선택지)은 동의 전에 렌더되면 안 된다.
    for (const label of ["정체를 밝힌다", "시험한다", "침묵한다"]) {
      expect(screen.queryByRole("button", { name: label })).toBeNull();
    }
  });

  it("동의하면 카드가 사라지고 선택지가 열린다", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(SCENE4);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "준비됐어요 · 들어갈게요" }),
    );

    expect(
      await screen.findByRole("button", { name: "정체를 밝힌다" }),
    ).toBeEnabled();
    expect(screen.queryByText("잠깐 — 다음 장면 안내")).toBeNull();
  });

  it("건너뛰기는 skip 결정을 백엔드로 보낸다 — 이야기를 잃지 않는 문", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(SCENE4);
    vi.mocked(decideJoseph).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "이 장면은 건너뛸게요 →" }),
    );

    expect(decideJoseph).toHaveBeenCalledWith(SESSION, 4, { value: "skip" });
    // 건너뛰어도 서사는 이어진다 — 결말 씬이 실제로 렌더돼야 한다.
    expect(
      await screen.findByRole("button", { name: "미션 완료" }),
    ).toBeEnabled();
  });

  it("yml 이 정본 문구(consent_card_ko)를 주면 화면 문구 대신 그것을 쓴다", async () => {
    // 정본이 있는데 화면 문구가 이기면, 안전 검토자가 yml 을 고쳐도 화면은 그대로다.
    vi.mocked(startJoseph).mockResolvedValue(
      scene(4, {
        ...SCENE4.scenePayload,
        trigger_warning: {
          ...SCENE4_WARNING,
          consent_card_ko:
            "이 장면은 형제의 배신을 다룹니다.\n[계속한다] [건너뛰기 →]",
        },
      }),
    );
    renderPage();

    expect(
      await screen.findByText("이 장면은 형제의 배신을 다룹니다."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/가족의 배신과 재회/)).toBeNull();
  });

  it("경고가 없는 씬은 게이트 없이 바로 본문을 연다", async () => {
    // 게이트가 씬 번호가 아니라 payload 로 결정되는지 — 4번이라도 경고가 없으면 열려야 한다.
    vi.mocked(startJoseph).mockResolvedValue(
      scene(4, {
        title: "형제들이 왔다",
        type: "interaction",
        interaction: "pick_one",
        options: [{ id: "reveal", label: "정체를 밝힌다" }],
      }),
    );
    renderPage();

    expect(
      await screen.findByRole("button", { name: "정체를 밝힌다" }),
    ).toBeEnabled();
    expect(screen.queryByText("잠깐 — 다음 장면 안내")).toBeNull();
  });
});

describe("요셉 미션 — 응답 텍스트의 출처", () => {
  it("백엔드 responseText 가 있으면 프론트 하드코딩 문구보다 우선한다", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(SCENE2);
    vi.mocked(decideJoseph).mockResolvedValue(
      scene(3, SCENE3.scenePayload, "백엔드가 고른 문구입니다."),
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "33% 저장" }));

    expect(
      await screen.findByText("백엔드가 고른 문구입니다."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/요셉이 실제로 따른 비율/)).toBeNull();
  });

  it("매칭되는 문구가 어느 쪽에도 없으면 빈 인용 상자를 띄우지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(
      scene(2, {
        title: "저장 비율 결정",
        type: "interaction",
        interaction: "pick_one",
        options: [{ id: "save_99", label: "99% 저장" }],
      }),
    );
    vi.mocked(decideJoseph).mockResolvedValue(SCENE3);
    renderPage();

    await user.click(await screen.findByRole("button", { name: "99% 저장" }));

    expect(
      await screen.findByRole("button", { name: "농민 줄" }),
    ).toBeEnabled();
    // echo 상자에만 붙는 AI 보조 고지. 상자가 없으면 이 문구도 없어야 한다.
    expect(screen.queryByText(/AI 보조/)).toBeNull();
  });

  it("알 수 없는 분배 줄은 결말 톤을 오염시키지 않는다", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(
      scene(3, {
        title: "흉년",
        type: "interaction",
        interaction: "distribute",
        queues: [{ id: "unknown", label: "알 수 없는 줄" }],
      }),
    );
    vi.mocked(decideJoseph).mockResolvedValue(SCENE5);
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: "알 수 없는 줄" }),
    );

    // 패턴 매칭 실패 → 기본 결말(농민)로 떨어지고, 화면은 비지 않는다.
    expect(await screen.findByText(/이집트는 살았다/)).toBeInTheDocument();
  });
});

describe("요셉 미션 — 백엔드가 실패했을 때 사용자가 보는 것", () => {
  it("세션 시작 실패는 먹통 대신 재시도 손잡이를 준다", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph)
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce(SCENE1);
    renderPage();

    expect(
      await screen.findByText("세션을 시작하지 못했습니다."),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(
      await screen.findByRole("heading", { name: "파라오의 꿈" }),
    ).toBeInTheDocument();
  });

  it("401 은 만료 안내와 상태 코드를 보여준다", async () => {
    vi.mocked(startJoseph).mockRejectedValue({ response: { status: 401 } });
    renderPage();

    expect(
      await screen.findByText("세션이 만료됐습니다. 다시 시작해 주세요."),
    ).toBeInTheDocument();
    expect(screen.getByText(/오류 코드 401/)).toBeInTheDocument();
  });

  it("결정 전송이 실패하면 오류를 알리고 선택지를 되돌려 준다", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(SCENE2);
    vi.mocked(decideJoseph).mockRejectedValue(
      new Error("서버가 응답하지 않습니다"),
    );
    renderPage();

    await user.click(await screen.findByRole("button", { name: "33% 저장" }));

    expect(
      await screen.findByText("오류: 서버가 응답하지 않습니다"),
    ).toBeInTheDocument();
    // 사용자가 다시 시도할 수 있어야 한다 — 선택지가 사라지면 그대로 갇힌다.
    expect(screen.getByRole("button", { name: "33% 저장" })).toBeEnabled();
  });

  it("전송 중에는 버튼이 잠긴다 — 중복 결정 방지", async () => {
    const user = userEvent.setup();
    vi.mocked(startJoseph).mockResolvedValue(SCENE1);
    vi.mocked(decideJoseph).mockReturnValue(new Promise(() => {}));
    renderPage();

    await user.click(await screen.findByRole("button", { name: "계속 →" }));

    const pending = await screen.findByRole("button", { name: "..." });
    expect(pending).toBeDisabled();
  });
});
