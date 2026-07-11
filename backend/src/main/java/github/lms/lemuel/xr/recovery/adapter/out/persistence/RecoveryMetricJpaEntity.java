package github.lms.lemuel.xr.recovery.adapter.out.persistence;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** recovery_metrics — 자체 회복 지표 (V3). 일별 cron 으로 채움. */
@Entity
@Table(name = "recovery_metrics")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class RecoveryMetricJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "emotion_diversity_count")
    private Integer emotionDiversityCount;

    @Column(name = "avg_intensity", precision = 3, scale = 1)
    private BigDecimal avgIntensity;

    @Column(name = "diary_word_count")
    private Integer diaryWordCount;

    @Column(name = "mission_completed_count")
    private Integer missionCompletedCount;

    @Column(name = "top_keywords", columnDefinition = "text[]")
    private String[] topKeywords;

    @Column(name = "risk_signal_count")
    private Integer riskSignalCount;
}
