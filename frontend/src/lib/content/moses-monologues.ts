/**
 * 모세 미션의 *정적* 모놀로그 / 아웃컴 텍스트.
 *
 * 요셉과 동일한 *frontend 직결* 형식 (`joseph-monologues.ts` 참조). backend 는
 * moses.yml 의 `monologue_cache_keys` 만 갖고 있고 `monologues/outcomes/reactions`
 * map 은 없으므로 `DecideSceneUseCase.matchResponseText` 는 moses 에서 null 을 반환한다.
 * 따라서 각 결정의 echo 텍스트는 *frontend fallback* (본 파일) 이 담당한다.
 * 자문 통과 후 backend round-trip + LLM 으로 shape 유지한 채 교체 가능.
 *
 * shape: `<scene><decision> : Monologue`.
 *
 * `Monologue` 는 *산문 + 인용 위치* 의 배열이다 — 성경 자구는 이 파일에 없고
 * `/api/scripture` 응답에서 온다(`scripture-quote.ts` 머리말).
 *
 * 신학 기준 — 합신(개혁주의/웨스트민스터) + 성경만 근거.
 *  - 모세의 소명·해방은 *하나님의 부르심* 에 대한 순종으로 다룬다 (출 3~14).
 *  - 정치적 해방(출애굽)이라도 특정 정파 선동 금지. 해방 신학의 급진주의
 *    (혁명 신학·만인구원) 는 개혁주의 범위 밖 — 배제.
 *  - 섭리(providence)는 인간 책임을 지우지 않는다. "내가 반드시 너와 함께 있으리라"(출 3:12)
 *    는 소명은 두려움을 *없애는* 것이 아니라 *동행하시는* 약속이다.
 *
 * 정신건강 안전선 —
 *  - R2 (고난 미화) 금지: "두려움을 견뎌라 / 참는 것이 미덕" 톤 배제.
 *    두려운 채로 *한 걸음* 을 다룰 뿐, 고통 자체를 의미화하지 않는다.
 *  - R3 (회복 압박) 금지: "믿음으로 두려움을 이겨라" 대신 "두려운 채로 가도 된다".
 *  - R1 (위기) 톤: 결말은 소망을 지금의 회복 속도와 *분리* 해 제시.
 */

import { q, refsOf, type Monologue, type Segment } from "./scripture-quote";

export type Scene2Gesture = "reverence" | "hesitation";
export type Scene3Pattern = "all_throw" | "all_heart" | "mixed";
export type Scene3Card =
  "who_am_i" | "who_sent_me" | "trust_me" | "cant_speak" | "send_other";
export type Scene4Choice = "cast_now" | "hesitate" | "with_aaron";
export type Scene5Gesture = "lift_staff";

/**
 * Scene 1 — 미디안 광야(부름 받기 전 40년). MVP-MOSES-CONTENT.md §1.2 대본.
 * §0.7 톤: 소명 회피 이전의 *기다림·이름 없음* 을 다룬다. R2 가드 — 광야의 무게를
 * 의미화·미화하지 않고, 있는 그대로 앉아 있게 둔다.
 */
export const scene1Wilderness: Monologue = [
  "미디안 광야. 해가 진다. 애굽에서 도망친 지 40년이 지났다.\n\n" +
    "이제 나는 이름 없는 양치기다. 왕자였던 기억도, 도망친 죄도, 시간이 덮었다. " +
    "오늘도 어제와 같고, 내일도 어제와 같을 것이다.\n\n" +
    "부름은 아직 오지 않았다. 다만, 이 기다림의 자리에 잠시 앉아 있어도 된다.",
];

/** Scene 2 — 떨기나무 앞. 신을 벗고 고개를 드는 경외. */
export const scene2Monologues: Record<Scene2Gesture, Monologue> = {
  reverence: [
    "신을 벗는다. 발 아래 땅이 거룩하다(출 3:5). " +
      "떨림은 사라지지 않았지만, 너는 그 떨림을 안고 고개를 들었다. " +
      "경외는 두려움을 없애는 것이 아니라, 두려움 앞에서도 시선을 드는 일이다.",
  ],
  hesitation: [
    "얼굴을 가린다 — 하나님 뵙기가 두려웠다(출 3:6). " +
      "그 머뭇거림은 잘못이 아니다. 부름 앞에서 떨지 않는 사람은 없다. " +
      "다만 불은 여전히 타고, 떨기나무는 사위지 않는다. 부름은 아직 거기 있다.",
  ],
};

/**
 * Scene 3 — 다섯 변명의 카드. 각 카드를 '던진다(throw)' 혹은 '가슴에 품는다(heart)'.
 *  - all_throw : 다섯 변명을 모두 내려놓는다 → 온전한 순종에 가깝다
 *  - all_heart : 다섯 변명을 모두 품는다 → 자기방어에 머문다
 *  - mixed     : 일부는 내려놓고 일부는 품는다 → 실제 모세의 여정
 */
