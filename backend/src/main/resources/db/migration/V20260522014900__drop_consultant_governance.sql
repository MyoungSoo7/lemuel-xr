-- V20260522014900: 자문 거버넌스 도메인 전면 폐기
--
-- 배경: 2026-05-22 프로젝트 positioning 전면 수정.
--   기존: "임상·신학 자문가 검증을 통과한 본문만 사용자에게 노출" 거버넌스 모델.
--   신규: "자살예방 영적단련 *교육* 콘텐츠" 모델 — 임상 진단·자살위험 평가 영역에서
--         완전히 빠짐. 출판물·교육물 카테고리로 자문 *필수 검증* 절차 자체를 제거.
--
-- 본 마이그레이션은 자문 거버넌스 도메인의 모든 테이블·인덱스·제약을 drop.
-- Drop 순서는 FK 의존 역순:
--   1) content_quarantine  → theology_reviews, clinical_reviews, content_versions 참조
--   2) theology_reviews    → content_versions, reviewer_profiles 참조
--   3) clinical_reviews    → content_versions, reviewer_profiles 참조
--   4) content_versions    → self-reference (parent_version_id)
--   5) reviewer_profiles   → users 참조 (leaf)
--
-- [domain: THEOLOGY - DESTRUCTIVE]


BEGIN;

DROP TABLE IF EXISTS content_quarantine CASCADE;
DROP TABLE IF EXISTS theology_reviews   CASCADE;
DROP TABLE IF EXISTS clinical_reviews   CASCADE;
DROP TABLE IF EXISTS content_versions   CASCADE;
DROP TABLE IF EXISTS reviewer_profiles  CASCADE;

COMMIT;


-- ROLLBACK NOTES (자동 적용 금지 — 운영 사고 시 reference)
-- 본 drop 은 *완전 의도된* 도메인 폐기. 복원은 V8 / V20260521040956 /
-- V20260521135130 / V20260521173629 / V20260521220227 다섯 마이그레이션의
-- CREATE TABLE 부분을 새 timestamp 로 다시 작성하는 형태로만 가능.
-- 데이터 복원은 백업본 (Velero) 에서 .pgdump 추출 필요.
