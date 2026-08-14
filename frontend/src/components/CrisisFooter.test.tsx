import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { CrisisFooter } from "./CrisisFooter";
import {
  CRISIS_DEFAULT,
  CRISIS_LIFELINE,
  CRISIS_RESOURCES,
} from "@/lib/crisis-resources";

/**
 * 위기 상담 footer 의 유닛 테스트.
 *
 * CI 의 `scripts/check_frontend_hotline.py` 는 **번호 리터럴의 위치**(정본 모듈
 * 바깥에 숫자가 없는가)와 백엔드 대체값과의 일치만 본다. 화면에 그 번호가
 * 실제로 나오는지, 사용자가 그걸 누를 수 있는지는 재지 않는다.
 *
 * 여기서 재는 것이 그 축이다 — docs/safety-guidelines.md §1 의 "어떤 페이지든
 * 사라지지 않는다" 를 DOM 으로 확인한다. 번호는 정본 배열에서 읽어 비교하므로
 * 이 파일에도 숫자를 적지 않는다(판정기 규칙과 같은 규율).
 */

/** 실제 사용자에게 보이는 쪽. 자리지기 사본은 aria-hidden 이라 role 쿼리에서 빠진다. */
function 실푸터() {
  return screen.getByRole("region", { name: "위기 상담 자원 안내" });
}

describe("CrisisFooter — 영구 노출", () => {
  it("접힌 기본 상태에서도 기본 상담번호가 바로 걸 수 있는 링크로 보인다", () => {
    render(<CrisisFooter />);
    const 링크 = within(실푸터()).getByRole("link", {
      name: new RegExp(CRISIS_DEFAULT.tel),
    });
    // 눌러서 *전화가 걸리는가* 가 요점이다. 텍스트만 있고 tel: 이 아니면
    // 위기 상태의 사용자가 번호를 손으로 옮겨 적어야 한다.
    expect(링크).toHaveAttribute("href", `tel:${CRISIS_DEFAULT.tel}`);
    expect(링크).toHaveTextContent(CRISIS_DEFAULT.label);
  });

  it("닫기·숨기기 손잡이가 없다 — 접기만 되고 사라지지는 않는다", async () => {
    const user = userEvent.setup();
    render(<CrisisFooter />);
    const 푸터 = 실푸터();

    // 버튼은 펼치기/접기 토글 하나뿐이어야 한다.
    const 버튼들 = within(푸터).getAllByRole("button");
    expect(버튼들).toHaveLength(1);
    expect(버튼들[0]).toHaveAccessibleName("위기 자원 목록 펼치기");

    // 펼쳤다 다시 접어도 번호 줄은 그대로 남는다.
    await user.click(버튼들[0]);
    await user.click(
      within(실푸터()).getByRole("button", { name: "위기 자원 목록 접기" }),
    );
    expect(
      within(실푸터()).getByRole("link", {
        name: new RegExp(CRISIS_DEFAULT.tel),
      }),
    ).toBeInTheDocument();
  });

  it("스크린 리더에 상담 자원이 두 번 읽히지 않는다 — 자리지기 사본은 접근성 트리 밖", () => {
    render(<CrisisFooter />);
    // 높이 확보용 사본이 aria-hidden 이 아니면 같은 번호가 두 번 낭독된다.
    const 링크들 = screen.getAllByRole("link", {
      name: new RegExp(CRISIS_DEFAULT.tel),
    });
    expect(링크들).toHaveLength(1);
    // 사본 자체는 DOM 에는 있어야 한다 (fixed 푸터가 가리는 만큼의 자리를 잡는 역할).
    expect(
      document.querySelectorAll('a[href="tel:' + CRISIS_DEFAULT.tel + '"]')
        .length,
    ).toBe(2);
  });
});

