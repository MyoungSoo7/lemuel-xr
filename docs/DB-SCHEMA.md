# DB 설계 — Lemuel XR

> **범위**: 트랙 A (정적 회복 1~7) + 트랙 B (서사 게임 8~11 — 요셉·모세·다윗·예수) + 3차원 진입 + 정신건강 안전장치
> **DB**: PostgreSQL 17 + pgvector + Pinecone (LLM 임베딩)
> **상태**: 설계 단계 (구현 X) — Flyway V1~V12 마이그레이션 계획안

---

## 0. 설계 원칙

| 원칙              | 적용                                                                |
| ----------------- | ------------------------------------------------------------------- |
| **익명 우선**     | guest user 가 1차 — 모든 PII 는 옵션                                |
| **헥사고날 분리** | 도메인 (게임/일기/시편/안전) 별 스키마 분리, FK 최소화              |
| **JSONB 적극**    | Scene 결정 같은 _분기 데이터_ 는 JSONB 로 — 캐릭터 추가 시 ALTER 무 |
| **벡터 분리**     | scripture 임베딩은 pgvector 또는 Pinecone (대용량 시)               |
| **삭제 가능**     | 사용자가 _모든 데이터 삭제_ 요청 가능 (GDPR/개인정보보호법)         |
| **암호화**        | 일기·시편 본문은 _pgcrypto_ 로 row-level 암호화                     |

---

## 1. 도메인 별 테이블 그룹

```
┌─────────────────────────────────────────────────────────────┐
│  IDENTITY (사용자·세션)                                       │
│  └ users, devices, sessions                                  │
├─────────────────────────────────────────────────────────────┤
│  EMOTION (감정 입력 + 3차원 모드)                              │
│  └ emotion_logs, emotion_dimensions, recovery_metrics        │
├─────────────────────────────────────────────────────────────┤
│  GAME (트랙 B 4인물)                                          │
│  └ game_sessions, game_decisions, scene_views                │
├─────────────────────────────────────────────────────────────┤
│  CONTENT (트랙 A 1~7)                                        │
│  └ diary_entries, user_psalms, proverbs_interactions         │
├─────────────────────────────────────────────────────────────┤
│  SCRIPTURE (성경 본문)                                        │
│  └ scripture_passages, scripture_embeddings                  │
├─────────────────────────────────────────────────────────────┤
│  SAFETY (정신건강 안전장치)                                    │
│  └ safety_alerts, crisis_resources_log                       │
├─────────────────────────────────────────────────────────────┤
│  THEOLOGY (콘텐츠 검증)                                       │
│  └ theology_reviews, content_versions                        │
├─────────────────────────────────────────────────────────────┤
│  AI (LLM 캐시 + 비용 추적)                                    │
│  └ llm_cache, llm_usage                                      │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. IDENTITY — 사용자·세션

### `users`

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- 익명 우선: 게스트가 기본, OAuth 는 Phase 2
    user_type       VARCHAR(20) NOT NULL DEFAULT 'guest',  -- 'guest' | 'oauth_google' | 'oauth_kakao'
    external_id     VARCHAR(255),  -- OAuth 시 sub claim

    -- 신앙 톤 설정 (강/약) — UX 영향
    faith_tone      VARCHAR(20) DEFAULT 'balanced',  -- 'strong' | 'balanced' | 'soft'

    -- 사용자 선호 진입 모드 — null 이면 매번 선택
    preferred_mode  VARCHAR(20),  -- 'spiritual' | 'emotional' | 'rational' | NULL

    -- 정서 보호 설정
    haptic_intensity VARCHAR(10) DEFAULT 'medium',  -- 'off' | 'low' | 'medium' | 'high'
    skip_intro_silence BOOLEAN DEFAULT FALSE,  -- Scene 1 침묵 건너뛰기

    -- 데이터 보존 정책
    data_retention_days INTEGER DEFAULT 90,  -- 일기 보존 일수, NULL = 영구

    -- 삭제 가능성 (soft delete 우선)
    deleted_at      TIMESTAMPTZ
);

CREATE INDEX idx_users_external ON users(external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_users_active ON users(created_at) WHERE deleted_at IS NULL;
```

