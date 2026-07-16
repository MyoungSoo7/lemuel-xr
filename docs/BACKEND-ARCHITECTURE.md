# Backend 전체 아키텍처 — lemuel-xr

> **상태**: 설계 (DB V1~V12 구현됨, 코드 layer 진행 중)
> **최종 갱신**: 2026-05-21 (DB 구현 확인 후 보강 — §16 참조)
> **범위**: 사업계획서 기준 11개 주제 (Track A 1~7 + Track B 8~11) + 생성형 AI **전방위** 활용
> **상위 단일 진실 원천 (SOT) 분담**:
>   - 본 문서 — **전체 아키텍처·결정·로드맵**
>   - [`DB-SCHEMA.md`](./DB-SCHEMA.md) — DB 도메인 + V1~V12 마이그레이션 계획
>   - [`AI-ARCHITECTURE.md`](./AI-ARCHITECTURE.md) — AI 16 use case + multi-provider + LangChain
>   - [`XR-INTEGRATION.md`](./XR-INTEGRATION.md) — Backend ↔ XR 클라이언트 통합 규약
>   - [`BACKEND-API-DESIGN.md`](./BACKEND-API-DESIGN.md) — API 표면 (endpoint 27 개)
>   - [`ETHICS-LEGAL.md`](./ETHICS-LEGAL.md) — 윤리·법적 가이드
>   - [`CONTENT-WORKFLOW.md`](./CONTENT-WORKFLOW.md) — AI 생성 → 신학 검토 → 공개
>   - [`SEQUENCE-DIAGRAMS.md`](./SEQUENCE-DIAGRAMS.md) — Mermaid 시퀀스 7종
>   - [`USER-FLOW.md`](./USER-FLOW.md) — 사용자 여정 wireframe
>   - [`EMOTION-CLASSIFIER.md`](./EMOTION-CLASSIFIER.md) — 감정 분류 알고리즘
>   - [`FUNCTIONAL-SPEC.md`](./FUNCTIONAL-SPEC.md) — 기능 명세
>   - 인물 MVP — [`MVP-JOSEPH.md`](./MVP-JOSEPH.md) / [`MVP-MOSES.md`](./MVP-MOSES.md) / [`MVP-DAVID.md`](./MVP-DAVID.md)
>   - 트랙 A 상세 — [`TRACK-A-1-4-WISDOM-EMOTION.md`](./TRACK-A-1-4-WISDOM-EMOTION.md) / [`TRACK-A-5-7-ACTION-GUIDANCE.md`](./TRACK-A-5-7-ACTION-GUIDANCE.md)

본 문서와 다른 SOT 문서가 충돌하면 **도메인별 SOT 가 우선** — 본 문서는 상위 결정/연결만 다룬다.

본 문서는 사업계획서의 *프로젝트 개발계획* 11개 주제와 *기본 FLOW* + *생성형 AI 전방위 활용* 조건을 충족하는 **백엔드 전체 설계** 다. 시스템 컨텍스트 → 도메인 → 컨테이너 → 모듈 → 데이터 흐름 → 비기능 → 로드맵 순으로 정리.

---

## 0. Executive Summary

### 0.1 한 문장

> 11개 성경 주제(정적 회복 7 + 서사 게임 4)를 **3차원 진입(영성·감성·이성) × 정서 보호 가드레일** 하에서 제공하는 **AI 전방위** 백엔드 — Spring Boot(Kotlin) 모놀리스 + Python 사이드카(AI/TTS) + PostgreSQL(+pgvector) + Redis + R2 5-tier.

### 0.2 핵심 결정 7가지

| # | 결정 | 근거 |
|---|---|---|
| 1 | **모놀리스 (Spring Boot 4.0.4, Kotlin 2.2.20) + Python 사이드카 2개 (AI/TTS)** | 풀 MSA 비용 미정당화 (예상 동시 사용자 ≤ 1k). asat 의 *모놀리스 + Python 사이드카* 검증 패턴 재사용. (백엔드는 Java 25 → Kotlin 100% 마이그레이션 완료, Lombok 제거) |
| 2 | **헥사고날 아키텍처** | 11개 주제가 *유사 도메인 다른 콘텐츠* — 포트/어댑터로 콘텐츠 교체 가능하게. 기존 `JosephGameController` 구조 확장. |
| 3 | **이벤트 driven 부분 도입** (Redis Streams) | 분석 이벤트·LLM cache-warmup 은 비동기. 사용자 응답 경로는 동기. |
| 4 | **3차원 모드(영성·감성·이성) 는 세션 단위 일관성** | scene payload 가 모드별 다른 자산 반환. 모드 도중 변경 X. |
| 5 | **모든 LLM 호출은 Spring 통과** | 클라이언트 → 직접 LLM X. 캐시 hit rate 80%+ 목표. 키 보호. |
| 6 | **저장소 다층** | Postgres (관계+pgvector) / Redis (세션·캐시·rate limit) / R2 (wav·이미지) / 로컬 PVC (Postgres 데이터) |
| 7 | **GitOps 배포 — 기존 인프라 재사용** | helm-deploy 레포에 `charts/lemuel-xr` 추가, ArgoCD application 등록. K3s 클러스터 추가 비용 0. |
| 8 | **Outbox + Triple Idempotency 패턴 채택** | settlement 프로젝트 검증 패턴 차용. V11 `outbox_events` + `processed_events` 구현됨. Scene decide → 분석 이벤트 → ELK ship 의 트랜잭션 일관성 보장. |
| 9 | **AI 는 Multi-Provider + LangChain RAG** | OpenAI `gpt-4o-mini` (분류·NPC·게임 분기) + Anthropic `claude-3.5-sonnet` (묵상·시편·챗봇·신학 보조) + 자체 호스팅 (Coqui XTTS-v2·SDXL). PROVIDER_PRIORITY fallback. RAG 는 별도 Python 서비스. AI-ARCHITECTURE.md §1~2 참조. |
| 10 | **API 는 device-agnostic + 자산은 device-variant** | XR-INTEGRATION.md §1 — 백엔드는 디바이스 모름. asset_manifests(V10) 가 device·quality tier 별 URL 묶음 반환. |
| 11 | **pgcrypto row-level 암호화** | V5 / V12 에서 `diary_entries.body`, `user_psalms.raw_text`, `emotion_logs.raw_text`, `safety_alerts.snippet` 암호화. ETHICS-LEGAL.md §2.2 충족. |
| 12 | **콘텐츠 신학 검수 워크플로우** | V8 `content_versions` (draft → review → approved → published → archived) + `theology_reviews`. AI 생성 콘텐츠는 *반드시* approved 상태에서만 사용자 노출. CONTENT-WORKFLOW.md 참조. |

### 0.3 비용 예상 (MVP, 사용자 100명/월)

| 항목 | 월 비용 |
|---|---:|
| Google Gemini 1.5 Flash (감정·분기·묵상) | $20~50 (캐시 80%+ 적용) |
| TTS — 자체 Coqui XTTS-v2 (david 노드 ai-inference) | $0 (전기·하드웨어만) |
| R2 (wav·이미지) | $5 이하 |
| Postgres + Redis + Backend (K3s 재사용) | $0 |
| **합계** | **~$50/월** |

Phase 2 (1k 사용자 + 다국어 + 이미지 생성) 시 $300~500/월 가능. 자체 모델 fine-tune 검토.

---

## 1. 시스템 컨텍스트 (C4 Level 1)

```
                  ┌─────────────────────────────────────────┐
                  │             Person: 사용자                │
                  │  (Quest 3 / Vision Pro / Galaxy XR /     │
                  │   WebXR / Desktop fallback)              │
                  └────────────────────┬────────────────────┘
                                       │ HTTPS (JWT)
                                       ▼
       ┌──────────────────────────────────────────────────────────┐
       │              System: lemuel-xr Backend                    │
       │   ┌──────────────────────────────────────────────────┐    │
       │   │  Spring Boot 4.0.4 · Kotlin (모놀리스, hexagonal) │    │
       │   └──────┬──────────────┬──────────────┬────────────┘    │
       │          │              │              │                  │
       │   ┌──────▼──────┐ ┌─────▼──────┐ ┌────▼────────┐         │
       │   │ Python AI   │ │ Python TTS │ │ Image Gen   │         │
       │   │  (Gemini    │ │ (Coqui     │ │ (Phase 2 —  │         │
       │   │   sidecar)  │ │  XTTS-v2)  │ │  Stable     │         │
       │   │             │ │            │ │  Diffusion) │         │
       │   └─────────────┘ └────────────┘ └─────────────┘         │
       └──────────────────────────────────────────────────────────┘
                  │           │           │
       ┌──────────▼──────┐  ┌─▼──────┐  ┌─▼────────┐
       │ Google Gemini   │  │ R2     │  │ Postgres │
       │  (외부 API)      │  │ (자산) │  │ (메타+   │
       └─────────────────┘  └────────┘  │  pgvec)  │
                                         └──────────┘
                  │                       ▲
                  ▼                       │
          ┌────────────────┐     ┌────────┴────────┐
          │ Telegram Bot   │     │ Redis           │
          │  (운영 알람)    │     │ (세션·캐시·RL)   │
          └────────────────┘     └─────────────────┘
```

### 1.1 외부 의존성

| 시스템 | 용도 | Phase |
|---|---|---|
| Google Gemini API | LLM 추론 (감정 분류·서사 분기·묵상 생성) | 1 |
| Cloudflare R2 | wav·이미지·일기 백업 정적 자산 | 1 |
| Cloudflare CDN/Tunnel | 외부 진입 (이미 클러스터에 통합) | 1 |
| Telegram Bot API | 운영 알람 + ChatOps (asat·academy 와 공유) | 1 |
| Stable Diffusion (자체호스팅) | 성경 장면 이미지 생성 | 2 |
| OAuth Providers (Google/Apple) | 회원 시스템 | 2 |
| 한국선교사데이터 / Bible Gateway API | 다국어 본문 | 3 |

---

## 2. 11개 주제 도메인 분해

### 2.1 Track A (1~7) — 정적 회복 콘텐츠

각 주제는 **3차원 진입 모드** × **AI 활용 영역** 으로 분해.

#### Theme 1 — 일기와 묵상

```
도메인:
  - Journal (UUID, userId, topicId=1, text, mode, linkedEmotionLogId, createdAt)
  - MeditationResponse (journalId, llmText, scriptureRef, voiceUrl?, createdAt)

AI 활용:
  - 감정 분류 (입력 텍스트 → 7 emotion)
  - 개인화 묵상문 생성 (시편 톤, 사용자 일기 입력)
  - TTS (생성된 묵상문 음성, 출퇴근 듣기 시나리오)

API: /api/content/topics/1/scene
     /api/content/journal (POST, GET)
     POST /api/content/journal/{id}/meditate  ← 묵상 변환 트리거
```

#### Theme 2 — 잠언과 지혜

