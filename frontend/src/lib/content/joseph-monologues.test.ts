import { describe, expect, it } from "vitest";
import {
  scene1Dream,
  scene2Monologues,
  scene3DecisionToPattern,
  scene3Outcomes,
  scene4Reactions,
  scene5OutroByScene3,
  type Scene3Pattern,
} from "./joseph-monologues";

/**
 * 이 모듈은 *정적 데이터* 다 — import 만 해도 라인 커버리지는 오르지만 아무것도 지키지 못한다.
 * 그래서 여기서 재는 것은 "값이 있다" 가 아니라 **깨지면 화면이 조용히 비는 불변조건** 이다.
 *
 * 이 파일의 텍스트는 `src/app/joseph/page.tsx` 가 씬 전환마다 echo/outro 로 직접 렌더한다.
 * backend 의 `DecideSceneUseCase.matchResponseText` 는 joseph 계열에 대해 텍스트를 주지 않으므로
 * (파일 상단 주석 참조) **키가 하나라도 비면 사용자는 미션 중간에 빈 화면을 본다.**
 *
 * 선택지 id 의 정본은 `backend/src/main/resources/scenarios/joseph.yml` 이다. 프론트 Record 의
 * 키가 그 id 집합과 어긋나는 순간 조회가 undefined 로 떨어진다 — 그 어긋남을 여기서 잡는다.
 */

/** joseph.yml Scene 2 `options[].id` (47~49행). 저장 비율 3분기. */
const YML_SCENE2_OPTION_IDS = ["save_20", "save_33", "save_50"] as const;
/** joseph.yml Scene 3 `queues[].id` (69~71행). 페이지는 이걸 `{priority: id}` 로 보낸다. */
const YML_SCENE3_QUEUE_IDS = ["farmer", "immigrant", "merchant"] as const;
/** joseph.yml Scene 4 `options[].id` (91~93행). 형제 재회 반응 3분기. */
const YML_SCENE4_OPTION_IDS = ["reveal", "test", "silent"] as const;

/** 결말 텍스트에는 본문 인용이 붙는다 — 인용이 빠지면 신학 검수 근거가 사라진다. */
const SCRIPTURE_REF = /\((창|출|삼상|시|요|마|막|눅|계)\s?\d+[:：]\d+/;

describe("요셉 미션 모놀로그", () => {
  describe("씬 1~5 가 빠짐없이 텍스트를 갖는다", () => {
    // 씬 연속성. 5개 씬 중 하나라도 텍스트 소스가 없으면 그 씬은 빈 화면이 된다.
    it("Scene 1 도입부가 비어 있지 않다", () => {
      expect(scene1Dream.trim().length).toBeGreaterThan(0);
      expect(scene1Dream).toMatch(SCRIPTURE_REF);
    });

    it("Scene 2~5 의 모든 분기 텍스트가 비어 있지 않다", () => {
      const records = [
        scene2Monologues,
        scene3Outcomes,
        scene4Reactions,
        scene5OutroByScene3,
      ];
      for (const record of records) {
        const entries = Object.entries(record);
        expect(entries.length).toBeGreaterThan(0);
        for (const [key, text] of entries) {
          expect(typeof text, `${key} 는 문자열이어야 한다`).toBe("string");
          expect(text.trim().length, `${key} 가 빈 문자열`).toBeGreaterThan(0);
          // 문자열 연결 중 undefined 가 섞여 들어가는 사고를 잡는다.
          expect(text).not.toContain("undefined");
        }
      }
    });
  });

  describe("선택지 id ↔ 텍스트 키가 정확히 일치한다 (yml 정본 기준)", () => {
    // 키가 하나 빠지면 그 선택지를 고른 사용자만 echo 를 못 본다 — QA 가 가장 놓치기 쉬운 구멍.
    it("Scene 2 저장 비율 3개", () => {
      expect(Object.keys(scene2Monologues).sort()).toEqual(
        [...YML_SCENE2_OPTION_IDS].sort(),
      );
    });

    it("Scene 4 재회 반응 3개", () => {
      expect(Object.keys(scene4Reactions).sort()).toEqual(
        [...YML_SCENE4_OPTION_IDS].sort(),
      );
    });

    it("Scene 3 결과와 Scene 5 결말이 같은 패턴 키 집합을 쓴다", () => {
      // 두 Record 가 어긋나면 Scene 3 는 나왔는데 Scene 5 결말만 비는 형태로 깨진다.
      expect(Object.keys(scene5OutroByScene3).sort()).toEqual(
        Object.keys(scene3Outcomes).sort(),
      );
    });
  });

  describe("scene3DecisionToPattern — 어떤 선택도 도달할 결말이 있다(전사)", () => {
    it("yml 의 모든 큐 id 가 실재하는 결말로 매핑된다", () => {
      for (const id of YML_SCENE3_QUEUE_IDS) {
        const pattern = scene3DecisionToPattern({ priority: id });
        expect(pattern, `${id} 가 매핑되지 않음`).not.toBeNull();
        // 매핑만 되고 텍스트가 없으면 결국 빈 화면이다. 텍스트까지 확인해야 의미가 있다.
        expect(scene3Outcomes[pattern as Scene3Pattern]).toBeTruthy();
        expect(scene5OutroByScene3[pattern as Scene3Pattern]).toBeTruthy();
      }
    });

    it("세 결말이 모두 도달 가능하다 — 죽은 분기 없음", () => {
      const reached = new Set(
        YML_SCENE3_QUEUE_IDS.map((id) =>
          scene3DecisionToPattern({ priority: id }),
        ),
      );
      expect(reached.size).toBe(3);
      expect([...reached].sort()).toEqual(Object.keys(scene3Outcomes).sort());
    });

    it("모르는 값·비객체는 null 로 떨어진다 — 페이지가 fallback 을 탈 수 있게", () => {
      expect(scene3DecisionToPattern({ priority: "soldier" })).toBeNull();
      expect(scene3DecisionToPattern({})).toBeNull();
      expect(scene3DecisionToPattern(null)).toBeNull();
      expect(scene3DecisionToPattern("farmer")).toBeNull();
      expect(scene3DecisionToPattern(42)).toBeNull();
      expect(scene3DecisionToPattern(undefined)).toBeNull();
    });
  });

  describe("결말 텍스트의 성질", () => {
    it("세 결말이 서로 다르다 — 복붙으로 분기가 무의미해지는 회귀 방지", () => {
      const outros = Object.values(scene5OutroByScene3);
      expect(new Set(outros).size).toBe(outros.length);
      const outcomes = Object.values(scene3Outcomes);
      expect(new Set(outcomes).size).toBe(outcomes.length);
    });

    it("모든 결말이 본문 인용을 포함한다", () => {
      for (const [key, text] of Object.entries(scene5OutroByScene3)) {
        expect(text, `${key} 결말에 성경 인용 없음`).toMatch(SCRIPTURE_REF);
      }
    });

    it("결말은 창 50:20 을 공통 축으로 갖는다", () => {
      // 파일 상단이 선언한 신학 축(요셉 서사의 정점). 한 분기만 빠지면 톤이 어긋난다.
      for (const text of Object.values(scene5OutroByScene3)) {
        expect(text).toContain("창 50:20");
      }
    });
  });
});
