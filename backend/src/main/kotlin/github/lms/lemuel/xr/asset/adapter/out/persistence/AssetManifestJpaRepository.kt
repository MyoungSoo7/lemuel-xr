package github.lms.lemuel.xr.asset.adapter.out.persistence

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface AssetManifestJpaRepository : JpaRepository<AssetManifestJpaEntity, UUID> {

    /**
     * 미션·씬·디바이스 조합으로 최신 active manifest. version 내림차순.
     * scene 이 null 이거나 여러 씬 manifest 가 매칭될 수 있으므로 [Limit] 로 1행만
     * 취해 NonUniqueResultException 을 방지한다(호출부는 Limit.of(1)).
     */
    @Query(
        "SELECT a FROM AssetManifestJpaEntity a " +
            "WHERE a.missionId = :mission AND a.deviceType IN (:device, '*') " +
            "AND (:scene IS NULL OR a.sceneNumber = :scene OR a.sceneNumber IS NULL) " +
            "AND a.isActive = true ORDER BY a.version DESC",
    )
    fun findLatest(
        @Param("mission") missionId: String,
        @Param("scene") sceneNumber: Short?,
        @Param("device") deviceType: String,
        limit: Limit,
    ): Optional<AssetManifestJpaEntity>

    fun existsByMissionIdAndSceneNumberAndDeviceTypeAndVersion(
        missionId: String,
        sceneNumber: Short?,
        deviceType: String,
        version: String,
    ): Boolean
}
