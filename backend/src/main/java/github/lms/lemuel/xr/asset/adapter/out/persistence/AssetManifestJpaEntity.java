package github.lms.lemuel.xr.asset.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** asset_manifests (V10) — 미션·씬·디바이스별 자산 묶음. */
@Entity
@Table(name = "asset_manifests")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AssetManifestJpaEntity {

    @Id
    private UUID id;

    @Column(name = "mission_id", nullable = false, length = 50)
    private String missionId;

    @Column(name = "scene_number")
    private Short sceneNumber;

    @Column(name = "device_type", nullable = false, length = 30)
    private String deviceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities_min", columnDefinition = "jsonb")
    private Map<String, Object> capabilitiesMin;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "manifest", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> manifest;

    @Column(name = "audio_locale", length = 10)
    private String audioLocale;

    @Column(name = "total_size_bytes")
    private Long totalSizeBytes;

    @Column(name = "cdn_base_url", columnDefinition = "TEXT")
    private String cdnBaseUrl;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;
}