### `devices`

```sql
-- 디바이스별 게스트 ID 추적 — 같은 디바이스의 사용자 연속성
CREATE TABLE devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    device_type     VARCHAR(30),    -- 'quest3' | 'visionpro' | 'galaxy_xr' | 'web'
    device_fingerprint VARCHAR(255),  -- 해시 — Apple Vision Pro·Quest 디바이스 고유 ID

    last_seen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_devices_fingerprint ON devices(device_fingerprint);
```

### `app_sessions`

```sql
-- 사용자 앱 진입~종료 세션 (게임 세션과 다름)
CREATE TABLE app_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id       UUID REFERENCES devices(id),

    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at        TIMESTAMPTZ,
    duration_seconds INTEGER GENERATED ALWAYS AS (
        CASE WHEN ended_at IS NULL THEN NULL
             ELSE EXTRACT(EPOCH FROM (ended_at - started_at))::INTEGER
        END
    ) STORED,

    -- 분석용 메타데이터
    entry_emotion   VARCHAR(30),    -- 첫 감정 입력
    completed_missions JSONB DEFAULT '[]'::jsonb  -- ['moses', 'david']
);

CREATE INDEX idx_app_sessions_user ON app_sessions(user_id, started_at DESC);
```

---

## 3. EMOTION — 감정 입력 + 3차원 모드

### `emotion_logs`

```sql
CREATE TABLE emotion_logs (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    app_session_id  UUID REFERENCES app_sessions(id),

    -- 사용자 원본 입력 (선택적 암호화)
    raw_text        TEXT,  -- pgcrypto 암호화 옵션
    raw_text_encrypted BYTEA,

    -- AI 분류 결과
    classified_emotion VARCHAR(30) NOT NULL,  -- '불안'·'슬픔'·'분노'·'외로움'·'기쁨'·'평온'·'감사'·'혼란'·'무자격감'·'두려움' ...
    intensity       SMALLINT CHECK (intensity BETWEEN 1 AND 10),
    confidence      NUMERIC(3,2),  -- 0.00~1.00

    -- 3차원 사용자 선택 / AI 추정
    chosen_dimension VARCHAR(20),  -- 'spiritual' | 'emotional' | 'rational' | 'auto'

    -- 추천된 콘텐츠 (트래킹용)
    recommended_track VARCHAR(1),  -- 'A' | 'B'
    recommended_content VARCHAR(50),  -- 'psalm:23' | 'mission:moses' | 'diary:guided' ...

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_emotion_logs_user_time ON emotion_logs(user_id, created_at DESC);
CREATE INDEX idx_emotion_logs_classified ON emotion_logs(classified_emotion);
```

### `recovery_metrics` — 자체 회복 지표 (PHQ-9 류 진단 도구 대체)

```sql
CREATE TABLE recovery_metrics (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 일별 집계 (cron 으로 갱신)
    metric_date     DATE NOT NULL,

    emotion_diversity_count INTEGER,  -- 사용한 감정 단어 종류 (Pennebaker 지표)
    avg_intensity   NUMERIC(3,1),
    diary_word_count INTEGER,  -- 일기 글자 수 (감정 처리 성숙도)
    mission_completed_count INTEGER,

    -- 추출된 사용자 키워드 top 5 (TF-IDF)
    top_keywords    TEXT[],

    -- 위험 신호 (자살·자해 키워드 빈도)
    risk_signal_count INTEGER DEFAULT 0,

    UNIQUE (user_id, metric_date)
);

CREATE INDEX idx_recovery_user_date ON recovery_metrics(user_id, metric_date DESC);
```

---

## 4. GAME — 트랙 B 4 인물

### `game_sessions`

