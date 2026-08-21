import { describe, expect, it } from "vitest";
import * as david from "./david-monologues";
import * as jesus from "./jesus-monologues";
import * as joseph from "./joseph-monologues";
import * as moses from "./moses-monologues";
import { refsOf, type Monologue } from "./scripture-quote";
import { render } from "@/test/seed-passages";
import {
  entriesOf,
  isMonologue,
  isMonologueRecord,
  REFS_EXPORT,
} from "@/test/monologue-shape";

/**
 * 네 인물 파일의 *공통 규약* 을 잰다.
 *
 * 왜 별도 파일인가 — 인물 페이지(`src/app/{joseph,david,moses,jesus}/page.tsx`)는 서로 다른
 * 컴포넌트지만 같은 관용(씬 1 도입 모놀로그 + 분기 Record + 조회 함수)을 전제로 쓰여 있다.
 * **한 인물만 모양이 다르면 그 인물에서만 화면이 깨진다** — 그리고 그건 그 미션을 실제로
 * 끝까지 플레이해 보기 전엔 아무도 모른다. 그래서 모양 자체를 여기서 고정한다.
 *
 * 개별 파일 테스트가 "이 인물의 키가 yml 과 맞는가" 를 잰다면, 이 파일은
 * "네 인물이 서로 같은 규약을 지키는가" 를 잰다.
 *
 * 규약은 2026-08-21 에 한 번 바뀌었다. 예전에는 "export 는 문자열이거나 문자열 Record"
 * 였지만, 성경 자구가 `/api/scripture` 로 옮겨 가면서 이제 **산문 + 인용 조각의 배열**
 * (`Monologue`) 이다. 그래서 여기서 재는 문자열은 전부 `render()` 로 시드 자구를 채운
 * 결과여야 한다 — 조각만 보면 인용 쪽에 들어온 금지 어휘·빈 문자열을 통째로 놓친다.
 */

type Mod = Record<string, unknown>;

const CHARACTERS: Array<{ name: string; mod: Mod; scenes: number }> = [
  { name: "joseph", mod: joseph as Mod, scenes: 5 },
  { name: "david", mod: david as Mod, scenes: 6 },
  { name: "moses", mod: moses as Mod, scenes: 6 },
  { name: "jesus", mod: jesus as Mod, scenes: 7 },
];

/**
 * 모듈이 내보낸 모든 사용자 노출 문자열을 평탄화한다 — **해석한 뒤의** 문자열이다.
 * 조각 단계에서 재면 인용 자구가 검사 밖으로 빠진다. 함수와 `allRefs` 는 제외.
 */
function allTexts(mod: Mod): Array<[string, string]> {
  return entriesOf(mod).map(([name, monologue]) => [name, render(monologue)]);
}

