package github.lms.lemuel.xr.emotion.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "emotion_logs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class EmotionLogJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "app_session_id")
    private UUID appSessionId;

    // raw_text / raw_text_encrypted 컬럼은 docs/safety-guidelines.md §3 (PHI 비수집)
    // 에 의거 *영속화 금지*. 분류 직후 폐기됨. V20260522210000 마이그레이션이 DB 에서도 제거.

    @Column(name = "classified_emotion", length = 30)
    private String classifiedEmotion;

    @Column(precision = 4, scale = 3)
    private BigDecimal confidence;

    @Column
    private Short intensity;

    @Column(name = "chosen_dimension", length = 20)
    private String chosenDimension;

    @Column(name = "recommended_track", length = 1)
    private String recommendedTrack;

    @Column(name = "recommended_content", length = 50)
    private String recommendedContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
