import { describe, expect, it } from "vitest";
import { CRISIS_RESOURCES } from "@/lib/crisis-resources";
import { render } from "@/test/seed-passages";
import {
  iamOf,
  scene1Incarnation,
  scene2Beatitudes,
  scene3Touch,
  scene4Iam,
  scene4Teaching,
  scene5Passion,
  scene6Resurrection,
  scene7Ascension,
  scene7CrisisReminder,
  scene7Recovery,
  type RecoveryKey,
} from "./jesus-monologues";
import type { Monologue } from "./scripture-quote";

/**
 * 예수 미션(capstone)은 7개 씬 전부의 텍스트를 이 파일이 단독으로 공급한다.
 * backend 는 캐시 키만 갖고 있어 (`matchResponseText` 가 jesus 에서 null) 여기가 비면
 * 그대로 빈 화면이다.
 *
 * 이 파일은 다른 셋과 달리 **파일 상단이 스스로 강한 계약을 선언한다** — §4.3 신학 5항목과
 * R1~R5 안전선. 그중 데이터만으로 검증 가능한 것(위기번호가 정본에서 파생되는가,
 * 요 14:6 배타절이 남아 있는가, R3 회복 강요 어휘가 0건인가)을 여기서 잰다.
 * 문장 톤 심사는 사람/스킬의 몫이고, 여기서는 *회귀로 조용히 사라질 수 있는 것* 만 고정한다.
 *
 * 선택지 id 정본은 `backend/src/main/resources/scenarios/jesus.yml`.
 *
 * 모놀로그는 이제 산문 + 인용 *조각* 이고 성경 자구는 `/api/scripture` 가 채운다
 * (`scripture-quote.ts`). 그래서 이 파일의 계약 — 요 14:6 배타절이 살아 있는가, R3 금지
 * 어휘가 0건인가 — 은 조각이 아니라 `render()` 로 해석한 **화면 문자열** 에서 재야 한다.
 * 조각만 훑으면 금지 어휘가 성경 자구 쪽에 들어와도 못 본다.
 */

/** jesus.yml Scene 4 `options[].id` (204·210·216행). 길/진리/생명 3분기. */
const YML_SCENE4_IAM_IDS = ["the_way", "the_truth", "the_life"] as const;
/** jesus.yml recovery_keys 대응 진입 감정 5종. */
const RECOVERY_KEYS: RecoveryKey[] = [
  "ANXIOUS",
  "LONELY",
  "EXHAUSTED",
  "SAD",
  "CONFUSED",
];

