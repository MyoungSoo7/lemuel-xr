/**
 * 프론트엔드가 렌더하는 위기 상담 자원 — **번호 리터럴이 존재하는 유일한 파일**.
 *
 * 왜 모듈 하나로 모으는가
 * ─────────────────────────
 * 백엔드는 이미 2026-08-02 에 같은 결론에 도달해 있었다. 시나리오 yml 은 번호를
 * 적지 않고 `{{crisis_resources.default}}` 토큰만 쓰며, 실제 번호는 런타임에
 * `CrisisTokenResolver` 가 DB `crisis_resources` 정본에서 주입한다. 그 주석이
 * 이유를 정확히 적어 놨다 — "번호를 6개 yml 에 하드코딩해 두면 다음 개정 때 또
 * 낡은 번호가 위기 카피에 남는다."
 *
 * 프론트는 그 규율 바깥에 있었다. 2026-08-11 실측으로 9개 파일이 번호를 각자
 * 문자열로 들고 있었고, 그 9벌은 서로를 모른다. 2024-01-01 자살예방 상담 109 통합
 * 같은 개정이 또 오면 아홉 군데를 전부 찾아야 하고, 한 군데를 놓치면 **위기 상태의
 * 사용자가 죽은 번호를 받는다.** 이건 화면이 깨지는 종류의 실패가 아니라 조용히
 * 통화가 안 되는 종류의 실패다 — 어떤 테스트도 빨강이 되지 않는다.
 *
 * 왜 백엔드에서 fetch 하지 않는가
 * ────────────────────────────────
 * DB 가 정본이지만 프론트가 런타임에 그걸 가져오게 만들면, API 가 죽었을 때
 * 위기 footer 가 번호 없이 렌더된다. 위기 자원은 서비스가 고장난 순간에 가장
 * 필요하다. 그래서 여기서는 **정적 미러**로 둔다.
 *
 * 미러는 갈라진다 — 그래서 `scripts/check_frontend_hotline.py` 가
 *   ① 이 파일 바깥의 번호 리터럴 0건
 *   ② 이 파일의 기본 번호 == backend application.yml 의
 *      `safety.crisis.default-contact` 대체값
 * 두 가지를 CI 에서 강제한다. 갈라지면 빨강이다.
 *
 * 번호 정본 (2026-08-02 교정): 자살예방 상담은 2024-01-01 자로 **109** 로 통합됐다
 * (보건복지부 "분산된 자살예방 상담전화 1월 1일부터 '109'로 통합 운영").
 * 구 번호 1393 은 정번호가 아니다. 1577-0199(정신건강)·129(보건복지)·생명의전화는
 * 폐지되지 않았고 각자 담당 분야 상담을 계속하므로 그대로 둔다.
 */

export interface CrisisResource {
  /** `tel:` 링크에 그대로 들어가는 번호. */
  readonly tel: string;
  /** 기관·서비스 이름. */
  readonly label: string;
  /**
   * 좁은 자리용 축약 이름. 기존 화면들이 `정신건강위기상담` 처럼 `전화` 를 뗀 형태로
   * 쓰고 있었다 — 통합하면서 문안이 바뀌지 않게 두 형태를 다 들고 있는다.
   * 위기 카피는 검토를 거친 문장이므로 리팩터링이 조용히 고쳐서는 안 된다.
   */
  readonly shortLabel: string;
  /** 이용 조건 (24시간 여부 등). */
  readonly note: string;
}

/**
 * 전화 자원. **순서가 곧 우선순위**다 — 0번이 기본 자원이고,
 * 백엔드 `safety.crisis.default-contact` 대체값과 같아야 한다.
 */
export const CRISIS_RESOURCES: readonly CrisisResource[] = [
  {
    tel: "109",
    label: "자살예방 상담전화",
    shortLabel: "자살예방 상담전화",
    note: "24시간 무료, 전화",
  },
  {
    tel: "1577-0199",
    label: "정신건강위기상담전화",
    shortLabel: "정신건강위기상담",
    note: "24시간 무료",
  },
  {
    tel: "129",
    label: "보건복지상담센터",
    shortLabel: "보건복지상담센터",
    note: "24시간 무료",
  },
] as const;

/** 기본(우선순위 1위) 자원. */
export const CRISIS_DEFAULT: CrisisResource = CRISIS_RESOURCES[0];

/** 한국생명의전화 — 전화가 어려운 사용자를 위한 온라인 채팅 경로. */
export const CRISIS_LIFELINE = {
  url: "https://www.lifeline.or.kr",
  label: "한국생명의전화",
  note: "온라인 채팅 상담",
} as const;

/**
 * 좁은 자리(footer 한 줄·카드 하단)용 압축 표기.
 * 예: `109 · 1577-0199 (24시간)`
 */
export const CRISIS_LINE_SHORT = `${CRISIS_RESOURCES[0].tel} · ${CRISIS_RESOURCES[1].tel} (24시간)`;

/**
 * 문장 안에 들어가는 전체 표기.
 * 예: `위기 시: 109 (자살예방 상담전화) · 1577-0199 (정신건강위기상담전화) · 24시간`
 */
export const CRISIS_LINE_FULL = `위기 시: ${CRISIS_RESOURCES[0].tel} (${CRISIS_RESOURCES[0].shortLabel}) · ${CRISIS_RESOURCES[1].tel} (${CRISIS_RESOURCES[1].shortLabel}) · 24시간`;

/** `tel:` href 생성. 링크에도 번호가 두 번 나타나지 않게 한다. */
export function telHref(r: CrisisResource): string {
  return `tel:${r.tel}`;
}
