-- V20260521220227: theology_reviews 에 referenced_pmids JSONB 컬럼 + 인덱스 + 제약 추가
--
-- 배경: V20260521135130__align_theology_reviews_with_profiles.sql 파일이 *적용된 뒤*
-- referenced_pmids 섹션이 사후에 추가됐다. Flyway 는 같은 version 을 재실행하지 않으므로
-- production DB 에는 컬럼이 들어가지 않은 채 entity (TheologyReviewJpaEntity.referencedPmids)
-- 만 새 컬럼을 기대하게 됨 → Hibernate 의 schema validation 실패로 새 backend pod CrashLoop.
--
-- Production 은 수동 ALTER 로 우회 fix 됐지만 flyway_schema_history 와 repo 의 마이그레이션
-- 파일에 기록이 없어 *다른 환경 (개발/스테이징/DR 복구)* 에서 같은 crashloop 재발 위험.
-- 본 보조 마이그레이션이 그 gap 을 메운다.
--
-- 모든 SQL 은 `IF NOT EXISTS` / `IF NOT EXISTS` 로 작성 — production 에서는 no-op 으로
-- 안전 통과, 다른 환경에서는 누락 컬럼·인덱스·제약을 채운다.
--
-- [domain: THEOLOGY]


-- ============================================================
-- 컬럼 추가 — clinical_reviews 와 동일 구조 유지
-- ============================================================

ALTER TABLE theology_reviews
    ADD COLUMN IF NOT EXISTS referenced_pmids JSONB NOT NULL DEFAULT '[]'::jsonb;


-- ============================================================
-- 배열 타입 무결성 제약
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_theology_reviews_referenced_pmids_array'
    ) THEN
        ALTER TABLE theology_reviews
            ADD CONSTRAINT ck_theology_reviews_referenced_pmids_array
            CHECK (jsonb_typeof(referenced_pmids) = 'array');
    END IF;
END$$;


-- ============================================================
-- GIN 인덱스 — PMID 부분 조회 패턴 대비
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_theology_reviews_referenced_pmids_gin
    ON theology_reviews USING gin (referenced_pmids);


COMMENT ON COLUMN theology_reviews.referenced_pmids IS
    'PubMed PMID 배열 (JSONB). clinical_reviews 와 동일 구조 — cross-check / 동일 검토 API 형태 유지.';


-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
-- DROP INDEX IF EXISTS idx_theology_reviews_referenced_pmids_gin;
-- ALTER TABLE theology_reviews DROP CONSTRAINT IF EXISTS ck_theology_reviews_referenced_pmids_array;
-- ALTER TABLE theology_reviews DROP COLUMN IF EXISTS referenced_pmids;
