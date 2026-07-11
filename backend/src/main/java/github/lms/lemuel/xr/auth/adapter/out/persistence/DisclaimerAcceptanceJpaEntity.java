package github.lms.lemuel.xr.auth.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** disclaimer_acceptances (V20260521224700) — 동의 audit log. */
@Entity
@Table(name = "disclaimer_acceptances")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class DisclaimerAcceptanceJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "accepted_at", nullable = false)
    private OffsetDateTime acceptedAt;

    @Column(name = "disclaimer_version", nullable = false, length = 20)
    private String disclaimerVersion;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** ip_hash — raw IP 저장 X. SHA-256(IP) 만 (분쟁 시 추적용). */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;
}
