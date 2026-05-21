-- V3: IDENTITY + EMOTION 도메인 확장 (DB-SCHEMA.md §2~3 매핑)
-- 기존 V1 의 users·emotion_logs 컬럼 보강 + 새 테이블 추가

-- ============================================================
-- IDENTITY — users 컬럼 보강
-- ============================================================
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_type          VARCHAR(20) NOT NULL DEFAULT 'guest',
    ADD COLUMN IF NOT EXISTS external_id        VARCHAR(255),
    ADD COLUMN IF NOT EXISTS faith_tone         VARCHAR(20) DEFAULT 'balanced',
    ADD COLUMN IF NOT EXISTS preferred_mode     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS haptic_intensity   VARCHAR(10) DEFAULT 'medium',
    ADD COLUMN IF NOT EXISTS skip_intro_silence BOOLEAN DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS data_retention_days INTEGER DEFAULT 90,
    ADD COLUMN IF NOT EXISTS updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS deleted_at         TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_users_external_oauth
    ON users(external_id) WHERE external_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_active
    ON users(created_at) WHERE deleted_at IS NULL;

-- ============================================================
-- IDENTITY — devices (디바이스별 게스트 ID 추적)
-- ============================================================
CREATE TABLE IF NOT EXISTS devices (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_type         VARCHAR(30),   -- 'quest3' | 'visionpro' | 'galaxyxr' | 'web'
    device_fingerprint  VARCHAR(255),  -- 해시
    last_seen_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_devices_fingerprint
    ON devices(device_fingerprint) WHERE device_fingerprint IS NOT NULL;

-- ============================================================
-- IDENTITY — app_sessions (앱 진입~종료 단위)
-- ============================================================
CREATE TABLE IF NOT EXISTS app_sessions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id               UUID REFERENCES devices(id) ON DELETE SET NULL,
    started_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at                TIMESTAMPTZ,
    duration_seconds        INTEGER GENERATED ALWAYS AS (
        CASE WHEN ended_at IS NULL THEN NULL
             ELSE EXTRACT(EPOCH FROM (ended_at - started_at))::INTEGER
        END
    ) STORED,
    entry_emotion           VARCHAR(30),
    completed_missions      JSONB NOT NULL DEFAULT '[]'::jsonb
);
CREATE INDEX IF NOT EXISTS idx_app_sessions_user
    ON app_sessions(user_id, started_at DESC);

-- ============================================================
-- EMOTION — emotion_logs 컬럼 보강 (V1 의 기본 스키마에서)
-- ============================================================
ALTER TABLE emotion_logs
    ADD COLUMN IF NOT EXISTS app_session_id      UUID REFERENCES app_sessions(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS raw_text_encrypted  BYTEA,
    ADD COLUMN IF NOT EXISTS intensity           SMALLINT CHECK (intensity BETWEEN 1 AND 10),
    ADD COLUMN IF NOT EXISTS chosen_dimension    VARCHAR(20),   -- 'spiritual'|'emotional'|'rational'|'auto'
    ADD COLUMN IF NOT EXISTS recommended_track   VARCHAR(1),    -- 'A' | 'B'
    ADD COLUMN IF NOT EXISTS recommended_content VARCHAR(50);

-- raw_text 는 평문 OR 암호화 둘 중 하나 — 둘 다 NULL 안 됨
-- 마이그레이션 시점엔 둘 다 NULL 허용 (기존 데이터 호환)

-- ============================================================
-- EMOTION — recovery_metrics (자체 회복 지표, 진단 도구 대체)
-- ============================================================
CREATE TABLE IF NOT EXISTS recovery_metrics (
    id                          BIGSERIAL PRIMARY KEY,
    user_id                     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    metric_date                 DATE NOT NULL,
    emotion_diversity_count     INTEGER,
    avg_intensity               NUMERIC(3,1),
    diary_word_count            INTEGER,
    mission_completed_count     INTEGER,
    top_keywords                TEXT[],
    risk_signal_count           INTEGER DEFAULT 0,
    UNIQUE (user_id, metric_date)
);
CREATE INDEX IF NOT EXISTS idx_recovery_user_date
    ON recovery_metrics(user_id, metric_date DESC);
