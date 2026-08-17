package github.lms.lemuel.xr.asset.adapter.out.persistence

import github.lms.lemuel.xr.asset.application.port.out.AssetManifestPort
import github.lms.lemuel.xr.asset.domain.AssetManifest
import github.lms.lemuel.xr.asset.domain.XrMode
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import java.util.Optional

/**
 * [AssetManifestPort] 를 Spring Data JPA 로 구현. 포트(도메인) ↔ JpaRepository(엔티티)
 * 사이 어댑터.
 *
 * `AssetManifestJpaEntity` 를 import 하는 유일한 애플리케이션 협력 지점.
 * 엔티티↔도메인 매핑을 여기서 전담한다.
 */
@Component
class AssetManifestPersistenceAdapter(
    private val jpa: AssetManifestJpaRepository,
) : AssetManifestPort {

    override fun findLatest(
        missionId: String,
        sceneNumber: Short?,
        deviceType: String,
        xrMode: XrMode,
    ): Optional<AssetManifest> =
        jpa.findLatest(missionId, sceneNumber, deviceType, xrMode.wire, Limit.of(1)).map(::toDomain)

    override fun existsByCoordinates(
        missionId: String,
        sceneNumber: Short?,
        deviceType: String,
        xrMode: XrMode,
        version: String,
    ): Boolean =
        jpa.existsByMissionIdAndSceneNumberAndDeviceTypeAndXrModeAndVersion(
            missionId, sceneNumber, deviceType, xrMode.wire, version,
        )

    override fun save(manifest: AssetManifest): AssetManifest =
        toDomain(jpa.save(toEntity(manifest)))

    private fun toDomain(e: AssetManifestJpaEntity): AssetManifest =
        AssetManifest(
            id = e.id!!,
            missionId = e.missionId!!,
            sceneNumber = e.sceneNumber,
            deviceType = e.deviceType!!,
            xrMode = XrMode.from(e.xrMode) ?: XrMode.VR,
            capabilitiesMin = e.capabilitiesMin,
            version = e.version!!,
            manifest = e.manifest ?: mutableMapOf(),
            audioLocale = e.audioLocale,
            totalSizeBytes = e.totalSizeBytes,
            cdnBaseUrl = e.cdnBaseUrl,
            active = e.isActive,
            createdAt = e.createdAt!!,
            supersededAt = e.supersededAt,
        )

    private fun toEntity(d: AssetManifest): AssetManifestJpaEntity =
        AssetManifestJpaEntity().apply {
            id = d.id
            missionId = d.missionId
            sceneNumber = d.sceneNumber
            deviceType = d.deviceType
            xrMode = d.xrMode.wire
            capabilitiesMin = d.capabilitiesMin?.toNonNullMutableMap()
            version = d.version
            manifest = d.manifest.toNonNullMutableMap()
            audioLocale = d.audioLocale
            totalSizeBytes = d.totalSizeBytes
            cdnBaseUrl = d.cdnBaseUrl
            isActive = d.active
            createdAt = d.createdAt
            supersededAt = d.supersededAt
        }

    /**
     * 도메인의 `Map<String, Any?>` 를 JSONB 컬럼용 `MutableMap<String, Any>` 로 변환.
     * null 값 항목은 제거한다(JSONB 저장 시 null 엔트리는 의미가 없음).
     */
    private fun Map<String, Any?>.toNonNullMutableMap(): MutableMap<String, Any> =
        entries.mapNotNull { (k, v) -> v?.let { k to it } }.toMap().toMutableMap()
}
