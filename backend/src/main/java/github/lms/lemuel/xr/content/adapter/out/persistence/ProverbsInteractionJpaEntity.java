package github.lms.lemuel.xr.content.adapter.out.persistence;

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
@Table(name = "proverbs_interactions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class ProverbsInteractionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_situation", columnDefinition = "text")
    private String userSituation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_proverbs", columnDefinition = "jsonb")
    private List<Map<String, Object>> recommendedProverbs;

    @Column(name = "chosen_proverb_ref", length = 20)
    private String chosenProverbRef;

    @Column(name = "chosen_dimension", length = 20)
    private String chosenDimension;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reframing_response", columnDefinition = "jsonb")
    private Map<String, Object> reframingResponse;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