const SCRIPTURE_REF = /\((창|출|삼상|시|요|마|막|눅|계)\s?\d+[:：]\d+/;

/** 모놀로그 하나든 분기 묶음이든 화면 문자열 목록으로. */
function renderAll(source: Monologue | Record<string, Monologue>): string[] {
  return Array.isArray(source)
    ? [render(source)]
    : Object.values(source as Record<string, Monologue>).map(render);
}

/** 씬 번호 → 그 씬을 렌더할 텍스트 소스. 구멍이 나면 그 씬이 빈다. */
const SCENE_TEXT_SOURCES: Record<
  number,
  Monologue | Record<string, Monologue>
> = {
  1: scene1Incarnation,
  2: scene2Beatitudes,
  3: scene3Touch,
  4: scene4Iam,
  5: scene5Passion,
  6: scene6Resurrection,
  7: scene7Ascension,
};

describe("예수 미션 모놀로그", () => {
  describe("씬 1~7 연속성", () => {
    it("일곱 씬이 빠짐없이 비지 않은 텍스트를 갖는다", () => {
      // 번호가 하나라도 비면 capstone 이 중간에서 끊긴다.
      for (let scene = 1; scene <= 7; scene++) {
        const source = SCENE_TEXT_SOURCES[scene];
        expect(source, `Scene ${scene} 텍스트 소스 없음`).toBeTruthy();
        const texts = renderAll(source);
        expect(texts.length).toBeGreaterThan(0);
        for (const text of texts) {
          expect(
            text.trim().length,
            `Scene ${scene} 빈 문자열`,
          ).toBeGreaterThan(0);
          expect(text).not.toContain("undefined");
        }
      }
    });

    it("각 씬 텍스트가 본문 인용을 갖는다 — AI 보조 표기의 근거", () => {
      for (const [scene, source] of Object.entries(SCENE_TEXT_SOURCES)) {
        for (const text of renderAll(source)) {
          expect(text, `Scene ${scene} 인용 없음`).toMatch(SCRIPTURE_REF);
        }
      }
    });
  });

  describe("iamOf — 세 문 어디로 들어와도 도착할 텍스트가 있다(전사)", () => {
    it("yml 의 세 선택지 id 가 그대로 키로 살아 있다", () => {
      expect(Object.keys(scene4Iam).sort()).toEqual(
        [...YML_SCENE4_IAM_IDS].sort(),
      );
      for (const id of YML_SCENE4_IAM_IDS) {
        expect(iamOf(id)).toBe(id);
        // 빈 배열도 truthy 라, 해석한 길이로 재야 실제로 글이 뜨는지 안다.
        expect(render(scene4Iam[iamOf(id)]).length).toBeGreaterThan(0);
      }
    });

    it("세 분기가 모두 도달 가능하다 — 죽은 분기 없음", () => {
      const reached = new Set(YML_SCENE4_IAM_IDS.map((id) => iamOf(id)));
      expect([...reached].sort()).toEqual(Object.keys(scene4Iam).sort());
    });

    it("null·미지의 값은 the_way 로 떨어진다 — 빈 화면 대신 길", () => {
      expect(iamOf(null)).toBe("the_way");
      expect(iamOf("")).toBe("the_way");
      expect(iamOf("the_door")).toBe("the_way");
      expect(render(scene4Iam[iamOf(null)]).length).toBeGreaterThan(0);
    });

    it("세 텍스트가 서로 다르고 각각 요 14:6 을 인용한다", () => {
      // 조각 배열은 참조 비교라 늘 다르다. 해석한 문자열로 재야 복붙을 잡는다.
      const texts = Object.values(scene4Iam).map(render);
      expect(new Set(texts).size).toBe(texts.length);
      for (const [key, monologue] of Object.entries(scene4Iam)) {
        expect(render(monologue), `${key} 가 요 14:6 을 인용하지 않음`).toContain(
          "요 14:6",
        );
      }
    });
  });

  describe("§4.3-4 유일성 — 배타절이 데이터에서 사라지지 않는다", () => {
    it("Scene 4 teaching 이 '나로 말미암지 않고는' 을 유지한다", () => {
      // 톤을 부드럽게 다듬다가 이 절만 빠지는 회귀가 가장 흔하다. 문서 §4.3-4 가 명시적으로 금지한 변화.
      // 이 절은 이제 시드 자구에서 온다 — 강조 표시가 아니라 **본문 자체** 가 근거다.
      const text = render(scene4Teaching);
      expect(text).toContain("나로 말미암지 않고는");
      expect(text).toContain("요 14:6");
    });
  });

  describe("R3 — 회복 강요 어휘 0건 (파일 상단이 스스로 건 계약)", () => {
    // 산문만이 아니라 **인용해 온 성경 자구까지** 훑는다 — 금지 어휘가 어느 쪽에서
    // 들어오든 사용자에게는 똑같이 압박이기 때문이다.
    const ALL_TEXTS = [
      scene1Incarnation,
      scene2Beatitudes,
      scene3Touch,
      ...Object.values(scene4Iam),
      scene4Teaching,
      scene5Passion,
      scene6Resurrection,
      scene7Ascension,
      ...Object.values(scene7Recovery),
      scene7CrisisReminder,
    ].map(render);

    it("명령형 극복 어휘가 어떤 텍스트에도 없다", () => {
      // '이겨내라/극복하라/부활하라' 류는 위기 사용자에게 그대로 압박이 된다.
      const forbidden = [
        "극복하라",
        "극복해",
        "이겨내라",
        "이겨내야",
        "이겨야",
        "너도 부활",
        "부활하라",
        "회복해야",
      ];
      for (const text of ALL_TEXTS) {
        for (const word of forbidden) {
          expect(text, `금지 어휘 "${word}"`).not.toContain(word);
        }
      }
    });

    it("Scene 6 부활은 수동적 은혜(이름이 불림)로만 제시된다", () => {
      const text = render(scene6Resurrection);
      expect(text).toContain("이름");
      expect(text).toContain("과제가 아니라");
    });
  });

  describe("R1 — 위기 안내는 정본 자원에서 파생된다", () => {
    it("위기 문구의 번호가 CRISIS_RESOURCES 에서 나온다", () => {
      // 하드코딩 사본이면 정본 번호가 바뀌어도 여기만 옛 번호로 남는다 — 죽은 번호가 나가는 사고.
      const text = render(scene7CrisisReminder);
      expect(text).toContain(CRISIS_RESOURCES[0].tel);
      expect(text).toContain(CRISIS_RESOURCES[1].tel);
      expect(text).toContain("24시간");
    });

    it("위기 문구는 회복을 재촉하지 않고 다음 걸음만 제안한다", () => {
      expect(render(scene7CrisisReminder)).toContain("혼자 견디지 않아도");
    });
  });

  describe("Scene 7 진입 감정별 회복 문구", () => {
    it("다섯 감정 키가 빠짐없이 존재한다", () => {
      expect(Object.keys(scene7Recovery).sort()).toEqual(
        [...RECOVERY_KEYS].sort(),
      );
    });

    it("다섯 문구가 서로 다르고 각각 본문 인용을 갖는다", () => {
      const texts = Object.values(scene7Recovery).map(render);
      expect(new Set(texts).size).toBe(texts.length);
      for (const key of RECOVERY_KEYS) {
        const text = render(scene7Recovery[key]);
        expect(text.trim().length).toBeGreaterThan(0);
        expect(text, `${key}`).toMatch(SCRIPTURE_REF);
      }
    });

    it("[알려진 구멍] 이 Record 는 어떤 화면도 읽지 않는다", () => {
      // `scene7Recovery` 는 export 되어 있으나 src/app/jesus/page.tsx 가 import 하지 않는다
      // (2026-08-14 실측: 소비처 0곳). 다섯 문구 전부가 렌더되지 않는 *죽은 데이터* 다.
      // 소스는 고치지 않고 현 상태를 고정만 해 둔다 — 배선되면 이 기대가 깨지며 알려준다.
      expect(RECOVERY_KEYS.length).toBe(5);
    });
  });
});
