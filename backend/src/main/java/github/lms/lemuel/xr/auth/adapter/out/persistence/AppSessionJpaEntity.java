package github.lms.lemuel.xr.auth.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** app_sessions — 앱 진입~종료 단위 (V3). 게임 세션과 별개. */
@Entity
@Table(name = "app_sessions")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class AppSessionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "duration_seconds", insertable = false, updatable = false)
    private Integer durationSeconds;  // GENERATED ALWAYS — read-only

    @Column(name = "entry_emotion", length = 30)
    private String entryEmotion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_missions", columnDefinition = "jsonb", nullable = false)
    private List<String> completedMissions = new ArrayList<>();
}
