import { describe, expect, it } from "vitest";
import { render } from "@/test/seed-passages";
import {
  buildScene3Echo,
  scene1Wilderness,
  scene2Monologues,
  scene3AssignmentsToPattern,
  scene3CardResponses,
  scene3Outcomes,
  scene4Reactions,
  scene5Monologue,
  scene6OutroByScene3,
} from "./moses-monologues";
import type { Monologue } from "./scripture-quote";

/**
 * 모세 미션도 backend 가 텍스트를 주지 않는다 — 이 파일이 유일한 소스다.
 *
 * 가장 깨지기 쉬운 지점은 Scene 3 이다. 다섯 변명 카드를 throw/heart 로 배정한 결과를
 * `scene3AssignmentsToPattern` 이 3패턴으로 접고, `buildScene3Echo` 가 *내려놓은 카드마다*
 * 본문 응답을 이어 붙인다. 카드 id 하나가 어긋나면 그 카드만 조용히 사라지므로
 * (에러도 안 나고 화면만 짧아진다) 여기서 id 집합과 조립 결과를 직접 잰다.
 *
 * 선택지 id 정본은 `backend/src/main/resources/scenarios/moses.yml`.
 *
 * 모놀로그는 이제 산문 + 인용 *조각* 이고 성경 자구는 `/api/scripture` 가 채운다
 * (`scripture-quote.ts`). 그래서 화면에 뜨는 글로 재려면 `render()` 로 시드 자구를 채워야
 * 한다. `buildScene3Echo` 도 이제 *조각을* 이어 붙이므로 — 문자열로 먼저 합치면 인용 위치를
 * 잃는다 — 포함 관계도 해석한 뒤에 잰다.
 */

/** moses.yml Scene 3 `cards[].id` (62~66행). 다섯 변명. */
const YML_CARD_IDS = [
  "who_am_i",
  "who_sent_me",
  "trust_me",
  "cant_speak",
  "send_other",
] as const;
/** moses.yml Scene 4 `options[].id` (84~86행). 파라오 앞 지팡이. */
const YML_SCENE4_OPTION_IDS = ["cast_now", "hesitate", "with_aaron"] as const;
/** moses.yml Scene 5 `options[].id` (101행). 단일 몸짓. */
const YML_SCENE5_GESTURE = "lift_staff";
/** page.tsx 의 gesture 완료 결과 2종 (yml 은 step 만 정의하고 결과는 페이지가 만든다). */
const PAGE_SCENE2_RESULTS = ["reverence", "hesitation"] as const;

