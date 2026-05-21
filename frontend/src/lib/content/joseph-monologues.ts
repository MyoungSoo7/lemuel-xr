/**
 * 요셉 미션의 *정적* 모놀로그 / 아웃컴 텍스트.
 *
 * 프로토타입 단계의 *frontend 직결* 형식. 자문 (`docs/governance/CLINICAL-REVIEW.md`)
 * 검증 후 backend round-trip + LLM (Python AI 사이드카) 으로 교체 가능한 형태로
 * shape 만 정의. shape: `monologues[scene][decision] : string`.
 *
 * 자문 받을 항목 (현재 텍스트):
 *  - Scene 2 monologue 3패턴 (save_20 / save_33 / save_50)
 *  - Scene 3 outcome 3패턴 (farmer_first / immigrant_first / merchant_first)
 *  - Scene 4 reaction 3패턴 (reveal / test / silent)
 *  - Scene 5 outro 3패턴 (Scene 3 결과 기반 톤 분기)
 *
 * 정책 — 본 텍스트는 자문 통과 전까지 *prototype demo 한정*. 사용자 명시 동의 없이
 * 외부 노출 금지. AI 보조 footer 표시 (자문 후 활성).
 */

export type SceneId = 2 | 3 | 4 | 5;
export type Scene2Choice = "save_20" | "save_33" | "save_50";
export type Scene3Pattern = "farmer_first" | "immigrant_first" | "merchant_first";
export type Scene4Choice = "reveal" | "test" | "silent";

export const scene2Monologues: Record<Scene2Choice, string> = {
  save_20:
    "백성 중 일부만 살리는 분량. 부족이 닥치면 누군가는 굶을 것이다. " +
    "지금의 풍요를 더 많이 누리는 대신 미래의 위기를 적게 본 너의 선택이다.",
  save_33:
    "요셉이 실제로 따른 비율(창 41:34). 풍요와 미래의 책임을 나눠 든다. " +
    "지금 손에 쥔 자루의 무게는 7년 뒤 누군가의 양식이 되어 돌아온다.",
  save_50:
    "과한 저장. 지금 풍년의 기쁨은 줄어든다. " +
    "그러나 너의 신중함으로 굶주리는 자가 더 적을 것이다.",
};

export const scene3Outcomes: Record<Scene3Pattern, string> = {
  farmer_first:
    "이집트가 살았다. 백성이 너를 기억할 것이다. " +
    "다만 국경 너머에서 굶주린 이주민들의 손길은 너의 창고에 닿지 못했다.",
  immigrant_first:
    "야곱의 가문이 너를 통해 살았다. " +
    "13년 전 너를 구덩이에 던진 형제가, 너의 양식을 받으러 올 것이다. " +
    "원망과 용서의 거리를 너는 직접 걷게 된다.",
  merchant_first:
    "재정은 견고해졌으나 굶는 자들의 원망이 쌓였다. " +
    "통치는 강해지지만 다음 흉년의 분배는 더 어려워질 것이다.",
};

export const scene4Reactions: Record<Scene4Choice, string> = {
  reveal:
    "정체를 즉시 밝힌다 — *나는 요셉이다. 너희가 애굽에 판 그 동생이다*. " +
    "13년의 원망을 한 문장에 풀어놓는다. 형제들의 얼굴이 흙빛이 된다.",
  test:
    "잠시 시험한다 — 베냐민을 데려오라 명한다. " +
    "형제들의 변화가 진실한지, 그들의 마음이 13년 전과 같은지 확인한다.",
  silent:
    "침묵한다 — 다만 곡식을 내어준다. " +
    "재회의 기쁨도 분노도 아직 너의 몫이 아니다. 시간이 더 필요하다.",
};

export const scene5OutroByScene3: Record<Scene3Pattern, string> = {
  farmer_first:
    '"하나님이 생명을 구원하시려고 나를 너희 앞서 보내셨나니" (창 45:5)\n\n' +
    "이집트는 살았다. 너의 결정 7년이 한 나라의 양식이 되었다. " +
    "다만 국경 너머의 굶주린 자들을 기억하라 — 다음 부름은 그쪽일 수 있다.",
  immigrant_first:
    '"하나님이 생명을 구원하시려고 나를 너희 앞서 보내셨나니" (창 45:5)\n\n' +
    "너를 판 형제가 너의 양식으로 살았다. " +
    "원망과 용서의 거리를 너는 직접 걸었다. *섭리는 가해자의 정당화가 아니다* — " +
    "형의 죄는 죄로 남았고, 너의 회복도 너의 것이다.",
  merchant_first:
    '"하나님이 생명을 구원하시려고 나를 너희 앞서 보내셨나니" (창 45:5)\n\n' +
    "재정은 견고해졌고 통치는 강해졌다. " +
    "그러나 굶주린 자의 원망이 너의 다음 결정을 무겁게 만들 것이다. " +
    "다시 7년이 온다면, 너는 어떤 줄을 먼저 부르겠는가?",
};

/** Scene 3 의 *우선 큐 선택* (prototype 단순화) 을 outcome 패턴으로 변환. */
export function scene3DecisionToPattern(decision: unknown): Scene3Pattern | null {
  if (typeof decision !== "object" || decision === null) return null;
  const priority = (decision as { priority?: string }).priority;
  if (priority === "farmer") return "farmer_first";
  if (priority === "immigrant") return "immigrant_first";
  if (priority === "merchant") return "merchant_first";
  return null;
}
