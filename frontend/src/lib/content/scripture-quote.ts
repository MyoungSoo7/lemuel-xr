/**
 * 모놀로그 안의 성경 인용 — **자구를 갖지 않고 위치만 갖는다.**
 *
 * ─────────────── 왜 생겼나 (2026-08-21) ───────────────
 *
 * `ScenePassage` 는 원칙 1 을 이렇게 적어 두었다: *본문은 오직 `/api/scripture`
 * 응답에서만 온다. 프론트에 사본을 두지 않는다.* 그런데 같은 화면 위쪽의
 * `*-monologues.ts` 는 정확히 그 사본이었다 — 성경 자구 47건이 TS 문자열 리터럴로
 * 박혀 있었고, DB 의 같은 절과 **두 벌** 로 살았다.
 *
 * 두 벌이라는 게 이론적인 위험이 아니었다. 2026-08-21 전수 대조에서 32건 중 16건이
 * 개역개정과 달랐고(#83), 그 다음 날 화면 아래위가 서로 다른 역본으로 뜨는 것이
 * 드러났다(#84). 두 사고 다 "사본이 둘이라 한쪽만 고쳐졌다" 의 결과다.
 *
 * ─────────────── 왜 절 전체를 받아 오지 않나 ───────────────
 *
 * 인용 47건 중 42건이 **문장 한가운데 박힌 조각** 이다:
 *
 *     "전쟁은 여호와께 속한 것"(삼상 17:47)이다. 네 몫은 나아가는 것…
 *     "내가 곧 길이요" → "…진리요" → "…생명이니" → 절 전체  (요 14:6 을 4단계로 여는 연출)
 *
 * 절 전체를 통째로 끼워 넣으면 이 글이 전부 망가진다. 그래서 인용은 **클립**
 * (`Clip`) 이다 — 절 참조 + 낱말 인덱스 구간. 자구는 런타임에 API 응답에서 잘라 온다.
 *
 * ─────────────── 사람은 이걸 어떻게 검토하나 ───────────────
 *
 * `{ from: 11, to: 15 }` 만 보고 무엇이 화면에 뜨는지 알 수 없다는 게 이 설계의 값이다.
 * 그래서 해석 결과를 `docs/monologue-quotes.lock.txt` 에 **생성해 커밋** 한다
 * (`monologue-lock.test.ts` 가 씀·검사). 인덱스를 잘못 옮기면 그 파일의 diff 로 보인다.
 * 리포의 기준선(baseline) 관용 그대로다.
 *
 * ─────────────── 함정 ───────────────
 *
 * - **낱말 인덱스는 API 자구의 공백 분해에 걸려 있다.** 시드 본문에 각주 번호 같은
 *   군더더기가 섞이면 인덱스가 통째로 밀린다. 실제로 `jn-1:14` 이 "은혜와 4)진리가"
 *   로 시드에 앉아 있었고 `V20260821090000` 이 씻었다.
 * - **없는 절을 인용하면 그 모놀로그는 통째로 실패한다.** 백엔드에 폴백이 없다
 *   (`GetScripturePassageUseCase` 는 `E_SCRIPTURE_NOT_FOUND` 를 던진다). 그래서
 *   `resolveMonologue` 는 자구가 하나라도 비면 `null` 을 돌려주고, 화면은 그걸
 *   **눈에 보이게** 알린다. 산문만 남기고 인용만 빠뜨리면 뜻이 바뀐 문장이 뜬다.
 */

/**
 * 한 절에서 잘라 낼 구간. 낱말 인덱스는 **0 기반 · 반열림**(`from` 포함, `to` 제외),
 * 낱말은 API 자구를 공백으로 나눈 것이다.
 */
export interface Clip {
  /** 시드 참조 키 (`docs/SCRIPTURE-REF-CONVENTION.md` — 예: `1sam-17:47`). */
  ref: string;
  from: number;
  to: number;
  /**
   * 마지막 낱말에서 **앞 n 글자만** 취한다.
   *
   * 한국어 활용어미를 끊는 자리 두 곳뿐이다 — 개역개정 "…전쟁은 여호와께 속한
   * 것인즉" 에서 인용은 "…속한 것" 에서 끝나고(삼상 17:47), "…혀가 둔한 자니이다"
   * 에서 "혀가 둔한 자"(출 4:10) 로 끝난다. 뒤의 어미는 인용문이 아니라 *그 절
   * 자신의 문장* 에 속하므로 문장 안에 끌고 들어오면 비문이 된다.
   */
  lastWordChars?: number;
}