```
도메인:
  - ProverbCard (situationKey, scriptureRef, modernExplanation, embedding)
  - WisdomQuery (userId, situationText, recommendedCards[], createdAt)

AI 활용:
  - 상황 → 잠언 매칭 (pgvector + LLM rerank)
  - 사용자 상황에 맞춘 해석 reframing (CBT 패턴)

API: /api/content/topics/2/scene
     POST /api/content/wisdom/recommend  ← 상황 입력 → 카드 3장
```

#### Theme 3 — 전도서와 인생

```
도메인:
  - LifeViewCard (themeKey: 'meaningless'|'time'|'work'|'death'|'joy', scriptureRef, modernText)

AI 활용:
  - 사용자 *허무감* 입력 → 매칭된 카드 + 위로 한 문장
  - 시각화: 카드별 XR 환경 (석양·황혼·여명·별빛)
```

#### Theme 4 — 시편과 감정

```
도메인:
  - PsalmMapping (emotion → psalmRef[]). 8 emotion × N psalms
  - PsalmRendition (psalmRef, narrationUrl, durationMs, voiceId, mode)

AI 활용:
  - 감정 → 시편 매칭 (사전 큐레이션 + 의미 검색 fallback)
  - TTS 낭독 (시편 23편 등 사전 생성 + 캐시)
```

#### Theme 5 — 고통과 진리 (욥기)

```
도메인:
  - SufferingNarrative (jobChapterRange, snippet, theologicalExplanation, supportingText)
  - UserStory (userId, painText, matchedNarrativeId)

AI 활용:
  - 사용자 *고통 서술* → 욥기 발췌 + 신학 해설 매칭
  - **신학 검증 가드**: AI 가 *값싼 위로* 톤 금지 — 회사 자체 prompt guard
```

#### Theme 6 — 마음을 지키는 것

```
도메인:
  - HeartGuardSession (userId, durationSec, completedAt, breathingPattern)

AI 활용:
  - 호흡 가이드 음성 (TTS, 잠언 4:23 톤)
  - 사용자 불안 수준 reflection (5점 척도 전후 측정)
```

#### Theme 7 — 사람을 두려워하지 않는 것

```
도메인:
  - InterpersonalFearScenario (situationKey, scriptureRef, dailyTip)
  - PracticeLog (userId, scenarioId, completedAt, reflection)

AI 활용:
  - 다윗·다니엘 사례 매칭
  - 사용자 *실천 일기* 후 격려 응답
```

### 2.2 Track B (8~11) — 서사 게임 미션

각 인물은 **5~7개 Scene** + **3차원 모드** + **정서 보호 가드** 공유.

| # | 인물 | 핵심 감정 anchor | Scene 수 | 핵심 인터랙션 | LLM 호출 빈도 |
|---|---|---|---:|---|---|
| 8 | **요셉** (경제) | 결정 - 책임 - 분배 | 5 | 곡식 자루 잡기·분배 | Low (대부분 사전 캐시) |
| 9 | **모세** (정치) | 무자격 - 떨림 - 동행 | 6 | 신 벗기·5카드·지팡이 | Mid (5카드 조합 캐시) |
| 10 | **다윗** (외세) | 작음 - 모욕 - 정체성 | 6 | 수금·갑옷 입탈·sling | Mid (5돌 조합 + Scene 2 분기) |
| 11 | **예수** (영적) | (별도 담당자 godjinho — 본 문서 범위 외) | — | — | — |

도메인 공통:
```
GameSession (id, userId, character, scenarioId, mode, startedAt, completedAt,
             decisions JSONB, finalOutcome, exitReason?, appliedSafety JSONB)

ScenePayload (sceneId, type, narrationId, options, scriptureRefs[], hapticHint, durationEstimate)

DecisionRecord (sessionId, sceneId, type, value, durationMs, modeApplied, ts)
```

### 2.3 횡단 도메인

```
User (id, deviceType, safetyDefaults JSONB, createdAt)
EmotionLog (id, userId, rawText, primaryEmotion, secondaryEmotions JSONB, confidence)
ScripturePassage (id, ref, translation, book, chapter, verseStart/End, text, embedding vector(3072))
LlmCache (cacheKey, response, model, hitCount, createdAt)
TtsCache (cacheKey, textHash, voiceId, storageUrl, durationMs, hitCount)
InteractionEvent (id, sessionId, userId, type, sceneId?, value, ts)
JournalEntry (id, userId, topicId, text, linkedEmotionLogId, createdAt)
```

---

## 3. Bounded Context 매핑 (DDD)

```
                    ┌───────────────────────────────────────┐
                    │           [User & Auth]                │
                    │   게스트 UUID, JWT, 디바이스 등록       │
                    └───────────┬───────────────────────────┘
                                │
            ┌───────────────────┼───────────────────┐
            ▼                                       ▼
  ┌──────────────────┐                  ┌──────────────────┐
  │   [Emotion]       │                  │   [Safety]        │
  │  분류 + 이력      │                  │  haptic·skip·exit │
  └────────┬─────────┘                  └────────┬─────────┘
           │ 추천 트리거                          │ session-level
           ▼                                     ▼
  ┌──────────────────┐    ┌──────────────────────────────────┐
  │ [Content — Track A]│   │     [Game — Track B 4 인물]       │
  │  1~7 주제 콘텐츠   │    │  Joseph/Moses/David/Jesus        │
  │  journal/wisdom/   │    │  scene progression, decisions    │
  │  psalm/job/etc.    │    │  branching narrative             │
  └────────┬─────────┘    └────────┬─────────────────────────┘
           │                       │
           └──────┬────────────────┘
                  ▼
       ┌─────────────────────┐
       │   [Scripture]        │
       │  본문 + pgvector      │
       │  의미 검색            │
       └────────┬────────────┘
                ▼
   ┌──────────────────────────────────────────────────┐
   │           [AI Orchestration]                       │
   │   LLM proxy · cache · prompt template              │
   │   TTS proxy · cache · voice catalog                │
   │   Image gen (Phase 2) · embedding (offline batch)  │
   └────────┬───────────────────────────────────────────┘
            ▼
   ┌──────────────────────┐
   │   [Analytics]         │
   │  events · journey     │
   │  weekly report        │
   └──────────────────────┘
```

8개 bounded context. 각 context 는 자기 도메인·application·adapter 만 알고, 다른 context 와는 **명시적 API + 도메인 이벤트** 로만 통신.

---

## 4. 컨테이너 구조 (C4 Level 2)

### 4.1 컨테이너 목록

| Container | 기술 | 용도 | 노드 라벨 | Replicas |
|---|---|---|---|---:|
| `lemuel-xr-backend` | Spring Boot 4.0.4 + Kotlin 2.2.20 (JDK 25 툴체인, Kotlin JVM target 24) | API · orchestration · DB I/O | `general` | 2 |
| `lemuel-xr-ai` | Python 3.12 + FastAPI + google-genai | LLM proxy + prompt template | `ai-inference` (david) | 1 (Phase 2: 2) |
| `lemuel-xr-tts` | Python 3.12 + FastAPI + Coqui XTTS-v2 | TTS 생성 | `ai-inference` (david) | 1 |
| `lemuel-xr-postgres` | PostgreSQL 16 + pgvector | 관계 데이터 + 임베딩 | `ssd` (solomon) | 1 (PVC) |
| `lemuel-xr-redis` | Redis 7 | 세션·캐시·rate limit·streams | `general` | 1 (PVC small) |
| `lemuel-xr-minio` (opt) | MinIO | 일기 백업 (Phase 2) | `ssd` | 1 |

R2 / Cloudflare 는 컨테이너 외부 — 자산 저장 + 외부 진입.

### 4.2 트래픽 흐름

```
Unity Client (XR)
   │
   │ HTTPS
   ▼
Cloudflare (xr.lemuel.co.kr)
   │
   ▼
ArgoCD-managed Ingress (Traefik)
   │
   ▼
Spring backend (NodePort 30xxx via traefik)
   │
   ├─► Postgres (Cluster IP, asat-postgres 와 분리)
   ├─► Redis    (Cluster IP)
   ├─► AI sidecar (Cluster IP, ai-inference 노드)
   ├─► TTS sidecar (Cluster IP, ai-inference 노드)
   └─► R2 (외부 HTTPS, presigned URL)
```

### 4.3 K3s 노드 매핑

| 노드 | 역할 | XR 워크로드 |
|---|---|---|
| lemuel (101) | control-plane | — |
| ilwon (110) | control-plane, 무거운 워크로드 | postgres·redis 자연 스케줄 |
| solomon (108) | control-plane | (예비) |
| david (107) | worker, AI inference | ai sidecar + tts sidecar (`ai-inference=true` 라벨) |
| louise (109) | worker | backend pods |

기존 ASAT·academy 와 노드 공유, namespace 분리 (`xr-prod` / `xr-staging`).

---

## 5. Spring Module / Package Layout (Full)

