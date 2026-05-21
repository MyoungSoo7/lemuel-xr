-- V7: SAFETY 도메인 (위기 신호·자원 안내)
-- DB-SCHEMA.md §7 + ETHICS-LEGAL.md §3 (위기 자원)

-- ============================================================
-- safety_alerts — 위기 키워드 매칭 발생 기록
-- ============================================================
CREATE TABLE IF NOT EXISTS safety_alerts (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             UUID REFERENCES users(id) ON DELETE SET NULL,   -- 게스트 가능
    app_session_id      UUID REFERENCES app_sessions(id) ON DELETE SET NULL,
    emotion_log_id      BIGINT REFERENCES emotion_logs(id) ON DELETE SET NULL,
    matched_pattern     VARCHAR(50) NOT NULL,          -- 'self_harm' | 'suicidal_ideation' | 'abuse' | 'crisis_keyword'
    severity            VARCHAR(10) NOT NULL,          -- 'low' | 'medium' | 'high'
    trigger_source      VARCHAR(20) NOT NULL,          -- 'emotion_text' | 'diary' | 'psalm' | 'game_decision'
    raw_excerpt_hash    VARCHAR(64),                   -- 평문 저장 금지 — 검색용 SHA-256
    shown_resources     JSONB,                         -- 보여준 자원 리스트
    user_acknowledged   BOOLEAN DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_safety_alerts_user
    ON safety_alerts(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_safety_alerts_severity_time
    ON safety_alerts(severity, created_at DESC);

-- ============================================================
-- crisis_resources — 위기 자원 카탈로그 (다국어)
-- ============================================================
CREATE TABLE IF NOT EXISTS crisis_resources (
    id                  BIGSERIAL PRIMARY KEY,
    region              VARCHAR(10) NOT NULL,          -- 'KR' | 'US' | 'JP' | ...
    locale              VARCHAR(10) NOT NULL,          -- 'ko-KR' | 'en-US' | ...
    name                VARCHAR(100) NOT NULL,
    contact_type        VARCHAR(20) NOT NULL,          -- 'phone' | 'chat' | 'web' | 'sms'
    contact_value       VARCHAR(255) NOT NULL,
    description         TEXT,
    hours               VARCHAR(50),                   -- '24/7' | '평일 09-18' ...
    category            VARCHAR(30),                   -- 'suicide' | 'mental_health' | 'abuse' | 'general'
    priority            SMALLINT DEFAULT 50,           -- 낮을수록 우선
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_crisis_resources_region_active
    ON crisis_resources(region, locale, active, priority);

-- 한국 핵심 위기 자원 seed
INSERT INTO crisis_resources (region, locale, name, contact_type, contact_value, description, hours, category, priority) VALUES
    ('KR', 'ko-KR', '자살예방상담전화',        'phone', '1393',                  '24시간 자살·정신건강 위기 상담',         '24/7',         'suicide',       1),
    ('KR', 'ko-KR', '정신건강위기상담전화',     'phone', '1577-0199',             '정신건강 종합 상담',                   '24/7',         'mental_health', 2),
    ('KR', 'ko-KR', '청소년전화',              'phone', '1388',                  '청소년 위기 상담',                     '24/7',         'mental_health', 3),
    ('KR', 'ko-KR', '여성긴급전화',            'phone', '1366',                  '가정폭력·성폭력 피해 지원',            '24/7',         'abuse',         4),
    ('KR', 'ko-KR', '한국생명의전화',          'phone', '1588-9191',             '자살 위기 상담',                       '24/7',         'suicide',       5),
    ('KR', 'ko-KR', '보건복지부 정신건강정보', 'web',   'https://www.mentalhealth.go.kr', '정신건강 종합 정보 포털',     'always',       'general',       10)
ON CONFLICT DO NOTHING;
