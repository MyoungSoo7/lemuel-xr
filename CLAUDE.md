# CLAUDE.md — lemuel-xr 작업 가이드

## 프로젝트 개요

**Mission (2026-05-22 최종 정착)**: **lemuel-xr 는 절망 비상 대비 영적 훈련 프로그램이다.**

> 큐티가 *일상 영적 양식*, 민방위 교육이 *비상 대비 훈련* 이라면,
> lemuel-xr 는 *절망 비상* 에 대비하는 *영적 단련 프로그램*.

누구나 절망에 빠질 수 있고, OECD 자살율 1위 대한민국의 불명예 뒤에는 *그 절망에서 빠져나오지 못한* 사람들이 있다. lemuel-xr 는 그 *비극이 일어나기 전에* 4 인물 (요셉·모세·다윗·예수) 의 *각자 다른 절망 → 아름다운 회복* 이야기 + 일상의 1~7 가치 습관화로 *내면을 단단히* 만드는 *예방 영적 교육*.

**중요 — 이 앱은**:
- ✅ 영적 단련 / 예방 교육 / 큐티 / 회복탄력성 빌더
- ❌ 의료 진단 / 치료 / 임상 도구 / 위기 개입 (그 영역은 1393·전문가)

**타겟 사용자**: **누구나** — 내일 절망을 만날 수 있는 모든 사람. 우울증 환자 한정 X. *영적 비상 대비* 교육을 받고 싶은 사람.

### 4 인물 — 각자 다른 절망의 결, 모두 동등하게 중요

| 인물 | 절망의 결 | 회복의 결 | 일상 가치 (1~7) 연계 |
|---|---|---|---|
| **요셉** | 형제 배신 · 노예 · 13년 옥살이 · 억울함 | 신중함·꾸준함 → 총리·민족 구원 | 신중함 → 잠언, 인내 → 일기 |
| **모세** | 광야 40년 도피 · 무자격감 · 짊어진 백성의 원망 | 부름의 동행 인식 → 출애굽 · 율법 | 인내 → 시편, 동행 → 사람을 두려워하지 않음 |
| **다윗** | 형의 멸시 · 사울의 추격 · 시편 비탄의 자리 | 솔직함 → 시인이자 왕 · 회개의 사람 | 솔직함 → 일기, 감정 → 시편 |
| **예수** | 광야 시험 · 겟세마네 고뇌 · 십자가 외침 · 죽음 | 내려놓음 → 부활 · 인류 구원 | 내려놓음 → 마음 지키기, 영성 → 전도서 |

→ **4 인물 모두 *각자의 절망* 을 *각자의 방식* 으로 통과**. 그 4가지 결이 사용자에게 *다른 거울* 이 된다.

### VR(8~11) ↔ AR(1~7) 교차 구조

- **VR**: 4 인물 미션 — *집중 영적 훈련 의식* (한 번에 5~10분, 깊은 몰입)
- **AR**: 일상 7 가치 — *매일 영적 습관* (일기·잠언·시편·전도서·욥·마음지키기·사람두려워하지않음)
- **궁극 목표**: 사용자가 *자기만의 7 가치 루틴* 만들고 습관화. 4 인물은 *그 루틴을 빛내는 매개*.

**현재 상태**: Phase 1 인프라 완성. Disclaimer 5-layer 안전선 가동 (치료 X 메시지·CrisisLockout·AI 라벨 — 모두 유지).

**다음 마일스톤**: 4 인물 시나리오 폴리시 + AR 1~7 습관 빌더.

## 핵심 문서

- [`README.md`](README.md) — 개요
- [`docs/PLAN.md`](docs/PLAN.md) — 전체 기획서 (370줄)

## 작업 시 유의사항

### 0. 콘텐츠 생성 정책 (2026-05-22 최종)

**정체성 = 영적 비상 대비 훈련 (큐티 + 민방위)**. 임상·치료 영역이 아니므로 임상 자문 강제 X. *인간의 보편 이야기* 로 자리잡음.

**AI 생성 활성화** — `AI_GENERATION_ENABLED=true` (default). Prompt guard 는 *영적 단련 / 큐티 톤* 강조:
- ✅ 권장: *내면 단련*, *영적 양식*, *예방 영성*, *4 인물의 절망 → 회복*, *희망의 각인*
- ❌ 금지: *치료*, *진단*, *상담*, *처방*, *의료 권고*, 자살 사고에 대한 직접 조언

**4-layer 안전 유지** (mission 변경과 무관하게 깔리는 안전선):
1. Disclaimer Gate — *치료 도구 아님 / 예방 영적 교육*
2. CrisisLockout — 위기 키워드 매칭 시 1393 으로 즉시 전달
3. AI 라벨링 — 모든 LLM 응답에 *AI 보조 — 본문은 성경 참조*
4. ResponseHeaderFilter — `X-Lemuel-Disclaimer: not-medical-device`

**큐레이션 + AI 보강 혼합**:
- 성경 본문 (DB seed) — 큐레이션
- 시나리오 yml 의 정적 분기 응답 — 큐레이션
- 사용자 일기·시편 작성 → AI 응답 — *영적 단련 prompt 큐티 톤*
- 사용자 자기만의 7 가치 빌더 → AI 가이드 — 영적 양식

