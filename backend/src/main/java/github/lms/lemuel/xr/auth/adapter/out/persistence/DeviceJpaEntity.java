package github.lms.lemuel.xr.auth.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** devices 테이블 (V3) — last_seen_at / created_at 모두 TIMESTAMPTZ. */
@Entity
@Table(name = "devices")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class DeviceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @Column(name = "device_fingerprint", length = 255)
    private String deviceFingerprint;

    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
