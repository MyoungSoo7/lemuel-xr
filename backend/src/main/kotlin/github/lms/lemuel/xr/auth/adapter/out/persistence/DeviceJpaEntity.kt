package github.lms.lemuel.xr.auth.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/** devices 테이블 (V3) — last_seen_at / created_at 모두 TIMESTAMPTZ. */
@Entity
@Table(name = "devices")
class DeviceJpaEntity {

    @Id
    var id: UUID? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "device_type", length = 30)
    var deviceType: String? = null

    @Column(name = "device_fingerprint", length = 255)
    var deviceFingerprint: String? = null

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: OffsetDateTime? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: OffsetDateTime? = null
}
