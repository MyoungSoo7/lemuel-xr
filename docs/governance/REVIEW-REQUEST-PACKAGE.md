# 자문 요청 패키지 — Joseph MVP 프로토타입 기준

> **위치**: [`docs/governance/`](.) — 자문가 영입 후 *첫 번째 검토 요청* 묶음
> **상위 문서**: [`CLINICAL-REVIEW.md`](CLINICAL-REVIEW.md) (워크플로) / [`REVIEWER-BOOTSTRAP.md`](REVIEWER-BOOTSTRAP.md) (등록)
> **목적**: 자문가가 영입되면 *추상적 거버넌스* 보다 *구체적 콘텐츠* 부터 검토할 수 있도록, 현재 프로토타입에서 *실제로 작성된* 콘텐츠를 한 곳에 정리

---

## 0. 프로토타입 완성도 (2026-05-21 기준)

| 영역 | 상태 | 비고 |
|---|---|---|
| Backend `/api/game/joseph/{start,decide,complete}` | ✅ 동작 | DecideSceneUseCase + ScenarioYamlLoader + GameSession |
| `scenarios/joseph.yml` Scene 1~5 정적 정의 | ✅ 정의 | options / queues / cache_keys 명시, 실제 텍스트는 frontend |
| Frontend `/joseph` 페이지 | ✅ 동작 | Scene 1~5 UI + 결정 후 monologue echo |
| **Scene 2 monologue 3 패턴** | ✅ **자문 1순위** | `frontend/src/lib/content/joseph-monologues.ts` §scene2Monologues |
| **Scene 3 outcome 3 패턴** | ✅ **자문 1순위** | 같은 파일 §scene3Outcomes |
| **Scene 4 reaction 3 패턴** | ✅ **자문 1순위** | 같은 파일 §scene4Reactions |
| **Scene 5 결말 3 패턴 (Scene 3 기반 분기)** | ✅ **자문 1순위** | 같은 파일 §scene5OutroByScene3 |
| Scene 배경 이미지 (Imagen 4.0 fast 생성) | ✅ 5장 | `frontend/public/images/scenes/{1..5}.jpg` |
| LLM 실시간 호출 (Scene 4 realtime_llm) | ❌ **미구현** | Phase 2 — 자문 통과 후 Python AI 사이드카 연결 |
| 사용자 데이터 / 위기 감지 / safety_alerts | ⚠️ 백엔드 schema 만 — controller endpoint 미구현 | Phase 2 |
| 신학·임상 검토 API (`/api/theology/reviews` / `/api/clinical/reviews`) | ⚠️ DB 만 — controller endpoint 미구현 | Phase 2 (자문가 영입 후 즉시 구현) |
| 임상자문 영입 | ⚠️ 진행 중 | [`outreach/CLINICAL-REVIEWER-OUTREACH.md`](outreach/CLINICAL-REVIEWER-OUTREACH.md) |

---

## 1. 자문 요청 1순위 — 12개 사용자 노출 텍스트

전부 `frontend/src/lib/content/joseph-monologues.ts` 에 정의. 본 문서는 *자문 검토용으로* 본문을 재인용.

### 1.1 Scene 2 — 풍년기 저장 결정 후 monologue

| 사용자 선택 | 표시 텍스트 |
|---|---|
| `save_20` (1/5 저장) | *백성 중 일부만 살리는 분량. 부족이 닥치면 누군가는 굶을 것이다. 지금의 풍요를 더 많이 누리는 대신 미래의 위기를 적게 본 너의 선택이다.* |
| `save_33` (1/3 저장) | *요셉이 실제로 따른 비율(창 41:34). 풍요와 미래의 책임을 나눠 든다. 지금 손에 쥔 자루의 무게는 7년 뒤 누군가의 양식이 되어 돌아온다.* |
| `save_50` (1/2 저장) | *과한 저장. 지금 풍년의 기쁨은 줄어든다. 그러나 너의 신중함으로 굶주리는 자가 더 적을 것이다.* |

**자문 질의 (신학)**:
- 창세기 41:34 의 *1/5 vs 1/3* 해석 분쟁 (히브리어 `chamash` 가 *5분의 1* 또는 *5등분* 양가 해석) — 사용자에게 *"요셉이 실제로 따른"* 으로 단정하는 게 적절한가?
- *"풍요와 미래의 책임을 나눠 든다"* 가 *복음주의 청지기 신학* 과 정합한가?