export const scene3Outcomes: Record<Scene3Pattern, Monologue> = {
  all_throw: [
    "다섯 변명을 모두 내려놓았다. ",
    q("출 3:11", ["ex-3:11", 3, 5]),
    ", ",
    q("출 4:10", ["ex-4:10", 22, 25, 1]),
    " — 그 말들이 거짓은 아니다. " +
      "다만 부름의 무게는 네 자격에 달린 것이 아니라, 함께 가시는 분께 달렸다(출 3:12).",
  ],
  all_heart: [
    "다섯 변명을 모두 품에 안았다. 자기방어는 정직한 고백이다 — 너는 정말 두렵다. " +
      "하나님은 그 두려움을 꾸짖어 없애지 않으시고, 아론을 붙여 주신다(출 4:14~16). " +
      "떨림을 안은 채로도 길은 열린다. 지금 다 내려놓지 못해도 괜찮다.",
  ],
  mixed: [
    "어떤 변명은 내려놓고, 어떤 변명은 아직 품고 있다 — 실제 모세가 걸은 길이다. " +
      "순종은 두려움이 다 사라진 뒤에 오는 것이 아니라, 두려움과 함께 시작된다. " +
      "완전하지 않은 걸음도 걸음이다.",
  ],
};

/**
 * Scene 3 — 카드별 떨기나무 응답 (MVP-MOSES-CONTENT.md §3.4).
 * 각 변명 카드를 내려놓거나 품는 순간, 떨기나무가 *본문 인용* 으로 응답한다.
 *  ① who_am_i(정체성)   → 출 3:12
 *  ② who_sent_me(권위)  → 출 3:14  (disputed: E.J. Young 자존성 vs Brueggemann 관계적·미래지향)
 *  ③ trust_me(신뢰)     → 출 4:2~5
 *  ④ cant_speak(능력)   → 출 4:11~12  (능력 평가 ≠ 인격 평가 — R2 학습된 무력감 정당화 차단)
 *  ⑤ send_other(책임)   → 출 4:14~16  (§3.4 결정: 진노 어휘 회피, *동행자 제공* 해결책 톤으로만)
 */
export const scene3CardResponses: Record<Scene3Card, Monologue> = {
  who_am_i: [
    q("출 3:12", ["ex-3:12", 2, 7]),
    '. "내가 누구이기에"라는 물음의 답은 *네가 누구인가* 가 아니라, *누가 함께 가는가* 였다.',
  ],
  who_sent_me: [
    q("출 3:14", ["ex-3:14", 3, 7], ["ex-3:14", 14, 21]),
    ". 이름을 여쭙는 자리에서, 그분은 자기 존재로 답하셨다. " +
      "(이 이름은 '스스로 계신 분'(E.J. Young)으로도, '함께 있으실 분'(Brueggemann)으로도 읽힌다 — 한 줄로 다 담기지 않는 이름이다.)",
  ],
  trust_me: [
    q("출 4:2~5", ["ex-4:2", 3, 8], ["ex-4:3", 2, 5]),
    ". 믿지 않으면 어쩌나 하는 두려움 앞에서, 그분은 네가 이미 쥐고 있는 지팡이 하나를 가리키셨다.",
  ],
  cant_speak: [
    q("출 4:11~12", ["ex-4:11", 3, 7], ["ex-4:12", 0, 10]),
    ". 말이 둔한 것은 *능력의 결* 일 뿐, *인격의 결* 이 아니다. 둔한 입에도 그분이 함께 계신다.",
  ],
  send_other: [
    q("출 4:14~16", ["ex-4:14", 7, 12], ["ex-4:15", 8, 19]),
    ". 다른 이를 보내달라는 회피 앞에서도, 그분은 꾸짖어 내치는 대신 *함께 걸을 사람* 을 붙여 주셨다.",
  ],
};

/** Scene 4 — 파라오 앞에서 지팡이. */
export const scene4Reactions: Record<Scene4Choice, Monologue> = {
  cast_now: [
    "지팡이를 곧장 던진다(출 7:10). 손을 떠난 것은 네 통제가 아니라, 네 순종이다. " +
      "결과가 뱀이 될지 파라오의 마음을 여는 표적이 될지는 네 손 밖의 일이다.",
  ],
  hesitate: [
    "잠시 망설인다. 파라오의 권세 앞에서 손이 떨리는 것은 당연하다. " +
      "망설임은 실패가 아니다 — 그 5초 뒤에도 네 손은 여전히 지팡이를 들 수 있다.",
  ],
  with_aaron: [
    "아론을 돌아본다. 하나님은 너를 혼자 보내지 않으셨다(출 4:14~16). " +
      "동행은 나약함의 증거가 아니라, 부름이 애초에 함께 걷도록 설계되었다는 표시다.",
  ],
};

