-- V20260521040956: 임상 자문 + content_versions.references JSONB
--
-- 배경: Issue #4 (Milstein 2025 COPE 프레임워크) — 신학 자문과 *병렬* 로 임상 자문
-- 검토를 도입. theology_reviews 는 신학 체크리스트(scripture_accuracy / doctrinal_balance),
-- clinical_reviews 는 임상 체크리스트(trauma_safety / crisis_resource / moral_injury / evidence)
-- 로 역할별 분리. reviewer_profiles 는 자문가 등록·자격·권한·범위 일반화.
--
-- V13 부터 타임스탬프 컨벤션 (skill: lemuel-xr-flyway-migration). V1~V12 정수 유지.
--
-- [domain: THEOLOGY + SAFETY 횡단]


-- ============================================================
-- [P3] content_versions.references — PMID / DOI / URL 배열 (학술 근거 트레이스)
-- ============================================================

ALTER TABLE content_versions
    ADD COLUMN IF NOT EXISTS "references" JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE content_versions
    ADD CONSTRAINT ck_content_versions_references_is_array
    CHECK (jsonb_typeof("references") = 'array');

-- 자주 조회될 PMID 검색용 GIN 인덱스
CREATE INDEX IF NOT EXISTS idx_content_versions_references_gin
    ON content_versions USING gin ("references");

COMMENT ON COLUMN content_versions."references" IS
    'PMID/DOI/URL 배열. ex: [{"type":"pmid","id":"35609469","note":"moral injury"}]';


-- ============================================================
-- reviewer_profiles — 자문가 등록 (theology / clinical / ethics / editorial)
-- ============================================================
CREATE TABLE IF NOT EXISTS reviewer_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role                VARCHAR(20) NOT NULL,           -- 'theology'|'clinical'|'ethics'|'editorial'
    credential          VARCHAR(255),                   -- 면허/자격 (정신과 의사·임상심리사·목사·신학 석사 등)
    organization        VARCHAR(255),                   -- 소속
    bio                 TEXT,
    -- 활성화 상태
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deactivated_at      TIMESTAMPTZ,
    -- 권한
    can_veto            BOOLEAN NOT NULL DEFAULT FALSE, -- 단독 reject 권한 (moral injury 위험 등)
    review_scopes       JSONB NOT NULL DEFAULT '[]'::jsonb,  -- ex: ["theme_5","theme_11","trigger_high"]
    -- ---
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_reviewer_profiles_role
        CHECK (role IN ('theology','clinical','ethics','editorial')),
    CONSTRAINT ck_reviewer_profiles_scopes_array
        CHECK (jsonb_typeof(review_scopes) = 'array'),
    CONSTRAINT uq_reviewer_user_role UNIQUE (user_id, role)
);

CREATE INDEX IF NOT EXISTS idx_reviewer_profiles_active_role
    ON reviewer_profiles (role, is_active)
    WHERE is_active = TRUE;

CREATE INDEX IF NOT EXISTS idx_reviewer_profiles_scopes_gin
    ON reviewer_profiles USING gin (review_scopes);

COMMENT ON TABLE reviewer_profiles IS
    'Issue #4 거버넌스 — Milstein 2025 COPE Professional engagement 축.';


-- ============================================================
-- clinical_reviews — 임상 자문 검토 결과
--   theology_reviews 와 *동일 content_version 에 병렬* 로 작성됨
-- ============================================================
CREATE TABLE IF NOT EXISTS clinical_reviews (
    id                          BIGSERIAL PRIMARY KEY,
    content_version_id          UUID NOT NULL REFERENCES content_versions(id) ON DELETE CASCADE,
    reviewer_id                 UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewer_profile_id         UUID REFERENCES reviewer_profiles(id) ON DELETE SET NULL,
    verdict                     VARCHAR(20) NOT NULL,   -- 'approve'|'request_changes'|'reject'

    -- 임상 체크리스트 (1-5 score; NULL = 적용 안 함)
    trauma_safety               SMALLINT,                -- 트라우마 자극 안전 (높을수록 안전)
    crisis_resource_compliance  SMALLINT,                -- 위기 자원 catalog 적절 활용
    moral_injury_risk           SMALLINT,                -- moral injury 위험 (낮을수록 좋음 — Jones 2022 PMID 35609469)
    evidence_quality            SMALLINT,                -- 인용된 PMID·근거의 적절성

    -- veto (자해/심각한 moral injury 위험 시 reviewer 단독 reject)
    veto_used                   BOOLEAN NOT NULL DEFAULT FALSE,
    veto_reason                 TEXT,

    -- 검토 메타
    notes                       TEXT,
    referenced_pmids            JSONB NOT NULL DEFAULT '[]'::jsonb,
    suggested_changes           JSONB,

    reviewed_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_clinical_reviews_verdict
        CHECK (verdict IN ('approve','request_changes','reject')),
    CONSTRAINT ck_clinical_reviews_trauma_safety_range
        CHECK (trauma_safety IS NULL OR trauma_safety BETWEEN 1 AND 5),
    CONSTRAINT ck_clinical_reviews_crisis_range
        CHECK (crisis_resource_compliance IS NULL OR crisis_resource_compliance BETWEEN 1 AND 5),
    CONSTRAINT ck_clinical_reviews_moral_injury_range
        CHECK (moral_injury_risk IS NULL OR moral_injury_risk BETWEEN 1 AND 5),
    CONSTRAINT ck_clinical_reviews_evidence_range
        CHECK (evidence_quality IS NULL OR evidence_quality BETWEEN 1 AND 5),
    CONSTRAINT ck_clinical_reviews_referenced_pmids_array
        CHECK (jsonb_typeof(referenced_pmids) = 'array'),
    CONSTRAINT ck_clinical_reviews_veto_requires_reason
        CHECK (NOT veto_used OR veto_reason IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_clinical_reviews_version
    ON clinical_reviews (content_version_id);

CREATE INDEX IF NOT EXISTS idx_clinical_reviews_pending_changes
    ON clinical_reviews (reviewed_at DESC)
    WHERE verdict = 'request_changes';

CREATE INDEX IF NOT EXISTS idx_clinical_reviews_veto
    ON clinical_reviews (reviewed_at DESC)
    WHERE veto_used = TRUE;

CREATE INDEX IF NOT EXISTS idx_clinical_reviews_referenced_pmids_gin
    ON clinical_reviews USING gin (referenced_pmids);

COMMENT ON TABLE clinical_reviews IS
    'theology_reviews 와 *병렬* — 양쪽 모두 approve 여야 content_versions.status=published.';


-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
-- DROP INDEX IF EXISTS idx_clinical_reviews_referenced_pmids_gin;
-- DROP INDEX IF EXISTS idx_clinical_reviews_veto;
-- DROP INDEX IF EXISTS idx_clinical_reviews_pending_changes;
-- DROP INDEX IF EXISTS idx_clinical_reviews_version;
-- DROP TABLE IF EXISTS clinical_reviews;
--
-- DROP INDEX IF EXISTS idx_reviewer_profiles_scopes_gin;
-- DROP INDEX IF EXISTS idx_reviewer_profiles_active_role;
-- DROP TABLE IF EXISTS reviewer_profiles;
--
-- DROP INDEX IF EXISTS idx_content_versions_references_gin;
-- ALTER TABLE content_versions DROP CONSTRAINT IF EXISTS ck_content_versions_references_is_array;
-- ALTER TABLE content_versions DROP COLUMN IF EXISTS "references";
