package github.lms.lemuel.xr.game.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_sessions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class GameSessionJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "app_session_id")
    private UUID appSessionId;

    @Column(nullable = false, length = 20)
    private String character;

    @Column(name = "chosen_dimension", length = 20)
    private String chosenDimension;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "abandoned_at")
    private LocalDateTime abandonedAt;

    @Column(name = "triggered_by_emotion_log_id")
    private Long triggeredByEmotionLogId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decisions", columnDefinition = "jsonb")
    private Map<String, Object> decisions = new HashMap<>();

    @Column(name = "final_outcome", length = 50)
    private String finalOutcome;

    @Column(name = "closing_message", columnDefinition = "text")
    private String closingMessage;

    @Column(name = "scene_count_completed")
    private Short sceneCountCompleted = 0;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "device_type", length = 30)
    private String deviceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "capabilities", columnDefinition = "jsonb")
    private Map<String, Object> capabilities;

    @Column(name = "assets_manifest_version", length = 20)
    private String assetsManifestVersion;
}
