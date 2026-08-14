import { describe, expect, it } from "vitest";
import {
  CRISIS_DEFAULT,
  CRISIS_LIFELINE,
  CRISIS_LINE_FULL,
  CRISIS_LINE_SHORT,
  CRISIS_RESOURCES,
  telHref,
} from "./crisis-resources";

/**
 * 이 모듈의 실패는 화면이 깨지는 종류가 아니다 — 위기 상태의 사용자에게 조용히
 * 죽은 번호가 나가는 종류다(파일 상단 주석 참조). 그래서 "렌더된다" 가 아니라
 * **번호 자체**를 테스트한다.
 *
 * CI 의 `check_frontend_hotline.py` 는 *이 파일 바깥에* 번호 리터럴이 없는지와
 * 백엔드 대체값과의 일치를 본다. 여기서 재는 것은 그 판정기가 안 보는 축 —
 * 파생 문자열들이 실제로 정본 배열에서 나오는가다.
 */
describe("위기 상담 자원", () => {
  it("기본 자원은 109 (2024-01-01 통합 번호)", () => {
    // 구 번호 1393 으로 되돌아가는 회귀를 여기서 잡는다.
    expect(CRISIS_DEFAULT.tel).toBe("109");
    expect(CRISIS_DEFAULT.label).toBe("자살예방 상담전화");
  });

  it("기본 자원은 배열 0번과 같은 객체다 — 우선순위가 곧 순서", () => {
    expect(CRISIS_DEFAULT).toBe(CRISIS_RESOURCES[0]);
  });

  it("모든 자원이 4개 필드를 비우지 않고 갖는다", () => {
    expect(CRISIS_RESOURCES.length).toBeGreaterThanOrEqual(3);
    for (const r of CRISIS_RESOURCES) {
      expect(r.tel).toMatch(/^[\d-]+$/);
      expect(r.label.length).toBeGreaterThan(0);
      expect(r.shortLabel.length).toBeGreaterThan(0);
      expect(r.note.length).toBeGreaterThan(0);
    }
  });

  it("번호가 중복되지 않는다", () => {
    const tels = CRISIS_RESOURCES.map((r) => r.tel);
    expect(new Set(tels).size).toBe(tels.length);
  });

  it("telHref 는 tel: 스킴을 붙인다", () => {
    expect(telHref(CRISIS_DEFAULT)).toBe("tel:109");
    expect(telHref(CRISIS_RESOURCES[1])).toBe("tel:1577-0199");
  });

  describe("파생 문자열은 정본 배열에서 나온다", () => {
    // 하드코딩된 사본이 아니라 배열에서 조립됐는지 — 배열을 고치면 문안도
    // 따라 바뀐다는 성질을 재는 것이지 문안 자체를 외우는 게 아니다.
    it("짧은 표기에 상위 두 번호가 들어간다", () => {
      expect(CRISIS_LINE_SHORT).toContain(CRISIS_RESOURCES[0].tel);
      expect(CRISIS_LINE_SHORT).toContain(CRISIS_RESOURCES[1].tel);
      expect(CRISIS_LINE_SHORT).toContain("24시간");
    });

    it("전체 표기에 상위 두 자원의 번호와 축약 이름이 들어간다", () => {
      expect(CRISIS_LINE_FULL).toContain(CRISIS_RESOURCES[0].tel);
      expect(CRISIS_LINE_FULL).toContain(CRISIS_RESOURCES[0].shortLabel);
      expect(CRISIS_LINE_FULL).toContain(CRISIS_RESOURCES[1].tel);
      expect(CRISIS_LINE_FULL).toContain(CRISIS_RESOURCES[1].shortLabel);
    });
  });

  it("생명의전화는 전화가 아닌 온라인 경로다", () => {
    expect(CRISIS_LIFELINE.url).toMatch(/^https:\/\//);
    expect(CRISIS_LIFELINE.label).toBe("한국생명의전화");
  });
});
