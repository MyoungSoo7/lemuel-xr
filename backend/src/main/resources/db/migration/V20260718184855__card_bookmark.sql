-- V20260718184855: AR 토픽 카드 북마크 (card_bookmarks).
--
-- Seed(ooo): 익명 게스트가 AR 토픽 카드(topic_contents)를 북마크하고 '내 북마크' 목록에서 다시 본다.
--   - 신원: 서버 + 익명 guest_id — users(guest) 행에 FK (로그인 불필요, 익명 우선 P1)
--   - 대상: topic_contents(id) (BIGSERIAL)
--   - 멱등: UNIQUE(user_id, topic_content_id) — 중복 북마크 방지, un-bookmark = 행 DELETE(토글)
--
-- CONTENT 도메인. cross-domain FK 는 users(IDENTITY) 뿐 — 익명 우선 원칙상 필수.

CREATE TABLE IF NOT EXISTS card_bookmarks (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_content_id    BIGINT NOT NULL REFERENCES topic_contents(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, topic_content_id)
);

-- '내 북마크' 목록 = 사용자별 최신순 조회.
CREATE INDEX IF NOT EXISTS idx_card_bookmarks_user_time
    ON card_bookmarks(user_id, created_at DESC);

COMMENT ON TABLE card_bookmarks IS
    'AR 토픽 카드(topic_contents) 북마크. 익명 guest_id FK, UNIQUE(user_id, topic_content_id) 멱등.';

-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
-- DROP INDEX IF EXISTS idx_card_bookmarks_user_time;
-- DROP TABLE IF EXISTS card_bookmarks;
