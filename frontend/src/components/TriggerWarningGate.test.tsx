import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import {
  TriggerWarningGate,
  readTriggerWarning,
  type TriggerWarning,
} from "./TriggerWarningGate";

/**
 * R4 동의 게이트의 유닛 테스트.
 *
 * CI 의 `scripts/check_frontend_trigger_warning.py` 는 자기 docstring 에 스스로
 * 적어 놨듯 **"카드가 실제로 렌더되는지"는 재지 않는다** — 화면이 payload 를
 * *읽기만* 하고 아무것도 그리지 않아도 그 판정기는 초록이다.
 *
 * 여기서 재는 것이 정확히 그 구멍이다:
 *   ① trigger_warning 이 있으면 경고 카드가 **실제 DOM 에 나오는가**
 *   ② 동의 전에 본문(선택지)이 **가려지는가**
 *   ③ 건너뛰기 손잡이가 **실제로 동작하는가**
 *   ④ payload 모양이 규약과 다를 때 readTriggerWarning 이 어떻게 되는가
 *
 * 이 파일의 실패는 "화면이 깨졌다"가 아니라 "정서 부담이 큰 장면에 경고 없이
 * 들어간다"는 실패다. 그래서 렌더 여부를 문자 그대로 단언한다.
 */

const 기본산문 = <p>다음 장면은 가족의 배신과 재회를 다룹니다.</p>;

/** 요셉 Scene 4 (joseph.yml) 의 실제 선언 모양. */
const JOSEPH_SCENE4: TriggerWarning = {
  level: "medium",
  content: ["betrayal", "family_trauma"],
  consent_card_id: "joseph_scene4_reunion_warning",
  skip_alternative_scene_id: 5,
};

