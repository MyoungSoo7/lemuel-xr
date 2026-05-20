# AI-ARCHITECTURE — AI 활용 통합 설계

> *"생성형 AI 는 전방위적으로 사용예정"* (원본 기획서) — 무엇이·어떻게·얼마에 쓰이는지 한 곳에.

---

## 0. AI 사용처 (16 개) 한눈에

| # | 기능 | 모델/도구 | 트리거 | 응답 시간 | 비용/콜 |
|---|---|---|---|---|---|
| **1** | 감정 분류 | gpt-4o-mini | 사용자 텍스트 입력 | < 2초 | $0.00015 |
| **2** | 콘텐츠 추천 | rule-based + LLM rerank | 감정 분류 후 | < 100ms | $0 |
| **3** | 일기 → 묵상 변환 | claude-3.5-sonnet | 사용자 명시 요청 | < 3초 | $0.003 |
| **4** | 위기 키워드 검출 | regex (로컬) | 모든 입력 | < 10ms | $0 |
| **5** | 잠언 카드 추천 | 임베딩 검색 (pgvector) | Theme 2 진입 | < 200ms | $0.00001 (embedding) |
| **6** | 시편 매칭 | 임베딩 검색 (pgvector) | Theme 4 진입 | < 200ms | $0.00001 |
| **7** | 사용자 시편 운율 다듬기 | claude-3.5-sonnet | 사용자 요청 | < 3초 | $0.003 |
| **8** | 게임 분기 독백 (요셉/모세/다윗/예수) | gpt-4o-mini (캐시 우선) | Scene 결정 후 | < 1초 (캐시 hit) | $0.0002 (miss) |
| **9** | NPC 대화 한 줄 (아론·형들·NPC) | gpt-4o-mini (캐시) | Scene 진입 | < 1초 | $0.0002 |
| **10** | TTS (캐릭터 음성) | Coqui XTTS-v2 (self-hosted) | 콘텐츠 재생 | < 5초 (사전 생성) | $0 (서버) |
| **11** | 이미지 생성 (성경 장면) | SDXL (self-host) + Midjourney (premium) | 콘텐츠 제작 단계 | 사전 | $0~0.05 |
| **12** | 주간 회복 리포트 요약 | claude-3.5-sonnet | Day 7/30 | < 4초 | $0.005 |
| **13** | 키워드 추출 (사용자 패턴) | gpt-4o-mini | 일별 cron | < 2초 | $0.0001 |
| **14** | 신학 검토 보조 (AI 1차 스크리닝) | claude-3.5-sonnet | 콘텐츠 생성 시 | < 5초 | $0.005 |
| **15** | 챗봇 위로 응답 | claude-3.5-sonnet (긴 컨텍스트) | 사용자 명시 요청 | < 4초 | $0.005 |
| **16** | 다국어 번역 (Phase 3) | gpt-4o | Phase 3 | < 3초 | $0.005 |

---

## 1. LLM 선택 기준

| Provider | 모델 | 강점 | 사용처 | 비용 |
|---|---|---|---|---|
| OpenAI | **gpt-4o-mini** | 빠름·싸다·structured output | 분류·NPC 대화·키워드 | $0.15/1M in, $0.60/1M out |
| OpenAI | **text-embedding-3-small** | 임베딩 | scripture 벡터 | $0.02/1M |
| Anthropic | **claude-3.5-sonnet** | 한국어 자연·신학적 미묘 | 묵상 변환·시편 다듬기·챗봇 | $3/1M in, $15/1M out |
| Anthropic | **claude-3-haiku** | 빠름·싸다 | gpt-4o-mini fallback | $0.25/1M in, $1.25/1M out |
| Self-host | **Coqui XTTS-v2** | 한국어 음성 cloning | TTS 4 캐릭터 | infrastructure |
| Self-host | **SDXL** | 이미지 | 성경 장면 배경 | infrastructure |

### Multi-Provider 전략

```python
PROVIDER_PRIORITY = {
    "classify_emotion": ["openai:gpt-4o-mini", "anthropic:claude-3-haiku"],
    "polish_psalm":     ["anthropic:claude-3.5-sonnet", "openai:gpt-4o"],
    "game_branch":      ["openai:gpt-4o-mini", "anthropic:claude-3-haiku"],
    "weekly_report":    ["anthropic:claude-3.5-sonnet", "openai:gpt-4o"],
}
```

→ Primary 실패 시 Fallback. 비용·품질 다르지만 *완전 다운 방지*.

---

## 2. Orchestration — LangChain

### 왜 LangChain?

