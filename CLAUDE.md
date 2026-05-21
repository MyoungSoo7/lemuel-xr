# CLAUDE.md — lemuel-xr 작업 가이드

## 프로젝트 개요

**Mission (2026-05-22 재정의)**: OECD 자살율 1위 대한민국의 회복을 위한 *영성 동행 도구*. 절망 한가운데서 *언어 허락 + 회복의 소망* 을 주는 콘텐츠.

성경 기반 **위기 후 회복(Recovery)** 사용자를 위한 영성 동행 플랫폼. **치료 도구가 아니다** — 의료·정신과 진단/치료를 *대체하지 않으며*, *영적·인간적 동행* 이다.

**타겟 사용자 (B — Recovery)**: 우울증 진단 + 치료 중, 안정기에 들어선 사용자. *지금 active crisis 상태* 사용자는 1393·전문가에게 우선 안내 (DisclaimerGateFilter + CrisisLockout 으로 자동 분기).

**현재 상태**: Phase 1 인프라 완성 + Disclaimer 5-layer 안전선 가동. 콘텐츠는 *큐레이션 우선*.

**다음 마일스톤**: Stage 1 (욥·엘리야·시편 88) MVP — 6~8주.

### 인물 우선순위 (2026-05-22 재정렬)

| Stage | 인물 | 사용자 상태 | 코드 상태 |
|---|---|---|---|
| 1 — 함께 있기 | **욥·엘리야·시편 88** | 절망 언어 허락 | **MVP — 신규** |
| 2 — 떨면서 한 발 | 모세 (광야 죽음 갈구 → 부름) | 무자격감·회복 진입 | 코드 있음 (Recovery 적합) |
| 3 — 정체성 찾기 | 다윗 (시편 비탄 + 골리앗) | 정체성 흔들림 | 코드 있음 |
| 4 — 회복 후 시야 | 요셉 | 이미 살아남은 사람의 자기 서사 | 코드·시나리오 *보존*, Phase 2~3 활성화 |
| 5 — 영적 핵심 | 예수 겟세마네·십자가 | 가장 깊은 동행 | godjinho 담당 |

요셉은 *번영·회복 결과* 모델. Active crisis 사용자에게 *비교 박탈감* 유발 위험으로 MVP 에서 미룸 (R2 가스라이팅 회피).

## 핵심 문서

- [`README.md`](README.md) — 개요
- [`docs/PLAN.md`](docs/PLAN.md) — 전체 기획서 (370줄)

## 작업 시 유의사항

### 0. 콘텐츠 생성 정책 (2026-05-22 결정)

**LLM 생성 콘텐츠는 *시스템 wide 비활성화*** — 임상 자문 1명 영입 전까지. 환경변수 `AI_GENERATION_ENABLED=false` (default). 이유:
- 자살예방 영역에 *책임 있는 콘텐츠* 보장이 *속도·매력* 보다 우선
- R2 (가스라이팅), 신학적 일탈 위험을 *prompt guard 만으로* 잡기 어려움 (특히 욥기 같은 답 없는 본문)
- 사용자 5~10명 베타 → 자문 영입 → AI 생성 활성화 (Phase 2)

**현재 콘텐츠 = 큐레이션만**:
- 성경 본문 인용 (DB seed)
- 사전 검수된 묵상문 (사람이 작성, 신학·임상 자문 통과)
- 위기 자원 (KR 시드)
- 시나리오 yml 의 정적 분기 분기 응답

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

## 기술 스택 (예정)

- Backend: Spring Boot 4 + Kotlin
- AI: LangChain + OpenAI/Claude
- Game: Unity 6 LTS
- Frontend: React Native
- DB: PostgreSQL + Pinecone (성경 임베딩)

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
