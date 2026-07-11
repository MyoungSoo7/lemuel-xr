package github.lms.lemuel.xr.auth.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** users 테이블 — V1 (id, created_at TIMESTAMP) + V3 확장 (updated_at/deleted_at TIMESTAMPTZ). */
@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UserJpaEntity {

    @Id
    private UUID id;

    // V1: TIMESTAMP (no TZ)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // V3: TIMESTAMPTZ
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "user_type", nullable = false, length = 20)
    private String userType;     // 'guest' | 'oauth_google' | 'oauth_kakao'

    @Column(name = "external_id", length = 255)
    private String externalId;

    @Column(name = "faith_tone", length = 20)
    private String faithTone;    // 'strong' | 'balanced' | 'soft'

    @Column(name = "preferred_mode", length = 20)
    private String preferredMode; // 'spiritual' | 'emotional' | 'rational' | null

    @Column(name = "haptic_intensity", length = 10)
    private String hapticIntensity; // 'off' | 'low' | 'medium' | 'high'

    @Column(name = "skip_intro_silence")
    private Boolean skipIntroSilence;

    @Column(name = "data_retention_days")
    private Integer dataRetentionDays;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    // V20260521224700 — Disclaimer 동의 게이트
    @Column(name = "disclaimer_accepted_at")
    private OffsetDateTime disclaimerAcceptedAt;

    @Column(name = "disclaimer_version", length = 20)
    private String disclaimerVersion;

    @Column(name = "ai_opt_out", nullable = false)
    private Boolean aiOptOut = Boolean.FALSE;
}
