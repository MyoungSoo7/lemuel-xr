import { readFileSync, readdirSync } from "node:fs";
import { resolve } from "node:path";
import {
  resolveMonologue,
  type Monologue,
  type PassageTexts,
} from "@/lib/content/scripture-quote";

/**
 * 테스트가 `/api/scripture` 대신 쓰는 **절 자구 공급원** — Flyway 시드 SQL 을 그대로 읽는다.
 *
 * ─────────────── 왜 픽스처 파일을 안 만드나 ───────────────
 *
 * 모놀로그를 API 로 옮긴 이유가 "성경 사본이 두 벌이라 한쪽만 고쳐졌다" 였다
 * (`scripture-quote.ts` 머리말). 그 상태에서 프론트에 자구 픽스처 JSON 을 새로
 * 커밋하면 **세 벌째** 가 생긴다. 그래서 여기서는 사본을 만들지 않고, API 가 실제로
 * 퍼내는 그 테이블을 채우는 시드 SQL 을 직접 파싱한다.
 *
 * 그 대가로 이 파일은 SQL 파서를 하나 더 갖는다(파이썬 쪽 `scripture_text_check.py`
 * 의 `seeded_rows` 와 같은 규칙). 두 파서가 어긋나면 **여기가 먼저 시끄럽게 죽는다** —
 * 인용이 여는 참조를 못 찾으면 `passageTexts()` 가 그 키를 안 실어 주고, 그 모놀로그를
 * 쓰는 테스트가 전부 실패한다. 조용히 초록이 되는 경로가 없다.
 *
 * 시드가 최종 상태가 아니라는 함정이 하나 있다: 뒤 마이그레이션의 `UPDATE ... SET text`
 * 교정이 앞의 INSERT 를 덮는다(예: `V20260821090000` 이 각주 부스러기를 씻었다).
 * 그래서 INSERT 를 다 모은 뒤 UPDATE 를 순서대로 덧씌운다.
 */
// vitest 는 `frontend/` 에서 돈다(`npm test` · CI 의 working-directory 둘 다).
// jsdom 환경에서는 `import.meta.url` 이 http URL 이라 파일 경로로 못 쓴다.
const MIGRATIONS = resolve(
  process.cwd(),
  "../backend/src/main/resources/db/migration",
);

/** INSERT 한 행. book/book_code 열 순서가 마이그레이션마다 달라 사이는 건너뛴다. */
const ROW_RE =
  /\('([a-z0-9]+)-(\d+):(\d+)',\s*'(\w+)',[\s\S]*?,\s*(\d+),\s*(\d+),\s*(\d+|NULL),(?:\s*--[^\n]*)?\s*('(?:[^']|'')*')/g;

const UPDATE_RE =
  /UPDATE scripture_passages\s+SET text = ('(?:[^']|'')*')\s+WHERE reference = '([^']+)' AND translation = '(\w+)'/g;

function unquote(sql: string): string {
  return sql.slice(1, -1).replace(/''/g, "'");
}

function migrationSql(): Array<[string, string]> {
  return readdirSync(MIGRATIONS)
    .filter((name) => name.endsWith(".sql"))
    .sort()
    .map((name) => [name, readFileSync(resolve(MIGRATIONS, name), "utf-8")]);
}

/**
 * `fetchScripturePassage` 의 기본 번역본과 **같아야 한다**. 다르면 테스트만 통과하고
 * 화면은 다른 역본을 읽는다 — #84 가 정확히 그 사고였다.
 */
const TRANSLATION = "rev"; // 개역개정

function passageTexts(): PassageTexts {
  const texts: Record<string, string> = {};
  for (const [, sql] of migrationSql()) {
    for (const m of sql.matchAll(ROW_RE)) {
      const [, book, chapter, verse, trans, , , , body] = m;
      if (trans !== TRANSLATION) continue;
      texts[`${book}-${chapter}:${verse}`] = unquote(body);
    }
  }
  for (const [, sql] of migrationSql()) {
    for (const [, body, ref, trans] of sql.matchAll(UPDATE_RE)) {
      if (trans === TRANSLATION && ref in texts) texts[ref] = unquote(body);
    }
  }
  return texts;
}

/** 모듈 레벨에서 한 번만 읽는다 — 마이그레이션 100여 개를 테스트마다 다시 파싱할 이유가 없다. */
export const SEED_TEXTS: PassageTexts = passageTexts();

/**
 * `fetchScripturePassage` 의 가짜 구현 — 시드가 아는 참조면 그 자구를, 모르면 404 처럼 던진다.
 *
 * 인물 페이지 테스트는 이걸 `vi.mock("@/lib/api/content")` 에 물린다. 자구를 테스트에
 * 직접 적지 않는 게 요점이다: 페이지가 `useMonologueTexts` → `MonologueText` 로 이어지는
 * 배선을 실제로 타면서도, 흐르는 자구는 API 가 퍼내는 그 시드와 같다.
 *
 * 모르는 참조에서 조용히 빈 문자열을 돌려주면 화면은 인용이 빠진 문장을 그리고 테스트는
 * 초록이 된다. `GetScripturePassageUseCase.byReference` 가 fallback 없이
 * `E_SCRIPTURE_NOT_FOUND` 를 던지는 것과 같은 이유로, 여기서도 던진다.
 */
export function seedPassage(reference: string, translation = TRANSLATION) {
  const text = SEED_TEXTS[reference];
  if (translation !== TRANSLATION || text === undefined) {
    return Promise.reject(
      new Error(`E_SCRIPTURE_NOT_FOUND: ${reference} (${translation})`),
    );
  }
  const [book, rest] = reference.split("-");
  const [chapter, verse] = rest.split(":");
  return Promise.resolve({
    id: 0,
    reference,
    translation,
    book,
    bookCode: book,
    chapter: Number(chapter),
    verseStart: Number(verse),
    verseEnd: null,
    text,
    themeTags: null,
    characterTags: null,
  });
}

/**
 * 모놀로그를 화면 문자열로. **해석에 실패하면 던진다** — 테스트에서 `null` 을 받아
 * 넘기면 "인용이 통째로 빠진 문장" 을 검사하게 되고, 그건 이 리포가 없애려던 상태다.
 */
export function render(monologue: Monologue): string {
  const text = resolveMonologue(monologue, SEED_TEXTS);
  if (text === null) {
    throw new Error(
      `모놀로그 해석 실패 — 시드에 없는 참조이거나 클립 구간이 절 밖이다: ${JSON.stringify(monologue)}`,
    );
  }
  return text;
}