- 다중 LLM provider 추상화
- RAG (Retrieval-Augmented Generation) — 성경 임베딩 검색 자연 통합
- Prompt 버전 관리 (LangSmith)
- Tracing — 사용자 케이스 디버깅
- Python 생태계 풍부

### 대안 — *직접 호출* vs LangChain

| 시나리오 | 권장 |
|---|---|
| 단순 LLM 호출 (분류·NPC 대화) | 직접 OpenAI SDK |
| RAG (성경 검색 + LLM) | **LangChain** |
| 다단계 chain (분류 → 검색 → 생성) | **LangChain** |
| Agent (도구 사용) | Phase 3, LangChain |

→ 단순 호출은 Spring 의 *AI Layer Adapter* 가 직접. RAG·chain 은 별도 Python 서비스 (Spring 이 HTTP 호출).

### 아키텍처

```
┌──────────────────────────────────────────────────┐
│ Spring Boot (Backend)                             │
│  ├ EmotionController                              │
│  └ → AiOrchestrationClient (HTTP)                 │
└────────────────────┬──────────────────────────────┘
                     │ POST /ai/classify
                     │ POST /ai/recommend
                     │ POST /ai/polish
                     ▼
┌──────────────────────────────────────────────────┐
│ Python AI Service (FastAPI + LangChain)           │
│  ├ /ai/classify   → OpenAI / Claude               │
│  ├ /ai/recommend  → 임베딩 검색 + LLM rerank      │
│  ├ /ai/polish     → Claude                        │
│  ├ /ai/tts        → Coqui XTTS-v2                 │
│  └ /ai/image      → SDXL                          │
└──────────────────────────────────────────────────┘
                     │
                     ▼
              External LLM API + 자체 호스팅 모델
```

→ **Spring 이 도메인 로직 처리**, **Python 이 AI 호출 orchestration**.

---

## 3. Prompt 버전 관리

### 디렉토리 구조
```
ai-service/
├── prompts/
│   ├── emotion_classifier/
│   │   ├── v1.0.txt        # 첫 출시
│   │   ├── v1.1.txt        # 한국어 표현 보강
│   │   └── current → v1.1  # symlink
│   ├── meditation_generation/
│   │   ├── spiritual_v1.0.txt
│   │   ├── emotional_v1.0.txt
│   │   └── rational_v1.0.txt
│   ├── joseph_scene2/
│   ├── joseph_scene3/
│   ├── moses_scene3/
│   ├── david_scene4/
│   └── jesus_scene*/  (godjinho 작업)
└── prompt_loader.py
```

### 변경 시 정책

1. 새 버전 `v1.x` 파일 추가 (기존 유지)
2. `current` symlink 만 갱신
3. A/B 테스트 (5% 트래픽) 후 100% 전환
4. 30일 후 old version archive

---

## 4. 캐싱 전략 — 비용 70% 절감 목표

### 캐시 키 패턴

| 사용처 | 키 | TTL |
|---|---|---|
| 감정 분류 | `cls:{sha256(text)}` | 24h |
| 묵상 변환 | `med:{user_id}:{diary_id}` | 영구 |
| 게임 분기 독백 | `game:{char}:{scene}:{decision_pattern}` | 영구 |
| 시편 매칭 | `psalm:{emotion}:{intensity}` | 1h |
| NPC 대화 | `npc:{char}:{scene}:{npc}:{user_choice}` | 영구 |

### 캐시 hit rate 예상

| 사용처 | 예상 hit rate | 비용 절감 |
|---|---|---|
| 감정 분류 | 30~40% | $1.5 → $0.9 |
| 게임 분기 (5~10 분기) | **80%+** | $0.001 → $0.0002 |
| NPC 대화 | **90%+** | 거의 무료 |
| 묵상 변환 (개인화) | 0% | 캐시 X |
| 시편 매칭 (감정 별) | **70%** | $0.0001 → $0.00003 |

### 사전 캐싱 (Pre-warming)

게임 시작 전 *모든 분기 조합* 사전 생성:
- 요셉: 3 분기 (1/5 · 1/3 · 1/2 저장) + 3 분배 패턴 = **9 조합**
- 모세: 5장 카드 × 2 선택 = 32 조합, 결말 톤 3개로 압축 = **3**
- 다윗: 7 돌 중 5개 = 21 조합, 톤 5개로 압축 = **5**
- 예수: godjinho 가 정의

→ 게임 시작 시 *LLM 호출 0회*. 비용 0.

---

## 5. RAG (성경 검색) — 자세히

### 인덱싱

```python
# 일회성 임베딩 생성 (V10 마이그레이션에서)
for passage in scripture_passages:
    embedding = openai.embeddings.create(
        model="text-embedding-3-small",
        input=passage.text
    )
    db.scripture_embeddings.insert(
        passage_id=passage.id,
        embedding=embedding.data[0].embedding
    )
```

