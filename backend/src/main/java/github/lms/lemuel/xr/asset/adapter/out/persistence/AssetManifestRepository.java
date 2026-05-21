package github.lms.lemuel.xr.asset.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetManifestRepository extends JpaRepository<AssetManifestJpaEntity, UUID> {

    /** 미션·씬·디바이스 조합으로 최신 active manifest. version 내림차순. */
    @Query("SELECT a FROM AssetManifestJpaEntity a " +
           "WHERE a.missionId = :mission AND a.deviceType IN (:device, '*') " +
           "AND (:scene IS NULL OR a.sceneNumber = :scene OR a.sceneNumber IS NULL) " +
           "AND a.isActive = true ORDER BY a.version DESC")
    Optional<AssetManifestJpaEntity> findLatest(@Param("mission") String missionId,
                                                 @Param("scene") Short sceneNumber,
                                                 @Param("device") String deviceType);

    boolean existsByMissionIdAndSceneNumberAndDeviceTypeAndVersion(
            String missionId, Short sceneNumber, String deviceType, String version);
}