```
github.lms.lemuel.xr/
│
├── LemuelXrApplication.kt       ← @SpringBootApplication
│
├── auth/                           ← (신규) [User & Auth]
│   ├── adapter/in/web/AuthController.kt
│   ├── adapter/out/persistence/{User,Device}{JpaEntity,Repository}.kt
│   ├── application/{CreateGuestUser,IssueJwt,RotateToken}UseCase.kt
│   └── domain/{User,Device,SafetyDefaults}.kt
│
├── emotion/                        ← 기존, 확장
│   ├── adapter/in/web/EmotionController.kt
│   ├── adapter/out/persistence/EmotionLog{JpaEntity,Repository}.kt
│   ├── adapter/out/llm/EmotionLlmClient.kt  ← AI sidecar 호출
│   ├── application/{ClassifyEmotion,RecommendByEmotion}UseCase.kt
│   └── domain/{Emotion,EmotionLog,Classification}.kt
│
├── content/                        ← (신규) [Content - Track A]
│   ├── adapter/in/web/
│   │   ├── ContentController.kt
│   │   ├── JournalController.kt
│   │   ├── WisdomController.kt
│   │   └── HeartGuardController.kt
│   ├── adapter/out/persistence/
│   │   ├── JournalEntry{JpaEntity,Repository}.kt
│   │   ├── ProverbCard{JpaEntity,Repository}.kt
│   │   ├── LifeViewCard{JpaEntity,Repository}.kt
│   │   ├── PsalmMapping{JpaEntity,Repository}.kt
│   │   ├── SufferingNarrative{JpaEntity,Repository}.kt
│   │   ├── HeartGuardSession{JpaEntity,Repository}.kt
│   │   └── InterpersonalScenario{JpaEntity,Repository}.kt
│   ├── application/
│   │   ├── RecommendTopicByEmotionUseCase.kt
│   │   ├── LoadTopicSceneUseCase.kt
│   │   ├── CreateJournalUseCase.kt
│   │   ├── GenerateMeditationFromJournalUseCase.kt   ← AI sidecar
│   │   ├── RecommendProverbsBySituationUseCase.kt     ← pgvector + LLM rerank
│   │   ├── MapEmotionToPsalmUseCase.kt
│   │   └── ...
│   └── domain/                     ← 7개 Theme 별 도메인 클래스
│       ├── Topic.kt (enum: JOURNAL/PROVERBS/.../FEAR)
│       ├── theme1_journal/{Journal,Meditation}.kt
│       ├── theme2_proverbs/ProverbCard.kt
│       ├── theme3_ecclesiastes/LifeViewCard.kt
│       ├── theme4_psalms/{PsalmMapping,PsalmRendition}.kt
│       ├── theme5_job/SufferingNarrative.kt
│       ├── theme6_heart/HeartGuardSession.kt
│       └── theme7_fear/InterpersonalScenario.kt
│
├── game/                           ← 기존, 일반화 + 인물 확장
│   ├── adapter/in/web/
│   │   ├── GameController.kt                     ← (신규) /api/game/{character}/*
│   │   ├── JosephGameController.kt               ← 기존 유지, alias
│   │   └── SessionController.kt                  ← /api/game/sessions/*
│   ├── adapter/out/persistence/
│   │   ├── GameSession{JpaEntity,Repository}.kt  ← 기존
│   │   ├── DecisionRecord{JpaEntity,Repository}.kt
│   │   └── Scenario{JpaEntity,Repository}.kt
│   ├── adapter/out/scenario/ScenarioYamlLoader.kt
│   ├── application/
│   │   ├── StartGameSessionUseCase.kt
│   │   ├── DecideSceneUseCase.kt
│   │   ├── CompleteGameSessionUseCase.kt
│   │   ├── ExitSessionUseCase.kt
│   │   ├── GenerateBranchOutcomeUseCase.kt       ← LLM 호출
│   │   └── MapEmotionToCharacterUseCase.kt
│   └── domain/
│       ├── Character.kt (enum: JOSEPH/MOSES/DAVID/JESUS)
│       ├── GameSession.kt                         ← 기존
│       ├── Scenario.kt
│       ├── Scene.kt (with SceneType enum)
│       ├── Decision.kt
│       ├── RecoveryMessage.kt
│       └── ApplicableMode.kt (영성/감성/이성/AUTO)
│
├── scripture/                      ← 기존, 확장
│   ├── adapter/in/web/ScriptureController.kt
│   ├── adapter/out/persistence/ScripturePassage{Entity,Repository}.kt
│   ├── adapter/out/embedding/EmbeddingClient.kt  ← AI sidecar
│   ├── application/
│   │   ├── GetPassageByRefUseCase.kt
│   │   ├── GetRangeUseCase.kt
│   │   ├── SemanticSearchUseCase.kt
│   │   └── ReindexEmbeddingsBatchJob.kt          ← @Scheduled
│   └── domain/{Reference,Passage,Translation}.kt
│
├── ai/                             ← (신규) [AI Orchestration]
│   ├── adapter/in/web/InternalLlmController.kt (X-Internal-Token)
│   ├── adapter/out/sidecar/AiSidecarClient.kt    ← WebClient → Python
│   ├── adapter/out/persistence/LlmCache{Entity,Repository}.kt
│   ├── application/
│   │   ├── GenerateLlmResponseUseCase.kt
│   │   ├── CacheKeyComputer.kt                   ← sha256(promptKey || JSON.stringify(variables))
│   │   ├── WarmupCacheUseCase.kt                 ← batch endpoint + scheduled
│   │   └── PromptTemplateRegistry.kt             ← .yaml 로 prompt 관리
│   └── domain/
│       ├── PromptKey.kt                          ← 'joseph.s2.monologue' 등 ID 체계
│       ├── LlmRequest.kt
│       └── LlmResponse.kt
│
├── tts/                            ← (신규) AI 의 하위 자매
│   ├── adapter/in/web/TtsController.kt
│   ├── adapter/out/sidecar/TtsSidecarClient.kt
│   ├── adapter/out/storage/R2StorageClient.kt
│   ├── adapter/out/persistence/TtsCache{Entity,Repository}.kt
│   ├── application/{SynthesizeTts,WarmupTtsBatch}UseCase.kt
│   └── domain/{Voice,Synthesis,SpeakingRate}.kt
│
├── safety/                         ← (신규) [Safety]
│   ├── adapter/in/web/SafetyController.kt        ← /api/game/sessions/{sid}/exit
│   ├── application/{EmergencyExit,SoftLanding}UseCase.kt
│   └── domain/{ExitReason,SoftLandingMessage}.kt
│
├── analytics/                      ← (신규) [Analytics]
│   ├── adapter/in/web/AnalyticsController.kt
│   ├── adapter/in/stream/InteractionEventConsumer.kt (Redis Streams)
│   ├── adapter/out/persistence/InteractionEvent{Entity,Repository}.kt
│   ├── adapter/out/elastic/EventShipperClient.kt (옵션, ELK 클러스터로)
│   ├── application/
│   │   ├── RecordEventUseCase.kt
│   │   ├── BuildJourneyUseCase.kt
│   │   └── GenerateWeeklyReportJob.kt            ← @Scheduled, asat ReportArtifact 패턴
│   └── domain/{InteractionEvent,Journey,WeeklyReport}.kt
│
└── common/                         ← 공유 인프라
    ├── ErrorCode.kt                              ← E_SESSION_INVALID 등
    ├── ProblemDetailMapper.kt                    ← @ControllerAdvice RFC 7807
    ├── RateLimitFilter.kt                        ← asat 패턴 재사용
    ├── InternalServiceTokenFilter.kt             ← asat 패턴 재사용
    ├── jwt/{JwtIssuer,JwtVerifier}.kt
    └── observability/{LoggingFilter,MetricsConfig}.kt
```

총 **9 bounded context · 패키지 수십 개 · @Controller 약 12개**. 모놀리스지만 *DDD-segregated* 구조라 향후 추출 가능.

> **헥사고날 규약 (2026-07 클린 리팩터 후)** — 각 bounded context 는
> `application/port/out/*Port` **아웃바운드 포트 인터페이스**를 두고, 이를
> `adapter/out/persistence/*PersistenceAdapter` (또는 `adapter/out/sidecar`,
> `adapter/out/metrics`) 가 구현한다. **포트는 도메인 타입만 주고받으며 JPA 엔티티를
> 노출하지 않는다** — 순수 도메인 모델은 `<ctx>/domain/` 에 있고, 어댑터 내부에서
> 도메인 ↔ JPA 엔티티를 매핑한다. 컨트롤러는 use-case 에만 의존하고 DTO 를 반환하며
> repository 를 직접 주입받지 않는다. HTTP 사이드카 클라이언트(ai·tts)는
> `adapter/out/sidecar` 에서 포트 뒤에, 메트릭은 `adapter/out/metrics` 의
> `*MetricsPort` 뒤에 둔다.
>
> **언어/관용구 (Java 25 → Kotlin 2.2.20 마이그레이션 완료, Lombok 제거)** — DTO 는
> `data class`(+ `companion object` 팩토리), 의존성은 **primary-constructor 주입**,
> 로거는 `companion object { private val log = LoggerFactory.getLogger(...) }`,
> JPA `@Entity` 는 가변 `var` 를 가진 Kotlin `class` (no-arg 는 `kotlin-jpa` 플러그인).
> Java `record`/Lombok `@Getter`/`@RequiredArgsConstructor`/`@Slf4j` 는 더 이상 없다.

**적용된 디자인 패턴**:

| 패턴 | 위치 |
|---|---|
| **Adapter** (헥사고날) | `adapter/out/*` 가 `application/port/out/*Port` 구현 |
| **Strategy** | `asset` 의 디바이스별 `InputMappingProvider` 빈 + `InputMappingResolver`; `game` 의 `ResponseResolver` |
| **Factory Method** | 도메인 companion 팩토리 — `GameSession.start`/`reconstitute`, `OutboxEvent.pending`, `LlmCache.freshEntry`, `TtsCache.freshEntry`, `CdrIndex.fromPractices` |
| **Template Method (합성)** | `common/sidecar/SidecarHttp` 를 ai·tts WebClient 사이드카 어댑터가 공유 |

---

## 6. 데이터 흐름 — 핵심 시나리오 3종

### 6.1 시나리오 A — *"오늘 너무 불안해" → Track A 매칭*

```
[Unity] → POST /api/emotion/classify { text, context }
            │
            ▼
[Spring auth filter] verify JWT → userId
            │
            ▼
[EmotionController.classify]
   → ClassifyEmotionUseCase.execute(text)
       → AiSidecarClient.classify(text)  ←──── [Python AI] POST /classify
                                                  ↓ Gemini 1.5 Flash
                                                  ↓ JSON Schema strict 모드
   ← { primary: ANXIETY, secondary: [LONELINESS], confidence }
   → EmotionLogRepository.save(...)
   → RecommendByEmotionUseCase.execute(ANXIETY)
       → Topic ID 4 (시편과 감정), 6 (마음 지키는 것) 룰 매칭
       → MapEmotionToCharacterUseCase.execute(ANXIETY)
       → Character.MOSES (Scene 4 두려움), DAVID (Scene 4 5돌)
   ← combined response

[Unity] ← 200 OK
        { primary, secondary, recommendations: { trackA: [...], trackB: [...] } }
```

**Latency 예산**: classify 600ms (Gemini) + 매핑 50ms = p99 ~800ms

### 6.2 시나리오 B — *모세 미션 Scene 3 분기 결정 (실시간 LLM)*

```
[Unity] → POST /api/game/moses/{sid}/decide
            { sceneId: 3, decision: { type: "DISTRIBUTE",
                                      value: ["throw","throw","heart","throw","heart"] } }
            │
            ▼
[GameController.decide]
   → DecideSceneUseCase.execute(sessionId, sceneId, decision)
       → GameSessionRepository.findById(sid) → 검증 (mode, currentScene, not exited)
       → 사용자 카드 패턴 분석 → branchId = "moses-s3-mixed"
       │
       ├── 캐시 hit? → LlmCacheRepository.findByKey("moses.s3.mixed:EMOTIONAL")
       │   ├── hit → 즉시 반환
       │   └── miss → GenerateLlmResponseUseCase.execute(promptKey, variables)
       │       → AiSidecarClient.generate(promptKey, variables)
       │           ←─── [Python AI] Gemini 1.5 Flash + system prompt
       │       → LlmCacheRepository.save(...)  (다음 호출에 재사용)
       │
       → GameSessionRepository.update(decisions += {moses:3 → ...}, currentScene=4)
       → Scene 4 payload load (scenarios/moses.yml)

[Unity] ← 200 OK
        { previousScene: 3, currentScene: 4, scenePayload, branchInfo: {monologueText} }
```

**Latency 예산**:
- Cache hit: 50ms (DB + serialize)
- Cache miss: 1500~2000ms (Gemini round-trip) — Unity 측 *"요셉이 생각하고 있어요…"* 마스킹 UI 권장

### 6.3 시나리오 C — *세션 emergency exit*

