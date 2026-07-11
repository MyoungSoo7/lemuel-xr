package github.lms.lemuel.xr.content.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "diary_entries")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class DiaryEntryJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "body_encrypted")
    private byte[] bodyEncrypted;

    @Column(name = "form_type", length = 20)
    private String formType;

    @Column(name = "emotion_label", length = 30)
    private String emotionLabel;

    @Column
    private Short intensity;

    @Column(name = "meditation_text", columnDefinition = "text")
    private String meditationText;

    @Column(name = "meditation_accepted")
    private Boolean meditationAccepted = false;

    @Column(name = "meditation_dimension", length = 20)
    private String meditationDimension;

    @Column(name = "word_count")
    private Integer wordCount;

    @Column(name = "sentiment_score", precision = 3, scale = 2)
    private BigDecimal sentimentScore;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
