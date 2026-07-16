package github.lms.lemuel.xr.recovery.adapter.out.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** recovery_metrics — 자체 회복 지표 (V3). 일별 cron 으로 채움. */
@Entity
@Table(name = "recovery_metrics")
class RecoveryMetricJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "metric_date", nullable = false)
    var metricDate: LocalDate? = null

    @Column(name = "emotion_diversity_count")
    var emotionDiversityCount: Int? = null

    @Column(name = "avg_intensity", precision = 3, scale = 1)
    var avgIntensity: BigDecimal? = null

    @Column(name = "diary_word_count")
    var diaryWordCount: Int? = null

    @Column(name = "mission_completed_count")
    var missionCompletedCount: Int? = null

    @Column(name = "top_keywords", columnDefinition = "text[]")
    var topKeywords: Array<String>? = null

    @Column(name = "risk_signal_count")
    var riskSignalCount: Int? = null
}
