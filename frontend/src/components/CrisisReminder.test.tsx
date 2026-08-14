import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { CrisisReminder } from "./CrisisReminder";

/**
 * 이 컴포넌트의 실패는 조용하다 — 화면은 멀쩡히 돌고 위기 상태의 사용자만 연결을
 * 못 받는다. 그래서 "렌더된다" 뿐 아니라 **안 렌더되는 조건**까지 못 박는다.
 */
describe("CrisisReminder", () => {
  it("백엔드가 준 문구를 그대로 낸다 — 화면이 문안을 고쳐 쓰지 않는다", () => {
    // 위기 카피는 안전 검토를 거친 문장이다. 자르거나 다듬으면 검토가 무의미해진다.
    const text = "지금 이 순간이 무겁다면, 어딘가로 연결해도 괜찮습니다.";
    render(<CrisisReminder text={text} />);

    expect(screen.getByRole("note")).toHaveTextContent(text);
  });

  it("문구가 없으면 빈 상자를 만들지 않는다", () => {
    // 테두리만 남은 노란 상자는 "안내가 있는 줄 알았는데 비어 있다" 는 인상을 준다.
    const { container } = render(<CrisisReminder text={undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("null 도 없음으로 본다", () => {
    const { container } = render(<CrisisReminder text={null} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("공백만 있는 문구도 없음으로 본다", () => {
    // yml 에서 값을 지우다 만 경우(`crisis_reminder: " "`) 가 실제로 생긴다.
    // 중괄호로 넘긴다 — JSX 어트리뷰트 문자열은 이스케이프를 해석하지 않아서
    // `text="\n"` 은 개행이 아니라 역슬래시+n 두 글자가 된다(공백이 아니다).
    const { container } = render(<CrisisReminder text={"   \n  "} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("보조 안내로 표시된다 — 본문 흐름과 섞이지 않는다", () => {
    // 스크린리더가 이야기 본문과 같이 읽으면 사용자는 이게 안내인지 서사인지
    // 구분하지 못한다.
    render(<CrisisReminder text="안내" />);
    expect(screen.getByRole("note")).toBeInTheDocument();
  });
});
