package github.lms.lemuel.xr.game.adapter.out.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_decisions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class GameDecisionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_session_id", nullable = false)
    private UUID gameSessionId;

    @Column(name = "scene_number", nullable = false)
    private Short sceneNumber;

    @Column(name = "scene_name", length = 50)
    private String sceneName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> decision;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interaction_meta", columnDefinition = "jsonb")
    private Map<String, Object> interactionMeta;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;
}
