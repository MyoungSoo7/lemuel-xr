import { describe, expect, it } from "vitest";
import { render } from "@/test/seed-passages";
import {
  lastStoneOf,
  scene1Psalm23,
  scene2Reactions,
  scene4LastStone,
  scene5Monologue,
  scene6OutroByLastStone,
} from "./david-monologues";
import type { Monologue } from "./scripture-quote";

/**
 * 다윗 미션은 backend 가 텍스트를 주지 않는다(`matchResponseText` 가 david 에서 null).
 * 즉 **이 파일이 유일한 텍스트 소스** 다 — 키가 비면 곧바로 빈 echo 다.
 *
 * 특히 Scene 4(다섯 돌)는 사용자가 집은 *순서* 로 결말이 갈린다. `src/app/david/page.tsx` 는
 * `priority: \`last_${lastStoneOf(order)}\`` 로 보내고, 받는 쪽에서 `last_` 를 떼어
 * `scene4LastStone` / `scene6OutroByLastStone` 을 조회한다. 이 왕복이 한 군데라도 어긋나면
 * 특정 감정을 마지막에 쥔 사용자만 결말이 비는, 재현이 어려운 버그가 된다.
 *
 * 선택지 id 정본은 `backend/src/main/resources/scenarios/david.yml`.
 *
 * 모놀로그는 이제 산문 + 인용 *조각* 이다(`scripture-quote.ts`) — 성경 자구는 `/api/scripture`
 * 가 채운다. 그래서 "비어 있지 않다"·"인용이 있다" 는 전부 `render()` 로 시드 자구를 채운 뒤의
 * **화면 문자열** 로 재야 한다. 조각 배열만 보면 인용이 하나도 안 풀려도 배열은 비어 있지 않다.
 */

/** david.yml Scene 2 `options[].id` (50~52행). 엘리압의 비난 앞 반응. */
const YML_SCENE2_OPTION_IDS = ["rebut", "silence", "walk_past"] as const;
/** david.yml Scene 4 `stones[].id` (92~96행). 다섯 감정 = 다섯 돌. */
const YML_STONE_IDS = [
  "fear",
  "humiliation",
  "loneliness",
  "trust",
  "prayer",
] as const;