/** Scene 5 — 홍해 앞. 지팡이를 들고 손을 뻗는 단 하나의 행동. */
export const scene5Monologue: Record<Scene5Gesture, Monologue> = {
  lift_staff: [
    "지팡이를 들고 손을 바다 위로 뻗는다(출 14:21). ",
    q("출 14:13", ["ex-14:13", 3, 15]),
    ". 길을 여는 것은 네 손의 힘이 아니다. 네 몫은 손을 드는 것, 그 하나였다.",
  ],
};

/**
 * Scene 6 — outro. Scene 3 의 변명 처리 패턴에 따라 결말 톤 분기.
 * 소망은 *지금의 회복 속도와 분리* 해 제시한다 (R3 회복 압박 방지).
 */
export const scene6OutroByScene3: Record<Scene3Pattern, Monologue> = {
  all_throw: [
    q("출 3:12", ["ex-3:12", 2, 7]),
    "\n\n" +
      "너는 변명을 내려놓고 광야로 나섰다. 그러나 광야 40년은 *기다림* 의 시간이었다 — " +
      "빠른 승리가 아니라 긴 동행이었다. 오늘 네가 다 준비되지 않아도, 함께 가시는 분은 이미 거기 계신다.",
  ],
  all_heart: [
    q("출 3:12", ["ex-3:12", 2, 7]),
    "\n\n" +
      "너는 두려움을 품은 채로 여기까지 왔다. 그것으로 충분하다. " +
      "부름은 두려움이 사라진 사람을 찾지 않는다 — 두려운 채로도 한 걸음을 떼는 사람과 함께 걷는다. " +
      "지금 다 강해지지 않아도, 그 소망은 사라지지 않는다.",
  ],
  mixed: [
    q("출 3:12", ["ex-3:12", 2, 7]),
    "\n\n" +
      "완전하지 않은 순종으로 너는 바다를 건넜다. 모세도 그랬다 — 끝까지 더디게 말했고, 끝까지 사람이었다. " +
      "네 걸음의 속도는 부름의 진실함을 재는 자가 아니다. 오늘의 한 걸음이면 된다.",
  ],
};

/** 이 화면이 열어야 하는 성경 참조 전부. `useMonologueTexts` 에 그대로 넘긴다. */
export const allRefs = refsOf(
  scene1Wilderness,
  scene2Monologues,
  scene3Outcomes,
  scene3CardResponses,
  scene4Reactions,
  scene5Monologue,
  scene6OutroByScene3,
);

/**
 * Scene 3 의 카드→슬롯 배정 결과를 outcome 패턴으로 변환.
 * assignments: 카드 id → "throw" | "heart".
 */
export function scene3AssignmentsToPattern(
  assignments: Record<string, "throw" | "heart">,
): Scene3Pattern {
  const values = Object.values(assignments);
  if (values.length === 0) return "mixed";
  const allThrow = values.every((v) => v === "throw");
  const allHeart = values.every((v) => v === "heart");
  if (allThrow) return "all_throw";
  if (allHeart) return "all_heart";
  return "mixed";
}

const SCENE3_CARD_ORDER: Scene3Card[] = [
  "who_am_i",
  "who_sent_me",
  "trust_me",
  "cant_speak",
  "send_other",
];

/**
 * Scene 3 echo — outcome 패턴 텍스트 뒤에 *내려놓은(throw)* 카드별 떨기나무 본문 응답을 잇는다.
 * 응답은 카드를 *놓거나 치우는 순간* 울린다(§3.4) — 여기서는 내려놓은 카드에 한해 인용한다.
 * 하나도 내려놓지 않았으면(all_heart) 대표 응답(정체성, 출 3:12)만 붙여 과부하를 피한다.
 */
export function buildScene3Echo(
  pattern: Scene3Pattern,
  assignments: Record<string, "throw" | "heart">,
): Monologue {
  const base = scene3Outcomes[pattern];
  const thrown = SCENE3_CARD_ORDER.filter(
    (id) => assignments[id] === "throw" && scene3CardResponses[id],
  );
  const cards = thrown.length > 0 ? thrown : (["who_am_i"] as Scene3Card[]); // all_heart 등 — 대표 응답만
  // 이어붙이기는 *조각 단위* 다. 문자열로 먼저 합쳤다가는 인용 위치를 잃는다.
  const out: Segment[] = [...base];
  for (const id of cards) out.push("\n\n", ...scene3CardResponses[id]);
  return out;
}