```sql
CREATE TABLE game_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    app_session_id  UUID REFERENCES app_sessions(id),

    character       VARCHAR(20) NOT NULL,  -- 'joseph' | 'moses' | 'david' | 'jesus'
    chosen_dimension VARCHAR(20),  -- 'spiritual' | 'emotional' | 'rational' (Scene 진입 톤 결정)

    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    abandoned_at    TIMESTAMPTZ,  -- 중단 시점

    -- 트리거 (감정 입력 → 미션 진입한 경우)
    triggered_by_emotion_log_id BIGINT REFERENCES emotion_logs(id),

    -- 결말 메시지 (사용자가 받은)
    closing_message TEXT,

    -- 완료 결과 (분석용)
    scene_count_completed SMALLINT DEFAULT 0,
    duration_seconds INTEGER
);

CREATE INDEX idx_game_sessions_user ON game_sessions(user_id, started_at DESC);
CREATE INDEX idx_game_sessions_character ON game_sessions(character, completed_at);
```

### `game_decisions` — Scene 별 사용자 선택 기록

```sql
-- Scene 결정을 *flat 테이블* 로 풀어 분석 용이
CREATE TABLE game_decisions (
    id              BIGSERIAL PRIMARY KEY,
    game_session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,

    scene_number    SMALLINT NOT NULL,  -- 1~7
    scene_name      VARCHAR(50),  -- 'dream_interpretation' | 'burning_bush' | 'sling_throw' ...

    -- 결정 — JSONB 로 다양한 형식 수용
    decision        JSONB NOT NULL,  -- 캐릭터별 schema 자유
    /* 예시:
       요셉 Scene 2: {"save_ratio": "1/3"}
       모세 Scene 3: {"cards": ["throw","heart","throw","throw","heart"]}
       다윗 Scene 4: {"stones": ["fear","trust","prayer","loneliness","humiliation"], "order": [3,1,5,2,4]}
       예수 Scene 3 (예): {"cup": "drink", "delay_seconds": 4.2}
    */

    -- 인터랙션 추가 데이터 (햅틱 강도, 시선 시간 등)
    interaction_meta JSONB,
    /* 예시: {"haptic_intensity_used":"high", "gaze_seconds":12.5, "hesitation_ms":3200} */

    decided_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_game_decisions_session ON game_decisions(game_session_id, scene_number);
-- 캐릭터별 Scene 패턴 분석용
CREATE INDEX idx_game_decisions_pattern ON game_decisions((decision->>'save_ratio'))
    WHERE scene_name = 'storage_decision';
```

### `scene_views` — 사용자가 _각 Scene 에서 보낸 시간_

```sql
-- A/B 테스트 + UX 분석용
CREATE TABLE scene_views (
    id              BIGSERIAL PRIMARY KEY,
    game_session_id UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    scene_number    SMALLINT NOT NULL,

    entered_at      TIMESTAMPTZ NOT NULL,
    exited_at       TIMESTAMPTZ,
    duration_seconds INTEGER GENERATED ALWAYS AS (
        EXTRACT(EPOCH FROM (exited_at - entered_at))::INTEGER
    ) STORED,

    -- 중간 이탈 추적
    exit_reason     VARCHAR(30),  -- 'completed' | 'abandoned' | 'skipped' | 'safety_triggered'
    skipped_silence BOOLEAN DEFAULT FALSE
);
```

---

## 5. CONTENT — 트랙 A (Theme 1~7)

### `diary_entries` — Theme 1

```sql
CREATE TABLE diary_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- 본문 (암호화 권장)
    body            TEXT,  -- 또는 BYTEA pgcrypto

    -- 사용자 입력 모드
    form_type       VARCHAR(20),  -- 'free' | '5w1h' | 'emotion_label'
    emotion_label   VARCHAR(30),
    intensity       SMALLINT,

    -- AI 묵상 변환 (옵션)
    meditation_text TEXT,
    meditation_accepted BOOLEAN DEFAULT FALSE,  -- 사용자가 저장 선택했나
    meditation_dimension VARCHAR(20),  -- 'spiritual' | 'emotional' | 'rational'

    -- 자동 분석
    word_count      INTEGER,
    sentiment_score NUMERIC(3,2),  -- -1 ~ +1

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_diary_user_time ON diary_entries(user_id, created_at DESC);
```

### `user_psalms` — Theme 4 (사용자 작성 시편)

