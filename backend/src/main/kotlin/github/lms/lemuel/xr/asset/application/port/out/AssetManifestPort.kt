package github.lms.lemuel.xr.asset.application.port.out

import github.lms.lemuel.xr.asset.domain.AssetManifest
import java.util.Optional

/**
 * asset manifest 영속화 아웃바운드 포트.
 *
 * ISP — 애플리케이션(유스케이스/시더)이 실제로 쓰는 3개 연산만 노출한다.
 * `JpaRepository` 의 나머지 CRUD 전면은 의도적으로 감춘다.
 *
 * 포트는 도메인 [AssetManifest] 만 주고받는다. `AssetManifestJpaEntity` 는
 * 어댑터(`AssetManifestPersistenceAdapter`) 안에만 존재한다.
 */
interface AssetManifestPort {

    /**
     * 미션·씬·디바이스 조합으로 최신 active manifest (version 내림차순 1행).
     * scene 이 null 이거나 여러 씬 manifest 가 매칭될 수 있어 1행만 취해
     * NonUniqueResultException 을 방지한다.
     */
    fun findLatest(missionId: String, sceneNumber: Short?, deviceType: String): Optional<AssetManifest>

    /** (mission_id, scene_number, device_type, version) 중복 여부 — 재배포 안전 시드용. */
    fun existsByCoordinates(missionId: String, sceneNumber: Short?, deviceType: String, version: String): Boolean

    /** manifest 저장 후 영속화된 상태를 도메인으로 반환. */
    fun save(manifest: AssetManifest): AssetManifest
}
