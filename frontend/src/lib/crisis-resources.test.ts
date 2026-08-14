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
 * 죽은 번호가 나가는 종류다(파일 상단 주석 참조).
 *
 * 그런데 **번호 값 자체는 여기서 재지 않는다.** 값을 못 미더워서가 아니라, 이
 * 파일이 값을 다시 적는 순간 그게 정본의 두 번째 사본이 되기 때문이다. 값은 이미
 * 두 군데서 고정돼 있다 —
 *   ① `scripts/check_frontend_hotline.py` 축 B: 이 모듈의 기본 번호 ==
 *      backend `application.yml` 의 `safety.crisis.default-contact` 대체값
 *   ② 백엔드 `ScenarioHotlineRatchetTest`
 * 여기 사본을 하나 더 두면 개정 때 고쳐야 할 자리만 늘고, 놓치면 "테스트는 옛
 * 번호를 지키고 화면은 새 번호를 쓴다" 는 최악의 모양이 된다. 그래서
 * `check_frontend_hotline.py` 는 테스트와 주석까지 포함해 리터럴을 금지한다.
 *
 * 대신 여기서 재는 것은 그 두 판정기가 안 보는 축이다 — 자료 구조가 성립하는가,
 * 그리고 파생 문자열들이 실제로 정본 배열에서 *조립*되는가.
 */
describe("위기 상담 자원", () => {
  it("기본 자원에 번호와 이름이 둘 다 있다", () => {
    // 값은 게이트가 백엔드와 대조한다. 여기서는 "비어 있지 않은가" 만 본다 —
    // 번호 없는 자원이 렌더되면 위기 카드가 이름만 띄우고 연결은 못 한다.
    expect(CRISIS_DEFAULT.tel.length).toBeGreaterThan(0);
    expect(CRISIS_DEFAULT.label.length).toBeGreaterThan(0);
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

  it("telHref 는 스킴만 붙이고 번호를 손대지 않는다", () => {
    // `telHref` 를 여기서 다시 구현해 비교하면(`\`tel:${r.tel}\``) 같은 오타를 양쪽에
    // 쓰고 초록을 받는다. 그래서 결과의 *성질* 을 잰다 — 접두사가 붙었고, 번호가
    // 그대로 들어 있고, 그 둘 말고는 아무것도 덧붙지 않았다(길이로 확인).
    for (const r of CRISIS_RESOURCES) {
      const href = telHref(r);
      expect(href.startsWith("tel:")).toBe(true);
      expect(href).toContain(r.tel);
      expect(href).toHaveLength("tel:".length + r.tel.length);
    }
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
