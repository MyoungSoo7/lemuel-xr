package github.lms.lemuel.xr.auth.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

/** users 테이블 — V1 (id, created_at TIMESTAMP) + V3 확장 (updated_at/deleted_at TIMESTAMPTZ). */
@Entity
@Table(name = "users")
class UserJpaEntity {

    @Id
    var id: UUID? = null

    // V1: TIMESTAMP (no TZ)
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime? = null

    // V3: TIMESTAMPTZ
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime? = null

    @Column(name = "user_type", nullable = false, length = 20)
    var userType: String? = null     // 'guest' | 'oauth_google' | 'oauth_kakao'

    @Column(name = "external_id", length = 255)
    var externalId: String? = null

    @Column(name = "faith_tone", length = 20)
    var faithTone: String? = null    // 'strong' | 'balanced' | 'soft'

    @Column(name = "preferred_mode", length = 20)
    var preferredMode: String? = null // 'spiritual' | 'emotional' | 'rational' | null

    @Column(name = "haptic_intensity", length = 10)
    var hapticIntensity: String? = null // 'off' | 'low' | 'medium' | 'high'

    @Column(name = "skip_intro_silence")
    var skipIntroSilence: Boolean? = null

    @Column(name = "data_retention_days")
    var dataRetentionDays: Int? = null

    @Column(name = "deleted_at")
    var deletedAt: OffsetDateTime? = null

    // V20260521224700 — Disclaimer 동의 게이트
    @Column(name = "disclaimer_accepted_at")
    var disclaimerAcceptedAt: OffsetDateTime? = null

    @Column(name = "disclaimer_version", length = 20)
    var disclaimerVersion: String? = null

    @Column(name = "ai_opt_out", nullable = false)
    var aiOptOut: Boolean = false
}
