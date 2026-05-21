package github.lms.lemuel.xr.safety.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "safety_alerts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class SafetyAlertJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "app_session_id")
    private UUID appSessionId;

    @Column(name = "emotion_log_id")
    private Long emotionLogId;

    @Column(name = "matched_pattern", nullable = false, length = 50)
    private String matchedPattern;

    @Column(nullable = false, length = 10)
    private String severity;

    @Column(name = "trigger_source", nullable = false, length = 20)
    private String triggerSource;

    @Column(name = "raw_excerpt_hash", length = 64)
    private String rawExcerptHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shown_resources", columnDefinition = "jsonb")
    private List<Map<String, Object>> shownResources;

    @Column(name = "user_acknowledged")
    private Boolean userAcknowledged = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
