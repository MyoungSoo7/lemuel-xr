-- V8: THEOLOGY 도메인 (콘텐츠 검수 + 버전 관리)
-- DB-SCHEMA.md §8 + CONTENT-WORKFLOW.md §3 (신학·심리 검수 흐름)

-- ============================================================
-- content_versions — 발행 가능한 모든 콘텐츠의 버전 단위
--   대상: scene_script, meditation_template, proverb_card, psalm_polish_template ...
-- ============================================================
CREATE TABLE IF NOT EXISTS content_versions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_kind        VARCHAR(30) NOT NULL,          -- 'scene_script'|'meditation'|'proverb_card'|...
    content_ref         VARCHAR(100) NOT NULL,         -- 'joseph.scene2.storage_decision' 같은 의미 키
    version             VARCHAR(20) NOT NULL,          -- '1.0.0' (semver) | '2026-05-15'
    body                JSONB NOT NULL,                -- 콘텐츠 페이로드 (스크립트·옵션·메타)
    status              VARCHAR(20) NOT NULL DEFAULT 'draft',  -- 'draft'|'in_review'|'approved'|'rejected'|'published'|'archived'
    generated_by        VARCHAR(20),                   -- 'human' | 'ai_gpt4o' | 'ai_claude_opus' ...
    generation_prompt   TEXT,                          -- AI 생성이라면 프롬프트 보존
    created_by          UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ,
    superseded_by       UUID REFERENCES content_versions(id) ON DELETE SET NULL,
    UNIQUE (content_kind, content_ref, version)
);
CREATE INDEX IF NOT EXISTS idx_content_versions_ref_status
    ON content_versions(content_kind, content_ref, status);
CREATE INDEX IF NOT EXISTS idx_content_versions_published
    ON content_versions(content_kind, content_ref, published_at DESC)
    WHERE status = 'published';

-- ============================================================
-- theology_reviews — 신학·심리 검수 결과
-- ============================================================
CREATE TABLE IF NOT EXISTS theology_reviews (
    id                  BIGSERIAL PRIMARY KEY,
    content_version_id  UUID NOT NULL REFERENCES content_versions(id) ON DELETE CASCADE,
    reviewer_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewer_role       VARCHAR(20) NOT NULL,          -- 'theology' | 'psychology' | 'ethics'
    verdict             VARCHAR(20) NOT NULL,          -- 'approve' | 'request_changes' | 'reject'
    -- 신학 검수 체크리스트
    scripture_accuracy  SMALLINT,                      -- 1-5
    doctrinal_balance   SMALLINT,                      -- 1-5 (특정 교파 편향 여부)
    therapeutic_safety  SMALLINT,                      -- 1-5 (해롭지 않은가)
    notes               TEXT,
    suggested_changes   JSONB,
    reviewed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_theology_reviews_version
    ON theology_reviews(content_version_id);
CREATE INDEX IF NOT EXISTS idx_theology_reviews_pending
    ON theology_reviews(reviewer_role, reviewed_at DESC)
    WHERE verdict = 'request_changes';
