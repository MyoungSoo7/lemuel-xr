-- V20260521135130: theology_reviews 에 reviewer_profile_id FK 추가
--
-- 배경: V20260521040956 에서 reviewer_profiles 일반화 테이블 + clinical_reviews 추가.
-- 그러나 기존 theology_reviews (V8) 는 reviewer_id (users) 만 가지고 있어 자문가의 자격·
-- 권한·범위 정보를 즉시 join 못 함. 임상 검토와 같은 데이터 구조로 정합성 맞춤.
--
-- [domain: THEOLOGY] — V8__theology_domain.sql 의 후속


-- ============================================================
-- theology_reviews 에 reviewer_profile_id 컬럼 추가
-- ============================================================

ALTER TABLE theology_reviews
    ADD COLUMN IF NOT EXISTS reviewer_profile_id UUID
        REFERENCES reviewer_profiles(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_theology_reviews_profile
    ON theology_reviews (reviewer_profile_id)
    WHERE reviewer_profile_id IS NOT NULL;

COMMENT ON COLUMN theology_reviews.reviewer_profile_id IS
    '자문가의 등록 프로필 (role=theology). 신규 검토는 이 컬럼 채움. ' ||
    '레거시 row 는 reviewer_id (users) 만 있고 NULL.';


-- ============================================================
-- (선택) 백필 — 기존 theology_reviews 의 reviewer_id 와 매칭되는
--                reviewer_profiles row 가 있으면 연결
-- ============================================================

UPDATE theology_reviews tr
SET reviewer_profile_id = rp.id
FROM reviewer_profiles rp
WHERE tr.reviewer_id IS NOT NULL
  AND tr.reviewer_id = rp.user_id
  AND rp.role = 'theology'
  AND tr.reviewer_profile_id IS NULL;


-- ============================================================
-- 권장 — clinical_reviews 와 동일하게 referenced_pmids JSONB 도 추가
--          (cross-check / 동일 검토 API 형태 유지)
-- ============================================================

ALTER TABLE theology_reviews
    ADD COLUMN IF NOT EXISTS referenced_pmids JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE theology_reviews
    ADD CONSTRAINT ck_theology_reviews_referenced_pmids_array
    CHECK (jsonb_typeof(referenced_pmids) = 'array');

CREATE INDEX IF NOT EXISTS idx_theology_reviews_referenced_pmids_gin
    ON theology_reviews USING gin (referenced_pmids);


-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
-- DROP INDEX IF EXISTS idx_theology_reviews_referenced_pmids_gin;
-- ALTER TABLE theology_reviews DROP CONSTRAINT IF EXISTS ck_theology_reviews_referenced_pmids_array;
-- ALTER TABLE theology_reviews DROP COLUMN IF EXISTS referenced_pmids;
--
-- DROP INDEX IF EXISTS idx_theology_reviews_profile;
-- ALTER TABLE theology_reviews DROP COLUMN IF EXISTS reviewer_profile_id;
