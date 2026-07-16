# SPEC.md — lemuel-xr 기능명세서

> CLAUDE.md 에서 분리한 **기능 명세**. 미션·정체성·기능 단위 요구사항을 정리한다.
> 심화 문서: [`docs/FUNCTIONAL-SPEC.md`](docs/FUNCTIONAL-SPEC.md) · [`docs/PLAN.md`](docs/PLAN.md) · [`docs/USER-FLOW.md`](docs/USER-FLOW.md)

---

## 1. 미션 & 정체성

**lemuel-xr 는 절망 비상 대비 영적 훈련 프로그램이다.** (2026-05-22 최종 정착)

> 큐티가 *일상 영적 양식*, 민방위 교육이 *비상 대비 훈련* 이라면,
> lemuel-xr 는 *절망 비상* 에 대비하는 *영적 단련 프로그램*.

4 인물(요셉·모세·다윗·예수)의 *각자 다른 절망 → 아름다운 회복* 이야기 + 일상의 1~7 가치 습관화로 *내면을 단단히* 만드는 **예방 영적 교육**.

| 이 앱은 | 이 앱이 아닌 것 |
|---|---|
| ✅ 영적 단련 / 예방 교육 / 큐티 / 회복탄력성 빌더 | ❌ 의료 진단 / 치료 / 임상 도구 / 위기 개입 |

- **타겟 사용자**: 누구나 — 내일 절망을 만날 수 있는 모든 사람. 우울증 환자 한정 아님.
- **위기 영역은 앱이 담당하지 않음** — 1393(자살예방상담)·전문가에게 즉시 이관.

---

## 2. 4 인물 서사 (트랙 B — VR 8~11)

각자 다른 절망의 결, 모두 동등하게 중요.

| 인물 | 절망의 결 | 회복의 결 | 일상 가치 연계 |
|---|---|---|---|
| **요셉** | 형제 배신 · 노예 · 13년 옥살이 · 억울함 | 신중함·꾸준함 → 총리·민족 구원 | 신중함 → 잠언, 인내 → 일기 |
| **모세** | 광야 40년 도피 · 무자격감 · 백성의 원망 | 부름의 동행 인식 → 출애굽·율법 | 인내 → 시편, 동행 → 사람을 두려워하지 않음 |
| **다윗** | 형의 멸시 · 사울의 추격 · 비탄의 자리 | 솔직함 → 시인이자 왕·회개의 사람 | 솔직함 → 일기, 감정 → 시편 |
| **예수** | 광야 시험 · 겟세마네 고뇌 · 십자가 외침 · 죽음 | 내려놓음 → 부활·인류 구원 | 내려놓음 → 마음 지키기, 영성 → 전도서 |

- **기능**: 각 인물별 XR 미션 = *집중 영적 훈련 의식* (1회 5~10분, 깊은 몰입).
- **콘텐츠 소스**: 인물별 MVP 문서 — [`docs/MVP-JOSEPH.md`](docs/MVP-JOSEPH.md) · [`docs/MVP-MOSES.md`](docs/MVP-MOSES.md) · [`docs/MVP-DAVID.md`](docs/MVP-DAVID.md) · [`docs/MVP-JESUS.md`](docs/MVP-JESUS.md).
- **시나리오 정의**: `backend/src/main/resources/scenarios/*.yml` (joseph·moses·david·jesus·elijah·job).

---

## 3. 11개 주제 & 듀얼 트랙

| # | 주제 | 트랙 | 성격 |
|---|---|---|---|
| 1~3 | 자기 기록 (일기·잠언·전도서) | A | 매일 영적 습관 |
| 4~5 | 정서 처리 (시편·고통) | A | 매일 영적 습관 |
| 6~7 | 행동 지침 (마음·사람) | A | 매일 영적 습관 |
| 8 | 요셉 — 경제 구원 | B | 집중 훈련 의식 |
| 9 | 모세 — 정치 구원 | B | 집중 훈련 의식 |
| 10 | 다윗 — 외세 구원 | B | 집중 훈련 의식 |
| 11 | 예수 — 영적 구원 | B | 집중 훈련 의식 |

**VR(8~11) ↔ AR(1~7) 교차 구조**:
- **AR (트랙 A)**: 일상 7 가치 — 매일 영적 습관 (일기·잠언·시편·전도서·욥·마음지키기·사람두려워하지않음).
- **VR (트랙 B)**: 4 인물 미션 — 집중 영적 훈련 의식.
- **궁극 목표**: 사용자가 *자기만의 7 가치 루틴* 을 만들고 습관화. 4 인물은 그 루틴을 빛내는 매개.
- 교차 매핑 상세: [`docs/CROSS-MAPPING-VR-AR.md`](docs/CROSS-MAPPING-VR-AR.md).

### 3.1 듀얼 트랙 분기 (F — 사용자가 의식 안 해도 자동 분기)
- 감정 입력 → **트랙 A** 자동 진입.
- 게임 메뉴 진입 → **트랙 B**.
- 두 모드 UI 가 *섞여 보이면 안 됨*.

---

## 4. 트랙 A 세부 기능 (1~7 가치)

구현 컨트롤러 기준 (`backend .../content`, `frontend/src/app/topics`):

| 기능 | 설명 | 백엔드 |
|---|---|---|
| 일기 (Journal) | 사용자 자기 기록 + AI 큐티 톤 가이드 | `JournalController`, `JournalGuidanceController` |
| 잠언 (Proverbs) | 지혜 본문 열람·묵상 | `ProverbsController` |
| 전도서 (Ecclesiastes) | 지혜 본문 열람·묵상 | `EcclesiastesController` |
| 시편 (User Psalm) | 사용자 시편 작성 + AI 응답 | `UserPsalmController` |
| 실천 성찰 (Practice) | 행동 지침 성찰 기록 | `PracticeReflectionController` |
| 가치 빌더 (Values) | 자기만의 1~7 가치 루틴 구성 | `ValuesController` |

