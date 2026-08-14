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

  it("yml 이 `trigger_warning:` 를 빈 값으로 두면 null — 호출자의 !!warning 에서 게이트가 사라진다", () => {
    // YAML 에서 키만 적고 값을 비우면 null 이 온다. 저작자는 "경고를 선언했다"고
    // 믿지만 화면에는 아무것도 안 뜬다. 판정기는 코드가 필드를 *읽는지*만 보므로
    // 이 경로도 통과시킨다 — 조용히 열린 문이다.
    const warning = readTriggerWarning({ trigger_warning: null });
    expect(warning).toBeNull();
    expect(!!warning).toBe(false);
  });

  it("레거시 boolean(true) 도 걸러내지 않는다 — 정보 0 인 카드가 뜬다", () => {
    // david.yml 이 한때 쓰던 `extras.violence_warning` 같은 레거시 boolean 이
    // trigger_warning 자리에 들어오면, readTriggerWarning 은 캐스팅만 하므로
    // 그대로 통과하고 호출자의 !!warning 은 참이 된다.
    const warning = readTriggerWarning({ trigger_warning: true });
    expect(!!warning).toBe(true);

    render(
      <TriggerWarningGate
        warning={warning as unknown as TriggerWarning}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );

    // 카드는 뜨지만 레벨·트리거 종류·건너뛰기 목적지가 전부 비어 있다.
    // "경고했다"는 흔적만 남고 무엇을 경고하는지는 사용자에게 전달되지 않는다.
    expect(screen.getByText("잠깐 — 다음 장면 안내")).toBeInTheDocument();
    expect(screen.queryByText(/정서 강도/)).not.toBeInTheDocument();
    expect(screen.queryByText(/건너뛰면 Scene/)).not.toBeInTheDocument();
  });

  it("문자열 레벨('high') 도 그대로 통과한다 — 마찬가지로 빈 카드", () => {
    const warning = readTriggerWarning({ trigger_warning: "high" });
    render(
      <TriggerWarningGate
        warning={warning as unknown as TriggerWarning}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(screen.getByText("잠깐 — 다음 장면 안내")).toBeInTheDocument();
    expect(screen.queryByText(/정서 강도/)).not.toBeInTheDocument();
  });

  it("content 를 배열이 아닌 문자열로 적으면 렌더가 통째로 죽는다", () => {
    // `(warning.content ?? []).map` — 문자열엔 map 이 없다. yml 에
    // `content: violence` 처럼 대괄호를 빠뜨리면 경고 카드가 아니라 씬 전체가
    // 터진다. 시끄럽게 실패한다는 점은 다행이지만, 방어는 없다.
    const 에러무시 = vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() =>
      render(
        <TriggerWarningGate
          warning={{ content: "violence" as unknown as string[] }}
          fallbackProse={기본산문}
          pending={false}
          onContinue={vi.fn()}
          onSkip={vi.fn()}
        />,
      ),
    ).toThrow();
    에러무시.mockRestore();
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
        warning={{ level: "high", content: ["self_harm", "death"] }}
        fallbackProse={기본산문}
        pending={false}
        onContinue={vi.fn()}
        onSkip={vi.fn()}
      />,
    );
    expect(screen.getByText("self_harm")).toBeInTheDocument();
    expect(screen.getByText("죽음")).toBeInTheDocument();
    expect(screen.getByText("정서 강도: 높음")).toBeInTheDocument();
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
    expect(screen.getByText("정서 강도: extreme")).toBeInTheDocument();
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

  it("[대괄호]로 시작하는 안전 문구는 UI 지시줄이 아니어도 통째로 사라진다", () => {
    // 버그 문서화: 필터가 '[' 로 시작하는 *모든* 줄을 버린다. 저작자가
    // "[주의] 자해 묘사가 있습니다" 처럼 대괄호 머리말을 쓰면 그 경고가
    // 조용히 증발한다 — 화면은 멀쩡해 보이고 어떤 게이트도 빨개지지 않는다.
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
    expect(screen.getByText(/잠시 숨을 고르세요/)).toBeInTheDocument();
    expect(screen.queryByText(/자해에 대한 묘사/)).not.toBeInTheDocument();
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