**자문 질의 (임상)**:
- save_20 의 *"누군가는 굶을 것이다"* 가 *과도한 자기책임 부담* 자극 위험?
- save_50 의 *"신중함으로 굶주리는 자가 더 적을 것"* 이 *완벽주의 사용자* 에게 강박 강화?

### 1.2 Scene 3 — 흉년기 분배 결정 후 outcome

| 분배 우선 | 표시 텍스트 |
|---|---|
| `farmer_first` | *이집트가 살았다. 백성이 너를 기억할 것이다. 다만 국경 너머에서 굶주린 이주민들의 손길은 너의 창고에 닿지 못했다.* |
| `immigrant_first` | *야곱의 가문이 너를 통해 살았다. 13년 전 너를 구덩이에 던진 형제가, 너의 양식을 받으러 올 것이다. 원망과 용서의 거리를 너는 직접 걷게 된다.* |
| `merchant_first` | *재정은 견고해졌으나 굶는 자들의 원망이 쌓였다. 통치는 강해지지만 다음 흉년의 분배는 더 어려워질 것이다.* |

**자문 질의 (신학)**:
- *"13년 전 너를 구덩이에 던진 형제"* — Joseph 의 *용서 narrative* 를 *지금 받으러 올 것* 으로 미리 단정. 사용자에게 *premature forgiveness* 압박이 안 되는가?
- merchant_first 의 *"재정 견고, 원망 쌓임"* 이 *부의 신학 비판* 으로 적절한 톤인가?

**자문 질의 (임상)**:
- immigrant_first 의 *"원망과 용서의 거리를 너는 직접 걷게 된다"* — Forgiveness Therapy (Enright) 패러다임의 *피해자 자율* 원칙 충실?
- merchant_first 의 *"통치는 강해지지만"* 이 *사회경제적 트라우마* 사용자 (예: 빈곤 경험) 에게 불편 자극?

### 1.3 Scene 4 — 형제와 재회 reaction

| 사용자 선택 | 표시 텍스트 |
|---|---|
| `reveal` | *정체를 즉시 밝힌다 — "나는 요셉이다. 너희가 애굽에 판 그 동생이다". 13년의 원망을 한 문장에 풀어놓는다. 형제들의 얼굴이 흙빛이 된다.* |
| `test` | *잠시 시험한다 — 베냐민을 데려오라 명한다. 형제들의 변화가 진실한지, 그들의 마음이 13년 전과 같은지 확인한다.* |
| `silent` | *침묵한다 — 다만 곡식을 내어준다. 재회의 기쁨도 분노도 아직 너의 몫이 아니다. 시간이 더 필요하다.* |

**자문 질의 (신학)**:
- 성경 본문 (창 42~45) 에서 요셉이 *실제로* 사용한 패턴은 *시험* 인데, *reveal* 과 *silent* 가 *대안 시나리오* 로 제시되는 게 정통 narrative interpretation 안의 자유인가?
- *silent* 의 *"시간이 더 필요하다"* 가 *건강한 회피* vs *영적 정체* 사이 어느 쪽으로 해석되나?

**자문 질의 (임상)**:
- *test* 의 *"형제들의 변화가 진실한지 확인"* 이 가족 갈등 사용자에게 *건강한 경계 설정* 모델이 되는가, *불신 강화* 가 되는가?
- *reveal* 의 *"형제들의 얼굴이 흙빛"* 묘사가 *직면 트라우마* 사용자에게 불편 자극?

### 1.4 Scene 5 — 결말 (Scene 3 패턴 기반 3 분기)

각 패턴마다 결말 톤 다름. 공통 상단: `"하나님이 생명을 구원하시려고 나를 너희 앞서 보내셨나니" (창 45:5)`.

