package github.lms.lemuel.xr.user.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 게스트 사용자 — UUID 자동 발급, 디바이스 단위 식별.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class UserJpaEntity {
    @Id
    private UUID id;

    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @Column(nullable = false)
    private Boolean guest = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