비용 (1회): 1,189 시편 + 출 3~14 + 삼상 16~17 + 마태~요한 핵심 = ~3000 절 × $0.00001 = **$0.03**

### 검색

```python
# 사용자 감정 → 시편 매칭
emotion_text = "외로움 + 지침"
emotion_embedding = embed(emotion_text)  # 1 회 호출

# pgvector cosine similarity
SELECT p.reference, p.text, e.embedding <=> :query AS distance
FROM scripture_passages p
JOIN scripture_embeddings e ON e.passage_id = p.id
WHERE p.character_tags && ARRAY['david']  -- 필요 시 필터
ORDER BY distance ASC
LIMIT 5;
```

→ p95 응답 200ms. 비용 $0.00001/회.

---

## 6. TTS — Coqui XTTS-v2 (자체 호스팅)

### 왜 자체 호스팅?

| 서비스 | 비용 | 한국어 품질 | 음성 cloning |
|---|---|---|---|
| ElevenLabs | $5~99/월 | ⭐⭐⭐⭐ | ✅ |
| OpenAI TTS | $15/1M chars | ⭐⭐⭐ | ❌ |
| Google Cloud TTS | $4/1M chars | ⭐⭐⭐ | ❌ |
| **Coqui XTTS-v2 self-host** | **$0** (인프라만) | ⭐⭐⭐⭐ | ✅ |

→ Coqui 가 *비용 0 + 음성 cloning + 한국어 4성* 으로 압도. 단점: 자체 GPU 필요.

### 인프라

- David 노드 (`ai-inference=true` 라벨) 의 RTX 3060 GPU
- Python FastAPI 컨테이너
- 사전 생성한 wav 는 R2 (S3) 에 저장
- 사용자가 듣기 누르면 R2 URL CDN 통해 스트리밍

### 음성 카탈로그

| 캐릭터 | 톤 | 사용 Scene |
|---|---|---|
| 요셉 | 청년 (20대 중반) | Scene 2~5 내면 독백 |
| 파라오 | 중년 권위 | 요셉 Scene 1 |
| 모세 | 중년 (떨림) | 모세 Scene 3 변명 |
| 떨기나무 | 내레이터 (직접적 신 표현 회피) | 모세 Scene 2 |
| 아론 | 형, 신뢰감 | 모세 Scene 4 |
| 다윗 (소년) | 청소년 | 다윗 Scene 1, 4 |
| 엘리압 | 형 (날카로움) | 다윗 Scene 2 |
| 골리앗 | 거인 (낮은 베이스 + reverb) | 다윗 Scene 5 |
| 예수 | 내레이터 톤 | 예수 미션 (godjinho) |
| 시편 낭독자 | 평온·여성/남성 선택 | 트랙 A Theme 4 |

---

## 7. 이미지 생성 — SDXL + Midjourney

### 사용처

| 용도 | 도구 | 빈도 |
|---|---|---|
| 시편 카드 일러스트 | SDXL self-host | 콘텐츠 추가 시 |
| 게임 배경 (이집트·광야·갈릴리) | Midjourney premium | 일회성 |
| 사용자 입력 시각화 (옵션, Phase 3) | SDXL | 사용자 요청 |

### 톤 가이드라인

- *실사풍 회피* — 종교 인물 시각화 부담
- *추상화 + 일러스트* 권장
- *밝은 톤·평온* 우선 (불안 자극 최소)
- 인물 얼굴은 *직접 노출 회피* (실루엣·뒷모습)

### 비용

- SDXL self-host: $0/이미지 (인프라)
- Midjourney: $30/월 standard

---

## 8. 비용 시뮬레이션

### 사용자 100명 × 일 5회 진입

| 항목 | 월 콜 수 | 단가 | 월 비용 |
|---|---|---|---|
| 감정 분류 (cache 35%) | 9,750 | $0.00015 | $1.46 |
| 콘텐츠 추천 (임베딩) | 15,000 | $0.00001 | $0.15 |
| 묵상 변환 (10% 사용) | 1,500 | $0.003 | $4.50 |
| 게임 분기 (cache 80%) | 1,000 | $0.0002 | $0.20 |
| 주간 리포트 | 400 | $0.005 | $2.00 |
| 키워드 추출 | 3,000 | $0.0001 | $0.30 |
| 챗봇 위로 (사용자 5%) | 750 | $0.005 | $3.75 |
| TTS (캐시·R2) | - | $0 | $0 |
| 이미지 (사전 생성) | - | $0 | $0 |
| **합계** | | | **~$12.36** |