| Scene 3 패턴 | 결말 톤 |
|---|---|
| `farmer_first` | *이집트는 살았다. 너의 결정 7년이 한 나라의 양식이 되었다. 다만 국경 너머의 굶주린 자들을 기억하라 — 다음 부름은 그쪽일 수 있다.* |
| `immigrant_first` | *너를 판 형제가 너의 양식으로 살았다. 원망과 용서의 거리를 너는 직접 걸었다. **섭리는 가해자의 정당화가 아니다** — 형의 죄는 죄로 남았고, 너의 회복도 너의 것이다.* |
| `merchant_first` | *재정은 견고해졌고 통치는 강해졌다. 그러나 굶주린 자의 원망이 너의 다음 결정을 무겁게 만들 것이다. 다시 7년이 온다면, 너는 어떤 줄을 먼저 부르겠는가?* |

**자문 질의 (신학)**:
- **immigrant_first 의 *"섭리는 가해자의 정당화가 아니다"*** — `MENTAL-HEALTH-PAPERS.md §14` Jones 2022 moral injury 직접 매핑. 정통 개혁주의 *섭리론* 과 정합한가?
- *"형의 죄는 죄로 남았고"* 가 *전적 타락 + 칭의* 신학과 충돌 없는가?

**자문 질의 (임상)**:
- *moral injury* 메커니즘이 사용자에게 *직접* 의식되는 위치에 있는 게 적절한 노출인가? 또는 *각주* / *footer* 로 분리해야 하는가?
- merchant_first 의 *"다시 7년이 온다면, 너는 어떤 줄을 먼저 부르겠는가?"* — 사용자에게 *재선택 동기 부여* 인가, *우유부단 강박* 자극인가?

---

## 2. 자문 *불요* 항목 (확인만)

| 항목 | 이유 |
|---|---|
| Scene 1 narration "파라오의 꿈을 해석한다. 7년 풍년과 7년 흉년이 다가온다" | 단순 사실 narration |
| Scene 4 진입 문구 "형제들이 곡식을 구하러 왔다. 어떻게 응대할 것인가?" | 분기 안내 |
| Scene 배경 이미지 (Imagen 4.0 fast) | *시각 자문* 별도 — 본 패키지 범위 외 |
| 버튼 라벨 ("계속 →", "미션 완료" 등) | UX |

---

## 3. 자문 워크플로 (영입 후 즉시 가능)

1. **자문가 OAuth 가입** → `users.external_id` 결정
2. **`application-bootstrap.yml` entry 추가** → backend 재배포 → `reviewer_profiles` 시드
3. **자문가에게 본 문서 (REVIEW-REQUEST-PACKAGE.md) link 전달**
4. 자문가가 §1 의 12개 텍스트 검토 → `verdict` + 체크리스트 4종 score 작성
5. *현재 시점* — `theology_reviews` / `clinical_reviews` REST controller 미구현 → **임시로 이슈 코멘트 또는 PR 리뷰 형태** 로 검토 결과 받음 (자문가가 익숙)
6. 검토 통과 텍스트는 *git commit* 으로 본 문서 + monologue ts 동시 업데이트
7. 검토 거부 텍스트는 *대체안* 받아 monologue ts 수정

→ Phase 2 (자문 진행 중) 에 `theology_reviews` / `clinical_reviews` controller endpoint 구현 시 검토 결과를 DB 적재로 *마이그레이션*. 그 전엔 git history 가 audit log 역할.

---

## 4. 향후 확장 (자문 통과 후)

- **Phase 2-A**: Scene 2/3 monologue/outcome 을 *backend round-trip* 으로 전환 (현재 frontend 직결 → backend DTO `responseText` 필드 + DecideSceneUseCase 응답에 포함)
- **Phase 2-B**: Scene 4 의 `realtime_llm: true` 활성화 — Python AI 사이드카 호출 + LLM 캐시 (Scene 3 결과 + Scene 4 선택 조합 → 미세 차이 1줄)
- **Phase 2-C**: 사용자의 `users.faith_tone` (strong / balanced / soft) 기반 monologue 톤 분기 — 3 톤 × 12 텍스트 = 36 패턴 (자문 검증 분량 3배)
- **Phase 3**: 모세 / 다윗 / 예수 미션에 같은 패키지 형식 적용

---

> *본 패키지는 자문가가 영입되면 즉시 첫 검토 큐로 들어감. 텍스트는 자문 통과 전까지 prototype demo 한정 — 외부 공개 금지. PUBLISHED 시점에 "AI 보조" footer 와 함께 사용자 노출.*
