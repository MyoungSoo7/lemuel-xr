import { readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import * as joseph from "./joseph-monologues";
import * as moses from "./moses-monologues";
import * as david from "./david-monologues";
import * as jesus from "./jesus-monologues";
import { isQuote, type Segment } from "./scripture-quote";
import { render } from "@/test/seed-passages";
import { entriesOf } from "@/test/monologue-shape";

/**
 * `docs/monologue-quotes.lock.txt` — 모놀로그가 **화면에 실제로 내는 글** 의 기준선.
 *
 * ─────────────── 왜 필요한가 ───────────────
 *
 * 모놀로그의 성경 인용은 이제 자구가 아니라 `["1sam-17:47", 11, 15, 1]` 같은 낱말
 * 인덱스다(`scripture-quote.ts`). 이 표기의 값은 사본이 사라진다는 것이고, 대가는
 * **사람이 코드만 보고는 무엇이 뜨는지 알 수 없다** 는 것이다. 인덱스를 하나 잘못
 * 옮겨도 리뷰어 눈에는 숫자 하나 바뀐 diff 로만 보인다.
 *
 * 그래서 해석 결과를 생성해 커밋한다. 인덱스가 밀리면 이 파일의 diff 에 *문장* 으로
 * 드러난다. 시드 자구가 바뀌어도 마찬가지다 — 그건 사고가 아니라 정상적인 교정일
 * 수 있지만, 교정이 인용 47건 중 어디를 건드렸는지는 이 diff 말고는 볼 곳이 없다.
 *
 * ─────────────── 갱신 ───────────────
 *
 *     UPDATE_MONOLOGUE_LOCK=1 npx vitest run src/lib/content/monologue-lock.test.ts
 *
 * 갱신 후 **diff 를 읽는 것까지가 절차** 다. 기준선을 눈감고 덮어쓰면 이 파일은
 * 아무것도 지키지 않는다 — 리포의 다른 기준선(`scripts/gates/BASELINE.json`) 과 같다.
 *
 * 자구는 `/api/scripture` 가 퍼내는 그 시드에서 온다(`src/test/seed-passages.ts`).
 * 프론트에 자구 사본을 새로 두지 않기 위해서다.
 */
const LOCK = resolve(process.cwd(), "../docs/monologue-quotes.lock.txt");

const HEADER = `# 모놀로그 해석 기준선 — **생성물이다. 손으로 고치지 마라.**
#
# 각 항목은 \`*-monologues.ts\` 의 export 하나를 개역개정(rev) 시드 자구로 해석한
# 결과, 즉 화면에 뜨고 TTS 가 읽는 그 문자열이다. 성경 인용은 소스에 자구가 없고
# 낱말 인덱스만 있으므로(scripture-quote.ts), 인덱스가 밀렸는지 시드가 바뀌었는지는
# 이 파일의 diff 로만 눈에 보인다.
#
# 갱신: UPDATE_MONOLOGUE_LOCK=1 npx vitest run src/lib/content/monologue-lock.test.ts
# 갱신했으면 diff 를 읽어라. 그게 이 파일의 전부다.
`;

const MODULES: Array<[string, Record<string, unknown>]> = [
  ["joseph-monologues.ts", joseph as unknown as Record<string, unknown>],
  ["moses-monologues.ts", moses as unknown as Record<string, unknown>],
  ["david-monologues.ts", david as unknown as Record<string, unknown>],
  ["jesus-monologues.ts", jesus as unknown as Record<string, unknown>],
];

function build(): string {
  const parts = [HEADER];
  for (const [file, mod] of MODULES) {
    parts.push(`\n════════════ ${file} ════════════\n`);
    for (const [name, monologue] of entriesOf(mod)) {
      parts.push(`\n──── ${name} ────\n${render(monologue)}\n`);
    }
  }
  return parts.join("");
}

describe("모놀로그 해석 기준선", () => {
  it("docs/monologue-quotes.lock.txt 와 한 글자도 다르지 않다", () => {
    const built = build();
    if (process.env.UPDATE_MONOLOGUE_LOCK) {
      writeFileSync(LOCK, built, "utf-8");
    }
    expect(readFileSync(LOCK, "utf-8")).toBe(built);
  });

  it("모든 인용이 자구를 얻는다 — 산문만 남은 모놀로그가 없다", () => {
    // `render` 는 해석 실패 시 던지므로 위 테스트가 이미 이걸 재지만, 실패했을 때
    // "어느 인용이" 를 짚어 주는 건 이쪽이다. 기준선 diff 는 파일 전체를 보여 준다.
    for (const [file, mod] of MODULES) {
      for (const [name, monologue] of entriesOf(mod)) {
        for (const segment of monologue as readonly Segment[]) {
          if (!isQuote(segment)) continue;
          expect(
            render([segment]),
            `${file}:${name} 의 ${segment.cite}`,
          ).not.toBe("");
        }
      }
    }
  });
});
