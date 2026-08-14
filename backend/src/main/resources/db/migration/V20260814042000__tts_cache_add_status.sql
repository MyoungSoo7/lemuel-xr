-- tts_cache 에 합성 상태를 추가한다 (비동기 202 + 폴링 전환).
--
-- 기존 스키마는 "행이 있으면 오디오도 있다" 를 암묵 전제로 했다. 비동기가 되면
-- *아직 오디오가 없는 행* (PENDING) 이 정상 상태로 존재하므로 구분자가 필요하다.
--
-- 롤아웃 안전성: DEFAULT 'READY' 를 먼저 걸어 두면, 배포 도중 남아 있는 구버전 파드가
-- status 를 빼고 INSERT 해도 기본값이 채워져 NOT NULL 을 위반하지 않는다.
-- (구버전은 동기 합성이라 행을 만들 때 이미 오디오가 있다 = READY 가 맞는 값이다.)
ALTER TABLE tts_cache ADD COLUMN IF NOT EXISTS status VARCHAR(16) DEFAULT 'READY';

-- 기존 행 백필: 오디오가 있으면 READY, 없으면 과거에 실패해 남은 잔해이므로 FAILED.
UPDATE tts_cache SET status = 'READY' WHERE status IS NULL AND audio_url IS NOT NULL;
UPDATE tts_cache SET status = 'FAILED' WHERE status IS NULL;

ALTER TABLE tts_cache ALTER COLUMN status SET NOT NULL;

-- 폴링(GET /api/tts/jobs/{id}) 은 PK 조회라 인덱스가 필요 없다.
-- 청소용(오래된 PENDING 회수) 조회만 status 를 스캔하므로 부분 인덱스로 충분하다.
CREATE INDEX IF NOT EXISTS idx_tts_cache_pending
    ON tts_cache (created_at)
    WHERE status = 'PENDING';