```
[Unity] → POST /api/game/sessions/{sid}/exit
            { reason: "TRAUMA_TRIGGER", atSceneId: 3 }
            │
            ▼
[SafetyController.exit]
   → ExitSessionUseCase.execute(sid, reason, sceneId)
       → GameSessionRepository.markExited(...)
       → SoftLandingUseCase.execute(reason)
           → SynthesizeTtsUseCase.execute("잠시 멈추셨네요. 다음에 다시 만나요.")
               ├── TtsCache hit → R2 presigned URL
               └── miss → TtsSidecarClient.synthesize(...)
                     ←─── [Python TTS] Coqui XTTS-v2
                     → R2 upload → presigned URL
   ← { sessionId, exitedAt, gentleMessage, softLandingAudioUrl }

[Analytics async] InteractionEventConsumer 가 Redis Streams 에서 "session_exit" 이벤트 처리,
                  postgres `interaction_events` 에 저장, ELK ship.
```

**핵심**: 실패 시나리오를 *부정적 시그널* 이 아니라 *사용자 보호 동작* 으로 분류.

---

## 7. AI 활용 — 8가지 시나리오 상세

사업계획서가 *"생성형 AI 전방위 활용"* 명시. PLAN.md §3 의 8가지를 백엔드 구현 단위로 분해.

| # | 시나리오 | 모델 / 도구 | 호출 위치 | 캐싱 전략 | 일일 호출량 (사용자 100명) |
|---|---|---|---|---|---:|
| 1 | **감정 분류** | Gemini 1.5 Flash + JSON schema | EmotionController.classify | 텍스트 hash 캐싱 (90% miss 가정 — 개인 텍스트라 캐싱 의미 적음) | ~500 |
| 2 | **개인화 묵상문 생성** | Gemini 1.5 Pro (품질 우선) | GenerateMeditationFromJournalUseCase | 캐싱 X (개인 일기 → 개인 응답) | ~200 |
| 3 | **게임 시나리오 분기** | Gemini 1.5 Flash | DecideSceneUseCase (Scene 4 등) | promptKey + variables hash → 영구 캐시 | ~300 (캐시 hit 80%, miss 60) |
| 4 | **위로 대화 (챗봇)** | Gemini 1.5 Flash + 시편 system prompt | ChatController (Phase 2) | 세션 짧음 → 캐싱 X. session memory 짧게 유지 | (Phase 2) |
| 5 | **TTS 낭독** | Coqui XTTS-v2 (자체) | SynthesizeTtsUseCase | text+voice+rate sha256 → R2 영구 캐시 | ~1000 (miss 5%) |
| 6 | **이미지 생성** | Stable Diffusion (자체, Phase 2) | ImageController (Phase 2) | prompt hash → R2 영구 캐시 | (Phase 2) |
| 7 | **주간 리포트** | Gemini 1.5 Pro + 사용자 데이터 prompt | GenerateWeeklyReportJob @Scheduled | 사용자별 1회 / 주 | ~14 (100명 / 7일) |
| 8 | **다국어 번역** | Gemini 1.5 Flash (대안: papago/deepl) | TranslationController (Phase 3) | 본문 hash → 영구 | (Phase 3) |

### 7.1 PromptTemplate 관리

`resources/prompts/*.yaml` 으로 외부화. 코드 재배포 없이 prompt 변경 가능.

```yaml
# resources/prompts/joseph.s4.brotherReunion.yaml
key: joseph.s4.brotherReunion
description: 형제 재회 시 요셉의 선택별 대사
variables: [choice, mode, savePercentage]
system: |
  당신은 청년 요셉입니다. 13년 만에 자신을 모르는 형제들 앞에서 ...
user: |
  사용자가 선택한 행동: {{choice}}
  적용 모드: {{mode}}
  Scene 2 저장 비율: {{savePercentage}}
  한국어 2~3문장으로 요셉의 첫 대사를 작성하세요.
guard:
  forbidden: ["하나님이 가해자를 정당화", "고통은 다 뜻이 있다"]
  required: ["용서", "섭리"]
```

PromptTemplateRegistry 가 부팅 시 모두 로드 + watch (Phase 2). cache key 생성 시 prompt **content hash** 도 포함 — prompt 바뀌면 자동 cache invalidate.

### 7.2 LLM 가드레일 — 신학·정서 보호

| 가드 | 구현 |
|---|---|
| **본문 hallucination 금지** | LLM 은 *해설·내면 독백* 만 생성. 본문 인용은 항상 DB 에서 lookup. response 후처리에서 *"인용 가짜 검출"* (정규식: `\(창세기 \d+:\d+\)`) → DB 검증 |
| **트라우마 가스라이팅 차단** | `forbidden` 토큰 목록 — *"고통도 뜻이 있다"*, *"네가 약해서"*, *"믿음이 부족해서"* 출력 시 즉시 fallback to canned response |
| **종교색 강도 사용자 선택** | `mode` 가 EMOTIONAL/RATIONAL 일 때 신학 용어 줄이고 *지혜 텍스트* 톤. SPIRITUAL 일 때 직접 표현 |
| **AI 출력 표시** | 모든 LLM 응답에 `_aiGenerated: true` 필드 추가. Unity 측 *"AI 보조"* 라벨 의무 표시 |

---

## 8. 비기능 요구사항

### 8.1 성능 SLO

| Endpoint Class | p50 | p99 | 시간 측정 위치 |
|---|---:|---:|---|
| auth/scripture (정적) | 50ms | 200ms | server-side |
| game/start, decide(cached) | 100ms | 400ms | server-side |
| game/decide(realtime LLM) | 1.2s | 2.5s | server-side (sidecar 포함) |
| emotion/classify | 700ms | 1800ms | server-side |
| tts/synthesize(cached) | 80ms | 300ms | server-side |
| tts/synthesize(miss) | 3s | 10s | server-side |
| content/recommend | 150ms | 500ms | server-side |

**Unity 클라이언트 측** 추가 + 네트워크 RTT (한국 내 ~30ms, Cloudflare 통과 ~50ms).

### 8.2 가용성

- **Backend**: 2 replica + HPA (CPU > 70% → 최대 5). 노드 1개 사망에도 무중단.
- **AI sidecar**: 1 replica. 사망 시 backend 가 *"AI 응답 지연 — fallback 메시지 표시"* 로 graceful degradation.
- **TTS sidecar**: 1 replica. 사망 시 *Unity 측 텍스트 fallback*.
- **Postgres**: 1 replica + 매일 pg_dump → R2. Phase 2 streaming replica.
- **Redis**: 1 replica + AOF persistence.

### 8.3 보안

| 영역 | 구현 |
|---|---|
| 인증 | JWT (HMAC-SHA256, secret SOPS encrypted in helm-deploy) |
| 내부 토큰 | `X-Internal-Token` (`/api/internal/*` 만, asat 패턴) |
| Rate Limiting | `RateLimitFilter` (사용자별 + IP별 버킷, Redis 백엔드) |
| Network Policy | xr-prod ns 에 default-deny + 명시 allow. asat 보안 점검 시 발견된 격차 해결 |
| Secret 관리 | SOPS-operator 로 git-encrypted. helm-deploy 의 `charts/lemuel-xr/secrets.sops.yaml` |
| HTTPS | Cloudflare edge ↔ Traefik. 평문 단계 없음 |
| LLM 입력 검증 | text 최대 1000자, 한국어/영어 외 거부, prompt injection 패턴 차단 |
| 개인정보 | 게스트 UUID + device hash. 일기는 사용자별 amazon AES-256 encrypted at rest. PII export API 제공 (개인정보보호법 대비) |

### 8.4 관측 (Observability)

```
Backend logs → fluent-bit → Logstash → Elasticsearch → Kibana
Backend metrics → micrometer → Prometheus → Grafana
Traces → OpenTelemetry → tempo (Phase 2)

알람:
  - Telegram 봇 (기존 server-monitor 와 통합)
  - p99 latency 상승 / 5xx rate / LLM cache hit rate < 60% / TTS sidecar down
```

### 8.5 배포 (GitOps)

```
inter-xr (코드 레포)
    │
    │ push to master
    ▼
GHA: k3s-images.yml → ghcr.io/myoungsoo7/lemuel-xr-{backend,ai,tts}:latest+:sha
    │
    ▼
argocd-image-updater (write-back: argocd)
    │
    ▼
ArgoCD Application: lemuel-xr-prod
    │ source: helm-deploy / charts/lemuel-xr
    │ selfHeal: true, prune: false
    ▼
K3s cluster (namespace: xr-prod)
```

asat 와 동일 GitOps 패턴 — 별도 도구·운영 노하우 추가 없음.

---

## 9. 데이터 모델 (DDL — Phase 1 종합)

§14 `BACKEND-API-DESIGN.md` 의 V3~V7 + 다음 보강:

```sql
-- V3 — Track A 도메인 테이블 (7 Theme 분)
CREATE TABLE proverb_cards (
    id              BIGSERIAL PRIMARY KEY,
    situation_key   VARCHAR(50) NOT NULL,
    scripture_ref   VARCHAR(50) NOT NULL,
    modern_text     TEXT NOT NULL,
    embedding       vector(3072),
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE life_view_cards (
    id              BIGSERIAL PRIMARY KEY,
    theme_key       VARCHAR(30) NOT NULL,    -- 'meaningless'|'time'|...
    scripture_ref   VARCHAR(50) NOT NULL,
    modern_text     TEXT NOT NULL,
    asset_skybox    VARCHAR(100)
);

CREATE TABLE psalm_mappings (
    id              BIGSERIAL PRIMARY KEY,
    emotion         VARCHAR(30) NOT NULL,
    scripture_ref   VARCHAR(50) NOT NULL,
    weight          NUMERIC(3,2) NOT NULL DEFAULT 1.0
);
CREATE INDEX idx_psalm_emotion ON psalm_mappings (emotion);

CREATE TABLE suffering_narratives (
    id                  BIGSERIAL PRIMARY KEY,
    job_chapter_range   VARCHAR(20) NOT NULL,
    snippet             TEXT NOT NULL,
    theological_text    TEXT NOT NULL,
    embedding           vector(3072)
);

CREATE TABLE heart_guard_sessions (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id),
    duration_sec    INT NOT NULL,
    breathing       VARCHAR(20),
    pre_anxiety     INT,  -- 1~5
    post_anxiety    INT,  -- 1~5
    completed_at    TIMESTAMP
);

CREATE TABLE interpersonal_scenarios (
    id              BIGSERIAL PRIMARY KEY,
    situation_key   VARCHAR(50) NOT NULL,
    scripture_ref   VARCHAR(50) NOT NULL,
    daily_tip       TEXT NOT NULL
);

-- V4 — chat / 위로 대화 (Phase 2)
CREATE TABLE chat_sessions (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    started_at      TIMESTAMP DEFAULT NOW(),
    ended_at        TIMESTAMP,
    mode            VARCHAR(20),
    messages        JSONB DEFAULT '[]'::jsonb
);

-- V5 — 주간 리포트 (asat ReportArtifact 패턴)
CREATE TABLE weekly_reports (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL,
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    storage_url     VARCHAR(500),       -- R2 url to PDF
    summary_json    JSONB,
    generated_at    TIMESTAMP
);

-- V6 — interaction events (analytics)
-- 이미 BACKEND-API-DESIGN.md §14 V7 에 정의됨
```

---

## 10. Phase별 구현 로드맵

