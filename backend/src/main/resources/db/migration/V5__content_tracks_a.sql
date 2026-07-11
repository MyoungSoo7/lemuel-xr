-- V5: CONTENT 도메인 (트랙 A — 일기·시편·잠언·전도서)
-- DB-SCHEMA.md §5 + ETHICS-LEGAL §2.2 (민감 정보 암호화)

-- pgcrypto 활성화 (일기·시편 본문 row-level 암호화 위해)
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- diary_entries — Theme 1 (일기와 묵상)
-- ============================================================
CREATE TABLE IF NOT EXISTS diary_entries (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body                    TEXT,                       -- 평문 (옵션) OR
    body_encrypted          BYTEA,                      -- pgcrypto 암호화 (권장)
    form_type               VARCHAR(20),                -- 'free' | '5w1h' | 'emotion_label'
    emotion_label           VARCHAR(30),
    intensity               SMALLINT,
    meditation_text         TEXT,
    meditation_accepted     BOOLEAN DEFAULT FALSE,
    meditation_dimension    VARCHAR(20),                -- 'spiritual'|'emotional'|'rational'
    word_count              INTEGER,
    sentiment_score         NUMERIC(3,2),               -- -1.00 ~ +1.00
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (body IS NOT NULL OR body_encrypted IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_diary_user_time
    ON diary_entries(user_id, created_at DESC);

-- ============================================================
-- user_psalms — Theme 4 (사용자 작성 시편)
-- ============================================================
CREATE TABLE IF NOT EXISTS user_psalms (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    psalm_form              VARCHAR(20),                -- 'lament'|'petition'|'thanksgiving'|'praise'
    raw_text                TEXT,
    raw_text_encrypted      BYTEA,
    polished_text           TEXT,                       -- AI 가 시편 운율로 다듬은 버전
    accepted_polished       BOOLEAN DEFAULT FALSE,
    inspired_by_psalm       VARCHAR(20),                -- '23' | '42:1-3' ...
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_user_psalms_user
    ON user_psalms(user_id, created_at DESC);

-- ============================================================
-- proverbs_interactions — Theme 2 (잠언 카드 추천)
-- ============================================================
CREATE TABLE IF NOT EXISTS proverbs_interactions (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_situation           TEXT,
    recommended_proverbs     JSONB,                     -- [{"ref":"prov-3:5","fit_score":0.92}, ...]
    chosen_proverb_ref       VARCHAR(20),
    chosen_dimension         VARCHAR(20),
    reframing_response       JSONB,                     -- {"applied_to":...,"doubt":...,"next_step":...}
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_proverbs_user
    ON proverbs_interactions(user_id, created_at DESC);

-- ============================================================
-- ecclesiastes_views — Theme 3 (전도서 사용 기록)
-- ============================================================
CREATE TABLE IF NOT EXISTS ecclesiastes_views (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chapter_ref              VARCHAR(20),               -- 'eccl-1' | 'eccl-3:1-8'
    user_season              VARCHAR(20),
    futility_note            TEXT,
    meaning_note             TEXT,
    listened_audio           BOOLEAN DEFAULT FALSE,
    conclusion_viewed        BOOLEAN DEFAULT FALSE,     -- 전 12:13 함께 봤나
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ecclesiastes_user
    ON ecclesiastes_views(user_id, created_at DESC);