### 1. 신학적 검증

AI 가 생성한 묵상·해석은 **자동 출판 금지**. 출력 → 검토 → 게시. 출력에 *"AI 보조"* 표시 필수.

### 1.5 임상적 검증 — 신학과 *병렬*

AI 가 생성한 *사용자 노출 콘텐츠* (특히 Theme 5 고통·11 십자가, F-6 안전장치, LLM 시스템 프롬프트, trigger_warning ≥ medium Scene) 는 **신학 자문 + 임상 자문 양쪽 모두 approve** 가 PUBLISHED 의 조건.

- **임상 체크리스트 4종** (1-5 score): trauma_safety / crisis_resource_compliance / **moral_injury_risk** (Jones 2022 PMID 35609469 직접 매핑) / evidence_quality
- **Veto 단독 reject**: 임상 자문은 moral_injury / 자해 안전망 부재 / consent 없는 trauma 자극 시 신학 verdict 무관하게 단독 reject 가능
- **escalation**: 신학 OK / 임상 reject → **임상 우선** (사용자 안전). 신학 reject / 임상 OK → **신학 우선** (콘텐츠 정체성)
- **2-of-2 approve 필수**: Theme 11 (예수) + trigger_warning=high Scene

상세: [`docs/governance/CLINICAL-REVIEW.md`](docs/governance/CLINICAL-REVIEW.md) / [FUNCTIONAL-SPEC §F-7.5](docs/FUNCTIONAL-SPEC.md) / [SEQUENCE-DIAGRAMS §5](docs/SEQUENCE-DIAGRAMS.md) / [Issue #4](https://github.com/MyoungSoo7/lemuel-xr/issues/4)

### 2. 톤 — 사용자가 종교색 강도를 *선택*

- 강 — 신학 용어, 성경 인용 직접
- 약 — *지혜의 책* / *고대 텍스트* 톤, 비신자도 진입 가능

### 3. 듀얼 트랙 — 사용자가 의식 안 해도 분기

감정 입력 → 트랙 A 자동, 게임 메뉴 진입 → 트랙 B. 두 모드 UI 가 *섞여 보이면 안 됨*.

### 4. 11개 주제 + 담당자

| # | 주제 | 트랙 | 담당 |
|---|---|---|---|
| 1~3 | 자기 기록 (일기·잠언·전도서) | A | (미정) |
| 4~5 | 정서 처리 (시편·고통) | A | (미정) |
| 6~7 | 행동 지침 (마음·사람) | A | (미정) |
| 8 | 요셉 — 경제 구원 | B | @MyoungSoo7 |
| 9 | 모세 — 정치 구원 | B | @MyoungSoo7 |
| 10 | 다윗 — 외세 구원 | B | @MyoungSoo7 |
| 11 | 예수 — 영적 구원 | B | @MyoungSoo7 (단독 진행 — D) |

**담당 = 설계·시나리오·신학 검토 1차 책임자.** 코드/구현은 별도 분담 가능.

### 5. 경쟁사

| 앱 | 카테고리 | 펀딩/사용자 |
|---|---|---|
| Hallow | 가톨릭 | $50M+ |
| Pray.com | 기독교 일반 | 600만+ |
| YouVersion | 성경 앱 | 6억 다운로드 |
| Calm/Headspace | 일반 명상 | $70/년 |

Lemuel 차별화: **개신교 + 게임 듀얼 + AI 전방위 + 한국/동아시아**

## 기술 스택

- Backend: **Spring Boot 4.0.4 + Kotlin 2.2.20** — 100% Kotlin (Java 0개, .kt 305개), Lombok 완전 제거. JDK 25 툴체인 / Kotlin JVM 바이트코드 24 타깃(JVM 25 로드 정상). 헥사고날(ports & adapters) — 각 바운디드 컨텍스트가 `application/port/out/*Port` ↔ `adapter/out/persistence/*PersistenceAdapter`, 순수 도메인 모델은 어댑터에서 JPA 엔티티와 매핑. `build.gradle.kts` (gradlew 래퍼 없음, `gradle bootJar -x test`)
- AI: Python 사이드카(`ai/`, `tts/`) — WebClient proxy (`adapter/out/sidecar`) + OpenAI/Gemini/Claude
- Game: Unity 6 LTS
- Frontend: React Native / Next.js
- DB: PostgreSQL 16 + pgvector (성경 임베딩)

## 다음 액션 (시간 순)

1. 타겟 인터뷰 5명 (1주)
2. 신학 자문 영입 (2주)
3. Figma wireframe — 트랙 A 1~5 (2주)
4. AI 비용 시뮬레이션 (1일)
5. V1.0 개발 (3개월)

자세히는 [`docs/PLAN.md §8`](docs/PLAN.md#8-다음-액션-시간-순)

## 작업 컨벤션

- 한국어 문서 (코드 주석은 한/영 혼용 OK)
- markdown 으로 문서 일원화 (Word/PPT 지양)
- 인터뷰 노트는 `docs/interviews/YYYY-MM-DD-이니셜.md` 형식
- 신학 검토는 `docs/theology-review/주제별.md`