### 10.1 Phase 1 — Joseph MVP + Track A Theme 1·4 (6주)

사업계획서의 *11개 중 우선 3개* (Theme 1·4 + Joseph) 로 MVP. *시연 가능한 가장 작은 단위*.

| Week | Backend 작업 |
|---|---|
| 1 | helm-deploy 차트 + ArgoCD 등록 + auth 모듈 + Spring profile 정리 |
| 2 | emotion classify 확장 + Track A topic 1·4 콘텐츠 골격 + content/recommend |
| 3 | Joseph game 일반화 (`/api/game/{character}/*`) + scenarios yaml loader |
| 4 | AI sidecar + TTS sidecar + 캐싱 + prompt template registry |
| 5 | Theme 1 journal + meditate · Theme 4 psalm + TTS 사전 생성 |
| 6 | 통합 테스트 + 5명 사용자 데모 + observability 셋업 |

### 10.2 Phase 2 — 모세·다윗 + Track A 2·3·6 (8주)

| Week | 작업 |
|---|---|
| 1~2 | 모세 시나리오 + 5카드 분기 + 본문 시드 (출 3~14) |
| 3~4 | 다윗 시나리오 + 5돌 분기 + sling + 본문 시드 (삼상 16~17, 시 23) |
| 5 | Theme 2 잠언 + pgvector + 상황 매칭 |
| 6 | Theme 3 전도서 + life view cards |
| 7 | Theme 6 마음 지키기 + breathing + 호흡 음성 |
| 8 | 사용자 테스트 20명 + 분석 + Phase 3 백로그 |

### 10.3 Phase 3 — 나머지 + 고도화 (12주+)

| 영역 | 작업 |
|---|---|
| 콘텐츠 | Theme 5 욥기 + Theme 7 대인 공포 + 예수 미션 (godjinho 협업) |
| 기능 | 위로 대화 챗봇 + 이미지 생성 + 주간 리포트 + 다국어 |
| 인프라 | OAuth 회원 시스템 + 멀티 플레이어 (공동체 묵상) |
| 분석 | 임상 검증 (한양대 임상심리 협업 모색) |

---

## 11. 위험 매트릭스

| # | 위험 | 가능성 | 영향 | 대응 |
|---|---|:---:|:---:|---|
| 1 | LLM 비용 폭증 (캐시 hit rate 미달) | M | H | 1) Gemini Flash 우선 2) 사전 캐시 batch 3) Rate limit 4) 자체 모델 fine-tune 검토 (Phase 3) |
| 2 | LLM hallucination (본문 왜곡) | M | H | RAG + 본문은 DB only + 후처리 정규식 검증 + canned fallback |
| 3 | 트라우마 가스라이팅 (*"고통도 뜻"*) | L | **Critical** | forbidden token list + 인간 감수 (목사 자문 영입) + 사용자 신고 기능 |
| 4 | 현대인의 성경 라이선스 분쟁 | M | H | Phase 1 fair use 한정 (테스터 ≤10명) + 개역개정 fallback + 라이선스 협의 병행 |
| 5 | XR 디바이스 다양성으로 백엔드 부적합 | L | M | OpenXR + 디바이스 ID로 자산 분기. 백엔드는 device-agnostic |
| 6 | 사용자 정서 사고 (Scene 2 모욕 자극) | M | H | skip 옵션 + 사전 경고 + soft landing audio + 신고 채널 |
| 7 | 개인정보(일기) 유출 | L | **Critical** | AES-256 at rest + JWT 인증 강제 + audit log + PII export API |
| 8 | 사이드카 장애로 미션 진행 불가 | M | M | Cached fallback + graceful degradation + 알람 즉시 |
| 9 | 사업계획서 *11개 전체* 데드라인 압박 | H | M | Phase 별 우선순위 + STATUS.md 매주 갱신 + 미달 항목 사전 공지 |
| 10 | godjinho (예수 미션 담당) 협업 지연 | M | M | 본 백엔드는 character-agnostic — JESUS 도메인은 시나리오만 추가하면 동작. 백엔드 차원에서 독립 가능 |

---

## 12. 사업계획서 매핑 검증 표

11개 주제 × *생성형 AI 전방위* 각 항목이 어디서 다뤄지는지 확인용:

| 사업계획서 항목 | 본 설계의 위치 |
|---|---|
| 1. 일기와 묵상 | §2.1 Theme 1 도메인 + §7 AI #2 묵상 생성 + §5 content/journal·meditate |
| 2. 잠언과 지혜 | §2.1 Theme 2 + §7 AI #3 reframing + §5 wisdom/recommend |
| 3. 전도서와 인생 | §2.1 Theme 3 + §5 LifeViewCard |
| 4. 시편과 감정 | §2.1 Theme 4 + §7 AI #5 TTS 낭독 + §5 PsalmMapping |
| 5. 고통과 진리 | §2.1 Theme 5 + §11 risk #3 가스라이팅 가드 |
| 6. 마음을 지키는 것 | §2.1 Theme 6 + breathing + TTS 호흡 음성 |
| 7. 사람을 두려워하지 않는 것 | §2.1 Theme 7 |
| 8. 경제적 구원자 요셉 | §2.2 + 기존 docs/MVP-JOSEPH.md + §5 game/joseph |
| 9. 정치적 구원자 모세 | §2.2 + docs/MVP-MOSES.md + §5 game/moses |
| 10. 외세적 구원자 다윗 | §2.2 + docs/MVP-DAVID.md + §5 game/david |
| 11. 영적 구원자 예수 | §2.2 (godjinho 담당, 본 백엔드는 character-agnostic 으로 수용 준비) |
| **기본 FLOW (감정→1~7 / 게임→8~11)** | §6.1 시나리오 A — emotion/classify 응답에 trackA + trackB 동시 반환 |
| **생성형 AI 전방위** | §7 8가지 시나리오 모두 매핑 (감정·묵상·분기·대화·TTS·이미지·리포트·번역) |

→ **사업계획서 요구사항 100% 커버**.

---

## 13. Multi-XR 플랫폼 지원 — 백엔드 책임 경계

> **배경**: 본 시스템은 **Meta Quest 3 + Apple Vision Pro + Galaxy XR (Android XR)** 3종을 동시에 지원해야 한다. 백엔드는 클라이언트 측 XR SDK 차이에 직접 개입하지 않지만, *어떤 차이를 흡수하고 어떤 차이를 노출할지* 의 경계가 명확해야 한다.

### 13.1 플랫폼 비교 — 백엔드 관점

| 항목 | Meta Quest 3 | Apple Vision Pro | Galaxy XR (Android XR) |
|---|---|---|---|
| OS / 런타임 | Android (Horizon OS) | visionOS | Android XR (Google) |
| Client 빌드 | Unity 6 + Meta XR SDK + OpenXR | Unity 6 + PolySpatial + RealityKit *or* native | Unity 6 + Jetpack XR + OpenXR |
| 자산 포맷 | glTF/glb, FBX, BC7 텍스처 | USDZ + RealityKit Reality Composer | glTF/glb, ASTC 텍스처 (preferred) |
| 입력 | 컨트롤러 + 손 트래킹 (양쪽) | 손 + 시선만 (컨트롤러 없음) | 손 + 시선 + 옵션 컨트롤러 |
| 햅틱 | 컨트롤러 진동 (좌우 분리) | **없음** (애플 정책) | 컨트롤러 진동 (선택) |
| 패스스루 | RGB color passthrough | 최고 품질 (MR 우선) | 보통 ~ 양호 |
| 시선 트래킹 | Quest Pro 만 (3은 ❌) | 표준 (정밀) | 표준 |
| 공간 오디오 | Meta XR Spatial Audio | PHASE + binaural | OpenXR Spatial Audio |
| 네트워크 | Wi-Fi 6E | Wi-Fi 6E | Wi-Fi 6E + 셀룰러 옵션 |
| 화면 | 2064×2208 / 120Hz | 3660×3200 / 90Hz | ~Quest 3 동급 (2026 추정) |
| Store 정책 검토 | Meta Horizon (성인 콘텐츠·정치·종교 가이드) | App Store (종교 콘텐츠 더 엄격) | Google Play |

→ **백엔드가 흡수해야 하는 차이**: 자산 포맷, 입력 시맨틱, 햅틱 유무, 시선 트래킹 유무, store 정책별 콘텐츠 변종.
→ **백엔드가 노출해도 되는 차이**: 디바이스 ID(`QUEST_3`/`VISION_PRO`/`GALAXY_XR`), 캐퍼빌리티 플래그, 자산 variant URL.

### 13.2 추가 설계 원칙 (Multi-XR 전용)

1. **API contract 는 device-agnostic** — endpoint 시그니처에 `device` 가 들어가지 않는다. 동일 endpoint 가 모든 디바이스에서 동작.
2. **자산은 device-variant** — 동일 scene 의 자산을 *디바이스별 variant* 로 미리 빌드 후 R2 에 업로드. 응답 시 device 에 맞는 URL 만 반환.
3. **입력은 *시맨틱 action* 으로 추상화** — 클라이언트가 *"이 컨트롤러 A 버튼"* 이 아닌 *"GRAB / RELEASE / GAZE_DURATION / POINT_AT"* 같은 의미 단위로 보낸다. 백엔드는 컨트롤 종류를 모름.
4. **햅틱은 *권고(hint)* 만 보내고 클라이언트가 결정** — backend payload 에 `hapticHint: { intensity, durationMs, pattern }` 를 boolean 이 아닌 hint 로. Vision Pro 는 무시, Quest 3 는 컨트롤러 진동으로 매핑.
5. **시선 트래킹 없어도 동작** — Scene 설계 시 *시선 응시* 필수 인터랙션은 *옵션* 으로. Quest 3 / 시선 미지원 디바이스는 *시간 경과* 또는 *손가락 포인터* 로 대체.
6. **store 정책별 빌드 분기** — 백엔드는 *동일 API* 지만, 클라이언트 빌드 시 *store 별 콘텐츠 필터링* 옵션 (예: 폭력적 시각 효과 강도). 백엔드는 `clientProfile` 헤더로 트래픽 구분만.

### 13.3 Capability Negotiation — 세션 시작 시 디바이스 자기 신고

`POST /api/game/{character}/start` 요청 body 확장:

```json
{
  "mode": "EMOTIONAL",
  "safety": { ... },
  "linkedEmotionLogId": 12345,
  "client": {
    "deviceType": "VISION_PRO",
    "osVersion": "visionOS 2.0",
    "appVersion": "1.0.0",
    "capabilities": {
      "controllers": false,
      "handTracking": true,
      "eyeTracking": true,
      "haptics": false,
      "passthrough": "MR_HIGH",
      "spatialAudio": true,
      "displayHz": 90,
      "memoryClassMb": 8192,
      "languageRegion": "ko-KR"
    }
  }
}
```

백엔드 응답 (scenePayload) 에 *디바이스 맞춤 자산·인터랙션*:

