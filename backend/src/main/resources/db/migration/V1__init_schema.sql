-- V1: Lemuel XR 초기 스키마
-- 게스트 사용자 + 감정 기록 + 게임 세션 + 본문 + LLM 캐시 5개 테이블

-- 사용자 (게스트 모드 — 디바이스별 UUID 발급)
CREATE TABLE users (
    id              UUID            PRIMARY KEY,
    device_id       VARCHAR(100),                       -- 디바이스 식별자 (옵션)
    device_type     VARCHAR(30),                        -- 'quest3' | 'vision_pro' | 'galaxy_xr'
    guest           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    last_seen_at    TIMESTAMP
);

CREATE INDEX idx_users_device_id ON users (device_id);

-- 감정 입력 로그
CREATE TABLE emotion_logs (
    id                   BIGSERIAL    PRIMARY KEY,
    user_id              UUID         NOT NULL REFERENCES users(id),
    raw_text             TEXT         NOT NULL,
    classified_emotion   VARCHAR(30),                  -- 불안/슬픔/분노/혼란/외로움/지침/감사
    confidence           NUMERIC(4,3),
    created_at           TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_emotion_logs_user_id ON emotion_logs (user_id, created_at DESC);

-- 게임 세션
CREATE TABLE game_sessions (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL REFERENCES users(id),
    character       VARCHAR(20)     NOT NULL,           -- 'joseph' (MVP) | 'david' | 'moses' | 'jesus'
    started_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMP,
    decisions       JSONB           NOT NULL DEFAULT '{}'::jsonb,
                                                        -- {"scene2_save":"1/3", "scene3_distribute":["farmer","immigrant"]}
    final_outcome   VARCHAR(50)                         -- 'farmer_first' | 'immigrant_first' | 'merchant_first'
);

CREATE INDEX idx_game_sessions_user_id ON game_sessions (user_id, started_at DESC);

-- 성경 본문 (창세기 41~45 MVP 범위)
CREATE TABLE scripture_passages (
    id           BIGSERIAL    PRIMARY KEY,
    reference    VARCHAR(50)  NOT NULL,    -- 'gen-45:5'
    translation  VARCHAR(20)  NOT NULL,    -- 'modern' (현대인의 성경) | 'rev' (개역개정 fallback)
    book         VARCHAR(20)  NOT NULL,    -- 'genesis'
    chapter      INT          NOT NULL,
    verse_start  INT          NOT NULL,
    verse_end    INT,
    text         TEXT         NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE (reference, translation)
);

CREATE INDEX idx_scripture_book_chapter ON scripture_passages (book, chapter, verse_start);

-- LLM 응답 캐시 (사전 생성 또는 hit 시 누적)
CREATE TABLE llm_cache (
    cache_key     VARCHAR(200)   PRIMARY KEY,
    response      TEXT           NOT NULL,
    model         VARCHAR(50)    NOT NULL,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    hit_count     INT            NOT NULL DEFAULT 0
);

-- TTS 생성 wav 캐시 메타 (실 wav 파일은 R2 또는 PVC)
CREATE TABLE tts_cache (
    cache_key      VARCHAR(200)    PRIMARY KEY,
    text_hash      VARCHAR(64)     NOT NULL,
    voice_id       VARCHAR(50),
    storage_url    VARCHAR(500)    NOT NULL,             -- s3://lemuel-xr-tts/... 또는 file://
    duration_ms    INT,
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    hit_count      INT             NOT NULL DEFAULT 0
);

CREATE INDEX idx_tts_cache_hash ON tts_cache (text_hash);
