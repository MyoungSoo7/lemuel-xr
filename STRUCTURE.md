# STRUCTURE.md — 리포지토리 구조

> CLAUDE.md 에서 분리한 **리포지토리 구조** 문서. 실제 디렉토리 트리 기준.
> 아키텍처 원리는 [`ARCHITECTURE.md`](ARCHITECTURE.md), 기능은 [`SPEC.md`](SPEC.md) 참조.

---

## 1. 최상위 레이아웃

```
lemuel-xr/
├── backend/         # Spring Boot 4 + Kotlin — 코어 API (헥사고날)
├── frontend/        # Next.js (React) — 웹 클라이언트
├── ai/              # Python AI 사이드카 — LLM proxy (OpenAI/Gemini/Claude)
├── tts/             # Python TTS 사이드카 — 음성 합성
├── unity/           # Unity 6 LTS — XR 게임 (트랙 B)
├── unity-stub/      # Unity 스텁 (Assets, CI/개발용)
├── content/         # 인물별 콘텐츠·매니페스트 (joseph/moses/david)
├── scripts/         # 유틸 스크립트 (generate_scenes.py)
├── docs/            # 기획·설계·거버넌스 문서 (심화)
├── docker-compose.yml   # 로컬 스택 (postgres + ai + tts)
├── CLAUDE.md        # 작업 가이드 (정적 프로젝트 지식)
├── SPEC.md          # 기능명세서
├── STRUCTURE.md     # 본 문서
├── ARCHITECTURE.md  # 아키텍처
└── README.md / CONTRIBUTING.md
```

---

## 2. backend/ — Spring Boot 4 + Kotlin

- **언어**: 100% Kotlin (`.kt` 305개, Java 0개).
- **빌드**: `build.gradle.kts` (gradlew 래퍼 없음 → `gradle bootJar -x test`).
- **패키지 루트**: `github.lms.lemuel.xr`

```
backend/src/main/kotlin/github/lms/lemuel/xr/
├── LemuelXrApplication.kt      # 진입점
│
├── auth/            # 인증 (JWT, 익명 우선)
├── emotion/         # 감정 분류 (트랙 A 자동 분기)
├── scripture/       # 성경 본문 + pgvector 임베딩
├── content/         # 트랙 A — 일기·잠언·전도서·시편·실천 성찰
├── values/          # 1~7 가치 빌더
├── journey/         # 사용자 여정 진행
├── game/            # 트랙 B — 인물 미션 게임 세션
├── recovery/        # 회복/위기 자원
├── safety/          # 안전선 — CrisisKeywordScanner, SafetyAlert
├── ai/              # LLM 응답 생성·캐시·사용량 (사이드카 proxy)
├── tts/             # TTS 합성 (사이드카 proxy)
├── asset/           # XR/미디어 자산 매니페스트
├── analytics/       # 분석 뷰
├── outbox/          # 도메인 이벤트 아웃박스
├── common/          # 공통 — jwt · security · web · jpa · metrics · sidecar · jackson · chatops
└── config/          # 설정
```

### 2.1 바운디드 컨텍스트 내부 (헥사고날 공통 레이아웃)

각 컨텍스트는 동일한 3-레이어 구조를 따른다. 예 — `safety/`:

```
safety/
├── domain/                         # 순수 도메인 모델 (프레임워크 무의존)
│   ├── SafetyAlert.kt
│   └── CrisisResource.kt
├── application/                    # 유스케이스 + 아웃바운드 포트
│   ├── CrisisKeywordScanner.kt
│   ├── GetCrisisResourcesUseCase.kt
│   ├── RecordSafetyAlertUseCase.kt
│   └── port/out/                   # *Port 인터페이스 (SafetyAlertPort, ...)
└── adapter/
    ├── in/web/                     # REST 컨트롤러 (SafetyController)
    └── out/
        ├── persistence/            # *JpaEntity + *JpaRepository + *PersistenceAdapter
        └── metrics/                # Micrometer 어댑터
```

> **원칙**: 도메인 모델은 순수 Kotlin, 어댑터에서 JPA 엔티티와 매핑. `application/port/out/*Port` ↔ `adapter/out/persistence/*PersistenceAdapter` 로 연결. (헥사고날 세부는 [`ARCHITECTURE.md`](ARCHITECTURE.md))

### 2.2 REST 컨트롤러 (adapter/in/web)

`auth · recovery · values · content(6종) · game · journey · safety · ai(internal) · tts · scripture · emotion · analytics · asset`

### 2.3 리소스

```
backend/src/main/resources/
├── db/migration/       # Flyway V1~V12 정수 + V13~ 타임스탬프 (총 25개)
├── scenarios/          # 인물 시나리오 yml (joseph·moses·david·jesus·elijah·job)
└── manifests/          # 인물별 자산 매니페스트 (joseph/david/moses)
```

---

## 3. frontend/ — Next.js

```
frontend/
├── src/
│   ├── app/            # App Router
│   │   ├── joseph/  david/  moses/  jesus/   # 트랙 B 인물
│   │   ├── values/                            # 가치 빌더
│   │   ├── topics/                            # 트랙 A (journal·proverbs·ecclesiastes·practice)
│   │   └── api/[...path]/                      # 백엔드 프록시 라우트
│   ├── components/
│   ├── lib/
│   ├── types/
│   └── middleware.ts
├── tests/  (+ playwright.config.ts)
├── Dockerfile
└── package.json
```

---

## 4. ai/ · tts/ — Python 사이드카

```
ai/                     tts/
├── app.py              ├── app.py
├── providers.py        ├── selftest.py
├── tracing.py          ├── requirements.txt
├── requirements.txt    └── Dockerfile
└── Dockerfile
```

- `ai/` — LLM provider proxy (OpenAI / Gemini / Claude).
- `tts/` — 음성 합성.
- 백엔드가 `adapter/out/sidecar` (WebClient) 로 호출. 외부에 직접 노출하지 않음.

---

## 5. unity/ · content/ · scripts/

- `unity/`, `unity-stub/` — XR 게임(트랙 B). `unity-stub/Assets` 는 개발/CI 용 스텁.
- `content/{joseph,david,moses}/manifests/` — 인물별 콘텐츠·자산 매니페스트.
- `scripts/generate_scenes.py` — Scene yml 생성 유틸.

---

## 6. docs/ — 심화 문서

| 범주 | 파일 |
|---|---|
| 기획·로드맵 | `PLAN.md` · `BUILD-PLAN.md` · `MISSION.md` |
| 기능·플로우 | `FUNCTIONAL-SPEC.md` · `USER-FLOW.md` · `CROSS-MAPPING-VR-AR.md` |
| 아키텍처 | `BACKEND-ARCHITECTURE.md` · `BACKEND-API-DESIGN.md` · `AI-ARCHITECTURE.md` · `XR-INTEGRATION.md` · `DB-SCHEMA.md` |
| 콘텐츠 | `MVP-JOSEPH*.md` · `MVP-MOSES*.md` · `MVP-DAVID*.md` · `MVP-JESUS.md` · `TRACK-A-*.md` · `CONTENT-WORKFLOW.md` |
| AI·감정 | `EMOTION-CLASSIFIER.md` |
| 안전·거버넌스 | `safety-guidelines.md` · `ETHICS-LEGAL.md` · `governance/CLINICAL-REVIEW.md` · `governance/outreach/` |
| 참고 | `THEOLOGY-REFERENCES.md` · `RESEARCH-PSYCHOLOGY-PAPERS.md` · `SEQUENCE-DIAGRAMS.md` · `MEDIA-PLAN.md` · `research/` |