→ **사용자 1명당 월 $0.12** (≈150원). 매우 합리적.

### 사용자 10,000명 × 일 3회

→ $1,236 / 월. 매출 (구독 월 3,900원 × 10% 전환 = $260) 보다 *3.6배 적은 비용* — 영업 이익 가능.

---

## 9. Failure Handling

| 시나리오 | 대응 |
|---|---|
| OpenAI rate limit | Anthropic fallback. 둘 다 실패 시 keyword fallback |
| OpenAI 다운 | Anthropic 자동 전환 |
| Anthropic 다운 | OpenAI 자동 전환 |
| 둘 다 다운 | 키워드 사전 (정확도 ↓) + 사용자에게 *"AI 일시 불가, 단순 모드"* 알림 |
| Coqui TTS 다운 | 텍스트만 표시 (음성 없음) |
| LLM 응답 timeout (>10s) | 자동 cancel + 사용자에게 *"잠시 후 다시"* |
| LLM 응답 hallucination (성경 본문 조작) | 후처리 검증 — 본문은 DB 에서만 (LLM 본문 출력 금지) |

---

## 10. 환각 (Hallucination) 방지

### 규칙
1. **성경 본문은 LLM 출력 금지** — 항상 `scripture_passages` 테이블에서 인용
2. **LLM 은 *해석·묵상* 만 생성** — 본문 인용은 placeholder `{ref}` 로
3. **system prompt 명시** — *"성경 본문 직접 인용 금지. 절 참조만"*

### 예시 — 잘못된 vs 올바른

❌ **잘못된 LLM 출력**:
> *"창세기 45:5 에서 요셉은 '나를 보내신 분이 하나님이라'고 말합니다..."*
> (실제 본문이 맞는지 LLM 도 모름)

✅ **올바른 출력**:
> *"요셉의 자기 인식은 [REF:gen-45:5] 에 잘 나타납니다. 그가 형제를 향해 한 말처럼..."*
> → 시스템이 `[REF:gen-45:5]` 를 DB 의 실제 본문으로 치환

---

## 11. 모니터링·관측

### Prometheus 메트릭 (Spring + Python)

```
# Spring 측
ai_request_total{provider="openai", purpose="classify", status="success"} 12453
ai_request_total{provider="openai", purpose="classify", status="error"} 8
ai_request_duration_seconds{provider, purpose, quantile="0.95"} 1.8
ai_cost_usd_total{provider, purpose} 4.52
ai_cache_hit_total{purpose} 4221
ai_cache_miss_total{purpose} 9802

# Python 측
llm_token_usage_total{provider, model, type="input"} 1234567
llm_token_usage_total{provider, model, type="output"} 234567
```

### 알람

- *시간당 비용 > $1* → Telegram 알람
- *AI 응답 에러율 > 5%* → 알람
- *cache hit rate < 60%* → 알람 (캐시 효율 문제)

---

## 12. Spring 헥사고날 매핑

```
ai/
├── domain/
│   ├── AiRequest.java
│   ├── AiResponse.java
│   └── AiProvider.java   (enum)
├── application/
│   ├── port/in/
│   │   ├── ClassifyEmotionUseCase
│   │   ├── GenerateMeditationUseCase
│   │   └── PolishPsalmUseCase
│   ├── port/out/
│   │   ├── AiOrchestrationClient   # Python AI 서비스
│   │   ├── LlmCacheRepository
│   │   └── LlmUsageRepository
│   └── service/
│       ├── AiOrchestrationService
│       └── CostTrackingService
└── adapter/
    ├── in/web/AiAdminController.java   # /admin/ai/usage
    └── out/
        ├── http/AiOrchestrationHttpClient.java  # Python 호출
        └── persistence/LlmCacheJpaAdapter.java
```

---

## 13. 다음 단계 (구현 시작 시)

1. Python AI 서비스 (FastAPI) 부트스트랩
2. LangChain prompt 파일 v1.0 작성 (16 사용처)
3. Spring 의 `AiOrchestrationHttpClient` 어댑터
4. 캐시 사전 생성 스크립트 (게임 분기 12개)
5. Coqui TTS 컨테이너 + David 노드 GPU 할당
6. Prometheus 메트릭 export
7. 비용 모니터링 Telegram 알람

---

> **TL;DR** — 16개 AI 사용처를 16개 분리된 chain 으로. 비용 절감의 핵심은 **캐싱 (게임 80%·NPC 90%)** + **사전 생성 (TTS·이미지)** + **임베딩 검색 (RAG)**. 사용자 100명에 월 $12, 1만 명에 월 $1,236 — 구독 수익의 1/3.
