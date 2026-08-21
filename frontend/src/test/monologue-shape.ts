import type { Monologue, Segment } from "@/lib/content/scripture-quote";

/**
 * `*-monologues.ts` 모듈의 **모양을 런타임에 판별하는** 도구. 두 테스트가 같이 쓴다 —
 * `monologue-contract.test.ts` 는 이 판별로 규약을 *강제* 하고,
 * `monologue-lock.test.ts` 는 같은 판별로 기준선에 넣을 export 를 *고른다*.
 *
 * 두 곳에 각자 두면 한쪽만 고쳐져 "기준선에는 들어갔는데 규약 검사에서는 빠지는" 형태로
 * 어긋난다. 실제로 그렇게 어긋났을 때 눈에 띄는 증상은 기준선 파일에 정체불명 항목이
 * 하나 늘어나는 것뿐이라, 리뷰에서 잡히지 않는다.
 */

/** 참조 목록 export 의 이름. 네 모듈의 관례이자 페이지가 `useMonologueTexts` 에 넘기는 값. */
export const REFS_EXPORT = "allRefs";

/**
 * 모놀로그인가 — 배열이고 원소가 전부 산문(문자열) 아니면 인용(`{cite, clips}`).
 *
 * 주의: `allRefs` 같은 `string[]` 도 이 조건을 만족한다. 구조만으로는 구별할 수 없어
 * 이름으로 뺀다(`entriesOf`). 참조를 `Quote` 로 감싸서 구조적으로 갈라놓는 방법도 있지만,
 * 그건 화면과 무관한 포장을 소스에 남기는 대가를 치른다.
 */
export function isMonologue(value: unknown): value is Monologue {
  return (
    Array.isArray(value) &&
    value.length > 0 &&
    value.every(
      (s: unknown) =>
        typeof s === "string" ||
        (typeof s === "object" && s !== null && "cite" in s && "clips" in s),
    )
  );
}

/** `Record<string, Monologue>` 인가 — 선택지별 분기 묶음. */
export function isMonologueRecord(
  value: unknown,
): value is Record<string, Monologue> {
  if (typeof value !== "object" || value === null || Array.isArray(value))
    return false;
  const values = Object.values(value);
  return values.length > 0 && values.every(isMonologue);
}

/**
 * 모듈의 모놀로그 export 를 **전수로** 훑어 `[이름, 모놀로그]` 로 편다. 분기 묶음은
 * `이름.키` 로 평탄화한다. 새 모놀로그를 추가하면 목록에 손대지 않아도 자동으로 들어온다 —
 * 손으로 유지하는 목록은 빠뜨린 그 하나를 못 지킨다.
 */
export function entriesOf(
  mod: Record<string, unknown>,
): Array<[string, Monologue]> {
  const out: Array<[string, Monologue]> = [];
  for (const [name, value] of Object.entries(mod).sort(([a], [b]) =>
    a < b ? -1 : 1,
  )) {
    if (name === REFS_EXPORT) continue;
    if (isMonologue(value)) out.push([name, value]);
    else if (isMonologueRecord(value))
      for (const [key, m] of Object.entries(value))
        out.push([`${name}.${key}`, m]);
  }
  return out;
}

/** 모놀로그 안의 인용 조각만. 규약 검사가 인용 유무를 셀 때 쓴다. */
export function quotesOf(monologue: Monologue): Segment[] {
  return (monologue as readonly Segment[]).filter(
    (s): s is Exclude<Segment, string> => typeof s !== "string",
  );
}
