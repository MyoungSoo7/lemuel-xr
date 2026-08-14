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
 * ── 왜 500 이 아니라 280 인가 (2026-08-15 실측) ──
 * 위 500 은 백엔드 검증기(@Size)만 보고 잡은 값이다. 그런데 그 앞에 *두 번째 천장*
 * 이 있다 — `tts.timeout-seconds:300`. 조각이 아무리 검증을 통과해도 300초 안에
 * 합성이 안 끝나면 백엔드가 결과를 버리고 job 을 실패시킨다.
 *
 * 운영 환경에서 직접 재 봤다. 한국어 400자 = 오디오 67.7초 = 합성 298.5초
 * (RTF 4.41). 사이드카는 **성공**해서 200 을 돌려줬는데, 백엔드가 1.5초 먼저
 * 타임아웃을 때려 다 만들어진 오디오를 버렸다. *상한 500 은 애초에 도달할 수 없는
 * 값이었다.*
 *
 * 그동안 안 터진 이유는 그 길이 글들이 대개 캐시에 데워져 있어서다. 캐시가 식은
 * 조합만 조용히 버튼이 사라졌다 — 모세 3씬 카드 32조합 중 18개가 390자 넘는
 * 조각을 가졌고, 아래 최악 속도로 재면 21개가 *순수 합성 시간만으로* 300초를 넘는다.
 *
 * 상한을 280 으로 잡은 근거는 세 가지다.
 * 1. 최악 조각이 280자 = 242초라 타임아웃까지 19% 여유가 남는다 (300자였다면
 *    294자 = 254초로 15%). 32조합 전부가 안전해진다.
 * 2. **캐시가 하나도 안 식는다.** 300 → 280 으로 내려도 나오는 조각 텍스트 15개가
 *    전부 300 일 때 나오던 18개 안에 이미 들어 있다. 새로 식는 항목 0개 —
 *    이 값이 270 이나 260 이 아닌 이유가 이거다.
 * 3. 첫 조각이 짧을수록 첫 소리가 빨리 난다. 합성은 재생보다 4배쯤 느려서 중간
 *    대기는 어차피 생기므로, 앞을 짧게 가져가는 쪽이 체감이 낫다.
 *
 * 영어는 같은 글자 수에서 오디오가 더 짧으므로 한국어 기준인 이 값이 곧 최악이다.
 *
 * ── 상한 이하는 절대 쪼개지 않는다 ──
 * 캐시 키가 sha256(본문) 이라, 쪼개는 순간 그 문장은 *다른 항목* 이 된다. 무조건
 * 쪼개면 이미 데워 둔 캐시가 전부 미스로 바뀐다. 그래서 짧은 글은 종전과 한 바이트도
 * 다르지 않게 통짜로 보내고, 상한을 넘는 글만 나눈다.
 *
 * 경계는 문단(빈 줄) → 줄 → 문장 → (그래도 길면) 글자 순으로 양보한다. 낭독이라
 * 가능한 한 뜻이 끊기지 않는 자리에서 쉬어야 한다.
 */

/**
 * 백엔드 `SynthesizeRequest.text` 의 @Size(max = 500). 넘기면 400 E_VALIDATION 이다.
 * 계약상의 천장일 뿐, 실제로 여기까지 보내면 안 된다 — 아래 [TTS_MAX_CHARS] 참고.
 */
export const TTS_BACKEND_MAX_CHARS = 500;

/**
 * 실제로 쓰는 상한. 검증기가 아니라 **합성 타임아웃**(`tts.timeout-seconds:300`)이
 * 정하는 값이다. 최악 조각 280자 × [TTS_SECONDS_PER_CHAR_WORST] ≈ 242초로
 * 타임아웃까지 19% 여유를 남긴다.
 *
 * 올리려면 타임아웃부터 올려야 한다. 둘 중 하나만 만지면 다시 "사이드카는 성공했는데
 * 백엔드가 버리는" 상태로 돌아간다. 내리는 것도 공짜가 아니다 — 캐시 키가
 * sha256(본문) 이라 조각 텍스트가 바뀌면 그만큼 다시 식는다. 280 은 그 이탈이
 * 0 이 되는 자리라서 고른 값이다.
 */
export const TTS_MAX_CHARS = 280;

/**
 * 합성 시간(초/자) — 한국어, CPU-only XTTS-v2. **관측된 최악값**이다.
 *
 * 안전 예산을 재는 용도라 평균이 아니라 최악을 쓴다. 2026-08-15 운영 실측 3건:
 * - 280자 → 242.1초 = 0.865 (인용부호·괄호·말줄임이 많은 문단)
 * - 204자 → 156.3초 = 0.766
 * - 268자 → 183.3초 = 0.684
 *
 * 같은 글자 수라도 문장부호가 많으면 오디오가 길어져 편차가 크다. 그래서 평균
 * (약 0.77)이 아니라 위쪽 끝을 기준으로 상한을 잡는다.
 */
export const TTS_SECONDS_PER_CHAR_WORST = 242.1 / 280;

/** 백엔드가 사이드카를 기다려 주는 시간(초). `tts.timeout-seconds` 기본값. */
export const TTS_TIMEOUT_SECONDS = 300;

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
