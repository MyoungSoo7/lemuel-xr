package github.lms.lemuel.xr.asset.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

/** asset_manifests (V10) — 미션·씬·디바이스별 자산 묶음. */
@Entity
@Table(name = "asset_manifests")
class AssetManifestJpaEntity {

    @Id
    var id: UUID? = null

    @Column(name = "mission_id", nullable = false, length = 50)
    var missionId: String? = null

    @Column(name = "scene_number")
    var sceneNumber: Short? = null

    @Column(name = "device_type", nullable = false, length = 30)
    var deviceType: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities_min", columnDefinition = "jsonb")
    var capabilitiesMin: MutableMap<String, Any>? = null

    @Column(name = "version", nullable = false, length = 20)
    var version: String? = null

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest", columnDefinition = "jsonb", nullable = false)
    var manifest: MutableMap<String, Any>? = null

    @Column(name = "audio_locale", length = 10)
    var audioLocale: String? = null

    @Column(name = "total_size_bytes")
    var totalSizeBytes: Long? = null

    @Column(name = "cdn_base_url", columnDefinition = "TEXT")
    var cdnBaseUrl: String? = null

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null

    @Column(name = "superseded_at")
    var supersededAt: LocalDateTime? = null
}
