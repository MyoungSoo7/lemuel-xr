-- V4: GAME 도메인 확장 (DB-SCHEMA.md §4 + XR-INTEGRATION §6.2)
-- 기존 V1 의 game_sessions 컬럼 보강 + game_decisions, scene_views 추가

-- ============================================================
-- GAME — game_sessions 컬럼 보강
-- ============================================================
ALTER TABLE game_sessions
    ADD COLUMN IF NOT EXISTS app_session_id              UUID REFERENCES app_sessions(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS chosen_dimension            VARCHAR(20),
    ADD COLUMN IF NOT EXISTS abandoned_at                TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS triggered_by_emotion_log_id BIGINT REFERENCES emotion_logs(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS closing_message             TEXT,
    ADD COLUMN IF NOT EXISTS scene_count_completed       SMALLINT DEFAULT 0,
    ADD COLUMN IF NOT EXISTS duration_seconds            INTEGER,
    -- XR-INTEGRATION 보강 (디바이스 차이 추적)
    ADD COLUMN IF NOT EXISTS device_type                 VARCHAR(30),
    ADD COLUMN IF NOT EXISTS capabilities                JSONB,
    ADD COLUMN IF NOT EXISTS assets_manifest_version     VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_game_sessions_character_completed
    ON game_sessions(character, completed_at);

-- ============================================================
-- GAME — game_decisions (Scene 별 사용자 선택, JSONB)
-- ============================================================
CREATE TABLE IF NOT EXISTS game_decisions (
    id                  BIGSERIAL PRIMARY KEY,
    game_session_id     UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    scene_number        SMALLINT NOT NULL,
    scene_name          VARCHAR(50),
    decision            JSONB NOT NULL,           -- 캐릭터별 schema 자유
    interaction_meta    JSONB,                    -- 햅틱·시선·망설임 등
    decided_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_game_decisions_session
    ON game_decisions(game_session_id, scene_number);

-- 요셉 Scene 2 저장 비율 패턴 분석용 expression index
CREATE INDEX IF NOT EXISTS idx_game_decisions_joseph_storage
    ON game_decisions((decision->>'save_ratio'))
    WHERE scene_name = 'storage_decision';

-- ============================================================
-- GAME — scene_views (Scene 별 머문 시간, A/B 분석)
-- ============================================================
CREATE TABLE IF NOT EXISTS scene_views (
    id                 BIGSERIAL PRIMARY KEY,
    game_session_id    UUID NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
    scene_number       SMALLINT NOT NULL,
    entered_at         TIMESTAMPTZ NOT NULL,
    exited_at          TIMESTAMPTZ,
    duration_seconds   INTEGER GENERATED ALWAYS AS (
        CASE WHEN exited_at IS NULL THEN NULL
             ELSE EXTRACT(EPOCH FROM (exited_at - entered_at))::INTEGER
        END
    ) STORED,
    exit_reason        VARCHAR(30),   -- 'completed' | 'abandoned' | 'skipped' | 'safety_triggered'
    skipped_silence    BOOLEAN DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_scene_views_session
    ON scene_views(game_session_id, scene_number);