describe("readTriggerWarning — payload 모양별 처리", () => {
  it("선언이 없으면 undefined — 게이트를 띄우지 않는다", () => {
    // 경고가 없는 씬까지 카드를 띄우면 사용자는 카드를 습관적으로 넘기게 되고,
    // 그때부터 진짜 경고도 안 읽힌다. 없으면 없어야 한다.
    expect(
      readTriggerWarning({ title: "Scene 1", type: "cinematic" }),
    ).toBeUndefined();
  });

  it("구조화된 선언은 그대로 통과한다", () => {
    const payload = { trigger_warning: JOSEPH_SCENE4 };
    expect(readTriggerWarning(payload)).toEqual(JOSEPH_SCENE4);
  });

  it("명시적으로 끈 선언(false)만 게이트를 닫는다", () => {
    expect(readTriggerWarning({ trigger_warning: false })).toBeUndefined();
  });

  it("빈 선언(null)은 게이트를 연다 — 선언한 이상 닫는 쪽으로 넘어가지 않는다", () => {
    // 주의: 이 경로는 *현재 프로덕션에서는 도달하지 않는다.* yml 에 키만 적고
    // 값을 비우면 SnakeYAML 이 null 을 싣지만, application.yml 의
    // `spring.jackson.default-property-inclusion: non_null` 이 그 키를 JSON 에서
    // 통째로 지워서 프론트에는 "선언 없음"과 똑같이 도착한다. 그래서 그 사고는
    // yml 을 직접 읽는 scripts/check_frontend_trigger_warning.py 가 잡는다.
    //
    // 그럼에도 여기서 undefined 와 뭉뚱그리지 않는 이유는, 직렬화 설정이 바뀌어
    // null 이 도착하게 되는 날 문이 조용히 *열리는* 쪽이 아니라 닫히는 쪽이어야
    // 하기 때문이다. 이 단언이 그 방향을 고정한다.
    expect(readTriggerWarning({ trigger_warning: null })).toEqual({});
  });

  it("레거시 boolean(true) 은 빈 경고로 정규화되고, 카드는 문과 안내를 갖춘 채 뜬다", () => {
    // david.yml 이 한때 쓰던 `extras.violence_warning` 같은 레거시 boolean 이
    // trigger_warning 자리에 들어오는 경우. 예전에는 캐스팅만 해서 통과시켰고,
    // 카드에는 레벨·트리거 종류·건너뛰기 목적지가 전부 빈 채로 떴다 —
    // "경고했다"는 흔적만 남고 무엇을 경고하는지는 전달되지 않았다.
    const warning = readTriggerWarning({ trigger_warning: true });
    expect(warning).toEqual({});

    render(
      <TriggerWarningGate
        warning={warning!}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );

    // 메타데이터는 없지만 *동의를 구하는 문* 으로서는 온전하다 —
    // 화면이 소유한 산문 + 두 손잡이 + 건너뛰어도 된다는 사실.
    expect(screen.getByText("잠깐 — 다음 장면 안내")).toBeInTheDocument();
    expect(
      screen.getByText("다음 장면은 가족의 배신과 재회를 다룹니다."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "이 장면은 건너뛸게요 →" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("건너뛰어도 이야기는 이어집니다"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/정서 강도/)).not.toBeInTheDocument();
  });

  it("스칼라 문자열은 동의 카드 본문으로 보여 준다 — 적힌 글자를 삼키지 않는다", () => {
    // 저작자가 무엇을 의도했는지(레벨? 태그? 문구?) 알 수 없다. 넘겨짚는 대신
    // 사용자에게 그대로 전달한다. 화면에 뜨면 잘못 적은 것도 눈에 띈다.
    const warning = readTriggerWarning({
      trigger_warning: "이 장면에는 폭력 묘사가 있습니다.",
    });
    expect(warning).toEqual({
      consent_card_ko: "이 장면에는 폭력 묘사가 있습니다.",
    });

    render(
      <TriggerWarningGate
        warning={warning!}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(
      screen.getByText("이 장면에는 폭력 묘사가 있습니다."),
    ).toBeInTheDocument();
  });

  it("content 를 배열이 아닌 문자열로 적어도 태그 하나로 살아난다", () => {
    // 예전에는 `(warning.content ?? []).map` 에서 문자열에 map 이 없어
    // **씬 전체가 터졌다.** yml 에 `content: violence` 처럼 대괄호를 빠뜨린
    // 대가가 화면 붕괴라면, 저작자는 경고를 안 붙이는 쪽으로 학습한다.
    const warning = readTriggerWarning({
      trigger_warning: { content: "violence" },
    });
    expect(warning?.content).toEqual(["violence"]);

    render(
      <TriggerWarningGate
        warning={warning!}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(screen.getByText("폭력")).toBeInTheDocument();
  });

  it("skip 목적지를 문자열로 적어도 숫자로 읽는다", () => {
    const warning = readTriggerWarning({
      trigger_warning: { skip_alternative_scene_id: "5" },
    });
    expect(warning?.skip_alternative_scene_id).toBe(5);
  });

  it("모양을 알 수 없는 값도 게이트는 연다", () => {
    // 배열·숫자 같은 엉뚱한 모양. 무엇을 경고하는지는 못 읽어도 "경고할 씬"
    // 이라는 선언은 읽었다. 읽지 못한 것을 없는 것으로 취급하지 않는다.
    expect(readTriggerWarning({ trigger_warning: 3 })).toEqual({});
    expect(readTriggerWarning({ trigger_warning: ["violence"] })).toEqual({});
  });
});

describe("경고 카드가 실제로 렌더된다", () => {
  it("level·content·skip 목적지가 사용자가 읽을 수 있는 한국어로 나온다", () => {
    render(
      <TriggerWarningGate
        warning={JOSEPH_SCENE4}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );

    expect(screen.getByText("잠깐 — 다음 장면 안내")).toBeInTheDocument();
    expect(
      screen.getByText("다음 장면은 가족의 배신과 재회를 다룹니다."),
    ).toBeInTheDocument();
    // content 태그가 한국어 라벨로 바뀌어 실제로 보인다.
    expect(screen.getByText("배신")).toBeInTheDocument();
    expect(screen.getByText("가족 관계의 상처")).toBeInTheDocument();
    // 강도와 건너뛰기 목적지도 같은 줄에 나온다.
    expect(
      screen.getByText("정서 강도: 중간 · 건너뛰면 Scene 5 으로 이어집니다"),
    ).toBeInTheDocument();
  });

  it("라벨 사전에 없는 트리거는 영문 토큰 그대로 노출된다 — 조용히 사라지지 않는다", () => {
    // 여기서 토큰을 버리면 "경고가 없는 것"과 화면상 구별되지 않는다.
    // 보기 나쁜 쪽이 안전한 쪽이다.
    render(
      <TriggerWarningGate
        warning={{ level: "high", content: ["public_humiliation", "death"] }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(screen.getByText("public_humiliation")).toBeInTheDocument();
    expect(screen.getByText("죽음")).toBeInTheDocument();
    expect(screen.getByText(/정서 강도: 높음/)).toBeInTheDocument();
  });

  it("배포된 yml 이 쓰는 태그·강도는 전부 한국어 라벨을 갖는다", () => {
    /*
      solomon.yml Scene 3·4 의 실제 선언. 이 태그들은 solomon 화면이 로컬
      TriggerWarning 타입에 `content` 를 두지 않아 **한 번도 뜬 적이 없었고**,
      뜨게 만들고 보니 사전에도 없어 영문 토큰이 그대로 나왔다.
      원문 노출은 *모르는* 태그를 위한 안전망이지 배포된 태그의 자리가 아니다.
    */
    render(
      <TriggerWarningGate
        warning={{
          level: "low_medium",
          content: [
            "infant_loss",
            "bereavement",
            "child_endangerment",
            "emptiness",
            "existential_despair",
          ],
        }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    for (const label of [
      "영아 상실",
      "사별",
      "아이가 위험에 놓임",
      "공허",
      "삶의 의미를 잃은 절망",
    ]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText(/정서 강도: 낮음~중간/)).toBeInTheDocument();
  });

  it("사전에 없는 level 도 원문 그대로 뜬다", () => {
    render(
      <TriggerWarningGate
        warning={{ level: "extreme" }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(screen.getByText(/정서 강도: extreme/)).toBeInTheDocument();
  });

  it("skip 목적지를 모를 때도 건너뛰어도 된다는 사실은 말해 준다", () => {
    // 예전에는 skip_alternative_scene_id 가 없으면 이 줄이 통째로 비었다.
    // 건너뛰면 이야기를 잃는 건지 아닌지 모르는 채로 고르게 하는 게 문제다.
    render(
      <TriggerWarningGate
        warning={{ level: "medium" }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(
      screen.getByText("정서 강도: 중간 · 건너뛰어도 이야기는 이어집니다"),
    ).toBeInTheDocument();
  });

  it("level 이 없고 skip 목적지만 있으면 목적지만 나온다", () => {
    render(
      <TriggerWarningGate
        warning={{ skip_alternative_scene_id: 7 }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(
      screen.getByText("건너뛰면 Scene 7 으로 이어집니다"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/정서 강도/)).not.toBeInTheDocument();
  });

  it("ruth.yml 이 쓰는 강도 표기(mid · low_mid)도 한국어 라벨을 갖는다", () => {
    /*
      룻은 `mid` · `low_mid` 를 쓴다 — solomon 의 `medium` · `low_medium` 과 같은 뜻의
      다른 표기다. 사전에 없으면 원문 노출 안전망을 타고 「정서 강도: low_mid」 라는
      영문 토큰이 사용자에게 그대로 간다. 안전망은 *모르는* 값을 위한 자리지
      배포된 값의 자리가 아니다.
    */
    const { unmount } = render(
      <TriggerWarningGate
        warning={{ level: "mid" }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(screen.getByText(/정서 강도: 중간/)).toBeInTheDocument();
    unmount();

    render(
      <TriggerWarningGate
        warning={{ level: "low_mid" }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(screen.getByText(/정서 강도: 낮음~중간/)).toBeInTheDocument();
  });

  it("화면 고유 컨트롤(children)은 산문과 버튼 사이에 들어간다", () => {
    // jesus 화면의 '음성/자막 강도 조절' 자리. 버튼보다 앞에 있어야
    // 사용자가 강도를 낮춘 뒤 계속을 누를 수 있다.
    render(
      <TriggerWarningGate
        warning={JOSEPH_SCENE4}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      >
        <button type="button">강도 낮추기</button>
      </TriggerWarningGate>,
    );
    const 강도 = screen.getByRole("button", { name: "강도 낮추기" });
    const 계속 = screen.getByRole("button", {
      name: "준비됐어요 · 들어갈게요",
    });
    expect(
      강도.compareDocumentPosition(계속) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});

describe("문구 소유권 — consent_card_ko 정본이 화면 문구를 이긴다", () => {
  it("정본이 있으면 fallbackProse 대신 정본이 렌더된다", () => {
    // 이 우선순위가 뒤집히면 안전 검토자가 yml 문구를 개정해도 화면은 그대로다.
    render(
      <TriggerWarningGate
        warning={{
          ...JOSEPH_SCENE4,
          consent_card_ko:
            "지금 이 장면은 버거울 수 있습니다.\n건너뛰어도 이야기는 이어집니다.",
        }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(
      screen.getByText(/지금 이 장면은 버거울 수 있습니다/),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("다음 장면은 가족의 배신과 재회를 다룹니다."),
    ).not.toBeInTheDocument();
  });

  it("정본 안의 UI 지시줄과 강도 안내줄은 산문에서 걷어내고 실제 버튼으로 대체한다", () => {
    render(
      <TriggerWarningGate
        warning={{
          consent_card_ko: [
            "이 장면에는 형제들과의 재회가 나옵니다.",
            "음성/자막 강도: 낮음 / 보통",
            "[계속한다] [건너뛰기 — Scene 5]",
          ].join("\n"),
        }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    const 산문 = screen.getByText(/이 장면에는 형제들과의 재회가 나옵니다/);
    expect(산문.textContent).not.toContain("[계속한다]");
    expect(산문.textContent).not.toContain("음성/자막 강도:");
    // 지시줄 대신 진짜 버튼이 있다.
    expect(
      screen.getByRole("button", { name: "준비됐어요 · 들어갈게요" }),
    ).toBeInTheDocument();
  });

  it("[주의] 같은 대괄호 머리말이 붙은 안전 문구는 살아남는다", () => {
    /*
      예전 필터는 '[' 로 시작하는 *모든* 줄을 버렸다. 그래서 저작자가
      "[주의] 자해 묘사가 있습니다" 처럼 대괄호 머리말을 쓰면 그 경고가 조용히
      증발했다 — 화면은 멀쩡해 보이고 어떤 게이트도 빨개지지 않는다. 대괄호는
      한국어 안전 카피의 흔한 머리말이라 특히 밟기 쉬웠다.

      이제 걷어내는 건 *줄 전체가 대괄호 토큰뿐인* UI 지시줄로 한정한다.
    */
    render(
      <TriggerWarningGate
        warning={{
          consent_card_ko: [
            "잠시 숨을 고르세요.",
            "[주의] 이 장면에는 자해에 대한 묘사가 있습니다.",
            "[계속한다] [건너뛰기]",
          ].join("\n"),
        }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    const 산문 = screen.getByText(/잠시 숨을 고르세요/);
    expect(산문).toHaveTextContent(
      "[주의] 이 장면에는 자해에 대한 묘사가 있습니다.",
    );
    // UI 지시줄은 여전히 버튼으로 대체된다.
    expect(산문.textContent).not.toContain("[계속한다]");
  });

  it("정본이 UI 지시줄뿐이면 남는 산문이 없어 fallbackProse 로 되돌아간다", () => {
    render(
      <TriggerWarningGate
        warning={{
          consent_card_ko: "[계속한다] [건너뛰기]\n음성/자막 강도: 보통",
        }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(
      screen.getByText("다음 장면은 가족의 배신과 재회를 다룹니다."),
    ).toBeInTheDocument();
  });
});

/**
 * 호출자(joseph/david/jesus page.tsx)가 게이트를 쓰는 방식을 그대로 재현한 하네스.
 * `needsConsent = !!warning && !consented` → 동의 전에는 본문을 렌더하지 않는다.
 * 이 계약이 깨지면 경고 카드가 떠 있어도 그 아래로 선택지가 그대로 보인다.
 */
function ConsentHarness({
  warning,
  onSkip,
  pending = false,
}: {
  warning: TriggerWarning | undefined;
  onSkip: () => void;
  pending?: boolean;
}) {
  const [consented, setConsented] = useState(false);
  const needsConsent = !!warning && !consented;

  return needsConsent && warning ? (
    <TriggerWarningGate
      warning={warning}
      fallbackProse={기본산문}
      pending={pending}
      onContinue={() => setConsented(true)}
      onSkip={onSkip}
    />
  ) : (
    <button type="button">형제들을 용서한다</button>
  );
}

describe("동의 전에는 본문이 가려진다", () => {
  it("경고가 선언된 씬은 카드만 보이고 선택지는 렌더되지 않는다", async () => {
    render(<ConsentHarness warning={JOSEPH_SCENE4} onSkip={vi.fn()} />);

    expect(screen.getByText("잠깐 — 다음 장면 안내")).toBeInTheDocument();
    // 핵심 단언 — 동의 전에 씬 선택지가 DOM 에 없다.
    expect(
      screen.queryByRole("button", { name: "형제들을 용서한다" }),
    ).not.toBeInTheDocument();
  });

  it("경고가 없는 씬은 카드 없이 바로 본문이 나온다", () => {
    render(<ConsentHarness warning={undefined} onSkip={vi.fn()} />);
    expect(
      screen.getByRole("button", { name: "형제들을 용서한다" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("잠깐 — 다음 장면 안내")).not.toBeInTheDocument();
  });

  it("계속하기를 누른 뒤에야 본문이 열린다", async () => {
    const user = userEvent.setup();
    render(<ConsentHarness warning={JOSEPH_SCENE4} onSkip={vi.fn()} />);

    await user.click(
      screen.getByRole("button", { name: "준비됐어요 · 들어갈게요" }),
    );

    expect(
      screen.getByRole("button", { name: "형제들을 용서한다" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("잠깐 — 다음 장면 안내")).not.toBeInTheDocument();
  });

  it("건너뛰기는 onSkip 을 부르고 본문을 열지 않는다 — 대체 씬으로 나가는 길이다", async () => {
    const user = userEvent.setup();
    const onSkip = vi.fn();
    render(<ConsentHarness warning={JOSEPH_SCENE4} onSkip={onSkip} />);

    await user.click(
      screen.getByRole("button", { name: "이 장면은 건너뛸게요 →" }),
    );

    expect(onSkip).toHaveBeenCalledTimes(1);
    // 건너뛰기는 '동의'가 아니다. 이 씬의 선택지가 열리면 안 된다.
    expect(
      screen.queryByRole("button", { name: "형제들을 용서한다" }),
    ).not.toBeInTheDocument();
  });

  it("화면이 라벨을 갈아끼우면 그 라벨로 렌더된다", () => {
    render(
      <TriggerWarningGate
        warning={JOSEPH_SCENE4}
        fallbackProse={기본산문}
        continueLabel="괜찮아요 · 계속할게요"
        skipLabel="이번엔 넘어갈게요"
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(
      screen.getByRole("button", { name: "괜찮아요 · 계속할게요" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "이번엔 넘어갈게요" }),
    ).toBeInTheDocument();
  });
});

describe("pending 중 버튼 잠금", () => {
  it("전송 중에는 계속·건너뛰기 둘 다 눌리지 않는다", async () => {
    const user = userEvent.setup();
    const onContinue = vi.fn();
    const onSkip = vi.fn();
    render(
      <TriggerWarningGate
        warning={JOSEPH_SCENE4}
        fallbackProse={기본산문}
        pending
        onContinue={onContinue}
        onSkip={onSkip}
      />,
    );

    const 계속 = screen.getByRole("button", {
      name: "준비됐어요 · 들어갈게요",
    });
    const 건너뛰기 = screen.getByRole("button", {
      name: "이 장면은 건너뛸게요 →",
    });
    expect(계속).toBeDisabled();
    // 주의 — 건너뛰기까지 같이 잠긴다. 결정 요청이 느리거나 매달리면
    // 부담을 느낀 사용자가 빠져나갈 손잡이도 그동안 못 쓴다.
    expect(건너뛰기).toBeDisabled();

    await user.click(계속);
    await user.click(건너뛰기);
    expect(onContinue).not.toHaveBeenCalled();
    expect(onSkip).not.toHaveBeenCalled();
  });
});

/**
 * 룻 Scene 3 (ruth.yml) — 리포 전체에서 `declined_route` 를 선언하는 유일한 카드.
 *
 * `skip_alternative_scene_id: 4` 와 `declined_route: closing` 을 **둘 다** 선언하지만
 * 카드가 여는 문은 「여기서 마친다」 하나다. 앞의 값을 읽으면 종결로 보내는 버튼 밑에
 * "Scene 4 로 이어집니다" 라고 적히게 된다 — 버튼과 안내가 서로 다른 말을 하는 카드.
 */
const RUTH_SCENE3: TriggerWarning = {
  level: "low_mid",
  content: ["bereavement"],
  consent_card_id: "ruth_midpoint_consent",
  skip_alternative_scene_id: 4,
  declined_route: "closing",
};

describe("둘째 문 — 건너뛰기와 거절은 다른 문이다", () => {
  /*
    이 구별을 안 하면 아무것도 빨개지지 않는다. 카드는 뜨고, 버튼도 눌리고,
    `check_frontend_trigger_warning.py` 도 "payload 를 읽고 skip 을 보낸다" 며 초록이다.
    달라지는 건 **마치겠다고 고른 사람에게 다음 씬이 열린다** 는 것뿐이다.
    백엔드에서 고친 결함(스킵 목적지 ≠ next)과 정확히 같은 모양의 조용한 실패다.
  */
  it("declined_route 가 없으면 지금까지처럼 skip 을 알린다", async () => {
    const user = userEvent.setup();
    const onSkip = vi.fn();
    render(
      <TriggerWarningGate
        warning={JOSEPH_SCENE4}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={onSkip}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "이 장면은 건너뛸게요 →" }),
    );
    expect(onSkip).toHaveBeenCalledWith("skip");
  });

  it("declined_route 를 선언한 카드의 둘째 문은 decline 을 알린다", async () => {
    const user = userEvent.setup();
    const onSkip = vi.fn();
    render(
      <TriggerWarningGate
        warning={RUTH_SCENE3}
        fallbackProse={기본산문}
        skipLabel="여기서 마친다"
        pending={false}
        onContinue={vi.fn()}
        onSkip={onSkip}
      />,
    );

    await user.click(screen.getByRole("button", { name: "여기서 마친다" }));
    expect(onSkip).toHaveBeenCalledWith("decline");
  });

  it("거절 문의 안내는 '이어집니다' 가 아니라 '종결' 이라고 적는다", () => {
    render(
      <TriggerWarningGate
        warning={RUTH_SCENE3}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );

    expect(
      screen.getByText(/여기서 마치면 다음 장면 없이 종결 화면으로 갑니다/),
    ).toBeInTheDocument();
    // 같은 카드에 실려 온 skip 목적지를 읽으면 안 된다 — 이 문은 그리로 가지 않는다.
    expect(
      screen.queryByText(/Scene 4 으로 이어집니다/),
    ).not.toBeInTheDocument();
  });

  it("readTriggerWarning 이 두 새 키를 payload 에서 실어 온다", () => {
    /*
      읽지 않으면 `declined_route` 는 yml 안에서만 참이 되고, 다리 나레이션은
      건너뛴 사람에게 영영 안 뜬다 — 룻이라는 이름이 처음 나오는 대목을 통째로
      지나친 채 Scene 3 에 도착한다.
    */
    const w = readTriggerWarning({
      trigger_warning: {
        declined_route: "closing",
        skip_bridge_narration_ko: "남은 사람의 이름은 룻이었다.",
      },
    });
    expect(w?.declined_route).toBe("closing");
    expect(w?.skip_bridge_narration_ko).toBe("남은 사람의 이름은 룻이었다.");
  });

  it("공백뿐인 declined_route 는 거절 문으로 치지 않는다", async () => {
    // 빈 값에 종결 동작을 붙이면 목적지 없는 거절이 된다 — 사용자는 어디로도 못 간다.
    const user = userEvent.setup();
    const onSkip = vi.fn();
    render(
      <TriggerWarningGate
        warning={{ ...RUTH_SCENE3, declined_route: "   " }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={onSkip}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "이 장면은 건너뛸게요 →" }),
    );
    expect(onSkip).toHaveBeenCalledWith("skip");
    expect(
      screen.getByText(/건너뛰면 Scene 4 으로 이어집니다/),
    ).toBeInTheDocument();
  });
});
