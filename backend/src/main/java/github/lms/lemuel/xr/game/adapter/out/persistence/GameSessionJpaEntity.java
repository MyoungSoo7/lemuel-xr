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

    @Column(nullable = false, length = 20)
    private String character;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decisions", columnDefinition = "jsonb")
    private Map<String, Object> decisions = new HashMap<>();

    @Column(name = "final_outcome", length = 50)
    private String finalOutcome;
}
