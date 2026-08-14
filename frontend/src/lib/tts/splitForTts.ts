/**
 * 나레이션 본문을 TTS 요청 단위로 나눈다.
 *
 * ── 왜 필요한가 ──
 * 백엔드 `/api/tts/synthesize` 의 `text` 는 500자 상한이다(@Size(max = 500)).
 * 그 값은 "앱 안 나레이션 185개 전수 측정, 최대 492자" 를 근거로 잡혔는데, 그
 * 조사가 센 것은 소스에 적힌 *문자열 리터럴* 이었다. 화면에 실제로 뜨는 글에는
 * 런타임에 이어 붙는 것이 있다 — 모세 3씬 echo 는 결과문 뒤에 *내려놓은 카드마다*
 * 응답을 잇는다. 카드 배정 32조합을 전수로 재 보니 7개가 상한을 넘었고(최대 716자),
 * 4장 이상 내려놓으면 무조건 걸렸다. 상한을 넘으면 400 이 떨어지고 버튼은
 * `onUnavailable="hide"` 라 조용히 사라진다. 하필 카드를 많이 내려놓은 사람 —
 * 이 씬이 향하는 바로 그 사람 — 만 소리를 못 듣고 있었다.
 *
 * ── 왜 상한을 올리지 않았나 ──
 * 사이드카 워커는 1개다. 716자면 오디오가 2분 넘고 CPU-only XTTS-v2 는 오디오
 * 1초당 CPU 4초를 쓰므로, 그 한 건이 워커를 10분 가까이 독점한다. 그동안 다른
 * 사용자는 전원 대기다. 상한은 워커 1개 구조에 맞게 잡힌 값이라 그대로 둔다.
 *
 * ── 상한 이하는 절대 쪼개지 않는다 ──
 * 캐시 키가 sha256(본문) 이라, 쪼개는 순간 그 문장은 *다른 항목* 이 된다. 무조건
 * 쪼개면 이미 데워 둔 캐시가 전부 미스로 바뀐다. 그래서 짧은 글은 종전과 한 바이트도
 * 다르지 않게 통짜로 보내고, 상한을 넘는 글만 나눈다.
 *
 * 경계는 문단(빈 줄) → 줄 → 문장 → (그래도 길면) 글자 순으로 양보한다. 낭독이라
 * 가능한 한 뜻이 끊기지 않는 자리에서 쉬어야 한다.
 */

/** 백엔드 `SynthesizeRequest.text` 의 @Size(max = 500) 과 같은 값이어야 한다. */
export const TTS_MAX_CHARS = 500;

/** 문장 끝으로 인정하는 자리. 한국어 마침표류 + 닫는 따옴표까지 함께 넘긴다. */
const SENTENCE_END = /(?<=[.!?。？！]["'”’)\]]*)\s+/;

/** 빈 문자열을 걸러 낸 trim 결과만 남긴다. */
function clean(parts: string[]): string[] {
  return parts.map((p) => p.trim()).filter(Boolean);
}

/**
 * 조각들을 순서대로 이어 붙이되 [max] 를 넘지 않게 묶는다.
 * 조각 하나가 이미 [max] 를 넘으면 묶지 않고 그대로 흘려보낸다 (다음 단계가 더 잘게 나눈다).
 */
function pack(parts: string[], joiner: string, max: number): string[] {
  const out: string[] = [];
  let buf = "";
  for (const part of parts) {
    const merged = buf ? buf + joiner + part : part;
    if (buf && merged.length > max) {
      out.push(buf);
      buf = part;
    } else {
      buf = merged;
    }
  }
  if (buf) out.push(buf);
  return out;
}

/** 어떤 경계로도 안 줄어드는 조각을 글자 수로 자른다 — 최후 수단. */
function hardSplit(text: string, max: number): string[] {
  const out: string[] = [];
  for (let i = 0; i < text.length; i += max) out.push(text.slice(i, i + max));
  return out;
}

/**
 * [text] 를 각 [max] 자 이하인 조각들로 나눈다.
 *
 * [max] 이하면 **원문 그대로 담긴 배열 하나** 를 돌려준다 — 캐시 키가 바뀌지 않도록.
 * 빈 문자열/공백뿐이면 빈 배열.
 */
export function splitForTts(
  text: string,
  max: number = TTS_MAX_CHARS,
): string[] {
  const trimmed = text?.trim() ?? "";
  if (!trimmed) return [];
  if (trimmed.length <= max) return [trimmed];

  // 문단(빈 줄) → 줄 → 문장 순으로 경계를 낮춰 가며, 아직 긴 조각만 더 나눈다.
  let chunks = pack(clean(trimmed.split(/\n{2,}/)), "\n\n", max);

  chunks = chunks.flatMap((c) =>
    c.length <= max ? [c] : pack(clean(c.split("\n")), "\n", max),
  );

  chunks = chunks.flatMap((c) =>
    c.length <= max ? [c] : pack(clean(c.split(SENTENCE_END)), " ", max),
  );

  return chunks.flatMap((c) => (c.length <= max ? [c] : hardSplit(c, max)));
}
