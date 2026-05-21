-- V10: ASSET MANIFEST — 디바이스 capabilities 기반 에셋 선택
-- XR-INTEGRATION.md §6.3

-- ============================================================
-- asset_manifests — 미션·씬·디바이스별 에셋 묶음
--   클라이언트는 미션 시작 시 (mission_id, device_type, version) 으로 조회
-- ============================================================
CREATE TABLE IF NOT EXISTS asset_manifests (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_id          VARCHAR(50) NOT NULL,          -- 'joseph' | 'moses' | 'david' | 'jesus'
    scene_number        SMALLINT,                      -- NULL = 미션 전체
    device_type         VARCHAR(30) NOT NULL,          -- 'quest3' | 'visionpro' | 'galaxyxr' | 'web' | '*'
    capabilities_min    JSONB,                         -- {"passthrough":true,"hand_tracking":true,"eye_tracking":false}
    version             VARCHAR(20) NOT NULL,          -- semver
    manifest            JSONB NOT NULL,                -- {"models":[{"id":"granary","url":".../granary.glb","size":12345}], ...}
    audio_locale        VARCHAR(10),                   -- 'ko-KR' | 'en-US' | '*' (locale-agnostic 에셋)
    total_size_bytes    BIGINT,
    cdn_base_url        TEXT,                          -- 'https://cdn.r2.dev/lemuel-xr/'
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    superseded_at       TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_asset_manifests_lookup
    ON asset_manifests(mission_id, scene_number, device_type, version)
    WHERE is_active = TRUE;

-- ============================================================
-- asset_downloads — 클라이언트별 에셋 다운로드 진척 (오프라인 동기화)
-- ============================================================
CREATE TABLE IF NOT EXISTS asset_downloads (
    id                  BIGSERIAL PRIMARY KEY,
    device_id           UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    manifest_id         UUID NOT NULL REFERENCES asset_manifests(id) ON DELETE CASCADE,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',  -- 'pending'|'downloading'|'complete'|'failed'
    bytes_downloaded    BIGINT DEFAULT 0,
    bytes_total         BIGINT,
    started_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ,
    error_message       TEXT,
    UNIQUE (device_id, manifest_id)
);
CREATE INDEX IF NOT EXISTS idx_asset_downloads_device
    ON asset_downloads(device_id, status);