describe("CrisisFooter — 펼치기", () => {
  it("접힌 상태에서는 전체 자원 목록이 렌더되지 않는다", () => {
    render(<CrisisFooter />);
    const 푸터 = 실푸터();
    // 기본 자원 외 나머지는 아직 없다.
    for (const r of CRISIS_RESOURCES.slice(1)) {
      expect(
        within(푸터).queryByText(new RegExp(r.tel)),
      ).not.toBeInTheDocument();
    }
    expect(
      within(푸터).queryByRole("link", { name: /lifeline/ }),
    ).not.toBeInTheDocument();
  });

  it("펼치면 정본 배열의 자원이 하나도 빠짐없이 전화 링크로 나온다", async () => {
    const user = userEvent.setup();
    render(<CrisisFooter />);
    await user.click(
      within(실푸터()).getByRole("button", { name: "위기 자원 목록 펼치기" }),
    );

    const 푸터 = 실푸터();
    // 손으로 목록을 다시 쓰지 않고 배열을 순회한다 — 자원이 추가됐는데 화면에
    // 안 나오면 여기서 빨개진다(safety-guidelines §1: 한 줄도 사라지면 안 됨).
    for (const r of CRISIS_RESOURCES) {
      const 링크 = within(푸터).getAllByRole("link", { name: r.tel })[0];
      expect(링크).toHaveAttribute("href", `tel:${r.tel}`);
      expect(
        within(푸터).getByText(new RegExp(`${r.label}\\s*\\(${r.note}\\)`)),
      ).toBeInTheDocument();
    }
  });

  it("전화가 어려운 사용자를 위한 온라인 경로(생명의전화)도 함께 나온다", async () => {
    const user = userEvent.setup();
    render(<CrisisFooter />);
    await user.click(
      within(실푸터()).getByRole("button", { name: "위기 자원 목록 펼치기" }),
    );

    const 링크 = within(실푸터()).getByRole("link", {
      name: CRISIS_LIFELINE.url.replace(/^https:\/\/www\./, ""),
    });
    expect(링크).toHaveAttribute("href", CRISIS_LIFELINE.url);
    // 새 탭으로 나가되 opener 를 넘기지 않는다.
    expect(링크).toHaveAttribute("target", "_blank");
    expect(링크).toHaveAttribute("rel", "noreferrer");
  });

  it("펼친 목록에 '의료·임상 도구가 아니다' 면책이 포함된다", async () => {
    const user = userEvent.setup();
    render(<CrisisFooter />);
    await user.click(
      within(실푸터()).getByRole("button", { name: "위기 자원 목록 펼치기" }),
    );
    expect(
      within(실푸터()).getByText(/의료·임상 도구가 아닙니다/),
    ).toBeInTheDocument();
  });

  it("토글 버튼의 aria-expanded 와 라벨이 상태를 따라간다", async () => {
    const user = userEvent.setup();
    render(<CrisisFooter />);

    const 토글 = within(실푸터()).getByRole("button", {
      name: "위기 자원 목록 펼치기",
    });
    expect(토글).toHaveAttribute("aria-expanded", "false");

    await user.click(토글);
    const 접기 = within(실푸터()).getByRole("button", {
      name: "위기 자원 목록 접기",
    });
    expect(접기).toHaveAttribute("aria-expanded", "true");

    await user.click(접기);
    expect(
      within(실푸터()).getByRole("button", { name: "위기 자원 목록 펼치기" }),
    ).toHaveAttribute("aria-expanded", "false");
  });

  it("전화 링크와 펼치기 버튼은 분리된 타깃이다 — 링크를 눌러도 목록이 펼쳐지지 않는다", async () => {
    const user = userEvent.setup();
    render(<CrisisFooter />);

    // 예전에는 줄 전체가 펼치기 버튼이고 번호가 그 안의 23×15px 링크였다.
    // 통합으로 되돌아가면 이 단언이 깨진다.
    await user.click(
      within(실푸터()).getByRole("link", {
        name: new RegExp(CRISIS_DEFAULT.tel),
      }),
    );
    expect(
      within(실푸터()).getByRole("button", { name: "위기 자원 목록 펼치기" }),
    ).toHaveAttribute("aria-expanded", "false");
  });
});