```json
{
  "sceneId": 3,
  "type": "DISTRIBUTE",
  "assets": {
    "skybox": "https://r2.../skybox-3660.usdz",         // Vision Pro 만
    "ambient": "https://r2.../ambient.wav",
    "models": [
      { "id": "card-1", "url": "https://r2.../card-1.usdz" }
    ]
  },
  "interactions": [
    {
      "id": "card-grab",
      "semantic": "GRAB",
      "inputModes": ["HAND_PINCH", "GAZE_DWELL"],         // controllers 미보유 → 손/시선 자동
      "dwellMs": 1500,                                    // 시선 응시 시간 (eyeTracking 있을 때만 적용)
      "hapticHint": { "intensity": 0.0 }                  // VP 는 무시. Quest 는 약한 진동
    }
  ],
  "fallback": {
    "ifGazeUnsupported": "POINTER_REQUIRED",              // Quest 3 같은 무 시선 디바이스용 대안
    "ifHapticUnsupported": "AUDIO_CUE"                    // VP 용 — 진동 대신 사운드
  }
}
```

→ 백엔드는 **capability snapshot 을 받아서 payload 를 가공**. 클라이언트는 *추가 분기 로직 없이* 그대로 렌더.

### 13.4 Asset Variant Strategy

#### 13.4.1 자산 빌드 파이프라인

```
원본 자산 (Blender/Maya)
    │
    ├─► glTF/glb (Quest 3, Galaxy XR — Android 빌드 친화)
    │     └─► BC7 texture (Quest), ASTC texture (Galaxy XR)
    │
    └─► USDZ (Vision Pro — Apple PolySpatial)
          └─► RealityComposer scene

R2 upload (variant 별 prefix):
  /assets/quest3/scenes/joseph/s2/...
  /assets/visionpro/scenes/joseph/s2/...
  /assets/galaxyxr/scenes/joseph/s2/...
```

#### 13.4.2 자산 카탈로그 테이블 (DDL V8)

```sql
CREATE TABLE scene_assets (
    id              BIGSERIAL PRIMARY KEY,
    scenario_id     VARCHAR(50) NOT NULL,        -- 'joseph-mvp-v1'
    scene_id        INT NOT NULL,
    asset_key       VARCHAR(100) NOT NULL,       -- 'skybox', 'card-1'
    device_type     VARCHAR(20) NOT NULL,        -- 'QUEST_3' | 'VISION_PRO' | 'GALAXY_XR' | 'COMMON'
    format          VARCHAR(20) NOT NULL,        -- 'glb' | 'usdz' | 'wav' | 'png' ...
    storage_url     VARCHAR(500) NOT NULL,       -- R2 URL
    size_bytes      BIGINT,
    checksum_sha256 VARCHAR(64),
    quality_tier    VARCHAR(20),                 -- 'LOW' | 'MID' | 'HIGH'
    created_at      TIMESTAMP DEFAULT NOW(),
    UNIQUE (scenario_id, scene_id, asset_key, device_type, format, quality_tier)
);

CREATE INDEX idx_scene_assets_lookup
  ON scene_assets (scenario_id, scene_id, device_type);
```

`COMMON` 디바이스 타입 = wav/png 같은 *디바이스 무관* 자산. `device_type = COMMON OR device_type = $clientDevice` 로 lookup.

#### 13.4.3 Quality Tier — 디바이스/메모리 기반 자동 선택

```
client.capabilities.memoryClassMb ≥ 6144  → HIGH
client.capabilities.memoryClassMb 3072~   → MID
그 외                                     → LOW
```

Quest 3 (8GB) 기본 HIGH, Vision Pro 기본 HIGH, Galaxy XR 시뮬레이션 MID. 사용자가 *"고품질 모드"* off 하면 한 단계 강제 다운.

### 13.5 Input Semantic 표준화

#### 13.5.1 시맨틱 액션 ENUM

```
GRAB         — 객체 잡기 (컨트롤러 grip / 손 pinch)
RELEASE      — 잡은 객체 놓기 (release / pinch-open)
THROW        — 잡은 상태에서 속도 임계 이상으로 release
POINT_AT     — 객체 가리키기 (raycast - 손가락 / 컨트롤러 ray / 시선 dwell)
GAZE_DURATION— 시선 응시 시간 (eyeTracking 필요. fallback: 손가락 hover)
CONFIRM      — 결정 확정 (트리거 / 핀치 / dwell after gaze)
CANCEL       — 결정 취소 (B 버튼 / 두번 손 가로젓기)
LOCOMOTION   — 이동 (teleport / smooth — 사용자 prefer)
GESTURE_*    — 신 벗기, 카드 가슴에 가져가기 등 특수 (semantic ID 로 정의)
```

각 액션은 **디바이스 입력 매핑 테이블** 에서 정의:

```yaml
# resources/input-mapping/quest3.yml
GRAB: { source: "controller", binding: "grip", threshold: 0.5 }
        | { source: "hand", binding: "pinch", threshold: 0.7 }
POINT_AT: { source: "controller", binding: "raycast" }
        | { source: "hand", binding: "index_finger_raycast" }
GAZE_DURATION: { source: "head", binding: "head_direction_dwell" }  # eye tracking 없으므로 fallback

# resources/input-mapping/visionpro.yml
GRAB: { source: "hand", binding: "pinch", threshold: 0.7 }
POINT_AT: { source: "eye+hand", binding: "look_and_pinch" }
GAZE_DURATION: { source: "eye", binding: "eye_dwell", supported: true }
```

클라이언트가 이 매핑을 *부팅 시 backend 에서 다운로드* → Unity Input Actions 에 동적 binding. **`/api/config/input-mapping?device=VISION_PRO`** endpoint 추가.

#### 13.5.2 백엔드 수신 payload 표준

decide 요청의 `decision.value` 는 디바이스가 무엇이든 *동일 시맨틱 표현*:

```json
{
  "type": "DISTRIBUTE",
  "value": [
    { "stone": "fear", "action": "GRAB->RELEASE", "targetSlot": "pocket-1",
      "inputMode": "HAND_PINCH",         // 분석용 메타, 백엔드 결정 영향 X
      "durationMs": 1240 }
  ]
}
```

→ 백엔드 게임 로직은 `inputMode` 를 보지 않는다. 분석/QA 만 사용.

### 13.6 Haptic Hint Protocol

햅틱은 *권고(hint)* 로 제공. 클라이언트가 디바이스 능력에 맞춰 해석.

```json
{
  "hapticHint": {
    "intensity": 0.6,           // 0.0 ~ 1.0
    "durationMs": 200,
    "pattern": "PULSE_SOFT",    // PULSE_SOFT | PULSE_HARD | RUMBLE | NONE
    "channel": "LEFT_HAND",     // LEFT_HAND | RIGHT_HAND | BOTH
    "fallback": {
      "audioCueId": "haptic-fallback-soft-pulse"  // VP 처럼 햅틱 없는 경우
    }
  }
}
```

Vision Pro 클라이언트는 `fallback.audioCueId` 의 짧은 사운드를 spatial audio 로 재생 — *진동의 청각 변환*.

### 13.7 Store 정책 + 콘텐츠 가드

#### 13.7.1 Store 별 정책 요약

| Store | 핵심 가이드 (종교·심리 콘텐츠 관련) |
|---|---|
| Meta Horizon | 종교 콘텐츠 허용, 단 *정치적 선동·우월주의 금지*. AI 생성 콘텐츠 disclosure 권장. |
| Apple App Store | **종교 콘텐츠 더 엄격** — *분파 간 모욕*, *개종 압박* 금지. Mental health 청구 시 disclaimer + 전문가 자문 명시 필수. |
| Google Play | 종교 콘텐츠 허용, *cultic 마케팅 금지*. AI 생성은 출시 신고 의무 (2025+). |

#### 13.7.2 백엔드 대응

1. **AI 생성 출력 자동 라벨링** — 모든 LLM 응답에 `_aiGenerated: true` + `_generationModel: "gemini-1.5-flash"` 메타. 클라이언트 UI 에 *"AI 보조"* 표시 의무.
2. **클라이언트 프로파일 헤더** — `X-Client-Profile: meta` | `apple` | `google` — store 별 *콘텐츠 강도 다이얼*. 예: Apple 빌드는 Scene 5 골리앗 무너지는 효과 강도 -1 단계, *"위협적 음성"* 사전 경고 + 자체 toggle off 가능.
3. **사용자 신고 endpoint** — `POST /api/safety/report` (콘텐츠·AI 응답·시각 효과). Store 심사 대비 즉시 응답 가능 시스템 보유.
4. **Mental health disclaimer 자동 노출** — 사용자 첫 진입 시 *"이 앱은 의료·심리치료를 대체하지 않습니다. 위기 시 1393 (한국 자살예방상담전화)"* 화면. 첫 진입 기록 user 테이블에.

### 13.8 Cross-Device Session Continuity (Phase 2)

> 사용자가 Vision Pro 에서 Scene 2 까지 진행, Quest 3 으로 이어서 Scene 3 부터.

#### 설계

```
GameSession.userId 동일 → 모든 디바이스에서 같은 세션 fetch 가능
GameSession.lastDeviceType — 마지막 진행 디바이스 기록

POST /api/game/sessions/{sid}/resume?onDevice=QUEST_3
  → currentScene 페이로드 (디바이스 변경된 capability 로 재생성)
  → 이전 디바이스의 *입력 메타데이터* 무시 (decisions 본문은 유효)
```

#### 함정

- 자산 캐시는 디바이스별 무효 → 재다운로드 시간 필요. 클라이언트 *"디바이스 전환 감지: 자산 준비 중…"* UX 필요.
- 햅틱 hint 가 Quest → Vision Pro 전환 시 *없어진다* — 사용자가 *"진동 없는 게 어색하다"* 느낄 수 있음. 사전 메시지로 reframing.
- 시선 트래킹 인터랙션은 Quest 3 에서 fallback 됨 — 진행은 가능하나 *"VP 에서 한 응시 결정"* 의 데이터를 *덜 정밀하게* 분석.

### 13.9 분석 — 디바이스 차원 추가

`interaction_events` 테이블에 다음 컬럼 추가 (V9):

```sql
ALTER TABLE interaction_events
  ADD COLUMN device_type VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
  ADD COLUMN input_mode  VARCHAR(30),    -- 'HAND_PINCH' | 'CONTROLLER_GRIP' | 'EYE_DWELL' | ...
  ADD COLUMN client_version VARCHAR(20);

CREATE INDEX idx_events_device ON interaction_events (device_type, ts);
```

ELK Kibana 대시보드에 *디바이스별 SLO·CTR·완주율* 비교 패널 추가.

핵심 분석 질문 (Phase 1 끝낼 때):
- 디바이스별 *평균 세션 완주율* 차이
- *햅틱 있는 Quest vs 없는 VP* 의 사용자 만족도 차이
- *시선 응시 인터랙션* 의 정밀도 vs 손가락 fallback 정밀도
- 자산 다운로드 latency p99 — 디바이스/지역별

### 13.10 추가 위험