/** 한 인용 — 표기(`(삼상 17:47)`)와 그 표기가 가리키는 클립들. */
export interface Quote {
  /** 화면에 그대로 뜨는 표기. 절 범위 인용이면 `창 41:26~27` 처럼 범위 그대로. */
  cite: string;
  clips: readonly Clip[];
}

/** 모놀로그의 한 조각 — 저자가 쓴 산문이거나, 성경 인용이거나. */
export type Segment = string | Quote;

/**
 * 모놀로그 하나. 산문과 인용이 번갈아 놓인 배열이고, 화면에 뜨는 문자열은
 * `resolveMonologue` 가 API 자구를 채워 만든다.
 */
export type Monologue = readonly Segment[];

type ClipSpec = readonly [
  ref: string,
  from: number,
  to: number,
  lastWordChars?: number,
];

/** 인용 하나를 짧게 적기 위한 생성자. `q("삼상 17:47", ["1sam-17:47", 11, 15, 1])` */
export function q(cite: string, ...clips: ClipSpec[]): Quote {
  return {
    cite,
    clips: clips.map(([ref, from, to, lastWordChars]) =>
      lastWordChars === undefined
        ? { ref, from, to }
        : { ref, from, to, lastWordChars },
    ),
  };
}

export function isQuote(segment: Segment): segment is Quote {
  return typeof segment !== "string";
}

/** 이 모놀로그들이 여는 참조 전부 (중복 제거, 안정 정렬). */
export function refsOf(
  ...sources: ReadonlyArray<Monologue | Record<string, Monologue>>
): string[] {
  const out = new Set<string>();
  for (const source of sources) {
    const monologues: Monologue[] = Array.isArray(source)
      ? [source as Monologue]
      : Object.values(source as Record<string, Monologue>);
    for (const m of monologues) {
      for (const segment of m) {
        if (isQuote(segment)) {
          for (const clip of segment.clips) out.add(clip.ref);
        }
      }
    }
  }
  return [...out].sort();
}

/** 참조 -> API 가 내려 준 절 자구. */
export type PassageTexts = Readonly<Record<string, string | undefined>>;

/**
 * 클립 한 개를 자구로 바꾼다. 구간이 절 밖으로 나가면 `null` — 조용히 잘라 내면
 * 인용이 짧아진 채로 화면에 뜨고, 그게 이 파일이 없애려던 실패 모드다.
 */
export function resolveClip(clip: Clip, text: string): string | null {
  const words = text.split(/\s+/).filter(Boolean);
  if (clip.from < 0 || clip.to > words.length || clip.from >= clip.to) {
    return null;
  }
  const picked = words.slice(clip.from, clip.to);
  if (clip.lastWordChars !== undefined) {
    const last = picked[picked.length - 1];
    if (clip.lastWordChars <= 0 || clip.lastWordChars > last.length)
      return null;
    picked[picked.length - 1] = last.slice(0, clip.lastWordChars);
  }
  return picked.join(" ");
}

/**
 * 인용 하나를 화면 문자열로. 떨어진 클립은 생략부호로 잇는다 — *생략했다는 사실을
 * 독자에게 표시* 하는 것이 `check_monologue_quotes.py` 의 통과 조건이다(SPLICE_NOMARK
 * 는 자구가 전부 원문이어도 실패다).
 */
export function resolveQuote(quote: Quote, texts: PassageTexts): string | null {
  const parts: string[] = [];
  for (const clip of quote.clips) {
    const text = texts[clip.ref];
    if (text === undefined) return null;
    const part = resolveClip(clip, text);
    if (part === null) return null;
    parts.push(part);
  }
  return `"${parts.join(" … ")}"(${quote.cite})`;
}

/**
 * 모놀로그 전체를 화면 문자열로. 인용 자구가 하나라도 없으면 **`null`** —
 * 산문만 그리면 "…라고 하셨다" 같은 문장이 주어 없이 뜬다.
 */
export function resolveMonologue(
  monologue: Monologue,
  texts: PassageTexts,
): string | null {
  const parts: string[] = [];
  for (const segment of monologue) {
    if (typeof segment === "string") {
      parts.push(segment);
      continue;
    }
    const rendered = resolveQuote(segment, texts);
    if (rendered === null) return null;
    parts.push(rendered);
  }
  return parts.join("");
}

/** 산문만 이어 붙인 것 — 인용 자구를 빼고 재는 검사(금지 어휘 등)를 위한 것이다. */
export function proseOf(monologue: Monologue): string {
  return monologue.filter((s): s is string => typeof s === "string").join("");
}
