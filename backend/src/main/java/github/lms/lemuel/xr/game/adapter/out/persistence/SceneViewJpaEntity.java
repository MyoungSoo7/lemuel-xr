package github.lms.lemuel.xr.game.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "scene_views")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class SceneViewJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_session_id", nullable = false)
    private UUID gameSessionId;

    @Column(name = "scene_number", nullable = false)
    private Short sceneNumber;

    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    @Column(name = "exited_at")
    private LocalDateTime exitedAt;

    @Column(name = "duration_seconds", insertable = false, updatable = false)
    private Integer durationSeconds;

    @Column(name = "exit_reason", length = 30)
    private String exitReason;

    @Column(name = "skipped_silence")
    private Boolean skippedSilence = false;
}
