-- asset_manifests 에 xr_mode 축 추가 — 같은 (미션·씬·디바이스) 라도 몰입 모드가 다르면
-- 다른 에셋 묶음이 필요하다. VR 은 환경 모델을 통째로 내려받지만, AR 은 실제 방을 배경으로
-- 쓰므로 환경을 빼고 앵커에 붙일 소품만 받는다.
--
-- 기존 행은 전부 'vr' (DEFAULT). 어떤 미션이 'ar' 를 노출할지는 DB 가 아니라
-- 애플리케이션 정책(lemuel.xr.ar-enabled-missions, 기본 joseph)이 정한다 —
-- 디바이스/모드 분기를 도메인이 아니라 경계에 두는 XR-INTEGRATION §6 원칙 유지.

ALTER TABLE asset_manifests
    ADD COLUMN IF NOT EXISTS xr_mode VARCHAR(10) NOT NULL DEFAULT 'vr';

ALTER TABLE asset_manifests
    DROP CONSTRAINT IF EXISTS chk_asset_manifests_xr_mode;
ALTER TABLE asset_manifests
    ADD CONSTRAINT chk_asset_manifests_xr_mode CHECK (xr_mode IN ('vr', 'ar'));

-- 조회 인덱스에 모드를 포함. 클라이언트 조회 키가
-- (mission_id, scene_number, device_type, xr_mode, version) 로 확장된다.
DROP INDEX IF EXISTS idx_asset_manifests_lookup;
CREATE INDEX IF NOT EXISTS idx_asset_manifests_lookup
    ON asset_manifests (mission_id, scene_number, device_type, xr_mode, version)
    WHERE is_active = TRUE;