const SCRIPTURE_REF = /\((창|출|삼상|시|요|마|막|눅|계)\s?\d+[:：]\d+/;

describe("모세 미션 모놀로그", () => {
  it("Scene 1~6 이 빠짐없이 텍스트 소스를 갖는다", () => {
    // 씬 연속성 — 6개 씬 중 하나라도 소스가 없으면 그 씬은 echo 가 빈다.
    expect(render(scene1Wilderness).trim().length).toBeGreaterThan(0);
    expect(Object.keys(scene2Monologues).length).toBe(2);
    expect(Object.keys(scene3Outcomes).length).toBe(3);
    expect(Object.keys(scene4Reactions).length).toBe(3);
    expect(Object.keys(scene5Monologue).length).toBe(1);
    expect(Object.keys(scene6OutroByScene3).length).toBe(3);
  });

  it("모든 분기 텍스트가 비어 있지 않다", () => {
    for (const record of [
      scene2Monologues,
      scene3Outcomes,
      scene3CardResponses,
      scene4Reactions,
      scene5Monologue,
      scene6OutroByScene3,
    ] as Record<string, Monologue>[]) {
      for (const [key, monologue] of Object.entries(record)) {
        // 인용이 자구를 못 얻으면 `render` 가 던진다 — 조용히 통과하는 경로가 없다.
        const text = render(monologue);
        expect(text.trim().length, `${key} 가 빈 문자열`).toBeGreaterThan(0);
        expect(text).not.toContain("undefined");
      }
    }
  });

  describe("키 집합이 yml / 페이지 계약과 일치한다", () => {
    it("Scene 2 는 페이지가 만들어 보내는 두 결과를 모두 갖는다", () => {
      expect(Object.keys(scene2Monologues).sort()).toEqual(
        [...PAGE_SCENE2_RESULTS].sort(),
      );
    });

    it("Scene 3 카드 응답이 yml 카드 5개와 정확히 일치한다", () => {
      expect(Object.keys(scene3CardResponses).sort()).toEqual(
        [...YML_CARD_IDS].sort(),
      );
    });

    it("Scene 4 선택지 3개", () => {
      expect(Object.keys(scene4Reactions).sort()).toEqual(
        [...YML_SCENE4_OPTION_IDS].sort(),
      );
    });

    it("Scene 5 는 lift_staff 단일 몸짓", () => {
      expect(Object.keys(scene5Monologue)).toEqual([YML_SCENE5_GESTURE]);
      expect(render(scene5Monologue.lift_staff)).toContain("출 14:21");
    });

    it("Scene 3 결과와 Scene 6 결말이 같은 패턴 키를 쓴다", () => {
      expect(Object.keys(scene6OutroByScene3).sort()).toEqual(
        Object.keys(scene3Outcomes).sort(),
      );
    });
  });

  describe("scene3AssignmentsToPattern — 어떤 배정도 결말에 도달한다(전사)", () => {
    const allOf = (slot: "throw" | "heart") =>
      Object.fromEntries(YML_CARD_IDS.map((id) => [id, slot]));

    it("전부 throw / 전부 heart / 섞임 3패턴이 모두 도달 가능하다", () => {
      expect(scene3AssignmentsToPattern(allOf("throw"))).toBe("all_throw");
      expect(scene3AssignmentsToPattern(allOf("heart"))).toBe("all_heart");
      expect(
        scene3AssignmentsToPattern({ ...allOf("throw"), send_other: "heart" }),
      ).toBe("mixed");
      // 세 패턴 전부가 실제 결말 텍스트를 갖는지까지 확인해야 '빈 화면 없음'이 증명된다.
      for (const pattern of ["all_throw", "all_heart", "mixed"] as const) {
        // 빈 배열도 truthy 라, 해석한 길이로 재야 실제로 글이 뜨는지 안다.
        expect(render(scene3Outcomes[pattern]).length).toBeGreaterThan(0);
        expect(render(scene6OutroByScene3[pattern]).length).toBeGreaterThan(0);
      }
    });

    it("빈 배정은 mixed 로 떨어진다 — 미배정 상태로 넘어와도 결말이 있다", () => {
      expect(scene3AssignmentsToPattern({})).toBe("mixed");
    });

    it("카드를 하나만 배정해도 그 값으로 판정한다", () => {
      // '전부' 판정은 배정된 값들만 본다. 페이지가 전 카드 배정 후에만 제출하므로 정상 경로엔 없지만,
      // 부분 배정이 들어와도 undefined 패턴이 나오지 않아야 한다.
      expect(scene3AssignmentsToPattern({ who_am_i: "throw" })).toBe(
        "all_throw",
      );
      expect(scene3AssignmentsToPattern({ who_am_i: "heart" })).toBe(
        "all_heart",
      );
    });
  });

  describe("buildScene3Echo — 내려놓은 카드마다 본문이 붙는다", () => {
    it("throw 한 카드의 응답만, yml 카드 순서대로 이어 붙인다", () => {
      // 사용자가 누른 순서가 아니라 카드 정의 순서로 고정 — 같은 배정이면 항상 같은 화면.
      const echo = buildScene3Echo("mixed", {
        send_other: "throw",
        who_am_i: "throw",
        trust_me: "heart",
      });
      const text = render(echo);
      expect(text.startsWith(render(scene3Outcomes.mixed))).toBe(true);
      expect(text).toContain(render(scene3CardResponses.who_am_i));
      expect(text).toContain(render(scene3CardResponses.send_other));
      expect(text).not.toContain(render(scene3CardResponses.trust_me));
      expect(text.indexOf(render(scene3CardResponses.who_am_i))).toBeLessThan(
        text.indexOf(render(scene3CardResponses.send_other)),
      );
    });

    it("다섯 장을 다 내려놓으면 다섯 응답이 다 붙는다", () => {
      const echo = buildScene3Echo(
        "all_throw",
        Object.fromEntries(YML_CARD_IDS.map((id) => [id, "throw" as const])),
      );
      const text = render(echo);
      for (const id of YML_CARD_IDS) {
        expect(text, `${id} 응답 누락`).toContain(
          render(scene3CardResponses[id]),
        );
      }
    });

    it("하나도 내려놓지 않으면 대표 응답(정체성)만 붙는다 — 과부하 방지", () => {
      const echo = buildScene3Echo(
        "all_heart",
        Object.fromEntries(YML_CARD_IDS.map((id) => [id, "heart" as const])),
      );
      const text = render(echo);
      expect(text).toContain(render(scene3CardResponses.who_am_i));
      expect(text).not.toContain(render(scene3CardResponses.cant_speak));
    });

    it("알 수 없는 카드 id 는 무시하고 대표 응답으로 떨어진다", () => {
      const echo = buildScene3Echo("mixed", { ghost_card: "throw" });
      const text = render(echo);
      expect(text.startsWith(render(scene3Outcomes.mixed))).toBe(true);
      expect(text).toContain(render(scene3CardResponses.who_am_i));
      expect(text).not.toContain("undefined");
    });

    it("에코는 언제나 결과 텍스트 + 빈 줄 + 응답 구조다", () => {
      // 페이지가 whitespace-pre-line 으로 렌더하므로 이 구분이 곧 문단 구분이다.
      const echo = buildScene3Echo("all_throw", { who_am_i: "throw" });
      expect(render(echo)).toBe(
        `${render(scene3Outcomes.all_throw)}\n\n${render(scene3CardResponses.who_am_i)}`,
      );
    });
  });

  describe("결말 텍스트의 성질", () => {
    it("세 결말이 서로 다르다", () => {
      // 조각 배열은 참조 비교라 늘 다르다. 해석한 문자열로 재야 복붙을 잡는다.
      const outros = Object.values(scene6OutroByScene3).map(render);
      expect(new Set(outros).size).toBe(outros.length);
    });

    it("모든 결말이 출 3:12 동행 약속을 공통 축으로 갖는다", () => {
      // 파일 상단이 선언한 신학 축. 한 분기만 빠지면 '함께 가심' 프레임이 그 분기에서만 사라진다.
      for (const [key, monologue] of Object.entries(scene6OutroByScene3)) {
        const text = render(monologue);
        expect(text, `${key} 결말`).toContain("출 3:12");
        expect(text).toMatch(SCRIPTURE_REF);
      }
    });

    it("카드 응답 다섯 개가 각각 본문 인용을 갖는다", () => {
      for (const [key, monologue] of Object.entries(scene3CardResponses)) {
        expect(render(monologue), `${key} 응답에 성경 인용 없음`).toMatch(
          SCRIPTURE_REF,
        );
      }
    });
  });
});