| # | 위험 | 가능성 | 영향 | 대응 |
|---|---|:---:|:---:|---|
| 11 | Vision Pro USDZ 빌드 파이프라인 학습 곡선 | H | M | Phase 1 Week 1~2 는 *Quest 3 빌드 우선* + Vision Pro 빌드는 Week 4 부터. Apple 개발자 문서 + PolySpatial 샘플 1주 학습 시간 확보 |
| 12 | Vision Pro App Store 심사 거절 (종교 콘텐츠) | M | H | 사전 *Mental health disclaimer + AI label + 전문가 자문* 명시. App Review Team 사전 문의 옵션 |
| 13 | Quest 3 시선 트래킹 없어 인터랙션 부족 | M | M | 모든 *"시선 응시"* Scene 에 손가락 hover fallback 필수. 본 설계 §13.5 에 명시 |
| 14 | 자산 R2 다운로드 모바일 회선에서 느림 | M | M | 자산 분할 + 사전 캐싱 + LOW quality tier 강제 옵션 |
| 15 | 햅틱 없는 VP 사용자 *"감각이 부족"* 피드백 | M | L | 햅틱 대체 audio cue (§13.6) + 시각 강조 (light flash) 보강 |
| 16 | 3 store 각 정책 변경 추적 부담 | H | L | 분기별 store 정책 리뷰 — STATUS.md 갱신 항목 |

### 13.11 API 추가 — Multi-XR 전용

새 endpoint 3개 추가 (BACKEND-API-DESIGN.md §3 보강 항목):

| Endpoint | Method | 용도 |
|---|---|---|
| `/api/config/input-mapping?device={type}` | GET | 디바이스별 입력 시맨틱 매핑 yaml 반환. 클라이언트 부팅 시 fetch + Input Actions binding |
| `/api/config/asset-manifest?device={type}&scenario={id}` | GET | scenario 전체 자산 URL + checksum 리스트. 클라이언트 사전 캐싱 결정용 |
| `/api/safety/report` | POST | 사용자 콘텐츠/AI 응답 신고. store 심사 대응 |

### 13.12 보완된 핵심 결정 (§0.2 보강)

§0.2 의 7 결정에 다음 2 항목 추가:

| # | 결정 | 근거 |
|---|---|---|
| 8 | **API 는 device-agnostic + 자산은 device-variant** | API contract 안정성 + 자산 효율의 균형. 클라이언트가 API 분기를 만들지 않게. |
| 9 | **입력은 시맨틱 액션으로 추상화, 햅틱은 hint** | 3 플랫폼의 입력/햅틱 차이를 *동일 API* 로 흡수. 새 디바이스 추가 시 input-mapping yaml 만 추가하면 동작. |

### 13.13 매핑 검증 — Multi-XR 요구사항 충족

| 요구사항 | 본 설계의 충족 |
|---|---|
| Quest 3 / Vision Pro / Galaxy XR 동시 지원 | §13.1 + §13.2 + §13.4 (asset variant) + §13.5 (input semantic) |
| 백엔드 동일, 클라이언트만 분기 | §13.3 capability negotiation + device-agnostic API |
| 디바이스별 입력 차이 흡수 | §13.5 input-mapping yaml + endpoint |
| 햅틱 유무 흡수 | §13.6 haptic hint protocol |
| 시선 트래킹 유무 흡수 | §13.3 fallback.ifGazeUnsupported |
| Store 정책별 대응 | §13.7 client profile + AI label + disclaimer |
| 디바이스 전환 시 연속성 | §13.8 cross-device resume (Phase 2) |
| 디바이스별 분석 | §13.9 interaction_events device 컬럼 |

→ **Multi-XR 3 플랫폼 100% 백엔드 측 흡수 가능**. 클라이언트는 *Unity OpenXR + 디바이스별 SDK 분기* 만 책임.

---

## 14. 미해결 / 다음 합의 필요

1. **인물 5번째 (예수) 의 백엔드 통합 시점** — godjinho 와 시나리오 합의 후 character enum 에 JESUS 추가하는 단순 작업. 일정 합의 필요.
2. **OAuth 도입 트리거** — 회원 수가 ?명 넘으면 게스트 → 회원 마이그레이션. 임계값 결정.
3. **자체 LLM fine-tune 시점** — Gemini 비용 월 $300 넘으면? 또는 응답 품질 issue 누적 시?
4. **임상 검증 협업 (Phase 3)** — 한양대 임상심리 그룹 / 정신건강의학과 자문 접근 — 외부 IRB 필요할 수 있음.
5. **다국어 첫 타겟** — 일본·대만? 또는 동남아? Bible 라이선스가 더 유연한 곳 우선.
6. **신학 자문 영입** — 목사 1명 (Track A 본문 검수 + Track B 시나리오 신학 검토). 인력·예산 합의 필요.

---

## 15. 2026-05-21 — DB 구현 현황 기준 보강

> 본 섹션은 *DB V1~V12 + 17 docs 확인 후* (2026-05-21) 위 §0~§14 의 가정과 *실제 구현*을 정렬한 보강 메모. §0~§14 에 모순되는 항목은 본 섹션이 우선한다.

### 15.1 DB 구현 현황 (V1~V12)

```
V1  init_schema                   → users, emotion_logs, game_sessions, scripture_passages,
                                     llm_cache, tts_cache (5 base tables)
V2  seed_scripture_genesis_41_45  → 창세기 41~45 본문 시드 (현대인의 성경, fair use)
V3  expand_identity_emotion       → users 컬럼 보강 (user_type, faith_tone, preferred_mode,
                                     haptic_intensity, skip_intro_silence, data_retention_days,
                                     deleted_at), devices, app_sessions, emotion_logs 컬럼 보강
                                     (intensity, chosen_dimension, recommended_*),
                                     recovery_metrics
V4  expand_game_domain             → game_sessions 컬럼 보강 (chosen_dimension,
                                     triggered_by_emotion_log_id, closing_message,
                                     scene_count_completed, duration_seconds),
                                     game_decisions, scene_views
V5  content_tracks_a               → pgcrypto + diary_entries, user_psalms,
                                     proverbs_interactions, ecclesiastes_views
V6  scripture_embeddings           → pgvector + scripture_embeddings (HNSW 인덱스),
                                     scripture_passages.theme_tags / character_tags
V7  safety_domain                  → safety_alerts (위기 키워드 매칭), crisis_resources
                                     (자살예방상담전화 1393, 정신건강상담 1577-0199,
                                     청소년 1388, 생명의전화 1588-9191 시드됨)
V8  theology_domain                → content_versions (draft/review/approved/published/archived),
                                     theology_reviews (reviewer · decision · concern_tags)
V9  ai_domain                      → llm_cache 컬럼 보강 (prompt_template_version, hit_count,
                                     expires_at), tts_cache 보강, llm_usage (비용 추적),
                                     llm_usage_daily MATERIALIZED VIEW
V10 asset_manifests                → asset_manifests (미션·씬·디바이스·quality_tier 별 묶음),
                                     asset_downloads (다운로드 추적·실패 재시도)
V11 events_outbox                  → outbox_events (트랜잭션 보장 큐), processed_events
                                     (Triple Idempotency L1 — settlement 패턴)
V12 analytic_views                 → v_user_30d_summary 외 분석 뷰 + 인덱스
```

→ **§0.2 의 V3~V7 *제안* 은 모두 *실제 V3~V12 로 구현됨*** + 4개 도메인 추가 (recovery, safety, theology, outbox, asset). §0.4 비용 추정도 V9 `llm_usage` 가 실측 데이터로 대체 가능해짐.

### 15.2 §0~§14 에서 보강·정정 필요한 항목

| § | 본 문서 가정 | 실제 구현 (V1~V12) | 정정 |
|---|---|---|---|
| §0.2 #5 | LLM 호출 backend 통과 + Gemini Flash | **OpenAI gpt-4o-mini + Anthropic claude-3.5-sonnet 멀티 프로바이더 + LangChain RAG** | §0.2 #9 로 업데이트 완료. AI-ARCHITECTURE.md §1 가 SOT. |
| §0.4 | $50/월 (Gemini 기준) | gpt-4o-mini + claude-3.5-sonnet 혼합 (use case별 라우팅) — 실측 비용은 `llm_usage_daily` view 로 추적 | $50~150/월 추정 폭으로 보정. AI-ARCHITECTURE.md §0 의 cost/콜 표 참조. |
| §2.2 표 | 인물 4명 도메인 공통 구조 (Decision Schema 통일) | game_decisions.decision JSONB 가 캐릭터별 자유 schema (DB-SCHEMA.md §13 의 예시 매핑) | 호환. 단 *분석 편의* 를 위해 캐릭터별 권장 schema 합의 필요 (DB-SCHEMA §13 의 4종 예시가 기준). |
| §2.3 | User, EmotionLog, GameSession, ... 8 도메인 | + **devices, app_sessions, recovery_metrics, user_psalms, ecclesiastes_views, safety_alerts, crisis_resources, content_versions, theology_reviews, llm_usage, asset_manifests, outbox_events, processed_events** — 13 표 추가됨 | §2 에 누락된 도메인 7종 (recovery, safety, theology, asset, outbox) 보강 — §15.3 참조. |
| §3 Bounded Context | 8 contexts | **+ recovery (회복 지표), safety (위기 자원), theology (콘텐츠 검수), asset (자산 카탈로그)** — 12 contexts | §15.3 의 매핑 도식으로 갱신. |
| §5 패키지 layout | 9 패키지 (auth/emotion/content/game/scripture/ai/tts/safety/analytics/common) | + **recovery, theology, asset, outbox** 4 패키지 추가 필요 | §15.4 의 보강된 layout 참조. |
| §7 AI 8 시나리오 | 8가지 | **AI-ARCHITECTURE.md 가 16가지** (감정·추천 rerank·일기 묵상·위기 키워드·잠언·시편·시편 다듬기·게임 분기·NPC 대화·TTS·이미지·주간 리포트·키워드 추출·신학 보조·챗봇·번역) | AI-ARCHITECTURE.md 가 SOT. 본 §7 표는 발췌 요약. |
| §9 DDL 제안 (V3~V6) | 제안 단계 | **V1~V12 전부 작성됨** (rollback 노트 포함) | §9 는 *역사 메모* 로 보존, 신규 변경은 DB-SCHEMA.md + lemuel-xr-flyway-migration skill 가이드. |
| §10 로드맵 Week 1 | helm-deploy + ArgoCD + auth 모듈 | **DB 12 마이그레이션 + scripture 시드 + JosephGameController + EmotionController 골격 완성** | 로드맵은 *현재 단계* 기준 재산정: "JPA Entity + Use Case + Adapter 구현" 단계로 진입. §15.5 참조. |
| §11 위험 #2 hallucination | RAG + 본문 DB only + 후처리 검증 | + **CONTENT-WORKFLOW.md 의 신학 검수 단계 (V8 content_versions)** 가 추가 가드 | 위험 #2 완화 ↑. content_versions.status='approved' 만 사용자 노출. |
| §11 위험 #3 가스라이팅 | forbidden token list | + **lemuel-xr-mental-health-safety skill 의 5개 안전선 (R1~R5)** + safety_alerts (V7) 자동 매칭 + crisis_resources 시드 | 위험 #3 완화 ↑↑. ETHICS-LEGAL.md §3 가 SOT. |
| §13 Multi-XR | 본 문서 §13 신규 작성 | **XR-INTEGRATION.md (560줄)** 가 더 상세한 SOT | XR-INTEGRATION.md 가 우선. 본 §13 은 *발췌 요약*. 충돌 시 XR-INTEGRATION 따름. |