const SCRIPTURE_REF = /\((창|출|삼상|시|요|마|막|눅|계)\s?\d+[:：]\d+/;

describe("다윗 미션 모놀로그", () => {
  it("Scene 1 도입부가 시 23:1 을 인용한다", () => {
    // Scene 1 은 '시편 = 일기' 프레임의 앵커다(파일 상단 R1 라우팅 예비). 인용이 빠지면 프레임이 무너진다.
    const text = render(scene1Psalm23);
    expect(text.trim().length).toBeGreaterThan(0);
    expect(text).toContain("시 23:1");
  });

  it("Scene 2 반응 키가 yml 선택지 id 와 정확히 일치한다", () => {
    expect(Object.keys(scene2Reactions).sort()).toEqual(
      [...YML_SCENE2_OPTION_IDS].sort(),
    );
  });

  it("Scene 5 는 단일 나아감(throw) 하나만 갖는다", () => {
    // david.yml Scene 5 는 two_handed_throw — 선택지가 없는 단일 행동 씬이다.
    expect(Object.keys(scene5Monologue)).toEqual(["throw"]);
    expect(render(scene5Monologue.throw)).toContain("삼상 17:47");
  });

  it("모든 분기 텍스트가 비어 있지 않다", () => {
    for (const record of [
      scene2Reactions,
      scene4LastStone,
      scene5Monologue,
      scene6OutroByLastStone,
    ] as Record<string, Monologue>[]) {
      for (const [key, monologue] of Object.entries(record)) {
        // 인용이 자구를 못 얻으면 `render` 가 던진다 — 조용히 통과하는 경로가 없다.
        const text = render(monologue);
        expect(text.trim().length, `${key} 가 빈 문자열`).toBeGreaterThan(0);
        expect(text).not.toContain("undefined");
      }
    }
  });

  describe("돌(감정) → 결말 매핑이 전사(全射)다", () => {
    it("yml 의 다섯 돌 전부가 echo 텍스트와 결말 텍스트를 갖는다", () => {
      // 하나라도 빠지면 그 감정을 마지막에 쥔 사용자만 Scene 4 echo 또는 Scene 6 결말이 빈다.
      for (const id of YML_STONE_IDS) {
        // 빈 배열도 truthy 라, 해석한 길이로 재야 실제로 글이 뜨는지 안다.
        expect(
          render(scene4LastStone[id]).length,
          `${id} echo 없음`,
        ).toBeGreaterThan(0);
        expect(
          render(scene6OutroByLastStone[id]).length,
          `${id} 결말 없음`,
        ).toBeGreaterThan(0);
      }
      expect(Object.keys(scene4LastStone).sort()).toEqual(
        [...YML_STONE_IDS].sort(),
      );
      expect(Object.keys(scene6OutroByLastStone).sort()).toEqual(
        [...YML_STONE_IDS].sort(),
      );
    });

    it("다섯 결말이 모두 도달 가능하다 — 죽은 분기 없음", () => {
      // 각 감정을 마지막에 집는 순서를 만들어 실제로 그 결말 키가 나오는지 확인한다.
      const reached = new Set(
        YML_STONE_IDS.map((id) =>
          lastStoneOf([...YML_STONE_IDS].filter((s) => s !== id).concat(id)),
        ),
      );
      expect([...reached].sort()).toEqual([...YML_STONE_IDS].sort());
    });

    it("페이지의 last_ 접두 왕복을 거쳐도 키가 살아남는다", () => {
      // page.tsx 가 `last_${id}` 로 싣고 정규식으로 떼어낸다. 그 왕복을 그대로 재현한다.
      for (const id of YML_STONE_IDS) {
        const priority = `last_${lastStoneOf([id])}`;
        const stone = priority.replace(/^last_/, "");
        expect(stone in scene4LastStone).toBe(true);
        expect(stone in scene6OutroByLastStone).toBe(true);
      }
    });
  });

  describe("lastStoneOf", () => {
    it("뒤에서부터 첫 유효 id 를 고른다", () => {
      expect(lastStoneOf(["fear", "prayer", "trust"])).toBe("trust");
      expect(lastStoneOf(["loneliness"])).toBe("loneliness");
    });

    it("꼬리의 알 수 없는 id 는 건너뛴다", () => {
      // 프론트가 stones 를 yml 에서 그대로 받으므로 정상 경로엔 없지만,
      // 잡값 하나가 섞였다고 결말이 통째로 fallback 되면 안 된다.
      expect(lastStoneOf(["humiliation", "bogus", "???"])).toBe("humiliation");
    });

    it("빈 배열·전부 잡값이면 trust 로 떨어진다", () => {
      expect(lastStoneOf([])).toBe("trust");
      expect(lastStoneOf(["nope", "nah"])).toBe("trust");
      // 주의: 이 fallback 은 사용자가 실제로 '신뢰'를 마지막에 쥔 경우와 구별되지 않는다.
      expect(render(scene6OutroByLastStone[lastStoneOf([])])).not.toBe("");
    });
  });

  describe("결말 텍스트의 성질", () => {
    it("다섯 결말이 서로 다르다", () => {
      // 조각 배열은 참조 비교라 늘 다르다. 해석한 문자열로 재야 복붙을 잡는다.
      const outros = Object.values(scene6OutroByLastStone).map(render);
      expect(new Set(outros).size).toBe(outros.length);
    });

    it("모든 결말이 본문 인용으로 시작한다", () => {
      for (const [key, monologue] of Object.entries(scene6OutroByLastStone)) {
        const text = render(monologue);
        expect(text, `${key} 결말에 성경 인용 없음`).toMatch(SCRIPTURE_REF);
        // 인용 뒤 빈 줄로 본문이 이어지는 서식 — 페이지가 whitespace-pre-line 으로 렌더한다.
        expect(text).toContain("\n\n");
      }
    });

    it("모든 결말이 '일기 한 줄' 로 라우팅한다 — R1 안전 경로", () => {
      // 파일 상단이 선언한 안전 설계(승리를 과제로 던지지 않고 일기로 연결)를 데이터에서 강제한다.
      for (const [key, monologue] of Object.entries(scene6OutroByLastStone)) {
        expect(render(monologue), `${key} 결말에 일기 라우팅 없음`).toMatch(
          /한 줄/,
        );
      }
    });
  });
});
