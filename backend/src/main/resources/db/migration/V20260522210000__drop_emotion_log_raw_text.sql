-- V20260522210000: emotion_logs 에서 사용자 원본 텍스트 컬럼 폐기 (PHI 비수집)
--
-- 배경: docs/safety-guidelines.md §3 (사용자 정신건강 데이터 비수집).
--   사용자가 "오늘 너무 외롭다" 같은 자유 텍스트를 입력하면 그 *원본* 이
--   분류 후 폐기돼야 함. 그러나 V1 / V3 에서 raw_text / raw_text_encrypted
--   컬럼이 정의돼 있어 영속화 가능 상태였음. 본 마이그레이션이 그 가능성을 제거.
--
-- 분류 결과 (classified_emotion enum) 만 보존, 원본 텍스트는 DB 스키마 자체에서 빼버려
-- 향후 *실수로 채울 수 없게* 한다.
--
-- [domain: EMOTION - PRIVACY]


BEGIN;

ALTER TABLE emotion_logs
    DROP COLUMN IF EXISTS raw_text;

ALTER TABLE emotion_logs
    DROP COLUMN IF EXISTS raw_text_encrypted;

-- V3 가 raw_text 에 대한 NOT NULL 또는 CHECK 제약을 걸었으면 같이 떨어짐 (DROP COLUMN CASCADE).
-- 인덱스도 함께 떨어짐.

COMMIT;


-- ROLLBACK NOTES (자동 적용 금지)
-- 본 drop 은 *완전 의도된* PHI 제거. 복원은 안전 가이드 §3 의 명시적 변경이 선행돼야 하며,
-- 단순 컬럼 복원으로는 충분치 않다 (사용자 동의·암호화 키 관리 등이 함께 설계돼야 함).
