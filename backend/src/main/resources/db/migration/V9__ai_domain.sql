-- V9: AI 도메인 확장 — LLM 사용 로그 + 캐시 보강
-- DB-SCHEMA.md §9 + AI-ARCHITECTURE.md §4 (멀티 프로바이더 + 비용 추적)

-- ============================================================
-- llm_cache — V1 의 기본 캐시 컬럼 보강
-- ============================================================
ALTER TABLE llm_cache
    ADD COLUMN IF NOT EXISTS provider           VARCHAR(20),       -- 'openai'|'anthropic'|'google'|'self_hosted'
    ADD COLUMN IF NOT EXISTS model              VARCHAR(50),
    ADD COLUMN IF NOT EXISTS purpose            VARCHAR(30),       -- 'emotion_classify'|'meditation'|'psalm_polish'|...
    ADD COLUMN IF NOT EXISTS prompt_tokens      INTEGER,
    ADD COLUMN IF NOT EXISTS completion_tokens  INTEGER,
    ADD COLUMN IF NOT EXISTS hit_count          INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_hit_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expires_at         TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_llm_cache_purpose_hit
    ON llm_cache(purpose, hit_count DESC);
CREATE INDEX IF NOT EXISTS idx_llm_cache_expires
    ON llm_cache(expires_at) WHERE expires_at IS NOT NULL;

-- ============================================================
-- tts_cache — V1 의 기본 캐시 컬럼 보강
-- ============================================================
ALTER TABLE tts_cache
    ADD COLUMN IF NOT EXISTS voice_id           VARCHAR(50),       -- Coqui voice clone ID
    ADD COLUMN IF NOT EXISTS engine             VARCHAR(20),       -- 'coqui_xtts2' | 'azure' | 'elevenlabs'
    ADD COLUMN IF NOT EXISTS audio_url          TEXT,              -- R2 CDN 경로
    ADD COLUMN IF NOT EXISTS duration_ms        INTEGER,
    ADD COLUMN IF NOT EXISTS hit_count          INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_hit_at        TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS expires_at         TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_tts_cache_voice
    ON tts_cache(voice_id, hit_count DESC);

-- ============================================================
-- llm_usage — 호출 단위 사용 로그 (개인 PII 없는 메타만)
-- ============================================================
CREATE TABLE IF NOT EXISTS llm_usage (
    id                  BIGSERIAL PRIMARY KEY,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    user_id             UUID REFERENCES users(id) ON DELETE SET NULL,
    purpose             VARCHAR(30) NOT NULL,
    provider            VARCHAR(20) NOT NULL,
    model               VARCHAR(50) NOT NULL,
    prompt_tokens       INTEGER,
    completion_tokens   INTEGER,
    latency_ms          INTEGER,
    cache_hit           BOOLEAN NOT NULL DEFAULT FALSE,
    cost_usd            NUMERIC(8,5),
    request_id          VARCHAR(80),                                -- 프로바이더 request id (디버깅)
    success             BOOLEAN NOT NULL DEFAULT TRUE,
    error_code          VARCHAR(50)
);
CREATE INDEX IF NOT EXISTS idx_llm_usage_time
    ON llm_usage(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_llm_usage_purpose_time
    ON llm_usage(purpose, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_llm_usage_user_time
    ON llm_usage(user_id, occurred_at DESC) WHERE user_id IS NOT NULL;

-- ============================================================
-- llm_usage_daily — 일자별 집계 (MATERIALIZED VIEW)
--   - 비용 대시보드용
--   - cron 으로 매일 새벽 REFRESH (REFRESH MATERIALIZED VIEW CONCURRENTLY ...)
-- ============================================================
CREATE MATERIALIZED VIEW IF NOT EXISTS llm_usage_daily AS
SELECT
    DATE(occurred_at)                                       AS usage_date,
    purpose,
    provider,
    model,
    COUNT(*)                                                AS calls,
    SUM(CASE WHEN cache_hit THEN 1 ELSE 0 END)              AS cache_hits,
    SUM(COALESCE(prompt_tokens, 0))                         AS prompt_tokens,
    SUM(COALESCE(completion_tokens, 0))                     AS completion_tokens,
    SUM(COALESCE(cost_usd, 0))                              AS cost_usd,
    AVG(latency_ms)::INTEGER                                AS avg_latency_ms
FROM llm_usage
GROUP BY DATE(occurred_at), purpose, provider, model
WITH NO DATA;

CREATE UNIQUE INDEX IF NOT EXISTS idx_llm_usage_daily_pk
    ON llm_usage_daily(usage_date, purpose, provider, model);
