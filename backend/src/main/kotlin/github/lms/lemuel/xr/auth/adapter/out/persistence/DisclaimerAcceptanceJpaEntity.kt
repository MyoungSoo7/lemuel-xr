package github.lms.lemuel.xr.auth.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/** disclaimer_acceptances (V20260521224700) — 동의 audit log. */
@Entity
@Table(name = "disclaimer_acceptances")
class DisclaimerAcceptanceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "accepted_at", nullable = false)
    var acceptedAt: OffsetDateTime? = null

    @Column(name = "disclaimer_version", nullable = false, length = 20)
    var disclaimerVersion: String? = null

    @Column(name = "user_agent", length = 255)
    var userAgent: String? = null

    /** ip_hash — raw IP 저장 X. SHA-256(IP) 만 (분쟁 시 추적용). */
    @Column(name = "ip_hash", length = 64)
    var ipHash: String? = null
}
