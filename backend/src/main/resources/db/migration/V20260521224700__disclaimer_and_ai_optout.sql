-- V20260521224700: Disclaimer 동의 게이트 + AI opt-out 컬럼
--
-- 배경: 2026-05-21 기획 전환 — "절망 → 회복·소망" mission 으로 격상되면서
-- 사용자 노출 콘텐츠 *전체* 가 medical-device 오인 위험을 가짐.
-- "치료 도구가 아닙니다" 디스클레이머를 *기능적 게이트* 로 만들어 법적 방어선.
--
-- 추가:
--   users.disclaimer_accepted_at  — NULL 이면 모든 콘텐츠 endpoint 차단 (Gate 1)
--   users.disclaimer_version      — 본문 변경 시 재동의 트리거 (예: '1.0', '1.1')
--   users.ai_opt_out              — true 면 모든 LLM 호출 skip, 큐레이션 콘텐츠로 fallback (Layer 4)
--
-- Rollback:
--   ALTER TABLE users DROP COLUMN disclaimer_accepted_at, DROP COLUMN disclaimer_version, DROP COLUMN ai_opt_out;
--
-- 호환: 기존 사용자 (V3 이전 가입) 는 disclaimer_accepted_at IS NULL → 다음 진입 시 동의 게이트.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS disclaimer_accepted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS disclaimer_version     VARCHAR(20),
    ADD COLUMN IF NOT EXISTS ai_opt_out             BOOLEAN NOT NULL DEFAULT FALSE;

-- 미동의 사용자 빠른 검색 (Gate filter 가 매 요청 lookup)
CREATE INDEX IF NOT EXISTS idx_users_disclaimer_pending
    ON users(id) WHERE disclaimer_accepted_at IS NULL AND deleted_at IS NULL;

-- 동의 이력 audit table — 6개월마다 재동의 등 추적용
CREATE TABLE IF NOT EXISTS disclaimer_acceptances (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    accepted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    disclaimer_version VARCHAR(20) NOT NULL,
    user_agent      VARCHAR(255),
    ip_hash         VARCHAR(64)       -- raw IP 저장 X — SHA-256 hash 만 (분쟁 시 추적용)
);
CREATE INDEX IF NOT EXISTS idx_disclaimer_acceptances_user
    ON disclaimer_acceptances(user_id, accepted_at DESC);

COMMENT ON COLUMN users.disclaimer_accepted_at IS
    '치료 도구 아님·위기 자원 안내·AI 라벨링 동의 시각. NULL = 미동의 = 콘텐츠 endpoint 차단.';
COMMENT ON COLUMN users.disclaimer_version IS
    '동의한 disclaimer 본문 버전. 변경 시 재동의 트리거.';
COMMENT ON COLUMN users.ai_opt_out IS
    'true 면 LLM 생성 콘텐츠 모두 skip, 큐레이션 정적 콘텐츠만 제공. ETHICS-LEGAL §AI 선택권.';
