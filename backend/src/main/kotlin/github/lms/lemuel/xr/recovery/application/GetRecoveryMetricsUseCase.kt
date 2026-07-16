package github.lms.lemuel.xr.recovery.application

import github.lms.lemuel.xr.recovery.application.port.out.RecoveryMetricPort
import github.lms.lemuel.xr.recovery.domain.RecoveryMetric
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** 본인 최근 N일 회복 추이 조회 — 포트에서 도메인 조회 후 DTO 로 매핑한다 (영속 상세 유출 금지). */
@Service
class GetRecoveryMetricsUseCase(
    private val metrics: RecoveryMetricPort,
) {

    @Transactional(readOnly = true)
    fun execute(userId: UUID, days: Int): List<MetricDto> =
        metrics.findRecent(userId, LocalDate.now().minusDays(days.toLong())).map(::toDto)

    private fun toDto(r: RecoveryMetric): MetricDto =
        MetricDto(
            r.date, r.emotionDiversity, r.avgIntensity, r.diaryWords,
            r.missionsCompleted, r.riskSignals,
        )

    data class MetricDto(
        val date: LocalDate,
        val emotionDiversity: Int?,
        val avgIntensity: BigDecimal?,
        val diaryWords: Int?,
        val missionsCompleted: Int?,
        val riskSignals: Int?,
    )
}
