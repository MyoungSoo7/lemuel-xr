-- V20260522014700: 자기만의 7 가치 빌더 (CROSS-MAPPING-VR-AR.md §5).
--
-- 배경 (2026-05-22 mission 정착):
--   lemuel-xr = 영적 비상 대비 훈련. 궁극 목표 = 사용자가 *자기만의 7 가치* 만들고 습관화.
--   4 인물 (VR) 은 그 7 가치를 *빛내는 매개*.
--
-- 추가:
--   user_value_profiles  — 사용자별 7 가치 JSON. 사용자 1명당 1행 (UNIQUE user_id).
--   user_value_practices — 일별 실천 로그. CDR Index 계산용.
--
-- Rollback:
--   DROP TABLE user_value_practices; DROP TABLE user_value_profiles;

CREATE TABLE IF NOT EXISTS user_value_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- 7 가치 = JSON. 키 1~7 고정 (string), value 는 사용자 정의 텍스트 + 옵션 매핑.
    -- 예시:
    --   {
    --     "1": {"title": "흔들리지 않는 결정", "anchor_character": "joseph", "anchor_scripture": "gen-41:33", "note": "결정 전 잠깐 멈추기"},
    --     "4": {"title": "솔직한 감정 토로", "anchor_character": "david", "anchor_scripture": "ps-22:1"},
    --     ...
    --   }
    values_json         JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS user_value_practices (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    value_id            SMALLINT NOT NULL CHECK (value_id BETWEEN 1 AND 7),
    practiced_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    duration_sec        INTEGER,
    note                TEXT,
    -- 4 인물 미션과 연계 (옵션) — VR Scene 종료 직후 실천 기록 시 박힘.
    linked_character    VARCHAR(20),
    linked_game_session UUID REFERENCES game_sessions(id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_practices_user_value_time
    ON user_value_practices(user_id, value_id, practiced_at DESC);
CREATE INDEX IF NOT EXISTS idx_practices_user_streak
    ON user_value_practices(user_id, practiced_at DESC);

-- topic_contents — AR 1~7 각 주제의 *큐레이션 콘텐츠 카드* (본문 + 묵상 + 적용).
-- V20260522014700 의 일부로 함께 — 시드는 별도 V20260522014800 마이그레이션.
CREATE TABLE IF NOT EXISTS topic_contents (
    id                  BIGSERIAL PRIMARY KEY,
    topic_id            SMALLINT NOT NULL CHECK (topic_id BETWEEN 1 AND 7),
    title               VARCHAR(200) NOT NULL,
    scripture_ref       VARCHAR(50),
    body                TEXT NOT NULL,
    -- 4 인물 매핑 — 이 콘텐츠가 어느 인물 가치를 빛내는지.
    anchor_character    VARCHAR(20),
    -- 큐레이션 메타
    curator             VARCHAR(50) NOT NULL DEFAULT 'system',
    published_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- 추천 알고리즘용
    target_emotion      VARCHAR(30),
    difficulty          SMALLINT DEFAULT 1 CHECK (difficulty BETWEEN 1 AND 3),
    -- 사용자 노출 여부 (false 면 작업 중)
    active              BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE INDEX IF NOT EXISTS idx_topic_contents_topic_active
    ON topic_contents(topic_id, active, target_emotion);

COMMENT ON TABLE user_value_profiles IS
    '사용자가 자기 언어로 정의한 7 가치 프로파일 (CROSS-MAPPING-VR-AR.md §5).';
COMMENT ON TABLE user_value_practices IS
    '일별 실천 로그. CDR Index (영적 비상 대비 준비도) 계산용.';
COMMENT ON TABLE topic_contents IS
    'AR 1~7 주제 큐레이션 카드. 4 인물 매핑 + 감정·난이도 추천 필터.';
