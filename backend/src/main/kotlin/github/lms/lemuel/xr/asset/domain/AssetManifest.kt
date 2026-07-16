package github.lms.lemuel.xr.asset.domain

import java.time.LocalDateTime
import java.util.UUID

/**
 * asset manifest 도메인 모델 — 미션·씬·디바이스별 자산 묶음.
 *
 * 영속화 세부(JPA)와 무관한 불변 표현. 아웃바운드 포트([github.lms.lemuel.xr.asset.application.port.out.AssetManifestPort])는
 * 이 타입만 주고받으며, `AssetManifestJpaEntity` 는
 * `AssetManifestPersistenceAdapter` 안에만 갇혀 있다.
 */
data class AssetManifest(
    val id: UUID,
    val missionId: String,
    val sceneNumber: Short?,
    val deviceType: String,
    val capabilitiesMin: Map<String, Any?>?,
    val version: String,
    val manifest: Map<String, Any?>,
    val audioLocale: String?,
    val totalSizeBytes: Long?,
    val cdnBaseUrl: String?,
    val active: Boolean,
    val createdAt: LocalDateTime,
    val supersededAt: LocalDateTime?,
)