상세: [`docs/TRACK-A-1-4-WISDOM-EMOTION.md`](docs/TRACK-A-1-4-WISDOM-EMOTION.md) · [`docs/TRACK-A-5-7-ACTION-GUIDANCE.md`](docs/TRACK-A-5-7-ACTION-GUIDANCE.md).

---

## 5. 톤 선택 (사용자가 종교색 강도 선택)

- **강** — 신학 용어·성경 인용 직접.
- **약** — *지혜의 책* / *고대 텍스트* 톤, 비신자도 진입 가능.

---

## 6. AI 콘텐츠 생성 정책

`AI_GENERATION_ENABLED=true` (default). Prompt guard 는 *영적 단련 / 큐티 톤* 강조.

- ✅ 권장: 내면 단련·영적 양식·예방 영성·4 인물의 절망→회복·희망의 각인.
- ❌ 금지: 치료·진단·상담·처방·의료 권고·자살 사고에 대한 직접 조언.

**큐레이션 + AI 보강 혼합**:
- 성경 본문(DB seed)·시나리오 yml 정적 분기 = 큐레이션.
- 사용자 일기·시편 작성 → AI 응답 = 영적 단련 prompt 큐티 톤.
- 사용자 7 가치 빌더 → AI 가이드 = 영적 양식.

관련: [`docs/AI-ARCHITECTURE.md`](docs/AI-ARCHITECTURE.md) · [`docs/EMOTION-CLASSIFIER.md`](docs/EMOTION-CLASSIFIER.md).

---

## 7. 안전선 (미션 변경과 무관하게 항상 가동)

### 7.1 4-layer 안전 아키텍처
1. **Disclaimer Gate** — *치료 도구 아님 / 예방 영적 교육* 고지.
2. **CrisisLockout** — 위기 키워드 매칭 시 1393 으로 즉시 전달 (`safety` 컨텍스트 `CrisisKeywordScanner`).
3. **AI 라벨링** — 모든 LLM 응답에 *AI 보조 — 본문은 성경 참조* 표시.
4. **ResponseHeaderFilter** — `X-Lemuel-Disclaimer: not-medical-device`.

### 7.2 신학 + 임상 병렬 검증 (게시 게이트)
AI 생성 *사용자 노출 콘텐츠* 는 **자동 출판 금지** — 출력 → 검토 → 게시. 신학·임상 양쪽 approve 가 PUBLISHED 조건.

- **임상 체크리스트 4종** (1–5 score): trauma_safety / crisis_resource_compliance / **moral_injury_risk** (Jones 2022 PMID 35609469) / evidence_quality.
- **Veto 단독 reject**: 임상 자문은 moral_injury·자해 안전망 부재·consent 없는 trauma 자극 시 신학 verdict 무관하게 단독 reject 가능.
- **escalation**: 신학 OK / 임상 reject → 임상 우선(안전). 신학 reject / 임상 OK → 신학 우선(정체성).
- **2-of-2 approve 필수**: Theme 11(예수) + trigger_warning=high Scene.

상세: [`docs/governance/CLINICAL-REVIEW.md`](docs/governance/CLINICAL-REVIEW.md) · [FUNCTIONAL-SPEC §F-7.5](docs/FUNCTIONAL-SPEC.md) · [`docs/safety-guidelines.md`](docs/safety-guidelines.md) · [`docs/ETHICS-LEGAL.md`](docs/ETHICS-LEGAL.md).

---

## 8. 지원 기능 (백엔드 컨텍스트별)

| 기능 | 컨텍스트 | 비고 |
|---|---|---|
| 인증 (JWT) | `auth` | 익명 우선 |
| 감정 분류 | `emotion` | 트랙 A 자동 분기 트리거 |
| 성경 본문/임베딩 | `scripture` | pgvector 유사도 검색 |
| LLM 응답 생성·캐시 | `ai` | 사이드카 proxy + 캐시/사용량 집계 |
| TTS 음성 합성 | `tts` | 사이드카 proxy |
| 여정 진행 | `journey` | 사용자 진행 상태 |
| 게임 세션 | `game` | 트랙 B 미션 진행 |
| 회복 리소스 | `recovery` | 위기 자원 안내 |
| 자산 매니페스트 | `asset` | XR/미디어 매니페스트 검증 |
| 분석 뷰 | `analytics` | 집계 뷰 |
| 이벤트 아웃박스 | `outbox` | 도메인 이벤트 신뢰 발행 |

---

## 9. 현재 상태 & 로드맵

- **현재**: Phase 1 인프라 완성. Disclaimer 5-layer 안전선 가동. 요셉 XR 게임을 V1.0 MVP 로 진행(2026-05-20 결정 — 트랙 A 정적 콘텐츠는 Phase 2).
- **다음 마일스톤**: 4 인물 시나리오 폴리시 + AR 1~7 습관 빌더.
- **다음 액션**: 타겟 인터뷰 5명 → 신학 자문 영입 → Figma wireframe(트랙 A 1~5) → AI 비용 시뮬레이션 → V1.0 개발.

상세 로드맵: [`docs/PLAN.md §8`](docs/PLAN.md) · [`docs/BUILD-PLAN.md`](docs/BUILD-PLAN.md).