describe("인물 모놀로그 공통 규약", () => {
  describe("모든 인물이 같은 shape 관용을 따른다", () => {
    it.each(CHARACTERS)(
      "$name — 씬 1 도입 모놀로그를 정확히 하나 갖는다",
      ({ mod }) => {
        // 페이지는 예외 없이 씬 1 을 '선택지 없는 단일 모놀로그'로 렌더한다.
        const scene1 = Object.entries(mod).filter(([name]) =>
          name.startsWith("scene1"),
        );
        expect(scene1.length).toBe(1);
        // 분기 Record 가 아니라 단일 모놀로그여야 한다 — Record 로 바뀌면 그 인물의
        // 도입 화면만 조회 키가 없어 빈다.
        expect(isMonologue(scene1[0][1])).toBe(true);
        expect(render(scene1[0][1] as Monologue).trim().length).toBeGreaterThan(
          0,
        );
      },
    );

    it.each(CHARACTERS)(
      "$name — 내보낸 값은 모놀로그 / 모놀로그 Record / 함수 / allRefs 뿐이다",
      ({ name, mod }) => {
        // 공용 렌더 경로는 `MonologueText` 하나다. 그 컴포넌트가 못 받는 모양이
        // 한 인물에게만 섞이면 그 인물에서만 화면이 깨진다.
        for (const [key, value] of Object.entries(mod)) {
          if (typeof value === "function" || key === REFS_EXPORT) continue;
          expect(
            isMonologue(value) || isMonologueRecord(value),
            `${name}.${key} 가 예상 밖 형태`,
          ).toBe(true);
        }
      },
    );

    it.each(CHARACTERS)(
      "$name — 화면이 열어야 할 참조 전부를 allRefs 로 내보낸다",
      ({ name, mod }) => {
        // 페이지는 이 하나를 `useMonologueTexts` 에 넘긴다. 여기서 빠진 참조는
        // 조회되지 않고, 그 인용을 쓰는 모놀로그는 통째로 렌더를 포기한다
        // (`resolveMonologue` 는 all-or-nothing) — 문장 하나가 아니라 문단이 사라진다.
        const declared = mod[REFS_EXPORT];
        expect(Array.isArray(declared), `${name}.allRefs 없음`).toBe(true);
        const used = new Set(
          entriesOf(mod).flatMap(([, m]) => refsOf(m as Monologue)),
        );
        expect([...used].sort()).toEqual([...(declared as string[])].sort());
      },
    );

    it.each(CHARACTERS)(
      "$name — 씬 번호가 1부터 연속이고 마지막 씬까지 텍스트가 있다",
      ({ name, mod, scenes }) => {
        // export 이름의 sceneN 접두로 씬 커버리지를 센다. yml 의 씬 수와 어긋나면
        // 중간 씬이 통째로 빈 화면이 된다 (backend 가 텍스트를 안 주므로 fallback 도 없다).
        const covered = new Set<number>();
        for (const key of Object.keys(mod)) {
          const m = /^scene(\d+)/.exec(key);
          if (m) covered.add(Number(m[1]));
        }
        const missing: number[] = [];
        for (let i = 1; i <= scenes; i++) {
          if (!covered.has(i)) missing.push(i);
        }
        // david Scene 3(사울의 갑옷: wear/walk/remove)은 모놀로그 텍스트가 없다 —
        // page.tsx 의 buildLocalEcho 도 fromScene===3 에서 null 을 돌려준다.
        // *알려진 구멍* 이므로 소스를 고치지 않고 현 상태만 고정한다.
        const KNOWN_GAPS: Record<string, number[]> = { david: [3] };
        expect(missing, `${name} 의 빠진 씬`).toEqual(KNOWN_GAPS[name] ?? []);
        expect(covered.has(1)).toBe(true);
        expect(covered.has(scenes)).toBe(true);
      },
    );
  });

  describe("모든 인물의 모든 텍스트가 렌더 가능한 상태다", () => {
    it.each(CHARACTERS)(
      "$name — 빈 문자열·미완성 자리표시자가 없다",
      ({ name, mod }) => {
        const texts = allTexts(mod);
        expect(texts.length).toBeGreaterThan(0);
        for (const [key, text] of texts) {
          expect(
            text.trim().length,
            `${name}.${key} 가 빈 문자열`,
          ).toBeGreaterThan(0);
          // 문자열 연결 사고와 미완성 초안을 함께 잡는다.
          for (const bad of [
            "undefined",
            "NaN",
            "[object Object]",
            "TODO",
            "TBD",
          ]) {
            expect(text, `${name}.${key} 에 "${bad}"`).not.toContain(bad);
          }
          // 앞뒤 공백이 남으면 whitespace-pre-line 렌더에서 그대로 보인다.
          expect(text, `${name}.${key} 앞뒤 공백`).toBe(text.trim());
        }
      },
    );

    it.each(CHARACTERS)(
      "$name — 같은 Record 안에 중복 텍스트가 없다",
      ({ name, mod }) => {
        // 한 Record 안의 두 선택지가 같은 문장이면 그 분기는 사실상 죽은 분기다
        // (사용자는 다른 선택을 했는데 같은 화면을 본다).
        for (const [key, value] of Object.entries(mod)) {
          if (!isMonologueRecord(value)) continue;
          // 조각 배열끼리는 참조 비교라 늘 다르다. 해석한 문자열로 재야 복붙을 잡는다.
          const texts = Object.values(value).map(render);
          if (texts.length < 2) continue;
          expect(new Set(texts).size, `${name}.${key} 에 중복 텍스트`).toBe(
            texts.length,
          );
        }
      },
    );
  });

  describe("R2/R3 안전선 — 네 파일이 공통으로 선언한 금지 어휘", () => {
    // 네 파일 상단이 모두 "고난 미화 금지 / 회복 압박 금지" 를 명시한다. 그 선언이
    // 문장 손질 과정에서 조용히 뒤집히는 것을 데이터 수준에서 막는다.
    const FORBIDDEN = [
      "극복하라",
      "이겨내라",
      "이겨내야",
      "이겨야",
      "견뎌내라",
      "견디면 강해",
      "부활하라",
      "회복해야",
    ];

    it.each(CHARACTERS)(
      "$name — 명령형 극복·회복 어휘 0건",
      ({ name, mod }) => {
        for (const [key, text] of allTexts(mod)) {
          for (const word of FORBIDDEN) {
            expect(text, `${name}.${key} 에 금지 어휘 "${word}"`).not.toContain(
              word,
            );
          }
        }
      },
    );
  });

  describe("조회 함수는 어떤 입력에도 결말을 돌려준다", () => {
    it("null 로 떨어질 수 있는 것은 joseph 하나뿐이다 — 나머지는 항상 fallback", () => {
      // 페이지가 null 을 다뤄야 하는 곳이 어디인지가 인물마다 다르면 공용 처리 관용이 깨진다.
      // 현 규약: joseph.scene3DecisionToPattern 만 null 가능(페이지가 그 분기를 명시 처리),
      // 나머지 셋은 fallback 값을 반환해 화면이 비지 않게 한다.
      expect(joseph.scene3DecisionToPattern({ priority: "???" })).toBeNull();
      expect(david.lastStoneOf([])).toBe("trust");
      expect(moses.scene3AssignmentsToPattern({})).toBe("mixed");
      expect(jesus.iamOf(null)).toBe("the_way");
    });

    it("각 fallback 값이 실제 텍스트를 갖는다 — fallback 이 빈 화면이면 의미 없다", () => {
      // 빈 배열도 truthy 라, 해석한 길이로 재야 실제로 글이 뜨는지 안다.
      expect(
        render(david.scene6OutroByLastStone[david.lastStoneOf([])]).length,
      ).toBeGreaterThan(0);
      expect(
        render(moses.scene6OutroByScene3[moses.scene3AssignmentsToPattern({})])
          .length,
      ).toBeGreaterThan(0);
      expect(render(jesus.scene4Iam[jesus.iamOf(null)]).length).toBeGreaterThan(
        0,
      );
    });

    it("[알려진 불일치] jesus 만 분기 키 outro Record 가 없다", () => {
      // joseph/david/moses 는 '결말 Record'(분기 키 → 결말 문장)를 갖지만 jesus 의 outro
      // (scene7Ascension)는 단일 문자열이고, 분기용 scene7Recovery 는 페이지가 읽지 않는다.
      // 공용 outro 컴포넌트를 만들면 jesus 에서만 모양이 다르다는 뜻 — 현 상태를 고정해 둔다.
      const outroRecordOf = (mod: Mod) =>
        Object.entries(mod).find(
          ([k, v]) => /Outro/.test(k) && isMonologueRecord(v),
        );
      expect(outroRecordOf(joseph as Mod)?.[0]).toBe("scene5OutroByScene3");
      expect(outroRecordOf(david as Mod)?.[0]).toBe("scene6OutroByLastStone");
      expect(outroRecordOf(moses as Mod)?.[0]).toBe("scene6OutroByScene3");
      expect(outroRecordOf(jesus as Mod)).toBeUndefined();
    });
  });
});
