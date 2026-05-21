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

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Column(name = "raw_text_encrypted")
    private byte[] rawTextEncrypted;

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
