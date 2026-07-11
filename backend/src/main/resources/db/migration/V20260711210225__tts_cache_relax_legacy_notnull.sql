-- AI 도메인 — tts_cache 레거시 컬럼 NOT NULL 완화.
--
-- V1 이 정의한 text_hash / storage_url 은 V9 에서 각각 cache_key(PK) 와 audio_url 로
-- 대체됐다. TtsCacheJpaEntity 는 신규 컬럼(cache_key, audio_url)만 매핑하고 레거시 두
-- 컬럼은 세팅하지 않으므로, 캐시 미스 시 INSERT 가 NOT NULL 제약에 걸려 500(E_INTERNAL)
-- 이 발생한다. 사용되지 않는 레거시 NOT NULL 제약을 제거해 실제 write 경로를 복구한다.
-- (컬럼 자체는 과거 데이터 보존을 위해 DROP 하지 않고 nullable 로만 완화.)

ALTER TABLE tts_cache ALTER COLUMN text_hash DROP NOT NULL;
ALTER TABLE tts_cache ALTER COLUMN storage_url DROP NOT NULL;

-- ROLLBACK NOTES (운영 사고 시 reference; 자동 적용 금지)
-- text_hash / storage_url 을 다시 NOT NULL 로 되돌리려면 기존 행의 NULL 을 먼저 백필해야 함:
--   UPDATE tts_cache SET text_hash = cache_key WHERE text_hash IS NULL;
--   UPDATE tts_cache SET storage_url = audio_url WHERE storage_url IS NULL;
--   ALTER TABLE tts_cache ALTER COLUMN text_hash SET NOT NULL;
--   ALTER TABLE tts_cache ALTER COLUMN storage_url SET NOT NULL;
