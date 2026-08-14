import { describe, expect, it } from "vitest";
import { splitForTts, TTS_MAX_CHARS } from "./splitForTts";
import {
  buildScene3Echo,
  scene3AssignmentsToPattern,
  scene3CardResponses,
} from "@/lib/content/moses-monologues";

describe("splitForTts", () => {
  it("상한 이하는 원문 그대로 한 조각이다 — 캐시 키가 바뀌면 안 된다", () => {
    // 이게 이 모듈의 가장 중요한 계약이다. 짧은 글까지 쪼개면 이미 합성해 둔
    // 캐시(sha256(본문))가 전부 미스로 바뀌어 첫 재생이 다시 수십 초가 된다.
    const text = "여호와는 나의 목자시니\n\n내게 부족함이 없으리로다.";
    expect(splitForTts(text)).toEqual([text]);
  });

  it.each([
    ["빈 문자열", ""],
    ["공백뿐", "   \n\n  "],
  ])("읽을 것이 없으면(%s) 빈 배열", (_이름, text) => {
    expect(splitForTts(text)).toEqual([]);
  });

  it("상한을 넘으면 문단(빈 줄) 경계에서 나뉜다", () => {
    const a = "가".repeat(40);
    const b = "나".repeat(40);
    const c = "다".repeat(40);
    expect(splitForTts([a, b, c].join("\n\n"), 90)).toEqual([
      `${a}\n\n${b}`,
      c,
    ]);
  });

  it("문단 하나가 상한을 넘으면 줄 경계로 더 나눈다", () => {
    const a = "가".repeat(60);
    const b = "나".repeat(60);
    expect(splitForTts(`${a}\n${b}`, 100)).toEqual([a, b]);
  });

  it("줄 하나가 상한을 넘으면 문장 경계로 더 나눈다", () => {
    const one = "가".repeat(60) + ".";
    const two = "나".repeat(60) + ".";
    expect(splitForTts(`${one} ${two}`, 100)).toEqual([one, two]);
  });

  it("어떤 경계도 없으면 글자 수로 자른다 — 최후 수단", () => {
    expect(splitForTts("가".repeat(250), 100)).toEqual([
      "가".repeat(100),
      "가".repeat(100),
      "가".repeat(50),
    ]);
  });

  it("어떤 입력이든 모든 조각이 상한 이하다", () => {
    const 표본 = [
      "가".repeat(1200),
      Array.from({ length: 30 }, (_, i) => `${i}번째 문장입니다.`).join(" "),
      Array.from({ length: 20 }, () => "문단".repeat(30)).join("\n\n"),
      "짧다",
    ];
    for (const t of 표본) {
      for (const chunk of splitForTts(t, 100)) {
        expect(chunk.length).toBeLessThanOrEqual(100);
      }
    }
  });

  it("나눠도 글자는 하나도 잃지 않는다", () => {
    const text = Array.from({ length: 12 }, (_, i) =>
      `${i}번 문단입니다. `.repeat(6),
    ).join("\n\n");
    const 원문 = text.replace(/\s+/g, "");
    const 조각합 = splitForTts(text, 200).join("").replace(/\s+/g, "");
    expect(조각합).toBe(원문);
  });

  /**
   * 이 버그의 실물이다 — 모세 3씬 echo 는 결과문 뒤에 *내려놓은 카드마다* 응답을
   * 잇는다. 32조합 중 7개가 500자를 넘어 400 을 맞고, 버튼이 조용히 사라졌다.
   * 카드를 많이 내려놓을수록 길어지므로, 씬이 권하는 선택을 한 사람만 소리를
   * 못 듣는 구조였다.
   */
  describe("모세 3씬 echo — 회귀의 실물", () => {
    const cards = Object.keys(scene3CardResponses);
    const 조합 = Array.from({ length: 1 << cards.length }, (_, mask) =>
      Object.fromEntries(
        cards.map((c, i) => [c, mask & (1 << i) ? "throw" : "heart"]),
      ),
    ) as Record<string, "throw" | "heart">[];

    it("전수 32조합이 모두 상한 이하로 나뉜다", () => {
      let 넘던_조합 = 0;
      for (const assignments of 조합) {
        const text = buildScene3Echo(
          scene3AssignmentsToPattern(assignments),
          assignments,
        );
        if (text.length > TTS_MAX_CHARS) 넘던_조합 += 1;
        for (const chunk of splitForTts(text)) {
          expect(chunk.length).toBeLessThanOrEqual(TTS_MAX_CHARS);
        }
      }
      // 고칠 것이 실제로 있었음을 못박아 둔다. 콘텐츠가 짧아져 0이 되면 이 테스트가
      // 지켜 주는 것이 없어지므로 그때 다시 판단하라는 뜻이다.
      expect(넘던_조합).toBe(7);
    });

    it("전부 내려놓기(716자)는 여러 조각이 되고, 전부 품기는 통짜 그대로다", () => {
      const 전부_내려놓기 = Object.fromEntries(
        cards.map((c) => [c, "throw"]),
      ) as Record<string, "throw">;
      const 전부_품기 = Object.fromEntries(
        cards.map((c) => [c, "heart"]),
      ) as Record<string, "heart">;

      const 긴글 = buildScene3Echo("all_throw", 전부_내려놓기);
      expect(긴글.length).toBeGreaterThan(TTS_MAX_CHARS);
      expect(splitForTts(긴글).length).toBeGreaterThan(1);

      const 짧은글 = buildScene3Echo("all_heart", 전부_품기);
      expect(짧은글.length).toBeLessThanOrEqual(TTS_MAX_CHARS);
      expect(splitForTts(짧은글)).toEqual([짧은글]);
    });
  });
});