```sql
CREATE TABLE user_psalms (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    psalm_form      VARCHAR(20),  -- 'lament' | 'petition' | 'thanksgiving' | 'praise'
    raw_text        TEXT,
    polished_text   TEXT,  -- AI 가 시편 운율로 다듬은 버전 (옵션)
    accepted_polished BOOLEAN DEFAULT FALSE,

    -- 영감 받은 원래 시편
    inspired_by_psalm VARCHAR(20),  -- '23' | '42:1-3' ...

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_psalms_user ON user_psalms(user_id, created_at DESC);
```

### `proverbs_interactions` — Theme 2

```sql
CREATE TABLE proverbs_interactions (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    user_situation  TEXT,  -- 사용자가 입력한 상황 1문장
    recommended_proverbs JSONB,  -- [{"ref":"prov-3:5", "fit_score":0.92}, ...]
    chosen_proverb_ref VARCHAR(20),

    -- 3차원 진입 — 어떤 모드로 풀었나
    chosen_dimension VARCHAR(20),

    -- 재구조화 워크시트 응답
    reframing_response JSONB,  -- {"applied_to":"...", "doubt":"...", "next_step":"..."}

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### `ecclesiastes_views` — Theme 3 (전도서 사용 기록)

```sql
CREATE TABLE ecclesiastes_views (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    chapter_ref     VARCHAR(20),  -- 'eccl-1' | 'eccl-3:1-8'
    user_season     VARCHAR(20),  -- 사용자가 선택한 '계절' (전도서 3장)
    futility_note   TEXT,  -- 사용자가 적은 '오늘 헛된 것'
    meaning_note    TEXT,  -- 사용자가 적은 '오늘 의미있는 것'

    listened_audio  BOOLEAN DEFAULT FALSE,
    conclusion_viewed BOOLEAN DEFAULT FALSE,  -- 전 12:13 함께 봤나

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 6. SCRIPTURE — 성경 본문 + 임베딩

### `scripture_passages`

```sql
CREATE TABLE scripture_passages (
    id              BIGSERIAL PRIMARY KEY,
    reference       VARCHAR(50) UNIQUE NOT NULL,  -- 'gen-45:5' | 'ps-23:1' | 'matt-26:39'
    book_code       VARCHAR(10) NOT NULL,         -- 'gen' | 'ex' | '1sam' | 'ps' | 'eccl' | 'prov' | 'matt' | 'john'
    chapter         SMALLINT NOT NULL,
    verse           SMALLINT NOT NULL,
    verse_end       SMALLINT,  -- 'ps-23:1-6' 처럼 범위인 경우

    -- 다중 번역 — translation 컬럼으로 분리
    translation     VARCHAR(20) NOT NULL,  -- 'krv' (개역개정) | 'nlt-ko' (현대인의 성경) | 'esv' | 'niv'
    text            TEXT NOT NULL,

    -- 메타데이터
    theme_tags      TEXT[],   -- ['fear', 'identity', 'covenant']
    character_tags  TEXT[],   -- ['joseph', 'moses', 'david', 'jesus']

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reference, translation)
);

CREATE INDEX idx_scripture_book_chapter ON scripture_passages(book_code, chapter);
CREATE INDEX idx_scripture_tags ON scripture_passages USING GIN(theme_tags);
CREATE INDEX idx_scripture_character ON scripture_passages USING GIN(character_tags);
```

### `scripture_embeddings` (pgvector)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE scripture_embeddings (
    passage_id      BIGINT PRIMARY KEY REFERENCES scripture_passages(id) ON DELETE CASCADE,
    embedding       vector(1536) NOT NULL,  -- text-embedding-3-small (1536d)
    embed_model     VARCHAR(50) NOT NULL DEFAULT 'text-embedding-3-small',
    embed_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW 인덱스 (검색 빠름, 빌드 느림 — 성경 본문은 일회성 빌드라 OK)
CREATE INDEX idx_scripture_embeddings_hnsw ON scripture_embeddings
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

→ _대용량 확장 시_ Pinecone 으로 이전 가능 (외부 vector DB).

---

## 7. SAFETY — 정신건강 안전장치

### `safety_alerts` — 위험 신호 감지 로그

```sql
CREATE TABLE safety_alerts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    triggered_by    VARCHAR(50),  -- 'diary_keyword' | 'emotion_intensity_high' | 'repeated_negative'

    severity        VARCHAR(20) NOT NULL,  -- 'low' | 'medium' | 'high' | 'critical'
    matched_keywords TEXT[],
    snippet         TEXT,  -- 매칭된 문맥 (필요 시 암호화)

    -- 시스템 액션
    crisis_resource_shown BOOLEAN DEFAULT FALSE,
    therapist_suggestion_shown BOOLEAN DEFAULT FALSE,

    -- 사용자 반응
    user_acknowledged BOOLEAN DEFAULT FALSE,
    user_clicked_resource BOOLEAN DEFAULT FALSE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_safety_user_severity ON safety_alerts(user_id, severity, created_at DESC);
```

### `crisis_resources` — 위기 자원 카탈로그

```sql
CREATE TABLE crisis_resources (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(100),
    phone           VARCHAR(50),
    url             VARCHAR(255),
    language        VARCHAR(10) DEFAULT 'ko',
    available_hours VARCHAR(50),  -- '24/7' | '09:00-22:00'
    region          VARCHAR(50)   -- '한국' | '전세계'
);

-- 시드 데이터 (실구현: V7__safety_domain.sql + V20260802031449__crisis_resource_109_canonical.sql)
-- {{crisis_resources.default}} 토큰 = region/locale 별 우선순위 1위 *활성 전화* 자원 = 109.
-- ('자살예방 상담전화', '109', NULL, 'ko', '24/7', '한국'),          -- DEFAULT · priority 1 (2024-01-01 통합 정번호)
-- ('정신건강위기상담전화', '1577-0199', NULL, 'ko', '24/7', '한국'),  -- 폐지 아님 — 정신건강 상담 담당
-- ('자살예방상담(구 번호·보조)', '1393', NULL, 'ko', '24/7', '한국'), -- 정번호 지위만 상실 (불통 아님) · priority 6
-- ('청소년전화 1388', '1388', 'https://www.cyber1388.kr', 'ko', '24/7', '한국'),   -- 만 9~24세: default 앞에 prepend 렌더
-- ('생명의전화', '1588-9191', 'https://www.lifeline.or.kr', 'ko', '24/7', '한국')
```

> **번호 정본 (2026-08-02 교정)** — 2024-01-01 부로 분산돼 있던 자살예방 상담전화가 **109** 로 통합됐다
> ([보건복지부 보도자료](https://www.mohw.go.kr/board.es?act=view&bid=0027&cg_code=&list_no=1479607&mid=a10503000000&tag=)).
> 통합된 것은 _자살예방 상담 기능_ 뿐이다 — 정신건강상담전화(1577-0199)·청소년전화(1388)·여성긴급전화(1366) 등은
> 종전과 같이 본연의 역할에 따라 담당 분야 상담을 계속 수행한다
> ([보건복지상담센터 FAQ](https://www.129.go.kr/109)). 1393 이 _불통_ 이라는 근거는 어느 출처에도 없으므로
> 시드에서 삭제하지 않고 `구 번호·보조` 로 강등만 한다.

---

## 8. THEOLOGY — 콘텐츠 검증

### `content_versions`

```sql
-- AI 생성 묵상문·시편 등 *공개 콘텐츠* 의 버전 관리
CREATE TABLE content_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type    VARCHAR(30),  -- 'closing_message' | 'meditation_template' | 'scene_narration'
    scope           VARCHAR(50),  -- 'joseph:scene_5' | 'moses:scene_3:card_4' ...

    text            TEXT NOT NULL,
    language        VARCHAR(10) DEFAULT 'ko',

    -- 워크플로우
    status          VARCHAR(20) NOT NULL DEFAULT 'draft',  -- 'draft' | 'review' | 'approved' | 'published' | 'archived'
    created_by      VARCHAR(50),   -- 'ai-gpt4o' | 'ai-claude' | 'human:user_id'
    approved_by     VARCHAR(50),   -- 신학 자문 식별자
    approved_at     TIMESTAMPTZ,
    published_at    TIMESTAMPTZ,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_versions_scope ON content_versions(scope, status);
```

### `theology_reviews`

```sql
CREATE TABLE theology_reviews (
    id              BIGSERIAL PRIMARY KEY,
    content_version_id UUID NOT NULL REFERENCES content_versions(id) ON DELETE CASCADE,

    reviewer_id     VARCHAR(50),
    decision        VARCHAR(20),  -- 'approve' | 'reject' | 'revise'
    comment         TEXT,
    concern_tags    TEXT[],  -- ['heresy_risk', 'pastoral_concern', 'unclear_doctrine']

    reviewed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## 9. AI — LLM 캐시·비용

### `llm_cache`

```sql
CREATE TABLE llm_cache (
    cache_key       VARCHAR(255) PRIMARY KEY,
    /* key 패턴 예시:
       "joseph:scene2:save:1/3"          (게임 분기)
       "moses:scene3:cards:TTHTH"        (변명 5장 패턴)
       "diary_meditation:emotion:불안"    (트랙 A 묵상 템플릿)
    */

    response_text   TEXT NOT NULL,
    model           VARCHAR(50),
    prompt_template_version VARCHAR(20),

    -- 통계
    hit_count       INTEGER DEFAULT 0,
    last_hit_at     TIMESTAMPTZ,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ  -- NULL = 영구
);

CREATE INDEX idx_llm_cache_hits ON llm_cache(hit_count DESC);
```

### `llm_usage` — 비용 추적

```sql
CREATE TABLE llm_usage (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,

    purpose         VARCHAR(50),  -- 'emotion_classify' | 'diary_meditation' | 'game_branch' | 'psalm_polish'
    model           VARCHAR(50),
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    cost_usd        NUMERIC(10,6),

    cache_hit       BOOLEAN DEFAULT FALSE,

    called_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 일별 집계 view
CREATE MATERIALIZED VIEW llm_usage_daily AS
SELECT
    DATE(called_at) as date,
    purpose,
    model,
    COUNT(*) as call_count,
    SUM(cost_usd) as total_cost_usd,
    AVG(CASE WHEN cache_hit THEN 1 ELSE 0 END) as cache_hit_rate
FROM llm_usage
GROUP BY DATE(called_at), purpose, model;

CREATE INDEX idx_llm_usage_daily_date ON llm_usage_daily(date DESC);
```

---

## 10. Flyway 마이그레이션 순서

| 버전    | 내용                                                                          | 의존성 |
| ------- | ----------------------------------------------------------------------------- | ------ |
| **V1**  | `users`, `devices`, `app_sessions`                                            | (none) |
| **V2**  | `emotion_logs`, `recovery_metrics`                                            | V1     |
| **V3**  | `scripture_passages` + pgvector extension                                     | (none) |
| **V4**  | `scripture_embeddings`                                                        | V3     |
| **V5**  | `game_sessions`, `game_decisions`, `scene_views`                              | V1, V2 |
| **V6**  | `diary_entries`, `user_psalms`, `proverbs_interactions`, `ecclesiastes_views` | V1     |
| **V7**  | `safety_alerts`, `crisis_resources` + 시드                                    | V1     |
| **V8**  | `content_versions`, `theology_reviews`                                        | (none) |
| **V9**  | `llm_cache`, `llm_usage`                                                      | V1     |
| **V10** | 성경 본문 시드 (창세기 41~50 / 출 3~14 / 삼상 16~17 + 시 23 / 마태~요한 핵심) | V3     |
| **V11** | 분석용 인덱스 + materialized views                                            | All    |
| **V12** | pgcrypto 적용 (diary·user_psalms)                                             | V6     |

---

## 11. 인덱스·성능 전략

### 자주 쓰일 쿼리 패턴

| 쿼리                                                  | 최적화                                                |
| ----------------------------------------------------- | ----------------------------------------------------- |
| _최근 30일 사용자의 감정 분포_                        | `idx_emotion_logs_user_time` + 부분 인덱스            |
| _Scene 결정 분포 (요셉 Scene 2 의 1/3 vs 1/5 vs 1/2)_ | `idx_game_decisions_pattern` (expression index)       |
| _Pinecone 대체 — 성경 의미 검색_                      | HNSW vector index                                     |
| _위험 사용자 알람_                                    | `idx_safety_user_severity` + 트리거                   |
| _비용 모니터링_                                       | `llm_usage_daily` materialized view (cron 1h refresh) |

### 파티셔닝 (Phase 3 — 사용자 1만+ 시)

```sql
-- emotion_logs 와 game_decisions 가 가장 빨리 커짐 (사용자당 일 5~20 row)
-- 월별 파티셔닝 추천
CREATE TABLE emotion_logs PARTITION BY RANGE (created_at);
CREATE TABLE emotion_logs_2026_06 PARTITION OF emotion_logs
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
```

---

## 12. 데이터 보안·개인정보보호

### 12.1 PII 분류

| 컬럼                            | 분류                      | 처리                 |
| ------------------------------- | ------------------------- | -------------------- |
| `users.external_id` (OAuth sub) | PII (Identifier)          | 해시 저장 권장       |
| `diary_entries.body`            | PII Sensitive (내면 정보) | pgcrypto 암호화 권장 |
| `user_psalms.raw_text`          | PII Sensitive             | pgcrypto 권장        |
| `emotion_logs.raw_text`         | PII Sensitive             | pgcrypto 권장        |
| `safety_alerts.snippet`         | PII Sensitive             | pgcrypto 필수        |
| 게임 결정 (`game_decisions`)    | 비-PII                    | 평문 OK              |
| 성경 본문                       | 공개                      | 평문                 |

### 12.2 사용자 삭제 (GDPR / 한국 개인정보보호법)

```sql
-- 사용자가 *모든 데이터 삭제* 요청 시
BEGIN;
UPDATE users SET deleted_at = now() WHERE id = $user_id;
DELETE FROM emotion_logs WHERE user_id = $user_id;
DELETE FROM diary_entries WHERE user_id = $user_id;
DELETE FROM user_psalms WHERE user_id = $user_id;
DELETE FROM game_sessions WHERE user_id = $user_id;
-- (자식 테이블들 CASCADE)
-- safety_alerts 는 *익명화* 만 (법적 기록 보존 필요할 수 있음)
UPDATE safety_alerts SET user_id = NULL, snippet = '[DELETED]' WHERE user_id = $user_id;
COMMIT;
```

### 12.3 보존 정책

| 데이터    | 기본 보존          | 사용자 옵션            |
| --------- | ------------------ | ---------------------- |
| 일기·시편 | 90 일              | 30/90/180/365일 / 영구 |
| 감정 로그 | 90 일              | 같음                   |
| 게임 결정 | 1년 (분석용)       | 30일 까지 단축 가능    |
| 안전 알람 | 1년 (책임 추적)    | 단축 불가              |
| LLM 캐시  | 영구 (콘텐츠 추적) | 사용자 무관            |

자동 삭제: `pg_cron` 으로 매일 새벽 만료 데이터 cleanup.

---

## 13. 캐릭터별 game_decisions 의 schema 예시

각 캐릭터 Scene 결정은 JSONB 라 자유 — 단 _분석 편의_ 위해 패턴 통일.

### 요셉

```json
{
  "scene1": { "viewed_dream": true },
  "scene2": { "save_ratio": "1/3" },
  "scene3": {
    "distribution": ["farmer", "immigrant", "trader", "farmer", "farmer"]
  },
  "scene4": { "reveal_choice": "test_first" },
  "scene5": { "closing_emotion_match": "unburdening" }
}
```

### 모세

```json
{
  "scene1": { "silence_duration_seconds": 40, "skipped": false },
  "scene2": { "approached_bush": true, "removed_shoes": true },
  "scene3": { "cards": ["throw", "heart", "throw", "throw", "heart"] },
  "scene4": { "action": "with_aaron", "hesitation_seconds": 3.2 },
  "scene5": { "reached_hand": true }
}
```

### 다윗

```json
{
  "scene1": { "psalm_lines_touched": 12 },
  "scene2": { "reaction": "silence" },
  "scene3": { "tried_armor": true, "removed_steps": 3 },
  "scene4": {
    "stones": ["fear", "loneliness", "prayer", "trust", "humiliation"],
    "order": [1, 3, 5, 4, 2],
    "last_stone": "humiliation"
  },
  "scene5": { "sling_rotation_speed": "moderate", "released_at_seconds": 5.8 }
}
```

### 예수 (godjinho 가 설계 — 예시)

```json
{
  "scene1": { "cup": "drink", "delay_seconds": 4.2 },
  "scene2": { "feet_washed": true },
  "scene3": { "silence_before_pilate": true },
  "scene4": { "viewed_cross": true, "duration_seconds": 60 },
  "scene5": { "resurrection_acknowledged": true }
}
```

---

## 14. 분석용 쿼리 예시

### _사용자의 30일 회복 추이_

```sql
SELECT
    metric_date,
    emotion_diversity_count,
    avg_intensity,
    diary_word_count
FROM recovery_metrics
WHERE user_id = $user_id
  AND metric_date >= CURRENT_DATE - INTERVAL '30 days'
ORDER BY metric_date;
```

### _요셉 Scene 2 의 가장 인기있는 저장 비율_

```sql
SELECT
    decision->>'save_ratio' as save_ratio,
    COUNT(*) as count
FROM game_decisions
WHERE scene_name = 'storage_decision'
GROUP BY decision->>'save_ratio'
ORDER BY count DESC;
```

### _3차원 진입 모드별 미션 완료율_

```sql
SELECT
    chosen_dimension,
    character,
    COUNT(*) FILTER (WHERE completed_at IS NOT NULL)::FLOAT / COUNT(*) as completion_rate,
    AVG(duration_seconds) as avg_duration_s
FROM game_sessions
WHERE chosen_dimension IS NOT NULL
GROUP BY chosen_dimension, character;
```

### _위험 신호 발생 후 7일 내 추적_

```sql
SELECT
    sa.user_id,
    sa.severity,
    sa.created_at as alert_at,
    el.classified_emotion as next_emotion,
    el.created_at as next_emotion_at
FROM safety_alerts sa
LEFT JOIN LATERAL (
    SELECT classified_emotion, created_at
    FROM emotion_logs
    WHERE user_id = sa.user_id
      AND created_at > sa.created_at
      AND created_at < sa.created_at + INTERVAL '7 days'
    ORDER BY created_at
    LIMIT 1
) el ON true
WHERE sa.severity IN ('high', 'critical');
```

---

## 15. 다음 단계

1. **Spring Boot 헥사고날 매핑** — 각 도메인 별 패키지 (`identity/`, `emotion/`, `game/`, `content/`, `scripture/`, `safety/`, `theology/`, `ai/`)
2. **JPA Entity 매핑** — JSONB 는 `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6+)
3. **Repository 인터페이스** — port/out 패턴
4. **Flyway V1~V12 마이그레이션 파일 작성**
5. **시드 스크립트** — 성경 본문 ETL (창세기·출애굽기·사무엘상·시편·복음서)
6. **로컬 docker-compose** — postgres17 + pgvector + redis (캐시)
7. **테스트 데이터 생성기** — 사용자 10명 시뮬레이션

---

## 16. 의도적으로 _지금 빼놓은_ 것

| 항목                        | 보류 이유         | 언제 추가   |
| --------------------------- | ----------------- | ----------- |
| 결제·구독 (`subscriptions`) | Phase 3 수익화 시 | V13+        |
| 다국어 (`translations`)     | MVP 한국어만      | Phase 3     |
| 소셜 (익명 기도 매칭)       | Phase 3           | V14+        |
| 푸시 알림 토큰              | OAuth 도입과 함께 | V13         |
| B2B (교회 단체)             | Phase 4           | 별도 schema |

---

> **TL;DR** — 8개 도메인 × 약 20개 테이블 × Flyway V1~V12. JSONB 적극 사용해 _캐릭터 추가 시 ALTER 0_. pgcrypto 로 일기 암호화, pg_cron 으로 보존 정책 자동화, pgvector 로 성경 의미 검색. 사용자 1만 명까진 PostgreSQL 단독으로 충분.
