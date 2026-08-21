import { describe, expect, it } from "vitest";
import {
  splitForTts,
  TTS_BACKEND_MAX_CHARS,
  TTS_MAX_CHARS,
  TTS_SECONDS_PER_CHAR_WORST,
  TTS_TIMEOUT_SECONDS,
} from "./splitForTts";
import {
  buildScene3Echo,
  scene3AssignmentsToPattern,
  scene3CardResponses,
} from "@/lib/content/moses-monologues";
import { render } from "@/test/seed-passages";

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

    /**
     * echo 는 이제 산문 + 인용 *조각* 이다(`scripture-quote.ts`) — 성경 자구는
     * `/api/scripture` 가 채운다. TTS 가 읽는 것은 그 자구까지 채워진 문자열이므로,
     * 길이·조각 수는 반드시 해석한 뒤에 재야 한다. 조각 배열의 길이를 재면 문단 수를
     * 재는 셈이라 이 파일의 모든 수가 무의미해진다.
     */
    const echoText = (assignments: Record<string, "throw" | "heart">) =>
      render(buildScene3Echo(scene3AssignmentsToPattern(assignments), assignments));

    it("전수 32조합이 모두 상한 이하로 나뉜다", () => {
      let 넘던_조합 = 0;
      for (const assignments of 조합) {
        const text = echoText(assignments);
        if (text.length > TTS_BACKEND_MAX_CHARS) 넘던_조합 += 1;
        for (const chunk of splitForTts(text)) {
          expect(chunk.length).toBeLessThanOrEqual(TTS_MAX_CHARS);
        }
      }
      // 고칠 것이 실제로 있었음을 못박아 둔다. 콘텐츠가 짧아져 0이 되면 이 테스트가
      // 지켜 주는 것이 없어지므로 그때 다시 판단하라는 뜻이다.
      // 7 → 8: 2026-08-21 출 4:14~16 인용을 개역개정 축자로 복원하며 send_other 응답이
      // 길어졌다("할 말을" → "너희들이 행할 일을"). 원문 대조가 콘텐츠 길이를 늘리는
      // 방향으로 작동하므로, 이 수는 앞으로도 인용 교정마다 올라갈 수 있다.
      expect(넘던_조합).toBe(8);
    });

    /**
     * 두 번째 층이다. 위 테스트는 *검증기* 400 만 막아 준다. 실제로 사람이 못 듣던
     * 이유는 그게 아니라 합성 타임아웃이었다 — 사이드카는 298.5초에 성공했는데
     * 백엔드가 300초에 먼저 포기하고 다 만든 오디오를 버렸다. 검증기만 보면 통과인
     * 조각이 런타임에 통째로 사라지므로, 시간 예산도 같이 못박는다.
     */
    it("전수 32조합의 모든 조각이 합성 타임아웃 안에 끝난다", () => {
      const 예산초 = TTS_TIMEOUT_SECONDS * 0.9; // 10% 는 큐·전송 몫으로 남긴다
      for (const assignments of 조합) {
        const text = echoText(assignments);
        for (const chunk of splitForTts(text)) {
          expect(chunk.length * TTS_SECONDS_PER_CHAR_WORST).toBeLessThan(
            예산초,
          );
        }
      }
    });

    /**
     * 상한을 내리는 것도 공짜가 아니다 — 조각 텍스트가 바뀌면 sha256 캐시 키가
     * 바뀌어 이미 데워 둔 항목이 미스가 된다. 280 을 고른 이유가 바로 "여기까지는
     * 이탈이 0" 이어서이므로, 그 성질을 테스트로 못박아 둔다. 더 내리고 싶어지면
     * 이 테스트가 먼저 대가를 보여 준다.
     */
    it("300 → 280 으로 내려도 새로 식는 조각이 없다", () => {
      const 조각들 = (max: number) => {
        const out = new Set<string>();
        for (const assignments of 조합) {
          splitForTts(echoText(assignments), max).forEach((c) => out.add(c));
        }
        return out;
      };
      const 이전 = 조각들(300);
      const 지금 = 조각들(TTS_MAX_CHARS);
      const 새로_식는 = [...지금].filter((c) => !이전.has(c));
      expect(새로_식는).toEqual([]);
    });

    it("고치기 전 상한(500자)이었다면 절반 넘는 조합이 타임아웃에 걸렸다", () => {
      // 이 숫자가 이 커밋이 무엇을 고쳤는지에 대한 유일한 기록이다. 상한을 되돌리면
      // 위 테스트가 깨지는 게 아니라 *이 테스트* 가 먼저 이유를 말해 준다.
      // 여유분 없이 타임아웃 그대로 재는 것에 주의 — "여유가 모자란" 조합이 아니라
      // "실제로 못 끝내는" 조합의 수다.
      const 못_끝내는 = 조합.filter((assignments) => {
        return splitForTts(echoText(assignments), TTS_BACKEND_MAX_CHARS).some(
          (c) => c.length * TTS_SECONDS_PER_CHAR_WORST >= TTS_TIMEOUT_SECONDS,
        );
      });
      // 최악 속도(0.865초/자) 기준 21개. 평균에 가까운 0.746 으로 재면 15개였다 —
      // 즉 이 수는 "얼마나 느린 문장이 걸리느냐"에 따라 15~21 사이에서 움직인다.
      // 어느 쪽이든 절반 안팎이 못 듣고 있었다는 결론은 같다.
      expect(못_끝내는).toHaveLength(21);
    });

    it("전부 내려놓기(716자)는 여러 조각이 되고, 전부 품기는 통짜 그대로다", () => {
      const 전부_내려놓기 = Object.fromEntries(
        cards.map((c) => [c, "throw"]),
      ) as Record<string, "throw">;
      const 전부_품기 = Object.fromEntries(
        cards.map((c) => [c, "heart"]),
      ) as Record<string, "heart">;

      const 긴글 = render(buildScene3Echo("all_throw", 전부_내려놓기));
      expect(긴글.length).toBeGreaterThan(TTS_MAX_CHARS);
      expect(splitForTts(긴글).length).toBeGreaterThan(1);

      const 짧은글 = render(buildScene3Echo("all_heart", 전부_품기));
      expect(짧은글.length).toBeLessThanOrEqual(TTS_MAX_CHARS);
      expect(splitForTts(짧은글)).toEqual([짧은글]);
    });
  });
});
