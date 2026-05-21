-- V20260521173629: content_quarantine — 검토 거부 / Veto 격리 테이블
--
-- 배경: SEQUENCE-DIAGRAMS §5 의 거부 분기에서 *content_quarantine* INSERT 로 표시했었지만
-- 실제 테이블이 없었음. B 산출물 — 임상 자문 Veto / 양쪽 reject / 자동 키워드 필터에
-- 걸린 콘텐츠를 격리 + 운영자 사후 검토.
--
-- [domain: THEOLOGY] — V8 의 후속


CREATE TABLE IF NOT EXISTS content_quarantine (
    id                  BIGSERIAL PRIMARY KEY,
    content_version_id  UUID NOT NULL REFERENCES content_versions(id) ON DELETE CASCADE,

    /** 격리 사유 — clinical_veto / theology_reject / clinical_reject / auto_keyword_filter / manual */
    veto_by             VARCHAR(32) NOT NULL,

    /** 자유 텍스트 사유 — Veto 시 clinical_reviews.veto_reason 복사, reject 시 verdict notes */
    reason              TEXT NOT NULL,

    /** 키워드 필터 트리거 시 매칭 키워드 — 영지주의 / 뉴에이지 / 자해 트리거 등 */
    blocked_keywords    JSONB NOT NULL DEFAULT '[]'::jsonb,

    /** 참조 review row — 어느 검토가 이 격리를 트리거했는지 */
    triggered_by_theology_review_id BIGINT REFERENCES theology_reviews(id) ON DELETE SET NULL,
    triggered_by_clinical_review_id BIGINT REFERENCES clinical_reviews(id) ON DELETE SET NULL,

    /** 운영자 후속 처리 */
    reviewed_by_admin   UUID REFERENCES users(id) ON DELETE SET NULL,
    admin_action        VARCHAR(32),                          -- 'closed' | 'escalated' | NULL (pending)
    admin_notes         TEXT,

    quarantined_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at         TIMESTAMPTZ,

    CONSTRAINT ck_content_quarantine_veto_by
        CHECK (veto_by IN ('clinical_veto', 'theology_reject', 'clinical_reject',
                           'both_reject', 'auto_keyword_filter', 'manual')),
    CONSTRAINT ck_content_quarantine_blocked_keywords_array
        CHECK (jsonb_typeof(blocked_keywords) = 'array'),
    CONSTRAINT ck_content_quarantine_admin_action
        CHECK (admin_action IS NULL OR admin_action IN ('closed', 'escalated'))
);

CREATE INDEX IF NOT EXISTS idx_content_quarantine_pending
    ON content_quarantine (quarantined_at DESC)
    WHERE resolved_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_content_quarantine_version
    ON content_quarantine (content_version_id, quarantined_at DESC);

CREATE INDEX IF NOT EXISTS idx_content_quarantine_veto_by
    ON content_quarantine (veto_by, quarantined_at DESC);

CREATE INDEX IF NOT EXISTS idx_content_quarantine_keywords_gin
    ON content_quarantine USING gin (blocked_keywords);

COMMENT ON TABLE content_quarantine IS
    'SEQUENCE-DIAGRAMS §5 의 거부 분기 적재 테이블. clinical Veto / 양쪽 reject / 키워드 필터 격리.';


-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
-- DROP INDEX IF EXISTS idx_content_quarantine_keywords_gin;
-- DROP INDEX IF EXISTS idx_content_quarantine_veto_by;
-- DROP INDEX IF EXISTS idx_content_quarantine_version;
-- DROP INDEX IF EXISTS idx_content_quarantine_pending;
-- DROP TABLE IF EXISTS content_quarantine;
