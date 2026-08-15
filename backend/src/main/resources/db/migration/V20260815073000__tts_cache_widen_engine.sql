-- tts_cache.engine 을 VARCHAR(20) -> VARCHAR(64) 로 넓힌다.
--
-- 왜: 엔진을 XTTS-v2 에서 Gemini 로 바꾸면서 engine 값이 `xtts-v2`(7자)에서
-- `gemini-3.1-flash-tts-preview`(28자)가 됐다. 20자 제한에 걸려 합성 결과를 저장하는
-- UPDATE 가 통째로 터졌다 — 사이드카는 200 으로 오디오를 잘 만들어 돌려주는데,
-- 백엔드가 그걸 못 쓰고 매번 FAILED 로 떨어뜨렸다(프로덕션 실측 2026-08-15 07:23,
-- `ERROR: value too long for type character varying(20)`).
--
-- 사용자에게는 "나레이션이 그냥 안 나온다"로만 보이고, 사이드카 로그는 전부 200 이라
-- 사이드카만 보고 있으면 원인이 안 보인다.
--
-- 64 로 잡은 근거: 현재 가장 긴 값이 `gemini-2.5-flash-preview-tts`(28자)이고,
-- 모델 이름은 벤더가 정하는 문자열이라 우리가 통제하지 못한다. 넉넉히 두는 비용은
-- varchar 라 실제 저장에서 0 이다(Postgres 는 선언 길이만큼 미리 잡지 않는다).
ALTER TABLE tts_cache ALTER COLUMN engine TYPE VARCHAR(64);

-- 이 사고로 FAILED 가 된 행을 지운다. 남겨두면 캐시 조회가 FAILED 를 만나 재시도로
-- 흐르긴 하지만, 원인이 사라진 지금은 그냥 없는 편이 깨끗하다(재요청하면 다시 구워진다).
DELETE FROM tts_cache WHERE status = 'FAILED' AND audio_url IS NULL;
