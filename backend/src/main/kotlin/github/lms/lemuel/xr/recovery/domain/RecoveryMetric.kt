package github.lms.lemuel.xr.recovery.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * recovery_metrics 도메인 모델 — 불변. 자체 회복 지표 (V3), 일별 cron 으로 채운다.
 *
 * 영속 상세(JPA 엔티티)는 어댑터에만 존재하며, 아웃바운드 포트는 이 순수 도메인 타입으로만
 * 대화한다. [id] 는 영속 전(신규 집계) 에는 `null` 일 수 있다.
 */
data class RecoveryMetric(
    val id: Long?,
    val userId: UUID,
    val date: LocalDate,
    val emotionDiversity: Int?,
    val avgIntensity: BigDecimal?,
    val diaryWords: Int?,
    val missionsCompleted: Int?,
    val topKeywords: Array<String>?,
    val riskSignals: Int?,
) {

    companion object {
        /** 신규 집계용 팩토리 — 아직 영속되지 않아 [id] 는 `null`. */
        fun newMetric(
            userId: UUID,
            date: LocalDate,
            emotionDiversity: Int?,
            avgIntensity: BigDecimal?,
            diaryWords: Int?,
            missionsCompleted: Int?,
            topKeywords: Array<String>?,
            riskSignals: Int?,
        ): RecoveryMetric =
            RecoveryMetric(
                id = null,
                userId = userId,
                date = date,
                emotionDiversity = emotionDiversity,
                avgIntensity = avgIntensity,
                diaryWords = diaryWords,
                missionsCompleted = missionsCompleted,
                topKeywords = topKeywords,
                riskSignals = riskSignals,
            )
    }
}