### 15.3 Bounded Context 갱신 (12 contexts)

§3 의 8 contexts → 12 contexts 로 확장:

```
┌──────────────────────────────────────────────────┐
│   [Identity & Auth]                                 │
│   users, devices, app_sessions                       │
└──────┬─────────────────────────────────────────────┘
       │
       ├─► [Emotion]           emotion_logs (+intensity, chosen_dimension)
       ├─► [Recovery] ★신규     recovery_metrics (PHQ-9 류 자체 지표)
       │
       ├─► [Content — Track A]
       │     diary_entries, user_psalms, proverbs_interactions, ecclesiastes_views
       │
       ├─► [Game — Track B]
       │     game_sessions, game_decisions, scene_views
       │
       ├─► [Scripture]
       │     scripture_passages, scripture_embeddings (pgvector)
       │
       ├─► [Safety] ★신규
       │     safety_alerts, crisis_resources
       │
       ├─► [Theology] ★신규
       │     content_versions, theology_reviews (AI 생성 → 승인 워크플로우)
       │
       ├─► [AI Orchestration]
       │     llm_cache, llm_usage, tts_cache (멀티 프로바이더 라우팅)
       │
       ├─► [Asset] ★신규
       │     asset_manifests, asset_downloads (XR 디바이스·quality_tier 별)
       │
       ├─► [Outbox] ★신규
       │     outbox_events, processed_events (Triple Idempotency)
       │
       └─► [Analytics]
             interaction events (analytic views V12)
```

### 15.4 패키지 layout 보강 (§5 갱신)

§5 의 9 패키지에 4 추가:

```
github.lms.lemuel.xr/
├── auth/             ← §5 그대로
├── emotion/          ← §5 그대로
├── recovery/         ★신규 — RecoveryMetricsScheduler + GetRecoveryTrendUseCase
│   ├── adapter/out/persistence/RecoveryMetric{Entity,Repository}.kt
│   ├── application/{ComputeDailyMetrics, GetTrendOver30Days}UseCase.kt
│   └── domain/{RecoveryMetric, Trend, RiskSignal}.kt
├── content/          ← §5 + theme별 도메인 추가 (diary, psalm, proverbs, ecclesiastes)
├── game/             ← §5 그대로
├── scripture/        ← §5 그대로 (V6 embedding HNSW 인덱스 활용)
├── safety/           ★확장 — V7 safety_alerts 자동 매칭
│   ├── adapter/in/web/{SafetyController,CrisisResourceController}.kt
│   ├── adapter/in/scheduler/SafetyAlertScannerJob.kt
│   ├── application/{DetectCrisisKeyword, SuggestResource, AcknowledgeAlert}UseCase.kt
│   └── domain/{SafetyAlert, CrisisResource, Severity}.kt
├── theology/         ★신규 — V8 content_versions 워크플로우
│   ├── adapter/in/web/ContentVersionController.kt  (admin only)
│   ├── application/{SubmitForReview, ApproveContent, PublishContent}UseCase.kt
│   └── domain/{ContentVersion, TheologyReview, Status: DRAFT/REVIEW/APPROVED/PUBLISHED/ARCHIVED}.kt
├── ai/               ← §5 그대로 (multi-provider routing 적용)
├── tts/              ← §5 그대로
├── asset/            ★신규 — V10 asset_manifests
│   ├── adapter/in/web/AssetManifestController.kt
│   ├── adapter/out/persistence/{AssetManifest,AssetDownload}{Entity,Repository}.kt
│   ├── application/{GetManifestForDeviceQuality, RecordDownload}UseCase.kt
│   └── domain/{AssetManifest, DeviceType, QualityTier}.kt
├── outbox/           ★신규 — V11 outbox_events + Triple Idempotency
│   ├── adapter/in/scheduler/OutboxRelayJob.kt
│   ├── adapter/out/persistence/{OutboxEvent,ProcessedEvent}{Entity,Repository}.kt
│   ├── application/{PublishEventTx, MarkProcessed, DeduplicateByIdempotencyKey}UseCase.kt
│   └── domain/{OutboxEvent, IdempotencyKey, EventStatus}.kt
├── analytics/        ← §5 + V12 views 활용
└── common/           ← §5 그대로
```

→ 총 **13 패키지** (auth, emotion, recovery, content, game, scripture, safety, theology, ai, tts, asset, outbox, analytics, +common).

### 15.5 로드맵 재산정 (§10 갱신)

**현재 도달 단계**: DB 12 마이그레이션 완성 + Joseph/Emotion 컨트롤러 골격 + AI/TTS Python 사이드카 골격.

**다음 6 sprint 우선순위** (각 sprint = 1주):

| Sprint | 목표 | 산출물 |
|---|---|---|
| 1 | Identity + Outbox 골격 | auth 패키지 (게스트 JWT + RateLimit + InternalToken), outbox 패키지 + OutboxRelayJob @Scheduled |
| 2 | Game 도메인 일반화 | `/api/game/{character}/*` GameController + ScenarioYamlLoader. Joseph 호환 유지 |
| 3 | Emotion 확장 + Safety scanner | classify 응답에 recommendations · safety scanner 가 위기 키워드 매칭 시 crisis_resource 추천 |
| 4 | Content Track A — Theme 1·4 | diary entries + user psalms + AI 묵상 변환 (claude-3.5-sonnet) + TTS 시편 23 사전 생성 |
| 5 | Theology workflow + AI 생성 콘텐츠 게이트 | content_versions DRAFT → REVIEW 자동 + reviewer admin UI · approved 만 user 노출 |
| 6 | Asset manifest + Multi-XR | `/api/config/asset-manifest` 구현, Quest 3 빌드용 변종 우선 + Vision Pro USDZ 변종 추가 |

→ **6주 만에 Phase 1 MVP** (요셉 + Theme 1·4 + 안전·신학 검수 가드) 가능 추정.

### 15.6 추가 보강 — DB 가 강제하는 설계 결정

V1~V12 가 *암묵적으로 강제* 하는 결정들 (본 문서 §0~§14 에 명시 안 됐던 것):

1. **3차원 모드는 *세션 단위*, 사용자 default 는 *faith_tone*** — `users.faith_tone` (strong/balanced/soft) 와 `users.preferred_mode` (spiritual/emotional/rational/null) 가 분리됨. faith_tone 은 *신앙 톤 강도*, preferred_mode 는 *3차원 진입 모드*. 두 dimension 이 직교.

2. **`abandoned_at` 이 `completed_at` 과 분리** — game_sessions 가 *완료* / *중단(emergency exit)* / *진행 중* 의 3-상태 모델. 안전 exit 후 final_outcome 이 'safe_exit' 인 게 §6.3 흐름과 일치.

3. **recovery_metrics 는 일별 cron 으로 채움** — *실시간 계산 X*. `RecoveryMetricsScheduler` @Scheduled(cron="0 0 4 * * *") 자정 4시 작업 필요.

4. **safety_alerts.severity** 4단계 — `low / medium / high / critical`. crisis_resource 표시 임계값은 medium 이상.

5. **content_versions 5-상태** — `draft → review → approved → published → archived`. AI 생성 후 자동 *draft*, 신학 자문 승인 시 *approved*, 공개 시 *published*. 사용자 노출 시점은 **published 만**.

6. **scripture_passages.translation 다중성** — `modern` (현대인의 성경) / `rev` (개역개정) / `niv` / `esv`. 같은 reference 가 multiple translation 으로 저장. **번역별 사용자 선택 가능** (사용자 메타에 preferred_translation 추가 권장).

7. **scripture_embeddings 차원 1536** — text-embedding-3-small. §13.4 의 *3072* 차원 가정은 정정 필요. embedding model 은 `embed_model` 컬럼으로 추적.

8. **outbox + idempotency_key 패턴** — settlement Triple Idempotency 의 L1 (event-level 중복 차단) 가 V11 에 구현됨. Scene decide 등 *멱등성 보장 필수* 작업은 client 에 idempotency_key 받아 processed_events 에 INSERT … ON CONFLICT 패턴 사용.

### 15.7 인스턴스 검수 — 본 문서 vs 실제 상태 충돌 항목

| 본 문서 | 실제 (DB/문서) | 처리 |
|---|---|---|
| §6.2 시나리오 B의 `LlmCacheRepository.findByKey("moses.s3.mixed:EMOTIONAL")` | V9 llm_cache.cache_key 패턴은 `"moses:scene3:cards:TTHTH"` 식 (DB-SCHEMA §9) | 본 §6.2 의 키 예시를 *concept only* 로 보고, 실제 키 패턴은 AI-ARCHITECTURE.md + DB-SCHEMA.md 따름. |
| §8 TTS endpoint 응답에 `cacheKey: sha256(text+voiceId+rate)` | V1 tts_cache.text_hash + voice_id (sha256 의 text_hash 만) — voiceId 와 rate 는 별도 컬럼 | 키 생성 알고리즘만 합의: `sha256(text)` + `voice_id` + `speaking_rate` 의 composite key. |
| §13.4.3 quality tier 결정 식 (memoryClassMb 기반) | V10 asset_manifests.quality_tier 는 LOW/MID/HIGH — 매핑 정책은 백엔드 책임 | 매핑 정책 함수 명세화 필요 (asset/application/SelectQualityTierByCapability) — sprint 6 작업. |

### 15.8 다음 PR 단위 (구현 진입)

본 §15 보강 후 첫 PR:

1. **package skeleton PR** — §15.4 의 13 패키지 디렉토리 + 기본 Application/Domain 인터페이스 생성 (코드 없이 파일 구조만). lemuel-xr-flyway-migration / lemuel-xr-theology-tone skill 가이드 준수.
2. **auth + outbox PR** — Sprint 1 산출물.
3. **Game generalization PR** — Sprint 2.

PR 마다 SEQUENCE-DIAGRAMS.md 갱신 (lemuel-xr-mermaid-sequence skill) + STATUS 갱신 (별도 STATUS.md 신규 추가 권장).

---

## 16. 참고

- 본 문서는 **사업계획서 + PLAN.md + BUILD-PLAN.md + MVP-JOSEPH/MOSES/DAVID + TRACK-A-1-4** 의 *단일 진실 원천*
- API 표면 상세는 [`BACKEND-API-DESIGN.md`](./BACKEND-API-DESIGN.md)
- asat (`inter-asat`) 의 동일 패턴: hexagonal + Python 사이드카 + helm-deploy GitOps + RateLimitFilter + InternalServiceTokenFilter + ReportArtifact
- academy 의 동일 패턴: scenarios yaml 외부화, multi-character 일반화
- 인프라: [GitOps 전문가의 시야 — 36개 Application 운영]({기존 blog 글}) 의 패턴이 본 시스템에 그대로 적용
